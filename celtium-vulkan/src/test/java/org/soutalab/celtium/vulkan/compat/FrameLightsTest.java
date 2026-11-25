package org.soutalab.celtium.vulkan.compat;

import org.embeddedt.embeddium.impl.render.chunk.light.DynamicLightService;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The frame block's light region, byte for byte: the layout is the one the shipped terrain shaders
 * read (item 430), so a drift here would not fail anywhere - the raise would simply land somewhere
 * else or nowhere. Pinned at a nonzero base offset as well, because in the frame block this region
 * does not start at zero.
 */
class FrameLightsTest {

    @Test
    void theBlockIsHeaderPlusEverySourceSlot() {
        assertEquals(16 + DynamicLightService.MAX_LIGHTS * 16, FrameLights.BLOCK_BYTES);
        assertEquals(528, FrameLights.BLOCK_BYTES);
    }

    @Test
    void writePutsCountCameraAndSourcesAtTheSettledOffsets() {
        ByteBuffer block = ByteBuffer.allocate(1024);
        float[] sources = {1.0F, 2.0F, 3.0F, 15.0F, -4.0F, -5.0F, -6.0F, 9.0F};
        int written = FrameLights.write(block, 64, sources, 2, 100.5, 64.0, -200.25);
        assertEquals(2, written);
        assertEquals(2.0F, block.getFloat(64 + FrameLights.COUNT_OFFSET));
        assertEquals(100.5F, block.getFloat(64 + FrameLights.CAMERA_OFFSET));
        assertEquals(64.0F, block.getFloat(64 + FrameLights.CAMERA_OFFSET + 4));
        assertEquals(-200.25F, block.getFloat(64 + FrameLights.CAMERA_OFFSET + 8));
        int first = 64 + FrameLights.SOURCES_OFFSET;
        assertEquals(1.0F, block.getFloat(first));
        assertEquals(15.0F, block.getFloat(first + 12));
        assertEquals(-4.0F, block.getFloat(first + FrameLights.SOURCE_BYTES));
        assertEquals(9.0F, block.getFloat(first + FrameLights.SOURCE_BYTES + 12));
    }

    @Test
    void anOverReportingServiceIsClampedToTheBlock() {
        ByteBuffer block = ByteBuffer.allocate(FrameLights.BLOCK_BYTES);
        float[] sources = new float[(DynamicLightService.MAX_LIGHTS + 8) * 4];
        int written = FrameLights.write(block, 0, sources, DynamicLightService.MAX_LIGHTS + 8,
                0.0, 0.0, 0.0);
        assertEquals(DynamicLightService.MAX_LIGHTS, written);
        assertEquals((float) DynamicLightService.MAX_LIGHTS,
                block.getFloat(FrameLights.COUNT_OFFSET));
    }

    @Test
    void clearEmptiesTheBlock() {
        ByteBuffer block = ByteBuffer.allocate(FrameLights.BLOCK_BYTES);
        FrameLights.write(block, 0, new float[] {1.0F, 2.0F, 3.0F, 4.0F}, 1, 5.0, 6.0, 7.0);
        FrameLights.clear(block, 0);
        assertEquals(0.0F, block.getFloat(FrameLights.COUNT_OFFSET));
    }
}
