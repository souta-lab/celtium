package org.soutalab.celtium.vulkan.batch;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The S5-1 per-texture fallback: with bindless disabled, fusion must stop at texture changes
 * (a fused entry can only sample the one bound texture), and the plan must carry each entry's
 * texture slot so the executor knows where to rebind. With bindless enabled - the default -
 * texture changes fuse exactly as before, because the texture rides in the instance record.
 */
class ImmediateBatcherFallbackTest {

    private static final int BYTES_PER_VERTEX = 16;

    private static ByteBuffer vertices(int quads) {
        return ByteBuffer.allocate(quads * 4 * BYTES_PER_VERTEX);
    }

    /** Two captures that fuse whenever texture is ignored: same key, one shared instance record, contiguous quads. */
    private static BatchPlan twoCaptures(boolean bindless, int firstTexture, int secondTexture) {
        ImmediateBatcher batcher = new ImmediateBatcher();
        batcher.setBindlessEnabled(bindless);
        batcher.beginFrame(new DirectStagingRing(1, 1 << 16));
        assertTrue(batcher.captureQuads(1, 10, 0, 0, firstTexture, 0, false,
                vertices(1), 1, BYTES_PER_VERTEX));
        assertTrue(batcher.captureQuads(1, 10, 0, 0, secondTexture, 0, false,
                vertices(1), 1, BYTES_PER_VERTEX));
        return batcher.endFrame();
    }

    @Test
    void bindlessFusionIgnoresTextureChanges() {
        BatchPlan plan = twoCaptures(true, 10, 20);
        assertEquals(1, plan.entryCount());
        assertEquals(12, plan.indexCount(0));
    }

    @Test
    void fallbackFusionStopsAtTextureChanges() {
        BatchPlan plan = twoCaptures(false, 10, 20);
        assertEquals(2, plan.entryCount());
        assertEquals(6, plan.indexCount(0));
        assertEquals(6, plan.indexCount(1));
        // The entries carry their textures in capture order: the executor rebinds between them.
        assertEquals(10, plan.textureSlot(0));
        assertEquals(20, plan.textureSlot(1));
    }

    @Test
    void fallbackFusionStillMergesSameTextureNeighbours() {
        BatchPlan plan = twoCaptures(false, 10, 10);
        assertEquals(1, plan.entryCount());
        assertEquals(12, plan.indexCount(0));
        assertEquals(10, plan.textureSlot(0));
    }
}
