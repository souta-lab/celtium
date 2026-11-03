package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Which images a pair clear still has to move into the transfer layout, checked without a device.
 * <p>
 * A pair clear records one {@code vkCmdPipelineBarrier} for whichever of the pair's two images is not already where
 * the clear wants it. The pair clear runs once per pair per frame - 12 a frame on the measured world scene, where
 * GregTech's bloom chain binds a mip pyramid of framebuffers and each level's pair is cleared - and the window split
 * put it at 19 us a window, the largest named per-window item. The two transitions used to be two separate
 * {@code vkCmdPipelineBarrier} calls; they are one now, and this pins the decision that says whether there is a
 * barrier at all.
 * <p>
 * What these tests prove: the decision table. They prove nothing about a frame - a green test here is not evidence
 * that a clear was recorded, or that the barrier it recorded was legal.
 */
class PairTransferBarrierTest {

    @Test
    void bothImagesMoveOnTheOrdinaryClear() {
        // The ordinary case: the pair was left in the attachment layout by the previous frame's pass, so both images
        // need the move and both go into the one barrier.
        int images = GllessFrameTarget.celtium$transferImages(true, true, true, false, false);
        assertEquals(GllessFrameTarget.TRANSFER_COLOR | GllessFrameTarget.TRANSFER_DEPTH, images,
                "a clear of both images from the attachment layout moves both");
    }

    @Test
    void anImageAlreadyInTheTransferLayoutIsNotMovedAgain() {
        assertEquals(GllessFrameTarget.TRANSFER_DEPTH,
                GllessFrameTarget.celtium$transferImages(true, true, true, true, false),
                "the colour is already in the transfer layout, so only the depth moves");
        assertEquals(GllessFrameTarget.TRANSFER_COLOR,
                GllessFrameTarget.celtium$transferImages(true, true, true, false, true),
                "the depth is already in the transfer layout, so only the colour moves");
        assertEquals(0, GllessFrameTarget.celtium$transferImages(true, true, true, true, true),
                "both already there: no barrier at all");
    }

    @Test
    void aColourClearInGeneralNeedsNoMove() {
        // -Dceltium.vulkan.colorClearTransfer=false clears the colour in GENERAL, so the colour transition is not
        // recorded and the clear names GENERAL as its layout.
        assertEquals(GllessFrameTarget.TRANSFER_DEPTH,
                GllessFrameTarget.celtium$transferImages(true, true, false, false, false),
                "with the transfer clear off, only the depth moves");
        assertEquals(0, GllessFrameTarget.celtium$transferImages(true, false, false, false, false),
                "a colour-only clear with the transfer clear off records no barrier");
    }

    @Test
    void anImageTheClearDoesNotNameIsNotMoved() {
        assertEquals(GllessFrameTarget.TRANSFER_COLOR,
                GllessFrameTarget.celtium$transferImages(true, false, true, false, false),
                "a colour-only clear does not move the depth");
        assertEquals(GllessFrameTarget.TRANSFER_DEPTH,
                GllessFrameTarget.celtium$transferImages(false, true, true, false, false),
                "a depth-only clear does not move the colour");
        assertEquals(0, GllessFrameTarget.celtium$transferImages(false, false, true, false, false),
                "a clear that names neither image records no barrier");
    }
}
