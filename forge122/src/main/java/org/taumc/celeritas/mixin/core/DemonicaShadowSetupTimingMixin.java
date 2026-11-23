package org.taumc.celeritas.mixin.core;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Item 690: the inside of the shadow phase's setup, split at the seams the item 648 census
 * fixed (worldrest-census-648.md section 7). The 648 census attributed 827.0 ms of the
 * 1890.1 ms slowest-16 worldRest total to the setup part of ShadowRenderer.renderShadows -
 * the work around the record, not the record itself (wpShadowPhase minus packRecordShadow).
 * These four brackets name that setup's parts so the next census can say which of them owns it.
 * <p>
 * The seams are the call sites inside renderShadows, read off Demonica 0.9.1's bytecode
 * (item690-instrument.md section 1), so the handlers take the target method's own parameters
 * and never declare a Demonica type: the Angelica Camera is declared for real (the stubs
 * source set's compile-time stub), because an {@code @Inject} handler must name every
 * parameter with its exact type - item 648's WR1/F1 legs proved widening it to {@code Object}
 * quarantines the group and omitting it crashes the apply.
 * <p>
 * {@code wpShadowMatrix} is the modelview build plus the perspective/ortho generation
 * (getShadowModelView up to the InternalShadowRenderingState.begin call). {@code wpShadowFrustum}
 * is the frustum construction as two sequential spans under one name - the terrain frustum
 * (createShadowFrustum through its Frustum position set, ordinal 0) and the entity frustum
 * (createEntityShadowFrustum through its own position set, ordinal 1). The two spans are
 * sequential, never nested, so the outermost-only accumulation stays exact. The frustum's
 * position set is a {@code Frustum.func_78547_a(DDD)} invoke; the ordinal selects which of
 * the method's two occurrences it is. {@code wpShadowTerrainSetup} is the Celeritas-side
 * terrain setup between the frustum and the GL state (enterManagedCode through the
 * setupGlState call - the viewport creation, the section-graph dirty mark, setupShadowTerrain).
 * {@code wpShadowTargetPrep} is setupGlState itself, the shadow targets' bind and GL state
 * preparation. All four are inclusive INSIDE names: reported, never subtracted, like their
 * parent wpShadowPhase - the census nets the contained names out of the per-frame profiles
 * offline, the same reconstruction pack-tail-2 used for its terrainRecord split.
 * <p>
 * What stays unnamed on purpose: the method header (the pipeline lookup, the tile-entity
 * list clears, the CURRENT_TARGETS assignment), CullingDataCache.saveState/restoreState, the
 * mipmap generation, restoreGlState and the shadow composite renderAll - none of them is
 * frustum, matrix or target preparation, so they read as the setup residual
 * (wpShadowPhase minus these four minus the already-named record) instead.
 */
@Pseudo
@Mixin(targets = "net.coderbot.iris.pipeline.ShadowRenderer")
public abstract class DemonicaShadowSetupTimingMixin {

    private static final String SHADOW_RENDERER =
            "Lnet/coderbot/iris/pipeline/ShadowRenderer;";
    private static final String FRUSTUM_SET_POSITION =
            "Lnet/minecraft/client/renderer/culling/Frustum;func_78547_a(DDD)V";

    @Inject(method = "renderShadows",
            at = @At(value = "INVOKE", target = SHADOW_RENDERER + "getShadowModelView()Lnet/coderbot/iris/gl/MatrixStack;"),
            require = 0)
    private void celtium$wpShadowMatrixTimedBegin(net.minecraft.client.renderer.EntityRenderer renderer,
            com.gtnewhorizons.angelica.compat.mojang.Camera camera, CallbackInfo info) {
        org.soutalab.celtium.vulkan.FramePassTimings.begin("wpShadowMatrix");
    }

    @Inject(method = "renderShadows",
            at = @At(value = "INVOKE",
                    target = "Lcom/gtnewhorizons/angelica/glsm/shadow/InternalShadowRenderingState;begin(Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;ZZZ)V"),
            require = 0)
    private void celtium$wpShadowMatrixTimedEnd(net.minecraft.client.renderer.EntityRenderer renderer,
            com.gtnewhorizons.angelica.compat.mojang.Camera camera, CallbackInfo info) {
        org.soutalab.celtium.vulkan.FramePassTimings.end("wpShadowMatrix");
    }

    @Inject(method = "renderShadows",
            at = @At(value = "INVOKE",
                    target = SHADOW_RENDERER + "createShadowFrustum(FLnet/coderbot/iris/shadows/frustum/FrustumHolder;)Lnet/coderbot/iris/shadows/frustum/FrustumHolder;"),
            require = 0)
    private void celtium$wpShadowFrustumTimedBegin(net.minecraft.client.renderer.EntityRenderer renderer,
            com.gtnewhorizons.angelica.compat.mojang.Camera camera, CallbackInfo info) {
        org.soutalab.celtium.vulkan.FramePassTimings.begin("wpShadowFrustum");
    }

    @Inject(method = "renderShadows",
            at = @At(value = "INVOKE", target = FRUSTUM_SET_POSITION, ordinal = 0, shift = At.Shift.AFTER),
            require = 0)
    private void celtium$wpShadowFrustumTimedEnd(net.minecraft.client.renderer.EntityRenderer renderer,
            com.gtnewhorizons.angelica.compat.mojang.Camera camera, CallbackInfo info) {
        org.soutalab.celtium.vulkan.FramePassTimings.end("wpShadowFrustum");
    }

    @Inject(method = "renderShadows",
            at = @At(value = "INVOKE",
                    target = SHADOW_RENDERER + "createEntityShadowFrustum(FLnet/coderbot/iris/shadows/frustum/FrustumHolder;)Lnet/coderbot/iris/shadows/frustum/FrustumHolder;"),
            require = 0)
    private void celtium$wpShadowFrustumEntityTimedBegin(net.minecraft.client.renderer.EntityRenderer renderer,
            com.gtnewhorizons.angelica.compat.mojang.Camera camera, CallbackInfo info) {
        org.soutalab.celtium.vulkan.FramePassTimings.begin("wpShadowFrustum");
    }

    @Inject(method = "renderShadows",
            at = @At(value = "INVOKE", target = FRUSTUM_SET_POSITION, ordinal = 1, shift = At.Shift.AFTER),
            require = 0)
    private void celtium$wpShadowFrustumEntityTimedEnd(net.minecraft.client.renderer.EntityRenderer renderer,
            com.gtnewhorizons.angelica.compat.mojang.Camera camera, CallbackInfo info) {
        org.soutalab.celtium.vulkan.FramePassTimings.end("wpShadowFrustum");
    }

    @Inject(method = "renderShadows",
            at = @At(value = "INVOKE",
                    target = "Lorg/embeddedt/embeddium/impl/gl/device/RenderDevice;enterManagedCode()V"),
            require = 0)
    private void celtium$wpShadowTerrainSetupTimedBegin(net.minecraft.client.renderer.EntityRenderer renderer,
            com.gtnewhorizons.angelica.compat.mojang.Camera camera, CallbackInfo info) {
        org.soutalab.celtium.vulkan.FramePassTimings.begin("wpShadowTerrainSetup");
    }

    @Inject(method = "renderShadows",
            at = @At(value = "INVOKE",
                    target = SHADOW_RENDERER + "setupGlState(Lorg/joml/Matrix4f;)V"),
            require = 0)
    private void celtium$wpShadowTerrainSetupTimedEnd(net.minecraft.client.renderer.EntityRenderer renderer,
            com.gtnewhorizons.angelica.compat.mojang.Camera camera, CallbackInfo info) {
        org.soutalab.celtium.vulkan.FramePassTimings.end("wpShadowTerrainSetup");
    }

    @Inject(method = "renderShadows",
            at = @At(value = "INVOKE",
                    target = SHADOW_RENDERER + "setupGlState(Lorg/joml/Matrix4f;)V"),
            require = 0)
    private void celtium$wpShadowTargetPrepTimedBegin(net.minecraft.client.renderer.EntityRenderer renderer,
            com.gtnewhorizons.angelica.compat.mojang.Camera camera, CallbackInfo info) {
        org.soutalab.celtium.vulkan.FramePassTimings.begin("wpShadowTargetPrep");
    }

    @Inject(method = "renderShadows",
            at = @At(value = "INVOKE",
                    target = SHADOW_RENDERER + "setupGlState(Lorg/joml/Matrix4f;)V",
                    shift = At.Shift.AFTER),
            require = 0)
    private void celtium$wpShadowTargetPrepTimedEnd(net.minecraft.client.renderer.EntityRenderer renderer,
            com.gtnewhorizons.angelica.compat.mojang.Camera camera, CallbackInfo info) {
        org.soutalab.celtium.vulkan.FramePassTimings.end("wpShadowTargetPrep");
    }
}
