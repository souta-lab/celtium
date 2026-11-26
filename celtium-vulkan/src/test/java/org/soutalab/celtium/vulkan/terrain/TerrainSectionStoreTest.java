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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The resident store: mesher output becomes SectionDesc records whose packed image
 * and factor bases stay consistent across registration and swap-removal. Arena
 * placements are page-rounded (4096 units), which the offset assertions reflect.
 */
class TerrainSectionStoreTest {

    private static BuiltSectionMeshParts parts(int quadsPosX, int quadsUnassigned, boolean factors) {
        int quads = quadsPosX + quadsUnassigned;
        NativeBuffer vertices = new NativeBuffer(quads * 4 * TerrainVertex16.STRIDE);
        Map<ModelQuadFacing, VertexRange> ranges = new EnumMap<>(ModelQuadFacing.class);
        if (quadsPosX > 0) {
            ranges.put(ModelQuadFacing.POS_X, new VertexRange(0, quadsPosX * 4));
        }
        if (quadsUnassigned > 0) {
            ranges.put(ModelQuadFacing.UNASSIGNED,
                    new VertexRange(quadsPosX * 4, quadsUnassigned * 4));
        }
        int[] rdh = null;
        if (factors) {
            rdh = new int[quads];
            for (int i = 0; i < quads; i++) {
                rdh[i] = 100 + i;
            }
        }
        return new BuiltSectionMeshParts(vertices, null, null, ranges, rdh);
    }

    @Test
    void mesherRejectsNonTierABuffers() {
        AsyncSectionMesher mesher = new AsyncSectionMesher();
        NativeBuffer wrongStride = new NativeBuffer(24);
        try {
            Map<ModelQuadFacing, VertexRange> ranges = new EnumMap<>(ModelQuadFacing.class);
            ranges.put(ModelQuadFacing.POS_X, new VertexRange(0, 4));
            BuiltSectionMeshParts parts = new BuiltSectionMeshParts(wrongStride, null, null, ranges, null);
            assertThrows(IllegalArgumentException.class, () -> mesher.build(0, 0, 0, 0, parts, 0));
        } finally {
            wrongStride.free();
        }
    }

    @Test
    void registrationPacksDescsAndFactorBases() {
        AsyncSectionMesher mesher = new AsyncSectionMesher();
        TerrainSectionStore store = new TerrainSectionStore();
        var first = mesher.build(1, 2, 3, 0, parts(2, 1, true), 0);
        var second = mesher.build(4, 5, 6, 0, parts(1, 0, true), 0);
        assertNotNull(first);
        assertNotNull(second);
        var regFirst = store.register(first, 11);
        var regSecond = store.register(second, 12);
        assertNotNull(regFirst);
        assertNotNull(regSecond);
        assertEquals(2, store.recordCount());
        assertEquals(0, regFirst.geometry.byteOffset);
        assertEquals(4096, regSecond.geometry.byteOffset, "geometry placements are page-rounded");
        assertEquals(0, regFirst.desc.vertexOffset);
        assertEquals(4096 / 16, regSecond.desc.vertexOffset);
        assertEquals(11, regFirst.desc.generation);
        assertEquals(2, regFirst.desc.bucketCounts[0]);
        assertEquals(1, regFirst.desc.bucketCounts[6]);

        int[] image = store.descImage();
        SectionDesc packed = SectionDesc.unpack(image, 0);
        assertEquals(1, packed.sectionX);
        assertEquals(3, packed.totalQuads());
        assertEquals(0, regFirst.factorBaseUints());
        assertEquals(4096 / 4, regSecond.factorBaseUints(), "factor placements are page-rounded");
        int[] bases = store.factorBaseImage();
        assertEquals(0, bases[0]);
        assertEquals(4096 / 4, bases[1]);
    }

    @Test
    void updateSortedKeepsOrReplacesTheSortedRange() {
        AsyncSectionMesher mesher = new AsyncSectionMesher();
        TerrainSectionStore store = new TerrainSectionStore();
        // A sorted pass part: an index buffer beside the 16-byte vertices.
        int quads = 2;
        NativeBuffer vertices = new NativeBuffer(quads * 4 * TerrainVertex16.STRIDE);
        NativeBuffer indices = new NativeBuffer(quads * 6 * 4);
        Map<ModelQuadFacing, VertexRange> ranges = new EnumMap<>(ModelQuadFacing.class);
        ranges.put(ModelQuadFacing.POS_X, new VertexRange(0, quads * 4));
        BuiltSectionMeshParts sortedParts = new BuiltSectionMeshParts(vertices, indices, null, ranges, null);
        var built = mesher.build(9, 9, 9, 0, sortedParts, 0);
        assertNotNull(built);
        var registration = store.register(built, 21);
        assertNotNull(registration);
        assertNotNull(registration.sorted);
        TerrainSectionStore.Key key = new TerrainSectionStore.Key(9, 9, 9, 0);
        long generationBefore = store.imageGeneration();

        // Same size: the range is kept and the desc image does not move.
        assertEquals(TerrainSectionStore.SortedUpdate.IN_PLACE,
                store.updateSorted(key, indices.getLength(), 22));
        assertEquals(generationBefore, store.imageGeneration());

        // Different size: a new range is allocated and the desc follows it.
        assertEquals(TerrainSectionStore.SortedUpdate.RESIZED,
                store.updateSorted(key, indices.getLength() + 24, 23));
        assertEquals(indices.getLength() + 24, registration.sorted.byteSize);
        assertEquals(registration.sorted.byteOffset / 4, registration.desc.sortedIndexBase);
        assertTrue(store.imageGeneration() > generationBefore);

        assertEquals(TerrainSectionStore.SortedUpdate.NOT_REGISTERED,
                store.updateSorted(new TerrainSectionStore.Key(1, 1, 1, 0), 24, 24));
    }

    @Test
    void unregisterSwapsTheLastRecordIntoTheHole() {
        AsyncSectionMesher mesher = new AsyncSectionMesher();
        TerrainSectionStore store = new TerrainSectionStore();
        store.register(mesher.build(1, 0, 0, 0, parts(1, 0, true), 0), 1);
        store.register(mesher.build(2, 0, 0, 0, parts(1, 0, true), 0), 2);
        assertNotNull(store.unregister(new TerrainSectionStore.Key(1, 0, 0, 0), 5));
        assertEquals(1, store.recordCount());
        SectionDesc packed = SectionDesc.unpack(store.descImage(), 0);
        assertEquals(2, packed.sectionX, "the surviving record is packed at index 0");
        // Arena placements are stable across a swap-remove (nothing is copied); only
        // the image slot moves, so slot 0 now carries the survivor's own factor base.
        assertEquals(4096 / 4, store.factorBaseImage()[0]);
    }

    @Test
    void duplicateRegistrationIsRefused() {
        AsyncSectionMesher mesher = new AsyncSectionMesher();
        TerrainSectionStore store = new TerrainSectionStore();
        assertNotNull(store.register(mesher.build(7, 7, 7, 0, parts(1, 0, true), 0), 1));
        assertThrows(IllegalStateException.class,
                () -> store.register(mesher.build(7, 7, 7, 0, parts(1, 0, true), 0), 2));
    }

    private static BuiltSectionMeshParts partsForStride(int quadsPosX, int quadsUnassigned, int stride) {
        int quads = quadsPosX + quadsUnassigned;
        NativeBuffer vertices = new NativeBuffer(quads * 4 * stride);
        Map<ModelQuadFacing, VertexRange> ranges = new EnumMap<>(ModelQuadFacing.class);
        if (quadsPosX > 0) {
            ranges.put(ModelQuadFacing.POS_X, new VertexRange(0, quadsPosX * 4));
        }
        if (quadsUnassigned > 0) {
            ranges.put(ModelQuadFacing.UNASSIGNED,
                    new VertexRange(quadsPosX * 4, quadsUnassigned * 4));
        }
        return new BuiltSectionMeshParts(vertices, null, null, ranges, null);
    }

    @Test
    void mesherAssemblesAForeignStride() {
        AsyncSectionMesher mesher = new AsyncSectionMesher();
        var built = mesher.build(0, 0, 0, 1, partsForStride(2, 1, 44), 0, 44);
        assertNotNull(built);
        assertEquals(44, built.stride);
        assertEquals(3, built.totalQuads);
        assertEquals(3L * 4 * 44, built.slabBytes(),
                "a foreign stream reserves no factor area: vertices alone");
        assertEquals(2, built.descDraft.bucketCounts[0]);
        assertEquals(1, built.descDraft.bucketCounts[6]);
    }

    @Test
    void mesherRejectsBytesThatAreNotWholeForeignVertices() {
        AsyncSectionMesher mesher = new AsyncSectionMesher();
        NativeBuffer odd = new NativeBuffer(44 * 4 + 4);
        try {
            Map<ModelQuadFacing, VertexRange> ranges = new EnumMap<>(ModelQuadFacing.class);
            ranges.put(ModelQuadFacing.POS_X, new VertexRange(0, 4));
            BuiltSectionMeshParts parts = new BuiltSectionMeshParts(odd, null, null, ranges, null);
            assertThrows(IllegalArgumentException.class, () -> mesher.build(0, 0, 0, 0, parts, 0, 44));
        } finally {
            odd.free();
        }
    }

    @Test
    void packRegistrationLivesInItsOwnArenaWithoutAFactor() {
        AsyncSectionMesher mesher = new AsyncSectionMesher();
        TerrainSectionStore store = new TerrainSectionStore();
        var vanilla = store.register(mesher.build(1, 0, 0, 0, parts(1, 0, true), 0), 1);
        var packFirst = store.register(mesher.build(2, 0, 0, 1, partsForStride(3, 0, 44), 0, 44), 2);
        var packSecond = store.register(mesher.build(3, 0, 0, 1, partsForStride(1, 0, 44), 0, 44), 3);
        assertNotNull(vanilla);
        assertNotNull(packFirst);
        assertNotNull(packSecond);
        assertTrue(packFirst.pack());
        assertEquals(TerrainSectionStore.PACK_SLAB_BASE, packFirst.desc.slabId,
                "the first pack arena's first slab sits at the pack base");
        assertEquals(0, packFirst.packGeometry.byteOffset);
        assertEquals(0, packFirst.desc.vertexOffset);
        assertEquals(0, packFirst.desc.quadStart);
        assertEquals(44 * 256, packSecond.packGeometry.byteOffset,
                "pack placements are rounded to whole-vertex pages (stride * 256)");
        assertEquals(256, packSecond.desc.vertexOffset,
                "the desc vertex offset is exact at the presented stride");
        assertEquals(0, packFirst.factorBaseUints(), "no factor area is allocated for pack records");
        assertEquals(0, store.factorBaseImage()[packFirst.recordIndex]);

        assertEquals(1, store.slabRecordCounts()[0], "only the vanilla record counts for slab dispatch");
        assertEquals(2, store.packSlabRecordCounts()[TerrainSectionStore.PACK_SLAB_BASE]);
        assertEquals(2, store.packRecords(1).size());
        assertEquals(0, store.packRecords(0).size());
        assertEquals(44, store.packArenaOfSlab(TerrainSectionStore.PACK_SLAB_BASE).stride);
    }

    @Test
    void aSecondStrideOpensASecondPackArena() {
        AsyncSectionMesher mesher = new AsyncSectionMesher();
        TerrainSectionStore store = new TerrainSectionStore();
        var wide = store.register(mesher.build(0, 0, 0, 1, partsForStride(1, 0, 44), 0, 44), 1);
        var narrow = store.register(mesher.build(1, 0, 0, 1, partsForStride(1, 0, 28), 0, 28), 2);
        assertNotNull(wide);
        assertNotNull(narrow);
        assertEquals(TerrainSectionStore.PACK_SLAB_BASE, wide.desc.slabId);
        assertEquals(TerrainSectionStore.PACK_SLAB_BASE + 8, narrow.desc.slabId);
        assertEquals(28, store.packArenaOfSlab(TerrainSectionStore.PACK_SLAB_BASE + 8).stride);
    }

    @Test
    void packUnregisterReleasesBackIntoThePackArena() {
        AsyncSectionMesher mesher = new AsyncSectionMesher();
        TerrainSectionStore store = new TerrainSectionStore();
        assertNotNull(store.register(mesher.build(0, 0, 0, 1, partsForStride(2, 0, 44), 0, 44), 1));
        assertNotNull(store.unregister(new TerrainSectionStore.Key(0, 0, 0, 1), 5));
        store.collectReleased(5);
        var again = store.register(mesher.build(1, 0, 0, 1, partsForStride(1, 0, 44), 0, 44), 6);
        assertNotNull(again);
        assertEquals(0, again.packGeometry.byteOffset, "the released pack range is reused");
        assertEquals(1, store.recordCount());
    }
}
