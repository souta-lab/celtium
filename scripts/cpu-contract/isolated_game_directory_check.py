#!/usr/bin/env python3
"""Exercise actual directory preparation without booting Minecraft or deleting a prior run."""
import importlib.util
from pathlib import Path
import sys
import tempfile
import unittest

SCRIPTS = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(SCRIPTS))
spec = importlib.util.spec_from_file_location("prism_validation", SCRIPTS / "prism_validation.py")
runner = importlib.util.module_from_spec(spec)
spec.loader.exec_module(runner)


class IsolatedGameDirectoryCheck(unittest.TestCase):
    def assert_preserved(self, files):
        with tempfile.TemporaryDirectory() as tmp:
            game = Path(tmp) / "game"
            game.mkdir()
            for name, data in files.items():
                target = game / name
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(data)
            before = {str(p.relative_to(game)): p.read_bytes() for p in game.rglob("*") if p.is_file()}
            with self.assertRaises(FileExistsError):
                runner.create_isolated_game_directory(game)
            after = {str(p.relative_to(game)): p.read_bytes() for p in game.rglob("*") if p.is_file()}
            self.assertEqual(after, before)

    def test_fresh_nested_directory_is_created(self):
        with tempfile.TemporaryDirectory() as tmp:
            game = Path(tmp) / "nested" / "game"
            runner.create_isolated_game_directory(game)
            self.assertTrue(game.is_dir())
            self.assertEqual(list(game.iterdir()), [])

    def test_crash_without_fixture_report_is_preserved(self):
        self.assert_preserved({
            "hs_err_pid42.log": b"SIGSEGV: libxkbcommon",
            "validation-console.log": b"failed before fixture produced a report",
            "validation-command.json": b"[]",
            "saves/world/region/r.0.0.mca": b"prior world",
        })

    def test_prior_accepted_run_is_preserved(self):
        self.assert_preserved({"logs/celtium-validation.json": b'{"fixtureStatus":"PASSED"}',
                               "screenshots/presented.png": b"picture"})

    def test_unknown_existing_files_are_preserved(self):
        self.assert_preserved({"notes.txt": b"not owned by this launcher", "saves/level.dat": b"data"})


if __name__ == "__main__":
    unittest.main()
