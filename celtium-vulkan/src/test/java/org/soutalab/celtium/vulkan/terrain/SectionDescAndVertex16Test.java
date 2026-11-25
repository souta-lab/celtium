package org.soutalab.celtium.vulkan.terrain;

import org.embeddedt.embeddium.impl.render.chunk.vertex.format.ChunkVertexEncoder;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SectionDescAndVertex16Test {

    @Test
    void sectionDescPacksAndUnpacksExact48ByteLayout() {
        SectionDesc.Record original = new SectionDesc.Builder()
                .section(-12, 4, 307)
                .allocation(2, 4096, 1024)
                .bucketCounts(new int[] {10, 20, 30, 40, 50, 60, 7})
                .passOrdinal(1)
                .animated(true)
                .generation(99)
                .sortedIndexBase(12345)
                .build();

        assertEquals(48, SectionDesc.STRIDE_BYTES);
        assertEquals(217, original.totalQuads());
        assertEquals(217 * 4, original.totalVertices());
        assertEquals(217 * 6, original.totalIndices());
        assertEquals(1024, original.bucketQuadOffset(SectionDesc.BUCKET_DOWN));
        assertEquals(1024 + 10, original.bucketQuadOffset(SectionDesc.BUCKET_UP));
        assertEquals(1024 + 10 + 20 + 30 + 40 + 50 + 60, original.bucketQuadOffset(SectionDesc.BUCKET_UNASSIGNED));
        assertEquals((1024 + 10) * 6, original.bucketFirstIndex(SectionDesc.BUCKET_UP));
        assertTrue(original.isAnimated());
        assertTrue(original.usesSortedIndex());
        assertFalse(original.isOversize());

        int[] ints = new int[SectionDesc.STRIDE_INTS];
        original.pack(ints, 0);
        assertEquals(original, SectionDesc.unpack(ints, 0));

        ByteBuffer buf = ByteBuffer.allocateDirect(SectionDesc.STRIDE_BYTES).order(ByteOrder.nativeOrder());
        original.pack(buf, 0);
        assertEquals(original, SectionDesc.unpack(buf, 0));
    }

    @Test
    void sectionDescFlagsOversizeAndRejectsEmpty() {
        assertThrows(IllegalStateException.class, () -> new SectionDesc.Builder()
                .section(0, 0, 0)
                .build());

        SectionDesc.Record oversize = new SectionDesc.Builder()
                .section(1, 2, 3)
                .bucketCount(SectionDesc.BUCKET_UP, 70000)
                .build();
        assertTrue(oversize.isOversize());
        assertEquals(SectionDesc.MAX_BUCKET_QUADS, oversize.bucketCount(SectionDesc.BUCKET_UP));
        assertEquals(SectionDesc.UNUSED_SORTED_INDEX_BASE, oversize.sortedIndexBase());
        assertFalse(oversize.usesSortedIndex());
    }

    @Test
    void terrainVertex16RoundTripsPositionUvLightMaterialColorAndNormal() {
        int[] words = new int[TerrainVertex16.STRIDE_INTS];
        int packedLight = (0xE0) | (0xF0 << 16); // block=224, sky=240
        int color = 0xFF88AA44;
        TerrainVertex16.encodeToInts(
                words, 0,
                3.25f, 15.5f, -0.5f,
                TerrainVertex16.NORMAL_NORTH,
                0x5,
                packedLight,
                0.375f, 0.8125f,
                color
        );

        TerrainVertex16.DecodedVertex decoded = TerrainVertex16.decode(words[0], words[1], words[2], words[3]);
        assertEquals(3.25f, decoded.x(), TerrainVertex16.MODEL_SCALE);
        assertEquals(15.5f, decoded.y(), TerrainVertex16.MODEL_SCALE);
        assertEquals(-0.5f, decoded.z(), TerrainVertex16.MODEL_SCALE);
        assertEquals(TerrainVertex16.NORMAL_NORTH, decoded.normalId());
        assertEquals(0x5, decoded.materialBits());
        assertEquals(0xE0, decoded.lightBlock());
        assertEquals(0xF0, decoded.lightSky());
        assertEquals(0.375f, decoded.u(), TerrainVertex16.TEXTURE_SCALE);
        assertEquals(0.8125f, decoded.v(), TerrainVertex16.TEXTURE_SCALE);
        assertEquals(color, decoded.color());
    }

    @Test
    void asyncSectionMesherCompactsSevenBucketsContiguously() {
        try (AsyncSectionMesher.Scratch scratch = new AsyncSectionMesher.Scratch()) {
            scratch.reset();
            assertNull(scratch.finish(0, 0, 0, 0, 1));

            ChunkVertexEncoder.Vertex[] quad = ChunkVertexEncoder.Vertex.uninitializedQuad();
            for (int i = 0; i < 4; i++) {
                quad[i].x = i;
                quad[i].y = 1.0f;
                quad[i].z = 2.0f;
                quad[i].u = 0.25f;
                quad[i].v = 0.5f;
                quad[i].color = 0xFFFFFFFF;
                quad[i].light = (0x80) | (0xF0 << 16);
            }

            scratch.emitQuad(SectionDesc.BUCKET_EAST, 1, quad, TerrainVertex16.NORMAL_EAST);
            scratch.emitQuad(SectionDesc.BUCKET_DOWN, 2, quad, TerrainVertex16.NORMAL_DOWN);
            scratch.markAnimated();

            try (AsyncSectionMesher.BuiltSection16 built = scratch.finish(3, 4, 5, 0, 7)) {
                assertNotNull(built);
                assertEquals(2, built.totalQuads());
                assertEquals(2 * AsyncSectionMesher.QUAD_BYTES, built.byteSize());
                assertEquals(1, built.bucketQuadCount(SectionDesc.BUCKET_DOWN));
                assertEquals(1, built.bucketQuadCount(SectionDesc.BUCKET_EAST));
                assertTrue(built.animated());

                // Bucket 0 (DOWN) must come before bucket 5 (EAST) in compacted output.
                ByteBuffer view = built.vertexDataView();
                TerrainVertex16.DecodedVertex first = TerrainVertex16.decodeFromBuffer(view, 0);
                TerrainVertex16.DecodedVertex second = TerrainVertex16.decodeFromBuffer(view, AsyncSectionMesher.QUAD_BYTES);
                assertEquals(TerrainVertex16.NORMAL_DOWN, first.normalId());
                assertEquals(TerrainVertex16.NORMAL_EAST, second.normalId());
            }
        }
    }
}
