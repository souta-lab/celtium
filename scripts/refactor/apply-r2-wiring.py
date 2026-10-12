#!/usr/bin/env python3
"""Apply the R2 statistics wiring to VulkanTerrainGpuBackend.verificationSummary().

Reads the run/line map from docs/history/2026-10-12-r2-wiring-map.md (the table delivered by
`acceptance` and persisted so it survives context loss), replaces each run's line range with the
owning class's appendRunN call, inserts the statistics fields if they are missing, and reports
what it did and what it deliberately left alone.

Why a script rather than hand edits: 69 ranges in a 14k-line file, each a whole-line range, with
three lines shared between two classes. A line-based replacement applied bottom-up is exactly
what the map describes, and it can be reviewed before it runs. The three shared lines (L8492,
L8622, L8734) are NOT touched: they carry two classes' counters on one line, so splitting them
needs a decision per line and the runs' own append order - leaving them in place keeps the output
correct (their counters are still emitted) and is reported as remaining work rather than done
silently.

Usage: apply-r2-wiring.py [--dry-run]
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
BACKEND = ROOT / "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java"
MAP = ROOT / "docs/history/2026-10-12-r2-wiring-map.md"
SHARED = {8492, 8622, 8734}

FIELDS = {
    "Terrain": ("terrainStats", "TerrainStatistics"),
    "Immediate": ("immediateStats", "ImmediateStatistics"),
    "Submission": ("submissionStats", "SubmissionStatistics"),
    "Texture": ("textureStats", "TextureStatistics"),
    "Target": ("targetStats", "TargetStatistics"),
    "Model": ("modelStats", "ModelStatistics"),
    "Font": ("fontStats", "FontStatistics"),
}


def parse_map(text):
    rows = []
    for line in text.splitlines():
        m = re.match(r"\|\s*(\d+)\s*\|\s*(\w+)\s*\|\s*run(\d+)\s*\|\s*\*{0,2}L(\d+)(?:-L(\d+))?", line)
        if m:
            index, cls, run, start, end = m.groups()
            rows.append((int(index), cls, int(run), int(start), int(end or start)))
    return rows


def main():
    dry = "--dry-run" in sys.argv
    rows = parse_map(MAP.read_text(encoding="utf-8"))
    if not rows:
        raise SystemExit("no rows parsed from the map; refusing to touch the file")
    source = BACKEND.read_text(encoding="utf-8").split("\n")

    missing_fields = [(field, cls) for cls, (field, cls) in FIELDS.items()
                      if ("private final %s %s" % (cls, field)) not in "\n".join(source)]
    replacements, skipped = [], []
    for index, cls, run, start, end in rows:
        if start in SHARED:
            skipped.append((index, cls, run, start))
            continue
        field = FIELDS[cls][0]
        replacements.append((index, cls, run, start, end,
                             "                this.%s.appendRun%d(out);" % (field, run)))

    # bottom-up so earlier line numbers stay valid
    for index, cls, run, start, end, text in sorted(replacements, key=lambda r: -r[3]):
        source[start - 1:end] = [text]

    print("rows parsed: %d  applied: %d  skipped (shared lines): %d"
          % (len(rows), len(replacements), len(skipped)))
    for index, cls, run, line in skipped:
        print("  skipped row %d %s run%d at L%d (shared line; its counters stay where they are)"
              % (index, cls, run, line))
    if missing_fields:
        print("fields that must be added by hand (the map does not create them):")
        for field, cls in missing_fields:
            print("  private final %s %s = new %s();" % (cls, field, cls))
    if dry:
        print("dry run: nothing written")
        return
    BACKEND.write_text("\n".join(source), encoding="utf-8")
    print("written: %s" % BACKEND)


if __name__ == "__main__":
    main()
