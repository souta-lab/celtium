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
        int[] state = Lane2ImmediateRecorder.celtium$immediateStandardState(
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
        int[] state = Lane2ImmediateRecorder.celtium$immediateStandardState(
                0, true, true, false, false);
        assertEquals(0, state[0]);
        assertEquals(VK10.VK_BLEND_FACTOR_ZERO, state[1]);
        assertEquals(1, state[3]);
        assertEquals(1, state[4]);
    }

    @Test
    void additiveStandardStateUsesTheOneDestination() {
        int[] state = Lane2ImmediateRecorder.celtium$immediateStandardState(
                2, true, false, false, false);
        assertEquals(1, state[0]);
        assertEquals(VK10.VK_BLEND_FACTOR_SRC_ALPHA, state[1]);
        assertEquals(VK10.VK_BLEND_FACTOR_ONE, state[2]);
    }

    @Test
    void standardKeysSeparateFormatsAndTemplates() {
        int[] alpha = Lane2ImmediateRecorder.celtium$immediateStandardState(
                1, true, false, false, false);
        int[] opaque = Lane2ImmediateRecorder.celtium$immediateStandardState(
                0, true, true, false, false);
        long texAlpha = Lane2ImmediateRecorder.celtium$stateKey(2, alpha);
        assertNotEquals(texAlpha, Lane2ImmediateRecorder.celtium$stateKey(6, alpha));
        assertNotEquals(texAlpha, Lane2ImmediateRecorder.celtium$stateKey(2, opaque));
        assertEquals(texAlpha, Lane2ImmediateRecorder.celtium$stateKey(2, alpha.clone()));
    }

    @Test
    void branchKeyFoldsTheParticleBlendPairAndThePass() {
        long key = 123456789L;
        long pass = 0x350000000035L;
        long particle = key * 31L + ((long) VK10.VK_BLEND_FACTOR_SRC_ALPHA << 20)
                + VK10.VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA;
        assertEquals(passFold(particle, pass), Lane2ImmediateRecorder.celtium$immediateBranchKey(5, key, pass));
        assertEquals(passFold(key, pass), Lane2ImmediateRecorder.celtium$immediateBranchKey(2, key, pass));
        assertEquals(passFold(key, pass), Lane2ImmediateRecorder.celtium$immediateBranchKey(1, key, pass));
    }

    /** The pass fold of {@code celtium$immediateBranchKey}, restated: the mix, then the fold under the line bit. */
    private static long passFold(long branch, long pass) {
        long mix = pass;
        mix ^= mix >>> 33;
        mix *= 0xff51afd7ed558ccdL;
        mix ^= mix >>> 33;
        long flag = branch & Lane2ImmediateRecorder.LINE_KEY_BIT;
        return ((branch & ~Lane2ImmediateRecorder.LINE_KEY_BIT) * 31L + mix)
                & ~Lane2ImmediateRecorder.LINE_KEY_BIT | flag;
    }
}
