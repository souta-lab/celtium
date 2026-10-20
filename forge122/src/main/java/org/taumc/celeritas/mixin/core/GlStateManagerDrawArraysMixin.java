package org.taumc.celeritas.mixin.core;

import net.minecraft.client.renderer.GlStateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.taumc.celeritas.core.CeltiumNativeDraw;

/**
 * Takes Minecraft's own {@code glDrawArrays} call sites before they reach a GL frame.
 *
 * <p>{@code GlStateManager.func_187439_f} is the wrapper {@code VertexBuffer.drawArrays} and the other in-game callers
 * use. Measured on leg gv-affine1, this was the last vanilla path still arriving through the GL stack:
 * {@code GL11.glDrawArrays} -> {@code CeltiumGllessHint.glDrawArrays} -> {@code CeltiumGlStub.drawArrays}. Answering it
 * here removes those frames from the vanilla draw rather than shortening them.
 *
 * <p>{@code require = 0}: a build whose wrapper is renamed simply keeps the old path instead of failing.
 */
@Mixin(GlStateManager.class)
public abstract class GlStateManagerDrawArraysMixin {

    @Inject(method = "func_187439_f", at = @At("HEAD"), cancellable = true, require = 0)
    private static void celtium$drawArrays(int mode, int first, int count, CallbackInfo callback) {
        if (CeltiumNativeDraw.drawArrays(mode, first, count)) {
            callback.cancel();
        }
    }
}
