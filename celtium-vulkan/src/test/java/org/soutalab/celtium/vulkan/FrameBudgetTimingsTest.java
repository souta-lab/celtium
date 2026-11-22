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

    @Test
    void loopSpanIsOptInLikeTheCadence() {
        // The harness's loopSpan=true turns the pairing on; a normal run must not pay for it.
        assertFalse(FrameBudgetTimings.loopSpanEnabled(), "the loop span must stay opt-in");
    }

    @Test
    void outsideSpanIsPresentToBoundary() {
        assertEquals(7_000_000L, FrameBudgetTimings.outsideSpan(10_000_000L, 3_000_000L),
                "boundary minus the present that preceded it");
        assertEquals(0L, FrameBudgetTimings.outsideSpan(3_000_000L, 3_000_000L), "a zero span is a measurement");
        assertEquals(-1L, FrameBudgetTimings.outsideSpan(10_000_000L, 0L), "no present to pair against");
        assertEquals(-1L, FrameBudgetTimings.outsideSpan(3_000_000L, 10_000_000L),
                "a boundary before its present is not a pair");
    }

    @Test
    void tailSpanIsBoundaryToPresent() {
        assertEquals(2_000_000L, FrameBudgetTimings.tailSpan(5_000_000L, 3_000_000L),
                "present minus the boundary that preceded it");
        assertEquals(-1L, FrameBudgetTimings.tailSpan(5_000_000L, 0L), "no boundary to pair against");
        assertEquals(-1L, FrameBudgetTimings.tailSpan(3_000_000L, 5_000_000L),
                "a present before its boundary is not a pair");
    }

    // The split's index tables, as literals: the production printing path resolves these from
    // FramePassTimings' name table, but resolving them here would initialise that class - it latches its
    // harness switches at initialisation, and PassTimingCoverageTest depends on setting the properties
    // before that happens. The layout is windowMeansNanos': a bracket's mean sits at its index + 1.
    private static final int TERRAIN = 10;
    private static final int SETUP = 11;
    private static final int ENTITIES = 12;
    private static final int PARTICLES = 13;
    private static final int PARTICLES_LIT = 14;
    private static final int SKY = 15;
    private static final int WORLD_PASS = 20;
    private static final int FLUSH = 21;
    private static final int[][] BUCKETS = {
            {TERRAIN}, {SETUP}, {ENTITIES}, {PARTICLES, PARTICLES_LIT}, {SKY}, {16, 17}, {18, 19}};
    private static final int[] WORLD_MEMBERS = {TERRAIN, SETUP, ENTITIES, PARTICLES, PARTICLES_LIT, SKY, FLUSH};

    /** A means row in {@link FramePassTimings}' window layout: index 0 is the frame, bracket i sits at i + 1. */
    private static long[] meansWith(int bracket, long nanos) {
        long[] means = new long[40];
        means[bracket + 1] = nanos;
        return means;
    }

    @Test
    void otherSplitNamesEachBucketFromTheGivenTable() {
        long[] means = meansWith(TERRAIN, 2_000_000L);
        long[] split = FrameBudgetTimings.otherSplit(5_800_000L, 500_000L, means, 300_000L,
                BUCKETS, WORLD_PASS, WORLD_MEMBERS, FLUSH, 0L);
        assertEquals(500_000L, split[0], "outside passes through");
        assertEquals(2_000_000L, split[1], "the terrain bucket is the terrain bracket's mean");
        assertEquals(300_000L, split[10], "the pass residual passes through");
        assertEquals(5_800_000L - 500_000L - 2_000_000L - 300_000L, split[11],
                "unexplained is what no bucket covered");
    }

    @Test
    void otherSplitWorldRestIsWorldPassMinusTheNamedInsideBrackets() {
        long[] means = meansWith(WORLD_PASS, 4_000_000L);
        means[TERRAIN + 1] = 1_500_000L;
        means[ENTITIES + 1] = 1_000_000L;
        long[] split = FrameBudgetTimings.otherSplit(5_800_000L, 0L, means, 0L,
                BUCKETS, WORLD_PASS, WORLD_MEMBERS, FLUSH, 0L);
        assertEquals(1_500_000L, split[9],
                "worldRest keeps only what worldPass holds beyond the bucketed brackets");
    }

    @Test
    void otherSplitWorldRestNeverGoesNegativeWhenBracketsOverlap() {
        long[] means = meansWith(WORLD_PASS, 1_000_000L);
        means[TERRAIN + 1] = 1_500_000L;
        long[] split = FrameBudgetTimings.otherSplit(5_800_000L, 0L, means, 0L,
                BUCKETS, WORLD_PASS, WORLD_MEMBERS, FLUSH, 0L);
        assertEquals(0L, split[9], "an overlap artefact is floored, not reported as negative time");
    }

    @Test
    void otherSplitSumsMultiMemberBuckets() {
        long[] means = meansWith(PARTICLES, 400_000L);
        means[PARTICLES_LIT + 1] = 100_000L;
        means[18 + 1] = 700_000L;
        means[19 + 1] = 200_000L;
        long[] split = FrameBudgetTimings.otherSplit(5_800_000L, 0L, means, 0L,
                BUCKETS, WORLD_PASS, WORLD_MEMBERS, FLUSH, 0L);
        assertEquals(500_000L, split[4], "particles and its lit half are one bucket");
        assertEquals(900_000L, split[7], "gui and hand are one bucket");
    }

    @Test
    void otherSplitFlushIsNettedByTheRecordSpanItContains() {
        long[] means = meansWith(FLUSH, 2_000_000L);
        long[] split = FrameBudgetTimings.otherSplit(5_800_000L, 0L, means, 0L,
                BUCKETS, WORLD_PASS, WORLD_MEMBERS, FLUSH, 1_100_000L);
        assertEquals(900_000L, split[8],
                "flushNet is the flush minus the record span, which the budget already accounted");
        long[] clamped = FrameBudgetTimings.otherSplit(5_800_000L, 0L, means, 0L,
                BUCKETS, WORLD_PASS, WORLD_MEMBERS, FLUSH, 2_500_000L);
        assertEquals(0L, clamped[8], "a record mean above the flush mean floors at zero, never negative");
    }

    @Test
    void otherSplitWorldRestExcludesTheFlushToo() {
        long[] means = meansWith(WORLD_PASS, 4_000_000L);
        means[TERRAIN + 1] = 1_500_000L;
        means[FLUSH + 1] = 500_000L;
        long[] split = FrameBudgetTimings.otherSplit(5_800_000L, 0L, means, 0L,
                BUCKETS, WORLD_PASS, WORLD_MEMBERS, FLUSH, 0L);
        assertEquals(2_000_000L, split[9],
                "the flush is named by its own bucket, so the remainder no longer absorbs it");
    }
}
