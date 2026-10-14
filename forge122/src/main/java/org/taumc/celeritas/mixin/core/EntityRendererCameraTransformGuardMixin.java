package org.taumc.celeritas.mixin.core;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Skips the camera transform while the client has a world but no player, which vanilla's render loop reaches.
 * <p>
 * Measured 2026-10-15 (leg {@code gv-cohort-fix131}, after the fog, viewpoint, splash, atlas and fence guards):
 * the client died with {@code NullPointerException: Cannot read field "field_71080_cy" because
 * "this.field_78531_r.field_71439_g" is null} at {@code EntityRenderer.setupCameraTransform(EntityRenderer.java:740)},
 * that line being {@code this.mc.player.prevTimeInPortal + (this.mc.player.timeInPortal - ...) * partialTicks}.
 * <p>
 * The window is real and vanilla's own: {@code Minecraft.runGameLoop} calls
 * {@code EntityRenderer.updateCameraAndRender} under nothing but {@code !skipRenderWorld} (Minecraft.java:1205-1209),
 * while {@code loadWorld} leaves {@code player} as it was - null, when the client has just joined - until the server's
 * spawn packet creates it. Vanilla dereferences {@code mc.player} unguarded in that window rather than guarding it.
 * <p>
 * The guard is placed here rather than on {@code renderWorld} because that method carries this project's own
 * HEAD/RETURN pair for pass accounting (EntityRendererMixin), and cancelling it would leave that pair unbalanced. The
 * only other injection into this method is a RETURN camera-state capture, which is right to skip when there is no
 * camera: what is skipped is the projection and modelview setup, so the previous frame's matrices carry over, the
 * same thing the fog guard's comment describes for the fog colour. The test is {@code mc.player} alone, the value the
 * stack proves is null; a null render view entity with a live player is a different window, and it is already
 * answered without skipping the camera transform by the fog guard and the viewpoint guard.
 */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererCameraTransformGuardMixin {

    @Inject(method = "func_78479_a", at = @At("HEAD"), cancellable = true, require = 0)
    private void celtium$skipCameraTransformWithoutPlayer(float partialTicks, int pass, CallbackInfo info) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.world == null || mc.player == null) {
            info.cancel();
        }
    }
}
