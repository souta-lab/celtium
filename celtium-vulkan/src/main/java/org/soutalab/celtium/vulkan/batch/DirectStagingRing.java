package org.soutalab.celtium.vulkan.batch;

import java.nio.ByteBuffer;

/**
 * {@link StagingRing} over one direct {@link ByteBuffer}, split into equal slots. The headless
 * implementation used by tests and tooling; the production ring is the HAL's host-visible Vulkan
 * memory behind the same interface. Writes are plain memory copies, mirroring the zero-allocation
 * discipline of the capture path: no per-write objects.
 */
public final class DirectStagingRing implements StagingRing {

    private final ByteBuffer buffer;
    private final int slotCount;
    private final long slotCapacity;
    private int currentSlot;
    private long cursor;

    public DirectStagingRing(int slotCount, long slotCapacityBytes) {
        if (slotCount < 1) {
            throw new IllegalArgumentException("slotCount " + slotCount);
        }
        if (slotCapacityBytes < 1 || slotCapacityBytes > Integer.MAX_VALUE / slotCount) {
            throw new IllegalArgumentException("slotCapacityBytes " + slotCapacityBytes);
        }
        this.slotCount = slotCount;
        this.slotCapacity = slotCapacityBytes;
        this.buffer = ByteBuffer.allocateDirect((int) (slotCount * slotCapacityBytes));
    }

    @Override
    public int slotCount() {
        return this.slotCount;
    }

    @Override
    public long slotCapacity() {
        return this.slotCapacity;
    }

    @Override
    public int currentSlot() {
        return this.currentSlot;
    }

    @Override
    public long allocate(int size, int alignment) {
        if (size < 0) {
            throw new IllegalArgumentException("size " + size);
        }
        long aligned = alignUp(this.cursor, Math.max(1, alignment));
        if (aligned + size > this.slotCapacity) {
            return -1;
        }
        this.cursor = aligned + size;
        return this.currentSlot * this.slotCapacity + aligned;
    }

    @Override
    public void write(long offset, ByteBuffer source) {
        ByteBuffer view = this.buffer.duplicate();
        view.position((int) offset);
        view.put(source.duplicate());
    }

    @Override
    public void advanceFrame() {
        this.currentSlot = (this.currentSlot + 1) % this.slotCount;
        this.cursor = 0;
    }

    /** Bytes reserved so far in the current slot (diagnostics and tests). */
    public long usedInCurrentSlot() {
        return this.cursor;
    }

    /** The backing buffer (tests read back what captures wrote). */
    public ByteBuffer buffer() {
        return this.buffer;
    }

    private static long alignUp(long value, int alignment) {
        // Division form, not a bit mask: vertex strides like 24 are not powers of two, and the
        // batcher's vertex-offset arithmetic needs exact multiples of the stride.
        return (value + alignment - 1) / alignment * alignment;
    }
}
