package org.taumc.celeritas.mixin.core;

import net.minecraft.client.Minecraft;
import net.minecraftforge.client.GuiIngameForge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Leaves the HUD undrawn on frames that have no player.
 * <p>
 * Measured 2026-10-15 (leg {@code gv-cohort-fix142}, after the world render was taken out of the same frame):
 * {@code NullPointerException: Cannot invoke "EntityPlayerSP.func_184187_bx()" because "this.field_73839_d..."} at
 * {@code GuiIngameForge.func_175180_a(GuiIngameForge.java:121)} - the HUD reading {@code mc.player.ridingEntity} to
 * decide whether to draw the mount bar. With the world render skipped and the HUD still drawn, the client reached the
 * second half of the same frame.
 * <p>
 * The target is Forge's class rather than {@code GuiIngame}, because Forge overrides {@code renderGameOverlay} there
 * and an injection into the base class would never run - {@code javap} on the class the game loads confirms
 * {@code GuiIngame.func_175180_a(F)V} is the invoke in {@code EntityRenderer.func_181560_a}, and the crash stack names
 * the Forge override as what executes.
 * <p>
 * Cancelling is the honest answer rather than answering around the null: every element this method draws is derived
 * from the player - health, armour, food, air, the hotbar, the crosshair - and with no player there is nothing to
 * draw. The next frame with a player draws all of it again.
 */
@Mixin(GuiIngameForge.class)
public abstract class GuiIngameForgeNoPlayerGuardMixin {

    @Inject(method = "func_175180_a", at = @At("HEAD"), cancellable = true, require = 0)
    private void celtium$skipHudWithoutPlayer(float partialTicks, CallbackInfo info) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc != null && mc.world != null && mc.player == null) {
            info.cancel();
        }
    }
}
