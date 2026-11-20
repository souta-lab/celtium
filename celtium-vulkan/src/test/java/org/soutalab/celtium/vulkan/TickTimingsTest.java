package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The arithmetic behind {@code CELTIUM_TICK_SPLIT2} (item 407), checked without a game.
 * <p>
 * What these tests can and cannot prove: they prove the per-tick residual never goes negative, that both new
 * switches stay opt-in, and that the top-level set the residual subtracts is the design's thirteen names with
 * no duplicates (a duplicate would subtract one call site twice and read as attribution that never happened).
 * They prove nothing about a measured tick - the brackets themselves are read from the game, and a green test
 * here is not evidence about any leg.
 */
class TickTimingsTest {

    @Test
    void split2IsOffUnlessTheHarnessAsksForIt() {
        // The test JVM runs no harness, which is the shipping configuration: a normal run must not pay for the
        // instrument, and this is the only assertion that can catch the default being flipped.
        assertFalse(TickTimings.split2Enabled(), "TickSplit2 must stay opt-in");
    }

    @Test
    void theTickClockDefaultsToWallTime() {
        assertFalse(TickTimings.cpuClock(), "the CPU clock must stay opt-in, so old numbers keep their meaning");
    }

    @Test
    void residualIsWhatTheUnionLeaves() {
        assertEquals(1_749_000L, TickTimings.residualFromCovered(1_844_000L, 95_000L),
                "the Gate 0 shape: a 1,844 us tick with 95 us covered leaves 1,749 us");
        assertEquals(500L, TickTimings.residualFromCovered(500L, 0L), "nothing covered, everything residual");
    }

    @Test
    void residualNeverGoesNegative() {
        // Overlapping brackets are unioned by the coverage tracker, so this should not happen - but a covered
        // span read on the CPU clock can still overtake its total, and the report must not print a negative
        // residual as if it were a tick that happened.
        assertEquals(0L, TickTimings.residualFromCovered(100L, 250L));
    }

    @Test
    void tickTop2IsTheDesignSetWithoutDuplicates() {
        int[] top = TickTimings.tickTop2Indices();
        assertEquals(13, top.length, "the legacy three plus the ten runTick call-site names");
        for (int i = 0; i < top.length; i++) {
            assertTrue(top[i] >= 0, "every tickTop2 name must resolve in the shared name table");
            for (int j = i + 1; j < top.length; j++) {
                assertTrue(top[i] != top[j], "a duplicated top bracket would be subtracted twice");
            }
        }
    }

    @Test
    void everySplit2NameResolves() {
        // A name that does not resolve reads -1, and a hook holding -1 measures nothing while looking wired:
        // the constants class must never carry one.
        int[] indices = {
                TickSplit2Index.TICK_SCHED, TickSplit2Index.TICK_NET, TickSplit2Index.TICK_INPUT,
                TickSplit2Index.TICK_GAME_MODE, TickSplit2Index.TICK_SOUND, TickSplit2Index.TICK_GUI_TICK,
                TickSplit2Index.TICK_FML, TickSplit2Index.TICK_WORLD, TickSplit2Index.TICK_LVL_RENDERER,
                TickSplit2Index.TICK_FIXTURE, TickSplit2Index.TICK_ENT, TickSplit2Index.TICK_TILE_ENT,
                TickSplit2Index.TICK_PARTICLES, TickSplit2Index.TICK_CHUNKS, TickSplit2Index.TICK_DRAIN,
        };
        for (int index : indices) {
            assertTrue(index >= 0, "every TickSplit2 bracket name must exist in FramePassTimings' table");
        }
    }
}
