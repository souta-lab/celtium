package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DP-29's census: the no-camera guards' skips are counted, and the consecutive world-skip run is
 * tracked so a stuck session separates from the one-frame entry transient the guards exist for.
 */
class NoCameraSkipCensusTest {

    @Test
    void eachGuardIsCountedApart() {
        long flagTrue = NoCameraSkipCensus.flagTrueFrames();
        long world = NoCameraSkipCensus.worldSkips();
        long hud = NoCameraSkipCensus.hudSkips();
        NoCameraSkipCensus.noteFlagTrue();
        NoCameraSkipCensus.noteWorldSkip();
        NoCameraSkipCensus.noteHudSkip();
        NoCameraSkipCensus.noteHudSkip();
        assertEquals(flagTrue + 1, NoCameraSkipCensus.flagTrueFrames(), "flagTrue");
        assertEquals(world + 1, NoCameraSkipCensus.worldSkips(), "worldSkips");
        assertEquals(hud + 2, NoCameraSkipCensus.hudSkips(), "hudSkips");
        NoCameraSkipCensus.noteWorldRendered();
    }

    /**
     * The run resets when a world render actually runs: after a reset, one more skip must not extend
     * the recorded maximum - if the reset were missing, the run (and the max) would keep growing.
     */
    @Test
    void aRenderedFrameResetsTheConsecutiveRun() {
        NoCameraSkipCensus.noteWorldRendered();
        NoCameraSkipCensus.noteWorldSkip();
        NoCameraSkipCensus.noteWorldSkip();
        NoCameraSkipCensus.noteWorldSkip();
        long max = NoCameraSkipCensus.maxConsecutiveWorldSkips();
        assertTrue(max >= 3, "a three-skip run is recorded, max=" + max);
        NoCameraSkipCensus.noteWorldRendered();
        NoCameraSkipCensus.noteWorldSkip();
        assertEquals(max, NoCameraSkipCensus.maxConsecutiveWorldSkips(),
                "one skip after a rendered frame starts a new run instead of extending the old one");
        NoCameraSkipCensus.noteWorldRendered();
    }

    @Test
    void summaryNamesTheRun() {
        String summary = NoCameraSkipCensus.summary();
        assertTrue(summary.contains("maxConsecutiveWorldSkips="
                + NoCameraSkipCensus.maxConsecutiveWorldSkips()), summary);
        assertTrue(summary.contains("hudSkips=" + NoCameraSkipCensus.hudSkips()), summary);
    }
}
