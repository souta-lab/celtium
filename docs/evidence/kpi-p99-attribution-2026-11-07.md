# The p99's owner, by percentile (2026-11-07)

Round 6 of phase 4. The KPI's only unmet condition is the p99, and every previous attempt to name its owner read
**means** of the tick frames. A mean over ~200 tick frames is dominated by the handful of frames that hit a one-off
event - the shared index buffer's single regeneration read `terrainSharedIndex=75 us` a frame in one block, which is
one 14.6 ms frame, not a per-tick cost - and the evidence history has read those means as per-tick costs more than
once. This round added a percentile form of the same split and used it.

## What was added

| switch | what it prints |
|---|---|
| `-Dceltium.vulkan.tickPercentiles=true` | `CELTIUM_FRAME_TICK_PERCENTILES`: per bracket, the tick frames' p50, **the tick percentile that is the all-frames p99**, and the tick p95, beside the plain frames' p79. Keeps every frame's own values (4 096 frames x 96 brackets x 8 B = ~3 MB, allocated only when the switch is on). |
| `-Dceltium.vulkan.tickPercentileFrames=N` | the block size (default 4 096; 1 024 puts several blocks inside the 10 s warm window). |
| `deferredUploads=[batches= outputs= drains= drained= maxQueued= maxDrain=]` | whether the chunk-upload budget was ever consulted: an arrival over `uploadBurstThreshold` (32) is what engages it. |

The percentile report is the answer to "which bracket owns the p99" without averaging outliers: 200 of ~4 200 warm
frames carry a tick, so the 99th percentile of all frames is the ~79th percentile of the tick frames, and the report
computes that rank from the block's own tick/plain counts (`p99RankInTick`).

## The p99 frame's composition

`r6budget-amd` (renderer `f2158a87…`, AMD RX 6400, world-accept, guiScale 3, validation off, warm ticks [400,600),
`passSplit` + `tickPercentiles` + `frameTail` + `uploadBudget=64`; `fixtureStatus=PASSED`, validation 0). Second
percentile block (frames 4 097-8 192, `tickFrames=196 plainFrames=3900 p99RankInTick=155`), microseconds, columns
**tick p50 / tick p99-equivalent / tick p95 / plain p79**:

| bracket | tick p50 | **tick p99eq** | tick p95 | plain p79 | p99eq - plain p79 |
|---|---|---|---|---|---|
| frame | 1 600 | **3 858** | 11 644 | 1 819 | +2 039 |
| world | 1 170 | 3 147 | 9 610 | 1 289 | +1 858 |
| worldPass | 1 096 | 3 053 | 9 517 | 1 274 | +1 779 |
| **entities** | 117 | **1 145** | 2 345 | 48 | **+1 097** |
| gui | 415 | 586 | 1 158 | 436 | +150 |
| flush | 218 | 478 | 800 | 270 | +208 |
| terrain | 115 | 421 | 1 012 | 119 | +302 |
| particles | 1 | 112 | 319 | 1 | +111 |
| setupTerrain | 56 | 93 | 1 602 | 53 | +40 |
| lightmap | 59 | 64 | 82 | 0 | +64 |
| sky | 45 | 60 | 98 | 32 | +28 |
| hand | 65 | 84 | 135 | 61 | +23 |

The named top-level brackets account for the frame's excess: world +1 858, gui +150, hand +23 = +2 031 against the
frame's +2 039. Inside the world pass, **`entities` is 54 % of the p99 frame's excess** (1 145 us of 2 039), then
`terrain` (302), `flush` (208), `particles` (111), `lightmap` (64).

`entities` is `RenderGlobal.renderEntities`, called twice a frame (render pass 0 and 1). Its distribution is bimodal:
117 us at the tick p50, 1 145 us at the p99 rank, 2 345 us at the tick p95 - a 20x spread that a per-frame constant
(the render-info traversal) cannot produce. The plain frames never see it (p79 48 us).

## The same split in the settled warm window, and the two regimes

`r6final-amd` (renderer `4cee51d8…`, the round's final build, same flags with `tickPercentileFrames=1024` so several
blocks land inside the 10 s warm window; `fixtureStatus=PASSED`, validation 0, picture present). The last blocks are
the warm window; the mean split (`CELTIUM_FRAME_TICK_PASSES`, tick/plain us) reads:

| block | frame | entities | terrain | flush | gui | lightmap |
|---|---|---|---|---|---|---|
| world load (first block) | 5 756 / 1 337 | **2 651 / 180** | 743 / 117 | 367 / 208 | 648 / 387 | 66 / 0 |
| settled (last block) | 2 227 / 1 790 | **707 / 462** | 234 / 174 | 307 / 274 | 408 / 376 | 47 / 0 |

So the tick frame's render excess is +4 419 us during the world load and +437 us once settled, and **`entities` is
56 % of it in both regimes** (2 471 of 4 419; 245 of 437). The percentile blocks agree: in the settled blocks the
tick p99-equivalent frame is 1 928-3 034 us against a plain p79 of 1 628-2 940 us, and `entities` is 615-1 057 us at
the tick p50 there - the entity pass is expensive for both frame kinds once the world is loaded, and the tick frame's
extra is the difference, not a 20x spike.

That is also the answer to the run-to-run p99 spread the KPI has shown all along (±6-12 % per pair): the warm window
starts ~20-30 s after the join, so **how much of the chunk-load wave is still inside it decides whether the p99 is the
wave's tail (entities 1 145 us against a plain p79 of 48) or the settled difference (entities 707 against 462)**. The
same build can read either, which is why a single paired set cannot settle the every-pair condition.

## Where the tick frame's time is, by loop part

`CELTIUM_FRAME_TICK_SPLIT` (same leg, settled block, mean us, slowest tenth of tick loops / all tick loops / loops
without a tick):

| part | tick loops | loops without a tick |
|---|---|---|
| loop | 2 982 | 1 505 |
| tick | 1 185 | 0 |
| render | 1 216 | 987 |
| display | 113 | 94 |
| fmlTick (Forge render-tick hook) | 350 | 331 |

So a tick loop's +1 477 us over a plain loop is **80 % the client tick itself** (1 185 us), 15 % the render (+229 us)
and the rest display/hook. The client tick is equal between the arms (KPI r130: 1.70 against 1.69 ms), which is why
the tick frame's p50 is level and only its upper-middle separates: the renderer's share of a tick frame is small, so
Celtium's 12-18 % renderer lead on plain frames compresses toward 1 on tick frames.

## The chunk-upload budget is not reachable in this scene (negative, with a positive control)

`-Dceltium.vulkan.uploadBudget=64` was set on the whole leg and the counters read
`deferredUploads=[batches=0 outputs=0 drains=0 drained=0 maxQueued=0 maxDrain=0]` at run end: **no `uploadMeshes`
arrival exceeded the 32-output burst threshold**, so the budget path was never entered. The counters are the positive
control - they are printed, they were consulted, and they are zero - so "the budget changed nothing" here means "it
was never asked", not "it does not help". The budget's own re-entry condition (a burst inflating the distribution)
belongs to a scene that produces bursts; the KPI scene does not.

## What this says to do next

1. **`entities` (54-56 % of the p99 excess in both regimes)** is the target, and it needs a *comparative* measurement
   before a change: Celtium's bracket says where the time is, not whether Celeritas pays it. The next discriminator is
   a warm-window profile of both arms restricted to the tick frames (the 2026-11-05 profiles were whole-window and put
   the extra time in the mod HUD, not the entity pass).
2. **The warm window's start decides the regime.** A p99 comparison that is meant to be reproducible has to say which
   regime it measured; the fixture's warm window begins ~20-30 s after the join, inside the chunk-load wave's tail.
   Either the wave is made cheaper (it is the game's own chunk and entity work) or the measurement is taken after it.
3. **`flush` +208 us** is the immediate window recording with more groups on the p99 frame (`wrecLoop` 259 against
   119 us, `grpDraw` 116 against 76): ~2.2x the groups at ~1.1 us each. The per-group cost is the lever, and the
   indirect multi-draw path is still its structural answer.
4. The percentile report is now the instrument for this question; the mean form (`CELTIUM_FRAME_TICK_PASSES`) stays
   for continuity but should not be read as a per-tick cost.

## Reproduce

```sh
scripts/gv-leg.sh r6budget-amd .gv-verify/jars/r6-budget world amd 240 \
  -Dceltium.vulkan.validation=false -Dceltium.vulkan.presentReadback=false \
  -Dceltium.vulkan.presentedDumpEvery=0 -Dceltium.validation.warmFromTick=400 \
  -Dceltium.validation.warmToTick=600 -Dceltium.validation.holdUntilTick=620 \
  -Dceltium.validation.guiScale=3 -Dceltium.vulkan.frameStats=true \
  -Dceltium.debug.logLinesPerTag=1000000 -Dceltium.vulkan.passSplit=true \
  -Dceltium.vulkan.tickPercentiles=true -Dceltium.debug.frameTail=true \
  -Dceltium.vulkan.uploadBudget=64
```
