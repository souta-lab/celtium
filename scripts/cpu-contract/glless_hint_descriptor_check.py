#!/usr/bin/env python3
"""Check that every CeltiumGllessHint call the GL-less transformer emits names a method that exists.

The transformer rewrites GL entry points into INVOKESTATIC calls on CeltiumGllessHint, spelled as a (name, descriptor)
pair in the transformer's source. A pair that names no real method compiles fine and fails only at run time, in
whichever mod first calls that GL entry point: glCopyTexImage2D was emitted as (IIIIIIII)V while the hint only had
the seven-argument form, and The Betweenlands died with NoSuchMethodError in ATM3. This reads both sources and checks
every emitted pair against the hint's declared public static methods.
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
TRANSFORMER = ROOT / "forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessNativeTransformer.java"
HINT = ROOT / "forge122/src/main/java/org/taumc/celeritas/core/CeltiumGllessHint.java"

PRIMITIVES = {"void": "V", "int": "I", "long": "J", "float": "F", "double": "D", "boolean": "Z", "byte": "B",
              "short": "S", "char": "C", "String": "Ljava/lang/String;"}


def descriptor_of(java_type: str) -> str | None:
    java_type = java_type.strip()
    dims = java_type.count("[]")
    base = java_type.replace("[]", "").strip()
    code = PRIMITIVES.get(base)
    if code is None:
        return None
    return "[" * dims + code


def declared_methods(source: str) -> set[tuple[str, str]]:
    methods = set()
    for match in re.finditer(r"public\s+static\s+(?:(?:synchronized|final)\s+)*([\w.\[\]]+)\s+(\w+)\s*\(([^)]*)\)", source, re.S):
        returns, name, params = match.groups()
        parts = [p.strip() for p in params.split(",") if p.strip()]
        codes = [descriptor_of(" ".join(p.split()[:-1])) for p in parts]
        ret = descriptor_of(returns)
        if ret is None or any(code is None for code in codes):
            continue
        methods.add((name, "(" + "".join(codes) + ")" + ret))
    return methods


def emitted_calls(source: str) -> set[tuple[str, str]]:
    pattern = r'"org/taumc/celeritas/core/CeltiumGllessHint",\s*"(\w+)",\s*"(\([^"]*\)[^"]*)"'
    return set(re.findall(pattern, source, re.S))


def main() -> int:
    calls = emitted_calls(TRANSFORMER.read_text(encoding="utf-8"))
    methods = declared_methods(HINT.read_text(encoding="utf-8"))
    missing = sorted(call for call in calls if call not in methods)
    for name, desc in missing:
        print(f"FAIL the transformer emits CeltiumGllessHint.{name}{desc}, which the hint does not declare")
    if not calls:
        print("FAIL no emitted hint calls were found - the pattern no longer matches the transformer")
        return 1
    if missing:
        return 1
    print(f"ok   all {len(calls)} emitted CeltiumGllessHint calls name a declared method")
    return 0


if __name__ == "__main__":
    sys.exit(main())
