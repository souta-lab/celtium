package org.soutalab.celtium.vulkan;

/**
 * Counts the draws actually recorded, which is the number the batching work is judged by.
 *
 * <p>Batch counts and group counts were both being read as "draws", and neither is: a group is what the renderer keeps,
 * a draw is what the command buffer receives. Reducing 1:1 translation means fewer commands, so the command is what is
 * counted here - one increment where {@code vkCmdDrawIndexed} or {@code vkCmdDrawIndirect} is actually issued, and one
 * line per 200 frames so a leg reports it without any new switch.
 */
public final class DrawCounters {

    // Plain longs: every writer and every reader of these counters runs on the render thread (note* from the
    // recording path, report from the same thread's frame boundary), so the atomics only added a locked instruction to
    // counters that are incremented once per group, pipeline bind and descriptor bind.
    private static long INDEXED;
    private static long INDIRECT;
    private static long NON_INDEXED;
    private static long UPLOADS;
    private static long UPLOAD_BYTES;
    private static long lastReportedUploads;
    private static long lastReportedUploadBytes;

    private static long lastReportedIndexed;
    private static long lastReportedFrame = -1L;

    // vkCmdPipelineBarrier has no single wrapper (76 direct call sites), so the count is taken at the call sites
    // themselves, the same way the draws above are. The per-frame distribution is what the barrier work is judged
    // by: a mean alone hides whether the barriers sit in a few spike frames or in every frame.
    private static long PIPELINE_BARRIERS;
    private static long lastReportedBarriers;
    private static long lastSampledBarriers;
    private static long lastSampledFrame = -1L;
    private static long barrierSamples;
    private static long barrierMax;
    private static long barrierOverflowSamples;
    private static final int BARRIER_HISTOGRAM_BUCKETS = 4096;
    private static final long[] BARRIER_HISTOGRAM = new long[BARRIER_HISTOGRAM_BUCKETS];

    /** Per-frame work counters for the KPI: what the flush records and how often state is rebound. */
    private static long IMMEDIATE_GROUPS;
    private static long PIPELINE_BINDS;
    private static long DESCRIPTOR_BINDS;

    /** One group of the immediate window's draw list was recorded. */
    public static void noteImmediateGroup() {
        IMMEDIATE_GROUPS++;
    }

    /** One graphics pipeline was bound while recording. */
    public static void notePipelineBind() {
        PIPELINE_BINDS++;
    }

    /** One descriptor set was bound while recording. */
    public static void noteDescriptorBind() {
        DESCRIPTOR_BINDS++;
    }

    public static long immediateGroups() {
        return IMMEDIATE_GROUPS;
    }

    public static long pipelineBinds() {
        return PIPELINE_BINDS;
    }

    public static long descriptorBinds() {
        return DESCRIPTOR_BINDS;
    }

    private DrawCounters() {
    }

    /** One {@code vkCmdDrawIndexed} was recorded. */
    public static void noteIndexed() {
        INDEXED++;
    }

    /** One {@code vkCmdDrawIndirect} / {@code vkCmdDrawIndexedIndirect} was recorded. */
    public static void noteIndirect() {
        INDIRECT++;
    }

    /** One terrain mesh upload (vertex or index range written into its arena). */
    public static void noteUpload(long bytes) {
        UPLOADS++;
        UPLOAD_BYTES += bytes;
    }

    /** One non-indexed {@code vkCmdDraw} was recorded. */
    public static void noteNonIndexed() {
        NON_INDEXED++;
    }

    /** One {@code vkCmdPipelineBarrier} was recorded. */
    public static void notePipelineBarrier() {
        PIPELINE_BARRIERS++;
    }

    /** Total {@code vkCmdPipelineBarrier} calls recorded since startup. */
    public static long pipelineBarriers() {
        return PIPELINE_BARRIERS;
    }

    /** Number of per-frame barrier samples taken by {@link #sampleFrame(long)}. */
    public static long barrierSampleCount() {
        return barrierSamples;
    }

    /** Largest per-frame barrier count sampled so far. */
    public static long barrierMax() {
        return barrierMax;
    }

    /**
     * Takes one per-frame sample of the barrier count at a frame boundary: the barriers recorded since the
     * previous sample become one entry of the distribution. Called from the same frame-boundary point as
     * {@link #reportIfDue(long)}, so a frame whose buffer never finished takes no sample and its barriers fold
     * into the next sample instead of being lost.
     *
     * @param frameId the frame that just ended
     */
    public static void sampleFrame(long frameId) {
        if (frameId <= 0L || frameId <= lastSampledFrame) {
            return;
        }
        lastSampledFrame = frameId;
        long total = PIPELINE_BARRIERS;
        long delta = total - lastSampledBarriers;
        lastSampledBarriers = total;
        barrierSamples++;
        if (delta > barrierMax) {
            barrierMax = delta;
        }
        if (delta < BARRIER_HISTOGRAM_BUCKETS) {
            BARRIER_HISTOGRAM[(int) delta]++;
        } else {
            barrierOverflowSamples++;
        }
    }

    /** The {@code percent}-th percentile of the per-frame barrier samples (0 when no sample exists). */
    public static long barrierPercentile(int percent) {
        return percentileOf(BARRIER_HISTOGRAM, barrierMax, barrierSamples, percent);
    }

    /**
     * Percentile over a bucket histogram whose values are the bucket indices, with every sample above the last
     * bucket reported as {@code overflowValue}. The rank is the smallest value whose cumulative count reaches
     * {@code ceil(percent/100 * samples)}.
     */
    static long percentileOf(long[] buckets, long overflowValue, long samples, int percent) {
        if (samples <= 0L) {
            return 0L;
        }
        long rank = (long) Math.ceil((percent / 100.0d) * (double) samples);
        if (rank < 1L) {
            rank = 1L;
        }
        long seen = 0L;
        for (int bucket = 0; bucket < buckets.length; bucket++) {
            seen += buckets[bucket];
            if (seen >= rank) {
                return bucket;
            }
        }
        return overflowValue;
    }

    /** The per-frame barrier distribution in the one-line form the diagnostics line splices in. */
    public static String barrierSummary() {
        return "barriersPerFrame=[n=" + barrierSamples
                + " p50=" + barrierPercentile(50)
                + " p95=" + barrierPercentile(95)
                + " p99=" + barrierPercentile(99)
                + " max=" + barrierMax
                + " total=" + PIPELINE_BARRIERS + "]";
    }

    /**
     * Restarts the per-frame distribution from the current total, so a reader that brackets a measured window
     * (the validation fixture at its warm-window edges) reads the window's own distribution instead of the run's.
     * The cumulative total and the 200-frame line's window keep running; only the samples restart.
     */
    public static void resetBarrierSamples() {
        java.util.Arrays.fill(BARRIER_HISTOGRAM, 0L);
        barrierSamples = 0L;
        barrierMax = 0L;
        barrierOverflowSamples = 0L;
        lastSampledBarriers = PIPELINE_BARRIERS;
        lastSampledFrame = -1L;
    }

    static void clearBarriersForTests() {
        PIPELINE_BARRIERS = 0L;
        lastReportedBarriers = 0L;
        lastSampledBarriers = 0L;
        lastSampledFrame = -1L;
        barrierSamples = 0L;
        barrierMax = 0L;
        barrierOverflowSamples = 0L;
        java.util.Arrays.fill(BARRIER_HISTOGRAM, 0L);
    }

    /**
     * Prints the draw rate when due.
     *
     * @param frameId the frame the count belongs to
     */
    public static void reportIfDue(long frameId) {
        if (!PeriodicLog.ENABLED || frameId <= 0L || frameId % 200L != 0L || frameId == lastReportedFrame) {
            return;
        }
        long indexed = INDEXED;
        long frames = lastReportedFrame <= 0L ? frameId : frameId - lastReportedFrame;
        long delta = indexed - lastReportedIndexed;
        lastReportedIndexed = indexed;
        lastReportedFrame = frameId;
        long uploads = UPLOADS;
        long uploadBytes = UPLOAD_BYTES;
        long uploadDelta = uploads - lastReportedUploads;
        long bytesDelta = uploadBytes - lastReportedUploadBytes;
        lastReportedUploads = uploads;
        lastReportedUploadBytes = uploadBytes;
        long barriers = PIPELINE_BARRIERS;
        long barrierDelta = barriers - lastReportedBarriers;
        lastReportedBarriers = barriers;
        System.out.println("[Celtium] CELTIUM_DRAWS frame=" + frameId
                + " indexed=" + indexed + " indirect=" + INDIRECT + " nonIndexed=" + NON_INDEXED
                + " perFrame=" + (frames > 0 ? (delta / frames) : 0)
                + " uploadsPerFrame=" + (frames > 0 ? (uploadDelta / frames) : 0)
                + " uploadKBPerFrame=" + (frames > 0 ? (bytesDelta / frames / 1024L) : 0)
                + " barriersPerFrame=" + (frames > 0 ? (barrierDelta / frames) : 0));
    }
}
