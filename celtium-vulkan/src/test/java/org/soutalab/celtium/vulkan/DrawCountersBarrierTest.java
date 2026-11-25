package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DrawCountersBarrierTest {

    @BeforeEach
    void clearBarriers() {
        DrawCounters.clearBarriersForTests();
    }

    @Test
    void percentileOfNoSamplesIsZero() {
        assertEquals(0L, DrawCounters.percentileOf(new long[4096], 0L, 0L, 50));
        assertEquals(0L, DrawCounters.barrierPercentile(95));
    }

    @Test
    void percentileOfKnownHistogram() {
        long[] buckets = new long[4096];
        buckets[10] = 3L;
        buckets[20] = 1L;
        // Samples sorted: 10, 10, 10, 20. Rank is ceil(percent/100 * n).
        assertEquals(10L, DrawCounters.percentileOf(buckets, 0L, 4L, 25));
        assertEquals(10L, DrawCounters.percentileOf(buckets, 0L, 4L, 50));
        assertEquals(10L, DrawCounters.percentileOf(buckets, 0L, 4L, 75));
        assertEquals(20L, DrawCounters.percentileOf(buckets, 0L, 4L, 100));
    }

    @Test
    void percentileAboveLastBucketReportsOverflowValue() {
        long[] buckets = new long[4096];
        buckets[3] = 1L;
        // One sample in the buckets, one above them: the top rank lands on the overflow value.
        assertEquals(3L, DrawCounters.percentileOf(buckets, 5000L, 2L, 50));
        assertEquals(5000L, DrawCounters.percentileOf(buckets, 5000L, 2L, 100));
    }

    @Test
    void sampleFrameBuildsTheDistribution() {
        for (int i = 0; i < 5; i++) {
            DrawCounters.notePipelineBarrier();
        }
        DrawCounters.sampleFrame(1L);
        for (int i = 0; i < 3; i++) {
            DrawCounters.notePipelineBarrier();
        }
        DrawCounters.sampleFrame(2L);
        DrawCounters.sampleFrame(3L);
        // Samples: 5, 3, 0.
        assertEquals(3L, DrawCounters.barrierSampleCount());
        assertEquals(8L, DrawCounters.pipelineBarriers());
        assertEquals(5L, DrawCounters.barrierMax());
        assertEquals(3L, DrawCounters.barrierPercentile(50));
        assertEquals(5L, DrawCounters.barrierPercentile(100));
        String summary = DrawCounters.barrierSummary();
        assertTrue(summary.contains("n=3"), summary);
        assertTrue(summary.contains("total=8"), summary);
    }

    @Test
    void sampleFrameIgnoresRepeatedAndNonPositiveFrames() {
        DrawCounters.notePipelineBarrier();
        DrawCounters.sampleFrame(0L);
        DrawCounters.sampleFrame(7L);
        DrawCounters.sampleFrame(7L);
        DrawCounters.sampleFrame(3L);
        assertEquals(1L, DrawCounters.barrierSampleCount());
    }

    @Test
    void sampleAboveHistogramRangeReportsTheSampleAsMax() {
        for (int i = 0; i < 5000; i++) {
            DrawCounters.notePipelineBarrier();
        }
        DrawCounters.sampleFrame(1L);
        assertEquals(5000L, DrawCounters.barrierMax());
        assertEquals(5000L, DrawCounters.barrierPercentile(99));
    }
}
