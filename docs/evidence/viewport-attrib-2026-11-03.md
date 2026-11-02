# the attribute stack puts the viewport back, which is the group this pack's mods select and change (2026-11-03)

`GL_VIEWPORT_BIT` was the one *selected-by-a-real-mod* attribute group this renderer could not put back. This round
closes it, and the pack census that picked it is part of the evidence: the group's priority came from reading every
`glPushAttrib` call site in the 193 mod jars of the Supersymmetry instance, not from guessing which group "sounds
important".

## What the pack actually does

`javap -c -p` over every class that references `glPushAttrib`, reading the constant pushed before the call:

| mask | call sites | groups it selects that this renderer could not restore |
|---|---|---|
| `0xFFFFF` | Susy-Core `SunGlowRenderer`, `PlanetSurfaceRenderer`, `RingFeatureRenderer` (space sky, every frame), OpenComputers `MFUTargetRenderer`, Commons0815 `EnergyBar`/`ClientRenderer`, LagGoggles `LagOverlayGui` | `POLYGON_STIPPLE`, `PIXEL_MODE`, `LIGHTING`, `FOG`, `ACCUM`, `STENCIL`, `VIEWPORT`, `TRANSFORM`, `HINT`, `EVAL` |
| `0x41040` `TEXTURE\|TRANSFORM\|LIGHTING` | SGCraft `BaseGLRenderTarget`/`DHDScreen`/`PowerScreen`, architecturecraft `RenderTargetGL` | `TRANSFORM`, `LIGHTING` |
| `0x40B71` | BetterQuesting `RenderUtils` | `POLYGON_STIPPLE`, `PIXEL_MODE`, `LIGHTING`, `VIEWPORT` |
| `0x40` `LIGHTING` | GregTech `RenderUtil.useLightMap`, `MetaTileEntityCentralMonitor` | `LIGHTING` |
| `0x80000` `SCISSOR` | MrTJPCore `ClipNode` | none (modelled) |
| `0x6000` `ENABLE\|TRANSFORM` | SGCraft `DHDScreen` | `TRANSFORM` |

The second half of the census is what the pushed blocks *change* inside their own region, because a group is only a
picture defect when a value behind it moves:

* **`glViewport` inside a pushed block**: Susy-Core's three space renderers (`glViewport(0, 0, displayWidth,
  displayHeight)` then `glPopAttrib`; they run in the space dimensions, not in the calibrated overworld scene),
  BetterQuesting's mask selects the group, and AkashicTome/graduatedcylinders call `glViewport` but restore it by hand
  (so they need the query, not the pop).
* **light state**: no mod jar calls `glLightfv`, `glLightModelfv`, `glMaterialfv`, `glColorMaterial` or `glColorMaterial`
  at all. The only light values a push protects are the two constant sets vanilla's `RenderHelper` re-applies, which is
  why `GL_LIGHTING_BIT` stays unmodelled with a measured reason instead of being next in a list.
* **fog**: only BiomesOPlenty references `glFog`, and no `0xFFFFF` pusher changes fog state inside its block.
* **transform**: SGCraft's and architecturecraft's masks select `GL_TRANSFORM_BIT`, but those classes call
  `glPushMatrix`/`glPopMatrix` for their own matrix work; the group's matrices are not written inside the pushed block.

## The fix

* `LegacyAttribSnapshot` carries the four `GL_VIEWPORT` components; `celtium$pushAttrib` reads them from the stub (the
  live record, not a copy of a copy), and `restoreAttrib` writes them back when the pushed mask selected
  `GL_VIEWPORT_BIT`.
* The restore goes through the backend's `viewport(...)` when one exists, because that method owns the group key's
  viewport term and the live sizes an off-contract offer is refused against; without a backend it writes the packed
  rectangle directly. The packed value is the one the capture reads per offer, so a restore that updated only the
  stub's fields would answer `glGetIntegerv(GL_VIEWPORT)` correctly and still draw under the popped block's rectangle.
* `recordViewportPacked` is now the one place both entries use: the transformed native's route (`celtium$viewport`) and
  the stub's own dispatch table (`SEM_VIEWPORT`). Before this, the dispatch entry wrote the fields and left the packed
  value at "never set", which the capture reads as a full-target offer.
* `GL_VIEWPORT_BIT` moved from `UNMODELLED_GROUPS` to `MODELLED_GROUPS`, so the census stops counting it.
* The font run key now sees the whole cull triple (`FontBatch.FontRunState.cullState()`) instead of the enable alone: a
  run that spans a `glCullFace` change used to be drawn under the later face.

## What the arm measures

The fixture's attribute arms now set a viewport inside the masked push (`17x19` at `11,13`), read it back with
`glGetIntegerv(GL_VIEWPORT)` before/inside/after, and set a different rectangle (`31x37` at `23,29`) inside the
`GL_ENABLE_BIT`-only push. Both are asserted in the same frame:

* `attribBefore.vw == attribAfter.vw` - the pop put the caller's rectangle back;
* `attribInside.vw == 11,13,17,19` and `attribOutsideAfter.vw == 23,29,31,37` - the two writes landed, and the push that
  did not select the group left its rectangle in force (GL's rule; the old "restore everything" stack would have put it
  back).

## The measurement

Leg `r33a-viewport` (AMD Radeon RX 6400, RADV NAVI24; renderer `7c955c4c8fc5447171feb3bcb0aaf8be86a7ab26df84722614cb74effb4460d1`,
fixture `f946d937d75f615f0929cc07ff2dd04fcaf9b5ad2a39cb6de82b1809ad5251b8` - both built from the tree this commit
contains; the rebuild after the last comment-only edits produced different archive hashes, and `javap -p -c` of
`LegacyAttribSnapshot` and `DisplayListLegacyScreen` compared equal between the two builds, i.e. only the line-number
tables moved; `plain`, 1280x720,
`guiScale3.txt`, validation ON, `-Dceltium.validation.screen=displaylist`, 300 s). The leg is `UNCALIBRATED_PRESENTED_IMAGE`
(exit 2) by construction, as in the round that added the cull arms: the named probe screen replaces the terrain picture the
scene gate judges, so the gate is not the measurement here.

The scene's own readback, in both dumped frames (`displaylist-f8-a`, `displaylist-f9-b`):

| key | before the push | inside the masked push | after the pop | after the `GL_ENABLE_BIT`-only push |
|---|---|---|---|---|
| `vw` | `0,0,1280,720` | **`11,13,17,19`** | **`0,0,1280,720`** | **`23,29,31,37`** (not restored - GL's rule) |
| `cm` | `1111` | `0000` | `1111` | `0000` (not restored - GL's rule) |
| `cf` | `1029` | `1028` | `1029` | `1029` |

`attribBefore == attribAfter` for every key including `vw`, and `attribOutsideAfter.vw` is the rectangle the second block
set - the pop that did not select the group left it in force. `attribMask=0x86908` is the arm's mask
(`ENABLE|COLOR_BUFFER|DEPTH_BUFFER|POLYGON|SCISSOR|VIEWPORT`).

The picture arms are unchanged from the previous round: `armD_green=3600 armE_green=0` (the colour-mask rule),
`armF_green=3600 armG_green=0` (the cull face rule), `armA_white=3600 armB_magenta=3600 texControl_magenta=3600
armC_outside=3600 armC_inside=0 triangle_cyan=340 direct_yellow=14400`. Checker: **PASSED**, all five positive controls
rejected (`baked-colour`, `compile-layout`, `compile-colour`, `attribute-mask`, `cull-face`).

Health: `celtium/vulkan-check passes=3273 skipped=0 errors=0`, `validationMessageCount=0`,
`validationAllowedCount=0`, `validationClean=true`, `gpuRequirementMet=true` (native device row `AMD Radeon RX 6400 (RADV NAVI24)`), zero `VUID` lines, zero `DEVICE_LOST`, no crash reports,
no Celtium exception.

## The calibrated scene, and what it says the next group is

Leg `r33b-world` (same stage and JAR, `world`, AMD RADV NAVI24, 1280x720, `guiScale3.txt`, 300 s) closes the round on the
real play scene rather than the probe: **PASSED** (exit 0, `problems=[]`, `fixtureStatus=PASSED`,
`sceneCalibrationKeyMatches=true` at pose `x=217.5 y=48.0 z=217.5 yaw=180 pitch=10`, floors enforced,
`presentedBlackFraction=0.007221`), `celtium/vulkan-check passes=37604 skipped=0 errors=0`,
`validationMessageCount=0`, `validationClean=true`, `gpuRequirementMet=true`, zero `VUID`, zero `DEVICE_LOST`, no crash
reports. The kept picture shows terrain, HUD, the minimap and entities as expected.

Its attribute census is the useful part for the next round, because it names the group that is actually pushed in this
scene:

```
celtium/attrib[masks=true pushes=1038 pops=1038 unmatchedPops=0 overflow=0 restoredGroups=0
               selectedUnrestoredGroups=1038 last=0x40/0x0 histogram=[0x40:1038]]
```

Every push in the calibrated world scene is GregTech's `0x40` (`GL_LIGHTING_BIT`) and **not one of them restores
anything** (`restoredGroups=0`), because the light parameters are not modelled as a restorable group. GT's
`RenderUtil.useLightMap` is exactly that caller: it pushes `GL_LIGHTING_BIT`, calls
`RenderHelper.disableStandardItemLighting()`, then `OpenGlHelper.setLightmapTextureCoords`, runs the caller's render, and
calls `RenderHelper.enableStandardItemLighting()` before the pop - the final call re-sets light 0/1 through the
modelview in force, and GL's pop would put the previous values back. This renderer's mirror keeps the last ones. The
`0xFFFFF` pushers the static scan found (Susy-Core's space renderers) do not run in this overworld scene, so their group
is a capability rather than a measured frequency.

That is the measured reason `GL_VIEWPORT_BIT` is this round and `GL_LIGHTING_BIT` is next: the viewport group is selected
by callers that run in space dimensions and by BetterQuesting, while the lighting group is selected >1000 times in the
calibrated scene and restores nothing today.

## What this does not claim

* The matrices (`GL_TRANSFORM_BIT`), light parameters (`GL_LIGHTING_BIT`), fog (`GL_FOG_BIT`) and the stencil group stay
  unmodelled. The census above is the reason each is deferred, and each is counted in `selectedUnrestoredGroups`.
* `glDepthRange` (part of `GL_VIEWPORT_BIT`) is a no-op in this renderer: the Vulkan viewport always spans 0..1 depth,
  and no mod in the pack calls `glDepthRange`.
* The space renderers' own rectangles were not compared against a Celeritas reference arm this round; the claim is the
  attribute stack's restore, measured by the scene's own query readback.
