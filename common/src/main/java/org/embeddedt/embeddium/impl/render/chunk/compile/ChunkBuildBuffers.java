package org.embeddedt.embeddium.impl.render.chunk.compile;

import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import lombok.Getter;
import org.embeddedt.embeddium.impl.gl.util.VertexRange;
import org.embeddedt.embeddium.impl.model.quad.properties.ModelQuadFacing;
import org.embeddedt.embeddium.impl.render.chunk.RenderPassConfiguration;
import org.embeddedt.embeddium.impl.render.chunk.RenderSection;
import org.embeddedt.embeddium.impl.render.chunk.compile.buffers.BakedChunkModelBuilder;
import org.embeddedt.embeddium.impl.render.chunk.compile.buffers.ChunkModelBuilder;
import org.embeddedt.embeddium.impl.render.chunk.data.BuiltRenderSectionData;
import org.embeddedt.embeddium.impl.render.chunk.data.BuiltSectionMeshParts;
import org.embeddedt.embeddium.impl.render.chunk.data.YLevelShelves;
import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;
import org.embeddedt.embeddium.impl.render.chunk.terrain.material.Material;
import org.embeddedt.embeddium.impl.common.util.NativeBuffer;
import org.embeddedt.embeddium.impl.render.chunk.sorting.SortState;
import org.embeddedt.embeddium.impl.render.chunk.vertex.format.ChunkVertexEncoder;

import static org.taumc.celeritas.lwjgl.LWJGLServiceProvider.LWJGL;

import java.nio.ByteBuffer;
import java.util.*;

/**
 * A collection of temporary buffers for each worker thread which will be used to build chunk meshes for given render
 * passes. This makes a best-effort attempt to pick a suitable size for each scratch buffer, but will never try to
 * shrink a buffer.
 */
public final class ChunkBuildBuffers {
    private static final ModelQuadFacing[] ONLY_UNASSIGNED = new ModelQuadFacing[] { ModelQuadFacing.UNASSIGNED };
    private final Reference2ReferenceOpenHashMap<TerrainRenderPass, BakedChunkModelBuilder> builders = new Reference2ReferenceOpenHashMap<>();

    @Getter
    private final RenderPassConfiguration<?> renderPassConfiguration;

    private BuiltRenderSectionData renderData;
    private int sectionIndex;

    public ChunkBuildBuffers(RenderPassConfiguration<?> configuration) {
        this.renderPassConfiguration = configuration;
    }

    public void init(BuiltRenderSectionData renderData, int sectionIndex) {
        this.renderData = renderData;
        this.sectionIndex = sectionIndex;
        for (var builder : this.builders.values()) {
            builder.begin(renderData, sectionIndex);
        }
    }

    public BuiltRenderSectionData getSectionContextBundle() {
        return this.renderData;
    }

    public ChunkModelBuilder get(Material material) {
        return this.get(material.pass);
    }

    private ChunkModelBuilder createBuilder(TerrainRenderPass pass) {
        var vertexType = this.renderPassConfiguration.getVertexTypeForPass(pass);
        var builder = new BakedChunkModelBuilder(vertexType.createEncoder(), vertexType.getVertexFormat().getStride(), pass);
        Objects.requireNonNull(renderData, "Buffers have not been started");
        builder.begin(renderData, sectionIndex);
        this.builders.put(pass, builder);
        return builder;
    }

    public ChunkModelBuilder get(TerrainRenderPass pass) {
        var builder = this.builders.get(pass);
        return builder != null ? builder : createBuilder(pass);
    }

    public Set<TerrainRenderPass> getBuilderPasses() {
        return this.builders.keySet();
    }

    /**
     * Creates immutable baked chunk meshes from all non-empty scratch buffers. This is used after all blocks
     * have been rendered to pass the finished meshes over to the graphics card. This function can be called multiple
     * times to return multiple copies.
     */
    public BuiltSectionMeshParts createMesh(TerrainRenderPass pass, float camX, float camY, float camZ) {
        var builder = this.builders.get(pass);

        if (builder == null || builder.isEmpty()) {
            return null;
        }

        List<ByteBuffer> vertexBuffers = new ArrayList<>();
        List<ByteBuffer> permutedBuffers = new ArrayList<>();
        var vertexRanges = new EnumMap<ModelQuadFacing, VertexRange>(ModelQuadFacing.class);
        Map<ModelQuadFacing, int[]> shelfCumulative = null;

        int vertexCount = 0;

        var vertexType = renderPassConfiguration.getVertexTypeForPass(pass);
        int vertexStride = vertexType.getVertexFormat().getStride();

        ModelQuadFacing[] facingsToUpload = pass.isSorted() ? ONLY_UNASSIGNED : ModelQuadFacing.VALUES;
        SortState sortState = pass.isSorted() ? builder.getVertexBuffer(ModelQuadFacing.UNASSIGNED).getSortState() : null;

        for (ModelQuadFacing facing : facingsToUpload) {
            var buffer = builder.getVertexBuffer(facing);

            if (buffer.isEmpty()) {
                continue;
            }

            ByteBuffer uploadBuffer = buffer.slice();

            // ±Y level shelves (item 671): reorder the Y buckets into shelf order and keep their cumulative
            // tables for the section row. The levels were recorded at push time, so this does not depend on
            // the vertex format. A bucket whose pushes were not all quads (level count disagreeing with the
            // vertex count) is left unsorted with no table, which the assembler reads as "never trim".
            if (!pass.isSorted() && YLevelShelves.ENABLED
                    && (facing == ModelQuadFacing.POS_Y || facing == ModelQuadFacing.NEG_Y)) {
                int[] levels = buffer.getQuadLevels();

                if (levels != null && levels.length * YLevelShelves.VERTICES_PER_QUAD == buffer.count()) {
                    int[] order = YLevelShelves.sortOrder(levels, facing == ModelQuadFacing.POS_Y);

                    if (!isIdentity(order)) {
                        uploadBuffer = permuteQuads(uploadBuffer, order, vertexStride);
                        permutedBuffers.add(uploadBuffer);
                    }

                    if (shelfCumulative == null) {
                        shelfCumulative = new EnumMap<>(ModelQuadFacing.class);
                    }
                    shelfCumulative.put(facing, YLevelShelves.cumulative(levels));
                }
            }

            vertexBuffers.add(uploadBuffer);
            vertexRanges.put(facing, new VertexRange(vertexCount, buffer.count()));

            vertexCount += buffer.count();
        }

        if (vertexCount == 0) {
            return null;
        }

        var primitiveType = renderPassConfiguration.getPrimitiveTypeForPass(pass);

        var mergedBuffer = new NativeBuffer(vertexCount * vertexType.getVertexFormat().getStride());
        var mergedBufferBuilder = mergedBuffer.getDirectBuffer();

        for (var buffer : vertexBuffers) {
            mergedBufferBuilder.put(buffer);
        }

        for (var permuted : permutedBuffers) {
            LWJGL.memFree(permuted);
        }

        mergedBufferBuilder.flip();

        NativeBuffer mergedIndexBuffer;

        if (pass.isSorted()) {
            int numPrimitives = vertexCount / primitiveType.getVerticesPerPrimitive();
            // Generate the canonical index buffer
            mergedIndexBuffer = new NativeBuffer(primitiveType.getIndexBufferSize(numPrimitives));

            // Do the initial sort now
            primitiveType.generateSortedIndexBuffer(mergedIndexBuffer.getDirectBuffer(), numPrimitives, sortState, camX, camY, camZ);
        } else {
            mergedIndexBuffer = null;
        }

        // Uncompacted: the consumer needs the sorting level before it throws away the data behind it.
        return new BuiltSectionMeshParts(mergedBuffer, mergedIndexBuffer, sortState, vertexRanges, shelfCumulative);
    }

    private static boolean isIdentity(int[] order) {
        for (int i = 0; i < order.length; i++) {
            if (order[i] != i) {
                return false;
            }
        }
        return true;
    }

    /**
     * Copies the quads of {@code source} into a new buffer in the order given by {@code order}
     * ({@code order[i]} is the source quad placed at position {@code i}). The caller owns the returned
     * buffer and must free it with {@code LWJGL.memFree} once its contents are consumed.
     */
    private static ByteBuffer permuteQuads(ByteBuffer source, int[] order, int vertexStride) {
        int quadBytes = vertexStride * YLevelShelves.VERTICES_PER_QUAD;
        ByteBuffer target = LWJGL.memAlloc(source.remaining());

        long src = LWJGL.memAddress(source);
        long dst = LWJGL.memAddress(target);

        for (int i = 0; i < order.length; i++) {
            LWJGL.memCopy(src + ((long) order[i] * quadBytes), dst + ((long) i * quadBytes), quadBytes);
        }

        return target;
    }

    public void destroy() {
        this.sectionIndex = 0;
        this.renderData = null;
        for (var builder : this.builders.values()) {
            builder.destroy();
        }
    }
}
