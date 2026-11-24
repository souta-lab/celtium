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

    @Test
    void recordAddEmptyCountsReasonsSeparatelyAndPrintsAddEmptyAtLineEnd() {
        // Item 683: sections committed EMPTY at add time never reach prepare, so the add path
        // must land in the same reason buckets (the leg splits the population by reason) while
        // staying separable from prepare-path counts: it must not bump the prepare call count,
        // and the printed line must carry its share at the line end as addEmpty=.
        var census = new PrepareNullCensus(24);

        var originalOut = System.out;
        var captured = new java.io.ByteArrayOutputStream();
        System.setOut(new java.io.PrintStream(captured));
        try {
            census.recordAddEmpty(PrepareNullCensus.Reason.BLANK_CHUNK);
        } finally {
            System.setOut(originalOut);
        }

        var snapshot = census.snapshot();
        assertEquals(0, snapshot.calls);
        assertEquals(1, snapshot.blankChunk);
        assertEquals(1, snapshot.nulls());
        assertEquals(1, census.printedLines());

        var line = captured.toString().strip();
        org.junit.jupiter.api.Assertions.assertTrue(line.startsWith("[Celtium] CELTIUM_PREPARE_NULL calls=0"),
                "unexpected census line: " + line);
        org.junit.jupiter.api.Assertions.assertTrue(line.endsWith(" addEmpty=1"),
                "add-path share must be the line-end addEmpty field: " + line);
    }

    @Test
    void sectionKeyPackRoundTripsIncludingNegatives() {
        long key = PrepareNullCensus.packSectionKey(-1555, 7, 2153);
        org.junit.jupiter.api.Assertions.assertEquals(-1555, PrepareNullCensus.unpackSectionX(key));
        org.junit.jupiter.api.Assertions.assertEquals(7, PrepareNullCensus.unpackSectionY(key));
        org.junit.jupiter.api.Assertions.assertEquals(2153, PrepareNullCensus.unpackSectionZ(key));

        long zero = PrepareNullCensus.packSectionKey(0, 0, 0);
        org.junit.jupiter.api.Assertions.assertEquals(0, PrepareNullCensus.unpackSectionX(zero));
        org.junit.jupiter.api.Assertions.assertEquals(0, PrepareNullCensus.unpackSectionZ(zero));
    }

    @Test
    void sweepCountsLateArrivalOnceAndDropsResolvedKeys() {
        var census = new PrepareNullCensus(24);
        long arrived = PrepareNullCensus.packSectionKey(10, 3, -4);
        long interiorArrived = PrepareNullCensus.packSectionKey(11, 2, -4);
        long stillEmpty = PrepareNullCensus.packSectionKey(12, 3, -4);
        long gone = PrepareNullCensus.packSectionKey(13, 3, -4);

        census.registerAddEmpty(arrived, false);
        census.registerAddEmpty(interiorArrived, true);
        census.registerAddEmpty(stillEmpty, false);
        census.registerAddEmpty(gone, false);
        org.junit.jupiter.api.Assertions.assertEquals(4, census.trackedEmptyCount());

        java.util.Map<Long, Integer> states = java.util.Map.of(
                arrived, 1, interiorArrived, 1, stillEmpty, 0, gone, 2);
        int inspected = census.sweepAddEmpty(512, k -> states.getOrDefault(k, 0));

        org.junit.jupiter.api.Assertions.assertEquals(4, inspected);
        org.junit.jupiter.api.Assertions.assertEquals(2, census.lateArrivalCount());
        org.junit.jupiter.api.Assertions.assertEquals(1, census.lateInteriorCount());
        // Only the still-empty key remains registered; a second sweep must not recount.
        org.junit.jupiter.api.Assertions.assertEquals(1, census.trackedEmptyCount());
        census.sweepAddEmpty(512, k -> 1);
        org.junit.jupiter.api.Assertions.assertEquals(3, census.lateArrivalCount());
        org.junit.jupiter.api.Assertions.assertEquals(0, census.trackedEmptyCount());
    }

    @Test
    void sweepRespectsItsSliceBound() {
        var census = new PrepareNullCensus(0);
        for (int i = 0; i < 10; i++) {
            census.registerAddEmpty(PrepareNullCensus.packSectionKey(i, 1, 0), false);
        }
        org.junit.jupiter.api.Assertions.assertEquals(3, census.sweepAddEmpty(3, k -> 0));
        org.junit.jupiter.api.Assertions.assertEquals(10, census.trackedEmptyCount());
    }
}
