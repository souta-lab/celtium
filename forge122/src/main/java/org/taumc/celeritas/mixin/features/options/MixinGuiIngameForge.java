package org.taumc.celeritas.mixin.features.options;

import net.minecraftforge.client.GuiIngameForge;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.taumc.celeritas.CeleritasVintage;

@Mixin(GuiIngameForge.class)
public class MixinGuiIngameForge {
    @Redirect(method = "renderGameOverlay", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;isFancyGraphicsEnabled()Z"))
    private boolean celeritas$redirectVignette() {
        return CeleritasVintage.options().quality.enableVignette;
    }

    /**
     * The distribution build has to be able to say which Vulkan device and driver it is running on, and the debug
     * overlay is where a user looks. The status string already existed for the backend; nothing printed it, so the
     * report could name the device while the screen could not. This injects at RETURN rather than redirecting
     * anything: a second {@code @Redirect} here would fight every other mod that targets the same call.
     */
    @org.spongepowered.asm.mixin.injection.Inject(method = "renderGameOverlay", at = @At("RETURN"), require = 0)
    private void celtium$drawVulkanDeviceLine(float partialTicks,
            org.spongepowered.asm.mixin.injection.callback.CallbackInfo info) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
        if (mc == null || mc.gameSettings == null || !mc.gameSettings.showDebugInfo) {
            return;
        }
        String line = org.soutalab.celtium.forge.TerrainBackendStatus.describe();
        if (line == null || line.isEmpty()) {
            return;
        }
        mc.fontRenderer.drawStringWithShadow("Celtium: " + line, 2, 2, 0xFFFFFF);
    }
}