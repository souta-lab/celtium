package org.soutalab.celtium.forge.terrain;

/**
 * Everything the zero-based mesher needs to know about a block state, as flat per-state queries
 * over a state table whose index 0 is air. The mesher reads only this interface, never the game:
 * the Forge binding ({@code ForgeBlockFaceData}) builds the table from the game's block-state
 * registry and baked models, and unit tests build small tables by hand.
 * <p>
 * Two geometry shapes exist. A <b>full-cube</b> state emits its six axis faces through the
 * SWAR/greedy path into {@code PackedAxisQuad}s - except a face whose baked UVs are a partial
 * rectangle or rotated/flipped, which escapes into the general pool per face
 * ({@link #faceEscapesToGeneral}). Any other state emits <b>general quads</b>
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

    /**
     * Whether this full-cube state's face on side {@code dir} escapes the axis path into the
     * general pool: the face's baked quads carry partial-rectangle or rotated/flipped UVs (or
     * the face has more than one quad), which the packed axis form cannot express. The mesher
     * suppresses the axis face for such a (state, dir) and the face's quads are read through
     * {@link #generalQuadCount} / {@link #readGeneralQuad} instead (Neon UV ruling
     * NEON-UV-RULING-2026-11-28 section 5.3). Always false for non-full-cube states.
     */
    boolean faceEscapesToGeneral(int state, int dir);

    /** Whether the state is leaves (the leaf-culling predicates apply). */
    boolean isLeaves(int state);

    /**
     * Whether the state is a liquid (its material is liquid). Vanilla tints fluid surfaces
     * through the block colour handlers unconditionally - the fluid renderer applies them
     * by block, never by model tint index - so the scanner resolves a tint for liquid
     * states regardless of their model quads, and the mesher passes that tint to every
     * general quad of a liquid state.
     */
    boolean isLiquid(int state);

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

    /**
     * The state's own light emission (0..15), vanilla's {@code IBlockState.getLightValue()}:
     * every light sample the smooth-lighting corner computation takes is floored at the face
     * block's emission, exactly as {@code World.getCombinedLight(pos, lightValue)} does.
     */
    int lightValue(int state);

    /**
     * Vanilla's {@code IBlockState.getAmbientOcclusionLightValue()}: 0.2 for a normal cube,
     * 1.0 for everything else (air included), unless a block overrides it. The per-corner AO
     * multiplier of the smooth-lighting model averages this value over the corner's sample
     * cells.
     */
    float aoLightValue(int state);

    /**
     * Vanilla's {@code IBlockState.isTranslucent()}: the smooth-lighting diagonal sample of a
     * corner quadrant is taken only when at least one of the two cells just beyond the
     * quadrant's edge cells (one more step along the face direction) is translucent.
     */
    boolean isTranslucent(int state);

    /**
     * The number of general (baked-model) quads this state emits; 0 for air and for full cubes
     * whose faces all stay on the axis path (escaped faces contribute their quads here).
     */
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
        /** 4 packed Q1.15 sprite-local UV words, one per corner ({@code GeneralQuad.packUv}). */
        public final int[] uvPacked = new int[4];
    }
}
