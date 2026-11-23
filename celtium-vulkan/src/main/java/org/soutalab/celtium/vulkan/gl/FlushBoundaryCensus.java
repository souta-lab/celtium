package org.soutalab.celtium.vulkan.gl;

/**
 * GLSM stage 3, GAP-FL-1 (census F-1..F-11): the single flush-boundary census.
 * <p>
 * Today the flush causes are split three ways - the backend's own accounting,
 * GlDrawWindowCensus and FramePassTimings - so "why did this frame flush" has three
 * partial answers. This census is the one ledger every flush cause records into, keyed
 * by the census's eleven triggers. It counts only; the flush decision stays with the
 * backend. F-11 is the specification decision bundled here: a guest glFlush/glFinish
 * does NOT flush (it is SEM_NOOP today) - {@link #noteGuestFlush()} counts the arrival
 * so the decision stays visible, and never triggers a boundary.
 */
public final class FlushBoundaryCensus {
    /** The eleven flush triggers of census §5, in census order. */
    public enum Reason {
        SIZE,               // F-1 group queue capacity
        FBO_BIND,           // F-2 framebuffer bind switch
        ORDERING,           // F-3 ordering guarantees (clear/copy/refusal order &c.)
        DRAW_ENTRY,         // F-4 GL draw entry
        FRAME_DRAIN,        // F-5 frame-boundary drain
        POST_CHAIN,         // F-6 before post-chain input
        RASTER_CHANGE,      // F-7 raster state change (NativeRasterState.beforeChange)
        IMMEDIATE_RUN,      // F-8 immediate run commit (glEnd &c.)
        CLEAR_CONSUME,      // F-9 pending clear consumption
        PROGRAM_MATERIALIZE,// F-10 program materialize (uniform version -> re-pack)
        GUEST_FLUSH         // F-11 guest glFlush/glFinish: counted, never a boundary
    }

    private static final FlushBoundaryCensus CURRENT = new FlushBoundaryCensus();

    public static FlushBoundaryCensus current() { return CURRENT; }

    private final long[] counts = new long[Reason.values().length];
    private long boundaries;

    private FlushBoundaryCensus() {
    }

    /** Records one flush boundary under its cause. */
    public void note(Reason reason) {
        this.counts[reason.ordinal()]++;
        if (reason != Reason.GUEST_FLUSH) this.boundaries++;
    }

    /** F-11: a guest flush/finish arrival - counted, and by specification nothing else. */
    public void noteGuestFlush() { this.note(Reason.GUEST_FLUSH); }

    public long count(Reason reason) { return this.counts[reason.ordinal()]; }

    /** Real boundaries (every reason except the guest-flush no-op). */
    public long boundaries() { return this.boundaries; }
}
