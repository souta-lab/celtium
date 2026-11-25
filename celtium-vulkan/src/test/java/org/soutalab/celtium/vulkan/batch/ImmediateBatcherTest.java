package org.soutalab.celtium.vulkan.batch;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImmediateBatcherTest {

    private static final int BYTES_PER_VERTEX = 16;

    private static ByteBuffer vertices(int quads) {
        return ByteBuffer.allocate(quads * 4 * BYTES_PER_VERTEX);
    }

    private static ImmediateBatcher batcherOn(DirectStagingRing ring) {
        ImmediateBatcher batcher = new ImmediateBatcher();
        batcher.beginFrame(ring);
        return batcher;
    }

    @Test
    void opaqueItemsSortByPipelineWithinAPass() {
        ImmediateBatcher batcher = batcherOn(new DirectStagingRing(1, 1 << 16));
        assertTrue(batcher.captureQuads(1, 30, 0, 0, 0, false, vertices(1), 1, BYTES_PER_VERTEX));
        assertTrue(batcher.captureQuads(1, 10, 0, 0, 0, false, vertices(1), 1, BYTES_PER_VERTEX));
        assertTrue(batcher.captureQuads(1, 20, 0, 0, 0, false, vertices(1), 1, BYTES_PER_VERTEX));
        BatchPlan plan = batcher.endFrame();
        assertEquals(3, plan.entryCount());
        assertEquals(10, plan.pipelineKey(0));
        assertEquals(20, plan.pipelineKey(1));
        assertEquals(30, plan.pipelineKey(2));
        // Index ranges follow capture order, not plan order: pipeline 10 was captured second.
        assertEquals(6, plan.firstIndex(0));
        assertEquals(12, plan.firstIndex(1));
        assertEquals(0, plan.firstIndex(2));
    }

    @Test
    void vertexOffsetLandsPatternIndicesOnTheRingVertices() {
        ImmediateBatcher batcher = batcherOn(new DirectStagingRing(1, 1 << 16));
        // First capture: 2 quads at ring offset 0. Second: 1 quad right behind it.
        batcher.captureQuads(1, 10, 0, 0, 0, false, vertices(2), 2, BYTES_PER_VERTEX);
        batcher.captureQuads(1, 10, 0, 0, 0, false, vertices(1), 1, BYTES_PER_VERTEX);
        BatchPlan plan = batcher.endFrame();
        // Both entries share vertexOffset 0: pattern value 4q + 0 is the absolute vertex index
        // for every quad of this frame, because quads and vertices were laid down in lockstep.
        assertEquals(2, plan.entryCount());
        assertEquals(0, plan.vertexOffset(0));
        assertEquals(0, plan.vertexOffset(1));
        assertEquals(12, plan.indexCount(0));
        assertEquals(6, plan.indexCount(1));
    }

    @Test
    void orderedItemsComeAfterOpaqueInTheSamePassAndKeepCaptureOrder() {
        ImmediateBatcher batcher = batcherOn(new DirectStagingRing(1, 1 << 16));
        batcher.captureQuads(1, 10, 0, 0, 0, true, vertices(1), 1, BYTES_PER_VERTEX);
        batcher.captureQuads(1, 99, 0, 0, 0, false, vertices(1), 1, BYTES_PER_VERTEX);
        batcher.captureQuads(1, 5, 0, 0, 0, true, vertices(1), 1, BYTES_PER_VERTEX);
        BatchPlan plan = batcher.endFrame();
        assertEquals(3, plan.entryCount());
        assertEquals(99, plan.pipelineKey(0));
        assertEquals(10, plan.pipelineKey(1));
        assertEquals(5, plan.pipelineKey(2));
    }

    @Test
    void passesStayInPassOrder() {
        ImmediateBatcher batcher = batcherOn(new DirectStagingRing(1, 1 << 16));
        batcher.captureQuads(2, 1, 0, 0, 0, false, vertices(1), 1, BYTES_PER_VERTEX);
        batcher.captureQuads(1, 1, 0, 0, 0, false, vertices(1), 1, BYTES_PER_VERTEX);
        BatchPlan plan = batcher.endFrame();
        assertEquals(6, plan.firstIndex(0));
        assertEquals(0, plan.firstIndex(1));
    }

    @Test
    void adjacentCapturesOfOneInstanceRecordFuse() {
        ImmediateBatcher batcher = batcherOn(new DirectStagingRing(1, 1 << 16));
        // Same pipeline, same shared instance record 7, back to back.
        batcher.captureQuads(1, 10, 0, 0, 0, 7, false, vertices(1), 1, BYTES_PER_VERTEX);
        batcher.captureQuads(1, 10, 0, 0, 0, 7, false, vertices(2), 2, BYTES_PER_VERTEX);
        BatchPlan plan = batcher.endFrame();
        assertEquals(2, plan.sourceItems());
        assertEquals(1, plan.entryCount());
        assertEquals(18, plan.indexCount(0));
        assertEquals(7, plan.firstInstance(0));
    }

    @Test
    void distinctInstanceRecordsDoNotFuse() {
        ImmediateBatcher batcher = batcherOn(new DirectStagingRing(1, 1 << 16));
        batcher.captureQuads(1, 10, 0, 0, 0, 7, false, vertices(1), 1, BYTES_PER_VERTEX);
        batcher.captureQuads(1, 10, 0, 0, 0, 8, false, vertices(1), 1, BYTES_PER_VERTEX);
        BatchPlan plan = batcher.endFrame();
        assertEquals(2, plan.entryCount());
    }

    @Test
    void fullRingDropsTheCaptureAndCountsIt() {
        ImmediateBatcher batcher = batcherOn(new DirectStagingRing(1, 64));
        assertTrue(batcher.captureQuads(1, 10, 0, 0, 0, false, vertices(1), 1, BYTES_PER_VERTEX));
        assertFalse(batcher.captureQuads(1, 10, 0, 0, 0, false, vertices(1), 1, BYTES_PER_VERTEX));
        assertEquals(1, batcher.droppedCaptures());
        assertEquals(1, batcher.endFrame().entryCount());
    }

    @Test
    void mismatchedVertexDataIsRejected() {
        ImmediateBatcher batcher = batcherOn(new DirectStagingRing(1, 1 << 16));
        try {
            batcher.captureQuads(1, 10, 0, 0, 0, false, vertices(1), 2, BYTES_PER_VERTEX);
            throw new AssertionError("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // rejected before anything was captured
        }
        assertEquals(0, batcher.capturedItems());
    }
}
