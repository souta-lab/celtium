package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R-55, Step C (with R-21's per-pass ignition counter): the FallbackPaths
 * wiring of the Tier-A branch. The upload verdict is a per-section count
 * under {@code terrain.upload}; the ignition verdict is a per-pass,
 * per-frame count under {@code terrain.engine.<pass>} only — the bare
 * {@code terrain.engine} name is never counted at the ignition site, so the
 * two granularities cannot double-count. The counting behaviour is exercised
 * directly; the device-bound wiring sites are pinned as source contracts.
 */
class TierAFallbackWiringTest {

    @BeforeEach
    void clearRegistry() {
        FallbackPaths.clearForTests();
    }

    @Test
    void theUploadPathNameIsTheRegisteredConstant() {
        assertEquals("terrain.upload", FallbackPaths.TERRAIN_UPLOAD);
    }

    @Test
    void perPassIgnitionNamesCountIndependentlyOfTheBareName() {
        FallbackPaths.Handle solid = FallbackPaths.register(FallbackPaths.TERRAIN_ENGINE + ".solid");
        solid.attempt();
        solid.verdict(true);
        solid.attempt();
        solid.verdict(false);

        assertEquals(2L, solid.attempts());
        assertEquals(1L, solid.fastHits());
        assertEquals(1L, solid.fallbacks());
        assertEquals(0L, FallbackPaths.register(FallbackPaths.TERRAIN_ENGINE).attempts(),
                "the bare terrain.engine name carries no ignition counts");
        assertTrue(FallbackPaths.summary().contains("terrain.engine.solid"),
                "the per-pass path reaches the diagnostics summary");
    }

    /** The ignition verdict stands immediately at the tierAArmed determination in recordRenderPass, under the per-pass name. */
    @Test
    void theIgnitionVerdictIsCountedAtTheArmedDetermination() throws IOException {
        String source = Files.readString(findRepoFile(
                "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java"));
        int armed = source.indexOf("boolean tierAArmed = tierAEngine != null;");
        assertTrue(armed >= 0, "the armed determination is located");
        String after = source.substring(armed, armed + 1200);
        assertTrue(after.contains("FallbackPaths.TERRAIN_ENGINE + \".\" + pass.name()"),
                "the ignition path is the per-pass name");
        assertTrue(after.contains("terrainEnginePath.attempt();"), "one attempt per pass per frame");
        assertTrue(after.contains("terrainEnginePath.verdict(tierAArmed);"),
                "the verdict is the armed flag itself");
        assertFalse(after.contains("FallbackPaths.attempt(FallbackPaths.TERRAIN_ENGINE)"),
                "the bare name is not counted at the ignition site");
    }

    /** Every upload offer ends in exactly one terrain.upload verdict, fast on a skip and fallback on a legacy upload. */
    @Test
    void theUploadVerdictIsCountedPerOffer() throws IOException {
        String source = Files.readString(findRepoFile(
                "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainRenderer.java"));
        assertTrue(source.contains("FallbackPaths.register(FallbackPaths.TERRAIN_UPLOAD)"),
                "the renderer holds the terrain.upload handle");
        int decision = source.indexOf("for (TierAOffer offer : tierAOffers)");
        assertTrue(decision >= 0, "the offer decision loop is located");
        String loop = source.substring(decision, decision + 1200);
        assertTrue(loop.contains("TERRAIN_UPLOAD_PATH.attempt();"), "one attempt per offer");
        assertTrue(loop.contains("TERRAIN_UPLOAD_PATH.fast();"), "a skipped legacy upload is the fast verdict");
        assertTrue(loop.contains("TERRAIN_UPLOAD_PATH.fallback();"), "a kept legacy upload is the fallback verdict");
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
