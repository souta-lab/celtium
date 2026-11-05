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

## Correction (same day): in-world sprite animation is not the cost

The deferral (`a565cbf03`) measured no effect either (`r77defer`, ratio 0.988x, tick frames equal) and was reverted
(`440479bd6`). Then positive controls, which both negative results lacked:

- Engagement (`r79-engage`, deferral code restored with counters, validation on, PASSED, validation 0): over 864
  animation brackets (one per tick) only **361 patches** were deferred, with no fallback. The bracket worked, but there
  is under one animated sprite upload per tick in this scene (Celeritas's animate-only-visible filter).
- Sources (`r79-source`): 17,296 animated uploads in the run, 17,167 to GL texture 83 (block atlas); sampled callers
  are almost all `TextureMap.finishLoading` <- `ModelManager.onResourceManagerReload`, i.e. resource loading, which
  ends before the world starts (last reload 14:48:32, join 14:49:40 in `r79-time`).
- Cost (`r79-time`): in-world announcements timed inside the bracket: **308 calls, 2 ms in total** (worst 242 us).

So the batching and deferral had nothing to act on, and the 59 samples the join-aligned profile put on in-world
uploads do not match the direct count (that profile's window-to-tick mapping was already shown to be loose).

What remains unexplained: `skipAnimatedUploads=upload` lowered the client tick in all three pairs (`r78upload`:
1.83/1.70/1.77 vs 1.98/2.00/2.03 ms; ratio 0.943x) and `=all` lowered tick frames (`r75anim`). Both modes also skip the
~17,000 load-time uploads, so the difference is most likely the texture state that loading leaves behind, not
per-tick work. `=flush` changed nothing and its counter shows that flush never fires (`r78flush`, 0 in every leg).
Next discriminating test: skip uploads only before the first world frame vs only after it.

`r80load` (`=load`, renderer `8aed10f9…`, 3 pairs, PASSED): median control ratio 0.902x but rep 1 went the other way
(2.478 vs 2.305), client tick 1.93/1.92/1.92 vs 1.84/1.99/2.09, and the control alone ranged 2.305-2.692 ms across
reps. Inconclusive in the daytime: the rep-to-rep spread is larger than the effect. Resume on a quiet machine with
five pairs each of `=load` and `=upload`.

## Intel, same renderer: p50 NOT_SLOWER, the tail is render work on tick frames

`r81intel` (renderer `8aed10f9…` = current behaviour, reference `567dcf8a…`, Intel UHD 630, world-accept, guiScale 3,
3 pairs, all PASSED): **median paired p50 0.901x NOT_SLOWER**, p95 1.015, p99 1.054, goalCondition NOT_MET.

| rep | p50 Celtium / Celeritas | client tick p50 | tick-frame p50 | plain-frame p99 |
|---|---|---|---|---|
| 1 | 3.539 / 3.974 | 2.29 / 2.48 | 6.95 / 6.69 | 6.14 / 6.34 |
| 2 | 3.415 / 3.600 | 2.24 / 2.35 | 6.58 / 6.47 | 6.18 / 5.70 |
| 3 | 3.454 / 3.834 | 2.22 / 2.33 | 6.71 / 6.42 | 5.68 / 6.16 |

On Intel Celtium's client tick is *faster* than Celeritas's, yet its tick frames are slower: the excess on tick frames
is the render that follows the tick, not the tick itself.

## GC and allocation in the warm window (join-aligned JFR, `settings=profile`, 9 s)

| run | young GCs | pause total | worst |
|---|---|---|---|
| `r84jfr-celtium` (renderer `8aed10f9…`) | 2 | 108.8 ms | 69.8 ms |
| `r84jfr-celeritas` (`567dcf8a…`) | 1 | 18.0 ms | 18.0 ms |
| `r85jfr-celtium` (`1abdadae…`, model-run buffers reused) | 1 | 42.4 ms | 42.4 ms |

One window each: not enough to rank GC behaviour. The allocation samples are not usable for attribution at this count:
r84 put 59.9% of the weight on `VulkanModelInstances.draw` (DirectLongBufferU), and after `0f3524831` removed those
wrappers r85 put 59.8% on an unrelated `ConcurrentHashMap$Node` - one TLAB-sized sample carries most of the weight.
`0f3524831` stays (it removes real per-draw wrapper objects; `r85-world-amd` PASSED, validation 0), but no frame-time or
GC effect is claimed for it.

## Tick-frame bracket split

`r82split`/`r83split` (`passSplit`, `passSplitMicros`): on AMD a frame after a tick costs ~2.3 ms more than a plain
one; terrain (chunk uploads, `stUpload` 115-188 us vs ~0) and `terrainEnsure` grow, but so do trivially cheap brackets
(`terrainCapacity` 46/0 us, `terrainSharedIndex` 260/4 us with one index regeneration in the whole run). Part of the
tick-frame cost is therefore not work inside those brackets but time the client thread loses on those frames
(preemption by build workers, safepoints or GC); not yet separated.
