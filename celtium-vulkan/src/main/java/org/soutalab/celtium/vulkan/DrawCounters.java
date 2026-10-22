package org.soutalab.celtium.vulkan;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Counts the draws actually recorded, which is the number the batching work is judged by.
 *
 * <p>Batch counts and group counts were both being read as "draws", and neither is: a group is what the renderer keeps,
 * a draw is what the command buffer receives. Reducing 1:1 translation means fewer commands, so the command is what is
 * counted here - one increment where {@code vkCmdDrawIndexed} or {@code vkCmdDrawIndirect} is actually issued, and one
 * line per 200 frames so a leg reports it without any new switch.
 */
public final class DrawCounters {

    private static final AtomicLong INDEXED = new AtomicLong();
    private static final AtomicLong INDIRECT = new AtomicLong();
    private static final AtomicLong NON_INDEXED = new AtomicLong();
    private static final AtomicLong UPLOADS = new AtomicLong();
    private static final AtomicLong UPLOAD_BYTES = new AtomicLong();
    private static long lastReportedUploads;
    private static long lastReportedUploadBytes;

    private static long lastReportedIndexed;
    private static long lastReportedFrame = -1L;

    /** Per-frame work counters for the KPI: what the flush records and how often state is rebound. */
    private static final AtomicLong IMMEDIATE_GROUPS = new AtomicLong();
    private static final AtomicLong PIPELINE_BINDS = new AtomicLong();
    private static final AtomicLong DESCRIPTOR_BINDS = new AtomicLong();

    /** One group of the immediate window's draw list was recorded. */
    public static void noteImmediateGroup() {
        IMMEDIATE_GROUPS.incrementAndGet();
    }

    /** One graphics pipeline was bound while recording. */
    public static void notePipelineBind() {
        PIPELINE_BINDS.incrementAndGet();
    }

    /** One descriptor set was bound while recording. */
    public static void noteDescriptorBind() {
        DESCRIPTOR_BINDS.incrementAndGet();
    }

    public static long immediateGroups() {
        return IMMEDIATE_GROUPS.get();
    }

    public static long pipelineBinds() {
        return PIPELINE_BINDS.get();
    }

    public static long descriptorBinds() {
        return DESCRIPTOR_BINDS.get();
    }

    private DrawCounters() {
    }

    /** One {@code vkCmdDrawIndexed} was recorded. */
    public static void noteIndexed() {
        INDEXED.incrementAndGet();
    }

    /** One {@code vkCmdDrawIndirect} / {@code vkCmdDrawIndexedIndirect} was recorded. */
    public static void noteIndirect() {
        INDIRECT.incrementAndGet();
    }

    /** One terrain mesh upload (vertex or index range written into its arena). */
    public static void noteUpload(long bytes) {
        UPLOADS.incrementAndGet();
        UPLOAD_BYTES.addAndGet(bytes);
    }

    /** One non-indexed {@code vkCmdDraw} was recorded. */
    public static void noteNonIndexed() {
        NON_INDEXED.incrementAndGet();
    }

    /**
     * Prints the draw rate when due.
     *
     * @param frameId the frame the count belongs to
     */
    public static void reportIfDue(long frameId) {
        if (frameId <= 0L || frameId % 200L != 0L || frameId == lastReportedFrame) {
            return;
        }
        long indexed = INDEXED.get();
        long frames = lastReportedFrame <= 0L ? frameId : frameId - lastReportedFrame;
        long delta = indexed - lastReportedIndexed;
        lastReportedIndexed = indexed;
        lastReportedFrame = frameId;
        long uploads = UPLOADS.get();
        long uploadBytes = UPLOAD_BYTES.get();
        long uploadDelta = uploads - lastReportedUploads;
        long bytesDelta = uploadBytes - lastReportedUploadBytes;
        lastReportedUploads = uploads;
        lastReportedUploadBytes = uploadBytes;
        System.out.println("[Celtium] CELTIUM_DRAWS frame=" + frameId
                + " indexed=" + indexed + " indirect=" + INDIRECT.get() + " nonIndexed=" + NON_INDEXED.get()
                + " perFrame=" + (frames > 0 ? (delta / frames) : 0)
                + " uploadsPerFrame=" + (frames > 0 ? (uploadDelta / frames) : 0)
                + " uploadKBPerFrame=" + (frames > 0 ? (bytesDelta / frames / 1024L) : 0));
    }
}
