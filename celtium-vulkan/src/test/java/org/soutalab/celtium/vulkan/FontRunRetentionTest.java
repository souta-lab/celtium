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
    private static final class State implements FontBatch.FontRunState {
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

    private static final float[] IDENTITY = {
            1.0F, 0.0F, 0.0F, 0.0F,
            0.0F, 1.0F, 0.0F, 0.0F,
            0.0F, 0.0F, 1.0F, 0.0F,
            0.0F, 0.0F, 0.0F, 1.0F,
    };

    /** Records every run it is offered, with a checksum of the bytes, and refuses while {@link #refuse} is set. */
    private static final class Sink implements FontBatch.RunSink {
        final List<Long> checksums = new ArrayList<Long>();
        boolean refuse;

        @Override public boolean submit(FontBatch.Run run) {
            long sum = 0L;
            for (int i = 0; i < run.vertexCount * run.stride; i++) {
                sum = (sum * 31L) + (MemoryUtil.memGetByte(run.address + i) & 0xFF);
            }
            this.checksums.add(Long.valueOf(sum));
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
