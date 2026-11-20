package org.taumc.celeritas.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * The dynamic-light mirror's raise (item 430), pinned without a world: per source
 * {@code level - distance} measured at the queried block's centre, the maximum over sources,
 * floored into the integer block component of the vanilla combined packing
 * ({@code sky << 20 | block << 4}). The raise is a maximum - it never adds and never lowers -
 * and every bit outside the block component is returned untouched.
 */
class CeltiumLightCacheMirrorTest {

    private static int combined(int sky, int block) {
        return sky << 20 | block << 4;
    }

    private static float[] sources(float... vec4s) {
        return vec4s;
    }

    @Test
    void noSourcesLeavesTheValueAlone() {
        int value = combined(9, 4);
        assertEquals(value, CeltiumLightCache.raiseCombinedLight(value, 10, 64, -3, new float[0], 0));
    }

    @Test
    void aSourceAtTheCentreRaisesToItsLevel() {
        // Block (10, 64, -3), centre (10.5, 64.5, -2.5); a level-15 source right there.
        int raised = CeltiumLightCache.raiseCombinedLight(combined(9, 4), 10, 64, -3,
                sources(10.5F, 64.5F, -2.5F, 15.0F), 1);
        assertEquals(combined(9, 15), raised);
    }

    @Test
    void falloffIsOneLevelPerBlockOfDistance() {
        // Three blocks east of the centre: 15 - 3 = 12.
        int raised = CeltiumLightCache.raiseCombinedLight(combined(0, 0), 10, 64, -3,
                sources(13.5F, 64.5F, -2.5F, 15.0F), 1);
        assertEquals(combined(0, 12), raised);
    }

    @Test
    void theContributionIsFlooredNotRounded() {
        // Distance 2.1 from a level-10 source contributes 7.9; the integer block level is 7, so
        // the mirror never outshines the terrain raise at the same centre.
        int raised = CeltiumLightCache.raiseCombinedLight(combined(0, 0), 0, 0, 0,
                sources(0.5F, 0.5F, 2.6F, 10.0F), 1);
        assertEquals(combined(0, 7), raised);
    }

    @Test
    void theStrongestSourceWins() {
        // A weak near source (10 - 1 = 9) and a stronger far one (15 - 4 = 11): the max is 11.
        int raised = CeltiumLightCache.raiseCombinedLight(combined(0, 0), 0, 0, 0,
                sources(1.5F, 0.5F, 0.5F, 10.0F, 4.5F, 0.5F, 0.5F, 15.0F), 2);
        assertEquals(combined(0, 11), raised);
    }

    @Test
    void aBrighterVanillaBlockIsNeverLowered() {
        int value = combined(3, 14);
        int raised = CeltiumLightCache.raiseCombinedLight(value, 0, 0, 0,
                sources(5.5F, 0.5F, 0.5F, 10.0F), 1);
        assertEquals(value, raised);
    }

    @Test
    void aSourceBeyondItsReachChangesNothing() {
        // Level 5, twenty blocks away: the contribution is negative, the value stands.
        int value = combined(15, 2);
        int raised = CeltiumLightCache.raiseCombinedLight(value, 0, 0, 0,
                sources(20.5F, 0.5F, 0.5F, 5.0F), 1);
        assertEquals(value, raised);
    }
}
