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

## 3. The block overlay: the poison leg ran, its control did not

`t39-poison` (breakBlock + `-Dceltium.vulkan.mirrorPoisonFlat=12=FFFF00FF`) ran: `id3=4/4` accepted, the overlay
registered (`breakProgressCalls=5`, `breakSubject=BlockPos{x=0,y=11,z=-5} minecraft:stone[variant=stone]`), verdict
PASSED. The control (`t39-poison-control`, same flags plus `breakSubjectOnly=true`) was queued out of the slot
window and did not run, so the question the pair answers - does the drawn pixel change when the atlas mirror's
content is replaced, i.e. does the draw sample the mirror's image for name 12 - is **not yet answered**. One run
remains: `run-legs.sh t39-poison-control t39-poison-control`, then compare the block region of the two poisoned
legs. (Lead's `texMat=none` for all seven `fmt=3` groups already killed the stale-affine hypothesis.)

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

**But the settings screen's panel is still flat**: `guiPanelDistinctColours=133` against the reference's 15,805,
`guiPanelChangedFraction=0.999996`, verdict FAILED_PRESENTED_IMAGE (`sceneCalibrationKeyMatches=true`). The record is
consumed and the wrap is REPEAT, yet the tiled options background does not appear - so the wrap was necessary and is
not sufficient, and the next measurement is the panel crop of the GUI frame against the stored reference
(`.celtium-work/runtime/vs-pic-ref`) with the mirror dump of the background texture to see which region/level it
samples. No `CELTIUM_SAMPLER_WRAP` line fired, so no sampler was rebuilt for a changed mode - the mirrors were
created after the record existed.
