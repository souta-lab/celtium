package org.taumc.celeritas.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The funnel's contract (item 378), pinned without a world: the cache may answer a
 * {@code World#getCombinedLight} call only where its answer is identical to the vanilla body's -
 * enabled, no lightValue floor to fold in, a client world, the client main thread - and the cache
 * key layout is the documented vanilla packing. The epoch invariants (tick roll, light-write
 * invalidation) live in code this change does not touch and are covered by the fixture legs, not
 * here: they need a running world, and a test that faked one would pin the fake.
 */
class CeltiumLightCacheFunnelTest {

    @Test
    void funnelAppliesOnlyWhenEveryConditionHolds() {
        assertTrue(CeltiumLightCache.funnelApplies(true, 0, true, true));
        // Kill switch off: vanilla answers.
        assertFalse(CeltiumLightCache.funnelApplies(false, 0, true, true));
        // A non-zero lightValue is an argument only the vanilla body may fold in.
        assertFalse(CeltiumLightCache.funnelApplies(true, 1, true, true));
        assertFalse(CeltiumLightCache.funnelApplies(true, 15, true, true));
        // A server world's light is a different world's answer.
        assertFalse(CeltiumLightCache.funnelApplies(true, 0, false, true));
        // The maps are unsynchronised statics: off the client main thread, vanilla answers.
        assertFalse(CeltiumLightCache.funnelApplies(true, 0, true, false));
    }

    @Test
    void packMatchesTheVanillaCoordinateLayout() {
        assertEquals(0L, CeltiumLightCache.pack(0, 0, 0));
        assertEquals(1L << 38, CeltiumLightCache.pack(1, 0, 0));
        assertEquals(1L << 26, CeltiumLightCache.pack(0, 1, 0));
        assertEquals(1L, CeltiumLightCache.pack(0, 0, 1));
        // Negative coordinates wrap inside their own field and never bleed into a neighbour's.
        assertEquals((0x3FFFFFFL << 38) | (0xFFFL << 26) | 0x3FFFFFFL,
                CeltiumLightCache.pack(-1, -1, -1));
        assertEquals(0x3FFFFFFL, CeltiumLightCache.pack(0, 0, -1));
    }

    @Test
    void packKeepsAdjacentHeightsApartAtNegativeZ() {
        // The pre-fix layout shifted y by 25 while z's field is 26 bits wide, so bit 25 belonged
        // to both: at any negative z (whose sign bit is that shared bit), y and y^1 packed to the
        // same key and vertically adjacent blocks were served each other's cached light.
        assertNotEquals(CeltiumLightCache.pack(0, 0, -1), CeltiumLightCache.pack(0, 1, -1));
        assertEquals((1L << 26) | 0x3FFFFFFL, CeltiumLightCache.pack(0, 1, -1));
        assertNotEquals(CeltiumLightCache.pack(5, 64, -100), CeltiumLightCache.pack(5, 65, -100));
        // The whole negative-z half of the world, across the y field's range: flipping y's low
        // bit must always change the key.
        long[] negativeZ = {-1L, -100L, -30000000L};
        int[] heights = {0, 1, 255, 256, 4095};
        for (long z : negativeZ) {
            for (int y : heights) {
                assertNotEquals(CeltiumLightCache.pack(0, y, z), CeltiumLightCache.pack(0, y ^ 1, z));
            }
        }
    }

    @Test
    void funnelSummaryCarriesTheDynamicSideCounters() {
        // Item 430: a leg discriminates the dynamic-light candidates from this one line, so the
        // dynamic-side keys (snapshot consulted / sources found / answers raised, plus the
        // service's gather split) must stay present alongside the cache counters.
        String summary = CeltiumLightCache.funnelSummary();
        assertTrue(summary.contains("funnelCalls="), summary);
        assertTrue(summary.contains("funnelSourceQueries="), summary);
        assertTrue(summary.contains("funnelSourceHits="), summary);
        assertTrue(summary.contains("funnelRaised="), summary);
        assertTrue(summary.contains("gatherCalls="), summary);
        assertTrue(summary.contains("snapshotMismatch="), summary);
        assertTrue(summary.contains("snapshotEmpty="), summary);
    }
}
