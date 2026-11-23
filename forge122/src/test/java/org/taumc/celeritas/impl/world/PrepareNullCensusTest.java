package org.taumc.celeritas.impl.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PrepareNullCensusTest {
    @Test
    void classifyCoversEveryReason() {
        // A blank chunk's storage is all null slots; the chunk reason must win over the slot reason.
        assertEquals(PrepareNullCensus.Reason.BLANK_CHUNK,
                PrepareNullCensus.classify(true, 16, 3, true, false));
        assertEquals(PrepareNullCensus.Reason.Y_OUT_OF_RANGE,
                PrepareNullCensus.classify(false, 16, 16, true, false));
        assertEquals(PrepareNullCensus.Reason.Y_OUT_OF_RANGE,
                PrepareNullCensus.classify(false, 16, -1, true, false));
        assertEquals(PrepareNullCensus.Reason.SLOT_ABSENT,
                PrepareNullCensus.classify(false, 16, 3, true, false));
        assertEquals(PrepareNullCensus.Reason.SLOT_EMPTY,
                PrepareNullCensus.classify(false, 16, 3, false, true));
    }

    @Test
    void classifyReturnsNullForBuildableSection() {
        assertNull(PrepareNullCensus.classify(false, 16, 3, false, false));
    }

    @Test
    void classifyForNullBranchNeverReturnsNull() {
        // Inputs that re-read as buildable (a racing chunk fill) still have to land in a bucket.
        assertEquals(PrepareNullCensus.Reason.SLOT_EMPTY,
                PrepareNullCensus.classifyForNullBranch(false, 16, 3, false, false));
        assertEquals(PrepareNullCensus.Reason.BLANK_CHUNK,
                PrepareNullCensus.classifyForNullBranch(true, 16, 3, false, false));
    }

    @Test
    void censusCountsCallsContextsAndReasons() {
        var census = new PrepareNullCensus(0);

        census.recordContext();
        census.recordContext();
        census.recordNull(PrepareNullCensus.Reason.BLANK_CHUNK);
        census.recordNull(PrepareNullCensus.Reason.SLOT_ABSENT);
        census.recordNull(PrepareNullCensus.Reason.SLOT_ABSENT);
        census.recordNull(PrepareNullCensus.Reason.SLOT_EMPTY);
        census.recordNull(PrepareNullCensus.Reason.Y_OUT_OF_RANGE);

        var snapshot = census.snapshot();
        assertEquals(7, snapshot.calls);
        assertEquals(2, snapshot.contexts);
        assertEquals(5, snapshot.nulls());
        assertEquals(1, snapshot.blankChunk);
        assertEquals(2, snapshot.slotAbsent);
        assertEquals(1, snapshot.slotEmpty);
        assertEquals(1, snapshot.yOutOfRange);
        // The split the leg reads: fabricated-from-missing-data vs legitimately empty.
        assertEquals(3, snapshot.legitimateEmpty());
        assertEquals(2, snapshot.suspicious());
    }

    @Test
    void printCadenceIsFirstNullHundredthAndFirstSuspicious() {
        var census = new PrepareNullCensus(24);

        census.recordNull(PrepareNullCensus.Reason.SLOT_EMPTY); // nulls=1: prints
        assertEquals(1, census.printedLines());
        census.recordNull(PrepareNullCensus.Reason.SLOT_EMPTY); // nulls=2: quiet
        assertEquals(1, census.printedLines());
        census.recordNull(PrepareNullCensus.Reason.BLANK_CHUNK); // first suspicious sighting: prints out of cadence
        assertEquals(2, census.printedLines());
        census.recordNull(PrepareNullCensus.Reason.BLANK_CHUNK); // second sighting: quiet again
        assertEquals(2, census.printedLines());

        var hundredth = new PrepareNullCensus(24);
        for (int i = 0; i < 100; i++) {
            hundredth.recordNull(PrepareNullCensus.Reason.SLOT_ABSENT);
        }
        // Prints at nulls=1 and nulls=100 only.
        assertEquals(2, hundredth.printedLines());
    }

    @Test
    void printLineLimitIsAHardCap() {
        var census = new PrepareNullCensus(1);

        for (int i = 0; i < 200; i++) {
            census.recordNull(PrepareNullCensus.Reason.SLOT_EMPTY);
        }

        assertEquals(1, census.printedLines());
        assertEquals(200, census.snapshot().nulls());
    }
}
