package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The topology conversion every immediate-mode batch goes through. It is pure arithmetic, so it is verified without a
 * GPU: the Vulkan side has no quads, and a wrong expansion would silently draw half a model.
 */
class VulkanImmediateDrawerTest {
    @Test
    void quadBecomesTwoTrianglesKeepingWinding() {
        int[] indices = VulkanImmediateDrawer.expandIndices(GL11.GL_QUADS, 4);
        assertNotNull(indices);
        assertArrayEquals(new int[]{0, 1, 2, 0, 2, 3}, indices);
    }

    @Test
    void multipleQuadsAreAppendedInOrder() {
        int[] indices = VulkanImmediateDrawer.expandIndices(GL11.GL_QUADS, 8);
        assertNotNull(indices);
        assertEquals(12, indices.length);
        assertArrayEquals(new int[]{0, 1, 2, 0, 2, 3, 4, 5, 6, 4, 6, 7}, indices);
    }

    @Test
    void aQuadBatchWithAPartialQuadIsRejectedRatherThanTruncated() {
        assertNull(VulkanImmediateDrawer.expandIndices(GL11.GL_QUADS, 6));
    }

    @Test
    void trianglesPassThrough() {
        assertArrayEquals(new int[]{0, 1, 2}, VulkanImmediateDrawer.expandIndices(GL11.GL_TRIANGLES, 3));
    }

    @Test
    void fanAndStripKeepTheirWinding() {
        assertArrayEquals(new int[]{0, 1, 2, 0, 2, 3}, VulkanImmediateDrawer.expandIndices(GL11.GL_TRIANGLE_FAN, 4));
        assertArrayEquals(new int[]{0, 1, 2, 2, 1, 3}, VulkanImmediateDrawer.expandIndices(GL11.GL_TRIANGLE_STRIP, 4));
    }

    @Test
    void modesWithoutAVulkanEquivalentSayWhy() {
        assertNull(VulkanImmediateDrawer.expandIndices(GL11.GL_LINES, 2));
        assertNotNull(VulkanImmediateDrawer.unsupportedReason(GL11.GL_LINES));
        assertNull(VulkanImmediateDrawer.unsupportedReason(GL11.GL_QUADS));
    }
}
