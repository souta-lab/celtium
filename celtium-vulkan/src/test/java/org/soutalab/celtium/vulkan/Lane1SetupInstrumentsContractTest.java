package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contracts for the D8-b instrument wiring (G2-30's added instruments and the G2-09 /
 * G2-31 readouts): the st* brackets sit on the terrain driver's real boundaries, the
 * setup-statistics line exists, every recorded pipeline barrier is counted, and the
 * K3 barrier span is registered at both of its seats. The seats live inside device-bound
 * recording methods, so the wiring is pinned by source contracts in the shape
 * {@code DrawpathTerrainHealthContractTest} established.
 */
class Lane1SetupInstrumentsContractTest {

    private static final String DRIVER =
            "forge122/src/main/java/org/soutalab/celtium/forge/terrain/CeltiumTerrainDriver.java";
    private static final String SCANNER =
            "forge122/src/main/java/org/soutalab/celtium/forge/terrain/ForgeSectionScanner.java";
    private static final String FRONTEND =
            "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/terrain/lane1/Lane1TerrainFrontend.java";
    private static final String ADAPTER =
            "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanBackendAdapter.java";
    private static final String COORDINATOR =
            "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameCoordinator.java";
    private static final String MAIN_TREE =
            "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan";

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
    void stBracketsAreWiredOntoTheDriverBoundaries() throws IOException {
        String driver = read(DRIVER);
        // The five dead pass-split slots each gained exactly one begin seat in the driver:
        // stEvents/stUpdate inside the tick gate, stUpload around the drain, stPrepare
        // around the dirty copy and sort, stAsync around the scanner fill.
        for (String name : new String[]{"stEvents", "stUpdate", "stUpload", "stPrepare", "stAsync"}) {
            assertEquals(1, count(driver, "FramePassTimings.begin(\"" + name + "\")"),
                    name + " must open exactly once in the driver");
            assertEquals(1, count(driver, "FramePassTimings.end(\"" + name + "\")"),
                    name + " must close exactly once in the driver");
        }
    }

    @Test
    void setupStatisticsLineAndTintAccumulationExist() throws IOException {
        String driver = read(DRIVER);
        assertTrue(driver.contains("CELTIUM_LANE1_SETUP_STATS"),
                "the driver prints the setup-statistics line");
        String scanner = read(SCANNER);
        assertTrue(scanner.contains("tintNanos +="),
                "the scanner accumulates resolveTint time locally, never in a bracket");
        assertTrue(scanner.contains("tintCellsTotal"),
                "the scanner publishes tinted-cell totals for the driver's window");
    }

    @Test
    void everyRecordedBarrierIsCounted() throws IOException {
        // BackendCallCounters' own contract: one noteBarrier per vkCmdPipelineBarrier
        // invocation. The terrain frontend's three seats and the pack seats were the
        // census's gaps (G2-09); this walks the whole main tree so a new uncounted
        // seat fails here instead of silently undercounting a leg.
        Path root = findRepoFile(MAIN_TREE);
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String text = Files.readString(file);
                int barriers = count(text, "vkCmdPipelineBarrier(");
                if (barriers == 0) {
                    continue;
                }
                if (file.getFileName().toString().equals("PresentSwapchain.java")) {
                    // Its single occurrence is a quoted validation message inside a
                    // javadoc, not a recording seat (G2-09 census, section 2.2).
                    continue;
                }
                assertEquals(barriers, count(text, "noteBarrier()"),
                        file.getFileName() + ": every recorded barrier must be counted");
            }
        }
    }

    @Test
    void barrierSpanIsRegisteredAtBothSeats() throws IOException {
        // K3's third term: the cull seat and the HiZ seat each file one span under the
        // shared name, and the readback sums a name's slots into the frame total.
        assertEquals(1, count(read(ADAPTER), "\"lane1_barriers\""),
                "the cull seat registers the barrier span once");
        assertEquals(1, count(read(COORDINATOR), "\"lane1_barriers\""),
                "the HiZ seat registers the barrier span once");
        String frontend = read(FRONTEND);
        assertEquals(3, count(frontend, "BackendCallCounters.noteBarrier()"),
                "the frontend counts its two cull barriers and the image-barrier helper");
    }
}
