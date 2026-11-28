package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contracts for the drawpath audit's terrain/pack health wiring (DP-04, DP-05,
 * DP-36): the quantities a leg must gate on - executor disablement, head-of-line
 * blockage, slab capacity skips, latched pipeline failures, atlas backoffs, pack
 * terrain refusals and skipped regions - are counted at their seats and carried
 * into {@code CELTIUM_VERIFY}. The seats live inside device-bound methods (a
 * frame recording, an atlas re-supply, a pipeline creation), so the wiring is
 * pinned by source contracts in the shape {@code FallbackWiringContractTest}
 * established; the bridge half of DP-06 is covered behaviourally by
 * {@code DemonicaTerrainBridgeFailureTest}.
 */
class DrawpathTerrainHealthContractTest {

    private static final String BACKEND =
            "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java";
    private static final String DIAGNOSTICS =
            "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java";
    private static final String ENGINE_A =
            "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainEngineA.java";
    private static final String HOST =
            "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainEngineAHost.java";
    private static final String RENDERER =
            "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainRenderer.java";
    private static final String COPY_READ_SIDE =
            "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/CopyReadSide.java";
    private static final String BRIDGE =
            "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/DemonicaTerrainBridge.java";

    private static String read(String repoRelative) throws IOException {
        return Files.readString(findRepoFile(repoRelative));
    }

    private static Path findRepoFile(String repoRelative) {
        Path direct = Paths.get(repoRelative);
        if (Files.exists(direct)) {
            return direct;
        }
        Path fromModule = Paths.get("..").resolve(repoRelative);
        if (Files.exists(fromModule)) {
            return fromModule;
        }
        throw new IllegalStateException("cannot locate " + repoRelative + " from "
                + Paths.get("").toAbsolutePath());
    }

    private static int count(String text, String needle) {
        int found = 0;
        int from = 0;
        while ((from = text.indexOf(needle, from)) >= 0) {
            found++;
            from += needle.length();
        }
        return found;
    }





    @Test
    void atlasBackoffEntriesAreCountedAtTheFailureSeat() throws IOException {
        String copy = read(COPY_READ_SIDE);
        assertEquals(1, count(copy, "funnel.atlasRefreshFailures++"),
                "exactly one seat counts atlas backoff entries");
        int failed = copy.indexOf("block atlas upload failed");
        int counted = copy.indexOf("funnel.atlasRefreshFailures++");
        int backoff = copy.indexOf("this.backend.atlasRefreshCountdown = 400", counted);
        assertTrue(failed >= 0 && counted > failed && backoff > counted,
                "the count sits in the failure catch, beside the backoff it names");
    }


}
