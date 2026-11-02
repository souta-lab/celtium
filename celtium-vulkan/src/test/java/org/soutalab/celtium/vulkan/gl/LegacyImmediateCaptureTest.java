package org.soutalab.celtium.vulkan.gl;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryUtil;

import org.soutalab.celtium.vulkan.VulkanImmediateDrawer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The packing contract of the legacy immediate capture, checked without a GPU.
 * <p>
 * The defect this class exists for: {@code glBegin}/{@code glVertex*}/{@code glEnd} under the GL-less window used to
 * be state-set no-ops, so a mod that draws its models this way - and Forge's own SplashProgress, which draws the
 * loading bar this way - lost every vertex. The capture was then written and wired but could only be observed by
 * starting a game with {@code -Dceltium.gless.legacyImmediate=true}, which is why its packing rules were never pinned:
 * a wrong stride, an uninitialised colour byte or a lost {@code w} division would all look the same from a log line.
 * <p>
 * What is checked here is the whole of what the capture can get wrong on its own: the layout it chooses, the bytes it
 * writes into that layout, and the reason it names when it refuses a run.
 */
class LegacyImmediateCaptureTest {

    /** Records what a sink was handed, and at which bytes. */
    private static final class Recorder implements LegacyImmediateCapture.Sink {
        final List<long[]> runs = new ArrayList<>();
        final List<int[]> shapes = new ArrayList<>();
        boolean refuse;

        @Override
        public boolean submit(long address, int stride, int format, int vertexCount, int drawMode) {
            if (this.refuse) {
                return false;
            }
            long[] bytes = new long[(vertexCount * stride + 7) / 8];
            for (int i = 0; i < bytes.length; i++) {
                long at = address + (long) i * 8L;
                bytes[i] = at + 8L <= address + (long) vertexCount * stride
                        ? MemoryUtil.memGetLong(at)
                        : 0L;
            }
            this.runs.add(bytes);
            this.shapes.add(new int[] {stride, format, vertexCount, drawMode});
            return true;
        }

        int[] lastShape() {
            return this.shapes.get(this.shapes.size() - 1);
        }

        long[] lastBytes() {
            return this.runs.get(this.runs.size() - 1);
        }
    }

    /** A gate that answers exactly as the renderer does. */
    private static final LegacyImmediateCapture.ModeGate GATE = VulkanImmediateDrawer::refusalReason;

    private static LegacyImmediateCapture capture() {
        return new LegacyImmediateCapture();
    }

    @Test
    void aTexturedQuadPacksPositionTextureAndColourInThatOrder() {
        LegacyImmediateCapture capture = capture();
        Recorder sink = new Recorder();
        assertTrue(capture.begin(GL11.GL_QUADS, true, false, GATE), "a quad run is collected");
        capture.texCoord(0.25f, 0.5f);
        capture.color(1.0f, 0.0f, 0.0f, 1.0f);
        capture.vertex3f(1.0f, 2.0f, 3.0f);
        capture.texCoord(0.75f, 0.5f);
        capture.vertex3f(4.0f, 5.0f, 6.0f);
        assertTrue(capture.end(sink), "the run reaches the sink");

        assertArrayEquals(new int[] {LegacyImmediateCapture.STRIDE_POSITION_TEX_COLOR,
                LegacyImmediateCapture.FORMAT_POSITION_TEX_COLOR, 2, GL11.GL_QUADS}, sink.lastShape(),
                "a textured run uses POSITION_TEX_COLOR at 24 bytes");
        long base = capture.address();
        assertEquals(1.0f, MemoryUtil.memGetFloat(base), "x");
        assertEquals(2.0f, MemoryUtil.memGetFloat(base + 4L), "y");
        assertEquals(3.0f, MemoryUtil.memGetFloat(base + 8L), "z");
        assertEquals(0.25f, MemoryUtil.memGetFloat(base + 12L), "u of the first vertex");
        assertEquals(0.5f, MemoryUtil.memGetFloat(base + 16L), "v of the first vertex");
        assertEquals((byte) 255, MemoryUtil.memGetByte(base + 20L), "red byte");
        assertEquals((byte) 0, MemoryUtil.memGetByte(base + 21L), "green byte");
        assertEquals((byte) 0, MemoryUtil.memGetByte(base + 22L), "blue byte");
        assertEquals((byte) 255, MemoryUtil.memGetByte(base + 23L), "alpha byte");
        // The coordinate is current value, not per-run state: the second vertex carries the second texCoord.
        long second = base + 24L;
        assertEquals(4.0f, MemoryUtil.memGetFloat(second), "second x");
        assertEquals(0.75f, MemoryUtil.memGetFloat(second + 12L), "second u follows the later glTexCoord2f");
        assertEquals(1, capture.submittedRuns(), "one run submitted");
        assertEquals(2, capture.submittedVertices(), "two vertices submitted");
    }

    @Test
    void anUntexturedRunUsesTheColourOnlyLayoutAndStillWritesItsColour() {
        LegacyImmediateCapture capture = capture();
        Recorder sink = new Recorder();
        assertTrue(capture.begin(GL11.GL_QUADS, false, false, GATE));
        capture.color(0.0f, 1.0f, 0.0f, 0.5f);
        capture.vertex3f(-1.0f, -2.0f, -3.0f);
        assertTrue(capture.end(sink));
        assertArrayEquals(new int[] {LegacyImmediateCapture.STRIDE_POSITION_COLOR,
                LegacyImmediateCapture.FORMAT_POSITION_COLOR, 1, GL11.GL_QUADS}, sink.lastShape(),
                "an untextured run uses POSITION_COLOR at 16 bytes");
        long base = capture.address();
        assertEquals(-1.0f, MemoryUtil.memGetFloat(base));
        assertEquals(-3.0f, MemoryUtil.memGetFloat(base + 8L));
        assertEquals((byte) 0, MemoryUtil.memGetByte(base + 12L), "red byte");
        assertEquals((byte) 255, MemoryUtil.memGetByte(base + 13L), "green byte");
        assertEquals((byte) 128, MemoryUtil.memGetByte(base + 15L), "alpha 0.5 rounds to 128");
    }

    @Test
    void theColourSaturatesBeforeItBecomesAByte() {
        LegacyImmediateCapture capture = capture();
        Recorder sink = new Recorder();
        assertTrue(capture.begin(GL11.GL_TRIANGLES, false, false, GATE));
        capture.color(2.0f, -1.0f, 0.5f, 1.0f);
        capture.vertex3f(0.0f, 0.0f, 0.0f);
        assertTrue(capture.end(sink));
        long base = capture.address();
        assertEquals((byte) 255, MemoryUtil.memGetByte(base + 12L), "a colour above one saturates");
        assertEquals((byte) 0, MemoryUtil.memGetByte(base + 13L), "a negative colour clamps to zero");
        assertEquals((byte) 128, MemoryUtil.memGetByte(base + 14L), "0.5 rounds to 128");
    }

    @Test
    void aFourComponentVertexIsProjectedByItsW() {
        LegacyImmediateCapture capture = capture();
        Recorder sink = new Recorder();
        assertTrue(capture.begin(GL11.GL_TRIANGLES, false, false, GATE));
        capture.vertex4f(2.0f, 4.0f, 6.0f, 2.0f);
        assertTrue(capture.end(sink));
        long base = capture.address();
        assertEquals(1.0f, MemoryUtil.memGetFloat(base), "x/w");
        assertEquals(2.0f, MemoryUtil.memGetFloat(base + 4L), "y/w");
        assertEquals(3.0f, MemoryUtil.memGetFloat(base + 8L), "z/w");
    }

    @Test
    void aVertexWithZeroWRefusesTheRunRatherThanDrawingAnInfinitePosition() {
        LegacyImmediateCapture capture = capture();
        Recorder sink = new Recorder();
        assertTrue(capture.begin(GL11.GL_TRIANGLES, false, false, GATE));
        capture.vertex4f(1.0f, 1.0f, 1.0f, 0.0f);
        assertFalse(capture.active(), "the run is abandoned");
        assertFalse(capture.end(sink), "and nothing is offered");
        assertTrue(sink.runs.isEmpty());
        assertEquals(1, capture.refusals(), "the loss is counted");
        assertTrue(capture.lastRefusal().contains("w=0"), "and named: " + capture.lastRefusal());
    }

    @Test
    void theTwoComponentSpellingsLeaveZAtZero() {
        LegacyImmediateCapture capture = capture();
        Recorder sink = new Recorder();
        assertTrue(capture.begin(GL11.GL_LINES, false, false, GATE));
        capture.vertex2f(1.5f, 2.5f);
        capture.vertex2d(3.5, 4.5);
        assertTrue(capture.end(sink), "lines are drawn by the renderer, so they are offered");
        long base = capture.address();
        assertEquals(1.5f, MemoryUtil.memGetFloat(base));
        assertEquals(2.5f, MemoryUtil.memGetFloat(base + 4L));
        assertEquals(0.0f, MemoryUtil.memGetFloat(base + 8L), "glVertex2f leaves z at zero");
        assertEquals(3.5f, MemoryUtil.memGetFloat(base + 16L), "glVertex2d narrows to the float layout");
        assertEquals(4.5f, MemoryUtil.memGetFloat(base + 20L));
    }

    @Test
    void everyPrimitiveTheRendererDrawsIsAccepted() {
        int[] drawable = {GL11.GL_QUADS, GL11.GL_TRIANGLES, GL11.GL_TRIANGLE_FAN, GL11.GL_TRIANGLE_STRIP,
                GL11.GL_QUAD_STRIP, GL11.GL_POLYGON, GL11.GL_LINES, GL11.GL_LINE_STRIP, GL11.GL_LINE_LOOP};
        LegacyImmediateCapture capture = capture();
        for (int mode : drawable) {
            assertTrue(capture.begin(mode, false, false, GATE), "mode " + mode + " is drawable");
            capture.vertex3f(0.0f, 0.0f, 0.0f);
            assertTrue(capture.end(new Recorder()), "mode " + mode + " reaches the sink");
        }
        assertEquals(drawable.length, capture.submittedRuns());
        assertEquals(0, capture.modeRefusals(), "the renderer's own rule is the only filter");
    }

    @Test
    void aModeTheRendererCannotDrawIsRefusedByName() {
        LegacyImmediateCapture capture = capture();
        Recorder sink = new Recorder();
        assertFalse(capture.begin(GL11.GL_POINTS, false, false, GATE), "points have no pipeline");
        assertFalse(capture.active(), "nothing is collected");
        assertTrue(capture.lastRefusal().contains("point"), "the reason names the mode family: "
                + capture.lastRefusal());
        capture.vertex3f(1.0f, 1.0f, 1.0f);
        assertFalse(capture.end(sink), "and no run is offered");
        assertEquals(1, capture.modeRefusals());
    }

    @Test
    void geometryInsideADisplayListCompileBelongsToTheList() {
        LegacyImmediateCapture capture = capture();
        Recorder sink = new Recorder();
        assertFalse(capture.begin(GL11.GL_QUADS, true, true, GATE), "a list compile owns its geometry");
        capture.vertex3f(0.0f, 0.0f, 0.0f);
        assertFalse(capture.end(sink));
        assertEquals(1, capture.listRefusals());
        assertTrue(capture.lastRefusal().contains("display list"));
    }

    @Test
    void aSecondBeginAbandonsTheFirstRunAndIsCounted() {
        LegacyImmediateCapture capture = capture();
        Recorder sink = new Recorder();
        assertTrue(capture.begin(GL11.GL_QUADS, false, false, GATE));
        capture.vertex3f(1.0f, 1.0f, 1.0f);
        assertTrue(capture.begin(GL11.GL_QUADS, false, false, GATE), "the later run is the one that survives");
        assertEquals(0, capture.vertexCount(), "the abandoned run's vertex is gone");
        assertEquals(1, capture.nestedRefusals());
        capture.vertex3f(2.0f, 2.0f, 2.0f);
        assertTrue(capture.end(sink));
        assertEquals(1, sink.lastShape()[2], "only the second run is offered");
    }

    @Test
    void anEmptyRunIsNotADraw() {
        LegacyImmediateCapture capture = capture();
        Recorder sink = new Recorder();
        assertTrue(capture.begin(GL11.GL_QUADS, true, false, GATE));
        assertFalse(capture.end(sink), "glBegin/glEnd with no vertices draws nothing");
        assertTrue(sink.runs.isEmpty());
        assertEquals(0, capture.submittedRuns());
    }

    @Test
    void aRefusedRunIsCountedAndTheNextRunStillDraws() {
        LegacyImmediateCapture capture = capture();
        Recorder refusing = new Recorder();
        refusing.refuse = true;
        assertTrue(capture.begin(GL11.GL_QUADS, false, false, GATE));
        capture.vertex3f(0.0f, 0.0f, 0.0f);
        assertFalse(capture.end(refusing));
        assertEquals(1, capture.sinkRefusals());
        Recorder accepting = new Recorder();
        assertTrue(capture.begin(GL11.GL_QUADS, false, false, GATE));
        capture.vertex3f(0.0f, 0.0f, 0.0f);
        assertTrue(capture.end(accepting), "the capture is not left wedged by a refusal");
        assertEquals(1, capture.submittedRuns());
    }

    @Test
    void theRunIsOfferedWithItsOwnMemorySizeAndNotTheCallersMode() {
        LegacyImmediateCapture capture = capture();
        Recorder sink = new Recorder();
        assertTrue(capture.begin(GL11.GL_TRIANGLE_STRIP, true, false, GATE));
        for (int i = 0; i < 6; i++) {
            capture.vertex3f(i, i + 1, i + 2);
        }
        assertTrue(capture.end(sink));
        assertEquals(GL11.GL_TRIANGLE_STRIP, sink.lastShape()[3], "the mode reaches the renderer unchanged");
        assertEquals(6, sink.lastShape()[2]);
        assertEquals(6 * 24, sink.lastBytes().length * 8, "one 24-byte vertex per vertex");
    }

    @Test
    void theSummaryNamesEveryKindOfLoss() {
        LegacyImmediateCapture capture = capture();
        assertTrue(capture.begin(GL11.GL_QUADS, false, false, GATE));
        capture.vertex3f(0.0f, 0.0f, 0.0f);
        assertTrue(capture.end(new Recorder()));
        capture.begin(GL11.GL_POINTS, false, false, GATE);
        capture.begin(GL11.GL_QUADS, false, true, GATE);
        String summary = capture.summary();
        assertTrue(summary.contains("submitted=1"), summary);
        assertTrue(summary.contains("modeRefusals=1"), summary);
        assertTrue(summary.contains("listRefusals=1"), summary);
        assertTrue(summary.startsWith("legacyImmediate "), summary);
    }

    /**
     * The integer spelling GL rounds to float.
     * <p>
     * A mod that draws a legacy surface with {@code glVertex3i}/{@code glVertex2i} used to get nothing appended: the
     * call was a no-op, so the run reached the sink with fewer vertices than the mod wrote - a partially drawn model,
     * which reads as a rendering bug rather than as a missing route. The layout is the float one; only the source
     * type differs, so the bytes at the offsets are the whole check.
     */
    @Test
    void theIntegerVertexSpellingsPackIntoTheSameLayout() {
        LegacyImmediateCapture capture = capture();
        Recorder sink = new Recorder();
        assertTrue(capture.begin(GL11.GL_QUADS, true, false, GATE));
        capture.texCoord2i(1, 0);
        capture.vertex3i(2, -3, 4);
        capture.texCoord2i(0, 1);
        // The two-component form leaves z at GL's current zero, exactly like glVertex2f.
        capture.vertex2i(-5, 6);
        assertTrue(capture.end(sink), "the run reaches the sink");

        assertArrayEquals(new int[] {LegacyImmediateCapture.STRIDE_POSITION_TEX_COLOR,
                LegacyImmediateCapture.FORMAT_POSITION_TEX_COLOR, 2, GL11.GL_QUADS}, sink.lastShape(),
                "an integer vertex lands in the float layout, not a second one");
        long base = capture.address();
        assertEquals(2.0f, MemoryUtil.memGetFloat(base), "x");
        assertEquals(-3.0f, MemoryUtil.memGetFloat(base + 4L), "y is signed, not clamped or narrowed to a byte");
        assertEquals(4.0f, MemoryUtil.memGetFloat(base + 8L), "z");
        assertEquals(1.0f, MemoryUtil.memGetFloat(base + 12L), "u from glTexCoord2i");
        assertEquals(0.0f, MemoryUtil.memGetFloat(base + 16L), "v from glTexCoord2i");
        long second = base + LegacyImmediateCapture.STRIDE_POSITION_TEX_COLOR;
        assertEquals(-5.0f, MemoryUtil.memGetFloat(second), "second x is negative and survives the narrowing");
        assertEquals(6.0f, MemoryUtil.memGetFloat(second + 4L), "second y");
        assertEquals(0.0f, MemoryUtil.memGetFloat(second + 8L), "a two-component vertex keeps z at zero");
        assertEquals(0.0f, MemoryUtil.memGetFloat(second + 12L), "the second glTexCoord2i reached the second vertex");
        assertEquals(1.0f, MemoryUtil.memGetFloat(second + 16L), "including its v");
        assertEquals(2, capture.submittedVertices(), "both integer vertices were appended");
    }

    /**
     * The integer four-component form divides by its {@code w}, like the float one, and refuses a zero {@code w} with
     * the same name. The division is GL's semantics, not a float-path detail, so a projection through
     * {@code glVertex4i} has to behave identically.
     */
    @Test
    void theIntegerFourComponentVertexDividesByItsWAndRefusesZero() {
        LegacyImmediateCapture capture = capture();
        Recorder sink = new Recorder();
        assertTrue(capture.begin(GL11.GL_TRIANGLES, false, false, GATE));
        capture.vertex4i(2, 4, 6, 2);
        assertTrue(capture.end(sink));
        long base = capture.address();
        assertEquals(1.0f, MemoryUtil.memGetFloat(base), "x/w");
        assertEquals(2.0f, MemoryUtil.memGetFloat(base + 4L), "y/w");
        assertEquals(3.0f, MemoryUtil.memGetFloat(base + 8L), "z/w");

        LegacyImmediateCapture refusing = capture();
        Recorder never = new Recorder();
        assertTrue(refusing.begin(GL11.GL_TRIANGLES, false, false, GATE));
        refusing.vertex4i(1, 1, 1, 0);
        assertFalse(refusing.active(), "the run is abandoned, as the float spelling does");
        assertFalse(refusing.end(never), "and nothing is offered");
        assertEquals(1, refusing.refusals(), "the loss is counted");
        assertTrue(refusing.lastRefusal().contains("w=0"), "and named: " + refusing.lastRefusal());
    }
}
