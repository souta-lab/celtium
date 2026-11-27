package org.soutalab.celtium.forge.terrain;

/**
 * Celtium's native leaf culling (decision D-57), implemented over the section snapshot for the
 * zero-based mesher. The behaviour is the D-57 specification the existing build-time
 * implementation follows - what is culled, under which conditions - re-expressed against the
 * snapshot's flat arrays; it is not a call into that implementation (the zero-based scanner and
 * mesher carry no Celeritas code).
 * <p>
 * Two mechanisms, both decided at mesh time:
 * <ul>
 *   <li><b>Face suppression</b> - a leaf face is suppressed (never forced on) when HOLLOW's
 *       depth-2 leaf run holds along the face direction, or, in the SOLID modes, when the
 *       neighbouring block the face points at is itself surrounded.</li>
 *   <li><b>Material swap</b> - in the SOLID modes, a surrounded leaf block emits its faces into
 *       the SOLID layer instead of the leaves' layer, so it renders as an opaque block with its
 *       own texture. The swap - and only the swap - is suppressed while a shader pack is in
 *       use; face suppression does not consult pack state.</li>
 * </ul>
 * Snapshot boundary rule: the predicates read the 1-block apron. A check that would step past
 * the snapshot answers "not leaves / not blocked", which fails every predicate - suppression
 * and solidification only ever remove or move faces, so the boundary error is always in the
 * keeping direction.
 */
public final class NativeLeafCulling {

    public enum Mode {
        NONE,
        HOLLOW,
        SOLID,
        SOLID_AGGRESSIVE;

        /** The mode the product ships with (S1-c settings default, D-57). */
        public static final Mode DEFAULT = SOLID;
    }

    private NativeLeafCulling() {
    }

    private static boolean inSnapshot(int x, int y, int z) {
        return x >= 0 && x < WorldSectionSnapshot.SIZE
                && y >= 0 && y < WorldSectionSnapshot.SIZE
                && z >= 0 && z < WorldSectionSnapshot.SIZE;
    }

    private static boolean isLeaves(WorldSectionSnapshot snapshot, BlockFaceData data, int x, int y, int z) {
        if (!inSnapshot(x, y, z)) {
            return false;
        }
        return data.isLeaves(snapshot.stateAt(x, y, z));
    }

    /**
     * Predicate A ("surrounded"): every checked direction from the cell is blocked, where a
     * direction is blocked when the neighbouring cell holds leaves, or when the neighbouring
     * block's face towards this cell is solid. SOLID checks all six directions;
     * SOLID_AGGRESSIVE skips the vertical ones. Air blocks nothing.
     */
    public static boolean isSurrounded(WorldSectionSnapshot snapshot, BlockFaceData data,
                                       int x, int y, int z, Mode mode) {
        for (int dir = 0; dir < TerrainDirs.COUNT; dir++) {
            if (mode == Mode.SOLID_AGGRESSIVE && TerrainDirs.AXIS[dir] == 1) {
                continue;
            }
            int nx = x + TerrainDirs.DX[dir];
            int ny = y + TerrainDirs.DY[dir];
            int nz = z + TerrainDirs.DZ[dir];
            if (!inSnapshot(nx, ny, nz)) {
                return false;
            }
            int neighbor = snapshot.stateAt(nx, ny, nz);
            if (data.isLeaves(neighbor)) {
                continue;
            }
            if (!data.isSideSolid(neighbor, TerrainDirs.opposite(dir))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Predicate B (face culling, depth-parameterised): the cells at distance {@code 1..depth}
     * along {@code dir} are all leaves. The only depth the behaviour uses is 2.
     */
    public static boolean shouldCullSide(WorldSectionSnapshot snapshot, BlockFaceData data,
                                         int x, int y, int z, int dir, int depth) {
        for (int distance = 1; distance <= depth; distance++) {
            if (!isLeaves(snapshot, data,
                    x + TerrainDirs.DX[dir] * distance,
                    y + TerrainDirs.DY[dir] * distance,
                    z + TerrainDirs.DZ[dir] * distance)) {
                return false;
            }
        }
        return true;
    }

    /**
     * The face-suppression decision for the leaf face at the cell facing {@code dir}. True only
     * when the face may be dropped; every other case keeps the face.
     */
    public static boolean shouldSuppressFace(WorldSectionSnapshot snapshot, BlockFaceData data,
                                             int x, int y, int z, int dir, Mode mode) {
        switch (mode) {
            case NONE:
                return false;
            case HOLLOW:
                return shouldCullSide(snapshot, data, x, y, z, dir, 2);
            case SOLID:
            case SOLID_AGGRESSIVE:
                // The face points at the neighbouring block; it is suppressed when that
                // neighbour is itself surrounded.
                return isSurrounded(snapshot, data,
                        x + TerrainDirs.DX[dir], y + TerrainDirs.DY[dir], z + TerrainDirs.DZ[dir], mode);
            default:
                return false;
        }
    }

    /**
     * The material-swap decision for the block at the cell: the block is leaves, the mode is a
     * SOLID mode, the block is surrounded, and no shader pack is in use.
     */
    public static boolean shouldSolidify(WorldSectionSnapshot snapshot, BlockFaceData data,
                                         int x, int y, int z, Mode mode, boolean shaderPackInUse) {
        if (mode != Mode.SOLID && mode != Mode.SOLID_AGGRESSIVE) {
            return false;
        }
        if (shaderPackInUse) {
            return false;
        }
        if (!data.isLeaves(snapshot.stateAt(x, y, z))) {
            return false;
        }
        return isSurrounded(snapshot, data, x, y, z, mode);
    }
}
