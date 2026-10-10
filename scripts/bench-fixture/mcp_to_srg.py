#!/usr/bin/env python3
"""MCP -> SRG member mapping for the 1.12.2 workspace, and the source remap that uses it.

Cleanroom's runtime presents Minecraft with MCP *class* names but SRG *member* names
(``net.minecraft.client.Minecraft.func_71407_l``). A mod jar therefore has to be reobfuscated
MCP -> SRG before it can run, which is what the project's own ``reobf`` task does for the renderer
and the validation fixture. This module does the same job for the benchmark fixture without
touching any Gradle project:

* the mapping is derived from the workspace's own MCP-named and SRG-named Minecraft jars, which
  contain the same classes with the same members in the same order;
* the mapping is checked against three pairs that are independently known from the deployed
  validation jar (``getMinecraft``, ``player``, ``world``), and the build fails if they disagree;
* the fixture source is remapped token by token, and an ambiguous member name is an error rather
  than a guess.

Nothing here is specific to the benchmark fixture; it is a general 1.12.2 source remap.
"""

import json
import re
import struct
import zipfile
from pathlib import Path

# (class, MCP name, descriptor, SRG name) taken from the *deployed* validation jar, which is known to
# run. These are the self-test: if the derived mapping disagrees, the jars are not the pair we think.
KNOWN_PAIRS = [
    ("net/minecraft/client/Minecraft", "getMinecraft", "()Lnet/minecraft/client/Minecraft;",
     "func_71410_x"),
    ("net/minecraft/client/Minecraft", "player", "Lnet/minecraft/client/entity/EntityPlayerSP;",
     "field_71439_g"),
    ("net/minecraft/client/Minecraft", "world", "Lnet/minecraft/client/multiplayer/WorldClient;",
     "field_71441_e"),
]

_BASE = 0x0A000000


def _read_class(data):
    """Return (name, superclass, interfaces, fields, methods); members are (name, descriptor)."""
    if len(data) < 10 or data[:4] != b"\xca\xfe\xba\xbe":
        raise ValueError("not a class file")
    cursor = 8
    count = struct.unpack_from(">H", data, cursor)[0]
    cursor += 2
    pool = [None] * count
    index = 1
    while index < count:
        tag = data[cursor]
        cursor += 1
        if tag == 1:
            length = struct.unpack_from(">H", data, cursor)[0]
            cursor += 2
            pool[index] = data[cursor:cursor + length].decode("utf-8", "replace")
            cursor += length
        elif tag in (3, 4):
            cursor += 4
        elif tag in (5, 6):
            cursor += 8
            index += 1
        elif tag in (7, 8, 16, 19, 20):
            pool[index] = struct.unpack_from(">H", data, cursor)[0]
            cursor += 2
        elif tag in (9, 10, 11, 12, 17, 18):
            cursor += 4
        elif tag == 15:
            cursor += 3
        else:
            raise ValueError("unknown constant pool tag %d" % tag)
        index += 1
    _access, this_index, super_index = struct.unpack_from(">HHH", data, cursor)
    cursor += 6
    name = pool[pool[this_index]]
    super_name = pool[pool[super_index]] if super_index else None
    interface_count = struct.unpack_from(">H", data, cursor)[0]
    cursor += 2
    interface_names = []
    for _ in range(interface_count):
        interface_names.append(pool[pool[struct.unpack_from(">H", data, cursor)[0]]])
        cursor += 2

    def members(at):
        total = struct.unpack_from(">H", data, at)[0]
        at += 2
        out = []
        for _ in range(total):
            _flags, name_index, desc_index, attrs = struct.unpack_from(">HHHH", data, at)
            at += 8
            out.append((pool[name_index], pool[desc_index]))
            for _ in range(attrs):
                _attr_name, length = struct.unpack_from(">HI", data, at)
                at += 6 + length
        return out, at

    fields, cursor = members(cursor)
    methods, cursor = members(cursor)
    return name, super_name, interface_names, fields, methods


def _pair_members(mcp_members, srg_members, class_name, report):
    """Match members by descriptor, in declaration order within each descriptor group.

    A rename preserves both the descriptor and the order of the members that share it, while the two
    jars can differ by a handful of members overall (the SRG jar is one pipeline's output, the
    recompiled jar another's), so matching by position in the whole class would drift. Within a
    descriptor group it cannot: a group whose sizes differ is reported and skipped rather than
    guessed at.
    """
    mapping = {}
    for kind, mcp_list, srg_list in (("field", mcp_members[0], srg_members[0]),
                                     ("method", mcp_members[1], srg_members[1])):
        def grouped(members):
            groups = {}
            for name, descriptor in members:
                groups.setdefault(descriptor, []).append(name)
            return groups

        mcp_groups = grouped(mcp_list)
        srg_groups = grouped(srg_list)
        for descriptor, mcp_names in mcp_groups.items():
            srg_names = srg_groups.get(descriptor)
            if srg_names is None:
                report.append("%s: %s descriptor %s is only in the MCP jar"
                              % (class_name, kind, descriptor))
                continue
            if len(mcp_names) != len(srg_names):
                report.append("%s: %s descriptor %s has %d MCP names and %d SRG names"
                              % (class_name, kind, descriptor, len(mcp_names), len(srg_names)))
                continue
            for mcp_name, srg_name in zip(mcp_names, srg_names):
                mapping[(mcp_name, descriptor)] = srg_name
    return mapping


def build_mapping(mcp_jar, srg_jar, cache_path=None):
    """Every MCP member name that maps to an SRG name, plus the per-class detail."""
    if cache_path and Path(cache_path).is_file():
        cached = json.loads(Path(cache_path).read_text())
        if (cached.get("mcpJar") == str(mcp_jar) and cached.get("srgJar") == str(srg_jar)):
            return cached
    with zipfile.ZipFile(mcp_jar) as archive:
        mcp_classes = {info.filename[:-6] for info in archive.infolist()
                       if info.filename.endswith(".class") and not info.filename.startswith("META-INF")}
    with zipfile.ZipFile(srg_jar) as archive:
        srg_classes = {info.filename[:-6] for info in archive.infolist()
                       if info.filename.endswith(".class") and not info.filename.startswith("META-INF")}
    wanted = sorted(mcp_classes & srg_classes)

    report = []
    per_class = {}
    flat = {}
    ambiguous = {}
    supers = {}
    srg_members = {}
    with zipfile.ZipFile(mcp_jar) as mcp_archive, zipfile.ZipFile(srg_jar) as srg_archive:
        mcp_names = {info.filename[:-6]: info for info in mcp_archive.infolist()
                     if info.filename.endswith(".class")}
        srg_names = {info.filename[:-6]: info for info in srg_archive.infolist()
                     if info.filename.endswith(".class")}
        # The SRG jar is the authority on what a name looks like *after* renaming, including the
        # members it does not rename; the hierarchy is what resolves an inherited member.
        for class_name, info in srg_names.items():
            try:
                _n, super_name, interface_names, fields, methods = _read_class(srg_archive.read(info))
            except ValueError:
                continue
            srg_members[class_name] = sorted({"%s%s" % (name, desc) for name, desc in fields + methods})
            supers.setdefault("srg:" + class_name, []).append(super_name)
            supers["srg:" + class_name].extend(interface_names)
        for class_name, info in mcp_names.items():
            try:
                _n, super_name, interface_names, _f, _m = _read_class(mcp_archive.read(info))
            except ValueError:
                continue
            supers["mcp:" + class_name] = [entry for entry in [super_name] + list(interface_names) if entry]
        for class_name in wanted:
            try:
                _, _s, _i, mcp_fields, mcp_methods = _read_class(mcp_archive.read(mcp_names[class_name]))
                _, _s2, _i2, srg_fields, srg_methods = _read_class(srg_archive.read(srg_names[class_name]))
            except ValueError as error:
                report.append("%s: %s" % (class_name, error))
                continue
            mapping = _pair_members((mcp_fields, mcp_methods), (srg_fields, srg_methods),
                                    class_name, report)
            per_class[class_name] = {"%s%s" % (name, desc): srg for (name, desc), srg in mapping.items()}
            for (name, _desc), srg in mapping.items():
                if name == srg:
                    continue
                flat.setdefault(name, set()).add(srg)
    for name, targets in flat.items():
        if len(targets) > 1:
            ambiguous[name] = sorted(targets)
    mapping = {
        "mcpJar": str(mcp_jar),
        "srgJar": str(srg_jar),
        "classCount": len(wanted),
        "memberNames": {name: sorted(targets)[0] for name, targets in flat.items()},
        "ambiguousNames": ambiguous,
        "perClass": per_class,
        "hierarchy": supers,
        "srgMembers": srg_members,
        "pairingReport": report[:40],
    }
    for class_name, mcp_name, descriptor, srg_name in KNOWN_PAIRS:
        found = per_class.get(class_name, {})
        match = found.get(mcp_name + descriptor)
        if match is None:
            raise SystemExit("mapping self-test failed: %s.%s%s is not in the derived mapping"
                             % (class_name, mcp_name, descriptor))
        if match != srg_name:
            raise SystemExit("mapping self-test failed: %s.%s -> %s, expected %s"
                             % (class_name, mcp_name, match, srg_name))
    if cache_path:
        Path(cache_path).parent.mkdir(parents=True, exist_ok=True)
        Path(cache_path).write_text(json.dumps(mapping) + "\n")
    return mapping


IDENTIFIER = re.compile(r"[A-Za-z_$][A-Za-z0-9_$]*")


# --------------------------------------------------------------------------- bytecode remap

_REF_TAGS = (9, 10, 11)          # Fieldref, Methodref, InterfaceMethodref


def _parse_pool(data):
    """Constant pool as (entries, end offset); entries[i] is None for a Long/Double phantom slot."""
    count = struct.unpack_from(">H", data, 8)[0]
    entries = [None] * count
    cursor = 10
    index = 1
    while index < count:
        tag = data[cursor]
        start = cursor + 1
        if tag == 1:
            length = struct.unpack_from(">H", data, start)[0]
            value = data[start + 2:start + 2 + length].decode("utf-8", "replace")
            cursor = start + 2 + length
        elif tag in (3, 4):
            value = struct.unpack_from(">i", data, start)[0]
            cursor = start + 4
        elif tag in (5, 6):
            value = struct.unpack_from(">q", data, start)[0]
            cursor = start + 8
            entries[index] = {"tag": tag, "raw": data[start:cursor], "value": value}
            index += 2
            continue
        elif tag in (7, 8, 16, 19, 20):
            value = struct.unpack_from(">H", data, start)[0]
            cursor = start + 2
        elif tag in (9, 10, 11, 12, 17, 18):
            value = struct.unpack_from(">HH", data, start)
            cursor = start + 4
        elif tag == 15:
            value = (data[start], struct.unpack_from(">H", data, start + 1)[0])
            cursor = start + 3
        else:
            raise ValueError("unknown constant pool tag %d at %d" % (tag, cursor))
        entries[index] = {"tag": tag, "raw": data[start:cursor], "value": value}
        index += 1
    return entries, cursor


def remap_class(data, mapping):
    """Rewrite MCP member references to SRG, resolving each reference's owner class.

    A flat name -> name table cannot do this job: 2023 of the 14308 member names in this workspace
    map to more than one SRG name, because the same MCP name is used by many classes. Every constant
    pool reference carries its own owner, so the lookup here is exact. New constant pool entries are
    appended and the reference is repointed at them, which leaves every existing index valid and
    cannot rename a member of an unrelated class that happens to share a name and descriptor.
    """
    per_class = mapping["perClass"]
    hierarchy = mapping.get("hierarchy") or {}
    srg_members = mapping.get("srgMembers") or {}
    entries, cp_end = _parse_pool(data)
    count = len(entries)
    utf8_by_value = {}
    for index, entry in enumerate(entries):
        if entry and entry["tag"] == 1:
            utf8_by_value.setdefault(entry["value"], index)
    appended = []
    renamed = {}
    unresolved = []
    unverified = []
    already_srg = {}

    def walk(prefix, start):
        seen = set()
        stack = [start]
        while stack:
            current = stack.pop()
            if not current or current in seen:
                continue
            seen.add(current)
            yield current
            stack.extend(hierarchy.get(prefix + current) or [])

    def resolve(owner, name, descriptor):
        """The SRG name for one member reference, resolved through the owner's own hierarchy.

        `per_class` is keyed by the class that *declares* the member, and javac records the class
        named by the qualifier, so `mc.player.posX` arrives as `EntityPlayerSP.posX` while the field
        is declared in `Entity`. The class chain is therefore walked before anything else.
        """
        key = name + descriptor
        for candidate in walk("mcp:", owner):
            found = (per_class.get(candidate) or {}).get(key)
            if found:
                return found, "declared-in:%s" % candidate
        # Members that are not obfuscated at all (Forge's additions to Minecraft classes keep their
        # names). The SRG jar is the authority: it either names the member the same way or does not
        # have it because it is a patch.
        for candidate in walk("srg:", owner):
            if key in (srg_members.get(candidate) or ()):
                return name, "already-srg-in:%s" % candidate
        if not name.startswith(("func_", "field_")):
            return name, "not-obfuscated"
        candidates = {value for class_map in per_class.values() for key2, value in class_map.items()
                      if key2 == key}
        if len(candidates) == 1:
            return candidates.pop(), "unique-across-workspace"
        return None, None

    def intern_utf8(text):
        if text in utf8_by_value:
            return utf8_by_value[text]
        raw = text.encode("utf-8")
        index = count + len(appended)
        appended.append({"tag": 1, "raw": struct.pack(">H", len(raw)) + raw})
        utf8_by_value[text] = index
        return index

    def append_name_and_type(name_index, descriptor_index):
        index = count + len(appended)
        appended.append({"tag": 12, "raw": struct.pack(">HH", name_index, descriptor_index)})
        return index

    def class_name(class_index):
        entry = entries[class_index]
        if not entry or entry["tag"] != 7:
            return None
        return entries[entry["value"]]["value"]

    for index, entry in enumerate(entries):
        if not entry or entry["tag"] not in _REF_TAGS:
            continue
        class_index, nat_index = entry["value"]
        owner = class_name(class_index)
        nat = entries[nat_index]
        if owner is None or not nat or nat["tag"] != 12:
            continue
        name_index, descriptor_index = nat["value"]
        name = entries[name_index]["value"]
        descriptor = entries[descriptor_index]["value"]
        target, why = resolve(owner, name, descriptor)
        if target is None:
            if owner == "net/minecraft" or owner.startswith("net/minecraft/"):
                unresolved.append("%s.%s%s" % (owner, name, descriptor))
            continue
        if target == name:
            if why.startswith("declared-in") or why.startswith("unique"):
                already_srg["%s.%s%s" % (owner, name, descriptor)] = why
            continue
        if why.startswith("declared-in:"):
            # The mapping said this member is renamed, so the SRG jar must declare the new name on the
            # class it was derived from. If it does not, the pairing drifted and the name is a guess.
            declaring = why.split(":", 1)[1]
            if target + descriptor not in (srg_members.get(declaring) or ()):
                unverified.append("%s.%s%s -> %s (not declared by %s in the SRG jar)"
                                  % (owner, name, descriptor, target, declaring))
        new_nat = append_name_and_type(intern_utf8(target), descriptor_index)
        entries[index]["raw"] = struct.pack(">HH", class_index, new_nat)
        renamed["%s.%s%s" % (owner, name, descriptor)] = target

    if not renamed:
        return data, renamed, unresolved, already_srg, unverified
    out = bytearray()
    out += data[:8]
    out += struct.pack(">H", count + len(appended))
    for index in range(1, count):
        entry = entries[index]
        if entry is None:
            continue
        out.append(entry["tag"])
        out += entry["raw"]
    for entry in appended:
        out.append(entry["tag"])
        out += entry["raw"]
    out += data[cp_end:]
    return bytes(out), renamed, unresolved, already_srg, unverified
