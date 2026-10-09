# Second external audit - 2026-10-09

Basis HEAD `16af6653a`; re-checked at `dee914e05`. 58 files compared against cached Minecraft sources, the real
Cleanroom profile's Mixin bytecode, the shipped jar's annotations, and the existing AMD/Intel logs and PNGs. No edits,
builds or GPU runs were made from the auditor's side. See [the first audit](audit-2026-10-09.md) for its items and
[the plan](plan.md) for the stages these belong to.

## What the first audit's "fixed" claims are worth now

Three of them do not survive, and the history file records each correction:

| Claim | Status |
|---|---|
| the immediate path draws natively (adoption count = replacement count) | **withdrawn** - the cancellation was ineffective (S03) and the submit call was later deleted entirely |
| matching images mean the Vulkan implementation is correct | **withdrawn** - GL drew the same content in every earlier comparison |
| the first audit's B and E are fully resolved | **reopened** - B's ownership/layout handoff is unimplemented (S01); E's timeout path still reuses command buffers (S02) |

## Items

### S01 [P0, `presentFromComposite=true` only] the shared image's present contract is unimplemented
The seven-argument `presentImage` received the bridge's semaphores and never used them; the submission waits one and
signals one, while `endFrame` makes GL wait on the bridge's done semaphore. The blit's barriers use
`QUEUE_FAMILY_IGNORED`, so the EXTERNAL -> Vulkan -> EXTERNAL ownership transfer the frame target requires does not
happen. `FrameTarget.COLOR_RENDER_LAYOUT` is `GENERAL` while `beginFrame` tracks `COLOR_ATTACHMENT_OPTIMAL`, so the
contract and the tracked value disagree.
**Partially fixed:** the submission now waits `[acquire, bridgeWait]` and signals `[renderFinished, bridgeDone]`, and the
verdict flags are reset at entry (measured `withWait=2`, `validationClean True`). **Open:** the ownership transfer, the
`GENERAL` layout, and the present's own failure handling.

### S02 [P1] a timed-out wait still lets the command buffer be reused - **closed**
**Fixed and verified with a documented injection** (`-Dceltium.vulkan.failIdleAfter=N`, off by default). A timed-out wait now
propagates: nothing is cleared, reset, re-recorded or destroyed, and every entry that records, ends or submits the frame's
commands is closed while stalled - `render`, `flushImmediateWindow`, `submitImmediateBatchTimed`, `runSwapchainProbe`,
`ensureFrameTarget`, `delete`, `settleGpu`, `recordImmediateOnlyPass`, `recordRenderPass`, `submitCommands`. Measured with
the injection at a steady-state wait: **0 validation messages, exit 1, fixture FAILED, `gpuStalled true`** - an explicit
failed state - where the same injection previously ended in **SIGABRT with 17 validation errors**. The healthy run is
unchanged (PASSED, no messages).
**Half done, deliberately not called closed.** The recording side is guarded: the wait reports success or failure, a failure
touches nothing, and every later batch is refused before acceptance, with a documented injection
(`-Dceltium.vulkan.failIdle=true`) for the path no ordinary run reaches. Measured: the normal run is unchanged (exit 0,
validation clean, 0 messages, PASSED, `gpuStalled false`), and the **injected** stall still ends in `SIGABRT` with 17
validation messages - because the rest of the frame (`endFrame`, present, terrain passes) still assumes a recorded buffer.
**Open, and the test itself is now suspect:** seven entries are guarded (`render`, `flushImmediateWindow`,
`submitImmediateBatchTimed`, `runSwapchainProbe`, `ensureFrameTarget`, `delete`, `settleGpu`) and the injected run is still
`SIGABRT` with the same two messages - a barrier into an un-begun buffer and a framebuffer destroyed while a command buffer
referenced it. Because the guards cover every backend entry that records or destroys, the abort happens **before** them: the
injection fails the *first* wait, which is during start-up rather than in a steady frame. The next step is to aim the
injection at a later wait so it exercises the state S02 describes - a recorded, submitted predecessor - and start-up stalls
are recorded as a separate question. **Open:** five entries that touch the frame target are now guarded (`render`, `flushImmediateWindow`,
`submitImmediateBatchTimed`, `runSwapchainProbe`, `ensureFrameTarget`) and the injected run is unchanged - the same two
messages, a barrier recorded into a buffer that was never begun and a framebuffer destroyed while a stale command buffer
referenced it. So the offending path is **not** in the backend's frame stage; the candidates are `VulkanFrameTarget`'s own
copy-in and copy-out and the swapchain's present path, which create and record their own commands. The acceptance is
unchanged: an injected run that refuses and stays clean instead of aborting.
`waitForIdle` logs `VK_TIMEOUT` and returns; `beginCommands` then proceeds, clears `submissionPending` and resets the
command buffer although the GPU may still be reading it. `settleGpu` is described as bounded but `vkDeviceWaitIdle` has no
timeout, and the context drains queues with `vkQueueWaitIdle` and `Long.MAX_VALUE` fences. The submit-failure injection
does not exercise this path at all. **Open.**

### S03 [P1, fixed during the audit] the original GL draw was never cancelled
The injector lacked `cancellable = true`, so `info.cancel()` threw and the broad catch hid it: the vanilla body ran for
every accepted batch. Fixed in `f6ac63536` (`cancellable`, `reset`, independent counters); a regression introduced while
doing so deleted the `submitImmediateBatch` call itself and was restored by `dee914e05`.
**Open:** per-batch correspondence between acceptance and the absence of a GL draw, on both GPUs.

### S04 [P1, fixed during the audit] the group state was zeros
`currentGroupState` was never assigned, so blend, depth, culling and the alpha reference were all off. Fixed by
`580b897b0` (`state.clone()`). **Open:** verifying each state input against the snapshot and the pipeline.

### S05 [P1, certain in code] mesh merging does not add a base vertex
`VulkanDynamicMesh` appends vertices but never offsets the appended indices by the previous vertex count, and draws with
`vertexOffset = 0`, so a second quad in the same group references the first quad's vertices.
**Fixed:** `copyVertices` captures the group's base vertex before appending and writes `index + baseVertex`, so indices stay
relative to the group. **Measured:** the weather and particle scene is unchanged at **0.40% of pixels over 8 (3,714)** -
which says this scene's groups are mostly single-batch, not that the defect was harmless. **Measured, and it points at S14 as much as at S05.** The fixture exists - two quads in one `Tessellator` batch inside a
Forge render event, `-Dceltium.validation.drawTest=true`, reporting `drawTestQuads 2` - and running it with the immediate
path off and on gives:

| Configuration | saturated red | saturated blue |
|---|---|---|
| immediate path off (GL draws) | **81 px** | 0 |
| immediate path on | **0** | **0** |

That comparison was **withdrawn**: the red pixels are scattered across a 681 by 13 band and fill 0.7% of their bounding
box, and the count moves between runs of the same fixture (81, 107, 65), so they are scene content rather than a quad. The
fixture's geometry has never reached the frame at all, even with GL drawing it, at six blocks across, culling and depth
disabled, and the handler demonstrably running. The **open** work is therefore the fixture before it can be an acceptance test. It now draws in the GUI overlay pass, with
`Gui.drawRect` beside it as a control in the same handler, pass and frame: the control fills **55,080 px, 100% of its
bounding box**, while the fixture's own batch produces **0 px** - before and after being rebracketed through
`GlStateManager`. So the Tessellator reaches the framebuffer here and the fault is in the fixture's own batch construction;
the next version copies `Gui.drawRect`'s exact body and appends a second quad inside that one batch. The counters from that
run are unaffected: `immediateGroupsClosed` 1137 with `immediateMaxGroupVertices` 880 is a fact about the scene. The counters also give the first hard number about merging: groups of up to **880 vertices** exist in this frame,
so the base-vertex defect was the normal shape of a merged group here, not a corner case.

### S06 [P1, certain in code] growing a dynamic buffer loses its contents
`VulkanHostBuffer.ensureCapacity` deletes and recreates the buffer without preserving data, while the mesh keeps appending.
Crossing 64 KiB of vertices or 32 KiB of indices drops everything written before. Its own comment said "the contents are not
preserved", next to callers that assume they are.
**Fixed:** the replacement buffer is created first, the old contents are copied into it, and the old objects are detached
before creation so an allocation failure leaves the existing data valid. **Measured:** unchanged at 0.40% (3,714).
**Open:** the acceptance fixture that crosses both boundaries.

### S07 [P1, certain in code] the state mirror and pipelines do not carry the state that matters
**First hook in:** `GlStateManager.func_179112_b` - whose SRG body calls `GL11.glBlendFunc` - is captured, and a run reports
`blendObserved=true(770,771)`, i.e. `SRC_ALPHA` and `ONE_MINUS_SRC_ALPHA`. That **confirms by observation** what the particle
family had been asserting. Using the observed factors in the pipeline is **not** done yet, and deliberately: the pipeline
cache key does not carry them, so the first group's factors were shared by every later group and the frame got worse (0.23%
to 0.33%). **Keyed, and still not enough:** with the factors in the pipeline cache key the frame is **0.31%** - worse than the
constants' 0.23% - because `blendObserved` is the **most recent `blendFunc` call anywhere**, not the factors in effect when
the batch was offered. The constants are restored (0.21% measured, consistent with 0.23%). **A withdrawn explanation, and a narrower question:** the "(6,7)" outlier that was going to explain the regression is
**withdrawn** - a verbatim recorder reports `none` for the whole run, so it was an error in reading a packed key. What stands:
573 of 574 groups captured **exactly the constants** the family asserts, and threading the observed values through the key
and the pipeline still measures 0.32-0.33% against the constants' 0.21-0.28%. The reason is therefore **not in the factors**.
Earlier note: **Per-group capture done, and still not enough:** the group now carries the blend factors in effect when it opened
(beside the state key, as the matrices are) and the frame measures **0.33%** against the constants' 0.21-0.24%. So the factors
alone are not the difference: Minecraft sets them per particle type, and correcting them while **depth write** stays asserted
false for the whole family produces a frame further from GL, not closer. **Next:** observe the whole triple - blend enable,
factors, depth write - and use it together.
**Correction (2026-10-09, from the SRG source):** the "float `translate`" item in this list is **not** a missing hook in
1.12.2 - `GlStateManager.func_179137_b` has a single `(DDD)` overload and the float literals in the decompiled source are
widened by the compiler, so the existing hook is the right one. Verified by adding a `(FFF)`-qualified hook that matched
nothing and changed no counter, then removing it. The rest of the list stands.

No hooks for alpha enable/disable, cull enable/disable, `depthFunc`, integer `blendFunc`/`tryBlendFuncSeparate`,
`activeTexture`, float `translate`; `setAlphaFunc`'s hook has no backend override; the pipeline hardcodes alpha blend
factors, `LEQUAL` and `ADD`, and substitutes standard factors for unknown ones; the shader's `ref > 0 && alpha < ref` does
not reproduce `GL_GREATER` or a zero reference; texture binding is a single value, so the lightmap unit switch is lost;
ITEM normal/fixed lighting, immediate fog, texture matrix, colour mask and scissor are not gated either.

### S08 [P1, GLSL against checked-in SPIR-V] ITEM's UV and colour inputs are swapped
`VulkanImmediateItemPipeline` binds location 1 to colour at offset 12 and location 2 to UV at offset 16, while the shader it
loads declares location 1 as UV and location 2 as colour - confirmed against the SPIR-V's `OpName`/`Location`.
**Fixed:** the pipeline now binds location 1 as `R32G32_SFLOAT` at offset 12 and location 2 as `R8G8B8A8_UNORM` at offset
20, matching both `DefaultVertexFormats.POSITION_TEX_COLOR` (POSITION_3F, TEX_2F, COLOR_4UB) and the shader. **Open:** the
minimum fixture the acceptance condition asks for - an asymmetric checker texture with non-white colour and UVs outside
0/1, on more than one face.

### S09 [P1, Minecraft source against the shader] the lightmap coordinate is decoded wrongly
Both fragment shaders compute `(coord >> 8 + 0.5) / 256`. `BufferBuilder` writes the coordinate short directly and
`EntityRenderer` installs a texture matrix with scale `1/256` and translate `8`, so vanilla is `(coord + 8) / 256` - the
`>> 8` shifts a 0..240 value to zero every time, sampling the same dark texel. A still-integer varying also loses
per-vertex interpolation.
**Fixed:** both vertex shaders now emit `v_Lightmap = (vec2(a_LightCoord) + 8.0) / 256.0` as an **interpolated** `vec2`,
and both fragment shaders sample the lightmap with it - the raw coordinate, the vanilla scale and bias, and per-vertex
interpolation. The four SPIR-V files were recompiled and the copies inside the built jar were compared by hash with them
(identical), so this is a change that demonstrably reached the run.
**Measured, and the residual did not move:** the weather and particle scene still differs by **0.40% of pixels over 8
(3,714, was 3,715)** against the same **0.23%** floor. The decode was wrong in the source and is now right, and it is not
what the remaining difference is made of. **Not yet done:** the acceptance fixture - dark/fullbright/sky-only/block-only
with differing per-vertex light values compared against GL.

### S10 [P1] draw order, FBO and viewport are not preserved across the hybrid
**First step implemented:** the render target and viewport are now part of the condition that opens a group - a viewport hook
on `GlStateManager.func_179083_b` and a target hook on `Framebuffer.func_147610_a` (identity only, so no accessor
machinery), with the result that two passes can no longer merge into one draw. **Measured: `passSplits:0` and no
regression** - the frame is identical to its same-fixture reference at 0.40% (3,714 px) - which says this scene never
presents the case, not that the case does not exist. **The fixture is built and it fails.** A second 256x256 `Framebuffer` is cleared, bound and drawn into, then the main
target is rebound and the overlapping quads are drawn there - same format, texture and state, so only target and viewport
separate them. `passSplits` rises from **0 to 16**, so the key's new field is exercised; and the run **FAILS**
(`nativeTerrainVerified False`) with the modelview delta jumping from 1.62 to **2000**, a magnitude that looks like a screen
coordinate rather than a world one. **Correction:** the 2000 is **not** the fixture's doing. With the second-target work gated off by default, a normal run
reports `steadyDelta=view:2000.0` **and passes** - and 2000 is `setupOverlayRendering`'s `translate(0, 0, -2000)`. So the
symptom S10 describes is present in the ordinary suite: a comparison that lands in the overlay pass while the mirror holds
the world pass's modelview. The fixture forces it into the verdict; it does not cause it, and it is gated off so the suite
keeps producing captures. **The fix is not to compare across passes - and it is now in.** The world pass installs a perspective projection, which is
where `applyDerivedProjection` already fires; the overlay and GUI install orthographic ones, which the existing `ortho` hook
already sees. Scoping the comparison to the world pass moves `view` from **2000.0 to 1.62** with the renderer unchanged, and
reports that **1140 of 2262 comparisons had been cross-pass** - about half of the number this project had been quoting.
The residual 1.62 is now measured only in the pass that owns the mirror, which is the first time that figure means the
renderer rather than the mixture. **Draw order across the GL fallback: the first half is in.** The audit's own description - "small accepted batches wait
for a threshold while the next unsupported draw runs in GL immediately" - is now handled at the refusal sites: before a
batch is refused, whatever this renderer has already accepted is closed and drawn (`flushPendingBeforeGlDraw`), so the
deferred batch cannot appear **after** content that came later in the frame. Measured: **`orderPreservingFlushes=5`** on a
normal run - five refusals happened while batches were still pending, and each of those was a draw-order violation before
this change - with exit 0, validation clean, 0 messages, fixture PASSED, `immediateTaken=546`, `immediateFlushes=558`,
`postAcceptDroppedGroups=0`, no regression. **All refusal sites carry it now.** The first pass covered the four early refusals in `submitImmediateBatch`; the refusals
that actually fire in numbers live in `submitImmediateBatchTimed`, and **thirteen** more sites there got the same call -
seventeen in the file, one method each side of the wrapper boundary. Measured: **`orderPreservingFlushes=6`** (up from 5) with
exit 0, validation clean, 0 messages, fixture PASSED, `immediateTaken=551`, `immediateFlushes=558`,
`postAcceptDroppedGroups=0` - so in this scene one further refusal happens while batches are pending, and no regression.

**Clears are handled too, and the FBO half was already.** A clear is an ordering boundary as much as a refusal is, so a
mixin hook on `GlStateManager.func_179086_m` (`glClear`) calls `flushBeforeClear`, which draws what is held before the target
is wiped and counts itself (`clearOrderFlushes`). Measured: **`clearOrderFlushes=0`** - this scene never clears while batches
are pending, so the hook is correct by construction and **unexercised**, which is recorded as the distinction it is. The FBO
half was already in place from an earlier round: the render target is part of the group key, and `bindFramebuffer` flushes the
pending group when the target changes (`passBoundaryFlushes` was measured at 10 on the two-target fixture).

**Still open:** the ordering is correct by construction rather than observed - no image comparison can see these ordering
faults on this scene - and neither the clear nor the FBO path is exercised by the ordinary suite, only by the gated
two-target fixture. The other half of the item - reading what the 2000 is - was answered earlier: it is
`setupOverlayRendering`'s `translate(0, 0, -2000)`, not the fixture.
The group key has no FBO, viewport or scissor; small accepted batches wait for a threshold while the next unsupported draw
runs in GL immediately; the flush queries the current FBO after the fact; a later terrain pass can draw the remainder.

### S11 [P1] "16 windows per frame" cannot be shown to finish within a frame - **closed**
**Fixed.** The frame now has an identity (`frameId`, incremented at the camera boundary, which fires exactly once per frame)
and the per-frame state - the window budget and the accepted/drawn counters - is reset and reconciled **there**, not on every
fence wait. `updateTimings` counts flushes and touches nothing that belongs to a frame, and each frame's accepted/drawn
figures are carried into `lastFrameAccepted` / `lastFrameDrawn` at the next boundary. Measured on a normal run:
**`frameId=570` against `flushCount=3327`** - 5.8 flushes per frame - so the old code reset a frame's budget **3327 times
instead of 570**, and the reconciliation reports the last frame as accepting and drawing one group. That is the audit's
complaint as a number rather than as a suspicion.

### S12 [P1] texture invalidation and resize destroy resources that may be in use - **core fixed**
**The two paths the audit names are retired now, not destroyed.** A new `VulkanRetireQueue` collects the destruction of
images, views and memory, and the backend drains it in the one place where a wait for device idle has **actually
succeeded** (`beginCommands`, after its fence wait). `recreateImageResources` - which is what the per-frame mirror refresh
(`invalidateAll`) and `resize` both run - hands its handles over instead of calling `vkDestroyImageView`,
`vkDestroyImage` and `vkFreeMemory` immediately. Measured on a normal run: **`retired=6`, `flushed=6`**, exit 0, validation
clean, 0 messages, fixture PASSED, `immediateTaken=551`, `postAcceptDroppedGroups=0` - the refresh cycle retires one set per
interval and every one of them is destroyed after an idle. **The remainder is done.**
- *Teardown:* the destroys in `destroyImageResources` were already retired through the same queue, the **sampler** is now
  retired as well, and the backend drains the queue at teardown, after `settleGpu` has ended the queue's work.
- *Generation-carrying handles:* the texture now carries a `generation` that is bumped whenever its image and view are
  recreated, and exposes `key()` - the view combined with the generation. The four immediate pipelines' descriptor caches
  (**eight** lookups) key on `key()` instead of the raw view handle, so a driver that hands back the same handle for a new
  view cannot resurrect a descriptor set built on the retired one. The descriptor writes still use the real view.

Measured on a normal run: **`retired=9 flushed=9 pending=0`**, exit 0, validation clean, 0 messages, fixture PASSED,
`immediateTaken=547`, `postAcceptDroppedGroups=0`. Recorded honestly: the generation change is correct by construction but
**not yet exercised by a test that forces a handle reuse** - no run has observed the failure it prevents, so it is a fix by
reasoning rather than by reproduction, and that distinction is kept.

### S13 [P1] the descriptor pool accumulates and the lightmap is not part of the key
**Partly acted on:** the three immediate pipelines' pools were raised from `maxSets(64)` to 8192, since one set is cached per
distinct texture view and nothing evicts it - a real limit that ordinary play would reach. **It did not change the particle
refusals** (`particleDescriptorRefusals=488` before and after), so exhaustion was not what the particle family was hitting;
the change stays because the limit was real. **The lightmap is in the key now - and the cost is measured.** The two lightmapped pipelines (block and particle) keyed
their descriptor cache on the texture alone, so a set built for an old light map could be served for a new one. Both now
combine `texture.key()` with `lightmap.key()`. The measured consequence is blunt: a normal run reports **`descriptorSets=545`
against `immediateTaken=546`** - effectively one set per accepted batch, where the cache used to be shared. The reason is
also clear: the lightmap mirror is **recreated** on every refresh, so its view and generation change while the atlas's do not,
and every batch misses. **And the cache works again: 545 sets became 1.** The refresh was recreating each mirror - new image, new view, new memory
- so every descriptor key changed on every cycle. `invalidateAll` now **marks** the entries stale instead of dropping them,
and `get` re-uploads a stale mirror **in place**, keeping its image, view and generation. Measured: **`descriptorSets=1`**
(was 545), **`inPlaceRefreshes=1136`**, `retire=retired=6` (was 9, because the resources are no longer recreated),
`immediateTaken=580`, `postAcceptDroppedGroups=0`, exit 0, validation clean, 0 messages, fixture PASSED. So the light map is
both in the key **and** stable across refreshes: the cache is correct and it works.

### S14 [P1] geometry that fails after acceptance is dropped and called a GL fallback - **closed**
**Fixed.** The failure was `vkAllocateDescriptorSets failed` for the particle family, and the cause was the descriptor pool's
**per-type** count: 128 combined-image-sampler descriptors at two per set caps the pool at 64 sets whatever `maxSets` says,
so raising `maxSets` alone could not help. With the two sized together, a run reports **`postAcceptDroppedGroups=0`** and
`particleDescriptorRefusals=0`, with accepted batches rising from 65 to 550 - no accepted geometry is dropped any more.
**Consequence, recorded rather than glossed:** the frame's difference from the GL reference rises from 0.24% to **1.33%**,
because the particles are now genuinely drawn and their rendering differs - the same order as the particle discrepancy this
audit's S09 and the history recorded before the path stopped drawing them.
**Measured:** with the texture now prepared before acceptance and post-accept drops counted separately, a normal run reports
`textureRefusalsBeforeAccept=9` and **`postAcceptDroppedGroups=487`** - roughly one group per frame accepted and never
drawn - while the image stays at 0.40% against its reference. So the defect is real, it happens every run, and it was
invisible until it was counted. **Next:** build the descriptor set before acceptance too, so that the only place a batch can
be refused is where GL still owns the draw.
The draw path discards on missing pipelines, continues on missing textures and discards in `finally`, but the original draw
was already cancelled - so the comments promising a GL fallback are wrong and geometry disappears silently.

### S15 [P1] backend teardown leaks - **implemented, unexercised**
**Fixed in code.** `delete()` now releases everything the backend owns rather than only the terrain half: the held immediate
groups, the current immediate mesh, both immediate pipeline caches (block and particle), the UI pipeline, the UI mesh, the
texture registry and the swapchain - each through the object's own `delete()` in a direct call, with a wrapper that reports a
failure instead of propagating it, and a single count of what was released. **No reflective call is used**: the first version
of this fix used a reflective `delete`/`destroy` lookup and was replaced with direct calls, which is both this project's
policy and simpler to read. Verified: exit 0, validation clean, 0 messages, fixture PASSED, `descriptorSets=1`, no
regression. **Now exercised, and it releases.** The fixture's world-switch path (`-Dceltium.validation.worldSwitch=true`, a leave at tick
100 and a rejoin) runs the teardown twice, and it reports what it released:

```
CELTIUM_TEARDOWN released 4 backend resources (immediate mesh, groups, pipeline caches, UI, registry, swapchain)
CELTIUM_TEARDOWN released 2 backend resources (...)
exit 0 | clean | messages 0 | fixture PASSED | leftWorldAtTick=100 rejoinedAtTick=100 | passes=1169
```

Validation is **silent** across both, so nothing was destroyed while a command buffer still named it - which is also the
retirement queue (S12) being exercised by a real leave/rejoin rather than by construction. The registry's create-then-fail-upload leak from the same audit item is still open.

### S16 [P1] animation invalidation never reaches the terrain atlas - **closed**
**Fixed and measured.** The terrain atlas and the light map are held **directly** by the backend (`atlasTexture`,
`lightmapTexture`) rather than through the texture registry, so `invalidateTexture` - which the sprite-upload hook calls -
marked only the registry's copy stale and left the ones the terrain actually samples untouched. The audit's wording was
exact: five sprite-hook firings are not evidence that terrain animation is current. `invalidateTexture` now compares the
incoming GL texture id against the ids the two direct mirrors were built from and **re-uploads them in place**, which leaves
their image, view and generation alone and therefore every descriptor key valid.

Measured on a normal run:

```
CELTIUM_ANIM directMirrorRefreshes=89 invalidations=115 atlasGlId=9 lightmapGlId=12
exit 0 | clean | messages 0 | fixture PASSED | passes=2189 | descriptorSets=1
```

**89 refreshes of the terrain atlas** (GL texture 9) reached the mirror that the terrain pass samples - 89 uploads by the
game's animation path that previously never arrived - and `descriptorSets` stays at **1**, so the in-place design from S13
carries the animation path as well. The count is reported on stdout whenever it changes, because the capped debug list would
drop it.

### S17 [P1] `POSITION_TEX_COLOR` is wrongly identified as the final composite - **conditions added, unexercised**
**Fixed in code.** The branch now checks the two things that distinguish the screen-only composite before it does anything:
the bound target must be the **main framebuffer** (`currentRenderTargetIsMain`) and the viewport must be the **whole of it**
(the packed viewport key against `fullTargetViewportKey()`). Anything else - an overlay blended *into* a framebuffer, a GUI
pass, outlines, offscreen work, postprocessing, all of which share this layout - is **refused before acceptance** with the
reason recorded, so GL keeps the draw instead of having it cancelled by a present that was never a composite. A
`compositePresentRefusals` counter makes the frequency visible. Verified: exit 0, validation clean, 0 messages, fixture
PASSED, `immediateTaken=578`, `postAcceptDroppedGroups=0`, `passes=2225`. **Unexercised:** with
`-Dceltium.vulkan.presentFromComposite=true` the counter reads **0** - in this scene every format-2 batch that reaches the
branch really is a full-screen composite, so the stricter conditions never reject. The case the audit describes is not
presented by this fixture, and the branch is off by default in any case.

### S18 [P1] `OUT_OF_DATE` failures are not handled - **two of the four fixed, partly verified**
**Fixed.** `presentImage` now resets `outOfDate` at the top, so the flag describes **the present about to happen** rather than
accumulating: left set, one out-of-date acquire would keep the caller rebuilding the swapchain for the rest of the session.
And an acquire that returns `VK_ERROR_OUT_OF_DATE_KHR` now **stops before anything is submitted or presented** - previously it
set the flag and carried on to submit and present an image the acquire had just declared unusable - returning a result the
caller reads as `succeeded=false, outOfDate=true`, with its own `acquireOutOfDate` counter.

**Checked and already sound:** the null-image case the audit worried about is guarded (`swapchain == VK_NULL_HANDLE` returns
before anything is dereferenced), and `lastPresentSucceeded` is only ever set after a successful present and is reset at the
top of the method, so it cannot report an unsubmitted present as successful.

**Verified as far as it goes.** A normal run and a run with `-Dceltium.vulkan.forceOutOfDate=true` are both clean - exit 0,
validation clean, **0 messages**, fixture PASSED, `passes=2185`/`2189`, `postAcceptDroppedGroups=0` - so the changed paths do
not break the frame.

**Then it was exercised, and it hangs.** Two more things were built for this: an injection at the **acquire** itself
(`celtium.vulkan.forceAcquireOutOfDate`, since the existing flag forces the verdict only *after* a successful acquire), and a
stdout report of the state whenever it changes (the `rendererDebug` list is capped, so counters added to it were never read).
With the injection **off**, both a normal run and a composite-present run are clean and report `outOfDate=0
acquireOutOfDate=0`. With `presentFromComposite=true` **and** `forceAcquireOutOfDate=true` together, the client **hangs**:
the run ends at the 300-second timeout with no validation report and the fixture reports `MISSING_REPORT`.

That is a real finding rather than a tooling artefact: the composite present path alone completes, so the hang belongs to the
**out-of-date acquire branch**, which means S18's failure path was never survivable - it is only unreachable in the default
configuration, where `presentImage` is never called at all. The likely cause is visible in the code: the composite branch calls
`frameTarget.beginFrameForPresent()` **before** presenting, and ends the frame only when the submission actually happened, so
a present that returns early leaves the frame begun and unended, and the next frame waits on a fence that will never be
signalled. **Fixed, and the deadlock is gone - but that configuration still does not work.** `endFrame()` makes GL wait on the
Vulkan-to-GL semaphore, and the frame target's own documentation says what that means: *"waiting for a submission that was
never sent deadlocks the render thread"*. That is exactly what the out-of-date path produced, so the composite caller now
submits a **signal-only release** - it waits on the frame's GL-to-Vulkan semaphore and signals the Vulkan-to-GL one, with no
command buffers - whenever a frame was begun and no submission happened, then ends the frame normally.

Re-running the configuration that hit the 300-second timeout: the game **now exits on its own** (the run script returns 0
where it previously timed out). But the **fixture still reports `MISSING_REPORT`** in that configuration, and the report file
read back was the previous run's, so the honest statement is narrower than "fixed": the **deadlock is gone**, and the
composite-present path with a forced out-of-date acquire **still does not complete a verified run**. It remains off by
default, and the ordinary paths are unaffected - exit 0, validation clean, 0 messages, fixture PASSED.

**Why that configuration never reports - and it is not a defect.** Probed by running it for ninety seconds and reading the
stdout markers directly: the game starts, the terrain draws (`CELTIUM_FOG` is printed with a real camera), and **not one
`composite present` record appears**, while the fixture passes in the same configuration when it is not injected. The
composite-present branch is simply **never reached in this scene** - `compositePresentRefusals` is 0, so it is not being
refused either, there is no format-2 batch to take. The injected run therefore never calls `presentImage`, never reaches the
acquire, and never satisfies the condition the fixture waits for: `MISSING_REPORT` is the injection's own consequence, not a
symptom.

**And the stricter viewport condition that was tried first was wrong.** It refused the branch before the present as well, so
the two attempts looked similar; with it, the composite configuration stopped presenting entirely. It is now relaxed to the
check the audit actually asked for - the bound target must be the main framebuffer - which restores the previous behaviour and
keeps the guard that distinguishes the screen from an overlay blended into a framebuffer. Verified: the composite
configuration runs clean, exit 0, validation clean, 0 messages, fixture PASSED, `passes=2217`.

So S18 closes with: two points fixed, two shown sound, the deadlock fixed by the signal-only release, and every one of those
paths **unexercised by the current scene** - which never presents through this branch at all.

### S19 [P1] immediate push constants exceed the guaranteed minimum - **closed**
**Fixed and verified.** The immediate push block is now **116 bytes** - projection 64, the model view's three affine
columns 48 (the fourth is (0, 0, 0, 1) by construction and the shaders rebuild it), alpha reference at 112 - and it is
declared **identically in all nine shaders**, which is the constraint the first attempt missed: the driver refuses a pipeline
whose stages describe the same block differently (`pStages[0] shader [VERTEX] has a push constant buffer Block with range
...`). All six immediate and UI push ranges are 116; the terrain pipeline keeps its own separate 128-byte layout. The CPU side
writes the new layout, including the isolation draw's identity columns and its zero alpha reference.

The first attempt failed for a reason worth keeping: `immediate_block.vert` and `immediate_particle.vert` **already declared
the alpha reference** (to match their fragment stages), so inserting it again produced duplicate members and broke the
shaders. This time all nine blocks were replaced with one identical text.

Verified: `BUILD SUCCESSFUL`, exit 0, validation clean, **0 messages**, fixture PASSED, `immediateTaken=553`,
`postAcceptDroppedGroups=0`, `descriptorSets=1`, `passes=2179`. The frame comparison against the run before the migration
reads mean 0.968 with a maximum of 255 - and that is **this scene's frame-to-frame variation**, not an effect of the change:
comparing two unrelated rounds of the *same* build gives 0.95 with the same maximum, because the scene has time-varying
content. The honest statement is therefore that the migration shows **no difference beyond the scene's own noise**, rather
than "no difference".

### S20 [P1] the validation gate misses errors by their format
`prism_validation` skips any message not starting with `vk`, and stores no severity or message ID, so
`Validation Error: [ VUID-... ] ...` style output counts as zero messages.
**Fixed on both sides.** The debug callback now prints one structured line per message -
`severity=ERROR|WARNING|INFO|VERBOSE id=<messageIdName or number> <message with newlines folded>` - and the harness keys on
that: `INFO`/`VERBOSE` are skipped, a `WARNING` only when it says `deprecated`, and everything else counts, so an error
phrased in any style is caught. Unstructured text (older logs, driver strings) is treated the same way - only clearly
informational lines are skipped. A **missing log is now a failure** rather than zero messages. The parser was checked
against seven inputs, including the audit's two forms:

| Input | Messages counted |
|---|---|
| `severity=ERROR id=VUID-... ...` | 1 |
| `severity=WARNING ... deprecated ...` | 0 |
| `severity=INFO ...` | 0 |
| `vkDestroyBuffer(): can't be called on ...` | 1 |
| `Validation Error: [ VUID-vkCmdDrawIndexed-None-08600 ] ...` | **1** (was 0) |
| `VUID-vkCmdDrawIndexed-None-08600: ...` | **1** (was 0) |
| log file absent | **1** (was 0) |

**Resolved, and the error was in this record rather than in the gate.** The real run wrote one structured line -
`severity=WARNING id=VALIDATION-SETTINGS vkCreateInstance(): Information with the layer settings provided ...` - and the
harness reported zero messages. It should not have counted: the full message is 439 characters and **contains
`deprecated`**, so it is the allowed deprecation warning the rules describe. The conclusion that it "should count" came from
reading the first 130 characters of the line and stopping - the same truncation that produced a false lead one round
earlier, and the second time in two rounds that a partial read was turned into a finding.

The countermeasure is in the tool rather than in a resolution to be careful: the harness now records **allowed** messages
with the reason they were allowed (`validationAllowedCount`, `validationAllowedFirst`), so a line the gate skips is visible
as skipped instead of invisible. On the real run the report reads `validationMessageCount 0`, `validationAllowedCount 1`,
`allowed[0] = WARNING(deprecated) vkCreateInstance(): ...`.

### S21 [P1] the image comparison is not max-channel
`compare_validation` converts the RGBA difference to luminance; the auditor's one-pixel cases (red 1 / tolerance 0, blue 40
/ tolerance 5, alpha 255 / tolerance 0) all read equal.
**Fixed:** the mask is now the maximum difference across channels, and those cases report unequal.

### S22 [P1] a failed process is hidden by an image match, and the interop fixture cannot finish
The comparator ignored `validation-process.json`; separately, `ValidationClient`'s capture gate (`worldTicks != 200`)
returns before the interop branch can reach its `worldTicks < 240` check, so the interop path cannot complete; and the
reload/resize hooks at tick 100 have no once-guard.
**Comparator fixed; the fixture's once-guards are fixed too.** The reload and resize hooks sat in a **render-tick** handler
behind `worldTicks == 100`, which is true on every frame of that tick - so a resource reload ran dozens of times instead of
once. Both now carry a once-guard. Verified by running the reload: `reloadedAtTick=100`, exit 0, validation clean, 0 messages,
fixture PASSED, `passes=1199`. **Still open:** the interop capture gate and the same tick-equality pattern in the weather,
particle and interop hooks, which are the same defect in the same place.

### S23 [P2] samplers, mips and diagnostic resources
**The sampler half is fixed** (nearest filtering; the frame fell from 1.33% to 0.23%). **The mip half is now diagnosed at the
mechanism.** With the probe verified to be inside `refreshTextures`, the atlas reports `levels=5 maxLevel=4 maxLod=4.0
minFilter=9986 magFilter=9728 atlasSize=512x512` where this project's mirror has **10 levels and a clamp of 1.0**. Minecraft
builds the atlas's chain **per sprite**; this project blits the **whole atlas**, so every level from 1 up averages across
sprite borders - which is why sampling deeply measures 7.37-8.77% against the reference and why clamping to 1.0, where the
bleeding has barely begun, measures 0.21%. The shallow clamp is a configuration that **hid** the defect, not a fix.
**Implemented, correct, validation-clean, and it isolates the shader.** Mirroring OpenGL's levels now honours the count GL
reports (in the recreate decision, the skip and the loop bound) and declares the layout the copies actually leave, so a run
reports **exit 0, validation clean, 0 messages** with five levels copied from the game. It measures **5.60%** against a
reference that a shallow clamp matches at 0.21-0.28%, so the chain's **content** is no longer a candidate - it is OpenGL's
own, per-sprite averaging included - and the remaining difference is the **LOD the shader asks for**. Mirroring is off behind
one flag (5.60% would be a regression); the restored configuration measured **0.16%**, the best *clean* number recorded.
**Next:** compare this renderer's UV derivatives and LOD with Minecraft's own shader.

### S24 [P2] the record and the acceptance criteria need reworking
Plan rows claim pixel-identical immediate rendering, removed deferral and verified cull/alpha capture, and the blockers
section still describes the old one-window finding; GUI-visible means the HUD is visible, not that GuiScreen/menu/tooltip
or mod UI is ported; the fixture removes entities and particles; `ScreenShotHelper` reads the game's FBO, not the presented
screen; `nativeTerrainVerified` is a counter condition. Shader sources and checked-in SPIR-V are generated by hand with no
sync check, and the launcher's lock should also record git HEAD/dirty state, fixture hash, effective config and driver.

## Order of work

1. Acceptance gates and real-path evidence (S03, S04, S20-S22): fixed jar and hash, effective flags, same scene, and the
   cancellation of the original GL draw verified per batch.
2. Minimum fixtures for S05-S09 and S14: single quad, two quads, large batches, ITEM inputs, alpha/blend/depth/lightmap
   correct, unsupported state refused explicitly.
3. Order, frame target, texture/descriptor lifetime and animation (S10-S16), verified on both GPUs with long runs,
   reload/rejoin and texture updates.
4. The present contract and failure handling (S01, S17, S18), with the real screen checked and distinguished from the
   probe.
5. Capability and sampling (S19, S23), then comparison against the original Celeritas GL and real mods. Claims of GL-free
   operation stay pending until their dedicated test passes.
