package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The decision table for {@code -Dceltium.vulkan.stickyWindowPass=true}: which windows may record into the render pass
 * an earlier window left open instead of closing it and opening another.
 * <p>
 * The merge is worth ~19 us of fixed cost per window on the shipped profile (23.5 windows a frame, six groups each),
 * and every condition below is a way it would be wrong rather than merely slow - so the table is the part that has to
 * be right before a leg is spent on the measurement.
 * <p>
 * What these tests prove: the table. They prove nothing about a frame - a green test here is not evidence that a merge
 * happened, that the picture is unchanged, or that the release barrier the close records is the one the next acquire
 * expects.
 */
class StickyWindowPassTest {

    /** Every condition satisfied: the merge is allowed. */
    private static boolean allowed(boolean stickyEnabled, boolean passOpen, boolean gllessTarget, boolean offTarget,
            boolean sameTarget, boolean sameRenderPass, boolean acquireNeeded, boolean replacementInProgress) {
        return VulkanTerrainGpuBackend.celtium$stickyMergeAllowed(stickyEnabled, passOpen, gllessTarget, offTarget,
                sameTarget, sameRenderPass, acquireNeeded, replacementInProgress);
    }

    @Test
    void theSteadyStateMerges() {
        assertTrue(allowed(true, true, true, false, true, true, false, false),
                "the switch is on, the pass is open, the window is the same target and pass, and nothing is pending");
    }

    @Test
    void theSwitchOffNeverMerges() {
        assertFalse(allowed(false, true, true, false, true, true, false, false),
                "a run that did not ask for the merge must record exactly the windows it recorded before");
    }

    @Test
    void noOpenPassNeverMerges() {
        assertFalse(allowed(true, false, true, false, true, true, false, false),
                "there is nothing to record into");
    }

    @Test
    void theBridgeNeverMerges() {
        assertFalse(allowed(true, true, false, false, true, true, false, false),
                "the real-GL bridge has no GllessFrameTarget owner state, so the close could not record its release");
    }

    @Test
    void anotherFramebufferNeverMerges() {
        assertFalse(allowed(true, true, true, true, true, true, false, false),
                "an off-target window attaches a pair, not the frame image");
    }

    @Test
    void anotherIdentityNeverMerges() {
        assertFalse(allowed(true, true, true, false, false, true, false, false),
                "the pass draws into one identity's image");
    }

    @Test
    void anotherRenderPassNeverMerges() {
        assertFalse(allowed(true, true, true, false, true, false, false, false),
                "a different render pass means a different framebuffer, so the open pass is not the one this window needs");
    }

    @Test
    void aPendingGameClearNeverMerges() {
        // The acquire is also where a latched glClear is applied. Merging a window that carries one would record the
        // clear after the draws it has to erase - the fixture's clear-order phase is exactly that shape.
        assertFalse(allowed(true, true, true, false, true, true, true, false),
                "a window whose acquire is needed must keep its own pass");
    }

    @Test
    void aFramebufferReplacementNeverMerges() {
        assertFalse(allowed(true, true, true, false, true, true, false, true),
                "the groups were offered for a framebuffer whose size no longer exists");
    }
}
