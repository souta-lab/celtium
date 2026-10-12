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
 * The numbers are a decomposition of the <b>frame</b> bracket: {@code residual = frame - (world + hand + clouds +
 * weather + gui)}. {@code entities} and {@code particles} are measured <b>inside</b> {@code world} and are reported
 * for information - they must not be subtracted as well, or the residual double-counts them. This is deliberately
 * not comparable with the profiler's self-time sum, which is 4.01x the frame time because {@code frame}, {@code root}
 * and {@code gameRenderer} each hold all of it; the two must never be added together.
 * <p>
 * Off unless something is measuring ({@link VulkanDiagnostics#enabled()}) or
 * {@code -Dceltium.vulkan.passSplit=true} is set, the same rule the other repeating instruments follow.
 */
public final class FramePassTimings {

    /** Top-level passes: their sum is what the frame's residual is measured against. */
    private static final String[] TOP_LEVEL = {"world", "hand", "clouds", "weather", "gui"};
    /**
     * Measured inside {@code world}; reported, never subtracted.
     * <p>
     * {@code present} and {@code flush} are ours rather than the game's: the lag-spike frame's {@code world} bracket
     * holds the whole spike (fixture: 367.9 ms of a 411.5 ms frame) and the profiler cannot say which part of that
     * pass is the renderer's own present/composite/window-flush chain against the game's renderWorldPass work. These
     * two brackets name it.
     */
    private static final String[] INSIDE = {"entities", "particles", "present", "flush", "terrain", "chunkUpload"};

    private static final int CAPACITY = 512;

    private static final long[] frameTotals = new long[CAPACITY];
    private static final long[][] topLevel = new long[TOP_LEVEL.length][CAPACITY];
    private static final long[][] inside = new long[INSIDE.length][CAPACITY];
    private static final long[] residuals = new long[CAPACITY];
    private static int written;

    private static final long[] starts = new long[TOP_LEVEL.length + INSIDE.length];
    private static long frameStart;
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
        frameStart = System.nanoTime();
    }

    /** Closes the frame bracket and files the frame's record. */
    public static void endFrame() {
        if (!enabled() || frameStart == 0L) return;
        currentFrameTotal = System.nanoTime() - frameStart;
        frameStart = 0L;
        long named = 0L;
        for (int i = 0; i < currentTopLevel.length; i++) named += currentTopLevel[i];
        long residual = currentFrameTotal - named;
        if (residual < 0L) residual = 0L;
        int slot = written % CAPACITY;
        frameTotals[slot] = currentFrameTotal;
        residuals[slot] = residual;
        for (int i = 0; i < TOP_LEVEL.length; i++) topLevel[i][slot] = currentTopLevel[i];
        for (int i = 0; i < INSIDE.length; i++) inside[i][slot] = currentInside[i];
        written++;
        Arrays.fill(currentTopLevel, 0L);
        Arrays.fill(currentInside, 0L);
        maybeReport();
    }

    /** Opens a pass bracket. Nested passes are allowed: an inner pass's time stays inside its outer pass. */
    public static void begin(String pass) {
        if (!enabled()) return;
        int i = indexOf(pass);
        if (i >= 0) starts[i] = System.nanoTime();
    }

    /** Closes a pass bracket and adds its time to the current frame. */
    public static void end(String pass) {
        if (!enabled()) return;
        int i = indexOf(pass);
        if (i < 0 || starts[i] == 0L) return;
        long elapsed = System.nanoTime() - starts[i];
        starts[i] = 0L;
        if (i < TOP_LEVEL.length) currentTopLevel[i] += elapsed;
        else currentInside[i - TOP_LEVEL.length] += elapsed;
    }

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
     * {@return the per-pass split of the frames measured so far}, as p50/p95/worst milliseconds plus the residual.
     * <p>
     * The residual is per frame ({@code frame - sum(top-level)}) and then summarised, not a difference of
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
