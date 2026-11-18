package org.embeddedt.embeddium.impl.render.chunk.occlusion;

import org.embeddedt.embeddium.impl.render.chunk.RenderSection;
import org.embeddedt.embeddium.impl.render.chunk.region.RenderRegion;
import org.embeddedt.embeddium.impl.render.chunk.region.TestRegions;
import org.joml.Vector3i;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class SectionLatticeWindowTest {
    private static final int[][] ROOTS = {
            {2, 8, 7}, {3, 9, 7},
            {8, 8, 7}, {9, 8, 6}, {10, 10, 8}, {13, 5, 9}, {14, 8, 7},
    };

    @ParameterizedTest
    @CsvSource({
            "14, 8, 7, 5",
            // Y is windowed (item 493, upstream 7818b3317): a Y-only camera move slides the
            // window in Y as well, so the root at y=5 falls out of the retained set.
            "8, 14, 7, 6",
            "40, 8, 7, 0",
    })
    void windowMoveKeepsVisibleCellsOnTheirSections(int cameraX, int cameraY, int cameraZ, int retained) {
        var lattice = new SectionLattice(0, 16, true, false);
        Map<Long, RenderRegion> regions = new HashMap<>();

        for (int x = 0; x <= 30; x++) {
            for (int y = 0; y <= 15; y++) {
                for (int z = 0; z <= 14; z++) {
                    int rx = x >> RenderRegion.REGION_WIDTH_SH;
                    int ry = y >> RenderRegion.REGION_HEIGHT_SH;
                    int rz = z >> RenderRegion.REGION_LENGTH_SH;
                    var region = regions.computeIfAbsent(RenderRegion.key(rx, ry, rz),
                            k -> TestRegions.create(rx, ry, rz, regions.size()));
                    lattice.attach(new RenderSection(region, x, y, z));
                }
            }
        }

        lattice.ensureWindowCovers(new Vector3i(8, 8, 7), 32.0f);

        long[] cells = lattice.ensureVisibleCellsCapacity(ROOTS.length);
        for (int i = 0; i < ROOTS.length; i++) {
            int[] root = ROOTS[i];
            int idx = lattice.indexOf(root[0], root[1], root[2]);
            cells[i] = ((long) lattice.packXyz(root[0], root[1], root[2]) << 32) | (idx & 0xFFFFFFFFL);
        }
        lattice.visibleCount = ROOTS.length;

        lattice.ensureWindowCovers(new Vector3i(cameraX, cameraY, cameraZ), 32.0f);

        Set<RenderSection> expected = new HashSet<>();
        for (int[] root : ROOTS) {
            int idx = lattice.indexOf(root[0], root[1], root[2]);
            if (idx >= 0) {
                expected.add(lattice.sectionAt(idx));
            }
        }
        assertEquals(retained, expected.size());
        assertEquals(retained, lattice.visibleCount);

        Set<RenderSection> seen = new HashSet<>();
        for (int i = 0; i < lattice.visibleCount; i++) {
            long entry = lattice.visibleCells[i];
            var section = lattice.sectionAt((int) entry);
            assertTrue(expected.contains(section), "root " + i + " points at " + section);
            assertTrue(seen.add(section), "root " + i + " duplicated");
            assertEquals(lattice.packXyz(section.getChunkX(), section.getChunkY(), section.getChunkZ()), (int) (entry >>> 32));
        }
    }

    // Item 493 (upstream 7818b3317): a world taller than the lattice's per-axis limit must not
    // fail construction. Y is windowed instead, so only sections near the camera's height are
    // installed, and the window slides in Y with the camera.
    @Test
    void tallWorldIsWindowedInYInsteadOfThrowing() {
        // World height 2048 sections: worldDimY = 2050 > MAX_DIM (1024). The pre-493
        // constructor threw IllegalStateException for this.
        SectionLattice lattice = assertDoesNotThrow(() -> new SectionLattice(0, 2048, false, false));
        Map<Long, RenderRegion> regions = new HashMap<>();

        RenderSection low = attach(lattice, regions, 8, 10, 7);
        RenderSection mid = attach(lattice, regions, 8, 1000, 7);
        RenderSection high = attach(lattice, regions, 8, 1010, 7);

        lattice.ensureWindowCovers(new Vector3i(8, 1000, 7), 32.0f);

        // Only the section inside the Y window around the camera is installed.
        assertEquals(-1, lattice.indexOf(low.getChunkX(), low.getChunkY(), low.getChunkZ()));
        int midIdx = lattice.indexOf(mid.getChunkX(), mid.getChunkY(), mid.getChunkZ());
        assertTrue(midIdx >= 0);
        assertEquals(mid, lattice.sectionAt(midIdx));
        assertEquals(-1, lattice.indexOf(high.getChunkX(), high.getChunkY(), high.getChunkZ()));
        assertEquals(1, lattice.installedCount);

        // Sliding the camera up in Y moves the window: mid leaves, high enters.
        lattice.ensureWindowCovers(new Vector3i(8, 1010, 7), 32.0f);

        assertEquals(-1, lattice.indexOf(mid.getChunkX(), mid.getChunkY(), mid.getChunkZ()));
        int highIdx = lattice.indexOf(high.getChunkX(), high.getChunkY(), high.getChunkZ());
        assertTrue(highIdx >= 0);
        assertEquals(high, lattice.sectionAt(highIdx));
        assertEquals(1, lattice.installedCount);
    }

    private static RenderSection attach(SectionLattice lattice, Map<Long, RenderRegion> regions, int x, int y, int z) {
        int rx = x >> RenderRegion.REGION_WIDTH_SH;
        int ry = y >> RenderRegion.REGION_HEIGHT_SH;
        int rz = z >> RenderRegion.REGION_LENGTH_SH;
        var region = regions.computeIfAbsent(RenderRegion.key(rx, ry, rz),
                k -> TestRegions.create(rx, ry, rz, regions.size()));
        var section = new RenderSection(region, x, y, z);
        lattice.attach(section);
        return section;
    }
}
