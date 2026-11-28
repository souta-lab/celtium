package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Behavioral and VERIFY-wiring tests for DP-11: FontBatch retention drop breakdown
 * (boundary drops vs. cap-exceeded drops) and re-offer success counters.
 */
class FontRetentionBreakdownTest {

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

    private static final class State implements FontBatch.FontRunState {
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
        @Override public float alphaFuncReference() { return 0.0F; }
        @Override public float[] projection() { return IDENTITY; }
        @Override public float[] modelView() { return IDENTITY; }
    }

    private static final class Sink implements FontBatch.RunSink {
        boolean refuse;

        @Override public boolean submit(FontBatch.Run run) {
            return !this.refuse;
        }
    }

    @Test
    void reofferedRunIncrementsReofferedCountersAndVerifySummary() {
        FontBatch batch = new FontBatch();
        Sink sink = new Sink();
        batch.celtium$setFontRunState(new State());
        batch.celtium$setFontRunSink(sink);
        ByteBuffer buffer = MemoryUtil.memAlloc(VERTICES * STRIDE);
        long address = MemoryUtil.memAddress(buffer);
        try {
            assertTrue(batch.celtium$fontAppend(null, STRIDE, FORMAT_ID, address, VERTICES, DRAW_MODE));
            sink.refuse = true;
            batch.celtium$flushPendingFontBatch("state-change");
            assertEquals(0L, batch.retainedReofferedRuns());
            assertEquals(0L, batch.retainedReofferedQuads());

            sink.refuse = false;
            batch.celtium$flushPendingFontBatch("boundary");
            assertEquals(1L, batch.retainedReofferedRuns(), "accepted re-offer increments retainedReofferedRuns");
            assertEquals(1L, batch.retainedReofferedQuads(), "accepted re-offer increments retainedReofferedQuads");
            String verify = batch.celtium$fontRetentionVerifySummary();
            assertTrue(verify.contains("fontRetainedReofferedRuns=1"));
            assertTrue(verify.contains("fontRetainedReofferedQuads=1"));
        } finally {
            batch.close();
            MemoryUtil.memFree(buffer);
        }
    }

    @Test
    void boundaryDropAndCapOverflowAreSeparated() {
        FontBatch batch = new FontBatch();
        Sink sink = new Sink();
        sink.refuse = true;
        batch.celtium$setFontRunState(new State());
        batch.celtium$setFontRunSink(sink);
        ByteBuffer buffer = MemoryUtil.memAlloc(VERTICES * STRIDE);
        long address = MemoryUtil.memAddress(buffer);
        try {
            // Retain 64 runs (fills MAX_RETAINED_RUNS) + 2 overflow runs.
            for (int i = 0; i < 66; i++) {
                assertTrue(batch.celtium$fontAppend(null, STRIDE, FORMAT_ID, address, VERTICES, DRAW_MODE));
                batch.celtium$flushPendingFontBatch("state-change");
            }
            assertEquals(0L, batch.retainedBoundaryDroppedRuns(),
                    "cap-exceeded drops do not increment boundary-drop counter");
            assertTrue(batch.celtium$fontRetentionVerifySummary().contains("fontRetainedDroppedRuns=2"),
                    "two overflow runs are counted in total dropped runs");

            batch.celtium$dropRetainedAtFrameBoundary();
            assertEquals(64L, batch.retainedBoundaryDroppedRuns(),
                    "all 64 queued runs are counted as boundary-dropped");
            assertEquals(64L, batch.retainedBoundaryDroppedQuads());
            String verify = batch.celtium$fontRetentionVerifySummary();
            assertTrue(verify.contains("fontRetainedDroppedRuns=66"),
                    "total dropped runs equals cap overflow (2) + boundary drops (64)");
            assertTrue(verify.contains("fontRetainedBoundaryDroppedRuns=64"));
        } finally {
            batch.close();
            MemoryUtil.memFree(buffer);
        }
    }

    @Test
    void verifyLineExposesFontRetentionSummaryBesideFontStatsRun0() throws IOException {
        Path direct = Paths.get("celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java");
        Path path = Files.exists(direct) ? direct
                : Paths.get("../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java");
        String diag = Files.readString(path).replace("\r\n", "\n");
        int anchor = diag.indexOf("+ funnel.fontStats.run0()");
        assertTrue(anchor >= 0, "fontStats.run0() anchor is located");
        String window = diag.substring(anchor, Math.min(diag.length(), anchor + 200));
        assertTrue(window.contains("+ \" \" + funnel.fontStats.retentionSummary()"),
                "VERIFY exposes fontStats.retentionSummary() next to fontStats.run0()");
    }
}
