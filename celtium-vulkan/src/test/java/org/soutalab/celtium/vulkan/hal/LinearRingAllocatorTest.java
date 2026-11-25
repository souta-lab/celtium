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
        assertThrows(IllegalArgumentException.class, () -> ring.alloc(8, -16));
        assertTrue(ring.used() == 0L);
    }

    // -------------------------------------------------- non-power-of-two alignment (Stage 5's contract)

    @Test
    void nonPowerOfTwoAlignmentRoundsByDivision() {
        // A 24-byte vertex stride is the standing case: slice starts must be whole vertices, and 24 is not a
        // power of two. The pre-fix allocator threw IllegalArgumentException for this alignment, which is what
        // made Stage 5's staging ring unusable against Stage 2's allocator.
        LinearRingAllocator ring = new LinearRingAllocator(4096);
        assertEquals(0L, ring.alloc(10, 1));
        assertEquals(24L, ring.alloc(24, 24));
        assertEquals(48L, ring.used());
        assertEquals(48L, ring.alloc(24, 24));
    }

    @Test
    void strideSlicesPackAtExactMultiples() {
        LinearRingAllocator ring = new LinearRingAllocator(24 * 8);
        for (int i = 0; i < 8; i++) {
            assertEquals(24L * i, ring.alloc(24, 24));
        }
        assertEquals(-1L, ring.alloc(24, 24));
    }

    @Test
    void oddAlignmentsAreLegalAndExact() {
        LinearRingAllocator ring = new LinearRingAllocator(64);
        assertEquals(0L, ring.alloc(5, 1));
        assertEquals(6L, ring.alloc(4, 3));
        assertEquals(10L, ring.used());
        // lcm(16, stride) values the immediate mesh asks for are not powers of two either (48 for stride 24).
        LinearRingAllocator meshRing = new LinearRingAllocator(4096);
        assertEquals(0L, meshRing.alloc(100, 1));
        assertEquals(144L, meshRing.alloc(24, 48));
    }

    @Test
    void nonPowerOfTwoPaddingCountsAgainstCapacity() {
        LinearRingAllocator ring = new LinearRingAllocator(100);
        assertEquals(0L, ring.alloc(10, 1));
        // Aligning to 24 puts the next slice at 24, leaving 76: a 77-byte request must refuse, not wrap.
        assertEquals(-1L, ring.alloc(77, 24));
        assertEquals(24L, ring.alloc(76, 24));
    }

    // -------------------------------------------------- holds and the deferred reset

    @Test
    void aHeldSlotDefersItsResetUntilTheLastRelease() {
        LinearRingAllocator ring = new LinearRingAllocator(1024);
        assertEquals(0L, ring.alloc(400, 1));
        ring.hold();
        assertEquals(false, ring.reset());
        assertTrue(ring.resetDeferred());
        // The reset was not applied: the cursor kept its place, so a later slice lands after the held one.
        assertEquals(400L, ring.used());
        assertEquals(400L, ring.alloc(100, 1));
        ring.release();
        // The last release applied the deferred reset.
        assertEquals(0L, ring.used());
        assertTrue(!ring.resetDeferred());
        assertEquals(0L, ring.alloc(400, 1));
    }

    @Test
    void resetAppliesImmediatelyWhenNothingIsHeld() {
        LinearRingAllocator ring = new LinearRingAllocator(1024);
        assertEquals(0L, ring.alloc(400, 1));
        assertEquals(true, ring.reset());
        assertEquals(0L, ring.used());
    }

    @Test
    void onlyTheLastOfSeveralHoldsAppliesTheDeferredReset() {
        LinearRingAllocator ring = new LinearRingAllocator(1024);
        ring.alloc(128, 1);
        ring.hold();
        ring.hold();
        assertEquals(false, ring.reset());
        ring.release();
        assertEquals(128L, ring.used());
        assertTrue(ring.resetDeferred());
        ring.release();
        assertEquals(0L, ring.used());
    }

    @Test
    void releasingWithoutAHoldThrows() {
        LinearRingAllocator ring = new LinearRingAllocator(64);
        assertThrows(IllegalStateException.class, ring::release);
    }
}
