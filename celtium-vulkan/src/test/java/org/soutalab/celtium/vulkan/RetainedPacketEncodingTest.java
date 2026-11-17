package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Item 350: the retained packet encoding on {@link VulkanMultiDrawBatch} must be byte-identical to
 * the renderer's per-offer loop - same fields, same order - for both index sources, and the offset
 * verdict it carries must match the per-offer walk's.
 */
class RetainedPacketEncodingTest {

    private static int field(long base, int command, int offset) {
        return MemoryUtil.memGetInt(base + (long) command * VulkanMultiDrawBatch.INDIRECT_COMMAND_BYTES + offset);
    }

    @Test
    void sharedVariantEncodesCountInstanceFirstIndexBaseVertex() {
        VulkanMultiDrawBatch batch = new VulkanMultiDrawBatch(4);
        batch.appendDrawCommand(100, 60, 0L);
        batch.appendDrawCommand(220, 36, 0L);
        try {
            long base = batch.retainedPacketsAddress(false);
            assertEquals(60, field(base, 0, 0));
            assertEquals(1, field(base, 0, 4));
            assertEquals(0, field(base, 0, 8));
            assertEquals(100, field(base, 0, 12));
            assertEquals(0, field(base, 0, 16));
            assertEquals(36, field(base, 1, 0));
            assertEquals(220, field(base, 1, 12));
            assertTrue(batch.unsortedOffsetsClean());
        } finally {
            batch.delete();
        }
    }

    @Test
    void regionVariantResolvesFirstIndexFromTheByteOffset() {
        VulkanMultiDrawBatch batch = new VulkanMultiDrawBatch(4);
        batch.appendDrawCommand(7, 48, 4096L);
        batch.appendDrawCommand(9, 24, 0L);
        try {
            long base = batch.retainedPacketsAddress(true);
            assertEquals(48, field(base, 0, 0));
            assertEquals(1024, field(base, 0, 8));
            assertEquals(7, field(base, 0, 12));
            assertEquals(0, field(base, 1, 8));
            // A non-zero element pointer anywhere makes the batch ineligible for the shared path.
            assertFalse(batch.unsortedOffsetsClean());
        } finally {
            batch.delete();
        }
    }

    @Test
    void mergedCommandsEncodeTheMergedCount() {
        VulkanMultiDrawBatch batch = new VulkanMultiDrawBatch(4);
        batch.appendDrawCommand(0, 60, 0L);
        batch.mergeIntoLastCommand(36);
        try {
            long base = batch.retainedPacketsAddress(false);
            assertEquals(96, field(base, 0, 0));
            assertEquals(1, batch.size());
        } finally {
            batch.delete();
        }
    }
}
