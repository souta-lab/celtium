#!/usr/bin/env python3
"""Check maintained entry docs, not historical claims or renderer acceptance.

Catch misspelled executable -D keys, broken local links, stale generated index,
and accidental growth of the repeatedly-read instruction/state documents.
"""
from __future__ import annotations

import importlib.util
from pathlib import Path
import re
import sys
from urllib.parse import unquote, urlsplit

ROOT = Path(__file__).resolve().parent.parent
MAINTAINED = (
    "AGENTS.md", "README.md", "docs/goal.md", "docs/plan.md",
    "docs/development.md", "docs/verification-traps.md", "docs/instruments.md",
    ".agents/skills/celtium-verify/SKILL.md",
)
# Byte budgets prevent returning to megabyte entrypoints; they are not token counts.
BUDGETS = {"AGENTS.md": 10 * 1024, "docs/goal.md": 8 * 1024, "docs/plan.md": 16 * 1024}
FLAG = re.compile(r"-D((?:celtium|celeritas)\.[A-Za-z0-9_-]+(?:\.[A-Za-z0-9_-]+)*)(?=[=\s`])")
LINK = re.compile(r"\[[^\]\n]*\]\((?:<([^>\n]+)>|([^\s)]+))\)")
LIVE_ID = re.compile(r"goal-[0-9a-f]{8}-[0-9a-f-]{27,}")


def load_index():
    spec = importlib.util.spec_from_file_location("instrument_index", ROOT / "scripts/instrument-index.py")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def property_problems(text, keys):
    return [f"unknown executable JVM property: {name}" for name in sorted(set(FLAG.findall(text)) - set(keys))]


def link_problems(path, text):
    problems = []
    for match in LINK.finditer(text):
        target = match.group(1) or match.group(2)
        parts = urlsplit(target)
        if parts.scheme or parts.netloc or not parts.path:
            continue
        resolved = path.parent / unquote(parts.path)
        if not resolved.exists():
            problems.append(f"broken local link: {target}")
    return problems


def check(root=ROOT):
    root = Path(root)
    index = load_index()
    props, markers = index.collect()
    problems = []
    for rel in MAINTAINED:
        path = root / rel
        if not path.is_file():
            problems.append(f"{rel}: missing maintained document")
            continue
        text = path.read_text(encoding="utf-8")
        for problem in property_problems(text, props) + link_problems(path, text):
            problems.append(f"{rel}: {problem}")
        if rel in BUDGETS and path.stat().st_size > BUDGETS[rel]:
            problems.append(f"{rel}: {path.stat().st_size} bytes exceeds entrypoint budget {BUDGETS[rel]}")
        if rel == "docs/goal.md":
            if LIVE_ID.search(text):
                problems.append(f"{rel}: session Goal UUID belongs in the goal API, not the product specification")
            if re.search(r"(?m)^## (?:最終指示|聖典)", text):
                problems.append(f"{rel}: archived execution directives leaked into the product goal")
    generated = root / "docs/instruments.md"
    if generated.exists() and generated.read_text(encoding="utf-8") != index.render(props, markers):
        problems.append("docs/instruments.md: regenerate with python3 scripts/instrument-index.py")
    return problems


def main():
    problems = check()
    for problem in problems:
        print(f"FAIL: {problem}", file=sys.stderr)
    if problems:
        return 1
    print("Agent docs: executable flags, local links, entrypoint budgets and index freshness OK.")
    print("This does not validate historical assertions, semantics, GPU results or performance.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
