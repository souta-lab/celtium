package org.soutalab.celtium.forge.mixin;

import net.minecraft.client.renderer.EntityRenderer;
import org.soutalab.celtium.forge.ui.ShaderPackAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The shader pack frame drive: at the head of every world pass, feed the active pack's
 * frame state and, while the frame owner has the native frame seat open, record the pack
 * frame through the runtime. Defensive by construction - the hook never breaks a frame.
 */
@Mixin(value = EntityRenderer.class, remap = false)
public abstract class ShaderPackFrameMixin {

    @Inject(method = "func_78471_a", at = @At("HEAD"), require = 0)
    private void celtium$packFrameHead(int renderPass, float partialTicks, long finishTimeNano,
                                       CallbackInfo ci) {
        try {
            ShaderPackAccess.onWorldPassBegin(partialTicks);
        } catch (Throwable ignored) {
            // Pack driving must never break the frame.
        }
    }
}
