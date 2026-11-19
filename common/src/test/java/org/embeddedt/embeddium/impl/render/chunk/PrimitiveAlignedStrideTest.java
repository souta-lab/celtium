package org.embeddedt.embeddium.impl.render.chunk;

import org.embeddedt.embeddium.impl.common.util.MathUtil;
import org.embeddedt.embeddium.impl.render.chunk.compile.sorting.QuadPrimitiveType;
import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;
import org.embeddedt.embeddium.impl.render.chunk.vertex.format.ChunkMeshFormats;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The geometry arena's element size (the "common vertex stride") as a contract: an arena element must hold a
 * whole number of primitives for every render pass, because the terrain shaders derive a vertex's corner from
 * {@code gl_VertexIndex & 3} and that is only the corner when the allocation's base vertex starts on a quad
 * boundary. These tests pin the pure computation behind {@link RenderPassConfiguration#commonVertexStride()};
 * they are not a picture acceptance — whether the defect was visible in presented images is a leg question.
 */
class PrimitiveAlignedStrideTest {

    /** The pre-fix formula: the lcm of the strides alone. Kept here so the defect case stays readable. */
    private static int strideOnlyLcm(int... strides) {
        int result = 1;
        for (int stride : strides) {
            result = MathUtil.lcm(result, stride);
        }
        return result;
    }

    private static void assertWholePrimitives(int elementSize, int stride, int verticesPerPrimitive) {
        assertEquals(0, elementSize % stride, "element size must be a multiple of the stride");
        assertEquals(0, (elementSize / stride) % verticesPerPrimitive,
                "element must hold a whole number of primitives");
    }

    @Test
    void noPassesKeepsThePlaceholderSize() {
        assertEquals(1, RenderPassConfiguration.primitiveAlignedStride());
    }

    @Test
    void singlePassSizesAreOnePrimitive() {
        // The two formats celtium ships: compact (24 bytes) and vanilla-like (32 bytes), both quad primitives.
        assertEquals(96, RenderPassConfiguration.primitiveAlignedStride(24 * 4));
        assertEquals(128, RenderPassConfiguration.primitiveAlignedStride(32 * 4));
    }

    @Test
    void shippedFormatsAlignToWholeQuads() {
        int elementSize = RenderPassConfiguration.primitiveAlignedStride(24 * 4, 32 * 4);
        assertEquals(384, elementSize);
        assertWholePrimitives(elementSize, 24, 4);
        assertWholePrimitives(elementSize, 32, 4);
    }

    @Test
    void strideOnlyLcmIsTheDefectCase() {
        // lcm(24, 32) = 96 holds three 32-byte vertices: an allocation could start mid-quad. The aligned size
        // must differ from the stride-only value exactly here, and must repair the quotient.
        int old = strideOnlyLcm(24, 32);
        assertEquals(96, old);
        assertNotEquals(0, (old / 32) % 4);
        int aligned = RenderPassConfiguration.primitiveAlignedStride(24 * 4, 32 * 4);
        assertNotEquals(old, aligned);
        assertEquals(0, (aligned / 32) % 4);
    }

    @Test
    void arbitraryStridesAndPrimitiveWidthsStayWhole() {
        // Strides that do not divide each other, and a non-quad primitive width, exercise the general form of
        // the formula rather than the shipped formats only.
        int elementSize = RenderPassConfiguration.primitiveAlignedStride(20 * 4, 36 * 4);
        assertEquals(720, elementSize);
        assertWholePrimitives(elementSize, 20, 4);
        assertWholePrimitives(elementSize, 36, 4);

        int triangles = RenderPassConfiguration.primitiveAlignedStride(24 * 3);
        assertEquals(72, triangles);
        assertWholePrimitives(triangles, 24, 3);
    }

    @Test
    void resultIsAMultipleOfEveryPrimitiveSize() {
        int[] primitiveSizes = {24 * 4, 32 * 4, 20 * 4, 36 * 4};
        int elementSize = RenderPassConfiguration.primitiveAlignedStride(primitiveSizes);
        for (int primitiveSize : primitiveSizes) {
            assertEquals(0, elementSize % primitiveSize);
        }
        assertTrue(elementSize > 0);
    }

    @Test
    void configurationComputesTheAlignedSizeFromItsPasses() {
        TerrainRenderPass compact = TerrainRenderPass.builder()
                .name("compact")
                .vertexType(ChunkMeshFormats.COMPACT)
                .primitiveType(QuadPrimitiveType.DIRECT)
                .build();
        TerrainRenderPass vanillaLike = TerrainRenderPass.builder()
                .name("vanilla-like")
                .vertexType(ChunkMeshFormats.VANILLA_LIKE)
                .primitiveType(QuadPrimitiveType.TRIANGULATED)
                .build();
        var configuration = new RenderPassConfiguration<String>(
                Map.of(),
                Map.of("stage", List.of(compact, vanillaLike)),
                null, null, null);
        assertEquals(384, configuration.commonVertexStride());
    }
}
