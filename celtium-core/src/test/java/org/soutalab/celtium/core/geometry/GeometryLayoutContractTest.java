package org.soutalab.celtium.core.geometry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

import org.junit.jupiter.api.Test;

/**
 * The geometry contracts are byte sizes and bit layouts (design section 2.1): 16 B / 64 B / 48 B.
 * These tests pin them, because every later stage generates and consumes exactly these bits.
 */
class GeometryLayoutContractTest {

    @Test
    void layoutSizesMatchTheContract() {
        assertEquals(16L, PackedAxisQuad.BYTE_SIZE);
        assertEquals(16L, PackedAxisQuad.LAYOUT.byteSize());
        assertEquals(64L, GeneralQuad.BYTE_SIZE);
        assertEquals(64L, GeneralQuad.LAYOUT.byteSize());
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
