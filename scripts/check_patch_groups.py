#!/usr/bin/env python3
"""Check the patch-group table against the mixins that actually exist.

The table lives in `celtium-vulkan/.../patch/PatchGroups.java` (item 356 design, step 356-A). It decides which
mixin classes are registered together and therefore which group a persistent quarantine can name, so a table that
drifts from the source tree either hides a mixin from the gate or names a group member that no longer exists.
Three properties are checked, from two inputs (the Java table and the mixin source tree):

1. **Completeness** - every discovered mixin is listed exactly once. An unlisted mixin silently falls back to
   `mechanism` (the group that is never quarantined), so drift fails closed at runtime and is caught here.
2. **Existence** - every listed name is a mixin that exists. A stale entry would make a group look repairable
   when part of it is already gone.
3. **No simple-name addressing** - three simple names are already shared by two mixins each (`RenderItemMixin`,
   `TextureUtilMixin`, `TextureAtlasSpriteMixin`), so a member written as a bare simple name would file one of
   the pair under the other's group. The collision sets themselves are reported, not rejected.
4. **The JSON shadow list is pinned** - `mixins.celeritas.json`'s arrays hold exactly the two known shadow
   entries (item 356-B's ruling). The JSON pass runs before the companion pass and claims those names in the
   global mixin list, while the plugin's `shouldApplyMixin` refuses them, so the two are registered by nobody:
   they are shadowed, not merely unused. Adding a name there would silently shadow a live mixin the same way,
   and removing one of the two would let the companion pass register and apply it for the first time - both
   are behaviour changes that belong to their own item, not to an edit of this file.
5. **No patch property reads in the plugin** - the mixin plugin may not carry a `celtium.patch.` property
   literal: the kill-switch and ledger reads live in the ledger class, so the gate's decisions stay in the
   module the offline tests can reach (and the plugin's early path stays free of property reads, as the
   GL-less CPU contract already requires for its own reasons).

Discovery mirrors the runtime rule in `CeleritasVintageMixinPlugin.getMixins()` at source level: a file below the
mixin package that carries an `@Mixin` annotation in code (comments and string literals do not count) is a mixin.
Exit status is 0 when clean and 1 when a problem is reported, like `scripts/check_agent_docs.py`.
"""
from __future__ import annotations

from pathlib import Path
import json
import re
import sys

ROOT = Path(__file__).resolve().parent.parent
TABLE = Path("celtium-vulkan/src/main/java/org/soutalab/celtium/patch/PatchGroups.java")
MIXIN_DIR = Path("forge122/src/main/java/org/taumc/celeritas/mixin")
CONFIG_JSON = Path("forge122/src/main/resources/mixins.celeritas.json")
PLUGIN = Path("forge122/src/main/java/org/taumc/celeritas/mixin/CeleritasVintageMixinPlugin.java")
SHADOW_ENTRIES = ("core.GLAllocationMixin", "core.SplashProgressMixin")
PROPERTY_LITERAL = '"celtium.patch.'
PACKAGE = "org.taumc.celeritas.mixin."
MIXIN_ANNOTATION = re.compile(r"@Mixin\s*\(")
GROUP_CALL = re.compile(r"(?<![\w.$])group\s*\(")
STRING = re.compile(r'"((?:[^"\\]|\\.)*)"')
FLAG = re.compile(r"\b(true|false)\b")


def strip_source(text, blank_strings=False):
    """Return `text` without comments (and with string/char bodies blanked when asked).

    Both passes matter: the table's own javadoc mentions `group(...)` and `@Mixin`, and a cost string may contain
    parentheses, so the naive regex over the raw file would parse prose and mis-split arguments.
    """
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
            literal = text[index:end]
            out.append(" " * len(literal) if blank_strings else literal)
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


def discover_mixins(root=ROOT):
    """Return the sorted relative names (dotted) of every mixin found below the mixin package."""
    base = Path(root) / MIXIN_DIR
    if not base.is_dir():
        return []
    found = []
    for path in sorted(base.rglob("*.java")):
        code = strip_source(path.read_text(encoding="utf-8", errors="replace"), blank_strings=True)
        if MIXIN_ANNOTATION.search(code):
            relative = path.relative_to(base).with_suffix("")
            found.append(".".join(relative.parts))
    return found


def _arguments(text, open_index):
    """Return the argument text of the call whose `(` sits at `open_index`, skipping strings and nesting."""
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


def parse_table(text):
    """Return (groups, problems): `groups` is a list of dicts with id/title/essential/cost/members."""
    code = strip_source(text)
    groups = []
    problems = []
    for match in GROUP_CALL.finditer(code):
        arguments = _arguments(code, match.end() - 1)
        if arguments is None:
            problems.append("unbalanced group( call in the patch-group table")
            continue
        if '"' not in arguments:
            # The table helper's own declaration (`private static void group(String id, ...)`) matches too; a real
            # entry always carries its strings.
            continue
        literals = STRING.findall(arguments)
        flags = FLAG.findall(arguments)
        if len(literals) < 3:
            problems.append(f"group( call needs an id, a title and a cost: {arguments.strip()[:60]}")
            continue
        if len(flags) != 1:
            problems.append(f"group({literals[0]}) needs exactly one true/false essential flag, found {len(flags)}")
            continue
        groups.append({
            "id": literals[0],
            "title": literals[1],
            "essential": flags[0] == "true",
            "cost": literals[2],
            "members": literals[3:],
        })
    if not groups:
        problems.append(f"no group( entries found in {TABLE}")
    return groups, problems


def common_suffix_names(mixins):
    """Return {simple name: [relative names]} for simple names shared by more than one mixin."""
    by_simple = {}
    for name in mixins:
        by_simple.setdefault(name.rsplit(".", 1)[-1], []).append(name)
    return {simple: sorted(qualified) for simple, qualified in by_simple.items() if len(qualified) > 1}


def analyze(root=ROOT):
    """Return (problems, summary) for the tree below `root`."""
    root = Path(root)
    problems = []
    table_path = root / TABLE
    if not table_path.is_file():
        return [f"{TABLE}: missing patch-group table"], {"groups": [], "discovered": [], "collisions": {}}
    groups, problems = parse_table(table_path.read_text(encoding="utf-8"))
    discovered = discover_mixins(root)
    if not discovered:
        problems.append(f"{MIXIN_DIR}: no mixins discovered (check the path before trusting a clean result)")
    discovered_set = set(discovered)
    collisions = common_suffix_names(discovered)

    seen_ids = {}
    listed = {}
    for group in groups:
        identifier = group["id"]
        if identifier in seen_ids:
            problems.append(f"duplicate group id: {identifier}")
        seen_ids[identifier] = group
        if not group["members"]:
            problems.append(f"group {identifier} lists no mixins")
        for member in group["members"]:
            if member in listed:
                problems.append(f"{member} is listed twice (groups {listed[member]} and {identifier})")
            listed[member] = identifier
            if member not in discovered_set:
                problems.append(f"group {identifier} lists {member}, which is not a mixin in the source tree")
            simple = member.rsplit(".", 1)[-1]
            if "." not in member and simple in collisions:
                problems.append(
                    f"group {identifier} addresses {member} by simple name, but {simple} is also "
                    f"{', '.join(name for name in collisions[simple] if name != member)}; use the relative name")

    for name in discovered:
        if name not in listed:
            problems.append(f"mixin not in the patch-group table: {name} (an unlisted name falls back to mechanism)")

    shadow = None
    json_path = root / CONFIG_JSON
    if json_path.is_file():
        try:
            config = json.loads(json_path.read_text(encoding="utf-8"))
            entries = []
            for key in ("mixins", "client", "server"):
                value = config.get(key) if isinstance(config, dict) else None
                if isinstance(value, list):
                    entries.extend(item for item in value if isinstance(item, str))
            shadow = sorted(entries)
            if sorted(entries) != sorted(SHADOW_ENTRIES) or len(entries) != len(SHADOW_ENTRIES):
                problems.append(
                    f"{CONFIG_JSON} lists {sorted(entries)}, but the pinned shadow list is "
                    f"{sorted(SHADOW_ENTRIES)}: the JSON arrays shadow the companion pass (the plugin refuses "
                    "what they name and they claim the name first), so adding a name shadows a live mixin and "
                    "removing one applies it for the first time - either change belongs to its own item")
        except (OSError, ValueError) as failure:
            problems.append(f"{CONFIG_JSON}: cannot parse the mixin config ({failure})")

    plugin_property_reads = None
    plugin_path = root / PLUGIN
    if plugin_path.is_file():
        plugin_property_reads = plugin_path.read_text(encoding="utf-8", errors="replace").count(PROPERTY_LITERAL)
        if plugin_property_reads:
            problems.append(
                f"{PLUGIN} carries {plugin_property_reads} patch property literal(s): the kill-switch and "
                "ledger reads belong in the patch ledger class, not in the mixin plugin")

    summary = {
        "groups": groups,
        "discovered": discovered,
        "collisions": collisions,
        "listed": listed,
        "shadow": shadow,
        "plugin_property_reads": plugin_property_reads,
    }
    return problems, summary


def check(root=ROOT):
    """Return the list of problems for the tree below `root` (empty when the table is consistent)."""
    return analyze(root)[0]


def describe(summary):
    """Return the one-line summary printed on success."""
    groups = summary["groups"]
    essential = [group["id"] for group in groups if group["essential"]]
    collisions = summary["collisions"]
    listed = len(summary["listed"])
    parts = [
        f"Patch groups: {len(groups)} groups ({len(essential)} essential, {len(groups) - len(essential)} quarantinable), "
        f"{listed} mixins listed, {len(summary['discovered'])} discovered"
    ]
    if collisions:
        detail = "; ".join(f"{simple} = {', '.join(names)}" for simple, names in sorted(collisions.items()))
        parts.append(f"{len(collisions)} simple-name collisions addressed by relative names ({detail})")
    else:
        parts.append("no simple-name collisions")
    if summary.get("shadow") is not None:
        parts.append(f"shadow-json={len(summary['shadow'])} entries (unchanged)")
    if summary.get("plugin_property_reads") is not None:
        parts.append(f"plugin property reads={summary['plugin_property_reads']}")
    return ", ".join(parts) + "."


def main():
    problems, summary = analyze()
    for problem in problems:
        print(f"FAIL: {problem}", file=sys.stderr)
    if problems:
        return 1
    print(describe(summary))
    print("This does not validate the group split, what each group's cost actually is, or that a group which is "
          "quarantined still renders.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
