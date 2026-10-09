package org.taumc.celeritas.mixin.core;

import net.minecraft.client.shader.Framebuffer;
import org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackend;
import org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackends;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Tells the backend which render target a batch belongs to.
 * <p>
 * The group key held matrices, format, texture and state but not the target, so batches from different passes could merge
 * into one draw. The audit lists this as S10, and the framebuffer turned out not to come through {@code GlStateManager} at
 * all - its SRG source has no {@code glBindFramebuffer} - so this hooks the Minecraft class that does the binding.
 * <p>
 * Only the identity of the framebuffer is reported, not its GL name: a pass discriminator needs to tell targets apart, and
 * taking an object identity avoids reaching for a shadowed field or any other accessor machinery.
 */
@Mixin(Framebuffer.class)
public abstract class FramebufferMixin {

    @Inject(method = "func_147610_a", at = @At("HEAD"), require = 0)
    private void celtium$noteRenderTarget(boolean setViewport, CallbackInfo info) {
        try {
            TerrainGpuBackend backend = TerrainGpuBackends.current();
            if (backend != null) {
                // The main framebuffer is the only target the hybrid can honour: everything it draws goes into the shared
                // image, so a batch issued for another target cannot be drawn correctly and must be refused before
                // acceptance rather than taken and then dropped (audit S10/S14).
                net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
                boolean main = mc != null && mc.getFramebuffer() == (Object) this;
                backend.bindFramebuffer(System.identityHashCode(this), main);
            }
        } catch (Throwable ignored) {
            // A hint must never break the frame.
        }
    }
}
