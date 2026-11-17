#!/usr/bin/env python3
"""Check the packaged celtium jar against the Demonica compatibility surface manifest.

Demonica links against upstream Celeritas internals; the subset of celtium it touches is a
compatibility surface that a refactor can break silently - the class still compiles, the
game still starts, and Demonica dies (or quietly loses a mixin) only when a player runs
both. The manifest (demonica-compat-manifest.txt, extracted from Demonica's shipped
bytecode by extract_demonica_compat_manifest.py) records that surface as names and
descriptors; this check reads the packaged jar's own bytecode and proves every entry is
still there, at descriptor level. Design: COMPAT-SURFACE-DESIGN-2026-11-17.md, Tier A.

Resolution follows the JVM's own rules, approximated over what the jar contains: a member
counts as provided when the owner class, one of its superclasses or one of its interfaces
(including interfaces of superclasses) declares it. Constructors are only ever declared by
the owner itself. When the search leaves the jar through a non-JDK ancestor (a vanilla
Minecraft or Forge class, say), the member may legitimately be declared out there; those
entries are reported as EXTERNAL and do not fail the check. Leaving through a JDK class
grants no such exemption: the JDK's surface is fixed, and a host member that is missing
above a JDK root is missing.

The known-incompatible file (Tier C, the shadow-joml cluster) is read for the record: its
entries are reported, never required.

Usage:
  python3 scripts/cpu-contract/demonica_compat_surface_check.py [packaged jar ...]

With no argument the jars are looked up under build-1122/build/libs, the packageJar output
the minecraft-1122 CI job produces before this check runs. Several jars are indexed as one
surface, so the check does not care how the build splits its artifacts.
"""
from __future__ import annotations

import sys
import zipfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from classfile import ClassFile  # noqa: E402

ROOT = Path(__file__).resolve().parents[2]
MANIFEST = Path(__file__).resolve().parent / "demonica-compat-manifest.txt"
KNOWN_INCOMPATIBLE = Path(__file__).resolve().parent / "demonica-compat-known-incompatible.txt"

JDK_PREFIXES = ("java/", "javax/", "jdk/", "sun/", "com/sun/")


def load_manifest(path: Path):
    classes, methods, fields = set(), set(), set()
    for line in path.read_text().splitlines():
        if not line or line.startswith("#"):
            continue
        parts = line.split(" ")
        if parts[0] == "CLASS":
            classes.add(parts[1])
        elif parts[0] == "METHOD":
            methods.add((parts[1], parts[2], parts[3]))
        elif parts[0] == "FIELD":
            fields.add((parts[1], parts[2], parts[3]))
        else:
            raise ValueError(f"{path}: unparsable manifest line: {line!r}")
    return classes, methods, fields


class JarIndex:
    def __init__(self, jar_paths: list[Path]):
        self.classes: dict[str, dict] = {}
        for jar_path in jar_paths:
            with zipfile.ZipFile(jar_path) as jar:
                for name in jar.namelist():
                    if not name.endswith(".class"):
                        continue
                    parsed = ClassFile(jar.read(name))
                    self.classes[parsed.this_class] = {
                        "super": parsed.super_class,
                        "interfaces": parsed.interfaces,
                        "methods": {(m["name"], m["descriptor"]) for m in parsed.methods},
                        "fields": {(f["name"], f["descriptor"]) for f in parsed.fields},
                    }

    def ancestors(self, internal_name: str):
        """Yield (name, record-or-None) walking superclasses and interfaces, jar-out last.

        Records are None for classes outside the jar; the walk still reports them once so
        the caller can tell where the chain left the jar.
        """
        seen = set()
        queue = [internal_name]
        while queue:
            current = queue.pop()
            if current in seen:
                continue
            seen.add(current)
            record = self.classes.get(current)
            yield current, record
            if record is None:
                continue
            if record["super"]:
                queue.append(record["super"])
            queue.extend(record["interfaces"])

    def resolve_member(self, owner: str, name: str, descriptor: str, kind: str) -> str:
        """Return "ok", "external" or "missing" for one manifest member entry."""
        if owner not in self.classes:
            return "missing"
        if name == "<init>":
            record = self.classes[owner]
            return "ok" if (name, descriptor) in record["methods"] else "missing"
        key = (name, descriptor)
        member_set = "methods" if kind == "method" else "fields"
        exited_to = None
        for ancestor, record in self.ancestors(owner):
            if record is None:
                exited_to = ancestor
                continue
            if key in record[member_set]:
                return "ok"
        if exited_to is not None and not exited_to.startswith(JDK_PREFIXES):
            return "external"
        return "missing"


def find_packaged_jars() -> list[Path]:
    candidates = sorted((ROOT / "build-1122" / "build" / "libs").glob("*/*.jar"))
    candidates = [c for c in candidates if "sources" not in c.name]
    if not candidates:
        raise SystemExit("no packaged jar found under build-1122/build/libs; "
                         "pass the jar path explicitly")
    return candidates


def main(argv: list[str]) -> int:
    jar_paths = [Path(arg) for arg in argv[1:]] if len(argv) > 1 else find_packaged_jars()
    index = JarIndex(jar_paths)
    req_classes, req_methods, req_fields = load_manifest(MANIFEST)
    failures: list[str] = []
    external: list[str] = []

    for internal_name in sorted(req_classes):
        if internal_name not in index.classes:
            failures.append(f"CLASS {internal_name}")
    for kind, entries in (("method", req_methods), ("field", req_fields)):
        for owner, name, descriptor in sorted(entries):
            verdict = index.resolve_member(owner, name, descriptor, kind)
            label = f"{kind.upper()} {owner} {name} {descriptor}"
            if verdict == "missing":
                failures.append(label)
            elif verdict == "external":
                external.append(label)

    tier_c_classes, tier_c_methods, tier_c_fields = load_manifest(KNOWN_INCOMPATIBLE)
    tier_c_total = len(tier_c_classes) + len(tier_c_methods) + len(tier_c_fields)

    print(f"jars: {', '.join(str(p) for p in jar_paths)} ({len(index.classes)} classes)")
    print(f"required surface: {len(req_classes)} classes, {len(req_methods)} methods, "
          f"{len(req_fields)} fields")
    for label in external:
        print(f"EXTERNAL (declared outside the jar, not verified): {label}")
    print(f"known-incompatible (Tier C, recorded, not required): {tier_c_total} entries")
    if failures:
        print(f"FAIL: {len(failures)} compatibility-surface entries are missing:")
        for label in failures:
            print(f"  MISSING {label}")
        return 1
    print("PASS: the packaged jar provides the full Demonica compatibility surface")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
