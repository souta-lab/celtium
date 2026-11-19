package org.embeddedt.embeddium.impl.render.chunk;

import org.embeddedt.embeddium.impl.common.util.MathUtil;
import org.embeddedt.embeddium.impl.render.chunk.compile.sorting.ChunkPrimitiveType;
import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;
import org.embeddedt.embeddium.impl.render.chunk.terrain.material.Material;
import org.embeddedt.embeddium.impl.render.chunk.vertex.format.ChunkVertexType;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Stream;

public record RenderPassConfiguration<R>(Map<R, Material> chunkRenderTypeToMaterialMap,
                                      Map<R, Collection<TerrainRenderPass>> vanillaRenderStages,
                                      Material defaultSolidMaterial,
                                      Material defaultCutoutMippedMaterial,
                                      Material defaultTranslucentMaterial) {
    @Deprecated
    public ChunkVertexType getVertexTypeForPass(TerrainRenderPass pass) {
        return pass.vertexType();
    }

    @Deprecated
    public ChunkPrimitiveType getPrimitiveTypeForPass(TerrainRenderPass pass) {
        return pass.primitiveType();
    }

    public Material getMaterialForRenderType(Object type) {
        Objects.requireNonNull(type, "Null render type provided");
        var material = chunkRenderTypeToMaterialMap.get(type);
        if (material == null) {
            throw new IllegalArgumentException(type.toString());
        }
        return material;
    }

    public Stream<TerrainRenderPass> getAllKnownRenderPasses() {
        return vanillaRenderStages().values().stream().flatMap(Collection::stream).distinct();
    }

    /**
     * Picks the element size, in bytes, that every geometry arena for this configuration allocates in. The size
     * holds a whole number of primitives for every pass, so each allocation starts on a primitive boundary. The
     * terrain shaders rely on this: they find a vertex's corner within its quad from {@code gl_VertexIndex & 3},
     * which is only the corner if the allocation's base vertex is itself a whole number of quads in.
     * <p>
     * Taking the least common multiple of the strides alone does not give this. With the compact (24-byte) and
     * vanilla-like (32-byte) formats the lcm of the strides is 96, which holds four compact vertices but only
     * three vanilla-like ones, so an allocation in the vanilla-like format could start mid-quad and every corner
     * derived from it would be wrong.
     */
    public int commonVertexStride() {
        return primitiveAlignedStride(getAllKnownRenderPasses()
                .mapToInt(pass -> pass.vertexType().getVertexFormat().getStride()
                        * pass.primitiveType().getVerticesPerPrimitive())
                .toArray());
    }

    /**
     * The least common multiple of the given per-pass primitive sizes (stride &times; vertices per primitive).
     * The result is a multiple of every input, so an arena element of this size holds a whole number of
     * primitives of every pass it was computed from. With no passes the size is 1, matching the placeholder
     * stride used before a world exists.
     */
    static int primitiveAlignedStride(int... primitiveSizes) {
        return Arrays.stream(primitiveSizes).reduce(1, MathUtil::lcm);
    }
}
