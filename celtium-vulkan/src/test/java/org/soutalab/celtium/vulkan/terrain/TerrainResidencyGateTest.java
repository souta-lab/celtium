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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The residency gate (order (i)): leak events are counted by reason, and the
 * per-pass verification cross-checks the expected set against the store —
 * the predicate the VulkanArena deletion will stand on.
 */
class TerrainResidencyGateTest {

    private static TerrainSectionStore.Key key(int x, int pass) {
        return new TerrainSectionStore.Key(x, 0, 0, pass);
    }

    private static BuiltSectionMeshParts parts(int quadsPosX) {
        NativeBuffer vertices = new NativeBuffer(quadsPosX * 4 * TerrainVertex16.STRIDE);
        Map<ModelQuadFacing, VertexRange> ranges = new EnumMap<>(ModelQuadFacing.class);
        ranges.put(ModelQuadFacing.POS_X, new VertexRange(0, quadsPosX * 4));
        return new BuiltSectionMeshParts(vertices, null, null, ranges, null);
    }

    @Test
    void leakReasonsAreCountedAndAttributedPerPass() {
        TerrainResidencyGate gate = new TerrainResidencyGate();
        TerrainSectionStore store = new TerrainSectionStore();
        gate.noteMissing(key(1, 0), TerrainResidencyGate.Reason.OVERSIZE);
        gate.noteMissing(key(2, 0), TerrainResidencyGate.Reason.ARENA_FULL);
        gate.noteMissing(key(3, 0), TerrainResidencyGate.Reason.DEFERRED);
        gate.noteMissing(key(4, 1), TerrainResidencyGate.Reason.STRIDE_GATE);

        assertEquals(1, gate.count(TerrainResidencyGate.Reason.OVERSIZE));
        assertEquals(1, gate.count(TerrainResidencyGate.Reason.ARENA_FULL));
        assertEquals(1, gate.count(TerrainResidencyGate.Reason.DEFERRED));
        assertEquals(1, gate.count(TerrainResidencyGate.Reason.STRIDE_GATE));
        assertEquals(0, gate.count(TerrainResidencyGate.Reason.BUILD_EXCEPTION));

        TerrainResidencyGate.Report pass0 = gate.verify(0, store);
        assertFalse(pass0.complete());
        assertEquals(3, pass0.expected());
        assertEquals(0, pass0.resident());
        assertEquals(3, pass0.missing());
        assertEquals(1, pass0.missingByReason().get(TerrainResidencyGate.Reason.OVERSIZE));
        assertEquals(1, pass0.missingByReason().get(TerrainResidencyGate.Reason.ARENA_FULL));
        // The pass-1 leak does not leak into pass 0's verdict.
        assertEquals(1, gate.verify(1, store).missing());
    }

    @Test
    void aRegisteredSectionVerifiesResidentAndCompletesThePass() {
        TerrainResidencyGate gate = new TerrainResidencyGate();
        TerrainSectionStore store = new TerrainSectionStore();
        AsyncSectionMesher mesher = new AsyncSectionMesher();
        var built = mesher.build(7, 0, 0, 0, parts(1), 0);
        assertNotNull(built);
        gate.expect(key(7, 0));
        gate.noteMissing(key(7, 0), TerrainResidencyGate.Reason.DEFERRED);
        assertNotNull(store.register(built, 1));
        gate.noteResident(key(7, 0));

        TerrainResidencyGate.Report report = gate.verify(0, store);
        assertTrue(report.complete(), TerrainResidencyGate.describe(report));
        assertEquals(1, report.resident());
        // The deferred event stays in the cumulative counter even after residency.
        assertEquals(1, gate.count(TerrainResidencyGate.Reason.DEFERRED));
    }

    @Test
    void headOfLineIsCountedOnceAndForgetClearsEverything() {
        TerrainResidencyGate gate = new TerrainResidencyGate();
        TerrainSectionStore store = new TerrainSectionStore();
        gate.noteMissing(key(9, 0), TerrainResidencyGate.Reason.DEFERRED);
        gate.noteHeadOfLine(key(9, 0));
        gate.noteHeadOfLine(key(9, 0));
        assertEquals(1, gate.headOfLineBlocked());
        assertEquals(1, gate.verify(0, store).headOfLineBlocked());

        gate.forget(key(9, 0));
        assertEquals(0, gate.headOfLineBlocked());
        assertTrue(gate.verify(0, store).complete());
        assertEquals(0, gate.verify(0, store).expected());
    }

    @Test
    void storeDiagnosesOversizeSeparatelyFromArenaFull() {
        TerrainSectionStore store = new TerrainSectionStore();
        AsyncSectionMesher mesher = new AsyncSectionMesher();
        // One bucket past the uint16 SectionDesc cap is oversize, not arena-full.
        var oversize = mesher.build(11, 0, 0, 0, parts(SectionDesc.MAX_BUCKET_QUADS + 1), 0);
        assertNotNull(oversize);
        TerrainSectionStore.RegisterResult result = store.registerDiagnosed(oversize, 1);
        assertEquals(TerrainSectionStore.RegisterFailure.OVERSIZE, result.failure());
        assertNull(result.registration());
    }
}
