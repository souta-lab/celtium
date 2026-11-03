package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The arithmetic behind {@code batchDist}, checked without a GPU.
 * <p>
 * The instrument exists because every accept-path number this project has quoted is a mean, and a mean over a short
 * interval on a host with an unrelated process holding a core is not a cost. These tests pin the two properties that
 * make the distribution readable: a percentile is an <em>upper bound</em> of the bucket it lands in (never an
 * interpolation between buckets, which would invent precision the histogram does not have), and a distribution whose
 * mass is in one bucket reports that bucket as its median no matter how long its tail is.
 * <p>
 * What they cannot prove: that any measured offer took the time the buckets say. The buckets are filled from the
 * renderer's own clock, and a green test here is not evidence about a frame.
 */
class BatchDurationDistributionTest {

    /** The bucket a duration lands in, mirroring {@code celtium$noteBatchDuration}. */
    private static int bucketOf(long nanos) {
        return nanos <= 0L ? 0 : 64 - Long.numberOfLeadingZeros(nanos);
    }

    @Test
    void aBucketCoversTheHalfOpenRangeBelowItsOwnPowerOfTwo() {
        // The mapping is the whole contract: bucket b is [2^(b-1), 2^b), so the reported bound is exact.
        assertEquals(0, bucketOf(0L), "zero is its own bucket");
        assertEquals(1, bucketOf(1L), "1 ns is the first positive bucket");
        assertEquals(10, bucketOf(1023L), "1023 ns is still below 1024");
        assertEquals(11, bucketOf(1024L), "1024 ns opens the next bucket");
        assertEquals(11, bucketOf(2047L), "2047 ns is the top of that bucket");
        assertEquals(21, bucketOf(1_048_576L), "a millisecond is bucket 21");
    }

    @Test
    void thePercentileIsTheUpperBoundOfTheBucketItLandsIn() {
        long[] buckets = new long[65];
        // 98 offers at 300 ns (bucket 9, upper bound 512) and two at 100 us (bucket 17, upper bound 131072).
        buckets[bucketOf(300L)] = 98L;
        buckets[bucketOf(100_000L)] = 2L;

        assertEquals(512L, VulkanTerrainGpuBackend.celtium$batchDistPercentile(buckets, 100L, 50),
                "the median is the 300 ns bucket's upper bound, not the mean the tail would produce");
        assertEquals(512L, VulkanTerrainGpuBackend.celtium$batchDistPercentile(buckets, 100L, 95),
                "95 of 100 samples are still in the fast bucket");
        assertEquals(131_072L, VulkanTerrainGpuBackend.celtium$batchDistPercentile(buckets, 100L, 99),
                "the tail is named by the bucket it is in, not by its own value");
    }

    @Test
    void aTailDoesNotMoveTheMedian() {
        // The property the instrument is for: the mean of this population is ~1.3 us, the median is 512 ns.
        long[] buckets = new long[65];
        buckets[bucketOf(300L)] = 9_800L;
        buckets[bucketOf(100_000L)] = 200L;

        assertEquals(512L, VulkanTerrainGpuBackend.celtium$batchDistPercentile(buckets, 10_000L, 50),
                "a 2% tail of 100 us stalls must not move the median");
        assertEquals(131_072L, VulkanTerrainGpuBackend.celtium$batchDistPercentile(buckets, 10_000L, 99),
                "and it must not be hidden either: the 99th percentile names the stall's bucket");
    }

    @Test
    void anEmptyHistogramAnswersZeroRatherThanGuessing() {
        long[] buckets = new long[65];
        assertEquals(0L, VulkanTerrainGpuBackend.celtium$batchDistPercentile(buckets, 0L, 50),
                "no samples is not a percentile");
        assertEquals(0L, VulkanTerrainGpuBackend.celtium$batchDistPercentile(buckets, 10L, 0),
                "a zero percentile is not a question the histogram answers");
    }

    @Test
    void theLargestBucketDoesNotOverflowIntoANegativeBound() {
        long[] buckets = new long[65];
        buckets[63] = 1L;
        assertEquals(Long.MAX_VALUE, VulkanTerrainGpuBackend.celtium$batchDistPercentile(buckets, 1L, 50),
                "2^63 does not fit a positive long, so the bound saturates instead of going negative");
        assertTrue(VulkanTerrainGpuBackend.celtium$batchDistPercentile(buckets, 1L, 50) > 0L);
    }
}
