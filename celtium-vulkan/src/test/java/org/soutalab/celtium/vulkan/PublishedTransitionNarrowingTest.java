package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which published names the attach-side hand-back still has to visit.
 * <p>
 * The walk over the published frame textures runs once per window - 33 a frame on the measured world scene - and on the
 * attach side it exists for exactly one reason: the pass declares {@code GENERAL} for attachment 0, so every published
 * name whose image that pass attaches has to be moved back before {@code vkCmdBeginRenderPass}
 * ({@code VUID-vkCmdBeginRenderPass-initialLayout-00900}). A name published over a different image is not attached by
 * that pass and is not moved by it.
 * <p>
 * What these tests prove: the decision table. They prove nothing about a frame - a green test here is not evidence that
 * a transition was recorded, or that the image the pass attached is the one the caller named.
 */
class PublishedTransitionNarrowingTest {

    private static final long PAIR = 0x1000L;
    private static final long OTHER = 0x2000L;

    @Test
    void theSamplingSideVisitsEveryName() {
        // The sampling side is the direction that has to cover every name a draw in the pass might sample, and that
        // set is not known at the walk: narrowing it would be a missing transition, not a saved one.
        assertTrue(PostChainBridge.celtium$attachHandBackNeeded(false, PAIR, PAIR),
                "a name over the attached image is visited on the sampling side");
        assertTrue(PostChainBridge.celtium$attachHandBackNeeded(false, PAIR, OTHER),
                "a name over another image is visited too: the pass may sample it");
        assertTrue(PostChainBridge.celtium$attachHandBackNeeded(false, 0L, OTHER),
                "the sampling side does not narrow even when no image was named");
    }

    @Test
    void theAttachSideVisitsOnlyTheAttachedImage() {
        assertTrue(PostChainBridge.celtium$attachHandBackNeeded(true, PAIR, PAIR),
                "the name that stands for the attached image is the one the hand-back exists for");
        assertFalse(PostChainBridge.celtium$attachHandBackNeeded(true, PAIR, OTHER),
                "a name over another image is neither attached nor moved by this pass");
    }

    @Test
    void anUnnamedAttachImageKeepsTheOldBehaviour() {
        // The post chain's own attach sites do not name an image, and they must keep the unconditional walk: a
        // narrowing that guessed there would be a transition dropped on a path this change did not measure.
        assertTrue(PostChainBridge.celtium$attachHandBackNeeded(true, 0L, PAIR),
                "no image named means no narrowing");
        assertTrue(PostChainBridge.celtium$attachHandBackNeeded(true, 0L, OTHER),
                "no image named means no narrowing, whatever the name stands for");
        assertTrue(PostChainBridge.celtium$attachHandBackNeeded(true, 0L, 0L),
                "a name with no image is still visited when the caller did not narrow");
    }

    @Test
    void aNameWithNoImageIsSkippedWhenTheAttachImageIsKnown() {
        // A published name whose image is 0 cannot be the image the pass attaches, so the hand-back has nothing to do
        // for it. This is the case that made the old walk pay a full lookup per name per window for nothing.
        assertFalse(PostChainBridge.celtium$attachHandBackNeeded(true, PAIR, 0L),
                "a name with no image is not the attached image");
    }
}
