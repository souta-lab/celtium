# Implementation roadmap

The first increment establishes the fork and a capability-query tool. All terrain still uses OpenGL.

| Stage | Scope | State |
| --- | --- | --- |
| P0 | Pin upstream, build baseline, record an installed Cleanroom/modpack configuration | Upstream pinned; mod build blocked on plugin dependency resolution; installed runtime not yet identified |
| P1a | Isolated GL/Vulkan capability query and fail-closed eligibility tests | CLI built; 15 tests passed; RX 6400 and UHD 630 queries recorded |
| P1b | External image import, color/depth in both directions, binary semaphores, resize and cost measurement | Pending |
| P2 | Extract the narrow terrain GPU boundary; verify the GL backend against stock Celeritas | Pending |
| P3 | Vulkan mesh upload, arena, atlas/lightmap and SOLID rendering | Pending |
| P4 | Cutout/translucency, index updates, resource reload and deferred destruction | Pending |
| P5 | Batching, performance work and declared modpack regression | Pending |

## Capability-query scope

The Linux query assumes opaque-FD handles, optimal 2D images, transfer-source and transfer-destination usage, and single-sample targets. Color images also require color-attachment usage; depth images require depth/stencil-attachment usage.

The initial experiment's required formats are `R8G8B8A8_UNORM` and `D24_UNORM_S8_UINT`. Other formats are reported separately. This does not establish the format of an arbitrary mod's active FBO or permit conversion between depth formats.

Eligibility checks UUID identity, GL and Vulkan FD extensions, Vulkan 1.2, both semaphore directions, and both required formats. Software Vulkan devices are rejected as the hardware target. GL/Vulkan driver UUID differences are reported as unverified cross-driver interoperability.

No successful capability result changes the renderer. Actual GL import and image-layout transitions may still fail. Device capabilities do not prove that depth blits to an existing Minecraft renderbuffer are valid.

## Runtime isolation

The CLI creates and destroys its own surfaceless EGL context. It can run without a desktop session or a Minecraft instance.

The in-game command uses the existing context, issues capability queries, and never creates or binds an FBO or texture. It runs only after an explicit command, through Minecraft's render-thread task queue. Stock startup has no Vulkan-loader initialization. The Forge jar retains `celeritas` as its mod ID and existing hooks.

## Required evidence before P2

1. Identify the actual stable Cleanroom, Celeritas, Java, LWJGL, GPU driver and modpack versions.
2. Verify GL-written depth affects Vulkan drawing and Vulkan-written depth affects subsequent GL drawing.
3. Verify color/depth/stencil format and sample compatibility against the active Minecraft FBO.
4. Exercise resize, recreation and delayed resource retirement.
5. Measure full-frame synchronization and copy cost with the expected terrain pass sequence.

The design's effort ranges are provisional. Re-estimate P2 onward after this evidence is available.

See [the initial validation record](validation-2026-10-08.md) and [the Codex handoff](HANDOFF.md). On the tested RX 6400/RADV configuration, the required D24 external-image query fails while D32 succeeds. This is a format-specific gate to investigate, not evidence of successful depth sharing or a blanket exclusion of Vulkan on this GPU.
