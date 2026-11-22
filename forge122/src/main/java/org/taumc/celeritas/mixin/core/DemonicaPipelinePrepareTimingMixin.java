package org.taumc.celeritas.mixin.core;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Item 648: the pack frame-begin phase, first half - Iris's pipeline preparation, timed where it runs.
 * <p>
 * In a Demonica frame, Demonica's own EntityRenderer handler runs at the head of renderWorldPass and
 * calls {@code PipelineManager.preparePipeline} before {@code beginLevelRendering}; both calls sit
 * inside the world pass but outside every vanilla call bracket, so their cost used to land in
 * FrameBudgetTimings' worldRest with no name. This bracket is an inclusive, report-only span (it is
 * deliberately not a WORLD_PASS_MEMBERS entry): the census reads it off the per-frame profiles.
 * <p>
 * The target is a Demonica class, named by string so this mixin carries no compile-time dependency on
 * Demonica, and {@code @Pseudo} keeps it silently off in a run without Demonica. The return type is
 * read as {@code Object}: the pipeline type is Demonica's own and equally absent from the classpath.
 */
@Pseudo
@Mixin(targets = "net.coderbot.iris.pipeline.PipelineManager")
public abstract class DemonicaPipelinePrepareTimingMixin {

    @Inject(method = "preparePipeline", at = @At("HEAD"), require = 0)
    private void celtium$wpPackPrepareTimedBegin(String dimensionName,
            CallbackInfoReturnable<Object> info) {
        org.soutalab.celtium.vulkan.FramePassTimings.begin("wpPackPrepare");
    }

    @Inject(method = "preparePipeline", at = @At("RETURN"), require = 0)
    private void celtium$wpPackPrepareTimedEnd(String dimensionName,
            CallbackInfoReturnable<Object> info) {
        org.soutalab.celtium.vulkan.FramePassTimings.end("wpPackPrepare");
    }
}
