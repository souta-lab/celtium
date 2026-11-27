"""Positive and negative controls for the loss-counter gate (drawpath audit X-1).

The synthetic logs are the gate's script-level positive controls: a nonzero aggregate, a
stalled device, present failures, native-pass refusals and batch-route losses must each turn
into a problem, a clean run must produce none, and evidence the log limiter cut must read as
unproven rather than clean.
"""
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import loss_counter_gate


def verify_line(**overrides):
    parts = {
        "lossAggregate": "[postAcceptDroppedGroups=0,orderingFlushFailures=0,"
                         "orderingFlushDroppedBatches=0,stalledGeometryDrops=0,"
                         "stalledGeometryDroppedBatches=0,unflushedTargetGroupsDropped=0,"
                         "windowFlushFailures=0,staleGroupRefusals=0,total=0]",
        "gpuStalled": "false",
        "swapPointExceptions": "0",
        "logLimitSuppressed": "0",
    }
    parts.update(overrides)
    body = " ".join(f"{name}={value}" for name, value in parts.items())
    return f"[Celtium] CELTIUM_VERIFY via=shutdown celtium/device[passes=3] {body}"


CLEAN_LOG = "\n".join([
    verify_line(),
    "[Celtium] CELTIUM_PRESENT hookCalls=300 attempts=300 presented=299 failures=0 "
    "drainFailures=0 drainUnfinished=0 skips=1",
    "[Celtium] CELTIUM_NATIVE_PASS via=shutdown registered=4 compiled=4 compileRefusals=0 "
    "executed=120 execRefusals=0 lastFailure=[none]",
    "[Celtium] CELTIUM_BATCH_ROUTE captured=50 arrayCaptured=50 fallback=0 droppedDraws=0 "
    "discardedSegments=0 batcherDropped=0",
])


class LossCounterGateTest(unittest.TestCase):
    def test_clean_log_passes(self):
        problems, breakdown = loss_counter_gate.check(CLEAN_LOG)
        self.assertEqual([], problems)
        self.assertEqual(0, breakdown["lossAggregateTotal"])
        self.assertFalse(breakdown["gpuStalled"])
        self.assertEqual(1, breakdown["presentSkips"])

    def test_nonzero_aggregate_fails(self):
        log = verify_line(lossAggregate="[postAcceptDroppedGroups=2,orderingFlushFailures=0,"
                                        "orderingFlushDroppedBatches=0,stalledGeometryDrops=0,"
                                        "stalledGeometryDroppedBatches=0,"
                                        "unflushedTargetGroupsDropped=0,windowFlushFailures=0,"
                                        "staleGroupRefusals=0,total=2]")
        problems, breakdown = loss_counter_gate.check(log)
        self.assertEqual(2, breakdown["lossAggregateTotal"])
        self.assertTrue(any("loss aggregate is nonzero" in p for p in problems))

    def test_gpu_stalled_fails(self):
        problems, _ = loss_counter_gate.check(verify_line(gpuStalled="true"))
        self.assertTrue(any("gpuStalled=true" in p for p in problems))

    def test_swap_point_exceptions_fail(self):
        problems, _ = loss_counter_gate.check(verify_line(swapPointExceptions="3"))
        self.assertTrue(any("swapPointExceptions=3" in p for p in problems))

    def test_present_failures_fail_but_skips_do_not(self):
        log = "\n".join([
            verify_line(),
            "[Celtium] CELTIUM_PRESENT hookCalls=300 attempts=300 presented=290 failures=2 "
            "drainFailures=1 drainUnfinished=0 skips=9",
        ])
        problems, breakdown = loss_counter_gate.check(log)
        self.assertTrue(any("present failures" in p for p in problems))
        self.assertTrue(any("present drain failures" in p for p in problems))
        self.assertFalse(any("skips" in p for p in problems))
        self.assertEqual(9, breakdown["presentSkips"])

    def test_native_pass_refusals_fail(self):
        log = "\n".join([
            verify_line(),
            "[Celtium] CELTIUM_NATIVE_PASS via=shutdown registered=4 compiled=3 "
            "compileRefusals=1 executed=90 execRefusals=2 lastFailure=[bloom: state]",
        ])
        problems, _ = loss_counter_gate.check(log)
        self.assertTrue(any("compileRefusals=1" in p for p in problems))
        self.assertTrue(any("execRefusals=2" in p for p in problems))

    def test_batch_route_losses_fail(self):
        log = "\n".join([
            verify_line(),
            "[Celtium] CELTIUM_BATCH_ROUTE captured=50 arrayCaptured=48 fallback=2 "
            "droppedDraws=5 discardedSegments=1 batcherDropped=0",
        ])
        problems, breakdown = loss_counter_gate.check(log)
        self.assertTrue(any("droppedDraws=5" in p for p in problems))
        self.assertTrue(any("discardedSegments=1" in p for p in problems))
        self.assertEqual(5, breakdown["batchDroppedDraws"])

    def test_batch_keys_on_the_verify_line_fail(self):
        line = verify_line() + " batchDroppedDraws=4 batchDiscardedSegments=0"
        problems, _ = loss_counter_gate.check(line)
        self.assertTrue(any("batchDroppedDraws=4" in p for p in problems))

    def test_missing_verify_line_is_unproven(self):
        problems, breakdown = loss_counter_gate.check("some other log\n")
        self.assertTrue(any("unproven" in p for p in problems))
        self.assertFalse(breakdown["verifyLine"])

    def test_verify_line_without_aggregate_is_unproven(self):
        problems, _ = loss_counter_gate.check(
            "[Celtium] CELTIUM_VERIFY via=shutdown celtium/device[passes=3] gpuStalled=false")
        self.assertTrue(any("no lossAggregate" in p for p in problems))

    def test_suppressed_verify_tag_is_unproven(self):
        log = verify_line(logLimitSuppressed="17",
                          logLimitSuppressedByTag="[CELTIUM_VERIFY=9,CELTIUM_PRESENT=8]")
        problems, breakdown = loss_counter_gate.check(log)
        self.assertTrue(breakdown["verifySuppressed"])
        self.assertTrue(any("unproven" in p for p in problems))

    def test_log_limit_line_naming_verify_is_unproven(self):
        log = "\n".join([
            verify_line(),
            "[Celtium] CELTIUM_LOG_LIMIT suppressed 25 lines in 2 tags after 16 each "
            "(-Dceltium.debug.periodicLogs=true prints all): CELTIUM_VERIFY=20 CELTIUM_FONT=5",
        ])
        problems, breakdown = loss_counter_gate.check(log)
        self.assertTrue(breakdown["verifySuppressed"])
        self.assertEqual(25, breakdown["logLimitSuppressed"])
        self.assertTrue(any("unproven" in p for p in problems))

    def test_last_verify_line_wins(self):
        log = "\n".join([
            verify_line(gpuStalled="true"),
            verify_line(),
        ])
        problems, _ = loss_counter_gate.check(log)
        self.assertEqual([], problems)


# The follow-up wiring (landing checklist section 5): the loss keys the other drawpath
# bundles put on the verification line. FOLLOWUP_ZERO is the healthy shape of every one of
# them; each case below flips exactly one and expects the gate to name it.
FOLLOWUP_ZERO = (
    "postChainGateRefusals=0 postChainCreationFailures=0 postChainBorrowRefusals=0 "
    "postChainUnsupportedRefusals=0 chainInputFlushFailures=0 framebufferHintFailures=0 "
    "nativeCompositeRefusals=0 modRefusals=0 "
    "modRefusalsByReason[lab-switch=0 no-program=0 unsupported-format=0] "
    "nativePassExecRefusals=0 nativePassCompileRefusals=0 "
    "stageGate[terrain=on failures=0 trips=0 post-chain=on failures=0 trips=0] "
    "celtium/offerDeaths[swallowed=0 noData=0 noAddress=0] "
    "celtium/nativeDrawSkips[stalled=0 refused=0] "
    "celtium/noCameraSkips[flagTrue=0 worldSkips=0 maxConsecutiveWorldSkips=0 hudSkips=0] "
    "modelRunDropped=0 modelInstancesFailed=0 modelMissingMeshes=0 "
    "fontRetainedRuns=0 fontRetainedQuads=0 fontRetainedDroppedRuns=0 "
    "fontRetainedDroppedQuads=0 fontRetainedBoundaryDroppedRuns=0 "
    "fontRetainedBoundaryDroppedQuads=0 fontRetainedReofferedRuns=0 "
    "fontRetainedReofferedQuads=0 legacyFontWindowSuppressed=0 "
    "packTerrainRefused=0 packTerrainSkippedRegions=0 offTargetRedirects=0 "
    "totalPostAcceptDrops=0"
)


def followup_line(**flips):
    line = verify_line() + " " + FOLLOWUP_ZERO
    for token, replacement in flips.items():
        assert token in line, token
        line = line.replace(token, replacement)
    return line


class FollowupWiringTest(unittest.TestCase):
    def test_followup_keys_all_zero_passes(self):
        problems, breakdown = loss_counter_gate.check(followup_line())
        self.assertEqual([], problems)
        self.assertEqual(0, breakdown["postChainGateRefusals"])
        self.assertEqual(0, breakdown["stageGateSum"])
        self.assertEqual(0, breakdown["celtium/offerDeathsSum"])
        self.assertEqual(0, breakdown["totalPostAcceptDrops"])

    def test_followup_scalar_losses_fail(self):
        cases = {
            "postChainGateRefusals=0": "postChainGateRefusals=2",
            "postChainCreationFailures=0": "postChainCreationFailures=1",
            "postChainBorrowRefusals=0": "postChainBorrowRefusals=1",
            "postChainUnsupportedRefusals=0": "postChainUnsupportedRefusals=1",
            "chainInputFlushFailures=0": "chainInputFlushFailures=3",
            "framebufferHintFailures=0": "framebufferHintFailures=1",
            "nativeCompositeRefusals=0": "nativeCompositeRefusals=1",
            "modRefusals=0": "modRefusals=4",
            "nativePassExecRefusals=0": "nativePassExecRefusals=1",
            "nativePassCompileRefusals=0": "nativePassCompileRefusals=2",
            "modelRunDropped=0": "modelRunDropped=1",
            "modelInstancesFailed=0": "modelInstancesFailed=2",
            "modelMissingMeshes=0": "modelMissingMeshes=1",
            "fontRetainedDroppedRuns=0": "fontRetainedDroppedRuns=1",
            "fontRetainedDroppedQuads=0": "fontRetainedDroppedQuads=5",
            "fontRetainedBoundaryDroppedRuns=0": "fontRetainedBoundaryDroppedRuns=1",
            "fontRetainedBoundaryDroppedQuads=0": "fontRetainedBoundaryDroppedQuads=2",
            "packTerrainRefused=0": "packTerrainRefused=7",
            "packTerrainSkippedRegions=0": "packTerrainSkippedRegions=3",
            "offTargetRedirects=0": "offTargetRedirects=1",
            "totalPostAcceptDrops=0": "totalPostAcceptDrops=6",
        }
        for token, replacement in cases.items():
            with self.subTest(replacement=replacement):
                problems, _ = loss_counter_gate.check(followup_line(**{token: replacement}))
                key = replacement.split("=")[0]
                self.assertTrue(any(key in p for p in problems),
                                f"{replacement} produced no problem: {problems}")

    def test_followup_bracket_losses_fail(self):
        cases = {
            "stageGate[terrain=on failures=0 trips=0":
                "stageGate[terrain=on failures=1 trips=0",
            "post-chain=on failures=0 trips=0":
                "post-chain=on failures=0 trips=2",
            "modRefusalsByReason[lab-switch=0":
                "modRefusalsByReason[lab-switch=3",
            "celtium/offerDeaths[swallowed=0":
                "celtium/offerDeaths[swallowed=2",
            "celtium/nativeDrawSkips[stalled=0 refused=0":
                "celtium/nativeDrawSkips[stalled=0 refused=1",
            "celtium/noCameraSkips[flagTrue=0 worldSkips=0":
                "celtium/noCameraSkips[flagTrue=0 worldSkips=4",
        }
        for token, replacement in cases.items():
            with self.subTest(replacement=replacement):
                problems, _ = loss_counter_gate.check(followup_line(**{token: replacement}))
                self.assertTrue(any("bracket sums" in p for p in problems),
                                f"{replacement} produced no bracket problem: {problems}")

    def test_followup_census_keys_are_report_only(self):
        line = followup_line(**{
            "fontRetainedRuns=0": "fontRetainedRuns=12",
            "fontRetainedQuads=0": "fontRetainedQuads=340",
            "fontRetainedReofferedRuns=0": "fontRetainedReofferedRuns=3",
            "fontRetainedReofferedQuads=0": "fontRetainedReofferedQuads=30",
            "legacyFontWindowSuppressed=0": "legacyFontWindowSuppressed=8",
        })
        problems, breakdown = loss_counter_gate.check(line)
        self.assertEqual([], problems)
        self.assertEqual(12, breakdown["fontRetainedRuns"])
        self.assertEqual(340, breakdown["fontRetainedQuads"])
        self.assertEqual(8, breakdown["legacyFontWindowSuppressed"])


if __name__ == "__main__":
    unittest.main()
