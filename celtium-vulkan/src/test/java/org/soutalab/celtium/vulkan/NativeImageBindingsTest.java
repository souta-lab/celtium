package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NativeImageBindingsTest {
    @Test
    void recordsFullTupleIgnoresInvalidUnitsAndClearsDeletedNames() {
        NativeImageBindings bindings = new NativeImageBindings();
        bindings.bind(2, 91, 3, true, 4, 0x88BA, 0x881A);
        assertEquals(91, bindings.texture(2));
        assertEquals(3, bindings.level(2));
        assertTrue(bindings.layered(2));
        assertEquals(4, bindings.layer(2));
        assertEquals(0x88BA, bindings.access(2));
        assertEquals(0x881A, bindings.format(2));
        bindings.bind(-1, 12, 0, false, 0, 0, 0);
        bindings.bind(NativeImageBindings.IMAGE_UNITS_PER_PROGRAM, 13, 0, false, 0, 0, 0);
        assertEquals(0, bindings.texture(-1));
        assertEquals(0, bindings.texture(NativeImageBindings.IMAGE_UNITS_PER_PROGRAM));
        bindings.delete(91);
        assertEquals(0, bindings.texture(2));
        assertFalse(bindings.layered(2));
    }
}
