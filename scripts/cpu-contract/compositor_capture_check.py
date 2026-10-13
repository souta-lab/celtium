#!/usr/bin/env python3
"""Exercise compositor_capture.main with real child processes, without Minecraft or a compositor."""
import importlib.util
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

MODULE_PATH = Path(__file__).resolve().parents[1] / "compositor_capture.py"
spec = importlib.util.spec_from_file_location("compositor_capture", MODULE_PATH)
capture = importlib.util.module_from_spec(spec)
spec.loader.exec_module(capture)


class CaptureStatusCheck(unittest.TestCase):
    def run_case(self, code=0, validation=0, gpu=True, metadata=True, grim_ok=True):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            game = root / "game"
            game.mkdir()
            captures = root / "captures"
            launcher = root / "launcher.py"
            launcher.write_text(
                "import json, pathlib, sys, time\n"
                "game = pathlib.Path(sys.argv[sys.argv.index('--game-dir') + 1])\n"
                "(game / 'headless').mkdir()\n"
                "(game / 'headless/display.json').write_text(json.dumps("
                "{'DISPLAY': ':fake', 'WAYLAND_DISPLAY': 'fake', 'SWAYSOCK': '/fake'}))\n"
                + ("(game / 'validation-process.json').write_text(json.dumps("
                   + repr({"validationExitCode": validation, "gpuRequirementMet": gpu,
                           "validationClean": True}) + "))\n" if metadata else "")
                + "time.sleep(0.25)\n"
                + f"sys.exit({code})\n"
            )
            grim = root / "grim"
            grim.write_text("#!/usr/bin/env python3\nimport pathlib, sys\n"
                            + ("pathlib.Path(sys.argv[-1]).write_bytes(b'captured')\n"
                               if grim_ok else "sys.exit(3)\n"))
            grim.chmod(0o755)
            argv = ["compositor_capture.py", "--game-dir", str(game), "--capture-dir", str(captures),
                    "--interval", "0.01", "--startup-timeout", "3", "--",
                    "--game-dir", str(game), "--headless"]
            with patch.object(capture, "LAUNCHER", launcher), patch.object(capture, "ROOT", root), \
                    patch.object(capture.sys, "argv", argv), \
                    patch.dict(os.environ, {"PATH": str(root) + os.pathsep + os.environ["PATH"]}), \
                    patch.object(capture, "sway_query", return_value=None), \
                    patch.object(capture, "game_pid", return_value=None), \
                    patch("builtins.print"):
                result = capture.main()
            summary = json.loads((captures / "captures.json").read_text())["summary"]
            return result, summary

    def test_success(self):
        result, summary = self.run_case()
        self.assertEqual(result, 0)
        self.assertGreater(summary["successfulCaptures"], 0)
        self.assertEqual(summary["captureExitCode"], result)

    def test_launcher_failure_survives_successful_capture(self):
        result, summary = self.run_case(code=7, validation=7)
        self.assertEqual(result, 7)
        self.assertGreater(summary["successfulCaptures"], 0)

    def test_launcher_zero_does_not_hide_validation_failure(self):
        result, summary = self.run_case(validation=1)
        self.assertEqual(result, 1)
        self.assertEqual(summary["validationExitCode"], 1)

    def test_wrong_gpu_cannot_pass(self):
        result, summary = self.run_case(gpu=False)
        self.assertEqual(result, 1)
        self.assertIn("the game did not use the required GPU", summary["failureReasons"])

    def test_missing_metadata_cannot_pass(self):
        result, summary = self.run_case(metadata=False)
        self.assertEqual(result, 1)
        self.assertIsNone(summary["validationExitCode"])

    def test_failed_captures_cannot_pass(self):
        result, summary = self.run_case(grim_ok=False)
        self.assertEqual(result, 1)
        self.assertEqual(summary["successfulCaptures"], 0)

    def test_signal_exit_is_shell_compatible(self):
        result, _, _, _ = capture.run_outcome(Path("/missing"), -15, [])
        self.assertEqual(result, 143)


if __name__ == "__main__":
    unittest.main()
