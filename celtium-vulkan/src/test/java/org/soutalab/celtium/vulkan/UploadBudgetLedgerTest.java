package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the upload budget ledger's arithmetic (S3R-1 layer 1): window rolling by frame key, overrun
 * counted as only the portion beyond the budget, the unlimited escape, the pure {@code wouldExceed}
 * judgment, and the layer-2 deferral account with its cap forcing an issue.
 */
final class UploadBudgetLedgerTest {

    @Test
    void bytesWithinBudgetAccumulateWithoutOverrun() {
        UploadBudgetLedger ledger = new UploadBudgetLedger(100L, 0L);
        assertEquals(0L, ledger.noteFlushed(1L, 40L));
        assertEquals(0L, ledger.noteFlushed(1L, 60L));
        assertEquals(100L, ledger.windowBytes());
        assertEquals(0L, ledger.windowOverrunBytes());
        assertEquals(100L, ledger.totalFlushedBytes());
        assertEquals(0L, ledger.totalOverrunBytes());
    }

    @Test
    void overrunCountsOnlyThePortionBeyondTheBudget() {
        UploadBudgetLedger ledger = new UploadBudgetLedger(100L, 0L);
        assertEquals(0L, ledger.noteFlushed(1L, 60L));
        // 60 already in the window; of the next 60 only 20 sit beyond the budget of 100.
        assertEquals(20L, ledger.noteFlushed(1L, 60L));
        // Everything past a full window is overrun.
        assertEquals(10L, ledger.noteFlushed(1L, 10L));
        assertEquals(130L, ledger.windowBytes());
        assertEquals(30L, ledger.windowOverrunBytes());
        assertEquals(30L, ledger.totalOverrunBytes());
    }

    @Test
    void windowRollsOnANewFrameKey() {
        UploadBudgetLedger ledger = new UploadBudgetLedger(100L, 0L);
        assertEquals(50L, ledger.noteFlushed(1L, 150L));
        // A new key starts a fresh window: the same 100 that overran frame 1 fits frame 2 exactly.
        assertEquals(0L, ledger.noteFlushed(2L, 100L));
        assertEquals(100L, ledger.windowBytes());
        assertEquals(0L, ledger.windowOverrunBytes());
        assertEquals(250L, ledger.totalFlushedBytes());
        assertEquals(50L, ledger.totalOverrunBytes());
    }

    @Test
    void unlimitedBudgetNeverOverruns() {
        UploadBudgetLedger ledger = new UploadBudgetLedger(0L, 0L);
        assertEquals(0L, ledger.noteFlushed(1L, 1L << 30));
        assertFalse(ledger.wouldExceed(1L, 1L << 30));
        assertEquals(0L, ledger.totalOverrunBytes());
    }

    @Test
    void wouldExceedJudgesTheCurrentWindowWithoutMutatingIt() {
        UploadBudgetLedger ledger = new UploadBudgetLedger(100L, 0L);
        ledger.noteFlushed(1L, 60L);
        assertTrue(ledger.wouldExceed(1L, 41L));
        assertFalse(ledger.wouldExceed(1L, 40L));
        // An unopened key reads as an empty window.
        assertFalse(ledger.wouldExceed(2L, 100L));
        assertTrue(ledger.wouldExceed(2L, 101L));
        // The query changed nothing: the next accounting still lands in frame 1's window.
        assertEquals(0L, ledger.noteFlushed(1L, 40L));
        assertEquals(100L, ledger.windowBytes());
    }

    @Test
    void deferralCapRefusesAndCountsAForcedIssue() {
        UploadBudgetLedger ledger = new UploadBudgetLedger(0L, 100L);
        assertTrue(ledger.noteDeferred(60L));
        // 60 outstanding of a 100 cap: another 60 does not fit and must be issued instead.
        assertFalse(ledger.noteDeferred(60L));
        assertEquals(1L, ledger.deferredCapForce());
        assertEquals(60L, ledger.deferredOutstandingBytes());
        assertEquals(60L, ledger.deferredTotalBytes());
        assertEquals(1L, ledger.deferredCount());
        // Issuing the held bytes frees the backlog; the cumulative totals stay.
        ledger.noteDeferredIssued(60L);
        assertEquals(0L, ledger.deferredOutstandingBytes());
        assertTrue(ledger.noteDeferred(60L));
        assertEquals(120L, ledger.deferredTotalBytes());
        assertEquals(2L, ledger.deferredCount());
    }

    @Test
    void uncappedDeferralAlwaysAccepts() {
        UploadBudgetLedger ledger = new UploadBudgetLedger(0L, 0L);
        assertTrue(ledger.noteDeferred(1L << 30));
        assertEquals(0L, ledger.deferredCapForce());
        assertEquals(1L << 30, ledger.deferredOutstandingBytes());
    }
}
