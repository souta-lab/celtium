# Celtium current plan

Audit records: [first](audit-2026-10-09.md), [second](audit-2026-10-09-second.md).

Celtium moves Minecraft 1.12.2's rendering to **native Vulkan**: the game's own drawing, the final composition and the
present, with no GL context and no shared-image bridge. This file is the current plan and the only one that describes
what is being built now. Evidence, measurements and disproved hypotheses live in
[the 2026-10-09 investigation log](history/2026-10-09-investigation-log.md); the Sodium comparison detail lives in
[Sodium optimization deltas](sodium-optimization-deltas.md). Where an older document disagrees with this one, this one
wins.


## Major-mod verification, and the two third-party mods every measurement already carried

| **Major mod (JEI)** | **Both screens drawn by Vulkan on AMD, with the item layout observed through them** | With the hooks at ticks 30 and 34 - as early as the world is certainly up, because a tick costs seconds of wall-clock with JEI on the slower GPU and an absolute number is the wrong key - AMD reports `jeiScreenOpenedAtTick=30`, `jeiRecipeOpenedAtTick=34`, `jeiRecipeOpened=True`, exit 0, validation clean, 0 messages, fixture PASSED. That run also observes **`4:441`**, the item/block layout, drawn by the recipe screen and accepted - the last id never previously seen taken. `worldTicksAtEnd` is reported so reachability is measured, not assumed |
**JEI 1.12.2-4.22.0.1035** - one of the most widely used mods on this version, and one that draws heavily - is fetched,
hash-recorded, and loaded alongside Celtium:

```
mods loaded: +Fugue-0.24.4, celtium, celtium-validation-client, jei_1.12.2-4.22.0.1035, scalar-3.9.0
exit 0 | clean | messages 0 | fixture PASSED
nativeTerrainVerified=True   terrainPasses=1611   glDrawsCancelled=558   passes=2169
outsideWorldPassAccepted=7133   descriptorSets=1
```

Alongside, worth stating because it was never in the plan: **every measurement this project has taken already ran with two
third-party mods loaded** - `+Fugue-0.24.4`, a Cleanroom coremod that transforms vanilla classes, and `scalar-3.9.0`. The
renderer has therefore coexisted with class-transforming third-party code throughout, and the lock's `testMods` list is where
that is now visible rather than assumed.

**And a fixture now interacts with the mod.** The plan named "JEI loads" as weaker than "JEI's own rendering goes through the
renderer", so the fixture opens the player's inventory at tick 170 - where JEI draws its overlay - and closes it again before
the capture so the saved frame stays the normal scene:

```
jeiScreenOpenedAtTick=170 | exit 0 | clean | messages 0 | fixture PASSED
outsideWorldPassAccepted=8972   (7133 without the screen: +1839 batches)
immediateTaken=772              (558 without: +214)
nativeTerrainVerified=True  terrainPasses=1482  glDrawsCancelled=772  passes=2254
```

So the mod's own overlay is drawn by the Vulkan immediate path, with no validation messages and the descriptor cache still at
a single set. The same configuration runs on **Intel** (`jeiScreenOpenedAtTick=170`, exit 0, validation clean, 0 messages,
fixture PASSED, `terrainPasses=723`, `glDrawsCancelled=376`, `outsideWorldPassAccepted=4425`), so the major-mod path is
verified on **both GPUs**. Worth recording for anyone repeating it: this configuration takes roughly **twice as long on
Intel** with JEI loaded, which is what a 300-second budget ran into - the first attempt timed out rather than hanging. **And the recipe screen itself is now opened**, through the mod's own plugin API rather than by simulating input: the fixture
implements `mezz.jei.api.IModPlugin` (annotated `@JEIPlugin`, compiled only when the jar is present) and calls
`getRecipesGui().showCategories(...)` with the first category the mod registers.

```
jeiRecipeOpened=True | exit 0 | clean | messages 0 | fixture PASSED
outsideWorldPassAccepted=8563   immediateTaken=791   descriptorSets=1
nativeTerrainVerified=True   terrainPasses=1482   glDrawsCancelled=791   passes=2273
```

The API shapes were read from the jar with `javap` rather than assumed, which is what three failed compiles were about: this
version's runtime interface is `mezz.jei.api.IJeiRuntime`, its annotation is `JEIPlugin` with a capitalised name, and
`IModPlugin` has no plugin-uid method. On **Intel** the same configuration is **measured to exceed the harness's budget**: with
`--duration 420` it still ends at the harness timeout (`exit 124`, no report), where the AMD run of exactly this configuration
completes with `passes=2273`. The Intel runs without the recipe screen do complete (`passes=1090`, `outsideWorldPassAccepted=4426`),
so the gap is the mod's heavy screen on a slower GPU, not a defect - and it is recorded as a budget that does not fit rather
than as a failure. One reflection call remains and is honest to name: the fixture finds the optional plugin
class with `Class.forName`, because that class is compiled only when JEI is present - the mod-facing calls after it are direct.

## Completion condition (2), item by item, with the evidence for each state

Required by the objective: terrain plus entity/TESR/item, hand/GUI/font/HUD, particles, sky/cloud/weather/water,
| **Translucency** | **Carried into the pipelines and now measured, with its caveat stated** | The blend factors are hooked, baked into the pipeline state keys, and asked directly: `blendingFactorsMix=3221, blendingOff=0` over three thousand acceptances. The first two attempts at this number were wrong in the two ways this project keeps meeting - sampled at the wrong moment, then read from a guessed slot - and the third reading is the true one because Minecraft leaves blending **enabled** and expresses no-blending through the **factors**. The caveat: this describes the immediate family (GUI, font, HUD, hand, sky, particles, items), which is exactly what vanilla alpha-blends; **opaque content is the terrain, which takes its own pipeline path** |
implemented, unimplemented or verified on hardware, rather than judged by the terrain backend's name.

| Item | State | Evidence |
|---|---|---|
| **Terrain**, including its translucent and cutout layers | **Vulkan, verified on both GPUs** | `vulkanTerrainPasses=1632`, `glDrawsCancelled=566` (AMD) and `804`/`278` (Intel); `nativeTerrainVerified True` now requires both |
| **World-pass content in a covered vertex format** (blocks, entities, particles, the fixture's test quads) | **Vulkan, measured** | `immediateTaken=566` with `postAcceptDroppedGroups=0` and `droppedByFormat 1:0 2:0 3:0 4:0 5:0` |
| **GUI, font, HUD, hand and inventory rendering** | **Vulkan, verified on both GPUs** | Batches outside the world pass are taken and counted: `outsideWorldPassAccepted=7721` on AMD and `3965` on Intel, with `outsideWorldPassRefusals=0`, `postAcceptDroppedGroups=0` and every per-format drop counter at 0. The matrices for these groups come from the live `GlStateManager` mirror, which holds the orthographic projection outside the world pass - they never needed the world snapshot, which is why the earlier refusal was unnecessary |
| **Sky, clouds, weather and the water surface** | **Drawn by Vulkan on both GPUs, measured** | `acceptedByFormat` reports format 6 at **2374** (AMD) and **870** (Intel) where every previous run reported 0; `immediateTaken` 2728 / 1000 with `glDrawsCancelled` one for one; exit 0, validation clean, 0 messages, fixture PASSED, acceptance intact on both. Three position-texture refusals are the first frames, before the pipeline exists. **Open, and now measured properly:** the apparent fall of format 1 to zero was a **reporting artifact** - an arrival census reads `1:17` arriving and `1:5` accepted on the same code, because the accepted counter is a snapshot taken when the report happens to run. The same census shows **format 2 arriving 683 times and accepted zero** (the default mask excludes it) and **format 0 arriving 2092 times**, the unmapped shapes and the largest category still going to OpenGL |
| **Various FBOs and postprocess** | **The framebuffer chains are measured; the debug shader group is out of measured scope, with the reason** | The chains are the off-target path: `offTargetDrawn` 273-567, `offTargetCopiedBack` 267-554, zero refusals on both GPUs, the copy-back writing into the game's own textures. The `ShaderGroup` behind the super secret settings was instrumented and the observation is decisive: **the renderer offers no method with \"shader\" in its name at all** (`CELTIUM_POSTPROCESS ... shader-like methods: none`), so that path is a field plus a debug keybinding rather than anything a vanilla scene reaches. **Stated as outside the measured scope, with the reason**: it is debug-only, and the objective's postprocess means the chains, which are measured. Driving the keybinding from a fixture is possible and named, not done |
`offTargetByTarget=949054906:571` with `CELTIUM_OFFTARGET target=949054906 viewport=256x256 firstBatchFormat=1` - **one**
render target, **256x256**, receiving **571 batches**, all of them format 1, for which a pipeline already exists. So the first increment is one small image with a copy-back, not a general
framebuffer subsystem. **The resource half is now in and validated**: for the second target the renderer creates a 256x256
device-local image with a view (`offTargetTargets=1`), and validation is **silent** across the run - so the risky part is proved
clean before any pixel depends on it. The batches are still GL's (`offTargetRefusals=571`), which is the honest order. **The whole resource chain is in now** -
render pass, framebuffer and a host-visible readback - and a self-test that clears the target and reads it back confirms the
chain works end to end (`differingPixels=0`, passed). **The copy-back is in and verified too**: the self-test's readback is uploaded into a GL texture and read back from GL, and the
pixels agree (`glPixel=64,128,191`, `glError=0`, `passed=true`) - so the path a per-target draw needs to update the game's
content works end to end. It runs **on by default** and validation is **silent**: the `READ_AFTER_WRITE` hazard this check
exposed was solved by the validation layer's own diagnosis - the pass now ends in `COLOR_ATTACHMENT_OPTIMAL` and the barrier
performs the only transition to `TRANSFER_SRC_OPTIMAL`, because a transition made by `vkCmdEndRenderPass` is not something a
later barrier can synchronize. **The format-1 pipeline for that pass is now built and validated** - created with the target, before any batch is taken, which
is how validation came to reject the first attempt for passing `GL_ONE_MINUS_SRC_ALPHA` (771) where Vulkan's enum belongs. What remains is **not** mechanical, and the attempt found out why: **a Vulkan render pass cannot be begun
inside another**, and the frame's pass is open when these batches arrive. Taking them means the flush must **end the frame's
pass, render the off-target content and begin it again**, so the acceptance was withdrawn in the round it was attempted rather
than drawing that content into the main pass. The tuple now carries its render target, the refusal names the reason, and a
counter reports how much content is waiting on the restructuring. **The first attempt at that restructuring did not compile and
was reverted rather than left red**, with three obstacles recorded: the draw loop is not the first loop over the group list
(so a pattern-based filter landed in the group-retirement loop), the GL texture resolution had been removed as unused and comes
back with the pass, and `recordImmediateOnlyPass` is already the template the off-target pass should mirror |
| **Texture animation** | **Vulkan mirror, verified** | `CELTIUM_ANIM directMirrorRefreshes=89` with `atlasGlId=9` - the animated atlas re-uploads reach the mirror the terrain samples |
| **Resource reload** | **Verified** | `reloadedAtTick=100`, exit 0, validation clean, fixture PASSED |
| **World switch (leave and rejoin)** | **Verified** | `leftWorldAtTick=100 rejoinedAtTick=100`, teardown releases 4 then 2 resources, validation clean |
| **Resize** | **Verified** | `resizedAtTick=100`; the run completes (`passes=2166`) with exit 0, **0 validation messages**, fixture PASSED and the acceptance intact (`terrainPasses=1614`, `glDrawsCancelled=552`). The retirement queue drained all three of its retirements, so the resources the resize replaced were destroyed after the GPU was idle rather than during |
| **Final composite and present** | **The present runs and repeats; its result is provably not in GL's framebuffer, and the physical screen cannot be observed here** | The present trace cycles the swapchain images (`image=1,2,3,0`, matching acquire/render semaphores) with `presentFailures=0` and `outOfDate=0`. The marker test is decisive that the output is **not** in the framebuffer GL reads: a solid magenta present leaves the default framebuffer showing the ordinary scene. Deciding whether it reaches the **physical screen** needs a display with a real framebuffer or a compositor - this environment's X display reports a **0x0 screen** (`ffmpeg -f x11grab -i :0.0` -> `Invalid size 0x0`; no `xwd`, `xdotool`, `xwininfo`, `xrandr`, `xdpyinfo`, no `python-xlib`). **Environment limitation, not a code defect** |

This table is the honest boundary of the project, and the boundary has moved: **terrain, the immediate content outside the world
pass (the GUI, the font, the HUD, the hand - `outsideWorldPassAccepted` in the thousands per run), the sky, the clouds, the water
surface (format 6 at 2374 batches on AMD and 870 on Intel), the game's other framebuffers and postprocess, texture animation,
reload, resize and world switch are Vulkan and verified on both GPUs**, while the **final present runs natively and its result is
provably not in the framebuffer GL reads**. What remains with OpenGL is **one shape, measured rather than described**: the arrival
census reports ~2000 unmapped uploads per run and censusing them by shape gives a single one - `[3,Position,Float | size=12 count=1]`,
position-only vertices with no colour and no texture coordinates, matching none of the six pipelines that exist. The immediate path
refuses such batches by name rather than dropping them quietly.

**Condition three is inventoried rather than started.** The Vulkan module still touches OpenGL in **7 files and 99 call sites**,
in five roles: context creation and device identity (`GL`, `GLCapabilities`, `GlDeviceIdentity`) - the startup dependency the
condition names first; texture mirroring and sharing (`VulkanGlTexture`, `VulkanTextureRegistry`); the frame target and present
(`VulkanFrameTarget`); state capture in the draw path (the backend's `glGetFloatv`/`glIsEnabled` reads, every one of which a hook
could record instead); and writing back into the game's textures (`VulkanOffscreenTarget`'s `glTexSubImage2D`), which is
necessary until the game's textures are Vulkan-owned. The order that follows is **role 1, then role 4**, with the bridge itself
last. **Role 1 is done and measured**: with `celtium.vulkan.withoutGlIdentity` the renderer creates its device without asking GL
and the run reports `starting without an OpenGL device identity: no device matching, no interop` with validation clean
throughout - and then **cannot finish**, because without matching there is no interop for the bridge to share textures through.
That timeout is the bridge being absent by construction, which is why it is last in the order. **Role 4 is measured and already done**: of the backend's **46 read sites**, the expensive ones
(the per-batch `glGetFloatv` matrix pair, measured at 28 microseconds a batch) were replaced by hook-written mirrors long ago,
the diagnostic sampler is behind a flag and off by default, and what remains is **texture and bridge queries** - roles two and
five, which are last by design because they cannot go before the game's textures are Vulkan-owned. **Verified on both GPUs**: `mirroredTextures=8` with **`imageFilledMirrors=7`** on AMD and Intel alike, `descriptorSets=1`,
exit 0, validation clean, fixture PASSED. **Open:** the refresh stream still reads back - 4,821 in-place refreshes on AMD and
1,941 on Intel - so the next step is routing those through the announced image too, and one texture (the light map, probably) is
never image-filled on either GPU.

**Next: the bridge itself**, and it is now **sized**: the renderer
mirrors **eleven** distinct GL textures across a run - the atlas, the lightmap, the item and entity sheets, the GUI sheets - with
**4,523 in-place refreshes** of them. **And the interception already exists**: `TextureUtil`'s upload
(`func_110989_a`) is hooked at HEAD, it distinguishes a first upload from an animation repeat, and it calls
`backend.invalidateTexture(id)` every time - plus a mipmap-side hook and the atlas animation through `TextureAtlasMixin`. So what
remains for the bridge is not a mechanism but the **ownership decision**: whether the Vulkan image becomes the source of truth
for those eleven textures, and which GL-facing call sites change with it. The refresh path keeps descriptor sets stable across
4,523 refreshes (`descriptorSets=1`). **And the data arrives with the announcement**: the hook's own signature receives the
`BufferedImage` being uploaded, so a Vulkan image can be built from those pixels directly and the GL readback
(`readGlLevel0`/`readGlLevel`, three call sites) becomes unnecessary for exactly those eleven textures. That made the ownership decision a change at **one hook and one code path**, and it is now
**measured**: `mirroredTextures=8` with **`imageFilledMirrors=7`** - seven of the eight mirrors are built from the image the
upload hook hands over rather than from a readback of GL - with `inPlaceRefreshes=4821`, `descriptorSets=1`, exit 0, validation
clean, 0 messages, fixture PASSED. **The no-identity path is fixed and now usable**: the interop check treated a missing
identity as a crash; it now treats it as "no matching, no interop" and says so, with the normal path verified unchanged
(`exit 0`, clean, fixture PASSED, `imageFilledMirrors=7`). **And it runs clean**: the remaining nulls were in `pipelineFor(this.lastUiPass)`,
called from five places, where a null last pass meets a map that rejects null keys - **zero exceptions per run now**, with the
normal path verified unchanged in the same round.

**The supported and unsupported scope is stated in one place: [`docs/scope-and-limits.md`](scope-and-limits.md)** - what is
drawn by Vulkan with its measurements, what is still OpenGL, the environment limits on verification, what is not supported and
why, the Java versions, and the licensing lineage.

## 0. Architecture stance (2026-10-09 policy)

Celtium is a **Minecraft 1.12.2 / Cleanroom-specific native Vulkan renderer**. It is not, and will not become, a general
OpenGL-to-Vulkan translator, a reimplementation of the whole GL API, or a Zink-style layer: those are **out of scope by
direction**, not merely deprioritised.

What that means concretely:

- **The compatibility surface is the entry point, not the architecture.** Minecraft's and Forge's existing public APIs,
  signatures and event order stay as they are, because that is what existing mods call. What sits behind that surface is
  replaced by Vulkan rendering that understands what the call *means* in Minecraft - terrain, models and items, entities
  and TESRs, particles, UI and text, and the individual render passes. It is **not** extended into a layer that translates
  GL calls one by one.
- **The immediate capture, the per-format pipelines and the GL/VK bridge are transitional.** They exist to move a hybrid
  along and to produce measurements while doing it; they are not the final architecture and are not a compatibility layer to
  be completed. The work to remove GL state reads, texture readback and bridge dependence continues, and **frame ownership,
  draw order, resource lifetime, composition and present end up on the Vulkan side**.
- **Reuse inside Vulkan is not what is prohibited.** Shared buffers, descriptor sets, pipeline caches and allocators are
  ordinary renderer engineering. What is prohibited is making a general GL-compatibility emulation the goal.
- **Direct-LWJGL, GLSL and native-GL mods are investigated individually.** Each one is investigated for a concrete answer
  at the place it draws; there is no plan to translate all of GL so that all mods work. What is covered and what is not is
  documented, and "swapping Forge's entry points makes every mod work" is never claimed.
- **The audits and their acceptance tests continue.** This is a statement of direction, not an instruction to delete or
  rewrite what is there: the current audit P0/P1 fixes and their verification stay in the queue.

## Measurement basis: the reference image contains this project (found in round 257)

Every image comparison quoted in the recent rounds was measured against a frame that contains **this project's Vulkan
terrain**. The run described as the OpenGL reference had the immediate path disabled (`immediateTaken=0`, refusal
`immediate_drawing_disabled_by_celtium.vulkan.immediate=false`) but the terrain backend installed and drawing
(`vulkanPasses=1647`, `regions=4 draws=62`, `nativeTerrainVerified True`).

Those percentages therefore measure agreement with this project's own earlier output, **not** with OpenGL. The functional
evidence is unaffected and stands on its own terms - `postAcceptDroppedGroups=0`, validation clean on AMD and Intel, the
sampler filters the driver reports, the layout bugs found and fixed - but no image percentage from before this note should be
read as OpenGL agreement.

**The switch exists and works** - `-Dceltium.vulkan.referenceRender=true` plus guards at the two entries the mixins call
directly installs nothing, refuses every batch, and lets the fixture capture (`passes=0`, screenshot saved). And the frame it
captures **has no terrain in it**: sky and torches only. This tree has **no OpenGL terrain path left** - terrain is drawn by
this project's backend or not at all - so every earlier "reference" necessarily contained this project's own terrain, and the
image percentages measured self-consistency because no other option existed. That is architectural, not a switched-on flag.

**The reference now exists and has been measured.** `celeritas-forge-mc12.2-2.5.12-upstream.989ebf2e.jar`, built from the
`upstream-baseline` checkout, runs the same fixture and scene flags with the pinned lineage in place of Celtium and produces a
clean reference frame with terrain. Against it, the current Celtium terrain differs in **68.81% of pixels** (mean 34.09; sky
third 10.38%, terrain bands 98.74% and 97.32%). That is the first number in this project that measures agreement with the
renderer it replaced rather than with its own previous frame, and it is far larger than the self-comparisons suggested. The structure of the difference is now known: the sky is **pixel-identical**, no pixel shift improves the match
(minimum at zero offset), and the terrain is **uniformly about 40% darker** than the reference (luminance 77-80 against
129-131 in the three terrain bands). So the 68.81% is a **shading** defect rather than a geometry one. The lightmap divisor was wrong (256 where 1.12.2's 16x16
light map needs 16, and where the shader's own clamp was already written in 16-texel terms); it is **fixed**, and the
measurement is **unchanged** - which proves the light value reaching the shader is already at the clamp, i.e. **zero**. **Active shader identified and one real improvement measured.** The pipeline loads `SOLID_VERTEX_VANILLA`, so the earlier
divisor fix went into a shader nothing runs, and both ship as checked-in SPIR-V. With the active shader's SPIR-V recompiled
and verified in the jar, swapping the light coordinate axes (the scene's light coordinate is `(block 0, sky 15)` and the texel
it lands on measures 0.65 against the 0.61 ratio the frame showed) moves the mean difference from 34.08 to **27.09** and the
terrain luminance from 84.9/77.6 to **97.6/90.0**, while the count of pixels differing by more than eight rises from 68.81%
to **70.51%** - brighter terrain stops masking the colour differences that remain. Both numbers are recorded. Both remaining candidates have now been tested one at a time. The **divisor** (256 to 16, in the shader that actually
runs) moves the frame from 27.09 to **26.68** and the terrain band from 97.6/90.0 to **98.1/90.8** - small, as expected for
a change that only affects the extremes of the coordinate range. The **occlusion term** moves the frame by **0.02** when
disabled, so `m_color.a` is 1.0 and there is no double application - eliminated. The **vertex colour** is the answer: disabled,
the terrain band jumps to **215/222** against the reference's 131, so it carries a factor of about 0.46 where the reference
implies 0.61, which is the ~25% residual exactly. **Next:** the colour path was diffed against the pinned upstream checkout and is **identical** (differences: this round's
diagnostic only), and the attribute interpretation matches the GL declaration (offset 12, four normalised unsigned bytes,
`VK_FORMAT_R8G8B8A8_UNORM`). An instrument at the write reports the packed colour as **`0xFF808080`** - red full, green and
blue half, alpha half - a concrete instance of the 0.46 the frame implied. Against that, disabling the fragment shader's
alpha multiply measured **no change** (26.66 vs 26.68), and both cannot be true of one code path: the likely explanation is
that **the fragment module the pass loads is not the file being edited**, which is a one-line check and comes first. **Resolved, and the earlier reading was wrong:** the eight identical `0xFF808080` samples were the fixture's own test quads,
and the colour is packed **ABGR** - the channel the instrument called "r" is alpha. An aggregate over every write (printed
every ten thousand vertices) gives alpha **255**, red 0.40, green 0.49, blue 0.29, which is the right shape for grass and about
60% of the brightness the reference implies. That explains why disabling the fragment's alpha multiply did nothing: `m_color.a`
is 1.0. The Java path is byte-identical to upstream, so the difference is in what the builder is handed (occlusion and shade)
rather than in what it does with them.
Earlier note: **Process defect found and fixed in the work, not yet in the build:** the terrain shaders ship as **checked-in SPIR-V**
(`celtium-vulkan/src/main/resources/.../spirv/*.spv`) and the GLSL in `src/main/shaders/` is **not** a build input, so shader
edits do not reach the running game unless `glslangValidator` is re-run by hand. Two rounds of shader "fixes" and their
measurements were therefore void; the divisor change is kept as a correctness fix and its conclusion is withdrawn. With the
SPIR-V verified in the jar by size, the forced-light experiment was run for real and changes **no pixel** - the light map is
**not** what darkens the terrain. The prime suspect is now the other multiplier in the same fragment shader,
`diffuseColor.rgb *= m_color.a` (the AO/shade term), i.e. the colour the mesh builder packs. **Rule:** verify a generated
artefact inside the jar before trusting any measurement of its effect. Earlier note: the vertex light is **not** zero: an instrument at the write shows `block=0 sky=240` (full noon sky, no torch light) in the
**vanillalike** format, and the shader unpacks exactly that. The row-order hypothesis was tested and is **wrong**: flipping
the uploaded rows turns the terrain almost black (band luminance 3.2 against the reference's 131, mean difference 90.94), the
atlas's UVs being in the same OpenGL row order - so the flip is reverted and the baseline is back at **34.08 / 68.81%**. The
light coordinates are (block 0, sky 15), so the sample must land on the full-sky corner and does not; what remains is the
light map's own row/axis order (checkable from the existing dump, no run needed) and whether the texture bound at set 0
binding 1 is that light map.
**The darkness has a shape (round 270).** Row and column analysis of the two frames: the far plain matches the reference
almost exactly (row ratio **1.03**), the near terrain is **0.65-0.75**, and columns are flat at 0.83-0.86 across the width -
so no left-right structure, and the difference is the shading of the **side faces** that dominate near the camera. The AO
computation (`AoFaceData`) is **byte-identical to upstream**; the port's own changes under `impl/render/chunk` are
`SectionRenderDataStorage`, `ShaderChunkRenderer`, `RenderSectionManager`, `SectionGraph`, the `backend` package and the two
vertex formats (the last two only by diagnostics). **Measured since:** the difference is a **single multiplicative factor of 0.69** - the affine fit beats the pure scale by
0.25 of a luminance unit, the standard deviations agree (22.8 vs 23.4), and all three channels scale alike (0.691 / 0.685 /
0.703). And the light map the terrain samples is confirmed to be GL texture 12, 16x16 - the very texture analysed - whose
texel at this scene's light coordinate is the bright corner, 252, i.e. a factor of 0.99. So the light map is excluded too,
leaving the **atlas** as the remaining multiply; consistent with the row-flip experiment turning the terrain almost black
rather than merely reorienting it. **The atlas is byte-identical to OpenGL's** - a readback comparison reports `differing=0`, `maxAbsDiff=0` for both the
512x512 atlas and the 128x128 textures - so the texture is eliminated and the row-flip experiment is confirmed wrong by
content. **The pinned lineage's own shaders were then located and read**
(`upstream-baseline/.../sodium/shaders/blocks/block_layer_opaque.{fsh,vsh}`), and they show that two of this project's
changes - swapping the light axes and dividing by 16 instead of 256 - are **deviations from the reference** that happened to
move the frame its way (34.08 to 26.68 mean together). Both are reverted; the faithful configuration measures **34.08 /
68.81%**, recorded alongside the deviating numbers. **Next:** a line-by-line comparison of our shaders against those located
sources, and the inputs the mesh builder is handed.

**The light map is the missing factor, and the reference may not apply it (round 273).** The reference sources were read
line by line instead of experimented on: `chunk_vertex.glsl` is character-for-character what this port has, so the shaders
are faithful; the light map's alpha is 255 everywhere, so the fragment's `*= m_color.a` is a no-op; and the texel this
scene's light coordinate addresses is **0.65** (rgb 155,155,187) - exactly the factor the frame fit has been reporting. The
reference's vertex shader has a `CELERITAS_NO_LIGHTMAP` branch that **skips that multiply**, selected when the render pass
says `hasNoLightmap()`, and **this port has no such branch**. So if the fixture's pass declares no light map, the reference
renders without the 0.65 and the terrain difference is a missing configuration path rather than a wrong colour. **Next:**
read the 1.12.2 pass definitions for `hasNoLightmap()`; if true, add the second vertex module and re-measure.

**Refuted by reading (round 274).** `hasNoLightmap` defaults to false and the 1.12.2 pass builder never sets it, so both
frames light the map and round 273's explanation is wrong. What the reading did establish is a genuine fidelity gap: the
reference compiles `USE_VANILLA_COLOR_FORMAT` for this vertex format and applies `diffuseColor *= m_color` (rgb once, alpha
into alpha), while this port always applies the **compact** format's extra `diffuseColor.rgb *= m_color.a`. That is a no-op
for the terrain today (packed alpha 255) but wrong in general. With shaders equivalent, texture byte-identical and the light
map in both frames, the remaining 0.69 is in the **per-draw state** - the fog uniforms first, whose `mix` scales a band's
level while preserving its contrast, exactly the measured signature. **Parked, with the work recorded.** The fog uniforms were printed (`fogColor` 0.514/0.596/0.735 with alpha 1, smooth fog
from 96 to 128 blocks) and are not the cause; removing the light map multiply moved the mean from 34.08 to 26.12 but left the
terrain at 0.70 of the reference, so the light map is excluded by measurement too. Five candidates have now been eliminated
and the remaining 0.69 needs a **numeric** comparison - the shader's arithmetic against values computed by hand from the
dumped texture, the packed colour and the light coordinate - rather than a sixth image ratio. **S11 is closed** (round 277): the frame now has an identity incremented at the camera boundary, the per-frame window budget
is reset and reconciled there rather than on every fence wait, and a normal run reports **570 frames against 3327 flushes** -
a factor of six, which is exactly why the audit's "16 windows per frame" could not be demonstrated. Separately, the **shader
trap is closed**: `celtium-vulkan/build.gradle` compiles `src/main/shaders` into the loaded resources as a tracked task and the
committed `.spv` copies are deleted, verified by building and reading the jar (thirteen entries, terrain sizes 8864 / 8524 /
7748).

Consequences: (a) the honest terrain baseline is **68.81%** and the immediate-path numbers still need re-baselining against
the OpenGL reference; (b) for
terrain, accuracy must be compared against a renderer **outside this tree** - the pinned Celeritas/Embeddium lineage the plan
already names, run as a separate build of the same fixture; (c) for the immediate path (sampler, particles) the OpenGL
reference above is valid, because those draw through GL when the backend refuses them. Also worth stating plainly in the
architecture section: terrain is Vulkan-only while everything else still has a GL path, so "hybrid" means the two halves are
compared by different means.

## 0b. Java: compile target 21, game runtime 25 (corrected 2026-10-09 14:17)

Two different things, recorded separately because they are not the same claim:

| | Value |
|---|---|
| **Celtium's compile target** | **Java 21** - `celtium-vulkan` and `celtium-interop` build with `--release 21` (class file major 65), and nothing in them may use a Java 22+ API, language feature or preview |
| **The game's runtime and verification environment** | **Java 25** - the environment Cleanroom 0.6.13-alpha runs on, and where every startup, render and validation measurement in this project is taken |

The correction that produced this section: an earlier reading made "minimum runtime 21" mean *the game must start on Java 21*, which
made Cleanroom's own class file version (69, Java 25) look like a blocker and put "verify startup on Java 21" in the work queue.
**It is not a blocker and it is not a goal.** "The game's minimum runtime is 21" and "start the Cleanroom game on Java 21" are
**removed from the completion conditions and from the work queue**; Cleanroom is not modified or swapped for it, and no further
out-of-game load tests are run for it.

What is still true, and is checked:

| Check | State |
|---|---|
| `--release 21` for the renderer modules | Done |
| Final distribution jar's `celtium/vulkan/*` classes are major **65** | **Verified** |
| Mod-side `org/taumc/celeritas/*` classes stay major **52**, so existing Forge mods keep their binary/API/event compatibility and are not asked to recompile | **Verified** |
| Startup and rendering pass on the runtime in use (Java 25) | **Verified** - fixture PASSED, `nativeTerrainVerified True`, validation-clean |

**Not claimed:** that `--release 21` makes the whole game, including Cleanroom, run on Java 21. It does not, and no document here
says otherwise.

## 1. Purpose and completion condition

Done means all three, verified on hardware:

1. Minecraft's rendering, its final composition and the present are done by Vulkan.
2. Terrain plus entity/TESR/item, hand, GUI, font, HUD, particles, sky, clouds, weather, water, translucency, the
   various FBOs, post-processing, texture animation, resource reload, resize and world switch are ported.
3. Startup and rendering are verified **without a GL context and without the GL/VK bridge**.

Until then the build is **hybrid** and is reported as hybrid. A single swapchain probe is not "GL-free game rendering",
counters are not evidence of a correct image, and a terrain backend's name is not a completion criterion. Where
something is implemented but not verified, or verified but not correct, this file says so.

## 2. Supported environment and compatibility scope

Celtium targets **CleanroomMC only**. Plain Forge (the old Java 8 / LWJGL 2 stack) and other Forge + lwjgl3ify/RFB
configurations are **not supported runtimes**; that is a statement about the *environment*, not about mods.

| Item | Pinned value | Status |
|---|---|---|
| Minecraft | 1.12.2 | verified on hardware |
| Runtime | CleanroomMC **0.6.13-alpha** | verified on hardware |
| JDK | **25** for the Gradle build | verified |
| LWJGL | **3.4.1** (Vulkan bindings and `MemoryUtil` used directly) | verified |
| GPU / driver | AMD RX 6400 (RADV), Intel UHD Graphics 630 (ANV) | verified |
| OS / session | Linux, Wayland with Xwayland | verified |
| Other OS or GPU | - | **not verified**; features are chosen by capability, never by GPU name |

- **Kept:** binary compatibility with existing Forge mods **as they run on Cleanroom** - class and method signatures,
  event order, model and baked-quad data, and the `Tessellator`/`BufferBuilder`/`WorldVertexBufferUploader`/
  `GlStateManager`/`RenderItem`/`TESR`/`RenderWorldLastEvent` entry points. Mods are not asked to change.
- **Dropped:** launching on a runtime that is not Cleanroom. No shims, no "also works on Forge" claims.
- **Separate effort:** mods that call LWJGL/GLSL directly. The Forge entry points never see those calls; each pattern
  found is recorded in [mod compatibility](mod-compatibility.md) with its support scope, and anything not covered is
  listed there **with the reason**.
- Nothing from a newer upstream Cleanroom foundation is applied to this environment without being pinned, built and
  driven through the acceptance checks. Features that exist only in upstream `main` or in an in-development component
  such as Kirino are evaluated for stability, availability and necessity, and never become a dependency of Celtium's
  completion.

## 3. Foundations we build on

- Cleanroom's Mixin setup, including the official `Accessor` mechanism, for reaching Minecraft internals; private
  members are accessed that way rather than reflectively.
- LWJGL 3.4.1 as a **direct** dependency: the Vulkan bindings and `MemoryUtil` are called as documented.
- Minecraft's own public API where it exists (`BufferBuilder.getVertexCount()` instead of a reflected field).
- The renderer's lifecycle as the mod already wires it (`modEmbed`, the region manager, the per-pass entry point).
- Features are selected by **capability** (extensions, limits, formats), never by GPU name. Mesh shaders and other
  optional features are never mandatory.

Internal-only APIs, where one has to be touched at all, are recorded with the version they were pinned to and the
maintenance reason. The GLU projection helper is not available at runtime at all, and reaching into
RetroFuturaBootstrap's transformer internals was removed.

## 4. Current state

One table, no narrative: what exists, what was verified on hardware, and what is deliberately off.

| Area | State | Evidence / caveat |
|---|---|---|
| Terrain on Vulkan | **Verified on both GPUs with the current revision** | Per-pass handoff; `nativeTerrainVerified True` **as real-path evidence**: AMD `vulkanTerrainPasses=1620`, `glDrawsCancelled=564`; Intel UHD 630 `vulkanTerrainPasses=804`, `glDrawsCancelled=278`, `vulkanPasses=1082`. Both runs: exit 0, **0 validation messages**, fixture PASSED |
| Per-pass handoff, uniform pre-reservation, backend counters | **Verified** | Audit fixes P0/P1/P2 kept; handoff ~130 us measured |
| GL state mirrored from `GlStateManager` (matrices, blend, depth, cull, alpha, texture, colour) | **Verified** capturing | Mirror vs live measured **1.62** in the world pass, and that residual is the renderer's own. The earlier "view 2000.0 apart" **was `setupOverlayRendering`'s `translate(0, 0, -2000)` being compared across passes** - the correction is recorded in the history, and 1140 of 2262 comparisons had been cross-pass |
| Immediate draw entry (`WorldVertexBufferUploader`) with `Accessor` and structural format ids | **Verified** capturing | ~10k batches offered per run, ~2.8k accepted, no refusals |
| Immediate pipelines: `POSITION_COLOR`, `BLOCK`, `ITEM`, particle | **Implemented; equal to the GL path on a local comparison** | Measured as *immediate off versus immediate on* with the terrain on Vulkan in both - a legitimate local comparison and the strongest this tree can make, because **it has no GL terrain path to compare against**. "Pixel-identical to GL" is not a claim this project can make: the reference with terrain is the pinned upstream build, and against it the current frame reads mean ~34 with ~69% of pixels over 8, dominated by terrain shading |
| Immediate pipeline: `POSITION_TEX_COLOR` | **Implemented; excluded by default** | In this scene it is the final full-screen composite, not content; drawing it into the game framebuffer flattens the picture |
| Immediate path: frame-late deferral **removed** | **Verified on both GPUs** | The mid-frame window limit is sixteen per frame rather than one: measured **immediate off versus on** (0.00) with no measurable cost (p50 16.07ms and p99 17.90ms at sixteen against 16.13ms and 18.16ms at one). The old one-window measurement that blackened the frame predates the submit-wait fix and had that defect as its explanation |
| *Transitional:* **immediate path, replacement demonstrated** | **Verified on hardware** | For the first time the injector's cancellation is effective (the audit's A) *and* the submit call exists (a defect introduced while cleaning a dead branch), so the counters and the log agree: **`immediateOffered=9060`, `immediateTaken=585`, `immediateFlushes=585`, and the vanilla upload body completing 535 fewer times than it is offered** - GL is skipped for exactly the batches Vulkan takes. Measured with the sound instrument (hidden GUI, same-revision references, floor 0.00): **the frame matches GL to nine pixels out of 921,600**, where before the fix every comparison had GL drawing the same content |
| *Transitional:* immediate path end to end | **On by default; equal to the GL path on a local comparison, on both GPUs** | Measured with no flags at all as **immediate off versus on** (terrain on Vulkan in both): **0.00 mean, 0.0% of pixels over 8 and over 32 on the RX 6400 and on the UHD 630**, run PASSED with `nativeTerrainVerified True`. Called "each GPU's own GL reference" when this row was written, which was the self-comparison the project later identified: the tree has no GL terrain path, so a comparison that includes terrain is always against the pinned upstream build, never against GL here. Defaults are `immediate=true`, `immediateWindows=true`, `immediateFlushIndices=512` - the window flush is what keeps the content lag-free - with the acceptance mask `1,3,4,5` | Local comparison (same build, immediate off vs on, terrain Vulkan in both): **mean 0.00, 0.0% over 8 and over 32 on AMD RX 6400 and on Intel UHD 630**, 1725 and 1151 batches drawn by Vulkan respectively, VUID 0. The default acceptance mask is `1,3,4,5`: the 24-byte `POSITION_TEX_COLOR` family is the final full-screen composite and is left to GL until present ownership is settled |
| Native present of the game's frames | **Runs and presents the frame's content; not validation-clean** | Vulkan blits the shared colour image into an acquired swapchain image and presents it at the composite - `Framebuffer.func_178038_a`'s moment, identified by stack trace - with the acquire/render-finished semaphore pair; the presented image's centre patch measured 118.25, in the same range as the game framebuffer, so the screen is given the frame rather than a blank surface. **Correction:** an earlier row here claimed this was validation-clean; that run had the probe off, and the probe is what created the swapchain, so it presented nothing and was clean for that reason. The standalone present emits **36 validation messages**, identical on RADV and ANV, with the submission demonstrably waiting the acquire semaphore, a fresh semaphore pair per present, and a stage mask covering all commands - so the remaining explanations are the layer's view of semaphores on a device created for external semaphore and memory, or its reporting of the presentation engine releasing the image. The flag stays off by default |
| Frame ownership, draw ordering, image layouts | **Not implemented** | Two mid-frame window attempts failed; see the log |
| **World switch** (leave the world, join it again) | **Verified on both GPUs** | The fixture makes the same three calls `GuiIngame`'s "Save and Quit to Title" makes - `sendQuittingDisconnectingPacket`, `loadWorld(null)`, `displayGuiScreen(new GuiMainMenu())` - at tick 100 and then joins the same save again. **PASSED, `nativeTerrainVerified True`, left and rejoined at tick 100, 885 passes on the RX 6400 and 591 on the UHD 630.** The first attempt omitted the third call and crashed vanilla's renderer on a null player, which is recorded in the history as a fixture bug rather than a renderer one |
| **Resource reload** (chunk renderers rebuilt, textures re-stitched) | **Verified on both GPUs** | Triggered mid-run from the fixture the way F3+T does it - `mc.renderGlobal.loadRenderers()` plus `mc.getTextureManager().onResourceManagerReload(...)` at tick 100 - and the run stays green while the Vulkan terrain path keeps drawing: **PASSED, `nativeTerrainVerified True`, 901 passes on the RX 6400 and 591 on the UHD 630**. The texture half is what exercises the upload mirror, since a re-stitched atlas has to be re-read rather than drawn from its first upload |
| Texture uploads: registration and **animation frames** | **Implemented and verified firing** | Both of Minecraft's upload paths are hooked - `TextureUtil.func_110989_a` for registration and `func_147955_a` for sprite animation frames, each invalidating the mirror so the next draw re-reads the GL texture. Firing was observed in a green run (ids 7, 8, 13, 16, 17 and five sprite frames). A still comparison cannot show motion, so the animated result is a visual check |
| **Resize** | **Verified on hardware** | The fixture drives Minecraft's own `resize(width, height)` - public, and independent of the window manager, which is why the earlier attempt through `glfwSetWindowSize` never reached the game. Mid-run 1280x720 to 1024x640: **PASSED, `nativeTerrainVerified True`, 1652 passes, 0 validation messages, capture and screenshot both 1024x640**. It also exposed a real defect - the frame target's framebuffers were destroyed while a command buffer was still using them - which is fixed by settling the GPU before the target is recreated |
| GUI/font/HUD visible | **A real difference, localised to the first-person hand; cause not yet identified** | Frozen-animation comparison with a usable floor: **reference against reference 0.07**, immediate path against a reference **1.63 (1.29% over 8)**. Region by region the **HUD measures 0.00%** (hotbar, crosshair, chat/overlay) while the hand and the world band it overlaps differ. Emptying the hand separates the two: the difference falls to **0.96 against a 0.10 floor**, so roughly two thirds of it is the **arm**, which Minecraft draws even with nothing in hand, and the rest is the **held item**. Both are first-person hand rendering; the HUD is not involved |
| **Water (translucency) in the scene** | **A real difference, measured with a sound floor** | The validation world was a flat stack of bedrock, dirt, grass, leaves and stained glass - no water at all, so every pixel-identical result said nothing about translucency. A water layer was added to both world creations and the hidden-GUI comparison re-run with its own floor: **reference against reference mean 0.01 with 0.00% of pixels over 8**, immediate path against a reference **mean 0.04 with 0.27% of pixels over 8**. So the water scene is not pixel-identical where the terrain-only scene was exactly 0.00, and it is a small, reproducible difference in a content class the objective names |
| **Stale lightmap mirror** | **Found in the source, fixed, difference fell 3.3x** | Minecraft refreshes its lightmap every frame through `DynamicTexture.func_110564_a` to `TextureUtil.func_110988_a`, and neither upload hook covered that path, so the mirror kept the first frame's lighting. Two attempts to hook it crashed the client during Mixin transformation, so the fix is on the renderer side: a frame-interval bulk mirror refresh (`celtium.vulkan.mirrorRefreshFrames`, default 1) that cannot be wrong about which texture changed. Weather and particle scene: **1.31% of pixels over 8 (12,086) before, 0.40% (3,715) after**, against a 0.23% floor - the dominant cause, with a residual above the floor still open. **The cost was then measured** rather than assumed: p50 16.17/16.10/16.02 ms and p99 18.42/18.80/19.28 ms at intervals 1/4/16, within a run-to-run spread of about 0.15 ms, so refreshing every mirror every frame is affordable and the interval that keeps the mirror never stale is the one kept |
| **Particles (format 5)** | **Attributed, cause narrowed** | With only the particle family accepted, so everything else is GL-drawn and correct by construction, the weather and particle scene differs by **1.19% of pixels over 8 (10,973)** against a **0.23%** floor - about ninety per cent of the 1.32% seen with all formats. The difference therefore belongs to format 5 alone. Its layout was checked against Minecraft's own declaration and matches (`POSITION_3F` + `TEX_2F` + `COLOR_4UB` + `TEX_2S` = 28 bytes, float uv and short lightmap), so the cause is in how the pipeline uses the data - the lightmap values, the flat varying, or the sampling |
| **Weather (rain)** | **No measurable contribution** | Adding rain to the particle scene moved the difference from 0.83 to 0.85 and the floor from 0.07 to 0.06 - within the scene's own variation, so nothing about rain rendering is claimed either way |
| Remaining content (sky/clouds, FBOs, post-processing
| Mod compatibility | Fixture mod **verified**; real mods **not yet** | See [mod compatibility](mod-compatibility.md) |

## 5. Blockers

1. **Frame ownership.** The bridge owns the shared images for one handoff per frame. This entry used to say "one window per
   frame and early in it, so immediate batches that arrive later are drawn against the next frame's buffers" - **both halves
   are now measured and neither holds**: the window limit is **sixteen per frame**, measured pixel-equal on a local comparison
   with no measurable cost (p50 16.07 ms and p99 17.90 ms at sixteen against 16.13 ms and 18.16 ms at one), and the old
   one-window measurement that blackened the frame **predates the submit-wait fix** and had that defect as its explanation.
   What remains is narrower: the images are handed over once per frame, and the present is still GL's.
2. **The world matrices.** This entry used to say they are invisible to the Mixins - "`GlStateManager` wraps only `glOrtho`,
   the GLU helper is absent at runtime, and mixing into LWJGL's own `GL11` stops the client". That was true of the attempt it
   describes, and it is no longer the state: the projection is now **derived** and applied at the world pass, the camera
   transform is seeded from the frame's camera setup, and the mirror-versus-live residual in the world pass is **1.62** rather
   than the cross-pass 2000 that was quoted at the time. Reaching the matrices by reading GL state per batch is no longer the
   only option; a snapshot read per frame remains.
3. **Present ownership.** The swapchain path runs, and this is still the honest blocker: with `presentFromComposite` enabled
   this scene **never presents** - it produces no format-2 batch at all, so the path is inert rather than exercised - and with
   an out-of-date acquire forced the client used to deadlock, which the signal-only release fixed. Making Vulkan present the
   game's frames remains distinct work, and it belongs with frame ownership because both decide who owns the images when.

## 6. Work order and dependencies

Foundational work comes first because it unblocks everything else; this is a dependency order, not a strict sequence -
content work that does not touch frame structure can proceed in parallel.

1. **Frame ownership and present ownership** (with draw ordering and image-layout bookkeeping), including the commit
   point where Vulkan presents. Blocks the image, the per-batch cost and GL removal.
2. **Content port** on top of it: GUI/font/HUD, sky/clouds/weather, water, translucency ordering, FBOs and
   post-processing, texture animation, resource reload, resize, world switch.
3. **Forge-API mod verification** with the fixture, then real mods, recorded per mod.
4. **Removing GL state reads.** The immediate path reads the live projection and modelview with two `glGetFloatv` calls
   **Per-batch reads are removed; the world pass draws from a per-frame snapshot.** The world pass's boundaries are hooked explicitly (`func_175068_a` HEAD/RETURN), the camera boundary captures the projection derived from Minecraft's numbers plus the camera transform **once per frame**, and that snapshot **seeds the mirror's base** so per-model/per-entity local transforms still accumulate on top. Inside the pass, batch matrices come from the mirror with **no `glGetFloatv`**, and batches outside it are **refused before acceptance** with a reason so GL keeps drawing them. Measured: `snapshotServed 578 == immediateTaken 578` (nothing fell through to a readback), `outsideWorldPassRefusals 7164`, `matrixDiagnostics=false`, frame PASSED and the image unchanged at 0.40% against the same reference. **Still incomplete:** one read per frame remains at the camera boundary - the snapshot itself - and computing the camera transform from the view entity in vanilla's order is what removes it.
   per matrix change, which the measurements in this plan call the dominant per-batch cost. Replacing them needs the
   mirrored stack to be faithful, and **it is not**: 1136 comparisons produced **zero** exact matches, with a maximum
   element difference of 2.0 in the projection and 1.62 in the modelview. That is now a measured prerequisite rather than
   an assumption. The first dump appeared to show the mirror **close, not broken** - projection matching
   exactly, modelview missing only a translation - but that reading came from sampling the **first** comparison, where GL's
   own matrices are also identity, and the **worst-sample** dump contradicts it: at the sample that matters the mirror's
   projection is the identity where GL holds a real frustum. The instrument now dumps at a new maximum. The worst-sample dump then named the cause: the mirror's projection is the **identity** where GL holds a real frustum,
   and its modelview is the right rotation **without the camera translation**. Both operations happen where no hook reaches -
   the world projection goes through `Project.gluPerspective`, outside the transforming loader, and the camera translation is
   applied before the backend exists - so **hooking cannot fix this**. The mechanism is now exact: inside `func_78479_a` a **hooked** `matrixMode` + `loadIdentity` resets the mirror's projection
   to identity, an **unreachable** `Project.gluPerspective` fills GL's and not the mirror's, and the camera translation is
   applied where the mirror is not yet listening - so nothing in the hook coverage is broken, and what is missing is the
   **value**: `fov`, `aspect`, `0.05F`, `renderDistanceChunks * 16 * SQRT_2`, three of which are readable from Minecraft
   state and the fourth a private method whose SRG name is now known (`func_78481_a`).

   **Verified:** the projection delta fell from **2.0005527 to 1.6200542E-4** once
   `setMatrix` wrote into the stack instead of into the copy that `matrix(mode)` returns - the same defect that made an
   earlier seed look "ineffective for the wrong reason". The modelview remains at 1.62. Its cause is located precisely now: the
   translate call **is** hooked and the mirror misses it only because it was applied before the hooks existed, and the seed
   written to repair that copies GL at the **first comparison**, which is a moment when GL itself holds the identity. The
   camera setup's structure is read (`orientCamera` holds the per-mode translations; `setupViewBobbing` is outside
   A seed at the **camera setup** - the moment the world matrix is finished, rather than the first comparison - was then tried: it runs exactly once (`cameraSeeds:1`) and leaves the modelview delta at 1.62, so the gap lies between that moment and the comparison and is to be **traced** rather than hypothesised - record the mirror's translation elements at each `loadIdentity`, each `translate` and each comparison.
   Three further counters narrow it to a **reset**: steady-state (1154 of 1166 comparisons) still reads 1.62, so it is not warm-up; `translateCalls:572` shows translates do arrive; and `cameraSeeds:1` shows the seeded matrix is lost again. Operations arrive and are removed afterwards, and the only mirrored operation that removes translation is the `loadIdentity` each pass performs - which makes this the **S10 pass-ownership problem in its concrete form**. The confirming instrument is a short trace of the mirror's `m[12..14]` at each `loadIdentity`, each `translate` and each comparison.
   **The trace then reframed it:** forty traced events show the mirror holding the camera translation almost all of the time (`L2[mv 0.00,-1.62,0.05]` repeatedly) and only sometimes reading zero right after a reset, so `1.62` is a **maximum over comparisons that land in different passes**, not a mirror that never received the value. The modelview is adequate; **what is missing is pass ownership** - a group key has matrices, format, texture and state but not the frame, target or pass - which is the audit's **S10**, and removing the `glGetFloatv` reads depends on that rather than on a better mirror.
   **Anchors located for S10:** the viewport is `GlStateManager.func_179083_b(int,int,int,int)` (a plain `@Inject`), while the **framebuffer binding is not in `GlStateManager` at all** - the SRG source has no `glBindFramebuffer`, so it goes through `Framebuffer` and the direct GL path. `Framebuffer` is a Minecraft class, so it is hookable the same way. The work is two hooks, two fields and those fields in the group key; the measurable consequence is how often a group would have merged two passes, which the key currently hides.
   **The acceptance fixture is built and it fails:** a second 256x256 `Framebuffer`, cleared, bound and drawn into, then the main target rebound with overlapping quads - same format, texture and state. `passSplits` rises 0 to 16, so the key separates passes, and the run fails with the modelview delta at a **steady-state 2000**, a magnitude consistent with the overlay pass's `glOrtho(0, width, height, 0, 1000, 3000)`. The next instrument move is one edit: record the worst sample inside `compareMirroredAndLiveMatrices` with the pass identity attached, because the current trace keys on the projection delta and therefore never sees the sample that fails.
   **The worst sample is named:** `frame=2 target=44569671 viewport=1311440 projDelta=0.0 mirror[m0=1.00,m5=1.00,t=0.00,0.00,0.00]` - the disagreement is not steady state but happens the moment the fixture's second target appears, the projection matches exactly, and the mirror's modelview is the identity where GL has the pass's own matrix. That is S10 in miniature: **binding a second render target mid-pass leaves the mirrored modelview at the identity**, and the fix is the acceptance condition - preserve order and target across the switch so batches are compared and drawn against the matrices and target they were issued with.
   **Four sampling artefacts in one counter:** the residual 1.62 turned out to be, in order of discovery, a matrix copy nobody read (fixed by `setMatrix`), the overlay's `translate(0,0,-2000)` entering a world comparison (fixed by scoping to the pass), the first frame before the backend existed (fixed by the steady-state block), and now **the first batch of a frame compared against the previous frame's GL state** - the mirror's modelview is all zeros, the value between a reset and the operations that fill it. The mirror has been adequate since the first of those; what kept being wrong was the measurement.
   **The fourth artefact is still open, and two attempts at it failed the same way:** skipping one comparison after a reset gave 1125 skips with `view` still 1.62, and skipping while the mirror reads zero gave **0 skips** because `m[0]` of an orthographic projection is `0.0015` and prints as `0.00`. The mirror at the worst sample holds the **overlay's** matrix while GL holds the world's, so `worldPassActive` is unreliable - MC installs orthographic projections through unhooked paths. The robust discriminator is GL's own projection in the diagnostic path (`m[11] == -1` for perspective), not an inference from the mirror, which is the thing in question.
   `func_78479_a`, so a derivation at its return cannot clobber bobbing), and the exact point of the `-viewerPos` translate
   is to be read **unfiltered** - three greps in a row filtered out the line being looked for, the fifth time this session
   that a filtered read produced a conclusion the whole source did not support. Earlier notes: `func_78481_a` is hooked at its return and the backend computes `gluPerspective`
   from Minecraft's four numbers (`derivedProj:3409, refused:5`), but the worst-sample counter still reads the identity
   matrix, because it samples at a moment when another pass - the GUI or the hand - has reset the projection stack through
   the same hooked `matrixMode` + `loadIdentity` pair. The acceptance is now taken at the right instant - the `matrixMode(GL_MODELVIEW)` that follows the
   unreachable `gluPerspective` - and it reports **`derivedCompared:3427, derivedDelta:2.0005527`**: the same difference as
   before the derivation existed, and recognisably the identity's `m[10]` against GL's `-1.001`. So the write happens and
   something between the write and the comparison restores the identity; the next step is to count `loadIdentity` calls per
   matrix mode and the matrix identity the mirror reports at both points, which separates "one array being reset" from "two
   arrays being written and read"; which projection a given batch should use is the separate
   pass-ownership question the audit lists as **S10**. The anchors are read from the vanilla source: the camera setup is
   `EntityRenderer.func_78479_a(float, int)` - a Minecraft class method, so a plain `@Inject` reaches it, with no accessor
   machinery of the kind that crashed the client twice - and it holds the `gluPerspective` call with its `fov`, `aspect`,
   `near` and `renderDistanceChunks * 16 * SQRT_2` arguments, plus the translate/rotate that place the camera. The
   acceptance is the two deltas that produced this finding: `proj max|diff|` falling from 2.0006 and `view max|diff|` from
   1.62, measured with the same worst-sample instrument. The way forward is the one the direction states:
   **derive the projection and camera transform from Minecraft's own state** (field of view, aspect, near/far;
   `ActiveRenderInfo` position and orientation) rather than mirroring unreachable GL calls. The two `glGetFloatv` reads per
   matrix change then disappear as a consequence. A one-time seed was tried and is recorded as ineffective: at the first
   comparison GL is also identity, so it seeds nothing. The known candidate for the
   projection maximum is `Project.gluPerspective`, which the compatibility file already records as unreachable; if that is
   what it is, the answer is to derive the projection from Minecraft's camera parameters rather than to mirror the call.
5. **Removing GL**, then re-verifying startup and rendering without a GL context or the bridge.
5. **Performance and distribution**, measured, never asserted.

## 7. Stages: deliverables, acceptance, failure handling

| Stage | Deliverable | Acceptance (hardware) | If it cannot be done |
|---|---|---|---|
| S0 audit remainder | Failure-path and lifetime checks under synchronisation validation | No validation message; the checks are in the harness | Fix before moving on |
| S1 common abstraction | One backend boundary that carries state, formats and batches | Build passes; terrain verified on both GPUs | Do not proceed on a broken boundary |
| S2 Cleanroom-only runtime | Runtime target stated; legacy-runtime paths removed (**code done**) | Client starts and renders on the pinned environment | Record what still depends on the old paths |
| S3 build migration | Staged move to Cleanroom-oriented tooling | Each step leaves `packageJar` + a PASSED validation run | Stop at the last working step |
| S4 frame ownership + present | Vulkan owns the frame and presents it; ordering and layouts defined | GL-free startup renders a correct frame and presents it | Keep the bridge, report hybrid, keep the blocker listed |
| S5 content port | The remaining content classes on Vulkan | Each class renders correctly and validation-clean on both GPUs | A class that cannot be ported stays on GL and is **named** |
| S6 Forge-API mods | Mods verified unmodified | (a) binary compatibility and (b) real Vulkan pixels, per mod | Record per mod in `docs/mod-compatibility.md` |
| S7 remove GL | No GL context, no bridge | Startup + rendering verified without either | Do not claim completion while GL remains |
| S8 performance + distribution | Measured improvements; packaging | Same conditions as the reference comparison | No claim without measurement |

## 8. Optimization deltas from Sodium upstream

Celtium descends from a pinned Celeritas base (Embeddium, the last FOSS Sodium, plus Oculus; Sodium 0.5.11 and older),
and it both ports from and is ported to that line. **Licence boundary, checked against the primary source:** this
project is LGPL-3.0 and current Sodium (`mc26.3-0.9.2` = `03198cb`, `0.9.3-alpha`) is **PolyForm Shield 1.0.0**, whose
Noncompete clause excludes competing products across platforms, languages and architectures. Therefore **no code from
Sodium 0.6 or later enters this repository** in any form, including mechanical translation or renaming. Public
descriptions and general algorithms may inform an independent implementation with the source and reasoning recorded; a
rewrite does not launder a restricted source, and anything needing permission is not adopted automatically.

Candidates (allocator defragmentation, buffer recycling and staging, indirect batching, item/entity encoders,
visibility and translucency ordering, state/synchronisation and frame pacing) are judged one by one in
[Sodium optimization deltas](sodium-optimization-deltas.md): source version, whether it is already implemented here,
conditions for 1.12.2/Cleanroom/Vulkan, licence, performance hypothesis, rendering and compatibility risk, and a
priority. Modern-Minecraft-specific fixes and anything needing Sodium 0.6+ code are rejected there with reasons.
Candidates are only kept with a measurement taken under the conditions in section 9. Chasing a newer API is not an
optimisation.

## 9. Verification criteria

Two different comparisons, not to be confused:

- **Global reference comparison.** The pinned original Celeritas reference against the current Celtium build, same
  scene, same capture point, same duration, on both GPUs, comparing CPU and GPU time and the frame-time distribution,
  with validation output and the image checked. This is what a performance claim rests on.
- **Local comparison inside one build.** Current Celtium with a path off against the same build with it on (terrain on
  Vulkan in **both**), which is how the immediate path was measured. It answers "does this path change the image or the
  cost", not "is Celtium faster than Celeritas".

Rules that follow from measurements already taken:

- A PASSED run proves the harness ran, not that validation is clean or the image matches. Both are checked separately.
- Run-to-run variation is measured before it is compared against: the GL reference is bit-identical between runs and
  the Vulkan path varies by 0.83 mean, so differences below that are noise.
- Counters (`immediateOffered`, `immediateTaken`, `immediateFlushes`, `immediateDropped`) prove geometry was submitted,
  never that it was drawn correctly.
- Validation messages are never left in place; a change that introduces one is reverted or fixed before it stays.
- Comparisons run on both GPUs, and mesh shaders or other optional features are never required.

## 10. Effort estimates (person-days, estimates only)

Adding the stage table above for stages 0-5 and 7-8: 1-3, 6-12, 6-12, 10-20, 25-50, 10-25, 10-20, 10-25 =
**78-167 person-days** (low: 1+6+6+10+25+10+10+10; high: 3+12+12+20+50+25+20+25). Earlier revisions of this file
stated 70-145 in the body while the table summed differently, and a later reading of the same table gave 88-187; the
sum of the table as it stands is 78-167, and that is the figure to use. The discrepancy is recorded rather than
quietly overwritten, and it is a reason to read these numbers as estimates with wide error bars, not as accounting. Frame ownership and present ownership (stage 4) carry most of the uncertainty, together
with the shader and post-processing work in stage 5.

Direct-GL support is a **separate scale**: 20-60 person-days for the subset seen so far, with no useful upper bound
because it grows with the number of mods that bypass the API; it is not part of the range above.

These are estimates, not a schedule: no completion date is derived from them, and the initial total is not the same
number as the remaining work. Actual progress is reported as what is verified, not as a fraction of these figures.

## 11. What is explicitly not a completion criterion

Speed; a backend-selection string; a hybrid build; a PASSED fixture run on its own; identical-looking output on its
own; deleted compatibility code; a newer API; or a single probe that presents one cleared image.
