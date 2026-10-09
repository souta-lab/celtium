# Second external audit - 2026-10-09

Basis HEAD `16af6653a`; re-checked at `dee914e05`. 58 files compared against cached Minecraft sources, the real
Cleanroom profile's Mixin bytecode, the shipped jar's annotations, and the existing AMD/Intel logs and PNGs. No edits,
builds or GPU runs were made from the auditor's side. See [the first audit](audit-2026-10-09.md) for its items and
[the plan](plan.md) for the stages these belong to.

## What the first audit's "fixed" claims are worth now

Three of them do not survive, and the history file records each correction:

| Claim | Status |
|---|---|
| the immediate path draws natively (adoption count = replacement count) | **withdrawn** - the cancellation was ineffective (S03) and the submit call was later deleted entirely |
| matching images mean the Vulkan implementation is correct | **withdrawn** - GL drew the same content in every earlier comparison |
| the first audit's B and E are fully resolved | **reopened** - B's ownership/layout handoff is unimplemented (S01); E's timeout path still reuses command buffers (S02) |

## Items

### S01 [P0, `presentFromComposite=true` only] the shared image's present contract is unimplemented
The seven-argument `presentImage` received the bridge's semaphores and never used them; the submission waits one and
signals one, while `endFrame` makes GL wait on the bridge's done semaphore. The blit's barriers use
`QUEUE_FAMILY_IGNORED`, so the EXTERNAL -> Vulkan -> EXTERNAL ownership transfer the frame target requires does not
happen. `FrameTarget.COLOR_RENDER_LAYOUT` is `GENERAL` while `beginFrame` tracks `COLOR_ATTACHMENT_OPTIMAL`, so the
contract and the tracked value disagree.
**Partially fixed:** the submission now waits `[acquire, bridgeWait]` and signals `[renderFinished, bridgeDone]`, and the
verdict flags are reset at entry (measured `withWait=2`, `validationClean True`). **Open:** the ownership transfer, the
`GENERAL` layout, and the present's own failure handling.

### S02 [P1] a timed-out wait still lets the command buffer be reused
`waitForIdle` logs `VK_TIMEOUT` and returns; `beginCommands` then proceeds, clears `submissionPending` and resets the
command buffer although the GPU may still be reading it. `settleGpu` is described as bounded but `vkDeviceWaitIdle` has no
timeout, and the context drains queues with `vkQueueWaitIdle` and `Long.MAX_VALUE` fences. The submit-failure injection
does not exercise this path at all. **Open.**

### S03 [P1, fixed during the audit] the original GL draw was never cancelled
The injector lacked `cancellable = true`, so `info.cancel()` threw and the broad catch hid it: the vanilla body ran for
every accepted batch. Fixed in `f6ac63536` (`cancellable`, `reset`, independent counters); a regression introduced while
doing so deleted the `submitImmediateBatch` call itself and was restored by `dee914e05`.
**Open:** per-batch correspondence between acceptance and the absence of a GL draw, on both GPUs.

### S04 [P1, fixed during the audit] the group state was zeros
`currentGroupState` was never assigned, so blend, depth, culling and the alpha reference were all off. Fixed by
`580b897b0` (`state.clone()`). **Open:** verifying each state input against the snapshot and the pipeline.

### S05 [P1, certain in code] mesh merging does not add a base vertex
`VulkanDynamicMesh` appends vertices but never offsets the appended indices by the previous vertex count, and draws with
`vertexOffset = 0`, so a second quad in the same group references the first quad's vertices.

### S06 [P1, certain in code] growing a dynamic buffer loses its contents
`VulkanHostBuffer.ensureCapacity` deletes and recreates the buffer without preserving data, while the mesh keeps appending.
Crossing 64 KiB of vertices or 32 KiB of indices drops everything written before.

### S07 [P1, certain in code] the state mirror and pipelines do not carry the state that matters
No hooks for alpha enable/disable, cull enable/disable, `depthFunc`, integer `blendFunc`/`tryBlendFuncSeparate`,
`activeTexture`, float `translate`; `setAlphaFunc`'s hook has no backend override; the pipeline hardcodes alpha blend
factors, `LEQUAL` and `ADD`, and substitutes standard factors for unknown ones; the shader's `ref > 0 && alpha < ref` does
not reproduce `GL_GREATER` or a zero reference; texture binding is a single value, so the lightmap unit switch is lost;
ITEM normal/fixed lighting, immediate fog, texture matrix, colour mask and scissor are not gated either.

### S08 [P1, GLSL against checked-in SPIR-V] ITEM's UV and colour inputs are swapped
`VulkanImmediateItemPipeline` binds location 1 to colour at offset 12 and location 2 to UV at offset 16, while the shader it
loads declares location 1 as UV and location 2 as colour - confirmed against the SPIR-V's `OpName`/`Location`.
**Fixed:** the pipeline now binds location 1 as `R32G32_SFLOAT` at offset 12 and location 2 as `R8G8B8A8_UNORM` at offset
20, matching both `DefaultVertexFormats.POSITION_TEX_COLOR` (POSITION_3F, TEX_2F, COLOR_4UB) and the shader. **Open:** the
minimum fixture the acceptance condition asks for - an asymmetric checker texture with non-white colour and UVs outside
0/1, on more than one face.

### S09 [P1, Minecraft source against the shader] the lightmap coordinate is decoded wrongly
Both fragment shaders compute `(coord >> 8 + 0.5) / 256`. `BufferBuilder` writes the coordinate short directly and
`EntityRenderer` installs a texture matrix with scale `1/256` and translate `8`, so vanilla is `(coord + 8) / 256` - the
`>> 8` shifts a 0..240 value to zero every time, sampling the same dark texel. A still-integer varying also loses
per-vertex interpolation.
**Fixed:** both vertex shaders now emit `v_Lightmap = (vec2(a_LightCoord) + 8.0) / 256.0` as an **interpolated** `vec2`,
and both fragment shaders sample the lightmap with it - the raw coordinate, the vanilla scale and bias, and per-vertex
interpolation. The four SPIR-V files were recompiled and the copies inside the built jar were compared by hash with them
(identical), so this is a change that demonstrably reached the run.
**Measured, and the residual did not move:** the weather and particle scene still differs by **0.40% of pixels over 8
(3,714, was 3,715)** against the same **0.23%** floor. The decode was wrong in the source and is now right, and it is not
what the remaining difference is made of. **Not yet done:** the acceptance fixture - dark/fullbright/sky-only/block-only
with differing per-vertex light values compared against GL.

### S10 [P1] draw order, FBO and viewport are not preserved across the hybrid
The group key has no FBO, viewport or scissor; small accepted batches wait for a threshold while the next unsupported draw
runs in GL immediately; the flush queries the current FBO after the fact; a later terrain pass can draw the remainder.

### S11 [P1] "16 windows per frame" cannot be shown to finish within a frame
`updateTimings` resets the per-frame counter, but `beginCommands` calls it on every fence wait, so multiple flushes reset
the same frame's budget. No unique frame ID, no frame-end reconciliation of accepted/drawn/held/discarded.

### S12 [P1] texture invalidation and resize destroy resources that may be in use
`invalidate` deletes immediately, `resize` discards the old view/image/memory before any wait, and the descriptor caches
keep sets built on stale views. No generation-carrying handles, no fence-based retirement.

### S13 [P1] the descriptor pool accumulates and the lightmap is not part of the key
`setsByTextureView` grows without bound behind `pool.maxSets = 64`, every pipeline also allocates an unused initial set,
and the block/particle keys use only the main texture view, so a rebuilt lightmap reuses a stale binding.

### S14 [P1] geometry that fails after acceptance is dropped and called a GL fallback
The draw path discards on missing pipelines, continues on missing textures and discards in `finally`, but the original draw
was already cancelled - so the comments promising a GL fallback are wrong and geometry disappears silently.

### S15 [P1] backend teardown leaks
`delete()` frees terrain resources but not the current immediate mesh, the group list, the immediate pipeline caches, the
UI pipeline and mesh, the texture registry, the white texture or the swapchain; the registry's create-then-fail-upload path
also leaks the GL texture.

### S16 [P1] animation invalidation never reaches the terrain atlas
`invalidateTexture` updates only the registry; the terrain atlas keeps its own copy and its 400-pass refresh, so five
sprite-hook firings are not evidence that terrain animation is current.

### S17 [P1] `POSITION_TEX_COLOR` is wrongly identified as the final composite
The backend branches to present for format 2 before any mask, without checking the FBO or the call's meaning. The captured
stack was `RenderGlobal` -> `Framebuffer.func_178038_a`, which the cached source shows blending an overlay into the
framebuffer with its third argument false - not a screen-only composite. GUI, outlines, offscreen passes and postprocessing
share the same layout, so cancelling the GL draw removes overlays and can produce several presents.

### S18 [P1] `OUT_OF_DATE` failures are not handled
The flags are not reset at the top of `presentImage`, so an early return leaves the previous verdict; a real
`VK_ERROR_OUT_OF_DATE_KHR` from the acquire returns before the recreate branch; a stale `true` can make an unsubmitted
present look successful; and after `swapchain = null` the next present can dereference a null image. The injection measured
a successful acquire, not the failure path, and re-injects on every recreate.

### S19 [P1] immediate push constants exceed the guaranteed minimum
The immediate pipelines require 132 bytes; Vulkan's guaranteed minimum is 128 (256 as of 1.4). Two desktops working does
not generalise. Reference: https://docs.vulkan.org/spec/latest/chapters/limits.html

### S20 [P1] the validation gate misses errors by their format
`prism_validation` skips any message not starting with `vk`, and stores no severity or message ID, so
`Validation Error: [ VUID-... ] ...` style output counts as zero messages.
**Fixed on both sides.** The debug callback now prints one structured line per message -
`severity=ERROR|WARNING|INFO|VERBOSE id=<messageIdName or number> <message with newlines folded>` - and the harness keys on
that: `INFO`/`VERBOSE` are skipped, a `WARNING` only when it says `deprecated`, and everything else counts, so an error
phrased in any style is caught. Unstructured text (older logs, driver strings) is treated the same way - only clearly
informational lines are skipped. A **missing log is now a failure** rather than zero messages. The parser was checked
against seven inputs, including the audit's two forms:

| Input | Messages counted |
|---|---|
| `severity=ERROR id=VUID-... ...` | 1 |
| `severity=WARNING ... deprecated ...` | 0 |
| `severity=INFO ...` | 0 |
| `vkDestroyBuffer(): can't be called on ...` | 1 |
| `Validation Error: [ VUID-vkCmdDrawIndexed-None-08600 ] ...` | **1** (was 0) |
| `VUID-vkCmdDrawIndexed-None-08600: ...` | **1** (was 0) |
| log file absent | **1** (was 0) |

**Open, and honestly unexplained:** a real run with the new callback wrote exactly one structured line -
`severity=WARNING id=VALIDATION-SETTINGS vkCreateInstance(): Information with the layer settings provided ...` - and the
harness reported `validationMessageCount 0` with `validationClean True`. By the rules above that line should count, so
either the harness reads a different file or a different parse path than the one edited, or the line is written after its
read. **Until that is resolved the gate is not claimed as fixed**, because the whole point of this item is that a clean
result must be trustworthy.

### S21 [P1] the image comparison is not max-channel
`compare_validation` converts the RGBA difference to luminance; the auditor's one-pixel cases (red 1 / tolerance 0, blue 40
/ tolerance 5, alpha 255 / tolerance 0) all read equal.
**Fixed:** the mask is now the maximum difference across channels, and those cases report unequal.

### S22 [P1] a failed process is hidden by an image match, and the interop fixture cannot finish
The comparator ignored `validation-process.json`; separately, `ValidationClient`'s capture gate (`worldTicks != 200`)
returns before the interop branch can reach its `worldTicks < 240` check, so the interop path cannot complete; and the
reload/resize hooks at tick 100 have no once-guard.
**Partially fixed:** the comparator now disqualifies a failing process result. The fixture defects are open.

### S23 [P2] samplers, mips and diagnostic resources
Samplers are created before the image and bake `maxLod` from an initial `mipLevels = 1`; the registry creates every texture
with linear filtering, ignoring the GL sampler state and the original mipmaps; the first size query uses the currently
bound texture rather than the given ID; the pixel hash is updated before the upload succeeds. The `FRESH_SEMAPHORES` pool
is deleted without ever being filled, so the earlier "tracked and destroyed" note was wrong.

### S24 [P2] the record and the acceptance criteria need reworking
Plan rows claim pixel-identical immediate rendering, removed deferral and verified cull/alpha capture, and the blockers
section still describes the old one-window finding; GUI-visible means the HUD is visible, not that GuiScreen/menu/tooltip
or mod UI is ported; the fixture removes entities and particles; `ScreenShotHelper` reads the game's FBO, not the presented
screen; `nativeTerrainVerified` is a counter condition. Shader sources and checked-in SPIR-V are generated by hand with no
sync check, and the launcher's lock should also record git HEAD/dirty state, fixture hash, effective config and driver.

## Order of work

1. Acceptance gates and real-path evidence (S03, S04, S20-S22): fixed jar and hash, effective flags, same scene, and the
   cancellation of the original GL draw verified per batch.
2. Minimum fixtures for S05-S09 and S14: single quad, two quads, large batches, ITEM inputs, alpha/blend/depth/lightmap
   correct, unsupported state refused explicitly.
3. Order, frame target, texture/descriptor lifetime and animation (S10-S16), verified on both GPUs with long runs,
   reload/rejoin and texture updates.
4. The present contract and failure handling (S01, S17, S18), with the real screen checked and distinguished from the
   probe.
5. Capability and sampling (S19, S23), then comparison against the original Celeritas GL and real mods. Claims of GL-free
   operation stay pending until their dedicated test passes.
