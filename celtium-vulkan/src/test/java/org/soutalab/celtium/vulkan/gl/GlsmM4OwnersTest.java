package org.soutalab.celtium.vulkan.gl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GLSM M-4 (INT-2 remainder): the clear-stencil record in {@link GlsmClearState} and the indexed
 * buffer bindings in {@link GlsmBufferState}. Both owners are singletons, so the assertions are
 * delta-based with values no other test uses. Not run by the alpha lane (alpha rules); the merge
 * phase executes it.
 */
class GlsmM4OwnersTest {

    private static final int TARGET = 0x8D62; // arbitrary target constant no other test binds indexed slots on
    private static final int NAME = 987_654;

    @Test
    void clearStencilIsRecordedAndReadable() {
        GlsmClearState state = GlsmClearState.current();
        long before = state.version();
        state.clearStencil(0x5A);
        assertEquals(0x5A, state.stencil());
        assertTrue(state.version() > before);
        state.clearStencil(0);
        assertEquals(0, state.stencil());
    }

    @Test
    void bindBufferBaseRecordsAWholeBufferBinding() {
        GlsmBufferState state = GlsmBufferState.current();
        state.bindBufferBase(TARGET, 7, NAME);
        GlsmBufferState.IndexedBinding binding = state.indexedBinding(TARGET, 7);
        assertNotNull(binding);
        assertEquals(NAME, binding.buffer);
        assertEquals(0L, binding.offset);
        assertEquals(-1L, binding.size);
        assertFalse(binding.ranged);
    }

    @Test
    void bindBufferRangeRecordsTheRangeAndIndicesStayIndependent() {
        GlsmBufferState state = GlsmBufferState.current();
        state.bindBufferRange(TARGET, 8, NAME, 256L, 512L);
        state.bindBufferBase(TARGET, 9, NAME + 1);
        GlsmBufferState.IndexedBinding ranged = state.indexedBinding(TARGET, 8);
        assertNotNull(ranged);
        assertEquals(256L, ranged.offset);
        assertEquals(512L, ranged.size);
        assertTrue(ranged.ranged);
        GlsmBufferState.IndexedBinding other = state.indexedBinding(TARGET, 9);
        assertNotNull(other);
        assertEquals(NAME + 1, other.buffer);
        assertNull(state.indexedBinding(TARGET, 10));
    }

    @Test
    void bindingNameZeroUnbindsTheSlot() {
        GlsmBufferState state = GlsmBufferState.current();
        state.bindBufferBase(TARGET, 11, NAME);
        assertNotNull(state.indexedBinding(TARGET, 11));
        state.bindBufferBase(TARGET, 11, 0);
        assertNull(state.indexedBinding(TARGET, 11));
    }

    @Test
    void unbindNameSweepsTheIndexedLedgerToo() {
        GlsmBufferState state = GlsmBufferState.current();
        state.bindBufferBase(TARGET, 12, NAME);
        state.bindBufferRange(TARGET, 13, NAME, 0L, 64L);
        state.bindBufferBase(TARGET, 14, NAME + 2);
        state.unbindName(NAME);
        assertNull(state.indexedBinding(TARGET, 12));
        assertNull(state.indexedBinding(TARGET, 13));
        assertNotNull(state.indexedBinding(TARGET, 14));
        state.unbindName(NAME + 2);
    }
}
