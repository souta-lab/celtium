#!/usr/bin/env python3
"""Check that a re-asserting main menu cannot keep the fixture out of the world.

The fixture has to clear whatever screen stands between it and the world. The first version of that cleared the
screen once, set a flag so it would not repeat, and let a later tick observe the cleared screen and launch.

That assumes the screen stays cleared. Measured on the 268-mod E2E pack, which ships CustomMainMenu: the screen was
still `lumien.custommainmenu.gui.GuiCustom` at `clientTicks=1200` with `worldTicks=0`. The dismissal had fired, the
flag was spent, the menu put its screen back, and the world was never launched for the whole leg.

So the two properties this checks are the two that were wrong: the dismissal must be **repeatable**, and the launch
must be allowed **on the same tick** as the dismissal rather than waiting for a later tick to see a clear screen.
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

    start = src.index("A mod may put its own screen up before the title screen")
    block = src[start : src.index("if (!launched && (menuOrCleared", start)]

    # The dismissal condition must not be gated on the one-shot flag: a menu that re-asserts its screen would
    # otherwise win on the second tick.
    dismiss_cond = block[block.index("if (!launched && mc.world == null") :]
    dismiss_cond = dismiss_cond[: dismiss_cond.index("{")]
    results.append(check("the dismissal is not gated on the one-shot flag",
                         "!celtium$foreignScreenDismissed" in dismiss_cond, False))

    # The flag may still exist to report the first screen's name once, but it must not gate the clearing.
    results.append(check("the first screen is still named once",
                         "report.addProperty(\"screenDismissedBeforeWorld\"" in block, True))

    # The count must be reported, so a leg says how many times it took rather than only that it happened.
    results.append(check("the dismissal count is reported",
                         "screenDismissalsBeforeWorld" in block, True))
    results.append(check("the count is incremented", "celtium$foreignScreenDismissals++" in block, True))

    # The launch must be allowed on the same tick. Waiting for a later tick is what let the menu win.
    results.append(check("the launch is allowed on the same tick", "menuOrCleared = true;" in block, True))
    same_tick = block.index("menuOrCleared = true;")
    clearing = block.index("displayGuiScreen")
    results.append(check("the same-tick allowance follows the clearing", clearing < same_tick, True))

    # And the launch condition must accept it.
    results.append(check("the launch accepts it", "if (!launched && (menuOrCleared" in src, True))

    # The screen must still be cleared, not merely recorded.
    results.append(check("the screen is actually cleared",
                         "mc.displayGuiScreen((net.minecraft.client.gui.GuiScreen) null)" in block, True))

    failed = results.count(False)
    print(f"\nrepeatable dismissal contract: {len(results) - failed}/{len(results)} checks passed")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
