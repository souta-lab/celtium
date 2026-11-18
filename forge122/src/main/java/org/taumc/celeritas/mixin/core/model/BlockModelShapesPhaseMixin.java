package org.taumc.celeritas.mixin.core.model;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.BlockModelShapes;
import net.minecraft.client.renderer.block.model.IBakedModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.taumc.celeritas.impl.model.ModelPhaseTrace;

import java.util.Map;

/**
 * Item 338a instrument: the stateStore bracket. {@code reloadModels} materialises one baked
 * model reference per valid block state; its body is vanilla (the Forge patch touches a
 * neighbouring method, not this one).
 */
@Mixin(BlockModelShapes.class)
public class BlockModelShapesPhaseMixin {
    @Shadow
    private Map<IBlockState, IBakedModel> field_178129_a;

    @Inject(method = "func_178124_c", at = @At("HEAD"), require = 0)
    private void celtium$stateStoreBegin(CallbackInfo ci) {
        ModelPhaseTrace.stateStoreBegin();
    }

    @Inject(method = "func_178124_c", at = @At("RETURN"), require = 0)
    private void celtium$stateStoreEnd(CallbackInfo ci) {
        if (!ModelPhaseTrace.ENABLED) {
            return;
        }
        ModelPhaseTrace.stateStoreEnd(this.field_178129_a == null ? -1 : this.field_178129_a.size());
    }
}
