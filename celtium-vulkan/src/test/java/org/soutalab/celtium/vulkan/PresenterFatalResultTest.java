package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.vulkan.VK10.VK_SUCCESS;
import static org.lwjgl.vulkan.VK10.VK_ERROR_DEVICE_LOST;
import static org.lwjgl.vulkan.VK10.VK_ERROR_OUT_OF_DEVICE_MEMORY;
import static org.lwjgl.vulkan.KHRSwapchain.VK_SUBOPTIMAL_KHR;
import static org.lwjgl.vulkan.KHRSwapchain.VK_ERROR_OUT_OF_DATE_KHR;

class PresenterFatalResultTest {
    @Test void lostDeviceOrMemoryMustAbortRatherThanRecreateSwapchain() {
        assertTrue(VulkanContext.presenterResultIsFatal(VK_ERROR_DEVICE_LOST));
        assertTrue(VulkanContext.presenterResultIsFatal(VK_ERROR_OUT_OF_DEVICE_MEMORY));
        assertTrue(VulkanContext.presenterResultIsFatal(1), "An unexpected non-error status is not safe to ignore");
    }

    @Test void aLostDeviceFenceMustNotReachResetOrRecycling() {
        assertTrue(VulkanContext.canReclaimDeferred(VK_SUCCESS));
        assertFalse(VulkanContext.canReclaimDeferred(VK_ERROR_DEVICE_LOST),
                "Native vkResetFences on a lost NVIDIA device crashed in hs_err_pid24484.log");
        assertFalse(VulkanContext.canReclaimDeferred(VK_ERROR_OUT_OF_DEVICE_MEMORY));
        assertFalse(VulkanContext.canReclaimDeferred(VK_ERROR_OUT_OF_DATE_KHR));
    }

    @Test void windowsDefaultsToSingleThreadedPresentUntilDriverIsValidated() {
        assertFalse(VulkanContext.asyncPresentDefaultForOs("Windows 11"));
        assertFalse(VulkanContext.asyncPresentDefaultForOs("Windows 10"));
        assertTrue(VulkanContext.asyncPresentDefaultForOs("Linux"));
        assertTrue(VulkanContext.asyncPresentDefaultForOs("Mac OS X"));
    }

    @Test void recoverableSwapchainResultsDoNotAbortDevice() {
        assertFalse(VulkanContext.presenterResultIsFatal(VK_SUCCESS));
        assertFalse(VulkanContext.presenterResultIsFatal(VK_SUBOPTIMAL_KHR));
        assertFalse(VulkanContext.presenterResultIsFatal(VK_ERROR_OUT_OF_DATE_KHR));
    }
}
