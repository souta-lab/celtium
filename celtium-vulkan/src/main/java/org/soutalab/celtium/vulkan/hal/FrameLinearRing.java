package org.soutalab.celtium.vulkan.hal;

import org.soutalab.celtium.vulkan.FrameSubmissionScheduler;
import org.soutalab.celtium.vulkan.VulkanContext;
import org.soutalab.celtium.vulkan.VulkanHostBuffer;

import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_INDEX_BUFFER_BIT;
import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_TRANSFER_SRC_BIT;
import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT;
import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_VERTEX_BUFFER_BIT;

/**
 * The Vulkan half of the Stage 2 per-frame linear ring: one persistently mapped host buffer per frame slot,
 * carved by a {@link LinearRingAllocator} (design CELTIUM-A-TO-B-PLAN-2026-11-25, Stage 2).
 * <p>
 * The ring exists to give every frame-lifetime temporary - the transfers a frame stages and then discards, the
 * frame's uniform bytes, texture-update staging - a home that is <em>not</em> the persistent-resource allocator.
 * The tick383 out-of-memory was structural: per-frame temporary transfers were allocated out of the same fixed
 * slab as persistent geometry, so a long frame chewed the slab into fragments until a 32 KiB request found no
 * run and the game died mid-flight. A temporary that lives in this ring can never do that: the ring is bump-only
 * (no fragmentation can form), it is reset wholesale when its slot comes round again, and exhausting it is a
 * {@code -1} the caller falls back from, never a growth, a wait or a slab allocation.
 * <p>
 * The reset gate is the class's one safety rule, and it is not this class's to waive: {@link #reset(int)} may
 * only be called once the submission that read the slot's slices has been confirmed complete. The backend's
 * frame-slot wait is that confirmation - it returns only when the slot's own submission is done - and it is the
 * wait, not this class, that calls the reset (through {@code VulkanContext.celtium$resetFrameLinearSlot}). A
 * slot whose wait has not confirmed is never reset, so the CPU never overwrites bytes the GPU still reads.
 * <p>
 * Slot count follows {@link FrameSubmissionScheduler#FRAME_SLOTS}: ring slot {@code s} serves the frame recorded
 * into frame slot {@code s}, and comes round again exactly when that frame slot does.
 */
public final class FrameLinearRing {
    /** Default bytes per slot, from {@code celtium.vulkan.frameRingSlotMiB} (clamped to 16..32, default 32). */
    public static final long DEFAULT_SLOT_BYTES =
            (long) clampSlotMiB(Integer.getInteger("celtium.vulkan.frameRingSlotMiB", 32).intValue()) << 20;

    private static int clampSlotMiB(int requested) {
        return Math.max(16, Math.min(32, requested));
    }

    private final VulkanHostBuffer[] buffers;
    private final LinearRingAllocator[] allocators;
    private boolean deleted;

    /** Creates one ring slot per frame slot, each {@link #DEFAULT_SLOT_BYTES} bytes. */
    public FrameLinearRing(VulkanContext context) {
        this(context, DEFAULT_SLOT_BYTES);
    }

    /**
     * Creates one ring slot per frame slot, each {@code slotBytes} bytes, as persistently mapped host buffers
     * usable as a transfer source and as vertex, index and uniform data.
     */
    public FrameLinearRing(VulkanContext context, long slotBytes) {
        if (context == null) {
            throw new IllegalArgumentException("context must not be null");
        }
        int slots = FrameSubmissionScheduler.FRAME_SLOTS;
        this.buffers = new VulkanHostBuffer[slots];
        this.allocators = new LinearRingAllocator[slots];
        int usage = VK_BUFFER_USAGE_TRANSFER_SRC_BIT | VK_BUFFER_USAGE_VERTEX_BUFFER_BIT
                | VK_BUFFER_USAGE_INDEX_BUFFER_BIT | VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT;
        try {
            for (int slot = 0; slot < slots; slot++) {
                this.buffers[slot] = new VulkanHostBuffer(context, slotBytes, usage);
                this.allocators[slot] = new LinearRingAllocator(this.buffers[slot].size());
            }
        } catch (RuntimeException | LinkageError failure) {
            // A half-built ring must not leak the slots it did create: the context that asked for it is
            // typically being torn down by the same failure.
            for (int slot = 0; slot < slots; slot++) {
                if (this.buffers[slot] != null) {
                    this.buffers[slot].delete();
                    this.buffers[slot] = null;
                }
            }
            throw failure;
        }
    }

    /** {@return how many slots the ring holds} - the frame-slot count it was built against. */
    public int slots() {
        return this.buffers.length;
    }

    /** {@return the fixed capacity of one slot in bytes}. */
    public long slotCapacity(int slot) {
        requireSlot(slot);
        return this.allocators[slot].capacity();
    }

    /**
     * Hands out a slice of {@code slot}'s buffer.
     *
     * @return the slice's offset from the slot buffer's base, or {@code -1} when the slice does not fit in what
     *         remains of the slot - the caller then falls back, because the ring never grows and never waits
     */
    public long alloc(int slot, long size, long alignment) {
        requireSlot(slot);
        if (this.deleted) {
            return -1L;
        }
        return this.allocators[slot].alloc(size, alignment);
    }

    /** {@return the {@code VkBuffer} handle backing {@code slot}}, or {@code VK_NULL_HANDLE} after delete. */
    public long buffer(int slot) {
        requireSlot(slot);
        return this.buffers[slot] == null ? 0L : this.buffers[slot].buffer();
    }

    /** {@return the mapped host address of {@code slot}'s base plus {@code offset}}. */
    public long mappedAddress(int slot, long offset) {
        requireSlot(slot);
        if (this.buffers[slot] == null) {
            throw new IllegalStateException("frame linear ring slot " + slot + " is deleted");
        }
        return this.buffers[slot].mappedAddress() + offset;
    }

    /** {@return the bytes handed out of {@code slot} since its last reset}, including alignment padding. */
    public long used(int slot) {
        requireSlot(slot);
        return this.allocators[slot].used();
    }

    /** {@return the most bytes ever handed out of {@code slot} between two resets}. */
    public long highWater(int slot) {
        requireSlot(slot);
        return this.allocators[slot].highWater();
    }

    /**
     * Returns {@code slot}'s cursor to zero. Only the frame-slot completion gate may call this - see the class
     * comment; calling it while the GPU may still read the slot's slices is a lifetime violation, not a shortcut.
     */
    public void reset(int slot) {
        requireSlot(slot);
        this.allocators[slot].reset();
    }

    /** Deletes every slot buffer. Idempotent. The owning context calls this before destroying its device. */
    public void delete() {
        if (this.deleted) {
            return;
        }
        this.deleted = true;
        for (int slot = 0; slot < this.buffers.length; slot++) {
            if (this.buffers[slot] != null) {
                this.buffers[slot].delete();
                this.buffers[slot] = null;
            }
        }
    }

    private void requireSlot(int slot) {
        if (slot < 0 || slot >= this.buffers.length) {
            throw new IllegalArgumentException("frame linear ring slot " + slot + " is outside 0.."
                    + (this.buffers.length - 1));
        }
    }
}
