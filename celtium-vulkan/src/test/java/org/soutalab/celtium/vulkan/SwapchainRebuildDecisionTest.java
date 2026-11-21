package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The backend's ensureSwapchain consumes the swapchain's own verdicts through {@link VulkanSwapchain#rebuildDecision}
 * (bughunt LANE-05 #1/#5): the out-of-date flag alone had no production reader, so rebuilds the present path asked
 * for never happened, and a lost surface was classified nowhere. The decision is pinned here because the branches it
 * separates are the recovery itself - reusing a lost surface rebuilds on a dead handle forever.
 */
class SwapchainRebuildDecisionTest {
    @Test
    void noVerdictKeepsTheSwapchain() {
        assertEquals("keep", VulkanSwapchain.rebuildDecision(false, false));
    }

    @Test
    void aRebuildRequestRebuilds() {
        assertEquals("rebuild", VulkanSwapchain.rebuildDecision(false, true));
    }

    @Test
    void aLostSurfaceRecreatesTheSurface() {
        assertEquals("recreate-surface", VulkanSwapchain.rebuildDecision(true, false));
    }

    @Test
    void aLostSurfaceWinsOverAPlainRebuild() {
        // Both set (a present that reported the surface lost after an out-of-date acquire): dropping through the
        // plain rebuild would detach and re-adopt the dead surface, which is the failure being avoided.
        assertEquals("recreate-surface", VulkanSwapchain.rebuildDecision(true, true));
    }

    /** A surface a real desktop WSI would report: pinned extent, colour attachment, one usable format. */
    private static SwapchainCreateArguments.Surface usableSurface(long extentWidth, long extentHeight) {
        SwapchainCreateArguments.Surface surface = new SwapchainCreateArguments.Surface();
        surface.minImageCount = 2;
        surface.maxImageCount = 0;
        surface.supportedUsage = SwapchainCreateArguments.USAGE_COLOR_ATTACHMENT
                | SwapchainCreateArguments.USAGE_TRANSFER_DST | SwapchainCreateArguments.USAGE_TRANSFER_SRC;
        surface.supportedCompositeAlpha = SwapchainCreateArguments.ALPHA_OPAQUE;
        surface.formats = new int[] {SwapchainCreateArguments.FORMAT_B8G8R8A8_UNORM, 0};
        surface.currentExtentWidth = extentWidth;
        surface.currentExtentHeight = extentHeight;
        return surface;
    }

    @Test
    void aPinnedZeroExtentIsRefusedByName() {
        // A minimised window can pin currentExtent to 0x0; fed unprocessed into vkCreateSwapchainKHR it failed
        // creation every frame with nothing naming why (bughunt LANE-05 #8).
        SwapchainCreateArguments.Result result =
                SwapchainCreateArguments.select(usableSurface(0, 0), 1024, 640, 3);
        assertNotNull(result.refusal);
    }

    @Test
    void aPinnedZeroHeightIsRefusedByName() {
        SwapchainCreateArguments.Result result =
                SwapchainCreateArguments.select(usableSurface(1024, 0), 1024, 640, 3);
        assertNotNull(result.refusal);
    }

    @Test
    void aPinnedUsableExtentIsNotRefused() {
        SwapchainCreateArguments.Result result =
                SwapchainCreateArguments.select(usableSurface(1024, 640), 1024, 640, 3);
        assertNull(result.refusal);
        assertEquals(1024, result.width);
        assertEquals(640, result.height);
    }

    @Test
    void anUnspecifiedExtentClampsTheRequestAndIsNotRefused() {
        SwapchainCreateArguments.Result result = SwapchainCreateArguments.select(
                usableSurface(SwapchainCreateArguments.EXTENT_UNSPECIFIED,
                        SwapchainCreateArguments.EXTENT_UNSPECIFIED),
                1024, 640, 3);
        assertNull(result.refusal);
        assertEquals(1024, result.width);
        assertEquals(640, result.height);
    }
}
