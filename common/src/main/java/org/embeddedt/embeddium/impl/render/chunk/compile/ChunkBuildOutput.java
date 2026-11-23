package org.embeddedt.embeddium.impl.render.chunk.compile;

import it.unimi.dsi.fastutil.objects.Reference2ReferenceMap;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import org.embeddedt.embeddium.impl.common.util.NativeBuffer;
import org.embeddedt.embeddium.impl.render.chunk.RenderSection;
import org.embeddedt.embeddium.impl.render.chunk.data.BuiltRenderSectionData;
import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;
import org.embeddedt.embeddium.impl.render.chunk.data.BuiltSectionMeshParts;
import org.jetbrains.annotations.Nullable;

/**
 * The result of a chunk rebuild task which contains any and all data that needs to be processed or uploaded on
 * the main thread. If a task is cancelled after finishing its work and not before the result is processed, the result
 * will instead be discarded.
 */
public class ChunkBuildOutput extends ChunkTaskOutput {
    public final BuiltRenderSectionData info;
    public final Reference2ReferenceMap<TerrainRenderPass, BuiltSectionMeshParts> meshes;

    public ChunkBuildOutput(RenderSection render, BuiltRenderSectionData info, Reference2ReferenceMap<TerrainRenderPass, BuiltSectionMeshParts> meshes, int buildTime) {
        super(render, buildTime);
        this.info = info;
        this.meshes = meshes;

        if (this.info != null) {
            this.info.bake();
        }
    }

    @Override
    public void delete() {
        for (BuiltSectionMeshParts data : this.meshes.values()) {
            data.free();
        }
    }

    /**
     * {@return a copy whose vertex and index buffers are fresh allocations holding the same bytes}.
     * <p>
     * The section info, the sort states and the vertex ranges are shared with the original: {@link #delete()}
     * frees none of them, and {@code BuiltRenderSectionData.bake()} is a no-op, so rebuilding the output around
     * the copied buffers changes nothing else. A copy that fails halfway frees the buffers it already made -
     * nobody else owns them.
     */
    @Override
    public ChunkBuildOutput copyForDeferral() {
        Reference2ReferenceMap<TerrainRenderPass, BuiltSectionMeshParts> copied =
                new Reference2ReferenceOpenHashMap<>();
        try {
            for (Reference2ReferenceMap.Entry<TerrainRenderPass, BuiltSectionMeshParts> entry
                    : this.meshes.reference2ReferenceEntrySet()) {
                BuiltSectionMeshParts parts = entry.getValue();
                if (parts == null) {
                    copied.put(entry.getKey(), null);
                    continue;
                }
                copied.put(entry.getKey(), new BuiltSectionMeshParts(
                        NativeBuffer.copy(parts.vertexBuffer().getDirectBuffer()),
                        parts.indexBuffer() != null
                                ? NativeBuffer.copy(parts.indexBuffer().getDirectBuffer()) : null,
                        parts.sortState(), parts.ranges()));
            }
        } catch (RuntimeException error) {
            for (BuiltSectionMeshParts parts : copied.values()) {
                if (parts != null) {
                    parts.free();
                }
            }
            throw error;
        }
        return new ChunkBuildOutput(this.render, this.info, copied, this.buildTime);
    }
}
