package org.soutalab.celtium.vulkan.hiz;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HiZPyramidTest {

    @Test
    void plan1280x720TracksOddDimensionsConservatively() {
        HiZPyramidPlan plan = HiZPyramidPlan.forDepthExtent(1280, 720);
        assertEquals(640, plan.baseWidth());
        assertEquals(360, plan.baseHeight());
        // 640x360 -> 320x180 -> 160x90 -> 80x45 -> 40x22 -> 20x11 -> 10x5 -> 5x2 -> 2x1 -> 1x1 (10 mips)
        assertEquals(10, plan.mipLevels());

        HiZPyramidPlan.MipDispatch m0 = plan.mip(0);
        assertTrue(m0.readsDepthAttachment());
        assertEquals(1280, m0.srcWidth());
        assertEquals(720, m0.srcHeight());
        assertEquals(640, m0.dstWidth());
        assertEquals(360, m0.dstHeight());
        assertEquals(80, m0.groupsX());
        assertEquals(45, m0.groupsY());
        assertFalse(m0.oddSrcX());
        assertFalse(m0.oddSrcY());

        // Mip 4: 80x45 -> 40x22 (oddSrcY must be true so row 44 is folded)
        HiZPyramidPlan.MipDispatch m4 = plan.mip(4);
        assertEquals(80, m4.srcWidth());
        assertEquals(45, m4.srcHeight());
        assertEquals(40, m4.dstWidth());
        assertEquals(22, m4.dstHeight());
        assertFalse(m4.oddSrcX());
        assertTrue(m4.oddSrcY());

        // Mip 6: 20x11 -> 10x5 (oddSrcY=true)
        assertTrue(plan.mip(6).oddSrcY());
        // Mip 7: 10x5 -> 5x2 (oddSrcY=true)
        assertTrue(plan.mip(7).oddSrcY());
        // Mip 8: 5x2 -> 2x1 (oddSrcX=true)
        assertTrue(plan.mip(8).oddSrcX());
        assertFalse(plan.mip(8).oddSrcY());

        HiZPyramidPlan.MipDispatch last = plan.mip(plan.mipLevels() - 1);
        assertEquals(1, last.dstWidth());
        assertEquals(1, last.dstHeight());
        assertEquals(1, last.groupsX());
        assertEquals(1, last.groupsY());

        // R32_SFLOAT base is 640*360*4 = 921,600B; full mip chain ~1.33x (< 1.3 MiB)
        assertTrue(plan.estimatedBytes() > 921_600L);
        assertTrue(plan.estimatedBytes() < 1_300_000L);
    }

    @Test
    void plan1x1ProducesSingleMip() {
        HiZPyramidPlan plan = HiZPyramidPlan.forDepthExtent(1, 1);
        assertEquals(1, plan.mipLevels());
        assertEquals(1, plan.baseWidth());
        assertEquals(1, plan.baseHeight());
        assertFalse(plan.mip(0).oddSrcX());
        assertFalse(plan.mip(0).oddSrcY());
        assertEquals(4L, plan.estimatedBytes());
    }

    @Test
    void invalidDimensionsThrow() {
        assertThrows(IllegalArgumentException.class, () -> HiZPyramidPlan.forDepthExtent(0, 720));
        assertThrows(IllegalArgumentException.class, () -> HiZPyramidPlan.forDepthExtent(1280, -1));
    }

    @Test
    void selectMipLevelClampsToValidRange() {
        HiZPyramidPlan plan = HiZPyramidPlan.forDepthExtent(1280, 720);
        assertEquals(0, plan.selectMipLevel(0.5f));
        assertEquals(0, plan.selectMipLevel(2.0f));
        assertEquals(1, plan.selectMipLevel(4.0f));
        assertEquals(2, plan.selectMipLevel(8.0f));
        assertEquals(plan.mipLevels() - 1, plan.selectMipLevel(10000.0f));
    }

    @Test
    void occlusionTestMatchesStandardAndReverseZ() {
        // Standard Z: occluded iff boxMinDepth > hizMaxDepth
        assertTrue(HiZPyramidPlan.isOccluded(0.8f, 0.5f, false));
        assertFalse(HiZPyramidPlan.isOccluded(0.5f, 0.5f, false));
        assertFalse(HiZPyramidPlan.isOccluded(0.3f, 0.5f, false));

        // Reverse Z: occluded iff boxMaxDepth < hizMinDepth
        assertTrue(HiZPyramidPlan.isOccluded(0.2f, 0.5f, true));
        assertFalse(HiZPyramidPlan.isOccluded(0.5f, 0.5f, true));
        assertFalse(HiZPyramidPlan.isOccluded(0.7f, 0.5f, true));
    }

    @Test
    void recorderEmitsOrderedDispatchesAndPerMipBarriers() {
        HiZPyramidPlan plan = HiZPyramidPlan.forDepthExtent(16, 16);
        // 8x8 -> 4x4 -> 2x2 -> 1x1 (4 mips)
        assertEquals(4, plan.mipLevels());

        List<String> events = new ArrayList<>();
        HiZBuildRecorder recorder = new HiZBuildRecorder(false);
        recorder.record(plan, new HiZBuildRecorder.CommandSink() {
            @Override
            public void transitionAllMipsToGeneral(int mipCount) {
                events.add("INIT_ALL:" + mipCount);
            }

            @Override
            public void bindPipeline() {
                events.add("BIND_PIPE");
            }

            @Override
            public void bindMipDescriptor(int dstMip, int srcMip) {
                events.add("DESC:" + dstMip + "<-" + srcMip);
            }

            @Override
            public void pushConstants(int srcW, int srcH, int dstW, int dstH, int flags, int mipLevel) {
                events.add("PC:" + mipLevel + ":" + srcW + "x" + srcH + "->" + dstW + "x" + dstH + ":f=" + flags);
            }

            @Override
            public void dispatch(int gx, int gy, int gz) {
                events.add("DISPATCH:" + gx + "x" + gy + "x" + gz);
            }

            @Override
            public void transitionMipGeneralToShaderRead(int mipLevel) {
                events.add("BARRIER_READ:" + mipLevel);
            }
        }, true);

        assertEquals(1, recorder.framesRecorded());
        assertEquals(4, recorder.totalDispatches());
        assertEquals(5, recorder.totalBarriers()); // 1 init + 4 per-mip transitions
        assertEquals("INIT_ALL:4", events.get(0));
        assertEquals("BIND_PIPE", events.get(1));
        assertEquals("DESC:0<-" + (-1), events.get(2));
        assertEquals("PC:0:16x16->8x8:f=" + HiZBuildRecorder.FLAG_SRC_DEPTH_ATTACHMENT, events.get(3));
        assertEquals("DISPATCH:1x1x1", events.get(4));
        assertEquals("BARRIER_READ:0", events.get(5));
        assertEquals("DESC:1<-0", events.get(6));
        assertEquals("BARRIER_READ:3", events.get(events.size() - 1));
    }

    @Test
    void pushConstantsPackLittleEndian24Bytes() {
        HiZPyramidPlan plan = HiZPyramidPlan.forDepthExtent(1280, 720);
        ByteBuffer buf = ByteBuffer.allocate(HiZBuildRecorder.PUSH_CONSTANT_BYTES);
        HiZBuildRecorder.packPushConstants(plan.mip(0), true, buf);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(1280, buf.getInt(0));
        assertEquals(720, buf.getInt(4));
        assertEquals(640, buf.getInt(8));
        assertEquals(360, buf.getInt(12));
        assertEquals(HiZBuildRecorder.FLAG_REVERSE_Z | HiZBuildRecorder.FLAG_SRC_DEPTH_ATTACHMENT, buf.getInt(16));
        assertEquals(0, buf.getInt(20));
    }
}
