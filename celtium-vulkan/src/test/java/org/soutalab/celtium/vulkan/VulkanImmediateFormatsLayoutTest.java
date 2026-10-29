package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The one layout whose stride and pipeline width disagree, and the rule that fixes it without a second stride.
 * <p>
 * What this test proves: the 32-byte particle-with-normal layout maps to the 28-byte PARTICLE id, and
 * {@link VulkanImmediateFormats#compactedStride(String, int)} says 28 for exactly that layout and leaves every other one
 * at its own width. What it cannot prove is that the compaction is correct in a frame - that is a GPU leg - but the
 * stride contract is the thing that was wrong when the 24-byte normal format was answered with the 28-byte id.
 */
class VulkanImmediateFormatsLayoutTest {

    @Test
    void particleWithTrailingNormalMapsToParticle() {
        assertEquals(VulkanImmediateFormats.PARTICLE,
                VulkanImmediateFormats.layout(VulkanImmediateFormats.LAYOUT_PARTICLE_NORMAL_PAD, 32, 6));
    }

    @Test
    void theUnpaddedParticleLayoutStillMapsToParticle() {
        assertEquals(VulkanImmediateFormats.PARTICLE,
                VulkanImmediateFormats.layout(VulkanImmediateFormats.LAYOUT_PARTICLE, 28, 4));
    }

    @Test
    void compactedStrideCutsOnlyThePaddedParticleLayout() {
        assertEquals(28, VulkanImmediateFormats.compactedStride(VulkanImmediateFormats.LAYOUT_PARTICLE_NORMAL_PAD, 32));
        assertEquals(28, VulkanImmediateFormats.compactedStride(VulkanImmediateFormats.LAYOUT_PARTICLE, 28));
        assertEquals(28, VulkanImmediateFormats.compactedStride(VulkanImmediateFormats.LAYOUT_ITEM, 28));
        assertEquals(24, VulkanImmediateFormats.compactedStride(VulkanImmediateFormats.LAYOUT_POSITION_TEX_COLOR, 24));
        assertEquals(32, VulkanImmediateFormats.compactedStride(VulkanImmediateFormats.LAYOUT_POSITION_TEX_NORMAL_FLOAT,
                32));
        assertEquals(20, VulkanImmediateFormats.compactedStride("something else", 20));
    }

    @Test
    void thePaddedLayoutIsNotMistakenForAStandardOneAtAnotherWidth() {
        // The element list alone is not an identity: the same list at 28 bytes is PARTICLE and must stay so, and the
        // padded list at any other width maps to nothing rather than to a pipeline it does not fit.
        assertEquals(0, VulkanImmediateFormats.layout(VulkanImmediateFormats.LAYOUT_PARTICLE_NORMAL_PAD, 28, 6));
        assertEquals(0, VulkanImmediateFormats.layout(VulkanImmediateFormats.LAYOUT_PARTICLE_NORMAL_PAD, 32, 4));
    }
}
