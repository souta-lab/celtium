package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;
import org.soutalab.celtium.vulkan.RenderTargetRegistry.TargetFormatClass;
import org.soutalab.celtium.vulkan.hal.PresentEngine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The pure rules of Stage 1's BGRA ignition (A→B overhaul; designer audit 2026-11-25 section 1 Stage 1):
 * the frame colour format follows the swapchain's format, so a generation in the format most desktop
 * surfaces actually offer (B8G8R8A8_UNORM) can be borrowed for direct present instead of composed. The
 * device halves (the surface offer itself, the usable() feature check) are measured in the legs; what is
 * pinned here is every decision that does not need a device, because a wrong decision here is silent -
 * the frame would stay on the composed present forever and only the fallback counters would say so.
 */
class FrameColorFormatDecisionTest {

    private static final int BGRA = SwapchainCreateArguments.FORMAT_B8G8R8A8_UNORM;
    private static final int BGRA_SRGB = SwapchainCreateArguments.FORMAT_B8G8R8A8_SRGB;
    private static final int RGBA = SwapchainCreateArguments.FORMAT_R8G8B8A8_UNORM;
    private static final int UNDEFINED = SwapchainCreateArguments.FORMAT_UNDEFINED;

    // The ignition case itself: the [50, 44] offer measured on both leg machines (UHD 630, HD 620) -
    // the chooser takes 44, and the frame follows it when the device can render into BGRA.
    @Test
    void aBgraSurfaceIsFollowedWhenTheDeviceCanRenderIntoIt() {
        assertEquals(BGRA, SwapchainCreateArguments.frameColorFormat(BGRA, true, true));
    }

    // Direct present off: nothing borrows, so the frame keeps the classic format every existing pass
    // and readback was built against - the off state (the default since the opt-in landing) must
    // reproduce the pre-Stage-1 shape exactly.
    @Test
    void directPresentOffKeepsTheClassicFrame() {
        assertEquals(RGBA, SwapchainCreateArguments.frameColorFormat(BGRA, false, true));
        assertEquals(RGBA, SwapchainCreateArguments.frameColorFormat(RGBA, false, true));
    }

    // A BGRA class the device cannot attach, blend and sample is not followed: a frame the device
    // cannot render into would be worse than the composed present this leaves in place.
    @Test
    void aBgraTheDeviceCannotUseIsNotFollowed() {
        assertEquals(RGBA, SwapchainCreateArguments.frameColorFormat(BGRA, true, false));
    }

    // The sRGB swapchain formats are never adopted: an sRGB attachment applies a linear-to-sRGB
    // conversion on every write, and Minecraft's pipeline is not linear. (The chooser never picks 50
    // either; this pins the decision for a generation that somehow exists in it.)
    @Test
    void anSrgbSurfaceIsNeverFollowed() {
        assertEquals(RGBA, SwapchainCreateArguments.frameColorFormat(BGRA_SRGB, true, true));
    }

    // No generation yet, or an RGBA generation: the classic format - the provisional answer the
    // context returns before a surface has spoken, and the final one on an RGBA surface.
    @Test
    void noGenerationOrAnRgbaGenerationKeepsTheClassicFrame() {
        assertEquals(RGBA, SwapchainCreateArguments.frameColorFormat(UNDEFINED, true, true));
        assertEquals(RGBA, SwapchainCreateArguments.frameColorFormat(RGBA, true, true));
        assertEquals(RGBA, SwapchainCreateArguments.frameColorFormat(RGBA, true, false));
    }

    // The borrow gate itself is format equality against the frame's own format: with the frame
    // following, a BGRA generation borrows and an RGBA one composes - and the reverse on a classic
    // frame. No generation (UNDEFINED) never borrows.
    @Test
    void theBorrowGateIsEqualityWithTheFrameFormat() {
        assertTrue(PresentEngine.formatAllowsBorrow(BGRA, BGRA));
        assertFalse(PresentEngine.formatAllowsBorrow(RGBA, BGRA));
        assertTrue(PresentEngine.formatAllowsBorrow(RGBA, RGBA));
        assertFalse(PresentEngine.formatAllowsBorrow(BGRA, RGBA));
        assertFalse(PresentEngine.formatAllowsBorrow(UNDEFINED, RGBA));
    }

    // The BGRA class itself: 44, 4 bytes a pixel, and never the answer to a GL internal format - it is
    // reachable only through the frame pin, so a mod's format request can never pull a pair into it.
    @Test
    void theBgraClassIsPinOnly() {
        assertEquals(44, TargetFormatClass.BGRA8_UNORM.vkFormat());
        assertEquals(4, TargetFormatClass.BGRA8_UNORM.bytesPerPixel());
        assertFalse(TargetFormatClass.BGRA8_UNORM.isDepth());
        for (int glInternal : new int[] {0x8058, 0x1908, 0x881A, 0x8814, 0x8C3A}) {
            assertFalse(TargetFormatClass.forGlInternalFormat(glInternal) == TargetFormatClass.BGRA8_UNORM,
                    "no GL internal format maps to the frame's BGRA class");
        }
    }
}
