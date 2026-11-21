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
}
