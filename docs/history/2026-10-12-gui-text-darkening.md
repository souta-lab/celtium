# GUI text darkening: the tooltip crop's colours are vanilla-exact, and its one real difference is a main-pass stroke loss that the current tree does not reproduce

Owner: **gui-text**. Unit: the user's report of 2026-10-12, verbatim 「GUI上の文字の一部が暗くなる問題」, with the
item-tooltip crop the Lead copied to `.celtium-work/evidence/user-reports/2026-10-12-tooltip-darkening.png`
(358x124, sha256 `771c1b89c9e7f2748c055e1c0143723afa9a96f9584d4e1ad136e85387121e21`).

## What the craft is, read from the sources (not guessed)

* `FontRenderer.drawString(text, x, y, colour, dropShadow=true)` - SRG `func_175065_a` - runs the whole string
  **twice** (`FontRenderer.java:325-342`): `renderString(text, x+1, y+1, colour, true)` then
  `renderString(text, x, y, colour, false)`. Both passes draw the *same* glyph geometry; only the colour differs.
* The colour comes from the same `colorCode[]` table with `if (shadow) i1 += 16` (`renderStringAtPos:399-406`), so
  the shadow of §e (0xFFFF55) is `colorCode[30]` = 0x3F3F15 = (63,63,21), and the shadow of §7 (0xAAAAAA) is
  `colorCode[23]` = 0x2A2A2A = (42,42,42). The tooltip is drawn by
  `GuiScreen.drawHoveringText` → Forge `GuiUtils.drawHoveringText`, which brackets the text with
  `disableStandardItemLighting / glDisable(GL_LIGHTING) / glDisable(GL_DEPTH_TEST)` and the matching re-enables.
* The item is an Enchanted Book, so the lines are "§eEnchanted Book" (rarity `EnumRarity.UNCOMMON` =
  `TextFormatting.YELLOW`, verified in `EnumRarity.java`) over "§7Blast Protection IV".

## What the crop shows, measured

The crop is a screenshot at **GUI scale 3** (every sheet texel is exactly 3x3 px). Rebuilding both lines from
`ascii.png` the way `FontRenderer.readFontTexture` computes `charWidth[]`, and comparing per pixel:

| line | stroke pixels expected | drawn at the main colour | drawn at the shadow colour | foreign pixels |
|---|---|---|---|---|
| title "§eEnchanted Book" | 321 | **321** | as expected, 0 wrong | **0** |
| enchant "§7Blast Protection IV" | 196 ink cells | **172** | shadow complete | **0** |

* **The title line is pixel-exact vanilla**: every stroke is at the main colour, every shadow pixel is at the
  shadow colour at (+1,+1), no pixel is displaced and no pixel has a foreign colour.
* **The colour contract is right in both lines**: the main colours are exactly (255,255,85) and (170,170,170),
  the shadows exactly (63,63,21) and (42,42,42). There is **no leak of the shadow's colour into the main pass** -
  the candidate mechanism the plan recorded at §0.342 is refuted by this crop.
* **The one real difference**: 24 of the enchant line's 196 ink pixels (12 %) are **absent from the main pass**.
  Every one of them has its shadow-pass pixel present at (+1,+1) - the shadow pass drew the full glyph - and the
  main pass did not draw those pixels at all (the tooltip's own background shows through). All 24 are **ink
  pixels adjacent to a transparent texel** (the top row of a bar, the bottom row of a bowl, the left wall of a
  stem); the pattern is per glyph quad, not per glyph: 'o' loses 2 pixels at index 14 and none at index 8, 't'
  loses 2 at index 9 and none at 4 or 12.

## The controls: the current tree does not produce this

New instrument, landed with this record: `scripts/cpu-contract/font-shadow-main-check.py`. The invariant it uses
needs no string, no atlas and no reference picture - **every pixel whose colour is exactly the shadow's must have
a main-colour pixel one GUI pixel up and to the left** (`shadow(q) => ink(q - (1,1)) => main(q - (1,1))`).
`font-glyph-cell-check.py` (glyph-fix) cannot see this half: the F3 line is drawn **without** a drop shadow, which
is exactly what that record states ("the chat's texel check is not established (shadow + scale)").

| frame | text | scale | shadow px | shadow without main |
|---|---|---|---|---|
| user's crop, title line | §e yellow, shadowed | 3 | 1305 | **0 (0.0 %)** |
| user's crop, enchant line | §7 grey, shadowed | 3 | 1467 | **207 (14.1 %)** |
| `ww27-cand` tooltip region (our renderer) | white, shadowed | 3 | 694 | 1 (0.1 %, one item-icon pixel) |
| `ww28-ref` tooltip region (upstream GL) | white, shadowed | 3 | 693 | 0 (0.0 %) |
| `ww38` chat crop (includes the world behind the bar) | white, shadowed | 3 | 18302 | 45 (0.2 %) |
| Intel inventory tooltip | white, shadowed | 3 | 1386 | **0 (0.0 %)** |
| `mod3-jei` tooltip | white, shadowed | 3 | 693 | **0 (0.0 %)** |

**The same instrument on our own renderer's shadowed text finds no missing main strokes** (0, 0, 0 and one
item-icon false positive), and the upstream GL reference finds none either. The user's crop is therefore not a
picture the current tree draws.

Two more facts from the user's own logs (their instance jar md5 `2747418bc071642e2ed2fd0352b15bd2`, which
*contains* the strip separators, the coalesced run and the light-map rule):

* `CELTIUM_FONT_GLYPH submitted=200000 accepted=200000 refused=0 ... reasons={}` - **no glyph quad is refused**,
  so no whole glyph is missing from their session either; the 13.7 % refusal in the round-11 session
  (`refused=138929`) is gone.
* `CELTIUM_SAMPLER_FILTER glTexture=8 ... mag=NEAREST min=NEAREST mipmapMode=LINEAR maxLod=0` - the font sheet is
  sampled with **NEAREST at level 0**, so a NEAREST sample of an opaque ink texel is opaque and the sampler cannot
  turn a stroke into background.
* The first glyph of their session is `rgba=63,63,0` = exactly `(0xFFFF00 & 0xFCFCFC) >> 2`, the shadow of the main
  menu splash text's colour (`GuiMainMenu.drawScreen`: `16776960` = 0xFFFF00) - **the vertex-colour contract is
  right in their own session too**.

## Verdict and what remains

* **Reproduced**: the crop is a correct-colour tooltip whose *main* pass lost 24 stroke pixels in the second line.
* **Attributed**: not the vertex colour (measured exact), not the alpha (the main text is exactly the base colour,
  so alpha 1.0), not the light-map unit (the font is the `POSITION_TEX_COLOR` layout, which has no light-map term
  at all; the fixed rule and the model path never touch it), not the blend state (a blend cannot remove a stroke
  from a `v_Color`-carried opaque fragment), and not the sampler (NEAREST/level 0 in their own session).
  The shape that is left is a **per-quad coverage or per-pass state difference between the two `renderString`
  calls**: the shadow pass drew the pixel, the main pass did not, in the same group flush, with the same texture,
  the same UVs and the same alpha reference.
* **Not landed**: a fix. The evidence does not name a contract in the current tree - the current tree and the
  upstream reference both draw shadowed text with zero missing strokes - and the project's own rule is that a
  guess is not a fix. Landing one here would be an unexplained change to the text path.
* **The next measurement, named**: take one presented frame from the user's jar (or a fresh crop) and run
  `font-shadow-main-check.py` on it; if it shows violations again, dump the two passes' groups with
  `CELTIUM_GROUP_BOUNDS` (`groupBoundsFormat=2`) and compare `texMat`, `state`, `colour0` and `vertexColour0`
  between the shadow-run group and the main-run group of the same line - the instrument already prints every one
  of those fields, and that is the only place a per-pass difference can still be hiding.
