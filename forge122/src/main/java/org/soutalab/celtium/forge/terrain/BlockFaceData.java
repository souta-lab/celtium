package org.soutalab.celtium.forge.terrain;

/**
 * Everything the zero-based mesher needs to know about a block state, as flat per-state queries
 * over a state table whose index 0 is air. The mesher reads only this interface, never the game:
 * the Forge binding ({@code ForgeBlockFaceData}) builds the table from the game's block-state
 * registry and baked models, and unit tests build small tables by hand.
 * <p>
 * Two geometry shapes exist. A <b>full-cube</b> state emits its six axis faces through the
 * SWAR/greedy path into {@code PackedAxisQuad}s. Any other state emits <b>general quads</b>
 * (its baked model's quads, in block-local coordinates) into {@code GeneralQuad}s; the mesher
 * translates them into section space and bakes light and tint.
 */
public interface BlockFaceData {

    /** The state table index of air. Every table has air at 0. */
    int AIR = 0;

    boolean isAir(int state);

    /** Whether the state is a full 1&times;1&times;1 cube (the axis-face path). */
    boolean isFullCube(int state);

    /**
     * Whether this state's face on side {@code dir} fully hides the neighbouring block's
     * opposite face. A full opaque cube occludes every side; a state whose face shape is not
     * solid occludes nothing.
     */
    boolean occludes(int state, int dir);

    /**
     * Whether faces between two cells of this same state are culled even though the state does
     * not occlude (glass, water and the like merging with themselves).
     */
    boolean cullsSameState(int state);

    /** The layer bucket this state's faces belong to ({@link TerrainLayer} ordinal). */
    int layer(int state);

    /** The bindless texture-table index of this state's face on side {@code dir}. */
    int faceTexture(int state, int dir);

    /**
     * The face texture's atlas cell origin, packed as {@code cellU | (cellV << 8)} - the
     * {@code PackedAxisQuad} cell fields. Non-atlas textures return 0.
     */
    int faceCell(int state, int dir);

    /** Whether this state's face on side {@code dir} takes the cell's resolved tint colour. */
    boolean faceUsesTint(int state, int dir);

    /** Whether the state is leaves (the leaf-culling predicates apply). */
    boolean isLeaves(int state);

    /** Whether this state's face on side {@code dir} counts as solid for the leaf predicates. */
    boolean isSideSolid(int state, int dir);

    /**
     * Whether greedy merging must be disabled for this state while a shader pack is in use
     * (wind-swayed foliage and fluids, whose vertices the pack animates per original quad).
     * Pack OFF, the state meshes greedily like everything else.
     */
    boolean noGreedyWhenPack(int state);

    /** The {@code mc_Entity} id the pack auxiliary column carries for this state. */
    short mcEntityId(int state);

    /** The number of general (baked-model) quads this state emits; 0 for full cubes and air. */
    int generalQuadCount(int state);

    /**
     * Reads general quad {@code quadIndex} of {@code state} into {@code out} (reused across
     * calls). Positions are block-local (0..1 space, may exceed it for overhanging models).
     */
    void readGeneralQuad(int state, int quadIndex, ModelQuadView out);

    /** A reusable view of one baked-model quad, in block-local coordinates. */
    final class ModelQuadView {
        /** 12 floats: corner 0..3, each (x, y, z), block-local. */
        public final float[] positions = new float[12];
        /** {@code GeneralQuad.packNormal} form of the quad's face normal. */
        public int normalPacked;
        /** The bindless texture-table index of the quad's sprite. */
        public int textureIndex;
        /** Whether the quad takes the cell's resolved tint colour. */
        public boolean usesTint;
        /** Whether greedy-style merging is off for this quad under a pack (sway). */
        public boolean noGreedyWhenPack;
    }
}
