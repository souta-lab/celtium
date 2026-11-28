package org.soutalab.celtium.core.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * The recorded-list boundary of E5 seat C: the uninstalled answer is "nothing recorded,
 * nothing executing", and the composition root's installation is the only way the answers
 * change.
 */
class RecordedListProbesTest {

    @AfterEach
    void restoreUninstalled() {
        RecordedListProbes.install(null);
    }

    @Test
    void uninstalledProbeAnswersNothingRecordedAndNothingExecuting() {
        RecordedListProbe probe = RecordedListProbes.current();
        assertSame(RecordedListProbe.NONE, probe);
        assertFalse(probe.hasRecordedList(1));
        assertFalse(probe.hasRecordedList(110001));
        assertEquals(0, probe.executingListId());
    }

    @Test
    void installedProbeAnswersFromItsStore() {
        RecordedListProbes.install(new RecordedListProbe() {
            @Override
            public boolean hasRecordedList(int listId) {
                return listId == 7;
            }

            @Override
            public int executingListId() {
                return 7;
            }
        });
        RecordedListProbe probe = RecordedListProbes.current();
        assertTrue(probe.hasRecordedList(7));
        assertFalse(probe.hasRecordedList(8));
        assertEquals(7, probe.executingListId());
    }

    @Test
    void installingNullRestoresTheUninstalledAnswer() {
        RecordedListProbes.install(new RecordedListProbe() {
            @Override
            public boolean hasRecordedList(int listId) {
                return true;
            }

            @Override
            public int executingListId() {
                return 3;
            }
        });
        RecordedListProbes.install(null);
        assertSame(RecordedListProbe.NONE, RecordedListProbes.current());
        assertFalse(RecordedListProbes.current().hasRecordedList(3));
        assertEquals(0, RecordedListProbes.current().executingListId());
    }
}
