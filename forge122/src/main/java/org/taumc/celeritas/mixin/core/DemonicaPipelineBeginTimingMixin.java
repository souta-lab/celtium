package org.taumc.celeritas.mixin.core;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Item 648: the pack frame-begin phase, second half - the deferred pipeline's level-rendering begin.
 * <p>
 * {@code beginLevelRendering} is where the pack pipeline opens the frame: render targets are prepared
 * and the stage state is reset before the pass' first vanilla bracket (the camera setup) opens.
 * Demonica's EntityRenderer handler calls it at the head of renderWorldPass, right after
 * {@code preparePipeline} (the {@code wpPackPrepare} bracket), so both halves of the frame-begin phase
 * used to sit inside worldRest unnamed. Like its sibling this span is inclusive and report-only, not
 * a WORLD_PASS_MEMBERS entry.
 * <p>
 * Only the deferred pipeline is bracketed: it is the pipeline a shader pack with a deferred stage
 * (the census protocol's packs included) runs. The fixed-function pipeline is the no-pack path, where
 * there is no pack frame-begin to attribute. The target is named by string and {@code @Pseudo} keeps
 * the mixin silently off when Demonica is absent.
 */
@Pseudo
@Mixin(targets = "net.coderbot.iris.pipeline.DeferredWorldRenderingPipeline")
public abstract class DemonicaPipelineBeginTimingMixin {

    @Inject(method = "beginLevelRendering", at = @At("HEAD"), require = 0)
    private void celtium$wpPackBeginTimedBegin(CallbackInfo info) {
        org.soutalab.celtium.vulkan.FramePassTimings.begin("wpPackBegin");
    }

    @Inject(method = "beginLevelRendering", at = @At("RETURN"), require = 0)
    private void celtium$wpPackBeginTimedEnd(CallbackInfo info) {
        org.soutalab.celtium.vulkan.FramePassTimings.end("wpPackBegin");
    }
}
