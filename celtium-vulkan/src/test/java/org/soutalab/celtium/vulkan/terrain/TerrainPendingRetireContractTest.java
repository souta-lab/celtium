package org.soutalab.celtium.vulkan.terrain;

import org.embeddedt.embeddium.impl.common.util.NativeBuffer;
import org.embeddedt.embeddium.impl.render.chunk.data.VertexRange;
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
 * The pending-retirement ledger (T3 design section 2.3, slice S2): a removed
 * record's ranges are not released at removal time at all — they pend, stay
 * fully live under every collection horizon, and only the frame-boundary
 * checkpoint ({@code stampPendingRetirements} followed by
 * {@code collectReleased}) returns them, per ledger (INV-1a/1c). The
 * collect-before-fail retry of {@code allocateDiagnosed} (review M3) is the
 * one other collection point.
 */
class TerrainPendingRetireContractTest {

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

    @Test
    void vanillaRangesStayLiveUntilStampedThenCollected() {
        AsyncSectionMesher mesher = new AsyncSectionMesher();
        TerrainSectionStore store = new TerrainSectionStore();
        var registration = store.register(mesher.build(1, 0, 0, 0, parts(2, true), 0), 1, 1);
        assertNotNull(registration);
        long geometryLive = store.geometryAllocator().liveBytes();
        long quadLive = store.quadSpaceAllocator().liveBytes();
        long factorLive = store.factorAllocator().liveBytes();
        long sortedLive = store.sortedAllocator().liveBytes();
        assertTrue(geometryLive > 0 && quadLive > 0 && factorLive > 0 && sortedLive > 0,
                "the sorted vanilla record holds a range in every vanilla ledger");

        store.unregister(new TerrainSectionStore.Key(1, 0, 0, 0));
        assertEquals(4, store.pendingRetirementCount(),
                "geometry, quad space, factor and sorted each pend exactly one range");

        // No collection horizon reclaims a range that was never stamped.
        store.collectReleased(Long.MAX_VALUE);
        assertEquals(geometryLive, store.geometryAllocator().liveBytes());
        assertEquals(quadLive, store.quadSpaceAllocator().liveBytes());
        assertEquals(factorLive, store.factorAllocator().liveBytes());
        assertEquals(sortedLive, store.sortedAllocator().liveBytes());

        assertEquals(4, store.stampPendingRetirements(7));
        assertEquals(0, store.pendingRetirementCount());
        // Stamped is not collected: the ranges stay live until the horizon
        // reaches the stamp, and return exactly there.
        store.collectReleased(6);
        assertEquals(geometryLive, store.geometryAllocator().liveBytes());
        store.collectReleased(7);
        assertEquals(0, store.geometryAllocator().liveBytes());
        assertEquals(0, store.quadSpaceAllocator().liveBytes());
        assertEquals(0, store.factorAllocator().liveBytes());
        assertEquals(0, store.sortedAllocator().liveBytes());
    }

    @Test
    void packRangesStayLiveUntilStampedThenCollected() {
        AsyncSectionMesher mesher = new AsyncSectionMesher();
        TerrainSectionStore store = new TerrainSectionStore();
        var registration = store.register(mesher.build(2, 0, 0, 1, partsForStride(2, 44), 0, 44), 1, 1);
        assertNotNull(registration);
        TerrainSectionStore.PackArena arena = store.packArenaForStride(44);
        assertNotNull(arena);
        long geometryLive = arena.geometry.liveBytes();
        long quadLive = arena.quadSpace.liveBytes();
        assertTrue(geometryLive > 0 && quadLive > 0);

        store.unregister(new TerrainSectionStore.Key(2, 0, 0, 1));
        assertEquals(2, store.pendingRetirementCount(),
                "the pack record pends its geometry and quad-space ranges, in its own arena");

        store.collectReleased(Long.MAX_VALUE);
        assertEquals(geometryLive, arena.geometry.liveBytes());
        assertEquals(quadLive, arena.quadSpace.liveBytes());

        assertEquals(2, store.stampPendingRetirements(3));
        store.collectReleased(3);
        assertEquals(0, arena.geometry.liveBytes());
        assertEquals(0, arena.quadSpace.liveBytes());
        // The vanilla ledgers were never touched by the pack record's retirement.
        assertEquals(0, store.geometryAllocator().liveBytes());
        assertEquals(0, store.quadSpaceAllocator().liveBytes());
    }

    @Test
    void aRetiredRangeIsReusedOnlyAfterStampAndCollect() {
        AsyncSectionMesher mesher = new AsyncSectionMesher();
        TerrainSectionStore store = new TerrainSectionStore();
        var first = store.register(mesher.build(1, 0, 0, 0, parts(1, false), 0), 1, 1);
        var second = store.register(mesher.build(2, 0, 0, 0, parts(1, false), 0), 2, 2);
        assertNotNull(first);
        assertNotNull(second);
        long firstSlab = first.geometry.slabId;
        long firstOffset = first.geometry.byteOffset;

        store.unregister(new TerrainSectionStore.Key(1, 0, 0, 0));
        var third = store.register(mesher.build(3, 0, 0, 0, parts(1, false), 0), 3, 3);
        assertNotNull(third);
        assertTrue(third.geometry.slabId != firstSlab || third.geometry.byteOffset != firstOffset,
                "the retired range is still live, so the new record cannot land on it");

        store.stampPendingRetirements(5);
        store.collectReleased(5);
        var fourth = store.register(mesher.build(4, 0, 0, 0, parts(1, false), 0), 4, 4);
        assertNotNull(fourth);
        assertEquals(firstSlab, fourth.geometry.slabId);
        assertEquals(firstOffset, fourth.geometry.byteOffset,
                "after stamp and collect the range is the first fit again");
    }

    @Test
    void pendingCountTracksUnregisterResizedSortedAndStamp() {
        AsyncSectionMesher mesher = new AsyncSectionMesher();
        TerrainSectionStore store = new TerrainSectionStore();
        var registration = store.register(mesher.build(9, 9, 9, 0, parts(2, true), 0), 1, 1);
        assertNotNull(registration);
        TerrainSectionStore.Key key = new TerrainSectionStore.Key(9, 9, 9, 0);
        assertEquals(0, store.pendingRetirementCount());

        assertEquals(TerrainSectionStore.SortedUpdate.RESIZED, store.updateSorted(key, 2 * 6 * 4 + 24));
        assertEquals(1, store.pendingRetirementCount(), "the replaced sorted range pends");
        assertEquals(TerrainSectionStore.SortedUpdate.IN_PLACE, store.updateSorted(key, 2 * 6 * 4 + 24));
        assertEquals(1, store.pendingRetirementCount(), "an in-place update retires nothing");

        store.unregister(key);
        assertEquals(5, store.pendingRetirementCount(),
                "plus geometry, quad space, factor and the record's current sorted range");
        assertEquals(5, store.stampPendingRetirements(9));
        assertEquals(0, store.pendingRetirementCount());
        assertEquals(0, store.stampPendingRetirements(9), "an empty ledger stamps nothing");
    }

    @Test
    void collectBeforeFailRetriesOnceAtTheCompletedStamp() {
        AsyncSectionMesher mesher = new AsyncSectionMesher();
        TerrainSectionStore store = new TerrainSectionStore();
        // 32,768-quad sorted sections: the single 32 MiB sorted arena is the
        // binding constraint (768 KiB of indices per section), every other
        // arena keeps room to spare, so ARENA_FULL names the sorted arena.
        var victim = store.register(mesher.build(1000, 0, 0, 0, parts(32768, true), 0), 1, 0);
        assertNotNull(victim);
        TerrainSectionStore.RegisterResult failure = null;
        for (int i = 0; i < 100; i++) {
            var result = store.registerDiagnosed(mesher.build(i, 0, 0, 0, parts(32768, true), 0), 1, 0);
            if (result.registration() == null) {
                failure = result;
                break;
            }
        }
        assertNotNull(failure, "the sorted arena must fill");
        assertEquals(TerrainSectionStore.RegisterFailure.ARENA_FULL, failure.failure());

        // Retire the victim: its ranges pend, then stamp at 10 — deferred in
        // every ledger, collected nowhere.
        store.unregister(new TerrainSectionStore.Key(1000, 0, 0, 0));
        assertEquals(4, store.stampPendingRetirements(10));

        // A completed stamp below the release stamp cannot collect the
        // victim's ranges: the collect-before-fail retry still fails.
        var tooEarly = store.allocateDiagnosed(mesher.build(2000, 0, 0, 0, parts(32768, true), 0), 2, 5);
        assertNull(tooEarly.pending());
        assertEquals(TerrainSectionStore.RegisterFailure.ARENA_FULL, tooEarly.failure());

        // At the release stamp the retry collects the victim's ranges and lands.
        var retried = store.allocateDiagnosed(mesher.build(2000, 0, 0, 0, parts(32768, true), 0), 2, 10);
        assertNotNull(retried.pending(),
                "collect-before-fail must free the stamped ranges and retry once");
        store.publish(retried.pending());

        // Full again, with nothing left to collect at any horizon: the
        // failure stands after the one retry.
        var hopeless = store.allocateDiagnosed(
                mesher.build(2001, 0, 0, 0, parts(32768, true), 0), 3, Long.MAX_VALUE);
        assertNull(hopeless.pending());
        assertEquals(TerrainSectionStore.RegisterFailure.ARENA_FULL, hopeless.failure());
    }

    @Test
    void oversizeIsRefusedWithoutCollecting() {
        AsyncSectionMesher mesher = new AsyncSectionMesher();
        TerrainSectionStore store = new TerrainSectionStore();
        var registration = store.register(mesher.build(1, 0, 0, 0, parts(2, true), 0), 1, 0);
        assertNotNull(registration);
        store.unregister(new TerrainSectionStore.Key(1, 0, 0, 0));
        store.stampPendingRetirements(10);
        long geometryLive = store.geometryAllocator().liveBytes();
        assertTrue(geometryLive > 0, "the stamped range is deferred, not yet collected");

        var oversize = mesher.build(11, 0, 0, 0, parts(SectionDesc.MAX_BUCKET_QUADS + 1, false), 0);
        assertNotNull(oversize);
        var result = store.allocateDiagnosed(oversize, 2, Long.MAX_VALUE);
        assertNull(result.pending());
        assertEquals(TerrainSectionStore.RegisterFailure.OVERSIZE, result.failure());
        assertEquals(geometryLive, store.geometryAllocator().liveBytes(),
                "an oversize refusal never collects: collection belongs to the ARENA_FULL retry only");
    }
}
