package org.soutalab.celtium.vulkan;

/**
 * E1(b) + E3 (items 679 / 721): frame-image layout ownership, aggregated per segment.
 * <p>
 * The frame image's contract with the rest of the world is one sentence: outside a render pass it is found in
 * {@code VK_IMAGE_LAYOUT_GENERAL}. The per-pass / per-window acquire-release pairs that used to maintain that
 * sentence paid two {@code vkCmdPipelineBarrier} calls at every boundary even when the only thing on either
 * side of the boundary was another pass attaching the same image - the measured cost was prepAcquire plus
 * winBarrier at about 5.5% of frame p50 on the flight scene (e1e3-census). This class is the decision half of
 * the aggregation: a <em>segment</em> is a run of consecutive passes / windows with no outside consumer in
 * between, and inside a segment the acquire is recorded once at its head and the release once at its close.
 * Only the barrier <em>count</em> changes; the stage and access masks of the barriers that are recorded are
 * {@code GllessFrameTarget.layoutBarrierAccessMasks}' unchanged output.
 * <p>
 * The ownership state is not stored here and is not stored anywhere new: it is derived from the frame target's
 * existing tracked colour layout (the image is owned exactly when that layout is the attachment layout). That
 * derivation is what keeps BH-26 intact - a discarded recording rolls the tracked layout back through the
 * existing {@code RecordingSnapshot}, and the derived ownership rolls back with it, with no second truth to
 * reconcile.
 * <p>
 * Why dropping the interior barriers is sound is the step-0 inventory recorded with this change: every render
 * pass that attaches the frame image (the terrain frame pass, the post-chain pass, the native pass executor's
 * passes) declares {@code initialLayout == finalLayout} and no explicit subpass dependencies, so consecutive
 * passes are ordered for attachment accesses by the implicit external subpass dependencies - the begin
 * dependency's destination access mask covers colour / depth attachment reads and writes, and the end
 * dependency's source mask publishes them. The explicit acquire / release barriers carry the wider
 * {@code ALL_COMMANDS} scope that <em>non-attachment</em> accesses need (shader sampling of the frame image,
 * transfers, copies, present, GL), and those consumers are exactly the segment boundaries: each one either
 * calls {@code requireGeneral} first or closes the segment at a frame / submission boundary.
 * <p>
 * Both switches default off. With {@link #ENABLED} off every call site passes through to the exact recording
 * the code made before this class existed, in the same order and count.
 */
public final class LayoutOwnership {

    /** Master switch: {@code -Dceltium.vulkan.layoutOwnership=true}. Default off. */
    public static final boolean ENABLED =
            Boolean.parseBoolean(System.getProperty("celtium.vulkan.layoutOwnership", "false"));

    /**
     * Audit mode: {@code -Dceltium.vulkan.layoutOwnershipAudit=true}. Default off, and inert unless
     * {@link #ENABLED} is also on. Counts every decision and lets the frame boundary print a bounded
     * {@code CELTIUM_LAYOUT_OWNER} line, so a validation leg can show the aggregation really engaged and a
     * segment never stayed open across a boundary.
     */
    public static final boolean AUDIT = ENABLED
            && Boolean.parseBoolean(System.getProperty("celtium.vulkan.layoutOwnershipAudit", "false"));

    // Audit counters. Written only on the render thread, only consulted when AUDIT is on; plain longs are
    // enough for a diagnostic that never gates behaviour.
    private static long acquireRecorded;
    private static long acquireSkippedOwned;
    private static long releaseRecorded;
    private static long releaseHeldAtPassEnd;
    private static long segmentCloses;

    private LayoutOwnership() {
    }

    /** {@return whether the tracked colour layout says a segment currently owns the frame image}. */
    public static boolean owned(int trackedColorLayout, int attachmentLayout) {
        return trackedColorLayout == attachmentLayout;
    }

    /**
     * {@return whether an acquire barrier has to be recorded at a pass / window head}.
     * <p>
     * Not when the segment already owns the image - unless a clear is pending (or the image was never
     * cleared): the acquire is also the vehicle that applies game clears in the attachment layout, so a
     * clear-carrying head keeps the full acquire, whose barrier then names the attachment layout on both
     * sides and changes nothing but the dependency.
     */
    public static boolean acquireNeeded(boolean owned, boolean clearPending) {
        return !owned || clearPending;
    }

    /** {@return whether a release barrier has to be recorded to hand the image back to GENERAL}. */
    public static boolean releaseNeeded(boolean owned) {
        return owned;
    }

    /**
     * {@return whether a pass / window end holds the segment open instead of releasing}. Only ever true with
     * the master switch on; off, every end releases exactly as before.
     */
    public static boolean holdAtPassEnd() {
        return ENABLED;
    }

    public static void noteAcquireRecorded() {
        if (AUDIT) {
            acquireRecorded++;
        }
    }

    public static void noteAcquireSkippedOwned() {
        if (AUDIT) {
            acquireSkippedOwned++;
        }
    }

    public static void noteReleaseRecorded() {
        if (AUDIT) {
            releaseRecorded++;
        }
    }

    public static void noteReleaseHeldAtPassEnd() {
        if (AUDIT) {
            releaseHeldAtPassEnd++;
        }
    }

    public static void noteSegmentClose() {
        if (AUDIT) {
            segmentCloses++;
        }
    }

    /** The audit counters as {@code [acquireRecorded, acquireSkippedOwned, releaseRecorded, held, closes]}. */
    public static long[] auditCounts() {
        return new long[] {acquireRecorded, acquireSkippedOwned, releaseRecorded, releaseHeldAtPassEnd,
                segmentCloses};
    }
}
