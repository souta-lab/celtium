package org.taumc.celeritas.mixin.core;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Leaves the world undrawn on frames that have no camera at all, using vanilla's own switch.
 * <p>
 * Measured 2026-10-15 (leg {@code gv-cohort-fix136}, the first run whose camera guard applied):
 * {@code NullPointerException: Cannot read field "field_70142_S" because "entity" is null} inside
 * {@code EntityRenderer.renderWorld:1367}. {@code renderWorld} does try to repair itself - its first statements are
 * {@code if (mc.getRenderViewEntity() == null) mc.setRenderViewEntity(mc.player)} - so the window that reaches this
 * point is one where {@code mc.player} is null as well, and the repair has nothing to install. The world then has no
 * camera to be drawn from, and vanilla dereferences the null anyway.
 * <p>
 * {@code renderWorld} cannot simply be cancelled: this project's own HEAD/RETURN pair on it calls the backend's
 * {@code beginWorldPass} and {@code endWorldPass}, so a cancellation would leave the pass open. Vanilla has a switch
 * for exactly this case instead - {@code updateCameraAndRender} gates the world draw on
 * {@code if (!mc.skipRenderWorld)} (EntityRenderer.java:1098) - so the flag is set to the condition rather than
 * cancelling anything: when there is no player and no render view entity the world pass is skipped by vanilla, the
 * pair never runs and stays balanced, and the screen still draws.
 * <p>
 * The flag is written on every frame rather than once, so it clears itself as soon as a camera exists again; vanilla
 * only ever sets it false, and nothing else here writes it.
 */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererSkipWorldWithoutCameraMixin {

    @Inject(method = "func_78471_a", at = @At("HEAD"), require = 0)
    private void celtium$skipWorldWithoutCamera(float partialTicks, long nanoTime, CallbackInfo info) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc != null) {
            mc.skipRenderWorld = mc.player == null && mc.getRenderViewEntity() == null;
        }
    }
}
