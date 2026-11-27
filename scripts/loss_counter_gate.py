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
  printed on their own line and read by nobody).

What makes the evidence unproven rather than failed: no verification line at all, a
verification line without the aggregate (a jar from before the reader wiring), or the log
limiter having suppressed the CELTIUM_VERIFY tag itself - then the last visible line may be
stale, so the run proves nothing about losses either way.

Report-only: the present line's skip aggregate (warmup skips are legitimate) and the limiter's
overall suppression count.
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
