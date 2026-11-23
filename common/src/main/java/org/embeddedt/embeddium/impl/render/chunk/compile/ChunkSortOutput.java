package org.embeddedt.embeddium.impl.render.chunk.compile;

import it.unimi.dsi.fastutil.objects.Reference2ReferenceMap;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import org.embeddedt.embeddium.impl.common.util.NativeBuffer;
import org.embeddedt.embeddium.impl.render.chunk.RenderSection;
import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;

public class ChunkSortOutput extends ChunkTaskOutput {
    public record SortedMesh(NativeBuffer indexData) {}

    public final Reference2ReferenceMap<TerrainRenderPass, SortedMesh> meshes;

    public ChunkSortOutput(RenderSection render, int buildTime, Reference2ReferenceMap<TerrainRenderPass, SortedMesh> meshes) {
        super(render, buildTime);
        this.meshes = meshes;
    }

    @Override
    public void delete() {
        for (SortedMesh data : this.meshes.values()) {
            data.indexData().free();
        }
    }

    /**
     * {@return a copy whose index buffers are fresh allocations holding the same bytes}. A copy that fails
     * halfway frees the buffers it already made - nobody else owns them.
     */
    @Override
    public ChunkSortOutput copyForDeferral() {
        Reference2ReferenceMap<TerrainRenderPass, SortedMesh> copied = new Reference2ReferenceOpenHashMap<>();
        try {
            for (Reference2ReferenceMap.Entry<TerrainRenderPass, SortedMesh> entry
                    : this.meshes.reference2ReferenceEntrySet()) {
                SortedMesh mesh = entry.getValue();
                copied.put(entry.getKey(), mesh == null ? null
                        : new SortedMesh(NativeBuffer.copy(mesh.indexData().getDirectBuffer())));
            }
        } catch (RuntimeException error) {
            for (SortedMesh mesh : copied.values()) {
                if (mesh != null) {
                    mesh.indexData().free();
                }
            }
            throw error;
        }
        return new ChunkSortOutput(this.render, this.buildTime, copied);
    }
}
