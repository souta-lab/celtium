# Texture mirrors: per-texture sampler reach, and the game's own mip levels

Owner: **texture-mirror** (task-26). Jars quoted by hash; pictures under
`evidence/texture-mirror-20261011/`. Read `2026-10-11-texture-mirror-acceptance.md` first.

## What was wrong

One global sampler clamp, `SAMPLER_MAX_LOD = 1`, was passed to `vkCreateSampler` for every mirror. Minecraft's block
atlas is allocated with `GL_TEXTURE_MAX_LEVEL`/`GL_TEXTURE_MAX_LOD` 4 (`TextureUtil:215/:217`, `TextureMap:225`) and
its levels 0..4 are the game's own per-sprite averages (`TextureAtlasSprite.generateMipmaps`, uploaded level by level
by `TextureUtil.uploadTextureMipmap`), while this class wrote level 0 and regenerated the chain above it with a
whole-image blit that averages across sprite borders. So the atlas was sampled one level deep and the levels it did
have above 0 were averages no producer wrote.

## The change

**Per-texture reach.** `VulkanGlTexture.samplerMaxLod` replaces the global constant: `SAMPLER_MAX_LOD_DEFAULT = 1`
stays for a mirror whose chain was generated here (the measured best for that content), and it is raised to the
deepest level the game actually wrote when the mirror is filled from the game's own per-level surfaces
(`setSamplerReach`, which recreates the sampler and bumps the mirror's generation so descriptor caches keyed on
`key()` cannot keep naming a destroyed sampler). `mirrorLevelSummary`'s `samplerReach` now reports the highest reach
any sampler in the process was created with, so the census and the sampler move together.

**The game's own levels.** `VulkanTextureRegistry.celtium$capturedLevels` walks the stub's per-level captures
(`textureUploadRgba8(name, level, extent)`), stopping at the first level with no capture, an extent that is not
`base >> level`, or no game pixels (`textureLevelHasGamePixels`, the previous task's fact). `VulkanGlTexture.
uploadCapturedLevels` hands those levels to the existing per-level patch writer, which already writes each level at
its own scaled extent with its own barriers (audit N07) - so the mirror holds the chain GL holds instead of a blit.
Control: `-Dceltium.vulkan.mirrorCapturedLevels=false` keeps the old path (level 0 + generated chain).

## Measurements (AMD RX 6400, scene=terrain, pose x0.5 y12.0 z0.5 yaw180 pitch20, tick 200)

Reference: upstream Celeritas `989ebf2e` (`d12425eaaf184e79`), same fixture/scene/pose/tick, GL screenshot
(`t26-ref`, PASSED, `validationClean=true`, `worldFrameNonBackgroundFraction=0.761173`). Candidate jar
`a6bd8d62818ad80b5cd567d0`.

| vs the reference | mean\|d\| | pixels >8 | of |
|---|---|---|---|
| **band rows 200-320, per-level content + reach 4** (`t26`) | **6.207** | **5.86%** | 153,600 |
| band, generated chain + reach 1 (control `t26-nomips`) | 26.728 | 50.00% | 153,600 |
| sky rows 0-205 (`t26`) | 0.442 | **0.00%** | 262,400 |
| rest rows 320-720 (`t26`) | 0.006 | 0.01% | 512,000 |
| terrain centre (`t26`) | 0.010 | 0.02% | 24,000 |

fog-path's numbers for comparison: control (mirror clamp 1) 29.187 / 65.82%, their terrain sampler 20.996 / 59.08%.
The general path is at 6.207 / 5.86% - below 20.996 - with the sky, the rest of the frame and the terrain centre
untouched.

Per-texture facts from the run (`t26`):

- `CELTIUM_MIRROR_SOURCE id=12 source=capture size=512x512 content=opaque=139685/262144 … levels=5 samplerMaxLod=4
  full=true` - the atlas mirror holds **5 levels, the game's own 0..4**, and its reach is 4.
- `CELTIUM_SAMPLER size=512x512 levels=10 … minLod=0.0 maxLod=4.0 bias=0.0 nearest=true` - what was handed to the
  driver (was 1.0).
- `mirrorLevelSummary`: `levels=[0:4 1:4 2:4 3:4 4:4] written=20 beyondProducerChain=0 samplerReach=4
  reachUnwritten=0 refusals=0` - no level the sampler can reach is unwritten.
- Every other texture keeps the default: e.g. the GUI sheets are `source=announcement` (level 0 only) and their
  samplers read `maxLod=1.0`.
- `validationClean=true`, `validationMessageCount=0`, verdict PASSED.

**The per-level half is evidenced on its own measurement**: with the generated chain the band is 26.728 / 50.00% and
the terrain centre 1.256 / 6.76%; with the game's own levels 6.207 / 5.86% and 0.010 / 0.02%. That also supersedes
the S23 note in `VulkanGlTexture` ("mirrored levels 5.60% where generated levels 0.16-0.28%"), which was measured
with the global clamp at 1.0 and against a bridge reference, not against upstream GL with the game's own reach.

**Confound, stated rather than hidden**: the control differs in *both* variables (generated chain *and* reach 1),
because the reach is only raised by the per-level path. Separating them needs a third leg - generated chain with the
reach forced to 4 - which was not taken.

## The equivalence control for fog-path's terrain sampler

`-Dceltium.vulkan.terrainAtlasMaxLod=1` makes the terrain pipeline use the mirror's sampler instead of its own
maxLod-4 one; with the general path in place both legs now read to level 4. Measured (`t26` default vs
`t26-control`), leg to leg:

- sky rows 0-205: **0 pixels differ** of 262,400; rest rows 320-720: **0 of 512,000**; terrain centre: **0 of 24,000**.
- band rows 200-320: **8,474 pixels differ (5.5%), all inside rows 225-256** - the rows fog-path identified as the
  ones the sampler and the build state move. Against the reference the two legs read 6.207 (default) and 6.007
  (control).

So the general path is **not pixel-identical** to the terrain sampler, and the residual is confined to exactly the
rows where the two samplers can differ; outside them the legs are bit-identical. Since both now read `maxLod=4.0`
(`CELTIUM_SAMPLER … maxLod=4.0` in both legs), the remaining difference is not the reach but either the samplers'
other parameters (fog-path's clamps to edge and is created by the terrain pipeline; the mirror's carries the GL
texture's own wrap, which under the stub reads as clamp because the stub records no `glTexParameteri`) or the
run-to-run build state in those rows, which fog-path measured as build-state-sensitive. **The terrain sampler should
stay until that is separated**; deleting it on this evidence would trade a measured 0.2 residual for an unmeasured
one. The measurement that would settle it: the same pair with the far ring's build state pinned (or two default legs
compared to each other for the build-state floor of those rows).

## Not done

- The third leg above (generated chain with reach 4), so the content/reach split is not separated.
- The per-level path is exercised for the atlas only; a texture whose per-level captures stop early keeps the
  generated chain below the captured depth and the default reach.
- `GL_TEXTURE_MAX_LEVEL` is not read under the stub (the stub records no texture parameters), so the reach there is
  the deepest captured level; under a GL context the parameter could act as the ceiling and is not consulted yet.
