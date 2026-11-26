package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InstanceDataStoreTest {

    /** A slot buffer over plain native memory: growth reallocates and copies, like the host buffer. */
    private static final class TestBuffer implements InstanceDataStore.SlotBuffer {
        private ByteBuffer memory;
        private long size;

        TestBuffer(long capacityBytes) {
            this.size = capacityBytes;
            this.memory = MemoryUtil.memAlloc((int) capacityBytes);
        }

        @Override
        public long mappedAddress() {
            return MemoryUtil.memAddress(this.memory);
        }

        @Override
        public long bufferHandle() {
            return 0L;
        }

        @Override
        public void ensureCapacity(long requiredBytes) {
            if (requiredBytes <= this.size) {
                return;
            }
            ByteBuffer grown = MemoryUtil.memAlloc((int) requiredBytes);
            MemoryUtil.memCopy(MemoryUtil.memAddress(this.memory), MemoryUtil.memAddress(grown), this.size);
            MemoryUtil.memFree(this.memory);
            this.memory = grown;
            this.size = requiredBytes;
        }

        @Override
        public void delete() {
            if (this.memory != null) {
                MemoryUtil.memFree(this.memory);
                this.memory = null;
            }
        }
    }

    private static final class TestFactory implements InstanceDataStore.SlotBufferFactory {
        final List<TestBuffer> created = new ArrayList<TestBuffer>();

        @Override
        public InstanceDataStore.SlotBuffer create(long capacityBytes) {
            TestBuffer buffer = new TestBuffer(capacityBytes);
            this.created.add(buffer);
            return buffer;
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

    @Test
    void recordsLandInTheSelectedSlotOnly() {
        TestFactory factory = new TestFactory();
        InstanceDataStore store = new InstanceDataStore(factory);
        store.selectSlot(0);
        assertEquals(0, store.record(pushBlock(256, 0x11), -1));
        store.selectSlot(1);
        assertEquals(0, store.record(pushBlock(256, 0x22), -1));
        long slot0 = factory.created.get(0).mappedAddress();
        long slot1 = factory.created.get(1).mappedAddress();
        assertEquals(0x11, MemoryUtil.memGetByte(slot0) & 0xFF);
        assertEquals(0x22, MemoryUtil.memGetByte(slot1) & 0xFF);
        // Writing slot 1's second record leaves slot 0's bytes untouched.
        assertEquals(1, store.record(pushBlock(256, 0x33), -1));
        assertEquals(0x11, MemoryUtil.memGetByte(slot0) & 0xFF);
        assertEquals(1, store.count(0));
        assertEquals(2, store.count(1));
        store.delete();
    }

    @Test
    void selectingASlotRewindsItsCursor() {
        TestFactory factory = new TestFactory();
        InstanceDataStore store = new InstanceDataStore(factory);
        store.selectSlot(0);
        store.record(pushBlock(256, 1), -1);
        store.record(pushBlock(256, 2), -1);
        assertEquals(2, store.count());
        store.selectSlot(1);
        store.selectSlot(0);
        assertEquals(0, store.count());
        assertEquals(0, store.record(pushBlock(256, 3), -1));
        store.delete();
    }

    @Test
    void recordLayoutMatchesTheImmediateDrawRecordsStride() {
        TestFactory factory = new TestFactory();
        InstanceDataStore store = new InstanceDataStore(factory);
        store.selectSlot(0);
        // A 208-byte textured-family block: the rest of the 256-byte record is zeroed, and the
        // bindless array index lands as float bits at offset 216, as the record shaders expect.
        store.record(pushBlock(208, 0x7F), 7);
        long base = factory.created.get(0).mappedAddress();
        assertEquals(0x7F, MemoryUtil.memGetByte(base + 207) & 0xFF);
        assertEquals(0, MemoryUtil.memGetByte(base + 208) & 0xFF);
        assertEquals(7, Float.floatToRawIntBits(MemoryUtil.memGetFloat(base + 216)));
        assertEquals(0, MemoryUtil.memGetByte(base + 255) & 0xFF);
        store.delete();
    }

    @Test
    void shortBlocksAreRefused() {
        TestFactory factory = new TestFactory();
        InstanceDataStore store = new InstanceDataStore(factory);
        store.selectSlot(0);
        assertEquals(-1, store.record(pushBlock(100, 1), -1));
        assertEquals(-1, store.record(null, -1));
        assertEquals(0, store.count());
        store.delete();
    }

    @Test
    void growthPreservesRecordsAndIsPerSlot() {
        TestFactory factory = new TestFactory();
        InstanceDataStore store = new InstanceDataStore(factory);
        store.selectSlot(0);
        // 256 records fill the initial capacity; the 257th grows slot 0 only.
        for (int i = 0; i < 256; i++) {
            assertEquals(i, store.record(pushBlock(256, i & 0xFF), -1));
        }
        assertEquals(256, store.record(pushBlock(256, 0xAB), -1));
        long slot0 = factory.created.get(0).mappedAddress();
        assertEquals(1, MemoryUtil.memGetByte(slot0 + InstanceDataStore.RECORD_BYTES) & 0xFF);
        assertEquals(0xAB, MemoryUtil.memGetByte(slot0 + 256L * InstanceDataStore.RECORD_BYTES) & 0xFF);
        // Slot 1 was never touched by slot 0's growth.
        store.selectSlot(1);
        assertEquals(0, store.count());
        assertEquals(0, store.record(pushBlock(256, 0xCD), -1));
        store.delete();
    }
}
