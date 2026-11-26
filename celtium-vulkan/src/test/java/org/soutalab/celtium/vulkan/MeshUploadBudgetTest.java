package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The mesh producer's own budget account (budget layer 2, option C): window arithmetic charged
 * at issue time, the deferral account against its cap, and the budget's resolution - the byte
 * property over the MiB property over the shared budget.
 */
class MeshUploadBudgetTest {

    private static final long CAP = UploadBudgetLedger.DEFAULT_DEFERRED_CAP_BYTES;

    // ------------------------------------------------------------------ window

    /** The window rolls on a new key: the old key's window no longer reads, the new one starts empty. */
    @Test
    void windowRollsWhenTheKeyChanges() {
        MeshUploadBudget account = new MeshUploadBudget(1000L, CAP);
        account.noteIssued(7L, 500L);
        assertEquals(500L, account.windowBytes(7L));
        assertEquals(0L, account.windowBytes(8L), "a window the key has not opened reads as empty");
        account.noteIssued(8L, 300L);
        assertEquals(300L, account.windowBytes(8L));
        assertEquals(0L, account.windowBytes(7L), "the rolled-away window is gone, not carried over");
    }

    /** The judgment is a pure query against the current window only; asking changes nothing. */
    @Test
    void wouldExceedIsAPureQueryAgainstTheCurrentWindow() {
        MeshUploadBudget account = new MeshUploadBudget(1000L, CAP);
        account.noteIssued(7L, 800L);
        assertTrue(account.wouldExceed(7L, 201L));
        assertFalse(account.wouldExceed(7L, 200L));
        assertFalse(account.wouldExceed(8L, 1000L), "an unopened window reads as empty");
        assertTrue(account.wouldExceed(8L, 1001L));
        assertEquals(800L, account.windowBytes(7L), "the queries charged nothing");
    }

    /**
     * The closed loop the shared ledger never gave the mesh side: bytes issued now are charged at
     * issue time, so the next judgment in the same window reads them.
     */
    @Test
    void issuedChargesAreWhatTheNextJudgmentReads() {
        MeshUploadBudget account = new MeshUploadBudget(1000L, CAP);
        assertFalse(account.wouldExceed(7L, 600L));
        account.noteIssued(7L, 600L);
        assertTrue(account.wouldExceed(7L, 500L), "the first issue's charge is the second judgment's input");
    }

    /** An unlimited budget never exceeds, whatever is charged; the accounting still runs. */
    @Test
    void unlimitedBudgetNeverExceeds() {
        MeshUploadBudget account = new MeshUploadBudget(0L, CAP);
        account.noteIssued(7L, 1L << 40);
        assertFalse(account.wouldExceed(7L, 1L << 40));
        assertEquals(1L << 40, account.windowBytes(7L));
    }

    // ------------------------------------------------------------------ deferral account

    /** A deferral is accounted until its issue settles it; the cumulative totals survive the settle. */
    @Test
    void deferralIsAccountedUntilIssued() {
        MeshUploadBudget account = new MeshUploadBudget(1000L, CAP);
        assertTrue(account.noteDeferred(300L));
        assertEquals(300L, account.deferredOutstandingBytes());
        assertEquals(300L, account.deferredTotalBytes());
        assertEquals(1L, account.deferredCount());
        account.noteDeferredIssued(300L);
        assertEquals(0L, account.deferredOutstandingBytes());
        assertEquals(300L, account.deferredTotalBytes(), "the cumulative totals survive the settle");
        assertEquals(1L, account.deferredCount());
    }

    /** When the cap refuses, nothing is accounted and the refusal itself is the counted fact. */
    @Test
    void capRefusalCountsAndAccountsNothing() {
        MeshUploadBudget account = new MeshUploadBudget(1000L, 50L);
        assertFalse(account.noteDeferred(60L));
        assertEquals(1L, account.deferredCapForce());
        assertEquals(0L, account.deferredOutstandingBytes());
        assertEquals(0L, account.deferredTotalBytes());
        assertEquals(0L, account.deferredCount());
        assertTrue(account.noteDeferred(50L), "a deferral the cap accepts still goes through");
    }

    // ------------------------------------------------------------------ resolution

    /** The byte property wins over the MiB property and over the shared budget. */
    @Test
    void bytePropertyWinsOverMiBPropertyAndTheSharedBudget() {
        withMeshProperties("262144", "64", () -> {
            assertEquals(262144L, MeshUploadBudget.resolveBudgetBytes(999L));
            assertEquals(262144L, MeshUploadBudget.resolve(999L).budgetBytes());
        });
    }

    /** The MiB property applies when the byte property is unset. */
    @Test
    void miBPropertyAppliesWhenTheBytePropertyIsUnset() {
        withMeshProperties(null, "2", () ->
                assertEquals(2L << 20, MeshUploadBudget.resolveBudgetBytes(999L)));
    }

    /** With neither override set, the shared budget stands - the default behavior is unchanged. */
    @Test
    void sharedBudgetStandsWhenNeitherPropertyIsSet() {
        withMeshProperties(null, null, () -> {
            assertEquals(999L, MeshUploadBudget.resolveBudgetBytes(999L));
            assertEquals(999L, MeshUploadBudget.resolve(999L).budgetBytes());
        });
    }

    /** A non-positive override resolves to 0, unlimited, on the shared property's convention. */
    @Test
    void nonPositiveOverridesResolveToUnlimited() {
        withMeshProperties("0", null, () ->
                assertEquals(0L, MeshUploadBudget.resolveBudgetBytes(999L)));
        withMeshProperties("-1", null, () ->
                assertEquals(0L, MeshUploadBudget.resolveBudgetBytes(999L)));
        withMeshProperties(null, "0", () ->
                assertEquals(0L, MeshUploadBudget.resolveBudgetBytes(999L)));
    }

    /** Sets (or clears, on null) both mesh budget properties for one probe, then restores them. */
    private static void withMeshProperties(String bytes, String mib, Runnable probe) {
        String oldBytes = System.getProperty(MeshUploadBudget.BYTES_PROPERTY);
        String oldMib = System.getProperty(MeshUploadBudget.MIB_PROPERTY);
        try {
            setOrClear(MeshUploadBudget.BYTES_PROPERTY, bytes);
            setOrClear(MeshUploadBudget.MIB_PROPERTY, mib);
            probe.run();
        } finally {
            setOrClear(MeshUploadBudget.BYTES_PROPERTY, oldBytes);
            setOrClear(MeshUploadBudget.MIB_PROPERTY, oldMib);
        }
    }

    private static void setOrClear(String name, String value) {
        if (value == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, value);
        }
    }
}
