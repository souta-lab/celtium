# Sodium upstream: lineage, licence boundary and optimization deltas

This is the detail behind `docs/plan.md` section 8. It exists so the plan can stay short while the evidence, the
licence reasoning and the candidate list remain recorded and checkable.

## Lineage and licence boundary

Celtium descends from a pinned Celeritas base (`989ebf2e52ceed052ec112a569e0c1d172095756`), which is itself a fork of
Embeddium - the last FOSS-licensed Sodium base - plus Oculus 1.7, and its own README credits "Sodium 0.5.11 & older".
So the lineage is **not** "Celeritas equals unmodified Sodium 0.5.11": this repository's own history contains ports in
both directions, for instance `18d65a9c4 Port BE iteration optimization from Embeddium 1.21+` and
`35b8846aa Port Embeddium's buffer builder sizing fix`, which is why the comparison below has a column for whether the
idea is already implemented here rather than a column for which project it came from.

**Licence boundary, checked against the primary source rather than assumed.** `docs/upstream-README.md` records this
project as LGPL-3.0 (`COPYING.LESSER`). Sodium's `dev` branch `LICENSE.md` is **PolyForm Shield 1.0.0**, whose
*Noncompete* clause permits any purpose "except for providing any product that competes with the software", and whose
*Competition* clause says goods and services compete "even when they provide functionality through different kinds of
interfaces or for different technical platforms ... even if they're written in different programming languages or for
different computer architectures". A Vulkan renderer for Minecraft is a competing product by that definition.

Consequences, which are rules rather than preferences:

- **No code from Sodium 0.6 or later enters this repository** - not cherry-picked, not copied, not mechanically
  translated, not renamed. That includes the current `mc26.3-0.9.2` / `0.9.3-alpha` line and its Vulkan path.
- **The legitimate porting source is the FOSS line** the project already descends from and ports from: the pinned
  Celeritas/Embeddium code and Sodium 0.5.11-and-older, whose licence is compatible with LGPL-3.0. Anything taken from
  there keeps its notices and attribution.
- **Ideas are not code.** Public release notes, issue descriptions and general algorithms (arena allocation, staging
  ring buffers, indirect batching, fast-path vertex encoders) may inform an **independent implementation**, with the
  source recorded next to the code and the reasoning written down. An independent implementation is judged on its own
  merits: rewriting a restricted implementation does not launder it, so restricted sources are not consulted for
  implementation detail, and if a port would need permission it is **not adopted automatically** - the reason is
  recorded here instead.

### Optimization deltas worth evaluating

Read as a work list, not as commitments. Versions are pinned when a comparison is actually run (`mc26.3-0.9.2` at
`03198cb` at the time of writing, re-checked before use). "Already here?" asks whether the pinned base or Celtium
already does it, and "Apply?" records the conditions for 1.12.2/Cleanroom/Vulkan.

| Candidate | Source (pinned) | Already here? | Apply? | Hypothesis / risk | Priority |
|---|---|---|---|---|---|
| Incrementally defragmenting, auto-sizing multi-arena allocator | Sodium 0.9.x release notes (idea only) | Region allocator and `GlBufferArena`/`MappedStagingBuffer` exist; no defragmenting pass | Yes, as independent work: a Vulkan-side arena with defragmentation and growth | Fewer resize stalls and less fragmentation on long sessions; risk is moving live regions mid-frame, so it needs the frame-ownership work first | Later (after frame ownership) |
| Buffer recycling, upload/staging reuse, frame pacing fixes | Sodium 0.9.x release notes (idea only) | `VulkanHostBuffer`, `VulkanDynamicMesh`, staging buffers exist; recycling policy is minimal | Yes: measure upload stalls, then add recycling/ring semantics | Cuts allocation churn and CPU stalls; risk of using a buffer still in flight, needs the existing deferred-release pattern | High (CPU path) |
| Draw command batching / indirect | Sodium 0.9.x notes (VKIndirectContext capacity) | `DrawCommandList`, `MultiDrawBatch`, `IndirectMultiDrawBatch` exist in the GL path; the Vulkan backend records per-region draws | Evaluate: a Vulkan indirect path for the terrain backend | Fewer draw calls per pass; risk is the extra indirection cost at low region counts | Medium |
| Item meshing/rendering and an entity-format fast quad encoder | Sodium 0.9.x notes (idea only) | Our immediate path has per-format pipelines and does raw vertex copies (no encoder step) | Partly: the copy path is already encoder-free; the meshing side belongs to the content stages | Faster item/entity submission; the risk is doing format conversion on the CPU that a pipeline can do | Medium (stage 4) |
| Visibility/mesh generation/translucency ordering | Sodium 0.9.x notes + pinned base | `VintageRenderSectionManager`, `ChunkBuildOutput`, region sorting exist; translucency parity still open in section 2 | Yes: keep working the existing translucency difference | Sorting correctness affects both image and fill rate | High (image parity) |
| State changes, synchronisation, handoff | Sodium 0.9.x notes (idea only) | Semaphore handoff per pass, measured ~130 us; per-batch GL matrix read dominates the immediate path | Yes: reduce handoffs and stop querying GL per batch | Directly attacks the measured costs; risk is the layout-transition redesign the flush-point window already exposed | Highest |
| Modern-Minecraft-specific fixes (neoforge hooks, new render pipelines, mesh shaders) | Sodium 0.9.x | Not applicable: 1.12.2 has none of those subsystems | No | Applying them would be chasing an API, not an optimisation | Rejected |
| Anything requiring code from Sodium 0.6+ | Sodium 0.9.x | - | **No** (licence) | - | Rejected |

How a candidate is judged before it is kept: same conditions as the original Celeritas reference and as the current
Celtium build (same scene, same capture point, same duration), on **both** GPUs, comparing CPU and GPU time and the
frame-time distribution, with the immediate path's counters, the validation output and the image checked. Mesh shaders
and other optional features are never made mandatory. As the standing rule says, **no FPS claim follows from
specialisation or from deleting compatibility code** - only from a measurement taken this way.

## Candidate deltas

Read as a work list, not as commitments. Versions are pinned when a comparison is actually run (`mc26.3-0.9.2` at
`03198cb` at the time of writing, re-checked before use). "Already here?" asks whether the pinned base or Celtium
already does it, and "Apply?" records the conditions for 1.12.2/Cleanroom/Vulkan.

| Candidate | Source (pinned) | Already here? | Apply? | Hypothesis / risk | Priority |
|---|---|---|---|---|---|
| Incrementally defragmenting, auto-sizing multi-arena allocator | Sodium 0.9.x release notes (idea only) | Region allocator and `GlBufferArena`/`MappedStagingBuffer` exist; no defragmenting pass | Yes, as independent work: a Vulkan-side arena with defragmentation and growth | Fewer resize stalls and less fragmentation on long sessions; risk is moving live regions mid-frame, so it needs the frame-ownership work first | Later (after frame ownership) |
| Buffer recycling, upload/staging reuse, frame pacing fixes | Sodium 0.9.x release notes (idea only) | `VulkanHostBuffer`, `VulkanDynamicMesh`, staging buffers exist; recycling policy is minimal | Yes: measure upload stalls, then add recycling/ring semantics | Cuts allocation churn and CPU stalls; risk of using a buffer still in flight, needs the existing deferred-release pattern | High (CPU path) |
| Draw command batching / indirect | Sodium 0.9.x notes (VKIndirectContext capacity) | `DrawCommandList`, `MultiDrawBatch`, `IndirectMultiDrawBatch` exist in the GL path; the Vulkan backend records per-region draws | Evaluate: a Vulkan indirect path for the terrain backend | Fewer draw calls per pass; risk is the extra indirection cost at low region counts | Medium |
| Item meshing/rendering and an entity-format fast quad encoder | Sodium 0.9.x notes (idea only) | Our immediate path has per-format pipelines and does raw vertex copies (no encoder step) | Partly: the copy path is already encoder-free; the meshing side belongs to the content stages | Faster item/entity submission; the risk is doing format conversion on the CPU that a pipeline can do | Medium (stage 4) |
| Visibility/mesh generation/translucency ordering | Sodium 0.9.x notes + pinned base | `VintageRenderSectionManager`, `ChunkBuildOutput`, region sorting exist; translucency parity still open in section 2 | Yes: keep working the existing translucency difference | Sorting correctness affects both image and fill rate | High (image parity) |
| State changes, synchronisation, handoff | Sodium 0.9.x notes (idea only) | Semaphore handoff per pass, measured ~130 us; per-batch GL matrix read dominates the immediate path | Yes: reduce handoffs and stop querying GL per batch | Directly attacks the measured costs; risk is the layout-transition redesign the flush-point window already exposed | Highest |
| Modern-Minecraft-specific fixes (neoforge hooks, new render pipelines, mesh shaders) | Sodium 0.9.x | Not applicable: 1.12.2 has none of those subsystems | No | Applying them would be chasing an API, not an optimisation | Rejected |
| Anything requiring code from Sodium 0.6+ | Sodium 0.9.x | - | **No** (licence) | - | Rejected |

How a candidate is judged before it is kept: same conditions as the original Celeritas reference and as the current
Celtium build (same scene, same capture point, same duration), on **both** GPUs, comparing CPU and GPU time and the
frame-time distribution, with the immediate path's counters, the validation output and the image checked. Mesh shaders
and other optional features are never made mandatory. As the standing rule says, **no FPS claim follows from
specialisation or from deleting compatibility code** - only from a measurement taken this way.
