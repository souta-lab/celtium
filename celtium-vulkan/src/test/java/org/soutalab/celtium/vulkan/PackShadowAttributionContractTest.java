package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Shadow-stage attribution instrument (design:
 * OVERHAUL-SHADOW-ATTRIBUTION-INSTRUMENT-2026-11-26): one pack executor
 * serves both the shadow stage and the gbuffers stage, so its cumulative
 * drawn total cannot name the stage a drawn frame came from. recordDraws is
 * handed the executing stage and splits the total at the same seat, which
 * makes drawnShadow + drawnGbuffers == drawn hold by construction. The
 * wiring is pinned as source contracts, in the same form as the existing
 * pack censuses (a behaviour test would need a Vulkan context).
 */
class PackShadowAttributionContractTest {

    /** recordDraws receives the executing stage as a third argument. */
    @Test
    void recordDrawsCarriesTheStage() throws IOException {
        String source = packEngineSource();
        assertTrue(source.contains(
                        "boolean recordDraws(VkCommandBuffer commandBuffer, int slotIndex, boolean shadowStage)"),
                "recordDraws is handed the stage");
    }

    /** The split counters are declared as their own fields, not derived at print time. */
    @Test
    void theSplitCountersAreDeclared() throws IOException {
        String source = packEngineSource();
        assertTrue(source.contains("private long framesDrawnShadow;"),
                "the shadow side of the split is a field");
        assertTrue(source.contains("private long framesDrawnGbuffers;"),
                "the gbuffers side of the split is a field");
    }

    /**
     * The split increments sit in the same if (any) block as the total's
     * increment, and each counter is incremented at exactly one seat in the
     * file: the split is a partition of the total, so the invariant
     * drawnShadow + drawnGbuffers == drawn cannot drift.
     */
    @Test
    void theSplitIncrementsShareTheTotalsBlock() throws IOException {
        String source = packEngineSource();
        int method = source.indexOf("boolean recordDraws(VkCommandBuffer");
        assertTrue(method >= 0, "recordDraws is present");
        int block = source.indexOf("if (any) {", method);
        int total = source.indexOf("this.framesDrawn++;", method);
        int shadow = source.indexOf("this.framesDrawnShadow++;", method);
        int gbuffers = source.indexOf("this.framesDrawnGbuffers++;", method);
        assertTrue(block >= 0 && total > block, "the total increments inside if (any)");
        assertTrue(shadow > total && gbuffers > total,
                "both sides of the split increment alongside the total");
        int blockEnd = source.indexOf("\n        }", total);
        assertTrue(blockEnd > shadow && blockEnd > gbuffers,
                "both sides of the split increment before the if (any) block closes");
        assertEquals(1, countOccurrences(source, "this.framesDrawn++;"),
                "the total has exactly one increment seat");
        assertEquals(1, countOccurrences(source, "this.framesDrawnShadow++;"),
                "the shadow side has exactly one increment seat");
        assertEquals(1, countOccurrences(source, "this.framesDrawnGbuffers++;"),
                "the gbuffers side has exactly one increment seat");
    }

    /** The census line prints the split immediately after the total, under the existing tag. */
    @Test
    void theCensusLinePrintsTheSplitAfterTheTotal() throws IOException {
        String source = packEngineSource();
        int line = source.indexOf("CELTIUM_TERRAIN_A_PACK pass=");
        assertTrue(line >= 0, "the periodic census line is present");
        int drawn = source.indexOf("\" drawn=\"", line);
        int drawnShadow = source.indexOf("\" drawnShadow=\"", line);
        int drawnGbuffers = source.indexOf("\" drawnGbuffers=\"", line);
        assertTrue(drawn >= 0 && drawnShadow > drawn && drawnGbuffers > drawnShadow,
                "drawnShadow= and drawnGbuffers= follow drawn= in that order");
    }

    /** The one production call site hands the stage signal it already holds in scope. */
    @Test
    void theOnlyProductionCallHandsTheStage() throws IOException {
        String source = backendSource();
        assertEquals(1, countOccurrences(source, "packEngine.recordDraws("),
                "the pack executor has exactly one production draw call site");
        assertTrue(source.contains(
                        "packEngine.recordDraws(this.commandBuffer, this.frameSlot, shadowStage);"),
                "the call site hands its in-scope shadowStage");
    }

    /** prepareFrame keeps the call shape the increment (iv) contract pins: no stage is threaded into it. */
    @Test
    void prepareFrameKeepsItsShape() throws IOException {
        String source = packEngineSource();
        assertTrue(source.contains(
                        "boolean prepareFrame(VkCommandBuffer commandBuffer, int slotIndex, float[] viewProjection,"),
                "prepareFrame's signature is unchanged");
    }

    private static int countOccurrences(String source, String needle) {
        int count = 0;
        int index = 0;
        while ((index = source.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }

    private static String packEngineSource() throws IOException {
        return readSource(
                "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainPackEngine.java");
    }

    private static String backendSource() throws IOException {
        return readSource(
                "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java");
    }

    private static String readSource(String repoRelative) throws IOException {
        return Files.readString(findRepoFile(repoRelative)).replace("\r\n", "\n");
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
