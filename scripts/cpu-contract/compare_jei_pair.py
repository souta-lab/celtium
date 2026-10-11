#!/usr/bin/env python3
"""Compare the inventory/JEI picture between the GL-less leg and the GL bridge leg.

Both are 1280x720 and both were taken at the same tick with the same screen open, which is what
made this comparison impossible until the fixture's tick hooks were fixed: the screen opens at 30
and closes at 190, and every capture used to happen at 200 or later.

Reported per region: mean absolute difference, and the fraction of pixels that differ by more
than 8. Regions are the ones the font work named - the JEI item count, the tooltip, and the
inventory panel itself - plus the whole frame as the denominator.
"""
import sys
from PIL import Image, ImageChops

REGIONS = {
    "whole frame": (0, 0, 1280, 720),
    "jei item count (1/16)": (1030, 20, 1140, 60),
    "tooltip (Gold Ore)": (660, 315, 820, 355),
    "inventory panel": (420, 120, 860, 600),
    "hotbar": (490, 660, 790, 720),
}


def main(gl_less, bridge):
    a = Image.open(gl_less).convert("RGB")
    b = Image.open(bridge).convert("RGB")
    if a.size != b.size:
        print("sizes differ: %s vs %s" % (a.size, b.size))
        return 1
    diff = ImageChops.difference(a, b)
    print("%-26s %10s %12s %12s" % ("region", "mean", ">8 fraction", "pixels>8"))
    for name, box in REGIONS.items():
        region = diff.crop(box)
        pixels = list(region.getdata())
        total = len(pixels)
        changed = 0
        acc = 0
        for pixel in pixels:
            value = max(pixel)
            acc += value
            if value > 8:
                changed += 1
        print("%-26s %10.3f %11.4f%% %12d" % (name, acc / float(total), 100.0 * changed / total, changed))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1], sys.argv[2]))
