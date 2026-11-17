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

    /**
     * Three {@code mat4} and nothing else: 192 bytes of std140 block. This is the shape GLSM's fixed-function vertex
     * generator emits before any optional uniform ({@code u_ModelViewMatrix}, {@code u_ProjectionMatrix},
     * {@code u_MVPMatrix}), and the block every Demonica leg had refused under the old 128-byte ceiling.
     */
    private static final String THREE_MATRIX_FRAGMENT = """
            #version 120
            uniform mat4 modelView;
            uniform mat4 projection;
            uniform mat4 mvp;
            void main() { gl_FragColor = vec4(1.0); }
            """;

    @Test
    void acceptsABlockUpToTheSelectedDevicesPushConstantLimit() {
        ModPushConstantLimit.noteDevice(256);
        try {
            assertEquals(256, ModPushConstantLimit.limit());
            ModGlslTranslator.Result result = ModGlslTranslator.translate(QUAD_VERTEX, THREE_MATRIX_FRAGMENT);
            assertNull(result.failure(), () -> "unexpected failure: " + result.failure());
            assertEquals(192, result.uniformBlockSize());
            assertTrue(result.fragment().contains("layout(push_constant, std140) uniform CeltiumModUniforms"));
            // Accepted is not the same as compilable: the point of raising the ceiling is that shaderc now gets to see
            // the block, so the stage is compiled here rather than only counted.
            assertCompiles(result.fragment(), Shaderc.shaderc_fragment_shader);
        } finally {
            ModPushConstantLimit.forgetDeviceForTest();
        }
    }

    @Test
    void refusesABlockAboveTheSelectedDevicesLimitNamingBothNumbers() {
        ModPushConstantLimit.noteDevice(256);
        try {
            // Five mat4: 320 bytes, above this device's 256 and above the 128-byte minimum too, so the refusal is the
            // device's own limit and not the fallback - and it must name both numbers.
            String vertex = """
                    #version 120
                    uniform mat4 a, b, c, d, e;
                    void main() { gl_Position = vec4(0.0); }
                    """;
            ModGlslTranslator.Result result = ModGlslTranslator.translate(vertex,
                    "void main() { gl_FragColor = vec4(1.0); }");
            assertFalse(result.ok());
            assertTrue(result.failure().contains("320"), result.failure());
            assertTrue(result.failure().contains("256"), result.failure());
            assertTrue(result.failure().contains("maxPushConstantsSize"), result.failure());
        } finally {
            ModPushConstantLimit.forgetDeviceForTest();
        }
    }

    @Test
    void keepsTheGuaranteedMinimumWhileNoDeviceIsSelected() {
        ModPushConstantLimit.forgetDeviceForTest();
        assertEquals(128, ModPushConstantLimit.limit());
        assertTrue(ModPushConstantLimit.source().contains("no device selected"), ModPushConstantLimit.source());
        ModGlslTranslator.Result result = ModGlslTranslator.translate(QUAD_VERTEX, THREE_MATRIX_FRAGMENT);
        assertFalse(result.ok(), "a 192-byte block must still be refused before a device is selected");
        assertTrue(result.failure().contains("192"), result.failure());
        assertTrue(result.failure().contains("128"), result.failure());
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

    @Test
    void lowersFormatlessFragmentStorageImagesWithoutRenamingImageBuiltins() {
        String fragment = "uniform image2D colorimg0;\nvoid main() { vec4 v = imageLoad(colorimg0, ivec2(0)); "
                + "imageStore(colorimg0, ivec2(0), v); gl_FragColor = v; }";
        ModGlslTranslator.Result result = ModGlslTranslator.translate(QUAD_VERTEX, fragment);
        assertTrue(result.ok(), result.failure());
        assertEquals(List.of(new ModGlslTranslator.ImageBinding("colorimg0", "image2D", 0)), result.images());
        assertTrue(result.fragment().contains("layout(set = 0, binding = 0) uniform image2D colorimg0;"));
        assertTrue(result.fragment().contains("imageLoad(colorimg0"));
        assertTrue(result.fragment().contains("imageStore(colorimg0"));
    }

    @Test
    void lowersMultipleDrawBuffersToTheirOwnOutputs() {
        String fragment = """
                #version 120
                void main() {
                    gl_FragData[0] = vec4(1.0, 0.0, 0.0, 1.0);
                    gl_FragData[1] = vec4(0.0, 1.0, 0.0, 1.0);
                    gl_FragData[2] = vec4(0.0, 0.0, 1.0, 1.0);
                }
                """;
        ModGlslTranslator.Result result = ModGlslTranslator.translate(QUAD_VERTEX, fragment);
        assertTrue(result.ok(), result.failure());
        assertEquals(3, result.fragOutputCount());
        assertTrue(result.fragment().contains("layout(location = 0) out vec4 celtium_FragColor;"));
        assertTrue(result.fragment().contains("layout(location = 1) out vec4 celtium_FragData1;"));
        assertTrue(result.fragment().contains("layout(location = 2) out vec4 celtium_FragData2;"));
        assertTrue(result.fragment().contains("celtium_FragData1 = vec4(0.0, 1.0, 0.0, 1.0);"));
        assertFalse(result.fragment().contains("gl_FragData"), result.fragment());
        assertCompiles(result.fragment(), Shaderc.shaderc_fragment_shader);
    }

    @Test
    void refusesDrawBuffersPastTheLoweredCeilingAndDynamicIndices() {
        ModGlslTranslator.Result past = ModGlslTranslator.translate(QUAD_VERTEX,
                "void main() { gl_FragData[8] = vec4(1.0); }");
        assertFalse(past.ok());
        assertTrue(past.failure().contains("gl_FragData[8]"), past.failure());

        ModGlslTranslator.Result dynamic = ModGlslTranslator.translate(QUAD_VERTEX,
                "uniform int i;\nvoid main() { gl_FragData[i] = vec4(1.0); }");
        assertFalse(dynamic.ok());
        assertTrue(dynamic.failure().contains("non-constant"), dynamic.failure());
    }

    @Test
    void namesUnsupportedImageShapesAndStages() {
        assertTrue(ModGlslTranslator.translate("uniform image2D vertexImage; void main(){gl_Position=vec4(0.0);}",
                "void main(){gl_FragColor=vec4(1.0);}").failure().contains("vertexImage"));
        assertTrue(ModGlslTranslator.translate(QUAD_VERTEX,
                "uniform image3D volume; void main(){gl_FragColor=vec4(1.0);}").failure().contains("volume"));
        assertTrue(ModGlslTranslator.translate(QUAD_VERTEX,
                "uniform image2D images[2]; void main(){gl_FragColor=vec4(1.0);}").failure().contains("images"));
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
