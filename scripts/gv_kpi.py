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


def stage_inputs(jars: Path, role: str) -> tuple[Path, Path, dict]:
    """Resolves one staged renderer/fixture pair and checks it against that stage's own build provenance."""
    renderer = gv_leg.one_jar(jars, "celtium-forge-mc1.12.2-*.jar")
    fixture = gv_leg.one_jar(jars, "celtium-validation-client-*.jar")
    provenance_path = jars / "build-provenance.json"
    provenance = object_at(provenance_path)
    if provenance.get("buildExitCode") != 0 or not re.fullmatch(r"[0-9a-f]{40}", str(provenance.get("head", ""))):
        raise KpiError("stage needs successful build provenance with its source revision")
    checked = {}
    for name, path in (("renderer", renderer), ("fixture", fixture)):
        sha = digest(path)
        if provenance.get("artifacts", {}).get(path.name, {}).get("sha256") != sha:
            raise KpiError(f"{role} {name} does not match the stage's build provenance")
        checked[name] = {"source": str(path.resolve()), "sha256": sha}
    return renderer, fixture, checked


def inputs(jars: Path, reference: Path | None, control: Path | None = None) -> dict:
    """No default historical stage, and no inference that the current git HEAD produced an arbitrary jar."""
    renderer, fixture, checked = stage_inputs(jars, "stage")
    pinned = dict(checked)
    if reference is not None:
        pinned["reference"] = {"source": str(reference.resolve()), "sha256": digest(reference)}
    else:
        pinned["reference"] = None
    provenance_path = jars / "build-provenance.json"
    provenance = object_at(provenance_path)
    pinned["buildProvenance"] = {"source": str(provenance_path.resolve()), "sha256": digest(provenance_path),
                                 "gitHead": provenance["head"], "dirtyFiles": provenance.get("dirtyFiles", [])}
    if control is not None:
        controlRenderer, controlFixture, controlChecked = stage_inputs(control, "control stage")
        # A rebuilt fixture jar never has the same bytes as the candidate's, so identity cannot be required - but the
        # comparison only holds if both fixtures report the same window, and that is checked when each report is read
        # (warmFromTick/warmToTick/sample count/completion). The difference is recorded rather than waved through.
        if controlFixture.name != fixture.name:
            raise KpiError("the control stage's fixture file name differs from the candidate's")
        pinned["control"] = controlChecked["renderer"]
        pinned["controlFixture"] = controlChecked["fixture"]
        pinned["controlFixtureDiffers"] = controlChecked["fixture"]["sha256"] != checked["fixture"]["sha256"]
    return pinned


def finite_time(report: dict, key: str) -> float:
    value = report.get(key)
    if type(value) not in (float, int) or not math.isfinite(value) or value < 0.05:
        raise KpiError(f"missing/invalid {key}: {value!r}")
    return float(value)


def warm_metric(report: dict, process: dict, result: dict, manifest: dict, pinned: dict, reference: bool,
                renderer_key: str = None) -> dict:
    if result.get("accepted") is not True or result.get("exitCode") != 0:
        raise KpiError(f"leg was not accepted: {result.get('problems')}")
    if (type(process.get("exitCode")) is not int or process["exitCode"] != 0
            or process.get("fixtureStatus") != "PASSED" or report.get("status") != "PASSED"
            or process.get("gpuRequirementMet") is not True):
        raise KpiError("missing/failed process, fixture, or actual GPU evidence")
    expected_renderer = pinned[renderer_key if renderer_key else ("reference" if reference else "renderer")]
    expected_fixture = pinned["controlFixture"] if renderer_key == "control" else pinned["fixture"]
    for role, expected in (("renderer", expected_renderer), ("fixture", expected_fixture)):
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


def name_samples(pair: dict, name: str) -> int:
    return pair[name]["samples"]


def summarize(pairs: list[dict], arm_only: list[str]) -> dict:
    control_ratios = [p["celtium"]["p50Ms"] / p["control"]["p50Ms"] for p in pairs if "control" in p]
    # A warm window that produced far fewer frames than its peers did not run on the same machine. Sample counts are
    # the throughput each leg actually achieved, so a wide gap inside one repetition is disturbance, not a result: a run
    # whose control legs managed 1072 and 1365 samples against the candidate's 4461 reported the control as 36% slower
    # when the machine had simply starved it.
    # Only the arm names: iterating the pair dict would include "repetition", which is an int and has no samples.
    arms = ("celtium", "celeritas", "control")
    disturbed = [p.get("repetition", index + 1) for index, p in enumerate(pairs)
                 if max(name_samples(p, n) for n in arms if n in p) > 1.5 * min(
                     name_samples(p, n) for n in arms if n in p)]
    reference_pairs = [p for p in pairs if "celeritas" in p]
    ratios = [p["celtium"]["p50Ms"] / p["celeritas"]["p50Ms"] for p in reference_pairs]
    ratio = statistics.median(ratios) if ratios else None
    # A single pair, asymmetrical flags, or a frame limiter cannot establish the product's performance KPI.
    paced = any(p[name]["conditions"].get("vsyncEnabled") is not False
                or p[name]["conditions"].get("framerateLimitSetting") != 260
                for p in pairs for name in ("celtium", "celeritas") if name in p)
    verdict = ("NO_SPEED_VERDICT" if len(pairs) < 3 or arm_only or paced or disturbed or not ratios
               else ("NOT_SLOWER" if ratio <= 1 else "SLOWER"))
    summary = {"metric": "frameIntervalWarmP50Ms", "windowWorldTicks": [400, 600], "pairs": pairs,
            "pairRatios": ratios, "medianPairRatio": ratio, "verdict": verdict,
            "scope": "fixture frame intervals; startup/build reported separately; includes simulation, GUI and limiter; "
                     "no automatic noise margin or significance claim"}
    summary["disturbedRepetitions"] = disturbed
    if disturbed:
        summary["scope"] += ("; repetitions " + str(disturbed) + " produced fewer than two thirds of their peers' "
                             "frames, so the arms did not run on the same machine - no verdict from them")
    if control_ratios:
        # Candidate against a control renderer measured in the same window: the same jar family, same arm, same legs -
        # so a machine that drifts between legs moves both and the ratio survives it. This is the number that can rank
        # two builds; the reference ratio answers a different question (how far from Celeritas are we).
        summary["controlRatios"] = control_ratios
        summary["medianControlRatio"] = statistics.median(control_ratios)
        summary["controlScope"] = ("candidate against the control build, interleaved in the same repetitions; "
                                  "cancels between-leg machine drift, does not compare against the reference")
    return summary


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
    if not os.environ.get("CELTIUM_KPI_JARS"):
        raise KpiError("set CELTIUM_KPI_JARS to an explicit build stage")
    if not os.environ.get("CELTIUM_KPI_REFERENCE") and not os.environ.get("CELTIUM_KPI_SKIP_REFERENCE") == "1":
        raise KpiError("set CELTIUM_KPI_REFERENCE to the reference jar, or CELTIUM_KPI_SKIP_REFERENCE=1 with a control")
    # Optional third stage: a fixed build measured in the same repetitions as the candidate. Its ratio to the
    # candidate is same-arm and same-window, so machine drift between legs moves both legs and cancels - which the
    # reference ratio cannot do, because the reference is a different renderer with a different cost profile.
    control = Path(os.environ["CELTIUM_KPI_CONTROL_JARS"]).resolve() if os.environ.get("CELTIUM_KPI_CONTROL_JARS") else None
    # The reference arm answers "how far from Celeritas are we"; the control arm answers "is this build faster than
    # that build". Ranking a change only needs the second, and dropping the reference arm cuts a run from nine legs to
    # six - which matters when the machine is loaded and every leg is a chance to be starved.
    skip_reference = os.environ.get("CELTIUM_KPI_SKIP_REFERENCE") == "1"
    if skip_reference and control is None:
        raise KpiError("CELTIUM_KPI_SKIP_REFERENCE needs CELTIUM_KPI_CONTROL_JARS: without either arm there is nothing "
                       "to compare")
    root = Path(__file__).resolve().parent.parent
    os.chdir(root)
    jars = Path(os.environ["CELTIUM_KPI_JARS"]).resolve()
    reference = Path(os.environ["CELTIUM_KPI_REFERENCE"]).resolve() if os.environ.get("CELTIUM_KPI_REFERENCE") else None
    pinned = inputs(jars, None if skip_reference else reference, control)
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
            arms = ["celtium"] + ([] if skip_reference else ["celeritas"]) + (["control"] if control is not None else [])
            for name in arms:
                if name == "celeritas":
                    os.environ["CELTIUM_LEG_REFERENCE_RENDERER"] = str(reference)
                else:
                    os.environ.pop("CELTIUM_LEG_REFERENCE_RENDERER", None)
                stage = control if name == "control" else jars
                pair[name] = gv_leg.make_plan(f"{tag}-{name}-r{rep}", stage, scene, gpu, duration,
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
        order = ["celtium"] + ([] if skip_reference else ["celeritas"]) + (["control"] if control is not None else [])
        if rep % 2 == 0:
            order.reverse()
        for name in order:
            plan = plans_for_pair[name]
            result = gv_leg.execute(plan)
            # The leg's own verdict first: a run that never reached the fixture leaves no report to parse, and reading
            # it anyway reported "Expecting value: line 1 column 1" instead of the harness's actual problem (a missing
            # validation-process.json on one control leg). A failed leg names itself now.
            if result.get("accepted") is not True or result.get("exitCode") != 0:
                raise KpiError(f"{plan.leg} was not accepted: {result.get('problems')}")
            report = object_at(plan.paths["game"] / "logs/celtium-validation.json")
            process = object_at(plan.paths["game"] / "validation-process.json")
            metric = warm_metric(report, process, result, plan.manifest, pinned, name == "celeritas",
                                 "control" if name == "control" else None)
            metric["leg"] = plan.leg
            pair[name] = metric
            print(f"{plan.leg}: warm p50={metric['p50Ms']:.3f}ms p95={metric['p95Ms']:.3f}ms "
                  f"samples={metric['samples']} renderer={metric['rendererSha256']}", flush=True)
        if not arm_only and "celeritas" in pair:
            same_conditions(pair)
        pairs.append(pair)
    summary = summarize(pairs, arm_only)
    summary["environment"] = {"initialLoadAverage": load, "finalLoadAverage": os.getloadavg()[0],
                              "loadLimit": load_limit, "cpuCount": os.cpu_count(),
                              "guardBypassed": os.environ.get("CELTIUM_KPI_FORCE") == "1"}
    final_load = summary["environment"]["finalLoadAverage"]
    if load > load_limit or final_load > load_limit:
        # Persistent external load (the browser showing this session) is not a renderer property: the paired,
        # order-alternating design cancels it between arms, but the absolute milliseconds keep its floor.
        summary["scope"] += ("; measured under external load " + f"{load:.2f} -> {final_load:.2f}"
                             + f" (limit {load_limit:g}, override recorded): ratios only, not absolute frame times")
    if final_load > max(load_limit, load * 1.5):
        # A run whose load more than doubles, or ends above the guard, was overtaken by something else (this session's
        # own browser is the usual cause). Its pairs then compare different machines, and a median taken across them is
        # not a measurement of the build - which is what happened on the fbo-cache-c run, 2.69 -> 7.34.
        summary["verdict"] = "NO_SPEED_VERDICT"
        summary["scope"] += ("; load rose during the run (" + f"{load:.2f} -> {final_load:.2f}"
                             + "): the pairs compare different machine states, so no verdict is safe")
    with (root / ".gv-verify" / f"{tag}-kpi-result.json").open("x") as out:
        json.dump({"inputs": pinned, **summary}, out, indent=2)
    if summary.get("medianPairRatio") is not None:
        print(f"median paired ratio={summary['medianPairRatio']:.3f}x verdict={summary['verdict']}")
    else:
        print(f"no reference arm (ranking mode); verdict={summary['verdict']}")
    if "medianControlRatio" in summary:
        print(f"median control ratio={summary['medianControlRatio']:.3f}x "
              f"(candidate vs the control build, interleaved in the same repetitions)")
    return {"NOT_SLOWER": 0, "SLOWER": 1, "NO_SPEED_VERDICT": 2}[summary["verdict"]]


if __name__ == "__main__":
    try:
        sys.exit(main(sys.argv[1:]))
    except (KpiError, gv_leg.LegError, OSError, ValueError, TypeError, KeyError) as error:
        print(f"KPI unusable (no speed verdict): {error}", file=sys.stderr)
        sys.exit(2)
