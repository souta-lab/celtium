# glPushAttrib/glPopAttrib: the mask decides what comes back (2026-11-02, round 10)

Owner: **custom rendering fidelity** (a mod's save/restore around its own draw). Unit: "the state a popped block
leaves behind is the state GL leaves behind".

## The rule, settled against real GL

GL's attribute stack is mask-selective: `glPushAttrib(mask)` saves the state groups the mask names and `glPopAttrib()`
puts exactly those back. The groups are not interchangeable - `GL_ENABLE_BIT` saves capability bits, while
`GL_COLOR_BUFFER_BIT` saves the blend factors and equation, the colour write mask and the alpha test's comparison;
`GL_DEPTH_BUFFER_BIT` saves the depth write mask and comparison; `GL_POLYGON_BIT` the cull mode and polygon offset;
`GL_SCISSOR_BIT` the box; `GL_LIST_BIT` the list base; `GL_TEXTURE_BIT` the bound texture; `GL_CURRENT_BIT` the colour.

Two things followed from that for this renderer:

* the old stack **ignored the mask** and put back everything it tracked, which is a superset of GL. Vanilla's own
  `GlStateManager.pushAttrib()` uses mask `8256` = `GL_ENABLE_BIT|GL_LIGHTING_BIT`, so the pop also restored the
  current colour, the line width and the point size that the mask never covered;
* it tracked only capabilities, the colour and the two sizes. The blend factors, the colour write mask, the alpha test,
  the depth write mask and comparison, the cull mode, the scissor box, the polygon offset, the bound texture and
  `glListBase` were **not saved at all** - so a mod that wrote them inside a push leaked them past the pop into the next
  vanilla pass, and a mod that read the state back with `glGet*` to save it itself was reading a mirror that only some
  queries answered.

## The change

`LegacyAttribSnapshot` (new, GL/Vulkan-independent) holds one push: the mask, the capability bits and every value
listed above. `CeltiumGlStub.celtium$pushAttrib(mask)` fills it from the **live** authority for each group (the raster
mirror for blending/mask/culling/scissor/offset, the backend's state mirror for the depth and alpha comparisons, the
stub for the colour, the sizes, the list base and the texture binding), the stack is bounded (64 entries, overflow
counted), and `celtium$popAttrib()` restores **only the groups the pushed mask selected** (`restores(int group)`), in
GL's group order.

`-Dceltium.glstub.attribMasks=false` restores every modelled group whatever the mask said - the old behaviour, kept as
the A/B arm for this rule rather than as a supported mode.

Two supporting fixes were needed to make the depth and alpha groups restorable at all:

* `celtium$noteDepthMask/noteDepthFunc/noteAlphaFunc` record the values on the stub as well, and the raw and managed
  routes (`CeltiumGllessHint.glDepthMask/glDepthFunc/glAlphaFunc`, `GlStateManagerMixin`) call them. The backend mirror
  remains the read authority while one exists; the record is what a push that outlives a backend swap restores, and
  what the queries answer with no backend;
* `glGetFloatv(GL_DEPTH_WRITEMASK)` had no case and answered the generic zero, and `GL_DEPTH_WRITEMASK`'s initial
  value in the enable record was false instead of GL's true. Both are fixed: a save/restore that reads the depth mask
  through the float query now gets the depth mask.

`celtium/attrib[masks= pushes= pops= unmatchedPops= overflow= restoredGroups= selectedUnrestoredGroups= last=/ 
histogram=[mask:count ...]]` is the census, in the same verify line as the display-list one.

## The measurement

One paired set, one fixture JAR (`25df5c89…`), one scene (`-Dceltium.validation.screen=displaylist`), AMD RX 6400
(RADV NAVI24 for Celtium, radeonsi navi24/ACO for the reference), 1280x720, guiScale3, 300 s budget.

The scene gained **arm D** and **arm E**, two 20x20 quads on the bottom row that differ only in the mask they push:

* **arm D** pushes `GL_ENABLE_BIT|GL_COLOR_BUFFER_BIT|GL_DEPTH_BUFFER_BIT|GL_POLYGON_BIT|GL_SCISSOR_BIT` (`0x86108`),
  writes a colour mask that keeps no channel, an off depth mask, a different blend func, no depth test, the front face,
  a small scissor box and a polygon offset inside the push, reads all of it back, pops, reads it back again, and then
  draws a green quad. GL restores the colour mask, so the quad is visible.
* **arm E** pushes `GL_ENABLE_BIT` only, writes the same colour mask, pops, reads it back, and draws the same green
  quad. GL does **not** restore the colour mask (it is `GL_COLOR_BUFFER_BIT` state), so the quad must be absent. The
  arm restores the mask by hand afterwards - the caller's own restore, which is what a mod has to do too, and what
  keeps the rest of the frame whole.

| reading | Celtium (`r30b-attribmasks`, renderer `a7d100dc…`) | Celeritas 2.5.0-dev (`r30c-attribmasks-ref`, `567dcf8a…`) |
|---|---|---|
| `attribBefore` | `cm=1111 dm=1 bs=770 bd=771 dt=1 cf=1 sb=0,0,1280,720 po=0.0` | `cm=1111 dm=1 bs=770 bd=771 dt=1 cf=1029 sb=0,0,1280,720 po=0.0` |
| `attribInside` | `cm=0000 dm=0 bs=1 bd=0 dt=0 cf=1028 sb=1,2,3,4 po=7.0` | identical |
| `attribAfter` | identical to `attribBefore` (every selected group restored) | identical |
| `attribOutsideAfter` | `cm=0000`, everything else as `attribBefore` | identical |
| `armD_green` / `armE_green` | 3600 / 0 (both dumps) | 3600 / 0 (screenshot) |
| the four existing controls | all rejected | all rejected |
| health | `validationMessageCount=0`, 0 VUID, `vulkan-check passes=1728 errors=0`, 0 device lost, `attrib[pushes=172 pops=172 unmatchedPops=0 overflow=0 restoredGroups=336 selectedUnrestoredGroups=60]` | — (GL) |

The reference answers the same questions the same way, which is what makes the scene's expectation a measurement
rather than an assertion `([image](attribute-stack-mask-2026-11-02.png): left Celtium presented dump, right Celeritas
screenshot, bottom the four zooms - arm D visible on both, arm E absent on both)`.

The census also says what the pack actually pushes: `0x86108` and `0x2000` are this scene's arms, and `0x40`
(`GL_LIGHTING_BIT`) appears 60 times from the game/mod code - a group this renderer does not model, counted as
`selectedUnrestoredGroups=60` rather than silently dropped.

## Not claimed

* `GL_TRANSFORM_BIT` (the matrix stack and matrix mode) is not restorable through this stack: the matrices live in the
  backend's mirror, and `glPushMatrix`/`glPopMatrix` - which are routed - are the calls mods use for them. A caller that
  saves the matrix stack with `glPushAttrib` alone gets it back unchanged; the census counts it.
* `GL_LIGHTING_BIT`, `GL_FOG_BIT`, `GL_STENCIL_BUFFER_BIT`, `GL_VIEWPORT_BIT` and the hint/eval groups are likewise not
  modelled. Vanilla's mask `8256` selects `GL_LIGHTING_BIT`, so this is a real gap for the item-frame and totem paths,
  not a hypothetical one.
* The mask rule was measured with one fixture that pushes the two masks on purpose; how often the *pack* pushes
  `GL_ALL_ATTRIB_BITS` and depends on the groups this round added is not measured.
* Intel was not run.

## A new trap this leg produced

`glGetBooleanv(GL_COLOR_WRITEMASK)` answered **zero** in the game run while `glGetFloatv(GL_COLOR_WRITEMASK)` answered
`1,1,1,1` in the same frame, and the pipeline's own `colorMaskHistogram` said `0xf` for every group. The in-process
dispatch (`CeltiumGlStub`'s table, no context) answers the boolean form correctly, so the difference is in the
in-run route, not in the query implementation. Real GL answers both widths (`cmB=1111` in the reference), so this is a
Celtium-only hole: a mod that saves state with the boolean query reads zeros and "restores" the wrong state. The
fixture now reports both widths side by side (`cm=` from `glGetFloatv`, `cmB=` from `glGetBooleanv`) and the checker
judges the float form; the boolean route is the next round's first instrument.

## Re-running

```sh
JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64 scripts/build.sh :celtium-vulkan:test :forge122:packageJar :forge122:validationRemapJar
# stage = the packaged renderer + the remapped fixture
CELTIUM_INSTANCE_COHORT=$HOME/.local/share/PrismLauncher/instances/Supersymmetry \
CELTIUM_LEG_SEED_OPTIONS=$PWD/.celtium-work/options/guiScale3.txt \
  scripts/gv-leg.sh <fresh> .gv-verify/jars/<stage> plain amd 300 \
    -Dceltium.validation.screen=displaylist -Dceltium.validation.screenshotAtTick=120
python3 scripts/cpu-contract/display-list-legacy-check.py --run .gv-verify/run-<fresh>
# the same scene under real GL
CELTIUM_LEG_REFERENCE_RENDERER=$HOME/ダウンロード/01-celeritas-forge-mc12.2-2.5.0-dev.jar \
CELTIUM_INSTANCE_COHORT=$HOME/.local/share/PrismLauncher/instances/Supersymmetry \
CELTIUM_LEG_SEED_OPTIONS=$PWD/.celtium-work/options/guiScale3.txt \
  scripts/gv-leg.sh <fresh-ref> .gv-verify/jars/<stage> plain amd 300 \
    -Dceltium.validation.screen=displaylist -Dceltium.validation.screenshotAtTick=120
python3 scripts/cpu-contract/display-list-legacy-check.py \
  --report .gv-verify/run-<fresh-ref>/logs/celtium-validation.json \
  --picture .gv-verify/run-<fresh-ref>/screenshots/celtium-validation.png
```
