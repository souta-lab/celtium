package org.soutalab.celtium.vulkan.batch;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The per-entry texture forms of the batcher: an item whose texture rides in its record
 * (the bindless array form) fuses across texture changes with other record-riding items,
 * because the array samples per record and the change is not a state change. Any pairing
 * involving a per-texture item stops at a texture change - a fused entry that binds a
 * per-texture set can only sample the one bound texture - and the plan carries each
 * entry's form and texture slot so the executor knows where to rebind. The fallback
 * identity is the point: with no bindless binding the capture resolves no index for any
 * texture, every item arrives unflagged, and the fusion behaviour is exactly the old
 * per-texture one.
 */
class ImmediateBatcherFallbackTest {

    private static final int BYTES_PER_VERTEX = 16;

    private static ByteBuffer vertices(int quads) {
        return ByteBuffer.allocate(quads * 4 * BYTES_PER_VERTEX);
    }

    /** Two captures that fuse whenever the texture rule lets them: same key and state, one shared instance record, contiguous quads. */
    private static BatchPlan twoCaptures(boolean firstInRecord, boolean secondInRecord,
                                         int firstTexture, int secondTexture) {
        return twoCaptures(firstInRecord, secondInRecord, firstTexture, secondTexture, 0, 0);
    }

    private static BatchPlan twoCaptures(boolean firstInRecord, boolean secondInRecord,
                                         int firstTexture, int secondTexture,
                                         int firstState, int secondState) {
        ImmediateBatcher batcher = new ImmediateBatcher();
        batcher.beginFrame(new DirectStagingRing(1, 1 << 16));
        assertTrue(batcher.captureQuads(1, 10, firstState, 0, firstTexture, firstInRecord, 0,
                false, vertices(1), 1, BYTES_PER_VERTEX));
        assertTrue(batcher.captureQuads(1, 10, secondState, 0, secondTexture, secondInRecord, 0,
                false, vertices(1), 1, BYTES_PER_VERTEX));
        return batcher.endFrame();
    }

    @Test
    void recordRidingFusionIgnoresTextureChanges() {
        BatchPlan plan = twoCaptures(true, true, 10, 20);
        assertEquals(1, plan.entryCount());
        assertEquals(12, plan.indexCount(0));
        assertTrue(plan.textureInRecord(0), "the fused entry carries the record-riding form");
    }

    @Test
    void perTextureFusionStopsAtTextureChanges() {
        BatchPlan plan = twoCaptures(false, false, 10, 20);
        assertEquals(2, plan.entryCount());
        assertEquals(6, plan.indexCount(0));
        assertEquals(6, plan.indexCount(1));
        // The entries carry their textures in capture order: the executor rebinds between them.
        assertEquals(10, plan.textureSlot(0));
        assertEquals(20, plan.textureSlot(1));
        assertFalse(plan.textureInRecord(0));
        assertFalse(plan.textureInRecord(1));
    }

    @Test
    void perTextureFusionStillMergesSameTextureNeighbours() {
        BatchPlan plan = twoCaptures(false, false, 10, 10);
        assertEquals(1, plan.entryCount());
        assertEquals(12, plan.indexCount(0));
        assertEquals(10, plan.textureSlot(0));
    }

    @Test
    void mixedPairDoesNotFuseAcrossTextureChanges() {
        // One side record-riding is not enough: the per-texture side of the pair can only
        // sample the bound texture, so the change stays a fusion boundary, and each entry
        // keeps its own form for the executor.
        BatchPlan plan = twoCaptures(true, false, 10, 20);
        assertEquals(2, plan.entryCount());
        assertTrue(plan.textureInRecord(0));
        assertFalse(plan.textureInRecord(1));

        BatchPlan reversed = twoCaptures(false, true, 10, 20);
        assertEquals(2, reversed.entryCount());
        assertFalse(reversed.textureInRecord(0));
        assertTrue(reversed.textureInRecord(1));
    }

    @Test
    void recordRidingFusionStopsAtStateChanges() {
        // The flag widens only the texture rule: a state change is a fusion boundary for
        // record-riding items exactly as for per-texture ones.
        BatchPlan plan = twoCaptures(true, true, 10, 20, 0, 1);
        assertEquals(2, plan.entryCount());
        assertTrue(plan.textureInRecord(0));
        assertTrue(plan.textureInRecord(1));
    }

    @Test
    void recordRidingFusionStopsAtPipelineChanges() {
        ImmediateBatcher batcher = new ImmediateBatcher();
        batcher.beginFrame(new DirectStagingRing(1, 1 << 16));
        assertTrue(batcher.captureQuads(1, 10, 0, 0, 10, true, 0, false,
                vertices(1), 1, BYTES_PER_VERTEX));
        assertTrue(batcher.captureQuads(1, 11, 0, 0, 20, true, 0, false,
                vertices(1), 1, BYTES_PER_VERTEX));
        BatchPlan plan = batcher.endFrame();
        assertEquals(2, plan.entryCount());
    }
}
