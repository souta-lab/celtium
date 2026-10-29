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
    void frameBudgetIsOffUnlessTheArgumentAsksForIt() {
        // The test JVM passes no -Dceltium.vulkan.frameBudget, which is the shipping configuration: a normal run
        // must not pay for the instrument, and this is the only assertion that can catch the default being flipped.
        assertFalse(FrameBudgetTimings.enabled(), "the instrument must stay opt-in");
    }

    @Test
    void otherIsTheUnaccountedRemainder() {
        long[] sample = FrameBudgetTimings.split(16_000_000L, 1_000_000L, 4_000_000L, 300_000L, 200_000L, 0L,
                100_000L, 9_000_000L);

        assertEquals(9, sample.length, "frame, six spans, gpu and other");
        assertEquals(16_000_000L, sample[0], "the interval the player sees");
        assertEquals(9_000_000L, sample[7], "the GPU's own time is reported");
        assertEquals(10_400_000L, sample[8], "16.0 ms frame minus the 5.6 ms the renderer accounted for");
    }

    @Test
    void theGpuTimeIsNeverSubtractedFromTheFrame() {
        // GPU work overlaps the CPU spans that submitted it; subtracting it would double-count the same time.
        long[] withGpu = FrameBudgetTimings.split(20_000_000L, 1_000_000L, 1_000_000L, 0L, 0L, 0L, 0L, 18_000_000L);
        long[] withoutGpu = FrameBudgetTimings.split(20_000_000L, 1_000_000L, 1_000_000L, 0L, 0L, 0L, 0L, 0L);

        assertEquals(withoutGpu[8], withGpu[8], "a GPU-heavy frame's other must not shrink");
        assertEquals(18_000_000L, withGpu[8]);
    }

    @Test
    void aBatchThatContainsTheSubmitCannotMakeOtherNegative() {
        // The documented overlap: submitGeometryBatch may flush, and the flush is also counted as submit. The frame
        // is then shorter than the parts that claim it, and the floor is zero rather than a negative remainder.
        long[] sample = FrameBudgetTimings.split(5_000_000L, 4_000_000L, 4_000_000L, 1_000_000L, 0L, 0L, 0L, 0L);

        assertEquals(0L, sample[8], "the floor, not a negative");
    }

    @Test
    void aFrameWithNothingMeasuredLeavesTheWholeIntervalUnaccounted() {
        long[] sample = FrameBudgetTimings.split(33_000_000L, 0L, 0L, 0L, 0L, 0L, 0L, 0L);

        assertEquals(33_000_000L, sample[8], "all of it is unattributed, which is the honest answer");
    }
}
