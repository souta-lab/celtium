# task-39: the confound, the rows 225-256 residual, the overlay poison, and the wrap record

Owner: **texture-mirror**. Extends `2026-10-11-texture-sampler-reach.md` and
`2026-10-11-block-overlay-id3.md`.

## 1. task-26's confound is closed

Three legs, same fixture/scene/pose/tick, against the same upstream reference (`t26-ref`, tick 200). The third leg
forces the reach independently of the content (`-Dceltium.vulkan.mirrorReachOverride=4`, which applies the reach on
the generated path too; jar `28dabb1dde958a7f5ca6bd28`).

| leg | band rows 200-320 | terrain centre | sky rows 0-205 |
|---|---|---|---|
| per-level content + reach 4 (`t26`) | **6.207** / 5.86% | **0.010** / 0.02% | 0.442 / 0.00% |
| generated chain + reach 1 (`t26-nomips`) | 26.728 / 50.00% | 1.256 / 6.76% | 0.442 / 0.00% |
| generated chain + reach 4 (`t39-gen-reach4`) | 21.309 / 50.00% | 1.256 / 6.76% | 0.442 / 0.00% |

**Both, and the content dominates.** The reach alone moves the band 26.728 -> 21.309 and does not move the >8
fraction at all (it changes the magnitude of the residual, not how many pixels differ); the game's own per-level
content takes the band to 6.207 and the terrain centre from 1.256 to 0.010. The terrain centre is content-only:
forcing the reach on a generated chain leaves it exactly at 1.256.

## 2. The rows 225-256 residual is the build state

Same pair (default vs `-Dceltium.vulkan.terrainAtlasMaxLod=1`) with fog-path's pinned build state
(`captureCondition=sections captureSections=41 captureMinWorldFrames=295`), plus a second default leg for the
build-state floor (jar `d0781f5da1cd082d974305ee`):

| leg to leg | band mean | band >8 | rows 225-256 | whole frame >8 |
|---|---|---|---|---|
| `t39-pin` (default) vs `t39-pin-clamp` (terrain sampler off) | 0.354 | 959 px | **0 px, mean 0.000** | 959 px |
| `t39-pin` vs `t39-pin2` (two default legs) | 21.380 | 95,328 px | **40,957 px, mean 33.250** | 104,585 px |

So with the build state pinned, rows 225-256 are **bit-identical between the two sampler configurations**, and the
sampler's own residual is a narrow strip at **rows 210-219 (959 px)**. The build-state floor in the same rows is
40,957 px between two runs that differ only in the far ring's build state (`t39-pin2`'s condition fired as
`sections-deadline (listed 35 of 41)`). **The 8,474 px I attributed to "the sampler or the build state" in the
task-26 record is the build state**; the terrain sampler's own contribution is the 959 px at rows 210-219, so it
still should not be deleted, but the residual it fixes is 9x smaller than the earlier number suggested.

## 3. The block overlay: the draw DOES sample the mirror for name 12, at the wrong region or level

Both poisoned legs ran (jar `d0781f5da1cd082d974305ee`): `t39-poison` (breakBlock +
`-Dceltium.vulkan.mirrorPoisonFlat=12=FFFF00FF`, `id3=4/4` accepted, `breakProgressCalls=5`) and its control
`t39-poison-control` (same flags plus `breakSubjectOnly=true`). The only difference between them is the registered
progress, and the atlas mirror's content is a flat magenta in both.

| region | overlay leg | control leg | difference |
|---|---|---|---|
| block (560,300)-(720,450) | (147.6, 116.4, 114.2) | **(235.8, 0.0, 235.8)** = the poison | mean 111.1, **24,000 of 24,000 px differ** |
| block surroundings (400,200)-(880,520) | (119.8, 92.1, 92.2) | (240.6, 25.3, 249.8) | mean 116.6, 137,589 of 153,600 |
| sky rows 0-205 | (166.7, 198.7, 255.0) | identical | **0 px** |

So replacing the atlas mirror's content changes the overlay's contribution from *nothing* (with the real atlas, the
crack is absent) to a large change (with every texel opaque, the fragments pass the alpha test and draw). **The draw
really samples the mirror's image for name 12** - the image, the view and the descriptor are the right ones - and
the fault is in **which region or level** it reads: with the real atlas the sampled texels' alpha is below 0.1 over
the whole quad, so the UVs land where the atlas is transparent (the destroy-stage sprite is opaque along its cracks)
or the sampler reads a level whose blurred alpha has fallen below the cutoff. The `texMat=none` result already
excludes a texture-matrix affine, and the geometry/colour/blend/alphaRef/depth are all the mirrored vanilla ones.

Next experiment (one pair, not taken yet): `-Dceltium.vulkan.mirrorEncode=12` with the overlay and its
`breakSubjectOnly` control. The ramp makes every texel alpha=255 *and* writes the sampled texel's (x,y) into R,G, so
the block's pixels read back the UVs the shader used - which separates "wrong region" from "wrong level" by
comparing the sampled atlas coordinate against the destroy-stage sprite's own atlas rectangle.

## 4. The wrap record, consumed

weather-water landed the stub's record (`recordedTextureParameter` / `textureParameter` /
`textureParameterRecorded`, and `glGetTextureParameteri` answers from it). Consumer half landed in my files:

- `nearestFilterFor` now prefers the recorded `GL_TEXTURE_MAG_FILTER` and keeps the write-shape inference only for a
  texture whose MAG_FILTER the game never set (the record wins; the inference is the fallback and is named as such).
- `wrapRepeats` keeps the query - which now answers from the record - and counts `wrapDefaults` when the pname was
  never recorded, so "never set" is distinguishable from "set to CLAMP". The counter is in the `glLess[...]` split.
- `VulkanGlTexture.confirmWrapFromRecord(glTextureId)`, called once per mirror at its first lookup, re-consults the
  record and recreates the sampler (bumping the generation so descriptor caches keyed on `key()` cannot keep naming
  the destroyed sampler). This is the ordering fix weather-water named: `uploadTextureImageAllocate` uploads the
  image first and sets the wrap after, so a sampler built at creation time can hold the default.

Measured in the `videoSettings` leg (`t39-wrap`, jar `d0781f5da1cd082d974305ee`):
`CELTIUM_TEX_PARAM_CENSUS texture params: recorded=194 textures=29 wrapRepeat=38 wrapClamp=0 calls=20592 …` and
`wrapDefaults=16` -> `22` - the game sets REPEAT on 38 texture parameters and CLAMP on none, so the old hardcoded
CLAMP answer was the bug, as weather-water measured from the sources.

**And the tiled background now matches the GL reference exactly.** The `videoSettings` GUI frame of the
new leg against the stored upstream reference (`.celtium-work/runtime/vs-pic-ref`, real GL, same scene and pose), by
region:

| region | reference vs after (new jar) | reference vs before (`vs-apply4`, hardcoded CLAMP) |
|---|---|---|
| tiled background, left margin (20,120)-(150,620) | **mean 0.00, >8 0.00%**, 33 distinct | mean 3.55, >8 11.43%, 26 distinct |
| tiled background, right margin (1150,120)-(1270,620) | **0.00, 0.00%**, 35 | 4.48, 17.15%, **8** |
| tiled background, bottom band (20,620)-(1260,700) | **0.00, 0.00%**, 69 | 5.12, 24.55%, 66 |
| options/buttons area (180,100)-(1100,600) | **0.00, 0.00%**, 103 | 1.55, 4.35%, 76 |

The whole screen is bit-identical to the real GL render after the record is consumed; before it, the tiled
background differed in 11-25% of its pixels and its distinct-colour count collapsed (8 against 35 in the right
margin - one stretched tile). That is the user's "the settings screen's background is buggy", measured and fixed.

**The scene's panel floor does not describe this state, and that is not a renderer defect.** The `videoSettings`
gate wants `guiPanelDistinctColours >= 15,805`, and the *reference leg itself* reports `guiPanelDistinctColours=133`
with `presentedImageVerdict=FAILED_PRESENTED_IMAGE` on the same frame that is pixel-identical to the candidate. Both
legs are flat-panel-free (the screen is fully drawn); the floor was calibrated on a different fixture state. Handed
to the acceptance owner as a calibration finding, not a defect.

No `CELTIUM_SAMPLER_WRAP` line fired, so no sampler was rebuilt for a changed mode: the mirrors were created after
the record existed, which is the ordering weather-water predicted for this scene.
