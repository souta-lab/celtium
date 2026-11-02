# `GL_LIGHTING_BIT` is restored in part, and this scene never needed it (2026-11-03)

The previous round's calibrated world leg ended with a census that named the next target instead of leaving it to
intuition: `pushes=1038 pops=1038 restoredGroups=0 selectedUnrestoredGroups=1038 histogram=[0x40:1038]`. Every
attribute push in the calibrated scene is GregTech's `GL_LIGHTING_BIT`, and not one of them put anything back. This
round reads what that block actually does, restores the part of the group this renderer carries, and measures both
halves: the restore works, and in this scene it never had a value to move.

## What the block does (read from the class file, not assumed)

`javap -p -c gregtech/client/utils/RenderUtil.class` on `gregtech-1.12.2-2.8.10-beta.jar`:

```
public static void useLightMap(float, float, java.lang.Runnable);
   0: bipush 64                     // GL_LIGHTING_BIT
   2: invokestatic GL11.glPushAttrib:(I)V
   5: invokestatic RenderHelper.func_74518_a:()V          // disableStandardItemLighting
   8: getstatic    OpenGlHelper.lastBrightnessX:F
  17: getstatic    OpenGlHelper.field_77476_b:I
  22: invokestatic OpenGlHelper.func_77475_a:(IFF)V       // setLightmapTextureCoords
  30: invokeinterface Runnable.run:()V
  41: invokestatic OpenGlHelper.func_77475_a:(IFF)V       // coords back
  44: invokestatic RenderHelper.func_74519_b:()V          // enableStandardItemLighting
  47: invokestatic GL11.glPopAttrib:()V
```

So the block ends by re-establishing vanilla's standard item lighting, and lets the pop undo it. The earlier
193-jar scan's finding - "no mod in the pack calls `glLightfv`/`glLightModelfv`/`glMaterialfv`/`glColorMaterial`" -
was true and beside the point: the vanilla helper the block calls does, through `GlStateManager.glLight`, which the
mixin routes into the renderer's mirror. That is why the group had a live provider after all.

## The fix

* `LegacyAttribSnapshot` carries the four terms the mirror holds: light 0/1 eye-space directions, light 0/1 diffuse
  colours, `GL_LIGHT_MODEL_AMBIENT`, `GL_SHADE_MODEL`. `lightingCaptured` records whether they were really read (the
  mirror is the only authority; a push taken before a backend exists must not write GL's initial values over a mirror
  that has since started tracking).
* `VulkanGlState.restoreLightState(...)` writes them **without** the modelview multiply. `setLightPosition` folds the
  modelview in force at the call into the stored value - correct for a caller setting a light, wrong for a pop, whose
  snapshot already holds eye-space values and whose modelview is the pushed block's. It bumps `lightGeneration` once
  when anything moved, so an immediate group opened before the pop cannot be reused after it, and leaves the
  generation alone when the values already match (vanilla re-sets the same two lights on every
  `enableStandardItemLighting`, so a bump per call would split identical groups).
* `GL_LIGHTING_BIT` moved out of `UNMODELLED_GROUPS` into `MODELLED_GROUPS` **and** into a new `PARTIAL_GROUPS`. The
  group is restored in part: `GL_AMBIENT`/`GL_SPECULAR`/`GL_SPOT_*`/attenuation per light, the material parameters and
  `GL_COLOR_MATERIAL` do not exist in this payload, so they cannot be put back, and the census counts the group in
  `selectedPartialGroups` rather than claiming the whole group.
* The census gained `selectedPartialGroups=`, `lightingRestores=` and `lightingRestoresChanged=`.

## The measurement

### The mechanism, end to end (leg `r34a-displaylist`)

The fixture's attribute arm now includes `GL_LIGHTING_BIT` in its mask, writes light 0's position and diffuse and the
light-model ambient inside the block through the **raw** `glLightfv`/`glLightModelfv` route (a different path from the
`GlStateManager` mixin the game uses), and reads all three back with `glGetLightfv`/`glGetFloatv`. The `GL_ENABLE_BIT`-only
arm writes the same values again, so "the pop restored it" and "the pop left it" are two different readings of the same
frame. An outer `glPushAttrib(GL_LIGHTING_BIT)`/`glPopAttrib()` pair saves and restores the caller's own light, which is
also a second exercise of the group.

Leg `r34a-displaylist`: AMD RX 6400 (RADV NAVI24), plain, 1280x720, `guiScale3`, validation ON, 300 s, renderer
`20030e8e6ded1da7536f15c03bf6b6d42fa937f329a57da4bdab91fbd5dd9315`, fixture
`e66b6583b6cb9e819d9a49d4a71bb124ae12be008b2517dbd5850848cfbd8f4b`. The leg's verdict is
`UNCALIBRATED_PRESENTED_IMAGE` (exit 2) **by construction** - its picture is the probe screen - so the checker is the
measurement:

| reading | `lp` (light 0 position) | `ld` (light 0 diffuse) | `la` (light-model ambient) |
|---|---|---|---|
| `attribBefore` | `-0.24,-0.63,0.74` | `0.60,0.60,0.60` | `0.40,0.40,0.40` |
| `attribInside` | `0.37,0.11,-0.92` | `0.25,0.50,0.75` | `0.33,0.22,0.11` |
| `attribAfter` | `-0.24,-0.63,0.74` | `0.60,0.60,0.60` | `0.40,0.40,0.40` |
| `attribOutsideAfter` | `0.37,0.11,-0.92` | `0.25,0.50,0.75` | `0.33,0.22,0.11` |

`attribMask=0x86948` (the previous `0x86908` plus `0x40`). The before/after value is *not* the world-space
`0.2,1,-0.7` vanilla sets: it is that direction already folded through the modelview in force when vanilla set it,
which is exactly the eye-space value a driver reports and the reason the restore must not transform it again. The
inside value is the raw arm value because the GUI pass's modelview is identity at that point.

`display-list-legacy-check.py` **PASSED** with all five positive controls rejected (baked colour, compile layout,
compile colour, attribute mask, cull face), every pre-existing arm unchanged (`armD_green=3600 armE_green=0
armF_green=3600 armG_green=0 armA_white=3600 armB_magenta=3600 texControl_magenta=3600 armC_outside=3600
armC_inside=0 triangle_cyan=340 direct_yellow=14400`), `validationMessageCount=0 validationAllowedCount=0
validationClean=true gpuRequirementMet=true`, 0 `VUID`, 0 `DEVICE_LOST`, no crash reports.

### The group in the calibrated scene (leg `r34b-world`)

Leg `r34b-world` (same stage, world, `sceneCalibrationKeyMatches=true`, `sceneGateCalibrated=true`,
`sceneGateFloorsEnforced=true`, pose x=217.5 y=48 z=217.5 yaw=180 pitch=10, floors enforced): **PASSED** (exit 0,
`problems` [], `fixtureStatus=PASSED`, `presentedBlackFraction=0.006596`, `pictureScreenshotNonBlackFraction_world=0.98031`),
`celtium/vulkan-check passes=47573 skipped=0 errors=0`, validation 0, 0 `VUID`, 0 `DEVICE_LOST`. The kept picture was
inspected: terrain, HUD, Xorero minimap, entities and machines normal, no black regions.

Its attribute census:

```
pushes=1317 pops=1317 unmatchedPops=0 overflow=0 restoredGroups=1317 selectedUnrestoredGroups=0
selectedPartialGroups=1317 lightingRestores=1317 lightingRestoresChanged=0 last=0x40/0x40 histogram=[0x40:1317]
```

`restoredGroups` went from 0 to 1317 and `selectedUnrestoredGroups` from 1038 to 0, so the group the pack pushes is
now put back. **`lightingRestoresChanged=0`** is the honest second half: in this scene the restore never had to move a
value. The block's last call is `enableStandardItemLighting`, which re-sets exactly the two lights and the ambient that
were in force when the block was entered (the runnable restores the modelview before it runs), so the snapshot and the
live mirror agree at every pop. The fix is therefore a GL-semantics fix, not a picture fix for this pack path - and the
census says so instead of leaving a later round to guess.

## The last attribute group with a possible provider is closed by measurement

The same 193-jar scan, extended to `GL_TRANSFORM_BIT`: nine push sites select it - Commons0815's `EnergyBar` and
`ClientRenderer` (two sites), SGCraft's `BaseGLRenderTarget`, Susy-Core's `CelestialRenderer`, `RingFeatureRenderer`,
`SunGlowRenderer` and `PlanetSurfaceRenderer`, and architecturecraft's `RenderTargetGL`. Exactly one class writes a
matrix inside such a block, and both of its sites balance the write with the block's own
`glPushMatrix`/`glPopMatrix`:

```
ldc 1048575 ; glPushAttrib
glPushMatrix
glScaled(...)            // site 1: the GUI resolution scale
glTranslated(...)        // site 2: the camera offset
  loop: glPushMatrix ... glPopMatrix
glPopMatrix
glPopAttrib
```

The net matrix at the pop is the matrix at the push, so a pop that restored the matrix stack would write back the
values already in force. `GL_TRANSFORM_BIT` therefore has no live provider in this pack, and the attribute stack's
remaining gap is `GL_FOG_BIT` (BiomesOPlenty only) and `GL_STENCIL_BUFFER_BIT` (no stencil attachment) - both with
written reasons. The next round should not spend itself on attribute groups.

## What this does not claim

* The material and per-light ambient/specular/spot terms are **not** restored; the group is counted as partial.
* `GL_TRANSFORM_BIT` (SGCraft `DHDScreen` `0x6000`, SGCraft/architecturecraft `0x41040`) is still unmodelled: the
  matrix stacks are the backend's mirror and a `glPushAttrib`-only save of them is not implemented. Whether those
  blocks actually write matrices inside the push is not measured yet.
* `GL_FOG_BIT` (BiomesOPlenty only) and `GL_STENCIL_BUFFER_BIT` (no stencil attachment) stay unmodelled with their
  written reasons.
* No paired Celeritas FPS KPI and no Intel leg this round.

## Re-running

```sh
CELTIUM_INSTANCE_COHORT=$HOME/.local/share/PrismLauncher/instances/Supersymmetry \
CELTIUM_LEG_SEED_OPTIONS=$PWD/.celtium-work/options/guiScale3.txt \
scripts/gv-leg.sh <fresh> .gv-verify/jars/<stage> plain amd 300 \
  -Dceltium.validation.screen=displaylist -Dceltium.validation.screenshotAtTick=120
python3 scripts/cpu-contract/display-list-legacy-check.py --run .gv-verify/run-<fresh>

CELTIUM_LEG_SEED_OPTIONS=$PWD/.celtium-work/options/guiScale3.txt \
scripts/gv-leg.sh <fresh> .gv-verify/jars/<stage> world amd 300
```
