# Implementation roadmap

The first increment establishes the fork and a capability-query tool. All terrain still uses OpenGL.

| Stage | Scope | State |
| --- | --- | --- |
| P0 | Pin upstream, build baseline, record an installed Cleanroom/modpack configuration | Forge jar and contents verified; supplied Cleanroom runtime locked; stock/fork flat-world startup and FBO observations recorded; representative performance/scene coverage still pending |
| P1a | Isolated GL/Vulkan capability query and fail-closed eligibility tests | CLI built; 15 tests passed; RX 6400 and UHD 630 queries recorded |
| P1b | External image import, color/depth in both directions, binary semaphores, resize and cost measurement | RGBA8 / D32 sharing and D24 conversion verified on RX 6400 and UHD 630; standalone recreation/retirement/cost and real Cleanroom FBO tested; contract restricted to no stencil, one sample |
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

The in-game command uses the existing context, issues capability queries, and never creates or binds an FBO or texture. It runs only after an explicit command, through Minecraft's render-thread task queue. Stock startup has no Vulkan-loader initialization. The Forge jar retains `celeritas` as its mod ID and existing hooks. A separately built validation mod drives a disposable flat world, invokes the commands and observes the target through named GL queries. It is not included in Celtium's jar.

## Required evidence before P2

Capability-based selection and multiple GPU support are core completion requirements, at the user's explicit request. An RX 6400-only implementation is not a finished backend. Selection must use the current GL device/driver UUIDs and the exact image/semaphore/queue/feature contract. The agreed physical validation targets are RX 6400 and UHD 630; the user has no NVIDIA hardware, so NVIDIA remains unverified and is not a required physical test environment. Retain GL with a stated reason on unsupported configurations. Additional operating systems remain P6 scope.

1. Identify the actual stable Cleanroom, Celeritas, Java, LWJGL, GPU driver and modpack versions.
2. Verify GL-written depth affects Vulkan drawing and Vulkan-written depth affects subsequent GL drawing.
3. Verify color/depth/stencil format and sample compatibility against the active Minecraft FBO.
4. Exercise resize, recreation and delayed resource retirement.
5. Measure full-frame synchronization and copy cost with the expected terrain pass sequence.

The design's effort ranges are provisional. Re-estimate P2 onward after this evidence is available.

See [the validation record](validation-2026-10-08.md), [interop reproduction](interop-validation.md) and [the Codex handoff](HANDOFF.md). The RX 6400 D24 query still fails. Successful D32 sharing with explicit D24 conversion is established by the separate experiment, rather than inferred from that query. The supported source contract is RGBA8 / D24 renderbuffer, no stencil, one sample. Other targets must retain GL until separately validated.
