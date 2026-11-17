package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The frame budget's own arithmetic (item 328, V-03), without a device: the vertex charges for the two index
 * shapes, whole-batch drops at the caps with their counters, the rebuild allowance's adaptation to measured
 * rebuild time, and the frame-window rollover that keeps one budget per presented frame across its passes.
 */
class TerrainFrameBudgetTest {

    private static VulkanMultiDrawBatch batchOf(int... elementCounts) {
        VulkanMultiDrawBatch batch = new VulkanMultiDrawBatch(elementCounts.length);
        for (int i = 0; i < elementCounts.length; i++) {
            batch.appendDrawCommand(i * 4, elementCounts[i], 0L);
        }
        return batch;
    }

    @Test
    void unsortedCommandsChargeFourVerticesPerSixIndices() {
        TerrainFrameBudget budget = new TerrainFrameBudget(1_000_000L, 16_384, 128, 2_000_000L);
        // 600 indices of the shared quad pattern are 100 quads = 400 vertices; 6 more are one quad = 4.
        assertEquals(404L, budget.verticesOf(batchOf(600, 6), false));
    }

    @Test
    void sortedCommandsChargeTheIndexCountAsTheVertexUpperBound() {
        TerrainFrameBudget budget = new TerrainFrameBudget(1_000_000L, 16_384, 128, 2_000_000L);
        assertEquals(606L, budget.verticesOf(batchOf(600, 6), true));
    }

    @Test
    void aBatchThatWouldCrossTheVertexCapIsDroppedWholeAndCounted() {
        TerrainFrameBudget budget = new TerrainFrameBudget(1_000L, 16_384, 128, 2_000_000L);
        budget.beginFrame(1L);
        assertTrue(budget.tryConsumeDraw(900L, 3), "the first batch fits");
        assertFalse(budget.tryConsumeDraw(200L, 2), "900 + 200 crosses the 1000 vertex cap");
        budget.noteDroppedBatch(200L, 2);
        assertEquals(1, budget.droppedBatches());
        assertEquals(200L, budget.totalDroppedVertices());
        assertEquals(2L, budget.totalDroppedDraws());
        // The dropped batch charged nothing: a later small batch still fits the remainder.
        assertTrue(budget.tryConsumeDraw(100L, 1));
        assertEquals(1_000L, budget.totalDrawnVertices());
    }

    @Test
    void theDrawCapIsEnforcedIndependentlyOfTheVertexCap() {
        TerrainFrameBudget budget = new TerrainFrameBudget(0L, 4, 128, 2_000_000L);
        budget.beginFrame(1L);
        assertTrue(budget.tryConsumeDraw(10L, 3));
        assertFalse(budget.tryConsumeDraw(10L, 2), "3 + 2 crosses the 4 draw cap");
        assertTrue(budget.tryConsumeDraw(10L, 1), "the vertex budget is disabled at 0, the draw remainder fits");
    }

    @Test
    void aZeroBudgetDisablesThatCap() {
        TerrainFrameBudget budget = new TerrainFrameBudget(0L, 0, 128, 2_000_000L);
        budget.beginFrame(1L);
        assertTrue(budget.tryConsumeDraw(999_999_999L, 999_999));
    }

    @Test
    void theWindowRollsOncePerFrameAndPassesShareIt() {
        TerrainFrameBudget budget = new TerrainFrameBudget(1_000L, 16_384, 128, 2_000_000L);
        budget.beginFrame(7L);
        assertTrue(budget.tryConsumeDraw(600L, 1));
        budget.beginFrame(7L);
        assertFalse(budget.tryConsumeDraw(600L, 1), "a second pass of frame 7 shares frame 7's budget");
        budget.beginFrame(8L);
        assertTrue(budget.tryConsumeDraw(600L, 1), "frame 8 starts with a fresh budget");
    }

    @Test
    void theAllowanceHalvesWhenRebuildsOverrunTheTargetAndRecoversUnderIt() {
        TerrainFrameBudget budget = new TerrainFrameBudget(0L, 0, 128, 2_000_000L);
        budget.beginFrame(1L);
        for (int i = 0; i < 128; i++) {
            assertTrue(budget.rebuildAllowed(), "rebuild " + i + " is within the initial allowance");
            budget.noteRebuild(100_000L);
        }
        assertFalse(budget.rebuildAllowed(), "the allowance is exhausted at the cap");
        // 128 rebuilds x 100 us = 12.8 ms against a 2 ms target: the next frame's allowance halves.
        budget.beginFrame(2L);
        assertEquals(64, budget.allowance());
        // A quiet frame (one fast rebuild, well under a quarter of target) grows it back by a quarter.
        budget.noteRebuild(1_000L);
        budget.beginFrame(3L);
        assertEquals(80, budget.allowance());
    }

    @Test
    void theAllowanceNeverFallsBelowTheFloor() {
        TerrainFrameBudget budget = new TerrainFrameBudget(0L, 0, 16, 1_000L);
        long frame = 1L;
        for (int round = 0; round < 6; round++) {
            budget.beginFrame(frame++);
            budget.noteRebuild(1_000_000L);
        }
        budget.beginFrame(frame);
        assertEquals(8, budget.allowance(), "halving stops at the floor of 8");
    }

    @Test
    void forcedFirstBuildsFeedTheSignalWithoutConsumingAllowance() {
        TerrainFrameBudget budget = new TerrainFrameBudget(0L, 0, 16, 1_000L);
        budget.beginFrame(1L);
        budget.noteForcedFirstBuild(5_000_000L);
        assertTrue(budget.rebuildAllowed(), "a first build does not consume rebuild allowance");
        assertEquals(0, budget.rebuildsUsed());
        assertEquals(1L, budget.totalForcedFirstBuilds());
        budget.beginFrame(2L);
        assertEquals(8, budget.allowance(), "but its time still halves the next frame's allowance");
    }

    @Test
    void theFinishedWindowSnapshotCarriesTheCompletedFrame() {
        TerrainFrameBudget budget = new TerrainFrameBudget(10L, 16_384, 128, 2_000_000L);
        budget.beginFrame(5L);
        assertFalse(budget.tryConsumeDraw(50L, 1));
        budget.noteDroppedBatch(50L, 1);
        budget.beginFrame(6L);
        assertTrue(budget.finishedHadEvents());
        assertEquals(5L, budget.finishedFrame());
        assertTrue(budget.finishedSummary().startsWith("frame=5 "), budget.finishedSummary());
        assertTrue(budget.finishedSummary().contains("droppedBatches=1"), budget.finishedSummary());
        budget.beginFrame(7L);
        assertFalse(budget.finishedHadEvents(), "a quiet frame's snapshot has no events");
        assertEquals(6L, budget.finishedFrame());
    }

    @Test
    void deferredReuseIsCountedInTheWindowAndTheTotals() {
        TerrainFrameBudget budget = new TerrainFrameBudget(0L, 0, 128, 2_000_000L);
        budget.beginFrame(1L);
        assertFalse(budget.windowHasEvents());
        budget.noteDeferredReuse();
        assertTrue(budget.windowHasEvents());
        assertEquals(1, budget.reusedSnapshots());
        assertEquals(1L, budget.totalDeferredRebuilds());
        budget.beginFrame(2L);
        assertFalse(budget.windowHasEvents(), "the next frame starts with a clean event tally");
        assertEquals(1L, budget.totalReusedSnapshots(), "the cumulative totals survive the rollover");
    }
}
