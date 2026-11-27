#!/usr/bin/env python3
"""Check that every startup refusal in CeltiumCompetitors.KNOWN is documented in both compat docs.

Extracted from the retired `scripts/check_addon_surface.py` (stage 0 addon-surface abolition,
decision D-87): Ledger II/III addon-surface checks were removed, while startup-refusal drift
checking against `docs/mod-compatibility.md` and `notworkingmods.md` stays active.

Exit status is 0 when clean and 1 when a problem is reported.
"""
from __future__ import annotations

from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parent.parent
COMPETITORS = Path("forge122/src/main/java/org/taumc/celeritas/core/CeltiumCompetitors.java")
REFUSAL_DOCS = (Path("docs/mod-compatibility.md"), Path("notworkingmods.md"))


def check_refusals(root=ROOT):
    """Return the problems with the startup-refusal tables (code list against both docs)."""
    root = Path(root)
    problems = []
    source = root / COMPETITORS
    if not source.is_file():
        return [f"{COMPETITORS}: missing; the refusal list lives there"]
    names = [entry.split(" (")[0].strip() for entry in re.findall(
        r'\{\s*"[^"]+\.class"\s*,\s*"([^"]+)"', source.read_text(encoding="utf-8", errors="replace"))]
    if not names:
        return [f"{COMPETITORS}: no KNOWN entries parsed (the checker's pattern no longer matches the table)"]
    for relative in REFUSAL_DOCS:
        path = root / relative
        if not path.is_file():
            problems.append(f"{relative}: missing; it must list the startup refusals")
            continue
        text = path.read_text(encoding="utf-8", errors="replace").lower()
        for name in names:
            if name.lower() not in text:
                problems.append(f"{relative}: does not name {name}, which {COMPETITORS.name} refuses at startup")
    return problems


def main():
    problems = check_refusals()
    for problem in problems:
        print(f"FAIL: {problem}", file=sys.stderr)
    if problems:
        return 1
    print("Competitor refusals: the startup-refusal list in CeltiumCompetitors.KNOWN matches both docs.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
