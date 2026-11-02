# A display list compiled out of `glBegin`/`glEnd` geometry is captured and replayed (2026-11-02)

**Symptom**: "モデル/カスタム描画の不可視" - some mods' custom rendering does not appear at all. This round measures and
fixes one concrete instance of that class, the one this pack actually contains.

## The code path, read from the mod's own class file

`gregtech-1.12.2-2.8.10-beta.jar`, `gregtech/common/ToolEventHandlers.drawRotationMarker` (`javap -c`, static method):

```
glNewList(rotationMarkerDisplayList, 4864)   // 4864 = GL_COMPILE
glBegin(GL_LINE_LOOP)                        // iconst_2
   121 x glVertex3d(...)                    // a ring, then a second inner ring, then three marker vertices
glEnd()
glEndList()
...
glCallList(rotationMarkerDisplayList)        // every frame, under glPushMatrix/glTranslated/glScaled
```

That is a display list compiled from immediate-mode geometry, drawn by `glCallList` at the caller's transform. Other
mods in the pack use the same route (`li/cil/oc/.../BufferRenderer`, `RobotRenderer`, `TextRenderCache`;
`com/brandon3055/aenetvistool/.../NetPartRenderer$FXHandler`).

## What the renderer did before

`LegacyImmediateCapture.begin(..., compilingList=true, ...)` refused the run:

```
CELTIUM_LEGACY_REFUSED mode=... reason=a display list is being compiled (list N)
```

The stub also does not record `glBegin`/`glVertex` into the list's command array (those calls arrive through the legacy
entry points, not the GL dispatch), so the list was published **empty**: every `glCallList` of it drew nothing. The
reported symptom and the code path agree, and the refusal was the only trace.

## The change

- `LegacyImmediateCapture` now collects a run inside a compile instead of refusing it, and hands it to a
  `CompiledRunSink` instead of the drawing sink. The mode gate still applies first, so a list cannot hold geometry its
  replay would have to refuse.
- `CompiledLegacyRun` copies the vertices out of the capture's reused buffer (a display list stores its data; the
  capture's buffer is overwritten by the next `glBegin`). It carries vertices, layout and primitive only: no matrix, no
  texture binding, no blend state - a list does not bake those in, and the recorded commands change them in order at
  replay.
- `CeltiumGlStub` inserts the run as an entry **in the list's own command array, in position**, so `glCallList` replays
  the recorded calls before and after it and the run draws under exactly the state GL would give it. The copy is
  registered as a list payload, so replacing or deleting the list frees it through the one lifecycle that already owns
  every other compile-time buffer. 64 MiB of live copies is the ceiling; past it a run is dropped and counted.
- Switch: `-Dceltium.glstub.legacyListCompile=false` restores the old refusal for A/B. Default on, because the old
  behaviour draws nothing.

New counters in the verification line:

```
celtium/legacyListRuns[enabled=true recorded=2/7 replayed=96/336 lists=2 bytes=112 droppedBytes=0 replayRefusals=0]
```

`recorded` counts runs filed with a list, `replayed` the ones a `glCallList` actually drew - GregTech's marker is
compiled once and called every frame, so the two numbers differ by design.

CPU tests: `LegacyImmediateCaptureTest` (a compiled run goes to the list and not the drawing sink, the whole-primitive
cut applies, a mode with no pipeline is refused before filing, a record refusal is counted, a compile with no record is
refused by name) and `CompiledLegacyRunTest` (the copy survives the capture buffer being reused; the size arithmetic is
`count * stride`).

## The scene that measures it

`-Dceltium.validation.screen=displaylist` (`DisplayListLegacyScreen`, fixture only). One frame contains:

| element | how it is drawn | what it proves |
|---|---|---|
| green square, 60x60 GUI units | `GL_COMPILE` list of 4 `GL_QUADS` vertices, compiled at the origin, colour set **outside** the list | the list was replayed, at the transform current at `glCallList`, with the compiled colour and not the call-time one |
| cyan triangle | `GL_COMPILE` list of `GL_LINE_LOOP` vertices - GregTech's primitive | the line pipeline receives a replayed list too |
| yellow square, 40x40 | direct `glBegin`/`glEnd`, same matrix, same state | control: if this is missing, the immediate path drew nothing and the list's absence is not attributable |
| dark gradient backdrop + green/cyan corner markers | `drawGradientRect` / `drawRect` | the panel is where the report says, and the frame clears the leg gate's distinct-colour smoke floor |

The square alternates between a slot at a quarter and three quarters of the panel height on consecutive frames, and the
screen asks for two consecutive presented dumps itself.

## Two defects in the probe, found and fixed rather than explained

1. **The first version could not attribute its own result.** Leg `r26a-displaylist` (JAR `ebcb9edd`, fixture `011d252f`)
   reported `recorded=2/7 replayed=106/371 replayRefusals=0`, and its picture held only the corner markers
   (`distinct=3`, 144 green + 132 cyan px). Without a same-frame control there was no way to tell "list replay failed"
   from "nothing in this scene drew". The direct-immediate control is the fix, and it answered on the next run.
2. **The quads were wound back-facing.** Leg `r26b-displaylist` (JAR `ebcb9edd`, fixture `374c0346`) measured 340 cyan
   line-loop pixels per frame in the loop's band and **0** green in either slot and **0** yellow in the control: the
   two quads were wound the opposite way to vanilla's `Gui.drawRect`, and the overlay's y-down ortho makes that a back
   face, which GL culls. That is the renderer doing the right thing; the scene was wrong. The winding now matches
   vanilla's.
   (The first version also compiled its runs with `GL_TEXTURE_2D` left on by the GUI, so the vertices were packed as
   `POSITION_TEX_COLOR` with no `glTexCoord` of their own and sampled whatever coordinate and texture were current.
   That is a real fidelity question of its own, but it was not the cause of the missing ink, and it is not claimed as
   one: the scene now disables texture around its geometry so the question it asks is only about the list.)

## The measurement

Leg `r26c-displaylist`: `plain`/terrain, AMD RX 6400 (RADV NAVI24), 1280x720, guiScale 3, validation ON,
seed options `guiScale3.txt`, renderer JAR `ebcb9edd298af5c8f26937ad72a31a244f2c5f23a137ec197160980285779a7d`,
fixture `1574abdfa8a191e1bc7a2f5cfe80f11a28e850fe7fa5cbd628feed23537c03ba`,
scene `-Dceltium.validation.screen=displaylist`, leg verdict `UNCALIBRATED_PRESENTED_IMAGE` (exit 2).

Layout from the report: `gui=427x240 panel=0,0,427,240 square=30 slots=60,180 triangle=341,120,24 direct=85,120,20
colour=0,255,0 frames=48 compiles=1 dumps=2`.

Checker (`scripts/cpu-contract/display-list-legacy-check.py`), ink counted per band:

| picture | slot A (upper) green | slot B (lower) green | red | line loop cyan | direct yellow |
|---|---|---|---|---|---|
| `displaylist-f40-a` (even frame) | **32400** | 0 | 0 | 340 | **14400** |
| `displaylist-f41-b` (odd frame) | 0 | **32400** | 0 | 340 | **14400** |
| positive control (f40 pasted into f41's empty slot) | 32220 | 32400 | 0 | - | - |

`32400 = (2*30*3)^2` is exactly the 60x60 GUI square at the picture's own scale, and `14400 = (40*3)^2` the direct
control's, so the numbers are the geometry's area, not a threshold pass. The square is **green** (0 red pixels anywhere
in either band) although the call sets red immediately before `glCallList` - GL compiles the colour. It is in the slot
the frame called it to, and the other slot is empty, so the transform is the call's and no frame was retained. The
positive control makes the same analysis report both slots carrying, so a clean result is not vacuous.

Picture: [display-list-legacy-2026-11-02.png](display-list-legacy-2026-11-02.png) - frame 40 (square upper) and frame
41 (square lower) side by side; yellow control and cyan triangle in both.

Run facts: `displayListDrawnFrames=48`, `displayListCompiles=1`, `displayListDumpCount=2` with both dumps
`completed=true` at presented frames 44 and 45; `presentedDistinctColours=78`, `presentedNonBackgroundFraction=0.9647`,
`presentedBlackFraction=0.0`; `celtium/vulkan-check passes=453 errors=0` at the last report; 0 VUID lines, 0 Celtium
exceptions, `validationMessageCount=0`, `validationClean=true`.

`sceneCalibrationKeyMatches=false` and `sceneGateFloorsEnforced=false` are the probe's shape, not a defect: opening a
named screen changes the scene key away from the calibrated terrain pose, so the run is `UNCALIBRATED_PRESENTED_IMAGE`
and the judgement is the checker's, not the scene gate's.

## Not measured / not claimed

- A list compiled with texturing enabled whose vertices carry no `glTexCoord`: the replay uses the layout the compile
  chose, while GL samples according to the state at `glCallList`. Unmeasured, and the scene sidesteps it.
- `GL_COMPILE_AND_EXECUTE`: a compiled run is filed and not drawn; the old behaviour drew it neither.
- Hierarchical lists (a `glCallList` recorded inside a compile) with legacy runs inside the child.
- GregTech in game with a wrench in hand: this is the same code path reproduced in the fixture, not a capture of GT's
  own frame.
- Intel.
