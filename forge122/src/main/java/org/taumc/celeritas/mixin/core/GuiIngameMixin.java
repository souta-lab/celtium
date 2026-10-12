package org.taumc.celeritas.mixin.core;

import net.minecraftforge.client.GuiIngameForge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;

/**
 * Times the in-game HUD overlay for the frame's pass split (plan §0.227).
 * <p>
 * The target is <b>Forge's</b> {@code GuiIngameForge}, not vanilla's {@code GuiIngame}: measured, the HUD class in a
 * Cleanroom client is the Forge one, and its {@code func_175180_a} overrides vanilla's without calling {@code super},
 * so a hook on the base class never fires and the pass reads 0.0 ms while the HUD is plainly in the frame. That is
 * exactly the kind of silent zero the pass split exists to make visible.
 * <p>
 * The user's capture puts {@code gui} at a constant ~13.7 ms per frame - about half the median frame - and it does not
 * grow in the spike frames, which makes it a constant tax rather than a spike source. Bracketing it here is what lets
 * our own legs say whether that holds for the build we are measuring.
 */
@Mixin(GuiIngameForge.class)
public abstract class GuiIngameMixin {

    @Inject(method = "func_175180_a", at = @At("HEAD"), require = 0)
    private void celtium$guiTimedBegin(float partialTicks,
            org.spongepowered.asm.mixin.injection.callback.CallbackInfo info) {
        org.soutalab.celtium.vulkan.FramePassTimings.begin("gui");
    }

    @Inject(method = "func_175180_a", at = @At("RETURN"), require = 0)
    private void celtium$guiTimedEnd(float partialTicks,
            org.spongepowered.asm.mixin.injection.callback.CallbackInfo info) {
        org.soutalab.celtium.vulkan.FramePassTimings.end("gui");
    }
}
