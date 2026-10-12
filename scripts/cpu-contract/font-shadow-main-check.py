#!/usr/bin/env python3
"""Font glyphs: did the *main* pass draw every stroke its *shadow* pass drew?

Owner: **gui-text**. Unit: "GUI text darkens - part of it, the tooltip crop the user sent".

## The contract this checks

`FontRenderer.drawString(text, x, y, colour, dropShadow=true)` - SRG `func_175065_a` - calls
`renderString` **twice**: first at `(x+1, y+1)` with `shadow=true`, then at `(x, y)` with `shadow=false`
(`FontRenderer.java:325-342`). Both passes draw **the same glyph geometry**; the only difference is the
colour, which comes from the same `colorCode[]` table with the index shifted by 16 for the shadow
(`renderStringAtPos`, `FontRenderer.java:399-406`).

That gives an invariant that needs no string, no atlas and no reference picture:

    every pixel whose colour is exactly the shadow colour must have a pixel of exactly the main colour
    one GUI pixel up and to the left (scale, scale) - because the shadow pixel at q is the main glyph's
    ink at q - (1,1), and the main pass draws that pixel too.

A violation is therefore one of exactly two things:

* a **main-pass stroke that was not drawn** (the pixel shows the tooltip background or the shadow of a
  different stroke), or
* a pixel that is *coincidentally* the shadow colour and not part of a glyph run (an item texture, the
  world behind a translucent chat bar) - which is why a frame has to be read per region, and why a
  count is only a finding when the region is known to be text.

It cannot be produced by the colour contract: a leaked or stale colour moves the *whole* glyph to the
wrong colour, not a stroke of it. It cannot be produced by the sampler under `mag=NEAREST min=NEAREST`
either - a NEAREST sample of an opaque ink texel is opaque - so a violation at GUI scale N points at
the *coverage* (geometry, the strip's triangulation, the clip) or at a *state* that differs between the
two passes, which is why the GUI scale must be given rather than guessed.

## The trap this instrument exists for

`font-glyph-cell-check.py` (glyph-fix, `docs/history/2026-10-12-font-glyph-cell-edge.md`) checks the F3
overlay's line against `ascii.png`. The F3 text is drawn **without a drop shadow**
(`GuiOverlayDebug.call()`: `drawString(s, 2, 2 + 9*i, 14737632)`), so that check cannot see the shadow
pass at all, and the record says so: "the chat's texel check is not established (shadow + scale)".
This is the complementary half: it needs no known string, only the two colours and the GUI scale.

## Usage

    font-shadow-main-check.py FRAME.png --main 170,170,170 --shadow 42,42,42 --scale 3
    font-shadow-main-check.py FRAME.png --main 255,255,255 --shadow 63,63,63 --scale 3 --region 285,100,445,145
    font-shadow-main-check.py FRAME.png --main 255,255,255 --shadow 63,63,63 --auto-scale

`--auto-scale` tries 1..4 and reports the scale with the fewest violations per shadow pixel, which is
the GUI scale of the picture; `--region x0,y0,x1,y1` restricts the scan to a text region, which is what
turns a count into evidence (see the second bullet above).
"""

import argparse
import sys

from PIL import Image


def parse_colour(text):
    parts = text.split(",")
    if len(parts) != 3:
        raise argparse.ArgumentTypeError("a colour is R,G,B")
    return tuple(int(p) for p in parts)


def region_bounds(spec, size):
    if spec is None:
        return (0, 0, size[0], size[1])
    x0, y0, x1, y1 = (int(v) for v in spec.split(","))
    return (max(0, x0), max(0, y0), min(size[0], x1), min(size[1], y1))


def scan(image, main, shadow, scale, region):
    """{return (shadow_pixels, violations, scale_violations)} for one scale."""
    pixels = image.load()
    x0, y0, x1, y1 = region
    shadows = 0
    violations = []
    for y in range(max(y0, scale), y1):
        for x in range(max(x0, scale), x1):
            if pixels[x, y] == shadow:
                shadows += 1
                if pixels[x - scale, y - scale] != main:
                    violations.append((x, y))
    return shadows, violations


def main(argv):
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("frame", help="a presented frame or a crop of one, as PNG")
    parser.add_argument("--main", required=True, type=parse_colour, help="the main pass's text colour, R,G,B")
    parser.add_argument("--shadow", required=True, type=parse_colour, help="the shadow pass's colour, R,G,B")
    parser.add_argument("--scale", type=int, default=0, help="the GUI scale of the picture (0 = use --auto-scale)")
    parser.add_argument("--auto-scale", action="store_true", help="try scales 1..4 and report each")
    parser.add_argument("--region", help="x0,y0,x1,y1 - restrict the scan to a text region")
    parser.add_argument("--list", type=int, default=0, help="print at most this many violating pixels")
    args = parser.parse_args(argv)

    image = Image.open(args.frame).convert("RGB")
    region = region_bounds(args.region, image.size)
    print(f"frame={args.frame} size={image.size} region={region} "
          f"main={args.main} shadow={args.shadow}")

    scales = [args.scale] if args.scale else ([1, 2, 3, 4] if args.auto_scale else [1])
    best = None
    for scale in scales:
        shadows, violations = scan(image, args.main, args.shadow, scale, region)
        rate = 0.0 if shadows == 0 else 100.0 * len(violations) / shadows
        print(f"  scale={scale} shadow_px={shadows} shadow_without_main={len(violations)} ({rate:.1f} %)")
        if best is None or (shadows > 0 and len(violations) < best[2]):
            best = (scale, shadows, len(violations), violations)
    if best is not None and args.list:
        for x, y in best[3][:args.list]:
            print(f"    ({x},{y})")
    # The verdict is deliberately about the instrument, not about a defect: a region that is not text
    # can only ever produce false positives, and the caller owns that judgement.
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
