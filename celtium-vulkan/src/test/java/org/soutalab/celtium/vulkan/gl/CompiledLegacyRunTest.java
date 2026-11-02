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
                LegacyImmediateCapture.STRIDE_POSITION_COLOR, 4, 7 /* GL_QUADS */, address);
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
                LegacyImmediateCapture.STRIDE_POSITION_COLOR, 0, 7, 1L), "zero vertices");
        assertNull(CompiledLegacyRun.copy(LegacyImmediateCapture.FORMAT_POSITION_COLOR,
                LegacyImmediateCapture.STRIDE_POSITION_COLOR, 4, 7, 0L), "no source address");
        assertNull(CompiledLegacyRun.copy(LegacyImmediateCapture.FORMAT_POSITION_COLOR,
                0, 4, 7, 1L), "no vertex width");
    }
}
