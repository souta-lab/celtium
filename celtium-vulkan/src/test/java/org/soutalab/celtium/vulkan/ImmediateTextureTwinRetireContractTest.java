package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The two contracts audit-vulkan-core's D1/D2 findings broke, pinned at the pure decisions they turn on.
 * <p>
 * D1: a record-reading twin built at the wrong stride reads every vertex after the first out of phase, so the
 * stride table and the question of which variants may have a twin at all are pinned input by input. D2: retired
 * descriptor sets are handed back to their pools through a completion-gated free, and the pairing that free is
 * built from - a set handle is unique only within its pool - is pinned the same way.
 */
class ImmediateTextureTwinRetireContractTest {

    @Test
    @DisplayName("the stride decision covers every variant layout, explicit stride first")
    void strideCoversEveryVariant() {
        assertEquals(VulkanImmediateTexturePipeline.COMBINE4_BYTES,
                VulkanImmediateTexturePipeline.strideFor(
                        VulkanImmediateTexturePipeline.COMBINE4_BYTES, 0, false, false),
                "combine4's explicit stride wins");
        assertEquals(44, VulkanImmediateTexturePipeline.strideFor(44,
                VulkanImmediateTexturePipeline.POSITION_TEX_COLOR_NORMAL_BYTES, false, false),
                "an explicit stride wins over a trailing element");
        assertEquals(VulkanImmediateTexturePipeline.POSITION_NORMAL_BYTES,
                VulkanImmediateTexturePipeline.strideFor(
                        VulkanImmediateTexturePipeline.POSITION_NORMAL_BYTES, 0, true, true),
                "POSITION_NORMAL keeps its 16-byte explicit stride");
        assertEquals(VulkanImmediateTexturePipeline.POSITION_TEX_COLOR_NORMAL_BYTES,
                VulkanImmediateTexturePipeline.strideFor(0,
                        VulkanImmediateTexturePipeline.POSITION_TEX_COLOR_NORMAL_BYTES, false, false),
                "the 28-byte entity layout is its trailing element");
        assertEquals(VulkanImmediateTexturePipeline.POSITION_TEX_NORMAL_FLOAT_BYTES,
                VulkanImmediateTexturePipeline.strideFor(0,
                        VulkanImmediateTexturePipeline.POSITION_TEX_NORMAL_FLOAT_BYTES, false, true),
                "the 32-byte float-normal layout is its trailing element");
        assertEquals(VulkanImmediateTexturePipeline.POSITION_TEX_NORMAL_BYTES,
                VulkanImmediateTexturePipeline.strideFor(0,
                        VulkanImmediateTexturePipeline.POSITION_TEX_NORMAL_BYTES, false, true),
                "the 24-byte colourless normal layout is its trailing element, not the base stride");
        assertEquals(VulkanImmediateTexturePipeline.POSITION_BYTES,
                VulkanImmediateTexturePipeline.strideFor(0, 0, true, true), "position only is 12");
        assertEquals(VulkanImmediateTexturePipeline.POSITION_TEX_BYTES,
                VulkanImmediateTexturePipeline.strideFor(0, 0, false, true), "position-tex is 20");
        assertEquals(VulkanImmediateTexturePipeline.VERTEX_BYTES,
                VulkanImmediateTexturePipeline.strideFor(0, 0, false, false), "the base layout is 24");
    }

    @Test
    @DisplayName("only the combine and texgen variants are refused a record-reading twin")
    void twinSupportIsRefusedOnlyForCombineAndTexgen() {
        assertTrue(VulkanImmediateTexturePipeline.celtium$recordsTwinSupported(false, false),
                "the base and stride-carrying variants have records shader pairs");
        assertFalse(VulkanImmediateTexturePipeline.celtium$recordsTwinSupported(true, false),
                "no records pair evaluates the combine");
        assertFalse(VulkanImmediateTexturePipeline.celtium$recordsTwinSupported(false, true),
                "no records pair generates texgen coordinates");
        assertFalse(VulkanImmediateTexturePipeline.celtium$recordsTwinSupported(true, true),
                "a variant that is both is refused on either ground");
    }

    @Test
    @DisplayName("retired sets drain grouped by their own pool, the pair being the identity")
    void retiredSetsDrainGroupedByPool() {
        VulkanImmediateTexturePipeline.RetiredDescriptorSets retired =
                new VulkanImmediateTexturePipeline.RetiredDescriptorSets();
        retired.add(100L, 7L);
        retired.add(200L, 7L);
        retired.add(100L, 9L);
        assertEquals(3, retired.size(), "the same handle in two pools is two retirements");
        Map<Long, long[]> byPool = retired.drainGrouped();
        assertEquals(0, retired.size(), "a drain hands everything off");
        assertArrayEquals(new long[] {7L, 9L}, byPool.get(100L), "pool 100 gets its two sets in order");
        assertArrayEquals(new long[] {7L}, byPool.get(200L), "pool 200 gets its own 7, not pool 100's");
        assertTrue(retired.drainGrouped().isEmpty(), "a second drain has nothing left to hand off");
    }

    @Test
    @DisplayName("a cleared retired store hands nothing off")
    void retiredSetsClearDropsEverything() {
        VulkanImmediateTexturePipeline.RetiredDescriptorSets retired =
                new VulkanImmediateTexturePipeline.RetiredDescriptorSets();
        retired.add(100L, 7L);
        retired.clear();
        assertEquals(0, retired.size());
        assertTrue(retired.drainGrouped().isEmpty(), "cleared pairs are not freed after their pool is gone");
    }
}
