"""The per-leg device record after the probe retirement (item 375): read what the run wrote, name what it does not.

No JVM, window, GPU or Gradle: the fixture's own report is written as JSON into a temporary game directory and the
runner's post-run block is exercised through `run_locked`, the same way `test_prism_verdict.py` does it.
"""
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
spec = importlib.util.spec_from_file_location("prism_validation_device", SCRIPTS / "prism_validation.py")
runner = importlib.util.module_from_spec(spec)
spec.loader.exec_module(runner)


class CompletedProcess:
    """A process that exits zero without a device line: this file is about the report, not the console."""

    def __init__(self, *args, **kwargs):
        pass

    def wait(self, timeout=None):
        return 0

    def poll(self):
        return 0


# A GL-less run, as the bootstrap writes it: `matched` present, `openGl` answered by the stub (no device UUIDs).
GL_LESS = {
    "schemaVersion": 1,
    "scope": "device-bootstrap-only",
    "effectiveTerrainBackend": "vulkan",
    "nativeTerrainImplemented": True,
    "terrainBackendSource": "live renderer: native Vulkan terrain",
    "status": "DEVICE_READY",
    "lwjglVersion": "3.4.3",
    "loaderVersion": "1.3.290",
    "openGl": {"vendor": "Celtium", "renderer": "celtium-gl-stub", "version": "4.6", "deviceUuids": []},
    "matched": {
        "name": "AMD Radeon RX 6400 (RADV NAVI24)",
        "deviceUuid": "aaaabbbbccccdddd0000111122223333",
        "driverUuid": "11112222333344445555666677778888",
        "apiVersion": "1.3.280",
        "deviceType": 2,
        "deviceTypeName": "DISCRETE_GPU",
        "uuidMatchesOpenGl": False,
        "driverUuidMatchesOpenGl": False,
        "queueFamilyIndex": 1,
        "queueCount": 1,
        "graphicsQueue": True,
        "transferQueue": True,
    },
    "logicalDeviceCreated": True,
    "reasons": [],
}

# The same bootstrap in a process whose GL context exposes a device UUID, and one that matches.
GL_BRIDGE = json.loads(json.dumps(GL_LESS))
GL_BRIDGE["openGl"] = {"vendor": "AMD", "renderer": "AMD Radeon RX 6400 (RADV NAVI24)", "version": "4.6",
                       "deviceUuids": ["aaaabbbbccccdddd0000111122223333"],
                       "driverUuid": "11112222333344445555666677778888"}
GL_BRIDGE["matched"]["uuidMatchesOpenGl"] = True
GL_BRIDGE["matched"]["driverUuidMatchesOpenGl"] = True

# What the retired `/celtium probe` wrote. Kept here so the test states which fields the old reader asked for.
RETIRED_CAPABILITIES = {
    "deviceUUID": "aaaabbbbccccdddd0000111122223333",
    "vulkanDevices": [{"name": "AMD Radeon RX 6400 (RADV NAVI24)", "apiVersion": "1.3.280",
                       "deviceType": 2, "deviceUuid": "aaaabbbbccccdddd0000111122223333"}],
}


def exercise(device_report=None, capabilities=None, raw_device_text=None):
    with tempfile.TemporaryDirectory() as tmp:
        game = Path(tmp)
        (game / "logs").mkdir()
        (game / "screenshots").mkdir()
        (game / "screenshots/presented.png").write_bytes(b"\x89PNG\r\n\x1a\n" + b"x" * 32)
        fixture = {"status": "PASSED", "pictureEvidenceSource": "presented-dump",
                   "pictureFile_world": "screenshots/presented.png"}
        (game / "logs/celtium-validation.json").write_text(json.dumps(fixture))
        (game / "logs/latest.log").write_text("")
        if device_report is not None:
            (game / "logs/celtium-vulkan-device.json").write_text(
                raw_device_text if raw_device_text is not None else json.dumps(device_report))
        if capabilities is not None:
            (game / "logs/celtium-capabilities.json").write_text(json.dumps(capabilities))
        args = SimpleNamespace(validate=True, window_size="1280x720", jvm_arg=[], headless=False,
                               force_x11=False, duration=5, require_gpu=None)
        with patch.object(runner.subprocess, "Popen", CompletedProcess), redirect_stdout(io.StringIO()):
            runner.run_locked(args, game, [], "OfflineFixture", [], [], Path("/not-invoked/java"),
                              game / "assets", "test", None)
        return json.loads((game / "validation-process.json").read_text())


class DeviceRecordTests(unittest.TestCase):
    def test_bootstrap_report_supplies_the_record(self):
        outcome = exercise(device_report=GL_LESS)
        driver = outcome["driver"]
        self.assertEqual(driver["source"], "logs/celtium-vulkan-device.json")
        self.assertEqual(driver["deviceName"], "AMD Radeon RX 6400 (RADV NAVI24)")
        # The bootstrap spells the UUID `deviceUuid`; the retired reader looked for `deviceUUID` and never matched.
        self.assertEqual(driver["deviceUuid"], "aaaabbbbccccdddd0000111122223333")
        self.assertEqual(driver["driverUuid"], "11112222333344445555666677778888")
        self.assertEqual(driver["apiVersion"], "1.3.280")
        self.assertEqual(driver["deviceType"], "DISCRETE_GPU")
        self.assertEqual(driver["lwjglVersion"], "3.4.3")
        self.assertEqual(driver["vulkanLoaderVersion"], "1.3.290")
        self.assertEqual(driver["status"], "DEVICE_READY")
        self.assertTrue(driver["logicalDeviceCreated"])
        self.assertEqual(driver["openGlRenderer"], "celtium-gl-stub")

    def test_a_gl_less_run_says_why_the_device_was_taken(self):
        outcome = exercise(device_report=GL_LESS)
        self.assertEqual(outcome["driver"]["deviceMatch"], "no-opengl-device-uuid")
        self.assertFalse(outcome["driver"]["uuidMatchesOpenGl"])

    def test_a_uuid_matched_bootstrap_is_named_as_such(self):
        outcome = exercise(device_report=GL_BRIDGE)
        self.assertEqual(outcome["driver"]["deviceMatch"], "uuid-matched")
        self.assertTrue(outcome["driver"]["driverUuidMatchesOpenGl"])

    def test_fields_the_bootstrap_does_not_measure_are_named_not_invented(self):
        driver = exercise(device_report=GL_LESS)["driver"]
        self.assertIn("driverVersion", driver["notInSource"])
        self.assertIn("vulkanDeviceCount", driver["notInSource"])
        # Not present as a value: a synthesised one would be indistinguishable from a measurement.
        self.assertNotIn("driverVersion", driver)
        self.assertNotIn("vulkanDeviceCount", driver)

    def test_no_report_is_stated_rather_than_left_empty(self):
        outcome = exercise()
        self.assertNotIn("driver", outcome)
        self.assertIn("driverUnavailable", outcome)

    def test_the_retired_capability_file_is_not_read_even_when_present(self):
        # A leftover file in the game directory must not become this run's provenance.
        outcome = exercise(device_report=GL_LESS, capabilities=RETIRED_CAPABILITIES)
        self.assertEqual(outcome["driver"]["source"], "logs/celtium-vulkan-device.json")
        self.assertEqual(len(outcome["driver"]), len(exercise(device_report=GL_LESS)["driver"]))

    def test_only_the_retired_capability_file_present_gives_no_driver(self):
        outcome = exercise(capabilities=RETIRED_CAPABILITIES)
        self.assertNotIn("driver", outcome)
        self.assertIn("driverUnavailable", outcome)

    def test_an_unreadable_report_is_an_error_record_not_a_crash(self):
        outcome = exercise(device_report={}, raw_device_text="{ truncated")
        self.assertEqual(outcome["driver"]["source"], "logs/celtium-vulkan-device.json")
        self.assertIn("error", outcome["driver"])

    def test_an_unavailable_bootstrap_yields_no_invented_device(self):
        # status UNSUPPORTED_OR_UNAVAILABLE writes `reasons` and no `matched`: the record must say so rather than
        # label a device that was never chosen.
        outcome = exercise(device_report={"status": "UNSUPPORTED_OR_UNAVAILABLE", "reasons": ["no loader"],
                                        "openGl": {"deviceUuids": ["abc"]}})
        self.assertIsNone(outcome["driver"]["deviceName"])
        self.assertIsNone(outcome["driver"]["deviceUuid"])
        self.assertEqual(outcome["driver"]["deviceMatch"], "no-matched-device")
        self.assertEqual(outcome["driver"]["status"], "UNSUPPORTED_OR_UNAVAILABLE")


class PreRunBlockTests(unittest.TestCase):
    """The second (pre-run) reader of the retired file, which was dead rather than merely empty.

    The harness writes its lock report and only then creates the game directory, and
    `cpu-contract/isolated_game_directory_check.py` already asserts that an existing directory is refused. So a file
    read at that point could only belong to a previous run: dead when nothing wrote it any more, stale provenance if
    anything had. It is gone rather than migrated.
    """

    def test_the_lock_report_carries_no_driver_record(self):
        # Grep, not behaviour, because the branch is unreachable by construction; the point is that a re-added reader
        # would reintroduce a field that can only hold another run's data.
        source = Path(runner.__file__).read_text()
        self.assertNotIn('report["driver"]', source)


if __name__ == "__main__":
    unittest.main()
