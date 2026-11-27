package org.soutalab.celtium.shader;

import org.junit.jupiter.api.Test;
import org.soutalab.celtium.shader.compiler.PackSpirvCompiler;
import org.soutalab.celtium.shader.model.InputAvailability;
import org.soutalab.celtium.shader.transform.PatchShaderType;
import org.soutalab.celtium.shader.transform.TransformPatcher;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Complementary's line program reads the upstream-Iris attribute names
 * vaPosition / vaNormal / vaUV0, which the transform must declare as aliases
 * of the iris_ attribute seats (same locations). Before the alias injection
 * the real ComplementaryReimagined r5.9.3 pack failed to compile with
 * 'vaPosition' : undeclared identifier.
 */
class VaAttributeAliasTest {

    private static final String LINE_VSH = """
            #version 120
            varying vec2 texcoord;
            void main() {
                texcoord = vaUV0;
                vec4 start = gl_ProjectionMatrix * gl_ModelViewMatrix * vec4(vaPosition, 1.0);
                vec4 end = gl_ProjectionMatrix * gl_ModelViewMatrix * vec4(vaPosition + vaNormal, 1.0);
                gl_Position = start + end;
            }
            """;

    private static final String LINE_FSH = """
            #version 120
            varying vec2 texcoord;
            void main() {
                gl_FragColor = vec4(texcoord, 0.0, 1.0);
            }
            """;

    @Test
    void vaAliasesAreDeclaredAndTheLineShapeCompiles() {
        Map<PatchShaderType, String> transformed = TransformPatcher.patchAttributes(
                LINE_VSH, null, LINE_FSH, InputAvailability.ALL);
        assertNotNull(transformed);
        String vsh = transformed.get(PatchShaderType.VERTEX);
        assertNotNull(vsh);
        assertTrue(vsh.contains("layout(location = 0) in vec3 vaPosition;"), vsh);
        assertTrue(vsh.contains("layout(location = 4) in vec3 vaNormal;"), vsh);
        assertTrue(vsh.contains("layout(location = 2) in vec2 vaUV0;"), vsh);
        PackSpirvCompiler.CompiledProgram compiled = PackSpirvCompiler.SHADERC.compileGraphics(
                "gbuffers_line", vsh, transformed.get(PatchShaderType.FRAGMENT), null);
        assertNotNull(compiled);
    }
}
