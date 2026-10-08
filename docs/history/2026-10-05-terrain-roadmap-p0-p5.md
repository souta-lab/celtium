# Implementation roadmap

> **Superseded on 2026-10-09 by [`docs/plan.md`](../plan.md).** This was the P0-P5 terrain-only roadmap; the
> goal is now full-screen native Vulkan with the Forge/Minecraft API swap.

The first increment establishes the fork and a capability-query tool. All terrain still uses OpenGL.

| Stage | Scope | State |
| --- | --- | --- |
| P0 | Pin upstream, build baseline, record an installed Cleanroom/modpack configuration | Forge jar and contents verified; supplied Cleanroom runtime locked; stock/fork flat-world startup and FBO observations recorded; representative performance/scene coverage still pending |
| P1a | Isolated GL/Vulkan capability query and fail-closed eligibility tests | CLI built; 15 tests passed; RX 6400 and UHD 630 queries recorded |
| P1b | External image import, color/depth in both directions, binary semaphores, resize and cost measurement | RGBA8 / D32 sharing and D24 conversion verified on RX 6400 and UHD 630; standalone recreation/retirement/cost and real Cleanroom FBO tested; contract restricted to no stencil, one sample |
| P2 | Extract the narrow terrain GPU boundary; verify the GL backend against stock Celeritas | Boundary extracted behind `TerrainGpuBackend`, selection is fail-closed and reported; stock and Celtium captures are pixel-identical on RX 6400 and UHD 630 for the fixed flat world |
| P3 | Vulkan mesh upload, arena, atlas/lightmap and SOLID rendering | Device bootstrap done: a logical device is created for the GL GPU's UUID on both targets and destroyed again; upload, arena and SOLID draws pending |
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

1. Identify the actual stable Cleanroom, Celeritas, Java, LWJGL, GPU driver and modpack versions. **Recorded.**
2. Verify GL-written depth affects Vulkan drawing and Vulkan-written depth affects subsequent GL drawing. **Verified by the interop experiment on both GPUs.**
3. Verify color/depth/stencil format and sample compatibility against the active Minecraft FBO. **Verified for RGBA8 colour and a D24 depth renderbuffer, no stencil, one sample; other contracts are not covered.**
4. Exercise resize, recreation and delayed resource retirement. **Verified by the interop experiment; retirement is fence-based.**
5. Measure full-frame synchronization and copy cost with the expected terrain pass sequence. **Only limited copy/sync costs are measured; the full terrain pass sequence does not exist yet.**

P2 is complete as the boundary and GL-parity stage; the partial item 5 stays open and is re-estimated with P3.
See [the P2 record](p2-backend-boundary.md).

## P3 sequence

1. Device bootstrap: create the Vulkan instance/device for the GL device's UUID, with the required extensions and
   the interop contract already proven in P1b. Diagnostics, not a fallback, when bindings or the contract are
   missing; the selection stays `opengl` and says why. **Done — see [the bootstrap record](p3-vulkan-device-bootstrap.md).**
2. Backend-neutral upload records: vertices, indices, vertex format, pass, per-face ranges and generation, so the
   GPU placement decision is no longer read out of GL arena state.
3. Vulkan arena and mesh upload through the boundary, with the existing CPU meshing, graph and sort code untouched.
4. Atlas and lightmap binding for the SOLID pass only, reusing the interop path validated on both GPUs.
5. Compare against GL with the P2 fixture: same visible sections per pass, then address cutout/translucency (P4),
   batching and performance (P5).

The design's effort ranges are provisional. Re-estimate P2 onward after this evidence is available.

See [the validation record](validation-2026-10-08.md), [interop reproduction](interop-validation.md) and [the Codex handoff](HANDOFF.md). The RX 6400 D24 query still fails. Successful D32 sharing with explicit D24 conversion is established by the separate experiment, rather than inferred from that query. The supported source contract is RGBA8 / D24 renderbuffer, no stencil, one sample. Other targets must retain GL until separately validated.
