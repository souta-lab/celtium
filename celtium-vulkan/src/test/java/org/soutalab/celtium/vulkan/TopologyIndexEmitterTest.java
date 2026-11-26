package org.soutalab.celtium.vulkan;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryUtil;
import org.soutalab.celtium.vulkan.batch.SharedQuadIndex;

/**
 * The CPU side of the immediate path's primitive expansion, after Stage 5 slice S5-0 moved it
 * out of {@code VulkanImmediateDrawer.expandIndices} (and its {@code int[]} cache) into
 * {@link TopologyIndexEmitter}, which writes into mapped memory instead of returning an array.
 * <p>
 * These cases are the old drawer's oracle cases carried over verbatim, plus the modes the old
 * test never pinned (strips, quad strips, polygons, identity triangles) and the equivalence that
 * matters most now: the emitter's streamed quad pattern must be the shared buffer's pattern,
 * because a mesh addresses one or the other depending on its indexing scheme and the picture
 * cannot depend on which.
 * <p>
 * The line cases matter because Minecraft draws the block-selection outline with
 * {@code GL_LINE_STRIP}: those batches are refused by the renderer today, so this is the contract
 * the native line path will rely on when it accepts them.
 */
class TopologyIndexEmitterTest {

    /** Expands through the emitter into native scratch memory and reads the sequence back. */
    private static int[] expand(int glMode, int vertexCount, int baseVertex) {
        int count = TopologyIndexEmitter.indexCount(glMode, vertexCount);
        assertTrue(count >= 0, "mode " + glMode + " count " + vertexCount + " was refused");
        long address = MemoryUtil.nmemAlloc(Math.max(count, 1) * 4L);
        try {
            int written = TopologyIndexEmitter.writeIndices(glMode, vertexCount, baseVertex, address);
            assertEquals(count, written, "written count disagrees with indexCount");
            int[] result = new int[count];
            for (int i = 0; i < count; i++) {
                result[i] = MemoryUtil.memGetInt(address + (long) i * 4L);
            }
            return result;
        } finally {
            MemoryUtil.nmemFree(address);
        }
    }

    @Test
    void aLineListKeepsItsPairs() {
        assertArrayEquals(new int[] {0, 1, 2, 3}, expand(GL11.GL_LINES, 4, 0));
    }

    @Test
    void anOddLineListIsRefused() {
        assertEquals(-1, TopologyIndexEmitter.indexCount(GL11.GL_LINES, 5));
    }

    @Test
    void aLineStripBecomesConsecutiveSegments() {
        assertArrayEquals(new int[] {0, 1, 1, 2, 2, 3}, expand(GL11.GL_LINE_STRIP, 4, 0));
    }

    @Test
    void aLineLoopAddsTheClosingSegment() {
        assertArrayEquals(new int[] {0, 1, 1, 2, 2, 3, 3, 0}, expand(GL11.GL_LINE_LOOP, 4, 0));
    }

    @Test
    void aSingleVertexStripIsRefused() {
        assertEquals(-1, TopologyIndexEmitter.indexCount(GL11.GL_LINE_STRIP, 1));
    }

    @Test
    void triangleFansPreserveWindingAndFanOrigin() {
        assertArrayEquals(new int[] {0, 1, 2}, expand(GL11.GL_TRIANGLE_FAN, 3, 0));
        assertArrayEquals(new int[] {0, 1, 2, 0, 2, 3}, expand(GL11.GL_TRIANGLE_FAN, 4, 0));
        assertArrayEquals(new int[] {0, 1, 2, 0, 2, 3, 0, 3, 4}, expand(GL11.GL_TRIANGLE_FAN, 5, 0));
    }

    @Test
    void quadsStillExpandToTwoTriangles() {
        assertArrayEquals(new int[] {0, 1, 2, 0, 2, 3}, expand(GL11.GL_QUADS, 4, 0));
    }

    @Test
    void trianglesAreTheIdentitySequence() {
        assertArrayEquals(new int[] {0, 1, 2, 3, 4}, expand(GL11.GL_TRIANGLES, 5, 0));
    }

    @Test
    void triangleStripsAlternateWinding() {
        assertArrayEquals(new int[] {0, 1, 2, 2, 1, 3, 2, 3, 4},
                expand(GL11.GL_TRIANGLE_STRIP, 5, 0));
    }

    @Test
    void quadStripsPairConsecutiveQuads() {
        assertArrayEquals(new int[] {0, 1, 3, 0, 3, 2, 2, 3, 5, 2, 5, 4},
                expand(GL11.GL_QUAD_STRIP, 6, 0));
    }

    @Test
    void polygonsFanFromTheFirstVertex() {
        assertArrayEquals(new int[] {0, 1, 2, 0, 2, 3, 0, 3, 4}, expand(GL11.GL_POLYGON, 5, 0));
        assertEquals(-1, TopologyIndexEmitter.indexCount(GL11.GL_POLYGON, 2));
    }

    @Test
    void theBaseVertexShiftsEveryIndex() {
        assertArrayEquals(new int[] {100, 101, 101, 102, 102, 103},
                expand(GL11.GL_LINE_STRIP, 4, 100));
        assertArrayEquals(new int[] {8, 9, 10, 8, 10, 11}, expand(GL11.GL_QUADS, 4, 8));
    }

    @Test
    void theStreamedQuadPatternIsTheSharedPattern() {
        int[] shared = SharedQuadIndex.generate(4);
        assertArrayEquals(shared, expand(GL11.GL_QUADS, 16, 0));
        int[] shifted = expand(GL11.GL_QUADS, 12, 40);
        int[] pattern = SharedQuadIndex.generate(3);
        assertEquals(pattern.length, shifted.length);
        for (int i = 0; i < pattern.length; i++) {
            assertEquals(pattern[i] + 40, shifted[i], "index " + i);
        }
    }

    @Test
    void refusalsMatchTheOldExpansion() {
        assertEquals(-1, TopologyIndexEmitter.indexCount(GL11.GL_QUADS, 6));
        assertEquals(-1, TopologyIndexEmitter.indexCount(GL11.GL_TRIANGLE_FAN, 1));
        assertEquals(-1, TopologyIndexEmitter.indexCount(GL11.GL_TRIANGLE_STRIP, 1));
        assertEquals(-1, TopologyIndexEmitter.indexCount(GL11.GL_LINE_LOOP, 1));
        assertEquals(-1, TopologyIndexEmitter.indexCount(GL11.GL_QUAD_STRIP, 3));
        assertEquals(-1, TopologyIndexEmitter.indexCount(9999, 10));
        // A non-positive count was an empty expansion for every mode, known or not - never a refusal.
        assertEquals(0, TopologyIndexEmitter.indexCount(GL11.GL_QUADS, 0));
        assertEquals(0, TopologyIndexEmitter.indexCount(9999, 0));
        assertEquals(0, TopologyIndexEmitter.indexCount(GL11.GL_TRIANGLES, -3));
    }

    @Test
    void writtenCountsMatchIndexCountsAcrossModesAndCounts() {
        int[] modes = {GL11.GL_QUADS, GL11.GL_TRIANGLES, GL11.GL_TRIANGLE_FAN, GL11.GL_TRIANGLE_STRIP,
                GL11.GL_LINES, GL11.GL_LINE_STRIP, GL11.GL_LINE_LOOP, GL11.GL_QUAD_STRIP, GL11.GL_POLYGON};
        for (int mode : modes) {
            for (int vertexCount = 0; vertexCount <= 20; vertexCount++) {
                if (TopologyIndexEmitter.indexCount(mode, vertexCount) < 0) {
                    continue;
                }
                expand(mode, vertexCount, 3);
            }
        }
    }

    @Test
    void writingAnUnknownModeThrows() {
        long address = MemoryUtil.nmemAlloc(16L);
        try {
            assertThrows(IllegalArgumentException.class,
                    () -> TopologyIndexEmitter.writeIndices(9999, 4, 0, address));
        } finally {
            MemoryUtil.nmemFree(address);
        }
    }
}
