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
}
