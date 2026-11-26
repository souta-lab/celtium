package org.soutalab.celtium.vulkan;

import org.embeddedt.embeddium.impl.render.chunk.compile.sorting.QuadPrimitiveType;
import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;
import org.embeddedt.embeddium.impl.render.chunk.vertex.format.ChunkMeshFormats;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Terrain regression fix (2026-11-26): the terrain pass entry must not refuse
 * a pass whose geometry lives entirely in the Tier-A store. The entry's
 * legacy-presence test (hasPass) reads the regions' legacy storage sets, so
 * an all-Tier-A pass reported no presence while its only per-frame driver —
 * the pass head, which also drains the deferred queue and rolls the upload
 * budget — sat inside the refused entry. The gate therefore also consults
 * the host's live-work peek. The host is pure CPU state, so the peek's
 * no-engine case is exercised directly; the device-bound wiring (the entry
 * condition, the executor predicate) is pinned as source contracts.
 */
class TierALiveWorkGateTest {

    private static TerrainRenderPass pass(String name) {
        return TerrainRenderPass.builder()
                .name(name)
                .vertexType(ChunkMeshFormats.TERRAIN_A16)
                .primitiveType(QuadPrimitiveType.TRIANGULATED)
                .build();
    }

    @Test
    void aPassWithNoExecutorHasNoLiveWork() {
        TerrainEngineAHost host = new TerrainEngineAHost();
        assertFalse(host.hasLiveWork(pass("never-offered")),
                "no engine exists for the pass, so the entry's early return stands (the world-entry regime)");
    }

    /** The entry refuses a pass only when neither route has anything for it. */
    @Test
    void thePassEntryConsultsTierALiveWorkBeforeRefusing() throws IOException {
        String source = Files.readString(findRepoFile(
                "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java"));
        assertTrue(source.contains(
                        "if (!renderLists.hasPass(pass) && !celtium$tierAHasLiveWork(pass)) {"),
                "the hasPass early return also requires Tier-A to hold no live work for the pass");
    }

    /** The host's answer is a peek at the existing executor, never a creation: creating one would manufacture presence. */
    @Test
    void theHostPeekNeverCreatesAnEngine() throws IOException {
        String source = Files.readString(findRepoFile(
                "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainEngineAHost.java"));
        int start = source.indexOf("boolean hasLiveWork(TerrainRenderPass pass) {");
        assertTrue(start >= 0, "the host exposes the live-work peek");
        int end = source.indexOf("\n    }", start);
        String body = source.substring(start, end);
        assertTrue(body.contains("this.engines.get(pass)"), "the peek reads the existing executor");
        assertTrue(body.contains("engine.hasLiveWork()"), "the peek defers to the executor's own state");
        assertFalse(body.contains("engineFor("), "the peek must not create the executor it tests for");
    }

    /** Live work is resident records or a pending deferred queue, and a disabled executor has none. */
    @Test
    void theExecutorPredicateReadsRecordsDeferredAndDisabled() throws IOException {
        String source = Files.readString(findRepoFile(
                "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainEngineA.java"));
        int start = source.indexOf("boolean hasLiveWork() {");
        assertTrue(start >= 0, "the executor exposes its live-work predicate");
        int end = source.indexOf("\n    }", start);
        String body = source.substring(start, end);
        assertTrue(body.contains("!this.disabled"), "a disabled executor has no live work");
        assertTrue(body.contains("this.store.recordCount() > 0"), "resident records are live work");
        assertTrue(body.contains("!this.deferred.isEmpty()"),
                "deferred registrations are live work: prepareFrame is their only way in");
    }

    /** Test working directories differ between the module gate and a repo-root run; both shapes are tried. */
    private static Path findRepoFile(String repoRelative) {
        Path direct = Paths.get(repoRelative);
        if (Files.exists(direct)) {
            return direct;
        }
        Path fromModule = Paths.get("..").resolve(repoRelative);
        if (Files.exists(fromModule)) {
            return fromModule;
        }
        throw new IllegalStateException("cannot locate " + repoRelative + " from " + Paths.get("").toAbsolutePath());
    }
}
