package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The decision table behind the clear-order flush skip, checked one reason at a time.
 * <p>
 * {@code VulkanTerrainGpuBackend.flushBeforeClear} used to open a window for every {@code glClear} the game issued: the
 * ordering rule (audit S10) says the draws already accepted have to reach the image before the clear erases them, and a
 * window was the only way the backend knew to do that. The rule is about <em>one image</em>, but the queue holds groups
 * for every target the frame has offered, so the flush drew all of them - a clear on a mod's framebuffer submitted the
 * window's own composite groups, which that clear cannot touch.
 * <p>
 * Measured on leg {@code r61a-defer} (AMD RX 6400, 1680 world frames): 27.8 clears a frame, 83% of them on the main
 * framebuffer, and every foreign clear logged {@code queued=1 lastQueuedTarget=0x0} - the queue held a group for the
 * window identity while the clear named a mod's framebuffer. The marker route this replaced (attach the clear to a
 * queued group of its own target) never fired once, because a mod clears its framebuffer right after binding it.
 * <p>
 * The rule is pinned here as a pure function: one assertion per reason the flush is still needed, plus the skip case. A
 * future reason that is added to the implementation but forgotten in the table would skip the flush for a clear that
 * does have draws to erase - a lost draw, not a slow frame.
 */
class ClearDeferralContractTest {

    /** No reason applies: the clear's own image has nothing queued, so the flush is skipped. */
    @Test
    void skipsWhenTheClearsImageHasNoPendingDraws() {
        assertFalse(VulkanTerrainGpuBackend.celtium$clearNeedsOrderingFlushDecision(
                true, false, true, false));
    }

    /** The GL bridge has no pair queue, so the flush is the only ordering there is. */
    @Test
    void flushesWithoutAPairQueue() {
        assertTrue(VulkanTerrainGpuBackend.celtium$clearNeedsOrderingFlushDecision(
                false, false, true, false));
    }

    /** The flush also records the coalesced texture copies, which the queue does not order against a clear. */
    @Test
    void flushesWithRawWritesPending() {
        assertTrue(VulkanTerrainGpuBackend.celtium$clearNeedsOrderingFlushDecision(
                true, true, true, false));
    }

    /** An unresolved framebuffer cannot be shown to have nothing queued. */
    @Test
    void flushesWhenTheIdentityIsUnresolved() {
        assertTrue(VulkanTerrainGpuBackend.celtium$clearNeedsOrderingFlushDecision(
                true, false, false, false));
    }

    /** The clear's own image has queued draws, which the clear must erase: the flush is the ordering. */
    @Test
    void flushesWhenTheClearsImageHasPendingDraws() {
        assertTrue(VulkanTerrainGpuBackend.celtium$clearNeedsOrderingFlushDecision(
                true, false, true, true));
    }

    /** Two reasons at once still flush: the table is a disjunction of reasons, not a vote. */
    @Test
    void flushesWhenTwoReasonsApply() {
        assertTrue(VulkanTerrainGpuBackend.celtium$clearNeedsOrderingFlushDecision(
                true, true, true, true));
    }

    /** The mask half of the rule: only colour and depth are attachments this renderer owns. */
    @Test
    void ownsColourAndDepthButNotStencil() {
        assertTrue(VulkanTerrainGpuBackend.celtium$clearNamesOwnedAttachment(0x4000), "colour");
        assertTrue(VulkanTerrainGpuBackend.celtium$clearNamesOwnedAttachment(0x0100), "depth");
        assertTrue(VulkanTerrainGpuBackend.celtium$clearNamesOwnedAttachment(0x4100), "colour+depth");
        assertFalse(VulkanTerrainGpuBackend.celtium$clearNamesOwnedAttachment(0x0400), "stencil only");
        assertFalse(VulkanTerrainGpuBackend.celtium$clearNamesOwnedAttachment(0x0000), "nothing");
    }

    /**
     * The carried-clear match: the pending mask is consumed only when it names exactly the clear the queue carried.
     * <p>
     * The two decisions have to agree, because a "carry" skips the ordering flush and the clear's own route is then the
     * only thing that orders it. A reservation that no clear consumed must not answer for a later one - the values it
     * holds are the earlier clear's - and a pending mask that carries a bit the reservation does not name belongs to a
     * clear this route did not carry, so consuming it would drop that clear.
     */
    @Test
    void consumesOnlyTheClearTheQueueCarried() {
        assertTrue(VulkanTerrainGpuBackend.celtium$carriedClearMatches(0x4100, 0x4100), "exact");
        assertTrue(VulkanTerrainGpuBackend.celtium$carriedClearMatches(0x4100, 0x4000), "colour half of a carried pair");
        assertTrue(VulkanTerrainGpuBackend.celtium$carriedClearMatches(0x4100, 0x0100), "depth half of a carried pair");
        assertFalse(VulkanTerrainGpuBackend.celtium$carriedClearMatches(0x4000, 0x4100), "a depth bit nobody carried");
        assertFalse(VulkanTerrainGpuBackend.celtium$carriedClearMatches(0x0100, 0x4000), "a colour bit nobody carried");
        assertFalse(VulkanTerrainGpuBackend.celtium$carriedClearMatches(0, 0x4100), "no reservation");
        assertFalse(VulkanTerrainGpuBackend.celtium$carriedClearMatches(0, 0), "no reservation, no clear");
    }
}
