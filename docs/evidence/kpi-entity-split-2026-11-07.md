# The entity pass, split into its four owners (2026-11-07)

Round 7 of phase 4. Round 6 named `entities` as 54-56 % of the p99 frame's excess and could not say which part of it
that was: the bracket is `RenderGlobal.renderEntities`, and four different owners live inside it - the membership
gather (tick-gated), the per-entity cull, the entity draws, and the block entities that the second injection renders
inside the same bracket. This round bracketed all four and measured them, and the answer is not the one the previous
round's next-step list expected.

## What was added

| switch | what it prints |
|---|---|
| `-Dceltium.vulkan.passSplit=true` (existing) | the new brackets below, in `CELTIUM_FRAME_PASS_SPLIT`, `CELTIUM_FRAME_TICK_PASSES` and `CELTIUM_FRAME_TICK_PERCENTILES` |
| `entGather` / `entGatherWalk` | the membership gather, and the chunk walk inside it; `entGather - entGatherWalk` is the per-pass predicate pass over the gathered membership |
| `entCull` / `entDraw` | the per-entity loop (cull + draw) and the `renderEntityStatic` calls inside it |
| `entBlockEntities` / `entSetTile` | `CeleritasWorldRenderer.renderBlockEntities` and the `setTileEntities` fallback loop |

`EntityGatherer` also changed: the membership is a plain array with a count instead of an `ArrayList` fed through a
`Consumer`, so the walk pays no interface dispatch, no `ArrayList.add` bounds/growth check and no null store per
element on the next `clear()`. The stale tail is nulled only by the amount the membership shrank, so an unloaded
entity is still collectable and a steady-state tick pays no clearing. Behaviour is unchanged: same order, same
predicate, same per-pass lists.

## The split, settled warm window

`r7ent-amd` (renderer `8686c9ae…`, AMD RX 6400, world-accept, guiScale 3, validation off, warm ticks [400,600),
`passSplit` + `tickPercentiles` + `tickPercentileFrames=1024`; `fixtureStatus=PASSED`, validation 0,
`gpuRequirementMet=true`, picture present and inspected). Last percentile block (`tickFrames=50 plainFrames=974
p99RankInTick=40`), microseconds, **tick p50 / tick p99-equivalent / tick p95 / plain p79**:

| bracket | tick p50 | **tick p99eq** | tick p95 | plain p79 | p99eq - plain p79 |
|---|---|---|---|---|---|
| frame | 2 142 | **2 268** | 2 386 | 1 914 | +354 |
| world | 1 732 | 1 831 | 1 944 | 1 482 | +349 |
| worldPass | 1 673 | 1 767 | 1 875 | 1 461 | +306 |
| **entities** | 706 | **756** | 775 | 512 | **+244** |
| **entBlockEntities** | 524 | **548** | 574 | 466 | **+82** |
| **entGather** | 138 | **149** | 160 | 2 | **+147** |
| **entGatherWalk** | 135 | **146** | 157 | 0 | **+146** |
| flush | 295 | 309 | 326 | 290 | +19 |
| terrain | 203 | 225 | 250 | 179 | +46 |
| particles | 50 | 55 | 56 | 41 | +14 |
| lightmap | 48 | 50 | 61 | 0 | +50 |
| sky | 41 | 44 | 46 | 31 | +13 |

`entCull` and `entDraw` are **absent from the report**, which prints a bracket only when its tick p99-equivalent
differs from the plain p79 by 10 us or more: the per-entity loop and the entity draws are the same on both frame
kinds. `entities - entGather - entBlockEntities - entSetTile` leaves ~44 us for the whole loop, on both.

So the settled entity pass is **74 % block entities** (548 of 756 us at the p99 rank) and **20 % the gather walk**
(146 us), and the gather walk is **100 % tick-gated** (146 us on tick frames, 0 on plain). The block entities are
nearly frame-kind-independent here (+82 us), so in the settled window the tick frame's entity excess is mostly the
gather walk.

## The split, world-load wave

The same leg's second mean block (`CELTIUM_FRAME_TICK_PASSES`, `tickFrames=189 plainFrames=3811`, mean us
tick/plain) is the wave, and there the block entities are the whole story:

| bracket | tick | plain | ratio |
|---|---|---|---|
| frame | 4 896 | 1 375 | 3.6x |
| entities | 2 443 | 178 | 13.7x |
| **entBlockEntities** | **2 257** | **156** | **14.5x** |
| entGather | 112 | 1 | 112x |
| entGatherWalk | 109 | 0 | - |
| entSetTile | 51 | 10 | 5.1x |
| offCapture | 29 | 12 | 2.4x |
| offPrepare | 59 | 17 | 3.5x |
| offAppend | 36 | 20 | 1.8x |
| flush | 350 | 221 | 1.6x |
| terrain | 331 | 128 | 2.6x |

**`entBlockEntities` is 2 257 us of a 4 896 us tick frame during the wave (46 %), and 92 % of the entity pass.** The
offer path inside it is only ~124 us (`offCapture` + `offPrepare` + `offAppend`), so the cost is not Celtium's batch
capture: it is the block entities' own `TileEntitySpecialRenderer.render` work, which the wave multiplies because
newly visible sections bring their machines with them. That work is the pack's, and Celeritas pays it too - but it is
where the tick frame's time is, and it is why the p99's `entities` excess is a wave phenomenon rather than a
per-frame constant.

## The wave's p99 is not the entity pass at all

The first percentile block (`tickFrames=81 plainFrames=943 p99RankInTick=71`, the world-load block) puts the p99
frame at 7 817 us against a plain p79 of 2 651, and the excess is **`world` +3 035 and `gui` +1 229**, not
`entities` (+164). Inside `world`: `worldPass` +2 557, of which `lightmap` +442, `flush` +324, `setupTerrain` +161,
`terrain` +108, `sky` +70, `entities` +164. Inside `gui`: `guiBatch` +322 and `guiFlush` +142.

So the two regimes have different owners, and a p99 comparison that does not say which regime it measured is
comparing two different questions:

| regime | p99 frame | biggest excess |
|---|---|---|
| world-load wave (block 1) | 7 817 us | `world` +3 035 (lightmap 442, flush 324, setupTerrain 161), `gui` +1 229 |
| settled (block 11) | 2 268 us | `world` +349 (entities 244: block entities 82, gather walk 146) |

## What this says to do next

1. **The gather walk is the one Celtium-owned item in the settled p99's excess** (146 us of 244). It is 100 %
   tick-gated and it is Celtium's own addition - the reference (`01-celeritas-forge-mc12.2-2.5.0-dev`,
   `EntityGatherer`) walks the chunks on every call, twice a frame, and fills both pass lists in that walk. Celtium
   walks once per tick and re-filters per pass. The walk itself is `celeritas$getLoadedChunks().values()` filtered by
   `celeritas$getHasEntities()` and then 16 `ClassInheritanceMultiMap.forEach` per chunk that has entities. The next
   step is to make the walk proportional to the chunks that *have* entities rather than to the loaded chunks: a
   maintained set of entity-bearing chunks, updated by the same mutation hook that bumps `EntityMembershipEpoch`.
2. **Block entities are 74 % of the settled entity pass and 46 % of a wave tick frame**, and the offer path is only
   ~5 % of that. The cost is the TESRs' own work, so the lever is not the batch path - it is how many block entities
   are rendered and how often. `renderCulledBlockEntities` walks the visible sections every frame; whether the
   per-section block-entity list can be filtered once per tick (the same epoch the gather uses) is the question to
   answer before changing anything.
3. **The wave's p99 is `gui` + `lightmap` + `flush`**, not the entity pass. `lightmap` at 442 us on the wave's p99
   frame is a new name in this analysis and has no owner yet.
4. Every KPI evidence table must state the regime (wave or settled), because the two have different owners and the
   warm window's start decides which one is measured.

## Reproduce

```sh
scripts/gv-leg.sh r7ent-amd .gv-verify/jars/r7-entity world amd 240 \
  -Dceltium.vulkan.validation=false -Dceltium.vulkan.presentReadback=false \
  -Dceltium.vulkan.presentedDumpEvery=0 -Dceltium.validation.warmFromTick=400 \
  -Dceltium.validation.warmToTick=600 -Dceltium.validation.holdUntilTick=620 \
  -Dceltium.validation.guiScale=3 -Dceltium.vulkan.frameStats=true \
  -Dceltium.debug.logLinesPerTag=1000000 -Dceltium.vulkan.passSplit=true \
  -Dceltium.vulkan.tickPercentiles=true -Dceltium.vulkan.tickPercentileFrames=1024 \
  -Dceltium.debug.frameTail=true
```
