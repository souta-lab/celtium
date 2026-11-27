package org.soutalab.celtium.vulkan;

/**
 * The common counters the RenderBackend adoption gate (REV2 §4.2 item 3, gate G2) reads in one
 * shape: how many calls enter through the stub's dispatch and how much CPU time they take, how
 * many explicit barriers the frame records and how many times the render target switches. The
 * current form is wired here; a form with a single backend seam (a {@code RenderBackend}
 * implementation) records into the same line shape from its own boundary, so a breakdown run
 * prints the same line for either form and the numbers sit side by side without translation.
 *
 * <p>The counting contract, which an Arm B wiring must match event for event:
 * <ul>
 *   <li>{@code stubDispatchCalls} / {@code stubDispatchNanos}: one per call entering through the
 *   GLSM stub's uniform dispatch ({@code GlsmFrontend.dispatch} / {@code legacyDispatch}), timed
 *   entry to return - the calls that arrive as native function pointers (FFM upcalls and libffi
 *   trampolines). Measured limit, stated plainly so nobody reads this key as the GL call volume:
 *   on the BSL pack scene (2026-11-27, agera) it counted 8 calls in the first 200 frames and none
 *   after, because that scene's mod GL arrives through the stub's Java method surface, which has
 *   no single entry point to count at. The Java-surface volume on the current form is carried by
 *   the existing GLSM census counters ({@code windowRecords}, the flush site counts, the draw
 *   counters). Where a real single seam exists - a {@code RenderBackend} interface's method
 *   invocations - the same shape of key at that seam is the exact call count, and that is where a
 *   future arm records it; this class's dispatch keys stay what they measure.</li>
 *   <li>{@code explicitBarriers}: one per {@code vkCmdPipelineBarrier} recorded into a frame
 *   command buffer, counted at the call site. Every call site in this module is wired (the G0 sweep
 *   is grep-verifiable: one {@code noteBarrier} per {@code vkCmdPipelineBarrier} invocation outside
 *   imports and comments), so the key covers the whole module on Arm A; Arm B counts at its own
 *   call sites the same way.</li>
 *   <li>{@code fboSwitches}: one per {@code vkCmdBeginRenderPass} - the point where the bound
 *   framebuffer actually changes - wired at every render-pass begin in the module the same way.</li>
 * </ul>
 *
 * <p>The counters are a measurement instrument, live exactly when the measurement family is: the
 * pass-timing report or the frame budget is enabled, or {@code -Dceltium.backendCounters=true} asks
 * for them alone. While disabled every hook is one static-final branch the JIT folds away, so an
 * instrument-OFF run - the runs the G2 verdict is taken from - pays nothing. Plain longs, as in
 * {@link DrawCounters}: the stub's dispatch gate serialises callers and the recording path is the
 * render thread, so the writers are effectively single-threaded.
 */
public final class BackendCallCounters {

    private static final boolean ENABLED = Boolean.getBoolean("celtium.backendCounters")
            || GpuPassTimingReport.enabled() || FrameBudgetTimings.enabled();

    private static long calls;
    private static long callNanos;
    private static long barriers;
    private static long fboSwitches;

    private static long lastReportedCalls;
    private static long lastReportedCallNanos;
    private static long lastReportedBarriers;
    private static long lastReportedFboSwitches;
    private static long lastReportedFrame = -1L;

    private BackendCallCounters() {
    }

    /** Whether the measurement family is collecting these counters on this run. */
    public static boolean enabled() {
        return ENABLED;
    }

    /**
     * Opens one timed backend call; pair the return value with {@link #noteCallEnd(long)}. Returns
     * {@code 0} while disabled, which the end hook reads as "not collecting" and skips.
     */
    public static long noteCallBegin() {
        return ENABLED ? System.nanoTime() : 0L;
    }

    /** Closes a call opened by {@link #noteCallBegin()} and files its count and elapsed time. */
    public static void noteCallEnd(long beginNanos) {
        if (beginNanos == 0L) {
            return;
        }
        calls++;
        callNanos += System.nanoTime() - beginNanos;
    }

    /** One explicit {@code vkCmdPipelineBarrier} was recorded. */
    public static void noteBarrier() {
        if (ENABLED) {
            barriers++;
        }
    }

    /** One render pass was begun, switching the bound framebuffer. */
    public static void noteFboSwitch() {
        if (ENABLED) {
            fboSwitches++;
        }
    }

    /** The four keys as one space-separated fragment, for a reader that prints its own line. */
    public static String format() {
        return "stubDispatchCalls=" + calls + " stubDispatchNanos=" + callNanos
                + " explicitBarriers=" + barriers + " fboSwitches=" + fboSwitches;
    }

    /**
     * Prints the window's counts every 200 frames, in the same one-line shape as the other periodic
     * instruments: totals recorded since the previous report and the frames they span, so a reader
     * divides by {@code frames} for the per-frame shape the G2 breakdown compares.
     */
    public static void reportIfDue(long frameId) {
        if (!ENABLED || frameId <= 0L || frameId % 200L != 0L || frameId == lastReportedFrame) {
            return;
        }
        long frames = lastReportedFrame < 0L ? frameId : frameId - lastReportedFrame;
        System.out.println("[Celtium] CELTIUM_BACKEND_CALLS frame=" + frameId + " frames=" + frames
                + " stubDispatchCalls=" + (calls - lastReportedCalls)
                + " stubDispatchNanos=" + (callNanos - lastReportedCallNanos)
                + " explicitBarriers=" + (barriers - lastReportedBarriers)
                + " fboSwitches=" + (fboSwitches - lastReportedFboSwitches));
        lastReportedCalls = calls;
        lastReportedCallNanos = callNanos;
        lastReportedBarriers = barriers;
        lastReportedFboSwitches = fboSwitches;
        lastReportedFrame = frameId;
    }
}
