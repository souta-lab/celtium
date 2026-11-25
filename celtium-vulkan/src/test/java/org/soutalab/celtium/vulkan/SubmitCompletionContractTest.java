package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.vulkan.VK10.VK_SUCCESS;
import static org.lwjgl.vulkan.VK10.VK_ERROR_DEVICE_LOST;
import static org.lwjgl.vulkan.VK10.VK_ERROR_OUT_OF_DEVICE_MEMORY;
import static org.lwjgl.vulkan.KHRSwapchain.VK_ERROR_OUT_OF_DATE_KHR;

class SubmitCompletionContractTest {
    @Test void aLostDeviceFenceMustNotReachResetOrRecycling() {
        assertTrue(VulkanContext.canReclaimDeferred(VK_SUCCESS));
        assertFalse(VulkanContext.canReclaimDeferred(VK_ERROR_DEVICE_LOST),
                "Native vkResetFences on a lost NVIDIA device crashed in hs_err_pid24484.log");
        assertFalse(VulkanContext.canReclaimDeferred(VK_ERROR_OUT_OF_DEVICE_MEMORY));
        assertFalse(VulkanContext.canReclaimDeferred(VK_ERROR_OUT_OF_DATE_KHR));
    }

    @Test void descriptorAllocationMustPropagateDeviceLoss() {
        assertTrue(VulkanImmediateTexturePipeline.descriptorResultIsDeviceLost(VK_ERROR_DEVICE_LOST));
        assertFalse(VulkanImmediateTexturePipeline.descriptorResultIsDeviceLost(VK_SUCCESS));
        assertFalse(VulkanImmediateTexturePipeline.descriptorResultIsDeviceLost(VK_ERROR_OUT_OF_DEVICE_MEMORY));
    }

    /**
     * The planner writes into a caller's array now and answers the count, so that the submit path makes no allocation
     * per chunk. This mirrors what {@code submitChunk} does with its own scratch, rather than asking for the old
     * returning-array form, which no longer exists and had left this file uncompilable.
     */
    private static int[] plan(boolean[] barriers) {
        int[] sizes = new int[barriers.length];
        int written = VulkanContext.planSubmitGroupsInto(barriers, barriers.length, sizes);
        return java.util.Arrays.copyOf(sizes, written);
    }

    @Test void semaphoreBoundariesPreserveOrderWhilePlainBuffersCoalesce() {
        assertArrayEquals(new int[]{128}, plan(new boolean[128]));
        assertArrayEquals(new int[]{1, 1, 1}, plan(new boolean[]{true, true, true}));
        assertArrayEquals(new int[]{2, 1, 3, 1, 1},
                plan(new boolean[]{false, false, true, false, false, false, true, false}));
        assertArrayEquals(new int[0], plan(new boolean[0]));
    }
}
