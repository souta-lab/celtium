package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the wait/refusal classification behind the shipped v0.3.0 swapchain crash.
 * <p>
 * A surface that pins {@code currentExtent} at 0x0 (a minimised window on Windows) is refused by name in
 * {@link SwapchainCreateArguments#select} so the caller can skip the frame and retry - but the refusal
 * was fed to {@link SwapchainCreationEscalation} like any other, and a healthy session whose surface
 * pinned 0x0 while a world loaded reached the 120-refusal limit in about two seconds of loading-screen
 * presents and died. The fix is the classification pinned here: the pinned-extent refusal, and only that
 * refusal, carries {@code awaitingSurfaceSize}, which the backend routes around the escalation. The
 * structural refusals (a surface that cannot carry a frame at any size) must stay unflagged, or the
 * escalation loses the permanently refusing surface it exists to catch. The wiring (the outcome carried
 * through {@code VulkanSwapchain.create}, the backend's branch around {@code recordRefusal}) is not
 * reachable from a unit test, as the escalation's own test notes of its wiring.
 */
class SwapchainAwaitingSurfaceSizeTest {
    private static final int ALL_USAGE = SwapchainCreateArguments.USAGE_COLOR_ATTACHMENT
            | SwapchainCreateArguments.USAGE_TRANSFER_DST | SwapchainCreateArguments.USAGE_TRANSFER_SRC;

    /** A surface that would create fine at the size it pins, so only the pinned extent can refuse it. */
    private static SwapchainCreateArguments.Surface usableSurface() {
        SwapchainCreateArguments.Surface surface = new SwapchainCreateArguments.Surface();
        surface.minImageCount = 2;
        surface.maxImageCount = 0;
        surface.supportedUsage = ALL_USAGE;
        surface.supportedCompositeAlpha = SwapchainCreateArguments.ALPHA_OPAQUE;
        surface.formats = new int[] {SwapchainCreateArguments.FORMAT_B8G8R8A8_UNORM, 0};
        surface.currentExtentWidth = 1920;
        surface.currentExtentHeight = 1080;
        return surface;
    }

    @Test
    void aSurfacePinningZeroExtentIsRefusedAsAWait() {
        SwapchainCreateArguments.Surface surface = usableSurface();
        surface.currentExtentWidth = 0;
        surface.currentExtentHeight = 0;
        SwapchainCreateArguments.Result result = SwapchainCreateArguments.select(surface, 1920, 1080, 3);
        assertNotNull(result.refusal);
        assertTrue(result.refusal.contains("pins a current extent of 0x0"), result.refusal);
        assertTrue(result.awaitingSurfaceSize,
                "a pinned 0x0 extent is a wait: the escalation must never count it");
    }

    @Test
    void aSurfacePinningZeroHeightAloneIsAlsoAWait() {
        SwapchainCreateArguments.Surface surface = usableSurface();
        surface.currentExtentHeight = 0;
        SwapchainCreateArguments.Result result = SwapchainCreateArguments.select(surface, 1920, 1080, 3);
        assertNotNull(result.refusal);
        assertTrue(result.awaitingSurfaceSize);
    }

    @Test
    void aSizedSurfaceIsNotAWait() {
        SwapchainCreateArguments.Result result =
                SwapchainCreateArguments.select(usableSurface(), 1920, 1080, 3);
        assertNull(result.refusal);
        assertFalse(result.awaitingSurfaceSize);
    }

    @Test
    void anUnspecifiedExtentClampsTheRequestAndIsNotAWait() {
        SwapchainCreateArguments.Surface surface = usableSurface();
        surface.currentExtentWidth = SwapchainCreateArguments.EXTENT_UNSPECIFIED;
        surface.currentExtentHeight = SwapchainCreateArguments.EXTENT_UNSPECIFIED;
        // Even a zero request lands on the surface's minimum through the clamp, never on the wait.
        SwapchainCreateArguments.Result result = SwapchainCreateArguments.select(surface, 0, 0, 3);
        assertNull(result.refusal);
        assertFalse(result.awaitingSurfaceSize);
        assertTrue(result.width >= 1 && result.height >= 1, result.describe());
    }

    @Test
    void structuralRefusalsAreNotWaits() {
        // No transfer-destination usage: the surface cannot carry the present blit at any size.
        SwapchainCreateArguments.Surface noTransfer = usableSurface();
        noTransfer.supportedUsage = SwapchainCreateArguments.USAGE_COLOR_ATTACHMENT
                | SwapchainCreateArguments.USAGE_TRANSFER_SRC;
        SwapchainCreateArguments.Result transferResult =
                SwapchainCreateArguments.select(noTransfer, 1920, 1080, 3);
        assertNotNull(transferResult.refusal);
        assertFalse(transferResult.awaitingSurfaceSize,
                "a structural refusal must still count toward the escalation: " + transferResult.refusal);

        // No composite-alpha mode at all.
        SwapchainCreateArguments.Surface noAlpha = usableSurface();
        noAlpha.supportedCompositeAlpha = 0;
        SwapchainCreateArguments.Result alphaResult = SwapchainCreateArguments.select(noAlpha, 1920, 1080, 3);
        assertNotNull(alphaResult.refusal);
        assertFalse(alphaResult.awaitingSurfaceSize);

        // No formats advertised.
        SwapchainCreateArguments.Surface noFormats = usableSurface();
        noFormats.formats = new int[0];
        SwapchainCreateArguments.Result formatResult =
                SwapchainCreateArguments.select(noFormats, 1920, 1080, 3);
        assertNotNull(formatResult.refusal);
        assertFalse(formatResult.awaitingSurfaceSize);
    }
}
