package org.soutalab.celtium.vulkan.batch;

import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;
import org.soutalab.celtium.vulkan.hal.FrameLinearRingAccess;
import org.soutalab.celtium.vulkan.hal.LinearRingAllocator;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Fixes the adapter's own translation over a fake ring (ringmap section 7 item 2, decided as
 * option (a) at the Stage 5 stage 1 intake): which slot a reservation is placed in, how a size
 * of 0 is absorbed, and where a write lands. The fake reuses the real {@link LinearRingAllocator}
 * policy per slot, so what is under test is the adapter's wiring, not the allocator's arithmetic
 * (that has its own tests). Whether the real ring's mapped address really is its slot buffer is
 * a device fact no fake can fix; it stays with the wiring leg.
 */
class FrameLinearStagingRingAdapterTest {

    /** A {@link FrameLinearRingAccess} over direct buffers, one real allocator per slot. */
    private static final class FakeRing implements FrameLinearRingAccess {
        private final ByteBuffer[] memory;
        private final LinearRingAllocator[] allocators;
        private int allocCalls;
        private int lastAllocSlot = -1;

        FakeRing(int slots, long slotCapacity) {
            this.memory = new ByteBuffer[slots];
            this.allocators = new LinearRingAllocator[slots];
            for (int slot = 0; slot < slots; slot++) {
                this.memory[slot] = ByteBuffer.allocateDirect(Math.toIntExact(slotCapacity));
                this.allocators[slot] = new LinearRingAllocator(slotCapacity);
            }
        }

        @Override
        public int slots() {
            return this.memory.length;
        }

        @Override
        public long slotCapacity(int slot) {
            return this.allocators[slot].capacity();
        }

        @Override
        public long alloc(int slot, long size, long alignment) {
            this.allocCalls++;
            this.lastAllocSlot = slot;
            return this.allocators[slot].alloc(size, alignment);
        }

        @Override
        public long used(int slot) {
            return this.allocators[slot].used();
        }

        @Override
        public long mappedAddress(int slot, long offset) {
            return MemoryUtil.memAddress(this.memory[slot]) + offset;
        }

        byte read(int slot, int index) {
            return this.memory[slot].get(index);
        }
    }

    /** The adapter's write contract takes direct buffers only (the capture side's shape). */
    private static ByteBuffer direct(byte... bytes) {
        ByteBuffer buffer = ByteBuffer.allocateDirect(bytes.length);
        buffer.put(bytes);
        buffer.flip();
        return buffer;
    }

    @Test
    void allocateDelegatesToTheCurrentSlotOnly() {
        FakeRing ring = new FakeRing(2, 64);
        FrameLinearStagingRingAdapter adapter = new FrameLinearStagingRingAdapter(ring);
        assertEquals(0, adapter.allocate(16, 1));
        assertEquals(16, adapter.allocate(8, 1));
        assertEquals(0, ring.lastAllocSlot);
        assertEquals(24, ring.used(0));
        assertEquals(0, ring.used(1));
    }

    @Test
    void allocateAfterAdvanceGoesToTheNextSlot() {
        FakeRing ring = new FakeRing(2, 64);
        FrameLinearStagingRingAdapter adapter = new FrameLinearStagingRingAdapter(ring);
        assertEquals(0, adapter.allocate(16, 1));
        adapter.advanceFrame();
        assertEquals(1, adapter.currentSlot());
        assertEquals(0, adapter.allocate(16, 1));
        assertEquals(1, ring.lastAllocSlot);
        assertEquals(16, ring.used(0));
        assertEquals(16, ring.used(1));
    }

    @Test
    void allocateHonoursAlignmentThroughTheRing() {
        FakeRing ring = new FakeRing(1, 128);
        FrameLinearStagingRingAdapter adapter = new FrameLinearStagingRingAdapter(ring);
        assertEquals(0, adapter.allocate(3, 1));
        assertEquals(16, adapter.allocate(4, 16));
    }

    @Test
    void allocateReturnsMinusOneWhenTheSlotIsFull() {
        FakeRing ring = new FakeRing(1, 32);
        FrameLinearStagingRingAdapter adapter = new FrameLinearStagingRingAdapter(ring);
        assertEquals(0, adapter.allocate(32, 1));
        assertEquals(-1, adapter.allocate(1, 1));
    }

    @Test
    void zeroSizeIsAbsorbedWithoutTouchingTheAllocator() {
        FakeRing ring = new FakeRing(1, 64);
        FrameLinearStagingRingAdapter adapter = new FrameLinearStagingRingAdapter(ring);
        assertEquals(0, adapter.allocate(5, 1));
        int callsBefore = ring.allocCalls;
        // The aligned offset the next reservation would start at, as DirectStagingRing returns.
        assertEquals(16, adapter.allocate(0, 16));
        assertEquals(callsBefore, ring.allocCalls);
        // The one documented accounting difference from the headless ring: the adapter does not
        // consume the alignment padding on a zero reservation.
        assertEquals(5, ring.used(0));
    }

    @Test
    void zeroSizePastTheSlotEndReturnsMinusOne() {
        FakeRing ring = new FakeRing(1, 16);
        FrameLinearStagingRingAdapter adapter = new FrameLinearStagingRingAdapter(ring);
        assertEquals(0, adapter.allocate(16, 1));
        assertEquals(-1, adapter.allocate(0, 32));
    }

    @Test
    void negativeSizeThrows() {
        FrameLinearStagingRingAdapter adapter = new FrameLinearStagingRingAdapter(new FakeRing(1, 64));
        assertThrows(IllegalArgumentException.class, () -> adapter.allocate(-1, 1));
    }

    @Test
    void writeLandsInTheCurrentSlotAtTheOffset() {
        FakeRing ring = new FakeRing(2, 64);
        FrameLinearStagingRingAdapter adapter = new FrameLinearStagingRingAdapter(ring);
        long offset = adapter.allocate(4, 1);
        adapter.write(offset, direct((byte) 10, (byte) 11, (byte) 12, (byte) 13));
        assertEquals(10, ring.read(0, 0));
        assertEquals(13, ring.read(0, 3));
        assertEquals(0, ring.read(1, 0));
    }

    @Test
    void writeAfterAdvanceLandsInTheSecondSlot() {
        FakeRing ring = new FakeRing(2, 64);
        FrameLinearStagingRingAdapter adapter = new FrameLinearStagingRingAdapter(ring);
        adapter.advanceFrame();
        long offset = adapter.allocate(4, 1);
        assertEquals(0, offset);
        adapter.write(offset, direct((byte) 20, (byte) 21, (byte) 22, (byte) 23));
        assertEquals(0, ring.read(0, 0));
        assertEquals(20, ring.read(1, 0));
        assertEquals(23, ring.read(1, 3));
    }

    @Test
    void writeDoesNotAdvanceTheSourcePosition() {
        FakeRing ring = new FakeRing(1, 64);
        FrameLinearStagingRingAdapter adapter = new FrameLinearStagingRingAdapter(ring);
        ByteBuffer source = direct((byte) 1, (byte) 2, (byte) 3, (byte) 4);
        source.position(1);
        adapter.write(adapter.allocate(3, 1), source);
        assertEquals(1, source.position());
        assertEquals(2, ring.read(0, 0));
        assertEquals(4, ring.read(0, 2));
    }

    @Test
    void writeOutsideTheSlotThrows() {
        FakeRing ring = new FakeRing(1, 64);
        FrameLinearStagingRingAdapter adapter = new FrameLinearStagingRingAdapter(ring);
        ByteBuffer four = direct((byte) 1, (byte) 2, (byte) 3, (byte) 4);
        assertThrows(IllegalArgumentException.class, () -> adapter.write(-1, four));
        assertThrows(IllegalArgumentException.class, () -> adapter.write(62, four));
    }

    @Test
    void advanceFrameRotatesModuloSlotCount() {
        FakeRing ring = new FakeRing(2, 64);
        FrameLinearStagingRingAdapter adapter = new FrameLinearStagingRingAdapter(ring);
        assertEquals(2, adapter.slotCount());
        assertEquals(64, adapter.slotCapacity());
        assertEquals(0, adapter.currentSlot());
        adapter.advanceFrame();
        assertEquals(1, adapter.currentSlot());
        adapter.advanceFrame();
        assertEquals(0, adapter.currentSlot());
    }

    @Test
    void constructorRejectsNull() {
        assertThrows(IllegalArgumentException.class, () -> new FrameLinearStagingRingAdapter(null));
    }
}
