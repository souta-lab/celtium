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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Scene-demand measurement for the terrain arenas (leg r8, 2026-11-25: pass-1
 * expected=82 resident=24 missing=58 ARENA_FULL, cumulative arenaFull=469 against
 * ~189 resident records). The raw caps are coherent by design — geometry
 * 32 slabs x 16 MiB and the factor arena (one 32 MiB arena, 4 bytes per quad)
 * bind at 8,388,608 quads, the total span unchanged from the 8 x 64 MiB form,
 * while the quad space (32 slabs x 1,048,576 quads) now has headroom above
 * that bound — so this suite measures where registration actually stops under
 * a scene-shaped load, per arena.
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
     * is 32 slabs x 256 such sections = 8,192 records. Registration must not stop at
     * one slab's worth (256) because the geometry and quad-space placements diverged.
     */
    @Test
    void smallSectionsFillAllSlabsNotJustTheFirst() {
        AsyncSectionMesher mesher = new AsyncSectionMesher();
        TerrainSectionStore store = new TerrainSectionStore();
        int registered = 0;
        TerrainSectionStore.RegisterResult failure = null;
        for (int i = 0; i < 600; i++) {
            var result = store.registerDiagnosed(built(mesher, i, 0, 0, i % 3, 1024), 1, 1);
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
        assertNull(failure, "600 small sections must fit the designed 32-slab capacity");
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
                var result = store.registerDiagnosed(built(mesher, i, pass, 0, pass, quads), 1, 1);
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
     * The BO2 scene's demand scale (window pack 2, 2026-11-26: logical live
     * 59,400,192 bytes at the terminal census) under the resized slabs
     * (16 MiB x 32, BO convergence landing A): a full generation registers,
     * retires with its frees still pending - the flight form, where the old
     * generation's ranges are timeline-held while the new one streams in - and
     * the replacement generation registers against that held space. No
     * registration may stop with ARENA_FULL, and the logical slab count stays
     * inside the landing prep's estimated band (12-20 for the real scene's
     * fragmentation; this synthetic stream packs tighter, so the pin is the
     * band's ceiling plus the subdivision itself, not an exact count).
     */
    @Test
    void bo2ScaleDemandRegistersWithoutArenaFull() {
        AsyncSectionMesher mesher = new AsyncSectionMesher();
        TerrainSectionStore store = new TerrainSectionStore();
        long target = 59_400_192L;
        int index = 0;
        // Generation 0: half the demand.
        while (store.geometryAllocator().liveBytes() < target / 2) {
            var result = store.registerDiagnosed(
                    built(mesher, index, 0, 0, index % 3, r8Quads(index, 0)), 1, 1);
            assertNotNull(result.registration(), "generation 0 section " + index + " refused: "
                    + result.failure());
            index++;
        }
        int generationZero = index;
        // Retire the whole generation; its frees pend at stamp 5, uncollected.
        for (int i = 0; i < generationZero; i++) {
            assertNotNull(store.unregister(new TerrainSectionStore.Key(i, 0, 0, i % 3)));
        }
        store.stampPendingRetirements(5);
        // Generation 1 registers against the still-held ranges, up to the target.
        while (store.geometryAllocator().liveBytes() < target) {
            var result = store.registerDiagnosed(
                    built(mesher, index, 0, 0, index % 3, r8Quads(index, 1)), 1, 1);
            assertNotNull(result.registration(), "generation 1 section " + index + " refused: "
                    + result.failure());
            index++;
        }
        store.collectReleased(5);
        // Top back up to the target now that the old ranges are free again.
        while (store.geometryAllocator().liveBytes() < target) {
            var result = store.registerDiagnosed(
                    built(mesher, index, 0, 0, index % 3, r8Quads(index, 2)), 6, 6);
            assertNotNull(result.registration(), "top-up section " + index + " refused: "
                    + result.failure());
            index++;
        }
        int slabs = store.geometryAllocator().slabCount();
        System.out.println("[arena-measure] bo2 scale registered=" + store.recordCount()
                + " geometryLiveBytes=" + store.geometryAllocator().liveBytes()
                + " slabs=" + slabs);
        assertTrue(slabs >= 2 && slabs <= 20,
                "the resized slabs subdivide the BO2-scale demand without nearing the cap: " + slabs);
    }

    /**
     * At the resized slab size the geometry arena binds first (prep risk R1):
     * an 8,192-quad section reserves 512 KiB of geometry but only 2 of a slab's
     * 256 quad pages, so a slab fills by geometry after 32 such sections while
     * its quad space is still nearly empty. 100 sections must therefore span
     * exactly 4 slabs, placed as pairs, with no refusal.
     */
    @Test
    void geometryBindsBeforeQuadSpaceAtTheResizedSlabSize() {
        AsyncSectionMesher mesher = new AsyncSectionMesher();
        TerrainSectionStore store = new TerrainSectionStore();
        for (int i = 0; i < 100; i++) {
            var result = store.registerDiagnosed(built(mesher, i, 0, 0, 0, 8192), 1, 1);
            assertNotNull(result.registration(), "section " + i + " refused: " + result.failure());
        }
        assertEquals(4, store.geometryAllocator().slabCount(),
                "100 x 512 KiB of geometry spans 4 slabs of 16 MiB");
        assertEquals(4, store.quadSpaceAllocator().slabCount(),
                "the quad space follows the geometry slab by slab (paired placement)");
    }

    private static int r8Quads(int i, int pass) {
        return 256 + ((i * 977 + pass * 331) % 7937);
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
            assertNotNull(store.register(built(mesher, i, 0, 0, 0, 1024), 1, 1));
        }
        for (int i = 0; i < 300; i += 2) {
            assertNotNull(store.unregister(new TerrainSectionStore.Key(i, 0, 0, 0)));
        }
        // The old form's "deferred at stamp 5, collected at 5" in the new form:
        // removal pends, the checkpoint stamps at 5, then collection at 5 bites.
        store.stampPendingRetirements(5);
        store.collectReleased(5);
        for (int i = 300; i < 450; i++) {
            var result = store.registerDiagnosed(built(mesher, i, 0, 0, 0, 1024), 6, 6);
            assertNotNull(result.registration(), "post-churn section " + i + " refused: " + result.failure());
        }
        assertEquals(300, store.recordCount());
    }
}
