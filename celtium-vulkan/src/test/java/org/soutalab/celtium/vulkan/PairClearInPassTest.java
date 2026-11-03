package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

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
}
