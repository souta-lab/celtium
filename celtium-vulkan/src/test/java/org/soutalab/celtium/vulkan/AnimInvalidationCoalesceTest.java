package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Truth-table contract for {@link VulkanTerrainGpuBackend#shouldCoalesceAnimInvalidation}: a per-sprite
 * invalidation inside an animation batch may be skipped only when the coalescing switch is on, a batch is
 * open, the bound texture is the batch's own texture, and that texture's invalidation already ran once in
 * this batch. Every other combination must run the full invalidation.
 */
class AnimInvalidationCoalesceTest {

    @Test
    void coalescesOnlyTheRepeatOfTheBatchTexture() {
        assertTrue(VulkanTerrainGpuBackend.shouldCoalesceAnimInvalidation(true, true, 7, 7, true));
    }

    @Test
    void firstInvalidationInTheBatchAlwaysRuns() {
        assertFalse(VulkanTerrainGpuBackend.shouldCoalesceAnimInvalidation(true, true, 7, 7, false));
    }

    @Test
    void disabledSwitchNeverCoalesces() {
        assertFalse(VulkanTerrainGpuBackend.shouldCoalesceAnimInvalidation(false, true, 7, 7, true));
    }

    @Test
    void outsideABatchNeverCoalesces() {
        assertFalse(VulkanTerrainGpuBackend.shouldCoalesceAnimInvalidation(true, false, 7, 7, true));
    }

    @Test
    void aDifferentTextureThanTheBatchNeverCoalesces() {
        assertFalse(VulkanTerrainGpuBackend.shouldCoalesceAnimInvalidation(true, true, 8, 7, true));
        assertFalse(VulkanTerrainGpuBackend.shouldCoalesceAnimInvalidation(true, true, 0, 7, true));
    }

    @Test
    void anUnnamedBatchNeverCoalesces() {
        assertFalse(VulkanTerrainGpuBackend.shouldCoalesceAnimInvalidation(true, true, 7, 0, true));
        assertFalse(VulkanTerrainGpuBackend.shouldCoalesceAnimInvalidation(true, true, 7, -1, true));
    }
}
