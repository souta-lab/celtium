package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Source contracts for the R-06 swap-point ordering fix: a swapchain generation change (a
 * present-mode rebuild, an out-of-date rebuild, an extent rebuild) must not be applied before
 * the submissions of the frame holding the old generation's borrow - the present-time drain,
 * the unified frame buffer's finish and the present attempt itself - have all run. Applying it
 * at the head of the present body, as the inherited shape did, dropped the generation under
 * the live borrow: the drain and the present then submitted command buffers referencing the
 * destroyed generation's images (VUID-vkQueueSubmit-pCommandBuffers-00070), and the transition
 * frame's draws spanned two generations (VUID-vkCmdDrawIndexed-renderPass-02684). Both were
 * measured at one event per generation change on the vanilla flight legs.
 * <p>
 * The mechanism lives inside device-bound methods and cannot be driven on a CPU-only instance,
 * so the ordering is pinned by shape, the form {@code LandedFixGuardContractTest} established.
 * The design record is OVERHAUL-R06-DISPOSITION-2026-11-26.md section 2; the confirmation leg
 * (both VUIDs at zero events on the standard terrain flight leg) is the landing window's, not
 * this test's.
 */
class SwapchainRebuildOrderContractTest {

    private static final String PRESENT_SWAPCHAIN =
            "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PresentSwapchain.java";

    private static String read(String repoRelative) throws IOException {
        return Files.readString(findRepoFile(repoRelative));
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

    /**
     * The swap point's head ensure is the existence-only form, and the rebuild consumption sits
     * after the present call and before the next frame's borrow is taken.
     */
    @Test
    void theSwapPointConsumesRebuildsOnlyAfterItsPresentAttempt() throws IOException {
        String source = read(PRESENT_SWAPCHAIN);

        // The split itself: the full ensure consumes the verdicts before existence, and the
        // swap point's own ensure is the existence-only half of the same body.
        int full = source.indexOf("boolean ensureSwapchain() {");
        assertTrue(full >= 0, "the full ensure is declared");
        String fullBody = source.substring(full, full + 200);
        assertTrue(fullBody.contains("consumeSwapchainRebuildVerdicts();"),
                "the full ensure consumes the rebuild verdicts first");
        assertTrue(fullBody.contains("return ensureSwapchainExists(true);"),
                "the full ensure then assures existence, extent consumption included");
        int forPresent = source.indexOf("private boolean ensureSwapchainForPresent() {");
        assertTrue(forPresent >= 0, "the swap point's existence-only ensure is declared");
        assertTrue(source.substring(forPresent, forPresent + 200).contains("return ensureSwapchainExists(false);"),
                "the swap point's ensure assures existence without consuming a rebuild");

        int bodyStart = source.indexOf("private boolean celtium$presentFrameBody()");
        int bodyEnd = source.indexOf("private void dumpFrameIfRequested", bodyStart);
        assertTrue(bodyStart >= 0 && bodyEnd > bodyStart, "the present body is present");
        String body = source.substring(bodyStart, bodyEnd);
        assertTrue(body.contains("if (!ensureSwapchainForPresent())"),
                "the swap point's head ensure is the existence-only form");
        assertEquals(0, count(body, "if (!ensureSwapchain())"),
                "the full ensure must not gate the swap point again - it would consume the"
                        + " pending rebuild under the borrow this body presents");
        int present = body.indexOf("backend.swapchain.presentDirect(backend.frameTarget)");
        assertTrue(present >= 0, "the direct present call is present");
        int consume = body.indexOf("consumeSwapchainRebuild();");
        assertTrue(consume > present,
                "the rebuild consumption runs after the present attempt, not before it");
        int borrow = body.indexOf("celtium$ensureBorrowedFrameImage();");
        assertTrue(borrow > consume,
                "the next frame's borrow is taken after the consumption, so it borrows the new"
                        + " generation when a rebuild was consumed");
    }

    /**
     * The borrow side keeps the full ensure, applied before the borrow is taken: a generation
     * change consumed there can never span a live borrow, because the borrow does not exist yet.
     */
    @Test
    void theBorrowIsTakenOnlyAfterTheFullEnsureHasApplied() throws IOException {
        String source = read(PRESENT_SWAPCHAIN);
        int start = source.indexOf("void celtium$ensureBorrowedFrameImage()");
        int end = source.indexOf("void runSwapchainProbe()", start);
        assertTrue(start >= 0 && end > start, "the borrow entry is present");
        String body = source.substring(start, end);
        int ensure = body.indexOf("if (!ensureSwapchain())");
        assertTrue(ensure >= 0, "the borrow entry applies the full ensure");
        int borrow = body.indexOf("borrowNextPresentImage(");
        assertTrue(borrow > ensure,
                "the borrow is taken after the ensure has applied any pending rebuild");
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
