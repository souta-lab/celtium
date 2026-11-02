package org.soutalab.celtium.vulkan.gl;

import java.nio.ByteBuffer;

import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The copy contract of a display list's compiled legacy run, checked without a GPU.
 *
 * <p>What this pins is the one thing that can go wrong silently: a list that remembered the capture's buffer instead of
 * copying would replay whatever the next {@code glBegin} of the frame left there. The capture owns one buffer for the
 * whole frame, so the test writes one run, copies it, overwrites the capture's buffer with a different run, and checks
 * that the copy still holds the first run's vertices. It also pins the size arithmetic - the number of bytes copied is
 * {@code count * stride}, not {@code count * 4} - and that a request that cannot be paid for answers {@code null}
 * rather than a half-filled buffer.
 */
class CompiledLegacyRunTest {

    @Test
    void theCopySurvivesTheCaptureBufferBeingReused() {
        ByteBuffer source = MemoryUtil.memAlloc(4 * LegacyImmediateCapture.STRIDE_POSITION_COLOR);
        long address = MemoryUtil.memAddress(source);
        for (int vertex = 0; vertex < 4; vertex++) {
            long at = address + (long) vertex * LegacyImmediateCapture.STRIDE_POSITION_COLOR;
            MemoryUtil.memPutFloat(at, vertex);
            MemoryUtil.memPutFloat(at + 4L, vertex + 10.0f);
            MemoryUtil.memPutFloat(at + 8L, 0.5f);
            MemoryUtil.memPutByte(at + 12L, (byte) 0x11);
            MemoryUtil.memPutByte(at + 13L, (byte) 0x22);
            MemoryUtil.memPutByte(at + 14L, (byte) 0x33);
            MemoryUtil.memPutByte(at + 15L, (byte) 0xFF);
        }
        CompiledLegacyRun run = CompiledLegacyRun.copy(LegacyImmediateCapture.FORMAT_POSITION_COLOR,
                LegacyImmediateCapture.STRIDE_POSITION_COLOR, 4, 7 /* GL_QUADS */, address,
                CompiledLegacyRun.ATTRIBUTE_COLOUR_FROM_CALL);
        assertNotNull(run, "a four-vertex run is copyable");
        assertEquals(4 * LegacyImmediateCapture.STRIDE_POSITION_COLOR, run.bytes());
        assertEquals(4, run.count);
        assertEquals(7, run.mode);
        assertEquals(LegacyImmediateCapture.FORMAT_POSITION_COLOR, run.format);

        // The capture reuses its buffer for the next glBegin: the copy must not follow it.
        for (int vertex = 0; vertex < 4; vertex++) {
            long at = address + (long) vertex * LegacyImmediateCapture.STRIDE_POSITION_COLOR;
            MemoryUtil.memPutFloat(at, 99.0f);
            MemoryUtil.memPutFloat(at + 4L, 99.0f);
            MemoryUtil.memPutFloat(at + 8L, 99.0f);
            MemoryUtil.memPutByte(at + 12L, (byte) 0);
            MemoryUtil.memPutByte(at + 13L, (byte) 0);
            MemoryUtil.memPutByte(at + 14L, (byte) 0);
            MemoryUtil.memPutByte(at + 15L, (byte) 0);
        }
        long copied = run.address;
        assertEquals(0.0f, MemoryUtil.memGetFloat(copied), "first x is the compiled run's, not the reused buffer's");
        assertEquals(10.0f, MemoryUtil.memGetFloat(copied + 4L));
        assertEquals(0.5f, MemoryUtil.memGetFloat(copied + 8L));
        assertEquals((byte) 0x11, MemoryUtil.memGetByte(copied + 12L), "the colour bytes are part of the copy");
        assertEquals((byte) 0x22, MemoryUtil.memGetByte(copied + 13L));
        assertEquals((byte) 0x33, MemoryUtil.memGetByte(copied + 14L));
        assertEquals((byte) 0xFF, MemoryUtil.memGetByte(copied + 15L));
        long last = copied + 3L * LegacyImmediateCapture.STRIDE_POSITION_COLOR;
        assertEquals(3.0f, MemoryUtil.memGetFloat(last), "the fourth vertex is in the copy too");
        assertEquals(13.0f, MemoryUtil.memGetFloat(last + 4L));

        run.free();
        MemoryUtil.memFree(source);
    }

    @Test
    void aRequestThatHoldsNoVerticesIsNotARun() {
        assertNull(CompiledLegacyRun.copy(LegacyImmediateCapture.FORMAT_POSITION_COLOR,
                LegacyImmediateCapture.STRIDE_POSITION_COLOR, 0, 7, 1L, 0), "zero vertices");
        assertNull(CompiledLegacyRun.copy(LegacyImmediateCapture.FORMAT_POSITION_COLOR,
                LegacyImmediateCapture.STRIDE_POSITION_COLOR, 4, 7, 0L, 0), "no source address");
        assertNull(CompiledLegacyRun.copy(LegacyImmediateCapture.FORMAT_POSITION_COLOR,
                0, 4, 7, 1L, 0), "no vertex width");
    }

    /**
     * The one thing a display list must not freeze is the value of an attribute it never set, and this is where the
     * replay puts the caller's value: into the run's own copy, at the offsets of the layout it was packed for.
     */
    @Test
    void anAttributeTheListNeverSetIsWrittenFromTheCallState() {
        ByteBuffer source = MemoryUtil.memAlloc(2 * LegacyImmediateCapture.STRIDE_POSITION_COLOR);
        long address = MemoryUtil.memAddress(source);
        for (int vertex = 0; vertex < 2; vertex++) {
            long at = address + (long) vertex * LegacyImmediateCapture.STRIDE_POSITION_COLOR;
            MemoryUtil.memPutFloat(at, vertex);
            MemoryUtil.memPutByte(at + 12L, (byte) 0x7F); // a compile-time colour the list did not set
        }
        CompiledLegacyRun run = CompiledLegacyRun.copy(LegacyImmediateCapture.FORMAT_POSITION_COLOR,
                LegacyImmediateCapture.STRIDE_POSITION_COLOR, 2, 7, address,
                CompiledLegacyRun.ATTRIBUTE_COLOUR_FROM_CALL);
        assertTrue(run.applyCallState(1.0f, 0.0f, 0.0f, 0.5f, 0.0f, 0.0f), "the colour was left to the call");
        for (int vertex = 0; vertex < 2; vertex++) {
            long at = run.address + (long) vertex * LegacyImmediateCapture.STRIDE_POSITION_COLOR;
            assertEquals((byte) 255, MemoryUtil.memGetByte(at + 12L), "red is the call-time colour");
            assertEquals((byte) 0, MemoryUtil.memGetByte(at + 13L));
            assertEquals((byte) 0, MemoryUtil.memGetByte(at + 14L));
            assertEquals((byte) 128, MemoryUtil.memGetByte(at + 15L), "0.5 alpha rounds to 128");
            assertEquals((float) vertex, MemoryUtil.memGetFloat(at), "positions are untouched");
        }
        assertEquals(1, run.callTimeColourReplays());
        // A second replay with another colour is what a list drawn twice in a frame does: the last one wins.
        assertTrue(run.applyCallState(0.0f, 0.0f, 1.0f, 1.0f, 0.0f, 0.0f));
        assertEquals((byte) 255, MemoryUtil.memGetByte(run.address + 14L), "the newest call-time colour is written");
        assertEquals(2, run.callTimeColourReplays());

        run.free();
        MemoryUtil.memFree(source);
    }

    @Test
    void aTexturedRunTakesTheCoordinateAndKeepsItsColourOffsets() {
        ByteBuffer source = MemoryUtil.memAlloc(LegacyImmediateCapture.STRIDE_POSITION_TEX_COLOR);
        long address = MemoryUtil.memAddress(source);
        MemoryUtil.memPutFloat(address + 12L, 0.125f);
        MemoryUtil.memPutFloat(address + 16L, 0.875f);
        MemoryUtil.memPutByte(address + 20L, (byte) 3);
        CompiledLegacyRun run = CompiledLegacyRun.copy(LegacyImmediateCapture.FORMAT_POSITION_TEX_COLOR,
                LegacyImmediateCapture.STRIDE_POSITION_TEX_COLOR, 1, 7, address,
                CompiledLegacyRun.ATTRIBUTE_TEX_FROM_CALL | CompiledLegacyRun.ATTRIBUTE_COLOUR_FROM_CALL);
        assertTrue(run.applyCallState(0.0f, 1.0f, 0.0f, 1.0f, 0.25f, 0.75f));
        assertEquals(0.25f, MemoryUtil.memGetFloat(run.address + 12L), "s is the call-time coordinate");
        assertEquals(0.75f, MemoryUtil.memGetFloat(run.address + 16L));
        assertEquals((byte) 255, MemoryUtil.memGetByte(run.address + 21L), "the colour lives after the coordinate");
        assertEquals(1, run.callTimeTexReplays());

        run.free();
        MemoryUtil.memFree(source);
    }

    @Test
    void aRunTheListSetItselfIsNotRewritten() {
        ByteBuffer source = MemoryUtil.memAlloc(LegacyImmediateCapture.STRIDE_POSITION_COLOR);
        long address = MemoryUtil.memAddress(source);
        MemoryUtil.memPutByte(address + 12L, (byte) 0x40);
        CompiledLegacyRun run = CompiledLegacyRun.copy(LegacyImmediateCapture.FORMAT_POSITION_COLOR,
                LegacyImmediateCapture.STRIDE_POSITION_COLOR, 1, 7, address, 0);
        assertFalse(run.applyCallState(1.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f),
                "the list carries the command, so the baked byte is the answer");
        assertEquals((byte) 0x40, MemoryUtil.memGetByte(run.address + 12L));
        assertEquals(0, run.callTimeColourReplays());

        run.free();
        MemoryUtil.memFree(source);
    }
}
