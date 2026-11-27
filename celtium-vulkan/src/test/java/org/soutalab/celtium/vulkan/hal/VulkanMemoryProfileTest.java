package org.soutalab.celtium.vulkan.hal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.vulkan.VK10.VK_PHYSICAL_DEVICE_TYPE_CPU;
import static org.lwjgl.vulkan.VK10.VK_PHYSICAL_DEVICE_TYPE_DISCRETE_GPU;
import static org.lwjgl.vulkan.VK10.VK_PHYSICAL_DEVICE_TYPE_INTEGRATED_GPU;
import static org.lwjgl.vulkan.VK10.VK_PHYSICAL_DEVICE_TYPE_OTHER;
import static org.lwjgl.vulkan.VK10.VK_PHYSICAL_DEVICE_TYPE_VIRTUAL_GPU;

class VulkanMemoryProfileTest {

    @Test
    void integratedAndCpuDevicesAreZeroCopyUma() {
        assertEquals(VulkanMemoryProfile.UMA_ZERO_COPY,
                VulkanMemoryProfile.select(VK_PHYSICAL_DEVICE_TYPE_INTEGRATED_GPU, false));
        assertEquals(VulkanMemoryProfile.UMA_ZERO_COPY,
                VulkanMemoryProfile.select(VK_PHYSICAL_DEVICE_TYPE_INTEGRATED_GPU, true));
        assertEquals(VulkanMemoryProfile.UMA_ZERO_COPY,
                VulkanMemoryProfile.select(VK_PHYSICAL_DEVICE_TYPE_CPU, false));
    }

    @Test
    void discreteGpuSplitsOnTheRebarMemoryType() {
        assertEquals(VulkanMemoryProfile.DGPU_REBAR,
                VulkanMemoryProfile.select(VK_PHYSICAL_DEVICE_TYPE_DISCRETE_GPU, true));
        assertEquals(VulkanMemoryProfile.DGPU_STAGED,
                VulkanMemoryProfile.select(VK_PHYSICAL_DEVICE_TYPE_DISCRETE_GPU, false));
    }

    @Test
    void unrecognisedDevicesTakeTheSafeSideOfTheirMemoryReport() {
        assertEquals(VulkanMemoryProfile.DGPU_REBAR,
                VulkanMemoryProfile.select(VK_PHYSICAL_DEVICE_TYPE_VIRTUAL_GPU, true));
        assertEquals(VulkanMemoryProfile.DGPU_STAGED,
                VulkanMemoryProfile.select(VK_PHYSICAL_DEVICE_TYPE_OTHER, false));
    }

    @Test
    void onlyTheStagedProfilePaysAStagingCopy() {
        assertTrue(VulkanMemoryProfile.UMA_ZERO_COPY.zeroCopyUploads());
        assertTrue(VulkanMemoryProfile.DGPU_REBAR.zeroCopyUploads());
        assertFalse(VulkanMemoryProfile.DGPU_STAGED.zeroCopyUploads());
    }
}
