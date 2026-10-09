package org.soutalab.celtium.vulkan;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VkBufferCreateInfo;
import org.lwjgl.vulkan.VkMemoryAllocateInfo;
import org.lwjgl.vulkan.VkMemoryRequirements;
import org.lwjgl.vulkan.VkPhysicalDeviceMemoryProperties;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;

import static org.lwjgl.vulkan.VK10.*;

/**
 * A persistently mapped, host-visible uniform buffer holding the per-draw terrain uniform blocks.
 * <p>
 * Terrain draws rewrite a small block for every region and pass, so the data has to be written from the CPU
 * without a staging copy. Host-coherent memory is required rather than preferred: without it every block would
 * need an explicit flush before the draw that reads it. The buffer only grows; the caller addresses blocks by
 * dynamic offset, so existing contents stay valid across a growth.
 */
final class VulkanUniformBuffer {
    private final VulkanContext context;

    private long buffer;
    private long memory;
    private long mappedAddress;
    private long size;

    VulkanUniformBuffer(VulkanContext context, long initialSize) {
        this.context = context;
        allocate(alignToDevice(initialSize));
    }

    long size() {
        return this.size;
    }

    long buffer() {
        return this.buffer;
    }

    /** The mapped range. Writes through this view are visible to the GPU after the owning queue submission. */
    ByteBuffer mapped() {
        return MemoryUtil.memByteBuffer(this.mappedAddress, (int) this.size);
    }

    void ensureCapacity(long required) {
        if (required <= this.size) {
            return;
        }
        long previousSize = this.size;
        long previousBuffer = this.buffer;
        long previousMemory = this.memory;
        long previousAddress = this.mappedAddress;

        allocate(alignToDevice(required));

        // Keep the old contents: blocks written earlier in the frame are still referenced by recorded draws.
        MemoryUtil.memCopy(previousAddress, this.mappedAddress, previousSize);

        vkUnmapMemory(this.context.device(), previousMemory);
        vkDestroyBuffer(this.context.device(), previousBuffer, null);
        vkFreeMemory(this.context.device(), previousMemory, null);
    }

    void delete() {
        if (this.mappedAddress != 0) {
            vkUnmapMemory(this.context.device(), this.memory);
            this.mappedAddress = 0;
        }
        if (this.buffer != 0) {
            vkDestroyBuffer(this.context.device(), this.buffer, null);
            this.buffer = 0;
        }
        if (this.memory != 0) {
            vkFreeMemory(this.context.device(), this.memory, null);
            this.memory = 0;
        }
        this.size = 0;
    }

    private void allocate(long bytes) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkBufferCreateInfo create = VkBufferCreateInfo.calloc(stack).sType$Default()
                    .size(bytes)
                    .usage(VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT)
                    .sharingMode(VK_SHARING_MODE_EXCLUSIVE);
            LongBuffer handle = stack.mallocLong(1);
            check(vkCreateBuffer(this.context.device(), create, null, handle), "vkCreateBuffer(uniform)");
            this.buffer = handle.get(0);

            VkMemoryRequirements requirements = VkMemoryRequirements.calloc(stack);
            vkGetBufferMemoryRequirements(this.context.device(), this.buffer, requirements);

            VkPhysicalDeviceMemoryProperties properties = VkPhysicalDeviceMemoryProperties.calloc(stack);
            vkGetPhysicalDeviceMemoryProperties(this.context.physicalDevice(), properties);
            int type = findMemoryType(properties, requirements.memoryTypeBits(),
                    VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
            if (type < 0) {
                vkDestroyBuffer(this.context.device(), this.buffer, null);
                this.buffer = 0;
                throw new IllegalStateException("No host-visible, host-coherent memory type for the terrain uniforms");
            }

            VkMemoryAllocateInfo allocate = VkMemoryAllocateInfo.calloc(stack).sType$Default()
                    .allocationSize(requirements.size())
                    .memoryTypeIndex(type);
            if (org.soutalab.celtium.vulkan.VulkanAllocationFault.shouldFail("VulkanUniformBuffer:110")) {
                // Cleaned up before it leaves: VulkanBuffer already does this, and without it a failed allocation here
                // leaked the VkBuffer that had just been created for it.
                vkDestroyBuffer(this.context.device(), this.buffer, null);
                this.buffer = 0;
                throw new IllegalStateException("injected allocation failure: VulkanUniformBuffer:110");
            }
            try {
                check(vkAllocateMemory(this.context.device(), allocate, null, handle), "vkAllocateMemory(uniform)");
            } catch (RuntimeException error) {
                vkDestroyBuffer(this.context.device(), this.buffer, null);
                this.buffer = 0;
                throw error;
            }
            this.memory = handle.get(0);
            check(vkBindBufferMemory(this.context.device(), this.buffer, this.memory, 0), "vkBindBufferMemory(uniform)");

            org.lwjgl.PointerBuffer pointer = stack.mallocPointer(1);
            check(vkMapMemory(this.context.device(), this.memory, 0, bytes, 0, pointer), "vkMapMemory(uniform)");
            this.mappedAddress = pointer.get(0);
            this.size = bytes;
        }
    }

    private static int findMemoryType(VkPhysicalDeviceMemoryProperties properties, int typeBits, int required) {
        for (int i = 0; i < properties.memoryTypeCount(); i++) {
            if ((typeBits & (1 << i)) != 0 && (properties.memoryTypes(i).propertyFlags() & required) == required) {
                return i;
            }
        }
        return -1;
    }

    private long alignToDevice(long value) {
        long alignment = 256;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            org.lwjgl.vulkan.VkPhysicalDeviceProperties properties =
                    org.lwjgl.vulkan.VkPhysicalDeviceProperties.calloc(stack);
            vkGetPhysicalDeviceProperties(this.context.physicalDevice(), properties);
            alignment = Math.max(256, properties.limits().minUniformBufferOffsetAlignment());
        }
        long mask = alignment - 1;
        return Math.max(1024, (value + mask) & ~mask);
    }

    private static void check(int result, String operation) {
        if (result != VK_SUCCESS) {
            throw new IllegalStateException(operation + " returned " + result);
        }
    }
}
