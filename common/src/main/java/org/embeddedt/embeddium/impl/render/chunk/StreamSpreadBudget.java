package org.embeddedt.embeddium.impl.render.chunk;

/**
 * The token bucket that paces chunk-build result processing when stream spreading is on (QUEUE 728, F1).
 * <p>
 * The unit is measured microseconds of render-thread work (one slice of
 * {@code RenderSectionManager.processChunkBuildResults}: the mesh upload plus the commit that follows it). The
 * bucket refills at {@code budgetMicros} per 16.666 ms of wall time and holds at most twice the budget, so light
 * frames bank credit that a streaming burst may spend, and a heavy slice puts the bucket into debt that later
 * refills must first repay - the carry-over is literal, in both directions. Frame boundaries play no part: the
 * bucket is refilled from the wall clock whenever it is consulted, so a frame that consults it twice (the terrain
 * pass uploads in {@code stUpload} and again for immediate updates) does not receive the budget twice.
 * <p>
 * Pure and clock-injected so the pacing arithmetic is testable without a game.
 */
public final class StreamSpreadBudget {
    /** Nanoseconds in the nominal frame the refill rate is expressed against. */
    static final long REFILL_WINDOW_NANOS = 16_666_000L;

    private final long budgetMicros;
    private final long capacityMicros;
    private long tokensMicros;
    private long lastRefillNanos;

    public StreamSpreadBudget(long budgetMicros, long nowNanos) {
        if (budgetMicros <= 0) {
            throw new IllegalArgumentException("budgetMicros must be positive: " + budgetMicros);
        }
        this.budgetMicros = budgetMicros;
        this.capacityMicros = budgetMicros * 2L;
        this.tokensMicros = this.capacityMicros;
        this.lastRefillNanos = nowNanos;
    }

    /** Adds the credit the elapsed wall time earned, capped at the capacity. Debt is repaid before credit accrues. */
    public void refill(long nowNanos) {
        long elapsedNanos = nowNanos - this.lastRefillNanos;
        if (elapsedNanos <= 0) {
            return;
        }
        this.lastRefillNanos = nowNanos;
        // The elapsed time is capped before the multiply so arbitrarily long pauses (menu screens, world
        // switches) cannot overflow: a thousand windows of credit is already far more than the capacity plus
        // any debt a real slice can create, and the min below discards the excess anyway.
        long cappedElapsedNanos = Math.min(elapsedNanos, REFILL_WINDOW_NANOS * 1000L);
        long earnedMicros = cappedElapsedNanos * this.budgetMicros / REFILL_WINDOW_NANOS;
        this.tokensMicros = Math.min(this.capacityMicros, this.tokensMicros + earnedMicros);
    }

    /** {@return whether any credit remains; a call that already processed a slice this frame must stop at zero}. */
    public boolean hasCredit() {
        return this.tokensMicros > 0L;
    }

    /** Debits the measured cost of one processed slice. May drive the bucket negative (debt). */
    public void debit(long spentMicros) {
        if (spentMicros > 0L) {
            this.tokensMicros -= spentMicros;
        }
    }

    /** {@return the current credit in microseconds; negative is debt}. */
    public long tokensMicros() {
        return this.tokensMicros;
    }

    /** {@return the per-window budget this bucket refills at}. */
    public long budgetMicros() {
        return this.budgetMicros;
    }

    /** {@return the most credit the bucket can hold}. */
    public long capacityMicros() {
        return this.capacityMicros;
    }
}
