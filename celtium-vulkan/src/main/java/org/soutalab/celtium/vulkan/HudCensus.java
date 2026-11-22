package org.soutalab.celtium.vulkan;

/**
 * A per-frame census of the UI's share of the immediate path: the in-game HUD overlay (the {@code gui}
 * bracket) and any open screen (the {@code screen} bracket), counted together as the UI section.
 * <p>
 * Why this exists beside the instruments already here: {@link FramePassTimings} times {@code gui},
 * {@code guiBatch} and {@code guiFlush}, but {@code guiFlush} only covers a flush entered while the
 * bracket is still open - the HUD's own drain is the frame-boundary flush after it closes, which no
 * time bracket names - and nothing counts <em>how many</em> flushes and windows a frame's UI work
 * causes. {@link HudP0Census} classifies the groups the {@code gui} bracket closes, but it predates
 * the {@code screen} bracket, so an open inventory or container screen - the HUD-heavy case of an
 * inventory full of items, a minimap and a screenful of status icons - is in no HUD population at
 * all: {@code GuiScreen.drawScreen} is not {@code GuiIngameForge.renderGameOverlay}. The flush-reason
 * census names trigger sites, but session-cumulatively and at a stack walk per flush. This census is
 * the per-frame shape those three cannot give: offers, closed groups, flush calls, actual flushes,
 * drain windows and flush wall time, split into the UI section's share and the frame's whole.
 * <p>
 * A flush window is attributed to the UI in either of two ways: it is entered while a UI bracket is
 * open, or it drains at least one group stamped {@code closedInUiSection} at its close (the same
 * stamp-not-bracket rule {@link HudP0Census#guiClosedGroups} established for the frame-boundary
 * drain). Windows are the unit for the time split, frames for everything else.
 * <p>
 * Off unless the run asks for it: {@code -Dceltium.vulkan.hudCensusFrames=<n>} bounds how many
 * frames are filed, and {@code -Dceltium.vulkan.hudCensusMinFrame=<n>} skips the loading phase, the
 * same two spellings the item-312 census uses. Every note method is one static-final boolean read
 * while the census is off. The counting itself lives in {@link Ledger}, an instance class like
 * {@link HudP0Census.Frame}, so the aggregation is testable without the switch.
 */
public final class HudCensus {

    private static final int FRAMES = Integer.getInteger("celtium.vulkan.hudCensusFrames", 0).intValue();
    private static final long MIN_FRAME = Long.getLong("celtium.vulkan.hudCensusMinFrame", 0L).longValue();
    private static final int REPORT_EVERY = 200;

    private static final Ledger LEDGER = new Ledger(FRAMES, MIN_FRAME);

    private HudCensus() {
    }

    /** {@return whether the census is counting at all}. */
    public static boolean enabled() {
        return FRAMES > 0;
    }

    /** Rolls the census to {@code frameId}, filing the previous frame if it had any counted activity. */
    public static void noteFrame(long frameId) {
        if (FRAMES > 0) {
            LEDGER.noteFrame(frameId);
        }
    }

    /** One immediate batch was offered while a UI bracket was open. */
    public static void noteOffer(long frameId) {
        if (FRAMES > 0) {
            LEDGER.noteOffer(frameId);
        }
    }

    /** One group was closed while a UI bracket was open. */
    public static void noteGroupClosed(long frameId) {
        if (FRAMES > 0) {
            LEDGER.noteGroupClosed(frameId);
        }
    }

    /** {@code flushPendingBeforeGlDraw} was entered; {@code uiActive} says a UI bracket was open. */
    public static void noteFlushCall(long frameId, boolean uiActive) {
        if (FRAMES > 0) {
            LEDGER.noteFlushCall(frameId, uiActive);
        }
    }

    /** One actual flush (a window, or an in-pass ordering draw) completed; see {@link Ledger}. */
    public static void noteActualFlush(long frameId, boolean uiActive, int uiGroups, long nanos) {
        if (FRAMES > 0) {
            LEDGER.noteActualFlush(frameId, uiActive, uiGroups, nanos);
        }
    }

    /** {@return the census so far, as per-frame means over the filed frames}. */
    public static String summary() {
        return LEDGER.summary();
    }

    /** {@return how many of a flush window's pending groups were closed inside a UI bracket}. */
    static int uiClosedGroups(java.util.List<ImmediateGroup> pending) {
        int count = 0;
        for (ImmediateGroup group : pending) {
            if (group.closedInUiSection) {
                count++;
            }
        }
        return count;
    }

    /**
     * The census' counting: one frame's counters, filed on rollover into running totals, with a
     * printed summary every {@link #REPORT_EVERY} filed frames and at the bound.
     */
    static final class Ledger {
        private final int frameBound;
        private final long minFrame;

        private long currentFrameId = -1L;
        private long framesFiled;
        private long totalOffers;
        private long totalGroupsClosed;
        private long totalFlushCalls;
        private long totalFlushCallsUi;
        private long totalActualFlushes;
        private long totalActualFlushesUi;
        private long totalDrainWindows;
        private long totalGroupsDrainedUi;
        private long totalFlushNanos;
        private long totalFlushNanosUi;
        private long maxActualFlushesPerFrame;
        private long maxFlushNanosUiPerFrame;

        private long frameOffers;
        private long frameGroupsClosed;
        private long frameFlushCalls;
        private long frameFlushCallsUi;
        private long frameActualFlushes;
        private long frameActualFlushesUi;
        private long frameDrainWindows;
        private long frameGroupsDrainedUi;
        private long frameFlushNanos;
        private long frameFlushNanosUi;

        Ledger(int frameBound, long minFrame) {
            this.frameBound = frameBound;
            this.minFrame = minFrame;
        }

        private boolean live(long frameId) {
            return frameId >= this.minFrame && this.framesFiled < this.frameBound;
        }

        void noteFrame(long frameId) {
            if (frameId == this.currentFrameId) {
                return;
            }
            if (this.currentFrameId >= 0L && live(this.currentFrameId) && hasActivity()) {
                fileFrame();
            }
            this.currentFrameId = frameId;
            this.frameOffers = 0L;
            this.frameGroupsClosed = 0L;
            this.frameFlushCalls = 0L;
            this.frameFlushCallsUi = 0L;
            this.frameActualFlushes = 0L;
            this.frameActualFlushesUi = 0L;
            this.frameDrainWindows = 0L;
            this.frameGroupsDrainedUi = 0L;
            this.frameFlushNanos = 0L;
            this.frameFlushNanosUi = 0L;
        }

        private boolean hasActivity() {
            return this.frameOffers > 0L || this.frameGroupsClosed > 0L
                    || this.frameFlushCalls > 0L || this.frameActualFlushes > 0L;
        }

        private void fileFrame() {
            this.framesFiled++;
            this.totalOffers += this.frameOffers;
            this.totalGroupsClosed += this.frameGroupsClosed;
            this.totalFlushCalls += this.frameFlushCalls;
            this.totalFlushCallsUi += this.frameFlushCallsUi;
            this.totalActualFlushes += this.frameActualFlushes;
            this.totalActualFlushesUi += this.frameActualFlushesUi;
            this.totalDrainWindows += this.frameDrainWindows;
            this.totalGroupsDrainedUi += this.frameGroupsDrainedUi;
            this.totalFlushNanos += this.frameFlushNanos;
            this.totalFlushNanosUi += this.frameFlushNanosUi;
            if (this.frameActualFlushes > this.maxActualFlushesPerFrame) {
                this.maxActualFlushesPerFrame = this.frameActualFlushes;
            }
            if (this.frameFlushNanosUi > this.maxFlushNanosUiPerFrame) {
                this.maxFlushNanosUiPerFrame = this.frameFlushNanosUi;
            }
            if (this.framesFiled % REPORT_EVERY == 0L || this.framesFiled == this.frameBound) {
                System.out.println("[Celtium] " + summary());
            }
        }

        void noteOffer(long frameId) {
            noteFrame(frameId);
            if (live(frameId)) {
                this.frameOffers++;
            }
        }

        void noteGroupClosed(long frameId) {
            noteFrame(frameId);
            if (live(frameId)) {
                this.frameGroupsClosed++;
            }
        }

        void noteFlushCall(long frameId, boolean uiActive) {
            noteFrame(frameId);
            if (!live(frameId)) {
                return;
            }
            this.frameFlushCalls++;
            if (uiActive) {
                this.frameFlushCallsUi++;
            }
        }

        /**
         * One actual flush completed. {@code uiGroups} is how many of the window's pending groups
         * were closed inside a UI bracket; a flush entered outside the brackets that still drains
         * such a group is a drain window, the frame-boundary case the bracket state cannot see.
         */
        void noteActualFlush(long frameId, boolean uiActive, int uiGroups, long nanos) {
            noteFrame(frameId);
            if (!live(frameId)) {
                return;
            }
            this.frameActualFlushes++;
            this.frameFlushNanos += nanos;
            if (uiActive || uiGroups > 0) {
                this.frameActualFlushesUi++;
                this.frameFlushNanosUi += nanos;
                this.frameGroupsDrainedUi += uiGroups;
                if (!uiActive) {
                    this.frameDrainWindows++;
                }
            }
        }

        String summary() {
            if (this.framesFiled == 0L) {
                return "CELTIUM_HUD_CENSUS frames=0 (the census filed no frame)";
            }
            double frames = (double) this.framesFiled;
            return "CELTIUM_HUD_CENSUS frames=" + this.framesFiled
                    + " perFrame offers=" + round1(this.totalOffers / frames)
                    + " groupsClosed=" + round1(this.totalGroupsClosed / frames)
                    + " flushCalls=" + round1(this.totalFlushCalls / frames)
                    + " flushCallsUi=" + round1(this.totalFlushCallsUi / frames)
                    + " actualFlushes=" + round1(this.totalActualFlushes / frames)
                    + " actualFlushesUi=" + round1(this.totalActualFlushesUi / frames)
                    + " drainWindows=" + round1(this.totalDrainWindows / frames)
                    + " groupsDrainedUi=" + round1(this.totalGroupsDrainedUi / frames)
                    + " flushMsUi=" + round1(this.totalFlushNanosUi / frames / 1.0e6)
                    + " flushMsTotal=" + round1(this.totalFlushNanos / frames / 1.0e6)
                    + " maxActualFlushesPerFrame=" + this.maxActualFlushesPerFrame
                    + " maxFlushMsUiPerFrame=" + round1(this.maxFlushNanosUiPerFrame / 1.0e6);
        }

        private static String round1(double value) {
            return String.format(java.util.Locale.ROOT, "%.1f", value);
        }

        long framesFiled() {
            return this.framesFiled;
        }

        long totalActualFlushesUi() {
            return this.totalActualFlushesUi;
        }

        long totalDrainWindows() {
            return this.totalDrainWindows;
        }

        long totalFlushNanosUi() {
            return this.totalFlushNanosUi;
        }
    }
}
