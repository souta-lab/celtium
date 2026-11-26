package org.soutalab.celtium.vulkan;

/**
 * The mesh (terrain chunk) producer's own upload budget account (budget layer 2, option C of
 * OVERHAUL-BUDGET-MESH-STARVATION-DIAGNOSIS-2026-11-27 §3): a device-free window account the
 * terrain backend owns, kept apart from the shared {@link UploadBudgetLedger} the texture
 * producer judges against.
 * <p>
 * Why a separate account: under the shared ledger the mesh judgment ran once per frame, at the
 * head of terrain setup, against a window the frame's charges had not reached yet - the texture
 * batches' synchronous charges and the frame flush's aggregate charge all land in the same
 * window only after the mesh judgment has already read it as empty. The mesh side therefore saw
 * "0 + its own batch weight" whatever the budget was tightened to, and only the texture side -
 * whose per-request gate charges its own bytes the moment it issues - ever deferred. This
 * account closes the mesh side's loop the same way: bytes are charged at issue time
 * ({@link #noteIssued(long, long)}), by the arrival path when a burst goes through at once and
 * by the drain when a deferred entry is issued, so a judgment reads the mesh work the window
 * has already carried, including this frame's drain.
 * <p>
 * The price, named and bounded: the frame's total upload bytes are no longer bounded by the one
 * nominal budget. The texture side may spend the shared budget and the mesh side its own in the
 * same frame, so the total can exceed the nominal budget by at most the mesh budget, and the two
 * producers' deferred backlogs carry separate caps whose sum is the combined worst case. The
 * layer-1 ledger still accounts every flushed byte and its overrun, so the total stays
 * observable; nothing about the texture side's gate, account or summary keys changes.
 * <p>
 * The budget resolves to the shared upload budget (the {@code celtium.vulkan.uploadBudgetMiB}
 * resolution and the device-type defaults behind it) unless a mesh-only override is set:
 * {@code celtium.vulkan.meshUploadBudgetBytes} wins over
 * {@code celtium.vulkan.meshUploadBudgetMiB}. As with the shared property, a resolved value of
 * {@code <= 0} means unlimited (the A/B escape). The deferred cap is the design's standing cap,
 * the same value the shared ledger carries. Not thread-safe, on the same game-thread
 * confinement as the ledger.
 */
public final class MeshUploadBudget {
    /** Byte-granularity override of the mesh budget; wins over the MiB property when set. */
    public static final String BYTES_PROPERTY = "celtium.vulkan.meshUploadBudgetBytes";
    /** MiB-granularity override of the mesh budget; the shared budget stands when neither is set. */
    public static final String MIB_PROPERTY = "celtium.vulkan.meshUploadBudgetMiB";

    private final long budgetBytes;
    private final long deferredCapBytes;

    /** The frame key the current window belongs to; {@code Long.MIN_VALUE} before the first window. */
    private long windowKey = Long.MIN_VALUE;
    private long windowBytes;

    // The deferral account: bytes held back and not yet issued, the cumulative deferred total and
    // count the summary reports, and the forced issues the cap has caused.
    private long deferredOutstandingBytes;
    private long deferredTotalBytes;
    private long deferredCount;
    private long deferredCapForce;

    public MeshUploadBudget(long budgetBytes, long deferredCapBytes) {
        this.budgetBytes = budgetBytes;
        this.deferredCapBytes = deferredCapBytes;
    }

    /**
     * {@return the mesh budget's byte count}: the byte property when set, else the MiB property,
     * else {@code sharedBudgetBytes} (the shared ledger's resolved budget). A property value of
     * {@code <= 0} resolves to 0, unlimited, on the shared property's convention.
     */
    public static long resolveBudgetBytes(long sharedBudgetBytes) {
        long budget = sharedBudgetBytes;
        Long overrideMiB = Long.getLong(MIB_PROPERTY);
        if (overrideMiB != null) {
            budget = overrideMiB.longValue() <= 0L ? 0L : overrideMiB.longValue() << 20;
        }
        String overrideBytes = System.getProperty(BYTES_PROPERTY);
        if (overrideBytes != null) {
            long parsed = Long.parseLong(overrideBytes);
            budget = parsed <= 0L ? 0L : parsed;
        }
        return budget;
    }

    /** Resolves an account against {@code sharedBudgetBytes} with the design's standing deferred cap. */
    public static MeshUploadBudget resolve(long sharedBudgetBytes) {
        return new MeshUploadBudget(resolveBudgetBytes(sharedBudgetBytes),
                UploadBudgetLedger.DEFAULT_DEFERRED_CAP_BYTES);
    }

    /**
     * {@return whether issuing {@code bytes} under {@code frameKey} would take that window over
     * the budget}. A pure query: it mutates nothing, so a window the key has not opened yet reads
     * as empty. Always false when the budget is unlimited.
     */
    public boolean wouldExceed(long frameKey, long bytes) {
        if (this.budgetBytes <= 0L) {
            return false;
        }
        long inWindow = frameKey == this.windowKey ? this.windowBytes : 0L;
        return inWindow + bytes > this.budgetBytes;
    }

    /**
     * Charges {@code bytes} issued under {@code frameKey} to that key's window, rolling the
     * window first when the key is new. This is the arrival-time charge: it runs when the bytes
     * are issued - an arrival going through at once, an entry the drain issues - never at flush
     * time, so the next judgment in the same window reads them.
     */
    public void noteIssued(long frameKey, long bytes) {
        if (frameKey != this.windowKey) {
            this.windowKey = frameKey;
            this.windowBytes = 0L;
        }
        this.windowBytes += bytes;
    }

    /**
     * {@return the bytes the window belonging to {@code frameKey} has been charged so far}, or 0
     * when that window has not opened yet.
     */
    public long windowBytes(long frameKey) {
        return frameKey == this.windowKey ? this.windowBytes : 0L;
    }

    /**
     * Accounts deferring {@code bytes} of not-yet-issued arrivals.
     *
     * @return false when the deferred backlog's cap refuses the deferral - the bytes must be
     * issued now instead; the refusal is counted in {@link #deferredCapForce()} and nothing is
     * accounted
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

    /** {@return the deferred bytes still outstanding}. */
    public long deferredOutstandingBytes() {
        return this.deferredOutstandingBytes;
    }

    /** {@return the cumulative deferred bytes: the summary's {@code meshUploadDeferredBytes}}. */
    public long deferredTotalBytes() {
        return this.deferredTotalBytes;
    }

    /** {@return the cumulative deferral count: the summary's {@code meshUploadDeferredCount}}. */
    public long deferredCount() {
        return this.deferredCount;
    }

    /** {@return the forced issues the cap has caused: the summary's {@code meshUploadDeferredCapForce}}. */
    public long deferredCapForce() {
        return this.deferredCapForce;
    }
}
