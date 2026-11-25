package org.soutalab.celtium.vulkan.meshb;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pins the Java half of the Tier-B terrain data contract (MeshTerrainLayout) to the
 * canonical SectionDesc / TerrainVertex16 definition (SECTION-DESC-SPEC-2026-11-25.md)
 * and to the GLSL include the Tier-B stages compile against
 * (terrain_meshb_layout.glsl): word positions, field widths, bucket order, prefix-sum
 * derivation and meshlet arithmetic. If the canonical definition moves, this test and
 * the two mirror files move together.
 */
class MeshTerrainLayoutTest {

    private static ByteBuffer buffer(int bytes) {
        return ByteBuffer.allocate(bytes).order(ByteOrder.LITTLE_ENDIAN);
    }

    @Test
    void descRecordMatchesCanonicalWords() {
        ByteBuffer out = buffer(MeshTerrainLayout.DESC_BYTES);
        int[] counts = {10, 0, 65535, 3, 64, 1, 7};
        MeshTerrainLayout.packDesc(out, 0, -3, 4, 12, 0x11223344, 4096, 1024, counts,
                2, MeshTerrainLayout.FLAG_ANIMATED | MeshTerrainLayout.FLAG_SORTED_INDEX,
                77, MeshTerrainLayout.SORTED_INDEX_UNUSED);
        assertEquals(-3, out.getInt(0));
        assertEquals(4, out.getInt(4));
        assertEquals(12, out.getInt(8));
        assertEquals(0x11223344, out.getInt(12));
        assertEquals(4096, out.getInt(16));
        assertEquals(1024, out.getInt(20));
        // Bucket counts pack as u16 pairs, low bucket in the low half.
        assertEquals(10 | (0 << 16), out.getInt(24));
        assertEquals(65535 | (3 << 16), out.getInt(28));
        assertEquals(64 | (1 << 16), out.getInt(32));
        // Word 9: count[6] low, pass ordinal at bits 16..23, flags at bits 24..31.
        assertEquals(7 | (2 << 16) | 0x06000000, out.getInt(36));
        assertEquals(77, out.getInt(40));
        assertEquals(0xFFFFFFFF, out.getInt(44));
        assertEquals(48, MeshTerrainLayout.DESC_BYTES);
        assertEquals(12, MeshTerrainLayout.DESC_WORDS);
    }

    @Test
    void bucketStartsArePrefixSumsInCanonicalOrder() {
        int[] counts = {10, 20, 30, 40, 50, 60, 70};
        assertEquals(0, MeshTerrainLayout.bucketStartInSection(counts,
                MeshTerrainLayout.BUCKET_POS_X));
        assertEquals(10, MeshTerrainLayout.bucketStartInSection(counts,
                MeshTerrainLayout.BUCKET_POS_Y));
        assertEquals(30, MeshTerrainLayout.bucketStartInSection(counts,
                MeshTerrainLayout.BUCKET_POS_Z));
        assertEquals(60, MeshTerrainLayout.bucketStartInSection(counts,
                MeshTerrainLayout.BUCKET_NEG_X));
        assertEquals(210, MeshTerrainLayout.bucketStartInSection(counts,
                MeshTerrainLayout.BUCKET_UNASSIGNED));
        assertEquals(280, MeshTerrainLayout.totalQuads(counts));
        // The bucket numbering is ModelQuadFacing.VALUES order, not EnumFacing.
        assertEquals(0, MeshTerrainLayout.BUCKET_POS_X);
        assertEquals(1, MeshTerrainLayout.BUCKET_POS_Y);
        assertEquals(2, MeshTerrainLayout.BUCKET_POS_Z);
        assertEquals(3, MeshTerrainLayout.BUCKET_NEG_X);
        assertEquals(4, MeshTerrainLayout.BUCKET_NEG_Y);
        assertEquals(5, MeshTerrainLayout.BUCKET_NEG_Z);
        assertEquals(6, MeshTerrainLayout.BUCKET_UNASSIGNED);
    }

    @Test
    void vertexFieldsLandInCanonicalBitPositions() {
        ByteBuffer out = buffer(MeshTerrainLayout.VERTEX_BYTES);
        // local (0, 8, 15.5) -> stored ((local + 8) * 128) = (1024, 2048, 3008).
        // uv (0.5, 0.25) -> 18-bit values (65536, 32768): high16 (16384, 8192), low 0.
        MeshTerrainLayout.packVertex(out, 0, 0.0f, 8.0f, 15.5f,
                0x11223344, 0.5f, 0.25f, 5, 0xA, 200, 100);
        // word 0: posX | posY << 12 | posZ low 8 << 24.
        assertEquals(1024 | (2048 << 12) | ((3008 & 0xFF) << 24), out.getInt(0));
        // word 1: posZ high | normal << 4 | material << 7 | block << 11 | sky << 19
        //         | uvLowU << 27 | uvLowV << 29.
        assertEquals(((3008 >> 8) & 0xF) | (5 << 4) | (0xA << 7) | (200 << 11) | (100 << 19),
                out.getInt(4));
        // word 2: texU high | texV high << 16.
        assertEquals(16384 | (8192 << 16), out.getInt(8));
        assertEquals(0x11223344, out.getInt(12));
        assertEquals(16, MeshTerrainLayout.VERTEX_BYTES);
    }

    @Test
    void vertexClampsAndMasksOutOfRangeInputs() {
        ByteBuffer out = buffer(MeshTerrainLayout.VERTEX_BYTES);
        // Position beyond the model range clamps to the 12-bit ceiling; light texel
        // coords mask to 8 bits; normal/material mask to their widths; uv clamps.
        MeshTerrainLayout.packVertex(out, 0, 100.0f, -100.0f, 0.0f,
                0, 3.0f, -1.0f, 9, 0x1F, 300, -1);
        assertEquals(0xFFF | (0 << 12) | ((1024 & 0xFF) << 24), out.getInt(0));
        int word1 = out.getInt(4);
        assertEquals((1024 >> 8) & 0xF, word1 & 0xF);
        assertEquals(1, (word1 >> 4) & 7);
        assertEquals(0xF, (word1 >> 7) & 0xF);
        assertEquals(44, (word1 >> 11) & 0xFF);
        assertEquals(255, (word1 >> 19) & 0xFF);
        // uv clamped to the 18-bit ceiling / floor: high16 = 0xFFFF / 0, lows = 3 / 0.
        assertEquals(0xFFFF, out.getInt(8) & 0xFFFF);
        assertEquals(0, (out.getInt(8) >> 16) & 0xFFFF);
        assertEquals(3, (word1 >> 27) & 3);
        assertEquals(0, (word1 >> 29) & 3);
    }

    @Test
    void meshletArithmeticMatchesShader() {
        assertEquals(0, MeshTerrainLayout.meshletsForCount(0));
        assertEquals(1, MeshTerrainLayout.meshletsForCount(1));
        assertEquals(1, MeshTerrainLayout.meshletsForCount(64));
        assertEquals(2, MeshTerrainLayout.meshletsForCount(65));
        int[] counts = {64, 65, 0, 1, 128, 0, 63};
        int allBuckets = 0x7F;
        assertEquals(1 + 2 + 0 + 1 + 2 + 0 + 1,
                MeshTerrainLayout.meshletsForMask(counts, allBuckets));
        int onlyPosXAndUnassigned = (1 << MeshTerrainLayout.BUCKET_POS_X)
                | (1 << MeshTerrainLayout.BUCKET_UNASSIGNED);
        assertEquals(1 + 1, MeshTerrainLayout.meshletsForMask(counts, onlyPosXAndUnassigned));
        assertEquals(0, MeshTerrainLayout.meshletsForMask(counts, 0));
    }
}
