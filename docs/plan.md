# Celtium current plan

This is the single current plan. Stage records written earlier are history and are kept in `docs/history/` and in the
measurement logs (`docs/p3-vulkan-terrain-backend.md`, `docs/validation-*.md`); where they contradict this document,
this document wins.

Last rewritten: 2026-10-09.

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
- **Stage 5 capture evidence (mod-style drawing in a Forge event).** The validation fixture now has a
  `RenderWorldLastEvent` handler that draws through `Tessellator`/`BufferBuilder` and `GlStateManager` exactly as a
  content mod does, with no Celtium-specific call, gated behind `-Dceltium.validation.drawTestQuad=true` so reference
  frames are unaffected. Verified on hardware: `modDrawTestFrames=574` while `immediateOffered` rises from 8365 (the
  baseline run) to **9078** and `immediateState` from 103691 to 106058 - the mod-style batches and state calls really
  reach Celtium's Vulkan entry points, are refused with a stated reason (the per-format pipelines are missing) and are
  drawn by GL as before, with the run clean (PASSED, no validation message). This is the "(a) binary compatibility"
  half of the stage-5 evidence: a Forge-API mod is captured without being changed; the "(b) drawn by Vulkan" half
  needs the pipelines below, and real major mods come after that.

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
