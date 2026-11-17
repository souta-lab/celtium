package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The fault-injection pattern inherited from Vulcanizator (survey V-01), at the seam celtium's offline suite
 * can arm: the stage gate itself. A stage that is switched off or quarantined must answer disabled, its
 * fallback must be the caller's, and no failure of one stage may touch another.
 */
class StageGateTest {

    private static StageGate gateWithRecords(List<String> records, StageGate.Stage... off) {
        StageGate gate = StageGate.withSwitchedOff(
                off.length == 0 ? EnumSet.noneOf(StageGate.Stage.class) : EnumSet.of(off[0], off));
        gate.setDiagnostics(records::add);
        return gate;
    }

    @Test
    void aSwitchedOffStageIsDisabledAndTheOthersAreNot() {
        StageGate gate = gateWithRecords(new ArrayList<>(), StageGate.Stage.POST_CHAIN);

        assertFalse(gate.enabled(StageGate.Stage.POST_CHAIN));
        assertTrue(gate.switchedOff(StageGate.Stage.POST_CHAIN));
        assertTrue(gate.enabled(StageGate.Stage.TERRAIN_PIPELINES),
                "a kill switch is per-stage: terrain is not its neighbour's switch");
    }

    @Test
    void aTripQuarantinesImmediatelyAndRecordsExactlyOnce() {
        List<String> records = new ArrayList<>();
        StageGate gate = gateWithRecords(records);
        RuntimeException failure = new RuntimeException("creation died");

        gate.trip(StageGate.Stage.POST_CHAIN, failure);
        gate.trip(StageGate.Stage.POST_CHAIN, failure);

        assertFalse(gate.enabled(StageGate.Stage.POST_CHAIN));
        assertTrue(gate.quarantined(StageGate.Stage.POST_CHAIN));
        assertEquals(1, gate.trips(StageGate.Stage.POST_CHAIN), "the corpse is never retried, or re-recorded");
        assertEquals(1, records.size(), "one record per quarantine: " + records);
        assertTrue(records.get(0).contains("CELTIUM_STAGE_QUARANTINED"));
        assertTrue(records.get(0).contains("postChain"), "the record names the stage: " + records.get(0));
        assertTrue(records.get(0).contains("RuntimeException"), "the record names the failure: " + records.get(0));
    }

    @Test
    void consecutiveFailuresTripAtTheStageThreshold() {
        List<String> records = new ArrayList<>();
        StageGate gate = gateWithRecords(records);
        RuntimeException failure = new RuntimeException("submission died");

        gate.noteFailure(StageGate.Stage.POST_CHAIN, failure);
        gate.noteFailure(StageGate.Stage.POST_CHAIN, failure);
        assertTrue(gate.enabled(StageGate.Stage.POST_CHAIN),
                "two consecutive failures are still below the post chain's threshold of three");

        gate.noteFailure(StageGate.Stage.POST_CHAIN, failure);
        assertFalse(gate.enabled(StageGate.Stage.POST_CHAIN));
        assertEquals(3, gate.failures(StageGate.Stage.POST_CHAIN));
        assertEquals(1, records.size());
    }

    @Test
    void aSuccessResetsTheConsecutiveCountButNeverAQuarantine() {
        StageGate gate = gateWithRecords(new ArrayList<>());
        RuntimeException failure = new RuntimeException("submission died");

        gate.noteFailure(StageGate.Stage.POST_CHAIN, failure);
        gate.noteFailure(StageGate.Stage.POST_CHAIN, failure);
        gate.noteSuccess(StageGate.Stage.POST_CHAIN);
        gate.noteFailure(StageGate.Stage.POST_CHAIN, failure);
        gate.noteFailure(StageGate.Stage.POST_CHAIN, failure);
        assertTrue(gate.enabled(StageGate.Stage.POST_CHAIN),
                "the success broke the run: two + success + two never reaches three in a row");
        assertEquals(2, gate.consecutiveFailures(StageGate.Stage.POST_CHAIN));

        gate.trip(StageGate.Stage.POST_CHAIN, failure);
        gate.noteSuccess(StageGate.Stage.POST_CHAIN);
        assertFalse(gate.enabled(StageGate.Stage.POST_CHAIN),
                "a quarantine is for the session: a later success does not resurrect the stage");
    }

    @Test
    void terrainFailuresAreCountedButNeverAutoTrip() {
        StageGate gate = gateWithRecords(new ArrayList<>());

        for (int i = 0; i < 10; i++) {
            gate.noteFailure(StageGate.Stage.TERRAIN_PIPELINES, new RuntimeException("pass " + i));
        }

        assertTrue(gate.enabled(StageGate.Stage.TERRAIN_PIPELINES),
                "terrain's per-pass remembered failure is the finer instrument; the gate only counts here");
        assertEquals(10, gate.failures(StageGate.Stage.TERRAIN_PIPELINES));
        assertEquals(0, gate.trips(StageGate.Stage.TERRAIN_PIPELINES));
    }

    @Test
    void theSummaryNamesEveryStageAndItsState() {
        StageGate gate = gateWithRecords(new ArrayList<>(), StageGate.Stage.TERRAIN_PIPELINES);
        gate.trip(StageGate.Stage.POST_CHAIN, new RuntimeException("creation died"));

        String summary = gate.summary();
        assertTrue(summary.contains("terrainPipelines=off(kill-switch)"), summary);
        assertTrue(summary.contains("postChain=off(quarantined)"), summary);
        assertTrue(summary.contains("trips=1"), summary);
    }
}
