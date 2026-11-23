package org.soutalab.celtium.vulkan.gl;

import org.junit.jupiter.api.Test;
import org.lwjgl.util.shaderc.Shaderc;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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
            assertFalse(result.uniformsViaBuffer());
            assertTrue(result.fragment().contains("layout(push_constant, std140) uniform CeltiumModUniforms"));
            // Accepted is not the same as compilable: the point of raising the ceiling is that shaderc now gets to see
            // the block, so the stage is compiled here rather than only counted.
            assertCompiles(result.fragment(), Shaderc.shaderc_fragment_shader);
        } finally {
            ModPushConstantLimit.forgetDeviceForTest();
        }
    }

    /**
     * The shape GLSM's fixed-function generator emits (written for the test in that shape, not copied from the
     * mod): GLSL 330 core - attributes carrying their own {@code layout(location = N)}, plain {@code in}/{@code out}
     * varyings, the fragment stage's own {@code out vec4}, a {@code texture()} call, and the three matrices of the
     * FFP vertex stage (192 bytes of push block). The old declaration recognition saw none of the {@code in}/{@code out}
     * globals, so they reached shaderc with no location and SPIR-V refused the program (leg 16, program=16).
     */
    private static final String FFP_VERTEX = """
            #version 330 core
            layout(location = 0) in vec3 a_Position;
            layout(location = 1) in vec4 a_Color;
            layout(location = 2) in vec4 a_TexCoord0;
            uniform mat4 u_ModelViewMatrix;
            uniform mat4 u_ProjectionMatrix;
            uniform mat4 u_MVPMatrix;
            out vec4 v_Color;
            out vec4 v_TexCoord0;
            void main() {
                gl_Position = u_MVPMatrix * vec4(a_Position, 1.0);
                v_Color = a_Color;
                v_TexCoord0 = a_TexCoord0;
            }
            """;

    private static final String FFP_FRAGMENT = """
            #version 330 core
            in vec4 v_Color;
            in vec4 v_TexCoord0;
            uniform sampler2D u_Sampler0;
            out vec4 fragColor;
            void main() {
                fragColor = texture(u_Sampler0, v_TexCoord0.st) * v_Color;
            }
            """;

    @Test
    void lowersModernInOutGlobalsAndShadercCompilesBothStages() {
        ModPushConstantLimit.noteDevice(256);
        try {
            ModGlslTranslator.Result result = ModGlslTranslator.translate(FFP_VERTEX, FFP_FRAGMENT);
            assertNull(result.failure(), () -> "unexpected failure: " + result.failure());
            // The attributes keep the locations the source declared: the mod's own vertex setup binds by them.
            assertEquals(List.of(new ModGlslTranslator.Attribute("a_Position", "vec3", 0),
                    new ModGlslTranslator.Attribute("a_Color", "vec4", 1),
                    new ModGlslTranslator.Attribute("a_TexCoord0", "vec4", 2)), result.attributes());
            assertEquals(List.of("v_Color", "v_TexCoord0"), result.varyings());
            assertEquals(192, result.uniformBlockSize());
            assertEquals(1, result.fragOutputCount());
            assertTrue(result.vertex().contains("layout(location = 0) out vec4 v_Color;"), result.vertex());
            assertTrue(result.vertex().contains("layout(location = 1) out vec4 v_TexCoord0;"), result.vertex());
            assertTrue(result.fragment().contains("layout(location = 0) in vec4 v_Color;"), result.fragment());
            assertTrue(result.fragment().contains("layout(location = 0) out vec4 fragColor;"), result.fragment());
            // The stage declared its own output, so the lowered gl_FragColor output is not emitted beside it.
            assertFalse(result.fragment().contains("celtium_FragColor"), result.fragment());
            assertCompiles(result.vertex(), Shaderc.shaderc_vertex_shader);
            assertCompiles(result.fragment(), Shaderc.shaderc_fragment_shader);
        } finally {
            ModPushConstantLimit.forgetDeviceForTest();
        }
    }

    @Test
    void honoursAnExplicitVaryingLocationFromEitherStageAndKeepsInterpolation() {
        String vertex = """
                #version 330 core
                layout(location = 0) in vec3 a_Position;
                flat out vec2 v_LineStart;
                out vec4 v_Color;
                void main() {
                    gl_Position = vec4(a_Position, 1.0);
                    v_LineStart = vec2(0.0);
                    v_Color = vec4(1.0);
                }
                """;
        String fragment = """
                #version 330 core
                layout(location = 3) flat in vec2 v_LineStart;
                in vec4 v_Color;
                out vec4 fragColor;
                void main() { fragColor = v_Color + vec4(v_LineStart, 0.0, 1.0); }
                """;
        ModGlslTranslator.Result result = ModGlslTranslator.translate(vertex, fragment);
        assertNull(result.failure(), () -> "unexpected failure: " + result.failure());
        // The fragment stage's number binds the pair; the unnumbered varying takes the first free location.
        assertTrue(result.vertex().contains("layout(location = 3) flat out vec2 v_LineStart;"), result.vertex());
        assertTrue(result.fragment().contains("layout(location = 3) flat in vec2 v_LineStart;"), result.fragment());
        assertTrue(result.vertex().contains("layout(location = 0) out vec4 v_Color;"), result.vertex());
        assertCompiles(result.vertex(), Shaderc.shaderc_vertex_shader);
        assertCompiles(result.fragment(), Shaderc.shaderc_fragment_shader);
    }

    @Test
    void refusesConflictingVaryingLocationsAcrossStages() {
        ModGlslTranslator.Result result = ModGlslTranslator.translate(
                "#version 330 core\nlayout(location = 1) out vec4 v_Data;\n"
                        + "void main() { gl_Position = vec4(0.0); v_Data = vec4(1.0); }",
                "#version 330 core\nlayout(location = 2) in vec4 v_Data;\nout vec4 fragColor;\n"
                        + "void main() { fragColor = v_Data; }");
        assertFalse(result.ok());
        assertTrue(result.failure().contains("v_Data"), result.failure());
        assertTrue(result.failure().contains("location 1"), result.failure());
        assertTrue(result.failure().contains("2 in the fragment stage"), result.failure());
    }

    @Test
    void lowersABlockAboveTheSelectedDevicesLimitThroughTheUniformBuffer() {
        ModPushConstantLimit.noteDevice(256);
        try {
            // Five mat4: 320 bytes, above this device's 256. The limit is a property of the push route, not of
            // the block, so the program lowers with the block as a uniform buffer at the set's first free
            // binding (no samplers or images here, so binding 0) instead of being refused - the shape
            // Demonica's 480/496-byte deferred programs and 1568-byte gbuffers programs need.
            String vertex = """
                    #version 120
                    uniform mat4 a, b, c, d, e;
                    void main() { gl_Position = a * vec4(0.0); }
                    """;
            ModGlslTranslator.Result result = ModGlslTranslator.translate(vertex,
                    "void main() { gl_FragColor = vec4(1.0); }");
            assertNull(result.failure(), () -> "unexpected failure: " + result.failure());
            assertEquals(320, result.uniformBlockSize());
            assertTrue(result.uniformsViaBuffer());
            assertTrue(result.vertex().contains(
                    "layout(set = 0, binding = 0, std140) uniform CeltiumModUniforms"), result.vertex());
            assertFalse(result.vertex().contains("push_constant"), result.vertex());
            assertCompiles(result.vertex(), Shaderc.shaderc_vertex_shader);
            assertCompiles(result.fragment(), Shaderc.shaderc_fragment_shader);
        } finally {
            ModPushConstantLimit.forgetDeviceForTest();
        }
    }

    @Test
    void namesTheUniformBufferBindingAfterTheSamplersAndImages() {
        ModPushConstantLimit.noteDevice(256);
        try {
            // One sampler: the block's binding is 1, the index the pipeline's descriptor layout declares after
            // the sampler bindings. The two sides must agree or the set binds the block where the shader reads
            // a texture.
            String fragment = """
                    #version 120
                    uniform sampler2D tex;
                    uniform mat4 a, b, c, d, e;
                    void main() { gl_FragColor = texture2D(tex, vec2(0.0)) + a[0] + b[0] + c[0] + d[0] + e[0]; }
                    """;
            ModGlslTranslator.Result result = ModGlslTranslator.translate(QUAD_VERTEX, fragment);
            assertNull(result.failure(), () -> "unexpected failure: " + result.failure());
            assertEquals(320, result.uniformBlockSize());
            assertTrue(result.uniformsViaBuffer());
            assertEquals(1, result.samplers().size());
            assertTrue(result.fragment().contains(
                    "layout(set = 0, binding = 1, std140) uniform CeltiumModUniforms"), result.fragment());
            assertCompiles(result.vertex(), Shaderc.shaderc_vertex_shader);
            assertCompiles(result.fragment(), Shaderc.shaderc_fragment_shader);
        } finally {
            ModPushConstantLimit.forgetDeviceForTest();
        }
    }

    @Test
    void routesByTheGuaranteedMinimumWhileNoDeviceIsSelected() {
        ModPushConstantLimit.forgetDeviceForTest();
        assertEquals(128, ModPushConstantLimit.limit());
        assertTrue(ModPushConstantLimit.source().contains("no device selected"), ModPushConstantLimit.source());
        // The threshold still selects the route before a device is selected - a 192-byte block takes the
        // buffer route, which any device can carry, rather than being refused until selection happens.
        ModGlslTranslator.Result result = ModGlslTranslator.translate(QUAD_VERTEX, THREE_MATRIX_FRAGMENT);
        assertNull(result.failure(), () -> "unexpected failure: " + result.failure());
        assertEquals(192, result.uniformBlockSize());
        assertTrue(result.uniformsViaBuffer());
        assertTrue(result.fragment().contains(
                "layout(set = 0, binding = 0, std140) uniform CeltiumModUniforms"), result.fragment());
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
    void lowersNormalScaleWithTheNoRescaleDefault() {
        // Twilight Forest 3.15.1's standard_texcoord2.vert, verbatim: the vertex stage of the firefly /
        // aurora / spiral / pulsing / starburst / shield programs. It reads gl_NormalScale into a varying,
        // and until this lowering the leftover check refused the link ("built-in gl_NormalScale is not
        // lowered", measured in the Nostalgic Hardcore pack's session A log, item 643).
        String vertex = """
                #version 120

                varying vec3 position;
                varying vec3 worldPos;
                varying vec2 texCoord0;
                varying float scale;
                varying vec4 colorIn;

                void main() {
                    gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;
                    position = (gl_ModelViewMatrix * gl_Vertex).xyz;

                    worldPos = gl_Vertex.xyz;
                    scale = gl_NormalScale;

                    texCoord0 = vec2(gl_MultiTexCoord0);

                    colorIn = gl_Color;
                }
                """;
        // The fragment half in the shape of firefly.frag: the varyings it declares and reads.
        String fragment = """
                #version 120
                uniform sampler2D zero;
                uniform float time;
                varying vec3 position;
                varying vec3 worldPos;
                varying vec2 texCoord0;
                void main() {
                    vec4 color = texture2D(zero, texCoord0);
                    gl_FragColor = vec4(color.xyz, color.a * sin(time + worldPos.x + position.y));
                }
                """;
        ModGlslTranslator.Result result = ModGlslTranslator.translate(vertex, fragment);
        assertNull(result.failure(), () -> "unexpected failure: " + result.failure());
        assertTrue(result.uniforms().stream().anyMatch(u -> u.name().equals("celtium_NormalScale")
                && u.type().equals("float") && u.size() == 4));
        assertArrayEquals(new float[] {1.0f}, result.uniformDefaults().get("celtium_NormalScale"));
        assertTrue(result.vertex().contains("celtium_NormalScale"));
        assertFalse(result.vertex().contains("gl_NormalScale"));
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

    /** U+0000, the terminator Iris's {@code GlShader} appends ({@code glShaderSource(handle, src + '\0')}). */
    private static final String NUL = "\0";

    /**
     * The shape Iris's centerDepthSmooth program hands over (written for the test, not copied): one
     * {@code layout(location = 0) in vec3}, two samplers, two floats, its own {@code out float} - the four fields the
     * Demonica legs logged for the program that failed ({@code samplers=2 uniformBytes=16 vertex=own}).
     */
    private static final String DEPTH_QUAD_VERTEX = """
            #version 330 core
            layout(location = 0) in vec3 a_Position;
            void main() { gl_Position = vec4(a_Position.xyz * 2.0 - 1.0, 1.0); }
            """;

    private static final String DEPTH_QUAD_FRAGMENT = """
            #version 330 core
            uniform sampler2D depth;
            uniform sampler2D altDepth;
            uniform float lastFrameTime;
            uniform float decay;
            out float fragDepth;
            void main() {
                fragDepth = mix(texture(depth, vec2(0.5)).r, texture(altDepth, vec2(0.5)).r,
                        1.0 - exp(-decay * lastFrameTime));
            }
            """;

    /**
     * Every source Iris hands over ends in U+0000 (item 371). The lowering appends its own entry point after the mod's
     * body, so a kept terminator sits mid-file and glslang stops with {@code '' : unexpected token} - the message this
     * program's stage produced in every leg since 5a (leg 18: {@code celtium-mod-program:16}).
     */
    @Test
    void lowersAndCompilesStagesThatKeepIrisNulTerminator() {
        ModGlslTranslator.Result result = ModGlslTranslator.translate(DEPTH_QUAD_VERTEX + NUL,
                DEPTH_QUAD_FRAGMENT + NUL);
        assertNull(result.failure(), () -> "unexpected failure: " + result.failure());
        assertEquals(List.of(new ModGlslTranslator.Attribute("a_Position", "vec3", 0)), result.attributes());
        assertEquals(List.of(new ModGlslTranslator.SamplerBinding("depth", "sampler2D", 0),
                new ModGlslTranslator.SamplerBinding("altDepth", "sampler2D", 1)), result.samplers());
        // std140: two floats are the whole block, aligned up to 16 - the size the fixture logged for the program.
        assertEquals(16, result.uniformBlockSize());
        assertEquals(1, result.fragOutputCount());
        assertFalse(result.vertex().contains(NUL), "the vertex stage still carries its terminator");
        assertFalse(result.fragment().contains(NUL), "the fragment stage still carries its terminator");
        assertCompiles(result.vertex(), Shaderc.shaderc_vertex_shader);
        assertCompiles(result.fragment(), Shaderc.shaderc_fragment_shader);
    }

    /**
     * A terminator is a terminator: one that would end up <em>inside</em> the text is not text, and it is refused with
     * the stage and the offset rather than spliced into the output for glslang to choke on.
     */
    @Test
    void refusesAnInteriorNulNamingTheStageAndTheOffset() {
        String fragment = "#version 120\nvoid celtium_user_main() {}" + NUL + "void main() {}";
        int offset = fragment.indexOf('\0');
        ModGlslTranslator.Result result = ModGlslTranslator.translate(QUAD_VERTEX, fragment);
        assertFalse(result.ok());
        assertTrue(result.failure().contains("fragment stage"), result.failure());
        assertTrue(result.failure().contains("offset " + offset), result.failure());
        // Nothing was handed to shaderc, so no lowered stage comes back beside the failure.
        assertNull(result.vertex());
        assertNull(result.fragment());
    }

    @Test
    void lowersStandardGlsl450BuiltinsAndLegacyTextureProjCalls() {
        String vertex = """
                #version 120
                attribute vec3 position;
                varying vec4 v_proj;
                varying float v_id;
                void main() {
                    v_id = float(gl_VertexID + gl_InstanceID);
                    v_proj = vec4(position, 1.0);
                    gl_Position = vec4(position, 1.0);
                }
                """;
        String fragment = """
                #version 120
                uniform sampler2D u_tex;
                varying vec4 v_proj;
                varying float v_id;
                void main() {
                    vec4 sampleCol = texture2DProj(u_tex, v_proj);
                    gl_FragColor = gl_FrontFacing ? sampleCol : sampleCol * 0.5;
                    gl_FragDepth = clamp(v_id * 0.001, 0.0, 1.0);
                }
                """;
        ModGlslTranslator.Result result = ModGlslTranslator.translate(vertex, fragment);
        assertTrue(result.ok(), () -> "expected lowering to succeed, got: " + result.failure());
        assertTrue(result.vertex().contains("gl_VertexIndex"));
        assertTrue(result.vertex().contains("gl_InstanceIndex"));
        assertTrue(result.fragment().contains("textureProj("));
        assertTrue(result.fragment().contains("gl_FrontFacing"));
        assertTrue(result.fragment().contains("gl_FragDepth"));
        assertCompiles(result.vertex(), Shaderc.shaderc_vertex_shader);
        assertCompiles(result.fragment(), Shaderc.shaderc_fragment_shader);
    }

    @Test
    void lowersLayoutQualifiedUniformsIntegerSamplersQualifiedVaryingsAndPointCoord() {
        String vertex = """
                #version 120
                attribute vec3 position;
                flat varying int v_kind;
                varying vec2 v_uv;
                void main() {
                    v_kind = gl_VertexID;
                    v_uv = position.xy;
                    gl_Position = vec4(position, 1.0);
                }
                """;
        String fragment = """
                #version 120
                layout(binding = 0) uniform sampler2D u_color;
                uniform isampler2D u_ids;
                uniform sampler1D u_lut;
                flat varying int v_kind;
                varying vec2 v_uv;
                void main() {
                    int id = texture2D(u_ids, v_uv).r + v_kind;
                    vec4 lut = texture1D(u_lut, gl_PointCoord.x);
                    vec4 col = texture2DGradARB(u_color, v_uv, vec2(0.01, 0.0), vec2(0.0, 0.01));
                    gl_FragColor = (id > 0 ? col : lut);
                }
                """;
        ModGlslTranslator.Result result = ModGlslTranslator.translate(vertex, fragment);
        assertTrue(result.ok(), () -> "expected lowering to succeed, got: " + result.failure());
        assertEquals(3, result.samplers().size());
        assertEquals("isampler2D", result.samplers().get(1).type());
        assertTrue(result.vertex().contains("flat out int v_kind;"), result.vertex());
        assertTrue(result.fragment().contains("flat in int v_kind;"), result.fragment());
        assertTrue(result.fragment().contains("gl_PointCoord"), result.fragment());
        assertTrue(result.fragment().contains("textureGrad("), result.fragment());
        assertCompiles(result.vertex(), Shaderc.shaderc_vertex_shader);
        assertCompiles(result.fragment(), Shaderc.shaderc_fragment_shader);
    }

    @Test
    void refusesAVaryingWhoseTypeDiffersAcrossStages() {
        // GL fails this link; the lowering compiles the stages separately, so without the check the fragment
        // stage would read a vec3 from a location the vertex stage wrote a vec2 into.
        String vertex = """
                #version 120
                attribute vec2 position;
                varying vec2 varyingData;
                void main() { varyingData = position; gl_Position = vec4(position, 0.0, 1.0); }
                """;
        String fragment = """
                #version 120
                varying vec3 varyingData;
                void main() { gl_FragColor = vec4(varyingData, 1.0); }
                """;
        ModGlslTranslator.Result result = ModGlslTranslator.translate(vertex, fragment);
        assertNotNull(result.failure());
        assertTrue(result.failure().contains("varying varyingData"), result.failure());
    }

    @Test
    void refusesAVaryingWhoseArrayLengthDiffersAcrossStages() {
        String vertex = """
                #version 120
                attribute vec2 position;
                varying float weights[2];
                void main() { weights[0] = 1.0; weights[1] = 0.0; gl_Position = vec4(position, 0.0, 1.0); }
                """;
        String fragment = """
                #version 120
                varying float weights[3];
                void main() { gl_FragColor = vec4(weights[0]); }
                """;
        ModGlslTranslator.Result result = ModGlslTranslator.translate(vertex, fragment);
        assertNotNull(result.failure());
        assertTrue(result.failure().contains("varying weights"), result.failure());
    }

    @Test
    void refusesAVaryingWhoseInterpolationDiffersAcrossStages() {
        // An unqualified varying is smooth: flat against unqualified is a mismatch, not a match.
        String vertex = """
                #version 120
                attribute vec2 position;
                flat varying float shade;
                void main() { shade = position.x; gl_Position = vec4(position, 0.0, 1.0); }
                """;
        String fragment = """
                #version 120
                varying float shade;
                void main() { gl_FragColor = vec4(shade); }
                """;
        ModGlslTranslator.Result result = ModGlslTranslator.translate(vertex, fragment);
        assertNotNull(result.failure());
        assertTrue(result.failure().contains("varying shade"), result.failure());
        assertTrue(result.failure().contains("flat"), result.failure());
    }

    @Test
    void lowersAMatchingFlatVaryingPair() {
        String vertex = """
                #version 120
                attribute vec2 position;
                flat varying float shade;
                void main() { shade = position.x; gl_Position = vec4(position, 0.0, 1.0); }
                """;
        String fragment = """
                #version 120
                flat varying float shade;
                void main() { gl_FragColor = vec4(shade); }
                """;
        ModGlslTranslator.Result result = ModGlslTranslator.translate(vertex, fragment);
        assertNull(result.failure(), () -> "unexpected failure: " + result.failure());
        assertCompiles(result.vertex(), Shaderc.shaderc_vertex_shader);
        assertCompiles(result.fragment(), Shaderc.shaderc_fragment_shader);
    }

    @Test
    void carriesAUniformConstructorInitializerAsItsDefault() {
        // Before, the parentheses in the initializer made the declaration unparseable: the uniform never
        // reached the interface and every write to it was dropped without a word.
        String vertex = """
                #version 120
                uniform vec4 tint = vec4(0.25, 0.5, 0.75, 1.0);
                attribute vec2 position;
                void main() { gl_Position = tint * vec4(position, 0.0, 1.0); }
                """;
        String fragment = """
                #version 120
                void main() { gl_FragColor = vec4(1.0); }
                """;
        ModGlslTranslator.Result result = ModGlslTranslator.translate(vertex, fragment);
        assertNull(result.failure(), () -> "unexpected failure: " + result.failure());
        assertEquals(List.of(new ModGlslTranslator.UniformMember("tint", "vec4", 0, 0, 16)), result.uniforms());
        assertArrayEquals(new float[] {0.25f, 0.5f, 0.75f, 1.0f}, result.uniformDefaults().get("tint"));
        assertCompiles(result.vertex(), Shaderc.shaderc_vertex_shader);
        assertCompiles(result.fragment(), Shaderc.shaderc_fragment_shader);
    }

    @Test
    void carriesScalarAndSiblingInitializers() {
        String vertex = """
                #version 120
                uniform float gain = 2.0;
                uniform vec4 a = vec4(1.0, 2.0, 3.0, 4.0), b;
                attribute vec2 position;
                void main() { gl_Position = (a + b) * gain * vec4(position, 0.0, 1.0); }
                """;
        String fragment = """
                #version 120
                void main() { gl_FragColor = vec4(1.0); }
                """;
        ModGlslTranslator.Result result = ModGlslTranslator.translate(vertex, fragment);
        assertNull(result.failure(), () -> "unexpected failure: " + result.failure());
        assertEquals(List.of(new ModGlslTranslator.UniformMember("gain", "float", 0, 0, 4),
                new ModGlslTranslator.UniformMember("a", "vec4", 0, 16, 16),
                new ModGlslTranslator.UniformMember("b", "vec4", 0, 32, 16)), result.uniforms());
        assertArrayEquals(new float[] {2.0f}, result.uniformDefaults().get("gain"));
        assertArrayEquals(new float[] {1.0f, 2.0f, 3.0f, 4.0f}, result.uniformDefaults().get("a"));
        assertNull(result.uniformDefaults().get("b"));
        assertCompiles(result.vertex(), Shaderc.shaderc_vertex_shader);
    }

    /**
     * Item 726 (Twilight Forest's aurora): an array constructor may omit its size -
     * {@code uniform float offset[5] = float[](...)} - and take the length from the declaration. The
     * lowering used to refuse it twice over (the constructor pattern had no empty brackets, and the array
     * branch demanded an explicit size), so the whole program failed to translate.
     */
    @Test
    void carriesAnUnsizedArrayConstructorInitializerAsItsDefault() {
        String vertex = """
                #version 120
                attribute vec2 position;
                varying vec2 uv;
                void main() { gl_Position = vec4(position, 0.0, 1.0); uv = position; }
                """;
        String fragment = """
                #version 120
                varying vec2 uv;
                uniform float offset[5] = float[](0.0, 1.0, 2.0, 3.0, 4.0);
                uniform float weight[5] = float[](0.2270270270, 0.1945945946, 0.1216216216, 0.0540540541, 0.0162162162);
                void main() { gl_FragColor = vec4(weight[0] + offset[1]); }
                """;
        ModGlslTranslator.Result result = ModGlslTranslator.translate(vertex, fragment);
        assertNull(result.failure(), () -> "unexpected failure: " + result.failure());
        assertArrayEquals(new float[] {0.0f, 1.0f, 2.0f, 3.0f, 4.0f}, result.uniformDefaults().get("offset"));
        assertArrayEquals(new float[] {0.2270270270f, 0.1945945946f, 0.1216216216f, 0.0540540541f, 0.0162162162f},
                result.uniformDefaults().get("weight"));
        assertCompiles(result.vertex(), Shaderc.shaderc_vertex_shader);
        assertCompiles(result.fragment(), Shaderc.shaderc_fragment_shader);
    }

    @Test
    void refusesAnUnsizedArrayConstructorWhoseCountMissesTheDeclaredLength() {
        String fragment = """
                #version 120
                uniform float offset[5] = float[](0.0, 1.0, 2.0);
                void main() { gl_FragColor = vec4(offset[0]); }
                """;
        ModGlslTranslator.Result result = ModGlslTranslator.translate(
                "void main() { gl_Position = vec4(0.0); }", fragment);
        assertNotNull(result.failure());
        assertTrue(result.failure().contains("initializer this lowering does not evaluate"), result.failure());
    }

    /**
     * Item 726 (Twilight Forest's pulsing_yellow): GLSL 120 has no built-in {@code round}, so a 120 shader
     * may define its own. At {@code #version 450} that definition collides with glslang's built-in overload
     * (parameter precision mismatch) and shaderc refuses it; the lowering renames the definition and its
     * calls instead.
     */
    @Test
    void renamesAUserFunctionThatCollidesWithA450Builtin() {
        String vertex = """
                #version 120
                attribute vec2 position;
                void main() { gl_Position = vec4(position, 0.0, 1.0); }
                """;
        String fragment = """
                #version 120
                float round(float n) {
                    float y = n + 0.5;
                    return y - mod(y, 1.0);
                }
                void main() { gl_FragColor = vec4(round(0.4)); }
                """;
        ModGlslTranslator.Result result = ModGlslTranslator.translate(vertex, fragment);
        assertNull(result.failure(), () -> "unexpected failure: " + result.failure());
        assertTrue(result.fragment().contains("float celtium_r_round(float n)"), result.fragment());
        assertTrue(result.fragment().contains("celtium_r_round(0.4)"), result.fragment());
        assertCompiles(result.vertex(), Shaderc.shaderc_vertex_shader);
        assertCompiles(result.fragment(), Shaderc.shaderc_fragment_shader);
    }

    @Test
    void leavesACollidingFunctionNameAloneAt130AndAbove() {
        // At 1.30+ the source's `round` may be the built-in; renaming is not applied there.
        String fragment = """
                #version 130
                out vec4 color;
                float round(float n) { return n; }
                void main() { color = vec4(round(0.4)); }
                """;
        ModGlslTranslator.Result result = ModGlslTranslator.translate(
                "#version 130\nvoid main() { gl_Position = vec4(0.0); }", fragment);
        if (result.ok()) {
            assertFalse(result.fragment().contains("celtium_r_round"), result.fragment());
        }
    }

    /**
     * Item 726 (Twilight Forest's starburst / shield): the fragment stage's {@code uniform vec3 position}
     * enters the instance-less common block, which is emitted into the vertex stage too, where
     * {@code position} is a varying - a redefinition glslang refuses. The member is renamed inside the
     * block (and in the declaring stage's references); the reported interface keeps the GL name.
     */
    @Test
    void renamesAUniformMemberThatCollidesWithAVaryingName() {
        String vertex = """
                #version 120
                varying vec3 position;
                void main() {
                    position = (gl_ModelViewMatrix * gl_Vertex).xyz;
                    gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;
                }
                """;
        String fragment = """
                #version 120
                uniform vec3 position;
                void main() { gl_FragColor = vec4(position * 0.5 + 0.5, 1.0); }
                """;
        ModGlslTranslator.Result result = ModGlslTranslator.translate(vertex, fragment);
        assertNull(result.failure(), () -> "unexpected failure: " + result.failure());
        assertTrue(result.uniforms().stream().anyMatch(m -> m.name().equals("position")),
                result.uniforms().toString());
        assertTrue(result.vertex().contains("vec3 celtium_u_position;"), result.vertex());
        assertTrue(result.vertex().contains("out vec3 position;"), result.vertex());
        assertTrue(result.fragment().contains("celtium_u_position * 0.5"), result.fragment());
        assertCompiles(result.vertex(), Shaderc.shaderc_vertex_shader);
        assertCompiles(result.fragment(), Shaderc.shaderc_fragment_shader);
    }

    @Test
    void refusesAUniformVaryingCollisionShadowedInTheDeclaringStage() {
        String vertex = """
                #version 120
                varying vec3 position;
                void main() { position = gl_Vertex.xyz; gl_Position = gl_Vertex; }
                """;
        String fragment = """
                #version 120
                uniform vec3 position;
                float shade(float position) { return position; }
                void main() { gl_FragColor = vec4(shade(1.0)); }
                """;
        ModGlslTranslator.Result result = ModGlslTranslator.translate(vertex, fragment);
        assertNotNull(result.failure());
        assertTrue(result.failure().contains("shadowed"), result.failure());
    }

    @Test
    void refusesAUniformInitializerThatIsNotAConstant() {
        String vertex = """
                #version 120
                uniform float t = computeT();
                attribute vec2 position;
                void main() { gl_Position = vec4(position * t, 0.0, 1.0); }
                """;
        String fragment = """
                #version 120
                void main() { gl_FragColor = vec4(1.0); }
                """;
        ModGlslTranslator.Result result = ModGlslTranslator.translate(vertex, fragment);
        assertNotNull(result.failure());
        assertTrue(result.failure().contains("initializer this lowering does not evaluate"), result.failure());
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

    @Test
    void anOldStyleLayoutLocationIsHonouredNotRenumbered() {
        // The modern in/out spelling has always captured layout(location = N); the attribute/varying spelling
        // read the layout and dropped it, so an explicitly laid out attribute was renumbered by declaration
        // order and the vertex data landed in the wrong input.
        String vertex = """
                #version 120
                layout(location = 2) attribute vec3 a_first;
                attribute vec2 a_second;
                void main(void) {
                    gl_Position = vec4(a_first, 1.0) + vec4(a_second, 0.0, 1.0);
                }
                """;
        String fragment = """
                #version 120
                void main() {
                    gl_FragColor = vec4(1.0);
                }
                """;
        ModGlslTranslator.Result result = ModGlslTranslator.translate(vertex, fragment);
        assertNull(result.failure(), () -> "unexpected failure: " + result.failure());
        assertEquals(List.of(
                new ModGlslTranslator.Attribute("a_first", "vec3", 2),
                new ModGlslTranslator.Attribute("a_second", "vec2", 0)), result.attributes());
    }

    @Test
    void aBlockMarkerInsideALineCommentEatsNothing() {
        // The comment stripper used to run its block pattern over the whole text first, so a /* mentioned
        // in a line comment paired with the next real */ below and ate the live code in between; the line
        // pattern then ate what the block pass had joined onto the comment's own line. Both uniforms here
        // were lost, and the body's uses of them failed compilation as undeclared identifiers.
        String fragment = """
                #version 120
                varying vec2 uv;
                uniform sampler2D first;
                // note: the /* marker below opens the kernel table
                uniform float eaten;
                /* kernel */ uniform float kept;
                void main() {
                    gl_FragColor = texture2D(first, uv) * (eaten + kept);
                }
                """;
        ModGlslTranslator.Result result = ModGlslTranslator.translate(QUAD_VERTEX, fragment);
        assertTrue(result.ok(), result.failure());
        assertTrue(result.uniforms().stream().anyMatch(u -> u.name().equals("eaten")), result.uniforms().toString());
        assertTrue(result.uniforms().stream().anyMatch(u -> u.name().equals("kept")), result.uniforms().toString());
    }

    @Test
    void aDeclarationInsideADefineBodyIsNotAnInterfaceMember() {
        // A #define body is macro text: extracting its declaration shape filed the stamped-out name - and,
        // for a parameterised macro, the parameter itself - as real uniform block members, and left the
        // define gutted. Preprocessor lines are not code and are left exactly as they are.
        String fragment = """
                #version 120
                #define SHARED uniform vec4 phantom;
                #define MKU(n) uniform vec4 n;
                varying vec2 uv;
                uniform sampler2D first;
                void main() {
                    gl_FragColor = texture2D(first, uv);
                }
                """;
        ModGlslTranslator.Result result = ModGlslTranslator.translate(QUAD_VERTEX, fragment);
        assertTrue(result.ok(), result.failure());
        assertTrue(result.uniforms().stream().noneMatch(u -> u.name().equals("phantom")), result.uniforms().toString());
        assertTrue(result.uniforms().stream().noneMatch(u -> u.name().equals("n")), result.uniforms().toString());
        assertTrue(result.fragment().contains("#define SHARED uniform vec4 phantom;"), result.fragment());
        assertTrue(result.fragment().contains("#define MKU(n) uniform vec4 n;"), result.fragment());
    }

    @Test
    void lowersProjectiveFetchesFor1DAnd3D() {
        String fragment = """
                #version 120
                varying vec2 uv;
                uniform sampler1D lut;
                uniform sampler3D vol;
                void main() {
                    gl_FragColor = texture1DProj(lut, vec4(uv, 0.0, 1.0)) + texture3DProj(vol, vec4(uv, 0.0, 1.0));
                }
                """;
        ModGlslTranslator.Result result = ModGlslTranslator.translate(QUAD_VERTEX, fragment);
        assertTrue(result.ok(), result.failure());
        assertTrue(result.fragment().contains("textureProj("), result.fragment());
        assertFalse(result.fragment().contains("texture1DProj"), result.fragment());
        assertFalse(result.fragment().contains("texture3DProj"), result.fragment());
    }

    @Test
    void aShadowSamplerDeclarationIsCarriedNotRefused() {
        // A shadow sampler type is a sampler like any other to this lowering: it is extracted, given a
        // binding and emitted with its type intact (the measured packs declare sampler2DShadow for
        // shadowtex0/shadowtex1). Whether the texture a binding resolves to has a comparing sampler is a
        // property of that texture, checked by the prepare path - not a translation refusal.
        String fragment = """
                #version 120
                varying vec2 uv;
                uniform sampler2DShadow smap;
                uniform sampler2D first;
                void main() {
                    gl_FragColor = texture2D(first, uv);
                }
                """;
        ModGlslTranslator.Result result = ModGlslTranslator.translate(QUAD_VERTEX, fragment);
        assertTrue(result.ok(), result.failure());
        assertTrue(result.samplers().stream().anyMatch(
                s -> s.name().equals("smap") && s.type().equals("sampler2DShadow")), result.samplers().toString());
        assertTrue(result.fragment().contains("uniform sampler2DShadow smap;"), result.fragment());
    }

    @Test
    void lowersLegacyShadowCallsToWrappedComparisonFetches() {
        // The measured packs' consumption shapes: Complementary reads the comparison through .x and .z
        // (shadowSampling.glsl, volumetricLight), BSL's texture2DShadow helper returns shadow2D(...).x,
        // and Complementary's gbuffers_basic assigns the whole vec4 as a colour. The wrap restores the
        // legacy (d, d, d, 1) answer around the modern fetch, so all three survive; a name-only rewrite
        // would leave a scalar where the .z swizzle and the vec4 assignment need four components.
        String fragment = """
                #version 120
                varying vec2 uv;
                uniform sampler2DShadow smap;
                void main() {
                    float d = shadow2D(smap, vec3(uv, 0.5)).x;
                    vec4 whole = shadow2D(smap, vec3(0.5));
                    gl_FragColor = whole * d + vec4(shadow2DProj(smap, vec4(uv, 0.5, 1.0)).z);
                }
                """;
        ModGlslTranslator.Result result = ModGlslTranslator.translate(QUAD_VERTEX, fragment);
        assertTrue(result.ok(), result.failure());
        assertTrue(result.fragment().contains("vec4(vec3(texture(smap, vec3(uv, 0.5))), 1.0).x"),
                result.fragment());
        assertTrue(result.fragment().contains("vec4(vec3(texture(smap, vec3(0.5))), 1.0);"),
                result.fragment());
        assertTrue(result.fragment().contains("vec4(vec3(textureProj(smap, vec4(uv, 0.5, 1.0))), 1.0).z"),
                result.fragment());
        assertFalse(result.fragment().contains("shadow2D"), result.fragment());
    }

    @Test
    void lowersThe1DShadowCallWithItsCoordinateNarrowed() {
        // GLSL 1.20's shadow1D takes a vec3 (the coordinate in .x, the reference value in .z, the middle
        // component ignored); the modern fetch on a sampler1DShadow takes a vec2 of exactly those two
        // components, so the coordinate is repacked, not carried over at its old width.
        String fragment = """
                #version 120
                varying vec2 uv;
                uniform sampler1DShadow smap;
                void main() {
                    gl_FragColor = shadow1D(smap, vec3(uv.x, 0.0, 0.5));
                }
                """;
        ModGlslTranslator.Result result = ModGlslTranslator.translate(QUAD_VERTEX, fragment);
        assertTrue(result.ok(), result.failure());
        assertTrue(result.fragment().contains(
                "vec4(vec3(texture(smap, vec2((vec3(uv.x, 0.0, 0.5)).x, (vec3(uv.x, 0.0, 0.5)).z))), 1.0)"),
                result.fragment());
    }

    @Test
    void aShadowCallThisLoweringCannotParseIsNamedNotPassedThrough() {
        // The wrap has to span the whole call, so a call whose arguments do not split into the sampler,
        // coordinate and optional bias it takes is refused by name - not left in the text to die at
        // shader compilation as an unknown function, which was the pre-hunt-8 behaviour for the family.
        String fragment = """
                #version 120
                varying vec2 uv;
                uniform sampler2DShadow smap;
                void main() {
                    gl_FragColor = shadow2D(smap);
                }
                """;
        ModGlslTranslator.Result result = ModGlslTranslator.translate(QUAD_VERTEX, fragment);
        assertFalse(result.ok());
        assertNotNull(result.failure());
        assertTrue(result.failure().contains("shadow2D"), result.failure());
    }

    @Test
    void aDuplicateAttributeOfTheSameTypeFoldsIntoOne() {
        // Part B of the a_PosId disposition, pinned without its preprocessor mask: two declarations of
        // one attribute name in plain body text - no conditionals anywhere - both reached the attribute
        // list, so the lowered header carried the name twice, at two locations, and shaderc refused the
        // stage with a redefinition error. The same-type duplicate now folds into the first declaration:
        // one attribute, and the folded copy consumes no location, so the next attribute moves up.
        String vertex = """
                #version 120
                attribute vec3 a_dup;
                attribute vec3 a_dup;
                attribute vec2 a_next;
                void main(void) {
                    gl_Position = vec4(a_dup, 1.0) + vec4(a_next, 0.0, 1.0);
                }
                """;
        String fragment = """
                #version 120
                void main() {
                    gl_FragColor = vec4(1.0);
                }
                """;
        ModGlslTranslator.Result result = ModGlslTranslator.translate(vertex, fragment);
        assertNull(result.failure(), () -> "unexpected failure: " + result.failure());
        assertEquals(List.of(
                new ModGlslTranslator.Attribute("a_dup", "vec3", 0),
                new ModGlslTranslator.Attribute("a_next", "vec2", 1)), result.attributes());
    }

    @Test
    void aDuplicateAttributeOfAnotherTypeIsRefusedByName() {
        // The other half of the same disposition: a same-name duplicate whose type differs is not a fold
        // candidate - which of the two declarations the vertex data means is undecidable here - so
        // translation refuses it by name, in the uniform collision's phrasing, instead of emitting both
        // declarations for shaderc to reject as a redefinition.
        String vertex = """
                #version 330 core
                in uvec4 a_dup;
                in vec3 a_dup;
                void main() {
                    gl_Position = vec4(0.0);
                }
                """;
        String fragment = """
                #version 330 core
                out vec4 outColor;
                void main() {
                    outColor = vec4(1.0);
                }
                """;
        ModGlslTranslator.Result result = ModGlslTranslator.translate(vertex, fragment);
        assertFalse(result.ok());
        assertNotNull(result.failure());
        assertTrue(result.failure().contains("attribute a_dup is declared as uvec4 and vec3"), result.failure());
    }

    /**
     * The shape Complementary's shared uniforms take (written for the test in that shape, not copied from the
     * pack): one name spelled as two sampler types in exclusive branches. The pack's own defines select the
     * branch - the composite stage defines COMPOSITE1 - so extraction follows the selection instead of merging
     * both spellings into a collision. The rejected spelling's text stays in the body inside its conditional:
     * the body is emitted unrewritten, and shaderc's preprocessor drops it there.
     */
    private static final String SHADOWTEX_COMPOSITE = """
            #version 120
            #define SHADOW_QUALITY 2
            #define COMPOSITE1
            uniform sampler2D colortex0;
            #if SHADOW_QUALITY > -1 || defined LIGHTSHAFTS_ACTIVE || defined FF_BLOCKLIGHT
            #ifdef COMPOSITE1
            uniform sampler2D shadowtex0;
            #else
            uniform sampler2DShadow shadowtex0;
            #endif
            #endif
            void main() { gl_FragColor = vec4(1.0); }
            """;

    private static final String SHADOWTEX_NO_COMPOSITE = """
            #version 120
            #define SHADOW_QUALITY 2
            uniform sampler2D colortex0;
            #if SHADOW_QUALITY > -1 || defined LIGHTSHAFTS_ACTIVE || defined FF_BLOCKLIGHT
            #ifdef COMPOSITE1
            uniform sampler2D shadowtex0;
            #else
            uniform sampler2DShadow shadowtex0;
            #endif
            #endif
            void main() { gl_FragColor = vec4(1.0); }
            """;

    @Test
    void exclusiveBranchesSelectTheDefinedSpelling() {
        ModGlslTranslator.Result result = ModGlslTranslator.translate(QUAD_VERTEX, SHADOWTEX_COMPOSITE);
        assertNull(result.failure(), () -> "unexpected failure: " + result.failure());
        assertEquals(List.of(new ModGlslTranslator.SamplerBinding("colortex0", "sampler2D", 0),
                new ModGlslTranslator.SamplerBinding("shadowtex0", "sampler2D", 1)), result.samplers());
        // The rejected spelling was not extracted, so its text is still in the body under its conditional.
        assertTrue(result.fragment().contains("uniform sampler2DShadow shadowtex0;"));
        assertTrue(result.fragment().contains("#ifdef COMPOSITE1"));
        assertCompiles(result.fragment(), Shaderc.shaderc_fragment_shader);
    }

    @Test
    void exclusiveBranchesSelectTheShadowSpellingWhenTheStageDefineIsAbsent() {
        ModGlslTranslator.Result result = ModGlslTranslator.translate(QUAD_VERTEX, SHADOWTEX_NO_COMPOSITE);
        assertNull(result.failure(), () -> "unexpected failure: " + result.failure());
        assertEquals(List.of(new ModGlslTranslator.SamplerBinding("colortex0", "sampler2D", 0),
                new ModGlslTranslator.SamplerBinding("shadowtex0", "sampler2DShadow", 1)), result.samplers());
        assertTrue(result.fragment().contains("uniform sampler2D shadowtex0;"));
        assertCompiles(result.fragment(), Shaderc.shaderc_fragment_shader);
    }

    /**
     * A condition the source's own defines cannot decide - GL_ES may be predefined by the compiler itself -
     * keeps the extract-everything behaviour: both spellings reach the interface and the collision refuses,
     * exactly as before extraction learned to follow conditionals.
     */
    @Test
    void anUndecidableConditionKeepsTheCollisionRefusal() {
        String fragment = """
                #version 120
                #ifdef GL_ES
                uniform float mode;
                #else
                uniform int mode;
                #endif
                void main() { gl_FragColor = vec4(1.0); }
                """;
        ModGlslTranslator.Result result = ModGlslTranslator.translate(QUAD_VERTEX, fragment);
        assertFalse(result.ok());
        assertEquals("uniform mode is declared as float and int", result.failure());
    }

    @Test
    void anUndecidableConditionStillMergesMatchingSpellings() {
        String fragment = """
                #version 120
                #ifdef GL_ES
                uniform float mode;
                #else
                uniform float mode;
                #endif
                void main() { gl_FragColor = vec4(mode); }
                """;
        ModGlslTranslator.Result result = ModGlslTranslator.translate(QUAD_VERTEX, fragment);
        assertNull(result.failure(), () -> "unexpected failure: " + result.failure());
        assertEquals(List.of(new ModGlslTranslator.UniformMember("mode", "float", 0, 0, 4)), result.uniforms());
    }

    @Test
    void decidableIfExpressionsSelectByValue() {
        String fragment = """
                #version 120
                #define QUALITY 2
                #if QUALITY > 1
                uniform float gain;
                #else
                uniform int gain;
                #endif
                #if QUALITY < 1
                uniform int bias;
                #else
                uniform float bias;
                #endif
                void main() { gl_FragColor = vec4(gain + bias); }
                """;
        ModGlslTranslator.Result result = ModGlslTranslator.translate(QUAD_VERTEX, fragment);
        assertNull(result.failure(), () -> "unexpected failure: " + result.failure());
        assertEquals(List.of(new ModGlslTranslator.UniformMember("gain", "float", 0, 0, 4),
                new ModGlslTranslator.UniformMember("bias", "float", 0, 4, 4)), result.uniforms());
        assertCompiles(result.fragment(), Shaderc.shaderc_fragment_shader);
    }

    @Test
    void anElifChainSelectsTheMatchingArm() {
        String fragment = """
                #version 120
                #define TIER 2
                #if TIER == 1
                uniform int level;
                #elif TIER == 2
                uniform float level;
                #else
                uniform vec2 level;
                #endif
                void main() { gl_FragColor = vec4(level); }
                """;
        ModGlslTranslator.Result result = ModGlslTranslator.translate(QUAD_VERTEX, fragment);
        assertNull(result.failure(), () -> "unexpected failure: " + result.failure());
        assertEquals(List.of(new ModGlslTranslator.UniformMember("level", "float", 0, 0, 4)), result.uniforms());
    }

    @Test
    void anUndefBetweenDefineAndBranchAppliesInSourceOrder() {
        String fragment = """
                #version 120
                #define FLAG
                #undef FLAG
                #ifdef FLAG
                uniform int value;
                #else
                uniform float value;
                #endif
                void main() { gl_FragColor = vec4(value); }
                """;
        ModGlslTranslator.Result result = ModGlslTranslator.translate(QUAD_VERTEX, fragment);
        assertNull(result.failure(), () -> "unexpected failure: " + result.failure());
        assertEquals(List.of(new ModGlslTranslator.UniformMember("value", "float", 0, 0, 4)), result.uniforms());
    }

    @Test
    void aRejectedOuterBranchSilencesAnUndecidableInnerOne() {
        String fragment = """
                #version 120
                #if 0
                #ifdef GL_ES
                uniform int deep;
                #else
                uniform float deep;
                #endif
                #endif
                uniform float deep;
                void main() { gl_FragColor = vec4(deep); }
                """;
        ModGlslTranslator.Result result = ModGlslTranslator.translate(QUAD_VERTEX, fragment);
        assertNull(result.failure(), () -> "unexpected failure: " + result.failure());
        assertEquals(List.of(new ModGlslTranslator.UniformMember("deep", "float", 0, 0, 4)), result.uniforms());
        assertCompiles(result.fragment(), Shaderc.shaderc_fragment_shader);
    }

    @Test
    void anAttributeInTheRejectedBranchIsNotInTheInterface() {
        String vertex = """
                #version 120
                #ifdef USE_VEC3_POS
                attribute vec3 position;
                #else
                attribute vec2 position;
                #endif
                varying vec2 uv;
                void main() {
                    gl_Position = vec4(position, 0.0, 1.0);
                    uv = position;
                }
                """;
        ModGlslTranslator.Result result = ModGlslTranslator.translate(vertex, COMBINE_FRAGMENT);
        assertNull(result.failure(), () -> "unexpected failure: " + result.failure());
        assertEquals(List.of(new ModGlslTranslator.Attribute("position", "vec2", 0)), result.attributes());
        assertCompiles(result.vertex(), Shaderc.shaderc_vertex_shader);
    }

    @Test
    void aModernInputInTheRejectedBranchIsNotInTheInterface() {
        String vertex = """
                #version 330 core
                layout(location = 0) in vec2 a_Pos;
                #ifdef WIDE
                in vec3 a_Extra;
                #else
                in vec2 a_Extra;
                #endif
                out vec2 v_Uv;
                void main() {
                    v_Uv = a_Pos + a_Extra;
                    gl_Position = vec4(a_Pos, 0.0, 1.0);
                }
                """;
        String fragment = """
                #version 330 core
                in vec2 v_Uv;
                out vec4 o_Color;
                void main() { o_Color = vec4(v_Uv, 0.0, 1.0); }
                """;
        ModGlslTranslator.Result result = ModGlslTranslator.translate(vertex, fragment);
        assertNull(result.failure(), () -> "unexpected failure: " + result.failure());
        assertEquals(List.of(new ModGlslTranslator.Attribute("a_Pos", "vec2", 0),
                new ModGlslTranslator.Attribute("a_Extra", "vec2", 1)), result.attributes());
        assertCompiles(result.vertex(), Shaderc.shaderc_vertex_shader);
        assertCompiles(result.fragment(), Shaderc.shaderc_fragment_shader);    }

    /**
     * BSL's composite tail in miniature: the light-shafts value divided down and raised to a fractional
     * power through a vector exponent - the call wedge-16 measured coming out as NaN on RADV, at a base
     * of exactly 0 that the shader's own falloff clamp produced.
     */
    private static final String SHAFTS_FRAGMENT = """
            #version 120
            varying vec2 uv;
            uniform sampler2D tex;
            uniform float falloff;
            void main() {
                vec3 vl = texture2D(tex, uv).rgb * falloff;
                vl = pow(vl / 32.0, vec3(0.25));
                gl_FragColor = vec4(vl, 1.0);
            }
            """;

    private static ModGlslTranslator.Result translateWithPowGuard(boolean on, String vertex, String fragment) {
        System.setProperty("celtium.vulkan.fractionalPowGuard", Boolean.toString(on));
        try {
            return ModGlslTranslator.translate(vertex, fragment);
        } finally {
            System.clearProperty("celtium.vulkan.fractionalPowGuard");
        }
    }

    private static ModGlslTranslator.Result translateWithPowGuardUnset(String vertex, String fragment) {
        System.clearProperty("celtium.vulkan.fractionalPowGuard");
        try {
            return ModGlslTranslator.translate(vertex, fragment);
        } finally {
            System.clearProperty("celtium.vulkan.fractionalPowGuard");
        }
    }

    @Test
    void fractionalPowGuardIsOnByDefault() {
        // The default flipped after the item-684 verification leg measured the cone NaN gone with
        // the guard on (and reproduced with it off) on the shipped jar: with the property unset the
        // shafts call is guarded.
        ModGlslTranslator.Result result = translateWithPowGuardUnset(QUAD_VERTEX, SHAFTS_FRAGMENT);
        assertNull(result.failure(), () -> "unexpected failure: " + result.failure());
        assertTrue(result.fragment().contains("celtium_pow_guard(vl / 32.0, vec3(0.25))"),
                () -> "the default lowering must route the call through the guard:\n"
                        + result.fragment());
        assertCompiles(result.fragment(), Shaderc.shaderc_fragment_shader);
    }

    @Test
    void fractionalPowGuardExplicitOffKeepsThePreGuardLowering() {
        // The byte-equivalence pin that survives the default flip: an explicit off returns the
        // lowering byte-for-byte what it was before the guard existed.
        ModGlslTranslator.Result result = translateWithPowGuard(false, QUAD_VERTEX, SHAFTS_FRAGMENT);
        assertNull(result.failure(), () -> "unexpected failure: " + result.failure());
        assertTrue(result.fragment().contains("pow(vl / 32.0, vec3(0.25))"),
                () -> "the unguarded lowering must keep the call as written:\n" + result.fragment());
        assertFalse(result.fragment().contains("celtium_pow_guard"));
        assertCompiles(result.fragment(), Shaderc.shaderc_fragment_shader);
    }

    @Test
    void fractionalPowGuardRewritesTheShaftsCall() {
        ModGlslTranslator.Result off = translateWithPowGuard(false, QUAD_VERTEX, SHAFTS_FRAGMENT);
        ModGlslTranslator.Result result = translateWithPowGuard(true, QUAD_VERTEX, SHAFTS_FRAGMENT);
        assertNull(result.failure(), () -> "unexpected failure: " + result.failure());
        assertTrue(result.fragment().contains("celtium_pow_guard(vl / 32.0, vec3(0.25))"),
                () -> "the shafts call must be routed through the guard:\n" + result.fragment());
        assertTrue(result.fragment().contains("float celtium_pow_guard(float celtium_base, float celtium_exp)"),
                () -> "the overload set must be injected into the stage that gained a rewrite:\n"
                        + result.fragment());
        // The vertex stage has no pow call: it stays byte-for-byte the unguarded lowering.
        assertEquals(off.vertex(), result.vertex());
        assertCompiles(result.vertex(), Shaderc.shaderc_vertex_shader);
        assertCompiles(result.fragment(), Shaderc.shaderc_fragment_shader);
        // The switch is read per translation, not latched: off again is the unguarded text again.
        ModGlslTranslator.Result offAgain = translateWithPowGuard(false, QUAD_VERTEX, SHAFTS_FRAGMENT);
        assertEquals(off.fragment(), offAgain.fragment());
    }

    @Test
    void fractionalPowGuardRewritesScalarAndComponentExponents() {
        String fragment = """
                #version 120
                varying vec2 uv;
                uniform vec3 tint;
                void main() {
                    float a = pow(uv.x, 0.25);
                    vec3 b = pow(tint, vec3(0.25, 0.5, 2.2));
                    vec2 c = pow(uv, 0.5);
                    gl_FragColor = vec4(b + vec3(a) + vec3(c, 1.0), 1.0);
                }
                """;
        ModGlslTranslator.Result result = translateWithPowGuard(true, QUAD_VERTEX, fragment);
        assertNull(result.failure(), () -> "unexpected failure: " + result.failure());
        assertTrue(result.fragment().contains("celtium_pow_guard(uv.x, 0.25)"),
                () -> "scalar base and exponent:\n" + result.fragment());
        assertTrue(result.fragment().contains("celtium_pow_guard(tint, vec3(0.25, 0.5, 2.2))"),
                () -> "vector exponent with fractional components:\n" + result.fragment());
        assertTrue(result.fragment().contains("celtium_pow_guard(uv, 0.5)"),
                () -> "vector base with a scalar exponent:\n" + result.fragment());
        assertCompiles(result.fragment(), Shaderc.shaderc_fragment_shader);
    }

    @Test
    void fractionalPowGuardLeavesOtherExponentsAlone() {
        String fragment = """
                #version 120
                varying vec2 uv;
                uniform float gain;
                uniform vec3 tint;
                void main() {
                    float a = pow(uv.x, 2.0);
                    float b = pow(uv.x, gain);
                    float c = pow(uv.x, -0.5);
                    float d = pow(uv.x, 1.0 / 2.2);
                    vec3 e = pow(tint, vec3(2.0));
                    gl_FragColor = vec4(e + vec3(a + b + c + d), 1.0);
                }
                """;
        ModGlslTranslator.Result off = translateWithPowGuard(false, QUAD_VERTEX, fragment);
        ModGlslTranslator.Result result = translateWithPowGuard(true, QUAD_VERTEX, fragment);
        assertNull(result.failure(), () -> "unexpected failure: " + result.failure());
        assertFalse(result.fragment().contains("celtium_pow_guard"),
                () -> "no exponent here is a positive fractional constant:\n" + result.fragment());
        assertEquals(off.fragment(), result.fragment());
        assertCompiles(result.fragment(), Shaderc.shaderc_fragment_shader);
    }

    @Test
    void fractionalPowGuardRewritesNestedCallsAndMacroBodies() {
        String fragment = """
                #version 120
                varying vec2 uv;
                #define SHAFT(x) pow(x, 0.25)
                void main() {
                    float a = pow(pow(uv.x, 0.5), 0.25);
                    float b = SHAFT(uv.y);
                    gl_FragColor = vec4(vec3(a + b), 1.0);
                }
                """;
        ModGlslTranslator.Result result = translateWithPowGuard(true, QUAD_VERTEX, fragment);
        assertNull(result.failure(), () -> "unexpected failure: " + result.failure());
        assertTrue(result.fragment().contains("celtium_pow_guard(celtium_pow_guard(uv.x, 0.5), 0.25)"),
                () -> "the inner call is guarded inside the outer one:\n" + result.fragment());
        assertTrue(result.fragment().contains("#define SHAFT(x) celtium_pow_guard(x, 0.25)"),
                () -> "a call in a macro body is guarded in place, so expansions inherit it:\n"
                        + result.fragment());
        assertCompiles(result.fragment(), Shaderc.shaderc_fragment_shader);
    }

    @Test
    void fractionalPowGuardIgnoresCommentText() {
        String fragment = """
                #version 120
                varying vec2 uv;
                /* pow(broken, 0.25 - the parenthesis never closes in here */
                // pow(also, 0.25) is a comment too
                void main() {
                    float a = pow(uv.x, 2.0);
                    gl_FragColor = vec4(vec3(a), 1.0);
                }
                """;
        ModGlslTranslator.Result result = translateWithPowGuard(true, QUAD_VERTEX, fragment);
        assertNull(result.failure(), () -> "unexpected failure: " + result.failure());
        assertFalse(result.fragment().contains("celtium_pow_guard"),
                () -> "comment text is not a call, and must not corrupt the parse of the real one:\n"
                        + result.fragment());
        assertTrue(result.fragment().contains("pow(uv.x, 2.0)"),
                () -> "the real call after the comments is carried through untouched:\n"
                        + result.fragment());
        assertCompiles(result.fragment(), Shaderc.shaderc_fragment_shader);
    }

    @Test
    void fractionalPowGuardRewritesTheVertexStageToo() {
        String vertex = """
                #version 120
                attribute vec2 position;
                varying vec2 uv;
                void main() {
                    gl_Position = vec4(position, 0.0, 1.0);
                    uv = vec2(pow(position.x * 0.5 + 0.5, 0.5), position.y);
                }
                """;
        String fragment = """
                #version 120
                varying vec2 uv;
                void main() { gl_FragColor = vec4(uv, 0.0, 1.0); }
                """;
        ModGlslTranslator.Result result = translateWithPowGuard(true, vertex, fragment);
        assertNull(result.failure(), () -> "unexpected failure: " + result.failure());
        assertTrue(result.vertex().contains("celtium_pow_guard(position.x * 0.5 + 0.5, 0.5)"),
                () -> "the vertex stage is guarded by the same rule:\n" + result.vertex());
        assertFalse(result.fragment().contains("celtium_pow_guard"));
        assertCompiles(result.vertex(), Shaderc.shaderc_vertex_shader);
        assertCompiles(result.fragment(), Shaderc.shaderc_fragment_shader);
    }

    @Test
    void fractionalPowGuardPassesThroughACallItCannotParse() {
        String fragment = """
                #version 120
                varying vec2 uv;
                void main() { gl_FragColor = vec4(pow(uv.x)); }
                """;
        ModGlslTranslator.Result result = translateWithPowGuard(true, QUAD_VERTEX, fragment);
        // Not compiled here: shaderc refuses the source's own one-argument pow, as it did before the
        // guard existed. The pass's contract is only that it does not refuse or rewrite what it
        // cannot prove.
        assertNull(result.failure(), () -> "unexpected failure: " + result.failure());
        assertTrue(result.fragment().contains("pow(uv.x)"),
                () -> "a one-argument call is passed through verbatim:\n" + result.fragment());
        assertFalse(result.fragment().contains("celtium_pow_guard"));
    }
}
