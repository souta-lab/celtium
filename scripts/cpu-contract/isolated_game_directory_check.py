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

    def test_supplemental_pack_dirs_are_allowlisted(self):
        # Only content directories may be added; the four the harness always copies are not supplemental, and
        # anything else is refused rather than silently carried into a run the harness never audited.
        self.assertEqual(["scripts", "resources"], runner.supplemental_pack_dirs(["scripts", "resources"]))
        self.assertEqual(["resources"], runner.supplemental_pack_dirs(["resources", "resources"]))
        self.assertEqual([], runner.supplemental_pack_dirs([]))
        for refused in ("mods", "config", "resourcepacks", "saves", "memory_repo", "../evil", "/etc"):
            with self.assertRaises(ValueError):
                runner.supplemental_pack_dirs([refused])
        with self.assertRaises(ValueError):
            runner.supplemental_pack_dirs(["scripts", "mods"])

    def test_supplemental_pack_dirs_are_copied_and_recorded(self):
        with tempfile.TemporaryDirectory() as tmp:
            source = Path(tmp) / "profile"
            (source / "scripts").mkdir(parents=True)
            (source / "scripts" / "pack.zs").write_text("// recipe")
            (source / "resources").mkdir()
            (source / "resources" / "pack.mcmeta").write_text("{}")
            (source / "config").mkdir()
            (source / "config" / "pack.cfg").write_text("x=1")
            game = Path(tmp) / "game"
            game.mkdir()
            recorded = runner.copy_pack_into_game_dir(source, game, ["scripts", "resources", "schematics"], ())
            # The always-copied directory is there, the requested ones carry their files, and a directory the
            # profile does not have is recorded as not copied instead of being counted as content.
            self.assertTrue((game / "config" / "pack.cfg").is_file())
            self.assertEqual(
                [{"name": "scripts", "copied": True, "files": 1},
                 {"name": "resources", "copied": True, "files": 1},
                 {"name": "schematics", "copied": False, "files": 0}],
                recorded)
            # A second call needs its own game dir: the copy refuses to overwrite an existing one, which is the
            # harness's own rule and not something this option relaxes.
            fresh = Path(tmp) / "game2"
            fresh.mkdir()
            self.assertEqual([], runner.copy_pack_into_game_dir(source, fresh, [], ()))


if __name__ == "__main__":
    unittest.main()
