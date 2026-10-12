#!/usr/bin/env python3
"""Compare the presented dumps of two runs, stage by stage.

The 2-jar experiment needs a fixture that both jars can drive, so the reference run and the candidate
run must carry the *same* fixture jar (the mod jar is what differs). This prints, for each stage, the
size, the distinct-colour count, the mean and the share of the most common colour, plus the number of
pixels differing by more than 32, and writes a difference image next to the candidate's dump.

    python3 scripts/refactor/presented-dump-compare.py REF_RUN_DIR CAND_RUN_DIR [--out-prefix P]

A stage the candidate does not have is reported as missing rather than as a match: a missing dump is
not a matching one.
"""

import argparse
import glob
import os
import sys
from collections import Counter

from PIL import Image


def dumps(run_dir):
    found = {}
    for path in glob.glob(os.path.join(run_dir, "screenshots", "*presented-*.png")):
        if path.endswith(".identity.json"):
            continue
        name = os.path.basename(path)
        parts = name.split("-")
        # celtium-presented-<stage>-run-<id>-<fixture>[-kept].png
        stage = parts[2] if len(parts) > 2 else name
        if stage not in found or "-kept" in name:
            found[stage] = path
    return found


def stats(path):
    image = Image.open(path).convert("RGB")
    pixels = list(image.getdata())
    counts = Counter(pixels)
    top = counts.most_common(1)[0]
    mean = tuple(int(sum(p[i] for p in pixels) / len(pixels)) for i in range(3))
    return image, len(counts), top[1] / len(pixels), mean


def main(argv):
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("reference")
    parser.add_argument("candidate")
    parser.add_argument("--out-prefix", default=".celtium-work/r212-diff")
    args = parser.parse_args(argv)

    ref = dumps(args.reference)
    cand = dumps(args.candidate)
    print("reference=%s stages=%s" % (args.reference, ",".join(sorted(ref))))
    print("candidate=%s stages=%s" % (args.candidate, ",".join(sorted(cand))))
    for stage in sorted(set(ref) | set(cand)):
        if stage not in ref or stage not in cand:
            print("  %-8s MISSING (%s)" % (stage, "reference" if stage not in ref else "candidate"))
            continue
        ref_image, ref_colours, ref_top, ref_mean = stats(ref[stage])
        cand_image, cand_colours, cand_top, cand_mean = stats(cand[stage])
        if ref_image.size != cand_image.size:
            print("  %-8s size differs: reference %s candidate %s" % (stage, ref_image.size, cand_image.size))
            continue
        ref_pixels = list(ref_image.getdata())
        cand_pixels = list(cand_image.getdata())
        big = sum(1 for a, b in zip(ref_pixels, cand_pixels)
                  if max(abs(a[i] - b[i]) for i in range(3)) > 32)
        diff = Image.new("RGB", ref_image.size)
        diff.putdata([(min(255, abs(a[0] - b[0]) * 4), min(255, abs(a[1] - b[1]) * 4),
                       min(255, abs(a[2] - b[2]) * 4))
                      for a, b in zip(ref_pixels, cand_pixels)])
        out = "%s-%s.png" % (args.out_prefix, stage)
        diff.save(out)
        print("  %-8s ref: distinct=%6d top=%.3f mean=%s | cand: distinct=%6d top=%.3f mean=%s | "
              "pixels>32: %d (%.2f%%) diff=%s"
              % (stage, ref_colours, ref_top, ref_mean, cand_colours, cand_top, cand_mean,
                 big, 100.0 * big / len(ref_pixels), out))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
