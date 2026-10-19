package org.soutalab.celtium.shaderpack;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyGlslLoweringTest {
    private static final String LEGACY_VERTEX = """
            #version 120
            // A pack's vertex stage: fixed-function inputs, a varying, a matrix and ftransform.
            attribute vec4 mc_Entity;
            varying vec2 texcoord;
            varying vec4 tint;

            void main() {
                texcoord = gl_MultiTexCoord0.xy;
                tint = gl_Color;
                gl_Position = ftransform();
                if (mc_Entity.x > 0.0) {
                    gl_Position.y += 0.001;
                }
            }
            """;

    private static final String LEGACY_FRAGMENT = """
            #version 120
            uniform sampler2D texture;
            varying vec2 texcoord;
            varying vec4 tint;

            void main() {
                vec4 colour = texture2D(texture, texcoord) * tint;
                gl_FragColor = colour;
            }
            """;

    @Test
    void rewritesTheOptifineInterface() {
        LegacyGlslLowering.Result vertex = LegacyGlslLowering.lower(Stage.VERTEX, LEGACY_VERTEX, Map.of());
        assertTrue(vertex.source().contains("#version 450"), "version was not raised");
        assertFalse(vertex.source().contains("attribute "), "attribute survived");
        assertFalse(vertex.source().contains("varying "), "varying survived");
        assertTrue(vertex.source().contains("layout(location = 0) in vec4 mc_Entity;"), vertex.source());
        assertTrue(vertex.source().contains("gl_Position = celtium_ftransform(a_Position);"), vertex.source());
        assertTrue(vertex.source().contains("layout(set = 0, binding = 0, std140) uniform CeltiumTransforms"),
                vertex.source());
        assertEquals(Integer.valueOf(0), vertex.attributes().get("mc_Entity"));
        assertEquals(2, vertex.varyings().size());
        assertEquals(120, vertex.originalVersion());
    }

    @Test
    void fragmentStageGetsAnOutputAndVulkanTextureCalls() {
        LegacyGlslLowering.Result fragment = LegacyGlslLowering.lower(Stage.FRAGMENT, LEGACY_FRAGMENT, Map.of());
        assertFalse(fragment.source().contains("gl_FragColor"), "gl_FragColor survived");
        assertFalse(fragment.source().contains("texture2D("), "texture2D survived");
        assertTrue(fragment.source().contains("layout(location = 0) out vec4 celtium_FragColour;"), fragment.source());
        // The sampler is named `texture`, which the lowered call cannot keep: `texture(texture, uv)` resolves the name to
        // the variable. The pass renames it and the call follows.
        assertTrue(fragment.source().contains("layout(binding = 0) uniform sampler2D celtium_sampler_texture;"),
                fragment.source());
        assertTrue(fragment.source().contains("texture(celtium_sampler_texture, texcoord)"), fragment.source());
        assertTrue(fragment.varyings().get(0).startsWith("in "), "fragment varyings must be inputs: " + fragment.varyings());
    }

    @Test
    void leavesCommentsAndLongerNamesAlone() {
        String source = """
                #version 120
                // gl_Vertex in a comment stays a comment
                attribute vec4 gl_VertexNotReally;
                void main() {
                    vec4 v = gl_Vertex; // trailing note
                    gl_Position = v;
                }
                """;
        LegacyGlslLowering.Result result = LegacyGlslLowering.lower(Stage.VERTEX, source, Map.of());
        assertTrue(result.source().contains("// gl_Vertex in a comment stays a comment"), result.source());
        assertTrue(result.source().contains("// trailing note"), result.source());
        assertTrue(result.source().contains("gl_VertexNotReally"), "a longer name was rewritten");
        assertTrue(result.source().contains("vec4 v = a_Position;"), result.source());
    }

    @Test
    void reportsWhatItCannotLower() {
        String source = """
                #version 120
                #extension GL_ARB_shader_texture_lod : enable
                void main() {
                    gl_FrontColor = vec4(1.0);
                    gl_Position = gl_Vertex;
                }
                """;
        LegacyGlslLowering.Result result = LegacyGlslLowering.lower(Stage.VERTEX, source, Map.of());
        assertFalse(result.unsupported().isEmpty(), "the fixed-function interpolant was not reported");
    }

    /**
     * A local that shares a uniform's name shadows it in its own function only (audit #376). The stage-wide set this used
     * to be made main's reference to the global uniform - the one the original shader meant - skip the rewrite, and the
     * compiler reported it as an undeclared identifier.
     */
    @Test
    void aLocalShadowingAUniformDoesNotHideTheGlobalReference() {
        String source = """
                #version 120
                uniform float gain;
                float helper() {
                  float gain = 2.0;
                  return gain;
                }
                void main() {
                  gl_FragColor = vec4(gain);
                }
                """;
        LegacyGlslLowering.Result result = LegacyGlslLowering.lower(Stage.FRAGMENT, source, Map.of());
        assertTrue(result.source().contains("vec4(celtium_uniforms.gain)"),
                "main's reference is the uniform: " + result.source());
        assertTrue(result.source().contains("float gain = 2.0;"),
                "the local declaration stays as the pack wrote it: " + result.source());
        assertTrue(result.source().contains("return gain;"),
                "the local's own use is not rewritten: " + result.source());
        assertTrue(result.usesPlainUniformBlock(), result.source());
    }

    /** A comma declaration of two samplers needs two bindings, not one shared by both (audit #377). */
    @Test
    void commaDeclaredSamplersGetTheirOwnBindings() {
        String source = """
                #version 120
                uniform sampler2D first, second;
                void main() {
                    gl_FragColor = texture2D(first, vec2(0.0)) + texture2D(second, vec2(0.0));
                }
                """;
        LegacyGlslLowering.Result result = LegacyGlslLowering.lower(Stage.FRAGMENT, source, Map.of(),
                Map.of("first", Integer.valueOf(2), "second", Integer.valueOf(3)), Map.of(), false, java.util.List.of());
        String lowered = result.source();
        assertTrue(lowered.contains("layout(binding = 2) uniform sampler2D first;"), lowered);
        assertTrue(lowered.contains("layout(binding = 3) uniform sampler2D second;"), lowered);
        assertFalse(lowered.contains("sampler2D first, second"), lowered);
    }

    /**
     * The plain-uniform block is its own metadata (audit #378): a shader with plain uniforms and no matrix emits binding 1
     * and reported an empty uniforms() list, which is what the descriptor layout was decided from.
     */
    @Test
    void theplainUniformBlockIsReportedSeparatelyFromTheTransformBlock() {
        LegacyGlslLowering.Result plainOnly = LegacyGlslLowering.lower(Stage.FRAGMENT, """
                #version 120
                uniform float gain;
                void main() { gl_FragColor = vec4(gain); }
                """, Map.of());
        assertTrue(plainOnly.usesPlainUniformBlock(), plainOnly.source());
        assertTrue(plainOnly.uniforms().isEmpty(), "no matrices, so no transform list: " + plainOnly.uniforms());

        LegacyGlslLowering.Result transformOnly = LegacyGlslLowering.lower(Stage.VERTEX, """
                #version 120
                void main() { gl_Position = ftransform(); }
                """, Map.of(), Map.of(), Map.of(), true, java.util.List.of());
        assertFalse(transformOnly.usesPlainUniformBlock(), transformOnly.source());
        assertFalse(transformOnly.uniforms().isEmpty(), "the transform block is in use");
    }
}
