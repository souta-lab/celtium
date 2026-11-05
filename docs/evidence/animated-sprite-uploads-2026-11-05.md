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

## Intel tick-frame split (`r90split-intel`, renderer `3d4fd4fe…`, hold to tick 900)

Steady block (tickFrames=352, plainFrames=3648, mean us): frame 5420/2973, world 4683/2345. Named inner brackets that
grow on tick frames sum to ~0.8 ms (terrain 701/493, setupTerrain 240/69, particles 145/52, offPreflight 121/25,
offPrepare 103/25, terrainEnsure 108/29, flush 685/611); **~1.5 ms of the world bracket's tick-frame excess is outside
every named bracket**. That unbracketed part of the world pass is the next instrument gap for the Intel tail.

## Where in time the tick-frame excess is (`r92split-amd`, 1000-frame blocks, renderer `0701f03e8`)

Join 16:31:31; warm ticks 400..600 are about 16:31:51-16:32:01.

| block ending | tick / plain frame (us) | what grows on tick frames |
|---|---|---|
| 16:31:42 (world start) | 47620 / 1302 | everything |
| 16:31:52 (join+14..21 s) | **11219 / 2880** | chunk-load wave: setupTerrain 1447/104, terrainEnsure 734/26, stPrepare 613/35, stUpload 361/3, chunkUpload 280/1 |
| 16:31:56 (in window) | 3215 / 2641 | worldPass +463: flush +62, terrain +58, particles +44, lightmap 70/0 |
| 16:31:59 (in window) | 2616 / 2108 | worldPass +345, lightmap 60/0 |
| 16:32:02-16:32:07 | 2299/1919 ... 2119/1746 | settled: +370..400, lightmap 48-57/0 |

The post-join chunk-load wave sits right at the start of the warm window, so whether a run's p99 carries it depends
on small timing shifts - consistent with the run-to-run spread of the tail all day. The settled excess on tick frames is
~0.4-0.5 ms and spread out; the only Celtium-specific named item is `lightmap` (50-70 us on every tick frame, 0 on plain
ones: the per-tick lightmap re-upload). ~250 us of the world-pass excess is still unnamed.

## Five pairs each (2026-11-05 evening, renderer `3d4fd4fe…`, AMD, world-accept, guiScale 3, same jar both arms)

| mode | median control ratio | client tick p50, candidate (5 reps) | control (5 reps) |
|---|---|---|---|
| `=load` (`n4load`) | 1.000x | 1.86 1.85 1.92 1.88 1.91 | 1.80 1.95 1.94 1.83 1.83 |
| `=upload` (`n4upload`) | 1.035x | 1.68 1.74 2.14 1.69 1.88 | 1.98 1.87 2.01 1.93 2.27 |

- `=load` has no effect: the r80load 0.902x was noise. So the earlier explanation ("texture state that loading leaves
  behind") is rejected.
- `=upload` lowers the client tick again (median 1.74 vs 1.98 ms; 4 of 5 pairs, and 3 of 3 in r78upload). The frame
  ratio went the other way (1.035x), but that half ran under user load (load average 4.9, candidate r3 p99 14.3 ms),
  so it is not read as a frame-time result.
- The registry's own cost line answers the count: at fixture end the control legs report `patches=17041/62769252B/
  547ms` (r1) and `/510ms` (r2) - the whole run's sprite patches, i.e. the ~17,300 load-time ones of r79-source, so
  in-world patches stay in the hundreds and r79-time's 308 calls / 2 ms stands. (`animatedImageFills`, 17,049 -> 23,107
  over the world, is not that count: it also counts whole-texture int[] uploads such as the lightmap and per-frame mod
  textures.) In-world mirror work therefore cannot carry 0.24 ms per tick.
- What `=upload` changes besides that: load-time patches are dropped too, so the block atlas mirror is never filled
  from patches (`patches=0/0B` in candidate legs) and every later announcement meets a different registry state.
  `=load` drops the same load-time patches but runs the in-world path, and showed nothing. The two sets ran at
  different machine load (control medians 1.83 vs 1.98 ms), so the 0.24 ms is also within the set-to-set spread.
  Status: unexplained, possibly not real; not a lead for the p99 tail. The counter `inWorldAnimMirror=<calls>/<us>/
  worst` (e68601a76) confirms the in-world count on the next leg.

## Reading the tick-frame brackets as means (code reading, no new run)

- `terrainSharedIndex` 71/4 us (tick/plain, AMD split) is one event: `sharedIndexRegenerations=1` per run in every
  leg checked (n3kpi-amd-celtium-r1, n4load/n4upload control r1). A mean over ~200 tick frames turns one regeneration
  into 71 us; it is not a per-tick cost and cannot carry p99. Means of rare events have to be read with their counts.
- The lightmap's per-tick path (`announceFullPixels` whole-texture -> `uploadFromImage` -> `copyToImage`) waits only
  on its own previous upload's fence (one tick earlier), writes 1 KB to persistent staging, records into its own
  command buffer and submits deferred; nothing in it waits on the frame. Its measured 50-70 us per tick is CPU work,
  not a stall. Next (needs a leg): the split with `e68601a76` (entities bracket, `inWorldAnimMirror`).

## l1split-amd: the tick-frame excess named (2026-11-05 21:23, renderer `56293e99…` = 79392badc, world-accept, guiScale 3, validation off, PASSED)

With the `entities` bracket closed on the cancelled path (e68601a76), the settled block (1000 frames, 61 tick frames)
reads frame 1433/1183 us (tick/plain, +250): `lightmap` 74/0, `entities` 83/34, `terrain` 127/87, `flush` 224/184,
sky/winRecord/wrecLoop ~+10 each; `worldPass` 860/718, so little is left unnamed inside it. The block before it had
`lightmap` 308/0. `inWorldAnimMirror=231/4999us/worst801us`: the direct count agrees with r79-time (hundreds of calls,
a few ms per run), so in-world sprite animation is not a tick-frame cost. Next: dynAnnounce/dynFlush/dynMirror inside
the light map's upload (a1e25c8ee).
