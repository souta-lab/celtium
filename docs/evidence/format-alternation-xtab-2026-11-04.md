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
