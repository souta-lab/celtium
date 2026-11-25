package org.soutalab.celtium.vulkan;

/**
 * Counts the draws actually recorded, which is the number the batching work is judged by.
 *
 * <p>Batch counts and group counts were both being read as "draws", and neither is: a group is what the renderer keeps,
 * a draw is what the command buffer receives. Reducing 1:1 translation means fewer commands, so the command is what is
 * counted here - one increment where {@code vkCmdDrawIndexed} or {@code vkCmdDrawIndirect} is actually issued, and one
 * line per 200 frames so a leg reports it without any new switch.
 */
public final class DrawCounters {

    // Plain longs: every writer and every reader of these counters runs on the render thread (note* from the
    // recording path, report from the same thread's frame boundary), so the atomics only added a locked instruction to
    // counters that are incremented once per group, pipeline bind and descriptor bind.
    private static long INDEXED;
    private static long INDIRECT;
    private static long NON_INDEXED;
    private static long UPLOADS;
    private static long UPLOAD_BYTES;
    private static long lastReportedUploads;
    private static long lastReportedUploadBytes;

    private static long lastReportedIndexed;
    private static long lastReportedFrame = -1L;

    /** Per-frame work counters for the KPI: what the flush records and how often state is rebound. */
    private static long IMMEDIATE_GROUPS;
    private static long PIPELINE_BINDS;
    private static long DESCRIPTOR_BINDS;

    /** One group of the immediate window's draw list was recorded. */
    public static void noteImmediateGroup() {
        IMMEDIATE_GROUPS++;
    }

    /** One graphics pipeline was bound while recording. */
    public static void notePipelineBind() {
        PIPELINE_BINDS++;
    }

    /** One descriptor set was bound while recording. */
    public static void noteDescriptorBind() {
        DESCRIPTOR_BINDS++;
    }

    public static long immediateGroups() {
        return IMMEDIATE_GROUPS;
    }

    public static long pipelineBinds() {
        return PIPELINE_BINDS;
    }

    public static long descriptorBinds() {
        return DESCRIPTOR_BINDS;
    }

    private DrawCounters() {
    }

    /** One {@code vkCmdDrawIndexed} was recorded. */
    public static void noteIndexed() {
        INDEXED++;
    }

    /** One {@code vkCmdDrawIndirect} / {@code vkCmdDrawIndexedIndirect} was recorded. */
    public static void noteIndirect() {
        INDIRECT++;
    }

    /** One terrain mesh upload (vertex or index range written into its arena). */
    public static void noteUpload(long bytes) {
        UPLOADS++;
        UPLOAD_BYTES += bytes;
    }

    /** One non-indexed {@code vkCmdDraw} was recorded. */
    public static void noteNonIndexed() {
        NON_INDEXED++;
    }

    /**
     * Prints the draw rate when due.
     *
     * @param frameId the frame the count belongs to
     */
    public static void reportIfDue(long frameId) {
        if (!PeriodicLog.ENABLED || frameId <= 0L || frameId % 200L != 0L || frameId == lastReportedFrame) {
            return;
        }
        long indexed = INDEXED;
        long frames = lastReportedFrame <= 0L ? frameId : frameId - lastReportedFrame;
        long delta = indexed - lastReportedIndexed;
        lastReportedIndexed = indexed;
        lastReportedFrame = frameId;
        long uploads = UPLOADS;
        long uploadBytes = UPLOAD_BYTES;
        long uploadDelta = uploads - lastReportedUploads;
        long bytesDelta = uploadBytes - lastReportedUploadBytes;
        lastReportedUploads = uploads;
        lastReportedUploadBytes = uploadBytes;
        System.out.println("[Celtium] CELTIUM_DRAWS frame=" + frameId
                + " indexed=" + indexed + " indirect=" + INDIRECT + " nonIndexed=" + NON_INDEXED
                + " perFrame=" + (frames > 0 ? (delta / frames) : 0)
                + " uploadsPerFrame=" + (frames > 0 ? (uploadDelta / frames) : 0)
                + " uploadKBPerFrame=" + (frames > 0 ? (bytesDelta / frames / 1024L) : 0));
    }

    // ---- Batch-call timing and diagnostic switches, owned here since Stage 6 ④a slice 4. ----
    // These lived as statics on VulkanTerrainGpuBackend, which nothing about them needs: the batch-call
    // counters are written by the WorldVertexBufferUploaderMixin and read by BackendDiagnostics, the
    // switches gate the repeating censuses, and DrawCounters is the instrument home the reporters already
    // read. The declarations below are the backend's own, moved unchanged; batchCallCount stays behind
    // for its own owner reassignment (design B6).

    /**
     * Whether the per-batch/per-group {@code System.nanoTime()} deltas the immediateStatistics *Nanos counters take
     * are read at all. The investigation added one nanoTime pair per stage of the batch-accept path - matrix copy,
     * state capture, state key, program query, preflight, clone, group create, append - eight-to-ten clock reads on a
     * ~3600-batch frame ≈ 1.3-1.9 ms/frame spent measuring, not drawing. Off by default
     * ({@code -Dceltium.vulkan.batchTimings=true} or a diagnostics run re-enables); with it off the counters read 0,
     * which the reports already treat as a missing measurement.
     */
    public static final boolean BATCH_TIMINGS = Boolean.getBoolean("celtium.vulkan.batchTimings")
            || VulkanDiagnostics.enabled();

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

    /**
     * Wall time of the game's batch call, measured by the caller (the mixin) around {@code submitImmediateBatch}.
     * <p>
     * The per-frame budget after the terrain passes (2.2 ms) and the handoffs (2.3 ms) left ~38.5 ms unaccounted for,
     * and the {@code immediate=false} A/B says it belongs to the immediate path. This is the instrument for the part of
     * that path the game calls.
     */
    public static long batchCallNanos;

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

    public static long batchCallOpeningNanos;
    public static int batchCallOpeningCount;

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

    /** The thread's CPU time right now, or -1 when unsupported. */
    public static long threadCpuNanos() {
        if (!CPU_SPLIT) {
            return -1L;
        }
        return THREAD_CPU_SUPPORTED ? THREAD_MX_BEAN.getCurrentThreadCpuTime() : -1L;
    }

    /**
     * {@code -Dceltium.vulkan.frameStats=true}: report the real interval between completed frames, once every 120
     * frames. Each bracketed section can be short while the delivered rate is low, and that gap is what a user sees as
     * low FPS, so it is measured on the frame boundary instead of inferred from the sections.
     */
    static final boolean FRAME_STATS = Boolean.getBoolean("celtium.vulkan.frameStats");

    /** {@code -Dceltium.debug.groupCensus=true}: the drawn groups' families and depth/blend state, for the ordering scope. */
    static final boolean GROUP_CENSUS = Boolean.getBoolean("celtium.debug.groupCensus");

    /** {@code -Dceltium.debug.bufferCensus=true}: how many distinct buffers a frame's groups draw from. */
    static final boolean BUFFER_CENSUS = Boolean.getBoolean("celtium.debug.bufferCensus");

    /** {@code -Dceltium.debug.drawKeyCensus=true}: the distinct material keys a frame uses and how often they alternate. */
    static final boolean DRAW_KEY_CENSUS = Boolean.getBoolean("celtium.debug.drawKeyCensus");

    /** {@code -Dceltium.debug.retainedCensus=true}: how much of a frame is retained geometry, and how it repeats. */
    static final boolean RETAINED_CENSUS = Boolean.getBoolean("celtium.debug.retainedCensus");

    static final boolean TEXTURE_DIAGNOSTICS =
            Boolean.getBoolean("celtium.vulkan.textureDiagnostics");

    /**
     * The last {@link #DISTRIBUTION_SAMPLES} durations of the two frame costs that decide the interval but are invisible
     * in the render brackets: the swap-point image path (frame image to swapchain blit plus the wait for the frame's
     * completion) and the time from a submission reaching the queue to its fence being confirmed. Totals cannot answer
     * "what does a steady-state frame pay" - the load phase dominates them - so the summary reports p50/p95/worst of
     * both.
     */
    static final int DISTRIBUTION_SAMPLES = 512;

    /**
     * {@code -Dceltium.vulkan.skyFrameProbe=<n>}: for the frames from n on (bounded), read the frame image's sky band at
     * the frame boundary and print it beside the number of sky draws the frame issued.
     * <p>
     * The offer census says the plane is offered every frame, the group census says it is drawn every frame, and the
     * picture says its pixels are in one frame of a pair and not in the next. Those three can only all be true if the
     * loss is downstream of the draw, and the two candidates are the frame image and the present. This probe reads the
     * frame image itself, so the frame whose picture is flat is named: a flat frame image means the draw's pixels never
     * reached the image the present blits, and a gradient frame image beside a flat presented frame means the blit or
     * the present is where they went.
     * <p>
     * The band is the picture's top rows, which are the image's last rows: the frame image is in GL texture order (row 0
     * is the picture's bottom), the same order {@code readImageRectangle} returns.
     */
    static final long SKY_FRAME_PROBE = Long.getLong("celtium.vulkan.skyFrameProbe", -1L).longValue();
    static final int SKY_FRAME_PROBE_LIMIT =
            Integer.getInteger("celtium.vulkan.skyFrameProbeLimit", 24).intValue();
}
