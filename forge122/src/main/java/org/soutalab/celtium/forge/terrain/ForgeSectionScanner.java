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
 * answers "no tint" for a state the model marks as tinted.
 */
public final class ForgeSectionScanner {
    private ForgeSectionScanner() {
    }

    // The resolveTint share of the scan (G2-30's added instruments): fill accumulates the
    // tint resolutions of one section into locals and publishes them here at the end, so
    // the cell loop pays two clock reads per tinted cell and no bracket calls at all. The
    // driver drains both totals when it prints its setup-statistics window.
    private static long tintNanosTotal;
    private static long tintCellsTotal;

    /** {@return the tint-resolution nanoseconds accumulated since the last drain}, resetting the total. */
    static long drainTintNanos() {
        long value = tintNanosTotal;
        tintNanosTotal = 0L;
        return value;
    }

    /** {@return the tinted cells resolved since the last drain}, resetting the total. */
    static long drainTintCells() {
        long value = tintCellsTotal;
        tintCellsTotal = 0L;
        return value;
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
        // The storages' raw light bytes, in the same [cx][cz][sy] shape: the cell loop reads
        // light straight from these instead of two Chunk.getLightFor calls per cell (11,664
        // virtual calls per section). A null entry means "no array": for block light that
        // cannot happen on a live storage, and for sky light it is exactly the dimensions
        // whose provider has no sky light - the case getLightFor answers 0 for.
        byte[][][][] skyLightData = new byte[3][3][3][];
        byte[][][][] blockLightData = new byte[3][3][3][];
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
                            net.minecraft.world.chunk.storage.ExtendedBlockStorage storage =
                                    sections[sectionIndex];
                            storages[cx][cz][sy] = storage;
                            if (storage != null) {
                                net.minecraft.world.chunk.NibbleArray sky = storage.getSkyLight();
                                skyLightData[cx][cz][sy] = sky == null ? null : sky.getData();
                                net.minecraft.world.chunk.NibbleArray block = storage.getBlockLight();
                                blockLightData[cx][cz][sy] = block == null ? null : block.getData();
                            }
                        }
                    }
                }
            }
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        long tintNanos = 0L;
        long tintCells = 0L;
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
                    // The chunk's own light read, from the storage's raw NibbleArray bytes:
                    // Chunk.getLightFor's storage-present branches are exactly these reads -
                    // BLOCK is the block array's nibble, SKY is 0 when the sky array is null
                    // (a provider without sky light) and its nibble otherwise (verified
                    // against the decompiled Chunk / ExtendedBlockStorage / NibbleArray).
                    byte[] blockData = blockLightData[cx][cz][sy];
                    int blockLight = blockData == null ? 0 : lightNibble(blockData, lx, ly, lz);
                    byte[] skyData = skyLightData[cx][cz][sy];
                    int skyLight = skyData == null ? 0 : lightNibble(skyData, lx, ly, lz);
                    int tint = WorldSectionSnapshot.NO_TINT;
                    if (table.stateUsesTint(id)) {
                        pos.setPos(wx, wy, wz);
                        long tintStart = System.nanoTime();
                        tint = resolveTint(world, pos, state);
                        tintNanos += System.nanoTime() - tintStart;
                        tintCells++;
                    }
                    snapshot.setCell(index, id, blockLight, skyLight, tint);
                    snapshot.setNeighborBrightness(index, state.useNeighborBrightness());
                }
            }
        }
        // The snapshot's light must become render light (what World.getCombinedLight
        // returns per cell), not the raw stored values: states that use neighbour
        // brightness take the maximum over their neighbours. That pass is pure
        // snapshot-array work, so it no longer runs here on the render thread - the
        // build worker applies it at the head of TerrainBuildScheduler.build
        // (S2-b-perf CPU-3). The fluid pass below does not touch the light arrays,
        // so moving the rule past it changes nothing.
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
        tintNanosTotal += tintNanos;
        tintCellsTotal += tintCells;
    }

    /**
     * One cell's light value from a {@code NibbleArray}'s raw bytes, in the array's own
     * arithmetic (decompiled {@code NibbleArray.get}): the coordinate index is
     * {@code y << 8 | z << 4 | x}, an even index is the byte's low nibble, an odd index the
     * high nibble. Package-private so the equivalence with {@code NibbleArray.get} is
     * unit-testable without a world.
     */
    static int lightNibble(byte[] data, int x, int y, int z) {
        int index = (y << 8) | (z << 4) | x;
        int b = data[index >> 1];
        return (index & 1) == 0 ? b & 15 : (b >> 4) & 15;
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
