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
 * Both {@code loadWorld} overloads are hooked at their head rather than their return, and that is the whole point:
 * the join handler received its packet twice and returned from neither (leg {@code gv-cohort-fix147}, probe
 * {@code CELTIUM_JOIN}), so the question is whether {@code loadWorld} is entered and never left, or never reached at
 * all. A head line answers it; a return line cannot, because a method that never returns never logs.
 * <p>
 * See {@link CeltiumWorldEntryWatch} for why the record - if it is taken - has to be taken at this moment rather than
 * tested at the frame boundary.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftWorldAssignedWatchMixin {

    @Inject(method = "func_71403_a", at = @At("HEAD"), require = 0)
    private void celtium$noteWorldAssigned(WorldClient world, CallbackInfo info) {
        CeltiumWorldEntryWatch.noteWorldAssigned(Minecraft.getMinecraft());
    }

    @Inject(method = "func_71353_a", at = @At("HEAD"), require = 0)
    private void celtium$noteWorldAssignedWithMessage(WorldClient world, String loadingMessage, CallbackInfo info) {
        CeltiumWorldEntryWatch.noteWorldAssigned(Minecraft.getMinecraft());
    }
}
