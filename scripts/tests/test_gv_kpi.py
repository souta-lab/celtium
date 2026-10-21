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
