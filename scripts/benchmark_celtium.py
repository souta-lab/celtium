#!/usr/bin/env python3
"""Benchmark Celtium against the pinned Celeritas reference under identical conditions.

What this produces, per configuration:

* raw per-frame times for a fixed number of frames, after a warm-up that must finish first, so
  percentiles (p50/p95/p99/max) are computed from samples rather than asserted;
* process CPU time and peak RSS measured inside the game process (``/proc/self``) and, independently,
  by sampling ``/proc/<pid>`` from outside;
* the load average at both ends of the measured window, and a comparison-validity verdict computed
  from those numbers (a comparison whose two sides ran under materially different load is reported as
  such, and the configuration on the loaded side is re-run once when ``--auto-rerun`` allows it);
* the frame cap, vsync flag, render distance, resolution, camera, seed and world time as they were
  actually seen at both ends, plus a cross-configuration audit that fails loudly if any of them differ;
* the renderer's own chunk-queue counters and device-memory counters (steady state and the
  post-placement rebuild window) and, when the backend publishes them, per-terrain-pass GPU times
  from its Vulkan timestamp queries;
* the selected GPU from the renderer's own ``CELTIUM_NATIVE_DEVICE`` line, plus
  ``logs/celtium-capabilities.json`` when the renderer's capability command ran, plus the fixture's
  own GL renderer string.

What it deliberately does not do: change the renderer, touch the validation fixture, copy anything into
the Prism instance, or claim a speed-up. It runs one game instance at a time (the same ``pgrep -x java``
+ ``/proc/<pid>/cmdline`` check the other agents use), it pins every configuration's jar by hash into
``.celtium-work/bench/jars/`` before the first run so a concurrent rebuild cannot change what is being
measured, and it hashes the pinned jar again after every run.

Configurations (``--configs``):

  celtium-glless                Celtium, GL-less native Vulkan terrain. The objective's target.
  celtium-bridge                Celtium, native Vulkan terrain through the GL bridge (shipped default).
  reference-celeritas-989ebf2e  pinned upstream Celeritas 989ebf2e, OpenGL terrain. The reference.
  celtium-gl-only               Celtium with the native model draws disabled. Not a GL reference: the
                                terrain backend stays Vulkan. Only for use as a labelled substitute if
                                the pinned upstream jar cannot be run.

Usage:

    python3 scripts/benchmark_celtium.py --rounds 2 --frames 900 --warmup-ticks 600
    python3 scripts/benchmark_celtium.py --configs celtium-glless --rounds 1 --frames 120 --dry-run
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
PINNED_JARS = WORK / "jars"

DEFAULT_INSTANCE = Path("/home/s0u7a/.local/share/PrismLauncher/instances/cleanroom-0.6.13-alpha")
DEFAULT_JAVA = Path("/usr/lib/jvm/java-25-openjdk-amd64/bin/java")
DEFAULT_JAVAC = Path("/usr/lib/jvm/java-25-openjdk-amd64/bin/javac")
DEFAULT_RENDER_NODE = Path("/dev/dri/renderD129")  # AMD RX 6400; renderD128 is the Intel UHD 630
# The Vulkan bindings the renderer's native backend needs. Every configuration adds this jar to the test
# classpath, including the GL reference, so the classpaths stay identical; the reference never loads it.
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
# `IntegratedServer.addScheduledTask` is declared to return a Guava `ListenableFuture`, so the type has to
# be resolvable to compile against it even though the call's result is ignored. The game ships Guava at
# runtime; this is the same jar for the compiler only.
COMPILE_ONLY_JARS = [
    LIBS / "com/google/guava/guava/33.6.0-jre/guava-33.6.0-jre.jar",
]

# The development version was rebranded from `2.5.12-celtium.0.1.0-dev.1` to `0.0.2-alpha-dev`, and the old build
# output stayed in `forge122/build/libs`. A recipe that names the old file - or globs the directory and takes the
# last match, because the old name sorts after every `0.0.2` one - then measures a build from before the rename,
# and nothing in the numbers says so: the record still reads "Celtium".
#
# That trap fired: a loading-screen leg was launched from the documented recipe with the 13:08 jar, and its missing
# `loading` stage was read as "the hook does not fire" while `celtium$dumpLoadingFrame` was simply not in the jar
# (the leg's `celtium-loading.jar` was byte-identical to `celtium-forge-mc1.12.2-2.5.12-celtium.0.1.0-dev.1.jar`).
# So the jar is resolved from the build output by name family and mtime, every candidate is printed, and a
# pre-rebrand or intermediate candidate is named as ignored instead of being silently passed over.
PRE_REBRAND_VERSION = "2.5.12-celtium.0.1.0-dev.1"


def resolve_celtium_jar():
    """The Celtium jar to measure: the newest real artifact in ``forge122/build/libs``.

    Intermediates (``*-reobf.jar``, ``*-dev-dev.jar``) and pre-rebrand names are excluded by name, the rest are
    ordered by mtime. The selection is printed because a benchmark's first question is which bytes ran, and the
    answer must be in the log even when the run is killed before `pin_jar` records it.
    """
    candidates = sorted((ROOT / "forge122/build/libs").glob("celtium-forge-mc1.12.2-*.jar"),
                        key=lambda path: path.stat().st_mtime, reverse=True)
    usable = [path for path in candidates
              if PRE_REBRAND_VERSION not in path.name
              and not path.name.endswith(("-reobf.jar", "-dev-dev.jar"))]
    if not usable:
        found = ", ".join(path.name for path in candidates) or "nothing"
        raise SystemExit("no measurable celtium-forge jar in forge122/build/libs (found: %s); run "
                         "scripts/build.sh first" % found)
    chosen = usable[0]
    print("Celtium jar under test: %s (%.2f MB, built %s)"
          % (chosen.name, chosen.stat().st_size / 1e6,
             time.strftime("%H:%M:%S", time.localtime(chosen.stat().st_mtime))))
    for other in usable[1:]:
        print("    also present, older: %s" % other.name)
    for old in candidates:
        if old not in usable:
            print("    ignored (pre-rebrand or intermediate): %s" % old.name)
    return chosen


CELTIUM_JAR = resolve_celtium_jar()
BASELINE_JAR = (ROOT / ".celtium-work/upstream-baseline/forge122/build/libs/"
                       "celeritas-forge-mc12.2-2.5.12-upstream.989ebf2e.jar")

CONFIGS = {
    "celtium-glless": {
        "jar": CELTIUM_JAR,
        "what": "Celtium fork, GL-less native Vulkan terrain (the objective's target configuration)",
        "jvmArgs": ["-Dceltium.vulkan.glless=true"],
        "native": True,
    },
    "celtium-bridge": {
        "jar": CELTIUM_JAR,
        "what": "Celtium fork, native Vulkan terrain through the GL bridge (the shipped default)",
        "jvmArgs": [],
        "native": True,
    },
    "reference-celeritas-989ebf2e": {
        "jar": BASELINE_JAR,
        "what": "pinned upstream Celeritas 989ebf2e, OpenGL terrain (the reference)",
        "jvmArgs": [],
        "native": False,
    },
    "celtium-gl-only": {
        "jar": CELTIUM_JAR,
        "what": "Celtium fork with the native model draws disabled (-Dceltium.vulkan.modelDraw=false). The "
                "terrain backend stays Vulkan, so this is NOT a GL reference; it is only a labelled "
                "substitute for use if the pinned upstream jar cannot be run",
        "jvmArgs": ["-Dceltium.vulkan.modelDraw=false"],
        "native": True,
    },
}

DEFAULT_CONFIGS = "celtium-glless,celtium-bridge,reference-celeritas-989ebf2e"
DEFAULT_BASELINE = "reference-celeritas-989ebf2e"
# A static flat world: no lava, fire, water or leaves, because those keep producing block updates (the
# chunk queue then never rests, the visible scene drifts between runs, and the chunk-update cost the
# benchmark is supposed to isolate is drowned by the ticking). Stained glass keeps the translucent pass
# populated; the stack is bedrock, two dirt, grass, glass, so the fixed camera at y=12 looks down on it.
DEFAULT_WORLD_LAYERS = ("3;minecraft:bedrock,2*minecraft:dirt,minecraft:grass,"
                        "minecraft:stained_glass;1;")
DEFAULT_CAMERA = "0.5,12.0,0.5,180.0,20.0"  # x,y,z,yaw,pitch; the validation fixture's terrain pose

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


def gpu_metrics_dir(render_node):
    """The sysfs directory of the GPU behind a render node, when the driver publishes counters.

    amdgpu exposes ``gpu_busy_percent``, ``mem_info_vram_used`` and ``mem_info_gtt_used`` here. They are
    the only GPU-side numbers that can be taken for *every* configuration: the renderer's own timestamp
    queries exist only on the Vulkan terrain backend, so the OpenGL reference has none of its own.
    """
    try:
        device = (Path("/sys/class/drm") / Path(render_node).name / "device").resolve()
    except OSError:
        return None
    if (device / "gpu_busy_percent").is_file() or (device / "mem_info_vram_used").is_file():
        return device
    return None


def sample_gpu(directory):
    if directory is None:
        return None
    out = {}
    for key, name in (("busyPercent", "gpu_busy_percent"),
                      ("vramUsedBytes", "mem_info_vram_used"),
                      ("vramTotalBytes", "mem_info_vram_total"),
                      ("gttUsedBytes", "mem_info_gtt_used")):
        try:
            out[key] = int((directory / name).read_text().strip())
        except (OSError, ValueError):
            out[key] = None
    return out


WINDOW_LINE = re.compile(r"^\[(\d{2}):(\d{2}):(\d{2})\] .*\[CeltiumBench\] window (open|close)")


def fixture_window_epochs(console_text, reference_epoch):
    """The wall-clock seconds at which the fixture opened and closed its measured window.

    The console log carries a local ``[HH:MM:SS]`` and no date, so the run's own start supplies the date;
    a close earlier than the open means the window crossed midnight.
    """
    marks = {}
    for line in console_text.splitlines():
        match = WINDOW_LINE.match(line.strip())
        if not match:
            continue
        hour, minute, second, which = (int(match.group(1)), int(match.group(2)),
                                       int(match.group(3)), match.group(4))
        local = time.localtime(reference_epoch)
        stamp = time.mktime((local.tm_year, local.tm_mon, local.tm_mday, hour, minute, second, 0, 0, -1))
        while stamp < reference_epoch - 3600:
            stamp += 86400
        marks[which] = stamp
    if "open" not in marks:
        return None, None
    close = marks.get("close")
    if close is not None and close < marks["open"]:
        close += 86400
    return marks["open"], close


def gpu_statistics(samples, key):
    values = [sample.get(key) for sample in samples]
    values = [value for value in values if isinstance(value, (int, float))]
    if not values:
        return None
    ordered = sorted(values)
    return {
        "count": len(values),
        "min": ordered[0],
        "median": statistics.median(values),
        "mean": statistics.fmean(values),
        "p95": percentile_nearest_rank(values, 0.95),
        "max": ordered[-1],
    }


def pin_jar(config_key, args):
    """Copy a configuration's jar to a content-addressed path and measure the copy, not the build output.

    Another agent rebuilding ``forge122/build/libs`` in the middle of a comparison would otherwise change
    what is being measured between two runs. The pinned copy is named after its own SHA-256, so two runs
    that used the same bytes share one path and the hash is visible in the name.
    """
    source = CONFIGS[config_key]["jar"]
    if not source.is_file():
        raise SystemExit("jar for %s is missing: %s" % (config_key, source))
    source_sha = digest(source)
    target = PINNED_JARS / ("%s-%s" % (source_sha[:16], source.name))
    if not target.is_file() or digest(target) != source_sha:
        PINNED_JARS.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source, target)
    pinned_sha = digest(target)
    if pinned_sha != source_sha:
        raise SystemExit("the pinned copy of %s does not match its source (%s != %s); the build output "
                         "changed while it was being copied" % (source, pinned_sha, source_sha))
    return {"pinnedPath": str(target), "pinnedSha256": pinned_sha,
            "sourcePath": str(source), "sourceSha256": source_sha,
            "sourceMtime": source.stat().st_mtime,
            "sourceMtimeIso": time.strftime("%Y-%m-%dT%H:%M:%S", time.localtime(source.stat().st_mtime))}


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
    for jar in [MCP_JAR] + LWJGL_JARS + COMPILE_ONLY_JARS:
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
        return jar_path, {"reused": True, "sha256": digest(jar_path)}
    if classes.exists():
        shutil.rmtree(classes)
    classes.mkdir(parents=True)
    classpath = os.pathsep.join(str(jar) for jar in [MCP_JAR] + LWJGL_JARS + COMPILE_ONLY_JARS)
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
    kept = sorted({name for entry in remap_report.values() for name in entry["leftWithTheirOwnName"]})
    if kept:
        # A Minecraft member the mapping leaves alone is a runtime NoSuchMethodError waiting to happen,
        # so the names are printed rather than counted.
        print("fixture members left with their own name: %s" % ", ".join(kept))
    print("fixture jar: %s (%d bytes, sha256 %s, %d members remapped MCP->SRG)"
          % (jar_path, jar_path.stat().st_size, digest(jar_path)[:16],
             sum(len(entry["renamed"]) for entry in remap_report.values())))
    return jar_path, {"reused": False, "sha256": digest(jar_path),
                      "mappedMinecraft": str(MCP_JAR),
                      "mappedMinecraftSha256": digest(MCP_JAR),
                      "srgMinecraft": str(SRG_JAR), "srgMinecraftSha256": digest(SRG_JAR),
                      "remappedMembers": sum(len(entry["renamed"]) for entry in remap_report.values()),
                      "leftWithTheirOwnName": kept,
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
NATIVE_DEVICE = re.compile(r'CELTIUM_NATIVE_DEVICE name="([^"]*)" uuid=([0-9a-fA-F]*) matchedOpenGl=(\w+)')
CHUNK_QUEUES = re.compile(r"Chunk Queues: U=(\d+) \(P0=(\d+) \| P1=(\d+) \| P2=(\d+)\) S=(\d+)/(\d+)")
DEVICE_MEMORY = re.compile(r"G:\s*(\d+)/(\d+),\s*I:\s*(\d+)/(\d+)\s*MiB\s*\((\d+) buffers\)")
TRANSFER_QUEUE = re.compile(r"Transfer Queue:\s*(.*)$")
TREE_SORT = re.compile(r"Tree Sort: (\d+) planes, (\d+) trig/s, (\d+) sorts/s, (\S+) avg")


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
    match = NATIVE_DEVICE.search(console_text)
    if not match:
        return {"found": False}
    return {"found": True, "name": match.group(1), "uuid": match.group(2),
            "matchedOpenGl": match.group(3) == "true"}


def parse_renderer_counters(debug_strings):
    """The renderer's own counters, as published in its F3 debug strings.

    Every field is ``None`` when the line is absent: the pinned upstream reference publishes the same
    queue lines but no ``Terrain Backend:`` line, and a GL backend has no Vulkan timestamps to publish.
    """
    counters = {"chunkQueues": None, "deviceMemory": None, "transferQueue": None, "treeSort": None,
                "terrainBackend": None}
    for line in debug_strings or []:
        text = line.strip()
        queue = CHUNK_QUEUES.search(text)
        if queue:
            counters["chunkQueues"] = {
                "upload": int(queue.group(1)),
                "p0": int(queue.group(2)), "p1": int(queue.group(3)), "p2": int(queue.group(4)),
                "sortImportant": int(queue.group(5)), "sort": int(queue.group(6)),
                "total": sum(int(queue.group(index)) for index in (1, 2, 3, 4)),
            }
        memory = DEVICE_MEMORY.search(text)
        if memory:
            counters["deviceMemory"] = {
                "geometryUsedMib": int(memory.group(1)), "geometryAllocatedMib": int(memory.group(2)),
                "indexUsedMib": int(memory.group(3)), "indexAllocatedMib": int(memory.group(4)),
                "buffers": int(memory.group(5)),
            }
        transfer = TRANSFER_QUEUE.search(text)
        if transfer:
            counters["transferQueue"] = transfer.group(1)
        tree = TREE_SORT.search(text)
        if tree:
            counters["treeSort"] = {"planes": int(tree.group(1)), "triggersPerSecond": int(tree.group(2)),
                                    "sortsPerSecond": int(tree.group(3)), "average": tree.group(4)}
        if text.startswith("Terrain Backend:"):
            counters["terrainBackend"] = text.split(":", 1)[1].strip()
    return counters


def clk_tck():
    try:
        return os.sysconf("SC_CLK_TCK")
    except (ValueError, OSError):
        return 100


def parse_load(text):
    try:
        return float(str(text).split()[0])
    except (ValueError, IndexError, TypeError):
        return None


# --------------------------------------------------------------------------- one run

def run_configuration(config_key, run_ordinal, args, bench_jar, out_root, pinned, tag=""):
    cfg = CONFIGS[config_key]
    pin = pinned[config_key]
    jar = Path(pin["pinnedPath"])
    suffix = ("-" + tag) if tag else ""
    run_dir = out_root / "runs" / ("%s-%02d-%s%s" % (args.stamp, run_ordinal, config_key, suffix))
    lock = Path(str(run_dir) + ".lock.json")
    launcher_log = Path(str(run_dir) + ".launcher.log")
    if run_dir.exists():
        raise SystemExit("run directory already exists: %s" % run_dir)
    jar_before = digest(jar)
    record = {
        "config": config_key,
        "what": cfg["what"],
        "tag": tag,
        "ordinal": run_ordinal,
        "jar": str(jar),
        "jarSha256Before": jar_before,
        "jarSha256After": None,
        "jarChangedDuringRun": None,
        "jarSourcePath": pin["sourcePath"],
        "jarSourceSha256": pin["sourceSha256"],
        "jarSourceMtimeIso": pin["sourceMtimeIso"],
        "jarChangedSincePin": jar_before != pin["pinnedSha256"],
        "gameDirectory": str(run_dir.relative_to(ROOT)),
        "startedAt": time.strftime("%Y-%m-%dT%H:%M:%S"),
        "loadAverageBeforeLaunch": load_average(),
    }

    jvm_args = [
        "-Dceltium.bench=true",
        "-Dceltium.bench.label=%s" % (config_key + suffix),
        "-Dceltium.bench.frames=%d" % args.frames,
        "-Dceltium.bench.warmupTicks=%d" % args.warmup_ticks,
        "-Dceltium.bench.warmupFrames=%d" % args.warmup_frames,
        "-Dceltium.bench.framelimit=%d" % args.framelimit,
        "-Dceltium.bench.vsync=%s" % ("true" if args.vsync else "false"),
        "-Dceltium.bench.renderDistance=%d" % args.render_distance,
        "-Dceltium.bench.chunkPhase=%s" % ("true" if args.chunk_phase else "false"),
        "-Dceltium.bench.chunkBlocks=%d" % args.chunk_blocks,
        "-Dceltium.bench.chunkDrainWindowFrames=%d" % args.chunk_window_frames,
        "-Dceltium.bench.timeoutSeconds=%d" % args.fixture_timeout,
        "-Dceltium.bench.worldLayers=%s" % args.world_layers,
        "-Dceltium.bench.cameraX=%s" % args.camera_pose[0],
        "-Dceltium.bench.cameraY=%s" % args.camera_pose[1],
        "-Dceltium.bench.cameraZ=%s" % args.camera_pose[2],
        "-Dceltium.bench.cameraYaw=%s" % args.camera_pose[3],
        "-Dceltium.bench.cameraPitch=%s" % args.camera_pose[4],
    ] + list(cfg["jvmArgs"]) + list(args.extra_jvm_arg or [])
    record["benchProperties"] = jvm_args

    command = [
        sys.executable, str(SCRIPTS / "prism_validation.py"), str(args.instance),
        "--output", str(lock), "--java", str(args.java), "--game-dir", str(run_dir),
        "--mod", str(jar), "--mod", str(bench_jar),
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
    gpu_samples = []
    gpu_dir = gpu_metrics_dir(args.render_node)
    run_started_epoch = time.time()
    with launcher_log.open("w") as log:
        process = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, cwd=ROOT)
        game_pid = None
        while process.poll() is None:
            if game_pid is None or not Path("/proc/%s" % game_pid).exists():
                game_pid = find_game_pid(run_dir)
            sample = sample_process(game_pid) if game_pid else None
            if sample:
                samples.append(sample)
            gpu = sample_gpu(gpu_dir)
            if gpu:
                gpu["atEpoch"] = time.time()
                gpu_samples.append(gpu)
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
    record["gpuMetricsDir"] = str(gpu_dir) if gpu_dir else None

    # ---- collect what the run left behind
    fixture_path = run_dir / "logs" / "celtium-benchmark.json"
    if fixture_path.is_file():
        try:
            record["fixture"] = json.loads(fixture_path.read_text())
            record["fixturePath"] = str(fixture_path)
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
    # The fork's development build prints its own diagnostic counters to stdout; the pinned reference
    # does not. The volume is measured here rather than assumed, because it is a cost one side pays and
    # the other does not.
    logging = {
        "consoleLines": console_text.count("\n"),
        "consoleBytes": len(console_text.encode("utf-8", "replace")),
        "celitumDiagnosticLines": console_text.count("CELTIUM_"),
    }
    latest_log = run_dir / "logs" / "latest.log"
    if latest_log.is_file():
        try:
            with latest_log.open("rb") as stream:
                data = stream.read()
            logging["latestLogBytes"] = len(data)
            logging["latestLogLines"] = data.count(b"\n")
        except OSError:
            pass
    record["logging"] = logging

    # ---- GPU-side samples (amdgpu sysfs), sliced to the fixture's own measured window
    window_open, window_close = fixture_window_epochs(console_text, run_started_epoch)
    record["windowEpochs"] = {"open": window_open, "close": window_close,
                              "runStarted": run_started_epoch}
    record["gpuLoad"] = {"source": ("%s gpu_busy_percent / mem_info_vram_used, sampled every 0.2 s "
                                    "from outside the game process" % gpu_dir) if gpu_dir else None,
                         "wholeGpu": bool(gpu_dir),
                         "sampleCount": len(gpu_samples)}
    if gpu_samples:
        window_samples = [sample for sample in gpu_samples
                          if window_open is not None and window_close is not None
                          and window_open - 1.0 <= sample.get("atEpoch", 0) <= window_close + 1.0]
        record["gpuLoad"]["wholeRun"] = {
            "busyPercent": gpu_statistics(gpu_samples, "busyPercent"),
            "vramUsedBytes": gpu_statistics(gpu_samples, "vramUsedBytes"),
            "gttUsedBytes": gpu_statistics(gpu_samples, "gttUsedBytes"),
        }
        record["gpuLoad"]["measuredWindow"] = {
            "sampleCount": len(window_samples),
            "busyPercent": gpu_statistics(window_samples, "busyPercent"),
            "vramUsedBytes": gpu_statistics(window_samples, "vramUsedBytes"),
            "gttUsedBytes": gpu_statistics(window_samples, "gttUsedBytes"),
        }

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
        "CeltiumBench": [line for line in console_text.splitlines() if "[CeltiumBench]" in line][:60],
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
    # The post-placement rebuild window is compared over a fixed number of frames for every
    # configuration, so a queue that drains early does not silently shrink its own sample.
    chunk_frames = (fixture.get("chunkDrainFrameTimesMs") or [])[:args.chunk_window_frames]
    record["chunkPhaseStatistics"] = frame_statistics(chunk_frames)
    record["chunkCompareFrames"] = args.chunk_window_frames
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
    record["rendererCountersAtWindowEnd"] = parse_renderer_counters(debug_end)
    record["rendererCountersAtWindowStart"] = parse_renderer_counters(
        fixture.get("debugStringsAtWindowStart") or [])
    joined_debug = "\n".join(debug_end)
    record["terrainCountersAtWindowEnd"] = {
        "terrainPasses": (TERRAIN_COUNTERS.search(joined_debug).group(1)
                          if TERRAIN_COUNTERS.search(joined_debug) else None),
        "immediateTaken": (IMMEDIATE_COUNTERS.search(joined_debug).group(1)
                           if IMMEDIATE_COUNTERS.search(joined_debug) else None),
    }
    record["rendererDebugStringsAtWindowEnd"] = debug_end
    record["windowLoadStart"] = parse_load(fixture.get("loadAverageStart"))
    record["windowLoadEnd"] = parse_load(fixture.get("loadAverageEnd"))
    record["achievedFpsVersusCaps"] = describe_caps(record, args)
    record["chunkUpdate"] = describe_chunk_update(record, args)
    record["jarSha256After"] = digest(jar)
    record["jarChangedDuringRun"] = record["jarSha256After"] != jar_before
    record["finishedAt"] = time.strftime("%Y-%m-%dT%H:%M:%S")
    return record


def cap_verdict(record):
    """Is a run's frame interval a render cost, or a floor imposed outside the renderer?

    A frame time that sits on the 60 Hz presentation interval with a nearly constant distribution is not
    a measure of how long the renderer takes; a frame time well below that interval is. The distinction
    is the difference between a result and an artefact, so it is computed from the samples rather than
    left to the reader.
    """
    stats = record.get("frameStatistics") or {}
    fixture = record.get("fixture") or {}
    p50 = stats.get("p50Ms")
    p95 = stats.get("p95Ms")
    vsync_ms = 1000.0 / 60.0  # the headless compositor's output is 1280x720@60Hz
    cap_fps = fixture.get("framelimitAtEnd") or fixture.get("framelimitAtStart")
    cap_ms = (1000.0 / cap_fps) if cap_fps else None
    if not p50:
        return {"verdict": "no samples", "p50Ms": p50, "p95Ms": p95, "spreadMs": None,
                "presentationIntervalMs": vsync_ms, "frameCapMs": cap_ms,
                "detail": "the run produced no measured frames"}
    spread = (p95 - p50) if p95 is not None else None
    on_interval = abs(p50 - vsync_ms) <= vsync_ms * 0.06
    tight = spread is not None and spread <= 1.5
    if on_interval and tight:
        verdict = "presentation-floor"
        detail = ("p50 %.3f ms is within 6%% of the 16.667 ms 60 Hz interval and p95-p50 is only %.2f ms, "
                  "so this frame interval is a floor imposed outside the renderer (swap, compositor or "
                  "limiter), not a render cost" % (p50, spread))
    elif p50 < vsync_ms * 0.94:
        verdict = "render-cost"
        detail = ("p50 %.3f ms is %.0f%% below the 16.667 ms 60 Hz interval, so the frame time is the "
                  "render cost rather than a floor" % (p50, 100.0 * (1.0 - p50 / vsync_ms)))
    else:
        verdict = "inconclusive"
        detail = ("p50 %.3f ms is near the 16.667 ms 60 Hz interval but the distribution is not tight "
                  "(p95-p50 = %s ms), so it is neither a clean floor nor a clean cost"
                  % (p50, ("%.2f" % spread) if spread is not None else "n/a"))
    return {"verdict": verdict, "p50Ms": p50, "p95Ms": p95, "spreadMs": spread,
            "presentationIntervalMs": vsync_ms, "frameCapMs": cap_ms, "detail": detail}


def describe_caps(record, args):
    """Whether the frame cap, the vsync flag and the compositor could have bound this run."""
    fixture = record.get("fixture") or {}
    stats = record.get("frameStatistics") or {}
    p50 = stats.get("p50Ms")
    cap_ms = 1000.0 / args.framelimit if args.framelimit else None
    vsync_ms = 1000.0 / 60.0  # the headless compositor's output is 1280x720@60Hz
    note = []
    if p50:
        if cap_ms and p50 > cap_ms * 1.02:
            note.append("p50 %.3f ms is slower than the %.3f ms the %s fps cap allows, so the cap did "
                        "not bind this run" % (p50, cap_ms, args.framelimit))
        else:
            note.append("p50 %.3f ms is at or below the %.3f ms the %s fps cap allows, so the cap may "
                        "have bound this run" % (p50, cap_ms or float("nan"), args.framelimit))
        note.append("%s the 16.667 ms the 60 Hz compositor would impose if vsync were swapping"
                    % ("at or above" if p50 >= vsync_ms * 0.98 else "below"))
    return {
        "frameCapRequestedFps": args.framelimit,
        "frameCapEquivalentMs": cap_ms,
        "frameCapSeenAtWindowStart": fixture.get("framelimitAtStart"),
        "frameCapSeenAtWindowEnd": fixture.get("framelimitAtEnd"),
        "vsyncRequested": args.vsync,
        "vsyncSeenAtWindowStart": fixture.get("vsyncAtStart"),
        "vsyncSeenAtWindowEnd": fixture.get("vsyncAtEnd"),
        "vsyncAppliedHow": fixture.get("vsyncAppliedHow"),
        "compositorRefreshHz": 60,
        "note": "; ".join(note) if note else "no measured frames, so no cap verdict",
        "verdict": cap_verdict(record),
    }


def describe_chunk_update(record, args):
    """What the run says about chunk updates, and what produced it."""
    fixture = record.get("fixture") or {}
    stats = record.get("chunkPhaseStatistics") or {}
    queues_start = (record.get("rendererCountersAtWindowStart") or {}).get("chunkQueues")
    queues_end = (record.get("rendererCountersAtWindowEnd") or {}).get("chunkQueues")
    return {
        "source": "the fixture's chunk phase: N blocks placed at a fixed tick cadence, then the frame "
                  "times of the next %d frames; plus the renderer's own Chunk Queues line at both ends "
                  "of the steady-state window where the renderer publishes it" % args.chunk_window_frames,
        "blocksRequested": fixture.get("chunkBlocksRequested"),
        "blocksPlaced": fixture.get("chunkBlocksPlaced"),
        "placeFailures": fixture.get("chunkPlaceFailures"),
        "placeMillis": fixture.get("chunkPlaceMillis"),
        "phaseSeconds": fixture.get("chunkPhaseSeconds"),
        "framesAfterPlacement": fixture.get("chunkFramesAfterPlacement"),
        "queueCountersReadable": fixture.get("chunkQueueCountersReadable"),
        "queueEmptyAtFrame": fixture.get("chunkQueueEmptyAtFrame"),
        "queueAtFirstFrameAfterPlacement": fixture.get("chunkQueueAtDrainStart"),
        "queueAtPhaseEnd": fixture.get("chunkQueueAtPhaseEnd"),
        "drainCriterion": fixture.get("chunkDrainCriterion"),
        "postPlacementFrameStatistics": stats,
        "queueDepthAtWindowStart": (queues_start or {}).get("total"),
        "queueDepthAtWindowEnd": (queues_end or {}).get("total"),
        "queueBreakdownAtWindowEnd": queues_end,
        "queueMaxDuringWindow": fixture.get("queueMaxDuringWindow"),
    }


# --------------------------------------------------------------------------- comparison

def comparison_rows(runs):
    rows = []
    for run in runs:
        if run.get("skipped"):
            continue
        stats = run.get("frameStatistics") or {}
        fixture = run.get("fixture") or {}
        counters = run.get("rendererCountersAtWindowEnd") or {}
        rows.append({
            "config": run["config"],
            "tag": run.get("tag") or "",
            "ordinal": run["ordinal"],
            "samples": stats.get("count"),            "p50Ms": stats.get("p50Ms"),
            "p95Ms": stats.get("p95Ms"),
            "p99Ms": stats.get("p99Ms"),
            "maxMs": stats.get("maxMs"),
            "meanMs": stats.get("meanMs"),
            "fpsFromMean": stats.get("fpsFromMean"),
            "fpsFromP50": stats.get("fpsFromP50"),
            "fpsOnePercentLow": stats.get("fpsOnePercentLow"),
            "cpuSeconds": (run.get("cpu") or {}).get("seconds"),
            "cpuPercent": (run.get("cpu") or {}).get("cpuPercentOfOneCore"),
            "peakRssMb": (run.get("memory") or {}).get("peakRssMb"),
            "windowSeconds": fixture.get("windowSeconds"),
            "loadAtWindowStart": run.get("windowLoadStart"),
            "loadAtWindowEnd": run.get("windowLoadEnd"),
            "loadOutlier": run.get("loadOutlier"),
            "status": fixture.get("status"),
            "reason": fixture.get("reason"),
            "device": (run.get("nativeDevice") or {}).get("name"),
            "glRenderer": fixture.get("glRendererString"),
            "terrainBackend": counters.get("terrainBackend") or terrain_label(run),
            "geometryUsedMib": (counters.get("deviceMemory") or {}).get("geometryUsedMib"),
            "queueStart": (run.get("chunkUpdate") or {}).get("queueDepthAtWindowStart"),
            "queueEnd": (run.get("chunkUpdate") or {}).get("queueDepthAtWindowEnd"),
            "queueMax": fixture.get("queueMaxDuringWindow"),
            "chunkEmptyAtFrame": fixture.get("chunkQueueEmptyAtFrame"),
            "chunkPlaceMillis": fixture.get("chunkPlaceMillis"),
        })
    return rows


def terrain_label(run):
    for line in (run.get("rendererDebugStringsAtWindowEnd") or []):
        if line.startswith("Terrain Backend:"):
            return line.split(":", 1)[1].strip()
    return None


def run_load(run):
    """The 1-minute load average at the start of the measured window, with a fallback."""
    value = run.get("windowLoadStart")
    if value is not None:
        return value
    band = run.get("loadAverageDuringRunOneMinMax")
    if band:
        return band[0]
    return None


def load_context(runs, args):
    """Whether the configurations were compared under materially the same machine load.

    The demanded control is the machine's load average at the time of each measurement and a re-run if
    it changed materially between the two configurations being compared. This function computes the
    verdict from the fixture's own in-window samples: runs whose window-start 1-minute load exceeds
    (lowest window-start load + --load-delta-limit) are marked outliers and excluded from the deltas.
    """
    usable = [run for run in runs if run_load(run) is not None]
    loads = [run_load(run) for run in usable]
    if not loads:
        return {"balanced": None, "reason": "no run recorded a load average", "outliers": [],
                "runsWithLoad": 0, "limit": args.load_delta_limit, "spread": None}
    low, high = min(loads), max(loads)
    outliers = []
    for run in runs:
        value = run_load(run)
        run["loadOutlier"] = bool(value is not None and value > low + args.load_delta_limit)
        if run["loadOutlier"]:
            outliers.append({"config": run["config"], "tag": run.get("tag") or "",
                             "ordinal": run["ordinal"], "loadStart": value, "bandLow": low})
    return {
        "balanced": (high - low) <= args.load_delta_limit,
        "reason": "window-start 1-minute load averages span %.2f..%.2f over %d runs; the comparison is "
                  "%s within the %.2f limit"
                  % (low, high, len(loads), "balanced" if (high - low) <= args.load_delta_limit
                     else "NOT balanced", args.load_delta_limit),
        "outliers": outliers,
        "runsWithLoad": len(usable),
        "low": low,
        "high": high,
        "spread": high - low,
        "limit": args.load_delta_limit,
    }


def comparable_runs(runs):
    """The runs the deltas are computed from: not load outliers, and with usable frame statistics."""
    return [run for run in runs
            if not run.get("loadOutlier") and (run.get("frameStatistics") or {}).get("count")]


def median_of(runs, extractor):
    values = [extractor(run) for run in runs]
    values = [value for value in values if value is not None]
    return statistics.median(values) if values else None


def pair_deltas(runs, args):
    by_config = {}
    for run in runs:
        by_config.setdefault(run["config"], []).append(run)
    baseline_key = args.baseline
    if baseline_key not in by_config:
        return [], baseline_key, "the baseline configuration %r has no usable run" % baseline_key
    deltas = []
    fields = [("p50Ms", "lower"), ("p95Ms", "lower"), ("p99Ms", "lower"), ("maxMs", "lower"),
              ("meanMs", "lower"), ("fpsFromP50", "higher"), ("fpsOnePercentLow", "higher"),
              ("cpuPercent", "lower"), ("cpuSeconds", "lower"), ("peakRssMb", "lower")]
    baseline_runs = comparable_runs(by_config[baseline_key])
    if not baseline_runs:
        return [], baseline_key, "the baseline %r has no run that passed the load gate" % baseline_key
    for key, config_runs in by_config.items():
        if key == baseline_key:
            continue
        candidate_runs = comparable_runs(config_runs)
        if not candidate_runs:
            deltas.append({"config": key, "metrics": [],
                           "note": "no run passed the load gate, so no delta is computed"})
            continue
        metrics = []
        for name, direction in fields:
            candidate = median_of(candidate_runs, lambda run, name=name: extract(run, name))
            baseline = median_of(baseline_runs, lambda run, name=name: extract(run, name))
            if candidate is None or baseline is None:
                continue
            metrics.append({
                "metric": name,
                "better": direction,
                "candidateMedian": candidate,
                "baselineMedian": baseline,
                "candidateMinusBaseline": candidate - baseline,
                "ratio": (candidate / baseline) if baseline else None,
                "candidateRuns": [extract(run, name) for run in candidate_runs],
                "baselineRuns": [extract(run, name) for run in baseline_runs],
                "improvement": (baseline - candidate) if direction == "lower" else (candidate - baseline),
                "candidateRunCount": len(candidate_runs),
                "baselineRunCount": len(baseline_runs),
            })
        deltas.append({"config": key, "metrics": metrics,
                       "candidateRuns": [run["ordinal"] for run in candidate_runs],
                       "baselineRuns": [run["ordinal"] for run in baseline_runs]})
    return deltas, baseline_key, None


def extract(run, name):
    stats = run.get("frameStatistics") or {}
    if name in stats:
        return stats[name]
    if name == "cpuSeconds":
        return (run.get("cpu") or {}).get("seconds")
    if name == "cpuPercent":
        return (run.get("cpu") or {}).get("cpuPercentOfOneCore")
    if name == "peakRssMb":
        return (run.get("memory") or {}).get("peakRssMb")
    return None


def controls_audit(runs, args):
    """Every control that has to be equal across configurations, checked rather than assumed."""
    runs = [run for run in runs if not run.get("skipped")]
    fields = [
        ("framelimitAtStart", "frame cap seen at window start", True),
        ("framelimitAtEnd", "frame cap seen at window end", True),
        ("vsyncAtStart", "vsync flag seen at window start", True),
        ("vsyncAtEnd", "vsync flag seen at window end", True),
        ("renderDistanceAtStart", "render distance at window start", True),
        ("renderDistanceAtEnd", "render distance at window end", True),
        ("displayWidth", "framebuffer width", True),
        ("displayHeight", "framebuffer height", True),
        ("seedRequested", "world seed requested", True),
        ("worldSeed", "world seed in use", True),
        ("worldLayers", "flat-world layer string", True),
        ("pinnedWorldTime", "pinned world time", True),
        ("guiHidden", "GUI hidden at window open", False),
        ("lightmapFlickerPinned", "torch flicker pinned to zero", True),
        ("cameraPoseX", "camera pose x requested", True),
        ("cameraPoseY", "camera pose y requested", True),
        ("cameraPoseZ", "camera pose z requested", True),
        ("cameraPoseYaw", "camera pose yaw requested", True),
        ("cameraPosePitch", "camera pose pitch requested", True),
        ("cameraX", "camera x at window open", True),
        ("cameraY", "camera y at window open", True),
        ("cameraZ", "camera z at window open", True),
        ("cameraYaw", "camera yaw at window open", True),
        ("cameraPitch", "camera pitch at window open", True),
    ]
    per_config = {}
    for run in runs:
        per_config.setdefault(run["config"], []).append(run.get("fixture") or {})
    checks = []
    for name, label, must_match in fields:
        values = {}
        measured = 0
        total = 0
        for key, records in per_config.items():
            seen = sorted({json.dumps(record.get(name)) for record in records})
            values[key] = seen
            for record in records:
                total += 1
                if record.get(name) is not None:
                    measured += 1
        distinct = {value for seen in values.values() for value in seen}
        # A control that was never measured is not a control that agrees. `json.dumps(None)` is the string "null", so
        # a field missing from every run produced the single distinct value {"null"} and was reported as consistent -
        # which is how this audit could say "all required controls are equal" while every required control was absent.
        # Reproduced by scripts/cpu-contract/bench_controls_check.py before this change.
        missing = must_match and measured == 0
        checks.append({
            "field": name,
            "label": label,
            "mustMatch": must_match,
            "valuesPerConfig": values,
            "distinctValues": sorted(distinct),
            "measuredRuns": measured,
            "totalRuns": total,
            "missing": missing,
            "consistent": (False if missing else (len(distinct) <= 1)) if must_match else None,
        })
    mismatch = [check["field"] for check in checks if check["mustMatch"] and not check["consistent"]]
    missing_fields = [check["field"] for check in checks if check["missing"]]
    return {
        "controls": checks,
        "consistent": not mismatch,
        "mismatchedFields": mismatch,
        "missingFields": missing_fields,
        "note": ("these required controls were never measured, so nothing is known about them: "
                 + ", ".join(missing_fields)) if missing_fields else
                ("every control that must be equal is equal across all runs"
                 if not mismatch else
                 "these controls differ across runs: " + ", ".join(mismatch)),
        "camera": {key: sorted({"%s,%s,%s,%s,%s" % (record.get("cameraX"), record.get("cameraY"),
                                                    record.get("cameraZ"), record.get("cameraYaw"),
                                                    record.get("cameraPitch"))
                                for record in records})
                   for key, records in per_config.items()},
        "frameCounts": {key: sorted({record.get("measuredFrames") for record in records})
                        for key, records in per_config.items()},
        "warmup": {key: sorted({(record.get("warmupTicks"), record.get("warmupFrames"))
                                for record in records}) for key, records in per_config.items()},
        "requestedFrames": args.frames,
        "requestedWarmupTicks": args.warmup_ticks,
        "requestedWarmupFrames": args.warmup_frames,
    }


# --------------------------------------------------------------------------- markdown

def shorten(text, limit=180):
    """One debug string per row is a table cell, not a data dump: the full text is in the run's own log."""
    text = str(text)
    return text if len(text) <= limit else text[:limit] + "... [%d chars total]" % len(text)


def fmt(value, digits=2, suffix=""):
    if value is None or (isinstance(value, float) and (math.isnan(value) or math.isinf(value))):
        return "n/a"
    if isinstance(value, float):
        return ("%%.%df%s" % (digits, suffix)) % value
    return "%s%s" % (value, suffix)


def render_markdown(results, args):
    """The generated half of the write-up: every number and control this run recorded."""
    out = []
    add = out.append
    host = results["host"]
    measured = [run for run in results["runs"] if not run.get("skipped")]
    add("# Benchmark evidence: Celtium vs pinned Celeritas %s" % results["stamp"])
    add("")
    add("Generated by `scripts/benchmark_celtium.py` (this file is regenerated; the interpretation lives "
        "in the hand-written document beside it). No claim of a speed-up is made by this file.")
    if results.get("mergeWarning"):
        add("")
        add("> **Merged document:** " + results["mergeWarning"])
    add("")
    add("## Host and build")
    add("")
    add("| item | value |")
    add("| --- | --- |")
    add("| generated at | %s |" % time.strftime("%Y-%m-%dT%H:%M:%S"))
    add("| git HEAD | `%s` |" % host.get("gitHead"))
    add("| git dirty | %s |" % host.get("gitDirty"))
    add("| platform | %s |" % host.get("platform"))
    add("| CPUs | %s |" % host.get("cpuCount"))
    add("| load average at start (1/5/15) | %s |"
        % " ".join(fmt(value) for value in host.get("loadAverageAtStart") or []))
    add("| render node | `%s` |" % results["method"]["renderNode"])
    add("| fixture jar sha256 | `%s` |" % (results["fixtureBuild"].get("sha256") or "")[:16])
    add("")

    add("## Method and controls")
    add("")
    method = results["method"]
    add("| control | value |")
    add("| --- | --- |")
    add("| measured frames per run | %s (warm-up excluded) |" % method["frames"])
    add("| warm-up | %s world ticks AND %s rendered frames, both required |"
        % (method["warmupTicks"], method["warmupFrames"]))
    add("| frame cap (GameSettings.limitFramerate) | %s fps, requested and read back at both ends |"
        % method["framelimit"])
    add("| vsync | %s in every configuration, read back at both ends |"
        % ("ON" if method["vsync"] else "OFF"))
    add("| render distance | %s chunks |" % method["renderDistance"])
    add("| resolution | %s |" % method["resolution"])
    add("| world | %s, seed %s, layers `%s` |"
        % (method["worldType"], method["seed"], method["worldLayers"]))
    add("| world time / weather | pinned to %s, rain and thunder off |" % method["worldTime"])
    add("| camera | fixed pose x=%s y=%s z=%s yaw=%s pitch=%s, held every tick with flying pinned and "
        "teleported on the server once |" % tuple(method["cameraPose"]))
    add("| chunk phase | %s blocks placed, then %s post-placement frames measured |"
        % (method["chunkBlocks"], method["chunkCompareFrames"]))
    add("| percentile rule | %s |" % method["percentileRule"])
    add("| CPU time source | %s |" % method["cpuTimeSource"])
    add("| memory source | %s |" % method["rssSource"])
    add("| load source | %s |" % method["loadSource"])
    add("| run order | %s |" % method["runOrder"])
    add("| jar pinning | %s |" % method["jarPinning"])
    add("")
    add("### Configurations")
    add("")
    add("| key | jar | jar sha256 (pinned, measured) | extra JVM args | what |")
    add("| --- | --- | --- | --- | --- |")
    for key, cfg in results["method"]["configurations"].items():
        add("| `%s` | `%s` | `%s` | `%s` | %s |"
            % (key, cfg["jar"], cfg["jarSha256"], " ".join(cfg["extraJvmArgs"]) or "(none)", cfg["what"]))
    add("")

    audit = results["controls"]["audit"]
    add("### Control audit (read back from inside the game at both window ends)")
    add("")
    add("| control | " + " | ".join("`%s`" % key for key in results["method"]["configurations"]) + " | equal |")
    add("| --- | " + " | ".join("---" for _ in results["method"]["configurations"]) + " | --- |")
    for check in audit["controls"]:
        cells = []
        for key in results["method"]["configurations"]:
            seen = check["valuesPerConfig"].get(key) or []
            cells.append(", ".join(seen) if seen else "n/a")
        if check.get("missing"):
            verdict = "**not measured**"
        else:
            verdict = ("yes" if check["consistent"] else "**NO**") if check["mustMatch"] else "n/a"
        add("| %s | %s | %s |" % (check["label"], " | ".join(cells), verdict))
    add("")
    add("Camera per configuration: " + "; ".join(
        "`%s` = %s" % (key, ", ".join(value for value in values) or "n/a")
        for key, values in audit["camera"].items()))
    add("")
    add("Measured frame counts per configuration: " + "; ".join(
        "`%s` = %s" % (key, values) for key, values in audit["frameCounts"].items()))
    add("")
    missing = audit.get("missingFields") or []
    add("**Verdict: %s.** %s" % (
        "ALL REQUIRED CONTROLS ARE EQUAL" if audit["consistent"] and not missing
        else ("REQUIRED CONTROLS WERE NOT MEASURED" if missing else "CONTROLS DIFFER"),
        audit["note"]))
    skipped = [run for run in results["runs"] if run.get("skipped")]
    if skipped:
        add("")
        add("Runs skipped because another game instance held the machine: "
            + ", ".join("run %s `%s`" % (run.get("ordinal"), run["config"]) for run in skipped))
    add("")

    load = results["controls"]["load"]
    add("### Machine load at the time of each measurement")
    add("")
    add("| run | config | window-start load (1 min) | window-end load | external load band during the run | load outlier |")
    add("| --- | --- | --- | --- | --- | --- |")
    for run in measured:
        if run.get("skipped"):
            continue
        band = run.get("loadAverageDuringRunOneMinMax")
        add("| %s | `%s` | %s | %s | %s | %s |"
            % (run.get("ordinal"), run["config"], fmt(run.get("windowLoadStart")),
               fmt(run.get("windowLoadEnd")),
               ("%.2f..%.2f" % (band[0], band[1])) if band else "n/a",
               "**yes**" if run.get("loadOutlier") else "no"))
    add("")
    add("Load gate (limit: window-start loads within %.2f of each other): %s"
        % (load["limit"], load["reason"]))
    if load.get("outliers"):
        for outlier in load["outliers"]:
            add("- outlier: `%s`%s run %s at load %.2f (band low %.2f)"
                % (outlier["config"], (" " + outlier["tag"]) if outlier["tag"] else "",
                   outlier["ordinal"], outlier["loadStart"], outlier["bandLow"]))
    add("")

    add("## Steady state")
    add("")
    add("| run | config | frames | wall s | p50 ms | p95 ms | p99 ms | max ms | mean ms | fps (p50) | fps (1% low) | CPU s | CPU % of one core | peak RSS MB | queue start/end/max | status | terrain backend |")
    add("| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |")
    for row in results["table"]:
        add("| %(ordinal)s | `%(config)s`%(tag)s | %(samples)s | %(windowSeconds)s | %(p50Ms)s | %(p95Ms)s | "
            "%(p99Ms)s | %(maxMs)s | %(meanMs)s | %(fpsFromP50)s | %(fpsOnePercentLow)s | %(cpuSeconds)s | "
            "%(cpuPercent)s | %(peakRssMb)s | %(queue)s | %(status)s | %(terrain)s |"
            % {"ordinal": row["ordinal"], "config": row["config"],
               "tag": (" " + row["tag"]) if row["tag"] else "",
               "samples": row["samples"], "windowSeconds": fmt(row["windowSeconds"], 2),
               "p50Ms": fmt(row["p50Ms"]), "p95Ms": fmt(row["p95Ms"]), "p99Ms": fmt(row["p99Ms"]),
               "maxMs": fmt(row["maxMs"]), "meanMs": fmt(row["meanMs"]),
               "fpsFromP50": fmt(row["fpsFromP50"], 1), "fpsOnePercentLow": fmt(row["fpsOnePercentLow"], 1),
               "cpuSeconds": fmt(row["cpuSeconds"], 2), "cpuPercent": fmt(row["cpuPercent"], 1),
               "peakRssMb": fmt(row["peakRssMb"], 0),
               "queue": "%s/%s/%s" % (row.get("queueStart"), row.get("queueEnd"), row.get("queueMax")),
               "status": row["status"], "terrain": row["terrainBackend"] or "n/a"})
    add("")
    add("Frame-cap / presentation-floor verdict per run:")
    add("")
    for run in measured:
        verdict = cap_verdict(run)
        add("- run %s `%s`: **%s** - %s" % (run.get("ordinal"), run["config"], verdict["verdict"],
                                            verdict["detail"]))
    add("")

    add("## Selected GPU (from the renderer's own line)")
    add("")
    add("| run | config | `CELTIUM_NATIVE_DEVICE` | launcher-recorded device | fixture GL renderer string | GL version |")
    add("| --- | --- | --- | --- | --- | --- |")
    for run in measured:
        device = run.get("nativeDevice") or {}
        launcher = run.get("nativeDeviceFromLauncher") or {}
        fixture = run.get("fixture") or {}
        if device.get("found"):
            cell = 'name="%s" uuid=%s matchedOpenGl=%s' % (device.get("name"), device.get("uuid"),
                                                           device.get("matchedOpenGl"))
        else:
            cell = "**line absent**"
        launcher_cell = ("%s / %s" % (launcher.get("name"), launcher.get("uuid"))
                         if launcher.get("name") else "n/a")
        add("| %s | `%s` | %s | %s | %s | %s |"
            % (run.get("ordinal"), run["config"], cell, launcher_cell,
               fixture.get("glRendererString") or "n/a", fixture.get("glVersionString") or "n/a"))
    add("")
    caps = [run for run in measured if (run.get("capabilitiesFile") or {}).get("vulkanDevices")]
    for run in caps[:1]:
        add("`%s` enumerated by the renderer's capability command:" % run["capabilitiesFile"]["path"])
        add("")
        add("| device | type | api | vendor/device id | uuid | driver |")
        add("| --- | --- | --- | --- | --- | --- |")
        for device in run["capabilitiesFile"]["vulkanDevices"]:
            add("| %s | %s | %s | %s/%s | %s | %s |"
                % (device.get("name"), device.get("deviceType"), device.get("apiVersion"),
                   device.get("vendorId"), device.get("deviceId"), device.get("deviceUUID"),
                   device.get("driverVersion")))
        add("")
        add("Effective terrain backend reported by the capability command: `%s`; matched Vulkan device "
            "uuid: `%s`." % (run["capabilitiesFile"].get("effectiveTerrainBackend"),
                             run["capabilitiesFile"].get("matchedVulkanDeviceUuid")))
        add("")

    add("## GPU time (renderer's own timestamp queries, where published)")
    add("")
    add("| run | config | pass | sections | GPU ms | raw |")
    add("| --- | --- | --- | --- | --- | --- |")
    any_pass = False
    for run in measured:
        for name, entry in sorted((run.get("gpuPassTimesMs") or {}).items()):
            any_pass = True
            add("| %s | `%s` | %s | %s | %s | %s |"
                % (run.get("ordinal"), run["config"], name, entry.get("sections"),
                   fmt(entry.get("ms"), 3), entry.get("raw")))
    if not any_pass:
        add("| - | - | no pass published a GPU time in any configuration | - | - | - |")
    add("")
    add("Whole-frame GPU time is not published by either configuration; these are per-terrain-pass "
        "timestamp-query results only, and the OpenGL reference publishes none at all.")
    add("")

    add("## GPU utilisation and VRAM (driver counters, whole GPU)")
    add("")
    if not any((run.get("gpuLoad") or {}).get("sampleCount") for run in measured):
        add("The render node's driver published no `gpu_busy_percent`/`mem_info_vram_used` counters, so "
            "there is no GPU-side measurement in this run.")
    else:
        add("Sampled every 0.2 s from outside the game process, sliced to the fixture's own measured "
            "window (the `[CeltiumBench] window open` / `window close` console marks). This is a "
            "whole-GPU utilisation counter: any other process using this GPU, and any other agent's "
            "game on it, is inside these numbers for every configuration alike.")
        add("")
        add("| run | config | window samples | GPU busy % min/median/p95/max | VRAM used peak MiB | GTT used peak MiB | whole-run samples |")
        add("| --- | --- | --- | --- | --- | --- | --- |")
        for run in measured:
            gpu = run.get("gpuLoad") or {}
            window = gpu.get("measuredWindow") or {}
            busy = window.get("busyPercent") or {}
            vram = (window.get("vramUsedBytes") or {}).get("max")
            gtt = (window.get("gttUsedBytes") or {}).get("max")
            add("| %s | `%s` | %s | %s/%s/%s/%s | %s | %s | %s |"
                % (run.get("ordinal"), run["config"], window.get("sampleCount"),
                   fmt(busy.get("min"), 1), fmt(busy.get("median"), 1), fmt(busy.get("p95"), 1),
                   fmt(busy.get("max"), 1),
                   fmt((vram / (1024.0 * 1024.0)) if vram is not None else None, 0),
                   fmt((gtt / (1024.0 * 1024.0)) if gtt is not None else None, 0),
                   gpu.get("sampleCount")))
        add("")
        by_config = {}
        for run in comparable_runs(measured):
            by_config.setdefault(run["config"], []).append(run)
        add("Medians over the runs that passed the load gate:")
        add("")
        add("| config | runs | GPU busy % (median of window medians) | VRAM used peak MiB |")
        add("| --- | --- | --- | --- |")
        for key, config_runs in by_config.items():
            busy = [(run.get("gpuLoad") or {}).get("measuredWindow", {}).get("busyPercent", {}).get("median")
                    for run in config_runs]
            busy = [value for value in busy if value is not None]
            vram = [(run.get("gpuLoad") or {}).get("measuredWindow", {}).get("vramUsedBytes", {}).get("max")
                    for run in config_runs]
            vram = [value for value in vram if value is not None]
            add("| `%s` | %s | %s | %s |"
                % (key, len(config_runs), fmt(statistics.median(busy) if busy else None, 1),
                   fmt((max(vram) / (1024.0 * 1024.0)) if vram else None, 0)))
    add("")

    add("## Diagnostic output (a cost one side pays and the other does not)")
    add("")
    add("The Celtium development build prints its own counters (`CELTIUM_*` lines) to stdout from inside "
        "the render path; the pinned upstream reference prints none. The volume is measured so the "
        "confound is visible in the numbers rather than only in prose.")
    add("")
    add("| run | config | console lines | `CELTIUM_` lines | latest.log lines | latest.log MiB |")
    add("| --- | --- | --- | --- | --- | --- |")
    for run in measured:
        logging = run.get("logging") or {}
        size = logging.get("latestLogBytes")
        add("| %s | `%s` | %s | %s | %s | %s |"
            % (run.get("ordinal"), run["config"], logging.get("consoleLines"),
               logging.get("celitumDiagnosticLines"), logging.get("latestLogLines"),
               fmt((size / (1024.0 * 1024.0)) if size is not None else None, 1)))
    add("")

    add("## Chunk updates")
    add("")
    add("| run | config | blocks placed (failures) | placement ms | phase s | frames after placement | queue empty at frame | queue counters readable | criterion |")
    add("| --- | --- | --- | --- | --- | --- | --- | --- | --- |")
    for run in measured:
        chunk = run.get("chunkUpdate") or {}
        add("| %s | `%s` | %s (%s) | %s | %s | %s | %s | %s | %s |"
            % (run.get("ordinal"), run["config"], chunk.get("blocksPlaced"), chunk.get("placeFailures"),
               chunk.get("placeMillis"), fmt(chunk.get("phaseSeconds")),
               chunk.get("framesAfterPlacement"), chunk.get("queueEmptyAtFrame"),
               chunk.get("queueCountersReadable"), chunk.get("drainCriterion")))
    add("")
    add("Frame times of the first %s frames after the last placement (the rebuild burst):"
        % results["method"]["chunkCompareFrames"])
    add("")
    add("| run | config | samples | p50 ms | p95 ms | p99 ms | max ms | mean ms |")
    add("| --- | --- | --- | --- | --- | --- | --- | --- |")
    for run in measured:
        stats = (run.get("chunkUpdate") or {}).get("postPlacementFrameStatistics") or {}
        add("| %s | `%s` | %s | %s | %s | %s | %s | %s |"
            % (run.get("ordinal"), run["config"], stats.get("count"), fmt(stats.get("p50Ms")),
               fmt(stats.get("p95Ms")), fmt(stats.get("p99Ms")), fmt(stats.get("maxMs")),
               fmt(stats.get("meanMs"))))
    add("")
    add("Renderer's own `Chunk Queues` line (upload + three rebuild priorities):")
    add("")
    add("| run | config | at window start | at window end | max during window |")
    add("| --- | --- | --- | --- | --- |")
    for run in measured:
        chunk = run.get("chunkUpdate") or {}
        add("| %s | `%s` | %s | %s | %s |"
            % (run.get("ordinal"), run["config"], chunk.get("queueDepthAtWindowStart"),
               chunk.get("queueDepthAtWindowEnd"), chunk.get("queueMaxDuringWindow")))
    add("")
    add("Renderer's own device-memory counters (`G:` geometry, `I:` index) at the end of the window:")
    add("")
    add("| run | config | geometry used/allocated MiB | index used/allocated MiB | buffers | transfer queue |")
    add("| --- | --- | --- | --- | --- | --- |")
    for run in measured:
        memory = ((run.get("rendererCountersAtWindowEnd") or {}).get("deviceMemory") or {})
        transfer = (run.get("rendererCountersAtWindowEnd") or {}).get("transferQueue")
        add("| %s | `%s` | %s/%s | %s/%s | %s | %s |"
            % (run.get("ordinal"), run["config"], memory.get("geometryUsedMib"),
               memory.get("geometryAllocatedMib"), memory.get("indexUsedMib"),
               memory.get("indexAllocatedMib"), memory.get("buffers"),
               shorten(transfer) if transfer else "n/a"))
    add("")

    add("## Deltas")
    add("")
    if results["deltas"]["error"]:
        add("No deltas: %s" % results["deltas"]["error"])
    else:
        add("Baseline: `%s`. Medians over the runs that passed the load gate; a positive "
            "`candidate - baseline` is worse for the candidate on 'lower' metrics." % results["deltas"]["baseline"])
        add("")
        add("| config | metric | candidate median | baseline median | candidate - baseline | ratio | better |")
        add("| --- | --- | --- | --- | --- | --- | --- |")
        for delta in results["deltas"]["rows"]:
            for metric in delta.get("metrics", []):
                add("| `%s` | %s | %s | %s | %s | %s | %s |"
                    % (delta["config"], metric["metric"], fmt(metric["candidateMedian"], 3),
                       fmt(metric["baselineMedian"], 3), fmt(metric["candidateMinusBaseline"], 3),
                       fmt(metric["ratio"], 3), metric["better"]))
    add("")

    add("## Limits")
    add("")
    for limit in results["limits"]:
        add("- %s" % limit)
    add("")
    return "\n".join(out) + "\n"


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
    parser.add_argument("--configs", default=DEFAULT_CONFIGS,
                        help="comma-separated keys from %s" % ",".join(CONFIGS))
    parser.add_argument("--baseline", default=DEFAULT_BASELINE,
                        help="configuration the deltas are computed against")
    parser.add_argument("--rounds", type=int, default=2,
                        help="rounds over all configurations; the order is reversed in every other round")
    parser.add_argument("--frames", type=int, default=600, help="measured frames per run")
    parser.add_argument("--warmup-ticks", type=int, default=600)
    parser.add_argument("--warmup-frames", type=int, default=300)
    parser.add_argument("--framelimit", type=int, default=260,
                        help="GameSettings.limitFramerate; 260 is the highest the 1.12.2 option accepts")
    parser.add_argument("--vsync", action="store_true", help="request vsync (default: off, as both sides use)")
    parser.add_argument("--render-distance", type=int, default=8)
    parser.add_argument("--world-layers", default=DEFAULT_WORLD_LAYERS,
                        help="flat-world layer string; the default is static (no lava, fire, water or "
                             "leaves) so the chunk queue can settle and the visible scene cannot drift")
    parser.add_argument("--camera", default=DEFAULT_CAMERA,
                        help="fixed camera pose x,y,z,yaw,pitch, held on the client every tick and "
                             "teleported on the server once")
    parser.add_argument("--chunk-blocks", type=int, default=64)
    parser.add_argument("--chunk-window-frames", type=int, default=120,
                        help="post-placement frames measured in the chunk phase, identically per configuration")
    parser.add_argument("--no-chunk-phase", dest="chunk_phase", action="store_false", default=True)
    parser.add_argument("--fixture-timeout", type=int, default=600,
                        help="seconds after which the fixture writes a partial report and exits")
    parser.add_argument("--duration", type=int, default=1200,
                        help="launcher timeout in seconds; a timeout is not a result")
    parser.add_argument("--idle-timeout", type=int, default=1800)
    parser.add_argument("--load-delta-limit", type=float, default=1.0,
                        help="window-start 1-minute load averages within this of each other count as the "
                             "same conditions; runs above (lowest + limit) are excluded from the deltas")
    parser.add_argument("--auto-rerun", type=int, default=1,
                        help="how many load-outlier runs may be repeated once, sequentially")
    parser.add_argument("--out", type=Path, help="results JSON (default .celtium-work/bench/results-<stamp>.json)")
    parser.add_argument("--markdown", type=Path, help="generated evidence markdown path")
    parser.add_argument("--only", help="run only this configuration key (still respects the idle gate)")
    parser.add_argument("--from-results", type=Path,
                        help="re-render the evidence markdown from an existing results JSON instead of "
                             "running anything")
    parser.add_argument("--merge-out", type=Path, help="where a --merge-results document is written")
    parser.add_argument("--merge-results",
                        help="comma-separated results JSONs to combine into one evidence document; the "
                             "runs keep their own jar hashes and window loads, the deltas and the load "
                             "gate are recomputed over the union")
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
    if args.baseline not in CONFIGS:
        parser.error("unknown baseline %r" % args.baseline)
    camera = [part.strip() for part in args.camera.split(",")]
    if len(camera) != 5:
        parser.error("--camera takes x,y,z,yaw,pitch")
    try:
        args.camera_pose = [float(part) for part in camera]
    except ValueError:
        parser.error("--camera values must be numbers")

    if args.merge_results:
        sources = [Path(part.strip()) for part in args.merge_results.split(",") if part.strip()]
        merged_runs = []
        reference_method = None
        hashes_by_config = {}
        for source in sources:
            document = json.loads(source.read_text())
            for key, cfg in (document["method"].get("configurations") or {}).items():
                hashes_by_config.setdefault(key, {})[cfg.get("jarSha256")] = source.name
            if reference_method is None:
                reference_method = document["method"]
                merged_host = document["host"]
                merged_fixture = document["fixtureBuild"]
            for run in document["runs"]:
                run = dict(run)
                run["sourceStamp"] = document.get("stamp")
                run["sourceResults"] = str(source)
                merged_runs.append(run)
        for index, run in enumerate(merged_runs, start=1):
            run["sourceOrdinal"] = run.get("ordinal")
            run["ordinal"] = index
        mixed = {key: sorted(hashes) for key, hashes in hashes_by_config.items() if len(hashes) > 1}
        merge_warning = None
        if mixed:
            merge_warning = ("these configurations were measured with more than one jar in this merged "
                             "document, so their rows are not a single-build comparison: "
                             + "; ".join("%s = %s" % (key, ", ".join(hashes))
                                         for key, hashes in sorted(mixed.items())))
            print("WARNING: " + merge_warning)
        context = load_context(merged_runs, args)
        deltas, baseline_key, delta_error = pair_deltas(merged_runs, args)
        audit = controls_audit(merged_runs, args)
        merged = {
            "schemaVersion": 2,
            "scope": "merged evidence from " + ", ".join(str(source) for source in sources),
            "stamp": "merged-%s-of-%d-documents" % (time.strftime("%Y%m%d-%H%M%S"), len(sources)),
            "host": merged_host,
            "method": reference_method,
            "fixtureBuild": merged_fixture,
            "launcher": json.loads(sources[0].read_text()).get("launcher"),
            "runs": merged_runs,
            "table": comparison_rows(merged_runs),
            "deltas": {"baseline": baseline_key, "rows": deltas, "error": delta_error,
                       "loadBalanced": context.get("balanced"), "loadReason": context.get("reason")},
            "controls": {"audit": audit, "load": context},
            "limits": json.loads(sources[0].read_text()).get("limits", []),
            "mergeWarning": merge_warning,
        }
        out_json = args.merge_out or (WORK / ("results-merged-%s.json" % time.strftime("%Y%m%d-%H%M%S")))
        out_json.parent.mkdir(parents=True, exist_ok=True)
        out_json.write_text(json.dumps(merged, indent=2) + "\n", encoding="utf-8")
        markdown_path = args.markdown or (ROOT / "docs" / ("performance-%s-evidence.md"
                                                           % time.strftime("%Y-%m-%d")))
        markdown_path.parent.mkdir(parents=True, exist_ok=True)
        markdown_path.write_text(render_markdown(merged, args), encoding="utf-8")
        print("merged %d runs from %d documents: %s" % (len(merged_runs), len(sources), out_json))
        print("evidence: %s" % markdown_path)
        print("load gate: %s" % context.get("reason"))
        print("controls: %s" % audit["note"])
        return 0

    if args.from_results:
        results = json.loads(Path(args.from_results).read_text())
        markdown_path = args.markdown or (ROOT / "docs" / ("performance-%s-evidence.md"
                                                           % time.strftime("%Y-%m-%d")))
        markdown_path.parent.mkdir(parents=True, exist_ok=True)
        markdown_path.write_text(render_markdown(results, args), encoding="utf-8")
        print("evidence re-rendered from %s: %s" % (args.from_results, markdown_path))
        return 0

    bench_jar, fixture_build = compile_fixture(args.java, args.javac)
    pinned = {key: pin_jar(key, args) for key in keys}
    if not args.dry_run:
        seen = {}
        for key, pin in pinned.items():
            if pin["pinnedSha256"] in seen:
                print("%s and %s are the same bytes (%s); their difference is the JVM arguments only"
                      % (seen[pin["pinnedSha256"]], key, pin["pinnedSha256"][:16]))
            seen.setdefault(pin["pinnedSha256"], key)

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
        "kernel": platform.release(),
    }
    print("host: %s, %s cores, load %s" % (host["platform"], host["cpuCount"],
                                           " ".join("%.2f" % value for value in host["loadAverageAtStart"])))
    print("git HEAD %s (dirty=%s)" % (host["gitHead"], host["gitDirty"]))
    print("fixture jar %s" % bench_jar)

    runs = []
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
        record = run_configuration(key, ordinal, args, bench_jar, out_root, pinned)
        runs.append(record)
        stats = record.get("frameStatistics") or {}
        print("    p50=%.2f ms p95=%.2f p99=%.2f max=%.2f n=%s status=%s load=%s"
              % (stats.get("p50Ms", float("nan")), stats.get("p95Ms", float("nan")),
                 stats.get("p99Ms", float("nan")), stats.get("maxMs", float("nan")),
                 stats.get("count"), (record.get("fixture") or {}).get("status"),
                 record.get("windowLoadStart")), flush=True)

    # The load gate: a materially loaded side is repeated once rather than compared as if it were equal.
    reruns = 0
    while args.auto_rerun > 0 and reruns < args.auto_rerun and not args.dry_run:
        context = load_context(runs, args)
        if not context.get("outliers"):
            break
        outlier = context["outliers"][0]
        reruns += 1
        ordinal = len(runs) + 1
        print("[rerun %d/%d] %s: window load %s is above the band (lowest %s + %.2f)"
              % (reruns, args.auto_rerun, outlier["config"], fmt(outlier["loadStart"]),
                 fmt(outlier["bandLow"]), args.load_delta_limit), flush=True)
        if not wait_for_idle(args.idle_timeout):
            print("    skipped: another game instance never exited")
            break
        record = run_configuration(outlier["config"], ordinal, args, bench_jar, out_root, pinned,
                                   tag="rerun%d" % reruns)
        runs.append(record)
        stats = record.get("frameStatistics") or {}
        print("    p50=%.2f ms n=%s load=%s" % (stats.get("p50Ms", float("nan")), stats.get("count"),
                                                record.get("windowLoadStart")), flush=True)

    context = load_context(runs, args)
    deltas, baseline_key, delta_error = pair_deltas(runs, args)
    audit = controls_audit(runs, args)
    markdown_path = args.markdown or (ROOT / "docs" / ("performance-%s-evidence.md"
                                                       % time.strftime("%Y-%m-%d")))
    classpath, classpath_source = newest_launch_classpath()
    results = {
        "schemaVersion": 2,
        "scope": ("steady-state frame timing, process CPU/RSS, renderer-published chunk-queue and "
                  "device-memory counters, chunk-rebuild burst cost and selected GPU for the Celtium "
                  "GL-less and GL-bridge configurations and the pinned Celeritas 989ebf2e reference, "
                  "under identical settings; no speed-up is asserted by this file"),
        "stamp": args.stamp,
        "host": host,
        "method": {
            "configurations": {
                key: {"what": CONFIGS[key]["what"], "jar": pinned[key]["pinnedPath"],
                      "jarSource": pinned[key]["sourcePath"],
                      "jarSha256": pinned[key]["pinnedSha256"],
                      "jarSourceMtimeIso": pinned[key]["sourceMtimeIso"],
                      "extraJvmArgs": list(CONFIGS[key]["jvmArgs"])}
                for key in keys},
            "frames": args.frames,
            "warmupTicks": args.warmup_ticks,
            "warmupFrames": args.warmup_frames,
            "framelimit": args.framelimit,
            "vsync": args.vsync,
            "renderDistance": args.render_distance,
            "chunkPhase": args.chunk_phase,
            "chunkBlocks": args.chunk_blocks,
            "chunkCompareFrames": args.chunk_window_frames,
            "seed": 12345678,
            "worldType": "FLAT",
            "worldLayers": args.world_layers,
            "cameraPose": args.camera_pose,
            "cameraPoseDescription": "held on the client every tick with flying pinned and motion zeroed, "
                                     "and teleported once on the server at tick 25 through "
                                     "EntityPlayerMP.setPositionAndUpdate, so the server's move correction "
                                     "cannot restore the fuzzed spawn position",
            "worldTime": 6000,
            "resolution": "1280x720",
            "renderNode": str(args.render_node),
            "headlessCompositorRefreshHz": 60,
            "percentileRule": "nearest rank, ceil(f*n)-1, the same rule the validation fixture uses",
            "warmupRule": "measured frames start only after warmupTicks world ticks AND warmupFrames "
                          "rendered frames; the first eligible frame only opens the window and is not "
                          "sampled",
            "cpuTimeSource": "in-process /proc/self/stat utime+stime, converted with CLK_TCK",
            "rssSource": "in-process /proc/self/status VmHWM",
            "loadSource": "/proc/loadavg, sampled inside the process at both ends of the window",
            "runOrder": " -> ".join(order),
            "jarPinning": "each configuration's jar is copied to .celtium-work/bench/jars/"
                          "<sha256[:16]>-<name> before the first run and the copy is what runs, so a "
                          "concurrent rebuild cannot change the measured bytes",
            "loadGate": "runs whose window-start 1-minute load average exceeds (lowest + %.2f) are "
                        "marked outliers, excluded from the deltas and re-run once when --auto-rerun "
                        "allows it" % args.load_delta_limit,
        },
        "fixtureBuild": fixture_build,
        "launcher": {
            "script": "scripts/prism_validation.py",
            "isolation": "fresh game directory under .celtium-work/bench/runs; mods copied from the "
                         "Prism instance's mods (Fugue, scalar) plus the pinned mod jar and the fixture "
                         "jar; the instance's own minecraft/mods is never written to",
            "launcherClasspathSource": (classpath_source.as_posix() if classpath_source else None),
            "oneGameInstanceAtATime": "pgrep -x java + /proc/<pid>/cmdline contains foundation.boot.Foundation",
        },
        "runs": runs,
        "table": comparison_rows(runs),
        "deltas": {"baseline": baseline_key, "rows": deltas, "error": delta_error,
                   "loadBalanced": context.get("balanced"), "loadReason": context.get("reason")},
        "controls": {"audit": audit, "load": context},
        "limits": [
            "Whole-frame GPU time is not available: the Vulkan terrain backend publishes per-pass "
            "timestamp-query times, the pinned OpenGL reference publishes none, and nothing here "
            "measures the swap/present GPU cost of either configuration. 'GPU time' in this document "
            "therefore means the renderer's own per-terrain-pass timestamps only.",
            "The CPU time is the whole process (render thread, client thread, integrated-server thread "
            "and GC together), not the render thread alone.",
            "The headless compositor's output is 1280x720@60Hz; the achieved frame times, not the "
            "vsync flag alone, are the evidence about whether either cap bound the run.",
            "This machine is shared with other agents. Every run records the load average at both ends "
            "of its measured window and the external sampler's band; runs outside the load gate are "
            "marked and excluded from the deltas, and the write-up states whether the compared runs "
            "were balanced.",
            "The chunk phase measures the frame times of a fixed post-placement window and, where the "
            "renderer publishes queue counters, the frame at which the queue first read zero. It is "
            "the cost of the same 64 block placements in the same flat world, not a general "
            "chunk-generation benchmark.",
            "The pinned upstream jar is one commit (989ebf2e) of one project built from the "
            "repository's own baseline workspace; nothing here says anything about other versions.",
            "The Celtium build's own diagnostic logging runs inside the measured window (see the "
            "diagnostic-output table); the pinned reference has none. That cost is inside every "
            "Celtium frame and is one of the reasons a difference here is not attributable to the "
            "renderer alone.",
            "The measured window in this scene is not an idle scene: the flat world contains lava and "
            "fire, which keep rebuilding chunks. The renderer's own queue counters are reported at both "
            "ends of every window so the reader can see the queue depth the frame times were taken at; "
            "'steady state' here means the window opened after the warm-up with a stable frame-time "
            "distribution, not that the chunk queue was empty.",
        ],
    }
    out_json.parent.mkdir(parents=True, exist_ok=True)
    out_json.write_text(json.dumps(results, indent=2) + "\n", encoding="utf-8")
    markdown_path.parent.mkdir(parents=True, exist_ok=True)
    markdown_path.write_text(render_markdown(results, args), encoding="utf-8")
    print("results: %s" % out_json)
    print("evidence: %s" % markdown_path)
    print("load gate: %s" % context.get("reason"))
    print("controls: %s" % audit["note"])
    return 0


if __name__ == "__main__":
    sys.exit(main())
