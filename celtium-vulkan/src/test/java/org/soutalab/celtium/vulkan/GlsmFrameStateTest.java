package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;
import org.soutalab.celtium.vulkan.GlsmFrameState.ClearRoute;
import org.soutalab.celtium.vulkan.GlsmFrameState.ClearState;
import org.soutalab.celtium.vulkan.GlsmFrameState.PendingClear;
import org.soutalab.celtium.vulkan.RenderTargetRegistry.ImageSlot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the S2-2 ClearState contract (glsm-stage2-design.md §3.4, §5): the per-identity
 * pending table, the per-buffer fold, the carried lifecycle and its rescue, the colour
 * ownership rule, and the accounting identity every transition must preserve.
 */
class GlsmFrameStateTest {

    private static ClearState newState() {
        return new GlsmFrameState().clearState();
    }

    @Test
    void latchPeekConsumeRoundTripsTheIssuedValues() {
        ClearState state = newState();
        state.latch(42L, 0x4100, 0.25F, 0.5F, 0.75F, 1.0F, 0.5D, 7);

        PendingClear peeked = state.peek(42L);
        assertNotNull(peeked);
        assertEquals(0x4100, peeked.mask);
        assertEquals(0.25F, peeked.red);
        assertEquals(0.75F, peeked.blue);
        assertEquals(0.5D, peeked.depth);
        assertEquals(7, peeked.issueDrawFbo);
        // Peeking consumes nothing.
        assertTrue(state.hasPending(42L));
        assertEquals(0L, state.appliedTotal());
        assertTrue(state.identityHolds());

        PendingClear consumed = state.consume(42L);
        assertNotNull(consumed);
        assertEquals(0x4100, consumed.mask);
        assertFalse(state.hasPending(42L));
        assertEquals(1L, state.appliedTotal());
        assertEquals(1L, state.routeCount(ClearRoute.LATCH));
        assertTrue(state.identityHolds());
        assertNull(state.consume(42L));
    }

    @Test
    void foldKeepsTheColourAcrossADepthOnlyClear() {
        ClearState state = newState();
        state.latch(9L, 0x4000, 0.1F, 0.2F, 0.3F, 0.4F, 1.0D, 3);
        // A later depth-only clear carries live colour values that must not replace the
        // colour the first clear was issued with (the per-buffer snapshot rule).
        state.latch(9L, 0x0100, 0.9F, 0.9F, 0.9F, 0.9F, 0.25D, 3);

        PendingClear folded = state.peek(9L);
        assertNotNull(folded);
        assertEquals(0x4100, folded.mask);
        assertEquals(0.1F, folded.red);
        assertEquals(0.4F, folded.alpha);
        assertEquals(0.25D, folded.depth);
        assertEquals(2, folded.issues);
        assertEquals(2L, state.pendingIssues());

        state.consume(9L);
        // Folding preserves the identity: both issues moved to applied together.
        assertEquals(2L, state.appliedTotal());
        assertTrue(state.identityHolds());
    }

    @Test
    void pendingTableIsPerIdentitySoLatchesCannotOverwrite() {
        ClearState state = newState();
        state.latch(100L, 0x4000, 1.0F, 0.0F, 0.0F, 1.0F, 1.0D, 1);
        state.latch(200L, 0x4000, 0.0F, 1.0F, 0.0F, 1.0F, 1.0D, 2);

        // The registry's single latch record lost the first of these; the table keeps both.
        assertEquals(1.0F, state.peek(100L).red);
        assertEquals(1.0F, state.peek(200L).green);
        state.consume(100L);
        assertTrue(state.hasPending(200L));
        assertTrue(state.identityHolds());
    }

    @Test
    void carriedLifecycleAppliesAndRescueReturnsToPending() {
        ClearState state = newState();
        float[] rgba = {0.5F, 0.5F, 0.5F, 1.0F};
        state.reserveCarried(0x4100, rgba, 1.0D, 5, 77L);
        assertEquals(0x4100, state.carriedMask());
        state.noteQueuedCarried();
        assertEquals(1L, state.carriedOutstanding());
        assertTrue(state.identityHolds());

        state.noteCarriedApplied();
        assertEquals(0L, state.carriedOutstanding());
        assertEquals(1L, state.appliedTotal());
        assertEquals(1L, state.routeCount(ClearRoute.CARRIED));
        assertTrue(state.identityHolds());

        // The discard path: a second carried clear is rescued into the pending table under
        // the main identity instead of being re-pended through the stub.
        state.reserveCarried(0x4000, rgba, 1.0D, 5, 78L);
        state.noteQueuedCarried();
        state.rescueCarriedToPending(1234L, 0x4000, rgba, 1.0D);
        assertEquals(0L, state.carriedOutstanding());
        assertTrue(state.hasPending(1234L));
        assertEquals(1L, state.routeCount(ClearRoute.RESCUED));
        assertTrue(state.identityHolds());

        PendingClear rescued = state.consume(1234L);
        assertNotNull(rescued);
        assertEquals(0x4000, rescued.mask);
        assertEquals(2L, state.appliedTotal());
        assertTrue(state.identityHolds());
    }

    @Test
    void colourOwnershipRefusesAnotherSlotAndBeginFrameResets() {
        ClearState state = newState();
        assertTrue(state.noteClear(1L, ImageSlot.CHAIN_PAIR, true, false));
        assertEquals(ImageSlot.CHAIN_PAIR, state.colourClearOwner());
        // Another slot's colour clear in the same frame is refused and counted.
        assertFalse(state.noteClear(2L, ImageSlot.MAIN, true, false));
        assertEquals(1, state.clearCollisions());
        // A depth-only clear never collides.
        assertTrue(state.noteClear(2L, ImageSlot.MAIN, false, false));
        // The same slot may clear again.
        assertTrue(state.noteClear(1L, ImageSlot.CHAIN_PAIR, true, false));

        state.beginFrame(2L);
        assertNull(state.colourClearOwner());
        assertTrue(state.noteClear(2L, ImageSlot.MAIN, true, false));
    }

    @Test
    void latchedNoteClearConsumesThePendingRecord() {
        ClearState state = newState();
        state.latch(55L, 0x4100, 0.0F, 0.0F, 0.0F, 0.0F, 1.0D, 4);
        assertTrue(state.noteClear(55L, ImageSlot.CHAIN_PAIR, true, true));
        assertFalse(state.hasPending(55L));
        assertEquals(1L, state.appliedTotal());
        assertTrue(state.identityHolds());
    }

    @Test
    void snapshotRestorePairUndoesAConsumption() {
        ClearState state = newState();
        state.latch(66L, 0x4100, 0.3F, 0.3F, 0.3F, 1.0F, 1.0D, 6);
        PendingClear snapshot = state.peek(66L);
        long appliedAtSnapshot = state.appliedTotal();

        state.consume(66L);
        assertFalse(state.hasPending(66L));
        assertEquals(1L, state.appliedTotal());

        state.restore(66L, snapshot);
        state.restoreAppliedTotal(appliedAtSnapshot);
        assertTrue(state.hasPending(66L));
        assertEquals(0L, state.appliedTotal());
        assertTrue(state.identityHolds());
    }

    @Test
    void dropPendingCountsTheDrop() {
        ClearState state = newState();
        state.latch(77L, 0x4000, 0.0F, 0.0F, 0.0F, 1.0F, 1.0D, 8);
        state.dropPending(77L);
        assertFalse(state.hasPending(77L));
        assertEquals(1L, state.droppedTotal());
        assertTrue(state.identityHolds());
    }

    @Test
    void absorbSummariesNameRoutesAndAttribution() {
        ClearState state = newState();
        state.noteRoute(ClearRoute.QUEUED);
        state.noteRoute(ClearRoute.QUEUED);
        state.noteRoute(ClearRoute.RESCUED);
        String routes = state.routeSummary();
        assertTrue(routes.contains("QUEUED=2"), routes);
        assertTrue(routes.contains("RESCUED=1"), routes);
        assertTrue(routes.contains("STANDALONE=0"), routes);
        state.latch(78L, 0x4000, 0.0F, 0.0F, 0.0F, 1.0F, 1.0D, 8);
        assertTrue(state.noteClear(78L, ImageSlot.DEFAULT_PAIR, true, true));
        String attribution = state.clearAttribution();
        assertTrue(attribution.contains("latched=1"), attribution);
        assertTrue(attribution.contains("live=0"), attribution);
    }
}
