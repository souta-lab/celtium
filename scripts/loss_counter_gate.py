#!/usr/bin/env python3
"""Gate a leg's log on the loss counters that used to be counted and never read.

Drawpath audit X-1 measured the structure: the verification line carries hundreds of keys, the
leg scripts read none of the loss counters, and the line itself can be cut by the per-tag log
limiter - so a run could lose geometry, stall the device or refuse native passes and still pass
every gate. This module is the reader. It is a pure function over log text so the unit tests are
its script-level positive controls; the in-game positive controls (a forced drop, a forced
stall) ride the confirmation-leg bundle.

What fails a leg:

- the verification line's loss aggregate total is nonzero (post-accept drops, ordering-flush
  failures and their dropped batches, stalled-submission geometry drops, unflushed target
  groups, failed window flushes, stale-group refusals - each zero on a healthy run);
- ``gpuStalled=true`` on the verification line;
- ``swapPointExceptions`` (the present bundle's key) is nonzero when present;
- the batch route's dropped draws / discarded segments are nonzero, read from the last
  CELTIUM_BATCH_ROUTE line or from the verification line when the batch bundle's promotion
  has landed - whichever seat a given jar prints;
- the present heartbeat's failures / drain failures / unfinished drains are nonzero;
- the native-pass census's compile / execution refusals are nonzero (DP-37: the counts were
  printed on their own line and read by nobody);
- the follow-up wiring (landing checklist section 5): the loss keys the other drawpath
  bundles added to the verification line after this gate's first form - the post-chain
  bridge's refusals and creation failures, the chain-input / framebuffer-hint flush
  failures, the native compositor's refusals, the mod-program refusals (total and the
  per-reason bracket sum), the native-pass refusal counts promoted onto the verification
  line, the stage gate's failure / trip sums, the mixin-side silent-loss censuses
  (celtium/offerDeaths, celtium/nativeDrawSkips, celtium/noCameraSkips - bracket sums,
  read beside the backend's own offerRefusals, never confused with it), the model route's
  dropped runs / failed instances / missing meshes, the font retention drops (retained
  runs and quads dropped, boundary discards included), the pack terrain route's refusals
  and skipped regions, the off-target redirect count, and the older totalPostAcceptDrops
  aggregate (its immediateGroupsDiscarded member is not in the loss aggregate, so the
  DP-01 merge reads both aggregates).

What makes the evidence unproven rather than failed: no verification line at all, a
verification line without the aggregate (a jar from before the reader wiring), or the log
limiter having suppressed the CELTIUM_VERIFY tag itself - then the last visible line may be
stale, so the run proves nothing about losses either way.

Report-only: the present line's skip aggregate (warmup skips are legitimate), the limiter's
overall suppression count, and the font retention census (fontRetainedRuns / Quads and the
reoffered pair count work retained, not work lost) with the legacy font window suppression
count (a by-design suppression whenever a font window is active).
"""
from __future__ import annotations

import re

_VERIFY = "CELTIUM_VERIFY"
_PRESENT = "CELTIUM_PRESENT"
_NATIVE_PASS = "CELTIUM_NATIVE_PASS"
_BATCH_ROUTE = "CELTIUM_BATCH_ROUTE"
_LOG_LIMIT = "CELTIUM_LOG_LIMIT"

_AGGREGATE = re.compile(r"lossAggregate=\[[^\]]*?total=(\d+)\]")
_SUPPRESSED_BY_TAG = re.compile(r"logLimitSuppressedByTag=\[([^\]]*)\]")
_LIMIT_LINE = re.compile(r"CELTIUM_LOG_LIMIT suppressed (\d+) lines in (\d+) tags")


def _last_line(text: str, tag: str) -> str | None:
    found = None
    for line in text.splitlines():
        if tag in line:
            found = line
    return found


def _key(line: str, name: str) -> int | None:
    match = re.search(r"(?:^|[\s,\[])" + re.escape(name) + r"=(-?\d+)", line)
    return int(match.group(1)) if match else None


def _bracket_sum(line: str, name: str) -> int | None:
    """Sum the numeric name=value pairs inside a ``name[...]`` bracket on the line.

    The bracket opens directly after the name (``stageGate[...]``,
    ``celtium/offerDeaths[...]``); no equals sign is printed before it.
    """
    match = re.search(re.escape(name) + r"=?\[([^\]]*)\]", line)
    if match is None:
        return None
    return sum(int(value) for value in re.findall(r"[\w/]+?=(\d+)", match.group(1)))


# Follow-up wiring (landing checklist section 5): verification-line loss keys added by the
# other drawpath bundles. Each is zero on a healthy run; a nonzero value fails the leg.
_FAIL_SCALARS = (
    # L-X (DP-31/34/35/37): post-chain bridge, flush seats, compositor, mod programs,
    # and the native-pass refusal counts promoted onto the verification line.
    "postChainCreationFailures",
    "postChainUnsupportedRefusals", "chainInputFlushFailures", "framebufferHintFailures",
    "nativeCompositeRefusals", "modRefusals", "nativePassExecRefusals",
    "nativePassCompileRefusals",
    # L-M (DP-23/DP-11): model route losses and the font retention drops.
    "modelRunDropped", "modelInstancesFailed", "modelMissingMeshes",
    "fontRetainedDroppedRuns", "fontRetainedDroppedQuads",
    "fontRetainedBoundaryDroppedRuns", "fontRetainedBoundaryDroppedQuads",
    # L-T (DP-36): the pack terrain route's refusals and skipped regions.
    "packTerrainRefused", "packTerrainSkippedRegions",
    # L-B (DP-33): draws redirected off the bound target.
    "offTargetRedirects",
    # DP-01 merge: the older post-accept aggregate; its immediateGroupsDiscarded member
    # is not part of the loss aggregate, so both aggregates are read.
    "totalPostAcceptDrops",
)

_FAIL_BRACKETS = (
    # L-X: stageGate[stage=on failures=N trips=N ...] - the sum is failures + trips.
    "stageGate",
    # L-X: modRefusalsByReason[reason=N ...].
    "modRefusalsByReason",
    # L-F (DP-01 mixin side / DP-03 / DP-29): the silent-loss censuses.
    "celtium/offerDeaths", "celtium/nativeDrawSkips", "celtium/noCameraSkips",
)

# Read into the breakdown but never a failure: censuses of work retained or of a
# by-design suppression, nonzero on healthy runs.
_REPORT_SCALARS = (
    "fontRetainedRuns", "fontRetainedQuads",
    "fontRetainedReofferedRuns", "fontRetainedReofferedQuads",
    "legacyFontWindowSuppressed",
)


def check(text: str) -> tuple[list[str], dict]:
    """Return (problems, breakdown) for one leg log's full text."""
    problems: list[str] = []
    breakdown: dict = {}

    verify = _last_line(text, _VERIFY)
    if verify is None:
        problems.append("loss gate unproven: no CELTIUM_VERIFY line in the log")
        breakdown["verifyLine"] = False
        return problems, breakdown
    breakdown["verifyLine"] = True

    aggregate = _AGGREGATE.search(verify)
    if aggregate is None:
        problems.append("loss gate unproven: the CELTIUM_VERIFY line carries no lossAggregate "
                        "(jar predates the reader wiring, or the line was truncated)")
        breakdown["lossAggregateTotal"] = None
    else:
        total = int(aggregate.group(1))
        breakdown["lossAggregateTotal"] = total
        if total > 0:
            problems.append(f"loss aggregate is nonzero: lossAggregate total={total} "
                            f"in the last CELTIUM_VERIFY line")

    if "gpuStalled=true" in verify:
        breakdown["gpuStalled"] = True
        problems.append("gpuStalled=true in the last CELTIUM_VERIFY line")
    else:
        breakdown["gpuStalled"] = False

    swap_exceptions = _key(verify, "swapPointExceptions")
    if swap_exceptions is not None:
        breakdown["swapPointExceptions"] = swap_exceptions
        if swap_exceptions > 0:
            problems.append(f"swapPointExceptions={swap_exceptions} in the last CELTIUM_VERIFY line")

    batch_flagged = set()
    for name in ("batchDroppedDraws", "batchDiscardedSegments"):
        value = _key(verify, name)
        if value is not None:
            breakdown[name] = value
            if value > 0:
                batch_flagged.add(name)
                problems.append(f"{name}={value} in the last CELTIUM_VERIFY line")

    for name in _FAIL_SCALARS:
        value = _key(verify, name)
        if value is not None:
            breakdown[name] = value
            if value > 0:
                problems.append(f"{name}={value} in the last CELTIUM_VERIFY line")

    for name in _FAIL_BRACKETS:
        value = _bracket_sum(verify, name)
        if value is not None:
            breakdown[name + "Sum"] = value
            if value > 0:
                problems.append(f"{name} bracket sums to {value} "
                                f"in the last CELTIUM_VERIFY line")

    for name in _REPORT_SCALARS:
        value = _key(verify, name)
        if value is not None:
            breakdown[name] = value

    suppressed = _key(verify, "logLimitSuppressed")
    if suppressed is not None:
        breakdown["logLimitSuppressed"] = suppressed
    by_tag = _SUPPRESSED_BY_TAG.search(verify)
    verify_suppressed = bool(by_tag and "CELTIUM_VERIFY=" in by_tag.group(1))
    limit_line = _LIMIT_LINE.search(text)
    if limit_line is not None:
        breakdown["logLimitSuppressed"] = int(limit_line.group(1))
        tail = text[limit_line.end():].splitlines()[0] if text[limit_line.end():] else ""
        if "CELTIUM_VERIFY=" in tail:
            verify_suppressed = True
    breakdown["verifySuppressed"] = verify_suppressed
    if verify_suppressed:
        problems.append("loss gate unproven: the log limiter suppressed CELTIUM_VERIFY lines, "
                        "so the last visible verification line may be stale")

    present = _last_line(text, _PRESENT)
    if present is not None:
        for name, label in (("failures", "present failures"),
                            ("drainFailures", "present drain failures"),
                            ("drainUnfinished", "present drains unfinished")):
            value = _key(present, name)
            if value is not None:
                breakdown["present" + name[0].upper() + name[1:]] = value
                if value > 0:
                    problems.append(f"{label}: {name}={value} in the last CELTIUM_PRESENT line")
        skips = _key(present, "skips")
        if skips is not None:
            breakdown["presentSkips"] = skips

    native = _last_line(text, _NATIVE_PASS)
    if native is not None:
        for name in ("compileRefusals", "execRefusals"):
            value = _key(native, name)
            if value is not None:
                breakdown["native" + name[0].upper() + name[1:]] = value
                if value > 0:
                    problems.append(f"native pass {name}={value} in the last CELTIUM_NATIVE_PASS line")

    route = _last_line(text, _BATCH_ROUTE)
    if route is not None:
        for name, key in (("droppedDraws", "batchDroppedDraws"),
                          ("discardedSegments", "batchDiscardedSegments")):
            value = _key(route, name)
            if value is not None:
                if key not in breakdown or value > breakdown[key]:
                    breakdown[key] = value
                if value > 0 and key not in batch_flagged:
                    problems.append(f"batch route {name}={value} in the last CELTIUM_BATCH_ROUTE line")

    return problems, breakdown
