package org.soutalab.celtium.vulkan.graph;

import org.soutalab.celtium.vulkan.PipelinePrewarm;
import org.soutalab.celtium.vulkan.batch.ImmediateBatcher;
import org.soutalab.celtium.vulkan.bindless.BindlessTextureTable;

/**
 * The composition root of the new rendering path (design section 2.1: {@code FrameGraphCompiler
 * & RendererCore}): the compiled frame graph, the deferred immediate batcher, the bindless
 * texture table and the pipeline prewarm scheduler, held together so the backend's frame entry
 * has exactly one object to drive.
 *
 * <p>Integration contract for the backend (the wiring the migration's integration step performs):
 * at startup, build the {@link VanillaDirectGraph} for the window size, create the core around
 * it, register the standard pipelines with {@link #prewarm()} and warm them; per frame, feed
 * immediate draws into {@link #batcher()} against the HAL staging ring, compile the
 * {@link org.soutalab.celtium.vulkan.batch.BatchPlan}, and run
 * {@link GraphExecutor#execute} with the HAL's barrier sink and per-pass recorders that issue
 * the terrain indirect draws and the batch plan inside {@code MainForwardPass}. The swapchain
 * image the graph imports is bound by the HAL's present engine at acquire time. The composite
 * route this replaces is deleted in the same integration that gates this path, per the
 * migration's no-permanent-flag rule.
 */
public final class RendererCore {

    private final VanillaDirectGraph vanillaGraph;
    private final ImmediateBatcher batcher;
    private final BindlessTextureTable textureTable;
    private final PipelinePrewarm prewarm;

    public RendererCore(VanillaDirectGraph vanillaGraph, ImmediateBatcher batcher,
                        BindlessTextureTable textureTable, PipelinePrewarm prewarm) {
        this.vanillaGraph = vanillaGraph;
        this.batcher = batcher;
        this.textureTable = textureTable;
        this.prewarm = prewarm;
    }

    public VanillaDirectGraph vanillaGraph() {
        return this.vanillaGraph;
    }

    public CompiledGraph frameGraph() {
        return this.vanillaGraph.graph();
    }

    public ImmediateBatcher batcher() {
        return this.batcher;
    }

    public BindlessTextureTable textureTable() {
        return this.textureTable;
    }

    public PipelinePrewarm prewarm() {
        return this.prewarm;
    }
}
