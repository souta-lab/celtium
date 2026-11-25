package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;
import org.lwjgl.vulkan.VK10;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the direct present path's swapchain usage contract (design section 2.5; lanechat #8830 (3)(4)):
 * <ul>
 *   <li>{@link SwapchainCreateArguments#select} requires {@code COLOR_ATTACHMENT} - the frame renders
 *       straight into the swapchain image - and keeps the transfer bits the surface offers, for the
 *       present readback/dump block and the loading screen's standalone present. This replaced the
 *       transfer-only default of issue #422 (a), which belonged to the blit path deleted in the same
 *       landing: a transfer-only swapchain cannot be rendered into at all.</li>
 *   <li>{@link VulkanSwapchain#imageViewsWanted(int)} creates views exactly when colour-attachment usage
 *       is present - under the direct path that is always, so every generation carries the views the
 *       lender hands borrowers (and {@code VUID-VkImageViewCreateInfo-image-04441} stays satisfied for
 *       any transfer-only usage a future caller might still probe).</li>
 *   <li>{@link VulkanSwapchain#transferDstEntrySrcStage()} still chains the {@code UNDEFINED ->
 *       TRANSFER_DST} transition at {@code TRANSFER_BIT}: the clear-and-present probe and the image
 *       initialization path still enter swapchain images as transfer destinations.</li>
 * </ul>
 */
class SwapchainDirectPresentUsageTest {
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
    void selectKeepsColourAttachmentAndEveryOfferedTransferBit() {
        SwapchainCreateArguments.Result result =
                SwapchainCreateArguments.select(surfaceWithUsage(ALL_USAGE), 1024, 640, 3);
        assertNull(result.refusal);
        assertEquals(ALL_USAGE, result.usage);
        assertEquals("", result.droppedUsage);
        assertFalse(result.describe().contains("transfer-only"),
                "the reconciliation must not claim the deleted blit path's shape: " + result.describe());
    }

    @Test
    void selectRefusesASurfaceWithoutColourAttachment() {
        int noColour = SwapchainCreateArguments.USAGE_TRANSFER_DST
                | SwapchainCreateArguments.USAGE_TRANSFER_SRC;
        SwapchainCreateArguments.Result result =
                SwapchainCreateArguments.select(surfaceWithUsage(noColour), 1024, 640, 3);
        assertNotNull(result.refusal);
        assertTrue(result.refusal.contains("colour-attachment"), result.refusal);
    }

    @Test
    void selectAcceptsColourAttachmentOnlyAndNamesTheDroppedTransferBits() {
        SwapchainCreateArguments.Result result = SwapchainCreateArguments.select(
                surfaceWithUsage(SwapchainCreateArguments.USAGE_COLOR_ATTACHMENT), 1024, 640, 3);
        assertNull(result.refusal);
        assertEquals(SwapchainCreateArguments.USAGE_COLOR_ATTACHMENT, result.usage);
        assertTrue(result.droppedUsage.contains("TRANSFER_DST"), result.droppedUsage);
        assertTrue(result.droppedUsage.contains("TRANSFER_SRC"), result.droppedUsage);
    }

    @Test
    void selectKeepsOnlyTheTransferBitsTheSurfaceOffers() {
        int colourAndDst = SwapchainCreateArguments.USAGE_COLOR_ATTACHMENT
                | SwapchainCreateArguments.USAGE_TRANSFER_DST;
        SwapchainCreateArguments.Result result =
                SwapchainCreateArguments.select(surfaceWithUsage(colourAndDst), 1024, 640, 3);
        assertNull(result.refusal);
        assertEquals(colourAndDst, result.usage);
        assertTrue(result.droppedUsage.contains("TRANSFER_SRC"), result.droppedUsage);
        assertFalse(result.droppedUsage.contains("TRANSFER_DST"), result.droppedUsage);
    }

    @Test
    void imageViewsAreCreatedForColourAttachmentSwapchains() {
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
