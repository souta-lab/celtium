package org.soutalab.celtium.vulkan;

import java.util.Arrays;

/**
 * Attributes the frame's own time to the passes inside it, so the profiler's unattributed
 * {@code gameRenderer} residual has a name.
 * <p>
 * Why this exists: the user's lag-spike capture (perf-ring's decode, {@code docs/plan.md} §0.227) shows 358 frames
 * over 20 s with a mean of 55.9 ms and spikes whose {@code gameRenderer} self time is 318.6 ms against 25.0 ms in
 * median frames - and the profiler's named children explain only about 72 ms of it. Roughly 246 ms per spike frame
 * belongs to no section at all, and {@code frameFenceWaitMillis} is 0.14 ms per wait, orders of magnitude too small.
 * The profiler cannot say which pass holds it; this can, because it brackets the passes with wall-clock time of our
 * own rather than reading the profiler's tree.
 * <p>
 * Pass times are inclusive. Minecraft calls hand/clouds/weather inside renderWorld, so summing those times
 * double-counts their overlap. The residual subtracts the <b>union</b> of world/hand/clouds/weather/gui brackets,
 * not their sum. Auxiliary brackets are reported for attribution and do not change that coverage. This is deliberately
 * not comparable with the profiler's self-time sum, which is 4.01x the frame time because {@code frame}, {@code root}
 * and {@code gameRenderer} each hold all of it; the two must never be added together.
 * <p>
 * Off unless something is measuring ({@link VulkanDiagnostics#enabled()}) or
 * {@code -Dceltium.vulkan.passSplit=true} is set, the same rule the other repeating instruments follow.
 */
public final class FramePassTimings {

    /** Named inclusive passes whose union is subtracted from the frame. */
    private static final String[] TOP_LEVEL = {"world", "hand", "clouds", "weather", "gui"};
    /**
     * Measured inside {@code world}; reported, never subtracted.
     * <p>
     * {@code present} and {@code flush} are ours rather than the game's: the lag-spike frame's {@code world} bracket
     * holds the whole spike (fixture: 367.9 ms of a 411.5 ms frame) and the profiler cannot say which part of that
     * pass is the renderer's own present/composite/window-flush chain against the game's renderWorldPass work. These
     * two brackets name it.
     */
    private static final String[] INSIDE = {"entities", "particles", "present", "flush", "terrain", "chunkUpload",
            "setupTerrain", "sky"};

    private static final int CAPACITY = 512;

    private static final long[] frameTotals = new long[CAPACITY];
    private static final long[][] topLevel = new long[TOP_LEVEL.length][CAPACITY];
    private static final long[][] inside = new long[INSIDE.length][CAPACITY];
    private static final long[] residuals = new long[CAPACITY];
    private static int written;

    private static final long[] starts = new long[TOP_LEVEL.length + INSIDE.length];
    private static long frameStart;
    private static final PassTimingCoverage coverage = new PassTimingCoverage(TOP_LEVEL.length);
    private static final int[] insideDepth = new int[INSIDE.length];
    private static long currentFrameTotal;
    private static final long[] currentTopLevel = new long[TOP_LEVEL.length];
    private static final long[] currentInside = new long[INSIDE.length];
    private static long lastReportedFrames;

    private FramePassTimings() {
    }

    /** {@return whether the instrument should measure at all}. */
    public static boolean enabled() {
        return VulkanDiagnostics.enabled() || Boolean.getBoolean("celtium.vulkan.passSplit");
    }

    /** Brackets the frame itself. */
    public static void beginFrame() {
        if (!enabled()) return;
        coverage.reset();
        Arrays.fill(starts, 0L);
        Arrays.fill(insideDepth, 0);
        Arrays.fill(currentTopLevel, 0L);
        Arrays.fill(currentInside, 0L);
        frameStart = System.nanoTime();
    }

    /** Closes the frame bracket and files the frame's record. */
    public static void endFrame() {
        if (!enabled() || frameStart == 0L) return;
        long frameEnd = System.nanoTime();
        currentFrameTotal = frameEnd - frameStart;
        frameStart = 0L;
        long residual = currentFrameTotal - coverage.coveredNanos(frameEnd);
        if (residual < 0L) residual = 0L;
        int slot = written % CAPACITY;
        frameTotals[slot] = currentFrameTotal;
        residuals[slot] = residual;
        for (int i = 0; i < TOP_LEVEL.length; i++) topLevel[i][slot] = currentTopLevel[i];
        for (int i = 0; i < INSIDE.length; i++) inside[i][slot] = currentInside[i];
        written++;
        Arrays.fill(currentTopLevel, 0L);
        Arrays.fill(currentInside, 0L);
        Arrays.fill(starts, 0L);
        Arrays.fill(insideDepth, 0);
        coverage.reset();
        maybeReport();
    }

    /**
     * How many times each bracket was opened, counted whether or not the timings are enabled.
     * <p>
     * The times themselves are only recorded under {@link #enabled()}, which is off by default, so an empty
     * {@link #summary()} cannot say whether a bracket ran - and that ambiguity is exactly what the missing HUD needed
     * settled: `GuiIngameMixin` brackets Forge's {@code GuiIngameForge.func_175180_a} with {@code require = 0}, so a
     * failed injection and a HUD that is never drawn both leave the timing summary silent. These counts separate them:
     * a non-zero {@code gui} count with zero identity-0 groups in the frame means the HUD is drawn and its batches are
     * not captured, and a zero count means the function does not run at all.
     */
    private static final long[] BEGIN_CALLS = new long[TOP_LEVEL.length + INSIDE.length];

    /** {@return how many times the bracket for one pass name was opened, enabled or not}. */
    public static long passBeginCalls(String pass) {
        int i = indexOf(pass);
        return i < 0 ? -1L : BEGIN_CALLS[i];
    }

    /**
     * Whether the {@code gui} bracket is open right now.
     * <p>
     * Kept whether or not the timings are enabled, for the same reason the counts above are: the question it answers
     * ("is this group being created inside the HUD's own drawing function?") has to be answerable without turning the
     * timings on. {@code GuiIngameMixin} brackets Forge's {@code func_175180_a} with {@code require = 0}, so an open
     * bracket plus the group's own identity and projection is what says whether the HUD's batches reach the renderer,
     * which image they name, and with which matrix they were projected.
     */
    public static boolean guiSectionActive() {
        return guiActive;
    }

    private static boolean guiActive;

    /** {@return every bracket's open count, as {@code name=count} pairs}, so no name has to be guessed. */
    public static String passBeginCallsSummary() {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < BEGIN_CALLS.length; i++) {
            if (i > 0) text.append(',');
            text.append(i < TOP_LEVEL.length ? TOP_LEVEL[i] : INSIDE[i - TOP_LEVEL.length]).append('=')
                    .append(BEGIN_CALLS[i]);
        }
        return text.toString();
    }

    /** Opens a pass bracket. Nested passes are allowed: an inner pass's time stays inside its outer pass. */
    public static void begin(String pass) {
        int counted = indexOf(pass);
        if (counted >= 0) BEGIN_CALLS[counted]++;
        if (counted == GUI_INDEX) guiActive = true;
        if (!enabled() || frameStart == 0L || counted < 0) return;
        int i = counted;
        long now = System.nanoTime();
        boolean outermost = i < TOP_LEVEL.length ? coverage.begin(i, now)
                : insideDepth[i - TOP_LEVEL.length]++ == 0;
        if (outermost) starts[i] = now;
    }

    /** Closes a pass bracket and adds its time to the current frame. */
    public static void end(String pass) {
        if (indexOf(pass) == GUI_INDEX) guiActive = false;
        if (!enabled() || frameStart == 0L) return;
        int i = indexOf(pass);
        if (i < 0 || starts[i] == 0L) return;
        long now = System.nanoTime();
        boolean outermost;
        if (i < TOP_LEVEL.length) {
            outermost = coverage.end(i, now);
        } else {
            int insideIndex = i - TOP_LEVEL.length;
            outermost = insideDepth[insideIndex] > 0 && --insideDepth[insideIndex] == 0;
        }
        if (!outermost) return;
        long elapsed = now - starts[i];
        starts[i] = 0L;
        if (i < TOP_LEVEL.length) currentTopLevel[i] += elapsed;
        else currentInside[i - TOP_LEVEL.length] += elapsed;
    }

    /** The index of {@code gui} in the pass tables, so the flag is set by name rather than by a literal. */
    private static final int GUI_INDEX = indexOf("gui");

    private static int indexOf(String pass) {
        for (int i = 0; i < TOP_LEVEL.length; i++) if (TOP_LEVEL[i].equals(pass)) return i;
        for (int i = 0; i < INSIDE.length; i++) if (INSIDE[i].equals(pass)) return TOP_LEVEL.length + i;
        return -1;
    }

    /** Prints a one-line split every 200 frames, so a running session shows the shape without a report. */
    private static void maybeReport() {
        if (written < 200 || written - lastReportedFrames < 200) return;
        lastReportedFrames = written;
        System.out.println("[Celtium] CELTIUM_FRAME_PASS_SPLIT " + summary());
    }

    /**
     * {@return the frame bracket alone, p50/p95/worst}, the short form the F3 panel's default shows.
     * <p>
     * The complaint about that panel was its length, not its content: the full split is a few hundred characters of
     * bracket text per line. The same numbers stay available through {@link #summary()} in the log's
     * {@code CELTIUM_VERIFY} line and in the panel under {@code -Dceltium.f3.fullSummary=true}.
     */
    public static String summaryShort() {
        if (written == 0) return "not measured yet";
        int n = Math.min(written, CAPACITY);
        return "p50=" + ms(percentile(frameTotals, n, 0.50)) + " p95=" + ms(percentile(frameTotals, n, 0.95))
                + " worst=" + ms(worst(frameTotals, n)) + " ms";
    }

    /**
     * {@return the pass split of the <em>worst single frame</em> in the ring, in milliseconds}.
     *
     * <p>Written for the frame-time work: the fixture keeps a bounded record of the slowest frames, and a summary of
     * percentiles over the ring cannot say which pass produced one of them. The worst frame can be read directly,
     * because its leave-behind is a single frame that did happen - {@code terrain} at 470 ms and {@code present} at
     * 3 ms is a statement about one frame, where "the p95 of terrain minus the p95 of the frame" is not.
     */
    /**
     * {@return the pass split of the frame that ended most recently, in milliseconds}.
     *
     * <p>Per-entry fidelity for the fixture's slow-frame record: {@link #worstFrameSummary()} answers "which pass
     * produced the worst frame so far", which is one frame repeated for every entry, while a slow frame is recorded
     * right after it ends and wants its own split. Both exist because they are different questions - the worst frame
     * is the headline, and the entry beside a slow frame has to describe that frame.
     */
    public static String lastFrameSummary() {
        if (!enabled() || written == 0) return "pass split not measured";
        return frameSummary((written - 1) % CAPACITY);
    }

    /** {@return one frame's pass split, from the ring slot that holds it}. */
    private static String frameSummary(int slot) {
        StringBuilder text = new StringBuilder("frame=").append(ms(frameTotals[slot]));
        for (int i = 0; i < TOP_LEVEL.length; i++) {
            text.append(' ').append(TOP_LEVEL[i]).append('=').append(ms(topLevel[i][slot]));
        }
        for (int i = 0; i < INSIDE.length; i++) {
            text.append(' ').append(INSIDE[i]).append('=').append(ms(inside[i][slot]));
        }
        text.append(" residual=").append(ms(residuals[slot]));
        return text.toString();
    }

    public static String worstFrameSummary() {
        if (!enabled() || written == 0) return "pass split not measured";
        int n = Math.min(written, CAPACITY);
        int slot = 0;
        for (int i = 1; i < n; i++) {
            if (frameTotals[i] > frameTotals[slot]) slot = i;
        }
        return frameSummary(slot);
    }

    /**
     * {@return the per-pass split of the frames measured so far}, as p50/p95/worst milliseconds plus the residual.
     * <p>
     * The residual is per frame ({@code frame - union(named brackets)}) and then summarised, not a difference of
     * percentiles - a p50 of the frame minus a p50 of the passes would describe a frame that never happened.
     */
    public static String summary() {
        if (written == 0) return "frames=0 (the instrument did not measure; enabled=" + enabled() + ")";
        int n = Math.min(written, CAPACITY);
        StringBuilder text = new StringBuilder("frames=").append(n);
        text.append(" frame[p50=").append(ms(percentile(frameTotals, n, 0.50)))
                .append(" p95=").append(ms(percentile(frameTotals, n, 0.95)))
                .append(" worst=").append(ms(worst(frameTotals, n))).append(']');
        for (int i = 0; i < TOP_LEVEL.length; i++) {
            text.append(' ').append(TOP_LEVEL[i]).append("[p50=").append(ms(percentile(topLevel[i], n, 0.50)))
                    .append(" p95=").append(ms(percentile(topLevel[i], n, 0.95)))
                    .append(" worst=").append(ms(worst(topLevel[i], n))).append(']');
        }
        text.append(" residual[p50=").append(ms(percentile(residuals, n, 0.50)))
                .append(" p95=").append(ms(percentile(residuals, n, 0.95)))
                .append(" worst=").append(ms(worst(residuals, n))).append(']');
        for (int i = 0; i < INSIDE.length; i++) {
            text.append(' ').append(INSIDE[i]).append("(inside world)[p50=")
                    .append(ms(percentile(inside[i], n, 0.50)))
                    .append(" p95=").append(ms(percentile(inside[i], n, 0.95)))
                    .append(" worst=").append(ms(worst(inside[i], n))).append(']');
        }
        return text.toString();
    }

    private static long percentile(long[] values, int n, double q) {
        long[] copy = Arrays.copyOf(values, n);
        Arrays.sort(copy);
        int index = (int) Math.round(q * (n - 1));
        return copy[Math.max(0, Math.min(n - 1, index))];
    }

    private static long worst(long[] values, int n) {
        long max = 0L;
        for (int i = 0; i < n; i++) max = Math.max(max, values[i]);
        return max;
    }

    private static String ms(long nanos) {
        return String.format(java.util.Locale.ROOT, "%.1f", nanos / 1_000_000.0D);
    }
}
