#!/usr/bin/env python3
"""Check that the fixture can answer "did it tick?" from the log alone.

This exists because it could not. Every other fact the tick handler produces is a report property, and the report
is written at shutdown, so a run killed by its time limit reports nothing at all - and the absence of those
properties was then read as "the handler never ran". That reading was drawn from a 268-mod leg and it was wrong:
`exitCode 143` with `MISSING_REPORT` says how the run ended, not what the handler did.

So the check is that the liveness line is emitted early enough to survive a killed run, and that it carries the
fields needed to tell the two cases apart - the handler never ran versus the handler ran and the world never
launched.
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java"


def check(name: str, got, want) -> bool:
    ok = got == want
    print(f"{'ok  ' if ok else 'FAIL'} {name}: got {got!r} want {want!r}")
    return ok


def main() -> int:
    src = SOURCE.read_text(encoding="utf-8")
    results = []

    tick = src[src.index("public void tick(TickEvent.ClientTickEvent event)"):]
    tick = tick[: tick.index("\n    }")]

    m = re.search(r'System\.out\.println\("\[Celtium\] (CELTIUM_FIXTURE_TICK)[^"]*"', tick)
    results.append(check("the tick handler logs a liveness line", m.group(1) if m else None, "CELTIUM_FIXTURE_TICK"))

    # It must fire on the first tick: a run killed at any point after that still says the handler ran.
    results.append(check("it fires on the first tick", "celtium$clientTicks == 1" in tick, True))

    # And periodically, so a long run shows it was still alive rather than only that it started.
    results.append(check("it fires periodically", re.search(r"celtium\$clientTicks % \d+ == 0", tick) is not None, True))

    # The fields must separate "never ticked" from "ticked but no world".
    for field in ("clientTicks=", "worldTicks=", "screen=", "world="):
        results.append(check(f"the line carries {field}", field in tick, True))

    # It must be inside the handler, after the early return, so it cannot fire when the fixture is disabled.
    guard = tick.index("if (!enabled || finished")
    emit = tick.index("CELTIUM_FIXTURE_TICK")
    results.append(check("it is after the enabled guard", guard < emit, True))

    # The counter it reports must be the one it increments, not a second one.
    results.append(check("it reports the counter the handler increments", "celtium$clientTicks++" in tick[:emit], True))

    # The render-tick handler must be instrumented per phase: the 268-mod leg never reached the END half, and
    # "event not delivered" versus "only START delivered" is exactly the distinction that needs to be readable.
    frame = src[src.index("public void frame(TickEvent.RenderTickEvent event)"):]
    frame = frame[: frame.index("\n    }")]
    results.append(check("the frame handler logs START", "CELTIUM_FIXTURE_FRAME phase=START" in frame, True))
    results.append(check("the frame handler logs END", "CELTIUM_FIXTURE_FRAME phase=END" in frame, True))
    # START must be logged before its early return, or it would never be reached.
    start_ret = frame.index("celtium$applyClientPoseOverride")
    results.append(check("START is logged before the early return", frame.index("phase=START") < start_ret, True))
    # And END before the call whose absence started this: an END line that came after it would prove nothing.
    results.append(check("END is logged before completePendingDumps",
                         frame.index("phase=END") < frame.index("celtium$completePendingDumps()"), True))

    failed = results.count(False)
    print(f"\nfixture liveness contract: {len(results) - failed}/{len(results)} checks passed")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
