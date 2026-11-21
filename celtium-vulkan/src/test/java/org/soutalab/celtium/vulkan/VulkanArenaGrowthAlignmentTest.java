package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * L02-04 (bughunt 2026-11-21, lane 2): the arena's growth and split alignment.
 * <p>
 * BH-01's invariant is that every arena boundary - capacity included - is a multiple of the alignment unit
 * {@code lcm(4, stride)}. The constructor honoured it, but {@code grow} aimed at a raw maximum whose
 * minimum-growth term (524,288 bytes) is not a multiple of units like 24, 28 or 36, and
 * {@code VulkanBuffer.ensureCapacity} rounds only to 4: the capacity could land off the unit, the appended
 * tail then ended off the unit, and a split of that tail derived a start below the range it split -
 * overlapping the live segment in front and driving the remainder's length negative. These tests pin the
 * two pure functions the fix is made of ({@code growthTarget} and {@code splitStart}), because the arena
 * itself cannot be constructed without a device; the wiring is one call each in {@code grow} and
 * {@code allocate}.
 */
class VulkanArenaGrowthAlignmentTest {

    @Test
    void growthTargetIsAlwaysAMultipleOfTheUnit() {
        // Units whose lcm(4, stride) does not divide 524,288 - the strides the raw maximum betrayed.
        int[] units = {24, 28, 36};
        int[] capacities = {96, 4096, 100_008, 524_280, 1_048_576};
        int[] neededs = {24, 4096, 300_000, 2_000_000};
        for (int unit : units) {
            for (int capacity : capacities) {
                for (int needed : neededs) {
                    long target = VulkanArena.growthTarget(capacity, needed, unit);
                    assertEquals(0L, target % unit,
                            "growth target must stay on the unit (unit=" + unit + ", capacity=" + capacity
                                    + ", needed=" + needed + ")");
                    assertTrue(target >= (long) capacity + needed,
                            "growth target must hold the used bytes plus the request");
                    assertTrue(target >= 524_288L, "growth target keeps the minimum growth");
                }
            }
        }
    }

    @Test
    void theMinimumGrowthTermIsRoundedUpToTheUnit() {
        // The concrete BH-01 shape: a small arena whose doubling and capacity+needed both stay under the
        // 524,288 minimum. 524,288 mod 24 = 8, so the raw maximum ended 8 bytes off the unit; the target
        // is the next whole unit up.
        assertEquals(524_304L, VulkanArena.growthTarget(4096, 24, 24));
        // Units that do divide the minimum keep it exactly.
        assertEquals(524_288L, VulkanArena.growthTarget(4096, 16, 16));
        assertEquals(524_288L, VulkanArena.growthTarget(4096, 4, 4));
    }

    @Test
    void doublingStaysExactWhenTheUnitDividesItAndRoundsUpWhenItDoesNot() {
        // The two arenas the tree actually creates start at 4 MiB / 512 KiB with small units: doubling
        // already lands on the unit, and the rounding must not move those targets.
        assertEquals(8_388_608L, VulkanArena.growthTarget(4_194_304, 1024, 4));
        assertEquals(1_048_576L, VulkanArena.growthTarget(524_288, 1024, 4));
        // 2^20 is not a multiple of 24, so a doubled 512 KiB capacity rounds up to the next whole unit.
        assertEquals(1_048_584L, VulkanArena.growthTarget(524_288, 1024, 24));
    }

    @Test
    void splitStartPullsTheEndDownToTheUnit() {
        // Aligned range, more than enough room: the segment starts at end - needed, already on the unit.
        assertEquals(96, VulkanArena.splitStart(0, 120, 24, 24));
        // An unaligned tail end is trimmed down to the last whole element, never below the range.
        assertEquals(72, VulkanArena.splitStart(8, 100, 24, 24));
        // Exact fit on an aligned start hands the range out from its own offset.
        assertEquals(48, VulkanArena.splitStart(48, 24, 24, 24));
    }

    @Test
    void splitStartRefusesAStartBelowTheRange() {
        // Exact fit on an unaligned start: alignDownTo lands below the offset, which used to return a
        // segment overlapping its predecessor with a negative-length remainder.
        assertThrows(IllegalStateException.class, () -> VulkanArena.splitStart(8, 24, 24, 24));
        // Less than one unit of slack past an unaligned offset has no aligned element to give.
        assertThrows(IllegalStateException.class, () -> VulkanArena.splitStart(8, 30, 24, 24));
    }
}
