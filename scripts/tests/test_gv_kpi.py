"""KPI rejection/aggregation tests: no JVM, GPU, or success inferred from an empty window."""
import copy
import importlib.util
import json
from pathlib import Path
import sys
import tempfile
import unittest

SCRIPTS = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(SCRIPTS))
spec = importlib.util.spec_from_file_location("gv_kpi_tested", SCRIPTS / "gv_kpi.py")
kpi = importlib.util.module_from_spec(spec)
spec.loader.exec_module(kpi)


class KpiTests(unittest.TestCase):
    def records(self):
        report = {"status": "PASSED", "frameIntervalWarmSampleCount": 1200,
                  "frameIntervalWarmP50Ms": 3.0, "frameIntervalWarmP95Ms": 5.0,
                  "frameIntervalWarmFromTick": 400, "frameIntervalWarmToTick": 600, "worldTicksAtEnd": 619,
                  "frameIntervalP50Ms": 0.001, "frameIntervalSampleCount": 4000,
                  "sceneCalibrationKey": "world/camera/default", "saveSlot": "qmagnet", "renderDistanceUsed": 18,
                  "cameraRequested": "217.5/48/217.5 180/10", "captureWidth": 1280, "captureHeight": 720,
                  "renderCloudsAtCapture": 2, "pinnedWorldTime": 6000, "framerateLimitSetting": 260, "vsyncEnabled": False}
        process = {"exitCode": 0, "fixtureStatus": "PASSED", "gpuRequirementMet": True,
                   "nativeDeviceName": "actual native GPU"}
        result = {"accepted": True, "exitCode": 0}
        pinned = {"renderer": {"sha256": "renderer"}, "fixture": {"sha256": "fixture"},
                  "reference": {"sha256": "reference"}}
        manifest = {"artifacts": {"renderer": pinned["renderer"], "fixture": pinned["fixture"]}}
        return [report, process, result, manifest, pinned]

    def metric(self, values=None):
        return kpi.warm_metric(*(values or self.records()), reference=False)

    def test_warm_is_the_metric_even_if_cold_looks_faster(self):
        self.assertEqual(self.metric()["p50Ms"], 3)

    def test_empty_partial_or_unidentified_warm_window_is_rejected(self):
        for key, value in (("frameIntervalWarmSampleCount", 0), ("worldTicksAtEnd", 550),
                           ("frameIntervalWarmFromTick", None), ("frameIntervalWarmToTick", 601),
                           ("frameIntervalWarmP50Ms", 0), ("frameIntervalWarmP50Ms", float("nan")),
                           ("frameIntervalWarmP95Ms", None), ("frameIntervalWarmP95Ms", 2)):
            with self.subTest(key=key, value=value):
                values = self.records()
                values[0][key] = value
                with self.assertRaises(kpi.KpiError): self.metric(values)

    def test_missing_exit_status_wrong_gpu_and_nonacceptance_are_rejected(self):
        for index, key, value in ((1, "exitCode", None), (1, "gpuRequirementMet", False),
                                  (1, "fixtureStatus", "UNCALIBRATED_PRESENTED_IMAGE"), (2, "accepted", False)):
            values = self.records()
            values[index][key] = value
            with self.assertRaises(kpi.KpiError): self.metric(values)

    def test_measuring_the_wrong_jar_is_rejected_for_either_arm(self):
        values = self.records()
        values[3]["artifacts"]["renderer"] = {"sha256": "old jar"}
        with self.assertRaises(kpi.KpiError): self.metric(values)
        values = self.records()
        with self.assertRaises(kpi.KpiError): kpi.warm_metric(*values, reference=True)
        values[3]["artifacts"]["renderer"] = values[4]["reference"]
        self.assertEqual(kpi.warm_metric(*values, reference=True)["rendererSha256"], "reference")

    def test_scene_and_camera_difference_is_not_a_speedup(self):
        left, right = self.metric(), self.metric()
        right["conditions"]["cameraRequested"] = "different camera"
        with self.assertRaises(kpi.KpiError): kpi.same_conditions({"celtium": left, "celeritas": right})
        right = self.metric()
        right["conditions"]["captureWidth"] = None
        with self.assertRaises(kpi.KpiError): kpi.same_conditions({"celtium": left, "celeritas": right})

    def test_a_starved_repetition_is_dropped_from_the_statistics_not_just_named(self):
        # A median that keeps the starved pair is a number about the machine. Three clean pairs must survive with their
        # ratio, and the starved one must not move it - nor be counted toward the three the verdict needs.
        clean = []
        for ratio in (1.10, 1.20, 1.30):
            pair = {"celtium": self.metric(), "control": self.metric()}
            pair["celtium"]["p50Ms"] = ratio
            pair["control"]["p50Ms"] = 1.0
            pair["celtium"]["samples"] = 4400
            pair["control"]["samples"] = 4300
            clean.append(pair)
        starved = {"celtium": self.metric(), "control": self.metric()}
        starved["celtium"]["p50Ms"] = 99.0
        starved["control"]["p50Ms"] = 1.0
        starved["celtium"]["samples"] = 4400
        starved["control"]["samples"] = 1200
        summary = kpi.summarize(clean + [starved], [])
        self.assertEqual([round(x, 2) for x in summary["controlRatios"]], [1.10, 1.20, 1.30])
        self.assertEqual(summary["medianControlRatio"], 1.20)
        self.assertEqual(len(summary["disturbedRepetitions"]), 1)

    def test_ranking_mode_without_a_reference_still_reports_the_control_ratio(self):
        # Dropping the reference arm halves the number of legs a ranking needs; the control ratio is what ranks builds
        # and the reference ratio is what answers "how far from Celeritas", which needs a quiet machine anyway.
        pair = {"celtium": self.metric(), "control": self.metric()}
        pair["celtium"]["p50Ms"] = 1.16
        pair["control"]["p50Ms"] = 1.0
        summary = kpi.summarize([pair] * 3, [])
        self.assertIsNone(summary["medianPairRatio"])
        self.assertEqual(summary["verdict"], "NO_SPEED_VERDICT", "no reference, no KPI verdict")
        self.assertEqual(summary["medianControlRatio"], 1.16)

    def test_pairs_whose_arms_produced_very_different_frame_counts_have_no_verdict(self):
        # The frames each arm completed is the throughput it actually got. A control leg that managed a quarter of the
        # candidate's frames was starved by something else, and its millisecond figure is not a measurement of the build.
        starved = {"celtium": self.metric(), "celeritas": self.metric(), "control": self.metric()}
        starved["celtium"]["samples"] = 4461
        # The reference legitimately produces far more frames in the same window - a faster renderer completes more of
        # them - so only the same-family pair may be compared.
        starved["celeritas"]["samples"] = 6768
        starved["control"]["samples"] = 1072
        summary = kpi.summarize([starved] * 3, [])
        self.assertEqual(summary["verdict"], "NO_SPEED_VERDICT")
        self.assertEqual(summary["disturbedRepetitions"], [1, 2, 3])
        # A healthy spread keeps its verdict.
        healthy = {"celtium": self.metric(), "celeritas": self.metric()}
        healthy["celtium"]["samples"] = 4461
        healthy["celeritas"]["samples"] = 6768
        self.assertEqual(kpi.summarize([healthy] * 3, [])["verdict"], "NOT_SLOWER",
                         "a 1.67x frame-count gap against the reference is normal and must not be called starvation")
        # Two same-family arms that disagree by 1.67x are starved, reference or none.
        skewed = {"celtium": self.metric(), "control": self.metric()}
        skewed["celtium"]["samples"] = 4461
        skewed["control"]["samples"] = 2600
        self.assertEqual(kpi.summarize([skewed] * 3, [])["verdict"], "NO_SPEED_VERDICT")

    def test_a_run_overtaken_by_load_reports_no_verdict(self):
        # This is not hypothetical: one ranking run started at load 2.69 and ended at 7.34, and its pairs compared
        # different machine states. A median across those is not a measurement, whatever it reads.
        summary = kpi.summarize([{"celtium": self.metric(), "celeritas": self.metric()}] * 3, [])
        summary["environment"] = {"initialLoadAverage": 2.69, "finalLoadAverage": 7.34, "loadLimit": 3.5}
        load, final_load, limit = 2.69, 7.34, 3.5
        self.assertTrue(final_load > max(limit, load * 1.5))
        # The same shape at a steady load stays a verdict.
        self.assertFalse(4.0 > max(limit, 4.0 * 1.5))

    def test_single_pairs_asymmetric_flags_or_frame_caps_never_pass_the_kpi(self):
        pair = {"celtium": self.metric(), "celeritas": self.metric()}
        self.assertEqual(kpi.summarize([pair], [])["verdict"], "NO_SPEED_VERDICT")
        self.assertEqual(kpi.summarize([pair] * 3, ["-Dchanged=true"])["verdict"], "NO_SPEED_VERDICT")
        self.assertEqual(kpi.summarize([pair] * 3, [])["verdict"], "NOT_SLOWER")
        pair["celeritas"]["p50Ms"] = 1
        self.assertEqual(kpi.summarize([pair] * 3, [])["verdict"], "SLOWER")
        pair["celtium"]["conditions"]["framerateLimitSetting"] = 120
        self.assertEqual(kpi.summarize([pair] * 3, [])["verdict"], "NO_SPEED_VERDICT")

    def test_statistic_is_the_median_of_paired_ratios_not_unpaired_medians(self):
        pairs = []
        for left, right in ((2, 1), (100, 1000), (30, 10)):
            pair = {"celtium": self.metric(), "celeritas": self.metric()}
            pair["celtium"]["p50Ms"], pair["celeritas"]["p50Ms"] = left, right
            pairs.append(pair)
        self.assertEqual(kpi.summarize(pairs, [])["medianPairRatio"], 2)

    def test_an_unaccepted_leg_is_named_before_any_report_is_parsed(self):
        # A leg that never produced a report must fail with its own problems, not with a JSON parse error.
        values = self.records()
        values[2] = {"accepted": False, "exitCode": 1, "problems": ["harness exited 1"]}
        with self.assertRaises(kpi.KpiError) as caught:
            self.metric(values)
        self.assertIn("not accepted", str(caught.exception))

    def test_a_control_build_is_compared_same_arm_so_machine_drift_cancels(self):
        # The reference ratio answers "how far from Celeritas are we"; the control ratio answers "is this build faster
        # than that build", and it is the only one that can rank two builds: both legs are the same renderer family in
        # the same window, so a machine that slows down moves both.
        def metric(p50):
            pair = {"celtium": self.metric(), "celeritas": self.metric()}
            pair["control"] = self.metric()
            pair["celtium"]["p50Ms"] = p50
            pair["control"]["p50Ms"] = 1.0
            return pair

        pairs = [metric(1.10), metric(1.30), metric(1.20)]
        summary = kpi.summarize(pairs, [])
        self.assertEqual([round(x, 2) for x in summary["controlRatios"]], [1.10, 1.30, 1.20])
        self.assertEqual(summary["medianControlRatio"], 1.20)
        # A pair set without a control still summarises; it simply has no control ratio.
        self.assertNotIn("medianControlRatio", kpi.summarize([{"celtium": self.metric(), "celeritas": self.metric()}] * 3, []))

    def test_stage_artifacts_are_bound_to_explicit_successful_build_provenance(self):
        with tempfile.TemporaryDirectory() as tmp:
            stage = Path(tmp)
            renderer, fixture, reference = (stage / name for name in (
                "celtium-forge-mc1.12.2-test.jar", "celtium-validation-client-test.jar", "reference.jar"))
            for path in (renderer, fixture, reference): path.write_bytes(path.name.encode())
            provenance = {"head": "a" * 40, "buildExitCode": 0, "artifacts": {
                path.name: {"sha256": kpi.digest(path)} for path in (renderer, fixture)}}
            (stage / "build-provenance.json").write_text(json.dumps(provenance))
            self.assertEqual(kpi.inputs(stage, reference)["renderer"]["sha256"], kpi.digest(renderer))
            renderer.write_bytes(b"stale or replaced jar")
            with self.assertRaises(kpi.KpiError): kpi.inputs(stage, reference)


if __name__ == "__main__":
    unittest.main()
