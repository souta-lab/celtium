package org.soutalab.celtium.forge.terrain;

import net.minecraft.block.Block;
import net.minecraft.block.BlockBreakable;
import net.minecraft.block.BlockLeaves;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.client.renderer.vertex.VertexFormatElement;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import java.util.ArrayList;
import java.util.List;

/**
 * The {@link BlockFaceData} table built from the game's own registries: one row per block state
 * (index = {@code Block.BLOCK_STATE_IDS} id, air at 0), computed once from block properties and
 * baked models. Every per-state query that needs a world runs against a stub access that answers
 * air everywhere - the answers used here (face shape, side solidity, bounding box) are
 * state-derived for the blocks that have them, and any state whose block throws against the
 * stub falls back to the conservative row (occludes nothing, general geometry only).
 * <p>
 * Client-side: the table reads baked models and sprites, so it is built on the client after
 * model bake, lazily at first use.
 */
public final class ForgeBlockFaceData implements BlockFaceData {

    /** A minimal access that answers air / no tile entity / full light everywhere. */
    private static final IBlockAccess STUB_ACCESS = new IBlockAccess() {
        @Override
        public net.minecraft.tileentity.TileEntity getTileEntity(BlockPos pos) {
            return null;
        }

        @Override
        public int getCombinedLight(BlockPos pos, int lightValue) {
            return (15 << 20) | (15 << 4);
        }

        @Override
        public boolean isSideSolid(BlockPos pos, EnumFacing side, boolean _default) {
            return _default;
        }

        @Override
        public IBlockState getBlockState(BlockPos pos) {
            return net.minecraft.init.Blocks.AIR.getDefaultState();
        }

        @Override
        public boolean isAirBlock(BlockPos pos) {
            return true;
        }

        @Override
        public net.minecraft.world.biome.Biome getBiome(BlockPos pos) {
            return net.minecraft.init.Biomes.PLAINS;
        }

        @Override
        public int getStrongPower(BlockPos pos, EnumFacing direction) {
            return 0;
        }

        @Override
        public net.minecraft.world.WorldType getWorldType() {
            return net.minecraft.world.WorldType.DEFAULT;
        }
    };

    private final AtlasSpriteIndex sprites;
    private final int stateCount;
    private final boolean[] air;
    private final boolean[] fullCube;
    private final boolean[] occludes;      // [state * 6 + dir]
    private final boolean[] sideSolid;     // [state * 6 + dir]
    private final boolean[] cullsSame;
    private final byte[] layer;
    private final boolean[] leaves;
    private final boolean[] liquid;
    private final boolean[] noGreedy;
    private final short[] mcEntity;
    private final float[] aoValue;
    private final boolean[] translucent;
    private final byte[] lightEmission;
    private final int[] faceTexture;       // [state * 6 + dir]
    private final int[] faceCell;          // [state * 6 + dir]
    private final boolean[] faceTint;      // [state * 6 + dir]
    private final boolean[] faceGeneral;   // [state * 6 + dir] face escapes the axis path
    private final int[] generalOffset;     // per state, into the quad pool
    private final int[] generalCount;
    private final float[] quadPositions;   // pool: 12 floats per quad
    private final int[] quadMeta;          // pool: texture, normalPacked, tintFlag per quad
    private final int[] quadUvs;           // pool: 4 packed Q1.15 UV words per quad

    private ForgeBlockFaceData(AtlasSpriteIndex sprites, Tables tables) {
        this.sprites = sprites;
        this.stateCount = tables.size;
        this.air = tables.air;
        this.fullCube = tables.fullCube;
        this.occludes = tables.occludes;
        this.sideSolid = tables.sideSolid;
        this.cullsSame = tables.cullsSame;
        this.layer = tables.layer;
        this.leaves = tables.leaves;
        this.liquid = tables.liquid;
        this.noGreedy = tables.noGreedy;
        this.mcEntity = tables.mcEntity;
        this.aoValue = tables.aoValue;
        this.translucent = tables.translucent;
        this.lightEmission = tables.lightEmission;
        this.faceTexture = tables.faceTexture;
        this.faceCell = tables.faceCell;
        this.faceTint = tables.faceTint;
        this.faceGeneral = tables.faceGeneral;
        this.generalOffset = tables.generalOffset;
        this.generalCount = tables.generalCount;
        this.quadPositions = tables.quadPositions;
        this.quadMeta = tables.quadMeta;
        this.quadUvs = tables.quadUvs;
    }

    /** Builds the table from the live registries and the client's baked models. */
    public static ForgeBlockFaceData build(AtlasSpriteIndex sprites) {
        Tables tables = new Tables();
        List<IBlockState> states = new ArrayList<>();
        int maxId = -1;
        for (Block block : Block.REGISTRY) {
            for (IBlockState state : block.getBlockState().getValidStates()) {
                int id = Block.BLOCK_STATE_IDS.get(state);
                if (id > maxId) {
                    maxId = id;
                }
                while (states.size() <= id) {
                    states.add(null);
                }
                states.set(id, state);
            }
        }
        tables.size = maxId + 1;
        tables.allocate();
        List<Float> positions = new ArrayList<>();
        List<Integer> meta = new ArrayList<>();
        List<Integer> uvs = new ArrayList<>();
        List<Float> atlasUvs = new ArrayList<>();
        List<Integer> needsCorrection = new ArrayList<>();
        for (int id = 0; id < tables.size; id++) {
            IBlockState state = states.get(id);
            if (state == null) {
                tables.air[id] = true;
                continue;
            }
            fillRow(tables, sprites, id, state, positions, meta, uvs, atlasUvs, needsCorrection);
        }
        correctMisdeclaredSprites(sprites, meta, uvs, atlasUvs, needsCorrection);
        tables.quadPositions = new float[positions.size()];
        for (int i = 0; i < positions.size(); i++) {
            tables.quadPositions[i] = positions.get(i);
        }
        tables.quadMeta = new int[meta.size()];
        for (int i = 0; i < meta.size(); i++) {
            tables.quadMeta[i] = meta.get(i);
        }
        tables.quadUvs = new int[uvs.size()];
        for (int i = 0; i < uvs.size(); i++) {
            tables.quadUvs[i] = uvs.get(i);
        }
        return new ForgeBlockFaceData(sprites, tables);
    }

    /**
     * The defensive correction of ruling section 5.2-4, run once the whole table exists (so the
     * sprite index knows every sprite any quad referenced): a pooled quad whose declared sprite
     * was missing, degenerate or did not contain its own UV centroid is re-based on the sprite
     * that actually contains the centroid - both its texture index and its sprite-local UVs.
     * Quads with no containing sprite keep their provisional (clamped) values.
     */
    private static void correctMisdeclaredSprites(AtlasSpriteIndex sprites, List<Integer> meta,
                                                  List<Integer> uvs, List<Float> atlasUvs,
                                                  List<Integer> needsCorrection) {
        for (int poolIndex : needsCorrection) {
            float centroidU = 0.0f;
            float centroidV = 0.0f;
            int finite = 0;
            for (int c = 0; c < 4; c++) {
                float u = atlasUvs.get(poolIndex * 8 + c * 2);
                float v = atlasUvs.get(poolIndex * 8 + c * 2 + 1);
                if (Float.isFinite(u) && Float.isFinite(v)) {
                    centroidU += u;
                    centroidV += v;
                    finite++;
                }
            }
            if (finite == 0) {
                continue;
            }
            centroidU /= finite;
            centroidV /= finite;
            TextureAtlasSprite found = sprites.spriteContaining(centroidU, centroidV);
            if (found == null) {
                continue;
            }
            meta.set(poolIndex * 3, sprites.indexFor(found));
            float du = found.getMaxU() - found.getMinU();
            float dv = found.getMaxV() - found.getMinV();
            for (int c = 0; c < 4; c++) {
                float u = atlasUvs.get(poolIndex * 8 + c * 2);
                float v = atlasUvs.get(poolIndex * 8 + c * 2 + 1);
                uvs.set(poolIndex * 4 + c, org.soutalab.celtium.core.geometry.GeneralQuad.packUv(
                        (u - found.getMinU()) / du, (v - found.getMinV()) / dv));
            }
        }
    }

    private static void fillRow(Tables t, AtlasSpriteIndex sprites, int id, IBlockState state,
                                List<Float> positions, List<Integer> meta, List<Integer> uvs,
                                List<Float> atlasUvs, List<Integer> needsCorrection) {
        Block block = state.getBlock();
        Material material = state.getMaterial();
        t.air[id] = material == Material.AIR;
        // The smooth-lighting inputs (R-107) are state-derived like the rest of the row and
        // are recorded for air too: corner samples read the states of air cells constantly.
        try {
            t.aoValue[id] = state.getAmbientOcclusionLightValue();
            t.translucent[id] = state.isTranslucent();
            t.lightEmission[id] = (byte) state.getLightValue();
        } catch (Throwable ignored) {
            t.aoValue[id] = 1.0f;
            t.translucent[id] = false;
            t.lightEmission[id] = 0;
        }
        t.liquid[id] = material.isLiquid();
        if (t.air[id]) {
            return;
        }
        t.leaves[id] = block instanceof BlockLeaves;
        t.mcEntity[id] = (short) Block.getIdFromBlock(block);
        t.layer[id] = (byte) layerOf(state);
        t.cullsSame[id] = block instanceof BlockBreakable || block instanceof BlockLiquid;
        t.noGreedy[id] = material == Material.LEAVES || material == Material.PLANTS
                || material == Material.VINE || block instanceof BlockLiquid;

        boolean opaque = false;
        boolean fullBox = false;
        try {
            opaque = state.isOpaqueCube();
            fullBox = state.getBoundingBox(STUB_ACCESS, BlockPos.ORIGIN) == Block.FULL_BLOCK_AABB;
        } catch (Throwable ignored) {
            // State-derived answers only; a block that needs a real world keeps the defaults.
        }
        boolean opaqueCube = opaque && fullBox;
        IBakedModel model = modelFor(state);
        // Axis-path eligibility (NEON-G1-DEPTH-RULING section 3.2 Step 3, honouring the
        // NEON-UV-RULING section 5.3 routing invariant): leaves and glass-like blocks
        // are never opaque cubes, so the opaque test alone routed them to the general
        // pool and D-57 leaf culling, same-state correction, the 16B axis form and
        // greedy merging never fired for them. A full-box leaves/breakable state whose
        // model is exactly one full-face axis-aligned full-sprite quad per facing is
        // axis-expressible and joins the axis path; anything with partial UVs, several
        // quads per face or off-plane geometry stays in the general pool. Occlusion
        // and side-solidity keep the opaque-cube seed below - leaves occlude only
        // once applySolidify surrounds them (the mesher sets those bits itself).
        boolean fullCube = opaqueCube;
        if (!fullCube && fullBox && model != null
                && (block instanceof BlockLeaves || block instanceof BlockBreakable)
                && modelIsFullCubeFaces(model, state)) {
            fullCube = true;
        }
        t.fullCube[id] = fullCube;
        for (int dir = 0; dir < TerrainDirs.COUNT; dir++) {
            EnumFacing facing = EnumFacing.VALUES[dir];
            boolean occ = opaqueCube;
            boolean solid = false;
            try {
                if (!occ) {
                    occ = state.getBlockFaceShape(STUB_ACCESS, BlockPos.ORIGIN, facing)
                            == net.minecraft.block.state.BlockFaceShape.SOLID;
                }
                solid = state.isSideSolid(STUB_ACCESS, BlockPos.ORIGIN, facing);
            } catch (Throwable ignored) {
            }
            t.occludes[id * 6 + dir] = occ;
            t.sideSolid[id * 6 + dir] = solid || opaqueCube;
        }

        if (model == null) {
            return;
        }
        if (t.fullCube[id]) {
            t.generalOffset[id] = meta.size() / 3;
            int written = 0;
            for (int dir = 0; dir < TerrainDirs.COUNT; dir++) {
                List<BakedQuad> quads = safeQuads(model, state, EnumFacing.VALUES[dir]);
                if (quads.isEmpty()) {
                    continue;
                }
                if (quads.size() == 1 && hasCanonicalFullSpriteUv(quads.get(0))) {
                    BakedQuad quad = quads.get(0);
                    TextureAtlasSprite sprite = quad.getSprite();
                    t.faceTexture[id * 6 + dir] = sprites.indexFor(sprite);
                    t.faceCell[id * 6 + dir] = sprites.cellFor(sprite);
                    t.faceTint[id * 6 + dir] = quad.getTintIndex() != -1;
                } else {
                    // Routing invariant (ruling section 5.3): a face whose UVs are a partial
                    // rectangle or rotated/flipped - or which has several quads - must not be
                    // absorbed by the packed axis path; it escapes into the general pool.
                    t.faceGeneral[id * 6 + dir] = true;
                    for (BakedQuad quad : quads) {
                        if (appendQuad(positions, meta, uvs, atlasUvs, needsCorrection, sprites, quad)) {
                            written++;
                        }
                    }
                }
            }
            t.generalCount[id] = written;
            if (written == 0) {
                t.generalOffset[id] = 0;
            }
            return;
        }
        // General geometry: the model's unculled quads plus every facing's quads.
        List<BakedQuad> all = new ArrayList<>();
        all.addAll(safeQuads(model, state, null));
        for (EnumFacing facing : EnumFacing.VALUES) {
            List<BakedQuad> quads = safeQuads(model, state, facing);
            if (!quads.isEmpty()) {
                // Face textures stay recorded for non-cube states too: the fluid emitter
                // (SectionMesher.emitFluids) reads faceTexture for fluid states, which are
                // never full cubes, and an unrecorded entry reads as sprite index 0 (the
                // unknown sprite) - fluid surfaces rendered as the missing-texture pattern
                // (Gate G1 Step 2 run, 2026-11-28). The axis path never sees these states
                // (its boards are fullCube-only), so recording here affects fluids alone.
                BakedQuad quad = quads.get(0);
                TextureAtlasSprite sprite = quad.getSprite();
                int dir = facing.getIndex();
                t.faceTexture[id * 6 + dir] = sprites.indexFor(sprite);
                t.faceCell[id * 6 + dir] = sprites.cellFor(sprite);
                t.faceTint[id * 6 + dir] = quad.getTintIndex() != -1;
            }
            all.addAll(quads);
        }
        t.generalOffset[id] = meta.size() / 3;
        int written = 0;
        boolean liquid = state.getMaterial().isLiquid();
        TextureAtlasSprite liquidStill = liquid ? fluidSprite(state, true) : null;
        TextureAtlasSprite liquidFlow = liquid ? fluidSprite(state, false) : null;
        for (BakedQuad quad : all) {
            if (appendQuad(positions, meta, uvs, atlasUvs, needsCorrection, sprites, quad)) {
                written++;
                if (liquid && quad.getSprite() != null
                        && "missingno".equals(quad.getSprite().getIconName())) {
                    // A liquid state's model quads carry the missing sprite (vanilla
                    // renders fluids from texture-map sprites, never from the model),
                    // so substitute the fluid's own sprite in the pool record: still
                    // for the horizontal faces, flowing for the sides. A model that
                    // offers a real sprite (a modded fluid's own model) keeps it.
                    int normalPacked = meta.get(meta.size() - 2);
                    float ny = org.soutalab.celtium.core.geometry.GeneralQuad
                            .unpackNormalComponent(normalPacked, 10);
                    TextureAtlasSprite sprite = Math.abs(ny) > 0.5f ? liquidStill : liquidFlow;
                    if (sprite != null) {
                        meta.set(meta.size() - 3, sprites.indexFor(sprite));
                    }
                }
            }
        }
        t.generalCount[id] = written;
        if (written == 0) {
            t.generalOffset[id] = 0;
        }
        if (state.getMaterial().isLiquid()) {
            // Vanilla never renders fluids through baked models (BlockFluidRenderer
            // tessellates them from texture-map sprites fetched by name); a liquid
            // state's model only offers the missing sprite (measured in the Gate G1
            // Step 2 separation run: VanillaModelWrapper quads are all "missingno"),
            // and the model-based recording above faithfully stored that sprite.
            // Resolve the fluid's own sprites the same way vanilla does and let them
            // have the final word as the face textures: still on the horizontal
            // faces, flowing on the sides. (Liquids are never full cubes, so this
            // seat - the end of the general path - is the only one they reach.)
            TextureAtlasSprite still = fluidSprite(state, true);
            TextureAtlasSprite flow = fluidSprite(state, false);
            for (int dir = 0; dir < TerrainDirs.COUNT; dir++) {
                TextureAtlasSprite sprite = dir == TerrainDirs.UP || dir == TerrainDirs.DOWN
                        ? still : flow;
                if (sprite != null) {
                    t.faceTexture[id * 6 + dir] = sprites.indexFor(sprite);
                    t.faceCell[id * 6 + dir] = sprites.cellFor(sprite);
                }
            }
        }
    }

    /** The canonical full-sprite corner assignment, in corner order: (0,0), (0,1), (1,1), (1,0). */
    private static final float[] CANONICAL_U = {0.0f, 0.0f, 1.0f, 1.0f};
    private static final float[] CANONICAL_V = {0.0f, 1.0f, 1.0f, 0.0f};
    private static final float CANONICAL_EPSILON = 1e-4f;

    /**
     * Whether the quad's UVs are exactly the canonical unrotated full-sprite assignment - the
     * only UV shape the packed axis path may absorb (ruling section 5.3). A quad with no usable
     * UV element counts as canonical (there is no partial UV to lose); a quad whose declared
     * sprite is missing or degenerate does not (its UVs cannot be trusted to the cell path).
     */
    private static boolean hasCanonicalFullSpriteUv(BakedQuad quad) {
        TextureAtlasSprite sprite = quad.getSprite();
        if (sprite == null) {
            return false;
        }
        float du = sprite.getMaxU() - sprite.getMinU();
        float dv = sprite.getMaxV() - sprite.getMinV();
        if (du <= 0.0f || dv <= 0.0f) {
            return false;
        }
        float[] uv = new float[8];
        if (!extractAtlasUvs(quad, uv)) {
            return true;
        }
        for (int c = 0; c < 4; c++) {
            float localU = (uv[c * 2] - sprite.getMinU()) / du;
            float localV = (uv[c * 2 + 1] - sprite.getMinV()) / dv;
            if (Math.abs(localU - CANONICAL_U[c]) > CANONICAL_EPSILON
                    || Math.abs(localV - CANONICAL_V[c]) > CANONICAL_EPSILON) {
                return false;
            }
        }
        return true;
    }

    /**
     * Reads the quad's four corner atlas UVs (8 floats: u, v per corner) from its vertex data,
     * using the {@code EnumUsage.UV} element with index 0. False when the format carries no
     * such element, the element is not FLOAT, or the vertex data is too short.
     */
    private static boolean extractAtlasUvs(BakedQuad quad, float[] out) {
        int[] vertexData = quad.getVertexData();
        VertexFormat format = quad.getFormat();
        int uvOffset = -1;
        boolean uvFloat = false;
        int elementByteOffset = 0;
        for (VertexFormatElement element : format.getElements()) {
            if (element.getUsage() == VertexFormatElement.EnumUsage.UV && element.getIndex() == 0) {
                uvOffset = elementByteOffset / 4;
                uvFloat = element.getType() == VertexFormatElement.EnumType.FLOAT;
            }
            elementByteOffset += element.getSize();
        }
        int strideInts = format.getIntegerSize();
        if (uvOffset < 0 || !uvFloat || vertexData.length < strideInts * 4) {
            return false;
        }
        for (int corner = 0; corner < 4; corner++) {
            int base = corner * strideInts + uvOffset;
            out[corner * 2] = Float.intBitsToFloat(vertexData[base]);
            out[corner * 2 + 1] = Float.intBitsToFloat(vertexData[base + 1]);
        }
        return true;
    }

    private static int layerOf(IBlockState state) {
        Block block = state.getBlock();
        try {
            if (block.canRenderInLayer(state, BlockRenderLayer.TRANSLUCENT)) {
                return TerrainLayer.TRANSLUCENT.ordinal();
            }
            if (block.canRenderInLayer(state, BlockRenderLayer.CUTOUT)
                    || block.canRenderInLayer(state, BlockRenderLayer.CUTOUT_MIPPED)) {
                return TerrainLayer.CUTOUT.ordinal();
            }
        } catch (Throwable ignored) {
        }
        return TerrainLayer.SOLID.ordinal();
    }

    /** Whether the model is exactly one full-face axis-aligned full-sprite quad per facing. */
    private static boolean modelIsFullCubeFaces(IBakedModel model, IBlockState state) {
        for (int dir = 0; dir < TerrainDirs.COUNT; dir++) {
            List<BakedQuad> quads = safeQuads(model, state, EnumFacing.VALUES[dir]);
            if (quads.size() != 1 || !quadIsFullFace(quads.get(0), dir)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether one quad is the full face of the unit cube on its facing's plane, sampling
     * its whole sprite with the sprite's U driven by the face's U axis alone and V by the
     * V axis alone (either direction per axis) - the only shape the axis path reproduces
     * exactly. Partial UVs, rotated UVs and off-plane corners all fail.
     */
    private static boolean quadIsFullFace(BakedQuad quad, int dir) {
        final float eps = 1.0e-4f;
        int[] vertexData = quad.getVertexData();
        VertexFormat format = quad.getFormat();
        int positionOffset = -1;
        int uvOffset = -1;
        int strideInts = format.getIntegerSize();
        int elementByteOffset = 0;
        for (VertexFormatElement element : format.getElements()) {
            if (element.getUsage() == VertexFormatElement.EnumUsage.POSITION
                    && element.getType() == VertexFormatElement.EnumType.FLOAT) {
                positionOffset = elementByteOffset / 4;
            }
            if (element.getUsage() == VertexFormatElement.EnumUsage.UV
                    && element.getType() == VertexFormatElement.EnumType.FLOAT) {
                uvOffset = elementByteOffset / 4;
            }
            elementByteOffset += element.getSize();
        }
        if (positionOffset < 0 || uvOffset < 0 || vertexData.length < strideInts * 4) {
            return false;
        }
        TextureAtlasSprite sprite = quad.getSprite();
        if (sprite == null) {
            return false;
        }
        int axis = TerrainDirs.AXIS[dir];
        int uAxis = TerrainDirs.uAxis(dir);
        int vAxis = TerrainDirs.vAxis(dir);
        float plane = TerrainDirs.NEGATIVE[dir] ? 0.0f : 1.0f;
        float[] uByA = {Float.NaN, Float.NaN};
        float[] vByB = {Float.NaN, Float.NaN};
        boolean[][] seen = new boolean[2][2];
        for (int corner = 0; corner < 4; corner++) {
            int base = corner * strideInts;
            float[] pos = {
                    Float.intBitsToFloat(vertexData[base + positionOffset]),
                    Float.intBitsToFloat(vertexData[base + positionOffset + 1]),
                    Float.intBitsToFloat(vertexData[base + positionOffset + 2])};
            if (Math.abs(pos[axis] - plane) > eps) {
                return false;
            }
            int a = Math.round(pos[uAxis]);
            int b = Math.round(pos[vAxis]);
            if (a < 0 || a > 1 || b < 0 || b > 1
                    || Math.abs(pos[uAxis] - a) > eps || Math.abs(pos[vAxis] - b) > eps) {
                return false;
            }
            float u = Float.intBitsToFloat(vertexData[base + uvOffset]);
            float v = Float.intBitsToFloat(vertexData[base + uvOffset + 1]);
            if (!Float.isNaN(uByA[a]) && Math.abs(uByA[a] - u) > eps) {
                return false;
            }
            if (!Float.isNaN(vByB[b]) && Math.abs(vByB[b] - v) > eps) {
                return false;
            }
            uByA[a] = u;
            vByB[b] = v;
            seen[a][b] = true;
        }
        for (int a = 0; a < 2; a++) {
            for (int b = 0; b < 2; b++) {
                if (!seen[a][b]) {
                    return false;
                }
            }
        }
        boolean uEdges = Math.abs(uByA[0] - sprite.getMinU()) <= eps
                && Math.abs(uByA[1] - sprite.getMaxU()) <= eps
                || Math.abs(uByA[0] - sprite.getMaxU()) <= eps
                && Math.abs(uByA[1] - sprite.getMinU()) <= eps;
        boolean vEdges = Math.abs(vByB[0] - sprite.getMinV()) <= eps
                && Math.abs(vByB[1] - sprite.getMaxV()) <= eps
                || Math.abs(vByB[0] - sprite.getMaxV()) <= eps
                && Math.abs(vByB[1] - sprite.getMinV()) <= eps;
        return uEdges && vEdges;
    }

    /** The atlas sprite vanilla's fluid renderer would use for this liquid state. */
    private static TextureAtlasSprite fluidSprite(IBlockState state, boolean still) {
        String name = null;
        Block block = state.getBlock();
        if (block instanceof net.minecraftforge.fluids.IFluidBlock fluidBlock
                && fluidBlock.getFluid() != null) {
            net.minecraft.util.ResourceLocation loc = still
                    ? fluidBlock.getFluid().getStill() : fluidBlock.getFluid().getFlowing();
            if (loc != null) {
                name = loc.toString();
            }
        }
        if (name == null) {
            Material material = state.getMaterial();
            if (material == Material.WATER) {
                name = still ? "minecraft:blocks/water_still" : "minecraft:blocks/water_flow";
            } else if (material == Material.LAVA) {
                name = still ? "minecraft:blocks/lava_still" : "minecraft:blocks/lava_flow";
            }
        }
        if (name == null) {
            return null;
        }
        try {
            return Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite(name);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static IBakedModel modelFor(IBlockState state) {
        try {
            return Minecraft.getMinecraft().getBlockRendererDispatcher().getModelForState(state);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static List<BakedQuad> safeQuads(IBakedModel model, IBlockState state, EnumFacing facing) {
        try {
            List<BakedQuad> quads = model.getQuads(state, facing, 0L);
            return quads == null ? List.of() : quads;
        } catch (Throwable ignored) {
            return List.of();
        }
    }

    /** The centroid tolerance of ruling section 5.2-4, in atlas UV space. */
    private static final float CENTROID_EPSILON = 1e-3f;

    /**
     * Appends one baked quad to the pool; false when the vertex format carries no positions.
     * The quad's corner UVs are extracted, normalised against its sprite and packed Q1.15
     * (ruling section 5.2). A quad whose sprite is missing/degenerate or does not contain its
     * own UV centroid is provisionally packed against what it declared and flagged for the
     * post-build correction ({@link #correctMisdeclaredSprites}); a quad with no usable UV
     * element gets the canonical full-sprite UV.
     */
    private static boolean appendQuad(List<Float> positions, List<Integer> meta, List<Integer> uvs,
                                      List<Float> atlasUvs, List<Integer> needsCorrection,
                                      AtlasSpriteIndex sprites, BakedQuad quad) {
        int[] vertexData = quad.getVertexData();
        VertexFormat format = quad.getFormat();
        int positionOffset = -1;
        int normalOffset = -1;
        int strideInts = format.getIntegerSize();
        // VertexFormatElement carries no offset getter in 1.12.2; offsets accumulate in element
        // order (the same walk CeltiumVertexFormats uses to validate a format).
        int elementByteOffset = 0;
        for (VertexFormatElement element : format.getElements()) {
            if (element.getUsage() == VertexFormatElement.EnumUsage.POSITION
                    && element.getType() == VertexFormatElement.EnumType.FLOAT) {
                positionOffset = elementByteOffset / 4;
            }
            if (element.getUsage() == VertexFormatElement.EnumUsage.NORMAL) {
                normalOffset = elementByteOffset / 4;
            }
            elementByteOffset += element.getSize();
        }
        if (positionOffset < 0 || vertexData.length < strideInts * 4) {
            return false;
        }
        int poolIndex = meta.size() / 3;
        for (int corner = 0; corner < 4; corner++) {
            int base = corner * strideInts + positionOffset;
            positions.add(Float.intBitsToFloat(vertexData[base]));
            positions.add(Float.intBitsToFloat(vertexData[base + 1]));
            positions.add(Float.intBitsToFloat(vertexData[base + 2]));
        }
        int normalPacked;
        if (normalOffset >= 0) {
            int packed = vertexData[normalOffset];
            float nx = (byte) (packed & 0xFF) / 127.0f;
            float ny = (byte) ((packed >>> 8) & 0xFF) / 127.0f;
            float nz = (byte) ((packed >>> 16) & 0xFF) / 127.0f;
            normalPacked = org.soutalab.celtium.core.geometry.GeneralQuad.packNormal(nx, ny, nz);
        } else {
            net.minecraft.util.math.Vec3i vec = quad.getFace().getDirectionVec();
            normalPacked = org.soutalab.celtium.core.geometry.GeneralQuad.packNormal(
                    vec.getX(), vec.getY(), vec.getZ());
        }
        meta.add(sprites.indexFor(quad.getSprite()));
        meta.add(normalPacked);
        meta.add(quad.getTintIndex() != -1 ? 1 : 0);

        float[] cornerUv = new float[8];
        boolean hasUv = extractAtlasUvs(quad, cornerUv);
        TextureAtlasSprite sprite = quad.getSprite();
        float du = sprite == null ? 0.0f : sprite.getMaxU() - sprite.getMinU();
        float dv = sprite == null ? 0.0f : sprite.getMaxV() - sprite.getMinV();
        boolean spriteUsable = sprite != null && du > 0.0f && dv > 0.0f;
        if (hasUv) {
            for (int i = 0; i < 8; i++) {
                atlasUvs.add(cornerUv[i]);
            }
            float centroidU = (cornerUv[0] + cornerUv[2] + cornerUv[4] + cornerUv[6]) / 4.0f;
            float centroidV = (cornerUv[1] + cornerUv[3] + cornerUv[5] + cornerUv[7]) / 4.0f;
            boolean centroidInside = spriteUsable
                    && centroidU >= sprite.getMinU() - CENTROID_EPSILON
                    && centroidU <= sprite.getMaxU() + CENTROID_EPSILON
                    && centroidV >= sprite.getMinV() - CENTROID_EPSILON
                    && centroidV <= sprite.getMaxV() + CENTROID_EPSILON;
            if (!spriteUsable || !centroidInside) {
                needsCorrection.add(poolIndex);
            }
            for (int c = 0; c < 4; c++) {
                if (spriteUsable) {
                    uvs.add(org.soutalab.celtium.core.geometry.GeneralQuad.packUv(
                            (cornerUv[c * 2] - sprite.getMinU()) / du,
                            (cornerUv[c * 2 + 1] - sprite.getMinV()) / dv));
                } else {
                    uvs.add(org.soutalab.celtium.core.geometry.GeneralQuad.FULL_SPRITE_UV_PACKED[c]);
                }
            }
        } else {
            for (int i = 0; i < 8; i++) {
                atlasUvs.add(Float.NaN);
            }
            for (int c = 0; c < 4; c++) {
                uvs.add(org.soutalab.celtium.core.geometry.GeneralQuad.FULL_SPRITE_UV_PACKED[c]);
            }
        }
        return true;
    }

    // ---------------------------------------------------------------------------------------------
    // BlockFaceData
    // ---------------------------------------------------------------------------------------------

    @Override
    public boolean isAir(int state) {
        return state < 0 || state >= stateCount || air[state];
    }

    @Override
    public boolean isFullCube(int state) {
        return state >= 0 && state < stateCount && fullCube[state];
    }

    @Override
    public boolean occludes(int state, int dir) {
        return state >= 0 && state < stateCount && occludes[state * 6 + dir];
    }

    @Override
    public boolean cullsSameState(int state) {
        return state >= 0 && state < stateCount && cullsSame[state];
    }

    @Override
    public int layer(int state) {
        return state >= 0 && state < stateCount ? layer[state] : TerrainLayer.SOLID.ordinal();
    }

    @Override
    public int faceTexture(int state, int dir) {
        return state >= 0 && state < stateCount ? faceTexture[state * 6 + dir] : 0;
    }

    @Override
    public int faceCell(int state, int dir) {
        return state >= 0 && state < stateCount ? faceCell[state * 6 + dir] : 0;
    }

    @Override
    public boolean faceUsesTint(int state, int dir) {
        return state >= 0 && state < stateCount && faceTint[state * 6 + dir];
    }

    @Override
    public boolean faceEscapesToGeneral(int state, int dir) {
        return state >= 0 && state < stateCount && faceGeneral[state * 6 + dir];
    }

    @Override
    public boolean isLeaves(int state) {
        return state >= 0 && state < stateCount && leaves[state];
    }

    @Override
    public boolean isLiquid(int state) {
        return state >= 0 && state < stateCount && liquid[state];
    }

    @Override
    public boolean isSideSolid(int state, int dir) {
        return state >= 0 && state < stateCount && sideSolid[state * 6 + dir];
    }

    @Override
    public boolean noGreedyWhenPack(int state) {
        return state >= 0 && state < stateCount && noGreedy[state];
    }

    @Override
    public short mcEntityId(int state) {
        return state >= 0 && state < stateCount ? mcEntity[state] : 0;
    }

    @Override
    public int lightValue(int state) {
        return state >= 0 && state < stateCount ? lightEmission[state] & 0xFF : 0;
    }

    @Override
    public float aoLightValue(int state) {
        return state >= 0 && state < stateCount ? aoValue[state] : 1.0f;
    }

    @Override
    public boolean isTranslucent(int state) {
        return state >= 0 && state < stateCount && translucent[state];
    }

    @Override
    public int generalQuadCount(int state) {
        return state >= 0 && state < stateCount ? generalCount[state] : 0;
    }

    @Override
    public void readGeneralQuad(int state, int quadIndex, ModelQuadView out) {
        int poolIndex = generalOffset[state] + quadIndex;
        System.arraycopy(quadPositions, poolIndex * 12, out.positions, 0, 12);
        out.textureIndex = quadMeta[poolIndex * 3];
        out.normalPacked = quadMeta[poolIndex * 3 + 1];
        out.usesTint = quadMeta[poolIndex * 3 + 2] != 0;
        out.noGreedyWhenPack = noGreedy[state];
        System.arraycopy(quadUvs, poolIndex * 4, out.uvPacked, 0, 4);
    }

    /** Whether any face or general quad of the state takes a tint (the scanner resolves colours only then). */
    public boolean stateUsesTint(int state) {
        if (state < 0 || state >= stateCount) {
            return false;
        }
        for (int dir = 0; dir < TerrainDirs.COUNT; dir++) {
            if (faceTint[state * 6 + dir]) {
                return true;
            }
        }
        for (int q = 0; q < generalCount[state]; q++) {
            if (quadMeta[(generalOffset[state] + q) * 3 + 2] != 0) {
                return true;
            }
        }
        return false;
    }

    public int stateCount() {
        return stateCount;
    }

    private static final class Tables {
        int size;
        boolean[] air;
        boolean[] fullCube;
        boolean[] occludes;
        boolean[] sideSolid;
        boolean[] cullsSame;
        byte[] layer;
        boolean[] leaves;
        boolean[] liquid;
        boolean[] noGreedy;
        short[] mcEntity;
        float[] aoValue;
        boolean[] translucent;
        byte[] lightEmission;
        int[] faceTexture;
        int[] faceCell;
        boolean[] faceTint;
        boolean[] faceGeneral;
        int[] generalOffset;
        int[] generalCount;
        float[] quadPositions;
        int[] quadMeta;
        int[] quadUvs;

        void allocate() {
            air = new boolean[size];
            fullCube = new boolean[size];
            occludes = new boolean[size * 6];
            sideSolid = new boolean[size * 6];
            cullsSame = new boolean[size];
            layer = new byte[size];
            leaves = new boolean[size];
            liquid = new boolean[size];
            noGreedy = new boolean[size];
            mcEntity = new short[size];
            aoValue = new float[size];
            java.util.Arrays.fill(aoValue, 1.0f);
            translucent = new boolean[size];
            lightEmission = new byte[size];
            faceTexture = new int[size * 6];
            faceCell = new int[size * 6];
            faceTint = new boolean[size * 6];
            faceGeneral = new boolean[size * 6];
            generalOffset = new int[size];
            generalCount = new int[size];
        }
    }
}
