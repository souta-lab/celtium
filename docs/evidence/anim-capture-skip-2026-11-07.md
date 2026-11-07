# The animation announcement's second conversion, and where a tick frame's excess actually sits (2026-11-07, round 13)

Round 12 named the client tick's Celtium cost as `animMirror` 30 + `animStub` 28 + `animBatchFlush` 65 = 123 us and
left two contained candidates. This round measured the tick frame's whole bracket profile first, then removed the one
that is pure duplication.

## 1. The tick frame's excess is the light map, and it is common to both arms

`r13prof-amd` (jar `a3de00e6`, AMD RX 6400, world, guiScale 3, validation off, warm ticks [400,600), 240 s,
`fixtureStatus=PASSED`, validation 0), settled block, us, tick-frame p50 / plain-frame p50:

```
frame=1983/1697  world=1423/1172  worldPass=1236/1159  flush=254/216  gui=488/462  hand=91/86
lightmap=173/0  dynUpload=18/0  dynAnnounce=15/0  dynMirror=13/0  lightCache=19/4
winRecord=141/127  wrecLoop=105/97  winBarrier=57/47  grpDraw=65/61  sky=43/33  setupTerrain=33/29
```

The tick frame's renderer bracket is **286 us larger** than a plain frame's, and **173 us of that is `lightmap`** -
`EntityRenderer.updateLightmap`, which runs only on a frame whose tick dirtied it and is the same game code in both
arms. The Celtium-specific part of the difference is `dynUpload` 18 + `dynAnnounce` 15 + `dynMirror` 13 = **46 us**
(the light map's texture upload) plus `lightCache` 19. So a tick frame carries ~236 us of Celtium work in total: 123 us
in the tick (round 12) and ~113 us in the renderer, against a 4.7-4.9 ms interval. **That is the ceiling on what
removing Celtium's tick-frame overhead can buy: 5 %.**

## 2. `animStub` was a second conversion of pixels the announcement already had

`TextureUtil.func_147955_a` is announced at HEAD (`TextureUtilMixin`), and the original body then issues the
`glTexSubImage2D` for the same region. The announcement converts the pixels (`toRgba`) and writes them into the mirror
through `uploadPatchedLevels`; the stub's `texSubImage` then converts the identical pixels again (`uploadToRgba8`, a
fresh `ByteBuffer.allocateDirect` per call) and pastes them into the captured level. The capture exists so that
`fillFromCapture` can establish a mirror that does not exist yet - and a mirror the announcement just updated does
exist.

**The change**: `announceAnimatedUpload` arms a one-shot token (`CeltiumGlStub.celtium$noteAnnouncedPatch`) after an
announcement that applied pixels, and the next `texSubImage` consumes it if the region matches exactly. The token is
cleared by the first `texSubImage` whatever it is, so it cannot leak to a later upload; a refused announcement never
arms one; and a published external image is excluded, because `announceFullPixels` answers `true` for one without
applying anything (`VulkanTextureRegistry.isExternalImage`). The skips are counted as `announcedCaptureSkips` in the
`CELTIUM_VERIFY` census - a saving, not a loss.

The same method's identical-pixel check (OPT-21) compared 1,024 bytes one `ByteBuffer.get` at a time for a 16x16
sprite. It now compares eight bytes a call on absolute addresses, with a byte tail; a row is a whole number of texels,
so the chunks never straddle a row.

## 3. What the confirmation leg shows, and what it cannot

`r13tick-amd` (jar `3409b1b7`, same flags and scene):

```
CELTIUM_TICK_SPLIT ticks=200 window=[400,600) tick[p50=2872 p95=4657]
  anim=202/489/3 animUpload=46/77/16 animMirror=30/52/16 animBatchEnd=86/158/3 animBatchFlush=83/125/3
  animBatchBegin=6/10/1 tickOther=2670
```

`animUpload` fell **72 → 46 us** and `animStub` fell below the 5 us reporting floor (from 27). The run as a whole was
~20 % slower than `r13prof-amd` (`tickOther` 2212 → 2670, tick p50 2412 → 2872), so the absolute tick is not
comparable across the two legs; the Celtium-specific sum is 122 → 113 us, and `animBatchFlush` moved 66 → 83 in the
same direction as the rest of the machine. **The attribution this leg supports is the 26 us drop in `animUpload` with
the same 16 calls per tick, not a tick-frame delta** - the paired set below is the frame-level measurement.

## 4. The paired set: the goal condition is MET on AMD

`r13kpi-amd` (AMD RX 6400, Supersymmetry 0.2.0.2, validation off, warm ticks [400,600), guiScale 3, 240 s, 3 reps,
renderer `25d73c78`, fixture `f15ba461`, reference Celeritas 2.5.0-dev `567dcf8a`, load guard passed and not bypassed:
initial busy 0.165 cores, external 0.164, limits 2.0 / 0.5; all six runs `fixtureStatus=PASSED`, validation 0,
`gpuRequirementMet=true`, `disturbedRepetitions` empty):

| rep | Celtium p50/p95/p99 (ms) | Celeritas p50/p95/p99 (ms) | p50 | p95 | p99 |
|---|---|---|---|---|---|
| 1 | 2.149 / 4.128 / 5.612 | 2.450 / 4.604 / 5.589 | 0.877 | 0.877 | **0.980** |
| 2 | 2.224 / 4.170 / 5.612 | 2.474 / 4.836 / 5.970 | 0.899 | 0.857 | **0.937** |
| 3 | 2.154 / 4.172 / 5.612 | 2.414 / 4.623 / 5.697 | 0.893 | 0.902 | **0.985** |
| **median** | | | **0.893** | **0.877** | **0.980** |

`verdict=NOT_SLOWER`, **`goalCondition=MET`** - every percentile below Celeritas in every clean pair, which is the
first time the goal's performance condition has been met on AMD. The split:

| population | r13 | r12 |
|---|---|---|
| plain p50 / p95 / p99 | 0.887 / 0.909 / 0.815 | 0.885 / 0.898 / 0.884 |
| tick p50 | **0.960** | 0.965 |
| tick p90 | 0.967 | 0.993 |
| tick p95 | 0.930 | 0.996 |
| tick p99 | 0.917 | 0.882 |

**What moved is the spread, not the median.** Celtium's tick-frame p50 is now below Celeritas's in all three
repetitions (4.755 / 4.908 / 4.927 against 4.954 / 5.284 / 5.122; round 12 lost rep 3 at 5.057 against 4.708), and its
tick p90 is 5.698 / 5.787 / 5.721 - a 89 us spread against Celeritas's 443 us (5.669 / 5.982 / 6.112). Round 12's
p99 failure was rep 3, where both arms moved in opposite directions; this set has no such pair, and the p99 ratios are
0.980 / 0.937 / 0.985.

**Attribution, stated as the evidence allows.** The build differs from round 12's by the capture skip and the
long-chunk compare, both of which remove CPU work and native allocation from the tick: `announcedCaptureSkips=16960`
(run 1) / `17041` (run 2) is the whole animated path's capture, and each skipped call also skips one
`ByteBuffer.allocateDirect`. The leg in §3 measured `animUpload` 72 → 46 us with the same 16 calls per tick. **A
plausible mechanism for the narrower tail is that 16 direct-buffer allocations and their Cleaners per tick stopped
feeding the collector, so the pauses that landed in the p99 stopped landing there - that is a hypothesis, not a
measurement**: this round did not instrument GC, and the two paired sets are separate runs on a machine whose load
average differs. What the set does establish is that the build meets the condition with three clean pairs.

## 5. Intel

See the handoff: the Intel paired set on the same stage is the next measurement, because the goal needs both GPUs and
this round's set is AMD only.

## Reproduce

```
scripts/gv-leg.sh r13prof-amd .gv-verify/jars/r12-fix world amd 240 <flags> \
  -Dceltium.vulkan.tickTimings=true -Dceltium.vulkan.tickPercentiles=true \
  -Dceltium.vulkan.tickPercentileFrames=512 -Dceltium.vulkan.passSplitMicros=true -Dceltium.debug.frameTail=true
CELTIUM_KPI_GPU=amd CELTIUM_KPI_LOAD_LIMIT=2 CELTIUM_KPI_JARS=.gv-verify/jars/r13-fix \
  CELTIUM_KPI_REFERENCE=.gv-verify/lib/01-celeritas-forge-mc12.2-2.5.0-dev.jar CELTIUM_KPI_REPS=3 \
  scripts/gv-kpi.sh r13kpi-amd 240 <flags>
```

with `<flags>` = `-Dceltium.vulkan.validation=false -Dceltium.vulkan.presentReadback=false
-Dceltium.vulkan.presentedDumpEvery=0 -Dceltium.validation.guiScale=3 -Dceltium.validation.warmFromTick=400
-Dceltium.validation.warmToTick=600 -Dceltium.validation.holdUntilTick=620 -Dceltium.debug.logLinesPerTag=1000000
-Dceltium.validation.warmSpikeMs=0`.
