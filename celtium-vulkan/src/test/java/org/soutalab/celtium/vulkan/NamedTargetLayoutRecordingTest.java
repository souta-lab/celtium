package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;
import org.soutalab.celtium.vulkan.RenderTargetRegistry.NamedTargetSpec;
import org.soutalab.celtium.vulkan.RenderTargetRegistry.NamedTargetState;
import org.soutalab.celtium.vulkan.RenderTargetRegistry.TargetClearPolicy;
import org.soutalab.celtium.vulkan.RenderTargetRegistry.TargetFormatClass;
import org.soutalab.celtium.vulkan.RenderTargetRegistry.TargetSizing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_UNDEFINED;

/**
 * The three-phase layout discipline of named targets (audit-vulkan-core E2).
 * <p>
 * A named target's confirmed layout is the one the last accepted submission left the image in. A
 * transition recorded into a command buffer moves only the recorded head - the layout a later barrier
 * in the same recording chains from - until the submission that carries it is accepted and
 * {@code commitNamedTargetLayoutRecording} makes it fact. A recording that is abandoned, explicitly or
 * by the next recording opening on the same handle, leaves no trace. Before the fix,
 * {@code noteNamedTargetLayout} moved the confirmed layout at recording time, so a frame that was
 * recorded and then abandoned left the registry believing a layout the image was never in.
 */
class NamedTargetLayoutRecordingTest {

    private static final long CB_A = 0x1000L;
    private static final long CB_B = 0x2000L;

    private static RenderTargetRegistry registryWithAllocatedTarget() {
        RenderTargetRegistry registry = new RenderTargetRegistry();
        registry.declareNamedTarget(NamedTargetSpec.color(
                "scene_color", TargetFormatClass.RGBA8_UNORM, TargetSizing.scaled(1.0f),
                TargetClearPolicy.CLEAR_EACH_FRAME));
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
    void aRecordedTransitionDoesNotMoveTheConfirmedLayout() {
        RenderTargetRegistry registry = registryWithAllocatedTarget();
        registry.beginNamedTargetLayoutRecording(CB_A);
        registry.noteNamedTargetLayout(CB_A, "scene_color", VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);

        assertEquals(VK_IMAGE_LAYOUT_UNDEFINED, state(registry).layout());
        assertEquals(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL, state(registry).recordedLayout());
    }

    @Test
    void commitMakesTheRecordedTransitionFact() {
        RenderTargetRegistry registry = registryWithAllocatedTarget();
        registry.beginNamedTargetLayoutRecording(CB_A);
        registry.noteNamedTargetLayout(CB_A, "scene_color", VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);
        registry.commitNamedTargetLayoutRecording(CB_A);

        assertEquals(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL, state(registry).layout());
        assertEquals(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL, state(registry).recordedLayout());
    }

    @Test
    void abandonDropsTheRecordedTransition() {
        RenderTargetRegistry registry = registryWithAllocatedTarget();
        registry.beginNamedTargetLayoutRecording(CB_A);
        registry.noteNamedTargetLayout(CB_A, "scene_color", VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);
        registry.abandonNamedTargetLayoutRecording(CB_A);

        assertEquals(VK_IMAGE_LAYOUT_UNDEFINED, state(registry).layout());
        assertEquals(VK_IMAGE_LAYOUT_UNDEFINED, state(registry).recordedLayout());
    }

    @Test
    void openingANewRecordingOnTheSameHandleAbandonsThePrevious() {
        RenderTargetRegistry registry = registryWithAllocatedTarget();
        registry.beginNamedTargetLayoutRecording(CB_A);
        registry.noteNamedTargetLayout(CB_A, "scene_color", VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);

        // The buffer was reset and re-begun without its submission ever being accepted: opening the
        // new recording drops the previous one's notes, and committing the new (empty) recording
        // cannot resurrect them.
        registry.beginNamedTargetLayoutRecording(CB_A);
        assertEquals(VK_IMAGE_LAYOUT_UNDEFINED, state(registry).recordedLayout());
        registry.commitNamedTargetLayoutRecording(CB_A);
        assertEquals(VK_IMAGE_LAYOUT_UNDEFINED, state(registry).layout());
    }

    @Test
    void theLastNoteInARecordingIsBothTheHeadAndTheFact() {
        RenderTargetRegistry registry = registryWithAllocatedTarget();
        registry.beginNamedTargetLayoutRecording(CB_A);
        registry.noteNamedTargetLayout(CB_A, "scene_color", VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);
        registry.noteNamedTargetLayout(CB_A, "scene_color", VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);

        assertEquals(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL, state(registry).recordedLayout());
        registry.commitNamedTargetLayoutRecording(CB_A);
        assertEquals(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL, state(registry).layout());
    }

    @Test
    void aCommitOnOneBufferDoesNotSettleAnotherBuffersRecording() {
        RenderTargetRegistry registry = registryWithAllocatedTarget();
        registry.beginNamedTargetLayoutRecording(CB_A);
        registry.noteNamedTargetLayout(CB_A, "scene_color", VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);

        registry.commitNamedTargetLayoutRecording(CB_B);
        assertEquals(VK_IMAGE_LAYOUT_UNDEFINED, state(registry).layout());
        assertEquals(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL, state(registry).recordedLayout());

        registry.commitNamedTargetLayoutRecording(CB_A);
        assertEquals(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL, state(registry).layout());
    }

    @Test
    void publicationChainsFromTheRecordedHeadUntilCommit() {
        RenderTargetRegistry registry = registryWithAllocatedTarget();
        PublishedFrameTextures published = new PublishedFrameTextures();

        registry.beginNamedTargetLayoutRecording(CB_A);
        registry.noteNamedTargetLayout(CB_A, "scene_color", VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);
        published.syncNamedTargets(registry);
        assertEquals(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL,
                published.publishedNamed("scene_color").layout());
        assertEquals(VK_IMAGE_LAYOUT_UNDEFINED, state(registry).layout());

        registry.abandonNamedTargetLayoutRecording(CB_A);
        published.syncNamedTargets(registry);
        assertEquals(VK_IMAGE_LAYOUT_UNDEFINED, published.publishedNamed("scene_color").layout());
    }

    @Test
    void reallocationSupersedesRecordedTransitions() {
        RenderTargetRegistry registry = registryWithAllocatedTarget();
        registry.beginNamedTargetLayoutRecording(CB_A);
        registry.noteNamedTargetLayout(CB_A, "scene_color", VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);

        // The target is destroyed and recreated mid-recording: the recorded transition named the old
        // image and must not land on the new one at commit.
        NamedTargetState recreated = registry.noteNamedTargetAllocated(
                "scene_color", 102L, 202L, 302L, 64, 64, VK_IMAGE_LAYOUT_UNDEFINED);
        assertEquals(2L, recreated.generation());
        registry.commitNamedTargetLayoutRecording(CB_A);

        assertEquals(VK_IMAGE_LAYOUT_UNDEFINED, state(registry).layout());
        assertEquals(VK_IMAGE_LAYOUT_UNDEFINED, state(registry).recordedLayout());
    }

    @Test
    void commitAndAbandonWithoutARecordingAreNoOps() {
        RenderTargetRegistry registry = registryWithAllocatedTarget();
        registry.commitNamedTargetLayoutRecording(CB_A);
        registry.abandonNamedTargetLayoutRecording(CB_A);
        registry.beginNamedTargetLayoutRecording(CB_A);
        registry.commitNamedTargetLayoutRecording(CB_A);

        assertEquals(VK_IMAGE_LAYOUT_UNDEFINED, state(registry).layout());
        assertEquals(VK_IMAGE_LAYOUT_UNDEFINED, state(registry).recordedLayout());
    }
}
