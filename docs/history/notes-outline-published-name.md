# Round 252 - which game draw samples the published chain-pair name

Source reading only; no leg, no edits. Paths: `.celtium-work/mc-sources/...` (vanilla).

## 1. The draw, in the sources

- `net/minecraft/client/renderer/EntityRenderer.java:1126-1129`: inside `if (OpenGlHelper.shadersSupported)` the
  renderer calls `this.mc.renderGlobal.renderEntityOutlineFramebuffer();`.
- `net/minecraft/client/renderer/RenderGlobal.java:250-259`:
  `if (this.isRenderEntityOutlines()) { GlStateManager.enableBlend(); tryBlendFuncSeparate(SRC_ALPHA,
  ONE_MINUS_SRC_ALPHA, ZERO, ONE); this.entityOutlineFramebuffer.framebufferRenderExt(this.mc.displayWidth,
  this.mc.displayHeight, false); GlStateManager.disableBlend(); }`.
- `entityOutlineFramebuffer` is the entity-outline shader's `"final"` framebuffer
  (`RenderGlobal.java:229`, cleared at `:679`).
- `Framebuffer.framebufferRenderExt` (`net/minecraft/client/shader/Framebuffer.java:232`) draws **one full-screen
  quad** that samples that framebuffer's colour texture (the `bindTexture` of `framebufferTexture` inside the method,
  after the `ortho(0, width, height, 0, 1000, 3000)` / `translate(0,0,-2000)` setup at `:236-248`).

**Which game feature it serves**: the entity outline / glow - the composite of the silhouette framebuffer that
`RenderGlobal` fills when entity outlines are on. It is the draw that samples the chain pair's colour image, because the
chain pair *is* the adopted silhouette image and `celtium$publishChainOutput`
(`VulkanTerrainGpuBackend.java:11182`) publishes that image under this framebuffer's colour-texture name.

**Order**: it runs inside `EntityRenderer.updateCameraAndRender`, i.e. **before** the HUD bracket's end and before
`Minecraft.runGameLoop`'s `this.framebuffer.framebufferRender(...)` (`Minecraft.java:1237`). So it is *not* the
finished-frame composite.

## 2. What the drop removes

- Dropping a group that samples the published name **loses the glow**. The frame image holds the world, but it does
  not hold the outline: the outline exists only in the pair, and this composite is the only draw that carries it to the
  screen. The finished-frame composite (`Minecraft.java:1237`) samples the *game* framebuffer's colour texture, a
  different name, and is unaffected.
- So the drop is not "a redundant composite": in a glow leg it removes the white outline entirely, and the acceptance
  for the glow row (outline visible at the reference's position) would fail with the drop on.

## 3. The correct treatment instead of dropping

- Serve the sampling transition - which `celtium$transitionPublishedFrameTextures(false)` already does at both sampling
  sites - and keep the composite **out of the pass that attaches the pair**: the composite's own target must be the
  image the pass attaches only when that is a genuine self-read, and for this composite the correct attachment is the
  frame image (identity 0 / `windowIdentity` path at `VulkanTerrainGpuBackend.java:7789`), not the pair.
- If a frame really records the composite inside the pass that attaches the pair, the fix is to order the pair's
  sampling transition before that pass and route the composite to the frame image, not to skip the group. The site to
  change is the skip predicate `celtium$samplesAttachedImage` and its call site in the offer path - the predicate is
  what turns an ordering bug into a missing feature.
- The alternative named in the question - copy the pair once into a non-attached image - is correct but costs a
  full-image copy per frame; the transition-plus-routing fix costs nothing.

## 4. The counter that would show the loss

- `publishedFrameTexturesSkippedForAttach` (raised by `noteSkippedForAttach`) is the direct one: a non-zero value in a
  glow leg means this composite was dropped.
- Beside it: `chainAdoptions` and `offTargetDrawn`/`offTargetCopiedBack` (the pair was adopted and drawn), and the
  picture itself - the glow row's white outline region. If `chainAdoptions > 0` and
  `publishedFrameTexturesSkippedForAttach > 0` in the same leg, the skip is what removed the glow.
