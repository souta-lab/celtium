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
 *   <li><b>Holds defer a reset, they never cancel one.</b> A consumer whose slices may outlive the frame that
 *   allocated them (an immediate group held for another render target is the standing example) takes a
 *   {@link #hold()} when its first slice is sealed and {@link #release()}s it when the group is deleted. A reset
 *   that arrives while holds are outstanding is remembered, not applied: the cursor keeps bumping (later slices
 *   land after the held ones, never over them), and the pending reset is applied by the release that drops the
 *   count to zero. Both gate conditions - the owner's completion confirmation and every consumer's release -
 *   are monotonic, so applying the reset late is exactly as safe as applying it on time, and a consumer that is
 *   never released degrades the slot to exhaustion-and-fallback rather than to corruption.</li>
 * </ul>
 * Not thread-safe: a slot is written by the one thread recording its frame.
 */
public final class LinearRingAllocator {
    private final long capacity;
    private long cursor;
    private long highWater;
    /** Consumers still holding slices of this slot; while positive, a reset is deferred, never applied. */
    private int holds;
    /** A reset was confirmed by the owner while {@link #holds} was positive and is waiting for the last release. */
    private boolean resetDeferred;

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

    /** {@return the bytes handed out since the last applied {@link #reset()}}, including alignment padding. */
    public long used() {
        return this.cursor;
    }

    /** {@return the most bytes ever handed out between two applied resets}, the sizing evidence for the capacity. */
    public long highWater() {
        return this.highWater;
    }

    /** {@return how many consumers currently hold slices of this slot}. */
    public int holds() {
        return this.holds;
    }

    /** {@return whether a confirmed reset is waiting for the last hold to be released}. */
    public boolean resetDeferred() {
        return this.resetDeferred;
    }

    /**
     * Hands out {@code size} bytes at the next offset satisfying {@code alignment}.
     * <p>
     * The alignment is applied by division, not by mask: any positive alignment is legal, including the ones
     * that are not powers of two. A vertex stride of 24 bytes is the standing case - a slice that starts at a
     * whole number of vertices lets the draw call carry the slice offset as a vertex offset, and 24 (like the
     * {@code lcm(16, stride)} the immediate mesh asks for) is not a power of two. This is the same division form
     * Stage 5's {@code DirectStagingRing} aligns by, so the two rings state one contract.
     *
     * @param size      bytes wanted; must be positive
     * @param alignment any positive alignment, a power of two or not
     * @return the slice's offset from the slot's base, or {@code -1} when the slice does not fit in what remains
     */
    public long alloc(long size, long alignment) {
        if (size <= 0L) {
            throw new IllegalArgumentException("ring allocation size must be positive, was " + size);
        }
        if (alignment < 1L) {
            throw new IllegalArgumentException("ring alignment must be positive, was " + alignment);
        }
        long offset = alignUp(this.cursor, alignment);
        if (offset < 0L || offset > this.capacity || size > this.capacity - offset) {
            return -1L;
        }
        this.cursor = offset + size;
        if (this.cursor > this.highWater) {
            this.highWater = this.cursor;
        }
        return offset;
    }

    /** Rounds {@code value} up to a multiple of {@code alignment}; any positive alignment, not only a power of two. */
    static long alignUp(long value, long alignment) {
        return (value + alignment - 1L) / alignment * alignment;
    }

    /**
     * Records one more consumer holding slices of this slot. While the count is positive a {@link #reset()} is
     * deferred instead of applied, so the held slices are never overwritten by a later frame's bump.
     */
    public void hold() {
        this.holds++;
    }

    /**
     * Gives back one {@link #hold()}. When the count reaches zero with a reset deferred, the reset is applied
     * here: the owner's completion confirmation already arrived (that is what deferred it), and this release is
     * the last consumer letting go, so both halves of the reuse gate now hold.
     *
     * @throws IllegalStateException when there is no hold to give back
     */
    public void release() {
        if (this.holds <= 0) {
            throw new IllegalStateException("ring hold released without a matching hold");
        }
        this.holds--;
        if (this.holds == 0 && this.resetDeferred) {
            applyReset();
        }
    }

    /**
     * Returns the cursor to zero - or, while consumers still hold slices, remembers that it must do so when the
     * last one releases. The owner calls this only under the completion gate stated above; the deferral exists
     * because that gate proves the slot's <em>submission</em> completed, while a held consumer (a group queued
     * for another target) is deleted on its own, later, completion-confirmed path.
     *
     * @return whether the cursor was zeroed by this call ({@code false} when the reset was deferred to a release)
     */
    public boolean reset() {
        if (this.holds > 0) {
            this.resetDeferred = true;
            return false;
        }
        applyReset();
        return true;
    }

    private void applyReset() {
        this.resetDeferred = false;
        this.cursor = 0L;
    }
}
