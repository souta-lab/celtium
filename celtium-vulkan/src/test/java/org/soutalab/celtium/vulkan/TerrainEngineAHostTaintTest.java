package org.soutalab.celtium.vulkan;

import org.embeddedt.embeddium.impl.render.chunk.compile.sorting.QuadPrimitiveType;
import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;
import org.embeddedt.embeddium.impl.render.chunk.vertex.format.ChunkMeshFormats;
import org.junit.jupiter.api.Test;
import org.soutalab.celtium.vulkan.terrain.TerrainResidencyGate;
import org.soutalab.celtium.vulkan.terrain.TerrainSectionStore;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R-55, Step B: the per-pass taint lifecycle at host level. A rejection taints
 * the pass; only a complete residency verification — the same report
 * {@code prepareFrame} produces every frame — clears it. The host is pure CPU
 * state, so the lifecycle is exercised directly, without a device.
 */
class TerrainEngineAHostTaintTest {

    private static TerrainRenderPass pass(String name) {
        return TerrainRenderPass.builder()
                .name(name)
                .vertexType(ChunkMeshFormats.TERRAIN_A16)
                .primitiveType(QuadPrimitiveType.TRIANGULATED)
                .build();
    }

    @Test
    void aPassNeverOfferedASectionIsNotTainted() {
        TerrainEngineAHost host = new TerrainEngineAHost();
        assertFalse(host.isPassTainted(pass("fresh")),
                "a pass with no ordinal yet was never offered a section, so it is clean");
    }

    @Test
    void taintIsPerPass() {
        TerrainEngineAHost host = new TerrainEngineAHost();
        TerrainRenderPass tainted = pass("tainted");
        TerrainRenderPass other = pass("other");
        host.ordinalFor(other);
        host.taintPass(tainted);
        assertTrue(host.isPassTainted(tainted), "the rejected section's pass is tainted");
        assertFalse(host.isPassTainted(other), "another pass is untouched");
    }

    @Test
    void anIncompleteReportKeepsTheTaint() {
        TerrainEngineAHost host = new TerrainEngineAHost();
        TerrainRenderPass pass = pass("incomplete");
        host.taintPass(pass);
        int ordinal = host.ordinalFor(pass);
        // The rejected section is expected but not resident: the report is incomplete.
        host.residencyGate().noteMissing(new TerrainSectionStore.Key(1, 2, 3, ordinal),
                TerrainResidencyGate.Reason.ARENA_FULL);
        TerrainResidencyGate.Report report = host.residencyGate().verify(ordinal, host.store());
        assertFalse(report.complete(), "the section is still missing, so the report is incomplete");
        host.noteResidencyReport(report);
        assertTrue(host.isPassTainted(pass), "an incomplete report clears nothing");
    }

    @Test
    void aCompleteReportClearsTheTaint() {
        TerrainEngineAHost host = new TerrainEngineAHost();
        TerrainRenderPass pass = pass("cleared");
        host.taintPass(pass);
        int ordinal = host.ordinalFor(pass);
        TerrainSectionStore.Key key = new TerrainSectionStore.Key(4, 5, 6, ordinal);
        host.residencyGate().noteMissing(key, TerrainResidencyGate.Reason.OVERSIZE);
        // The section leaves the expected set (rebuilt or unloaded): nothing is missing any more.
        host.residencyGate().forget(key);
        TerrainResidencyGate.Report report = host.residencyGate().verify(ordinal, host.store());
        assertTrue(report.complete(), "no expected section is missing, so the report is complete");
        host.noteResidencyReport(report);
        assertFalse(host.isPassTainted(pass), "a complete report clears the taint");
    }

    @Test
    void aCompleteReportForAnotherPassKeepsTheTaint() {
        TerrainEngineAHost host = new TerrainEngineAHost();
        TerrainRenderPass tainted = pass("still-tainted");
        TerrainRenderPass clean = pass("clean");
        host.taintPass(tainted);
        int cleanOrdinal = host.ordinalFor(clean);
        TerrainResidencyGate.Report report = host.residencyGate().verify(cleanOrdinal, host.store());
        assertTrue(report.complete(), "the clean pass has no expected sections");
        host.noteResidencyReport(report);
        assertTrue(host.isPassTainted(tainted), "clearing is per pass: the tainted pass stays tainted");
    }
}
