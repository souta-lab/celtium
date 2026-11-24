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

The comparison is by field name alone, so a new class that reuses a baseline name for a
different field reads as a loss. KNOWN_NAME_COLLISIONS below records the audited cases: each is
a constructor-injected `private final` field in a Phase 0 class, distinct from the backend
field of the same name (which still exists, initializer intact, only no longer `private`, so
this scan does not see it). A `final` field cannot be left unassigned - the compiler refuses -
so the accident this check guards against cannot occur there. Entries are (file, field) pairs,
and an entry applies only while the declaration is still `final`; anything else still fails.

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

DECL = re.compile(r"^    private (?:static )?(final )?[\w<>.\[\], ?]+ (\w+)( = [^;]*)?;$")

# Audited name collisions (see the module docstring): constructor-injected `private final`
# fields of the Phase 0 classes, not the backend fields whose names they reuse.
KNOWN_NAME_COLLISIONS = {
    ("NativePassRegistry.java", "targetRegistry"),
    ("PassExecutor.java", "targetRegistry"),
    ("PassExecutor.java", "publishedTextures"),
    # E10-a's census class (added after the baseline): its timestampPeriod is a distinct
    # field from the backend's (`= 1.0f`, still intact), assigned in the constructor from
    # the physical device's real VkPhysicalDeviceLimits.timestampPeriod.
    ("VulkanGpuTerrainCensus.java", "timestampPeriod"),
}


def declarations(source):
    """{field name: (initializer or None, is_final)} for one file's text."""
    found = {}
    for line in source.split("\n"):
        m = DECL.match(line)
        if m:
            found[m.group(2)] = (m.group(3), m.group(1) is not None)
    return found


def main():
    baseline = sys.argv[1] if len(sys.argv) > 1 else DEFAULT_BASELINE
    old_text = subprocess.run(["git", "show", "%s:%s/VulkanTerrainGpuBackend.java" % (baseline, PACKAGE)],
                              capture_output=True, text=True, cwd=ROOT).stdout
    if not old_text:
        raise SystemExit("could not read the baseline revision %s" % baseline)
    old = declarations(old_text)
    lost = []
    known = []
    for path in sorted((ROOT / PACKAGE).glob("*.java")):
        for name, (init, is_final) in declarations(path.read_text(encoding="utf-8")).items():
            if init is None and old.get(name, (None, False))[0] is not None:
                if is_final and (path.name, name) in KNOWN_NAME_COLLISIONS:
                    known.append((path.name, name))
                else:
                    lost.append((path.name, name, old[name][0].strip()))
    print("baseline: %s (%d fields with an initializer)" % (baseline, sum(1 for v in old.values() if v[0])))
    for file, name in known:
        print("  known name collision (constructor-injected final, audited): %s %s" % (file, name))
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
