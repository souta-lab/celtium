package org.soutalab.celtium.vulkan;

import java.util.ArrayList;
import java.util.List;

/**
 * The item-695 (E2) census: which call point opened each immediate window, with the
 * GL-draw family of entrances split by the boundary that fired it.
 * <p>
 * Why this exists: the flight census (flight-c4) counted windows by trigger as
 * {@code windowsByTrigger} - size / order / frame-end / gl-draw - and found the gl-draw
 * share at 61-67% of all windows. That trigger census has since landed on main as the
 * accept-flush census in {@link FramePassTimings} ({@code CELTIUM_ACCEPT_FLUSH}), and
 * the two instruments divide the work (secretary ruling on the item-695 landing): the
 * accept-flush census is the canonical count of windows by trigger - window totals
 * are read from it - while this census is the subdivision of the gl-draw family by
 * call point only. The same confirmed window is filed by both, under its trigger
 * there and under its cause here; the two lines are two views of one population and
 * must never be summed. Neither view answers the E2 question alone, and the older
 * attribution cannot either: {@code CELTIUM_FLUSH_SITES}
 * counts flush <em>requests</em> at twelve refusal/order sites (session-cumulative, and a
 * request that opens no window is still counted), and the {@code FLUSH_ENTRY} breadcrumb
 * separates only the framebuffer binds (raw-register versus vanilla) for the flushes a
 * crash leaves behind. Neither says, per window actually opened, whether the boundary
 * that opened it was a framebuffer bind (and which kind), a texture announce, a batch
 * submission, a copy ordering point or a replay - which is the split the E2 exemption
 * design (an allow-list of boundaries whose ordering the GL-less stub does not need)
 * has to be chosen from.
 * <p>
 * The discipline is the one flight-c4 designed and {@code pendingFlushEntryKind} already
 * follows: an entrance stamps the pending cause, the stamp is spent by the window it
 * leads to and cannot leak onto a later, unrelated window. Here the stamp lives in
 * {@link VulkanTerrainGpuBackend} beside the entry kind, is scoped by save/restore
 * around the entrance's flush call (the {@code celtium$bindFromRawRegister} pattern),
 * and is read at the one point a window is confirmed - {@code flushImmediateWindow}
 * setting {@code immediateWindowBusy} - so busy, empty and target-less early returns
 * are not windows, exactly as flight-c4 ruled. A flush that draws in an already-open
 * pass opens no window and is not counted. The size-triggered window inside the batch
 * submission is stamped {@link #CAUSE_SIZE} at its direct {@code flushImmediateWindow}
 * call, so the census total reconciles with the window count the immediate statistics
 * keep: every confirmed window is filed under exactly one cause.
 * <p>
 * Off unless the run asks for it ({@code -Dceltium.vulkan.glDrawWindowCensus=true}).
 * While off, each entrance's stamp is one static-final boolean read inside the scoped
 * flush helper, which then calls {@code flushPendingBeforeGlDraw} directly - the same
 * off-cost discipline as {@link HudCensus}. The report rides the
 * {@link FramePassTimings} window cadence beside {@link PackUniformCensus}: one
 * {@code CELTIUM_GLDRAW_WINDOW_CENSUS} line per 200-frame window that opened a window,
 * carrying the window's per-cause counts and the since-start totals. All state is
 * render-thread confined; the aggregation lives in {@link Ledger} so it is testable
 * without the switch.
 */
public final class GlDrawWindowCensus {

    /** The census switch; default off. */
    static final boolean ENABLED = Boolean.getBoolean("celtium.vulkan.glDrawWindowCensus");

    /** No entrance stamped a cause: pre-terrain, frame-boundary, present and pass-end flushes. */
    public static final int CAUSE_OTHER = 0;
    /** {@code bindFramebuffer} bound the game's main target. */
    public static final int CAUSE_BIND_FRAMEBUFFER_MAIN = 1;
    /** {@code bindFramebuffer} bound an off-target framebuffer (a mod's own FBO). */
    public static final int CAUSE_BIND_FRAMEBUFFER_TARGET = 2;
    /** {@code bindFramebuffer} ran on behalf of raw framebuffer registration. */
    public static final int CAUSE_BIND_FRAMEBUFFER_RAW = 3;
    /** A texture announce (dynamic / upload / animated) whose queued groups sample the texture. */
    public static final int CAUSE_TEXTURE_ANNOUNCE = 4;
    /** A texture lifetime boundary: reallocation, or the splash canvases being dropped. */
    public static final int CAUSE_TEXTURE_LIFETIME = 5;
    /** An ordering flush inside a batch submission (the GL draw entrance itself). */
    public static final int CAUSE_BATCH_SUBMIT = 6;
    /** A texture copy / read-back ordering point ({@code TextureCopyService}, {@code CopyReadSide}). */
    public static final int CAUSE_COPY_ORDER = 7;
    /** An off-target replay entrance ({@code ModelReplay}, {@code PostChainBridge}). */
    public static final int CAUSE_REPLAY_OFFTARGET = 8;
    /** {@code flushBeforeClear}: the clear-ordering flush. */
    public static final int CAUSE_CLEAR_ORDER = 9;
    /** {@code celtium$flushBeforeRefusal}: a refusal site's S10 ordering flush. */
    public static final int CAUSE_REFUSAL_ORDER = 10;
    /** The size-triggered window inside the batch submission (the accept path's own flush). */
    public static final int CAUSE_SIZE = 11;
    /** The raw-write sample gate inside a batch submission, and the raw-write application's own flush. */
    public static final int CAUSE_RAW_WRITE_GATE = 12;

    static final String[] CAUSE_NAMES = {
            "other", "bind-framebuffer-main", "bind-framebuffer-target", "bind-framebuffer-raw",
            "texture-announce", "texture-lifetime", "batch-submit", "copy-order",
            "replay-offtarget", "clear-order", "refusal-order", "size", "raw-write-gate"};

    private static final Ledger LEDGER = new Ledger();

    private GlDrawWindowCensus() {
    }

    /** {@return whether the census is counting at all}. */
    public static boolean enabled() {
        return ENABLED;
    }

    /** One window was confirmed opened; {@code cause} is the stamp its entrance left. */
    public static void noteWindow(int cause) {
        if (ENABLED) {
            LEDGER.noteWindow(cause);
        }
    }

    /** Prints the ended window's line on the pass timings' cadence; silent while off or idle. */
    static void reportWindow(long frames) {
        if (ENABLED) {
            for (String line : LEDGER.windowLines(frames)) {
                System.out.println("[Celtium] " + line);
            }
        }
    }

    /** The counting: per-cause window counts for the current window and since start. */
    static final class Ledger {
        private final long[] windowCounts = new long[CAUSE_NAMES.length];
        private final long[] totalCounts = new long[CAUSE_NAMES.length];
        private long windowTotal;
        private long totalTotal;

        void noteWindow(int cause) {
            int index = cause >= 0 && cause < CAUSE_NAMES.length ? cause : CAUSE_OTHER;
            this.windowCounts[index]++;
            this.totalCounts[index]++;
            this.windowTotal++;
            this.totalTotal++;
        }

        /** {@return the since-start count filed under {@code cause} (out-of-range reads as other)}. */
        long totalFor(int cause) {
            int index = cause >= 0 && cause < CAUSE_NAMES.length ? cause : CAUSE_OTHER;
            return this.totalCounts[index];
        }

        /** {@return every window filed since start}. */
        long total() {
            return this.totalTotal;
        }

        /**
         * {@return the ended window's line} - the window's per-cause counts, then the
         * since-start totals, non-zero causes only - and resets the window's counters.
         * An idle window returns no lines.
         */
        List<String> windowLines(long frames) {
            List<String> lines = new ArrayList<>();
            if (this.windowTotal == 0L) {
                return lines;
            }
            lines.add("CELTIUM_GLDRAW_WINDOW_CENSUS frames=" + frames
                    + " window[" + counts(this.windowCounts, this.windowTotal) + "]"
                    + " total[" + counts(this.totalCounts, this.totalTotal) + "]");
            java.util.Arrays.fill(this.windowCounts, 0L);
            this.windowTotal = 0L;
            return lines;
        }

        private static String counts(long[] values, long total) {
            StringBuilder line = new StringBuilder();
            line.append("windows=").append(total);
            for (int i = 0; i < values.length; i++) {
                if (values[i] == 0L) {
                    continue;
                }
                line.append(' ').append(CAUSE_NAMES[i]).append('=').append(values[i]);
            }
            return line.toString();
        }
    }
}
