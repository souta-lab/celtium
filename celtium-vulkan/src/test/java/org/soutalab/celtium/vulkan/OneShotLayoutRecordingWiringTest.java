package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;

/**
 * The one-shot command buffers' place in the shared-image layout discipline (audit N05), pinned as a source
 * contract plus the ledger behaviour it relies on.
 * <p>
 * The frame recording has always been token-bound: {@code VulkanTerrainGpuBackend.beginCommands} opens a
 * layout recording and {@code FrameCoordinator} commits it once the queue accepted the submission, so a
 * recorded transition is never mistaken for fact. {@code VulkanContext}'s one-shot buffers - the pool behind
 * texture copies and pair bootstrap operations - were not bound: no recording was opened for them, so every
 * transition noted into one fell into {@code noteForeignTransition}'s unbound fallback and was promoted to
 * the confirmed layout at record time. On the pack terrain route a chain pair's operations run on one-shot
 * buffers while the same shared depth image is sampled and attached through the frame recording several
 * times a frame, and the early promotion declared the depth attached
 * ({@code DEPTH_STENCIL_ATTACHMENT_OPTIMAL}) while it still sat in {@code SHADER_READ_ONLY_OPTIMAL} -
 * VUID-vkCmdDraw-None-09600 in both measured BSL-on runs, zero in the off run.
 * <p>
 * The fix binds the one-shot lifecycle at its three points: the recording opens in
 * {@code VulkanContext.beginCommands}, the synchronous path commits in {@code endCommands} after its submit
 * is accepted, and the deferred path commits each flushed entry in {@code celtium$flushBatchedSubmits} once
 * the batch submit succeeds (abandoning them when it fails). These pins fail if any of the three points
 * loses the binding again; the ledger semantics themselves are pinned behaviourally in
 * {@link ForeignLayoutLedgerTest}.
 */
class OneShotLayoutRecordingWiringTest {

    private static final String MAIN = "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/";

    @Test
    void beginCommandsOpensALayoutRecordingAfterTheBufferIsBegun() throws IOException {
        String context = Files.readString(findRepoFile(MAIN + "VulkanContext.java"));
        String begin = methodSlice(context, "public VkCommandBuffer beginCommands() {", 7000);
        int begun = begin.indexOf("check(result, \"vkBeginCommandBuffer\")");
        int opened = begin.indexOf("VulkanGlTexture.beginLayoutRecording(commandBuffer);");
        assertTrue(begun >= 0, "beginCommands still begins the buffer through the checked call");
        assertTrue(opened > begun,
                "a one-shot buffer gets its layout recording once it is begun, so its transitions stay pending");
    }

    @Test
    void endCommandsCommitsTheRecordingAfterItsSubmitIsAccepted() throws IOException {
        String context = Files.readString(findRepoFile(MAIN + "VulkanContext.java"));
        String end = methodSlice(context, "public void endCommands(VkCommandBuffer commandBuffer) {", 7000);
        int submitted = end.indexOf("check(vkQueueSubmit(this.queue, submit, fence), \"vkQueueSubmit\")");
        int committed = end.indexOf("VulkanGlTexture.commitLayoutRecording(commandBuffer);");
        assertTrue(submitted >= 0, "endCommands still submits through the checked call");
        assertTrue(committed > submitted,
                "the one-shot recording is committed at acceptance, not at record time");
    }

    @Test
    void theBatchFlushCommitsDeferredOneShotRecordingsAndAbandonsThemOnFailure() throws IOException {
        String context = Files.readString(findRepoFile(MAIN + "VulkanContext.java"));
        String flush = methodSlice(context, "public void celtium$flushBatchedSubmits() {", 5000);
        int submitted = flush.indexOf("submitBatch(batch, entries)");
        int committed = flush.indexOf("VulkanGlTexture.commitLayoutRecording(entry.commandBuffer);");
        int abandoned = flush.indexOf("VulkanGlTexture.abandonLayoutRecording(entry.commandBuffer,");
        assertTrue(submitted >= 0, "the flush still submits through submitBatch");
        assertTrue(committed > submitted,
                "a deferred one-shot recording is committed once its batch was accepted");
        assertTrue(abandoned > submitted,
                "a failed batch drops its recordings instead of letting them become fact");
    }

    @Test
    void aOneShotHandBackBecomesFactOnlyAtCommit() {
        long commandBuffer = 0x9600L;
        long image = 0x96000001L;
        // A shared depth image resting in the read layout after being sampled.
        VulkanGlTexture.declareForeignImage(image, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);

        // A pair operation on a one-shot buffer records the hand-back to the attachment layout, the way
        // Pair.depthLayout(command, layout) mirrors it through noteExternalForeignLayout.
        VulkanGlTexture.beginLayoutRecording(commandBuffer, "test one-shot");
        int from = VulkanGlTexture.foreignTransitionFrom(commandBuffer, image,
                VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL);
        assertEquals(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL, from);
        VulkanGlTexture.noteForeignTransition(commandBuffer, image, from,
                VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL, null);
        assertEquals(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL, VulkanGlTexture.foreignLayoutOf(image),
                "a recorded hand-back is not fact until the one-shot submission is accepted - declaring the "
                        + "attachment layout here is what the validation layer reported as 09600");

        VulkanGlTexture.commitLayoutRecording(commandBuffer);
        assertEquals(VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL, VulkanGlTexture.foreignLayoutOf(image),
                "the accepted one-shot submission is what makes the hand-back fact");
    }

    @Test
    void anAbandonedOneShotRecordingLeavesTheDepthInTheReadLayout() {
        long commandBuffer = 0x9601L;
        long image = 0x96000002L;
        VulkanGlTexture.declareForeignImage(image, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);

        VulkanGlTexture.beginLayoutRecording(commandBuffer, "test one-shot that never submits");
        int from = VulkanGlTexture.foreignTransitionFrom(commandBuffer, image,
                VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL);
        assertEquals(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL, from);
        VulkanGlTexture.noteForeignTransition(commandBuffer, image, from,
                VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL, null);
        VulkanGlTexture.abandonLayoutRecording(commandBuffer, "injected one-shot abandonment");
        assertEquals(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL, VulkanGlTexture.foreignLayoutOf(image),
                "a dropped one-shot recording must not move the depth out of the read layout");
    }

    /** A slice of one method, from its signature, long enough to cover the lines this pin reads. */
    private static String methodSlice(String source, String signature, int length) {
        int start = source.indexOf(signature);
        assertTrue(start >= 0, "cannot find " + signature);
        return source.substring(start, Math.min(source.length(), start + length));
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
}
