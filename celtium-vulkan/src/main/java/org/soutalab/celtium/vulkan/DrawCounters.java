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
}
