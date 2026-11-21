package org.embeddedt.embeddium.impl.render.chunk;

import org.embeddedt.embeddium.impl.gl.device.CommandList;
import org.embeddedt.embeddium.impl.gl.device.MultiDrawBatch;
import org.embeddedt.embeddium.impl.gl.device.RenderDevice;
import org.embeddedt.embeddium.impl.gl.tessellation.GlPrimitiveType;
import org.embeddedt.embeddium.impl.gl.tessellation.GlTessellation;
import org.embeddedt.embeddium.impl.render.chunk.data.SectionRenderDataStorage;
import org.embeddedt.embeddium.impl.render.chunk.fog.FogService;
import org.embeddedt.embeddium.impl.render.chunk.lists.ChunkRenderListIterable;
import org.embeddedt.embeddium.impl.render.chunk.shader.ChunkShaderInterface;
import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;
import org.embeddedt.embeddium.impl.render.viewport.CameraTransform;

/**
 * Minimal binary-compatibility shell retained for shader-mod / Demonica Tier A bytecode references.
 * Celtium renders terrain exclusively through {@code VulkanTerrainGpuBackend} and does not instantiate
 * Celeritas's OpenGL chunk renderer or GL tessellation pipeline.
 */
public abstract class DefaultChunkRenderer extends ShaderChunkRenderer {

    public DefaultChunkRenderer(RenderDevice device, RenderPassConfiguration<?> renderPassConfiguration, FogService fogService) {
        super(device, renderPassConfiguration, fogService);
    }

    @Override
    public boolean useBlockFaceCulling() {
        return true;
    }

    protected Object getRenderView() {
        return SectionRenderDataStorage.BatchCacheParams.DEFAULT_VIEW;
    }

    protected SectionRenderDataStorage.BatchCacheParams batchCacheParams(boolean useBlockFaceCulling) {
        return new SectionRenderDataStorage.BatchCacheParams(useBlockFaceCulling, this.getRenderView());
    }

    protected abstract void configureShaderInterface(ChunkShaderInterface shader);

    @Override
    public void render(ChunkRenderMatrices matrices,
                       CommandList commandList,
                       ChunkRenderListIterable renderLists,
                       TerrainRenderPass renderPass,
                       CameraTransform occlusionCamera,
                       CameraTransform camera) {
        throw new UnsupportedOperationException("Celtium renders terrain exclusively through VulkanTerrainGpuBackend");
    }

    protected void executeBatch(CommandList commandList, MultiDrawBatch batch, GlTessellation tessellation, GlPrimitiveType primitiveType) {
        throw new UnsupportedOperationException("Celtium renders terrain exclusively through VulkanTerrainGpuBackend");
    }

    @Override
    public void delete(CommandList commandList) {
        super.delete(commandList);
    }
}
