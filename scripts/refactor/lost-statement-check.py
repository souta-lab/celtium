#!/usr/bin/env python3
"""Which statements did the refactoring lose?

The extraction moved thousands of lines out of
`VulkanTerrainGpuBackend.java` into owner classes. Moving a *field* without its initializer
compiles, passes every CPU contract check and kills the game on the first render tick - that
class of loss cost two crashes and is now covered by `field_initializer_check.py`. Losing a
*statement* is the same kind of loss one level down: it compiles, the line still prints, and
the camera, the panorama or the loading screen simply stops being updated.

So this check reads two revisions of the module and asks a single question: is there a
statement in the baseline that no longer exists anywhere in the current tree?

    python3 scripts/refactor/lost-statement-check.py [--baseline REV] [--module DIR] [--all]

Normalisation, because a moved statement legitimately changes its spelling:

  * comments and whitespace are removed;
  * a dotted name keeps only its last component, so `this.terrain.writeUniforms(...)` and
    `writeUniforms(...)` are the same statement;
  * `this.` is dropped for the same reason.

What it cannot do: tell a lost statement from a deleted one. A removal that a commit message
justifies is expected output, and every line it prints has to be checked against the current
code before it is called a bug. The default output is limited to lines whose normalised form
*looks like an effect* - an assignment, an increment, a call or a return - because a lost
declaration is the other check's job.

Exit status is always 0: this is a report, not a gate. The reading is the deliverable.
"""

import argparse
import re
import subprocess
import sys
from collections import Counter

MODULE = "celtium-vulkan/src/main/java"

BLOCK_COMMENT = re.compile(r"/\*.*?\*/", re.S)
LINE_COMMENT = re.compile(r"//[^\n]*")
STRING_LITERAL = re.compile(r'"(?:\\.|[^"\\])*"')
NAME = re.compile(r"@?[A-Za-z_$][A-Za-z0-9_$]*(?:\s*\.\s*[A-Za-z_$][A-Za-z0-9_$]*)*")
EFFECT = re.compile(r"(=|--|\+\+|\(|\breturn\b)")


def strip_comments(text):
    text = BLOCK_COMMENT.sub(" ", text)
    return LINE_COMMENT.sub("", text)


def normalise(line):
    """One canonical string for a statement, with the qualification removed."""
    line = line.strip()
    if not line or line in "{};":
        return None

    def name(match):
        parts = [p.strip() for p in match.group(0).split(".")]
        return parts[-1]

    line = NAME.sub(name, line)
    line = re.sub(r"\s+", " ", line)
    return line


def statements(text):
    """Normalised statements of a Java source, as a multiset."""
    out = Counter()
    for raw in strip_comments(text).split("\n"):
        # a statement can wrap several source lines; split on the terminators so a re-wrapped
        # move still produces the same statement
        for piece in re.split(r"(?<=[;{}])", raw):
            norm = normalise(piece)
            if norm:
                out[norm] += 1
    return out


def java_files(rev, module):
    if rev is None:
        out = subprocess.run(["git", "ls-files", module], capture_output=True, text=True, check=True)
        return [p for p in out.stdout.split() if p.endswith(".java")]
    out = subprocess.run(["git", "ls-tree", "-r", "--name-only", rev, module],
                         capture_output=True, text=True, check=True)
    return [p for p in out.stdout.split() if p.endswith(".java")]


def read(rev, path):
    if rev is None:
        with open(path) as handle:
            return handle.read()
    return subprocess.run(["git", "show", "%s:%s" % (rev, path)],
                          capture_output=True, text=True, check=True).stdout


def main(argv):
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--baseline", default="5a22acb5b",
                        help="the revision the extraction started from (default: %(default)s)")
    parser.add_argument("--module", default=MODULE, help="the module to sweep")
    parser.add_argument("--all", action="store_true",
                        help="print every unmatched line, not only effect-looking ones")
    parser.add_argument("--direction", choices=("lost", "extra"), default="lost",
                        help="lost: statements the baseline has and the tree does not; "
                             "extra: statements the tree now runs more often than the baseline did")
    args = parser.parse_args(argv)

    before = Counter()
    origin = {}
    for path in java_files(args.baseline, args.module):
        for stmt, count in statements(read(args.baseline, path)).items():
            before[stmt] += count
            origin.setdefault(stmt, path)

    after = Counter()
    for path in java_files(None, args.module):
        after.update(statements(read(None, path)))

    missing = [(stmt, count - after.get(stmt, 0)) for stmt, count in before.items()
               if after.get(stmt, 0) < count]
    extra = [(stmt, count - before.get(stmt, 0)) for stmt, count in after.items()
             if count > before.get(stmt, 0)]
    if args.direction == "extra":
        rows = [(stmt, n) for stmt, n in extra if args.all or EFFECT.search(stmt)]
    else:
        rows = [(stmt, n) for stmt, n in missing if args.all or EFFECT.search(stmt)]
    rows.sort(key=lambda item: (-item[1], item[0]))

    total = sum(n for _, n in rows)
    print("baseline=%s module=%s statements=%d current=%d direction=%s rows=%d (%s)"
          % (args.baseline, args.module, sum(before.values()), sum(after.values()),
             args.direction, total, "all" if args.all else "effect-looking only"))
    for stmt, count in rows:
        print("  x%-3d %s" % (count, stmt))
        if args.direction == "lost":
            print("        e.g. %s" % origin[stmt])
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
