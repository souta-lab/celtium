package org.soutalab.celtium.vulkan.batch;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DirectStagingRingTest {

    @Test
    void allocationsAreSlotRelativeInEverySlot() {
        DirectStagingRing ring = new DirectStagingRing(2, 64);
        assertEquals(0, ring.allocate(16, 1));
        assertEquals(16, ring.allocate(8, 1));
        ring.advanceFrame();
        assertEquals(1, ring.currentSlot());
        assertEquals(0, ring.allocate(16, 1));
        assertEquals(16, ring.allocate(8, 1));
    }

    @Test
    void alignmentIsHonoured() {
        DirectStagingRing ring = new DirectStagingRing(1, 128);
        assertEquals(0, ring.allocate(3, 1));
        assertEquals(16, ring.allocate(4, 16));
    }

    @Test
    void fullSlotReturnsMinusOneAndAdvanceRecycles() {
        DirectStagingRing ring = new DirectStagingRing(2, 32);
        assertEquals(0, ring.allocate(32, 1));
        assertEquals(-1, ring.allocate(1, 1));
        ring.advanceFrame();
        assertEquals(0, ring.allocate(32, 1));
        ring.advanceFrame();
        assertEquals(0, ring.currentSlot());
        assertEquals(0, ring.allocate(32, 1));
    }

    @Test
    void writeLandsAtTheAllocatedOffset() {
        DirectStagingRing ring = new DirectStagingRing(1, 64);
        long offset = ring.allocate(4, 1);
        ring.write(offset, ByteBuffer.wrap(new byte[]{10, 11, 12, 13}));
        ByteBuffer back = ring.buffer().duplicate();
        assertEquals(10, back.get(0));
        assertEquals(13, back.get(3));
    }

    @Test
    void writeInSecondSlotLandsInThatSlotsRegion() {
        DirectStagingRing ring = new DirectStagingRing(2, 64);
        ring.advanceFrame();
        long offset = ring.allocate(4, 1);
        assertEquals(0, offset);
        ring.write(offset, ByteBuffer.wrap(new byte[]{20, 21, 22, 23}));
        ByteBuffer back = ring.buffer().duplicate();
        assertEquals(0, back.get(0));
        assertEquals(20, back.get(64));
        assertEquals(23, back.get(67));
    }

    @Test
    void selectSlotSelectsTheNamedSlotsRegion() {
        DirectStagingRing ring = new DirectStagingRing(3, 64);
        ring.selectSlot(2);
        assertEquals(2, ring.currentSlot());
        long offset = ring.allocate(4, 1);
        ring.write(offset, ByteBuffer.wrap(new byte[]{30, 31, 32, 33}));
        ByteBuffer back = ring.buffer().duplicate();
        assertEquals(0, back.get(0));
        assertEquals(0, back.get(64));
        assertEquals(30, back.get(128));
        assertEquals(33, back.get(131));
    }

    @Test
    void selectSlotRejectsOutOfRangeSlots() {
        DirectStagingRing ring = new DirectStagingRing(2, 64);
        assertThrows(IllegalArgumentException.class, () -> ring.selectSlot(-1));
        assertThrows(IllegalArgumentException.class, () -> ring.selectSlot(2));
        assertEquals(0, ring.currentSlot());
    }
}
