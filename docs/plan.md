# Celtium current plan

Celtium moves Minecraft 1.12.2's rendering to **native Vulkan**: the game's own drawing, the final composition and the
present, with no GL context and no shared-image bridge. This file is the current plan and the only one that describes
what is being built now. Evidence, measurements and disproved hypotheses live in
[the 2026-10-09 investigation log](history/2026-10-09-investigation-log.md); the Sodium comparison detail lives in
[Sodium optimization deltas](sodium-optimization-deltas.md). Where an older document disagrees with this one, this one
wins.

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
| Immediate path end to end | **On by default and pixel-identical to GL on both GPUs** | Measured with no flags at all against each GPU's own GL reference: **0.00 mean, 0.0% of pixels over 8 and over 32 on the RX 6400 and on the UHD 630**, run PASSED with `nativeTerrainVerified True`. Defaults are `immediate=true`, `immediateWindows=true`, `immediateFlushIndices=512` - the window flush is what keeps the content lag-free - with the acceptance mask `1,3,4,5` | Local comparison (same build, immediate off vs on, terrain Vulkan in both): **mean 0.00, 0.0% over 8 and over 32 on AMD RX 6400 and on Intel UHD 630**, 1725 and 1151 batches drawn by Vulkan respectively, VUID 0. The default acceptance mask is `1,3,4,5`: the 24-byte `POSITION_TEX_COLOR` family is the final full-screen composite and is left to GL until present ownership is settled |
| Native present of the game's frames | **Runs and presents the frame's content; not validation-clean** | Vulkan blits the shared colour image into an acquired swapchain image and presents it at the composite - `Framebuffer.func_178038_a`'s moment, identified by stack trace - with the acquire/render-finished semaphore pair; the presented image's centre patch measured 118.25, in the same range as the game framebuffer, so the screen is given the frame rather than a blank surface. **Correction:** an earlier row here claimed this was validation-clean; that run had the probe off, and the probe is what created the swapchain, so it presented nothing and was clean for that reason. The standalone present emits **36 validation messages**, identical on RADV and ANV, with the submission demonstrably waiting the acquire semaphore, a fresh semaphore pair per present, and a stage mask covering all commands - so the remaining explanations are the layer's view of semaphores on a device created for external semaphore and memory, or its reporting of the presentation engine releasing the image. The flag stays off by default |
| Frame ownership, draw ordering, image layouts | **Not implemented** | Two mid-frame window attempts failed; see the log |
| **World switch** (leave the world, join it again) | **Verified on both GPUs** | The fixture makes the same three calls `GuiIngame`'s "Save and Quit to Title" makes - `sendQuittingDisconnectingPacket`, `loadWorld(null)`, `displayGuiScreen(new GuiMainMenu())` - at tick 100 and then joins the same save again. **PASSED, `nativeTerrainVerified True`, left and rejoined at tick 100, 885 passes on the RX 6400 and 591 on the UHD 630.** The first attempt omitted the third call and crashed vanilla's renderer on a null player, which is recorded in the history as a fixture bug rather than a renderer one |
| **Resource reload** (chunk renderers rebuilt, textures re-stitched) | **Verified on both GPUs** | Triggered mid-run from the fixture the way F3+T does it - `mc.renderGlobal.loadRenderers()` plus `mc.getTextureManager().onResourceManagerReload(...)` at tick 100 - and the run stays green while the Vulkan terrain path keeps drawing: **PASSED, `nativeTerrainVerified True`, 901 passes on the RX 6400 and 591 on the UHD 630**. The texture half is what exercises the upload mirror, since a re-stitched atlas has to be re-read rather than drawn from its first upload |
| Texture uploads: registration and **animation frames** | **Implemented and verified firing** | Both of Minecraft's upload paths are hooked - `TextureUtil.func_110989_a` for registration and `func_147955_a` for sprite animation frames, each invalidating the mirror so the next draw re-reads the GL texture. Firing was observed in a green run (ids 7, 8, 13, 16, 17 and five sprite frames). A still comparison cannot show motion, so the animated result is a visual check |
| **Resize** | **Verified on hardware** | The fixture drives Minecraft's own `resize(width, height)` - public, and independent of the window manager, which is why the earlier attempt through `glfwSetWindowSize` never reached the game. Mid-run 1280x720 to 1024x640: **PASSED, `nativeTerrainVerified True`, 1652 passes, 0 validation messages, capture and screenshot both 1024x640**. It also exposed a real defect - the frame target's framebuffers were destroyed while a command buffer was still using them - which is fixed by settling the GPU before the target is recreated |
| GUI/font/HUD visible | **A real difference, localised; cause not yet identified** | With the first-person item's animation frozen the comparison has a usable floor: **reference against reference 0.07 mean (0.05% over 8)**, immediate path against a reference **1.63 mean (1.29% over 8)** - twenty-three times the floor, so there is a difference to explain. Region by region: **HUD hotbar 0.00%, crosshair 0.00%, chat/overlay strip 0.00%**, while the **hand and held item differ by 4.60%** and the world band around it by 5.42%. So the HUD is drawn correctly and the item - or what the item's drawing changes around it - is not; the next step is a run with an empty hand, which separates the two without guessing |
| Remaining content (sky/clouds/weather, water, FBOs, post-processing
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
4. **Removing GL**, then re-verifying startup and rendering without a GL context or the bridge.
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
