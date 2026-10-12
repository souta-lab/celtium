# Round 250 - where the pair's render pass is built, and what a recorded-layout declaration would take

## 1. Pass-build sites and the exact layout values

- The pass is built once by `VulkanTerrainPipeline.createFrameRenderPass(VulkanContext)`
  (`celtium-vulkan/.../VulkanTerrainPipeline.java:994`), called from `VulkanTerrainGpuBackend.uiRenderPass()`
  (`:126-128`).
- Colour attachment: `.initialLayout(COLOR_ATTACHMENT_LAYOUT)` and `.finalLayout(COLOR_ATTACHMENT_LAYOUT)`
  (`VulkanTerrainPipeline.java:1011-1012`); depth: `DEPTH_ATTACHMENT_LAYOUT` (`:1024-1025`); the subpass reference
  uses the same constants: `.layout(COLOR_ATTACHMENT_LAYOUT)` (`:1028`).
- `COLOR_ATTACHMENT_LAYOUT` is the pass's own constant (GENERAL, matching
  `GllessFrameTarget.COLOR_RENDER_LAYOUT = VK_IMAGE_LAYOUT_GENERAL`).
- The pair's framebuffer is built against **whatever pass the caller passes**:
  `ensurePairFramebuffer(long renderPass, long colorView, long depthView, String what)`
  (`VulkanTerrainGpuBackend.java:10033-10040`), and the callers pass `terrain.renderPass()` when a terrain pass exists
  or `uiRenderPass()` otherwise (`:7142`, `:7711`, `:8020`).

## 2. Is that pass shared? - yes, and that decides the shape of the fix

- `uiRenderPass()` is used at `:4852`, `:7142`, `:7711` and `:8020`, i.e. for every immediate pass with no terrain
  pass (menu, splash, off-target). In world frames the pair's framebuffer is built against the **terrain** pass's render
  pass instead - so the same pass object serves the frame image, the pairs and the terrain pipelines.
- A per-layout variant is therefore only safe if it is **keyed**: one pass handle per distinct attachment layout, with
  the framebuffer cache keyed on the same value. It is *not* safe to mutate the single pass.

## 3. Minimal change that makes the declaration follow the pair's recorded layout

- Give `createFrameRenderPass` an attachment-layout parameter and cache one pass per layout (today's single
  `uiRenderPass` field becomes a small map keyed by `(colorLayout, depthLayout)`); the pair's framebuffer is then built
  against the variant for `Pair.colorImageLayout`, and the cache is invalidated when that value changes (the pair's
  `recordAcquire`/`ensure`/`release` and the published wrapper's barrier already move it, and `CELTIUM_PAIR_LAYOUT`
  prints it on change).
- **The pipelines do not have to be rebuilt**: Vulkan's render-pass compatibility rule covers attachment formats,
  samples and the subpass structure - `initialLayout` is not part of it - so a variant pass is compatible with the
  pipelines already created against the base pass. That is what makes this cheaper than a pass-per-pipeline design.
- The frame image does not need the variant: its layout is *moved to the pass's constant* by its own acquire/release
  barriers (`GllessFrameTarget.colorImageLayout` at `:178`, `noteColorImageLayout` `:554-555`, `:620`, and the present
  path's `presentSourceLayout` at `VulkanTerrainGpuBackend.java:11734/:11792`), so pass and image agree. The pair's
  layout is moved by a **different owner** - the published wrapper's barrier - which is exactly why the pair and the
  constant disagree and the frame image does not.

## 4. What would break if the pass's initialLayout became SHADER_READ_ONLY unconditionally

- The clear-first contract: `celtium$pairClearNeeded` / `RenderTargetRegistry.notePairCleared` assume the pair is
  cleared before it is attached each frame, and `vkCmdClearColorImage` leaves the image in the layout it was given
  (GENERAL). A pass declaring `SHADER_READ_ONLY_OPTIMAL` would then attach an image the clear left in GENERAL - the
  reverse VUID, unchanged in kind.
- The published wrapper's `ensureGeneralLayout`/`ensureSampledLayout` would have to agree with the pass on every frame,
  so the layout the pass declares must be the layout the *last writer* left, not a constant either way.
- Conclusion: the fix is not "declare SHADER_READ_ONLY instead of GENERAL" but "declare the pair's **recorded** layout",
  with the pass variant keyed on it - and if that cannot be kept in step, the alternative is to make the published
  wrapper's transition happen *before* the pass that attaches the pair (so the recorded layout is always the constant),
  which is a barrier-ordering fix in the same place the wrapper is moved today.
