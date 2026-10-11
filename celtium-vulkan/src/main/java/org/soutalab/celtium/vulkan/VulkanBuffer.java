package org.soutalab.celtium.vulkan;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkBufferCopy;
import org.lwjgl.vulkan.VkBufferCreateInfo;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkMemoryAllocateInfo;
import org.lwjgl.vulkan.VkMemoryRequirements;

import java.nio.LongBuffer;

import static org.lwjgl.vulkan.VK10.*;

/**
 * A device-local Vulkan buffer with its own memory allocation.
 * <p>
 * The buffer always carries the transfer source and destination usages, because every byte that reaches it is
 * written through {@link VulkanContext#uploadToBuffer} and every growth copies the old contents across. Vertex and
 * index usages are added by the caller.
 * <p>
 * <b>Handles:</b> LWJGL represents non-dispatchable Vulkan handles such as {@code VkBuffer} and
 * {@code VkDeviceMemory} as plain {@code long} values - there is no {@code VkBuffer} wrapper type in LWJGL 3.4.1 -
 * so {@link #buffer()} returns the raw handle, ready to be passed to {@code vkCmdCopyBuffer},
 * {@code vkCmdBindVertexBuffers}, and so on.
 * <p>
 * <b>Threading:</b> every method must be called on the render thread that owns the {@link VulkanContext}, and
 * {@link #delete()} must happen before the context is deleted. All uploads wait for the queue to go idle before they
 * return, so a buffer is never still in use by the GPU when a caller finishes an upload call.
 */
public final class VulkanBuffer {
    private final VulkanContext context;
    private final int usageFlags;

    private long handle = VK_NULL_HANDLE;
    private long memory = VK_NULL_HANDLE;
    private long size;

    /**
     * Creates the buffer and binds a device-local memory allocation to it.
     *
     * @param context   owning context; the buffer must be deleted before the context is
     * @param sizeBytes requested size, rounded up to a multiple of 4; must be positive
     * @param usageFlags extra {@code VK_BUFFER_USAGE_*} flags; the transfer flags are added automatically
     * @throws IllegalStateException when the buffer, the memory or the binding cannot be created, or when the device
     *                               has no device-local memory type
     */
    public VulkanBuffer(VulkanContext context, long sizeBytes, int usageFlags) {
        if (context == null) {
            throw new IllegalArgumentException("context must not be null");
        }
        if (sizeBytes <= 0L) {
            throw new IllegalArgumentException("sizeBytes must be positive, was " + sizeBytes);
        }
        this.context = context;
        this.usageFlags = usageFlags | VK_BUFFER_USAGE_TRANSFER_SRC_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT;
        this.size = VulkanContext.alignUp4(sizeBytes);
        create();
    }

    /** Current size of the buffer in bytes. */
    public long size() {
        return this.size;
    }

    /** The raw {@code VkBuffer} handle; {@code VK_NULL_HANDLE} once the buffer has been deleted. */
    public long buffer() {
        return this.handle;
    }

    /**
     * Uploads {@code length} bytes from a native address into this buffer at {@code offsetBytes}.
     * <p>
     * {@code offsetBytes} must be a multiple of 4, and the written range must be padded to 4 bytes by the allocator
     * because the underlying copy is padded; see
     * {@link VulkanContext#uploadToBuffer(long, long, long, long)}. Render thread only.
     */
    public void write(long offsetBytes, long sourceAddress, long length) {
        requireLive();
        requireRange(offsetBytes, length);
        if (length == 0L) {
            return;
        }
        // The deferred form: the handle is resolved when the frame's upload batch is submitted, so a growth of this
        // buffer between here and the flush (the arena grows when a mesh does not fit) cannot leave the copy naming a
        // destroyed VkBuffer. The copy still lands at this offset, because offsets survive a growth.
        this.context.uploadToBuffer(this, offsetBytes, sourceAddress, length);
    }

    /**
     * Copies a byte range of this buffer into a host-visible buffer and makes it readable by the host.
     * <p>
     * This exists for one question that cannot be answered from the CPU side: whether the device-local bytes are the
     * bytes the upload was given. The copy is recorded with the barrier a host read requires
     * ({@code TRANSFER_WRITE} to {@code HOST_READ}), and the queue is drained before this returns, so a caller can
     * compare {@link VulkanHostBuffer#mappedAddress()} against its own source immediately. Diagnostic path only: it
     * submits and waits per call.
     * <p>
     * Render thread only. The destination must be at least {@code length} bytes.
     *
     * @throws IllegalArgumentException when the range is outside this buffer or does not fit the destination
     */
    public void readInto(VulkanHostBuffer destination, long offsetBytes, long length) {
        requireLive();
        if (destination == null) {
            throw new IllegalArgumentException("destination must not be null");
        }
        requireRange(offsetBytes, length);
        if (length == 0L) {
            return;
        }
        if (length > destination.size()) {
            throw new IllegalArgumentException("A read of " + length + " bytes does not fit the destination buffer of "
                    + destination.size() + " bytes.");
        }
        // A copy of this buffer may still be pending, or in flight, in the frame's upload batch, and this read has to
        // see it: the batch is submitted and waited for here rather than assuming it already went out.
        this.context.settleUploads();
        VkCommandBuffer commandBuffer = this.context.beginCommands();
        try {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                VkBufferCopy.Buffer copy = VkBufferCopy.calloc(1, stack);
                copy.get(0).srcOffset(offsetBytes).dstOffset(0L).size(length);
                vkCmdCopyBuffer(commandBuffer, this.handle, destination.buffer(), copy);
                // A copy is a transfer write; without this the host read below may see the bytes the copy replaced.
                org.lwjgl.vulkan.VkMemoryBarrier.Buffer barrier = org.lwjgl.vulkan.VkMemoryBarrier.calloc(1, stack);
                barrier.get(0).sType$Default()
                        .srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                        .dstAccessMask(VK_ACCESS_HOST_READ_BIT);
                vkCmdPipelineBarrier(commandBuffer, VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_HOST_BIT,
                        0, barrier, null, null);
            }
        } finally {
            // Same shape as the backend's own readback: whatever was recorded is submitted and waited for, and the
            // command buffer is freed, even when recording threw halfway through.
            this.context.endCommands(commandBuffer);
        }
    }

    /**
     * Grows the buffer to at least {@code requiredBytes} and keeps its contents: the smaller requested size is a
     * no-op, otherwise a new buffer of exactly {@code requiredBytes} (rounded up to 4) is created, the old contents
     * are copied with a {@code vkCmdCopyBuffer} through {@link VulkanContext}, and only then are the old buffer and
     * its memory destroyed. The growth policy belongs to the caller - the arena decides by how much to grow and reads
     * {@link #size()} back afterwards.
     * <p>
     * The handle changes, so any derived state that cached {@link #buffer()} must be refreshed. The byte ranges
     * themselves do not move, so existing offsets stay valid. Render thread only.
     *
     * @throws IllegalStateException when the replacement buffer cannot be created; the old buffer stays usable
     */
    public void ensureCapacity(long requiredBytes) {
        requireLive();
        if (requiredBytes <= this.size) {
            return;
        }
        // A copy into this buffer may still be pending, or already submitted and in flight, in the frame's upload
        // batch. Both are settled here, while the handle the copies resolve to is still the current one: the copy-out
        // below reads this buffer's contents and the old buffer is destroyed at the end of this call. Growth is rare
        // (a handful per run), so this does not put a per-copy wait back into the upload path.
        this.context.settleUploads();
        long newSize = VulkanContext.alignUp4(requiredBytes);
        long oldHandle = this.handle;
        long oldMemory = this.memory;
        long oldSize = this.size;
        // Create the replacement first. If that fails, the old objects are put back so a failed growth does not
        // destroy live terrain data.
        this.handle = VK_NULL_HANDLE;
        this.memory = VK_NULL_HANDLE;
        this.size = newSize;
        try {
            create();
        } catch (RuntimeException | Error error) {
            this.handle = oldHandle;
            this.memory = oldMemory;
            this.size = oldSize;
            throw error;
        }
        VkCommandBuffer commandBuffer = this.context.beginCommands();
        try {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                // Both sizes are multiples of 4, which is what a Vulkan buffer copy requires.
                VkBufferCopy.Buffer copy = VkBufferCopy.calloc(1, stack);
                copy.get(0).srcOffset(0L).dstOffset(0L).size(oldSize);
                vkCmdCopyBuffer(commandBuffer, oldHandle, this.handle, copy);
            }
            this.context.endCommands(commandBuffer);
        } catch (RuntimeException | Error error) {
            // The contents were not copied, so the new buffer is undefined: drop it and stay on the intact one.
            // The old buffer may still be referenced by commands submitted in an earlier frame, so the queue is
            // drained before it is destroyed; validation reports the alternative as a buffer destroyed while in use.
            check(vkQueueWaitIdle(this.context.queue()), "vkQueueWaitIdle(buffer growth)");
            vkDestroyBuffer(this.context.device(), this.handle, null);
            vkFreeMemory(this.context.device(), this.memory, null);
            this.handle = oldHandle;
            this.memory = oldMemory;
            this.size = oldSize;
            throw error;
        }
        // endCommands waited for the queue to go idle, so the GPU no longer reads the old objects.
        vkDestroyBuffer(this.context.device(), oldHandle, null);
        vkFreeMemory(this.context.device(), oldMemory, null);
    }

    /**
     * Destroys the buffer and frees its memory. Idempotent, and safe to call after the context is gone (destroying
     * the logical device frees its children implicitly). Render thread only.
     */
    public void delete() {
        // A copy already submitted by the frame's upload batch may still be reading or writing this buffer. The batch
        // is confirmed complete before the handle goes away; that is the destination half of the rule the asynchronous
        // batch needs, and it costs a wait only where a batch happens to be in flight (teardown, eviction, reload).
        this.context.awaitUploads();
        VkDevice device = this.context.device();
        if (device == null) {
            this.handle = VK_NULL_HANDLE;
            this.memory = VK_NULL_HANDLE;
            return;
        }
        if (this.handle != VK_NULL_HANDLE) {
            vkDestroyBuffer(device, this.handle, null);
            this.handle = VK_NULL_HANDLE;
        }
        if (this.memory != VK_NULL_HANDLE) {
            vkFreeMemory(device, this.memory, null);
            this.memory = VK_NULL_HANDLE;
        }
    }

    private void create() {
        VkDevice device = this.context.device();
        long createdBuffer = VK_NULL_HANDLE;
        long createdMemory = VK_NULL_HANDLE;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkBufferCreateInfo createInfo = VkBufferCreateInfo.calloc(stack).sType$Default()
                    .size(this.size)
                    .usage(this.usageFlags)
                    .sharingMode(VK_SHARING_MODE_EXCLUSIVE);
            LongBuffer handleBuffer = stack.mallocLong(1);
            check(vkCreateBuffer(device, createInfo, null, handleBuffer), "vkCreateBuffer(" + this.size
                    + " bytes, usage 0x" + Integer.toHexString(this.usageFlags) + ")");
            createdBuffer = handleBuffer.get(0);
            VkMemoryRequirements requirements = VkMemoryRequirements.calloc(stack);
            vkGetBufferMemoryRequirements(device, createdBuffer, requirements);
            int memoryType = this.context.memoryTypeIndex(requirements.memoryTypeBits(),
                    VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT, "a device-local buffer of " + this.size + " bytes");
            VkMemoryAllocateInfo allocate = VkMemoryAllocateInfo.calloc(stack).sType$Default()
                    .allocationSize(requirements.size())
                    .memoryTypeIndex(memoryType);
            LongBuffer memoryHandle = stack.mallocLong(1);
            if (org.soutalab.celtium.vulkan.VulkanAllocationFault.shouldFail("VulkanBuffer:189")) {
                throw new IllegalStateException("injected allocation failure: VulkanBuffer:189");
            }
            check(vkAllocateMemory(device, allocate, null, memoryHandle), "vkAllocateMemory(" + requirements.size()
                    + " bytes, memory type " + memoryType + ")");
            createdMemory = memoryHandle.get(0);
            check(vkBindBufferMemory(device, createdBuffer, createdMemory, 0), "vkBindBufferMemory");
        } catch (RuntimeException | Error error) {
            if (createdBuffer != VK_NULL_HANDLE) {
                vkDestroyBuffer(device, createdBuffer, null);
            }
            if (createdMemory != VK_NULL_HANDLE) {
                vkFreeMemory(device, createdMemory, null);
            }
            throw error;
        }
        this.handle = createdBuffer;
        this.memory = createdMemory;
    }

    private void requireLive() {
        if (this.handle == VK_NULL_HANDLE) {
            throw new IllegalStateException("This VulkanBuffer has been deleted.");
        }
    }

    private void requireRange(long offsetBytes, long length) {
        if (offsetBytes < 0L) {
            throw new IllegalArgumentException("offsetBytes must not be negative, was " + offsetBytes);
        }
        if (length < 0L) {
            throw new IllegalArgumentException("length must not be negative, was " + length);
        }
        if (length > this.size - offsetBytes) {
            throw new IllegalArgumentException("A write of " + length + " bytes at offset " + offsetBytes
                    + " exceeds the buffer size of " + this.size + " bytes.");
        }
    }

    private static void check(int result, String operation) {
        if (result != VK_SUCCESS) {
            throw new IllegalStateException(operation + " returned VkResult " + result);
        }
    }
}
