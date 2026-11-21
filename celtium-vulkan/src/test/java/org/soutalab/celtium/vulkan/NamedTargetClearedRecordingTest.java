package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;
import org.soutalab.celtium.vulkan.RenderTargetRegistry.NamedTargetSpec;
import org.soutalab.celtium.vulkan.RenderTargetRegistry.NamedTargetState;
import org.soutalab.celtium.vulkan.RenderTargetRegistry.TargetClearPolicy;
import org.soutalab.celtium.vulkan.RenderTargetRegistry.TargetFormatClass;
import org.soutalab.celtium.vulkan.RenderTargetRegistry.TargetSizing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_UNDEFINED;

/**
 * The three-phase cleared discipline of named targets (lanechat #1596, the cleared-frame sibling of
 * audit-vulkan-core E2).
 * <p>
 * A named target's confirmed cleared frame is the frame serial whose clear the last accepted submission
 * carried. A clear recorded into a command buffer moves only the recorded head - the value a clear
 * decision made while recording reads, so a pass later in the same recording sees an earlier pass's
 * clear - until the submission that carries it is accepted and the recording is committed. A recording
 * that is abandoned, explicitly or by the next recording opening on the same handle, leaves no trace.
 * Before the fix, {@code noteNamedTargetCleared} moved the confirmed cleared frame at recording time,
 * so a frame that was recorded and then abandoned left a {@code CLEAR_ON_CREATE} target believing it
 * had been cleared when it never had, and no later frame would ever clear it.
 */
class NamedTargetClearedRecordingTest {

    private static final long CB_A = 0x1000L;
    private static final long CB_B = 0x2000L;
    private static final long FRAME_41 = 41L;
    private static final long FRAME_42 = 42L;

    private static RenderTargetRegistry registryWithAllocatedTarget() {
        RenderTargetRegistry registry = new RenderTargetRegistry();
        registry.declareNamedTarget(NamedTargetSpec.color(
                "scene_color", TargetFormatClass.RGBA8_UNORM, TargetSizing.scaled(1.0f),
                TargetClearPolicy.CLEAR_ON_CREATE));
        registry.noteNamedTargetAllocated("scene_color", 101L, 201L, 301L, 64, 64,
                VK_IMAGE_LAYOUT_UNDEFINED);
        return registry;
    }

    private static NamedTargetState state(RenderTargetRegistry registry) {
        NamedTargetState state = registry.namedTargetState("scene_color");
        assertNotNull(state);
        return state;
    }

    @Test
    void aRecordedClearDoesNotMoveTheConfirmedClearedFrame() {
        RenderTargetRegistry registry = registryWithAllocatedTarget();
        registry.beginNamedTargetLayoutRecording(CB_A);
        registry.noteNamedTargetCleared(CB_A, "scene_color", FRAME_41);

        assertEquals(-1L, state(registry).clearedFrame());
        assertEquals(FRAME_41, state(registry).recordedClearedFrame());
    }

    @Test
    void commitMakesTheRecordedClearFact() {
        RenderTargetRegistry registry = registryWithAllocatedTarget();
        registry.beginNamedTargetLayoutRecording(CB_A);
        registry.noteNamedTargetCleared(CB_A, "scene_color", FRAME_41);
        registry.commitNamedTargetLayoutRecording(CB_A);

        assertEquals(FRAME_41, state(registry).clearedFrame());
        assertEquals(FRAME_41, state(registry).recordedClearedFrame());
    }

    @Test
    void abandonDropsTheRecordedClear() {
        RenderTargetRegistry registry = registryWithAllocatedTarget();
        registry.beginNamedTargetLayoutRecording(CB_A);
        registry.noteNamedTargetCleared(CB_A, "scene_color", FRAME_41);
        registry.abandonNamedTargetLayoutRecording(CB_A);

        assertEquals(-1L, state(registry).clearedFrame());
        assertEquals(-1L, state(registry).recordedClearedFrame());
    }

    @Test
    void openingANewRecordingOnTheSameHandleAbandonsThePreviousClear() {
        RenderTargetRegistry registry = registryWithAllocatedTarget();
        registry.beginNamedTargetLayoutRecording(CB_A);
        registry.noteNamedTargetCleared(CB_A, "scene_color", FRAME_41);

        // The buffer was reset and re-begun without its submission ever being accepted: opening the
        // new recording drops the previous one's clear, and committing the new (empty) recording
        // cannot resurrect it.
        registry.beginNamedTargetLayoutRecording(CB_A);
        assertEquals(-1L, state(registry).recordedClearedFrame());
        registry.commitNamedTargetLayoutRecording(CB_A);
        assertEquals(-1L, state(registry).clearedFrame());
    }

    @Test
    void aClearDecisionInsideTheRecordingSeesTheRecordedClear() {
        RenderTargetRegistry registry = registryWithAllocatedTarget();
        registry.beginNamedTargetLayoutRecording(CB_A);

        // The CLEAR_ON_CREATE decision reads the recorded head: negative before any clear is recorded,
        // satisfied by a clear recorded earlier in the same recording, and negative again once that
        // recording is abandoned - the next frame must clear after all.
        assertTrue(state(registry).recordedClearedFrame() < 0L);
        registry.noteNamedTargetCleared(CB_A, "scene_color", FRAME_41);
        assertTrue(state(registry).recordedClearedFrame() >= 0L);
        registry.abandonNamedTargetLayoutRecording(CB_A);
        assertTrue(state(registry).recordedClearedFrame() < 0L);
    }

    @Test
    void aLaterClearInTheSameRecordingSupersedesTheEarlier() {
        RenderTargetRegistry registry = registryWithAllocatedTarget();
        registry.beginNamedTargetLayoutRecording(CB_A);
        registry.noteNamedTargetCleared(CB_A, "scene_color", FRAME_41);
        registry.noteNamedTargetCleared(CB_A, "scene_color", FRAME_42);

        assertEquals(FRAME_42, state(registry).recordedClearedFrame());
        registry.commitNamedTargetLayoutRecording(CB_A);
        assertEquals(FRAME_42, state(registry).clearedFrame());
    }

    @Test
    void aCommitOnOneBufferDoesNotSettleAnotherBuffersClear() {
        RenderTargetRegistry registry = registryWithAllocatedTarget();
        registry.beginNamedTargetLayoutRecording(CB_A);
        registry.noteNamedTargetCleared(CB_A, "scene_color", FRAME_41);

        registry.commitNamedTargetLayoutRecording(CB_B);
        assertEquals(-1L, state(registry).clearedFrame());
        assertEquals(FRAME_41, state(registry).recordedClearedFrame());

        registry.commitNamedTargetLayoutRecording(CB_A);
        assertEquals(FRAME_41, state(registry).clearedFrame());
    }

    @Test
    void clearsShareTheLayoutRecordingsLifecycle() {
        RenderTargetRegistry registry = registryWithAllocatedTarget();
        registry.beginNamedTargetLayoutRecording(CB_A);
        registry.noteNamedTargetLayout(CB_A, "scene_color", VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);
        registry.noteNamedTargetCleared(CB_A, "scene_color", FRAME_41);
        registry.abandonNamedTargetLayoutRecording(CB_A);

        assertEquals(VK_IMAGE_LAYOUT_UNDEFINED, state(registry).layout());
        assertEquals(-1L, state(registry).clearedFrame());
        assertEquals(-1L, state(registry).recordedClearedFrame());

        registry.beginNamedTargetLayoutRecording(CB_A);
        registry.noteNamedTargetLayout(CB_A, "scene_color", VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);
        registry.noteNamedTargetCleared(CB_A, "scene_color", FRAME_41);
        registry.commitNamedTargetLayoutRecording(CB_A);

        assertEquals(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL, state(registry).layout());
        assertEquals(FRAME_41, state(registry).clearedFrame());
    }

    @Test
    void reallocationSupersedesRecordedClears() {
        RenderTargetRegistry registry = registryWithAllocatedTarget();
        registry.beginNamedTargetLayoutRecording(CB_A);
        registry.noteNamedTargetCleared(CB_A, "scene_color", FRAME_41);

        // The target is destroyed and recreated mid-recording: the recorded clear named the old
        // image and must not land on the new one at commit.
        NamedTargetState recreated = registry.noteNamedTargetAllocated(
                "scene_color", 102L, 202L, 302L, 64, 64, VK_IMAGE_LAYOUT_UNDEFINED);
        assertEquals(2L, recreated.generation());
        registry.commitNamedTargetLayoutRecording(CB_A);

        assertEquals(-1L, state(registry).clearedFrame());
        assertEquals(-1L, state(registry).recordedClearedFrame());
    }

    @Test
    void forgettingSupersedesRecordedClears() {
        RenderTargetRegistry registry = registryWithAllocatedTarget();
        registry.beginNamedTargetLayoutRecording(CB_A);
        registry.noteNamedTargetCleared(CB_A, "scene_color", FRAME_41);
        registry.forgetNamedTargetAllocation("scene_color");
        registry.commitNamedTargetLayoutRecording(CB_A);

        // Reallocating after the forget starts from a clean slate; the dropped recording's clear
        // must not land on the new allocation.
        registry.noteNamedTargetAllocated("scene_color", 102L, 202L, 302L, 64, 64,
                VK_IMAGE_LAYOUT_UNDEFINED);
        assertEquals(-1L, state(registry).clearedFrame());
        assertEquals(-1L, state(registry).recordedClearedFrame());
    }

    @Test
    void commitAndAbandonWithoutARecordingAreNoOps() {
        RenderTargetRegistry registry = registryWithAllocatedTarget();
        registry.commitNamedTargetLayoutRecording(CB_A);
        registry.abandonNamedTargetLayoutRecording(CB_A);
        registry.beginNamedTargetLayoutRecording(CB_A);
        registry.commitNamedTargetLayoutRecording(CB_A);

        assertEquals(-1L, state(registry).clearedFrame());
        assertEquals(-1L, state(registry).recordedClearedFrame());
    }
}
