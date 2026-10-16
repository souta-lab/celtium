package org.taumc.celeritas.mixin.core;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Which background a screen draws, and whether a world was there when it asked.
 * <p>
 * The measurement this exists for: the videoSettings scene's GUI-stage frame is vanilla {@code GuiVideoSettings} over
 * the OPTIONS_BACKGROUND texture, at pixel values that match that texture at the tint this code passes (dirt
 * 133,96,66 x 64/255 = 33,24,16, measured 16.7,12.4,8.2 at the same ratios). Vanilla only reaches that texture through
 * {@code drawWorldBackground}'s no-world branch, yet the fixture reports {@code world=true} and a populated world at
 * the capture, so the two cannot both be read as correct - and nothing in the frame says which screen asked, or
 * whether the world was there when it did. One bounded line per branch answers it.
 * <p>
 * Off unless {@code -Dceltium.probe.guiBackground=true}.
 */
@Mixin(GuiScreen.class)
public class GuiScreenBackgroundProbeMixin {

    @Unique
    private static final boolean celtium$PROBE = Boolean.getBoolean("celtium.probe.guiBackground");
    @Unique
    private static int celtium$probeLines;

    // Both spellings: the development jar carries source (MCP) names and the shipped one carries SRG, and a
    // require=0 injection that finds nothing is silent. Whichever matches reports; the other does not.
    @Inject(method = {"func_146278_c(I)V", "drawBackground(I)V"}, at = @At("HEAD"), require = 0)
    private void celtium$probeDrawBackground(int tint, CallbackInfo info) {
        celtium$report("drawBackground", tint);
    }

    @Inject(method = {"func_146270_b(I)V", "drawWorldBackground(I)V"}, at = @At("HEAD"), require = 0)
    private void celtium$probeDrawWorldBackground(int tint, CallbackInfo info) {
        Minecraft mc = Minecraft.getMinecraft();
        boolean world = mc != null && mc.world != null;
        celtium$report("drawWorldBackground world=" + world + " branch=" + (world ? "gradient" : "drawBackground(tint)"),
                tint);
    }

    @Unique
    private static void celtium$report(String what, int tint) {
        if (!celtium$PROBE || celtium$probeLines >= 12) {
            return;
        }
        celtium$probeLines++;
        Minecraft mc = Minecraft.getMinecraft();
        System.out.println("[Celtium] CELTIUM_GUI_BACKGROUND screen="
                + (mc == null || mc.currentScreen == null ? "none" : mc.currentScreen.getClass().getSimpleName())
                + " " + what + " tint=" + tint + " world=" + (mc != null && mc.world != null));
    }
}
