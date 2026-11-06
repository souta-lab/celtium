# The client tick's own split, and the light cache's invalidation (2026-11-07, round 12)

Round 11 named the paired KPI's largest Celtium-specific difference and could not explain it: the fixture measures
`Minecraft.runTick` head to return (`warmTickP50Ms` / `warmTickP95Ms`, the same game code in both arms) and Celtium
read **1.827 / 2.258 ms against Celeritas's 1.696 / 2.010** - +131 us at the median, +248 us at p95 - while the tick
frames' p95 gap was 520 us. Celtium's *named* work inside the tick was 33 us. The other ~100 us had no name, because
the frame brackets cannot see the tick: `FramePassTimings.begin` returns early while no frame is open, and the tick
runs between frames.

## 1. The instrument: `TickTimings`

`TickTimings` keeps one row per tick and its own percentile report, and `FramePassTimings` forwards every bracket to
it while a tick is open - so the existing names (`dynUpload`, `dynAnnounce`, `dynMirror`, `lightmap`, ...) are
measured in the tick with no call-site change, and the tick-only names are ordinary bracket names. The window is the
fixture's warm window (`[400, 600)` ticks after the world loads, `-Dceltium.vulkan.tickTimingsFrom/To`), so the
world-load wave cannot dilute the percentiles. Off unless `-Dceltium.vulkan.tickTimings=true`.

New names: `anim` (`TextureMap.updateAnimations`, the whole animated-sprite pass), `animUpload` (one sprite's
`TextureUtil.func_147955_a`), `animMirror` / `animFlush` / `animStub` (the three things Celtium adds to that upload),
`animBatchEnd` and its halves `animBatchFlush` / `animBatchBegin` / `animBatchCmd`, `animInvalidate`, and
`lightCache` (Celtium's per-tick light lookups).

## 2. What the tick costs

Leg `r12tick2-amd` (jar `6e860121`, AMD RX 6400, world, guiScale 3, validation off, warm ticks [400,600), 240 s,
`fixtureStatus=PASSED`, validation 0):

```
CELTIUM_TICK_SPLIT ticks=200 window=[400,600) tick[p50=2561 p95=3037]
  (us; p50/p95/calls-per-tick, brackets with p50 >= 5)
  mouseOver=21/29/1 anim=201/288/3 animUpload=75/103/16 animMirror=30/43/16 animStub=28/34/16
  animBatchEnd=68/84/3 animBatchFlush=65/79/3 tickOther=2360
```

| bracket | p50 | p95 | calls/tick | what it is |
|---|---|---|---|---|
| `anim` | **201** | 288 | 3 | `TextureMap.updateAnimations` for the three atlases that tick |
| `animUpload` | 75 | 103 | 16 | one sprite's frame: the announcement, the stub's capture, the game's own copy |
| `animMirror` | 30 | 43 | 16 | Celtium: the atlas mirror's patch |
| `animStub` | 28 | 34 | 16 | Celtium: the stub's conversion and capture write |
| `animBatchEnd` | **68** | 84 | 3 | Celtium: the patch batch's close |
| `animBatchFlush` | 65 | 79 | 3 | Celtium: the deferred regions' recording (inside `animBatchEnd`) |
| `tickOther` | 2360 | | | the game's own tick work |

`animBatchBegin` and `animBatchCmd` did not reach 5 us, so the command buffer's acquisition and the submit are not
the batch's cost: **`animBatchFlush` is** - the deferred regions are 16 sprites times their mip levels, recorded as
buffer-to-image copies, and RADV implements each region as its own meta operation.

**Celtium's own work in the tick is `animMirror` 30 + `animStub` 28 + `animBatchFlush` 65 = 123 us**, which is the
+131 us the KPI measures. The animation path is the tick's Celtium cost; the light cache and the dynamic-texture path
do not reach 5 us in the tick at all.

The split's total (2561 us) is the `runTick` head-to-return span, not the fixture's `warmTickP50Ms` (1.855 ms on leg
`r12tick-amd`, the same build family): the fixture's handler sees only the part of the `ClientTickEvent` dispatch
between its own START and END handlers, and with 188 mods the rest of the dispatch is ~650 us. That difference is the
same in both arms and is not part of the comparison.

## 3. The light cache's invalidation was O(capacity)

`CELTIUM_FRAME_TICK_CONSTANTS` on the same leg (late block, frames 8193-12288, tick/plain p50 in us):

```
frame=1845/942 world=1278/561 ... lightCache=73/1
```

`lightCache` costs **73 us on a tick frame and 1 us on a plain frame**. The mechanism is `rollTick`: the cache is
emptied whenever the world time or the light epoch changes, and `getTotalWorldTime()` advances on every tick, so a
tick frame pays a clear while a plain frame does not. A `Long2IntLinkedOpenHashMap` of 16 384 slots clears four
arrays (key, value, link, order) - about 320 KB per map, 640 KB for the pair - and the day-cycle sweep can bump the
epoch again mid-frame.

**The fix**: the value carries the generation in its high 32 bits (`Long2LongLinkedOpenHashMap`), and a lookup
compares it. An invalidation is one increment; a stale entry is a miss that is overwritten in place. The maps keep
their LRU bound, so the working set is unchanged, and the only case that still clears is the 32-bit wrap (4 billion
invalidations away). Semantics are preserved by construction: an entry is served only if it was written in the
current generation, which is exactly what the clear guaranteed.

## 4. The paired set on this build

`r12kpi-amd` (AMD RX 6400, Supersymmetry 0.2.0.2, validation off, warm ticks [400,600), guiScale 3, 240 s, 3 reps,
renderer `a3de00e6`, fixture `f15ba461`, reference Celeritas 2.5.0-dev `567dcf8a`, load guard passed and not
bypassed: initial busy 0.136 cores, external 0.174, limits 2.0 / 0.5; all six runs `fixtureStatus=PASSED`,
validation 0, `gpuRequirementMet=true`):

| rep | Celtium p50/p95/p99 (ms) | Celeritas p50/p95/p99 (ms) | p50 | p95 | p99 |
|---|---|---|---|---|---|
| 1 | 2.202 / 4.159 / 5.573 | 2.476 / 4.647 / 5.926 | 0.889 | 0.895 | 0.940 |
| 2 | 2.112 / 3.996 / 5.304 | 2.470 / 4.622 / 5.597 | 0.855 | 0.865 | 0.948 |
| 3 | 2.226 / 4.190 / 5.873 | 2.366 / 4.421 / 5.120 | 0.941 | 0.948 | **1.147** |
| **median** | | | **0.889** | **0.895** | **0.948** |

`verdict=NOT_SLOWER`, `goalCondition=NOT_MET` - one p99 pair is above 1.0. The split (`frameSplitRatios` medians):

| population | r12 | r11 |
|---|---|---|
| plain p50 | **0.885** | 0.909 |
| plain p95 | 0.898 | 1.002 |
| plain p99 | **0.884** | 0.918 |
| tick p50 | **0.965** | 1.016 |
| tick p90 | **0.993** | 1.094 |
| tick p95 | **0.996** | 1.093 |
| tick p99 | 0.882 | 1.342 |

**The tick frames are at parity where round 11 read 9 % behind.** The remaining failure is rep 3, and it is a pair
outlier on both sides: Celtium's tick frames read 6.104 / 6.500 ms there against 5.848 / 6.005 in rep 1, while
Celeritas's read 5.211 / 5.469 against 6.017 / 6.531. The KPI's `disturbedRepetitions` is empty, so the detector did
not flag it; the numbers do.

**Attribution, stated as the evidence allows.** The build differs from round 11's by the tick instrument (off in the
KPI: no `tickTimings`, no `passSplit`, so every bracket returns on one static read) and by the light cache's
invalidation. The tick frames' move from 1.094 to 0.993 is the direction that fix predicts, but the bracket measured
73 us of a 5.7 ms tick frame (1.3 %) in the late regime and the move is 9 %, so the size is **not** attributable to
it alone - the two paired sets are separate runs, and Celtium's own absolute p50 moved 2.09-2.30 ms (r11) to
2.11-2.23 ms (r12) within run-to-run variance. What the paired set does establish is that the build does not regress
and that the tick frames no longer lose.

## 5. What is left

The p99 needs the tick frames' p90 at ~0.9 in the worst rep, not 0.993. The tick's Celtium work is 123 us of 2410
(5 %), so halving it buys 1 %; the rest is the renderer's absolute cost on a tick frame (`flush` 188-247 us,
`terrain` 277, `guiBatch` 91-110, `dyn` 38, `setupTerrain` 47, `sky` 41) and the game's own work. Two contained
candidates the split now names: `animStub` (28 us - the stub's capture of a patch the announcement already supplies)
and `animBatchFlush` (65 us - the mirror's per-region copy recording, which is the driver's cost per region).

## Reproduce

```
scripts/gv-leg.sh r12tick2-amd .gv-verify/jars/r12-tick2 world amd 240 <flags> \
  -Dceltium.vulkan.tickTimings=true -Dceltium.vulkan.tickPercentiles=true \
  -Dceltium.vulkan.tickPercentileFrames=4096 -Dceltium.vulkan.passSplitMicros=true
CELTIUM_KPI_GPU=amd CELTIUM_KPI_LOAD_LIMIT=2 CELTIUM_KPI_JARS=.gv-verify/jars/r12-fix \
  CELTIUM_KPI_REFERENCE=.gv-verify/lib/01-celeritas-forge-mc12.2-2.5.0-dev.jar CELTIUM_KPI_REPS=3 \
  scripts/gv-kpi.sh r12kpi-amd 240 <flags>
```

with `<flags>` = `-Dceltium.vulkan.validation=false -Dceltium.vulkan.presentReadback=false
-Dceltium.vulkan.presentedDumpEvery=0 -Dceltium.validation.guiScale=3 -Dceltium.validation.warmFromTick=400
-Dceltium.validation.warmToTick=600 -Dceltium.validation.holdUntilTick=620 -Dceltium.debug.logLinesPerTag=1000000
-Dceltium.validation.warmSpikeMs=0`.
