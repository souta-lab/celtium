package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;
import org.lwjgl.vulkan.VK10;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The rule that decides whether a terrain pass draws into the bound framebuffer's chain pair instead of the frame image
 * (item 332, {@code ~/workspace/celtium-plan/ITEM332-DESIGN-REVIEW-2026-11-18.md}).
 * <p>
 * The defect it guards against: an Iris shadow pass' terrain was drawn into the frame image while the shadow
 * framebuffer's pair borrowed the shared depth image that the pre-translucent depth copy reads, so the copy was served
 * from an image the shadow pass never wrote ({@code SHADOW-NATIVE-DESIGN} §6, legs 14/15/18). The route that closes it
 * must be reachable for exactly one state - a non-main identity whose pair exists at the depth texture's extent and
 * borrows its shared depth image - and unreachable for every other one, because two of those other states are measured
 * regressions: main-target passes belong to the frame image, and identity 0 is the window.
 * <p>
 * What this proves and what it does not: it pins the decision table, including the two claims the item's design review
 * states as its safety argument - that the switch being off is off (the first condition, so the default build records
 * none of this) and that a switch-on build still refuses every state but the measured one. It proves nothing about a
 * frame: no barrier is recorded here, no pair is created, no pass is begun, and a green test here is not evidence that
 * a shadow pass was routed or that any depth reached a copy. Those are the acceptance leg's questions.
 */
class TerrainDepthTargetRouteTest {

    /** The measured state: the shadow framebuffer's pair, 2048x2048, borrowing its depth texture's image. */
    private static boolean routed(boolean enabled, boolean currentIsMain, long identity, boolean pairExists,
            int pairWidth, int pairHeight, int depthWidth, int depthHeight, boolean depthShared, int colors) {
        return VulkanTerrainGpuBackend.celtium$routeTerrainToDepthTarget(enabled, currentIsMain, identity,
                pairExists, pairWidth, pairHeight, depthWidth, depthHeight, depthShared, colors);
    }

    private static final long SHADOW_IDENTITY = 0x2f16b5c4L;

    @Test
    void theMeasuredShadowStateIsTheOnlyStateThatRoutes() {
        assertTrue(routed(true, false, SHADOW_IDENTITY, true, 2048, 2048, 2048, 2048, true, 1),
                "a non-main identity whose pair borrows its depth texture's image at the same extent is the route");
    }

    @Test
    void theSwitchOffIsOffWhateverTheStateIs() {
        // The first condition, and the whole of the default build's guarantee: the shipped build does not run the
        // lookups, the barriers or the census, because the only call site asks this function after the switch check and
        // this answer is false. A regression here would route the shadow pass in every build.
        assertFalse(routed(false, false, SHADOW_IDENTITY, true, 2048, 2048, 2048, 2048, true, 1));
        assertEquals("disabled", VulkanTerrainGpuBackend.celtium$depthTargetRefusal(false, false, SHADOW_IDENTITY,
                true, 2048, 2048, 2048, 2048, true, 1));
    }

    @Test
    void theMainTargetAndTheWindowNeverRoute() {
        // Main: the game's own framebuffer. Its terrain must stay in the frame image, which is what the whole renderer
        // is built around.
        assertFalse(routed(true, true, 0x1L, true, 2048, 2048, 2048, 2048, true, 1));
        assertEquals("main", VulkanTerrainGpuBackend.celtium$depthTargetRefusal(true, true, 0x1L, true, 2048, 2048,
                2048, 2048, true, 1));
        // Identity 0: GL name 0 is the window (see the routing note in recordImmediateOnlyPass); its draws already
        // land in the frame image and there is no framebuffer to route for.
        assertFalse(routed(true, false, 0L, true, 2048, 2048, 2048, 2048, true, 1));
        assertEquals("window", VulkanTerrainGpuBackend.celtium$depthTargetRefusal(true, false, 0L, true, 2048, 2048,
                2048, 2048, true, 1));
    }

    @Test
    void aPairThatDoesNotExistYetKeepsTheFramePath() {
        // Item 333's registration creates the pair; a pass that arrives before it (or after the pair was retired by a
        // depth-texture reallocation) has nothing to draw into. R9 of the design review: counted, not silent.
        assertFalse(routed(true, false, SHADOW_IDENTITY, false, 0, 0, 2048, 2048, true, 0));
        assertEquals("no-pair", VulkanTerrainGpuBackend.celtium$depthTargetRefusal(true, false, SHADOW_IDENTITY, false,
                0, 0, 2048, 2048, true, 0));
    }

    @Test
    void aMultiTargetPairIsRefusedByName() {
        // Render-pass compatibility compares the attachment count, so the frame pass (one colour target) cannot attach
        // a pair holding two. The design review's R5: a named refusal here rather than a pass that would be invalid.
        assertFalse(routed(true, false, SHADOW_IDENTITY, true, 2048, 2048, 2048, 2048, true, 2));
        assertEquals("mrt", VulkanTerrainGpuBackend.celtium$depthTargetRefusal(true, false, SHADOW_IDENTITY, true,
                2048, 2048, 2048, 2048, true, 2));
    }

    @Test
    void aPairWhoseDepthIsNotSharedIsNotTheRoute() {
        // The item's whole subject: the pair has to be rendering into the shared depth image for this framebuffer's
        // depth texture. A pair with its own private depth writes an image nothing copies from or samples, and routing
        // it would move the terrain out of the frame image for no measurement.
        assertFalse(routed(true, false, SHADOW_IDENTITY, true, 2048, 2048, 2048, 2048, false, 1));
        assertEquals("not-shared", VulkanTerrainGpuBackend.celtium$depthTargetRefusal(true, false, SHADOW_IDENTITY,
                true, 2048, 2048, 2048, 2048, false, 1));
    }

    @Test
    void theExtentComparedIsThePairsNotTheWindows() {
        // The extent that separates this route from the existing bound-target experiment: a 2048x2048 pair and a
        // 2048x2048 depth texture route even though the window is 1280x720 - no window extent is an argument here at
        // all. A pair at any other size is a different framebuffer, and the framebuffer cache would build a handle at
        // the wrong extent (VUID-VkFramebufferCreateInfo-flags-04533/04534, the 256x256 pair's own history).
        assertFalse(routed(true, false, SHADOW_IDENTITY, true, 1280, 720, 2048, 2048, true, 1));
        assertEquals("extent", VulkanTerrainGpuBackend.celtium$depthTargetRefusal(true, false, SHADOW_IDENTITY, true,
                1280, 720, 2048, 2048, true, 1));
        // A depth texture whose level 0 was never recorded has no extent to compare; that is not a match by default.
        assertFalse(routed(true, false, SHADOW_IDENTITY, true, 2048, 2048, 0, 0, true, 1));
        assertEquals("extent", VulkanTerrainGpuBackend.celtium$depthTargetRefusal(true, false, SHADOW_IDENTITY, true,
                2048, 2048, 0, 0, true, 1));
    }

    @Test
    void theRefusalTableAnswersOnlyNullForTheRoutedState() {
        // The census line and this predicate share one table, so the only state that answers true must be the only one
        // that answers null. This is the check that the two cannot drift apart.
        assertNull(VulkanTerrainGpuBackend.celtium$depthTargetRefusal(true, false, SHADOW_IDENTITY, true,
                2048, 2048, 2048, 2048, true, 1));
    }

    @Test
    void aClearOfASharedDepthHappensOncePerFramePerDepthKey() {
        // The shadow family's framebuffers share one depth texture, so the depth clear is keyed by the depth image and
        // the frame, not by the pair that is asked: the second member's pair (created later in the same frame, and so
        // not acquired yet) would otherwise clear the depth the first member's pass just wrote, and the copy that
        // follows would serve a cleared image (design review §2.4 and its R1).
        final long depthKey = 0x1a2b3c4d5e6fL;
        assertTrue(GllessFrameTarget.celtium$sharedDepthClearNeeded(depthKey, -1L, 41L),
                "a depth image that has never been cleared this frame may be cleared");
        assertFalse(GllessFrameTarget.celtium$sharedDepthClearNeeded(depthKey, 41L, 41L),
                "the same depth image, already cleared in this frame, is refused for the second identity");
        assertTrue(GllessFrameTarget.celtium$sharedDepthClearNeeded(depthKey, 41L, 42L),
                "the next frame may clear it again - the record is per frame, not once ever");
        assertFalse(GllessFrameTarget.celtium$sharedDepthClearNeeded(0L, -1L, 41L),
                "no shared depth key means there is nothing to guard; the caller only asks when the pair borrows one");
    }

    @Test
    void aBarrierFromAnUndefinedLayoutCarriesNoSourceAccess() {
        // VUID-vkCmdPipelineBarrier-pImageMemoryBarriers-02819: srcAccessMask must be 0 when oldLayout is UNDEFINED.
        // Item 333's first shared-depth leg hit it, and the routed acquire is the second place a pair's barrier names a
        // layout that pair did not set itself (the store's record for a borrowed depth), so the one table both use is
        // pinned here. The other three answers are the ones that keep content: a transition preserves the image, and a
        // barrier that names no source access at all leaves the previous writes unavailable without discarding them.
        assertEquals(0, GllessFrameTarget.celtium$layoutSourceAccess(VK10.VK_IMAGE_LAYOUT_UNDEFINED));
        assertTrue((GllessFrameTarget.celtium$layoutSourceAccess(VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL)
                & VK10.VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT) != 0,
                "a depth image left in its attachment layout holds attachment writes");
        assertTrue((GllessFrameTarget.celtium$layoutSourceAccess(VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
                & VK10.VK_ACCESS_SHADER_READ_BIT) != 0,
                "a sampled image holds shader reads");
        assertTrue((GllessFrameTarget.celtium$layoutSourceAccess(VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL)
                & VK10.VK_ACCESS_TRANSFER_WRITE_BIT) != 0,
                "the clear's transfer layout holds transfer writes");
    }

    @Test
    void theLayoutNameTheCopyLinePrintsDistinguishesTheTwoOutcomes() {
        // The acceptance leg's A3 reads this field: served with DEPTH_RENDER_LAYOUT means the copy read the depth the
        // pass drew, and UNDEFINED means the barrier discarded what it read. The two names have to be different and
        // stable, and the ones nothing else produces must still be named rather than dropped.
        assertEquals("DEPTH_RENDER_LAYOUT",
                VulkanTerrainGpuBackend.celtium$layoutName(VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL));
        assertEquals("UNDEFINED", VulkanTerrainGpuBackend.celtium$layoutName(VK10.VK_IMAGE_LAYOUT_UNDEFINED));
        assertEquals("GENERAL", VulkanTerrainGpuBackend.celtium$layoutName(VK10.VK_IMAGE_LAYOUT_GENERAL));
        assertEquals("layout-9999", VulkanTerrainGpuBackend.celtium$layoutName(9999));
    }
}
