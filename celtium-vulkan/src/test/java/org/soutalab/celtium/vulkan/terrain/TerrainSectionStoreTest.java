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
    void unregisterSwapsTheLastRecordIntoTheHole() {
        AsyncSectionMesher mesher = new AsyncSectionMesher();
        TerrainSectionStore store = new TerrainSectionStore();
        store.register(mesher.build(1, 0, 0, 0, parts(1, 0, true), 0), 1);
        store.register(mesher.build(2, 0, 0, 0, parts(1, 0, true), 0), 2);
        assertNotNull(store.unregister(new TerrainSectionStore.Key(1, 0, 0, 0), 5));
        assertEquals(1, store.recordCount());
        SectionDesc packed = SectionDesc.unpack(store.descImage(), 0);
        assertEquals(2, packed.sectionX, "the surviving record is packed at index 0");
        assertEquals(0, store.factorBaseImage()[0]);
    }

    @Test
    void duplicateRegistrationIsRefused() {
        AsyncSectionMesher mesher = new AsyncSectionMesher();
        TerrainSectionStore store = new TerrainSectionStore();
        assertNotNull(store.register(mesher.build(7, 7, 7, 0, parts(1, 0, true), 0), 1));
        assertThrows(IllegalStateException.class,
                () -> store.register(mesher.build(7, 7, 7, 0, parts(1, 0, true), 0), 2));
    }
}
