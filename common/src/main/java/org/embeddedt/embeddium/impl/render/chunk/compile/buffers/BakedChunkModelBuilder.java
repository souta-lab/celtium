package org.embeddedt.embeddium.impl.render.chunk.compile.buffers;

import lombok.Getter;
import org.embeddedt.embeddium.impl.model.quad.properties.ModelQuadFacing;
import org.embeddedt.embeddium.impl.render.chunk.data.BuiltRenderSectionData;
import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;
import org.embeddedt.embeddium.impl.render.chunk.vertex.builder.ChunkMeshBufferBuilder;
import org.embeddedt.embeddium.impl.render.chunk.vertex.format.ChunkVertexEncoder;

import java.util.Objects;

public class BakedChunkModelBuilder implements ChunkModelBuilder {
    private final ChunkMeshBufferBuilder[] vertexBuffers;
    private final boolean splitBySide;
    @Getter
    private final ChunkVertexEncoder encoder;

    private BuiltRenderSectionData renderData;

    public BakedChunkModelBuilder(ChunkVertexEncoder encoder, int stride, TerrainRenderPass pass) {
        var vertexBuffers = new ChunkMeshBufferBuilder[ModelQuadFacing.COUNT];

        for (int facing = 0; facing < ModelQuadFacing.COUNT; facing++) {
            vertexBuffers[facing] = new ChunkMeshBufferBuilder(encoder, stride, 64 * 1024, pass.isSorted() && facing == ModelQuadFacing.UNASSIGNED.ordinal());
        }

        this.encoder = encoder;
        this.vertexBuffers = vertexBuffers;
        this.splitBySide = !pass.isSorted();
    }

    @Override
    public ChunkMeshBufferBuilder getVertexBuffer(ModelQuadFacing facing) {
        Objects.requireNonNull(this.renderData, "Builder has not been started");
        return splitBySide ? this.vertexBuffers[facing.ordinal()] : this.vertexBuffers[ModelQuadFacing.UNASSIGNED.ordinal()];
    }

    @Override
    public BuiltRenderSectionData getSectionContextBundle() {
        return this.renderData;
    }

    public void destroy() {
        for (ChunkMeshBufferBuilder builder : this.vertexBuffers) {
            if(builder != null) {
                builder.destroy();
            }
        }
    }

    public void begin(BuiltRenderSectionData renderData, int sectionIndex) {
        this.renderData = renderData;

        for (var vertexBuffer : this.vertexBuffers) {
            if(vertexBuffer != null) {
                vertexBuffer.start(sectionIndex);
            }
        }
    }

    /**
     * Concatenates the per-facing correction-factor side streams in the given facing
     * order — the same order {@code ChunkBuildBuffers.createMesh} merges the vertex
     * buffers in, so entry {@code i} of the result belongs to quad {@code i} of the
     * merged buffer.
     *
     * @return the factors, or {@code null} when no facing captured a side stream
     */
    public int[] rdhFactors(ModelQuadFacing[] order) {
        int[][] perFacing = new int[order.length][];
        int total = 0;
        boolean any = false;
        for (int i = 0; i < order.length; i++) {
            perFacing[i] = this.vertexBuffers[order[i].ordinal()].rdhFactors();
            if (perFacing[i] != null) {
                any = true;
                total += perFacing[i].length;
            }
        }
        if (!any) {
            return null;
        }
        int[] merged = new int[total];
        int at = 0;
        for (int[] factors : perFacing) {
            if (factors != null) {
                System.arraycopy(factors, 0, merged, at, factors.length);
                at += factors.length;
            }
        }
        return merged;
    }

    public boolean isEmpty() {
        for (var vertexBuffer : this.vertexBuffers) {
            if (vertexBuffer != null && !vertexBuffer.isEmpty()) {
                return false;
            }
        }
        return true;
    }
}
