package org.taumc.celeritas.mixin.core;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Item 690: the inside of the pack frame-begin phase, split at the seams the item 648 census
 * fixed (worldrest-census-648.md section 7). The 648 census attributed 319.5 ms of the
 * 1890.1 ms slowest-16 worldRest total to the frame-begin phase, and its bytecode read showed
 * the whole of it is the deferred pipeline's {@code beginLevelRendering}: the sibling
 * wpPackPrepare bracket (PipelineManager.preparePipeline) measured ~0 because that call is a
 * cached lookup, so only beginLevelRendering is split here.
 * <p>
 * {@code wpBeginUniforms} sums the four uniform-update calls at the head of the method -
 * IrisTimeUniforms.updateTime, WorldTimeUniforms.snapshot, FrameUpdateNotifier.onNewFrame and
 * CustomUniforms.update - as sequential spans under one name. {@code wpBeginTargets} is
 * prepareRenderTargets plus the custom-image clear loop (one GlImage.clear call per image,
 * again sequential spans under one name). The spans under a name are sequential, never
 * nested, so the outermost-only accumulation stays exact. What remains unnamed is the
 * section 7 "other" bucket by construction: wpPackBegin minus these two children reads as
 * the begin composite (CompositeRenderer.renderAll), the phase set and the horizon work.
 * <p>
 * The seams are the call sites inside beginLevelRendering, read off Demonica 0.9.1's
 * bytecode (item690-instrument.md section 1); each target invoke occurs exactly once in the
 * method (the clear loop is one invoke site executed per image). The handlers take only a
 * CallbackInfo - the target method has no parameters - so no Demonica type is declared.
 * Both brackets are inclusive INSIDE names: reported, never subtracted, like their parent
 * wpPackBegin. Only the deferred pipeline is bracketed, for the same reason as the parent:
 * it is the pipeline a shader pack with a deferred stage runs.
 */
@Pseudo
@Mixin(targets = "net.coderbot.iris.pipeline.DeferredWorldRenderingPipeline")
public abstract class DemonicaPipelineBeginChildrenTimingMixin {

    @Inject(method = "beginLevelRendering",
            at = @At(value = "INVOKE", target = "Lnet/coderbot/iris/uniforms/IrisTimeUniforms;updateTime()V"),
            require = 0)
    private void celtium$wpBeginUniformsTimeBegin(CallbackInfo info) {
        org.soutalab.celtium.vulkan.FramePassTimings.begin("wpBeginUniforms");
    }

    @Inject(method = "beginLevelRendering",
            at = @At(value = "INVOKE", target = "Lnet/coderbot/iris/uniforms/IrisTimeUniforms;updateTime()V",
                    shift = At.Shift.AFTER),
            require = 0)
    private void celtium$wpBeginUniformsTimeEnd(CallbackInfo info) {
        org.soutalab.celtium.vulkan.FramePassTimings.end("wpBeginUniforms");
    }

    @Inject(method = "beginLevelRendering",
            at = @At(value = "INVOKE", target = "Lnet/coderbot/iris/uniforms/WorldTimeUniforms;snapshot()V"),
            require = 0)
    private void celtium$wpBeginUniformsSnapshotBegin(CallbackInfo info) {
        org.soutalab.celtium.vulkan.FramePassTimings.begin("wpBeginUniforms");
    }

    @Inject(method = "beginLevelRendering",
            at = @At(value = "INVOKE", target = "Lnet/coderbot/iris/uniforms/WorldTimeUniforms;snapshot()V",
                    shift = At.Shift.AFTER),
            require = 0)
    private void celtium$wpBeginUniformsSnapshotEnd(CallbackInfo info) {
        org.soutalab.celtium.vulkan.FramePassTimings.end("wpBeginUniforms");
    }

    @Inject(method = "beginLevelRendering",
            at = @At(value = "INVOKE", target = "Lnet/coderbot/iris/uniforms/FrameUpdateNotifier;onNewFrame()V"),
            require = 0)
    private void celtium$wpBeginUniformsNotifyBegin(CallbackInfo info) {
        org.soutalab.celtium.vulkan.FramePassTimings.begin("wpBeginUniforms");
    }

    @Inject(method = "beginLevelRendering",
            at = @At(value = "INVOKE", target = "Lnet/coderbot/iris/uniforms/FrameUpdateNotifier;onNewFrame()V",
                    shift = At.Shift.AFTER),
            require = 0)
    private void celtium$wpBeginUniformsNotifyEnd(CallbackInfo info) {
        org.soutalab.celtium.vulkan.FramePassTimings.end("wpBeginUniforms");
    }

    @Inject(method = "beginLevelRendering",
            at = @At(value = "INVOKE", target = "Lnet/coderbot/iris/uniforms/custom/CustomUniforms;update()V"),
            require = 0)
    private void celtium$wpBeginUniformsCustomBegin(CallbackInfo info) {
        org.soutalab.celtium.vulkan.FramePassTimings.begin("wpBeginUniforms");
    }

    @Inject(method = "beginLevelRendering",
            at = @At(value = "INVOKE", target = "Lnet/coderbot/iris/uniforms/custom/CustomUniforms;update()V",
                    shift = At.Shift.AFTER),
            require = 0)
    private void celtium$wpBeginUniformsCustomEnd(CallbackInfo info) {
        org.soutalab.celtium.vulkan.FramePassTimings.end("wpBeginUniforms");
    }

    @Inject(method = "beginLevelRendering",
            at = @At(value = "INVOKE",
                    target = "Lnet/coderbot/iris/pipeline/DeferredWorldRenderingPipeline;prepareRenderTargets()V"),
            require = 0)
    private void celtium$wpBeginTargetsTimedBegin(CallbackInfo info) {
        org.soutalab.celtium.vulkan.FramePassTimings.begin("wpBeginTargets");
    }

    @Inject(method = "beginLevelRendering",
            at = @At(value = "INVOKE",
                    target = "Lnet/coderbot/iris/pipeline/DeferredWorldRenderingPipeline;prepareRenderTargets()V",
                    shift = At.Shift.AFTER),
            require = 0)
    private void celtium$wpBeginTargetsTimedEnd(CallbackInfo info) {
        org.soutalab.celtium.vulkan.FramePassTimings.end("wpBeginTargets");
    }

    @Inject(method = "beginLevelRendering",
            at = @At(value = "INVOKE", target = "Lnet/coderbot/iris/gl/image/GlImage;clear()V"),
            require = 0)
    private void celtium$wpBeginTargetsClearBegin(CallbackInfo info) {
        org.soutalab.celtium.vulkan.FramePassTimings.begin("wpBeginTargets");
    }

    @Inject(method = "beginLevelRendering",
            at = @At(value = "INVOKE", target = "Lnet/coderbot/iris/gl/image/GlImage;clear()V",
                    shift = At.Shift.AFTER),
            require = 0)
    private void celtium$wpBeginTargetsClearEnd(CallbackInfo info) {
        org.soutalab.celtium.vulkan.FramePassTimings.end("wpBeginTargets");
    }
}
