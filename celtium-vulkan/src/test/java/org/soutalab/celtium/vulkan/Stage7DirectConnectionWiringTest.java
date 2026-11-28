package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Stage 5 stage 7 (S7-c), the direct connection of the frontend's replay streams.
 * <p>
 * E4-c: four of this contract's five tests pinned the monolith frontend's own submit sites
 * and list-replay routes ({@code submitImmediateBatch} call sites, compiled-run and
 * compiled-array replay inside the frontend). That machinery was not ported to the capture
 * entry - under the decoders ruling the entry routes draws and list replay through
 * GLSMDraws/DisplayListManager into the executor - so those four tests retired with the
 * frontend they scanned. What survives of the contract is the backend half below: retained
 * geometry still converges on the shared append machinery.
 */
class Stage7DirectConnectionWiringTest {

    private static final String ROOT = "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/";
    private static final String BACKEND = ROOT + "VulkanTerrainGpuBackend.java";

    private static String read(String repoRelative) throws IOException {
        return Files.readString(findRepoFile(repoRelative));
    }

    @Test
    @DisplayName("retained geometry converges on the same append machinery")
    void retainedGeometryConverges() throws IOException {
        String backend = read(BACKEND);
        assertTrue(backend.contains("return submitGeometryBatch(null, geometry.stride(), geometry.formatId(), geometry.address(),"),
                "submitRetainedGeometry delegates into the shared append machinery, not a second draw path");
    }

    private static Path findRepoFile(String repoRelative) {
        return findRepoRoot().resolve(repoRelative);
    }

    private static Path findRepoRoot() {
        Path direct = Paths.get("celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java");
        if (Files.exists(direct)) {
            return Paths.get("");
        }
        return Paths.get("..");
    }
}
