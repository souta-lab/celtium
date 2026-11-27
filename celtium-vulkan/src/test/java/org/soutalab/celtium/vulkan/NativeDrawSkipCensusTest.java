package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DP-02/DP-03's census: native-draw skips are counted at the draw entry, split by cause.
 * <p>
 * The split is the point: a stalled skip is the device-loss degraded state (the renderer is being
 * rebuilt), a refused skip is a live backend declining retained geometry - the case that used to throw
 * out of {@code CeltiumNativeDraw} into uncaught callers. Neither population is
 * {@code stalledGeometryDrops}, which counts accepted batches dropped at flush time.
 */
class NativeDrawSkipCensusTest {

    @Test
    void stalledAndRefusedAreCountedApart() {
        long stalled = NativeDrawSkipCensus.stalledSkips();
        long refused = NativeDrawSkipCensus.refusedSkips();
        NativeDrawSkipCensus.noteStalledSkip();
        NativeDrawSkipCensus.noteRefusedSkip();
        NativeDrawSkipCensus.noteRefusedSkip();
        assertEquals(stalled + 1, NativeDrawSkipCensus.stalledSkips(), "stalled");
        assertEquals(refused + 2, NativeDrawSkipCensus.refusedSkips(), "refused");
    }

    @Test
    void summaryNamesBothCauses() {
        String summary = NativeDrawSkipCensus.summary();
        assertTrue(summary.contains("stalled=" + NativeDrawSkipCensus.stalledSkips()), summary);
        assertTrue(summary.contains("refused=" + NativeDrawSkipCensus.refusedSkips()), summary);
    }
}
