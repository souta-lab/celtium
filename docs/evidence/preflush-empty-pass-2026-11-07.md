# The pre-flush window, and what the p99 actually is (2026-11-07, round 11)

Round 10 measured the p99 frame's composition and concluded the renderer is a quarter of its excess. This round
started from the same question, found one window that round 9's in-pass fix never reached, and measured the p99 as a
*tick-frame* statistic for the first time - which changes what "the p99 is at parity" means.

## 1. The pre-flush window that `preflushInPass` never reached

Round 9 moved the pre-terrain flush from a window of its own into the terrain pass's render pass (`PREFLUSH_IN_PASS`,
`b64cb2fce`). That fix is applied at the *head* of `celtium$renderPass`, and the pass has an early return before it:

```java
if (!renderLists.hasPass(pass)) {          // no geometry for this pass
    celtium$preflushIfDeferred();          // <- the flush, with no render pass open
    return;
}
```

With no render pass open the flush takes the window path: `frameTarget.beginFrame()`, the acquire barriers, a render
pass begin/end, the release barriers, a submit and `endFrame()` - for the sky, the void plane and the sun. The
`CELTIUM_LAYER_GEOMETRY` line says when that branch is the normal frame:

```
[Celtium] CELTIUM_LAYER_GEOMETRY layer=Solid regions=3 passes=[TerrainRenderPass[name=solid]=false TerrainRenderPass[name=cutout_mipped]=false]
```

`CELTIUM_FRAME_TICK_CONSTANTS` on the world-entry blocks of leg `r11ab-on-amd` (jar `0bd3dc1d`, AMD RX 6400, world,
guiScale 3, validation off, warm ticks [400,600), `tickPercentileFrames=4096`):

| block (frames) | frame tick/plain p50 (us) | terrain | terrainSetup | terrainPreflush | terrainRecord |
|---|---|---|---|---|---|
| 1-4096 | 1724 / 1093 | 127 / 65 | 126 / 64 | **125 / 64** | - |
| 4097-8192 | 1297 / 944 | 109 / 57 | 78 / 51 | **83 / 50** | - |
| 8193-12288 | 2074 / 1731 | 216 / 142 | - | 34 / 23 | 152 / 114 |

So on the world-entry blocks the terrain pass's whole cost *is* the pre-flush window, and the pass records nothing.
On the third block the pass has geometry, the flush happens inside its render pass, and `terrainPreflush` is 34 us.

**The fix**: when the pass has no geometry, leave the groups queued. The flush exists to put them in the image
*before this pass records the terrain into the same command buffer*; a pass with no geometry records nothing, so
there is nothing for them to be ordered against, and the queue is FIFO - the next window drains them in the order
they were offered, into the same render pass (`recordImmediateOnlyPass` takes `terrain.renderPass()`).
`-Dceltium.vulkan.preflushWhenEmpty=true` restores the old behaviour.

## 2. The A/B: same jar, one switch, and the capture tick

Legs `r11ab-on-amd` (`preflushWhenEmpty=true`) and `r11ab-off-amd` (`=false`), same jar `0bd3dc1d`, same flags, AMD
RX 6400, 240 s, both `fixtureStatus=PASSED`, validation 0.

| block | on: terrainPreflush | off: terrainPreflush |
|---|---|---|
| 1-4096 | 125 / 64 | **absent (< 10)** |
| 4097-8192 | 83 / 50 | **absent (< 10)** |
| 8193-12288 (warm window) | 34 / 23 | 37 / 26 |

The world-picture comparison of the two runs reads **11.3 mean abs diff / 7.24 % over 24 levels**, and that number is
the *capture tick*, not the change: the fixture's world picture was taken at worldTick 343 in one run and 350 in the
other. Both runs also keep a presented dump at **worldTick 200**, and that pair - same tick, same pose, same sky
phase - differs by **0.1 mean abs diff / 0.07 % of pixels over 24 levels**. The change is picture-neutral; the
world-picture number is the confound the handoff warned about, measured rather than assumed.

The frame effect is small and only at world entry (block 1: 1724 -> 1707 us p50), because the groups are still
drawn - they move to the next window, and what is saved is the window's own begin/barriers/submit. In the warm
window the change is **inert**: the pass has geometry there, so the flush was already in-pass.

## 3. What the p99 is: the tick frames' upper quartile

The fixture now reports the tick and plain frame populations separately, and the KPI artifact carries them
(`frameSplit`, `frameSplitRatios`). The `r10kpi-amd` legs, which are the last paired set, read:

| leg | plain p50 | plain p95 | plain p99 | tick p50 | tick p95 | tick frames / all |
|---|---|---|---|---|---|---|
| celtium r2 | 2.146 | 2.770 | 3.260 | 4.959 | 6.269 | 200 / 4289 |
| celeritas r2 | 2.414 | 2.941 | 3.718 | 4.857 | 5.700 | 200 / 3848 |
| celtium r3 | 2.163 | 2.846 | 3.500 | 4.863 | 6.157 | 200 / 4047 |
| celeritas r3 | 2.457 | 3.094 | 3.969 | 5.035 | 5.978 | 200 / 3564 |

Ratios (Celtium / Celeritas):

| population | r2 | r3 |
|---|---|---|
| plain p95 | **0.942** | **0.920** |
| plain p99 | **0.877** | **0.882** |
| tick p50 | 1.021 | 0.966 |
| tick p95 | **1.100** | **1.030** |

**Celtium is 6-12 % faster on plain frames and 3-10 % slower on the tick frames' tail.** The all-frames p99 is a tick
frame at the ~78th percentile of the tick population (200 tick frames of ~4 000), so the p99 ratio is a statement
about that upper quartile - and the tick frames carry the game's own client tick (1.73 ms p50, both arms) plus the
loop overhead, which is why the p99 sits at parity while the p50 is 11 % ahead.

At the same rank the gap is ~370 us: Celtium's p78.5 of tick frames is 5.624 ms, Celeritas's p81 is 5.289 ms, and
interpolating Celeritas to p78.5 gives ~5.25 ms.

## 4. The warm window's frame, and the p99 frame's excess

`r11ab-on-amd`, block 3 (frames 8193-12288, the block the warm window falls in), `CELTIUM_FRAME_TICK_CONSTANTS`
(us, tick p50 / plain p50):

```
frame=2074/1731  world=1640/1322  worldPass=1574/1309  entities=584/472  entBlockEntities=532/434
gui=385/354  flush=241/238  terrain=216/142  terrainRecord=152/114  recordPrepare=88/59
prepBarriers=27/18  prepBegin=48/33  prepDescriptors=14/2  terrainPreflush=34/23
winRecord=173/162  wrecLoop=156/149  grpDraw=96/85  winBarrier=21/18  guiBatch=91/87  guiFlush=71/73
offCapture=14/13  offPrepare=21/19  offAppend=27/25  offPreflight=20/17
setupTerrain=37/32  sky=31/17  hand=55/50  particles=52/37  lightmap=45/0
dynUpload=12/0  dynAnnounce=11/0  dynMirror=10/0  terrainBegin=38/10  closeGroup=19/17
rcPrepared=25/23  rcPush=22/21  rcViewport=16/15  recLoop=22/18  recDraw=13/12
```

The frame is 2074 us on a tick frame. The game's own work is the majority: `worldPass`'s own 533 us (1574 minus
entities 584, terrain 216 and flush 241), the pack's block entities 532 us, the HUD 385 us (of which Celtium's
`guiBatch` is 91). Celtium's own work is ~730 us (35 %): flush 241, terrain 216, `guiBatch` 91, the offer path 82,
`setupTerrain` 37, `sky` 31, the dynamic-texture path 33.

The new `CELTIUM_FRAME_TICK_TAIL` line ranks each bracket's own p99-rank value minus its own median, which is the
p99 frame's excess per bracket (us, excess / p99eq):

```
frame=239/2313  world=172/1812  worldPass=166/1740  terrain=63/279  gui=54/439  entities=52/636
terrainRecord=48/200  entBlockEntities=45/577  flush=20/261  recBind=20/29  recordPrepare=16/104
guiBatch=16/107  lightmap=13/58  terrainPrep=13/29  terrainEnsure=13/25  winRecord=11/184
terrainRefresh=11/16  setupTerrain=9/46  wrecLoop=8/164  particles=7/59  prepBarriers=7/34
winBarrier=7/28  terrainBegin=6/44  recordBatch=5/7
```

The p99 frame is **239 us above the median tick frame inside the renderer**, and the excess is spread over eight
brackets with no single owner: terrain 63 (of which `terrainRecord` 48, `recBind` 20, `recordPrepare` 16), the HUD's
accept path 16, the block entities 45, the flush 20. That is the shape a p99 ratio of 1.01 has - the renderer is a
minority of the p99 frame and its excess is not concentrated.

The flush is at its floor for its group count: `immediateGroupsDrawn=1502528` over `immediateWindows=61701` is 24.4
groups a window and 5.02 windows a frame, i.e. ~122 groups a frame, and `wrecLoop` 156 us / 122 is 1.28 us a group
with `grpDraw` 0.79 us of it.

## 5. What the round changed

- **The pre-flush no longer opens a window on a pass with no geometry** (`PREFLUSH_WHEN_EMPTY`, default on;
  `-Dceltium.vulkan.preflushWhenEmpty=true` restores). Picture-neutral at the same tick (0.1 / 0.07 %), and the
  world-entry blocks' `terrainPreflush` goes from 125 us to under 10 us.
- **`CELTIUM_FRAME_TICK_TAIL`**: per bracket, its own p99-rank value minus its own median, largest first. The
  existing percentile line filters on `|p99eq - plainP79|`, which hides exactly the brackets that are expensive on
  every frame - the shape the p99 has.
- **The tick-frame p90 and p99 in the fixture report** (`frameIntervalWarmTickFrameP90Ms` / `...P99Ms`) and the
  plain p50, and **the split in the KPI artifact** (`frameSplit` per arm, `frameSplitRatios` per metric). The p99
  cannot be read as a renderer number without them.

## 6. The paired set on this build (`r11kpi-amd`)

AMD RX 6400, Supersymmetry 0.2.0.2, validation off, warm ticks [400,600), guiScale 3, 240 s, 3 reps, renderer
`4e1507af…`, fixture `f15ba461…`, reference Celeritas 2.5.0-dev `567dcf8a…`, load guard passed and not bypassed
(initial busy 0.187 cores, external 0.161, limits 2.0 / 0.5):

| rep | Celtium p50/p95/p99 (ms) | Celeritas p50/p95/p99 (ms) | p50 | p95 | p99 |
|---|---|---|---|---|---|
| 1 | 2.264 / 4.281 / 6.021 | 2.481 / 4.668 / 5.547 | 0.913 | 0.917 | 1.085 |
| 2 | 2.298 / 4.210 / 5.743 | 2.378 / 4.431 / 5.342 | 0.966 | 0.950 | 1.075 |
| 3 | 2.091 / 3.974 / 5.501 | 2.512 / 4.686 / 5.633 | 0.832 | 0.848 | 0.977 |
| **median** | | | **0.912** | **0.917** | **1.075** |

`verdict=NOT_SLOWER`, `goalCondition=NOT_MET`. The p99 is 1.075, i.e. worse than round 10's 1.013 - and the split
says why, in the same artifact for the first time (`frameSplitRatios`, medians of the three pairs):

| population | median ratio |
|---|---|
| plain p50 | **0.909** |
| plain p95 | 1.002 |
| plain p99 | **0.918** |
| tick p50 | 1.016 |
| tick p90 | **1.094** |
| tick p95 | **1.093** |
| tick p99 | 1.342 |

**Celtium is 9 % faster on the plain frames' median and 9-13 % slower on the tick frames' p90/p95.** The all-frames
p99 is the tick frames' upper quartile, so the p99 ratio is the tick ratio.

And the tick frames' own non-renderer half is where the difference is: the fixture's client-tick measurement
(`warmTickP50Ms` / `warmTickP95Ms`, the same game code in both arms) reads

| leg | tick p50 | tick p95 |
|---|---|---|
| celtium r1 / r2 / r3 | 1.827 / 1.846 / 1.702 | 2.258 / 2.237 / 2.260 |
| celeritas r1 / r2 / r3 | 1.696 / 1.613 / 1.715 | 1.999 / 2.010 / 2.079 |
| **median difference** | **+131 us** | **+248 us** |

**Celtium's client tick is 131 us slower at the median and 248 us slower at p95.** That is inside `Minecraft.runTick`,
not inside the frame bracket, and it is the largest single Celtium-specific difference the KPI has produced: the
tick frames' p95 gap is 6.19 vs 5.67 ms (+520 us), of which the client tick is +248 and the renderer the rest.

## 7. What it means for the goal

The p99 needs ~370 us off Celtium's tick-frame interval, and the split now says where it is not: it is not the plain
frames (0.909 / 0.918), and it is not a single renderer bracket (the p99 frame's renderer excess is 239 us spread
over eight). The two levers the data names:

1. **Celtium's client tick: +131 us p50 / +248 us p95.** The tick is `Minecraft.runTick` - entity and tile-entity
   ticks, texture animation, the lightmap - and Celtium's hooks in it are the texture-animation announcement
   (`announceAnimatedUpload` → `announceFullPixels`) and the dynamic-texture path (`dynUpload` / `dynAnnounce` /
   `dynMirror`, 33 us of the frame). 33 us of the 131 is named; the rest is not, and finding it is the next round's
   first job because it is the largest Celtium-specific number in the KPI.
2. **The renderer's absolute cost on a tick frame.** The frame is 2074 us, Celtium's own work is 730 us (35 %), and
   the flush (241 us) is at its floor for 122 groups. A 370 us cut is half of Celtium's own work, so it is a
   multi-round effort.

The pre-flush window this round removed is real but belongs to the world-entry regime, not the warm window: it is
picture-neutral (0.1 / 0.07 % at the same tick) and it takes `terrainPreflush` from 125 us to under 10 us on the
blocks where the pass has no geometry. It does not move the KPI, and this section says so rather than implying
otherwise.

## Reproduce

```
scripts/gv-leg.sh r11ab-on-amd  .gv-verify/jars/r11-final world amd 240 <flags> -Dceltium.vulkan.preflushWhenEmpty=true
scripts/gv-leg.sh r11ab-off-amd .gv-verify/jars/r11-final world amd 240 <flags> -Dceltium.vulkan.preflushWhenEmpty=false
```

with `<flags>` = `-Dceltium.vulkan.validation=false -Dceltium.vulkan.presentReadback=false
-Dceltium.vulkan.presentedDumpEvery=0 -Dceltium.validation.guiScale=3 -Dceltium.validation.warmFromTick=400
-Dceltium.validation.warmToTick=600 -Dceltium.validation.holdUntilTick=620
-Dceltium.debug.logLinesPerTag=1000000 -Dceltium.validation.warmSpikeMs=0
-Dceltium.vulkan.tickPercentiles=true -Dceltium.vulkan.tickPercentileFrames=4096`.

The same-tick picture comparison is the two runs' `screenshots/celtium-presented-tick200-*-kept.png`; the
world-picture comparison (`scripts/pack_verify.py picture`) is confounded by the capture tick and is not the A/B.
