package org.taumc.celeritas.mixin.core;

import net.minecraft.client.particle.ParticleManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;

/**
 * Times particle rendering, measured inside the world pass and reported but never subtracted from the residual.
 * <p>
 * Both of the game's particle loops are bracketed: {@code func_78874_a} ({@code renderParticles}, layers 0-2)
 * under {@code particles} and {@code func_78872_b} ({@code renderLitParticles}, layer 3) under
 * {@code particlesLit}. The lit bracket's injections are {@code require = 1}: they carry the measurement the
 * particle-culling work reads its denominator from, so a silent miss would read as "no lit particles" rather
 * than as a broken instrument. The original pair stays {@code require = 0}, as shipped.
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

    @Inject(method = "func_78872_b", at = @At("HEAD"), require = 1)
    private void celtium$particlesLitTimedBegin(net.minecraft.entity.Entity entityIn, float partialTicks,
            org.spongepowered.asm.mixin.injection.callback.CallbackInfo info) {
        org.soutalab.celtium.vulkan.FramePassTimings.begin("particlesLit");
    }

    @Inject(method = "func_78872_b", at = @At("RETURN"), require = 1)
    private void celtium$particlesLitTimedEnd(net.minecraft.entity.Entity entityIn, float partialTicks,
            org.spongepowered.asm.mixin.injection.callback.CallbackInfo info) {
        org.soutalab.celtium.vulkan.FramePassTimings.end("particlesLit");
    }
}
