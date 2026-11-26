package org.soutalab.celtium.vulkan.terrain;

import org.embeddedt.embeddium.impl.common.util.NativeBuffer;
import org.embeddedt.embeddium.impl.gl.util.VertexRange;
import org.embeddedt.embeddium.impl.model.quad.properties.ModelQuadFacing;
import org.embeddedt.embeddium.impl.render.chunk.data.BuiltSectionMeshParts;
import org.embeddedt.embeddium.impl.render.chunk.vertex.format.impl.TerrainVertex16;
import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The deferred registration queue's ownership discipline (T3 design
 * section 4.2): copy-on-defer means no entry ever references a caller
 * buffer, unregister invalidation and re-offer replacement keep the queue
 * free of dead sections, and a head whose key was registered meanwhile is
 * a counted stale-drop, never an exception.
 */
class DeferredSectionQueueTest {

    /** Parts whose vertex bytes are a seed pattern, with a factor stream and a sorted index buffer. */
    private static BuiltSectionMeshParts parts(int quads, int seed, boolean sorted) {
        NativeBuffer vertices = new NativeBuffer(quads * 4 * TerrainVertex16.STRIDE);
        ByteBuffer view = vertices.getDirectBuffer();
        for (int i = 0; i < view.capacity(); i++) {
            view.put(i, (byte) (seed + i));
        }
        NativeBuffer indices = null;
        if (sorted) {
            indices = new NativeBuffer(quads * 6 * 4);
            ByteBuffer indexView = indices.getDirectBuffer();
            for (int i = 0; i < indexView.capacity(); i++) {
                indexView.put(i, (byte) (seed * 3 + i));
            }
        }
        Map<ModelQuadFacing, VertexRange> ranges = new EnumMap<>(ModelQuadFacing.class);
        ranges.put(ModelQuadFacing.POS_X, new VertexRange(0, quads * 4));
        int[] rdh = new int[quads];
        for (int i = 0; i < quads; i++) {
            rdh[i] = seed * 1000 + i;
        }
        return new BuiltSectionMeshParts(vertices, indices, null, ranges, rdh);
    }

    private static void freeParts(BuiltSectionMeshParts parts) {
        parts.vertexBuffer().free();
        if (parts.indexBuffer() != null) {
            parts.indexBuffer().free();
        }
    }

    @Test
    void deferredPayloadIsIndependentOfTheCallersBuffers() {
        AsyncSectionMesher mesher = new AsyncSectionMesher();
        DeferredSectionQueue queue = new DeferredSectionQueue();
        BuiltSectionMeshParts source = parts(2, 5, true);
        var built = mesher.build(1, 2, 3, 0, source, 0);
        assertNotNull(built);
        long sourceAddress = MemoryUtil.memAddress(built.vertexData);
        int[] expectedFactors = built.rdhFactors.clone();
        byte[] expectedVertices = new byte[built.vertexBytes];
        built.vertexData.duplicate().get(expectedVertices);
        byte[] expectedIndices = new byte[built.sortedIndices.getLength()];
        built.sortedIndices.getDirectBuffer().duplicate().get(expectedIndices);

        DeferredSectionQueue.Entry entry = queue.offer(built, 42L);
        assertEquals(42L, entry.generation);
        assertNotEquals(sourceAddress, MemoryUtil.memAddress(entry.built().vertexData),
                "the queued payload is the queue's own allocation, not a view of the caller's");

        // The caller's unconditional free after the register call (the
        // embeddium uploadChunks shape), preceded by trashing the bytes.
        ByteBuffer trash = source.vertexBuffer().getDirectBuffer();
        for (int i = 0; i < trash.capacity(); i++) {
            trash.put(i, (byte) 0xEE);
        }
        built.rdhFactors[0] = -777;
        freeParts(source);

        ByteBuffer queuedVertices = entry.built().vertexData.duplicate();
        for (int i = 0; i < expectedVertices.length; i++) {
            assertEquals(expectedVertices[i], queuedVertices.get(i),
                    "queued vertex byte " + i + " survived the caller's free");
        }
        ByteBuffer queuedIndices = entry.built().sortedIndices.getDirectBuffer().duplicate();
        for (int i = 0; i < expectedIndices.length; i++) {
            assertEquals(expectedIndices[i], queuedIndices.get(i));
        }
        assertEquals(expectedFactors[0], entry.built().rdhFactors[0],
                "the factor stream was cloned at deferral, not shared");
        queue.clear();
    }

    @Test
    void offerReplacesTheQueuedBuildOfTheSameKey() {
        AsyncSectionMesher mesher = new AsyncSectionMesher();
        DeferredSectionQueue queue = new DeferredSectionQueue();
        BuiltSectionMeshParts first = parts(1, 10, false);
        BuiltSectionMeshParts second = parts(1, 60, false);
        queue.offer(mesher.build(4, 5, 6, 0, first, 0), 5L);
        DeferredSectionQueue.Entry entry = queue.offer(mesher.build(4, 5, 6, 0, second, 0), 7L);
        assertEquals(1, queue.size(), "the newest build wins: one entry per key");
        assertEquals(7L, entry.generation);
        assertEquals((byte) 60, entry.built().vertexData.get(0),
                "the queued bytes are the replacement build's");
        freeParts(first);
        freeParts(second);
        queue.clear();
    }

    @Test
    void cancelRemovesOnlyTheNamedKey() {
        AsyncSectionMesher mesher = new AsyncSectionMesher();
        DeferredSectionQueue queue = new DeferredSectionQueue();
        BuiltSectionMeshParts a = parts(1, 1, false);
        BuiltSectionMeshParts b = parts(1, 2, false);
        queue.offer(mesher.build(0, 0, 0, 0, a, 0), 1L);
        queue.offer(mesher.build(9, 0, 0, 0, b, 0), 1L);
        TerrainSectionStore.Key keyA = new TerrainSectionStore.Key(0, 0, 0, 0);
        TerrainSectionStore.Key keyB = new TerrainSectionStore.Key(9, 0, 0, 0);
        assertTrue(queue.cancel(keyA));
        assertFalse(queue.contains(keyA));
        assertTrue(queue.contains(keyB));
        assertEquals(1, queue.size());
        assertFalse(queue.cancel(keyA), "cancelling an absent key is a plain miss");
        freeParts(a);
        freeParts(b);
        queue.clear();
    }

    @Test
    void staleHeadIsDroppedAndCounted() {
        AsyncSectionMesher mesher = new AsyncSectionMesher();
        TerrainSectionStore store = new TerrainSectionStore();
        DeferredSectionQueue queue = new DeferredSectionQueue();
        BuiltSectionMeshParts registered = parts(1, 3, false);
        BuiltSectionMeshParts queued = parts(1, 4, false);
        BuiltSectionMeshParts other = parts(1, 5, false);
        var registration = store.register(mesher.build(7, 7, 7, 0, registered, 0), 1L);
        assertNotNull(registration);
        // The same key waits in the queue (an older build), behind an
        // unrelated live entry at the head.
        queue.offer(mesher.build(8, 8, 8, 0, other, 0), 1L);
        queue.offer(mesher.build(7, 7, 7, 0, queued, 0), 1L);

        assertFalse(queue.dropStaleHead(store), "the head is not stale: its key is unregistered");
        assertEquals(2, queue.size());
        DeferredSectionQueue.Entry drained = queue.pollFirst();
        assertNotNull(drained, "drain the live head the way prepareFrame would");
        drained.freeCopies();

        assertTrue(queue.dropStaleHead(store),
                "the new head's key is registered already: stale-drop, not an exception");
        assertEquals(0, queue.size());
        assertEquals(1L, queue.staleDropCount());
        assertFalse(queue.dropStaleHead(store), "an empty queue drops nothing");
        assertEquals(1L, queue.staleDropCount());
        freeParts(registered);
        freeParts(queued);
        freeParts(other);
        queue.clear();
    }

    @Test
    void clearFreesTheOwnedCopies() {
        AsyncSectionMesher mesher = new AsyncSectionMesher();
        DeferredSectionQueue queue = new DeferredSectionQueue();
        BuiltSectionMeshParts source = parts(2, 9, true);
        var built = mesher.build(3, 3, 3, 0, source, 0);
        assertNotNull(built);
        long baseline = NativeBuffer.getTotalAllocated();
        queue.offer(built, 1L);
        assertEquals(baseline + built.vertexBytes + built.sortedIndices.getLength(),
                NativeBuffer.getTotalAllocated(), "the deferral copies are queue-owned allocations");
        queue.clear();
        assertEquals(baseline, NativeBuffer.getTotalAllocated(),
                "clear returns the copies: nothing of the queue's stays allocated");
        assertNull(queue.peekFirst());
        freeParts(source);
    }
}
