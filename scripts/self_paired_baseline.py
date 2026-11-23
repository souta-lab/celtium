#!/usr/bin/env python3
"""Self-paired baseline harness - item 693, the measurement remainder of item 658 Stage 0.

WHAT THIS IS
    Item 658 section 4 fixed the self-paired yardstick for the Celeritas exit: a pair is two
    measurements of jar A and jar A' run alternately under identical machine/session conditions,
    one judgment unit is at least 3 pairs, and the baseline those stages will be judged against -
    current main against itself, A == A' - had never been measured (658 section 4.3). Item 693 is
    that measurement. This harness executes it reproducibly; it does not run any leg by itself -
    each run is launched through a driver command the operator supplies (see DRIVER CONTRACT),
    because the two scenes live on different machines with machine-local drivers.

REGISTERED PROTOCOL (fixed here before any run; changing it is a new pre-registration, 658 4.2)
    - Arms: A and A' are two renderer jars, each pinned by sha256 in the result. For item 693
      they are the same jar (identicalJars=true); the same harness serves later Stage pairs
      where A' is a changed build.
    - Order: pair 1 runs A then A', pair 2 runs A' then A, alternating, so order/thermal drift
      is exposed rather than always gifted to one arm (the agera-total-frame-pair record's
      fixed-order caveat is the defect this removes).
    - Primary statistic: warm-window P50 frame interval; secondary P95 (P99 recorded).
      Ratios are per pair, A'/A, all written out; the judgment takes the median.
    - Band (658 4.2, unchanged): median <= 1.00 non-degradation, 1.00-1.03 noise band,
      > 1.03 degradation. For identical jars the band is applied symmetrically
      ([1/1.03, 1.03]): the arm labels are arbitrary under alternation, so a median outside
      the symmetric band means the yardstick itself drifted more than its own noise band.
    - Quiet window: before the first run, load average, busy cores (10 s sample, gv_kpi's
      method) and other GPU tenants are recorded. A busy machine refuses the start; forcing
      the start is recorded and caps the verdict at NOT_ESTABLISHED. During each pair, CPU
      busy outside the legs is measured (gv_kpi's external-cores accounting); a pair over
      the external limit is excluded as confounded.
    - Exclusions, never re-rolls: a pair whose arms did not run on the same machine state
      (external load over limit, or warm sample counts differing by more than 1.5x - gv_kpi's
      starved screen), whose scene did not reproduce (conditions, worldContent nonAir or
      captureListedSections differing between arms), or - for identical jars - whose
      presented images differ, is excluded with the reason recorded. The harness runs
      exactly the registered pairs; if exclusions leave fewer than 3 valid pairs the
      verdict is NOT_ESTABLISHED and says so. Extra pairs need a new registration
      (--pairs, recorded in the result).
    - Presentation identity: every run must carry its capture presented image; for
      identical jars the two arms of a pair must produce byte-identical images. The
      executing lane must also view one presented image per arm with its own eyes and
      attest it (attest subcommand); a positive verdict stays *_PENDING_VISUAL until then.

SCENES (each scene keeps the window its own history established - see the note below)
    flight: agera HD Graphics 620. Generated world seed 12345678, RD12, 1280x720, camera
        y=100/yaw270/pitch20, flight from tick 400 at 20 blocks/s, warm window tick
        [400, 600), holdUntilTick 700. Harness form is kpi-1's clean one
        (presentReadback=false, frameBudget=true, presentedDumpEvery=0, no census
        instruments). Positive controls: worldContent nonAir in [5660, 5661].
    pack: RX 6400 (OptiPlex), pack-fps-1c final form. Same world and camera, flight from
        tick 200, warm window tick [400, 800), holdUntilTick 900, ComplementaryReimagined
        r5.9.3 + Demonica 0.9.1, presentedDumpEvery=0. Console gates: the run's console
        must show CELTIUM_PACK_TERRAIN_DRAWN and the shaderpack load line. Positive
        controls as flight.
    NOTE on 658 4.2's window wording: the text names one warm window, "[400, 800) frames",
    but the two scenes' established protocols disagree with a single window - every flight
    measurement since kpi-1 uses tick [400, 600), and the pack scene (pack-fps-1c/2, item
    725 P1) uses tick [400, 800). Unifying them would break comparability with both
    histories, so this harness registers each scene's own window and flags the wording
    here rather than silently resolving it.

DRIVER CONTRACT
    --driver-cmd is a shell template with placeholders {run_id}, {arm} (A or Aprime),
    {jar}, {out_dir}, {scene}. The driver must:
      - run exactly one game, in its own private run directory, with the scene's fixed
        conditions above, and restore whatever it staged (the tfp-*/run_leg_rx forms);
      - exit 0 only when the run completed;
      - leave in {out_dir}: celtium-validation.json (the fixture report),
        presented.png (the capture presented image), console.log (full console output).
    The game slot discipline is the driver's and the machine's: on gv_leg machines the
    tracked harness (prism_validation.py) owns the shared slot; on relauncher-form
    machines the operator holds the lanechat fixture resource for the scene's machine
    before starting and releases it after. This harness never starts a second game
    itself and runs the pairs strictly sequentially.

USAGE
    self-paired-baseline.sh plan  --tag T --scene flight --jar-a A.jar --jar-aprime A.jar --driver-cmd '...'
    self-paired-baseline.sh run   (same arguments; executes, writes <tag>-selfpaired.json)
    self-paired-baseline.sh attest --result <tag>-selfpaired.json --visual-a '...' --visual-aprime '...'
    self-paired-baseline.sh judge --result <tag>-selfpaired.json
    Exit codes: 0 = measurement complete and verdict positive (or pending visual),
    1 = verdict NOT_ESTABLISHED / DEGRADED / NO_VERDICT, 2 = harness error.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import math
import os
from pathlib import Path
import re
import statistics
import subprocess
import sys
import time

import gv_kpi
import gv_leg


class BaselineError(ValueError):
    pass


# --- The registered protocol constants (item 658 section 4.2) ---
BAND = 1.03                  # noise-band edge; see the module docstring
DEFAULT_PAIRS = 3            # one judgment unit is at least 3 pairs
MAX_PAIRS = 6                # more pairs are a new registration, not a quiet extension
MIN_SAMPLES = 50             # gv_kpi warm_metric's floor for a complete warm window
STARVED_RATIO = 1.5          # gv_kpi's same-machine screen on warm sample counts
LOAD_LIMIT = 2.0             # busy cores at start (gv_kpi's CELTIUM_KPI_LOAD_LIMIT default)
EXTERNAL_LIMIT = 0.5         # cores busy outside the legs within one pair (gv_kpi default)
DISK_MIN_BYTES = 5 * 1024 ** 3
RUN_TIMEOUT = 1800           # seconds per run; the slowest historical pack arm took ~430 s

SCENES = {
    "flight": {
        "window": (400, 600),
        "minWorldTicksAtEnd": 600,
        "nonAirBand": (5660, 5661),
        "consoleGates": (),
        "description": "agera HD Graphics 620, generated world seed 12345678, RD12, 1280x720, "
                       "camera y=100/yaw270/pitch20, flight from tick 400 at 20 blocks/s, "
                       "kpi-1 clean harness form (presentReadback=false, frameBudget=true, "
                       "presentedDumpEvery=0)",
    },
    "pack": {
        "window": (400, 800),
        "minWorldTicksAtEnd": 800,
        "nonAirBand": (5660, 5661),
        "consoleGates": ("CELTIUM_PACK_TERRAIN_DRAWN",
                         "Using shaderpack: ComplementaryReimagined_r5.9.3.zip"),
        "description": "RX 6400, pack-fps-1c final form: generated world seed 12345678, RD12, "
                       "1280x720, camera y=100/yaw270/pitch20, flight from tick 200 at "
                       "20 blocks/s, ComplementaryReimagined_r5.9.3 + Demonica 0.9.1, "
                       "presentedDumpEvery=0",
    },
}

# The per-run condition fields the two arms of a pair must agree on exactly: a disagreement
# means the scene did not reproduce, whatever the frame times say.
CONDITION_FIELDS = ("renderDistanceUsed", "captureWidth", "captureHeight", "pinnedWorldTime",
                    "vsyncEnabled", "framerateLimitSetting")


def now_iso() -> str:
    return time.strftime("%Y-%m-%dT%H:%M:%S%z")


def sha256_file(path: Path) -> str:
    result = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            result.update(block)
    return result.hexdigest()


def finite_ms(report: dict, key: str, problems: list[str]) -> float | None:
    value = report.get(key)
    if type(value) not in (float, int) or not math.isfinite(value) or value < 0.05:
        problems.append(f"missing/invalid {key}: {value!r}")
        return None
    return float(value)


def parse_nonair(report: dict) -> int | None:
    """worldContent is a string of the form 'samples=N nonAir=M air=K' (ValidationClient)."""
    text = report.get("worldContent")
    if not isinstance(text, str):
        return None
    match = re.search(r"nonAir=(\d+)", text)
    return int(match.group(1)) if match else None


def extract_run(run_dir: Path, scene: dict, driver_exit: int | None) -> dict:
    """Read one finished run's evidence; every failed check is named, none is waived.

    The fixture report's own `status` is deliberately NOT a gate here: on relauncher-form
    pack legs a FAILED_PRESENTED_IMAGE status appears on valid legs (a known calibration
    offset, recorded in pack-fps-2), so the frame-interval fields and the positive
    controls are the evidence, as they were for kpi-1..8 and pack-fps-1c/2.
    """
    problems: list[str] = []
    record: dict = {"problems": problems}
    if driver_exit is not None and driver_exit != 0:
        problems.append(f"driver exited {driver_exit}")
    report_path = run_dir / "celtium-validation.json"
    try:
        report = json.loads(report_path.read_text(encoding="utf-8"))
        if not isinstance(report, dict):
            raise ValueError("report is not a JSON object")
    except (OSError, ValueError) as error:
        problems.append(f"cannot read fixture report {report_path}: {error}")
        return record
    window = (report.get("frameIntervalWarmFromTick"), report.get("frameIntervalWarmToTick"))
    if window != scene["window"]:
        problems.append(f"warm window identity differs: report has {window}, scene registers {scene['window']}")
    ticks_end = report.get("worldTicksAtEnd")
    if type(ticks_end) is not int or ticks_end < scene["minWorldTicksAtEnd"]:
        problems.append(f"fixture did not complete the warm window: worldTicksAtEnd={ticks_end!r}")
    samples = report.get("frameIntervalWarmSampleCount")
    if type(samples) is not int or samples < MIN_SAMPLES:
        problems.append(f"warm window has too few samples: {samples!r}")
    p50 = finite_ms(report, "frameIntervalWarmP50Ms", problems)
    p95 = finite_ms(report, "frameIntervalWarmP95Ms", problems)
    p99 = finite_ms(report, "frameIntervalWarmP99Ms", problems)
    if None not in (p50, p95, p99) and not (p99 >= p95 >= p50):
        problems.append(f"warm percentiles are not ordered: p50={p50} p95={p95} p99={p99}")
    nonair = parse_nonair(report)
    low, high = scene["nonAirBand"]
    if nonair is None or not low <= nonair <= high:
        problems.append(f"positive control failed: worldContent nonAir={nonair!r}, band is [{low}, {high}]")
    sections = report.get("captureListedSections")
    if type(sections) is not int:
        problems.append(f"positive control missing: captureListedSections={sections!r}")
    picture = run_dir / "presented.png"
    picture_info = None
    if not picture.is_file():
        problems.append("presented image is missing (presentation identity cannot be checked)")
    else:
        try:
            size = gv_leg.png_dimensions(picture)
            picture_info = {"path": str(picture), "sha256": sha256_file(picture), "size": list(size)}
        except (gv_leg.LegError, OSError, ValueError) as error:
            problems.append(f"presented image is not a valid PNG: {error}")
    console_path = run_dir / "console.log"
    if not console_path.is_file():
        problems.append("console log is missing")
    elif scene["consoleGates"]:
        console = console_path.read_text(encoding="utf-8", errors="replace")
        for gate in scene["consoleGates"]:
            if gate not in console:
                problems.append(f"console gate missing: {gate!r}")
    record.update({
        "p50Ms": p50, "p95Ms": p95, "p99Ms": p99, "samples": samples,
        "worldTicksAtEnd": ticks_end, "nonAir": nonair, "captureListedSections": sections,
        "warmTickP50Ms": report.get("warmTickP50Ms"), "warmSpikeCount": report.get("warmSpikeCount"),
        "tickFrames": report.get("frameIntervalWarmTickFrames"),
        "tickP50Ms": report.get("frameIntervalWarmTickFrameP50Ms"),
        "plainFrames": report.get("frameIntervalWarmPlainFrames"),
        "plainP50Ms": report.get("frameIntervalWarmPlainFrameP50Ms"),
        "conditions": {key: report.get(key) for key in CONDITION_FIELDS},
        "picture": picture_info,
    })
    record["ok"] = not problems
    return record


def render_node_holders() -> list[dict]:
    """Processes holding a DRM render node, other than this one - a scan, recorded as evidence.

    Unreadable /proc entries are skipped, so this can undercount; it is a preflight record
    for the operator, not a proof of absence. The hard gate is the Foundation/game scan.
    """
    holders = []
    for proc in Path("/proc").glob("[0-9]*"):
        pid = int(proc.name)
        if pid == os.getpid():
            continue
        try:
            fds = list((proc / "fd").iterdir())
        except OSError:
            continue
        for fd in fds:
            try:
                target = os.readlink(fd)
            except OSError:
                continue
            if "/dev/dri/renderD" in target:
                try:
                    comm = (proc / "comm").read_text(encoding="utf-8", errors="replace").strip()
                except OSError:
                    comm = "?"
                holders.append({"pid": pid, "comm": comm})
                break
    return holders


def preflight(out_root: Path, force: bool) -> dict:
    """The quiet-window gate of 658 4.2, measured the way gv_kpi measures it."""
    record = {"at": now_iso(), "host": os.uname().nodename, "forced": force}
    games = gv_leg.foundation_pids()
    record["foundationPids"] = games
    if games:
        raise BaselineError(f"another Foundation client is running (pids {games}); no run was started")
    record["renderNodeHolders"] = render_node_holders()
    record["loadAverage"] = list(os.getloadavg())
    busy = gv_kpi.busy_cores(10.0)
    record["busyCores"] = round(busy, 3)
    record["loadLimit"] = LOAD_LIMIT
    free = os.statvfs(out_root).f_bavail * os.statvfs(out_root).f_frsize
    record["diskFreeBytes"] = free
    if free < DISK_MIN_BYTES:
        raise BaselineError(f"only {free} bytes free under {out_root}; a pair of pack copies alone is ~2 GB")
    if busy > LOAD_LIMIT and not force:
        raise BaselineError(f"loaded machine ({busy:.2f} busy cores > {LOAD_LIMIT}); wait for a quiet window "
                            "or pass --force to record a forced start (the verdict is then capped)")
    record["quiet"] = busy <= LOAD_LIMIT
    return record


def compute_verdict(result: dict) -> tuple[str, list[str]]:
    """Pure judgment over a result dict - shared by run, attest and judge."""
    reasons: list[str] = []
    pairs = result["pairs"]
    valid = [p for p in pairs if p.get("valid")]
    identical = result["inputs"]["identicalJars"]
    visual = result.get("visualInspection") or {}
    visual_done = bool(visual.get("armA")) and bool(visual.get("armAprime"))
    if result["preflight"].get("forced"):
        reasons.append("the start was forced on a loaded machine, so no quiet-window verdict is available")
        return "NOT_ESTABLISHED", reasons
    if len(valid) < DEFAULT_PAIRS:
        reasons.append(f"only {len(valid)} of {len(pairs)} registered pairs are valid "
                       f"(exclusions are recorded per pair; excluded pairs are never re-rolled)")
        return ("NOT_ESTABLISHED" if identical else "NO_VERDICT"), reasons
    ratios = [p["p50Ratio"] for p in valid]
    median = statistics.median(ratios)
    reasons.append(f"per-pair P50 ratios (A'/A): {ratios}; median {median:.4f} over {len(valid)} valid pairs")
    if identical:
        if median > BAND or median < 1.0 / BAND:
            reasons.append(f"median {median:.4f} is outside the symmetric noise band "
                           f"[{1.0 / BAND:.4f}, {BAND}] on identical jars: the yardstick itself "
                           "drifted more than its registered band in this window")
            return "NOT_ESTABLISHED", reasons
        if not visual_done:
            reasons.append("within band; awaiting the executing lane's own-eye attestation of one "
                           "presented image per arm")
            return "ESTABLISHED_PENDING_VISUAL", reasons
        reasons.append("baseline established: 3+ valid pairs in a quiet window, median within the "
                       "registered noise band, presentation attested")
        return "ESTABLISHED", reasons
    if median <= 1.00:
        verdict = "NON_DEGRADATION"
    elif median <= BAND:
        verdict = "NOISE_BAND"
    else:
        reasons.append(f"median {median:.4f} exceeds the {BAND} band edge (658 4.2)")
        return "DEGRADED", reasons
    if not visual_done:
        return verdict + "_PENDING_VISUAL", reasons
    return verdict, reasons


def run_driver(template: str, mapping: dict, out_dir: Path) -> tuple[int, float]:
    """Launch one run through the operator's driver; own process group, hard timeout."""
    command = template.format(**mapping)
    log_path = out_dir / "driver.log"
    started = time.monotonic()
    with log_path.open("xb") as log:
        process = subprocess.Popen(["/bin/sh", "-c", command], stdout=log, stderr=subprocess.STDOUT,
                                   start_new_session=True)
        try:
            code = process.wait(timeout=RUN_TIMEOUT)
        except subprocess.TimeoutExpired:
            try:
                os.killpg(process.pid, 9)
            except ProcessLookupError:
                pass
            process.wait()
            return 124, time.monotonic() - started
    return code, time.monotonic() - started


def pair_exclusions(pair: dict, identical: bool, external: float) -> list[str]:
    """The confound screens of 658 4.2 / gv_kpi, applied to one assembled pair."""
    reasons = []
    run_a, run_b = pair["runs"]["A"], pair["runs"]["Aprime"]
    for arm, run in (("A", run_a), ("A'", run_b)):
        if not run.get("ok"):
            reasons.append(f"arm {arm} run is not clean: {run.get('problems')}")
    if reasons:
        return reasons
    if external > EXTERNAL_LIMIT:
        reasons.append(f"{external:.2f} cores were busy outside the legs during this pair "
                       f"(limit {EXTERNAL_LIMIT}): the pair compares different machine states")
    counts = (run_a["samples"], run_b["samples"])
    if max(counts) > STARVED_RATIO * min(counts):
        reasons.append(f"warm sample counts differ {counts[0]} vs {counts[1]}: the arms did not "
                       "run on the same machine state (gv_kpi's starved screen)")
    for field in CONDITION_FIELDS + ("nonAir", "captureListedSections",
                                     "frameIntervalWarmFromTick", "frameIntervalWarmToTick"):
        left = run_a["conditions"].get(field, run_a.get(field))
        right = run_b["conditions"].get(field, run_b.get(field))
        if left != right:
            reasons.append(f"scene did not reproduce: {field} is {left!r} on A and {right!r} on A'")
    if identical:
        sha_a = (run_a.get("picture") or {}).get("sha256")
        sha_b = (run_b.get("picture") or {}).get("sha256")
        if sha_a != sha_b:
            reasons.append("presented images differ between identical-jar arms: the scene did "
                           "not reproduce (or state leaked between runs)")
    return reasons


def build_result(args, jar_a: Path, jar_b: Path) -> dict:
    scene = SCENES[args.scene]
    template = args.driver_cmd or ""
    driver_script = None
    tokens = template.split()
    if tokens and Path(tokens[0]).is_file():
        driver_script = {"path": tokens[0], "sha256": sha256_file(Path(tokens[0]))}
    return {
        "schemaVersion": 1, "kind": "self-paired-baseline", "item": 693,
        "protocolSource": "celeritas-exit-stage0-658.md section 4.2 (self-paired yardstick)",
        "tag": args.tag, "scene": args.scene, "sceneDescription": scene["description"],
        "registration": {
            "pairs": args.pairs, "band": BAND, "window": list(scene["window"]),
            "orderRule": "odd pairs run A then A', even pairs run A' then A",
            "exclusionRule": "confounded or non-reproducing pairs are excluded with reasons "
                             "recorded; excluded pairs are never re-rolled",
            "loadLimit": LOAD_LIMIT, "externalLimit": EXTERNAL_LIMIT,
            "minSamples": MIN_SAMPLES, "registeredAt": now_iso(),
        },
        "inputs": {
            "armA": {"jar": str(jar_a), "sha256": sha256_file(jar_a)},
            "armAprime": {"jar": str(jar_b), "sha256": sha256_file(jar_b)},
            "identicalJars": sha256_file(jar_a) == sha256_file(jar_b),
            "driverTemplate": template, "driverScript": driver_script,
        },
        "pairs": [], "visualInspection": {"armA": None, "armAprime": None},
        "verdict": None, "verdictReasons": [], "verdictHistory": [],
    }


def stamp_verdict(result: dict, by: str) -> None:
    verdict, reasons = compute_verdict(result)
    result["verdict"] = verdict
    result["verdictReasons"] = reasons
    result["verdictHistory"].append({"verdict": verdict, "at": now_iso(), "by": by})


def verdict_exit(verdict: str) -> int:
    return 0 if verdict in ("ESTABLISHED", "ESTABLISHED_PENDING_VISUAL", "NON_DEGRADATION",
                            "NON_DEGRADATION_PENDING_VISUAL", "NOISE_BAND",
                            "NOISE_BAND_PENDING_VISUAL") else 1


def cmd_plan(args) -> int:
    jar_a, jar_b = Path(args.jar_a), Path(args.jar_aprime)
    result = build_result(args, jar_a, jar_b)
    print(json.dumps({"registration": result["registration"], "inputs": result["inputs"],
                      "scene": result["scene"], "sceneDescription": result["sceneDescription"]},
                     indent=2))
    for index in range(1, args.pairs + 1):
        order = ("A", "Aprime") if index % 2 == 1 else ("Aprime", "A")
        for arm in order:
            run_id = f"{args.tag}-p{index}-{arm}"
            out_dir = Path(args.out_root) / f"selfpaired-{args.tag}" / run_id
            jar = jar_a if arm == "A" else jar_b
            command = (args.driver_cmd or "").format(run_id=run_id, arm=arm, jar=str(jar),
                                                     out_dir=str(out_dir), scene=args.scene)
            print(f"pair {index} arm {arm}: {command}")
    return 0


def cmd_run(args) -> int:
    jar_a, jar_b = Path(args.jar_a), Path(args.jar_aprime)
    for jar in (jar_a, jar_b):
        if not jar.is_file():
            raise BaselineError(f"jar is not a file: {jar}")
    if not args.driver_cmd:
        raise BaselineError("--driver-cmd is required for run (see the driver contract in the docstring)")
    out_root = Path(args.out_root)
    result_path = out_root / f"{args.tag}-selfpaired.json"
    if result_path.exists():
        raise BaselineError(f"result already exists; preserve it and choose a fresh tag: {result_path}")
    base_dir = out_root / f"selfpaired-{args.tag}"
    if base_dir.exists():
        raise BaselineError(f"run directory already exists; preserve it and choose a fresh tag: {base_dir}")
    result = build_result(args, jar_a, jar_b)
    out_root.mkdir(parents=True, exist_ok=True)
    result["preflight"] = preflight(out_root, args.force)
    base_dir.mkdir(parents=True)
    scene = SCENES[args.scene]
    total_start = gv_kpi.cpu_snapshot()
    for index in range(1, args.pairs + 1):
        order = ("A", "Aprime") if index % 2 == 1 else ("Aprime", "A")
        pair: dict = {"pair": index, "order": list(order), "runs": {}}
        pair_start = gv_kpi.cpu_snapshot()
        for arm in order:
            run_id = f"{args.tag}-p{index}-{arm}"
            run_dir = base_dir / run_id
            run_dir.mkdir()
            jar = jar_a if arm == "A" else jar_b
            code, elapsed = run_driver(args.driver_cmd,
                                       {"run_id": run_id, "arm": arm, "jar": str(jar),
                                        "out_dir": str(run_dir), "scene": args.scene}, run_dir)
            record = extract_run(run_dir, scene, code)
            record.update({"runId": run_id, "driverExitCode": code,
                           "elapsedSeconds": round(elapsed, 1),
                           "jarSha256": result["inputs"]["armA" if arm == "A" else "armAprime"]["sha256"]})
            pair["runs"][arm] = record
            state = "clean" if record.get("ok") else f"problems: {record.get('problems')}"
            print(f"{run_id}: driver exit {code}, {state}", flush=True)
        external = gv_kpi.external_cores(pair_start, gv_kpi.cpu_snapshot())
        pair["externalCpuCores"] = round(external, 3)
        exclusions = pair_exclusions(pair, result["inputs"]["identicalJars"], external)
        pair["exclusions"] = exclusions
        pair["valid"] = not exclusions
        if pair["valid"]:
            pair["p50Ratio"] = pair["runs"]["Aprime"]["p50Ms"] / pair["runs"]["A"]["p50Ms"]
            pair["p95Ratio"] = pair["runs"]["Aprime"]["p95Ms"] / pair["runs"]["A"]["p95Ms"]
        result["pairs"].append(pair)
        print(f"pair {index}: {'valid' if pair['valid'] else 'EXCLUDED ' + str(exclusions)}", flush=True)
    result["environment"] = {"externalCpuCoresTotal": round(
        gv_kpi.external_cores(total_start, gv_kpi.cpu_snapshot()), 3)}
    stamp_verdict(result, "run")
    with result_path.open("x", encoding="utf-8") as stream:
        stream.write(json.dumps(result, indent=2) + "\n")
    print(f"verdict={result['verdict']}")
    for reason in result["verdictReasons"]:
        print(f"  - {reason}")
    print(f"result: {result_path}")
    if result["verdict"].endswith("PENDING_VISUAL"):
        first_valid = next(p for p in result["pairs"] if p["valid"])
        for arm in ("A", "Aprime"):
            run = first_valid["runs"][arm]
            print(f"view before attesting ({arm}): {(run.get('picture') or {}).get('path')}")
    return verdict_exit(result["verdict"])


def load_result(path: str) -> tuple[Path, dict]:
    result_path = Path(path)
    value = json.loads(result_path.read_text(encoding="utf-8"))
    if not isinstance(value, dict) or value.get("kind") != "self-paired-baseline":
        raise BaselineError(f"not a self-paired baseline result: {path}")
    return result_path, value


def cmd_attest(args) -> int:
    result_path, result = load_result(args.result)
    result["visualInspection"] = {"armA": args.visual_a, "armAprime": args.visual_aprime,
                                  "at": now_iso()}
    stamp_verdict(result, "attest")
    result_path.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print(f"verdict={result['verdict']}")
    for reason in result["verdictReasons"]:
        print(f"  - {reason}")
    return verdict_exit(result["verdict"])


def cmd_judge(args) -> int:
    _, result = load_result(args.result)
    verdict, reasons = compute_verdict(result)
    print(f"verdict={verdict}")
    for reason in reasons:
        print(f"  - {reason}")
    return verdict_exit(verdict)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Self-paired baseline harness (item 693; protocol in the docstring)")
    sub = parser.add_subparsers(dest="command", required=True)
    for name in ("plan", "run"):
        child = sub.add_parser(name)
        child.add_argument("--tag", required=True)
        child.add_argument("--scene", choices=sorted(SCENES), required=True)
        child.add_argument("--jar-a", required=True)
        child.add_argument("--jar-aprime", required=True)
        child.add_argument("--driver-cmd", default="")
        child.add_argument("--pairs", type=int, default=DEFAULT_PAIRS)
        child.add_argument("--out-root", default=".gv-verify")
        child.add_argument("--force", action="store_true",
                           help="start on a loaded machine; recorded, and caps the verdict")
    attest = sub.add_parser("attest")
    attest.add_argument("--result", required=True)
    attest.add_argument("--visual-a", required=True,
                        help="the executing lane's own-eye note on arm A's presented image")
    attest.add_argument("--visual-aprime", required=True)
    judge = sub.add_parser("judge")
    judge.add_argument("--result", required=True)
    args = parser.parse_args(argv)
    if getattr(args, "tag", None) is not None and not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9_-]{0,39}", args.tag):
        raise BaselineError("tag must be 1-40 ASCII letters/digits/_/-, starting with a letter or digit")
    pairs = getattr(args, "pairs", None)
    if pairs is not None and not DEFAULT_PAIRS <= pairs <= MAX_PAIRS:
        raise BaselineError(f"pairs must be {DEFAULT_PAIRS}..{MAX_PAIRS} (658 4.2: one judgment unit is "
                            f"at least {DEFAULT_PAIRS} pairs; more is a new registration)")
    handler = {"plan": cmd_plan, "run": cmd_run, "attest": cmd_attest, "judge": cmd_judge}[args.command]
    return handler(args)


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (BaselineError, gv_leg.LegError, OSError, ValueError, KeyError, TypeError) as error:
        print(f"self-paired baseline unusable: {error}", file=sys.stderr)
        sys.exit(2)
