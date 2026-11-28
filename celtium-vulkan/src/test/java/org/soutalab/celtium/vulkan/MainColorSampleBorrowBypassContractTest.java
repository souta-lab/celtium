package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Blackness fix A: main-colour sampling against the Stage 1 present borrow.
 * The borrowed swapchain image is created without SAMPLED usage, and the
 * borrow bypass conditions originally covered only shader packs, adopted
 * post chains (ShaderGroup) and readbacks - so a chain that samples the
 * frame colour through {@code ModPrograms} (GTCEu's bloom) was handed the
 * borrowed image as a sampler input, and its blend-off pass into main
 * composited undefined content over the drawn world (VUID 00337/09600,
 * world-frame black fractions of 0.81-0.84 on the NH and SuSy legs).
 * The fix has three parts, and these contracts pin each one: the
 * publication entry for the {@code "frame-image"} route notes the demand
 * on a backend latch and refuses the one publication while a borrow is
 * live; the borrow decision bypasses on the latch from the next frame on;
 * and {@code ModPrograms} refuses the closed frame's blend-off pass into
 * main, whose inputs that frame are only the chain's cleared
 * intermediates.
 */
class MainColorSampleBorrowBypassContractTest {

    /** The frame-image route notes the sampling demand and closes the live-borrow frame. */
    @Test
    void theFrameImageRouteNotesTheDemandAndClosesOverALiveBorrow() throws IOException {
        String source = backendSource();
        int branch = source.indexOf("if (\"frame-image\".equals(route)) {");
        assertTrue(branch >= 0, "the frame-image publication branch is present");
        int branchEnd = source.indexOf("image = this.frameTarget.colorImage();", branch);
        assertTrue(branchEnd > branch, "the branch resolves the frame image after the closure");
        String body = source.substring(branch, branchEnd);
        assertTrue(body.contains("this.mainColorSampled = true;"),
                "the route notes the main-sampling demand on the backend latch");
        assertTrue(body.contains("hasLivePresentBorrow()"),
                "the closure is conditioned on a live present borrow");
        int closed = body.indexOf("this.mainColorSampledBorrowClosedFrame = this.frameId;");
        assertTrue(closed >= 0, "the closure records the frame it closed");
        assertTrue(body.indexOf("return false;", closed) > closed,
                "the publication is refused on the closed frame, after recording it");
    }

    /** The latch and the closed frame each have exactly one setting site: no path sets them halfway. */
    @Test
    void theLatchAndTheClosureEachHaveOneSettingSite() throws IOException {
        String source = backendSource();
        assertTrue(source.contains("boolean mainColorSampled;"),
                "the latch is a backend field beside the post chain executor");
        assertTrue(source.contains("long mainColorSampledBorrowClosedFrame = Long.MIN_VALUE;"),
                "the closed frame starts at the no-frame sentinel");
        assertEquals(1, source.split("this\\.mainColorSampled = true;", -1).length - 1,
                "one site notes the demand: the frame-image publication route");
        assertEquals(1, source.split("this\\.mainColorSampledBorrowClosedFrame = this\\.frameId;", -1).length - 1,
                "one site records a closed frame: the live-borrow refusal in the same route");
    }

    /** The borrow decision bypasses on the latch, after the pre-existing conditions. */
    @Test
    void theBorrowDecisionBypassesOnTheLatch() throws IOException {
        String source = Files.readString(findRepoFile(
                "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PresentSwapchain.java"));
        int readback = source.indexOf("celtium$noteBorrowBypass(\"readback\")");
        assertTrue(readback >= 0, "the readback bypass condition is present");
        int condition = source.indexOf("if (backend.mainColorSampled) {");
        assertTrue(condition > readback,
                "the main-sampled condition follows the pre-existing bypass conditions");
        int noted = source.indexOf("celtium$noteBorrowBypass(\"main-sampled\")", condition);
        assertTrue(noted > condition, "the bypass is noted under the main-sampled reason");
    }

    /** ModPrograms refuses the closed frame's blend-off pass into main. */
    @Test
    void modProgramsRefusesTheOpaqueMainPassOnTheClosedFrame() throws IOException {
        String source = Files.readString(findRepoFile(
                "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ModPrograms.java"));
        int guard = source.indexOf("b.mainColorSampledBorrowClosedFrame == b.frameId");
        assertTrue(guard >= 0, "the closed-frame guard is present in the prepare path");
        int descriptor = source.indexOf("long set = pipeline.samplersAndImages", guard);
        assertTrue(descriptor > guard, "the closure guard runs before descriptor allocation");
        String body = source.substring(guard, descriptor);
        assertTrue(body.contains("!like.celtium$colorBlend().blend()"),
                "the guard is scoped to blend-off passes");
        assertTrue(body.contains("b.currentFramebufferIdentity == b.targetRegistry.mainIdentity()"),
                "the guard is scoped to passes into the game's framebuffer");
        assertTrue(body.contains("celtium$modFailure"),
                "the guard refuses through the prepare path's failure channel");
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
