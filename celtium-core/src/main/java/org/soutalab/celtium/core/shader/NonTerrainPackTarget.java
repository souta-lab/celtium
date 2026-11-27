package org.soutalab.celtium.core.shader;

import java.lang.foreign.MemorySegment;

import org.soutalab.celtium.core.frame.ActiveRenderTargets;

/**
 * Interface 2's connection point into the non-terrain lane (design V040-FINAL-PLAN section 2.3):
 * while a pack stage is active, the lane's batcher, model-replay engine and particle instancer
 * draw through the currently active target - the stage's pipelines, the pack uniform buffer and
 * the stage's render targets - and issue their draws directly, never through a GL-shaped layer.
 * With no pack active there is no target, and the lane draws through its fixed native pipelines.
 * <p>
 * Pipeline handles are opaque {@code VkPipeline} addresses owned by the HAL; the lane asks for the
 * handle of the program it is about to draw (a {@code gbuffers_*} or {@code shadow_*} program name)
 * and binds it. A name the stage does not define yields {@link #NO_PIPELINE}, and the draw is
 * skipped - a pack that omits a program is not an error.
 */
public interface NonTerrainPackTarget {

    /** {@link #pipelineHandle(String)} result for a program the active stage does not define. */
    long NO_PIPELINE = 0L;

    /** The stage's name (e.g. {@code gbuffers_entities}, {@code shadow}), for diagnostics. */
    String passName();

    /** The attachments the stage renders into. */
    ActiveRenderTargets renderTargets();

    /** The pack uniform block for this frame (the segment the program set filled). */
    MemorySegment uniformBuffer();

    /** The pipeline for a non-terrain program of this stage, or {@link #NO_PIPELINE}. */
    long pipelineHandle(String programName);
}
