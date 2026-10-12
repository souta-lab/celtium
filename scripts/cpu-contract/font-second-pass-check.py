#!/usr/bin/env python3
"""The font's second pass, checked against the glyph atlas instead of against a counter.

Owner: **gui-text**, task-66 ("HEI tooltip: main-pass font ink missing"). The defect it exists to
measure: in the HEI acceptance frame the vanilla item tooltip (`(660,315)-(820,355)`, the string
"Gold Ore" at GUI scale 3) keeps only 238 of its 828 main-pass ink pixels, while the same instrument's
invariant (`shadow_without_main`, `font-shadow-main-check.py`) reads 585 of 797. HEI's own text in the
same frame is pixel-identical to the reference.

What this adds over `font-shadow-main-check.py`: that instrument needs the shadow colour and the main
colour and nothing else, which is why it can measure any frame - but it cannot say *which* glyph lost
*which* pixels, and it cannot tell "the second pass drew nothing" from "the second pass drew the wrong
texels". This one rebuilds the draw from the originals and compares:

  * `FontRenderer.drawString` (SRG `func_175065_a`, `FontRenderer.java:325-342`) runs the string
    twice: `renderString(s, x+1, y+1, colour, true)` then `renderString(s, x, y, colour, false)`.
  * `renderDefaultChar` (`FontRenderer.java:230-249`) emits one quad per glyph: cell
    `(i, j) = (ch % 16 * 8, ch / 16 * 8)`, `l = charWidth[ch]`, `f = l - 0.01`, the four vertices
    `(posX, posY)`, `(posX, posY + 7.99)`, `(posX + f - 1, posY)`, `(posX + f - 1, posY + 7.99)`
    and the matching `u = i .. (i + f - 1)`, `v = j .. (j + 7.99)` over the 128x128 `ascii.png`.
  * `charWidth` is computed the way `readFontTexture` computes it (rightmost inked cell column + 2 from
    the atlas itself), so no width table is trusted to a constant.

The reconstruction is exact where the original is: on the GL reference frame it predicts 828 ink
pixels and all 828 are the frame's white pixels (IoU 1.000, no pixel of one set outside the other), so
a deficit on another frame is that frame's, not the model's.

Usage:

  scripts/cpu-contract/font-second-pass-check.py <frame.png> [--text "Gold Ore"] \
      [--x 675 --y 321] [--scale 3] [--region 660,315,820,355] [--expect complete|equal-to REF]

Exit status: 0 when the second pass is complete over the region (or matches the expected frame), 1 when
it is not, with the numbers printed per glyph and per region. `--min-coverage` sets the accepted
fraction (default 1.0: every predicted ink pixel must be white in the frame).
"""
import argparse
import sys

import numpy as np
from PIL import Image

CELL = 8
ATLAS_SIZE = 128


def load_atlas(path):
    image = np.asarray(Image.open(path).convert("RGBA"))
    return image[:, :, 3] > 0, image.shape[0]


def cell_xy(ch, atlas_size):
    cell = atlas_size // 16
    return (ch % 16) * cell, (ch // 16) * cell


def char_width(alpha, ch, atlas_size):
    """Vanilla `readFontTexture`: the rightmost inked column of the cell, plus two."""
    i, j = cell_xy(ch, atlas_size)
    cell = atlas_size // 16
    for column in range(cell - 1, -1, -1):
        if alpha[j:j + cell, i + column].any():
            return column + 2
    return None


def quads(text, x, y, atlas_size, alpha):
    """The quads vanilla emits for a style-free string, in GUI coordinates."""
    out = []
    pen = float(x)
    for ch in text:
        if ch == " ":
            out.append((ch, None))
            pen += 4.0
            continue
        width = char_width(alpha, ord(ch), atlas_size)
        if width is None:
            out.append((ch, None))
            pen += 4.0
            continue
        i, j = cell_xy(ord(ch), atlas_size)
        f = width - 0.01
        pos = ((pen, y), (pen, y + 7.99), (pen + f - 1.0, y), (pen + f - 1.0, y + 7.99))
        uv = ((i / 128.0, j / 128.0), (i / 128.0, (j + 7.99) / 128.0),
              ((i + f - 1.0) / 128.0, j / 128.0), ((i + f - 1.0) / 128.0, (j + 7.99) / 128.0))
        # the shadow pass draws the same geometry one GUI pixel down and right
        shadow = tuple((px + 1.0, py + 1.0) for (px, py) in pos)
        out.append((ch, (i, j, width, pos, uv, shadow)))
        pen += width
    return out


def rasterise_glyph(quad, scale, alpha, atlas_size, width, height, offset=(0.0, 0.0)):
    """The ink a glyph quad paints: positions and uvs interpolated, atlas sampled nearest."""
    _, _, _, pos, uv, _ = quad
    mask = np.zeros((height, width), bool)
    texel = np.full((height, width, 2), -1, np.int32)
    points = [((px + offset[0]) * scale, (py + offset[1]) * scale) for (px, py) in pos]
    texels = [(u * atlas_size, v * atlas_size) for (u, v) in uv]
    for a, b, c in ((0, 1, 2), (1, 2, 3)):
        (ax, ay), (bx, by), (cx, cy) = points[a], points[b], points[c]
        (atx, aty), (btx, bty), (ctx, cty) = texels[a], texels[b], texels[c]
        det = (by - cy) * (ax - cx) + (cx - bx) * (ay - cy)
        if det == 0:
            continue
        lo_x = max(0, int(min(ax, bx, cx)) - 1)
        hi_x = min(width, int(max(ax, bx, cx)) + 2)
        lo_y = max(0, int(min(ay, by, cy)) - 1)
        hi_y = min(height, int(max(ay, by, cy)) + 2)
        if hi_x <= lo_x or hi_y <= lo_y:
            continue
        qx, qy = np.meshgrid(np.arange(lo_x, hi_x) + 0.5, np.arange(lo_y, hi_y) + 0.5)
        wa = ((by - cy) * (qx - cx) + (cx - bx) * (qy - cy)) / det
        wb = ((cy - ay) * (qx - cx) + (ax - cx) * (qy - cy)) / det
        wc = 1.0 - wa - wb
        inside = (wa >= -1e-9) & (wb >= -1e-9) & (wc >= -1e-9)
        if not inside.any():
            continue
        u = wa * atx + wb * btx + wc * ctx
        v = wa * aty + wb * bty + wc * cty
        ti = np.clip(np.floor(u).astype(int), 0, atlas_size - 1)
        tj = np.clip(np.floor(v).astype(int), 0, atlas_size - 1)
        hit = inside & alpha[tj, ti]
        mask[lo_y:hi_y, lo_x:hi_x] |= hit
        texel[lo_y:hi_y, lo_x:hi_x] = np.where(hit[..., None],
                np.stack((ti, tj), axis=-1), texel[lo_y:hi_y, lo_x:hi_x])
    return mask, texel


def main(argv):
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("frame", help="the presented frame or GL screenshot, as PNG")
    parser.add_argument("--atlas", default="forge122/build/rfg/minecraft-src/resources/assets/minecraft/textures/font/ascii.png")
    parser.add_argument("--text", default="Gold Ore")
    parser.add_argument("--x", type=int, default=675, help="the string's x in screen pixels (GUI x * scale)")
    parser.add_argument("--y", type=int, default=321)
    parser.add_argument("--scale", type=int, default=3)
    parser.add_argument("--region", default="660,315,820,355")
    parser.add_argument("--min-coverage", type=float, default=1.0,
            help="the accepted fraction of predicted second-pass ink that must be drawn (default 1.0)")
    parser.add_argument("--expect", help="another frame the second pass must match pixel for pixel")
    args = parser.parse_args(argv)

    alpha, atlas_size = load_atlas(args.atlas)
    image = np.asarray(Image.open(args.frame).convert("RGB"))
    height, width = image.shape[:2]
    white = image.min(axis=2) >= 250
    rx0, ry0, rx1, ry1 = (int(v) for v in args.region.split(","))

    predicted = np.zeros((height, width), bool)
    rows = []
    for ch, quad in quads(args.text, args.x / float(args.scale), args.y / float(args.scale), atlas_size, alpha):
        if quad is None:
            rows.append((ch, 0, 0))
            continue
        mask, _ = rasterise_glyph(quad, args.scale, alpha, atlas_size, width, height)
        predicted |= mask
        box = mask[ry0:ry1, rx0:rx1]
        drawn = int((box & white[ry0:ry1, rx0:rx1]).sum())
        rows.append((ch, int(box.sum()), drawn))

    region_predicted = predicted[ry0:ry1, rx0:rx1]
    region_white = white[ry0:ry1, rx0:rx1]
    total = int(region_predicted.sum())
    drawn_total = int((region_predicted & region_white).sum())
    extra = int((region_white & ~region_predicted).sum())
    coverage = drawn_total / total if total else 0.0

    print(f"{args.frame} {width}x{height} atlas={args.atlas} scale={args.scale} region={args.region}")
    print(f'  string "{args.text}" at ({args.x},{args.y})')
    print("  glyph  predicted  drawn")
    for ch, want, got in rows:
        if want:
            print(f"    {ch!r:5s} {want:8d} {got:6d}" + ("   MISSING" if got < want else ""))
    print(f"  second pass: predicted={total} drawn={drawn_total} missing={total - drawn_total} "
          f"coverage={coverage:.3f} ink-not-predicted={extra}")
    if extra:
        print(f"  note: {extra} white pixels are outside the reconstruction - the model or the pose is wrong "
              "for this frame, so the deficit above is not attributable to the second pass")

    status = 0
    if args.expect:
        other = np.asarray(Image.open(args.expect).convert("RGB"))
        other_white = other.min(axis=2) >= 250
        same = int((predicted & white == predicted & other_white).all()) if predicted.any() else 0
        mismatched = int((predicted & white != predicted & other_white).sum())
        print(f"  against {args.expect}: {mismatched} of {total} predicted pixels differ")
        if mismatched:
            status = 1
    if coverage < args.min_coverage:
        print(f"  FAIL the second pass drew {coverage:.1%} of the glyph ink, expected {args.min_coverage:.1%}")
        status = 1
    elif not status:
        print("  ok the second pass drew the whole glyph ink the reconstruction predicts")
    return status


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
