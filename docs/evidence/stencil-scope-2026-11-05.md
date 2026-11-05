# Stencil: what exists and what an implementation needs (2026-11-05, static reading, no leg)

## Facts in the code

- Every GL stencil entry point (`glStencilFunc/Op/Mask` and their `Separate` forms) is in the stub's no-op set
  (`CeltiumGlStub` no-op table; `CeltiumGllessNativeTransformer` routes them there).
- A stencil-only clear (mask 0x400) is recognised and skipped without a flush
  (`VulkanTerrainGpuBackend.flushBeforeClear`, `clearOrderNoTargetMask`) - the measured scene's dominant clear is
  ModularUI's `Stencil#reset`, issued every frame.
- `VulkanDepthFormat` prefers `D32_SFLOAT` (no stencil) and falls back to the S8 formats only when it is missing; the
  terrain and post-chain render passes use `stencilLoadOp DONT_CARE`.
- Pipelines declare only `VIEWPORT`, `SCISSOR` and `DEPTH_BIAS` as dynamic state.

## Who is affected in the measured packs

E2E-E 1.87.3: only `bogosorter-1.6.4` and `bithop-2.0.1` reference `com/cleanroommc/modularui/` (class-constant scan of
the 428 staged jars); `Stencil` itself is used inside ModularUI's widgets (`apply(x, y, w, h, ctx)`: an `Area` stack of
rectangles; `apply(Runnable, …)`: arbitrary shapes). Without stencil, their clipped scroll areas draw unclipped.

## What an implementation needs

1. A depth format with stencil on the targets ModularUI draws into (main frame image and the GUI window pass), i.e.
   `D24_UNORM_S8_UINT` / `D32_SFLOAT_S8_UINT` there, with the clear and load ops covering the stencil aspect.
2. Stencil test/ops/masks/reference in the immediate state key (or as dynamic state:
   `STENCIL_COMPARE_MASK`, `STENCIL_WRITE_MASK`, `STENCIL_REFERENCE` dynamic, `stencilTestEnable` and ops in the key),
   fed by the stub's stencil calls instead of the no-op set.
3. `glClear(0x400)` recorded as a stencil-aspect clear in the queue, keeping the current no-flush ordering.

A cheaper partial path for ModularUI only: map `Stencil.apply(x, y, w, h, ctx)`'s rectangle stack onto the existing
scissor (intersected per push, restored on `remove`) with an optional mixin; shapes passed as `Runnable` stay
unclipped. Either needs a leg with a ModularUI screen open (BogoSorter's config) against the GL reference.
