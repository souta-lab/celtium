"""Exercise the relauncher-instance support in the canonical harness, with no Java, window or GPU.

What these tests can prove: the heap the harness asks for comes from the right place, a relauncher instance is
recorded with the Cleanroom version it will prepare, and the Cleanroom generation check names the 0.6 line it cannot
host. They prove nothing about a launch: that needs the game, and a green test here is not evidence about any run.
"""
import importlib.util
import json
from pathlib import Path
import sys
import tempfile
import unittest

SCRIPTS = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(SCRIPTS))
spec = importlib.util.spec_from_file_location("prism_validation_relauncher", SCRIPTS / "prism_validation.py")
runner = importlib.util.module_from_spec(spec)
spec.loader.exec_module(runner)


def instance_with(tmp, max_mem=None, relauncher=None, config=None):
    """{@return} a profile whose files are the ones the harness reads."""
    root = Path(tmp)
    instance = root / "instances" / "pack"
    (instance / "minecraft" / "mods").mkdir(parents=True)
    (instance / "minecraft" / "config").mkdir(parents=True)
    cfg = "[General]\n"
    if max_mem is not None:
        cfg += f"MaxMemAlloc={max_mem}\n"
    (instance / "instance.cfg").write_text(cfg)
    if relauncher is not None:
        (instance / "minecraft" / "mods" / relauncher).write_bytes(b"")
    if config is not None:
        (instance / "minecraft" / "config" / "relauncher.json").write_text(json.dumps(config))
    return instance


class InstanceHeapTests(unittest.TestCase):
    def test_explicit_memory_wins_over_the_instance(self):
        with tempfile.TemporaryDirectory() as tmp:
            instance = instance_with(tmp, max_mem=10240)
            self.assertEqual(6144, runner.instance_heap_mib(instance, 6144))

    def test_the_instance_heap_is_used_when_the_caller_does_not_say(self):
        # A relauncher copies the launcher's -Xmx to the game, so the instance's own allocation is the right default
        # for a pack that recorded one; the old fixed 2G was the defect this test exists for.
        with tempfile.TemporaryDirectory() as tmp:
            instance = instance_with(tmp, max_mem=10240)
            self.assertEqual(10240, runner.instance_heap_mib(instance))

    def test_a_profile_without_memory_falls_back_to_two_gigabytes(self):
        with tempfile.TemporaryDirectory() as tmp:
            instance = instance_with(tmp)
            self.assertEqual(2048, runner.instance_heap_mib(instance))

    def test_a_sub_minimum_heap_is_refused(self):
        with tempfile.TemporaryDirectory() as tmp:
            instance = instance_with(tmp, max_mem=4096)
            with self.assertRaises(ValueError):
                runner.instance_heap_mib(instance, 256)


class RelauncherDetectionTests(unittest.TestCase):
    def test_a_forge_profile_without_the_relauncher_is_not_one(self):
        with tempfile.TemporaryDirectory() as tmp:
            instance = instance_with(tmp)
            self.assertIsNone(runner.relauncher_summary(instance))

    def test_the_relauncher_and_its_cleanroom_version_are_recorded(self):
        with tempfile.TemporaryDirectory() as tmp:
            instance = instance_with(
                tmp,
                relauncher="!cleanroom-relauncher-1.2.0.jar",
                config={"selectedVersion": "0.7.14-alpha", "latestVersion": "0.7.14-alpha",
                        "targetJavaVersion": 25, "autoSetup": True, "enableRelauncher": True,
                        "args": "-XX:+UseCompactObjectHeaders"},
            )
            summary = runner.relauncher_summary(instance)
            self.assertEqual("!cleanroom-relauncher-1.2.0.jar", summary["mod"])
            self.assertEqual("1.2.0", summary["version"])
            self.assertEqual("0.7.14-alpha", summary["cleanroomVersion"])
            self.assertEqual(25, summary["targetJavaVersion"])
            self.assertTrue(summary["forwardsLauncherJvmArgs"])

    def test_a_relauncher_without_a_config_still_reports_the_mod(self):
        with tempfile.TemporaryDirectory() as tmp:
            instance = instance_with(tmp, relauncher="cleanroom-relauncher.jar")
            summary = runner.relauncher_summary(instance)
            self.assertIsNone(summary["cleanroomVersion"])
            self.assertIsNone(summary["version"])


class CleanroomGenerationTests(unittest.TestCase):
    def test_the_seven_line_is_named(self):
        self.assertEqual((0, 7), runner.cleanroom_generation("0.7.14-alpha"))

    def test_the_old_generation_is_named(self):
        self.assertEqual((0, 6), runner.cleanroom_generation("0.6.13-alpha"))

    def test_an_unknown_version_is_not_guessed(self):
        self.assertIsNone(runner.cleanroom_generation(None))
        self.assertIsNone(runner.cleanroom_generation("snapshot"))


if __name__ == "__main__":
    unittest.main()


class ComponentResolutionTests(unittest.TestCase):
    def test_a_local_patch_wins_over_the_meta(self):
        with tempfile.TemporaryDirectory() as tmp:
            instance = instance_with(tmp)
            (instance / "patches").mkdir()
            (instance / "patches" / "net.minecraft.json").write_text("{}")
            prism = instance.parent.parent
            (prism / "meta" / "net.minecraft").mkdir(parents=True)
            (prism / "meta" / "net.minecraft" / "1.12.2.json").write_text("{}")
            path, source = runner.component_patch(instance, prism, {"uid": "net.minecraft", "version": "1.12.2"})
            self.assertEqual("local", source)
            self.assertEqual(instance / "patches" / "net.minecraft.json", path)

    def test_an_imported_profile_resolves_from_the_launcher_meta(self):
        # This is the relauncher case: Prism imported the Forge pack, so patches/ is empty and the launcher's meta is
        # the only place the component exists. Reading only patches/ refused the configuration the testers run.
        with tempfile.TemporaryDirectory() as tmp:
            instance = instance_with(tmp)
            prism = instance.parent.parent
            (prism / "meta" / "net.minecraftforge").mkdir(parents=True)
            (prism / "meta" / "net.minecraftforge" / "14.23.5.2860.json").write_text("{}")
            path, source = runner.component_patch(
                instance, prism, {"uid": "net.minecraftforge", "version": "14.23.5.2860"})
            self.assertEqual("meta", source)
            self.assertTrue(str(path).endswith("14.23.5.2860.json"))

    def test_an_unresolvable_component_is_none(self):
        with tempfile.TemporaryDirectory() as tmp:
            instance = instance_with(tmp)
            prism = instance.parent.parent
            self.assertEqual((None, None), runner.component_patch(instance, prism, {"uid": "x", "version": "1"}))


class LibraryDescriptorTests(unittest.TestCase):
    def test_an_artifact_is_preferred(self):
        lib = {"name": "a.b:c:1", "downloads": {"artifact": {"url": "u"},
                                                 "classifiers": {"natives-linux": {"url": "n"}}}}
        self.assertEqual("u", runner.library_descriptor(lib)["url"])

    def test_a_natives_only_library_resolves_the_linux_classifier(self):
        # jinput-platform is the 1.12.2 case: no artifact, only classifier jars, so a profile named by coordinate has
        # no file the launcher keeps. The classifier is what the launcher actually places.
        lib = {"name": "net.java.jinput:jinput-platform:2.0.5",
               "downloads": {"classifiers": {"natives-windows": {"url": "w"}, "natives-linux": {"url": "l"}}}}
        descriptor = runner.library_descriptor(lib)
        self.assertEqual("l", descriptor["url"])
        self.assertEqual("natives-linux", descriptor["classifier"])
        self.assertEqual("jinput-platform-2.0.5-natives-linux.jar",
                         runner.coordinate_path(lib["name"], descriptor["classifier"]).name)

    def test_a_library_with_no_downloads_is_empty(self):
        self.assertEqual({}, runner.library_descriptor({"name": "a.b:c:1"}))


class JavaVersionTests(unittest.TestCase):
    def test_java_8_is_parsed_from_the_legacy_string(self):
        self.assertEqual(8, runner.java_major_version('openjdk version "1.8.0_504"'))
        self.assertFalse(runner.java_supports_native_access('openjdk version "1.8.0_504"'))

    def test_a_modern_jvm_gets_the_native_access_flag(self):
        self.assertEqual(25, runner.java_major_version('openjdk version "25.0.5" 2025-10-21'))
        self.assertTrue(runner.java_supports_native_access('openjdk version "25.0.5" 2025-10-21'))
        self.assertTrue(runner.java_supports_native_access(21))
        self.assertFalse(runner.java_supports_native_access(8))

    def test_an_unreadable_version_is_not_guessed(self):
        self.assertIsNone(runner.java_major_version(""))


class RelauncherArgumentTests(unittest.TestCase):
    def test_diagnostic_flags_are_written_into_the_isolated_copy(self):
        with tempfile.TemporaryDirectory() as tmp:
            game = Path(tmp) / "game"
            (game / "config").mkdir(parents=True)
            (game / "config" / "relauncher.json").write_text(
                json.dumps({"selectedVersion": "0.7.14-alpha", "args": "-XX:+UseCompactObjectHeaders"}))
            added = runner.inject_relauncher_arguments(
                game, ["-Dceltium.vulkan.frameBudget=true", "-Xmx5g", "-Dfml.readTimeout=180"])
            self.assertEqual(["-Dceltium.vulkan.frameBudget=true", "-Dfml.readTimeout=180"], added)
            written = json.loads((game / "config" / "relauncher.json").read_text())
            self.assertEqual("-XX:+UseCompactObjectHeaders -Dceltium.vulkan.frameBudget=true -Dfml.readTimeout=180",
                             written["args"])

    def test_a_run_without_flags_writes_nothing(self):
        with tempfile.TemporaryDirectory() as tmp:
            game = Path(tmp) / "game"
            game.mkdir()
            self.assertEqual([], runner.inject_relauncher_arguments(game, []))
            self.assertFalse((game / "config" / "relauncher.json").exists())
