# N03 — the vanilla glyph path: the committed code, and the pictures

Worker record for the audit's N03 (P1: "vanilla text still reaches the GL-less no-op immediate path").
Tree: `318a24d0a` (`font: the probe notes corrected, and the counter summary printed at exit`), on top of
`aa041c2d4` (`font: the glyph buffer was submitted from one past its last written byte`).
Jar used for every run below: `celtium-forge-mc1.12.2-2.5.12-celtium.0.1.0-dev.1.jar`,
sha256 `044f5aba990550f707b59c112121ecabbe4a842790d91bc1d62e511197d719b3` (the glyph path is byte-identical to
`aa041c2d4`; `318a24d0a` adds a comment correction and a shutdown-hook summary only).
Runner: `.celtium-work/fontmatrix.py` (builds the same isolated game directory `scripts/prism_validation.py`
builds, adds the `options.txt` the harness never copies, and replays the harness's own command through the
harness's `inspect()`). Evidence sheets: `.celtium-work/n03/`. Run directories: `.celtium-work/runtime/n03-*`.

## 1. What the committed code does per entry point

**`forge122/.../mixin/core/FontRendererMixin.java`** — a scope marker around the font's whole drawing surface:

| entry point | vanilla method | injection |
|---|---|---|
| `renderDefaultChar` (ascii page) | `func_78266_a` | HEAD `:29`, RETURN `:34` |
| `renderUnicodeChar` (per-page unicode sheets) | `func_78277_a` | HEAD `:39`, RETURN `:44` |

Both SRG names were checked in both directions with `scripts/mc-sources.sh --srg`, and the bodies were read in
`.celtium-work/mc-sources/net/minecraft/client/gui/FontRenderer.java` (`renderDefaultChar` 230–248,
`renderUnicodeChar` 272–303). Neither injection writes the return value — the file contains no
`setReturnValue` — so `charWidth[ch]` (`:247`) and `(f1-f)/2.0F + 1.0F` (`:301`) are the values the game
returns, unchanged. `require = 0` everywhere: a build without a name degrades to "not captured".

**`.../mixin/core/GlStateManagerImmediateMixin.java`** — exactly the four calls a glyph emits, and nothing
else (no generic GL translator): `glBegin` = `func_187447_r` `:30`, `glTexCoord2f` = `func_187426_b` `:35`,
`glVertex3f` = `func_187435_e` `:40`, `glEnd` = `func_187437_J` `:45`.

**`.../impl/font/CeltiumFontGlyphCapture.java`** — the batch:

* **window** `openWindow(boolean unicode)` `:214`, `closeWindow()` `:246`. Armed only when
  `CeltiumGlStub.isInstalled() && TerrainGpuBackends.current() != null`; in the bridge it counts the window
  (`bridgeWindows`) and submits nothing, so no glyph is drawn twice.
* **glyph** `immediateBegin` `:260`, **texture coordinates** `immediateTexCoord` `:274`, **vertex**
  `immediateVertex` `:288`, **end** `immediateEnd` `:308`; the sequence is refused unless it is a 4-vertex
  `GL_TRIANGLE_STRIP`/`GL_QUADS` with four finite vertices and four UVs (`:308-337`).
* **texture** is not passed: the backend binds the mirror of the currently bound texture (the `bindTexture`
  hook, `GlStateManagerMixin.java:203`), which is what `renderDefaultChar`'s `bindTexture(locationFontTexture)`
  and `renderUnicodeChar`'s `loadGlyphTexture(page)` set immediately before `glBegin`. Measured at the glyph:
  `boundTexture=9` (the font page).
* **colour and alpha** come from `currentColor` `:200`, fed by the existing `GlStateManager.color` hook
  (`GlStateManagerMixin.java:209-215`) — the same call the backend's own colour mirror is fed from — and are
  baked into the quad's four `COLOR_4UB` bytes.
* **shadow** needs no path of its own: vanilla draws the shadow as the same two glyph methods with a dark
  colour, so it travels the same capture and the colour mirror carries it.
* **advance**: untouched (above).
* **submit** `:340-450`: one `POSITION_TEX_COLOR` (format 2, stride 24) batch of four vertices, the strip
  vanilla emitted (`mode 5`), through `backend.submitImmediateBatch(...)` `:441`. The address is taken after
  `flip()` and its extent checked (`:421-430`, `aa041c2d4`).
* **counters**: progress lines `:578`, `summary()` `:595`, and — new in `318a24d0a` — a shutdown hook `:604`
  that prints `CELTIUM_FONT_GLYPH_SUMMARY` once at exit, so the totals no longer have to be added up from
  capped progress lines.
* **diagnostics, all off by default**: `isolation=positionColor` `:96`, `mode=quads` `:106`,
  `winding=swapped` `:119`, `screenFill` `:128`, `matrixProbe` `:138`, `stateProbe` `:152`. The `screenFill`
  probe was broken (it mutated the vertex arrays *after* the buffer had been written) and is fixed `:373-388`.

## 2. Do the accepted quads reach the presented image?

**Before `aa041c2d4`: no.** Seven runs on 11:01–11:22 (`n03-a-magenta`, `-b-isolation`, `-d-fill`,
`-e-fillquads`, `-i-quadtext`, `-k-quadisotext`) each reported 34000–48000 quads accepted with
`refused=0` and `CELTIUM_REFUSALS_BY_FORMAT {}`, and the magenta probe
(`-Dceltium.font.debugRgb=0xFF00FF`) put **zero** magenta pixels into any presented frame, in the GUI frame
and the world frame alike. Cause (found and fixed by the parent, not by me): `MemoryUtil.memAddress(ByteBuffer)`
returns base **plus the buffer's position**, and the write loop left the position at the capacity — the backend
was handed a pointer one past the last written byte and read 96 bytes of whatever followed. Every probe I had
built on that address (draw mode, isolation, screen fill) shared the defect, so their negative results are
void and are not carried forward.

**After `aa041c2d4`: yes.** Same scene, same probe (`n03-n-fixed`, 11:26): the glyphs are in the presented
frame — the title, the tutorial toast and the chat block, all in magenta. Sheet
`.celtium-work/n03/magenta-probe.png`: top = the pre-fix GUI frame cropped `(450,0,830,60)` (no text), bottom
= the post-fix frame, same crop ("Video Settings" in magenta). Census: 838 magenta-hue pixels in the GUI frame,
15062 in the world frame of the same run.

Final totals on the current tree (`n03-z-final`, video settings scene, GL-less):

```
CELTIUM_FONT_GLYPH_SUMMARY windows=53140 defaultWindows=53140 unicodeWindows=0 bridgeWindows=0
  quads=52406 submitted=52406 accepted=52406 refused=0 submitMillis=1636 reasons={}
CELTIUM_REFUSALS_BY_FORMAT {}
```

Unicode leg (`n03-r-ja`, `lang:ja_jp` + `forceUnicodeFont:true`, the options.txt the harness never copies):
`unicodeWindows=40548, defaultWindows=0, submitted=40000 accepted=40000 refused=0` at the progress cap —
`func_78277_a` is exercised and accepted for the first time.

## 3. The text categories, each with its bridge/GL-less pair and crops

All crops are `x0,y0,x1,y1` in the 1280×720 frame. Sheets are in `.celtium-work/n03/`.

| category | GL-less leg | bridge leg | sheet | crops |
|---|---|---|---|---|
| ASCII + shadow | `n03-u-clean` presented GUI frame | `n03-v-cleanbridge` game screenshot | `ascii-shadow-bold.png` | title `520,0,760,45`; toast `880,10,1280,90`; Done `520,630,760,690`; list label `640,110,1110,175` |
| bold + italic (`§l`/`§o`) | `n03-p-ascii` | `n03-q-asciibridge` | `decorated-labels.png` | Done `§o` `520,630,760,690`; `§l§o` label `640,110,1110,175`; bold toast `880,10,1280,90` |
| unicode (Japanese) | `n03-r-ja` | `n03-s-jabridge` | `unicode-ja.png` | toast `760,20,1280,100`; Done `560,620,760,700`; title `500,0,780,60` |
| chat (shadowed) | `n03-w-terrain` (status PASSED, nonBackground 0.841211, black 0.004333) | `n03-x-terrainbridge` | `chat-terrain.png` | chat `0,320,660,620`; toast `760,10,1280,100` |
| inventory + JEI | `n03-t-jei` early presented dump (tick 40) | **none exists** | `jei-inventory.png` | JEI page `1030,20,1140,60`; tooltip `660,315,820,355` |

What the pairs show:

* **ASCII/shadow**: "Video Settings" `(520,0,760,45)`, the toast `(880,10,1280,90)`, "Done"
  `(520,630,760,690)` are at the same size and position in both legs. The list label
  `(640,110,1110,175)` is only partially comparable: in the GL-less leg the world's shard triangles cover the
  panel, so only the tail of "Render Distance: 8 chunks" ("ks") is visible; that is the frame-composition
  defect, not the font.
* **bold/italic**: the pack `.celtium-work/packs/n03-format` (installed into the isolated game directory
  only) puts `§l` on `options.video`, `§o` on `gui.done` and `§l§o` on `options.renderDistance`. "Done" is
  visibly slanted in both legs; "Render Distance: 8 chunks" is heavy and slanted in the bridge and tail-only
  in the GL-less leg (same occlusion); the tutorial toast's own bold words match in both.
* **unicode**: "W、A、S、D で移動します / SPACE でジャンプします" `(760,20,1280,100)` and "完了"
  `(560,620,760,700)` are pixel-comparable between the legs. The title `(500,0,780,60)` is not: in Japanese
  the toast is wider and its icon sits over the title's spot in the GL-less leg.
* **chat**: both legs carry the same `/celtium` messages at a 27–30 px line pitch with 24 px glyphs starting
  at x≈6. In the GL-less leg parts of the block are covered by the world shards (the last line, "Terrain still
  renders with OpenGL.", is complete; the lines above it are cut where the shards cross them). The matrix probe
  (`n03-y-chatprobe`) shows the glyph quads carrying the game's own coordinates and advances
  (e.g. `quad=(256.0,-53.0)-(260.99,-53.0)`, a 4.99-unit advance = the game's own `charWidth - 0.01`), and the
  state probe reads the chat's own model view `(2,200,-2000)` = `translate(2,8)` inside
  `GuiIngame`'s `translate(0, 192, 0)` (`GuiNewChat.java:54`, `GuiIngame.java:361`).
* **inventory + JEI**: the GL-less early presented dump shows JEI's overlay with its text — the page indicator
  "1/16" and the "Gold Ore" tooltip. **No bridge leg exists**: the fixture opens `GuiInventory` at tick 30,
  closes it at tick 190, and every capture (world tick 200, the GUI stage, the game screenshot) is after that;
  `-Dceltium.validation.dumpPresentedTick` requests a *presented* dump, which a bridge run does not produce
  (the present path is GL-less-only). This is a fixture limitation, not a rendering result, and it is the one
  category whose bridge/GL-less pair the acceptance cannot be taken on with this fixture.

## 4. The advance arithmetic

* **By construction**: `FontRendererMixin` injects at HEAD/RETURN with a `CallbackInfoReturnable` it never
  writes, so `charWidth[ch]` and `(f1-f)/2+1` are returned exactly as vanilla returns them; every character
  after a glyph therefore advances by the same amount.
* **By picture**: with the mixins active in *both* legs, the title, the toast, the Done button and the
  unicode toast/完了 land at pixel-identical positions and sizes in the bridge and the GL-less leg (the crops
  above) — a corrupted return value would shift the characters after it and show up there first.
* **At the glyph**: the state probe (`n03-g-state`, `n03-m-mirror`) at armed overlay glyphs reads
  `cull=true depthTest=true blend=true alphaTest=true texture2d=true boundTexture=9`, the overlay ortho
  (`m[5]<0`, `tx=-1`, `ty=1`) and `mirrorMvT=(2,200,-2000)` — the game's own chat translate, not a defect.

## 5. What remains unproven

1. **No bridge reference for the inventory/JEI screen** (fixture timing, section 3). The GL-less leg shows the
   text; the pair cannot be completed without changing when the fixture captures.
2. **`§l`/`§o` through the unicode glyph method** was not exercised: the formatted labels are ascii strings
   from the pack, and the ja run has no formatting codes.
3. **The GL-less frame still lacks the GUI's Tessellator geometry** — panel and button faces, the chat's
   background bars, the hotbar — and the world's shard blob covers parts of the text in the GUI frames. That is
   a different finding (frame composition / immediate ordering), and it is what limits the list-label crops.
4. **The font texture mirror's own census** was not taken: the magenta glyphs prove the sampling works
   (texture × v_Color, the font page bound), but no separate measurement of the mirror's content was made.
5. The pre-fix probes (`mode=quads`, `winding=swapped`, `isolation=positionColor`, `screenFill`) were not
   re-run after the address fix beyond the configurations named above; their earlier results are void.
