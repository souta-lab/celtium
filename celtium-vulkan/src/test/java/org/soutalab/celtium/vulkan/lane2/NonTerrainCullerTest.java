package org.soutalab.celtium.vulkan.lane2;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NonTerrainCullerTest {

    private static long section(int x, int y, int z) {
        return ((long) x & 0x3FFFFF) << 42 | ((long) y & 0x3FFFFF) << 20 | ((long) z & 0x3FFFFF);
    }

    /** A section-visibility view over a fixed set, using the same packing as the test. */
    private static NonTerrainCuller.SectionVisibility visible(Set<Long> sections) {
        return (x, y, z) -> sections.contains(section(x, y, z));
    }

    private static NonTerrainCuller.Candidate box(double minX, double minY, double minZ,
                                                  double maxX, double maxY, double maxZ) {
        return NonTerrainCuller.Candidate.bounded(
                NonTerrainCuller.Kind.ENTITY, minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static NonTerrainCuller.Result cullOne(NonTerrainCuller.Candidate candidate,
                                                   NonTerrainCuller.SectionVisibility sections,
                                                   NonTerrainCuller.HiZVerdict hiZ) {
        return NonTerrainCuller.cull(new NonTerrainCuller.Candidate[]{candidate}, sections, hiZ);
    }

    @Test
    void unknownBoundsAreAlwaysVisible() {
        NonTerrainCuller.Result result = cullOne(
                NonTerrainCuller.Candidate.unknown(NonTerrainCuller.Kind.ITEM),
                visible(Set.of()), index -> true);
        assertTrue(result.visible()[0]);
        assertEquals(0, result.culledBySection());
        assertEquals(0, result.culledByHiZ());
    }

    @Test
    void alwaysVisibleFlagShortCircuits() {
        NonTerrainCuller.Candidate candidate = new NonTerrainCuller.Candidate(
                NonTerrainCuller.Kind.TILE_ENTITY, true, true, 500, 0, 500, 501, 1, 501);
        NonTerrainCuller.Result result = cullOne(candidate, visible(Set.of()), null);
        assertTrue(result.visible()[0]);
    }

    @Test
    void boundedCandidateInsideAVisibleSectionIsVisible() {
        // Entity at block (20, 40, 20): section (1, 2, 1).
        NonTerrainCuller.Result result = cullOne(
                box(20.2, 40.0, 20.2, 20.8, 41.8, 20.8),
                visible(Set.of(section(1, 2, 1))), null);
        assertTrue(result.visible()[0]);
    }

    @Test
    void boundedCandidateOutsideEveryVisibleSectionIsCulledBySection() {
        NonTerrainCuller.Result result = cullOne(
                box(20.2, 40.0, 20.2, 20.8, 41.8, 20.8),
                visible(Set.of(section(9, 9, 9))), index -> true);
        assertFalse(result.visible()[0]);
        assertEquals(1, result.culledBySection());
        assertEquals(0, result.culledByHiZ());
    }

    @Test
    void hizCullsOnlyOnAPositiveVerdict() {
        NonTerrainCuller.Candidate candidate = box(20.2, 40.0, 20.2, 20.8, 41.8, 20.8);
        var visibleSections = visible(Set.of(section(1, 2, 1)));
        NonTerrainCuller.Result occluded = cullOne(candidate, visibleSections, index -> true);
        assertFalse(occluded.visible()[0]);
        assertEquals(1, occluded.culledByHiZ());

        NonTerrainCuller.Result notProven = cullOne(candidate, visibleSections, index -> false);
        assertTrue(notProven.visible()[0]);

        // No Hi-Z view at all: section visibility alone decides.
        NonTerrainCuller.Result noPyramid = cullOne(candidate, visibleSections, null);
        assertTrue(noPyramid.visible()[0]);
    }

    @Test
    void negativeCoordinatesFloorIntoTheRightSection() {
        // Block (-1, 0, -1) is section (-1, 0, -1), not section (0, 0, 0).
        NonTerrainCuller.Candidate candidate = box(-0.9, 0.1, -0.9, -0.1, 0.9, -0.1);
        assertTrue(cullOne(candidate, visible(Set.of(section(-1, 0, -1))), null).visible()[0]);
        assertFalse(cullOne(candidate, visible(Set.of(section(0, 0, 0))), null).visible()[0]);
    }

    @Test
    void aBoxSpanningSectionsIsVisibleIfAnySectionIs() {
        // From block 14 to block 18 on x: sections 0 and 1.
        NonTerrainCuller.Candidate candidate = box(14.0, 0.0, 0.0, 18.0, 1.0, 1.0);
        assertTrue(cullOne(candidate, visible(Set.of(section(1, 0, 0))), null).visible()[0]);
    }

    @Test
    void batchDecisionsAreIndependentPerCandidate() {
        NonTerrainCuller.Candidate[] candidates = {
                box(20.2, 40.0, 20.2, 20.8, 41.8, 20.8),
                NonTerrainCuller.Candidate.unknown(NonTerrainCuller.Kind.PARTICLE),
                box(200.0, 40.0, 200.0, 201.0, 41.0, 201.0),
        };
        NonTerrainCuller.Result result = NonTerrainCuller.cull(
                candidates, visible(Set.of(section(1, 2, 1))), index -> index == 0);
        assertFalse(result.visible()[0]);
        assertTrue(result.visible()[1]);
        assertFalse(result.visible()[2]);
        assertEquals(1, result.culledByHiZ());
        assertEquals(1, result.culledBySection());
        assertEquals(1, result.visibleCount());
    }
}
