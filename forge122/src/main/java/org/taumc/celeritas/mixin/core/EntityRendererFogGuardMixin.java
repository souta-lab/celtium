package org.taumc.celeritas.mixin.core;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Skips {@code EntityRenderer.updateFogColor} while there is no render view entity.
 * <p>
 * Vanilla's {@code func_78466_h} calls {@code world.getSkyColor(mc.getRenderViewEntity(), ...)}
 * without a null check (EntityRenderer.java:1785). During world entry there is a real window where
 * the world exists but {@code getRenderViewEntity()} is still null, and the first
 * {@code renderWorld} frame in that window crashes the client with an NPE inside
 * {@code World.getSkyColorBody}. Reproduced twice on the GL (non-GL-less) comparison leg at world
 * load; the GL-less leg happened not to render a frame in that window.
 * <p>
 * Skipping the fog update for that frame is the same thing vanilla effectively does - there is no
 * fog color to compute without a camera entity, and the previous frame's fog values simply carry
 * over, which is what vanilla shows anyway when the method early-outs on other conditions.
 */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererFogGuardMixin {

    @Inject(method = "func_78466_h", at = @At("HEAD"), cancellable = true, require = 0)
    private void celtium$guardMissingRenderViewEntity(float partialTicks, CallbackInfo info) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.world == null || mc.getRenderViewEntity() == null) {
            info.cancel();
        }
    }
}
