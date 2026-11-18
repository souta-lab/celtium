#!/usr/bin/env python3
"""Re-validate every SPIR-V binary packaged inside a built celtium jar.

verify_forge_jar.py proves each required .spv is present in the jar; nothing proved the
packaged bytes are still valid SPIR-V. Build-time glslangValidator compiles the GLSL
sources, but packaging (shading, relocation, zip writing) sits between that compile and
the shipped artifact. This script closes the loop at the artifact: it extracts every
.spv entry from the jar and runs spirv-val on each one.

spirv-val is a hard requirement: if it is missing the audit fails rather than silently
passing, because a release gate that no-ops when its tool is absent is worse than none.
"""

import shutil
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path


def main():
    if len(sys.argv) != 2:
        print("usage: verify_jar_spirv.py <celtium jar>", file=sys.stderr)
        return 2
    spirv_val = shutil.which("spirv-val")
    if spirv_val is None:
        print("spirv-val not found on PATH; install spirv-tools to run the jar SPIR-V audit",
              file=sys.stderr)
        return 1
    jar_path = Path(sys.argv[1])
    failures = []
    with zipfile.ZipFile(jar_path) as jar:
        spv_names = [name for name in jar.namelist() if name.endswith(".spv")]
        if not spv_names:
            print(f"no .spv entries in {jar_path}; nothing was packaged to validate",
                  file=sys.stderr)
            return 1
        with tempfile.TemporaryDirectory() as tmp:
            for name in spv_names:
                target = Path(tmp) / Path(name).name
                target.write_bytes(jar.read(name))
                result = subprocess.run([spirv_val, str(target)],
                                        capture_output=True, text=True)
                if result.returncode != 0:
                    failures.append((name, result.stderr.strip()))
    if failures:
        for name, error in failures:
            print(f"INVALID packaged SPIR-V: {name}\n{error}", file=sys.stderr)
        return 1
    print(f"jar SPIR-V audit: {len(spv_names)} packaged .spv entries, all valid")
    return 0


if __name__ == "__main__":
    sys.exit(main())
