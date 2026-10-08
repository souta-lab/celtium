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
- `vkCreateSwapchainKHR` path: an argument check inside LWJGL throws `NullPointerException` after surface creation
  and the present-support check succeed. The create-info's values are now captured and all of them are spec-valid
  (`format=44` BGRA8, `usage=18` colour-attachment|transfer-dst, `sharing=1` concurrent with one family and a
  non-null list, `extent=1280x720`, `minImages=4`, `transform=1`, `compositeAlpha=1`, `presentMode=1` mailbox,
  `oldSwapchain=0`, `pNext=0`), and decompiling the LWJGL wrapper shows the failure is its
  `Checks.check(long)` on one of the pointers it forwards to the native call, not the `(Buffer,int)` check that the
  queue-family list and the output buffer satisfy. An explicit allocation-callbacks struct instead of a null
  allocator did not change it. Next: find which forwarded pointer is zero, for example by calling the native
  function directly with the same values or by reproducing the creation outside the mod.
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
