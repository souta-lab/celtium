package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the upload-ring slot ownership invariant in {@link VulkanContext#celtium$releaseUploadSlotsAfterFlush}
 * (issue #422): flushing a later batch must never reopen a slot still owned by an earlier in-flight batch.
 */
final class UploadSlotOwnershipTest {

    @Test
    void flushingSecondBatchPreservesInFlightSlotOwnedByFirstBatch() {
        boolean[] free = {false, true, true, true}; // slot 0 is in flight from batch 1
        long[] cursor = {0L, 4096L, 0L, 0L};        // batch 2 wrote into slot 1

        VulkanContext.celtium$releaseUploadSlotsAfterFlush(free, cursor, 1L << 1, true);

        assertFalse(free[0], "slot 0 is still owned by in-flight batch 1 and must not be reopened");
        assertFalse(free[1], "slot 1 was just submitted by batch 2 and must stay closed until its fence");
        assertTrue(free[2], "unused free slot 2 stays free");
        assertTrue(free[3], "unused free slot 3 stays free");
        assertEquals(0L, cursor[1], "submitted slot 1 cursor resets");
    }

    @Test
    void refusedSubmissionReopensOnlyItsOwnSlotsNotEarlierInFlightSlots() {
        boolean[] free = {false, true, true, true}; // slot 0 in flight from batch 1
        long[] cursor = {0L, 2048L, 0L, 0L};        // batch 2 used slot 1, then failed to submit

        VulkanContext.celtium$releaseUploadSlotsAfterFlush(free, cursor, 1L << 1, false);

        assertFalse(free[0], "slot 0 is still owned by in-flight batch 1");
        assertTrue(free[1], "refused batch 2 owns nothing, so slot 1 reopens");
        assertEquals(0L, cursor[1]);
    }
}
