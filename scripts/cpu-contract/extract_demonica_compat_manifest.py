#!/usr/bin/env python3
"""Extract the Demonica compatibility surface manifest from a Demonica jar.

Demonica (AGPL-3.0) is written against upstream Celeritas. The classes of celtium that it
links against - by bytecode reference, by mixin target, by @Shadow declaration and by the
target strings inside its injection annotations - are celtium's Demonica compatibility
surface (docs: COMPAT-SURFACE-DESIGN-2026-11-17.md in the planning tree). This extractor
reads the *shipped bytecode* of a Demonica jar and records the facts of that linkage: class
names and member names with their descriptors. It copies no Demonica code; a name and a
descriptor are the contract, not an implementation.

The output is two files:

  demonica-compat-manifest.txt             the surface celtium must provide
  demonica-compat-known-incompatible.txt   the part celtium deliberately does not provide
                                           (Tier C of the design: the shadow-joml type
                                           cluster, whose descriptors can never wire to
                                           celtium's real org.joml types)

Regenerate after any Demonica version change:

  python3 scripts/cpu-contract/extract_demonica_compat_manifest.py path/to/Demonica-x.y.z.jar

The Demonica jar is not part of this repository; it is an input, named in the manifest header
together with its SHA-256 so a stale manifest is recognisable.
"""
from __future__ import annotations

import hashlib
import re
import sys
import zipfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from classfile import ClassFile  # noqa: E402

HOST_PREFIXES = ("org/taumc/celeritas/", "org/embeddedt/embeddium/")

# Tier C of the design: upstream shades JOML into impl.shadow.joml and Demonica's bridge
# classes are compiled against those types. celtium uses real org.joml, so any reference
# owned by, or mentioning, these types cannot be satisfied by adding classes; the shadow
# path is celtium-native instead. GlUniformMatrix3f belongs to the cluster because its only
# consumer is the cluster's shader interface.
TIER_C_PREFIXES = ("org/embeddedt/embeddium/impl/shadow/joml/",)
TIER_C_CLASSES = {"org/embeddedt/embeddium/impl/gl/shader/uniform/GlUniformMatrix3f"}

_MIXIN = "Lorg/spongepowered/asm/mixin/Mixin;"
_SHADOW = "Lorg/spongepowered/asm/mixin/Shadow;"
_OVERWRITE = "Lorg/spongepowered/asm/mixin/Overwrite;"

_TYPE_IN_DESCRIPTOR = re.compile(r"L([A-Za-z0-9_/$]+);")
_HOST_NAME_IN_TEXT = re.compile(r"(?:org/taumc/celeritas|org/embeddedt/embeddium)/[A-Za-z0-9_/$]+")
_HOST_DOTTED_IN_TEXT = re.compile(r"(?:org\.taumc\.celeritas|org\.embeddedt\.embeddium)(?:\.[A-Za-z0-9_$]+)+")

# References the bytecode cannot show: a compile-time constant is inlined into the reading
# class, leaving neither a field reference nor a class constant behind. The design's
# source-level inventory (COMPAT-SURFACE-DESIGN-2026-11-17.md section 3.2) found exactly one
# such reference in Demonica 0.9.1; it is recorded here by hand so the manifest stays
# complete, and a future Demonica version must be re-checked for new ones at source level.
SUPPLEMENT = [
    ("CLASS", "org/embeddedt/embeddium/impl/render/chunk/RenderSectionFlags", None, None),
    ("FIELD", "org/embeddedt/embeddium/impl/render/chunk/RenderSectionFlags", "HAS_BLOCK_GEOMETRY", "I"),
]
_METHOD_TARGET = re.compile(
    r"L([A-Za-z0-9_/$]+);([A-Za-z_$<>][A-Za-z0-9_$<>]*)\(([^()]*)\)(\[*L[A-Za-z0-9_/$]+;|\[*[VZBCSIJFD])")
_FIELD_TARGET = re.compile(
    r"L([A-Za-z0-9_/$]+);([A-Za-z_$][A-Za-z0-9_$]*):(\[*L[A-Za-z0-9_/$]+;|\[*[VZBCSIJFD])")


def is_host(internal_name: str) -> bool:
    return internal_name.startswith(HOST_PREFIXES)


def is_tier_c(owner: str, descriptor: str) -> bool:
    if owner.startswith(TIER_C_PREFIXES) or owner in TIER_C_CLASSES:
        return True
    for type_name in _TYPE_IN_DESCRIPTOR.findall(descriptor):
        if type_name.startswith(TIER_C_PREFIXES) or type_name in TIER_C_CLASSES:
            return True
    return False


class Surface:
    def __init__(self):
        self.classes: set[str] = set()
        self.methods: set[tuple[str, str, str]] = set()
        self.fields: set[tuple[str, str, str]] = set()

    def add_descriptor_types(self, descriptor: str):
        for type_name in _TYPE_IN_DESCRIPTOR.findall(descriptor):
            if is_host(type_name):
                self.classes.add(type_name)

    def add_method(self, owner: str, name: str, descriptor: str):
        if is_host(owner):
            self.methods.add((owner, name, descriptor))
            self.classes.add(owner)
            self.add_descriptor_types(descriptor)

    def add_field(self, owner: str, name: str, descriptor: str):
        if is_host(owner):
            self.fields.add((owner, name, descriptor))
            self.classes.add(owner)
            self.add_descriptor_types(descriptor)

    def add_class(self, internal_name: str):
        internal_name = internal_name.lstrip("[").removeprefix("L").removesuffix(";")
        if is_host(internal_name):
            self.classes.add(internal_name)


def mixin_targets(class_file: ClassFile) -> list[str]:
    targets = []
    for annotation in class_file.annotations:
        if annotation["type"] != _MIXIN:
            continue
        for key in ("value", "targets"):
            raw = annotation["values"].get(key)
            if raw is None:
                continue
            values = raw if isinstance(raw, list) else [raw]
            for value in values:
                if isinstance(value, str):
                    targets.append(value.replace(".", "/") if "/" not in value else value)
    return targets


def extract(jar_path: Path) -> tuple[Surface, Surface, int]:
    required = Surface()
    tier_c = Surface()

    def route_method(owner, name, descriptor):
        (tier_c if is_tier_c(owner, descriptor) else required).add_method(owner, name, descriptor)

    def route_field(owner, name, descriptor):
        (tier_c if is_tier_c(owner, descriptor) else required).add_field(owner, name, descriptor)

    def route_class(internal_name):
        name = internal_name.lstrip("[").removeprefix("L").removesuffix(";")
        (tier_c if is_tier_c(name, "") else required).add_class(name)

    class_count = 0
    with zipfile.ZipFile(jar_path) as jar:
        defined = {name[:-len(".class")] for name in jar.namelist() if name.endswith(".class")}
        for name in sorted(defined):
            class_file = ClassFile(jar.read(name + ".class"))
            class_count += 1

            # Bytecode references: the ground truth of what this class links against.
            for kind, owner, member_name, descriptor in class_file.member_references():
                if not is_host(owner) or owner in defined:
                    continue
                if kind == "field":
                    route_field(owner, member_name, descriptor)
                else:
                    route_method(owner, member_name, descriptor)
            for referenced in class_file.referenced_class_names():
                if referenced not in defined:
                    route_class(referenced)
            # Types named in this class's own declared signatures must also exist.
            for member in class_file.fields + class_file.methods:
                for type_name in _TYPE_IN_DESCRIPTOR.findall(member["descriptor"]):
                    route_class(type_name)

            # Annotation payloads: mixin targets, @Shadow/@Overwrite members, and the
            # target strings of injection points (which are references the constant pool
            # holds only as text).
            targets = [t for t in mixin_targets(class_file) if is_host(t)]
            for target in targets:
                route_class(target)
            for member in class_file.fields + class_file.methods:
                annotation_types = {a["type"] for a in member["annotations"]}
                if annotation_types & {_SHADOW, _OVERWRITE}:
                    is_method = member["descriptor"].startswith("(")
                    for target in targets:
                        if is_method:
                            route_method(target, member["name"], member["descriptor"])
                        else:
                            route_field(target, member["name"], member["descriptor"])
            for text in class_file.utf8_strings():
                for match in _METHOD_TARGET.finditer(text):
                    owner, member_name, args, returns = match.groups()
                    if is_host(owner) and owner not in defined:
                        route_method(owner, member_name, f"({args}){returns}")
                for match in _FIELD_TARGET.finditer(text):
                    owner, member_name, descriptor = match.groups()
                    if is_host(owner) and owner not in defined:
                        route_field(owner, member_name, descriptor)
                for match in _HOST_NAME_IN_TEXT.finditer(text):
                    route_class(match.group(0))
                for match in _HOST_DOTTED_IN_TEXT.finditer(text):
                    route_class(match.group(0).replace(".", "/"))

    for kind, owner, name, descriptor in SUPPLEMENT:
        if kind == "CLASS":
            required.add_class(owner)
        elif kind == "FIELD":
            required.add_field(owner, name, descriptor)
        else:
            required.add_method(owner, name, descriptor)
    return required, tier_c, class_count


def render(surface: Surface, header: list[str]) -> str:
    lines = list(header)
    for internal_name in sorted(surface.classes):
        lines.append(f"CLASS {internal_name}")
    for owner, name, descriptor in sorted(surface.fields):
        lines.append(f"FIELD {owner} {name} {descriptor}")
    for owner, name, descriptor in sorted(surface.methods):
        lines.append(f"METHOD {owner} {name} {descriptor}")
    return "\n".join(lines) + "\n"


def main(argv: list[str]) -> int:
    if len(argv) != 2:
        print(__doc__)
        return 2
    jar_path = Path(argv[1])
    digest = hashlib.sha256(jar_path.read_bytes()).hexdigest()
    required, tier_c, class_count = extract(jar_path)
    out_dir = Path(__file__).resolve().parent
    provenance = [
        f"# Extracted from {jar_path.name} (sha256 {digest}), {class_count} classes scanned.",
        "# Regenerate with: python3 scripts/cpu-contract/extract_demonica_compat_manifest.py <demonica jar>",
        "# Entries are linkage facts (names and descriptors) only; no Demonica code is recorded here.",
    ]
    (out_dir / "demonica-compat-manifest.txt").write_text(render(required, [
        "# Demonica compatibility surface: the upstream-Celeritas linkage celtium must provide.",
        "# Design: COMPAT-SURFACE-DESIGN-2026-11-17.md (Tier A). Checked by",
        "# scripts/cpu-contract/demonica_compat_surface_check.py against the packaged jar.",
        *provenance,
    ]))
    (out_dir / "demonica-compat-known-incompatible.txt").write_text(render(tier_c, [
        "# Known-incompatible Demonica linkage (Tier C of COMPAT-SURFACE-DESIGN-2026-11-17.md):",
        "# the shadow-joml type cluster. celtium uses real org.joml where upstream shades JOML,",
        "# so these descriptors cannot wire by adding classes; shadows are celtium-native.",
        "# Recorded so the boundary is a decision on file, not a crash to rediscover. The surface",
        "# check reports these for the record and does not require them.",
        *provenance,
    ]))
    print(f"required: {len(required.classes)} classes, {len(required.methods)} methods, "
          f"{len(required.fields)} fields")
    print(f"tier C:   {len(tier_c.classes)} classes, {len(tier_c.methods)} methods, "
          f"{len(tier_c.fields)} fields")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
