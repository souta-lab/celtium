package org.soutalab.celtium.vulkan.hal;

import org.lwjgl.vulkan.VkPhysicalDevice;
import org.lwjgl.vulkan.VkPhysicalDeviceMemoryProperties;
import org.lwjgl.vulkan.VkPhysicalDeviceProperties;

import static org.lwjgl.vulkan.VK10.VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT;
import static org.lwjgl.vulkan.VK10.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT;
import static org.lwjgl.vulkan.VK10.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT;
import static org.lwjgl.vulkan.VK10.VK_PHYSICAL_DEVICE_TYPE_CPU;
import static org.lwjgl.vulkan.VK10.VK_PHYSICAL_DEVICE_TYPE_DISCRETE_GPU;
import static org.lwjgl.vulkan.VK10.VK_PHYSICAL_DEVICE_TYPE_INTEGRATED_GPU;
import static org.lwjgl.vulkan.VK10.vkGetPhysicalDeviceMemoryProperties;
import static org.lwjgl.vulkan.VK10.vkGetPhysicalDeviceProperties;

/**
 * The memory profile the frame's upload path is built against (design V040-FINAL-PLAN section 2.4):
 * <ul>
 *   <li>{@link #UMA_ZERO_COPY} - integrated GPUs (UHD 630 / HD 620) and CPU devices: one memory,
 *       host-visible and device-local at once, so the per-frame ring is written directly with no
 *       staging copy at all.</li>
 *   <li>{@link #DGPU_REBAR} - a discrete GPU whose BAR exposes device-local host-visible memory:
 *       the ring is written directly into VRAM through the BAR.</li>
 *   <li>{@link #DGPU_STAGED} - a discrete GPU without that exposure: uploads go through the one
 *       staging copy into device-local memory.</li>
 * </ul>
 * The selection is a pure function of the device type and whether a
 * DEVICE_LOCAL | HOST_VISIBLE | HOST_COHERENT memory type exists, so it is unit-testable without a
 * GPU; {@link #fromDevice} is the thin reader that feeds it the device's own answers.
 */
public enum VulkanMemoryProfile {
    UMA_ZERO_COPY,
    DGPU_REBAR,
    DGPU_STAGED;

    public static VulkanMemoryProfile select(int deviceType, boolean deviceLocalHostVisibleCoherent) {
        if (deviceType == VK_PHYSICAL_DEVICE_TYPE_INTEGRATED_GPU
                || deviceType == VK_PHYSICAL_DEVICE_TYPE_CPU) {
            return UMA_ZERO_COPY;
        }
        if (deviceType == VK_PHYSICAL_DEVICE_TYPE_DISCRETE_GPU) {
            return deviceLocalHostVisibleCoherent ? DGPU_REBAR : DGPU_STAGED;
        }
        // Virtual GPUs and anything unrecognised take the path that is correct everywhere: direct
        // writes only when the memory type that makes them safe was actually reported.
        return deviceLocalHostVisibleCoherent ? DGPU_REBAR : DGPU_STAGED;
    }

    public static VulkanMemoryProfile fromDevice(VkPhysicalDevice device) {
        VkPhysicalDeviceProperties properties = VkPhysicalDeviceProperties.calloc();
        VkPhysicalDeviceMemoryProperties memory = VkPhysicalDeviceMemoryProperties.calloc();
        try {
            vkGetPhysicalDeviceProperties(device, properties);
            vkGetPhysicalDeviceMemoryProperties(device, memory);
            int wanted = VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT
                    | VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT
                    | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT;
            boolean combo = false;
            for (int i = 0; i < memory.memoryTypeCount(); i++) {
                if ((memory.memoryTypes(i).propertyFlags() & wanted) == wanted) {
                    combo = true;
                    break;
                }
            }
            return select(properties.deviceType(), combo);
        } finally {
            properties.free();
            memory.free();
        }
    }

    /** Whether per-frame uploads can be written straight into device memory (no staging copy). */
    public boolean zeroCopyUploads() {
        return this != DGPU_STAGED;
    }
}
