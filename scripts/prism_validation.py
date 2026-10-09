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
    report = {
        "scope": "runtime-lock; no rendering, compatibility or performance claim",
        "sourceInstance": str(instance),
        "platform": platform.platform(),
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
        report["mods"].append({"file": path.name, "sha256": digest(path), "size": path.stat().st_size})
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
    parser.add_argument("--headless", action="store_true", help="Use an isolated GPU-rendered Sway/Xwayland display")
    parser.add_argument("--render-node", type=Path, help="Explicit DRM render node for the headless compositor")
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
    # Never merge another experiment or source game with these test writes.
    game.mkdir(parents=True, exist_ok=False)
    for name in ("mods", "config", "resourcepacks"):
        source = source_game / name
        if source.is_dir():
            shutil.copytree(source, game / name)
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
    report["isolatedGameDirectory"] = str(game)
    args.output.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    if not args.run:
        return 0
    player = "CeltiumValidation"
    player_uuid = uuid.UUID(bytes=hashlib.md5(("OfflinePlayer:" + player).encode("utf-8")).digest(), version=3).hex
    validation_args = ["-Dceltium.validation=true"] if args.validate else []
    command = [str(java), "-Xms512m", "-Xmx2g", "--enable-native-access=ALL-UNNAMED",
               *jvm_args, *args.jvm_arg, *validation_args, "-cp", ":".join(cp), main_class,
               "--username", player, "--version", "1.12.2", "--gameDir", str(game),
               "--assetsDir", str(assets), "--assetIndex", asset_index,
               "--uuid", player_uuid, "--accessToken", "0", "--userType", "legacy",
               "--versionType", "release", "--width", "1280", "--height", "720"]
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
        # Keep the test client's OpenAL audio off the desktop speakers.
        if args.headless:
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
        outcome["screenshotSaved"] = screenshot.is_file() and screenshot.stat().st_size > 24 and screenshot.read_bytes()[:8] == b"\x89PNG\r\n\x1a\n"
        if not outcome["screenshotSaved"] and code == 0:
            code = 1
        if args.require_gpu:
            outcome["gpuRequirementMet"] = args.require_gpu.lower() in fixture.get("glRenderer", "").lower()
            if not outcome["gpuRequirementMet"] and code == 0:
                code = 1
        if outcome["fixtureStatus"] != "PASSED" and code == 0:
            code = 1
        # Validation messages are a failure, not a detail. Leaving this out is what let a run report PASSED with
        # thirty-six validation messages inside it: the fixture's counters only see the renderer's own errors, and the
        # layer reports through the log. The whole log is read after the process has exited, so a message emitted during
        # shutdown still counts.
        console = game / "logs/latest.log"
        messages = []
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
                        continue
                    if severity == "WARNING" and "deprecated" in text:
                        continue
                else:
                    # Unstructured text, which is what older logs and driver messages look like. Only clearly
                    # informational lines are skipped: everything else counts, so an error phrased in any style - the
                    # "Validation Error: [ VUID-... ]" form the audit used - cannot pass as clean.
                    if "Information" in text and "Error" not in text:
                        continue
                messages.append(("severity=%s " % severity if severity else "") + text)
        else:
            # No log at all is not a clean run: the old check treated a missing file as no messages.
            messages.append("the game log is missing, so the validation result is unknown")
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
    (game / "validation-process.json").write_text(json.dumps(outcome, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(outcome))
    return code if ended_by == "process-exit" else 124


if __name__ == "__main__":
    sys.exit(main())
