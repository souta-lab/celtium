# The undefined level is not content — capture provenance in the GL stub

Follow-up to `2026-10-11-texture-mirror-acceptance.md` (task-4). Owner: **texture-mirror**.
Jars quoted by sha256; pictures under `evidence/texture-mirror-20261011/`.

## The defect

`glTexImage2D` with a NULL data pointer is a legal call that reserves a level and leaves its
content undefined, and Minecraft allocates every level that way before filling it in patches
(`TextureUtil.allocateTextureImpl` → `glTexImage2D(..., (IntBuffer) null)`; `TextureMap:225` for
the atlas, `DynamicTexture` for the light map). The stub has to keep a surface for those levels,
because the patches that follow land in it — `CeltiumGlStub.undefinedLevel` zero-fills one and
files it as a capture.

The surface alone cannot say whether any of it is the game's, and `VulkanTextureRegistry` treated
it as content: `fillFromCapture` copied the zeros into a mirror, marked the mirror whole, and the
sampling side then drew zeros as if they were pixels. Measured in `tm2-on`/`tm2-off`:

- `CELTIUM_MIRROR_SOURCE id=3 source=capture size=1280x720 content=opaque=0/921600 full=true`
- the same for `id=42` — the game's own framebuffer colour textures
- `id=15` (light map) `source=capture size=16x16 content=opaque=0/256 full=true` before the
  announcement replaced it, and `id=116` 1x1 the same way
- capture census `undefined=22` at the first kept invalidation, `34` at the hundredth

## The fix

**Stub (one method, plus the fact it reports).** `TexCapture` carries `volatile boolean
gamePixels`: set by `texImagePixels` when the upload carried data, set by `texSubImage` when a
patch is copied into an existing surface (after the copy, so a reader that sees the flag sees the
rows), and clear for `undefinedLevel`'s zero surface. `CeltiumGlStub.textureLevelHasGamePixels(
textureName, level)` is the query. A level with any real upload answers true, because that surface
is what GL holds — the patches where they were written, undefined (read as zeros) elsewhere.

**Registry.** `VulkanTextureRegistry.fillFromCapture` refuses a surface with no game pixels,
counts it in `capturesRefusedWithoutGamePixels` (reported as `noGamePixels=` in the `glLess[…]`
split of `patchCostSummary`, which the GL-less line prints), and names the reason once through
`CELTIUM_PATCH_REJECTED`.

**Why the refusal is caller-aware.** The atlas and the light map are filled through hooks that run
at the *head* of the upload call, one instruction before the write: at that moment the level
legitimately has no game pixels yet, and the zero surface is exactly what the announced patch is
about to land on. `fillFromCapture` therefore takes a `gameWritesThisLevel` flag — true from the
announce path, false from every lookup. Measured consequence of getting this wrong: the
unconditional version (`tm3-on`, jar `f21bc38693c8dadb`) refused four surfaces instead of two
(`noGamePixels=4`, including the light map's and the 1x1's announce-path fills).

**Direct supplies.** `VulkanGlTexture.readCapturedLevel` turns a no-game-pixels level into the
existing `captureMiss`, so `uploadFromGlTexture` cannot copy allocation zeros into a mirror either;
the callers already count and record that (`CELTIUM_CAPTURE_MISS`).

## Measurements (jar `a106e84ac2f26a84`, GL-less, same fixture/scene/pose/tick)

| | before (`tm2-on`, `77f110f92a4907cd`) | after (`tm4-on`, `a106e84ac2f26a84`) |
|---|---|---|
| `id=3` / `id=42` mirror | `source=capture … opaque=0/921600 full=true` | `source=none content=not-carried full=false` |
| refusals | none | `CELTIUM_PATCH_REJECTED` names both 1280x720 surfaces; `CELTIUM_CAPTURE_MISS glTexture=42/3 reason=the level was allocated with no data and no upload ever wrote pixels into it` |
| counter | `glLess[fromAnnouncement=215 fromCapture=7 noContent=0]` | `glLess[fromAnnouncement=226 fromCapture=6 noContent=2 noGamePixels=2]` (kept=100) |
| light map | capture `opaque=0/256 full=true` then `announced-int[] 256/256` | unchanged: the announce path still takes the zero base the announced write lands on |
| atlas | capture `opaque=139685/262144 full=true`, `patches=89` | `opaque=139685/262144 full=true`, `patches=90`, `beforeInit=0`, `partialRefusals=0` |
| panel | present | present; the panel face band `(380,470,920,540)` is **bit-identical** between the two runs (mean 0.000) and the panel edges are identical to the upstream GL reference at `x 357..923, y 165..554` |

The unconditional control (`tm3-on`) is kept because it is the counter-experiment for the
caller-aware rule; it fixed the same two frame-sized mirrors but also refused the announce-path
fills.

## Which classes would have been affected had their pixels not arrived another way

- **The game's framebuffer colour textures (ids 3/42, 1280x720).** Nothing announces them; the
  renderer publishes its own live off-target images under other names. Any batch that sampled
  those GL names got a transparent mirror that claimed whole content. This is the one class the
  fix changes today, and it changes it from "drawn transparent and claimed full" to "content-less
  and reported". Who samples those two names under GL-less is still open — the lazy supply fails
  on them ~70 times per 100 kept invalidations (`lazySupply=0/70`), which is the same failure the
  pre-fix code had (it copied zeros and could not mark them full, so it retried every batch).
- **The light map (id 15) and 1x1 dynamic textures (id 116).** They relied on the announce-path
  allowance and are unaffected; had their announcements never come, the draw-time read would now
  be refused instead of serving zeros.
- **The block atlas (id 12) and the font sheet (id 9).** Have real uploads, so the new query
  answers true; nothing changed. The atlas's stitch is exactly the case the caller-aware rule
  protects.
- The remaining `undefined` levels (census 33-34) never reached a mirror at all — no lookup and no
  announcement — so they were already harmless.

## Verified and not verified

Verified: the stub's per-level fact against three upload shapes (full image, patch into an
undefined surface, undefined allocation with no upload); the registry refusal and its counter; the
direct-supply refusal; the atlas, light map, font sheet and panel mirrors unchanged; the panel
face bit-identical across the fix.

Not verified: who samples the two framebuffer colour names (owned by the frame/composite and GUI
stage tasks); a texture whose *only* source is a patch into an undefined level and whose
announcement never arrives (the atlas's shape without the announcement — the capture is then
refused and the mirror stays content-less, which is honest but untested by this fixture);
compressed uploads (still `unconverted`, unchanged).
