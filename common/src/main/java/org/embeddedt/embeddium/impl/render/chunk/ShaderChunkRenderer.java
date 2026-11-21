package org.embeddedt.embeddium.impl.render.chunk;

import org.embeddedt.embeddium.impl.gl.device.CommandList;
import org.embeddedt.embeddium.impl.gl.device.RenderDevice;
import org.embeddedt.embeddium.impl.gl.shader.GlProgram;
import org.embeddedt.embeddium.impl.render.chunk.fog.FogService;
import org.embeddedt.embeddium.impl.render.chunk.light.DynamicLightService;
import org.embeddedt.embeddium.impl.render.chunk.shader.ChunkShaderEnvironment;
import org.embeddedt.embeddium.impl.render.chunk.shader.ChunkShaderInterface;
import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;

/**
 * Minimal GL-free binary-compatibility shell retained for Demonica Tier A bytecode references.
 * Celtium renders terrain exclusively through {@code VulkanTerrainGpuBackend} and does not compile
 * Celeritas's OpenGL chunk shaders.
 */
public abstract class ShaderChunkRenderer implements ChunkRenderer {
    protected final RenderPassConfiguration<?> renderPassConfiguration;
    protected final RenderDevice device;
    protected GlProgram<ChunkShaderInterface> activeProgram;
    protected final ChunkShaderEnvironment environment;

    public ShaderChunkRenderer(RenderDevice device, RenderPassConfiguration<?> renderPassConfiguration, FogService fogService) {
        this.device = device;
        this.environment = new ChunkShaderEnvironment(fogService);
        this.renderPassConfiguration = renderPassConfiguration;
    }

    /** {@return the fog inputs this renderer uses}. */
    public FogService getFogService() {
        return this.environment.fogService();
    }

    /** {@return the shading-time dynamic light sources available to a terrain backend}. */
    public DynamicLightService getDynamicLightService() {
        return DynamicLightService.EMPTY;
    }

    protected void begin(TerrainRenderPass pass) {
        throw new UnsupportedOperationException("Celtium renders terrain exclusively through VulkanTerrainGpuBackend");
    }

    protected void end(TerrainRenderPass pass) {
        throw new UnsupportedOperationException("Celtium renders terrain exclusively through VulkanTerrainGpuBackend");
    }

    @Override
    public void delete(CommandList commandList) {
    }

    @Override
    public RenderPassConfiguration<?> getRenderPassConfiguration() {
        return this.renderPassConfiguration;
    }
}
