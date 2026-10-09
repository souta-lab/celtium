# Celtium current plan

Audit records: [first](audit-2026-10-09.md), [second](audit-2026-10-09-second.md).

Celtium moves Minecraft 1.12.2's rendering to **native Vulkan**: the game's own drawing, the final composition and the
present, with no GL context and no shared-image bridge. This file is the current plan and the only one that describes
what is being built now. Evidence, measurements and disproved hypotheses live in
[the 2026-10-09 investigation log](history/2026-10-09-investigation-log.md); the Sodium comparison detail lives in
[Sodium optimization deltas](sodium-optimization-deltas.md). Where an older document disagrees with this one, this one
wins.

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
| Terrain on Vulkan (both GPUs) | **Verified** | Per-pass handoff; `nativeTerrainVerified True`; ~1600 passes per run; VUID 0 |
| Per-pass handoff, uniform pre-reservation, backend counters | **Verified** | Audit fixes P0/P1/P2 kept; handoff ~130 us measured |
| GL state mirrored from `GlStateManager` (matrices, blend, depth, cull, alpha, texture, colour) | **Verified** capturing; **not faithful** for the world matrices | Mirror vs live measured: proj 2.0, view 2000.0 apart |
| Immediate draw entry (`WorldVertexBufferUploader`) with `Accessor` and structural format ids | **Verified** capturing | ~10k batches offered per run, ~2.8k accepted, no refusals |
| Immediate pipelines: `POSITION_COLOR`, `BLOCK`, `ITEM`, particle | **Implemented and pixel-identical to GL** | Verified on both GPUs (0.00 local difference) |
| Immediate pipeline: `POSITION_TEX_COLOR` | **Implemented; excluded by default** | In this scene it is the final full-screen composite, not content; drawing it into the game framebuffer flattens the picture |
| Immediate path: frame-late deferral **removed** | **Verified on both GPUs** | The mid-frame window limit is sixteen per frame rather than one: measured pixel-identical to GL (0.00) with no measurable cost (p50 16.07ms and p99 17.90ms at sixteen against 16.13ms and 18.16ms at one). The old one-window measurement that blackened the frame predates the submit-wait fix and had that defect as its explanation |
| *Transitional:* **immediate path, replacement demonstrated** | **Verified on hardware** | For the first time the injector's cancellation is effective (the audit's A) *and* the submit call exists (a defect introduced while cleaning a dead branch), so the counters and the log agree: **`immediateOffered=9060`, `immediateTaken=585`, `immediateFlushes=585`, and the vanilla upload body completing 535 fewer times than it is offered** - GL is skipped for exactly the batches Vulkan takes. Measured with the sound instrument (hidden GUI, same-revision references, floor 0.00): **the frame matches GL to nine pixels out of 921,600**, where before the fix every comparison had GL drawing the same content |
| *Transitional:* immediate path end to end | **On by default and pixel-identical to GL on both GPUs** | Measured with no flags at all against each GPU's own GL reference: **0.00 mean, 0.0% of pixels over 8 and over 32 on the RX 6400 and on the UHD 630**, run PASSED with `nativeTerrainVerified True`. Defaults are `immediate=true`, `immediateWindows=true`, `immediateFlushIndices=512` - the window flush is what keeps the content lag-free - with the acceptance mask `1,3,4,5` | Local comparison (same build, immediate off vs on, terrain Vulkan in both): **mean 0.00, 0.0% over 8 and over 32 on AMD RX 6400 and on Intel UHD 630**, 1725 and 1151 batches drawn by Vulkan respectively, VUID 0. The default acceptance mask is `1,3,4,5`: the 24-byte `POSITION_TEX_COLOR` family is the final full-screen composite and is left to GL until present ownership is settled |
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

1. **Frame ownership.** The bridge owns the shared images only inside a terrain pass, one window per frame and early in
   it, so immediate batches that arrive later are drawn against the next frame's colour and depth buffers while
   carrying the previous frame's matrices. That is one defect with three faces: the image difference above, the ~27 us
   spent per batch reading the live GL matrices, and the two failed attempts to open a second window mid-frame (the
   image layouts are written for exactly one handoff per frame). It is the prerequisite for the image and for the
   per-batch cost, not a later optimisation.
2. **The world matrices are invisible to our Mixins.** They are set through direct GL calls: `GlStateManager` wraps only
   `glOrtho`, the GLU helper is absent at runtime, and mixing into LWJGL's own `GL11` stops the client during startup.
   Until the renderer supplies the matrices itself, reading them back per batch is the only correct option.
3. **Present ownership.** The swapchain path works as a probe; making Vulkan present the game's frames is a distinct
   piece of work that belongs with frame ownership, because both decide who owns the images at which moment.

## 6. Work order and dependencies

Foundational work comes first because it unblocks everything else; this is a dependency order, not a strict sequence -
content work that does not touch frame structure can proceed in parallel.

1. **Frame ownership and present ownership** (with draw ordering and image-layout bookkeeping), including the commit
   point where Vulkan presents. Blocks the image, the per-batch cost and GL removal.
2. **Content port** on top of it: GUI/font/HUD, sky/clouds/weather, water, translucency ordering, FBOs and
   post-processing, texture animation, resource reload, resize, world switch.
3. **Forge-API mod verification** with the fixture, then real mods, recorded per mod.
4. **Removing GL state reads.** The immediate path reads the live projection and modelview with two `glGetFloatv` calls
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
