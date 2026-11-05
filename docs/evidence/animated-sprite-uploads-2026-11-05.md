# Animated sprite uploads: the tick-frame tail (2026-11-05)

## Where the remaining gap is

Round 74 (`r74kpi`, renderer `ae276acd…` vs Celeritas `567dcf8a…`, AMD, world-accept, 3 clean pairs): p50 0.914x
NOT_SLOWER, but p99 1.06-1.11x in every pair. Split by frame kind (fixture fields, warm ticks 400..600):

| | Celtium r1/r2/r3 | Celeritas r1/r2/r3 |
|---|---|---|
| plain-frame p99 | 3.85 / 3.04 / 3.23 | 4.51 / 4.24 / 4.20 |
| tick-frame p50 | 5.28 / 5.28 / 5.02 | 4.78 / 4.77 / 4.73 |
| client tick p50 | 1.85 / 1.87 / 1.76 | 1.66 / 1.62 / 1.62 |

Plain frames are already better; the tail is the 200 tick frames.

## Ceiling: skip the animated uploads

`-Dceltium.vulkan.skipAnimatedUploads=true` (`35609443f`, diagnostic: the atlas keeps each sprite's first frame).
`r75anim` (renderer `04dddede…`, paired same jar): reps 1-2 clean, rep 3 lost to an external load spike (load average
16; candidate r3 disturbed, control r3 timed out while loading), so no runner verdict.

| rep | tick-frame p50 skip / normal | client tick p50 skip / normal | frame p50 skip / normal |
|---|---|---|---|
| 1 | 4.57 / 5.43 | 1.48 / 1.88 | 2.215 / 2.339 |
| 2 | 4.44 / 4.77 | 1.52 / 1.74 | 2.036 / 2.121 |

Without the uploads the tick frame is below Celeritas's. In the join-aligned wall profile (r69prof) the path is 60
samples per 9 s, 51 of them in `VulkanGlTexture.uploadPatchedLevels`, mostly `vkCmdCopyBufferToImage` - one per sprite
per mip level, each a meta operation on RADV.

## What did not help

One command buffer and one submission per tick instead of per sprite (`b8ab01381`, reverted `801fb45bc`): `r73anim`
median control ratio 0.993x, tick frames unchanged. The submit was not the cost.

## The change

`a565cbf03`: inside the per-tick bracket each sprite's patch stages its bytes and records its regions; at the end, one
barrier, one `vkCmdCopyBufferToImage` per mip level with every sprite's region, one barrier back. Rules that keep it
exact: only levels already in SHADER_READ_ONLY defer; a baseline clear takes the old path; an overlapping region
flushes first; every submit of the batch records pending regions first; the image's other writers submit them before
their own commands. Rollback: `-Dceltium.vulkan.deferPatchRegions=false`.
