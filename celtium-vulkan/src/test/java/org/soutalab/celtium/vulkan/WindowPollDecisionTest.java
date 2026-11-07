package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The rule that decides whether the end of a window polls the frame slots.
 * <p>
 * A window's submission has three answers ({@link FrameCoordinator.Submission}): {@code SKIPPED} (the device is
 * stalled, nothing was recorded), {@code RECORDED} (the batched path kept the segment in the frame's own command
 * buffer, so the queue was given nothing) and {@code QUEUED} (the segment reached the queue). Only the last one can
 * have changed what a poll would see, so only it polls.
 * <p>
 * What these tests prove: the rule. They prove nothing about a frame - a green test here is not evidence that a poll
 * was skipped, that the skipped poll would have confirmed nothing, or that the deferred confirmation still arrives.
 */
class WindowPollDecisionTest {

    @Test
    void aQueuedSubmissionPolls() {
        assertTrue(VulkanTerrainGpuBackend.celtium$windowPollNeeded(FrameCoordinator.Submission.QUEUED),
                "the segment reached the queue, so its fence is new information");
    }

    @Test
    void aRecordedSubmissionDoesNotPoll() {
        assertFalse(VulkanTerrainGpuBackend.celtium$windowPollNeeded(FrameCoordinator.Submission.RECORDED),
                "the batched path put nothing on the queue; the next frame's begin polls instead");
    }

    @Test
    void aSkippedSubmissionDoesNotPoll() {
        assertFalse(VulkanTerrainGpuBackend.celtium$windowPollNeeded(FrameCoordinator.Submission.SKIPPED),
                "a stalled device recorded nothing, so there is no fence to ask about");
    }

    @Test
    void everyAnswerIsDecided() {
        // The rule is a total function of the enum: a new answer must be classified rather than defaulted into a poll
        // or into silence. This is the assertion that fails when the enum grows.
        for (FrameCoordinator.Submission submission : FrameCoordinator.Submission.values()) {
            boolean polls = VulkanTerrainGpuBackend.celtium$windowPollNeeded(submission);
            if (submission == FrameCoordinator.Submission.QUEUED) {
                assertTrue(polls, submission + " must poll");
            } else {
                assertFalse(polls, submission + " must not poll");
            }
        }
    }
}
