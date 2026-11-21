package org.soutalab.celtium.vulkan.gl;

import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The buffer storage semantics the extraction moved into {@link BufferStorageRecord} (refactor/giant-split-2):
 * allocation and reuse, the recorded logical sizes (#387), the data copy, and the mapping contract (#361).
 * Names are large and arbitrary so no {@code NativeGeometryBuffer} registration can answer for them.
 */
class BufferStorageRecordTest {

    private static final int NAME = 100_001;

    private static BufferStorageRecord record(
            it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<ByteBuffer> store) {
        return new BufferStorageRecord(store);
    }

    private static it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<ByteBuffer> store() {
        return new it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<>();
    }

    @Test
    void allocatesStorageAndRecordsTheLogicalSize() {
        var store = store();
        BufferStorageRecord record = record(store);
        record.bufferDataFor(NAME, 32, 0L);
        assertTrue(store.containsKey(NAME));
        assertEquals(32, store.get(NAME).capacity());
        assertEquals(32, record.logicalSizeOf(NAME, 999));
    }

    @Test
    void reusesTheStorageWhileItIsLargeEnoughSoTheAddressStaysStable() {
        var store = store();
        BufferStorageRecord record = record(store);
        record.bufferDataFor(NAME, 32, 0L);
        ByteBuffer first = store.get(NAME);
        record.bufferDataFor(NAME, 16, 0L);
        assertSame(first, store.get(NAME));
        assertEquals(32, store.get(NAME).capacity());
        assertEquals(16, record.logicalSizeOf(NAME, 999));
    }

    @Test
    void replacesTheStorageWhenTheRequestOutgrowsIt() {
        var store = store();
        BufferStorageRecord record = record(store);
        record.bufferDataFor(NAME, 16, 0L);
        ByteBuffer first = store.get(NAME);
        record.bufferDataFor(NAME, 64, 0L);
        assertNotSame(first, store.get(NAME));
        assertEquals(64, store.get(NAME).capacity());
        assertEquals(64, record.logicalSizeOf(NAME, 999));
    }

    @Test
    void aZeroSizeRedefinitionEmptiesTheBufferAndRecordsZero() {
        var store = store();
        BufferStorageRecord record = record(store);
        record.bufferDataFor(NAME, 32, 0L);
        record.bufferDataFor(NAME, 0, 0L);
        assertFalse(store.containsKey(NAME));
        // A recorded zero is a size, not an absence: the capacity fallback must not answer for it (#387).
        assertEquals(0, record.logicalSizeOf(NAME, 77));
    }

    @Test
    void copiesTheInitialDataAndRestoresTheStoragePosition() {
        var store = store();
        BufferStorageRecord record = record(store);
        record.bufferDataFor(NAME, 8, 0L);
        ByteBuffer storage = store.get(NAME);
        storage.position(3);
        ByteBuffer source = ByteBuffer.allocateDirect(8).order(java.nio.ByteOrder.nativeOrder());
        for (int i = 0; i < 8; i++) {
            source.put(i, (byte) (0x40 + i));
        }
        record.bufferDataFor(NAME, 8, MemoryUtil.memAddress(source));
        assertSame(storage, store.get(NAME));
        assertEquals(3, storage.position());
        for (int i = 0; i < 8; i++) {
            assertEquals((byte) (0x40 + i), storage.get(i));
        }
    }

    @Test
    void ignoresNamesAndSizesGlWouldNotAllocate() {
        var store = store();
        BufferStorageRecord record = record(store);
        record.bufferDataFor(0, 16, 0L);
        record.bufferDataFor(NAME, -1, 0L);
        record.bufferDataFor(NAME, (long) Integer.MAX_VALUE + 1L, 0L);
        assertTrue(store.isEmpty());
        assertEquals(55, record.logicalSizeOf(NAME, 55));
    }

    @Test
    void anUnrecordedNameFallsBackToTheCapacity() {
        BufferStorageRecord record = record(store());
        assertEquals(123, record.logicalSizeOf(NAME, 123));
    }

    @Test
    void forgetDropsTheRecordedSizeBackToTheCapacityFallback() {
        var store = store();
        BufferStorageRecord record = record(store);
        record.bufferDataFor(NAME, 24, 0L);
        assertEquals(24, record.logicalSizeOf(NAME, 99));
        record.forget(NAME);
        assertEquals(99, record.logicalSizeOf(NAME, 99));
    }

    @Test
    void mapsTheWholeBufferAndRangesInsideIt() {
        var store = store();
        BufferStorageRecord record = record(store);
        record.bufferDataFor(NAME, 16, 0L);
        long base = record.mapBufferFor(NAME, false, 0L, 0L, 0L);
        assertEquals(MemoryUtil.memAddress(store.get(NAME)), base);
        assertEquals(base + 8L, record.mapBufferFor(NAME, true, 8L, 8L, 0L));
        assertEquals("calls=2 refusals=0", record.census());
    }

    @Test
    void refusesRangesOutsideTheLogicalSizeAndCountsThem() {
        var store = store();
        BufferStorageRecord record = record(store);
        record.bufferDataFor(NAME, 32, 0L);
        // The backing capacity is still 32 after the shrink; the range check runs against the logical 16 (#387).
        record.bufferDataFor(NAME, 16, 0L);
        assertEquals(0L, record.mapBufferFor(NAME, true, -1L, 8L, 0L));
        assertEquals(0L, record.mapBufferFor(NAME, true, 8L, 16L, 0L));
        assertEquals("calls=2 refusals=2", record.census());
    }

    @Test
    void mappingAnUnallocatedBufferCountsTheCallAndAnswersAnAddress() {
        BufferStorageRecord record = record(store());
        record.mapBufferFor(NAME, false, 0L, 0L, 0L);
        assertEquals("calls=1 refusals=0", record.census());
    }

    @Test
    void theRangeContractIsTheRecordsOwn() {
        assertEquals(0L, BufferStorageRecord.mapRangeOffset(0, 16, 16));
        assertEquals(8L, BufferStorageRecord.mapRangeOffset(8, 8, 16));
        assertEquals(-1L, BufferStorageRecord.mapRangeOffset(-1, 1, 16));
        assertEquals(-1L, BufferStorageRecord.mapRangeOffset(8, 1024, 16));
        assertEquals(-1L, BufferStorageRecord.mapRangeOffset(0, 0, 16));
    }
}
