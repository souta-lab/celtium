package org.soutalab.celtium.vulkan.gl;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.lwjgl.system.MemoryUtil;

/**
 * The mod-owned GL programs the stub has seen: their sources, their link result as Vulkan GLSL, and the uniform values
 * {@code glUniform*} set on them.
 * <p>
 * Under GL-less a mod's {@code glShaderSource}/{@code glAttachShader}/{@code glLinkProgram}/{@code glUniform*} used to
 * be listed no-ops, so a program bound for a draw was an empty id and its batch could only be refused. This keeps what
 * a native pass needs to run the program instead: the lowered stages ({@link ModGlslTranslator}) and the values to pack
 * into the uniform block and to pick the sampled textures.
 * <p>
 * Client thread only, like every GL call the stub receives. A shader's stage is the one {@code glCreateShader}
 * was called with, recorded by {@link #shaderStage} when the GL-less route mints the name. A name minted without
 * that call (a test, or a route that does not reach it) falls back to reading the stage from the source - a stage
 * that writes {@code gl_Position} is the vertex stage - with the comments stripped first, so a fragment stage
 * that merely mentions {@code gl_Position} in a comment is not misread as the vertex stage.
 * <p>
 * Uniform locations are the stub's stable name hash ({@code CeltiumGllessHint.glUniformLocation}: 31-hash of the
 * name's bytes, masked to 30 bits), so a location maps back to a name once the program is linked.
 */
public final class ModProgramRegistry {

    /** A linked program: the lowering result and the values recorded for its uniforms, by GL name. */
    public static final class Program {
        final int id;
        final IntArrayList shaders = new IntArrayList();
        ModGlslTranslator.Result lowered;
        final Int2ObjectOpenHashMap<String> nameByLocation = new Int2ObjectOpenHashMap<>();
        final Map<String, float[]> floatValues = new HashMap<>();
        final Map<String, int[]> intValues = new HashMap<>();
        int uniformWrites;
        long uniformVersion = 1L;
        ByteBuffer cachedPackedUniforms;
        long cachedPackedVersion = -1L;
        /** Block member index per uniform name, in the lowered block's member order; -1 for a name that takes a
         *  location but is not a block member (a sampler or image unit). Built at link; null before the first. */
        Map<String, Integer> uniformMemberIndex;
        /** Block members whose values changed since the retained pack was written, a bitset over member indices. */
        long[] uniformPackDirtyWords;
        /** Whether the retained pack cannot be trusted member-by-member and must be rewritten in full. */
        boolean uniformPackAllDirty = true;
        /** The block the incremental packer last wrote, kept across versions so unchanged members survive. */
        ByteBuffer retainedPackedUniforms;
        /** The lowering {@link #retainedPackedUniforms} was packed for; a re-link replaces it and forces a full pack. */
        ModGlslTranslator.Result retainedPackResult;
        /** The compiled stages, when shaderc was available at link; null otherwise. */
        byte[] vertexSpirv;
        /** GLSM stage 1: the frequency bucket of each uniform name (block members and the members the
         *  translator extracted alike), built at link; null before the first successful link. */
        Map<String, GlsmPackState.Bucket> uniformBuckets;
        /** GLSM stage 1: a version per bucket, so a write advances only its own bucket's consumers.
         *  The scalar {@link #uniformVersion} still exists for the DYNAMIC block's pack cache; what
         *  changes under the flag is that REGION and PER_FRAME writes no longer advance it. */
        final long[] bucketVersions = new long[GlsmPackState.Bucket.values().length];
        byte[] fragmentSpirv;
        String compileFailure;

        Program(int id) {
            this.id = id;
        }

        /** {@return the GL program id this record is registered under}. */
        public int id() {
            return this.id;
        }

        public ModGlslTranslator.Result lowered() {
            return this.lowered;
        }

        /** {@return the float components last set for a uniform}, or null if none were. */
        public float[] floats(String name) {
            return this.floatValues.get(name);
        }

        /** {@return the integer components last set for a uniform (a sampler's texture unit)}, or null if none were. */
        public int[] ints(String name) {
            return this.intValues.get(name);
        }

        public int uniformWrites() {
            return this.uniformWrites;
        }

        public long uniformVersion() {
            return this.uniformVersion;
        }

        /** {@return the GLSM bucket version for {@code bucket}: how many writes that bucket has taken}. */
        public long bucketVersion(GlsmPackState.Bucket bucket) {
            return this.bucketVersions[bucket.ordinal()];
        }

        /** {@return the GLSM bucket {@code name} was classified into at link}, or null when unclassified. */
        public GlsmPackState.Bucket uniformBucket(String name) {
            return this.uniformBuckets == null ? null : this.uniformBuckets.get(name);
        }

        public ByteBuffer cachedPackedUniforms() {
            return this.cachedPackedVersion == this.uniformVersion ? this.cachedPackedUniforms : null;
        }

        /**
         * Returns a reusable zero-cleared direct ByteBuffer of at least {@code bytes} capacity, avoiding a fresh
         * {@code ByteBuffer.allocateDirect} (and Cleaner registration) on every uniform version change.
         */
        public ByteBuffer reusablePackedUniformBuffer(int bytes) {
            ByteBuffer buf = this.cachedPackedUniforms;
            if (buf == null || buf.capacity() < bytes) {
                buf = ByteBuffer.allocateDirect(bytes).order(java.nio.ByteOrder.nativeOrder());
            } else {
                buf.clear();
                MemoryUtil.memSet(MemoryUtil.memAddress(buf), 0, buf.capacity());
            }
            return buf;
        }

        public void cachePackedUniforms(long version, ByteBuffer packed) {
            this.cachedPackedUniforms = packed;
            this.cachedPackedVersion = version;
        }

        /**
         * Advances the uniform version for a value change to {@code name}, remembering which block member
         * changed so the incremental packer can rewrite just that member into the retained block. A name that
         * is not a block member (a sampler or image unit) leaves the block's bytes alone; a name the link never
         * classified distrusts the whole retained block, as does a write before the first link.
         */
        void bumpUniformVersion(String name) {
            // GLSM stage 1: under the flag, a write advances its bucket's version; a REGION or
            // PER_FRAME write owns no program-block bytes (the SSBO table / the shared block carry
            // it), so it must not advance the scalar version the block pack cache keys on - that
            // advance was the whole per-region re-derivation this stage removes. The value itself
            // is already recorded by the caller, which is what the table write and the harvest read.
            if (GlsmPackState.ENABLED && this.uniformBuckets != null) {
                GlsmPackState.Bucket bucket = this.uniformBuckets.get(name);
                if (bucket != null) {
                    this.bucketVersions[bucket.ordinal()]++;
                    if (bucket == GlsmPackState.Bucket.REGION || bucket == GlsmPackState.Bucket.PER_FRAME) {
                        return;
                    }
                }
            }
            this.uniformVersion++;
            Map<String, Integer> index = this.uniformMemberIndex;
            Integer member = index == null ? null : index.get(name);
            if (member == null) {
                this.uniformPackAllDirty = true;
            } else if (member >= 0) {
                int word = member >>> 6;
                if (this.uniformPackDirtyWords == null) {
                    this.uniformPackDirtyWords = new long[word + 1];
                } else if (word >= this.uniformPackDirtyWords.length) {
                    this.uniformPackDirtyWords = java.util.Arrays.copyOf(this.uniformPackDirtyWords, word + 1);
                }
                this.uniformPackDirtyWords[word] |= 1L << (member & 63);
            }
        }

        /** {@return whether the retained incremental pack must be rewritten in full}. */
        public boolean uniformPackAllDirty() {
            return this.uniformPackAllDirty;
        }

        /** {@return the live dirty bitset over block members for the incremental packer}, or null when none. */
        public long[] uniformPackDirtyWords() {
            return this.uniformPackDirtyWords;
        }

        /** Marks every member packed: the retained block now matches the current values. */
        public void consumeUniformPackDirty() {
            if (this.uniformPackDirtyWords != null) {
                java.util.Arrays.fill(this.uniformPackDirtyWords, 0L);
            }
            this.uniformPackAllDirty = false;
        }

        /** {@return the block the incremental packer last wrote}, or null before its first pack. */
        public ByteBuffer retainedPackedUniforms() {
            return this.retainedPackedUniforms;
        }

        /** {@return the lowering the retained block was packed for}. */
        public ModGlslTranslator.Result retainedPackResult() {
            return this.retainedPackResult;
        }

        /** Keeps the block the incremental packer wrote, and the lowering it was written for. */
        public void retainPackedUniforms(ByteBuffer packed, ModGlslTranslator.Result result) {
            this.retainedPackedUniforms = packed;
            this.retainedPackResult = result;
        }

        /** {@return whether both stages are compiled to SPIR-V} and the program could therefore run natively. */
        public boolean compiled() {
            return this.vertexSpirv != null && this.fragmentSpirv != null;
        }

        public byte[] vertexSpirv() {
            return this.vertexSpirv;
        }

        public byte[] fragmentSpirv() {
            return this.fragmentSpirv;
        }

        /** {@return why the program has no SPIR-V}: shaderc's message, its absence, or the lowering's failure. */
        public String compileFailure() {
            return this.compileFailure;
        }
    }

    /** {@code GL_VERTEX_SHADER} / {@code GL_FRAGMENT_SHADER}: the stages this registry can lower. */
    private static final int GL_VERTEX_SHADER = 0x8B31;
    private static final int GL_FRAGMENT_SHADER = 0x8B30;

    private static final Int2ObjectOpenHashMap<String> SHADER_SOURCES = new Int2ObjectOpenHashMap<>();
    /** The stage each shader name was created for, by GL enum; absent when the creating call was never routed here. */
    private static final it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap SHADER_STAGES =
            new it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap();
    private static final Int2ObjectOpenHashMap<Program> PROGRAMS = new Int2ObjectOpenHashMap<>();
    /**
     * Shader names deleted while a live program still referenced them (item 328, V-04). GL defers such a
     * deletion until detach, so the source is retained for the referencing program - but the name itself is
     * spent: if it is attached afresh (recycled by the driver for a new shader object), the retained source
     * must not be what a link compiles. A fresh {@code glShaderSource} for the name spends the marker.
     */
    private static final IntOpenHashSet DELETED_SHADERS = new IntOpenHashSet();
    /** Uniform writes to a location no linked program names: counted, so a lost value is visible. */
    private static int unmatchedUniformWrites;
    /**
     * Links where two different uniform names hashed to the same location, so one name's mapping silently
     * replaced the other's: counted, so the loss is visible. The location is the name hash itself (the
     * contract with {@code CeltiumGllessHint}), so a collision cannot be renumbered away - only reported.
     */
    private static int locationNameCollisions;
    private static boolean reportedUnavailable;

    private ModProgramRegistry() {
    }

    private static Program getOrCreateProgram(int program) {
        Program p = PROGRAMS.get(program);
        if (p == null) {
            p = new Program(program);
            PROGRAMS.put(program, p);
        }
        return p;
    }

    /**
     * {@code glCreateShader}: the name the GL-less route minted is for a shader of {@code glStage}. Creating is
     * what makes the name a new object, so a deletion marker the name still carries is spent here and the source
     * retained for its predecessor is dropped - the same fresh start {@link #attach} gives a recycled name, taken
     * at the call that actually starts the new incarnation.
     */
    public static void shaderStage(int shader, int glStage) {
        SHADER_STAGES.put(shader, glStage);
        if (DELETED_SHADERS.remove(shader)) {
            SHADER_SOURCES.remove(shader);
        }
    }

    /** {@code glShaderSource}: the stage's text, concatenated as GL concatenates the strings. */
    public static void shaderSource(int shader, String source) {
        SHADER_SOURCES.put(shader, source);
        // A fresh source is the current incarnation speaking for itself; any deletion marker the name
        // carried is spent.
        DELETED_SHADERS.remove(shader);
    }

    /** {@code glAttachShader}. */
    public static void attach(int program, int shader) {
        Program p = getOrCreateProgram(program);
        if (!p.shaders.contains(shader)) {
            // A name deleted while attached elsewhere and now attached afresh is a new shader object: in GL
            // it starts with no source, so its predecessor's retained source is dropped here rather than
            // compiled by this program's link. A later glShaderSource re-arms the name.
            if (DELETED_SHADERS.remove(shader)) {
                SHADER_SOURCES.remove(shader);
            }
            p.shaders.add(shader);
        }
    }

    /** {@code glLinkProgram}: lowers the attached stages, and names the result once per program. */
    public static Program link(int program) {
        Program p = getOrCreateProgram(program);
        String vertex = null;
        String fragment = null;
        String stageFailure = null;
        for (int i = 0, n = p.shaders.size(); i < n; i++) {
            int shader = p.shaders.getInt(i);
            String source = SHADER_SOURCES.get(shader);
            if (source == null) {
                continue;
            }
            // The stage is the one the shader was created for. The source heuristic remains only for a name
            // whose creation never reached shaderStage, and it reads the comment-stripped text: before, a
            // fragment stage that mentioned gl_Position in a comment was classified as the vertex stage and
            // the whole program was refused for having no fragment stage. Two shaders claiming one stage is
            // a link error in GL; it is one here too, rather than the second silently overwriting the first.
            int stage = SHADER_STAGES.get(shader);
            boolean isVertex;
            if (stage == GL_VERTEX_SHADER) {
                isVertex = true;
            } else if (stage == GL_FRAGMENT_SHADER) {
                isVertex = false;
            } else if (stage != 0) {
                stageFailure = "shader stage 0x" + Integer.toHexString(stage) + " is not lowered ("
                        + p.shaders.size() + " shaders)";
                break;
            } else {
                String stripped = ModGlslTranslator.strip(source);
                isVertex = containsWord(stripped, "gl_Position") || containsWord(stripped, "ftransform");
            }
            if (isVertex) {
                if (vertex != null) {
                    stageFailure = "two vertex stages attached (" + p.shaders.size() + " shaders)";
                    break;
                }
                vertex = source;
            } else {
                if (fragment != null) {
                    stageFailure = "two fragment stages attached (" + p.shaders.size() + " shaders)";
                    break;
                }
                fragment = source;
            }
        }
        if (stageFailure == null && fragment == null) {
            stageFailure = "no fragment stage attached (" + p.shaders.size() + " shaders)";
        }
        p.lowered = stageFailure != null
                ? new ModGlslTranslator.Result(null, null, List.of(), List.of(), 0, List.of(), List.of(), List.of(), 0,
                        false, java.util.Map.of(), stageFailure, null, List.of(), List.of())
                : ModGlslTranslator.translate(vertex, fragment);
        p.nameByLocation.clear();
        // A link starts the program's uniforms over, as in GL: the values a previous link's interface
        // collected belong to that interface, and a same-named member of the new one must not inherit them.
        p.floatValues.clear();
        p.intValues.clear();
        p.cachedPackedUniforms = null;
        p.cachedPackedVersion = -1L;
        p.uniformVersion++;
        // The incremental packer's state belongs to the previous link's block too: the member order it
        // indexed and the block it retained both start over, and its first pack under this link is a full one.
        p.uniformMemberIndex = null;
        p.uniformBuckets = null;
        p.uniformPackDirtyWords = null;
        p.uniformPackAllDirty = true;
        p.retainedPackedUniforms = null;
        p.retainedPackResult = null;
        if (p.lowered.ok()) {
            for (ModGlslTranslator.UniformMember member : p.lowered.uniforms()) {
                mapName(p, member.name());
            }
            for (ModGlslTranslator.SamplerBinding sampler : p.lowered.samplers()) {
                mapName(p, sampler.name());
            }
            // Image uniforms take locations too: Iris asks glGetUniformLocation for colorimg<i> and writes the
            // image unit back with glUniform1i, the same uniform-value model as the samplers - an unmapped name
            // would silently drop that write and every image would resolve to unit 0.
            for (ModGlslTranslator.ImageBinding image : p.lowered.images()) {
                mapName(p, image.name());
            }
            // The incremental packer's name classification, in the same pass that maps the locations:
            // block members by their index in the lowered member order, samplers and images as -1 (they
            // take locations and values but no block bytes). A name collision keeps the member reading -
            // the block is the side a missed dirty bit would silently corrupt.
            Map<String, Integer> memberIndex = new HashMap<>();
            for (int i = 0; i < p.lowered.uniforms().size(); i++) {
                memberIndex.put(p.lowered.uniforms().get(i).name(), i);
            }
            for (ModGlslTranslator.SamplerBinding sampler : p.lowered.samplers()) {
                memberIndex.putIfAbsent(sampler.name(), -1);
            }
            for (ModGlslTranslator.ImageBinding image : p.lowered.images()) {
                memberIndex.putIfAbsent(image.name(), -1);
            }
            p.uniformMemberIndex = memberIndex;
            // GLSM stage 1: the translator's extracted members (REGION / PER_FRAME) own no block
            // bytes, but their locations and values are still the program's interface - the guest
            // writes them by location and the harvester / table read the recorded values - so they
            // are mapped exactly like block members, and every uniform name gets its link-time
            // bucket for bumpUniformVersion's per-bucket versions.
            for (ModGlslTranslator.UniformMember extracted : p.lowered.extractedUniforms()) {
                mapName(p, extracted.name());
            }
            Map<String, GlsmPackState.Bucket> buckets = new HashMap<>();
            for (ModGlslTranslator.UniformMember member : p.lowered.uniforms()) {
                buckets.put(member.name(),
                        GlsmPackState.classify(member.name(), member.type(), member.arrayLength()));
            }
            for (ModGlslTranslator.UniformMember extracted : p.lowered.extractedUniforms()) {
                buckets.put(extracted.name(),
                        GlsmPackState.classify(extracted.name(), extracted.type(), extracted.arrayLength()));
            }
            p.uniformBuckets = buckets;
            java.util.Arrays.fill(p.bucketVersions, 1L);
        }
        p.vertexSpirv = null;
        p.fragmentSpirv = null;
        p.compileFailure = p.lowered.ok() ? null : "not lowered";
        if (p.lowered.ok()) {
            if (!ModShaderCompiler.available()) {
                p.compileFailure = "shaderc unavailable";
                if (!reportedUnavailable) {
                    reportedUnavailable = true;
                    System.out.println("[Celtium] CELTIUM_MOD_SHADERC available=false reason="
                            + ModShaderCompiler.unavailableReason());
                }
            } else {
                // Per stage, with the stage named: shaderc's message does not say which stage it was
                // compiling (both stages go in under the same input name), so a single shared catch left
                // the LINKED line unable to say whether the vertex or the fragment stage failed.
                try {
                    p.vertexSpirv = ModShaderCompiler.compile(p.lowered.vertex(), true);
                } catch (RuntimeException failure) {
                    // Spent like the fragment catch below spends it: on a re-link of a program that
                    // compiled before, the old bytes must not survive beside the new failure -
                    // compiled() reads the pair, and a stale vertex half would call this link compiled.
                    p.vertexSpirv = null;
                    p.compileFailure = "vertex stage: " + failure.getMessage();
                }
                if (p.compileFailure == null) {
                    try {
                        p.fragmentSpirv = ModShaderCompiler.compile(p.lowered.fragment(), false);
                    } catch (RuntimeException failure) {
                        p.vertexSpirv = null;
                        p.compileFailure = "fragment stage: " + failure.getMessage();
                    }
                }
            }
        }
        // The sampler and image names are the identity a leg can key on: program ids shift from run to run, while
        // the names say which pass a program serves (a deferred program samples the shadow textures, a composite
        // program the colour targets) - the distinction the 43/45/46/49 identification question turned on, which
        // the id-and-size line could not answer (offer-path diagnosis section 7, 2026-11-20).
        String names = "";
        if (p.lowered.ok()) {
            StringBuilder listed = new StringBuilder();
            for (ModGlslTranslator.SamplerBinding sampler : p.lowered.samplers()) {
                listed.append(listed.length() == 0 ? "" : ",").append(sampler.name());
            }
            for (ModGlslTranslator.ImageBinding image : p.lowered.images()) {
                listed.append(listed.length() == 0 ? "" : ",").append("img:").append(image.name());
            }
            names = listed.toString();
        }
        System.out.println("[Celtium] CELTIUM_MOD_PROGRAM_LINKED program=" + program
                + " compiled=" + p.compiled()
                + (p.compiled() ? " spirvBytes=" + (p.vertexSpirv.length + p.fragmentSpirv.length)
                        : " compileFailure=" + p.compileFailure)
                + (p.lowered.ok()
                        ? " lowered=true samplers=" + p.lowered.samplers().size() + " images=" + p.lowered.images().size() + " uniformBytes="
                                + p.lowered.uniformBlockSize() + " route=" + (p.lowered.uniformsViaBuffer() ? "ubo" : "push")
                                + " vertex=" + (vertex == null ? "fixed-function" : "own")
                                + " names=[" + names + "]"
                        : " lowered=false reason=" + p.lowered.failure()));
        return p;
    }

    private static boolean containsWord(String text, String word) {
        int wordLen = word.length();
        int from = 0;
        while ((from = text.indexOf(word, from)) >= 0) {
            boolean leftOk = from == 0 || !isWordChar(text.charAt(from - 1));
            int after = from + wordLen;
            boolean rightOk = after >= text.length() || !isWordChar(text.charAt(after));
            if (leftOk && rightOk) {
                return true;
            }
            from = after;
        }
        return false;
    }

    private static boolean isWordChar(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_';
    }

    private static void mapName(Program p, String name) {
        String replaced = p.nameByLocation.put(location(name), name);
        if (replaced != null && !replaced.equals(name)) {
            locationNameCollisions++;
        }
        replaced = p.nameByLocation.put(location(name + "[0]"), name);
        if (replaced != null && !replaced.equals(name)) {
            locationNameCollisions++;
        }
    }

    /** The stub's location for a uniform name; must stay equal to {@code CeltiumGllessHint.glUniformLocation}. */
    public static int location(String name) {
        int hash = 0;
        int len = name.length();
        for (int i = 0; i < len; i++) {
            char c = name.charAt(i);
            if (c >= 0x80) {
                return locationUtf8(name);
            }
            hash = 31 * hash + (byte) c;
        }
        return hash & 0x3FFFFFFF;
    }

    private static int locationUtf8(String name) {
        int hash = 0;
        byte[] bytes = name.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        for (byte b : bytes) {
            hash = 31 * hash + b;
        }
        return hash & 0x3FFFFFFF;
    }

    private static String resolveUniformName(Program p, int location) {
        if (p == null) {
            return null;
        }
        return p.nameByLocation.get(location);
    }

    /** {@code glUniform*f*}/{@code glUniformMatrix*}: the components, for the program currently bound. */
    public static void uniformFloats(int program, int location, float[] values) {
        Program p = PROGRAMS.get(program);
        String name = resolveUniformName(p, location);
        if (name == null) {
            unmatchedUniformWrites++;
            return;
        }
        float[] existing = p.floatValues.get(name);
        if (existing != null && existing.length == values.length) {
            boolean changed = false;
            for (int i = 0; i < values.length; i++) {
                if (Float.floatToRawIntBits(existing[i]) != Float.floatToRawIntBits(values[i])) {
                    existing[i] = values[i];
                    changed = true;
                }
            }
            if (changed) {
                p.bumpUniformVersion(name);
            }
        } else {
            p.floatValues.put(name, values);
            p.bumpUniformVersion(name);
        }
        p.uniformWrites++;
    }

    /** Zero-allocation scalar {@code glUniform1f}. */
    public static void uniformFloat1(int program, int location, float x) {
        Program p = PROGRAMS.get(program);
        String name = resolveUniformName(p, location);
        if (name == null) {
            unmatchedUniformWrites++;
            return;
        }
        float[] existing = p.floatValues.get(name);
        if (existing != null && existing.length == 1) {
            if (Float.floatToRawIntBits(existing[0]) != Float.floatToRawIntBits(x)) {
                existing[0] = x;
                p.bumpUniformVersion(name);
            }
        } else {
            p.floatValues.put(name, new float[] {x});
            p.bumpUniformVersion(name);
        }
        p.uniformWrites++;
    }

    /** Zero-allocation {@code glUniform2f}. */
    public static void uniformFloat2(int program, int location, float x, float y) {
        Program p = PROGRAMS.get(program);
        String name = resolveUniformName(p, location);
        if (name == null) {
            unmatchedUniformWrites++;
            return;
        }
        float[] existing = p.floatValues.get(name);
        if (existing != null && existing.length == 2) {
            if (Float.floatToRawIntBits(existing[0]) != Float.floatToRawIntBits(x)
                    || Float.floatToRawIntBits(existing[1]) != Float.floatToRawIntBits(y)) {
                existing[0] = x;
                existing[1] = y;
                p.bumpUniformVersion(name);
            }
        } else {
            p.floatValues.put(name, new float[] {x, y});
            p.bumpUniformVersion(name);
        }
        p.uniformWrites++;
    }

    /** Zero-allocation {@code glUniform3f}. */
    public static void uniformFloat3(int program, int location, float x, float y, float z) {
        Program p = PROGRAMS.get(program);
        String name = resolveUniformName(p, location);
        if (name == null) {
            unmatchedUniformWrites++;
            return;
        }
        float[] existing = p.floatValues.get(name);
        if (existing != null && existing.length == 3) {
            if (Float.floatToRawIntBits(existing[0]) != Float.floatToRawIntBits(x)
                    || Float.floatToRawIntBits(existing[1]) != Float.floatToRawIntBits(y)
                    || Float.floatToRawIntBits(existing[2]) != Float.floatToRawIntBits(z)) {
                existing[0] = x;
                existing[1] = y;
                existing[2] = z;
                p.bumpUniformVersion(name);
            }
        } else {
            p.floatValues.put(name, new float[] {x, y, z});
            p.bumpUniformVersion(name);
        }
        p.uniformWrites++;
    }

    /** Zero-allocation {@code glUniform4f}. */
    public static void uniformFloat4(int program, int location, float x, float y, float z, float w) {
        Program p = PROGRAMS.get(program);
        String name = resolveUniformName(p, location);
        if (name == null) {
            unmatchedUniformWrites++;
            return;
        }
        float[] existing = p.floatValues.get(name);
        if (existing != null && existing.length == 4) {
            if (Float.floatToRawIntBits(existing[0]) != Float.floatToRawIntBits(x)
                    || Float.floatToRawIntBits(existing[1]) != Float.floatToRawIntBits(y)
                    || Float.floatToRawIntBits(existing[2]) != Float.floatToRawIntBits(z)
                    || Float.floatToRawIntBits(existing[3]) != Float.floatToRawIntBits(w)) {
                existing[0] = x;
                existing[1] = y;
                existing[2] = z;
                existing[3] = w;
                p.bumpUniformVersion(name);
            }
        } else {
            p.floatValues.put(name, new float[] {x, y, z, w});
            p.bumpUniformVersion(name);
        }
        p.uniformWrites++;
    }

    /** Zero-allocation pointer form for {@code nglUniform*fv} / {@code nglUniformMatrix*fv}. */
    public static void uniformFloatPtr(int program, int location, int count, long valuesPtr, int components,
                                       boolean transpose, int matrixSide) {
        Program p = PROGRAMS.get(program);
        String name = resolveUniformName(p, location);
        if (name == null) {
            unmatchedUniformWrites++;
            return;
        }
        int total = count * components;
        float[] dst = p.floatValues.get(name);
        boolean fresh = dst == null || dst.length != total;
        if (fresh) {
            dst = new float[total];
        }
        boolean changed = fresh;
        if (!transpose || matrixSide <= 0) {
            for (int i = 0; i < total; i++) {
                float v = MemoryUtil.memGetFloat(valuesPtr + 4L * i);
                if (!changed && Float.floatToRawIntBits(dst[i]) != Float.floatToRawIntBits(v)) {
                    changed = true;
                }
                dst[i] = v;
            }
        } else {
            for (int m = 0; m < count; m++) {
                int base = m * components;
                for (int r = 0; r < matrixSide; r++) {
                    for (int c = 0; c < matrixSide; c++) {
                        float v = MemoryUtil.memGetFloat(valuesPtr + 4L * (base + r * matrixSide + c));
                        int dstIdx = base + c * matrixSide + r;
                        if (!changed && Float.floatToRawIntBits(dst[dstIdx]) != Float.floatToRawIntBits(v)) {
                            changed = true;
                        }
                        dst[dstIdx] = v;
                    }
                }
            }
        }
        if (fresh) {
            p.floatValues.put(name, dst);
        }
        if (changed) {
            p.bumpUniformVersion(name);
        }
        p.uniformWrites++;
    }

    /** {@code glUniform*i*}: the components (a sampler's unit), for the program currently bound. */
    public static void uniformInts(int program, int location, int[] values) {
        Program p = PROGRAMS.get(program);
        String name = resolveUniformName(p, location);
        if (name == null) {
            unmatchedUniformWrites++;
            return;
        }
        int[] existing = p.intValues.get(name);
        if (existing != null && existing.length == values.length) {
            boolean changed = false;
            for (int i = 0; i < values.length; i++) {
                if (existing[i] != values[i]) {
                    existing[i] = values[i];
                    changed = true;
                }
            }
            if (changed) {
                p.bumpUniformVersion(name);
            }
        } else {
            p.intValues.put(name, values);
            p.bumpUniformVersion(name);
        }
        p.uniformWrites++;
    }

    /** Zero-allocation {@code glUniform1i}. */
    public static void uniformInt1(int program, int location, int x) {
        Program p = PROGRAMS.get(program);
        String name = resolveUniformName(p, location);
        if (name == null) {
            unmatchedUniformWrites++;
            return;
        }
        int[] existing = p.intValues.get(name);
        if (existing != null && existing.length == 1) {
            if (existing[0] != x) {
                existing[0] = x;
                p.bumpUniformVersion(name);
            }
        } else {
            p.intValues.put(name, new int[] {x});
            p.bumpUniformVersion(name);
        }
        p.uniformWrites++;
    }

    /** Zero-allocation {@code glUniform2i}. */
    public static void uniformInt2(int program, int location, int x, int y) {
        Program p = PROGRAMS.get(program);
        String name = resolveUniformName(p, location);
        if (name == null) {
            unmatchedUniformWrites++;
            return;
        }
        int[] existing = p.intValues.get(name);
        if (existing != null && existing.length == 2) {
            if (existing[0] != x || existing[1] != y) {
                existing[0] = x;
                existing[1] = y;
                p.bumpUniformVersion(name);
            }
        } else {
            p.intValues.put(name, new int[] {x, y});
            p.bumpUniformVersion(name);
        }
        p.uniformWrites++;
    }

    /** Zero-allocation {@code glUniform3i}. */
    public static void uniformInt3(int program, int location, int x, int y, int z) {
        Program p = PROGRAMS.get(program);
        String name = resolveUniformName(p, location);
        if (name == null) {
            unmatchedUniformWrites++;
            return;
        }
        int[] existing = p.intValues.get(name);
        if (existing != null && existing.length == 3) {
            if (existing[0] != x || existing[1] != y || existing[2] != z) {
                existing[0] = x;
                existing[1] = y;
                existing[2] = z;
                p.bumpUniformVersion(name);
            }
        } else {
            p.intValues.put(name, new int[] {x, y, z});
            p.bumpUniformVersion(name);
        }
        p.uniformWrites++;
    }

    /** Zero-allocation {@code glUniform4i}. */
    public static void uniformInt4(int program, int location, int x, int y, int z, int w) {
        Program p = PROGRAMS.get(program);
        String name = resolveUniformName(p, location);
        if (name == null) {
            unmatchedUniformWrites++;
            return;
        }
        int[] existing = p.intValues.get(name);
        if (existing != null && existing.length == 4) {
            if (existing[0] != x || existing[1] != y || existing[2] != z || existing[3] != w) {
                existing[0] = x;
                existing[1] = y;
                existing[2] = z;
                existing[3] = w;
                p.bumpUniformVersion(name);
            }
        } else {
            p.intValues.put(name, new int[] {x, y, z, w});
            p.bumpUniformVersion(name);
        }
        p.uniformWrites++;
    }

    /** Zero-allocation pointer form for {@code nglUniform*iv}. */
    public static void uniformIntPtr(int program, int location, int count, long valuesPtr, int components) {
        Program p = PROGRAMS.get(program);
        String name = resolveUniformName(p, location);
        if (name == null) {
            unmatchedUniformWrites++;
            return;
        }
        int total = count * components;
        int[] dst = p.intValues.get(name);
        boolean fresh = dst == null || dst.length != total;
        if (fresh) {
            dst = new int[total];
        }
        boolean changed = fresh;
        for (int i = 0; i < total; i++) {
            int v = MemoryUtil.memGetInt(valuesPtr + 4L * i);
            if (!changed && dst[i] != v) {
                changed = true;
            }
            dst[i] = v;
        }
        if (fresh) {
            p.intValues.put(name, dst);
        }
        if (changed) {
            p.bumpUniformVersion(name);
        }
        p.uniformWrites++;
    }

    /** {@return the program for a GL id}, or null if the stub never saw it linked. */
    public static Program program(int id) {
        return PROGRAMS.get(id);
    }

    /**
     * {@return the link status a GL query for this program should answer}: TRUE when the link this registry
     * recorded compiled both stages, FALSE when it recorded why the program has no SPIR-V, and null when no
     * link was recorded for the id. The null case matters: a program whose link never reached this registry
     * (a route this class does not feed) must not be condemned by an absence it knows nothing about, so the
     * query surfaces keep their default answer there.
     */
    public static Boolean linkStatus(int id) {
        Program p = PROGRAMS.get(id);
        if (p == null || p.lowered == null) {
            return null;
        }
        return p.compiled();
    }

    /**
     * {@return the info-log text for a program whose recorded link failed}: the recorded failure, or null
     * when the program compiled or no link was recorded for the id.
     */
    public static String linkInfoLog(int id) {
        Program p = PROGRAMS.get(id);
        if (p == null || p.lowered == null || p.compiled()) {
            return null;
        }
        // The lowering's own reason names what the translator refused; the compile failure field is the
        // stage-prefixed shaderc message (or why no compile was attempted). Either is the log GL would have
        // carried - the field alone would say only "not lowered" for the lowering case.
        return !p.lowered.ok() && p.lowered.failure() != null ? p.lowered.failure() : p.compileFailure;
    }

    /** {@code glDeleteProgram}. Shaders are kept: GL deletes them only when detached, and mods reuse them. */
    public static void deleteProgram(int id) {
        Program removed = PROGRAMS.remove(id);
        if (removed != null) {
            // Sources whose deletion was deferred while this program referenced them are released once no
            // live program references them any more (item 328, V-04).
            for (int i = 0, n = removed.shaders.size(); i < n; i++) {
                int shader = removed.shaders.getInt(i);
                if (DELETED_SHADERS.contains(shader) && !referencedByLiveProgram(shader)) {
                    DELETED_SHADERS.remove(shader);
                    SHADER_SOURCES.remove(shader);
                    SHADER_STAGES.remove(shader);
                }
            }
        }
    }

    /**
     * {@code glDeleteShader} (item 328, V-04). An unreferenced shader's source is forgotten with it, so the
     * name's next owner starts empty as GL promises. A shader still referenced by a live program keeps its
     * source for that program (GL defers the deletion until detach) and is marked deleted instead, which is
     * what {@link #attach} and {@link #deleteProgram} consult.
     */
    public static void deleteShader(int id) {
        if (referencedByLiveProgram(id)) {
            DELETED_SHADERS.add(id);
        } else {
            DELETED_SHADERS.remove(id);
            SHADER_SOURCES.remove(id);
            SHADER_STAGES.remove(id);
        }
    }

    private static boolean referencedByLiveProgram(int shader) {
        for (Program p : PROGRAMS.values()) {
            if (p.shaders.contains(shader)) {
                return true;
            }
        }
        return false;
    }

    public static int unmatchedUniformWrites() {
        return unmatchedUniformWrites;
    }

    public static int locationNameCollisions() {
        return locationNameCollisions;
    }

    /** Test support: forget everything. */
    static void clear() {
        SHADER_SOURCES.clear();
        SHADER_STAGES.clear();
        PROGRAMS.clear();
        DELETED_SHADERS.clear();
        unmatchedUniformWrites = 0;
        locationNameCollisions = 0;
        GlsmPackState.clear();
    }
}
