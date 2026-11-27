package org.soutalab.celtium.core.geometry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

import org.junit.jupiter.api.Test;

/**
 * The geometry contracts are byte sizes and bit layouts (design section 2.1): 16 B / 80 B / 48 B.
 * These tests pin them, because every later stage generates and consumes exactly these bits.
 * The general quad's 80 B form (64 B base + 16 B of per-corner Q1.15 sprite-local UV) is the
 * Neon UV ruling NEON-UV-RULING-2026-11-28.
 */
class GeometryLayoutContractTest {

    @Test
    void layoutSizesMatchTheContract() {
        assertEquals(16L, PackedAxisQuad.BYTE_SIZE);
        assertEquals(16L, PackedAxisQuad.LAYOUT.byteSize());
        assertEquals(80L, GeneralQuad.BYTE_SIZE);
        assertEquals(80L, GeneralQuad.LAYOUT.byteSize());
        assertEquals(0L, GeneralQuad.BYTE_SIZE % 16L);
        assertEquals(64L, GeneralQuad.UV_PACKED_OFFSET);
        assertEquals(48L, SectionDesc.BYTE_SIZE);
        assertEquals(48L, SectionDesc.LAYOUT.byteSize());
    }

    @Test
    void packedAxisQuadFieldsRoundTrip() {
        PackedAxisQuad quad = PackedAxisQuad.of(
                3, 17, 9, 2, true,
                12, 5, 123456,
                40, 41, 15, 7, 200,
                10, 20, 30, PackedAxisQuad.FLAG_TINT_PRESENT);
        assertEquals(3, quad.minX());
        assertEquals(17, quad.minY());
        assertEquals(9, quad.minZ());
        assertEquals(2, quad.axis());
        assertTrue(quad.negativeFacing());
        assertEquals(12, quad.extentU());
        assertEquals(5, quad.extentV());
        assertEquals(123456, quad.textureIndex());
        assertEquals(40, quad.cellU());
        assertEquals(41, quad.cellV());
        assertEquals(15, quad.skyLight());
        assertEquals(7, quad.blockLight());
        assertEquals(200, quad.shade());
        assertEquals(10, quad.tintR());
        assertEquals(20, quad.tintG());
        assertEquals(30, quad.tintB());
        assertTrue(quad.hasTint());
    }

    @Test
    void packedAxisQuadRejectsOutOfRangeFields() {
        assertThrows(IllegalArgumentException.class, () -> PackedAxisQuad.of(
                64, 0, 0, 0, false, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> PackedAxisQuad.of(
                0, 0, 0, 0, false, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> PackedAxisQuad.of(
                0, 0, 0, 3, false, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> PackedAxisQuad.of(
                0, 0, 0, 0, false, 1, 1, 1 << 20, 0, 0, 0, 0, 0, 0, 0, 0, 0));
    }

    @Test
    void packedAxisQuadSegmentRoundTrip() {
        PackedAxisQuad quad = PackedAxisQuad.of(
                1, 2, 3, 1, false, 16, 16, 7, 0, 0, 3, 4, 255, 0, 0, 0, 0);
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment segment = arena.allocate(PackedAxisQuad.BYTE_SIZE * 2L);
            quad.writeTo(segment, PackedAxisQuad.BYTE_SIZE);
            assertEquals(quad, PackedAxisQuad.readFrom(segment, PackedAxisQuad.BYTE_SIZE));
        }
    }

    @Test
    void generalQuadSegmentRoundTrip() {
        float[] positions = {0f, 1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f, 9f, 10f, 11f};
        GeneralQuad quad = new GeneralQuad(positions, 42, 0x1234, GeneralQuad.packNormal(0f, 1f, 0f), 9);
        positions[0] = 99f;
        assertEquals(0f, quad.cornerX(0), 0.0f);
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment segment = arena.allocate(GeneralQuad.BYTE_SIZE);
            quad.writeTo(segment, 0L);
            assertEquals(quad, GeneralQuad.readFrom(segment, 0L));
        }
    }

    @Test
    void generalQuadUvSegmentRoundTrip() {
        float[] positions = {0f, 1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f, 9f, 10f, 11f};
        int[] uvs = {GeneralQuad.packUv(0.25f, 0.5f), GeneralQuad.packUv(0.25f, 0.75f),
                GeneralQuad.packUv(0.5f, 0.75f), GeneralQuad.packUv(0.5f, 0.5f)};
        GeneralQuad quad = new GeneralQuad(positions, 7, 3, 0, 0, uvs);
        uvs[0] = 0;
        assertEquals(GeneralQuad.packUv(0.25f, 0.5f), quad.uvPacked(0));
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment segment = arena.allocate(GeneralQuad.BYTE_SIZE);
            quad.writeTo(segment, 0L);
            assertEquals(quad, GeneralQuad.readFrom(segment, 0L));
            // The UV words land at offsets 64..79 and nowhere else.
            assertEquals(quad.uvPacked(3), segment.get(java.lang.foreign.ValueLayout.JAVA_INT, 76L));
        }
        assertThrows(IllegalArgumentException.class,
                () -> new GeneralQuad(new float[12], 0, 0, 0, 0, new int[3]));
    }

    /** Ruling section 5.1: dyadic boundaries round-trip through Q1.15 with zero error. */
    @Test
    void generalQuadUvBoundariesAreBitExact() {
        float[] exact = {0.0f, 1.0f, 0.5f, 0.25f, 0.75f};
        for (float v : exact) {
            assertUvRoundTrip(v, v, 0.0f);
        }
        for (int k = 0; k <= 16; k++) {
            assertUvRoundTrip(k / 16.0f, k / 16.0f, 0.0f);
        }
        for (int k = 0; k <= 64; k++) {
            assertUvRoundTrip(k / 64.0f, k / 64.0f, 0.0f);
        }
    }

    /** Ruling section 5.1: rotated / flipped / partial-rectangle corner UVs round-trip per corner. */
    @Test
    void generalQuadUvArbitraryCornersRoundTrip() {
        float[][] corners = {
                {0.1f, 0.9f},   // flipped V
                {0.333f, 0.125f}, // partial rectangle corner
                {0.9f, 0.1f},   // 90-degree rotated partner
                {0.6180339f, 0.382f}}; // non-dyadic diagonal
        int[] packed = new int[4];
        for (int c = 0; c < 4; c++) {
            packed[c] = GeneralQuad.packUv(corners[c][0], corners[c][1]);
        }
        float tolerance = 1.0f / 65536.0f;
        for (int c = 0; c < 4; c++) {
            assertEquals(corners[c][0], GeneralQuad.unpackUvU(packed[c]), tolerance);
            assertEquals(corners[c][1], GeneralQuad.unpackUvV(packed[c]), tolerance);
        }
    }

    /** Ruling section 5.1: out-of-range and non-finite inputs clamp without cross-half leakage. */
    @Test
    void generalQuadUvClampingIsSafe() {
        assertEquals(0.0f, GeneralQuad.unpackUvU(GeneralQuad.packUv(-1e-5f, -1e-5f)), 0.0f);
        assertEquals(1.0f, GeneralQuad.unpackUvU(GeneralQuad.packUv(1.00001f, 0.0f)), 0.0f);
        assertEquals(0.0f, GeneralQuad.unpackUvU(GeneralQuad.packUv(Float.NaN, 0.0f)), 0.0f);
        assertEquals(0.0f, GeneralQuad.unpackUvV(GeneralQuad.packUv(0.0f, Float.POSITIVE_INFINITY)), 0.0f);
        assertEquals(0.0f, GeneralQuad.unpackUvU(GeneralQuad.packUv(Float.NEGATIVE_INFINITY, 0.0f)), 0.0f);
        // 32768 is 0x8000: the halves must not sign-extend into each other.
        int both = GeneralQuad.packUv(1.0f, 1.0f);
        assertEquals(0x8000, both & 0xFFFF);
        assertEquals(0x8000, (both >>> 16) & 0xFFFF);
        assertEquals(1.0f, GeneralQuad.unpackUvU(both), 0.0f);
        assertEquals(1.0f, GeneralQuad.unpackUvV(both), 0.0f);
        int vOnly = GeneralQuad.packUv(0.0f, 1.0f);
        assertEquals(0, vOnly & 0xFFFF);
        assertEquals(0.0f, GeneralQuad.unpackUvU(vOnly), 0.0f);
    }

    private static void assertUvRoundTrip(float u, float v, float tolerance) {
        int packed = GeneralQuad.packUv(u, v);
        assertEquals(u, GeneralQuad.unpackUvU(packed), tolerance);
        assertEquals(v, GeneralQuad.unpackUvV(packed), tolerance);
    }

    @Test
    void generalQuadNormalPackingRoundTrips() {
        int packed = GeneralQuad.packNormal(0.0f, 1.0f, -1.0f);
        assertEquals(0.0f, GeneralQuad.unpackNormalComponent(packed, 0), 0.002f);
        assertEquals(1.0f, GeneralQuad.unpackNormalComponent(packed, 10), 0.002f);
        assertEquals(-1.0f, GeneralQuad.unpackNormalComponent(packed, 20), 0.002f);
        assertThrows(IllegalArgumentException.class, () -> GeneralQuad.packNormal(0f, 2f, 0f));
        assertThrows(IllegalArgumentException.class,
                () -> new GeneralQuad(new float[11], 0, 0, 0, 0));
    }

    @Test
    void sectionDescSegmentRoundTripAndAuxiliaryRule() {
        SectionDesc desc = new SectionDesc(10, 4, -7, 3, 4096L, 8192L, 120, 5, 64, SectionDesc.NO_AUXILIARY);
        assertFalse(desc.hasAuxiliaryColumn());
        assertEquals(125, desc.totalQuadCount());
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment segment = arena.allocate(SectionDesc.BYTE_SIZE);
            desc.writeTo(segment, 0L);
            assertEquals(desc, SectionDesc.readFrom(segment, 0L));
        }
        SectionDesc withAux = new SectionDesc(0, 0, 0, 0, 0L, 0L, 8, 0, 0, 2048);
        assertTrue(withAux.hasAuxiliaryColumn());
        assertThrows(IllegalArgumentException.class,
                () -> new SectionDesc(0, 0, 0, 0, -1L, 0L, 0, 0, 0, SectionDesc.NO_AUXILIARY));
    }
}
