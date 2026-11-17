package org.embeddedt.embeddium.impl.render.chunk.shader;

import org.embeddedt.embeddium.impl.gl.shader.GlProgram;
import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;
import org.embeddedt.embeddium.impl.render.chunk.vertex.format.ChunkVertexType;
import org.jetbrains.annotations.Nullable;

/**
 * Core-owned seam for querying a replacement terrain program, extended vertex format and face culling policy for a
 * {@link TerrainRenderPass}.
 * <p>
 * Phase 0 defines the type and query seam only; when no provider is attached, terrain rendering keeps the exact default
 * behavior.
 */
public interface TerrainProgramProvider {

    /** Face culling policy requested by a replacement terrain program. */
    enum FaceCullingPolicy {
        DEFAULT,
        FORCE_DISABLE,
        FORCE_ENABLE
    }

    /** Replacement specification returned for a single {@link TerrainRenderPass}. */
    record PassOverride(
            @Nullable GlProgram<ChunkShaderInterface> program,
            @Nullable ChunkVertexType vertexType,
            FaceCullingPolicy faceCullingPolicy) {
        public PassOverride {
            if (faceCullingPolicy == null) {
                faceCullingPolicy = FaceCullingPolicy.DEFAULT;
            }
        }
    }

    /**
     * Queries whether {@code pass} should use a replacement program, vertex format or face culling policy.
     *
     * @return the override descriptor, or {@code null} to keep the default pass behavior
     */
    @Nullable PassOverride resolvePass(TerrainRenderPass pass);
}
