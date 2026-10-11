# The near-horizon terrain band: it was the build state, not the renderer

Owner: **fog-path** (task-16, task-33). Date: 2026-10-11, ~17:45 JST. GPU: AMD RX 6400 (RADV NAVI24).
Scene: `terrain`, pose `x=0.5 y=12.0 z=0.5 yaw=180 pitch=20`, 1280x720, GL-less candidate vs upstream
Celeritas `989ebf2e` (sha256 `d12425eaaf184e7925cd41d1deb4f9df69064115c8e78c8fcedfd6ecc63f93e7`,
`worldFrameNonBackgroundFraction = 0.761173`, PASSED).

## The result

At equal build state the band is closed:

| region | before (tick 200, unequal build) | at equal build state |
|---|---|---|
| sky rows 0-199 | 0 of 254720 px over 8 | **0 of 254720** |
| band rows 200-320 | mean 26.989, 40.21% over 8 | **mean 0.403, 744/153600 = 0.484%** |
| terrain rows 320-720 | 2.68% over 8 | 0.012% |
| whole frame | 11.92% over 8 | 0.087% |

`vanilla_stage_compare.py` regions at that state: sky band `(0,0,1280,120)` 0 px over 8; horizon band
`(0,120,1280,300)` mean 0.429 / 0.32%; terrain centre `(320,300,960,640)` mean 0.016 / 0.0097%.

## The mechanism (file:line)

The render list only lists a section whose geometry has been built - `VisibleChunkCollector.visit` adds a
section when `PackedSectionMetadata.getCompactVisualsFlags(meta) != 0`
(`common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/lists/VisibleChunkCollector.java:69-76`) -
and the build budget is per **frame** (`RenderSectionManager.getTargetQueueSize()`,
`common/.../RenderSectionManager.java:464-470`: `builder.getTargetQueueSize() * REBUILD_LIST_FRAMES`), while
the fixture captured at a fixed **tick**. A leg that renders fewer frames per tick arrives at tick 200 with
less of the world built:

| leg | world frames at capture | listed sections | geometry |
|---|---|---|---|
| candidate, tick 200 | 106-162 | 34 | 23 MiB |
| reference, tick 200 | 483 | 41 | 29 MiB |
| candidate, section target | **819** | **41** | 16/18 MiB |
| reference, section target | 405 | 43 | 30/39 MiB |

So the 22-vs-32 and 34-vs-41 counts were **build throughput, not culling**. The frustum is not the limiter
(ring probes of eight boxes at 96..192 blocks: 3/8 visible at every distance), the search distance is
identical (`renderDistance=128.0 fogCutoff=128.0 searchDistance=128.0` in both), and the list-building code
is byte-identical to the pinned upstream checkout.

## The atlas-sampler control (one flag apart, same build state)

`VulkanTerrainPipeline` binds a block-atlas sampler with the game's own `GL_TEXTURE_MAX_LOD` (`maxLod = 4`;
`TextureUtil.java:215/:217`, atlas at `TextureMap.java:225`) instead of the mirror's global clamp of 1
(`VulkanGlTexture.java:290`). Same build state, same fixture:

| leg | band rows 200-320 | whole frame |
|---|---|---|
| `terrainAtlasMaxLod=4` | mean 0.403, 0.484% over 8 | 0.087% over 8 |
| `terrainAtlasMaxLod=1` (mirror clamp) | mean 1.149, **1.193%** over 8 | **0.205%** over 8 |

The sampler is required; the effect is at the terrain edge (rows 216-224: 174 px over 8 with the mirror's
clamp, 43 with the sampler; rows 232-320 are 0 px in both).

**Withdrawn claim.** An earlier reading of this band attributed "flat in the reference, striped here" to the
atlas mip *content*. At equal build state rows 225-255 are identical in all three legs (candidate
`std 2.34 mean (113.0,110.8,99.8)`, mirror-clamp control `std 2.34 mean (113.0,110.8,99.8)`, reference
`std 2.40 mean (113.0,110.8,99.8)`). The striping at tick 200 was the unbuilt far ring putting nearer,
lower-LOD terrain in those rows. Task-26's mip content is therefore not evidenced as this band's cause.

## Instruments

- `CELTIUM_LIST_INPUT` (`-Dceltium.debug.listInputLog`, default 6): render distance, fog cutoff/alpha/shape,
  search distance, occlusion traversal, graph sections, regions, camera, frustum ring probes.
- `CELTIUM_LIST_SIZE` (`-Dceltium.debug.listSizeLog`, default 0): listed sections per pass and the graph size
  over time.
- Fixture (`validation/client/ValidationClient.java`): `-Dceltium.validation.captureCondition` =
  `tick` (default, unchanged) | `built` (listed count stable for `captureStableFrames`, default 30) |
  `sections` (listed count reaches `captureSections`); `captureMinWorldFrames` floor;
  `captureDeadlineTick` fallback. Report fields under every condition: `captureCondition`,
  `captureConditionRequested`, `captureSectionsTarget`, `captureMinWorldFrames`, `captureStableFrames`,
  `captureStableFramesRequired`, `captureListedSections`, `captureBuildState`, `captureWorldFrames`.

## Runs

`t33b-fogon` (candidate, `fog-path-mod8.jar` sha256
`609eb69a9064df057dda3c5bc9ea2146080bb843ed5d9cc0ec854544dd6bb1fd`), `t33b-upstream` (reference),
`t33c-atlas1` (mirror-clamp control), fixture `fog-path-fixture4.jar` sha256
`30d0ded469c529b282f65fe07ebff70dd4c47bad8dc3cb0334a0697a03e9d83e`. All PASSED,
`pictureFrameDelta_world=0`. Crop: `.celtium-work/fogpath-jar/crops/t33b-horizon-stack.png`.
Earlier legs (`fp3-fogon`, `fp3-upstream`, `t33-fogon`, `t33-upstream`, `t16*`) are the before/after pair.
