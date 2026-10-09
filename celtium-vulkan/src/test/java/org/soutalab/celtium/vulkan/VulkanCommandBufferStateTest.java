package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VulkanCommandBufferStateTest {
    @Test void allocatedHandleIsNotRecordingAndCannotDraw() {
        VulkanCommandBufferState state = new VulkanCommandBufferState(101);
        assertFalse(state.recording());
        assertThrows(IllegalStateException.class, () -> state.requireDraw(7));
        state.begin();
        assertThrows(IllegalStateException.class, () -> state.requireDraw(7));
        state.beginPass(7);
        state.requireDraw(7);
        assertThrows(IllegalStateException.class, () -> state.requireDraw(8));
        state.endPass(); state.end();
        assertThrows(IllegalStateException.class, () -> state.requireDraw(7));
    }
    @Test void twoBuffersHaveIndependentRecordingAndTargetOwnership() {
        VulkanCommandBufferState upload = new VulkanCommandBufferState(101);
        VulkanCommandBufferState frame = new VulkanCommandBufferState(202);
        upload.begin();
        assertFalse(frame.recording());
        frame.begin(); frame.beginPass(7);
        upload.end(); upload.submitted(); upload.completed();
        frame.requireDraw(7);
        assertTrue(frame.recording());
        frame.endPass(); frame.beginPass(8); frame.requireDraw(8); frame.endPass(); frame.end();
    }
    @Test void pendingResetAndReuseAreProhibitedUntilConfirmedCompletion() {
        VulkanCommandBufferState state = new VulkanCommandBufferState(202);
        state.begin(); state.beginPass(7); state.endPass(); state.end(); state.submitted();
        assertThrows(IllegalStateException.class, state::reset);
        assertThrows(IllegalStateException.class, state::begin);
        assertEquals(VulkanCommandBufferState.State.PENDING, state.state());
        state.completed(); state.reset(); state.begin();
        assertTrue(state.recording());
    }
    @Test void unsubmittedFailedAttemptCanResetButNotEndWithAPassOpen() {
        VulkanCommandBufferState state = new VulkanCommandBufferState(202);
        state.begin(); state.beginPass(7);
        assertThrows(IllegalStateException.class, state::end);
        state.reset(); state.begin(); state.end(); state.reset();
        assertEquals(VulkanCommandBufferState.State.INITIAL, state.state());
    }
}
