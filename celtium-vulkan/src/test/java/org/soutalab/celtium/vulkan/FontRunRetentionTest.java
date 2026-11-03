package org.soutalab.celtium.vulkan;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The font run's sink contract: a refused run is retained and offered again, not dropped.
 * <p>
 * {@link FontBatch.RunSink} says it in one sentence - "Returning {@code false} means the run was refused ... Either way
 * the batch retains the run and offers it again at the next flush, because a refused run that is dropped is text drawn
 * by nobody" - and the backend's sink did not honour it: {@code celtium$submitFontRun} called
 * {@code submitImmediateBatch} and then returned {@code true} regardless of the result. The retention queue could
 * therefore only be entered by a <em>thrown</em> submission, which the batch path does not do on a refusal, so the
 * queue was unreachable through the shipped sink and every refused glyph run was lost in silence.
 * <p>
 * This test pins the half of the contract the batch owns, so a future sink that discards the result again is caught by
 * the build rather than by a picture: a refusing sink must leave the run in the queue with its bytes intact, and the
 * retry must hand the sink the same bytes. The backend half is the one-line return in
 * {@code VulkanTerrainGpuBackend.celtium$submitFontRun}, which no unit test can reach without a device; the leg reads
 * it through {@code CELTIUM_FONT_GLYPH_SUMMARY}'s {@code fontRunRefusals}.
 */
class FontRunRetentionTest {

    private static final int STRIDE = 24;
    private static final int FORMAT_ID = 2;
    private static final int DRAW_MODE = org.lwjgl.opengl.GL11.GL_TRIANGLE_STRIP;
    private static final int VERTICES = 4;

    /** The state view the batch reads: one main target, one texture, no stall. */
    private static class State implements FontBatch.FontRunState {
        long targetIdentity = 1L;

        @Override public boolean stalled() { return false; }
        @Override public boolean matricesDirty() { return false; }
        @Override public boolean mainTarget() { return true; }
        @Override public long targetIdentity() { return this.targetIdentity; }
        @Override public boolean texture2dEnabled() { return true; }
        @Override public int boundTextureName() { return 42; }
        @Override public int whiteTextureId() { return 7; }
        @Override public float colorRed() { return 1.0F; }
        @Override public float colorGreen() { return 1.0F; }
        @Override public float colorBlue() { return 1.0F; }
        @Override public float colorAlpha() { return 1.0F; }
        @Override public boolean alphaTestEnabled() { return false; }
        @Override public float alphaFuncReference() { return 0.0F; }
        @Override public float[] projection() { return IDENTITY; }
        @Override public float[] modelView() { return IDENTITY; }
    }

    /** The same view with the three values a test moves: the colour, the alpha function and the alpha reference. */
    private static final class MutableState extends State {
        float red = 1.0F;
        int alphaFunc;
        float alphaRef;

        @Override public float colorRed() { return this.red; }
        @Override public int alphaFuncSelector() { return this.alphaFunc; }
        @Override public float alphaFuncReference() { return this.alphaRef; }
    }

    private static final float[] IDENTITY = {
            1.0F, 0.0F, 0.0F, 0.0F,
            0.0F, 1.0F, 0.0F, 0.0F,
            0.0F, 0.0F, 1.0F, 0.0F,
            0.0F, 0.0F, 0.0F, 1.0F,
    };

    /** Records every run it is offered, with a checksum of the bytes, and refuses while {@link #refuse} is set. */
    private static final class Sink implements FontBatch.RunSink {
        final List<Long> checksums = new ArrayList<Long>();
        final List<Integer> vertexCounts = new ArrayList<Integer>();
        boolean refuse;

        @Override public boolean submit(FontBatch.Run run) {
            long sum = 0L;
            for (int i = 0; i < run.vertexCount * run.stride; i++) {
                sum = (sum * 31L) + (MemoryUtil.memGetByte(run.address + i) & 0xFF);
            }
            this.checksums.add(Long.valueOf(sum));
            this.vertexCounts.add(Integer.valueOf(run.vertexCount));
            return !this.refuse;
        }
    }

    private static long field(String summary, String key) {
        for (String part : summary.split(" ")) {
            int equals = part.indexOf('=');
            if (equals > 0 && part.substring(0, equals).equals(key)) {
                return Long.parseLong(part.substring(equals + 1));
            }
        }
        throw new AssertionError("no field " + key + " in " + summary);
    }

    /**
     * One witness's count out of {@code colourWitness=true by[colour=1 modelView=0]}. The entries sit inside the
     * brackets, so the plain {@link #field} splitter (which expects top-level {@code key=value} tokens) cannot read
     * them; a witness that never fired is absent and reads as 0.
     */
    private static long witnessCount(String summary, String name) {
        int open = summary.indexOf("by[");
        int close = summary.indexOf(']', open);
        if (open < 0 || close < 0) {
            throw new AssertionError("no witness census in " + summary);
        }
        for (String part : summary.substring(open + 3, close).split(" ")) {
            int equals = part.indexOf('=');
            if (equals > 0 && part.substring(0, equals).equals(name)) {
                return Long.parseLong(part.substring(equals + 1));
            }
        }
        return 0L;
    }

    /** Appends one quad's worth of vertices and returns whether the batch took them. */
    private static boolean append(FontBatch batch, long address) {
        return batch.celtium$fontAppend(null, STRIDE, FORMAT_ID, address, VERTICES, DRAW_MODE);
    }

    @Test
    void aRefusedRunIsRetainedAndReofferedWithTheSameBytes() {
        FontBatch batch = new FontBatch();
        State state = new State();
        Sink sink = new Sink();
        batch.celtium$setFontRunState(state);
        batch.celtium$setFontRunSink(sink);
        java.nio.ByteBuffer buffer = MemoryUtil.memAlloc(VERTICES * STRIDE);
        long address = MemoryUtil.memAddress(buffer);
        try {
            for (int i = 0; i < VERTICES * STRIDE; i++) {
                MemoryUtil.memPutByte(address + i, (byte) (i * 7 + 3));
            }
            assertTrue(append(batch, address), "the batch takes a run for the main target");
            sink.refuse = true;
            batch.celtium$flushPendingFontBatch("state-change");
            assertEquals(1, sink.checksums.size(), "the refused run reached the sink once");
            assertEquals(1L, field(batch.celtium$fontRetentionSummary(), "retainedRuns"),
                    "a refused run is retained, not dropped");
            assertEquals(1L, field(batch.celtium$fontRetentionSummary(), "retainedPendingRuns"),
                    "the retained run is still waiting for its target");
            assertTrue(field(batch.celtium$fontRetentionSummary(), "retainedPendingBytes") > 0L,
                    "the retained run owns a copy of its bytes");

            // The retry: the sink now accepts, and the bytes it receives must be the ones the refusal carried.
            sink.refuse = false;
            assertTrue(append(batch, address), "a second run opens after the first was retained");
            batch.celtium$flushPendingFontBatch("state-change");
            assertEquals(3, sink.checksums.size(), "the retained run and the new one both reached the sink");
            assertEquals(sink.checksums.get(0), sink.checksums.get(1),
                    "the retry carries the refused run's own bytes");
            assertEquals(0L, field(batch.celtium$fontRetentionSummary(), "retainedPendingRuns"),
                    "the retry drained the queue");
        } finally {
            MemoryUtil.memFree(buffer);
        }
    }

    @Test
    void anAcceptedRunIsNotRetained() {
        FontBatch batch = new FontBatch();
        Sink sink = new Sink();
        batch.celtium$setFontRunState(new State());
        batch.celtium$setFontRunSink(sink);
        java.nio.ByteBuffer buffer = MemoryUtil.memAlloc(VERTICES * STRIDE);
        long address = MemoryUtil.memAddress(buffer);
        try {
            assertTrue(append(batch, address));
            batch.celtium$flushPendingFontBatch("state-change");
            assertEquals(1, sink.checksums.size());
            assertEquals(0L, field(batch.celtium$fontRetentionSummary(), "retainedRuns"),
                    "an accepted run leaves nothing in the queue");
        } finally {
            MemoryUtil.memFree(buffer);
        }
    }

    /**
     * The boundary rule the retention exists for: a run whose target did not come back inside the frame is dropped
     * there, counted, rather than offered in a later frame at the previous frame's positions.
     */
    @Test
    void aRetainedRunIsDroppedAtTheFrameBoundary() {
        FontBatch batch = new FontBatch();
        Sink sink = new Sink();
        batch.celtium$setFontRunState(new State());
        batch.celtium$setFontRunSink(sink);
        java.nio.ByteBuffer buffer = MemoryUtil.memAlloc(VERTICES * STRIDE);
        long address = MemoryUtil.memAddress(buffer);
        try {
            assertTrue(append(batch, address));
            sink.refuse = true;
            batch.celtium$flushPendingFontBatch("state-change");
            assertEquals(1L, field(batch.celtium$fontRetentionSummary(), "retainedPendingRuns"));
            batch.celtium$dropRetainedAtFrameBoundary();
            assertEquals(0L, field(batch.celtium$fontRetentionSummary(), "retainedPendingRuns"),
                    "nothing survives the frame boundary");
            assertEquals(1L, field(batch.celtium$fontRetentionSummary(), "retainedDroppedRuns"),
                    "the loss is counted, not silent");
        } finally {
            MemoryUtil.memFree(buffer);
        }
    }

    /**
     * The witness census names the value that closed the run, and under the combine it names the colour.
     * <p>
     * The census exists because the run count alone cannot say what to fold: the world frame starts one run per string
     * segment, and the only way to merge two segments is to know which of the seventeen witnesses differed. On the
     * measured scene every witness-closed run was closed by the colour, so this pins that the counter attributes a
     * colour change to the colour and not to a neighbour.
     * <p>
     * The reference is negative here on purpose: that is the GL_COMBINE branch, the only one whose fragment stage reads
     * the push block's colour, and therefore the only one where a colour change has to close the run. The non-combine
     * case is {@link #aColourChangeMergesWhenThePushColourCannotReachThePicture}.
     */
    @Test
    void theWitnessCensusNamesTheColourForAColourChange() {
        FontBatch batch = new FontBatch();
        MutableState state = new MutableState();
        state.alphaRef = -0.5F;
        Sink sink = new Sink();
        batch.celtium$setFontRunState(state);
        batch.celtium$setFontRunSink(sink);
        java.nio.ByteBuffer buffer = MemoryUtil.memAlloc(VERTICES * STRIDE);
        long address = MemoryUtil.memAddress(buffer);
        try {
            assertTrue(append(batch, address), "the first quad opens a run");
            state.red = 0.25F;
            assertTrue(append(batch, address), "the second quad opens a new run");
            assertEquals(1L, witnessCount(batch.celtium$fontWitnessSummary(), "colour"),
                    "the colour change is what closed the first run");
            assertEquals(0L, witnessCount(batch.celtium$fontWitnessSummary(), "modelView"),
                    "the matrices did not change, so they are not blamed");
            assertEquals(2L, field(batch.celtium$fontBatchSummary(), "fontRunsStarted"),
                    "two runs, one per colour");
        } finally {
            MemoryUtil.memFree(buffer);
        }
    }

    /**
     * A colour change does <em>not</em> close the run when the push colour cannot reach the picture.
     * <p>
     * {@code immediate_tex_color.frag} multiplies the sample by the vertex colour and reads {@code u_ColorR/G/B} only
     * inside {@code if (u_AlphaRef < 0.0)}. Every text draw has a non-negative reference, so its colour is per vertex
     * and a run may span a colour change: the quads keep their own colours and the picture is the one a run per colour
     * would have produced. This is the merge the {@code -Dceltium.font.colourWitness=false} arm measured (menu runs
     * 284 -> 123), applied only where it is provably invisible.
     */
    @Test
    void aColourChangeMergesWhenThePushColourCannotReachThePicture() {
        FontBatch batch = new FontBatch();
        MutableState state = new MutableState();
        Sink sink = new Sink();
        batch.celtium$setFontRunState(state);
        batch.celtium$setFontRunSink(sink);
        java.nio.ByteBuffer buffer = MemoryUtil.memAlloc(VERTICES * STRIDE);
        long address = MemoryUtil.memAddress(buffer);
        try {
            assertTrue(append(batch, address), "the first quad opens a run");
            state.red = 0.25F;
            assertTrue(append(batch, address), "the second quad joins the same run");
            assertEquals(0L, witnessCount(batch.celtium$fontWitnessSummary(), "colour"),
                    "the colour is not a witness outside the combine");
            assertEquals(1L, field(batch.celtium$fontBatchSummary(), "fontRunsStarted"),
                    "one run holds both colours");
            batch.celtium$flushPendingFontBatch("state-change");
            assertEquals(1, sink.checksums.size(), "the merged run reached the sink once");
            // Two quads plus the strip's two degenerate separator vertices: a strip of 4N vertices draws 4N-2
            // triangles, so the second quad's vertices would otherwise connect to the first quad's last vertex.
            assertEquals(2L * VERTICES + 2L, sink.vertexCounts.get(0).longValue(),
                    "the merged run carries both quads' vertices");
        } finally {
            MemoryUtil.memFree(buffer);
        }
    }

    /**
     * The narrowing itself: under the shipped {@code combine} mode the colour is a witness only under the combine,
     * {@code all} keeps the previous behaviour, and {@code off} removes it everywhere. A run can never span a change
     * of the branch, because the alpha reference is compared by the key on its own line - so the only runs that lose
     * the colour are the ones whose submission colour the fragment stage cannot read.
     */
    @Test
    void theColourWitnessIsNeededOnlyUnderTheCombine() {
        assertFalse(FontBatch.celtium$colourWitnessNeeded("combine", 0.0F),
                "the alpha test path never reads the push colour");
        assertFalse(FontBatch.celtium$colourWitnessNeeded("combine", 0.1F),
                "a positive reference is the alpha test path too");
        assertTrue(FontBatch.celtium$colourWitnessNeeded("combine", -0.5F),
                "a negative reference selects the combine, which reads it");
        assertTrue(FontBatch.celtium$colourWitnessNeeded("all", 0.0F),
                "the A/B arm compares the colour on every run");
        assertFalse(FontBatch.celtium$colourWitnessNeeded("off", -0.5F),
                "the diagnostic control removes the witness even under the combine");
    }

    /**
     * The alpha comparison function is part of the key. It travels with the reference into the group's captured state
     * and into the push block, so a run held across a change of the function would be drawn with the function of its
     * submission for every quad - the hole the reference and the draw state were put in the key to close.
     */
    @Test
    void theAlphaFunctionIsPartOfTheRunKey() {
        FontBatch batch = new FontBatch();
        MutableState state = new MutableState();
        Sink sink = new Sink();
        batch.celtium$setFontRunState(state);
        batch.celtium$setFontRunSink(sink);
        java.nio.ByteBuffer buffer = MemoryUtil.memAlloc(VERTICES * STRIDE);
        long address = MemoryUtil.memAddress(buffer);
        try {
            assertTrue(append(batch, address));
            state.alphaFunc = 0x0203;
            assertTrue(append(batch, address));
            assertEquals(1L, witnessCount(batch.celtium$fontWitnessSummary(), "alphaFunc"),
                    "the function change closed the run");
            assertEquals(2L, field(batch.celtium$fontBatchSummary(), "fontRunsStarted"));
        } finally {
            MemoryUtil.memFree(buffer);
        }
    }

    /** A sink that throws is a refusal too: the run must not be lost because the submission failed. */
    @Test
    void aThrowingSinkAlsoRetains() {
        FontBatch batch = new FontBatch();
        batch.celtium$setFontRunState(new State());
        batch.celtium$setFontRunSink(run -> {
            throw new IllegalStateException("no device");
        });
        java.nio.ByteBuffer buffer = MemoryUtil.memAlloc(VERTICES * STRIDE);
        long address = MemoryUtil.memAddress(buffer);
        try {
            assertTrue(append(batch, address));
            batch.celtium$flushPendingFontBatch("state-change");
            assertEquals(1L, field(batch.celtium$fontRetentionSummary(), "retainedRuns"));
            assertFalse(batch.celtium$fontRetentionSummary().isEmpty());
        } finally {
            MemoryUtil.memFree(buffer);
        }
    }
}
