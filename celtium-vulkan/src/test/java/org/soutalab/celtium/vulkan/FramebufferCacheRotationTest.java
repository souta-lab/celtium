package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The frame cache's bookkeeping under the direct present rotation (A→B overhaul Stage 1/2 gate).
 * <p>
 * The borrowed swapchain image rotates the frame target's colour view every frame, so
 * {@link FramebufferCache#frame} is asked for the same render pass over alternating views. The
 * inherited implementation answered same-pass requests from a last-entry memo: on a rotation the
 * memo still named the previous view's entry, its stamp mismatch retired the memo's handle while
 * removing the requested view's key, and the memo's entry stayed in the map under its own key with
 * a handle the retire queue was already destroying. Once another pass moved the memo on, a request
 * for that view took the map path, matched the stranded entry's stamp, and was handed the destroyed
 * handle - run-s1bgra2-agera's invalid VkFramebuffer at vkCmdBeginRenderPass, ~9 s into the world.
 * <p>
 * These tests drive {@code frame} itself through a creation/retirement seam (no device), so the
 * map's behaviour - not only the reuse predicate - is what is pinned: a rotation creates each
 * view's framebuffer once, retires nothing, and hands the same handle back on return; a stale
 * entry is retired only under its own key.
 */
class FramebufferCacheRotationTest {

    private static final long PASS = 0x1111_0000L;
    private static final long OTHER_PASS = 0x2222_0000L;
    private static final long VIEW_A = 0xA001_0000L;
    private static final long VIEW_B = 0xB002_0000L;
    private static final long DEPTH = 0xD003_0000L;
    private static final long DEPTH_MOVED = 0xD004_0000L;
    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;

    /** Synthetic handles in creation order, plus every handle the cache asked to retire. */
    private static final class FakeHandles {
        private long next = 0x5000_0000_0000_0001L;
        private int creations;
        private final List<Long> retired = new ArrayList<>();

        private long create(long renderPass, long colorView, long depthView, int width, int height) {
            this.creations++;
            return this.next++;
        }
    }

    private static FramebufferCache cache(FakeHandles fake) {
        return new FramebufferCache(new RenderTargetRegistry(), fake::create, fake.retired::add);
    }

    /** The rotation itself: each view's framebuffer is built once and answers again on return. */
    @Test
    void rotationReusesEachViewsFramebufferAndRetiresNothing() {
        FakeHandles fake = new FakeHandles();
        FramebufferCache cache = cache(fake);
        long a = cache.frame(PASS, VIEW_A, DEPTH, WIDTH, HEIGHT);
        long b = cache.frame(PASS, VIEW_B, DEPTH, WIDTH, HEIGHT);
        assertNotEquals(a, b, "each colour view gets its own framebuffer");
        assertEquals(a, cache.frame(PASS, VIEW_A, DEPTH, WIDTH, HEIGHT),
                "returning to view A hands back A's framebuffer, not a recreation");
        assertEquals(b, cache.frame(PASS, VIEW_B, DEPTH, WIDTH, HEIGHT),
                "returning to view B hands back B's framebuffer");
        assertEquals(2, fake.creations, "one creation per (pass, view) pair");
        assertTrue(fake.retired.isEmpty(),
                "a rotation retires nothing: both views are live for the generation's life");
    }

    /**
     * The crash's exact shape: another pass interleaves between rotations, so the returning request
     * is answered from the map rather than from any same-pass memo. The stranded-entry defect handed
     * this request the handle the retire queue had already destroyed.
     */
    @Test
    void anInterleavedPassDoesNotResurrectARetiredHandle() {
        FakeHandles fake = new FakeHandles();
        FramebufferCache cache = cache(fake);
        long a = cache.frame(PASS, VIEW_A, DEPTH, WIDTH, HEIGHT);
        cache.frame(PASS, VIEW_B, DEPTH, WIDTH, HEIGHT);
        cache.frame(OTHER_PASS, VIEW_A, DEPTH, WIDTH, HEIGHT);
        assertEquals(a, cache.frame(PASS, VIEW_A, DEPTH, WIDTH, HEIGHT),
                "after an interleaved pass, view A still answers with its live framebuffer");
        assertTrue(fake.retired.isEmpty(),
                "nothing was retired, so no answer can name a destroyed handle");
        assertEquals(3, fake.creations, "one creation per (pass, view) pair, the interleave included");
    }

    /**
     * A stamp mismatch retires the entry under the requested key only. The defect retired the
     * previous view's handle instead, leaving that view's entry in the map pointing at a destroyed
     * handle while the requested view's entry was dropped unretired.
     */
    @Test
    void aStaleEntryIsRetiredOnlyUnderItsOwnKey() {
        FakeHandles fake = new FakeHandles();
        FramebufferCache cache = cache(fake);
        long a1 = cache.frame(PASS, VIEW_A, DEPTH, WIDTH, HEIGHT);
        long b = cache.frame(PASS, VIEW_B, DEPTH, WIDTH, HEIGHT);
        long a2 = cache.frame(PASS, VIEW_A, DEPTH_MOVED, WIDTH, HEIGHT);
        assertNotEquals(a1, a2, "the depth view moved: view A's entry is rebuilt");
        assertEquals(List.of(a1), fake.retired,
                "exactly view A's stale handle is retired - view B's entry is untouched");
        assertEquals(b, cache.frame(PASS, VIEW_B, DEPTH, WIDTH, HEIGHT),
                "view B still answers with its original framebuffer");
        assertEquals(List.of(a1), fake.retired, "answering view B retires nothing further");
    }

    /** The plain rebuild case, without a rotation around it: same key, moved depth. */
    @Test
    void aDepthMoveUnderTheSameViewRetiresOnlyTheOldHandle() {
        FakeHandles fake = new FakeHandles();
        FramebufferCache cache = cache(fake);
        long first = cache.frame(PASS, VIEW_A, DEPTH, WIDTH, HEIGHT);
        long second = cache.frame(PASS, VIEW_A, DEPTH_MOVED, WIDTH, HEIGHT);
        assertNotEquals(first, second, "a moved depth view is a different framebuffer");
        assertEquals(List.of(first), fake.retired, "the old handle is retired exactly once");
        assertEquals(second, cache.frame(PASS, VIEW_A, DEPTH_MOVED, WIDTH, HEIGHT),
                "the rebuilt entry answers the next request");
        assertEquals(List.of(first), fake.retired, "the rebuild is not retired by its own reuse");
    }
}
