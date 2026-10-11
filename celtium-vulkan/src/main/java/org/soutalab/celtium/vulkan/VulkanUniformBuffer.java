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
        publish(create(alignToDevice(initialSize)));
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

    /**
     * Grows the buffer to hold at least {@code required} bytes, keeping the old contents.
     * <p>
     * <b>BH-24.</b> The replacement is built as its own resource set and this object is not touched until it is
     * complete: {@link #create} throws with only its own partial resources cleaned up, so a failed growth leaves the
     * previous buffer, memory and mapped address exactly as they were. The earlier version saved the old handles in
     * locals and then called {@code allocate}, which wrote {@code this.buffer} first - so a failure anywhere after
     * that left the object holding a zero or half-published handle while the old buffer was only in a local, leaked
     * and unreachable. Only after the copy and the swap is the old set destroyed.
     */
    void ensureCapacity(long required) {
        if (required <= this.size) {
            return;
        }
        long previousSize = this.size;
        long previousBuffer = this.buffer;
        long previousMemory = this.memory;
        long previousAddress = this.mappedAddress;

        Allocation replacement = create(alignToDevice(required));

        // Keep the old contents: blocks written earlier in the frame are still referenced by recorded draws.
        MemoryUtil.memCopy(previousAddress, replacement.mappedAddress, previousSize);

        publish(replacement);
        this.growths++;

        vkUnmapMemory(this.context.device(), previousMemory);
        vkDestroyBuffer(this.context.device(), previousBuffer, null);
        vkFreeMemory(this.context.device(), previousMemory, null);
    }

    /** Successful growths, so a run says whether the replacement path was taken at all. */
    private int growths;

    /** {@return how many times the buffer was replaced by a larger one}. */
    int growths() {
        return this.growths;
    }

    /** The four handles one allocation owns, built completely before any of them is published to this object. */
    private static final class Allocation {
        private long buffer;
        private long memory;
        private long mappedAddress;
        private long size;
    }

    /** Makes the replacement the live one; called only once its allocation, binding and mapping have all succeeded. */
    private void publish(Allocation allocation) {
        this.buffer = allocation.buffer;
        this.memory = allocation.memory;
        this.mappedAddress = allocation.mappedAddress;
        this.size = allocation.size;
    }

    /**
     * Builds a fresh buffer/memory/mapping of {@code bytes} and returns it, or throws with everything it created
     * destroyed. Nothing here writes a field of this object, which is what lets {@link #ensureCapacity} keep the old
     * allocation intact when a growth fails.
     */
    private Allocation create(long bytes) {
        Allocation allocation = new Allocation();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkBufferCreateInfo create = VkBufferCreateInfo.calloc(stack).sType$Default()
                    .size(bytes)
                    .usage(VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT)
                    .sharingMode(VK_SHARING_MODE_EXCLUSIVE);
            LongBuffer handle = stack.mallocLong(1);
            check(vkCreateBuffer(this.context.device(), create, null, handle), "vkCreateBuffer(uniform)");
            allocation.buffer = handle.get(0);

            VkMemoryRequirements requirements = VkMemoryRequirements.calloc(stack);
            vkGetBufferMemoryRequirements(this.context.device(), allocation.buffer, requirements);

            VkPhysicalDeviceMemoryProperties properties = VkPhysicalDeviceMemoryProperties.calloc(stack);
            vkGetPhysicalDeviceMemoryProperties(this.context.physicalDevice(), properties);
            int type = findMemoryType(properties, requirements.memoryTypeBits(),
                    VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
            if (type < 0) {
                throw destroyReplacement(allocation,
                        "No host-visible, host-coherent memory type for the terrain uniforms");
            }

            VkMemoryAllocateInfo allocate = VkMemoryAllocateInfo.calloc(stack).sType$Default()
                    .allocationSize(requirements.size())
                    .memoryTypeIndex(type);
            if (org.soutalab.celtium.vulkan.VulkanAllocationFault.shouldFail("VulkanUniformBuffer:110")) {
                throw destroyReplacement(allocation, "injected allocation failure: VulkanUniformBuffer:110");
            }
            try {
                check(vkAllocateMemory(this.context.device(), allocate, null, handle), "vkAllocateMemory(uniform)");
            } catch (RuntimeException error) {
                throw destroyReplacement(allocation, error.getMessage());
            }
            allocation.memory = handle.get(0);
            check(vkBindBufferMemory(this.context.device(), allocation.buffer, allocation.memory, 0),
                    "vkBindBufferMemory(uniform)");

            org.lwjgl.PointerBuffer pointer = stack.mallocPointer(1);
            check(vkMapMemory(this.context.device(), allocation.memory, 0, bytes, 0, pointer), "vkMapMemory(uniform)");
            allocation.mappedAddress = pointer.get(0);
            allocation.size = bytes;
            return allocation;
        } catch (RuntimeException | LinkageError error) {
            // Every path above either returned a complete allocation or threw through destroyReplacement, which
            // released its own resources; this catch only makes that guarantee explicit for an unexpected failure.
            destroyReplacement(allocation, null);
            throw error;
        }
    }

    /** Releases whatever of a failed replacement exists and returns the exception to throw, never a live allocation. */
    private IllegalStateException destroyReplacement(Allocation allocation, String message) {
        if (allocation.mappedAddress != 0) {
            vkUnmapMemory(this.context.device(), allocation.memory);
            allocation.mappedAddress = 0;
        }
        if (allocation.buffer != 0) {
            vkDestroyBuffer(this.context.device(), allocation.buffer, null);
            allocation.buffer = 0;
        }
        if (allocation.memory != 0) {
            vkFreeMemory(this.context.device(), allocation.memory, null);
            allocation.memory = 0;
        }
        return new IllegalStateException(message == null ? "uniform buffer replacement failed" : message);
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
