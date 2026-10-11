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
    /**
     * Host-visible buffers created and destroyed since process start, with the count still live.
     * <p>
     * OPT-05's acceptance is "no vkAllocateMemory proportional to the group count after warm-up", and that is a
     * property of how many times this class allocated, not of a frame time. Counting it here - at the only place a
     * host buffer's memory is allocated - keeps the statement in the report instead of in a source reading.
     */
    private static long allocations;
    private static long deletions;
    /** Buffers created for the frame arena, counted apart so its bounded growth is not read as per-group allocation. */
    private static long arenaAllocations;

    /** {@return the host-buffer allocation census, so a report can say whether allocation scales with groups}. */
    public static synchronized String allocationSummary() {
        return "hostBuffersCreated=" + allocations + " hostBuffersDestroyed=" + deletions
                + " hostBuffersLive=" + (allocations - deletions) + " hostBuffersFromFrameArena=" + arenaAllocations;
    }

    private static synchronized void noteAllocated(boolean arena) {
        allocations++;
        if (arena) {
            arenaAllocations++;
        }
    }

    private static synchronized void noteDeleted() {
        deletions++;
    }

    private final VulkanContext context;
    private final int usageFlags;
    /** Whether this buffer belongs to the frame arena (the census separates its growth from per-group buffers). */
    private final boolean arenaOwned;

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
        this(context, sizeBytes, usageFlags, false);
    }

    /**
     * The same buffer, marked as the frame arena's when {@code arenaOwned} is set.
     * <p>
     * A frame arena slice is written while the frame is being built and read by the GPU only when that frame's
     * submission runs, so its reuse is bounded by a completion fence rather than by the frame having ended; the flag
     * exists so the report can tell those buffers apart from the ones a model mesh or a UI mesh owns.
     */
    public VulkanHostBuffer(VulkanContext context, long sizeBytes, int usageFlags, boolean arenaOwned) {
        if (context == null) {
            throw new IllegalArgumentException("context must not be null");
        }
        if (sizeBytes <= 0L) {
            throw new IllegalArgumentException("sizeBytes must be positive, was " + sizeBytes);
        }
        this.context = context;
        this.usageFlags = usageFlags;
        this.arenaOwned = arenaOwned;
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
        VulkanHostBuffer replacement = new VulkanHostBuffer(this.context, requiredBytes, this.usageFlags,
                this.arenaOwned);
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
            noteDeleted();
        }
        if (this.memory != VK_NULL_HANDLE) {
            vkFreeMemory(this.context.device(), this.memory, null);
            this.memory = VK_NULL_HANDLE;
        }
    }

    private void create() {
        VkDevice device = this.context.device();
        try {
            celtium$create(device);
        } catch (RuntimeException | Error error) {
            // The allocations happen in order - buffer, memory, bind, map - so a failure part way through leaves the
            // earlier ones alive: the fault injection before vkAllocateMemory is exactly that case, a buffer created
            // with no memory behind it. delete() releases whatever exists and is idempotent, so the partial state is
            // released here rather than leaked (audit ownership gap).
            delete();
            throw error;
        }
    }

    private void celtium$create(VkDevice device) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkBufferCreateInfo createInfo = VkBufferCreateInfo.calloc(stack).sType$Default()
                    .size(this.size)
                    .usage(this.usageFlags)
                    .sharingMode(VK_SHARING_MODE_EXCLUSIVE);
            LongBuffer handleBuffer = stack.mallocLong(1);
            check(vkCreateBuffer(device, createInfo, null, handleBuffer), "vkCreateBuffer(host buffer, " + this.size
                    + " bytes)");
            this.handle = handleBuffer.get(0);
            // Counted from the moment the buffer exists: delete() counts the matching destruction, so a create that
            // fails before this line leaves the census balanced (audit ownership gap's partial state).
            noteAllocated(this.arenaOwned);

            VkMemoryRequirements requirements = VkMemoryRequirements.calloc(stack);
            vkGetBufferMemoryRequirements(device, this.handle, requirements);
            int memoryType = this.context.memoryTypeIndex(requirements.memoryTypeBits(),
                    VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
                    "a host-visible buffer of " + this.size + " bytes");
            VkMemoryAllocateInfo allocate = VkMemoryAllocateInfo.calloc(stack).sType$Default()
                    .allocationSize(requirements.size())
                    .memoryTypeIndex(memoryType);
            LongBuffer memoryHandle = stack.mallocLong(1);
            if (org.soutalab.celtium.vulkan.VulkanAllocationFault.shouldFail("VulkanHostBuffer:126")) {
                throw new IllegalStateException("injected allocation failure: VulkanHostBuffer:126");
            }
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
