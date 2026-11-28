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
 * Blackness 09600 residual: the frame image's first-sample bootstrap.
 * The fixed-jar confirmation leg recovered the world (black fraction 0.008169, the fix A closure
 * and fix B re-publication both holding) but left one VUID-vkCmdDraw-None-09600, on frame 2 only:
 * the owned colour image had sat in {@code UNDEFINED} through a borrow-only stretch (title screens
 * under direct present), and the first frame the borrow bypass put main-colour sampling back on it,
 * a bloom pass sampled it ahead of the frame's own acquire submission - against a descriptor the
 * published wrapper had written with a standing {@code GENERAL} declaration, for an image no
 * submitted command buffer had ever transitioned. The chain-pair route already carries the
 * matching bootstrap ({@code initializeChainPairForSampling}); the frame-image route had none.
 * <p>
 * What these tests prove: the decision table of the initialization rule, and the wiring - the
 * publication route calls the bootstrap after its borrow closure and before it takes the image
 * handles, and the bootstrap itself keeps the borrow-lifetime discipline (it names the
 * borrow-aware image, never the owned field). They prove nothing about a frame: a green test
 * here is not evidence that a leg samples a transitioned image.
 */
class FrameImageSamplingInitContractTest {

    @Test
    void anUntouchedOwnedImageWithNoBorrowNeedsTheBootstrap() {
        // The frame-2 state: no borrow live (the bypass dropped it), and the owned image's tracked
        // layout is still the UNDEFINED it was created in.
        assertTrue(GllessFrameTarget.celtium$frameImageSamplingInitNeeded(false,
                        org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_UNDEFINED),
                "an owned image no submitted command buffer has transitioned must be initialized"
                        + " before a sampler is handed it");
    }

    @Test
    void aLiveBorrowNeverNeedsTheBootstrap() {
        // While a borrow is live the tracked layout describes the borrowed image, and the
        // publication entry refuses over a live borrow before this question is reached - the
        // owned image's turn never comes in this state, whatever the record says.
        assertFalse(GllessFrameTarget.celtium$frameImageSamplingInitNeeded(true,
                        org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_UNDEFINED),
                "a live borrow is the closure's state, not the bootstrap's");
        assertFalse(GllessFrameTarget.celtium$frameImageSamplingInitNeeded(true,
                        org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_GENERAL),
                "a live borrow never initializes the owned image");
    }

    @Test
    void aSettledImageNeedsNoBootstrap() {
        // Every frame after the first attach: the frame's own acquire has moved the image, so a
        // bootstrap would only add a submission. The rule is equality with UNDEFINED, not
        // "not GENERAL" - any other recorded layout is proof a transition already happened.
        assertFalse(GllessFrameTarget.celtium$frameImageSamplingInitNeeded(false,
                        org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_GENERAL),
                "the steady state pays nothing");
        assertFalse(GllessFrameTarget.celtium$frameImageSamplingInitNeeded(false,
                        org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL),
                "a mid-clear layout is still a transitioned image");
        assertFalse(GllessFrameTarget.celtium$frameImageSamplingInitNeeded(false,
                        org.lwjgl.vulkan.KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR),
                "a presented layout is a transitioned image, not an untouched one");
    }

    /** The publication route calls the bootstrap after its refusals and before it takes the handles. */
    @Test
    void theFrameImageRouteInitializesBeforeItTakesTheImage() throws IOException {
        String source = backendSource();
        int branch = source.indexOf("if (\"frame-image\".equals(route)) {");
        assertTrue(branch >= 0, "the frame-image publication branch is present");
        int branchEnd = source.indexOf("image = this.frameTarget.colorImage();", branch);
        assertTrue(branchEnd > branch, "the branch resolves the frame image at its end");
        String body = source.substring(branch, branchEnd);
        int init = body.indexOf("gllessTarget.initializeFrameImageForSampling();");
        assertTrue(init >= 0, "the route initializes the frame image before publishing it");
        int nullRefusal = body.indexOf("reason=frame-target-null");
        assertTrue(nullRefusal >= 0 && init > nullRefusal,
                "the bootstrap stands after the frame-target-null refusal, on a live target");
        int closure = body.indexOf("this.mainColorSampledBorrowClosedFrame = this.frameId;");
        assertTrue(closure >= 0 && init > closure,
                "the bootstrap stands after the live-borrow closure: it never runs over a borrow");
        assertEquals(1, source.split("initializeFrameImageForSampling\\(\\)", -1).length - 1,
                "one call site: the frame-image publication route");
    }

    /** The bootstrap is guarded by the rule and completes its own submission before publication. */
    @Test
    void theBootstrapIsGuardedByTheRuleAndSubmitsOnItsOwn() throws IOException {
        String source = Files.readString(findRepoFile(
                "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/GllessFrameTarget.java"));
        int method = source.indexOf("public void initializeFrameImageForSampling() {");
        assertTrue(method >= 0, "the bootstrap is a frame target method beside the pair's");
        int methodEnd = source.indexOf("\n    }", method);
        assertTrue(methodEnd > method, "the bootstrap body is delimited");
        String body = source.substring(method, methodEnd);
        assertTrue(body.contains(
                        "celtium$frameImageSamplingInitNeeded(hasLivePresentBorrow(), this.colorImageLayout)"),
                "the guard is the pinned rule, read from the live borrow state and the tracked layout");
        assertTrue(body.contains("this.context.beginCommands()"),
                "the bootstrap records into its own command buffer");
        assertTrue(body.contains(
                        "this.context.endCommands(command, \"initialize frame image before first sample\")"),
                "the bootstrap's submission completes before publication, like the pair's");
        assertTrue(body.contains(".image(colorImage())"),
                "the initial transition names the borrow-aware image");
        assertEquals(0, body.split("\\.image\\(this\\.colorImage\\)", -1).length - 1,
                "the bootstrap never names the owned image field in a barrier");
        assertEquals(0, body.split("vkCmdClearColorImage\\(command, this\\.colorImage,", -1).length - 1,
                "the bootstrap's clear never names the owned image field");
    }

    private static String backendSource() throws IOException {
        return Files.readString(findRepoFile(
                "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameCoordinator.java"));
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
