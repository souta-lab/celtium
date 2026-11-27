package org.soutalab.celtium.forge.terrain;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EnumSkyBlock;
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

    public static void fill(WorldSectionSnapshot snapshot, IBlockAccess world,
                            ForgeBlockFaceData table, FluidloggedProbe fluids,
                            int sectionX, int sectionY, int sectionZ) {
        snapshot.fill(sectionX, sectionY, sectionZ);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int baseX = sectionX * 16 - 1;
        int baseY = sectionY * 16 - 1;
        int baseZ = sectionZ * 16 - 1;
        for (int y = 0; y < WorldSectionSnapshot.SIZE; y++) {
            for (int z = 0; z < WorldSectionSnapshot.SIZE; z++) {
                for (int x = 0; x < WorldSectionSnapshot.SIZE; x++) {
                    pos.setPos(baseX + x, baseY + y, baseZ + z);
                    IBlockState state = world.getBlockState(pos);
                    int id = Block.BLOCK_STATE_IDS.get(state);
                    int tint = WorldSectionSnapshot.NO_TINT;
                    if (table.stateUsesTint(id)) {
                        tint = resolveTint(world, pos, state);
                    }
                    int index = WorldSectionSnapshot.index(x, y, z);
                    snapshot.setCell(index, id,
                            world.getLightFor(EnumSkyBlock.BLOCK, pos),
                            world.getLightFor(EnumSkyBlock.SKY, pos),
                            tint);
                }
            }
        }
        if (fluids != null && fluids.present() && world instanceof net.minecraft.world.World realWorld) {
            Chunk chunk = realWorld.getChunk(sectionX, sectionZ);
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

    private static int resolveTint(IBlockAccess world, BlockPos pos, IBlockState state) {
        try {
            int color = Minecraft.getMinecraft().getBlockColors().getColor(state, world, pos, 0);
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
