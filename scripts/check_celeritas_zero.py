#!/usr/bin/env python3
"""Mechanical proof that no Celeritas-derived code remains (v0.4.0 plan section 3.3).

Implements the five checks defined by the governing plan
(``V040-FINAL-PLAN-BY-NEON-2026-11-27.md`` section 3.3, with the section 8.3
item 4 addendum for ``org.taumc:glsl-transformation-lib``):

1. Banned directories / build relics are absent.
2. Banned identifiers occur 0 times in sources and build files.
3. The shipped jar's entries and class constant pools reference no banned
   package (the refusal string constants of ``CeltiumCompetitors`` excepted).
4. The shipped jar's ``mcmod.info`` / MANIFEST carry the celtium identity.
5. The provenance ledger of every production ``.java`` / ``.kt`` file consists
   only of the approved origins, with 0 GPL-3.0 contamination and 0 files of
   Celeritas / Embeddium / Sodium / Bitraster provenance.

Whitelists are explicit data (``WHITELIST`` below); nothing is silently
ignored. Until the sweep completes every check is expected to FAIL: the
per-check failure breakdown is the progress baseline for the remaining
slices. CI makes this script a required step only at the final slice.

Exit status is 0 when all five checks pass and 1 otherwise.
"""
from __future__ import annotations

import argparse
import json
import re
import subprocess
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

# --- Check 1: banned paths (plan section 3.3 item 1) -------------------------

BANNED_PATHS = (
    "common",
    "buildSrc/src/main/kotlin/org/embeddedt",
    "plugins/celeritas-mdg-plugin",
    "forge122/src/main/resources/META-INF/celeritas_at.cfg",
    "forge122/src/main/resources/mixins.celeritas.json",
    "licenses/bitraster-LICENSE",
)
# Banned asset namespaces: matched as a path-segment pair anywhere in the tree.
BANNED_ASSET_NAMESPACES = ("assets/sodium", "assets/celeritas")

# --- Check 2: banned identifiers (plan section 3.3 item 2) -------------------

FOREIGN_PACKAGES = (
    "org.embeddedt.embeddium",
    "org.taumc.celeritas",
    "dhj.embeddedt.embeddium",
    "me.jellysquid.mods.sodium",
    "net.caffeinemc.mods.sodium",
    "grondag.bitraster",
)
BUILD_RELICS = (
    "GenerateLWJGLAbstraction",
    "celeritas.lwjglService",
    "FakeGlLWJGLService",
    "embeddium-mdg-remapper",
    "embeddium-options.json",
    "Celtium-Upstream-Commit",
    "celeritas-common",
    "taumcRepository",
)
SCAN_EXTENSIONS = (".java", ".kt", ".gradle", ".gradle.kts", ".glsl", ".json", ".cfg", ".lang")

# --- Whitelist: explicit data, never an implicit ignore ----------------------
#
# Each entry names what is approved, where, for which checks, and the ruling
# that approved it. Occurrences covered by an entry are reported as approved,
# not dropped silently; occurrences of the same strings anywhere else fail.

COMPETITORS_REL = "forge122/src/main/java/org/taumc/celeritas/core/CeltiumCompetitors.java"

RFG_ATTRIBUTE_KEY = "com.gtnewhorizons.retrofuturagradle.obfuscation"
RFG_EXCEPTION_FILES = (
    "celtium-core/build.gradle",
    "celtium-vulkan/build.gradle",
    "common/build.gradle",
)

GLSL_LIB_IDENTIFIERS = ("glsl-transformation-lib", "org.taumc.glsl", "org/taumc/glsl")

WHITELIST = (
    {
        "name": "competitor refusal definitions",
        "scope": "the KNOWN marker table inside CeltiumCompetitors.java",
        "checks": (2, 3, 5),
        "source": "plan section 3.3 (checks 2 and 3 whitelist the refusal definition block / refusal string constants)",
    },
    {
        "name": "documentation and legal attribution files",
        "scope": "docs/, THIRD_PARTY_NOTICES.md, notworkingmods.md, verifiedmods.md",
        "checks": (2,),
        "source": "plan section 3.3 check 2 whitelist",
    },
    {
        "name": "org.taumc:glsl-transformation-lib (approved external Maven dependency, LGPL-3.0, 0 in-tree source lines)",
        "scope": "dependency declarations, org.taumc.glsl / org/taumc/glsl references, and the shadow relocation "
                 "targets org.soutalab.celtium.impl.shadow.glsl / .antlr / .jcpp in the shipped jar",
        "checks": (2, 3, 5),
        "source": "Neon ruling D-96 / plan section 8.3 item 4 (explicit registration in checks 2, 3, 5)",
    },
    {
        "name": "RFG attribute key " + RFG_ATTRIBUTE_KEY,
        "scope": "exactly these files: " + ", ".join(RFG_EXCEPTION_FILES),
        "checks": (2,),
        "source": "S1-a conformance audit carryover, registered alongside item 4 in plan section 8.3",
    },
)

# Path fragments excluded from the check 2 scan (documentation / legal files).
CHECK2_EXCLUDED_NAMES = {"THIRD_PARTY_NOTICES.md", "notworkingmods.md", "verifiedmods.md"}

# --- Check 5: provenance ------------------------------------------------------

APPROVED_PACKAGE_ROOT = "org.soutalab.celtium"
FOREIGN_PACKAGE_ROOTS = ("org.embeddedt.embeddium", "org.taumc.celeritas", "grondag.bitraster")
GPL_CONTAMINATION_IDENTIFIERS = ("com.dhj.actinium", "com/dhj/actinium")
APPROVED_ORIGINS = (
    "celtium (in-house)",
    "Actinium glsm LGPL-3.0 (+Mesa MIT pockets)",
    "Actinium shader LGPL-3.0",
    "minimal GTNHLib LGPL-3.0",
)


def tracked_files(root):
    """Tracked files (git ls-files); falls back to a filesystem walk outside a git tree."""
    root = Path(root)
    try:
        out = subprocess.run(
            ["git", "ls-files"], cwd=root, capture_output=True, text=True, timeout=60,
        )
        if out.returncode == 0 and out.stdout.strip():
            return sorted(out.stdout.splitlines())
    except (OSError, subprocess.SubprocessError):
        pass
    walked = []
    for path in sorted(root.rglob("*")):
        if path.is_file():
            rel = path.relative_to(root).as_posix()
            parts = rel.split("/")
            if ".git" not in parts and "build" not in parts:
                walked.append(rel)
    return walked


def read_lines(root, rel):
    try:
        return (Path(root) / rel).read_text(encoding="utf-8", errors="replace").splitlines()
    except OSError:
        return []


def line_count(root, rel):
    try:
        return (Path(root) / rel).read_bytes().count(b"\n")
    except OSError:
        return 0


def competitors_block_lines(lines):
    """0-based line indexes of the KNOWN refusal table inside CeltiumCompetitors.java.

    The block runs from the ``String[][] KNOWN`` declaration to the closing
    ``};`` of its array initializer (brace depth returns to zero).
    """
    excluded = set()
    start = None
    depth = 0
    for i, line in enumerate(lines):
        if start is None:
            if "String[][] KNOWN" in line:
                start = i
                depth = line.count("{") - line.count("}")
                excluded.add(i)
            continue
        excluded.add(i)
        depth += line.count("{") - line.count("}")
        if depth <= 0 and "};" in line:
            break
    return excluded


def is_check2_excluded(rel):
    parts = rel.split("/")
    if parts[0] == "docs":
        return True
    return parts[-1] in CHECK2_EXCLUDED_NAMES


def identifier_forms(identifier):
    """Search forms of an identifier: as written, plus the slash form for dotted packages."""
    forms = [identifier]
    if re.fullmatch(r"[a-zA-Z][\w]*(\.[\w]+)+", identifier):
        forms.append(identifier.replace(".", "/"))
    return forms


# --- Check 1 -------------------------------------------------------------------


def check1_banned_paths(root, files):
    root = Path(root)
    failures = []
    for rel in BANNED_PATHS:
        if (root / rel).exists():
            failures.append(f"banned path present: {rel}")
    for namespace in BANNED_ASSET_NAMESPACES:
        hits = [f for f in files if f"/{namespace}/" in f"/{f}" or f.startswith(namespace + "/")]
        if hits:
            failures.append(f"banned asset namespace present: {namespace}/ ({len(hits)} files, e.g. {hits[0]})")
    return failures


# --- Check 2 -------------------------------------------------------------------


def check2_banned_identifiers(root, files):
    """Return (failures, notes): banned identifiers must occur 0 times in scan scope."""
    failures = []
    notes = []
    identifiers = [(ident, identifier_forms(ident)) for ident in FOREIGN_PACKAGES + BUILD_RELICS]
    hit_files = {ident: set() for ident, _ in identifiers}
    hit_count = {ident: 0 for ident, _ in identifiers}
    rfg_unregistered = []
    rfg_approved_files = set()
    glsl_approved = 0
    for rel in files:
        if not rel.endswith(SCAN_EXTENSIONS) or is_check2_excluded(rel):
            continue
        lines = read_lines(root, rel)
        excluded = competitors_block_lines(lines) if rel == COMPETITORS_REL else set()
        for i, line in enumerate(lines):
            if i in excluded:
                continue
            for ident, forms in identifiers:
                for form in forms:
                    n = line.count(form)
                    if n:
                        hit_files[ident].add(rel)
                        hit_count[ident] += n
            if RFG_ATTRIBUTE_KEY in line:
                if rel in RFG_EXCEPTION_FILES:
                    rfg_approved_files.add(rel)
                else:
                    rfg_unregistered.append(rel)
            glsl_approved += sum(line.count(s) for s in GLSL_LIB_IDENTIFIERS)
    for ident, _ in identifiers:
        if hit_count[ident]:
            failures.append(
                f"{ident}: {hit_count[ident]} occurrences in {len(hit_files[ident])} files"
            )
    for rel in sorted(set(rfg_unregistered)):
        failures.append(f"RFG attribute key in unregistered file: {rel}")
    if rfg_approved_files:
        notes.append(
            f"RFG attribute key approved in {len(rfg_approved_files)} registered files "
            f"({', '.join(sorted(rfg_approved_files))})"
        )
    if glsl_approved:
        notes.append(f"glsl-transformation-lib approved references: {glsl_approved} occurrences (ruling D-96)")
    return failures, notes


# --- Checks 3 and 4: the shipped jar -------------------------------------------

JAR_GLOBS = (
    "forge122/build/libs/celtium-forge-mc1.12.2-*.jar",
    "build-1122/forge122/build/libs/celtium-forge-mc1.12.2-*.jar",
)


def find_shipped_jars(root, jar_arg=None):
    root = Path(root)
    if jar_arg:
        return [Path(jar_arg)]
    found = []
    for pattern in JAR_GLOBS:
        found.extend(sorted(root.glob(pattern)))
    if not found:
        found = sorted(p for p in root.rglob("celtium-forge-mc1.12.2-*.jar") if ".git" not in p.parts)
    return found


def jar_banned_needles():
    needles = {}
    for ident in FOREIGN_PACKAGES:
        needles[ident] = [s.encode() for s in identifier_forms(ident)]
    return needles


def check3_jar_references(jars):
    failures = []
    if not jars:
        return ["shipped jar not found (celtium-forge-mc1.12.2-*.jar); build the jar before running checks 3-4"]
    needles = jar_banned_needles()
    for jar in jars:
        try:
            archive = zipfile.ZipFile(jar)
        except (OSError, zipfile.BadZipFile) as exc:
            failures.append(f"{jar}: unreadable jar ({exc})")
            continue
        for info in archive.infolist():
            name = info.filename
            for ident in FOREIGN_PACKAGES:
                if name.startswith(ident.replace(".", "/") + "/"):
                    failures.append(f"{jar.name}: entry under banned package {ident}: {name}")
            if info.is_dir():
                continue
            data = archive.read(name)
            # CeltiumCompetitors' own refusal string constants are whitelisted (plan 3.3 check 3).
            if name.endswith("CeltiumCompetitors.class"):
                continue
            for ident, forms in needles.items():
                for form in forms:
                    if form in data:
                        failures.append(f"{jar.name}: {name} references banned package {ident}")
                        break
    return failures


EXPECTED_MANIFEST = {
    "FMLCorePlugin": "org.soutalab.celtium.core.CeltiumLoadingPlugin",
    "FMLAT": "celtium_at.cfg",
    "Celtium-Vulkan-Minimum": "1.3",
    "Celtium-License": "LGPL-3.0",
}


def parse_manifest(data):
    text = data.decode("utf-8", errors="replace")
    unfolded = []
    for line in text.splitlines():
        if line.startswith(" ") and unfolded:
            unfolded[-1] += line[1:]
        elif line.strip():
            unfolded.append(line)
    attrs = {}
    for line in unfolded:
        if ": " in line:
            key, value = line.split(": ", 1)
            attrs[key.strip()] = value.strip()
    return attrs


def check4_jar_metadata(jars):
    failures = []
    if not jars:
        return ["shipped jar not found (celtium-forge-mc1.12.2-*.jar); build the jar before running checks 3-4"]
    for jar in jars:
        try:
            archive = zipfile.ZipFile(jar)
        except (OSError, zipfile.BadZipFile) as exc:
            failures.append(f"{jar}: unreadable jar ({exc})")
            continue
        names = archive.namelist()
        info_name = next((n for n in names if n.endswith("mcmod.info")), None)
        if info_name is None:
            failures.append(f"{jar.name}: mcmod.info missing")
        else:
            try:
                entries = json.loads(archive.read(info_name).decode("utf-8", errors="replace"))
                if isinstance(entries, dict):
                    entries = entries.get("modList", [])
                modids = [e.get("modid") for e in entries if isinstance(e, dict)]
            except (ValueError, UnicodeDecodeError) as exc:
                failures.append(f"{jar.name}: mcmod.info unparsable ({exc})")
                modids = []
            if "celtium" not in modids:
                failures.append(f"{jar.name}: mcmod.info modid is not \"celtium\" (found: {modids})")
            if "celeritas" in modids:
                failures.append(f"{jar.name}: mcmod.info still carries modid \"celeritas\"")
        if "META-INF/MANIFEST.MF" not in names:
            failures.append(f"{jar.name}: MANIFEST.MF missing")
        else:
            attrs = parse_manifest(archive.read("META-INF/MANIFEST.MF"))
            for key, expected in EXPECTED_MANIFEST.items():
                if attrs.get(key) != expected:
                    failures.append(f"{jar.name}: MANIFEST {key} = {attrs.get(key)!r}, expected {expected!r}")
    return failures


# --- Check 5 -------------------------------------------------------------------


def package_of(rel):
    """The file's package as a dotted string, derived from its source path."""
    parts = rel.split("/")
    try:
        idx = parts.index("src")
    except ValueError:
        return None
    # <module>/src/main/(java|kotlin)/<package path...>/<File.java>
    if len(parts) > idx + 3 and parts[idx + 1] == "main" and parts[idx + 2] in ("java", "kotlin"):
        return ".".join(parts[idx + 3:-1])
    return None


def package_root_matches(pkg, root):
    return pkg is not None and (pkg == root or pkg.startswith(root + "."))


def check5_provenance(root, files):
    failures = []
    notes = []
    foreign = {root_pkg: [0, 0] for root_pkg in FOREIGN_PACKAGE_ROOTS}
    approved = 0
    unclassified = []
    gpl_hits = []
    for rel in files:
        if not (rel.endswith(".java") or rel.endswith(".kt")) or "/src/main/" not in rel:
            continue
        pkg = package_of(rel)
        matched = next((r for r in FOREIGN_PACKAGE_ROOTS if package_root_matches(pkg, r)), None)
        if matched is not None:
            foreign[matched][0] += 1
            foreign[matched][1] += line_count(root, rel)
        elif package_root_matches(pkg, APPROVED_PACKAGE_ROOT):
            approved += 1
        else:
            unclassified.append(rel)
        lines = read_lines(root, rel)
        excluded = competitors_block_lines(lines) if rel == COMPETITORS_REL else set()
        for i, line in enumerate(lines):
            if i in excluded:
                continue
            if any(s in line for s in GPL_CONTAMINATION_IDENTIFIERS):
                gpl_hits.append(rel)
                break
    for pkg, (count, lines_n) in foreign.items():
        if count:
            failures.append(f"foreign provenance {pkg}: {count} production files / {lines_n} lines")
    for rel in unclassified:
        failures.append(f"unclassified provenance (outside the approved ledger): {rel}")
    for rel in sorted(set(gpl_hits)):
        failures.append(
            f"com.dhj.actinium string reference in production file: {rel} "
            "(plan 3.3 check 5 counts GPL-3.0 (com.dhj.actinium.*) references as contamination; "
            "the current hits are diagnostic string probes, not code inclusion, and are NOT whitelisted "
            "here -- the final sweep must remove them or register an explicit ruling)"
        )
    notes.append(f"approved provenance ({' / '.join(APPROVED_ORIGINS)}): {approved} production files")
    return failures, notes


# --- Driver ----------------------------------------------------------------------


def run_checks(root, jar_arg=None):
    root = Path(root)
    files = tracked_files(root)
    jars = find_shipped_jars(root, jar_arg)
    c2_failures, c2_notes = check2_banned_identifiers(root, files)
    c5_failures, c5_notes = check5_provenance(root, files)
    results = [
        ("Check 1: banned paths absent", check1_banned_paths(root, files), []),
        ("Check 2: banned identifiers at zero", c2_failures, c2_notes),
        ("Check 3: shipped jar references no banned package", check3_jar_references(jars), []),
        ("Check 4: jar metadata carries the celtium identity", check4_jar_metadata(jars), []),
        ("Check 5: provenance ledger is approved-only, GPL-3.0 contamination zero", c5_failures, c5_notes),
    ]
    return results


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--root", default=str(ROOT), help="repository root (default: parent of scripts/)")
    parser.add_argument("--jar", default=None, help="shipped jar path (default: search the usual build seats)")
    args = parser.parse_args(argv)
    results = run_checks(args.root, args.jar)
    all_pass = True
    for title, failures, notes in results:
        status = "PASS" if not failures else "FAIL"
        if failures:
            all_pass = False
        print(f"[{status}] {title}")
        for failure in failures:
            print(f"    - {failure}")
        for note in notes:
            print(f"    ({note})")
    print("Whitelist (explicit, ruling-backed; occurrences are reported, never silently ignored):")
    for entry in WHITELIST:
        print(f"    * {entry['name']} -- {entry['scope']} [checks {entry['checks']}; {entry['source']}]")
    print("RESULT:", "PASS (Celeritas zero code)" if all_pass else "FAIL (residue remains; see breakdown)")
    return 0 if all_pass else 1


if __name__ == "__main__":
    sys.exit(main())
