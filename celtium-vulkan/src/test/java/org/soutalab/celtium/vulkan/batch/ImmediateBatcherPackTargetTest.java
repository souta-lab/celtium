package org.soutalab.celtium.vulkan.batch;

import org.junit.jupiter.api.Test;

import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;

import org.soutalab.celtium.core.frame.ActiveRenderTargets;
import org.soutalab.celtium.core.shader.NonTerrainPackTarget;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The S3-a pack receptacle contract: with no pack target installed the batcher behaves
 * exactly as before (the plan's zero-tax requirement), and an installed target is only
 * ever consulted through the explicit pipeline query - capture and endFrame never call it.
 */
class ImmediateBatcherPackTargetTest {

    private static final class CountingTarget implements NonTerrainPackTarget {
        int pipelineCalls;
        int otherCalls;

        @Override
        public String passName() {
            this.otherCalls++;
            return "gbuffers_entities";
        }

        @Override
        public ActiveRenderTargets renderTargets() {
            this.otherCalls++;
            return null;
        }

        @Override
        public MemorySegment uniformBuffer() {
            this.otherCalls++;
            return null;
        }

        @Override
        public long pipelineHandle(String programName) {
            this.pipelineCalls++;
            return "gbuffers_entities".equals(programName) ? 4242L : NO_PIPELINE;
        }
    }

    @Test
    void unsetTargetMeansNativePipelineAndNoConsultation() {
        ImmediateBatcher batcher = new ImmediateBatcher();
        assertEquals(ImmediateBatcher.PACK_NATIVE, batcher.packPipeline("gbuffers_entities"));
    }

    @Test
    void captureAndEndFrameNeverConsultTheTarget() {
        CountingTarget target = new CountingTarget();
        ImmediateBatcher batcher = new ImmediateBatcher();
        batcher.setPackTarget(target);
        batcher.beginFrame(new DirectStagingRing(1, 1 << 16));
        ByteBuffer vertices = ByteBuffer.allocate(4 * 16);
        assertTrue(batcher.captureQuads(1, 10, 0, 0, 0, false, vertices, 1, 16));
        batcher.endFrame();
        assertEquals(0, target.pipelineCalls);
        assertEquals(0, target.otherCalls);

        // The explicit query is the one consultation point.
        assertEquals(4242L, batcher.packPipeline("gbuffers_entities"));
        assertEquals(1, target.pipelineCalls);
        assertEquals(NonTerrainPackTarget.NO_PIPELINE, batcher.packPipeline("shadow"));
        assertEquals(2, target.pipelineCalls);

        batcher.setPackTarget(null);
        assertEquals(ImmediateBatcher.PACK_NATIVE, batcher.packPipeline("gbuffers_entities"));
        assertEquals(2, target.pipelineCalls);
    }
}
