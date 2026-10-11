# BH-10 and BH-11: the fix sites, and the GL↔Vulkan depth-bias convention

Date: 2026-10-11. Owner: gui-stage. BH-09 is landed (the stub records `glScissor`, the backend intersects the group's
scissor with the game's box, `immediateScissoredGroups` counts it). This file is what BH-10 and BH-11 need before
anyone writes them, including the one convention the brief asked to have written down.

## BH-10 — colour mask

Five edits, in this order:

1. **`VulkanGlState.java`** — four booleans (`colorMaskRed`/`Green`/`Blue`/`Alpha`, default all true) plus
   `colorMask(r,g,b,a)` and a getter. Follow the blend/alpha pattern already in the file.
2. **`GlStateManagerMixin.java`** — a hook on the colour-mask method. **Look the SRG name up
   (`scripts/mc-sources.sh --srg colorMask`) rather than guessing it**: the BH-09 lesson was that guessing the hook
   site is how a fix ends up on a method that never fires, and `glColorMask` has both a `GlStateManager` wrapper and a
   direct `GL11` entry point, so *which* one the game uses decides where the hook belongs. `VintageDrawContext` uses
   `GL11` directly for the scissor; check whether anything does the same for the colour mask before choosing.
3. **`VulkanTerrainGpuBackend.java`** — a `setColorMask(...)` entry point, and one more slot in
   `celtium$captureState()`'s returned array. **The state key needs no separate change**: `celtium$stateKey` folds
   every element of the array (`for (int value : state) key = key * 31 + value;`), so a mask that is *in the array*
   rides the key by construction — which is the requirement, because otherwise the depth-only Fancy-cloud pass and the
   visible pass after it share one pipeline and the cloud is drawn twice.
4. **`VulkanImmediate*Pipeline`** — `VkPipelineColorBlendAttachmentState.colorWriteMask(...)` from the mask instead of
   the current unconditional `VK_COLOR_COMPONENT_R_BIT | G | B | A`. There are several pipeline classes
   (`VulkanImmediatePipeline`, `VulkanImmediateTexturePipeline`, `VulkanImmediateItemPipeline`,
   `VulkanImmediateParticlePipeline`); the cloud passes are `POSITION_TEX`/`POSITION`, so the texture pipeline is the
   one the acceptance leg exercises, but all of them should read the mask or the next format hides the same bug.
5. **Leg**: `-Dceltium.validation.renderClouds=2` (Fancy). Observable without a picture: the first cloud pass must
   leave colour unchanged while still writing depth. **Prefer a counter or the calibrated leg for any picture claim** -
   the uncalibrated scene has a 23.5 % boot-to-boot pixel floor, which is larger than most of what a state fix moves.

## BH-11 — polygon offset, and the convention

**The convention, written down:**

- **GL**: `glPolygonOffset(factor, units)` offsets the fragment's depth by `factor * m + units * r`, where `m` is the
  polygon's maximum depth slope and `r` is the smallest resolvable depth difference at that fragment. Applied in
  window depth space, where **smaller is nearer**.
- **Vulkan**: `depthBiasConstantFactor` (call it `c`) and `depthBiasSlopeFactor` (call it `s`) offset the fragment by
  `c * r + s * m`. **The roles are the same and the names are swapped**:
  - GL's `units` ↔ Vulkan's **`depthBiasConstantFactor`**;
  - GL's `factor` ↔ Vulkan's **`depthBiasSlopeFactor`**.
- **Sign**: both conventions add the offset to a depth where smaller is nearer, so vanilla's `glPolygonOffset(-3, -3)`
  (block damage, `RenderGlobal`) maps to `depthBiasConstantFactor = -3`, `depthBiasSlopeFactor = -3` **with the sign
  unchanged** - the negative offset pulls the coplanar overlay toward the viewer in both APIs. This is the claim to
  *verify with the leg*, not to assume: if the overlay disappears instead of appearing, the sign is the first thing to
  flip, and the record should say which it was.
- **Slope basis**: GL's `m` is the polygon's window-space depth slope; Vulkan's slope term uses the fragment's depth
  gradient. They are the same quantity, so the numeric factor carries across - this is the part that could still
  differ by a constant on a driver, and the acceptance leg (a block-breaking overlay at near and far distance, on a
  sloped face) is what would show it.

**The design choice worth making deliberately: use `VK_DYNAMIC_STATE_DEPTH_BIAS` + `vkCmdSetDepthBias`, not a pipeline
per bias pair.** The bias is *command* state in GL, exactly like the scissor that BH-09 just landed as command state.
Baking it into the pipeline would create a pipeline for every `(factor, units)` pair the game sets - and vanilla sets
several - for a value that can be changed between draws for free. `depthBiasEnable` must still be a pipeline field
(the rasterization state), so the enable bit *does* belong in the state key; the two numbers do not.

If that choice is taken, the fix sites are: the mirror and the key slot (1 and 3 above, unchanged), the pipeline's
`rasterizationState.depthBiasEnable(...)` plus `VK_DYNAMIC_STATE_DEPTH_BIAS` in the dynamic-state list, and a
`vkCmdSetDepthBias(commandBuffer, constantFactor, 0.0f, slopeFactor)` at the draw site. **No shader change is needed**,
so `celtium-vulkan/src/main/shaders/**` stays weather-water's and this package never has to touch it.
