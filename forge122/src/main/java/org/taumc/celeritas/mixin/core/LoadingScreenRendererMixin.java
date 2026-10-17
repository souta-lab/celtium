package org.taumc.celeritas.mixin.core;

import net.minecraft.client.LoadingScreenRenderer;
import net.minecraft.client.Minecraft;
import org.taumc.celeritas.interfaces.LoadingProgressState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Preserve native loading task state on resize; observe its real swaps only when the fixture opts in. */
@Mixin(LoadingScreenRenderer.class)
public abstract class LoadingScreenRendererMixin implements LoadingProgressState {
    @Shadow private String currentlyDisplayedText;
    @Shadow private String message;
    @Shadow private boolean loadingSuccess;
    @Unique private int celtium$captureFailures;

    @Override
    public String celtium$loadingTitle() {
        return this.currentlyDisplayedText;
    }

    @Override
    public String celtium$loadingMessage() {
        return this.message;
    }

    @Override
    public boolean celtium$loadingSuccess() {
        return this.loadingSuccess;
    }

    @Inject(method = "<init>(Lnet/minecraft/client/Minecraft;)V", at = @At("RETURN"))
    private void celtium$preserveLoadingTask(Minecraft mc, CallbackInfo ci) {
        // Minecraft.resize constructs this replacement before assigning mc.loadingScreen.
        // Copy task state, never the old resolution/FBO; the constructor has just sized new targets.
        if (Boolean.getBoolean("celtium.vulkan.glless") && mc.loadingScreen instanceof LoadingProgressState) {
            LoadingProgressState previous = (LoadingProgressState) mc.loadingScreen;
            this.currentlyDisplayedText = previous.celtium$loadingTitle();
            this.message = previous.celtium$loadingMessage();
            this.loadingSuccess = previous.celtium$loadingSuccess();
        }
    }

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
