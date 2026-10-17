"""Exercise canonical harness verdict handling with no Java, window or GPU."""
from contextlib import redirect_stdout
import importlib.util
import io
import json
from pathlib import Path
import sys
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch

SCRIPTS = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(SCRIPTS))
spec = importlib.util.spec_from_file_location("prism_validation_verdict", SCRIPTS / "prism_validation.py")
runner = importlib.util.module_from_spec(spec)
spec.loader.exec_module(runner)


class CompletedProcess:
    def __init__(self, *args, **kwargs):
        kwargs["stdout"].write(b'[Celtium] CELTIUM_NATIVE_DEVICE name="Intel(R) UHD Graphics 630 (CFL GT2)" uuid=abcd matchedOpenGl=false\n')

    def wait(self, timeout=None):
        return 0

    def poll(self):
        return 0


class PrismVerdictTests(unittest.TestCase):
    def exercise(self, status, validation_error=False, require_gpu="UHD Graphics 630"):
        with tempfile.TemporaryDirectory() as tmp:
            game = Path(tmp)
            (game / "logs").mkdir()
            (game / "screenshots").mkdir()
            (game / "screenshots/presented.png").write_bytes(b"\x89PNG\r\n\x1a\n" + b"x" * 32)
            fixture = {"status": status, "pictureEvidenceSource": "presented-dump",
                       "pictureFile_world": "screenshots/presented.png"}
            (game / "logs/celtium-validation.json").write_text(json.dumps(fixture))
            log = "Celtium Vulkan validation: severity=ERROR id=VUID-test bad lifetime\n" if validation_error else ""
            (game / "logs/latest.log").write_text(log)
            args = SimpleNamespace(validate=True, window_size="1280x720", jvm_arg=[], headless=False,
                                   force_x11=False, duration=5, require_gpu=require_gpu)
            with patch.object(runner.subprocess, "Popen", CompletedProcess), redirect_stdout(io.StringIO()):
                code = runner.run_locked(args, game, [], "OfflineFixture", [], [], Path("/not-invoked/java"),
                                         game / "assets", "test", None)
            outcome = json.loads((game / "validation-process.json").read_text())
            return code, outcome

    def test_accepted_fixture_stays_zero(self):
        code, outcome = self.exercise("PASSED")
        self.assertEqual(code, 0)
        self.assertEqual(outcome["validationExitCode"], 0)

    def test_uncalibrated_is_distinct_nonzero_not_accepted(self):
        code, outcome = self.exercise("UNCALIBRATED_PRESENTED_IMAGE")
        self.assertEqual(code, 2)
        self.assertEqual(outcome["exitCode"], 0)  # raw JVM code is still truthful
        self.assertEqual(outcome["validationExitCode"], 2)
        self.assertTrue(outcome["fixtureUncalibrated"])

    def test_actual_validation_failure_precedes_uncalibrated(self):
        code, outcome = self.exercise("UNCALIBRATED_PRESENTED_IMAGE", validation_error=True)
        self.assertEqual(code, 1)
        self.assertFalse(outcome["validationClean"])
        self.assertEqual(outcome["validationExitCode"], 1)

    def test_wrong_gpu_precedes_uncalibrated(self):
        code, outcome = self.exercise("UNCALIBRATED_PRESENTED_IMAGE", require_gpu="RX 6400")
        self.assertEqual(code, 1)
        self.assertFalse(outcome["gpuRequirementMet"])

    def test_failed_fixture_remains_failed(self):
        code, outcome = self.exercise("FAILED_PRESENTED_IMAGE")
        self.assertEqual(code, 1)
        self.assertEqual(outcome["validationExitCode"], 1)


if __name__ == "__main__":
    unittest.main()
