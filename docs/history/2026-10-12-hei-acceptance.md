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

## The candidate half: not taken, and why

Not taken **on purpose**. The candidate jar available at the time (`forge122/build/libs/…-0.0.2-alpha-dev.jar`)
contained `TextureCopyService.class`, i.e. another worker's **unlanded** texture-copy extraction, and was built
before `3870c4ae6`. A HEI acceptance taken on that jar could not separate "HEI's drawing is wrong" from "the
unlanded extraction broke the copy path", which is the same shape as the old-name-jar trap this project already
lost a leg to. The Lead's decision was the same: hold the candidate until the extraction lands, and spend the
wait on the reference.

**The candidate leg must use the same fixture and flags** as `hei-ref-list` and be compared with
`compare_jei_pair.py` (regions: whole frame, the item count, the tooltip, the inventory panel, the hotbar) plus
the cell-fill table above, with `mods[].sha256`, HEAD and `git status --short` in the record. The candidate's
per-format counters (`id4` accepted/drawn per frame) come from its own report; the upstream reference has no
such counters and none are claimed for it.

## Instruments named

* `scripts/cpu-contract/hei_screen_check.py` (new): one frame in, cell-fill table + search-box text pixels +
  item-list strip statistics + crops out. Geometry detected and printed, not hard-coded.
* `scripts/cpu-contract/compare_jei_pair.py` (existing): the region-by-region pair comparison.
* Fixture: `-Dceltium.validation.jeiSearch=<text>` (real keyboard path, with the mod-API read-back that measured
  its limit) and `-Dceltium.validation.jeiFilterText=<text>` (the mod's public API, named as such in the report).
