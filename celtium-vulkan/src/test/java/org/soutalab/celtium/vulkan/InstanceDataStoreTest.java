package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InstanceDataStoreTest {

    /**
     * An in-memory ring: per-slot byte arrays with a bump cursor that only moves forward, like
     * the frame-linear ring inside one frame. Slices are never handed back, so exhaustion is
     * observable exactly as production sees it.
     */
    private static final class TestRing implements InstanceDataStore.RingSource {
        private final ByteBuffer[] memory;
        private final long[] cursors;
        private final long capacity;

        TestRing(int slots, long capacityBytes) {
            this.memory = new ByteBuffer[slots];
            this.cursors = new long[slots];
            this.capacity = capacityBytes;
            for (int slot = 0; slot < slots; slot++) {
                this.memory[slot] = MemoryUtil.memAlloc((int) capacityBytes);
            }
        }

        @Override
        public int slots() {
            return this.memory.length;
        }

        @Override
        public long slotCapacity(int slot) {
            return this.capacity;
        }

        @Override
        public long alloc(int slot, long size, long alignment) {
            long mask = alignment - 1L;
            long offset = (this.cursors[slot] + mask) & ~mask;
            if (offset + size > this.capacity) {
                return -1L;
            }
            this.cursors[slot] = offset + size;
            return offset;
        }

        @Override
        public long mappedAddress(int slot, long offset) {
            return MemoryUtil.memAddress(this.memory[slot]) + offset;
        }

        @Override
        public long buffer(int slot) {
            return 0L;
        }

        /** Simulates bytes earlier ring tenants already took in the slot. */
        void consume(int slot, long bytes) {
            this.cursors[slot] += bytes;
        }

        int byteAt(int slot, long offset) {
            return MemoryUtil.memGetByte(MemoryUtil.memAddress(this.memory[slot]) + offset) & 0xFF;
        }

        void delete() {
            for (ByteBuffer buffer : this.memory) {
                MemoryUtil.memFree(buffer);
            }
        }
    }

    /** A direct push block of the given size, filled with a marker byte. */
    private static ByteBuffer pushBlock(int bytes, int marker) {
        ByteBuffer block = ByteBuffer.allocateDirect(bytes);
        for (int i = 0; i < bytes; i++) {
            block.put(i, (byte) marker);
        }
        return block;
    }

    private static InstanceDataStore store(TestRing ring) {
        return new InstanceDataStore(ring, 1L);
    }

    @Test
    void recordsLandInTheSelectedSlotOnly() {
        TestRing ring = new TestRing(2, 1L << 20);
        InstanceDataStore store = store(ring);
        store.selectSlot(0);
        assertEquals(0, store.record(pushBlock(256, 0x11), -1));
        store.selectSlot(1);
        assertEquals(0, store.record(pushBlock(256, 0x22), -1));
        assertEquals(0x11, ring.byteAt(0, 0));
        assertEquals(0x22, ring.byteAt(1, 0));
        // Writing slot 1's second record leaves slot 0's bytes untouched.
        assertEquals(1, store.record(pushBlock(256, 0x33), -1));
        assertEquals(0x11, ring.byteAt(0, 0));
        assertEquals(1, store.count(0));
        assertEquals(2, store.count(1));
        store.delete();
        ring.delete();
    }

    @Test
    void selectingASlotRewindsItsCursor() {
        TestRing ring = new TestRing(2, 1L << 20);
        InstanceDataStore store = store(ring);
        store.selectSlot(0);
        store.record(pushBlock(256, 1), -1);
        store.record(pushBlock(256, 2), -1);
        assertEquals(2, store.count());
        store.selectSlot(1);
        store.selectSlot(0);
        assertEquals(0, store.count());
        assertEquals(0, store.record(pushBlock(256, 3), -1));
        store.delete();
        ring.delete();
    }

    @Test
    void recordLayoutMatchesTheImmediateDrawRecordsStride() {
        TestRing ring = new TestRing(2, 1L << 20);
        InstanceDataStore store = store(ring);
        store.selectSlot(0);
        // A 208-byte textured-family block: the rest of the 256-byte record is zeroed, and the
        // bindless array index lands as float bits at offset 216, as the record shaders expect.
        store.record(pushBlock(208, 0x7F), 7);
        long base = store.descriptorDynamicOffset();
        assertEquals(0x7F, ring.byteAt(0, base + 207));
        assertEquals(0, ring.byteAt(0, base + 208));
        assertEquals(7, Float.floatToRawIntBits(MemoryUtil.memGetFloat(ring.mappedAddress(0, base + 216))));
        assertEquals(0, ring.byteAt(0, base + 255));
        store.delete();
        ring.delete();
    }

    @Test
    void shortBlocksAreRefused() {
        TestRing ring = new TestRing(2, 1L << 20);
        InstanceDataStore store = store(ring);
        store.selectSlot(0);
        assertEquals(-1, store.record(pushBlock(100, 1), -1));
        assertEquals(-1, store.record(null, -1));
        assertEquals(0, store.count());
        store.delete();
        ring.delete();
    }

    @Test
    void growthPreservesRecordsAndIsPerSlot() {
        TestRing ring = new TestRing(2, 1L << 20);
        InstanceDataStore store = store(ring);
        store.selectSlot(0);
        // 256 records fill the initial slice; the 257th grows into a larger slice in the same
        // slot, carrying the records written so far across.
        for (int i = 0; i < 256; i++) {
            assertEquals(i, store.record(pushBlock(256, i & 0xFF), -1));
        }
        assertEquals(256, store.record(pushBlock(256, 0xAB), -1));
        long base = store.descriptorDynamicOffset();
        assertEquals(1, ring.byteAt(0, base + InstanceDataStore.RECORD_BYTES));
        assertEquals(0xAB, ring.byteAt(0, base + 256L * InstanceDataStore.RECORD_BYTES));
        // Slot 1 was never touched by slot 0's growth.
        store.selectSlot(1);
        assertEquals(0, store.count());
        assertEquals(0, store.record(pushBlock(256, 0xCD), -1));
        store.delete();
        ring.delete();
    }

    @Test
    void ringExhaustionRefusesRecordsWithoutLosingWrittenOnes() {
        // A ring too small for even the initial slice: the selection has no slice at all and
        // every record is refused, which is the caller's cue to keep the per-group path.
        TestRing small = new TestRing(2, 32L << 10);
        InstanceDataStore starved = store(small);
        starved.selectSlot(0);
        assertEquals(-1, starved.record(pushBlock(256, 1), -1));
        assertEquals(0, starved.count());
        starved.delete();
        small.delete();

        // A ring that fits the initial slice but not its growth: the 257th record is refused
        // and the 256 already written stay put.
        TestRing tight = new TestRing(2, 96L << 10);
        InstanceDataStore store = store(tight);
        store.selectSlot(0);
        for (int i = 0; i < 256; i++) {
            assertEquals(i, store.record(pushBlock(256, i & 0xFF), -1));
        }
        assertEquals(-1, store.record(pushBlock(256, 0xAB), -1));
        assertEquals(256, store.count());
        long base = store.descriptorDynamicOffset();
        assertEquals(1, tight.byteAt(0, base + InstanceDataStore.RECORD_BYTES));
        store.delete();
        tight.delete();
    }

    @Test
    void sliceOffsetsHonorTheAlignment() {
        TestRing ring = new TestRing(2, 1L << 20);
        InstanceDataStore store = new InstanceDataStore(ring, 64L);
        // Earlier tenants leave the cursor unaligned; slices must still start aligned, because
        // the offset becomes a dynamic descriptor offset at bind time.
        ring.consume(0, 8L);
        store.selectSlot(0);
        long first = store.descriptorDynamicOffset();
        assertEquals(64L, first);
        // A second window in the same frame takes the next slice, also aligned.
        store.selectSlot(0);
        long second = store.descriptorDynamicOffset();
        assertTrue(second > first);
        assertEquals(0L, second % 64L);
        store.delete();
        ring.delete();
    }
}
