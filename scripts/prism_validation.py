#!/usr/bin/env python3
"""Lock a Linux x86_64 Prism profile and launch an isolated, offline test copy.

The source instance and shared library/asset caches are read only. Authentication
data is neither read nor copied. This currently supports local patched profiles,
such as the official Cleanroom instance; it is not a general Minecraft launcher.
"""

import argparse
import configparser
from contextlib import nullcontext
import hashlib
import json
import platform
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import time
import uuid

from headless_wayland import HeadlessWayland


def digest(path, algorithm="sha256"):
    result = hashlib.new(algorithm)
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            result.update(block)
    return result.hexdigest()


def coordinate_path(name):
    group, artifact, version, *classifier = name.split(":")
    suffix = "-" + classifier[0] if classifier else ""
    return Path(group.replace(".", "/")) / artifact / version / f"{artifact}-{version}{suffix}.jar"


def allowed(library):
    name = library["name"]
    if "natives-" in name and not name.endswith(":natives-linux"):
        return False
    permitted = not library.get("rules")
    for rule in library.get("rules", []):
        system = rule.get("os", {})
        matches = system.get("name", "linux") == "linux"
        matches &= bool(re.search(system.get("arch", ".*"), "amd64"))
        matches &= bool(re.search(system.get("version", ".*"), platform.release()))
        matches &= not any(rule.get("features", {}).values())
        if matches:
            permitted = rule["action"] == "allow"
    return permitted


def inspect(instance, java):
    prism = instance.parent.parent
    pack = json.loads((instance / "mmc-pack.json").read_text())
    libraries = {}
    main_jar = None
    main_class = None
    tweakers = []
    jvm_args = []
    assets = None
    patches = []
    for component in pack["components"]:
        patch = instance / "patches" / (component["uid"] + ".json")
        if not patch.exists():
            raise ValueError(f"A local patch is required for {component['uid']}")
        data = json.loads(patch.read_text())
        patches.append({"uid": component["uid"], "version": data["version"], "sha256": digest(patch)})
        main_class = data.get("mainClass", main_class)
        main_jar = data.get("mainJar", main_jar)
        assets = data.get("assetIndex", assets)
        tweakers.extend(data.get("+tweakers", []))
        jvm_args.extend(data.get("+jvmArgs", []))
        for lib in data.get("libraries", []):
            if allowed(lib):
                group, artifact, version, *classifier = lib["name"].split(":")
                libraries[(group, artifact, tuple(classifier))] = lib
    if not main_jar or not main_class or not assets:
        raise ValueError("Profile lacks a main jar, main class or asset index")

    if not java:
        config = configparser.ConfigParser(interpolation=None)
        config.read(instance / "instance.cfg")
        java = Path(config["General"]["JavaPath"])
    java = java.resolve()
    java_version = subprocess.run([str(java), "-version"], capture_output=True, text=True, check=True)
    # Provenance (audit S24): which commit, whether the tree was clean, and which flags actually reached the JVM. Without
    # these a measurement cannot be traced back to the code and configuration that produced it.
    def git(*args):
        try:
            out = subprocess.run(["git", *args], capture_output=True, text=True, cwd=Path(__file__).resolve().parent.parent)
            return out.stdout.strip() if out.returncode == 0 else None
        except OSError:
            return None

    dirty = git("status", "--porcelain")
    report = {
        "scope": "runtime-lock; no rendering, compatibility or performance claim",
        "sourceInstance": str(instance),
        "platform": platform.platform(),
        "git": {
            "head": git("rev-parse", "HEAD"),
            "subject": git("log", "-1", "--pretty=%s"),
            "dirty": bool(dirty),
            "dirtyFiles": dirty.splitlines() if dirty else [],
        },
        "effectiveConfig": {
            # The profile's arguments and the ones the runner adds are different channels, and only the union is what
            # actually reaches the JVM - recording just the first was the gap this field was added to close (S24).
            "profileJvmArgs": jvm_args,
            "runnerJvmArgs": [],
            "jvmArgs": list(jvm_args),
            "tweakers": tweakers,
            "mainClass": main_class,
            "mainJar": str(main_jar),
        },
        "java": {"executable": str(java), "version": (java_version.stderr + java_version.stdout).strip()},
        "components": patches,
        "libraries": [],
        "mods": [],
    }
    classpath = []
    for lib in list(libraries.values()) + [main_jar]:
        descriptor = lib.get("downloads", {}).get("artifact", {})
        # Prism stores by the coordinate, including patched version suffixes
        # such as 3.4.1-unsafe, even when downloads.artifact.path differs.
        path = prism / "libraries" / coordinate_path(lib["name"])
        if not path.is_file() and descriptor.get("path"):
            path = prism / "libraries" / descriptor["path"]
        if not path.is_file():
            raise FileNotFoundError(f"Missing cached library: {path}")
        if descriptor.get("sha1") and digest(path, "sha1") != descriptor["sha1"]:
            raise ValueError(f"Library does not match the profile SHA-1: {path}")
        report["libraries"].append({"coordinate": lib["name"], "sha256": digest(path), "size": path.stat().st_size})
        classpath.append(str(path))
    for path in sorted((instance / "minecraft/mods").glob("*.jar")):
        entry = {"file": path.name, "sha256": digest(path), "size": path.stat().st_size}
        # The fixture is what produces the acceptance verdict, so it is identified rather than left among the mods (S24).
        if "validation" in path.name:
            entry["role"] = "validation-fixture"
            report["fixture"] = {"file": path.name, "sha256": entry["sha256"]}
        report["mods"].append(entry)
    # The fixture the runner passes in is the one that produces the verdict, so it is recorded by hash even though it is
    # copied into the isolated game directory later rather than being an instance mod (S24).
    return report, java, classpath, main_class, tweakers, jvm_args, prism / "assets", assets["id"]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("instance", type=Path, help="Prism instance directory (parent of minecraft/)")
    parser.add_argument("--output", type=Path, required=True, help="Runtime lock JSON")
    parser.add_argument("--java", type=Path)
    parser.add_argument("--game-dir", type=Path, help="Create a fresh isolated game directory")
    parser.add_argument("--mod", type=Path, action="append", default=[])
    parser.add_argument("--library", type=Path, action="append", default=[], help="Extra test-only classpath jar; the source profile stays unchanged")
    parser.add_argument("--force-x11", action="store_true",
                        help="Hide the Wayland socket from the game so GLFW uses the Xwayland display instead; the "
                             "Wayland keyboard path compiles the compositor's keymap with libxkbcommon and a "
                             "rolling distribution can break that path for the client")
    parser.add_argument("--jvm-arg", action="append", default=[])
    parser.add_argument("--run", action="store_true")
    parser.add_argument("--validate", action="store_true", help="Enable and require the separately installed validation fixture")
    parser.add_argument("--duration", type=int, default=60, help="Maximum runtime in seconds; a timeout is not success")
    parser.add_argument("--slot-wait", type=int, default=900,
                        help="Seconds to wait for the single-game slot; the slot is a lock file in .celtium-work/game-slot")
    parser.add_argument("--headless", action="store_true", help="Use an isolated GPU-rendered Sway/Xwayland display")
    parser.add_argument("--render-node", type=Path, help="Explicit DRM render node for the headless compositor")
    parser.add_argument("--window-size", default="1280x720",
                        help="Window size the game is asked for (WxH, default 1280x720 = the headless output)")
    parser.add_argument("--require-gpu", help="Require this substring in the fixture's actual GL renderer")
    args = parser.parse_args()
    if sys.platform != "linux" or platform.machine() != "x86_64":
        parser.error("Only Linux x86_64 has been implemented for this validation launcher")
    if args.duration <= 0:
        parser.error("Duration must be positive")
    if (args.mod or args.library or args.run) and not args.game_dir:
        parser.error("--mod, --library and --run require an isolated --game-dir")
    if args.validate and not args.run:
        parser.error("--validate requires --run")
    if args.headless and (not args.run or not args.render_node):
        parser.error("--headless requires --run and an explicit --render-node")
    if args.require_gpu and not args.validate:
        parser.error("--require-gpu requires --validate")
    instance = args.instance.resolve()
    report, java, cp, main_class, tweakers, jvm_args, assets, asset_index = inspect(instance, args.java)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(f"Locked {len(report['libraries'])} library jars and {len(report['mods'])} mods")
    if not args.game_dir:
        return 0
    game = args.game_dir.resolve()
    source_game = instance / "minecraft"
    if game == source_game or game.is_relative_to(source_game) or source_game.is_relative_to(game):
        parser.error("The test directory must be separate from the source instance")
    # Never merge another experiment or source game with these test writes - but a directory left behind by a run that
    # died before it started is not another experiment. A run killed while it waited for the game slot leaves the
    # directory already created (the slot is taken before the launch), and the retry then failed with "File exists" and
    # cost a second slot wait; acceptance hit exactly that. The directory is only reclaimed when it holds no report,
    # which is the mark of a run that never produced evidence.
    if game.exists():
        report_path = game / "logs" / "celtium-validation.json"
        leftovers = list(game.iterdir())
        if not report_path.exists() and not (game / "screenshots").exists():
            shutil.rmtree(game)
        else:
            parser.error(f"The test directory exists and holds a report or screenshots: {game}")
    game.mkdir(parents=True, exist_ok=False)
    # A jar the caller supplies with --mod is not copied from the instance, even when the instance has one with the
    # same name. It used to be a hard error ("Test mod would overwrite a copied mod"), which was right when the instance
    # was never expected to hold one - but the user keeps a build in their instance mods to launch the game themselves,
    # and that made every test run fail at startup with the jar in place. The caller's jar wins for the names it names;
    # a genuine collision between two supplied files is still an error below.
    supplied = {mod.name for mod in args.mod}
    # The instance's own copy of this project's jars is never carried into a test game dir, whatever the caller names
    # its frozen copies. Excluding only the exact supplied names was not enough: a worker who freezes the build under a
    # different file name (to keep two legs on one hash) then gets the instance's jar copied alongside it, and the game
    # dies on a duplicate mod id - which cost fog-path a leg before this line existed.
    ignored_mods = ("celtium-forge-*.jar", "celtium-validation-client-*.jar") + tuple(supplied)
    for name in ("mods", "config", "resourcepacks"):
        source = source_game / name
        if source.is_dir():
            ignore = shutil.ignore_patterns(*ignored_mods) if name == "mods" else None
            shutil.copytree(source, game / name, ignore=ignore)
    (game / "mods").mkdir(exist_ok=True)
    for mod in args.mod:
        target = game / "mods" / mod.name
        if target.exists():
            raise FileExistsError(f"Test mod would overwrite a copied mod: {target}")
        shutil.copy2(mod, target)
    report["extraTestLibraries"] = []
    if args.library:
        library_dir = game / "test-libraries"
        library_dir.mkdir()
        for library in args.library:
            target = library_dir / library.name
            if library.suffix != ".jar" or target.exists():
                raise ValueError(f"Extra test library must be a distinct jar: {library}")
            shutil.copy2(library, target)
            cp.append(str(target))
            report["extraTestLibraries"].append({"file": target.name, "sha256": digest(target)})
    report["testMods"] = [{"file": p.name, "sha256": digest(p)} for p in sorted((game / "mods").glob("*.jar"))]
    # Reference the capabilities the renderer recorded rather than duplicating them (audit S24): the driver and limits
    # belong to the run, and that file is where they are measured rather than restated.
    capabilities = game / "logs" / "celtium-capabilities.json"
    if capabilities.is_file():
        try:
            caps = json.loads(capabilities.read_text())
            report["driver"] = {
                "source": str(capabilities.relative_to(game)),
                "deviceName": caps.get("deviceName") or caps.get("device", {}).get("deviceName"),
                "driverVersion": caps.get("driverVersion") or caps.get("device", {}).get("driverVersion"),
                "apiVersion": caps.get("apiVersion") or caps.get("device", {}).get("apiVersion"),
            }
        except (OSError, ValueError):
            report["driver"] = {"source": str(capabilities.relative_to(game)), "error": "unreadable"}    # The runner's own arguments are a separate channel from the profile's, and only the union reaches the JVM.
    report["effectiveConfig"]["runnerJvmArgs"] = [str(a) for a in (args.jvm_arg or [])]
    report["effectiveConfig"]["jvmArgs"] = (list(report["effectiveConfig"].get("profileJvmArgs", []))
            + report["effectiveConfig"]["runnerJvmArgs"])
    # The fixture that produces the verdict is passed in by the runner, not present as an instance mod.
    for candidate in (args.mod or []):
        candidate = Path(candidate)
        if candidate.is_file() and "validation" in candidate.name:
            report["fixture"] = {"file": candidate.name, "sha256": digest(candidate), "source": str(candidate)}

    report["isolatedGameDirectory"] = str(game)
    args.output.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    if not args.run:
        return 0
    # ---- one game instance at a time, arbitrated rather than hoped for -------------------------------
    # Every worker was told to check for a running client before launching, and four of them passed that check in the
    # same second: the check-then-launch window is the problem, not anyone's discipline. The slot is a directory created
    # atomically, holding the pid of the process that owns it; a stale slot (owner gone) is reclaimed, and a live one is
    # waited for. Two runs on one GPU are not evidence, and the runs they cost are the expensive part.
    slot = Path(__file__).resolve().parent.parent / ".celtium-work" / "game-slot"
    slot.mkdir(parents=True, exist_ok=True)
    lock = slot / "owner.pid"
    deadline = time.monotonic() + args.slot_wait
    while True:
        try:
            fd = os.open(lock, os.O_CREAT | os.O_EXCL | os.O_WRONLY)
            os.write(fd, str(os.getpid()).encode("ascii"))
            os.close(fd)
            break
        except FileExistsError:
            try:
                holder = int(lock.read_text(encoding="ascii").strip() or "0")
            except (OSError, ValueError):
                holder = 0
            alive = False
            if holder > 0:
                try:
                    os.kill(holder, 0)
                    alive = True
                except ProcessLookupError:
                    alive = False
                except PermissionError:
                    alive = True
            if not alive:
                print(f"Reclaiming the game slot from pid {holder}, which is gone")
                lock.unlink(missing_ok=True)
                continue
            if time.monotonic() >= deadline:
                parser.error(f"the game slot is held by pid {holder} and --slot-wait ({args.slot_wait}s) expired; "
                             "one instance at a time is a measurement requirement, not a courtesy")
            print(f"Waiting for the game slot (held by pid {holder}) ...")
            time.sleep(5)
    try:
        return run_locked(args, game, cp, main_class, tweakers, jvm_args, java, assets, asset_index, parser)
    finally:
        lock.unlink(missing_ok=True)


def run_locked(args, game, cp, main_class, tweakers, jvm_args, java, assets, asset_index, parser):
    player = "CeltiumValidation"
    player_uuid = uuid.UUID(bytes=hashlib.md5(("OfflinePlayer:" + player).encode("utf-8")).digest(), version=3).hex
    validation_args = ["-Dceltium.validation=true"] if args.validate else []
    # The window the game is asked for. It used to be hard-coded 1280x720, which is the headless compositor's output
    # size and therefore the only size any placement measurement could be taken at - while the operator's own window
    # is 1920x1080, and the presented frame's placement at that size is exactly what the "the menu is shifted" report
    # is about. `--window-size WxH` lets a leg start at the size in question.
    window_size = args.window_size.lower().split("x")
    if len(window_size) != 2:
        parser.error("--window-size must be WxH, e.g. 1920x1080")
    try:
        window_width = int(window_size[0])
        window_height = int(window_size[1])
    except ValueError:
        parser.error("--window-size must be WxH with integer values, e.g. 1920x1080")
    command = [str(java), "-Xms512m", "-Xmx2g", "--enable-native-access=ALL-UNNAMED",
               *jvm_args, *args.jvm_arg, *validation_args, "-cp", ":".join(cp), main_class,
               "--username", player, "--version", "1.12.2", "--gameDir", str(game),
               "--assetsDir", str(assets), "--assetIndex", asset_index,
               "--uuid", player_uuid, "--accessToken", "0", "--userType", "legacy",
               "--versionType", "release", "--width", str(window_width), "--height", str(window_height)]
    for tweaker in tweakers:
        command.extend(["--tweakClass", tweaker])
    (game / "validation-command.json").write_text(json.dumps(command, indent=2) + "\n", encoding="utf-8")
    started = time.monotonic()
    display = HeadlessWayland(game / "headless", args.render_node) if args.headless else nullcontext()
    with display as headless, (game / "validation-console.log").open("wb") as log:
        environment = headless.environment if headless else os.environ.copy()
        if args.force_x11:
            environment.pop("WAYLAND_DISPLAY", None)
            environment.pop("WAYLAND_SOCKET", None)
        # Keep the test client's OpenAL audio off the desktop speakers. Not only for --headless: a headless-compositor
        # run still plays its music through the system mixer, which the user hears as "music only, and it is loud"
        # from a run they cannot even see. A run that genuinely wants audio back sets CELTIUM_ALLOW_AUDIO=1.
        if not os.environ.get("CELTIUM_ALLOW_AUDIO"):
            environment["ALSOFT_DRIVERS"] = "null"
        process = subprocess.Popen(command, cwd=game, env=environment, stdout=log, stderr=subprocess.STDOUT)
        ended_by = "process-exit"
        try:
            try:
                code = process.wait(timeout=args.duration)
            except subprocess.TimeoutExpired:
                ended_by = "time-limit"
                process.terminate()
                try:
                    code = process.wait(timeout=15)
                except subprocess.TimeoutExpired:
                    process.kill()
                    code = process.wait()
        finally:
            if process.poll() is None:
                process.kill()
                process.wait()
    outcome = {"exitCode": code, "endedBy": ended_by, "elapsedSeconds": time.monotonic() - started}
    if args.headless:
        outcome["headlessSession"] = str(game / "headless/session.json")
    if args.validate:
        result = game / "logs/celtium-validation.json"
        fixture = json.loads(result.read_text()) if result.exists() else {}
        outcome["fixtureStatus"] = fixture.get("status", "MISSING_REPORT")
        screenshot = game / "screenshots/celtium-validation.png"
        # A PNG signature is not evidence of a picture. Under the GL-less stub the game-level screenshot is black by
        # construction - there is no GL default framebuffer to read - yet it passes a signature-and-size check, so the
        # field read as evidence while proving nothing (measured: 1 distinct colour, 0 bands, reported as saved). The
        # fixture now says which file its verdict is about (`pictureEvidenceSource` and `pictureFile_<stage>`), so the
        # harness reports that file and calls the GL screenshot what it is in this configuration.
        outcome["screenshotSaved"] = screenshot.is_file() and screenshot.stat().st_size > 24 and screenshot.read_bytes()[:8] == b"\x89PNG\r\n\x1a\n"
        evidence_source = fixture.get("pictureEvidenceSource") if isinstance(fixture, dict) else None
        outcome["pictureEvidenceSource"] = evidence_source
        if evidence_source:
            # Which stage's picture the verdict is about. A GUI run dumps both a world and a gui frame, and taking
            # whichever key the JSON happens to list first means a GUI verdict can be checked against the world image.
            stage = "gui" if fixture.get("sceneGuiStage") else "world"
            stage_file = fixture.get("pictureFile_" + stage) if isinstance(fixture, dict) else None
            if not (isinstance(stage_file, str) and stage_file):
                stage_file = next((v for k, v in (fixture or {}).items()
                                   if k.startswith("pictureFile_") and isinstance(v, str) and v), None)
                stage = "other" if stage_file else stage
            outcome["pictureStage"] = stage
            if stage_file:
                evidence = game / stage_file
                outcome["pictureFile"] = stage_file
                is_png = (evidence.is_file() and evidence.stat().st_size > 24
                          and evidence.read_bytes()[:8] == b"\x89PNG\r\n\x1a\n")
                outcome["picturePresent"] = is_png
                if not is_png:
                    outcome["pictureEvidenceProblem"] = (
                        "picture source " + str(evidence_source) + " names " + str(stage_file)
                        + ", which is missing or not a PNG")
            outcome["screenshotIsEvidence"] = evidence_source == "gl-screenshot"
            if evidence_source != "gl-screenshot":
                # Do not let a black GL screenshot stand in for the picture, and do not fail a run for its absence:
                # the presented dump is the evidence in this configuration - but the dump must be a real PNG, which
                # is what the check above is for. The previous form set this True without reading the file, which is
                # how a run could call itself evidenced while naming a file that did not exist.
                outcome["screenshotSaved"] = bool(outcome.get("picturePresent"))
        if not outcome["screenshotSaved"] and code == 0:
            code = 1
        # Which device actually rendered. The GL renderer string is not an answer in the GL-less configuration - it is
        # the stub's own name - so the authoritative line is the one the renderer prints when it has chosen its physical
        # device. The GL string stays as the bridge's preflight and as the fallback for a run that never reached device
        # selection, and the source of the verdict is recorded rather than implied.
        console = game / "validation-console.log"
        native_name = native_uuid = None
        native_matched = None
        try:
            text = console.read_text(errors="replace") if console.is_file() else ""
            match = re.search(r'CELTIUM_NATIVE_DEVICE name="([^"]*)" uuid=([0-9a-fA-F]*) matchedOpenGl=(\w+)', text)
            if match:
                native_name, native_uuid, native_matched = match.group(1), match.group(2), match.group(3) == "true"
        except OSError:
            pass
        outcome["nativeDeviceName"] = native_name
        outcome["nativeDeviceUuid"] = native_uuid
        outcome["nativeDeviceMatchedOpenGl"] = native_matched
        outcome["glRendererPreflight"] = fixture.get("glRenderer")
        if args.require_gpu:
            if native_name is not None:
                outcome["gpuRequirementSource"] = "nativeDeviceName"
                outcome["gpuRequirementMet"] = args.require_gpu.lower() in native_name.lower()
            else:
                # No device line: the run never selected a physical device, so the GL renderer is all there is to go on.
                outcome["gpuRequirementSource"] = "glRendererPreflight"
                outcome["gpuRequirementMet"] = args.require_gpu.lower() in fixture.get("glRenderer", "").lower()
            if not outcome["gpuRequirementMet"] and code == 0:
                code = 1
        # UNCALIBRATED is not a pass, but it is also not a defect: the scene has no reference at this pose/variant, so
        # the picture gate refused to judge it. It is reported as its own outcome instead of being folded into either
        # the failure path or the pass path - an uncalibrated scene that exits 0 and an uncalibrated scene that exits 1
        # would both be wrong in the same way, by pretending the refusal was a verdict.
        outcome["fixtureUncalibrated"] = outcome["fixtureStatus"] == "UNCALIBRATED_PRESENTED_IMAGE"
        if outcome["fixtureUncalibrated"]:
            print("note: the picture gate did not judge this scene (no reference at this pose/variant) - "
                  + str(outcome["fixtureStatus"]))
        if outcome["fixtureStatus"] not in ("PASSED", "UNCALIBRATED_PRESENTED_IMAGE") and code == 0:
            code = 1
        # Validation messages are a failure, not a detail. Leaving this out is what let a run report PASSED with
        # thirty-six validation messages inside it: the fixture's counters only see the renderer's own errors, and the
        # layer reports through the log. The whole log is read after the process has exited, so a message emitted during
        # shutdown still counts.
        console = game / "logs/latest.log"
        messages = []
        # Allowed messages are recorded too, not silently dropped: the point of this counter is that a skipped line must be
        # visible as skipped. Two rounds running, a message read through a truncated preview looked like something it was
        # not, and a gate that says nothing about what it allowed invites exactly that mistake.
        allowed = []
        if console.exists():
            for line in console.read_text(errors="ignore").splitlines():
                marker = "Celtium Vulkan validation: "
                if marker not in line:
                    continue
                text = line.split(marker, 1)[1].strip()
                severity = None
                if text.startswith("severity="):
                    head, _, rest = text.partition(" ")
                    severity = head.split("=", 1)[1]
                    if rest.startswith("id="):
                        _, _, rest = rest.partition(" ")
                    text = rest
                    if severity in ("INFO", "VERBOSE"):
                        allowed.append("%s %s" % (severity, text))
                        continue
                    if severity == "WARNING" and "deprecated" in text:
                        allowed.append("WARNING(deprecated) %s" % text)
                        continue
                else:
                    # Unstructured text, which is what older logs and driver messages look like. Only clearly
                    # informational lines are skipped: everything else counts, so an error phrased in any style - the
                    # "Validation Error: [ VUID-... ]" form the audit used - cannot pass as clean.
                    if "Information" in text and "Error" not in text:
                        allowed.append("Information %s" % text)
                        continue
                messages.append(("severity=%s " % severity if severity else "") + text)
        else:
            # No log at all is not a clean run: the old check treated a missing file as no messages.
            messages.append("the game log is missing, so the validation result is unknown")
        outcome["validationAllowedCount"] = len(allowed)
        if allowed:
            outcome["validationAllowedFirst"] = allowed[:3]
        outcome["validationMessageCount"] = len(messages)
        outcome["validationClean"] = not messages
        if messages:
            outcome["validationFirstMessages"] = messages[:3]
            if code == 0:
                code = 1
    # The reported codes have to carry the verdict: a validation message is a failed run, not a note in the JSON.
    outcome["validationExitCode"] = code if ended_by == "process-exit" else 124
    if outcome.get("validationClean") is False:
        outcome["fixturePassed"] = outcome.get("fixtureStatus") == "PASSED"
        outcome["validationClean"] = False
        if outcome["validationExitCode"] == 0:
            outcome["validationExitCode"] = 1
    # The driver and limits the renderer recorded, referenced rather than duplicated (audit S24). This is read after the
    # run, which is when the file exists - reading it when the lock is written finds nothing, which is what the first
    # attempt did.
    capabilities = game / "logs" / "celtium-capabilities.json"
    if capabilities.is_file():
        try:
            caps = json.loads(capabilities.read_text())
            devices = caps.get("vulkanDevices") or []
            # The device this run used, not the first one the loader listed: those differ whenever more than one GPU is
            # present, which is exactly the case this reporting exists for. Matched by the UUID the renderer printed,
            # then by name; the first entry is only the last resort.
            device = {}
            for candidate in devices:
                if not isinstance(candidate, dict):
                    continue
                if native_uuid and candidate.get("deviceUUID") == native_uuid:
                    device = candidate
                    break
                if native_name and candidate.get("name") == native_name:
                    device = candidate
                    break
            if not device and devices:
                device = devices[0]
                flat_note = "first-listed"
            else:
                flat_note = "selected"
            outcome.setdefault("driverSelection", flat_note)
            flat = {"lwjglVersion": caps.get("lwjglVersion"),
                    "vulkanLoaderVersion": caps.get("vulkanLoaderVersion"),
                    "vulkanDeviceCount": len(devices)}
            for key in ("name", "deviceName", "driverName", "driverInfo", "driverVersion", "apiVersion", "deviceType",
                        "deviceId", "vendorId", "deviceUUID"):
                if isinstance(device, dict) and device.get(key) is not None:
                    flat[key] = device[key]
            outcome["driver"] = {"source": str(capabilities.relative_to(game)), **flat}
        except (OSError, ValueError) as error:
            outcome["driver"] = {"source": str(capabilities.relative_to(game)), "error": str(error)}
    (game / "validation-process.json").write_text(json.dumps(outcome, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(outcome))
    return code if ended_by == "process-exit" else 124


if __name__ == "__main__":
    sys.exit(main())
