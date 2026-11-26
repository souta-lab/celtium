package org.soutalab.celtium.vulkan.hal;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkBufferCreateInfo;
import org.lwjgl.vulkan.VkMemoryAllocateInfo;
import org.lwjgl.vulkan.VkMemoryRequirements;
import org.soutalab.celtium.vulkan.FrameSubmissionScheduler;
import org.soutalab.celtium.vulkan.VulkanContext;

import java.nio.LongBuffer;

import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_INDEX_BUFFER_BIT;
import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_INDIRECT_BUFFER_BIT;
import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_STORAGE_BUFFER_BIT;
import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_TRANSFER_SRC_BIT;
import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT;
import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_VERTEX_BUFFER_BIT;
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
 * <b>The backing is dedicated, deliberately not {@code VulkanHostBuffer}.</b> Each slot is one buffer and one
 * {@code vkAllocateMemory} made by this class alone (the same shape as the context's upload ring), counted by
 * {@link VulkanContext#allocateMemory} as ordinary live allocations and by nothing else. That separation is a
 * Stage 3 precondition, not a style choice: Stage 3 moves {@code VulkanHostBuffer} onto the fixed slab pools,
 * and a ring backed by it would (a) occupy one to two 64 MiB slabs for its own slots and (b) hand the slab a
 * tenant whose whole point is to stay out of it. Owning the allocation here keeps the ring outside the slab
 * census no matter what the host-buffer class becomes.
 * <p>
 * The reset gate is the class's one safety rule, and it is not this class's to waive: {@link #reset(int)} may
 * only be called once the submission that read the slot's slices has been confirmed complete. The backend's
 * frame-slot wait is that confirmation - it returns only when the slot's own submission is done - and it is the
 * wait, not this class, that calls the reset (through {@code VulkanContext.celtium$resetFrameLinearSlot}). A
 * slot whose wait has not confirmed is never reset, so the CPU never overwrites bytes the GPU still reads.
 * A consumer that may outlive its frame (an immediate group held for another render target) additionally
 * {@link #hold(int)}s the slot; a confirmed reset that arrives under a hold is deferred by the allocator and
 * applied when the last hold is released, so held slices are never overwritten either.
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

    private final VulkanContext context;
    private final long[] buffers;
    private final long[] memories;
    private final long[] mapped;
    private final LinearRingAllocator[] allocators;
    private boolean deleted;

    /** Creates one ring slot per frame slot, each {@link #DEFAULT_SLOT_BYTES} bytes. */
    public FrameLinearRing(VulkanContext context) {
        this(context, DEFAULT_SLOT_BYTES);
    }

    /**
     * Creates one ring slot per frame slot, each {@code slotBytes} bytes, as a persistently mapped host buffer
     * usable as a transfer source and as vertex, index, uniform and storage data, and as the indirect-command
     * buffer a frame's draw arguments are read from. The storage and indirect usages are what let the Stage 5
     * batch path keep its instance-data SSBO slices and its {@code VkDrawIndexedIndirectCommand} runs inside
     * these same two slot buffers instead of spending more of the Stage 3 buffer budget. Every slot is a
     * dedicated allocation owned by this class - see the class comment for why it is not a
     * {@code VulkanHostBuffer}.
     */
    public FrameLinearRing(VulkanContext context, long slotBytes) {
        if (context == null) {
            throw new IllegalArgumentException("context must not be null");
        }
        if (slotBytes <= 0L) {
            throw new IllegalArgumentException("slotBytes must be positive, was " + slotBytes);
        }
        this.context = context;
        int slots = FrameSubmissionScheduler.FRAME_SLOTS;
        this.buffers = new long[slots];
        this.memories = new long[slots];
        this.mapped = new long[slots];
        this.allocators = new LinearRingAllocator[slots];
        int usage = VK_BUFFER_USAGE_TRANSFER_SRC_BIT | VK_BUFFER_USAGE_VERTEX_BUFFER_BIT
                | VK_BUFFER_USAGE_INDEX_BUFFER_BIT | VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT
                | VK_BUFFER_USAGE_STORAGE_BUFFER_BIT | VK_BUFFER_USAGE_INDIRECT_BUFFER_BIT;
        long size = alignUp4(slotBytes);
        try {
            for (int slot = 0; slot < slots; slot++) {
                createSlot(slot, size, usage);
                this.allocators[slot] = new LinearRingAllocator(size);
            }
        } catch (RuntimeException | LinkageError failure) {
            // A half-built ring must not leak the slots it did create: the context that asked for it is
            // typically being torn down by the same failure.
            this.deleted = true;
            for (int slot = 0; slot < slots; slot++) {
                destroySlot(slot);
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
        return this.buffers[slot];
    }

    /** {@return the mapped host address of {@code slot}'s base plus {@code offset}}. */
    public long mappedAddress(int slot, long offset) {
        requireSlot(slot);
        if (this.mapped[slot] == 0L) {
            throw new IllegalStateException("frame linear ring slot " + slot + " is deleted");
        }
        return this.mapped[slot] + offset;
    }

    /** {@return the bytes handed out of {@code slot} since its last applied reset}, including alignment padding. */
    public long used(int slot) {
        requireSlot(slot);
        return this.allocators[slot].used();
    }

    /** {@return the most bytes ever handed out of {@code slot} between two applied resets}. */
    public long highWater(int slot) {
        requireSlot(slot);
        return this.allocators[slot].highWater();
    }

    /**
     * Records one more consumer holding slices of {@code slot} (see {@link LinearRingAllocator#hold()}): while a
     * hold is outstanding, a confirmed {@link #reset(int)} is deferred rather than applied.
     */
    public void hold(int slot) {
        requireSlot(slot);
        this.allocators[slot].hold();
    }

    /** Gives back one {@link #hold(int)}; the last release applies a reset the holds had deferred. */
    public void release(int slot) {
        requireSlot(slot);
        this.allocators[slot].release();
    }

    /** {@return how many consumers currently hold slices of {@code slot}}. */
    public int holds(int slot) {
        requireSlot(slot);
        return this.allocators[slot].holds();
    }

    /** {@return whether {@code slot}'s confirmed reset is waiting for its last hold to be released}. */
    public boolean resetDeferred(int slot) {
        requireSlot(slot);
        return this.allocators[slot].resetDeferred();
    }

    /**
     * Returns {@code slot}'s cursor to zero, unless consumers still hold its slices - then the reset is deferred
     * to the last release (see the class comment). Only the frame-slot completion gate may call this.
     */
    public void reset(int slot) {
        requireSlot(slot);
        this.allocators[slot].reset();
    }

    /**
     * Deletes every slot buffer. Idempotent. The owning context calls this from its own teardown, after the
     * device-wide wait that makes "no submission still reads the ring" a fact (the same contract as the upload
     * ring's destroy) - the frees here are immediate, never deferred, because by then nothing can name them.
     */
    public void delete() {
        if (this.deleted) {
            return;
        }
        this.deleted = true;
        for (int slot = 0; slot < this.buffers.length; slot++) {
            destroySlot(slot);
        }
    }

    private void createSlot(int slot, long size, int usage) {
        long buffer = VK_NULL_HANDLE;
        long memory = VK_NULL_HANDLE;
        long mappedAddress = 0L;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkBufferCreateInfo createInfo = VkBufferCreateInfo.calloc(stack).sType$Default()
                    .size(size)
                    .usage(usage)
                    .sharingMode(VK_SHARING_MODE_EXCLUSIVE);
            LongBuffer handle = stack.mallocLong(1);
            check(vkCreateBuffer(this.context.device(), createInfo, null, handle),
                    "vkCreateBuffer(frame linear ring slot, " + size + " bytes)");
            buffer = handle.get(0);

            VkMemoryRequirements requirements = VkMemoryRequirements.calloc(stack);
            vkGetBufferMemoryRequirements(this.context.device(), buffer, requirements);
            int memoryType = this.context.memoryTypeIndex(requirements.memoryTypeBits(),
                    VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
                    "the frame linear ring's host-visible slot buffer of " + size + " bytes");
            VkMemoryAllocateInfo allocate = VkMemoryAllocateInfo.calloc(stack).sType$Default()
                    .allocationSize(requirements.size())
                    .memoryTypeIndex(memoryType);
            LongBuffer memoryHandle = stack.mallocLong(1);
            check(VulkanContext.allocateMemory(this.context.device(), allocate, memoryHandle),
                    "vkAllocateMemory(frame linear ring slot, " + requirements.size() + " bytes)");
            memory = memoryHandle.get(0);
            check(vkBindBufferMemory(this.context.device(), buffer, memory, 0L),
                    "vkBindBufferMemory(frame linear ring slot)");

            PointerBuffer pointer = stack.mallocPointer(1);
            check(vkMapMemory(this.context.device(), memory, 0L, size, 0, pointer),
                    "vkMapMemory(frame linear ring slot)");
            mappedAddress = pointer.get(0);
        } catch (RuntimeException | LinkageError failure) {
            // The allocations happen in order - buffer, memory, bind, map - so a failure part way through leaves
            // the earlier ones alive; release whatever exists here rather than leaking a partial slot.
            if (mappedAddress != 0L) {
                vkUnmapMemory(this.context.device(), memory);
            }
            if (buffer != VK_NULL_HANDLE) {
                vkDestroyBuffer(this.context.device(), buffer, null);
            }
            if (memory != VK_NULL_HANDLE) {
                VulkanContext.freeMemory(this.context.device(), memory);
            }
            throw failure;
        }
        this.buffers[slot] = buffer;
        this.memories[slot] = memory;
        this.mapped[slot] = mappedAddress;
    }

    private void destroySlot(int slot) {
        if (this.mapped[slot] != 0L) {
            vkUnmapMemory(this.context.device(), this.memories[slot]);
            this.mapped[slot] = 0L;
        }
        if (this.buffers[slot] != VK_NULL_HANDLE) {
            vkDestroyBuffer(this.context.device(), this.buffers[slot], null);
            this.buffers[slot] = VK_NULL_HANDLE;
        }
        if (this.memories[slot] != VK_NULL_HANDLE) {
            VulkanContext.freeMemory(this.context.device(), this.memories[slot]);
            this.memories[slot] = VK_NULL_HANDLE;
        }
    }

    private void requireSlot(int slot) {
        if (slot < 0 || slot >= this.buffers.length) {
            throw new IllegalArgumentException("frame linear ring slot " + slot + " is outside 0.."
                    + (this.buffers.length - 1));
        }
    }

    /** Rounds up to a multiple of 4, the buffer-size rule {@code VulkanHostBuffer} also follows. */
    private static long alignUp4(long value) {
        return (value + 3L) & ~3L;
    }

    private static void check(int result, String what) {
        if (result != VK_SUCCESS) {
            throw new IllegalStateException(what + " failed with VkResult " + result);
        }
    }
}
