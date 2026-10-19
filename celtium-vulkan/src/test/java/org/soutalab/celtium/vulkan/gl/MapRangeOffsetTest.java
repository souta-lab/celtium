package org.soutalab.celtium.vulkan.gl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The range check {@code glMapBufferRange} needs (audit #361). The old code clamped an invalid offset to zero and never
 * looked at the length, so a caller asking for a range outside the buffer got a pointer into it and could write past it.
 */
class MapRangeOffsetTest {
    @Test
    void acceptsAValidRange() {
        assertEquals(0L, CeltiumGlStub.mapRangeOffset(0, 16, 16));
        assertEquals(8L, CeltiumGlStub.mapRangeOffset(8, 8, 16));
        assertEquals(0L, CeltiumGlStub.mapRangeOffset(0, 1, 16));
    }

    @Test
    void refusesANegativeOffsetInsteadOfClampingIt() {
        assertEquals(-1L, CeltiumGlStub.mapRangeOffset(-1, 1, 16));
        assertEquals(-1L, CeltiumGlStub.mapRangeOffset(Long.MIN_VALUE, 1, 16));
    }

    @Test
    void refusesARangeThatRunsPastTheBuffer() {
        assertEquals(-1L, CeltiumGlStub.mapRangeOffset(8, 1024, 16));
        assertEquals(-1L, CeltiumGlStub.mapRangeOffset(16, 1, 16));
        assertEquals(-1L, CeltiumGlStub.mapRangeOffset(0, 17, 16));
    }

    @Test
    void refusesAnEmptyOrNegativeLength() {
        assertEquals(-1L, CeltiumGlStub.mapRangeOffset(0, 0, 16));
        assertEquals(-1L, CeltiumGlStub.mapRangeOffset(0, -4, 16));
    }

    @Test
    void refusesAnOverflowingRangeWithoutComputingTheSum() {
        // offset + length would wrap to a small positive number; the check must not depend on that sum.
        assertEquals(-1L, CeltiumGlStub.mapRangeOffset(Long.MAX_VALUE - 8, 16, 16));
        assertEquals(-1L, CeltiumGlStub.mapRangeOffset(Long.MAX_VALUE, Long.MAX_VALUE, 16));
    }

    @Test
    void refusesEverythingForAnEmptyBuffer() {
        assertEquals(-1L, CeltiumGlStub.mapRangeOffset(0, 1, 0));
    }
}
