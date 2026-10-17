#!/usr/bin/env python3
"""GPU-free safety checks; every mutation is confined to TemporaryDirectory fixtures."""
import contextlib
import hashlib
import importlib.util
import io
import json
import os
from pathlib import Path
import sys
import tempfile
import unittest
from unittest import mock

SCRIPTS = Path(__file__).absolute().parents[1]
sys.path.insert(0, str(SCRIPTS))
spec = importlib.util.spec_from_file_location("gv_prune", SCRIPTS / "gv-prune.py")
prune = importlib.util.module_from_spec(spec)
spec.loader.exec_module(prune)


def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value), encoding="utf-8")


class PruneSafetyTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix="celtium-prune-test-")
        self.addCleanup(self.temporary.cleanup)
        self.base = Path(self.temporary.name).resolve()
        self.root = self.base / "repo"
        self.root.mkdir()
        self.runs = self.root / ".gv-verify"
        self.runs.mkdir()
        self.owner = self.root / ".celtium-work" / "game-slot" / "owner.pid"
        self.slot = mock.patch.object(prune, "shared_game_slot", return_value=self.owner.parent)
        self.slot.start()
        self.addCleanup(self.slot.stop)

    def run_pruner(self, *args, root=None):
        output, errors = io.StringIO(), io.StringIO()
        with contextlib.redirect_stdout(output), contextlib.redirect_stderr(errors):
            code = prune.main(list(args), root=self.root if root is None else root)
        return code, output.getvalue(), errors.getvalue()

    def create_run(self, name="test", *, sources=True):
        run = self.runs / f"run-{name}"
        run.mkdir()
        dependencies = {"mods/example.jar": b"mod fixture bytes", "test-libraries/bindings.jar": b"library fixture bytes"}
        lock = {"isolatedGameDirectory": str(run), "effectiveConfig": {"jvmArgs": ["-Dceltium.validation=true"]},
                "testMods": [], "extraTestLibraries": []}
        for relative, data in dependencies.items():
            path = run / relative
            path.parent.mkdir(exist_ok=True)
            path.write_bytes(data)
            original = self.root / "originals" / path.name
            original.parent.mkdir(exist_ok=True)
            original.write_bytes(data)
            entry = {"file": path.name, "sha256": hashlib.sha256(data).hexdigest()}
            if sources:
                entry["source"] = str(original)
            lock["testMods" if relative.startswith("mods/") else "extraTestLibraries"].append(entry)
        write_json(self.runs / f"{name}-lock.json", lock)
        outcome = {"exitCode": 0, "endedBy": "process-exit", "fixtureStatus": "PASSED", "picturePresent": True,
                   "pictureFile": "screenshots/presented.png", "gpuRequirementMet": True, "fixtureUncalibrated": False,
                   "validationMessageCount": 0, "validationClean": True, "validationExitCode": 0}
        write_json(run / "validation-process.json", outcome)
        fixture = {"status": "PASSED", "presentedImageVerdict": "PASSED", "sceneGateCalibrated": True,
                   "sceneGateFloorsEnforced": True, "sceneCalibrationKeyMatches": True}
        write_json(run / "logs/celtium-validation.json", fixture)
        for relative in ("screenshots/presented.png", "validation-console.log", "logs/debug.log", "logs/latest.log",
                         "logs/unrecognised-report.json", "crash-reports/crash.txt", "framedump/frame.png",
                         "config/settings.cfg", "options.txt", "saves/fixture/level.dat", "world/region/r.0.0.mca",
                         "backups/before.zip", "resourcepacks/custom.zip", "resources/assets/example.png",
                         "dumps/native.dmp", "headless/sway.log", "scripts/recipes.zs", "unknown/keep.bin"):
            path = run / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(relative.encode())
        return run, lock, outcome, fixture

    def test_default_dry_run_removes_nothing(self):
        run, _, _, _ = self.create_run()
        before = {path.relative_to(run): path.read_bytes() for path in run.rglob("*") if path.is_file()}
        code, out, err = self.run_pruner("--keep", "0")
        self.assertEqual((code, err), (0, ""))
        self.assertIn("DRY-RUN", out)
        self.assertIn("WOULD REMOVE", out)
        self.assertEqual(before, {path.relative_to(run): path.read_bytes() for path in run.rglob("*") if path.is_file()})

    def test_apply_removes_only_recorded_recoverable_dependencies(self):
        run, _, _, _ = self.create_run()
        (run / "mods/unknown.jar").write_bytes(b"unrecorded")
        (run / "mods/disabled.jar.disabled").write_bytes(b"disabled")
        before = {path.relative_to(run): path.read_bytes() for path in run.rglob("*") if path.is_file()}
        code, out, err = self.run_pruner("--apply", "--keep", "0")
        self.assertEqual((code, err), (0, ""))
        removed = {Path("mods/example.jar"), Path("test-libraries/bindings.jar")}
        self.assertFalse(any((run / path).exists() for path in removed))
        self.assertEqual({path: data for path, data in before.items() if path not in removed},
                         {path.relative_to(run): path.read_bytes() for path in run.rglob("*") if path.is_file()})
        size = sum(len(before[path]) for path in removed)
        self.assertIn(f"Removed dependency payload: {size} bytes; errors: 0", out)
        self.assertTrue((self.root / "originals/example.jar").exists())

    def test_default_keep_and_newest_threshold(self):
        old, _, _, _ = self.create_run("old")
        new, _, _, _ = self.create_run("new")
        os.utime(old, (100, 100))
        os.utime(new, (200, 200))
        self.assertEqual(self.run_pruner("--apply")[0], 0)
        self.assertTrue((old / "mods/example.jar").exists())
        self.assertEqual(self.run_pruner("--apply", "--keep", "1")[0], 0)
        self.assertFalse((old / "mods/example.jar").exists())
        self.assertTrue((new / "mods/example.jar").exists())

    def test_negative_keep_is_rejected(self):
        run, _, _, _ = self.create_run()
        with contextlib.redirect_stderr(io.StringIO()), self.assertRaises(SystemExit) as raised:
            prune.main(["--apply", "--keep", "-1"], root=self.root)
        self.assertEqual(raised.exception.code, 2)
        self.assertTrue((run / "mods/example.jar").exists())

    def test_failed_incomplete_and_uncalibrated_are_preserved(self):
        changes = [({"exitCode": 1}, {}), ({"endedBy": "time-limit"}, {}), ({"fixtureStatus": "FAILED"}, {}),
                   ({"fixtureUncalibrated": True}, {}), ({"validationClean": False}, {}),
                   ({"validationMessageCount": 2}, {}), ({"gpuRequirementMet": False}, {}),
                   ({}, {"sceneGateFloorsEnforced": False}), ({}, {"sceneGateCalibrated": False}),
                   ({}, {"sceneCalibrationKeyMatches": False}), ({}, {"presentedImageVerdict": "FAILED"}),
                   ({}, {"status": "FAILED"})]
        for index, (process_change, fixture_change) in enumerate(changes):
            with self.subTest(index=index):
                run, _, outcome, fixture = self.create_run(f"bad-{index}")
                outcome.update(process_change)
                fixture.update(fixture_change)
                write_json(run / "validation-process.json", outcome)
                write_json(run / "logs/celtium-validation.json", fixture)
        self.assertEqual(self.run_pruner("--apply", "--keep", "0")[0], 0)
        self.assertTrue(all((run / "mods/example.jar").exists() for run in self.runs.iterdir() if run.is_dir()))

    def test_missing_reports_picture_console_and_lock_are_preserved(self):
        for index, relative in enumerate(("validation-process.json", "logs/celtium-validation.json",
                                          "screenshots/presented.png", "validation-console.log", None)):
            run, _, _, _ = self.create_run(f"missing-{index}")
            target = run / relative if relative else self.runs / f"missing-{index}-lock.json"
            prune.checked_path(target, self.root).unlink()
        code, out, err = self.run_pruner("--apply", "--keep", "0")
        self.assertEqual((code, err), (0, ""))
        self.assertIn("required evidence/provenance is missing", out)
        self.assertTrue(all((run / "mods/example.jar").exists() for run in self.runs.iterdir() if run.is_dir()))

    def test_missing_sources_changed_original_and_copy_are_preserved(self):
        run, _, _, _ = self.create_run(sources=False)
        self.assertEqual(self.run_pruner("--apply", "--keep", "0")[0], 0)
        self.assertTrue((run / "mods/example.jar").exists())
        run2, _, _, _ = self.create_run("changed")
        (self.root / "originals/example.jar").write_bytes(b"original replaced")
        (run2 / "test-libraries/bindings.jar").write_bytes(b"copy changed")
        self.assertEqual(self.run_pruner("--apply", "--keep", "0")[0], 0)
        self.assertTrue((run2 / "mods/example.jar").exists())
        self.assertTrue((run2 / "test-libraries/bindings.jar").exists())

    def test_wrong_run_lock_and_missing_hash_are_preserved(self):
        run, lock, _, _ = self.create_run()
        lock["isolatedGameDirectory"] = str(self.runs / "run-other")
        write_json(self.runs / "test-lock.json", lock)
        self.assertEqual(self.run_pruner("--apply", "--keep", "0")[0], 0)
        self.assertTrue((run / "mods/example.jar").exists())
        lock["isolatedGameDirectory"] = str(run)
        del lock["testMods"][0]["sha256"]
        write_json(self.runs / "test-lock.json", lock)
        self.assertEqual(self.run_pruner("--apply", "--keep", "0")[0], 0)
        self.assertTrue((run / "mods/example.jar").exists())

    def test_symlink_root_and_runs_root_are_refused(self):
        run, _, _, _ = self.create_run()
        alias = self.base / "alias"
        alias.symlink_to(self.root, target_is_directory=True)
        self.assertEqual(self.run_pruner("--apply", "--keep", "0", root=alias)[0], 1)
        other = self.base / "other"
        other.mkdir()
        (other / ".gv-verify").symlink_to(self.runs, target_is_directory=True)
        self.assertEqual(self.run_pruner("--apply", "--keep", "0", root=other)[0], 1)
        self.assertTrue((run / "mods/example.jar").exists())

    def test_symlink_run_dependency_logs_report_and_original_are_not_followed(self):
        outside = self.base / "outside"
        outside.mkdir()
        (outside / "sentinel.jar").write_bytes(b"outside evidence")
        (self.runs / "run-linked").symlink_to(outside, target_is_directory=True)
        for name, relative in (("linked-file", "mods/example.jar"), ("linked-dir", "logs"),
                               ("linked-report", "validation-process.json")):
            run, _, _, _ = self.create_run(name)
            target = run / relative
            if target.is_dir():
                # Preserve the generated logs under a new name rather than deleting a tree.
                retained = run / "saved-logs"
                prune.checked_path(target, run)
                self.assertEqual(retained.resolve(), run.resolve() / "saved-logs")
                target.rename(retained)
                target.symlink_to(outside, target_is_directory=True)
            else:
                prune.checked_path(target, run).unlink()
                target.symlink_to(outside / "sentinel.jar")
        run, lock, _, _ = self.create_run("linked-original")
        original = self.root / "originals/example.jar"
        prune.checked_path(original, self.root).unlink()
        original.symlink_to(outside / "sentinel.jar")
        code, out, err = self.run_pruner("--apply", "--keep", "0")
        self.assertEqual((code, err), (0, ""))
        self.assertIn("symlink refused", out)
        self.assertEqual((outside / "sentinel.jar").read_bytes(), b"outside evidence")
        self.assertTrue((run / "mods/example.jar").exists())
        self.assertTrue((self.runs / "run-linked-file/test-libraries/bindings.jar").exists())

    def test_nested_dependency_directory_and_unknown_files_are_kept(self):
        run, _, _, _ = self.create_run()
        nested = run / "mods/1.12.2/unknown.jar"
        nested.parent.mkdir()
        nested.write_bytes(b"nested mod")
        self.assertEqual(self.run_pruner("--apply", "--keep", "0")[0], 0)
        self.assertEqual(nested.read_bytes(), b"nested mod")

    def test_legacy_and_shared_worktree_owner_refuse_without_reclaiming(self):
        run, _, _, _ = self.create_run()
        other_slot = self.base / "shared-main/.celtium-work/game-slot"
        other_slot.mkdir(parents=True)
        owner = other_slot / "owner.pid"
        owner.write_text("999999999")
        with mock.patch.object(prune, "shared_game_slot", return_value=other_slot):
            code, out, _ = self.run_pruner("--apply", "--keep", "0")
        self.assertEqual(code, 0)
        self.assertIn("possibly stale", out)
        self.assertTrue(owner.exists())
        self.assertTrue((run / "mods/example.jar").exists())
        legacy = self.root / ".celtium-work/gpu.lock"
        legacy.mkdir(parents=True)
        self.assertEqual(self.run_pruner("--apply", "--keep", "0")[0], 0)
        self.assertTrue(legacy.exists())
        self.assertTrue((run / "mods/example.jar").exists())

    def test_sibling_manifest_sources_need_an_accepted_result(self):
        run, lock, _, _ = self.create_run(sources=False)
        manifest = {"dependencies": [
            {"path": f"{directory}/{entry['file']}", "source": str(self.root / "originals" / entry["file"]),
             "sha256": entry["sha256"]}
            for directory, field in prune.DEPENDENCIES.items() for entry in lock[field]]}
        write_json(self.runs / "test-manifest.json", manifest)
        self.assertEqual(self.run_pruner("--apply", "--keep", "0")[0], 0)
        self.assertTrue((run / "mods/example.jar").exists())
        write_json(self.runs / "test-result.json", {"accepted": False, "exitCode": 1})
        self.assertEqual(self.run_pruner("--apply", "--keep", "0")[0], 0)
        self.assertTrue((run / "mods/example.jar").exists())
        write_json(self.runs / "test-result.json", {"accepted": True, "exitCode": 0})
        self.assertEqual(self.run_pruner("--apply", "--keep", "0")[0], 0)
        self.assertFalse((run / "mods/example.jar").exists())
        self.assertTrue((self.runs / "test-manifest.json").exists())
        self.assertTrue((self.runs / "test-result.json").exists())

    def test_other_runs_cannot_serve_as_originals(self):
        run, lock, _, _ = self.create_run()
        other = self.runs / "run-original"
        other.mkdir()
        source = other / "original.jar"
        source.write_bytes((run / "mods/example.jar").read_bytes())
        lock["testMods"][0]["source"] = str(source)
        write_json(self.runs / "test-lock.json", lock)
        self.assertEqual(self.run_pruner("--apply", "--keep", "0")[0], 0)
        self.assertTrue((run / "mods/example.jar").exists())

    def test_other_worktree_run_cannot_serve_as_an_original(self):
        run, lock, _, _ = self.create_run()
        source = self.base / "other-worktree/.gv-verify/run-old/mods/example.jar"
        source.parent.mkdir(parents=True)
        source.write_bytes((run / "mods/example.jar").read_bytes())
        lock["testMods"][0]["source"] = str(source)
        write_json(self.runs / "test-lock.json", lock)
        self.assertEqual(self.run_pruner("--apply", "--keep", "0")[0], 0)
        self.assertTrue((run / "mods/example.jar").exists())

    def test_dependency_directory_symlink_is_not_followed(self):
        run, _, _, _ = self.create_run()
        target = run / "test-libraries"
        preserved = run / "preserved-libraries"
        prune.checked_path(target, run)
        self.assertEqual(preserved.resolve(), run.resolve() / "preserved-libraries")
        target.rename(preserved)
        target.symlink_to(self.root / "originals", target_is_directory=True)
        self.assertEqual(self.run_pruner("--apply", "--keep", "0")[0], 0)
        self.assertTrue((run / "mods/example.jar").exists())
        self.assertTrue((self.root / "originals/bindings.jar").exists())

    def test_legacy_instance_and_fixture_source_provenance(self):
        run, lock, _, _ = self.create_run(sources=False)
        instance = self.root / "instance"
        original = instance / "minecraft/mods/example.jar"
        original.parent.mkdir(parents=True)
        original.write_bytes((run / "mods/example.jar").read_bytes())
        lock["sourceInstance"] = str(instance)
        lock["mods"] = [dict(lock["testMods"][0])]
        lock["fixture"] = {"file": "bindings.jar", "sha256": lock["extraTestLibraries"][0]["sha256"],
                           "source": "originals/bindings.jar"}
        # Only the instance mod is backed by matching provenance; fixture is a mod, not a library alias.
        write_json(self.runs / "test-lock.json", lock)
        self.assertEqual(self.run_pruner("--apply", "--keep", "0")[0], 0)
        self.assertFalse((run / "mods/example.jar").exists())
        self.assertTrue((run / "test-libraries/bindings.jar").exists())

    def test_missing_configuration_and_dependency_inventory_preserve_run(self):
        for index, field in enumerate(("effectiveConfig", "testMods", "extraTestLibraries")):
            run, lock, _, _ = self.create_run(f"provenance-{index}")
            del lock[field]
            write_json(self.runs / f"provenance-{index}-lock.json", lock)
        self.assertEqual(self.run_pruner("--apply", "--keep", "0")[0], 0)
        self.assertTrue(all((run / "mods/example.jar").exists() for run in self.runs.iterdir() if run.is_dir()))

    def test_json_errors_are_nonzero_and_do_not_delete(self):
        run, _, _, _ = self.create_run()
        (run / "validation-process.json").write_text("not JSON")
        code, out, err = self.run_pruner("--apply", "--keep", "0")
        self.assertEqual(code, 1)
        self.assertIn("ERROR", err)
        self.assertIn("Removed dependency payload: 0 bytes; errors: 1", out)
        self.assertTrue((run / "mods/example.jar").exists())

    def test_unlink_errors_are_nonzero_and_not_counted_as_removed(self):
        run, _, _, _ = self.create_run()
        with mock.patch.object(Path, "unlink", side_effect=PermissionError("denied by test")):
            code, out, err = self.run_pruner("--apply", "--keep", "0")
        self.assertEqual(code, 1)
        self.assertIn("denied by test", err)
        self.assertIn("Removed dependency payload: 0 bytes; errors: 1", out)
        self.assertTrue((run / "mods/example.jar").exists())
        self.assertTrue((run / "test-libraries/bindings.jar").exists())

    def test_partial_deletion_only_counts_successful_unlinks(self):
        run, _, _, _ = self.create_run()
        unlink = Path.unlink
        def fail_library(path, *args, **kwargs):
            if path.parent.name == "test-libraries":
                raise OSError("test library cannot be removed")
            return unlink(path, *args, **kwargs)
        with mock.patch.object(Path, "unlink", fail_library):
            code, out, err = self.run_pruner("--apply", "--keep", "0")
        self.assertEqual(code, 1)
        self.assertIn("test library cannot be removed", err)
        self.assertIn(f"Removed dependency payload: {len(b'mod fixture bytes')} bytes; errors: 1", out)
        self.assertFalse((run / "mods/example.jar").exists())
        self.assertTrue((run / "test-libraries/bindings.jar").exists())


if __name__ == "__main__":
    unittest.main()
