package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_GENERAL;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;

/**
 * The submission-token rule for a shared image's layout (audit N05), exercised without a device.
 * <p>
 * The property under test is the one the audit found missing: a barrier that is recorded into a command buffer must
 * not move the image's recorded layout until the submission carrying it has been accepted. The ledger these calls
 * drive is the same one {@code VulkanGlTexture.transitionForeignLayout} uses; only the barrier recording itself needs
 * a device, and that is covered by the validation-clean runs.
 * <p>
 * The failure this test would catch: restoring the old behaviour where a transition becomes fact at recording time.
 * Then {@code abandonLayoutRecording} would leave the fact at {@code SHADER_READ}, the next
 * {@code ensureSampledLayout} would skip a barrier the image still needs, and the second assertion below would fail
 * with GENERAL expected against SHADER_READ.
 */
class ForeignLayoutLedgerTest {

    private static final long IMAGE = 0xABCD0000L;

    @Test
    void anAbandonedRecordingDoesNotMoveTheLayoutAndTheNextBarrierReRecords() {
        long commandBuffer = 0x1000L;
        VulkanGlTexture.declareForeignImage(IMAGE, VK_IMAGE_LAYOUT_GENERAL);
        assertEquals(VK_IMAGE_LAYOUT_GENERAL, VulkanGlTexture.foreignLayoutOf(IMAGE));

        VulkanGlTexture.beginLayoutRecording(commandBuffer, "test frame 1");
        int from = record(commandBuffer, IMAGE, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
        assertEquals(VK_IMAGE_LAYOUT_GENERAL, from);
        // Within the recording the image will be sampled: a second transition must chain from that, not from the
        // confirmed layout, because both barriers execute in one submission, in order.
        assertEquals(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
                VulkanGlTexture.recordingLayoutOf(commandBuffer, IMAGE));

        // The submission was refused and the command buffer dropped: the outline the recorded barrier would have
        // moved is still in the layout it was in, so the fact must not have moved either.
        VulkanGlTexture.abandonLayoutRecording(commandBuffer, "injected submit refusal");
        assertEquals(VK_IMAGE_LAYOUT_GENERAL, VulkanGlTexture.foreignLayoutOf(IMAGE),
                "an abandoned recording must roll its transitions back, or the next ensure* skips a needed barrier");

        // The next successful frame must record the barrier again, from the layout the image is really in.
        VulkanGlTexture.beginLayoutRecording(commandBuffer, "test frame 2");
        int again = record(commandBuffer, IMAGE, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
        assertEquals(VK_IMAGE_LAYOUT_GENERAL, again,
                "after an abandonment the re-recorded barrier must name the real current layout");
        VulkanGlTexture.commitLayoutRecording(commandBuffer);
        assertEquals(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL, VulkanGlTexture.foreignLayoutOf(IMAGE),
                "an accepted submission is what makes the transition fact");
    }

    /**
     * The two halves a production call performs around the barrier recording: decide first, register only once the
     * barrier is in the command buffer. No barrier is recorded here - that needs a device - but the ledger's half of
     * the contract is the same code the renderer runs.
     */
    private static int record(long commandBuffer, long image, int wanted) {
        int from = VulkanGlTexture.foreignTransitionFrom(commandBuffer, image, wanted);
        if (from >= 0) {
            VulkanGlTexture.noteForeignTransition(commandBuffer, image, from, wanted, null);
        }
        return from;
    }

    @Test
    void anAcceptedRecordingMakesItsLastTransitionFactAndSkipsTheNextCall() {
        long commandBuffer = 0x2000L;
        long image = 0xABCD0001L;
        VulkanGlTexture.declareForeignImage(image, VK_IMAGE_LAYOUT_GENERAL);

        VulkanGlTexture.beginLayoutRecording(commandBuffer, "test frame");
        assertEquals(VK_IMAGE_LAYOUT_GENERAL, record(commandBuffer, image,
                VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL));
        // The same layout asked for twice in one recording needs no second barrier: one submission carries both.
        assertEquals(-1, record(commandBuffer, image, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL));
        VulkanGlTexture.commitLayoutRecording(commandBuffer);
        assertEquals(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL, VulkanGlTexture.foreignLayoutOf(image));

        VulkanGlTexture.beginLayoutRecording(commandBuffer, "test frame 2");
        assertEquals(-1, record(commandBuffer, image, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL),
                "the accepted transition is fact, so the next call records nothing");
        assertEquals(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL, record(commandBuffer, image,
                VK_IMAGE_LAYOUT_GENERAL),
                "a hand-back records from the sampled layout the accepted submission left behind");
        VulkanGlTexture.abandonLayoutRecording(commandBuffer, "end of test");
    }

    @Test
    void aNewRecordingOnTheSameHandleRollsBackTheUnconfirmedOne() {
        long commandBuffer = 0x3000L;
        long image = 0xABCD0002L;
        VulkanGlTexture.declareForeignImage(image, VK_IMAGE_LAYOUT_GENERAL);

        VulkanGlTexture.beginLayoutRecording(commandBuffer, "frame that never confirms");
        record(commandBuffer, image, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
        // The same handle comes back for the next recording without a commit: that is the reset/recovery case, and
        // the transitions of the dropped recording must not survive it.
        VulkanGlTexture.beginLayoutRecording(commandBuffer, "next frame");
        assertEquals(VK_IMAGE_LAYOUT_GENERAL, VulkanGlTexture.foreignLayoutOf(image));
        VulkanGlTexture.abandonLayoutRecording(commandBuffer, "end of test");
    }

    @Test
    void oneImageHasOneFactSharedByItsWrappers() {
        long image = 0xABCD0003L;
        VulkanGlTexture.ForeignLayoutState first = VulkanGlTexture.declareForeignImage(image,
                VK_IMAGE_LAYOUT_GENERAL);
        long commandBuffer = 0x4000L;
        VulkanGlTexture.beginLayoutRecording(commandBuffer, "test frame");
        record(commandBuffer, image, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
        VulkanGlTexture.commitLayoutRecording(commandBuffer);

        // A second wrapper of the same VkImage - an adopted target under another GL name - must not hold a private
        // belief that would let it record a barrier with the wrong oldLayout.
        VulkanGlTexture.ForeignLayoutState second = VulkanGlTexture.declareForeignImage(image,
                VK_IMAGE_LAYOUT_GENERAL);
        assertSame(first, second, "the wrappers of one image must share one layout fact");
        assertEquals(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL, VulkanGlTexture.foreignLayoutOf(image),
                "a later declaration must not overwrite the recorded handshake");
        assertNotEquals(org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_UNDEFINED,
                VulkanGlTexture.foreignLayoutOf(image));
    }

    @Test
    void anUnboundTransitionKeepsThePreN05ContractAndSaysSo() {
        // No begin/commit around this call: the caller has not adopted the submission-token API, and the ledger must
        // keep the old semantics (the fact moves at recording time) rather than leaving the image's layout unknown.
        long commandBuffer = 0x5000L;
        long image = 0xABCD0004L;
        VulkanGlTexture.declareForeignImage(image, VK_IMAGE_LAYOUT_GENERAL);
        boolean[] unbound = new boolean[1];
        int from = VulkanGlTexture.foreignTransitionFrom(commandBuffer, image,
                VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
        VulkanGlTexture.noteForeignTransition(commandBuffer, image, from,
                VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL, unbound);
        assertEquals(VK_IMAGE_LAYOUT_GENERAL, from);
        assertTrue(unbound[0], "a transition recorded with no open recording is reported as unbound");
        assertEquals(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL, VulkanGlTexture.foreignLayoutOf(image),
                "unbound transitions keep the pre-N05 behaviour: the fact follows the recording");
        VulkanGlTexture.abandonLayoutRecording(commandBuffer, "end of test");
    }

}
