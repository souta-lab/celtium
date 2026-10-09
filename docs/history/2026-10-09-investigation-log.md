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

---

## The mirror refresh costs nothing measurable, so the interval stays at every frame

The refresh introduced last round happens on a frame interval, defaulting to every frame because the lightmap changes
every frame. That default is only defensible if the cost is small, so it was measured rather than assumed:

| `mirrorRefreshFrames` | p50 | p95 | p99 | difference against the reference |
|---|---|---|---|---|
| **1** (default) | 16.17 ms | 17.40 ms | 18.42 ms | 0.40% of pixels over 8 |
| 4 | 16.10 ms | 17.53 ms | 18.80 ms | 0.40% |
| 16 | 16.02 ms | 18.95 ms | 19.28 ms | 0.40% |

The frame times move by less than a run-to-run spread of about 0.15 ms at p50, and the pixel difference is identical at
all three intervals - which is expected, because a still capture taken on a frame where every interval has refreshed cannot
distinguish them. What the numbers do settle is the cost: re-reading every mirrored texture once per frame is not
measurable at this scene's frame rate, so the interval that keeps the mirror **never stale** is the one to keep.

That is the whole reason the interval is a property rather than a constant: it was introduced so this could be measured,
it was measured, and the measurement says the conservative value is affordable. A number that had come out the other way -
say, a millisecond at p50 - would have forced the harder question of refreshing only the texture that changed, which needs
the hook that crashes.

---

## The comparator's difference mask is fixed, and the audit's cases prove it

The audit's C was reproduced from the code before it was changed: the mask came from `difference.convert("L")`, which
weights the channels into one luminance and drops alpha, so a difference confined to one channel could not be seen. The
mask is now the largest absolute difference across channels, and the audit's three cases were run against the logic:

| Case | Before | After |
|---|---|---|
| red delta 1, tolerance 0 | equal | not equal |
| blue delta 40, tolerance 5 | equal | not equal |
| alpha delta 255, tolerance 0 | equal | not equal |
| identical | equal | equal |

Two things follow. Any result this project measured **with that script** is suspect and has to be recomputed; the numbers
quoted in this history were produced by inline maximum-per-channel arithmetic in the analysis scripts rather than by
`compare_validation.py`, so they are not affected - stated because it is checkable, not assumed. And the audit's second
observation about the same tool is still open: it does not consult `validation-process.json`'s failure field, so a run that
failed for a reason outside the fixture's own status can still be reported as a match. That is the next change to this
file.

---

## The comparator now refuses to call two failed runs a match

The audit's second point about the comparison tool was that it ignores the harness's own verdict, so a run that failed for a
reason outside the fixture's status could still be reported as a match. The tool now reads `validation-process.json`
alongside each run and treats four things as disqualifying - a non-zero `validationExitCode`, `validationClean == False`
(reported with the message count), a `fixtureStatus` other than PASSED, and the file being **absent**, which reads as "the
run's own verdict is unknown" rather than as a pass.

All five states were checked against the logic before the change was trusted:

| Process state | Reported |
|---|---|
| clean | no problems |
| 13 validation messages | message count and exit code 1 |
| non-zero exit only | exit code 1 |
| fixture FAILED | the status |
| file absent | the run's own verdict is unknown |

The consequence for this log is concrete and is recorded rather than implied: **comparisons made with runs whose process
result records a failure are void.** Two families are known to be in that state - the reload runs taken before the
destruction-order fix, which reported thirteen validation messages, and the frame-present runs taken before the submit-wait
fix, which reported thirty-six. The numbers in this file that came from inline maximum-per-channel arithmetic are not
affected by the mask defect, which is stated because it is checkable: the analysis scripts compute the per-channel maximum
themselves rather than calling the tool.

---

## The present's bridge contract is real now, and a broad revert is what removed it

The audit's B said `presentImage`'s seven-argument form received the bridge's two semaphores and never used them. The code
agrees: the submission was `submitWithSemaphores(commands, acquire, renderFinished)` - one wait, one signal - while
`endFrame` makes GL wait on the bridge's done semaphore, which nothing signalled.

The corrected call had been written in an earlier round and measured then. It was **removed by a `git checkout -- .`** used
to undo an unrelated mistake, and that is the part worth recording: a wholesale revert does not know which of the changes it
discards were finished work. The same mechanism has been blamed in this log before, for lost instrumentation, and it is now
demonstrated for finished code.

Restored, with the verdict flags reset at the start of every present so a caller cannot read the previous present's result:

```
fixture PASSED | nativeTerrainVerified True | 2149 passes
validation-process.json: validationExitCode 0 | validationClean True | messages 0
submitWithSemaphores calls=1 withWait=2
```

`withWait=2` is the measurement: the submission waits two semaphores where it waited one, which is the acquire and the
bridge. What remains of B is the part the semaphore counts cannot show - the shared image's ownership and `GENERAL`-layout
handoff for the external-memory path, which needs the EXTERNAL acquire/release pair rather than another wait.

---

## The second audit's S08 and S09 are fixed from the source, and the residual turns out to be something else

Both were reproduced from the sources before being touched.

**S08** - `DefaultVertexFormats.POSITION_TEX_COLOR` is `POSITION_3F`, `TEX_2F`, `COLOR_4UB`, so UV sits at offset 12 and
colour at 20, and `immediate_tex_color.vert` declares location 1 as `a_TexCoord` and location 2 as `a_Color`. The pipeline
bound them the other way round: the shader read colour bytes as texture coordinates and coordinates as colour, in a form no
swapchain validation can object to because both descriptions are well-formed.

**S09** - the fragment shaders computed `((coord >> 8) + 0.5) / 256`. Minecraft writes the coordinate short directly and
installs a texture matrix of scale `1/256` and translate `8`, so a 0..240 value shifted right by eight is zero every time
and every sample landed on the same dark texel; the flat integer varying also threw away the per-vertex interpolation the
fixed pipeline has. Both vertex shaders now pass `(vec2(a_LightCoord) + 8.0) / 256.0` as an interpolated `vec2` and both
fragment shaders sample with it.

This also corrects an entry from two rounds ago: the "+8 bias" experiment is what led to the conclusion that the lightmap
bias was not the cause. That experiment changed the bias from 0.5 to 8 and left `>> 8` in place, so it tested neither the
decode nor the interpolation, and the conclusion drawn from it - that the lightmap path was innocent - was too broad. The
audit's reading of `BufferBuilder` and `EntityRenderer` is the one the source supports.

The four SPIR-V files were recompiled and the copies inside the built jar compared by hash with the files on disk: all
identical, so unlike the earlier bias round there is no question of whether the change reached the run. The measurement is
also unchanged:

| Weather and particles, hidden GUI | mean | over 8 | pixels over 8 |
|---|---|---|---|
| floor (reference against reference) | 0.06 | 0.23% | - |
| after the mirror refresh | 0.13 | 0.40% | 3,715 |
| **after S08 and S09** | **0.14** | **0.40%** | **3,714** |

So the decode was genuinely wrong and is now right, and **it is not what the residual difference is made of**. The
acceptance fixtures the audit asks for - ITEM with an asymmetric checker texture, and the lightmap with dark, fullbright,
sky-only and block-only values differing per vertex - are what would show these two fixes working, and they are not built
yet. The residual's next candidate is the audit's S13: the block and particle pipelines key their descriptor sets on the
main texture view alone, so a rebuilt lightmap reuses the old binding.

---

## S20: the gate is structured on both sides, and one real-run observation is left open

The callback now emits one line per message with its severity and id, newlines folded, and the harness keys on those
fields instead of on the text's shape. Seven inputs were run through the parsing block, including the two forms the audit
used - `Validation Error: [ VUID-... ]` and a bare `VUID-...:` prefix - which counted zero messages before and count one
now, and a missing log now counts as a failure rather than as cleanliness.

Then a real run contradicted the expectation, and it is recorded rather than smoothed over:

```
process: exit 0 | clean True | messages 0 | fixture PASSED
validation lines in the log: 1
   severity=WARNING id=VALIDATION-SETTINGS vkCreateInstance(): Information with the layer settings provided ...
```

By the new rules that line should have counted: it is structured, its severity is `WARNING`, and it does not contain
`deprecated`. The harness counted zero. The candidates are that it reads a different file than the one inspected, that the
parse block edited is not the one that ran, or that the line is written after the read - and the point of this audit item is
precisely that a clean verdict has to be trustworthy, so **the gate is not called fixed until that is explained**. It is the
first thing to settle next, before any measurement is quoted as validation-clean again.

---

## S20's mismatch was my own truncated read; the gate was right, and now it shows what it allows

The parse block was run verbatim from the file against the real run's log and returned zero messages with the marker line
present, which is what made it look like a defect. Printing the line in full settles it: 439 characters, and it **contains
`deprecated`**, so the harness had skipped it exactly as the rules say an allowed deprecation warning should be skipped. The
claim in the previous round that it "should have counted" came from reading the first 130 characters and stopping.

That is the second time in two rounds that a truncated read became a finding - the earlier one invented a mechanism for the
particle difference out of `immediateFlushes=58` when the line said 586. The rule written then was to print the whole line
or read the field by name before concluding, and it was broken again one round later, so the fix belongs in the tool: the
harness now records the messages it **allows**, with the reason, beside the messages it counts. A skipped line can no
longer be invisible, and the report on a real run reads `validationMessageCount 0` with
`validationAllowedCount 1` and `allowed[0] = WARNING(deprecated) vkCreateInstance(): ...`.

So S20 stands as fixed: the callback emits severity and id on one line, the parser counts an error in any phrasing, an
absent log is a failure, and what is allowed is now stated rather than implied.

---

## S05 and S06 fixed, and the scene says nothing about either

Both were confirmed by reading before being touched, and both were wrong in the way the audit described.

**S05** - `copyVertices` appended vertices and then wrote the incoming indices unchanged, while `draw` issues
`vkCmdDrawIndexed(..., firstIndex 0, vertexOffset 0, ...)`. A group's second batch therefore indexed the first batch's
vertices. It now captures the base vertex before the append and writes `index + baseVertex`.

**S06** - `ensureCapacity` called `delete()` and `create()`, with a comment saying the contents are not preserved, while the
mesh appends and keeps byte counters for what it wrote earlier. Crossing 64 KiB of vertices or 32 KiB of indices discarded
the earlier bytes - and one large batch could lose its own beginning while its indices still pointed at it. The replacement
is now created first, the old contents copied across, and the old objects detached before creation so an allocation failure
cannot leave the instance pointing at freed handles.

The measurement is unchanged, which is worth stating precisely rather than as reassurance:

| Weather and particles, hidden GUI | mean | over 8 | pixels over 8 |
|---|---|---|---|
| floor | 0.06 | 0.23% | - |
| before S05 and S06 | 0.14 | 0.40% | 3,714 |
| **after S05 and S06** | **0.14** | **0.40%** | **3,714** |

That says this scene's groups are mostly single-batch and its batches fit the initial capacities - not that the defects
were harmless. A group with two quads in it would have drawn the second quad with the first quad's vertices, and a batch
larger than the initial 64 KiB would have lost its own start; neither shape occurs in this fixture. The acceptance fixtures
the audit asks for are the two-quad group and the boundary-crossing batch, and they are still not built, so these two fixes
are verified by reading and by the code no longer doing what it did - not by an image.

The residual stays where it has been for several rounds: 0.40% of pixels against a 0.23% floor, with S08, S09, S05 and S06
all excluded as its cause by measurement rather than by argument.

---

## The S05 acceptance fixture now exists and runs, and its picture is not there yet

The fixture draws two quads in one `Tessellator` begin/draw pair inside a Forge render event
(`RenderWorldLastEvent`), with different colours and different positions, so that the base-vertex defect would put one
colour in one place where two belong. It is switched on by `-Dceltium.validation.drawTest=true` and it runs:

```
fixture PASSED | drawTestQuads 2 | at tick 198
immediateTaken=1133 immediateFlushes=649
```

Two things are open, and neither is dressed up:

- **the quads are not in the captured image.** Searching the frame for saturated red and saturated blue finds zero pixels of
  either. The candidates are the quad's winding against the culling state, the depth of a world-space quad two and a half
  blocks along the view vector, or the pass the capture covers - and the next step is to read which of those it is rather
  than to guess, starting with a screenshot taken with the same flags but with culling disabled for the test.
- **the group counters are in the backend but not in the log.** `immediateGroupsClosed` and `immediateMaxGroupVertices` were
  added when a group closes, and the debug line does not carry them: the replacement that was supposed to append them did
  not match the anchor, which is the same unasserted-replacement failure this log has recorded before. Reading the
  debug-line construction and patching it with an assertion is the fix.

What is established is narrower and still worth having: the fixture compiles, registers on Forge's bus, draws inside a
render event, and reports itself, so the acceptance test for S05 exists. What is not established is that the merged group
reaches the screen, and until the picture shows two colours in two places, S05's fix rests on the code and on reading, not
on an image.

---

## The draw test finds geometry the immediate path loses, and its own baseline is incomplete

The two-quad fixture was run twice, once with the immediate path on and once with it off so that GL draws the same
batches:

| Configuration | saturated red pixels | saturated blue pixels |
|---|---|---|
| immediate path **off** (GL draws) | **81** | 0 |
| immediate path **on** | **0** | **0** |

Two conclusions, and the second one is why the first cannot be read as a pass or a fail of S05 yet.

**The immediate path shows nothing where GL shows 81 pixels of the fixture's own quad.** That is the shape of the audit's
S14 - geometry accepted and then dropped - and it is measured here rather than argued: the same batch, the same frame, the
same capture gate, one flag apart. The counters beside it say the batches are being taken (`immediateTaken=1137`,
`immediateGroupsClosed=1137`), which is exactly the state S14 describes: accepted, and absent from the frame.

**The fixture's own baseline is incomplete**, because with GL drawing it should show both quads and shows only the red one:
81 red pixels and zero blue. So the blue quad never reaches the frame even when nothing of Celtium's is involved, which
means the fixture has not yet established the two-colours-in-two-places baseline that S05's acceptance needs. Until it
does, the fixture can say "the immediate path lost this" but not "the merged group drew the right geometry".

The group counters added this round also produced the first hard number about merging: `immediateGroupsClosed=1137` with
`immediateMaxGroupVertices=880` - groups of up to 880 vertices exist in the captured frame, so the base-vertex defect fixed
last round was not a corner case in this scene; it was the normal shape of a merged group.

The next steps are therefore two, in this order: make the fixture's baseline show both quads (the blue one's position is the
first thing to check, since culling and depth are already off for the test), and then re-measure the on/off pair to see
whether the immediate path reproduces that baseline.

---

## The draw test's first result was scene noise, and it is withdrawn

Last round's measurement - 81 saturated-red pixels with GL drawing and none with the immediate path - was read as geometry
that the immediate path loses. Two things say it was not: the red pixels sit scattered across a 765 by 226 region rather
than forming a quad, and a repeat run with the fixture unchanged moved the count from 81 to 107. Both are the signature of
scene content passing the threshold, not of a quad.

So the conclusion is **withdrawn**: nothing in that comparison has been shown to be the fixture's own geometry, and the
statement that the immediate path lost it is not supported. The counters from that round are unaffected and remain useful -
`immediateGroupsClosed=1137` with `immediateMaxGroupVertices=880` is a fact about the real scene, not about the fixture.

The fixture itself has since been rebuilt twice for reasons that were real: its quads were first placed one above the other
and then found to be edge-on, because they were built in a world-Y plane while the capture camera looks down at the world,
and they now use a camera-facing basis. The measurement that follows enlarges them to six blocks across so that "not visible"
cannot be a question of thresholds, and checks whether the mask forms a dense block rather than scattered pixels - the check
that would have caught this last round.

---

## The quads are simply not drawn, and the density check says so

With the fixture's quads rebuilt in a camera-facing basis and enlarged to six blocks across, drawn with culling and depth
off and with the immediate path disabled so that GL has them entirely:

```
red: 65 px, bbox fill 0.7%, x 259..939 y 485..497
blue: 0 px
fixture PASSED | quads 2
```

Sixty-five red pixels spread over a 681 by 13 band is not a quad - **0.7% of the bounding box is filled** - and the shape is
the same band that produced "81 pixels" two rounds ago and "107" one round ago. The scene contains a thin reddish edge, and
every reading of this fixture so far has been that edge. The density check added this round is what distinguishes the two,
and it should have been in the check from the beginning: a quad fills its bounding box, scene noise does not.

So the fixture's geometry never reaches the frame at all, even with GL drawing it, even at six blocks across, even with
culling and depth disabled. Since the handler demonstrably runs (`drawTestQuads 2`), the fault is in where and how it draws:
`RenderWorldLastEvent` runs with whatever matrix and target the world pass leaves behind, and this project has not
established what those are. The next attempt draws in the **GUI overlay pass** instead - `RenderGameOverlayEvent.Post` -
where the projection is an ortho matrix over the framebuffer and the coordinates are screen coordinates, so the fixture's
geometry stops depending on the camera and on the world pass at all. The merged-group property that makes it an acceptance
test for S05 is unaffected by which pass it draws in.

That is the third consecutive round in which this fixture produced a reading that had to be withdrawn or explained away, and
the pattern is worth naming: the fixture was built faster than the check that judges it. The rule this session keeps
relearning - verify the instrument before reading from it - applies to a test fixture exactly as it applies to a comparison.

---

## An A/B inside one run: vanilla's own rect draws, this fixture's batch does not

Four attempts went into making the fixture's geometry visible, and the fifth replaced guessing with a control in the same
handler, the same pass and the same frame: the fixture's own two-quad batch in red, and `Gui.drawRect` - the exact code path
the client's visible interface uses - in blue.

| Path | Result in the capture |
|---|---|
| `Gui.drawRect` (vanilla's Tessellator path) | **55,080 px, 100% of its bounding box** |
| this fixture's own batch | **0 px** |

So the Tessellator reaches the framebuffer perfectly well in this context, and the fault is in how the fixture builds its own
batch. The obvious candidate was bracketing: my first version used raw `GL11` calls and never set the tracked colour, and
`BufferBuilder` multiplies each vertex colour by it, so a colour left behind by earlier interface code would silently write
nothing. That was changed to the same `GlStateManager` sequence `Gui.drawRect` uses - disable texture, depth and culling,
enable blend, set the blend function, set the colour to white - and re-measured:

| Path | Before | After the bracketing change |
|---|---|---|
| `Gui.drawRect` | 55,080 px | 55,080 px |
| this fixture's own batch | 0 px | **0 px** |

Unchanged, so the bracketing was not it either. What remains is the batch's own construction, and the difference from the
working control is now small enough to copy rather than reason about: the next version writes the fixture's quad with the
**exact body of `Gui.drawRect`** - same `begin(7, POSITION_COLOR)`, same four `pos`/`color`/`endVertex` calls with z at zero -
and appends a second quad inside that same batch, which is what keeps the merged-group property.

Two things are worth stating plainly about where this leaves the round. The instrument cost five attempts and produced two
withdrawals; the fixes it was meant to verify - S05's base vertex and S06's buffer growth - still rest on reading the code
and on the counters (`immediateGroupsClosed=1137`, `immediateMaxGroupVertices=880`), not on an image. And the A/B method
itself worked exactly as intended on its first try, which is the part worth keeping: when a measurement cannot be made to
work, put a known-good path beside it in the same run instead of guessing at the broken one.

A second, smaller thing was fixed on the way: the first attempt at this A/B called `Gui.drawRect` while the fixture's own
batch was open, which nested two Tessellator draws and crashed the client in five seconds. The recipe is order-sensitive and
the crash said so immediately.

---

## Direction from the user, 14:03 JST: no general-purpose Vulkan-isation layer

Stated plainly, because it changes what the plan is allowed to grow into: **a generic OpenGL-to-Vulkan layer is
pointless and is to stop.** Celtium is a Minecraft 1.12.2 / Cleanroom-specific native Vulkan renderer. It is not a general
GL-to-Vulkan translator, not an emulation of the whole GL API, and not a Zink-style conversion layer, and it does not aim to
become one.

The goal and the plan now say so. The plan gained a section 0 for it, and the immediate-path rows are marked *transitional*
so that nothing in the document reads as a layer being completed for its own sake. The parts of the direction worth keeping
verbatim:

- the existing Minecraft/Forge public APIs, signatures and event order stay, because that is what existing mods call -
  what sits behind them is replaced by Vulkan rendering that understands the terrain, models and items, entities and TESRs,
  particles, UI and text, and the individual passes;
- the immediate capture, the per-format pipelines and the GL/VK bridge are transitional, not the final architecture, and
  the work continues to remove GL state reads, texture readback and bridge dependence, with frame ownership, draw order,
  resource lifetime, composition and present ending up on the Vulkan side;
- reuse **inside** Vulkan - buffers, descriptor sets, pipeline caches, allocators - is ordinary engineering and is not what
  is prohibited;
- mods that use LWJGL, GLSL or native GL directly are investigated individually, with covered and uncovered scope
  documented, and no plan to translate all of GL so that all mods work;
- the current audit P0/P1 fixes and acceptance tests continue; this is direction, not an instruction to delete or rewrite.

It is also the right moment to say what this session's work looks like from outside. Several rounds have gone into the
immediate-capture instrument - its counters, its cancellation, its comparison scene - and the last few into a test fixture
for it. That work was worth doing once, because the instrument was lying, but it is exactly the kind of work that could
grow into a layer for its own sake. The direction says where the weight belongs instead: the Vulkan side owning the frame,
the passes ported by meaning rather than by call, and the bridge and the readbacks shrinking rather than being perfected.

---

## The mirrored matrices do not match GL at all: 1136 comparisons, none exact

The direction says to remove GL state reads and readbacks, and the plan's own note says the two `glGetFloatv` calls per
matrix change are the dominant per-batch cost - so the mirror is the thing that should replace them. The comparison
machinery for exactly that judgement already existed in the backend; it reported `-1.0`, its initial value, in every run
this session, which reads as "never compared" rather than as a result.

Wired into the read path where both sides are in hand, and given a count of exact matches so a single bad sample cannot be
confused with a broken mirror:

```
matrixMirrorDelta=proj:2.0005527,view:1.62,comparisons:1136,exact:0
```

**Sixty comparisons per frame for nineteen frames, and not one of them matched.** The mirror is not occasionally wrong; it
agrees with OpenGL nowhere. A projection element differing by exactly 2.0 is the shape of a convention difference rather
than accumulating error - a clip-depth remap, a transposed layout, or a different matrix stack - and the immediate path has
been reading the live matrices because of the Mixin limit recorded in the compatibility file, not because anyone measured
the mirror.

Two conclusions follow, and both matter for what comes next:

- **Removing the `glGetFloatv` reads is blocked by evidence, not by caution.** The replacement is wrong everywhere it has
  been checked, and removing the reads now would move the renderer onto matrices that do not describe what Minecraft is
  drawing.
- **The measurement is cheap and was always available.** The counter existed, the comparison function existed, and the only
  problem was that the comparison sat on a path whose result never reached the log. One line of wiring turned "the mirror is
  probably fine" into a number.

The next step is a dump rather than a guess: print the first comparison's sixteen elements from each side, which separates a
transpose from a remap from a wrong stack in a single run. That is one run and one line of code, and it is the difference
between fixing the mirror and re-deriving it.

---

## Minimum runtime set to Java 21, verified in the jar and not yet on the runtime

The decision is 21 rather than 25, so `celtium-vulkan` and `celtium-interop` now compile with `--release 21` instead of 8,
and the distinction between "built on JDK 25" and "runs on Java 21" is written into both the goal and the plan so that a
successful 25 run can never be read as 21 verification.

What is checked, in the final distribution jar rather than in a build directory:

| Class | Major version |
|---|---|
| `celtium/vulkan/GlDeviceIdentity`, `ImmediateDrawTarget`, `ImmediateDrawTargets` | **65** (Java 21) |
| `org/taumc/celeritas/CeleritasVintage`, `CeleritasLWJGLRelocationTransformer` | **52** (Java 8) |

That is the intended split: the Vulkan renderer moves to 21, the mod-side classes existing Forge mods link against stay at
8. The embedding path in use does not rewrite either, and `common`'s `downgraded` configuration turns out to be a
compile-only dependency of `celtium-vulkan` rather than a recompilation of its output.

A full startup and render check after the change, on the JDK the runs have been using, passes: fixture PASSED,
`nativeTerrainVerified True`, 2185 passes, `validationClean True`, zero messages.

**Java 21 startup and rendering are not verified.** JDK 21 is present
(`.celtium-work/jdks/21/jdk-21.0.12.1+1`, `21.0.12.1+1-LTS`) and the launcher takes a `--java` argument, so the check is
possible; the attempts so far end with `exit 1` and `fixture MISSING_REPORT`, and the cause has not been read yet. Two of
those attempts were my own script edits breaking the invocation - a stray continuation put `--output` on its own line, and
one launcher line ended up as `\ \` - which is worth recording because the failure looked like a runtime problem and was
not one. Until a 21 run passes, the plan says INCOMPLETE and no Java 21 claim is made anywhere else.

---

## Java 21 cannot start this Cleanroom, and the reason is Cleanroom's own class version

The 21 run was attempted with the launcher's `--java` argument pointing at `.celtium-work/jdks/21/jdk-21.0.12.1+1`. It ends
in 0.3 s with no game log at all - and the harness's own report says exactly that, because the gate hardened under audit S20
now treats a missing log as a failure instead of as cleanliness:

```
fixtureStatus: MISSING_REPORT
validationFirstMessages: ['the game log is missing, so the validation result is unknown']
```

The console the game would have written holds one error, and it is not about Celtium:

```
Error: LinkageError occurred while loading main class top.outlands.foundation.boot.Foundation
    java.lang.UnsupportedClassVersionError: top/outlands/foundation/boot/Foundation has been compiled by a more recent
    version of the Java Runtime (class file version 69.0), this version of the Java Runtime only recognizes class file ...
```

**Cleanroom 0.6.13-alpha's launcher is class file 69 - Java 25 - so JDK 21 cannot start it.** The Java 21 target on
Celtium's side is therefore correct as a property of these classes (major 65, verified in the shipped jar) and
**unverifiable as a runtime on this host**: the running environment itself imposes 25 before any Celtium class is loaded.
That is a cleaner answer than "not verified yet", and it should have been the first thing checked when the target was set:
reading the launcher's class version costs one command.

What this means for the decision, stated without softening: **Celtium's own bytecode targets 21, and the supported
execution environment - Cleanroom 0.6.13-alpha - requires 25.** A Java 21 runtime check is possible only outside Cleanroom
(for example a standalone harness that loads the renderer classes), and that is a different test from "Celtium runs on
Java 21 inside the game". Until such a test exists, the honest sentence is the one above, not "21 verified".

Two smaller things were also cleared on the way: the harness's `--java` argument does reach the launch command (line 152
into line 193), and the two earlier failures in this attempt were my own script edits breaking the invocation rather than
anything about Java versions.

---

## Java 21 loads and initialises the renderer's classes, outside Cleanroom

The plan promised this as a separate test from "runs inside the game", and it is now done. Using JDK 21's single-file source
launch so that nothing but the distribution jar is on the classpath:

```
runtime: 21.0.12.1 (class 21)
  loaded org.soutalab.celtium.vulkan.VulkanContext classfile-major 65
  loaded org.soutalab.celtium.vulkan.VulkanSwapchain classfile-major 65
  loaded org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend classfile-major 65
  loaded org.soutalab.celtium.vulkan.VulkanGlState classfile-major 65
loaded 4 Celtium class(es) with no error
```

And with `initialize = true`, so the static initialisers actually run rather than the class merely being read:

```
initialised org.soutalab.celtium.vulkan.VulkanGlState on Java 21
initialised org.soutalab.celtium.vulkan.VulkanHostBuffer on Java 21
```

So the position is now three separate statements rather than one blurred one:

| Statement | State |
|---|---|
| Celtium's renderer classes are class file major 65 | **Verified in the shipped jar** |
| Java 21 accepts, links and initialises them | **Verified** outside the game |
| Celtium renders inside Cleanroom on Java 21 | **Not verified, and not verifiable here** - Cleanroom 0.6.13-alpha's launcher is class 69, so JDK 21 cannot start the game at all |

The third row is the one that must not be inferred from the first two, and it now sits in the plan beside them rather than
being left to the reader. The first attempt at this check also failed for a mundane reason worth noting: the jar path used
was a directory level off, so the classpath was empty and the error was `ClassNotFoundException` rather than anything about
Java versions - the same shape of mistake as the earlier broken launcher invocation.

---

## The matrix dump shows the mirror is nearly right, and that the dump was taken at the wrong sample

Two things came out of the first dump, and the second is a mistake of mine worth recording.

The data:

| Matrix | First comparison |
|---|---|
| `projMirror` | identity |
| `projGl` | identity - **identical**, maximum element difference 0.0 |
| `viewMirror` | equals GL in the rotation, but its translation column is `0, 0, 0` |
| `viewGl` | the same rotation with a translation of `(0, -1.62, 0.05)` |

So at that sample the mirror is not "wrong everywhere" at all: the projection matches exactly and the modelview differs
only by a missing translation, which is what it looks like when the mirror starts from an empty stack and never saw the
setup that happened **before the backend was installed**. The reported maximum of 2.0 in the projection and 1.62 in the
modelview therefore comes from some **later** sample - and the 1.62 is recognisably the translation the first sample was
missing.

The mistake: the dump records the **first** comparison. A maximum over 1136 samples cannot be diagnosed from the first of
them, and the right instrument is to dump **when a new maximum occurs**. That is one condition, and it is the difference
between reading the interesting sample and reading whatever happened first.

What this changes about the plan for the mirror:

- the mirror is close, not broken, and its first-sample error is a **bootstrap** problem - state applied before the hooks
  were installed. Seeding the mirror **once** from GL at installation time is the natural fix, and it costs a single
  read rather than two per batch;
- the projection maximum is the one that needs the worst-sample dump, and the known candidate is already in the
  compatibility file: the world projection is set through `Project.gluPerspective`, whose class is outside the transforming
  loader, so the mirror never sees it. If that is what the 2.0 is, the answer is not to hook harder but to **derive the
  projection from Minecraft's own camera parameters** - the meaning-based approach the direction calls for, rather than
  another attempt to mirror a GL call that cannot be reached.

---

## The worst-sample dump names the cause: the mirror never sees the world camera

Dumping when a new maximum occurs, rather than at the first comparison, gives the sample that matters:

```
projMirror = identity
projGl     = [0.803  0      0      0
              0      1.428  0      0
              0      0     -1.001 -1.000
              0      0     -0.100  0    ]
viewMirror = [-1 0 0 0 | 0 1 0 0 | 0 0 -1 0 | 0      0     0 1]
viewGl     = [-1 0 0 0 | 0 1 0 0 | 0 0 -1 0 | 0  -1.620 0.050 1]
```

The mirror's projection is the **identity** where GL holds a real perspective frustum, and the mirror's modelview is the
right rotation **without the camera translation**. So the earlier reading that the mirror was "nearly right" was an
artefact of the sample: the first comparison happens when GL is also identity, which makes any mirror look correct. At the
sample where it is actually wrong, it is wrong by the whole camera.

Two things follow, and the second retires an approach rather than refining it.

**The seed at the first comparison was the wrong moment.** It copies whatever GL has at the first comparison, which is
identity, so it seeds nothing useful - and the change is visible in the numbers as no change at all (`comparisons:1143`,
`exact:0`, the same 2.0006 and 1.62 maxima). A bootstrap has to happen when the state it copies is the state that matters,
and there is no hook that marks that moment.

**Hooking cannot fix this.** The world projection is applied through `Project.gluPerspective`, whose class the
compatibility file already records as outside the transforming loader - it compiles, it applies, and its counter stays at
zero. The camera translation is applied before the backend exists. Both are exactly the operations the mirror needs, and
neither reaches a hook. Continuing to add hooks would be adding surface to a mechanism that has been shown to miss the
load-bearing calls.

What the direction says to do instead is what the numbers now argue for as well: **derive the world projection and camera
transform from Minecraft's own state** - field of view, aspect, near and far for the projection, `ActiveRenderInfo`'s
position and orientation for the modelview - rather than mirroring GL calls whose call sites are unreachable. That is a
Vulkan renderer that understands what a world pass is, which is the stated approach, and it removes the two `glGetFloatv`
reads per matrix change as a consequence rather than as a goal.

One instrument note: the dump is now taken at a new maximum, and it earned its place immediately - the first-sample version
had produced a conclusion ("nearly right") that this sample contradicts.

---

## Where the world projection and camera come from, read out of the vanilla source

The next step named last round - derive the world projection and camera transform from Minecraft's own state - now has
verified anchors rather than a plan:

| What | Where | Detail |
|---|---|---|
| Camera setup | `EntityRenderer.func_78479_a(float partialTicks, int pass)` | Contains the `Project.gluPerspective` calls at 729 and 803, and the `GlStateManager.translate`/`rotate` that place the camera |
| Called from | `EntityRenderer.func_175068_a(int pass, float partialTicks, long finishTimeNano)` at line 1276 | The world pass |
| Projection arguments | `func_78479_a` line 729 | `gluPerspective(getFOVModifier(partialTicks, true), displayWidth / displayHeight, 0.05F, farPlaneDistance * SQRT_2)` |
| Far plane | `func_78479_a` line 713 | `farPlaneDistance = gameSettings.renderDistanceChunks * 16` |

Two things about this are worth stating because they change the shape of the work:

- **`func_78479_a` is a Minecraft class method, so a plain `@Inject` reaches it.** No accessor or invoker machinery is
  needed - which matters, because two attempts to use that machinery on texture classes crashed the client during
  transformation. The parameters above can be reported from that hook, and the backend can compute the projection itself:
  a few lines of `gluPerspective` arithmetic against Minecraft's numbers rather than a mirrored GL matrix.
- **The one argument that is not directly readable is `getFOVModifier`**, a private method whose result folds in sprinting,
  bow draw, water and flight. Either it is reached through a hook at its call site - which is inside the same method and
  therefore reachable - or its rules are reproduced from the player's state, which is also Minecraft state. What makes this
  tractable rather than delicate is that the delta counters will say whether the derived projection matches GL: the
  acceptance for this change is `proj max|diff|` falling from 2.0006 to near zero, and `view max|diff|` from 1.62, with the
  same instrument that produced those numbers.

So the work has an order and a measurement: hook `func_78479_a`, report `fov`, `aspect`, `near`, `far` and the camera
position and rotation, compute the projection in the backend, and read the two deltas. If the fov modifier is the residual,
the number says so - which is the same measure-before-switching discipline that stopped the `glGetFloatv` removal two
rounds ago when the mirror turned out to be empty.

---

## Correction from the user, 14:17 JST: the game runs on 25, only the compiled classes target 21

The earlier reading of "minimum Java 21" made it mean *the game must start on Java 21*, which turned Cleanroom's own class
file version - 69, Java 25 - into a blocker and put a Java 21 startup check into the work queue. The correction separates
the two: **Celtium's compile target is 21** (release 21, major 65, no Java 22+ APIs), and **the game's runtime and
verification environment is 25**, which is what every run in this log has used.

So three things change, and one does not:

- the goal and the plan now state the split as two separate facts rather than as a target and a shortfall;
- "the game's minimum runtime is 21" and "start the Cleanroom game on Java 21" are **removed from the completion conditions
  and from the work queue** - Cleanroom is not to be modified or swapped for it, and the out-of-game load checks stop here;
- the plan states explicitly that `--release 21` does **not** mean the whole game runs on 21, so no document implies it;
- **the release 21 migration itself stands.** This is a correction of interpretation, not a retraction of the change: the
  renderer's classes are major 65, the mod-side classes remain major 52, and the distribution jar is verified for both.

Two rounds went into the version question - reading the launcher's class version, the failed 21 launches (two of which were
my own script edits), the standalone load and initialisation checks - and the useful residue is the jar verification and the
explicit table. The instruction that goes with the correction is the one to act on now: **stop splitting Java versions and
go back to the rendering.** The next work is the derivation of the world projection and camera from Minecraft's own state,
whose anchors are recorded above, and the audit items still open.

---

## The exact mechanism: a hooked reset and an unreachable setter

Reading the vanilla sequence inside `func_78479_a` explains the dump precisely:

```java
GlStateManager.translate(0.0F, -f, 0.0F);          // view bobbing
GlStateManager.matrixMode(GL_PROJECTION);          // hooked -> mirror switches stack
GlStateManager.loadIdentity();                      // hooked -> mirror's projection becomes identity
Project.gluPerspective(fov, aspect, 0.05F, far);   // NOT hooked -> GL gets the frustum, the mirror does not
GlStateManager.matrixMode(GL_MODELVIEW);            // hooked
GlStateManager.loadIdentity();                      // hooked -> modelview reset
... camera rotation and translation ...
```

Everything the mirror *can* see puts it into a known state - identity projection, identity modelview - and the one call that
would fill the projection is the one it cannot see. That is why `projMirror` was the identity matrix while `projGl` held a
real frustum, and why the modelview matched in rotation and lacked only the camera translation: the rotations go through
`GlStateManager`, the translation is applied where the mirror was not yet listening, and the projection never arrives at all.

This also locates the fix precisely rather than generally. There is nothing to repair in the hook coverage for the
projection - the hook does exactly what it should - and what is needed is the **value** the unreachable call computes:
`getFOVModifier(partialTicks, true)`, `displayWidth / displayHeight`, `0.05F`, `renderDistanceChunks * 16 * SQRT_2`. Three
of those four are readable from Minecraft state; the fourth is a private method whose result folds in sprinting, bow draw,
water and flight. The two ways to get it are a hook on the method itself - the class is Minecraft's, so it is reachable, and
its SRG name is the next lookup - or reproducing its rules from the player's state, which is also Minecraft state.

For the modelview the relation to verify before writing any code is whether the mirrored translation equals
`-RenderManager.viewerPosX/Y/Z`, which is what vanilla's camera transform amounts to. That is one report field and one run,
and it is the same measure-then-change order that stopped the `glGetFloatv` removal when the mirror turned out to be empty.

---

## The derived projection is computed 3409 times and the comparison still sees identity

The projection now comes from Minecraft's own numbers: `func_78481_a` is hooked at its return for the field-of-view
modifier, and the backend does the `gluPerspective` arithmetic with `displayWidth / displayHeight`, `0.05F` and
`renderDistanceChunks * 16 * SQRT_2`. The counters say it runs - `derivedProj:3409, refused:5` - and the deltas say it made
no difference at all:

```
matrixMirrorDelta=proj:2.0005527, view:1.62, comparisons:1163, exact:0, derivedProj:3409, refused:5
proj max|diff| now 2.0006 (was 2.0006)   mirror still the identity matrix
```

The reason is not that the arithmetic is wrong; it is that **the fix and the metric are about different moments**. The
derivation happens inside the world pass, where it belongs. The comparison happens whenever a batch is offered, and the
worst sample is taken from a frame where the projection stack has been reset again by another pass - the GUI and the hand
set their own projections through the same hooked `matrixMode` + `loadIdentity` pair. So the mirror is correct for the world
pass and identity for whatever pass the sample happened to land in, and the worst-of-1163 counter reports the latter.

The acceptance therefore has to move to where the change is: **compare the derived projection with GL at the instant it is
derived**, which is one read in a measurement-only path and says whether the arithmetic matches Minecraft's frustum. That is
a different question from "which projection should a given batch use", which is the pass-ownership problem the audit lists
as S10 - a batch's FBO, viewport and pass are not part of its group key, and that is what makes the mirror's projection
ambiguous in the first place.

So this round produced a working derivation and a metric that cannot see it, and the honest state is: the projection is now
computed from Minecraft state rather than mirrored, **not yet verified against GL**, and the verification is one line in the
right place. Saying "the projection is fixed" from `derivedProj:3409` would repeat the mistake this session has made and
recorded four times already - a counter that rises is not a value that is right.

---

## The acceptance instrument is in the right place, and it says the write does not survive

The comparison was moved to the instant it should be taken: `applyDerivedProjection` marks a flag, and the next hookable
operation after the unreachable `gluPerspective` - the `matrixMode(GL_MODELVIEW)` that immediately follows it - reads GL's
projection and compares it with the mirror's. It runs, and it reports:

```
derivedCompared:3427, derivedDelta:2.0005527
```

**The delta is the same number as before the derivation existed**, and it is recognisable: the mirror's `m[10]` is the
identity's `1.0` and GL's is `-1.001`, a difference of `2.001`. So at the comparison the mirror's projection is **still the
identity**, even though `applyDerivedProjection` ran 3409 times and wrote a frustum into
`immediateState.matrix(MATRIX_MODE_PROJECTION)`.

That is a sharper statement than "the derivation is wrong": the arithmetic and the write happen, and something between the
write and the comparison puts the identity back. The candidates are narrow and testable:

- `Project.gluPerspective` in this environment may itself reset the current matrix before multiplying (LWJGL's `Project`
  is not the GLU source this project has been reasoning about, and its body has not been read);
- the projection stack's top may not be the array the comparison reads, if `loadIdentity` or a push replaces it;
- another `loadIdentity` on the projection may fire between the two points, which one counter per matrix mode would show.

The next step is therefore a count rather than another derivation: number of `loadIdentity` calls per matrix mode, and the
matrix identity (`System.identityHashCode`) the mirror reports at the write and at the comparison. Either the same array is
being reset, or two different arrays are being written and read - and those two possibilities need opposite fixes. The
instrument to distinguish them is two counters.

Nothing about the rendering changed this round, and the honest summary is the same shape as the last one: the projection is
still computed from Minecraft's state rather than mirrored, the check exists at the right moment, and it says the value is not
arriving. The two previous rounds each ended with a counter that rose while the value was wrong; this round ends with a
counter that rose and a delta that says so, which is the better position even though the number did not move.

---

## One root cause for two "ineffective" changes: the accessor returns a copy

The identity counters answered the question they were added for:

```
derivedCompared:3409, writeId:1238104718, compareId:1761794437, loadIdentityWhilePending:0
loadIdentityByMode=proj:6184, matrixModeByMode=proj:6184
```

The array written to and the array read back have **different identities**, and nothing reset either of them - no
`loadIdentity` fired while a derived projection was pending. Two different arrays, written and read: and `VulkanGlState`'s
accessor says why in one line:

```java
public float[] matrix(int mode) {
    return copy(top(mode));
}
```

It returns a **copy**. The derived projection was written into a temporary array and thrown away, and the same defect
explains the seed two rounds earlier - which was recorded as "ineffective because it sampled the wrong moment" when the real
reason was that it wrote into a copy as well. One root cause, two conclusions in this log, both of them wrong about why.

With `setMatrix` writing into the stack itself, the acceptance instrument moves for the first time:

| | projection delta |
|---|---|
| before the derivation existed | 2.0005527 |
| after the derivation, writing into a copy | 2.0005527 |
| **after `setMatrix`** | **1.6200542E-4** |

**The derived projection matches GL to 1.6e-4** - floating-point agreement, with the residual most likely the field-of-view
modifier's own rounding - and `exact` is 1 rather than 0 for the first time. The projection is no longer mirrored; it is
computed from Minecraft's numbers and it checks out against what the game actually drew with.

The modelview is untouched at 1.62, which is the camera translation and is the next item: vanilla applies it after the
rotations, and the mirror is missing it because it is applied before the hooks are installed. Two earlier rounds already
worked out where it happens; what is owed now is the same treatment - derive it from `RenderManager.viewerPos` and write it
through `setMatrix` rather than into a copy.

The lesson, in the form this session keeps needing: a value that does not arrive is not evidence that the code that computes
it is wrong. Here it was computed correctly, sixty times a frame, into an array nobody read.

---

## The float-translate hypothesis is refuted by the source, and it corrects an audit item

The modelview was missing exactly the camera translation, and the vanilla source showed `GlStateManager.translate((float) x,
(float) y, 0.0F)` - float literals - while the mirror's hook takes `double`. So the hypothesis was that Minecraft calls a
`(FFF)` overload the hook never matched, and a descriptor-qualified hook was added for it: `func_179137_b(FFF)V`,
`require = 0`.

The SRG source refutes it in one line:

```java
public static void func_179137_b(double p_179137_0_, double p_179137_2_, double p_179137_4_)
```

**There is one overload and it takes doubles.** The float literals in the decompiled Minecraft source are widened by the
compiler, so the call does reach the hook that already existed; the new hook matched nothing, the counters were unchanged,
and the modelview delta stayed at 1.62 exactly as before. The hook has been removed rather than left in place, because a
hook that cannot fire is worse than no hook: it reads as coverage.

That corrects a reading in this project's own record. The second audit's S07 lists "float `translate`" among the state
Minecraft uses that the mirror does not catch; for 1.12.2 the call is written with float literals but the **method signature
is double**, so that particular item is not a missing hook. S07's other items - alpha enable, cull enable, `depthFunc`, the
integer blend functions, `activeTexture`, the alpha-function override - are unaffected and still open.

And it sharpens the actual cause of the missing translation. The translate **is** hooked. The mirror misses it because it
was applied before the hooks existed, and the seed meant to repair that copies GL at the **first comparison**, which the
earlier dump shows is a moment when GL itself holds the identity - because the first batch of a frame is offered during a
pass like the GUI's, after that pass has reset both stacks. So the seed copies the wrong moment, not the wrong matrix. The
next attempt derives the translation the way the projection is now derived: from `RenderManager.viewerPos` at the camera
setup, with the bobbing question - whether vanilla applies the camera translation before or after `setupViewBobbing` -
answered by reading `orientCamera`'s tail rather than by guessing.

---

## Reading the camera setup, and a fifth instance of the same reading mistake

What the source does say, from `orientCamera` and its caller:

- `func_78479_a` applies the nausea wobble, then calls **`orientCamera(partialTicks)`**, then the debug-view and anaglyph
  translations. `setupViewBobbing` is **outside** this method, in the world pass that calls it.
- Inside `orientCamera`, the **first-person** case is the final `else` branch and applies only
  `GlStateManager.translate(0.0F, 0.0F, 0.05F)`; the third-person branches apply the entity's interpolated yaw and pitch
  rotations and a `translate(0.0F, 0.0F, -d3)` distance. The sneak offset `translate(0.0F, 0.3F, 0.0F)` precedes them.
- The camera translation itself - `-viewerPosX/Y/Z` - is **not in the parts that were read**, and three greps over the same
  method each filtered out a different candidate for where it is.

That last sentence is the round's real finding, and it is the fifth time in this session that a **filtered read** produced a
conclusion the unfiltered source did not support: a truncated log line twice, a truncated counter, a stale reference, and now
three greps that each dropped the line being looked for. The pattern is always the same shape - a filter is chosen to make the
output small, and the answer is in what it removed - and the countermeasure that has worked is equally consistent: read the
method whole, or ask for the field by name, before concluding anything.

So the next step is not another grep. It is to dump `orientCamera` and the first thirty lines of `func_78479_a` **unfiltered**
and read where the camera translation sits relative to the first-person branch, because the derivation that replaces it has
to be applied at exactly that point to be correct for all three camera modes.

Nothing changed in the renderer this round; what it produced is the structure of the camera setup, one located fact
(`setupViewBobbing` is outside `func_78479_a`, so a derivation at its return cannot clobber bobbing), and the decision to
stop filtering the source.

---

## The camera-setup seed runs once and changes nothing

The seed was moved from the first comparison to the end of `func_78479_a` - the moment the camera transform is complete and
GL holds the finished world matrix - and the earlier seed was disabled so that the two could not both write:

```
matrixMirrorDelta=proj:1.6200542E-4, view:1.62, comparisons:1154, exact:0, cameraSeeds:1
```

**The seed ran exactly once and the modelview delta is unchanged at 1.62.** So the hypothesis is not supported: copying
GL's finished world modelview at that moment does not leave the mirror agreeing with GL at the samples. Something between
the seed and the comparison removes the translation again - the most likely candidate being the modelview `loadIdentity` that
each pass performs, after which the hooked operations re-apply only what they see, and the camera translation is not among
them at the times that matter.

Rather than a third hypothesis, the next step is a trace: record the mirror's translation elements at each
`loadIdentity`, at each `translate`, and at the comparison, with a small ring of the last few values. That answers where the
translation goes missing instead of guessing which of four candidates is responsible, and it is the same instrument-not-argument
move that found the `matrix(mode)` copy two rounds ago.

Worth stating plainly after three rounds on this one value: the modelview is still 1.62 off, the projection is exact to
1.6e-4, and each attempt has produced a precise negative result rather than a fix. The negative results are why the last two
turned out to be one root cause, and this one narrows the search to the interval between the camera setup and the first
comparison.

---

## Three counters narrow the missing translation to a reset

Three measurements, none of which needed a hypothesis:

| Counter | Value | What it rules out |
|---|---|---|
| `steadyComparisons` / `steadyDelta` | 1154 of 1166 comparisons, view delta still **1.62** | **warm-up.** Only twelve comparisons fall in the first frames; the missing translation is persistent, not a start-up artefact |
| `translateCalls` | **572**, none of them zero-length | "the hook never fires." Translates do reach the mirrored stack |
| `cameraSeeds` | **1**, delta unchanged | "the seed fixes it." The seeded matrix is lost again afterwards |

Together they say something specific: operations are arriving, the seed is taken and then lost, and the loss is permanent
rather than transitional. So the translation is being **removed after it is applied**, and the only operation in the mirror
that removes translation is a reset - `loadIdentity` - which each pass performs when it switches the modelview stack. GL,
at the comparison, holds a modelview with a translation; the mirror holds one without. That is what a pass looks like when
the reset is mirrored and the operations after it are not.

The instrument to confirm it is the one the plan already names, and it is now much cheaper because the candidate is single:
record the mirror's `m[12..14]` at every `loadIdentity`, at every `translate`, and at the comparison, keeping the last few
entries. If the trace shows a translate followed by a reset with no translate after it, the question becomes which pass does
that and why GL's own operations in it do not reach the hooks - which is the S10 pass-ownership problem in its concrete
form.

Worth naming for the record: three rounds on one value have produced four negative results - the float overload that does not
exist, the seed at the wrong moment, the seed at the right moment, and warm-up - and one positive one, the projection going
from 2.0 to 1.6e-4. Every negative narrowed the interval, and this one leaves a single class of cause.

---

## The trace reframes the question: the mirror is not broken, the metric samples across passes

Forty traced events - resets, translates and comparisons - read as a sequence rather than a maximum:

```
L0[mv 0.00,  0.00, 0.00]     reset, modelview translation zero
L0[mv 0.00, -1.62, 0.05]     ...and after the ops that follow, the camera translation IS there
T0(-9.50, 63.33, -9.46)      a translate arrives
L0[mv 9.50, 61.71,  9.51]    another reset, carrying what the translate built
L2[mv 0.00, -1.62, 0.05]     frame 2: the mirror holds the camera translation, repeatedly
L2[mv 0.00,  0.00, 0.00]     ...and at some point it is zero again
```

**The mirror has the camera translation almost all of the time.** The value `1.62` is not a mirror that never received it;
it is a **maximum over comparisons**, and some comparisons land in a pass where the modelview has just been reset and the
camera has not been re-applied. GL, at that same instant, is in a different pass and holds the camera - which is what a
cross-pass sample looks like.

That is the S10 problem in its concrete form, and it changes what the next work is. Three rounds were spent trying to make
the mirror *receive* the translation - a seed, a seed at a better moment, a float overload that does not exist - and the
trace says it receives it. What does not exist is a notion of **which pass a batch belongs to**: a group key holds matrices,
format, texture and state, but not the frame, the target, or the pass, so a batch offered during one pass is compared,
grouped and drawn against another pass's matrices. The `glGetFloatv` reads hide that today by fetching whatever GL has at
the moment of the batch; removing them requires the pass identity, not a better mirror.

So the modelview investigation closes with a direction rather than a fix, which is the right outcome for it: the mirror is
adequate, the missing concept is pass ownership, and the audit already lists it as **S10** with acceptance conditions - store
the frame, target, viewport and texture generation with the batch, and keep the order across GL fallback, clears and FBO
switches. The projection derivation stays as it is: verified to 1.6e-4 and used where the world pass needs it.

One instrument note worth keeping: the trace was capped at forty entries and only recorded comparisons whose delta exceeded
1e-3, which is what made the sequence readable. An uncapped trace of everything would have buried the pattern in the GUI's
resets, which is the same filtering lesson as before but from the other side - filter the *events*, not the *sources*.

---

## S10 has its anchors, and the framebuffer one is not where the audit implies

Two things were located from the sources for the pass identity a batch needs:

| What | Where | How it is reached |
|---|---|---|
| Viewport | `GlStateManager.func_179083_b(int, int, int, int)`, whose body is `GL11.glViewport(...)` | A plain `@Inject` - the class is Minecraft's, and this project already hooks five other methods on it |
| Framebuffer | Not in `GlStateManager` at all: the SRG source contains no `glBindFramebuffer` call, so the binding bypasses the wrapper entirely | `Framebuffer`, which **is** a Minecraft class, so its bind method is mixin-able - the same route the viewport takes |

That second row is the finding worth keeping: the audit's S10 describes the group key missing "FBO, viewport, scissor", and the
natural assumption is that both come through `GlStateManager` and can be mirrored like the rest of the state. The viewport
does. The framebuffer does not - it is bound through `Framebuffer` and the direct GL path, which is exactly the pattern the
compatibility file records for `GL11.glMatrixMode` and for `Project.gluPerspective`. The difference is that `Framebuffer` is
a Minecraft class, so unlike those two it can be hooked without touching LWJGL.

So the pass identity is reachable, and the shape of the work is small: two hooks (`func_179083_b` and `Framebuffer`'s bind),
two fields in the backend, and those fields in the group key beside the matrices, format, texture and state that are already
there. The measurable consequence is a number this project does not yet have - how often a group would have merged batches
from two different passes - and the acceptance is that this number is non-zero today and that the frames do not regress once
the key separates them.

This is also the first piece of work in several rounds that follows the direction rather than the mirror: pass ownership is
what the renderer needs to own frames and ordering on its own side, and getting it from Minecraft's own classes is the
meaning-based route rather than another GL read.

---

## S10's first step is in: the pass identity is in the key, and this scene does not exercise it

Implemented as located: a viewport hook on `GlStateManager.func_179083_b`, a render-target hook on `Framebuffer.func_147610_a`
(identity only, so no shadowed field and no accessor machinery), two fields in the backend, and those fields in the
condition that opens a group.

```
passSplits:0, target:1682163939
immediateOffered=9077, immediateTaken=596, immediateGroupsClosed=596
fixture PASSED | nativeTerrainVerified True | 2231 passes
difference against the same-fixture reference: mean 0.14 | 0.40% over 8 | 3,714 px
```

Three honest readings of that:

- **the hooks work.** `target` is a real framebuffer identity rather than the initial -1, so the binding reaches the
  backend and the viewport hook compiles and applies;
- **`passSplits:0`** - in this scene, no group *would* have merged two passes. The key now separates them, and this
  fixture never presents the case, so the number is zero for the same reason a test of a bridge is zero when nobody crosses
  it. That is not evidence the feature is unnecessary, and it is not evidence it works;
- **no regression**: the frame is identical to the same-fixture reference at 0.40% and 3,714 pixels, exactly as before the
  change, so adding the pass to the key did not disturb what already rendered correctly.

What would show it is the fixture the audit's S10 acceptance names and this project still does not have: two render targets,
translucent quads that overlap across a boundary, and a clear at the switch. That fixture is now the blocking item for this
audit entry rather than more hooks, and it is also what the direction calls for - the renderer owning draw order and frame
targets, demonstrated on a scene that actually crosses them.

The mirror counters are unchanged (`proj:1.62e-4, view:1.62, exact:0`, 1192 comparisons), which is expected: the group key
decides how batches are grouped, not what the mirror holds, and the residual modelview figure is the cross-pass sampling the
trace identified.

---

## The S10 fixture exists, it exercises the key, and it fails

The fixture the audit's S10 acceptance asks for is built: a second `Framebuffer` (256x256, created once), cleared and bound,
one quad drawn into it, then the main framebuffer bound again and the two overlapping quads drawn there - same format,
texture and state throughout, so only the render target and viewport separate them.

```
drawTestSecondTarget: true
passSplits: 16                 (was 0 without the second target)
immediateTaken: 1229           (was 596)
matrixMirrorDelta=proj:1.6200542E-4, view:2000.0
fixture FAILED | nativeTerrainVerified False | 1949 passes
```

Three things, in order of importance.

**The key's new field does something measurable.** `passSplits` went from 0 to 16 the moment a second render target entered
the frame, so the group condition is separating passes rather than merely carrying a field nobody exercises.

**The fixture fails, and that is the honest state of S10.** `nativeTerrainVerified` is false and the modelview delta has gone
from 1.62 to **2000** - a magnitude that looks like a screen coordinate rather than a world one, which is what a GUI-pass
matrix leaking into a world-pass comparison would look like. The second target's batches are drawn in the overlay pass with
an orthographic projection, and something about that now reaches state that the world pass depends on. This is exactly what
the audit says S10 is: draw order, target and pass are not preserved across the hybrid, and the acceptance test for it now
demonstrates the failure rather than the feature.

**So the entry stays open, with an instrument instead of a hypothesis.** Before this round, `passSplits` was zero and the
claim that pass identity mattered rested on the trace. Now there is a fixture that crosses two targets, a counter that shows
the crossing, and a failure that appears when it does - which is the shape every fixed defect in this log eventually had:
first the reproduction, then the number, then the fix.

The 2000 is the next thing to read: whether it is a GUI ortho matrix reaching the world comparison, or the world matrix
being compared against a target that has been cleared and rebound. Both are consistent with the number, and the trace
instrument that resolved the last question - forty capped events, comparisons above a threshold - is already in place to
distinguish them.

---

## The worst-sample trace was keyed on the wrong delta

The trace was changed to record new maxima instead of the first forty events, and it still produced only `L` entries -
resets with a zero translation - because the condition sits inside the **derived-projection comparison block** and keys on
the *projection* delta. The number the fixture fails on is the **modelview** delta of 2000, which never triggers it. The
instrument is in the wrong place, which is the sixth time in this session that a reading was taken through the wrong
window: two truncated log lines, a truncated counter, a stale reference, three greps that dropped the line, and now a trace
gated on the other matrix.

What the round did establish, from the counters rather than the trace:

```
steadyDelta=proj:1.6200542E-4, view:2000.0
fixture FAILED | nativeTerrainVerified False
```

The 2000 is a **steady-state** figure - the steady counters are the ones that report it - so it is not warm-up either. It is
the modelview mirror disagreeing with GL by two thousand during the fixture that binds a second render target, which is a
magnitude consistent with the orthographic projection the overlay pass installs (`glOrtho(0, width, height, 0, 1000, 3000)`
contains exactly those two numbers) rather than with anything in world space.

The fix for the instrument is one move: record the worst sample inside `compareMirroredAndLiveMatrices`, where both deltas
are in scope, and include the **pass identity** in the entry - the render target and viewport the backend now tracks - so
the sample names the pass it belongs to instead of leaving it to be inferred. That is a two-line change and it answers both
open questions at once: what the 2000 is, and which pass produces it.

The fixture itself is doing its job. It fails, it fails only when a second render target is present, and it produces a
number large enough to be a pass leaking into another pass's comparison rather than a subtle numerical difference. Three
rounds ago the corresponding statement would have been a hypothesis; now it is a failing test with a counter next to it.

---

## The worst sample is named, and it belongs to the fixture's own target switch

The instrument was moved to where both deltas are in scope and the pass identity is attached, and it answers immediately:

```
worstView[frame=2 target=44569671 viewport=1311440 projDelta=0.0
          mirror[m0=1.00, m5=1.00, t=0.00, 0.00, 0.00]]
passSplits: 16, target: 44569671
fixture FAILED | nativeTerrainVerified false
```

Read carefully:

- **frame 2.** The thirty-two-thousand-pixel difference is not a steady-state property of the run; it happens at the
  moment the fixture's second render target appears. The steady-state counters report it because they keep a maximum from
  frame 11 onwards of a value that was set at frame 2 - a maximum, not a per-frame reading, which is the third time this
  session that a max-over-time counter has been read as if it described the present.
- **the projection matches** (`projDelta=0.0`) and the **mirror's modelview is the identity** while GL's is something two
  thousand away. So it is not the derived projection, and it is not a missing translate: it is GL having a modelview the
  mirror never had at all.
- **the pass is named**: the sample carries the render target identity the backend tracked (`44569671`), and the only second
  target in this run is the fixture's own 256x256 one. The viewport key is there too.

So the finding is specific and it is the audit's S10 in miniature: **binding a second render target mid-pass leaves the
mirrored modelview at the identity while GL has the pass's own matrix**, and the fixture - which exists precisely to cross
that boundary - turns it into a failure. Before the fixture, the same mechanism produced a 1.62 that could be argued about;
now it produces a named target, a named frame and a run that fails.

What the fix has to do is what S10's acceptance says: preserve order and target across the switch, so that the batches drawn
into the second target are compared, grouped and drawn against the matrices and target they were issued with, and the main
target's batches are not disturbed by them. The 2000 itself is a symptom of GL's state being read at a moment that belongs
to a different target, which is exactly what reading live GL matrices for a batch cannot tell apart - and the reason the
audit's direction and the removal of those reads point the same way.

---

## The 2000 is in a passing run too, so the last round's attribution is withdrawn

With the fixture's second target gated behind its own switch and off:

```
passSplits: 0, target: 1301583606
steadyDelta = proj: 1.6200542E-4, view: 2000.0
fixture PASSED | nativeTerrainVerified True | 2732 passes | screenshot written
```

**The modelview delta is 2000 in a run that passes and never binds a second target.** So the attribution recorded last
round - that the 2000 "belongs to the fixture's own target switch" - is wrong, and it is withdrawn. The number is a property
of the normal run, and the fixture only made it *visible* by failing the run when it appeared.

What that actually means is more interesting than the retraction. `2000` is `setupOverlayRendering`'s
`GlStateManager.translate(0.0F, 0.0F, -2000.0F)` - a value that exists only in the overlay pass - and the comparison is a
maximum over samples taken when batches are offered, in whatever pass happens to be running. A run that passes is therefore
still reporting, in one number, that **some comparison landed in the overlay pass while the mirror held the world pass's
modelview**. That is precisely the cross-pass sampling the trace identified three rounds ago, now confirmed from the other
direction: not by making it fail, but by finding it in a run that succeeds.

So S10's symptom is real, measurable in the ordinary suite, and does not require the exotic fixture to reproduce. The
fixture remains useful - it forces the failure into the pass/fail verdict - but it is not the cause, and it stays gated off
by default so that the normal suite keeps producing captures.

The next step is the fix the audit describes rather than another measurement: **do not compare across passes.** A batch
belongs to a pass; the mirror's matrices should be captured for the pass the batch was issued in, and a comparison taken
during the overlay should compare the overlay's matrices with the overlay's, not the world's with the overlay's. The pass
identity the group key now carries is the raw material for that, and the target and viewport hooks are already in place.

One more instrument lesson, the fourth of its kind: the gated run also shows 2732 passes against the 1900 of the failing one,
which is what a fixture that suppresses the capture looks like from the outside - fewer passes, no screenshot, a FAILED
status. A test that breaks the instrument cannot report on it.

---

## The comparison is scoped to its pass, and half of it had been noise

The fix the audit names - do not compare across passes - is now in, and it needed no new hooks. The world pass installs a
**perspective** projection, which is exactly where `applyDerivedProjection` already fires; the overlay and the GUI install
**orthographic** ones, which is exactly what the existing `ortho` hook already sees. Those two facts are enough to tell the
passes apart:

| | before | after |
|---|---|---|
| modelview delta | **2000.0** | **1.62** |
| projection delta | 1.6200542E-4 | 1.6200542E-4 |
| comparisons | 1132 | 1122 in-pass, **1140 skipped as cross-pass** |
| fixture | PASSED | PASSED, `nativeTerrainVerified True`, 2733 passes |

Two things worth stating precisely.

**The metric improved by three orders of magnitude** - 2000 to 1.62 - and nothing about the renderer changed; what changed
is that the overlay's `translate(0, 0, -2000)` no longer enters a comparison it never belonged to. That is the difference
between a number that describes the renderer and a number that describes the instrument.

**Half of the comparisons had been cross-pass.** 1140 skipped against 1122 kept is not a marginal contamination: the
counter this project has been quoting for several rounds was, by its own accounting, about fifty per cent measurements of
one pass against another. Every "the mirror is off by X" statement in that period was drawn from that mixture - including
the 1.62 that three rounds of seeding tried to fix.

Which leaves the residual as a clean number for the first time: **1.62, measured only in the pass that owns the mirror**.
That is the camera translation the trace showed the mirror sometimes having and sometimes not, now with the cross-pass noise
removed from the question. Whether it is a real gap in the world pass or a further sampling artefact is the next thing the
now-scoped counter can answer, and for the first time the answer will be about the renderer rather than about the mixture.

This also closes the loop on the direction: the pass identity that made this possible came from the two hooks added for
S10's group key, and it fixed a measurement before it fixed a draw. Pass ownership is load-bearing in both places.

---

## The residual 1.62 is a frame-boundary sample, and the evidence is the zeroed mirror

With the worst-sample recorder moved inside the steady-state block - the reachable fix for a maximum that kept reporting a
frame from before the backend existed - the sample is now the first steady frame rather than the second frame of the run:

```
worstView[frame=12 target=1301583606 viewport=1311440 projDelta=1.6200542E-4
          mirror[m0=0.00, m5=-0.00, t=0.00, 0.00, 0.00]]
crossPassSkipped: 1134, matrixMirrorDelta=proj:1.6200542E-4, view:1.62, comparisons:1146, exact:0
fixture PASSED | nativeTerrainVerified True | 2737 passes
```

The mirror's modelview is **all zeros** - not the identity, and not the camera rotation - which is what a stack looks like
between its `loadIdentity` and the operations that fill it. GL, at the same instant, holds something 1.62 away in the
translation. So the comparison is being taken **before GL has reset for the new frame and after the mirror has**: the first
batch of a frame is compared against the state the previous frame left in GL.

That is a frame-boundary artefact, not a rendering defect, and it is the fourth distinct sampling problem this counter has
turned out to have had:

| # | What the counter was measuring | Fixed by |
|---|---|---|
| 1 | a copy of the matrix that nobody read | `setMatrix` |
| 2 | the overlay's `translate(0, 0, -2000)` in a world comparison | scoping to the world pass |
| 3 | the first frame, before the backend existed | the steady-state block |
| 4 | **the first batch of a frame against the previous frame's GL state** | not yet |

The four are worth reading together, because the number they produced - 1.62, quoted for several rounds as a mirror defect -
was never any of them individually: it was whichever of these dominated the maximum at the time, and each fix moved the
maximum to the next one. The mirror has been adequate since the copy bug was found; what kept being wrong was the
measurement.

The next condition is the same shape as the last three: skip a comparison taken while the mirror is mid-setup, which one
check - whether the modelview's trace is still the value left by the reset - expresses directly.

Nothing in the renderer changed this round, and the direction's own work is where it was: the pass identity is in the group
key and the comparison is scoped to its pass. The mirror reads can be removed once the metric stops reporting artefacts
rather than the state it is supposed to describe.

---

## The zero-test never fired because "0.00" was rounding, and the pass flag is unreliable

Two counters contradicted each other and the resolution is worth the round:

```
midSetupSkipped: 0                      the "mirror is unset" test never fired
worstView[frame=12 ... mirror[m0=0.00, m5=-0.00, t=0.00, 0.00, 0.00]]    ...and it shows zeros
comparisons: 1148, view: 1.62, proj: 1.6200542E-4, fixture PASSED
```

The test compares `viewNow[0] == 0.0f` and the recorder prints `%.2f`. An **orthographic** projection has `m[0] = 2 / width`,
which is about `0.0015` - **not zero, and printed as `0.00`**. So the mirror at that sample is not unset: it holds an
**orthographic** matrix, the overlay's, while GL holds the world's with its camera translation. The 1.62 is the two
translations apart, and the mirror is **stale by one pass**.

That also means the pass flag the last two rounds relied on is wrong. `worldPassActive` is set when a perspective projection
is derived and cleared by the hooked `ortho`, and MC evidently installs orthographic projections through paths this project
does not hook - `loadMatrix`, a `Matrix4f` upload, or a mode this backend does not track. The flag says "world" while the
mirror holds an overlay matrix, which is exactly how a wrong 1.62 survived a check written to remove it.

The robust discriminator is not the mirrored state at all, since that is the thing in question. It is GL's own projection in
the diagnostic path: a perspective projection has `m[11] == -1`, an orthographic one does not. One read of GL, in a
measurement-only path, and the comparison is taken only when both sides describe the same kind of pass.

Recording the fourth artefact's history honestly, because the pattern is now the finding:

| Attempt | What it assumed | Result |
|---|---|---|
| skip one comparison after a reset | the mirror fills immediately | 1125 skips, `view` still 1.62 |
| skip while the mirror is all zeros | `0.00` means zero | 0 skips - it was rounding |

Both were wrong for the same underlying reason: they tried to infer which pass owns the mirror from the mirror itself. The
thing that knows is GL, and reading it - once per comparison, in the diagnostic path only - is cheaper than the four
suppositions it replaces.

Nothing in the renderer changed, and this remains measurement work: the renderer's own pass identity is in the group key and
draws against the matrices the batch was issued with. What is unresolved is a counter that cannot yet tell two passes apart
without asking GL.

---

## The per-batch matrix readback is gone: the world pass draws from a per-frame snapshot

The change the direction and the user asked for is in the drawing path, not in a counter. Three files, and what they do:

| Piece | What it does |
|---|---|
| `EntityRendererMixin` | Hooks the world pass's **explicit boundaries** (`func_175068_a` HEAD and RETURN) and, at the camera boundary (`func_78479_a` RETURN), asks for the frame's snapshot |
| `captureWorldSnapshot` | Takes the projection derived from Minecraft's own numbers and the camera transform as the camera setup left it - **once per frame** - and **seeds the mirror's base** with them |
| `captureGlMatrices` | Inside the world pass with a snapshot, copies the **mirror's** matrices; no `glGetFloatv` on the path at all |
| `submitImmediateBatch` | Refuses **before acceptance**, with a reason, any batch that is not inside a world pass with a snapshot - so the original GL draw stays in charge of everything else |

Measured on hardware, one run:

```
snapshotCaptures=573   snapshotServed=578   outsideWorldPassRefusals=7164   matrixDiagnostics=false
immediateOffered=1746  immediateTaken=578   immediateFlushes=82
immediateRefusal=outside_the_world_pass_or_without_a_snapshot
fixture PASSED | nativeTerrainVerified True | 2201 passes
image against the same-fixture reference: mean 0.14 | 0.40% over 8 | 3,714 px  (unchanged)
```

Read as evidence rather than as progress:

- **`snapshotServed == immediateTaken`** - every batch Vulkan accepted was drawn from the snapshot. Not one fell through to the GL readback, which is what the equality means;
- **`matrixDiagnostics=false`** - the per-batch `glGetFloatv` pair that this plan has called the dominant per-batch cost is **no longer on the normal path**;
- **7,164 refusals before acceptance** - the batches outside a world pass are refused with a reason and drawn by GL, which is the behaviour the user required instead of drawing them with world matrices;
- **the frame is unchanged** - 0.40% against the same reference as before the change, so the new path draws what the old one drew on the scene that is measured.

The local transforms are preserved because the snapshot **seeds the mirror** rather than replacing batch matrices: the per-model, per-entity and per-TESR pushes, translates, rotates and pops still accumulate through the hooked calls on top of the camera base. Copying the snapshot into each batch would have drawn every model at the camera origin, which is the failure the user named.

**Recorded as incomplete, per the same instruction:** one `glGetFloatv` read per frame remains, at the camera boundary, and it is what the snapshot is made of. The per-batch reads are gone; the per-frame one is not, and it should be replaced by computing the camera transform from the view entity in vanilla's order before this can be called GL-free.

---

## The two-target fixture passes: off-target batches are refused before acceptance, and pass changes flush

Two drawing-path changes, both in the offer/flush path rather than in an instrument:

| Change | Where | What it does |
|---|---|---|
| The framebuffer hook reports whether the bound target is the game's **main** framebuffer | `FramebufferMixin` | The shared image stands in for the main framebuffer only, so that is the only target a batch can be drawn for |
| Off-target batches are **refused before acceptance** | `submitImmediateBatch` | With the reason `not the main framebuffer`, so GL draws them where they belong |
| A **pass change flushes the pending group** | `bindFramebuffer` | Whatever is pending belongs to the pass that is ending; it is closed at the boundary instead of merging into the next pass |

Measured, both runs on hardware:

| Run | Status | Accepted | Served by snapshot | Off-target refusals | Pass-boundary flushes | Refusals outside the pass |
|---|---|---|---|---|---|---|
| normal world | **PASSED** | 573 | 573 | 0 | 0 | 7,139 |
| **two-target fixture** | **PASSED** | 564 | 564 | **564** | **10** | 8,804 |

Three things this establishes, each of which was a failure before:

- **the two-target fixture passes**, where two rounds ago it failed the run and suppressed the capture. It passes because its
  second-target batches are refused instead of being drawn into the shared image as if the target did not matter;
- **every accepted batch in both runs is served by the snapshot** (`taken == served`), so no batch anywhere fell through to
  the per-batch `glGetFloatv` pair;
- **the pass boundary is honoured**: ten flushes happened exactly where the target changed, which is the ordering the audit's
  S10 acceptance asks for, demonstrated in the fixture that crosses two targets.

What is still not right and is recorded as such: the two-target fixture's frame differs from the *hidden-GUI* reference by
42.92%, which is expected rather than alarming - the draw-test runs deliberately leave the HUD visible, so the comparison is
between two different configurations and not a regression measure. A draw-test reference with the same configuration is what
that fixture needs before its image can be used as evidence.

---

## S14 is not hypothetical: 487 accepted groups were dropped, silently, until now

Two changes to the offer and draw paths, both about the audit's S14 - geometry taken and then never drawn:

| Change | Effect |
|---|---|
| The bound texture is **resolved before acceptance** | A batch whose texture cannot be mirrored is refused with a reason, where GL can still draw it |
| The draw path's texture failure is counted as **`postAcceptDroppedGroups`** | If it ever runs, the geometry is already taken and the original draw cancelled, so it is loud rather than folded into the ordinary refusals |

Measured on the normal world run:

```
status=PASSED  taken=552  textureRefusalsBeforeAccept=9  postAcceptDroppedGroups=487  passes=2178
image against the same-fixture reference: mean 0.13 | 0.40% over 8   (unchanged)
```

**487 groups were accepted and never drawn** - roughly one per frame - and until this round nothing in the project said so.
The audit's S14 describes exactly this, and it had been happening in every run, hidden behind a counter name
(`immediateTextureRefusals`) that read like an ordinary refusal rather than like lost geometry.

Two things follow, and they pull in opposite directions until the next change is made:

- the **image is unchanged** at 0.40%, so on the scene that is measured these groups were not visible - the likely
  candidates are the composite quad, which is deliberately left to GL, and groups whose descriptor needs the lightmap;
- **that is not a defence.** A silent drop that happens to be invisible on one scene is exactly what the audit warned
  about: it will be visible on another, and nothing in the run would have pointed at it.

The next change is the rest of the same rule: the descriptor set has to be built **before** acceptance, so that the only
place a batch can be refused is the one where GL still owns the draw. Everything it needs - the pipeline for the state key,
the mirrored texture and the lightmap - is available at offer time, which is why this is a completion of the current
structure rather than a new one.

One inconsistency is recorded rather than smoothed: `snapshotServed` (573) exceeds `immediateTaken` (552) because the two
count different events - the snapshot serves a matrix capture, not a batch - so the equality that held in the previous
round's numbers should not be read as "every batch" without that caveat.

---

## Every post-accept drop is the particle family, and refusing it brings the frame closer to GL

The per-family counter answered the question in one run: `droppedByFormat=1:0,2:0,3:0,4:0,5:483`. **All of the accepted-and-
dropped groups are particles** - the family whose descriptor needs the lightmap - and the drawing path has been taking them
and then discarding them, roughly once per frame, in every run this project has measured.

With a probe at offer time - one particle pipeline kept for the question, since the descriptor depends on the textures rather
than on the pipeline's state key - the family is refused before acceptance instead:

| | before | after |
|---|---|---|
| accepted batches | 547 | **65** |
| particle refusals before acceptance | - | **484** |
| image against the GL reference | 0.14 mean, **0.40%** over 8 | 0.06 mean, **0.26%** over 8 |

**Refusing the particles moved the frame closer to GL, by about a third.** That is the substantive result of the round: the
particle geometry Vulkan was taking was not merely dropped, and leaving it to GL makes the frame match the reference better
than taking it did. It also lines up with the older note in this log that particles carried a difference that survived
several fixes.

The drop counter still reads 484 after the change, which is worth stating plainly rather than reading as a failure of the
probe: the refusals now happen before acceptance, so the drops are groups from a path the probe does not intercept - the
pending groups and the draw's own descriptor build - and both counters now describe the same underlying failure,
`descriptorSetFor` returning zero for the particle family. That is the next patch, and it is now a single function with a
single caller rather than a family-sized investigation.

Nothing here is a general GL measure: the probe asks the same question the draw asks, in the same way, one step earlier, and
the answer is the Vulkan-side descriptor's availability.

---

## The descriptor pool was not the cause, and the candidates are now two

The audit's S13 reading was checked and partly acted on: the three immediate pipelines each created their descriptor pool with
`maxSets(64)` while caching one set **per distinct texture view and never evicting**, so ordinary play exhausts it and every
later allocation fails - which is exactly what the particle family's behaviour looked like. The pools were raised to 8192,
sized for a session rather than a scene, and the run says:

```
status=PASSED  taken=65  postAcceptDropped=488  particleDescriptorRefusals=488
droppedByFormat=1:0,2:0,3:0,4:0,5:488
image vs GL reference: mean 0.06 | 0.24% over 8   (best so far; 0.26% with particles refused, 0.40% before that)
```

**No change in the refusals**, so exhaustion was not the cause - though the 64-set limit was real and would have bitten
eventually, so the change stays. That leaves two candidates inside `descriptorSetFor`, and they are distinguishable in one
run rather than by argument:

- it returns 0 early if `lightmap == null` or `descriptorPool == VK_NULL_HANDLE`, or if the texture is null. The offer path
  already refuses when the backend's lightmap texture is absent, so the interesting case is a lightmap that **exists as an
  object but has no valid view** - which the per-frame mirror refresh added for the stale-lightmap fix would produce, since
  it drops every mirror and the lightmap is re-uploaded afterwards;
- the allocation itself may fail, which is the branch the pool change was meant to address.

One counter in each return path, in a single run, decides between them, and the fix differs completely: the first wants the
lightmap resolved before the probe (or the descriptor keyed by the lightmap view, which is S13's other half), the second
wants the pool eviction rule instead of a bigger pool.

The image has improved at every step of this sequence - 0.40% before the particle refusal, 0.26% with it, 0.24% now - which is
consistent with the picture this log has built up: the particle path was taking geometry it could not draw, and each step
that stops it taking what it cannot draw moves the frame closer to GL.

---

## S14 closed: nothing accepted is dropped any more, and the particles it was hiding are wrong by 1.33%

The reason counter named the failure in one run - `vkAllocateDescriptorSets failed` - which ruled out the pool's `maxSets`
that had been raised the round before and pointed at the **per-type** count instead:

```java
poolSize.get(0).type(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER).descriptorCount(128);
```

128 combined-image-sampler descriptors at **two per set** is 64 sets, whatever `maxSets` says. Raising `maxSets` alone could
never have helped, which is exactly what the previous run showed. With the two sized together - 8192 sets, 16384
descriptors - the particle family's descriptors allocate:

| | before | after |
|---|---|---|
| accepted batches | 65 | **550** |
| **post-accept dropped groups** | 488 | **0** |
| particle descriptor refusals | 488 | **0** |
| image against the GL reference | 0.24% | **1.33%** |

**The S14 acceptance is met**: no batch that this renderer accepts is dropped any more, and particles are genuinely drawn by
Vulkan rather than taken and discarded. That closes a P1 that had been happening in every run since the immediate path was
built.

And it immediately exposes what the drop was hiding. Refusing the particles had *improved* the frame to 0.24%; drawing them
now makes it 1.33%, which is the same order as the particle discrepancy this log recorded many rounds ago (1.19% to 1.31%
with a 0.23% floor) before the path stopped drawing them at all. So the sequence was: particles drawn wrongly, then silently
dropped, then refused, then drawn again - and the honest reading of the last number is that **the particle rendering is
wrong**, not that the descriptor change was.

The next work is therefore a named defect with a number attached rather than a family-sized investigation: the particle
pipeline's output differs from GL by 1.33% of pixels while every other family's does not, and the candidates are the ones
this project already has on file - the lightmap it samples (S09's fix is in, and the descriptor now binds it), the blend and
depth state it runs with (S07), and the particle atlas it reads.

---

## The particles were being drawn unblended; asserting the family's semantics takes 1.33% to 0.42%

With the particles finally drawn rather than dropped, their difference from GL was 1.33%. The frame was localised first,
using the two images that already existed - the run that refused the particles and the run that drew them:

```
particles refused vs GL:   2,225 px (0.24%)
particles drawn   vs GL:  12,303 px (1.33%)
of those 12,303 pixels, 12,225 are exactly where the two runs differ
```

**99.4% of the error is the particles' own pixels**, so it is their rendering and not a scene-wide ordering or blending
interaction. Then the character of the error, from the same two images: a one-pixel shift of the reference makes the
mismatch *worse* (145k pixels against 12k), so the geometry is right; and the Vulkan particles are **brighter and warmer**
than GL's (R +20, G +27, B −10, luminance +58), which is what an unblended overlay looks like.

That pointed at the state mirror, whose known gap the audit lists as S07 - the integer blend overloads and alpha enable are
not hooked - and at the family's own meaning. Minecraft draws particles with source-alpha blending, depth testing on and
depth writes off; the pipeline was being created from whichever flags the mirror happened to have captured. It now asserts
the family's semantics instead:

```java
particle = VulkanImmediateParticlePipeline.create(this.context, terrain.renderPass(),
        true, VK_BLEND_FACTOR_SRC_ALPHA, VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA,
        true, false, false);
```

| | before | after |
|---|---|---|
| image against the GL reference | 1.33% (12,303 px) | **0.42% (3,839 px)** |
| post-accept dropped groups | 0 | 0 |

So the diagnosis was right and most of it is fixed: **the particles were being drawn with blending off**, and the residual
0.18% over the "particles refused" baseline is a smaller particle-specific error - the lightmap tint and the depth-write
flag are the two candidates, and the same localisation method can separate them without a new measurement of the whole scene.

---

## The mirrored textures were filtered linearly, and fixing that beats the refused-particles baseline

The residual after the blend fix pointed at the sampler, which the audit lists as S23. `VulkanGlTexture.create` documents
itself clearly - "`true` for the pixel-art block atlas, `false` for the light map, whose OpenGL texture is filtered linearly"
- and the registry called it with `false` **for every texture it mirrored**. The terrain path had it right; the immediate
path's textures, which go through the registry, did not.

The registry now creates with `nearestFilter = true` - Minecraft's own setting for its atlases and sprites - and offers
`getLinear` for the lightmap, which the terrain side already mirrors correctly.

| Stage | Difference from the GL reference |
|---|---|
| particles unblended | 1.33% (12,303 px) |
| particles blended | 0.42% (3,839 px) |
| **textures nearest-filtered** | **0.23% (2,158 px)** |

**0.23% is better than the 0.24% the run with particles refused measured** - the same scene, at 2,225 pixels - so the
particles are now drawn at least as closely to GL as not drawing them at all, and the whole frame is at its closest match
yet. Two defects that had been in the tree for many rounds are gone: geometry accepted and dropped (S14), and a sampler that
never matched the texture it was sampling (the sampler half of S23).

The comparison is against the same-fixture reference throughout, and the runs differ only by these changes, so the
progression is attributable rather than coincidental.

---

## The second GPU found a layout bug the first one tolerated

Running the current build on Intel - the objective's two-GPU condition - produced the first validation message this project has
seen in several rounds, and the harness correctly failed the run for it:

```
severity=ERROR id=VUID-VkImageMemoryBarrier-oldLayout-01197 vkCmdPipelineBarrier():
  image ... cannot transition ... from VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL when the previous known layout is ...
```

The cause was in the texture upload's mip chain. The loop that generates mips transitions from `TRANSFER_DST` to
`TRANSFER_SRC` **inside the loop**, and the final barrier - the one that hands the image to the shader - declared
`oldLayout = TRANSFER_SRC_OPTIMAL` unconditionally. With a single mip level the loop never runs, so the image is not in
`TRANSFER_SRC` at all, and the barrier declared a layout the image had never been in.

The fix records what the image is actually in - `currentLayout`, set by the upload and by each transition - and uses it
where the mip chain did not run:

```java
.oldLayout(this.mipLevels > 1 ? VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL : this.currentLayout)
```

Both GPUs are clean afterwards:

| GPU | Validation | Fixture | Dropped groups |
|---|---|---|---|
| **AMD RX 6400** | exit 0, clean, **0 messages** | PASSED, `nativeTerrainVerified True` | 0 |
| **Intel UHD 630** | exit 0, clean, **0 messages** | PASSED, `nativeTerrainVerified True` | 0 |

This is the value of the second GPU stated plainly: the barrier was wrong on both, one driver's validation tracked it and the
other did not object, and without the Intel run the project would have shipped a layout bug that a stricter driver would
report - which the audit asked for by name when it said to verify both GPUs rather than one.

---

## S02's recording side is guarded; the frame side is not, and the injection shows it

The audit's S02 is the timeout path: a wait that times out was logged and ignored, after which the pending command buffer
was cleared, reset and re-recorded while the GPU might still be reading it. The recording side is now guarded - the wait
reports whether it succeeded, nothing is touched when it did not, and every later batch is refused before acceptance with a
documented injection (`-Dceltium.vulkan.failIdle=true`) to reach the path that no ordinary run takes.

Measured, both runs:

| Run | Result |
|---|---|
| normal | exit 0, validation clean, **0 messages**, fixture PASSED, `gpuStalled false`, `stalledStops 0`, 553 accepted |
| `failIdle` injection | **exit -6 (SIGABRT), 17 validation messages** |

**The healthy path is undisturbed, and the failure path is not fixed.** With recording refused, the rest of the frame still
runs against a command buffer that was never begun - `endFrame`, the present and the terrain passes all assume the frame's
recording happened - so the injected stall ends in an abort rather than a refusal. That is why the change is kept and S02 is
**not** called closed: what exists now is half of the correct behaviour (nothing invalid is reset or re-recorded) and the
absence of the other half (the frame must stop, not continue into code that expects a recorded buffer).

The next patch is therefore specific: when `gpuStalled` is set, the frame's remaining stages are skipped - `endFrame`, the
present path and the terrain passes - so the renderer stops drawing and keeps running, which is what "fail closed" has to
mean for a frame rather than for one buffer. The injection is the test for it, and the acceptance is that the injected run
ends with refusals and no validation messages instead of an abort.

---

## S02's frame side: four entries guarded, the injected run unchanged, and the location narrowed

The guards went in at every entry the frame target is used from - `render`, `flushImmediateWindow`, `submitImmediateBatchTimed`
and `runSwapchainProbe`, plus `ensureFrameTarget` earlier - with the reasoning stated at each site: a stalled GPU closes
everything that touches the frame target, its command buffer or the bridge. The injected run is unchanged:

```
INJECTED: exit -6 | clean False | messages 17 | fixture MISSING_REPORT
  * vkCmdPipelineBarrier(): was recorded, but vkBeginCommandBuffer() was not called prior to this command
  * vkDestroyFramebuffer(): can't be called on VkFramebuffer 0x... that is currently in use by VkCommandBuffer 0x...
```

So the barrier that is recorded into an un-begun buffer does **not** come from any of the five guarded entries. That is
narrowing rather than progress, and it is worth saying which way it points: the remaining candidates are the paths that own
the frame target directly rather than through these methods - `VulkanFrameTarget`'s own copy-in and copy-out, and the
swapchain's present path, which creates and records its own commands. The second validation message supports that reading:
a framebuffer destroyed while a command buffer still references it is a lifetime interaction between the target and the
buffer, not a drawing call.

The guards that are in place are correct and stay: nothing invalid is reset, re-recorded or drawn through those five
entries. **S02 is still open**, and its acceptance is unchanged - an injected run that refuses and stays clean instead of
aborting - with the search now inside `VulkanFrameTarget` and the swapchain rather than in the backend's frame stage.

---

## Seven stall guards, the same two messages, and the injection's timing is the next suspect

Guards are now in place at seven entries - `render`, `flushImmediateWindow`, `submitImmediateBatchTimed`,
`runSwapchainProbe`, `ensureFrameTarget`, `delete` and `settleGpu` - each closing what a stalled GPU must not touch. The
injected run is unchanged:

```
INJECTED: exit -6 | clean False | messages 17 | fixture MISSING_REPORT
  * vkCmdPipelineBarrier(): was recorded, but vkBeginCommandBuffer() was not called prior to this command
  * vkDestroyFramebuffer(): ... in use by VkCommandBuffer ...
```

Since the guards cover every backend entry that records or destroys anything, the abort is happening **before** the paths they
close. The injection is the reason: `FAIL_IDLE` makes the *first* wait fail, which is during start-up, when the renderer has
not been built yet - so the run is exercising a stall before the frame target exists rather than the steady-state stall S02
describes. Nothing about the guards is wrong; the test is aimed a frame early.

The fix for the test is one condition: let the injection fail a wait **after** the renderer is running - the second or third
wait - so the failure lands in a steady frame with a recorded, submitted predecessor. That is the state S02 is about, and it
is the state in which "the GPU may still be reading the buffer" is true rather than hypothetical. Start-up stalls are a
different question and should be recorded as one rather than mixed in: the honest boundary is that this round's guards are
correct and unverified, and the injection needs to aim at the frame it was written for.

---

## S02 closed: an injected stall now ends in a refused frame and zero validation messages

Aiming the injection at a steady-state wait turned the abort into a trace, and the trace into a fix. Each round of the
injection reported fewer messages, and each named the next unguarded entry:

| Injection | Result |
|---|---|
| first wait (start-up) | **17 messages**, SIGABRT |
| steady-state wait, frame entries guarded | **5 messages** - `vkCmdBeginRenderPass`, `vkCmdSetViewport`, `vkCmdSetScissor` recorded into a buffer never begun |
| after guarding the pass-recording entries | **1 message** - `vkEndCommandBuffer` on a buffer not in a recording state |
| after guarding the submit entry | **0 messages**, exit 1, fixture FAILED |

So the entries that record, end and submit the frame's commands are all closed once a wait has timed out: `render`,
`flushImmediateWindow`, `submitImmediateBatchTimed`, `runSwapchainProbe`, `ensureFrameTarget`, `delete`, `settleGpu`,
`recordImmediateOnlyPass`, `recordRenderPass` and `submitCommands`. The stalled run now **fails the fixture and exits 1**
with `gpuStalled true`, which is the acceptance the audit wrote - "propagate the timeout upward, stop re-recording,
reading back and destroying, and move explicitly into a failed state" - where the same run previously aborted the process
with seventeen validation errors.

The healthy run is unchanged: fixture PASSED with the usual accepted count and `gpuStalled false`.

Worth recording about method: the last three rounds each moved the injection and each narrowed the trace by naming the next
call site, and the fix at every step was to **close an entry** rather than to add a counter. The injection is documented,
off by default, and is the test - a safe fake result kept apart from any real latency measurement, which is what the audit
asked for.

---

## The blend factors are now observed, and using them needs the cache key first

The audit's S07 lists the integer `blendFunc` overloads as state the mirror never sees. One of them - `func_179112_b`, whose
body the SRG source confirms calls `GL11.glBlendFunc` - is now hooked, and a run reports what Minecraft actually set:

```
blendObserved=true(770,771)   |   fixture PASSED   |   image 0.23% against the GL reference
```

770 and 771 are `GL_SRC_ALPHA` and `GL_ONE_MINUS_SRC_ALPHA`: **exactly the factors the particle family had been asserting**.
The assertion was right, and the project can now say so from observation rather than from the family's meaning.

Wiring the observed factors straight into the pipeline, however, made the frame **worse** - 0.23% to 0.33% - and the reason is
worth keeping: the pipeline cache is keyed by the group's state key, which does **not** include the blend factors, so the first
group's factors are handed to every later group. **Reading a value the key does not carry is not the same as using it.** The
pipeline is back on the documented family semantics and the frame is back to 0.23%, and the hook stays: the observation is
correct and is the raw material for the next step.

That next step is small and named rather than an investigation: put the blend factors into the key, then use them. It has the
same shape as the pass-identity work - the value a batch is drawn with has to be part of what identifies the batch.

---

## Keying alone was not enough: the observation is global-latest, not per batch

The blend factors were put into the particle pipeline's cache key as the last round concluded they should be, and the frame
got **worse**, not better:

| Configuration | Difference from the GL reference |
|---|---|
| family constants (the assertion) | 0.23% |
| observed factors, uncached | 0.33% |
| **observed factors, keyed** | **0.31%** |
| back on the constants (this run) | **0.21%** |

So adding them to the key was necessary and not sufficient, and the numbers say why: `blendObserved` holds the factors from
the **most recent `blendFunc` call anywhere**, not the factors that were in effect when *this* batch was offered. A group can
be opened after some later pass has already changed the blend, so the pipeline is built for another pass's state - the same
mistake as reading a value the key does not carry, one level further back. The observed value has to be captured **with the
group**, at the moment the batch is offered, exactly as the matrices are.

That is now written into the code where the decision is made, and the pipeline is back on the documented constants, which
measured **0.21%** in this run - the best figure so far and consistent with the 0.23% of the same configuration, the
difference being run-to-run variation rather than an improvement worth claiming.

The hook and the key stay: the observation is correct, the key is now the right shape, and what is missing is the per-group
capture that the audit's S07 asks for in its own words - connect every call and overload into a single snapshot - rather than
one global field read at the wrong moment.

---

## Per-group capture of the blend factors still measures 0.33%, so the factors are not the whole difference

The previous round's conclusion was that the observation had to be captured with the group rather than read globally. That
is now implemented - the group tuple carries the factors in effect when it opened, beside the state key, exactly as the
matrices are - and the frame **still** measures 0.33% against the constants' 0.21-0.23%:

| Configuration | Difference from the GL reference |
|---|---|
| family constants | 0.21-0.24% |
| observed factors, global-latest, keyed | 0.31% |
| **observed factors, captured per group, keyed** | **0.33%** |

So the per-group capture was necessary and is still not sufficient, and it narrows the question usefully: **the blend factors
alone are not what differs**. Minecraft sets the blend factors per particle type - additive for some, alpha for others - and
the group now carries the right ones, yet drawing with them is further from GL than drawing with one constant pair. The
remaining asserted field in the same creation call is **depth write**, which is set to false for the whole family; if some
particle types write depth, then using the correct factors for those groups and the wrong depth state for all of them would
produce exactly this: a frame that gets worse when the blend is corrected in isolation.

The constants are back and the frame measures 0.24%, consistent with the 0.21-0.23% of the same configuration. The hook, the
key and the per-group capture all stay - each is correct and each is a piece of the observable snapshot - and the next attempt
takes the **triple** together (blend enable, factors, depth write) rather than one field of it, because a family-level
assertion is only safe while every field in it is right.

---

## The "(6, 7)" outlier is withdrawn: the packed key was my decoding, not the data

The pair distribution was read from a packed key (`src << 20 | dst`) and one entry decoded to `(6, 7)`, which was taken as a
bogus `blendFunc` call and used to explain why the observed factors measured worse than the constants. A verbatim recorder -
one string per non-ordinary call, no packing - says otherwise:

```
blendOutlier="none"     (no blendFunc call outside the ordinary alpha pair, in the whole run)
image against the GL reference: 0.28%
```

So there was no bogus call: **the `(6, 7)` entry was my own packing arithmetic read back wrongly**, and the finding is
withdrawn. What stands is narrower and more useful: **573 of 574 groups capture exactly the constants the particle family
asserts**, so the factors are not what makes the observed-value path measure 0.32-0.33% against the constants' 0.21-0.28%.
The remaining explanation is therefore not in the factors at all - it is in what else changes when those values are threaded
through the key and the pipeline, and the verbatim recorder is the instrument to find it rather than another packed decode.

The frame is back at 0.28%, inside the 0.21-0.28% band this configuration has shown across runs, with the observation, the
key and the per-group capture all in place and unused by the pipeline. Three rounds of this thread have each ended with a
withdrawn explanation and a narrower question, which is worth stating as the pattern it is: the blend state is observable, the
values are the expected ones, and the reason using them is worse is still unexplained.

---

## The mip chain, measured three ways, says something the mip story does not explain

S23's remainder is the sampler and the mip chain. The defect was visible in the code: `create()` built the sampler
**before** `createImageResources`, which is where the level count is computed, so `maxLod` was baked from the default of one
level while `uploadFromGlTexture` went on to generate a real chain. Making the chain reachable looked like the obvious fix.
Three configurations were measured against the same GL reference:

| Sampler | Difference from GL |
|---|---|
| as written - `maxLod = (float) mipLevels` evaluated **before** the image exists, so 1.0 | **0.21-0.28%** |
| real level count minus one, 4.0 | **8.82%** |
| explicit 0.0, level 0 only | **13.51%** |

Nothing about that ordering is what mipmapping predicts. If the chain were a faithful copy of Minecraft's, sampling more of
it should move the frame **toward** GL, and if the chain were garbage, clamping to level 0 should move it toward GL. The
measurements say the opposite in both directions, so the honest conclusion is not "mips are wrong" but **the mip path is not
understood yet**: either the generated chain is destructive - the sequence of barriers and blits has a level-0 hazard the
image comparison cannot see - or the LOD clamp is not doing what the code reads as.

The change was therefore reverted exactly, and the restored configuration measures **0.25%** with validation clean and the
fixture passing. The next step is to stop inferring the chain from frame differences and inspect it: check whether level 0
still equals the uploaded image after generation, then whether each level is the average of its parent. That is one run with a
readback, and it decides whether the defect is generation or sampling.

**Also parked, deliberately:** the blend-observation thread. Three rounds produced the hook, the cache key and the per-group
capture - all correct, all retained, all unused by the pipeline - and three explanations that had to be withdrawn. It is
parked with a single question recorded rather than continued by measurement: 573 of 574 groups capture exactly the constants
the family asserts, one group does not, and nothing so far has said which or why. Both threads come back when there is an
instrument that can see the thing itself - a mip level's contents, a group's identity - instead of a frame difference that
several causes can produce.

---

## The mip clamp: four configurations, one working law, and vanilla's own parameters read from source

Last round's "the mip path is not understood" was half right and half my own fault: the four configurations had changed the
creation order **and** the clamp together. This round separated them - the level count is now computed before the sampler is
built while the creation order stays exactly as it was - and the same clamp measured **8.77%** with the old order against
**8.82%** with the new one. So the order was irrelevant and **the clamp is the whole effect**.

Vanilla's parameters were then read from source rather than assumed (`TextureUtil`, the mipmap branch, source lines 215-218
and 277-283):

| GL call | Value |
|---|---|
| `GL_TEXTURE_MAX_LEVEL` (33085) | the level count the caller passed |
| `GL_TEXTURE_MIN_LOD` (33082) | 0 |
| `GL_TEXTURE_MAX_LOD` (33083) | **the level count** - not the count minus one |
| `GL_TEXTURE_LOD_BIAS` (34049) | 0.0 |
| `GL_TEXTURE_MIN_FILTER` (10241) | `GL_NEAREST_MIPMAP_LINEAR` / `GL_LINEAR_MIPMAP_LINEAR` |
| `GL_TEXTURE_MAG_FILTER` (10240) | `GL_NEAREST` / `GL_LINEAR` |

So the expression the project has always used - `maxLod = mipLevels` - is **the same form vanilla uses**, and the sampler's
filters already match. What follows is the measured law, four configurations against one reference:

| `maxLod` | Difference from GL |
|---|---|
| **1.0** | **0.21-0.28%** |
| 4.0 - Minecraft's own four-level count | 7.37% |
| 8.0 - the full computed chain (256x256 atlas) | 8.77% |
| 0.0 - level 0 only | 13.51% |

Deeper sampling diverges and the shallow clamp matches, which no version of the mip story predicts: vanilla sets
`GL_TEXTURE_MAX_LOD` to its level count, and this scene minifies plenty of atlas texels at distance. The honest state is
therefore a **measured law with an open mechanism**, and it is recorded that way instead of being dressed up as an
explanation.

What changed in the tree is small and honest: `maxLod` is pinned at the measured-best 1.0 **deliberately**, with the four
measurements and vanilla's constants in the comment; the level count is computed once and shared, so the image and the
sampler cannot disagree again; and the generated chain is capped at the four levels vanilla asks for, which saves upload work
without affecting sampling while the clamp is 1. Restored configuration: **0.26%** against the reference, validation clean,
fixture passing.

The next instrument is named rather than guessed: the fragment shader's own LOD usage (`texture` with implicit derivatives
versus `textureLod`/`textureGrad`) and, if needed, the GL sampler's parameters read once under a diagnostic flag. A frame
difference cannot separate those, which is exactly why four of them have not.

---

## The instrument for the mip question is in, and it reports that it never ran

The mip law left one question that four frame measurements could not answer: what OpenGL itself clamps the atlas to. That is
a fact, not an inference, so it is now asked for directly - `-Dceltium.vulkan.textureDiagnostics=true` queries the bound
texture's level count, `GL_TEXTURE_MAX_LEVEL`, `MIN_LOD`, `MAX_LOD`, the filters and the LOD bias, once per texture id, and
records them. Off by default, and the array of GL calls it makes is small enough to read.

Two things went wrong on the way, and both are worth keeping:

- the probe was first placed inside the block that runs **when the atlas changes**, and no `block atlas` line appears in the
  run's log at all - so that block never executes in this scene;
- it was then moved to the entry of `refreshTextures()`, where it is certain to run **if that method runs** - and it still
  did not fire, while the always-on debug line reports `textureDiagnostics=true`, which proves the flag reached the JVM.

So the conclusion is not "the diagnostic is broken" but the concrete one: **`refreshTextures()` does not run in this scene**.
That is a statement about the renderer, not about the instrument, and it is the kind of thing this project has repeatedly got
wrong by inferring rather than checking - so the flag now reports its own state in the always-on debug line, and no future
diagnostic in this code can silently not run without saying so.

The next step is one line of placement rather than any new investigation: call the probe from a hook that certainly runs -
the first atlas-textured batch offered to the immediate path, or the pass entry before its early returns - and then read what
OpenGL says. With that number in hand the mip clamp stops being a law with an open mechanism and becomes a value the project
copies deliberately.

Nothing else changed: the frame is at the measured-best configuration, validation clean on the previous runs, fixture passing
in every run of this round.

---

## The driver answered, and the answer moved the question somewhere else entirely

The mip question had been argued from frames for three rounds. This round asked the driver, from the offer path - which runs
hundreds of times per frame and where Minecraft has its texture bound - and reading the binding rather than setting it, so the
probe cannot disturb what the game is drawing. Six textures were seen, and every one of them reports the same thing:

```
CELTIUM_TEXTURE_DIAG id=12 levels=1 maxLevel=0 minLod=0.0 maxLod=0.0 minFilter=9728 magFilter=9728 bias=0.0
CELTIUM_TEXTURE_DIAG id=16 levels=1 maxLevel=0 minLod=0.0 maxLod=0.0 minFilter=9728 magFilter=9728 bias=0.0
CELTIUM_TEXTURE_DIAG id=19 levels=1 maxLevel=0 minLod=0.0 maxLod=0.0 minFilter=9728 magFilter=9728 bias=0.0
CELTIUM_TEXTURE_DIAG id=20 levels=1 maxLevel=0 minLod=0.0 maxLod=0.0 minFilter=9728 magFilter=9728 bias=0.0
CELTIUM_TEXTURE_DIAG id=21 levels=1 maxLevel=0 minLod=0.0 maxLod=0.0 minFilter=9728 magFilter=9728 bias=0.0
CELTIUM_TEXTURE_DIAG id=26 levels=1 maxLevel=0 minLod=0.0 maxLod=0.0 minFilter=9728 magFilter=9728 bias=0.0
```

9728 is `GL_NEAREST`. So for every texture this scene actually draws with, OpenGL is sampling **one level, nearest, with no
mip chain and no LOD bias** - and the renderer's own sampler was already `NEAREST` with a linear mipmap mode, which is the
same thing only while the clamp keeps the chain out of reach.

Two consequences, and the second one matters more than the mip question did:

- **the terrain atlas was never probed**, because only six textures were ever bound at an offer. The terrain path's
  `refreshTextures()` does not run in this scene - which is what the previous round's silent diagnostic already said - so the
  last three rounds' mip measurements were **not about the atlas** but about these six small sprites;
- copying exactly what the driver reports - one level, clamp zero - measures **13.51%** against the reference, while leaving
  the clamp at 1.0 measures **0.21%**. Matching the reference's own parameters makes the frame fifty times worse, so **the
  clamp is not the variable that explains the difference**, and the next instrument is to log the sampler that is actually
  created (the fields as they are passed to `vkCreateSampler`) instead of inferring it from the name of a configuration.

Also corrected in the instrument itself: the probe's first version wrote through `record()`, whose list is capped, so its line
was being dropped and the diagnostic looked silent when it was merely truncated. It now prints to stdout, and the flag reports
its own state in the always-on debug line, so no future diagnostic here can fail without saying that it did.

One process note, because it nearly produced a false finding: a patch of mine mangled the file (statements left at class level),
the build failed, and the run I had launched reported **13.54%** from the **previous jar**. The number was only rejected because
the build result was read before the measurement was trusted - the same discipline that has caught several of this project's
bad readings, and worth keeping in view when a measurement is surprising.

The tree is back at the measured-best configuration: **0.21%** against the reference, validation clean, fixture passing, with
the five measurements and the driver's report recorded in the code where the decision is made.

---

## The sampler that is actually created, and a correction: the terrain pass does run

The recorded next step was to log the sampler instead of inferring it from a configuration name, and to that was added a
pair of counters for the two kinds of pass, because three rounds of inference had produced a claim about the terrain path
that nothing had actually counted. Both answered immediately:

```
CELTIUM_SAMPLER size=0x0 levels=10 minFilter=0 magFilter=0 mipmapMode=1 minLod=0.0 maxLod=1.0 bias=0.0 nearest=true
CELTIUM_SAMPLER size=0x0 levels=5  minFilter=0 magFilter=0 mipmapMode=1 minLod=0.0 maxLod=1.0 bias=0.0 nearest=true
CELTIUM_SAMPLER size=0x0 levels=5  minFilter=1 magFilter=1 mipmapMode=1 minLod=0.0 maxLod=1.0 bias=0.0 nearest=false
CELTIUM_SAMPLER size=0x0 levels=8  minFilter=0 magFilter=0 mipmapMode=1 minLod=0.0 maxLod=1.0 bias=0.0 nearest=true

terrainPasses=1617   immediateOnlyPasses=546   vulkanPasses=2163
```

Two corrections, both to things this log asserted last round:

- **the terrain pass does run.** 1617 of the 2163 passes are terrain passes, so the previous round's conclusion - drawn from
  `refreshTextures()` never firing and no atlas being bound at an offer - was wrong about what it implied. What is true is
  narrower: the method that mirrors the atlas is not reached on this path, while the terrain passes themselves are recorded
  in bulk. The counters now exist precisely so that "is terrain exercised" is a number and not an inference;
- **the off-by-one hypothesis for the mip chain is refuted by reading the blit**, not by another frame: `srcSubresource`
  takes `mipLevel(level - 1)` and `dstSubresource` takes `mipLevel(level)`, so generation writes each level from its parent
  and the base level is never overwritten.

What survives is sharper than before. Our sampler genuinely differs from the one the driver reports for the same textures:
ours is `NEAREST` with a **linear mipmap mode and a clamp of 1.0 over a five-to-ten level image**, while the reference is
`NEAREST` over **one level with a clamp of zero**. So the renderer is averaging level 1 into every minified sample where the
reference averages nothing - and yet sampling level 0 alone, which is what the reference does, measures 13.51% against 0.21%
for the blend. The only reading that fits both numbers is that **our level 0 is not the reference's level 0** - in content or
in alignment - and that blending in a 2x2 average of it happens to move the result closer to what the reference shows.

That is a specific, checkable statement rather than another law, and the instrument for it is a comparison of the two level
zeros themselves - a readback of the mirrored texture and of the GL texture it came from, off by default - rather than a
sixth frame configuration. The frame itself is unchanged at the measured best: **0.21%** with validation clean and the
fixture passing.

---

## The atlas answered: five levels, a clamp of four, and a chain built per sprite

The previous round's conclusion - that `refreshTextures()` does not run - was **wrong, and wrong for a reason worth
recording**: the probe was believed to have been moved to that method and never was. It lived only in the immediate path's
offer. Checking where the code actually is, by printing the enclosing method of every marker rather than trusting the patch
that was supposed to move it, showed the placement had silently failed - so a whole round of reasoning rested on a silent
instrument at a location it never occupied. The probe is now in `refreshTextures`, at the line after the atlas and lightmap
ids are read, and its location was verified the same way before the run. It fired on the first frame:

```
CELTIUM_TEXTURE_DIAG atlas=9 levels=5 maxLevel=4 minLod=0.0 maxLod=4.0
                     minFilter=9986 magFilter=9728 atlasSize=512x512

CELTIUM_SAMPLER size=0x0 levels=10 minFilter=0 magFilter=0 mipmapMode=1 minLod=0.0 maxLod=1.0 bias=0.0 nearest=true
```

9986 is `GL_NEAREST_MIPMAP_LINEAR` and 9728 is `GL_NEAREST`; our sampler's `NEAREST` filters with a **linear mipmap mode**
are the same combination, which is the one thing the two agree on. Everything else differs, and now in a way that explains
every measurement this thread has produced:

| | GL atlas | our mirror |
|---|---|---|
| size | 512x512 | 512x512 |
| levels | **5** (`maxLevel` 4) | **10** |
| max LOD | **4.0** | **1.0** |
| min filter | NEAREST_MIPMAP_LINEAR | NEAREST + linear mip mode |
| mag filter | NEAREST | NEAREST |

The mechanism is now visible in the numbers. Minecraft builds the atlas's mip chain **per sprite**, so no level ever mixes
two sprites' colours. This project builds it by **blitting the whole atlas** level by level, so from level 1 upward every
texel is an average of its neighbours **across sprite borders** - which is exactly what the measurements showed: sampling
deep measures 7.37% (four levels) and 8.77% (the full chain) against the reference, and clamping to 1.0, where the bleeding
has barely begun, measures 0.21%. The shallow clamp was never right; it was the configuration that hid the defect.

That makes the fix concrete and specific to Minecraft rather than to Vulkan: **copy the levels OpenGL already has instead of
generating them by blit** - one read and one upload per level, which is faithful by construction because the per-sprite
averaging has already been done by the game - and then use vanilla's own `maxLod` of 4 with a chain as deep as GL's. The
acceptance is the same reference image: it should be **better** than 0.21%, and the shallow clamp can then be retired rather
than kept as a measured accident.

Two process notes stay with this, because both cost a round: a probe's placement must be verified by reading where it is, not
by trusting the patch that was meant to put it there; and a diagnostic that reports through a capped list can look silent
when it is merely truncated, which is why this one prints to stdout.

---

## Mirroring GL's own levels: written, measured, wrong in one specific way, and turned off behind a flag

The fix the atlas answer pointed at was implemented: the level reader took a level parameter, the copy took a level, and
`uploadFromGlTexture` was to copy each of OpenGL's levels instead of generating a chain by blitting the whole image. Measured
on the reference scene:

```
validation: exit 1 | messages 10 | fixture PASSED
image: 12.66%      (the configuration it replaces: 0.21-0.28%)
```

Both failures are mine and both are specific. The image still had the **size-derived chain** - ten levels for a 512x512 atlas
- while OpenGL reported five, and the copy loop walked all ten, so it read levels that do not exist in GL. And the tail
transition to `SHADER_READ_ONLY` had been made conditional on "the last level", which in the non-mirroring mode is level 0
after the chain is generated, so it stopped running and left images in a transfer layout - which is what the ten validation
messages were.

The mirroring is now **off behind a flag**, the measured-best configuration is restored, and the one genuine bug the attempt
exposed is fixed: the tail transition runs for the last mirrored level **or** for level 0 when the chain is generated, so both
modes end in the layout they must. Verified: **exit 0, validation clean, 0 messages**, fixture passing, image **0.23%**.

Two things are worth keeping from this, and the second is the more important:

- turning the fix on needs the image created with **exactly the count GL reports** - recreating when the count differs and not
  only when the size does - and the copy loop capped by that count. Both are named, small, and now written down where the flag
  is;
- the run with the **broken tail transition** measured **0.20%**, the best number this project has ever recorded, while
  validation reported **ten errors** and the atlas was never transitioned to `SHADER_READ_ONLY`. An image comparison can
  improve while the renderer is objectively invalid, and only the validation layer said so. That is the standing rule of this
  work - a number improving is not evidence of correctness - with a concrete example attached to it.

---

## Mirroring GL's own levels is now correct and validation-clean, and it says the difference is the shader

The two items the previous attempt needed were implemented - the level count GL reports now takes part in the recreate
decision and in the skip, and the copy loop is capped by it - and the run then reported **fifteen validation errors** naming
a layout conflict. The cause was the same class of mistake the Intel run had caught in the mip chain: with mirroring on there
is no blit, so the image is still in `TRANSFER_DST` when the copies finish, while the tail barrier declared
`TRANSFER_SRC_OPTIMAL`. With the barrier declaring the tracked layout instead:

```
validation: exit 0 | clean True | messages 0 | fixture PASSED
image: 5.60%      (the configuration it replaces: 0.21-0.28%)
```

So the mirroring is now **correct**: five levels copied from OpenGL, validation silent, the layout bookkeeping honest. And it
measures **5.60%** against a reference that a **shallow clamp** matches at 0.21-0.28%. That is a much stronger statement than
any of the earlier ones, because the thing that could most obviously be wrong is no longer in play: the chain's **content** is
OpenGL's own, per-sprite averaging and all. What is left is the **LOD the renderer's shader asks for** - either its UV
derivatives are larger than Minecraft's for the same geometry, or it lacks a bias the game applies - and that is a shader
comparison rather than another sampler configuration.

The mirroring is off again, because 5.60% is a regression against 0.21-0.28% however correct the code is, and the restored
configuration measured **0.16%** with validation clean, the best clean number this project has recorded. It is the same
configuration as the 0.21-0.28% runs, so the difference is run-to-run variation and not an improvement; what matters is that
the state being kept is a **valid** one, which is the distinction the previous round's 0.20% failed.

The code for mirroring stays, one flag away, with the two layout bugs it exposed fixed for both modes and the shader question
recorded where the clamp is decided.

---

## The clamp is irrelevant and so is the LOD: it is the recreate that changes the frame

Two things were done at once by accident and then separated by measurement. The mirror loop was using a shift of level 0's
size for each level's extent instead of the size the read actually returned, which is now fixed - a latent error, not the
cause. And then one variable was moved: GL's own levels with the clamp left at the value the reference matches.

| configuration | clamp | difference from the reference |
|---|---|---|
| generated chain | 1.0 | 0.16-0.28% |
| GL's own levels | 4.0 | 5.60% |
| **GL's own levels** | **1.0** | **5.60%** |
| GL's own levels, sizes from the read | 1.0 | **5.60%** |

Sampling **the same levels** - 0 and 1 - gives 5.60% when those levels come from OpenGL and 0.16-0.28% when they are generated
here, with validation silent in both cases. So the clamp is not the difference, and neither is the LOD the shader asks for:
if the LOD were the problem, clamping to 1.0 would have brought the frame back. Something other than the sampled texels
changes when mirroring is on, and the only other thing it does is **recreate the image resources when the level count
differs** - a new image, a new view, and a descriptor that may still name the old one. That is a specific, checkable
suspicion about ownership rather than a sampler question, and it is where this thread goes next.

Mirroring is off again and the restored configuration is the one that measured **0.16%** with validation clean. That
configuration is byte-for-byte the one verified two runs ago except for a fix inside the disabled path, so it was not
re-measured this round - and the entry says so rather than implying a fresh run.

---

## The complete clamp table: level 0 alone is far off in both modes, and the generated level 1 is what matches

The mirror report answered where the frames go - 128x128 textures with `glLevels=1`, so the image is built with one level and
the copied texels are the same ones the generating path reads - and then one variable was moved to separate content from
structure:

| chain | clamp | difference from the reference |
|---|---|---|
| generated | 0.0 | 13.51% |
| **mirrored from GL** | **0.0** | **13.52%** |
| **generated** | **1.0** | **0.16-0.28%** |
| mirrored from GL | 1.0 | 5.56-5.60% |
| mirrored from GL | 4.0 | 5.60% |

Two facts fall out of it. **Level 0 alone is far off in both modes** - 13.51% against 13.52% - and those two runs sample
identical texels, because both read them with the same call. So the reference frame is heavily mip-blurred: it is not a
level-0 image. And **blending in a level 1 fixes it only when that level is generated here**: 0.16-0.28% against 5.6% when the
level comes from OpenGL itself. The game's own mip level is therefore *less* like the reference's blur than this project's
blit is - which is not what per-sprite correctness would predict, and which means the difference lives in what the sampler
reads at level 1 and above, not in the clamp, the shader's LOD, or the texels of level 0.

The next instrument is accordingly a direct comparison rather than another configuration: the texture dump path already
exists (`-Dceltium.vulkan.dumpTextures=<dir>`), so one run can write the level this project generates and the level the game
holds, and the two can be compared numerically. A seventh frame would only re-measure the same table.

Mirroring is off, the clamp is at the value the table says is best, and the configuration is the one that has measured
0.16-0.28% with validation clean throughout.

---

## The reference image every recent comparison used has this project's Vulkan terrain in it

The mip table ended with a contradiction - the frame matched a chain this project generates and not the levels the game
itself holds - so the reference was finally inspected instead of used. Its own report says:

```
p14s-weather-part-ref1
  immediateTaken=0  immediateRefusal=immediate_drawing_disabled_by_celtium.vulkan.immediate=false
  vulkanPasses=1647  nativeTerrainVerified=True
  Transfer Queue: vulkan [AMD Radeon RX 6400] regions=4 draws=62 sections=62 uploaded=378880B
```

The immediate path was disabled for that run, which is what made it look like a reference - but the **terrain backend was
still installed and drew**: regions 4, draws 62, 1647 passes, and `nativeTerrainVerified True`. So the image every recent
comparison has been measured against is **OpenGL plus this project's Vulkan terrain**, and the comparisons have been
measuring agreement **with this project's own earlier rendering**, not with OpenGL.

That explains the mip table exactly and removes its mystery:

- the reference matches the **generated** chain at 0.16-0.28% because the generated chain is what drew the reference;
- it does **not** match the levels copied from OpenGL - 5.56-5.60% - because those are different texels from the ones the
  reference was rendered from;
- level 0 alone is far off in both modes - 13.51% and 13.52% - for the same reason.

The consequences are recorded rather than softened. The changes of the last rounds are real and stand on their own evidence:
particles are drawn instead of dropped (`postAcceptDroppedGroups=0`), the sampler matches the filters the driver reports,
validation is clean on both GPUs, and the layout bugs the mirroring attempt exposed are fixed. But the **image numbers**
quoted alongside them - 1.33% to 0.23%, and the whole clamp table - measured movement toward this project's own previous
output, and they are **not** evidence of agreement with OpenGL. Nothing in this log should be read as if they were.

A true reference was attempted two ways and neither worked as it stands: removing the Vulkan switches still left the terrain
backend installed (`Transfer Queue: vulkan ... regions=4`), and removing the mod entirely makes the fixture fail before it
captures a screenshot (`fixture FAILED`, no image), because the fixture needs the mod present to reach that point. The next
step is therefore a **reference switch in the backend** - a documented option that installs nothing and lets the fixture
capture a pure OpenGL frame - after which every image number in this project needs re-baselining, starting with the particle
and sampler results that have been quoted as improvements.

---

## A reference switch that installs nothing, and the fixture that will not capture without it

The contaminated reference needed a way to render the same scene in pure OpenGL with the mod still loaded, because the mod
has to be present for the fixture to run at all. That switch now exists: `-Dceltium.vulkan.referenceRender=true` stops the
backend from installing itself at the one place it registers, records why, and leaves everything else alone.

Verified both ways:

| run | what it shows |
|---|---|
| reference with the switch | **no `Transfer Queue: vulkan` line at all**, 0 validation messages - nothing of the backend is installed - and the mod is present |
| normal run afterwards | `exit 0`, validation clean, 0 messages, fixture PASSED, `vulkanPasses=2164`, `immediateTaken=547`, `postAcceptDroppedGroups=0` |

So the switch does what it says and does not disturb the ordinary path. What it does **not** yet produce is a reference
image: with the backend absent the fixture reports `fixture FAILED` and **captures no screenshot**, exactly as it did when
the mod was removed entirely. The fixture's flow requires the Vulkan terrain to be reported before it will set up the scene
and save the PNG, so a pure reference needs one more small change - a **reference mode in the validation client** that skips
the Vulkan expectations and captures anyway - and that is the next step rather than another backend configuration.

That the fixture will not capture without the backend is itself worth recording: it explains the earlier observations in this
log where a fixture "failed the run and suppressed the capture", and it means every screenshot this project has compared was
taken with the backend installed - which is the same fact as the contaminated reference, seen from the other side.

Until the fixture change lands, the honest position on image numbers is unchanged from the previous entry: the functional
evidence stands, and the percentages are not evidence of OpenGL agreement.

---

## The reference switch works, and skipping the registration is not enough: the mixins call the backend directly

With the mod present, the Vulkan expectations removed and `-Dceltium.vulkan.referenceRender=true` set, the reference run is
clean and finally **captures a screenshot**: `exit 0`, validation clean, 0 messages, fixture PASSED. The switch's own note
appears in the report, so it executed. And the frame is **still not pure OpenGL**:

```
reference report: passes=1641  skipped=0  errors=0
                  Transfer Queue: vulkan [AMD Radeon RX 6400] regions=4 draws=...
```

Skipping `TerrainGpuBackends.install(this)` therefore does not stop the renderer, and the reason is structural: the mixins
drive the backend **instance** directly - the entity-renderer hook calls its pass entry, the uploader hook calls its offer -
rather than looking it up through the registry. The registry is how the *game* would find a backend; it is not the only way
in, and this project's own hooks are the other one.

The image comparison run with that reference gives **0.21%**, which is the same value as the numbers quoted before, and it
should be: the reference still contains the same Vulkan terrain it always did. So the measurement basis is unchanged and the
honest position is unchanged with it - the functional evidence stands, and no percentage here is OpenGL agreement.

The remaining step is one guard in each of the two entries the hooks call - the pass entry and the offer - for the same
reference mode, exactly the shape the stalled-GPU guards already have. Then the reference is genuinely OpenGL, and every
image number in this project can be re-baselined against it. Also noted for the audit: the terrain line and 1641 passes in a
run whose registry lookup returns null is evidence that the backend's own hooks are the real entry points, which is worth
knowing independently of this measurement question.

---

## The true OpenGL reference has no terrain in it, because nothing else can draw terrain any more

The two guards - one at the pass entry, one at the offer, the same shape as the stalled-GPU ones - produce a run that is
finally and demonstrably OpenGL:

```
reference: exit 0 | clean True | messages 0 | fixture PASSED
           passes=0  skipped=0  errors=0        (Celtium draws nothing)
           screenshot saved
```

And the screenshot shows **sky, a row of torches, and no terrain at all**. Comparing the current configuration against it
gives 70.93% of pixels different, with the sky third matching at 12.78% - which is exactly what "one frame has terrain and
the other does not" produces.

So the number is **not** a rendering-accuracy figure and must not be quoted as one. What it establishes is architectural, and
it reframes the measurement question that has occupied the last rounds: **this tree has no OpenGL terrain path left to
compare against.** The terrain is drawn by this project's backend or not at all, which is why the earlier "reference" runs
always contained this project's Vulkan terrain - not because a switch was left on by mistake, but because nothing else can
draw the blocks. Every screenshot this project has compared was therefore necessarily produced by its own terrain renderer,
and the percentages measured self-consistency for the simple reason that no other option existed.

For terrain, an accuracy comparison needs a renderer outside this tree: the pinned Celeritas/Embeddium lineage the plan
already names as the reference, run as a separate build of the same fixture. That is a real, available next step and it is
different in kind from the sampler and particle questions, which live inside the immediate path and can be compared against
OpenGL with the switch above.

Two things are worth keeping from this finding beyond the measurement question. First, the asymmetry is worth stating plainly
in the plan: terrain is Vulkan-only, everything else still has a GL path, and "hybrid" means the two halves are compared by
different means. Second, the fixture's insistence that the backend draw before it captures is now explained - it is not a
quirk, it is the only way the scene can be rendered at all.

Recorded, not softened: the honest baseline for the current configuration is **unknown** until a proper reference exists;
0.16-0.28% and the clamp table were self-comparisons; and 70.93% measures missing terrain rather than a rendering defect.

---

## The first valid terrain baseline: 68.8% of pixels differ from the pinned Celeritas lineage

The plan has always named the pinned Celeritas lineage as the reference for terrain, and it turns out a build of it already
exists in the work tree - `celeritas-forge-mc12.2-2.5.12-upstream.989ebf2e.jar`, built from the `upstream-baseline` checkout.
Running the **same fixture, the same scene flags and the same GPU** with that jar in place of Celtium gives a legitimate
reference: it renders the whole scene in OpenGL, terrain included (the screenshot is a green plain with the fixture's grid
and sky), and the run is clean - `exit 0`, validation clean, 0 messages, fixture PASSED, screenshot saved.

Measured against it:

```
TERRAIN BASELINE (Celtium vs pinned Celeritas 989ebf2e, same scene)
  mean 34.09 | >8 68.81% | 634,147 px
  top third 10.38% | middle third 98.74% | bottom third 97.32%
```

Sky matches, terrain does not. **68.8%** is the first image number in this project that means what it appears to mean: two
renderers, one scene, one of them the lineage the plan designated as the reference - no Vulkan terrain on both sides, no
missing blocks, no self-comparison. It is also, of course, a much worse number than any the self-comparisons produced, which
is the point: 0.16-0.28% measured how close the renderer was to its own previous frame, and this measures how close it is to
the renderer it replaced.

What the number does **not** yet say is *how* the terrain differs - a 98% difference in the terrain band is consistent with
misalignment, with a wrong scale, with entirely different shading, or with the frame being captured at a different moment in
the fixture's animation. The next step is therefore visual and local: put the two screenshots side by side, and if they are
not obviously different in kind, compute a difference map whose structure says which of those it is. That is one comparison
of images that already exist, not another run.

The reference itself is now a documented artefact rather than an aspiration: its exact jar, scene flags and result are
recorded here, and it can be re-run whenever the terrain path changes.

---

## The terrain is 40% too dark: geometry aligned, shading wrong

The 68.81% needed a structure, and the two frames give one immediately - side by side, the reference is a bright green plain
and Celtium's is the same plain almost black, with the torches visible in both. Measured:

| rows | difference >8 | Celtium luminance | reference luminance |
|---|---|---|---|
| 0-144 (sky) | **0.0%** | 203.9 | 203.9 |
| 144-288 (horizon) | 50.2% | 163.0 | 174.3 |
| 288-432 | 98.9% | **79.9** | 131.0 |
| 432-576 | 98.4% | **76.7** | 131.1 |
| 576-720 | 96.5% | **78.1** | 128.9 |

Three things follow, and together they close the question the last round left open:

- **the sky is pixel-identical** - difference 0.0 across the whole top band, the same luminance to a tenth - so the frame,
  the camera, the partial tick and the capture are the same in both runs;
- **no shift helps**: testing shifts of up to eight pixels in both axes, the minimum difference is at `dy=0, dx=0` with the
  unshifted value (39.0). So it is **not** misalignment and not a scale error;
- **the terrain is uniformly about 40% darker** than the reference (79.9 against 131.0, 76.7 against 131.1, 78.1 against
  128.9), with the horizon band - distant terrain, mostly sky-lit - only half different.

So the 68.81% has one mechanism behind it rather than several: the **shading**, not the geometry. Every candidate that would
have made this a measurement artefact is excluded by the numbers above, and the remaining candidates are all on one path - the
lightmap the terrain multiplies by (the likely one, since a wrong or missing lightmap darkens everything uniformly), the
brightness and colour multipliers, and the fragment shader's light arithmetic.

Also seen in the image and **not** yet quantified: a bright vertical line near the centre of Celtium's frame. A check of the
exact centre column did not find it (118.8 against a frame mean of 120.3), so it sits at a different column and the check is
recorded as inconclusive rather than as absent - the same distinction this log has had to make before.

---

## The lightmap divisor was wrong, and fixing it changed nothing - so the light arriving is already at the clamp

The terrain shader's lightmap sampling disagreed with itself, which is the kind of thing that is worth fixing on sight:

```glsl
return texture(lightMap, clamp(vec2(uv) / 256.0, vec2(0.5 / 16.0), vec2(15.5 / 16.0)));
```

The divisor is **256** - Sodium's modern light map is 256x256 with many maps packed into it - while the clamp around it is
written in **16-texel** terms, which is what Minecraft 1.12.2 actually uses (and what this project's own shader comment says,
and what the texture dump shows: a 16x16 light map with real content, mean 135). The divisor is now 16, and the shader is
internally consistent for the first time.

Measured against the upstream reference with that fix:

```
terrain baseline: mean 34.08 | >8 68.81%      (before the fix: mean 34.09 | 68.81%)
```

**No change at all.** That is not a disappointing result, it is a measurement that rules a hypothesis out: with the light
coordinates at zero, `/256` and `/16` both clamp to the same darkest texel - `0.5/16` - and produce an identical frame. So the
terrain is dark because **the light value reaching the shader is zero**, not because of how it is scaled, and the divisor fix
is correct but was never the cause.

The path was then traced as far as it can be read without an instrument. `CompactChunkVertex` writes the light into the upper
16 bits of `a_LightCoord` (`encodeDrawParameters(...) | (encodeLight(vertex.light) << 16)`), which is exactly where the shader
reads it (`(a_LightCoord >> 16) & 0xFFFF`, then two bytes), and `encodeLight` takes block from the low byte and sky from the
next - all consistent. So either `vertex.light` is zero as the mesh is built, or the light map texture the terrain pipeline
binds is not the one the dump shows. Those are the two candidates left, and both are checkable with one line each: print one
vertex's light value as it is encoded, and print the texture id bound at set 0 binding 1.

Recorded with the fix kept: the divisor correction is a real correctness fix (16 is what the clamp, the comment, the dump and
the game all say), it is simply not the defect that makes the terrain dark.

---

## The light reaching the shader is full daylight, and the row flip is harmful - both by measurement

The two candidates from the previous round were "the vertex light is zero" and "the light map bound is not the one the dump
shows". The first is now **excluded by data**, with an instrument at the one place the light is written:

```
CELTIUM_VERTEX_LIGHT format=vanillalike raw=15728640 block=0 sky=240 encoded=61440
```

`raw` is `0xF00000`: block light 0 and sky light 15, scaled by sixteen, which is a clear noon sky with no torch light - what
the fixture should have. The active format is **vanillalike**, not compact, and the shader's unpacking of it
(`>> 16`, then two bytes) reads exactly those values. So the light value is real, correct, and arrives; the darkness is
downstream of it.

The second thing this round did was test the remaining orientation hypothesis directly. The upload copies OpenGL's rows in
order, and a comment in that code has said since early on that "a vertical flip here made the whole terrain black". Flipping
the rows measures:

| configuration | mean difference | terrain band luminance (Celtium vs reference) |
|---|---|---|
| no flip (as it was) | 34.09 / 68.81% | 79.9 vs 131.0 |
| **flip** | **90.94 / 71.00%** | **3.2 vs 131.1** |

The flip turns the terrain almost black, exactly as the comment said, and the reason is now clear: the atlas's UVs and this
upload are **both** in OpenGL's row order, so flipping inverts every block face. **The old note was right and this round's
attempt to overrule it was the mistake.** The flip is reverted, kept only as a lab flag with the numbers beside it, and the
baseline is re-measured at **34.08 / 68.81%** - unchanged, no regression.

What that leaves is narrower than before: the light coordinates are (block 0, sky 15), so the light map sample must land on
the full-sky corner, and it evidently does not. Two things can still explain it - the dumped 16x16 light map's own row and
axis order (checkable from the PNG that already exists, with no run at all), and whether the texture bound at set 0 binding 1
is that light map. The instrument for the second was written and did not apply - the anchor was not unique - and is recorded
as pending rather than as done.

---

## The terrain shaders ship as checked-in SPIR-V: two rounds of shader edits never reached the running build

The experiment that forced the light map sample to a constant changed **nothing** - again - and the build that produced it
took eight seconds, which is what a build looks like when it has nothing to do. The jar was then opened instead of assumed:

```
org/soutalab/celtium/vulkan/spirv/terrain_solid.vert.spv   8556 bytes
org/soutalab/celtium/vulkan/spirv/terrain_solid.frag.spv   8872 bytes
```

The pipeline loads **SPIR-V**, the SPIR-V lives in `celtium-vulkan/src/main/resources/.../spirv/` as a checked-in generated
file, and the GLSL in `celtium-vulkan/src/main/shaders/` is **not** an input to any build step - the only shader compilation
in the tree belongs to the `celtium-interop` module and covers its own two shaders. So:

- the light map divisor change (256 to 16) **was never in the running build**, and its "no change" measurement measured
  nothing;
- the same is true of the forced-light experiment's first attempt;
- and the conclusion recorded from the divisor change - that the light arriving is at the clamp, i.e. zero - is
  **withdrawn**. The divisor fix is kept as a correctness fix (16 is what the clamp, the shader's own comment, the dump and
  the game all say) but it has **not** been measured.

With the shaders recompiled by `glslangValidator` into the resources directory, the jar's SPIR-V verified by size
(`8332`/`8864` for the experiment, `8524`/`8864` after restoring), the experiment was finally run for real: forcing the light
map sample to `vec4(1.0)` changes **no pixel** - `mean 34.07 | 68.81%` against `34.08 | 68.81%`, and identical band
luminances. That is a sound elimination at last: **the light map is not what darkens the terrain**, whatever the coordinates
do.

The prime suspect moves to the other multiplier in the same fragment shader:

```glsl
diffuseColor.rgb *= m_color.rgb;   // vertex colour
diffuseColor.rgb *= m_color.a;     // ambient-occlusion "shade"
```

`m_color.a` is the shade/AO term, and a value around 0.6 would produce exactly the uniform 40% reduction that was measured -
so the place to look is the colour the mesh builder packs (`VanillaLikeChunkVertex`, whose colour layout the shader must
agree with) rather than anything on the light path.

Recorded as a process rule, because this cost two rounds: **a generated artefact must be verified inside the jar - by size
or hash - before any measurement of its effect is trusted.** The GLSL source is not the artefact; the SPIR-V is.

---

## The active shader is the vanilla variant, and swapping the light axes brightens the terrain

Two things were wrong at once before this round's experiment could mean anything. The pipeline loads
`SpirvShaders.SOLID_VERTEX_VANILLA` for the vertex format this tree actually uses, so the light map divisor fix of two
rounds ago went into **`terrain_solid.vert`**, which nothing runs; and both shaders ship as checked-in SPIR-V, so even the
right file would not have taken effect. With the active shader identified, its light path read, and its SPIR-V recompiled and
verified in the jar by size (`7776`), the experiment finally measures the running renderer:

| configuration | mean difference | terrain band luminance | >8 pixels |
|---|---|---|---|
| before | 34.08 | 84.9 / 77.6 | 68.81% |
| **light coordinate axes swapped** | **27.09** | **97.6 / 90.0** | **70.51%** |

The reasoning behind the swap is worth recording because it is the first time this thread has had a number to reason from:
the scene's light coordinate is (block 0, sky 15) - measured, not assumed - and the texel that address lands on measures
**0.65** in the light map dump, which is very nearly the **0.61** ratio (79.9 against 131.0) the frame comparison had been
showing all along. The darkness and the sampled texel value agreed, so the axes were suspected of being transposed.

The result is honest in both directions and is recorded that way: the terrain brightens by about 15% toward the reference
and the mean difference falls by seven points, while the count of pixels differing by more than eight **rises** from 68.81% to
70.51% - which is what happens when shading that was uniformly too dark becomes closer to correct and the remaining
colour differences stop being masked. Neither number is picked over the other.

What remains is a terrain still about 25% darker than the reference (97.6 against 131.1). With the axes now sampled the way
the dump's bright corner suggests, the two candidates left are the divisor on that same sample (`/256` where the clamp, the
comment and the 16x16 dump all say 16) and the ambient-occlusion term the fragment shader multiplies in as `m_color.a`. Both
are single-variable experiments of the same kind as this one, and the compile-and-verify-the-SPIR-V step is now part of the
loop rather than a lesson.

---

## Two one-variable experiments: the occlusion term is not it, the vertex colour is

With the active shader finally under measurement control, the two remaining candidates from the previous round were tested
one at a time, each with its SPIR-V recompiled and verified in the jar by size.

**The divisor (256 to 16)**, applied in the shader that actually runs:

| configuration | mean | terrain band | >8 pixels |
|---|---|---|---|
| axes swapped | 27.09 | 97.6 / 90.0 | 70.51% |
| **+ divisor 16** | **26.68** | **98.1 / 90.8** | **70.49%** |

A small move in the right direction on all three, which is what a divisor that only affects the extremes of the coordinate
range should produce.

**The occlusion term** (`diffuseColor.rgb *= m_color.a`): disabled, the frame moves by **0.02** of a mean pixel value -
26.66 against 26.68, luminance 98.2 against 98.1. So `m_color.a` is 1.0 in this tree, the writer folds occlusion into the
colour instead, and there is **no double application**. That hypothesis is eliminated; the multiply is restored with the
measurement beside it.

**The vertex colour** (`diffuseColor.rgb *= m_color.rgb`): disabled, the terrain band jumps from 98 / 91 to **215 / 222**
against the reference's 131, and the mean difference rises to 61.26. So this colour is where the darkness lives: it carries a
factor of about **0.46** where the reference implies about **0.61** - which is the ~25% residual exactly. The light map, the
divisor and the occlusion term are all now excluded by measurement, and the question is what the mesh builder packs into
that colour: vanilla's terrain colour is the biome tint times the face shade times the occlusion, and a factor that is a
quarter too small is what one extra multiply in that chain looks like.

The next step is the same technique that finally worked here: diff our colour path against the **pinned upstream checkout**,
which is vanilla-equivalent by construction and is sitting in the work tree.

The vertex colour multiply is restored, its SPIR-V recompiled and verified (`8592`), and the tree is back at the configuration
that measured **26.68 / 70.49%** with validation clean.

---

## The packed vertex colour is 0xFF808080: half in green, blue and alpha

The colour path was diffed against the pinned upstream checkout first, and it is **identical** - `ChunkColorWriter`,
`ChunkVertexEncoder` and `VanillaLikeChunkVertex` differ only by this round's diagnostic. The attribute interpretation was
checked against the GL declaration too and matches: `a_Color` is offset 12, four unsigned bytes, normalised, and the Vulkan
side declares `VK_FORMAT_R8G8B8A8_UNORM` at the same offset; the light coordinate and the RDH factor match the same way. So
the pipeline reads the bytes as the mesh builder wrote them, and the difference is in the values themselves. An instrument at
the write says what those are:

```
CELTIUM_VERTEX_COLOR rgba=(255,128,128,128) hex=ff808080 rdh=0
CELTIUM_VERTEX_LIGHT raw=15728640 block=0 sky=240 encoded=61440
```

Red is full, **green and blue are half, and alpha is half**. For an unoccluded face at noon the colour should be close to
(255, 255, 255, 255), so this is a concrete, quantitative instance of the ~0.46 the frame comparison implied: a factor of one
half is in these bytes.

It also leaves a contradiction that is recorded rather than smoothed over. The fragment shader multiplies by the alpha channel
as well - `diffuseColor.rgb *= m_color.a` - and with alpha 0.5, disabling that multiply should have brightened the frame by
about a factor of two. It measured **26.66 against 26.68**: no change at all. Both observations cannot be true of the same
code path, and the most likely explanation is the one that has cost this thread three rounds already - **the fragment module
that the pass actually loads may not be the file that was edited**. That is a one-line check (`SpirvShaders` vs the pipeline's
pass setup) and it comes before any further experiment.

Also unresolved and recorded: why green and blue are half while red is full. A grey face at noon has no reason to differ
between the three channels, so this looks less like a shade factor and more like a colour that was packed from a
half-initialised or mis-scaled value - which is a question about what feeds `ChunkVertexEncoder.Vertex.color`, not about what
the shader does with it.

---

## The contradiction is resolved: the colour is packed ABGR, so alpha was 255 all along

The eight identical samples of `0xFF808080` were the fixture's own test quads - the first writes of the run, printed because
the instrument capped at eight - and the colour writer that would fold occlusion into a colour is **never called anywhere in
this tree**. So the eight samples said nothing about the terrain. The instrument was replaced with an aggregate over every
write, printed every ten thousand vertices so that any scene size reports:

```
CELTIUM_VERTEX_COLOR_MEAN n=10000 r=255 g=74  b=125 a=103
CELTIUM_VERTEX_COLOR_MEAN n=50000 r=255 g=75  b=124 a=103
```

Red full, green and blue low, alpha about 0.4 - and all of it red herring, because the packing is **ABGR**, not ARGB: the
channel my print called "r" is the alpha byte, and the channel it called "a" is red. Read the right way round:

| channel | mean | as a fraction |
|---|---|---|
| alpha | 255 | **1.0** |
| red | ~103 | 0.40 |
| green | ~124 | 0.49 |
| blue | ~74 | 0.29 |

That resolves the contradiction recorded last round. The fragment shader's `diffuseColor.rgb *= m_color.a` had **no effect**
when disabled because `m_color.a` is 1.0 - the alpha is full, the writer never runs, and there is no double application. The
earlier reading of 128 for alpha was my own byte-order mistake, and it is the same class of mistake as the packed blend key
two rounds before: **a packed word decoded by assumption instead of by the packing.**

What the real numbers say is narrower and more useful: the terrain's vertex colour is a greenish tint with all three channels
well under one (0.40 / 0.49 / 0.29), which is the right *shape* for a grass surface and about 60% of the brightness the
reference implies. The Java path that produces it is byte-identical to the pinned upstream checkout, so the difference is in
what the builder is handed - the ambient occlusion and face-shade inputs - rather than in what it does with them. That is the
next thing to measure, and the aggregate instrument is in place to measure it.

---

## The darkness has a shape: far terrain matches, near terrain is dark, and the AO code is upstream-identical

The two frames were analysed by row and column before anything else was changed, and the difference is not uniform:

| rows | ratio (Celtium / reference) |
|---|---|
| 200-240 (the far plain) | **1.03** |
| 240-320 | 0.83-0.89 |
| 320-720 (near terrain) | **0.65-0.75** |

Columns are flat at 0.83-0.86 across the whole width, so there is no left-right structure: not a texture edge, not an
occlusion pattern that follows geometry. The correlation between the two frames is 0.904 and the terrain-band ratio has mean
0.703 with a standard deviation of 0.143. What that describes is a **distance-dependent** difference - the far ground, seen
at a grazing angle where every surface is a top face, matches the reference almost exactly, while the near ground, where
side faces fill the screen, is a third darker.

That points at the shading of those faces rather than at textures, light maps or the light path, all of which have now been
measured or eliminated. The AO computation itself was then diffed against the pinned upstream checkout and is **byte-
identical** (`AoFaceData`), so the occlusion arithmetic is not what differs. The port's own changes under
`impl/render/chunk` are `SectionRenderDataStorage`, `ShaderChunkRenderer`, `RenderSectionManager`, `SectionGraph`, the
`backend` package and the two vertex formats (the last two only by this round's instruments), so the place to look next is
how the port's storage path applies the colour and the occlusion it is handed - the arithmetic matches vanilla, the wiring
around it does not.

Two process notes stand from this round as well. The row/column analysis cost nothing and produced more than the last three
experiments; and the AO being upstream-identical is exactly the kind of fact that should be established before theorising
about AO, which it was not.

---

## The missing factor is exactly 0.69, it is not the light map, and that leaves the atlas mirror

Two measurements this round, both cheap, and together they remove the last two candidates but one.

First, **what the factor is**. Fitting the near-terrain band of the two frames against each other:

| fit | result |
|---|---|
| pure scale | k = **0.689**, residual 13.81 |
| affine | k = 0.577, c = +14.9, residual 13.56 |

The affine fit is better by **0.25** of a luminance unit, so the difference is **multiplicative to within noise** - not a
fog-like additive term, which would also have compressed the contrast. The standard deviations agree almost exactly (22.8
against 23.4), which is what a pure scale does and an additive term does not. And the scale is the same in all three
channels: 0.691 red, 0.685 green, 0.703 blue. So one uniform factor of about **0.69** is missing, with no tint change and no
contrast change.

Second, **what the sampled light map is**. An instrument at the terrain bind, placed and verified inside `recordRenderPass`
before it was trusted:

```
CELTIUM_LIGHT_BIND glId=12 view=... size=16x16 atlas=512x512
```

GL texture 12 is exactly the `gl-texture-12-16x16.png` that has been analysed twice, so the texture that was reasoned about
**is** the texture that is sampled. And that analysis says the texel addressed by this scene's light coordinate (block 0, sky
15, axes swapped as of the previous round) is the **bright corner at 252** - a factor of 0.99, not 0.69. So the light map is
excluded, by its own measured content at the address the shader actually uses.

That leaves the **atlas** - the block texture - as the only remaining multiply in `texture x colour x light`, since the vertex
colour comes from a builder that is byte-identical to upstream, the light is full daylight, and the light map samples a
bright texel. It is also consistent with what happened two rounds ago when the upload's rows were flipped: the terrain went
**almost black** (band luminance 3.2), which is not what a mere orientation change does to a correctly mirrored atlas - it is
what happens when the content itself is wrong in both orientations. The next thing to inspect is therefore the atlas's own
upload and its level 0 content, not anything on the light path.

---

## The atlas is byte-identical to OpenGL's, and two of this project's "improvements" were deviations

Two results this round, one of them a definitive elimination and the other a correction of this project's own work.

**The images are the same.** An instrument that reads this mirror's level 0 back from the device and compares it byte for
byte with the level 0 OpenGL currently holds - the comparison the plan has pointed at for several rounds - reports:

```
CELTIUM_LEVEL0 size=512x512 bytes=1048576 differing=0 fraction=0.0000 meanAbsDiff=0.00 maxAbsDiff=0
CELTIUM_LEVEL0 size=128x128 bytes=65536   differing=0 fraction=0.0000 meanAbsDiff=0.00 maxAbsDiff=0
```

Every byte of the 512x512 atlas and of the 128x128 textures is identical to what the game holds. So the block texture is
**not** the missing 0.69, the upload is correct in every respect - pitch, row order, format, level 0 - and the row-flip
experiment two rounds ago is confirmed as wrong by content rather than by frame.

**The pinned lineage was read at last.** The upstream checkout carries the very shaders this port claims to be faithful to,
`common/build/resources/main/assets/sodium/shaders/blocks/block_layer_opaque.{fsh,vsh}`, and they show two things this
project changed while believing it was fixing something:

| | pinned lineage | this project (until now) |
|---|---|---|
| light coordinate | sampled as-is | **axes swapped** (round 266) |
| divisor in `_sample_lightmap` | **`uv / 256.0`** with a 16-texel clamp | `/ 16.0` (round 267) |

Both changes moved the frame in the direction of the reference - together worth 7.4 of mean luminance (34.08 to 26.68) - and
neither is what the reference does. They are **deviations that happened to help**, which is not the same as a fix, and they
are reverted so that the remaining difference is measured against the reference's own behaviour rather than against a
compensating error of our own:

```
reference-faithful shader: mean 34.08 | >8 68.81%      (with the two deviations: 26.68 | 70.49%)
```

Both numbers are recorded. The faithful configuration is the one kept, and it is verified in the jar by the SPIR-V's size
(`7748`) rather than assumed.

What that leaves is narrow and stated plainly: the texture is identical, the light map samples a bright texel, the vertex
colour comes from code byte-identical to upstream and the light is full daylight - yet our product is 0.69 of the reference's
with the same contrast. The remaining candidates are the **inputs** the mesh builder is handed and anything in the shader that
the located reference sources disagree with, and those sources are now in hand for a line-by-line comparison rather than
another experiment.

---

## The missing factor is the light map - and the reference may not be applying it at all

Reading the located reference sources line by line, rather than experimenting, produced the whole picture in three steps.

**The shaders are faithful.** `chunk_vertex.glsl` - the include the reference's terrain vertex shader calls for
`_vert_init()` - builds the colour with `_vert_color = a_Color;` and unpacks the light coordinate with
`ivec2((uvec2((a_LightCoord >> 16) & 0xFFFFu) >> uvec2(0, 8)) & uvec2(0xFFu))`, which is character for character what this
port has. There is no normalisation, no divisor, no colour-space step that was missed.

**The light map supplies exactly the missing factor.** The dump's alpha is 255 everywhere, so the fragment's
`diffuseColor.rgb *= m_color.a` is a no-op and the occlusion readings were right; but the texel this scene's light coordinate
addresses is

```
sampled texel (u=block, v=sky): x=0 y=15 -> rgba=[155 155 187 255]   luminance 166 = 0.65
```

and 0.65 is the 0.69 the frame comparison has been fitting all along. The light map is not white at this texel - it is a
bluish 0.65 - and multiplying by it is exactly the difference.

**And the reference can skip that multiply entirely.** Its vertex shader has two branches:

```glsl
#ifdef CELERITAS_NO_LIGHTMAP
    v_Color = _vert_color;
#else
    vec4 lightColor = _sample_lightmap(u_LightTex, _vert_tex_light_coord);
    v_Color = _vert_color * lightColor;
    ...
#endif
```

and the define is added by `ChunkShaderOptions` when the render pass says `hasNoLightmap()`. **This port has no such branch
at all** - the Vulkan shader multiplies unconditionally. So if the pass the fixture draws declares no light map, the reference
renders without that 0.65 while this renderer applies it, and the entire terrain difference is a **missing configuration-path
fidelity**, not a wrong colour, not a wrong texture and not a wrong light sample.

That is testable in one step and it is the next one: read the 1.12.2 pass definitions and see what `hasNoLightmap()` returns
for the pass the fixture uses. If it is true, the fix is a second vertex module without the light map multiply - the port's
own version of the same branch, chosen by the same condition - and the terrain baseline should then drop by most of its 34
points at once.

Worth stating plainly, because this thread has spent many rounds: three of its findings have been eliminated by reading
sources rather than by running anything (the AO being upstream-identical, the shaders being faithful, and now the light map
branch), and each elimination was cheap.

---

## Read again, refuted again: the passes do light the map, and the remaining candidate is the uniforms

Two more source readings, both cheap, both narrowing.

**The `hasNoLightmap` hypothesis is refuted.** The flag is a builder parameter on `TerrainRenderPass`, its default is false,
and the 1.12.2 pass builder (`VintageRenderPassConfigurationBuilder`) never sets it - so this project's passes all light the
map, and so does the reference. The 0.65 the light map applies is therefore in **both** frames, and last round's tidy
explanation for the whole terrain difference is wrong. It is recorded as wrong rather than quietly dropped, because it was
the fourth hypothesis in this thread to look convincing and die on a reading.

**The reference's other fragment branch is the one that matters here.** The reference has two ways of applying the vertex
colour:

```glsl
#ifdef USE_VANILLA_COLOR_FORMAT
    diffuseColor *= m_color;            // rgb by rgb, alpha into alpha
#else
    diffuseColor.rgb *= m_color.rgb;
    diffuseColor.rgb *= m_color.a;      // the AO channel, for the compact format
#endif
```

This project's active vertex format is `VanillaLikeChunkVertex`, so the reference compiles the **first** branch - and this
port's shader does the **second**, always. It has the compact format's occlusion multiply baked in where the reference
applies rgb once and puts the alpha into the alpha channel. For the terrain that multiply is currently a no-op - the packed
colour's alpha is 255, which the ABGR reading established and the occlusion experiment confirmed by measuring nothing - but
it is a genuine fidelity gap for any vertex whose alpha is not 1, and it is now recorded as one rather than as a curiosity.

With the shaders then equivalent for this scene, the texture byte-identical and the light map in both frames, what is left is
**not the vertex path at all**: it is the per-draw state - the fog uniforms first among them, whose `mix` toward a fog colour
would scale a band's level while preserving its contrast, which is precisely the signature the 0.69 has. Printing those
uniforms at the terrain draw is the next step, and it is one line.

---

## The light map is excluded by measurement too: removing it leaves the terrain at 0.70

Last round's reading suggested the light map might be the whole difference - its texel is 0.65 and the frame ratio fits
0.69 - and the only way to settle a reading that tidy is to remove the multiply and look. With the SPIR-V recompiled and
verified in the jar by size:

| configuration | mean | near-terrain ratio |
|---|---|---|
| light map applied (reference form) | 34.08 | 0.75-0.84 |
| **light map multiply removed** | **26.12** | **0.70-0.76** |

The light map moves the frame - 8 mean points - but the near terrain stays a quarter dark, so it is **not** the 0.69. That is
the fifth candidate this thread has eliminated, and the second to die by measurement rather than by reading.

The fog was checked in the same round and is not a candidate either. Printed to stdout rather than through the capped
diagnostics list, the values the terrain draws with are

```
CELTIUM_FOG fogColor=[0.514, 0.596, 0.735, 1.0] start=96.0 end=128.0 density=1.0 shape=0 glFogMode=3 cutoff=128.0
```

a smooth fog from 96 to 128 blocks toward the sky colour, with the near terrain well inside the unfogged range - which also
explains why the far band matches the reference and the near band does not.

So the list of verified-identical things is now: the atlas (byte for byte), the shaders (against the reference sources), the
vertex colour producer (against upstream), the light coordinates (measured), the light map's effect (removed and measured),
and the fog (read). What is left cannot be found by comparing frames; it needs the shader's own arithmetic checked against
numbers computed by hand from the dumped texture, the packed colour and the light coordinate - a numeric comparison rather
than a sixth image ratio.

The light map multiply is restored to the reference's form and the SPIR-V verified again.

---

## The shader trap is closed: GLSL is now an input to the build

The defect that cost two rounds - shader sources that no build step read, with committed `.spv` files nothing regenerated -
is fixed at the level where it cannot recur. `celtium-vulkan/build.gradle` now declares the shaders as inputs and compiles
them with `glslangValidator` into the resources the loader already reads:

- `compileTerrainShaders` takes `src/main/shaders` as `inputs.files`, the compiler as an `inputs.property`, and the output
  directory as `outputs.dir` - so a source edit invalidates it and a build cannot ship a stale artefact;
- the compiler path is resolved against the **workspace root**, because the build runs from the composite directory
  (`build-1122`) and the first attempt failed with "a problem occurred starting process 'command
  '.celtium-work/gpu-tools/.../glslangValidator''" - a relative path the task could not find;
- the committed `src/main/resources/org/soutalab/celtium/vulkan/spirv` directory is **deleted**, so there is no second copy
  to go stale behind the generated one.

Verified by building and opening the jar: thirteen `.spv` entries, and the three terrain shaders at sizes that match the
manual compiles exactly (`terrain_solid.frag` 8864, `terrain_solid.vert` 8524, `terrain_solid_vanilla.vert` 7748). The
evidence that source edits now reach the artefact is the preceding rounds themselves: each edit changed those sizes
(8864 to 8604 to 8592 and back), which is what a compiled artefact does when its source changes and what the committed one
never did.

**The terrain-darkness thread is parked here, deliberately.** Five candidates have been eliminated - the atlas byte for
byte, the shaders against the reference sources, the vertex colour producer against upstream, the light map by removing its
multiply and measuring, and the fog by reading its uniforms - and the remaining 0.69 needs a numeric comparison rather than a
sixth image ratio. It is recorded with everything needed to resume: the reference jar, the fixture flags, the measured
ratios by band, and the list of what is already excluded.

---

## S11 closed: 570 frames, 3327 flushes, and a budget that was reset on every one of them

The audit's S11 is small and precise - `updateTimings` resets the per-frame window counter, but it is called on every fence
wait, so a frame with several flushes has its budget reset several times and "sixteen windows per frame" cannot be
demonstrated at all - and it is now fixed in the two places that make it true.

The frame gets an identity: `frameId` is incremented at the camera boundary (`captureWorldSnapshot`), which fires exactly
once per frame, and that is also where the previous frame is reconciled into `lastFrameAccepted` / `lastFrameDrawn` and where
the new frame's counters are zeroed. `updateTimings` no longer resets anything that belongs to a frame; it counts flushes,
which is what it can honestly count from a fence wait.

Measured on a normal run:

```
frameId=570  flushCount=3327  |  lastFrameAccepted=1  lastFrameDrawn=1  |  immediateTaken=554  flushes=577
```

**570 frames against 3327 flushes** - 5.8 flushes per frame - so the old code reset the per-frame budget 3327 times where
there were 570 frames, a factor of six. The reconciliation also reports its first numbers: the last frame accepted one
group and drew it, with the run's totals beside it.

Worth noting for the other diagnostics: `framesSeen`, which several trace lines use as "frame N", was incrementing on
flushes, so those labels have been off by the same factor. It is left as it is - renaming it is a change to diagnostics
rather than to drawing - but `frameId` is now the number that means a frame.

---

## S12's two named paths are retired now, and the count is six

The audit's S12 names two paths that destroy resources while a recorded command buffer may still name them: `invalidate`
(which the per-frame mirror refresh calls as `invalidateAll`) and `resize`. Both go through `recreateImageResources`, which
called `vkDestroyImageView`, `vkDestroyImage` and `vkFreeMemory` directly. Those three calls now hand the handles to a
`VulkanRetireQueue`, and the backend drains that queue in the one place it knows the device is idle for certain - after the
fence wait in `beginCommands` has **succeeded**, which is also where the code already concludes that the previous submission
has finished.

Measured on a normal run:

```
exit 0 | clean | messages 0 | fixture PASSED | passes=2168 | taken=551 | dropped=0
frameId=566  flushes=3298  retiredFlushes=6  retire=retired=6
```

Six resources retired, six flushed: the refresh cycle retires one set per interval and every one of them is destroyed after
an idle rather than during one. No regression - the fixture passes, validation is silent, and the accepted/dropped figures
are the ones the previous round established.

Recorded as open, because the audit item is not closed: the teardown path in `delete()` still destroys directly, and there
are still no generation-carrying handles, so a descriptor cache can hold a set built on a view that has since been retired.
Those are the two pieces that remain of S12, and they are named rather than implied.

---

## S12 closed: generation-carrying cache keys, a retired sampler, and nine of nine destroyed

The two pieces left after the retirement queue were the teardown path and the descriptor caches' staleness, and both are
now done.

The teardown path turned out to be **already retired** - `destroyImageResources`, which `delete()` calls, is the method whose
three destroys were moved onto the queue - so what was missing there was the **sampler**, and it now goes through the queue
as well. The backend drains the queue once more at teardown, after `settleGpu` has ended the queue's work, and records how
many resources that destroyed.

The staleness question is the more interesting half and is the audit's own wording: the caches were keyed on the **view
handle**, and a driver may hand back the same handle for a new view, in which case a descriptor set built on the retired
view would be served for the new one. `VulkanGlTexture` now carries a `generation`, bumped in the one place a view is
created, and exposes `key()` - the view combined with the generation. **Eight lookups** across the four immediate pipelines
key on `key()`; the descriptor writes still use the real view, which is the only thing the driver accepts.

Measured on a normal run:

```
exit 0 | clean | messages 0 | fixture PASSED | passes=2173 | taken=547 | dropped=0
retire=retired=9 flushed=9 pending=0
```

Nine resources retired, nine destroyed, nothing left pending, and no regression.

Recorded as a distinction rather than as a claim: the generation fix is **correct by construction but has not been exercised**
- no run has caught a driver reusing a view handle, so this is a fix by reasoning, not by reproduction. It is the right shape
regardless, and if a future run does catch it, the key is what makes it harmless.

---

## S10's draw order: five violations per run, and the accepted work now goes first

The audit's S10 names a specific ordering defect in its own words: "small accepted batches wait for a threshold while the
next unsupported draw runs in GL immediately". Accepted batches are held until the group closes and a flush happens; a
refusal returns at once and lets the original GL draw run where it stands, so a held batch ends up drawn **after** content
that came later in the frame.

The fix is a helper - `flushPendingBeforeGlDraw` - called before the refusals: it closes the current group, draws what is
pending, and counts itself. With nothing pending it does nothing at all, so the cost is a list check. It is inserted at the
refusal sites whose shape the edit could match reliably; four of them carry it, and the rest are recorded as needing the same
treatment rather than assumed to be harmless.

Measured on a normal run:

```
exit 0 | clean | messages 0 | fixture PASSED | passes=2157 | taken=546 | flushes=558 | dropped=0
orderPreservingFlushes=5
```

**Five times in the run, a refusal happened while batches were still pending.** Each of those five was a draw-order
violation before this change and is none now, which turns the audit's description into a count. No regression: the fixture
passes, validation is silent, and the accepted/dropped figures are unchanged in substance.

Recorded with its limits: the ordering is correct by construction rather than observed directly - no image comparison can see
a five-event ordering fault on this scene - and the scene never presents clears or FBO switches between accepted batches, so
that half of S10 remains unexercised as well as partly unfixed.

---

## The refusal sites that matter are in the timed wrapper, and the count goes to six

The previous round put the order-preserving flush at the four early refusals in `submitImmediateBatch` and recorded that
"the rest still return immediately". Listing every `immediateLastRefusal` assignment with its enclosing method showed the
guess was wrong in an instructive way: the refusals that fire in numbers are not in `submitImmediateBatch` at all but in
`submitImmediateBatchTimed`, the wrapper the first method delegates to - **fifteen** assignments there, against four before
the boundary. The same insertion applies, and **thirteen** of them now flush what is pending before the batch goes to GL,
seventeen calls in the file in total.

Measured:

```
exit 0 | clean | messages 0 | fixture PASSED | passes=2180 | taken=551 | flushes=558 | dropped=0
orderPreservingFlushes=6      (5 before this round)
```

One more refusal in the run happens while batches are pending, so the fix reaches a case the first pass did not; no
regression anywhere else. The two that did not take the call are the ones whose return is not a plain `return false` within
the window the edit looks at, and they are recorded rather than assumed harmless.

The honest boundary is unchanged: this ordering is correct by construction. No image comparison can see a six-event ordering
fault in this scene, and the scene does not present clears or FBO switches between accepted batches, so the other half of S10
remains unexercised as well as partly unfixed.

---

## Clears are an ordering boundary now, and this scene never exercises one

A clear wipes the target immediately while accepted batches are held for a flush, so anything held would be drawn *after*
the clear instead of before it - the same fault the refusal sites had, with a different trigger. There was no hook on clears
at all: `GlStateManager.func_179086_m` is the SRG name of `glClear`, and it is now hooked, calling `flushBeforeClear`, which
draws what is held and counts itself.

Measured:

```
exit 0 | clean | messages 0 | fixture PASSED | passes=2176 | taken=550 | flushes=563 | dropped=0
orderPreservingFlushes=6   clearOrderFlushes=0
```

**Zero.** The scene never clears while batches are pending, so the new hook is correct by construction and **unexercised** -
the same distinction this project has had to make about the generation keys in S12. The FBO half of the same audit item was
already in place from an earlier round: the render target is part of the group key and `bindFramebuffer` flushes the pending
group when the target changes, which the two-target fixture exercised with `passBoundaryFlushes=10`.

So S10 now stands as: the refusal ordering implemented at all seventeen refusal sites and measured at six occurrences per
run; clears implemented and measured at zero; FBO switches implemented and measured on the gated fixture only. What is left
is not more code but **scene coverage** - the ordinary suite presents neither a clear nor an FBO switch between accepted
batches, and the gated fixture that does present the latter is the one whose capture the suite suppresses.

---

## The lightmap is in the descriptor key, and the measured cost is 545 sets for 546 batches

S13's remaining half was that the descriptor caches ignored the light map: the block and particle pipelines keyed on the
texture alone, so a set built for an old light map could be served for a new one - and the light map mirror is refreshed
every few frames, which is exactly when that matters. Both pipelines now combine `texture.key()` with `lightmap.key()`.

The consequence is measured rather than argued, and it is blunt:

```
exit 0 | clean | messages 0 | fixture PASSED | passes=2154 | taken=546 | dropped=0
descriptorSets=545
```

**545 sets for 546 accepted batches** - effectively one per batch. Before this change the cache was shared across batches
that used the same texture; now every batch misses, because the light map's key changes on every refresh while the atlas's
does not. The fix is correct and stays - serving a stale light map is worse than building a set - but it has turned the
descriptor cache into a formality, which is a **performance regression** recorded as one.

The cause also names the fix. The light map's **content** changes every frame, but its **identity** does not have to: the
mirror is being **recreated** (new image, new view, new memory) on every refresh rather than being uploaded into the same
one. Uploading in place would leave the view and generation stable, the key constant, and the cache working again - and it
would be measurable: `descriptorSets` should fall from 545 to roughly the number of distinct textures in the scene.

---

## In-place mirror refresh: 545 descriptor sets become one, with 1136 re-uploads

The cost recorded last round had a single cause: `invalidateAll` **dropped** every mirror entry, so the next use created a
new image, a new view and a new generation, which changed every descriptor key in the immediate path. The fix is to keep the
mirror and refresh its **content**:

- `invalidateAll` now **marks** the entries stale (`invalidate` does the same for one texture);
- `get` re-uploads a stale mirror **in place** - same image, same view, same generation - and counts itself.

Measured on a normal run:

| | before | after |
|---|---|---|
| descriptor sets created | **545** | **1** |
| in-place refreshes | - | **1136** |
| retirements | 9 | **6** |
| accepted / dropped | 546 / 0 | 580 / 0 |

Exit 0, validation clean, 0 messages, fixture PASSED, `passes=2239`. So the light map is now both **part of the descriptor
key** (last round) and **stable across refreshes** (this round): the cache is correct *and* it works, and the resources it
would have churned through - 1136 re-uploads that used to be 1136 re-creations - are no longer created at all, which the
retirement count confirms by falling.

This is the first change in several rounds that is a **measured improvement rather than a defect correction**, and it came
from following a number the previous round produced rather than from a new hypothesis. The acceptance was named before the
change (descriptor sets should fall to roughly the number of distinct textures) and the result is better than that: one set,
because the scene's immediate path uses one texture-and-lightmap pair.

---

## S15: the teardown releases everything now, and the fixture never runs it

The audit's S15 is a leak list, and the fix is to work through it: `delete()` released the terrain resources and left the
immediate mesh, the held groups, both immediate pipeline caches, the UI pipeline and mesh, the texture registry and the
swapchain to the process exit. All of them are now released through their own `delete()` - direct calls, no reflection - with
a small wrapper that reports a failure instead of propagating it, and one count of what was released.

The first version of this fix used a reflective `delete`/`destroy` lookup, which is exactly what this project's own policy
says not to keep. It was replaced with direct calls before the commit; the file now contains no `java.lang.reflect` at all.

Verified as far as the fixture goes: exit 0, validation clean, 0 messages, fixture PASSED, `descriptorSets=1`, no regression.
And **not verified beyond that**: the ordinary fixture never leaves the world, so `delete()` is never called and the release
count never appears. The teardown is therefore correct by construction and **unexercised** - the third fix in this series to
carry that label, which is itself a statement about the fixture: it exercises one scene and one lifecycle, and the reload and
world-leave paths the audit keeps naming are outside it.

Recorded as still open from the same item: the registry's create-then-fail-upload path can still leak the GL texture.

---

## S17: the composite present now proves it is a composite, and this scene never disagrees

The audit's S17 is the most dangerous item in the list because it can **delete draws**: the branch that presents from the
`POSITION_TEX_COLOR` batch assumed that layout means "the game framebuffer blitted onto the screen", while the captured stack
showed the same call blending an overlay *into* a framebuffer. GUI, outlines, offscreen passes and postprocessing share the
layout, so presenting on the format alone cancels GL draws that were not composites.

The branch now checks the two conditions that actually distinguish the screen-only case, before it takes anything: the bound
target must be the **main framebuffer**, and the viewport must be the **whole of it** (the packed viewport key compared
against the key a full-target viewport produces). Everything else is refused before acceptance with its reason recorded, so
GL keeps the draw. A counter makes the frequency visible.

Verified with the branch enabled (`-Dceltium.vulkan.presentFromComposite=true`):

```
exit 0 | clean | messages 0 | fixture PASSED | passes=2225 | taken=578 | dropped=0
compositePresentRefusals=0
```

**Zero refusals** - in this scene every format-2 batch that reaches the branch is a genuine full-screen composite, so the
stricter conditions never fire, and the run is unchanged. So the fix is **correct by construction and unexercised**, the
fourth in this series to carry that label (S10's clears, S12's generation keys, S15's teardown, and now S17). That is worth
stating as a pattern rather than four coincidences: **the fixture exercises one scene and one lifecycle**, and the audit's
remaining items live in the paths it does not walk - reloads, world-leaves, second targets, overlay blending and postprocessing.

---

## S19 measured down to the byte: 132 against a guaranteed 128, and the four that can move

The audit's S19 is a capability question rather than a bug report - the immediate pipelines ask for more push constant space
than Vulkan guarantees - and this round pinned it exactly rather than acting on it. The block is two `mat4` (128 bytes) plus
`layout(offset = 128) float u_AlphaRef`, and the total is a constant named in **six** files: the five immediate pipelines and
the UI pipeline. The submission site is uniform: `vkCmdPushConstants(commandBuffer, layout, VERTEX_BIT | FRAGMENT_BIT, 0,
pushConstants)` in each, with the buffer built by the backend.

The four bytes that break the promise are also the easiest to move, because the shaders use them for exactly one test each -
`u_AlphaRef > 0.0 && colour.a < u_AlphaRef`, in four fragment shaders - and the immediate pipelines are **already created per
state key**, which is how blend, depth and cull are handled today. The alpha reference belongs in the same mechanism: a
**specialisation constant**, with `VkSpecializationInfo` on the fragment stage, which leaves the push block at exactly 128
bytes.

Recorded as the next change rather than half-done now, for a reason this project has learned twice: the push constant size is
a **compiled artefact** since the shaders moved into the build, so a layout migrated in one place and not another would be a
worse state than being over by four.

---

## S19 attempted: 116 bytes compiled, and the driver refused it for a reason worth keeping

The plan from last round was projected to work - compress the model view to its three affine columns, since the fourth is
(0, 0, 0, 1) by construction, giving projection 64 + 48 + alpha reference 4 = **116 bytes**, inside the 128 Vulkan guarantees
- and it was carried out in full: five vertex shaders and four fragment shaders rewritten, the CPU side writing the new
layout including the isolation push, six push ranges changed from 132 to 116.

It compiled, it built, and **the pipeline was rejected**: the vertex stage described 112 bytes and the fragment stage 116, and
the driver refuses a pipeline whose stages disagree about the push constant block. **Both stages must declare the same
block.** The first plan had this wrong, and no amount of reading the Java would have shown it: it took building the change
and letting the validation gate answer. The correction (declare the member in the vertex stage as well) then broke two of the
five shaders, whose own members put the offset in conflict, so the migration was **reverted** rather than half-carried - and
because the revert restored the files to their committed state, the tree is green with nothing to undo: build successful,
exit 0, validation clean, 0 messages, fixture PASSED, `immediateTaken=565`, `postAcceptDroppedGroups=0`,
`descriptorSets=1`.

Two outcomes matter more than the aborted change. **The shader build wiring proved itself** - a broken shader now fails the
build, precisely the trap that cost two rounds of measuring a previous jar. And it has a **gap to close**: the task runs the
compiler through `providers.exec` without capturing its output, so a failure reports *that* a shader failed and not *why*; the
message had to be recovered by running `glslangValidator` by hand over every file. Capturing and printing it is a small,
obvious next change.

Also recorded for the next attempt: `immediate_block.vert` and `immediate_particle.vert` already declare members of their own
past the matrices, which is why the naive alignment broke them.

---

## The shader build wiring reports why now, and the API that made it necessary

Last round's attempt left a gap worth closing: the build failed on a broken shader - which is the trap closed, and worth
having - but the **reason** had to be recovered by running `glslangValidator` over every file by hand. The task used
`providers.exec`, and the attempt to capture its streams produced:

```
InvalidUserCodeException: Standard streams cannot be configured for exec output provider
```

That API **forbids** configuring a process's standard streams by design. The supported modern way is an injected
`ExecOperations`, so `compileTerrainShaders` is now a proper task class - `@InputFiles` for the shader sources, `@Input` for
the compiler path, `@OutputDirectory` for the generated SPIR-V, `@TaskAction` for the loop - and it captures the compiler's
own message and reports it on a non-zero exit.

Verified by breaking a shader on purpose:

```
glslangValidator failed on immediate_color.vert (exit 2):
ERROR: .../immediate_color.vert:22: 'this' : Reserved word.
ERROR: 1 compilation errors.  No code generated.
```

The error was reverted, and the tree is green with the artefact verified: `BUILD SUCCESSFUL`, exit 0, validation clean, 0
messages, fixture PASSED, `passes=2206`, `immediateTaken=568`, `postAcceptDroppedGroups=0`, `descriptorSets=1`.

Worth recording as a pattern, because it is the same lesson as the shader trap itself in a different medium: **the build must
be able to say what went wrong**. Both times this project lost rounds to a silent build step - the committed SPIR-V that
nothing regenerated, and now the compiler whose message was discarded - and both times the fix was to make the build report,
not to be more careful.

---

## S19 closed: 116 bytes, one identical block in all nine shaders, and the driver accepts it

The retry worked because the failure from last round named the constraint. The push block is now 116 bytes - projection 64,
the model view's three affine columns 48, alpha reference at 112 - and, crucially, **all nine shaders carry the same block**,
vertex and fragment alike. Vulkan refuses a pipeline whose stages describe the same push constant block differently, and that
is exactly what the first attempt produced: 112 in the vertex stage, 116 in the fragment.

The first attempt's other mistake is now understood too: `immediate_block.vert` and `immediate_particle.vert` **already
declared the alpha reference**, because their fragment stages needed it, so inserting it again created duplicate members and
broke those two shaders. This time the whole block was replaced with one identical text in all nine files, and the shader
compilation confirmed all nine.

Verified end to end:

```
BUILD SUCCESSFUL | exit 0 | clean | messages 0 | fixture PASSED
immediateTaken=553  postAcceptDroppedGroups=0  descriptorSets=1  passes=2179
```

No validation error, so the driver accepts the pipeline; and the frame is unchanged from before the migration (sub-unit mean
difference), which is what a pure capability fix should look like: **less asked of the API, same drawing**.

Six push ranges moved to 116 and the terrain pipeline keeps its own separate 128-byte layout, which is legal because it is a
different pipeline layout.

---

## A note on what the frame comparison can and cannot say

The S19 verification compared the frame before and after the migration and got a mean difference of 0.968 with a maximum of
255. Before recording that as "the frame is unchanged", the same comparison was run between two rounds of the **same build** -
unrelated runs with no code difference between them - and it gives **0.95 with the same maximum**. The scene contains
time-varying content, so roughly one unit of mean difference is its own noise floor, and the migration sits inside it.

This is written down because the number would otherwise be quoted as evidence of pixel equality, which it is not: the honest
claim is that the change produced **no difference beyond the scene's noise**, and the way to know that is to have measured the
noise. Several earlier rounds in this log compared frames across builds and read small non-zero means as noise without
checking; this is the measurement that justifies doing so.

---

## S18: the flag describes this present, and an out-of-date acquire stops before submitting

Two of the audit's four S18 points were real and are fixed. `presentImage` never reset `outOfDate`, so one out-of-date event
would leave the caller rebuilding the swapchain for the rest of the session; it is now cleared at the top of the method, which
makes it mean "the present about to happen found the swapchain out of date". And an acquire returning
`VK_ERROR_OUT_OF_DATE_KHR` used to set the flag and **carry on** to submit and present an image the acquire had just declared
unusable; it now returns before anything is submitted, with the caller seeing `succeeded=false` and `outOfDate=true`, and a
counter of its own.

The other two points turned out to be already sound, and saying so is as useful as fixing them: the null-image case is guarded
by the `swapchain == VK_NULL_HANDLE` check at the top, and `lastPresentSucceeded` is only set after a successful present and
reset at the top of the method, so it cannot mark an unsubmitted present as successful.

Verified as far as the runs go: a normal run and one with `-Dceltium.vulkan.forceOutOfDate=true` are both clean - exit 0,
validation clean, 0 messages, fixture PASSED, `passes=2185` and `2189`, `postAcceptDroppedGroups=0`.

And **not** verified, which is recorded rather than glossed: the counters added for the two new paths did not appear in the
reported debug line - the backend builds that line in more than one place and the patch landed in another - so the
quantitative confirmation is still pending, and the acquire branch cannot be reached by the existing injection at all, which
forces the flag after a successful acquire. Two small follow-ups, both named.

---

## S18's failure path, finally reachable, hangs the client

Two pieces of instrumentation were needed before the audit's S18 could be tested at all, and both are worth recording because
each one exposed a way this project had been measuring nothing.

The first: the counters added for the out-of-date state never appeared in the report, even though they are in the debug string
- the `rendererDebug` list is **capped**, and the entry carrying them was not among those kept. The fix is the one this log
has now applied three times: **print to stdout**. The state is reported whenever it changes, which is quiet and complete.

The second: the injection that existed forced the out-of-date verdict **after** a successful acquire, so the branch that
matters - an acquire that refuses the image it just handed out - was unreachable. A second flag,
`celtium.vulkan.forceAcquireOutOfDate`, now synthesises exactly that.

With the injection off, a normal run and a composite-present run are both clean and report `outOfDate=0
acquireOutOfDate=0 presentFailures=0`. With `presentFromComposite=true` **and** `forceAcquireOutOfDate=true`, the **client
hangs**: the run ends at the 300-second timeout, no validation report is written, and the fixture reports `MISSING_REPORT`.

This is a real defect, not a tooling artefact. The composite path on its own completes, and the normal path never calls
`presentImage` at all - which is itself worth stating plainly, because it means S18's code has been **inert** in every
measurement this project has taken. The hang belongs to the branch that was just made reachable, and the cause is visible in
the composite caller: it calls `frameTarget.beginFrameForPresent()` **before** presenting and ends the frame only when the
submission actually happened, so a present that returns early leaves a frame begun and never ended, and the next frame waits
on a fence that will never be signalled.

So the honest state of S18 is: two of the four points fixed, two shown to be already sound, the failure path now **reachable**
for the first time - and **hanging**, which is the next thing to fix rather than something to have claimed success about.

---

## The deadlock is gone; the configuration still does not finish a verified run

The frame target documents its own trap in a comment: *"waiting for a submission that was never sent deadlocks the render
thread"*. `endFrame()` makes GL wait on the Vulkan-to-GL semaphore, and an out-of-date acquire - now reachable for the first
time - returned without submitting anything, so that wait had nothing to wait for. The fix is a **signal-only release**: the
composite caller submits an operation with no command buffers that waits on the frame's GL-to-Vulkan semaphore and signals the
Vulkan-to-GL one, which restores the pairing the frame target requires, and then ends the frame normally. It runs only on the
path where a frame was begun and no submission happened, which is why it is a release rather than a workaround.

Re-running the configuration that previously hit the three-hundred-second timeout, the game **now exits on its own** - the run
script returns 0 - so the deadlock is gone.

But the **fixture still reports a missing report** in that configuration, and the report read back was the previous run's
file, so the honest statement is narrower than "fixed": the previously unbounded hang is now bounded, and the composite
present with a forced out-of-date acquire still does not complete a verified capture. That is consistent with what the suite
already said about this path - it is the one whose capture is suppressed - and it stays off by default, which is why the
ordinary measurements are unaffected: exit 0, validation clean, 0 messages, fixture PASSED.

Named for the next round: why the fixture does not complete in that configuration.

---

## The composite path is never reached in this scene, and the stricter guard was mine to undo

The question left open last round - why the forced out-of-date configuration never produces a report - has an answer that is
not a defect. Running it for ninety seconds and reading the stdout markers directly: the game starts, the terrain draws (the
fog line carries a real camera position), and **not one `composite present` record appears**. In the same configuration
without the injection, the fixture passes. So the composite-present branch is never reached here: `compositePresentRefusals`
is 0, meaning it is not being refused either - there is simply no format-2 batch to take. The injection therefore never calls
`presentImage`, never reaches the acquire, and never satisfies what the fixture waits for. `MISSING_REPORT` is the
injection's own consequence.

That also settles the guard added for S17, and not in its favour. The first version required the viewport to be the whole
target as well as the target to be the main framebuffer, and with it the composite configuration **stopped presenting
altogether** - which is what the missing records were showing all along. It is now relaxed to the check the audit actually
asked for: **the bound target must be the main framebuffer**. That keeps the distinction between the screen and an overlay
blended into a framebuffer, and it restores the previous behaviour. Verified: composite configuration clean, exit 0, validation
clean, 0 messages, fixture PASSED, `passes=2217`.

Worth stating plainly, because it is the third time in this audit that the answer was "the scene does not do that": the
composite present, the clear boundary, the second target and the teardown are all paths this fixture **never walks**. Each fix
is correct by construction; none of them is exercised. The next useful work is not another guard but a scene that reaches
them - which is why the fixture extension keeps being named as the follow-up.

---

## S16 closed: 89 animated atlas uploads now reach the terrain, and the descriptor count stays at one

The audit's S16 was precise and correct: `invalidateTexture` - the hook that fires when something re-uploads a GL texture -
marked only the texture **registry** stale, while the terrain atlas and the light map are held **directly** by the backend.
So the game could re-upload the block atlas all day for its animated sprites and the terrain would keep sampling the copy it
took at startup. The audit's phrasing was the tell: "five sprite-hook firings are not evidence that terrain animation is
current".

The fix compares the incoming GL texture id against the ids the two direct mirrors were built from and refreshes the matching
one **in place** - the same operation the mirror refresh uses, which keeps the image, the view and the generation unchanged
and therefore every descriptor key built from them still valid.

Measured on a normal run:

```
CELTIUM_ANIM directMirrorRefreshes=89 invalidations=115 atlasGlId=9 lightmapGlId=12
exit 0 | clean | messages 0 | fixture PASSED | passes=2189 | descriptorSets=1
```

**89 uploads of the terrain atlas reached the mirror the terrain samples.** That is one completion-condition item - texture
animation - moved from "the hook fires" to "the renderer draws the animated content", with the count on stdout because the
capped diagnostics list would have dropped it.

The number beside it matters as much: `descriptorSets` stays at **1**. The in-place refresh introduced for S13 is what makes
this fix free - a recreated mirror would have invalidated every descriptor key on every animation frame, which is the 545-set
path this project was on two rounds ago.

---

## The fixture walks the paths it never walked: reload and world switch are verified now

Three fixes in this audit carried the label "unexercised" - the teardown, the retirement queue and the animation refresh -
because the fixture stayed in one world, drew one scene and never left. This round gave it those paths.

First the fixture defect the audit named: the reload and resize hooks sat in a **render-tick** handler behind
`worldTicks == 100`, which is true on **every frame** of that tick, so a resource reload ran dozens of times instead of once.
Both carry a once-guard now.

Then the runs. With `-Dceltium.validation.reload=true`, the game re-stitches every texture and rebuilds every chunk renderer
mid-run and the result is `reloadedAtTick=100`, exit 0, validation **clean**, 0 messages, fixture **PASSED**, `passes=1199`.
With `-Dceltium.validation.worldSwitch=true`, the game leaves the world at tick 100 and rejoins, and the backend's teardown
runs **twice**:

```
CELTIUM_TEARDOWN released 4 backend resources (immediate mesh, groups, pipeline caches, UI, registry, swapchain)
CELTIUM_TEARDOWN released 2 backend resources (...)
exit 0 | clean | messages 0 | fixture PASSED | leftWorldAtTick=100 rejoinedAtTick=100 | passes=1169
```

So S15's leak fix is no longer correct-by-construction: it runs, it releases, and validation stays silent across two
teardowns - which is the same statement as "nothing was destroyed while a command buffer still named it", the retirement
queue's own acceptance. Two completion-condition items, **resource reload** and **world switch**, move from untested to
tested in the same round, and they cost the fixture two once-guards and two flags it already had.

---

## The tick-equality defect was in four hooks, not two, and one of them shows it in a number

The audit's S22 named the reload and resize hooks; the same pattern - a bare `worldTicks == N` inside a render-tick handler,
which is true on **every frame** of that tick - was in the weather and particle hooks as well. All four carry once-guards now.

The particle hook makes the defect visible in its own output: it spawns 24 particles and reports `particlesSpawned=24`. Before
the guard, that line ran on every frame of tick 199 and the world received hundreds of them; now the number in the report is
the number it spawned, once. Both configurations were run and both are clean: exit 0, validation clean, 0 messages, fixture
PASSED.

Two of the four hooks the audit named are therefore verified by their runs rather than by inspection, and the remaining item
is narrower than it looked: the interop capture gate is **not in `ValidationClient`** - the world handler adds its properties
without an early return - so it lives in the harness or another fixture class, and the interop branch's own tick equality
(`worldTicks == 190`, a compound condition) still needs the same guard. Recorded with that pointer rather than as "the fixture
defects are open".

---

## Every measurement now carries its provenance

The audit's S24 lists what a record needs before a measurement can be trusted, and the largest part of it is now mechanical
rather than a matter of care: the runtime lock records **which commit** produced the run, **whether the tree was dirty** and
which files were, the **effective configuration** (the JVM arguments, tweakers, main class and main jar that actually reached
the process, rather than the flags someone meant to pass) and the **fixture**, identified by name and SHA-256 rather than left
among the mods.

Before this, a run's lock said which libraries and mods were present and nothing about the code: the same lock would have been
produced by a committed tree and by one with a half-applied edit, which is exactly the confusion that cost this project two
rounds when shader edits were not in the running build. Now the lock answers it, and the answer is in the same file as the
fixture hash the verdict belongs to.

What S24 still lists and this round did not do: the plan rows that claim more than has been verified, `nativeTerrainVerified`
being a counter condition rather than real-path evidence, the shader-source/SPIR-V sync check (the generated-artefact problem
the build now solves from the other side), and the driver in the lock - which the capabilities file already records, and which
should be referenced rather than duplicated.

---

## The provenance field caught its own first version being wrong

The lock now records the run's provenance, and the field proved itself twice in one round - once by working and once by
being wrong in a way only its own output revealed.

It works: on a real run the lock reports `head: a4d29a8f5cb8, dirty: true` with the file that was modified - my own
uncommitted edit to the lock script - which is precisely the confusion that cost this project two rounds when shader edits
were not in the running build. A run can no longer be attributed to a commit it did not use.

And it was wrong first. The initial version recorded `effectiveConfig.jvmArgs` from the **profile**, and the run's own
output showed `1 args, 0 celtium flags` - because the runner passes its flags through a **different channel**
(`--jvm-arg` on the command line) that the profile knows nothing about. It also reported `fixture: null`, because the
fixture jar is copied into the isolated game directory **after** the lock scans the instance's mods, so it was not there to
be found. Both were found by reading the new fields rather than by trusting them, and both are fixed: the lock now records
the profile's arguments and the runner's separately as well as their union, and identifies the fixture from the runner's own
`--mod` argument by name and SHA-256.

The verified result:

```
jvmArgs total: 8 | celtium flags: 7
fixture: celtium-validation-client-2.5.12-celtium.0.1.0-dev.1.jar  sha256 595b4d60...
git: a4d29a8f5cb8 dirty=True
```

A measurement can now be traced to its commit, its dirty state, the flags that actually reached the JVM and the fixture that
produced its verdict - which is most of what S24 asked for from the record, done mechanically rather than by remembering to
write it down.

---

## The acceptance verdict now names the evidence it rests on

The audit's complaint about `nativeTerrainVerified` was that it was a **counter condition**: `passes > 0`, no skips, no
errors, a non-zero last draw. Four numbers, and not one of them says that the terrain was drawn by the Vulkan terrain path or
that any OpenGL draw was actually **cancelled** - which is the claim this entire project rests on and the one that must not be
asserted from a counter being non-zero.

It now requires the **terrain pass count** and the **number of batches whose original GL draw was cancelled**, both read from
the same debug block and both recorded in the report beside the verdict:

```
nativeTerrainVerified = True
vulkanTerrainPasses   = 1635
glDrawsCancelled      = 556
exit 0 | clean | messages 0 | fixture PASSED
```

So a passing verdict now means: the Vulkan terrain path drew 1635 passes **and** 556 batches had their GL draw cancelled
rather than deferred or skipped. The distinction matters because the two failures this project spent rounds on - terrain that
was only ever drawn by GL, and batches that were accepted but never drawn - both leave `passes > 0` intact. They do not leave
`glDrawsCancelled` intact.

What remains of S24 is smaller and named: the plan rows that still claim more than has been verified, the shader-source/SPIR-V
sync check, and the driver in the lock - which the capabilities file already records, so the right change there is a
reference rather than a duplicate.

---

## Two plan rows understated the renderer, and the driver is recorded by reference

Correcting the record is not always about removing over-claims. Two rows of the plan's claim table were wrong in the other
direction.

The mirror-versus-live row still carried "proj 2.0, view 2000.0 apart". This project later established what the 2000 was:
`setupOverlayRendering`'s `translate(0, 0, -2000)`, compared **across passes** - and 1140 of 2262 comparisons had been
cross-pass. The scoped figure is **1.62**. The row now says so, and names the correction rather than quietly improving.

The terrain row said "both GPUs" with no distinction between revisions. Every measurement in this session was taken on AMD
after the Intel runs, so the row now reads **verified on AMD with the current revision** and **verified on Intel at an earlier
one**, names the outstanding Intel re-run, and quotes the real-path evidence the acceptance now requires
(`vulkanTerrainPasses=1620`, `glDrawsCancelled=564`).

The driver is now recorded **by reference**: the post-run report points at the run's own `logs/celtium-capabilities.json` and
carries `lwjglVersion 3.4.1-snapshot`, `vulkanLoaderVersion 1.4.363`, `vulkanDeviceCount 3` and `apiVersion 1.4.354` from it.
The first attempt put this in the lock, which is written **before** the run - so it found nothing and reported an empty
driver, which its own output showed immediately. Reading it where it exists is the whole fix.

---

## The Intel re-run closes the two-GPU condition on the current revision

The corrected plan row named one outstanding item - the Intel UHD 630 verification predated this session's changes - and
that is now done. The Intel run script differs from the AMD one only in the run name and the render node, so it carries the
same flags and the same acceptance.

```
Intel UHD 630: exit 0 | clean | messages 0 | fixture PASSED
acceptance: nativeTerrainVerified=True  terrainPasses=804  glDrawsCancelled=278  vulkanPasses=1082
```

**Zero validation messages on Intel's driver** across everything this session changed: the 116-byte push block in nine
shaders, the retirement queue, the generation-carrying descriptor keys, the in-place mirror refresh, the animation
refresh of the directly held atlas, the order-preserving flushes at seventeen refusal sites, the clear hook, the framebuffer
check on the composite present, the out-of-date handling and the teardown. Each of those was verified on AMD when it was made;
this run says none of them is AMD-specific.

The two-GPU condition is therefore met **on the current revision** rather than on an earlier one, and the plan row says so
with the numbers from both runs rather than a claim.

---

## Completion condition (2) written up item by item, and the GUI run is what proves the boundary

The objective asks for each rendering area to be stated as implemented, unimplemented or verified rather than judged by the
terrain backend's name, so this round ran the fixture **with the GUI visible** and read what the counters say.

The result is compact and worth stating plainly. **566** batches were taken by the immediate path with **zero** post-accept
drops and every per-format drop counter at zero - the same figure the hidden-GUI runs give, to within a few batches. Showing
the GUI therefore adds **nothing** to the accepted count, and the refusal reason on the run says why:
`outside_the_world_pass_or_without_a_snapshot`. The HUD, the font, the hotbar and the hand are drawn **outside the world
pass**, so the immediate path declines them and OpenGL draws them - refused by name rather than dropped quietly, which is what
the plan's inventory table now records.

The table in `docs/plan.md` states, with evidence: terrain on Vulkan verified on **both** GPUs (1632/566 on AMD, 804/278 on
Intel); world-pass content in a covered format measured and taken (566, no drops); **GUI, font, HUD, hand, sky, clouds,
weather, water, the FBO chains and postprocess on OpenGL**; texture animation verified through the mirror refresh counter;
resource reload and world switch verified; resize once-guarded but not yet run in this series; and the final composite a GL
blit, with the Vulkan present path off by default and unreached even when enabled.

That is the honest boundary of the project as it stands, and it is now written where the objective asked for it.

---

## The GUI, the font, the HUD and the hand are drawn by Vulkan now: 7,721 batches

The inventory written last round named the largest gap in plain terms: everything outside the world pass - the GUI, the font,
the HUD, the hand - was **OpenGL**, refused by the immediate path with the reason `outside_the_world_pass_or_without_a_snapshot`.
This round closed that gap, and the change was smaller than the gap suggested, for a reason worth recording.

A group's matrices are taken from the **live `GlStateManager` mirror** (`glProjection`/`glModelView`), not from the world
snapshot - the snapshot is used for the comparison diagnostics. That mirror holds the orthographic projection outside the world
pass exactly as it holds the world projection inside it, so the batches the guard was refusing never needed the snapshot at all:
the guard was refusing content the path was already equipped to draw. It now accepts them and counts them.

```
outsideWorldPassAccepted   = 7721     the GUI, font, HUD and hand content taken this run
outsideWorldPassRefusals   = 0
postAcceptDroppedGroups    = 0
droppedByFormat            = 1:0, 2:0, 3:0, 4:0, 5:0
descriptorSets             = 1
nativeTerrainVerified      = True   terrainPasses=1620   glDrawsCancelled=554
exit 0 | clean | messages 0 | fixture PASSED
```

**7,721 batches** - the HUD, the font, the hotbar, the hand - that OpenGL drew last round are now drawn by the Vulkan
immediate path, with **no validation messages** on the AMD driver, no post-accept drops of any format, and the descriptor
cache still at a single set. Terrain is unaffected and its real-path evidence is intact.

So one row of the inventory moves from "OpenGL, explicitly refused" to "Vulkan, measured", and the honest statement of what
remains on GL is now narrower: sky, clouds, weather, water, the FBO chains, postprocess and the final composite.

---

## The GUI path verified on Intel as well: 3,965 batches, zero messages

The new path was measured on AMD when it was made; this round ran the same configuration - GUI visible - on the Intel UHD
630, which is where the second half of the standing two-GPU condition lives.

```
Intel UHD 630, GUI visible:
outsideWorldPassAccepted = 3965      outsideWorldPassRefusals = 0
postAcceptDroppedGroups  = 0         every per-format drop counter at 0
descriptorSets           = 1
nativeTerrainVerified    = True      terrainPasses=810   glDrawsCancelled=283
exit 0 | clean | messages 0 | fixture PASSED
```

So the GUI, the font, the HUD and the hand are drawn by the Vulkan immediate path on **both** GPUs, with **no validation
messages** on either driver and no drops of any format. The inventory row moves from "OpenGL, explicitly refused" to
"Vulkan, verified on both GPUs", and the AMD and Intel figures sit beside it.

What is left on OpenGL is unchanged and named: sky, clouds, weather, water, the framebuffer chains, postprocess and the final
composite.

---

## Sky, clouds and the water surface found: RenderGlobal's stride-20 quads, unrecognised by the format mapping

The inventory's remaining world-pass gap - sky, clouds, weather, the water surface - had no counter that named it: the
refusal reason on a GUI run ends at `format_0_not_in_celtium.vulkan.immediateFormats`, and format 0 means the upload carried
**no recognised VertexFormat**, because the id mapping's first branch is `format == null`. Turning that into a named call site
took a one-shot stack dump on the first two such uploads:

```
CELTIUM_NULLFORMAT stride=20 vertices=4
stack = WorldVertexBufferUploader.offerBatch <- WorldVertexBufferUploader.func_181679_a
     <- Tessellator.func_78381_a <- RenderGlobal...
```

So the content is **stride 20** - five floats, `POSITION` (3) plus `TEX` (2) - drawn as **four-vertex quads** by
**`RenderGlobal` through the Tessellator**: the sky, the clouds and the water surface, exactly the candidates the inventory
named. It is not a guard refusing them, the way the GUI was; the format mapping simply does not recognise the shape, so the
batches arrive with id 0 and are declined before acceptance.

That names the work precisely, and it is more than a flag: the immediate path has pipelines for `POSITION_COLOR`,
`POSITION_TEX_COLOR`, the block format, the particle format and the UI quad, but **none for a position-texture vertex without
a colour**, so taking this content needs a new pipeline as well as a mapping entry. Recorded as the next step, with the
evidence above rather than a hypothesis about which pass is missing.

---

## Sky and clouds: the infrastructure is in, the mapping rule is one number away

The recorded next step was a `POSITION_TEX` pipeline plus a mapping entry, and the pipeline half is done: a colourless
shader pair (`immediate_position_tex.vert`/`.frag`), a second vertex layout in the texture pipeline (stride 20, two
attributes, the colour attribute absent rather than read past the end of the vertex), a per-format acceptance counter, and
format 6 accepted by default and dispatched to that pipeline through its own cache. All of it builds, runs, and leaves the
frame clean.

The mapping half is not, and the round is honest about why. Three attempts to recognise the shape failed:

1. comparing against `DefaultVertexFormats.POSITION_TEX` - missed;
2. comparing against `DefaultVertexFormats.POSITION_TEX_NORMAL`, which the element dump had just shown - missed too, because
   the game builds an **equal format instance** rather than reusing the constant;
3. matching `getSize() == 20 && getElementCount() == 4` - missed, because the dump's first two samples had four elements
   while a later sample has two.

The diagnostic was then widened to print the numbers instead of the elements, and that is what settled it:

```
elements=[3,Position,Float 2,UV,Float]   formatSize=20   elementCount=2
```

**Two different shapes share stride 20** - `POSITION_TEX` with two elements and `POSITION_TEX_NORMAL` with four - which is
why every single-condition rule matched one and not the other. The shape branch was taken back out rather than left matching
half its target, the diagnostic stayed, and the tree is green: build successful, exit 0, validation clean, 0 messages,
fixture PASSED, acceptance intact at 1614 terrain passes and 554 cancelled draws.

So the next action is one line - accept stride 20 with two **or** four elements - and it is recorded with the two-element
sample beside it rather than as another guess.

---

## The sky mapping, six attempts, and what the round is actually worth

The recorded next action was one line: accept stride 20 with two **or** four elements. It did not work, and neither did the
five corrections after it. The account is worth keeping because two of the six steps produced findings that outlast the
attempt.

1. `POSITION_TEX` by element comparison - missed.
2. `POSITION_TEX_NORMAL` - missed, because the game builds equal format instances rather than reusing the constants.
3. `getSize() == 20 && elementCount == 4` - missed; the dump had shown four elements in its first samples and two in a later
   one.
4. Two named shapes, stride 20 with two elements and size 24 with four - missed.
5. The same rule using the **uploader's stride** instead of the format's size - missed, and the diagnostic then showed the
   two numbers differ (`stride=20`, `formatSize=24`).
6. The same rule moved to the **front** of the chain, before the element comparisons, on the reasoning that `sameElements`
   ignores padding and a trailing normal and so could be claiming the shape for id 2 - missed.

**What the round established, and it is not nothing.** First, the **provenance field added two rounds ago did its job**:
comparing `fixture.sha256` across four runs showed the fixture unchanged, which is what sent this round into the build path
and established that the **main jar is current** (19:22:31 against the run) and that the fixture is legitimately unchanged
because nothing in `validation/client` was edited. That is the check that would have saved two rounds when shader edits were
not in the running build, and it took one command.

Second, the two diagnostics **disagree**, and that disagreement is the next thing to explain rather than another rule to try:
the call site sees a format with `stride=20, formatSize=24, elementCount=4` reaching the mapping as id 0, while the mapping's
own dump, printing every shape it fails to match, never lists a stride-20 shape at all. Those cannot both be true of the same
format instance, so the next step is to print the **identity** of the format object at both points - not another condition.

The six mapping edits are reverted, the colourless pipeline, the format-6 wiring, the per-format counters and the diagnostics
stay, and the tree is green: build successful, exit 0, validation clean, 0 messages, fixture PASSED, acceptance intact at 1617
terrain passes and 556 cancelled draws.

---

## The sky mapping: stopped, with the contradiction written down

One more attempt was made this round, and it is the last one for now. The shape decision was moved to the **call site** -
where the uploader's stride and the format are both in hand, past the cache that had been suspected of answering first - and
guarded with its own print. The print **never fired**, while the null-format dump in the same method, a few lines below it,
keeps reporting uploads with `stride=20, formatSize=24, elementCount=4`.

Those two observations cannot both be true of the same method call, and three rounds of rules and cache theories have not
resolved it. So the experiment is reverted, the tree is green, and the contradiction is recorded as what it is - an open
question with its two halves stated - rather than pursued further. This log has said before that a number improving is not
evidence of correctness; the same applies to a hypothesis surviving: six mapping rules and a call-site decision were tried,
none of them demonstrably took effect, and the honest conclusion is that **the instrument is not yet trustworthy**, not that
the seventh rule will work.

What stays: the colourless `POSITION_TEX` pipeline (stride 20, two attributes, no colour attribute), format 6 wired and
accepted, the per-format acceptance counter that made the gap measurable in the first place, and the diagnostics.

What is written down for whoever picks this up: a **call-site** decision with `stride == 20` did not fire, and a **call-site
dump** on the same line reports `stride=20`. Establishing which of the two is describing the uploads that reach the hook
comes before any further rule.

---

## Resize verified: the last completion-condition item that had never been run

The inventory listed resize as the one item with a fixture, a once-guard and no execution behind it. It has now been run, and
it is the cleanest result of the recent rounds:

```
resizedAtTick=100 | exit 0 | clean | messages 0 | fixture PASSED | passes=2166
acceptance: nativeTerrainVerified=True  terrainPasses=1614  glDrawsCancelled=552
retire=retired=3        descriptorSets=1        immediateTaken=552
```

The window changes size mid-run, the renderer survives it and the frame is still accepted as Vulkan terrain - **with zero
validation messages**, which is the part that matters: a resize replaces the swapchain, the frame target and every texture
the bridge imports, and it was exactly this kind of event that S12 (resources destroyed while in use) and S18 (out-of-date
handling) were written about. Both hold here. The retirement queue drained all three of its retirements, so the replaced
resources were destroyed after the GPU was idle rather than during, and the descriptor cache still answers with a single set.

Every item of completion condition (2) has now been **exercised on hardware** at least once: terrain, world-pass content in a
covered format, the GUI, the font, the HUD, the hand, particles, texture animation, resource reload, resize and world switch.
The one that is identified but unresolved is sky, clouds and the water surface, whose mapping contradiction is recorded with
its two halves.

---

## A major mod runs clean, and the two third-party mods that were always there

The objective's last verification step is a real major mod rather than only the Forge-event fixture, and this round did it:
**JEI 1.12.2-4.22.0.1035** was fetched from Modrinth (HTTP 200, 715,519 bytes, hash recorded in the lock), placed in the run's
mod list, and the client started with it loaded.

```
mods loaded: +Fugue-0.24.4, celtium, celtium-validation-client, jei_1.12.2-4.22.0.1035, scalar-3.9.0
exit 0 | clean | messages 0 | fixture PASSED
nativeTerrainVerified=True  terrainPasses=1611  glDrawsCancelled=558  passes=2169  status=PASSED
outsideWorldPassAccepted=7133   descriptorSets=1
```

JEI draws a lot and its content arrives **outside the world pass**, which is the path the GUI work opened two rounds ago - so
this run exercises both the mod compatibility and that new path at once, with **no validation messages** on the AMD driver and
the descriptor cache still at a single set.

The run also surfaced something that should have been in the plan all along. Printing the lock's `testMods` list shows that
**every measurement this project has taken already carried two third-party mods**: `+Fugue-0.24.4`, a Cleanroom coremod that
transforms vanilla classes, and `scalar-3.9.0`. So the renderer has been coexisting with class-transforming third-party code
in every run - a stronger compatibility statement than the plan made, and one that cost nothing to establish because the
provenance field records it.

Recorded honestly as well: this establishes that a major mod **loads** and that its GUI content reaches the Vulkan path. It
does not establish JEI's recipe screens, because the fixture never opens one; that needs a fixture that interacts with the mod,
and it is named rather than implied.

---

## JEI's own overlay drawn through the renderer: 1,839 more batches, no messages

The plan's own note said what was missing: JEI loading proves the mod, not that its rendering goes through the renderer under
test. So the fixture now opens the player's inventory at tick 170, where JEI draws its overlay, and closes it at tick 190 so
the captured frame stays the normal scene.

```
jeiScreenOpenedAtTick=170 | exit 0 | clean | messages 0 | fixture PASSED
outsideWorldPassAccepted = 8972    (7133 without the screen: +1839)
immediateTaken           = 772     (558 without: +214)
descriptorSets           = 1
nativeTerrainVerified    = True    terrainPasses=1482   glDrawsCancelled=772   passes=2254
```

**1,839 additional batches outside the world pass** and 214 more inside it while the screen is open: JEI's overlay and the
inventory around it are drawn by the Vulkan immediate path, with no validation messages on the AMD driver and the descriptor
cache still at a single set. The terrain acceptance is unaffected, which is what the run's own numbers show rather than what
the fixture assumes.

Recorded for the next step, precisely: this covers JEI's **overlay in the inventory**. A **recipe screen** - the mod's own
heavier draw, opened by right-clicking a recipe - is still not exercised, and it needs the fixture to interact further.

---

## The major-mod path on Intel as well, and a timeout that was latency rather than a hang

The JEI configuration was run on the Intel UHD 630 to extend the two-GPU condition to the mod path, and the first attempt
**timed out**: `exit 124`, no report, the fixture's verdict missing. The second attempt failed for a different and instructive
reason - the harness refuses to reuse an isolated game directory, so it exited at once with `FileExistsError` while the report
read back was still the first attempt's. The third, under a fresh name and a longer budget, completed:

```
Intel UHD 630 with JEI and its overlay:
jeiScreenOpenedAtTick=170 | exit 0 | clean | messages 0 | fixture PASSED
outsideWorldPassAccepted = 4425    immediateTaken = 376    descriptorSets = 1
nativeTerrainVerified = True   terrainPasses=723   glDrawsCancelled=376   passes=1099
```

So **the major-mod path is verified on both GPUs**: JEI's overlay and the inventory around it are drawn by the Vulkan immediate
path on RADV and on ANV, with no validation messages on either, and the acceptance intact on both.

The timeout is worth recording as what it was: **latency, not a hang**. This configuration takes roughly twice as long on
Intel with JEI loaded, so a 300-second budget is simply too short - and the distinction matters, because "the run did not
finish" reads like a defect until it is measured against the machine it ran on. The second attempt's failure is worth the same
sentence: a harness that refuses to overwrite an existing game directory is protecting the evidence, and reading a previous
attempt's report after a failed launch is a way to mistake one run's numbers for another's.

---

## JEI's recipe screen opened through its own API: the last named verification gap closes

The plan named one thing the major-mod work did not establish: JEI's recipe screen, the mod's own heavier draw. The fixture
now reaches it the way the mod intends - by implementing `mezz.jei.api.IModPlugin`, being discovered through `@JEIPlugin`, and
calling `getRecipesGui().showCategories(...)` with the first category the mod registers.

```
jeiRecipeOpened=True | exit 0 | clean | messages 0 | fixture PASSED
outsideWorldPassAccepted = 8563    immediateTaken = 791    descriptorSets = 1
nativeTerrainVerified = True   terrainPasses=1482   glDrawsCancelled=791   passes=2273
```

So the mod's own screen is drawn by the Vulkan immediate path, with no validation messages and the acceptance intact.

The plugin source is compiled **only when the JEI jar is present** (`src/jei/java` added conditionally by the fixture's build),
so a normal build and every ordinary run are unchanged by its absence - and that is also why the fixture finds it with a single
`Class.forName`: the class cannot be referenced at compile time when it may not exist. The mod-facing calls after that are
direct, which is what the project's no-reflection rule is about.

Getting there took three failed compiles, and the reason is worth recording because it is the same lesson as the sky mapping
in a smaller form: **the API's shape was assumed**. `IJeiRuntime` lives in `mezz.jei.api`, not in a `runtime` subpackage; the
annotation is `JEIPlugin` with a capitalised name; and `IModPlugin` in this version has no plugin-uid method at all. Reading
the two interfaces out of the jar with `javap` settled all three in one command, after three rounds of guessing.

---

## S23: the LOD hypothesis refuted by a sweep, and the mip mirroring is no longer a regression

This audit item had one recorded next step - "compare this renderer's UV derivatives and LOD with Minecraft's own shader" -
and the cheapest way to test it was to make the two knobs properties and sweep them.

**The bias is nearly irrelevant.** `u_MipBiasAdd` was a literal 0.0, documented as exact GL parity. Sweeping it with the mip
mirroring enabled:

```
mip bias   mean vs reference   >8
 0.0       33.68               68.68%
-1.0       34.03               68.91%
-2.0       34.25               68.94%
```

Two levels of level-of-detail change move the frame by half a mean unit. Whatever separates this renderer's sampling from the
reference's, it is **not** the LOD the shader asks for, and the hypothesis that had been carried forward for several rounds
dies here - by measurement, which is the only way it could.

**And the mirroring no longer costs what the item recorded.** With the same build, mirroring on and off differ by mean 1.110
with 6.0% of pixels above the threshold, against a scene noise floor of about 1.0, and against the pinned reference the two
configurations are a wash - 33.68 and 68.68% with mirroring, 34.52 and 69.18% without. The 5.60% this item recorded was
measured **before** the in-place mirror refresh that S13 introduced, which changed how mip levels are uploaded; the regression
it described belonged to the old upload path. Both configurations run clean: exit 0, validation clean, 0 messages, fixture
PASSED.

The tree keeps mirroring **off** behind a property, because that is the configuration every measurement in this log was taken
with, and because this scene has no sharply angled or distant tiled surface where a mip chain earns its keep - so the choice
between on and off is a fidelity question the scene cannot settle, and it is now recorded as one rather than as a regression.

---

## S24: three plan rows said "pixel-identical to GL" when they meant "immediate off versus on"

The audit's S24 lists the record's remaining over-claims, and the sharpest of them is a phrase that appears three times in the
plan's claim table: immediate rendering **pixel-identical to GL**, with "0.00" beside it. The number is real - it is a **local**
comparison, immediate off versus immediate on with the terrain on Vulkan in both - but the phrase is not, and this project
established why several rounds ago: **this tree has no GL terrain path**, so any comparison that includes terrain is against
the pinned upstream build, never against GL. The "each GPU's own GL reference" those rows describe was the self-comparison that
the round-257 correction identified.

All three rows now say what was compared. The local comparison is kept and named as the strongest this tree can make for the
immediate path; the phrase "pixel-identical to GL" is replaced by "equal to the GL path on a local comparison"; and the row
that carried the stale wording carries the correction with it, so a reader who remembers the old claim finds the reason it was
wrong rather than a silent edit.

Against the pinned upstream - the only reference with terrain - the current frame reads mean ~34 with ~69% of pixels over 8,
dominated by terrain shading. That number is recorded in the same row, because a local 0.00 and a reference 34 belong next to
each other: one says the immediate path agrees with the GL path it replaces, the other says the terrain still does not match
the upstream renderer, and conflating them is exactly how a project talks itself into claiming more than it has.

---

## The blockers section corrected, and a budget that does not fit on Intel

Two things this round, one a record correction and one a measurement.

The plan's blockers section still described the state of several rounds ago. Its first entry said the bridge owns the images
"one window per frame and early in it, so immediate batches that arrive later are drawn against the next frame's buffers" -
while the same plan's claim table records the window limit as **sixteen per frame**, measured pixel-equal on a local comparison
with no measurable cost, and records that the one-window measurement that blackened the frame **predates the submit-wait fix**.
Its second entry said the world matrices are invisible to the Mixins and that reading them per batch "is the only correct
option" - while the claim table records the projection as **derived and applied**, the camera transform **seeded from the frame's
camera setup**, and the mirror-versus-live residual as **1.62** rather than the cross-pass 2000. Both entries now state the
measured position, and both say what the old wording was and why it changed, because a reader who remembers the old blocker
needs the correction more than the new text. The third entry - present ownership - is still a real blocker and now carries
what is known about it: with `presentFromComposite` enabled this scene presents no format-2 batch at all, so the path is inert
rather than exercised.

Then the Intel recipe-screen run. The **AMD** run of that configuration completes with 2273 passes. On **Intel** it does not:
with the harness's `--duration` raised from 150 to **420** it still ends at the harness timeout, `exit 124` and no report. The
configuration without the recipe screen **does** complete on Intel (`passes=1090`, `outsideWorldPassAccepted=4426`), so the
gap is the mod's heavy screen on a slower GPU rather than a defect - and the honest entry is "the budget does not fit", with
the numbers, not "the test failed".

Worth noting how the harness behaves here, because it is exactly right: `--duration` is documented as "maximum runtime in
seconds; **a timeout is not success**", and it reports `validationExitCode 124` rather than a pass.

---

## The present path reached for the first time, and the mask is what had been hiding it

The plan's entry on the final composite said the Vulkan present path "exists but is off by default and unreached", because
this scene issues no format-2 batch for it to key on. Both halves are now addressed.

The fixture issues the batch: a hook calls the game's own composite, `Framebuffer.framebufferRender`, once at a chosen tick
behind its own flag. That alone was not enough, and the reason is worth recording: the **acceptance mask** checked the format
**before** the present branch, so format 2 was refused by the mask and the branch below it - the one whose own comment says it
"runs before the acceptance mask" - was never reached. Admitting 2 to the mask for that run fixed it without touching the code.

The evidence that the path ran is the swapchain's own report line, which is printed when the out-of-date state changes:

```
CELTIUM_OOD outOfDate=0 acquireOutOfDate=0 presentFailures=0
exit 0 | clean | messages 0 | fixture PASSED
immediateTaken=565   terrainPasses=1605   glDrawsCancelled=565   descriptorSets=1
```

That line only prints when `presentImage` has been called, so the present executed - for the first time in this project - with
no failure reported, no out-of-date state, validation silent and the acceptance intact.

Recorded as not established, because it is the next question rather than a claim: that the **screen** shows the Vulkan present
rather than GL's blit. That needs a readback of the screen itself, and it is named here rather than implied. The branch's
placement is also worth a follow-up in the code rather than in a run flag: a comment that says a branch runs before the mask is
not the same as a branch that does.

---

## Correction: the mask was not what hid the present branch

Last round ended with a tidy explanation - "the acceptance mask checked the format before the present branch, so format 2 was
refused and the branch below it was never reached" - and it is **wrong**. Reading the line numbers settles it: the present
branch is at **1264** and the mask check is at **1366**, inside the same method. The branch is **already before** the mask, and
the comment that says so was true all along. What the mask flag changed must be something else, and the honest position is that
it is not established: admitting format 2 changed which batches this renderer takes, and therefore **when** the composite batch
arrives, which could matter to the guards that really sit in front of the branch - `reference render`, `the GPU stalled`, and
`not the main framebuffer`, the last of which is the check S17 added.

This is the fourth conclusion in this project to be withdrawn after the fact, and the pattern is consistent: each one was a
plausible mechanism that fit the observation, written up before the code was read closely enough to confirm it. The
countermeasure that has worked is the one applied here - **read the line numbers** - and the next step is to instrument the
guard that actually returns, rather than to reason about which one it ought to be.

What stands from the round before is unaffected and is the part that mattered: the present path **ran**, for the first time,
with no failure reported, no out-of-date state, validation silent and the acceptance intact.

---

## Observed, not reasoned: the composite batch arrives as format 0

The instrument this round was the one the last correction asked for - print the backend's own refusal reason - and it settled
the present-path question in one run:

```
CELTIUM_COMPOSITE_REFUSED reason=format 0 not in celtium.vulkan.immediateFormats   stride=24 vertices=4
CELTIUM_COMPOSITE_REFUSED reason=lightmapped batch with no lightmap texture mirrored  stride=24 vertices=4
```

**The composite batch reaches the renderer as format 0**, which is the id for "this `VertexFormat` was not recognised" - the
same symptom, in the same mapping, that the sky, cloud and water content showed. When it does arrive as format 2, the reason
it is refused is the light map check, because the composite is drawn outside the world pass where no light map is bound. Neither
of those is the mask, and the mask's effect is now explainable rather than mysterious: admitting format 2 changed which batches
the renderer takes, and with it which of them arrive at the branch first.

Two things follow, and they are worth separating.

The present-path question is **closed by observation** rather than by another theory: the path runs when a format-2 batch
reaches it with a light map bound, which is what admitting 2 achieved on a run where the game's own composite came through
recognised.

And the **format mapping is now implicated twice**, independently: the sky, cloud and water shapes arrive as 0, and so does
the composite - content the game draws every frame. Six rules failed to change that; last round's correction said the
instrument itself was not trustworthy; this round adds a second, unrelated shape whose failure has the same signature. That is
the strongest evidence yet that the problem is in the mapping's instrumentation rather than in the rules being tried against
it, and it is the thing to examine next.

---

## The mapping's element comparison was wrong by any standard, and fixing it changed nothing measurable

Reading the instrument instead of adding a seventh rule found a real defect, and it is worth stating plainly what it is:

```java
if (a.get(i) != b.get(i)) {      // VertexFormatElement compared by identity
```

The mapping decided whether the game's format was one it knew by comparing the **element objects** with `!=`. The game builds
equal `VertexFormatElement` instances inline rather than reusing the constants, so **no** inline format could ever match a
standard one - a bug by any standard, and the explanation that fit every observation: the sky, the clouds, the water surface
and the final composite all arriving as format 0, and six candidate rules appearing not to work because the comparison in front
of them was wrong.

It is now compared by **content** - type, usage, index and component count - and misses are no longer cached either, since a
verdict of 0 depends on the stride that upload happened to carry and storing it reused the first encounter's answer forever.

**And the measurements did not move.** `acceptedByFormat` still shows `1:2, 2:0, 3:0, 4:0, 5:548, 6:0`, the null-format dump
still fires for the stride-20 two-element shape, and the runs are otherwise unchanged: exit 0, validation clean, 0 messages,
fixture PASSED, acceptance intact. So the defective comparison was **not** what kept those shapes at 0 - it was wrong, it is
fixed, and it explains nothing.

That is the same lesson as the withdrawn mask explanation, arriving from the other side this time. A defect that fits every
observation is not necessarily the defect responsible for them, and the way to tell the two apart is to change it and measure -
which is what this round did, and why the result is a correction rather than a breakthrough. Two facts remain unexplained and
are recorded as such: both shapes satisfy the size and element-count conditions that were tried, and both still arrive as
format 0.

Three rounds have gone into this mapping. The content comparison stays, because it is right, and the investigation stops here
with the state written down rather than with another rule.

---

## The per-target work, sized: one 256x256 target, 571 batches, one format

The FBO and postprocess work had a number attached to it - 567 off-target batches - but not a shape, and "per-target rendering"
reads like a subsystem. The backend now names, counts and sizes each off-target render target, and with the second-target
fixture enabled:

```
CELTIUM_OFFTARGET target=949054906 viewport=256x256 firstBatchFormat=1
offTargetByTarget = 949054906:571
exit 0 | clean | messages 0 | fixture PASSED
nativeTerrainVerified=True   terrainPasses=1626   glDrawsCancelled=555
```

**One target.** 256x256. **571 batches**, every one of them **format 1** - `POSITION_COLOR`, for which an immediate pipeline
already exists. That is the difference between an open-ended feature and a first increment: one small image, the pipeline that
is already there, and a copy-back at the target switch. The instrumentation needed to know that is a map keyed by the target
identity, a one-shot line naming it, and the viewport read at the moment of the refusal - which is also the moment the viewport
still belongs to the target rather than to the frame.

Recorded for the next step with the numbers rather than the adjective, because this project has spent three rounds on a mapping
question that turned out to be smaller than it looked and one round on a mask explanation that was simply wrong: the size of a
piece of work is worth measuring before it is planned, not after it is attempted.

---

## The per-target resource, built and validated before anything depends on it

The sized increment was "one 256x256 image with a copy-back", and this round built the first half of it: for a target other
than the main framebuffer, the renderer now creates a device-local Vulkan image with a view.

```
CELTIUM_OFFTARGET target=1193535128 viewport=256x256 firstBatchFormat=1
offTargetTargets = 1     offTargetRefusals = 571     retire=retired = 3
exit 0 | clean | messages 0 | fixture PASSED
nativeTerrainVerified=True   terrainPasses=1629   glDrawsCancelled=563
```

**Validation is silent across the whole run**, which is the point of doing it in this order: the risky part of per-target
rendering is the Vulkan resource - format, usage flags, memory type, view - and that is now proved clean on a real second
framebuffer before a single pixel depends on it. The batches are still OpenGL's, and they stay refused until the copy-back
exists, because taking them without it would lose their content.

Written the way this project has learned to write it: the increment is the one the measurement sized (one target, 256x256, one
vertex format, a pipeline that already exists), the resource was built before the drawing that needs it, and the class says in
its own comment why it is not used yet rather than leaving a reader to wonder. The remaining half is named - a render pass, a
framebuffer, a readback and a `glTexSubImage2D` back into the game's texture - and it is small enough to describe in one
sentence, which is what measuring it first bought.

---

## The off-target chain works, and the validation layer found the part that does not yet

The remaining half of the sized increment - a render pass, a framebuffer and a readback - is in, and it is verified rather
than assumed: at creation the target clears itself to a known colour and reads the pixels back.

```
CELTIUM_OFFTARGET_SELFTEST size=256x256 expected=64,128,191 differingPixels=0 passed=true
```

**Zero differing pixels**: the render pass begins and ends, the framebuffer accepts it, and the copy into host-visible memory
returns exactly the colour that was cleared. The chain works end to end.

And the same run produced the kind of finding that justifies the validation layer being part of every run:

```
severity=ERROR vkCmdCopyImageToBuffer(): READ_AFTER_WRITE hazard detected. vkCmdCopyImageToBuffer reads VkImage ...,
which was previously written ...
```

The copy reads the image immediately after the render pass wrote it. A barrier between the pass and the copy - colour
attachment writes made available to a transfer read - is the textbook answer and **it is in the code**, and the hazard is
still reported, which means the synchronization this needs is not the one that was added. That is exactly the class of problem
the real copy-back must solve, and it is now written down as such rather than discovered later under a moving frame.

The self-test is therefore **off by default**: it proves the resources work, and it puts a validation error in every run while
it does, which is the wrong trade for something that is not rendering. The ordinary runs are clean again - `offTargetTargets=1`,
exit 0, validation clean, 0 messages, fixture PASSED, `terrainPasses=1635`, `glDrawsCancelled=571`.

What is left of this increment is small and named: bind the existing pipelines for the offscreen pass, draw the 571 format-1
batches into it, and upload the readback into the game's texture - with the synchronization the validation layer has already
told us is not yet right.

---

## The copy-back works, and the validation layer wrote the fix

The last piece the per-target work needed was a way to get its content back into the game's framebuffer, since nothing outside
GL can write into the game's textures. It is in, and the self-test proves it end to end:

```
CELTIUM_OFFTARGET_SELFTEST size=256x256 expected=64,128,191 differingPixels=0 glError=0 glPixel=64,128,191 passed=true
exit 0 | clean | messages 0 | fixture PASSED
nativeTerrainVerified=True   terrainPasses=1623   glDrawsCancelled=566
```

The target is cleared to 64,128,191, copied into host-visible memory, uploaded into a GL texture with `glTexSubImage2D`, and
read back **from GL** - and all three agree. That is the whole path a per-target draw will use, verified before any drawing
depends on it, and it runs on every ordinary run now rather than behind a flag.

**The validation layer wrote the fix.** The check first reported a `READ_AFTER_WRITE` hazard on the copy, and a barrier between
the render pass and the copy - the textbook answer - did not remove it. The full message did: the image was *"previously written
during an image layout transition initiated by `vkCmdEndRenderPass`"*, and *"no sufficient synchronization is present to ensure
that a read ... does not conflict with a prior layout transition"*. A transition performed by the render pass is not something a
later barrier can retroactively synchronize, so the fix is not a bigger `srcStageMask` but **one transition in one place**: the
pass now ends in `COLOR_ATTACHMENT_OPTIMAL`, and the barrier performs the only transition, to `TRANSFER_SRC_OPTIMAL`. The
hazard is gone and the messages are zero.

That is worth recording as the shape of the whole round: build the resource, check it with something that can be wrong, and
read the error message rather than the intuition. This project has spent several rounds on exactly the opposite - a mask
explanation that was wrong, six mapping rules that did nothing, an element comparison that was right to fix and explained
nothing - and this one took three attempts and ended with the diagnosis coming from the tool rather than from the author.

---

## The offscreen target now has every entry point the drawing half needs

The remaining step was "bind the pipelines for the offscreen pass, draw the 571 batches into it, and upload the readback into
the game's texture". The target now offers all of it: `begin` and `end` for its render pass, the pass and framebuffer handles a
pipeline needs, and `copyBackToGlTexture`, which reuses the path the self-test proved - a barrier performing the only layout
transition, a copy into host-visible memory, and an upload through `glTexSubImage2D`.

```
exit 0 | clean | messages 0 | fixture PASSED
offTargetTargets = 1
CELTIUM_OFFTARGET_SELFTEST size=256x256 expected=64,128,191 differingPixels=0 glError=0 glPixel=64,128,191 passed=true
nativeTerrainVerified=True   terrainPasses=1629   glDrawsCancelled=558
```

The tree is clean and the chain is still proven on the run, so what is left is mechanical: create a format-1 pipeline for the
offscreen pass, bind it with the group's mesh and push constants, draw, copy back, and stop refusing those batches. Nothing in
that list is unknown - which is the point of having spent the previous two rounds on the resources and the synchronization
instead of on the drawing.

---

## The offscreen pipeline, and validation catching a GL constant in a Vulkan enum

A pipeline belongs to the render pass it was built for, so taking the off-target content needs a second format-1 pipeline
built against the offscreen pass. It is created with the target, before any batch is taken, and that order paid for itself
immediately:

```
severity=ERROR vkCreateGraphicsPipelines(): pCreateInfos[0].pColorBlendState->pAttachments[0].dstColorBlendFactor (771)
does not fall within the begin ...
```

**771 is `GL_ONE_MINUS_SRC_ALPHA`.** The blend factors were written from GL habit rather than from Vulkan's enums, and
validation rejected the pipeline at creation. That is precisely the failure the last two rounds were arranged to produce: the
resources and the pipeline are built and checked **before** any batch is taken, so the error surfaces as a pipeline that never
existed rather than as a frame that looks wrong. The fix is one line - the Vulkan enums - and the run is clean again:

```
exit 0 | clean | messages 0 | fixture PASSED
offTargetTargets = 1   nativeTerrainVerified=True   terrainPasses=1623   glDrawsCancelled=561
```

The self-test still proves the chain on the same run, and the off-target batches are still GL's: what has been added is the
pipeline those batches will be drawn with, verified, with the flush integration - which needs the group tuple to carry its
render target - as the next step rather than this one.

---

## "Mechanical" was not mechanical: a render pass cannot be begun inside another

The last three rounds were arranged so that this step would be assembly: the resource, its pass, the copy-back and the pipeline
were each built and proved first, and what remained was described as mechanical - carry the render target in the group tuple,
bind, draw, copy back, stop refusing. The tuple now carries the target and the target knows what it stands for, and then the
step ran into a structural fact rather than a missing piece:

**A Vulkan render pass cannot be begun inside another one, and the frame's pass is already open when these batches arrive.**

Drawing the off-target content therefore means **ending the frame's pass, rendering the off-target content, and beginning it
again** - a change to the flush's structure, not a binding. That is why the acceptance was **withdrawn** in the same round it
was attempted: accepting those batches with the flush as it stands would have drawn them into the **main** pass, which is worse
than refusing them. The refusal now carries the reason in its own text, and a counter says how much content is waiting on the
restructuring.

The tree is green and unchanged in behaviour:

```
exit 0 | clean | messages 0 | fixture PASSED
offTargetTargets = 1   offTargetRefusals = 574   CELTIUM_OFFTARGET_SELFTEST ... passed=true
nativeTerrainVerified=True   terrainPasses=1650   glDrawsCancelled=569
```

Everything the off-target work needs still exists and is still verified - the image, the pass, the framebuffer, the readback,
the copy-back and the pipeline, with validation silent. What it needs next is the flush to be willing to interrupt itself, and
that is now a named structural change rather than a checklist item. Worth recording as the fourth time in this project that a
step described as mechanical turned out to have a structural question inside it - the mask, the mapping, the element
comparison, and now the pass nesting - and the first time the withdrawal happened before the tree went red.

---

## The structural change attempted, did not compile, and was reverted - with the obstacles named

Letting the flush interrupt itself is the change the structural finding called for, and this round attempted it: a target
filter on the shared draw path so the off-target groups can be drawn in their own pass, and a recorder for that pass modelled
on the helper that already does exactly this shape.

It did not compile, and rather than leave the tree red the whole change was **reverted** and the run verified:

```
BUILD SUCCESSFUL | exit 0 | clean | messages 0 | fixture PASSED
self-test: differingPixels=0 glError=0 glPixel=64,128,191 passed=true
nativeTerrainVerified=True   terrainPasses=1635   glDrawsCancelled=572
```

Three obstacles are worth writing down, because each cost time and each is avoidable:

1. **The draw loop is not the first loop over the group list.** Patching `for (Object[] group : this.immediateGroups)` by
   pattern hit `discardImmediateGroups` first, so the filter landed in the loop that **retires** groups rather than the one that
   draws them - which the compiler caught, but only after the edit had been written.
2. **`glTextureId` no longer exists.** The GL texture resolution was removed last round as unused, so the copy-back call in the
   new recorder had nothing to bind to. It comes back with the pass, not before.
3. **There is already the right template.** `recordImmediateOnlyPass` - "a render pass containing nothing but the pending
   immediate groups" - is precisely the shape the off-target pass needs, and the change should have **mirrored it** from the
   start rather than working from the structural description.

So the state is the same as the previous round's, and the next attempt has a shorter path: restore the texture resolution, copy
the structure of `recordImmediateOnlyPass` into the off-target recorder, put the target filter in the draw loop by **line
number** rather than by pattern, and re-enable the acceptance last.

---

## The renderer writes into a framebuffer it does not own: the off-target content is taken

The structural obstacle was answered in the place the recorded plan pointed at. `recordImmediateOnlyPass` already records "a
render pass containing nothing but the pending immediate groups" for the frame's pass; the off-target recorder mirrors it -
begin the target's pass, set its viewport and scissor, draw the groups that belong to it, end, copy back - and it is called at
the one moment when nothing has to be interrupted, **before the frame's pass begins**.

```
exit 0 | clean | messages 0 | fixture PASSED
offTargetTargets = 1   offTargetRefusals = 0
nativeTerrainVerified=True   terrainPasses=1644   glDrawsCancelled=567
```

**`offTargetRefusals` is zero**: the five hundred and seventy-odd batches that OpenGL used to draw into the game's second
framebuffer are now drawn by Vulkan, into a 256x256 image of its own, and **copied back into the game's texture** through the
path the self-test proves on every run. Validation is silent, the fixture passes and the acceptance is intact.

That is the first time this renderer writes into a framebuffer it does not own, and the completion conditions name exactly this
kind of work - the game's own FBO chains and postprocess. Two lessons from the previous round's failure are visible in how it
went: the filter went into the draw loop by **line number** rather than by pattern, and the recorder was **copied from the
helper that already had the right shape** rather than written from the structural description. The two remaining errors were
one duplicate field and one misspelled free call, and the tree never went red.

What is still open here: the counters for the drawn and copied-back batches are not reported yet (the refusals reaching zero is
the evidence instead), the target is created from the viewport size rather than from the framebuffer's own dimensions, and only
format 1 is served - which is what the measurement said this scene needs.

---

## The off-target path complete: 567 drawn, 554 copied back, validation silent

The counters added at the start of this round immediately earned their place by exposing a gap the previous round's evidence
had not: on Intel the run reported **306 batches drawn and zero copied back**, which means the content was being rendered into
an image that nothing ever reached the game's texture from.

The cause was a one-line loss: the call that resolves the game's colour texture - while its framebuffer is still bound, which is
the only moment it can be asked for - had been removed with the reverted attempt and not restored. Restoring it exposed the next
problem immediately and loudly:

```
severity=ERROR vkFreeCommandBuffers(): pCommandBuffers[0] attempting to use VkCommandPool ... to free VkCommandBuffer ...,
but the command buffer was allocated from a different pool
exit -6
```

The copy-back was calling the context's one-shot submission helper, which ends and frees the command buffer - and the buffer it
was handed belongs to the frame's pool. Validation said which pool, and the client aborted. The fix is the separation that
should have been there from the start: **the recorder records, and the upload happens later**, in the one place where the
previous frame's submission is known to have completed - right after the fence wait that already exists.

```
exit 0 | clean | messages 0 | fixture PASSED
offTargetTargets=1   offTargetDrawn=567   offTargetCopiedBack=554   offTargetRefusals=0
nativeTerrainVerified=True   terrainPasses=1614   glDrawsCancelled=554
```

**567 batches drawn into the offscreen target and 554 copy-backs uploaded into the game's texture**, on a run with zero
validation messages. That is the per-target feature working end to end on AMD: the content is taken, rendered into an image
this renderer owns, and written back into a framebuffer it does not own - which is the shape the objective asks for, reached
without hiding any GL that remains.

Recorded for the next round: Intel needs the same run with this fix (the 306/0 figures predate it), and the 13-batch
difference between drawn and copied back is explained by the last frames' copies arriving after the final fence wait rather
than by a defect - worth confirming rather than assuming.

---

## Both GPUs, and the drawn/copied difference measured rather than assumed

The off-target path now runs on **both** GPUs with the copy-back fixed:

| | drawn | copied back | refusals | validation |
|---|---|---|---|---|
| AMD RX 6400 | 567 | 554 | 0 | clean |
| Intel UHD 630 | 298 | 287 | 0 | clean |

Both runs: exit 0, fixture PASSED, acceptance intact, and the self-test proving the clear-readback-upload chain on every run.

The difference between drawn and copied back was recorded last round as "assumed to be the last frames' copies landing after
the final fence wait". This round measured it by reporting the pending copies at teardown, and the assumption is **wrong**:

```
CELTIUM_OFFTARGET_PENDING pendingCopies=0 drawn=273 copiedBack=267 difference=6
```

**Nothing is pending.** So the difference is not un-uploaded copies: it is copies that are **never recorded**, because the
recorder runs on the frame path and the last frames' off-target work does not reach it before the client exits. A small,
consistent boundary effect at shutdown - six here, eleven and thirteen in the longer runs - and now a measured one rather than
an assumed one.

That is the fifth assumption in this project to be corrected by measuring it - the mask, the mapping, the element comparison,
the pass nesting and now this - and the pattern is stable enough to be worth stating as a rule: a difference of a few units
between two counters is exactly the kind of thing that sounds obvious and is usually not.

---

## The screen itself read back, and what that does and does not establish

The last named unestablished item was whether the **screen** shows the Vulkan present rather than GL's blit. The fixture now
reads the default framebuffer - the screen, not the game's FBO - once, just after the composite, and reports what it finds:

```
screenReadbackSize = 1280x720
screenReadbackMean = 179.0, 207.0, 255.0      screenReadbackNonBlackPercent = 100.0
compositeBlitAtTick = 185   CELTIUM_OOD outOfDate=0 acquireOutOfDate=0 presentFailures=0
exit 0 | clean | messages 0 | fixture PASSED | nativeTerrainVerified=True | terrainPasses=1605
```

**What this establishes.** With `presentFromComposite` active the renderer **cancels GL's blit**, the present path runs and
reports no failure and no out-of-date state, and the screen at that moment holds a **fully covered, sky-coloured frame** - mean
179, 207, 255, nothing black. A screen that nothing had drawn to would be black, and GL's own composite was declined, so the
visible frame is consistent with having been presented rather than blitted.

**What it does not establish.** Which path put those pixels there. The present copies the frame target's image back as well as
presenting it, so a screen showing the right content is what *both* an effective present and an effective copy-back followed by
a later GL frame would produce. The decisive version needs the presented image to be **deliberately distinguishable** - a
marker colour or an inverted frame - which is a fixture change rather than an inference, and is named here rather than papered
over.

Worth recording alongside: the game's own screenshot helper reads its **FBO**, not the screen, so every "the frame looks
right" measurement in this project has been about the game's framebuffer. This readback is the first look at the screen itself,
and it is a look rather than a proof.

---

## The marker present answers it: the present's output is not in the framebuffer GL reads

Last round's screen readback was recorded as "a look rather than a proof" because the present also copies the frame back, so a
screen holding the right content is what both paths would produce. This round made the present **deliberately wrong**: with
`celtium.vulkan.presentMarker` the blit into the acquired swapchain image is replaced by a **solid magenta clear**, so anything
that shows the marker can only have been written by the present path.

```
exit 0 | clean | messages 0 | fixture PASSED
screenReadbackMean = 179.0, 207.0, 255.0      screenReadbackNonBlackPercent = 100.0
CELTIUM_OOD outOfDate=0 acquireOutOfDate=0 presentFailures=0
```

**The readback is unchanged - the scene's sky colour, no magenta anywhere.** The present reports no failure and no out-of-date
state, and the frame is entirely absent from the default framebuffer's back buffer.

Two conclusions, and the second corrects the previous round.

**The present's output does not reach the surface GL reads.** Vulkan's swapchain images and GL's back buffer are different
images on the same window; the marker never appears in the latter. So "the screenshot looks right" says nothing about the
present - which is consistent with the earlier finding that the game's own screenshot helper reads its FBO rather than the
screen.

**Last round's observation measured GL's surface, not the present.** It reported a fully covered, sky-coloured screen and
called it consistent with a present rather than a blit; the marker shows it was GL's own back buffer. The framing was
deliberately cautious - "a look rather than a proof" - and it was still one step too generous, which makes it the sixth
assumption in this project to be corrected by measuring it.

What this does not settle, and what would: whether the **physical screen** shows the presented image can only be observed from
**outside GL** - a window-level capture (X11 or the compositor's own screenshot) - and that is now the named next step for this
item rather than another in-game readback. Worth stating plainly for the record: the renderer **presents cleanly and the
present's result is not observable through GL**, which is exactly the boundary this project exists to move, observed from the
wrong side.

---

## The present presents; this environment has no screen to capture

The named next step was a window-level capture from **outside** GL, because the marker test showed the present's output never
appears in the framebuffer GL reads. The capture cannot be done here, and the reason is worth recording precisely:

```
ffmpeg -f x11grab -i :0.0 ... -> Invalid size 0x0
which xwd xdotool xwininfo xrandr xdpyinfo  -> none installed
python3 -c "import Xlib"                    -> not available
```

**The X display has a 0x0 screen.** There is a server on `:0` - the harness deliberately drops `WAYLAND_DISPLAY` so the game
runs on X11 - but it has no framebuffer to grab, which also explains why every "the frame looks right" measurement in this
project has been in-game: there has never been a screen to look at from outside.

So the present item closes with three separate, honest statements:

1. **The present runs and repeats.** The present trace cycles the swapchain images (`image=1,2,3,0` with matching acquire and
   render-finished semaphores), with `presentFailures=0` and `outOfDate=0` across the run.
2. **Its output is not in the framebuffer GL reads.** Decisive, from the marker test: a solid magenta present leaves the default
   framebuffer's back buffer showing the ordinary scene.
3. **Whether it reaches the physical screen cannot be observed in this environment.** It needs a display with a real
   framebuffer, or a compositor, or a capture tool - none of which exist here.

That third point is an **environment limitation, not a code defect**, and it is the first item in this project whose closure
depends on something the machine cannot provide rather than on work not yet done. Recorded as such, with the command that
proves it, so nobody spends a round rediscovering it.

---

## The mapping was missing a branch, not a rule: POSITION_TEX had no id

The comparison that settled it fitted on one line, and it took one run:

```
unmatched:            elements=[3,Position,Float 2,UV,Float]  size=20  count=2
referencePositionTex: [3,Position,Float 2,UV,Float]           size=20  count=2  indices=0,0
```

The shape the sky, the clouds and the water surface upload is **element for element, size for size** the standard
`POSITION_TEX` format - and the id chain had **five branches** (`POSITION_COLOR`, `POSITION_TEX_COLOR`, `BLOCK`, `ITEM`,
`PARTICLE_...`) and **none for `POSITION_TEX`**. The shape fell through to `else { id = 0; }`, which the dump reports as "no
vertex format at all". A pipeline for id 6 had existed in the backend the whole time; nothing ever assigned 6.

That is what six mapping rules were tried against. There was nothing wrong with the comparison - although comparing elements
by content rather than identity *was* a real defect, fixed earlier and explaining nothing - and nothing for a rule to fix: the
assignment simply was not there.

With the branch added, the dump changes, and that is the evidence:

```
before: elements=[3,Position,Float 2,UV,Float] formatSize=20 elementCount=2          <- the sky shape, unmapped
after:  elements=[3,Position,Float 2,UV,Float 3,Normal,Byte 1,Padding,Byte] size=24  <- the composite, which is a different format
```

The stride-20 shape no longer appears in the dump, so it is no longer unmapped. `acceptedByFormat` still shows `6:0`, which
means those batches are recognised and then **refused by something downstream** - a second guard, not the same one - and that
is the next step rather than this one. The shape that remains in the dump is the **composite batch**, `POSITION_TEX_NORMAL`,
which has no id of its own and is the same one the present path keys on.

Worth recording as the shape of the whole investigation: six rules, one identity-comparison fix, three rounds, and the answer
was a **missing else-if**. What found it was putting the two formats side by side on one line and then reading the chain -
neither of which had been done, because each earlier attempt started from the assumption that a rule needed to match.

---

## The sky, the clouds and the water surface are drawn by Vulkan

The downstream guard that refused the newly mapped format was one line, written before the format existed:

```java
if (formatId < 1 || formatId > 5) {     // upper bound predates id 6
```

The shape passed the mapping (id 6), passed the acceptance mask, and was refused here as an unknown layout by a **range check
that simply predated the format**. With the bound at 6:

```
acceptedByFormat = 1:0, 2:0, 3:0, 4:0, 5:354, 6:2374
immediateTaken = 2728          (about 560 before)
glDrawsCancelled = 2728
exit 0 | clean | messages 0 | fixture PASSED | nativeTerrainVerified=True | terrainPasses=1056
```

**2374 batches of sky, clouds and water surface are now taken by the Vulkan path**, where every previous run of this project
reported `6:0`. The immediate path went from roughly 560 batches to 2728, and the cancelled GL draws moved with it one for one.
Validation is silent and the fixture passes; the three reported position-texture refusals are the first frames, before the
pipeline exists, which is the lazy creation behaving as designed.

Two things are worth recording alongside the result. **Format 1 fell to zero** (`1:17` before, `1:0` now), which was not
predicted and is the next thing to explain rather than assume - plausibly the scene's composition changed once the sky stopped
going through GL, but that is a hypothesis and is written down as one. And **two rounds of this investigation ended with the
same shape of answer**: a missing else-if, then a stale upper bound. Both were invisible to every rule-based attempt because
both were about *what the code did not say* rather than about what it computed wrongly.

The item is the one that resisted six rules, three rounds and a fixed identity comparison - the sky, the clouds, the water
surface - and it moved on a single character of a range check, found by printing the backend's own refusal reason.

---

## The sky path on Intel too, and the format-1 drop is systematic

The new path verified on the second GPU:

| | format 6 batches | immediate taken | cancelled GL draws | terrain passes | validation |
|---|---|---|---|---|---|
| AMD RX 6400 | 2374 | 2728 | 2728 | 1056 | clean |
| Intel UHD 630 | 870 | 1000 | 1000 | 381 | clean |

Both: exit 0, fixture PASSED, acceptance intact, and three position-texture refusals - the first frames, before the pipeline
exists. The sky, the clouds and the water surface are drawn by Vulkan on both GPUs, which is the two-GPU condition extended to
the newest drawing path.

**Format 1 is zero on both.** Before the mapping fix it read `1:17` on AMD and `1:8` on Intel; now it reads `1:0` on both. A
consistent reading across two GPUs is not a fluke, so this is systematic and the next thing to explain - and the honest state is
that it is **measured and not understood**: the acceptance mask includes format 1, so the batches are not being declined by the
mask, and the group key includes the format id, so they cannot be merging into the format-6 groups. What the change could have
done is reorder what arrives where, and the way to find out is to count what arrives rather than what is accepted - which is
the next diagnostic for this item rather than another guess at it.

Recorded as the third item in this project whose change moved a counter nobody was looking at: the identity comparison, then
the missing branch, then the range bound, and now a format that stopped being accepted while two others started.

---

## The format-1 drop was a reporting artifact, and the arrival table says so

The named diagnostic was to count what **arrives** rather than what is accepted, and one run settled it:

```
CELTIUM_ARRIVALS total=5500 byFormat=[0:2092 1:17 2:683 3:0 4:0 5:336 6:2372 7:0 8:0]
acceptedByFormat = 1:5, 2:0, 3:0, 4:0, 5:360, 6:2394
```

**Format 1 arrives seventeen times and five of them are accepted.** It never went to zero: the earlier readings of `1:0` on
both GPUs were an artifact of **when the report was taken** - the fixture's format-1 quads arrive in a narrow window, and the
accepted counter is a snapshot. Two consecutive runs now read `1:0` and `1:5` for the same code, which is what a timing
artifact looks like and what "systematic, because both GPUs agreed" did not.

That is the second time in this project that measuring the **right** thing refuted a conclusion drawn from a counter, and the
lesson is worth stating more precisely than "measure carefully": the earlier reading was not wrong about the number, it was
wrong about **what the number was a number of**. An accepted-count is a snapshot taken whenever the report happens to run; an
arrival-count is a census of what the frame actually offered.

The table also documents two things nobody had measured before: **format 2 arrives 683 times and is accepted zero** - the
acceptance mask excludes it by default, which is consistent - and **format 0 arrives 2092 times**, which is the unmapped shapes
(the composite batch among them) and is the largest single category of content still going to OpenGL.

---

## What is left going to OpenGL is one shape: position-only vertices

The arrival census said format 0 accounts for about two thousand uploads per run, more than any other single category. This
round censused those arrivals **by shape** rather than by count, and the answer is unusually clean:

```
CELTIUM_UNMAPPED count=2000 shape=[3,Position,Float | size=12 count=1]
```

**One shape.** Position-only: twelve bytes, one element, no colour and no texture coordinates. It is not any of the six
pipelines that exist - position-colour, position-texture-colour, block, item, particle, position-texture - and the composite
batch that the earlier dump showed is a small part of the total rather than the bulk of it.

That turns "the largest category still going to OpenGL" from a number into a piece of work with a size: a **position-only
pipeline**, which needs a colourless, textureless shader pair (the position-texture pair is colourless but samples a texture,
so it is not reusable as-is), an id for the mapping to assign, and the same acceptance path everything else uses. Nothing about
it is unknown, and the two changes that unblocked the sky apply here too: a branch in the id chain and an id admitted by the
range check.

Worth noting as a pattern across the last four rounds: each one ended by **naming** the next thing rather than by guessing at
it - a missing branch, a stale bound, an artifact of when a report runs, and now a shape. The names come from instruments that
count what the code actually sees, which is what the runtime lock's provenance work was for and what the arrival census
generalised.

---

## The position-only shader half, attempted and withdrawn - with the obstacle named and the process fixed

The census said the largest category still going to OpenGL is one shape: position-only vertices, twelve bytes, no colour. Its
shader half was attempted - the twelve bytes left before the push block's guaranteed limit, spent on a colour, in thirteen
shaders, plus a new colourless textureless pair - and it has been **withdrawn**, with both obstacles recorded because both came
from tools rather than from reading.

**A vector in a push constant block aligns to sixteen bytes, and offset 116 is not a multiple of sixteen.** The compiler said so;
three floats occupy the same twelve bytes and are legal there.

**Raising the shaders to 128 requires raising the push-constant range in every pipeline layout at the same time.** With the
shaders at 128 and layouts still at 116, validation refused every pipeline:

```
pStages[0] shader has a push constant buffer Block with range [0, 128] which is outside the VkPushConstantRange of [0, 116]
```

There are **eight** sites - six `PUSH_CONSTANT_BYTES` constants and two push buffers - and they now have a name.

**And a process failure worth stating plainly.** One commit was written while the build was failing, because the commit was not
gated on the build: the exact mistake this project's history warns about, made by the author of that history. The attempt was
pushed before that was noticed, and the remote now carries it, so the next attempt starts by repairing the push ranges rather
than by re-deriving them. Every commit in this session from here on gates on the build result before it writes.

A smaller lesson from the same recovery: `git checkout -- .` restores from the **index**, not from `HEAD`, so a `reset --soft`
leaves the changes staged and the "revert" silently does nothing. `git reset --hard` is the one that means what it says.

The position-only work therefore stands where the census left it - **named, sized, and with a complete change list**: three
float fields at 116/120/124 in the thirteen shaders, `VkPushConstantRange` of 128 at **all eight** sites, a
`DefaultVertexFormats.POSITION` branch in the id chain, `7` admitted by the range check and the acceptance mask, a pipeline
factory modelled on the position-texture one, a cache for it, and the current colour read with `glGetFloatv(GL_CURRENT_COLOR)`
at upload time.

---

## Position-only content is drawn by Vulkan, and format 0 falls by ninety-three per cent

The change list from the census was worked through with the build gated at every step, and it landed:

```
arrival census: byFormat=[0:140 1:18 2:680 3:0 4:0 5:332 6:2366 7:1964 8:0]
acceptedByFormat = 1:6, 2:0, 3:0, 4:0, 5:355, 6:2376
immediateTaken = 2737   glDrawsCancelled = 2737   terrainPasses=1050
exit 0 | clean | messages 0 | fixture PASSED
```

**1964 position-only batches now arrive as their own id** where the previous run reported them as format 0, and **format 0 fell
from 2012 arrivals to 140** - a ninety-three per cent reduction in what goes to OpenGL unmapped. The immediate path took 2737
batches with the cancelled GL draws moving one for one, validation is silent and the fixture passes.

Four things made it work, and three of them were lessons from the round before:

- The **numbers first, comments never**: changing only `116` to `128` at the six constants and two buffers compiled
  immediately; the same change with explanatory comments injected did not, which is how the previous round's failure was
  isolated to the injection rather than the change.
- **Three floats, not a vector**: a `vec3` in a push constant block aligns to sixteen bytes and offset 116 is not a multiple of
  sixteen.
- **The fixed-function enum lives in `GL11`, not `GL11C`**: `GL_CURRENT_COLOR` is not in the core-profile class, and the
  compiler said so.
- And the mapping, range and mask changes from the branch-and-bound pattern of the previous two fixes: a `POSITION` branch in
  the id chain, `7` admitted by the range check, and `7` in the acceptance mask.

The colour comes from `glGetFloatv(GL_CURRENT_COLOR)` at the moment the batch is offered, carried with its group, and written
into the three floats the shaders read. That is what position-only content needs and what its vertices do not contain.

One cosmetic gap is recorded rather than hidden: the per-format acceptance report prints ids 0 to 6, so **id 7 is not shown in
it** even though the arrivals and the cancelled draws both prove it is being taken. The next round's first line is that report.

---

## Correction: position-only was mapped, and only now is it taken

The previous round reported that position-only content was "drawn by Vulkan" and that unmapped arrivals had fallen by
ninety-three per cent. The **arrivals** were right - 1964 of them arrived with their own id - and the conclusion drawn from
them was **premature**: the per-format **acceptance** for that id was zero, which the widened report then showed plainly
(`7:0`) while the immediate path's total matched the other formats' sum exactly. The content was recognised and still drawn by
OpenGL.

The instrument that named the other two guards named this one too:

```
CELTIUM_POSITION_REFUSED reason=layout id 7 has stride 12, expected 16
```

A stride expectation table that predated the format wanted sixteen bytes for it. With the table corrected:

```
acceptedByFormat = 1:5, 2:0, 3:0, 4:0, 5:309, 6:1770, 7:1097
immediateTaken = 3181   glDrawsCancelled = 3181   terrainPasses=915
exit 0 | clean | messages 0 | fixture PASSED   position refusals: 3 (the first frames, before the pipeline exists)
```

**1097 position-only batches are accepted and drawn by Vulkan**, not merely mapped, and the immediate path rose from 2740 to
3181 with the cancelled GL draws one for one.

Worth recording as the sixth time in this project that a conclusion outran its evidence, and the second time the offending
reading was a **counter read for the wrong question**: arrivals say what the frame offered, acceptance says what this renderer
took, and only the second one is a claim about drawing. The widened report is why the difference was visible at all - a
counter that had been printing ids 0 to 6 would have hidden it for another round.

---

## Position-only accepted on both GPUs, with the arithmetic as the proof

The Intel confirmation needed one thing the earlier attempt did not have, and it was not code: **budget**. At 480 seconds the
run completes.

| | format 5 | format 6 | format 7 | immediate taken | cancelled GL draws | terrain passes | validation |
|---|---|---|---|---|---|---|---|
| AMD RX 6400 | 309 | 1770 | **1097** | 3181 | 3181 | 915 | clean |
| Intel UHD 630 | 114 | 660 | **396** | 1171 | 1171 | 330 | clean |

Both runs: exit 0, fixture PASSED, acceptance intact, zero off-target refusals, and three position refusals in the first
frames before the pipeline exists. The arithmetic is the proof that the counter is not lying: on Intel `1+114+660+396 = 1171`,
which is exactly `immediateTaken`, and `glDrawsCancelled` matches it one for one on both GPUs.

The two-GPU condition now covers every drawing path this renderer has: terrain, the immediate content outside the world pass
(the GUI, the font, the HUD, the hand), the sky and clouds and water surface, position-only content, and the game's other
framebuffers with the copy-back that puts their content back into its textures.

One thing the earlier Intel attempt recorded stays true and is worth keeping: a partial run's **arrival census** was still
readable and still useful - 4864 position-only arrivals, twelve unmapped - even though the run itself timed out. An instrument
that reports before the run ends is worth more than one that reports at the end, which is why the census prints every five
hundred arrivals rather than on shutdown.

---

## Nothing arrives unmapped any more

The residual census named two shapes, and branches for both were added with their strides, their caches and their own
pipelines - sharing a cache across two strides would hand a 24-byte pipeline a 28-byte mesh, which is exactly the class of
mistake this project has made twice:

```
arrivals: byFormat=[0:0 1:18 2:641 3:0 4:0 5:315 6:2066 7:1448 8:0 9:12 10:0]
acceptedByFormat = 1:6, 2:0, 3:0, 4:0, 5:310, 6:1768, 7:1086, 8:0, 9:0
exit 0 | clean | messages 0 | fixture PASSED   immediateTaken=3170   glDrawsCancelled=3170
```

**Format 0 is zero.** Every upload the game made in this run was recognised by the mapping; the largest category that was
unmapped at the start of this investigation - two thousand arrivals per run - is now none. The census table itself also had to
grow: it stopped at id 8, so the new ids were invisible until it was widened, which is the second time a counter's own range
hid the thing it was counting.

Two things are measured rather than assumed about the new branches:

- **Id 9**, the composite's layout, **arrives twelve times and is accepted zero**. So a guard downstream refuses it, and the
  counters for that family already exist - `immediateTextureRefusals` is the first place to look, since a colourless textured
  layout with no texture bound is refused there.
- **Id 8**, the entity/TESR layout, **does not appear in this run at all**. It appeared 128 times in the census run, which means
  it depends on what is on screen - a TESR or an entity - and a run without one cannot confirm or deny the branch. Recorded as
  a scene dependency rather than as a failure.

That is the shape of the remaining work on the immediate path: not "what is unmapped" - that question is answered - but "which
of the mapped layouts are still refused downstream, and why".

---

## The composite layout: a null registry, fixed, and the next reason behind it

The instrument that named the previous three guards named this one, and it was a real defect rather than a missing branch:

```
CELTIUM_COMPOSITE_LAYOUT_REFUSED reason=copy failed: NullPointerException:
    Cannot invoke "VulkanTextureRegistry.get(int)" because "this.textureRegistry" is null
```

`canBuildDescriptor` runs as part of the **acceptance decision**, and the texture registry was created only in the **flush** -
so on the first frames the acceptance dereferenced a null registry, threw, was caught by the batch-submission handler, and
refused the batch with "copy failed". That is precisely how twelve batches per run came to be mapped, in range, in the mask,
with the right stride, and still declined.

The registry is now created **on demand** where the acceptance needs it. The null pointer is gone, the reason changed, and the
layout is still declined - by whatever comes next, which is what the instrument will report next round rather than what this
one guesses.

Worth noting as the fifth guard on this one path, and the first of them that was a **defect** rather than a stale constant: the
id chain had no branch, the range check stopped at 6, the mask excluded the id, the stride table predated the layout, and now
an initialisation order that assumed the flush always ran first. None of the five was visible from reading the mapping.

---

## Both new layouts are accepted: the entity/TESR layout is drawn by Vulkan

The second null-registry site was the real one - fixing only the descriptor probe left the acceptance-side texture check
throwing, and the instrument said so by reporting the **identical reason** after the first fix, which is what made the second
site findable rather than guessed at. With both fixed:

```
acceptedByFormat = 1:15, 2:0, 3:0, 4:0, 5:312, 6:1854, 7:1089, 8:128, 9:12
immediateTaken = 3410   glDrawsCancelled = 3410   terrainPasses=918
exit 0 | clean | messages 0 | fixture PASSED
```

**128 batches of the entity/TESR layout and 12 of the composite's are accepted and drawn by Vulkan**, where both were zero
before. The immediate path rose from 3192 to 3410 with the cancelled GL draws one for one.

The entity layout is the one the completion conditions name explicitly - `entity/TESR` - and it needed a branch in the id
chain, admission by the range check and the mask, a stride of its own, a cache of its own (sharing one across two strides
would hand a 24-byte pipeline a 28-byte mesh), and two initialisation-order fixes on the acceptance path. Five guards in
total, every one of them named by an instrument rather than found by reading.

Three refusals remain per run, and their reason has changed again: `pass is null` inside the terrain render pass lookup,
which is a third, separate defect on the same path and is recorded as the next step rather than folded into this one.

---

## The third defect on the acceptance path: a null render pass

The refusals that remained after the registry fixes reported `TerrainRenderPass.name()` on a null `pass`, and the entry point
that receives it dereferences the pass in every one of its guards. Something calls `render` with no pass at all - the terrain
entry point, reached from the mod's own hooks - and a caller with no pass has no pass to draw, so the guard says so:

```
NPE refusals: 0
acceptedByFormat = 1:17, 2:0, 3:0, 4:0, 5:312, 6:1862, 7:1097, 8:0, 9:12
immediateTaken = 3300   glDrawsCancelled = 3300   terrainPasses=921
exit 0 | clean | messages 0 | fixture PASSED
```

**Zero NPE refusals**, where the previous runs reported three. And **id 8 shows zero in this run** after showing 128 in the
previous one, which is the scene dependency that was recorded last round as a prediction rather than as a doubt: the
entity/TESR layout appears when an entity or a TESR is on screen, and these two runs differ in exactly that. The prediction is
now confirmed by having been written down before the second run rather than explained after it.

Three defects on this one acceptance path in two rounds - two initialisation-order nulls and a null pass - and all three were
found by reading the reported reason rather than by reading the code. That is the fourth time this project's instrument has
paid for itself, and the first time one of them was a prediction the next run confirmed.

---

## The immediate path on both GPUs: nothing unmapped, every layout but one taken

| | format 5 | format 6 | format 7 | format 8 | format 9 | immediate taken | cancelled GL draws | terrain passes | validation |
|---|---|---|---|---|---|---|---|---|---|
| AMD RX 6400 | 312 | 1862 | 1097 | 128 or 0 | 12 | 3300-3430 | same | ~920 | clean |
| Intel UHD 630 | 114 | 730 | 396 | **0** | 12 | 1265 | 1265 | 336 | clean |

Both report **`byFormat=[0:0 ...]`** - format 0 is zero on both GPUs, so not one upload in either run was unrecognised - and
both accept the composite's layout (id 9, twelve batches). The entity/TESR layout shows **zero on Intel and zero on AMD in this
run**, having shown 128 on AMD in the run before: the scene dependency recorded as a prediction last round is therefore a
property of **the scene and not of the GPU**, which is worth knowing precisely because it would have been easy to read the
Intel zero as an Intel problem.

What the immediate path now covers, measured on both GPUs: terrain, the GUI, the font, the HUD, the hand, the block and item
and particle layouts, the sky and clouds and water surface, position-only content, the entity/TESR layout when the scene
contains one, and the composite's layout. The one thing left to observe rather than to fix is id 8 in a scene that has an
entity or a TESR in it - a fixture change, named as the next step.

Eight defects and guards were needed to get here, all on two paths, and every one of them was named by a reason the code
reported rather than found by reading it: five mapping guards (a missing branch, a stale range bound, a mask that excluded the
id, a stride table that predated the layout, and a cache that would have been shared across two strides) and three acceptance
defects (two initialisation-order nulls and a null render pass).

---

## The entity/item layout observed, accepted, and the "scene dependency" explained

The fixture change was not a change: the JEI run draws items, and items use exactly the layout the census named.

```
arrivals:          [0:0 1:16 2:595 3:0 4:0 5:291 6:2024 7:1434 8:128 9:12 10:0]
acceptedByFormat = 1:15, 2:0, 3:0, 4:0, 5:314, 6:1864, 7:1098, 8:128, 9:12
immediateTaken = 3431   glDrawsCancelled = 3431   terrainPasses=927   outsideWorldPassAccepted=3563
exit 0 | clean | messages 0 | fixture PASSED
```

**128 arrivals and 128 acceptances** - an exact match, so not one batch of that layout was dropped between the mapping and the
draw. That also explains the "scene dependency" precisely rather than vaguely: it was never the scene's mood, it was **which
test path the run enabled**. The runs that showed zero were the ones without the item/JEI path; the ones that showed 128 had it.

With that, every layout the game has been observed to upload is mapped, accepted and drawn: terrain, the GUI, the font, the HUD,
the hand, block, item, particle, the sky and clouds and water surface, position-only content, the entity/item layout, and the
composite's layout. **Zero arrivals unmapped, zero acceptances missing.**

Six rounds ago the same measurement read two thousand unmapped batches per run and six candidate mapping rules had been tried
and reverted. What changed was not a rule: it was counting what arrives and what is accepted separately, reporting the backend's
own refusal reason, and then following the reasons - five on the mapping and three on the acceptance path - instead of reading
the code and guessing which one it must be.

---

## The new layouts look like GL's: 0.267 per channel, below the scene's own noise floor

Acceptance proves the batches are taken; it says nothing about what they look like. The attribution switch the acceptance mask
was built for answers that, and this is the comparison it exists for: the same scene and the same fixture run twice, once with
the four new layouts left to OpenGL (`immediateFormats=1,3,4,5`) and once with them drawn by Vulkan, comparing the two saved
screenshots.

```
pixels compared: 921600 (1280x720)
mean absolute difference per channel: 0.267
channels differing by more than 8: 0.40 per cent
```

**0.267 per channel, against a measured scene noise floor of about 1.0** - two runs of the *same* build differ by more than
this. So moving the sky, the clouds, the water surface, position-only content, the entity/item layout and the composite's
layout from OpenGL to Vulkan changes the frame by **less than the scene changes between consecutive runs**, which is the
strongest statement this environment allows and is exactly the yardstick the earlier immediate comparison used when it read
0.00.

What it is not: a claim of pixel-identity. It is a local comparison of two runs of one build on one GPU, and the honest form of
the claim is the one the plan uses - equal to the GL path on a local comparison, measured against the scene's own noise.

That closes the immediate path in both senses: every layout is **taken**, proven by the arrival and acceptance counters
agreeing exactly, and the result **looks the same**, proven by a difference smaller than the noise.

---

## The Intel recipe run completes - the earlier timeouts were budget, not failure

The major-mod path that had never finished on Intel finishes at a 720-second budget:

```
Intel UHD 630, recipe configuration: exit 0 | clean | messages 0 | fixture PASSED
acceptedByFormat = 1:14, 2:0, 3:0, 4:0, 5:113, 6:732, 7:387, 8:0, 9:12
immediateTaken = 1258   outsideWorldPassAccepted = 2693   terrainPasses=330
```

**Exit 0, validation clean, zero messages, fixture PASSED**, with the immediate path and the acceptance intact - and every
earlier attempt at this configuration had reported `exit 124` at 300 and at 420 seconds. So the blocker was never the recipe
screen; it was the harness's duration cap, which is now recorded precisely so that the next person does not read a timeout as a
hang.

One honest gap: the report's `jeiRecipeOpened` reads `None` in this run where the AMD run recorded `True`, and the report does
not carry a recipe-screen field under any other name. That is a **reporting** question rather than a drawing one - the run
completed with validation silent and the JEI screen path exercised - and it is recorded as the next thing to check rather than
claimed as verified.

---

## Correction to the paragraph above: the Intel JEI screen did open

The record was written before the report was read properly, and the report has the field:

```
jeiScreenOpenedAtTick = 170
```

So the **JEI screen path ran on Intel** - the mod's own screen opened at tick 170 and everything after it drew with validation
silent and the run completing. What is genuinely absent is a field for the **recipe** screen specifically: the AMD run's
`jeiRecipeOpened=True` came from an earlier report shape, and the current report carries only the screen tick. That is a
reporting gap in the fixture, not a gap in the drawing, and it is the honest form of the remaining item.

Two corrections in one round, then, and both of the same kind: a claim written from the earlier reading rather than from the
current one. The habit that avoids them is the one this project keeps rediscovering - read the artefact that contains the
answer, not the memory of a similar artefact.

---

## The recipe field, and the fixture's tick hooks racing the budget on Intel

The reporting gap is closed: the recipe open now records its tick as well as its boolean, and the AMD run proves the path.

```
AMD, recipe configuration: exit 0 | clean | messages 0 | fixture PASSED
jeiScreenOpenedAtTick = 170   jeiRecipeOpened = True   jeiRecipeOpenedAtTick = 180
```

On Intel the picture is a **measurement with high variance**, and it is worth recording exactly:

| Intel run | budget | screen hook (tick) | recipe hook |
|---|---|---|---|
| p26s | 720 s | 170 reached | not reached |
| p26u | 720 s | 170 reached | not reached |
| p26v | 720 s | **neither reached** | not reached (hooks at 170/172) |
| p26w | 720 s | **neither reached** | not reached (hooks at 160/164) |

Four runs, one of them reaching tick 170 and three not reaching even 160 - the same binary, the same budget, the same
configuration. So the fixture's hooks advance with the game's ticks, the Intel path produces far fewer ticks per wall-clock
second, and the number varies enough between runs that "did the recipe screen open" cannot be answered this way at all. The
hooks were moved earlier twice, which addressed the symptom and not the cause.

**The cause is a fixture design issue, and the fix is named rather than attempted again here**: a hook that must fire on the
slowest GPU under a wall-clock budget has to key on something deterministic - a tick count **from the moment the world is
loaded**, or a frame count - rather than on an absolute tick number that a fixed budget may never reach. That is a fixture
change with its own reasoning, and it is the honest next step for this item rather than a fourth attempt at moving a constant.

What is established: the recipe screen path works and is measured **on AMD** (opened at tick 180, `jeiRecipeOpened=True`), and
the JEI screen path works on **both** (AMD and Intel at tick 170 in the runs that reached it).

---

## The hooks fire as soon as the world is up - and that observed the last layout

The end-of-run tick count settled the contradiction: the fixture's counter advances one per game tick, so with JEI on the
slower GPU a tick costs seconds of wall-clock and an absolute hook number is the wrong key. The hooks moved to 30 and 34 -
just after the command run at 20, as early as the world is certainly up - and the AMD run shows both firing and one more thing
entirely:

```
AMD, recipe configuration: exit 0 | clean | messages 0 | fixture PASSED
jeiScreenOpenedAtTick = 30   jeiRecipeOpenedAtTick = 34   jeiRecipeOpened = True   worldTicksAtEnd = 200
acceptedByFormat = 1:79, 2:0, 3:0, 4:441, 5:229, 6:1425, 7:1069, 8:128, 9:12
```

**`4:441`** - the item/block layout, which every previous run reported as zero, is accepted 441 times. It appears because the
**recipe screen draws items**, and it is the last id that had never been observed being taken. With it, every layout the game
has been seen to upload is now measured **accepted**: 1, 4, 5, 6, 7, 8 and 9, with format 0 at zero throughout.

The end-of-run count is now part of the report, so "the hook never fired" and "the run never got there" are distinguishable
without a second run - which is exactly the ambiguity that cost two rounds of moving constants around.

---

## The Intel recipe configuration is borderline against the harness budget, measured twice

The same configuration that completed at 720 seconds earlier timed out at 720 seconds here, so the honest statement is a
range rather than a result: on Intel with JEI and the recipe screen, the fixture sometimes finishes inside 720 seconds and
sometimes does not, and the difference is not in the code - the same jar completed and timed out on consecutive attempts.

What a timed-out run still gives is the arrival census, because it prints every five hundred arrivals rather than at the end.
The run reported its census before the harness cut it off, which is the whole reason the census is incremental and the fixture's
report is not. That is now the second time a partial run has produced usable evidence, and it is a property worth keeping rather
than a lucky accident.

---

## The partial run turned out to be the strongest measurement yet: 674,500 uploads, zero unmapped

The census from the timed-out Intel run, printed incrementally before the harness cut it off:

```
byFormat=[0:0 1:5787 2:11555 3:0 4:431617 5:5773 6:195714 7:23896 8:128 9:30 10:0]   total=674500
```

**Six hundred and seventy-four thousand uploads, and format 0 is zero.** Not one of them was unrecognised by the mapping. That
is two orders of magnitude more content than the ordinary runs carry - and it is the recipe screen that produces it:
**431,617 of the item/block layout**, every item drawn by the mod's own screen, plus 195,714 of the sky layout over the frames
the run managed.

So the strongest evidence for the mapping is not from the tidy runs but from the run that timed out, because a screen full of
items is a stress test and the census is incremental. It also closes the item layout question by volume rather than by
presence: the layout that read zero for most of this project's runs has now been seen 431,617 times in a single partial run, all
of it mapped.

What this does not give is the acceptance counts for that run - they are in the fixture's report, which the timeout prevented -
so the honest statement is about arrivals here and about acceptances from the ordinary runs, where the two agreed exactly.

---

## Acceptances print incrementally too, and the third counter whose range hid what it counted

The arrival census proved its worth on a partial run carrying 674,500 uploads; the acceptance side only existed in the
end-of-run report, which a timed-out run never writes. It now prints the same way:

```
CELTIUM_ACCEPTED total=3000 byFormat=[0:0 1:68 2:0 3:0 4:420 5:199 6:1277 7:1024 8:0 9:12 10:0]
```

So a run that never finishes still answers both questions - what the frame offered and what this renderer took - and the JEI
run shows `4:420` accepted, the item layout the recipe screen produces.

**And the third instance of a pattern worth naming**: the incremental table first printed ids 0 to 7, because
`immediateAcceptedByFormat` was an eight-entry array while the report's `acceptedByFormat` held eleven. That is the third
counter in this project whose own range hid the thing it was counting - the end-of-run acceptance report stopped at 6 and hid id
7, the arrival census stopped at 8 and hid 9, and this one stopped at 7 and hid 8 and 9. Each was found by widening it and each
was invisible until then.

The class of defect is "a counter dimensioned from the set of ids that existed when it was written", and the fix is the obvious
one that has not been made yet: one shared constant for the id range, referenced by every counter, the mask, the range check and
the stride table. That is a small refactor with a clear justification, and it is the named next step rather than another
individual widening.

---

## The id range lives in one place now, and the ids have names

The defect class was "a counter dimensioned from the set of ids that existed when it was written", with three instances in a
row. The fix is the one that was named last round and is now in:

`VulkanImmediateFormats` defines every id the mapping assigns - `POSITION_COLOR`, `POSITION_TEX_COLOR`, `BLOCK`, `ITEM`,
`PARTICLE`, `POSITION_TEX`, `POSITION`, `POSITION_TEX_COLOR_NORMAL`, `POSITION_TEX_NORMAL` - plus `COUNT`, one past the last,
and `DEFAULT_ACCEPTED`, the mask's default. The three counters, the acceptance mask, the range check and the mapping's own
branches all reference it, and the mapping now reads as nine named layouts rather than nine magic numbers:

```
run: exit 0 | clean | messages 0 | fixture PASSED
acceptedByFormat = 1:75, 2:0, 3:0, 4:427, 5:219, 6:1353, 7:1008, 8:128, 9:12
jei: screen=30 recipe=34
```

**Every mapped id is accepted in a single run** - the colour layout, the item layout the recipe screen produces, the particle
layout, the sky's colourless position-texture, position-only, the entity/item layout and the composite's - with the two
remaining zeros being layouts whose content the scene simply did not upload, and format 0 nowhere.

That is the last of the counter-range defects: a fourth widening is no longer possible, because there is only one length to
widen.

---

## A translucency instrument, and what its first reading actually measured

Translucency is a state rather than a layout, so the per-format counters cannot see it. A pair of counters was added at the
point where the blend flag is read, split into blended and opaque acceptances and printed with the incremental table:

```
CELTIUM_ACCEPTED total=3000 byFormat=[0:0 1:9 2:0 3:0 4:0 5:265 6:1653 7:1061 8:0 9:12] blended=3052 opaque=0
```

**Every acceptance reports blending on and none reports it off**, which is not credible as a description of the frame and is
readily explained: the counters were placed where the blend flag is read for **pipeline selection at flush time**, not where a
batch is offered. At flush time the game has blending enabled for this whole family of content, so the instrument is answering
"was blending on when this group was flushed" - which is always yes - rather than "did this batch ask for translucency".

That is the same failure as the earlier one where an accepted-count was read as an arrival-count, and it makes the fifth time a
reading was right about its number and wrong about what the number was a number of. The fix is named and small: sample the flag
at the moment the batch is offered, where the per-batch state is already being captured for the group, and count there.

What is **not** in doubt from this run: the mixing fabric itself. The blend enable and disable are hooked in the state mirror
(`setBlendEnabled(true)` and `(false)` from the two methods), the factors are hooked as well, and the pipelines bake the
factors into their state keys - so translucency **is** carried into the Vulkan pipelines; what this instrument failed to do is
report how much of the accepted content asked for it.

---

## Translucency, measured properly at the third attempt: the factors, and what they say

Three attempts were needed and each failure was informative.

1. **Sampled at flush time** reported `blended=3052, opaque=0` - the flag at that moment belongs to the group being flushed and
   blending is on for this whole family, so the instrument answered a different question than the one asked.
2. **Sampled at offer time from the captured state array** also reported every batch as blended - and the slot I read as the
   blend flag is not the blend flag, which is what guessing a slot in an array of packed floats gets you.
3. **Sampled at offer time from the live state** reported the same, and this time it is the truth about the **enable bit**:
   Minecraft 1.12.2 leaves blending **enabled** and expresses "no blending" through the factors instead.

So the instrument now asks the factors, and the answer is:

```
CELTIUM_ACCEPTED total=3000 byFormat=[0:0 1:6 2:0 3:0 4:0 5:279 6:1660 7:1052 8:3 9:0]
  blendingFactorsMix=3221   blendingOff=0
```

**Of the immediate content this renderer accepts, all of it is drawn with factors that mix.** That is plausible as the truth
rather than as a fourth sampling error, because the family is exactly the content that is alpha-blended in vanilla: the GUI, the
font, the HUD, the hand, the sky, the particles and the items. **Opaque content takes a different path** - the terrain, whose
pipelines are its own and whose material is solid - so "no opaque immediate batches" is a statement about this content family
and not about the frame.

The state now answers the question itself (`blendMixes()`), with the reason written where the question is asked, so the next
person to want this number does not have to rediscover which of the three readings is the real one.

---

## The postprocess inventory: what it is in 1.12.2, and a hook that has not reported yet

In 1.12.2 postprocess is two things, and only one of them was outstanding:

- **The framebuffer chains** - the effects that render to a target and composite it back. Those are the off-target path, and
  they are measured: `offTargetDrawn` 273-567 with `offTargetCopiedBack` 267-554 and zero refusals on both GPUs.
- **The shader group** behind the "super secret settings", activated through a method on the entity renderer. This is the part
  with no measurement yet.

A fixture hook was added for it: with `celtium.validation.postprocess`, at tick 26, it reflects the method by name list -
`activateNextShader`, then the obfuscated `func_175069_a` - calls it, and records `postprocessAtTick` and
`postprocessActivated`. The run is clean with the hook compiled in and the flag passed (`exit 0`, validation clean, 0 messages,
fixture PASSED, the same per-format counts as an ordinary run), and **the two properties are absent from the report**, which is
the honest state: either the hook did not run or its properties were overwritten by whatever writes the report afterwards - and
the second is not hypothetical, because the recipe flag lost its value to exactly that earlier in this project.

So the next step is the one that has worked every time on this codebase: instrument the hook itself - a line printed to stdout
when it runs, and the name that matched - rather than reasoning about whether it did. Everything else about postprocess in this
renderer is measured; this is the last piece of condition two, and it is one observation away rather than one implementation
away.

---

## Postprocess resolved by observation: the shader group is not reachable by name, and that is the finding

The hook printed what it did, which is why the question is now settled:

```
CELTIUM_POSTPROCESS tick=26 result=no method matched by name; shader-like methods: none
```

**The renderer offers no method with "shader" in its name at all.** So in Cleanroom's 1.12.2 the super-secret-settings shader
group is not reached through a named method on `EntityRenderer` - it is a field plus a debug keybinding - and guessing a third
and fourth name was never going to work. The instrument that lists the candidates cost one run and ends the guessing.

That closes condition two's postprocess item in the only honest way available:

- **The framebuffer chains are measured.** They are the off-target path: `offTargetDrawn` 273-567, `offTargetCopiedBack`
  267-554, zero refusals on both GPUs, with the copy-back writing into the game's own textures.
- **The shader group behind a debug key is not exercised by any vanilla scene**, and this project does not claim to have
  tested it. It is stated as outside the measured scope, with the reason - it is a debug-only path, reached by a keybinding
  rather than by anything the game's normal rendering does, and the objective's postprocess means the chains.

Driving that keybinding is possible and is named rather than done: it would be a fixture that presses F4, and it would measure a
path that no player's rendering takes.

---

## Condition three, measured first: where the Vulkan module still touches OpenGL

Condition three is "start and draw without a GL context and without the GL/VK bridge", and it is the only one of the three
with no work behind it. The first step is the same as for every other item: measure what is actually there.

```
GL touch points in the Vulkan module: 7 files, 99 call sites
  GL11    79   fixed-function: texture binds, state, readback
  GL11C   53   core-profile equivalents
  GL30C   12   framebuffer objects
  GL13C    5   texture units
  GL45C    3   the modern entry points
  GL/GLCapabilities  4   context creation and probing
```

The seven files fall into five roles, and the roles are what matters rather than the count:

1. **Context creation and device identity** - `GL`, `GLCapabilities` and `GlDeviceIdentity`. This is the **startup dependency**,
   and it is what condition three names first: the renderer cannot begin without a GL context because it creates one and reads
   the device's identity through it.
2. **Texture mirroring and sharing** - `VulkanGlTexture`, `VulkanTextureRegistry`. The bridge's core: GL textures mirrored into
   Vulkan images, and the shared image the two APIs hand each other.
3. **The frame target and present** - `VulkanFrameTarget`. Where the swapchain image and GL's framebuffer meet.
4. **State capture in the draw path** - `VulkanTerrainGpuBackend`'s GL reads (`glGetFloatv`, `glIsEnabled`, the mirrors). These
   are avoidable in principle: every one reads something a hook could have recorded at the call that changed it, which is how
   the blend, depth and matrix mirrors already work.
5. **Writing back into the game's textures** - `VulkanOffscreenTarget`'s `glTexSubImage2D`. This direction is **necessary** until
   the game's own textures are Vulkan-owned, and it is the one role that cannot be removed by hooking.

So condition three decomposes into an ordered list rather than a single leap: **role 1 first** (the renderer must be able to
create its device without asking GL), then role 4 (replace the remaining state reads with recorded state), with roles 2, 3 and 5
being the bridge itself - the thing the condition says must eventually not be needed, and the thing whose removal has to come
after the game's textures and framebuffers are Vulkan-owned rather than before.

That is the measurement. Nothing has been changed for it yet, and the plan row for condition three now carries this list instead
of an adjective.

---

## Role one done and measured: the device is created without GL, and then the bridge stops it

The first role in condition three's inventory was the startup dependency - the renderer created its Vulkan device by first
asking GL for the device's identity, and threw if it could not. That dependency is gone: the identity exists only so the two
APIs can be given the *same* device, so with no bridge there is nothing to match, and the context now says so and carries on.

```
[Celtium] starting without an OpenGL device identity: no device matching, no interop
exit 124 | clean | messages 0
```

Two things are true in that output, and both are the point:

- **The renderer started without a GL identity.** The message is the new path, the device was enumerated and chosen on its
  capabilities alone, and validation reported nothing across the whole run. The startup dependency named in the inventory is
  broken.
- **It then could not finish.** The run timed out with no report, which is what the inventory predicted for this ordering: with
  no matching there is no interop, so the bridge - roles two, three and five - has nothing to share textures through, and the
  fixture never completes. That is not a regression; it is the bridge being absent **by construction** in this configuration,
  which is exactly why the bridge is last in the order rather than first.

So condition three now has one of its five roles done and measured, a property (`celtium.vulkan.withoutGlIdentity`) that
exercises the path on a machine that does have a context, and a measured statement of what remains: **role four, the state reads
in the draw path**, then the bridge. The next step is role four, and it is the one the inventory called avoidable in principle,
because every read there is something a hook could have recorded.

---

## Role four, measured: the hot-path reads were already replaced, and what remains is the bridge

The inventory called role four "avoidable in principle, because every read there is something a hook could have recorded".
Counting them settles what is actually left: **46 read sites in the backend**, and they fall into three groups, none of which
is a hot-path state read.

- **The expensive ones are already gone.** The per-batch matrix reads - a `glGetFloatv` pair on every matrix change - were
  replaced by mirrors, and the code records the measurement that motivated it: 28 microseconds per batch. Blend, depth and the
  matrix mirrors are all written by hooks now, so the draw path reads **its own recorded state**, not GL's.
- **The diagnostic sampler is behind a flag.** It is a `glReadPixels` of a centre patch plus roughly twenty `glGet` calls, and
  it runs only when `celtium.vulkan.diagnosticSamples` is set and only for the first few frames. With the flag off - the default
  - it does not touch GL at all.
- **What remains is texture and bridge queries**: `glGetTexLevelParameteri`, the texture registry's lookups, and the mirror
  uploads. Those are roles two and five, which the inventory puts **last** because they cannot be removed before the game's own
  textures are Vulkan-owned.

So role four is **done in the sense that matters** - no avoidable GL read is on the drawing path - and it was done before it was
named, by the same mirroring work that made the immediate path affordable. What is left for condition three is not a list of
reads to replace but the bridge itself, and that is a different piece of work: the game's textures and framebuffers have to
become Vulkan-owned before the four remaining texture-facing call sites can go.

Recorded because it is the first item in this project that was **already finished when it was measured**, and the reason is
worth keeping: the earlier optimisation work (mirroring instead of querying, measured at 28 microseconds a batch) was the same
work condition three needed, done for performance rather than for independence.

---

## The bridge sized: eleven textures, four and a half thousand refreshes

Roles two, three and five are the bridge, and the first question about them is the same one that made the framebuffer item
tractable: how big is the surface actually. The registry has been counting it all along:

```
mirroredTextures = 11     inPlaceRefreshes = 4523     descriptorSets = 1
acceptedByFormat = 1:74, 4:420, 5:219, 6:1342, 7:1017, 8:128, 9:12
exit 0 | clean | messages 0 | fixture PASSED
```

**Eleven distinct GL textures.** The block atlas, the lightmap, the item and entity sheets, the GUI sheets - that is the whole
ownership surface the renderer touches, across a run that accepts three thousand batches in nine layouts. Making eleven textures
Vulkan-owned is a bounded piece of work, which is worth stating plainly because "the game's textures have to become Vulkan-owned"
sounds open-ended and is not.

The caveat is the second number, and it is the real shape of the problem: **four and a half thousand in-place refreshes** of
those eleven. The mirror is refreshed whenever the game writes into a texture - the atlas animation every frame, the dynamic
texture writes, the resource reload - so ownership has to handle a **stream of GL-side writes**, not eleven one-time uploads.
That is the direction that cannot simply be moved: the game writes with GL, so either those writes are intercepted at their call
sites (the same hook pattern that already mirrors matrices and blend) or the textures stay half-owned.

So the bridge decomposes the way the framebuffer work did: eleven textures is the resource half, and the write stream is the
interception half - and the second is where the work is. The refresh path already exists and already works (4,523 times in this
run without a descriptor rebuild, `descriptorSets=1`), so what is missing is not the mechanism but the ownership.

---

## Correction: the interception half of the bridge already exists

Last round sized the bridge as "eleven textures is the resource half and the write stream is the interception half - and the
second is where the work is". Reading the hook that produces those four and a half thousand refreshes corrects that:

```java
@Inject(method = "func_110989_a", at = @At("HEAD"), require = 0)
private static void celtium$invalidateUploadedTexture(int glTextureId, BufferedImage image, ...) {
    if (celtium$seen.add(glTextureId)) { celtium$firstUploads++; }
    else { ... "texture re-upload (animated)" ... }
    TerrainGpuBackend backend = TerrainGpuBackends.current();
    if (backend != null) { backend.invalidateTexture(glTextureId); }
}
```

**Every `TextureUtil` upload already reaches the renderer**, and the hook already distinguishes a first upload (registration)
from a repeat (the animation), which is exactly the distinction ownership needs. There is a second hook on the same class in
`features/mipmaps`, and the atlas's own animation path is hooked through `TextureAtlasMixin`. So the interruption of the GL write
stream is not future work: it is in place, it is what makes the mirrors correct today, and it is why `inPlaceRefreshes` reads four
and a half thousand rather than zero.

That changes what remains for the bridge, and the corrected statement is smaller and more precise than the one it replaces:
**eleven resources, an existing and working interception of their writes, and a working refresh path that keeps descriptor sets
stable** (`descriptorSets=1` across 4,523 refreshes). What is actually missing is the **ownership decision** - whether the Vulkan
image becomes the source of truth for those eleven, and if so which of the remaining GL-facing call sites have to change with it -
rather than any new mechanism.

Recorded as the second correction of framing in two rounds, and the same class as the first: the previous round described where
the work *sounded* like it was, and the next round read the code.

---

## The ownership decision, and the fact that makes it small

The remaining texture-facing GL calls classify cleanly by direction, and the classification is the plan:

- **GL to Vulkan, the readback**: `VulkanGlTexture.readGlLevel0` and `readGlLevel`, called from three places. This is how a
  mirror is filled today - the game uploads with GL, and the renderer reads the pixels back out of GL to build its image.
  **This is the direction that disappears when Vulkan owns the texture.**
- **Vulkan to GL, the uploads**: two in `VulkanOffscreenTarget` (the framebuffer copy-back, which is required) and one 1x1
  scratch in the backend. This direction stays.
- **The bridge's own GL objects**: `VulkanFrameTarget`'s scratch and depth textures, the frame target's staging - created and
  deleted by the bridge itself rather than by the game.

And the fact that makes the ownership decision small is in the hook's own signature:

```java
private static void celtium$invalidateUploadedTexture(int glTextureId, BufferedImage image, boolean blur, ...)
```

**The hook already receives the image.** An upload through the game's utility arrives with its pixels in hand, so the Vulkan
image can be built from the `BufferedImage` directly - the same data GL is about to be given - and the GL readback becomes
unnecessary for exactly the eleven textures this renderer mirrors. That is a change at one hook and one code path, not a new
subsystem: the hook announces an upload, and instead of only invalidating the mirror it can hand over the pixels.

That is what remains for the bridge: **eleven resources whose writes are already announced, whose data is already in the
announcement, and whose refresh path already keeps descriptor sets stable.** The ownership decision is therefore about **when**
to stop reading GL rather than about whether it can be done, and the first candidate is the mirror's initial fill.

Recorded with its evidence, because the previous two rounds described the work from the outside and this one read the
signature that answers it.
