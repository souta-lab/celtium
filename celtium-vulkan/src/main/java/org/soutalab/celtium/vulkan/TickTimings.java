package org.soutalab.celtium.vulkan;

import java.util.Arrays;

/**
 * Splits the <em>client tick</em> the way {@link FramePassTimings} splits the frame.
 * <p>
 * Why this exists: the paired KPI's largest Celtium-specific number is not inside the frame bracket at all. The
 * fixture measures {@code Minecraft.runTick} head to return ({@code warmTickP50Ms} / {@code warmTickP95Ms}, the same
 * game code in both arms) and Celtium reads 1.827 / 2.258 ms against Celeritas's 1.696 / 2.010 - <b>+131 us at the
 * median and +248 us at p95</b> - while the tick frames' p95 gap is 520 us. The tick is therefore half of the p99's
 * excess, and Celtium's named work inside it was only the texture-animation announcement and the dynamic-texture
 * path (33 us of the frame). The other ~100 us had no name at all.
 * <p>
 * The frame brackets cannot answer it: {@link FramePassTimings#begin} returns early while no frame is open, and the
 * tick runs between frames. This class keeps its own row per tick and its own percentile report, and
 * {@code FramePassTimings} forwards every bracket to it while a tick is open - so the existing names
 * ({@code dynUpload}, {@code dynAnnounce}, {@code dynMirror}, {@code lightmap}, ...) are measured in the tick with no
 * call-site change, and the tick-only names ({@code anim}, {@code animUpload}, {@code animMirror}, {@code animFlush},
 * {@code animBatchEnd}, {@code animStub}, {@code lightCache}) are ordinary bracket names.
 * <p>
 * The window is the fixture's warm window by default ({@code [400, 600)} ticks after the world loads), because that
 * is the interval the KPI judges; a tick outside it is not recorded, so the world-load wave cannot dilute the
 * percentiles. Off unless {@code -Dceltium.vulkan.tickTimings=true}, the same rule the other repeating instruments
 * follow: a bracket that runs per sprite per tick is not free.
 */
public final class TickTimings {

    private static final boolean ENABLED = Boolean.getBoolean("celtium.vulkan.tickTimings");
    private static final int FROM = Integer.getInteger("celtium.vulkan.tickTimingsFrom", 400).intValue();
    private static final int TO = Integer.getInteger("celtium.vulkan.tickTimingsTo", 600).intValue();
    private static final int CAPACITY = Math.max(64, TO - FROM + 8);

    /** {@return whether the tick split is switched on}. */
    public static boolean enabled() {
        return ENABLED;
    }

    /** Whether a tick is being measured right now; {@link FramePassTimings} routes its brackets here while it is. */
    private static boolean open;

    /** {@return whether a measured tick is open}. */
    public static boolean open() {
        return open;
    }

    /** Ticks with a world loaded, counted by the mixin; the window is expressed in this unit. */
    private static int tickIndex;
    private static long tickStart;
    private static int recorded;
    private static boolean reported;

    /**
     * The per-bracket storage, allocated on the first measured tick rather than in the class initialiser.
     * <p>
     * The width is {@link FramePassTimings}' name count, and a static initialiser here would run while that class may
     * still be initialising (its {@code enabled()} asks this class's switch) - a circular initialisation that would
     * read a half-built name table. Allocating on first use removes the cycle: by then both classes are complete.
     */
    private static long[] totals;
    private static long[][] sums;
    private static long[] current;
    private static long[] starts;
    private static int[] depth;
    private static long[] calls;
    /** The tick's own top-level brackets, whose union the residual subtracts. */
    private static int[] tickTop;

    private TickTimings() {
    }

    private static void ensure() {
        if (totals != null) {
            return;
        }
        int columns = FramePassTimings.bracketCount();
        totals = new long[CAPACITY];
        sums = new long[columns][CAPACITY];
        current = new long[columns];
        starts = new long[columns];
        depth = new int[columns];
        calls = new long[columns];
        // `anim` holds the whole texture-animation pass (and the per-sprite uploads inside it), `dynUpload` the
        // dynamic-texture upload the light map makes, and `lightCache` Celtium's per-tick light lookups - the three
        // places Celtium's own code runs inside Minecraft.runTick.
        tickTop = new int[] {
                FramePassTimings.indexOfName("anim"),
                FramePassTimings.indexOfName("dynUpload"),
                FramePassTimings.indexOfName("lightCache"),
        };
    }

    /**
     * Opens the tick's bracket.
     *
     * @param hasWorld whether a client world exists; a tick without one (the title screen) is not counted, so the
     *                 window's tick numbers mean the same thing the fixture's {@code worldTicks} mean
     */
    public static void beginTick(boolean hasWorld) {
        if (!ENABLED) {
            return;
        }
        if (!hasWorld) {
            open = false;
            return;
        }
        tickIndex++;
        if (tickIndex < FROM || tickIndex >= TO) {
            open = false;
            return;
        }
        ensure();
        open = true;
        tickStart = System.nanoTime();
        Arrays.fill(current, 0L);
        Arrays.fill(starts, 0L);
        Arrays.fill(depth, 0);
    }

    /** Closes the tick's bracket and files the row; prints the report when the window ends. */
    public static void endTick() {
        if (!ENABLED || !open) {
            return;
        }
        open = false;
        long total = System.nanoTime() - tickStart;
        if (recorded < CAPACITY) {
            totals[recorded] = total;
            for (int i = 0; i < current.length; i++) {
                sums[i][recorded] = current[i];
            }
            recorded++;
        }
        if (tickIndex + 1 >= TO && !reported) {
            reported = true;
            report();
        }
    }

    /** Opens one named bracket inside the tick. Nested brackets are allowed; the outer one keeps the time. */
    public static void begin(int index) {
        if (!open || index < 0) {
            return;
        }
        calls[index]++;
        if (depth[index]++ == 0) {
            starts[index] = System.nanoTime();
        }
    }

    /** Closes one named bracket inside the tick. */
    public static void end(int index) {
        if (!open || index < 0 || depth[index] == 0) {
            return;
        }
        if (--depth[index] == 0) {
            current[index] += System.nanoTime() - starts[index];
            starts[index] = 0L;
        }
    }

    /**
     * Prints the window's per-bracket percentiles.
     * <p>
     * The total is the tick head-to-return span, so it is directly comparable with the fixture's
     * {@code warmTickP50Ms}. Every bracket whose p50 is at least 5 us is printed with its p50, p95 and calls per
     * tick; the residual ({@code tickOther}) is the total minus the union of the tick's top-level brackets, which is
     * what says whether the named work explains the tick at all.
     */
    private static void report() {
        if (recorded == 0) {
            System.out.println("[Celtium] CELTIUM_TICK_SPLIT ticks=0 window=[" + FROM + "," + TO
                    + ") (the instrument did not record; enabled=" + ENABLED + ")");
            return;
        }
        long[] total = Arrays.copyOf(totals, recorded);
        Arrays.sort(total);
        long totalP50 = total[recorded / 2] / 1000L;
        long totalP95 = total[Math.min(recorded - 1, recorded * 95 / 100)] / 1000L;
        StringBuilder line = new StringBuilder("[Celtium] CELTIUM_TICK_SPLIT ticks=").append(recorded)
                .append(" window=[").append(FROM).append(',').append(TO).append(") tick[p50=").append(totalP50)
                .append(" p95=").append(totalP95).append("] (us; p50/p95/calls-per-tick, brackets with p50 >= 5)");
        long named = 0L;
        for (int i = 0; i < current.length; i++) {
            long[] values = Arrays.copyOf(sums[i], recorded);
            Arrays.sort(values);
            long p50 = values[recorded / 2] / 1000L;
            long p95 = values[Math.min(recorded - 1, recorded * 95 / 100)] / 1000L;
            if (p50 < 5L) {
                continue;
            }
            line.append(' ').append(FramePassTimings.nameOf(i)).append('=').append(p50).append('/').append(p95)
                    .append('/').append(calls[i] / Math.max(1, recorded));
            for (int top : tickTop) {
                if (top == i) {
                    named += p50;
                }
            }
        }
        line.append(" tickOther=").append(Math.max(0L, totalP50 - named));
        System.out.println(line);
    }
}
