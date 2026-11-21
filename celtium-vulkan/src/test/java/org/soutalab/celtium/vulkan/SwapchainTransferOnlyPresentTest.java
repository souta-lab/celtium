package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The issue #422 (a) A/B variant of {@link SwapchainCreateArguments#select}: with
 * {@code transferOnlyPresent} the usage request drops {@code COLOR_ATTACHMENT} so the swapchain's images
 * are allocated as transfer surfaces rather than renderable ones - the allocation regime is the variable
 * that arm of the experiment moves, while the present (a blit) is unchanged. The default arm must keep
 * asking for exactly what it asked for before the variant existed.
 */
class SwapchainTransferOnlyPresentTest {
    private static final int ALL_USAGE = SwapchainCreateArguments.USAGE_COLOR_ATTACHMENT
            | SwapchainCreateArguments.USAGE_TRANSFER_DST | SwapchainCreateArguments.USAGE_TRANSFER_SRC;

    private static SwapchainCreateArguments.Surface surfaceWithUsage(int usage) {
        SwapchainCreateArguments.Surface surface = new SwapchainCreateArguments.Surface();
        surface.minImageCount = 2;
        surface.maxImageCount = 0;
        surface.supportedUsage = usage;
        surface.supportedCompositeAlpha = SwapchainCreateArguments.ALPHA_OPAQUE;
        surface.formats = new int[] {SwapchainCreateArguments.FORMAT_B8G8R8A8_UNORM, 0};
        surface.currentExtentWidth = 1024;
        surface.currentExtentHeight = 640;
        return surface;
    }

    @Test
    void theDefaultArmStillRequiresColourAttachment() {
        SwapchainCreateArguments.Result result =
                SwapchainCreateArguments.select(surfaceWithUsage(ALL_USAGE), 1024, 640, 3);
        assertNull(result.refusal);
        assertEquals(ALL_USAGE, result.usage);
    }

    @Test
    void theDefaultArmRefusesASurfaceWithoutColourAttachment() {
        int noColour = SwapchainCreateArguments.USAGE_TRANSFER_DST
                | SwapchainCreateArguments.USAGE_TRANSFER_SRC;
        SwapchainCreateArguments.Result result =
                SwapchainCreateArguments.select(surfaceWithUsage(noColour), 1024, 640, 3);
        assertNotNull(result.refusal);
    }

    @Test
    void theVariantDropsColourAttachmentAndKeepsTheTransferBits() {
        SwapchainCreateArguments.Result result =
                SwapchainCreateArguments.select(surfaceWithUsage(ALL_USAGE), 1024, 640, 3, true);
        assertNull(result.refusal);
        assertEquals(SwapchainCreateArguments.USAGE_TRANSFER_DST
                | SwapchainCreateArguments.USAGE_TRANSFER_SRC, result.usage);
        assertTrue(result.describe().contains("transfer-only"),
                "the reconciliation must name the variant: " + result.describe());
    }

    @Test
    void theVariantAcceptsASurfaceWithoutColourAttachment() {
        // That acceptance is the point of the variant: a surface the default arm refuses outright can still
        // carry a transfer-only swapchain.
        int noColour = SwapchainCreateArguments.USAGE_TRANSFER_DST
                | SwapchainCreateArguments.USAGE_TRANSFER_SRC;
        SwapchainCreateArguments.Result result =
                SwapchainCreateArguments.select(surfaceWithUsage(noColour), 1024, 640, 3, true);
        assertNull(result.refusal);
        assertEquals(noColour, result.usage);
    }

    @Test
    void theVariantRefusesASurfaceWithoutTransferDst() {
        // The blit writes into the image; without transfer-dst there is no variant, and the refusal names it.
        int noDst = SwapchainCreateArguments.USAGE_COLOR_ATTACHMENT
                | SwapchainCreateArguments.USAGE_TRANSFER_SRC;
        SwapchainCreateArguments.Result result =
                SwapchainCreateArguments.select(surfaceWithUsage(noDst), 1024, 640, 3, true);
        assertNotNull(result.refusal);
        assertTrue(result.refusal.contains("transfer-dst"), result.refusal);
    }

    @Test
    void theVariantKeepsOnlyTheTransferBitsTheSurfaceOffers() {
        int dstOnly = SwapchainCreateArguments.USAGE_COLOR_ATTACHMENT
                | SwapchainCreateArguments.USAGE_TRANSFER_DST;
        SwapchainCreateArguments.Result result =
                SwapchainCreateArguments.select(surfaceWithUsage(dstOnly), 1024, 640, 3, true);
        assertNull(result.refusal);
        assertEquals(SwapchainCreateArguments.USAGE_TRANSFER_DST, result.usage);
        assertTrue(result.droppedUsage.contains("TRANSFER_SRC"), result.droppedUsage);
    }
}
