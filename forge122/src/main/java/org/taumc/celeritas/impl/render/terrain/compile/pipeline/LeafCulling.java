package org.taumc.celeritas.impl.render.terrain.compile.pipeline;

import net.minecraft.block.BlockLeaves;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import org.embeddedt.embeddium.impl.gui.SodiumGameOptions.LeafCullingMode;

import java.lang.reflect.Method;

/**
 * Celtium's native leaf culling (III-1): the behaviour of the Leaf Culling addon, implemented
 * inside celtium from its behaviour specification (design: OVERHAUL-III1-NATIVE-DESIGN-2026-11-26).
 * The addon jar carries no licence text; what was read from it is the specification below -
 * what is culled, under which conditions - and this class is an independent implementation of
 * that specification, not a port of its code.
 *
 * <p>Two mechanisms, both decided at chunk-build time so they apply to every consumer of the
 * built geometry (legacy registration, Tier-A, and the pack route alike):
 *
 * <ul>
 *   <li><b>Face suppression</b> - at the entrance of {@code BlockLeaves.shouldSideBeRendered}
 *   (see the mixin in {@code mixin/core/terrain}), a leaf face is suppressed - and only ever
 *   suppressed, never forced on - when:
 *     <ul>
 *       <li>HOLLOW: {@link #shouldCullSide} holds at depth 2 (the face looks into two or more
 *       consecutive leaves), or</li>
 *       <li>SOLID / SOLID_AGGRESSIVE: the neighbouring block the face points at is itself
 *       {@linkplain #isSurrounded surrounded}.</li>
 *     </ul></li>
 *   <li><b>Material swap</b> - in {@code VintageBlockRenderer.renderModel}, a leaf block that
 *   is surrounded (SOLID modes only) emits its quads into the SOLID layer's material instead
 *   of the leaves material, so it renders as an opaque block with its own texture. The swap -
 *   and only the swap - is suppressed while a shader pack is in use: under a pack, the pack
 *   owns the material-to-program mapping, and re-pointing leaves at the terrain program would
 *   misroute them. Face suppression does not consult shader state; that asymmetry is part of
 *   the specification.</li>
 * </ul>
 *
 * <p>The predicates are pure functions of an {@link IBlockAccess} and the mode passed in by
 * the caller; this class does not know where the setting lives.
 */
public final class LeafCulling {
    private LeafCulling() {
    }

    public static boolean isLeaves(IBlockState state) {
        return state.getBlock() instanceof BlockLeaves;
    }

    /**
     * Predicate A ("surrounded"): every checked direction from {@code pos} is blocked, where a
     * direction is blocked when the neighbouring cell holds leaves, or when the neighbouring
     * block's face towards {@code pos} is solid. SOLID checks all six directions;
     * SOLID_AGGRESSIVE checks only the four horizontal ones (the vertical neighbours pass
     * unconditionally). Air blocks nothing, so one open direction defeats the predicate.
     */
    public static boolean isSurrounded(IBlockAccess world, BlockPos pos, LeafCullingMode mode) {
        for (EnumFacing facing : EnumFacing.VALUES) {
            if (mode == LeafCullingMode.SOLID_AGGRESSIVE && facing.getAxis().isVertical()) {
                continue;
            }

            BlockPos neighborPos = pos.offset(facing);
            IBlockState neighbor = world.getBlockState(neighborPos);

            if (isLeaves(neighbor)) {
                continue;
            }

            if (!neighbor.isSideSolid(world, neighborPos, facing.getOpposite())) {
                return false;
            }
        }

        return true;
    }

    /**
     * Predicate B ("face culling", depth-parameterised): the cells at distance
     * {@code 1..depth} from {@code pos} along {@code facing} are all leaves. Distance 1 not
     * being leaves (air included) fails at once, as does any non-leaf cell further out. The
     * only depth the behaviour uses is 2.
     */
    public static boolean shouldCullSide(IBlockAccess world, BlockPos pos, EnumFacing facing, int depth) {
        for (int distance = 1; distance <= depth; distance++) {
            if (!isLeaves(world.getBlockState(pos.offset(facing, distance)))) {
                return false;
            }
        }

        return true;
    }

    /**
     * The face-suppression decision of the specification, for the leaf face at {@code pos}
     * facing {@code facing}. Returns true only when vanilla's answer may be overridden to
     * "do not render"; every other case leaves vanilla's answer untouched.
     */
    public static boolean shouldSuppressFace(IBlockAccess world, BlockPos pos, EnumFacing facing, LeafCullingMode mode) {
        switch (mode) {
            case NONE:
                return false;
            case HOLLOW:
                return shouldCullSide(world, pos, facing, 2);
            case SOLID:
            case SOLID_AGGRESSIVE:
                // The face points at the neighbouring block; it is suppressed when that
                // neighbour is itself surrounded.
                return isSurrounded(world, pos.offset(facing), mode);
            default:
                return false;
        }
    }

    /**
     * The material-swap decision of the specification, for the block being rendered: the
     * block is leaves, the mode is a SOLID mode, the block is surrounded, and no shader pack
     * is in use.
     */
    public static boolean shouldSolidify(IBlockState state, IBlockAccess world, BlockPos pos,
                                         LeafCullingMode mode, boolean shaderPackInUse) {
        if (mode != LeafCullingMode.SOLID && mode != LeafCullingMode.SOLID_AGGRESSIVE) {
            return false;
        }

        if (!isLeaves(state) || shaderPackInUse) {
            return false;
        }

        return isSurrounded(world, pos, mode);
    }

    // ---------------------------------------------------------------------------------------------
    // Shader-pack signal. The build context carries no existing pack state the forge122 side can
    // read (the pack host's own bridge is package-private to the Vulkan backend), so this is the
    // addon's own signal reproduced reflectively, in the shape of DemonicaTerrainBridge: the Iris
    // API answers whether a pack is in use, and any absence - no Iris, no API surface - answers
    // false. The landing leg's Demonica+BSL smoke (both modes) is the empirical check on this
    // probe; see the III-1 prep record.
    // ---------------------------------------------------------------------------------------------

    private static volatile boolean packProbeResolved;
    private static Object irisApi;
    private static Method packInUseMethod;

    public static boolean shaderPackInUse() {
        Method method = resolvePackProbe();
        if (method == null) {
            return false;
        }

        try {
            return Boolean.TRUE.equals(method.invoke(irisApi));
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Method resolvePackProbe() {
        if (packProbeResolved) {
            return packInUseMethod;
        }

        synchronized (LeafCulling.class) {
            if (!packProbeResolved) {
                try {
                    Class<?> cls = Class.forName("net.coderbot.iris.api.v0.IrisApi", false,
                            LeafCulling.class.getClassLoader());
                    irisApi = cls.getMethod("getInstance").invoke(null);
                    packInUseMethod = cls.getMethod("isShaderPackInUse");
                } catch (Throwable ignored) {
                    irisApi = null;
                    packInUseMethod = null;
                }
                packProbeResolved = true;
            }

            return packInUseMethod;
        }
    }
}
