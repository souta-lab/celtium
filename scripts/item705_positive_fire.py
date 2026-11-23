#!/usr/bin/env python3
"""Item 705: positive-fire proof harness for the descriptor-eviction rebuild (items 692/705).

The fixes for the queued-group descriptor use-after-free landed in v0.3.2 on code-level
proof plus contract tests, with no live-fire evidence: in the one verification leg that
ran the fixed jar, the eviction interleave never occurred and the dedicated counter
``evictedSetDescriptorsRebuilt`` stayed 0. This harness is the deterministic half of
the leg that closes that gap. The game runs themselves are driven on the PC fixture
by the procedure in alpha-item705-prep.md; this script owns the parts that must not
be eyeballed:

  plan        print the pre-registered run matrix (arms, jars, sha256 pins, args)
  install-jar copy an arm jar over the celtium jar in a private pack copy, sha-checked
  set-args    force the eviction lever into the copy's relauncher.json args
  judge       read one finished run directory and emit its per-run verdict JSON
  verdict     aggregate per-run JSONs into the item-705 verdict

Forcing design: ``-Dceltium.vulkan.descriptorPoolSets=4`` lowers
``VulkanImmediateTexturePipeline.DESCRIPTOR_CACHE_LIMIT`` from 64 to 4, and the Item
pipeline shares that constant, so both forceable families evict their eldest cached
set after a handful of distinct texture keys. Block/Particle keep their own limit of
2048 and are out of forcing reach; the counter is shared across all four families
(ImmediateStatistics.noteEvictedSetDescriptorsRebuilt, called from the single rebuild
site in drawPendingImmediate), so a rise proves the class, not one family - which is
what item 705 asks for. The counter is read from the LAST ``[Celtium] CELTIUM_VERIFY``
line of the run's console log, the established acceptance convention.

Pre-registered judgment (fixed before any run; attempts are not re-rolled):
  arm A (v0.3.1, pre-fix): up to 3 runs, stop early on the first signature crash.
  arm B (v0.3.2, fixed):   3 runs; any signature crash fails the leg outright.
  PROVEN     arm B has evicted >= 1 in at least one completed run, no arm B crash,
             and arm A produced the signature crash within its attempts.
  FIRED      arm B has evicted >= 1 and no arm B crash, but arm A never crashed.
  NOT_FIRED  arm B completed its runs with evicted == 0 everywhere; the record must
             then identify why (see the checklist in alpha-item705-prep.md).
  FAILED     arm B crashed with the signature even once.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import re
import shutil
import sys
from pathlib import Path

JAR_BEFORE = {
    "arm": "A",
    "label": "v0.3.1 (pre-fix, shipped)",
    "sha256": "0c0c6a7573c27fb29e6004dd0b0dab6c26682bb756fff6433e59abe88453e74e",
}
JAR_AFTER = {
    "arm": "B",
    "label": "v0.3.2 (fixed, shipped)",
    "sha256": "f63ea89ccb56dc7191d0750da137b2868fcb602fb3a7e5feb17202bac71f03d3",
}
FORCED_ARGS = [
    "-Dceltium.vulkan.descriptorPoolSets=4",
    "-Dceltium.debug.logLinesPerTag=100000",
]
ATTEMPTS_A = 3
ATTEMPTS_B = 3
RUN_SECONDS_AFTER_LOGIN = 420

VERIFY_RE = re.compile(r"\[Celtium\] CELTIUM_VERIFY via=(\S+) (.*)")
EVICTED_RE = re.compile(r"evictedSetDescriptorsRebuilt=(\d+)")
STALE_RE = re.compile(r"staleGroupDescriptorsRebuilt=(\d+)")
PROBLEM_FRAME_RE = re.compile(r"# C\s+\[libvulkan_radeon\.so")
STACK_MARKERS = (
    "MinimapFBORenderer",
    "flushPendingBeforeGlDraw",
    "drawPendingImmediate",
    "VulkanImmediateTexturePipeline.draw",
)


def sha256_of(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as fh:
        for chunk in iter(lambda: fh.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def cmd_plan(_args: argparse.Namespace) -> int:
    matrix = {
        "item": 705,
        "scene": "terrain2 private copy (Nostalgic Hardcore), entry (24849.5, 200, 34436.5), Xaero minimap live",
        "run_seconds_after_login": RUN_SECONDS_AFTER_LOGIN,
        "forced_args": FORCED_ARGS,
        "arms": [
            {**JAR_BEFORE, "attempts": ATTEMPTS_A, "stop_early": "first signature crash"},
            {**JAR_AFTER, "attempts": ATTEMPTS_B, "stop_early": "any signature crash fails the leg"},
        ],
        "read": "last [Celtium] CELTIUM_VERIFY line: evictedSetDescriptorsRebuilt, staleGroupDescriptorsRebuilt",
        "crash_signature": "new hs_err_pid*.log with SIGSEGV, Problematic frame in libvulkan_radeon.so, "
        "and at least one stack marker of " + ", ".join(STACK_MARKERS),
    }
    print(json.dumps(matrix, indent=2, ensure_ascii=False))
    return 0


def cmd_install_jar(args: argparse.Namespace) -> int:
    pin = JAR_BEFORE if args.arm == "A" else JAR_AFTER
    src = Path(args.jar)
    digest = sha256_of(src)
    if digest != pin["sha256"]:
        print(f"REFUSED: {src} sha256 {digest} != pinned {pin['label']} {pin['sha256']}", file=sys.stderr)
        return 2
    mods = Path(args.mods_dir)
    targets = sorted(mods.glob("celtium-*.jar"))
    if len(targets) != 1:
        print(f"REFUSED: expected exactly one celtium-*.jar in {mods}, found {[t.name for t in targets]}",
              file=sys.stderr)
        return 2
    shutil.copyfile(src, targets[0])
    placed = sha256_of(targets[0])
    if placed != pin["sha256"]:
        print(f"REFUSED: placed jar sha256 {placed} != pin", file=sys.stderr)
        return 2
    print(f"installed arm {args.arm} ({pin['label']}) as {targets[0].name} sha256={placed}")
    return 0


def cmd_set_args(args: argparse.Namespace) -> int:
    path = Path(args.relauncher_json)
    data = json.loads(path.read_text(encoding="utf-8"))
    current = list(data.get("args", []))
    kept = [a for a in current if not any(a.startswith(f.split("=")[0] + "=") for f in FORCED_ARGS)]
    data["args"] = kept + FORCED_ARGS
    path.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print("relauncher args now: " + " ".join(data["args"]))
    return 0


def parse_verify(log_text: str) -> tuple[int, dict | None]:
    """Return (number of CELTIUM_VERIFY lines, counters from the LAST one)."""
    lines = [m for m in (VERIFY_RE.search(line) for line in log_text.splitlines()) if m]
    if not lines:
        return 0, None
    tail = lines[-1].group(2)
    evicted = EVICTED_RE.search(tail)
    stale = STALE_RE.search(tail)
    if evicted is None:
        return len(lines), None
    return len(lines), {
        "via": lines[-1].group(1),
        "evictedSetDescriptorsRebuilt": int(evicted.group(1)),
        "staleGroupDescriptorsRebuilt": int(stale.group(1)) if stale else None,
    }


def crash_signature(run_dir: Path) -> tuple[bool, str | None]:
    """A run directory holds only the hs_err files that were NEW in that run."""
    for hs in sorted(run_dir.glob("hs_err_pid*.log")):
        text = hs.read_text(encoding="utf-8", errors="replace")
        if "SIGSEGV" not in text or "Problematic frame:" not in text:
            continue
        if not PROBLEM_FRAME_RE.search(text):
            continue
        marker = next((m for m in STACK_MARKERS if m in text), None)
        if marker is not None:
            return True, f"{hs.name} marker={marker}"
    return False, None


def cmd_judge(args: argparse.Namespace) -> int:
    run_dir = Path(args.run_dir)
    meta_path = run_dir / "run.json"
    meta = json.loads(meta_path.read_text(encoding="utf-8")) if meta_path.exists() else {}
    log_path = Path(args.log) if args.log else run_dir / "console.log"
    log_text = log_path.read_text(encoding="utf-8", errors="replace") if log_path.exists() else ""
    verify_count, counters = parse_verify(log_text)
    crashed, evidence = crash_signature(run_dir)
    result = {
        "arm": meta.get("arm"),
        "attempt": meta.get("attempt"),
        "login_seen": meta.get("login_seen"),
        "exit_code": meta.get("exit_code"),
        "verify_lines": verify_count,
        "counters": counters,
        "crash_signature": crashed,
        "crash_evidence": evidence,
    }
    out = json.dumps(result, indent=2, ensure_ascii=False)
    (run_dir / "judged.json").write_text(out + "\n", encoding="utf-8")
    print(out)
    return 0


def cmd_verdict(args: argparse.Namespace) -> int:
    runs = [json.loads(Path(p).read_text(encoding="utf-8")) for p in args.judged]
    arm_a = [r for r in runs if r.get("arm") == "A"]
    arm_b = [r for r in runs if r.get("arm") == "B"]
    a_crashed = any(r["crash_signature"] for r in arm_a)
    b_crashed = any(r["crash_signature"] for r in arm_b)
    b_fired = any(
        (r.get("counters") or {}).get("evictedSetDescriptorsRebuilt", 0) >= 1 for r in arm_b
    )
    b_completed = [r for r in arm_b if not r["crash_signature"]]
    if b_crashed:
        verdict = "FAILED"
        detail = "the fixed arm crashed with the flush-boundary signature; item 705 reopens as a defect"
    elif b_fired and a_crashed:
        verdict = "PROVEN"
        detail = "counter rose on the fixed jar and the pre-fix jar crashed under the same forced conditions"
    elif b_fired:
        verdict = "FIRED"
        detail = "counter rose on the fixed jar; the pre-fix crash did not reproduce within the registered attempts"
    elif b_completed:
        verdict = "NOT_FIRED"
        detail = "fixed arm completed with evictedSetDescriptorsRebuilt == 0; identify why per the record checklist"
    else:
        verdict = "INCOMPLETE"
        detail = "no completed arm B run was judged"
    out = {
        "verdict": verdict,
        "detail": detail,
        "arm_a_runs": len(arm_a),
        "arm_a_crashed": a_crashed,
        "arm_b_runs": len(arm_b),
        "arm_b_crashed": b_crashed,
        "arm_b_fired": b_fired,
        "arm_b_max_evicted": max(
            ((r.get("counters") or {}).get("evictedSetDescriptorsRebuilt", 0) for r in arm_b),
            default=0,
        ),
    }
    print(json.dumps(out, indent=2, ensure_ascii=False))
    return 0


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="command", required=True)

    sub.add_parser("plan").set_defaults(func=cmd_plan)

    p = sub.add_parser("install-jar")
    p.add_argument("--arm", choices=("A", "B"), required=True)
    p.add_argument("--jar", required=True)
    p.add_argument("--mods-dir", required=True)
    p.set_defaults(func=cmd_install_jar)

    p = sub.add_parser("set-args")
    p.add_argument("--relauncher-json", required=True)
    p.set_defaults(func=cmd_set_args)

    p = sub.add_parser("judge")
    p.add_argument("--run-dir", required=True)
    p.add_argument("--log")
    p.set_defaults(func=cmd_judge)

    p = sub.add_parser("verdict")
    p.add_argument("judged", nargs="+")
    p.set_defaults(func=cmd_verdict)

    args = parser.parse_args(argv)
    return args.func(args)


if __name__ == "__main__":
    raise SystemExit(main())
