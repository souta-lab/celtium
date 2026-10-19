package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.lwjgl.vulkan.VK10.VK_ERROR_DEVICE_LOST;
import static org.lwjgl.vulkan.VK10.VK_SUCCESS;
import static org.lwjgl.vulkan.VK10.VK_TIMEOUT;

/**
 * What a swapchain cleanup may do with its command pool, semaphores and views depends on whether the queue drain
 * confirmed idle (audit #368). A failed wait is not a lost device and neither is permission to free.
 */
class SwapchainCleanupDecisionTest {
    @Test
    void aConfirmedDrainDestroys() {
        assertEquals("destroy", VulkanSwapchain.cleanupDecision(VK_SUCCESS));
    }

    @Test
    void aLostDeviceIsItsOwnCaseAndStillCleansUp() {
        // The objects went with the device, and no wait can confirm anything, so holding them forever helps nobody.
        assertEquals("destroy-device-lost", VulkanSwapchain.cleanupDecision(VK_ERROR_DEVICE_LOST));
    }

    @Test
    void everyOtherResultPreservesTheHandles() {
        assertEquals("preserve", VulkanSwapchain.cleanupDecision(VK_TIMEOUT));
        assertEquals("preserve", VulkanSwapchain.cleanupDecision(-1));
        assertEquals("preserve", VulkanSwapchain.cleanupDecision(-2));
        // A drain that fails must never read as permission to destroy: that is the use-after-free the audit describes.
        for (int result = -64; result <= 1; result++) {
            String decision = VulkanSwapchain.cleanupDecision(result);
            if (result == VK_SUCCESS || result == VK_ERROR_DEVICE_LOST) {
                continue;
            }
            assertEquals("preserve", decision, "result=" + result);
        }
    }
}
