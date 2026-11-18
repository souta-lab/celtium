package org.taumc.celeritas.mixin.core.model;

import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ModelBakery;
import net.minecraft.client.renderer.block.model.ModelBlock;
import net.minecraft.client.renderer.block.model.ModelRotation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.taumc.celeritas.impl.model.ModelPhaseTrace;

/**
 * Item 338a instrument: the itemVariants bracket (parent of the nested itemModels phase in
 * {@link ModelLoaderPhaseMixin}) and {@code bakeModel} calls on the vanilla ModelBlock path,
 * which feed the same bake accumulation as the Forge {@code IModel.bake} loop.
 */
@Mixin(ModelBakery.class)
public class ModelBakeryPhaseMixin {
    @Inject(method = "func_177577_b", at = @At("HEAD"), require = 0)
    private void celtium$itemVariantsBegin(CallbackInfo ci) {
        ModelPhaseTrace.itemVariantsBegin();
    }

    @Inject(method = "func_177577_b", at = @At("RETURN"), require = 0)
    private void celtium$itemVariantsEnd(CallbackInfo ci) {
        ModelPhaseTrace.itemVariantsEnd();
    }

    @Inject(method = "bakeModel", at = @At("HEAD"), remap = false, require = 0)
    private void celtium$bakeModelBegin(ModelBlock model, ModelRotation transform, boolean uvLock,
            CallbackInfoReturnable<IBakedModel> cir) {
        ModelPhaseTrace.bakeCallBegin();
    }

    @Inject(method = "bakeModel", at = @At("RETURN"), remap = false, require = 0)
    private void celtium$bakeModelEnd(ModelBlock model, ModelRotation transform, boolean uvLock,
            CallbackInfoReturnable<IBakedModel> cir) {
        ModelPhaseTrace.bakeCallEnd();
    }
}
