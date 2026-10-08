package org.embeddedt.embeddium.impl.render.chunk.backend;

import it.unimi.dsi.fastutil.objects.Object2LongMap;
import org.embeddedt.embeddium.impl.render.chunk.ChunkRenderMatrices;
import org.embeddedt.embeddium.impl.render.chunk.RenderSectionManager;
import org.embeddedt.embeddium.impl.render.chunk.compile.ChunkTaskOutput;
import org.embeddedt.embeddium.impl.render.chunk.compile.executor.ChunkJobResult;
import org.embeddedt.embeddium.impl.render.chunk.lists.ChunkRenderListIterable;
import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;
import org.embeddedt.embeddium.impl.render.viewport.CameraTransform;

import java.util.Collection;

/** Terrain GPU operations; meshing, graph updates and sorting stay in the section manager. */
public interface TerrainGpuBackend {
    String getName();

    void render(ChunkRenderMatrices matrices, ChunkRenderListIterable renderLists, TerrainRenderPass pass,
                CameraTransform occlusionCamera, CameraTransform camera, boolean profile);

    /** Consume/copy native output data before the caller deletes those outputs. */
    void uploadMeshes(Collection<ChunkJobResult.Success<? extends ChunkTaskOutput>> results, Runnable graphUpdateTrigger);

    void updateRegions();

    void updateTimings();

    Object2LongMap<TerrainRenderPass> snapshotTimings();

    void collectMemoryStats(RenderSectionManager.DeviceMemoryStats destination);

    String getTransferQueueDebugString();

    void delete();
}
