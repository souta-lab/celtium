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
     * <p>
     * {@code recordPrepare} and {@code recordBatch} nest inside {@code terrainRecord}: a 779 ms record stall on Intel
     * (leg {@code loading-resize-state2-intel}, worldTick 473, 25 sections) did not scale with section count, so the
     * pass's fixed prepare work (uniform capacity, descriptors, barriers, framebuffer, render-pass begin) and its
     * per-region cached-batch rebuilds needed separate names before a cause could be claimed. The prepare split adds
     * {@code prepDescriptors} (uniform capacity + descriptor writes through {@code bindTextures}),
     * {@code prepBarriers} (acquire barriers + published-texture transitions) and {@code prepBegin} (render-pass
     * begin through pipeline bind) - recordPrepare ran ~70-85% of record on both GPUs, and these three names say
     * which of its driver-visible calls owns that.
     */
    private static final String[] INSIDE = {"entities", "particles", "present", "flush", "terrain", "chunkUpload",
            "setupTerrain", "stGraph", "stEvents", "stAsync", "stPrepare", "stUpload", "stUpdate", "stVisible", "stReclaim", "sky", "terrainHandoff", "terrainRecord", "terrainSubmit", "recordPrepare",
            "recordBatch", "prepDescriptors", "prepBarriers", "prepBegin",
            "recBind", "recDraw", "recLoop", "terrainTarget", "terrainSetup", "terrainClose", "terrainPrep", "terrainCapacity", "terrainSharedIndex", "terrainPreflush", "flushCloseGroup",
            "winBarrier", "winRecord", "winSubmit", "winEnd", "winBeginPass", "winTransition",
            // The terrain pass' three unmeasured spans: the fixed prepare work before recording (frame target,
            // pipeline lookup, shared index buffer, uniform capacity, atlas/lightmap mirrors), the command-buffer
            // begin, and the frame end after the submission. The pass' bracket (3.5 ms p50 against a 0.9 ms record
            // and 0.2 ms submit) leaves that gap unnamed, and the three candidates have different owners.
            "terrainEnsure", "terrainBegin", "terrainEnd",
            // The immediate path's two untimed spans: closing the batch still being filled (per group, and the
            // group's payload capture is the expensive half) and the window's own setup before it records
            // (recording snapshot, frame begin, target/device gates). They were the residue of the batch call's
            // measured parts (batchCallMillis 3,153 ms against ~1,450 ms of named sub-timers).
            "closeGroup", "windowSetup",
            // The flush's per-group halves: everything the group's own draw needs prepared (descriptor resolution,
            // push-block fill, viewport and bias state) against the pipeline call that binds and draws it.
            "grpPrep", "grpDraw",
            // The per-group body of the immediate flush, in four pieces: the state/descriptor resolution, the
            // push-block fill (matrix, fog, colour, light map), the viewport/scissor/depth-bias state, and the
            // pipeline's bind+draw call. ~2.5 us a group at 332 groups a frame is the largest renderer-owned item
            // left, and the four names say which piece it is.
            "rcResolve", "rcPush", "rcViewport",
            // The offer path's four pieces: the GL state capture and its key, the pipeline/descriptor preparation,
            // the group-match decision, and the vertex append (the transform for a pre-transformed group, a memcpy
            // otherwise). 0.71 ms a frame for ~292 batches is 2.4 us each, and the names say which part owns it.
            "offCapture", "offPrepare", "offMerge", "offAppend",
            // The flush's non-recording halves: the frame target's begin (semaphores and slot state), and the region
            // between the begin and the acquire barriers - target resolution, group filtering, the queued clear and
            // the diagnostics. actualFlush minus winRecord left more than half of the flush unaccounted for, and these
            // two names say which side of the pass it is on.
            "wfpBegin", "wfpMiddle",
            // The immediate window's group loop on its own: the window's recording is 0.95 ms a frame and the loop's
            // per-group work is the part the driver-call probes could not explain (skipping the pushes and the draws
            // changed neither the loop's own timer nor the frame).
            "wrecLoop", "rcPrepared"};

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
    /**
     * Resolved once: {@code Boolean.getBoolean} walks {@code System.getProperty} into a ConcurrentHashMap, and this is
     * asked on both sides of every bracket - the JFR census of the shipping configuration put that lookup at 1.8% of the
     * client thread's samples, 65 of the 87 ConcurrentHashMap.get samples, from the instrumentation itself. The switch is
     * a JVM argument, so it cannot change after this class initialises.
     */
    private static final boolean PASS_SPLIT_ENABLED = Boolean.getBoolean("celtium.vulkan.passSplit");

    public static boolean enabled() {
        // Deliberately not VulkanDiagnostics.enabled(): the acceptance fixture always passes celtium.vulkan.validation,
        // and tying the brackets to it put eight name lookups and two clock reads on every group of every acceptance leg -
        // the frame being judged paid for the instrument judging it. A run that wants the brackets asks for them.
        return PASS_SPLIT_ENABLED || BRACKETS_ENABLED;
    }

    /** {@code -Dceltium.vulkan.brackets=true}: keep the pass brackets on without the pass-split report. */
    private static final boolean BRACKETS_ENABLED = Boolean.getBoolean("celtium.vulkan.brackets");

    /**
     * The clock the brackets read.
     * <p>
     * {@code -Dceltium.vulkan.passSplitCpu=true} switches every bracket to the render thread's own CPU time, which
     * subtracts scheduler preemption and GC stop-the-world pauses from what the frame wall clock counts. The need is
     * the same one the immediate path's {@code getCurrentThreadCpuTime} split already serves (OPT-01): leg
     * perf-prep1 measured {@code prepBarriers} at ~95% of recordPrepare (5 ms AMD, 10.4 ms Intel p50) for what is
     * only a handful of {@code vkCmd*} recording calls, which are microseconds of CPU each - that gap is either real
     * driver work or preemption counted as work, and only a CPU clock separates them. Default stays wall time, so
     * every measured number before this flag keeps its meaning.
     */
    private static final boolean CPU_CLOCK = Boolean.getBoolean("celtium.vulkan.passSplitCpu");
    private static final java.lang.management.ThreadMXBean THREAD_BEAN =
            java.lang.management.ManagementFactory.getThreadMXBean();
    private static final boolean THREAD_CPU_SUPPORTED = THREAD_BEAN.isCurrentThreadCpuTimeSupported();

    /** {@return the timestamp the brackets compare, in nanoseconds under the selected clock}. */
    private static long clockNanos() {
        return CPU_CLOCK && THREAD_CPU_SUPPORTED ? THREAD_BEAN.getCurrentThreadCpuTime() : System.nanoTime();
    }

    /**
     * The brackets summed separately for frames drawn right after a client tick and for the rest, printed every
     * {@link #TICK_SPLIT_FRAMES} frames as {@code CELTIUM_FRAME_TICK_PASSES}: Intel's warm p99 is the tick frames, and
     * the frame drawn after a tick costs ~0.3 ms more than a plain one - these say which bracket holds it.
     */
    private static final int TICK_SPLIT_FRAMES = 4000;
    private static boolean tickSinceLastFrame;
    private static final long[] tickFrameSums = new long[1 + TOP_LEVEL.length + INSIDE.length];
    private static final long[] plainFrameSums = new long[1 + TOP_LEVEL.length + INSIDE.length];
    private static int tickFrames;
    private static int plainFrames;

    /** A client tick ended; the next frame to end is a tick frame. */
    public static void noteTick() {
        tickSinceLastFrame = true;
    }

    private static void fileTickSplit() {
        long[] into = tickSinceLastFrame ? tickFrameSums : plainFrameSums;
        if (tickSinceLastFrame) tickFrames++; else plainFrames++;
        tickSinceLastFrame = false;
        into[0] += currentFrameTotal;
        for (int i = 0; i < TOP_LEVEL.length; i++) into[1 + i] += currentTopLevel[i];
        for (int i = 0; i < INSIDE.length; i++) into[1 + TOP_LEVEL.length + i] += currentInside[i];
        if (tickFrames + plainFrames < TICK_SPLIT_FRAMES || tickFrames == 0) return;
        StringBuilder line = new StringBuilder("[Celtium] CELTIUM_FRAME_TICK_PASSES tickFrames=").append(tickFrames)
                .append(" plainFrames=").append(plainFrames).append(" (mean us, tick/plain, brackets differing by"
                        + " >=10 us)");
        for (int k = 0; k < tickFrameSums.length; k++) {
            long tick = tickFrameSums[k] / tickFrames / 1000L;
            long plain = plainFrameSums[k] / Math.max(1, plainFrames) / 1000L;
            if (k == 0 || Math.abs(tick - plain) >= 10L) {
                String name = k == 0 ? "frame" : k <= TOP_LEVEL.length ? TOP_LEVEL[k - 1] : INSIDE[k - 1 - TOP_LEVEL.length];
                line.append(' ').append(name).append('=').append(tick).append('/').append(plain);
            }
        }
        System.out.println(line);
        Arrays.fill(tickFrameSums, 0L);
        Arrays.fill(plainFrameSums, 0L);
        tickFrames = 0;
        plainFrames = 0;
    }

    /** Brackets the frame itself. */
    public static void beginFrame() {
        if (!enabled()) return;
        coverage.reset();
        Arrays.fill(starts, 0L);
        Arrays.fill(insideDepth, 0);
        Arrays.fill(currentTopLevel, 0L);
        Arrays.fill(currentInside, 0L);
        frameStart = clockNanos();
    }

    /** Closes the frame bracket and files the frame's record. */
    public static void endFrame() {
        if (!enabled() || frameStart == 0L) return;
        long frameEnd = clockNanos();
        currentFrameTotal = frameEnd - frameStart;
        frameStart = 0L;
        long residual = currentFrameTotal - coverage.coveredNanos(frameEnd);
        if (residual < 0L) residual = 0L;
        int slot = written % CAPACITY;
        frameTotals[slot] = currentFrameTotal;
        residuals[slot] = residual;
        for (int i = 0; i < TOP_LEVEL.length; i++) topLevel[i][slot] = currentTopLevel[i];
        for (int i = 0; i < INSIDE.length; i++) inside[i][slot] = currentInside[i];
        fileTickSplit();
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
        // The GUI flag first, by identity: the call sites pass string literals, so this is the same comparison indexOf
        // made, without the hash lookup. It has to stay unconditional because the group logic reads it while the timings
        // are off.
        if (pass == "gui") {
            guiActive = true;
        }
        // The call counts used to run on every bracket whether the timings were enabled or not - 1.1% of the render
        // thread in a steady-state JFR, spent on a hash lookup per bracket. The question they answer ("was the bracket
        // opened at all, or opened and empty?") is only asked while diagnosing, so they are opt-in now.
        // Resolved when anything needs it: the timings themselves when they are enabled, or the counts when asked for.
        // Skipping it when both are off is the point - it is the hash lookup that cost 1.1% of the render thread.
        int counted = enabled() || COUNT_CALLS ? indexOf(pass) : -1;
        if (counted >= 0) BEGIN_CALLS[counted]++;
        if (!enabled() || frameStart == 0L || counted < 0) return;
        int i = counted;
        long now = clockNanos();
        boolean outermost = i < TOP_LEVEL.length ? coverage.begin(i, now)
                : insideDepth[i - TOP_LEVEL.length]++ == 0;
        if (outermost) starts[i] = now;
    }

    /** Closes a pass bracket and adds its time to the current frame. */
    public static void end(String pass) {
        // The GUI flag is the only thing this method needs with the timings off, and it is one string comparison
        // against a literal - which is a pointer compare for the interned name the mixin passes - rather than the
        // map lookup the index needs. With the timings on, both are wanted and the lookup is paid once as before.
        // Measured: with the timings off, FramePassTimings.end was 1.4% of the client thread's JFR samples in
        // HashMap tree-bin lookups, because every bracket close in the frame went through the map.
        if (GUI_PASS.equals(pass)) {
            guiActive = false;
        }
        if (!enabled() || frameStart == 0L) return;
        int i = indexOf(pass);
        if (i < 0 || starts[i] == 0L) return;
        long now = clockNanos();
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

    /**
     * Name to bracket index, built once.
     * <p>
     * The lookup used to be two linear scans with string compares, run on every bracket open and twice on every close.
     * The dense world scene (leg splitD1, qmagnet's exhibition) opens several thousand brackets a frame - ~1,955
     * immediate batches each with their own spans - and JFR put 1.4% of the client thread's samples in this scan
     * alone. A bracket name is a literal, so a map is the right shape: it costs one hash instead of up to
     * {@code TOP_LEVEL.length + INSIDE.length} string comparisons.
     */
    private static final java.util.Map<String, Integer> INDEX = buildIndex();

    private static java.util.Map<String, Integer> buildIndex() {
        java.util.Map<String, Integer> index = new java.util.HashMap<String, Integer>();
        for (int i = 0; i < TOP_LEVEL.length; i++) {
            index.put(TOP_LEVEL[i], Integer.valueOf(i));
        }
        for (int i = 0; i < INSIDE.length; i++) {
            index.put(INSIDE[i], Integer.valueOf(TOP_LEVEL.length + i));
        }
        // A plain map: the wrapper added a call per lookup and the table is private and never mutated after this, while
        // the JFR showed Collections$UnmodifiableMap.get inside the hottest bracket path (validation runs keep the
        // brackets enabled, because diagnostics turns them on).
        return index;
    }

    /** The index of {@code gui} in the pass tables, so the flag is set by name rather than by a literal. */
    /**
     * A direct-mapped cache over the bracket-name lookup.
     * <p>
     * Bracket names are literals, so the same handful of strings repeat for the whole session; a 32-slot table keyed by
     * identity hash answers almost every call with a reference compare instead of a map get. Measured with the timings
     * disabled - the shipping configuration - {@code FramePassTimings.indexOf} was still 1.4% of the client thread's JFR
     * samples, because both begin and end resolve the name even when nothing is being timed.
     */
    private static final String[] INDEX_CACHE_NAME = new String[256];
    private static final int[] INDEX_CACHE_VALUE = new int[256];

    /** The GUI bracket's name: {@link #end} compares it directly so the disabled path pays no map lookup. */
    private static final String GUI_PASS = "gui";
    private static final int GUI_INDEX = indexOf("gui");

    /**
     * Whether per-bracket call counts are kept while the timings are disabled.
     * <p>
     * Off by default: the counts cost a hash lookup on every bracket, measured at 1.1% of the render thread in a
     * steady-state profile. They exist to separate "the bracket never ran" from "the bracket ran and measured zero", a
     * question asked while diagnosing, so they are a switch rather than a permanent tax.
     */
    private static final boolean COUNT_CALLS = Boolean.getBoolean("celtium.vulkan.passCounts");

    /** {@return whether per-bracket call counting is on} ({@code -Dceltium.vulkan.passCounts}). */
    public static boolean countsCallsEnabled() {
        return COUNT_CALLS;
    }

    private static int indexOf(String pass) {
        // 256 slots, not 32: the brackets number thirty-odd names and are entered in a repeating order, so a 32-slot
        // direct-mapped table collides and falls back to the map on the very path the cache exists for.
        int slot = System.identityHashCode(pass) & 255;
        if (INDEX_CACHE_NAME[slot] == pass) {
            return INDEX_CACHE_VALUE[slot];
        }
        Integer found = INDEX.get(pass);
        int index = found == null ? -1 : found.intValue();
        INDEX_CACHE_NAME[slot] = pass;
        INDEX_CACHE_VALUE[slot] = index;
        return index;
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
        StringBuilder text = new StringBuilder("clock=").append(CPU_CLOCK && THREAD_CPU_SUPPORTED ? "cpu" : "wall")
                .append(" frames=").append(n);
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
