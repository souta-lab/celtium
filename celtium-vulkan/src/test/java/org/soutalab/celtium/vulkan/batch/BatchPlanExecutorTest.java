package org.soutalab.celtium.vulkan.batch;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fixes the {@link BatchPlanExecutor}'s own contract against a recording sink: entries are
 * issued in plan order with their fields verbatim, pipelines bind only at key changes, and
 * the per-texture rebind lands exactly at the texture-slot change points of per-texture
 * entries - never for an entry whose texture rides in its instance record (slicing design
 * section 3-3). A record-riding entry also forces the next per-texture entry to rebind,
 * even for the same slot, because the array entry leaves the texture array bound at the
 * set index the per-texture set occupies. What a bind or a draw does on a real command
 * buffer is the production sink's side of the seam and stays with the wiring leg.
 *
 * <p>The accounting half of the contract (G2-24) is fixed here too: a sink answers each
 * draw with accepted or dropped, a drop is counted at the sink's seat as it is answered,
 * and {@code execute} returns the accepted tally - so the dropped count of an execution
 * is the entry count minus the return value, exactly the seat counter's growth.
 */
class BatchPlanExecutorTest {

    /** Records every sink call as one line, in call order. */
    private static final class RecordingSink implements BatchPlanExecutor.DrawSink {
        final List<String> calls = new ArrayList<>();
        /** Entries (named by firstInstance) this sink refuses - the texture-drop stand-in. */
        final java.util.Set<Integer> refusedInstances = new java.util.HashSet<>();
        /** The fake's seat counter: one per refused entry, counted as the drop is answered. */
        int seatDropped;

        @Override
        public void bindPipeline(int pipelineKey) {
            this.calls.add("pipeline=" + pipelineKey);
        }

        @Override
        public void bindTexture(int textureSlot) {
            this.calls.add("texture=" + textureSlot);
        }

        @Override
        public void bindState(int stateKey) {
            this.calls.add("state=" + stateKey);
        }

        @Override
        public boolean drawIndexed(int indexCount, int instanceCount, int firstIndex,
                                   int vertexOffset, int firstInstance) {
            String line = " ic=" + indexCount + " n=" + instanceCount
                    + " fi=" + firstIndex + " vo=" + vertexOffset + " inst=" + firstInstance;
            if (this.refusedInstances.contains(Integer.valueOf(firstInstance))) {
                this.seatDropped++;
                this.calls.add("dropped" + line);
                return false;
            }
            this.calls.add("draw" + line);
            return true;
        }
    }

    private static BatchPlan plan(int[] pipelines, int[] textures, int entries) {
        return plan(pipelines, textures, new int[entries], new boolean[entries], entries);
    }

    /** A plan whose entries all ride in their records: no texture bind is ever expected. */
    private static BatchPlan recordRidingPlan(int[] pipelines, int[] textures, int entries) {
        boolean[] inRecord = new boolean[entries];
        java.util.Arrays.fill(inRecord, true);
        return plan(pipelines, textures, new int[entries], inRecord, entries);
    }

    private static BatchPlan plan(int[] pipelines, int[] textures, int[] states, int entries) {
        return plan(pipelines, textures, states, new boolean[entries], entries);
    }

    private static BatchPlan plan(int[] pipelines, int[] textures, int[] states,
                                  boolean[] textureInRecord, int entries) {
        int[] indexCounts = new int[entries];
        int[] instanceCounts = new int[entries];
        int[] firstIndices = new int[entries];
        int[] vertexOffsets = new int[entries];
        int[] firstInstances = new int[entries];
        for (int i = 0; i < entries; i++) {
            indexCounts[i] = 6 * (i + 1);
            instanceCounts[i] = 1;
            firstIndices[i] = 6 * i;
            vertexOffsets[i] = 100 + i;
            firstInstances[i] = i;
        }
        return new BatchPlan(pipelines, indexCounts, instanceCounts, firstIndices,
                vertexOffsets, firstInstances, textures, states, textureInRecord, entries);
    }

    @Test
    void emptyPlanIssuesNothing() {
        RecordingSink sink = new RecordingSink();
        int accepted = new BatchPlanExecutor().execute(plan(new int[0], new int[0], 0), sink);
        assertEquals(0, accepted);
        assertTrue(sink.calls.isEmpty());
    }

    @Test
    void entriesAreIssuedInPlanOrderWithTheirFieldsVerbatim() {
        RecordingSink sink = new RecordingSink();
        BatchPlan plan = recordRidingPlan(new int[]{7, 8, 9}, new int[]{1, 1, 1}, 3);
        assertEquals(3, new BatchPlanExecutor().execute(plan, sink));
        assertEquals(List.of(
                "pipeline=7", "state=0", "draw ic=6 n=1 fi=0 vo=100 inst=0",
                "pipeline=8", "draw ic=12 n=1 fi=6 vo=101 inst=1",
                "pipeline=9", "draw ic=18 n=1 fi=12 vo=102 inst=2"), sink.calls);
    }

    @Test
    void pipelineBindsOnlyWhereTheKeyChanges() {
        RecordingSink sink = new RecordingSink();
        BatchPlan plan = recordRidingPlan(new int[]{5, 5, 6, 6, 5}, new int[]{1, 1, 1, 1, 1}, 5);
        assertEquals(5, new BatchPlanExecutor().execute(plan, sink));
        assertEquals(List.of(
                "pipeline=5", "state=0", "draw ic=6 n=1 fi=0 vo=100 inst=0",
                "draw ic=12 n=1 fi=6 vo=101 inst=1",
                "pipeline=6", "draw ic=18 n=1 fi=12 vo=102 inst=2",
                "draw ic=24 n=1 fi=18 vo=103 inst=3",
                "pipeline=5", "draw ic=30 n=1 fi=24 vo=104 inst=4"), sink.calls);
    }

    @Test
    void textureRebindsAtEveryChangePointOnThePerTexturePath() {
        RecordingSink sink = new RecordingSink();
        BatchPlan plan = plan(new int[]{5, 5, 5, 5}, new int[]{11, 11, 22, 11}, 4);
        assertEquals(4, new BatchPlanExecutor().execute(plan, sink));
        assertEquals(List.of(
                "pipeline=5", "texture=11", "state=0", "draw ic=6 n=1 fi=0 vo=100 inst=0",
                "draw ic=12 n=1 fi=6 vo=101 inst=1",
                "texture=22", "draw ic=18 n=1 fi=12 vo=102 inst=2",
                "texture=11", "draw ic=24 n=1 fi=18 vo=103 inst=3"), sink.calls);
    }

    @Test
    void textureChangesNeverRebindForRecordRidingEntries() {
        RecordingSink sink = new RecordingSink();
        BatchPlan plan = recordRidingPlan(new int[]{5, 5, 5}, new int[]{11, 22, 33}, 3);
        assertEquals(3, new BatchPlanExecutor().execute(plan, sink));
        assertEquals(List.of(
                "pipeline=5", "state=0", "draw ic=6 n=1 fi=0 vo=100 inst=0",
                "draw ic=12 n=1 fi=6 vo=101 inst=1",
                "draw ic=18 n=1 fi=12 vo=102 inst=2"), sink.calls);
    }

    /**
     * The mixed segment: a per-texture entry, a record-riding one, then the same texture
     * per-texture again. The record-riding entry issues no texture bind of its own, and the
     * third entry rebinds its texture even though the slot equals the first entry's - the
     * array entry's pipeline bind replaced the set at that index.
     */
    @Test
    void perTextureEntryAfterARecordRidingOneRebindsTheSameSlot() {
        RecordingSink sink = new RecordingSink();
        BatchPlan plan = plan(new int[]{5, 6, 5}, new int[]{11, 22, 11}, new int[]{0, 0, 0},
                new boolean[]{false, true, false}, 3);
        assertEquals(3, new BatchPlanExecutor().execute(plan, sink));
        assertEquals(List.of(
                "pipeline=5", "texture=11", "state=0", "draw ic=6 n=1 fi=0 vo=100 inst=0",
                "pipeline=6", "draw ic=12 n=1 fi=6 vo=101 inst=1",
                "pipeline=5", "texture=11", "draw ic=18 n=1 fi=12 vo=102 inst=2"), sink.calls);
    }

    /**
     * A state change re-issues only the state: the panel whose clip rectangle changes
     * halfway keeps one pipeline and one texture, and the executor's only extra call is the
     * state point between the two draws.
     */
    @Test
    void stateChangesReissueStateWithoutRebindingPipelineOrTexture() {
        RecordingSink sink = new RecordingSink();
        BatchPlan plan = plan(new int[]{5, 5}, new int[]{11, 11}, new int[]{3, 4}, 2);
        assertEquals(2, new BatchPlanExecutor().execute(plan, sink));
        assertEquals(List.of(
                "pipeline=5", "texture=11", "state=3", "draw ic=6 n=1 fi=0 vo=100 inst=0",
                "state=4", "draw ic=12 n=1 fi=6 vo=101 inst=1"), sink.calls);
    }

    /**
     * The batcher-to-executor seam end to end: ordered captures keep capture order through
     * the plan, and the executor's rebind points are the texture changes between them - the
     * GUI shape, where three strings on two font pages must draw in the order they were
     * offered, each under its own page. Unflagged captures are the per-texture form, which
     * is what an environment without a bindless binding produces for every group.
     */
    @Test
    void orderedCapturesExecuteInCaptureOrderWithRebindsBetweenPages() {
        DirectStagingRing ring = new DirectStagingRing(1, 4096);
        ImmediateBatcher batcher = new ImmediateBatcher();
        batcher.beginFrame(ring);
        ByteBuffer quad = ByteBuffer.allocateDirect(4 * 8);
        quad.limit(quad.capacity());
        assertTrue(batcher.captureQuads(0, 5, 0, 0, 101, true, quad.duplicate(), 1, 8));
        assertTrue(batcher.captureQuads(0, 5, 0, 0, 202, true, quad.duplicate(), 1, 8));
        assertTrue(batcher.captureQuads(0, 5, 0, 0, 101, true, quad.duplicate(), 1, 8));
        BatchPlan plan = batcher.endFrame();
        assertEquals(3, plan.entryCount());

        RecordingSink sink = new RecordingSink();
        assertEquals(3, new BatchPlanExecutor().execute(plan, sink));
        List<String> bindsAndDraws = new ArrayList<>();
        for (String call : sink.calls) {
            bindsAndDraws.add(call.startsWith("draw") ? "draw" : call);
        }
        assertEquals(List.of("pipeline=5", "texture=101", "state=0", "draw",
                "texture=202", "draw", "texture=101", "draw"), bindsAndDraws);
    }

    /**
     * The accounting contract (G2-24): entries the sink refuses are answered as drops,
     * counted at the sink's seat, and left out of the accepted tally the executor returns -
     * so the caller's dropped count (entries minus accepted) equals the seat counter's
     * growth exactly, and no dropped entry is ever accounted as drawn. The walk itself is
     * undisturbed: every entry is still offered once, in plan order.
     */
    @Test
    void droppedEntriesAreAnsweredCountedAtTheSeatAndLeftOutOfTheAcceptedTally() {
        RecordingSink sink = new RecordingSink();
        sink.refusedInstances.add(Integer.valueOf(1));
        sink.refusedInstances.add(Integer.valueOf(3));
        BatchPlan plan = recordRidingPlan(new int[]{5, 5, 5, 5}, new int[]{1, 1, 1, 1}, 4);
        int accepted = new BatchPlanExecutor().execute(plan, sink);
        assertEquals(2, accepted);
        assertEquals(2, sink.seatDropped);
        assertEquals(plan.entryCount() - accepted, sink.seatDropped);
        assertEquals(List.of(
                "pipeline=5", "state=0", "draw ic=6 n=1 fi=0 vo=100 inst=0",
                "dropped ic=12 n=1 fi=6 vo=101 inst=1",
                "draw ic=18 n=1 fi=12 vo=102 inst=2",
                "dropped ic=24 n=1 fi=18 vo=103 inst=3"), sink.calls);
    }

    /** A plan whose entries all drop accepts nothing, and the seat holds every drop. */
    @Test
    void aPlanWhoseEntriesAllDropAcceptsNothing() {
        RecordingSink sink = new RecordingSink();
        sink.refusedInstances.add(Integer.valueOf(0));
        sink.refusedInstances.add(Integer.valueOf(1));
        BatchPlan plan = recordRidingPlan(new int[]{5, 5}, new int[]{1, 1}, 2);
        int accepted = new BatchPlanExecutor().execute(plan, sink);
        assertEquals(0, accepted);
        assertEquals(plan.entryCount(), sink.seatDropped);
        assertTrue(sink.calls.stream().noneMatch(call -> call.startsWith("draw ")),
                "no dropped entry is recorded as a draw");
    }
}
