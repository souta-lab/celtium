package org.soutalab.celtium.forge.terrain;

/**
 * The six block face directions, in {@code EnumFacing} declaration order (the order the game's
 * own tables use), with the geometry tables the mesher and the snapshot share. A direction's
 * "axis" uses the {@code PackedAxisQuad} encoding (0 = X, 1 = Y, 2 = Z); the negative directions
 * are DOWN, NORTH and WEST.
 */
public final class TerrainDirs {
    public static final int DOWN = 0;
    public static final int UP = 1;
    public static final int NORTH = 2;
    public static final int SOUTH = 3;
    public static final int WEST = 4;
    public static final int EAST = 5;

    public static final int COUNT = 6;

    /** Cell offset per direction, in snapshot coordinates. */
    public static final int[] DX = {0, 0, 0, 0, -1, 1};
    public static final int[] DY = {-1, 1, 0, 0, 0, 0};
    public static final int[] DZ = {0, 0, -1, 1, 0, 0};

    /** PackedAxisQuad axis per direction: 0 = X, 1 = Y, 2 = Z. */
    public static final int[] AXIS = {1, 1, 2, 2, 0, 0};

    /** Whether the direction faces the axis' negative side. */
    public static final boolean[] NEGATIVE = {true, false, true, false, true, false};

    /**
     * The vanilla directional shade per direction as a fixed-point byte (0..255): down 0.5,
     * up 1.0, north/south 0.8, west/east 0.6, rounded to the nearest byte.
     */
    public static final int[] SHADE = {128, 255, 204, 204, 153, 153};

    private TerrainDirs() {
    }

    public static int opposite(int dir) {
        return dir ^ 1;
    }

    /**
     * The face-plane U axis direction index and V axis direction index for a face direction:
     * the two axes that span the face, in the order the greedy mesher scans them. For X faces
     * U runs along Z and V along Y; for Y faces U along X and V along Z; for Z faces U along X
     * and V along Y.
     */
    public static int uAxis(int dir) {
        return switch (AXIS[dir]) {
            case 0 -> 2; // X face: U = Z
            case 1 -> 0; // Y face: U = X
            default -> 0; // Z face: U = X
        };
    }

    public static int vAxis(int dir) {
        return switch (AXIS[dir]) {
            case 0 -> 1; // X face: V = Y
            case 1 -> 2; // Y face: V = Z
            default -> 1; // Z face: V = Y
        };
    }
}
