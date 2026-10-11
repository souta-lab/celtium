# The hotbar's light map: the pass rule fires, the plain item does not move, and the TESR item is the real case

Owner: **glyph-fix**. Unit: "hotbar items darken in dark places". Two legs of the pair, one GL reference, one
control leg, and one regression that is not this change.

## The rule that was asked for, implemented

For a group offered **outside** the world pass, `celtium$lightmapForGroup()` (`VulkanTerrainGpuBackend.java:10569`)
returns the full-bright pair `(240,240)` instead of the captured coordinate; inside the world pass it returns the
capture, unchanged. The decision is taken **when the group is opened** and carried in `currentGroupLightmapCoords`,
not read at close: `endWorldPass()` clears `worldPassActive` *before* it flushes the groups the world pass left
pending (`:3650` then `:3665`), so a live read would classify the hand and the entities as GUI-stage and draw them
full-bright. The model path (`:2900`) is deliberately left on the captured coordinate - see below.

`-Dceltium.vulkan.lightmapFullBrightGui=false` turns the rule off inside the same jar, which is what makes the
before/after a one-variable experiment. The census gained `lightmapCoordCalls` / `lightmapCoordGroups` /
`lightmapFullBrightGroups` (the first two had no reporting site at all) and the ITEM variant split
`itemLitGroups` / `itemLitGuiGroups` / `itemUnlitGroups` / `itemUnlitGuiGroups`.

## Measured: the plain hotbar item is unaffected

Legs (AMD Radeon RX 6400, 1280x720, GUI scale 3, `keepGui=true`, `heldItem=apple`, `worldTime=18000`,
`keepEntities`+`captureEntities`, GL-less, presented dump):

| leg | jar | fixture status | validationClean | messages | picture sha256 |
|---|---|---|---|---|---|
| reference, upstream Celeritas `989ebf2e`, real GL, no celtium jar | - | `UNCALIBRATED_PRESENTED_IMAGE` | true | **0** | `a99b57d081f49045…` |
| candidate, rule ON | `9170b641a57d05065d8bc34215450296` | `UNCALIBRATED_PRESENTED_IMAGE` | true | **0** | `5ccb05cafd9fee1f…` |
| candidate, rule OFF (`-Dceltium.vulkan.lightmapFullBrightGui=false`) | same jar | `UNCALIBRATED_PRESENTED_IMAGE` | true | **0** | `5872ea7b0c974952…` |

`docs/history/evidence/hotbar-lightmap-20261012/hotbar-night-ref-on-off.png` (sha256 `ced0b79ca816c6ec…`) is the
hotbar strip at the user's own scale, three rows: reference / rule ON / rule OFF.

The apple is **pixel-identical in all three**: 855 pixels with `R>120 and R>G+40`, mean **(220.3, 32.3, 45.0)**;
the empty slot beside it differs by less than 0.5. The counters say the rule did fire - `lightmapFullBrightGroups`
1399 with the rule on against **0** with it off, `lightmapCoordGroups=2440`/`2667` - so the picture's silence is not
the rule failing to run. **The acceptance criterion "the hotbar item stays bright while the terrain darkens" is
already satisfied before the fix**, and the reference agrees: vanilla draws the hotbar item bright, which is what
`EntityRenderer.enableLightmap()`/`disableLightmap()` (`EntityRenderer.java:845-865`) says - the light-map unit is
enabled only inside the entity pass (`RenderGlobal.java:1166/1205`), the first-person hand
(`EntityRenderer.java:825/827`) and the block-entity section (`:1434/1403`), and `GuiIngame.renderHotbar` never sets
the coordinate while `GuiContainer.java:107` sets `(240,240)` for its items.

So a plain item is not reached by the coordinate at all. The instrument that would say which ITEM variant it got is
built (see above) and the leg that reads it is blocked by the regression below.

## The case that does match the user's report: TESR items

`RenderItem.renderItem` sends an item whose model `isBuiltInRenderer()` - a chest, a shulker box, a banner - through
`getTileEntityItemStackRenderer().renderByItem(stack)` (`RenderItem.java:144-148`). Our renderer captures that through
the **model** path, which *is* lit (`group.lightParams` non-null, `:2899`) and takes the **captured world coordinate**
(`:2900`). In the hotbar that coordinate is the world's - dark at night, darker in a cave - while vanilla draws it
bright, because the light-map unit is disabled there. That is "hotbar items darken in dark places", and it is also
why the inventory looks right and the hotbar does not: the inventory's items get `(240,240)` from
`GuiContainer.java:107`.

The rule that matches vanilla for **both** cases keys on **the light-map unit's enable state at capture**, not on the
pass: full-bright when the unit was disabled (hotbar TESR item, plain GUI item), captured coordinate when it was
enabled (hand, entities, and the inventory's player model, which `GuiInventory.java:154-165` deliberately draws with
the unit on). That distinction is what the pass rule approximates, and it is why the pass rule must **not** be
extended to the model path as it stands: it would fix the chest in the hotbar and brighten the inventory's player
model, which vanilla world-lights on purpose.

The stub records only a *global* `GL_TEXTURE_2D` enable (`CeltiumGlStub`'s enables map is keyed by capability;
`activeTexture` is tracked separately), so the unit-keyed rule needs either a per-unit record for `GL_TEXTURE_2D` or
a hook on `enableLightmap`/`disableLightmap`.

## A regression at HEAD, not from this change

| jar | tree | same leg |
|---|---|---|
| `9170b641a57d05065d8bc34215450296` | older tree + this change | `exit=0`, `validationClean=true`, messages **0**, `worldTicksAtEnd=207`, `lightmapCoordCalls=960 lightmapCoordGroups=3964 lightmapFullBrightGroups=2313 windowFlushFailures=0` |
| `f33db13f41a47b12878b574b95f71717` | HEAD `f91503350` + this change | `exitCode=-6` (SIGABRT), `MISSING_REPORT`, messages **2**, no picture: `VUID-vkCmdCopyBuffer-dstBuffer-parameter` (`dstBuffer Invalid VkBuffer Object`) |

Reproduced twice (`hotbar-night-on2`, `hotbar-night-on3`). This change selects a float pair and adds counters; it
cannot invalidate a `VkBuffer`, and the identical code ran clean on the earlier jar. The commits between the two
builds are the suspects and someone should bisect them before more legs are taken.

## What this unit did **not** establish

* Which ITEM variant the hotbar's plain item gets (lit or unlit) - the instrument exists, the leg is blocked by the
  regression.
* Whether the user's own scene has a **TESR item** in the hotbar: the fixture's `heldItem` property builds an
  `Items.APPLE` whatever the value (`ValidationClient.java:3950-3963`), so this leg cannot place a chest there. The
  TESR-item mechanism is read from the sources, not seen in a frame.
* Any cave scene: `worldTime=18000` is night in the open, where the sky light is 15 and the light map's sky column is
  dark - the same sign as a cave, but not the same coordinate.
