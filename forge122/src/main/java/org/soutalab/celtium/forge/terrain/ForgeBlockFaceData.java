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
    private final boolean[] noGreedy;
    private final short[] mcEntity;
    private final int[] faceTexture;       // [state * 6 + dir]
    private final int[] faceCell;          // [state * 6 + dir]
    private final boolean[] faceTint;      // [state * 6 + dir]
    private final int[] generalOffset;     // per state, into the quad pool
    private final int[] generalCount;
    private final float[] quadPositions;   // pool: 12 floats per quad
    private final int[] quadMeta;          // pool: texture, normalPacked, tintFlag per quad

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
        this.noGreedy = tables.noGreedy;
        this.mcEntity = tables.mcEntity;
        this.faceTexture = tables.faceTexture;
        this.faceCell = tables.faceCell;
        this.faceTint = tables.faceTint;
        this.generalOffset = tables.generalOffset;
        this.generalCount = tables.generalCount;
        this.quadPositions = tables.quadPositions;
        this.quadMeta = tables.quadMeta;
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
        for (int id = 0; id < tables.size; id++) {
            IBlockState state = states.get(id);
            if (state == null) {
                tables.air[id] = true;
                continue;
            }
            fillRow(tables, sprites, id, state, positions, meta);
        }
        tables.quadPositions = new float[positions.size()];
        for (int i = 0; i < positions.size(); i++) {
            tables.quadPositions[i] = positions.get(i);
        }
        tables.quadMeta = new int[meta.size()];
        for (int i = 0; i < meta.size(); i++) {
            tables.quadMeta[i] = meta.get(i);
        }
        return new ForgeBlockFaceData(sprites, tables);
    }

    private static void fillRow(Tables t, AtlasSpriteIndex sprites, int id, IBlockState state,
                                List<Float> positions, List<Integer> meta) {
        Block block = state.getBlock();
        Material material = state.getMaterial();
        t.air[id] = material == Material.AIR;
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
        t.fullCube[id] = opaque && fullBox;
        for (int dir = 0; dir < TerrainDirs.COUNT; dir++) {
            EnumFacing facing = EnumFacing.VALUES[dir];
            boolean occ = t.fullCube[id];
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
            t.sideSolid[id * 6 + dir] = solid || t.fullCube[id];
        }

        IBakedModel model = modelFor(state);
        if (model == null) {
            return;
        }
        if (t.fullCube[id]) {
            for (int dir = 0; dir < TerrainDirs.COUNT; dir++) {
                List<BakedQuad> quads = safeQuads(model, state, EnumFacing.VALUES[dir]);
                if (!quads.isEmpty()) {
                    BakedQuad quad = quads.get(0);
                    TextureAtlasSprite sprite = quad.getSprite();
                    t.faceTexture[id * 6 + dir] = sprites.indexFor(sprite);
                    t.faceCell[id * 6 + dir] = sprites.cellFor(sprite);
                    t.faceTint[id * 6 + dir] = quad.getTintIndex() != -1;
                }
            }
            return;
        }
        // General geometry: the model's unculled quads plus every facing's quads.
        List<BakedQuad> all = new ArrayList<>();
        all.addAll(safeQuads(model, state, null));
        for (EnumFacing facing : EnumFacing.VALUES) {
            all.addAll(safeQuads(model, state, facing));
        }
        t.generalOffset[id] = meta.size() / 3;
        int written = 0;
        for (BakedQuad quad : all) {
            if (appendQuad(positions, meta, sprites, quad)) {
                written++;
            }
        }
        t.generalCount[id] = written;
        if (written == 0) {
            t.generalOffset[id] = 0;
        }
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

    /** Appends one baked quad to the pool; false when the vertex format carries no positions. */
    private static boolean appendQuad(List<Float> positions, List<Integer> meta,
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
    public boolean isLeaves(int state) {
        return state >= 0 && state < stateCount && leaves[state];
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
        boolean[] noGreedy;
        short[] mcEntity;
        int[] faceTexture;
        int[] faceCell;
        boolean[] faceTint;
        int[] generalOffset;
        int[] generalCount;
        float[] quadPositions;
        int[] quadMeta;

        void allocate() {
            air = new boolean[size];
            fullCube = new boolean[size];
            occludes = new boolean[size * 6];
            sideSolid = new boolean[size * 6];
            cullsSame = new boolean[size];
            layer = new byte[size];
            leaves = new boolean[size];
            noGreedy = new boolean[size];
            mcEntity = new short[size];
            faceTexture = new int[size * 6];
            faceCell = new int[size * 6];
            faceTint = new boolean[size * 6];
            generalOffset = new int[size];
            generalCount = new int[size];
        }
    }
}
