package org.taumc.celeritas.mixin.core;

/**
 * Wrapped texel indices for one cloud-cell build (item 672).
 * <p>
 * {@link ForgeCloudCellsMixin}'s build samples the cloud mask once per grid cell and, in fancy
 * mode, four more times per occupied cell for the neighbour checks. Every sample used to run two
 * {@link Math#floorMod(int, int)} divisions, so a single rebuild paid on the order of ten thousand
 * integer divisions for indices that only ever move one step at a time along each axis. The wrapped
 * index of every grid position on one axis is computed once here ({@code 2 * extent + 2} divisions
 * per axis, covering the one-step neighbour overhang on both sides), and the build then reads the
 * samples out of the two arrays.
 * <p>
 * This class is pure arithmetic with no Minecraft dependency so the equivalence with the
 * per-sample {@code floorMod} form is pinned by unit tests.
 */
final class CloudCellIndex {
    private CloudCellIndex() {
    }

    /**
     * {@return} an array of {@code count} wrapped indices where element {@code k} is
     * {@code Math.floorMod(offset + from + k, period)}.
     */
    static int[] axisIndices(int offset, int from, int count, int period) {
        int[] indices = new int[count];
        for (int k = 0; k < count; k++) {
            indices[k] = Math.floorMod(offset + from + k, period);
        }
        return indices;
    }

    /**
     * {@return} the mask sample at grid position {@code (i, j)} of a build whose grid starts at
     * index {@code -extent} on both axes, using the precomputed axis arrays. {@code columns} must
     * hold {@code axisIndices(offU, -extent - 1, 2 * extent + 2, maskW)} and {@code rowBases} the
     * same for the V axis multiplied by {@code maskW}, so element {@code i + extent + 1} /
     * {@code j + extent + 1} is the wrapped index of grid position {@code i} / {@code j} and the
     * arrays also cover the {@code i - 1} / {@code i + 1} neighbour positions the build reads.
     */
    static boolean occupied(boolean[] mask, int[] columns, int[] rowBases, int extent, int i, int j) {
        return mask[rowBases[j + extent + 1] + columns[i + extent + 1]];
    }
}
