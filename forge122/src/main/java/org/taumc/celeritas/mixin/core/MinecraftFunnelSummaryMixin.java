package org.taumc.celeritas.mixin.core;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.taumc.celeritas.impl.CeltiumLightCache;

/**
 * Prints the combined-light funnel's final summary when a world is exited.
 * <p>
 * Both {@code loadWorld} overloads are hooked at their head, in the same shape as
 * {@link MinecraftWorldAssignedWatchMixin}, and only a {@code null} world acts: assigning a
 * world is not an exit, while disconnect and the game's own shutdown sequence both hand
 * {@code null} in. The print has to happen from inside the game like this - the funnel's
 * counters used to be printed only by a JVM shutdown hook, and in the validation harness the
 * captured console is already torn down by hook time, so the final values a leg reads
 * (item 430's {@code funnelRaised} among them) never arrived. See
 * {@link CeltiumLightCache#printFunnelSummaryOnWorldExit()} for the once-per-world guard.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftFunnelSummaryMixin {

    @Inject(method = "func_71403_a", at = @At("HEAD"), require = 0)
    private void celtium$printFunnelSummaryOnWorldExit(WorldClient world, CallbackInfo info) {
        if (world == null) {
            CeltiumLightCache.printFunnelSummaryOnWorldExit();
        }
    }

    @Inject(method = "func_71353_a", at = @At("HEAD"), require = 0)
    private void celtium$printFunnelSummaryOnWorldExitWithMessage(WorldClient world, String loadingMessage, CallbackInfo info) {
        if (world == null) {
            CeltiumLightCache.printFunnelSummaryOnWorldExit();
        }
    }
}
