# OPT-18 first half: the default glyph body moves into Celtium, measured on one jar

Owner: **glyph-fix**. Jar `9a0eb18a3072b586c617726574391f0e`. The user asked for the font renderer overhaul; the split
instrument (`renderStringMillis` 4,663 ms, `glyphMillis` 4,657 ms, caller 6 ms, vanilla glyph body 4,037 ms at 3.15
µs/glyph) is the spec, and this lands the half it pointed at: the per-glyph body.

## What was built

`FontRendererMixin` no longer only brackets the glyph methods: on `func_78266_a` (the default page) it may **draw the
glyph itself** and cancel vanilla's body (`@Inject(HEAD, cancellable)`, `require = 0`).
`CeltiumFontGlyphCapture.fastDefaultGlyph` writes the four vertices and their texture coordinates **exactly as
`renderDefaultChar` computes them** (`FontRenderer.java:230-249`: `i = ch % 16 * 8`, `j = ch / 16 * 8`,
`k = italic ? 1 : 0`, `l = charWidth[ch]`, `f = l - 0.01F`, the four positions
`(posX+k, posY)`, `(posX-k, posY+7.99)`, `(posX+f-1+k, posY)`, `(posX+f-1-k, posY+7.99)` and the four UVs
`(i/128, j/128)`, `(i/128, (j+7.99)/128)`, `((i+f-1)/128, j/128)`, `((i+f-1)/128, (j+7.99)/128)`), and hands them to the
**capture's existing `submit()`** - format 2, stride 24, mode 5, the coalesced run, the strip separators, the same
refusals and the same counters. The colour is the capture's mirrored `GlStateManager.color` value, i.e. the same source
the window path used. What disappears is `TextureManager.bindTexture` per glyph and the nine `GlStateManager` wrappers.

Contracts kept: **unicode is untouched** (only `func_78266_a` has a fast path; `func_78277_a` still runs vanilla and
binds its page per glyph), so the mixed ASCII/unicode case is still correct - and the fast path re-binds the sheet when
the unit no longer holds it (93 re-binds over 1.28M glyphs, `fontTextureRebinds`), which is what a unicode page between
two ASCII glyphs, a mod's texture or the light-map unit dance would need. `-Dceltium.font.fastPath=false` is the
one-variable control.

## Measured: same jar, F3 on, `pinSections=64`, both legs `PASSED` with `validationClean=true`, `validationMessageCount=0`

| | fast path ON | fast path OFF (`-Dceltium.font.fastPath=false`) |
|---|---|---|
| glyphs / strings | 1,280,000 / 2,886 | 980,000 / 2,330 |
| `glyphMillis` (vanilla body) | **0** - it no longer runs | 14,280 ms (**14.6 µs/glyph**) |
| `renderStringMillis` (all text) | **1,093 ms** | 14,076 ms |
| per string | **0.38 ms** | 6.04 ms |
| per glyph | **0.85 µs** | 14.4 µs |
| cold p50 | **144.7 ms** (23 samples, 80 frames) | **550.6 ms** (8 samples, 43 frames) |
| `fastGlyphs` / `fontTextureRebinds` | 1,280,000 / 93 | 0 / 0 |
| `refused` | **0** | **0** |

**Text path: 14.4 → 0.85 µs/glyph (−94 %); cold p50 550.6 → 144.7 ms (−74 %)** in the same session on the same jar.
The earlier session measured the same vanilla body at 3.64 µs/glyph, so the *absolute* before number is machine-state
dependent; the per-glyph ratio and the same-jar control are not. The F3-off leg with the fast path on (60,000 glyphs)
gives cold p50 72.7 ms, i.e. the overlay costs 72 ms/frame in this session (128-139 ms in the earlier ones).

## The text is unchanged, measured on the glyphs themselves

`font-glyph-cell-check.py` on the fast-path frame: line 1 **449/449** strokes drawn exactly, line 4 **104/104**, **0**
of 896 empty texels drawn - the same numbers as the vanilla path, per texel against `ascii.png`.
Crops at 1x: `fastpath-f3-1x.png` (sha256 `35c5c6d2c0e17e45…`) and `fastpath-chat-1x.png` (`afbadd03aeae0cbc…`);
frame sha256 `05370807e760358d…`.

**Not established**: a chat-level texel check. The chat is drawn with a shadow and a scale, so the check that proves the
F3 line per texel does not apply to it as written, and a cross-leg crop comparison is background, not text (the chat
region's `>8` fraction between two runs is 11.9 % while the same text is drawn). The chat's own evidence is the crop
plus the fact that it takes the same `func_78266_a` path. Also not established: the unicode fast path (not built - it
would need the page-change bind), the layout/geometry cache, sign and map items of OPT-18, and the fast path's own
per-glyph cost split (the 0.85 µs is the whole text cost per glyph, which includes the fast path's array writes and the
backend submit; a timer inside it is the next instrument if the number needs to go lower).
