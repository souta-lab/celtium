package org.soutalab.celtium.vulkan.gl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The mod-owned GL programs the stub has seen: their sources, their link result as Vulkan GLSL, and the uniform values
 * {@code glUniform*} set on them.
 * <p>
 * Under GL-less a mod's {@code glShaderSource}/{@code glAttachShader}/{@code glLinkProgram}/{@code glUniform*} used to
 * be listed no-ops, so a program bound for a draw was an empty id and its batch could only be refused. This keeps what
 * a native pass needs to run the program instead: the lowered stages ({@link ModGlslTranslator}) and the values to pack
 * into the uniform block and to pick the sampled textures.
 * <p>
 * Client thread only, like every GL call the stub receives. The stage of a shader is read from its source (a stage that
 * writes {@code gl_Position} is the vertex stage) rather than from {@code glCreateShader}'s argument, so the stub's
 * name generation is not touched.
 * <p>
 * Uniform locations are the stub's stable name hash ({@code CeltiumGllessHint.glUniformLocation}: 31-hash of the
 * name's bytes, masked to 30 bits), so a location maps back to a name once the program is linked.
 */
public final class ModProgramRegistry {

    /** A linked program: the lowering result and the values recorded for its uniforms, by GL name. */
    public static final class Program {
        final int id;
        final List<Integer> shaders = new ArrayList<>();
        ModGlslTranslator.Result lowered;
        final Map<Integer, String> nameByLocation = new HashMap<>();
        final Map<String, float[]> floatValues = new HashMap<>();
        final Map<String, int[]> intValues = new HashMap<>();
        int uniformWrites;
        /** The compiled stages, when shaderc was available at link; null otherwise. */
        byte[] vertexSpirv;
        byte[] fragmentSpirv;
        String compileFailure;

        Program(int id) {
            this.id = id;
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

    private static final Map<Integer, String> SHADER_SOURCES = new HashMap<>();
    private static final Map<Integer, Program> PROGRAMS = new HashMap<>();
    /** Uniform writes to a location no linked program names: counted, so a lost value is visible. */
    private static int unmatchedUniformWrites;
    private static boolean reportedUnavailable;

    private ModProgramRegistry() {
    }

    /** {@code glShaderSource}: the stage's text, concatenated as GL concatenates the strings. */
    public static void shaderSource(int shader, String source) {
        SHADER_SOURCES.put(Integer.valueOf(shader), source);
    }

    /** {@code glAttachShader}. */
    public static void attach(int program, int shader) {
        Program p = PROGRAMS.computeIfAbsent(Integer.valueOf(program), Program::new);
        if (!p.shaders.contains(Integer.valueOf(shader))) {
            p.shaders.add(Integer.valueOf(shader));
        }
    }

    /** {@code glLinkProgram}: lowers the attached stages, and names the result once per program. */
    public static Program link(int program) {
        Program p = PROGRAMS.computeIfAbsent(Integer.valueOf(program), Program::new);
        String vertex = null;
        String fragment = null;
        for (Integer shader : p.shaders) {
            String source = SHADER_SOURCES.get(shader);
            if (source == null) {
                continue;
            }
            if (source.matches("(?s).*\\bgl_Position\\b.*")) {
                vertex = source;
            } else {
                fragment = source;
            }
        }
        p.lowered = fragment == null
                ? new ModGlslTranslator.Result(null, null, List.of(), List.of(), 0, List.of(), List.of(),
                        "no fragment stage attached (" + p.shaders.size() + " shaders)")
                : ModGlslTranslator.translate(vertex, fragment);
        p.nameByLocation.clear();
        if (p.lowered.ok()) {
            for (ModGlslTranslator.UniformMember member : p.lowered.uniforms()) {
                mapName(p, member.name());
            }
            for (ModGlslTranslator.SamplerBinding sampler : p.lowered.samplers()) {
                mapName(p, sampler.name());
            }
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
                try {
                    p.vertexSpirv = ModShaderCompiler.compile(p.lowered.vertex(), true);
                    p.fragmentSpirv = ModShaderCompiler.compile(p.lowered.fragment(), false);
                } catch (RuntimeException failure) {
                    p.vertexSpirv = null;
                    p.fragmentSpirv = null;
                    p.compileFailure = failure.getMessage();
                }
            }
        }
        System.out.println("[Celtium] CELTIUM_MOD_PROGRAM_LINKED program=" + program
                + " compiled=" + p.compiled()
                + (p.compiled() ? " spirvBytes=" + (p.vertexSpirv.length + p.fragmentSpirv.length)
                        : " compileFailure=" + p.compileFailure)
                + (p.lowered.ok()
                        ? " lowered=true samplers=" + p.lowered.samplers().size() + " uniformBytes="
                                + p.lowered.uniformBlockSize() + " vertex=" + (vertex == null ? "fixed-function" : "own")
                        : " lowered=false reason=" + p.lowered.failure()));
        return p;
    }

    private static void mapName(Program p, String name) {
        p.nameByLocation.put(Integer.valueOf(location(name)), name);
        p.nameByLocation.put(Integer.valueOf(location(name + "[0]")), name);
    }

    /** The stub's location for a uniform name; must stay equal to {@code CeltiumGllessHint.glUniformLocation}. */
    public static int location(String name) {
        int hash = 0;
        byte[] bytes = name.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        for (byte b : bytes) {
            hash = 31 * hash + b;
        }
        return hash & 0x3FFFFFFF;
    }

    /** {@code glUniform*f*}/{@code glUniformMatrix*}: the components, for the program currently bound. */
    public static void uniformFloats(int program, int location, float[] values) {
        Program p = PROGRAMS.get(Integer.valueOf(program));
        String name = p == null ? null : p.nameByLocation.get(Integer.valueOf(location));
        if (name == null) {
            unmatchedUniformWrites++;
            return;
        }
        p.floatValues.put(name, values);
        p.uniformWrites++;
    }

    /** {@code glUniform*i*}: the components (a sampler's unit), for the program currently bound. */
    public static void uniformInts(int program, int location, int[] values) {
        Program p = PROGRAMS.get(Integer.valueOf(program));
        String name = p == null ? null : p.nameByLocation.get(Integer.valueOf(location));
        if (name == null) {
            unmatchedUniformWrites++;
            return;
        }
        p.intValues.put(name, values);
        p.uniformWrites++;
    }

    /** {@return the program for a GL id}, or null if the stub never saw it linked. */
    public static Program program(int id) {
        return PROGRAMS.get(Integer.valueOf(id));
    }

    /** {@code glDeleteProgram}. Shaders are kept: GL deletes them only when detached, and mods reuse them. */
    public static void deleteProgram(int id) {
        PROGRAMS.remove(Integer.valueOf(id));
    }

    public static int unmatchedUniformWrites() {
        return unmatchedUniformWrites;
    }

    /** Test support: forget everything. */
    static void clear() {
        SHADER_SOURCES.clear();
        PROGRAMS.clear();
        unmatchedUniformWrites = 0;
    }
}
