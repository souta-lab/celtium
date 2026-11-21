package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The parts of the font batch's CPU contract that only the standalone check pinned (QUEUE 640).
 * <p>
 * {@code scripts/cpu-contract/FontBatchContractCheck.java} guarded the whole batch, but most of it has since
 * grown JUnit cover of its own - retention and the refused-run retry in {@link FontRunRetentionTest}, the
 * retained-offer order in {@link FontBatchRetainedOrderTest}, the flush placement in
 * {@link FontBatchFlushOrderTest}, the atlas aggregation in {@link FontAtlasAggregationTest}. What remained
 * check-only, and lives here now, is:
 * <ul>
 *   <li>the verification summary's field names and their order - that string is quoted by past acceptances and
 *   compared byte for byte, so a new counter may not appear in it;</li>
 *   <li>the run key's target witness - a run cannot span a switch between render targets, and each submitted run
 *   names the target it was appended under;</li>
 *   <li>the draw-state witness - a run held across a change of the state the owner's batch path reads at submit
 *   time (depth test/write, blend, cull, colour mask, scissor) is closed, not drawn under the state of its
 *   submission.</li>
 * </ul>
 * The standalone check retired when this landed; its recipe had rotted (it named a single-file compile against a
 * FontBatch that has since grown {@code FontAtlasRemap}/{@code VulkanCullState} dependencies and an LWJGL split
 * it did not know about).
 */
class FontBatchContractTest {

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

    /** The state view the batch reads, with the values the witness tests move. */
    private static final class State implements FontBatch.FontRunState {
        long targetIdentity = 1L;
        boolean depthTest = true;
        boolean depthWrite = true;
        boolean blend = true;
        boolean cull;
        int colorMask = 0xF;
        long scissorKey;

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
        @Override public boolean depthTestEnabled() { return this.depthTest; }
        @Override public boolean depthWriteEnabled() { return this.depthWrite; }
        @Override public boolean blendEnabled() { return this.blend; }
        @Override public boolean cullEnabled() { return this.cull; }
        @Override public int colorMask() { return this.colorMask; }
        @Override public long scissorKey() { return this.scissorKey; }
    }

    /** Records the target each offered run names, in offer order. */
    private static final class Sink implements FontBatch.RunSink {
        final List<Long> targets = new ArrayList<>();

        @Override public boolean submit(FontBatch.Run run) {
            this.targets.add(Long.valueOf(run.targetIdentity));
            return true;
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

    private static boolean append(FontBatch batch, long address) {
        return batch.celtium$fontAppend(null, STRIDE, FORMAT_ID, address, VERTICES, DRAW_MODE);
    }

    /** Fills {@code buffer} with a byte pattern and returns its native address. */
    private static long filledAddress(java.nio.ByteBuffer buffer) {
        long address = MemoryUtil.memAddress(buffer);
        for (int i = 0; i < VERTICES * STRIDE; i++) {
            MemoryUtil.memPutByte(address + i, (byte) (i * 7 + 3));
        }
        return address;
    }

    @Test
    void theSummaryFieldNamesAndOrderArePinned() {
        String summary = new FontBatch().celtium$fontBatchSummary();
        String[] tokens = summary.split(" ");
        String[] keys = new String[tokens.length];
        for (int i = 0; i < tokens.length; i++) {
            keys[i] = tokens[i].substring(0, tokens[i].indexOf('='));
        }
        assertEquals("[fontRunsStarted, fontQuadsAppended, fontStripSeparators, flushedIntervening, flushedBoundary,"
                        + " flushedStateChange, appendMillis]",
                Arrays.toString(keys), "summary keys are unchanged and in order");
        assertFalse(summary.contains("retained"), "retention counters stay out of that string");
        assertFalse(summary.contains("fontDrawStateFlushes"), "the draw-state counter stays out of that string");
    }

    @Test
    void aRunDoesNotSpanATargetSwitch() {
        FontBatch batch = new FontBatch();
        State state = new State();
        Sink sink = new Sink();
        batch.celtium$setFontRunState(state);
        batch.celtium$setFontRunSink(sink);
        java.nio.ByteBuffer buffer = MemoryUtil.memAlloc(VERTICES * STRIDE);
        try {
            long address = filledAddress(buffer);
            assertTrue(append(batch, address), "the append for target 1 is taken");
            state.targetIdentity = 2L;
            assertTrue(append(batch, address), "the append for target 2 is taken");
            assertEquals(2L, field(batch.celtium$fontBatchSummary(), "fontRunsStarted"),
                    "the target switch closed the target-1 run and started a new one");
            batch.celtium$flushPendingFontBatch("boundary");
            assertEquals(Arrays.asList(1L, 2L), sink.targets,
                    "each run goes out separately and names the target it was appended under");
        } finally {
            MemoryUtil.memFree(buffer);
        }
    }

    /**
     * Appends one quad, changes exactly one draw-state field, appends another, and requires the change to have
     * closed the first run: two runs started, and the draw-state census counted the close. A run still open across
     * the change would be submitted under the state of its second quad - the defect this witness exists for.
     */
    private static void assertDrawStateChangeClosesTheRun(String field, java.util.function.Consumer<State> change) {
        FontBatch batch = new FontBatch();
        State state = new State();
        Sink sink = new Sink();
        batch.celtium$setFontRunState(state);
        batch.celtium$setFontRunSink(sink);
        java.nio.ByteBuffer buffer = MemoryUtil.memAlloc(VERTICES * STRIDE);
        try {
            long address = filledAddress(buffer);
            assertTrue(append(batch, address), field + ": the first append is taken");
            change.accept(state);
            assertTrue(append(batch, address), field + ": the second append is taken");
            assertEquals(2L, field(batch.celtium$fontBatchSummary(), "fontRunsStarted"),
                    field + ": the change closed the first run");
            assertEquals(1L, field(batch.celtium$fontStateSummary(), "fontDrawStateFlushes"),
                    field + ": the close is attributed to the draw state");
        } finally {
            MemoryUtil.memFree(buffer);
        }
    }

    @Test
    void aDepthTestChangeClosesTheRun() {
        assertDrawStateChangeClosesTheRun("depthTest", state -> state.depthTest = false);
    }

    @Test
    void aDepthWriteChangeClosesTheRun() {
        assertDrawStateChangeClosesTheRun("depthWrite", state -> state.depthWrite = false);
    }

    @Test
    void aBlendChangeClosesTheRun() {
        assertDrawStateChangeClosesTheRun("blend", state -> state.blend = false);
    }

    @Test
    void aCullChangeClosesTheRun() {
        assertDrawStateChangeClosesTheRun("cull", state -> state.cull = true);
    }

    @Test
    void aColourMaskChangeClosesTheRun() {
        assertDrawStateChangeClosesTheRun("colorMask", state -> state.colorMask = 0x7);
    }

    @Test
    void aScissorChangeClosesTheRun() {
        assertDrawStateChangeClosesTheRun("scissor", state -> state.scissorKey = 0x0001000200030004L);
    }
}
