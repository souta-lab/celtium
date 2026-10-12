#!/usr/bin/env python3
"""Fail if a field that had an initializer in the baseline now has none.

Written after two refactor-derived crashes: an extraction that moves a field but not its
initializer compiles, passes the CPU contract check, and dies on the first render tick. The
backend's `lastTerrainIndirectFallback = "none"` and the three per-format arrays arrived in the
extracted classes as bare declarations, so the first leg that printed the verification line threw
a NullPointerException - and the screen never appeared.

This check is the cheap half of that lesson: it compares every field declaration in the package
against the baseline revision (the R0 freeze, docs/plan.md section 0.383) and reports any field
whose baseline form carried an initializer and whose current form does not. It cannot prove the
initializer was *needed* - only a leg can - but it makes the loss impossible to miss.

    scripts/cpu-contract/field_initializer_check.py [baseline-revision]

Exit code 0 when nothing was lost, 1 with a list of the fields otherwise.
"""
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
PACKAGE = "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan"
DEFAULT_BASELINE = "5a22acb5b"  # the R0 freeze, before the R1-R7 extraction stretch

DECL = re.compile(r"^    private (?:static )?(?:final )?[\w<>.\[\], ?]+ (\w+)( = [^;]*)?;$")


def declarations(source):
    """{field name: initializer or None} for one file's text."""
    found = {}
    for line in source.split("\n"):
        m = DECL.match(line)
        if m:
            found[m.group(1)] = m.group(2)
    return found


def main():
    baseline = sys.argv[1] if len(sys.argv) > 1 else DEFAULT_BASELINE
    old_text = subprocess.run(["git", "show", "%s:%s/VulkanTerrainGpuBackend.java" % (baseline, PACKAGE)],
                              capture_output=True, text=True, cwd=ROOT).stdout
    if not old_text:
        raise SystemExit("could not read the baseline revision %s" % baseline)
    old = declarations(old_text)
    lost = []
    for path in sorted((ROOT / PACKAGE).glob("*.java")):
        for name, init in declarations(path.read_text(encoding="utf-8")).items():
            if init is None and old.get(name) is not None:
                lost.append((path.name, name, old[name].strip()))
    print("baseline: %s (%d fields with an initializer)" % (baseline, sum(1 for v in old.values() if v)))
    if lost:
        print("FIELDS THAT LOST THEIR INITIALIZER:")
        for file, name, init in lost:
            print("  %-28s %-34s baseline had %s" % (file, name, init))
        print("field_initializer_check: FAIL (%d)" % len(lost))
        return 1
    print("field_initializer_check: PASS (no field lost an initializer)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
