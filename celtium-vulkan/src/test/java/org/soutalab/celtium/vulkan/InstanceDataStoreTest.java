package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        /** Whether {@link #beginSlot} rewinds the cursor, modelling the dedicated pool. */
        private boolean rewindOnBegin;

        TestRing(int slots, long capacityBytes) {
            this.memory = new ByteBuffer[slots];
            this.cursors = new long[slots];
            this.capacity = capacityBytes;
            for (int slot = 0; slot < slots; slot++) {
                this.memory[slot] = MemoryUtil.memAlloc((int) capacityBytes);
            }
        }

        /** Models the dedicated pool: the frame boundary rewinds the slot's region. */
        TestRing rewindable() {
            this.rewindOnBegin = true;
            return this;
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

        @Override
        public String kind() {
            return "test";
        }

        @Override
        public long usedBytes(int slot) {
            return this.cursors[slot];
        }

        @Override
        public void beginSlot(int slot) {
            if (this.rewindOnBegin) {
                this.cursors[slot] = 0L;
            }
        }

        @Override
        public void delete() {
            // Idempotent, like the production sources: the store's delete reaches here too.
            for (int slot = 0; slot < this.memory.length; slot++) {
                if (this.memory[slot] != null) {
                    MemoryUtil.memFree(this.memory[slot]);
                    this.memory[slot] = null;
                }
            }
        }

        /** Simulates bytes earlier ring tenants already took in the slot. */
        void consume(int slot, long bytes) {
            this.cursors[slot] += bytes;
        }

        int byteAt(int slot, long offset) {
            return MemoryUtil.memGetByte(MemoryUtil.memAddress(this.memory[slot]) + offset) & 0xFF;
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
    void recordLayoutMatchesTheStoreStride() {
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
    void recordAddressNamesTheWrittenRecordInTheStoresOwnHome() {
        TestRing ring = new TestRing(2, 1L << 20);
        InstanceDataStore store = store(ring);
        // A non-zero slot with earlier tenants in it: the slice starts past the region base,
        // so an address computed from the region base alone - or in another ring's home -
        // misses the record. The batch sink's push read stands on this address.
        ring.consume(1, 4096L);
        store.selectSlot(1);
        assertEquals(0, store.record(pushBlock(256, 0x2A), -1));
        assertEquals(1, store.record(pushBlock(256, 0x35), 9));
        long first = store.recordAddress(0);
        long second = store.recordAddress(1);
        assertEquals(ring.mappedAddress(1, store.descriptorDynamicOffset()), first);
        assertEquals(first + InstanceDataStore.RECORD_BYTES, second);
        assertEquals(0x2A, MemoryUtil.memGetByte(first) & 0xFF);
        assertEquals(0x35, MemoryUtil.memGetByte(second) & 0xFF);
        assertEquals(9, Float.floatToRawIntBits(MemoryUtil.memGetFloat(second + 216)));
        store.delete();
        ring.delete();
    }

    @Test
    void recordAddressFollowsASliceGrowth() {
        TestRing ring = new TestRing(2, 1L << 20);
        InstanceDataStore store = store(ring);
        store.selectSlot(0);
        for (int i = 0; i < 256; i++) {
            store.record(pushBlock(256, i & 0xFF), -1);
        }
        long before = store.recordAddress(1);
        // The 257th record grows the slice; the address of an earlier record must move with
        // it, to the copy - the descriptor bind moves the same way.
        store.record(pushBlock(256, 0xAB), -1);
        long after = store.recordAddress(1);
        assertEquals(ring.mappedAddress(0, store.descriptorDynamicOffset()
                + InstanceDataStore.RECORD_BYTES), after);
        assertEquals(1, MemoryUtil.memGetByte(after) & 0xFF);
        assertTrue(before != after, "the growth moved the slice, so the address must move");
        store.delete();
        ring.delete();
    }

    @Test
    void recordAddressIsZeroWhenTheSelectionHasNoSlice() {
        TestRing small = new TestRing(2, 32L << 10);
        InstanceDataStore store = store(small);
        store.selectSlot(0);
        assertEquals(0L, store.recordAddress(0));
        store.delete();
        small.delete();
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
    void lightmapRewriteAfterBindIsDeferredToTheFrameBoundary() {
        TestRing ring = new TestRing(2, 1L << 20);
        InstanceDataStore store = store(ring);
        store.selectSlot(0);
        // Before the slot's set is bound into the recording, a lightmap write lands at once.
        store.bindLightmap(111L, 222L, 0);
        assertEquals(111L, store.boundLightmapView(0));
        assertEquals(0L, store.pendingLightmapView(0));
        // Once the set is bound, a changed lightmap must not be rewritten under the open
        // recording: the request is remembered and the set keeps the written value.
        store.noteSetBound();
        store.bindLightmap(333L, 444L, 0);
        assertEquals(111L, store.boundLightmapView(0));
        assertEquals(333L, store.pendingLightmapView(0));
        // The slot's next frame boundary applies the deferred write.
        store.beginFrame(0);
        assertEquals(333L, store.boundLightmapView(0));
        assertEquals(0L, store.pendingLightmapView(0));
        store.delete();
        ring.delete();
    }

    @Test
    void deferredLightmapWriteKeepsOnlyTheNewestRequest() {
        TestRing ring = new TestRing(2, 1L << 20);
        InstanceDataStore store = store(ring);
        store.selectSlot(0);
        store.bindLightmap(111L, 222L, 0);
        store.noteSetBound();
        store.bindLightmap(333L, 444L, 0);
        store.bindLightmap(555L, 666L, 0);
        assertEquals(555L, store.pendingLightmapView(0));
        store.beginFrame(0);
        assertEquals(555L, store.boundLightmapView(0));
        store.delete();
        ring.delete();
    }

    @Test
    void lightmapDeferralIsPerSlot() {
        TestRing ring = new TestRing(2, 1L << 20);
        InstanceDataStore store = store(ring);
        store.selectSlot(0);
        store.bindLightmap(111L, 222L, 0);
        store.noteSetBound();
        store.bindLightmap(333L, 444L, 0);
        assertEquals(333L, store.pendingLightmapView(0));
        // Slot 1's set was never bound, so its write is immediate even while slot 0 defers.
        store.selectSlot(1);
        store.bindLightmap(777L, 888L, 0);
        assertEquals(777L, store.boundLightmapView(1));
        assertEquals(0L, store.pendingLightmapView(1));
        // Slot 0's deferred write still waits for slot 0's own boundary.
        assertEquals(111L, store.boundLightmapView(0));
        store.beginFrame(0);
        assertEquals(333L, store.boundLightmapView(0));
        store.delete();
        ring.delete();
    }

    @Test
    void frameBoundaryWithoutADeferredWriteReopensImmediateWrites() {
        TestRing ring = new TestRing(2, 1L << 20);
        InstanceDataStore store = store(ring);
        store.selectSlot(0);
        store.bindLightmap(111L, 222L, 0);
        store.noteSetBound();
        // No change was requested while bound: the boundary changes nothing in the set, but
        // it clears the bound mark, so the new frame's writes are immediate again.
        store.beginFrame(0);
        assertEquals(111L, store.boundLightmapView(0));
        assertEquals(0L, store.pendingLightmapView(0));
        store.bindLightmap(333L, 444L, 0);
        assertEquals(333L, store.boundLightmapView(0));
        store.delete();
        ring.delete();
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

    @Test
    void poolExhaustionRefusesCountsAndRecoversAtTheFrameBoundary() {
        // The dedicated pool's shape (contract (a) at the store's level): a region exactly one
        // initial slice deep. The first window is served; a second window in the same frame
        // finds no room and is refused as a counted -1 - the caller's cue to draw directly, so
        // the drawing survives - and the frame boundary rewinds the region so the next frame is
        // served again. Exhaustion never throws and never corrupts what was written.
        TestRing ring = new TestRing(2, 64L << 10).rewindable();
        InstanceDataStore store = store(ring);
        store.selectSlot(0);
        assertEquals(0, store.record(pushBlock(256, 1), -1));
        assertEquals(1, store.record(pushBlock(256, 2), -1));
        store.selectSlot(0);
        assertEquals(1L, store.selectFailures(0));
        assertEquals(-1, store.record(pushBlock(256, 3), -1));
        assertEquals(1L, store.refusalsNoSlice());
        store.beginFrame(0);
        store.selectSlot(0);
        assertEquals(0, store.record(pushBlock(256, 4), -1));
        assertEquals(3L, store.recordsWritten());
        store.delete();
        ring.delete();
    }

    @Test
    void refusalsAreCountedByCause() {
        // Contract (c) at the store's level: a refused record names its cause in the counters,
        // so a leg can tell a missing slice from a slice that could not grow and from a bad
        // block instead of reading one undifferentiated drop count.
        TestRing ring = new TestRing(2, 96L << 10);
        InstanceDataStore store = store(ring);
        store.selectSlot(0);
        assertEquals(-1, store.record(pushBlock(100, 1), -1));
        assertEquals(-1, store.record(null, -1));
        assertEquals(2L, store.refusalsBlock());
        for (int i = 0; i < 256; i++) {
            assertEquals(i, store.record(pushBlock(256, i & 0xFF), -1));
        }
        // The region fits the initial slice but not its growth: the 257th record and every
        // later one in this selection are grow refusals (the failure latches per selection).
        assertEquals(-1, store.record(pushBlock(256, 0xAB), -1));
        assertEquals(-1, store.record(pushBlock(256, 0xCD), -1));
        assertEquals(2L, store.refusalsGrow());
        assertEquals(0L, store.refusalsNoSlice());
        assertEquals(256L, store.recordsWritten());
        store.delete();
        ring.delete();
    }

    @Test
    void fallbackReselectionServesARecordTheWindowSelectionRefused() {
        // A region too small for the window's initial slice (64 KiB) but with room for a
        // per-group handful: the window selection is refused, and with it every record -
        // the window-wide loss the audit measured, in which the direct path's groups fell
        // with the batch's. The fallback reselection carves the handful and the direct
        // path's record lands: the degradation the separation's landing record promised
        // (the store refuses, the caller keeps the direct path, the drawing survives).
        TestRing ring = new TestRing(2, 32L << 10);
        InstanceDataStore store = store(ring);
        store.selectSlot(0);
        assertEquals(1L, store.selectFailures(0));
        assertEquals(-1, store.record(pushBlock(256, 1), -1));
        assertEquals(1L, store.refusalsNoSlice());
        assertTrue(store.reselectFallback());
        assertEquals(0, store.record(pushBlock(256, 0x5A), -1));
        assertEquals(1, store.count());
        long base = store.descriptorDynamicOffset();
        assertEquals(0x5A, ring.byteAt(0, base));
        assertEquals(ring.mappedAddress(0, base), store.recordAddress(0));
        assertEquals(1L, store.recordsWritten());
        store.delete();
        ring.delete();
    }

    @Test
    void fallbackReselectionServesAfterAGrowthRefusal() {
        // The growth shape of the same defect: the initial slice fits and fills, its
        // growth does not, and every later record of the window is refused - including
        // the direct path's one record, which is written into the same selection. The
        // fallback reselection starts a fresh small slice past the full one and serves
        // it; the abandoned slice's records are the caller's already-flushed business,
        // not this seat's.
        TestRing ring = new TestRing(2, 96L << 10);
        InstanceDataStore store = store(ring);
        store.selectSlot(0);
        for (int i = 0; i < 256; i++) {
            assertEquals(i, store.record(pushBlock(256, i & 0xFF), -1));
        }
        assertEquals(-1, store.record(pushBlock(256, 0xAB), -1));
        assertEquals(1L, store.refusalsGrow());
        long full = store.descriptorDynamicOffset();
        assertTrue(store.reselectFallback());
        assertEquals(0, store.record(pushBlock(256, 0xCD), -1));
        assertEquals(1, store.count());
        long fresh = store.descriptorDynamicOffset();
        assertTrue(fresh > full, "the fallback slice is carved past the full one");
        assertEquals(0xCD, ring.byteAt(0, fresh));
        assertEquals(257L, store.recordsWritten());
        store.delete();
        ring.delete();
    }

    @Test
    void fallbackSelectionGrowsAndKeepsItsRecords() {
        // A fallback selection is a selection: when the handful fills, it grows by the
        // same doubling and carries its records across, so a window whose direct path
        // serves many groups is not re-carved per group. The growth doubles the slice
        // actually held (8 KiB from the 4 KiB handful), not the window's remembered
        // demand - and it does not shrink that demand either: what the next window
        // asks is the frame boundary's decay to decide, not this seat's.
        TestRing ring = new TestRing(2, 96L << 10);
        InstanceDataStore store = store(ring);
        ring.consume(0, 64L << 10);
        store.selectSlot(0);
        assertEquals(-1, store.record(pushBlock(256, 1), -1));
        assertTrue(store.reselectFallback());
        for (int i = 0; i < 17; i++) {
            assertEquals(i, store.record(pushBlock(256, i + 1), -1));
        }
        assertEquals(17, store.count());
        assertEquals(1, MemoryUtil.memGetByte(store.recordAddress(0)) & 0xFF);
        assertEquals(17, MemoryUtil.memGetByte(store.recordAddress(16)) & 0xFF);
        assertEquals(256L * InstanceDataStore.RECORD_BYTES, store.demandBytes(0));
        store.delete();
        ring.delete();
    }

    @Test
    void fallbackReselectionFailsWhenTheRegionIsTrulyFull() {
        // The honest remainder: a region the initial slice fills to the last byte has
        // no handful to carve either. The reselection reports false and records keep
        // refusing - the caller's counted drop, not a silent success or a thrown error.
        // The failed reselection voids the selection like any failed selection.
        TestRing ring = new TestRing(2, 64L << 10);
        InstanceDataStore store = store(ring);
        store.selectSlot(0);
        for (int i = 0; i < 256; i++) {
            assertEquals(i, store.record(pushBlock(256, i & 0xFF), -1));
        }
        assertEquals(-1, store.record(pushBlock(256, 0xAB), -1));
        assertFalse(store.reselectFallback());
        assertEquals(1L, store.selectFailures(0));
        assertEquals(-1, store.record(pushBlock(256, 0xCD), -1));
        assertEquals(0, store.count());
        store.delete();
        ring.delete();
    }

    @Test
    void aRecordRescuedByTheFallbackSelectionIsWrittenNotDropped() {
        // The counting contract the drain's pass shape reads (rulings D-8/D-9 name the
        // fallback counter and the dropped counter as the shape's two sides): a group
        // the fallback selection serves must arrive as a drawn group, never as a drop.
        // At the store's level that means the rescued record counts as written, the one
        // refusal that triggered the reselection stays counted under its own cause, and
        // the successful reselection adds no selection failure. The batcher's own
        // counters - the identity's other side - live outside this store and are not
        // this fix's to move; the wiring test pins their seats in the backend.
        TestRing ring = new TestRing(2, 96L << 10);
        InstanceDataStore store = store(ring);
        store.selectSlot(0);
        for (int i = 0; i < 256; i++) {
            assertEquals(i, store.record(pushBlock(256, i & 0xFF), -1));
        }
        assertEquals(-1, store.record(pushBlock(256, 0xAB), -1));
        assertTrue(store.reselectFallback());
        assertEquals(0, store.record(pushBlock(256, 0xCD), -1));
        assertEquals(257L, store.recordsWritten());
        assertEquals(1L, store.refusalsGrow());
        assertEquals(0L, store.refusalsNoSlice());
        assertEquals(0L, store.selectFailures(0));
        store.delete();
        ring.delete();
    }

    @Test
    void demandDecaysToTheLargestWindowTheFrameServed() {
        // The demand ratchet's repair: demand still grows on use inside a frame, but the frame
        // boundary resets it to the largest window that frame actually served (floored at the
        // initial ask), so one busy phase stops taxing every later window of the slot.
        TestRing ring = new TestRing(2, 1L << 20);
        InstanceDataStore store = store(ring);
        store.selectSlot(0);
        for (int i = 0; i < 300; i++) {
            assertEquals(i, store.record(pushBlock(256, i & 0xFF), -1));
        }
        assertEquals(512L * InstanceDataStore.RECORD_BYTES, store.demandBytes(0));
        store.beginFrame(0);
        assertEquals(300L * InstanceDataStore.RECORD_BYTES, store.demandBytes(0));
        // A quiet frame decays demand back to the initial ask, not below it.
        store.selectSlot(0);
        assertEquals(0, store.record(pushBlock(256, 1), -1));
        store.beginFrame(0);
        assertEquals(256L * InstanceDataStore.RECORD_BYTES, store.demandBytes(0));
        store.delete();
        ring.delete();
    }
}
