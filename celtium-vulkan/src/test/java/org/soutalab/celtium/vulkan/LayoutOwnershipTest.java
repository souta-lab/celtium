package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * E1(b) + E3 (items 679 / 721): the segment decisions behind {@link LayoutOwnership} and the barrier counts
 * they produce for the sequences the design enumerates.
 * <p>
 * The model below is the wiring, reduced to its decisions: a pass / window head asks
 * {@link LayoutOwnership#acquireNeeded}, a pass / window end holds while the flag is on, a consumer or a
 * boundary asks {@link LayoutOwnership#releaseNeeded}. The runtime state it runs on is the one the frame
 * target keeps (segment open or not, clear pending or not), so the counts asserted here are the counts the
 * recorded command stream pays: legacy mode records an acquire and a release at every boundary; ownership
 * mode records one pair per segment. The masks of the barriers that are recorded are not modelled here -
 * they are {@code GllessFrameTarget.layoutBarrierAccessMasks}' unchanged output, pinned by
 * {@link #barrierMasksAreUnchanged()}.
 */
class LayoutOwnershipTest {

    /** The wiring reduced to its decisions, over the same state the frame target keeps. */
    private static final class Model {
        private final boolean ownership;
        private boolean segmentOpen;
        private boolean clearPending;
        private int acquires;
        private int releases;

        Model(boolean ownership) {
            this.ownership = ownership;
        }

        /** A pass / window head. An off-target window (attached=false) never touches the frame image. */
        void head(boolean attached) {
            if (!attached) {
                return;
            }
            if (!ownership) {
                acquires++;
                segmentOpen = true;
                clearPending = false;
                return;
            }
            if (LayoutOwnership.acquireNeeded(segmentOpen, clearPending)) {
                acquires++;
                segmentOpen = true;
                clearPending = false;
            }
        }

        /** A pass / window end: legacy releases; ownership holds the segment open. */
        void end(boolean attached) {
            if (!attached) {
                return;
            }
            if (!ownership) {
                releases++;
                segmentOpen = false;
                return;
            }
            // Held: no release recorded, the segment stays open.
        }

        /** An outside consumer (publish walk, copy, present): the image must be GENERAL before it. */
        void consumer() {
            if (LayoutOwnership.releaseNeeded(segmentOpen)) {
                releases++;
                segmentOpen = false;
            }
        }

        /** BH-26: a discarded recording restores the snapshot, segment state included. */
        void rollback(boolean snapshotSegmentOpen, boolean snapshotClearPending) {
            segmentOpen = snapshotSegmentOpen;
            clearPending = snapshotClearPending;
        }
    }

    @Test
    void legacyModeRecordsEveryPair() {
        Model model = new Model(false);
        for (int pass = 0; pass < 3; pass++) {
            model.head(true);
            model.end(true);
        }
        model.consumer();
        assertEquals(3, model.acquires);
        assertEquals(3, model.releases);
    }

    @Test
    void threeTerrainPassesFormOneSegment() {
        // The vanilla flight scene of the census: the publish walk is empty, so solid / cutout / translucent
        // are one segment - one acquire, and the release lands at the frame boundary (the consumer).
        Model model = new Model(true);
        for (int pass = 0; pass < 3; pass++) {
            model.head(true);
            model.end(true);
        }
        assertEquals(1, model.acquires);
        assertEquals(0, model.releases);
        model.consumer();
        assertEquals(1, model.releases);
        assertFalse(model.segmentOpen);
    }

    @Test
    void aConsumerSplitsTheSegment() {
        // Pass -> sampling walk -> pass: the walk must find GENERAL, so the segment closes and reopens.
        Model model = new Model(true);
        model.head(true);
        model.end(true);
        model.consumer();
        model.head(true);
        model.end(true);
        model.consumer();
        assertEquals(2, model.acquires);
        assertEquals(2, model.releases);
    }

    @Test
    void offTargetWindowsNeitherOpenNorSplitASegment() {
        Model model = new Model(true);
        model.head(true);
        model.end(true);
        model.head(false);
        model.end(false);
        model.head(true);
        model.end(true);
        model.consumer();
        assertEquals(1, model.acquires);
        assertEquals(1, model.releases);
    }

    @Test
    void aPendingClearForcesTheAcquireInsideASegment() {
        // The acquire is the vehicle that applies game clears, so a clear-carrying head keeps it even when
        // the segment already owns the image (design: clear-carrying windows stay a segment of their own).
        assertTrue(LayoutOwnership.acquireNeeded(true, true));
        assertFalse(LayoutOwnership.acquireNeeded(true, false));
        assertTrue(LayoutOwnership.acquireNeeded(false, false));
        Model model = new Model(true);
        model.head(true);
        model.end(true);
        model.clearPending = true;
        model.head(true);
        assertEquals(2, model.acquires);
        assertFalse(model.clearPending);
    }

    @Test
    void rollbackRestoresTheSegmentDecision() {
        // A recording that acquired and is then discarded (BH-26) rolls the segment state back with the
        // snapshot, so the re-record emits the acquire again instead of skipping a transition that never
        // reached the queue.
        Model model = new Model(true);
        boolean snapshotOpen = model.segmentOpen;
        boolean snapshotClear = model.clearPending;
        model.head(true);
        assertEquals(1, model.acquires);
        model.rollback(snapshotOpen, snapshotClear);
        assertFalse(model.segmentOpen);
        model.head(true);
        assertEquals(2, model.acquires);
    }

    @Test
    void ownershipIsDerivedFromTheTrackedLayoutWhereALayoutCarriesIt() {
        // Chain pairs track their colour layout through sampling layouts, so there the owned answer is a
        // derivation, not a stored flag.
        assertTrue(LayoutOwnership.owned(7, 7));
        assertFalse(LayoutOwnership.owned(5, 7));
        assertTrue(LayoutOwnership.releaseNeeded(true));
        assertFalse(LayoutOwnership.releaseNeeded(false));
    }

    @Test
    void barrierMasksAreUnchanged() {
        // The aggregation changes counts only: the masks of every recorded barrier stay the legacy function's
        // output, for acquire / release, first / steady, colour / depth. The values are pinned from the
        // Vulkan access bits themselves, not from the function's own arithmetic.
        int colorAttachment = org.lwjgl.vulkan.VK10.VK_ACCESS_COLOR_ATTACHMENT_READ_BIT
                | org.lwjgl.vulkan.VK10.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT;
        int general = colorAttachment | org.lwjgl.vulkan.VK10.VK_ACCESS_SHADER_READ_BIT
                | org.lwjgl.vulkan.VK10.VK_ACCESS_TRANSFER_READ_BIT
                | org.lwjgl.vulkan.VK10.VK_ACCESS_TRANSFER_WRITE_BIT;
        int attachmentSide = colorAttachment | org.lwjgl.vulkan.VK10.VK_ACCESS_TRANSFER_WRITE_BIT;
        // First acquire: the VUID-02819 exception - no source access at all (design invariant 3.7).
        assertArrayEquals(new int[] {0, attachmentSide},
                GllessFrameTarget.layoutBarrierAccessMasks(false, true, false));
        assertArrayEquals(new int[] {general, attachmentSide},
                GllessFrameTarget.layoutBarrierAccessMasks(false, false, false));
        assertArrayEquals(new int[] {attachmentSide, general},
                GllessFrameTarget.layoutBarrierAccessMasks(true, false, false));
        int depthAttachment = org.lwjgl.vulkan.VK10.VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_READ_BIT
                | org.lwjgl.vulkan.VK10.VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT;
        int depthGeneral = depthAttachment | org.lwjgl.vulkan.VK10.VK_ACCESS_SHADER_READ_BIT
                | org.lwjgl.vulkan.VK10.VK_ACCESS_TRANSFER_READ_BIT
                | org.lwjgl.vulkan.VK10.VK_ACCESS_TRANSFER_WRITE_BIT;
        assertArrayEquals(new int[] {depthGeneral,
                        depthAttachment | org.lwjgl.vulkan.VK10.VK_ACCESS_TRANSFER_WRITE_BIT},
                GllessFrameTarget.layoutBarrierAccessMasks(false, false, true));
    }
}
