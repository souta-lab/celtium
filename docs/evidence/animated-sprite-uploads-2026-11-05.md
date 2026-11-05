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

## The light map's tick cost is the game's own computation (l2/l3split-amd, 2026-11-05 21:27-21:35, PASSED)

Settled blocks, mean us on tick frames (plain frames 0 for all): l2 (`a1e25c8ee`) `lightmap` 70, `dynAnnounce` 10,
`dynMirror` 10, no `dynFlush` above 10; l3 (`39b9bc0cb`, jar `r97-diag`) `lightmap` 75, `dynUpload` 16, `dynAnnounce` 13,
`dynMirror` 11. The upload the renderer adds - stub `glTexSubImage2D` capture plus announcement - is ~16 us; the other
~60 us of `lightmap` is `EntityRenderer.updateLightmap`'s own per-texel computation, which Celeritas pays too (no
Celtium mixin touches the method beyond these brackets). So the light map is not where Celtium's tick frames lose to
Celeritas's; of the settled +250 us tick-frame excess over plain frames, the renderer-specific candidates are
`entities` (+30-50), `terrain` (+40), `flush` (+40-60) and the window recording (+15-30), each small. The p99 gap to
Celeritas therefore needs a comparative measurement of the same brackets, not a further split of Celtium alone.

## Client tick vs Celeritas, and a CPU profile of runTick (2026-11-05 21:35-22:21, light legs, all PASSED)

`l4kpi-amd` (2 pairs, renderer `c3102913…` = r97-diag, world-accept, guiScale 3): p50 1.000, p95 1.049, p99 1.121; client
tick p50 Celtium 2.03/1.90 vs Celeritas 1.73/1.78 ms, tick-frame p50 5.87/5.47 vs 5.12/5.19 ms. Here the tick itself is
~0.2 ms slower (r81intel had it faster, so this is not settled across runs).

`l5prof-{celtium,celeritas}`: async-profiler 4.6 `-e itimer -i 1ms -d 15` on the game JVM (the Java 25 child the Cleanroom
relauncher starts - the Java 8 parent and a short-lived helper were picked first and gave empty profiles; `-e cpu` gives
nothing at perf_event_paranoid=2; attaching 21 s after the join line is too late, the fixture exits ~22 s after it),
from 3 s after the join. Samples under `Minecraft.runTick`: 120 vs 33 of ~7,500 each.
- `ValidationClient`'s own tick handler: 52 vs 1 - but `finish()` (picture judging, PNG decode/encode) and the GL
  screenshot, i.e. after the warm window, plus `/celtium` commands that run once at world tick 100. The per-frame
  in-window part (`celtium$sampleUploadWait`, three reflective counter reads) is ~5 samples. Not a warm-window bias.
- The rest, 68 vs 32, is spread: `WorldClient` display ticks (13 vs 4), Fluidlogged API's fluid ticks (8 vs 1),
  in-game-info ticker (6 vs 14), `TextureMap.updateAnimations` (4 vs 0), chunk/entity ticks (a few each). With 1 ms
  samples over one 15 s window per arm this is not separable from noise.
Next discriminator: several profiles per arm restricted to the warm ticks (a fixture-side start/stop at ticks 400/600,
or JFR with timestamps), before any change is aimed at the tick.

## Warm-window CPU profiles, 3 pairs (l6prof, 2026-11-05 22:38-22:58, AMD, world-accept, guiScale 3, renderer `c3102913…`, fixture `12eab9d6…`, all PASSED)

async-profiler `-e itimer -i 1ms -d 10` started at the fixture's `CELTIUM_WARM_WINDOW begin` (ticks 400..600, KPI window flags),
on the Java 25 game JVM. Totals per arm over 3 reps: all samples 11,650 vs 12,053 (Celtium vs Celeritas), `Minecraft.runTick` 217 vs 272.

Client tick (`Minecraft.runTick`), Celtium minus Celeritas:
the native arm spends less CPU in the tick (-55). The only item above the noise is `TextureMap.updateAnimations`,
41 vs 28 (+13 in 30 s, ~20 us per tick); its samples sit in the per-sprite `uploadPatchedLevels`
(`vkCmdCopyBufferToImage`, `vkBeginCommandBuffer` in the Radeon driver). The stub answers `glClientWaitSync` as
already signalled, so RenderAheadManager does not wait in the tick either. l4kpi's tick wall-time gap (2 pairs) is
therefore not a tick CPU cost; it is unexplained and may be contention or noise.

Frame side, 3 reps (samples in 30 s): `onRenderTickEnd` 604 vs 243; InGameInfo HUD 446 vs 182 (+264, ~0.9% of all
time, ~30 us per frame); CreativeCore's overlay 84 vs 26 (+58); the fixture's counter reads 50 vs 2 (+48).

Breakdown (rep 1):
- InGameInfo's HUD (`Ticker.onRenderTick`): 191 vs 65. Its text is drawn through the font run, and each item icon's
  `RenderHelper.enableGUIStandardItemLighting` pushes a matrix, which is a font-run boundary: the run is submitted there
  (`celtium$submitFontRun` -> `submitImmediateBatch`), and 59 of those samples are a window flush the accept path
  triggers at `immediateFlushIndices` (8192) - Radeon driver time and `drawPendingImmediate`. Moving the boundary off
  `pushMatrix` would not help: the next `translate` is a boundary too.
- The fixture's own per-frame counter reads (`celtium$sampleUploadWait`, three `Field.getLong` through Cleanroom's
  compatibility hooks): 28 vs 0 - a measurement tax on the native arm only. Replaced by getter MethodHandles.
- CreativeCore's overlay (`OverlayRenderer.render`): 22 vs 5.

Changes queued from this: the per-tick animation batch restored (a565cbf03 via revert of 440479bd6, switches
`batchAnimationUploads` / `deferPatchRegions`), A/B `l9batch`; window-flush threshold 32768 vs 8192, A/B `l10flush`.

## Same-jar A/Bs on r100 (renderer `82f20d80…`, AMD, world-accept, guiScale 3, 2026-11-06 00:54-01:46)

| A/B | candidate / control | pairs | p50 | p95 | p99 | tick frame p50 |
|---|---|---|---|---|---|---|
| `l10flush` | `immediateFlushIndices=32768` / 8192 | 4 (pair 5's control stopped mid-load) | 0.941 | 0.937 | 1.001 | 0.993 |
| `l9bbatch` | `batchAnimationUploads=false` / batching on | 3 (disk full at pair 4) | 0.982 | 1.027 | 0.981 | 1.028 |

Per-pair ratios spread 0.88-1.06 (flush) and the control arm itself moved 2.07-2.55 ms, so neither is settled:
the larger flush threshold leans toward a p50/p95 gain, and the animation batch toward ~3% on tick frames only.
The batch stays on by default (it was measured as behaviour-identical before, and is aimed by the profile); the
threshold stays 8192 until a quieter or longer A/B. `stateKeyCache` hit rate in these legs: ~29% (r100).

## Unchanged state values keep stateVersion (4dffb77d6): r101 vs r100, 5 interleaved pairs (`l15state`, 2026-11-06 01:46-02:17)

Renderers `da008de3…` (r101) vs `82f20d80…` (r100), same fixture, AMD, world-accept, guiScale 3, all PASSED:
**p50 0.973, p95 1.000, p99 1.002, tick-frame p50 0.964** (medians of per-pair ratios). The mechanism is visible in the
legs' own counter: `stateKeyCache` hits 33.0/33.2/32.8/32.6/33.2% (r101) vs 29.1/28.6/30.2/29.7/27.4% (r100). Kept.

## Intel warm-window profiles, 2 pairs (`l16iprof`, 2026-11-06 02:18-02:33, UHD 630, renderer `da008de3…` = r101, all PASSED)

`scripts/warm-profile.sh`, samples in 20 s per arm (Celtium vs Celeritas): mod HUD (InGameInfo) 231 vs 190 (x1.2, AMD
x2.4); CreativeCore overlay 54 vs 19; `runTick` 139 vs 158; `TextureMap.updateAnimations` 11 vs 11; the fixture's
counter reads 3 (the handle fix holds). Same shape as AMD - the extra time is GUI drawing, not the tick - with a
smaller HUD factor, consistent with the window flush's driver cost being the part that differs between RADV and ANV.

## Why the offer-key cache misses (`l18miss-amd`, renderer `5a5907d0…` = 855c18763, world-accept, warm flags, PASSED)

`stateKeyCache=hit461505/1372779[invalid=9 state=1372770 env=0 window=0 sameKey=922517]` at run end: **every miss is
a stateVersion move, and 67% of them recompute the key the slot already held**. The two lighting setters still moved
the version on a repeated value (lightingEnabled bumped it and the light generation every call; lightEnabled bumped it
for an unchanged mask and for lights the mirror does not keep) - fixed in ba8d6c3a9; `l19miss-amd` and the r104-vs-r101
A/B `l20light` measure what is left.

`l19miss-amd` (r104, ba8d6c3a9): `hit512070/1487756[… state=1487747 … sameKey=1004901]` - the lighting guards did not
move the same-key share (67.5%). The key is built from blend/colour-mask/alpha/cull/depth/lighting-enable/texture-env/
window and **not** from the current colour or the per-light enables, yet both moved stateVersion; the GUI recolours
almost every text run. `l20light` (r104 vs r101, 5 pairs, PASSED): p50 0.982, p95 0.957, p99 0.997, tick frame 0.988,
key-cache hit rate unchanged (~33%) - read as noise, kept as harmless. Next: 866fff46b keys both caches by a version
the colour and light enables do not move (`l21miss-amd`, A/B `l22keyver` vs r104).

## Key version without the colour (866fff46b): r105 vs r104, 5 pairs (`l22keyver`, 2026-11-06 03:18-03:50, all PASSED)

Census `l21miss-amd`: `hit922301/874588[… sameKey=437381]` - hit rate **~25% -> ~51%**; in the A/B legs 51% (r105) vs
25% (r104). Speed (medians of per-pair ratios, r105/r104): **p50 0.981, p95 0.954, p99 0.970, tick frame 0.968**.
Kept. Half of the remaining misses still recompute the same key; the raster version moves only for keyed values, so
these are real A->B->A changes (lighting or blend toggled around an item and restored). Catching them needs a
value-keyed cache - a packed state word kept by the setters - rather than a version. Not started.

## Official KPI on r105 (866fff46b, renderer `2c713f2b…`) vs Celeritas `567dcf8a…`, 3 pairs each (2026-11-06 03:49-04:27)

| GPU | p50 | p95 | p99 | per-pair p99 |
|---|---|---|---|---|
| AMD (`l23kpi-amd`) | **0.898** | **0.927** | 1.009 | 1.009 / 1.003 / 1.010 |
| Intel (`l23kpi-intel`) | **0.951** | 0.991 | **0.876** | 0.876 / 0.872 / 1.098 |

goalCondition NOT_MET (it needs every percentile below 1 in every pair), but AMD p99 moved from 1.033 (n3, evening)
to within 1% in every pair, and AMD p95 from 1.028 to 0.927.
