package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;
import org.soutalab.celtium.vulkan.gl.ModGlslTranslator;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
        assertNull(VulkanModProgramPipeline.unsupportedAttributes(attributes,
                VulkanImmediateFormats.POSITION_TEX_COLOR));
        assertEquals(VulkanModProgramPipeline.Source.POSITION,
                VulkanModProgramPipeline.source(attributes.get(0), VulkanImmediateFormats.POSITION_TEX_COLOR));
        assertEquals(VulkanModProgramPipeline.Source.COLOR,
                VulkanModProgramPipeline.source(attributes.get(1), VulkanImmediateFormats.POSITION_TEX_COLOR));
        assertEquals(VulkanModProgramPipeline.Source.TEXCOORD,
                VulkanModProgramPipeline.source(attributes.get(2), VulkanImmediateFormats.POSITION_TEX_COLOR));
    }

    @Test
    void theOffsetsAreTheFormatsOwn() {
        // Load-bearing (TerrainVertexLayoutTest's rule): a colour described at the wrong offset reads a neighbour's
        // bytes and draws the wrong colour with no Vulkan error. POSITION_TEX_COLOR is position for 12 bytes, then the
        // UV's two floats, then four colour bytes, ending exactly at the 24-byte stride.
        assertEquals(0, VulkanModProgramPipeline.Source.POSITION.offset);
        assertEquals(VK_FORMAT_R32G32B32_SFLOAT, VulkanModProgramPipeline.Source.POSITION.format);
        assertEquals(12, VulkanModProgramPipeline.Source.TEXCOORD.offset);
        assertEquals(VK_FORMAT_R32G32_SFLOAT, VulkanModProgramPipeline.Source.TEXCOORD.format);
        assertEquals(20, VulkanModProgramPipeline.Source.COLOR.offset);
        assertEquals(VK_FORMAT_R8G8B8A8_UNORM, VulkanModProgramPipeline.Source.COLOR.format);
        assertEquals(VulkanModProgramPipeline.Source.TEXCOORD.offset + 8,
                VulkanModProgramPipeline.Source.COLOR.offset);
        assertEquals(VulkanImmediateTexturePipeline.VERTEX_BYTES,
                VulkanModProgramPipeline.Source.COLOR.offset + 4,
                "the colour ends at the stride; a fifth byte would be the next vertex's position");
    }

    @Test
    void aColourAttributeIsRefusedByTheColorlessLayout() {
        // POSITION_TEX (20 bytes) carries no colour element at all. The refusal names the attribute and the layout, so
        // a leg reads which one asked for what the batch does not have.
        String refusal = VulkanModProgramPipeline.unsupportedAttributes(glsmAttributes(),
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
                VulkanModProgramPipeline.unsupportedAttributes(result.attributes(),
                        VulkanImmediateFormats.POSITION_TEX_COLOR));
        assertNull(VulkanModProgramPipeline.source(new ModGlslTranslator.Attribute("a_Normal", "vec3", 4),
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
        assertNull(VulkanModProgramPipeline.unsupportedAttributes(result.attributes(),
                VulkanImmediateFormats.POSITION_TEX_COLOR));
        assertEquals(VulkanModProgramPipeline.Source.POSITION,
                VulkanModProgramPipeline.source(new ModGlslTranslator.Attribute("position", "vec3", 0),
                        VulkanImmediateFormats.POSITION_TEX_COLOR));
        assertEquals(VulkanModProgramPipeline.Source.POSITION,
                VulkanModProgramPipeline.source(new ModGlslTranslator.Attribute("celtium_Vertex", "vec4", 1),
                        VulkanImmediateFormats.POSITION_TEX_COLOR));
        assertEquals(VulkanModProgramPipeline.Source.TEXCOORD,
                VulkanModProgramPipeline.source(new ModGlslTranslator.Attribute("celtium_MultiTexCoord0", "vec4", 1),
                        VulkanImmediateFormats.POSITION_TEX_COLOR));
        assertEquals(VulkanModProgramPipeline.Source.COLOR,
                VulkanModProgramPipeline.source(new ModGlslTranslator.Attribute("celtium_Color", "vec4", 2),
                        VulkanImmediateFormats.POSITION_TEX_COLOR));
    }

    @Test
    void aLayoutThatMapsNoAttributesIsRefusedBeforeAnyAttributeIsRead() {
        assertEquals("batch format 3 is not mapped onto program attributes",
                VulkanModProgramPipeline.unsupportedAttributes(List.of(), VulkanImmediateFormats.BLOCK));
    }
}
