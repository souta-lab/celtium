package org.taumc.celeritas.mixin.core;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.taumc.celeritas.impl.profile.GameLoopTiming;

/**
 * Times the client tick, head to return, for the (g) comparison's unattributed per-frame time.
 * <p>
 * The renderer's own instruments bracket rendering, not the client loop: the comparison leg left ~17.4 ms of a
 * 38.7 ms interval outside every bracket, and this scene runs 1.11 world ticks per frame, so the tick is the first
 * candidate and had no instrument at all. See {@link GameLoopTiming} for what it measures and what it does not.
 * <p>
 * {@code require = 0} on purpose: a configuration where this method has a different shape must not fail to start the
 * game over a diagnostic - the counter simply stays at zero, which the report says.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftTickTimingMixin {

    @Inject(method = "runTick", at = @At("HEAD"), require = 0)
    private void celtium$tickStarted(CallbackInfo ci) {
        GameLoopTiming.tickStarted();
    }

    @Inject(method = "runTick", at = @At("RETURN"), require = 0)
    private void celtium$tickEnded(CallbackInfo ci) {
        GameLoopTiming.tickEnded();
    }
}
