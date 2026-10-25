# The splash and the present path: can the presented image be white while the frame image is not?

task-75. Read-only audit; no Java edited, no leg run. Every claim carries file:line.

## 1. The path a finished frame takes to the presented image

- The present blits **one image and only one**: `VulkanTerrainGpuBackend` calls
  `this.swapchain.presentImage(this.frameTarget.colorImage(), sourceLayout, …)` (`VulkanTerrainGpuBackend.java:5088`).
  `VulkanSwapchain.presentImage(long sourceImage, …)` (`VulkanSwapchain.java:711`, `:724`, `:737`, `:799`) never
  chooses a source of its own; the caller's image is the whole input.
- The presented-image dump is a readback of the **swapchain image**, taken inside the present path at the swap point,
  after the blit has been recorded into the same submission: `requestPresentedImageDump` (`VulkanSwapchain.java:168`)
  queues a path, and `writePresentedImage` (`:177`) -> `readBackBlock` (`:227`) reads the image the present just
  handed over. The fixture's `celtium$presentedImageMean()` reads the same value (`VulkanTerrainGpuBackend.java`, the
  `celtium$presentedImageMean` accessor).
- Points where a stale or unwritten image could be read instead of the finished frame, and their state:
  - **before the blit/composite** - not possible in this path: the readback is inside the present call, after the
    blit is recorded (`VulkanSwapchain.java:177-190`, called from the present path that also records the blit).
  - **a swapchain image not yet written** - not possible: the image read is the one the same present acquired and
    blitted into; the dump path is only reached from the present that wrote it.
  - **an image that is not the frame image** - this is the real one, and it is decided by the blit's source:
    `frameTarget.colorImage()` (`VulkanTerrainGpuBackend.java:5088`). Whatever the splash draws into any *other*
    image cannot reach the presented picture, however correct that image is.

## 2. The splash's two writes and where they land

- `Minecraft.drawSplashScreen` (`mc-sources net/minecraft/client/Minecraft.java:959-1011`) renders into its own
  `new Framebuffer(w, h, true)` (`:961-963`), draws the whole screen as one quad sampling tex (0,0) of the logo
  texture and then a centred 256x256 logo quad, and finally calls `framebufferRender(...)` (`:1004`).
- The logo writes are issued while the game's own FBO is bound, so their identity is non-zero and non-main. The
  routing rule sends every such identity to the **chain pair**
  (`GllessFrameTarget.pairLabelForIdentity`: identity 0 -> `default-pair`, every other non-main identity ->
  `chain-pair`; applied in `recordImmediateOnlyPass`, the `chainIdentity = this.currentRenderTarget != 0L` branch).
- `framebufferRender` draws with the **window/default framebuffer** bound (`Framebuffer.unbindFramebuffer`), i.e.
  identity 0 -> the **default pair**.
- The published-frame-texture mechanism publishes the *pair's* image under the FBO's colour texture name so that the
  composite's batch has something to sample (`PublishedFrameTextures`), but that only changes what the composite
  **reads**; the composite's own draw still lands in the default pair.
- Neither write therefore reaches the frame image: the frame image is written only by `isMain` draws (the game's own
  main framebuffer). A splash frame in which the frame image is untouched is white/cleared by construction.

## 3. Identity of the splash FBO versus identity 0

- The census calls the splash FBO's route `chain-pair`, i.e. its identity is non-zero and non-main. Identity 0 (the
  window) routes to `default-pair` (`GllessFrameTarget.pairLabelForIdentity`). The frame image is **neither** - it is
  the `isMain` target.
- So yes: the image the present reads (the frame image, `:5088`) is an image the splash did not draw into. The white
  presented frame is what the present path must serve while the splash's content lives in the pairs.

## 4. Verdicts

| point | verdict | deciding evidence |
|---|---|---|
| the present can serve white while the frame image holds the logo | **ruled out** | the blit source is only `frameTarget.colorImage()` (`:5088`); the logo is in the chain pair (non-zero identity) and the composite in the default pair (identity 0), neither of which the present reads |
| the present serves white because the frame image is untouched | **possible - and the code says this is the expected state** | same two citations: nothing in the splash writes the frame image, so the presented image is the frame image's cleared content |
| the readback reads a stale/unwritten image rather than the finished frame | **ruled out** | the dump is taken inside the present call after the blit (`VulkanSwapchain.java:168-190`, `:227`) |
| the identity difference (chain-pair vs identity 0) is what makes the present read an image the splash did not draw into | **possible, and named** | `GllessFrameTarget.pairLabelForIdentity` + the `chainIdentity` branch in `recordImmediateOnlyPass` |

**Counters/dumps that decide it without a new leg**: `CELTIUM_WINDOW_DRAW identity=0 image=frame` (which image the
window's draws land in), `offTargetPairClears=[default-pair=n, chain-pair=n]` (that both pairs are in use in the
splash phase), and the splash probe readback the Lead added at `VulkanTerrainGpuBackend.java:6941-6978`
(`celtium$readImageRectangle(this.frameTarget.colorImage(), …)`) - that one reads the frame image directly at the
first presents, which is exactly the measurement that separates "the frame image is right and the present is white"
from "the frame image is white too".
