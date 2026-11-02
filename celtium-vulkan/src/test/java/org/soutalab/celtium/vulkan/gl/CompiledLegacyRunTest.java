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

    /**
     * The layout is chosen at {@code glCallList}, not at {@code glNewList}: GL reads the texturing state when the vertex
     * is executed, so a textured-compiled list drawn with texturing off submits the untextured layout and the colour it
     * stored. Getting this wrong is not a missing draw - it samples the caller's texture - which is why the conversion's
     * colour offset is what the leg measures.
     */
    @Test
    void aTexturedRunDrawnUntexturedIsRepackedWithTheStoredColour() {
        ByteBuffer source = MemoryUtil.memAlloc(2 * LegacyImmediateCapture.STRIDE_POSITION_TEX_COLOR);
        long address = MemoryUtil.memAddress(source);
        for (int vertex = 0; vertex < 2; vertex++) {
            long at = address + (long) vertex * LegacyImmediateCapture.STRIDE_POSITION_TEX_COLOR;
            MemoryUtil.memPutFloat(at, 10.0f + vertex);
            MemoryUtil.memPutFloat(at + 4L, 20.0f);
            MemoryUtil.memPutFloat(at + 8L, 0.0f);
            MemoryUtil.memPutFloat(at + 12L, 0.125f);
            MemoryUtil.memPutFloat(at + 16L, 0.875f);
            MemoryUtil.memPutByte(at + 20L, (byte) 0x11);
            MemoryUtil.memPutByte(at + 21L, (byte) 0x22);
            MemoryUtil.memPutByte(at + 22L, (byte) 0x33);
            MemoryUtil.memPutByte(at + 23L, (byte) 0x44);
        }
        CompiledLegacyRun run = CompiledLegacyRun.copy(LegacyImmediateCapture.FORMAT_POSITION_TEX_COLOR,
                LegacyImmediateCapture.STRIDE_POSITION_TEX_COLOR, 2, 7, address,
                CompiledLegacyRun.ATTRIBUTE_COLOUR_FROM_CALL);
        CompiledLegacyRun.Submission submission = run.prepareCall(true, false, 1.0f, 1.0f, 1.0f, 1.0f, 0.5f, 0.5f);
        assertEquals(LegacyImmediateCapture.FORMAT_POSITION_COLOR, submission.format,
                "a list called with GL_TEXTURE_2D off submits the untextured layout");
        assertEquals(LegacyImmediateCapture.STRIDE_POSITION_COLOR, submission.stride);
        assertNotEquals(run.address, submission.address, "the converted vertices are their own buffer");
        for (int vertex = 0; vertex < 2; vertex++) {
            long at = submission.address + (long) vertex * LegacyImmediateCapture.STRIDE_POSITION_COLOR;
            assertEquals(10.0f + vertex, MemoryUtil.memGetFloat(at), "positions are copied (x of vertex " + vertex + ")");
            assertEquals(20.0f, MemoryUtil.memGetFloat(at + 4L));
            assertEquals((byte) 255, MemoryUtil.memGetByte(at + 12L), "the call-time white is the colour");
            assertEquals((byte) 255, MemoryUtil.memGetByte(at + 15L));
        }
        assertEquals(1, run.layoutRepacks());
        assertEquals(1, run.callTimeColourReplays(), "the colour was still left to the call");
        // The copy the list owns is untouched, so a call with texturing on still submits the compiled layout.
        assertEquals((byte) 0x11, MemoryUtil.memGetByte(run.address + 20L));
        CompiledLegacyRun.Submission textured = run.prepareCall(true, true, 1.0f, 1.0f, 1.0f, 1.0f, 0.5f, 0.5f);
        assertEquals(LegacyImmediateCapture.FORMAT_POSITION_TEX_COLOR, textured.format);
        assertEquals(run.address, textured.address);
        assertEquals(1, run.layoutRepacks(), "a matching layout needs no repack");

        run.free();
        MemoryUtil.memFree(source);
    }

    /**
     * The other direction: an untextured-compiled list drawn with texturing on samples the caller's texture at the
     * caller's coordinate. The layout cannot hold a coordinate, so the call-time one is the only possible answer, and
     * the stored colour has to move from offset 12 to the textured layout's offset 20.
     */
    @Test
    void anUntexturedRunDrawnTexturedSamplesTheCallTimeCoordinate() {
        ByteBuffer source = MemoryUtil.memAlloc(LegacyImmediateCapture.STRIDE_POSITION_COLOR);
        long address = MemoryUtil.memAddress(source);
        MemoryUtil.memPutFloat(address, 5.0f);
        MemoryUtil.memPutFloat(address + 4L, 6.0f);
        MemoryUtil.memPutFloat(address + 8L, 7.0f);
        MemoryUtil.memPutByte(address + 12L, (byte) 0x7E);
        MemoryUtil.memPutByte(address + 13L, (byte) 0x7F);
        MemoryUtil.memPutByte(address + 14L, (byte) 0x80);
        MemoryUtil.memPutByte(address + 15L, (byte) 0x81);
        CompiledLegacyRun run = CompiledLegacyRun.copy(LegacyImmediateCapture.FORMAT_POSITION_COLOR,
                LegacyImmediateCapture.STRIDE_POSITION_COLOR, 1, 7, address, 0);
        CompiledLegacyRun.Submission submission = run.prepareCall(true, true, 0.0f, 0.0f, 0.0f, 0.0f, 0.25f, 0.75f);
        assertEquals(LegacyImmediateCapture.FORMAT_POSITION_TEX_COLOR, submission.format,
                "a list called with GL_TEXTURE_2D on submits the textured layout");
        assertEquals(5.0f, MemoryUtil.memGetFloat(submission.address));
        assertEquals(0.25f, MemoryUtil.memGetFloat(submission.address + 12L), "the call-time s is the coordinate");
        assertEquals(0.75f, MemoryUtil.memGetFloat(submission.address + 16L));
        assertEquals((byte) 0x7E, MemoryUtil.memGetByte(submission.address + 20L),
                "the list's own colour moves to the textured layout's offset");
        assertEquals((byte) 0x81, MemoryUtil.memGetByte(submission.address + 23L));
        assertEquals(1, run.layoutRepacks());
        assertEquals(1, run.callTimeTexReplays());

        run.free();
        MemoryUtil.memFree(source);
    }

    @Test
    void aRunWithNoRepackKeepsItsBakedColourWhenTheCallStateSwitchIsOff() {
        ByteBuffer source = MemoryUtil.memAlloc(LegacyImmediateCapture.STRIDE_POSITION_TEX_COLOR);
        long address = MemoryUtil.memAddress(source);
        MemoryUtil.memPutByte(address + 20L, (byte) 0x40);
        CompiledLegacyRun run = CompiledLegacyRun.copy(LegacyImmediateCapture.FORMAT_POSITION_TEX_COLOR,
                LegacyImmediateCapture.STRIDE_POSITION_TEX_COLOR, 1, 7, address,
                CompiledLegacyRun.ATTRIBUTE_COLOUR_FROM_CALL);
        CompiledLegacyRun.Submission submission = run.prepareCall(false, false, 1.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f);
        assertEquals(LegacyImmediateCapture.FORMAT_POSITION_COLOR, submission.format, "the layout still follows the call");
        assertEquals((byte) 0x40, MemoryUtil.memGetByte(submission.address + 12L),
                "with the call-state switch off the compiled colour is kept");
        assertEquals(0, run.callTimeColourReplays());

        run.free();
        MemoryUtil.memFree(source);
    }
}
