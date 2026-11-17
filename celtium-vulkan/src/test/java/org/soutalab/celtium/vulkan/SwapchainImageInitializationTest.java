package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A swapchain image's content is undefined until something writes it, so creation gives every image a clear
 * (issue #422) - but the clear is a transfer, and a surface that does not offer TRANSFER_DST cannot take one.
 * The usage the fixture's surface produced (0x13: TRANSFER_SRC | TRANSFER_DST | COLOR_ATTACHMENT, from the
 * CELTIUM_SWAPCHAIN line) is the measured case the decision has to accept.
 */
class SwapchainImageInitializationTest {
    @Test
    void theFixtureUsageTakesTheClear() {
        assertTrue(VulkanSwapchain.imageInitializationWanted(0x13));
    }

    @Test
    void transferDstAloneTakesTheClear() {
        assertTrue(VulkanSwapchain.imageInitializationWanted(0x2));
    }

    @Test
    void noTransferDstNoClear() {
        assertFalse(VulkanSwapchain.imageInitializationWanted(0x10));
        assertFalse(VulkanSwapchain.imageInitializationWanted(0x11));
        assertFalse(VulkanSwapchain.imageInitializationWanted(0));
    }
}
