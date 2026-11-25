package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure contract tests for the default-OFF {@code evictionSkipSettle} and {@code sharedIndexSectionBound}
 * tail-latency switches in {@link VulkanTerrainGpuBackend}.
 */
class EvictionAndSharedIndexContractTest {

    @Test void evictionSettleDecisionMatchesFlag() {
        assertTrue(VulkanTerrainGpuBackend.celtium$regionEvictionNeedsSettle(false),
                "OFF default must preserve settleGpu before region eviction");
        assertFalse(VulkanTerrainGpuBackend.celtium$regionEvictionNeedsSettle(true),
                "ON flag must skip settleGpu because RegionResources.delete() owns no Vulkan handles");
    }

    @Test void sharedIndexOffMatchesLegacySharedArenaBound() {
        // 64 MiB shared vertex arena, 24 B/vertex, 4 verts/quad -> 699,050 + 1 = 699,051 quads.
        long arenaBytes = 64L * 1024L * 1024L;
        int req = VulkanTerrainGpuBackend.celtium$sharedIndexRequiredPrimitives(
                48_000L, 24, 4, arenaBytes, false);
        assertEquals((int) ((arenaBytes / 24L) / 4L) + 1, req);
        assertEquals(req, VulkanTerrainGpuBackend.celtium$sharedIndexPrimitiveCapacity(req, false));
        assertEquals(1024, VulkanTerrainGpuBackend.celtium$sharedIndexPrimitiveCapacity(1, false));
    }

    @Test void sharedIndexOnBoundsToMaxSectionWithPowerOfTwoHeadroom() {
        // Even with a 64 MiB shared arena, a 48 KiB max section (2,000 verts = 500 quads)
        // stays within the 16,384-primitive floor (384 KiB index buffer).
        long arenaBytes = 64L * 1024L * 1024L;
        int reqSmall = VulkanTerrainGpuBackend.celtium$sharedIndexRequiredPrimitives(
                48_000L, 24, 4, arenaBytes, true);
        assertEquals(501, reqSmall);
        assertEquals(16384, VulkanTerrainGpuBackend.celtium$sharedIndexPrimitiveCapacity(reqSmall, true));

        // A dense section exceeding 16,384 quads rounds up to the next power of two (32,768).
        long denseSectionBytes = 20_000L * 4L * 24L;
        int reqDense = VulkanTerrainGpuBackend.celtium$sharedIndexRequiredPrimitives(
                denseSectionBytes, 24, 4, arenaBytes, true);
        assertEquals(20_001, reqDense);
        assertEquals(32768, VulkanTerrainGpuBackend.celtium$sharedIndexPrimitiveCapacity(reqDense, true));
    }

    @Test void sharedIndexOnHandlesZeroSectionBytesSafely() {
        int req = VulkanTerrainGpuBackend.celtium$sharedIndexRequiredPrimitives(0L, 24, 4, 0L, true);
        assertEquals(1, req);
        assertEquals(16384, VulkanTerrainGpuBackend.celtium$sharedIndexPrimitiveCapacity(req, true));
    }

    @Test void directRunMergeRespectsSharedIndexElementCapWhenEnabled() {
        int maxElements = 16384 * 6;
        assertTrue(VulkanTerrainGpuBackend.celtium$directRunMergeFitsSharedIndex(
                maxElements, 600, maxElements, false),
                "OFF default keeps legacy unbounded direct merge");
        assertTrue(VulkanTerrainGpuBackend.celtium$directRunMergeFitsSharedIndex(
                maxElements - 600, 600, maxElements, true),
                "Merge exactly hitting the shared index capacity must be allowed");
        assertFalse(VulkanTerrainGpuBackend.celtium$directRunMergeFitsSharedIndex(
                maxElements - 594, 600, maxElements, true),
                "Merge exceeding the shared index capacity must split into a new draw");
        assertFalse(VulkanTerrainGpuBackend.celtium$directRunMergeFitsSharedIndex(
                Integer.MAX_VALUE - 10, 100, maxElements, true),
                "Overflowing int addition must still be rejected");
    }
}
