package org.soutalab.celtium.shader;

import org.junit.jupiter.api.Test;
import org.soutalab.celtium.core.shader.TerrainVertexRequirements;
import org.soutalab.celtium.shader.model.InputAvailability;
import org.soutalab.celtium.shader.transform.CeltiumTerrainTransformer;
import org.soutalab.celtium.shader.transform.PatchShaderType;
import org.soutalab.celtium.shader.transform.TransformPatcher;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CeltiumTerrainTransformerTest {

    @Test
    void detectsTerrainVertexRequirementsAccurately() {
        TerrainVertexRequirements none = CeltiumTerrainTransformer.detectVertexRequirements(
                "#version 120\nvoid main() { gl_Position = ftransform(); }");
        assertFalse(none.mcEntity());
        assertEquals(0, none.auxiliaryBytesPerQuad());

        TerrainVertexRequirements full = CeltiumTerrainTransformer.detectVertexRequirements(
                "#version 120\nattribute vec2 mc_Entity;\nattribute vec4 at_tangent;\nattribute vec2 mc_midTexCoord;\n"
                        + "void main() { vec3 n = gl_Normal; gl_Position = ftransform(); }");
        assertTrue(full.mcEntity());
        assertTrue(full.normal());
        assertTrue(full.tangent());
        assertTrue(full.midTexCoord());
        assertEquals(2, full.auxiliaryBytesPerQuad());
    }

    @Test
    void transformsLegacyTerrainShaderToCeltiumNativeInputs() {
        String vsh = """
                #version 120
                attribute vec2 mc_Entity;
                varying vec2 texcoord;
                varying vec4 color;
                void main() {
                    texcoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
                    color = gl_Color;
                    float id = mc_Entity.x;
                    gl_Position = ftransform();
                }
                """;
        String fsh = """
                #version 120
                uniform sampler2D texture;
                varying vec2 texcoord;
                varying vec4 color;
                void main() {
                    gl_FragData[0] = texture2D(texture, texcoord) * color;
                }
                """;

        Map<PatchShaderType, String> result = TransformPatcher.patchCeltiumTerrain(vsh, null, fsh);
        assertNotNull(result);
        String patchedVsh = result.get(PatchShaderType.VERTEX);
        String patchedFsh = result.get(PatchShaderType.FRAGMENT);
        assertNotNull(patchedVsh);
        assertNotNull(patchedFsh);

        assertTrue(patchedVsh.contains("celtium_Position"), "should inject Celtium native position input");
        assertTrue(patchedVsh.contains("celtium_Color"), "should remap gl_Color to celtium_Color");
        assertTrue(patchedVsh.contains("in uint mc_Entity"), "should unpack mc_Entity from uint auxiliary column");
        assertFalse(patchedVsh.contains("_celeritas_"), "must contain zero Celeritas symbols");
        assertTrue(patchedFsh.contains("texture("), "texture2D should be rewritten to core texture()");
    }

    @Test
    void transformsCompositeAndAttributesStages() {
        String compVsh = """
                #version 120
                varying vec2 texcoord;
                void main() {
                    gl_Position = ftransform();
                    texcoord = gl_MultiTexCoord0.xy;
                }
                """;
        String compFsh = """
                #version 120
                uniform sampler2D colortex0;
                varying vec2 texcoord;
                void main() {
                    gl_FragData[0] = texture2D(colortex0, texcoord);
                }
                """;

        Map<PatchShaderType, String> comp = TransformPatcher.patchComposite(compVsh, null, compFsh);
        assertNotNull(comp);
        assertNotNull(comp.get(PatchShaderType.VERTEX));
        assertNotNull(comp.get(PatchShaderType.FRAGMENT));

        Map<PatchShaderType, String> attr = TransformPatcher.patchAttributes(compVsh, null, compFsh, InputAvailability.ALL);
        assertNotNull(attr);
        assertNotNull(attr.get(PatchShaderType.VERTEX));
        assertNotNull(attr.get(PatchShaderType.FRAGMENT));
    }
}
