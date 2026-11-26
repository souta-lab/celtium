package org.soutalab.celtium.vulkan.batch;

import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the non-quad emitter's issue forms (Stage 5 stage 6 slice S6-a): which modes draw
 * non-indexed, which write which index expansion into the ring, and how staging failure is
 * reported. The expected sequences are the accept path's own expansions, read back out of
 * the staged ring bytes.
 */
class NonQuadImmediateEmitterTest {

    private static final int STRIDE = 8;

    /** Vertex data whose every byte names its position, so staged copies can be checked verbatim. */
    private static ByteBuffer vertices(int count) {
        ByteBuffer data = ByteBuffer.allocateDirect(count * STRIDE);
        for (int i = 0; i < count * STRIDE; i++) {
            data.put(i, (byte) (i + 1));
        }
        data.position(0);
        data.limit(count * STRIDE);
        return data;
    }

    private static int stagedIndex(DirectStagingRing ring, long indexOffset, int i) {
        ByteBuffer buffer = ring.buffer();
        return buffer.order(ByteOrder.LITTLE_ENDIAN).getInt((int) indexOffset + i * 4);
    }

    private static void assertVertexBytesCopied(DirectStagingRing ring, long offset, int count) {
        ByteBuffer buffer = ring.buffer();
        for (int i = 0; i < count * STRIDE; i++) {
            assertEquals((byte) (i + 1), buffer.get((int) offset + i), "byte " + i);
        }
    }

    @Test
    void trianglesAreStagedNonIndexed() {
        DirectStagingRing ring = new DirectStagingRing(1, 1 << 16);
        NonQuadImmediateEmitter emitter = new NonQuadImmediateEmitter(ring);
        NonQuadImmediateEmitter.Emission emission = emitter.stage(vertices(6), 6, STRIDE, GL11.GL_TRIANGLES);
        assertFalse(emission.indexed());
        assertEquals(-1L, emission.indexOffset);
        assertEquals(0, emission.indexCount);
        assertEquals(6, emission.vertexCount);
        assertEquals(0L, emission.vertexOffset);
        assertVertexBytesCopied(ring, emission.vertexOffset, 6);
        // Nothing but the vertex bytes was reserved.
        assertEquals(6L * STRIDE, ring.usedInCurrentSlot());
    }

    @Test
    void linesAreStagedNonIndexed() {
        DirectStagingRing ring = new DirectStagingRing(1, 1 << 16);
        NonQuadImmediateEmitter emitter = new NonQuadImmediateEmitter(ring);
        NonQuadImmediateEmitter.Emission emission = emitter.stage(vertices(4), 4, STRIDE, GL11.GL_LINES);
        assertFalse(emission.indexed());
        assertEquals(0, emission.indexCount);
        assertVertexBytesCopied(ring, emission.vertexOffset, 4);
    }

    @Test
    void triangleStripWritesTheStripExpansionBesideTheVertices() {
        DirectStagingRing ring = new DirectStagingRing(1, 1 << 16);
        NonQuadImmediateEmitter emitter = new NonQuadImmediateEmitter(ring);
        NonQuadImmediateEmitter.Emission emission = emitter.stage(vertices(5), 5, STRIDE, GL11.GL_TRIANGLE_STRIP);
        assertTrue(emission.indexed());
        assertEquals(9, emission.indexCount);
        int[] expected = {0, 1, 2, 2, 1, 3, 2, 3, 4};
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], stagedIndex(ring, emission.indexOffset, i), "index " + i);
        }
        assertVertexBytesCopied(ring, emission.vertexOffset, 5);
    }

    @Test
    void triangleFanWritesTheFanExpansion() {
        DirectStagingRing ring = new DirectStagingRing(1, 1 << 16);
        NonQuadImmediateEmitter emitter = new NonQuadImmediateEmitter(ring);
        NonQuadImmediateEmitter.Emission emission = emitter.stage(vertices(5), 5, STRIDE, GL11.GL_TRIANGLE_FAN);
        assertEquals(9, emission.indexCount);
        int[] expected = {0, 1, 2, 0, 2, 3, 0, 3, 4};
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], stagedIndex(ring, emission.indexOffset, i), "index " + i);
        }
    }

    @Test
    void polygonWritesTheFanExpansion() {
        DirectStagingRing ring = new DirectStagingRing(1, 1 << 16);
        NonQuadImmediateEmitter emitter = new NonQuadImmediateEmitter(ring);
        NonQuadImmediateEmitter.Emission emission = emitter.stage(vertices(4), 4, STRIDE, GL11.GL_POLYGON);
        assertEquals(6, emission.indexCount);
        int[] expected = {0, 1, 2, 0, 2, 3};
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], stagedIndex(ring, emission.indexOffset, i), "index " + i);
        }
    }

    @Test
    void lineStripWritesSegmentIndices() {
        DirectStagingRing ring = new DirectStagingRing(1, 1 << 16);
        NonQuadImmediateEmitter emitter = new NonQuadImmediateEmitter(ring);
        NonQuadImmediateEmitter.Emission emission = emitter.stage(vertices(4), 4, STRIDE, GL11.GL_LINE_STRIP);
        assertEquals(6, emission.indexCount);
        int[] expected = {0, 1, 1, 2, 2, 3};
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], stagedIndex(ring, emission.indexOffset, i), "index " + i);
        }
    }

    @Test
    void lineLoopClosesTheLoop() {
        DirectStagingRing ring = new DirectStagingRing(1, 1 << 16);
        NonQuadImmediateEmitter emitter = new NonQuadImmediateEmitter(ring);
        NonQuadImmediateEmitter.Emission emission = emitter.stage(vertices(4), 4, STRIDE, GL11.GL_LINE_LOOP);
        assertEquals(8, emission.indexCount);
        int[] expected = {0, 1, 1, 2, 2, 3, 3, 0};
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], stagedIndex(ring, emission.indexOffset, i), "index " + i);
        }
    }

    @Test
    void quadStripWritesTheQuadPatternExpansion() {
        DirectStagingRing ring = new DirectStagingRing(1, 1 << 16);
        NonQuadImmediateEmitter emitter = new NonQuadImmediateEmitter(ring);
        NonQuadImmediateEmitter.Emission emission = emitter.stage(vertices(6), 6, STRIDE, GL11.GL_QUAD_STRIP);
        assertEquals(12, emission.indexCount);
        int[] expected = {0, 1, 3, 0, 3, 2, 2, 3, 5, 2, 5, 4};
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], stagedIndex(ring, emission.indexOffset, i), "index " + i);
        }
    }

    @Test
    void emissionsFollowEachOtherInTheRing() {
        DirectStagingRing ring = new DirectStagingRing(1, 1 << 16);
        NonQuadImmediateEmitter emitter = new NonQuadImmediateEmitter(ring);
        NonQuadImmediateEmitter.Emission first = emitter.stage(vertices(3), 3, STRIDE, GL11.GL_TRIANGLES);
        NonQuadImmediateEmitter.Emission second = emitter.stage(vertices(4), 4, STRIDE, GL11.GL_LINE_STRIP);
        assertEquals(0L, first.vertexOffset);
        // The second emission starts right behind the first's vertices, aligned to its stride.
        assertEquals(3L * STRIDE, second.vertexOffset);
        assertEquals(3L * STRIDE + 4L * STRIDE, second.indexOffset);
        assertVertexBytesCopied(ring, second.vertexOffset, 4);
    }

    @Test
    void aFullRingRefusesStagingWithoutEmitting() {
        DirectStagingRing ring = new DirectStagingRing(1, 64);
        NonQuadImmediateEmitter emitter = new NonQuadImmediateEmitter(ring);
        // 16 vertices at 8 bytes do not fit a 64-byte slot.
        assertNull(emitter.stage(vertices(16), 16, STRIDE, GL11.GL_TRIANGLES));
    }

    @Test
    void aRingThatFitsVerticesButNotIndicesRefusesStaging() {
        DirectStagingRing ring = new DirectStagingRing(1, 5 * STRIDE + 8);
        NonQuadImmediateEmitter emitter = new NonQuadImmediateEmitter(ring);
        // 5 strip vertices fit (40 bytes) but their 9 indices (36 bytes) do not.
        assertNull(emitter.stage(vertices(5), 5, STRIDE, GL11.GL_TRIANGLE_STRIP));
    }

    @Test
    void quadsAreRefused() {
        NonQuadImmediateEmitter emitter = new NonQuadImmediateEmitter(new DirectStagingRing(1, 1 << 16));
        assertThrows(IllegalArgumentException.class,
                () -> emitter.stage(vertices(4), 4, STRIDE, GL11.GL_QUADS));
    }

    @Test
    void modesWithoutAnExpansionAreRefused() {
        NonQuadImmediateEmitter emitter = new NonQuadImmediateEmitter(new DirectStagingRing(1, 1 << 16));
        assertThrows(IllegalArgumentException.class,
                () -> emitter.stage(vertices(4), 4, STRIDE, GL11.GL_POINTS));
        assertFalse(NonQuadImmediateEmitter.isNonQuadMode(GL11.GL_POINTS));
        assertFalse(NonQuadImmediateEmitter.isNonQuadMode(GL11.GL_QUADS));
        assertTrue(NonQuadImmediateEmitter.isNonQuadMode(GL11.GL_TRIANGLE_STRIP));
    }

    @Test
    void mismatchedVertexDataIsRefused() {
        NonQuadImmediateEmitter emitter = new NonQuadImmediateEmitter(new DirectStagingRing(1, 1 << 16));
        assertThrows(IllegalArgumentException.class,
                () -> emitter.stage(vertices(3), 4, STRIDE, GL11.GL_TRIANGLES));
    }
}
