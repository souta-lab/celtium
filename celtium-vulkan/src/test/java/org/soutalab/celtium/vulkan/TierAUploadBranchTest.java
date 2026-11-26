package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R-55, Step A/B: the upload branch predicate and its wiring. The legacy
 * upload is skipped exactly when Tier-A took the section (accepted or
 * deferred) and its pass is not tainted; everything else keeps the legacy
 * upload. The predicate is pure, so its truth table is exercised directly;
 * the device-bound sites that consume it (the pass head's arm suppression,
 * prepareFrame's report relay) are pinned as source contracts.
 */
class TierAUploadBranchTest {

    @Test
    void anAcceptedSectionOfACleanPassSkipsTheLegacyUpload() {
        assertTrue(TerrainRenderer.tierAUploadSkipsLegacy(
                TerrainEngineA.RegisterOutcome.ACCEPTED, false));
    }

    @Test
    void aDeferredSectionOfACleanPassSkipsTheLegacyUpload() {
        assertTrue(TerrainRenderer.tierAUploadSkipsLegacy(
                TerrainEngineA.RegisterOutcome.DEFERRED, false),
                "a deferred section is Tier-A's: it becomes resident through the deferred queue");
    }

    @Test
    void aRejectedSectionKeepsTheLegacyUpload() {
        assertFalse(TerrainRenderer.tierAUploadSkipsLegacy(
                TerrainEngineA.RegisterOutcome.REJECTED, false));
        assertFalse(TerrainRenderer.tierAUploadSkipsLegacy(
                TerrainEngineA.RegisterOutcome.REJECTED, true));
    }

    @Test
    void aTaintedPassKeepsEverySectionOnTheLegacyUpload() {
        assertFalse(TerrainRenderer.tierAUploadSkipsLegacy(
                TerrainEngineA.RegisterOutcome.ACCEPTED, true),
                "while the pass is tainted it draws from the legacy route, so even accepted sections upload there");
        assertFalse(TerrainRenderer.tierAUploadSkipsLegacy(
                TerrainEngineA.RegisterOutcome.DEFERRED, true));
    }

    /** The pass head withholds the arm on a tainted pass, after prepareFrame has run (drain + verification keep running). */
    @Test
    void thePassHeadSuppressesTheArmOnATaintedPass() throws IOException {
        String source = Files.readString(findRepoFile(
                "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java"));
        int head = source.indexOf("celtium$terrainAPassHead");
        int prepared = source.indexOf("engine.prepareFrame(", head);
        int taintCheck = source.indexOf("host.isPassTainted(pass)", head);
        int armed = source.indexOf("this.terrainAArmedEngines.put(pass, engine);", head);
        assertTrue(prepared > head && taintCheck > prepared && armed > taintCheck,
                "the taint check stands between prepareFrame and the arm cache");
    }

    /** prepareFrame relays every residency report to the host, which is what clears taints. */
    @Test
    void prepareFrameRelaysTheResidencyReport() throws IOException {
        String source = Files.readString(findRepoFile(
                "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainEngineA.java"));
        assertTrue(source.contains("this.host.noteResidencyReport(report);"),
                "the per-frame verification reaches the host's taint state");
    }

    /** The renderer's decision loop reads the taint through the predicate, not around it. */
    @Test
    void theUploadDecisionConsultsTheTaint() throws IOException {
        String source = Files.readString(findRepoFile(
                "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainRenderer.java"));
        assertTrue(source.contains(
                        "if (tierAUploadSkipsLegacy(offer.outcome(), this.terrainAHost.isPassTainted(offer.pass())))"),
                "the legacy fallback of an offer is decided by the predicate over outcome and taint");
        assertTrue(source.contains("this.terrainAHost.taintPass(meshEntry.getKey());"),
                "a rejected offer taints its pass at offer time");
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
