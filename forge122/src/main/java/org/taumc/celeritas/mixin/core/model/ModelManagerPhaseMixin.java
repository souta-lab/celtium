package org.taumc.celeritas.mixin.core.model;

import net.minecraft.client.renderer.block.model.ModelManager;
import net.minecraft.client.resources.IResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.taumc.celeritas.impl.model.ModelPhaseTrace;

/**
 * Item 338a instrument: the outer bracket of one model reload. {@code require = 0} because the
 * Cleanroom-patched {@code ModelManager.onResourceManagerReload} is a known injection-sensitive
 * site (see scripts/modpack-compat/e2e_stellar_parallel_config.py); a silently missed bracket is
 * detected by the calibration legs' positive controls, never assumed away.
 */
@Mixin(ModelManager.class)
public class ModelManagerPhaseMixin {
    @Inject(method = "func_110549_a", at = @At("HEAD"), require = 0)
    private void celtium$modelPhaseBegin(IResourceManager resourceManager, CallbackInfo ci) {
        ModelPhaseTrace.modelBegin();
    }

    @Inject(method = "func_110549_a", at = @At("RETURN"), require = 0)
    private void celtium$modelPhaseEnd(IResourceManager resourceManager, CallbackInfo ci) {
        ModelPhaseTrace.modelEnd();
    }
}
