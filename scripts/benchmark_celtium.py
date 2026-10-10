#!/usr/bin/env python3
"""Benchmark Celtium against the pinned Celeritas reference under identical conditions.

What this produces, per configuration:

* raw per-frame times for a fixed number of frames, after a warm-up that must finish first, so
  percentiles (p50/p95/p99/max) are computed from samples rather than asserted;
* process CPU time and peak RSS measured inside the game process (``/proc/self``);
* the load average at both ends of the measured window, because this machine is shared;
* the frame cap, vsync flag and render distance as they were actually seen at both ends;
* the renderer's own chunk-queue counters (steady-state evidence) and, when the backend publishes
  them, per-terrain-pass GPU times from its Vulkan timestamp queries;
* the selected GPU from the renderer's own ``CELTIUM_NATIVE_DEVICE`` line, plus
  ``logs/celtium-capabilities.json`` when the renderer's capability command ran.

What it deliberately does not do: change the renderer, touch the validation fixture, copy anything
into the Prism instance, or claim a speed-up. It runs one game instance at a time (the same
``pgrep -x java`` + ``/proc/<pid>/cmdline`` check the other agents use) and it hashes every jar
before and after every run, so a mid-session rebuild cannot be averaged away.

Usage:

    python3 scripts/benchmark_celtium.py --rounds 2 --frames 600
    python3 scripts/benchmark_celtium.py --configs celtium --rounds 1 --frames 120 --dry-run
"""

import argparse
import hashlib
import json
import math
import os
import platform
import re
import shutil
import statistics
import subprocess
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SCRIPTS = ROOT / "scripts"
FIXTURE_SRC = SCRIPTS / "bench-fixture" / "src"
BUILD = SCRIPTS / "bench-fixture" / "build"
WORK = ROOT / ".celtium-work" / "bench"

DEFAULT_INSTANCE = Path("/home/s0u7a/.local/share/PrismLauncher/instances/cleanroom-0.6.13-alpha")
DEFAULT_JAVA = Path("/usr/lib/jvm/java-25-openjdk-amd64/bin/java")
DEFAULT_JAVAC = Path("/usr/lib/jvm/java-25-openjdk-amd64/bin/javac")
DEFAULT_RENDER_NODE = Path("/dev/dri/renderD129")  # AMD RX 6400; renderD128 is the Intel UHD 630
# The Vulkan bindings the renderer's native backend needs. Every existing run adds this jar to the test
# classpath; without it the backend cannot be created and Celtium refuses to render terrain at all.
DEFAULT_VULKAN_LIBRARY = Path(
    "/home/s0u7a/.gradle/caches/modules-2/files-2.1/org.lwjgl/lwjgl-vulkan/3.4.1/"
    "d0601fdc38890755e5638048c14cc0f7b990964b/lwjgl-vulkan-3.4.1.jar")

# The MCP-named Minecraft + Forge jar the 1.12.2 workspace compiles against. The runtime jars in the
# Prism instance carry obfuscated names and are remapped at runtime, so they cannot be compiled
# against; this artifact is where the mapped classes actually exist.
MCP_JAR = ROOT / ".celtium-work/upstream-baseline/forge122/build/rfg/recompiled_minecraft-1.12.2.jar"
SRG_JAR = ROOT / ".celtium-work/upstream-baseline/forge122/build/rfg/srg_merged_minecraft.jar"
LIBS = Path("/home/s0u7a/.local/share/PrismLauncher/libraries")
LWJGL_JARS = [
    LIBS / "org/lwjgl/lwjgl-opengl/3.4.1/lwjgl-opengl-3.4.1.jar",
    LIBS / "org/lwjgl/lwjgl/3.4.1-unsafe/lwjgl-3.4.1-unsafe.jar",
]

CONFIGS = {
    "celtium": {
        "jar": ROOT / "forge122/build/libs/celtium-forge-mc1.12.2-2.5.12-celtium.0.1.0-dev.1.jar",
        "what": "Celtium fork (built jar), native Vulkan terrain backend through the GL bridge",
    },
    "reference-celeritas-989ebf2e": {
        "jar": ROOT / ".celtium-work/upstream-baseline/forge122/build/libs/"
                   "celeritas-forge-mc12.2-2.5.12-upstream.989ebf2e.jar",
        "what": "pinned upstream Celeritas 989ebf2e, OpenGL terrain (the reference)",
    },
}

GAME_MARKER = b"foundation.boot.Foundation"


# --------------------------------------------------------------------------- helpers

def digest(path):
    result = hashlib.sha256()
    with Path(path).open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            result.update(block)
    return result.hexdigest()


def running_game_pids():
    """The mandated check: pgrep -x java, then look for Foundation in that process's cmdline.

    `pgrep -f` is not used anywhere here: it matches the shells that are waiting on a run.
    """
    found = []
    listing = subprocess.run(["pgrep", "-x", "java"], capture_output=True, text=True)
    for pid in listing.stdout.split():
        try:
            cmdline = Path("/proc/%s/cmdline" % pid).read_bytes()
        except OSError:
            continue
        if GAME_MARKER in cmdline:
            found.append(pid)
    return found


def wait_for_idle(timeout_seconds, poll=5.0):
    started = time.monotonic()
    announced = False
    while True:
        busy = running_game_pids()
        if not busy:
            return True
        if not announced:
            print("    another game instance is running (pid %s); waiting for it to exit"
                  % ",".join(busy), flush=True)
            announced = True
        if time.monotonic() - started > timeout_seconds:
            print("    gave up waiting for the other instance after %.0fs" % timeout_seconds, flush=True)
            return False
        time.sleep(poll)


def load_average():
    try:
        parts = Path("/proc/loadavg").read_text().split()
        return [float(parts[0]), float(parts[1]), float(parts[2])]
    except (OSError, ValueError, IndexError):
        return [float("nan")] * 3


def git(*args):
    try:
        out = subprocess.run(["git", *args], capture_output=True, text=True, cwd=ROOT)
        return out.stdout.strip() if out.returncode == 0 else None
    except OSError:
        return None


def sample_process(pid):
    """One /proc sample of the game process. Missing files are not an error: the process can exit."""
    try:
        stat = Path("/proc/%s/stat" % pid).read_text()
        close = stat.rfind(")")
        fields = stat[close + 2:].split()
        jiffies = int(fields[11]) + int(fields[12])
    except (OSError, ValueError, IndexError):
        return None
    rss = peak = -1
    try:
        for line in Path("/proc/%s/status" % pid).read_text().splitlines():
            if line.startswith("VmRSS:"):
                rss = int(line.split()[1])
            elif line.startswith("VmHWM:"):
                peak = int(line.split()[1])
    except (OSError, ValueError, IndexError):
        pass
    return {"jiffies": jiffies, "rssKb": rss, "peakKb": peak, "loadAverage": load_average(),
            "atSeconds": time.monotonic()}


def find_game_pid(run_dir):
    for pid in running_game_pids():
        try:
            cmdline = Path("/proc/%s/cmdline" % pid).read_bytes()
        except OSError:
            continue
        if str(run_dir).encode() in cmdline:
            return pid
    return None


# --------------------------------------------------------------------------- fixture build

def newest_launch_classpath():
    """The classpath a previous successful run actually used, as the fallback source."""
    candidates = sorted(WORK.parent.glob("runtime/*/validation-command.json"),
                        key=lambda p: p.stat().st_mtime, reverse=True)
    for path in candidates:
        try:
            command = json.loads(path.read_text())
            index = command.index("-cp")
        except (OSError, ValueError):
            continue
        return command[index + 1], path
    return None, None


def compile_fixture(java, javac):
    sources = sorted(FIXTURE_SRC.rglob("*.java"))
    if not sources:
        raise SystemExit("no benchmark fixture sources under %s" % FIXTURE_SRC)
    for jar in [MCP_JAR] + LWJGL_JARS:
        if not jar.is_file():
            raise SystemExit(
                "the compile classpath is missing %s\n"
                "This jar comes from the 1.12.2 dev workspace; rebuild it with:\n"
                "  cd .celtium-work/upstream-baseline && ./gradlew :forge122:classes" % jar)
    classes = BUILD / "classes"
    jar_path = BUILD / "celtium-bench-fixture.jar"
    newest_source = max(path.stat().st_mtime for path in sources)
    if jar_path.is_file() and jar_path.stat().st_mtime >= newest_source:
        print("fixture jar is up to date: %s" % jar_path)
        return jar_path, {"reused": True}
    if classes.exists():
        shutil.rmtree(classes)
    classes.mkdir(parents=True)
    classpath = os.pathsep.join(str(jar) for jar in [MCP_JAR] + LWJGL_JARS)
    command = [str(javac), "-nowarn", "-cp", classpath, "-d", str(classes)] + [str(p) for p in sources]
    print("compiling the benchmark fixture against the mapped Minecraft/Forge jar")
    result = subprocess.run(command, capture_output=True, text=True)
    if result.returncode != 0:
        raise SystemExit("fixture compilation failed:\n%s\n%s" % (result.stdout, result.stderr))

    # The runtime presents Minecraft with MCP class names but SRG member names, which is why every
    # mod jar is reobfuscated before it can load. The mapping is derived from the workspace's own
    # MCP/SRG jar pair and self-tested against three pairs known from the deployed validation jar.
    sys.path.insert(0, str(SCRIPTS / "bench-fixture"))
    import mcp_to_srg
    mapping = mcp_to_srg.build_mapping(MCP_JAR, SRG_JAR, cache_path=BUILD / "mcp-to-srg.json")
    remap_report = {}
    unresolved = []
    unverified = []
    for class_file in sorted(classes.rglob("*.class")):
        original = class_file.read_bytes()
        remapped, renamed, missed, already, doubtful = mcp_to_srg.remap_class(original, mapping)
        if renamed or missed or doubtful:
            remap_report[class_file.name] = {"renamed": renamed, "unresolved": missed,
                                             "unverified": doubtful, "leftWithTheirOwnName": already}
        unresolved.extend(missed)
        unverified.extend(doubtful)
        if remapped is not original:
            class_file.write_bytes(remapped)
    if unresolved or unverified:
        raise SystemExit("the fixture references Minecraft members the mapping could not resolve or could "
                         "not verify, so the jar would fail at runtime with NoSuchMethodError:\n  "
                         + "\n  ".join(sorted(set(unresolved + unverified))))
    (BUILD / "remap-report.json").write_text(json.dumps(remap_report, indent=2) + "\n", encoding="utf-8")

    build_jar = shutil.which("jar") or str(Path(java).with_name("jar"))
    manifest = BUILD / "MANIFEST.MF"
    manifest.write_text(
        "Manifest-Version: 1.0\n"
        "Implementation-Title: Celtium benchmark fixture\n"
        "Implementation-Version: 0.1.0\n"
        "Bench-MappedMinecraftSha256: %s\n"
        "Bench-SrgMinecraftSha256: %s\n"
        % (digest(MCP_JAR), digest(SRG_JAR)),
        encoding="utf-8")
    subprocess.run([build_jar, "--create", "--file", str(jar_path), "--manifest", str(manifest),
                    "-C", str(classes), "."], check=True, capture_output=True)
    print("fixture jar: %s (%d bytes, sha256 %s, %d members remapped MCP->SRG)"
          % (jar_path, jar_path.stat().st_size, digest(jar_path)[:16],
             sum(len(entry["renamed"]) for entry in remap_report.values())))
    return jar_path, {"reused": False, "mappedMinecraft": str(MCP_JAR),
                      "mappedMinecraftSha256": digest(MCP_JAR),
                      "srgMinecraft": str(SRG_JAR), "srgMinecraftSha256": digest(SRG_JAR),
                      "remappedMembers": sum(len(entry["renamed"]) for entry in remap_report.values()),
                      "mappingClasses": mapping["classCount"],
                      "sources": [str(p) for p in sources]}


# --------------------------------------------------------------------------- statistics

def percentile_nearest_rank(values, fraction):
    """The validation fixture's own percentile rule, so the two sets of numbers agree in method."""
    ordered = sorted(values)
    if not ordered:
        return float("nan")
    index = max(0, min(len(ordered) - 1, int(math.ceil(fraction * len(ordered))) - 1))
    return ordered[index]


def frame_statistics(samples):
    if not samples:
        return {}
    ordered = sorted(samples)
    mean = statistics.fmean(samples)
    p50 = percentile_nearest_rank(samples, 0.50)
    p95 = percentile_nearest_rank(samples, 0.95)
    p99 = percentile_nearest_rank(samples, 0.99)
    return {
        "count": len(samples),
        "minMs": ordered[0],
        "meanMs": mean,
        "p50Ms": p50,
        "p95Ms": p95,
        "p99Ms": p99,
        "maxMs": ordered[-1],
        "stdevMs": statistics.pstdev(samples) if len(samples) > 1 else 0.0,
        "fpsFromMean": 1000.0 / mean if mean > 0 else float("nan"),
        "fpsFromP50": 1000.0 / p50 if p50 > 0 else float("nan"),
        "fpsOnePercentLow": 1000.0 / p99 if p99 > 0 else float("nan"),
    }


PASS_LINE = re.compile(r"^(?P<pass>[A-Za-z_]+) - (?P<sections>\d+) sections, (?P<time>.+)$")
DURATION = re.compile(r"(?P<value>[0-9.]+)\s*(?P<unit>ns|us|µs|ms|s)\b")
VULKAN_CHECK = re.compile(r"celtium/vulkan-check passes=(\d+) skipped=(\d+) errors=(\d+) "
                          r"lastDraw=(\d+) lastSections=(\d+) skipReason=(\S+)")
TERRAIN_COUNTERS = re.compile(r"terrainPasses=(\d+)")
IMMEDIATE_COUNTERS = re.compile(r"immediateTaken=(\d+)")


def parse_gpu_pass_times(debug_strings):
    """Per-terrain-pass GPU time as the backend publishes it (Vulkan timestamp queries)."""
    times = {}
    for line in debug_strings or []:
        match = PASS_LINE.match(line.strip())
        if not match:
            continue
        text = match.group("time").strip()
        if text.startswith("?"):
            times[match.group("pass")] = {"sections": int(match.group("sections")), "ms": None,
                                          "raw": text}
            continue
        duration = DURATION.search(text)
        if not duration:
            times[match.group("pass")] = {"sections": int(match.group("sections")), "ms": None,
                                          "raw": text}
            continue
        value = float(duration.group("value"))
        unit = duration.group("unit")
        factor = {"ns": 1e-6, "us": 1e-3, "µs": 1e-3, "ms": 1.0, "s": 1000.0}[unit]
        times[match.group("pass")] = {"sections": int(match.group("sections")),
                                      "ms": value * factor, "raw": text}
    return times


def parse_native_device(console_text):
    match = re.search(r'CELTIUM_NATIVE_DEVICE name="([^"]*)" uuid=([0-9a-fA-F]*) matchedOpenGl=(\w+)',
                      console_text)
    if not match:
        return {"found": False}
    return {"found": True, "name": match.group(1), "uuid": match.group(2),
            "matchedOpenGl": match.group(3) == "true"}


def clk_tck():
    try:
        return os.sysconf("SC_CLK_TCK")
    except (ValueError, OSError):
        return 100


# --------------------------------------------------------------------------- one run

def run_configuration(config_key, run_ordinal, args, bench_jar, out_root, hashes):
    cfg = CONFIGS[config_key]
    run_dir = out_root / "runs" / ("%s-%02d-%s" % (args.stamp, run_ordinal, config_key))
    lock = Path(str(run_dir) + ".lock.json")
    launcher_log = Path(str(run_dir) + ".launcher.log")
    if run_dir.exists():
        raise SystemExit("run directory already exists: %s" % run_dir)
    jar_before = digest(cfg["jar"])
    record = {
        "config": config_key,
        "what": cfg["what"],
        "ordinal": run_ordinal,
        "jar": str(cfg["jar"].relative_to(ROOT)),
        "jarSha256Before": jar_before,
        "jarMtimeBefore": cfg["jar"].stat().st_mtime,
        "jarChangedSinceSessionStart": bool(hashes.get(config_key) and hashes[config_key] != jar_before),
        "gameDirectory": str(run_dir.relative_to(ROOT)),
        "startedAt": time.strftime("%Y-%m-%dT%H:%M:%S"),
        "loadAverageBeforeLaunch": load_average(),
    }
    if hashes.get(config_key) != jar_before:
        record["jarSha256FirstSeen"] = hashes.get(config_key)
    hashes.setdefault(config_key, jar_before)

    jvm_args = [
        "-Dceltium.bench=true",
        "-Dceltium.bench.label=%s" % config_key,
        "-Dceltium.bench.frames=%d" % args.frames,
        "-Dceltium.bench.warmupTicks=%d" % args.warmup_ticks,
        "-Dceltium.bench.warmupFrames=%d" % args.warmup_frames,
        "-Dceltium.bench.framelimit=%d" % args.framelimit,
        "-Dceltium.bench.vsync=%s" % ("true" if args.vsync else "false"),
        "-Dceltium.bench.renderDistance=%d" % args.render_distance,
        "-Dceltium.bench.chunkPhase=%s" % ("true" if args.chunk_phase else "false"),
        "-Dceltium.bench.chunkBlocks=%d" % args.chunk_blocks,
        "-Dceltium.bench.timeoutSeconds=%d" % args.fixture_timeout,
    ] + list(args.extra_jvm_arg or [])
    record["benchProperties"] = jvm_args
    record["vulkanLibrary"] = {"path": str(args.vulkan_library),
                               "sha256": digest(args.vulkan_library)}

    command = [
        sys.executable, str(SCRIPTS / "prism_validation.py"), str(args.instance),
        "--output", str(lock), "--java", str(args.java), "--game-dir", str(run_dir),
        "--mod", str(cfg["jar"]), "--mod", str(bench_jar),
        "--library", str(args.vulkan_library),
        "--headless", "--render-node", str(args.render_node),
        "--run", "--duration", str(args.duration),
    ]
    for jvm in jvm_args:
        # --jvm-arg=<value> rather than "--jvm-arg <value>": argparse reads a value that starts with a dash as
        # another option and fails with "expected one argument".
        command.append("--jvm-arg=%s" % jvm)
    record["launcherCommand"] = command

    if args.dry_run:
        print("  [dry-run] would launch: %s" % " ".join(command))
        record["dryRun"] = True
        return record

    print("  launching %s (jar sha256 %s)" % (config_key, jar_before[:16]), flush=True)
    samples = []
    with launcher_log.open("w") as log:
        process = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, cwd=ROOT)
        game_pid = None
        while process.poll() is None:
            if game_pid is None or not Path("/proc/%s" % game_pid).exists():
                game_pid = find_game_pid(run_dir)
            sample = sample_process(game_pid) if game_pid else None
            if sample:
                samples.append(sample)
            time.sleep(0.2)
        try:
            process.wait(timeout=60)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait()
    record["launcherExitCode"] = process.returncode
    record["gamePidSampled"] = bool(samples)
    record["loadAverageAfterExit"] = load_average()
    if samples:
        record["externalSampleCount"] = len(samples)
        record["externalCpuJiffiesLast"] = samples[-1]["jiffies"]
        record["externalPeakRssKb"] = max(sample["peakKb"] for sample in samples)
        loads = [sample["loadAverage"][0] for sample in samples]
        record["loadAverageDuringRunOneMinMax"] = [min(loads), max(loads)]

    # ---- collect what the run left behind
    fixture_path = run_dir / "logs" / "celtium-benchmark.json"
    if fixture_path.is_file():
        try:
            record["fixture"] = json.loads(fixture_path.read_text())
        except ValueError as error:
            record["fixtureError"] = "unreadable: %s" % error
    else:
        record["fixtureError"] = "logs/celtium-benchmark.json was not written"

    process_json = run_dir / "validation-process.json"
    if process_json.is_file():
        try:
            launched = json.loads(process_json.read_text())
            record["launcherOutcome"] = {key: launched.get(key) for key in
                                         ("exitCode", "endedBy", "elapsedSeconds", "headlessSession")}
            record["nativeDeviceFromLauncher"] = {
                "name": launched.get("nativeDeviceName"),
                "uuid": launched.get("nativeDeviceUuid"),
                "matchedOpenGl": launched.get("nativeDeviceMatchedOpenGl"),
            }
        except ValueError:
            pass

    console = run_dir / "validation-console.log"
    console_text = console.read_text(errors="replace") if console.is_file() else ""
    record["nativeDevice"] = parse_native_device(console_text)
    record["capabilitiesFile"] = None
    capabilities = run_dir / "logs" / "celtium-capabilities.json"
    if capabilities.is_file():
        try:
            caps = json.loads(capabilities.read_text())
            devices = []
            for device in caps.get("vulkanDevices") or []:
                if isinstance(device, dict):
                    devices.append({key: device.get(key) for key in
                                    ("name", "apiVersion", "deviceType", "deviceId", "vendorId",
                                     "deviceUUID", "driverVersion")})
            record["capabilitiesFile"] = {
                "path": str(capabilities.relative_to(ROOT)),
                "status": caps.get("status"),
                "effectiveTerrainBackend": caps.get("effectiveTerrainBackend"),
                "nativeTerrainImplemented": caps.get("nativeTerrainImplemented"),
                "depthRoundtripVerified": caps.get("depthRoundtripVerified"),
                "matchedVulkanDeviceUuid": caps.get("matchedVulkanDeviceUuid"),
                "lwjglVersion": caps.get("lwjglVersion"),
                "vulkanLoaderVersion": caps.get("vulkanLoaderVersion"),
                "vulkanDevices": devices,
                "openGlRenderer": (caps.get("openGl") or {}).get("renderer"),
                "reasons": caps.get("reasons"),
            }
        except ValueError:
            record["capabilitiesFile"] = {"path": str(capabilities.relative_to(ROOT)),
                                          "error": "unreadable"}
    record["consoleLines"] = {
        "CeltiumBench": [line for line in console_text.splitlines() if "[CeltiumBench]" in line][:40],
        "CELTIUM_": [line for line in console_text.splitlines() if "CELTIUM_" in line][:20],
    }
    vulkan = VULKAN_CHECK.search(console_text)
    if vulkan:
        record["vulkanCheck"] = {
            "passes": int(vulkan.group(1)), "skipped": int(vulkan.group(2)),
            "errors": int(vulkan.group(3)), "lastDraw": int(vulkan.group(4)),
            "lastSections": int(vulkan.group(5)), "skipReason": vulkan.group(6),
        }

    # ---- derived numbers
    fixture = record.get("fixture") or {}
    frames = fixture.get("frameTimesMs") or []
    record["frameStatistics"] = frame_statistics(frames)
    record["chunkPhaseStatistics"] = frame_statistics(fixture.get("chunkDrainFrameTimesMs") or [])
    tick = clk_tck()
    start, end = fixture.get("cpuJiffiesStart"), fixture.get("cpuJiffiesEnd")
    if isinstance(start, int) and isinstance(end, int) and start >= 0 and end >= start:
        cpu_seconds = (end - start) / tick
        window = fixture.get("windowSeconds") or 0.0
        record["cpu"] = {
            "source": "/proc/self/stat utime+stime, CLK_TCK=%d" % tick,
            "jiffies": end - start,
            "seconds": cpu_seconds,
            "windowSeconds": window,
            "cpuPercentOfOneCore": (100.0 * cpu_seconds / window) if window > 0 else None,
            "cores": os.cpu_count(),
        }
    if isinstance(fixture.get("rssPeakKbEnd"), int) and fixture["rssPeakKbEnd"] > 0:
        record["memory"] = {
            "peakRssMb": fixture["rssPeakKbEnd"] / 1024.0,
            "rssAtWindowStartMb": (fixture.get("rssKbStart") or 0) / 1024.0,
            "rssAtWindowEndMb": (fixture.get("rssKbEnd") or 0) / 1024.0,
            "source": "/proc/self/status VmHWM and VmRSS",
        }
    debug_end = fixture.get("debugStringsAtWindowEnd") or []
    record["gpuPassTimesMs"] = parse_gpu_pass_times(debug_end)
    joined_debug = "\n".join(debug_end)
    record["terrainCountersAtWindowEnd"] = {
        "terrainPasses": (TERRAIN_COUNTERS.search(joined_debug).group(1)
                          if TERRAIN_COUNTERS.search(joined_debug) else None),
        "immediateTaken": (IMMEDIATE_COUNTERS.search(joined_debug).group(1)
                           if IMMEDIATE_COUNTERS.search(joined_debug) else None),
    }
    record["rendererDebugStringsAtWindowEnd"] = debug_end
    record["achievedFpsVersusCaps"] = describe_caps(record, args)
    record["jarSha256After"] = digest(cfg["jar"])
    record["jarChangedDuringRun"] = record["jarSha256After"] != jar_before
    record["finishedAt"] = time.strftime("%Y-%m-%dT%H:%M:%S")
    return record


def describe_caps(record, args):
    stats = record.get("frameStatistics") or {}
    p50 = stats.get("p50Ms")
    cap_ms = 1000.0 / args.framelimit if args.framelimit else None
    vsync_ms = 1000.0 / 60.0  # the headless compositor's output is 1280x720@60Hz
    note = []
    if p50:
        note.append("p50 %.3f ms is %s the %.3f ms frame-cap equivalent"
                    % (p50, "at or above" if cap_ms and p50 >= cap_ms * 0.98 else "below", cap_ms or float("nan")))
        note.append("%s the 16.667 ms the 60 Hz compositor would impose if vsync were swapping"
                    % ("at or above" if p50 >= vsync_ms * 0.98 else "below"))
    return {"frameCapFps": args.framelimit, "frameCapEquivalentMs": cap_ms,
            "vsyncRequested": args.vsync, "compositorRefreshHz": 60,
            "note": "; ".join(note)}


# --------------------------------------------------------------------------- comparison

def comparison_rows(runs):
    rows = []
    by_config = {}
    for run in runs:
        by_config.setdefault(run["config"], []).append(run)
    keys = list(by_config)
    if "celtium" in keys and "reference-celeritas-989ebf2e" in keys:
        keys = ["celtium", "reference-celeritas-989ebf2e"] + [k for k in keys
                                                              if k not in ("celtium", "reference-celeritas-989ebf2e")]
    for key in keys:
        for run in by_config[key]:
            stats = run.get("frameStatistics") or {}
            rows.append({
                "config": key,
                "ordinal": run["ordinal"],
                "samples": stats.get("count"),
                "p50Ms": stats.get("p50Ms"),
                "p95Ms": stats.get("p95Ms"),
                "p99Ms": stats.get("p99Ms"),
                "maxMs": stats.get("maxMs"),
                "meanMs": stats.get("meanMs"),
                "fpsFromMean": stats.get("fpsFromMean"),
                "fpsOnePercentLow": stats.get("fpsOnePercentLow"),
                "cpuSeconds": (run.get("cpu") or {}).get("seconds"),
                "cpuPercent": (run.get("cpu") or {}).get("cpuPercentOfOneCore"),
                "peakRssMb": (run.get("memory") or {}).get("peakRssMb"),
                "loadAtWindowStart": (run.get("fixture") or {}).get("loadAverageStart"),
                "loadAtWindowEnd": (run.get("fixture") or {}).get("loadAverageEnd"),
                "windowSeconds": (run.get("fixture") or {}).get("windowSeconds"),
                "status": (run.get("fixture") or {}).get("status"),
                "device": (run.get("nativeDevice") or {}).get("name"),
                "terrainBackend": terrain_label(run),
            })
    return rows


def terrain_label(run):
    for line in (run.get("rendererDebugStringsAtWindowEnd") or []):
        if line.startswith("Terrain Backend:"):
            return line.split(":", 1)[1].strip()
    return None


def pair_deltas(runs):
    by_config = {}
    for run in runs:
        by_config.setdefault(run["config"], []).append(run)
    if "celtium" not in by_config or "reference-celeritas-989ebf2e" not in by_config:
        return []
    deltas = []
    fields = [("p50Ms", "lower"), ("p95Ms", "lower"), ("p99Ms", "lower"), ("maxMs", "lower"),
              ("meanMs", "lower"), ("fpsFromMean", "higher"), ("cpuSeconds", "lower"),
              ("peakRssMb", "lower")]
    for name, direction in fields:
        candidate = [extract(run, name) for run in by_config["celtium"]]
        reference = [extract(run, name) for run in by_config["reference-celeritas-989ebf2e"]]
        candidate = [value for value in candidate if value is not None]
        reference = [value for value in reference if value is not None]
        if not candidate or not reference:
            continue
        candidate_best = min(candidate) if direction == "lower" else max(candidate)
        reference_best = min(reference) if direction == "lower" else max(reference)
        deltas.append({
            "metric": name,
            "better": direction,
            "celtiumRuns": candidate,
            "referenceRuns": reference,
            "celtiumBest": candidate_best,
            "referenceBest": reference_best,
            "celtiumMinusReference": candidate_best - reference_best,
            "ratio": (candidate_best / reference_best) if reference_best else None,
        })
    return deltas


def extract(run, name):
    stats = run.get("frameStatistics") or {}
    if name in stats:
        return stats[name]
    if name == "cpuSeconds":
        return (run.get("cpu") or {}).get("seconds")
    if name == "peakRssMb":
        return (run.get("memory") or {}).get("peakRssMb")
    return None


# --------------------------------------------------------------------------- main

def main():
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--instance", type=Path, default=DEFAULT_INSTANCE)
    parser.add_argument("--java", type=Path, default=DEFAULT_JAVA)
    parser.add_argument("--javac", type=Path, default=DEFAULT_JAVAC)
    parser.add_argument("--render-node", type=Path, default=DEFAULT_RENDER_NODE)
    parser.add_argument("--vulkan-library", type=Path, default=DEFAULT_VULKAN_LIBRARY,
                        help="LWJGL Vulkan bindings added to the test classpath of *every* configuration, "
                             "so the classpaths stay identical; the pinned GL reference never loads it")
    parser.add_argument("--configs", default="celtium,reference-celeritas-989ebf2e",
                        help="comma-separated keys from %s" % ",".join(CONFIGS))
    parser.add_argument("--rounds", type=int, default=2,
                        help="rounds over all configurations; the order is reversed in every other round")
    parser.add_argument("--frames", type=int, default=600, help="measured frames per run")
    parser.add_argument("--warmup-ticks", type=int, default=240)
    parser.add_argument("--warmup-frames", type=int, default=300)
    parser.add_argument("--framelimit", type=int, default=260,
                        help="GameSettings.limitFramerate; 260 is the highest the 1.12.2 option accepts")
    parser.add_argument("--vsync", action="store_true", help="request vsync (default: off, as both sides use)")
    parser.add_argument("--render-distance", type=int, default=8)
    parser.add_argument("--chunk-blocks", type=int, default=64)
    parser.add_argument("--no-chunk-phase", dest="chunk_phase", action="store_false", default=True)
    parser.add_argument("--fixture-timeout", type=int, default=420,
                        help="seconds after which the fixture writes a partial report and exits")
    parser.add_argument("--duration", type=int, default=900,
                        help="launcher timeout in seconds; a timeout is not a result")
    parser.add_argument("--idle-timeout", type=int, default=900)
    parser.add_argument("--out", type=Path, help="results JSON (default .celtium-work/bench/results-<stamp>.json)")
    parser.add_argument("--only", help="run only this configuration key (still respects the idle gate)")
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument("--extra-jvm-arg", action="append", default=[])
    args = parser.parse_args()

    args.stamp = time.strftime("%Y%m%d-%H%M%S")
    keys = [key.strip() for key in args.configs.split(",") if key.strip()]
    if args.only:
        keys = [args.only]
    for key in keys:
        if key not in CONFIGS:
            parser.error("unknown configuration %r; known: %s" % (key, ", ".join(CONFIGS)))
        if not CONFIGS[key]["jar"].is_file():
            parser.error("jar for %s is missing: %s" % (key, CONFIGS[key]["jar"]))

    bench_jar, fixture_build = compile_fixture(args.java, args.javac)
    out_root = WORK
    runs_dir = out_root / "runs"
    runs_dir.mkdir(parents=True, exist_ok=True)
    out_json = args.out or (out_root / ("results-%s.json" % args.stamp))

    host = {
        "platform": platform.platform(),
        "machine": platform.machine(),
        "python": sys.version.split()[0],
        "cpuCount": os.cpu_count(),
        "loadAverageAtStart": load_average(),
        "gitHead": git("rev-parse", "HEAD"),
        "gitDirty": bool(git("status", "--porcelain")),
    }
    print("host: %s, %s cores, load %s" % (host["platform"], host["cpuCount"],
                                           " ".join("%.2f" % value for value in host["loadAverageAtStart"])))
    print("git HEAD %s (dirty=%s)" % (host["gitHead"], host["gitDirty"]))
    print("fixture jar %s" % bench_jar)

    runs = []
    hashes = {}
    order = []
    for round_index in range(max(1, args.rounds)):
        # Reversing the order in every other round is what keeps a slow drift in machine load from
        # landing on one configuration only.
        order.extend(keys if round_index % 2 == 0 else list(reversed(keys)))
    for ordinal, key in enumerate(order, start=1):
        print("[%d/%d] %s" % (ordinal, len(order), key), flush=True)
        if not args.dry_run:
            if not wait_for_idle(args.idle_timeout):
                print("    skipped: another game instance never exited")
                runs.append({"config": key, "ordinal": ordinal, "skipped": "machine busy"})
                continue
        record = run_configuration(key, ordinal, args, bench_jar, out_root, hashes)
        runs.append(record)
        stats = record.get("frameStatistics") or {}
        print("    p50=%.2f ms p95=%.2f p99=%.2f max=%.2f n=%s status=%s"
              % (stats.get("p50Ms", float("nan")), stats.get("p95Ms", float("nan")),
                 stats.get("p99Ms", float("nan")), stats.get("maxMs", float("nan")),
                 stats.get("count"), (record.get("fixture") or {}).get("status")), flush=True)

    results = {
        "schemaVersion": 1,
        "scope": ("steady-state frame timing, process CPU/RSS, chunk-update cost and selected GPU for "
                  "Celtium and the pinned Celeritas reference under identical settings; no speed-up is "
                  "asserted by this file"),
        "stamp": args.stamp,
        "host": host,
        "method": {
            "configurations": {key: {"what": CONFIGS[key]["what"], "jar": str(CONFIGS[key]["jar"])}
                               for key in keys},
            "frames": args.frames,
            "warmupTicks": args.warmup_ticks,
            "warmupFrames": args.warmup_frames,
            "framelimit": args.framelimit,
            "vsync": args.vsync,
            "renderDistance": args.render_distance,
            "chunkPhase": args.chunk_phase,
            "chunkBlocks": args.chunk_blocks,
            "seed": 12345678,
            "worldType": "FLAT",
            "resolution": "1280x720",
            "renderNode": str(args.render_node),
            "headlessCompositorRefreshHz": 60,
            "percentileRule": "nearest rank, ceil(f*n)-1, the same rule the validation fixture uses",
            "warmupRule": "measured frames start only after warmupTicks world ticks AND warmupFrames "
                          "rendered frames; the first eligible frame only opens the window and is not sampled",
            "cpuTimeSource": "in-process /proc/self/stat utime+stime, converted with CLK_TCK",
            "rssSource": "in-process /proc/self/status VmHWM",
            "loadSource": "/proc/loadavg, sampled inside the process at both ends of the window",
        },
        "fixtureBuild": fixture_build,
        "launcher": {
            "script": "scripts/prism_validation.py",
            "isolation": "fresh game directory under .celtium-work/bench/runs, mods copied from the "
                         "Prism instance's mods (Fugue, scalar) plus the two jars named here; the "
                         "instance's own minecraft/mods is never written to",
            "launcherClasspathSource": (newest_launch_classpath()[1].as_posix()
                                        if newest_launch_classpath()[1] else None),
        },
        "runs": runs,
        "table": comparison_rows(runs),
        "deltas": pair_deltas(runs),
        "limits": [
            "Whole-frame GPU time is not available: the Vulkan terrain backend publishes per-pass "
            "timestamp-query times, the pinned OpenGL reference publishes none, and nothing here "
            "measures the swap/present GPU cost of either configuration.",
            "The headless compositor's output is 1280x720@60Hz; the achieved frame times, not the "
            "vsync flag alone, are the evidence about whether either cap bound the run.",
            "This machine is shared with other agents; every run records the load average at both "
            "ends of its measured window and the comparison is only as good as those numbers.",
            "CPU time is the whole process (render thread, client thread, server thread and GC "
            "together), not the render thread alone.",
        ],
    }
    out_json.parent.mkdir(parents=True, exist_ok=True)
    out_json.write_text(json.dumps(results, indent=2) + "\n", encoding="utf-8")
    print("results: %s" % out_json)
    return 0


if __name__ == "__main__":
    sys.exit(main())
