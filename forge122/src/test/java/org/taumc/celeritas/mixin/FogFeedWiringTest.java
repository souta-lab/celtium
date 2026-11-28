package org.taumc.celeritas.mixin;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The R-105 fog feed census, pinned at the source seams in the shape {@code DrawPathLossWiringTest}
 * established: the mixin notes the feed inside its enable/disable injections, the terrain backend notes
 * the latch read at the per-frame fog latch, and the five values reach both the CELTIUM_FOG_STATE trace
 * and the CELTIUM_VERIFY summary. Which of the three seats a running game lands on is the confirmation
 * leg's business; what must not silently regress is the wiring.
 */
class FogFeedWiringTest {

    private static String read(String repoRelative, String moduleRelative) throws Exception {
        return Files.readString(findRepoFile(repoRelative, moduleRelative)).replace("\r\n", "\n");
    }

    private static Path findRepoFile(String repoRelative, String moduleRelative) {
        Path direct = Paths.get(repoRelative);
        if (Files.exists(direct)) {
            return direct;
        }
        Path fromModule = Paths.get(moduleRelative);
        if (Files.exists(fromModule)) {
            return fromModule;
        }
        Path up = Paths.get("..").resolve(repoRelative);
        if (Files.exists(up)) {
            return up;
        }
        return direct;
    }

    /** The feed is noted inside both fog injections, beside the state write it observes. */
    @Test
    void mixinNotesTheFeedInBothInjections() throws Exception {
        String mixin = read(
                "forge122/src/main/java/org/taumc/celeritas/mixin/core/GlStateManagerFogMixin.java",
                "src/main/java/org/taumc/celeritas/mixin/core/GlStateManagerFogMixin.java");
        int enableAt = mixin.indexOf("celtiumFog$enableFog");
        int noteEnableAt = mixin.indexOf("noteFeedEnable()", enableAt);
        int disableAt = mixin.indexOf("celtiumFog$disableFog");
        int noteDisableAt = mixin.indexOf("noteFeedDisable()", disableAt);
        assertTrue(enableAt >= 0 && noteEnableAt > enableAt,
                "the enable injection notes the feed");
        assertTrue(disableAt >= 0 && noteDisableAt > disableAt,
                "the disable injection notes the feed");
    }

    /** The latch read is noted inside the per-frame prepare, at the fog latch itself. */
    @Test
    void backendNotesTheLatchReadAtTheFogLatch() throws Exception {
        String backend = read(
                "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java",
                "../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java");
        int prepareAt = backend.indexOf("private void celtium$lane1PrepareFrame(");
        int noteAt = backend.indexOf("noteLatchRead(fogEnabled)", prepareAt);
        assertTrue(prepareAt >= 0 && noteAt > prepareAt,
                "the latch note sits inside celtium$lane1PrepareFrame");
    }

    /** The five census values reach both readouts a leg reads: the fog trace and the verify summary. */
    @Test
    void censusReachesTheFogTraceAndTheVerifySummary() throws Exception {
        String state = read(
                "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ImmediateFogState.java",
                "../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ImmediateFogState.java");
        assertTrue(state.contains("feedEnable=") && state.contains("feedDisable=")
                        && state.contains("latchEnabled=") && state.contains("latchDisabled=")
                        && state.contains("stateId="),
                "the CELTIUM_FOG_STATE trace carries the census and the state identity");
        assertTrue(state.contains("CELTIUM_FOG_FEED"),
                "the feed side has its own bounded line carrying the written instance's identity");
        String diagnostics = read(
                "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java",
                "../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java");
        assertTrue(diagnostics.contains("fogFeedEnable=") && diagnostics.contains("fogFeedDisable=")
                        && diagnostics.contains("fogLatchEnabled=") && diagnostics.contains("fogLatchDisabled=")
                        && diagnostics.contains("fogStateId="),
                "the CELTIUM_VERIFY summary carries the same census");
    }
}
