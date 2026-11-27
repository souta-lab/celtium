package org.soutalab.celtium.vulkan.terrain;

import org.embeddedt.embeddium.impl.common.util.NativeBuffer;
import org.embeddedt.embeddium.impl.render.chunk.data.VertexRange;
import org.embeddedt.embeddium.impl.model.quad.properties.ModelQuadFacing;
import org.embeddedt.embeddium.impl.render.chunk.data.BuiltSectionMeshParts;
import org.embeddedt.embeddium.impl.render.chunk.vertex.format.impl.TerrainVertex16;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The store half of the paired reset (T3 design section 5.2, condition (4)):
 * {@link TerrainSectionStore#resetForNewEpoch()} ends the world epoch in the
 * store - records, unpublished allocations, pack arenas and the deferred
 * releases still queued against the old world's completion currency all
 * leave with it, because each ledger is replaced wholesale rather than
 * drained. The gate and budget resets that accompany it in the host's step 3
 * are pinned here too: they are the same "back to the just-constructed
 * state" contract, one per accompanying object.
 */
class TerrainWorldEpochResetTest {

    private static BuiltSectionMeshParts parts(int quads, boolean sorted) {
        NativeBuffer vertices = new NativeBuffer(quads * 4 * TerrainVertex16.STRIDE);
        NativeBuffer indices = sorted ? new NativeBuffer(quads * 6 * 4) : null;
        Map<ModelQuadFacing, VertexRange> ranges = new EnumMap<>(ModelQuadFacing.class);
        ranges.put(ModelQuadFacing.POS_X, new VertexRange(0, quads * 4));
        return new BuiltSectionMeshParts(vertices, indices, null, ranges, null);
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

    private static void freeParts(BuiltSectionMeshParts parts) {
        parts.vertexBuffer().free();
        if (parts.indexBuffer() != null) {
            parts.indexBuffer().free();
        }
    }

    private static int packArenaCount(TerrainSectionStore store) throws Exception {
        Field field = TerrainSectionStore.class.getDeclaredField("packArenas");
        field.setAccessible(true);
        return ((List<?>) field.get(store)).size();
    }

    @Test
    void resetClearsRecordsAndReplacesEveryLedger() {
        AsyncSectionMesher mesher = new AsyncSectionMesher();
        TerrainSectionStore store = new TerrainSectionStore();
        BuiltSectionMeshParts source = parts(2, true);
        assertNotNull(store.register(mesher.build(1, 2, 3, 0, source, 0), 7L, 7L));
        assertEquals(1, store.recordCount());
        assertTrue(store.geometryAllocator().liveBytes() > 0,
                "the registration really reserved ranges before the reset");
        TerrainSlabAllocator geometryBefore = store.geometryAllocator();
        TerrainSlabAllocator quadSpaceBefore = store.quadSpaceAllocator();
        TerrainSlabAllocator factorBefore = store.factorAllocator();
        TerrainSlabAllocator sortedBefore = store.sortedAllocator();

        store.resetForNewEpoch();

        assertEquals(0, store.recordCount(), "no record crosses the epoch boundary");
        assertEquals(0, store.geometryAllocator().liveBytes());
        assertEquals(0, store.quadSpaceAllocator().liveBytes());
        assertEquals(0, store.factorAllocator().liveBytes());
        assertEquals(0, store.sortedAllocator().liveBytes());
        assertNotSame(geometryBefore, store.geometryAllocator(),
                "the ledger is replaced, not drained: its deferred releases die with it");
        assertNotSame(quadSpaceBefore, store.quadSpaceAllocator());
        assertNotSame(factorBefore, store.factorAllocator());
        assertNotSame(sortedBefore, store.sortedAllocator());
        assertEquals(0, store.descImage().length, "the desc image returns to its initial empty state");
        assertEquals(0, store.factorBaseImage().length);
        assertEquals(0, store.imageGeneration(), "the image generation restarts with the epoch");

        // The store serves the new epoch immediately, the same key included.
        BuiltSectionMeshParts again = parts(2, true);
        assertNotNull(store.register(mesher.build(1, 2, 3, 0, again, 0), 8L, 8L));
        assertEquals(1, store.recordCount());
        freeParts(source);
        freeParts(again);
    }

    @Test
    void resetDropsPackArenasAndDeferredReleases() throws Exception {
        AsyncSectionMesher mesher = new AsyncSectionMesher();
        TerrainSectionStore store = new TerrainSectionStore();
        BuiltSectionMeshParts packSource = partsForStride(3, 0, 44);
        assertNotNull(store.register(mesher.build(2, 0, 0, 1, packSource, 0, 44), 2L, 2L));
        assertEquals(1, packArenaCount(store), "the foreign stride created its pack arena");

        // A vanilla section leaves onto the pending-retire ledger (slice
        // S2): unregistered but never stamped at a checkpoint, its ranges
        // stay live until a collection that cannot happen in this epoch.
        TerrainSectionStore.Key key = new TerrainSectionStore.Key(5, 5, 5, 0);
        BuiltSectionMeshParts source = parts(2, false);
        assertNotNull(store.register(mesher.build(5, 5, 5, 0, source, 0), 3L, 3L));
        store.unregister(key);
        assertEquals(1, store.recordCount(), "the pack record is still resident");
        assertTrue(store.pendingRetirementCount() > 0,
                "the retirement waits on the pending ledger, unstamped");
        assertTrue(store.geometryAllocator().liveBytes() > 0,
                "the deferred release is outstanding: the old ledger still holds the ranges");

        store.resetForNewEpoch();

        assertEquals(0, store.pendingRetirementCount(),
                "no pending retirement crosses the epoch boundary (INV-4b)");

        assertEquals(0, store.recordCount());
        assertEquals(0, packArenaCount(store), "the pack arenas leave with the epoch");
        assertEquals(0, store.geometryAllocator().liveBytes(),
                "the deferred release is not collected into the new epoch - it is gone with its ledger");
        assertEquals(0, store.quadSpaceAllocator().liveBytes());
        assertEquals(0, store.factorAllocator().liveBytes());
        assertEquals(0, store.sortedAllocator().liveBytes());
        freeParts(packSource);
        freeParts(source);
    }

    @Test
    void resetClearsUnpublishedAllocations() {
        AsyncSectionMesher mesher = new AsyncSectionMesher();
        TerrainSectionStore store = new TerrainSectionStore();
        BuiltSectionMeshParts source = parts(2, false);
        var allocated = store.allocateDiagnosed(mesher.build(9, 9, 9, 0, source, 0), 3L, 3L);
        assertNotNull(allocated.pending(), "the key is held by an unpublished allocation");
        assertNull(allocated.failure());

        store.resetForNewEpoch();

        // The same key allocates cleanly: the pending hold did not cross.
        BuiltSectionMeshParts retry = parts(2, false);
        var again = store.allocateDiagnosed(mesher.build(9, 9, 9, 0, retry, 0), 4L, 4L);
        assertNotNull(again.pending());
        assertNull(again.failure());
        assertNotNull(store.publish(again.pending()));
        assertEquals(1, store.recordCount());
        freeParts(source);
        freeParts(retry);
    }

    @Test
    void gateAndBudgetResetsRestoreTheInitialState() {
        // The residency gate: an expectation and a miss of the old world must
        // not leak into the next world's verification.
        TerrainResidencyGate gate = new TerrainResidencyGate();
        TerrainSectionStore store = new TerrainSectionStore();
        TerrainSectionStore.Key key = new TerrainSectionStore.Key(1, 2, 3, 0);
        gate.expect(key);
        gate.noteMissing(key, TerrainResidencyGate.Reason.ARENA_FULL);
        assertFalse(gate.verify(0, store).complete(), "the section is missing in this epoch");
        gate.reset();
        assertTrue(gate.verify(0, store).complete(),
                "after the reset nothing is expected, so nothing can be missing");

        // The upload budget: admitted bytes and the deferred backlog alike
        // return to the just-constructed state.
        TerrainUploadBudget budget = new TerrainUploadBudget(1024);
        assertTrue(budget.tryAdmit(512));
        assertFalse(budget.tryAdmit(4096), "the oversize upload goes to the backlog");
        assertEquals(1, budget.pendingUploads());
        budget.reset();
        assertEquals(1024, budget.remainingThisFrame());
        assertEquals(0, budget.admittedThisFrame());
        assertEquals(0, budget.pendingBytes());
        assertEquals(0, budget.pendingUploads());
    }
}
