package org.taumc.celeritas.mixin.core.terrain;

import net.minecraft.block.BlockLeaves;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import org.embeddedt.embeddium.impl.gui.SodiumGameOptions.LeafCullingMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.taumc.celeritas.CeleritasVintage;
import org.taumc.celeritas.impl.render.terrain.compile.pipeline.LeafCulling;
import org.taumc.celeritas.impl.render.terrain.compile.pipeline.LeafCullingCensus;

/**
 * The face-suppression seat of celtium's native leaf culling (III-1): the entrance of
 * {@code BlockLeaves.shouldSideBeRendered}, the one point both build paths consult - the
 * vanilla dispatcher path (the default) and the fast renderer's face loop - so the
 * suppression is placed here rather than inside {@code renderModel} alone, where the
 * default configuration would never fire it.
 *
 * <p>The hook only ever suppresses: when {@link LeafCulling#shouldSuppressFace} holds it
 * answers false, and in every other case it leaves vanilla's answer untouched (a face
 * vanilla would not draw is never forced on). Mode NONE returns immediately, which makes
 * the option a complete kill switch. The decision itself lives in {@link LeafCulling}; this
 * class carries no condition of its own. The target is named in both the MCP and the SRG
 * form (the dual-form rule: a single-form hook is silently unbound in the other
 * environment), and {@code require} is stated explicitly so a rename fails loudly instead
 * of dropping the hook.
 */
@Mixin(BlockLeaves.class)
public class BlockLeavesMixin {
    @Inject(method = {"shouldSideBeRendered", "func_176225_a"}, at = @At("HEAD"), cancellable = true, require = 1)
    private void celeritas$cullLeafFace(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side,
                                        CallbackInfoReturnable<Boolean> cir) {
        LeafCullingMode mode = CeleritasVintage.options().quality.leafCullingMode;

        if (mode == LeafCullingMode.NONE) {
            return;
        }

        if (LeafCulling.shouldSuppressFace(world, pos, side, mode)) {
            LeafCullingCensus.recordSuppressedFace();
            cir.setReturnValue(false);
        }
    }
}
