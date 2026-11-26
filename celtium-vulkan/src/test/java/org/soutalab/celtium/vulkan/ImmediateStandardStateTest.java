package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;
import org.lwjgl.vulkan.VK10;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * The standard-state templates the prewarm set warms and the fallback substitutes
 * (Stage 5 slice S5-0): pure state arithmetic, exercised without a device the way
 * {@link StateSignatureVersionContractTest} exercises the signature machinery.
 */
class ImmediateStandardStateTest {

    @Test
    void alphaStandardStateCarriesTheLegacyBlendPairAndNoDepthWrite() {
        int[] state = VulkanTerrainGpuBackend.celtium$immediateStandardState(
                1, true, false, false, false);
        assertEquals(1, state[0]);
        assertEquals(VK10.VK_BLEND_FACTOR_SRC_ALPHA, state[1]);
        assertEquals(VK10.VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA, state[2]);
        assertEquals(1, state[3]);
        assertEquals(0, state[4]);
        assertEquals(VulkanCullState.NONE, state[5]);
        assertEquals(515, state[7]);
        assertEquals(VK10.VK_BLEND_OP_ADD, state[12]);
        assertEquals(VK10.VK_BLEND_OP_ADD, state[13]);
    }

    @Test
    void opaqueStandardStateDisablesBlendAndWritesDepth() {
        int[] state = VulkanTerrainGpuBackend.celtium$immediateStandardState(
                0, true, true, false, false);
        assertEquals(0, state[0]);
        assertEquals(VK10.VK_BLEND_FACTOR_ZERO, state[1]);
        assertEquals(1, state[3]);
        assertEquals(1, state[4]);
    }

    @Test
    void additiveStandardStateUsesTheOneDestination() {
        int[] state = VulkanTerrainGpuBackend.celtium$immediateStandardState(
                2, true, false, false, false);
        assertEquals(1, state[0]);
        assertEquals(VK10.VK_BLEND_FACTOR_SRC_ALPHA, state[1]);
        assertEquals(VK10.VK_BLEND_FACTOR_ONE, state[2]);
    }

    @Test
    void standardKeysSeparateFormatsAndTemplates() {
        int[] alpha = VulkanTerrainGpuBackend.celtium$immediateStandardState(
                1, true, false, false, false);
        int[] opaque = VulkanTerrainGpuBackend.celtium$immediateStandardState(
                0, true, true, false, false);
        long texAlpha = VulkanTerrainGpuBackend.celtium$stateKey(2, alpha);
        assertNotEquals(texAlpha, VulkanTerrainGpuBackend.celtium$stateKey(6, alpha));
        assertNotEquals(texAlpha, VulkanTerrainGpuBackend.celtium$stateKey(2, opaque));
        assertEquals(texAlpha, VulkanTerrainGpuBackend.celtium$stateKey(2, alpha.clone()));
    }

    @Test
    void branchKeyFoldsOnlyTheParticleFamily() {
        long key = 123456789L;
        long folded = key * 31L + ((long) VK10.VK_BLEND_FACTOR_SRC_ALPHA << 20)
                + VK10.VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA;
        assertEquals(folded, VulkanTerrainGpuBackend.celtium$immediateBranchKey(5, key));
        assertEquals(key, VulkanTerrainGpuBackend.celtium$immediateBranchKey(2, key));
        assertEquals(key, VulkanTerrainGpuBackend.celtium$immediateBranchKey(1, key));
    }
}
