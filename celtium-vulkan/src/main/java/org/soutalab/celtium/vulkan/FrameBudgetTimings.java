package org.soutalab.celtium.vulkan;

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
 * <li>{@code other} - {@code frame - (record + batch + submit + fence + slot + present)}, clamped at zero.</li>
 * </ul>
 * <p>
 * What this does <b>not</b> prove: {@code batch} can contain a flush, which is also counted in {@code submit}, so
 * {@code other} is a floor on the unattributed time and not an exact remainder - the double count can only make
 * {@code other} smaller. {@code gpu} covers the timestamped window passes only, not terrain recording or any copy
 * submitted outside them, so a frame can be GPU-bound with a small {@code gpu} number. None of these numbers is a
 * performance claim about a release: they say where a measured run's time went, on that run's driver and scene.
 * <p>
 * Off unless {@code -Dceltium.vulkan.frameBudget=true}, the same rule the other repeating instruments follow: the
 * acceptance fixtures pay for the instruments that judge them, and a normal run must not. Asking for it also opens
 * the window timestamp path (see {@code timeImmediatePass}), because the {@code gpu} column is what separates "the
 * GPU is the limiter" from "the GPU is idle and the CPU is the limiter" - and a report that cannot say which is the
 * report that could not name the Windows frame rate. That path costs two timestamp writes per window and one
 * readback per frame, which is the instrument's own price and why it stays off by default. The two neighbours are
 * {@link FramePassTimings} (which attributes the frame to the game's own passes, and needs its brackets on) and
 * {@code CELTIUM_BOUNDARY_SPLIT} (which reports submit/slot means every 600 frames with no per-frame record). This
 * one is per frame and opt-in; it costs seven clock reads a frame and nothing else when it is off.
 */
public final class FrameBudgetTimings {

    /** {@code -Dceltium.vulkan.frameBudget=true}: measure and print the per-frame split. */
    private static final boolean ENABLED = Boolean.getBoolean("celtium.vulkan.frameBudget");

    /**
     * {@code -Dceltium.vulkan.frameBudgetFrames=60}: frames per printed line.
     * <p>
     * A line per frame is unreadable and a line per second is what a tester can correlate with a visible stutter;
     * 60 frames is that second on a healthy session and six seconds on the reported 10 FPS, which is still short
     * enough to bracket a spike.
     */
    private static final int REPORT_EVERY_FRAMES =
            Math.max(1, Integer.getInteger("celtium.vulkan.frameBudgetFrames", 60).intValue());

    /**
     * {@code -Dceltium.vulkan.frameBudgetLines=120}: how many periodic lines a run may print.
     * <p>
     * Bounded on purpose: a two-hour session must not turn a diagnostic into the log's own performance problem,
     * and the teardown summary carries the session's totals after the last line. The cap is a line count, not a
     * byte count, so its cost is predictable.
     */
    private static final int MAX_LINES =
            Math.max(1, Integer.getInteger("celtium.vulkan.frameBudgetLines", 120).intValue());

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
        if (!ENABLED) {
            return;
        }
        long now = System.nanoTime();
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
     * Marks one presented frame: the cadence where no frame boundary runs.
     * <p>
     * The frame boundary only exists in the world path. The menu presents frames without ever reaching it, so a menu
     * at 6 FPS carried no measurement at all - a report whose frame count was zero while the operator watched a
     * slideshow. The interval between presents is the frame period wherever a frame was presented, and it costs one
     * clock read. It is a separate ring from the world frames so the two are never added: a world frame files both a
     * boundary sample and a present interval, and only the boundary sample has the record/batch/submit split.
     */
    public static void markPresent() {
        if (!ENABLED) {
            return;
        }
        long now = System.nanoTime();
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
                    + " intervalMs[" + triple(presentIntervalRing, count) + "]"
                    + " fps=" + presentRate(presentIntervalRing, count));
        }
    }

    /** {@return} frames per second implied by a window's median interval as {@code p50}}. */
    private static String presentRate(long[] ring, int count) {
        long[] copy = window(ring, count);
        long median = percentile(copy, 0.50D);
        if (median <= 0L) {
            return "0.0";
        }
        return String.format(Locale.ROOT, "%.1f", 1_000_000_000.0D / (double) median);
    }

    /** Files one frame's sample, prints the window's line when due, and resets the accumulators. */
    private static void file(long frameNanos) {
        long[] sample = split(frameNanos, currentRecord, currentBatch, currentSubmit, currentFence, currentSlot,
                currentPresent, currentGpu);
        int slot = written % CAPACITY;
        frameRing[slot] = frameNanos;
        recordRing[slot] = currentRecord;
        batchRing[slot] = currentBatch;
        submitRing[slot] = currentSubmit;
        fenceRing[slot] = currentFence;
        slotRing[slot] = currentSlot;
        presentRing[slot] = currentPresent;
        gpuRing[slot] = currentGpu;
        otherRing[slot] = sample[8];
        written++;
        currentRecord = 0L;
        currentBatch = 0L;
        currentSubmit = 0L;
        currentFence = 0L;
        currentSlot = 0L;
        currentPresent = 0L;
        currentGpu = 0L;
        if (written % REPORT_EVERY_FRAMES == 0) {
            printWindow();
        }
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
                + " other=" + triple(otherRing, count) + "]"
                + " gpuShare=" + share(gpuRing, frameRing, count)
                + " worstFrame=" + worstFrameText()
                + (lines >= MAX_LINES ? " (line budget reached; the teardown summary still prints)" : ""));
    }

    /**
     * Prints the session's split once, at teardown.
     * <p>
     * The periodic lines show the shape while the run happens; this is the number a report cites, and it is printed
     * whether or not the line budget was reached, so a long session's diagnostic is never empty.
     */
    public static void printSummary() {
        if (!ENABLED) {
            return;
        }
        if (written == 0) {
            System.out.println("[Celtium] CELTIUM_FRAME_BUDGET_SUMMARY frames=0 (no frame boundary was measured)");
            return;
        }
        if (presentIntervals > 0) {
            int presentCount = Math.min(presentIntervals, CAPACITY);
            System.out.println("[Celtium] CELTIUM_PRESENT_INTERVAL_SUMMARY presents=" + presentIntervals
                    + " window=" + presentCount
                    + " intervalMs[" + triple(presentIntervalRing, presentCount) + "]"
                    + " fps=" + presentRate(presentIntervalRing, presentCount));
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
                + " other=" + triple(otherRing, count) + "]"
                + " gpuShare=" + share(gpuRing, frameRing, count)
                + " worstFrame=" + worstFrameText());
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
                + " other=" + ms(otherRing[slot]) + "]";
    }

    /** {@return one ring's p50/p95/worst in milliseconds as {@code p50/p95/worst}}. */
    private static String triple(long[] ring, int count) {
        long[] copy = window(ring, count);
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
        long[] copy = new long[count];
        for (int i = 0; i < count; i++) {
            copy[i] = ring[(written - count + i + CAPACITY) % CAPACITY];
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
     * slot, present, gpu, other.
     * <p>
     * The subtraction lives here and {@link #file} consumes it, so the unit test asserts the production arithmetic
     * rather than a copy of it: {@code other} is a floor (a nesting span can only shrink it) and it must never be
     * negative, which a frame boundary that fires twice in one frame would otherwise produce.
     */
    static long[] split(long frameNanos, long recordNanos, long batchNanos, long submitNanos, long fenceNanos,
            long slotNanos, long presentNanos, long gpuNanos) {
        long accounted = recordNanos + batchNanos + submitNanos + fenceNanos + slotNanos + presentNanos;
        long other = frameNanos - accounted;
        if (other < 0L) other = 0L;
        return new long[] {frameNanos, recordNanos, batchNanos, submitNanos, fenceNanos, slotNanos, presentNanos,
                gpuNanos, other};
    }
}
