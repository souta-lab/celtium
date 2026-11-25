package org.soutalab.celtium.vulkan;

/**
 * The per-frame upload byte budget's ledger (S3R-1, design OVERHAUL-S3-REMAINDER-DESIGN §2.2 layer 1):
 * a device-free account of how many bytes each frame window's upload flushes carried, and how much of
 * that ran over the frame's byte budget.
 * <p>
 * The window is keyed by a caller-supplied frame key and rolls lazily, the {@link TerrainFrameBudget}
 * {@code beginFrame} shape: the first accounting call with a new key starts a fresh window, and calls
 * sharing a key share one budget. The caller owns what a "frame" is; this class only does arithmetic.
 * <p>
 * Layer 1 is accounting and judgment only. {@link #noteFlushed(long, long)} never refuses bytes: the
 * flush still records every copy it was given, and the part of a window's bytes beyond the budget is
 * counted as overrun ({@link #totalOverrunBytes()}) instead of being deferred. The judgment and
 * deferral accounts exist for the enforcement phase (design §2.2 layer 2, producer-side deferral):
 * {@link #wouldExceed(long, long)} is the query a producer asks before issuing, and
 * {@link #noteDeferred(long)} / {@link #noteDeferredIssued(long)} keep the deferred backlog's account
 * against its cap, counting a forced issue in {@link #deferredCapForce()} whenever the cap refuses a
 * deferral. Nothing in this landing calls the deferral side from production code, so those counters
 * read 0 until layer 2 lands; the API is here so that landing does not have to reshape the ledger.
 * <p>
 * The budget is a byte count per window; {@code <= 0} means unlimited (the A/B escape the design's
 * {@code -Dceltium.vulkan.uploadBudgetMiB=0} names). The deferred cap is likewise {@code <= 0} for no
 * cap. Not thread-safe by design: uploads are recorded and flushed on the game thread, the same
 * confinement the counters in {@code VulkanContext} already assume.
 */
public final class UploadBudgetLedger {
    /** The design's default frame budget for an integrated GPU: 4 MiB per frame. */
    public static final long DEFAULT_INTEGRATED_BUDGET_BYTES = 4L << 20;
    /** The design's default frame budget for a discrete (or other) GPU: 16 MiB per frame. */
    public static final long DEFAULT_DISCRETE_BUDGET_BYTES = 16L << 20;
    /** The design's cap on deferred, not-yet-issued upload bytes: 16 MiB (design §2.2 layer 2). */
    public static final long DEFAULT_DEFERRED_CAP_BYTES = 16L << 20;

    private final long budgetBytes;
    private final long deferredCapBytes;

    /** The frame key the current window belongs to; {@code Long.MIN_VALUE} before the first window. */
    private long windowKey = Long.MIN_VALUE;
    private long windowBytes;
    private long windowOverrunBytes;

    private long totalFlushedBytes;
    private long totalOverrunBytes;

    // The layer-2 deferral account: bytes a producer has held back and not yet issued, the cumulative
    // deferred total and count the summary reports, and the forced issues the cap has caused.
    private long deferredOutstandingBytes;
    private long deferredTotalBytes;
    private long deferredCount;
    private long deferredCapForce;

    public UploadBudgetLedger(long budgetBytes, long deferredCapBytes) {
        this.budgetBytes = budgetBytes;
        this.deferredCapBytes = deferredCapBytes;
    }

    /**
     * Accounts bytes a flush actually recorded under {@code frameKey}'s window, rolling the window
     * first when the key is new.
     *
     * @return the overrun this call adds: the part of the window's bytes that now sits beyond the
     * budget and did not before the call (0 while the window is within budget, and always 0 when the
     * budget is unlimited)
     */
    public long noteFlushed(long frameKey, long bytes) {
        if (frameKey != this.windowKey) {
            this.windowKey = frameKey;
            this.windowBytes = 0L;
            this.windowOverrunBytes = 0L;
        }
        long overrun = 0L;
        if (this.budgetBytes > 0L) {
            long overBefore = Math.max(0L, this.windowBytes - this.budgetBytes);
            this.windowBytes += bytes;
            long overAfter = Math.max(0L, this.windowBytes - this.budgetBytes);
            overrun = overAfter - overBefore;
            this.windowOverrunBytes = overAfter;
        } else {
            this.windowBytes += bytes;
        }
        this.totalFlushedBytes += bytes;
        this.totalOverrunBytes += overrun;
        return overrun;
    }

    /**
     * {@return whether issuing {@code bytes} under {@code frameKey} would take that window over the
     * budget}. A pure query: it mutates nothing, so a window the key has not opened yet reads as empty.
     * Always false when the budget is unlimited.
     */
    public boolean wouldExceed(long frameKey, long bytes) {
        if (this.budgetBytes <= 0L) {
            return false;
        }
        long inWindow = frameKey == this.windowKey ? this.windowBytes : 0L;
        return inWindow + bytes > this.budgetBytes;
    }

    /**
     * Accounts a producer deferring {@code bytes} of not-yet-issued uploads (layer 2).
     *
     * @return false when the deferred backlog's cap refuses the deferral - the bytes must be issued
     * now instead; the refusal is counted in {@link #deferredCapForce()} and nothing is accounted
     */
    public boolean noteDeferred(long bytes) {
        if (this.deferredCapBytes > 0L && this.deferredOutstandingBytes + bytes > this.deferredCapBytes) {
            this.deferredCapForce++;
            return false;
        }
        this.deferredOutstandingBytes += bytes;
        this.deferredTotalBytes += bytes;
        this.deferredCount++;
        return true;
    }

    /** Accounts deferred bytes finally being issued: the backlog shrinks, the cumulative totals stay. */
    public void noteDeferredIssued(long bytes) {
        this.deferredOutstandingBytes = Math.max(0L, this.deferredOutstandingBytes - bytes);
    }

    /** {@return the per-window byte budget, or {@code <= 0} when unlimited}. */
    public long budgetBytes() {
        return this.budgetBytes;
    }

    /** {@return the bytes the current window has carried so far}. */
    public long windowBytes() {
        return this.windowBytes;
    }

    /** {@return the current window's overrun: its bytes beyond the budget}. */
    public long windowOverrunBytes() {
        return this.windowOverrunBytes;
    }

    /** {@return every byte ever accounted through {@link #noteFlushed(long, long)}. */
    public long totalFlushedBytes() {
        return this.totalFlushedBytes;
    }

    /** {@return the cumulative overrun bytes: the summary's {@code uploadBudgetOverrunBytes}}. */
    public long totalOverrunBytes() {
        return this.totalOverrunBytes;
    }

    /** {@return the deferred bytes still outstanding (issued-deferred accounting, layer 2)}. */
    public long deferredOutstandingBytes() {
        return this.deferredOutstandingBytes;
    }

    /** {@return the cumulative deferred bytes: the summary's {@code uploadBudgetDeferredBytes}}. */
    public long deferredTotalBytes() {
        return this.deferredTotalBytes;
    }

    /** {@return the cumulative deferral count: the summary's {@code uploadBudgetDeferredCount}}. */
    public long deferredCount() {
        return this.deferredCount;
    }

    /** {@return the forced issues the cap has caused: the summary's {@code uploadBudgetDeferredCapForce}}. */
    public long deferredCapForce() {
        return this.deferredCapForce;
    }
}
