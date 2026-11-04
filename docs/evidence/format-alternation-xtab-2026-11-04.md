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
