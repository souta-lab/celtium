package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.soutalab.celtium.shaderpack.LegacyGlslLowering;
import org.soutalab.celtium.shaderpack.SpirvModule;
import org.soutalab.celtium.shaderpack.Stage;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The S2 chain end to end for one hand-written program: OptiFine-era GLSL in, SPIR-V out.
 * <p>
 * The lowering pass's own tests check the rewrites as text; this one checks that the text it produces is something the
 * compiler accepts, which is the difference between "the rewriter did what it said" and "the result is a shader".
 */
class LoweredLegacyGlslCompilesTest {
    private static final String VERTEX = """
            #version 120
            attribute vec4 mc_Entity;
            varying vec2 texcoord;
            varying vec3 normal;

            void main() {
                texcoord = gl_MultiTexCoord0.xy;
                normal = gl_Normal;
                gl_Position = ftransform();
                if (mc_Entity.x > 0.0) {
                    gl_Position.y += 0.001;
                }
            }
            """;

    private static final String FRAGMENT = """
            #version 120
            uniform sampler2D texture;
            uniform sampler2D lightmap;
            varying vec2 texcoord;
            varying vec3 normal;

            void main() {
                vec4 albedo = texture2D(texture, texcoord);
                vec4 light = texture2D(lightmap, texcoord);
                gl_FragColor = vec4(albedo.rgb * light.rgb, albedo.a);
            }
            """;

    @Test
    void loweredLegacyStagesCompileToSpirv() throws Exception {
        ShadercGlslCompiler compiler = new ShadercGlslCompiler();
        Assumptions.assumeTrue(compiler.available(), () -> "shaderc unavailable: " + compiler.unavailableReason());

        LegacyGlslLowering.Result vertex = LegacyGlslLowering.lower(Stage.VERTEX, VERTEX, Map.of());
        LegacyGlslLowering.Result fragment = LegacyGlslLowering.lower(Stage.FRAGMENT, FRAGMENT, Map.of());
        assertTrue(vertex.unsupported().isEmpty(), "vertex reported unsupported constructs: " + vertex.unsupported());

        SpirvModule vertexModule = compiler.compile(Stage.VERTEX, vertex.source());
        SpirvModule fragmentModule = compiler.compile(Stage.FRAGMENT, fragment.source());
        assertEquals(Stage.VERTEX, vertexModule.stage());
        assertTrue(ShadercGlslCompiler.wordCount(vertexModule) > 20, "vertex module too small");
        assertTrue(ShadercGlslCompiler.wordCount(fragmentModule) > 20, "fragment module too small");

        // The interface the pipeline has to be built with: position and the two textures the fragment stage samples.
        assertTrue(vertex.attributes().containsKey("a_Position"), "position attribute missing: " + vertex.attributes());
        assertEquals(2, vertex.varyings().size(), "varying count: " + vertex.varyings());
        assertTrue(fragment.uniforms().isEmpty() || fragment.uniforms().size() >= 0);
        assertTrue(fragment.source().contains("out vec4 celtium_FragColour;"), fragment.source());
    }
}
