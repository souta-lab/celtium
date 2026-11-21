package org.soutalab.celtium.vulkan;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The font atlas aggregation (QUEUE 631): a segment appended under a per-page texture that lives in the celtium
 * font atlas is keyed by the atlas and its UVs are rewritten into atlas space, so it joins the run the
 * atlas-bound segments of the same string opened instead of closing it at the texture witness.
 * <p>
 * The game side's own fast path already draws from the atlas; what reaches {@link FontBatch} under a page texture
 * is the interleaved remainder (the window path's glyphs). These tests pin the batch half of the contract: the
 * keying, the UV rewrite (including the strip separator's second vertex, which is copied out of the source), the
 * counter, and the two ways the aggregation must stay inert - an unregistered texture, and a registration whose
 * atlas is no longer the current one (a rebuild deleted that texture).
 */
class FontAtlasAggregationTest {

    private static final int STRIDE = 24;
    private static final int FORMAT_ID = 2;
    private static final int DRAW_MODE = org.lwjgl.opengl.GL11.GL_TRIANGLE_STRIP;
    private static final int VERTICES = 4;

    private static final int PAGE_A = 7;
    private static final int PAGE_B = 8;
    private static final int ATLAS = 47;

    /** Page A's rectangle in the atlas, and page B's: same atlas, different cell. */
    private static final FontAtlasRemap REMAP_A = new FontAtlasRemap(ATLAS, 0.25F, 0.5F, 0.5F, 0.25F);
    private static final FontAtlasRemap REMAP_B = new FontAtlasRemap(ATLAS, 0.25F, 0.75F, 0.5F, 0.25F);

    private static final float[] IDENTITY = {
            1.0F, 0.0F, 0.0F, 0.0F,
            0.0F, 1.0F, 0.0F, 0.0F,
            0.0F, 0.0F, 1.0F, 0.0F,
            0.0F, 0.0F, 0.0F, 1.0F,
    };

    /** The state view: one target, a settable bound texture, and the registry as the remap source. */
    private static final class State implements FontBatch.FontRunState {
        int texture = PAGE_A;

        @Override public boolean stalled() { return false; }
        @Override public boolean matricesDirty() { return false; }
        @Override public boolean mainTarget() { return true; }
        @Override public long targetIdentity() { return 1L; }
        @Override public boolean texture2dEnabled() { return true; }
        @Override public int boundTextureName() { return this.texture; }
        @Override public int whiteTextureId() { return 3; }
        @Override public float colorRed() { return 1.0F; }
        @Override public float colorGreen() { return 1.0F; }
        @Override public float colorBlue() { return 1.0F; }
        @Override public float colorAlpha() { return 1.0F; }
        @Override public boolean alphaTestEnabled() { return false; }
        @Override public float alphaFuncReference() { return 0.0F; }
        @Override public float[] projection() { return IDENTITY; }
        @Override public float[] modelView() { return IDENTITY; }
        @Override public FontAtlasRemap atlasRemap(int texture) { return FontAtlasRegistry.remapFor(texture); }
    }

    /** Records each run's texture and a copy of its UV pairs, read while the run's address is valid. */
    private static final class Sink implements FontBatch.RunSink {
        final List<Integer> textures = new ArrayList<Integer>();
        final List<float[]> uvs = new ArrayList<float[]>();

        @Override public boolean submit(FontBatch.Run run) {
            this.textures.add(Integer.valueOf(run.texture));
            float[] copy = new float[run.vertexCount * 2];
            for (int i = 0; i < run.vertexCount; i++) {
                long uv = run.address + (long) i * run.stride + 12L;
                copy[i * 2] = MemoryUtil.memGetFloat(uv);
                copy[i * 2 + 1] = MemoryUtil.memGetFloat(uv + 4L);
            }
            this.uvs.add(copy);
            return true;
        }
    }

    @AfterEach
    void clearRegistry() {
        FontAtlasRegistry.invalidate();
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

    /** One segment's vertices: vertex {@code i} carries U {@code 0.10 + i * 0.01}, V {@code 0.20 + i * 0.01}. */
    private static java.nio.ByteBuffer segment() {
        java.nio.ByteBuffer buffer = MemoryUtil.memAlloc(VERTICES * STRIDE);
        long address = MemoryUtil.memAddress(buffer);
        for (int i = 0; i < VERTICES; i++) {
            MemoryUtil.memPutFloat(address + (long) i * STRIDE + 12L, 0.10F + i * 0.01F);
            MemoryUtil.memPutFloat(address + (long) i * STRIDE + 16L, 0.20F + i * 0.01F);
        }
        return buffer;
    }

    private static boolean append(FontBatch batch, java.nio.ByteBuffer segment) {
        return batch.celtium$fontAppend(null, STRIDE, FORMAT_ID, MemoryUtil.memAddress(segment), VERTICES,
                DRAW_MODE);
    }

    @Test
    void aPageSegmentIsKeyedByTheAtlasWithItsUvsRewritten() {
        FontAtlasRegistry.setCurrentAtlas(ATLAS);
        FontAtlasRegistry.register(PAGE_A, REMAP_A);
        FontBatch batch = new FontBatch();
        Sink sink = new Sink();
        batch.celtium$setFontRunState(new State());
        batch.celtium$setFontRunSink(sink);
        java.nio.ByteBuffer segment = segment();
        try {
            assertTrue(append(batch, segment), "the batch takes the segment");
            batch.celtium$flushPendingFontBatch("state-change");
            assertEquals(1, sink.textures.size(), "one run reached the sink");
            assertEquals(ATLAS, sink.textures.get(0).intValue(), "the run is keyed by the atlas, not the page");
            float[] uvs = sink.uvs.get(0);
            assertEquals(REMAP_A.remapU(0.10F), uvs[0], 1.0E-6F, "the first vertex's U is in atlas space");
            assertEquals(REMAP_A.remapV(0.23F), uvs[7], 1.0E-6F, "the last vertex's V is in atlas space");
            assertEquals(1L, batch.fontAtlasAggregatedSegments(), "the rewrite is counted");
        } finally {
            MemoryUtil.memFree(segment);
        }
    }

    @Test
    void twoPagesAndTheAtlasItselfShareOneRun() {
        FontAtlasRegistry.setCurrentAtlas(ATLAS);
        FontAtlasRegistry.register(PAGE_A, REMAP_A);
        FontAtlasRegistry.register(PAGE_B, REMAP_B);
        FontBatch batch = new FontBatch();
        State state = new State();
        Sink sink = new Sink();
        batch.celtium$setFontRunState(state);
        batch.celtium$setFontRunSink(sink);
        java.nio.ByteBuffer segment = segment();
        try {
            state.texture = PAGE_A;
            assertTrue(append(batch, segment));
            state.texture = PAGE_B;
            assertTrue(append(batch, segment), "the second page joins instead of closing the run");
            state.texture = ATLAS;
            assertTrue(append(batch, segment), "an atlas-bound segment joins the same run");
            assertEquals(1L, field(batch.celtium$fontBatchSummary(), "fontRunsStarted"),
                    "three segments, one run");
            batch.celtium$flushPendingFontBatch("state-change");
            assertEquals(1, sink.textures.size(), "the sink saw the single run");
            float[] uvs = sink.uvs.get(0);
            // 4 + (2 separator + 4) + (2 separator + 4) vertices; index 5 is the first separator's second
            // vertex, a copy of page B's first vertex out of the source, so it must carry B's remap too.
            assertEquals(16, uvs.length / 2, "the run holds all three segments and both separators");
            assertEquals(REMAP_B.remapU(0.10F), uvs[5 * 2], 1.0E-6F,
                    "the separator's copied vertex is rewritten with its own segment");
            assertEquals(0.10F, uvs[12 * 2], 1.0E-6F,
                    "the atlas-bound segment keeps its UVs - it was already in atlas space");
            assertEquals(2L, batch.fontAtlasAggregatedSegments(), "only the two page segments were rewritten");
        } finally {
            MemoryUtil.memFree(segment);
        }
    }

    @Test
    void anUnregisteredTextureKeepsThePerTextureKeying() {
        FontBatch batch = new FontBatch();
        State state = new State();
        Sink sink = new Sink();
        batch.celtium$setFontRunState(state);
        batch.celtium$setFontRunSink(sink);
        java.nio.ByteBuffer segment = segment();
        try {
            state.texture = PAGE_A;
            assertTrue(append(batch, segment));
            state.texture = PAGE_B;
            assertTrue(append(batch, segment));
            assertEquals(2L, field(batch.celtium$fontBatchSummary(), "fontRunsStarted"),
                    "with no registration the texture witness still closes the run, as before");
            batch.celtium$flushPendingFontBatch("state-change");
            assertEquals(0.10F, sink.uvs.get(0)[0], 1.0E-6F, "the UVs are untouched");
            assertEquals(0L, batch.fontAtlasAggregatedSegments(), "nothing was rewritten");
        } finally {
            MemoryUtil.memFree(segment);
        }
    }

    @Test
    void aRegistrationForADeadAtlasIsInert() {
        FontAtlasRegistry.setCurrentAtlas(ATLAS);
        FontAtlasRegistry.register(PAGE_A, REMAP_A);
        // A rebuild deleted texture 47 and published 99; the page entry still names 47 and must not fire.
        FontAtlasRegistry.setCurrentAtlas(99);
        FontBatch batch = new FontBatch();
        State state = new State();
        Sink sink = new Sink();
        batch.celtium$setFontRunState(state);
        batch.celtium$setFontRunSink(sink);
        java.nio.ByteBuffer segment = segment();
        try {
            assertTrue(append(batch, segment));
            batch.celtium$flushPendingFontBatch("state-change");
            assertEquals(PAGE_A, sink.textures.get(0).intValue(),
                    "the run keys by the page while the registration's atlas is not current");
            assertEquals(0L, batch.fontAtlasAggregatedSegments(), "nothing was rewritten");
        } finally {
            MemoryUtil.memFree(segment);
        }
    }
}
