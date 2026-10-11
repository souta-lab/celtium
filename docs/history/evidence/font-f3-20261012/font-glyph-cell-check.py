#!/usr/bin/env python3
"""Does the F3 overlay's text sample its own glyph cell, and nothing else?

Answers unit "glyph-fix", prompt 1: the hypothesis was that the vanilla glyph quad's
UVs touch the 8x8-texel cell boundary of the 128x128 ascii.png sheet, so a LINEAR
sampler would pick up the neighbouring glyph's edge column. This script measures it
against a real presented frame instead of arguing about it:

  * it rebuilds the expected glyph geometry from the decompiled FontRenderer
    (`renderDefaultChar`: u0 = (ch%16)/16, u1 = u0 + (charWidth-1.01)/16,
     v0 = (ch/16)/16, v1 = v0 + 7.99/128) and from the actual sheet's charWidth[],
  * it checks every expected stroke texel against the presented frame at 3x3 screen
    pixels per sheet texel (GUI scale 3), and every expected-empty texel for bleed,
  * it renders the amplified background residual, which is where the user's "subtle
    artifacts between characters" actually live.

Inputs (all inside the repository):
  frame  .celtium-work/runtime/font-f3/screenshots/
         celtium-presented-world-run-1727828e99559-66d3c617-kept.png
         sha256 1740c1ab67ac84b890f63398807968957e48d3d180401781e2306f9965798d4d
  sheet  forge122/build/rfg/minecraft-src/resources/assets/minecraft/textures/font/ascii.png

Run:  python3 docs/history/evidence/font-f3-20261012/font-glyph-cell-check.py
"""
import os
import statistics
import sys

sys.path.insert(0, "")
from PIL import Image

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", "..", ".."))
# Any F3-overlay presented frame can be checked: pass its path as the first argument. The default is the
# leg the check was written on (jar 7acf4a9d-era), so the same measurement can be run before and after a
# change to the glyph path - the strip-separator fix was verified this way on the leg in
# .celtium-work/runtime/font-strip.
FRAME = os.path.join(ROOT, ".celtium-work/runtime/font-f3/screenshots",
                     "celtium-presented-world-run-1727828e99559-66d3c617-kept.png")
if len(sys.argv) > 1:
    FRAME = os.path.abspath(sys.argv[1])
SHEET = os.path.join(ROOT, "forge122/build/rfg/minecraft-src/resources/assets/"
                           "minecraft/textures/font/ascii.png")
OUT = os.path.dirname(os.path.abspath(__file__))

# The F3 left column, from the decompiled GuiOverlayDebug.call() and its helpers.
LINE_1 = "Minecraft 1.12.2 (1.12.2/fml,forge,cleanroom)"   # call() line 0
LINE_4 = "E: 0/1, B: 0"                                   # getDebugInfoEntities(), line 3
PEN_X_GUI = 2          # drawString(s, 2, i1, ...); i1 = 2 + 9*i
PEN_Y_GUI = {1: 2, 4: 29}
TEXEL_PX = 3           # GUI scale 3: one sheet texel is 3x3 screen pixels

sheet = Image.open(SHEET).convert("RGBA")
sp = sheet.load()


def cell(ch):
    c, r = ch % 16, ch // 16
    return [[sp[c * 8 + x, r * 8 + y][3] for x in range(8)] for y in range(8)]


def char_width(ch):
    """FontRenderer.readFontTexture: rightmost non-transparent column + 1, plus one."""
    c = cell(ch)
    last = -1
    for x in range(7, -1, -1):
        if any(c[y][x] for y in range(8)):
            last = x
            break
    return last + 2


def advance(ch):
    """renderChar returns 4.0F for space and nbsp without emitting a quad."""
    return 4 if ch in (" ", "\u00a0") else char_width(ord(ch))


def check(px, text, line, label):
    pen, stroked, ok, empties, bleed, missing = PEN_X_GUI, 0, 0, 0, 0, []
    ytop = PEN_Y_GUI[line] * TEXEL_PX
    for ch in text:
        if ch in (" ", "\u00a0"):
            pen += 4
            continue
        w, c = char_width(ord(ch)), cell(ord(ch))
        for t in range(w - 1):                       # the quad covers texels 0..w-2
            for ty in range(7):
                x, y = (pen + t) * TEXEL_PX, ytop + ty * TEXEL_PX
                drawn = all(px[x + dx, y + dy][0] >= 200
                            for dx in range(TEXEL_PX) for dy in range(TEXEL_PX))
                if c[ty][t]:
                    stroked += 1
                    if drawn:
                        ok += 1
                    else:
                        missing.append((ch, t, ty, x, y))
                else:
                    empties += 1
                    if drawn:
                        bleed += 1
        pen += advance(ch)
    print("  %-8s sheet strokes=%4d drawn exactly=%4d missing=%3d | empty texels=%4d "
          "drawn as a stroke=%d" % (label, stroked, ok, len(missing), empties, bleed))
    for m in missing[:5]:
        print("        missing stroke:", m)
    return stroked, ok, empties, bleed


def lum(p):
    return (p[0] * 299 + p[1] * 587 + p[2] * 114) // 1000


def expected_vs_observed(px, text, line, path):
    """Side by side: glyphs rebuilt from the sheet, the frame, and the difference."""
    ytop = PEN_Y_GUI[line] * TEXEL_PX
    width = sum((char_width(ord(c)) if c not in (" ", "\u00a0") else 4) for c in text)
    exp = Image.new("RGB", (width * TEXEL_PX, 7 * TEXEL_PX), (0, 0, 0))
    ep = exp.load()
    pen = 0
    for ch in text:
        if ch in (" ", "\u00a0"):
            pen += 4
            continue
        w, c = char_width(ord(ch)), cell(ord(ch))
        for t in range(w - 1):
            for ty in range(7):
                if c[ty][t]:
                    for dx in range(TEXEL_PX):
                        for dy in range(TEXEL_PX):
                            ep[(pen + t) * TEXEL_PX + dx, ty * TEXEL_PX + dy] = (224, 224, 224)
        pen += w
    obs = Image.new("RGB", exp.size)
    op = obs.load()
    diff = Image.new("RGB", exp.size)
    dp = diff.load()
    for x in range(exp.size[0]):
        for y in range(exp.size[1]):
            v = px[PEN_X_GUI * TEXEL_PX + x, ytop + y]
            op[x, y] = v
            want = ep[x, y][0] >= 200
            got = v[0] >= 200
            dp[x, y] = (0, 160, 0) if (want and got) else \
                       (200, 0, 0) if (want and not got) else \
                       (0, 0, 200) if (got and not want) else (30, 30, 30)
    scale = 3
    sheet_img = Image.new("RGB", (exp.size[0] * scale, exp.size[1] * scale * 3 + 24), (20, 20, 20))
    for i, img in enumerate((exp, obs, diff)):
        sheet_img.paste(img.resize((exp.size[0] * scale, exp.size[1] * scale), Image.NEAREST),
                        (0, i * (exp.size[1] * scale + 12)))
    sheet_img.save(path)
    print("  wrote", os.path.relpath(path, ROOT),
          "(top: from ascii.png, middle: presented frame, bottom: green=match red=missing blue=unexpected)")


def ghost(path):
    """Amplified background residual of line 4: the artifact the user reports."""
    ytop, ybot = PEN_Y_GUI[4] * TEXEL_PX, (PEN_Y_GUI[4] + 8) * TEXEL_PX
    x0, x1 = 3, 172
    out = Image.new("RGB", (x1 - x0, ybot - ytop))
    op = out.load()
    for x in range(x0, x1):
        bg = statistics.median([lum(px[x, y]) for y in (84, 85, 86)])
        for y in range(ytop, ybot):
            a = max(0, min(255, int(128 + 8 * (lum(px[x, y]) - bg))))
            op[x - x0, y - ytop] = (a, a, a)
    out = out.resize((out.size[0] * 3, out.size[1] * 3), Image.NEAREST)
    out.save(path)
    print("  wrote", os.path.relpath(path, ROOT))


CONTROL = os.path.join(ROOT, ".celtium-work/runtime/ww39-f3-off-batch/screenshots",
                       "celtium-presented-world-run-16d2d8b2a80e0-66d3c617-kept.png")


def control(path, label):
    """The same screen region with the F3 overlay off, from the only F3-off leg that
    kept a presented frame. Weak control: different leg, so different tick and camera;
    it can only say whether a smooth sky in that screen region carries 1-px lines."""
    img = Image.open(path).convert("RGB")
    p = img.load()

    def hp(x, y):
        return lum(p[x, y]) - (lum(p[x - 2, y]) + lum(p[x + 2, y])) / 2.0

    lines = 0
    for x in range(5, 170):
        col = [hp(x, y) for y in range(87, 111)]
        if sum(1 for v in col if abs(v) >= 5) >= 12:
            lines += 1
    print("  %-28s %d of 165 columns are 1-px vertical lines in rows 87-110; "
          "corner rgb %s" % (label, lines, p[5, 100]))


if __name__ == "__main__":
    frame = Image.open(FRAME).convert("RGB")
    px = frame.load()
    print("frame %s  %dx%d" % (os.path.relpath(FRAME, ROOT), frame.size[0], frame.size[1]))
    # Outputs carry the leg's own name, so running the check on a second leg cannot overwrite the first leg's
    # evidence: the before/after pair for the strip-separator fix is font-f3-* against font-strip-*.
    tag = os.path.basename(os.path.dirname(FRAME))
    if tag == "screenshots":                      # <leg>/screenshots/<frame>.png
        tag = os.path.basename(os.path.dirname(os.path.dirname(FRAME)))
    print("texel-exactness of the F3 overlay glyphs against ascii.png (GUI scale 3,")
    print("space advances 4 GUI units and emits no quad):")
    check(px, LINE_1, 1, "line 1")
    check(px, LINE_4, 4, "line 4")
    expected_vs_observed(px, LINE_1, 1, os.path.join(OUT, "%s-line1-sheet-vs-frame.png" % tag))
    ghost(os.path.join(OUT, "%s-line4-background-residual.png" % tag))
    # The bars, counted honestly. The high-pass reads x+-2, so a column within two pixels of a glyph stroke (or of the
    # F3 rect's right edge) reports a large value without carrying a bar - that footprint inflated the first version of
    # this count to "105 of 165". A column is usable only if neither it nor its +-2 neighbours carry a stroke.
    stroke_cols = set()
    pen = PEN_X_GUI
    for ch in LINE_4:
        if ch in (" ", "\u00a0"):
            pen += 4
            continue
        w, c = char_width(ord(ch)), cell(ord(ch))
        for t in range(w - 1):
            for ty in range(7):
                if c[ty][t]:
                    for dx in range(TEXEL_PX):
                        stroke_cols.add((pen + t) * TEXEL_PX + dx)
        pen += w
    usable = [x for x in range(5, 170)
              if all((x + d) not in stroke_cols for d in (-2, -1, 0, 1, 2))]
    bars = []
    for x in usable:
        col = [lum(px[x, y]) - (lum(px[x - 2, y]) + lum(px[x + 2, y])) / 2.0
               for y in range(87, 111)]
        if sum(1 for v in col if abs(v) >= 5) >= 12:
            bars.append(x)
    print("1-px bars in line 4's rect rows 87-110, detector-safe columns only:")
    print("  %-28s %d of %d usable columns %s" % ("F3 on (this leg)", len(bars), len(usable), bars[:16]))
    control(CONTROL, "F3 off (ww39, different leg)")
