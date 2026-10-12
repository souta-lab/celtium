*Celtium was a historical proposed name for element 72, now known as hafnium. [Name origin](https://en.wiktionary.org/wiki/celtium).*

# Celtium

An experimental **native Vulkan renderer for Minecraft 1.12.2 on CleanroomMC**, built from Celeritas.

> **Vulkan-only rendering works, but Celtium is currently very slow.**
> Current builds can have severe frame-time spikes, stuttering and temporary freezes. Rendering and mod compatibility are still incomplete. This is a development project, not a finished performance mod, and it currently makes **no claim of being faster than Celeritas**.
>
> **Vulkanだけで描画・表示できますが、現状は非常に重く、大きなカクつきや一時的な停止があります。描画とmod互換性も開発中で、通常プレイ向けの完成版ではありません。**

Status updated: **2026-10-12**.

## Current status

The mode documented here is the **Vulkan-only / GL-less mode**, enabled with:

```text
-Dceltium.vulkan.glless=true
```

It creates a window without an OpenGL context (`GLFW_NO_API`), renders into Vulkan-owned images, and presents the finished frame through a Vulkan swapchain. It does not depend on OpenGL drawing, GL readback, a GL/Vulkan bridge, GL swap or GL fallback.

Terrain, entities, items, particles, sky/clouds and GUI have native rendering paths. **Having a native path does not mean every scene or feature is correct.** Unsupported or incomplete paths can still produce missing or incorrect output.

Vulkan-only startup and presentation have been exercised on **AMD Radeon RX 6400 and Intel UHD 630 under Linux/Mesa**, with passing selected fixtures and zero validation messages. These are scoped test results, not proof that every GPU, mod or game scene works. Physical display scanout has not been measured.

Current work includes:

- Fixing major frame-time spikes and high CPU rendering costs.
- Completing and correcting menu panorama, loading-screen presentation, GUI text/state and other remaining rendering defects.
- Finishing framebuffer/post-process ownership, resource lifetime and recovery.
- Extending ordinary Forge mod compatibility and verifying real gameplay.
- Benchmarking against the fixed Celeritas reference with matching workloads.

See [the current status and priorities](docs/plan.md), [the performance plan](docs/performance_plan.md), and [mod compatibility](docs/mod-compatibility.md). Historical measurements in `docs/history/` are evidence for their specific builds, not current completion claims.

## Scope and compatibility

- **Minecraft 1.12.2 / CleanroomMC only.** Stock Forge with Java 8/LWJGL2 and other Forge + LWJGL3 replacement stacks are not supported.
- Ordinary gameplay/content mods are a compatibility goal. Minecraft/Forge public entry points, model data and event semantics are preserved where possible; compatibility is **not yet complete**.
- **Other rendering overhaul and rendering optimization mods are not supported alongside Celtium.** Celtium replaces the renderer; it does not aim to coexist with competing renderer replacements.
- Celtium replaces Celeritas. **Install one, not both.** The internal mod ID and resource namespace remain `celeritas` for existing references.
- Shader-pack and shadow-mod compatibility is planned **after** the shaderless renderer and major gameplay mods work. Vanilla ShaderGroup, spectator effects and entity outlines remain part of the base renderer's scope.
- This is not a general OpenGL-to-Vulkan translator. Native passes follow Minecraft's terrain, model/item, entity/TESR, particle, UI, texture and framebuffer semantics.
- Mesh shaders are **not required or planned**. Optimizations must preserve a usable baseline on weaker GPUs.

The supported development direction is Vulkan-only. Old bridge/probe experiments and retained upstream OpenGL code are not a supported alternate renderer or a promise of custom OpenGL compatibility.

## Requirements and tested environment

| Component | Current requirement / verification |
| --- | --- |
| Minecraft | 1.12.2 |
| Loader | CleanroomMC; verified with 0.6.13-alpha |
| LWJGL | LWJGL3; verified with 3.4.1. Vulkan bindings/native libraries must match the Cleanroom installation |
| Java for build and verified gameplay | JDK 25 |
| Celtium's own module bytecode | `--release 21`, class major 65 or lower; no Java 22+ API or preview dependency |
| Vulkan | 1.2+ loader and physical device, with the required graphics/presentation, format and resource capabilities |
| Verified GPUs / OS | RX 6400 and UHD 630 on Linux/Mesa |
| Other GPUs / OS | NVIDIA, other AMD/Intel models, Windows and macOS are unverified |

The exact minimum feature/format contract is still being audited and reduced. **Vulkan 1.2 support alone is not a compatibility guarantee.** Features are selected by device capabilities, not GPU name. Java 21 bytecode targeting does not constitute verification that the whole game launches on Java 21.

## Downloads

[Celtium 0.0.1-alpha](https://github.com/souta-lab/celtium/releases/tag/v0.0.1-alpha) is the first experimental binary release. It has the same severe performance and rendering limitations described above.

## Building and trying a development build

Run Gradle with JDK 25. The build also uses the JDK 21 compilation toolchain and the JDK 8 Minecraft preparation toolchain.

```sh
./gradlew -p build-1122 :forge122:packageJar
```

The packaged jar is written under:

```text
build-1122/build/libs/<version>/
```

Use a separate Cleanroom test instance, install the packaged Celtium jar instead of Celeritas, and add `-Dceltium.vulkan.glless=true` to the instance's JVM arguments. Keep LWJGL/Vulkan bindings consistent with that instance. Expect serious performance and rendering problems.

For reproducible validation and development, see [runtime validation](docs/runtime-validation.md), [AGENTS.md](AGENTS.md), [the objective](docs/goal.md) and [the performance plan](docs/performance_plan.md). Shader work has its own [follow-up plan](docs/shader-plan.md).

## Performance

**The current renderer is very heavy and can feel much worse than a normal Celeritas setup.** Some local optimizations have landed, including font rendering, shared geometry buffers and upload/draw batching, but these do not establish a whole-game FPS advantage or resolve the remaining stalls.

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

The inventoried candidates per project are kept in [docs/performance_plan.md](docs/performance_plan.md). Porting a listed item means: check the file/module license first (FalseTweaks and ArchaicFix contain non-LGPL islands), re-implement — do not copy code —, then credit in commit, code comment and PR.

Current Sodium source under its newer restrictive license is not imported by this project. Argentum and Smoothfont are all-rights-reserved and are never used as references. Independent optimization work and permitted source reuse must retain their provenance and applicable notices.
