package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The pack-scene 02684 residual disposition, plan A2-1: the prewarm fallback in
 * {@code celtium$resolveImmediatePipeline} returned the family's standard pipeline without
 * looking at the requested pass or colour count, but the standard set is built against the
 * one-colour terrain pass - so a first-sight state inside a multi-colour window drew with a
 * pipeline whose render pass leaves colour attachment [1] {@code VK_ATTACHMENT_UNUSED}
 * (VUID-vkCmdDrawIndexed-renderPass-02684, two messages in both the (f) and S3R-2 BSL
 * confirmation legs). The fallback now serves single-colour targets only; a multi-colour
 * miss falls through to the exact synchronous resolution the multi-colour fix (plan A) made
 * colour-aware.
 * <p>
 * The device-bound half (a draw actually issued inside a multi-colour window) cannot run on
 * a CPU-only instance, so the gate is pinned as a source contract at the one branch that
 * decides it, in the shape {@code ImmediateMultiColorPassContractTest} established - plus a
 * pin that the warm set itself is untouched: the per-count standard sets of plan A2-2 are
 * deliberately not built.
 */
class PrewarmFallbackColorGateContractTest {

    private static final String BACKEND =
            "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java";

    @Test
    @DisplayName("the prewarm fallback branch only stands for single-colour targets")
    void fallbackBranchIsGatedOnSingleColorTargets() throws IOException {
        String body = resolveMethodBody(read(BACKEND));
        assertTrue(body.contains("prewarm.needsFallback(key) && targetColors <= 1"),
                "the fallback condition carries the colour term beside the prewarm terms");
        assertTrue(body.indexOf("targetColors <= 1") < body.indexOf("return standard;"),
                "the colour term gates the branch that returns the standard pipeline");
    }

    @Test
    @DisplayName("a multi-colour miss falls through to the exact synchronous resolution")
    void multiColorMissesFallThroughToTheExactResolution() throws IOException {
        String body = resolveMethodBody(read(BACKEND));
        assertEquals(1, count(body,
                        "return celtium$getOrCreateImmediatePipeline(format, key, state, pass, targetColors);"),
                "the ungated tail of the resolution is the exact colour-aware build, once");
    }

    @Test
    @DisplayName("the single-colour fallback form is unchanged: standard key, counted draw, flagged prepare")
    void singleColorFallbackFormIsUnchanged() throws IOException {
        String body = resolveMethodBody(read(BACKEND));
        assertTrue(body.contains("celtium$immediateStandardKey(format, key, state)"),
                "the substitute is still looked up by the family's standard key");
        assertTrue(body.contains("prewarm.noteFallbackDraw();"),
                "a fallback draw is still counted on the prewarm instrument");
        assertTrue(body.contains("this.celtium$lastPrepareWasFallback = true;"),
                "the prepare is still flagged, so the material stays out of the prepare cache");
    }

    @Test
    @DisplayName("the warm set still builds its standards at one colour (plan A2-2 is not built)")
    void warmSetStillBuildsStandardsAtOneColor() throws IOException {
        String source = read(BACKEND);
        assertEquals(1, count(source,
                        "celtium$getOrCreateImmediatePipeline(format, key, state, pass, 1) != null"),
                "the startup warm set's creation action keeps its hard-coded one-colour build");
    }

    /** The body of {@code celtium$resolveImmediatePipeline}, from its signature to the next member. */
    private static String resolveMethodBody(String source) {
        int start = source.indexOf("private Object celtium$resolveImmediatePipeline");
        assertTrue(start >= 0, "the resolution method exists");
        int end = source.indexOf("\n    private ", start);
        assertTrue(end > start, "the method body is delimited by the next member");
        return source.substring(start, end);
    }

    private static String read(String repoRelative) throws IOException {
        return Files.readString(findRepoFile(repoRelative)).replace("\r\n", "\n");
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
