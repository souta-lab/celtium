#!/usr/bin/env python3
"""Fails if the benchmark's control audit calls an unmeasured control consistent.

The audit's job is to say whether the runs being compared really share their controls. Its failure mode, found by the
full audit and reproduced here, is that a required control missing from every run's record produced the single
distinct value {"null"} - because json.dumps(None) is the string "null" - and was therefore reported as equal. The
report could then read "all required controls are equal" while every required control was absent.

This check builds run records with the required controls absent, present-but-different, and present-and-equal, and
asserts the audit answers missing / differ / equal respectively. It runs without a GPU.
"""
import json
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent.parent))
import benchmark_celtium as bench  # noqa: E402


def run(config, fixture):
    return {"config": config, "fixture": fixture, "ordinal": 1}


class Args:
    """The audit echoes the requested frame and warm-up counts from the parsed arguments."""
    frames = 900
    warmup_ticks = 900
    warmup_frames = 300


def audit_for(fixtures):
    runs = [run("a", fixtures[0]), run("b", fixtures[1])]
    return bench.controls_audit(runs, Args())


def main():
    # The audit must stay importable: a check that cannot run is not a check.
    if not hasattr(bench, "controls_audit"):
        print("bench_controls_check: controls_audit is not importable from benchmark_celtium; the check cannot run")
        print("  (this is a failure: the invariant it guards is the one the audit found broken)")
        return 1
    # The names are the audit's own required fields, read from controls_audit's table; if that table changes, this
    # check must change with it rather than silently testing fields nobody reads.
    common = {"framelimitAtStart": 260, "framelimitAtEnd": 260,
              "vsyncAtStart": False, "vsyncAtEnd": False,
              "renderDistanceAtStart": 12, "renderDistanceAtEnd": 12,
              "displayWidth": 1280, "displayHeight": 720,
              "seedRequested": 12345678, "worldSeed": 12345678,
              "worldLayers": "3;minecraft:bedrock,2*minecraft:dirt,minecraft:grass,minecraft:stained_glass;1;",
              "pinnedWorldTime": 6000, "guiHidden": True, "lightmapFlickerPinned": True,
              "cameraPoseX": 0.5, "cameraPoseY": 12.0, "cameraPoseZ": 0.5,
              "cameraPoseYaw": 180.0, "cameraPosePitch": 20.0,
              "cameraX": 0.5, "cameraY": 12.0, "cameraZ": 0.5,
              "cameraYaw": 180.0, "cameraPitch": 20.0}
    failures = []

    missing = dict(common)
    for key in ("seedRequested", "worldSeed", "worldLayers", "pinnedWorldTime"):
        missing.pop(key)
    audit = audit_for([missing, dict(missing)])
    if audit["consistent"]:
        failures.append("a run set with the seed/layers/time controls absent was reported consistent")
    if not audit.get("missingFields"):
        failures.append("absent controls were not named in missingFields")
    for name in ("seedRequested", "worldLayers", "pinnedWorldTime"):
        check = next(c for c in audit["controls"] if c["field"] == name)
        if check["consistent"] is not False:
            failures.append("field %s absent from every run reported consistent=%r" % (name, check["consistent"]))

    differ = dict(common)
    other = dict(common)
    other["renderDistanceAtStart"] = 8
    other["renderDistanceAtEnd"] = 8
    audit = audit_for([differ, other])
    if audit["consistent"]:
        failures.append("runs with different render distances were reported consistent")
    if "renderDistanceAtStart" not in audit["mismatchedFields"]:
        failures.append("the differing control was not named in mismatchedFields")

    audit = audit_for([dict(common), dict(common)])
    if not audit["consistent"]:
        failures.append("identical run sets were reported inconsistent: %r" % (audit["mismatchedFields"],))
    if audit.get("missingFields"):
        failures.append("identical, fully measured run sets reported missing fields")

    print("bench_controls_check: missing -> %s, differ -> %s, equal -> %s"
          % ("ok", "ok", "ok") if not failures else "FAILURES:")
    for failure in failures:
        print("  - " + failure)
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
