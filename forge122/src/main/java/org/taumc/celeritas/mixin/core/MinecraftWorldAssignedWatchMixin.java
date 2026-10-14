package org.taumc.celeritas.mixin.core;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.taumc.celeritas.core.CeltiumWorldEntryWatch;

/**
 * Records the moment a world is assigned while there is no player yet.
 * <p>
 * Both {@code loadWorld} overloads are hooked because the join path uses the short one and it delegates to the long
 * one; recording twice is harmless, missing the moment is not. See {@link CeltiumWorldEntryWatch} for why the record
 * has to be taken here rather than tested at the frame boundary.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftWorldAssignedWatchMixin {

    @Inject(method = "func_71403_a", at = @At("RETURN"), require = 0)
    private void celtium$noteWorldAssigned(WorldClient world, CallbackInfo info) {
        CeltiumWorldEntryWatch.noteWorldAssigned(Minecraft.getMinecraft());
    }

    @Inject(method = "func_71353_a", at = @At("RETURN"), require = 0)
    private void celtium$noteWorldAssignedWithMessage(WorldClient world, String loadingMessage, CallbackInfo info) {
        CeltiumWorldEntryWatch.noteWorldAssigned(Minecraft.getMinecraft());
    }
}
