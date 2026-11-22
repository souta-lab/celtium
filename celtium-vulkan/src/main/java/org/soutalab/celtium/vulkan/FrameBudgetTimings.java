package org.soutalab.celtium.vulkan;

import org.soutalab.celtium.harness.HarnessConfig;

import java.util.Arrays;
import java.util.Locale;

/**
 * Per-frame split of the renderer's own submission chain: how much of a frame is spent recording commands,
 * calling into the driver, and blocked waiting for the GPU - and how much of it is none of those.
 * <p>
 * Why this exists: the Windows/NVIDIA report (RC5 Supersymmetry log, {@code mclo.gs/Yv63NpH}) is a frame rate
 * near 10 FPS whose cause no number in {@code CELTIUM_VERIFY} names. That report carries present counters
 * ({@code presentPerCallMicros=27}, so presentation is not it), submit counters ({@code immediateSubmitAvgNs=45532}
 * for 21,970 submissions, so the per-call driver cost is not it either), a fence distribution
 * ({@code submitToFenceMs[p50/p95/worst=2/201/5153]}) and a frame count - but nothing that puts those on the same
 * frame, so the log cannot answer "was that 100 ms frame the CPU, the driver call, or the GPU". This class answers
 * exactly that question, per frame, and prints it as it runs.
 * <p>
 * The parts are the renderer's own spans, measured where the work happens rather than inferred from profiler trees:
 * <ul>
 * <li>{@code frame} - wall time between two frame boundaries (the only number the player experiences);</li>
 * <li>{@code record} - the frame's own window pass, {@code recordImmediateOnlyPass};</li>
 * <li>{@code batch} - the immediate batch submission path ({@code submitGeometryBatch}), i.e. the game thread's
 *     per-batch work including any flush it triggers;</li>
 * <li>{@code submit} - the frame-boundary submit, {@code celtium$finishFrameBuffer};</li>
 * <li>{@code fence} - waits on frame fences, the renderer blocking on the GPU;</li>
 * <li>{@code slot} - waits for a frame slot's own previous submission, also the GPU;</li>
 * <li>{@code present} - the swap point, {@code celtium$presentFrame};</li>
 * <li>{@code gpu} - the GPU's own duration for the frame's window passes, from the timestamp queries this flag
 *     turns on for them (0 when the queue cannot record timestamps);</li>
 * <li>{@code terrainGpu} - the GPU's own duration for the frame's terrain passes, summed over the passes the
 *     backend times individually for {@code GpuPassTimingReport}: the main view's passes and the shadow view's
 *     alike, because the pass loop times them through the same bracket and this column is their sum. Read back
 *     with the window figure, so like it, it is the previous frame's measurement filed against this one;</li>
 * <li>{@code other} - {@code frame - (record + batch + submit + fence + slot + present)}, clamped at zero.</li>
 * </ul>
 * <p>
 * What this does <b>not</b> prove: {@code batch} can contain a flush, which is also counted in {@code submit}, so
 * {@code other} is a floor on the unattributed time and not an exact remainder - the double count can only make
 * {@code other} smaller. {@code gpu} covers the timestamped window passes only, and {@code terrainGpu} the
 * terrain passes' own brackets, not any copy or barrier submitted outside the passes, so a frame can be
 * GPU-bound with small numbers in both GPU columns. Neither GPU column is subtracted from the frame - GPU work
 * overlaps the CPU spans that submitted it - so {@code other} stays a CPU-side remainder and the GPU columns
 * separate by comparison: an {@code other} that stays large while {@code terrainGpu} is small is not terrain
 * execution. None of these numbers is a
 * performance claim about a release: they say where a measured run's time went, on that run's driver and scene.
 * <p>
 * Off unless the harness asks for it ({@code frameBudget=true} in the harness file), the same rule the other
 * repeating instruments follow: the
 * acceptance fixtures pay for the instruments that judge them, and a normal run must not. Asking for it also opens
 * the window timestamp path (see {@code timeImmediatePass}), because the {@code gpu} column is what separates "the
 * GPU is the limiter" from "the GPU is idle and the CPU is the limiter" - and a report that cannot say which is the
 * report that could not name the Windows frame rate. That path costs two timestamp writes per window and one
 * readback per frame, which is the instrument's own price and why it stays off by default. The
 * {@code terrainGpu} column adds no recording of its own: the terrain passes write their timestamps whenever the
 * queue can record them (they feed the pass report), and this instrument only sums that readback. The two neighbours are
 * {@link FramePassTimings} (which attributes the frame to the game's own passes, and needs its brackets on) and
 * {@code CELTIUM_BOUNDARY_SPLIT} (which reports submit/slot means every 600 frames with no per-frame record). This
 * one is per frame and opt-in; it costs seven clock reads a frame and nothing else when it is off.
 * <p>
 * The loop span ({@code loopSpan=true}) reuses this class's two clocks - the frame boundary's and the present
 * mark's - to name the one interval none of the spans above cover: from a frame's present to the next frame's
 * boundary. That interval holds the rest of {@code Display.update} after the swap point (the event pump), the
 * game loop itself, and whatever the next frame does before the renderer's boundary - on a tick frame it also
 * holds the tick, which {@code TickTimings} names separately, so the two are read together. Its pair is the
 * tail: boundary to the present that follows it, the renderer's own end-of-frame. Boundary to boundary is the
 * frame column above; outside + the renderer's spans + tail is what that column is made of, and the split says
 * which side of the boundary an unexplained remainder sits on. It costs one clock read at each mark it pairs
 * and nothing when it is off.
 * <p>
 * The other split ({@code otherSplit=true}) decomposes this class's {@code other} against
 * {@link FramePassTimings}' brackets over the same steady window: the terrain pass's own recording, the
 * game's setup and entity work, the world pass's remainder, the pass frame's residual, and the loop span's
 * outside. The two instruments' frames are offset by half a frame, so the split is a comparison of window
 * means, not a per-frame identity - see {@link #otherSplit} for the bucket construction and the bias the
 * {@code batch} double count puts on its remainder.
 */
public final class FrameBudgetTimings {

    /** The harness's {@code frameBudget=true}: measure and print the per-frame split. */
    private static final boolean ENABLED = HarnessConfig.bool("frameBudget", false);

    /**
     * The harness's {@code frameBudgetFrames} (default 60): frames per printed line.
     * <p>
     * A line per frame is unreadable and a line per second is what a tester can correlate with a visible stutter;
     * 60 frames is that second on a healthy session and six seconds on the reported 10 FPS, which is still short
     * enough to bracket a spike.
     */
    private static final int REPORT_EVERY_FRAMES =
            Math.max(1, HarnessConfig.integer("frameBudgetFrames", 60));

    /**
     * The harness's {@code frameBudgetLines} (default 120): how many periodic lines a run may print.
     * <p>
     * Bounded on purpose: a two-hour session must not turn a diagnostic into the log's own performance problem,
     * and the teardown summary carries the session's totals after the last line. The cap is a line count, not a
     * byte count, so its cost is predictable.
     */
    private static final int MAX_LINES =
            Math.max(1, HarnessConfig.integer("frameBudgetLines", 120));

    /** Ring capacity: the samples each printed line and the teardown summary are computed over. */
    private static final int CAPACITY = 512;

    private static final long[] frameRing = new long[CAPACITY];
    private static final long[] recordRing = new long[CAPACITY];
    private static final long[] batchRing = new long[CAPACITY];
    private static final long[] submitRing = new long[CAPACITY];
    private static final long[] fenceRing = new long[CAPACITY];
    private static final long[] slotRing = new long[CAPACITY];
    private static final long[] presentRing = new long[CAPACITY];
    private static final long[] gpuRing = new long[CAPACITY];
    private static final long[] terrainGpuRing = new long[CAPACITY];
    private static final long[] otherRing = new long[CAPACITY];
    /** Intervals between presented frames; see {@link #markPresent}. */
    private static final long[] presentIntervalRing = new long[CAPACITY];

    /** Frames filed since the start; also the ring's write cursor once it wraps. */
    private static int written;
    /** Periodic lines printed so far, against {@link #MAX_LINES}. */
    private static int lines;
    /** Presented frames marked since the start; independent of {@link #written}, which counts world frames. */
    private static int presentIntervals;
    /** Clock of the previous present mark, or 0 before the first one. */
    private static long lastPresentMarkNanos;
    /** Present-cadence lines printed, against the same {@link #MAX_LINES} budget. */
    private static int presentIntervalLines;

    /** The current frame's accumulators, reset at each boundary. */
    private static long currentRecord;
    private static long currentBatch;
    private static long currentSubmit;
    private static long currentFence;
    private static long currentSlot;
    private static long currentPresent;
    /** The last measured GPU window time in this frame; a measurement, not a sum. */
    private static long currentGpu;
    /** The last measured GPU terrain-pass time in this frame; a measurement, not a sum. */
    private static long currentTerrainGpu;
    /** Boundary clock of the previous frame, or 0 before the first boundary. */
    private static long lastBoundaryNanos;

    private FrameBudgetTimings() {
    }

    /** {@return whether the instrument measures at all}. Resolved once: a rejected property cannot change mid-run. */
    public static boolean enabled() {
        return ENABLED;
    }

    /**
     * Closes the frame that just ended and opens the next one.
     * <p>
     * Called at the renderer's frame boundary - the one place every per-frame counter is reset - so the interval it
     * measures is the same frame the other components were measured in. The first boundary only starts the clock:
     * there is no previous frame to file.
     */
    public static void frameBoundary() {
        if (!ENABLED && !LOOP_SPAN) {
            return;
        }
        long now = System.nanoTime();
        if (LOOP_SPAN) {
            loopBoundary(now);
        }
        if (!ENABLED) {
            // The loop span still needs this clock: the tail is measured from the boundary it follows.
            lastBoundaryNanos = now;
            return;
        }
        long previous = lastBoundaryNanos;
        lastBoundaryNanos = now;
        if (previous == 0L) {
            return;
        }
        long frameNanos = now - previous;
        if (frameNanos < 0L) frameNanos = 0L;
        file(frameNanos);
    }

    /** Adds the frame's own window-pass recording time. */
    public static void addRecord(long nanos) {
        if (ENABLED) currentRecord += nanos;
    }

    /** Adds one immediate batch submission's game-thread time (which may include a flush). */
    public static void addBatch(long nanos) {
        if (ENABLED) currentBatch += nanos;
    }

    /** Adds one frame-boundary submit's driver time. */
    public static void addSubmit(long nanos) {
        if (ENABLED) currentSubmit += nanos;
    }

    /** Adds one frame fence wait: the client thread blocked until the GPU signalled. */
    public static void addFenceWait(long nanos) {
        if (ENABLED) currentFence += nanos;
    }

    /** Adds one frame-slot reuse wait: the client thread blocked on this slot's previous submission. */
    public static void addSlotWait(long nanos) {
        if (ENABLED) currentSlot += nanos;
    }

    /** Adds the swap point's own time. */
    public static void addPresent(long nanos) {
        if (ENABLED) currentPresent += nanos;
    }

    /**
     * Records the GPU's duration for this frame's timestamped window passes.
     * <p>
     * A measurement, not a contribution: the readback is the last window's GPU time and replaces rather than adds,
     * so a frame without a readback keeps zero there instead of inheriting the previous frame's number.
     */
    public static void setGpuPass(long nanos) {
        if (ENABLED) currentGpu = nanos;
    }

    /**
     * Records the GPU's duration for this frame's terrain passes, summed over the passes the backend timed.
     * <p>
     * The same measurement rule as {@link #setGpuPass}: the readback is the last frame's terrain time and
     * replaces rather than adds, so a frame without a readback keeps zero there instead of inheriting the
     * previous frame's number. The backend sums only the durations it read back fresh for that frame - the
     * per-pass map it also fills keeps a pass's last measured value across frames, and summing that here would
     * file stale durations for passes whose readback has not landed yet.
     */
    public static void setTerrainGpuPass(long nanos) {
        if (ENABLED) currentTerrainGpu = nanos;
    }

    /** The terrain readback's census, as last pushed by the backend; see {@link #setTerrainTimingCensus}. */
    private static long terrainTimingRead;
    private static long terrainTimingDropped;
    private static long terrainTimingUnavailable;

    /**
     * Records the terrain timestamp readback's census: how many submitted frames' snapshots it has
     * consumed, how many confirmed snapshots it dropped unread because a newer confirmed submission
     * had already overwritten the shared pool, and how many timestamp slots a consumed read found
     * unavailable. The terrain column alone cannot distinguish "the passes cost nothing" from "the
     * readback never landed" - this census is what separates them on the printed lines, and it is
     * the number that proves the readback fires in a real run rather than merely compiling.
     */
    public static void setTerrainTimingCensus(long read, long dropped, long unavailable) {
        if (ENABLED) {
            terrainTimingRead = read;
            terrainTimingDropped = dropped;
            terrainTimingUnavailable = unavailable;
        }
    }

    /** The census as a line suffix, shared by the window line and the summary. */
    private static String terrainTimingCensusText() {
        return " tsRead=" + terrainTimingRead
                + " tsDrop=" + terrainTimingDropped
                + " tsUnavail=" + terrainTimingUnavailable;
    }

    /**
     * Whether the present cadence is measured. It is cheap enough to ask for on its own: the world-frame sample costs
     * four clock reads and a timestamp query per window, while the cadence costs one clock read per present. A leg
     * that only needs the frame period (the menu, or a renderer comparison where the split's own tax would change the
     * number it is trying to measure) turns this on with the harness's {@code presentInterval=true} and pays
     * nothing for the split.
     */
    /** The harness's {@code loopSpan=true}: pair the present mark with the frame boundary (see the class comment). */
    private static final boolean LOOP_SPAN = HarnessConfig.bool("loopSpan", false);

    /**
     * The harness's {@code otherSplit=true}: decompose {@code other} against {@link FramePassTimings}' brackets.
     * <p>
     * The budget frame and the pass frame are offset by half a frame (the pass frame is
     * {@code updateCameraAndRender}; the budget frame is boundary to boundary, and the boundary sits inside the
     * pass frame's world pass), so no per-frame pairing between the two instruments exists and none is faked:
     * the split is statistical, over each instrument's own last-512 window of the same steady scene - the same
     * reading the loop span's outside + tail &asymp; frame check already uses. Every bucket is a mean of bracket
     * means, because means add and percentiles of different brackets describe frames that never happened. The
     * buckets are chosen disjoint by construction (see {@link #otherSplit}); what no bucket covers - the swap
     * point's flush outside the pass frame, the loop glue, the two instruments' phase offset - is printed as
     * {@code unexplained} rather than silently absorbed, and it is biased low: {@code batch} is inside the
     * entities and gui brackets as well as in the budget's own accounted sum, so those two buckets are
     * subtracted once too often by up to the whole of {@code batch}.
     */
    private static final boolean OTHER_SPLIT = HarnessConfig.bool("otherSplit", false);

    /** Bucket member names for {@link #otherSplit}, in the printed order after {@code outside}. */
    private static final String[][] OTHER_BUCKETS = {
            {"terrain"},
            {"setupTerrain"},
            {"entities"},
            {"particles", "particlesLit"},
            {"sky"},
            {"clouds", "weather"},
            {"gui", "hand"},
    };

    /** Bracket indices of {@link #OTHER_BUCKETS}' members, resolved lazily from the shared name table. */
    private static int[][] otherBucketIndex;

    /** The {@code worldPass} bracket's index, for the remainder bucket computed in {@link #otherSplit}. */
    private static int worldPassIndex = -2;

    /**
     * Resolves the bucket indices on first use rather than in this class's static initialiser.
     * <p>
     * Loading {@link FramePassTimings} from a static initialiser would latch its own harness switches at
     * whatever moment some other class happened to load this one - in a test JVM that is before the test
     * that sets the properties those switches read, which silently leaves the pass instrument off there.
     * The split only runs when a leg asked for it, so resolving there costs nothing and loads nothing early.
     */
    private static int[][] otherBucketIndex() {
        int[][] resolved = otherBucketIndex;
        if (resolved == null) {
            resolved = new int[OTHER_BUCKETS.length][];
            for (int b = 0; b < OTHER_BUCKETS.length; b++) {
                resolved[b] = new int[OTHER_BUCKETS[b].length];
                for (int m = 0; m < OTHER_BUCKETS[b].length; m++) {
                    resolved[b][m] = FramePassTimings.indexOfName(OTHER_BUCKETS[b][m]);
                }
            }
            otherBucketIndex = resolved;
        }
        return resolved;
    }

    /** {@return the {@code worldPass} bracket's name-table index}, resolved with {@link #otherBucketIndex()}. */
    private static int worldPassIndex() {
        if (worldPassIndex == -2) {
            worldPassIndex = FramePassTimings.indexOfName("worldPass");
        }
        return worldPassIndex;
    }

    /** Indices of {@link #WORLD_PASS_MEMBERS}, resolved with {@link #otherBucketIndex()}. */
    private static int[] worldPassMemberIndex;

    private static int[] worldPassMemberIndex() {
        int[] resolved = worldPassMemberIndex;
        if (resolved == null) {
            resolved = new int[WORLD_PASS_MEMBERS.length];
            for (int i = 0; i < WORLD_PASS_MEMBERS.length; i++) {
                resolved[i] = FramePassTimings.indexOfName(WORLD_PASS_MEMBERS[i]);
            }
            worldPassMemberIndex = resolved;
        }
        return resolved;
    }

    /** Brackets inside {@code worldPass} that {@link #OTHER_BUCKETS} already names; the rest is worldRest. */
    private static final String[] WORLD_PASS_MEMBERS = {
            "terrain", "setupTerrain", "entities", "particles", "particlesLit", "sky"};

    private static final boolean PRESENT_INTERVAL = ENABLED
            || HarnessConfig.bool("presentInterval", false)
            || LOOP_SPAN;

    /**
     * Marks one presented frame: the cadence where no frame boundary runs.
     * <p>
     * The frame boundary only exists in the world path. The menu presents frames without ever reaching it, so a menu
     * at 6 FPS carried no measurement at all - a report whose frame count was zero while the operator watched a
     * slideshow. The interval between presents is the frame period wherever a frame was presented, and it costs one
     * clock read. It is a separate ring from the world frames so the two are never added: a world frame files both a
     * boundary sample and a present interval, and only the boundary sample has the record/batch/submit split.
     */
    public static void markPresent() {
        if (!PRESENT_INTERVAL) {
            return;
        }
        long now = System.nanoTime();
        if (LOOP_SPAN) {
            loopPresent(now);
        }
        long previous = lastPresentMarkNanos;
        lastPresentMarkNanos = now;
        if (previous == 0L || now <= previous) {
            return;
        }
        presentIntervalRing[presentIntervals % CAPACITY] = now - previous;
        presentIntervals++;
        if (presentIntervals % REPORT_EVERY_FRAMES == 0 && presentIntervalLines < MAX_LINES) {
            presentIntervalLines++;
            int count = Math.min(REPORT_EVERY_FRAMES, Math.min(presentIntervals, CAPACITY));
            System.out.println("[Celtium] CELTIUM_PRESENT_INTERVAL presents=" + presentIntervals
                    + " window=" + count
                    + " intervalMs[" + triple(presentIntervalRing, count, presentIntervals) + "]"
                    + " fps=" + presentRate(presentIntervalRing, count, presentIntervals));
        }
    }

    /** Loop-span samples: present mark to the next frame boundary. */
    private static final long[] loopOutsideRing = new long[CAPACITY];
    /** Loop-span samples: a frame boundary to the present that follows it. */
    private static final long[] loopTailRing = new long[CAPACITY];
    private static int loopOutsideSamples;
    private static int loopTailSamples;
    /** Loop-span lines printed, against the same {@link #MAX_LINES} budget. */
    private static int loopSpanLines;
    /** Clock of the most recent present mark not yet paired with a boundary, or 0 when none is waiting. */
    private static long loopLastPresentNanos;
    /** Clock of the most recent frame boundary not yet paired with a present, or 0 when none is waiting. */
    private static long loopBoundaryNanos;

    /** {@return whether the loop span is being measured}, for the same opt-in assertion the cadence carries. */
    public static boolean loopSpanEnabled() {
        return LOOP_SPAN;
    }

    /**
     * Pairs a frame boundary with the present that preceded it: the interval between them is one outside
     * sample. A present with no boundary after it (a menu frame) is not lost - it stays the anchor until a
     * boundary arrives, because the time it spans is outside the renderer's frame either way. Only the most
     * recent present anchors: earlier unpaired presents are inside the same outside interval.
     */
    private static void loopBoundary(long now) {
        if (loopLastPresentNanos != 0L) {
            long outside = outsideSpan(now, loopLastPresentNanos);
            if (outside >= 0L) {
                loopOutsideRing[loopOutsideSamples % CAPACITY] = outside;
                loopOutsideSamples++;
            }
            loopLastPresentNanos = 0L;
        }
        loopBoundaryNanos = now;
        if (loopOutsideSamples > 0 && loopOutsideSamples % REPORT_EVERY_FRAMES == 0
                && loopSpanLines < MAX_LINES) {
            loopSpanLines++;
            printLoopSpanWindow();
        }
    }

    /**
     * Pairs a present with the boundary that preceded it (the tail), then becomes the anchor the next
     * boundary pairs against. Only the first present after a boundary is that frame's tail; a later one
     * belongs to a frame the boundary never saw.
     */
    private static void loopPresent(long now) {
        if (loopBoundaryNanos != 0L) {
            long tail = tailSpan(now, loopBoundaryNanos);
            if (tail >= 0L) {
                loopTailRing[loopTailSamples % CAPACITY] = tail;
                loopTailSamples++;
            }
            loopBoundaryNanos = 0L;
        }
        loopLastPresentNanos = now;
    }

    /** {@return the outside span, or -1 when the pair cannot be measured} (no present, or a clock step back). */
    static long outsideSpan(long boundaryNanos, long presentNanos) {
        if (presentNanos == 0L || boundaryNanos < presentNanos) {
            return -1L;
        }
        return boundaryNanos - presentNanos;
    }

    /** {@return the tail span, or -1 when the pair cannot be measured} (no boundary, or a clock step back). */
    static long tailSpan(long presentNanos, long boundaryNanos) {
        if (boundaryNanos == 0L || presentNanos < boundaryNanos) {
            return -1L;
        }
        return presentNanos - boundaryNanos;
    }

    /** Prints the loop span's window line, in the same triple form as the frame budget's. */
    private static void printLoopSpanWindow() {
        int outsideCount = Math.min(loopOutsideSamples, CAPACITY);
        int tailCount = Math.min(loopTailSamples, CAPACITY);
        System.out.println("[Celtium] CELTIUM_LOOP_SPAN boundaries=" + loopOutsideSamples
                + " ms[outside=" + triple(loopOutsideRing, outsideCount, loopOutsideSamples)
                + " tail=" + (tailCount == 0 ? "0.0/0.0/0.0"
                        : triple(loopTailRing, tailCount, loopTailSamples)) + "]");
    }

    /** {@return} frames per second implied by a window's median interval as {@code p50}}. */
    private static String presentRate(long[] ring, int count, int cursor) {
        long[] copy = window(ring, count, cursor);
        long median = percentile(copy, 0.50D);
        if (median <= 0L) {
            return "0.0";
        }
        return String.format(Locale.ROOT, "%.1f", 1_000_000_000.0D / (double) median);
    }

    /** Files one frame's sample, prints the window's line when due, and resets the accumulators. */
    private static void file(long frameNanos) {
        long[] sample = split(frameNanos, currentRecord, currentBatch, currentSubmit, currentFence, currentSlot,
                currentPresent, currentGpu, currentTerrainGpu);
        int slot = written % CAPACITY;
        frameRing[slot] = frameNanos;
        recordRing[slot] = currentRecord;
        batchRing[slot] = currentBatch;
        submitRing[slot] = currentSubmit;
        fenceRing[slot] = currentFence;
        slotRing[slot] = currentSlot;
        presentRing[slot] = currentPresent;
        gpuRing[slot] = currentGpu;
        terrainGpuRing[slot] = currentTerrainGpu;
        otherRing[slot] = sample[9];
        written++;
        currentRecord = 0L;
        currentBatch = 0L;
        currentSubmit = 0L;
        currentFence = 0L;
        currentSlot = 0L;
        currentPresent = 0L;
        currentGpu = 0L;
        currentTerrainGpu = 0L;
        if (written % REPORT_EVERY_FRAMES == 0) {
            printWindow();
        }
    }

    /** {@return whether the present cadence is being measured}, for the call site's own conditional work. */
    public static boolean presentIntervalEnabled() {
        return PRESENT_INTERVAL;
    }

    /**
     * {@return the decomposition of one {@code other} mean}, in this order: outside, terrain, setup, entities,
     * particles, sky, cloudsWeather, guiHand, worldRest, passResidual, unexplained.
     * <p>
     * Pure arithmetic over the two instruments' window means, kept static and separate from the printing for
     * the same reason {@link #split} is: the unit test asserts the production arithmetic, not a copy of it.
     * The bracket indices arrive as parameters, resolved by the printing path from the shared name table, so
     * the arithmetic itself never loads {@link FramePassTimings}: that class latches its harness switches at
     * initialisation, and a caller that loaded it early (a test class, say) would pin them at the wrong values
     * for whoever came later.
     * {@code bracketMeans} is {@link FramePassTimings#windowMeansNanos()} (index 0 is the pass frame itself, so
     * a bracket's mean sits at its name-table index + 1). The buckets are disjoint by construction: {@code
     * terrain} contains its own sub-brackets (record, submit, prepare), {@code setupTerrain} contains the
     * {@code st*} family, {@code entities} the {@code ent*} family, and {@code worldRest} is what {@code
     * worldPass} holds beyond those four and {@code sky} - the snapshot's remainder after the boundary, the
     * render glue, and the inside brackets no bucket names ({@code dyn*}, {@code anim*}, {@code chunkUpload},
     * the window machinery when it runs inside the world pass). {@code worldRest} is floored at zero: nested
     * brackets can overlap at the edges, and a negative remainder would be an artefact of the overlap, not a
     * measurement. {@code unexplained} is not floored - a small negative there is the {@code batch} double
     * count (see {@link #OTHER_SPLIT}) showing itself, and printing it is how the reader knows the size of
     * that bias.
     */
    static long[] otherSplit(long otherMean, long outsideMean, long[] bracketMeans, long passResidualMean,
            int[][] bucketIndex, int worldPassIndex, int[] worldPassMembers) {
        long[] buckets = new long[bucketIndex.length];
        for (int b = 0; b < bucketIndex.length; b++) {
            long sum = 0L;
            for (int member : bucketIndex[b]) {
                if (member >= 0 && member + 1 < bracketMeans.length) {
                    sum += bracketMeans[member + 1];
                }
            }
            buckets[b] = sum;
        }
        long worldRest = 0L;
        if (worldPassIndex >= 0 && worldPassIndex + 1 < bracketMeans.length) {
            long rest = bracketMeans[worldPassIndex + 1];
            for (int member : worldPassMembers) {
                if (member >= 0 && member + 1 < bracketMeans.length) {
                    rest -= bracketMeans[member + 1];
                }
            }
            worldRest = Math.max(0L, rest);
        }
        long explained = outsideMean + passResidualMean + worldRest;
        for (long bucket : buckets) {
            explained += bucket;
        }
        return new long[] {outsideMean, buckets[0], buckets[1], buckets[2], buckets[3], buckets[4], buckets[5],
                buckets[6], worldRest, passResidualMean, otherMean - explained};
    }

    /** {@return the mean of a ring's window in nanoseconds}, read through the ring's own write cursor. */
    private static long ringMean(long[] ring, int cursor) {
        int count = Math.min(cursor, CAPACITY);
        if (count <= 0) {
            return 0L;
        }
        long sum = 0L;
        for (int i = 0; i < count; i++) {
            sum += ring[(cursor - count + i + CAPACITY) % CAPACITY];
        }
        return sum / count;
    }

    /**
     * Prints the other-split line for the current windows of both instruments, or the reason it cannot.
     * <p>
     * Called from the window print and the summary. The unavailable form matters: a leg that asked for the
     * split but forgot {@code passSplit} must read as "not measured", never as a row of zeros - the same
     * positive-control rule the pass instrument's own summary follows.
     */
    private static void printOtherSplit() {
        if (!OTHER_SPLIT || !ENABLED) {
            return;
        }
        if (FramePassTimings.windowFrameCount() == 0) {
            System.out.println("[Celtium] CELTIUM_OTHER_SPLIT unavailable (pass timings measured no frames;"
                    + " otherSplit needs passSplit or brackets on the same leg)");
            return;
        }
        long[] means = FramePassTimings.windowMeansNanos();
        long outsideMean = LOOP_SPAN ? ringMean(loopOutsideRing, loopOutsideSamples) : -1L;
        long otherMean = ringMean(otherRing, written);
        long[] split = otherSplit(otherMean, Math.max(0L, outsideMean), means,
                FramePassTimings.windowResidualMeanNanos(), otherBucketIndex(), worldPassIndex(),
                worldPassMemberIndex());
        int lightmap = FramePassTimings.indexOfName("lightmap");
        int mouseOver = FramePassTimings.indexOfName("mouseOver");
        int winRecord = FramePassTimings.indexOfName("winRecord");
        System.out.println("[Celtium] CELTIUM_OTHER_SPLIT frames=" + written
                + " passFrames=" + FramePassTimings.windowFrameCount()
                + " meanMs[other=" + ms(otherMean)
                + " outside=" + (outsideMean < 0L ? "-1.0" : ms(split[0]))
                + " terrain=" + ms(split[1])
                + " setup=" + ms(split[2])
                + " entities=" + ms(split[3])
                + " particles=" + ms(split[4])
                + " sky=" + ms(split[5])
                + " cloudsWeather=" + ms(split[6])
                + " guiHand=" + ms(split[7])
                + " worldRest=" + ms(split[8])
                + " passResidual=" + ms(split[9])
                + " unexplained=" + ms(split[10]) + "]"
                + " memoMs[batch=" + ms(ringMean(batchRing, written))
                + " record=" + ms(ringMean(recordRing, written))
                + " lightmap=" + ms(lightmap >= 0 ? means[lightmap + 1] : 0L)
                + " mouseOver=" + ms(mouseOver >= 0 ? means[mouseOver + 1] : 0L)
                + " winRecord=" + ms(winRecord >= 0 ? means[winRecord + 1] : 0L) + "]");
    }

    /** Prints the last window's percentiles, or nothing once the line budget is spent. */
    private static void printWindow() {
        if (lines >= MAX_LINES) {
            return;
        }
        int count = Math.min(REPORT_EVERY_FRAMES, Math.min(written, CAPACITY));
        if (count <= 0) {
            return;
        }
        lines++;
        System.out.println("[Celtium] CELTIUM_FRAME_BUDGET frames=" + written
                + " window=" + count
                + " ms[frame=" + triple(frameRing, count)
                + " record=" + triple(recordRing, count)
                + " batch=" + triple(batchRing, count)
                + " submit=" + triple(submitRing, count)
                + " fence=" + triple(fenceRing, count)
                + " slot=" + triple(slotRing, count)
                + " present=" + triple(presentRing, count)
                + " gpu=" + triple(gpuRing, count)
                + " terrainGpu=" + triple(terrainGpuRing, count)
                + " other=" + triple(otherRing, count) + "]"
                + " gpuShare=" + share(gpuRing, frameRing, count)
                + " worstFrame=" + worstFrameText()
                + terrainTimingCensusText()
                + (lines >= MAX_LINES ? " (line budget reached; the teardown summary still prints)" : ""));
        printOtherSplit();
    }

    /**
     * Prints the session's split once, at teardown.
     * <p>
     * The periodic lines show the shape while the run happens; this is the number a report cites, and it is printed
     * whether or not the line budget was reached, so a long session's diagnostic is never empty.
     */
    public static void printSummary() {
        if (!PRESENT_INTERVAL) {
            return;
        }
        // The cadence is independent of the split and prints first, because the two can be asked for separately: a
        // renderer-comparison leg wants the frame period without the split's tax, and a split leg still wants both.
        if (presentIntervals > 0) {
            int presentCount = Math.min(presentIntervals, CAPACITY);
            // The present ring has its own write cursor. Reading it through the frame ring's cursor (the static
            // `written`) is what printed intervalMs[0.0/0.0/0.0] for a run with 211 presents: the slots it read were
            // never written by markPresent.
            System.out.println("[Celtium] CELTIUM_PRESENT_INTERVAL_SUMMARY presents=" + presentIntervals
                    + " window=" + presentCount
                    + " intervalMs[" + triple(presentIntervalRing, presentCount, presentIntervals) + "]"
                    + " fps=" + presentRate(presentIntervalRing, presentCount, presentIntervals));
        }
        if (LOOP_SPAN && loopOutsideSamples > 0) {
            int outsideCount = Math.min(loopOutsideSamples, CAPACITY);
            int tailCount = Math.min(loopTailSamples, CAPACITY);
            System.out.println("[Celtium] CELTIUM_LOOP_SPAN_SUMMARY boundaries=" + loopOutsideSamples
                    + " ms[outside=" + triple(loopOutsideRing, outsideCount, loopOutsideSamples)
                    + " tail=" + (tailCount == 0 ? "0.0/0.0/0.0"
                            : triple(loopTailRing, tailCount, loopTailSamples)) + "]");
        }
        if (!ENABLED) {
            return;
        }
        if (written == 0) {
            System.out.println("[Celtium] CELTIUM_FRAME_BUDGET_SUMMARY frames=0 (no frame boundary was measured)");
            return;
        }
        int count = Math.min(written, CAPACITY);
        System.out.println("[Celtium] CELTIUM_FRAME_BUDGET_SUMMARY frames=" + written
                + " window=" + count
                + " ms[frame=" + triple(frameRing, count)
                + " record=" + triple(recordRing, count)
                + " batch=" + triple(batchRing, count)
                + " submit=" + triple(submitRing, count)
                + " fence=" + triple(fenceRing, count)
                + " slot=" + triple(slotRing, count)
                + " present=" + triple(presentRing, count)
                + " gpu=" + triple(gpuRing, count)
                + " terrainGpu=" + triple(terrainGpuRing, count)
                + " other=" + triple(otherRing, count) + "]"
                + " gpuShare=" + share(gpuRing, frameRing, count)
                + " worstFrame=" + worstFrameText()
                + terrainTimingCensusText());
        printOtherSplit();
    }

    /**
     * {@return the split of the longest frame in the ring as {@code frame=.. record=..}, or "none"}.
     * <p>
     * The percentiles answer "what does a typical frame look like"; a stutter is one frame, and a p95 cannot say
     * which of its parts grew. This names that frame's parts directly.
     */
    private static String worstFrameText() {
        int count = Math.min(written, CAPACITY);
        int slot = -1;
        long worst = 0L;
        for (int i = 0; i < count; i++) {
            int index = (written - count + i + CAPACITY) % CAPACITY;
            if (frameRing[index] > worst) {
                worst = frameRing[index];
                slot = index;
            }
        }
        if (slot < 0) {
            return "none";
        }
        return "[frame=" + ms(frameRing[slot])
                + " record=" + ms(recordRing[slot])
                + " batch=" + ms(batchRing[slot])
                + " submit=" + ms(submitRing[slot])
                + " fence=" + ms(fenceRing[slot])
                + " slot=" + ms(slotRing[slot])
                + " present=" + ms(presentRing[slot])
                + " gpu=" + ms(gpuRing[slot])
                + " terrainGpu=" + ms(terrainGpuRing[slot])
                + " other=" + ms(otherRing[slot]) + "]";
    }

    /** {@return one ring's p50/p95/worst in milliseconds as {@code p50/p95/worst}}. */
    private static String triple(long[] ring, int count) {
        return triple(ring, count, written);
    }

    /**
     * {@return one ring's p50/p95/worst, read through the ring's own write cursor}.
     * <p>
     * The frame rings advance with {@code written}; the present-interval ring advances with {@code presentIntervals}.
     * A ring read through the wrong cursor returns the slots the other ring wrote, which is how a run with 211
     * presents printed {@code intervalMs[0.0/0.0/0.0]}.
     */
    static String triple(long[] ring, int count, int cursor) {
        long[] copy = window(ring, count, cursor);
        return ms(percentile(copy, 0.50D)) + "/" + ms(percentile(copy, 0.95D)) + "/" + ms(copy[copy.length - 1]);
    }

    /**
     * {@return the GPU's share of the measured frame time in the window, or 0 when no timestamp was read}.
     * <p>
     * A ratio of sums rather than a mean of per-frame ratios: a single 16 ms frame with a 400 ms GPU sample must
     * weigh like the 400 ms it was, not like one frame. It answers the question the Windows report could not - "is
     * the GPU the limiter?" - only for the timestamped window passes; the class comment says what it leaves out.
     */
    private static String share(long[] numeratorRing, long[] denominatorRing, int count) {
        long numerator = 0L;
        long denominator = 0L;
        for (int i = 0; i < count; i++) {
            int index = (written - count + i + CAPACITY) % CAPACITY;
            numerator += numeratorRing[index];
            denominator += denominatorRing[index];
        }
        if (denominator <= 0L) {
            return "0";
        }
        return String.format(Locale.ROOT, "%.2f", numerator / (double) denominator);
    }

    /** Copies the last {@code count} samples of a ring into a sorted array. */
    private static long[] window(long[] ring, int count) {
        return window(ring, count, written);
    }

    /**
     * Copies the last {@code count} samples of a ring whose write cursor is {@code cursor} into a sorted array.
     * <p>
     * Package-private so the cursor rule is testable without a GPU: the present-interval ring is the one ring that
     * does not advance with {@code written}, and reading it through the frame cursor is a silent zero, not an error.
     */
    static long[] window(long[] ring, int count, int cursor) {
        long[] copy = new long[count];
        int capacity = ring.length;
        for (int i = 0; i < count; i++) {
            copy[i] = ring[(cursor - count + i + capacity) % capacity];
        }
        Arrays.sort(copy);
        return copy;
    }

    /** {@return the {@code q} quantile of a sorted sample}, with the same nearest-rank rule the reports use. */
    private static long percentile(long[] sorted, double q) {
        int index = (int) Math.round(q * (sorted.length - 1));
        return sorted[Math.max(0, Math.min(sorted.length - 1, index))];
    }

    private static String ms(long nanos) {
        return String.format(Locale.ROOT, "%.1f", nanos / 1_000_000.0D);
    }

    /**
     * {@return the frame's parts with {@code other} computed}, in this order: frame, record, batch, submit, fence,
     * slot, present, gpu, terrainGpu, other.
     * <p>
     * The subtraction lives here and {@link #file} consumes it, so the unit test asserts the production arithmetic
     * rather than a copy of it: {@code other} is a floor (a nesting span can only shrink it) and it must never be
     * negative, which a frame boundary that fires twice in one frame would otherwise produce. Both GPU figures
     * pass through untouched: they are durations on the GPU's timeline, overlapping the CPU spans, and
     * subtracting either would charge the same time twice.
     */
    static long[] split(long frameNanos, long recordNanos, long batchNanos, long submitNanos, long fenceNanos,
            long slotNanos, long presentNanos, long gpuNanos, long terrainGpuNanos) {
        long accounted = recordNanos + batchNanos + submitNanos + fenceNanos + slotNanos + presentNanos;
        long other = frameNanos - accounted;
        if (other < 0L) other = 0L;
        return new long[] {frameNanos, recordNanos, batchNanos, submitNanos, fenceNanos, slotNanos, presentNanos,
                gpuNanos, terrainGpuNanos, other};
    }
}
