package org.taumc.celeritas.impl.render.terrain.compile.pipeline;

import net.minecraft.block.Block;
import net.minecraft.block.BlockLeaves;
import net.minecraft.block.BlockPlanks;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.WorldType;
import net.minecraft.world.biome.Biome;
import org.embeddedt.embeddium.impl.gui.SodiumGameOptions.LeafCullingMode;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The truth tables of the native leaf-culling predicates (III-1), pinned against a stub
 * {@link IBlockAccess}: predicate A (surrounded) in both SOLID shapes, predicate B (the
 * depth-2 leaf run), the face-suppression decision per mode, and the material-swap decision
 * including its one asymmetry - a shader pack suppresses the swap and never the suppression.
 * The stub blocks answer {@code isSideSolid} from a field, so the tables do not depend on
 * vanilla's material rules; the leaves double is a real {@link BlockLeaves} so the
 * {@code instanceof} classification is exercised as production runs it.
 */
class LeafCullingTest {
    private static class StubBlock extends Block {
        private final boolean sideSolid;

        StubBlock(Material material, boolean sideSolid) {
            super(material);
            this.sideSolid = sideSolid;
        }

        @Override
        public boolean isSideSolid(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side) {
            return this.sideSolid;
        }
    }

    private static class StubLeaves extends BlockLeaves {
        public BlockPlanks.EnumType getWoodType(int meta) {
            return BlockPlanks.EnumType.OAK;
        }

        @Override
        public boolean isSideSolid(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side) {
            return false;
        }
    }

    private static final IBlockState AIR = new StubBlock(Material.AIR, false).getDefaultState();
    private static final IBlockState LEAVES = new StubLeaves().getDefaultState();
    private static final IBlockState SOLID_BLOCK = new StubBlock(Material.ROCK, true).getDefaultState();
    private static final IBlockState NON_SOLID_BLOCK = new StubBlock(Material.ROCK, false).getDefaultState();

    private static final class StubAccess implements IBlockAccess {
        private final Map<BlockPos, IBlockState> blocks = new HashMap<>();

        StubAccess set(BlockPos pos, IBlockState state) {
            this.blocks.put(pos, state);
            return this;
        }

        StubAccess fill(BlockPos from, BlockPos to, IBlockState state) {
            for (int x = from.getX(); x <= to.getX(); x++) {
                for (int y = from.getY(); y <= to.getY(); y++) {
                    for (int z = from.getZ(); z <= to.getZ(); z++) {
                        this.set(new BlockPos(x, y, z), state);
                    }
                }
            }
            return this;
        }

        @Override
        public IBlockState getBlockState(BlockPos pos) {
            return this.blocks.getOrDefault(pos, AIR);
        }

        @Override
        public TileEntity getTileEntity(BlockPos pos) {
            return null;
        }

        @Override
        public int getCombinedLight(BlockPos pos, int lightValue) {
            return 0;
        }

        @Override
        public boolean isAirBlock(BlockPos pos) {
            return this.getBlockState(pos) == AIR;
        }

        @Override
        public Biome getBiome(BlockPos pos) {
            return null;
        }

        @Override
        public int getStrongPower(BlockPos pos, EnumFacing direction) {
            return 0;
        }

        @Override
        public WorldType getWorldType() {
            return null;
        }
    }

    private static final EnumFacing[] HORIZONTAL_FACINGS =
            {EnumFacing.NORTH, EnumFacing.SOUTH, EnumFacing.WEST, EnumFacing.EAST};

    private static final BlockPos ORIGIN = new BlockPos(0, 0, 0);

    private static StubAccess leavesCube() {
        return new StubAccess().fill(new BlockPos(-1, -1, -1), new BlockPos(1, 1, 1), LEAVES);
    }

    // ---------------------------------------------------------------------------------------------
    // Predicate A
    // ---------------------------------------------------------------------------------------------

    @Test
    void surroundedByLeavesOnAllSixSides() {
        StubAccess world = leavesCube();
        assertTrue(LeafCulling.isSurrounded(world, ORIGIN, LeafCullingMode.SOLID));
        assertTrue(LeafCulling.isSurrounded(world, ORIGIN, LeafCullingMode.SOLID_AGGRESSIVE));
    }

    @Test
    void oneOpenSideDefeatsSolidButNotAggressiveWhenVertical() {
        StubAccess world = leavesCube().set(ORIGIN.up(), AIR);
        assertFalse(LeafCulling.isSurrounded(world, ORIGIN, LeafCullingMode.SOLID));
        // SOLID_AGGRESSIVE does not inspect the vertical neighbours at all.
        assertTrue(LeafCulling.isSurrounded(world, ORIGIN, LeafCullingMode.SOLID_AGGRESSIVE));
    }

    @Test
    void oneOpenHorizontalSideDefeatsBothSolidModes() {
        StubAccess world = leavesCube().set(ORIGIN.east(), AIR);
        assertFalse(LeafCulling.isSurrounded(world, ORIGIN, LeafCullingMode.SOLID));
        assertFalse(LeafCulling.isSurrounded(world, ORIGIN, LeafCullingMode.SOLID_AGGRESSIVE));
    }

    @Test
    void solidNeighboursBlockWithoutLeaves() {
        StubAccess world = new StubAccess();
        for (EnumFacing facing : EnumFacing.VALUES) {
            world.set(ORIGIN.offset(facing), SOLID_BLOCK);
        }
        assertTrue(LeafCulling.isSurrounded(world, ORIGIN, LeafCullingMode.SOLID));
    }

    @Test
    void nonSolidNeighbourDoesNotBlock() {
        StubAccess world = leavesCube().set(ORIGIN.west(), NON_SOLID_BLOCK);
        assertFalse(LeafCulling.isSurrounded(world, ORIGIN, LeafCullingMode.SOLID));
    }

    // ---------------------------------------------------------------------------------------------
    // Predicate B
    // ---------------------------------------------------------------------------------------------

    @Test
    void twoConsecutiveLeavesCullAtDepthTwo() {
        StubAccess world = new StubAccess()
                .set(ORIGIN.north(), LEAVES)
                .set(ORIGIN.offset(EnumFacing.NORTH, 2), LEAVES);
        assertTrue(LeafCulling.shouldCullSide(world, ORIGIN, EnumFacing.NORTH, 2));
    }

    @Test
    void secondCellOpenBreaksTheRun() {
        StubAccess world = new StubAccess().set(ORIGIN.north(), LEAVES);
        assertFalse(LeafCulling.shouldCullSide(world, ORIGIN, EnumFacing.NORTH, 2));
        // The same world answers true at depth 1: the depth is a real parameter.
        assertTrue(LeafCulling.shouldCullSide(world, ORIGIN, EnumFacing.NORTH, 1));
    }

    @Test
    void firstCellNotLeavesFailsAtOnce() {
        StubAccess world = new StubAccess()
                .set(ORIGIN.north(), SOLID_BLOCK)
                .set(ORIGIN.offset(EnumFacing.NORTH, 2), LEAVES);
        assertFalse(LeafCulling.shouldCullSide(world, ORIGIN, EnumFacing.NORTH, 2));
    }

    // ---------------------------------------------------------------------------------------------
    // Face suppression
    // ---------------------------------------------------------------------------------------------

    @Test
    void noneSuppressesNothing() {
        StubAccess world = leavesCube();
        for (EnumFacing facing : EnumFacing.VALUES) {
            assertFalse(LeafCulling.shouldSuppressFace(world, new BlockPos(0, 0, -1), facing, LeafCullingMode.NONE));
        }
    }

    @Test
    void hollowSuppressesFacesLookingIntoTwoLeaves() {
        StubAccess world = new StubAccess()
                .set(ORIGIN.north(), LEAVES)
                .set(ORIGIN.offset(EnumFacing.NORTH, 2), LEAVES);
        assertTrue(LeafCulling.shouldSuppressFace(world, ORIGIN, EnumFacing.NORTH, LeafCullingMode.HOLLOW));
        // Only one leaf to the south: no suppression.
        world.set(ORIGIN.south(), LEAVES);
        assertFalse(LeafCulling.shouldSuppressFace(world, ORIGIN, EnumFacing.SOUTH, LeafCullingMode.HOLLOW));
    }

    @Test
    void solidSuppressesTheFacePointingAtASurroundedNeighbour() {
        StubAccess world = leavesCube();
        // The leaf at (0,0,-1) facing south points at the cube's surrounded centre.
        assertTrue(LeafCulling.shouldSuppressFace(world, new BlockPos(0, 0, -1), EnumFacing.SOUTH, LeafCullingMode.SOLID));
        // Facing north it points out of the cube, at air: no suppression.
        assertFalse(LeafCulling.shouldSuppressFace(world, new BlockPos(0, 0, -1), EnumFacing.NORTH, LeafCullingMode.SOLID));
    }

    @Test
    void aggressiveSuppressionFollowsAggressiveSurrounding() {
        // Neighbour at ORIGIN is blocked horizontally by leaves, open above and below.
        StubAccess world = new StubAccess();
        for (EnumFacing facing : HORIZONTAL_FACINGS) {
            world.set(ORIGIN.offset(facing), LEAVES);
        }
        BlockPos viewer = new BlockPos(0, 0, -1);
        assertTrue(LeafCulling.shouldSuppressFace(world, viewer, EnumFacing.SOUTH, LeafCullingMode.SOLID_AGGRESSIVE));
        assertFalse(LeafCulling.shouldSuppressFace(world, viewer, EnumFacing.SOUTH, LeafCullingMode.SOLID));
    }

    // ---------------------------------------------------------------------------------------------
    // Material swap
    // ---------------------------------------------------------------------------------------------

    @Test
    void surroundedLeafSolidifiesInSolidMode() {
        StubAccess world = leavesCube();
        assertTrue(LeafCulling.shouldSolidify(LEAVES, world, ORIGIN, LeafCullingMode.SOLID, false));
    }

    @Test
    void shaderPackSuppressesTheSwapButNotFaceSuppression() {
        StubAccess world = leavesCube();
        assertFalse(LeafCulling.shouldSolidify(LEAVES, world, ORIGIN, LeafCullingMode.SOLID, true));
        // The asymmetry is part of the specification: suppression never consults shader state.
        assertTrue(LeafCulling.shouldSuppressFace(world, new BlockPos(0, 0, -1), EnumFacing.SOUTH, LeafCullingMode.SOLID));
    }

    @Test
    void hollowAndNoneNeverSolidify() {
        StubAccess world = leavesCube();
        assertFalse(LeafCulling.shouldSolidify(LEAVES, world, ORIGIN, LeafCullingMode.HOLLOW, false));
        assertFalse(LeafCulling.shouldSolidify(LEAVES, world, ORIGIN, LeafCullingMode.NONE, false));
    }

    @Test
    void nonLeafNeverSolidifies() {
        StubAccess world = leavesCube();
        assertFalse(LeafCulling.shouldSolidify(SOLID_BLOCK, world, ORIGIN, LeafCullingMode.SOLID, false));
    }

    @Test
    void aggressiveSolidifiesWithVerticalOpen() {
        StubAccess world = new StubAccess();
        for (EnumFacing facing : HORIZONTAL_FACINGS) {
            world.set(ORIGIN.offset(facing), LEAVES);
        }
        assertTrue(LeafCulling.shouldSolidify(LEAVES, world, ORIGIN, LeafCullingMode.SOLID_AGGRESSIVE, false));
        assertFalse(LeafCulling.shouldSolidify(LEAVES, world, ORIGIN, LeafCullingMode.SOLID, false));
    }

    // ---------------------------------------------------------------------------------------------
    // Census
    // ---------------------------------------------------------------------------------------------

    @Test
    void censusCountsBothEventKinds() {
        LeafCullingCensus census = new LeafCullingCensus(0);
        census.recordEvent(true);
        census.recordEvent(true);
        census.recordEvent(false);
        long[] snapshot = census.snapshot();
        assertTrue(snapshot[0] == 2 && snapshot[1] == 1);
    }
}
