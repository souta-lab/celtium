package org.soutalab.celtium.vulkan;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;

/**
 * The CPU side of the immediate path's primitive expansion.
 * <p>
 * The line cases matter because Minecraft draws the block-selection outline with {@code GL_LINE_STRIP}: those batches are
 * refused by the renderer today, so this is the contract the native line path will rely on when it accepts them.
 */
class VulkanImmediateDrawerTest {
    @Test
    void aLineListKeepsItsPairs() {
        assertArrayEquals(new int[] {0, 1, 2, 3}, VulkanImmediateDrawer.expandIndices(GL11.GL_LINES, 4));
    }

    @Test
    void anOddLineListIsRefused() {
        assertNull(VulkanImmediateDrawer.expandIndices(GL11.GL_LINES, 5));
    }

    @Test
    void aLineStripBecomesConsecutiveSegments() {
        assertArrayEquals(new int[] {0, 1, 1, 2, 2, 3}, VulkanImmediateDrawer.expandIndices(GL11.GL_LINE_STRIP, 4));
    }

    @Test
    void aLineLoopAddsTheClosingSegment() {
        assertArrayEquals(new int[] {0, 1, 1, 2, 2, 3, 3, 0}, VulkanImmediateDrawer.expandIndices(GL11.GL_LINE_LOOP, 4));
    }

    @Test
    void aSingleVertexStripIsRefused() {
        assertNull(VulkanImmediateDrawer.expandIndices(GL11.GL_LINE_STRIP, 1));
    }

    @Test
    void quadsStillExpandToTwoTriangles() {
        assertArrayEquals(new int[] {0, 1, 2, 0, 2, 3}, VulkanImmediateDrawer.expandIndices(GL11.GL_QUADS, 4));
    }
}
