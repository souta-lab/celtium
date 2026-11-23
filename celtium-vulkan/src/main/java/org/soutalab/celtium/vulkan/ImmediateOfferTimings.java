package org.soutalab.celtium.vulkan;

/**
 * The offer path's own timings: how long a {@code submitImmediateBatch} call takes, where inside it the time
 * goes (the offer stages), and what the slow calls carried (item 704, R5 seam - the immediateSubmit/offerStage
 * instruments named as next-seam candidate 1 in the R3 record).
 * <p>
 * The state used to be declared in three places inside {@code VulkanTerrainGpuBackend} - the counters beside
 * the offer fields, the reached histogram beside the draw-site census, and the stage marks and deltas inside
 * the frame-target zone - while every writer sat in the submit wrapper and the batch body. It moves here as
 * one owner; the backend keeps the door ({@code offerTimings}) and the diagnostics read through it, so the
 * printed lines are unchanged. The per-call accumulation and the stage-delta folding move with the state, as
 * methods only this class's fields decide.
 */
public final class ImmediateOfferTimings {

    /** The checkpoint the offer path last passed, so an unnamed refusal can still say where it stopped. */
    int offerStage;

    /** Total time spent inside submitImmediateBatch, so the hot path's cost is measured rather than asserted. */
    long immediateSubmitNanos;
    /**
     * Baseline at the first offer inside the world pass: menu/startup batches (atlas stitch, model init)
     * hit the offer path thousands of times while the JIT is still cold, which inflates the cumulative
     * immediateSubmitAvgNs far above what a steady-state call costs. The world-scoped average below
     * subtracts the counters at the moment the world pass first activates.
     */
    long immediateSubmitBaselineNanos = -1;
    long immediateSubmitBaselineOffered;
    /** Calls whose body took over 200µs: separates steady per-call cost from spike pollution in the average. */
    int immediateSubmitTailCalls;
    /** Histogram-ish spread: under 5µs / 5-50 / 50-200 / tail, so the average can be read against the mass. */
    int immediateSubmitUnder5, immediateSubmit5to50, immediateSubmit50to200;
    /**
     * What a tail call carried: a queue flush (immediateGroupsFlushed moved), a lazy texture mirror supply
     * (lazyMirrorSupplies moved), both, or neither (GC pause, JIT deopt, shader compile - the residue).
     */
    int tailCarriedFlush, tailCarriedSupply, tailCarriedBoth, tailCarriedNeither;
    private int submitFlushesAtEntry, submitSuppliesAtEntry;

    /** Offers that reached each stage index; printed as {@code offerStages=[1:..,2:..]}. */
    final int[] offerStageReached = new int[16];

    /**
     * Timestamps of the offer stages of the current batch, in nanoseconds.
     * <p>
     * Sized to the stage numbering, which runs to 11: the first version had nine entries and an index out of range for the
     * last two stages, so the measurement stopped exactly where the queueing work begins.
     */
    private final long[] offerStageMarks = new long[16];

    /** Per-stage totals and counts: [stage][0] nanos, [stage][1] batches that reached it. */
    private final long[][] offerStageDeltas = new long[16][2];

    /** {@return the per-offer-stage average nanoseconds, for the batch-timing summary}. */
    String offerStageNanosSummary() {
        StringBuilder text = new StringBuilder(" offerStages[nanosPerReached");
        for (int stage = 1; stage < this.offerStageDeltas.length; stage++) {
            long[] entry = this.offerStageDeltas[stage];
            if (entry[1] > 0L) {
                text.append(" s").append(stage).append('=').append(entry[0] / entry[1]);
            }
        }
        return text.append(']').toString();
    }

    /**
     * Sets the offer stage and counts how many offers reached it (BH-15's open question).
     * <p>
     * The stage field alone reaches a report only when a refusal names it, so an offer that stops between the offer
     * increment and the acceptance increment leaves no trace: the terrain legs read {@code offTargetOffered=793} with
     * {@code acceptedForOtherTargets=7} and every refusal counter at zero, which is a contradiction no existing
     * counter resolves. This histogram is the counter that resolves it - the highest stage with a non-zero count is
     * where the missing offers stop.
     */
    void celtium$offerStage(int stage) {
        if (VulkanTerrainGpuBackend.BATCH_TIMINGS && stage > 0) {
            this.offerStageMarks[stage - 1] = System.nanoTime();
        }
        this.offerStage = stage;
        if (stage >= 0 && stage < this.offerStageReached.length) {
            this.offerStageReached[stage]++;
        }
    }

    /**
     * Captures the world-pass baseline at the first offer inside it, so the world-scoped average can subtract
     * the cold menu/startup offers. Called on every offer; the guard is the baseline's own sentinel.
     */
    void captureBaselineIfFirst(long offered) {
        if (this.immediateSubmitBaselineNanos < 0) {
            this.immediateSubmitBaselineNanos = this.immediateSubmitNanos;
            this.immediateSubmitBaselineOffered = offered;
        }
    }

    /** Remembers what the flush and supply counters read at the offer's entry, for the tail classification. */
    void noteSubmitEntry(int groupsFlushed, int mirrorSupplies) {
        this.submitFlushesAtEntry = groupsFlushed;
        this.submitSuppliesAtEntry = mirrorSupplies;
    }

    /**
     * Adds one call's measured time and files it in the spread. {@return whether the call was a tail call
     * (over 200us), so the caller can add the same time to the statistics' own tail total}.
     */
    boolean recordSubmitNanos(long submitNanos, int groupsFlushedNow, int mirrorSuppliesNow) {
        this.immediateSubmitNanos += submitNanos;
        if (submitNanos > 200_000) {
            this.immediateSubmitTailCalls++;
            boolean carriedFlush = groupsFlushedNow != this.submitFlushesAtEntry;
            boolean carriedSupply = mirrorSuppliesNow != this.submitSuppliesAtEntry;
            if (carriedFlush && carriedSupply) this.tailCarriedBoth++;
            else if (carriedFlush) this.tailCarriedFlush++;
            else if (carriedSupply) this.tailCarriedSupply++;
            else this.tailCarriedNeither++;
            return true;
        } else if (submitNanos > 50_000) {
            this.immediateSubmit50to200++;
        } else if (submitNanos > 5_000) {
            this.immediateSubmit5to50++;
        } else {
            this.immediateSubmitUnder5++;
        }
        return false;
    }

    /** Folds the finished batch's stage marks into the per-stage totals and clears them for the next batch. */
    void foldOfferStageDeltas() {
        long[][] deltas = this.offerStageDeltas;
        for (int stage = 1; stage < this.offerStageMarks.length; stage++) {
            long from = this.offerStageMarks[stage - 1];
            long to = this.offerStageMarks[stage];
            if (from > 0L && to >= from) {
                deltas[stage][0] += to - from;
                deltas[stage][1]++;
            }
            this.offerStageMarks[stage - 1] = 0L;
        }
        this.offerStageMarks[this.offerStageMarks.length - 1] = 0L;
    }
}
