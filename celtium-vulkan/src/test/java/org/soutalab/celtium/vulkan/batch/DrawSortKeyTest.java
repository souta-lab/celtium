package org.soutalab.celtium.vulkan.batch;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DrawSortKeyTest {

    @Test
    void roundtrip() {
        long key = DrawSortKey.encode(3, 0xABCD, 0x5A, 7, 0x123456);
        assertEquals(3, DrawSortKey.passId(key));
        assertEquals(0xABCD, DrawSortKey.pipelineKey(key));
        assertEquals(0x5A, DrawSortKey.stateKey(key));
        assertEquals(7, DrawSortKey.layer(key));
        assertEquals(0x123456, DrawSortKey.sequence(key));
    }

    @Test
    void fieldPriorityIsPassThenPipelineThenStateThenLayerThenSequence() {
        long base = DrawSortKey.encode(1, 2, 3, 4, 5);
        assertTrue(DrawSortKey.encode(2, 0, 0, 0, 0) > base);
        assertTrue(DrawSortKey.encode(1, 3, 0, 0, 0) > base);
        assertTrue(DrawSortKey.encode(1, 2, 4, 0, 0) > base);
        assertTrue(DrawSortKey.encode(1, 2, 3, 5, 0) > base);
        assertTrue(DrawSortKey.encode(1, 2, 3, 4, 6) > base);
        assertTrue(DrawSortKey.encode(0, 0xFFFF, 0xFF, 0xFF, 0xFFFFFF) < base);
    }

    @Test
    void orderingHoldsAcrossTheSignBit() {
        // Pass ids at and above 128 set the packed top bit; the encoded order must still be the
        // field order.
        assertTrue(DrawSortKey.encode(200, 0, 0, 0, 0) > DrawSortKey.encode(100, 0xFFFF, 0xFF, 0xFF, 0xFFFFFF));
        assertTrue(DrawSortKey.encode(255, 0, 0, 0, 0) > DrawSortKey.encode(200, 0, 0, 0, 0));
        assertTrue(DrawSortKey.encode(0, 0, 0, 0, 0) < DrawSortKey.encode(100, 0, 0, 0, 0));
    }

    @Test
    void outOfRangeFieldsAreMasked() {
        long key = DrawSortKey.encode(0x1FF, 0x1FFFF, 0x1FF, 0x1FF, 0x1FFFFFF);
        assertEquals(0xFF, DrawSortKey.passId(key));
        assertEquals(0xFFFF, DrawSortKey.pipelineKey(key));
        assertEquals(0xFF, DrawSortKey.stateKey(key));
        assertEquals(0xFF, DrawSortKey.layer(key));
        assertEquals(0xFFFFFF, DrawSortKey.sequence(key));
    }
}
