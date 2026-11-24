package org.soutalab.celtium.vulkan.gl;

import org.junit.jupiter.api.Test;
import org.lwjgl.system.Callback;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the upcall machinery extracted into {@link GlTrampolines} (refactor/giant-split-3): which signatures may
 * ride the FFM upcall, which upcall shape a signature is read by, the per-signature descriptor cache, and the
 * libffi trampolines' routing into the dispatch entries they were constructed with - the float and double
 * entries converting to raw bits exactly as the stub's nested classes did.
 */
class GlTrampolinesTest {

    private static final class RecordingDispatch implements GlTrampolines.Dispatch {
        private int calls;
        private int functionId;
        private final long[] slots = new long[12];
        private long result = 4242L;

        @Override
        public long invoke(int functionId, long a0, long a1, long a2, long a3, long a4, long a5, long a6,
                long a7, long a8, long a9, long a10, long a11) {
            this.calls++;
            this.functionId = functionId;
            this.slots[0] = a0;
            this.slots[1] = a1;
            this.slots[2] = a2;
            this.slots[3] = a3;
            this.slots[4] = a4;
            this.slots[5] = a5;
            this.slots[6] = a6;
            this.slots[7] = a7;
            this.slots[8] = a8;
            this.slots[9] = a9;
            this.slots[10] = a10;
            this.slots[11] = a11;
            return this.result;
        }
    }

    private static final class RecordingLegacy implements GlTrampolines.LegacyDispatch {
        private int calls;
        private int functionId;
        private long result;
        private long args;

        @Override
        public void invoke(int functionId, long result, long args) {
            this.calls++;
            this.functionId = functionId;
            this.result = result;
            this.args = args;
        }
    }

    @Test
    void ffmCompatibilityExcludesFloatAndDoubleSignatures() {
        assertTrue(GlTrampolines.celtium$ffmCompatible(new byte[0]));
        assertTrue(GlTrampolines.celtium$ffmCompatible(
                new byte[] {GlsmFrontend.A_INT, GlsmFrontend.A_LONG, GlsmFrontend.A_PTR}));
        assertFalse(GlTrampolines.celtium$ffmCompatible(new byte[] {GlsmFrontend.A_FLOAT}));
        assertFalse(GlTrampolines.celtium$ffmCompatible(
                new byte[] {GlsmFrontend.A_INT, GlsmFrontend.A_DOUBLE}));
    }

    @Test
    void trampolineTypeFollowsWhereTheArgumentsLand() {
        assertSame(GlTrampolines.GlTrampolineDoubleI.class,
                GlTrampolines.trampolineType(new byte[] {GlsmFrontend.A_DOUBLE}));
        assertSame(GlTrampolines.GlTrampolineDoubleI.class, GlTrampolines.trampolineType(
                new byte[] {GlsmFrontend.A_FLOAT, GlsmFrontend.A_DOUBLE}));
        assertSame(GlTrampolines.GlTrampolineFloatI.class,
                GlTrampolines.trampolineType(new byte[] {GlsmFrontend.A_FLOAT, GlsmFrontend.A_FLOAT}));
        assertSame(GlTrampolines.GlTrampolineI.class,
                GlTrampolines.trampolineType(new byte[] {GlsmFrontend.A_INT, GlsmFrontend.A_PTR}));
        assertSame(GlTrampolines.GlTrampolineI.class, GlTrampolines.trampolineType(new byte[0]));
    }

    @Test
    void descriptorsAreCachedPerSignatureShape() {
        Callback.Descriptor first = GlTrampolines.descriptorFor(
                new byte[] {GlsmFrontend.A_INT, GlsmFrontend.A_PTR});
        assertSame(first, GlTrampolines.descriptorFor(
                new byte[] {GlsmFrontend.A_INT, GlsmFrontend.A_PTR}));
        assertNotSame(first, GlTrampolines.descriptorFor(new byte[] {GlsmFrontend.A_FLOAT}));
    }

    @Test
    void floatTrampolineConvertsToRawBitsAndRoutesBothEntries() {
        RecordingDispatch dispatch = new RecordingDispatch();
        RecordingLegacy legacy = new RecordingLegacy();
        GlTrampolines trampolines = new GlTrampolines(dispatch, legacy);
        byte[] args = {GlsmFrontend.A_FLOAT, GlsmFrontend.A_FLOAT, GlsmFrontend.A_FLOAT, GlsmFrontend.A_FLOAT};

        Callback callback = trampolines.libffiTrampoline("glClearColor", 7, args);
        assertSame(callback, trampolines.libffiTrampoline("glClearColor", 7, args));
        assertTrue(callback instanceof GlTrampolines.GlTrampolineFloatI);

        GlTrampolines.GlTrampolineFloatI entry = (GlTrampolines.GlTrampolineFloatI) callback;
        assertEquals(4242L, entry.invoke(0.25f, 0.5f, 0.75f, 1.0f, 0L, 0L, 0L, 0L));
        assertEquals(1, dispatch.calls);
        assertEquals(7, dispatch.functionId);
        assertEquals(Float.floatToRawIntBits(0.25f) & 0xFFFFFFFFL, dispatch.slots[0]);
        assertEquals(Float.floatToRawIntBits(0.5f) & 0xFFFFFFFFL, dispatch.slots[1]);
        assertEquals(Float.floatToRawIntBits(0.75f) & 0xFFFFFFFFL, dispatch.slots[2]);
        assertEquals(Float.floatToRawIntBits(1.0f) & 0xFFFFFFFFL, dispatch.slots[3]);
        assertEquals(0L, dispatch.slots[8]);

        entry.callback(123L, 456L);
        assertEquals(1, legacy.calls);
        assertEquals(7, legacy.functionId);
        assertEquals(123L, legacy.result);
        assertEquals(456L, legacy.args);
    }

    @Test
    void doubleTrampolineConvertsToRawLongBits() {
        RecordingDispatch dispatch = new RecordingDispatch();
        GlTrampolines trampolines = new GlTrampolines(dispatch, new RecordingLegacy());

        Callback callback = trampolines.libffiTrampoline("glClearDepth", 9, new byte[] {GlsmFrontend.A_DOUBLE});
        assertTrue(callback instanceof GlTrampolines.GlTrampolineDoubleI);

        GlTrampolines.GlTrampolineDoubleI entry = (GlTrampolines.GlTrampolineDoubleI) callback;
        entry.invoke(0.5, 11L, 0L, 0L, 0L, 0L, 0L, 0L);
        assertEquals(9, dispatch.functionId);
        assertEquals(Double.doubleToRawLongBits(0.5), dispatch.slots[0]);
        assertEquals(11L, dispatch.slots[1]);
        assertEquals(0L, dispatch.slots[8]);
    }

    @Test
    void genericTrampolinePassesEverySlotThrough() {
        RecordingDispatch dispatch = new RecordingDispatch();
        RecordingLegacy legacy = new RecordingLegacy();
        GlTrampolines trampolines = new GlTrampolines(dispatch, legacy);

        Callback callback = trampolines.libffiTrampoline("glTexImage2D", 5,
                new byte[] {GlsmFrontend.A_INT, GlsmFrontend.A_PTR});
        assertTrue(callback instanceof GlTrampolines.GlTrampolineI);

        GlTrampolines.GlTrampolineI entry = (GlTrampolines.GlTrampolineI) callback;
        entry.invoke(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L, 11L, 12L);
        assertEquals(5, dispatch.functionId);
        for (int slot = 0; slot < 12; slot++) {
            assertEquals(slot + 1L, dispatch.slots[slot]);
        }

        entry.callback(77L, 88L);
        assertEquals(1, legacy.calls);
        assertEquals(5, legacy.functionId);
    }

    @Test
    void addressForReturnsAStableNonZeroAddressPerName() {
        GlTrampolines trampolines = new GlTrampolines(new RecordingDispatch(), new RecordingLegacy());
        byte[] floatArgs = {GlsmFrontend.A_FLOAT, GlsmFrontend.A_FLOAT, GlsmFrontend.A_FLOAT,
                GlsmFrontend.A_FLOAT};
        long first = trampolines.addressFor("glClearColor", 7, floatArgs);
        assertTrue(first != 0L);
        assertEquals(first, trampolines.addressFor("glClearColor", 7, floatArgs));

        byte[] intArgs = {GlsmFrontend.A_INT};
        long resolved = trampolines.addressFor("glSomeIntegerCall", 3, intArgs);
        assertTrue(resolved != 0L);
        assertEquals(resolved, trampolines.addressFor("glSomeIntegerCall", 3, intArgs));
    }
}
