package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;
import org.lwjgl.vulkan.VK10;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the issue #422 (a) transfer-only swapchain present contract:
 * <ul>
 *   <li>By default {@link SwapchainCreateArguments#select} drops {@code COLOR_ATTACHMENT_BIT} so RDNA3
 *       proprietary drivers do not allocate displayable DCC compression on swapchain images written solely by
 *       {@code vkCmdBlitImage} / {@code vkCmdClearColorImage}.</li>
 *   <li>{@link VulkanSwapchain#imageViewsWanted(int)} skips {@code vkCreateImageView} on transfer-only swapchains
 *       to satisfy {@code VUID-VkImageViewCreateInfo-image-04441}.</li>
 *   <li>{@link VulkanSwapchain#transferDstEntrySrcStage()} chains the {@code UNDEFINED -> TRANSFER_DST} transition
 *       at {@code TRANSFER_BIT} (covered by the acquire semaphore wait stage) rather than {@code TOP_OF_PIPE_BIT}.</li>
 * </ul>
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
    void defaultSelectDropsColourAttachmentAndKeepsTransferBits() {
        SwapchainCreateArguments.Result result =
                SwapchainCreateArguments.select(surfaceWithUsage(ALL_USAGE), 1024, 640, 3);
        assertNull(result.refusal);
        assertEquals(SwapchainCreateArguments.USAGE_TRANSFER_DST | SwapchainCreateArguments.USAGE_TRANSFER_SRC,
                result.usage);
        assertTrue(result.describe().contains("transfer-only"),
                "the reconciliation must name transfer-only: " + result.describe());
    }

    @Test
    void defaultSelectAcceptsTransferDstOnlySurface() {
        SwapchainCreateArguments.Result result = SwapchainCreateArguments.select(
                surfaceWithUsage(SwapchainCreateArguments.USAGE_TRANSFER_DST), 1024, 640, 3);
        assertNull(result.refusal);
        assertEquals(SwapchainCreateArguments.USAGE_TRANSFER_DST, result.usage);
        assertTrue(result.droppedUsage.contains("transfer-only"));
        assertTrue(result.droppedUsage.contains("TRANSFER_SRC"));
    }

    @Test
    void defaultSelectRefusesSurfaceWithoutTransferDst() {
        int noTransferDst = SwapchainCreateArguments.USAGE_COLOR_ATTACHMENT
                | SwapchainCreateArguments.USAGE_TRANSFER_SRC;
        SwapchainCreateArguments.Result result =
                SwapchainCreateArguments.select(surfaceWithUsage(noTransferDst), 1024, 640, 3);
        assertNotNull(result.refusal);
        assertTrue(result.refusal.contains("transfer-destination"), result.refusal);
    }

    @Test
    void optOutRestoresColourAttachmentUsageAndRequirement() {
        SwapchainCreateArguments.Result withColour =
                SwapchainCreateArguments.select(surfaceWithUsage(ALL_USAGE), 1024, 640, 3, false);
        assertNull(withColour.refusal);
        assertEquals(ALL_USAGE, withColour.usage);

        int noColour = SwapchainCreateArguments.USAGE_TRANSFER_DST | SwapchainCreateArguments.USAGE_TRANSFER_SRC;
        SwapchainCreateArguments.Result withoutColour =
                SwapchainCreateArguments.select(surfaceWithUsage(noColour), 1024, 640, 3, false);
        assertNotNull(withoutColour.refusal);
    }

    @Test
    void imageViewsAreSkippedForTransferOnlySwapchains() {
        assertFalse(VulkanSwapchain.imageViewsWanted(SwapchainCreateArguments.USAGE_TRANSFER_DST));
        assertFalse(VulkanSwapchain.imageViewsWanted(
                SwapchainCreateArguments.USAGE_TRANSFER_DST | SwapchainCreateArguments.USAGE_TRANSFER_SRC));
        assertTrue(VulkanSwapchain.imageViewsWanted(ALL_USAGE));
        assertTrue(VulkanSwapchain.imageViewsWanted(SwapchainCreateArguments.USAGE_COLOR_ATTACHMENT));
    }

    @Test
    void transferDstEntryChainsAfterAcquireAtTransferStage() {
        assertEquals(VK10.VK_PIPELINE_STAGE_TRANSFER_BIT, VulkanSwapchain.transferDstEntrySrcStage());
    }
}
