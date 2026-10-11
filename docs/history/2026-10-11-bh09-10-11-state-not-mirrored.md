# BH-09 / BH-10 / BH-11: all three GL states are *never mirrored*, not "mirrored but unused"

Date: 2026-10-11. Owner: gui-stage. Package: GUI-stage GL state.

The brief asked for one of two answers per finding: mirrored-but-never-applied, or never-mirrored. Read from the code,
**all three are never mirrored.** The hooks do not exist, so there is nothing for a pipeline to apply.

## BH-09 — scissor

- `GlStateManagerMixin` has **no hook** for `glScissor` or `glEnable/glDisable(GL_SCISSOR_TEST)`: a grep for
  `Scissor|scissor` in `forge122/src/main/java/org/taumc/celeritas/mixin/core/GlStateManagerMixin.java` returns
  nothing.
- `VulkanGlState` has **no scissor field** at all.
- The only scissor in the backend is the render pass's own, set to the **full target**:
  `VulkanTerrainGpuBackend.java:7906-7909` — `scissor.offset().set(0, 0); scissor.extent().set(targetWidth,
  targetHeight); vkCmdSetScissor(...)`.
- The scissor box *is* read, but only for diagnostics: `:5865-5871` (`glGetIntegerv(GL_SCISSOR_BOX)` and
  `glIsEnabled(GL_SCISSOR_TEST)`) inside the GL-state snapshot.

**So `CELTIUM_GROUP_VIEWPORT` is not the scissor.** It mirrors `glViewport`, which is a different rectangle, and the
backend reuses the viewport rectangle as the Vulkan scissor — which is exactly why a UI clip cannot work: the game's
`glScissor` never reaches the backend at all. The fix is a `setScissor(x, y, w, h)` / `setScissorEnabled(boolean)`
mirror pair plus the existing hook style, and then the group's Vulkan scissor must be the **intersection** of the
target rectangle and the game's clip, not the viewport rectangle. `celtium$setGroupViewport` is where the group's
rectangle becomes the Vulkan viewport and scissor (`:5716`), so that is the one place the intersection belongs.

## BH-10 — colour mask

- No `glColorMask` hook in `GlStateManagerMixin`; **no colour-mask field** in `VulkanGlState`; no write mask in any
  `VulkanImmediate*Pipeline`.
- The blend attachment therefore writes RGBA unconditionally, which is the reported defect: vanilla's first Fancy-cloud
  pass sets `colorMask(false,false,false,false)` to build depth only, and the native path writes colour in it.
- Fix direction: `setColorMask(r,g,b,a)` on the mirror, into `celtium$captureState`'s state array and the state key,
  and out to `VkPipelineColorBlendAttachmentState.colorWriteMask` in the pipelines. It has to ride the **state key**,
  or a depth-only cloud pass and the visible pass after it would share one pipeline - which is the same class of bug as
  the 2-argument `blendFunc` that never wrote the mirror (task-27).

## BH-11 — polygon offset

- No `glPolygonOffset` hook in `GlStateManagerMixin`, and **no `depthBias` anywhere** in
  `VulkanImmediateTexturePipeline` (grep returns nothing) or in the other immediate pipelines.
- Fix direction: `setPolygonOffset(factor, units)` plus enable/disable on the mirror, carried in the state key, and
  mapped to `VkPipelineRasterizationStateCreateInfo.depthBiasConstantFactor` / `depthBiasSlopeFactor` with
  `depthBiasEnable`. **The GL and Vulkan rules differ in sign and in the slope term's basis**, so the mapping is the
  part to get right and to write down: GL's `factor` scales the polygon's depth slope and `units` is a
  minimum-resolvable-depth multiple, Vulkan's `depthBiasConstantFactor` is `units`-like and `depthBiasSlopeFactor` is
  `factor`-like, with the offset added with a sign that depends on the depth range convention. The shader part
  (`celtium-vulkan/src/main/shaders/**`) is only needed if the bias has to be applied in the fragment stage instead;
  **coordinate with weather-water through the Lead before touching the shaders**, per the brief.

## What is not done

Nothing is fixed in the tree yet. These are the three readings the brief asked for - **never mirrored, all three** -
with the file:line for each and the one place the scissor fix belongs. The verification legs are named in the brief
(a video-settings or inventory leg for the scissor, `-Dceltium.validation.renderClouds=2` for the colour mask) and none
has been taken.
