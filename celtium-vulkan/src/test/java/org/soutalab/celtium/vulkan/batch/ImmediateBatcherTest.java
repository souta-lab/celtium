package org.soutalab.celtium.vulkan.batch;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImmediateBatcherTest {

    private static final int BYTES_PER_VERTEX = 16;

    private static ByteBuffer vertices(int quads) {
        return ByteBuffer.allocate(quads * 4 * BYTES_PER_VERTEX);
    }

    private static ImmediateBatcher batcherOn(DirectStagingRing ring) {
        ImmediateBatcher batcher = new ImmediateBatcher();
        batcher.beginFrame(ring);
        return batcher;
    }

    @Test
    void opaqueItemsSortByPipelineWithinAPass() {
        ImmediateBatcher batcher = batcherOn(new DirectStagingRing(1, 1 << 16));
        assertTrue(batcher.captureQuads(1, 30, 0, 0, 0, false, vertices(1), 1, BYTES_PER_VERTEX));
        assertTrue(batcher.captureQuads(1, 10, 0, 0, 0, false, vertices(1), 1, BYTES_PER_VERTEX));
        assertTrue(batcher.captureQuads(1, 20, 0, 0, 0, false, vertices(1), 1, BYTES_PER_VERTEX));
        BatchPlan plan = batcher.endFrame();
        assertEquals(3, plan.entryCount());
        assertEquals(10, plan.pipelineKey(0));
        assertEquals(20, plan.pipelineKey(1));
        assertEquals(30, plan.pipelineKey(2));
        // Index ranges follow capture order, not plan order: pipeline 10 was captured second.
        assertEquals(6, plan.firstIndex(0));
        assertEquals(12, plan.firstIndex(1));
        assertEquals(0, plan.firstIndex(2));
    }

    @Test
    void vertexOffsetLandsPatternIndicesOnTheRingVertices() {
        ImmediateBatcher batcher = batcherOn(new DirectStagingRing(1, 1 << 16));
        // First capture: 2 quads at ring offset 0. Second: 1 quad right behind it.
        batcher.captureQuads(1, 10, 0, 0, 0, false, vertices(2), 2, BYTES_PER_VERTEX);
        batcher.captureQuads(1, 10, 0, 0, 0, false, vertices(1), 1, BYTES_PER_VERTEX);
        BatchPlan plan = batcher.endFrame();
        // Both entries share vertexOffset 0: pattern value 4q + 0 is the absolute vertex index
        // for every quad of this frame, because quads and vertices were laid down in lockstep.
        assertEquals(2, plan.entryCount());
        assertEquals(0, plan.vertexOffset(0));
        assertEquals(0, plan.vertexOffset(1));
        assertEquals(12, plan.indexCount(0));
        assertEquals(6, plan.indexCount(1));
    }

    @Test
    void orderedItemsComeAfterOpaqueInTheSamePassAndKeepCaptureOrder() {
        ImmediateBatcher batcher = batcherOn(new DirectStagingRing(1, 1 << 16));
        batcher.captureQuads(1, 10, 0, 0, 0, true, vertices(1), 1, BYTES_PER_VERTEX);
        batcher.captureQuads(1, 99, 0, 0, 0, false, vertices(1), 1, BYTES_PER_VERTEX);
        batcher.captureQuads(1, 5, 0, 0, 0, true, vertices(1), 1, BYTES_PER_VERTEX);
        BatchPlan plan = batcher.endFrame();
        assertEquals(3, plan.entryCount());
        assertEquals(99, plan.pipelineKey(0));
        assertEquals(10, plan.pipelineKey(1));
        assertEquals(5, plan.pipelineKey(2));
    }

    @Test
    void passesStayInPassOrder() {
        ImmediateBatcher batcher = batcherOn(new DirectStagingRing(1, 1 << 16));
        batcher.captureQuads(2, 1, 0, 0, 0, false, vertices(1), 1, BYTES_PER_VERTEX);
        batcher.captureQuads(1, 1, 0, 0, 0, false, vertices(1), 1, BYTES_PER_VERTEX);
        BatchPlan plan = batcher.endFrame();
        assertEquals(6, plan.firstIndex(0));
        assertEquals(0, plan.firstIndex(1));
    }

    @Test
    void adjacentCapturesOfOneInstanceRecordFuse() {
        ImmediateBatcher batcher = batcherOn(new DirectStagingRing(1, 1 << 16));
        // Same pipeline, same shared instance record 7, back to back.
        batcher.captureQuads(1, 10, 0, 0, 0, 7, false, vertices(1), 1, BYTES_PER_VERTEX);
        batcher.captureQuads(1, 10, 0, 0, 0, 7, false, vertices(2), 2, BYTES_PER_VERTEX);
        BatchPlan plan = batcher.endFrame();
        assertEquals(2, plan.sourceItems());
        assertEquals(1, plan.entryCount());
        assertEquals(18, plan.indexCount(0));
        assertEquals(7, plan.firstInstance(0));
    }

    @Test
    void distinctInstanceRecordsDoNotFuse() {
        ImmediateBatcher batcher = batcherOn(new DirectStagingRing(1, 1 << 16));
        batcher.captureQuads(1, 10, 0, 0, 0, 7, false, vertices(1), 1, BYTES_PER_VERTEX);
        batcher.captureQuads(1, 10, 0, 0, 0, 8, false, vertices(1), 1, BYTES_PER_VERTEX);
        BatchPlan plan = batcher.endFrame();
        assertEquals(2, plan.entryCount());
    }

    @Test
    void fullRingDropsTheCaptureAndCountsIt() {
        ImmediateBatcher batcher = batcherOn(new DirectStagingRing(1, 64));
        assertTrue(batcher.captureQuads(1, 10, 0, 0, 0, false, vertices(1), 1, BYTES_PER_VERTEX));
        assertFalse(batcher.captureQuads(1, 10, 0, 0, 0, false, vertices(1), 1, BYTES_PER_VERTEX));
        assertEquals(1, batcher.droppedCaptures());
        assertEquals(1, batcher.endFrame().entryCount());
    }

    @Test
    void sameKeyOpaqueItemsKeepCaptureOrder() {
        ImmediateBatcher batcher = batcherOn(new DirectStagingRing(1, 1 << 16));
        // Identical (pass, pipeline, state, layer): only the key's sequence field separates
        // the items, so the plan must list them in capture order. Distinct instance records
        // keep them unfused so the order is observable entry by entry.
        batcher.captureQuads(1, 10, 0, 0, 0, 7, false, vertices(1), 1, BYTES_PER_VERTEX);
        batcher.captureQuads(1, 10, 0, 0, 0, 8, false, vertices(1), 1, BYTES_PER_VERTEX);
        batcher.captureQuads(1, 10, 0, 0, 0, 9, false, vertices(1), 1, BYTES_PER_VERTEX);
        BatchPlan plan = batcher.endFrame();
        assertEquals(3, plan.entryCount());
        assertEquals(0, plan.firstIndex(0));
        assertEquals(6, plan.firstIndex(1));
        assertEquals(12, plan.firstIndex(2));
    }

    @Test
    void rankingStaysCorrectAcrossReusedFrames() {
        DirectStagingRing ring = new DirectStagingRing(2, 1 << 16);
        ImmediateBatcher batcher = new ImmediateBatcher();

        // Frame 1: enough items to grow the ranking buffers, in scrambled pipeline order.
        batcher.beginFrame(ring);
        for (int i = 0; i < 64; i++) {
            int pipeline = (i * 37) % 64;
            assertTrue(batcher.captureQuads(1, pipeline, 0, 0, 0, false,
                    vertices(1), 1, BYTES_PER_VERTEX));
        }
        BatchPlan first = batcher.endFrame();
        assertEquals(64, first.sourceItems());
        for (int e = 1; e < first.entryCount(); e++) {
            assertTrue(first.pipelineKey(e - 1) <= first.pipelineKey(e),
                    "frame 1 entries out of order at " + e);
        }

        // Frame 2: far fewer items than the buffers hold - stale capacity must not leak in.
        ring.advanceFrame();
        batcher.beginFrame(ring);
        assertTrue(batcher.captureQuads(1, 30, 0, 0, 0, false, vertices(1), 1, BYTES_PER_VERTEX));
        assertTrue(batcher.captureQuads(1, 10, 0, 0, 0, false, vertices(1), 1, BYTES_PER_VERTEX));
        BatchPlan second = batcher.endFrame();
        assertEquals(2, second.entryCount());
        assertEquals(10, second.pipelineKey(0));
        assertEquals(30, second.pipelineKey(1));

        // Frame 3: empty.
        ring.advanceFrame();
        batcher.beginFrame(ring);
        BatchPlan third = batcher.endFrame();
        assertEquals(0, third.sourceItems());
        assertEquals(0, third.entryCount());
    }

    /**
     * The int[] merge sort that replaced the boxed {@code Integer[]} ranking must produce the
     * identical plan the old algorithm produced. The oracle below is that old algorithm -
     * boxed sort with the same rank comparator, then the same fusion rule - run over the item
     * tuples the captures were known to append.
     */
    @Test
    void planMatchesTheBoxedSortOracleOnRandomFrames() {
        int[] strides = {16, 24, 32};
        int[] pipelines = {5, 10, 10, 20, 30};
        for (long seed = 1; seed <= 5; seed++) {
            Random random = new Random(seed);
            int items = 400;
            DirectStagingRing ring = new DirectStagingRing(1, 1 << 20);
            ImmediateBatcher batcher = batcherOn(ring);
            List<long[]> oracleItems = new ArrayList<>();
            long cursor = 0;
            int nextQuad = 0;
            for (int i = 0; i < items; i++) {
                int pass = 1 + random.nextInt(3);
                int pipeline = pipelines[random.nextInt(pipelines.length)];
                int state = random.nextInt(2);
                int layer = random.nextInt(3);
                int bpv = strides[random.nextInt(strides.length)];
                int quads = 1 + random.nextInt(3);
                boolean ordered = random.nextBoolean();
                boolean shared = random.nextInt(4) == 0;
                int instance = shared ? 7 + random.nextInt(2) : -1;
                ByteBuffer data = ByteBuffer.allocate(quads * 4 * bpv);
                boolean captured = shared
                        ? batcher.captureQuads(pass, pipeline, state, layer, 0, instance,
                                ordered, data, quads, bpv)
                        : batcher.captureQuads(pass, pipeline, state, layer, 0,
                                ordered, data, quads, bpv);
                assertTrue(captured, "seed " + seed + " item " + i + " dropped");
                long offset = (cursor + bpv - 1) / bpv * bpv;
                cursor = offset + (long) quads * 4 * bpv;
                int vertexOffset = (int) (offset / bpv) - nextQuad * 4;
                // sortKey, vertexOffset, firstQuad, quadCount, instanceIndex, orderedFlag
                oracleItems.add(new long[]{
                        DrawSortKey.encode(pass, pipeline, state, layer, i),
                        vertexOffset, nextQuad, quads,
                        shared ? instance : i, ordered ? 1 : 0});
                nextQuad += quads;
            }
            BatchPlan plan = batcher.endFrame();

            Integer[] order = new Integer[items];
            for (int i = 0; i < items; i++) {
                order[i] = i;
            }
            Arrays.sort(order, new Comparator<Integer>() {
                @Override
                public int compare(Integer a, Integer b) {
                    long keyA = oracleItems.get(a)[0];
                    long keyB = oracleItems.get(b)[0];
                    int byPass = Integer.compare(
                            DrawSortKey.passId(keyA), DrawSortKey.passId(keyB));
                    if (byPass != 0) {
                        return byPass;
                    }
                    boolean aOrdered = oracleItems.get(a)[5] != 0;
                    boolean bOrdered = oracleItems.get(b)[5] != 0;
                    if (aOrdered != bOrdered) {
                        return aOrdered ? 1 : -1;
                    }
                    if (aOrdered) {
                        return Integer.compare(
                                DrawSortKey.sequence(keyA), DrawSortKey.sequence(keyB));
                    }
                    return Long.compare(keyA, keyB);
                }
            });
            List<int[]> expected = new ArrayList<>();
            for (int n = 0; n < items; n++) {
                long[] item = oracleItems.get(order[n]);
                int pipeline = DrawSortKey.pipelineKey(item[0]);
                int indexCount = (int) item[3] * 6;
                int firstIndex = (int) item[2] * 6;
                int vertexOffset = (int) item[1];
                int instance = (int) item[4];
                int last = expected.size() - 1;
                if (last >= 0
                        && expected.get(last)[0] == pipeline
                        && expected.get(last)[4] == vertexOffset
                        && expected.get(last)[5] == instance
                        && expected.get(last)[2] == 1
                        && expected.get(last)[3] + expected.get(last)[1] == firstIndex) {
                    expected.get(last)[1] += indexCount;
                    continue;
                }
                // pipeline, indexCount, instanceCount, firstIndex, vertexOffset, firstInstance
                expected.add(new int[]{pipeline, indexCount, 1, firstIndex, vertexOffset, instance});
            }
            assertEquals(items, plan.sourceItems(), "seed " + seed);
            assertEquals(expected.size(), plan.entryCount(), "seed " + seed);
            for (int e = 0; e < expected.size(); e++) {
                int[] want = expected.get(e);
                String where = "seed " + seed + " entry " + e;
                assertEquals(want[0], plan.pipelineKey(e), where);
                assertEquals(want[1], plan.indexCount(e), where);
                assertEquals(want[2], plan.instanceCount(e), where);
                assertEquals(want[3], plan.firstIndex(e), where);
                assertEquals(want[4], plan.vertexOffset(e), where);
                assertEquals(want[5], plan.firstInstance(e), where);
            }
        }
    }

    @Test
    void mismatchedVertexDataIsRejected() {
        ImmediateBatcher batcher = batcherOn(new DirectStagingRing(1, 1 << 16));
        try {
            batcher.captureQuads(1, 10, 0, 0, 0, false, vertices(1), 2, BYTES_PER_VERTEX);
            throw new AssertionError("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // rejected before anything was captured
        }
        assertEquals(0, batcher.capturedItems());
    }
}
