/*
 * Built-in shader pack engine for Celtium (LGPL-3.0).
 */
package org.soutalab.celtium.vulkan.pack;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkBufferCreateInfo;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkMemoryAllocateInfo;
import org.lwjgl.vulkan.VkMemoryRequirements;
import org.lwjgl.vulkan.VkPhysicalDevice;
import org.lwjgl.vulkan.VkPhysicalDeviceMemoryProperties;

import java.lang.foreign.MemorySegment;
import java.nio.LongBuffer;

import static org.lwjgl.vulkan.VK13.VK_BUFFER_USAGE_STORAGE_BUFFER_BIT;
import static org.lwjgl.vulkan.VK13.VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT;
import static org.lwjgl.vulkan.VK13.VK_BUFFER_USAGE_VERTEX_BUFFER_BIT;
import static org.lwjgl.vulkan.VK13.VK_BUFFER_USAGE_INDIRECT_BUFFER_BIT;
import static org.lwjgl.vulkan.VK13.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT;
import static org.lwjgl.vulkan.VK13.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT;
import static org.lwjgl.vulkan.VK13.VK_SHARING_MODE_EXCLUSIVE;
import static org.lwjgl.vulkan.VK13.VK_SUCCESS;
import static org.lwjgl.vulkan.VK13.vkAllocateMemory;
import static org.lwjgl.vulkan.VK13.vkBindBufferMemory;
import static org.lwjgl.vulkan.VK13.vkCreateBuffer;
import static org.lwjgl.vulkan.VK13.vkDestroyBuffer;
import static org.lwjgl.vulkan.VK13.vkFreeMemory;
import static org.lwjgl.vulkan.VK13.vkGetBufferMemoryRequirements;
import static org.lwjgl.vulkan.VK13.vkGetPhysicalDeviceMemoryProperties;
import static org.lwjgl.vulkan.VK13.vkMapMemory;
import static org.lwjgl.vulkan.VK13.vkUnmapMemory;

/**
 * One host-visible GPU buffer owned by the pack executor for its whole lifetime: created once
 * at pack activation, mapped persistently, freed at close. This is the activation-time
 * allocation class (the same class as the attachment slab's slab), never a per-frame or
 * per-draw allocation - the frame path only writes through the mapped segment.
 */
final class PackGpuBuffer implements AutoCloseable {

    static final int USAGE_UNIFORM = VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT;
    static final int USAGE_STORAGE = VK_BUFFER_USAGE_STORAGE_BUFFER_BIT;
    static final int USAGE_VERTEX = VK_BUFFER_USAGE_VERTEX_BUFFER_BIT;
    static final int USAGE_INDIRECT = VK_BUFFER_USAGE_INDIRECT_BUFFER_BIT;
    static final int USAGE_TRANSFER_SRC = 0x00000001;

    private final VkDevice device;
    private final long buffer;
    private final long memory;
    private final long sizeBytes;
    private final MemorySegment mapped;

    PackGpuBuffer(VkPhysicalDevice physicalDevice, VkDevice device, long sizeBytes, int usage) {
        this.device = device;
        this.sizeBytes = sizeBytes;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkBufferCreateInfo bufferInfo = VkBufferCreateInfo.calloc(stack)
                    .sType(org.lwjgl.vulkan.VK13.VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO)
                    .size(sizeBytes)
                    .usage(usage)
                    .sharingMode(VK_SHARING_MODE_EXCLUSIVE);
            LongBuffer handle = stack.mallocLong(1);
            int result = vkCreateBuffer(device, bufferInfo, null, handle);
            if (result != VK_SUCCESS) {
                throw new IllegalStateException("vkCreateBuffer failed: " + result);
            }
            this.buffer = handle.get(0);

            VkMemoryRequirements requirements = VkMemoryRequirements.calloc(stack);
            vkGetBufferMemoryRequirements(device, buffer, requirements);
            int memoryType = findMemoryType(physicalDevice, requirements.memoryTypeBits(),
                    VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
            VkMemoryAllocateInfo allocInfo = VkMemoryAllocateInfo.calloc(stack)
                    .sType(org.lwjgl.vulkan.VK13.VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
                    .allocationSize(requirements.size())
                    .memoryTypeIndex(memoryType);
            result = vkAllocateMemory(device, allocInfo, null, handle);
            if (result != VK_SUCCESS) {
                vkDestroyBuffer(device, buffer, null);
                throw new IllegalStateException("vkAllocateMemory failed: " + result);
            }
            this.memory = handle.get(0);
            vkBindBufferMemory(device, buffer, memory, 0L);

            java.nio.ByteBuffer mappedBuffer = vkMapMemory(device, memory, 0L, sizeBytes, 0L);
            if (mappedBuffer == null) {
                throw new IllegalStateException("vkMapMemory returned null");
            }
            this.mapped = MemorySegment.ofBuffer(mappedBuffer);
        }
    }

    private static int findMemoryType(VkPhysicalDevice physicalDevice, int typeBits, int properties) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkPhysicalDeviceMemoryProperties memoryProperties =
                    VkPhysicalDeviceMemoryProperties.calloc(stack);
            vkGetPhysicalDeviceMemoryProperties(physicalDevice, memoryProperties);
            for (int i = 0; i < memoryProperties.memoryTypeCount(); i++) {
                if ((typeBits & (1 << i)) != 0
                        && (memoryProperties.memoryTypes(i).propertyFlags() & properties) == properties) {
                    return i;
                }
            }
        }
        throw new IllegalStateException("no host-visible memory type for pack buffer");
    }

    long bufferHandle() {
        return buffer;
    }

    long sizeBytes() {
        return sizeBytes;
    }

    /** The persistently mapped host view of the whole buffer. */
    MemorySegment mapped() {
        return mapped;
    }

    /** A byte-range view of the mapped segment. */
    MemorySegment slice(long offset, long size) {
        return mapped.asSlice(offset, size);
    }

    @Override
    public void close() {
        vkUnmapMemory(device, memory);
        vkDestroyBuffer(device, buffer, null);
        vkFreeMemory(device, memory, null);
    }
}
