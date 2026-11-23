package org.soutalab.celtium.vulkan;

/**
 * The batch-call timing instrument: the static counters the caller-side mixin fills around
 * {@code submitImmediateBatch}, and the thread-time helpers the split measurements read.
 * <p>
 * Extracted from {@code VulkanTerrainGpuBackend}'s immediate-mode state zone (item 704, R3) because the unit is
 * severable: the counters are written only by {@code WorldVertexBufferUploaderMixin} / {@code CloudDrawProbeMixin}
 * and read only by {@code BackendDiagnostics}, and the helpers are pure functions of the thread MXBean. The gate
 * {@code BATCH_TIMINGS} stays on the backend - the whole submit path reads it.
 */
public final class BatchCallTimings {

    private BatchCallTimings() {
    }

    /**
     * Wall time of the game's batch call, measured by the caller (the mixin) around {@code submitImmediateBatch}.
     * <p>
     * The per-frame budget after the terrain passes (2.2 ms) and the handoffs (2.3 ms) left ~38.5 ms unaccounted for,
     * and the {@code immediate=false} A/B says it belongs to the immediate path. This is the instrument for the part of
     * that path the game calls.
     */
    public static long batchCallNanos;
    /** Long because one measured session uploaded 1.35e8 batches: an int overflows inside a day of play. */
    public static long batchCallCount;
    /**
     * The single most expensive batch call, and the total of the first 200.
     * <p>
     * The per-frame mean of this counter is 2.5x higher on Intel than on AMD even though the work is the same CPU-side
     * code and the call counts per frame match, which points at the mean being inflated by a few very slow calls - the
     * JIT compiling the path on a GPU that produces half as many frames. The maximum and the opening window separate
     * that from a real per-call difference.
     */
    public static long batchCallWorstNanos;
    /**
     * The batch call measured as CPU time instead of wall time.
     * <p>
     * Every part measured inside the call adds up to ~1.1 ms/frame (AMD) and ~3.2 ms/frame (Intel), while the wall time
     * around the same call is 3.8 / 10.2 ms/frame. If the CPU time matches the parts, the difference is time the thread
     * spent descheduled - contention with the server thread, the upload thread or the driver - and not work this call
     * does itself.
     */
    public static long batchCallCpuNanos;

    /**
     * The thread MXBean, looked up once.
     * <p>
     * OPT-01: this used to be `ManagementFactory.getThreadMXBean()` on every call - twice per immediate batch for the
     * preflight split, plus once per group and once per frame - and the lookup is a synchronised platform-MBean
     * resolution, not a field read. The instrument's own cost is part of what a performance run measures, so the bean
     * and its support flag are resolved once here.
     */
    private static final java.lang.management.ThreadMXBean THREAD_MX_BEAN =
            java.lang.management.ManagementFactory.getThreadMXBean();

    /** Whether the JVM exposes per-thread CPU time here (it decides whether the split above means anything). */
    public static final boolean THREAD_CPU_SUPPORTED = THREAD_MX_BEAN.isCurrentThreadCpuTimeSupported();
    /**
     * How the render thread spent its non-running time, split by kind.
     * <p>
     * The batch call's wall time exceeds its CPU time by 2.0 ms/frame on AMD and 7.8 ms/frame on Intel. If these two
     * are near zero while that gap is large, the waiting is not Java-level contention but a native call blocking - the
     * bridge's synchronisation - which is a different fix.
     */
    static long threadWaitedMillis() {
        if (!THREAD_CPU_SUPPORTED) {
            return -1L;
        }
        java.lang.management.ThreadInfo info = THREAD_MX_BEAN.getThreadInfo(Thread.currentThread().getId());
        return info == null ? -1L : info.getWaitedTime();
    }

    static long threadBlockedMillis() {
        if (!THREAD_CPU_SUPPORTED) {
            return -1L;
        }
        java.lang.management.ThreadInfo info = THREAD_MX_BEAN.getThreadInfo(Thread.currentThread().getId());
        return info == null ? -1L : info.getBlockedTime();
    }


    /**
     * Whether the per-batch CPU split is measured at all.
     * <p>
     * OPT-01: the split costs two {@code getCurrentThreadCpuTime()} calls per immediate batch (preflight, group
     * creation, append and flush each take a start and an end), and it is instrumentation, not rendering. It is off by
     * default so a performance run measures the renderer rather than the meter; a run that wants the split turns it on
     * with {@code -Dceltium.vulkan.cpuSplit=true} and reports it as an instrumented run, which is the distinction
     * OPT-01's acceptance asks for.
     */
    public static final boolean CPU_SPLIT = Boolean.parseBoolean(
            System.getProperty("celtium.vulkan.cpuSplit", "false"));

    /** The thread's CPU time right now, or -1 when unsupported. */
    public static long threadCpuNanos() {
        if (!CPU_SPLIT) {
            return -1L;
        }
        return THREAD_CPU_SUPPORTED ? THREAD_MX_BEAN.getCurrentThreadCpuTime() : -1L;
    }
    public static long batchCallOpeningNanos;
    public static int batchCallOpeningCount;
}
