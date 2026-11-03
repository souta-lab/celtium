package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The one layout question a pair clear recorded inside the pass still has to answer.
 * <p>
 * {@code -Dceltium.vulkan.pairClearInPass=true} records the pair's once-per-frame clear as one
 * {@code vkCmdClearAttachments} inside the pass that follows it, instead of two {@code vkCmdClear*Image} calls and the
 * two barriers that make them legal outside a pass. Inside the pass the attachment is already in the layout the pass
 * declares, so the only move that can still be needed is the depth's: a pair that was just created is left in
 * {@code TRANSFER_DST_OPTIMAL} by its acquire, and the pass declares the attachment layout as its initialLayout.
 * <p>
 * What these tests prove: the decision table. They prove nothing about a frame - a green test here is not evidence
 * that a clear was recorded inside a pass, or that the pass it was recorded into attached the pair.
 */
class PairClearInPassTest {

    @Test
    void aDepthAlreadyInTheAttachmentLayoutNeedsNoBarrier() {
        // The steady state: the pass's own finalLayout leaves the depth in the attachment layout, so the next frame's
        // deferred clear records no barrier at all.
        assertFalse(GllessFrameTarget.celtium$deferredClearNeedsDepthBarrier(GllessFrameTarget.DEPTH_RENDER_LAYOUT),
                "the depth is where the pass declares it, so nothing moves");
    }

    @Test
    void aFreshlyAcquiredDepthIsMovedBeforeThePass() {
        // A pair created this frame: recordAcquire leaves the depth in the transfer layout the ordinary clear uses.
        assertTrue(GllessFrameTarget.celtium$deferredClearNeedsDepthBarrier(
                        org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL),
                "a depth in the transfer layout has to move before vkCmdBeginRenderPass");
        assertTrue(GllessFrameTarget.celtium$deferredClearNeedsDepthBarrier(
                        org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_UNDEFINED),
                "an undefined depth has to move before vkCmdBeginRenderPass");
    }

    @Test
    void theGeneralLayoutIsNotTheAttachmentLayout() {
        // GENERAL is legal for both a clear and an attachment, but the pass declares the attachment layout as its
        // initialLayout, so a depth left in GENERAL is still a move - and this is the case that would be missed by a
        // test that only asked "is the layout legal for a clear".
        assertTrue(GllessFrameTarget.celtium$deferredClearNeedsDepthBarrier(
                        org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_GENERAL),
                "GENERAL is not the layout the pass declares");
    }

    @Test
    void theClearIsInsideThePassUnlessThePropertySaysOtherwise() {
        // Round 54 moved the default: the paired measurement put winHead at 0.1 ms a frame with the clear outside the
        // pass and 0.0 with it inside. The property is an opt-out, so an absent value is the new default and only an
        // explicit false restores the two vkCmdClear*Image calls and their barriers.
        assertTrue(GllessFrameTarget.celtium$pairClearInPassDefault(null),
                "absent means on: the default is the clear inside the pass");
        assertTrue(GllessFrameTarget.celtium$pairClearInPassDefault("true"));
        assertTrue(GllessFrameTarget.celtium$pairClearInPassDefault("TRUE"),
                "Boolean.parseBoolean ignores case");
        assertFalse(GllessFrameTarget.celtium$pairClearInPassDefault("false"),
                "the opt-out has to reach the old path, or the A/B cannot be run");
        assertFalse(GllessFrameTarget.celtium$pairClearInPassDefault("0"),
                "an explicit value is read as a boolean, so anything but true is off");
    }

    @Test
    void theClearRectNeverExceedsTheAttachment() {
        // vkCmdClearAttachments requires every rect inside the render area. The caller passes the pass's render area;
        // the attachment is the pair's. They agree in the ordinary case, and the clamp is what keeps a pass whose area
        // came from the frame from asking for a rect larger than the image.
        assertEquals(256, GllessFrameTarget.celtium$deferredClearExtent(256, 256),
                "the ordinary case is unchanged");
        assertEquals(256, GllessFrameTarget.celtium$deferredClearExtent(1280, 256),
                "a frame-sized pass over a 256x256 pair clears the pair, not the frame");
        assertEquals(1280, GllessFrameTarget.celtium$deferredClearExtent(1280, 0),
                "an unknown pair extent leaves the caller's value alone rather than clearing nothing");
        assertEquals(0, GllessFrameTarget.celtium$deferredClearExtent(0, 256),
                "an empty pass area clears nothing");
    }
}
