*Celtium was a historical proposed name for element 72, now known as hafnium. [Name origin](https://en.wiktionary.org/wiki/celtium).*

# Celtium

An experimental **native Vulkan renderer for Minecraft 1.12.2 on CleanroomMC**, built from Celeritas.

> **Vulkan-only rendering works and the two primary modpacks pass on both test GPUs, but frame-time spikes remain.**
> The measured p50 is at or below the Celeritas reference in the calibrated fixtures, while **p99 is still about 119x the p50** — the spikes, not the average, are what a player feels. Rendering and mod compatibility are incomplete, real-instance FPS work is ongoing, and this is a development project, not a finished performance mod.
>
> **Vulkanのみで描画・表示でき、主要2 Modpack は両GPUで受入済みですが、フレーム時間のスパイク（p99）が残っています。p50 は較正済み fixture で Celeritas 以下、体感を決めているのはスパイク側です。描画とmod互換性は開発中で、通常プレイ向けの完成版ではありません。**

Status updated: **2026-10-16**.

## Current status

The mode documented here is the **Vulkan-only / GL-less mode**, enabled with:

```text
-Dceltium.vulkan.glless=true
```

It creates a window without an OpenGL context (`GLFW_NO_API`), renders into Vulkan-owned images, and presents the finished frame through a Vulkan swapchain. It does not depend on OpenGL drawing, GL readback, a GL/Vulkan bridge, GL swap or GL fallback.

Terrain, entities, items, particles, sky/clouds and GUI have native rendering paths. **Having a native path does not mean every scene or feature is correct.** Unsupported or incomplete paths can still produce missing or incorrect output.

Vulkan-only startup and presentation have been exercised on **AMD Radeon RX 6400 (RADV NAVI24) and Intel UHD Graphics 630 (CFL GT2) under Linux/Mesa**, with passing fixtures and zero validation messages. Physical display scanout has not been measured.

**Accepted on both GPUs** (each line is a recorded leg with its jar hash, device line and verdict; see [docs/plan.md](docs/plan.md) for the run names):

- **E2E 1.93a (268 mods) world image gate: PASSED on AMD and Intel** — Celtium replaces Nothirium in that pack. `worldFrameBlackFraction` 0.0051 (AMD) / 0.0038 (Intel) against a 0.05 floor, with the calibration key matched and the floors enforced.
- **RLCraft 2.9.3 (165 mods) terrain: PASSED on AMD and Intel** — p50 30.6 ms (AMD) in the recipe profile.
- **scene / resize / reload / minimize: PASSED on both GPUs**, with `validationMessageCount=0`. Resize is proven by the presented image actually changing size; reload by a resource reload firing mid-run.
- **Display-list semantics are implemented**: `glNewList`/`glCallList` model geometry is captured and drawn natively, and the entities around the camera and the player model appear (verified by picture on both GPUs — the same scene without the fix had none of them).

**Not accepted, and the current work**:

- Frame-time spikes: p99 is ~119x p50, and the largest recorded per-frame cost is client tick, not the renderer. The 141 ms frames are being localised with the submission/fence instrumentation.
- The defects a user reported against 0.0.5-alpha — recorded with screenshots in [docs/reports/0.0.5-user-reported-defects.md](docs/reports/0.0.5-user-reported-defects.md). The missing translucent backdrop in the Sodium-style options screen was fixed on 2026-10-16 (before/after in [docs/evidence/sodium-tooltip-backdrop-2026-10-16.png](docs/evidence/sodium-tooltip-backdrop-2026-10-16.png)). Still open: loading-screen progress bar/text and resize follow, panorama cost and blur, the option description overlapping the setting it describes, and faint clouds.
- Framebuffer/post-process ownership, resource lifetime and recovery are still being finished.
- Ordinary Forge mod compatibility and real gameplay verification continue; the calibrated verdicts above are scoped fixtures, not every scene.

See [the current status and priorities](docs/plan.md), [the performance plan](docs/performance_plan.md), and [mod compatibility](docs/mod-compatibility.md). Historical measurements in `docs/history/` are evidence for their specific builds, not current completion claims.

## Scope and compatibility

- **Minecraft 1.12.2 / CleanroomMC only.** Stock Forge with Java 8/LWJGL2 and other Forge + LWJGL3 replacement stacks are not supported.
- Ordinary gameplay/content mods are a compatibility goal. Minecraft/Forge public entry points, model data and event semantics are preserved where possible; compatibility is **not yet complete**.
- **Competing renderer replacements are not supported alongside Celtium.** Celtium replaces the renderer, so another renderer overhaul in the same instance is not a configuration this project tests. Ordinary optimization mods are different: the test instance runs the standard 1.12.2 performance stack as **coexistence targets** (see below), and the 268-mod E2E pack passes with StellarCore installed.
- Celtium replaces Celeritas. **Install one, not both.** The internal mod ID and resource namespace remain `celeritas` for existing references.
- Shader-pack and shadow-mod compatibility is planned **after** the shaderless renderer and major gameplay mods work. Vanilla ShaderGroup, spectator effects and entity outlines remain part of the base renderer's scope.
- This is not a general OpenGL-to-Vulkan translator. Native passes follow Minecraft's terrain, model/item, entity/TESR, particle, UI, texture and framebuffer semantics.
- Mesh shaders are **not required or planned**. Optimizations must preserve a usable baseline on weaker GPUs.
- **GL-less is installed from the coremod transformer chain**, immediately before the window is created, so a mod that loads `org.lwjgl.opengl.Display` early cannot turn the run into a hybrid any more. This was a real failure until 2026-10-15: in the 268-mod pack RandomPatches defined `Display` at 8.975 s while the mixin list was still being built at 9.837 s, so the `GLFW_NO_API` hint was never applied and the window came up with a real OpenGL 4.6 context. The hint now comes from `CeltiumGllessWindowTransformer`. Read `CELTIUM_GLLESS` lines in the log to confirm which path a run took rather than assuming it.

The supported development direction is Vulkan-only. Old bridge/probe experiments and retained upstream OpenGL code are not a supported alternate renderer or a promise of custom OpenGL compatibility.

## Requirements and tested environment

| Component | Current requirement / verification |
| --- | --- |
| Minecraft | 1.12.2 |
| Loader | CleanroomMC; verified with 0.6.13-alpha. Cleanroom 0.7.x (SDL 3 instead of GLFW, Kirino Engine wired into the loader lifecycle) is **not yet a verified base** — the window creation path and the `glfwIconifyWindow` fixture would both change |
| LWJGL | LWJGL3; verified with 3.4.1. Vulkan bindings/native libraries must match the Cleanroom installation |
| Java for build and verified gameplay | JDK 25 |
| Celtium's own module bytecode | `--release 21`, class major 65 or lower; no Java 22+ API or preview dependency |
| Vulkan | 1.2+ loader and physical device, with the required graphics/presentation, format and resource capabilities |
| Verified GPUs / OS | RX 6400 and UHD 630 on Linux/Mesa |
| Other GPUs / OS | NVIDIA, other AMD/Intel models, Windows and macOS are unverified |

The exact minimum feature/format contract is still being audited and reduced. **Vulkan 1.2 support alone is not a compatibility guarantee.** Features are selected by device capabilities, not GPU name. Java 21 bytecode targeting does not constitute verification that the whole game launches on Java 21.

## Downloads

[**Celtium 0.0.5-alpha**](https://github.com/souta-lab/celtium/releases/tag/v0.0.5-alpha) (2026-10-15) is the newest published binary. [0.0.3-alpha](https://github.com/souta-lab/celtium/releases/tag/v0.0.3-alpha) is the latest non-prerelease tag; 0.0.1-alpha and 0.0.2-alpha are older pre-releases. All of them carry the same limitations described above — the fixes since 0.0.5-alpha (display lists, the Sodium-style backdrop, texture-less layouts) are in `main` and in development builds only.

## Building and trying a development build

Run Gradle with JDK 25. The build also uses the JDK 21 compilation toolchain and the JDK 8 Minecraft preparation toolchain.

```sh
./gradlew -p build-1122 :forge122:packageJar
```

The packaged jar is written under a directory named after the version, which the build takes from `build-1122/build.gradle.kts` unless `-Pceltium_release_version` overrides it:

```text
build-1122/build/libs/0.0.4-alpha/celtium-forge-mc1.12.2-0.0.4-alpha.jar
```

Use a separate Cleanroom test instance, install the packaged Celtium jar instead of Celeritas, and add `-Dceltium.vulkan.glless=true` to the instance's JVM arguments. Keep LWJGL/Vulkan bindings consistent with that instance. Development builds still carry the open defects listed above, so keep a working Celeritas setup to fall back to.

For reproducible validation and development, see [runtime validation](docs/runtime-validation.md), [AGENTS.md](AGENTS.md), [the objective](docs/goal.md) and [the performance plan](docs/performance_plan.md). Shader work has its own [follow-up plan](docs/shader-plan.md).

## Performance

**The average frame is now at or below the Celeritas reference; the spikes are not.** In the calibrated fixtures measured against Celeritas `989ebf2e` on the same GPU, world and camera: warm p50 **1.182 ms (Celtium) vs 1.196 ms (Celeritas)**, cold p50 3.595 vs 3.599 ms, and RLCraft's recipe profile at 30.6 ms (down from 39.5 ms). What is left is p99: **141.1 ms vs 140.8 ms**, about 119x the p50, and the per-frame budget in the menu fixture splits as ~18 ms client tick, ~29 ms render and 3.2 ms present — the tick cost belongs to the fixture, not to a normal instance.

Earlier rounds chased one candidate for the spurious stalls — the per-copy host readback on the title screen, measured at ~23 ms per copy and 7.4 s in total — and an A/B with `-Dceltium.vulkan.windowCopyBlit=true` removed it (readbacks 324 to 0) **without moving the p50** (52.3 vs 56.1 ms). So that stall was real but not the dominant term, which is why the remaining p99 work is instrumentation-led: fence rings, no `vkDeviceWaitIdle`/`vkQueueWaitIdle` in the main loop, and no `eAllCommands` stage masks, following the same family of defect that wgpu records for Vulkan on AMD.

Performance acceptance requires matched Celeritas/Celtium scenes, raw frame times including p95/p99, CPU/GPU work, memory usage, latency and correct images on both test GPUs. Lower draw-call counts or isolated fast fixtures are not enough. See [performance_plan.md](docs/performance_plan.md).

## License and attribution

Celtium retains Celeritas' **LGPL-3.0** licensing and upstream history. See [COPYING](COPYING), [COPYING.LESSER](COPYING.LESSER), and [the preserved upstream README and credits](docs/upstream-README.md). Third-party notices remain in the source tree and distribution.

The fixed source reference is [GTNewHorizons/Celeritas at `989ebf2e52ceed052ec112a569e0c1d172095756`](https://github.com/GTNewHorizons/Celeritas/tree/989ebf2e52ceed052ec112a569e0c1d172095756); Celeritas' upstream is [embeddedt/celeritas](https://git.taumc.org/embeddedt/celeritas).

## External optimization references

Optimizations are also derived from — or cross-checked against — the following projects. The design is re-implemented against this renderer's GL-less contract rather than copied; **every derived change names its source in the commit message, a code comment at the implementation site, and the pull request description**:

| Project | License | Used/referenced for |
| --- | --- | --- |
| [GTNewHorizons/Angelica](https://github.com/GTNewHorizons/Angelica) | LGPL-3.0 (plus its own notices for code adapted from Sodium/Iris, FalseTweaks, Beddium, Mesa) | item mesh cache (PR #21, after [PR #1034](https://github.com/GTNewHorizons/Angelica/pull/1034)); dropped-item render distance (PR #23, after [PR #653](https://github.com/GTNewHorizons/Angelica/commit/6cf119dfee1a2e6cc869a1f138513d659793d542)); candidates: upload arena, TESR mesh keys, instancing |
| [RaphiMC/ImmediatelyFast](https://github.com/RaphiMC/ImmediatelyFast) | LGPL-3.0 | concept reference: sign/TESR text buffering, framebuffer-switch reduction, immediate-mode batching |
| [FalsePattern/FalseTweaks](https://github.com/FalsePattern/FalseTweaks) | LGPL-3.0 core — **mixed**: occlusion module is CoFHTweaks-derived and NOT LGPL; packing2d MIT-based; triangulator sorting LGPL-2.1+ | concept reference: multithreaded mipmap generation, texture stitching, animated-texture optimization. The occlusion module is not used |
| [makamys/Neodymium](https://github.com/makamys/Neodymium) | LGPL-3.0 | concept reference: bounded mesh/VBO reuse, tessellator-state capture |
| [GTNewHorizons/Hodgepodge](https://github.com/GTNewHorizons/Hodgepodge) | LGPL-3.0 | concept reference: renderer memoization, leak fixes |
| [embeddedt/ArchaicFix](https://github.com/embeddedt/ArchaicFix) | LGPL-3.0 — **occlusion module excluded** (not LGPL) | concept reference: cache lifetime, world-change freeing |
| [xCollateral/VulkanMod](https://github.com/xCollateral/VulkanMod) | LGPL-3.0 | closest architecture analogue (Vulkan replaces GL, modern versions): combined render-layer draws (~80% fewer draw calls), upload buffer reuse instead of per-frame buffer recreation, redundant color-clear skipping |
| [embeddedt/ModernFix](https://github.com/embeddedt/ModernFix) | LGPL-3.0 (config derived from Sodium under LGPL-3.0) | concept reference: dynamic/lazy model loading, faster startup, memory reduction |
| [malte0811/FerriteCore](https://github.com/malte0811/FerriteCore) | MIT | concept reference: dedup blockstate property maps, multipart model instances (~200k→1.5k), cached shape internals |
| [MoePus/Flerovium](https://github.com/MoePus/Flerovium) | LGPL-3.0 | modern (1.21): item render backface culling / fast math / LOD, particle light caching, entity fast math |

## Coexisting optimization mods (not porting sources)

The test instance runs the standard 1.12.2 performance stack — these are **coexistence targets**, not reference material: porting their features into Celtium would either duplicate what they already do on the instance or conflict with them.

- [embeddedt/VintageFix](https://github.com/embeddedt/VintageFix) (LGPL-3.0) — the 1.12.2 superset: lazy model loading, blockstate/model dedup, texture memory release
- [LoliKingdom/CensoredASM (LolASM)](https://github.com/LoliKingdom/LoliASM) (LGPL-3.0) — string dedup, faster init; itself a superset of the other 1.12.2 texture/resource fixes
- Reference list: [TheUsefulLists/usefulmods — Performance 1.12.x](https://github.com/TheUsefulLists/usefulmods/blob/main/Performance/Performance112.md)

1.12.2-native mods are therefore **not** part of the reference pool: the usable sources are 1.7.10 (GTNH: Angelica, FalseTweaks, Neodymium, ArchaicFix, Hodgepodge) and modern Minecraft (VulkanMod, ModernFix, FerriteCore, ImmediatelyFast, Flerovium).

- **Item mesh cache** (`RenderItemMixin`, PR #21) — after [Angelica PR #1034, "Optimize in-world item rendering"](https://github.com/GTNewHorizons/Angelica/pull/1034) (VBO caching of held/dropped/GUI item geometry, ~4–6x on item-heavy scenes).

The inventoried candidates per project are kept in [docs/performance_plan.md](docs/performance_plan.md). Porting a listed item means: check the file/module license first (FalseTweaks and ArchaicFix contain non-LGPL islands), re-implement — do not copy code —, then credit in commit, code comment and PR.

Current Sodium source under its newer restrictive license is not imported by this project. Independent optimization work and permitted source reuse must retain their provenance and applicable notices.
