package org.soutalab.celtium.vulkan;

import org.soutalab.celtium.harness.HarnessConfig;

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
 * percentiles. Off unless the harness asks for it ({@code tickTimings=true} in the harness file, the form the
 * retired {@code -Dceltium.vulkan.tickTimings=true} now takes), the same rule the other repeating instruments
 * follow: a bracket that runs per sprite per tick is not free.
 * <p>
 * TickSplit2 (item 407) extends this split because the legacy residual could not be attributed: {@code tickOther}
 * was a difference of percentiles (the total's p50 minus the top brackets' p50s), not a percentile of per-tick
 * residuals, and a top bracket whose p50 fell under the 5 us print threshold was never subtracted at all. With
 * {@code tickSplit2=true} in the harness file, the tick gains the {@code tickTop2} brackets (disjoint call sites
 * inside {@code runTick}, hooked by the tick-split mixins), a per-tick residual filed beside the total
 * ({@code total - union(tickTop2)}, the union taken by {@link PassTimingCoverage} rather than a sum), and a
 * second report line, {@code CELTIUM_TICK_SPLIT2}, which prints every top bracket with its calls per tick and
 * no print threshold on the subtraction side. The legacy line is printed exactly as before whenever the legacy
 * switch is on. Two names stay deliberately unhooked, so their zero call counts are the "not separated" signal
 * rather than a silent gap: {@code tickSched} (the scheduled-executables drain runs in {@code runGameLoop},
 * outside the tick this class measures) and {@code tickFixture} (the validation client's tick hook may only
 * reach the celtium-vulkan classes reflectively, so it cannot bracket itself directly; its work rides inside
 * the FML dispatch that {@code tickFml} does measure).
 * <p>
 * {@code tickTimingsCpu=true} switches this class's clock to the client thread's CPU time, the
 * {@link FramePassTimings} {@code passSplitCpu} precedent carried to the tick: the wall-minus-cpu difference of
 * the same leg shape is the upper bound on time the tick spent not working (GC pauses, preemption).
 */
public final class TickTimings {

    /**
     * The legacy split's switch, resolved through {@link HarnessConfig} and re-taken at every
     * {@link #beginTick(boolean)}; see {@link #resolveSwitches()} for why the re-take exists. The answers stay
     * field reads on the bracket path: a property lookup per bracket is exactly the tax
     * {@link FramePassTimings}' history records.
     */
    private static volatile boolean ENABLED;
    /** TickSplit2 (item 407): independent of the legacy switch, so a leg can take either split alone. */
    private static volatile boolean SPLIT2;
    /**
     * The tick clock's CPU form (item 407, layer 3), re-resolved with the other switches rather than once, the
     * same move {@link FramePassTimings}' clock switch made: {@link #now()} reads the field, never a property,
     * so the bracket path still pays no lookup.
     */
    private static volatile boolean CPU_CLOCK_TICK;
    private static final java.lang.management.ThreadMXBean THREAD_BEAN =
            java.lang.management.ManagementFactory.getThreadMXBean();
    private static final boolean THREAD_CPU_SUPPORTED = THREAD_BEAN.isCurrentThreadCpuTimeSupported();
    /** The measured window's first tick (inclusive), re-resolved with the switches. */
    private static volatile int FROM;
    /** The measured window's end tick (exclusive), re-resolved with the switches. */
    private static volatile int TO;
    /**
     * The row capacity the storage is allocated with, derived from the window at each resolution. The arrays
     * themselves are allocated once, at the first measured tick ({@link #ensure()}), with the capacity then in
     * force, and {@link #endTick()} files against the allocated length - so a later resolution that widens the
     * window cannot write past them.
     */
    private static volatile int CAPACITY;

    /**
     * (Re-)resolves every harness switch this class keeps, through {@link HarnessConfig}: the two split
     * switches, the clock switch and the window ({@link #FROM} / {@link #TO}, with the {@link #CAPACITY}
     * derived from them).
     * <p>
     * Called once when this class initialises, so a run that never opens a tick observes exactly the old
     * class-init resolution, and again at every {@link #beginTick(boolean)} - once a tick, never per bracket,
     * so the property walk stays off the bracket path. The tick open is the moment the configuration of the
     * tick being recorded is taken, and taking it there is what makes the resolution robust against this class
     * being initialised earlier by an unrelated touch: {@link FramePassTimings#enabled()} asks this class's
     * switches, so any touch of the frame timings - a production path a unit test exercises, ahead of the
     * harness configuration - initialises this class too, and a resolution fixed at that moment used to hold
     * for the whole JVM. This is the same latch {@link FramePassTimings} carried until its own fix, whose
     * record named this class as the remaining carrier (item 689). A player's run fixes its configuration
     * before launch, so the re-take reads the same values every tick and changes nothing there.
     */
    private static void resolveSwitches() {
        ENABLED = HarnessConfig.bool("tickTimings", false);
        SPLIT2 = HarnessConfig.bool("tickSplit2", false);
        CPU_CLOCK_TICK = HarnessConfig.bool("tickTimingsCpu", false);
        FROM = HarnessConfig.integer("tickTimingsFrom", 400);
        TO = HarnessConfig.integer("tickTimingsTo", 600);
        CAPACITY = Math.max(64, TO - FROM + 8);
    }

    static {
        resolveSwitches();
    }

    /** {@return whether either tick split is switched on}. */
    public static boolean enabled() {
        return ENABLED || SPLIT2;
    }

    /** {@return whether TickSplit2 (the per-tick residual and the tickTop2 brackets) is switched on}. */
    public static boolean split2Enabled() {
        return SPLIT2;
    }

    /** {@return whether the tick clock reads CPU time instead of wall time}. */
    public static boolean cpuClock() {
        return CPU_CLOCK_TICK && THREAD_CPU_SUPPORTED;
    }

    /** The timestamp the tick brackets compare, in nanoseconds under the selected clock. */
    private static long now() {
        return CPU_CLOCK_TICK && THREAD_CPU_SUPPORTED ? THREAD_BEAN.getCurrentThreadCpuTime() : System.nanoTime();
    }

    /**
     * {@return the per-tick residual: what is left of one tick after the covered span is taken out}.
     * <p>
     * Clamped at zero, because a covered span read on a coarser clock can overtake the total it was taken from;
     * a negative residual would be an artefact of the clocks, not a tick that happened.
     */
    static long residualFromCovered(long total, long covered) {
        return Math.max(0L, total - covered);
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
    /** TickSplit2's top-level brackets: the legacy three plus the runTick call sites, mutually disjoint. */
    private static int[] tickTop2;
    /** TickSplit2's reported-only children, in print order. */
    private static int[] tickChildren2;
    /** Per-tick TickSplit2 residual ({@code total - union(tickTop2)}), filed beside {@link #totals}. */
    private static long[] residuals2;
    /** Coverage slot per bracket column for {@link #tickTop2}, or -1 for a bracket the union does not subtract. */
    private static int[] top2Slot;
    /** Whether a bracket column is a TickSplit2 name, gated off while {@link #SPLIT2} is off. */
    private static boolean[] split2Column;
    /** The tickTop2 union, tracked live from the brackets' outermost opens and closes. */
    private static PassTimingCoverage coverage2;

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
        // TickSplit2's top set: the legacy three (their call sites are inside runTick too) plus the disjoint
        // runTick call sites the tick-split mixins bracket. tickSched and tickFixture are members so the report
        // prints them - with calls=0, the declared "not separated" state - but no hook ever opens them, so they
        // contribute nothing to the union. tickWorld accumulates two call sites (World.updateEntities and
        // WorldClient.tick); the children below sit inside those two spans.
        tickTop2 = new int[] {
                FramePassTimings.indexOfName("anim"),
                FramePassTimings.indexOfName("dynUpload"),
                FramePassTimings.indexOfName("lightCache"),
                TickSplit2Index.TICK_SCHED,
                TickSplit2Index.TICK_NET,
                TickSplit2Index.TICK_INPUT,
                TickSplit2Index.TICK_GAME_MODE,
                TickSplit2Index.TICK_SOUND,
                TickSplit2Index.TICK_GUI_TICK,
                TickSplit2Index.TICK_FML,
                TickSplit2Index.TICK_WORLD,
                TickSplit2Index.TICK_LVL_RENDERER,
                TickSplit2Index.TICK_FIXTURE,
        };
        tickChildren2 = new int[] {
                TickSplit2Index.TICK_ENT,
                TickSplit2Index.TICK_TILE_ENT,
                TickSplit2Index.TICK_PARTICLES,
                TickSplit2Index.TICK_CHUNKS,
                TickSplit2Index.TICK_DRAIN,
        };
        residuals2 = new long[CAPACITY];
        top2Slot = new int[columns];
        Arrays.fill(top2Slot, -1);
        for (int slot = 0; slot < tickTop2.length; slot++) {
            top2Slot[tickTop2[slot]] = slot;
        }
        split2Column = new boolean[columns];
        for (int top : tickTop2) {
            // The legacy three are not gated: they are the legacy split's own brackets.
            if (top != tickTop[0] && top != tickTop[1] && top != tickTop[2]) {
                split2Column[top] = true;
            }
        }
        for (int child : tickChildren2) {
            split2Column[child] = true;
        }
        coverage2 = new PassTimingCoverage(tickTop2.length);
    }

    /** {@return TickSplit2's top-level bracket indices}, for the unit test that pins their disjointness. */
    static int[] tickTop2Indices() {
        ensure();
        return tickTop2.clone();
    }

    /**
     * Opens the tick's bracket.
     *
     * @param hasWorld whether a client world exists; a tick without one (the title screen) is not counted, so the
     *                 window's tick numbers mean the same thing the fixture's {@code worldTicks} mean
     */
    public static void beginTick(boolean hasWorld) {
        // The tick's configuration is taken here, once per tick: see resolveSwitches().
        resolveSwitches();
        if (!ENABLED && !SPLIT2) {
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
        // The tick window is the other context FramePassTimings' brackets delegate into; the thread that
        // opens it owns those tables too (see FramePassTimings' contextThread).
        FramePassTimings.noteContextThread();
        tickStart = now();
        Arrays.fill(current, 0L);
        Arrays.fill(starts, 0L);
        Arrays.fill(depth, 0);
        if (SPLIT2) {
            coverage2.reset();
        }
    }

    /** Closes the tick's bracket and files the row; prints the report when the window ends. */
    public static void endTick() {
        if ((!ENABLED && !SPLIT2) || !open) {
            return;
        }
        open = false;
        long tickEnd = now();
        long total = tickEnd - tickStart;
        // The allocated length, not the current CAPACITY: a re-resolution may have widened the window
        // after ensure() allocated the rows (see the CAPACITY field note).
        if (recorded < totals.length) {
            totals[recorded] = total;
            for (int i = 0; i < current.length; i++) {
                sums[i][recorded] = current[i];
            }
            if (SPLIT2) {
                // The union is read at the tick's own end, so a top bracket still open here is clipped to the
                // tick instead of leaking into the next one (the coverage class's coveredNanos contract).
                residuals2[recorded] = residualFromCovered(total, coverage2.coveredNanos(tickEnd));
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
        if (!SPLIT2 && split2Column[index]) {
            return;
        }
        calls[index]++;
        if (depth[index]++ == 0) {
            starts[index] = now();
            if (SPLIT2 && top2Slot[index] >= 0) {
                coverage2.begin(top2Slot[index], starts[index]);
            }
        }
    }

    /** Closes one named bracket inside the tick. */
    public static void end(int index) {
        if (!open || index < 0 || depth[index] == 0) {
            return;
        }
        if (--depth[index] == 0) {
            long ended = now();
            current[index] += ended - starts[index];
            starts[index] = 0L;
            if (SPLIT2 && top2Slot[index] >= 0) {
                coverage2.end(top2Slot[index], ended);
            }
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
            if (ENABLED) {
                System.out.println("[Celtium] CELTIUM_TICK_SPLIT ticks=0 window=[" + FROM + "," + TO
                        + ") (the instrument did not record; enabled=" + ENABLED + ")");
            }
            if (SPLIT2) {
                System.out.println("[Celtium] CELTIUM_TICK_SPLIT2 ticks=0 window=[" + FROM + "," + TO
                        + ") (the instrument did not record; split2=" + SPLIT2 + ")");
            }
            return;
        }
        long[] total = Arrays.copyOf(totals, recorded);
        Arrays.sort(total);
        long totalP50 = total[recorded / 2] / 1000L;
        long totalP95 = total[Math.min(recorded - 1, recorded * 95 / 100)] / 1000L;
        long named = 0L;
        if (ENABLED) {
            StringBuilder line = new StringBuilder("[Celtium] CELTIUM_TICK_SPLIT ticks=").append(recorded)
                    .append(" window=[").append(FROM).append(',').append(TO).append(") tick[p50=").append(totalP50)
                    .append(" p95=").append(totalP95).append("] (us; p50/p95/calls-per-tick, brackets with p50 >= 5)");
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
        } else {
            // The SPLIT2 line carries the legacy residual for the attribution bars, computed by the legacy
            // rule (a difference of percentiles over the brackets the print threshold lets through).
            for (int i = 0; i < current.length; i++) {
                long[] values = Arrays.copyOf(sums[i], recorded);
                Arrays.sort(values);
                long p50 = values[recorded / 2] / 1000L;
                if (p50 < 5L) {
                    continue;
                }
                for (int top : tickTop) {
                    if (top == i) {
                        named += p50;
                    }
                }
            }
        }
        if (SPLIT2) {
            long[] residual = Arrays.copyOf(residuals2, recorded);
            Arrays.sort(residual);
            long residualP50 = residual[recorded / 2] / 1000L;
            long residualP95 = residual[Math.min(recorded - 1, recorded * 95 / 100)] / 1000L;
            StringBuilder line = new StringBuilder("[Celtium] CELTIUM_TICK_SPLIT2 ticks=").append(recorded)
                    .append(" window=[").append(FROM).append(',').append(TO).append(") clock=")
                    .append(cpuClock() ? "cpu" : "wall")
                    .append(" tick[p50=").append(totalP50).append(" p95=").append(totalP95)
                    .append("] (us; p50/p95/calls-per-tick, every tickTop2 bracket, no print threshold)");
            for (int top : tickTop2) {
                appendBracket(line, top);
            }
            for (int child : tickChildren2) {
                appendBracket(line, child);
            }
            line.append(" tickOther=").append(Math.max(0L, totalP50 - named))
                    .append(" tickOther2[p50=").append(residualP50).append(" p95=").append(residualP95).append(']');
            System.out.println(line);
        }
    }

    /** Appends one bracket's {@code name=p50/p95/calls-per-tick} to a TickSplit2 report line. */
    private static void appendBracket(StringBuilder line, int index) {
        long[] values = Arrays.copyOf(sums[index], recorded);
        Arrays.sort(values);
        long p50 = values[recorded / 2] / 1000L;
        long p95 = values[Math.min(recorded - 1, recorded * 95 / 100)] / 1000L;
        line.append(' ').append(FramePassTimings.nameOf(index)).append('=').append(p50).append('/').append(p95)
                .append('/').append(calls[index] / Math.max(1, recorded));
    }
}
