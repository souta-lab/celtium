package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FrameSubmissionProgressTest {
    @Test
    void nativeTargetCanCloseItsPassWithoutSubmittingTheStillOpenFrameBuffer() {
        assertTrue(FrameCoordinator.Submission.RECORDED.canCloseTarget(false));
        assertTrue(FrameCoordinator.Submission.QUEUED.canCloseTarget(false));
        assertFalse(FrameCoordinator.Submission.SKIPPED.canCloseTarget(false));
    }

    @Test
    void externalSemaphoreOwnerStillRequiresAnAcceptedSubmissionPacket() {
        assertFalse(FrameCoordinator.Submission.RECORDED.canCloseTarget(true));
        assertFalse(FrameCoordinator.Submission.SKIPPED.canCloseTarget(true));
        assertTrue(FrameCoordinator.Submission.QUEUED.canCloseTarget(true));
    }
}
