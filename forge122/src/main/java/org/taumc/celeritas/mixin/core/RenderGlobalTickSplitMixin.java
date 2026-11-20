package org.taumc.celeritas.mixin.core;

import net.minecraft.client.renderer.RenderGlobal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.soutalab.celtium.vulkan.TickSplit2Index;
import org.soutalab.celtium.vulkan.TickTimings;

/**
 * TickSplit2's {@code tickDrain} child (item 407): {@code RenderGlobal.updateClouds} bracketed at its own
 * edges, inside the {@code tickLvlRenderer} bracket the runTick call site carries. Item 336's Gate 0 already
 * attributed this drain at ~0% of the tick residual by sampling; this bracket is the direct re-check and the
 * negative control for the split - if it ever reads large, the sampling conclusion, not the bracket, is what
 * to doubt first.
 * <p>
 * {@code require = 0}, the tick instruments' rule: a differently shaped tree loses the split, not the game,
 * and the bracket's zero call count in the report says so.
 */
@Mixin(RenderGlobal.class)
public abstract class RenderGlobalTickSplitMixin {

    @Inject(method = "updateClouds", at = @At("HEAD"), require = 0)
    private void celtium$tickDrainBegin(CallbackInfo ci) {
        TickTimings.begin(TickSplit2Index.TICK_DRAIN);
    }

    @Inject(method = "updateClouds", at = @At("RETURN"), require = 0)
    private void celtium$tickDrainEnd(CallbackInfo ci) {
        TickTimings.end(TickSplit2Index.TICK_DRAIN);
    }
}
