#!/usr/bin/env python3
"""Run a fresh GPU leg; zero means the requested renderer's calibrated scene gates passed.

CLI: gv-leg.sh LEG JAR_DIR {cohort,plain,e2e,recipe,world} {amd,intel} [SECONDS] [-D...|-X...]
Native Vulkan is the default. CELTIUM_LEG_REFERENCE_RENDERER explicitly selects a GL reference,
whose GPU and screenshot evidence are checked as GL, never as native acceptance.
The tracked Prism harness owns the only game-slot lock. This wrapper never deletes
old artifacts, never changes the source instance, and never resumes a paused goal.
"""
from __future__ import annotations

import argparse
import binascii
from contextlib import contextmanager
import ctypes
from dataclasses import dataclass
import fnmatch
import hashlib
import json
import os
from pathlib import Path
import re
import shlex
import signal
import struct
import subprocess
import sys
import time
import zlib

RENDERER = "celtium-forge-mc1.12.2-*.jar"
FIXTURE = "celtium-validation-client-*.jar"
UNCALIBRATED = "UNCALIBRATED_PRESENTED_IMAGE"
SLOT_WAIT = 120
STARTUP_BUDGET = 120
# HeadlessWayland can spend up to nine seconds stopping its separate Sway session.
TERMINATE_GRACE = 12
NATIVE_DEVICE = re.compile(r'CELTIUM_NATIVE_DEVICE name="([^"]+)" uuid=([0-9a-fA-F-]+) matchedOpenGl=(?:true|false)')


class LegError(Exception):
    pass


@dataclass
class Plan:
    root: Path
    leg: str
    mode: str
    gpu: str
    duration: int
    paths: dict[str, Path]
    renderer: Path
    fixture: Path
    required_gpu: str
    command: list[str]
    environment: dict[str, str]
    manifest: dict
    reference_renderer: bool = False


def sha256(path: Path) -> str:
    result = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            result.update(block)
    return result.hexdigest()


def require_dir(path: Path) -> Path:
    if not path.is_dir():
        raise LegError(f"missing directory: {path}")
    return path.resolve()


def require_file(path: Path) -> Path:
    if not path.is_file():
        raise LegError(f"missing file: {path}")
    return path.resolve()


def jars_in(path: Path) -> list[Path]:
    require_dir(path)
    jars = sorted(p for p in path.glob("*.jar") if p.is_file())
    if not jars:
        raise LegError(f"no JARs in {path}")
    return jars


def one_jar(directory: Path, pattern: str) -> Path:
    matches = sorted(p for p in directory.glob(pattern) if p.is_file())
    if len(matches) != 1:
        raise LegError(f"expected exactly one {pattern} in {directory}, found {len(matches)}")
    return matches[0]


def git_state(root: Path) -> dict:
    def git(*args):
        try:
            done = subprocess.run(["git", "-C", str(root), *args], capture_output=True,
                                  text=True, timeout=5)
            return done.stdout.strip() if done.returncode == 0 else None
        except (OSError, subprocess.TimeoutExpired):
            return None
    dirty = git("status", "--porcelain")
    return {"head": git("rev-parse", "HEAD"), "dirty": bool(dirty) if dirty is not None else None,
            "dirtyFiles": dirty.splitlines() if dirty is not None else None}


def foundation_pids() -> list[int]:
    """Fail fast for an unmanaged client; the harness still arbitrates tracked clients."""
    pids = []
    for process in Path("/proc").glob("[0-9]*"):
        try:
            if b"foundation.boot.Foundation" in (process / "cmdline").read_bytes().split(b"\0"):
                pids.append(int(process.name))
        except (OSError, ValueError):
            pass  # A process can exit between directory enumeration and reading cmdline.
    return pids


def make_plan(leg: str, jar_dir: Path, mode: str, gpu: str, duration: int,
              extras: list[str], *, root: Path | None = None) -> Plan:
    if sys.platform != "linux":
        raise LegError("the native validation launcher and owned-child supervision require Linux")
    if not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9_-]{0,63}", leg):
        raise LegError("LEG must be 1-64 ASCII letters/digits/_/-, starting with a letter or digit")
    if type(duration) is not int or duration <= 0:
        raise LegError("duration must be a positive integer number of seconds")
    if mode not in ("cohort", "plain", "e2e", "recipe", "world") or gpu not in ("amd", "intel"):
        raise LegError("unknown mode or GPU")
    for arg in extras:
        # -XX: is part of the allowlist because profiling needs it: a JFR recording is started with
        # -XX:StartFlightRecording=..., and attributing sub-microsecond work with homemade brackets stopped working
        # once the brackets' own cost was the size of the thing being measured. The shape is still checked - a complete
        # option with a value, never a bare flag - so a typo cannot silently be passed to the JVM as something else.
        if "\0" in arg or not (re.fullmatch(r"-D[A-Za-z0-9_.-]+(?:=.*)?", arg, re.DOTALL)
                                 or (arg.startswith("-X") and len(arg) > 2 and not arg.startswith("-XX:"))
                                 or re.fullmatch(r"-XX:(?:[+-][A-Za-z][A-Za-z0-9_]+|[A-Za-z][A-Za-z0-9_]+=.+)", arg, re.DOTALL)):
            raise LegError(f"extra JVM argument must be a complete -Dname[=value], -X... or -XX:Name=value "
                           f"argument: {arg!r}")
        key, _, value = arg.partition("=")
        if key == "-Dceltium.validation.presentedDumpEvery":
            raise LegError("unknown celtium.validation.presentedDumpEvery; use -Dceltium.vulkan.presentedDumpEvery=0")
        if key in ("-Dceltium.vulkan.validation", "-Dceltium.vulkan.presentReadback") and value.lower() == "false":
            # The two opt-outs, and both are measurement switches rather than modes.
            # -Dceltium.vulkan.validation: the correctness arms keep the layer on (that is where the VUID evidence
            # comes from) while the shipping configuration has it off; measured on the standard scene it costs 6.6 ms
            # of an 11.0 ms frame.
            # -Dceltium.vulkan.presentReadback: the runner's default reads the presented image back on every present
            # (the picture check), which JFR put 16% of the client thread's samples inside - a GPU sync
            # and a full 1280x720 readback per frame. A performance arm turns both off and checks its picture through
            # the requested dump instead; the numbers from such a leg have to be labelled as the shipping arm.
            pass
        elif key in ("-Dceltium.vulkan.validation", "-Dceltium.validation",
                     "-Dceltium.vulkan.presentReadback", "-Dceltium.validation.dumpPresentedImage") and value.lower() != "true":
            raise LegError(f"this native acceptance runner requires {key}=true")
    root = require_dir(root or Path(os.environ.get("CELTIUM_ROOT", Path(__file__).resolve().parent.parent)))
    gv = root / ".gv-verify"
    if gv.is_symlink():
        raise LegError(f"artifact directory must not be a symlink: {gv}")
    require_dir(gv)
    harness = require_file(root / "scripts/prism_validation.py")
    headless = require_file(root / "scripts/headless_wayland.py")
    # The Vulkan bindings jar must be the LWJGL version the instance actually runs: Cleanroom 0.7 ships 3.4.3 and
    # exposes no GL, while 0.6 ships 3.4.1. The default is the version the validated 0.6 cohort uses.
    library = require_file(Path(os.environ.get("CELTIUM_LEG_LWJGL_VULKAN", str(gv / "lib/lwjgl-vulkan-3.4.1.jar"))))
    # Extra classpath jars for one leg, for features whose bindings the validated installation does not ship yet (the
    # in-process GLSL compiler is the first): `CELTIUM_LEG_EXTRA_LIBS` is a colon-separated list, kept out of the
    # standard set so a normal leg's classpath stays exactly what the product would have.
    extra_libraries = [Path(p) for p in os.environ.get("CELTIUM_LEG_EXTRA_LIBS", "").split(":") if p.strip()]
    for extra in extra_libraries:
        require_file(extra)
    jar_dir = require_dir(jar_dir)
    # The renderer can be replaced by an external jar for a reference arm: the same pack, scene, camera and fixture, with
    # Celeritas in place of this renderer, so a gate for a modpack world is calibrated from the reference that actually
    # draws it rather than from the flat fixture scene. The jar is named as it is - the harness only stages it - and the
    # leg is labelled by the caller (a "glref" prefix), which is what keeps the two arms apart in the evidence.
    reference_renderer = os.environ.get("CELTIUM_LEG_REFERENCE_RENDERER", "").strip()
    if reference_renderer:
        renderer = require_file(Path(reference_renderer))
    else:
        renderer = one_jar(jar_dir, RENDERER)
        # A broader family match catches stale/renamed renderer builds, not only the chosen version family.
        if len(list(jar_dir.glob("celtium-forge-*.jar"))) != 1:
            raise LegError(f"multiple or non-packaged renderer JARs in {jar_dir}")
    fixture = one_jar(jar_dir, FIXTURE)
    defaults = {
        "cohort": Path.home() / ".local/share/PrismLauncher/instances/cleanroom-0.6.13-alpha",
        "e2e": root / ".celtium-work/modpack-p0/prism-e2e-private/instances/e2e-1.93a-no-stellar-parallel",
        "recipe": root / ".celtium-work/modpack-p0/recipe-profile/instances/rlcraft-2.9.3-recipe",
        # The standard real-world profile: the clone of the cohort instance that carries the distributed map
        # (QMAGNET's Test Map) as the save "qmagnet". Clone it and install the map as that save to reproduce.
        "world": Path.home() / ".local/share/PrismLauncher/instances/celtium-world-accept",
    }
    profile = "cohort" if mode == "plain" else mode
    instance = require_dir(Path(os.environ.get(f"CELTIUM_INSTANCE_{profile.upper()}", defaults[profile])))
    require_file(instance / "mmc-pack.json")
    require_file(instance / "instance.cfg")
    require_dir(instance / "minecraft/mods")
    supplemental = ("resources", "structures", "scripts") if mode == "recipe" else ()
    for name in supplemental:
        require_dir(instance / "minecraft" / name)
    mods = [renderer, fixture]
    instance_exclusions = ["celtium-*pre-stub-changes.jar"]
    if mode == "cohort":
        mods.extend(jars_in(root / ".celtium-work/modcohort"))
        if os.environ.get("CLASH_DIR"):
            mods.extend(jars_in(Path(os.environ["CLASH_DIR"])))
    if mode == "e2e":
        # E2E_STAGED_MODS selects another pack's jars (E2E-E) for the same mode and checks.
        staged = jars_in(Path(os.environ.get("E2E_STAGED_MODS", root / ".celtium-work/modpack-p0/e2e-staged/mods")))
        exclusions = ["[Nn]othirium-*", "[Nn]aughthirium-*", *shlex.split(os.environ.get("E2E_EXCLUDE", ""))]
        # Filtering supplied mods alone is insufficient: the harness also copies
        # source-instance mods. Exclude the replaced renderers from BOTH inputs.
        instance_exclusions.extend(exclusions)
        staged = [p for p in staged if not any(fnmatch.fnmatchcase(p.name, pattern) for pattern in exclusions)]
        if not staged:
            raise LegError("e2e exclusions left no staged pack JARs")
        mods.extend(staged)
    if len({p.name for p in mods}) != len(mods):
        raise LegError("supplied JARs have colliding filenames")
    if any(fnmatch.fnmatchcase(p.name, "celtium-forge-*.jar") or fnmatch.fnmatchcase(p.name, FIXTURE)
           for p in mods[2:]):
        raise LegError("pack inputs contain another renderer or validation fixture")
    paths = {"game": gv / f"run-{leg}", "output": gv / f"{leg}.out",
             "runtimeLock": gv / f"{leg}-lock.json", "manifest": gv / f"{leg}-manifest.json",
             "result": gv / f"{leg}-result.json"}
    for path in paths.values():
        if os.path.lexists(path):
            raise LegError(f"artifact already exists; preserve it and choose a fresh LEG: {path}")
    busy = foundation_pids()
    if busy:
        raise LegError(f"another Foundation client is running (pids {busy}); no client was launched")
    environment = os.environ.copy()
    window_system = os.environ.get("CELTIUM_LEG_WINDOW_SYSTEM", "x11").strip().lower() or "x11"
    if window_system not in ("x11", "wayland"):
        raise LegError("CELTIUM_LEG_WINDOW_SYSTEM must be x11 or wayland")
    environment["SDL_VIDEODRIVER"] = window_system
    # The canonical script imports its sibling helper. Never prepend a stale .gv-verify module copy.
    environment.pop("PYTHONPATH", None)
    environment.pop("MESA_VK_DEVICE_SELECT", None)
    if gpu == "intel":
        environment["DRI_PRIME"] = "pci-0000_00_02_0!"
        render_node, required_gpu = "/dev/dri/by-path/pci-0000:00:02.0-render", "UHD Graphics 630"
    else:
        environment.pop("DRI_PRIME", None)
        render_node, required_gpu = "/dev/dri/by-path/pci-0000:03:00.0-render", "RX 6400"
    # The real-world mode drives the fixture's own world scene, which names its map, its spawn pose and its render
    # distance; every other mode keeps the flat acceptance scene.
    scene = "world" if mode == "world" else "terrain"
    jvm_args = (["-Xmx5g"] if mode in ("e2e", "recipe") else []) + [
        "-Dceltium.vulkan.presentReadback=true", "-Dfml.readTimeout=120",
        "-Dceltium.vulkan.validation=true", "-Dceltium.validation=true",
        f"-Dceltium.validation.scene={scene}",
        "-Dceltium.validation.keepGui=true", "-Dceltium.validation.minWorldFrames=5",
        "-Dceltium.validation.pinSections=64", "-Dceltium.validation.screenshotAtTick=200",
        "-Dceltium.validation.dumpPresentedImage=true", f"-Dceltium.vulkan.frameDumpDir={paths['game'] / 'framedump'}",
        *extras,
    ]
    command = [sys.executable, "-B", "-E", str(harness), str(instance), "--output", str(paths["runtimeLock"]),
               "--game-dir", str(paths["game"]), "--library", str(library), "--slot-wait", str(SLOT_WAIT)]
    # A different drawable size, for asking whether a frame is bound by the GPU or by the render thread: the calibration
    # is 1280x720, so this is for legs that measure rather than accept, and unset keeps the calibrated size.
    window_size = os.environ.get("CELTIUM_LEG_WINDOW_SIZE", "").strip()
    if window_size:
        if not re.fullmatch(r"[0-9]{3,4}x[0-9]{3,4}", window_size):
            raise LegError("CELTIUM_LEG_WINDOW_SIZE must look like 1280x720")
        command.extend(["--window-size", window_size])
    options_file = os.environ.get("CELTIUM_LEG_SEED_OPTIONS", "").strip()
    seeded_options = require_file(Path(options_file)) if options_file else None
    if seeded_options is not None:
        command.extend(["--seed-options", str(seeded_options)])
    for extra in extra_libraries:
        command.extend(["--library", str(extra)])
    for pattern in instance_exclusions:
        command.extend(["--exclude-instance-mod", pattern])
    for mod in mods:
        command.extend(["--mod", str(mod)])
    for name in supplemental:
        command.extend(["--copy-pack-dir", name])
    command.extend(f"--jvm-arg={arg}" for arg in jvm_args)
    if window_system == "x11":
        command.append("--force-x11")
    # --fetch-missing-libraries: the cohort and world profiles are clones whose minecraft/libraries is not complete
    # (the sources stage libraries through the launcher, not through the instance directory). Without it the run dies in
    # ForgeHooksClient.<clinit> with NoClassDefFoundError: org/lwjgl/BufferUtils before any renderer code runs, which was
    # measured on 0.7.11 with the plain and world modes on 2026-10-31 - a launch failure, not a gate failure.
    command.extend(["--run", "--validate", "--fetch-missing-libraries", "--duration", str(duration), "--headless",
                    "--render-node", render_node, "--require-gpu", required_gpu])
    artifacts = {name: {"source": str(path), "sha256": sha256(path)} for name, path in
                 (("renderer", renderer), ("fixture", fixture), ("harness", harness), ("headless", headless))}
    if seeded_options is not None:
        artifacts["seedOptions"] = {"source": str(seeded_options), "sha256": sha256(seeded_options)}
    dependencies = [{"path": f"mods/{p.name}", "source": str(p.resolve()), "sha256": sha256(p)} for p in mods]
    dependencies.append({"path": f"test-libraries/{library.name}", "source": str(library), "sha256": sha256(library)})
    manifest = {"schemaVersion": 1, "scope": "renderer input provenance; not an acceptance verdict",
                "rendererKind": "opengl-reference" if reference_renderer else "native-vulkan",
                "leg": leg, "mode": mode, "gpu": gpu, "windowSystemRequested": window_system,
                "durationSeconds": duration, "slotWaitSeconds": SLOT_WAIT,
                "sourceInstance": str(instance), "isolatedGameDirectory": str(paths["game"]),
                "artifacts": artifacts, "dependencies": dependencies, "git": git_state(root),
                "instanceModExclusions": instance_exclusions, "jvmArgs": jvm_args, "command": command,
                "deviceEnvironment": {key: environment.get(key) for key in
                                      ("DRI_PRIME", "MESA_VK_DEVICE_SELECT", "SDL_VIDEODRIVER")}}
    return Plan(root, leg, mode, gpu, duration, paths, renderer, fixture, required_gpu, command, environment, manifest,
                bool(reference_renderer))


def json_object(path: Path) -> dict:
    with path.open(encoding="utf-8") as stream:
        value = json.load(stream)
    if not isinstance(value, dict):
        raise LegError(f"JSON is not an object: {path}")
    return value


def write_json_new(path: Path, value: dict) -> None:
    with path.open("x", encoding="utf-8") as stream:
        stream.write(json.dumps(value, indent=2) + "\n")


def copied_artifacts(plan: Plan) -> list[dict]:
    mods = plan.paths["game"] / "mods"
    result = []
    renderer_pattern = plan.renderer.name if plan.reference_renderer else "celtium-forge-*.jar"
    for role, source, pattern in (("renderer", plan.renderer, renderer_pattern),
                                  ("fixture", plan.fixture, FIXTURE)):
        matches = list(mods.rglob(pattern)) if mods.is_dir() else []
        if len(matches) != 1 or matches[0].name != source.name:
            raise LegError(f"run-directory {role} count/name mismatch: {[p.name for p in matches]}")
        path = matches[0]
        if path.is_symlink() or not path.resolve().is_relative_to(plan.paths["game"]):
            raise LegError(f"run-directory {role} is not a contained copied file")
        measured = sha256(path)
        if measured != plan.manifest["artifacts"][role]["sha256"]:
            raise LegError(f"run-directory {role} SHA-256 mismatch")
        result.append({"role": role, "path": str(path.relative_to(plan.paths["game"])), "sha256": measured})
    return result


def png_dimensions(path: Path) -> tuple[int, int]:
    """Check PNG chunks/CRC and the complete deflate stream, not just its signature."""
    if path.stat().st_size > 64 * 1024 * 1024:
        raise LegError("picture PNG exceeds the 64 MiB evidence limit")
    data = path.read_bytes()
    if data[:8] != b"\x89PNG\r\n\x1a\n":
        raise LegError("picture is not a PNG")
    offset, header, compressed, ended = 8, None, bytearray(), False
    while offset + 12 <= len(data):
        length, kind = struct.unpack_from(">I4s", data, offset)
        end = offset + 12 + length
        if end > len(data):
            raise LegError("truncated PNG chunk")
        payload = data[offset + 8:end - 4]
        if binascii.crc32(kind + payload) & 0xffffffff != struct.unpack_from(">I", data, end - 4)[0]:
            raise LegError("PNG chunk CRC mismatch")
        if header is None and kind != b"IHDR":
            raise LegError("PNG has no initial IHDR")
        if kind == b"IHDR":
            if header is not None or length != 13:
                raise LegError("invalid PNG IHDR")
            header = struct.unpack(">IIBBBBB", payload)
        elif kind == b"IDAT":
            compressed.extend(payload)
        elif kind == b"IEND":
            ended = length == 0 and end == len(data)
            break
        offset = end
    if not header or not ended or not compressed:
        raise LegError("incomplete PNG")
    width, height, depth, colour, compression, filtering, interlace = header
    depths = {0: (1, 2, 4, 8, 16), 2: (8, 16), 3: (1, 2, 4, 8), 4: (8, 16), 6: (8, 16)}
    if (not 0 < width <= 16384 or not 0 < height <= 16384 or depth not in depths.get(colour, ())
            or compression != 0 or filtering != 0 or interlace not in (0, 1)):
        raise LegError("unsupported/invalid PNG header")
    channels = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}[colour]
    passes = ((0, 0, 1, 1),) if not interlace else (
        (0, 0, 8, 8), (4, 0, 8, 8), (0, 4, 4, 8), (2, 0, 4, 4),
        (0, 2, 2, 4), (1, 0, 2, 2), (0, 1, 1, 2))
    rows = []
    for x, y, step_x, step_y in passes:
        columns, count = max(0, (width - x + step_x - 1) // step_x), max(0, (height - y + step_y - 1) // step_y)
        if columns:
            rows.extend([(columns * channels * depth + 7) // 8 + 1] * count)
    expected = sum(rows)
    if expected > 128 * 1024 * 1024:
        raise LegError("PNG decompressed evidence exceeds 128 MiB")
    inflater = zlib.decompressobj()
    raw = inflater.decompress(compressed, expected + 1)
    if len(raw) != expected or not inflater.eof or inflater.unused_data or inflater.unconsumed_tail:
        raise LegError("incomplete or oversized PNG pixel stream")
    position = 0
    for row in rows:
        if raw[position] > 4:
            raise LegError("invalid PNG row filter")
        position += row
    return width, height


def postcheck(plan: Plan, harness_code: int) -> dict:
    result = {"accepted": False, "exitCode": 1, "verdict": "FAILED", "harnessExitCode": harness_code, "problems": []}
    problems = result["problems"]
    try:
        result["copiedArtifacts"] = copied_artifacts(plan)
        if plan.mode == "e2e":
            remaining = [p.name for p in (plan.paths["game"] / "mods").rglob("*.jar")
                         if p.name.lower().startswith(("nothirium-", "naughthirium-"))]
            if remaining:
                problems.append(f"E2E replaced renderer(s) remain in the run: {remaining}")
        runtime = json_object(plan.paths["runtimeLock"])
        process = json_object(plan.paths["game"] / "validation-process.json")
        fixture = json_object(plan.paths["game"] / "logs/celtium-validation.json")
        if runtime.get("isolatedGameDirectory") != str(plan.paths["game"]):
            problems.append("runtime-lock game directory does not identify this leg")
        if runtime.get("sourceInstance") != plan.manifest["sourceInstance"]:
            problems.append("runtime-lock source instance differs from the requested instance")
        if runtime.get("effectiveConfig", {}).get("runnerJvmArgs") != plan.manifest["jvmArgs"]:
            problems.append("runtime-lock JVM arguments differ from the requested arguments")
        for artifact in result["copiedArtifacts"]:
            named = [item for item in runtime.get("testMods", []) if item.get("file") == Path(artifact["path"]).name]
            if len(named) != 1 or named[0].get("sha256") != artifact["sha256"]:
                problems.append(f"runtime-lock {artifact['role']} hash/count mismatch")
        status = fixture.get("status")
        uncalibrated = status == UNCALIBRATED and process.get("fixtureStatus") == status
        result["fixtureStatus"] = status
        allowed_codes = (0, 2) if uncalibrated else (0,)
        if harness_code not in allowed_codes:
            problems.append(f"harness exited {harness_code}")
        if (type(process.get("exitCode")) is not int or process["exitCode"] != 0
                or process.get("endedBy") != "process-exit"
                or type(process.get("validationExitCode")) is not int
                or process["validationExitCode"] not in allowed_codes):
            problems.append("process did not finish with the required validation exit status")
        if (process.get("validationClean") is not True or type(process.get("validationMessageCount")) is not int
                or process["validationMessageCount"] != 0):
            problems.append("validation is dirty or its clean result is missing")
        if process.get("fixtureStatus") != status:
            problems.append("process and fixture statuses disagree")
        if status not in ("PASSED", UNCALIBRATED):
            problems.append("fixture did not produce a passing/calibrated verdict")
        if fixture.get("presentedImageVerdict") != status:
            problems.append("picture verdict does not match the fixture status")
        if not uncalibrated:
            for field in ("sceneGateCalibrated", "sceneGateFloorsEnforced", "sceneCalibrationKeyMatches"):
                if fixture.get(field) is not True:
                    problems.append(f"fixture gate is not established: {field}")
        console = (plan.paths["game"] / "validation-console.log").read_text(errors="replace")
        devices = NATIVE_DEVICE.findall(console)
        name, device_uuid = devices[-1] if devices else (None, None)
        if plan.reference_renderer:
            gl_name = fixture.get("glRenderer")
            if (devices or not isinstance(gl_name, str) or plan.required_gpu.lower() not in gl_name.lower()
                    or process.get("glRendererPreflight") != gl_name
                    or process.get("gpuRequirementMet") is not True
                    or process.get("gpuRequirementSource") != "glRendererPreflight"):
                problems.append("reference GL evidence does not establish the requested GPU")
            result["referenceGlRenderer"] = gl_name
        elif (not devices or any(plan.required_gpu.lower() not in item[0].lower() for item in devices)
              or process.get("nativeDeviceName") != name or process.get("nativeDeviceUuid") != device_uuid
              or process.get("gpuRequirementMet") is not True
              or process.get("gpuRequirementSource") != "nativeDeviceName"):
            problems.append("native device evidence does not establish the requested GPU")
        result["nativeDeviceName"] = name
        result["rendererKind"] = "opengl-reference" if plan.reference_renderer else "native-vulkan"
        if not (plan.paths["game"] / "logs/latest.log").is_file():
            problems.append("the game validation log is missing")
        if type(fixture.get("sceneGuiStage")) is not bool:
            problems.append("fixture does not identify whether the judged scene has a GUI stage")
        stage = "gui" if fixture.get("sceneGuiStage") else "world"
        picture_file = fixture.get(f"pictureFile_{stage}")
        expected_source = "gl-screenshot" if plan.reference_renderer else "presented-dump"
        if (fixture.get("pictureEvidenceSource") != expected_source
                or process.get("pictureEvidenceSource") != expected_source
                or process.get("pictureStage") != stage or process.get("pictureFile") != picture_file
                or process.get("picturePresent") is not True or not isinstance(picture_file, str)):
            raise LegError(f"missing/inconsistent {expected_source} picture evidence")
        relative = Path(picture_file)
        picture = plan.paths["game"] / relative
        if (relative.is_absolute() or ".." in relative.parts or picture.is_symlink()
                or not picture.resolve().is_relative_to(plan.paths["game"])):
            raise LegError("picture path escapes this run")
        result["picture"] = {"path": picture_file, "size": png_dimensions(picture)}
        for role in ("harness", "headless"):
            artifact = plan.manifest["artifacts"][role]
            if sha256(Path(artifact["source"])) != artifact["sha256"]:
                problems.append(f"{role} changed during the leg")
        if not problems:
            result.update(accepted=not uncalibrated, exitCode=2 if uncalibrated else 0,
                          verdict="INCONCLUSIVE" if uncalibrated else "PASSED")
    except (LegError, OSError, ValueError, TypeError, KeyError, AttributeError, zlib.error) as error:
        problems.append(str(error))
    return result


@contextmanager
def child_subreaper():
    """Linux launcher contract: reap orphaned children in our group, never unrelated processes."""
    libc = ctypes.CDLL(None, use_errno=True)
    previous = ctypes.c_int()
    if libc.prctl(37, ctypes.byref(previous), 0, 0, 0) != 0 or libc.prctl(36, 1, 0, 0, 0) != 0:
        raise LegError("cannot enable owned-child reaping on this Linux host")
    try:
        yield
    finally:
        libc.prctl(36, previous.value, 0, 0, 0)


def group_exists(pgid: int) -> bool:
    try:
        os.killpg(pgid, 0)
        return True
    except ProcessLookupError:
        return False


def live_member_states(states: list[str]) -> bool:
    """Whether any of these ``ps`` STAT values is a process that still runs - a zombie (``Z``) has exited."""
    return any(state and not state.startswith("Z") for state in states)


def group_running(pgid: int) -> bool:
    """Whether the group still has a member that has not exited.

    ``killpg(pgid, 0)`` also succeeds for a group whose only members are zombies: exited processes their parent has not
    reaped yet. A defunct Java of the pack's helper was reported as a leftover that way (leg e2ee-n2, after the fixture
    passed), so the group is judged by its members' states.
    """
    if not group_exists(pgid):
        return False
    try:
        listing = subprocess.run(["ps", "-o", "stat=", "-g", str(pgid)], capture_output=True, text=True,
                                 timeout=5).stdout
    except (OSError, subprocess.SubprocessError):
        return True
    return live_member_states([line.strip() for line in listing.splitlines()])


# A pack's own helper process may outlive the game by a few seconds: E2E-E's Crash Assistant starts a separate JVM that
# watches the game and exits after it (measured: still present when the harness returned, gone moments later, under
# both renderers). The group is given this long to empty before a leftover is reported.
CHILD_EXIT_GRACE = float(os.environ.get("CELTIUM_LEG_CHILD_EXIT_GRACE", "60"))


def wait_group_empty(pgid: int, grace: float, exists=None, sleep=time.sleep, clock=time.monotonic) -> float:
    """Wait up to ``grace`` seconds for the group to empty; return the seconds waited (``grace`` or more if it did not)."""
    exists = exists or group_running
    started = clock()
    while exists(pgid) and clock() - started < grace:
        sleep(0.5)
    return clock() - started


def group_members(pgid: int) -> list[str]:
    """The processes still in a group, as 'pid command' lines, so a leftover is named rather than only counted."""
    try:
        listing = subprocess.run(["ps", "-o", "pid=,args=", "-g", str(pgid)], capture_output=True, text=True,
                                 timeout=5).stdout
    except (OSError, subprocess.SubprocessError):
        return []
    return [" ".join(line.split())[:160] for line in listing.splitlines() if line.strip()]


def stop_owned_group(process: subprocess.Popen, grace: float = TERMINATE_GRACE) -> None:
    # SIGINT lets Python execute the harness's finally blocks (game slot, Java, compositor).
    # An uncooperative Java/test child is then terminated/killed in this *own* new session.
    for signum, wait in ((signal.SIGINT, grace), (signal.SIGTERM, 1), (signal.SIGKILL, 1)):
        try:
            os.killpg(process.pid, signum)
        except ProcessLookupError:
            break
        deadline = time.monotonic() + wait
        while time.monotonic() < deadline:
            leader_exited = process.poll() is not None
            if leader_exited:
                while True:
                    try:
                        child, _ = os.waitpid(-process.pid, os.WNOHANG)
                    except ChildProcessError:
                        break
                    if child == 0:
                        break
            if not group_exists(process.pid):
                process.wait()
                return
            if leader_exited and signum == signal.SIGINT:
                break  # No finally block is still running; do not burn the graceful budget on an orphan.
            time.sleep(0.02)
    process.wait(timeout=2)
    if group_exists(process.pid):
        raise LegError("owned process group did not exit after SIGKILL")


def execute(plan: Plan, *, timeout: float | None = None, terminate_grace: float = TERMINATE_GRACE) -> dict:
    """Reserve outputs exclusively and supervise only the process group started here."""
    started = time.monotonic()
    deadline = started + (timeout if timeout is not None else plan.duration + SLOT_WAIT + STARTUP_BUDGET)
    result = None
    requested_signal = None
    old_handlers = {}
    process = None

    def cancel(signum, _frame):
        nonlocal requested_signal
        requested_signal = requested_signal or signum

    with plan.paths["output"].open("xb") as output:
        # This exclusive reservation serializes users of one LEG, not GPU access. No old file is removed.
        for key, path in plan.paths.items():
            if key != "output" and os.path.lexists(path):
                raise LegError(f"artifact appeared while reserving the leg: {path}")
        write_json_new(plan.paths["manifest"], plan.manifest)
        try:
            for signum in (signal.SIGINT, signal.SIGTERM):
                old_handlers[signum] = signal.signal(signum, cancel)
            with child_subreaper():
                try:
                    process = subprocess.Popen(plan.command, cwd=plan.root, env=plan.environment,
                                               stdout=output, stderr=subprocess.STDOUT, start_new_session=True)
                    checked_copy = False
                    while process.poll() is None:
                        if requested_signal:
                            result = {"accepted": False, "verdict": "CANCELLED", "exitCode": 128 + requested_signal,
                                      "problems": [f"received signal {requested_signal}"]}
                            break
                        if time.monotonic() >= deadline:
                            result = {"accepted": False, "verdict": "TIME_LIMIT", "exitCode": 124,
                                      "problems": ["wrapper time limit expired (including startup and slot wait)"]}
                            break
                        if not checked_copy:
                            try:
                                runtime = json_object(plan.paths["runtimeLock"])
                            except (OSError, ValueError, LegError):
                                runtime = {}  # Canonical harness publishes JSON in stages; a partial write is not final.
                            if runtime.get("isolatedGameDirectory") == str(plan.paths["game"]):
                                copied_artifacts(plan)  # Fatal early rather than wasting the whole leg on the wrong jars.
                                checked_copy = True
                        try:
                            process.wait(timeout=min(0.2, max(0.001, deadline - time.monotonic())))
                        except subprocess.TimeoutExpired:
                            pass
                    if result is None:
                        result = postcheck(plan, process.returncode)
                        if group_running(process.pid):
                            result["childExitWaitSeconds"] = round(wait_group_empty(process.pid, CHILD_EXIT_GRACE), 1)
                        if group_running(process.pid):
                            result.update(accepted=False, verdict="FAILED", exitCode=1)
                            result["problems"].append("harness left owned child processes running")
                            result["leftoverProcesses"] = group_members(process.pid)
                finally:
                    if process is not None:
                        stop_owned_group(process, terminate_grace)
        except (LegError, OSError, ValueError, subprocess.SubprocessError) as error:
            result = {"accepted": False, "verdict": "FAILED", "exitCode": 1, "problems": [str(error)]}
        finally:
            for signum, handler in old_handlers.items():
                signal.signal(signum, handler)
        if requested_signal:
            result.update(accepted=False, verdict="CANCELLED", exitCode=128 + requested_signal)
        result["harnessExitCode"] = process.returncode if process is not None else None
        result["elapsedSeconds"] = time.monotonic() - started
        result["leg"] = plan.leg
        write_json_new(plan.paths["result"], result)
    return result


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("leg")
    parser.add_argument("jar_dir", type=Path)
    parser.add_argument("mode", choices=("cohort", "plain", "e2e", "recipe", "world"))
    parser.add_argument("gpu", choices=("amd", "intel"))
    parser.add_argument("duration", nargs="?", type=int, default=900)
    parser.add_argument("jvm_args", nargs=argparse.REMAINDER)
    args = parser.parse_args(argv)
    try:
        plan = make_plan(args.leg, args.jar_dir, args.mode, args.gpu, args.duration, args.jvm_args)
        result = execute(plan)
    except (LegError, OSError, ValueError) as error:
        print(f"FATAL: {error}", file=sys.stderr)
        return 3
    print(json.dumps(result))
    return result["exitCode"]


if __name__ == "__main__":
    sys.exit(main())
