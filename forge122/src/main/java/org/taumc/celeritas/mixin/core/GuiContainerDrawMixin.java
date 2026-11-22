package org.taumc.celeritas.mixin.core;

import net.minecraft.client.gui.inventory.GuiContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;

/**
 * Times a container screen's draw for the frame's pass split, as the outer half of the
 * {@code screen} bracket.
 * <p>
 * {@code GuiContainer.drawScreen} overrides {@code GuiScreen.drawScreen}: it draws the background,
 * calls {@code super.drawScreen} (which the base mixin brackets), and then renders the inventory's
 * item stacks itself. Bracketing only the base method would time the super call and miss the item
 * rendering - the heaviest part of the screen, and the part a HUD-heavy census exists to measure -
 * so the override is bracketed here as well, and {@code FramePassTimings} keeps the bracket as a
 * depth so the nested base bracket neither closes it early nor double-counts its union.
 */
@Mixin(GuiContainer.class)
public abstract class GuiContainerDrawMixin {

    @Inject(method = "func_73863_a", at = @At("HEAD"), require = 0)
    private void celtium$containerTimedBegin(int mouseX, int mouseY, float partialTicks,
            org.spongepowered.asm.mixin.injection.callback.CallbackInfo info) {
        org.soutalab.celtium.vulkan.FramePassTimings.begin("screen");
    }

    @Inject(method = "func_73863_a", at = @At("RETURN"), require = 0)
    private void celtium$containerTimedEnd(int mouseX, int mouseY, float partialTicks,
            org.spongepowered.asm.mixin.injection.callback.CallbackInfo info) {
        org.soutalab.celtium.vulkan.FramePassTimings.end("screen");
    }
}
