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

    /**
     * Seeds the mirror once the camera transform is complete.
     * <p>
     * The hooks cannot have seen everything: `func_78479_a` applies `loadIdentity` and the camera transform, and any of it
     * that ran before the backend existed is lost to the mirror. This runs at the end of that method, when GL holds the
     * finished world matrix - the right moment, unlike the first batch of a frame, which is offered during a pass that has
     * already reset both stacks.
     */
    @Inject(method = "func_78479_a", at = @At("RETURN"), require = 0)
    private void celtium$seedMirrorAtCameraSetup(float partialTicks, int pass,
            org.spongepowered.asm.mixin.injection.callback.CallbackInfo info) {
        try {
            TerrainGpuBackend backend = TerrainGpuBackends.current();
            if (backend != null) {
                // The camera transform is complete here: this is the boundary the snapshot is taken at.
                backend.seedMirrorFromCameraSetup();
                backend.captureWorldSnapshot();
            }
        } catch (Throwable ignored) {
            // A hint must never break the frame.
        }
    }

    /**
     * The world pass's explicit boundaries.
     * <p>
     * The pass lifecycle is what decides whether a batch may be drawn natively: matrices are valid for drawing only between
     * these two points, and the snapshot taken at the camera boundary is what the batches inside them are drawn with. The
     * earlier flag was inferred from which projection was installed and proved unreliable - a first-person hand pass or a
     * custom framebuffer can install anything - so the lifecycle is stated rather than guessed.
     */
    @Inject(method = "func_175068_a", at = @At("HEAD"), require = 0)
    private void celtium$worldPassBegin(int pass, float partialTicks, long finishTimeNano,
            org.spongepowered.asm.mixin.injection.callback.CallbackInfo info) {
        try {
            TerrainGpuBackend backend = TerrainGpuBackends.current();
            if (backend != null) {
                backend.beginWorldPass();
            }
        } catch (Throwable ignored) {
        }
    }

    @Inject(method = "func_175068_a", at = @At("RETURN"), require = 0)
    private void celtium$worldPassEnd(int pass, float partialTicks, long finishTimeNano,
            org.spongepowered.asm.mixin.injection.callback.CallbackInfo info) {
        try {
            TerrainGpuBackend backend = TerrainGpuBackends.current();
            if (backend != null) {
                backend.endWorldPass();
            }
        } catch (Throwable ignored) {
        }
    }

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
