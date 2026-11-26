package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The stage-8 shrink's binding contract (S8-d): a run's state identity is one {@link PipelineStateKey} packed
 * from the owner's view, not the seventeen hand-kept witness fields the batch compared before. What the key
 * carries closes the run when it changes; what it has no slot for - the alpha reference and function - is a raw
 * residual comparison beside it, and the granularity is the old witness set's exactly: a change the view never
 * witnessed (blend factors, the depth function) cannot be told apart by either form.
 */
class FontBatchShimKeyTest {

    private static final int STRIDE = 24;
    private static final int FORMAT_ID = 2;
    private static final int DRAW_MODE = org.lwjgl.opengl.GL11.GL_TRIANGLE_STRIP;
    private static final int VERTICES = 4;

    private static final float[] IDENTITY = {
            1.0F, 0.0F, 0.0F, 0.0F,
            0.0F, 1.0F, 0.0F, 0.0F,
            0.0F, 0.0F, 1.0F, 0.0F,
            0.0F, 0.0F, 0.0F, 1.0F,
    };

    /** The state view the batch reads, with the alpha pair and blend the key tests move. */
    private static final class State implements FontBatch.FontRunState {
        boolean blend = true;
        float alphaRef;
        int alphaFunc = 519;

        @Override public boolean stalled() { return false; }
        @Override public boolean matricesDirty() { return false; }
        @Override public boolean mainTarget() { return true; }
        @Override public long targetIdentity() { return 1L; }
        @Override public boolean texture2dEnabled() { return true; }
        @Override public int boundTextureName() { return 42; }
        @Override public int whiteTextureId() { return 7; }
        @Override public float colorRed() { return 1.0F; }
        @Override public float colorGreen() { return 1.0F; }
        @Override public float colorBlue() { return 1.0F; }
        @Override public float colorAlpha() { return 1.0F; }
        @Override public boolean alphaTestEnabled() { return false; }
        @Override public float alphaFuncReference() { return this.alphaRef; }
        @Override public int alphaFuncSelector() { return this.alphaFunc; }
        @Override public float[] projection() { return IDENTITY; }
        @Override public float[] modelView() { return IDENTITY; }
        @Override public boolean depthTestEnabled() { return true; }
        @Override public boolean depthWriteEnabled() { return true; }
        @Override public boolean blendEnabled() { return this.blend; }
        @Override public int cullState() { return 0; }
        @Override public int colorMask() { return 0xF; }
        @Override public long scissorKey() { return 0L; }
    }

    /** Records every offered run's vertex count, in offer order. */
    private static final class Sink implements FontBatch.RunSink {
        final List<Integer> vertexCounts = new ArrayList<>();

        @Override public boolean submit(FontBatch.Run run) {
            this.vertexCounts.add(Integer.valueOf(run.vertexCount));
            return true;
        }
    }

    private static boolean append(FontBatch batch, long address) {
        return batch.celtium$fontAppend(null, STRIDE, FORMAT_ID, address, VERTICES, DRAW_MODE);
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

    @Test
    void segmentsUnderOneKeyCoalesceIntoOneRun() {
        FontBatch batch = new FontBatch();
        State state = new State();
        Sink sink = new Sink();
        batch.celtium$setFontRunState(state);
        batch.celtium$setFontRunSink(sink);
        java.nio.ByteBuffer buffer = MemoryUtil.memAlloc(VERTICES * STRIDE);
        try {
            long address = MemoryUtil.memAddress(buffer);
            assertTrue(append(batch, address), "the first segment is taken");
            assertTrue(append(batch, address), "the second segment joins the same run");
            assertEquals(1L, field(batch.celtium$fontBatchSummary(), "fontRunsStarted"),
                    "one key, one run");
            batch.celtium$flushPendingFontBatch("boundary");
            // The strip separator pair between the two quads is part of the run the sink is offered.
            assertEquals(java.util.Arrays.asList(Integer.valueOf(VERTICES + 2 + VERTICES)), sink.vertexCounts,
                    "the coalesced run goes out once, separators included");
        } finally {
            MemoryUtil.memFree(buffer);
        }
    }

    @Test
    void anAlphaFunctionChangeWithTheTestDisabledClosesTheRun() {
        FontBatch batch = new FontBatch();
        State state = new State();
        Sink sink = new Sink();
        batch.celtium$setFontRunState(state);
        batch.celtium$setFontRunSink(sink);
        java.nio.ByteBuffer buffer = MemoryUtil.memAlloc(VERTICES * STRIDE);
        try {
            long address = MemoryUtil.memAddress(buffer);
            assertTrue(append(batch, address), "the first segment is taken");
            // The key erases the function while the test is disabled; the residual comparison is what closes
            // the run here, exactly as the hand-kept witness did.
            state.alphaFunc = 516;
            assertTrue(append(batch, address), "the second segment is taken");
            assertEquals(2L, field(batch.celtium$fontBatchSummary(), "fontRunsStarted"),
                    "the function change closed the first run even with the test disabled");
        } finally {
            MemoryUtil.memFree(buffer);
        }
    }

    @Test
    void anAlphaReferenceChangeClosesTheRun() {
        FontBatch batch = new FontBatch();
        State state = new State();
        Sink sink = new Sink();
        batch.celtium$setFontRunState(state);
        batch.celtium$setFontRunSink(sink);
        java.nio.ByteBuffer buffer = MemoryUtil.memAlloc(VERTICES * STRIDE);
        try {
            long address = MemoryUtil.memAddress(buffer);
            assertTrue(append(batch, address), "the first segment is taken");
            state.alphaRef = 0.5F;
            assertTrue(append(batch, address), "the second segment is taken");
            assertEquals(2L, field(batch.celtium$fontBatchSummary(), "fontRunsStarted"),
                    "the reference change closed the first run");
        } finally {
            MemoryUtil.memFree(buffer);
        }
    }

    @Test
    void aKeyChangeIsCountedUnderTheKeySlotOfTheWitnessSummary() {
        FontBatch batch = new FontBatch();
        State state = new State();
        Sink sink = new Sink();
        batch.celtium$setFontRunState(state);
        batch.celtium$setFontRunSink(sink);
        java.nio.ByteBuffer buffer = MemoryUtil.memAlloc(VERTICES * STRIDE);
        try {
            long address = MemoryUtil.memAddress(buffer);
            assertTrue(append(batch, address), "the first segment is taken");
            state.blend = false;
            assertTrue(append(batch, address), "the second segment is taken");
            assertTrue(batch.celtium$fontWitnessSummary().contains("key=1"),
                    "the blend change is a key change in the census: " + batch.celtium$fontWitnessSummary());
        } finally {
            MemoryUtil.memFree(buffer);
        }
    }
}
