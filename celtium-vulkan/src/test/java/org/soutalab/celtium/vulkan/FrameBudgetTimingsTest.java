package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * The arithmetic behind {@code CELTIUM_FRAME_BUDGET}, checked without a GPU.
 * <p>
 * What these tests can and cannot prove: they prove the subtraction that produces {@code other} never goes negative
 * and never subtracts the GPU's own time, and that the instrument is off when no argument asked for it. They prove
 * nothing about a measured frame - the spans themselves are read from the renderer, and a green test here is not
 * evidence about any driver.
 */
class FrameBudgetTimingsTest {

    @Test
    void presentIntervalIsOptInAndSeparateFromTheSplit() {
        // The harness's presentInterval=true turns on the cadence alone; a normal run must not pay for it, and this
        // is the only assertion that catches the default being flipped.
        assertFalse(FrameBudgetTimings.presentIntervalEnabled(), "the cadence must stay opt-in");
    }

    @Test
    void frameBudgetIsOffUnlessTheArgumentAsksForIt() {
        // The test JVM runs no harness, which is the shipping configuration: a normal run
        // must not pay for the instrument, and this is the only assertion that can catch the default being flipped.
        assertFalse(FrameBudgetTimings.enabled(), "the instrument must stay opt-in");
    }

    @Test
    void aRingIsReadThroughItsOwnCursor() {
        // The present-interval ring advances with presentIntervals, not with the frame ring's `written`. Reading it
        // through the frame cursor is not an error - it returns slots the other ring wrote, which are zero - and that
        // is what printed intervalMs[0.0/0.0/0.0] for a run with 211 presents. This pins the cursor rule.
        long[] ring = new long[8];
        ring[0] = 5_000_000L;
        ring[1] = 6_000_000L;
        long[] correct = FrameBudgetTimings.window(ring, 2, 2);
        assertEquals(5_000_000L, correct[0], "the present ring's own cursor reads the two samples it wrote");
        assertEquals(6_000_000L, correct[1]);
        long[] wrong = FrameBudgetTimings.window(ring, 2, 0);
        assertEquals(0L, wrong[0], "the frame cursor reads slots 6 and 7, which the present ring never wrote");
        assertEquals(0L, wrong[1]);
    }

    @Test
    void otherIsTheUnaccountedRemainder() {
        long[] sample = FrameBudgetTimings.split(16_000_000L, 1_000_000L, 4_000_000L, 300_000L, 200_000L, 0L,
                100_000L, 9_000_000L, 2_000_000L);

        assertEquals(10, sample.length, "frame, six spans, gpu, terrainGpu and other");
        assertEquals(16_000_000L, sample[0], "the interval the player sees");
        assertEquals(9_000_000L, sample[7], "the GPU's own window time is reported");
        assertEquals(2_000_000L, sample[8], "the GPU's own terrain time is reported");
        assertEquals(10_400_000L, sample[9], "16.0 ms frame minus the 5.6 ms the renderer accounted for");
    }

    @Test
    void theGpuTimeIsNeverSubtractedFromTheFrame() {
        // GPU work overlaps the CPU spans that submitted it; subtracting it would double-count the same time.
        // The terrain figure follows the same rule: a duration on the GPU's timeline, not a CPU span.
        long[] withGpu = FrameBudgetTimings.split(20_000_000L, 1_000_000L, 1_000_000L, 0L, 0L, 0L, 0L, 18_000_000L,
                12_000_000L);
        long[] withoutGpu = FrameBudgetTimings.split(20_000_000L, 1_000_000L, 1_000_000L, 0L, 0L, 0L, 0L, 0L, 0L);

        assertEquals(withoutGpu[9], withGpu[9], "a GPU-heavy frame's other must not shrink");
        assertEquals(18_000_000L, withGpu[9]);
    }

    @Test
    void aTerrainFigureLongerThanTheFrameIsReportedNotClamped() {
        // The terrain readback is the previous frame's measurement filed against this one, and a GPU working
        // through a backlog can spend longer on terrain than this frame's whole interval. The figure is a
        // measurement, so it stands as read; only `other` is floored, and only at zero.
        long[] sample = FrameBudgetTimings.split(10_000_000L, 1_000_000L, 1_000_000L, 0L, 0L, 0L, 0L, 0L,
                25_000_000L);

        assertEquals(25_000_000L, sample[8], "the terrain measurement stands as read back");
        assertEquals(8_000_000L, sample[9], "other is still the frame minus the CPU spans alone");
    }

    @Test
    void aBatchThatContainsTheSubmitCannotMakeOtherNegative() {
        // The documented overlap: submitGeometryBatch may flush, and the flush is also counted as submit. The frame
        // is then shorter than the parts that claim it, and the floor is zero rather than a negative remainder.
        long[] sample = FrameBudgetTimings.split(5_000_000L, 4_000_000L, 4_000_000L, 1_000_000L, 0L, 0L, 0L, 0L, 0L);

        assertEquals(0L, sample[9], "the floor, not a negative");
    }

    @Test
    void aFrameWithNothingMeasuredLeavesTheWholeIntervalUnaccounted() {
        long[] sample = FrameBudgetTimings.split(33_000_000L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L);

        assertEquals(33_000_000L, sample[9], "all of it is unattributed, which is the honest answer");
    }
}
