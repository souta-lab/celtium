package org.embeddedt.embeddium.impl.render.chunk.occlusion;

import org.soutalab.celtium.harness.HarnessConfig;

import java.util.Arrays;

/**
 * Item 386's supply-cost instrument for the M1.5 gate: how long the occluder-box supply
 * ({@code SectionVisibilityBuilder.computeOccluderBoxes()}, wired into the meshing task)
 * takes per section rebuild, and how often it produces data at all.
 *
 * <p>The plan's supply ceiling is a per-frame figure judged on legs (P2' converts with the
 * rebuild rate), so this instrument keeps the raw per-rebuild distribution: a bounded
 * reservoir of nanosecond samples, read back as p50/p99, plus the two counts the readout
 * names - sections supplied and sections whose built data stayed null. Recording happens
 * on chunk-build threads and only while the harness key {@code occluderBoxStats} is on;
 * with it off the meshing task never calls in, so the supply itself carries no timing
 * overhead. The accessors are read reflectively by the validation fixture, the same
 * hand-off {@link BeVisibilityProbe}'s counters use.
 */
public final class OccluderSupplyStats {
    private OccluderSupplyStats() {
    }

    /** The supply-cost switch, read once. Off, the meshing task does not even time the call. */
    public static final boolean ENABLED = HarnessConfig.bool("occluderBoxStats", false);

    /** Kept samples; older ones are overwritten, so percentiles describe recent rebuilds. */
    private static final int RESERVOIR_SIZE = 4096;

    private static final long[] SAMPLES = new long[RESERVOIR_SIZE];
    private static int sampleCursor;
    private static int sampleCount;
    private static int sections;
    private static int nullSections;

    /**
     * Records one section build's supply outcome. Called from meshing tasks, so it is
     * synchronized; the critical section is a few field writes against millisecond-scale
     * builds.
     */
    public static synchronized void record(long nanos, boolean hasData) {
        sections++;
        if (!hasData) {
            nullSections++;
        }
        SAMPLES[sampleCursor] = nanos;
        sampleCursor = (sampleCursor + 1) % RESERVOIR_SIZE;
        if (sampleCount < RESERVOIR_SIZE) {
            sampleCount++;
        }
    }

    /** Section builds recorded since the instrument started. */
    public static synchronized int occluderBoxSections() {
        return sections;
    }

    /** Recorded builds whose occluder data stayed null (empty sections). */
    public static synchronized int occluderBoxNull() {
        return nullSections;
    }

    /** Median per-rebuild supply time in nanoseconds, or 0 before the first sample. */
    public static long occluderBoxNanosP50() {
        return percentile(50);
    }

    /** 99th-percentile per-rebuild supply time in nanoseconds, or 0 before the first sample. */
    public static long occluderBoxNanosP99() {
        return percentile(99);
    }

    private static long percentile(int p) {
        long[] copy;
        synchronized (OccluderSupplyStats.class) {
            if (sampleCount == 0) {
                return 0;
            }
            copy = Arrays.copyOf(SAMPLES, sampleCount);
        }
        Arrays.sort(copy);
        int rank = (int) Math.ceil((p / 100.0) * copy.length);
        return copy[Math.max(0, Math.min(copy.length - 1, rank - 1))];
    }

    /** Test hook: clears every counter and the reservoir. */
    static synchronized void resetForTest() {
        Arrays.fill(SAMPLES, 0);
        sampleCursor = 0;
        sampleCount = 0;
        sections = 0;
        nullSections = 0;
    }
}
