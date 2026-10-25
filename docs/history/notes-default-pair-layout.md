# The default pair's UNDEFINED layout (round 255, source read)

No leg, no code edit. Image `0x3e000000003e` = the **default pair**. Message: a draw's descriptor names its view while
the image is `VK_IMAGE_LAYOUT_UNDEFINED`; the pair's own counters say it is never cleared, self-tested or acquired
(`offTargetPairs=[default(size=1280x720 creations=1 clears=0 selfTests=0 acquired=false) chain(... clears=15
selfTests=1 acquired=true)]`).

## 1. Every site that can put a pair view into a descriptor

| site | what it publishes | runs in a world leg? |
|---|---|---|
| `VulkanTerrainGpuBackend.java:11324` `celtium$publishFrameTexture(glTextureName, image, view, w, h)` -> `publishExternalImage` at `:11336` | the **frame image** (the frame target's colour image) | yes - the log's `CELTIUM_COPY_READ image=0x370000000037 … passAttached=0x210000000021 chai…` shows the copy-read resolution serving the **chain** pair, and the frame image is what the frame-target path publishes |
| `VulkanTerrainGpuBackend.java:11293` `celtium$publishChainOutput` -> `publishExternalImage` | the **chain pair** | yes (that is the published name the game's composite samples) |
| `VulkanTerrainGpuBackend.java:11702` (republish from the pruned entry) | re-publishes a name whose wrapper was dropped | yes, but only for a name already published |
| `TextureCopyService.java:816-866` (`glless.ensureOffTarget(...)` -> `offTargetColorImage()`) | **resolves** the read source; it does not publish | yes - and it is the path that makes the default pair exist |

**The default pair's view is not published by any of the three publish sites by name.** What the log shows is that the
copy read resolves to the **chain** pair (`image=0x370000000037`), so the default pair is created as a *read source
candidate* and, per its own counters, is never written (`clears=0`, `acquired=false`) - yet a descriptor naming its view
is accessed. That leaves one mechanism: the wrapper created for the *frame image* path
(`celtium$publishFrameTexture`) can be handed the default pair's image/view when the frame target's colour image **is**
the default pair in that configuration - the same object under two names, which is why the VUID names `0x3e…` while the
counters show the pair as never acquired.

## 2. The correct fix

Not "transition it once at creation": the pair is never written, so a one-time transition would only move an empty
image into `GENERAL` and hide the real question (why a descriptor exists for an image nothing draws into). The fix is
on the **serving** side, at `TextureCopyService.java:816-866`: the read resolution must only return an image that the
copy path will access as a **transfer** source (`vkCmdCopyImageToBuffer` / `vkCmdBlitImage`), which needs no descriptor
and no `GENERAL` layout. If any consumer of that resolution instead builds a **sampled** descriptor (the frame-target
publish path at `:11336`, or a pipeline that samples the resolved image), that consumer must be changed to not publish
a pair that has never been written - or the pair must be cleared/acquired first so the published view is a real render
target.

## 3. The line that shows it is gone

`offTargetPairs=[default(… acquired=false)]` moving to `acquired=true` (or the default pair no longer appearing), plus
the leg's `validationMessageCount` for `VUID-vkCmdDraw-None-09600` on `0x3e000000003e` going to **0** while the chain
pair's count stays at whatever round 254 left it. The single print that would name the publisher without a leg change:
at `publishExternalImage` (`VulkanTextureRegistry.java:237`), the GL name, the `image` handle and the caller's route
label - if that line shows the default pair's handle under the frame-image name, the publisher is `:11336`.

## Not established

- Whether the frame target's colour image *is* the default pair's image in these legs (the identity/handle pair would
  settle it; `CELTIUM_COPY_READ`'s `passAttached` and the publish line's `image=` are the two fields to compare).
- Whether any pipeline samples the resolved image directly (the pipelines were not opened in this read).
