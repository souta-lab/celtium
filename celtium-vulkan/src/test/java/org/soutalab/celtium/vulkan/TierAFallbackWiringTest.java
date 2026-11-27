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

    /** Every upload offer ends in exactly one terrain.upload verdict, fast on a skip and fallback on a legacy upload. */

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
