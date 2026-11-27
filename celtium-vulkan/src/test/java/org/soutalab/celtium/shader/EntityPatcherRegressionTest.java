package org.soutalab.celtium.shader;

import org.junit.jupiter.api.Test;
import org.soutalab.celtium.shader.compiler.PackSpirvCompiler;
import org.soutalab.celtium.shader.model.InputAvailability;
import org.soutalab.celtium.shader.transform.PatchShaderType;
import org.soutalab.celtium.shader.transform.TransformPatcher;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression: BSL's gbuffers_entities declares {@code uniform int entityId;} in the vertex
 * stage without reading it there (all reads are in the fragment stage). EntityPatcher used
 * to skip declared-but-unused id uniforms, leaving the declaration in the vertex stage; the
 * lowering translator builds one uniform block shared by both stages from surviving uniform
 * declarations, so the name collided with the fragment stage's localized variable
 * ("'entityId' : redefinition" / "can't modify a uniform"). The unused uniform declaration
 * must be removed too.
 */
class EntityPatcherRegressionTest {

    private static final String VSH = """
            #version 120
            uniform int entityId;
            varying vec2 texcoord;
            varying vec4 tint;
            void main() {
                texcoord = gl_MultiTexCoord0.xy;
                tint = gl_Color;
                gl_Position = ftransform();
            }
            """;

    private static final String FSH = """
            #version 120
            uniform sampler2D gtexture;
            uniform int entityId;
            varying vec2 texcoord;
            varying vec4 tint;
            void main() {
                float m = entityId == 10100 ? 0.0 : 1.0;
                gl_FragData[0] = texture2D(gtexture, texcoord) * tint * m;
            }
            """;

    @Test
    void declaredButUnusedEntityIdUniformIsRemovedFromTheVertexStage() {
        Map<PatchShaderType, String> transformed = TransformPatcher.patchAttributes(
                VSH, null, FSH, InputAvailability.ALL);
        String vsh = transformed.get(PatchShaderType.VERTEX);
        String fsh = transformed.get(PatchShaderType.FRAGMENT);
        assertFalse(vsh.contains("uniform int entityId"),
                "unused entityId uniform survived the vertex transform:\n" + vsh);
        assertTrue(fsh.contains("int entityId;"),
                "fragment stage must localize entityId:\n" + fsh);

        PackSpirvCompiler.CompiledProgram compiled = PackSpirvCompiler.SHADERC.compileGraphics(
                "gbuffers_entities", vsh, fsh, null);
        assertTrue(compiled.spirv().vertexWords().length > 0);
        assertTrue(compiled.spirv().fragmentWords().length > 0);
    }
}
