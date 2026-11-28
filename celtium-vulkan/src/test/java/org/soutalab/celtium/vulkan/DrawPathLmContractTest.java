package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;
import org.soutalab.celtium.vulkan.gl.GlsmFrontend;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Source contracts and behavioral pins for the draw-path L-M bundle
 * (DP-22, DP-23, DP-24, DP-26).
 */
class DrawPathLmContractTest {

    /** DP-22: ModelReplay.celtium$drawModelRun drops unrecorded runs without success accounting. */
    @Test
    void dp22DrawModelRunDropsWithoutSuccessAccounting() throws IOException {
        String backend = readSource(
                "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java");
        assertTrue(backend.contains("long modelRunDropped;"),
                "backend declares the modelRunDropped counter");

        String modelReplay = readSource(
                "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelReplay.java");
        int start = modelReplay.indexOf("void celtium$drawModelRun(ImmediateGroup group) {");
        int end = modelReplay.indexOf("private static String celtium$gameCaller()", start);
        assertTrue(start >= 0 && end > start, "drawModelRun method is located");
        String body = modelReplay.substring(start, end);

        int guard = body.indexOf("if (b.modelInstances == null || bindlessBinding == null) {");
        assertTrue(guard >= 0, "null-instance / null-bindless guard exists");
        int guardReturn = body.indexOf("return;", guard);
        assertTrue(guardReturn > guard, "guard returns immediately");
        String dropBlock = body.substring(guard, guardReturn + "return;".length());

        assertTrue(dropBlock.contains("b.modelRunDropped++;"),
                "drop path increments modelRunDropped");
        assertTrue(dropBlock.contains("b.retiredImmediateGroups.add(group);"),
                "drop path retires the group");
        assertTrue(dropBlock.contains("b.immediateStatistics.noteImmediateGroupsDiscarded();"),
                "drop path counts the discarded group");
        assertFalse(dropBlock.contains("postClears"),
                "drop path leaves postClears for the drain caller to process");
        assertFalse(dropBlock.contains("noteGroupDrawCount"),
                "drop path does not record group draw count");
        assertFalse(dropBlock.contains("recordedImmediateGroups"),
                "drop path does not add to recordedImmediateGroups");

        int firstSuccess = body.indexOf("b.immediateStatistics.noteGroupDrawCount();");
        assertTrue(firstSuccess > guardReturn,
                "all success accounting occurs strictly after the drop guard return");
    }

    /** DP-23: modelInstancesFailed (all 3 latch sites), modelRunDropped, and modelMissingMeshes are exposed in VERIFY. */
    @Test
    void dp23ModelFailureCountersExposedInVerify() throws IOException {
        String modelReplay = readSource(
                "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModelReplay.java");
        int appendStart = modelReplay.indexOf("boolean celtium$appendModelInstance(");
        int appendEnd = modelReplay.indexOf("private boolean celtium$tryModelReplayFastPath(", appendStart);
        assertTrue(appendStart >= 0 && appendEnd > appendStart, "appendModelInstance is located");
        String appendBody = modelReplay.substring(appendStart, appendEnd);
        int latchCount = countOccurrences(appendBody, "b.modelInstancesFailed = true;");
        assertEquals(3, latchCount, "all three failure-injection latch sites remain in appendModelInstance");

        String diag = readSource(
                "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java");
        int anchor = diag.indexOf("+ \" modelDrawRefusals=\" + b.modelDrawRefusals");
        assertTrue(anchor >= 0, "modelDrawRefusals anchor is located in BackendDiagnostics");
        String window = diag.substring(anchor, Math.min(diag.length(), anchor + 400));
        assertTrue(window.contains("+ \" modelRunDropped=\" + b.modelRunDropped"),
                "VERIFY exposes modelRunDropped next to modelDrawRefusals");
        assertTrue(window.contains("+ \" modelInstancesFailed=\" + b.modelInstancesFailed"),
                "VERIFY unconditionally exposes modelInstancesFailed next to modelDrawRefusals");
        assertTrue(window.contains("+ \" modelMissingMeshes=\" + b.modelGeometry.modelMissingMeshes()"),
                "VERIFY exposes modelMissingMeshes next to modelDrawRefusals");
    }

    /** DP-24: CeltiumFontGlyphCapture counts null-backend drops and non-Vulkan per-quad refusals. */
    @Test
    void dp24FontGlyphCaptureCountsDropsAndNonVulkanRefusals() throws IOException {
        String capture = readSource(
                "forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java");
        int flushStart = capture.indexOf("public static void flushBufferedQuads(CeltiumBackend backend) {");
        int flushEnd = capture.indexOf("private static boolean ensureBufferedCapacity(int bytes) {", flushStart);
        assertTrue(flushStart >= 0 && flushEnd > flushStart, "flushBufferedQuads is located");
        String flushBody = capture.substring(flushStart, flushEnd);

        int nonVulkan = flushBody.indexOf("} else if (backend != null) {");
        assertTrue(nonVulkan >= 0, "non-Vulkan branch is located");
        int nullBranch = flushBody.indexOf("} else if (bufferedQuadCount > 0) {", nonVulkan);
        assertTrue(nullBranch > nonVulkan, "null-backend branch is located");
        String nonVulkanBody = flushBody.substring(nonVulkan, nullBranch);
        String nullBranchBody = flushBody.substring(nullBranch, flushBody.indexOf("} catch (Throwable error) {", nullBranch));

        assertTrue(nonVulkanBody.contains("if (backend.submitImmediateBatch("),
                "non-Vulkan branch inspects submitImmediateBatch return value");
        assertTrue(nonVulkanBody.contains("takenThisFlush++"),
                "non-Vulkan branch counts accepted quads");
        assertTrue(nonVulkanBody.contains("refusedQuads += bufferedQuadCount - takenThisFlush"),
                "non-Vulkan branch accounts unaccepted quads to refusedQuads");
        assertTrue(nonVulkanBody.contains("refuse(\"flush-quad-refused\""),
                "non-Vulkan branch records flush-quad-refused");
        assertTrue(nonVulkanBody.contains("accepted += takenThisFlush"),
                "non-Vulkan branch accounts accepted quads");

        assertTrue(nullBranchBody.contains("nullBackendDroppedQuads += bufferedQuadCount"),
                "null-backend branch counts dropped quads");
        assertTrue(nullBranchBody.contains("CELTIUM_FONT_GLYPH_REFUSED reason=flush-no-backend"),
                "null-backend branch emits first-occurrence refusal log");

        assertTrue(capture.contains("+ \" nullBackendDroppedQuads=\" + nullBackendDroppedQuads"),
                "summary exposes nullBackendDroppedQuads");
    }

    /**
     * DP-26: Font window ownership suppresses legacy glBegin. (The VERIFY exposure of the
     * suppression counter was deleted with the old census fragments at S3-b E4-b1, Neon door
     * ruling section 3.1(3); the gate and its counter are what this contract pins.)
     */
    @Test
    void dp26FontWindowOwnershipSuppressesLegacyBegin() throws IOException {
        String capture = readSource(
                "forge122/src/main/java/org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.java");
        int openStart = capture.indexOf("private static void openWindowBody(boolean unicode) {");
        int openEnd = capture.indexOf("public static boolean fastPathAvailable() {", openStart);
        assertTrue(openStart >= 0 && openEnd > openStart, "openWindowBody is located");
        String openBody = capture.substring(openStart, openEnd);
        assertTrue(openBody.contains("GlsmFrontend.celtium$fontWindowBegin();"),
                "openWindowBody raises font window ownership flag when armed");

        int closeStart = capture.indexOf("public static void closeWindow() {");
        int closeEnd = capture.indexOf("private static void reset() {", closeStart);
        assertTrue(closeStart >= 0 && closeEnd > closeStart, "closeWindow is located");
        String closeBody = capture.substring(closeStart, closeEnd);
        assertEquals(2, countOccurrences(closeBody, "GlsmFrontend.celtium$fontWindowEnd();"),
                "closeWindow lowers font window ownership flag on both TIMINGS and non-TIMINGS paths");

        // Behavioral verification of the GlsmFrontend ownership gate itself.
        long before = GlsmFrontend.celtium$legacyFontWindowSuppressed();
        try {
            GlsmFrontend.celtium$fontWindowBegin();
            GlsmFrontend.celtium$legacyBegin(5);
            assertEquals(before + 1L, GlsmFrontend.celtium$legacyFontWindowSuppressed(),
                    "legacyBegin inside an active font window is suppressed and counted");
        } finally {
            GlsmFrontend.celtium$fontWindowEnd();
        }
        GlsmFrontend.celtium$legacyBegin(5);
        assertEquals(before + 1L, GlsmFrontend.celtium$legacyFontWindowSuppressed(),
                "legacyBegin after fontWindowEnd is not counted as font-window suppressed");
    }

    private static int countOccurrences(String text, String needle) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(needle, idx)) >= 0) {
            count++;
            idx += needle.length();
        }
        return count;
    }

    private static String readSource(String repoRelative) throws IOException {
        return Files.readString(findRepoFile(repoRelative)).replace("\r\n", "\n");
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
        throw new IllegalStateException("cannot locate " + repoRelative + " from " + Paths.get("").toAbsolutePath());
    }
}
