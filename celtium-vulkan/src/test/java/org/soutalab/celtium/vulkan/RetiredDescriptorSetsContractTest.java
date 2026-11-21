package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The pairing the block and item families' completion-gated descriptor free is built from, pinned at the pure
 * store it turns on (audit-vulkan-core D2, those families' shape of it).
 * <p>
 * A set handle is unique only within its pool, so the free must be addressed by the (pool, set) pair: the same
 * handle retired from two pools is two retirements, and a drain must hand each pool exactly its own sets.
 */
class RetiredDescriptorSetsContractTest {

    @Test
    @DisplayName("retired sets drain grouped by their own pool, the pair being the identity")
    void retiredSetsDrainGroupedByPool() {
        RetiredDescriptorSets retired = new RetiredDescriptorSets();
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
        RetiredDescriptorSets retired = new RetiredDescriptorSets();
        retired.add(100L, 7L);
        retired.clear();
        assertEquals(0, retired.size());
        assertTrue(retired.drainGrouped().isEmpty(), "cleared pairs are not freed after their pool is gone");
    }
}
