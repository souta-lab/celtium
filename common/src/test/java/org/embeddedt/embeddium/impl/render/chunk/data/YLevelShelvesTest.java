package org.embeddedt.embeddium.impl.render.chunk.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * The ±Y level shelves' pure contract (item 671): the level grouping, the cumulative table and the trim
 * arithmetic the batch assembler applies. These are the numbers a wrong sign would silently invert — the
 * failure mode is a missing floor or ceiling in the presented image — so they are pinned here exactly,
 * including the boundary cases: the camera's own level is kept in both buckets, and outside quads are
 * never trimmed.
 */
class YLevelShelvesTest {

    @Test
    void levelIsTheFloorOfTheFirstVertexY() {
        assertEquals(0, YLevelShelves.levelOf(0.0f));
        assertEquals(0, YLevelShelves.levelOf(0.99f));
        assertEquals(15, YLevelShelves.levelOf(15.99f));
        assertEquals(16, YLevelShelves.levelOf(16.0f));
        assertEquals(-1, YLevelShelves.levelOf(-0.01f));
        assertEquals(-8, YLevelShelves.levelOf(-8.0f));
    }

    @Test
    void keySeparatesInsideFromOutside() {
        assertEquals(0, YLevelShelves.keyOf(0));
        assertEquals(15, YLevelShelves.keyOf(15));
        assertEquals(YLevelShelves.OUTSIDE_KEY, YLevelShelves.keyOf(16));
        assertEquals(YLevelShelves.OUTSIDE_KEY, YLevelShelves.keyOf(-1));
        assertEquals(YLevelShelves.OUTSIDE_KEY, YLevelShelves.keyOf(Integer.MIN_VALUE));
    }

    @Test
    void negSortIsAscendingWithOutsideLastAndStable() {
        // Quad levels in bucket order: 5, 2, 5, outside, 0.
        int[] levels = {5, 2, 5, -1, 0};
        int[] order = YLevelShelves.sortOrder(levels, false);

        // 0 (idx 4), 2 (idx 1), 5 (idx 0 before idx 2: stable), outside (idx 3) last.
        assertArrayEquals(new int[]{4, 1, 0, 2, 3}, order);
    }

    @Test
    void posSortIsOutsideFirstThenAscendingAndStable() {
        int[] levels = {5, 2, 5, -1, 0};
        int[] order = YLevelShelves.sortOrder(levels, true);

        // Outside (idx 3) first, then 0, 2, 5, 5 in stable order.
        assertArrayEquals(new int[]{3, 4, 1, 0, 2}, order);
    }

    @Test
    void sortOfSortedInputIsIdentityAndEmptyIsEmpty() {
        assertArrayEquals(new int[]{0, 1, 2}, YLevelShelves.sortOrder(new int[]{0, 3, 15}, false));
        assertArrayEquals(new int[]{0, 1, 2}, YLevelShelves.sortOrder(new int[]{-2, 0, 15}, true));
        assertArrayEquals(new int[0], YLevelShelves.sortOrder(new int[0], false));
    }

    @Test
    void cumulativeCountsInsideVerticesBelowEachBoundary() {
        // Levels: 0, 0, 1, 15, and two outside quads that must not be counted.
        int[] cum = YLevelShelves.cumulative(new int[]{0, 0, 1, 15, -3, 20});

        assertEquals(YLevelShelves.CUM_ENTRIES, cum.length);
        assertEquals(0, cum[0]);
        assertEquals(8, cum[1]);   // the two level-0 quads
        assertEquals(12, cum[2]);  // plus the level-1 quad
        assertEquals(12, cum[15]); // nothing between levels 2..14
        assertEquals(16, cum[16]); // plus the level-15 quad; outside quads excluded
    }

    @Test
    void belowAndAboveClampAtTheSectionBounds() {
        assertEquals(0, YLevelShelves.below(-5));
        assertEquals(0, YLevelShelves.below(0));
        assertEquals(7, YLevelShelves.below(7));
        assertEquals(16, YLevelShelves.below(16));
        assertEquals(16, YLevelShelves.below(100));

        assertEquals(0, YLevelShelves.above(-5));
        assertEquals(0, YLevelShelves.above(-1));
        assertEquals(1, YLevelShelves.above(0));
        assertEquals(16, YLevelShelves.above(15));
        assertEquals(16, YLevelShelves.above(16));
        assertEquals(16, YLevelShelves.above(100));
    }

    @Test
    void negTrimDropsLevelsBelowTheCameraButKeepsItsOwnLevel() {
        // One quad at each of levels 0..3, plus one outside quad: 20 vertices from 100.
        int[] cum = YLevelShelves.cumulative(new int[]{0, 1, 2, 3, -1});

        assertEquals(100, YLevelShelves.negTrimmedStart(100, cum, 0));
        assertEquals(108, YLevelShelves.negTrimmedStart(100, cum, 2)); // levels 0,1 dropped; level 2 kept
        assertEquals(116, YLevelShelves.negTrimmedStart(100, cum, 16)); // all inside dropped
        assertEquals(116, YLevelShelves.negTrimmedStart(100, cum, 99)); // clamped: the outside quad remains
        assertEquals(100, YLevelShelves.negTrimmedStart(100, cum, -3)); // camera below: nothing dropped
    }

    @Test
    void posTrimDropsLevelsAboveTheCameraButKeepsItsOwnLevelAndTheOutsideQuads() {
        // Bucket [200, 220): one quad at each of levels 0..3, plus one outside quad at the front.
        int[] cum = YLevelShelves.cumulative(new int[]{0, 1, 2, 3, -1});

        assertEquals(204, YLevelShelves.posTrimmedEnd(200, 220, cum, -3)); // above clamps to 0: outside only
        assertEquals(212, YLevelShelves.posTrimmedEnd(200, 220, cum, 1));  // outside + levels 0,1 (own level kept)
        assertEquals(220, YLevelShelves.posTrimmedEnd(200, 220, cum, 15)); // everything kept
        assertEquals(220, YLevelShelves.posTrimmedEnd(200, 220, cum, 99)); // clamped: everything kept
    }

    @Test
    void zeroTableNeverTrims() {
        // The assembler's fallback for a bucket that was never shelf-sorted.
        int[] zero = new int[YLevelShelves.CUM_ENTRIES];

        assertEquals(100, YLevelShelves.negTrimmedStart(100, zero, 8));
        // With a zero table the whole bucket counts as "outside", so the end is the bucket end.
        assertEquals(220, YLevelShelves.posTrimmedEnd(200, 220, zero, 8));
    }

    @Test
    void shelfValidityIntervalIsOneStepInsideTheRegionAndClampedOutside() {
        int regionMin = 64; // chunk Y 4; the region spans blocks [64, 128)

        long below = YLevelShelves.shelfValidityIntervalY(0, regionMin);
        assertEquals(Integer.MIN_VALUE, (int) below);
        assertEquals(regionMin, (int) (below >>> 32));

        long justBelow = YLevelShelves.shelfValidityIntervalY(regionMin - 1, regionMin);
        assertEquals(Integer.MIN_VALUE, (int) justBelow);
        assertEquals(regionMin, (int) (justBelow >>> 32));

        long inside = YLevelShelves.shelfValidityIntervalY(100, regionMin);
        assertEquals(100, (int) inside);
        assertEquals(101, (int) (inside >>> 32));

        long above = YLevelShelves.shelfValidityIntervalY(128, regionMin);
        assertEquals(128, (int) above);
        assertEquals(Integer.MAX_VALUE, (int) (above >>> 32));
    }

    @Test
    void flagDefaultsOffAndOffKeepsTheCompactRowLayout() {
        // The off state is the shipped default: no sort, no shelf block, stride unchanged.
        assertFalse(YLevelShelves.ENABLED, "the shelves flag must default to off");
        assumeFalse(YLevelShelves.ENABLED);
        assertEquals(32, SectionRenderDataUnsafe.Strategy.COMPACT.getStride());
    }
}
