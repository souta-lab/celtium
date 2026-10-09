package org.soutalab.celtium.vulkan;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VkBufferCreateInfo;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkMemoryAllocateInfo;
import org.lwjgl.vulkan.VkMemoryRequirements;

import java.nio.LongBuffer;

import static org.lwjgl.vulkan.VK10.*;

/**
 * A host-visible, persistently mapped Vulkan buffer.
 * <p>
 * Some data is produced on the CPU for every frame and consumed by the GPU immediately - indirect draw commands in
 * particular - and copying it through a staging buffer would cost more than the draw itself. This class owns such a
 * buffer together with its mapped address, so callers write straight into device-visible memory.
 * <p>
 * <b>Handles:</b> LWJGL represents non-dispatchable handles as plain {@code long} values, so {@link #buffer()}
 * returns the raw {@code VkBuffer}. Render thread only, like the rest of the backend.
 */
public final class VulkanHostBuffer {
    private final VulkanContext context;
    private final int usageFlags;

    private long handle = VK_NULL_HANDLE;
    private long memory = VK_NULL_HANDLE;
    private long mapped = 0L;
    private long size;

    /**
     * @param context    owning context; the buffer must be deleted before the context is
     * @param sizeBytes  requested size, rounded up to a multiple of 4; must be positive
     * @param usageFlags extra {@code VK_BUFFER_USAGE_*} flags
     * @throws IllegalStateException when the buffer, its memory, the binding or the mapping cannot be created, or
     *                               when the device has no host-visible coherent memory type
     */
    public VulkanHostBuffer(VulkanContext context, long sizeBytes, int usageFlags) {
        if (context == null) {
            throw new IllegalArgumentException("context must not be null");
        }
        if (sizeBytes <= 0L) {
            throw new IllegalArgumentException("sizeBytes must be positive, was " + sizeBytes);
        }
        this.context = context;
        this.usageFlags = usageFlags;
        this.size = VulkanContext.alignUp4(sizeBytes);
        create();
    }

    /** The raw {@code VkBuffer} handle, or {@code VK_NULL_HANDLE} once deleted. */
    public long buffer() {
        return this.handle;
    }

    /** Address of the mapped memory, valid until the buffer is grown or deleted. */
    public long mappedAddress() {
        return this.mapped;
    }

    /** Size in bytes, always a multiple of 4. */
    public long size() {
        return this.size;
    }

    /**
     * Grows the buffer when {@code requiredBytes} does not fit, **preserving what it already holds**.
     * <p>
     * The previous version deleted and recreated the buffer, which the mesh's own bookkeeping did not expect: it appends
     * vertices and indices while keeping byte counters for what it wrote earlier, so crossing the capacity silently threw
     * away everything before the growth - and a single large batch could lose its own first half while its indices still
     * referred to it. The replacement is created first and the old contents copied into it, so a failure to allocate
     * leaves the existing data untouched.
     */
    public void ensureCapacity(long requiredBytes) {
        if (requiredBytes <= this.size) {
            return;
        }
        VulkanHostBuffer replacement = new VulkanHostBuffer(this.context, requiredBytes, this.usageFlags);
        MemoryUtil.memCopy(this.mapped, replacement.mapped, this.size);
        delete();
        this.handle = replacement.handle;
        this.memory = replacement.memory;
        this.mapped = replacement.mapped;
        this.size = replacement.size;
    }

    public void delete() {
        if (this.mapped != 0L) {
            vkUnmapMemory(this.context.device(), this.memory);
            this.mapped = 0L;
        }
        if (this.handle != VK_NULL_HANDLE) {
            vkDestroyBuffer(this.context.device(), this.handle, null);
            this.handle = VK_NULL_HANDLE;
        }
        if (this.memory != VK_NULL_HANDLE) {
            vkFreeMemory(this.context.device(), this.memory, null);
            this.memory = VK_NULL_HANDLE;
        }
    }

    private void create() {
        VkDevice device = this.context.device();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkBufferCreateInfo createInfo = VkBufferCreateInfo.calloc(stack).sType$Default()
                    .size(this.size)
                    .usage(this.usageFlags)
                    .sharingMode(VK_SHARING_MODE_EXCLUSIVE);
            LongBuffer handleBuffer = stack.mallocLong(1);
            check(vkCreateBuffer(device, createInfo, null, handleBuffer), "vkCreateBuffer(host buffer, " + this.size
                    + " bytes)");
            this.handle = handleBuffer.get(0);

            VkMemoryRequirements requirements = VkMemoryRequirements.calloc(stack);
            vkGetBufferMemoryRequirements(device, this.handle, requirements);
            int memoryType = this.context.memoryTypeIndex(requirements.memoryTypeBits(),
                    VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
                    "a host-visible buffer of " + this.size + " bytes");
            VkMemoryAllocateInfo allocate = VkMemoryAllocateInfo.calloc(stack).sType$Default()
                    .allocationSize(requirements.size())
                    .memoryTypeIndex(memoryType);
            LongBuffer memoryHandle = stack.mallocLong(1);
            check(vkAllocateMemory(device, allocate, null, memoryHandle), "vkAllocateMemory(host buffer, "
                    + requirements.size() + " bytes, memory type " + memoryType + ")");
            this.memory = memoryHandle.get(0);
            check(vkBindBufferMemory(device, this.handle, this.memory, 0L), "vkBindBufferMemory(host buffer)");

            org.lwjgl.PointerBuffer pointer = stack.mallocPointer(1);
            check(vkMapMemory(device, this.memory, 0L, this.size, 0, pointer), "vkMapMemory(host buffer)");
            this.mapped = pointer.get(0);
        }
    }

    private static void check(int result, String what) {
        if (result != VK_SUCCESS) {
            throw new IllegalStateException(what + " failed with VkResult " + result);
        }
    }
}
