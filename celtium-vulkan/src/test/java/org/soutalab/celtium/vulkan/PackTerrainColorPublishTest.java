package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the chain-pair owner sharing and MRT slot-count preservation rules used by
 * {@code VulkanTerrainGpuBackend.celtium$tryRenderPackTerrainPass} so separate Iris FBOs attaching
 * the same {@code colortex0} (clear / solid / cutout / translucent) draw into one shared chain-pair
 * image without discarding previously allocated extra MRT slots.
 */
class PackTerrainColorPublishTest {

    @Test
    void reusesExistingPublishedNonMainOwnerWhenPairExistsSizeAndDepthMatch() {
        long bound = 0x1000000027bL;
        long existingOwner = 0x100000000e4L;
        long mainId = 0x3abe1c9eL;
        long gameId = 0x3abe1c9eL;
        assertEquals(existingOwner, VulkanTerrainGpuBackend.celtium$resolvePackTerrainDrawIdentity(
                bound, existingOwner, mainId, gameId, true, true, true));
    }

    @Test
    void keepsBoundIdentityWhenNoPublishedOwnerOrMismatchOrMainOrDepthDiffers() {
        long bound = 0x1000000027bL;
        long existingOwner = 0x100000000e4L;
        long mainId = 0x3abe1c9eL;
        assertEquals(bound, VulkanTerrainGpuBackend.celtium$resolvePackTerrainDrawIdentity(
                bound, Long.MIN_VALUE, mainId, mainId, true, true, true));
        assertEquals(bound, VulkanTerrainGpuBackend.celtium$resolvePackTerrainDrawIdentity(
                bound, 0L, mainId, mainId, true, true, true));
        assertEquals(bound, VulkanTerrainGpuBackend.celtium$resolvePackTerrainDrawIdentity(
                bound, mainId, mainId, mainId, true, true, true));
        assertEquals(bound, VulkanTerrainGpuBackend.celtium$resolvePackTerrainDrawIdentity(
                bound, existingOwner, mainId, mainId, false, true, true));
        assertEquals(bound, VulkanTerrainGpuBackend.celtium$resolvePackTerrainDrawIdentity(
                bound, existingOwner, mainId, mainId, true, false, true));
        // Must not hijack a depth-free composite FBO (0x0c1/0x0c2) that lacks the terrain pass's shared depth.
        assertEquals(bound, VulkanTerrainGpuBackend.celtium$resolvePackTerrainDrawIdentity(
                bound, existingOwner, mainId, mainId, true, true, false));
    }

    @Test
    void preservesExistingExtraColorSlotsWhenLaterPassTargetsFewerColors() {
        assertEquals(3, VulkanTerrainGpuBackend.celtium$packTerrainPairColorCount(2, 3));
        assertEquals(3, VulkanTerrainGpuBackend.celtium$packTerrainPairColorCount(3, 1));
        assertEquals(2, VulkanTerrainGpuBackend.celtium$packTerrainPairColorCount(1, 2));
        assertEquals(1, VulkanTerrainGpuBackend.celtium$packTerrainPairColorCount(0, 0));
    }

    @Test
    void neverPreTransformsModProgramBatches() {
        assertTrue(VulkanTerrainGpuBackend.celtium$allowPreTransform(false, false, true));
        assertFalse(VulkanTerrainGpuBackend.celtium$allowPreTransform(false, true, true));
        assertFalse(VulkanTerrainGpuBackend.celtium$allowPreTransform(true, false, true));
        assertFalse(VulkanTerrainGpuBackend.celtium$allowPreTransform(false, false, false));
    }

    @Test
    void unifiesDepthAttachedRawFbosByColorAndDepthWhileKeepingDepthFreeCompositeIsolated() {
        // gbuffers_entities (fbo 228), gbuffers_terrain (fbo 635), gbuffers_translucent (fbo 637)
        // all attach (colortex0=128, depthtex0=5) and must share one canonical chain pair.
        long entitiesId = RenderTargetRegistry.rawTargetIdentity(228, 128, 5);
        long terrainId = RenderTargetRegistry.rawTargetIdentity(635, 128, 5);
        long translucentId = RenderTargetRegistry.rawTargetIdentity(637, 128, 5);
        assertEquals(entitiesId, terrainId);
        assertEquals(terrainId, translucentId);
        assertTrue(RenderTargetRegistry.isRawColorDepthIdentity(terrainId));

        // Depth-free composite FBO (fbo 193) attaching colortex0=128 without depth stays per-FBO.
        long compositeId = RenderTargetRegistry.rawTargetIdentity(193, 128, 0);
        assertFalse(RenderTargetRegistry.isRawColorDepthIdentity(compositeId));
        assertEquals(RenderTargetRegistry.rawFramebufferIdentity(193), compositeId);

        // Shadow FBOs attaching (shadowcolor0=180, shadowtex0=178) share a distinct canonical pair.
        long shadowId = RenderTargetRegistry.rawTargetIdentity(632, 180, 178);
        assertTrue(RenderTargetRegistry.isRawColorDepthIdentity(shadowId));
        assertFalse(shadowId == terrainId);
    }

    @Test
    void countsLeadingContiguousColorAttachments() {
        assertEquals(0, VulkanTerrainGpuBackend.celtium$leadingColorAttachmentCount(null));
        assertEquals(0, VulkanTerrainGpuBackend.celtium$leadingColorAttachmentCount(new int[0]));
        assertEquals(0, VulkanTerrainGpuBackend.celtium$leadingColorAttachmentCount(new int[] {0, 132}));
        assertEquals(1, VulkanTerrainGpuBackend.celtium$leadingColorAttachmentCount(new int[] {128, 0, 134}));
        assertEquals(3, VulkanTerrainGpuBackend.celtium$leadingColorAttachmentCount(new int[] {128, 132, 134, 0}));
    }

    @Test
    void handsOffColorFromDepthFreePublishedOwnerWhenDepthAttachedPassCannotDirectlyReusePair() {
        long translucent = 0x40008300005L;
        long deferred1 = 0x100000000bfL;
        long mainId = 0x3abe1c9eL;
        // Depth-free deferred1 owns colortex0 ALT (tex 131); depth-attached gbuffers_water cannot reuse
        // 0x100000000bf directly (ownerDepthMatches=false) so it must hand off colour from deferred1.
        assertEquals(deferred1, VulkanTerrainGpuBackend.celtium$resolvePackTerrainColorHandoffSource(
                translucent, translucent, deferred1, mainId, mainId, true, true, false, true));

        // No handoff when depth already matched (direct reuse), when owner was not yet acquired,
        // when size mismatches, or when owner is main/self/absent.
        assertEquals(0L, VulkanTerrainGpuBackend.celtium$resolvePackTerrainColorHandoffSource(
                translucent, deferred1, deferred1, mainId, mainId, true, true, true, true));
        assertEquals(0L, VulkanTerrainGpuBackend.celtium$resolvePackTerrainColorHandoffSource(
                translucent, translucent, deferred1, mainId, mainId, true, true, false, false));
        assertEquals(0L, VulkanTerrainGpuBackend.celtium$resolvePackTerrainColorHandoffSource(
                translucent, translucent, deferred1, mainId, mainId, true, false, false, true));
        assertEquals(0L, VulkanTerrainGpuBackend.celtium$resolvePackTerrainColorHandoffSource(
                translucent, translucent, mainId, mainId, mainId, true, true, false, true));
        assertEquals(0L, VulkanTerrainGpuBackend.celtium$resolvePackTerrainColorHandoffSource(
                translucent, translucent, translucent, mainId, mainId, true, true, false, true));
        assertEquals(0L, VulkanTerrainGpuBackend.celtium$resolvePackTerrainColorHandoffSource(
                translucent, translucent, Long.MIN_VALUE, mainId, mainId, true, true, false, true));
    }

    @Test
    void preservesDepthForMultiTargetImmediateWindowsWithDepthAttachment() {
        assertTrue(VulkanTerrainGpuBackend.celtium$preserveWindowDepth(true, true));
        assertFalse(VulkanTerrainGpuBackend.celtium$preserveWindowDepth(true, false));
        assertFalse(VulkanTerrainGpuBackend.celtium$preserveWindowDepth(false, true));
        assertFalse(VulkanTerrainGpuBackend.celtium$preserveWindowDepth(false, false));
    }
}
