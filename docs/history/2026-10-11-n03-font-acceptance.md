# N03 — the vanilla glyph path: the committed code, and the pictures

Worker record for the audit's N03 (P1: "vanilla text still reaches the GL-less no-op immediate path").
Tree: `6a27f1d51` (`font: A01 verified on the artifact, and the counter lines now reach the end of a run`), on top of
`318a24d0a` and the parent's `aa041c2d4` (the address fix). Evidence jars, each recorded in its run directory's
`jar-hashes.txt`:

| leg | engine jar sha256 | fixture jar sha256 |
|---|---|---|
| acceptance (`n03-ac/ae`, `n03-ad/af`, `n03-ag/ah`) | `216cd6284a3c22f79240222414cf64abbc4a2f5de8ab91d054a4be2dbd6f357d` | `2a9f705301d55c2e7f02798f2113570b71c7809b45d37f91e2a5427ed6d503f0` |
| earlier pairs (`n03-u/v`, `n03-p/q`, `n03-r/s`, `n03-w/x`) | `044f5aba990550f707b59c112121ecabbe4a842790d91bc1d62e511197d719b3` | `3fbc80fc7acf6087c02f95fee8fb138b84deaf1a66746086e16c4a291510fb94` |

The glyph path is byte-identical between those two engine jars; `216cd6284a` also carries the 64-line counter cap.
Runner: `.celtium-work/fontmatrix.py` (builds the same isolated game directory `scripts/prism_validation.py`
builds, adds the `options.txt` the harness never copies, records the jar hashes, and replays the harness's own
command through the harness's `inspect()`). Evidence sheets: `.celtium-work/n03/`. Run directories:
`.celtium-work/runtime/n03-*`.

## 0. A01 — the address defect, confirmed and verified on the artifact

The audit's A01 (`docs/history/2026-10-11-full-audit-1058.md`) is the same defect the parent fixed in `aa041c2d4`:
`submit()` wrote four 24-byte vertices and then took `MemoryUtil.memAddress(VERTEX)` without `flip()`, so the
backend was handed a pointer one past the last written byte and copied 96 bytes of unrelated native memory (the
isolation branch, 4×16, had the identical defect). The audit's live-jar observation was 11:15:29; the fix landed
11:24.

Verified the way the audit asks — on the built jar, not on the source:

```
$ unzip -o -q <engine jar> 'org/taumc/celeritas/impl/font/*'
$ javap -p -c org/taumc/celeritas/impl/font/CeltiumFontGlyphCapture.class | grep -E 'flip|memAddress|address-extent'
   579: invokevirtual ByteBuffer.flip()            <- isolation branch
   601: ldc_w "address-extent"
   645: invokestatic MemoryUtil.memAddress(ByteBuffer)
   655: invokevirtual ByteBuffer.flip()            <- normal branch
   679: ldc_w "address-extent"
   725: invokestatic MemoryUtil.memAddress(ByteBuffer)
```

Both branches flip, check `position == 0 && limit == 4*stride` and refuse with the named reason `address-extent`
before taking the address; the offsets the audit quoted (508/518, no flip) no longer exist.

CPU contract, no GPU, on the offered bytes themselves — `scripts/cpu-contract/GlyphAddressCheck.java`, extended
to both layouts with the producer's own write order and address-taking:

```
$ javac -cp <lwjgl-core.jar> -d /tmp scripts/cpu-contract/GlyphAddressCheck.java
$ java -Dorg.lwjgl.librarypath=<natives> -cp /tmp:<lwjgl-core.jar> GlyphAddressCheck
normal: position=96 capacity=96 memAddress(buf)-memAddress(buf,0)=96
  ok   normal: written extent is exactly 4x24 (position=0 limit=96)
normal: offered address is the first written byte: delta=0
  ok   normal: vertex 0..3 position, uv and colour recovered exactly (63,63,63,255)
  ok   isolation: written extent is exactly 4x16 (position=0 limit=64)
  ok   isolation: vertex 0..3 position and colour (offset 12) recovered exactly
defect: unflipped delta=96 reads 2.3801702E20, flipped delta=0 reads 1000.0
  ok   defect: the unflipped address does not read the first written vertex
  ok   defect: the flipped address reads the first written vertex
CELTIUM_CPU_CONTRACT PASS
```

The strip order, the italic offsets and the advance are untouched: the shipped path submits the vanilla
`GL_TRIANGLE_STRIP` order, mode 5, and no injection writes the return value. `-Dceltium.font.mode=quads` is a
diagnostic only, and its note now says that changing the draw mode alone does not preserve that strip's vertex
order — the pre-fix runs that suggested otherwise shared the bad address and are not evidence.

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

Final totals on the current tree (`n03-ac-terrain-deco`, terrain scene, GL-less, jar `216cd6284a`):
`submitted=56000 accepted=56000 refused=0` on the counter line, `CELTIUM_REFUSALS_BY_FORMAT {}`, and the run's own
verdict is **PASSED** (`presentedNonBackgroundFraction=0.841951`, `presentedBlackFraction=0.00733`). The unicode
leg (`n03-ad-terrain-ja`) reads `unicodeWindows` for every window, `defaultWindows=0`, 0 refused.

Unicode leg (`n03-r-ja`, `lang:ja_jp` + `forceUnicodeFont:true`, the options.txt the harness never copies):
`unicodeWindows=40548, defaultWindows=0, submitted=40000 accepted=40000 refused=0` at the old progress cap, and
`n03-ab-accept-ja` (same flags, jar `ed0e7000`) ended with
`CELTIUM_FONT_GLYPH_SUMMARY windows=76270 defaultWindows=0 unicodeWindows=76270 bridgeWindows=0 quads=75790
submitted=75790 accepted=75790 refused=0 reasons={}` — `func_78277_a` is exercised and accepted, at a batch
boundary of 75790 quads.

## 3. The text categories, each with its bridge/GL-less pair and crops

**The acceptance legs are the GL-less ones** (the bridge is the reference for what the text should look like; its
settings text is drawn by GL and is not this acceptance). They are taken in the **terrain** scene, which the
picture gate accepts, because in the video-settings scene the panel the floors describe is still missing under
GL-less (the open GUI-geometry gap) and that scene's verdict cannot be evidence for the font.

All crops are `x0,y0,x1,y1` in the 1280×720 frame. Sheets are in `.celtium-work/n03/`.

| category | GL-less leg (jar `216cd6284a`) | bridge leg | sheet | crops |
|---|---|---|---|---|
| ASCII + shadow | `n03-ac-terrain-deco` (PASSED) | `n03-ae-terrain-bridge` (PASSED) | `terrain-ascii-shadow.png` | chat `0,320,660,620`; toast `760,10,1280,100` |
| bold + italic (`§l`/`§o`) | `n03-ac-terrain-deco` | `n03-ae-terrain-bridge` | `terrain-ascii-shadow.png` | toast `760,10,1280,100` |
| unicode (Japanese) | `n03-ad-terrain-ja` | `n03-af-terrain-jabridge` | `terrain-unicode.png` | toast `760,10,1280,100`; chat `0,320,660,620` |
| video-settings crops (secondary) | `n03-aa-accept`, `n03-ab-accept-ja` | `n03-v-cleanbridge`, `n03-s-jabridge` | `ascii-shadow-bold.png`, `decorated-labels.png`, `unicode-ja.png` | title `520,0,760,45`; toast `880,10,1280,90`; Done `520,630,760,690`; list label `640,110,1110,175` |
| inventory + JEI | `n03-t-jei` early presented dump (tick 40) | **none exists** | `jei-inventory.png` | JEI page `1030,20,1140,60`; tooltip `660,315,820,355` |

What the pairs show:

* **ASCII + shadow**: the chat lines `(0,320,660,620)` carry the same `/celtium` messages in both legs, with the
  shadow's dark offset copy visible in each. 24 px glyphs, 27–30 px line pitch, lines starting at x≈6. In the
  GL-less leg the world's shard triangles cover parts of the block (the last line, "Terrain still renders with
  OpenGL.", is complete; the lines above it are cut where the shards cross them) — the frame-composition gap, not
  the font.
* **bold + italic**: the decoration pack `.celtium-work/packs/n03-format` (installed into the isolated game
  directory only) puts `§o` on `tutorial.move.title` and `§l§o` on `tutorial.move.description`, i.e. on the two
  strings the terrain scene actually draws (`TutorialToast.java:26-27` stores `getFormattedText()`, `:40-45` draws
  it with `fontRenderer.drawString`). The toast `(760,10,1280,100)` is italic in the GL-less leg and in the
  bridge, with vanilla's own bold words ("W, A, S, D", "SPACE") unchanged — identical size and position.
* **unicode**: the ja_jp + `forceUnicodeFont` toast `(760,10,1280,100)` reads
  "W、A、S、D で移動します / SPACE でジャンプします" in both legs, pixel-comparable, every glyph through
  `renderUnicodeChar`.
* **video-settings crops (secondary)**: the same categories on the GUI screen — "Video Settings"
  `(520,0,760,45)`, the toast `(880,10,1280,90)`, "Done" `(520,630,760,690)`, the `§l§o` list label
  `(640,110,1110,175)` — match the bridge in size and position, but the list label is tail-only in the GL-less
  leg because the missing panel is covered by the shards, and in Japanese the wider toast icon sits over the
  title's spot. Those runs' *verdicts* fail on the panel floors (`guiPanelRowAmplitude`,
  `guiPanelDistinctColours`), which is the GUI-geometry gap, not the font.
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

1. **No bridge reference for the inventory/JEI screen**, and the fixture's new support for it does not fire yet.
   `37fd1036b` added `-Dceltium.validation.screenshotAtTick=<tick>` (the configuration's own screenshot at that
   tick, with `screenshotAtTickScreen` recording which screen was open) and `keepInventoryScreen`, but in four
   runs on the current fixture the tick-30 open never happened: `jeiScreenOpenedAtTick` is absent from the report
   and `screenshotAtTickScreen` is absent at tick 35 and `none` at tick 60, while `worldTicksAtEnd` is 200–213 —
   i.e. `frame()` does not see `worldTicks == 30`/`35` although it does see 60. Runs: `n03-ag`, `n03-ah`,
   `n03-ai`, `n03-aj`, `n03-ak` (the last in the video-settings scene, so it is not the scene). The GL-less leg
   from the earlier fixture (`n03-t-jei`, tick-40 presented dump) is the only picture of that screen with text.
2. **`§l`/`§o` through the unicode glyph method** was not exercised: the formatted strings are ascii
   (`tutorial.move.*`, `gui.done`, `options.*`), and the ja run's toast comes from the ja asset without
   formatting codes.
3. **The GL-less frame still lacks the GUI's Tessellator geometry** — panel and button faces, the chat's
   background bars, the hotbar — and the world's shard blob covers parts of the text in the GUI frames. That is
   a different finding (frame composition / immediate ordering), and it is why the video-settings scene's
   verdict fails on panel floors and why the list-label crops are tail-only there.
4. **The font texture mirror's own census** was not taken: the magenta glyphs prove the sampling works
   (texture × v_Color, the font page bound), but no separate measurement of the mirror's content was made.
5. The pre-fix probes (`mode=quads`, `winding=swapped`, `isolation=positionColor`, `screenFill`) were not
   re-run after the address fix beyond the configurations named above; their earlier results are void.
6. **The `CELTIUM_FONT_GLYPH_SUMMARY` shutdown-hook line is not reliable**: it appeared in `n03-z-final` and
   `n03-ab-accept-ja` and not in `n03-aa-accept` — the same Log4j-shutdown race the parent measured for
   `CELTIUM_NOOP_CENSUS`. The counter lines are the evidence; the cap is 64 lines since `6a27f1d51` so they
   reach the end of a run (56000 accepted in the terrain acceptance, 0 refused).
