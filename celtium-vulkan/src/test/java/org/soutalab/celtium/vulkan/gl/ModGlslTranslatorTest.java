package org.soutalab.celtium.vulkan.gl;

import org.junit.jupiter.api.Test;
import org.lwjgl.util.shaderc.Shaderc;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The lowering of a mod's legacy GLSL program is checked by compiling its output with shaderc for Vulkan 1.2, and by
 * pinning the interface (locations, std140 offsets, sampler bindings) the native pass will fill.
 * <p>
 * The programs here are written for the test in the shape GTCEu's bloom passes have (a {@code #version 120}
 * full-screen quad whose vertex stage takes one {@code attribute vec2}, a fragment stage of {@code texture2D} reads and
 * {@code gl_FragColor}, float and vec2 uniforms, several samplers), not copied from the mod.
 */
class ModGlslTranslatorTest {

    private static final String QUAD_VERTEX = """
            #version 120
            attribute vec2 position;
            varying vec2 uv; // the full-screen quad's texture coordinate
            void main(void) {
                gl_Position = vec4(position, 0.0, 1.0);
                uv = position * 0.5 + 0.5;
            }
            """;

    private static final String COMBINE_FRAGMENT = """
            #version 120
            varying vec2 uv;
            uniform sampler2D first;
            uniform sampler2D second;
            uniform float strength;
            uniform vec2 texel, direction;
            /* weights for a five-tap kernel */
            uniform float weights[5];
            float shaped(const in float f) { return mix(f, 1.2 - f, strength); }
            void main() {
                vec4 sum = texture2D(first, uv) * weights[0];
                for (int i = 1; i < 5; i++) {
                    sum += texture2D(second, uv + direction * texel * float(i)) * weights[i];
                }
                gl_FragColor = sum * shaped(0.8);
            }
            """;

    @Test
    void lowersTheQuadPassAndShadercCompilesBothStages() {
        ModGlslTranslator.Result result = ModGlslTranslator.translate(QUAD_VERTEX, COMBINE_FRAGMENT);
        assertNull(result.failure(), () -> "unexpected failure: " + result.failure());
        assertEquals(List.of(new ModGlslTranslator.Attribute("position", "vec2", 0)), result.attributes());
        assertEquals(List.of("uv"), result.varyings());
        assertEquals(List.of(new ModGlslTranslator.SamplerBinding("first", "sampler2D", 0),
                new ModGlslTranslator.SamplerBinding("second", "sampler2D", 1)), result.samplers());
        // std140: float at 0, vec2s at 8 and 16, the float[5] array at 32 with a 16-byte stride.
        assertEquals(List.of(new ModGlslTranslator.UniformMember("strength", "float", 0, 0, 4),
                new ModGlslTranslator.UniformMember("texel", "vec2", 0, 8, 8),
                new ModGlslTranslator.UniformMember("direction", "vec2", 0, 16, 8),
                new ModGlslTranslator.UniformMember("weights", "float", 5, 32, 80)), result.uniforms());
        assertEquals(112, result.uniformBlockSize());
        assertCompiles(result.vertex(), Shaderc.shaderc_vertex_shader);
        assertCompiles(result.fragment(), Shaderc.shaderc_fragment_shader);
    }

    @Test
    void lowersTheFixedFunctionInputsAModStillReads() {
        String vertex = """
                #version 110
                void main() {
                    gl_TexCoord[0] = gl_MultiTexCoord0;
                    gl_FrontColor = gl_Color;
                    gl_Position = ftransform();
                }
                """;
        String fragment = """
                #version 110
                uniform sampler2D tex;
                void main() {
                    gl_FragData[0] = texture2D(tex, gl_TexCoord[0].st) * gl_Color;
                }
                """;
        ModGlslTranslator.Result result = ModGlslTranslator.translate(vertex, fragment);
        assertNull(result.failure(), () -> "unexpected failure: " + result.failure());
        assertTrue(result.attributes().stream().anyMatch(a -> a.name().equals("celtium_Vertex")));
        assertTrue(result.attributes().stream().anyMatch(a -> a.name().equals("celtium_Color")));
        assertTrue(result.uniforms().stream().anyMatch(u -> u.name().equals("celtium_ModelViewProjectionMatrix")
                && u.size() == 64));
        assertCompiles(result.vertex(), Shaderc.shaderc_vertex_shader);
        assertCompiles(result.fragment(), Shaderc.shaderc_fragment_shader);
    }

    @Test
    void reportsWhatItDoesNotLowerInsteadOfGuessing() {
        String fragment = """
                #version 120
                uniform sampler2D tex;
                void main() { gl_FragColor = texture2D(tex, gl_TexCoord[1].st); }
                """;
        ModGlslTranslator.Result result = ModGlslTranslator.translate(QUAD_VERTEX, fragment);
        assertFalse(result.ok());
        assertNotNull(result.failure());
        assertTrue(result.failure().contains("gl_TexCoord[0]"), result.failure());

        ModGlslTranslator.Result clash = ModGlslTranslator.translate(
                "uniform float k;\nvoid main() { gl_Position = vec4(k); }",
                "uniform vec2 k;\nvoid main() { gl_FragColor = vec4(k, 0.0, 1.0); }");
        assertFalse(clash.ok());
        assertTrue(clash.failure().contains("uniform k"), clash.failure());
    }

    /**
     * A mod's real shaders, when a directory of them is named by {@code CELTIUM_MOD_SHADER_DIR}: every {@code *.frag}
     * paired with the directory's single {@code *.vert} (the shape GTCEu's {@code Shaders} uses) must lower and
     * compile, or name what it does not lower. Skipped without the variable - the sources are the mod's, not ours.
     */
    @Test
    void lowersAModsOwnShadersWhenTheyAreProvided() throws java.io.IOException {
        String dir = System.getenv("CELTIUM_MOD_SHADER_DIR");
        org.junit.jupiter.api.Assumptions.assumeTrue(dir != null && !dir.isBlank(), "CELTIUM_MOD_SHADER_DIR not set");
        java.nio.file.Path root = java.nio.file.Path.of(dir);
        java.util.List<java.nio.file.Path> vertices;
        java.util.List<java.nio.file.Path> fragments;
        try (java.util.stream.Stream<java.nio.file.Path> files = java.nio.file.Files.list(root)) {
            java.util.List<java.nio.file.Path> all = files.sorted().toList();
            vertices = all.stream().filter(f -> f.toString().endsWith(".vert")).toList();
            fragments = all.stream().filter(f -> f.toString().endsWith(".frag")).toList();
        }
        assertEquals(1, vertices.size(), "one vertex stage expected in " + root);
        String vertex = java.nio.file.Files.readString(vertices.get(0));
        StringBuilder report = new StringBuilder();
        int lowered = 0;
        for (java.nio.file.Path fragmentPath : fragments) {
            String fragmentSource = java.nio.file.Files.readString(fragmentPath);
            ModGlslTranslator.Result result = ModGlslTranslator.translate(vertex, fragmentSource);
            if (!result.ok() && result.failure().contains("has no vertex output")) {
                // A fragment-only program: it reads fixed-function varyings the directory's vertex stage never writes.
                result = ModGlslTranslator.translate(null, fragmentSource);
            }
            if (!result.ok()) {
                report.append(fragmentPath.getFileName()).append(": not lowered: ").append(result.failure())
                        .append('\n');
                continue;
            }
            assertCompiles(result.vertex(), Shaderc.shaderc_vertex_shader);
            assertCompiles(result.fragment(), Shaderc.shaderc_fragment_shader);
            lowered++;
            report.append(fragmentPath.getFileName()).append(": lowered, samplers=").append(result.samplers().size())
                    .append(" uniformBytes=").append(result.uniformBlockSize()).append('\n');
        }
        System.out.println("[ModGlslTranslatorTest] " + lowered + "/" + fragments.size() + " lowered\n" + report);
    }

    private static void assertCompiles(String source, int kind) {
        long compiler = Shaderc.shaderc_compiler_initialize();
        long options = Shaderc.shaderc_compile_options_initialize();
        long result = 0L;
        try {
            Shaderc.shaderc_compile_options_set_target_env(options, Shaderc.shaderc_target_env_vulkan,
                    Shaderc.shaderc_env_version_vulkan_1_2);
            result = Shaderc.shaderc_compile_into_spv(compiler, source, kind, "mod-program", "main", options);
            int status = Shaderc.shaderc_result_get_compilation_status(result);
            String log = Shaderc.shaderc_result_get_error_message(result);
            assertEquals(Shaderc.shaderc_compilation_status_success, status,
                    () -> "shaderc rejected the lowered stage: " + log + "\n" + source);
            assertTrue(Shaderc.shaderc_result_get_length(result) > 20);
        } finally {
            if (result != 0L) {
                Shaderc.shaderc_result_release(result);
            }
            Shaderc.shaderc_compile_options_release(options);
            Shaderc.shaderc_compiler_release(compiler);
        }
    }
}
