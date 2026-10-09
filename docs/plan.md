# Celtium current plan

This is the single current plan. Stage records written earlier are history and are kept in `docs/history/` and in the
measurement logs (`docs/p3-vulkan-terrain-backend.md`, `docs/validation-*.md`); where they contradict this document,
this document wins.

Last rewritten: 2026-10-09.

## 0. Supported runtime: CleanroomMC only

Celtium targets **CleanroomMC** and nothing else. Starting a game on plain Forge (the old Java 8 / LWJGL 2 stack) or on
another Forge plus lwjgl3ify/RFB configuration is **not a supported runtime** and is not something this project will
keep working. That is a statement about the *environment*, not about mods:

- **Keep:** binary compatibility with existing Forge mods **as they run on Cleanroom** - class and method signatures,
  event order, model and baked-quad data, the `Tessellator`/`BufferBuilder`/`WorldVertexBufferUploader`/
  `GlStateManager` entry points, `RenderItem`, `TESR`, `RenderWorldLastEvent`. A mod that works on Cleanroom today is
  meant to keep working, unchanged.
- **Drop:** the ability to launch on a runtime that is not Cleanroom. No compatibility shims, no "it also works on
  Forge 1.12.2" claims, no testing matrix for the old stack.

Supported and verified environment (pinned, not floating):

| Item | Value | Status |
|---|---|---|
| Minecraft | 1.12.2 | verified on hardware |
| Runtime | CleanroomMC **0.6.13-alpha** | verified on hardware (both GPUs) |
| JDK | **25** for the Gradle build; the mod is produced for the runtime's target | verified |
| LWJGL | **3.4.1** | verified |
| GPUs | AMD RX 6400 (RADV), Intel UHD Graphics 630 (ANV) | verified |
| OS | Linux, Wayland/Xwayland session | verified |

Upstream `main` moves; nothing from a newer Cleanroom foundation is applied to this environment without being pinned,
built and driven through the acceptance checks below. "Newer" is not a reason, and **stopping Java 8 output is not by
itself treated as a speed-up** - only a measurement makes that claim.

### Migration stages for the specialization

| Stage | Work | Acceptance |
|---|---|---|
| S1 | State the target: this document, the README and any startup diagnostics say Cleanroom-only | Documents agree; no text promises plain-Forge support |
| S2 | Retire the legacy-runtime code paths: `CeleritasVintageMixinPlugin.onLoad`'s RFB detection and `CeleritasLwjgl3ifyCompat` (which reaches into `SharedConfig.getRfbTransformers()` to add exclusions), after checking what still depends on them | Build and startup succeed without them; the client starts and renders on the pinned Cleanroom; no reflection into RFB remains |
| S3 | Evaluate moving the build to Cleanroom-oriented tooling, in steps that each leave a working build behind | Each step: `packageJar` + validation run PASSED, `nativeTerrainVerified True`, VUID 0, before the next step starts |
| S4 | Keep mod compatibility measured, not assumed: the fixture mod, then real mods, with `docs/mod-compatibility.md` recording what was actually observed | A mod from outside this repository renders through the Forge API path without modification |

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

### What specialization does **not** solve

Dropping the old runtime is not the same as finishing the renderer, and the plan keeps these as independent work:

- **Direct GL calls** (`GL11` matrix functions, direct texture and framebuffer use, GLSL shipped by a mod) still need
  translation or replacement; the Forge entry points never see them. `docs/mod-compatibility.md` records each pattern
  found, and any feature that will not be covered is listed there **with the reason**.
- **GL readback and synchronisation** (the texture mirrors, the shared-image copies, the semaphore handoffs) still cost
  what they cost; removing the old runtime does not remove them.
- **Image correctness** still depends on frame ownership, as the measurements in section 2 record.

And the standing rule: deleting compatibility code is not evidence of a faster renderer. Rendering and performance
comparisons continue under the same conditions as the original Celeritas reference, and any speed claim comes with the
measurement that produced it.

## 1. Completion condition

Full-screen **native Vulkan** in Minecraft 1.12.2 on Cleanroom:

1. Minecraft's own rendering, the final composition and the window present are native Vulkan.
2. Terrain, entity/tile entity, item/hand, GUI/font/HUD, particles, sky/cloud/weather, water/translucency, the
   framebuffers and postprocess passes, texture animation, resource reload, resize and world switching are all
   ported.
3. Startup and rendering are verified **without a GL context** and **without the GL/VK shared-image bridge**.

Until (3) holds on both target GPUs, the build is *hybrid* and says so. Leaving features on GL while claiming
"fully Vulkan" is not acceptable; speed is a separate goal (section 8) and never a completion criterion.

## 2. Status: implemented / verified on hardware / not implemented / blockers

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

## 3. Stages, deliverables and dependencies

Not strictly serial: stages that only need the shared abstraction can proceed once that abstraction exists.

| # | Stage | Depends on | Deliverable |
| --- | --- | --- | --- |
| 0 | Finish audit correctness items | - | no swallowed errors, no stale descriptors, correct measurement labels, honest verdicts |
| 1 | Common GPU abstraction (`common/`) | - | device-agnostic buffers, textures, samplers, pipelines, state, render targets; terrain moved onto it first as proof |
| 2 | GL-free window/device/surface/swapchain/present | 1 | `VkSurfaceKHR`, swapchain, acquire/present, resize handling; a frame rendered and presented with no GL context |
| 3 | Minecraft drawing APIs on Vulkan | 1 | draw entry (`WorldVertexBufferUploader`), state translation (`GlStateManager`), deferred pipeline cache, vertex-format pipelines for the immediate formats |
| 4 | Content port | 3 | GUI/font/HUD, sky/clouds/weather, particles, entity/TESR/item/hand, water/translucency; vanilla shader (`ShaderGroup`) subset; framebuffer/postprocess targets |
| 5 | Forge-API mods | 3, 4 | a test mod drawing inside a Forge event verified on both GPUs; then major mods, one at a time |
| 6 | Direct-GL compatibility subset and per-mod work | 3 | the GL entry points that mods call directly, mapped where the subset allows, with per-mod results recorded |
| 7 | Remove GL context and bridge; full 2-GPU verification | 2, 4, 5 | startup and rendering with no GL context, no bridge, all vanilla features, both GPUs |
| 8 | Performance and distribution | 7 | measured improvements with unchanged frames; packaging and install story |

## 4. Per-stage deliverables, acceptance and failure handling

**Stage 0 - audit correctness.** Deliverable: every audit item either fixed with evidence or recorded as open with
the reason. Acceptance: the fixture cannot report PASSED when a pass was skipped, an error was swallowed or the
backend drew nothing; validation (including synchronization validation) is clean. Failure: if a fix cannot be made
safely, the behaviour is made loud (a defect report in the debug line) rather than silent.

**Stage 1 - common abstraction.** Deliverable: one interface set for buffers, textures, samplers, pipelines, state
and render targets, with the Vulkan implementation behind it and terrain using it. Acceptance: terrain still renders
the reference frame on both GPUs through the abstraction, with no behaviour change. Failure: keep the terrain path on
its current direct calls and land the abstraction only for new code, recording why.

**Stage 2 - GL-free present.** Deliverable: window/device/surface/swapchain/present path that does not need a GL
context, with resize and image-count handling. Acceptance: a frame is rendered and presented on both GPUs with the
GL context never created, validation clean, no hang under a watchdog. Failure: if a platform lacks a usable surface
extension, that platform is reported unsupported (capability-based, not GPU-name-based) and the rest continues.

**Stage 3 - Minecraft drawing APIs.** Deliverable: the `WorldVertexBufferUploader` draw entry, the `GlStateManager`
state translation, the deferred pipeline cache, and pipelines for the immediate vertex formats. Acceptance: vanilla
immediate-mode content (at minimum particles and the GUI) is drawn by Vulkan and matches the GL render, with
validation clean and no public API signature changed. Failure: if a format or state combination is not supported,
the batch falls through to the existing path with a recorded reason, so nothing disappears silently.

**Stage 4 - content port.** Deliverable: each content area listed in section 1(2) drawn by Vulkan, plus the vanilla
shader subset and the framebuffer/postprocess targets. Acceptance: per-area image comparison against a GL reference
on both GPUs, plus a written list of what is still GL. Failure: an area that cannot be ported yet stays on GL and is
named in the status section; it is never silently dropped.

**Stage 5 - Forge-API mods.** Deliverable: a small test mod that draws inside `RenderWorldLastEvent` through
`Tessellator`/`GlStateManager`, and then real major mods. Acceptance: the test mod is compiled against unmodified
Forge/Minecraft (binary compatibility) and its output is verified as Vulkan-drawn pixels; per major mod, the entry
points it uses are recorded with the result. Failure: a mod that bypasses the Forge entry points is moved to stage 6
and recorded as such, not patched around silently.

**Stage 6 - direct GL.** Deliverable: a compatibility layer for the GL subset that can be mapped safely (state,
matrix stack, texture bind, simple draws), plus per-mod results. Acceptance: each mapped entry point has a test, and
every unmapped one is reported with the mod that used it. Failure: unmapped calls are reported, never emulated
partially in a way that produces wrong output.

**Stage 7 - remove GL.** Deliverable: startup with no GL context and no shared-image bridge, all vanilla features on
both GPUs. Acceptance: the process never creates a GL context, Vulkan presents every frame, and the full feature list
of section 1(2) is exercised. Failure: if a feature still needs GL, the removal is not done and the feature is named.

**Stage 8 - performance and distribution.** Deliverable: measured improvements with byte-identical frames on the
fixed scene; packaging and install instructions. Acceptance: no frame changes, validation clean, and each
optimisation documented in `docs/optimizations.md`. Failure: an optimisation that changes the frame is reverted or
gated, never shipped as "equivalent".

**Major-mod selection rationale (stage 5).** Pick mods that (a) draw only through Minecraft/Forge APIs, so they
measure the API swap rather than the direct-GL layer; (b) are widely installed, so the result matters to users;
(c) exercise different entry points (TESR, GUI/HUD, world overlay, item rendering); and (d) are small enough to
diagnose. Mods that call LWJGL directly are tracked separately in `docs/mod-compatibility.md`.

## 5. Compatibility rules

- Public classes, methods, fields and event order of Minecraft and Forge are **not** changed carelessly. The
  compatibility layer adds classes under Celtium's own namespace, and Mixins use `@Accessor`/`@Inject` rather than
  signature changes.
- Celtium-side Mixins are the preferred mechanism. A Cleanroom/Forge fork is a **separate scope**: it needs a
  recorded reason (a Mixin that cannot reach the behaviour), plus an impact statement for distribution and
  maintenance, before anything is forked.
- Forge paths that are transformers or data (`IVertexConsumer`, `VertexBufferConsumer`, `BakedQuad`, `IModel`,
  `TRSRTransformation`) are not rerouted; event dispatch keeps its order and signatures.
- "The Forge API swap makes every mod work" is not claimed: mods that call LWJGL, compile GLSL or use GL-only
  features cannot be captured at Forge entry points and are handled as their own effort.

## 6. GPU and platform policy

- Every capability decision comes from features, extensions and limits (`VkPhysicalDeviceFeatures`,
  `VkPhysicalDeviceVulkan12Features`, extension feature structs, queried limits) - never from a GPU name or vendor.
  Optional accelerations such as mesh shaders are used only when advertised and never required.
- The Linux opaque-FD / GL-external-object requirements belong to the **hybrid** stage. The native-only target's
  requirements are surface support, a queue family that can present, and the image/format set the new render targets
  need - a different list.
- Verified on hardware so far: AMD RX 6400 (RADV) and Intel UHD 630 (Mesa), Linux only. NVIDIA, other operating
  systems, and the major mod packs are **unverified** and are listed as such wherever results are reported.

## 7. Effort estimates (person-days, explicitly estimates)

Ranges, not commitments; they assume one experienced graphics/modding developer working full-time with the existing
harness. They are not derived from session round counts and no completion date is predicted from them.

| Stage | Estimate | Main uncertainty |
| --- | --- | --- |
| 0 audit remainder | 1-3 | how many hidden failure paths the harness still has |
| 1 common abstraction | 6-12 | how much terrain code has to move before it is proven |
| 2 GL-free present | 6-12 | surface/swapchain quirks, present ownership vs GL |
| 3 Minecraft drawing APIs | 10-20 | how many state/format combinations vanilla actually needs |
| 4 content port | 25-50 | shader (`ShaderGroup`) translation and postprocess targets |
| 5 Forge-API mods | 10-25 | per-mod surprises and how many mods are in scope |
| 6 direct GL subset + per-mod | 20-60 **separate scale** | how many mods call GL directly; full support is open-ended |
| 7 remove GL + full verification | 10-20 | features that turn out to need GL |
| 8 performance + distribution | 10-25 | how much of the bridge cost survives |

Total for stages 0-5 and 7-8: roughly **70-145 person-days**, i.e. a multi-month effort for one developer, with the
usual wide band because stage 4 depends on how faithful the shader translation has to be. Stage 6 (direct GL) is a
separate, potentially open-ended effort and is deliberately not included in that total. These numbers are estimates
to size the work, not a schedule.

## 8. What is explicitly not a completion criterion

- Frame rate or any speed-up. Optimisations land only with unchanged frames (`docs/optimizations.md`) and never
  substitute for porting.
- "Terrain backend: vulkan" in the debug overlay, or any backend-selection string.
- A hybrid build that keeps GL for entities, GUI, particles or composition.
- Identical-looking output alone: same-frame composition, documented image comparison and a validation-error-free
  run are required, but the port itself is what completes the goal.
