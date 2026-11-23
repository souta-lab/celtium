package org.soutalab.celtium.vulkan;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The GLSM stage-2 slice S2-1 census: the single, always-on count of why each
 * immediate window opened, replacing the three split instruments that used to
 * divide this population (design: glsm-stage2-design section 4).
 * <p>
 * The three it replaces: {@code GlDrawWindowCensus} (13 entrance causes, off
 * unless {@code -Dceltium.vulkan.glDrawWindowCensus}), the accept-flush census
 * in {@link FramePassTimings} (4 window triggers, off unless the harness asked
 * for {@code acceptFlush}), and the backend's {@code FLUSH_REASON_CENSUS}
 * (a stack-walk guess at the calling site, opt-in). Their populations were
 * defined differently and every reader was warned never to sum them. Here
 * there is one population - a window confirmed opened, filed once at the
 * commit point in {@code flushImmediateWindow} - and the other views are
 * derivations of it: the 4 triggers are derived from the cause by
 * {@link #triggerForCause(int)}, and the reconciliation invariant
 * {@code sum(cause) == actualFlushes == sum(windowsByTrigger)} is checked at
 * every report window, with any mismatch counted as {@code censusDiscrepancy}
 * instead of drifting silently.
 * <p>
 * The cause is the kind of entrance, never a caller class name (the stack
 * guess is gone). An entrance stamps the pending cause in the backend under
 * the same save/restore discipline the E2 census used; the stamp is spent by
 * the window it leads to. {@link #CAUSE_CHAIN_ORDER} is new: the post chain's
 * input flush used to file under replay-offtarget, which hid the chain's own
 * ordering windows inside the replay family's. {@link #CAUSE_COMPUTE_ORDER}
 * is reserved for the compute dispatch entrance (no population yet).
 * {@link #CAUSE_FRAME_END} is new as a cause: frame-end windows used to file
 * as other, split only by the trigger view.
 * <p>
 * Sub-classifications ride bits on the stamp, not new causes: a copy-order
 * window is a readback when its stamp carries {@link #SUB_COPY_READBACK}, and
 * a frame-end window is a present drain when its stamp carries
 * {@link #SUB_FRAME_END_PRESENT} (the boundary drain is the unmarked form).
 * <p>
 * Beside the causes, the same report window carries the Angelica
 * FrameManager-shaped companions: {@code groupBreak} (why an open group
 * refused a batch, in 5 classes, counted at the accept path's split decision),
 * {@code midSubmit} (mid-frame submissions by submitter: window, chain,
 * one-shot upload) and the hold-key distribution (the tuple a window opened
 * under - bound identity, slot, layout generation, raster version and
 * viewport key - as a distribution only; it names the flushes a later slice
 * could fold, and folds nothing itself). The format class the design's tuple
 * names has no per-identity owner until S2-4's TargetRecord, so the viewport
 * key stands in its place for now.
 * <p>
 * Always on: counting is long-array increments on the render thread, and the
 * report rides the {@link FramePassTimings} 200-frame cadence on the backend
 * diagnostics' lines (no new log tag, so the {@code [Celtium]} limiter's
 * first-16 rule does not eat it). There is no switch and no flag: this is a
 * first-class instrument, not an opt-in probe. All state is render-thread
 * confined; the aggregation lives in {@link Ledger} so it is testable without
 * the backend.
 */
public final class FlushBoundaryCensus {

    /** No entrance stamped a cause. Expected to stay near zero; a steady population is a classification leak. */
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
    /** An off-target replay entrance ({@code ModelReplay}). */
    public static final int CAUSE_REPLAY_OFFTARGET = 8;
    /** {@code flushBeforeClear}: the clear-ordering flush. */
    public static final int CAUSE_CLEAR_ORDER = 9;
    /** {@code celtium$flushBeforeRefusal}: a refusal site's S10 ordering flush. */
    public static final int CAUSE_REFUSAL_ORDER = 10;
    /** The size-triggered window inside the batch submission (the accept path's own flush). */
    public static final int CAUSE_SIZE = 11;
    /** The raw-write sample gate inside a batch submission, and the raw-write application's own flush. */
    public static final int CAUSE_RAW_WRITE_GATE = 12;
    /** The post chain's input flush ({@code celtium$flushPendingForChainInput}): order before the chain reads. */
    public static final int CAUSE_CHAIN_ORDER = 13;
    /** Reserved: the ordering flush before a compute dispatch (E10's generation dispatch will stamp it). */
    public static final int CAUSE_COMPUTE_ORDER = 14;
    /** The frame-end drain: the frame boundary's flush, or the present path's drain (sub-classified). */
    public static final int CAUSE_FRAME_END = 15;

    static final String[] CAUSE_NAMES = {
            "other", "bind-framebuffer-main", "bind-framebuffer-target", "bind-framebuffer-raw",
            "texture-announce", "texture-lifetime", "batch-submit", "copy-order",
            "replay-offtarget", "clear-order", "refusal-order", "size", "raw-write-gate",
            "chain-order", "compute-order", "frame-end"};

    /** Stamp sub-bit: a {@link #CAUSE_COPY_ORDER} window ordered a readback, not a copy. */
    public static final int SUB_COPY_READBACK = 1;
    /** Stamp sub-bit: a {@link #CAUSE_FRAME_END} window was the present path's drain, not the boundary's. */
    public static final int SUB_FRAME_END_PRESENT = 1;

    /** A group broke because its pipeline (format, state key, prepared pipeline/descriptor) changed. */
    public static final int GROUP_BREAK_PIPELINE = 0;
    /** A group broke because the sampled texture (sampler side) changed. */
    public static final int GROUP_BREAK_SAMPLER_TEXTURE = 1;
    /** A group broke because raster-side state (scissor, colour, fog, light, matrix) changed. */
    public static final int GROUP_BREAK_RASTER = 2;
    /** A group broke because the bound target changed. */
    public static final int GROUP_BREAK_TARGET = 3;
    /** A group broke because the viewport changed. */
    public static final int GROUP_BREAK_VIEWPORT = 4;

    static final String[] GROUP_BREAK_NAMES = {
            "pipeline", "sampler-texture", "raster", "target", "viewport"};

    /** A mid-frame submission of an immediate window's frame buffer. */
    public static final int SUBMIT_WINDOW = 0;
    /** A mid-frame submission of a post chain run. */
    public static final int SUBMIT_CHAIN = 1;
    /** A mid-frame one-shot upload submission ({@code VulkanContext.endCommands}). */
    public static final int SUBMIT_ONE_SHOT_UPLOAD = 2;

    static final String[] SUBMIT_NAMES = {"window", "chain", "one-shot-upload"};

    private static final Ledger LEDGER = new Ledger();

    private FlushBoundaryCensus() {
    }

    /**
     * {@return the {@link FramePassTimings} window trigger a cause derives to}:
     * size to SIZE, the ordering family (clear, copy, refusal, chain, texture)
     * to ORDER, frame-end to FRAME_END, and everything else to GL_DRAW - the
     * derivation the design fixes, so the trigger line is a view of this
     * census and never a second population.
     */
    public static int triggerForCause(int cause) {
        switch (cause) {
            case CAUSE_SIZE:
                return FramePassTimings.WINDOW_TRIGGER_SIZE;
            case CAUSE_CLEAR_ORDER:
            case CAUSE_COPY_ORDER:
            case CAUSE_REFUSAL_ORDER:
            case CAUSE_CHAIN_ORDER:
            case CAUSE_TEXTURE_ANNOUNCE:
            case CAUSE_TEXTURE_LIFETIME:
                return FramePassTimings.WINDOW_TRIGGER_ORDER;
            case CAUSE_FRAME_END:
                return FramePassTimings.WINDOW_TRIGGER_FRAME_END;
            default:
                return FramePassTimings.WINDOW_TRIGGER_GL_DRAW;
        }
    }

    /** One window was confirmed opened; {@code cause}/{@code sub} are the stamp its entrance left. */
    public static void noteWindow(int cause, int sub) {
        LEDGER.noteWindow(cause, sub);
    }

    /** One window was confirmed opened under an unsubclassified stamp. */
    public static void noteWindow(int cause) {
        LEDGER.noteWindow(cause, 0);
    }

    /** One confirmed window's actual-flush count, filed at the same commit point as its cause. */
    public static void noteActualFlush() {
        LEDGER.noteActualFlush();
    }

    /** One group broke at the accept path's split decision, under {@code kind}. */
    public static void noteGroupBreak(int kind) {
        LEDGER.noteGroupBreak(kind);
    }

    /** One mid-frame submission by {@code submitter}. */
    public static void noteMidFrameSubmit(int submitter) {
        LEDGER.noteMidFrameSubmit(submitter);
    }

    /** One confirmed window opened under the hold {@code key} (distribution only). */
    public static void noteHoldKey(long key) {
        LEDGER.noteHoldKey(key);
    }

    /** {@return a one-line since-start summary}, for the backend diagnostics line. */
    public static String totalSummary() {
        return LEDGER.totalSummary();
    }

    /**
     * Prints the ended report window's lines on the pass timings' cadence and
     * reconciles the window against the trigger view's sums.
     */
    static void reportWindow(long frames, long triggerWindowSum, long triggerTotalSum) {
        for (String line : LEDGER.windowLines(frames, triggerWindowSum, triggerTotalSum)) {
            System.out.println("[Celtium] " + line);
        }
    }

    /** The counting: per-cause window counts for the current report window and since start. */
    static final class Ledger {
        private final long[] windowCounts = new long[CAUSE_NAMES.length];
        private final long[] totalCounts = new long[CAUSE_NAMES.length];
        private long windowTotal;
        private long totalTotal;
        private long windowActual;
        private long totalActual;
        private long copyReadbackWindow;
        private long copyReadbackTotal;
        private long frameEndPresentWindow;
        private long frameEndPresentTotal;
        private final long[] groupBreakWindow = new long[GROUP_BREAK_NAMES.length];
        private final long[] groupBreakTotal = new long[GROUP_BREAK_NAMES.length];
        private final long[] midSubmitWindow = new long[SUBMIT_NAMES.length];
        private final long[] midSubmitTotal = new long[SUBMIT_NAMES.length];
        private final long[] derivedTriggerWindow = new long[4];
        private final long[] derivedTriggerTotal = new long[4];
        private final Map<Long, Long> holdKeyWindow = new HashMap<Long, Long>();
        private final Map<Long, Long> holdKeyTotal = new HashMap<Long, Long>();
        private long censusDiscrepancy;
        private long lastTriggerWindowSum = -1L;
        private long lastTriggerTotalSum = -1L;

        void noteWindow(int cause, int sub) {
            int index = cause >= 0 && cause < CAUSE_NAMES.length ? cause : CAUSE_OTHER;
            this.windowCounts[index]++;
            this.totalCounts[index]++;
            this.windowTotal++;
            this.totalTotal++;
            int derived = triggerForCause(index);
            this.derivedTriggerWindow[derived]++;
            this.derivedTriggerTotal[derived]++;
            if (index == CAUSE_COPY_ORDER && (sub & SUB_COPY_READBACK) != 0) {
                this.copyReadbackWindow++;
                this.copyReadbackTotal++;
            }
            if (index == CAUSE_FRAME_END && (sub & SUB_FRAME_END_PRESENT) != 0) {
                this.frameEndPresentWindow++;
                this.frameEndPresentTotal++;
            }
        }

        void noteActualFlush() {
            this.windowActual++;
            this.totalActual++;
        }

        void noteGroupBreak(int kind) {
            if (kind < 0 || kind >= GROUP_BREAK_NAMES.length) return;
            this.groupBreakWindow[kind]++;
            this.groupBreakTotal[kind]++;
        }

        void noteMidFrameSubmit(int submitter) {
            if (submitter < 0 || submitter >= SUBMIT_NAMES.length) return;
            this.midSubmitWindow[submitter]++;
            this.midSubmitTotal[submitter]++;
        }

        void noteHoldKey(long key) {
            this.holdKeyWindow.merge(Long.valueOf(key), Long.valueOf(1L), Long::sum);
            this.holdKeyTotal.merge(Long.valueOf(key), Long.valueOf(1L), Long::sum);
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

        /** {@return how many report windows failed the reconciliation invariant}. */
        long censusDiscrepancy() {
            return this.censusDiscrepancy;
        }

        /** {@return the trigger sums the last reconciliation compared against} (window, then total). */
        long lastTriggerWindowSum() {
            return this.lastTriggerWindowSum;
        }

        String totalSummary() {
            return "windows=" + this.totalTotal + " actualFlushes=" + this.totalActual
                    + " censusDiscrepancy=" + this.censusDiscrepancy
                    + " [" + counts(this.totalCounts, this.totalTotal) + "]";
        }

        /**
         * Reconciles this report window - {@code sum(cause) == actualFlushes ==
         * sum(windowsByTrigger)}, for the window and for the totals - then
         * {@return the ended window's lines} and resets the window's counters.
         * A mismatch increments {@code censusDiscrepancy} once per scope
         * (window / total) instead of passing silently. An idle window
         * reconciles too (a trigger sum that moved while no cause was filed
         * is exactly the drift this exists to catch) but prints no census line
         * unless the reconciliation failed.
         */
        List<String> windowLines(long frames, long triggerWindowSum, long triggerTotalSum) {
            this.lastTriggerWindowSum = triggerWindowSum;
            this.lastTriggerTotalSum = triggerTotalSum;
            boolean windowOk = this.windowTotal == this.windowActual && this.windowTotal == triggerWindowSum
                    && derivedSum(this.derivedTriggerWindow) == triggerWindowSum;
            boolean totalOk = this.totalTotal == this.totalActual && this.totalTotal == triggerTotalSum
                    && derivedSum(this.derivedTriggerTotal) == triggerTotalSum;
            if (!windowOk) this.censusDiscrepancy++;
            if (!totalOk) this.censusDiscrepancy++;
            List<String> lines = new ArrayList<>();
            if (this.windowTotal == 0L && windowOk && totalOk) {
                resetWindow();
                return lines;
            }
            lines.add("CELTIUM_FLUSH_CENSUS frames=" + frames
                    + " window[" + counts(this.windowCounts, this.windowTotal) + "]"
                    + " total[" + counts(this.totalCounts, this.totalTotal) + "]"
                    + " actualFlushes=" + this.windowActual + "/" + this.totalActual
                    + " censusDiscrepancy=" + this.censusDiscrepancy
                    + " copyReadback=" + this.copyReadbackWindow + "/" + this.copyReadbackTotal
                    + " frameEndPresent=" + this.frameEndPresentWindow + "/" + this.frameEndPresentTotal
                    + " groupBreak[" + named(this.groupBreakWindow, GROUP_BREAK_NAMES) + "]"
                    + " midSubmit[" + named(this.midSubmitWindow, SUBMIT_NAMES) + "]"
                    + " holdKeys[" + holdKeys(this.holdKeyWindow) + "]");
            resetWindow();
            return lines;
        }

        private void resetWindow() {
            java.util.Arrays.fill(this.windowCounts, 0L);
            this.windowTotal = 0L;
            this.windowActual = 0L;
            this.copyReadbackWindow = 0L;
            this.frameEndPresentWindow = 0L;
            java.util.Arrays.fill(this.derivedTriggerWindow, 0L);
            java.util.Arrays.fill(this.groupBreakWindow, 0L);
            java.util.Arrays.fill(this.midSubmitWindow, 0L);
            this.holdKeyWindow.clear();
        }

        private static long derivedSum(long[] values) {
            long sum = 0L;
            for (long value : values) sum += value;
            return sum;
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

        private static String named(long[] values, String[] names) {
            StringBuilder line = new StringBuilder();
            for (int i = 0; i < values.length; i++) {
                if (values[i] == 0L) {
                    continue;
                }
                if (line.length() > 0) line.append(' ');
                line.append(names[i]).append('=').append(values[i]);
            }
            return line.length() == 0 ? "none" : line.toString();
        }

        /** {@return the window's three most common hold keys, as hex key = count}. */
        private static String holdKeys(Map<Long, Long> keys) {
            if (keys.isEmpty()) {
                return "none";
            }
            List<Map.Entry<Long, Long>> rows = new ArrayList<>(keys.entrySet());
            rows.sort((a, b) -> Long.compare(b.getValue().longValue(), a.getValue().longValue()));
            StringBuilder line = new StringBuilder();
            for (int i = 0; i < rows.size() && i < 3; i++) {
                if (i > 0) line.append(' ');
                line.append(Long.toHexString(rows.get(i).getKey().longValue()))
                        .append('=').append(rows.get(i).getValue());
            }
            return line.toString();
        }
    }
}
