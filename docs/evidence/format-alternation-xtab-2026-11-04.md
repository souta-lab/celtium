# Format alternation never shares a texture: layout unification buys nothing (2026-11-04)

Leg `r8-census` (renderer `1d8504b6…`, fixture `22fcf3e0…`, AMD RX 6400 RADV, `world` profile
`celtium-world-accept`, validation/readback OFF, `-Dceltium.vulkan.mergeCensus=true`, 6854 frames): PASSED.

The merge census names the first term that refused a merge, and format is checked before texture, so
`format-A>B` could not say whether an alternation would have merged had the layouts been one. A cross-tab now
records, for every format refusal, whether the texture was the same and whether the batch is order-free
(blend off, depth test and depth write on). Keys start with `xtab-` and are not part of the refusal sum.

| cross-tab | count | per frame |
|---|---|---|
| `xtab-format-sametex-*` | **0** | 0 |
| `xtab-format-difftex-blend` | 230,875 | 33.7 |
| `xtab-format-difftex-opaque` | 171,168 | 25.0 |
| `xtab-format-difftex-nodepth` | 34,269 | 5.0 |

Totals: `groupCreateCount=774,379` (113/frame), format refusals 431k (63/frame; 2<->6 226k, 2<->4 123k, 2<->7 67k).

## What it decides

- **Rejected: one pipeline for formats 2 and 6** (or any layout unification). Not one format alternation kept
  its texture, so every one of them would still open a group on the texture term.
- **Upper bound for a second open group**: only the order-free part can be reordered invisibly - 25 group opens a
  frame of 113 (22%), and only those that alternate between exactly two (format, texture) pairs with nothing
  between them. The blended 33.7/frame cannot be reordered.

Re-entry: if a later census shows same-texture format alternations, or the blended share collapses.

## A-B-A openings: the real ceiling of a second open group

Leg `r9-aba` (renderer `86c33dcb…`, fixture `22fcf3e0…`, same profile and flags, 7913 frames, PASSED). Each group
opening is compared with the group *before* the closing one (reset at every window boundary): equal key
(format, texture, state key) is an A-B-A alternation that a design keeping two groups open could have merged.

| opening | count | per frame |
|---|---|---|
| `xtab-open-aba-opaque` | 134,344 | **17.0** |
| `xtab-open-aba-ordered` (blend on, or no depth test/write) | 235,817 | **29.8** |
| `xtab-open-new-opaque` | 74,663 | 9.4 |
| `xtab-open-new-ordered` | 496,619 | 62.8 |

`groupCreateCount=941,443` (119/frame).

- An opaque-only second slot merges at most **17 of 119** openings (14%), and even that is not order-safe in
  general: moving A past B is invisible only if B cannot be seen through or over A (B blended or untested would now
  land on top of A).
- The larger share is the ordered A-B-A (29.8/frame, mostly GUI). Reordering it is invisible exactly when B's
  screen rectangle does not overlap A's - the ImmediatelyFast idea. A bounding box per batch (min/max during the
  vertex copy) would make the merge decision local and exact for the ortho GUI pass; the ceiling is 47 of 119.
  Not built yet.

## How many A-B-A openings stay clear of B on screen

Leg `r10-rect` (renderer `047b51c1…`, fixture `22fcf3e0…`, same profile and flags, 7816 frames, PASSED). For each
A-B-A opening the batch's NDC rectangle (positions through the current projection and model view; a vertex with
w <= 0 or a batch over 4096 vertices counts as the whole screen) is tested against the union rectangle of the closing
group B. Closed rectangles: a shared edge counts as overlap.

| A-B-A opening | count | per frame |
|---|---|---|
| `xtab-aba-disjoint-opaque` | 109,405 | 14.0 |
| `xtab-aba-disjoint-ordered` | 110,319 | 14.1 |
| `xtab-aba-overlap-ordered` | 101,091 | 12.9 |
| `xtab-aba-overlap-opaque` | 21,717 | 2.8 |

`groupCreateCount=898,083` (114.9/frame). **28.1 openings a frame (24%) could be merged back into A without any
change to the picture**, because nothing in B is over or under them. At ~0.9 us of recording plus ~3 us of slow-path
accept per group that is an estimated ~0.11 ms a frame - about a fifth of the 0.52 ms gap to Celeritas, not all of it.

## 2026-11-05: the merge-across switches cannot measure the ceiling

Leg `r65census` (renderer `ae90347a…`, fixture `22fcf3e0…`, AMD RX 6400, world-accept, guiScale 3 seeded, PASSED,
calibration key matched, validation off for timing) with `mergeAcrossFormat=true mergeAcrossTexture=true
mergeCensus=true`: **121.8 groups a frame**, unchanged from the shipped ~118. Only the predicate at
`VulkanTerrainGpuBackend` reads the two switches, so the openings they should have removed are refused by a later term.
A format switch carries its own per-group state: format 2 has per-vertex colour and 6/7/4/9 take the push-block tint
(the colour term), and a textured and an untextured layout differ in the texture affine. So the switches neutralise the
first term only and the census's first-term `format=544,076` cannot show the term that actually refused. (Without the
seeded guiScale 3 the world-accept legs come back `UNCALIBRATED_PRESENTED_IMAGE`, see verification-traps.md.)

The A-B-A count reproduces on this build: disjoint A-B-A openings (118,431 opaque + 130,398 ordered) / 8,465 frames
= **29.4 a frame** (round 60: 28.1). A merge-back compares the batch with A, which has the same format and texture, so
the finding above does not bound it.
