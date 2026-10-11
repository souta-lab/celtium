#!/usr/bin/env python3
"""The video-settings acceptance, as the self-consistency claim the Lead decided on (2026-10-12).

There is no Celeritas-screen reference: the diverter that opens `CeleritasVideoOptionsScreen` lives in *our* tree
(`forge122/.../mixin/features/options/MixinGuiOptions.java:20`), so an upstream leg opens vanilla's
`GuiVideoSettings` - which is why the stored references (`vs47d-ref`, `wbs-vs-ref2`) both record the fallback text and
why the 139-colour panel figure belongs to vanilla's screen and must not be applied to this one. So this script
measures the Celeritas screen against **itself** (an earlier frame of our own jar, same scene and flags) and against
the named properties, and reports the region statistics on the frames themselves:

  * crop `(180,80,1100,624)` - the panel region the plan's table uses;
  * `rows-left (180,90,620,620)` and `Done (340,640,940,690)` - the same controls, so a number can be compared
    across legs even when the reference is another of our frames;
  * `hotbar frame` - the strip below the Done button, which is world content and must not move;
  * distinct colours inside the crop, which is what "the panel is drawn rather than a flat fill" is.

Run:  python3 video-settings-crop-check.py <candidate-frame.png> [<our-earlier-frame.png>]

Both frames must be 1280x720. The candidate is our presented dump; the second frame is optional and is used for the
ratio columns (the plan's table is ratio + fraction over 8).
"""
import os
import sys

sys.path.insert(0, "")
from PIL import Image

REGIONS = {
    "panel (180,80,1100,624)": (180, 80, 1100, 624),
    "rows-left (180,90,620,620)": (180, 90, 620, 620),
    "Done (340,640,940,690)": (340, 640, 940, 690),
    "hotbar frame (360,690,920,720)": (360, 690, 920, 720),
}


def stats(im, box):
    crop = im.crop(box)
    px = crop.load()
    n = crop.size[0] * crop.size[1]
    sums = [0, 0, 0]
    colours = set()
    for y in range(crop.size[1]):
        for x in range(crop.size[0]):
            p = px[x, y]
            sums[0] += p[0]
            sums[1] += p[1]
            sums[2] += p[2]
            colours.add(p)
    return tuple(s / n for s in sums), len(colours), crop


def compare(a, b):
    """mean|d| per channel, ratio of the means, and the fraction of pixels over 8 in any channel."""
    over = 0
    n = a.size[0] * a.size[1]
    pa, pb = a.load(), b.load()
    diffs = [0, 0, 0]
    for y in range(a.size[1]):
        for x in range(a.size[0]):
            q, r = pa[x, y], pb[x, y]
            d = (abs(q[0] - r[0]), abs(q[1] - r[1]), abs(q[2] - r[2]))
            diffs[0] += d[0]
            diffs[1] += d[1]
            diffs[2] += d[2]
            if max(d) > 8:
                over += 1
    return tuple(d / n for d in diffs), over / n


if __name__ == "__main__":
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(2)
    cand = Image.open(sys.argv[1]).convert("RGB")
    ref = Image.open(sys.argv[2]).convert("RGB") if len(sys.argv) > 2 else None
    print("candidate %s %s" % (sys.argv[1], cand.size))
    if ref is not None:
        print("earlier   %s %s" % (sys.argv[2], ref.size))
    for name, box in REGIONS.items():
        mean, colours, crop = stats(cand, box)
        line = "  %-30s mean=%s distinct=%d" % (name, tuple(round(v, 1) for v in mean), colours)
        if ref is not None:
            rmean, rcolours, rcrop = stats(ref, box)
            mad, frac = compare(crop, rcrop)
            line += " | vs earlier: ratio=%s mean|d|=%s >8=%.2f%% (distinct %d)" % (
                tuple(round(mean[i] / rmean[i], 3) for i in range(3)),
                tuple(round(v, 2) for v in mad), 100.0 * frac, rcolours)
        print(line)
