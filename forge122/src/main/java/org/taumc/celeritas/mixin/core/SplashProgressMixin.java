package org.taumc.celeritas.mixin.core;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The FML loading screen drives a SECOND GL context on a hidden shared drawable - there is no such context under
 * the GL-less window, and {@code DrawableGL.createSharedContext} throws an exception that the splash's own
 * disable path fails to recover from. Skipping {@code start} keeps the loading sequence going with the normal
 * (non-splash) progress path, exactly as a {@code splash.properties enabled=false} install behaves.
 */
@Mixin(targets = "net.minecraftforge.fml.client.SplashProgress")
public class SplashProgressMixin {

    @Inject(method = "start", at = @At("HEAD"), cancellable = true, require = 0)
    private static void celtium$skipSplashUnderGlless(CallbackInfo ci) {
        if (!"false".equalsIgnoreCase(System.getProperty("celtium.vulkan.glless"))) {
            System.out.println("[Celtium] CELTIUM_GLLESS splash progress skipped (no shared GL context)");
            ci.cancel();
        }
    }
}
