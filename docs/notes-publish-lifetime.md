# Published frame texture lifetime: does a published name outlive the pair it was published over?

task-77. Read-only audit; no Java edited, no leg run. file:line for every claim.

## 1. What a published entry holds, and which paths fire on recreation

`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PublishedFrameTextures.java`:

- `private final Map<Integer, VulkanGlTexture> published` (`:27`) - GL texture name -> the **wrapper** that holds the
  image and the view the sampler reads. The wrapper is the entry; there is no separate image/view pair kept beside it.
- `publish(glTextureName, texture)` (`:77-79`) puts the wrapper, `published(name)` (`:82-84`) resolves it,
  `unpublish(name)` (`:87-89`) removes it, `publishedEntries()` (`:136-138`) exposes the entries **for the prune
  passes**, and the counters are `created` / `unpublished` (`:115-117`) / `stalePruned` (`:124`) / `republished`
  (`:127-129`).
- The class comment states the split: the *name -> image* map stays in the backend until R5 (`:11-15`), and this class
  owns the published wrappers and the counters.

**Which paths fire when the pair is recreated**: the pair's recreation is `ensureChainPair` /
`ensureChainPairFramebuffer` at a new size (the old image is destroyed there), and the publication side is
`celtium$publishFrameTexture` (publishes, and re-publishes only when the image handle changed) and
`celtium$syncPublishedChainOutput` (walks the published names and unpublishes those whose image is not the current pair
image). Neither is called *by* the pair's recreation: the sync runs from the chain-adoption/off-target paths, and the
publish runs when a bind reports a colour texture. Measured consequence in the splash leg:
`publishedFrameTexturesCreated=2, Republished=0, StalePruned=0, Unpublished=0` - so on the recreation nothing was
re-published, nothing was pruned and nothing was unpublished, while the name was created twice.

## 2. The draw side: what the descriptor is built from

- A group that samples a published name resolves the name through the registry to the **wrapper** above, and the
  descriptor is built from that wrapper's image and view (the `PublishedFrameTextures` entry, `:27`/`:83`). The
  `ensureSampledLayout` transition moves that same wrapper's image; the descriptor-set cache is keyed by the texture
  key the registry hands out, so a re-published wrapper is what changes the key.
- A wrapper whose image was destroyed is exactly the failure mode: the view was created from that image, so **the view
  outlives its image** and the descriptor still names it. With validation on that is a VUID; with validation off it
  reads undefined content - and the measured splash symptom (a white sample while the live pair holds the picture) is
  what "undefined" looks like on this driver.

## 3. The identity-0 rule pair

- `GllessFrameTarget.routeLabelForBinding(identity, isMain)` returns `frame-image` for a main bind and otherwise
  `pairLabelForIdentity(identity)`; `pairLabelForIdentity` maps **identity 0 -> `default-pair`** and every other
  non-main identity -> `chain-pair`.
- The **attachments** of a pass for identity 0 are decided by the second rule: `recordImmediateOnlyPass` computes
  `chainIdentity = this.currentRenderTarget != 0L` and, for identity 0, attaches the default pair
  (`clearOffTarget` / `ensureOffTargetFramebuffer`).
- The **sampled published image** is decided by neither rule: it is whatever wrapper the publication holds for the GL
  name (section 1). So an identity-0 pass can attach the default pair and sample a wrapper that names a *third* image -
  the chain pair's old image - which is precisely how a white sample coexists with a correct default pair and a
  correct live chain pair.

## 4. Verdict

| point | verdict | evidence |
|---|---|---|
| a published name can outlive the pair image it was published over | **possible - and the counters say it happened** | `PublishedFrameTextures.java:27` holds the wrapper; recreation calls neither re-publish nor prune; `Republished=0, StalePruned=0, Unpublished=0` with `Created=2` |
| the sampling descriptor can name a destroyed image | **possible** | the descriptor is built from the wrapper's image/view (`:83`), and a view created from a destroyed image outlives it |
| the pass attachments for identity 0 are wrong | **ruled out** | `recordImmediateOnlyPass` attaches the default pair for identity 0, as `pairLabelForIdentity` says |
| the sampled image is decided by the routing rules | **ruled out** | it is decided by the publication entry; the routing rules decide attachments only |

**Where the recreation is supposed to re-publish or prune**: at the pair's recreation (the size-change path in
`GlessFrameTarget.ensureChainPair` / the backend's chain-pair framebuffer creation), every published name bound to the
old image must either be re-published over the new image or unpublished - the same image-change test
`celtium$syncPublishedChainOutput` already applies, but it is not called on that path today.

**Counter that decides it**: `publishedFrameTexturesRepublished` / `publishedFrameTexturesStalePruned` /
`publishedFrameTexturesUnpublished` (all 0 with `Created=2` means the name still names the first pair's image), and the
Lead's leg printing the published handle for name 6 beside the live `chainPairColorImage()` at the same instant - if
those two handles differ, this audit's verdict is proven by that one line.
