package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;
import org.soutalab.celtium.vulkan.RenderTargetRegistry.TargetFormatClass;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Pins the D18-A mapping (QUEUE 394): a GL internal format to {@link TargetFormatClass}, and the refusal for
 * everything the mapping does not name.
 * <p>
 * The GLenums below are the values lwjgl-opengl 3.4.3 declares, quoted next to each case, so a wrong table entry fails
 * here rather than on a device. The unmapped formats are pinned as {@code null} for the reason recorded in
 * {@link TargetFormatClass#forGlInternalFormat}: the caller falls back to {@code RGBA8_UNORM} and counts the fallback,
 * which is only possible while "no class" and "the default class" stay distinguishable.
 * <p>
 * What this test cannot say: that a 16F pair is created on a device, that the render pass accepts it, or that the
 * picture changes. Those are the wiring (item 394's pair generation and render-pass variant) and the banding A/B
 * (item 395 and the leg), not this table.
 */
class TargetFormatMappingTest {

    // "Enumerated" = the rows of ITEM394-READINESS §2.1 that name both a GLenum and a class in the enum.
    private static final int GL_RGBA8 = 0x8058;
    private static final int GL_RGBA = 0x1908;
    private static final int GL_RGBA16F = 0x881A;
    private static final int GL_RGBA32F = 0x8814;
    private static final int GL_R11F_G11F_B10F = 0x8C3A;

    @Test
    void mapsTheEnumeratedColorFormats() {
        assertSame(TargetFormatClass.RGBA8_UNORM, TargetFormatClass.forGlInternalFormat(GL_RGBA8));
        assertSame(TargetFormatClass.RGBA8_UNORM, TargetFormatClass.forGlInternalFormat(GL_RGBA));
        assertSame(TargetFormatClass.RGBA16_FLOAT, TargetFormatClass.forGlInternalFormat(GL_RGBA16F));
        assertSame(TargetFormatClass.RGBA32_FLOAT, TargetFormatClass.forGlInternalFormat(GL_RGBA32F));
        assertSame(TargetFormatClass.R11G11B10_FLOAT, TargetFormatClass.forGlInternalFormat(GL_R11F_G11F_B10F));
    }

    /** The class each mapped format allocates must be the Vk format the readiness table pinned for it. */
    @Test
    void mappedClassesCarryThePinnedVkFormats() {
        assertEquals(37, TargetFormatClass.forGlInternalFormat(GL_RGBA8).vkFormat(), "VK_FORMAT_R8G8B8A8_UNORM");
        assertEquals(97, TargetFormatClass.forGlInternalFormat(GL_RGBA16F).vkFormat(),
                "VK_FORMAT_R16G16B16A16_SFLOAT");
        assertEquals(109, TargetFormatClass.forGlInternalFormat(GL_RGBA32F).vkFormat(),
                "VK_FORMAT_R32G32B32A32_SFLOAT");
        assertEquals(122, TargetFormatClass.forGlInternalFormat(GL_R11F_G11F_B10F).vkFormat(),
                "VK_FORMAT_B10G11R11_UFLOAT_PACK32");
    }

    /**
     * The remainders: formats the readiness table names as having no class, plus the unsized/sized channel formats the
     * readiness table does not enumerate. All of them must refuse, so the caller counts a fallback instead of the
     * table pretending the request was met.
     */
    @Test
    void refusesFormatsWithNoClassInTheVocabulary() {
        int[] unmapped = {
                0x8059, // GL_RGB10_A2 - would be VK_FORMAT_A2B10G10R10_UNORM_PACK32 (64); no class in the enum
                0x805B, // GL_RGBA16 - would be VK_FORMAT_R16G16B16A16_UNORM (91); no class in the enum
                0x8C43, // GL_SRGB8_ALPHA8 - would be VK_FORMAT_R8G8B8A8_SRGB (43); no sRGB class
                0x8F97, // GL_RGBA8_SNORM - signed 8-bit; no class
                0x8D62, // GL_RGB565 - 16-bit packed; no class
                0x8051, // GL_RGB8 - three channels; the vocabulary is RGBA-shaped
                0x1907, // GL_RGB - unsized three-channel base format
                0x8229, // GL_R8
                0x8223, // GL_RG8
                0x8227, // GL_RG (unsized two-channel)
                0x822D, // GL_R16F
                0x822F, // GL_RG16F
                0x822E, // GL_R32F
                0x881B, // GL_RGB16F
                0x0000, // no recorded internal format (the stub's "nothing was recorded" answer)
                0xFFFFFFFF, // a value no GL enum carries
        };
        for (int internal : unmapped) {
            assertNull(TargetFormatClass.forGlInternalFormat(internal),
                    "internal format 0x" + Integer.toHexString(internal) + " must not be mapped");
        }
    }

    /**
     * Depth requests refuse too: a frame's depth/stencil format is {@link VulkanDepthFormat}'s answer from the
     * device's feature set, so this table must not offer a second opinion. These are the stub's
     * {@code isDepthInternalFormat} values.
     */
    @Test
    void refusesDepthInternalFormats() {
        int[] depth = {0x1902, 0x81A5, 0x81A6, 0x81A7, 0x8CAC, 0x88F0, 0x8CAD, 0x84F9};
        for (int internal : depth) {
            assertNull(TargetFormatClass.forGlInternalFormat(internal),
                    "depth internal format 0x" + Integer.toHexString(internal) + " must not map to a color class");
        }
    }

    /**
     * A refused request falls back to {@code RGBA8_UNORM} (readiness §2.2 rule 2), and that class is the format the
     * renderer's targets already carry - so "fell back" is the picture that ships today, not a new one.
     */
    @Test
    void theFallbackTargetIsTheFormatTheFrameAlreadyUses() {
        assertSame(TargetFormatClass.RGBA8_UNORM, TargetFormatClass.forGlInternalFormat(GL_RGBA8));
        assertEquals(37, TargetFormatClass.RGBA8_UNORM.vkFormat(), "VK_FORMAT_R8G8B8A8_UNORM, the frame colour");
        assertFalse(TargetFormatClass.RGBA8_UNORM.isDepth());
        assertEquals(4, TargetFormatClass.RGBA8_UNORM.bytesPerPixel());
    }

    @Test
    void bytesPerPixelIsTheStorageSize() {
        assertEquals(4, TargetFormatClass.RGBA8_UNORM.bytesPerPixel());
        assertEquals(8, TargetFormatClass.RGBA16_FLOAT.bytesPerPixel());
        assertEquals(16, TargetFormatClass.RGBA32_FLOAT.bytesPerPixel());
        assertEquals(4, TargetFormatClass.R11G11B10_FLOAT.bytesPerPixel(), "packed 10/11/11 bits = 32 bits");
        assertEquals(4, TargetFormatClass.DEPTH32_FLOAT.bytesPerPixel());
        assertEquals(4, TargetFormatClass.DEPTH24_STENCIL8.bytesPerPixel(), "24 depth + 8 stencil");
    }

    /** One 1280x720 colour image, the leg's scene: the figures ITEM357-AUDIT §1.4 quotes (3.52 MiB vs 7.03 MiB). */
    @Test
    void oneFrameImageMatchesTheAuditTable() {
        int pixels = 1280 * 720;
        assertEquals(3_686_400, pixels * TargetFormatClass.RGBA8_UNORM.bytesPerPixel());
        assertEquals(7_372_800, pixels * TargetFormatClass.RGBA16_FLOAT.bytesPerPixel());
        assertEquals(3_686_400, pixels * TargetFormatClass.R11G11B10_FLOAT.bytesPerPixel(),
                "the 4-byte HDR class costs no extra memory");
        assertEquals(8_294_400, 1920 * 1080 * TargetFormatClass.RGBA8_UNORM.bytesPerPixel());
        assertEquals(16_588_800, 1920 * 1080 * TargetFormatClass.RGBA16_FLOAT.bytesPerPixel());
    }
}
