package org.taumc.celeritas.mixin.features.render.tileentity;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityPiston;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.taumc.celeritas.impl.CeltiumLightCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(TileEntityRendererDispatcher.class)
public class TileEntityRenderDispatcherMixin {
    /**
     * @author embeddedt
     * @reason Allow some invalid TEs to still be rendered. Modern vanilla
     * versions do this for all TEs, but we cannot do that here as mods may rely on their TESR not being invoked
     * when the TE is invalid.
     */
    @WrapOperation(method = "getRenderer(Lnet/minecraft/tileentity/TileEntity;)Lnet/minecraft/client/renderer/tileentity/TileEntitySpecialRenderer;", at = @At(value = "INVOKE", target = "Lnet/minecraft/tileentity/TileEntity;isInvalid()Z"))
    private boolean allowSomeInvalidTESRs(TileEntity te, Operation<Boolean> original) {
        if (te instanceof TileEntityPiston) {
            // Pistons invalidate their TE before the chunk renderer has finished the chunk update
            return false;
        }
        return original.call(te);
    }

    /**
     * The light the dispatcher sets before every block entity's renderer, through the per-tick light cache: the call
     * ran once per block entity per frame and {@code World.getCombinedLight} was ~7% of the client thread on the
     * qmagnet scene. Same value as the original between light writes (the cache drops on a tick and on any write).
     */
    @WrapOperation(method = "render(Lnet/minecraft/tileentity/TileEntity;FI)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/World;getCombinedLight(Lnet/minecraft/util/math/BlockPos;I)I"))
    private int celtium$cachedBlockEntityLight(World world, BlockPos pos, int lightValue, Operation<Integer> original) {
        if (lightValue != 0) {
            return original.call(world, pos, lightValue);
        }
        return CeltiumLightCache.combinedLight(world, pos);
    }
}
