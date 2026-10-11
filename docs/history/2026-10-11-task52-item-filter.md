# task-52: the item icons' blur is the magnification filter, re-consulted too late

Owner: **texture-mirror**. One leg measured (`t52-items`, jar `c6e7a8058c3fd511c068c2f8`), no clean picture yet
(see the flag at the end).

## The mechanism, confirmed

`nearestFilterFor` consults the stub's parameter record only when it exists *at that moment*; a texture's parameters
are set **after** the upload that creates its mirror (`TextureUtil.uploadTextureImageAllocate`: image first, then
`setTextureClamped`/`setTextureBlurred`), so an atlas mirror created at announce time falls back to the write-shape
inference - which answers LINEAR for a whole-image upload, and the atlas is uploaded exactly that way. The one-shot
refresh that fixed this for the wrap (`confirmWrapFromRecord`) read only `GL_TEXTURE_WRAP_S/T`, so the filter stayed
LINEAR for the mirror's lifetime. A GUI item at scale 3-4 is magnified, so LINEAR magnification is what "the icons are
the right shape but low resolution" is.

**Fix**: `confirmSamplerStateFromRecord` (renamed from `confirmWrapFromRecord`) now reads `GL_TEXTURE_MAG_FILTER` and
`GL_TEXTURE_MIN_FILTER` from the record as well, keeps them apart (`nearestFilter` for magnification,
`nearestMinFilter` for minification, defaulting to the caller's decision so a never-recorded texture behaves as
before), rebuilds the sampler when any of the four differs, and bumps the generation so descriptor caches keyed on
`key()` cannot keep naming the destroyed sampler. The log line names which source won.

## Measured, before and after (the same leg)

```
CELTIUM_SAMPLER_FILTER glTexture=12 repeatU=true repeatV=true mag=NEAREST min=NEAREST mipmapMode=LINEAR maxLod=1
  (mag=record min=record; the sampler was built before the game set it)
  wasMag=LINEAR wasMin=LINEAR wasRepeatU=true wasRepeatV=true
```

So the block atlas (name 12, the GUI items' texture) was built with **LINEAR mag and LINEAR min** and rebuilt with
the record's **NEAREST / NEAREST**, `mipmapMode=LINEAR` unchanged. The sampler handed to the driver afterwards:
`size=512x512 levels=10 minFilter=0 magFilter=0 mipmapMode=1 minLod=0.0 maxLod=1.0 bias=0.0 nearest=true`
(`magFilter/minFilter=0` is `VK_FILTER_NEAREST`). The record's census in the same leg:
`recorded=210 textures=31 wrapRepeat=42 wrapClamp=0 calls=23068` - the game never sets CLAMP. The item batch is
`id4=952/952` accepted.

`maxLod=1` here, not 4: in this scene the atlas mirror was filled through a path that carried only level 0, so the
per-level content path did not raise the reach. The filter fix is independent of that and is what the blur needed.

## Flag: this leg is not clean

The leg reports `validationMessageCount=20`, `validationClean=false`, all
`VUID-vkCmdBeginRenderPass-initialLayout-00900` (attachment 0's render pass initial layout is GENERAL while the image
is in SHADER_READ_ONLY_OPTIMAL). Nothing in this change touches image layouts or render passes, and the earlier
task-26/task-39 legs of the same jar family were clean, so this is most likely another in-flight change in the same
tree - but it is stated, not assumed: **the sampler evidence above is valid** (it is the object's own log line), and
the **sharpness/edge comparison against the reference must be re-taken on a clean leg** before task-52's picture
acceptance is claimed. Crops from this leg are kept as soon as the leg is clean.
