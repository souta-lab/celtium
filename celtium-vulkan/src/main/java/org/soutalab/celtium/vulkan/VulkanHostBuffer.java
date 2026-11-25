package org.soutalab.celtium.vulkan;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VkBufferCreateInfo;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkMemoryRequirements;
import org.soutalab.celtium.vulkan.hal.SlabAllocationHistogram;
import org.soutalab.celtium.vulkan.hal.SlabAllocator;

import java.nio.LongBuffer;

import static org.lwjgl.vulkan.VK10.*;

/**
 * A host-visible, persistently mapped Vulkan buffer, backed by a sub-allocation of a fixed host-visible
 * slab (see {@link SlabAllocator}).
 * <p>
 * Some data is produced on the CPU for every frame and consumed by the GPU immediately - indirect draw commands in
 * particular - and copying it through a staging buffer would cost more than the draw itself. This class owns such a
 * buffer together with its mapped address, so callers write straight into device-visible memory.
 * <p>
 * The buffer owns no {@code VkDeviceMemory} of its own: its bytes live inside one of the context's 64 MiB
 * host-visible slabs, bound at the sub-allocation's offset, and the mapped address is the slab's mapping plus
 * that offset (slabs are mapped once for their whole life, so there is no per-buffer {@code vkMapMemory}).
 * That is the new architecture's memory contract - the live {@code VkDeviceMemory} count is the slab count,
 * not the buffer count. Before this migration every host buffer paid its own {@code vkAllocateMemory}
 * (site {@code "hostBuffer"}), and the Phase 1 census leg (r3, 2026-11-25) measured that site at
 * 16,641 of 16,711 live allocations (99.6%). The host-visible pools are capped at 8 ordinary slabs per
 * memory type, so the live allocation count for all host buffers combined is bounded by that cap (plus one
 * dedicated slab per live oversized &gt;64 MiB request, which the allocator frees once it empties), however
 * many host buffers exist. Deletion hands the sub-allocation back to its pool as a deferred release
 * instead of calling {@code vkFreeMemory}.
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
     * Since the slab migration this counts buffers, not device allocations: the device-allocation side is
     * the context's allocation census in {@link VulkanContext#memoryAllocationSummary()}, where each slab is
     * one counted allocation.
     */
    private static final java.util.concurrent.atomic.AtomicLong ALLOCATIONS =
            new java.util.concurrent.atomic.AtomicLong();
    private static final java.util.concurrent.atomic.AtomicLong DELETIONS =
            new java.util.concurrent.atomic.AtomicLong();
    /** Buffers created for the frame arena, counted apart so its bounded growth is not read as per-group allocation. */
    private static final java.util.concurrent.atomic.AtomicLong ARENA_ALLOCATIONS =
            new java.util.concurrent.atomic.AtomicLong();

    /** {@return the host-buffer allocation census, so a report can say whether allocation scales with groups}. */
    public static String allocationSummary() {
        long allocs = ALLOCATIONS.get();
        long dels = DELETIONS.get();
        return "hostBuffersCreated=" + allocs + " hostBuffersDestroyed=" + dels
                + " hostBuffersLive=" + (allocs - dels) + " hostBuffersFromFrameArena=" + ARENA_ALLOCATIONS.get();
    }

    private static void noteAllocated(boolean arena) {
        ALLOCATIONS.incrementAndGet();
        if (arena) {
            ARENA_ALLOCATIONS.incrementAndGet();
        }
    }

    private static void noteDeleted() {
        DELETIONS.incrementAndGet();
    }

    private final VulkanContext context;
    private final int usageFlags;
    /** Whether this buffer belongs to the frame arena (the census separates its growth from per-group buffers). */
    private final boolean arenaOwned;
    /**
     * Whether growth keeps the superseded buffers alive until {@link #delete()} instead of retiring them.
     * Off by default; see {@link #retainSupersededBuffers()}.
     */
    private boolean retainSuperseded;
    /** Buffers this one grew away from while retention was on; destroyed by {@link #delete()}, never before. */
    private final SupersededBuffers superseded = new SupersededBuffers();

    private long handle = VK_NULL_HANDLE;
    private SlabAllocator.SlabAllocation allocation;
    private long mapped = 0L;
    private long size;

    /**
     * @param context    owning context; the buffer must be deleted before the context is
     * @param sizeBytes  requested size, rounded up to a multiple of 4; must be positive
     * @param usageFlags extra {@code VK_BUFFER_USAGE_*} flags
     * @throws IllegalStateException when the buffer, its slab allocation or the binding cannot be created, or
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

    /**
     * Opts this buffer into superseded-buffer retention: every buffer it later grows away from stays alive
     * until {@link #delete()} destroys it, instead of being handed to the context's retired-buffer drain at
     * growth time.
     * <p>
     * The drain destroys retired buffers once no recording is open, but it cannot see host-side state that
     * still names the old handle. A descriptor set is exactly that: TerrainEngineA's section-descriptor and
     * factor-base buffers (Tier-A bindings 6 and 7) are named by sets that are only rewritten at a pass head,
     * under conditions, for the current frame slot - so a set written before a growth could keep naming the
     * grown-away handle after the drain destroyed it, which is the VUID-vkCmdDrawIndexedIndirect-None-08114
     * window the 2026-11-26 diagnosis pinned. Retention closes the window for the buffers that opt in: the
     * old handle is never destroyed while any set could still name it, because {@code delete()} runs only at
     * engine teardown, after the sets are gone.
     * <p>
     * Only small, geometrically growing buffers should opt in - the retained count is one per growth, so it
     * is logarithmic in the final size. Call before the first growth; the flag is read at growth time.
     */
    public void retainSupersededBuffers() {
        this.retainSuperseded = true;
    }

    /** Address of the mapped memory (slab mapping plus this allocation's offset), valid until grown or deleted. */
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
        // Grow geometrically: appending in small steps otherwise reallocates and copies the whole buffer each time,
        // and the copy is what a growing mesh pays per step. The visible size is still the buffer's capacity.
        long grown = this.size <= 0L || this.size > (Long.MAX_VALUE / 2L)
                ? requiredBytes : Math.max(requiredBytes, this.size * 2L);
        VulkanHostBuffer replacement = new VulkanHostBuffer(this.context, grown, this.usageFlags,
                this.arenaOwned);
        if (this.size > 0L && this.mapped != 0L) {
            MemoryUtil.memCopy(this.mapped, replacement.mapped, this.size);
        }
        // Growth keeps the old handles alive until no recording can name them, instead of deleting into a possibly
        // open recording (same lifetime rule as the arena and the uniform buffer). The old slab range goes back
        // through the same deferred list: the drain destroys the buffer and releases the sub-allocation keyed on
        // the context's slab release value, so no vkFreeMemory ever names slab memory.
        long oldHandle = this.handle;
        SlabAllocator.SlabAllocation oldAllocation = this.allocation;
        this.handle = replacement.handle;
        this.allocation = replacement.allocation;
        this.mapped = replacement.mapped;
        this.size = replacement.size;
        // The replacement object is only a vehicle: its ownership has moved into this buffer, so detach it
        // before it goes out of scope (nothing ever calls its delete(), and it must not look live if it did).
        replacement.handle = VK_NULL_HANDLE;
        replacement.allocation = null;
        replacement.mapped = 0L;
        if (oldHandle != VK_NULL_HANDLE) {
            if (this.retainSuperseded) {
                // Retention (see retainSupersededBuffers()): the old buffer stays alive and stays counted
                // live - no retire, no deletion count yet. delete() destroys it at teardown, the first
                // moment nothing can still name it.
                this.superseded.retain(oldHandle, oldAllocation);
            } else {
                noteDeleted();
                this.context.celtium$retireSlabBuffer(oldHandle, oldAllocation, "hostBuffer.growth");
            }
        }
    }

    public void delete() {
        this.mapped = 0L;
        if (this.handle == VK_NULL_HANDLE && this.allocation == null && this.superseded.isEmpty()) {
            return;
        }
        VkDevice device = this.context.device();
        if (device == null) {
            // The context is gone: destroying the logical device freed its children implicitly, and the slab
            // allocator's own teardown frees the slab memory. Nothing is left to release here - the retained
            // superseded buffers are the device's children too, so the list is simply dropped with the rest.
            this.handle = VK_NULL_HANDLE;
            this.allocation = null;
            this.superseded.clear();
            return;
        }
        long currentHandle = this.handle;
        SlabAllocator.SlabAllocation currentAllocation = this.allocation;
        this.handle = VK_NULL_HANDLE;
        this.allocation = null;
        java.util.List<SupersededBuffer> retained = this.superseded.drain();
        if (this.context.celtium$recordingOpen()) {
            // An open recording may name this buffer; defer the destroy and the slab release to the context's list.
            if (currentHandle != VK_NULL_HANDLE) {
                noteDeleted();
            }
            this.context.celtium$retireSlabBuffer(currentHandle, currentAllocation, "hostBuffer.delete");
            // A retained superseded buffer takes the same deferred route: a descriptor set written before
            // the growth that superseded it may still name it, which is the whole reason it was retained.
            for (SupersededBuffer old : retained) {
                noteDeleted();
                this.context.celtium$retireSlabBuffer(old.handle, old.allocation, "hostBuffer.delete");
            }
            return;
        }
        destroyAndRelease(device, currentHandle, currentAllocation);
        for (SupersededBuffer old : retained) {
            destroyAndRelease(device, old.handle, old.allocation);
        }
    }

    /**
     * Destroys one buffer and hands its slab range back as a deferred release keyed on the context's
     * current slab release value: a frame still in flight may read the buffer, and the range may only be
     * reused once the completion currency passes the keyed value (the frame timeline's counter on a
     * timeline device, a later retired-buffer drain where there is none). There is no vkFreeMemory - the
     * slab is not this buffer's to free, and no vkUnmapMemory - the slab stays mapped for its whole life.
     */
    private void destroyAndRelease(VkDevice device, long handle, SlabAllocator.SlabAllocation allocation) {
        if (handle != VK_NULL_HANDLE) {
            vkDestroyBuffer(device, handle, null);
            noteDeleted();
        }
        if (allocation != null) {
            allocation.owner().release(allocation, this.context.celtium$slabReleaseValue());
        }
    }

    /** One buffer a {@link VulkanHostBuffer} grew away from while retention was on: a handle and its slab range. */
    static final class SupersededBuffer {
        final long handle;
        final SlabAllocator.SlabAllocation allocation;

        SupersededBuffer(long handle, SlabAllocator.SlabAllocation allocation) {
            this.handle = handle;
            this.allocation = allocation;
        }
    }

    /**
     * The bookkeeping of superseded-buffer retention, kept free of device calls so the growth → retain →
     * delete sequence can be pinned by a CPU test. Pairs are handed off exactly once, by {@link #drain()}:
     * whatever a drain returns is the delete path's to destroy, and a second drain returns nothing, so a
     * repeated {@code delete()} cannot double-destroy a retained buffer.
     */
    static final class SupersededBuffers {
        private final java.util.ArrayList<SupersededBuffer> retained = new java.util.ArrayList<>();

        void retain(long handle, SlabAllocator.SlabAllocation allocation) {
            this.retained.add(new SupersededBuffer(handle, allocation));
        }

        int size() {
            return this.retained.size();
        }

        boolean isEmpty() {
            return this.retained.isEmpty();
        }

        /** Hands every retained pair off in growth order and leaves the store empty. */
        java.util.List<SupersededBuffer> drain() {
            java.util.List<SupersededBuffer> drained = new java.util.ArrayList<>(this.retained);
            this.retained.clear();
            return drained;
        }

        /** Drops every retained pair without handing it off (the context is already gone). */
        void clear() {
            this.retained.clear();
        }
    }

    private void create() {
        VkDevice device = this.context.device();
        try {
            celtium$create(device);
        } catch (RuntimeException | Error error) {
            // The allocations happen in order - buffer, slab allocation, bind - so a failure part way through
            // leaves the earlier ones alive: the fault injection before the slab allocation is exactly that
            // case, a buffer created with no memory behind it. delete() releases whatever exists and is
            // idempotent, so the partial state is released here rather than leaked (audit ownership gap).
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
            if (org.soutalab.celtium.vulkan.VulkanAllocationFault.shouldFail("VulkanHostBuffer:126")) {
                throw new IllegalStateException("injected allocation failure: VulkanHostBuffer:126");
            }
            // The memory comes from the host-visible slab pool the context keys by the memory type this
            // buffer's own requirement bits select, and the bind goes through the allocator, so the slab
            // offset arithmetic lives in exactly one place - the same shape VulkanBuffer uses for its
            // device-local slabs. The mapped address is the slab's persistent mapping plus the offset.
            SlabAllocator allocator = this.context.celtium$hostVisibleSlabs(requirements.memoryTypeBits());
            this.allocation = allocator.allocate(requirements.size(), requirements.alignment(),
                    SlabAllocationHistogram.SITE_HOST_BUFFER);
            allocator.bindBuffer(this.handle, this.allocation);
            this.mapped = allocator.mappedAddress(this.allocation);
        }
    }

    private static void check(int result, String what) {
        if (result != VK_SUCCESS) {
            throw new IllegalStateException(what + " failed with VkResult " + result);
        }
    }
}
