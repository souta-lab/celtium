package org.soutalab.celtium.vulkan.lane2;

import org.junit.jupiter.api.Test;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

import org.soutalab.celtium.core.frame.ActiveRenderTargets;
import org.soutalab.celtium.core.shader.NonTerrainPackTarget;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParticleInstancerTest {

    @Test
    void recordLayoutIs24Bytes() {
        assertEquals(24, ParticleInstancer.INSTANCE_BYTES);
        assertEquals(0, ParticleInstancer.OFFSET_POSITION);
        assertEquals(12, ParticleInstancer.OFFSET_UV);
        assertEquals(20, ParticleInstancer.OFFSET_COLOR);
    }

    @Test
    void writtenRecordsReadBackAtTheLayoutOffsets() {
        try (ParticleInstancer instancer = new ParticleInstancer(4)) {
            assertTrue(instancer.add(1.5f, 2.5f, 3.5f, 0.25f, 0.75f, 0xA0B0C0D0));
            assertTrue(instancer.add(-1.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0xFF0000FF));
            assertEquals(2, instancer.count());
            MemorySegment segment = instancer.segment();
            assertEquals(1.5f, segment.get(ValueLayout.JAVA_FLOAT, 0));
            assertEquals(2.5f, segment.get(ValueLayout.JAVA_FLOAT, 4));
            assertEquals(3.5f, segment.get(ValueLayout.JAVA_FLOAT, 8));
            assertEquals(0.25f, segment.get(ValueLayout.JAVA_FLOAT, 12));
            assertEquals(0.75f, segment.get(ValueLayout.JAVA_FLOAT, 16));
            assertEquals(0xA0B0C0D0, segment.get(ValueLayout.JAVA_INT, 20));
            long second = ParticleInstancer.INSTANCE_BYTES;
            assertEquals(-1.0f, segment.get(ValueLayout.JAVA_FLOAT, second));
            assertEquals(0xFF0000FF, segment.get(ValueLayout.JAVA_INT, second + 20));
        }
    }

    @Test
    void fullInstancerRefusesInsteadOfOverwriting() {
        try (ParticleInstancer instancer = new ParticleInstancer(1)) {
            assertTrue(instancer.add(0, 0, 0, 0, 0, 0));
            assertFalse(instancer.add(1, 1, 1, 1, 1, 1));
            assertEquals(1, instancer.count());
            instancer.beginFrame();
            assertEquals(0, instancer.count());
            assertTrue(instancer.add(1, 1, 1, 1, 1, 1));
        }
    }

    @Test
    void billboardCornersExpandAlongRightAndUp() {
        // Centre (10, 20, 30), camera right = +x, up = +y, size 2: corners sit one unit
        // out along each axis from the centre.
        float[] corner0 = ParticleInstancer.billboardCorner(
                10, 20, 30, 1, 0, 0, 0, 1, 0, 2.0f, 0);
        assertEquals(9.0f, corner0[0]);
        assertEquals(19.0f, corner0[1]);
        assertEquals(30.0f, corner0[2]);
        float[] corner2 = ParticleInstancer.billboardCorner(
                10, 20, 30, 1, 0, 0, 0, 1, 0, 2.0f, 2);
        assertEquals(11.0f, corner2[0]);
        assertEquals(21.0f, corner2[1]);
    }

    /** A pack target that defines exactly one program. */
    private static final class OneProgramTarget implements NonTerrainPackTarget {
        int calls;

        @Override
        public String passName() {
            return "gbuffers_particles";
        }

        @Override
        public ActiveRenderTargets renderTargets() {
            return null;
        }

        @Override
        public MemorySegment uniformBuffer() {
            return null;
        }

        @Override
        public long pipelineHandle(String programName) {
            this.calls++;
            return "gbuffers_particles".equals(programName) ? 777L : NO_PIPELINE;
        }
    }

    @Test
    void packTargetReceptacleResolvesAndSkips() {
        try (ParticleInstancer instancer = new ParticleInstancer(2)) {
            assertEquals(org.soutalab.celtium.vulkan.batch.ImmediateBatcher.PACK_NATIVE,
                    instancer.packPipeline("gbuffers_particles"));
            OneProgramTarget target = new OneProgramTarget();
            instancer.setPackTarget(target);
            assertEquals(777L, instancer.packPipeline("gbuffers_particles"));
            assertEquals(NonTerrainPackTarget.NO_PIPELINE,
                    instancer.packPipeline("shadow_particles"));
            instancer.setPackTarget(null);
            assertEquals(org.soutalab.celtium.vulkan.batch.ImmediateBatcher.PACK_NATIVE,
                    instancer.packPipeline("gbuffers_particles"));
        }
    }
}
