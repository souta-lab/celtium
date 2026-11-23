package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * D3's separation census, driven through its Ledger directly: creations are filed per
 * (kind, site) - the same site name under graphics and compute stays two entries - the slow
 * subtotal counts only creations at or over the threshold, and the summary print cadence is
 * the powers of two, so a creation storm cannot flood (or, through the log limiter, erase)
 * its own evidence.
 */
class PipelineCreateCensusTest {
    private static final long SLOW = 10_000_000L;
    private static final long MS = 1_000_000L;

    @Test void sitesAreSplitByKindAndName() {
        PipelineCreateCensus.Ledger ledger = new PipelineCreateCensus.Ledger();
        ledger.note("graphics", "terrain", 1, 3 * MS, SLOW);
        ledger.note("graphics", "terrain", 2, 5 * MS, SLOW);
        ledger.note("compute", "terrain", 1, 7 * MS, SLOW);
        String line = ledger.summaryLine();
        assertTrue(line.contains("calls=3 pipelines=4 totalMs=15 worstMs=7"), line);
        assertTrue(line.contains("graphics:terrain{calls=2 pipelines=3 ms=8 worstMs=5 slow=0/0ms}"), line);
        assertTrue(line.contains("compute:terrain{calls=1 pipelines=1 ms=7 worstMs=7 slow=0/0ms}"), line);
    }

    @Test void slowSubtotalCountsOnlyCreationsAtOrOverTheThreshold() {
        PipelineCreateCensus.Ledger ledger = new PipelineCreateCensus.Ledger();
        ledger.note("graphics", "post-chain", 1, SLOW - 1, SLOW);
        ledger.note("graphics", "post-chain", 1, SLOW, SLOW);
        ledger.note("graphics", "post-chain", 1, 25 * MS, SLOW);
        String line = ledger.summaryLine();
        assertTrue(line.contains("slowCalls=2 slowMs=35"), line);
        assertTrue(line.contains("graphics:post-chain{calls=3 pipelines=3 ms=44 worstMs=25 slow=2/35ms}"), line);
    }

    @Test void summaryCadenceIsThePowersOfTwo() {
        PipelineCreateCensus.Ledger ledger = new PipelineCreateCensus.Ledger();
        assertTrue(ledger.note("compute", "opaque-copy", 1, MS, SLOW), "call 1 prints");
        assertTrue(ledger.note("compute", "opaque-copy", 1, MS, SLOW), "call 2 prints");
        assertFalse(ledger.note("compute", "opaque-copy", 1, MS, SLOW), "call 3 stays quiet");
        assertTrue(ledger.note("compute", "opaque-copy", 1, MS, SLOW), "call 4 prints");
        assertEquals(4L, ledger.totalCalls());
    }

    @Test void worstTracksTheSingleSlowestCreation() {
        PipelineCreateCensus.Ledger ledger = new PipelineCreateCensus.Ledger();
        ledger.note("graphics", "ui", 1, 40 * MS, SLOW);
        ledger.note("graphics", "ui", 1, 4 * MS, SLOW);
        assertTrue(ledger.summaryLine().contains("graphics:ui{calls=2 pipelines=2 ms=44 worstMs=40"), ledger.summaryLine());
    }
}
