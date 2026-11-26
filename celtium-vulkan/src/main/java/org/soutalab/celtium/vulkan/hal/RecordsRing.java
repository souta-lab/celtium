package org.soutalab.celtium.vulkan.hal;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkBufferCreateInfo;
import org.lwjgl.vulkan.VkMemoryAllocateInfo;
import org.lwjgl.vulkan.VkMemoryRequirements;
import org.soutalab.celtium.vulkan.FrameSubmissionScheduler;
import org.soutalab.celtium.vulkan.VulkanContext;

import java.nio.LongBuffer;

import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_STORAGE_BUFFER_BIT;
import static org.lwjgl.vulkan.VK10.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT;
import static org.lwjgl.vulkan.VK10.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT;
import static org.lwjgl.vulkan.VK10.VK_NULL_HANDLE;
import static org.lwjgl.vulkan.VK10.VK_SHARING_MODE_EXCLUSIVE;
import static org.lwjgl.vulkan.VK10.VK_SUCCESS;
import static org.lwjgl.vulkan.VK10.vkBindBufferMemory;
import static org.lwjgl.vulkan.VK10.vkCreateBuffer;
import static org.lwjgl.vulkan.VK10.vkDestroyBuffer;
import static org.lwjgl.vulkan.VK10.vkGetBufferMemoryRequirements;
import static org.lwjgl.vulkan.VK10.vkMapMemory;
import static org.lwjgl.vulkan.VK10.vkUnmapMemory;

/**
 * The draw records' dedicated pool (ring records separation, F-4): one persistently mapped host
 * buffer, carved into one fixed region per frame slot, each region bump-allocated by its own
 * {@link LinearRingAllocator}.
 * <p>
 * The records used to live in slices of the Stage 2 frame-linear ring, beside the frame's
 * geometry. That sharing made the records the fatal victim of geometry-side ring exhaustion:
 * the store selects a whole window's slice in one allocation, so the window the ring could not
 * serve lost <em>every</em> record at once - the all-or-nothing freeze the bindless verification
 * leg measured (captured pinned, then fallback and dropped draws growing 1:1). This pool is the
 * separation: geometry exhaustion can no longer reach the records at all, and this pool's own
 * exhaustion degrades the way the geometry side's does - the store refuses the record and the
 * caller draws that group on the direct path, so drawing survives instead of vanishing.
 * <p>
 * One buffer for all slots, not one per slot: the buffer-object budget is the constraint that
 * put the records in the shared ring in the first place (Stage 5 stage 3's plan B, adjudicated
 * when Live stood at 26 with a variable part of 22). The budget has since converged (Live 22
 * against the 24 gate, D-62), so the separation can pay exactly one buffer object - this one -
 * and no more. Regions are fixed at creation: a region cannot grow while the other slot's frame
 * is in flight, so growth is not offered; a window that outgrows its region is refused and
 * degrades, and the store's remembered demand (which decays to the previous frame's largest
 * served window at each frame boundary) keeps that the rare path rather than the common one.
 * <p>
 * Reuse discipline mirrors the frame ring's, at the store's own seat: a slot's region is rewound
 * only from {@link #beginSlot(int)}, which the records store calls from its frame boundary -
 * the point where the slot's previous submission is known complete and its next recording has
 * not begun. Nothing else holds this pool's slices (records are consumed by the submission that
 * names them and by nothing else), so the rewind never defers: there is no hold to wait for.
 * The confirmation tail's single convergence point for the shared ring is untouched by this
 * class; the pool is a separate home with its own, simpler gate.
 */
public final class RecordsRing {
    /** Default bytes per slot region, from {@code celtium.vulkan.recordsRingSlotMiB} (clamped 1..8, default 4). */
    public static final long DEFAULT_REGION_BYTES =
            (long) clampRegionMiB(Integer.getInteger("celtium.vulkan.recordsRingSlotMiB", 4).intValue()) << 20;

    private static int clampRegionMiB(int requested) {
        return Math.max(1, Math.min(8, requested));
    }

    private final VulkanContext context;
    private final long regionBytes;
    private final LinearRingAllocator[] allocators;
    private long buffer = VK_NULL_HANDLE;
    private long memory = VK_NULL_HANDLE;
    private long mapped;
    private boolean deleted;

    /** Creates the pool with the default region size. */
    public RecordsRing(VulkanContext context) {
        this(context, DEFAULT_REGION_BYTES);
    }

    /**
     * Creates the pool: one storage buffer of {@code regionBytes * slots}, one allocation,
     * persistently mapped, with slot {@code s} owning the region that starts at
     * {@code s * regionBytes}. Region bases are MiB multiples, so an offset aligned inside its
     * region is aligned in the buffer - which is what makes region-relative dynamic offsets
     * legal for the store's {@code STORAGE_BUFFER_DYNAMIC} binding.
     */
    public RecordsRing(VulkanContext context, long regionBytes) {
        if (context == null) {
            throw new IllegalArgumentException("context must not be null");
        }
        if (regionBytes <= 0L) {
            throw new IllegalArgumentException("regionBytes must be positive, was " + regionBytes);
        }
        this.context = context;
        this.regionBytes = regionBytes;
        int slots = FrameSubmissionScheduler.FRAME_SLOTS;
        this.allocators = new LinearRingAllocator[slots];
        for (int slot = 0; slot < slots; slot++) {
            this.allocators[slot] = new LinearRingAllocator(regionBytes);
        }
        long size = regionBytes * slots;
        long createdBuffer = VK_NULL_HANDLE;
        long createdMemory = VK_NULL_HANDLE;
        long mappedAddress = 0L;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkBufferCreateInfo createInfo = VkBufferCreateInfo.calloc(stack).sType$Default()
                    .size(size)
                    .usage(VK_BUFFER_USAGE_STORAGE_BUFFER_BIT)
                    .sharingMode(VK_SHARING_MODE_EXCLUSIVE);
            LongBuffer handle = stack.mallocLong(1);
            check(vkCreateBuffer(this.context.device(), createInfo, null, handle),
                    "vkCreateBuffer(records ring, " + size + " bytes)");
            createdBuffer = handle.get(0);

            VkMemoryRequirements requirements = VkMemoryRequirements.calloc(stack);
            vkGetBufferMemoryRequirements(this.context.device(), createdBuffer, requirements);
            int memoryType = this.context.memoryTypeIndex(requirements.memoryTypeBits(),
                    VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
                    "the records ring's host-visible buffer of " + size + " bytes");
            VkMemoryAllocateInfo allocate = VkMemoryAllocateInfo.calloc(stack).sType$Default()
                    .allocationSize(requirements.size())
                    .memoryTypeIndex(memoryType);
            LongBuffer memoryHandle = stack.mallocLong(1);
            check(VulkanContext.allocateMemory(this.context.device(), allocate, memoryHandle),
                    "vkAllocateMemory(records ring, " + requirements.size() + " bytes)");
            createdMemory = memoryHandle.get(0);
            check(vkBindBufferMemory(this.context.device(), createdBuffer, createdMemory, 0L),
                    "vkBindBufferMemory(records ring)");

            PointerBuffer pointer = stack.mallocPointer(1);
            check(vkMapMemory(this.context.device(), createdMemory, 0L, size, 0, pointer),
                    "vkMapMemory(records ring)");
            mappedAddress = pointer.get(0);
        } catch (RuntimeException | LinkageError failure) {
            // Created in order - buffer, memory, bind, map - so a failure part way through leaves
            // the earlier ones alive; release whatever exists rather than leaking a partial pool.
            if (mappedAddress != 0L) {
                vkUnmapMemory(this.context.device(), createdMemory);
            }
            if (createdBuffer != VK_NULL_HANDLE) {
                vkDestroyBuffer(this.context.device(), createdBuffer, null);
            }
            if (createdMemory != VK_NULL_HANDLE) {
                VulkanContext.freeMemory(this.context.device(), createdMemory);
            }
            throw failure;
        }
        this.buffer = createdBuffer;
        this.memory = createdMemory;
        this.mapped = mappedAddress;
    }

    /** {@return how many slot regions the pool holds} - the frame-slot count it was built against. */
    public int slots() {
        return this.allocators.length;
    }

    /** {@return the fixed capacity of one slot's region in bytes}. */
    public long slotCapacity(int slot) {
        requireSlot(slot);
        return this.regionBytes;
    }

    /**
     * Hands out a slice of {@code slot}'s region.
     *
     * @return the slice's offset from the pool buffer's base (region base included), or {@code -1}
     *         when the slice does not fit in what remains of the region - the caller then falls
     *         back, because the pool never grows and never waits
     */
    public long alloc(int slot, long size, long alignment) {
        requireSlot(slot);
        if (this.deleted) {
            return -1L;
        }
        long local = this.allocators[slot].alloc(size, alignment);
        return local < 0L ? -1L : slot * this.regionBytes + local;
    }

    /** {@return the mapped host address of the buffer base plus {@code offset}}. */
    public long mappedAddress(int slot, long offset) {
        requireSlot(slot);
        if (this.mapped == 0L) {
            throw new IllegalStateException("records ring is deleted");
        }
        return this.mapped + offset;
    }

    /** {@return the pool's one {@code VkBuffer} handle - the same for every slot}. */
    public long buffer() {
        return this.buffer;
    }

    /** {@return the bytes handed out of {@code slot}'s region since its last rewind}. */
    public long used(int slot) {
        requireSlot(slot);
        return this.allocators[slot].used();
    }

    /**
     * Rewinds {@code slot}'s region to empty. Only the records store's frame boundary may call
     * this: the seat where the slot's previous submission is known complete (see the class
     * comment). This pool takes no holds, so the rewind always applies.
     */
    public void beginSlot(int slot) {
        requireSlot(slot);
        this.allocators[slot].reset();
    }

    /** Deletes the buffer and its memory. Idempotent. */
    public void delete() {
        if (this.deleted) {
            return;
        }
        this.deleted = true;
        if (this.mapped != 0L) {
            vkUnmapMemory(this.context.device(), this.memory);
            this.mapped = 0L;
        }
        if (this.buffer != VK_NULL_HANDLE) {
            vkDestroyBuffer(this.context.device(), this.buffer, null);
            this.buffer = VK_NULL_HANDLE;
        }
        if (this.memory != VK_NULL_HANDLE) {
            VulkanContext.freeMemory(this.context.device(), this.memory);
            this.memory = VK_NULL_HANDLE;
        }
    }

    private void requireSlot(int slot) {
        if (slot < 0 || slot >= this.allocators.length) {
            throw new IllegalArgumentException("records ring slot " + slot + " is outside 0.."
                    + (this.allocators.length - 1));
        }
    }

    private static void check(int result, String what) {
        if (result != VK_SUCCESS) {
            throw new IllegalStateException(what + " failed with VkResult " + result);
        }
    }
}
