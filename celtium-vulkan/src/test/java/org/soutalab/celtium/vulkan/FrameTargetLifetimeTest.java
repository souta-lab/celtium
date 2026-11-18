package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The lifetime rules item 372 added to the frame-target path (issue #423), pinned as pure functions.
 * <p>
 * The fault on the reporting session was an access violation inside {@code vkCmdBeginRenderPass}, which is where the
 * driver first walks a framebuffer's attachments - and the audit found three ways a framebuffer cached over a
 * <em>previous</em> frame target could still be handed to that call: the frame cache keyed by render pass alone, a
 * recreate path that deleted the target's views after a release it never checked the result of, and a chain pair whose
 * generation did not move when its images were retired. Each rule is a function here so that a later edit cannot
 * quietly reverse it.
 * <p>
 * What these tests prove and what they do not: they prove the decision each rule makes for its inputs, including that
 * a stamp mismatch is a rebuild and not a reuse. They prove nothing about a device - that a mismatched entry is in
 * fact retired and recreated, and that the recreated handle is usable, is what the validation-run gates measure; the
 * reporting user's NVIDIA session is the acceptance this change is aimed at and it is not runnable here.
 */
class FrameTargetLifetimeTest {

    /** The frame target's colour and depth views, as the cache sees them (arbitrary non-zero distinct values). */
    private static final long COLOR_VIEW = 0x2000_0000_0000_0002L;
    private static final long DEPTH_VIEW = 0x3000_0000_0000_0003L;
    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;

    /** An entry built over the given frame target, at the given extent. */
    private static FramebufferCache.FrameEntry entry(long colorView, long depthView, int width, int height) {
        return new FramebufferCache.FrameEntry(0x4000_0000_0000_0004L, colorView, depthView, width, height);
    }

    /**
     * Hole 1: the entry a render pass is keyed by is only reusable while it still names the views and the extent the
     * caller is about to attach. It used to be reusable forever, so a target replacement whose framebuffer release did
     * not land left the old handle answering the next window.
     */
    @Test
    void anEntryBuiltOverTheSameAttachmentsIsReused() {
        FramebufferCache.FrameEntry entry = entry(COLOR_VIEW, DEPTH_VIEW, WIDTH, HEIGHT);
        assertTrue(FramebufferCache.celtium$frameEntryReusable(entry, COLOR_VIEW, DEPTH_VIEW, WIDTH, HEIGHT),
                "the same views at the same extent: the handle names exactly what the pass attaches");
    }

    /** The #423 shape: the frame target was replaced, so the pass' entry names views that no longer exist. */
    @Test
    void anEntryBuiltOverAnotherTargetsViewIsRebuilt() {
        FramebufferCache.FrameEntry entry = entry(COLOR_VIEW, DEPTH_VIEW, WIDTH, HEIGHT);
        // Either view moving is a different frame target, whatever the render pass says: the pass is compatible with
        // the *format and layout* of the attachments, not with their identity.
        assertFalse(FramebufferCache.celtium$frameEntryReusable(entry, COLOR_VIEW + 1, DEPTH_VIEW, WIDTH, HEIGHT),
                "the colour view moved: the cached framebuffer names a destroyed image view");
        assertFalse(FramebufferCache.celtium$frameEntryReusable(entry, COLOR_VIEW, DEPTH_VIEW + 1, WIDTH, HEIGHT),
                "the depth view moved");
        // The extent is part of the same fact: a framebuffer created at the old size cannot serve the new target's
        // render area, and Vulkan rejects the area rather than the attachments (VUID ...-04527 family).
        assertFalse(FramebufferCache.celtium$frameEntryReusable(entry, COLOR_VIEW, DEPTH_VIEW, WIDTH + 1, HEIGHT),
                "the width moved");
        assertFalse(FramebufferCache.celtium$frameEntryReusable(entry, COLOR_VIEW, DEPTH_VIEW, WIDTH, HEIGHT + 1),
                "the height moved");
    }

    /** A pass with no entry is built, which is the same branch a stamp mismatch takes - not a reuse of nothing. */
    @Test
    void noEntryIsNotAReuse() {
        assertFalse(FramebufferCache.celtium$frameEntryReusable(null, COLOR_VIEW, DEPTH_VIEW, WIDTH, HEIGHT),
                "a null entry must read as 'create one', never as a match");
    }

    /**
     * Hole 1's other half: {@code ensureFrameTarget} may only delete the old target when the release of the
     * framebuffers that name its views reported success. A release that could not settle leaves the cache holding the
     * old handles, so deleting the views is exactly the use-after-free the audit describes.
     */
    @Test
    void aTargetIsOnlyReplacedAfterItsFramebuffersWereFreed() {
        assertTrue(VulkanTerrainGpuBackend.celtium$frameTargetReplaceable(true),
                "the release landed: the old views are named by nothing and the target may be replaced");
        assertFalse(VulkanTerrainGpuBackend.celtium$frameTargetReplaceable(false),
                "the release was refused: the old target keeps drawing, and the recreation is retried on a later frame");
    }

    /**
     * Hole 3: retiring a pair's images must move the generation a cached framebuffer is stamped with, so the cache
     * reads it as stale whether or not the caller re-ensured the pair first. Creation alone used to move it, which
     * made reuse depend on the caller's ordering.
     */
    @Test
    void aRetirementMovesThePairGeneration() {
        long created = GllessFrameTarget.celtium$nextPairGeneration(0L);
        assertEquals(1L, created, "the first image set is generation 1");
        long retired = GllessFrameTarget.celtium$nextPairGeneration(created);
        assertNotEquals(created, retired,
                "the generation a framebuffer was built over must not survive the retirement of those images");
        assertTrue(retired > created, "the counter only ever moves forward within one frame target");
        // A session creates and retires pairs many times; the counter has to keep moving for each event.
        long previous = retired;
        for (int event = 0; event < 8; event++) {
            long next = GllessFrameTarget.celtium$nextPairGeneration(previous);
            assertTrue(next > previous, "event " + event + " did not move the counter");
            previous = next;
        }
    }

    /**
     * Hole 2's inert case: a world teardown that has no frame framebuffers must not touch the device at all - the
     * teardown runs on every world exit, including a session that never opened a window.
     */
    @Test
    void retiringAnEmptyFrameCacheDoesNothing() {
        // No device: the empty path never reads the context, which is what makes this the case a session that never
        // opened a window takes. The registry is real because isEmpty() asks it about the two pair slots.
        FramebufferCache cache = new FramebufferCache(null, new RenderTargetRegistry());
        assertTrue(cache.isEmpty(), "a fresh cache holds nothing");
        assertEquals(0, cache.retireFrameFramebuffers(), "nothing was held, so nothing is retired");
        assertTrue(cache.isEmpty());
    }
}
