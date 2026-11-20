package org.taumc.celeritas.mixin.core;

import net.minecraft.client.particle.ParticleManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.soutalab.celtium.vulkan.TickSplit2Index;
import org.soutalab.celtium.vulkan.TickTimings;

/**
 * TickSplit2's {@code tickParticles} child (item 407): the particle manager's per-tick update, bracketed at
 * the method's own edges. This is the tick-side update ({@code updateEffects}); the render-side particle
 * brackets live in {@link ParticleManagerMixin} and are a different question.
 * <p>
 * {@code require = 0}, the tick instruments' rule: a differently shaped tree loses the split, not the game,
 * and the bracket's zero call count in the report says so.
 */
@Mixin(ParticleManager.class)
public abstract class ParticleManagerTickSplitMixin {

    @Inject(method = "updateEffects", at = @At("HEAD"), require = 0)
    private void celtium$tickParticlesBegin(CallbackInfo ci) {
        TickTimings.begin(TickSplit2Index.TICK_PARTICLES);
    }

    @Inject(method = "updateEffects", at = @At("RETURN"), require = 0)
    private void celtium$tickParticlesEnd(CallbackInfo ci) {
        TickTimings.end(TickSplit2Index.TICK_PARTICLES);
    }
}
