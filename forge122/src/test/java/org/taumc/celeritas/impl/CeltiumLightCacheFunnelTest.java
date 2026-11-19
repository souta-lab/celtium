package org.taumc.celeritas.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        assertEquals(1L << 37, CeltiumLightCache.pack(1, 0, 0));
        assertEquals(1L << 25, CeltiumLightCache.pack(0, 1, 0));
        assertEquals(1L, CeltiumLightCache.pack(0, 0, 1));
        // Negative coordinates wrap inside their own field and never bleed into a neighbour's.
        assertEquals((0x3FFFFFFL << 37) | (0xFFFL << 25) | 0x3FFFFFFL,
                CeltiumLightCache.pack(-1, -1, -1));
        assertEquals(0x3FFFFFFL, CeltiumLightCache.pack(0, 0, -1));
    }
}
