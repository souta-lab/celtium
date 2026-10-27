#!/usr/bin/env python3
"""Copy GVCReversion2 alpha.10.1 with its A4 second-gun model/texture indices repaired.

Original mod: GVCReversion2 by SCOPE_MG36.
Does not change the input archive. No GVC bytecode is distributed with this tool.
"""
import argparse
import hashlib
import json
from pathlib import Path
import struct
import zipfile

CLASS = "gvcr2/entity/vehicle/air/EntityGVCR2_AIR_A4.class"
SOURCE = "src/gvcr2/entity/vehicle/air/EntityGVCR2_AIR_A4.java"
ORIGINAL_CLASS_SHA256 = "5b41e73e73d097d5ca43196d602e8e6edae7afe772a68a607ae9adb98662de84"


def constructor_code_start(data):
    """Locate Code in the World constructor, checking class-file boundaries as we walk."""
    if data[:4] != b"\xca\xfe\xba\xbe":
        raise ValueError("not a Java class")
    pos = 8
    def u2():
        nonlocal pos
        value = struct.unpack_from(">H", data, pos)[0]; pos += 2
        return value
    def u4():
        nonlocal pos
        value = struct.unpack_from(">I", data, pos)[0]; pos += 4
        return value
    cp_count = u2(); strings = {}; index = 1
    while index < cp_count:
        tag = data[pos]; pos += 1
        if tag == 1:
            length = u2(); strings[index] = data[pos:pos + length].decode("utf-8"); pos += length
        elif tag in (3, 4, 9, 10, 11, 12, 17, 18): pos += 4
        elif tag in (5, 6): pos += 8; index += 1
        elif tag in (7, 8, 16, 19, 20): pos += 2
        elif tag == 15: pos += 3
        else: raise ValueError(f"unknown constant-pool tag {tag}")
        index += 1
    pos += 6  # access, this, super
    interface_count = u2()
    pos += 2 * interface_count
    for _ in range(u2()):
        pos += 6
        for _ in range(u2()):
            u2(); size = u4(); pos += size
    for _ in range(u2()):
        u2(); name = strings.get(u2()); descriptor = strings.get(u2())
        for _ in range(u2()):
            attribute = strings.get(u2()); size = u4(); start = pos
            if name == "<init>" and descriptor == "(Lnet/minecraft/world/World;)V" and attribute == "Code":
                length = struct.unpack_from(">I", data, start + 4)[0]
                if start + 8 + length > start + size: raise ValueError("invalid constructor Code length")
                return start + 8, length
            pos = start + size
    raise ValueError("World constructor Code missing")


def patch_class(data):
    if hashlib.sha256(data).hexdigest() != ORIGINAL_CLASS_SHA256:
        raise ValueError("unsupported or already patched A4 class; refusing to guess bytecode offsets")
    start, length = constructor_code_start(data)
    result = bytearray(data)
    for offset in (475, 484):
        # GETFIELD bullet_model1/bullet_tex1, ICONST_0, LDC_W, AASTORE.
        # ILOAD_2 has identical size and stack type. Existing offsets/stack maps remain valid.
        if length <= offset + 4 or data[start + offset] != 0x03 or data[start + offset - 3] != 0xB4:
            raise ValueError("expected zero-index field initialization missing")
        if data[start + offset + 1] != 0x13 or data[start + offset + 4] != 0x53:
            raise ValueError("expected resource constant/array-store missing")
        result[start + offset] = 0x1C
    return bytes(result)


def patch_archive(source, output):
    source, output = Path(source), Path(output)
    if source.resolve() == output.resolve(): raise ValueError("output must differ from the original archive")
    if output.exists(): raise ValueError("output already exists")
    with zipfile.ZipFile(source) as before:
        if any(n.upper().endswith((".SF", ".RSA", ".DSA", ".EC")) and n.upper().startswith("META-INF/")
               for n in before.namelist()):
            raise ValueError("signed archive: refusing to invalidate signatures")
        patched = patch_class(before.read(CLASS))
        changes = {CLASS: patched}
        if SOURCE in before.namelist():
            source_text = before.read(SOURCE)
            for field in (b"bullet_model1", b"bullet_tex1"):
                pattern = field + b"[0]"
                if source_text.count(pattern) != 1: raise ValueError("unexpected bundled source")
                source_text = source_text.replace(pattern, field + b"[x]")
            changes[SOURCE] = source_text
        with zipfile.ZipFile(output, "x") as after:
            for entry in before.infolist():
                after.writestr(entry, changes.get(entry.filename, before.read(entry.filename)))
    return {"sourceSha256": hashlib.sha256(source.read_bytes()).hexdigest(),
            "outputSha256": hashlib.sha256(output.read_bytes()).hexdigest(),
            "originalClassSha256": ORIGINAL_CLASS_SHA256,
            "patchedClassSha256": hashlib.sha256(patched).hexdigest(),
            "changedClass": CLASS, "changedEntries": sorted(changes), "changedConstructorCodeOffsets": [475, 484],
            "change": "ICONST_0 -> ILOAD_2 for bullet_model1 and bullet_tex1"}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    try:
        print(json.dumps(patch_archive(args.source, args.output), indent=2))
    except (ValueError, KeyError, OSError, zipfile.BadZipFile, struct.error) as error:
        parser.exit(1, f"A4 patch refused: {error}\n")


if __name__ == "__main__": main()
