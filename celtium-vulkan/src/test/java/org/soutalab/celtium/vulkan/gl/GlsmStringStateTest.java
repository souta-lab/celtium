package org.soutalab.celtium.vulkan.gl;

import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The intern-buffer semantics {@link GlsmStringState} took over from the stub's field pair
 * (census S-23, superset-proof residual M-2): the answer string lands NUL-terminated at the
 * returned address, one buffer is shared and overwritten per call, and the buffer grows only
 * when a larger string arrives. The owner is a JVM singleton, so the tests are written to hold
 * at any starting capacity.
 */
class GlsmStringStateTest {

    @Test
    void internedStringReadsBackNulTerminatedAtTheReturnedAddress() {
        long address = GlsmStringState.current().intern("Celtium");
        assertEquals("Celtium", MemoryUtil.memUTF8(address));
        assertEquals(0, MemoryUtil.memGetByte(address + "Celtium".length()));
    }

    @Test
    void aSecondInternOverwritesTheSameBufferWithoutGrowing() {
        GlsmStringState state = GlsmStringState.current();
        long first = state.intern("Celtium");
        long second = state.intern("1.20");
        assertEquals(first, second);
        assertEquals("1.20", MemoryUtil.memUTF8(second));
    }

    @Test
    void aLargerStringGrowsTheBufferAndReadsBack() {
        GlsmStringState state = GlsmStringState.current();
        String large = "x".repeat(512);
        long address = state.intern(large);
        assertTrue(state.capacity() >= large.length() + 1);
        assertEquals(large, MemoryUtil.memUTF8(address));
    }

    @Test
    void theEmptyStringInternsAsJustTheTerminator() {
        long address = GlsmStringState.current().intern("");
        assertEquals("", MemoryUtil.memUTF8(address));
        assertEquals(0, MemoryUtil.memGetByte(address));
    }
}
