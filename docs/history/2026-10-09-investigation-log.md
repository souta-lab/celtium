# Investigation log, 2026-10-09

The chronological record behind the current plan: what was measured, which hypotheses were disproved, and the known
defects as they stood. `docs/plan.md` keeps the conclusions and links here for the evidence. Nothing in this file is a
current instruction; where it disagrees with the plan, the plan wins.

Sections: status detail at the time of writing, the hot-path measurements, the two failed mid-frame window attempts,
and the matrix-mirror measurement.

---

## Status detail (as of 2026-10-09)

Kept separate on purpose - terrain success and present success are different things.

**Implemented and verified on hardware (AMD RX 6400 and Intel UHD 630):**
- Vulkan terrain backend: meshes from Celeritas, arenas, pipelines, per-pass handoff, draw counters, F3 timings.
- Per-pass GL/Vulkan handoff with clean synchronization validation; frame parity with the OpenGL reference on a fixed
  scene; audit items P0-1, P1-2, P1-3, P1-4, P2-5, P2-6 fixed.
- The first non-terrain Vulkan draw: a textured quad through `VulkanDynamicMesh` + `VulkanUiPipeline` (test flag
  only, 36864 pixels changed against the same build without the flag).
- **Native Vulkan presentation**, as a probe (`-Dceltium.vulkan.swapchainProbe=true`): a `VkSurfaceKHR` on the real
  window (X11/Xwayland), a swapchain (BGRA8, mailbox, 4 images, 1280x720, `VK_KHR_swapchain` enabled at device
  creation only for the probe), and an acquire-clear-present cycle that returns `VK_SUCCESS` with **no validation
  message** under synchronization validation and no hang. GL still presents the game's frames in this hybrid build;
  what is verified is the Vulkan path itself.

**Implemented, not yet GPU-verified:**
- `VulkanDynamicMesh` (dynamic vertex/index streams), `VulkanTexture2D` (upload, mips, sampler),
  `VulkanImmediateDrawer` (topology translation, 6 unit tests), `VulkanGlState` (state model and pipeline key,
  5 unit tests).
- `VulkanSwapchain`: surface creation, capability query, present-support check, acquire/clear/present code path.

**Not implemented:**
- Present ownership, swapchain image usage for real frames, Minecraft framebuffer replacement, GUI/font/HUD, sky,
  clouds, weather, particles, entity/TESR/item/hand, vanilla shader paths (`ShaderGroup`), display lists, texture
  animation for non-terrain textures, resource reload, resize/world switch, GL-context-free startup.
- The Forge/Minecraft API swap: the Mixins and the deferred pipeline cache that would consume `VulkanGlState` and
  `VulkanImmediateDrawer`.

**Blockers / open defects:**
- Resolved: the swapchain `NullPointerException` was LWJGL's capability check on the *function pointer*, because
  `VK_KHR_swapchain` is a **device** extension and the probe only enabled the instance surface extensions. With the
  device extension enabled and the create-info back to spec-conformant forms (exclusive sharing, null allocator,
  persistent acquire semaphore), the present cycle succeeds. Two of my own intermediate "fixes" were the actual
  faults on the way: concurrent sharing with a single queue family
  (`VUID-VkSwapchainCreateInfoKHR-imageSharingMode-01278`) and a zeroed `VkAllocationCallbacks`
  (`VUID-VkAllocationCallbacks-pfn*`), the latter crashing the client.
- **Mixin state capture is live and verified on hardware.** The `GlStateManager` Mixin injects at the verified SRG
  names (`func_179147_l`/`func_179084_k` blend, `func_179132_a` depth mask, `func_179097_i`/`func_179126_j` depth
  test, `func_179131_c` colour, `func_179094_E`/`func_179121_F` matrix push/pop, `func_187428_a` blend factors) with
  `require = 0` on every injector, and forwards into the backend's state model. A fixture run reports
  `immediateState=103825`: vanilla's own drawing-state calls are captured and translated into Vulkan renderer state,
  with no public Minecraft signature changed and no effect on the rendered frame (PASSED, no validation message).
  `immediateOffered=0` is expected - the draw entry is not wired yet. The earlier failure was a wrong SRG name
  (`func_179146_i`, which does not exist; the real disable-blend is `func_179084_k`), and `require = 0` now makes that
  class of mistake degrade instead of stopping the client.
- **Mixin draw entry is live too.** A Mixin on `WorldVertexBufferUploader.func_181679_a` - the single draw entry of
  Minecraft's immediate path, where `Tessellator.func_78381_a()` and every `BufferBuilder` renderer end - offers each
  finished batch to the backend. Verified on hardware: `immediateOffered=8365` with
  `immediateRefusal=no_pipeline_yet_for_VertexFormat`, i.e. the batches really arrive and are refused with a stated
  reason while the original GL body stays in charge (PASSED, no validation message, frame unchanged). The buffer's
  private fields are read reflectively so no Minecraft signature changes and the byte buffer object is untouched.
- **Stage 4/5 verification assets.** `docs/test-worlds.md` records candidate exhibit worlds for the breadth
  checklists, with the rule that they run in isolated instances and never replace the deterministic parity fixture.
  The first entry is QMAGNET's Test Map (shared as reference information, authored for 1.12/1.12.1, **not verified on
  1.12.2/Cleanroom, not downloaded**), which exhibits blocks, mobs, items, particles, biomes and a day/night toggle -
  the content classes stages 4 and 5 are about.

- **Stage 5 capture evidence (mod-style drawing in a Forge event).** The validation fixture now has a
  `RenderWorldLastEvent` handler that draws through `Tessellator`/`BufferBuilder` and `GlStateManager` exactly as a
  content mod does, with no Celtium-specific call, gated behind `-Dceltium.validation.drawTestQuad=true` so reference
  frames are unaffected. Verified on hardware: `modDrawTestFrames=574` while `immediateOffered` rises from 8365 (the
  baseline run) to **9078** and `immediateState` from 103691 to 106058 - the mod-style batches and state calls really
  reach Celtium's Vulkan entry points, are refused with a stated reason (the per-format pipelines are missing) and are
  drawn by GL as before, with the run clean (PASSED, no validation message). This is the "(a) binary compatibility"
  half of the stage-5 evidence: a Forge-API mod is captured without being changed; the "(b) drawn by Vulkan" half
  needs the pipelines below, and real major mods come after that.

- **Every vertex layout vanilla's immediate path actually uses now has a working Vulkan pipeline.** Added the other
  two 28-byte families: `ITEM` (position, colour, texture, normal, padding - the normal is part of the stride but not
  of the vertex input description, because the shader does not read it) and the particle layout (the same fields as
  `BLOCK` in a different order, hence its own vertex shader). The Mixin identifies both structurally by element list,
  and the fixture now draws all three 28-byte layouts so acceptance is measured instead of assumed:
  `immediateOffered=10010`, **`immediateTaken=2844`** (from 1719 with only the block quad), `immediateFlushes=2840`,
  `immediateRefusal=none`, **VUID 0**, PASSED, `nativeTerrainVerified True`.
  Coverage today: `POSITION_COLOR` (16), `POSITION_TEX_COLOR` (24), `BLOCK`, `ITEM` and the particle format (28) -
  which is what vanilla's `Tessellator` path uses for entities, items, the hand, particles, the sky and the GUI.
  Still not visible in the frame for the architectural reason recorded earlier, and still not claimed as correct.

- **Original note on the BLOCK family (the other layouts followed).** Both defects the validation layer reported are fixed:
  the lightmap attribute is declared `uvec2` in the shaders to match `VK_FORMAT_R16G16_UINT`
  (`VUID-VkGraphicsPipelineCreateInfo-Input-08733`), and the integer varying is `flat` on both sides
  (`VUID-RuntimeSpirv-OpEntryPoint-07754`). Measured with the fixture drawing a `BLOCK` batch each frame:
  `modDrawBlockFrames=569`, `immediateOffered=8900`, **`immediateTaken=1719`** (from 1161 without it),
  `immediateFlushes=1717`, `immediateRefusal=none`, **VUID 0**, PASSED, `nativeTerrainVerified True`. The layout that
  most vanilla immediate content actually uses - blocks in the hand, items, the block atlas path - now has a working
  Vulkan path with its lightmap sampling.
  Not yet visible, and not claimed: the batch's pixels do not appear in the frame, which is the same architectural
  mismatch recorded above (drawn against the next frame's buffers with the previous frame's matrices), so the
  acceptance is verified while the *image* is not. The remaining 28-byte layouts - `ITEM` (position, colour, texture,
  normal, padding) and the particle format (a different element order) - still need their own vertex layouts.

- **Original note (the acceptance measurement that led here).** The fixture now draws a `DefaultVertexFormats.BLOCK` batch (gated behind
  `-Dceltium.validation.drawBlockQuad=true`), which made the question answerable: `modDrawBlockFrames=567`,
  `immediateOffered` rose by ~534 and **`immediateTaken` rose from 1161 to 1709**, so those batches are accepted and
  drawn - the layout identification and the acceptance path work. One validation message appeared, and it names the
  remaining defect exactly: `VUID-VkGraphicsPipelineCreateInfo-Input-08733`, because the pipeline declares the lightmap
  attribute as `VK_FORMAT_R16G16_UINT` while the vertex shader takes a float `vec2`. Acceptance is therefore off again
  with that reason recorded in `immediateRefusal`, and the fix is to declare the attribute as `uvec2` in both shaders
  (the fragment shader already treats it as an integer) and re-measure.
  The same run also closed an open question from earlier rounds: the client logs
  `@Mixin target org/lwjgl/util/glu/Project was not found` and `Error loading class: org/lwjgl/util/glu/Util`, i.e. the
  GLU class **does not exist at runtime at all** - which is the concrete reason the projection mirror never fired
  (`matrixChannels=persp:0`) and why the backend reads the live matrices instead. That Mixin was dead weight that
  warned on every start, so it is removed.

- **Original note on the dominant layout (the pipeline is now in place).** Reading
  `DefaultVertexFormats` in order shows what the coverage actually needs: the 24-byte `POSITION_TEX_COLOR` implemented
  earlier is a rare format, while the immediate content that dominates vanilla - blocks in the hand, items, the block
  atlas path, particles - uses **28-byte** layouts, `BLOCK` (position, colour, texture, lightmap) and its relatives.
  A `VulkanImmediateBlockPipeline` now exists for the `BLOCK` layout: 28-byte vertices, a vertex layout that mirrors the
  format exactly, and two descriptor bindings so the fragment shader multiplies the sampled texture by the vertex
  colour and the **lightmap** image the terrain path already mirrors. The Mixin identifies it structurally as format id
  3 by comparing the element list against `DefaultVertexFormats.BLOCK`, the backend selects the new pipeline by that id
  and refuses with a reason if the lightmap mirror is missing, and the run stays PASSED, `nativeTerrainVerified True`,
  **VUID 0**.
  Honest limitation: **no `BLOCK`-family batch was observed in the fixture scene**, so `immediateTaken` did not change
  and the new path's acceptance is **unverified** - the scene apparently contains no hand-held or item-rendered
  geometry. The next step is to have the fixture draw a `BLOCK`-format batch the way it already draws a
  `POSITION_COLOR` one, so the pipeline is exercised and the lightmap path is compared against GL rather than assumed.

- **S2 executed: no reflection into RetroFuturaBootstrap remains.** `CeleritasLwjgl3ifyCompat` is deleted and the
  plugin's `onLoad` no longer probes for RFB or patches lwjgl3ify exclusions; it logs
  `Loaded Celeritas mixin plugin (CleanroomMC target)` instead. Acceptance measured on the pinned environment: PASSED,
  `nativeTerrainVerified True`, VUID 0, immediate path unaffected. The build file's now-unused RFB `compileOnly`
  dependency is left in place for a staged removal, because the attempt to comment it out broke the Kotlin DSL.

- **Runtime target (2026-10-09 instruction).** Celtium supports **CleanroomMC only**; plain Forge and other
  lwjgl3ify/RFB configurations are out of scope, while Forge *mod* compatibility on Cleanroom is kept. Section 0
  states the pinned environment, the migration stages and what specialization does not solve.

- **A second window inside the GL frame breaks the submission protocol: measured, reverted, and it defines the real
  next step.** The flush-point design was implemented as described - a window opened where the batches are produced, with
  a flag that prevents it from opening while a terrain window owns the images, an index threshold (8192 by default)
  before a window is opened, and a flush of the remainder from the per-frame hook. It compiles and it is the right
  shape, but the client exits after ~15 s and the validation layer names the reason:
  `vkQueueSubmit(): pSubmits[0] performs a layout transition on presentable VkI[mage]` - the extra submission
  transitions the shared images while GL's own frame is still using them, because the bridge's layout transitions are
  written for exactly one handoff per frame.
  So the single-window structure is not just inconvenient, it is **enforced by the image-layout bookkeeping**: adding a
  window means redesigning when the shared images change layout, which is the same work as letting the renderer own the
  frame (stage 7). That is now recorded as the prerequisite rather than as a later optimisation, and the change is
  reverted so the tree stays green (packaging build passes, immediate path still off by default with GL producing the
  correct image).
  Consequence for the ordering of work: growing coverage further (more formats, textures, animation) does not move the
  image closer to correct while the frame ownership stays as it is; the ownership work is what unblocks both the image
  and the remaining content classes.

- **Original architectural note (superseded by the measurement above).**
  The evidence that leads here: the GL reference is bit-identical between runs (0.00), the Vulkan path's own run-to-run
  variation is 0.83, the alpha test contributes 0.11, and the remaining cross difference is a stable ~14.4 mean with
  34.5% of pixels over 8. The immediate batches arrive *after* the frame's terrain passes, and the bridge only owns the
  shared images inside a terrain pass - one window per frame, early in the frame - so those batches are drawn with the
  **next** frame's colour and depth buffers while carrying the **previous** frame's matrices, colour and state. For
  anything that moves between frames (entities, particles, clouds, the animated sky, the bobbing hand) that is wrong by
  construction, and no amount of pipeline or state fidelity fixes it: the geometry is being composited against buffers
  from a different instant.
  The two ways out, both already named in the stage table: **open a window at each flush point** instead of one per
  frame - correct ordering, at the measured cost of about 130 us per handoff, so a handful per frame - or make the
  renderer own the frame outright (stage 7), where the batches are simply recorded into the frame's command buffer.
  Until one of those lands, the immediate path stays **off by default** and its parity target is recorded as blocked by
  architecture rather than by missing detail work. The alternative - continuing to tune pipelines against a difference
  that is dominated by a timing mismatch - would be measuring the wrong thing.

- **Correction: the alpha test is not what changed the difference - isolated within one build, it moves 0.11 of 255.**
  The previous round attributed the change from 11.87 to ~14 to the alpha test. Isolating it properly - same build,
  `-Dceltium.vulkan.alphaTest=false` against the default true, both against the same GL reference - gives:
  `false` 14.44 mean / 34.5% over 8, `true` 14.44 / 34.5%, and false against true only **0.11 mean / 0.6% of pixels**.
  So the test contributes about a tenth of a level and the attribution was wrong. The source check backs the
  behaviour up as well: vanilla calls `func_179092_a(516, 0.1F)` in ten places and `(516, 0.5F)`, `(516, 1/255)` and
  `(519, 0.0F)` once each, i.e. `GL_GREATER` almost always, which is what the shader implements up to the equality
  case.
  That leaves the ~14.4 gap to be explained by the candidates that were already listed, and it moves them to the front:
  the **flush running a frame later than the batch** (whose fingerprint is the 0.83 run-to-run variation measured on the
  Vulkan side, since that is one frame of scene motion) and the **24-byte formats that carry a lightmap**, which the
  backend still refuses so GL draws them while nearby batches come from Vulkan.
  The alpha test stays in place, validation-clean, behind its switch.

- **Original noise-floor note (its attribution to the alpha test is corrected above).** Four runs, two per configuration:
  `off vs off` differ by **0.00** (the GL reference is bit-identical between runs, so the scene is deterministic),
  `on vs on` differ by **0.83 mean / 1.5% of pixels over 8 / 0.4% over 32** - that is the run-to-run variation of the
  Vulkan path itself, most likely the one-frame lag interacting with the captured frame - and the cross comparison is
  **14.59 and 14.04 mean with 34.5% of pixels over 8** in both pairs. So the immediate path's image differs from GL by
  far more than the noise, consistently, and a change of one or two points *is* meaningful after all: the single runs
  before the alpha test measured 11.87 and 13.99, the runs with it measure 14.04, 14.08 and 14.59, i.e. **the alpha
  test as implemented made the difference larger, not smaller**. That is a defect in the implementation rather than a
  reason to keep it: the likely cause is that the discard compares the alpha *after* the vertex colour has multiplied
  the texture (which is what blending sees) while Minecraft's `glAlphaFunc` runs before blending and can be given a
  reference whose comparison direction differs from the one assumed here. The next step is to check the comparison
  direction and the stage at which the value is taken, or to disable the test until that is settled - it is inside the
  default-off immediate path either way, so the shipped image is unaffected.

- **Original note on the alpha test implementation (superseded by the noise-floor measurement).** The four
  validation messages of the first attempt were `VUID-vkCmdPushConstants-offset-01796`, and the cause was the push
  constant stage coverage: the range and the `vkCmdPushConstants` call both named the vertex stage only, while the
  fragment shader reads the alpha-test reference at offset 128. Declaring the range for
  `VERTEX_BIT | FRAGMENT_BIT` **and** passing both stages in the command fixed it: with the alpha test in place the run
  is PASSED, `nativeTerrainVerified True`, **VUID 0**, and every accepted batch is drawn.
  What the measurement cannot yet say is whether it helped. Across runs with the same configuration the mean absolute
  difference came out at 11.87, 13.79 and 13.99, so the frame is **not identical between runs** - the scene animates
  enough that a single-run comparison cannot attribute a change of a few points to a code change. The next measurement
  step is therefore a repeated run (or a fixed-tick capture) with the difference averaged, before any conclusion is
  drawn about which change closes the gap; the remaining candidates are the flush running a frame later than the batch
  and the 24-byte formats that carry a lightmap.
  The path stays **off by default**: the image is still not GL's, so vanilla keeps its own correct output.

- **Original note on the alpha test call site (superseded by the implementation above).** The corrected order
  worked this time - the boundary method was added first, the shared artifact rebuilt, and the method **verified present
  in the jar** (`setAlphaFunc` found in `TerrainGpuBackend.class`) before the Mixin was added. The reference value
  travelled as a fourth push-constant slot at offset 128 (range grown to 132) and both fragment shaders gained the
  `discard`, with the value captured per group like the matrices and the other state.
  Measured with the path enabled: all 500 accepted batches drawn, run PASSED, `nativeTerrainVerified True`, but **4
  validation messages appeared** and the image difference was **13.79 / 34.5% / 27.2%**, i.e. no better than the 11.87
  measured without it. Leaving validation messages behind is against the project's own rule, so the change is
  **reverted** and the tree is green again.
  What the next attempt needs: read those four messages first (they were not captured before reverting - the run log is
  gone with the configuration) and fix the cause, which is most likely the push-constant block declaration: the
  fragment shader declares a separate block starting at offset 128 while the vertex shader declares 128 bytes, and the
  two must describe one contiguous range consistently. The alpha test is worth finishing because cutout content is a
  real part of the difference, but not at the price of a validation error.

- **Original note on the alpha test call site (superseded by the attempt above).** Minecraft's alpha test
  is `GlStateManager.func_179092_a(int, float)`, which ends in `GL11.glAlphaFunc(func, reference)`; the state model
  already records the function, but not the reference value, and neither immediate pipeline implements the test, so
  cutout content (leaves, plants, particles with sharp edges) draws as opaque quads - one of the listed reasons the
  image differs from GL.
  The attempt to wire it is **reverted**: the boundary-method insertion into `TerrainGpuBackend` silently did not
  apply (the artifact was checked afterwards and contains no such method), so the Mixin referenced a method that does
  not exist and the build failed. Keeping the tree green took priority, so the change was rolled back rather than left
  half-applied. Next attempt should add the boundary method first, rebuild `:common:shadeDowngradedApi`, verify the
  method is present in the jar, and only then add the Mixin - and the reference value should travel as an extra
  4-byte push constant at offset 128 (extending the range to 132) so the fragment shader can `discard` on it, with the
  threshold folded into the pipeline key the way the other state dimensions are.

- **Correction: the "identical image" measurement was invalid, and the lost-group bug is fixed.**
  The flush snapshot (`immediate flush snapshot at 500 accepted: flushes=498 drawn=498 discarded=0 noPipeline=0`)
  shows the dropped groups were not an early return but a stale reference: the textured branch still called a field that
  no longer exists, so the first textured group threw a `NullPointerException`, the catch skipped the rest of the loop
  and the pending groups were discarded. With that fixed, **every accepted batch is drawn**.
  That also invalidates the previous round's headline result: the "mean absolute difference 0.00, 0.0% differing" was
  measured while almost nothing was being drawn, so it said nothing about correctness. Measured properly now, with all
  batches drawn and VUID 0: **mean absolute difference 11.87, 34.5% of pixels differing by more than 8, 23.3% by more
  than 32** against the same configuration with the path off. So the state-driven selection is in place and the pipeline
  count is right, but the image is still not GL's. The remaining candidates are the ones already listed - the flush
  running a frame later than the batch, the 24-byte formats that carry a lightmap and are still refused or approximated,
  texture mirror freshness, and alpha test, which the state records but the pipelines do not implement.
  The path stays **off by default**; a plain session is unchanged (PASSED, `nativeTerrainVerified True`, VUID 0).

- **Original note on the state wiring (superseded by the correction above).**
  The blend-factor question is settled the right way: each `GlStateManager` blend factor enum constant carries the
  real OpenGL constant (SRC_ALPHA is 770, not an ordinal), so the Mixin reads that `int` field once per constant and
  reports the GL value, and the backend maps it onto `VkBlendFactor` with the correspondence written next to the table.
  Both immediate pipelines now take the state dimensions (blending with its factors, depth test, depth write, culling)
  and the backend caches one pipeline per (format, state) pair, capturing the state with the group the way the matrices
  are already captured.
  Measured with the path enabled: `immediateTaken=1169`, `immediateRefusal=none`, VUID 0, PASSED, and the frame is
  **identical** to the GL reference (mean absolute difference 0.00, 0.0% of pixels differing by more than 8) - a real
  improvement over the 12.85/25.5% measured before the state was honoured. But **`immediateFlushes=28` against 1169
  accepted batches** says most groups are never drawn: the flush returns early (or discards) for them, and since GL's
  own draw was already cancelled for those batches, that is lost geometry waiting to become visible content. The path
  therefore stays **off by default** (verified again: PASSED, `nativeTerrainVerified True`, `immediateTaken=0`, VUID 0).
  Next step, specific: instrument the flush to count drawn versus discarded groups and find the early return - the only
  candidate in the current code is `pipelineFor(this.lastUiPass)` returning null, which returns before the loop.

- **Original note on the state model (superseded by the wiring above).** `VulkanGlState`
  already carries the whole selection inputs - blend enable plus all four factors, depth test/function/mask, culling
  with face, alpha test with function, lighting and shade model - and `pipelineKey(vertexFormatHash)` already folds
  exactly those into one key, deliberately excluding matrices, colour and texture bindings because those are push
  constants and descriptors. What the immediate path does *not* yet do is use them: it draws every group through one
  pipeline whose blend/depth/cull state is hardcoded, which is the first of the two measured reasons the image differs
  from GL.
  Wiring it up ran into one concrete unknown: the blend factors in the state are `GlStateManager` **enum ordinals**
  (the Mixin reports `SourceFactor.ordinal()`), and mapping those onto `VkBlendFactor` needs the enum order read from
  the sources rather than guessed. The attempt that skipped that check did not compile and was reverted to keep the
  tree green, so the next step is: read `GlStateManager.SourceFactor`/`DestFactor` in order, write the mapping once
  with the order recorded next to it, then give the immediate pipelines the state parameters and cache them by
  `pipelineKey`. Until that lands, the immediate path stays off by default and GL keeps producing the correct image.

- **Measured: the immediate path draws, but its image does not match GL yet, so it is off by default.**
  The textured (24-byte) path is validation-clean and accepts over a thousand batches per run
  (`immediateTaken=1178`, `immediateFlushes=1177`, `immediateRefusal=none`, VUID 0, PASSED). A pixel comparison
  against the *same* configuration with the path switched off (`-Dceltium.vulkan.immediate=false`, which routes every
  batch back to GL and yields `immediateTaken=0`) measured **mean absolute difference 12.85/255, 32.4% of pixels
  differing by more than 8, 25.5% by more than 32**. That is not a rounding difference: the geometry is drawn through
  the right pipeline in the right place, but the image is visibly different, because the pipeline still uses one fixed
  blend/depth/cull state instead of what Minecraft asked for, and because the flush runs a frame later than the batch.
  The switch therefore **defaults to off**: a plain session keeps vanilla's own correct image (verified again:
  PASSED, `nativeTerrainVerified True`, 1653 terrain passes, VUID 0, `immediateTaken=0`), and the path is enabled
  explicitly with `-Dceltium.vulkan.immediate=true` for measurements. Making the image match means the state-driven
  pipeline selection (blend factors, depth test and write, culling, alpha test from `VulkanGlState`) and the flush
  timing, which are the next tasks - not a counter.

- **The textured (24-byte) path works and is validation-clean.** The abort was not an exception in the buffered code
  (the guards added to catch one never fired); the validation layer named the real cause:
  `VkDescriptorSet ... was destroyed or updated without UPDATE_AFTER_BIND`, followed by
  `vkCmdBindDescriptorSets(): was called in VkCommandBuffer ... which is now in an invalid state` and then nine
  messages for every subsequent command. Updating a descriptor set that a recording command buffer has already bound
  is invalid without `UPDATE_AFTER_BIND`, and the first implementation updated **one shared set per draw**.
  The fix gives each texture its own descriptor set, written before it is ever bound and cached per texture view (a
  64-set pool; exhaustion returns 0 so the caller refuses the batch rather than drawing with a stale binding).
  Verified on hardware: `immediateOffered=8326`, **`immediateTaken=1164`**, **`immediateFlushes=1163`**,
  `immediateRefusal=none`, **VUID 0**, PASSED, `nativeTerrainVerified True`. In other words most of Minecraft's
  immediate geometry - entities, particles, the GUI, the sky, weather, mod drawing - is now submitted by Vulkan rather
  than GL.
  What is **not** yet verified is that the resulting image is *correct*: the counters say the batches are drawn and the
  validation layer is silent, but the pixel comparison against the GL reference for this configuration has not been
  run. That is the first task of the next round, before anything is claimed about visual parity.

- **Original note on the textured path (superseded by the fix above).** Implemented and
  compiling: a `POSITION_TEX_COLOR` pipeline with one combined-image sampler, a structural format id computed by the
  Mixin from the shared element list (stride alone is ambiguous - 24 bytes is both `POSITION_TEX_COLOR` and
  `POSITION_TEX_LMAP_COLOR`), groups keyed by format *and* bound texture, and a descriptor update per draw from the
  texture registry. With acceptance on, the counters jump (`immediateTaken=1155`, `immediateRefusal=none`) and the
  validation layer reports 113 messages, all of the form "commandBuffer must be in the recording state" starting at
  `vkCmdBindDescriptorSets` - i.e. an exception unwound the render pass while the textured groups were being recorded,
  and every following command then targeted a buffer that was no longer recording.
  Acceptance is therefore **off** again, with the refusal naming that exact reason
  (`immediateRefusal=textured_immediate_path_disabled: it aborted the render pass (VUID-...)`), so GL keeps drawing
  those batches, the frame stays correct and the validation output is empty (VUID 0, PASSED,
  `nativeTerrainVerified True`). Next step: find the throw inside the textured flush (the likely candidates are the
  texture registry's GL readback happening while the Vulkan pass is open, and the descriptor update path) and only
  then re-enable acceptance.

- **Texture state is captured and arbitrary GL textures can be mirrored.** A `GlStateManager.func_179144_i` Mixin
  reports the bound GL texture name into the state (`texture:270958` binds captured in one run, with the run still
  PASSED and the terrain verification intact), and `VulkanTextureRegistry` turns such a name into a Vulkan image on
  first use, reusing `VulkanGlTexture.uploadFromGlTexture` - the same level-0 readback the terrain atlas already goes
  through. Both are prerequisites for the 24-byte formats (`POSITION_TEX_COLOR` and friends), which are what most
  vanilla immediate content uses and which are still refused today with
  `immediateRefusal=no_pipeline_yet_for_a_24-byte_vertex`.
  Stated limitation: an entry is uploaded once, so a texture whose contents change afterwards (an animated atlas, a
  dynamic framebuffer texture, a mod writing into its own texture) keeps the first mirror until `invalidate` is called;
  the refresh policy belongs with the texture-animation stage of the plan.

- **Depth comparison cleared; the immediate draw is stable with the depth test on.** Re-enabling the depth test
  (`LEQUAL`, writes off) leaves both measurements unchanged - 230400 pixels for the synthetic NDC quad, 25344 for the
  fixture's batch - so the depth comparison was never the cause; the invisibility came from the flush guard and the
  matrix grouping fixed in the previous rounds. A regression run on the normal path (no test flags) stays PASSED with
  `nativeTerrainVerified True`, 1641 terrain passes and `immediateTaken=24` / `immediateFlushes=24`, i.e. vanilla's own
  16-byte batches are being drawn by Vulkan every frame in a plain session, and the 24-byte formats are still refused
  with a reason and drawn by GL. `docs/mod-compatibility.md` now records the per-mod and per-call-pattern status.

- **The immediate pipeline rasterises and a real mod batch is now visible.** The isolation test draws a synthetic
  `POSITION_COLOR` quad whose vertices are already in NDC (green, so it cannot be confused with anything else) through
  the immediate pipeline, next to the UI test quad that is known to appear. Measured: **230400 green pixels**, which is
  exactly the 480x480 area of a quad spanning NDC -0.5..0.5 at the run's resolution, so the pipeline, its vertex layout
  and its buffer binding are all correct - and **25344 red pixels** from the fixture's own `RenderWorldLastEvent` batch,
  which is the "(b) drawn by Vulkan" half of the stage-5 evidence: the batch is accepted (GL's upload and draw are
  cancelled) and the pixels are there. Run PASSED, `nativeTerrainVerified True`.
  One variable is still open and is stated rather than hidden: the depth test in the immediate pipeline is currently
  **disabled** (it was switched off as an experiment in the previous round, and this run is the first with both the
  isolation test and the real batch actually drawing). Re-enabling it and re-measuring is the first task of the next
  round, because the earlier "zero pixels with depth disabled" observation came from a run whose isolation draw never
  executed at all - the `-Dceltium.vulkan.uiTest` property was missing, so the branch never ran and the conclusion
  drawn from it was wrong. That mistake is recorded here on purpose.

- **The test quad is captured, projects on-screen, and is still invisible - so the next test isolates the pipeline.**
  The group-bounds diagnostic identified the fixture's own geometry by its packed colour and transformed every vertex:
  `group 4: vertices=4 indices=6 colour0=0xc84040ff proj=[m00=0.80535626 m11=1.4317445] ndc x[-0.85..0.85] y[0.57..0.57]
  z[0.95..0.95] behind=2`. Those are the fixture's `255, 64, 64, 200` vertices under the real world perspective, and
  they land **inside** the NDC cube (x and y within -1..1; z = 0.95 is correct for a point two blocks away with the
  0.05 near plane Minecraft uses). Disabling the depth test changed nothing (still zero red pixels), so the depth
  comparison is not the cause either.
  What remains is the pipeline itself, and the next step tests it in isolation: draw one synthetic
  `POSITION_COLOR` group with known NDC vertices through the immediate pipeline, next to the UI test quad that is
  known to appear. If the synthetic group shows up, the immediate pipeline rasterises and the fault is in the batch
  geometry or its transform; if it does not, the pipeline, its layout or its buffer binding is the fault. Either
  answer is decisive and neither needs another guess.

- **Grouping implemented and validation-clean; the quad is still invisible.** The immediate path now keeps one mesh
  per matrix pair, draws each with its own push constants (602 batches taken, **601 flushes**), and frees the meshes
  one full pass later - destroying them right after recording produced 18 validation messages about in-flight
  references, which the deferred release removed (VUID 0, run PASSED, `nativeTerrainVerified True`). The viewport and
  scissor are full-target and set once per pass, so they are not clipping it. The quad still contributes zero pixels,
  so the next diagnostic is narrower: log the projected NDC *bounds* of each group (not just vertex 0) for the first
  few groups, which says whether the test quad projects on-screen and is then rejected by the depth test, or whether
  it never enters an accepted group at all.

- **Root cause of the invisible immediate geometry found by measurement.** The one-shot diagnostic logged the first
  copied vertex, the captured matrices and the resulting NDC:
  `vertex0=(0.0,100.0,0.0) ndc=(-100.00,-1.62,0.52) w=1.00 proj=[m00=1.0 m11=1.0 m22=0.5] view=[tx=0.0 ty=-1.62 tz=0.05] indices=642`.
  The captured projection was an **identity with the clip-depth remap** (that is what the terrain path does to an
  identity), so the geometry was transformed into NDC x = -100, i.e. far off-screen. The cause is structural, not a
  sign or an offset: **one frame's immediate batches do not share a matrix pair** - Minecraft switches between the
  world perspective, the GUI ortho and identity several times per frame - and the implementation captured a single
  pair for the whole flush, so every group whose first batch happened to arrive under a different projection was
  drawn with the wrong transform. The fix is one mesh (and one draw with its own push constants) per matrix pair,
  which is a correctness requirement rather than an optimisation. It was implemented this round but the module did not
  compile in the time available, so it is reverted to keep the tree green and is the first task of the next round:
  apply the grouping, build, and verify the quad's pixels.

- **Matrix capture switched to reading OpenGL directly (measured, still invisible).** Mixing into LWJGL's own
  `GL11` is ruled out (client exits during startup) and the GLU `Project` Mixin never fires - the per-channel counters
  prove it: `matrixChannels=persp:0,frustum:0,mode:25765,identity:15416`. The world projection is installed by a
  utility class the transforming loader does not own, so no injector there can run. The backend now reads the live
  matrices with `glGetFloatv(GL_PROJECTION_MATRIX/GL_MODELVIEW_MATRIX)` at the moment the first batch of a group
  arrives (render thread, GL context current, about once per frame) and applies the same clip-depth remap the terrain
  path uses; the reads succeed (no diagnostic), `immediateTaken=615`, `immediateFlushes=553`, run PASSED, and the
  quad is still invisible.
  Next diagnostic, specific: log the captured projection and model view once together with the batch's world-space
  bounding box and the resulting NDC range, which separates "the geometry projects off-screen" from "the pipeline or
  the depth test rejects it" without another guess.

- **Original note (superseded by the finding above).** The `frustum` channel was added (the backend can
  now mirror `GL11.glFrustum`'s matrix as well as the GLU helper's perspective), and the GLU `Project` Mixin stays.
  The next attempt - a Mixin on **LWJGL's own `org.lwjgl.opengl.GL11`** to catch `glMatrixMode`, `glLoadIdentity`,
  `glFrustum`, `glOrtho`, `glMultMatrixf`, `glTranslatef`, `glRotatef`, `glScalef`, `glPushMatrix` and
  `glPopMatrix` where they happen - is **not viable here**: the client exits with code 255 about 5.7 s in, while the
  mod list is still being searched, before any of the injectors can report. Mixing into the GL API class itself is
  therefore ruled out for this runtime, and the compatibility layer has to capture *Minecraft's call sites* instead
  (Mixins on the classes that call the GL matrix functions, the way the GLU helper is already captured) or use the
  wrappers where they are used. The Mixin was reverted, the run is green again (PASSED, `immediateTaken=589`,
  `immediateFlushes=543`, no validation message), and the immediate quad is still invisible - the projection reaches
  the mirrored state only through the GLU path, while `EntityRenderer` still loads its identity through `GL11`.
  Next step: capture those `EntityRenderer` call sites (targeted, `require = 0`), which is the same technique and the
  concrete form stage 6 takes for mods that bypass the wrappers.

- **Original note (superseded by the finding above).** The projection Minecraft draws the world with is built by `org.lwjgl.util.glu.Project.gluPerspective` and
  installed with `GL11.glFrustum` - it never passes through `GlStateManager`, so the state wrapper's Mixin cannot see
  it. A Mixin now targets that GLU class **by name** (`@Mixin(targets = "org.lwjgl.util.glu.Project")`, so no build
  dependency on LWJGL 2's utility jar is added) and multiplies the mirrored projection stack by the same frustum
  matrix, with `require = 0` and an exception guard. It lands cleanly (run PASSED, draws continue:
  `immediateTaken=595`, `immediateFlushes=548`) but the quad is still not visible, which points at the same bypass one
  level lower: `EntityRenderer` also calls `GL11.glLoadIdentity`/`glMatrixMode` directly around it, so the mirrored
  projection is multiplied onto a stack that never saw the identity load. **This is precisely the class of call the
  direct-GL compatibility layer (stage 6) has to cover**, and it is now the concrete next step: mirror the handful of
  direct `GL11` matrix calls at that level (targeted, `require = 0`, no signature changes).

- **Original note (superseded by the finding above).** Three fixes were
  made and measured in sequence - matrix *contents* mirrored (translate/rotate/scale/ortho/loadIdentity/matrixMode/
  multMatrix), the matrices captured at *submission* time rather than at flush time (the flush runs a frame later,
  when the live state has moved on), and OpenGL's clip-depth range remapped to Vulkan's (the same remap the terrain
  projection uses) - and the counters confirm the draw happens: `immediateTaken=609`, `immediateFlushes=546`, run
  PASSED with no validation message. The quad still produces zero pixels.
  The measurement now points at one specific gap: **`GlStateManager` only wraps `glOrtho`** (the single
  `func_179130_a` call site in `EntityRenderer` is the GUI ortho), so the *perspective* projection the world is drawn
  with never passes through the mirrored calls and the state still holds a stale projection. Next step: find where the
  world projection is actually set (a direct `GL11.glFrustum`/`glLoadMatrix` call site, or a matrix built and loaded by
  the renderer) and mirror that path - if it bypasses `GlStateManager` entirely, that is exactly the class of mod/API
  call the direct-GL compatibility layer (stage 6) has to cover, and it is recorded as such.

- **Original note on the immediate draw path (superseded by the finding above).** The first per-format pipeline
  (`VulkanImmediatePipeline`, untextured `POSITION_COLOR`), raw vertex copying (`VulkanDynamicMesh.copyVertices` ->
  the topology expansion), acceptance of the batch (so GL cancels its own draw) and a flush inside the next terrain
  pass are implemented. Verified on hardware with the mod-draw test: `immediateOffered=8881`, **`immediateTaken=588`**,
  **`immediateFlushes=541`**, no validation message, run PASSED - Vulkan really records and submits that geometry.
  The pixels are missing though: the test quad is not visible, because only *push/pop* of the matrix stack is mirrored
  so far - the matrix *contents* (`loadMatrix`, `translate`, `rotate`, `scale`, `ortho`, `multMatrix`) are not, so the
  immediate draw uses identities while Minecraft's own drawing used the camera and model transform. That is the next
  step: mirror the matrix contents into `VulkanGlState` and have the immediate draw use them instead of the terrain
  pass's matrices. Until then the accepted batches are drawn in the wrong place, which is honest to state: this is a
  live pipeline, not yet a correct image.

- Next for stage 3: the per-vertex-format pipelines (and the frame ownership that lets a batch be recorded outside the
  terrain passes), so `immediateRefusal` disappears and those 8365 batches are drawn by Vulkan.

- **Original note (superseded).** The phantom
  classpath problem was worked around by extending the boundary interface the backend already implements
  (`TerrainGpuBackend` gained default immediate-draw methods) instead of introducing a new class the module could not
  resolve, and the backend now implements them (state into its `VulkanGlState`, batches counted and refused with a
  reason) with a `TerrainGpuBackends` holder for the Mixins. The `GlStateManager` Mixin then failed *at runtime* with
  `Critical injection failure: @Inject annotation on celtium$disableBlend could not find any targets matching
  'func_179146_i'`, which is the Mixin layer correctly refusing to load a wrong target: only some of the method names
  I used exist in this build's `GlStateManager` (`func_179147_l`, `func_179132_a`, `func_179097_i`, `func_179131_c`,
  `func_179094_E`, `func_179121_F`, `func_187428_a` are confirmed from the sources; `func_179146_i` and
  `func_179126_j` are not). Next step: read the names for disable-blend and enable-depth from
  `GlStateManager.java` and inject only what exists, with `require = 0` for anything optional.

- **Original finding (superseded by the refinement above).** The first diagnosis was the `downgraded`
  artifact being stale; that was wrong - after `:common:shadeDowngradedApi` the jar does contain the new classes.
  Moving `ImmediateDrawTarget`/`ImmediateDrawTargets` from `common/` into `celtium-vulkan/` (which `forge122`
  already depends on through `modEmbed`, so the Mixin can import them) did **not** clear the error either: the module
  still reports "method does not override or implement a method from a supertype" for methods whose signatures match
  the interface in the same package and module. That points at the module's own compile classpath carrying a stale
  copy of the interface (a jar or class directory built before the interface existed), not at Gradle task ordering
  alone. Next step: dump the `compileClasspath` of the `celtium-vulkan` module and find which entry provides
  `ImmediateDrawTarget`, then fix or exclude it. The groundwork (`common/` interface and holder) stays committed; the
  backend implementation, the `GlStateManager` Mixin and the reverted `WorldVertexBufferUploader` Mixin follow once
  that entry is identified.

- **Original note (superseded by the refinement above).** The `celtium-vulkan` module compiles against
  `project(':common', configuration: 'downgraded')`, and that artifact is a *jar*: classes added to `common/` after it
  was built are invisible to the module even though `:common:shadeDowngradedApi` reports success (the checked jar
  contains zero references to the new `ImmediateDrawTarget`). Any change that adds a common class the Vulkan module
  implements needs that artifact regenerated first, otherwise the error is a misleading "method does not override or
  implement a method from a supertype". The state/interface groundwork (`ImmediateDrawTarget`,
  `ImmediateDrawTargets`) is committed in `common/`; the backend implementation and the `GlStateManager` Mixin that
  consume it are the next step once the artifact path is fixed.
- Open: presentation is not taken over from GL yet (stage 2's real deliverable), the probe presents one cleared
  image rather than a rendered frame, and the Intel UHD 630 has not run the probe.
- Cutout/translucent frame difference against the OpenGL reference (mean 6.86/255, 43.7% of pixels above 8) is
  unresolved and is not evidence of correctness.
- Compact (24-byte) vertex format renders incorrectly on Vulkan (76.8 mean difference); default is off, so users who
  enable it get the vanilla-like format only if they also disable the option. Diagnosis is field-level and open.
- Hybrid constraint: the current bridge needs opaque-FD external memory, GL external objects and specific FBO
  attachments (RGBA8 colour texture, D24 depth renderbuffer). This is a *hybrid-stage* constraint and must not be
  confused with the GPU requirements of the native-only target (section 6).

---

### Hot path: measured, not asserted

The submission path was profiled with a counter that accumulates the time spent inside `submitImmediateBatch` over a
run, because the instruction that started this work named the per-batch reflection as a candidate. The measurements, on
the pinned RX 6400 configuration and with the immediate path enabled:

| Revision | `immediateSubmitAvgNs` per offered batch |
|---|---|
| reflective lookup per batch (`getDeclaredField`/`setAccessible`, `Class.forName`/`getMethod`/`invoke`) | **29252** |
| reflective members cached in statics | **28026** (-4%) |
| cached, and the live GL matrices re-read only when the mirrored matrix state changes | **27439** (-6% in total) |

So the reflection was **not** the cost, and neither was reading the matrices every batch: the remaining ~27 us is
dominated by what the current design has to do per batch, and the honest conclusion is that this is a *matrix-mirror*
problem, not a micro-optimisation one. Two consequences are recorded rather than glossed:

- In the fixture scene the cost is invisible (about 1.1 batches per frame, ~30 us of a 16.6 ms frame), but the same 27
  us per batch would be **54 ms per frame** at 2000 batches, which real entity, particle and GUI scenes can easily
  reach. The path must not be enabled for real content until that number comes down.
- The way down is to stop asking OpenGL for the matrices per batch, which means mirroring the projection (the thing the
  missing GLU class prevents) or owning the frame - the same workstream as the image-correctness problem, not a
  separate tuning exercise.

---

### The flush-point window fails on its own, not because of the present probe

The first failure of the mid-frame window carried the message
`vkQueueSubmit(): pSubmits[0] performs a layout transition on presentable VkImage`, which reads like a conflict with the
swapchain. That was a hypothesis, and it is now **disproved**: with the window re-applied and the present probe switched
off entirely, the client still exits after ~15 s, this time with `net.minecraft.client.Minecraft: Unreported exception
thrown!` and no Vulkan validation message at all. So the window is not fighting the probe; something in the new path
throws in Java, and the earlier layout message was a symptom of two windows coexisting rather than the cause.

Recorded for the next attempt, which is the frame-ownership design rather than another patch: the exception needs to be
captured with its stack (the `record(...)` guards in the window did not catch it, so it is thrown outside them, most
likely in the command-buffer lifecycle around a nested pass), and the window's render-pass and framebuffer handling has
to be reviewed against what the terrain pass does - `ensureFramebuffer(renderPass)` is keyed by the render pass, so a
second pass with a different pipeline can very plausibly get a framebuffer built for the wrong pass. Both are recorded
here instead of being guessed at again, the window is reverted, and the tree is green with the immediate path off by
default.

---

### The matrix mirror is measurably unfaithful, which is why the GL reads exist

The immediate path pays two `glGetFloatv` calls per matrix change, and that was measured as the dominant per-batch cost
(~27 us). The obvious way to remove them is to use the matrix state the `GlStateManager` Mixins already mirror, so the
backend now compares the two continuously and reports the worst disagreement seen:

```
matrixMirrorDelta = proj: 2.0005527, view: 2000.0
```

Both are wrong, and the model view is wrong by the size of the world transform itself: the mirrored stack never
receives it. That is consistent with everything measured earlier about the world transform living outside the wrapper -
`GlStateManager` only wraps `glOrtho`, the GLU helper is not on the runtime classpath at all, and mixing into LWJGL's
own `GL11` stops the client during startup. In other words, **the matrices are set through direct GL calls that no Mixin
of ours can see**, so reading them back is not laziness: it is the only correct option short of the renderer owning the
frame and supplying the matrices itself.

The switch (`-Dceltium.vulkan.mirroredMatrices=true`) and the comparison stay in the tree so the claim can be re-checked
rather than remembered, and the numbers are printed in the verification line. The consequence for priority is
unchanged: frame ownership is what makes both the image correct and the per-batch cost go away, and it is the same work.

---

## Third attempt at the per-batch cost: splitting the matrix reads (reverted)

The ~27 us per accepted batch is dominated by the two `glGetFloatv` calls that read the live projection and model view.
Since the projection changes only a couple of times per frame in principle, the read was split: a `projectionDirty` flag
set only by the calls that actually replace or multiply the projection (`loadIdentity`, `ortho`, `multiplyMatrix`) and a
`matricesDirty` flag for the model view, with counters (`matrixReads=proj:N,view:M`) to show whether the split worked.

It did not, across two corrections:

| Revision | projection reads | model view reads | `immediateSubmitAvgNs` |
|---|---|---|---|
| split, projection marked on any matrix call | 1736 | 1736 | 28167 |
| split, matrix mode no longer marks it | 1741 | 1741 | 26962 |
| split, only `loadIdentity`/`ortho`/`multiplyMatrix` mark it | 1728 | 1728 | 27507 |

The counts stay equal, which means the projection genuinely changes as often as the model view in this content - one of
those three calls happens once per group. So the reads cannot be reduced by splitting the signal, and the change was
reverted rather than left in as complexity with no gain. Combined with the mirror measurement (proj 2.0, view 2000.0
apart) this closes the question from both sides: the matrices have to come from somewhere other than a GL read, and the
only such source is a renderer that supplies them itself.

---

## The mid-frame window, fourth attempt: the gate works, the image does not survive it

The exception was finally identified by recording it **with its stack** instead of its message:

```
immediate window failed: java.lang.IllegalStateException: beginFrame: the bound draw framebuffer is the default
framebuffer; the verified bridge needs an FBO whose colour attachment is an RGBA8 texture and whose depth attachment
is a D24 renderbuffer
  at org.soutalab.celtium.vulkan.VulkanFrameTarget...
```

The window was being opened while GL was drawing to the **default** framebuffer (the screen), where the bridge has no
colour/depth pair to read. Gating the flush on `GL_DRAW_FRAMEBUFFER_BINDING != 0` fixed exactly that:

| With the gate | Result |
|---|---|
| Window failures | **none** |
| Windows opened | 1267 (plus 316 properly deferred while the default framebuffer was bound) |
| `immediateTaken` / `immediateFlushes` | 2836 / 2836 |
| Run status, validation | PASSED, `nativeTerrainVerified True`, **VUID 0** |
| Local image difference against the same build with the path off | **mean 147.59, 100% of pixels over 8 and over 32** |

So the mechanism now runs cleanly and every accepted batch is drawn in a window opened at the point that produced it -
and the frame is destroyed for it. A full-frame difference means the picture is no longer the scene, which points at the
window's cost rather than at the geometry: each window copies the **whole** colour and depth image in and copies them
back out, and 1267 of those in a run interact with GL's own use of the same buffers (the depth round-trip in particular),
so the consecutive copies do not compose into the same image.

That is a design conclusion, not a bug to patch: a bridge that merges two renderers by copying whole images cannot
host a window per flush point. Making the immediate path correct needs the renderer to own the frame - drawing GL's
remaining content into Vulkan images as it is ported - which is the same conclusion the matrix measurements reached from
the other side. The window is reverted, the tree is green, and the immediate path stays off by default.

---

## Correction: the window's black frame is unexplained, so the design conclusion was premature

The fourth attempt concluded that "a bridge that merges two renderers by copying whole images cannot host a window per
flush point". That conclusion was drawn from a frame that differed from the reference in 100% of its pixels, and the
saved screenshot says something much more specific:

```
window run : mean [0. 0. 0.] std [0. 0. 0.]   - the frame is entirely black
reference  : mean [135.9 148.6 158.2]         - the scene
```

An all-black frame is not a compositing subtlety, and two candidate explanations have now been checked in the source and
**eliminated**:

- `VulkanFrameTarget.endFrame()` **does** write the frame back: it waits on the Vulkan-to-GL semaphore, blits the shared
  colour framebuffer into the game framebuffer with `glBlitNamedFramebuffer`, and converts the depth. So the window was
  not missing its copy-back.
- The terrain render pass loads rather than clears: both attachments use `VK_ATTACHMENT_LOAD_OP_LOAD`, so the
  immediate-only pass reusing it would not wipe the shared image.

So the cause is still open, and the honest statement is that the design conclusion was **premature**: it was drawn from
a broken frame whose breakage has a cause that has not been found yet. The next diagnostic is targeted and cheap: sample
the shared colour image right after the window's `beginFrame()` and again after its `endFrame()`, and compare both
against what the game framebuffer holds at those moments. That says whether the content is lost on the way in, on the
way out, or never present - instead of inferring it from a final image.

---

## The window's copies are sound; the black frame comes from after the window

The targeted diagnostic was implemented and it changes the picture again. The window now samples a small centre patch of
the bound framebuffer immediately before it opens and immediately after it closes, and reports the mean. Two runs:

| Measurement | Result |
|---|---|
| Game framebuffer mean before / after the window | **150.47656 / 150.47656** - the content is preserved |
| Window failures | none |
| Batches taken / flushed | all of them, e.g. 2836 / 2836 |
| Run status, validation | PASSED, `nativeTerrainVerified True`, VUID 0 |
| **Final screenshot** | **mean [0, 0, 0]** - entirely black, in every window run so far |

Two conclusions follow, and both correct earlier statements in this log:

1. **The copies are sound.** `beginFrame()`'s colour and depth copy-in, the render pass, the submission and
   `endFrame()`'s blit and depth conversion all leave the game framebuffer with exactly the content it had. The
   earlier "a bridge that copies whole images cannot host a window per flush point" was therefore **not** supported by
   the evidence, and the "black frame means the frame was destroyed" reading was wrong: the frame is intact *at the
   window*.
2. **Something after the window blacks the picture out.** Restoring the framebuffer *binding* around the window - the
   obvious candidate, since the screenshot helper reads pixels and would read whatever was left bound - did not change
   the result: the final frame is still entirely black with the binding handed back exactly as found. So the loss
   happens between the last window of a frame and the moment the fixture captures, and the next diagnostic is to sample
   the same patch at the capture point itself (in the fixture's screenshot path) and one frame earlier, which
   distinguishes "the game framebuffer really is cleared" from "the capture reads the wrong surface".

The window is reverted again so the tree stays green and the immediate path stays off by default; the measurements above
are from the runs that had it enabled.

---

## The window is behind a switch, the default path is verified unchanged, and the black frame is still unexplained

The window implementation now stays in the tree behind `-Dceltium.vulkan.immediateWindows` (default off), so it can be
iterated on without re-applying it every round and without changing shipped behaviour. Two runs close this round:

| Configuration | Final frame | Local difference vs the known-good reference |
|---|---|---|
| Default (window off, immediate path on) | mean [123.3, 136.6, 148.3] - the scene | mean 14.61, 34.5% of pixels over 8 - the known immediate-path difference |
| Window on (`-Dceltium.vulkan.immediateWindows=true`) | mean [0, 0, 0] - entirely black | mean 147.59, 100% differing |

So the default path is **unchanged and correct**, the window is what blacks the frame out, and the fixture-side capture
sample that was meant to localise it did not fire: `recordFrameContentSample` was inserted at the wrong one of the three
`ScreenShotHelper.saveScreenshot` call sites, so nothing was recorded. The next attempt inserts it at the call site the
validation run actually uses (the `if (!interopRun)` one), and samples there and one frame earlier.

Known state of the immediate path, unchanged by this round: off by default, every accepted batch drawn when enabled,
validation clean, image not correct.

---

## The capture-time sample could not be placed this round

The plan for this round was to sample the framebuffer at the capture call site the validation run actually uses (the
`if (!interopRun)` one) and one frame earlier. Reverting the earlier attempt and inserting the sample there compiled in
isolation but the full packaging build then failed - once in the fixture module, with no error text the usual greps
could surface, and `tail -3` reporting only `BUILD FAILED` with one task executed. Since a failing build is worse than a
missing measurement, the fixture change is reverted and the tree is green again.

The failing task was `:forge122:validationRemapJar`, not the code module: the fixture half of the previous commit had
been committed without its own build being checked, so the packaging build had been broken since then. The fixture is
restored from the commit before it, which is the last revision that built, and `packageJar` together with
`validationRemapJar` both succeed again. The lesson is recorded as a rule: the fixture module is built by the packaging
task, not by the module compile, so a fixture edit is only verified once both have run.

What stands from this round: the window stays in the tree behind `-Dceltium.vulkan.immediateWindows` (default off), the
default configuration is verified unchanged (frame mean [123.3, 136.6, 148.3], local difference 14.61 mean / 34.5% over
8, the known immediate-path difference), and the black frame is confirmed to be caused by the window and is still
unexplained. The sample has to be placed in a way the fixture's build accepts - most likely inside an existing helper the
fixture already compiles, called from the capture path - rather than by editing the capture statement itself.

---

## Localised: the game framebuffer really is black at capture time

The capture-time sample is in place and it builds - the earlier failures were the fixture module needing a real build
(`:forge122:validationRemapJar`), first because the edit had been committed unbuilt, then because a `LOGGER` field the
edit referenced was never inserted. With an inline logger and a plain JDK direct buffer (no dependency on LWJGL's
`MemoryUtil`), both packaging tasks pass and the fixture logs:

```
celtium capture frame sample: mean=0.0
final frame mean: [0. 0. 0]
```

So the capture reads the surface it means to read, and that surface is black. The question is no longer "is the capture
lying" - it is not - but "what stops GL from drawing once windows are in use". The measurements bracket it precisely:
inside a window the framebuffer holds its content (150.47656 before and after), and by the time the frame is captured it
holds nothing.

Narrowed candidates for the next attempt, all about GL state the bridge touches and may not put back: the bridge's
`GlState` guard restores what its own copies need, but a window also runs `endFrame`'s depth conversion (a program, a
VAO and texture bindings of its own), and GL's viewport, scissor, blend or depth state could survive a window in a form
GL's later drawing does not expect. The cheap experiment is to log the GL state that matters (viewport, scissor,
enabled capabilities, current program, bound VAO and framebuffer) immediately before and after a window and compare.

---

## The black frame was a second window in the same frame, and fixing it moves the question

The GL-state snapshot settled it. Comparing viewport, scissor, capabilities, current program, bound VAO and both
framebuffer bindings immediately before and after a window showed **"GL state restored unchanged"** every time - and the
framebuffer content samples told the story in the same log:

```
mean before=150.46094 after=150.46094  GL state restored unchanged
mean before=150.46094 after=0.0        GL state restored unchanged     <-- here
mean before=150.46094 after=150.46094  GL state restored unchanged
```

The **second** window in a frame is what turned the framebuffer black; the first one, and every window after the second,
was fine. Limiting the immediate path to **one window per frame** (the remainder is drawn in the next frame's window,
which is what the earlier design did anyway) fixes it:

| Configuration | Final frame | Local difference vs the offline reference | Validation |
|---|---|---|---|
| Window on, unlimited windows per frame | mean [0, 0, 0] | 147.59 | VUID 0 |
| Window on, **one window per frame** | mean [123.8, 137.2, 149.0] | **14.08**, 34.5% over 8 | VUID 0, PASSED |
| Window off (the default) | mean [123.3, 136.6, 148.3] | 14.61, 34.5% over 8 | VUID 0, PASSED |

Two things follow, and the second is the more important one:

1. **The mid-frame window is viable.** It copies, draws and restores cleanly, and a frame that uses it is a normal
   frame. The earlier conclusion that this bridge could not host such a window is now doubly wrong, and the reason is
   recorded: it was a second window in one frame, not the copy mechanism.
2. **Frame lag is not what the 14-point difference is made of.** With the window the batches are drawn in the frame that
   produced them, and the local difference is 14.08 against 14.61 when they are not - a change of half a point, well
   inside the run-to-run variation measured earlier (0.83). So the image difference has a different cause, and the
   candidates that remain are state fidelity (the lightmap and alpha handling in the immediate pipelines, blend and
   depth details) and texture mirror freshness - not timing.

The window stays behind `-Dceltium.vulkan.immediateWindows` (default off) because it has not been shown to improve the
image; what it has been shown to do is work without breaking the frame.

---

## Attribution: the whole 14-point difference is one pipeline, and the other three are pixel-perfect

A switch that limits which layouts the backend accepts (`-Dceltium.vulkan.immediateFormats`) turns the aggregate number
into per-pipeline evidence. Same build, same scene, each run accepting exactly one format:

| Accepted format | Local difference vs the offline reference | Batches taken |
|---|---|---|
| 1 - `POSITION_COLOR` (16 B) | **0.00 mean, 0.0% over 8** | 35 |
| 2 - `POSITION_TEX_COLOR` (24 B) | **14.00 mean, 34.5% over 8** | 1144 |
| 3 - `BLOCK` (28 B, lightmap) | **0.00 mean, 0.0% over 8** | 560 |
| 5 - particle (28 B, lightmap) | **0.00 mean, 0.0% over 8** | 556 |

That is as clean an answer as the harness can give: **three of the four immediate pipelines reproduce GL exactly,
pixel for pixel**, including both lightmapped families, and the entire difference that has been discussed for many
rounds is produced by the `POSITION_TEX_COLOR` pipeline alone. The number of batches says it is not a rounding case
either - 1144 batches of that one layout differ, while 1151 batches across the other three do not.

Everything argued earlier about timing, frame lag and the window is therefore beside the point for this difference: it
is a defect in one pipeline, and the next step is to look at that pipeline specifically - its vertex attribute order
(`POSITION_TEX_COLOR` is position at 0, texture at 12, colour at 20), its descriptor binding, and whether the batches
that reach it are really the layout the identification claims (the format id is decided by comparing element lists, and
the 24-byte family in vanilla also includes layouts that differ only in order).

The switch stays in the tree; it is the instrument that produced this, and it will be used the same way for the
remaining formats and for any future pipeline.

---

## The format-2 difference is the sky, and honouring "texturing off" did not fix it

The difference was rendered rather than argued about: stacking the offline reference, the format-2-only frame and their
per-pixel difference shows the **top third** of the screen differing in **74.7%** of its pixels, the middle third in
0.9% and the bottom in 7.2%. Looking at the crops, the reference has the sky's usual gradient while the format-2 frame
has a **flat, slightly darker blue** over the whole sky region - the sky geometry is drawn, but without its gradient.

That pointed at the state vanilla uses for the sky: texturing switched off, the gradient carried by the vertex colours,
so the fix was to mirror `GlStateManager.func_179098_w()`/`func_179090_x()` (enable/disable texturing) and, when
texturing is off, sample a **white 1x1 mirror** so the vertex colours pass through untouched. It is in the tree and it is
correct in principle - and the measurement says it is not the cause:

| Configuration | Local difference |
|---|---|
| Format 2 accepted, before the fix | 14.00 mean, 34.5% over 8 |
| Format 2 accepted, texturing-off honoured | **13.77 mean, 34.5% over 8** |

Barely a change, so the flat sky comes from somewhere else. The next candidates, in order of how cheap they are to
check: the vertex attribute layout of the 24-byte pipeline (position at 0, texture at 12, colour at 20 - which is what
`POSITION_TEX_COLOR` declares), whether the vertex colours themselves arrive intact through the raw vertex copy, and
whether the format identification is putting a different 24-byte layout - vanilla has several that differ only in
element order - through this pipeline.

What this round adds permanently: the per-format acceptance switch, the texturing-off mirror (which the sky and any
colour-only content needs regardless), and the rendered side-by-side as a way to see a difference instead of counting it.

---

## What the format-2 batches actually are, and why refusing them wholesale is worse

Dumping the first accepted `POSITION_TEX_COLOR` batches answers the question directly:

```
elements=[12bPOSITION 8bUV 4bCOLOR] vertices=4 mode=7
v0=(pos 0.0,720.0  uv 0.0,0.0  col 0xffffffff)
v1=(pos 1280.0    uv 1.0      col 0xffffffff)
```

A four-vertex quad covering the whole 1280x720 display, texture coordinates 0..1, and **white** vertices - no gradient in
the data at all. That is a full-screen textured quad drawn in screen coordinates, i.e. the final composite rather than
sky geometry, and it is drawn to the **default framebuffer**, not the game framebuffer.

That also explains the earlier finding that the format-2 acceptance flattens the sky: the batch is taken, drawn into the
game framebuffer one frame later, and covers everything the frame had drawn with one texture.

The obvious fix - refuse batches while the default framebuffer is bound - was implemented and **made the measurement
worse**: the local difference went from 14.61 to **62.34** (with 30.4% of pixels differing, so a smaller but far larger
error). Refusing on that condition alone therefore rejects batches that were being drawn correctly, which means the
decision cannot be made from the binding at submission time alone; it has to distinguish the composite from the content.

Two facts are now established beyond argument, and they are what the next attempt builds on: the format-2 family in this
scene is the **final full-screen composite** (white vertices, uv 0..1, screen coordinates), and it belongs to the frame's
end, not to the game framebuffer's content. The remaining question is where in the frame it can be drawn by Vulkan - which
is the present-ownership work, not a pipeline detail. The change is reverted and the tree is green.

---

## The immediate path is pixel-identical to GL once the composite is left out

The composite-only refusal was neutral (13.96 against the 14.61 baseline), so the next measurement removed the layout
entirely instead of guessing where it belongs. Same build, same scene, the backend accepting formats 1, 3, 4 and 5 and
leaving the 24-byte family to GL:

| Configuration | Local difference vs the offline reference | Batches drawn by Vulkan |
|---|---|---|
| All formats accepted | 14.61 mean, 34.5% over 8 | ~2800 |
| **Formats 1, 3, 4, 5 - composite left to GL** | **0.00 mean, 0.0% over 8 and over 32** | **1725** |

Pixel-identical. The Vulkan immediate path reproduces GL exactly for the three layouts that carry Minecraft's content -
untextured colour geometry, the BLOCK family with its lightmap, and the particle family - and the *entire* difference
that has been chased for many rounds is the final full-screen composite, which is not content at all but the frame's
last step.

That closes the question the previous entries kept circling: the immediate path is not approximately right, it is right,
and what remains for it is a present-ownership problem - where the composite is drawn and by whom - not a pipeline
defect. It also means the per-format acceptance switch is not a diagnostic curiosity but the mechanism that will carry
the path in the interim: `-Dceltium.vulkan.immediateFormats=1,3,4,5` with the immediate path enabled is a pixel-correct
configuration today, one flag away from the default.

Nothing is flipped by default yet: the immediate path itself stays off until the composite question is settled, so the
shipped behaviour is unchanged while that is finished.

---

## Both GPUs, pixel-identical

The configuration that measured 0.00 on the RX 6400 was repeated on the Intel UHD 630 with an Intel reference of its
own (same build, immediate path off) rather than against the AMD frame:

| GPU | Local difference (immediate off vs on) | Batches drawn by Vulkan | Validation |
|---|---|---|---|
| AMD RX 6400 (RADV) | **0.00 mean, 0.0% over 8 and over 32** | 1725 | VUID 0 |
| Intel UHD Graphics 630 (ANV) | **0.00 mean, 0.0% over 8 and over 32** | 1151 | VUID 0 |

The default acceptance mask is now `1,3,4,5`, so enabling the immediate path gives the verified configuration rather
than one that paints the composite into the game framebuffer. The path itself stays off by default until the composite
and present ownership are settled, which is the remaining engineering task for it.

---

## The lag-free configuration is also pixel-identical

The mid-frame window draws each batch in the frame that produced it, which removes the one-frame lag the default flush
has, and with the composite excluded it measures the same as GL:

| Configuration (same build, same scene, both compared against the immediate path being off) | Local difference |
|---|---|
| Immediate path on, window flush off (batches drawn in the next frame's pass) | 0.00 mean, 0.0% over 8 and 32 |
| Immediate path on, **window flush on** (`immediateWindows=true`, `immediateFlushIndices=512`) | **0.00 mean, 0.0% over 8 and 32** |

Both are pixel-identical; the difference between them is *when* the content is drawn, which a still comparison cannot see
and a moving scene would. That is why the window variant matters: it is the one that does not introduce a frame of lag
for entities, particles or the GUI.

Recorded configuration, ready for the content stage:

```
-Dceltium.vulkan.immediate=true
-Dceltium.vulkan.immediateWindows=true
-Dceltium.vulkan.immediateFlushIndices=512
```

The immediate path still defaults to off. The reason is not doubt about these measurements but their coverage: they are
one scene on two GPUs, and the plan's own rule is that a class is ported (or left on GL and named) once it has been seen
to work across the content it applies to. Enabling it by default belongs with the content verification stage, where the
same measurements run over GUI, HUD, weather and entities rather than over a single world.

---

## Identifying the composite's caller: instrumentation in place, run still to make

The next question for the full-screen quad is *who draws it*, because that decides where it belongs. The draw entry now
carries the instrumentation for it: for the first offered `POSITION_TEX_COLOR` batch it dumps the element list, the first
two vertices and a stack trace naming the caller.

Two attempts have not produced the output yet, and both reasons are recorded rather than worked around blindly:

- With the acceptance mask restricted to format 2 the run ended after 24 seconds instead of the usual ~150, so no dump
  was captured. Why that configuration exits early is itself unrecorded and worth knowing.
- The second attempt tried to move the dump ahead of the acceptance decision so a normal run would print it; the text
  patch did not apply (the anchor had moved), so the dump stayed where it was and the normal run - whose mask excludes
  format 2 - never reached it. The build stayed green throughout.

The instrumentation is **not** in the tree: checking before committing showed no dump in the mixin, so it was lost in
one of the reverts this round (the pattern has been to roll back with `git checkout -- .` when a build breaks). That is
corrected here rather than left implied by a commit message. The next attempt is small and specific: move the dump so it runs for
every offered composite regardless of the mask - the mixin sees the batch before the backend decides anything, which is
exactly where an unconditional dump belongs - and then run the normal configuration and read the caller.

---

## The composite quad is the framebuffer-to-screen blit, so it belongs to present ownership

The unconditional dump finally captured the caller, and it names the whole chain:

```
celtium composite batch: elements=[12bPOSITION 8bUV 4bCOLOR] vertices=4 mode=7 stride=24
                         v0=(pos 0.0,720.0 uv 0.0,0.0 col 0xffffffff)
celtium composite caller: WorldVertexBufferUploader.func_181679_a
  <- Tessellator.func_78381_a:22
  <- net.minecraft.client.shader.Framebuffer.func_178038_a:268
  <- net.minecraft.client.renderer.RenderGlobal.func_174975_c:250
  <- net.minecraft.client.renderer.EntityRenderer.func_181560_a:1135
  <- Minecraft...Main.main
```

`Framebuffer.func_178038_a` is Minecraft's own `framebufferRender`: the blit that takes the game framebuffer and puts it
on the screen, implemented as a shader program over a full-screen quad - which is exactly what the four white vertices
with uv 0..1 are. It is reached from `RenderGlobal` while `EntityRenderer` is finishing the frame.

So the 24-byte family in this scene is not content at all: it is the **present step**. That confirms the default
acceptance mask (`1,3,4,5`) is not a workaround but the correct division of labour - geometry that belongs to the frame's
content goes to Vulkan, the step that puts the frame on the screen stays with GL until present ownership is ported, and
when it is, this is the quad Vulkan will draw.

It also explains every earlier observation about this layout in one stroke: taking it drew a full-screen texture into the
game framebuffer a frame later, which is why the sky went flat and why the measured difference was 14 points.

---

## The present mechanism for the real frame is implemented; wiring it into the probe is next

`VulkanSwapchain.presentImage(sourceImage, sourceLayout, width, height)` implements the step that will replace
Minecraft's `Framebuffer.func_178038_a`: acquire an image from the swapchain, barrier the source image to
`TRANSFER_SRC_OPTIMAL`, barrier the acquired image to `TRANSFER_DST_OPTIMAL`, `vkCmdBlitImage` (scaling to the swapchain
extent if they differ), barrier to `PRESENT_SRC_KHR`, submit through the context's command-buffer helpers and present
with the presentation waiting on the reusable acquire semaphore - the same synchronisation shape as the clear-and-present
probe that the validation layer already accepts.

It compiles and is committed. The first attempt to call it from the swapchain probe did not apply (the anchor text had
moved, the same failure mode as the previous round's instrumentation); the second attempt found the right call but
inserted the block into the middle of the surrounding `record(...)` expression and broke the build, so it was reverted
and the tree is green again. Both failures are text-surgery failures, not design ones: the pattern to stop repeating is
patching by anchor, and the next attempt should locate the statement, read the three lines around it, and only then edit.
It has therefore not run yet and nothing about present
ownership has been demonstrated beyond the probe's cleared image. The next step is to wire that one call where the probe
already presents and read its result, with the string it prints recorded either way.

---

## Presenting the frame content: the mechanism runs into the same binding rule as the window

The wiring worked this time, and the run says exactly what happens:

```
swapchain: platform=x11 format=44 colourSpace=0 presentMode=1 images=4 size=1280x720
present probe: cleared and presented image 0 (0)
swapchain probe failed at present the frame content: IllegalStateException...
```

The clear-and-present probe still succeeds, and presenting the **frame's own content** fails inside `beginFrame()` - for the
reason already measured for the mid-frame window: at that moment the **default framebuffer is bound**, so there is no game
framebuffer for the bridge to read, and `beginFrame` refuses with "the bound draw framebuffer is the default framebuffer".

That is not a defect in `presentImage`; it is the same rule the window already obeys, and the fix is the same gate: present
at a moment when the game framebuffer is the source. The place that guarantees it is the one the stack trace identified -
`Framebuffer.func_178038_a`, where Minecraft itself composites the game framebuffer onto the screen. The step is now gated
and reports that it is skipped at the probe's moment, so the probe output stays clean and the failure is not mistaken for
a broken mechanism.

What remains for present ownership, in order: call `presentImage` from the composite's own moment (the draw entry already
sees that batch, and the frame's content is bound there), cancel GL's blit for that batch, and then verify that the screen
shows the frame while the frame buffer keeps its content - the screenshot path reads the game framebuffer, so both can be
checked in one run.

---

## Vulkan presents the game's own frame - with thirteen validation messages to fix

`presentFromComposite` is wired and it does what it says, at the moment the stack trace pointed at:

```
presented the frame from the composite: present image: blitted 1280x720 onto image 1 (0)
status PASSED | nativeTerrainVerified True
```

Minecraft's `Framebuffer.func_178038_a` batch arrives, the bridge is opened with `beginFrameForPresent()` - which reuses
the game framebuffer validated earlier in the frame, because at this moment the default framebuffer is bound - the shared
colour image is blitted into an acquired swapchain image (1280x720, VkResult 0) and presented, and GL's own blit for that
batch is cancelled. That is the present step running on Vulkan.

It is **not trustworthy yet**: the same run produced **13 validation messages**, which is against the project's rule and
is recorded rather than skipped over. The candidates are the ones the clear-and-present probe never had to face: the
layout the shared colour image is actually in when it is blitted (this asks for `COLOR_ATTACHMENT_OPTIMAL`, while
`endFrame`'s copy-back may leave it elsewhere), and the synchronisation of a blit that *writes* the acquired image - the
probe only cleared, so presenting right after the acquire happened to be ordered, whereas writing needs the submission
itself to wait on the acquire semaphore and the presentation to wait on a render-finished semaphore instead.

The switch is off by default, so the shipped path is unaffected and its validation output stays empty; the next step is
to read those thirteen messages, fix the layout and the semaphore pair, and only then consider the present step ported.

---

## The present's remaining validation error is the semaphore pair, exactly as recorded

Reading the messages rather than the repeat-warning lines gives the answer:

```
vkQueueSubmit(): pSubmits[0] performs a layout transition on presentable VkImage 0x80000000008,
but VkSemaphore 0x100000000010 signaled by image acquire operation was not waited on
The Vulkan spec states: After acquiring a presentable image and before modifying it, the application must use a
synchronization primitive to ensure that the presentation engine has finished reading from the image
```

So the second of the two candidates was right: the submission that blits into the acquired image must **wait** on the
acquire semaphore, because the layout transition modifies that image - and the presentation must then wait on a
*signal* from that submission rather than on the acquire semaphore itself. The clear-and-present probe never had to face
this because it presented straight after the acquire, which happened to be ordered; writing to the image is not.

Two fixes landed this round and one is now precisely specified:

- **Landed:** the acquire semaphore is now a pool, one per swapchain image used round-robin. That removed the
  `VUID-vkAcquireNextImageKHR-semaphore-01779` repeats - a single semaphore cannot be re-acquired while a previous wait
  on it is pending, which is what happens once this runs per frame instead of once in a probe.
- **Specified, not yet implemented:** `presentImage` must submit through a path that waits the acquire semaphore and
  signals a render-finished semaphore, and present waiting on that. The current submission goes through the context's
  command-buffer helpers, which submit with a fence and no semaphores, so this needs either a submit variant on the
  context or an inline `vkQueueSubmit` in the swapchain.

Until that is done the present-from-composite switch stays off and the shipped path's validation output stays empty.

---

## The semaphore pair is implemented and the present is still not validation-clean

`VulkanContext.submitWithSemaphores(commandBuffer, waitSemaphore, signalSemaphore)` now exists: it ends the command
buffer, submits with a wait on one semaphore and a signal on another (waiting at transfer and colour-attachment-output
stages), waits a fence so the buffer can be freed, and leaves the queue undrained so the caller's presentation can wait on
the signal. `presentImage` uses it - wait on the acquire, signal a render-finished semaphore from a pool paired with the
acquire pool - and the presentation waits on the render-finished one instead of on the acquire.

It runs and it presents (`present image: blitted 1280x720 onto image 1 (0)`), and the validation output is still **not
clean: ten VUIDs remain**, which is against the project's rule. So the fix addressed the message that was read last round
and the present step is not finished; the counting also shows how easy it is to misread this output - one of the greps
used here matched the layer's informational lines as well, and the honest number is the ten real VUIDs, not the 37 lines
that mention a call.

The state is unchanged in the way that matters: `presentFromComposite` is off by default, the shipped path's validation
output is empty, and the immediate path is pixel-identical on both GPUs with the composite left to GL. What is *not* true
is that present ownership is ported - it runs, and it is not clean yet, and both halves of that sentence go in the record.
The next step is to read the ten current messages (not the previous run's) and decide whether the remaining problem is the
layout the shared colour image is actually in, the fence-wait inside the submit removing the ordering the presentation
needs, or the reuse distance of the render-finished pool.

---

## Both present paths now share the correct submit and the messages are unchanged at 36

The probe's `clearAndPresent` was the remaining path submitting with no semaphores at all, so it was unified onto
`submitWithSemaphores` (wait the acquire, signal a render-finished one, present waiting on that). The result is unchanged:

```
real validation messages: 36
  vkQueueSubmit(): pSubmits[0] performs a layout transition on presentable VkImage 0x80000000008,
                   but VkSemaphore 0x100000000010 signaled by image acquire operation was not waited on
  vkQueuePresentKHR(): pPresentInfo->pImageIndices[0] was acquired with a semaphore
                   VkSemaphore 0x100000000010 that has not since been waited on
```

The same two messages, naming the *acquire* semaphore in both - the first says the submission that transitions the image did
not wait it, the second says the presentation's image was acquired with it and it has not been waited since. Both describe
the same semaphore, and the code demonstrably passes it as `pWaitSemaphores` on the submission and waits a *different*
semaphore at the presentation. So the mismatch is in the pairing, not in the absence of a wait, and no amount of reading
the same two lines again will say which pairing is wrong.

That is the point to stop reasoning and instrument: log, for the first few presents, the acquired image index, the acquire
semaphore handle, the render-finished handle, the semaphore the submit waited and the one the presentation waited - one
line per present - and read the sequence. With handles in hand the error is arithmetic instead of inference.

The state is unchanged and stated plainly: the frame is presented by Vulkan (`present image: blitted 1280x720 onto image
1 (0)`), the run is PASSED with `nativeTerrainVerified True`, and the validation output is **not** clean, so
`presentFromComposite` stays off by default and present ownership is not ported yet.

---

## Present ownership works and is validation-clean; the probe was the offender

The handle trace showed the mechanism is right - for every frame present, `submitWaits` is the same semaphore the
acquire signalled and `presentWaits` is the render-finished one:

```
image=1 acquire=0x110000000011 renderFinished=0x150000000015 submitWaits=0x110000000011 presentWaits=0x150000000015
image=2 acquire=0x120000000012 renderFinished=0x160000000016 submitWaits=0x120000000012 presentWaits=0x160000000016
```

and the semaphore the messages named, `0x100000000010`, was the *first* one - the one the **swapchain probe** consumed
before the first frame present. Turning the probe off and presenting the frame from the composite gives:

```
status PASSED | nativeTerrainVerified True
real validation messages WITHOUT the probe: 0
```

So the present step runs on Vulkan, presents the game's own frames, and is **validation-clean**. The 36 messages were
coming from the one-shot diagnostic probe, whose clear-and-present pair still mismatches after being unified onto the same
submit helper - a small, self-contained follow-up that no longer blocks anything, because the probe is off by default and
the frame path is clean on its own.

That closes the present question in the terms the plan asked for: the frame is composed by Vulkan and put on the screen by
Vulkan, with no validation message, on the tested GPU. What is still open around it is the immediate path's default (it
stays off until the content stage covers more scenes), the probe's own cleanup, and everything the plan lists for stages 4
and 5 - GUI, HUD, sky, weather, entities over more than one world, and real mods.

---

## Correction: the clean run proved less than it looked, because the probe also created the swapchain

Adding the presented-image readback made the gap visible. The backend's own log line for the first composite present
never appeared in the run without the probe, and the reason is simple once seen: **the swapchain is created by the probe**.
With `-Dceltium.vulkan.swapchainProbe=false` the object is null, so `presentFromComposite` takes the early branch, presents
nothing, and the validation output is empty for the trivial reason that no presentation happened.

So the previous entry's reading has to be corrected:

- What still stands: with the probe **on**, sixty-odd frame presents ran and the handle trace showed the correct pairing -
  each submit waiting the acquire semaphore the acquire signalled, each presentation waiting the render-finished one - and
  every message named the semaphore the *probe* consumed first. That evidence is about the pairing and it is unaffected.
- What does not stand: "0 validation messages without the probe therefore the frame present is clean". That run presented
  no frames. The two facts have to be separated, and this entry is where they are.

The fix is small and specific: create the swapchain lazily where it is used (the present path), rather than as a side
effect of the diagnostic probe. Then the probe can be deleted or left off, the frame present runs on its own, and the
question "is the frame present validation-clean" can finally be asked of a run that actually presents.

The readback itself is in place (`-Dceltium.vulkan.presentReadback=true` copies a centre patch of the blitted image to a
host buffer and reports its mean), which is what will make "the screen shows the frame" a measurement - once a run presents
again.

---

## The frame present now runs on its own, and the presented image has content - with 42 messages left

Three gates had to be pulled apart before the present could stand alone, and each one was a place where the *diagnostic
probe* had been standing in for the feature:

1. `ensureSwapchain()` - the swapchain was created as a side effect of the probe, so turning the probe off silently
   disabled the present. Anything that presents now calls this instead.
2. `VulkanContext`'s device-extension gate - `VK_KHR_swapchain` was enabled only when the probe property was set, so a
   present-only run created no swapchain at all.
3. `VulkanSwapchain.create`'s own guard - it also required the probe property, and refused for that reason even once the
   extension was there.

With those fixed, a run with the probe off and `presentFromComposite` on does what it says:

```
presented the frame from the composite: present image: blitted 1280x720 onto image 0 (0)
presented image mean=118.25
```

The mean is the readback measurement this round added, and it says the image Vulkan presented is **not** empty - it carries
the frame's content, in the same range as the game framebuffer's own mean. That answers the question the earlier attempts
could not: the screen is being given the frame, not a blank surface.

Two defects are recorded with it rather than glossed over:

- The readback itself is invalid: `vkCmdCopyImageToBuffer(): srcImage ... was created with
  VK_IMAGE_USAGE_2_TRANSFER_DST_BIT_KHR|VK_IMAGE_USAGE_2_COLOR_ATTACHMENT_BIT_KHR` - the swapchain image was created
  without `TRANSFER_SRC`, so the number above is provisional until that is added (or the readback is dropped and the check
  done another way).
- **42 validation messages remain** in this standalone run, the leading one being the same
  `pSubmits[0] performs a layout transition on presentable VkImage ... but VkSemaphore ... was signaled by image acquire
  operation was not waited on`. So the pairing complaint is not the probe's after all - the probe added its own, but this
  one is the frame path's, and it is now reproducible without any diagnostic flag in the picture.

`presentFromComposite` therefore stays off by default, and the honest summary is: the present runs, the image it presents
has content, and it is not validation-clean yet.

---

## Two candidates eliminated, thirty-six messages unchanged, and the next step is to watch the call itself

Three things were tried and measured against the standalone present (probe off, `presentFromComposite` on):

| Change | Real validation messages |
|---|---|
| readback enabled | 42 |
| **readback disabled** | **36** - so the readback added its own six and is not the cause of the rest |
| readback disabled, wait stage widened from `TRANSFER|COLOR_ATTACHMENT_OUTPUT` to `ALL_COMMANDS` | **36** - unchanged |

So the readback is exonerated, the stage mask is not it, and the messages are the same two that have been quoted for
several rounds: the submission that transitions the acquired image "did not wait" the acquire semaphore, and the
presentation's image "was acquired with" that same semaphore "and has not since been waited on". The code passes that
semaphore as `pWaitSemaphores` and the trace printed it doing so, which means one of two things is true and neither has
been checked: either **the submission that validation is looking at is not the one this code builds**, or the handles
printed are not the handles the driver received.

This project has been wrong twice already about *which code path runs* - the probe silently creating the swapchain, and
the fixture sampling at a call site the run never used - so the next step is to stop reasoning about the code and watch
it: log from inside `submitWithSemaphores` (the semaphore counts and handles it actually sets, and that it is called at
all), and log the present's, in the same run. Two lines per present, no inference.

Unchanged: the frame present runs and the image it presents has content; `presentFromComposite` stays off by default;
the shipped path's validation output stays empty; the immediate path stays pixel-identical on both GPUs with the
composite left to GL.

---

## The submit tracing attempt coincided with a failed run; reverted and the baseline re-verified

The plan was to watch the call itself - log from inside `submitWithSemaphores` the semaphore counts and handles it
actually sets, and that it is called at all, next to the presentation's own line. The run that carried it **failed**:

```
status = FAILED
error = java.lang.IllegalStateException: the Vulkan terrain backend did not render cleanly:
        passes=3 skipped=0 errors=1641 lastDraw=0
```

Twenty-four seconds instead of the usual ~150 - the same early-exit shape seen once before (the format-2-only run in an
earlier round), and the terrain passes reporting an error on every attempt rather than drawing. The tracing is the only
difference from a run that passed minutes earlier, so it is the plausible cause, but that is **not established**: no
message from the terrain path was read, and the tracing code itself is a few prints around a `vkQueueSubmit`. What is
established is the outcome, so the tracing is reverted and the state re-measured rather than assumed:

```
status PASSED | nativeTerrainVerified True | passes 1617
presented the frame from the composite: present image: blitted 1280x720 onto image 0 (0)
```

with the frame present running again. Two things are recorded as known-unknown rather than settled: why that
configuration exits after ~24 s with every terrain pass erroring, and what the present's 36 validation messages are
actually caused by - the `submit` trace meant to answer the second never ran, and the first has now been seen twice.

The next attempt at the trace should be smaller and independent of the frame path: the counts and handles can be logged
from the presentation side alone (they are already known there), and the submission's own view can be inferred by
comparing the handles in the same line rather than by adding prints inside the context.

---

## The call is observed, the wait is there, and the messages still say otherwise

Two measurements close this round, both without printing from inside the context (which is what coincided with the
failed run):

- **Bisection.** With the recording skipped entirely - acquire, present, nothing written - the submit and semaphore
  complaints disappear and only the layout complaint remains, which is expected because nothing transitions the image.
  So the acquire/present usage on its own is not what the messages are about; the recording's submission is.
- **Counters.** `VulkanContext.submitWithSemaphoresCalls` and `submitWithSemaphoresWaits` are incremented inside the
  helper and reported by the backend: `calls=1 withWait=1` at the first frame present. The helper *is* the one running,
  *is* called once per present, and *does* carry a wait - while the validation layer still reports that the acquire
  semaphore "was not waited on" and that the presented image "was acquired with" it "and has not since been waited on".

That eliminates the last code-shaped explanation: the call, the arguments and the wait are all in place, so the
disagreement is about timing rather than about which function runs. The two candidates left are both about a semaphore
being reused before what it orders against has retired:

1. The acquire semaphore is reused **before its presentation has completed**. The fence waited inside the submit covers
   the *submission*, not the presentation, which is asynchronous - so the next acquire can reuse the same semaphore while
   the presentation engine is still waiting on it. That is precisely what "was acquired with a semaphore that has not
   since been waited on" describes, and the standard fix is to track present completion per image (a fence or
   `vkWaitForPresentKHR`) rather than assuming the submit's fence covers it.
2. The image is re-acquired while its own previous presentation is still pending, which the same fix addresses by keying
   the semaphore to the image rather than cycling a pool independently of it.

The counters stay in the tree as the safe form of this diagnostic, and the present keeps its default: off, because the
validation output is not empty, and `presentFromComposite` is one flag away from the verified-by-eye behaviour it already
demonstrates (`present image: blitted 1280x720 onto image 0 (0)`, `presented image mean=118.25` in the readback run).

---

## The reuse experiment did not run: it made javac itself fail

The plan for this round was to test the last candidate directly - a brand-new semaphore pair for every present, never
reused, so that "the acquire semaphore is reused before its presentation retires" would either show up as zero messages
or be eliminated. It did not run, and the reason is worth recording because it wasted a run:

```
> Task :celtium-vulkan:compileJava FAILED
  1 error
  at jdk.compiler/com.sun.tools.javac.comp.TransTypes.visitBinary(TransTypes.java:1023)
```

**javac crashed** on the new code rather than reporting an error in it - the `LongBuffer` allocation with
`position(1)` for the second semaphore handle, compiled under this module's `-source 8`/`-target 8`, walks the compiler
into a stack trace instead of a message. The run that followed used the previous jar, so its "36 messages with fresh
semaphores" were the old pooled configuration's messages and mean nothing about the experiment.

Two lessons, both concrete: a build that fails must be confirmed to have failed *before* reading any measurement from the
run that follows it - the grep used here looked for `error:` and found nothing, which read as success. And the `-source 8`
path is fragile enough that handle-array plumbing is better written with the shapes the rest of this file already uses
(one `mallocLong(1)` per handle, as the pool creation does) than with buffer positions.

The experiment is reverted and the tree is green. It is still the right experiment: it needs to be written in the file's
existing idiom, and the run must be checked for `BUILD SUCCESSFUL` before its validation count is quoted.

---

## Fresh semaphores per present: the reuse theory is eliminated

The experiment that did not run last round ran this time, written one handle per `mallocLong(1)` as the pool creation
does, and **gated on `BUILD SUCCESSFUL`** so a failed build can no longer contaminate the reading:

```
BUILD OK - running the experiment
status PASSED | nativeTerrainVerified True
real validation messages (fresh semaphores): 36
```

A brand-new acquire/render-finished pair for every present, never reused, changes nothing: the same 36 messages, the same
two leading complaints naming the acquire semaphore. So the "semaphore reused before its presentation retires" candidate is
**eliminated**, and with it the per-image present-completion tracking that would have fixed it - that work would have been
wasted.

What that leaves is narrower than it has ever been. The submission is the one this code builds (counted), it carries a wait
on exactly the semaphore the acquire signalled (same local, traced, and now provably a fresh handle), the wait's stage mask
covers all commands, and the messages persist regardless. Two explanations remain, and both are about the *layer's* view
rather than the submission:

- the device is created with the external-semaphore/foreign-queue extensions this bridge needs, and the swapchain's own
  semaphores may be classified differently by the layer than a plain `VkSemaphore` is;
- or the complaints are about the *presentation's* view of the image - that the presentation engine has not released it -
  which the specification orders through the acquire, and which the layer cannot observe and may be reporting
  conservatively.

Both are testable without touching the present path: a minimal Vulkan program that does exactly this sequence (acquire,
barrier, blit, present with a fresh semaphore pair) and nothing else would show whether these messages are this code's or
this configuration's. That is the experiment the next round should build, because continuing to vary this code has now cost
several rounds with the messages unmoved.

---

## The immediate path is the default, and the default is pixel-identical on both GPUs

The verified configuration is now what ships, with no flags set:

```
status PASSED | nativeTerrainVerified True
       AMD RX 6400:  0.00 mean, 0.0% over 8 and over 32 against its own GL reference
       Intel UHD 630: 0.00 mean, 0.0% over 8 and over 32 against its own GL reference
```

Defaults: `celtium.vulkan.immediate=true` (36 batches drawn by Vulkan in the measured frame),
`celtium.vulkan.immediateWindows=true` with `immediateFlushIndices=512` - the window flush is what makes the path
lag-free, since it draws each batch in the frame that produced it - and the acceptance mask `1,3,4,5`, which keeps the
24-byte family on GL because in this scene it is the framebuffer-to-screen composite rather than content.

That is stage 3's deliverable in the plan's terms: the draw and upload entry points Minecraft and Forge mods use are
served by Vulkan by default, for every layout that carries content, with the frame measured identical to GL on both target
GPUs. It is deliberately not a claim about the whole renderer, and the plan's status table says why in the same row: the
composite and the presentation of the frame are still GL's, the Vulkan present exists but is off because it emits
validation messages, and the content that has not been exercised yet - entities with models, particles in motion, GUI over
more than one world, resource reload, resize, world switch - is what stages 4 and 5 are for.

---

## Two findings: the present messages are not driver-specific, and the texture mirror is one-shot

**The present's 36 messages reproduce identically on both drivers.** The same configuration run on the Intel UHD 630
(ANV) as on the RX 6400 (RADV) gives the same count and, notably, the *same handle values* in the messages
(`0xd700000000d7`, `0xdf00000000df`) - which is explained by both runs creating the same objects in the same order, not by
anything shared between the drivers. So this is not a RADV quirk and not a driver bug: with the submission demonstrably
carrying the wait (counted), a fresh semaphore per present (measured), and a stage mask covering all commands, the two
remaining explanations stay the ones recorded - the layer's view of semaphores on a device created for external
semaphore/memory, or its conservative reporting of the presentation engine's release of the image.

**The texture mirror uploads once, and the default configuration now depends on it.** `VulkanTextureRegistry` says so in
its own comment and offers `invalidate(int)`, described as "drops one mirror so the next use re-uploads it; used when a
texture's contents are known to have changed". Nothing calls it for animated textures, and Minecraft animates water, lava,
fire and every other animated sprite by re-uploading through
`TextureUtil.func_110989_a(int glTextureId, BufferedImage image, boolean blur, boolean clamp)` - a method that carries the
GL texture id, which is exactly what an invalidation hook needs.

That is a real gap in what is now the shipped default: any animated texture drawn through the immediate path would show
its first frame forever. It is recorded as P1 with the hook point identified rather than fixed in this round, because
registering a new mixin needs the config path this project uses confirmed first (the plugin loads
`/mixins.celeritas.json`, whose `client` array is empty, so the core mixins are applied by some other mechanism that has
to be read before adding one more).

---

## Texture invalidation is hooked, the crash it caused is fixed, and whether it fires is still unproven

The P1 recorded last round is implemented:

- `TextureUtilMixin` injects at the head of `TextureUtil.func_110989_a(int glTextureId, BufferedImage, boolean, boolean)`
  - Minecraft's own re-upload for animated sprites - and calls the backend. Registration needed no configuration work:
  the mixin plugin scans `org.taumc.celeritas.mixin` for mixin-annotated classes, so a new class in that package is picked
  up, which is why the whole `core` package is applied through the plugin while `mixins.celeritas.json`'s `client` array is
  empty.
- `TerrainGpuBackend.invalidateTexture(int)` is the boundary method, the Vulkan backend forwards it to
  `VulkanTextureRegistry.invalidate(int)`, and the mixin body is wrapped so a bookkeeping hint can never break a texture
  upload.

The first attempt **crashed the client** during texture registration - the registry is created lazily, and Minecraft
uploads textures before any world draw, so invalidating through a registry that does not exist yet threw. That is fixed
with a null guard, and the run is green again:

```
BUILD OK
status PASSED | nativeTerrainVerified True
```

What is **not** proven is that the hook fires during play: the counter meant to report how many invalidations happened did
not appear in the run's debug line, so either it was never incremented or it is not being printed - the two have not been
separated. Until that is read, the honest statement is that the mechanism is in place, guarded, and does not break
anything, not that animated textures now update.

---

## The hook fires, but it was hooked to the wrong method - the animation path is `func_147955_a`

Reading the sources answered what the counter could not. The registration hook demonstrably fires (ids 7, 8, 13, 16, 17,
one line per upload, from inside the mixin where the log definitely sees it), and yet **no texture was ever uploaded
twice** - because `func_110989_a` is not the animation path:

```
TextureMap.func_94248_c()                    // the animation tick
  -> TextureUtil.func_94277_a(glTextureId)   // binds the atlas
  -> TextureSprite.func_94219_l()            // per sprite
       -> TextureUtil.func_147955_a(int[][] data, w, h, x, y, blur, clamp)   // uploads the frame
```

`func_147955_a` carries **no texture id**, because it writes into whatever texture is bound - which is exactly why the
first hook looked plausible and was wrong, and why the counter never moved on a repeat.

So the animation injector is now on `func_147955_a`, with `TerrainGpuBackend.invalidateBoundTexture()` as its boundary
method and the bound name coming from the state mirror that already follows `GlStateManager.func_179144_i`. The run is
green: BUILD OK, PASSED, `nativeTerrainVerified True`.

What is still not proven is that invalidations now happen - the backend counter's reporting path is unreliable (it has not
appeared in the debug line in three runs, while a print from inside the mixin appears immediately), so the next check is a
print from the animation injector itself, the same shape that produced the ids above. Until then the honest statement is:
the mechanism is now attached to the method the sources identify as the animation path, it does not break the run, and
whether it fires has not been read.

---

## The animation hook fires - the P1 is fixed and verified

The print from inside the animation injector settles it:

```
animation upload: hit=1 size=32x32 at 0,0
animation upload: hit=2 size=32x32 at 32,0
animation upload: hit=3 size=16x16 at 0,32
animation upload: hit=4 size=16x16 at 16,32
animation upload: hit=5 size=16x16 at 32,32
status PASSED | nativeTerrainVerified True
```

Those are sprite frames being uploaded into the bound atlas, one after another - the animation path identified from the
sources - and each one now invalidates the mirror of the bound texture, so the next immediate draw re-reads the GL texture
instead of showing the frame it first saw. Together with the registration hook (ids 7, 8, 13, 16, 17) both upload paths
Minecraft uses are covered:

| Upload path | Method | Covered |
|---|---|---|
| Registration and full-image uploads | `TextureUtil.func_110989_a` | yes, verified firing |
| **Sprite animation frames** | `TextureUtil.func_147955_a` | **yes, verified firing** |

So the P1 recorded two rounds ago - "an animated texture drawn through the immediate path shows its first frame forever" -
is closed at the mechanism level, with the evidence being the hook firing in a green run rather than an argument. What a
still image cannot show is that the animation *moves* in the Vulkan path; the comparison frames are static, so motion
remains for a visual check rather than being claimed here.

---

## Resize: the test is written down, and the fixture cannot build it yet

The plan lists resize as unported until it has been seen to work, and the cheapest way to see it is from the fixture:
resize the window mid-run before the capture, and let the saved screenshot's own dimensions be the evidence that the
resize reached Minecraft. The test was written that way - `glfwSetWindowSize` to 1024x640 at tick 100, with
`resizedAtTick`, `captureWidth` and `captureHeight` added to the report - and it does not build:

```
ValidationClient.java:410: error: package org.lwjgl.glfw does not exist
```

The validation fixture compiles without the GLFW binding: the mod only needs the core LWJGL for `MemoryUtil`, which is
why this had not come up in any earlier fixture work. Two small ways forward, both one change: add `lwjgl-glfw` as a
compile-only dependency of the validation module, or drive the resize through Minecraft's own path so the fixture needs no
new dependency.

The change is reverted, the tree is green (`packageJar` and `validationRemapJar` both pass), and nothing about resize is
claimed either way - it stays on the unported list, now with the method and the obstacle written down so the next attempt
starts from here rather than from scratch.

---

## The resize harness works and the resize does not reach Minecraft in it

With `lwjgl-glfw` added as a compile-only dependency of the validation module, the fixture can drive a resize, and the
run reports what happened:

```
status PASSED | nativeTerrainVerified True
windowResizable True | resizedAtTick 100 | captureWidth 1280 | captureHeight 720
screenshot size: (1280, 720)
```

So the call is made on a window that reports itself resizable, and Minecraft's framebuffer is **still 1280x720** a hundred
ticks later, at capture time. The first attempt said the same thing and the extra attribute read rules out the obvious
explanation: this is not a non-resizable window refusing the request, it is a size request that the client never turns
into a framebuffer change in this harness.

That is worth stating precisely, because it means **resize stays unverified** - not because the renderer failed, but
because this setup cannot exercise it. The likely reasons are the ones a real client would not have: the harness runs the
client without a window-manager resize, and Cleanroom's LWJGL shim may take its display size once at startup rather than
from the window each frame.

Two things are kept from the round: the compile-only dependency, which is what lets the fixture see the window at all, and
the harness itself (the `celtium.validation.resize` switch with its `windowResizable` and `resizedAtTick` report fields),
since it is fixture-only, harmless in a green run, and is the instrument any future resize attempt will start from. What is
not claimed is that the renderer handles a resize; the plan keeps it on the unported list.

---

## Resource reload works on both GPUs

The fixture now performs the same reload F3+T does - `mc.renderGlobal.loadRenderers()` to rebuild every chunk renderer and
`mc.getTextureManager().onResourceManagerReload(mc.getResourceManager())` to re-stitch every texture - in the middle of a
run, and both GPUs come through it:

| GPU | Result |
|---|---|
| AMD RX 6400 | **PASSED, `nativeTerrainVerified True`**, reloaded at tick 100, 901 passes |
| Intel UHD 630 | **PASSED, `nativeTerrainVerified True`**, reloaded at tick 100, 591 passes |

So the renderer survives the two things a reload does to it: every chunk mesh is thrown away and rebuilt, and every
texture is uploaded again. The second half is the interesting one for the immediate path, because a re-stitched atlas
produces new GL texture contents for ids the mirror has already seen - exactly the case the upload-invalidation hooks from
the previous rounds exist for - and the frame afterwards is clean.

Reload moves off the plan's unported list; what stays there for the same area is resize, which this harness cannot exercise
(the window accepts the size request and Minecraft's framebuffer never changes), and world switch, which needs a second
world rather than a second call.

---

## The world-switch attempt crashed vanilla, and that is not evidence about the renderer

The fixture was told to switch worlds the way the plan lists it - leave the world, join the same save again - by calling
`mc.world.sendQuittingDisconnectingPacket()` and `mc.loadWorld(null)` from the tick handler at tick 100. The client
crashed:

```
java.lang.NullPointerException: Cannot invoke "EntityPlayerSP.func_70082_c(float, float)" because
  "this.field_78531_r.field_71439_g" is null
  at net.minecraft.client.renderer.EntityRenderer.func_181560_a(EntityRenderer.java:1088)
  at net.minecraft.client.Minecraft.func_71411_J(Minecraft.java:1109)
```

Read carefully, that stack is **vanilla**: the game loop runs one more frame with no player after `loadWorld(null)` was
called from inside the tick, and `EntityRenderer` dereferences the player it expects to exist. Minecraft's own disconnect
path tears the world down through the GUI and its scheduled tasks, not from the middle of a tick, which is why the button
works and this does not.

So the crash says nothing about Celtium - it is a fixture calling an internal teardown API from a place the game does not
call it from - and the test is reverted with the tree green. The recording matters more than the attempt for the next
round:

- a world switch has to be driven the way the game drives it (the disconnect path through the GUI, or a scheduled task),
  not by calling `loadWorld` from a tick handler;
- and a fixture-induced crash in vanilla code is not evidence about the renderer, so a test that crashes this way has to be
  fixed or discarded rather than reported as a renderer failure.

---

## World switch works on both GPUs, and the previous crash was the fixture's missing call

The same test, with the third call the vanilla path makes:

| GPU | Result |
|---|---|
| AMD RX 6400 | **PASSED, `nativeTerrainVerified True`**, left and rejoined at tick 100, 885 passes |
| Intel UHD 630 | **PASSED, `nativeTerrainVerified True`**, left and rejoined at tick 100, 591 passes |

`GuiIngame`'s "Save and Quit to Title" makes three calls - `sendQuittingDisconnectingPacket()`, `loadWorld(null)`,
`displayGuiScreen(new GuiMainMenu())` - and the previous round's crash came from making only the first two. With all three
the client leaves the world, joins the same save again, and the Vulkan terrain path keeps drawing through both: 885 and 591
passes, no validation message, run PASSED.

That closes the second of the three items the plan listed as unported around the frame's lifetime. The renderer now has
evidence for resource reload and world switch on both GPUs; what remains in that group is resize, which this harness cannot
exercise, and the present question, which is about validation-layer agreement rather than about the picture.

---

## The two messages reproduce with the probe alone, once - so they are about the semaphore shape

Narrowing this round was cheap and decisive. Core-only validation still reports them (27 messages with the frame path, so
they are core rather than synchronization-validation findings), and the **probe on its own** - one acquire, one clear, one
present, no blit, no bridge, no frame, no repetition - reports **exactly the same two**:

```
present probe: cleared and presented image 0 (0)
PROBE-ONLY validation messages: 2
  vkQueueSubmit(): pSubmits[0] performs a layout transition on presentable VkImage ..., but VkSemaphore ...
                   signaled by image acquire operation was not waited on
  vkQueuePresentKHR(): pPresentInfo->pImageIndices[0] was acquired with a semaphore ... that has not since been waited on
```

That removes everything the frame path does from the explanation: not the blit, not the bridge, not the number of presents.
What is left is the shape of the semaphore use, and this project already has a data point for a **different shape that was
clean**: this same probe measured zero validation messages in earlier rounds, when it submitted through `endCommands` -
that is, with **no semaphores at all** - and presented waiting the **acquire** semaphore directly. The current shape waits
the acquire in the submission and signals a render-finished semaphore that the presentation waits instead.

So the trigger is the shape introduced when the blit needed the acquire to be waited, and the next experiment is one change
rather than a program: make `clearAndPresent` use the earlier shape again and confirm the count returns to zero. If it does,
the two shapes differ in exactly one respect and the frame path needs the correct one - a submission that writes the
acquired image genuinely must wait the acquire - with the layer's disagreement about that being the real question, rather
than anything this code does wrong that has not already been ruled out.

---

## The shapes are separated: the present complaint is about waiting, the submit complaint is not about this code

The probe's earlier shape - submit with no semaphores, present waiting the acquire - was restored behind a switch and
measured:

| Probe shape | Validation messages |
|---|---|
| submit waits acquire, present waits render-finished (the frame path's shape) | **2** |
| **submit with no semaphores, present waits the acquire** (the earlier shape) | **1** |

So the two complaints have different causes and only one of them is about the shape:

- The **present** complaint ("pImageIndices[0] was acquired with a semaphore ... that has not since been waited on")
  disappears the moment the presentation waits the semaphore the acquire signalled. That is a real, wanted result: the
  present path should wait the acquire when nothing else does.
- The **submit** complaint ("performs a layout transition on presentable VkImage ... but VkSemaphore ... was not waited
  on") is present in **both** shapes - including the one where the submission demonstrably *does* wait the acquire, as the
  counters showed and the fresh-semaphore run confirmed. So it is not about what this code passes; it is about how the
  layer classifies the acquire semaphore or the transition on a device created for external semaphore and memory.

That leaves the frame path exactly where it should be: it keeps the correct shape (a submission that writes the acquired
image waits for the acquire), the presentation waits the render-finished signal, and one validation message per present
remains that this code cannot remove by changing what it passes. The switch stays in the tree as the evidence for that
statement, off by default, and the frame present stays off by default too - one message is still a message.

---

## The external audit was right: waitSemaphoreCount was never set, and that was the whole explanation

The audit pointed at primary generated code rather than at behaviour, and checking it here from the LWJGL 3.4.1 bytecode
confirms it exactly:

| Setter | What it does |
|---|---|
| `VkSubmitInfo.npWaitSemaphores(long, LongBuffer)` | `memPutAddress` - **the pointer only, no count** |
| `VkSubmitInfo.npSignalSemaphores(long, LongBuffer)` | pointer **and** `nsignalSemaphoreCount(remaining())` |
| `VkSubmitInfo.npCommandBuffers(long, PointerBuffer)` | pointer **and** `ncommandBufferCount(remaining())` |
| `VkPresentInfoKHR.npWaitSemaphores(long, LongBuffer)` | pointer **and** `nwaitSemaphoreCount(remaining())` |

So `VkSubmitInfo` is the one that needs an explicit count, and both submission sites in this project built theirs with
`calloc` and never set one: `VulkanTerrainGpuBackend.submitCommands` and `VulkanContext.submitWithSemaphores`. Every
submission therefore ran with `waitSemaphoreCount = 0` - **nothing was ever waited on**: not the GL-to-Vulkan bridge
semaphores, not the window-system acquire semaphore. The presents were fine, because that struct's setter does set its
count.

With `submit.waitSemaphoreCount(...)` set before `pWaitSemaphores` in both places, the frame present measures:

```
status PASSED | nativeTerrainVerified True | passes 2148
presented the frame from the composite: present image: blitted 1280x720 onto image 1 (0)
PRESENT + WAIT FIX validation messages: 0
```

**Zero** validation messages, against 36 before. This is the correction the audit asked for, and it retires several of this
log's own conclusions:

- "the submission demonstrably carries the wait (counted)" - the counter counted the *argument*, not the struct; the struct
  said zero. A handle log and a call counter are not evidence that a wait happened, and this log treated them as if they
  were.
- "the messages are not driver-specific, therefore the layer's view of external semaphores" - wrong. They were
  driver-independent because the defect was in the code, identical on both drivers. A hypothesis that explains
  driver-independence by blaming the layer was available and was wrong.
- "a fresh semaphore pair per present changes nothing" - true, and consistent: no semaphore was waited on at all, fresh or
  reused.
- The multi-window black frame now has a second, sufficient explanation: handoffs that were never waited on. The one-window
  limit is still a real observation, but it was not established as the cause.

---

## Audit C reproduced, then fixed: 13 messages on reload, 0 after settling the GPU

The audit's C was measured before it was changed, and it was real - the reload run reported:

```
exitCode 0 | fixtureStatus PASSED | validationMessageCount 13 | validationClean False
first: vkDestroyBuffer(): can't be called on VkBuffer 0xdd00000000dd that is currently in use by VkCommandBuffer ...
```

a PASSED fixture with thirteen messages inside it, which is exactly the shape the audit's F describes and which the new
harness guard now reports. `settleGpu(reason)` - `vkDeviceWaitIdle` with a message rather than a hang if the device stops
making progress - is called before `delete()` releases anything and before a region is evicted, and the same runs measure:

| Run | Before | After |
|---|---|---|
| Reload, RX 6400 | 13 messages | **0**, `validationClean True`, 897 passes |
| Reload, UHD 630 | (same path) | **0**, `validationClean True`, 585 passes |
| World leave and rejoin, RX 6400 | (same path) | **0**, `validationClean True`, 885 passes |

With the P0 fix in place this ordering matters more than it did: submissions genuinely wait now, so a resource released
while the GPU still reads it is a real hazard rather than a mostly-theoretical one.

---

## Audit B's core: the present now honours the bridge contract as well as the swapchain's

The contract was not a matter of interpretation - `VulkanFrameTarget` documents it: the caller's submission must wait
`waitSemaphore()` (the shared image is complete only once GL has signalled) and must signal `doneSemaphore()`, because
`endFrame` makes GL wait on that one, and the class warns that "waiting for a submission that was never sent deadlocks the
render thread". The present path did neither: it waited only the window-system acquire and signalled only its own
semaphore, which is the failure the audit names.

`VulkanContext.submitWithSemaphores` now accepts several semaphores on each side, with explicit counts, and `presentImage`
satisfies both contracts in one submission:

| Side | Before | After |
|---|---|---|
| waits | window-system acquire | acquire **and** the bridge's `waitSemaphore` |
| signals | its own render-finished | its own **and** the bridge's `doneSemaphore` |

Measured: **PASSED, `nativeTerrainVerified True`, 2152 passes, `validationMessageCount 0`, `validationClean True`** - the
harness guard active, so this is a clean run and not a run whose log says otherwise.

What remains inside B is written down rather than implied: the shared image's old layout is asserted instead of tracked,
the source is left in `TRANSFER_SRC` rather than handed back in the layout GL expects, and the caller still treats a
returned failure string as success instead of leaving GL's composite in place when the present did not happen.

---

## The fail-closed caller broke the run, so it is reverted with its evidence

The audit's B and E both end at the same place - the caller of `presentImage` - and the attempt to act there failed in a way
worth recording precisely.

`presentImage` now records two facts: `lastPresentSubmitDone` (the submission that signals the bridge's done semaphore was
made, so `endFrame` is safe) and `lastPresentSucceeded` (the frame actually reached the presentation engine, so GL's
composite may be cancelled). The caller was changed to gate on them: call `endFrame` only when the submission happened, and
return `false` - leaving GL's composite in place - when the present did not reach the screen.

Measured immediately after:

```
exitCode 0 | validationMessageCount 0 | validationClean True
fixture FAILED | nativeTerrainVerified False | passes 4
```

Four terrain passes instead of two thousand, so the change broke the path rather than making it safer, and the validation
output stayed clean while doing it - a reminder that a clean log is not a working run. The reverted state measures
**PASSED, `nativeTerrainVerified True`, 2178 passes, `validationMessageCount 0`**, so the flags are reported and nothing
acts on them yet.

The likely explanation, to be instrumented rather than guessed next time: gating `endFrame` means the bridge's copy-back
does not happen on that path, and whichever present takes it leaves the frame half-handled; the failure count would say
whether `lastPresentSucceeded` was false (the caller bailing out) or `lastPresentSubmitDone` was (the bridge left
uncopied). Printing which branch was taken is the next step, not another mutation of the same three lines.

---

## Fail-closed present, second attempt: measured first, and it works

The previous round's failure was left as a question - which flag was false - and this round answered it with a print
instead of another mutation:

```
composite present: submitDone=false succeeded=true frameTargetWidth=1280 result=present image: blitted 1280x720 onto image 0 (0)
```

A present that succeeded while its submission was reported as never made can only mean the flag was set somewhere else, and
it was: `lastPresentSubmitDone = true` had been inserted at the first textual match, which is inside `clearAndPresent` (the
probe), not inside `presentImage`. So the gating worked exactly as written and skipped `endFrame` on every frame, which is
why the bridge's copy-back never happened and the run dropped to four terrain passes.

With the flag set inside `presentImage` - and the gating re-applied unchanged - the same run measures:

```
composite present: submitDone=true succeeded=true
exitCode 0 | validationMessageCount 0 | validationClean True
fixture PASSED | nativeTerrainVerified True | passes 2159
```

So the caller is fail-closed now: it ends the frame only when the submission that signals the bridge's done semaphore was
made, and it leaves GL's composite in place when the present did not reach the screen - the audit's B3 and the
`endFrame`-on-failure half of E. The lesson is the one this log keeps relearning: an edit that silently fails to apply is
worse than one that fails loudly, because the code then behaves as if the change were made, and only the measurement
notices.

---

## The fail-closed endFrame guard: correct intention, wrong flag, measured and reverted

The audit's E ends at the same place B did - what the caller does when a submission did not happen - and the attempt used
`submissionPending`, the backend's own record of an outstanding submission, as the test:

```
default (terrain + immediate): fixture FAILED, passes 1, fail-closed path taken
present enabled:               fixture FAILED, passes 1, fail-closed path taken
```

Both configurations dropped to a single terrain pass with the guard refusing to end every frame, which says the flag is not
what it looks like from the outside: `submissionPending` is cleared by the idle wait before `endFrame` runs, so at that
point it reports "nothing is outstanding" for a frame that submitted perfectly well. The guard was therefore refusing a
frame that had already been handed over, on every frame, and the run died the way the earlier attempt did.

So the intention stays and the mechanism changes: "did this frame's submission reach the queue" has to be recorded **per
frame** by the submission itself and read immediately before `endFrame`, not inferred from the GPU-idle bookkeeping whose
lifecycle belongs to something else. The change is reverted, the state is green again (fixture PASSED, `nativeTerrainVerified
True`, and the pass count back in the thousands), and the audit's E keeps its open half with a precise statement of what the
next attempt must not do.

---

## Layout tracking for the shared image: the design is written down, the edit was not landed

The audit's B ends with the shared colour image's layout: the present path is told
`COLOR_ATTACHMENT_OPTIMAL` rather than reading it, and the image is left in `TRANSFER_SRC_OPTIMAL` afterwards rather than
returned to the layout the frame target keeps using.

The design is small and is recorded here so the next attempt starts from it rather than rediscovering it:

- `VulkanFrameTarget` should track the layout of its own shared colour image. Its commands are the only ones that move it
  - the render pass leaves it as a colour attachment, GL's blits are not Vulkan commands and do not enter this bookkeeping -
  so the tracker stays consistent across a frame. `colorImageLayout()` reads it and `noteColorImageLayout(int)` records
  what a caller left behind.
- `presentImage` should take the source layout **from that tracker** and be told the layout to hand the image back in,
  recording a barrier from `TRANSFER_SRC_OPTIMAL` to it after the blit instead of leaving the transfer layout behind.

The attempt did not land. Two text-surgery failures, the same pair this log has recorded before: the insertion into
`VulkanFrameTarget` went into the middle of an expression rather than after a statement, and the script that followed
raised before applying the swapchain and backend halves. Nothing about the change is therefore in the tree; it is reverted,
the build is green, and the design above is what the next attempt implements - with a statement read before it is edited,
and with every replacement asserted rather than assumed.

---

## The shared image's layout is tracked, not asserted, and handed back

The design recorded last round is implemented, and this time the edits were made by reading each statement first and
asserting every replacement - the two habits whose absence cost the previous two attempts.

- `VulkanFrameTarget` tracks its own shared colour image layout. The assignment sits after the copy-in statement, because
  that is the moment the bridge's commands put the image into the colour-attachment layout, and it exposes
  `colorImageLayout()` / `noteColorImageLayout(int)`. GL's blits are not Vulkan commands, so they do not enter this
  bookkeeping and it stays consistent across a frame.
- `presentImage` takes the source layout from that tracker and a seventh argument for the layout to hand the image back in,
  recording a barrier from `TRANSFER_SRC_OPTIMAL` to it between the blit and the transition to present.
- The backend reads the tracked layout, passes the colour-attachment layout as the one to restore, and records the
  hand-back so the owner's bookkeeping follows what the commands actually did.

Measured with the harness guard active:

| Configuration | Result |
|---|---|
| Frame present | **PASSED, `nativeTerrainVerified True`, 3264 passes, 0 messages** |
| Default (terrain + immediate) | **PASSED, `nativeTerrainVerified True`, 1665 passes, 0 messages** |

The audit's B therefore keeps only the swapchain-recreation case, which needs a resize or an out-of-date result that this
harness cannot produce.

---

## A process change, because the same failure has now cost three rounds

The out-of-date swapchain work failed the same way as the two rounds before it: a batch of text replacements applied by
one script, the script's assumptions about where methods begin and end wrong, and the file left in a state the compiler
rejects. Each time the fix was to revert and rerecord, which is honest but expensive - and the pattern is now clear enough
to name: **these files are too large to edit by bounded-segment surgery in one pass**.

So the process changes rather than the resolution to be more careful:

- **One replacement per step, with a compile between them.** Not a batch, not a script that edits three files and reports
  at the end. Each step is small enough that a failure identifies itself.
- **The anchor is the statement being changed, read from the file immediately before the edit**, never a method signature
  reconstructed from memory - the last three failures all came from recalling a name or a boundary rather than reading it.
- **`git diff --stat` after each step**, so a replacement that matched nothing is caught by the diff rather than by the
  compiler three edits later.

The out-of-date design itself is recorded for when it is done this way: record `VK_SUBOPTIMAL_KHR` and
`VK_ERROR_OUT_OF_DATE_KHR` from both the acquire and the present instead of accepting them silently, and let the backend
drop the swapchain so the next present's `ensureSwapchain()` rebuilds it - the lazy creation that already exists is the
whole rebuild.

---

## Swapchain recreation exists, built one step at a time as the new process requires

Four steps, each a single replacement, each compiled before the next, each confirmed by `git diff --stat` - the process
written down last round after three consecutive failures of the batch approach:

| Step | Change | Result |
|---|---|---|
| 1 | `outOfDate` field and getter on the swapchain | +14 lines, compiles |
| 2 | the acquire records `VK_SUBOPTIMAL_KHR` / `VK_ERROR_OUT_OF_DATE_KHR` | +5 lines, compiles |
| 3 | the present records them too | +3 lines, compiles |
| 4 | the backend drops the swapchain when it sees the flag, so the next present's lazy `ensureSwapchain()` rebuilds | +7 lines, compiles |

Measured afterwards, to show the healthy paths are untouched and the rebuild stays dormant:

| Configuration | Result | Out-of-date branch |
|---|---|---|
| Frame present | **PASSED, 3256 passes, 0 messages** | not taken |
| Default (terrain + immediate) | **PASSED, 1652 passes, 0 messages** | not taken |

So the audit's B is complete in the sense that a path for recreation exists and does not disturb anything; what is not
demonstrated is the branch itself, because this harness cannot drive a resize and neither driver reports sub-optimal at this
size. That is the same honest shape as the fail-closed frame path: built and compiled, not exercised. The process change is
what made the difference - four small edits landed where three batches had failed.

---

## The fail-closed frame path is exercised, and the numbers say why it matters

`-Dceltium.vulkan.failSubmits=N` refuses the first N submissions, so the path the audit's E asked to see can be shown rather
than only compiled. With two refusals - the frame's submission and its recovery - the run reports:

```
injected submit failure (1 left): this submission is not sent
no submission reached the queue this frame, so the frame is not ended and GL is not left waiting on a semaphore that was
never signalled
fixture FAILED | passes 1 | errors=1647
exited in 26s
```

The fixture failing is the intended outcome of an injected fault. What matters is the arithmetic around it: **1647 frames**
found no submission, and the wait they avoided is bounded at **five seconds** each. A build that ended the frame anyway
would have spent hours in GL waits; this one ended in twenty-six seconds, and the harness's own timeout never had to fire.
That is the difference between a guard that is written and a guard that works, and it is the first time in this audit that a
failure path has been demonstrated instead of argued.

---

## Batches the frame cannot draw are refused in-frame rather than deferred

The audit's D ends with a design point, not only a measurement: acceptance has to be guaranteed before GL's draw is
cancelled, so that a batch is never cancelled and then drawn a frame late. With the window budget at sixteen that case is
rare, but the code now handles it the way the audit asks:

- when the frame's window budget is spent, **and** no group of this frame is being held, **and** no window is open, the
  batch is **refused** - GL draws it in the frame that produced it;
- the cost is named rather than hidden: `immediateRefusedWindowBudget` counts how often geometry was left to GL for this
  reason, and the refusal message says so.

Measured on the shipped configuration:

| Check | Result |
|---|---|
| Fixture | **PASSED, `nativeTerrainVerified True`, 1667 passes** |
| Validation | **0 messages, `validationClean True`** |
| Frame against the GL reference | **0.00 mean, 0.0% of pixels over 8** |

So the change does not disturb anything measured. Whether the refusal actually fires at a sixteen-window budget is **not**
verified: the counter did not appear in the run's debug line, and that is either because it never fired or because the
reporting line did not take the new field - the same distinction this log has had to draw before, and it is drawn here
rather than guessed.

---

## The refusal counters are reported now, and why the firing is still unobserved

The counters that would show the in-frame refusal firing were not on any reported line, so they were added to the one
snapshot this backend prints - at exactly 500 accepted batches - together with `deferredNextFrame`. The run that should
make refusals frequent, a window budget of one, measures:

```
fixture PASSED | nativeTerrainVerified True | 1663 passes
(no snapshot line)
```

and the absence has a precise, checkable cause rather than being a mystery: the snapshot fires when the accepted count
**equals 500**, and with a budget of one the refusals happen *before* a batch is counted as accepted, so the counter can
stall below 500 and the snapshot never prints. The reporting mechanism is the limitation here, not the refusal logic.

So the state of this item is: the refusal is implemented, does not disturb the shipped configuration (verified earlier at
0.00 against the GL reference and 0 validation messages), and its counters are wired to a line that a low-budget run does
not reach. Making it observable needs a threshold that a run actually crosses - a counter printed periodically, or the
refusal count included in the run summary - and that is the next step rather than another attempt to force the firing.

---

## The in-frame refusal fires, and nothing is deferred

The previous round explained why the refusal counters were never printed - they were wired to a snapshot that fires at
exactly 500 accepted batches, and a low-budget run refuses batches *before* they are counted, so that count stalls below
500. The fix was to report the counters where the event happens, and it produces the observation the item was missing:

```
first batch left to GL because the frame's window budget was spent: windowsThisFrame=1 limit=1 deferredNextFrame=0
fixture PASSED | nativeTerrainVerified True | 1657 passes
```

with the window budget forced to one, which is sterner than the sixteen that ship.

`deferredNextFrame=0` is the property the audit's D asked for: geometry the frame cannot draw goes to **GL in that frame**,
and none of it is carried into a later one. The deferral path still exists for the case where a group of the current frame
is already being held - which is why the guard checks for that before refusing - but in a run that would previously have
deferred every excess batch, nothing is deferred at all.

So D closes with its measurement, its cost named (`immediateRefusedWindowBudget`, and the geometry that goes to GL), and the
configuration it was verified in - stated as sterner than the shipped one rather than as identical to it.

---

## With the GUI visible the default configuration differs from GL, and it is not the HUD

Every pixel-identical result in this log was measured with the HUD hidden, because the fixture sets `hideGUI = true` a few
ticks before the capture so that "chat lines, the hotbar or overlay text" cannot contribute differences. That made the
comparison clean and left the HUD unmeasured - a gap that only shows when the switch is turned around
(`-Dceltium.validation.keepGui=true`).

With the GUI visible, the shipped default against a same-scene reference whose immediate path is off measures:

```
immediate on:  PASSED, nativeTerrainVerified True, 1676 passes
immediate off: PASSED, nativeTerrainVerified True, 1662 passes
difference:    mean 1.58 | 1.19% of pixels over 8 | 0.80% over 32
```

and the localisation says what it is not: **hotbar, crosshair, chat and the overlay region all differ by 0.00%**, while the
whole difference sits in a world-space band at x 273..935, y 387..575 - middle and lower-middle of the frame. So the HUD
that the switch was turned on to measure is drawn correctly, and something else in the frame changes when the GUI is
present: the held item and the batch ordering that shifts once the GUI's own batches join the same frames are the
candidates, and the next step is to identify which one by instrumenting which batch or pass lands in that band rather than
by guessing between them.

That is a real, located, reproducible gap in the shipped configuration - small (1.2% of pixels, none of them far off) and
nowhere near the "no differences" the hidden-GUI runs reported, which is exactly why the difference between "measured with
the HUD hidden" and "measured" has to be stated wherever the earlier results are quoted.

---

## The GUI-visible "difference" was the held item animating between runs

One round after recording a 1.58 difference as a gap in the GUI path, the control that should have come first was run: the
**same configuration twice**.

```
reference vs reference (noise floor):  mean 3.72 | 2.93% of pixels over 8 | bbox x 9..935, y 387..575
immediate on vs reference:             mean 3.68 | 2.94% over 8            | bbox x 9..935, y 387..575
```

The difference between the immediate path and the reference is **smaller than the run-to-run variation**, with an
**identical bounding box** - the band the first-person held item occupies, which swings between frames. So:

- the earlier reading was **wrong**, and it is retracted here: with the GUI visible there is no established renderer
  difference, and the 1.19% (then 3.68 as the runs moved) is the scene's own variation;
- the hidden-GUI comparison keeps its 0.00 noise floor and stays the like-for-like measurement it always was;
- any future GUI-visible claim needs the noise floor beside it, exactly as this project's measurement rules require - a
  rule applied here only after publishing a claim without it.

The cause of the variation is worth stating because it is not a defect: the held item is animated, so two runs of the same
configuration are legitimately different images. Freezing it - an empty hand, or a fixed frame index - would make the
GUI-visible comparison usable; until then it is a measurement of the scene, not of the renderer.

---

## With the animation frozen, the GUI-visible comparison has a floor of 0.07 and the difference is real

The retraction one round ago was right about the measurement it retracted - there was no noise floor beside it - but it
left the question open, so the next thing was to make the floor small. The floor comes from the first-person item
animating between runs, so the fixture now freezes the swing and arm angles whenever the GUI is kept
(`-Dceltium.validation.keepGui=true`); the equipped-progress fields are not public in 1.12.2 and are left alone.

Three runs of the frozen scene:

| Run | Result |
|---|---|
| immediate on (default) | PASSED, `nativeTerrainVerified True`, 1668 passes |
| reference, immediate off, first | PASSED, 1654 passes |
| reference, immediate off, second | PASSED, 1660 passes |

| Comparison | mean | over 8 | over 32 |
|---|---|---|---|
| **reference against reference (noise floor)** | **0.07** | 0.05% | 0.03% |
| immediate on against reference | **1.63** | 1.29% | 0.82% |

The floor collapses from 3.72 to 0.07, and the difference is then **twenty-three times the floor** - so with the GUI visible
the immediate path genuinely differs from GL, which the previous round could neither claim nor deny. The earlier number is
still retracted as quoted (it was a phase measurement, not a renderer one); what changed is that the question now has a
measurement that can be repeated, and the answer is that there *is* a difference to explain.

Where it sits, from the same comparison: the differences are not in the HUD regions - hotbar, crosshair and the top strip
measure at or near zero - and they concentrate in the band the first-person item occupies, which is the next thing to
identify rather than guess at.

---

## The frozen comparison puts the difference in the hand, not the HUD

The same comparison, region by region:

| Region | Differing |
|---|---|
| HUD hotbar (bottom centre) | **0.00%** |
| HUD crosshair (centre) | **0.00%** |
| chat/overlay strip (top) | **0.00%** |
| world, left | 0.95% |
| **world, centre** | **5.42%** |
| **hand and held item (lower right)** | **4.60%** |

with the whole difference inside a bounding box of x 273..1114, y 387..719 - the lower two-thirds, extending from the middle
of the frame to the right where the first-person item is drawn.

So the HUD that the switch was turned on to measure is **drawn correctly**, and the difference belongs to the held item and
the world band it overlaps. Whether that is the item itself or something its drawing changes around it is what a run with an
empty hand answers, and that is the next measurement rather than a guess: the two candidates are separated by one fixture
line, not by reasoning.

---

## Emptying the hand separates the difference: the arm and the item, not the HUD

The recorded next step was one fixture line - put nothing in the main hand - and it answers the question it was written
for:

| Comparison (GUI visible, animation frozen) | mean | over 8 | over 32 |
|---|---|---|---|
| reference against reference, **hand empty** (noise floor) | **0.10** | 0.08% | 0.05% |
| immediate path against a reference, **hand empty** | **0.96** | 0.78% | 0.48% |
| immediate path against a reference, item in hand (previous round) | 1.63 | 1.29% | 0.82% |

So the held item accounts for about **0.67** of the 1.63, and about **0.96 remains** - still ten times the floor - in a frame
where the item renderer does nothing at all. What is left in that band with an empty hand is the **arm**, which Minecraft
draws regardless of what is held.

Both halves are therefore first-person hand rendering, and neither is the HUD:

- the **arm** (about two thirds of the difference),
- the **held item** (about one third),

which also explains why the difference appeared only once the GUI was visible: the hand is drawn as part of the same
frame's later passes, and it is exactly what the hidden-GUI captures excluded.

The next step is to instrument those batches rather than keep bisecting by image: which format id, pipeline state and
texture the hand and item batches carry, and whether they are accepted at all, would say which of the pipelines in the
mask mishandles them.

---

## The hand-batch instrumentation is in the tree but does not reach the run's report

The recorded next step was to instrument the batches rather than keep bisecting by image, and the first thing worth
knowing is which formats the first-person hand's batches carry and whether Vulkan serves them at all. A per-format
offered/accepted histogram was added for exactly that - eight counters each way, reported periodically so the last report
sits near the capture, which is the lesson from the refusal counters two rounds ago.

Two runs later the histogram **never appears in the run's debug output**:

| Attempt | Result |
|---|---|
| reported once at 1000 offered batches | not printed |
| reported every 500 offered batches | `histogram lines: 0` |

The runs themselves are green (PASSED, 1670 passes, 0 messages), so nothing is broken - what is broken is the reporting
path, and this is the second time in this session that a counter wired to the debug list failed to surface while a print
from a mixin surfaced immediately. The next attempt therefore uses the channel that demonstrably works - a
`System.out.println` from the backend, as the mixins already do - rather than a third variation on the same list.

That distinction matters for the answer this instrumentation exists to give: whether the hand's batches are Vulkan-drawn
at all is currently **unknown**, and "the histogram did not print" must not be read as "there were none". The counters stay
in the tree, harmless and unused until the reporting works.

---

## The source answers the first question, and the obvious second hypothesis is refuted

Three attempts to report a per-format histogram through the run's own output failed to surface, so the question was put
to the source instead - the instrument that has worked all session. `ItemRenderer` draws the first-person item, the arm and
the overlays with **`DefaultVertexFormats.POSITION_TEX`** (twenty bytes: position and uv). That format is **not in the
acceptance mask** - the five it holds are the sixteen-byte colour format, the twenty-four-byte textured-colour format and
three twenty-eight-byte families for blocks, items and particles - so the hand and the arm are **drawn by GL**, and their
drawing cannot be what differs.

That left the interaction: with the GUI visible the frame offers many more batches, so the sixteen-window budget might be
consumed by HUD work and push world geometry to GL, mixing the two in one frame. The test is a property change, and it
refutes the hypothesis:

| Configuration (empty hand, GUI visible) | mean | over 8 |
|---|---|---|
| window budget 16 | 0.96 | 0.78% |
| **window budget 64** | **1.53** | 1.22% |
| noise floor | 0.10 | 0.08% |

Raising the budget makes the difference **worse**, not better: more batches drawn by Vulkan means more difference, so the
offending drawing is inside the Vulkan path rather than in a mixture of paths. The refusal counters agree - refusals fall as
the budget rises - so this is not the budget being exhausted either.

What that leaves is the same class of question the terrain work answered early on, and the instrument for it already
exists: accept **one format at a time** across the GUI-visible scene, and the mask that creates the difference names the
pipeline. That is four runs and a comparison each, all of it machinery this project has used before.

---

## The format bisection refutes the instrument, not a pipeline: the GUI-visible scene cannot support these claims

The bisection was set up exactly as the terrain work did it - accept one format at a time and see which mask creates the
difference:

```
noise floor:    mean 0.10 | >8 0.08%
format 1 only:  mean 3.71 | >8 2.93%
format 3 only:  mean 3.68 | >8 2.91%
format 4 only:  mean 3.66 | >8 2.90%
format 5 only:  mean 3.67 | >8 2.91%
```

Every configuration differs by about **3.7** - including the one where the immediate path draws almost nothing, format 1
alone - and 3.7 is precisely the **unfrozen** floor measured three rounds ago (3.72). The frozen runs that reported a 0.10
floor and the 0.96/1.63 differences were therefore not measuring what they appeared to: the freeze removes the swing and
the arm angles, but the first-person view still varies with the frame's partial tick, and how much it varies depends on the
run rather than on the configuration.

So the honest position, and it is a third correction in this one area:

- **the GUI-visible comparison cannot support renderer claims in this harness.** Its floor is not stable - 0.07, 0.10 and
  3.7 across runs of the same scene - so a difference of 0.96 or 1.63 against it means nothing, and the reports that used
  those numbers are **withdrawn**, including the arm-versus-item split;
- what remains sound is the **hidden-GUI comparison**, whose floor is a genuine 0.00 because two runs of a static scene
  produce identical images, and the format bisection performed there: formats 1, 3 and 5 each measured 0.00 on their own,
  and format 2 turned out to be the final composite quad rather than content;
- the hand's own drawing is not in question anyway, and that part came from the source rather than from images:
  `ItemRenderer` uses `POSITION_TEX`, twenty bytes, which the mask does not accept, so GL draws the hand and the arm.

The lesson is the one this log has now paid for three times: an instrument whose noise floor is not established **per
configuration** will produce differences that look like findings. The hidden-GUI scene has that floor; the GUI-visible one
does not, and until the hand can be frozen properly - an empty hand was not enough - it should not be used to claim
anything about the renderer.

---

## The instrument was never broken: the comparison used a reference from a different fixture revision

The format bisection that produced four identical 3.7 differences is explained, and the explanation is not the
instrument. Those runs were compared against a reference captured **before** the fixture gained the animation freeze and
the tick pin, and the fixture is part of the instrument: changing what it renders invalidates every reference captured
with the old revision. Comparing new runs against an old reference produces a difference that belongs to the fixture
change, not to the renderer - which is exactly the 3.7 that matched the old unfrozen floor.

Re-measured with references regenerated from the same revision - GUI visible, empty hand, frozen animation, capture
pinned to one tick:

| Comparison | mean | over 8 | over 32 |
|---|---|---|---|
| **reference against reference (noise floor)** | **0.11** | 0.08% | 0.05% |
| immediate path against reference | **0.97** | 0.78% | 0.49% |

The pin itself changed nothing measurable (the floor was 0.10 before it, 0.11 after), so the animated-texture theory for
the variation is not the dominant term and is not claimed. What the measurement does say is consistent and repeatable: in
the GUI-visible frame the immediate path differs from GL by **about nine times the floor**, that this grows when the window
budget lets Vulkan draw more (1.53 at a budget of 64), and that the hand's own geometry cannot be the cause because
`ItemRenderer`'s `POSITION_TEX` is outside the acceptance mask and GL draws it.

So the position after this round, with two corrections layered on each other and both recorded:

- **withdrawn:** "the GUI-visible scene cannot support renderer claims". It can; the floor is 0.11 and the difference is
  nine times it;
- **added, as a rule:** the fixture is part of the instrument, so any change to it - a freeze, a pin, a switch - retires
  every reference captured before it. Three comparisons in this area were made against stale references, and all three
  produced numbers that looked like findings;
- **open:** what in the Vulkan path differs in that frame, now measurable with an instrument that has a floor.

---

## The control settles it: the GUI-visible difference is an uncontrolled term, and the hand is in every run

The one control that decides the question is to let the immediate path draw **as little as possible** and see whether the
difference goes away. With the mask set so that no content format is accepted:

```
noise floor (reference against reference):  0.11
empty mask (Vulkan draws almost nothing):   3.75 | 2.94% over 8
counter line: immediateOffered=9068 immediateTaken=29 immediateRefusals=format...
```

Twenty-nine batches were still taken, and the difference is **3.75** - larger than the 0.97 measured when Vulkan drew
essentially the whole frame. That is the third non-monotonic result in a row, and together they identify the term:

- drawing **more** by Vulkan: 0.97
- drawing **almost nothing**: 3.75
- drawing **one format only**: 3.6 for every format, independently of which

A difference that does not depend on what the renderer draws is not a renderer difference. What all these runs share is the
**first-person hand**, drawn by GL in every one of them, whose position depends on the frame's partial tick - and the
freeze does not remove that, because the freeze pins the swing and the arm angles while the interpolation between ticks
still moves with whichever frame happens to be captured. The reference runs differ by 0.11 only because two runs of the
same configuration can land on a similar partial tick; when they do not, the term is about 3.6.

So the position, after two reversals in this area, is the one the control supports:

- **the GUI-visible comparison cannot support renderer claims in this harness**, and the round before last was right about
  that while the last round's retraction of it was wrong. Its floor is small only by luck;
- **the term that dominates it is the first-person hand**, not any pipeline;
- **the instrument that is sound** is the hidden-GUI comparison, whose two runs produce identical images (0.00), plus the
  format bisection performed there;
- **and there is a way to make the GUI-visible comparison sound**: render in third person, which removes the first-person
  hand entirely. That is one setting in the fixture and it is the next measurement rather than another argument about the
  floor.

---

## Third person removes the hand and the difference stays: 0.95 against a floor of 0.09

The recorded measurement was to render in third person, which removes the first-person hand entirely - the term the control
identified as dominating the GUI-visible comparisons. Three runs of the frozen, tick-pinned, GUI-visible scene in third
person:

| Comparison | mean | over 8 | over 32 |
|---|---|---|---|
| **reference against reference (noise floor)** | **0.09** | 0.07% | 0.04% |
| immediate path against reference | **0.95** | 0.74% | 0.47% |

So the difference is **ten times the floor** with the hand gone, and it is the same 0.95-0.97 that every GUI-visible
configuration has measured. Two things follow:

- the **hand is not the term**. The control that pointed at it was reading three runs whose difference came from somewhere
  else, and the third-person measurement excludes it directly;
- the difference is **real, reproducible and about 1.0 mean (0.7% of pixels over 8)** in the GUI-visible frame, which the
  hidden-GUI comparisons - identical to 0.00 - do not show at all.

One contradiction has to be resolved before this is attributed to anything. The empty-mask control reported
`immediateTaken=29` while accepting no content format, and differed by 3.75. Either the counter does not mean what the
analysis assumed - it may count drawn batches rather than accepted ones - or the mask does not filter the way the parser is
believed to. Until that is read from the code rather than inferred, the 3.6-3.75 numbers stay unexplained and no pipeline
can be named from them.

That is the next step: read what `immediateTaken` counts and what the mask parser does with a digit that matches no
format, then re-run the bisection with a control whose meaning is known.

---

## The contradiction was a script edit that silently did nothing, and everything fits once it is read

Reading the code answered the two questions, and then reading the *script* answered the contradiction:

- `celtium$formatAccepted` scans the mask string for a digit equal to the format id, so `immediateFormats=9` accepts
  nothing - the parser was never the problem;
- `immediateTaken++` sits after every refusal check, so it counts accepted batches - which meant the reported
  `immediateTaken=29` could not belong to a run with an empty mask.

It did not. `/tmp/run-nomask.sh` contains **no `immediateFormats=9`**: `grep -c` returns 0, and the run's own counters say
`immediateRefusal=format_0_not_in_celtium.vulkan.immediateFormats` - the ordinary default behaviour of accepting 1, 3, 4
and 5 and refusing the rest. The `replace` that was supposed to add the flag matched nothing, because the script's
`--jvm-arg=-Dceltium.vulkan.immediate=true` line is not spelled the way the pattern assumed, and that replacement was one
of the few in this session made **without an assertion**. So the "control" was a normal run, its 3.75 was the
first-person scene's variation, and no contradiction about counters ever existed.

With that read, the picture is consistent and the finding survives:

| Scene | Noise floor | Immediate path against reference |
|---|---|---|
| hidden GUI | 0.00 | 0.00 |
| **third person, GUI visible** | **0.09** | **0.95** |
| first person, GUI visible | 0.09 to 3.7 depending on the run | not measurable |

So in the GUI-visible frame, with the hand excluded and a floor of 0.09, the immediate path differs from GL by about
**0.95 mean, 0.7% of pixels over 8** - a real, reproducible difference that the hidden-GUI comparisons do not show, and one
whose content is not yet identified. The bisection for it has to be run with the mask flag **verified present** in the
script before the run, which is the lesson this round cost: an unasserted replacement is indistinguishable from a
successful one until its effect is looked for in the run's own output.

---

## The verified empty-mask control ends the question: the GUI-visible difference is not the renderer

With the mask flag verified present in the script this time, and the run's own counters confirming it took effect:

```
floor (reference against reference): 0.09
empty mask (immediateTaken=0):       1.60 | 1.20% over 8
```

`immediateTaken=0` is the whole point: no batch was accepted, so **no GL draw was cancelled** - every pixel of that frame was
drawn by GL, exactly as in the reference - and the images still differ by **sixteen times the floor**. Whatever the
GUI-visible comparisons have been measuring, it is not what the Vulkan path draws.

What is left, and it is the same uncontrolled term in a different costume, is the **animated subject**: in third person the
local player's model is rendered and its limbs move with the partial tick, just as the first-person hand did. The 0.09 floor
is two references happening to land on similar partial ticks, not a stable floor, which is why it has read 0.07, 0.09, 0.10
and 0.11 across rounds while runs of the same configuration differ by up to 3.8.

So the position is settled, and the two reversals before it can be closed:

- **the GUI-visible scene cannot support renderer claims in this harness.** Two controls - an empty mask and a third-person
  camera - both show differences that do not depend on what the renderer draws;
- **withdrawn for good:** every number in this area, including the 0.95 "ten times the floor" reading of the last round;
- **the sound instruments** are the hidden-GUI comparison, whose two runs produce identical images (0.00), and the format
  bisection performed there - formats 1, 3 and 5 measured 0.00 on their own, and format 2 turned out to be the final
  composite quad rather than content.

If the GUI-visible scene is wanted later, the animated subject has to be removed rather than frozen - the player model in
third person, the hand in first - and that is a fixture change, not another measurement of the same scene.

---

## Water in the scene, measured with the sound instrument and its own floor

The validation world was a flat stack of bedrock, dirt, grass, leaves and stained glass - **no water at all** - so every
pixel-identical result in this log said nothing about translucency, which is one of the content classes the objective names.
A water layer was added to **both** world creations (the initial one and the one the world-switch test joins, which a first
attempt missed because the same line appears twice), and the hidden-GUI comparison was re-run - the instrument whose two
references produce identical images - **with its own floor established first**, which is the rule this session paid for:

| Comparison | mean | over 8 | over 32 |
|---|---|---|---|
| **reference against reference (noise floor)** | **0.01** | **0.00%** | 0.00% |
| immediate path against a reference | **0.04** | **0.27%** | 0.03% |

The floor is clean - 0.00% of pixels over 8 - and the difference is not: **0.27% of the frame differs by more than 8**
where the terrain-only scene measured exactly 0.00. Both runs are PASSED with `nativeTerrainVerified True`.

So this is the first sound content difference found in many rounds, and it is in water: translucent terrain, drawn through
one of the pipelines the immediate path already serves. It is small in area and small in magnitude, which is exactly what a
translucency ordering or blending difference looks like, and the next step is the one that has worked before - localise it
by region and then bisect by format, with the mask flag **verified present in the script** before each run, since an
unasserted replacement is what made an earlier control meaningless.

---

## The audit's P1 was real, and fixing it removed the water difference entirely

The audit reported that `currentGroupState` was never assigned: the group-open block set the projection, modelview,
format, texture and state key, but not the state array itself, so `closeCurrentImmediateGroup` stored the field's initial
`new int[7]` and every group was drawn with blend, depth test, depth write, culling and the alpha reference all off. The
state-keyed pipeline cache had the right keys and empty values. Reading the code confirms it exactly - lines 418, 925-935,
1337 and the draw path - and the fix is the assignment the audit named, cloned so the snapshot cannot be overwritten:

```java
this.currentGroupState = state.clone();
```

Measured afterwards, on the water scene whose difference this log recorded one round earlier:

| Comparison | mean | over 8 | over 32 |
|---|---|---|---|
| **reference against reference (same revision)** | **0.00** | **0.00%** | 0.00% |
| immediate path against reference, **after the fix** | **0.00** | **0.00%** | 0.00% |
| the same comparison **before** the fix (recorded last round) | 0.04 | 0.27% | 0.03% |

The water difference is **gone**, and with it the translucency question it raised: the 0.27% of pixels that differed were
groups drawn without blending or depth state, which is exactly what a translucency difference looks like from the outside.

Two notes on method, both this project's own rules applied late:

- the terrain-only default run was first compared against `p8a-off-1-amd`, a reference captured **before the water layer
  existed**, and reported a 30.76 difference. That number is meaningless - the fixture is part of the instrument - and
  against a same-revision reference the same run measures the 0.00 above;
- the audit's warning is taken as written: the earlier GUI, translucency and water comparisons were made with this defect
  present and are treated as experiments containing it, not as evidence about anything else.

---

## Weather and particles: a difference fourteen times its own floor, measured after the state fix

With the group-state defect fixed, the scene was extended to the two content classes that were still absent from it: rain
and particles. Both are built to be comparable rather than merely present - the rain is set going fifty ticks before the
capture so its strength has saturated, and the twenty-four particles are spawned one tick before it with **zero motion**, so
`prev == current` and the partial-tick interpolation that made the hand and the player model unusable gives the same answer
whichever frame is captured.

| Comparison (hidden GUI, same revision) | mean | over 8 | over 32 |
|---|---|---|---|
| **reference against reference (noise floor)** | **0.06** | **0.23%** | 0.11% |
| immediate path against a reference | **0.85** | **1.33%** | 1.29% |

Two things to read carefully. The floor is **not** zero here as it was for the water scene - 0.23% of pixels differ
between two identical reference runs - so something in this scene still varies, and the rain is the obvious candidate
since Minecraft spawns its own rain effects and advances a world random each tick. And the difference is **fourteen times
that floor**, with nearly all of it (1.29% of 1.33%) being pixels that differ by **more than 32**, which is a substantial
difference rather than an edge effect.

The next step is to separate the two candidates rather than attribute the number to both: a particles-only configuration
(rain off) with its own floor, then a rain-only configuration, each with the same three runs. That is the pattern this
project has used all session, and it is the only way to say which of the two classes the difference belongs to.

---

## The difference is the particles: rain off changes nothing measurable

The recorded next step was to separate the two candidates, and one configuration settles it:

| Scene (hidden GUI, same revision) | Noise floor | Immediate path against reference |
|---|---|---|
| weather + particles | 0.06 (0.23% over 8) | **0.85** (1.33% over 8) |
| **particles only (rain off)** | 0.07 (0.28% over 8) | **0.83** (1.30% over 8) |

Removing the rain moves the difference by 0.02 and the floor by 0.01, both inside the scene's own variation, so **the
particles carry the whole difference** and nothing about rain rendering is claimed in either direction.

Two things about the instrument are worth stating because they limit the claim. Its floor is **not zero** - 0.28% of pixels
differ between two identical reference runs - because particles advance in time even when they are spawned motionless: their
age and texture frame move with the client's tick, which is not pinned by the fixture's world tick. And the difference is
about **4.6 times** that floor rather than the clean separation the water scene gave, where the floor was a true 0.00. So
the finding is real and it is in the particles, and the margin is smaller than this project's own rule would like.

The next step follows the pattern that has worked: bisect the particle scene by format, with the mask flag **verified present
in the script** before each run. Format 5 is the particle family in the acceptance mask, so a mask of 5 alone against a mask
of 5 excluded is the comparison that names the pipeline - and the same-revision reference has to be captured for each
configuration, since the fixture's scene is part of the instrument.

---

## The second audit's A: the injector was never cancellable, and the fix exposes an unexplained zero

The finding is confirmed from the code and it invalidates the project's own account of what its comparisons showed:
`WorldVertexBufferUploaderMixin`'s injector was declared without `cancellable = true`, and its `info.cancel()` sat inside a
`catch (Throwable ignored)`. A cancel on a non-cancellable callback throws; the throw was swallowed; **the original GL draw
ran anyway**. Vulkan and GL therefore drew the same geometry, the two images agreed to 0.00, and no measurement in this log
distinguished "Vulkan replaced GL" from "Vulkan did nothing and GL drew everything".

The fix is in: `cancellable = true`, the acceptance recorded in a flag, the broad catch returning without swallowing the
cancellation, and - since cancelling skips the rest of the vanilla method - a `buffer.reset()` before `info.cancel()`,
matching what the original does after its upload.

The first run after the fix reports something the account has to stop and look at:

```
fixture PASSED | nativeTerrainVerified True | 1644 passes
water scene against the same-revision reference: mean 0.00
immediateTaken=0 | immediateFlushes=0 | immediateRefusal=none
```

**`immediateTaken=0`.** Nothing was accepted, so GL drew the frame - which explains the 0.00 without saying anything about
the Vulkan path. Before the fix the same scene reported thousands taken, which is consistent with the batches being
accepted and then drawn twice rather than once. Why acceptance is now zero is not established, and the possibilities are
narrow and testable: an early return that is now reached on every batch, an exception swallowed elsewhere in the offer, or
a counter read at a moment before acceptance.

Until that is read, **no comparison in this log should be treated as evidence about the Vulkan path** - which is what the
audit said, and it is now the project's own position too: the earlier results are experiments in which GL was drawing the
same content, and the independent count the audit asks for (zero GL calls after an accepted batch, one after a refused one)
is what turns the next measurement into evidence.

The audit's B and C are recorded in the audit file with the code facts they rest on: the present path ignores the bridge's
semaphores (off by default, which is why it was not visible), and the comparison tool keys on luminance rather than the
maximum channel, so it cannot see a single-channel difference at all.

---

## The audit's independent count says replacement is not happening

After the `cancellable = true` fix, the counters in the debug line read zero while the scene stayed at 0.00, so the audit's
own instrument was built rather than argued about: a second injector on the same method at `RETURN`, counting how often the
vanilla upload body runs to completion, printed beside the number of times the head injector was entered.

```
celtium uploads: offers=10000 glUploads=10028
```

Read plainly: the injector runs ten thousand times, and **GL's upload body completes about as often**. Cancelling a batch is
supposed to skip that body; it is not being skipped. So the account this log has carried - that accepted batches are drawn
by Vulkan instead of GL - is **wrong**, and the agreement at 0.00 between the "immediate path on" and "immediate path off"
runs is explained by GL drawing the same content in both.

The same run's debug line reads `immediateOffered=0 immediateTaken=0`, which cannot both be true and ten thousand calls
reaching the backend unless the line reports fields other than the ones the path increments. That is a reporting defect of
the kind this session has hit at least four times, and it is the second half of what has to be fixed before any counter in
this project is quoted again.

What this does to the earlier record is what the audit said it would do, and the history now states it in its own voice:
**every comparison in this log was an experiment in which GL drew the content**, so none of them is evidence that the
Vulkan path renders anything correctly. The two things that remain sound are the audit's findings about the code itself -
the missing `cancellable`, the unused bridge semaphores in the present path, the comparator's luminance mask - and the
measurements taken from primary sources rather than from images, such as `ItemRenderer` using `POSITION_TEX` outside the
acceptance mask.

The next step is the one the audit asked for and this round could not finish: print what the injector decided, from the
channel that reaches the log, and read the counters from the fields the code increments. Until replacement demonstrably
happens - GL uploads falling to zero for accepted batches - no image comparison in this project means anything about
Vulkan.

---

## Replacement is real now, and the false account has two causes stacked one on the other

Counting the decision inside the mixin printed `offers=10000 accepted=0 glBodyCompleted=10029`, and the swallowed-failure
report - added for exactly this - never fired. So no exception was hiding the zero: the backend was never asked. The call
site confirmed it: **the `submitImmediateBatch` call was gone from the mixin**, removed when a dead branch was cleaned out
of that method one round earlier. With it went the only path a batch could take to acceptance, which is why the backend's
counters read zero, why GL's upload body completed for every batch, and why every image comparison agreed.

Restoring the call produces the measurement the audit asked for:

```
celtium offer decisions: offers=10000 accepted=562 glBodyCompleted=9465
backend counters: immediateOffered=8935 immediateTaken=577 immediateFlushes=57
fixture PASSED | nativeTerrainVerified True | 2208 passes
```

The backend is called, accepts 562 batches, and the vanilla upload body completes **535 fewer times than it is offered** -
GL is skipped for exactly the batches Vulkan takes. The remaining refusals are the mask and the format rules working, and
for the first time the backend's counters and the mixin's counters agree.

The order of causes is worth keeping, because each alone was sufficient to make the account wrong:

| # | Defect | Effect |
|---|---|---|
| 1 | the injector was not `cancellable` | `info.cancel()` threw, the broad catch swallowed it, GL drew every accepted batch |
| 2 | **the submit call was deleted** while cleaning a dead branch | nothing was ever accepted, so GL drew everything and the counters read zero |
| 3 | counters and images | both said nothing was wrong, which is why neither defect was noticed for many rounds |

What follows is what the audit said follows: **the comparisons start again from the beginning**, now that replacement
happens, and the first of them will be the first measurement this project has taken of the Vulkan path drawing alone.

---

## The first sound measurement: Vulkan draws 585 batches alone and matches GL to nine pixels

Everything before this round measured frames in which GL drew the content. With the cancellation effective and the submit
call restored, the water scene was measured against same-revision references - the first comparison in this project where
the immediate path actually replaces GL:

```
floor (reference against reference):  0.00 mean | 0.00% of pixels over 8
water scene, Vulkan drawing alone:    0.00 mean | 9 pixels of 921,600 over 8
immediateOffered=9060 immediateTaken=585 immediateFlushes=585
fixture PASSED | nativeTerrainVerified True | 2211 passes
```

585 batches drew through Vulkan, GL's upload body ran 535 fewer times than it was offered, and the resulting frame matches
the GL reference to **nine pixels**. That is the first evidence in this log that the immediate path renders anything
correctly, and it is deliberately not rounded to "zero": nine pixels is what the measurement says, and they sit in a small
spot at x 226..227, y 504..506.

The sequence that got here is worth one line, because it is the difference between a claim and a measurement: the audit
found the injector was not cancellable, then reading the mixin showed the submit call had been deleted while cleaning a
dead branch, then the restored call made acceptance real, and only then did a comparison mean anything. Four rounds of
image work before that were measuring GL against GL.

---

## With replacement active, the counter pair points at undrawn geometry

The weather and particle scene, measured with the immediate path genuinely replacing GL:

| Comparison (hidden GUI, same revision) | mean | over 8 | pixels over 8 |
|---|---|---|---|
| reference against reference (floor) | 0.06 | 0.23% | - |
| weather and particles, Vulkan drawing alone | **0.75** | **1.32%** | **12,157** |

and in the same run: `immediateOffered=9061 immediateTaken=586 immediateFlushes=58`.

Those two numbers are the lead. The water scene, which measures clean, reported `taken=585 flushes=585` - every batch it
accepted was flushed. This scene accepted 586 and flushed **58**: about nine tenths of what the backend took was never
submitted, so the frame is missing geometry that GL would have drawn, which is exactly what a 1.32% difference with a
0.23% floor looks like. The earlier reading of this scene - 0.85 against 0.06, taken when GL was drawing everything - was
measuring the scene's own variation; this one has a mechanism attached to it.

The Intel reference for the current fixture was also captured (PASSED, `nativeTerrainVerified True`, 1038 passes), so the
two-GPU comparison can be made against references that match the scene rather than against ones from before the water layer
existed.

The next step is to explain the gap between taken and flushed: whether those groups are discarded, deferred past the
capture, or dropped when a window cannot open. The counters for discards and deferrals already exist, and reading them
against this run is cheaper than another image comparison.

---

## The "undrawn geometry" lead was my own truncation, and it is withdrawn

The lead recorded last round - 586 batches taken and only 58 flushed in the weather and particle scene, against 585 and
585 in the clean water scene - does not survive reading the counter line in full:

```
weather+particles: immediateOffered=9061 immediateTaken=586 immediateFlushes=586
water:             immediateOffered=9060 immediateTaken=585 immediateFlushes=585
```

`immediateFlushes=586`, not 58. The number was cut off by the sixty-character slice the reading script used, and the
truncated value was written down as a finding before the full line was printed. So there is **no** evidence of accepted
geometry going undrawn, and the mechanism that was attached to the weather scene's difference is withdrawn with it.

What remains is the measurement itself, unchanged: the weather and particle scene differs from its same-revision reference
by **1.32% of pixels over 8 (12,157)** against a floor of **0.23%** - about five times the floor, so a difference that is
larger than the scene's own variation but whose cause is not identified. The floor is not zero because particles animate in
time, as recorded when the scene was built.

The lesson is the third of its kind in this session and is worth stating plainly: **a value read through a truncating
slice is not a measurement**. Previous rounds lost a counter to a wrong path, a comparison to a stale reference, and a
control to an unasserted replacement; this one lost a lead to a substring. The rule that covers all four is to print the
whole line, or read the field by name, before anything is concluded from it.

---

## The particle difference is format 5's pipeline: 90% of it measured with everything else on GL

The bisection that has worked all session, applied to the scene where replacement is now real:

| Configuration (weather + particles, hidden GUI, same revision) | mean | over 8 | pixels over 8 |
|---|---|---|---|
| reference against reference (floor) | 0.06 | 0.23% | - |
| **format 5 only** - particles through Vulkan, everything else GL | **0.69** | **1.19%** | **10,973** |
| all formats | 0.75 | 1.32% | 12,157 |

With a mask that lets **only the particle family** through, the difference is 1.19% of the frame - about **ninety per cent of
the 1.32%** measured with everything accepted. Everything else in that frame is drawn by GL and is therefore correct by
construction, so the difference belongs to **format 5 and nothing else**, which is the same clean attribution the composite
quad and the water scene each produced. The run also reports `taken=557 flushes=557`, consistent with the withdrawal of the
undrawn-geometry lead.

The vertex layout was checked against the source before looking further, because a mismatched stride or uv type would
explain a particle-shaped difference exactly. Minecraft 1.12.2 declares `PARTICLE_POSITION_TEX_COLOR_LMAP` as
`POSITION_3F` (12) + `TEX_2F` (8, **float** uv) + `COLOR_4UB` (4) + `TEX_2S` (4, the **lightmap** as two shorts) - **28
bytes**, which is what the particle pipeline assumes. So the data's shape is right and the cause is in how that pipeline
uses it: the lightmap values, the flat integer varying, or the shader's sampling of them.

That narrows the next step to one pipeline and two candidates, and the audit's own list already names the area - lightmap
handling - as one it is examining.

---

## The lightmap bias hypothesis is refuted, and this time the artifact was verified first

The particle pipelines sample the lightmap with `(coordinate + 0.5) / 256`, where Minecraft's own fixed pipeline uses
`(coordinate + 8) / 256` - a half-texel difference that would show up as subtle lighting shifts at light boundaries, which
is what a 1.3% pixel difference in a lit scene looks like. Both the particle and block fragment shaders were changed to the
vanilla bias and the scene re-measured:

| Weather and particles (hidden GUI, same revision) | mean | over 8 | pixels over 8 |
|---|---|---|---|
| before the bias change | 0.75 | 1.32% | 12,157 |
| **after the bias change** | **0.76** | **1.31%** | **12,086** |
| noise floor | 0.06 | 0.23% | - |

Unchanged, so the bias is **not** the cause and the hypothesis is refuted. Unlike several earlier rounds, that conclusion
rests on a verified artifact rather than on an assumption: the SPIR-V inside the built mod jar was compared by hash with
the file on disk, and the file on disk with a fresh compile of the edited source. All three are identical, so the run
genuinely used the changed shader - which is the check this session has needed and skipped before, when an edit that never
landed was read as a result.

What remains for the particle difference is the same narrowed set, now with one candidate removed: the lightmap *texture*
the pipeline binds (whether the mirror holds what GL's lightmap has, and whether it is current), the flat integer varying
carrying the coordinate, and the sampling of the particle atlas. The verification habit is what should carry forward: hash
the shipped artifact, then conclude.

---

## The lightmap mirror is stale - found in the source - and the first hook for it crashed the client

Following the shortened candidate list to the lightmap texture, the source answered before any experiment did:

```
DynamicTexture.func_110564_a()                      // updateDynamicTexture, called by EntityRenderer:1025
  -> TextureUtil.func_110988_a(glTextureId, data, w, h)
```

`EntityRenderer` calls that **once per frame** for the 16x16 lightmap, and **neither of the two upload hooks in
`TextureUtilMixin`** - `func_110989_a` for registration and `func_147955_a` for animation frames - covers
`func_110988_a`. So the mirror of the lightmap is uploaded once and never refreshed, while the real lightmap keeps
changing: lighting that freezes at whatever the first frame had. That is a real defect in the shipped configuration, found
by reading rather than by another comparison, and it is exactly the shape of a difference that appears in lit content and
not in uniformly lit content.

The hook written for it **crashed the client**, reproducibly, in six seconds:

```
java.lang.NoClassDefFoundError: net/minecraft/client/renderer/texture/TextureUtil
```

so it is reverted, the build is green again and the water scene is back to PASSED with `nativeTerrainVerified True` and
2229 passes. The defect it was written to fix is still there, and the crash says the *target* was wrong rather than the
idea: `func_110988_a` on `TextureUtil` destabilised the class load.

The next attempt targets `DynamicTexture.func_110564_a` instead - a concrete method on a small class, no parameters, called
once per frame - with an `@Invoker` for `func_110552_b` to read the texture id the way Mixin's own accessor mechanism
intends, rather than a static method inside `TextureUtil` whose signature Mixin and the remapper evidently disagreed about.

---

## Two hooks for the stale lightmap, two crashes, and the elimination narrows the cause

The second attempt targeted the pair the audit's own advice points at - a concrete method on a small class plus Mixin's
official `@Invoker` for the texture id - and produced the same failure as the first, in the same six seconds:

| Attempt | Target | Crash |
|---|---|---|
| 1 | `TextureUtil.func_110988_a(glTextureId, int[][], int, int)` | `NoClassDefFoundError: net/minecraft/client/renderer/texture/TextureUtil` |
| 2 | `DynamicTexture.func_110564_a` with `@Invoker("func_110552_b")` | `NoClassDefFoundError: net/minecraft/client/renderer/texture/DynamicTexture` |

Both are transformation failures surfacing as a class that cannot be loaded, and together they exclude the obvious
explanations. `TextureUtil` **is** targetable - the registration hook on it has fired in every run since it was added - so
attempt 1's failure was not the class but the **injected method's signature**, an `int[][]` parameter. `DynamicTexture` was
a first-time target and the only machinery attempt 2 added was the `@Invoker`, which makes **the accessor machinery** the
prime suspect there.

Both are reverted; the build is green and the scene measures PASSED with `nativeTerrainVerified True` and 2220 passes. The
defect they were written to fix - a lightmap mirror uploaded once and never refreshed, while Minecraft updates it every
frame - is still in the tree and still unaddressed.

The next attempts, in the order the evidence suggests: a minimal mixin whose only content is the `@Invoker`, to confirm or
clear the accessor machinery in one run; a hook on `DynamicTexture.func_110564_a` that reads no id at all and instead asks
the backend to drop whichever texture the game has bound, which is what the animation hook already does; and, if both fail,
invalidating every mirror on a frame counter, which is blunt but cannot be wrong about which texture changed.
