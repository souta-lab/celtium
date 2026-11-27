package org.soutalab.celtium.vulkan.lane2;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NonTerrainCullerTest {

    private static long section(int x, int y, int z) {
        return ((long) x & 0x3FFFFF) << 42 | ((long) y & 0x3FFFFF) << 20 | ((long) z & 0x3FFFFF);
    }

    /** A section-visibility view over a fixed set, using the same packing as the culler. */
    private static NonTerrainCuller.SectionVisibility visible(Set<Long> sections) {
        return (x, y, z) -> sections.contains(section(x, y, z));
    }

    private static NonTerrainCuller.Candidate box(double minX, double minY, double minZ,
                                                  double maxX, double maxY, double maxZ) {
        return NonTerrainCuller.Candidate.bounded(
                NonTerrainCuller.Kind.ENTITY, minX, minY, minZ, maxX, maxY, maxZ, false);
    }

    @Test
    void unknownBoundsAreAlwaysVisible() {
        NonTerrainCuller.Candidate candidate =
                NonTerrainCuller.Candidate.unknown(NonTerrainCuller.Kind.ITEM);
        assertEquals(NonTerrainCuller.Verdict.VISIBLE,
                NonTerrainCuller.decide(candidate, visible(Set.of()), (minX, minY, minZ, maxX, maxY, maxZ) -> true));
    }

    @Test
    void alwaysVisibleFlagShortCircuits() {
        NonTerrainCuller.Candidate candidate = NonTerrainCuller.Candidate.bounded(
                NonTerrainCuller.Kind.TESR, 500, 0, 500, 501, 1, 501, true);
        assertEquals(NonTerrainCuller.Verdict.VISIBLE,
                NonTerrainCuller.decide(candidate, visible(Set.of()), null));
    }

    @Test
    void boundedCandidateInsideAVisibleSectionIsVisible() {
        // Entity at block (20, 40, 20): section (1, 2, 1).
        NonTerrainCuller.Candidate candidate = box(20.2, 40.0, 20.2, 20.8, 41.8, 20.8);
        assertEquals(NonTerrainCuller.Verdict.VISIBLE,
                NonTerrainCuller.decide(candidate, visible(Set.of(section(1, 2, 1))), null));
    }

    @Test
    void boundedCandidateOutsideEveryVisibleSectionIsCulledBySection() {
        NonTerrainCuller.Candidate candidate = box(20.2, 40.0, 20.2, 20.8, 41.8, 20.8);
        assertEquals(NonTerrainCuller.Verdict.CULLED_BY_SECTION,
                NonTerrainCuller.decide(candidate, visible(Set.of(section(9, 9, 9))),
                        (minX, minY, minZ, maxX, maxY, maxZ) -> true));
    }

    @Test
    void hizCullsOnlyOnAPositiveVerdict() {
        NonTerrainCuller.Candidate candidate = box(20.2, 40.0, 20.2, 20.8, 41.8, 20.8);
        var visibleSections = visible(Set.of(section(1, 2, 1)));
        assertEquals(NonTerrainCuller.Verdict.CULLED_BY_HIZ,
                NonTerrainCuller.decide(candidate, visibleSections,
                        (minX, minY, minZ, maxX, maxY, maxZ) -> true));
        assertEquals(NonTerrainCuller.Verdict.VISIBLE,
                NonTerrainCuller.decide(candidate, visibleSections,
                        (minX, minY, minZ, maxX, maxY, maxZ) -> false));
        // No Hi-Z view at all: section visibility alone decides.
        assertEquals(NonTerrainCuller.Verdict.VISIBLE,
                NonTerrainCuller.decide(candidate, visibleSections, null));
    }

    @Test
    void negativeCoordinatesFloorIntoTheRightSection() {
        // Block (-1, 0, -1) is section (-1, 0, -1), not section (0, 0, 0).
        NonTerrainCuller.Candidate candidate = box(-0.9, 0.1, -0.9, -0.1, 0.9, -0.1);
        assertEquals(NonTerrainCuller.Verdict.VISIBLE,
                NonTerrainCuller.decide(candidate, visible(Set.of(section(-1, 0, -1))), null));
        assertEquals(NonTerrainCuller.Verdict.CULLED_BY_SECTION,
                NonTerrainCuller.decide(candidate, visible(Set.of(section(0, 0, 0))), null));
    }

    @Test
    void aBoxSpanningSectionsIsVisibleIfAnySectionIs() {
        // From block 14 to block 18 on x: sections 0 and 1.
        NonTerrainCuller.Candidate candidate = box(14.0, 0.0, 0.0, 18.0, 1.0, 1.0);
        assertEquals(NonTerrainCuller.Verdict.VISIBLE,
                NonTerrainCuller.decide(candidate, visible(Set.of(section(1, 0, 0))), null));
    }
}
