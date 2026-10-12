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

The two strongest controls are from **after** the user's jar (their instance's jar is 10:32, the report 10:38), so
the comparison is not against an old tree:

| frame (run) | time | shadow px | shadow without main |
|---|---|---|---|
| `fast-f3off` `celtium-presented-world-run-193464c672f54-kept.png` (startup chat, whole frame) | 10-12 10:49 | **35208** | **0 (0.0 %)** |
| `bs44-texenv` `celtium-presented-world-run-1949a4e9a58f6-kept.png` (chat + the F3 chart) | 10-12 11:13 | 28672 | 238 (0.8 %), **all of them in x 276..821 / y 343..533** |

The 238 in the newest frame are not text: that rectangle is the F3 overlay's frame-time chart, whose bars are
`(0,255,0)`/`(255,255,0)`/`(0,0,255)` over dark panel pixels - the chart's own `(63,63,63)` pixels have no glyph
above-left of them. Every one of the 35,208 chat glyph-shadow pixels in the 10:49 frame has its main pixel.

**A colour-matched control exists, and it is a tooltip.** The damaged line's colours are §7, and exactly one frame in
the workspace has §7-shadowed text: `runtime/r135-e/screenshots/celtium-presented-gui-run-190294aef3f02-66d3c617.png`
(2026-10-12 09:51), the Celeritas video-options screen, whose description panel is a tooltip with
"§7Performance Impact:" and "§7Added by mod: minecr…" beside the white body text. Measured with the same instrument
and the same pair the user's line uses:

| frame | text | shadow px = `(42,42,42)` | shadow without `(170,170,170)` at (-3,-3) |
|---|---|---|---|
| user's crop, enchant line | §7, in a tooltip | 1467 | **207 (14.1 %)** |
| `r135-e` video-options tooltip | §7, in a tooltip | **2718** | **0 (0.0 %)** |

**The one-flag font-path pair closes the glyph path as the cause.** The user's jar (md5 `2747418b…`) predates the
Celtium-side default-glyph path (OPT-18), so "the old capture path drops strokes and the new fast path does not" was
the natural explanation - and the tree carries the control for it (`-Dceltium.font.fastPath=false`, the same jar).
Measured on the same jar, the same fixture and the same flags (`md5 c068dd6c6279fbb8f88f56194263a576`):

| leg | font counters | shadow px | shadow without main |
|---|---|---|---|
| `fast-f3off` (10:49, fast path **on**, default) | `windows=60000 fastGlyphs=60000 fontTextureRebinds=157` | 35208 | **0** |
| `gt-fastpath-off` (11:21, fast path **off**: `-Dceltium.font.fastPath=false`) | `windows=60000 defaultWindows=60000 fastGlyphs=0 hookMillis=8 glyphMillis=805` | **36018** | **0** |

Both leases of the flag render shadowed text with **zero** missing main strokes, and the counters prove the two legs
took different paths (`fastGlyphs=60000` against `fastGlyphs=0`). The old path is therefore not the mechanism, and
neither is the new one.

The two jars differ in exactly 19 class entries (the font capture and its mixin, `CeltiumGlStub` and its inner
classes, `VulkanTerrainGpuBackend`, `VulkanTextureRegistry`, `RenderGlobalMixin`); the font half of that difference
is now measured and excluded, which leaves the stub/backend/registry half as the only unmeasured difference between
the user's build and every control here - none of which touches the glyph colour, UV or alpha contract this unit is
about.

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
  upstream reference both draw shadowed text with zero missing strokes, the §7 pair itself is clean in a tooltip on a
  current-era frame, and the one-flag font-path pair shows the old capture path and the fast path are equally clean -
  and the project's own rule is that a guess is not a fix. Landing one here would be an unexplained change to the
  text path.
* **The next measurement, named**: take one presented frame from the user's jar (or a fresh crop) and run
  `font-shadow-main-check.py` on it; if it shows violations again, dump the two passes' groups with
  `CELTIUM_GROUP_BOUNDS` (`groupBoundsFormat=2`) and compare `texMat`, `state`, `colour0` and `vertexColour0`
  between the shadow-run group and the main-run group of the same line - the instrument already prints every one
  of those fields, and that is the only place a per-pass difference can still be hiding.

## task-66 (2026-10-12): the second pass, and the state the coalesced run is submitted under

Owner: **gui-text**; write scope `FontBatch.java` + `scripts/cpu-contract/*`. The HEI acceptance frame is the
reproduction: the vanilla item tooltip `(660,315)-(820,355)` ("Gold Ore", GUI scale 3) keeps only 238 of its
828 main-pass ink pixels (`shadow_without_main` 585/797), against 0/693 on the GL reference, on both candidate
builds (`543745fbe076f15d`, `7bd467145b0d28e4`), pixel for pixel.

### What the originals say, and what the reconstruction proves

`FontRenderer.drawString` (SRG `func_175065_a`, `FontRenderer.java:325-342`) runs the string twice - shadow at
`(x+1,y+1)` in the shadow colour, main at `(x,y)` in the main colour - and `renderDefaultChar`
(`:230-249`) gives each glyph one quad whose cell, width and texture coordinates come from `ascii.png` and the
width table `readFontTexture` builds from it. A CPU reconstruction of exactly that (new instrument
`scripts/cpu-contract/font-second-pass-check.py`) predicts the reference frame's ink **exactly**: 828 of 828,
no pixel of one set outside the other, per glyph 153/108/63/144/144/81/135 all complete. So the model is the
original's, and the 590 missing pixels on the candidate frames are the frame's, not the model's.

The measurement also fixes the *shape* of the loss, which rules out the hypotheses this round started from:

* **not** the atlas, the glyph cell, the pen advance, the UV range or the quad geometry: the surviving main ink
  is a **strict subset** of the reference's white pixels, at the same positions (candidate-only = 0), i.e. the
  right texels in the right places, with the fragment simply absent - the tooltip's own background shows
  through, and where the shadow pass inked, the shadow shows;
* **not** the capture's or the batcher's vertex arithmetic: a simulation of the real per-glyph stream (4
  vertices per glyph, the two-vertex degenerate separator between glyphs) reproduces the reference at IoU 1.000,
  and every single-vertex corruption of that stream (delete/duplicate/replace-with-neighbour over all 40
  vertices), every primitive reinterpretation (strip / triangle list / quad split / fan) and every shift of the
  ink scores below 0.34 against the candidate;
* **not** the first pass: reconstructing the shadow pass alone - its ink shifted by one GUI pixel, minus what
  the main pass covers - matches the candidate's shadow pixels at IoU 0.996 (797 predicted, 800 measured, 3
  stray). The shadow pass is complete; only the second pass loses ink;
* **not** a frame-wide or scene-wide capture artefact: in the candidate frame the *only* white ink missing is
  the tooltip's (a tile scan over 40x40 px tiles finds the loss in four tiles, all at `y=320`), and the frame
  has slightly *more* white ink than the reference overall. The extra `(63,63,63)` pixels the whole-frame
  instrument counts elsewhere are the world behind the GUI, not text shadows.

### The mechanism this round fixes: the run key does not carry the draw state

The two passes are two separate runs: the colour witness closes the shadow run inside `drawString`, and the
main run waits for the next flush point. The owner's batch path reads the draw state **when it builds the
group** - `ImmediateGroup` captures the decoded blend/depth/cull state (`state`, `stateKey`), the colour mask,
the texture matrix and the scissor box (`scissorBox`, `scissorEnabled`) at accept time - so a run appended
under one draw state and submitted under another is drawn with the state of the *submission*, not of its first
quad. `FontBatch.celtium$fontRunMatches` covered colour, texture, alpha test, target and the matrices (the last
one because the same hole was measured as garbled menu buttons, a rotated splash and block-like copyright
text); it did **not** cover blend, depth test/write, cull, colour mask or scissor.

The tooltip is the case where that matters: Forge draws it inside `GlStateManager.disableDepth()` ...
`enableDepth()`, so the shadow pass is submitted inside that bracket and the main pass - which stays in the
run - is submitted after it. That is the one asymmetry the two passes have, and it is on the code path the
measurement above proves is the only place the difference can be (the geometry, the UVs and the capture's
vertex stream are the original's).

**Fix** (in this unit's scope, `FontBatch.java`): the run key now carries the draw state through six new
`FontRunState` accessors - `depthTestEnabled`, `depthWriteEnabled`, `blendEnabled`, `cullEnabled`,
`colorMask`, `scissorKey` - all **default methods** ("no change"), so a state view that does not answer them
keeps today's behaviour exactly and the wiring is one commit per side. The runs this closes are counted in a
new `celtium$fontStateSummary()` (`fontDrawStateFlushes=…`), deliberately **not** added to
`celtium$fontBatchSummary()`: that string is quoted by past acceptances byte for byte, and the CPU contract
check pins its keys.

**Fix, backend half (Lead's file, anchor+replacement sent separately):** the anonymous
`FontBatch.FontRunState` at `VulkanTerrainGpuBackend.java:1161` overrides the six accessors from
`immediateState` (`depthTestEnabled()`, `depthMask()`, `blendEnabled()`, `cullEnabled()`, `colorMask()`) and
from `CeltiumGlStub` (`scissorTest()`/`scissorCalled()`/`scissorX()`/`scissorY()`/`scissorWidth()`/
`scissorHeight()` packed into `scissorKey()`). Until that lands the witness is inert (the defaults answer), so
this commit cannot change any picture on its own.

### The CPU check that fails before and passes after

`scripts/cpu-contract/FontBatchContractCheck.java` (extended, 38 checks): a dynamic proxy answers the state
view, a quad is appended, exactly one draw-state field changes, a second quad is appended, and the check reads
how many runs the sink received - one means the change closed the run, zero means the two quads would be drawn
under the state of the second.

| build | result |
|---|---|
| pre-fix (`HEAD`, `FontBatch.java` before this commit) | **7 failed**, exit 1: the interface declares no witness, and no run is closed on a depth/depth-write/blend/cull/colour-mask/scissor change |
| post-fix | **38 checks, 0 failed**, exit 0; the pinned summary keys are unchanged and the new counter stays out of them |

and the picture instrument, on the frames that exist:

| frame | predicted ink | drawn | coverage | exit |
|---|---|---|---|---|
| `hei-ref-list` GL reference | 828 | 828 | 1.000 | 0 |
| `hei-cand` (`543745fbe076f15d`) | 828 | 238 | 0.287 | 1 |
| `hei-cand2` (`7bd467145b0d28e4`) | 828 | 238 | 0.287 | 1 |

The picture *after* the fix needs a leg (`-Dceltium.font.batching=false` is the one-flag control: with
batching off every glyph is submitted where it is drawn, so the two passes cannot be separated by a
submission; if the tooltip then keeps its ink, the mechanism above is confirmed and the witness is what closes
it). This round did not take that leg: the Lead owns the GPU.
