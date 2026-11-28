package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;
import org.soutalab.celtium.vulkan.shader.ModGlslTranslator;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.vulkan.VK10.VK_FORMAT_R32G32B32A32_SFLOAT;
import static org.lwjgl.vulkan.VK10.VK_FORMAT_R32G32B32_SFLOAT;
import static org.lwjgl.vulkan.VK10.VK_FORMAT_R32G32_SFLOAT;
import static org.lwjgl.vulkan.VK10.VK_FORMAT_R8G8B8A8_UNORM;

/**
 * Which batch field each program attribute reads, and which attributes a layout cannot serve at all.
 * <p>
 * The wall this pins: leg 18 of the Demonica series had program=60 compiled and created, then refused at its first
 * draw with {@code attribute a_Color has no source in format 2} - and program=766 (the same FFP family, named 768 in
 * one leg) with the identical message. Nothing was missing from the batch: format 2 is
 * {@code 3,Position,Float 2,UV,Float 4,Vertex Color,Unsigned Byte}, 24 bytes with the colour at offset 20, and GLSM's
 * fixed-function vertex shader reads that colour at location 1 (its {@code VertexFormatElement.Usage.COLOR} number).
 * The gate knew only the lowering's own names, so the GLSL 330 generator's {@code a_Color}/{@code a_TexCoord0} read as
 * "no source". The translator test that pinned those attributes ({@code ModGlslTranslatorTest},
 * {@code lowersModernInOutGlobalsAndShadercCompilesBothStages}) never asked this side of the contract, which is why the
 * mismatch survived it; the cases below lower the same generator-shaped source - its names and locations, without the
 * 192-byte matrix block, which that test and {@code ModPushConstantLimit}'s own tests already own - and then ask the
 * pipeline.
 * <p>
 * What this cannot prove: that a drawn frame looks right. It pins the naming contract, the offsets, and the refusals
 * that must stay - "this layout has no such field", the sibling of the honest >256-byte boundary.
 * <p>
 * The Iris cases below are the same wall in a second vocabulary: Demonica's programs arrive with the names Iris's
 * {@code AttributeTransformer} injects at fixed locations ({@code iris_Vertex} at 0, {@code iris_Color} at 1,
 * {@code iris_MultiTexCoord0} at 2, {@code iris_MultiTexCoord1} at 3, {@code iris_Normal} at 4), because the
 * lowering keeps a mod's own names. Tier 1 of the Demonica series measured every fullscreen-quad program refused
 * at the backend with {@code attribute iris_MultiTexCoord0 has no source in format 6} - again nothing missing from
 * the batch, only from the gate's vocabulary.
 */
class ModProgramAttributeSourceTest {

    /**
     * The interface GLSM's {@code VertexShaderGenerator} emits for a textured, per-vertex-coloured key, which is what
     * the font and text draws bind: attributes at {@code Usage} locations 0/1/2 ({@code POSITION}, {@code COLOR},
     * {@code PRIMARY_UV} - the numbers the bundled GTNHLib declares), and the texture coordinate passed through as a
     * {@code vec4} whose {@code .st} the fragment stage divides.
     */
    private static final String FFP_VERTEX = """
            #version 330 core
            layout(location = 0) in vec3 a_Position;
            layout(location = 1) in vec4 a_Color;
            layout(location = 2) in vec4 a_TexCoord0;
            out vec4 v_Color;
            out vec4 v_TexCoord0;
            void main() {
                gl_Position = vec4(a_Position, 1.0);
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

    private static List<ModGlslTranslator.Attribute> glsmAttributes() {
        ModGlslTranslator.Result result = ModGlslTranslator.translate(FFP_VERTEX, FFP_FRAGMENT);
        assertNull(result.failure(), () -> "unexpected lowering failure: " + result.failure());
        return result.attributes();
    }

    @Test
    void theFixedFunctionGeneratorsNamesReadTheFormatsFields() {
        List<ModGlslTranslator.Attribute> attributes = glsmAttributes();
        // The locations are the source's own: GLSM binds its glVertexAttribPointer calls by them.
        assertEquals(List.of(new ModGlslTranslator.Attribute("a_Position", "vec3", 0),
                new ModGlslTranslator.Attribute("a_Color", "vec4", 1),
                new ModGlslTranslator.Attribute("a_TexCoord0", "vec4", 2)), attributes);
        // Leg 18's refusal, and the attribute that would have followed it: both must read a field of format 2.
        assertNull(VulkanTerrainGpuBackend.celtium$unsupportedAttributes(attributes,
                VulkanImmediateFormats.POSITION_TEX_COLOR));
        assertEquals(VulkanTerrainGpuBackend.AttributeSource.POSITION,
                VulkanTerrainGpuBackend.celtium$attributeSource(attributes.get(0), VulkanImmediateFormats.POSITION_TEX_COLOR));
        assertEquals(VulkanTerrainGpuBackend.AttributeSource.COLOR,
                VulkanTerrainGpuBackend.celtium$attributeSource(attributes.get(1), VulkanImmediateFormats.POSITION_TEX_COLOR));
        assertEquals(VulkanTerrainGpuBackend.AttributeSource.TEXCOORD,
                VulkanTerrainGpuBackend.celtium$attributeSource(attributes.get(2), VulkanImmediateFormats.POSITION_TEX_COLOR));
    }

    @Test
    void theOffsetsAreTheFormatsOwn() {
        // Load-bearing (TerrainVertexLayoutTest's rule): a colour described at the wrong offset reads a neighbour's
        // bytes and draws the wrong colour with no Vulkan error. POSITION_TEX_COLOR is position for 12 bytes, then the
        // UV's two floats, then four colour bytes, ending exactly at the 24-byte stride.
        assertEquals(0, VulkanTerrainGpuBackend.AttributeSource.POSITION.offset);
        assertEquals(VK_FORMAT_R32G32B32_SFLOAT, VulkanTerrainGpuBackend.AttributeSource.POSITION.format);
        assertEquals(12, VulkanTerrainGpuBackend.AttributeSource.TEXCOORD.offset);
        assertEquals(VK_FORMAT_R32G32_SFLOAT, VulkanTerrainGpuBackend.AttributeSource.TEXCOORD.format);
        assertEquals(20, VulkanTerrainGpuBackend.AttributeSource.COLOR.offset);
        assertEquals(VK_FORMAT_R8G8B8A8_UNORM, VulkanTerrainGpuBackend.AttributeSource.COLOR.format);
        assertEquals(VulkanTerrainGpuBackend.AttributeSource.TEXCOORD.offset + 8,
                VulkanTerrainGpuBackend.AttributeSource.COLOR.offset);
        assertEquals(VulkanImmediateTextureRecordsPipeline.VERTEX_BYTES,
                VulkanTerrainGpuBackend.AttributeSource.COLOR.offset + 4,
                "the colour ends at the stride; a fifth byte would be the next vertex's position");
    }

    @Test
    void aColourAttributeIsRefusedByTheColorlessLayout() {
        // POSITION_TEX (20 bytes) carries no colour element at all. The refusal names the attribute and the layout, so
        // a leg reads which one asked for what the batch does not have.
        String refusal = VulkanTerrainGpuBackend.celtium$unsupportedAttributes(glsmAttributes(),
                VulkanImmediateFormats.POSITION_TEX);
        assertNotNull(refusal, "a colour read from a layout without a colour element reads the next vertex");
        assertEquals("attribute a_Color has no source in format " + VulkanImmediateFormats.POSITION_TEX, refusal);
    }

    @Test
    void aFieldThisFormatDoesNotCarryIsStillRefused() {
        // The next thing the fixed-function generator can declare is a light map coordinate (Usage.SECONDARY_UV), and
        // it still has no source in format 2. Widening the name vocabulary must not turn the gate into "anything
        // goes", so the unknown name is refused rather than answered with a field that only has its width in common.
        ModGlslTranslator.Result result = ModGlslTranslator.translate("""
                #version 330 core
                layout(location = 0) in vec3 a_Position;
                layout(location = 3) in vec4 a_TexCoord1;
                void main() {
                    gl_Position = vec4(a_Position, 1.0);
                }
                """, """
                #version 330 core
                out vec4 fragColor;
                void main() { fragColor = vec4(1.0); }
                """);
        assertNull(result.failure(), () -> "unexpected lowering failure: " + result.failure());
        assertEquals("attribute a_TexCoord1 has no source in format 2",
                VulkanTerrainGpuBackend.celtium$unsupportedAttributes(result.attributes(),
                        VulkanImmediateFormats.POSITION_TEX_COLOR));
        assertNull(VulkanTerrainGpuBackend.celtium$attributeSource(new ModGlslTranslator.Attribute("a_Normal", "vec3", 4),
                VulkanImmediateFormats.POSITION_TEX_COLOR));
    }

    @Test
    void theLoweringsOwnNamesStillResolve() {
        // A legacy source: gl_Color and gl_MultiTexCoord0 come back as the lowering's names, and a declared
        // `attribute vec3 position;` stays at location 0 under its own name - the fallback that keeps every
        // #version 120 program working. ftransform() expands to a use of gl_Vertex, so the lowering declares
        // celtium_Vertex too; it is the position, and it is the one that lands at a nonzero location here.
        ModGlslTranslator.Result result = ModGlslTranslator.translate("""
                #version 120
                attribute vec3 position;
                void main() {
                    gl_Position = ftransform();
                    gl_FrontColor = gl_Color;
                    gl_TexCoord[0] = gl_MultiTexCoord0;
                }
                """, """
                #version 120
                void main() { gl_FragColor = gl_Color; }
                """);
        assertNull(result.failure(), () -> "unexpected lowering failure: " + result.failure());
        assertEquals(List.of(new ModGlslTranslator.Attribute("position", "vec3", 0),
                new ModGlslTranslator.Attribute("celtium_Vertex", "vec4", 1),
                new ModGlslTranslator.Attribute("celtium_Color", "vec4", 2),
                new ModGlslTranslator.Attribute("celtium_MultiTexCoord0", "vec4", 3)), result.attributes());
        assertNull(VulkanTerrainGpuBackend.celtium$unsupportedAttributes(result.attributes(),
                VulkanImmediateFormats.POSITION_TEX_COLOR));
        assertEquals(VulkanTerrainGpuBackend.AttributeSource.POSITION,
                VulkanTerrainGpuBackend.celtium$attributeSource(new ModGlslTranslator.Attribute("position", "vec3", 0),
                        VulkanImmediateFormats.POSITION_TEX_COLOR));
        assertEquals(VulkanTerrainGpuBackend.AttributeSource.POSITION,
                VulkanTerrainGpuBackend.celtium$attributeSource(new ModGlslTranslator.Attribute("celtium_Vertex", "vec4", 1),
                        VulkanImmediateFormats.POSITION_TEX_COLOR));
        assertEquals(VulkanTerrainGpuBackend.AttributeSource.TEXCOORD,
                VulkanTerrainGpuBackend.celtium$attributeSource(new ModGlslTranslator.Attribute("celtium_MultiTexCoord0", "vec4", 1),
                        VulkanImmediateFormats.POSITION_TEX_COLOR));
        assertEquals(VulkanTerrainGpuBackend.AttributeSource.COLOR,
                VulkanTerrainGpuBackend.celtium$attributeSource(new ModGlslTranslator.Attribute("celtium_Color", "vec4", 2),
                        VulkanImmediateFormats.POSITION_TEX_COLOR));
    }

    /**
     * The interface Iris's {@code AttributeTransformer} injects for a fullscreen-quad program: the renames of
     * {@code gl_Vertex}, {@code gl_MultiTexCoord0} and {@code gl_MultiTexCoord1} at their fixed locations.
     */
    private static final String IRIS_VERTEX = """
            #version 330 core
            layout(location = 0) in vec4 iris_Vertex;
            layout(location = 2) in vec4 iris_MultiTexCoord0;
            layout(location = 3) in vec4 iris_MultiTexCoord1;
            out vec4 v_TexCoord0;
            void main() {
                gl_Position = iris_Vertex;
                v_TexCoord0 = iris_MultiTexCoord0 + iris_MultiTexCoord1;
            }
            """;

    private static final String IRIS_FRAGMENT = """
            #version 330 core
            in vec4 v_TexCoord0;
            uniform sampler2D u_Sampler0;
            out vec4 fragColor;
            void main() {
                fragColor = texture(u_Sampler0, v_TexCoord0.st);
            }
            """;

    private static List<ModGlslTranslator.Attribute> irisAttributes() {
        ModGlslTranslator.Result result = ModGlslTranslator.translate(IRIS_VERTEX, IRIS_FRAGMENT);
        assertNull(result.failure(), () -> "unexpected lowering failure: " + result.failure());
        return result.attributes();
    }

    @Test
    void theIrisTransformersNamesReadTheTexLayout() {
        List<ModGlslTranslator.Attribute> attributes = irisAttributes();
        // The locations are the transformer's own fixed injection points.
        assertEquals(List.of(new ModGlslTranslator.Attribute("iris_Vertex", "vec4", 0),
                new ModGlslTranslator.Attribute("iris_MultiTexCoord0", "vec4", 2),
                new ModGlslTranslator.Attribute("iris_MultiTexCoord1", "vec4", 3)), attributes);
        // Tier 1's refusal: format 6 carries the UV at offset 12, so all three names must read a field of it.
        assertNull(VulkanTerrainGpuBackend.celtium$unsupportedAttributes(attributes,
                VulkanImmediateFormats.POSITION_TEX));
        assertEquals(VulkanTerrainGpuBackend.AttributeSource.POSITION,
                VulkanTerrainGpuBackend.celtium$attributeSource(attributes.get(0), VulkanImmediateFormats.POSITION_TEX));
        assertEquals(VulkanTerrainGpuBackend.AttributeSource.TEXCOORD,
                VulkanTerrainGpuBackend.celtium$attributeSource(attributes.get(1), VulkanImmediateFormats.POSITION_TEX));
        // The quad path folds the second coordinate's attribute (also offset 12) onto the one UV, so it reads
        // the same field on this layout.
        assertEquals(VulkanTerrainGpuBackend.AttributeSource.TEXCOORD,
                VulkanTerrainGpuBackend.celtium$attributeSource(attributes.get(2), VulkanImmediateFormats.POSITION_TEX));
        // Without the second coordinate, the same names also serve the colour layout, whose UV is the same field.
        assertNull(VulkanTerrainGpuBackend.celtium$unsupportedAttributes(attributes.subList(0, 2),
                VulkanImmediateFormats.POSITION_TEX_COLOR));
        assertEquals(VulkanTerrainGpuBackend.AttributeSource.TEXCOORD,
                VulkanTerrainGpuBackend.celtium$attributeSource(attributes.get(1), VulkanImmediateFormats.POSITION_TEX_COLOR));
    }

    @Test
    void theIrisSecondTexCoordIsRefusedByTheTexColorLayout() {
        // Format 2 has no second UV field at all - the ground on which a_TexCoord1 stays refused above - so the
        // fold that POSITION_TEX permits must not leak into it.
        List<ModGlslTranslator.Attribute> attributes = irisAttributes();
        assertNull(VulkanTerrainGpuBackend.celtium$attributeSource(attributes.get(2),
                VulkanImmediateFormats.POSITION_TEX_COLOR));
        assertEquals("attribute iris_MultiTexCoord1 has no source in format "
                        + VulkanImmediateFormats.POSITION_TEX_COLOR,
                VulkanTerrainGpuBackend.celtium$unsupportedAttributes(attributes,
                        VulkanImmediateFormats.POSITION_TEX_COLOR));
    }

    @Test
    void theIrisColourAndNormalKeepTheirAnswers() {
        // iris_Color is gl_Color renamed: it reads the colour field where a layout carries one, and stays refused
        // where none does. Serving a colour value no field carries is the constant-attribute question, which this
        // gate does not answer.
        ModGlslTranslator.Attribute irisColor = new ModGlslTranslator.Attribute("iris_Color", "vec4", 1);
        assertEquals(VulkanTerrainGpuBackend.AttributeSource.COLOR,
                VulkanTerrainGpuBackend.celtium$attributeSource(irisColor, VulkanImmediateFormats.POSITION_TEX_COLOR));
        assertNull(VulkanTerrainGpuBackend.celtium$attributeSource(irisColor, VulkanImmediateFormats.POSITION_TEX));
        assertEquals("attribute iris_Color has no source in format " + VulkanImmediateFormats.POSITION_TEX,
                VulkanTerrainGpuBackend.celtium$unsupportedAttributes(List.of(irisColor),
                        VulkanImmediateFormats.POSITION_TEX));
        // Neither layout carries a normal field, so iris_Normal stays refused on both.
        ModGlslTranslator.Attribute irisNormal = new ModGlslTranslator.Attribute("iris_Normal", "vec3", 4);
        assertNull(VulkanTerrainGpuBackend.celtium$attributeSource(irisNormal, VulkanImmediateFormats.POSITION_TEX));
        assertNull(VulkanTerrainGpuBackend.celtium$attributeSource(irisNormal, VulkanImmediateFormats.POSITION_TEX_COLOR));
        assertEquals("attribute iris_Normal has no source in format " + VulkanImmediateFormats.POSITION_TEX,
                VulkanTerrainGpuBackend.celtium$unsupportedAttributes(List.of(irisNormal),
                        VulkanImmediateFormats.POSITION_TEX));
    }

    @Test
    void aLayoutThatMapsNoAttributesIsRefusedBeforeAnyAttributeIsRead() {
        assertEquals("batch format 3 is not mapped onto program attributes",
                VulkanTerrainGpuBackend.celtium$unsupportedAttributes(List.of(), VulkanImmediateFormats.BLOCK));
    }

    @Test
    void aPositionOnlyProgramReadsThePositionLayout() {
        // Iris's horizon mesh arrives in POSITION (id 7): twelve bytes of position and nothing else, submitted
        // every frame. A program whose only attribute is the position reads it - the gate used to refuse the
        // whole format before any attribute was read, so even this program never drew.
        List<ModGlslTranslator.Attribute> attributes =
                List.of(new ModGlslTranslator.Attribute("iris_Vertex", "vec4", 0));
        assertNull(VulkanTerrainGpuBackend.celtium$unsupportedAttributes(attributes,
                VulkanImmediateFormats.POSITION));
        assertEquals(VulkanTerrainGpuBackend.AttributeSource.POSITION,
                VulkanTerrainGpuBackend.celtium$attributeSource(attributes.get(0), VulkanImmediateFormats.POSITION));
        // The position field (three floats at offset 0) ends exactly at the layout's stride; a fourth float
        // would be the next vertex.
        assertEquals(VulkanImmediateTextureRecordsPipeline.POSITION_BYTES,
                VulkanTerrainGpuBackend.AttributeSource.POSITION.offset + 12,
                "the position field ends at the position-only stride");
    }

    @Test
    void thePositionLayoutRefusesFieldsItDoesNotCarry() {
        // A texture coordinate the batch does not carry is refused (the colour is the one field GL's current
        // value answers on this layout: see theSkyProgramReadsTheCurrentColourAsAConstantAttribute).
        // A texture coordinate is refused the same way: offset 12 in a 12-byte vertex is the next vertex's
        // position, so answering the name with the UV field would read a neighbour's bytes.
        assertNull(VulkanTerrainGpuBackend.celtium$attributeSource(
                new ModGlslTranslator.Attribute("iris_MultiTexCoord0", "vec4", 2),
                VulkanImmediateFormats.POSITION));
        assertEquals("attribute iris_MultiTexCoord0 has no source in format " + VulkanImmediateFormats.POSITION,
                VulkanTerrainGpuBackend.celtium$unsupportedAttributes(
                        List.of(new ModGlslTranslator.Attribute("iris_MultiTexCoord0", "vec4", 2)),
                        VulkanImmediateFormats.POSITION));
        // The second coordinate stays refused here too (it is answered only by POSITION_TEX's fold).
        assertNull(VulkanTerrainGpuBackend.celtium$attributeSource(
                new ModGlslTranslator.Attribute("iris_MultiTexCoord1", "vec4", 3),
                VulkanImmediateFormats.POSITION));
    }

    @Test
    void theSkyProgramReadsTheCurrentColourAsAConstantAttribute() {
        // The horizon's own program (Complementary's sky basic) also reads a colour, which GL supplies from the
        // current generic attribute - the pipeline sets it to the fog colour just before the draw - not from the
        // batch. The colour names answer it from the constant ring: four floats on binding 1 at stride 0.
        List<ModGlslTranslator.Attribute> skyBasic = List.of(
                new ModGlslTranslator.Attribute("iris_Vertex", "vec4", 0),
                new ModGlslTranslator.Attribute("iris_Color", "vec4", 1));
        assertNull(VulkanTerrainGpuBackend.celtium$unsupportedAttributes(skyBasic, VulkanImmediateFormats.POSITION));
        assertEquals(VulkanTerrainGpuBackend.AttributeSource.CONSTANT_COLOR,
                VulkanTerrainGpuBackend.celtium$attributeSource(skyBasic.get(1), VulkanImmediateFormats.POSITION));
        assertEquals(VulkanTerrainGpuBackend.CONSTANT_BINDING,
                VulkanTerrainGpuBackend.AttributeSource.CONSTANT_COLOR.binding());
        assertEquals(0, VulkanTerrainGpuBackend.AttributeSource.POSITION.binding());
        assertEquals(VK_FORMAT_R32G32B32A32_SFLOAT, VulkanTerrainGpuBackend.AttributeSource.CONSTANT_COLOR.format);
        assertEquals(0, VulkanTerrainGpuBackend.AttributeSource.CONSTANT_COLOR.offset);
        assertEquals(16, VulkanTerrainGpuBackend.CONSTANT_SLOT_BYTES);
        assertTrue(VulkanTerrainGpuBackend.celtium$readsConstantAttributes(skyBasic, VulkanImmediateFormats.POSITION));
        // The colour layout keeps its own field, and the UV layout still refuses a colour.
        assertFalse(VulkanTerrainGpuBackend.celtium$readsConstantAttributes(skyBasic, VulkanImmediateFormats.POSITION_TEX_COLOR));
        assertEquals(VulkanTerrainGpuBackend.AttributeSource.COLOR,
                VulkanTerrainGpuBackend.celtium$attributeSource(skyBasic.get(1), VulkanImmediateFormats.POSITION_TEX_COLOR));
        assertNull(VulkanTerrainGpuBackend.celtium$attributeSource(skyBasic.get(1), VulkanImmediateFormats.POSITION_TEX));
        // A position-only program builds no ring.
        assertFalse(VulkanTerrainGpuBackend.celtium$readsConstantAttributes(List.of(skyBasic.get(0)), VulkanImmediateFormats.POSITION));
    }
}
