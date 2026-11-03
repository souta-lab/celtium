package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The accept path's flush bound, checked without a device.
 * <p>
 * {@code pendingImmediateIndices} is incremented by the index count of every accepted batch and read against
 * {@code IMMEDIATE_FLUSH_INDICES} to bound how much geometry a window holds before it is drawn. The draw side
 * subtracted every drawn group's index count, including the model-run groups whose indices the accept path never
 * added - the baseline verify line carries {@code pendingImmediateIndices=-3168} beside {@code modelDraws=88}. A
 * negative counter can never reach the threshold, so the bound was dead rather than tight, and the pending geometry
 * was unbounded until the next ordering flush.
 * <p>
 * What these tests can prove: the arithmetic of the two cases. They prove nothing about a frame - a green test here
 * is not evidence that any window was bounded.
 */
class PendingIndicesAccountingTest {

    @Test
    void aCountedGroupIsSubtracted() {
        assertEquals(900, VulkanTerrainGpuBackend.celtium$pendingIndicesAfterDraw(1000, false, 100),
                "a group the accept path counted gives its indices back when it is drawn");
    }

    @Test
    void aModelRunIsNotSubtracted() {
        // The defect: the replay's indices were never added, so subtracting them drove the counter negative.
        assertEquals(1000, VulkanTerrainGpuBackend.celtium$pendingIndicesAfterDraw(1000, true, 100),
                "a model-run group was never counted, so drawing it must leave the counter alone");
    }

    @Test
    void aModelRunCannotDriveTheCounterNegative() {
        int pending = 0;
        for (int i = 0; i < 32; i++) {
            pending = VulkanTerrainGpuBackend.celtium$pendingIndicesAfterDraw(pending, true, 99);
        }
        assertEquals(0, pending, "32 model runs on an empty queue leave the bound where it was");
        assertTrue(pending >= 0, "and never below zero, which is what made the threshold unreachable");
    }

    @Test
    void theCountedPathStillReachesZero() {
        int pending = 8192;
        while (pending > 0) {
            pending = VulkanTerrainGpuBackend.celtium$pendingIndicesAfterDraw(pending, false, 64);
        }
        assertEquals(0, pending, "a queue of counted groups drains exactly");
    }
}
