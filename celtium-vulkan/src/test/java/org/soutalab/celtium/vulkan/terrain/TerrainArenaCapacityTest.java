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

/**
 * Scene-demand measurement for the terrain arenas (leg r8, 2026-11-25: pass-1
 * expected=82 resident=24 missing=58 ARENA_FULL, cumulative arenaFull=469 against
 * ~189 resident records). The raw caps are coherent by design — geometry
 * 8 slabs x 64 MiB, quad space 8 slabs x 1,048,576 quads, factor one 32 MiB arena
 * (4 bytes per quad) all bound at 8,388,608 quads — so this suite measures where
 * registration actually stops under a scene-shaped load, per arena.
 */
class TerrainArenaCapacityTest {

    private static AsyncSectionMesher.BuiltSectionA built(AsyncSectionMesher mesher,
                                                          int x, int y, int z, int pass, int quads) {
        NativeBuffer vertices = new NativeBuffer(quads * 4 * TerrainVertex16.STRIDE);
        Map<ModelQuadFacing, VertexRange> ranges = new EnumMap<>(ModelQuadFacing.class);
        ranges.put(ModelQuadFacing.POS_X, new VertexRange(0, quads * 4));
        BuiltSectionMeshParts parts = new BuiltSectionMeshParts(vertices, null, null, ranges, null);
        return mesher.build(x, y, z, pass, parts, 0);
    }

    /**
     * Small sections (1,024 quads, one quad-space page each): the designed capacity
     * is 8 slabs x 256 such sections = 2,048 records. Registration must not stop at
     * one slab's worth (256) because the geometry and quad-space placements diverged.
     */
    @Test
    void smallSectionsFillAllSlabsNotJustTheFirst() {
        AsyncSectionMesher mesher = new AsyncSectionMesher();
        TerrainSectionStore store = new TerrainSectionStore();
        int registered = 0;
        TerrainSectionStore.RegisterResult failure = null;
        for (int i = 0; i < 600; i++) {
            var result = store.registerDiagnosed(built(mesher, i, 0, 0, i % 3, 1024), 1);
            if (result.registration() == null) {
                failure = result;
                break;
            }
            registered++;
        }
        System.out.println("[arena-measure] small sections registered=" + registered
                + " failure=" + (failure == null ? "none" : failure.failure())
                + " geometryLiveBytes=" + store.geometryAllocator().liveBytes()
                + " slabs=" + store.geometryAllocator().slabCount());
        assertNull(failure, "600 small sections must fit the designed 8-slab capacity");
        assertEquals(600, registered);
        assertEquals(600, store.recordCount());
    }

    /**
     * The r8 scene shape: 3 passes x 82 expected sections with a spread of quad
     * counts (256..8,192, deterministic). Total demand at this shape is
     * 246 sections / ~1.04M quads / ~67 MB of geometry — far below every raw cap —
     * so every section must register.
     */
    @Test
    void r8SceneShapeFitsEntirely() {
        AsyncSectionMesher mesher = new AsyncSectionMesher();
        TerrainSectionStore store = new TerrainSectionStore();
        long totalQuads = 0;
        int registered = 0;
        for (int pass = 0; pass < 3; pass++) {
            for (int i = 0; i < 82; i++) {
                int quads = 256 + ((i * 977 + pass * 331) % 7937);
                totalQuads += quads;
                var result = store.registerDiagnosed(built(mesher, i, pass, 0, pass, quads), 1);
                assertNotNull(result.registration(),
                        "section " + i + " of pass " + pass + " (" + quads + " quads) refused: "
                                + result.failure());
                registered++;
            }
        }
        System.out.println("[arena-measure] r8 shape registered=" + registered
                + " totalQuads=" + totalQuads
                + " geometryLiveBytes=" + store.geometryAllocator().liveBytes()
                + " slabs=" + store.geometryAllocator().slabCount());
        assertEquals(246, registered);
    }

    /**
     * Churn, as in flight: half the resident set is unregistered, its deferred frees
     * are collected, and a fresh half registers in its place. Placement must keep
     * working across the turnover instead of stranding free space in the wrong slab.
     */
    @Test
    void churnKeepsPlacementWorking() {
        AsyncSectionMesher mesher = new AsyncSectionMesher();
        TerrainSectionStore store = new TerrainSectionStore();
        for (int i = 0; i < 300; i++) {
            assertNotNull(store.register(built(mesher, i, 0, 0, 0, 1024), 1));
        }
        for (int i = 0; i < 300; i += 2) {
            assertNotNull(store.unregister(new TerrainSectionStore.Key(i, 0, 0, 0), 5));
        }
        store.collectReleased(5);
        for (int i = 300; i < 450; i++) {
            var result = store.registerDiagnosed(built(mesher, i, 0, 0, 0, 1024), 6);
            assertNotNull(result.registration(), "post-churn section " + i + " refused: " + result.failure());
        }
        assertEquals(300, store.recordCount());
    }
}
