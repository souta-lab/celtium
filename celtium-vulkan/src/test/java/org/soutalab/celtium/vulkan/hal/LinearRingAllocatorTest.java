package org.soutalab.celtium.vulkan.hal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The linear ring's structural claims, pinned without a device (Stage 2): bump-only allocation (no
 * fragmentation can form), alignment honoured, exhaustion reported as {@code -1} rather than by growth,
 * wrapping or waiting, and reset as the only reuse. These are the properties the tick383 slab fragmentation
 * failure is designed out by, so they are stated as tests rather than as comments.
 */
class LinearRingAllocatorTest {

    @Test
    void allocationsBumpInOrderWithoutGapsBeyondAlignment() {
        LinearRingAllocator ring = new LinearRingAllocator(1024);
        assertEquals(0L, ring.alloc(100, 1));
        assertEquals(100L, ring.alloc(28, 4));
        assertEquals(128L, ring.used());
    }

    @Test
    void alignmentRoundsTheCursorUp() {
        LinearRingAllocator ring = new LinearRingAllocator(4096);
        assertEquals(0L, ring.alloc(10, 1));
        assertEquals(256L, ring.alloc(8, 256));
        assertEquals(264L, ring.used());
    }

    @Test
    void exhaustionReturnsMinusOneAndHandsOutNothing() {
        LinearRingAllocator ring = new LinearRingAllocator(512);
        assertEquals(0L, ring.alloc(400, 1));
        assertEquals(-1L, ring.alloc(200, 1));
        // The failed allocation consumed nothing: a slice that does fit still lands at the same cursor.
        assertEquals(400L, ring.used());
        assertEquals(400L, ring.alloc(112, 1));
        assertEquals(-1L, ring.alloc(1, 1));
    }

    @Test
    void anAllocationExactlyFillingTheSlotSucceeds() {
        LinearRingAllocator ring = new LinearRingAllocator(1024);
        assertEquals(0L, ring.alloc(1024, 1));
        assertEquals(1024L, ring.used());
        assertEquals(-1L, ring.alloc(1, 1));
    }

    @Test
    void alignmentPaddingCountsAgainstCapacity() {
        LinearRingAllocator ring = new LinearRingAllocator(300);
        assertEquals(0L, ring.alloc(10, 1));
        // Aligning the next slice to 256 leaves only 44 bytes: a 100-byte request must refuse, not wrap.
        assertEquals(-1L, ring.alloc(100, 256));
        assertEquals(256L, ring.alloc(44, 256));
    }

    @Test
    void resetIsTheOnlyReuseAndHighWaterSurvivesIt() {
        LinearRingAllocator ring = new LinearRingAllocator(1024);
        assertEquals(0L, ring.alloc(700, 1));
        assertEquals(-1L, ring.alloc(700, 1));
        ring.reset();
        assertEquals(0L, ring.used());
        assertEquals(0L, ring.alloc(700, 1));
        assertEquals(700L, ring.highWater());
    }

    @Test
    void invalidArgumentsAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> new LinearRingAllocator(0));
        LinearRingAllocator ring = new LinearRingAllocator(64);
        assertThrows(IllegalArgumentException.class, () -> ring.alloc(0, 1));
        assertThrows(IllegalArgumentException.class, () -> ring.alloc(-8, 1));
        assertThrows(IllegalArgumentException.class, () -> ring.alloc(8, 0));
        assertThrows(IllegalArgumentException.class, () -> ring.alloc(8, 3));
        assertTrue(ring.used() == 0L);
    }
}
