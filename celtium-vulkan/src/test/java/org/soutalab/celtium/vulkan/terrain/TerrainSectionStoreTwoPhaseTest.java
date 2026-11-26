package org.soutalab.celtium.vulkan.terrain;

import org.embeddedt.embeddium.impl.common.util.NativeBuffer;
import org.embeddedt.embeddium.impl.gl.util.VertexRange;
import org.embeddedt.embeddium.impl.model.quad.properties.ModelQuadFacing;
import org.embeddedt.embeddium.impl.render.chunk.data.BuiltSectionMeshParts;
import org.embeddedt.embeddium.impl.render.chunk.vertex.format.impl.TerrainVertex16;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The two-phase registration (T3 design section 4.2): allocation reserves
 * ranges without making the record visible, publish is the single point
 * where a fully written record enters the image, and discarding an
 * unpublished record returns its ranges immediately — the rollback the
 * engines rely on when a byte write or a slab buffer creation fails
 * between the phases.
 */
class TerrainSectionStoreTwoPhaseTest {

    private static BuiltSectionMeshParts parts(int quads, boolean sorted) {
        NativeBuffer vertices = new NativeBuffer(quads * 4 * TerrainVertex16.STRIDE);
        NativeBuffer indices = sorted ? new NativeBuffer(quads * 6 * 4) : null;
        Map<ModelQuadFacing, VertexRange> ranges = new EnumMap<>(ModelQuadFacing.class);
        ranges.put(ModelQuadFacing.POS_X, new VertexRange(0, quads * 4));
        return new BuiltSectionMeshParts(vertices, indices, null, ranges, null);
    }

    private static BuiltSectionMeshParts partsForStride(int quads, int stride) {
        NativeBuffer vertices = new NativeBuffer(quads * 4 * stride);
        Map<ModelQuadFacing, VertexRange> ranges = new EnumMap<>(ModelQuadFacing.class);
        ranges.put(ModelQuadFacing.POS_X, new VertexRange(0, quads * 4));
        return new BuiltSectionMeshParts(vertices, null, null, ranges, null);
    }

    private static void freeParts(BuiltSectionMeshParts parts) {
        parts.vertexBuffer().free();
        if (parts.indexBuffer() != null) {
            parts.indexBuffer().free();
        }
    }

    @Test
    void allocationIsInvisibleUntilPublish() {
        AsyncSectionMesher mesher = new AsyncSectionMesher();
        TerrainSectionStore store = new TerrainSectionStore();
        BuiltSectionMeshParts source = parts(2, false);
        var built = mesher.build(1, 2, 3, 0, source, 0);
        TerrainSectionStore.Key key = new TerrainSectionStore.Key(1, 2, 3, 0);
        long generationBefore = store.imageGeneration();

        var allocated = store.allocateDiagnosed(built, 11L);
        assertNotNull(allocated.pending());
        assertNull(allocated.failure());
        assertNull(store.get(key), "an allocated record is not yet in the store");
        assertEquals(0, store.recordCount());
        assertEquals(generationBefore, store.imageGeneration(),
                "the image does not move before publish: no snapshot can name the record");
        assertEquals(0, store.descImage().length);

        var registration = store.publish(allocated.pending());
        assertEquals(0, registration.recordIndex, "the record index is assigned at publish");
        assertNotNull(store.get(key));
        assertEquals(1, store.recordCount());
        assertEquals(generationBefore + 1, store.imageGeneration());
        assertEquals(SectionDesc.WORDS, store.descImage().length);
        freeParts(source);
    }

    @Test
    void discardReturnsEveryRangeImmediately() {
        AsyncSectionMesher mesher = new AsyncSectionMesher();
        TerrainSectionStore store = new TerrainSectionStore();
        long geometryBefore = store.geometryAllocator().liveBytes();
        long quadBefore = store.quadSpaceAllocator().liveBytes();
        long factorBefore = store.factorAllocator().liveBytes();
        long sortedBefore = store.sortedAllocator().liveBytes();

        BuiltSectionMeshParts source = parts(2, true);
        var built = mesher.build(5, 5, 5, 0, source, 0);
        var allocated = store.allocateDiagnosed(built, 3L);
        assertNotNull(allocated.pending());
        assertTrue(store.geometryAllocator().liveBytes() > geometryBefore,
                "the allocation really reserved ranges before the discard");

        store.discardUnpublished(allocated.pending());
        assertEquals(geometryBefore, store.geometryAllocator().liveBytes(),
                "geometry returns immediately, not on the deferred currency");
        assertEquals(quadBefore, store.quadSpaceAllocator().liveBytes());
        assertEquals(factorBefore, store.factorAllocator().liveBytes());
        assertEquals(sortedBefore, store.sortedAllocator().liveBytes());
        assertEquals(0, store.recordCount());

        // The key is free again: the rollback stranded neither the key nor
        // the ranges, so the same section registers whole afterwards.
        BuiltSectionMeshParts retry = parts(2, true);
        var registration = store.register(mesher.build(5, 5, 5, 0, retry, 0), 4L);
        assertNotNull(registration);
        freeParts(source);
        freeParts(retry);
    }

    @Test
    void discardReturnsPackRangesImmediately() {
        AsyncSectionMesher mesher = new AsyncSectionMesher();
        TerrainSectionStore store = new TerrainSectionStore();
        BuiltSectionMeshParts source = partsForStride(2, 44);
        var built = mesher.build(6, 6, 6, 1, source, 0, 44);
        var allocated = store.allocateDiagnosed(built, 3L);
        assertNotNull(allocated.pending());
        var arena = store.packArenaForStride(44);
        assertNotNull(arena);
        assertTrue(arena.geometry.liveBytes() > 0);

        store.discardUnpublished(allocated.pending());
        assertEquals(0, arena.geometry.liveBytes());
        assertEquals(0, arena.quadSpace.liveBytes());
        assertEquals(0, store.recordCount());
        freeParts(source);
    }

    @Test
    void aPendingKeyCannotBeAllocatedOrRegisteredTwice() {
        AsyncSectionMesher mesher = new AsyncSectionMesher();
        TerrainSectionStore store = new TerrainSectionStore();
        BuiltSectionMeshParts first = parts(1, false);
        BuiltSectionMeshParts second = parts(1, false);
        var allocated = store.allocateDiagnosed(mesher.build(2, 2, 2, 0, first, 0), 1L);
        assertNotNull(allocated.pending());
        var dupe = mesher.build(2, 2, 2, 0, second, 0);
        assertThrows(IllegalStateException.class, () -> store.allocateDiagnosed(dupe, 2L),
                "a second allocation of an unpublished key would double-book its ranges");
        assertThrows(IllegalStateException.class, () -> store.registerDiagnosed(dupe, 2L));
        store.discardUnpublished(allocated.pending());
        assertNotNull(store.register(dupe, 3L), "after the discard the key registers normally");
        freeParts(first);
        freeParts(second);
    }

    @Test
    void settlingTwiceIsRefused() {
        AsyncSectionMesher mesher = new AsyncSectionMesher();
        TerrainSectionStore store = new TerrainSectionStore();
        BuiltSectionMeshParts source = parts(1, false);
        var allocated = store.allocateDiagnosed(mesher.build(3, 3, 3, 0, source, 0), 1L);
        assertNotNull(allocated.pending());
        store.publish(allocated.pending());
        assertThrows(IllegalStateException.class, () -> store.publish(allocated.pending()));
        assertThrows(IllegalStateException.class, () -> store.discardUnpublished(allocated.pending()),
                "a published record leaves through unregister, never through the unpublished discard");
        assertEquals(1, store.recordCount());
        freeParts(source);
    }
}
