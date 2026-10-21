#!/usr/bin/env python3
"""Paired warm-window renderer measurements with explicit, hash-pinned inputs.

Uses gv_leg's checked native/GL-reference paths. A timing ratio is not visual acceptance,
a statistical significance test, or proof that a tick-numbered window is idle/fully loaded.
"""
from __future__ import annotations

import hashlib
import json
import math
import os
from pathlib import Path
import re
import statistics
import sys

import gv_leg


class KpiError(ValueError):
    pass


def object_at(path: Path) -> dict:
    value = json.loads(path.read_text())
    if not isinstance(value, dict):
        raise KpiError(f"not a JSON object: {path}")
    return value


def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def inputs(jars: Path, reference: Path) -> dict:
    """No default historical stage, and no inference that the current git HEAD produced an arbitrary jar."""
    renderer = gv_leg.one_jar(jars, "celtium-forge-mc1.12.2-*.jar")
    fixture = gv_leg.one_jar(jars, "celtium-validation-client-*.jar")
    provenance_path = jars / "build-provenance.json"
    provenance = object_at(provenance_path)
    if provenance.get("buildExitCode") != 0 or not re.fullmatch(r"[0-9a-f]{40}", str(provenance.get("head", ""))):
        raise KpiError("stage needs successful build provenance with its source revision")
    pinned = {}
    for role, path in (("renderer", renderer), ("fixture", fixture), ("reference", reference)):
        sha = digest(path)
        if role != "reference" and provenance.get("artifacts", {}).get(path.name, {}).get("sha256") != sha:
            raise KpiError(f"{role} does not match the stage's build provenance")
        pinned[role] = {"source": str(path.resolve()), "sha256": sha}
    pinned["buildProvenance"] = {"source": str(provenance_path.resolve()), "sha256": digest(provenance_path),
                                 "gitHead": provenance["head"], "dirtyFiles": provenance.get("dirtyFiles", [])}
    return pinned


def finite_time(report: dict, key: str) -> float:
    value = report.get(key)
    if type(value) not in (float, int) or not math.isfinite(value) or value < 0.05:
        raise KpiError(f"missing/invalid {key}: {value!r}")
    return float(value)


def warm_metric(report: dict, process: dict, result: dict, manifest: dict, pinned: dict, reference: bool) -> dict:
    if result.get("accepted") is not True or result.get("exitCode") != 0:
        raise KpiError(f"leg was not accepted: {result.get('problems')}")
    if (type(process.get("exitCode")) is not int or process["exitCode"] != 0
            or process.get("fixtureStatus") != "PASSED" or report.get("status") != "PASSED"
            or process.get("gpuRequirementMet") is not True):
        raise KpiError("missing/failed process, fixture, or actual GPU evidence")
    for role, expected in (("renderer", pinned["reference" if reference else "renderer"]), ("fixture", pinned["fixture"])):
        if manifest.get("artifacts", {}).get(role, {}).get("sha256") != expected["sha256"]:
            raise KpiError(f"measured {role} differs from the pinned input")
    if report.get("frameIntervalWarmFromTick") != 400 or report.get("frameIntervalWarmToTick") != 600:
        raise KpiError("warm window identity is missing or differs from worldTicks [400,600)")
    if type(report.get("worldTicksAtEnd")) is not int or report["worldTicksAtEnd"] < 600:
        raise KpiError("fixture did not complete the warm window (increasing duration alone does not hold it open)")
    count = report.get("frameIntervalWarmSampleCount")
    if type(count) is not int or count < 50:
        raise KpiError(f"warm window has too few samples: {count!r}")
    p50 = finite_time(report, "frameIntervalWarmP50Ms")
    p95 = finite_time(report, "frameIntervalWarmP95Ms")
    if p95 < p50:
        raise KpiError("warm p95 is below p50")
    return {"p50Ms": p50, "p95Ms": p95, "samples": count, "worldTicksAtEnd": report["worldTicksAtEnd"],
            "rendererSha256": manifest["artifacts"]["renderer"]["sha256"],
            "fixtureSha256": manifest["artifacts"]["fixture"]["sha256"],
            "device": process.get("glRendererPreflight") if reference else process.get("nativeDeviceName"),
            "jvmArgs": manifest.get("jvmArgs"), "source": manifest.get("git"),
            "conditions": {key: report.get(key) for key in (
                "sceneCalibrationKey", "saveSlot", "renderDistanceUsed", "cameraRequested", "captureWidth",
                "captureHeight", "renderCloudsAtCapture", "pinnedWorldTime", "framerateLimitSetting", "vsyncEnabled")}}


def same_conditions(pair: dict) -> None:
    left, right = pair["celtium"]["conditions"], pair["celeritas"]["conditions"]
    if any(value is None for value in left.values()) or left != right:
        raise KpiError("the arms have missing/different scene, camera, resolution, distance, clouds, time or pacing settings")


def summarize(pairs: list[dict], arm_only: list[str]) -> dict:
    ratios = [p["celtium"]["p50Ms"] / p["celeritas"]["p50Ms"] for p in pairs]
    ratio = statistics.median(ratios)
    # A single pair, asymmetrical flags, or a frame limiter cannot establish the product's performance KPI.
    paced = any(p[name]["conditions"].get("vsyncEnabled") is not False
                or p[name]["conditions"].get("framerateLimitSetting") != 260
                for p in pairs for name in ("celtium", "celeritas"))
    verdict = "NO_SPEED_VERDICT" if len(pairs) < 3 or arm_only or paced else ("NOT_SLOWER" if ratio <= 1 else "SLOWER")
    return {"metric": "frameIntervalWarmP50Ms", "windowWorldTicks": [400, 600], "pairs": pairs,
            "pairRatios": ratios, "medianPairRatio": ratio, "verdict": verdict,
            "scope": "fixture frame intervals; startup/build reported separately; includes simulation, GUI and limiter; "
                     "no automatic noise margin or significance claim"}


def main(argv: list[str]) -> int:
    if not argv:
        raise KpiError("usage: gv-kpi.sh TAG [SECONDS=240] [shared flags...] [-- celtium-only flags...]")
    tag = argv[0]
    if not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9_-]{0,39}", tag):
        raise KpiError("TAG must be 1-40 ASCII letters/digits/_/-, starting with a letter or digit")
    tail = argv[1:]
    duration = int(tail.pop(0)) if tail and tail[0].isdigit() else 240
    split = tail.index("--") if "--" in tail else len(tail)
    shared, arm_only = tail[:split], tail[split + 1:]
    reps = int(os.environ.get("CELTIUM_KPI_REPS", "3"))
    if not 1 <= reps <= 20 or duration <= 0:
        raise KpiError("positive duration and 1..20 repetitions required")
    if not os.environ.get("CELTIUM_KPI_JARS") or not os.environ.get("CELTIUM_KPI_REFERENCE"):
        raise KpiError("set CELTIUM_KPI_JARS to an explicit build stage and CELTIUM_KPI_REFERENCE to the reference jar")
    root = Path(__file__).resolve().parent.parent
    os.chdir(root)
    jars = Path(os.environ["CELTIUM_KPI_JARS"]).resolve()
    reference = Path(os.environ["CELTIUM_KPI_REFERENCE"]).resolve()
    pinned = inputs(jars, reference)
    load = os.getloadavg()[0]
    load_limit = float(os.environ.get("CELTIUM_KPI_LOAD_LIMIT", "2"))
    if os.environ.get("CELTIUM_KPI_FORCE") != "1" and load > load_limit:
        raise KpiError(f"loaded machine ({load:.2f} > {load_limit}); wait, lower the limit only to record it, or "
                       "explicitly choose CELTIUM_KPI_FORCE=1")
    scene, gpu = os.environ.get("CELTIUM_KPI_SCENE", "world"), os.environ.get("CELTIUM_KPI_GPU", "amd")
    common = ["-Dceltium.vulkan.validation=false", "-Dceltium.vulkan.presentReadback=false",
              "-Dceltium.vulkan.presentedDumpEvery=0"]
    # These come LAST: no scene option may accidentally restore the fixture's usual ~200-tick early finish.
    window = ["-Dceltium.validation.warmFromTick=400", "-Dceltium.validation.warmToTick=600",
              "-Dceltium.validation.holdUntilTick=620"]
    old_reference = os.environ.get("CELTIUM_LEG_REFERENCE_RENDERER")
    plans = []
    try:
        for rep in range(1, reps + 1):
            pair = {}
            for name in ("celtium", "celeritas"):
                if name == "celeritas":
                    os.environ["CELTIUM_LEG_REFERENCE_RENDERER"] = str(reference)
                else:
                    os.environ.pop("CELTIUM_LEG_REFERENCE_RENDERER", None)
                pair[name] = gv_leg.make_plan(f"{tag}-{name}-r{rep}", jars, scene, gpu, duration,
                                             common + shared + (arm_only if name == "celtium" else []) + window, root=root)
            plans.append(pair)
    finally:
        if old_reference is None: os.environ.pop("CELTIUM_LEG_REFERENCE_RENDERER", None)
        else: os.environ["CELTIUM_LEG_REFERENCE_RENDERER"] = old_reference
    evidence = root / ".gv-verify" / f"{tag}-kpi-input.json"
    with evidence.open("x") as out:
        json.dump({"inputs": pinned, "gpu": gpu, "mode": scene, "reps": reps, "duration": duration,
                   "sharedFlags": shared, "celtiumOnlyFlags": arm_only, "initialLoadAverage": load,
                   "loadLimit": load_limit, "loadGuardBypassed": os.environ.get("CELTIUM_KPI_FORCE") == "1"},
                  out, indent=2)
    pairs = []
    for rep, plans_for_pair in enumerate(plans, 1):
        pair = {"repetition": rep}
        # Alternate order to expose order/thermal/JIT drift rather than always giving one renderer the second run.
        for name in (("celtium", "celeritas") if rep % 2 else ("celeritas", "celtium")):
            plan = plans_for_pair[name]
            result = gv_leg.execute(plan)
            report = object_at(plan.paths["game"] / "logs/celtium-validation.json")
            process = object_at(plan.paths["game"] / "validation-process.json")
            metric = warm_metric(report, process, result, plan.manifest, pinned, name == "celeritas")
            metric["leg"] = plan.leg
            pair[name] = metric
            print(f"{plan.leg}: warm p50={metric['p50Ms']:.3f}ms p95={metric['p95Ms']:.3f}ms "
                  f"samples={metric['samples']} renderer={metric['rendererSha256']}", flush=True)
        if not arm_only:
            same_conditions(pair)
        pairs.append(pair)
    summary = summarize(pairs, arm_only)
    summary["environment"] = {"initialLoadAverage": load, "finalLoadAverage": os.getloadavg()[0],
                              "loadLimit": load_limit, "cpuCount": os.cpu_count(),
                              "guardBypassed": os.environ.get("CELTIUM_KPI_FORCE") == "1"}
    if load > load_limit:
        # Persistent external load (the browser showing this session) is not a renderer property: the paired,
        # order-alternating design cancels it between arms, but the absolute milliseconds keep its floor.
        summary["scope"] += ("; measured under external load " + f"{load:.2f}"
                             + f" (limit {load_limit:g}, override recorded): ratios only, not absolute frame times")
    with (root / ".gv-verify" / f"{tag}-kpi-result.json").open("x") as out:
        json.dump({"inputs": pinned, **summary}, out, indent=2)
    print(f"median paired ratio={summary['medianPairRatio']:.3f}x verdict={summary['verdict']}")
    return {"NOT_SLOWER": 0, "SLOWER": 1, "NO_SPEED_VERDICT": 2}[summary["verdict"]]


if __name__ == "__main__":
    try:
        sys.exit(main(sys.argv[1:]))
    except (KpiError, gv_leg.LegError, OSError, ValueError, TypeError, KeyError) as error:
        print(f"KPI unusable (no speed verdict): {error}", file=sys.stderr)
        sys.exit(2)
