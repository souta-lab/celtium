package org.taumc.celeritas.mixin.core;

import net.minecraft.client.particle.ParticleManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;

/**
 * Times particle rendering, measured inside the world pass and reported but never subtracted from the residual.
 */
@Mixin(ParticleManager.class)
public abstract class ParticleManagerMixin {

    @Inject(method = "func_78874_a", at = @At("HEAD"), require = 0)
    private void celtium$particlesTimedBegin(net.minecraft.entity.Entity entityIn, float partialTicks,
            org.spongepowered.asm.mixin.injection.callback.CallbackInfo info) {
        org.soutalab.celtium.vulkan.FramePassTimings.begin("particles");
    }

    @Inject(method = "func_78874_a", at = @At("RETURN"), require = 0)
    private void celtium$particlesTimedEnd(net.minecraft.entity.Entity entityIn, float partialTicks,
            org.spongepowered.asm.mixin.injection.callback.CallbackInfo info) {
        org.soutalab.celtium.vulkan.FramePassTimings.end("particles");
    }
}
