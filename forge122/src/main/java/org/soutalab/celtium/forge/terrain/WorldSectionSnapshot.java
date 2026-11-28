package org.soutalab.celtium.forge.terrain;

import java.util.Arrays;

/**
 * One 16&sup3; section plus its 1-block apron, captured into flat primitive arrays (design
 * V040-FINAL-PLAN section 2.1: "18&sup3; flat arrays, zero allocation"). The apron exists so
 * face culling, light sampling and the leaf predicates read neighbours without ever going back
 * to the world; everything the mesher needs is in these arrays.
 * <p>
 * Layout: cell index {@code x + 18 * (z + 18 * y)} over snapshot coordinates 0..17; the inner
 * section occupies 1..16 on each axis. All arrays are allocated once in the constructor and
 * reused for every fill - {@link #fill} overwrites in place and allocates nothing.
 * <p>
 * Contents per cell:
 * <ul>
 *   <li>{@code states}: the block-state table index (see {@link BlockFaceData}); 0 is air.</li>
 *   <li>{@code fluids}: the fluidlogged fluid's state table index, or 0 when the cell holds no
 *       logged fluid. Filled only when the Fluidlogged API is present (the scanner's probe).</li>
 *   <li>{@code blockLight} / {@code skyLight}: the cell's light levels, 0..15.</li>
 *   <li>{@code tintRgb}: the block's resolved tint colour as {@code 0xRRGGBB}, or
 *       {@link #NO_TINT} when the block takes no tint at this position. The scanner resolves
 *       biome and colour-handler tints so the mesher never calls back into the game.</li>
 * </ul>
 */
public final class WorldSectionSnapshot {
    public static final int SIZE = 18;
    public static final int INNER = 16;
    public static final int VOLUME = SIZE * SIZE * SIZE;
    public static final int NO_TINT = -1;

    private int sectionX;
    private int sectionY;
    private int sectionZ;

    private final int[] states = new int[VOLUME];
    private final int[] fluids = new int[VOLUME];
    private final byte[] blockLight = new byte[VOLUME];
    private final byte[] skyLight = new byte[VOLUME];
    private final int[] tintRgb = new int[VOLUME];
    private final boolean[] neighborBrightness = new boolean[VOLUME];

    public static int index(int x, int y, int z) {
        return x + SIZE * (z + SIZE * y);
    }

    /** Whether (x, y, z) is inside the inner 16&sup3; section (not the apron). */
    public static boolean isInner(int x, int y, int z) {
        return x >= 1 && x <= INNER && y >= 1 && y <= INNER && z >= 1 && z <= INNER;
    }

    /** Begins a fill for a new section: records the coordinates and clears every array. */
    public void fill(int sectionX, int sectionY, int sectionZ) {
        this.sectionX = sectionX;
        this.sectionY = sectionY;
        this.sectionZ = sectionZ;
        Arrays.fill(states, 0);
        Arrays.fill(fluids, 0);
        Arrays.fill(blockLight, (byte) 0);
        Arrays.fill(skyLight, (byte) 0);
        Arrays.fill(tintRgb, NO_TINT);
        Arrays.fill(neighborBrightness, false);
    }

    public int sectionX() {
        return sectionX;
    }

    public int sectionY() {
        return sectionY;
    }

    public int sectionZ() {
        return sectionZ;
    }

    public void setCell(int index, int state, int blockLight, int skyLight, int tintRgb) {
        states[index] = state;
        this.blockLight[index] = (byte) blockLight;
        this.skyLight[index] = (byte) skyLight;
        this.tintRgb[index] = tintRgb;
    }

    public void setFluid(int index, int fluidState) {
        fluids[index] = fluidState;
    }

    public int stateAt(int index) {
        return states[index];
    }

    public int stateAt(int x, int y, int z) {
        return states[index(x, y, z)];
    }

    public int fluidAt(int index) {
        return fluids[index];
    }

    public int blockLightAt(int index) {
        return blockLight[index] & 0xFF;
    }

    public int skyLightAt(int index) {
        return skyLight[index] & 0xFF;
    }

    /** Whether the state at {@code index} samples its render light from its neighbours. */
    public void setNeighborBrightness(int index, boolean value) {
        neighborBrightness[index] = value;
    }

    /**
     * Applies vanilla's render-light rule (World.getCombinedLight, via
     * getLightFromNeighborsFor) to the raw per-cell light in place: a cell whose state
     * uses neighbour brightness - air, leaves, and every other non-full-cube state -
     * takes the maximum light over its six neighbours instead of its own stored value.
     * The stored arrays in this fixture are a binary cast (15 in the open, 0 in every
     * shadowed cell, no propagation gradient), so raw face reads go black across whole
     * canopies; the neighbour maximum at the face cell is what vanilla samples. Called
     * once at the end of a fill, over the raw arrays.
     */
    public void applyRenderLightRule() {
        applyRenderLightRule(new byte[VOLUME], new byte[VOLUME]);
    }

    /**
     * The render-light rule over caller-provided scratch: the two arrays receive a copy of
     * the raw light before the in-place maximum pass, so a caller that applies the rule
     * repeatedly (the build scheduler, once per build on a worker) passes thread-local
     * scratch instead of cloning both arrays per section. The scratch arrays must hold at
     * least {@link #VOLUME} bytes; their previous contents are irrelevant.
     */
    public void applyRenderLightRule(byte[] scratchSky, byte[] scratchBlock) {
        System.arraycopy(skyLight, 0, scratchSky, 0, VOLUME);
        System.arraycopy(blockLight, 0, scratchBlock, 0, VOLUME);
        for (int y = 0; y < SIZE; y++) {
            for (int z = 0; z < SIZE; z++) {
                for (int x = 0; x < SIZE; x++) {
                    int idx = index(x, y, z);
                    if (!neighborBrightness[idx]) {
                        continue;
                    }
                    int maxSky = 0;
                    int maxBlock = 0;
                    for (int dir = 0; dir < 6; dir++) {
                        int nx = x + TerrainDirs.DX[dir];
                        int ny = y + TerrainDirs.DY[dir];
                        int nz = z + TerrainDirs.DZ[dir];
                        if (nx < 0 || ny < 0 || nz < 0 || nx >= SIZE || ny >= SIZE || nz >= SIZE) {
                            continue;
                        }
                        int nIdx = idx + offset(dir);
                        int sky = scratchSky[nIdx] & 0xFF;
                        if (sky > maxSky) {
                            maxSky = sky;
                        }
                        int block = scratchBlock[nIdx] & 0xFF;
                        if (block > maxBlock) {
                            maxBlock = block;
                        }
                    }
                    skyLight[idx] = (byte) maxSky;
                    blockLight[idx] = (byte) maxBlock;
                }
            }
        }
    }

    /** The light of the cell a face points into: the neighbour cell along {@code dir}. */
    public int faceBlockLight(int index, int dir) {
        return blockLightAt(index + offset(dir)) & 0xFF;
    }

    public int faceSkyLight(int index, int dir) {
        return skyLightAt(index + offset(dir)) & 0xFF;
    }

    public int tintAt(int index) {
        return tintRgb[index];
    }

    /** The flat index offset of one step along {@code dir}. */
    public static int offset(int dir) {
        return TerrainDirs.DX[dir] + SIZE * (TerrainDirs.DZ[dir] + SIZE * TerrainDirs.DY[dir]);
    }

    // The backing arrays, exposed for the mesher's word-parallel passes. Callers must not retain
    // or mutate them outside a fill/mesh cycle.

    public int[] states() {
        return states;
    }

    public int[] fluids() {
        return fluids;
    }

    public byte[] blockLight() {
        return blockLight;
    }

    public byte[] skyLight() {
        return skyLight;
    }

    public int[] tintRgb() {
        return tintRgb;
    }
}
