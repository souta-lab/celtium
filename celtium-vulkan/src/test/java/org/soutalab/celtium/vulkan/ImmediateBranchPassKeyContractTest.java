package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The pass contract of the immediate branch caches (R-06, format axis): a pipeline names the
 * render pass it was built against, so the branch key a branch's cache is read under must
 * include that pass - an entry built in the frame format decision's provisional window (the
 * classic format) must never answer a draw under the pass the latched decision built (BGRA).
 * Keyed on the state alone it did exactly that, once per session
 * (VUID-vkCmdDrawIndexed-renderPass-02684; the seat record is
 * OVERHAUL-R06-SEAT-DESK-2026-11-26.md). The key arithmetic is pure and pinned here without a
 * device; the call shapes are pinned by source, the form {@code SwapchainRebuildOrderContractTest}
 * established, because the resolution itself lives inside device-bound methods. The leg that
 * counts the VUID by axis on a title-screen start is the landing window's, not this test's.
 */
class ImmediateBranchPassKeyContractTest {

    /**
     * The two render pass handles the bundle final leg's 02684 body names: 0x35... the
     * pipeline's (provisional, R8G8B8A8) and 0xa6... the command buffer's (latched, B8G8R8A8).
     * Both are a small id repeated in the handle's halves - the shape whose xor fold carries
     * only the halves' xor, which is why the production fold mixes the handle with a finalizer.
     * These two handles are the pair the fold must never merge.
     */
    private static final long PIPELINE_PASS = 0x350000000035L;
    private static final long CURRENT_PASS = 0xa600000000a6L;

    private static final String BACKEND =
            "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java";
    private static final String CONTEXT =
            "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java";

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

    /** An entry built under one pass never answers under another, in any branch family. */
    @Test
    void anEntryBuiltUnderOnePassNeverAnswersUnderAnother() {
        long key = 123456789L;
        for (int format : new int[] {1, 2, 5}) {
            assertNotEquals(
                    VulkanTerrainGpuBackend.celtium$immediateBranchKey(format, key, PIPELINE_PASS),
                    VulkanTerrainGpuBackend.celtium$immediateBranchKey(format, key, CURRENT_PASS),
                    "format " + format + ": the provisional window's entry must not read as the"
                            + " latched pass' entry");
        }
    }

    /** The hit path is unchanged in shape: the same pass, format and state read the same entry. */
    @Test
    void theSamePassAndStateReadTheSameEntry() {
        long key = 987654321L;
        for (int format : new int[] {1, 2, 5}) {
            assertEquals(
                    VulkanTerrainGpuBackend.celtium$immediateBranchKey(format, key, CURRENT_PASS),
                    VulkanTerrainGpuBackend.celtium$immediateBranchKey(format, key, CURRENT_PASS));
        }
    }

    /** Folding the pass in must not merge states the state key kept apart. */
    @Test
    void differentStatesStaySeparatedUnderOnePass() {
        assertNotEquals(
                VulkanTerrainGpuBackend.celtium$immediateBranchKey(2, 111L, CURRENT_PASS),
                VulkanTerrainGpuBackend.celtium$immediateBranchKey(2, 222L, CURRENT_PASS));
    }

    /**
     * The fold runs under the line bit, saved and restored the way {@code celtium$pipelineKey}
     * folds: a line batch keeps its flag (and its cache), a triangle batch never gains it.
     */
    @Test
    void theLineBitSurvivesThePassFold() {
        long line = 424242L | VulkanTerrainGpuBackend.LINE_KEY_BIT;
        long foldedLine = VulkanTerrainGpuBackend.celtium$immediateBranchKey(1, line, CURRENT_PASS);
        assertTrue((foldedLine & VulkanTerrainGpuBackend.LINE_KEY_BIT) != 0,
                "a line batch's branch key keeps the line bit");
        long foldedTriangle = VulkanTerrainGpuBackend.celtium$immediateBranchKey(1, 424242L, CURRENT_PASS);
        assertFalse((foldedTriangle & VulkanTerrainGpuBackend.LINE_KEY_BIT) != 0,
                "a triangle batch's branch key never gains the line bit");
        assertNotEquals(foldedLine, foldedTriangle,
                "the line and triangle entries stay separate under one pass");
    }

    /**
     * Every branch cache read - the resolver's exact read, its standard (fallback) read and the
     * synchronous build path - goes through the one pass-keyed shape, so no read can hand back
     * an entry another pass built.
     */
    @Test
    void everyBranchCacheReadUsesThePassKeyedShape() throws IOException {
        String source = read(BACKEND);
        assertTrue(source.contains("static long celtium$immediateBranchKey(int format, long key, long pass)"),
                "the branch key takes the render pass");
        assertEquals(0, count(source, "celtium$immediateBranchKey(format, key)"),
                "no two-argument branch key read remains");
        assertEquals(2, count(source, "celtium$immediateBranchKey(format, key, pass)"),
                "the exact reads - the resolver's and the synchronous build path's - are"
                        + " pass-keyed (the standard read is the multi-line third call)");
        int resolve = source.indexOf("private Object celtium$resolveImmediatePipeline");
        int prewarm = source.indexOf("private void celtium$requestImmediatePipelinePrewarm", resolve);
        assertTrue(resolve >= 0 && prewarm > resolve, "the resolver body is present");
        String body = source.substring(resolve, prewarm);
        assertTrue(body.contains("long branchKey = celtium$immediateBranchKey(format, key, pass);"),
                "the resolver's exact read is pass-keyed");
        assertTrue(body.contains("celtium$immediateStandardKey(format, key, state), pass)"),
                "the resolver's standard (fallback) read is pass-keyed with the same shape");
    }

    /**
     * The instrument the verification leg reads (seat record section 6.3): the frame format
     * decision's move is printed once, from the one latch helper both latch paths route through,
     * so a leg can establish the session's latched format and whether a provisional window
     * existed at all.
     */
    @Test
    void theFormatMoveIsPrintedOnceAtTheLatch() throws IOException {
        String source = read(CONTEXT);
        assertEquals(1, count(source, "System.out.println(\"[Celtium] CELTIUM_FRAME_FORMAT_MOVED"),
                "the move is printed at exactly one site");
        assertEquals(1, count(source, "celtium$latchFrameColorFormat(decided)"),
                "the decision's own latch routes through the helper");
        assertEquals(1, count(source, "celtium$latchFrameColorFormat(format)"),
                "the frame target's note routes through the same helper");
        int helper = source.indexOf("private void celtium$latchFrameColorFormat(int decided)");
        assertTrue(helper >= 0, "the latch helper is present");
        assertTrue(source.substring(helper, helper + 600).contains("CELTIUM_FRAME_FORMAT_MOVED"),
                "the print sits inside the latch helper");
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
