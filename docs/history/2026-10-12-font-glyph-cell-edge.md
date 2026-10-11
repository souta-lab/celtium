# Font glyphs: the UVs do touch the cell edge — and the sampler cannot care

Owner: **glyph-fix**. Unit 1 of the hand-off: the user's report of "subtle artifacts between characters" in the
F3 overlay, and the hypothesis that a glyph quad whose UVs touch the 128x128 sheet's cell boundary samples the
neighbouring glyph's texel column under LINEAR magnification.

Evidence leg: `.celtium-work/runtime/font-f3` (F3 overlay leg, `-Dceltium.validation.showDebugInfo=true`),
mod jar `celtium-forge-mc1.12.2-2.5.12-celtium.0.1.0-dev.1.jar` md5 `e2d0325cda78007a0cb0771620898a93`,
AMD Radeon RX 6400 (RADV NAVI24), 1280x720, GUI scale 3, `fixtureStatus=PASSED`,
`validationClean=true`, `validationMessageCount=0`. Presented frame
`screenshots/celtium-presented-world-run-1727828e99559-66d3c617-kept.png`
sha256 `1740c1ab67ac84b890f63398807968957e48d3d180401781e2306f9965798d4d`.
Reproduce every number here with `docs/history/evidence/font-f3-20261012/font-glyph-cell-check.py`.

## 1. The UVs, from the original (not from an argument)

`FontRenderer.renderDefaultChar` (`scripts/mc-sources.sh` → `.celtium-work/mc-sources/net/minecraft/client/gui/FontRenderer.java:230-249`):

```java
int i = ch % 16 * 8;  int j = ch / 16 * 8;  float f = (float)charWidth[ch] - 0.01F;
GlStateManager.glTexCoord2f((float)i / 128.0F, (float)j / 128.0F);                     // left,  top
GlStateManager.glTexCoord2f((float)i / 128.0F, ((float)j + 7.99F) / 128.0F);           // left,  bottom
GlStateManager.glTexCoord2f(((float)i + f - 1.0F) / 128.0F, (float)j / 128.0F);        // right, top
GlStateManager.glTexCoord2f(((float)i + f - 1.0F) / 128.0F, ((float)j + 7.99F) / 128.0F);
```

The sheet is 16x16 cells of 8x8 texels, cell pitch `8/128 = 0.0625 = 1/16`. With `charWidth[]` computed from the
real `ascii.png` the way `readFontTexture` computes it (rightmost non-transparent column + 1, plus one):

| char | cell (col,row) | charWidth | u0 | u1 | v0 | v1 | inset from the **left/top** edge | inset from the **right** edge | inset from the **bottom** edge |
|---|---|---|---|---|---|---|---|---|---|---|
| `E` | 5,4 | 6 | 0.312500 | 0.351484375 | 0.25 | 0.312421875 | **0 texels** | 3.01 texels | 0.01 texel |
| `M` | 13,4 | 6 | 0.812500 | 0.851484375 | 0.25 | 0.312421875 | **0 texels** | 3.01 texels | 0.01 texel |
| `0` | 0,3 | 6 | 0.000000 | 0.038984375 | 0.1875 | 0.249921875 | **0 texels** | 3.01 texels | 0.01 texel |
| `:` | 10,3 | 2 | 0.625000 | 0.632734375 | 0.1875 | 0.249921875 | **0 texels** | 7.01 texels | 0.01 texel |
| `/` | 15,2 | 6 | 0.937500 | 0.976484375 | 0.125 | 0.187421875 | **0 texels** | 3.01 texels | 0.01 texel |

So the hypothesis' premise about the geometry is **true**: the left and top edges sit exactly on the cell boundary
(0 texels of inset), the right edge is 1.01 texels inside the cell's right boundary and the bottom edge 0.01 texel
inside. The glyph capture does not move them: `FontRendererMixin` only opens/closes the window and
`CeltiumFontGlyphCapture.immediateTexCoord` (`:280`) stores `s,t` verbatim.

The leg's own instrument confirms the formula end to end:
`CELTIUM_FONT_GLYPH first: quad=(180.0,101.0)-(184.99,101.0) uv=(0.75,0.25)-(0.78898436,0.25) rgba=63,63,63,255
texture=9 windows=1 …` — cell (12,4) = `L`, `Δu * 128 = 4.99 = charWidth(6) - 1.01` exactly.

## 2. …and the sampler cannot bleed at that edge

```
CELTIUM_SAMPLER_FILTER glTexture=8 repeatU=true repeatV=true mag=NEAREST min=NEAREST mipmapMode=LINEAR maxLod=0
  (maxLod was 1, now from the record's GL_TEXTURE_MAX_LEVEL 0) (mag=record min=record; …)
```

`glTexture=8` is the font sheet (it is the texture that records `GL_TEXTURE_MAX_LEVEL 0`, i.e. the
`allocateTexture(id,w,h)` of `SimpleTexture`). Magnification **and** minification are NEAREST and only level 0 is
sampled, so there is no LINEAR stage anywhere between the sheet and the frame — the mechanism the hypothesis names
cannot fire in this configuration. A half-texel UV inset would therefore be a **no-op** under NEAREST, and under
LINEAR it would *shift* the glyph by half a texel instead of removing a bleed NEAREST does not produce.

## 3. The picture says the same thing: the glyphs are texel-exact

At GUI scale 3 one sheet texel is exactly 3x3 screen pixels, and the F3 left column's strings are known from
`GuiOverlayDebug.call()`/`getDebugInfoRenders()`/`getDebugInfoEntities()`
(`drawString(s, 2, 2 + 9*i, 14737632)`, **no** drop shadow). Checking every texel the sheet says is a stroke, and
every texel it says is empty:

```
line 1 "Minecraft 1.12.2 (1.12.2/fml,forge,cleanroom)"  strokes= 449 drawn exactly= 449 missing=0 | empty=769 drawn as a stroke=0
line 4 "E: 0/1, B: 0"                                  strokes= 104 drawn exactly= 104 missing=0 | empty=127 drawn as a stroke=0
```

(`font-f3-line1-sheet-vs-frame.png`: top = rebuilt from `ascii.png`, middle = the presented frame, bottom = the
per-texel verdict — all green, no red, no blue.) The glyph quads sample their own cell and nothing else, everywhere
in the line, at the very columns the hypothesis is about.

**Verdict on the assigned hypothesis: dead.** No inset, and no change to the advance or the quad geometry, is
justified by this evidence.

## 4. What the artifact actually is (and what it is not)

It exists, and it is not the glyph path. Two frames of the same run (the early one and the kept world frame) show, at
the same screen columns, 1-px vertical bars inside the F3 overlay's translucent background rectangle
(`drawRect(1, i1-1, 2+k+1, i1+j-1, -1873784752)`, `GuiOverlayDebug.java:76`). Measured on the kept frame:

* the bars are **±6..12 luminance** off the local background, and a few columns are **+55** (148 against a
  background of 93) — `font-f3-line4-background-residual.png` (8x amplified residual of line 4);
* they live only in the **glyph quads' rows** (87..110 = 8 texels x 3 px), *not* in the three rect-only rows above
  the text (84..86 are smooth: 92,92,92,92…) and not below the rect (111+ are smooth) — the rect's own band is
  27 px tall, so the bars follow the *text*, not the rect;
* they sit in the **gaps between glyph quads** (x=21..23 between `E` and `:`, x=27..41 in the space's four-unit
  advance, …), i.e. in columns no glyph quad covers;
* they are **not a shifted copy of the text**: cross-correlating the brighter-than-background non-stroke pixels of
  line 4 against the expected stroke map over shifts (±6, ±6) px peaks at 0.246 of the pixels, *below* the 0.269
  that a random placement would give;
* they are **not the sky behind the rect**: outside the rect the same rows are smooth (131,131,131,132… at
  x=175..239), and the only F3-off leg that kept a presented frame (`ww39-f3-off-batch`, different leg, similar
  corner colour) has **0 of 165** such columns where this leg has **105 of 165**. The F3-off leg is a weak control
  (different tick and camera), so this is a lead, not a closed attribution.

The one mechanism in this path that can put *text-shaped geometry from another time* into those gaps is the
coalesced font run of task-59: `celtium$fontAppend` appends quads into the reused `pendingFontVertices` buffer and
`celtium$flushPendingFontBatch` draws `pendingFontVertexCount` of them in one call
(`VulkanTerrainGpuBackend.java:3776-3956`). The bars are consistent with extra vertices being drawn from that
buffer (stale quads land at the previous frame's glyph positions, which is why their pattern is nearly static while
the current text moves), but this is a hypothesis, not a measurement.

**Next measurement, one flag on the same leg and the same jar**: `-Dceltium.font.batching=false` turns
`celtium$fontAppend` into a refusal for every glyph (`VulkanTerrainGpuBackend.java:1342`, `:3840`), so every glyph
goes through `submitImmediateBatch` on its own. If the bars survive that, the coalescing is not the source and the
next suspect is the rect's own submit; if they vanish, the run's buffer is.

## What this unit did **not** establish

* The cause of the bars. Only their geometry, their sign and magnitude, and that they are not the glyph quads, not
  a shadow-shaped copy of the text and not (weak control) the sky behind the rect.
* Whether the bars are visible to a user at a normal viewing distance or only in an 8x-amplified residual. No crop
  that a user would point at has been produced yet.
* Anything about the *unicode* glyph path: this leg is `unicodeWindows=0` of `windows=687406`, so
  `renderUnicodeChar`'s UVs (cell 16x16 texels, left edge inset by the glyph's high nibble, right edge 0.02 texel
  inside the cell — `FontRenderer.java:288-300`) were **not** measured in a frame.
* The GL reference for the same text: the F3-off control is a different leg, not a two-configuration pair on one
  fixture/pose/tick. That pair is what would promote §4 from "lead" to "attributed".
