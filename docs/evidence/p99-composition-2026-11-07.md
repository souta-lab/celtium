# What the p99 frame is made of (2026-11-07, round 10)

The only unmet goal condition is AMD's p99 every-pair rule (`r9kpi-amd`: p50 0.847, p95 0.861, **p99 1.005**,
`goalCondition=NOT_MET`). Round 9's handoff called the remaining gap "the world-load wave". This round measured the
p99 frame instead of assuming it, and the wave is not what it is.

## 1. The p99 is a tick frame, not a load wave

The fixture splits the warm window's frames by whether a client tick ran since the previous frame
(`frameIntervalWarmTickFrames` / `...PlainFrames`). In every KPI leg the plain frames' p99 is **below** the tick
frames' p50, so the top 1 % of frames can only be tick frames:

| leg | plain p99 (ms) | tick p50 (ms) | tick frames / all |
|---|---|---|---|
| `r9kpi-amd-celtium-r1` | 3.653 | 4.712 | 200 / 4314 |
| `r9kpi-amd-celeritas-r1` | 3.253 | 4.859 | 200 / 3975 |
| `r10wave-amd` (instrumented) | 4.657 | 5.070 | 200 / 3791 |

200 tick frames of ~4 300 is 4.6 %, so the all-frames p99 is the **~78th percentile of the tick frames** - the
instrument says so itself (`p99RankInTick`). The warm window is world ticks 400..600, i.e. *after* the fixture's own
chunk-build window (120..200), so "world-load wave" was the wrong name for it.

## 2. The p99 frame's excess is mostly not the renderer

`r10prof-amd` (jar `48c6ce7d…`, AMD RX 6400, world scene, guiScale 3, validation off, warm ticks [400,600),
`-Dceltium.vulkan.tickPercentiles=true -Dceltium.vulkan.tickPercentileFrames=512`, settled block):

```
frame=1782/1965/2061/1638   (us; tick p50 / tick p99eq / tick p95 / plain p79)
world=1387/1600/1680/1234   entities=494/514/747/420   entBlockEntities=468/487/721/397
terrain=165/176/189/128     flush=218/224/238/236      gui=341/359/365/341
lightmap=37/38/40/0         setupTerrain=31/40/47/29   sky=35/38/39/23
```

The all-frames p99 frame is 183 us above the tick frame's p50 **inside the renderer**. The rest of the p99's excess
over the tick frame's median interval (5.30 - 4.71 = 590 us in `r9kpi-amd`) is the game's own tick and the loop
overhead: the tick is 1.72 ms p50 / 2.01 ms p95 (fixture-measured, both arms), and `CELTIUM_FRAME_TICK_SPLIT` puts
the tick loop's render section only ~245 us above a no-tick loop's. So the p99 frame is roughly **25 % renderer,
40 % the game's client tick, 35 % loop overhead (present, Forge render-tick hooks, the fixture's own handler)**.

That is why the p50 ratio (0.847) and the p99 ratio (1.005) disagree: at the median the frame is mostly renderer and
Celtium wins by 15 %; at the p99 the frame is more than half work neither renderer owns, and a 147 us renderer win
becomes a 3 % frame win.

## 3. The extreme tail is the game's HUD, not the world

The new retention (`-Dceltium.validation.warmSpikeMs=0`, see §5) keeps the warm window's 16 slowest frames with the
renderer's own pass split. Fourteen of the sixteen are **non-tick** frames whose excess is the `gui` bracket, and our
share of it is a tenth:

| worldTick | interval | frame | world | gui | guiBatch | guiFlush | tick |
|---|---|---|---|---|---|---|---|
| 425 | 12 ms | 11.2 | 2.1 | **9.0** | 0.7 | 0.1 | no |
| 467 | 10 ms | 9.9 | 1.4 | **8.4** | 1.0 | 0.1 | no |
| 426 | 7 ms | 6.5 | 1.9 | **4.6** | 0.1 | 0.1 | no |
| 468 | 10 ms | 5.8 | 1.8 | **3.9** | 0.1 | 0.1 | no |
| 510 | 24 ms | 23.6 | **23.1** | 0.4 | 0.1 | 0.1 | no |

The 8-9 ms HUD frames are a mod's overlay inside `GuiIngameForge.renderGameOverlay`; Celtium's accept path and window
are 0.2-1.1 ms of it. The 23 ms world frame is a one-off (no tick, no GUI) and is the only frame of its size in the
window. Both arms pay the HUD; neither is a Celtium regression, and neither is the p99 (they sit above it).

## 4. The structural answer that is not available: the indirect path

`plan.md` has carried "fewer groups is the structural answer" since the group loop reached its floor. The indirect
run path is the way to fewer draws, and its reopening condition ("groups share a geometry arena") has since been met
by `celtium$newImmediateMesh`. Measured this round with the arena in place
(`-Dceltium.vulkan.indirectImmediate=true -Dceltium.vulkan.recordsPositionTwins=true`, leg `r10ind-amd`, same scene
and flags, picture gate PASSED):

| bracket (us, tick/plain p50) | baseline (`r10wave-amd`) | indirect (`r10ind-amd`) |
|---|---|---|
| frame | 3035 / 2743 | **5092 / 4677** |
| flush | 337 / 349 | **1639 / 1611** |
| wrecLoop | 215 / 210 | **1265 / 1261** |
| grpDraw | 118 / 111 | **905 / 899** |

The run path is **2.5x slower**, not faster: a run still closes at almost every group (the key names the descriptor
set, which changes with the texture), and each group pays a record write plus an indirect command on top of the draw
it was already paying. The arena removed the buffer-identity blocker and the path is still a loss, so "fewer groups
via indirect" is closed with data rather than left as an assumption.

## 5. What the round changed

- **`-Dceltium.validation.warmSpikeMs`** (default 16.7) and slowest-N retention for `warmSpikes`. The list used to
  keep the *first* 16 frames over 16.7 ms, which are the world-entry frames - a question about the entry, not about
  the p99. `warmSpikeMs=0` now retains the window's 16 slowest frames whatever their size, with the renderer's pass
  split, the upload path's counters and the loop timings at that frame. This is the instrument §3 was read from.
- **The measured window is resolved once.** `warmFromTick`/`warmToTick` were read with `Integer.getInteger` on every
  frame (and twice per tick) - a `System.getProperty` plus a parse inside the interval being measured, paid by both
  arms on every frame of the window the KPI reads. They are static finals now.
- **`vkResetCommandBuffer` without `VK_COMMAND_BUFFER_RESET_RELEASE_RESOURCES_BIT`** on the frame's own buffer: the
  flag lets the driver free the buffer's internal allocations, and this buffer is reset and re-begun every frame, so
  the release and the re-allocation were both paid every frame. The abort paths keep the flag.
- **The dead atlas fallback removed** (`refreshAtlasMirror` asked `LightmapIdSource.atlasTextureId()` a second time
  when the first answer was `<= 0`, so it could never change the answer).

## 6. What it means for the goal

The p99 needs the renderer's **absolute** cost on a tick frame to fall by ~250 us (14 % of 1 782 us), which moves the
p99 ratio by ~5 % and the p50/p95 ratios with it. The renderer's own work on a tick frame is ~1 030 us: flush 218
(the group loop is at its floor - one push and one draw per group, viewport/scissor/depth-bias already deduped),
terrain 165, the HUD's accept path and window ~110, the offer path ~60, setupTerrain 31, sky 35. No single bracket
holds 250 us, and the two structural answers that would (indirect multi-draw, a sticky pass) are both measured losses.
The next round's candidates, in order of size: the terrain pass's fixed prepare (`recordPrepare` 68-117 us, of which
`prepBarriers` 45 and `prepBegin` 42-54), the flush's window machinery in the *unsettled* blocks (it is ~0 in the
settled ones), and the HUD's window (~68 us).

## Reproduce

```
scripts/gv-leg.sh r10prof-amd .gv-verify/jars/r10-final world amd 240 \
  -Dceltium.vulkan.validation=false -Dceltium.vulkan.presentReadback=false -Dceltium.vulkan.presentedDumpEvery=0 \
  -Dceltium.validation.guiScale=3 -Dceltium.validation.warmFromTick=400 -Dceltium.validation.warmToTick=600 \
  -Dceltium.validation.holdUntilTick=620 -Dceltium.debug.logLinesPerTag=1000000 \
  -Dceltium.validation.warmSpikeMs=0 -Dceltium.vulkan.tickPercentiles=true \
  -Dceltium.vulkan.tickPercentileFrames=512 -Dceltium.debug.frameTail=true
```

`warmSpikes` in `logs/celtium-validation.json` is the p99 frame's profile; `CELTIUM_FRAME_TICK_PERCENTILES` blocks
between the `CELTIUM_WARM_WINDOW begin/end` lines are the window's per-bracket percentiles. The indirect comparison
adds `-Dceltium.vulkan.indirectImmediate=true -Dceltium.vulkan.recordsPositionTwins=true`.
