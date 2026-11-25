package org.soutalab.celtium.vulkan.hal;

/**
 * The offset policy of one per-frame linear ring slot: a bump cursor over a fixed capacity, nothing else.
 * <p>
 * This is the pure half of the Stage 2 linear ring (design CELTIUM-A-TO-B-PLAN-2026-11-25, Stage 2). It owns no
 * Vulkan object and no memory; {@link FrameLinearRing} owns the buffers and asks this class where a slice goes.
 * Keeping the policy free of the device is what makes the ring's structural claims testable without a GPU:
 * <ul>
 *   <li><b>Bump only.</b> An allocation is the cursor, aligned up, plus the size. There is no free list, no
 *   splitting and no coalescing, so fragmentation - the tick383 failure shape, where a fixed slab filled with
 *   frame-lifetime transfers until its largest free run was 16 KiB - cannot form inside a slot.</li>
 *   <li><b>Exhaustion is a value, not an event.</b> When a slice does not fit, {@link #alloc(long, long)} returns
 *   {@code -1}. The allocator never grows, never waits and never wraps into bytes the slot's earlier slices still
 *   occupy; the caller falls back (the settled blocking path, or a per-resource buffer) exactly as it did before
 *   the ring existed.</li>
 *   <li><b>Reset is the only reuse.</b> {@link #reset()} returns the cursor to zero, and the ring's owner calls it
 *   only after the submission that read the slot's slices has been confirmed complete (the frame-slot wait's
 *   contract), so a reset can never hand the CPU bytes the GPU is still reading.</li>
 * </ul>
 * Not thread-safe: a slot is written by the one thread recording its frame.
 */
public final class LinearRingAllocator {
    private final long capacity;
    private long cursor;
    private long highWater;

    /**
     * @param capacityBytes the slot's fixed size; must be positive
     */
    public LinearRingAllocator(long capacityBytes) {
        if (capacityBytes <= 0L) {
            throw new IllegalArgumentException("ring capacity must be positive, was " + capacityBytes);
        }
        this.capacity = capacityBytes;
    }

    /** {@return the slot's fixed capacity in bytes}. */
    public long capacity() {
        return this.capacity;
    }

    /** {@return the bytes handed out since the last {@link #reset()}}, including alignment padding. */
    public long used() {
        return this.cursor;
    }

    /** {@return the most bytes ever handed out between two resets}, the sizing evidence for the slot capacity. */
    public long highWater() {
        return this.highWater;
    }

    /**
     * Hands out {@code size} bytes at the next offset satisfying {@code alignment}.
     *
     * @param size      bytes wanted; must be positive
     * @param alignment a power of two, at least 1
     * @return the slice's offset from the slot's base, or {@code -1} when the slice does not fit in what remains
     */
    public long alloc(long size, long alignment) {
        if (size <= 0L) {
            throw new IllegalArgumentException("ring allocation size must be positive, was " + size);
        }
        if (alignment < 1L || (alignment & (alignment - 1L)) != 0L) {
            throw new IllegalArgumentException("ring alignment must be a power of two, was " + alignment);
        }
        long offset = (this.cursor + (alignment - 1L)) & -alignment;
        if (offset < 0L || size > this.capacity - offset) {
            return -1L;
        }
        this.cursor = offset + size;
        if (this.cursor > this.highWater) {
            this.highWater = this.cursor;
        }
        return offset;
    }

    /** Returns the cursor to zero. The owner calls this only under the completion gate stated above. */
    public void reset() {
        this.cursor = 0L;
    }
}
