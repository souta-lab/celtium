package org.soutalab.celtium.forge.terrain;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.chunk.Chunk;

/**
 * Fills a {@link WorldSectionSnapshot} from the live world: block states as table ids, block
 * and sky light, resolved tint colours, and - when the Fluidlogged API is present - the logged
 * fluids of the section's own cells. This is the only terrain-lane class that reads the world;
 * everything downstream works on the snapshot's flat arrays.
 * <p>
 * Tint resolution: when the face table says a state takes a tint, the colour is resolved here,
 * once per cell - the client's block colour handlers first (they cover biome grass/foliage/
 * water and every modded handler), with the biome's grass colour as the fallback when a handler
 * answers "no tint" for a state the model marks as tinted. Liquid states resolve through
 * {@link #resolveLiquidTint} instead: vanilla's fluid renderer applies the handlers to fluids
 * unconditionally (keyed by block, never by model tint index - a liquid's model quads carry
 * none), so a liquid is resolved whether or not its model marks it, and a handler answering
 * "no tint" means no tint, not the grass fallback.
 */
public final class ForgeSectionScanner {
    private ForgeSectionScanner() {
    }

    public static void fill(WorldSectionSnapshot snapshot, IBlockAccess world,
                            ForgeBlockFaceData table, FluidloggedProbe fluids,
                            int sectionX, int sectionY, int sectionZ) {
        snapshot.fill(sectionX, sectionY, sectionZ);
        // Direct chunk-storage reads. The first version of this scanner asked the world for
        // every one of the 18^3 cells (getBlockState + getCombinedLight per cell, each a
        // chunk-map lookup): ~2 ms per section on the render thread, which capped the build
        // pipeline at ~96 sections per 200 ms frame in the Gate G1 flight scene and kept the
        // warm window at ~171 ms. Reading the 3x3x3 chunk storages directly is the same
        // information the world getters return (the light rules below replicate
        // Chunk.getLightFor, including the null-storage canSeeSky rule), at array-read cost.
        net.minecraft.world.World realWorld = world instanceof net.minecraft.world.World w ? w : null;
        net.minecraft.world.chunk.storage.ExtendedBlockStorage[][][] storages =
                new net.minecraft.world.chunk.storage.ExtendedBlockStorage[3][3][3];
        Chunk[][] chunks = new Chunk[3][3];
        if (realWorld != null) {
            for (int cx = 0; cx < 3; cx++) {
                for (int cz = 0; cz < 3; cz++) {
                    Chunk chunk = realWorld.getChunk(sectionX + cx - 1, sectionZ + cz - 1);
                    chunks[cx][cz] = chunk;
                    if (chunk == null) {
                        continue;
                    }
                    net.minecraft.world.chunk.storage.ExtendedBlockStorage[] sections =
                            chunk.getBlockStorageArray();
                    for (int sy = 0; sy < 3; sy++) {
                        int sectionIndex = sectionY + sy - 1;
                        if (sectionIndex >= 0 && sectionIndex < sections.length) {
                            storages[cx][cz][sy] = sections[sectionIndex];
                        }
                    }
                }
            }
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int baseX = sectionX * 16 - 1;
        int baseY = sectionY * 16 - 1;
        int baseZ = sectionZ * 16 - 1;
        for (int y = 0; y < WorldSectionSnapshot.SIZE; y++) {
            int wy = baseY + y;
            for (int z = 0; z < WorldSectionSnapshot.SIZE; z++) {
                int wz = baseZ + z;
                for (int x = 0; x < WorldSectionSnapshot.SIZE; x++) {
                    int wx = baseX + x;
                    int index = WorldSectionSnapshot.index(x, y, z);
                    if (realWorld == null || wy < 0 || wy > 255) {
                        // Off the world (or no world handle): air, with the light the
                        // world's own out-of-range rule answers (sky full, block none).
                        snapshot.setCell(index, 0, 0, 15, WorldSectionSnapshot.NO_TINT);
                        snapshot.setNeighborBrightness(index, true);
                        continue;
                    }
                    int cx = (wx >> 4) - sectionX + 1;
                    int cz = (wz >> 4) - sectionZ + 1;
                    int sy = (wy >> 4) - sectionY + 1;
                    net.minecraft.world.chunk.storage.ExtendedBlockStorage storage =
                            storages[cx][cz][sy];
                    int lx = wx & 15;
                    int ly = wy & 15;
                    int lz = wz & 15;
                    if (storage == null) {
                        // No storage: air. Light follows Chunk.getLightFor's null-storage
                        // rule - sky is full where the column can see the sky, else dark.
                        Chunk chunk = chunks[cx][cz];
                        int sky = chunk != null && wy >= chunk.getHeightValue(lx, lz) ? 15 : 0;
                        snapshot.setCell(index, 0, 0, sky, WorldSectionSnapshot.NO_TINT);
                        snapshot.setNeighborBrightness(index, true);
                        continue;
                    }
                    IBlockState state = storage.get(lx, ly, lz);
                    int id = Block.BLOCK_STATE_IDS.get(state);
                    // The chunk's own light read: the same rule World.getCombinedLight
                    // delegates to, without the per-cell chunk-map lookup.
                    pos.setPos(wx, wy, wz);
                    Chunk chunk = chunks[cx][cz];
                    int blockLight = chunk == null ? 0
                            : chunk.getLightFor(net.minecraft.world.EnumSkyBlock.BLOCK, pos);
                    int skyLight = chunk == null ? 15
                            : chunk.getLightFor(net.minecraft.world.EnumSkyBlock.SKY, pos);
                    int tint = WorldSectionSnapshot.NO_TINT;
                    if (table.isLiquid(id)) {
                        pos.setPos(wx, wy, wz);
                        tint = resolveLiquidTint(world, pos, state);
                    } else if (table.stateUsesTint(id)) {
                        pos.setPos(wx, wy, wz);
                        tint = resolveTint(world, pos, state);
                    }
                    snapshot.setCell(index, id, blockLight, skyLight, tint);
                    snapshot.setNeighborBrightness(index, state.useNeighborBrightness());
                }
            }
        }
        // The snapshot's light is render light (what World.getCombinedLight returns per
        // cell), not the raw stored values: states that use neighbour brightness take
        // the maximum over their neighbours. The old scanner read getCombinedLight per
        // cell; this pass computes the same values from the raw arrays just captured.
        snapshot.applyRenderLightRule();
        if (fluids != null && fluids.present() && world instanceof net.minecraft.world.World realWorld2) {
            Chunk chunk = realWorld2.getChunk(sectionX, sectionZ);
            int[] sectionFluids = fluids.sectionFluids(chunk, sectionY);
            if (sectionFluids.length == 4096) {
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        for (int x = 0; x < 16; x++) {
                            int fluid = sectionFluids[x + 16 * (z + 16 * y)];
                            if (fluid != 0) {
                                snapshot.setFluid(
                                        WorldSectionSnapshot.index(x + 1, y + 1, z + 1), fluid);
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * The tint of a liquid cell: the block colour handlers' answer, taken as-is. Vanilla's
     * fluid renderer multiplies the fluid texel by the answer's channels, so an answer of
     * -1 (no handler registered for the block, or the handler declining) renders as white -
     * which is {@link WorldSectionSnapshot#NO_TINT} here. The grass fallback of
     * {@link #resolveTint} must never apply to liquids: it would dye handler-less liquids
     * grass-green.
     */
    private static int resolveLiquidTint(IBlockAccess world, BlockPos pos, IBlockState state) {
        try {
            return liquidTintFromColor(
                    Minecraft.getMinecraft().getBlockColors().colorMultiplier(state, world, pos, 0));
        } catch (Throwable ignored) {
            return WorldSectionSnapshot.NO_TINT;
        }
    }

    /** Maps a {@code BlockColors.colorMultiplier} answer to a liquid cell's snapshot tint. */
    static int liquidTintFromColor(int color) {
        return color == -1 ? WorldSectionSnapshot.NO_TINT : color & 0xFFFFFF;
    }

    private static int resolveTint(IBlockAccess world, BlockPos pos, IBlockState state) {
        try {
            int color = Minecraft.getMinecraft().getBlockColors().colorMultiplier(state, world, pos, 0);
            if (color != -1) {
                return color & 0xFFFFFF;
            }
        } catch (Throwable ignored) {
            // Fall through to the biome colour.
        }
        try {
            return world.getBiome(pos).getGrassColorAtPos(pos) & 0xFFFFFF;
        } catch (Throwable ignored) {
            return WorldSectionSnapshot.NO_TINT;
        }
    }
}
