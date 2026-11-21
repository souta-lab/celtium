#!/usr/bin/env python3
"""Check the addon connection surface ledger II against the mixin tree.

Item 378 generalised the surface addons run on instead of reimplementing one addon: celtium's fast
paths sit inside vanilla's high-level queries, and celtium never replaces an instruction point an
addon targets. Ledger II (the vanilla instruction points, written out in
`docs/mod-compatibility.md`) is the contract; `LEDGER_II` below is its single machine-readable
source, and this checker keeps the three representations of it - this list, the docs table and the
mixin tree - from drifting apart:

1. **No replacing injection on a ledger point.** A HEAD-cancelling `@Inject`, a `@WrapOperation`,
   a `@Redirect`, an `@Overwrite`, a `@WrapWithCondition`, or a value-rewriting
   `@ModifyReturnValue` / `@ModifyExpressionValue` / `@ModifyVariable` landing on a point marked
   `presence="none"` (or `"call"`) silently disables an addon injecting there - its injection
   applies but never runs, or its argument edit is overwritten. That failure mode is what item 378
   removed; this check keeps it out. For the `getCombinedLight` call site (II-4) `@ModifyArg`/
   `@ModifyArgs` count as replacing too: editing the call's arguments contests the same
   instruction point.
2. **Sanctioned presences exist as declared.** II-1 is a plain call in `LightDataCache` (no mixin
   on the method), II-6 is the one `@Overwrite` celtium keeps (the method itself must exist for
   addons calling it), II-7 is the funnel: exactly one `@WrapMethod` on `World.getCombinedLight`,
   in `WorldCombinedLightMixin`, and no other replacing injection on that method anywhere.
3. **Explicit `require` on ledger points (rule R1).** Any `@Inject`/`@WrapOperation`/`@Redirect`
   landing on a ledger point must declare `require` explicitly, so a renamed target fails as a
   deliberate decision, not as a silent no-op or a startup crash chosen by a default.
4. **The docs table matches this list.** Every ledger row appears in `docs/mod-compatibility.md`
   with its point named, and no row exists that this list does not know.

Exit status is 0 when clean and 1 when a problem is reported, like `scripts/check_patch_groups.py`.
"""
from __future__ import annotations

from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parent.parent
MIXIN_DIR = Path("forge122/src/main/java/org/taumc/celeritas/mixin")
DOCS = Path("docs/mod-compatibility.md")
LIGHT_DATA_CACHE = Path(
    "forge122/src/main/java/org/taumc/celeritas/impl/render/terrain/compile/light/LightDataCache.java")
RENDER_GLOBAL = MIXIN_DIR / "core/terrain/RenderGlobalMixin.java"
FUNNEL = MIXIN_DIR / "core/WorldCombinedLightMixin.java"

# The single machine-readable source of ledger II. `names` are the method identifiers an injection
# can land on (SRG names as written in this tree, plus the MCP name where the tree uses it);
# `presence` is the shape celtium itself keeps on the point.
LEDGER_II = [
    {"id": "II-1", "doc_token": "getPackedLightmapCoords",
     "names": ("getPackedLightmapCoords", "func_185484_c"), "presence": "call"},
    {"id": "II-2", "doc_token": "Entity.getBrightnessForRender",
     "names": ("func_70070_b", "getBrightnessForRender"), "presence": "none"},
    {"id": "II-3", "doc_token": "Particle.getBrightnessForRender",
     "names": ("func_189214_a", "getBrightnessForRender"), "presence": "none"},
    {"id": "II-4", "doc_token": "getCombinedLight",
     "names": (), "call_targets": ("getCombinedLight", "func_175626_b"), "presence": "none"},
    {"id": "II-5", "doc_token": "ItemRenderer.setLightmap",
     "names": ("setLightmap", "func_77008_a", "func_177105_a", "func_187464_b"),
     "mixin_target": "ItemRenderer", "presence": "none"},
    {"id": "II-6", "doc_token": "markBlocksForUpdate",
     "names": ("markBlocksForUpdate", "func_184385_a"), "presence": "overwrite"},
    {"id": "II-7", "doc_token": "World.getCombinedLight",
     "names": ("func_175626_b", "getCombinedLight"), "presence": "funnel"},
]

ANNOTATION = re.compile(r"@(Inject|WrapOperation|Redirect|Overwrite|WrapMethod|ModifyArg|ModifyArgs"
                        r"|ModifyReturnValue|ModifyExpressionValue|ModifyVariable|WrapWithCondition)\b")
STRING = re.compile(r'"((?:[^"\\]|\\.)*)"')
REPLACING = {"WrapOperation", "Redirect", "Overwrite", "WrapWithCondition",
             "ModifyReturnValue", "ModifyExpressionValue", "ModifyVariable"}
CALL_SITE_REPLACING = REPLACING | {"ModifyArg", "ModifyArgs"}


def strip_comments(text):
    """Return `text` with comments blanked (string and char literals kept: annotation args live there)."""
    out = []
    index = 0
    length = len(text)
    while index < length:
        char = text[index]
        if char in ('"', "'"):
            end = index + 1
            while end < length:
                if text[end] == "\\":
                    end += 2
                    continue
                if text[end] == char:
                    end += 1
                    break
                end += 1
            out.append(text[index:end])
            index = end
        elif text.startswith("//", index):
            end = text.find("\n", index)
            end = length if end < 0 else end
            out.append(" " * (end - index))
            index = end
        elif text.startswith("/*", index):
            end = text.find("*/", index + 2)
            end = length if end < 0 else end + 2
            out.append(" " * (end - index))
            index = end
        else:
            out.append(char)
            index += 1
    return "".join(out)


def _balanced(text, open_index):
    """Return the text inside the parenthesis at `open_index`, skipping string literals."""
    depth = 0
    index = open_index
    length = len(text)
    while index < length:
        char = text[index]
        if char in ('"', "'"):
            quote = char
            index += 1
            while index < length:
                if text[index] == "\\":
                    index += 2
                    continue
                if text[index] == quote:
                    break
                index += 1
        elif char == "(":
            depth += 1
        elif char == ")":
            depth -= 1
            if depth == 0:
                return text[open_index + 1:index]
        index += 1
    return None


def _attribute(arguments, name):
    """Return the string literals of `name = ...` inside an annotation's argument text."""
    match = re.search(r"(?<![\w$])" + re.escape(name) + r"\s*=", arguments)
    if match is None:
        return []
    rest = arguments[match.end():]
    if rest.lstrip().startswith("{"):
        brace = rest.index("{")
        depth = 0
        collected = []
        index = brace
        while index < len(rest):
            char = rest[index]
            if char == "{":
                depth += 1
            elif char == "}":
                depth -= 1
                if depth == 0:
                    collected.append(rest[brace:index + 1])
                    break
            index += 1
        return STRING.findall(collected[0]) if collected else []
    literal = STRING.search(rest)
    return [literal.group(1)] if literal else []


def find_injections(code):
    """Return the injections in one mixin source: dicts with kind/methods/targets/cancellable/require."""
    injections = []
    for match in ANNOTATION.finditer(code):
        kind = match.group(1)
        arguments = ""
        index = match.end()
        while index < len(code) and code[index] in " \t\r\n":
            index += 1
        if index < len(code) and code[index] == "(":
            arguments = _balanced(code, index) or ""
        tail = code[match.end():match.end() + 400]
        declared = None
        if kind == "Overwrite":
            # @Overwrite names its target by the declared method that follows it, not by an attribute.
            declared_match = re.search(r"\b(\w+)\s*\(", tail)
            declared = declared_match.group(1) if declared_match else None
        injections.append({
            "kind": kind,
            "methods": _attribute(arguments, "method"),
            "targets": _attribute(arguments, "target"),
            "cancellable": re.search(r"cancellable\s*=\s*true", arguments) is not None,
            "require": re.search(r"(?<![\w$])require\s*=", arguments) is not None,
            "arguments": arguments,
            "tail": tail,
            "declared": declared,
        })
    return injections


def _method_matches(injection, names):
    if any(any(name in method for name in names) for method in injection["methods"]):
        return True
    declared = injection.get("declared")
    return declared is not None and any(name == declared for name in names)


def _is_replacing(injection, call_site=False):
    if injection["kind"] in (CALL_SITE_REPLACING if call_site else REPLACING):
        return True
    return injection["kind"] == "Inject" and injection["cancellable"]


def check(root=ROOT):
    """Return the list of problems for the tree below `root` (empty when the surface holds)."""
    root = Path(root)
    problems = []
    base = root / MIXIN_DIR
    files = {}
    if base.is_dir():
        for path in sorted(base.rglob("*.java")):
            files[path.relative_to(root)] = strip_comments(
                path.read_text(encoding="utf-8", errors="replace"))
    if not files:
        problems.append(f"{MIXIN_DIR}: no mixin sources found (check the path before trusting a clean result)")

    parsed = {path: find_injections(code) for path, code in files.items()}

    for point in LEDGER_II:
        pid = point["id"]
        names = point.get("names", ())
        for path, injections in parsed.items():
            for injection in injections:
                on_point = _method_matches(injection, names) if names else False
                if point.get("call_targets"):
                    on_point = any(form in target for form in point["call_targets"]
                                   for target in injection["targets"])
                if point.get("mixin_target") and point["mixin_target"] not in files[path]:
                    on_point = False
                if not on_point:
                    continue
                sanctioned = (
                    (pid == "II-6" and injection["kind"] == "Overwrite" and path == RENDER_GLOBAL)
                    or (pid == "II-7" and injection["kind"] == "WrapMethod" and path == FUNNEL))
                if _is_replacing(injection, call_site=bool(point.get("call_targets"))) and not sanctioned:
                    problems.append(
                        f"{path}: @{injection['kind']} replaces {pid} ({point['doc_token']}), a ledger II "
                        "point an addon may target; celtium keeps no replacing injection there (item 378, R2)")
                if injection["kind"] in ("Inject", "WrapOperation", "Redirect") and not injection["require"]:
                    problems.append(
                        f"{path}: @{injection['kind']} lands on {pid} ({point['doc_token']}) without an "
                        "explicit require; injections on ledger II points declare require (item 378, R1)")

    cache_path = root / LIGHT_DATA_CACHE
    if not cache_path.is_file() or "getPackedLightmapCoords(" not in strip_comments(
            cache_path.read_text(encoding="utf-8", errors="replace")):
        problems.append(
            f"{LIGHT_DATA_CACHE}: II-1 expects the packed-lightmap lookup to stay a plain call here; "
            "the call is gone, so the terrain light path no longer reads the vanilla method")

    render_global = files.get(RENDER_GLOBAL)
    if render_global is None or not any(
            injection["kind"] == "Overwrite" and injection.get("declared") == "markBlocksForUpdate"
            for injection in parsed.get(RENDER_GLOBAL, [])):
        problems.append(
            f"{RENDER_GLOBAL}: II-6 expects celtium's @Overwrite of markBlocksForUpdate to stay in "
            "place (the method itself must exist for addons calling it); it is not there")

    funnel = files.get(FUNNEL)
    if funnel is None or not any(
            injection["kind"] == "WrapMethod"
            and _method_matches(injection, ("func_175626_b", "getCombinedLight"))
            for injection in parsed.get(FUNNEL, [])):
        problems.append(
            f"{FUNNEL}: II-7 expects the funnel - one @WrapMethod on World.getCombinedLight "
            "(func_175626_b, or the MCP name getCombinedLight) serving CeltiumLightCache inside "
            "the method; it is not there")

    docs_path = root / DOCS
    if not docs_path.is_file():
        problems.append(f"{DOCS}: missing; the ledger II table lives there")
    else:
        rows = {}
        for line in docs_path.read_text(encoding="utf-8", errors="replace").splitlines():
            match = re.match(r"\|\s*(II-\d)\s*\|(.*)", line)
            if match:
                rows[match.group(1)] = match.group(2)
        for point in LEDGER_II:
            row = rows.pop(point["id"], None)
            if row is None:
                problems.append(f"{DOCS}: ledger II row {point['id']} is missing from the docs table")
            elif point["doc_token"] not in row:
                problems.append(
                    f"{DOCS}: ledger II row {point['id']} does not name {point['doc_token']}; "
                    "the docs table and the checker list must stay one source (item 378, R3)")
        for leftover in rows:
            problems.append(f"{DOCS}: ledger II row {leftover} is not in the checker's list; "
                            "add it to LEDGER_II in scripts/check_addon_surface.py first")
    return problems


def main():
    problems = check()
    for problem in problems:
        print(f"FAIL: {problem}", file=sys.stderr)
    if problems:
        return 1
    print(f"Addon surface: ledger II holds ({len(LEDGER_II)} points) - no replacing injection on a "
          "ledger point, sanctioned presences in place, docs table in sync.")
    print("This does not prove an addon runs; it proves celtium's side of the surface did not drift. "
          "Whether an addon works is the acceptance leg's measurement, not this check's.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
