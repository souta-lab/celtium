package org.soutalab.celtium.vulkan;

/**
 * The common counters the RenderBackend adoption gate (REV2 §4.2 item 3, gate G2) reads from both
 * arms in one shape: how many calls cross from the GL-hosting layer into the backend, how much CPU
 * time they take, how many explicit barriers the frame records and how many times the render target
 * switches. Arm A is the current form wired here; Arm B (the {@code CeltiumVulkanRenderBackend}
 * prototype, REV2 Step C-3) records into the same keys from its own boundary, so a G2 breakdown run
 * prints the same line for either arm and the numbers sit side by side without translation.
 *
 * <p>The counting contract, which an Arm B wiring must match event for event:
 * <ul>
 *   <li>{@code backendCalls} / {@code backendCallNanos}: one per call entering the backend's
 *   execution surface from the hosting layer, timed entry to return. On Arm A the entry is the GLSM
 *   stub's uniform dispatch ({@code GlsmFrontend.dispatch} / {@code legacyDispatch}) - every
 *   stub-served GL call lands there exactly once, no-op class included. On Arm B the entry is one
 *   {@code RenderBackend} method invocation. The two timings are not the same quantity - Arm A's
 *   span includes the stub's state work, Arm B's starts below Actinium's GLSM - so the key is a
 *   structural indicator beside the gate's P50/P95/P99 verdict, never a substitute for it.</li>
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
        return "backendCalls=" + calls + " backendCallNanos=" + callNanos
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
                + " backendCalls=" + (calls - lastReportedCalls)
                + " backendCallNanos=" + (callNanos - lastReportedCallNanos)
                + " explicitBarriers=" + (barriers - lastReportedBarriers)
                + " fboSwitches=" + (fboSwitches - lastReportedFboSwitches));
        lastReportedCalls = calls;
        lastReportedCallNanos = callNanos;
        lastReportedBarriers = barriers;
        lastReportedFboSwitches = fboSwitches;
        lastReportedFrame = frameId;
    }
}
