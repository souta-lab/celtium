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
    void aSwitchedOffStageIsDisabled() {
        StageGate gate = gateWithRecords(new ArrayList<>(), StageGate.Stage.TERRAIN_PIPELINES);

        assertFalse(gate.enabled(StageGate.Stage.TERRAIN_PIPELINES));
        assertTrue(gate.switchedOff(StageGate.Stage.TERRAIN_PIPELINES));
    }

    @Test
    void aTripQuarantinesImmediatelyAndRecordsExactlyOnce() {
        List<String> records = new ArrayList<>();
        StageGate gate = gateWithRecords(records);
        RuntimeException failure = new RuntimeException("creation died");

        gate.trip(StageGate.Stage.TERRAIN_PIPELINES, failure);
        gate.trip(StageGate.Stage.TERRAIN_PIPELINES, failure);

        assertFalse(gate.enabled(StageGate.Stage.TERRAIN_PIPELINES));
        assertTrue(gate.quarantined(StageGate.Stage.TERRAIN_PIPELINES));
        assertEquals(1, gate.trips(StageGate.Stage.TERRAIN_PIPELINES), "the corpse is never retried, or re-recorded");
        assertEquals(1, records.size(), "one record per quarantine: " + records);
        assertTrue(records.get(0).contains("CELTIUM_STAGE_QUARANTINED"));
        assertTrue(records.get(0).contains("terrainPipelines"), "the record names the stage: " + records.get(0));
        assertTrue(records.get(0).contains("RuntimeException"), "the record names the failure: " + records.get(0));
    }

    @Test
    void aSuccessResetsTheConsecutiveCountButNeverAQuarantine() {
        StageGate gate = gateWithRecords(new ArrayList<>());
        RuntimeException failure = new RuntimeException("submission died");

        gate.noteFailure(StageGate.Stage.TERRAIN_PIPELINES, failure);
        gate.noteFailure(StageGate.Stage.TERRAIN_PIPELINES, failure);
        gate.noteSuccess(StageGate.Stage.TERRAIN_PIPELINES);
        gate.noteFailure(StageGate.Stage.TERRAIN_PIPELINES, failure);
        gate.noteFailure(StageGate.Stage.TERRAIN_PIPELINES, failure);
        assertTrue(gate.enabled(StageGate.Stage.TERRAIN_PIPELINES),
                "terrain failures leave the stage enabled until an explicit trip");
        assertEquals(2, gate.consecutiveFailures(StageGate.Stage.TERRAIN_PIPELINES));

        gate.trip(StageGate.Stage.TERRAIN_PIPELINES, failure);
        gate.noteSuccess(StageGate.Stage.TERRAIN_PIPELINES);
        assertFalse(gate.enabled(StageGate.Stage.TERRAIN_PIPELINES),
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
        StageGate switchedOff = gateWithRecords(new ArrayList<>(), StageGate.Stage.TERRAIN_PIPELINES);
        assertTrue(switchedOff.summary().contains("terrainPipelines=off(kill-switch)"));

        StageGate quarantined = gateWithRecords(new ArrayList<>());
        quarantined.trip(StageGate.Stage.TERRAIN_PIPELINES, new RuntimeException("creation died"));
        String summary = quarantined.summary();
        assertTrue(summary.contains("terrainPipelines=off(quarantined)"), summary);
        assertTrue(summary.contains("trips=1"), summary);
    }
}
