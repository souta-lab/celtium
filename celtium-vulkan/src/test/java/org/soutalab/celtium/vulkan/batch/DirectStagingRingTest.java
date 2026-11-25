package org.soutalab.celtium.vulkan.batch;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DirectStagingRingTest {

    @Test
    void allocationsAreSlotRelativeThenRingAbsolute() {
        DirectStagingRing ring = new DirectStagingRing(2, 64);
        assertEquals(0, ring.allocate(16, 1));
        assertEquals(16, ring.allocate(8, 1));
        ring.advanceFrame();
        assertEquals(1, ring.currentSlot());
        assertEquals(64, ring.allocate(16, 1));
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
        assertEquals(32, ring.allocate(32, 1));
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
}
