# The hotbar's light map: the unit's enable state is the key, and the TESR item is the case

Owner: **glyph-fix**. Unit: "hotbar items darken in dark places". Implemented, controlled, and measured on a chest in
the hotbar against upstream Celeritas in real GL.

## The rule

A group offered while the **light-map texture unit was disabled** writes the full-bright pair `(240,240)` into its
light-map slot instead of the captured coordinate; a group offered while the unit was enabled keeps the capture. The
key is the unit's own enable state, not the world-pass flag, because that is what real OpenGL modulates by:
`EntityRenderer.enableLightmap()`/`disableLightmap()` (SRG `func_180436_i`/`func_175072_h`, resolved from the SRG
table) bracket exactly three places - the entity pass (`RenderGlobal.java:1166/1205`), the first-person hand
(`EntityRenderer.java:825/827`, inside `renderWorldPass`) and the block-entity section (`:1434/1403`) - and the whole
GUI stage runs with the unit off.

* `EntityRendererMixin` hooks both methods (`require = 0`) and calls `TerrainGpuBackend.setLightmapUnitEnabled`
  (default no-op in the interface, implemented in `VulkanTerrainGpuBackend`).
* `celtium$lightmapForGroup()` (`VulkanTerrainGpuBackend.java`, next to `lightmapCoords()`) makes the choice, and both
  the immediate path (at group open, carried in `currentGroupLightmapCoords`) and the **model path** (`:3012-3019`)
  use it.
* The flag **defaults to enabled**, so a silently dropped injection leaves every group on the captured coordinate -
  the behaviour before this rule - rather than drawing every entity full-bright.
  `lightmapUnitEnables`/`lightmapUnitDisables` in the verification summary are what say the hook ran.
* `-Dceltium.vulkan.lightmapFullBrightGui=false` is the one-variable control: the same jar, the same scene and the
  same tick then draw the GUI item with the world's light.
* The fixture gained `-Dceltium.validation.heldItem=chest` (a TESR item) beside `apple`/`enchanted`, because the
  apple cannot reach the case this rule exists for.

## Why the pass flag alone was not the answer: the plain item never samples the light map

New instrument: `itemLitGroups` / `itemLitGuiGroups` / `itemUnlitGroups` / `itemUnlitGuiGroups`, the ITEM family's
batches split by variant and by stage. On the night leg with an apple in hand (jar `1a353ae3`, HEAD `e30a03cd6`,
`validationClean=true`, messages **0**):

```
itemLitGroups=4406  itemLitGuiGroups=0  itemUnlitGroups=116  itemUnlitGuiGroups=116
```

**Every GUI-stage ITEM batch is unlit; none is lit.** An unlit group never reads the light-map coordinate, so the
apple is pixel-identical in the rule-on, rule-off and reference legs (855 px, mean `(220.3, 32.3, 45.0)`) - which is
also what the reference draws. That is why the pass-based version of this rule changed no hotbar pixel: the criterion
"the hotbar item stays bright" was already met before it, and a plain item cannot be the user's report.

## The case that is: a TESR item in the hotbar

`RenderItem.renderItem` sends an item whose model `isBuiltInRenderer()` - a chest, a shulker box, a banner - through
`getTileEntityItemStackRenderer().renderByItem` (`RenderItem.java:144-148`). Our renderer captures that through the
**model** path, which is lit and used the captured world coordinate unconditionally. In the hotbar that coordinate is
the world's - dark at night, darker in a cave - while vanilla draws it with the unit disabled. Measured with
`heldItem=chest`, night (`worldTime=18000`), `keepGui=true`, AMD Radeon RX 6400, 1280x720, GUI scale 3, all three legs
`fixtureStatus=UNCALIBRATED_PRESENTED_IMAGE` (correct - no calibrated key for this variant), `validationClean=true`,
**`validationMessageCount=0`**, `windowFlushFailures=0`, `drawnByFormat` id9 `228/228`:

| leg | picture sha256 | hook | `lightmapFullBrightGroups` | `itemLitGuiGroups` | chest's own wood pixels (mean / max) |
|---|---|---|---|---|---|
| reference, upstream Celeritas `989ebf2e`, real GL | `c4ecf652bd84f283…` | - | - | - | **(106.5, 74.7, 25.6) / (171,121,47)** |
| our jar, rule ON | `558ee4d7b20ba96f…` | enables 805, disables 906 | 2638 | 201 | **(111.0, 76.4, 26.2) / (223,158,61)** |
| our jar, rule OFF (control) | `5cd70cb2cef5b876…` | enables 989, disables 1113 | **0** | 255 | **(82.2, 48.1, 16.8) / (106,63,26)** |

Selector for the chest's own pixels: `R-B > 40 and R > 60` inside slot 0. The rule-off leg has **no bright chest
pixel at all** (max 106 against the reference's 171) - that is the user's "dark chest in the hotbar", and the rule-on
leg is within ~4 % of the reference's mean. `itemLitGuiGroups=201/255` is the direct proof that a GUI-stage TESR item
reaches the lit path, which is what makes the coordinate matter there.

Evidence crop at the user's own scale, three rows (reference / rule ON / rule OFF):
`docs/history/evidence/hotbar-lightmap-20261012/hotbar-chest-ref-on-off.png`, sha256 `68c908460f835f52…`.

**World-pass control, same legs**: the mob band (430,130)-(900,360) means are `(52.7,36.7,37.4)` rule-on against
`(52.6,36.6,37.3)` rule-off, and the terrain band (300,420)-(900,600) `(79.9,67.0,65.7)` against `(80.9,67.4,66.2)` -
the rule does not touch the world pass, which is the whole point of keying on the unit. (The mob band's distance from
the reference's `(41.9,30.7,30.5)` is the pre-existing night-brightness residual, not this change.)

## What this unit did **not** establish

* That the user's own scene had a TESR item in the hotbar. The mechanism is now measured with one, and the fixture can
  place one, but the report itself was not reproduced on the user's instance.
* A **cave**: `worldTime=18000` is night in the open, where the sky light is 15 and the light map's sky column is dark.
  Same sign, different coordinate.
* The inventory screen's player model in a dark place (the model path now follows the unit there too, and
  `GuiInventory.java:154-165` only *disables* the unit, so the rule should leave it as vanilla draws it - not
  pictured).
* Whether any GUI-stage ITEM batch is ever lit (the census says none is today; a mod could change that, and the rule
  would then apply to it).
