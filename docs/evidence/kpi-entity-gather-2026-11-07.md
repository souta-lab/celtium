# The entity gather walk: from every loaded chunk to the chunks that have entities (2026-11-07)

Round 8 of phase 4. Round 7 split the entity pass and named the gather walk as the one Celtium-owned item in the
settled p99 frame's excess: 146 us of a 2 268 us tick frame on the fixture scene, 100 % tick-gated, and Celtium's own
addition (the reference walks every chunk twice a frame). This round removed it, and the measurement says the walk was
never about the entities.

## What the walk actually cost

The walk visited every loaded chunk and, for each chunk the sticky `hasEntities` flag admitted, iterated all sixteen
`ClassInheritanceMultiMap`s. `hasEntities` is set by `Chunk.addEntity` and **never cleared by the original code**
(`setHasEntities` has no caller in the vanilla or Forge sources), so after a few minutes of play it admits nearly every
loaded chunk.

The KPI scene is the Supersymmetry world (`qmagnet`, render distance 18, **one loaded entity** -
`captureLoadedEntities=1`). On `r8kpi-before` (renderer `8686c9ae…`, AMD RX 6400, world scene, guiScale 3, validation
off, warm ticks [400,600), `fixtureStatus=PASSED`, `validations=0`, picture present) the settled block reads, in
microseconds, **tick p50 / tick p99-equivalent / tick p95 / plain p79**:

| bracket | tick p50 | tick p99eq | tick p95 | plain p79 |
|---|---|---|---|---|
| frame | 1 920 | **1 998** | 2 060 | 1 650 |
| world | 1 545 | 1 615 | 1 680 | 1 262 |
| entities | 641 | 656 | 678 | 435 |
| **entGather** | 113 | **119** | 127 | 0 |
| **entGatherWalk** | 112 | **118** | 126 | 0 |
| entBlockEntities | 505 | 516 | 529 | 411 |
| flush | 274 | 284 | 296 | 267 |
| terrain | 167 | 178 | 187 | 135 |
| lightmap | 36 | 38 | 41 | 0 |
| sky | 29 | 31 | 33 | 18 |

So the walk was **118 us of a 1 998 us tick frame (5.9 %) with one entity in the world**, and the whole membership
predicate pass (`entGather - entGatherWalk`) was 1 us. The walk was not proportional to the entities at all.

## The per-chunk cache alone did not move it

The first attempt (stage `r8-gather`) gave each chunk its own gathered array, rebuilt only when one of the four
original `Chunk` methods changed that chunk's membership, so the walk became one array copy per chunk instead of
sixteen section iterators. It read **166 us against 112 us** on a leg whose whole frame was 11 % slower, so the honest
reading is that it did not move the walk: the cost is *visiting 1 281 chunk objects*, not iterating their section maps.
That negative is what pointed at the index below. (The leg was deleted with the rest of the set; the number is from the
run's own percentile block.)

## The fix: an index of the chunks that have entities

`EntityBearingChunks` is a per-world `ConcurrentHashMap` key set of the chunks that currently hold at least one entity.
The same four original `Chunk` methods keep it current, and the count that decides membership is **recomputed from the
section maps** rather than tracked with a counter - `removeEntity` delegates to `removeEntityAtIndex`, so a counter
would be decremented twice, and a recomputation cannot drift. `onUnload` removes the chunk unconditionally, because it
does not clear the section maps (`World.unloadEntities` only removes the entities from the world's own lists).

The set is concurrent because the entity tracker mutates it from its own thread while the render thread iterates it; a
weakly consistent iterator is the contract wanted (a chunk added during a gather is picked up by the next one). The
per-chunk cache stays: the index says *which* chunks, the cache says *what* they hold.

## The result

`r8kpi-index` (renderer `7539e68c…`, same scene, same flags, same GPU, back to back with the leg above):

| bracket | tick p50 | tick p99eq | tick p95 | plain p79 |
|---|---|---|---|---|
| frame | 1 841 | **1 911** | 1 996 | 1 701 |
| entities | 538 | 555 | 577 | 452 |
| **entGatherWalk** | 8 | **10** | 12 | 0 |
| entBlockEntities | 504 | 521 | 533 | 420 |
| flush | 276 | 294 | 308 | 271 |
| terrain | 177 | 194 | 210 | 147 |
| lightmap | 39 | 42 | 45 | 0 |

**`entGatherWalk` 118 → 10 us (-92 %)**, and the tick frame at the p99 rank 1 998 → 1 911 us (-87 us). The census is
the positive control for the index:

```
CELTIUM_ENTITY_GATHER gathers=400 chunks=135 entities=428 rebuilds=22 auditedChunks=135 auditMismatches=0
  auditedIndexes=8 indexMismatches=0
```

135 chunk visits over 200 gathers (0.7 per gather) against 1 281 per gather before; 22 rebuilds; and the audit - which
re-walks every loaded chunk every fiftieth gather and compares the count of chunks with entities - agreed 8 times out
of 8. The picture is unchanged: `r8kpi-index` against `r8kpi-before` is **0.042 mean abs diff / 0.061 % of pixels over
24 levels**, which is the entity and particle positions plus the FPS counter.

The frame interval moved less than the render bracket: `frameIntervalWarmP99Ms` 5.918 → 5.864 (-54 us) and
`frameIntervalWarmTickFrameP50Ms` 5.102 → 5.035. A single leg cannot separate that from the run-to-run spread; the
paired set is what decides.

## Where the tick frame's time actually is

`r8kpi-tail` (same renderer, `-Dceltium.debug.frameTail=true`) splits the loop itself. Settled block, microseconds,
**slowest tenth of tick loops / all tick loops / loops without a tick**:

| part | slowest tenth | all tick loops | loops without a tick |
|---|---|---|---|
| loop | 5 714 | **4 866** | 2 143 |
| tick | 2 580 | **2 300** | 0 |
| render | 2 458 | **2 032** | 1 661 |
| display | 106 | 91 | 72 |
| fmlTick | 467 | 344 | 307 |

A tick loop's excess over a loop without a tick is **2 723 us, of which 2 300 us (84 %) is the client tick itself** and
371 us is the renderer. The client tick is equal between the arms (KPI r130: 1.70 against 1.69 ms), so the renderer's
tick-frame excess is the only part a renderer change can move - and after this round it is 210 us at the p99 rank,
spread over block entities (101), terrain (47), lightmap (42) and flush (23).

That is the number to keep in view: **the p99 is a tick frame whose excess is 84 % simulation**, so a renderer change
that removes 118 us of it is worth ~2 % of the p99 frame, not the 5.9 % the bracket's share suggests.

## The paired set

Official paired set on the same build, both GPUs, 3 repetitions each, Supersymmetry 0.2.0.2, validation off, warm
ticks [400,600), `guiScale=3`, 240 s (`r8kpi-amd`, `r8kpi-intel`). Renderer `7539e68c…`, fixture `97685d8d…`,
reference Celeritas 2.5.0-dev `567dcf8a…`. Neither run bypassed the load guard (AMD initial busy 0.142 cores,
external 0.232; Intel 0.165 / 0.168; limits 2.0 / 0.5).

| GPU | p50 | p95 | p99 | per-pair p99 | verdict | goalCondition |
|---|---|---|---|---|---|---|
| AMD (`r8kpi-amd`) | **0.944** | **0.929** | **0.979** | 0.979 / 0.935 / 1.058 | NOT_SLOWER | **NOT_MET** |
| Intel (`r8kpi-intel`) | **0.889** | **0.944** | **0.914** | 0.995 / 0.868 / 0.914 | NOT_SLOWER | **MET** |

Absolute warm-window frame intervals (ms):

| GPU | rep | Celtium p50/p95/p99 | Celeritas p50/p95/p99 |
|---|---|---|---|
| AMD | 1 | 2.090 / 4.170 / 5.443 | 2.430 / 4.633 / 5.559 |
| AMD | 2 | 2.494 / 4.601 / 5.786 | 2.546 / 4.950 / 6.191 |
| AMD | 3 | 2.378 / 4.786 / 6.150 | 2.520 / 4.722 / 5.814 |
| Intel | 1 | 3.385 / 5.953 / 6.999 | 3.692 / 6.159 / 7.037 |
| Intel | 2 | 3.408 / 5.963 / 6.970 | 3.833 / 6.483 / 8.031 |
| Intel | 3 | 3.184 / 6.042 / 7.141 | 3.751 / 6.403 / 7.817 |

Against r130 (`c219ed9a`, before this round): AMD p50 0.869 → 0.944, p95 0.881 → 0.929, **p99 1.034 → 0.979**;
Intel p50 0.898 → 0.889, p95 0.983 → 0.944, **p99 0.985 → 0.914**. Intel now passes the every-pair condition on all
three percentiles. AMD's p50 passes on all three pairs; its p95 and p99 fail on pair 3 only, where Celtium's own p99
spread (5.443 / 5.786 / 6.150, 13 %) met Celeritas's best run (5.814) - the pairing, not a per-frame regression.

## What this says to do next

1. **AMD's p99 needs ~3 more points of margin.** The remaining tick-gated renderer excess is 210 us at the p99 rank
   (block entities 101, terrain 47, lightmap 42, flush 23), and the arithmetic above says ~150 us of it would take the
   median ratio to ~0.95 - enough for the every-pair condition to survive a 13 % pairing spread.
2. **Block entities are the largest remaining tick-gated render item** (101 us at the p99 rank, 521 us total). The
   offer path is ~5 % of it, so the question is how many block entities are rendered and how often, not the batch path.
3. **`lightmap` 42 us and `terrain` 47 us are tick-gated** and both are the game's own work; they need a comparative
   measurement against Celeritas before they are called Celtium's.
4. **The p99's spread is the wave.** The warm window starts ~20 s after the join and the chunk-load wave's tail decides
   whether the p99 is a wave frame or a settled one; that is why one pair can read 1.058 while the median reads 0.979.

## Reproduce

```sh
scripts/gv-leg.sh r8kpi-index .gv-verify/jars/r8-index world amd 240 \
  -Dceltium.vulkan.validation=false -Dceltium.vulkan.presentReadback=false \
  -Dceltium.vulkan.presentedDumpEvery=0 -Dceltium.validation.warmFromTick=400 \
  -Dceltium.validation.warmToTick=600 -Dceltium.validation.holdUntilTick=620 \
  -Dceltium.validation.guiScale=3 -Dceltium.vulkan.frameStats=true \
  -Dceltium.debug.logLinesPerTag=1000000 -Dceltium.vulkan.passSplit=true \
  -Dceltium.vulkan.tickPercentiles=true -Dceltium.vulkan.tickPercentileFrames=1024 \
  -Dceltium.vulkan.entityGatherCensus=true -Dceltium.vulkan.entityCacheAudit=true
```

The before leg is the same command with `.gv-verify/jars/r7-entity` and without the two census switches. The loop
split adds `-Dceltium.debug.frameTail=true`.
