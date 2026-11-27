package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DP-13 (drawpath audit N-C02): the E10 shadow issuance's visited count.
 * The stage head arms the generated list with the pre-enumerated visit
 * count, and the executor's {@code recordDraw} issues the stage's one
 * generated draw only when the region loop's own count reproduces it
 * exactly; on a mismatch it withholds the draw and disables itself. The
 * pack loop therefore has to count every region whose per-region draw it
 * suppresses under the arm, once, where the suppression happens — the
 * camera loop's {@code gpuDrawVisited} shape. Without the count the
 * invariant {@code visited == armedVisitCount} can never hold on an
 * armed frame with a non-empty population: the loop would report 0
 * against a positive prediction, the executor would disable itself on
 * the first armed frame, and that frame's shadow terrain would not be
 * drawn at all (the loop suppressed it, the generated draw was
 * withheld). These contracts pin the producer side of the invariant;
 * the consumer side (the executor's exact comparison) is pinned too, so
 * the check cannot be weakened to meet the count halfway.
 */
class ShadowIssuanceVisitCountContractTest {

    /** The armed branch of the pack loop's draw span counts the region it suppresses. */
    @Test
    void theArmedDrawBranchCountsTheSuppressedRegion() throws IOException {
        String source = backendSource();
        int branch = source.indexOf(
                "E10 shadow issuance: this region's commands are in the");
        assertTrue(branch >= 0, "the armed draw branch is present");
        int branchEnd = source.indexOf("} else if (TERRAIN_INDIRECT_ENABLED) {", branch);
        assertTrue(branchEnd > branch, "the armed draw branch precedes the indirect branch");
        String body = source.substring(branch, branchEnd);
        assertTrue(body.contains("shadowIssuanceVisited++;"),
                "the suppressed region is counted in the branch that suppresses it");
    }

    /** Exactly one increment site exists: the accounting branch after it must not count again. */
    @Test
    void theCountHasExactlyOneIncrementSite() throws IOException {
        String source = backendSource();
        int sites = source.split("shadowIssuanceVisited\\+\\+", -1).length - 1;
        assertEquals(1, sites,
                "one increment per suppressed region; a second site would double-count");
    }

    /** The counter starts at zero beside the arm decision and is what the loop's end hands to recordDraw. */
    @Test
    void theLoopEndHandsTheCountedVisitsToRecordDraw() throws IOException {
        String source = backendSource();
        int declared = source.indexOf("int shadowIssuanceVisited = 0;");
        int handed = source.indexOf(
                "this.shadowGpuTerrainDraw.recordDraw(this.commandBuffer, shadowIssuanceVisited)");
        assertTrue(declared >= 0, "the counter is declared, zeroed, beside the arm decision");
        assertTrue(handed > declared,
                "the loop's end hands the counted visits to the executor's recordDraw");
    }

    /** The camera loop counts its suppressed visits the same way: the shadow shape is a mirror, not a variant. */
    @Test
    void theCameraLoopCountsItsSuppressedVisitsInTheSameShape() throws IOException {
        String source = backendSource();
        int branch = source.indexOf("E10-b: this batch's commands are in the GPU-generated list");
        assertTrue(branch >= 0, "the camera loop's armed branch is present");
        String body = source.substring(branch, branch + 600);
        assertTrue(body.contains("gpuDrawVisited++;"),
                "the camera loop counts the batch it suppresses, in its draw span");
    }

    /** The executor's check stays exact: a count that differs from the armed prediction withholds the draw. */
    @Test
    void theExecutorCheckStaysExact() throws IOException {
        String source = Files.readString(findRepoFile(
                "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanGpuTerrainDraw.java"));
        assertTrue(source.contains("if (visitedCount != this.armedVisitCount)"),
                "recordDraw compares the loop's count against the armed prediction exactly");
        assertTrue(source.contains("CELTIUM_GPU_TERRAIN_DRAW_MISMATCH"),
                "a mismatch is printed, not silent");
    }

    private static String backendSource() throws IOException {
        return Files.readString(findRepoFile(
                "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java"));
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
