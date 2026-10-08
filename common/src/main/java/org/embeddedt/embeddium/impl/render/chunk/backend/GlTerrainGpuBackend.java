package org.embeddedt.embeddium.impl.render.chunk.backend;

import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.embeddedt.embeddium.impl.gl.device.CommandList;
import org.embeddedt.embeddium.impl.gl.device.RenderDevice;
import org.embeddedt.embeddium.impl.gl.profiling.TimerQueryManager;
import org.embeddedt.embeddium.impl.render.chunk.ChunkRenderMatrices;
import org.embeddedt.embeddium.impl.render.chunk.ChunkRenderer;
import org.embeddedt.embeddium.impl.render.chunk.RenderSectionManager;
import org.embeddedt.embeddium.impl.render.chunk.compile.ChunkTaskOutput;
import org.embeddedt.embeddium.impl.render.chunk.compile.executor.ChunkJobResult;
import org.embeddedt.embeddium.impl.render.chunk.lists.ChunkRenderListIterable;
import org.embeddedt.embeddium.impl.render.chunk.region.RenderRegionManager;
import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;
import org.embeddedt.embeddium.impl.render.viewport.CameraTransform;

import java.util.Collection;

/** Delegates to the original GL renderer/arena without changing their algorithms or hooks. */
public final class GlTerrainGpuBackend implements TerrainGpuBackend {
    private final RenderDevice device;
    private final ChunkRenderer renderer;
    private final RenderRegionManager regions;
    private final Object2ObjectOpenHashMap<TerrainRenderPass, TimerQueryManager> drawTimers = new Object2ObjectOpenHashMap<>();

    public GlTerrainGpuBackend(RenderDevice device, ChunkRenderer renderer, RenderRegionManager regions) {
        this.device = device;
        this.renderer = renderer;
        this.regions = regions;
    }

    @Override public String getName() { return "opengl"; }

    @Override public void render(ChunkRenderMatrices matrices, ChunkRenderListIterable renderLists, TerrainRenderPass pass,
                                 CameraTransform occlusionCamera, CameraTransform camera, boolean profile) {
        CommandList commands = device.createCommandList();
        TimerQueryManager timer = null;
        if (profile) {
            timer = drawTimers.computeIfAbsent(pass, $ -> new TimerQueryManager());
            timer.startProfiling();
        }
        try {
            renderer.render(matrices, commands, renderLists, pass, occlusionCamera, camera);
        } finally {
            if (profile) timer.finishProfiling();
        }
        commands.flush();
    }

    @Override public void uploadMeshes(Collection<ChunkJobResult.Success<? extends ChunkTaskOutput>> results, Runnable graphUpdateTrigger) {
        regions.uploadMeshes(device.createCommandList(), results, graphUpdateTrigger);
    }

    @Override public void updateRegions() { regions.update(); }

    @Override public void updateTimings() { drawTimers.values().forEach(TimerQueryManager::updateTime); }

    @Override public Object2LongMap<TerrainRenderPass> snapshotTimings() {
        Object2LongOpenHashMap<TerrainRenderPass> snapshot = new Object2LongOpenHashMap<>();
        for (var entry : drawTimers.entrySet()) snapshot.put(entry.getKey(), entry.getValue().getLastTime());
        return snapshot;
    }

    @Override public void collectMemoryStats(RenderSectionManager.DeviceMemoryStats destination) {
        for (var region : regions.getLoadedRegions()) {
            var resources = region.getResources();
            if (resources == null) continue;
            var geometry = resources.getGeometryArena();
            destination.deviceUsed += geometry.getDeviceUsedMemoryL();
            destination.deviceAllocated += geometry.getDeviceAllocatedMemoryL();
            var indices = resources.getIndexArena();
            if (indices != null) {
                destination.indexUsed += indices.getDeviceUsedMemoryL();
                destination.indexAllocated += indices.getDeviceAllocatedMemoryL();
            }
            destination.bufferCount++;
        }
    }

    @Override public String getTransferQueueDebugString() { return regions.getStagingBuffer().toString(); }

    @Override public void delete() {
        try (CommandList commands = device.createCommandList()) {
            regions.delete(commands);
            renderer.delete(commands);
        }
        drawTimers.values().forEach(TimerQueryManager::close);
        drawTimers.clear();
    }
}
