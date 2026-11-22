package org.taumc.celeritas.mixin.core;

import net.minecraft.client.gui.GuiScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;

/**
 * Times an open screen's draw for the frame's pass split, as the {@code screen} bracket.
 * <p>
 * The HUD overlay has been timed since the pass split existed ({@code GuiIngameMixin} on
 * {@code GuiIngameForge.renderGameOverlay}), but a screen - the inventory, a container, a chest -
 * is drawn by {@code GuiScreen.drawScreen}, a different method on a different class, so its batches
 * and flushes were in no HUD population at all. That gap is exactly the HUD-heavy case: a container
 * screen full of item icons is the heaviest UI a frame draws, and the pass split attributed it to
 * the residual. {@code GuiContainer} overrides this method and does its item rendering itself after
 * calling {@code super}, so the container has its own mixin ({@code GuiContainerDrawMixin}) and the
 * bracket is a depth in {@code FramePassTimings}, not a flag.
 */
@Mixin(GuiScreen.class)
public abstract class GuiScreenDrawMixin {

    @Inject(method = "func_73863_a", at = @At("HEAD"), require = 0)
    private void celtium$screenTimedBegin(int mouseX, int mouseY, float partialTicks,
            org.spongepowered.asm.mixin.injection.callback.CallbackInfo info) {
        org.soutalab.celtium.vulkan.FramePassTimings.begin("screen");
    }

    @Inject(method = "func_73863_a", at = @At("RETURN"), require = 0)
    private void celtium$screenTimedEnd(int mouseX, int mouseY, float partialTicks,
            org.spongepowered.asm.mixin.injection.callback.CallbackInfo info) {
        org.soutalab.celtium.vulkan.FramePassTimings.end("screen");
    }
}
