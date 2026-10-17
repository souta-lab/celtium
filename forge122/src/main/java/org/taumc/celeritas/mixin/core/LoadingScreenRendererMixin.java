package org.taumc.celeritas.mixin.core;

import net.minecraft.client.LoadingScreenRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Optional fixture observation at the producer's swap, not at a later client/world tick. */
@Mixin(LoadingScreenRenderer.class)
public abstract class LoadingScreenRendererMixin {
    @Shadow private String currentlyDisplayedText;
    @Shadow private String message;
    @Unique private int celtium$captureFailures;

    @Inject(method = "setLoadingProgress(I)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/Minecraft;updateDisplay()V"), require = 0)
    private void celtium$captureProgressFrame(int progress, CallbackInfo ci) {
        if (!Boolean.getBoolean("celtium.validation.dumpLoadingProgress")) {
            return;
        }
        try {
            Class.forName("org.soutalab.celtium.validation.ValidationClient")
                    .getMethod("requestLoadingProgressDump", int.class, String.class, String.class)
                    .invoke(null, progress, this.currentlyDisplayedText, this.message);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException error) {
            if (this.celtium$captureFailures++ < 2) {
                System.out.println("[Celtium] CELTIUM_LOADING_PROGRESS_CAPTURE hook failed: " + error);
            }
        }
    }
}
