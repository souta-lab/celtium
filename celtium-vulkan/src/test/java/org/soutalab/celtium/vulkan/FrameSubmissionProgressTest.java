package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FrameSubmissionProgressTest {
    @Test
    void aTargetClosesOnAnAcceptedRecordingOrAQueuedSubmissionButNotOnASkip() {
        // Stage 2 submit unification: no target carries an external semaphore packet a close could owe,
        // so the rule no longer branches on the target - RECORDED and QUEUED close, SKIPPED does not.
        assertTrue(FrameCoordinator.Submission.RECORDED.canCloseTarget());
        assertTrue(FrameCoordinator.Submission.QUEUED.canCloseTarget());
        assertFalse(FrameCoordinator.Submission.SKIPPED.canCloseTarget());
    }
}
