#!/usr/bin/env python3
"""HEI's real screen: what the picture says about the mod's item list, search box and icon geometry.

Owner: **gui-text**, for Goal C's HEI (Had Enough Items 4.31.2) acceptance. Companion to
`compare_jei_pair.py`, which reports the region-by-region difference between two legs; this one reports
what a *single* frame of the scene contains, so the same numbers can be read off the candidate and the
reference leg and compared without either being the other's input.

Two measurements, both of them things a wrong renderer changes:

* **cell fill fraction.** Every item slot draws a 16x16 icon into an 18x18 slot. An isometric block icon
  covers roughly 70 % of that square; a *flat* texture filled edge to edge covers ~1.0, and that is the
  shape the user's own JEI screenshots had (`docs/plan.md`, task-50: candidate `0.736, 0.722, ...`
  against reference `0.692, 0.722, ...`). The grid geometry is *detected*, not hard-coded: the slot
  background colour is found as the modal colour of the panel, its row pitch from the runs of that
  colour, so a different GUI scale or window size does not silently measure the wrong squares.
* **the search box and the item-list strip.** The search box is the dark rectangle at the bottom of the
  overlay; the count of pure-white pixels inside it is the text it is showing (HEI draws the filter text
  there). The strip's distinct-colour count and its non-background fraction say whether a filtered list
  (few items, mostly background) and an unfiltered one (hundreds of icons) are the pictures they claim.

The verdict is deliberately not in here: this prints numbers and writes crops, and the caller owns what
they mean. A missing counter is not a missing draw, but a *right-looking* number over the wrong
rectangle is worse - which is why the geometry is detected and printed with the result.
"""

import argparse
import statistics
import sys
from collections import Counter

from PIL import Image


def detect_grid(image, panel, icon):
    """{return (origin_x, origin_y, pitch, cols, rows)} of the slot grid inside `panel`.

    The pitch is measured from the slot background's own periodicity along x - the modal colour of the panel
    repeats with the slot pitch - and the origin is the first column and row at which that colour appears.
    The earlier version read the *bands* of slot-coloured rows as the pitch and got 42 for an 18 GUI px slot
    at scale 3 (54 px): each slot draws two slot-coloured outlines, so the band spacing alternates. The
    periodicity of the profile has one answer, and it is checked against the icon square before use.
    """
    pixels = image.load()
    x0, y0, x1, y1 = panel
    colours = Counter(pixels[x, y] for y in range(y0, y1) for x in range(x0, x1))
    slot, _ = colours.most_common(1)[0]
    profile = [sum(1 for y in range(y0, y1) if pixels[x, y] == slot) for x in range(x0, x1)]
    best = (0, 0)
    for period in range(icon, icon * 2):
        score = sum(profile[i] * profile[i + period] for i in range(0, len(profile) - period))
        if score > best[0]:
            best = (score, period)
    pitch = best[1]
    if pitch == 0:
        return None
    columns = [x for x in range(x0, x1) if profile[x - x0] > (y1 - y0) * 0.15]
    if not columns:
        return None
    origin_x = min(columns) + 3
    rows = []
    for y in range(y0, y1):
        run = sum(1 for x in range(origin_x, min(origin_x + pitch, x1)) if pixels[x, y] == slot)
        if run > pitch * 0.15:
            rows.append(y)
    if not rows:
        return None
    # The first row of the grid is the first row of the band that repeats at the pitch.
    origin_y = rows[0] + 3
    cols = max(1, (max(columns) - min(columns) + 1) // pitch)
    row_count = max(1, (y1 - origin_y) // pitch)
    return origin_x, origin_y, pitch, cols, row_count


def cell_fill(image, origin_x, origin_y, pitch, cols, rows, icon):
    """{return the per-slot fill fractions}: pixels in the icon square that differ from its modal colour."""
    pixels = image.load()
    width, height = image.size
    fills = []
    for r in range(rows):
        for c in range(cols):
            x0 = origin_x + c * pitch
            y0 = origin_y + r * pitch
            if x0 < 0 or y0 < 0 or x0 + icon > width or y0 + icon > height:
                continue
            square = [pixels[x, y] for y in range(y0, y0 + icon) for x in range(x0, x0 + icon)]
            modal, _ = Counter(square).most_common(1)[0]
            filled = sum(1 for p in square if p != modal)
            fills.append(filled / float(icon * icon))
    return fills


def main(argv):
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("frame", help="a presented frame or the GL screenshot of the HEI scene")
    parser.add_argument("--label", default="frame")
    parser.add_argument("--panel", default="280,140,900,620", help="x0,y0,x1,y1 of the slot panel")
    parser.add_argument("--search-box", default="950,655,1270,712", help="x0,y0,x1,y1 of the search box")
    parser.add_argument("--overlay", default="950,100,1280,640", help="x0,y0,x1,y1 of the item-list strip")
    parser.add_argument("--icon", type=int, default=48, help="icon square in pixels (16 GUI px at scale 3)")
    parser.add_argument("--crops", help="directory to write the tooltip/search/overlay crops into")
    args = parser.parse_args(argv)

    image = Image.open(args.frame).convert("RGB")
    panel = tuple(int(v) for v in args.panel.split(","))
    search = tuple(int(v) for v in args.search_box.split(","))
    overlay = tuple(int(v) for v in args.overlay.split(","))

    print(f"{args.label}: {args.frame} size={image.size}")
    grid = detect_grid(image, panel, args.icon)
    if grid is None:
        print("  slot grid: not detected in the panel - the fill numbers would be over the wrong squares")
    else:
        origin_x, origin_y, pitch, cols, rows = grid
        fills = cell_fill(image, origin_x, origin_y, pitch, cols, rows, args.icon)
        print(f"  slot grid: origin=({origin_x},{origin_y}) pitch={pitch} cols={cols} rows={rows}"
              f" -> slots measured={len(fills)}")
        if fills:
            print(f"  cell fill: min={min(fills):.3f} median={statistics.median(fills):.3f}"
                  f" max={max(fills):.3f} mean={statistics.mean(fills):.3f}")
            print("  per-slot: " + " ".join(f"{f:.2f}" for f in fills))

    pixels = image.load()
    dark = sum(1 for y in range(search[1], search[3]) for x in range(search[0], search[2])
               if sum(pixels[x, y]) < 120)
    white = sum(1 for y in range(search[1], search[3]) for x in range(search[0], search[2])
                if pixels[x, y] == (255, 255, 255))
    print(f"  search box {search}: dark={dark} pure-white(text)={white}")

    colours = Counter(pixels[x, y] for y in range(overlay[1], overlay[3])
                      for x in range(overlay[0], overlay[2]))
    total = sum(colours.values())
    background, count = colours.most_common(1)[0]
    print(f"  item-list strip {overlay}: distinct={len(colours)}"
          f" background={background} backgroundFraction={count / float(total):.3f}")

    if args.crops:
        import os
        os.makedirs(args.crops, exist_ok=True)
        image.crop((660, 315, 820, 355)).save(os.path.join(args.crops, args.label + "-tooltip.png"))
        image.crop(search).save(os.path.join(args.crops, args.label + "-search.png"))
        image.crop(overlay).save(os.path.join(args.crops, args.label + "-overlay.png"))
        print(f"  crops -> {args.crops}/{args.label}-{{tooltip,search,overlay}}.png")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
