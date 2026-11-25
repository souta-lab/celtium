package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * L02-04 (bughunt 2026-11-21, lane 2): the arena's growth and split alignment.
 * <p>
 * BH-01's invariant is that every arena boundary - capacity included - is a multiple of the alignment unit
 * {@code lcm(4, stride)}. The constructor honoured it, but the old doubling {@code grow} aimed at a raw
 * maximum whose minimum-growth term (524,288 bytes) is not a multiple of units like 24, 28 or 36, and
 * {@code VulkanBuffer.ensureCapacity} rounds only to 4: the capacity could land off the unit, the appended
 * tail then ended off the unit, and a split of that tail derived a start below the range it split -
 * overlapping the live segment in front and driving the remainder's length negative.
 * <p>
 * S3R-2 replaced the doubling growth with pages: a page's capacity is now the one number that has to stay
 * on the unit, because every later page's base is a sum of page capacities. These tests pin the two pure
 * functions that carry the invariant ({@code pageCapacityBytes} and {@code splitStart}), because the arena
 * itself cannot be constructed without a device; the wiring is one call each in {@code addPage} and
 * {@code allocate}.
 */
class VulkanArenaGrowthAlignmentTest {

    @Test
    void pageCapacityIsAlwaysAMultipleOfTheUnit() {
        // Units whose lcm(4, stride) does not divide the page sizes in play - the strides an off-unit
        // capacity would betray, because the next page's base inherits the error.
        int[] units = {24, 28, 36};
        int[] pageUnits = {96, 4096, 100_008, 524_288, 16_777_216, 67_108_864};
        int[] neededs = {0, 24, 4096, 300_000, 2_000_000};
        for (int unit : units) {
            for (int pageUnit : pageUnits) {
                for (int needed : neededs) {
                    long capacity = VulkanArena.pageCapacityBytes(pageUnit, needed, unit);
                    assertEquals(0L, capacity % unit,
                            "page capacity must stay on the unit (unit=" + unit + ", pageUnit=" + pageUnit
                                    + ", needed=" + needed + ")");
                    assertTrue(capacity >= pageUnit, "a page is never smaller than the page unit");
                    assertTrue(capacity >= needed, "a page always holds the request that forced it");
                }
            }
        }
    }

    @Test
    void anOrdinaryPageInheritsTheUnitExactly() {
        // The two arenas the tree actually creates: the page unit is the initial capacity, inherited
        // unchanged for every ordinary page - no doubling, no minimum-growth term.
        assertEquals(67_108_864L, VulkanArena.pageCapacityBytes(67_108_864, 4096, 4));
        assertEquals(16_777_216L, VulkanArena.pageCapacityBytes(16_777_216, 4096, 4));
        // A unit that is itself off the alignment unit rounds up once, at the page size, so every page
        // base stays on the unit: 524,288 mod 24 = 8, so the page is the next whole unit up.
        assertEquals(524_304L, VulkanArena.pageCapacityBytes(524_288, 24, 24));
        // Units that divide the page size keep it exactly.
        assertEquals(524_288L, VulkanArena.pageCapacityBytes(524_288, 16, 16));
    }

    @Test
    void anOversizeRequestGetsAPageOfItsOwnSize() {
        // A single allocation larger than the page unit: the page is the request's aligned size, not
        // a multiple of the unit pages - the excess of a doubled page would be stranded tail.
        assertEquals(2_000_000L, VulkanArena.pageCapacityBytes(4096, 2_000_000, 4));
        assertEquals(2_000_016L, VulkanArena.pageCapacityBytes(4096, 2_000_000, 24));
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
