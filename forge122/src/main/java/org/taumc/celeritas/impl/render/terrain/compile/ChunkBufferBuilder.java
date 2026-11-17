package org.taumc.celeritas.impl.render.terrain.compile;

import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.util.BlockRenderLayer;

/**
 * The buffer handed to vanilla's block rendering during a chunk build: a plain
 * {@link BufferBuilder} that also knows which build and which layer it belongs to.
 *
 * <p>Part of celtium's Demonica compatibility surface (design: COMPAT-SURFACE-DESIGN-2026-11-17.md,
 * Tier B). Demonica's patch S13 wraps the {@code BlockModelRenderer.renderModel} call inside
 * {@code BlockRendererDispatcher.renderBlock} and decides per block whether the fast renderer
 * meshes it; the decision starts with {@code buffer instanceof ChunkBufferBuilder} and then
 * reads {@link #getContext()} and {@link #getLayer()} from the buffer. Without this class the
 * {@code instanceof} itself throws {@code NoClassDefFoundError} on every block the meshing
 * task routes through the vanilla dispatcher, whether or not the fast renderer is enabled.
 *
 * <p>The implementation is celtium's own: the buffers are consumed exactly like any other
 * vanilla buffer, by {@link VintageChunkBuildContext#convertVanillaDataToCeleritasData}. The
 * only difference from a plain {@code BufferBuilder} is the two references carried here.
 */
public class ChunkBufferBuilder extends BufferBuilder {
    private final VintageChunkBuildContext context;
    private final BlockRenderLayer layer;

    public ChunkBufferBuilder(int bufferSize, VintageChunkBuildContext context, BlockRenderLayer layer) {
        super(bufferSize);
        this.context = context;
        this.layer = layer;
    }

    public VintageChunkBuildContext getContext() {
        return this.context;
    }

    public BlockRenderLayer getLayer() {
        return this.layer;
    }
}
