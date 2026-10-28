"""Offline wrapper contracts: only fake subprocesses and temporary files, never a GPU/Java/build."""
import binascii
from contextlib import redirect_stdout
import importlib.util
import io
import json
import os
from pathlib import Path
import signal
import struct
import subprocess
import sys
import tempfile
import time
import unittest
from unittest.mock import patch
import zlib

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import gv_leg as runner


# Deliberately independent of the production harness and its dependencies.
FAKE_HARNESS = r'''
import argparse, binascii, hashlib, json, os
from pathlib import Path
import shutil, signal, struct, subprocess, sys, time, zlib
p = argparse.ArgumentParser()
p.add_argument('instance')
p.add_argument('--output', type=Path)
p.add_argument('--game-dir', type=Path)
p.add_argument('--mod', action='append', default=[], type=Path)
p.add_argument('--library', action='append', default=[], type=Path)
p.add_argument('--jvm-arg', action='append', default=[])
p.add_argument('--copy-pack-dir', action='append', default=[])
p.add_argument('--require-gpu')
a, unknown = p.parse_known_args()
game = a.game_dir
game.mkdir()
(game / 'mods').mkdir()
(game / 'test-libraries').mkdir()
(game / 'logs').mkdir()
(game / 'screenshots').mkdir()
scenario = os.environ.get('FAKE_SCENARIO', 'passed')
def digest(path): return hashlib.sha256(path.read_bytes()).hexdigest()
for source in a.mod:
    if scenario == 'missing_renderer' and source.name.startswith('celtium-forge-'):
        continue
    shutil.copy2(source, game / 'mods' / source.name)
for source in a.library:
    shutil.copy2(source, game / 'test-libraries' / source.name)
for name in a.copy_pack_dir:
    shutil.copytree(Path(a.instance) / 'minecraft' / name, game / name)
(game / 'argv.json').write_text(json.dumps({'argv': sys.argv[1:], 'jvmArgs': a.jvm_arg,
    'supplemental': a.copy_pack_dir, 'DRI_PRIME': os.environ.get('DRI_PRIME'),
    'MESA_VK_DEVICE_SELECT': os.environ.get('MESA_VK_DEVICE_SELECT'),
    'PYTHONPATH': os.environ.get('PYTHONPATH')}))
lock = {'sourceInstance': a.instance, 'isolatedGameDirectory': str(game),
    'effectiveConfig': {'runnerJvmArgs': a.jvm_arg, 'jvmArgs': a.jvm_arg},
    'testMods': [{'file': path.name, 'sha256': digest(path)} for path in (game / 'mods').glob('*.jar')],
    'extraTestLibraries': [{'file': path.name, 'sha256': digest(path)} for path in (game / 'test-libraries').glob('*.jar')]}
if scenario == 'wrong_lock_hash': lock['testMods'][0]['sha256'] = '0' * 64
if scenario != 'missing_lock': a.output.write_text(json.dumps(lock))
for role in ('renderer', 'fixture'):
    if scenario == role + '_hash_mismatch':
        pattern = 'celtium-forge-*.jar' if role == 'renderer' else 'celtium-validation-client-*.jar'
        target = next((game / 'mods').glob(pattern))
        target.write_bytes(target.read_bytes() + b'wrong')
    if scenario == role + '_count_mismatch':
        name = 'celtium-forge-mc1.12.2-old.jar' if role == 'renderer' else 'celtium-validation-client-old.jar'
        (game / 'mods' / name).write_bytes(b'duplicate')
if scenario in ('hang', 'orphan'):
    child = subprocess.Popen([sys.executable, '-c',
        'import signal,time; signal.signal(signal.SIGINT,signal.SIG_IGN); signal.signal(signal.SIGTERM,signal.SIG_IGN); time.sleep(60)'])
    (game / 'child.pid').write_text(str(child.pid))
    # Child gets time to install its handlers before readiness is published.
    time.sleep(.1)
    (game / 'ready').write_text('ready')
    if scenario == 'hang':
        while True: time.sleep(1)
def chunk(kind, data):
    return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', binascii.crc32(kind + data) & 0xffffffff)
png = (b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', 2, 2, 8, 6, 0, 0, 0))
       + chunk(b'IDAT', zlib.compress((b'\x00' + b'\xff\x00\x00\xff' * 2) * 2)) + chunk(b'IEND', b''))
if scenario == 'bad_png': png = png[:28]
(game / 'screenshots/picture.png').write_bytes(png)
name = 'Intel(R) UHD Graphics 630 (CFL GT2)' if 'UHD' in a.require_gpu else 'AMD Radeon RX 6400'
if scenario == 'wrong_gpu': name = 'AMD Radeon RX 6400' if 'UHD' in a.require_gpu else 'Intel(R) UHD Graphics 630 (CFL GT2)'
native_uuid = 'a' * 32
reference = scenario.startswith('gl_reference')
native = '[Celtium] CELTIUM_NATIVE_DEVICE name="' + name + '" uuid=' + native_uuid + ' matchedOpenGl=false\n'
(game / 'validation-console.log').write_text('' if scenario == 'missing_device' or reference else native)
(game / 'logs/latest.log').write_text('')
status = 'UNCALIBRATED_PRESENTED_IMAGE' if scenario.startswith('uncalibrated') else 'PASSED'
fixture = {'status': status, 'presentedImageVerdict': status, 'sceneGateCalibrated': True,
    'sceneGateFloorsEnforced': True, 'sceneCalibrationKeyMatches': True, 'sceneGuiStage': False,
    'pictureEvidenceSource': 'presented-dump', 'pictureFile_world': 'screenshots/picture.png'}
if reference:
    fixture.update(glRenderer=name, pictureEvidenceSource='gl-screenshot')
if scenario == 'missing_gate': fixture.pop('sceneGateFloorsEnforced')
if scenario == 'false_gate': fixture['sceneCalibrationKeyMatches'] = False
if scenario == 'missing_verdict': fixture.pop('presentedImageVerdict')
if scenario == 'escaped_picture': fixture['pictureFile_world'] = '../outside.png'
if scenario != 'missing_fixture':
    (game / 'logs/celtium-validation.json').write_text('{' if scenario == 'malformed_fixture' else json.dumps(fixture))
code = 2 if scenario == 'uncalibrated_2' else 0
process = {'exitCode': 0, 'endedBy': 'process-exit', 'validationExitCode': code, 'fixtureStatus': status,
    'fixtureUncalibrated': status == 'UNCALIBRATED_PRESENTED_IMAGE', 'validationClean': True, 'validationMessageCount': 0, 'nativeDeviceName': name,
    'nativeDeviceUuid': native_uuid, 'gpuRequirementMet': True, 'gpuRequirementSource': 'nativeDeviceName',
    'pictureEvidenceSource': 'presented-dump', 'pictureStage': 'world',
    'pictureFile': fixture['pictureFile_world'], 'picturePresent': True}
if reference:
    process.update(nativeDeviceName=None, nativeDeviceUuid=None, gpuRequirementSource='glRendererPreflight',
                   glRendererPreflight=name, pictureEvidenceSource='gl-screenshot')
    if scenario == 'gl_reference_wrong_gpu': process['glRendererPreflight'] = 'wrong device'
if scenario == 'dirty': process.update(validationClean=False, validationMessageCount=1)
if scenario == 'missing_clean': process.pop('validationClean')
if scenario == 'preflight_gpu': process['gpuRequirementSource'] = 'glRendererPreflight'
if scenario == 'wrong_stage': process['pictureStage'] = 'gui'
if scenario != 'missing_process': (game / 'validation-process.json').write_text(json.dumps(process))
sys.exit(code)
'''


class LegRunnerTests(unittest.TestCase):
    def test_child_exit_grace_waits_for_a_group_that_empties_and_gives_up_on_one_that_does_not(self):
        clock = [0.0]
        def advance(seconds):
            clock[0] += seconds
        # A pack helper that exits two seconds after the game: waited for, not reported.
        waited = runner.wait_group_empty(1, 60.0, exists=lambda pgid: clock[0] < 2.0, sleep=advance,
                                         clock=lambda: clock[0])
        self.assertEqual(waited, 2.0)
        clock[0] = 0.0
        # One that never exits: the wait stops at the grace and the caller still reports it.
        waited = runner.wait_group_empty(1, 3.0, exists=lambda pgid: True, sleep=advance, clock=lambda: clock[0])
        self.assertGreaterEqual(waited, 3.0)

    def setUp(self):
        self._grace = runner.CHILD_EXIT_GRACE
        runner.CHILD_EXIT_GRACE = 0.2
        self.addCleanup(setattr, runner, "CHILD_EXIT_GRACE", self._grace)
        self.temporary = tempfile.TemporaryDirectory(prefix="gv-leg-test-")
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        (self.root / "scripts").mkdir()
        (self.root / "scripts/prism_validation.py").write_text(FAKE_HARNESS)
        (self.root / "scripts/headless_wayland.py").write_text("# fake helper, never imported\n")
        (self.root / ".gv-verify/lib").mkdir(parents=True)
        (self.root / ".gv-verify/lib/lwjgl-vulkan-3.4.1.jar").write_bytes(b"test-library")
        self.jars = self.root / "jars with spaces"
        self.jars.mkdir()
        (self.jars / "celtium-forge-mc1.12.2-test.jar").write_bytes(b"test-renderer")
        (self.jars / "celtium-validation-client-test.jar").write_bytes(b"test-fixture")
        self.instance = self.root / "instance"
        (self.instance / "minecraft/mods").mkdir(parents=True)
        (self.instance / "mmc-pack.json").write_text("{}")
        (self.instance / "instance.cfg").write_text("[General]\nJavaPath=/not-used\n")
        self.environment = {"CELTIUM_ROOT": str(self.root), "CELTIUM_INSTANCE_COHORT": str(self.instance),
                            "CELTIUM_INSTANCE_E2E": str(self.instance), "CELTIUM_INSTANCE_RECIPE": str(self.instance),
                            "FAKE_SCENARIO": "passed", "E2E_EXCLUDE": "", "CLASH_DIR": "",
                            "CELTIUM_LEG_REFERENCE_RENDERER": ""}
        self.environment["CELTIUM_LEG_WINDOW_SYSTEM"] = "x11"
        self.environment["CELTIUM_LEG_SEED_OPTIONS"] = ""
        self.env_patch = patch.dict(os.environ, self.environment)
        self.env_patch.start()
        self.addCleanup(self.env_patch.stop)

    def plan(self, leg="test", *, extras=None, mode="plain", gpu="amd", duration=1):
        return runner.make_plan(leg, self.jars, mode, gpu, duration, extras or [], root=self.root)

    def execute(self, scenario="passed", **kwargs):
        os.environ["FAKE_SCENARIO"] = scenario
        return runner.execute(self.plan(**kwargs), timeout=5, terminate_grace=.1)

    def test_window_system_is_explicit_and_wayland_keeps_its_socket(self):
        plan = self.plan()
        self.assertIn("--force-x11", plan.command)
        self.assertEqual(plan.environment["SDL_VIDEODRIVER"], "x11")
        os.environ["CELTIUM_LEG_WINDOW_SYSTEM"] = "wayland"
        plan = self.plan()
        self.assertNotIn("--force-x11", plan.command)
        self.assertEqual(plan.environment["SDL_VIDEODRIVER"], "wayland")
        self.assertEqual(plan.manifest["windowSystemRequested"], "wayland")
        os.environ["CELTIUM_LEG_WINDOW_SYSTEM"] = "typo"
        with self.assertRaises(runner.LegError):
            self.plan()

    def test_seeded_user_options_are_explicit_and_hash_pinned(self):
        options = self.root / "Japanese options.txt"
        options.write_text("lang:ja_jp\nrenderDistance:18\n")
        os.environ["CELTIUM_LEG_SEED_OPTIONS"] = str(options)
        plan = self.plan()
        position = plan.command.index("--seed-options")
        self.assertEqual(plan.command[position + 1], str(options))
        self.assertEqual(plan.manifest["artifacts"]["seedOptions"]["sha256"], runner.sha256(options))

    def test_explicit_gl_reference_uses_its_real_gpu_and_screenshot_evidence(self):
        reference = self.root / "celeritas-reference.jar"
        reference.write_bytes(b"GL reference")
        os.environ["CELTIUM_LEG_REFERENCE_RENDERER"] = str(reference)
        result = self.execute("gl_reference")
        self.assertTrue(result["accepted"], result)
        self.assertEqual(result["rendererKind"], "opengl-reference")
        self.assertEqual(result["referenceGlRenderer"], "AMD Radeon RX 6400")
        self.assertIsNone(result["nativeDeviceName"])

    def test_gl_evidence_never_substitutes_for_native_acceptance(self):
        result = self.execute("gl_reference")
        self.assertFalse(result["accepted"], result)

    def test_wrong_gpu_reference_is_not_accepted(self):
        reference = self.root / "celeritas-reference.jar"
        reference.write_bytes(b"GL reference")
        os.environ["CELTIUM_LEG_REFERENCE_RENDERER"] = str(reference)
        result = self.execute("gl_reference_wrong_gpu")
        self.assertFalse(result["accepted"], result)

    def test_jfr_recording_option_is_allowed_and_bare_or_malformed_XX_is_not(self):
        # Profiling is the reason -XX: is allowed at all: a JFR recording cannot be started with -D/-X.
        self.plan(extras=["-XX:StartFlightRecording=filename=/tmp/x.jfr,settings=profile,dumponexit=true"])
        # The strict form also admits flags: -XX:+UnlockDiagnosticVMOptions is a legitimate companion of a recording.
        self.plan(extras=["-XX:+UnlockDiagnosticVMOptions"])
        for bad in ("-XX:", "-XX:Name", "-XX:=value", "-XX:StartFlightRecording", "-XX:Name="):
            with self.subTest(bad=bad):
                with self.assertRaises(runner.LegError):
                    self.plan(extras=[bad])

    def test_leg_and_duration_rejected_before_output_creation(self):
        for leg in ("", "../escape", "x/../../escape", "a.b", "-dash", "x" * 65, "é"):
            with self.subTest(leg=leg), self.assertRaises(runner.LegError):
                self.plan(leg)
        for duration in (0, -1, True):
            with self.subTest(duration=duration), self.assertRaises(runner.LegError):
                self.plan(duration=duration)
        self.assertEqual(list((self.root / ".gv-verify").glob("*.out")), [])

    def test_missing_and_ambiguous_packaged_jars(self):
        extra = self.jars / "celtium-forge-mc1.12.2-old.jar"
        extra.write_bytes(b"stale")
        with self.assertRaisesRegex(runner.LegError, "exactly one"):
            self.plan()
        # Moving/deleting is unnecessary: use a separate deliberately empty input directory.
        empty = self.root / "empty"
        empty.mkdir()
        with self.assertRaisesRegex(runner.LegError, "exactly one"):
            runner.make_plan("missing", empty, "plain", "amd", 1, [], root=self.root)

    def test_missing_helper_library_profile_and_mode_inputs_fail_prelaunch(self):
        for mode in ("cohort", "e2e", "recipe"):
            with self.subTest(mode=mode), self.assertRaisesRegex(runner.LegError, "missing directory"):
                self.plan(mode=mode)
        with patch.dict(os.environ, CELTIUM_INSTANCE_COHORT=str(self.root / "absent")):
            with self.assertRaisesRegex(runner.LegError, "missing directory"):
                self.plan()
        # Real incomplete fixtures, not a mock that bypasses all file checks.
        for missing in ("headless_wayland.py", "lwjgl-vulkan-3.4.1.jar"):
            incomplete = self.root / missing
            (incomplete / "scripts").mkdir(parents=True)
            (incomplete / ".gv-verify/lib").mkdir(parents=True)
            (incomplete / "scripts/prism_validation.py").write_text(FAKE_HARNESS)
            if missing != "headless_wayland.py":
                (incomplete / "scripts/headless_wayland.py").write_text("# fake")
            if missing != "lwjgl-vulkan-3.4.1.jar":
                (incomplete / ".gv-verify/lib/lwjgl-vulkan-3.4.1.jar").write_bytes(b"fake")
            with self.subTest(missing=missing), self.assertRaisesRegex(runner.LegError, "missing file"):
                runner.make_plan("missing", self.jars, "plain", "amd", 1, [], root=incomplete)
        self.assertFalse((self.root / ".gv-verify/test.out").exists())

    def test_every_existing_artifact_is_preserved_and_no_reuse(self):
        for key in ("game", "output", "runtimeLock", "manifest", "result"):
            leg = key.lower()
            plan = self.plan(leg)
            target = plan.paths[key]
            if key == "game":
                target.mkdir()
                (target / "evidence.txt").write_text("preserve")
            else:
                target.write_text("preserve")
            with self.subTest(key=key), self.assertRaisesRegex(runner.LegError, "already exists"):
                self.plan(leg)
            self.assertEqual((target / "evidence.txt").read_text() if key == "game" else target.read_text(), "preserve")
        plan = self.plan("race")
        plan.paths["output"].write_text("other owner's output")
        with self.assertRaises(FileExistsError):
            runner.execute(plan)
        self.assertEqual(plan.paths["output"].read_text(), "other owner's output")

    def test_jvm_arguments_are_whole_and_device_environment_is_explicit(self):
        extras = ["-Dceltium.validation.screen=hello world * [literal]", "-Xmx3g", "-Dcustom=a=b"]
        with patch.dict(os.environ, PYTHONPATH="/untrusted/.gv-verify", DRI_PRIME="wrong", MESA_VK_DEVICE_SELECT="wrong"):
            result = self.execute(extras=extras, gpu="intel")
        self.assertTrue(result["accepted"], result)
        argv = json.loads((self.root / ".gv-verify/run-test/argv.json").read_text())
        self.assertEqual(argv["jvmArgs"][-3:], extras)
        self.assertEqual(argv["DRI_PRIME"], "pci-0000_00_02_0!")
        self.assertIsNone(argv["MESA_VK_DEVICE_SELECT"])
        self.assertIsNone(argv["PYTHONPATH"])
        self.assertFalse((self.root / ".celtium-work/gpu.lock").exists())

    def test_cli_thin_shell_forwards_spaces_and_optional_duration(self):
        shell = Path(runner.__file__).with_name("gv-leg.sh")
        done = subprocess.run(["bash", str(shell), "cli", str(self.jars), "plain", "amd", "1", "-Dcustom=has spaces *"],
                              capture_output=True, text=True, timeout=10)
        self.assertEqual(done.returncode, 0, done.stdout + done.stderr)
        argv = json.loads((self.root / ".gv-verify/run-cli/argv.json").read_text())
        self.assertEqual(argv["jvmArgs"][-1], "-Dcustom=has spaces *")
        # The default duration need not be waited out: the fake harness exits immediately.
        done = subprocess.run(["bash", str(shell), "default", str(self.jars), "plain", "amd"],
                              capture_output=True, text=True, timeout=10)
        self.assertEqual(done.returncode, 0, done.stdout + done.stderr)

    def test_reject_unusable_extra_and_known_wrong_control(self):
        for extra in ("--game-dir=/escape", "argument", "-D", "-Dceltium.validation.presentedDumpEvery=0",
                      "-Dceltium.vulkan.glless=false",
                      "-Dceltium.validation.dumpPresentedImage=false"):
            with self.subTest(extra=extra), self.assertRaises(runner.LegError):
                self.plan(extras=[extra])
        # The one control that may be turned off: the validation layer is the correctness arm's switch, and the
        # shipping configuration runs without it - measured at 6.6 ms of an 11.0 ms frame on the standard scene, so
        # a performance arm has to be able to say so. The plan keeps the two numbers apart.
        for extra in ("-Dceltium.vulkan.validation=false", "-Dceltium.vulkan.presentReadback=false"):
            with self.subTest(extra=extra):
                plan = self.plan(extras=[extra])
                self.assertEqual(plan.command.count(f"--jvm-arg={extra}"), 1)
        with self.assertRaisesRegex(runner.LegError, "celtium.vulkan.presentedDumpEvery"):
            self.plan(extras=["-Dceltium.validation.presentedDumpEvery=0"])

    def test_e2e_replaced_renderers_are_excluded_from_both_pack_inputs(self):
        from prism_validation import copy_pack_into_game_dir
        staged = self.root / ".celtium-work/modpack-p0/e2e-staged/mods"
        staged.mkdir(parents=True)
        replaced = ("Nothirium-test.jar", "nothirium-old.jar", "Naughthirium-test.jar", "naughthirium-old.jar")
        for name in (*replaced, "content-mod.jar"):
            (staged / name).write_bytes(name.encode())
            (self.instance / "minecraft/mods" / name).write_bytes(name.encode())
        plan = self.plan(mode="e2e")
        exclusions = [plan.command[i + 1] for i, arg in enumerate(plan.command[:-1])
                      if arg == "--exclude-instance-mod"]
        self.assertEqual(exclusions, plan.manifest["instanceModExclusions"])
        self.assertFalse(any(Path(dep["source"]).name in replaced for dep in plan.manifest["dependencies"]))
        # Exercise the real copy helper, not a fake launcher that never copies instance mods.
        isolated = self.root / "copy-helper-only"
        isolated.mkdir()
        copy_pack_into_game_dir(self.instance / "minecraft", isolated, [], exclusions)
        self.assertEqual([p.name for p in (isolated / "mods").iterdir()], ["content-mod.jar"])
        self.assertTrue(all((self.instance / "minecraft/mods" / name).is_file() for name in replaced))
        result = runner.execute(plan, timeout=5, terminate_grace=.1)
        self.assertTrue(result["accepted"], result)
        # A later unexpected/reintroduced copy must invalidate acceptance too.
        (plan.paths["game"] / "mods/Nothirium-unexpected.jar").write_bytes(b"bad input")
        rejected = runner.postcheck(plan, 0)
        self.assertFalse(rejected["accepted"], rejected)
        self.assertTrue(any("replaced renderer(s) remain" in p for p in rejected["problems"]))

    def test_recipe_copies_content_and_manifest_dependencies_have_sources(self):
        for name in ("resources", "structures", "scripts"):
            path = self.instance / "minecraft" / name
            path.mkdir()
            (path / "fixture.txt").write_text(name)
        result = self.execute(mode="recipe")
        self.assertEqual(result["exitCode"], 0, result)
        game = self.root / ".gv-verify/run-test"
        argv = json.loads((game / "argv.json").read_text())
        self.assertEqual(argv["supplemental"], ["resources", "structures", "scripts"])
        for name in argv["supplemental"]:
            self.assertEqual((game / name / "fixture.txt").read_text(), name)
        manifest = json.loads((self.root / ".gv-verify/test-manifest.json").read_text())
        self.assertEqual(set(manifest["artifacts"]), {"renderer", "fixture", "harness", "headless"})
        for dependency in manifest["dependencies"]:
            self.assertTrue(Path(dependency["source"]).is_absolute())
            self.assertEqual(runner.sha256(Path(dependency["source"])), dependency["sha256"])
            self.assertEqual(runner.sha256(game / dependency["path"]), dependency["sha256"])
        saved = json.loads((self.root / ".gv-verify/test-result.json").read_text())
        self.assertIs(saved["accepted"], True)
        self.assertEqual(saved["exitCode"], 0)

    def test_runner_provenance_is_consumable_by_conservative_pruner(self):
        result = self.execute()
        self.assertTrue(result["accepted"], result)
        script = Path(runner.__file__).with_name("gv-prune.py")
        spec = importlib.util.spec_from_file_location("gv_prune_integration", script)
        prune = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(prune)
        game = self.root / ".gv-verify/run-test"
        preserved = {str(p.relative_to(game)): p.read_bytes() for p in game.rglob("*")
                     if p.is_file() and p.parent.name not in ("mods", "test-libraries")}
        with redirect_stdout(io.StringIO()):
            code = prune.main(["--apply", "--keep", "0"], root=self.root)
        self.assertEqual(code, 0)
        self.assertEqual(list((game / "mods").glob("*.jar")), [])
        self.assertEqual(list((game / "test-libraries").glob("*.jar")), [])
        self.assertEqual(preserved, {str(p.relative_to(game)): p.read_bytes() for p in game.rglob("*")
                                     if p.is_file() and p.parent.name not in ("mods", "test-libraries")})
        self.assertEqual(len(list(self.jars.glob("*.jar"))), 2)

    def test_hash_count_and_runtime_hash_mismatches_are_fatal(self):
        scenarios = ("renderer_hash_mismatch", "fixture_hash_mismatch", "renderer_count_mismatch",
                     "fixture_count_mismatch", "missing_renderer", "wrong_lock_hash")
        for number, scenario in enumerate(scenarios):
            with self.subTest(scenario=scenario):
                result = self.execute(scenario, leg=f"bad-{number}")
                self.assertFalse(result["accepted"], result)
                self.assertNotEqual(result["exitCode"], 0, result)
                self.assertTrue(result["problems"], result)

    def test_missing_dirty_wrong_gpu_or_invalid_evidence_cannot_pass(self):
        scenarios = ("missing_lock", "missing_process", "missing_fixture", "malformed_fixture", "missing_gate",
                     "false_gate", "missing_verdict", "dirty", "missing_clean", "wrong_gpu", "missing_device",
                     "preflight_gpu", "wrong_stage", "bad_png", "escaped_picture")
        for number, scenario in enumerate(scenarios):
            with self.subTest(scenario=scenario):
                result = self.execute(scenario, leg=f"no-pass-{number}")
                self.assertFalse(result["accepted"], result)
                self.assertNotEqual(result["exitCode"], 0, result)
                self.assertTrue(result["problems"], result)

    def test_both_old_and_fixed_harness_uncalibrated_exit_are_inconclusive(self):
        for number, scenario in enumerate(("uncalibrated_0", "uncalibrated_2")):
            with self.subTest(scenario=scenario):
                result = self.execute(scenario, leg=f"uncal-{number}")
                self.assertEqual(result["verdict"], "INCONCLUSIVE", result)
                self.assertFalse(result["accepted"], result)
                self.assertEqual(result["exitCode"], 2, result)

    def wait_ready(self, process, game):
        deadline = time.monotonic() + 5
        while time.monotonic() < deadline:
            if (game / "ready").exists():
                return int((game / "child.pid").read_text())
            if process.poll() is not None:
                self.fail(f"fake harness runner exited before readiness: {process.communicate()}")
            time.sleep(.02)
        self.fail("fake harness never became ready")

    def test_cancellation_reaps_own_uncooperative_child_and_preserves_evidence(self):
        os.environ["FAKE_SCENARIO"] = "hang"
        command = [sys.executable, "-B", runner.__file__, "cancel", str(self.jars), "plain", "amd", "1"]
        process = subprocess.Popen(command, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
        game = self.root / ".gv-verify/run-cancel"
        try:
            child = self.wait_ready(process, game)
            process.send_signal(signal.SIGTERM)
            stdout, stderr = process.communicate(timeout=10)
            self.assertEqual(process.returncode, 128 + signal.SIGTERM, stdout + stderr)
            self.assertFalse(Path(f"/proc/{child}").exists(), "owned child was not reaped")
            self.assertTrue((game / "argv.json").exists())
            result = json.loads((self.root / ".gv-verify/cancel-result.json").read_text())
            self.assertFalse(result["accepted"])
            self.assertEqual(result["verdict"], "CANCELLED")
        finally:
            if process.poll() is None:
                process.send_signal(signal.SIGTERM)
                process.communicate(timeout=10)

    def test_timeout_is_nonzero_and_successful_harness_cannot_leave_orphan(self):
        os.environ["FAKE_SCENARIO"] = "hang"
        result = runner.execute(self.plan("timeout"), timeout=.4, terminate_grace=.1)
        self.assertEqual(result["exitCode"], 124, result)
        child = int((self.root / ".gv-verify/run-timeout/child.pid").read_text())
        self.assertFalse(Path(f"/proc/{child}").exists())
        result = self.execute("orphan", leg="orphan")
        self.assertFalse(result["accepted"], result)
        self.assertNotEqual(result["exitCode"], 0, result)
        child = int((self.root / ".gv-verify/run-orphan/child.pid").read_text())
        self.assertFalse(Path(f"/proc/{child}").exists())

    def test_unmanaged_client_is_a_fail_fast_preflight_error(self):
        with patch.object(runner, "foundation_pids", return_value=[123]):
            with self.assertRaisesRegex(runner.LegError, "another Foundation"):
                self.plan()
        self.assertFalse((self.root / ".gv-verify/test.out").exists())


if __name__ == "__main__":
    unittest.main()
