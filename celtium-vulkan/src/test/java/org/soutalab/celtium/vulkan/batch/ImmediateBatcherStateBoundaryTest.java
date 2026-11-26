package org.soutalab.celtium.vulkan.batch;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The S5-2 state boundary (slicing design section 3-6): the state key a capture carries -
 * the scissor rectangle and alpha/depth state hash the caller captured at the item's start -
 * is a fusion boundary. A fused entry draws under one state, so two captures that differ
 * only in state key must stay two entries even when pipeline, instance record, texture and
 * index contiguity would all allow one draw; captures under one state fuse as before.
 */
class ImmediateBatcherStateBoundaryTest {

    private static final int BYTES_PER_VERTEX = 16;

    private static ByteBuffer vertices(int quads) {
        return ByteBuffer.allocate(quads * 4 * BYTES_PER_VERTEX);
    }

    /** Two captures identical in everything but the state key, sharing one instance record. */
    private static BatchPlan twoCaptures(int firstState, int secondState, boolean ordered) {
        ImmediateBatcher batcher = new ImmediateBatcher();
        batcher.beginFrame(new DirectStagingRing(1, 1 << 16));
        assertTrue(batcher.captureQuads(1, 10, firstState, 0, 0, 0, ordered,
                vertices(1), 1, BYTES_PER_VERTEX));
        assertTrue(batcher.captureQuads(1, 10, secondState, 0, 0, 0, ordered,
                vertices(1), 1, BYTES_PER_VERTEX));
        return batcher.endFrame();
    }

    @Test
    void stateChangeSplitsFusion() {
        BatchPlan plan = twoCaptures(3, 4, false);
        assertEquals(2, plan.entryCount());
        assertEquals(6, plan.indexCount(0));
        assertEquals(6, plan.indexCount(1));
    }

    @Test
    void sameStateStillFuses() {
        BatchPlan plan = twoCaptures(3, 3, false);
        assertEquals(1, plan.entryCount());
        assertEquals(12, plan.indexCount(0));
    }

    /**
     * The GUI shape the boundary exists for: one panel whose clip rectangle changes halfway
     * through its ordered draws. The two halves keep capture order as separate entries, so
     * the executor can re-issue the scissor between them instead of clipping the whole
     * panel by whichever rectangle a fused draw would have carried.
     */
    @Test
    void orderedStateChangeSplitsFusionInCaptureOrder() {
        BatchPlan plan = twoCaptures(7, 9, true);
        assertEquals(2, plan.entryCount());
        assertEquals(0, plan.firstIndex(0));
        assertEquals(6, plan.firstIndex(1));
    }
}
