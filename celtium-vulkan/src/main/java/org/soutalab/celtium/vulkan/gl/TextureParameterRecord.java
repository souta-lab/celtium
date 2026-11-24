package org.soutalab.celtium.vulkan.gl;

/**
 * The per-texture-object record of GL texture parameters, extracted from {@code GlsmFrontend} unchanged in
 * behaviour (refactor/giant-split-1): the recorded values, their per-name versions, the bounded census and the
 * query instrument live here, while the stub keeps the binding resolution that names the texture a call applies
 * to and the static doors the mirror reads through.
 * <p>
 * GL keeps the filter and wrap state on the texture object, and the game sets it through
 * {@code GlStateManager.glTexParameteri -> GL11.glTexParameteri}: TextureUtil.setTextureClamped/setTextureBlurMipmap
 * for every uploaded texture, AbstractTexture.setBlurMipmapDirect, Framebuffer's attachment filters and
 * RenderGlobal's sky texture. The stub used to no-op every setter and answer every query with one hardcoded pair,
 * so the mirror's sampler was built CLAMP_TO_EDGE for textures the game had asked to REPEAT. Vanilla depends on
 * the difference: GuiOptions.drawBackground and Cleanroom's patched GuiSlot.drawContainerBackground draw
 * textures/gui/options_background.png with UVs up to height/32, which tiles only under REPEAT - and that texture
 * is given exactly that by TextureUtil.uploadTextureImageAllocate(..., clamp=false) -&gt;
 * setTextureClamped(false) -&gt; glTexParameteri(GL_TEXTURE_WRAP_S/T, 0x2901).
 * <p>
 * The record is keyed by texture <i>name</i> and pname rather than by binding, because the reads that need it name
 * the texture: the mirror's wrap/filter query is the DSA glGetTextureParameter*, which carries the name, and it may
 * run on a different unit than the one that set the parameter. Deleted names are never reissued by the stub, so
 * a record left behind by glDeleteTextures cannot be read as a later texture's state.
 */
final class TextureParameterRecord {

    private static final int GL_TEXTURE_MAG_FILTER = 0x2800;
    private static final int GL_TEXTURE_MIN_FILTER = 0x2801;
    private static final int GL_TEXTURE_WRAP_S = 0x2802;
    private static final int GL_TEXTURE_WRAP_T = 0x2803;
    /** GL_TEXTURE_BORDER_COLOR is a four-component vector; a scalar record cannot hold it, so it is not recorded. */
    private static final int GL_TEXTURE_BORDER_COLOR = 0x1004;
    /** GL's initial state for a fresh texture object: the answer when the game never set the pname. */
    private static final int GL_LINEAR = 0x2601;
    private static final int GL_NEAREST_MIPMAP_LINEAR = 0x2702;
    private static final int GL_REPEAT = 0x2901;
    private static final int GL_CLAMP_TO_EDGE = 0x812F;
    private static final int GL_NEAREST = 0x2600;

    /**
     * Per-texture counter of recorded parameter changes, so a consumer can skip re-reading four parameters it already
     * read for this texture.
     * <p>
     * {@code VulkanGlTexture.confirmSamplerStateFromRecord} re-consults the wrap and filter records on every texture
     * lookup - four to six boxed map lookups per draw - because BH-23 requires a parameter changed after the first
     * draw to be seen. A version bumped here, read there, keeps that requirement (a change still re-confirms) while a
     * texture whose parameters nobody touched costs one array read.
     */
    private int[] texParamVersions = new int[1024];

    /** Bumps the recorded-parameter version of this texture name, growing the table as names appear. */
    private void celtium$bumpTextureParameterVersion(int textureName) {
        if (textureName <= 0) {
            return;
        }
        if (textureName >= this.texParamVersions.length) {
            int grown = Math.max(textureName + 1, this.texParamVersions.length * 2);
            this.texParamVersions = java.util.Arrays.copyOf(this.texParamVersions, grown);
        }
        this.texParamVersions[textureName]++;
    }

    /** {@return how many times this texture's recorded parameters changed}, 0 when the game never set one. */
    int version(int textureName) {
        if (textureName <= 0 || textureName >= this.texParamVersions.length) {
            return 0;
        }
        return this.texParamVersions[textureName];
    }

    /** Recorded values, keyed {@code (textureName << 32) | pname}. Bounded: past the limit the call is counted. */
    private final it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap texParams =
            new it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap();
    private final java.util.concurrent.atomic.AtomicInteger texParamCalls =
            new java.util.concurrent.atomic.AtomicInteger();
    private final java.util.concurrent.atomic.AtomicInteger texParamUnbound =
            new java.util.concurrent.atomic.AtomicInteger();
    private final java.util.concurrent.atomic.AtomicInteger texParamOverflow =
            new java.util.concurrent.atomic.AtomicInteger();
    private final java.util.concurrent.atomic.AtomicInteger texParamQueries =
            new java.util.concurrent.atomic.AtomicInteger();
    private static final int TEX_PARAM_RECORD_LIMIT = 1 << 16;
    /** The distinct (name,pname,value) triples the {@code CELTIUM_TEX_PARAM} instrument has named. Bounded. */
    private static final java.util.Set<String> TEX_PARAM_SHAPES =
            java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final java.util.Set<String> TEX_PARAM_QUERY_SHAPES =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    private static long texParamKey(int textureName, int pname) {
        return ((long) textureName << 32) | (pname & 0xFFFFFFFFL);
    }

    /** {@return how many distinct set shapes the instrument has named so far} - the stub's site guard reads this. */
    static int paramShapeCount() {
        return TEX_PARAM_SHAPES.size();
    }

    /** {@return how many parameter sets were recorded or refused} - the exit census prints only when positive. */
    int calls() {
        return this.texParamCalls.get();
    }

    /** {@return how many parameter queries were noted} - the exit census prints only when positive. */
    int queries() {
        return this.texParamQueries.get();
    }

    private static String texParamPname(int pname) {
        switch (pname) {
            case GL_TEXTURE_MAG_FILTER: return "MAG_FILTER";
            case GL_TEXTURE_MIN_FILTER: return "MIN_FILTER";
            case GL_TEXTURE_WRAP_S: return "WRAP_S";
            case GL_TEXTURE_WRAP_T: return "WRAP_T";
            default: return "pname";
        }
    }

    private static String texParamValue(int value) {
        switch (value) {
            case GL_REPEAT: return "REPEAT";
            case GL_CLAMP_TO_EDGE: return "CLAMP_TO_EDGE";
            case GL_LINEAR: return "LINEAR";
            case GL_NEAREST: return "NEAREST";
            case GL_NEAREST_MIPMAP_LINEAR: return "NEAREST_MIPMAP_LINEAR";
            case 0x2602: return "NEAREST_MIPMAP_NEAREST";
            case 0x2703: return "LINEAR_MIPMAP_LINEAR";
            case 0x2701: return "LINEAR_MIPMAP_NEAREST";
            default: return "0x" + Integer.toHexString(value);
        }
    }

    /**
     * Records one texture parameter. {@code textureName} is the texture the call applies to - the bound one for the
     * non-DSA forms, the explicit first argument for the DSA ones - and a call with no texture bound (which real GL
     * rejects with GL_INVALID_OPERATION) is counted rather than filed under a name the game never used.
     */
    void set(int textureName, int pname, int value, String site) {
        if (textureName <= 0) {
            this.texParamUnbound.incrementAndGet();
            return;
        }
        if (pname == GL_TEXTURE_BORDER_COLOR) {
            return;
        }
        long key = texParamKey(textureName, pname);
        if (!this.texParams.containsKey(key)
                && this.texParams.size() >= TEX_PARAM_RECORD_LIMIT) {
            this.texParamOverflow.incrementAndGet();
            return;
        }
        this.texParams.put(key, value);
        celtium$bumpTextureParameterVersion((int) (key >>> 32));
        int calls = this.texParamCalls.incrementAndGet();
        if (org.soutalab.celtium.vulkan.VulkanDiagnostics.withinLimit(TEX_PARAM_SHAPES.size(), 24)) {
            String shape = textureName + "/" + pname + "=" + value;
            if (TEX_PARAM_SHAPES.add(shape)) {
                System.out.println("[Celtium] CELTIUM_TEX_PARAM texture=" + textureName
                        + " pname=0x" + Integer.toHexString(pname) + " (" + texParamPname(pname) + ")"
                        + " value=0x" + Integer.toHexString(value) + " (" + texParamValue(value) + ")"
                        + " calls=" + calls + " distinct=" + TEX_PARAM_SHAPES.size()
                        + " site=[" + site + "]");
            }
        }
    }

    /** GL's own initial state for a pname: the answer when the game never set it on the texture. */
    static int defaultValue(int pname) {
        switch (pname) {
            case GL_TEXTURE_WRAP_S:
            case GL_TEXTURE_WRAP_T:
                return GL_REPEAT;
            case GL_TEXTURE_MAG_FILTER:
                return GL_LINEAR;
            case GL_TEXTURE_MIN_FILTER:
                return GL_NEAREST_MIPMAP_LINEAR;
            default:
                return 0;
        }
    }

    /**
     * {@return the value a texture parameter query answers}: the record when the game set this pname on this texture,
     * otherwise GL's own initial state for the four pnames a fresh texture has, and 0 for everything else.
     * <p>
     * The default is the honest stub answer rather than a safe one: a texture the game never parameterised really does
     * wrap by repeating in GL, which is why the options background tiles without anyone setting a wrap on it.
     * <p>
     * This record-based answer is unconditional: the former one-flag control for the settings-background defect,
     * which answered the values hardcoded before the record existed (wrap CLAMP_TO_EDGE, filters LINEAR), was
     * removed once the record side was adopted (item 396).
     */
    int valueOrDefault(int textureName, int pname) {
        if (textureName > 0) {
            long key = texParamKey(textureName, pname);
            if (this.texParams.containsKey(key)) {
                return this.texParams.get(key);
            }
        }
        return defaultValue(pname);
    }

    /**
     * {@return the filter or wrap value recorded for this texture and pname, or -1 when the game never set it}.
     * <p>
     * This is the mirror's door to the record: {@link #valueOrDefault} answers GL's default for an unset pname,
     * while a caller that wants to keep its own fallback (the mirror's clamp) can tell the two apart here.
     */
    int recordedValue(int textureName, int pname) {
        if (textureName <= 0) {
            return -1;
        }
        long key = texParamKey(textureName, pname);
        return this.texParams.containsKey(key) ? this.texParams.get(key) : -1;
    }

    /** {@return whether the game set this pname on this texture} - false for the GL defaults. */
    boolean isRecorded(int textureName, int pname) {
        return textureName > 0 && this.texParams.containsKey(texParamKey(textureName, pname));
    }

    /**
     * Drops this name's share of the record: its parameter version and every recorded value under the name.
     * <p>
     * The name's version belongs to the deleted object (item 328, V-04): a recycled name must not inherit its
     * predecessor's parameter version - the version feeds the sampler re-confirm comparison in VulkanGlTexture,
     * where a stale count could stand in for a confirmation the new object never had. The values go for the same
     * reason: TextureUtil.allocateTexture deletes a name and re-uploads to the same one, and leaving the parameters
     * behind handed the new object the old wrap/filter (measured: WRAP_S stayed CLAMP_TO_EDGE where the default is
     * REPEAT, #389); the records are also capped, so a long session of reloads would eventually stop recording
     * new ones.
     */
    void forget(int name) {
        if (name >= 0 && name < this.texParamVersions.length) {
            this.texParamVersions[name] = 0;
        }
        if (!this.texParams.isEmpty()) {
            long keySpace = ((long) name) << 32;
            it.unimi.dsi.fastutil.longs.LongIterator keys = this.texParams.keySet().iterator();
            while (keys.hasNext()) {
                long key = keys.nextLong();
                if ((key & 0xFFFFFFFF00000000L) == keySpace) {
                    keys.remove();
                }
            }
        }
    }

    /** The named texture parameters, bounded: the record census and the query answers. */
    String summary() {
        int repeat = 0;
        int clamp = 0;
        it.unimi.dsi.fastutil.ints.IntOpenHashSet textures = new it.unimi.dsi.fastutil.ints.IntOpenHashSet();
        it.unimi.dsi.fastutil.longs.LongIterator keys = this.texParams.keySet().iterator();
        while (keys.hasNext()) {
            long key = keys.nextLong();
            textures.add((int) (key >>> 32));
            int pname = (int) (key & 0xFFFFFFFFL);
            if (pname != GL_TEXTURE_WRAP_S && pname != GL_TEXTURE_WRAP_T) {
                continue;
            }
            int value = this.texParams.get(key);
            if (value == GL_REPEAT) {
                repeat++;
            } else if (value == GL_CLAMP_TO_EDGE) {
                clamp++;
            }
        }
        return "texture params: recorded=" + this.texParams.size() + " textures=" + textures.size()
                + " wrapRepeat=" + repeat + " wrapClamp=" + clamp
                + " calls=" + this.texParamCalls.get() + " unbound=" + this.texParamUnbound.get()
                + " overflow=" + this.texParamOverflow.get() + " queries=" + this.texParamQueries.get()
                + " wrapClampControl=" + false;
    }

    /**
     * One query answer, named once per distinct shape so a run's log says what the mirror actually asked for.
     * The {@code site} is supplied lazily: reading the stack is the expensive part of this instrument, so it is
     * only asked for when the shape is actually printed.
     */
    void noteQuery(String name, int textureName, int pname, int value, boolean recorded,
                   java.util.function.Supplier<String> site) {
        this.texParamQueries.incrementAndGet();
        if (!org.soutalab.celtium.vulkan.VulkanDiagnostics.withinLimit(TEX_PARAM_QUERY_SHAPES.size(), 12)) {
            return;
        }
        String shape = name + "/" + textureName + "/" + pname + "=" + value + "/" + recorded;
        if (TEX_PARAM_QUERY_SHAPES.add(shape)) {
            System.out.println("[Celtium] CELTIUM_TEX_PARAM_QUERY " + name
                    + " texture=" + textureName
                    + " pname=0x" + Integer.toHexString(pname) + " (" + texParamPname(pname) + ")"
                    + " value=0x" + Integer.toHexString(value) + " (" + texParamValue(value) + ")"
                    + " recorded=" + recorded
                    + " source=" + (recorded ? "stub-record" : "gl-default")
                    + " site=[" + site.get() + "]");
        }
    }
}
