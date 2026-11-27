package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * D1 L4 follow-up: the borrow extent gate. On the L4 leg (external resize series
 * 1280x720 -&gt; 1024x640 -&gt; 800x600 -&gt; 1280x720) one vkCreateFramebuffer was built with the
 * old generation's declared extent (1280x720) over the new generation's borrowed view (800x600),
 * drawing VUID-VkFramebufferCreateInfo-04533/04534 once each. The two extents have separate
 * authorities: the framebuffer seats declare the frame target's extent, while the target's colour
 * view under a live borrow is the borrowed swapchain image's view. The swapchain follows the
 * surface at the end of the present body (and the borrow seat itself creates the new generation
 * before borrowing), but the frame target is remade only on the draw path, with a resize during a
 * recording deferred - so the window between the two is structural. The gate closes it at the
 * borrow seat, in the format gate's shape (GllessFrameTarget): while the extents disagree the
 * frame keeps its own image and takes the composed present, and the borrow resumes on its own
 * once the target has been remade at the generation's extent.
 */
class BorrowExtentGateContractTest {

    /** A mismatch in either dimension refuses the borrow. */
    @Test
    void extentMismatchRefusesTheBorrow() {
        assertFalse(PresentSwapchain.celtium$extentAllowsBorrow(800, 600, 1280, 720),
                "a generation narrower and shorter than the target is refused");
        assertFalse(PresentSwapchain.celtium$extentAllowsBorrow(800, 720, 1280, 720),
                "a width-only mismatch is refused");
        assertFalse(PresentSwapchain.celtium$extentAllowsBorrow(1280, 600, 1280, 720),
                "a height-only mismatch is refused");
    }

    /** Equal extents let the borrow proceed. */
    @Test
    void extentMatchAllowsTheBorrow() {
        assertTrue(PresentSwapchain.celtium$extentAllowsBorrow(1280, 720, 1280, 720),
                "the steady-state extents borrow");
        assertTrue(PresentSwapchain.celtium$extentAllowsBorrow(800, 600, 800, 600),
                "the resized generation borrows once the target matches it");
    }

    /** The same generation extent is refused against the old target and allowed against the remade one. */
    @Test
    void theBorrowResumesOnceTheTargetIsRemadeAtTheGenerationExtent() {
        assertFalse(PresentSwapchain.celtium$extentAllowsBorrow(800, 600, 1280, 720),
                "the new generation against the not-yet-remade target is refused");
        assertTrue(PresentSwapchain.celtium$extentAllowsBorrow(800, 600, 800, 600),
                "the same generation against the remade target is allowed: the gate reads the"
                        + " target's current extent per attempt, so nothing has to be unlatched");
    }

    /** The gate stands at the borrow seat: after the full ensure, before the borrow is taken. */
    @Test
    void theGateStandsBetweenTheEnsureAndTheBorrow() throws IOException {
        String body = ensureBody();
        int ensure = body.indexOf("if (!ensureSwapchain())");
        assertTrue(ensure >= 0, "the borrow entry applies the full ensure");
        int gate = body.indexOf("celtium$extentAllowsBorrow(");
        assertTrue(gate > ensure,
                "the extent gate is consulted after the ensure has applied any pending rebuild,"
                        + " when the generation extent exists");
        int borrow = body.indexOf("target.borrowNextPresentImage(");
        assertTrue(borrow > gate, "the borrow is taken only after the gate has allowed it");
        int noted = body.indexOf("celtium$noteBorrowBypass(\"extent\")", gate);
        assertTrue(noted > gate && noted < borrow,
                "a refusal is noted under the extent reason before the borrow seat is reached");
    }

    /** The gate reads both extents live at the call, and follows the pre-existing bypass conditions. */
    @Test
    void theGateReadsBothExtentsLiveAndFollowsTheExistingConditions() throws IOException {
        String body = ensureBody();
        assertTrue(body.contains("backend.swapchain.width(), backend.swapchain.height(),"),
                "the generation extent is read from the swapchain at the call");
        assertTrue(body.contains("target.width(), target.height()"),
                "the target extent is read from the target at the call, so a remake is seen");
        int lastCondition = body.indexOf("celtium$noteBorrowBypass(\"main-sampled\")");
        assertTrue(lastCondition >= 0, "the pre-existing bypass conditions are present");
        int gate = body.indexOf("celtium$extentAllowsBorrow(");
        assertTrue(gate > lastCondition,
                "the extent gate follows the pre-existing bypass conditions");
    }

    private static String ensureBody() throws IOException {
        String source = Files.readString(findRepoFile(
                "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PresentSwapchain.java"));
        int start = source.indexOf("void celtium$ensureBorrowedFrameImage()");
        int end = source.indexOf("void runSwapchainProbe()", start);
        assertTrue(start >= 0 && end > start, "the borrow entry is present");
        return source.substring(start, end);
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
