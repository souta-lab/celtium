package org.taumc.celeritas.mixin.core;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Item 648: the shadow phase whole, timed at Iris's own entry - {@code ShadowRenderer.renderShadows}.
 * <p>
 * pack-tail-2 split the shadow stage's terrain record out of terrainRecord (packRecordShadow) and its
 * section 5.2 named the likely owner of the remaining worldRest residual as the shadow pass' setup
 * phase: the frustum construction, camera/matrices preparation and target setup that run inside
 * renderShadows before - and around - the record itself. This bracket holds the whole method,
 * record included, as an inclusive report-only span: the setup share is the span minus the contained
 * names (packRecordShadow and the terrain span), netted out offline from the per-frame profiles,
 * the same reconstruction pack-tail-2 used for its terrainRecord split. It is deliberately not a
 * WORLD_PASS_MEMBERS entry, because subtracting a container would subtract its contents twice.
 * <p>
 * The target is a Demonica class, named by string so this mixin carries no compile-time dependency
 * on Demonica, and {@code @Pseudo} keeps it silently off in a run without Demonica. The second
 * parameter is Angelica's Camera type, which is not on this project's classpath either; the handler
 * reads it as {@code Object}, a plain reference widening at the bytecode level.
 */
@Pseudo
@Mixin(targets = "net.coderbot.iris.pipeline.ShadowRenderer")
public abstract class DemonicaShadowPhaseTimingMixin {

    @Inject(method = "renderShadows", at = @At("HEAD"), require = 0)
    private void celtium$wpShadowPhaseTimedBegin(net.minecraft.client.renderer.EntityRenderer renderer,
            Object camera, CallbackInfo info) {
        org.soutalab.celtium.vulkan.FramePassTimings.begin("wpShadowPhase");
    }

    @Inject(method = "renderShadows", at = @At("RETURN"), require = 0)
    private void celtium$wpShadowPhaseTimedEnd(net.minecraft.client.renderer.EntityRenderer renderer,
            Object camera, CallbackInfo info) {
        org.soutalab.celtium.vulkan.FramePassTimings.end("wpShadowPhase");
    }
}
