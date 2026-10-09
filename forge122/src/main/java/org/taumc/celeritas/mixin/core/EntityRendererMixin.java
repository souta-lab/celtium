package org.taumc.celeritas.mixin.core;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;
import org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackend;
import org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackends;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Supplies the world projection from Minecraft's own numbers instead of mirroring the GL call that sets it.
 * <p>
 * The mirror cannot see the world projection: {@code GlStateManager.matrixMode} and {@code loadIdentity} are hooked, so the
 * mirrored projection becomes the identity, and the call that would fill it is {@code Project.gluPerspective}, whose class
 * sits outside the transforming loader - measured, not assumed: its counter has stayed at zero. What that call computes is
 * four numbers, three of which are Minecraft state and the fourth the result of {@code func_78481_a}, the field-of-view
 * modifier. Hooking that method's return gives the fourth without any accessor machinery, and the backend does the
 * {@code gluPerspective} arithmetic itself.
 * <p>
 * This is the shape the direction asks for: a renderer that knows what a world projection is, rather than a layer that
 * mirrors a GL call it cannot reach.
 */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {

    @Inject(method = "func_78481_a", at = @At("RETURN"), require = 0)
    private void celtium$reportFieldOfView(float partialTicks, boolean useFovSetting,
            CallbackInfoReturnable<Float> info) {
        try {
            TerrainGpuBackend backend = TerrainGpuBackends.current();
            if (backend == null) {
                return;
            }
            Minecraft mc = Minecraft.getMinecraft();
            if (mc == null || mc.displayHeight <= 0) {
                return;
            }
            float aspect = (float) mc.displayWidth / (float) mc.displayHeight;
            // farPlaneDistance is renderDistanceChunks * 16 and the world pass uses farPlaneDistance * SQRT_2.
            float farPlane = mc.gameSettings.renderDistanceChunks * 16.0F * 1.41421356F;
            backend.applyDerivedProjection(info.getReturnValueF(), aspect, 0.05F, farPlane);
        } catch (Throwable ignored) {
            // A hint must never break the frame; the mirror simply keeps whatever it had.
        }
    }
}
