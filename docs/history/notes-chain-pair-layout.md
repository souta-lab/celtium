# Chain-pair layout: which side disagrees (VUID-vkCmdDraw-None-09600)

Round 249, bounded code read. No leg, no code edit. Image `0x370000000037` = the chain pair.

## 1. Transition sites

| site | layout set | note |
|---|---|---|
| `GllessFrameTarget.java:88` | `COLOR_RENDER_LAYOUT = VK_IMAGE_LAYOUT_GENERAL` | the constant every pair attachment declares |
| `GllessFrameTarget.java:855-856` | `colorImageLayout = GENERAL`, `depthImageLayout = GENERAL` | **frame image only** |
| `GllessFrameTarget.java:877` / `:887` | `newLayout(release ? GENERAL : COLOR_RENDER_LAYOUT)` | release/acquire barrier, frame image |
| `GllessFrameTarget.java:134` (comment) | "its only layout bookkeeping is the one-time UNDEFINED to GENERAL" | states that **the pair has no per-frame layout record** |
| `VulkanGlTexture.java:1552` | `currentLayout = SHADER_READ_ONLY_OPTIMAL` | set when the wrapper is used as a sampled image |
| `VulkanGlTexture.java:2531` | `currentLayout = SHADER_READ_ONLY_OPTIMAL` | the blit path's own barrier |
| `VulkanGlTexture.java:3127` | `currentLayout = TRANSFER_DST_OPTIMAL` | upload path |
| `VulkanGlTexture.java:2486` | `oldLayout(this.currentLayout)` | the wrapper's barriers are recorded **against its own tracked layout** |

## 2. Descriptor-write sites

- `VulkanTextureRegistry.publishExternalImage(int glTextureId, long image, long view, int w, int h)`
  (`VulkanTextureRegistry.java:237`) creates the sampled wrapper for a published image;
  `unpublishExternalImage` (`:307`) drops it. The wrapper's `currentLayout` starts `UNDEFINED`
  (`VulkanGlTexture.java:249`) and moves to `SHADER_READ_ONLY_OPTIMAL` at `:1552`/`:2531`.
- The descriptor write itself (`VkDescriptorImageInfo.imageLayout`) lives in the pipeline/backend files, which this
  audit did not open; the wrapper's tracked layout is the value any such write has available.

## 3. The single wrong site and its direction

**`GllessFrameTarget`'s pair attachment declares a constant `GENERAL` while the same image's wrapper flips its tracked
layout to `SHADER_READ_ONLY_OPTIMAL` whenever the image is sampled - and the pair has no bookkeeping to move it back
(`GllessFrameTarget.java:134` states this in words).**

That single asymmetry explains **both** directions of the VUID: the frame that samples the pair first leaves the
wrapper's `currentLayout` read-only, so the next pass that attaches it under a `GENERAL` initial layout is the
"GENERAL-declared vs read-only-actual" message; the frame that attaches it first leaves the real image in `GENERAL`
while the descriptor written for a sampled view recorded read-only, which is the opposite message. Same count before and
after round 248 because the change (`VulkanPostChain`'s sampled descriptors `GENERAL -> SHADER_READ_ONLY_OPTIMAL`,
`dd0b0389c`) touched the descriptor side only - the missing half is the pair's own layout record.

## 4. What the sources cannot decide, and the bounded probe

The sources cannot say **which** of the two sides ran first in a given frame, so they cannot say which of the two
messages a given submit produced. The probe that decides it, one line at each end:

1. at the pair's pass begin: the declared `initialLayout` plus `chainPairColorImage()` handle;
2. at the descriptor write for a published wrapper: the wrapper's `currentLayout` plus the `imageLayout` written into
   `VkDescriptorImageInfo`.

Values that distinguish: if the descriptor's recorded layout is `SHADER_READ_ONLY_OPTIMAL` while the pass declares
`GENERAL` for the same handle in the same frame, the pass's declaration is the wrong side (fix: give the pair the
`colorImageLayout`/`noteColorImageLayout` bookkeeping the frame image already has at `:855-877`, and declare the
tracked value); if both agree yet the VUID persists, the transition that moves the image is missing entirely and the
record is stale on the wrapper side.
