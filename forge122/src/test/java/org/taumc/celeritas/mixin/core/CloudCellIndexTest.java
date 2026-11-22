package org.taumc.celeritas.mixin.core;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Item 672: the fast cloud-cell build samples the mask through axis indices wrapped once per
 * build. These tests pin that form to the per-sample {@code floorMod} form it replaces, over
 * negative and large offsets, both cloud scales' extents and the neighbour overhang the fancy
 * side-face checks read — so the fast path emits the same boxes, in the same order, as the
 * shipped path.
 */
class CloudCellIndexTest {

    @Test
    void axisIndicesMatchFloorMod() {
        int[] offsets = {0, 1, -1, 255, 256, -256, 4096, -4096, 1_000_003, -1_000_003,
                Integer.MAX_VALUE / 2, Integer.MIN_VALUE / 2};
        int[] extents = {1, 8, 32, 48, 64};
        for (int offset : offsets) {
            for (int extent : extents) {
                int[] expected = new int[2 * extent + 2];
                for (int k = 0; k < expected.length; k++) {
                    expected[k] = Math.floorMod(offset - extent - 1 + k, 256);
                }
                assertArrayEquals(expected,
                        CloudCellIndex.axisIndices(offset, -extent - 1, 2 * extent + 2, 256),
                        "offset=" + offset + " extent=" + extent);
            }
        }
    }

    @Test
    void occupiedMatchesPerSampleFloorMod() {
        int w = 256;
        int h = 256;
        boolean[] mask = new boolean[w * h];
        Random random = new Random(672);
        for (int i = 0; i < mask.length; i++) {
            mask[i] = random.nextInt(4) == 0;
        }
        int[][] cases = {
                // offU, offV, extent
                {0, 0, 32}, {17, -29, 48}, {-255, 255, 32}, {4097, -8191, 64}, {-1, -1, 1},
        };
        for (int[] c : cases) {
            int offU = c[0];
            int offV = c[1];
            int extent = c[2];
            int[] columns = CloudCellIndex.axisIndices(offU, -extent - 1, 2 * extent + 2, w);
            int[] rows = CloudCellIndex.axisIndices(offV, -extent - 1, 2 * extent + 2, h);
            int[] rowBases = new int[rows.length];
            for (int k = 0; k < rows.length; k++) {
                rowBases[k] = rows[k] * w;
            }
            // The build reads i-1 and i+1 neighbours at the grid edges too, so sample the full
            // overhang range the arrays cover.
            for (int i = -extent - 1; i <= extent; i++) {
                for (int j = -extent - 1; j <= extent; j++) {
                    boolean expected = mask[Math.floorMod(offV + j, h) * w
                            + Math.floorMod(offU + i, w)];
                    assertEquals(expected,
                            CloudCellIndex.occupied(mask, columns, rowBases, extent, i, j),
                            "off=" + offU + "," + offV + " extent=" + extent + " at " + i + "," + j);
                }
            }
        }
    }
}
