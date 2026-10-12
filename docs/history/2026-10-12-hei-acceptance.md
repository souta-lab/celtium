# HEI (Had Enough Items 4.31.2) real-screen acceptance — Goal C

Owner: **gui-text** (the Lead handed this unit over when Goal C's mod acceptance was unowned). The renderer is the
one under test; **no renderer source was edited for this unit**. What was added is fixture-side only
(`ValidationClient.java`, `CeltiumJeiPlugin.java`) and one instrument
(`scripts/cpu-contract/hei_screen_check.py`).

## Why this is a *picture* acceptance and not "HEI loads"

`docs/mod-compatibility.md` already records that HEI loads and the game reaches the world (`r148-hei`:
6 mods, `worldTicksAtEnd=241`). Goal C says the same thing the file does: **loading is not acceptance**. This
unit takes the mod's own screens and measures them.

## The legs

**Reference leg first**, because the candidate's jar must not be built from a tree that carries another
worker's unlanded extraction WIP (`TextureCopyService` was in flight during this unit, and it touches exactly
the texture-copy path HEI's icons and tooltips use). The Lead's instruction was to use the wait for the
reference and to hold the candidate until the tree is clean.

| run | jar (sha256 prefix) | fixture (sha256 prefix) | scene | verdict |
|---|---|---|---|---|
| `hei-ref` | upstream Celeritas `d12425eaaf184e79` | `c39840d62e3695e9` | `jeiScreen` + `keepInventoryScreen`, no filter, `pinSections=64`, tick 200 | `UNCALIBRATED_PRESENTED_IMAGE`, `validationClean=true`, `validationMessageCount=0` |
| `hei-ref-list` | same | same | the same + `jeiSearch=diamond` + `jeiFilterText=diamond` | same |
| `hei-ref-recipe` | same | same | the same + `jeiRecipe=true` (HEI's own recipe GUI) | same |
| `hei-ref-cycle` | same | same | `jeiScreen` + `jeiRecipe` + `keepInventoryScreen=false` (inventory → HEI → back) | same |

All four: AMD Radeon RX 6400 (RADIX/radeonsi), 1280x720, `--headless --force-x11`, git HEAD `0e733a5f6`,
the upstream jar with **no celtium jar and no glless flag**, the same HEI 4.31.2 jar in the instance.
`UNCALIBRATED_PRESENTED_IMAGE` is correct here and is not a failure: the JEI/HEI screen variant has no
calibration key, which is what the fixture reports as `fixtureUncalibrated`.

## What the reference frames show (measured)

### (a) the item list draws, with isometric icons

`scripts/cpu-contract/hei_screen_check.py` detects the slot grid from the slot background's own periodicity and
reports the **cell fill fraction**: the fraction of a 16x16 icon square that differs from that square's modal
colour. An isometric block icon covers ~70 % (task-50 measured `0.692-0.746` on the reference for the same
kind of grid); a flat texture filled edge to edge covers ~1.0.

```
hei-ref-list/screenshots/celtium-at-tick-200.png
  slot grid: origin=(378,213) pitch=54 cols=9 rows=7 -> slots measured=63
  cell fill: min=0.234 median=0.781 max=0.848 mean=0.681
```

The pitch is 54 px = 18 GUI px at GUI scale 3, and the origin `(378,213)` is the grid's first icon square,
both derived from the picture rather than from a constant - the same instrument therefore reads the candidate
leg's frame with the same geometry.

### (b) the search box and its filter

```
hei-ref (no filter)   search box: dark=14269 pure-white(text)=0     item-list strip: distinct=8988 backgroundFraction=0.028
hei-ref-list (filter) search box: dark=12793 pure-white(text)=801   item-list strip: distinct=1042 backgroundFraction=0.076
```

and the mod's own account of the same moment, read through its public API in `CeltiumJeiPlugin.listState()`
(`IIngredientListOverlay.getVisibleIngredients()`, `IIngredientFilter.getFilterText()`):

```
before: visible=60  filtered=908 keyboardFocus=false filterText=""
after:  visible=13  filtered=13  keyboardFocus=false filterText="diamond"
```

The picture and the API agree: the search field shows `diamond` (801 pure-white text pixels against 0), and the
list drops from 908 matching ingredients to the 13 diamond ones.

**The keystroke route was attempted first and is measured not to work from a fixture.** `-Dceltium.validation.jeiSearch`
delivers each character through `GuiScreen.keyTyped` (SRG `func_73869_a`, resolved from the SRG table and invoked
reflectively - the same route the fixture uses for `mouseClicked`, and the method Minecraft's own keyboard loop
calls), and the mod's filter still read `keyboardFocus=false filterText=""` afterwards. That is a property of the
mod's handler (it reads the LWJGL keyboard event rather than the method's arguments) and of the screen the fixture
opens: **`jeiScreen` opens `net.minecraft.client.gui.inventory.GuiContainerCreative`, not `GuiInventory`**, because
the fixture's world is CREATIVE - the flag's name and the doc's description of it are both older than that
measurement. The filter is therefore set the way the mod offers for another mod to set it,
`IIngredientFilter.setFilterText`, and the artifact names that path in `jeiFilterTextPath` beside the state it
produced, so an API drive is never read as a keystroke.

### (c) the tooltip

The creative grid's own tooltip ("Gold Ore") is present in the same frame, at the region
`(660,315)-(820,355)` that `compare_jei_pair.py` already uses. Crops saved with the instrument into
`.celtium-work/hei-acceptance/crops/`: `<label>-tooltip.png`, `-search.png`, `-overlay.png`.

### (d) the transition inventory → HEI → back

```
hei-ref-cycle: jeiScreenOpenedAtTick=31   jeiRecipeOpened=true  jeiRecipeOpenedAtTick=41
               screenshotAtTickScreen=none (the screen is closed again before the tick-200 capture)
hei-ref-recipe: screenshotAtTickScreen=mezz.jei.gui.recipes.RecipesGui (HEI's own screen is the captured one)
```

`jeiRecipe` reaches HEI through the mod's own plugin API (`IRecipesGui.showCategories`, the first category the
mod offers), not by simulating input; `jeiRecipeOpened=true` is the mod's answer, and the captured screen class
is the mod's own GUI.

## The candidate half (taken 2026-10-12)

### The artifacts, and how they are attributed

The candidate legs run on jars copied out of the build tree into `.celtium-work/hei-cand-frozen/` **while the
tree was clean**, so that a concurrent rebuild cannot move the artifact under the evidence:

| file | sha256 | built (mtime) | attribution |
|---|---|---|---|
| `celtium-pre1830.jar` | `543745fbe076f15d…` | 16:30:25 | the content of `cc103738f`: that commit (16:35:46) captures the tree whose newest source edit is 16:30:05, and no source file changed between the jar write and the commit |
| `celtium-7bd46714.jar` | `7bd467145b0d28e4…` | 16:39:26 | the Lead's rebuild at `cc103738f` + perf-ring's curated-F3-panel WIP (which does not touch the HEI draw path) |
| `fixture-pre1830.jar` | `6ee2eb580015f94e…` | 16:29:28 | byte-identical to the fixture jar the Lead rebuilt afterwards (same sha256) |

Disclosure, because it costs a round if it is not written down: between the two legs the worktree stopped
compiling - perf-ring's first cut of `celtium$addCuratedLines` called the **instance** method
`VulkanContext.uploadBatchSummary()` (`VulkanContext.java:2425`) from a static context
(`CeleritasVintage.java:84`), and `scripts/build.sh` failed with that one error in 24 s
(`.celtium-work/hei-build.log`). The Lead reports the fix and a `BUILD SUCCESSFUL`; the second candidate leg was
taken on that rebuild. No renderer source was edited for this unit.

The fixture flags are **identical** to `hei-ref-list` (checked by diffing the flag sets); the only difference is
the three celtium-side flags under test (`glless`, `presentReadback`, `validation`).

### The legs

| run | celtium jar | flags vs `hei-ref-list` | verdict |
|---|---|---|---|
| `hei-cand` | `543745fbe076f15d…` | same | `UNCALIBRATED_PRESENTED_IMAGE`, `validationClean=true`, `validationMessageCount=0`, `errors=0` |
| `hei-cand2` | `7bd467145b0d28e4…` | same | same |
| `hei-cand-cycle` | `7bd467145b0d28e4…` | `keepInventoryScreen=false`, `jeiRecipe=true`, no `jeiFilterText` (mirrors `hei-ref-cycle`) | same |

All three: AMD Radeon RX 6400 (RADV NAVI24), 1280x720, `--headless --force-x11`, `--slot-wait`, HEAD
`cc103738f`, 6 mods locked, `nativeDeviceMatchedOpenGl=false` (correct under GL-less). The picture is the
**presented dump** (`pictureEvidenceSource=presented-dump`); the GL screenshot is black by construction and the
fixture says so (`pictureScreenshotUsable_tick200=false`).

### Measured, candidate against the reference frame

| quantity | reference (`hei-ref-list`, GL) | candidate (`hei-cand` / `hei-cand2`) |
|---|---|---|
| detected slot grid | `origin=(378,213) pitch=54 cols=9 rows=7` | identical |
| cell fill (median / mean) | `0.781` / `0.681` | `0.781` / `0.682` |
| search box pure-white text px | 801 | 801 (identical) |
| item-list strip distinct colours | 1042 | 1415 / 1330 |
| filter state (mod's own API) | `60/908 ""` -> `13/13 "diamond"` | `60/908 ""` -> `13/13 "diamond"` |
| tooltip region mean abs diff, px>8 | - | `22.710`, 662 (10.34 %) |
| `jeiScreenOpenedAtTick` | 31 (cycle) | 30 / 33 |
| `jeiRecipeOpenedAtTick` | 41 | 43 |
| `screenshotAtTickScreen` (cycle) | `none` | `none` (screen gone again before the tick-200 capture) |

Counters, read from the **last** `CELTIUM_VERIFY` line (`via=fixture end`) of each leg: `passed=1030 errors=0
refusals=0`, `acceptedByFormat[4]` (the item format, `id4`) `5614` / `8305` / `5666` and
`drawnByFormat[4]` `7876` / `11647` / `6866` (drawn > accepted because a group is replayed across passes);
`lastFrameAccepted=154 lastFrameDrawn=135` on the two list legs and `48/41` on the cycle leg. The upstream
reference has no such counters and none are claimed for it: **its legs print no `CELTIUM_VERIFY` line at all**,
which is a property of that leg (a slow leg never reaches the bounded `frame 120`/`present 120` triggers), not
evidence that its counters were zero.

### The finding: the tooltip's main text pass loses ink

The vanilla item tooltip ("Gold Ore", region `(660,315)-(820,355)`) is where the candidate and the reference
disagree, and `scripts/cpu-contract/font-shadow-main-check.py` names why. The invariant it checks comes from
`FontRenderer.renderString` (`.celtium-work/mc-sources/net/minecraft/client/gui/FontRenderer.java:562-593`,
called twice by `drawStringWithShadow`): both passes draw the *same* glyph geometry, the shadow at `(x+1,y+1)`
in the shadow colour, the main at `(x,y)` in the text colour, so every shadow-coloured pixel must have a
main-coloured pixel one GUI pixel up and to the left.

```
reference  tooltip: shadow_px=693  shadow_without_main=0   (0.0 %)
candidate  tooltip: shadow_px=797  shadow_without_main=585 (73.4 %)      (both candidate builds, identical)
```

The white main-pass ink in that box drops from **828 px to 238 px**, and the candidate's 238 white pixels are a
**strict subset** of the reference's 828 (candidate-only = 0, reference-only = 590): same geometry, same
positions, pixels simply not drawn - what shows through them in the candidate is the tooltip's own background
(`(19,4,19)`, `(18,3,18)`, `(25,10,25)`, …), and 104 of them are the exact shadow colour. The loss is
column-structured at GUI-pixel resolution (whole 18-px cells missing, neighbours perfect), which is what makes
it visible as "part of the text is dark" rather than as a shifted or dimmed string.

Scoping, from the same instrument on the same frame:

| region | reference | candidate |
|---|---|---|
| tooltip `(660,315,820,355)` | 0 / 693 | 585 / 797 |
| creative panel `(420,120,860,600)` | 26 / 719 | 732 / 944 (`cand2` 741 / 953) |
| HEI search box `(950,655,1270,712)` | 0 / 675 | 0 / 675 |
| item count `(1030,20,1140,60)`, hotbar `(490,660,790,720)` | 0 shadow px | 0 shadow px; `compare_jei_pair.py` reads a mean abs diff of `0.000` and 0 px > 8 on both |

So **HEI's own text is pixel-identical to the reference** (675 shadow pixels, zero violations, 801 pure-white
search text pixels on both sides) while the **vanilla GUI text drawn in the same screen** (the tooltip, and the
creative panel region around it) loses most of its main pass. The reference passes the same check cleanly, and
the two candidate builds - taken hours apart on two jars - give the same numbers to the pixel, so this is not a
capture race: the whole leg's other text (`item count`, `hotbar`) matches the GL reference **exactly**, and a
dump/readback artifact cannot preserve one run of glyphs to the pixel while deleting whole cells of another.

This is the user's original complaint ("part of the GUI text goes dark", the tooltip crop) reproduced on a real
mod screen with a GL partner that is clean, and it is **reported, not fixed here**: the state that differs
between the two passes in the immediate path is renderer-source territory
(`VulkanTerrainGpuBackend.submitImmediateBatchTimed`, the batch site the format counters come from). Which
state that is - clipping, the second pass's colour/coverage, or the batch split - has to be named by whoever
owns that file, with a two-jar experiment on this same crop.

### Crops

`.celtium-work/hei-acceptance/crops/`: `ref-`/`cand-`/`cand2-{tooltip,search,overlay}.png`, and
`tooltip-ref-vs-cand-4x.png` (the two tooltip boxes, 4x nearest, reference above candidate).

## Instruments named

* `scripts/cpu-contract/hei_screen_check.py` (new): one frame in, cell-fill table + search-box text pixels +
  item-list strip statistics + crops out. Geometry detected and printed, not hard-coded.
* `scripts/cpu-contract/compare_jei_pair.py` (existing): the region-by-region pair comparison.
* `scripts/cpu-contract/font-shadow-main-check.py` (existing, from the GUI-text unit): the shadow/main invariant
  above - it needs no known string and no reference picture, which is why it can be pointed at the candidate's
  tooltip box and at the reference's with the same arguments. A frame-wide count includes non-text pixels and is
  only a finding inside a region known to be text; the numbers quoted above are per-region.
* Fixture: `-Dceltium.validation.jeiSearch=<text>` (real keyboard path, with the mod-API read-back that measured
  its limit) and `-Dceltium.validation.jeiFilterText=<text>` (the mod's public API, named as such in the report).
