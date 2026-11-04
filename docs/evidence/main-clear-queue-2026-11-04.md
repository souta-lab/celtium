# The main framebuffer's clear cannot ride the queue, and the windows come from binds

Round 64. Two questions, one measurement each: whether the main framebuffer's `glClear` can be carried by the draw
queue instead of forcing a window of its own, and whether the frame is slower than round 58's.

## 1. The hypothesis

`flushBeforeClear` exists for one rule (audit S10): what the renderer already accepted has to reach the image before
the clear erases it. For the main framebuffer that rule was implemented by **submitting** the pending groups - a window
of their own - and then letting the frame machinery clear at the next pass begin. Two passes where one would do.

The queue already expresses the order the rule needs. A group carries the clears that arrived after it
(`ImmediateGroup.postClears`), and the window records them as one `vkCmdClearAttachments` between the draws before and
after - the route `celtium$gameClear` has used for **non-main** framebuffers since it was written. Round 61 measured
`offTargetPairClears=[default-pair=0, chain-pair=122285]`: 12 clears a frame already ride that carrier.

So the main clear was given the same carrier, behind `-Dceltium.vulkan.mainClearInQueue`:

- `flushBeforeClear` attaches the clear to the last queued group of the frame image and records the mask it carried;
- `CeltiumGlStub.recordClear` consumes the pending mask when it matches the reservation, so the latch and the window
  acquire do not apply the same clear a second time;
- the values are the ones the clear was issued with (`mainClearValues`), not the live `glClearColor` - the frame
  machinery already learned that lesson when a `framebufferClear` set its own black before clearing another target.

## 2. The measurement that refuted it

Leg `r64c-carry` (AMD RX 6400 RADV NAVI24, renderer `d805d8ba`, fixture `0e90c988`, 150 s, `world`): **PASSED**,
`problems=[]`, `fixtureStatus=PASSED`, validation 0, picture inspected.

| counter | value |
|---|---|
| `clearOrderFlushes` | 1090 |
| `clearOrderNoTargetMask` | 138 |
| `clearOrderSkipped` | 298 |
| **`mainClearsQueued`** | **4** |
| `mainClearRefusals` | `noMask=0 busy=0 foreign=… scissor=0 noTarget=2 rawWrites=0 noGroup=576` |

**Four clears carried out of 1090 clear-order calls.** The dominant refusal is `noGroup=576`: at the moment
`flushBeforeClear` runs, the queue holds **no group of the frame image**, so there is nothing to carry the clear. The
clears that do have pending draws are the few that already flush, and they are a small share of the frame's windows.

The same leg names where the windows actually come from: `flushSiteCounts=[bind-framebuffer=792]`. The flush is
triggered by a **framebuffer bind**, not by a clear. Round 61's reading - "the window's fixed cost is ~5.6 us per
`glClear`" - was a correlation between the clear count and the window count, not the call site; the site is the bind.

So the route is refuted as a source of frame time, and it stays behind its switch with the census that refuted it
(`mainClearsQueued`, `mainClearRefusals`), default **off**. What it cost to learn is one leg; what it would have bought
is four windows a run.

The scoping bug the first leg exposed is fixed in the same change: the first form matched any queued group of the frame
image without asking whether the clear was on the main framebuffer, so a mod's framebuffer clear could have been
recorded against the frame image. `celtium$drawFramebufferIsMain()` now gates it, and `mainClearRefusedForeign` counts
the refusals.

## 3. The regression question

Round 63 measured `medianPairRatio` 1.279 against round 58's 1.175 on the same scene, flags and GPU, and could not say
whether a Celtium change between them cost the frame 13% or whether the two sessions differed. Round 64 answers it with
a **control arm in the same repetition**: `CELTIUM_KPI_CONTROL_JARS` is a stage built from `65946f88a` (round 58), so
`medianControlRatio` = candidate / round 58 measured back to back, which cancels machine drift the way the paired
Celeritas ratio does.

Leg `r64kpi` (AMD RX 6400 RADV NAVI24, 3 repetitions, arms interleaved candidate -> Celeritas -> control, warm window
worldTicks [400,600), metric `frameIntervalWarmP50Ms`):

| rep | candidate | Celeritas | round 58 | candidate/Celeritas | candidate/round 58 |
|---|---|---|---|---|---|
| 1 | 2.742 ms | 2.332 ms | 2.785 ms | 1.176 | **0.985** |
| 2 | 2.869 ms | 2.413 ms | 2.699 ms | 1.189 | **1.063** |
| 3 | 3.620 ms | 2.311 ms | 2.623 ms | 1.566 | 1.380 |
| **median** | | | | **1.189** | **1.063** |

`verdict=SLOWER`, `goalCondition=NOT_MET`, `disturbedRepetitions=[]`, `externalCpuCores=0.078` of a 0.5 limit - but
`finalLoadAverage=3.98` against an initial 0.96, and repetition 3 is where the machine got busy: its paired ratio 1.566
is the same shape as round 63's contaminated repetition 2 (1.470). **On the two clean repetitions the candidate is
within -1.5% / +6.3% of round 58's build, and the paired Celeritas ratio is 1.176 / 1.189 against round 58's 1.175.**

So the round-63 reading - "Celtium alone regressed 13.1% between round 58 and round 63" - does not survive a control
arm. The two builds are the same speed to within the measurement's spread, and the 1.279 was a session in which one
repetition ran on a loaded machine. The per-window figure that looked like the regression's fingerprint
(`windowRecordMillis`/window 18.46 -> 20.94 us) is a denominator artefact: the window count fell 237481 -> 218069 while
the groups those windows carried did not fall by the same share, so the average rose without any window getting slower.

**What this does not say.** It does not say the frame is fast enough: 1.189 against Celeritas is `NOT_MET`, and the
remaining gap is the immediate path's accept and window cost, which rounds 59-63 measured and did not move. It also does
not say round 58's build is faster - the control arm exists to price a delta, not to pick a winner.

All nine legs: `fixtureStatus=PASSED`, `validationMessageCount=0`, `gpuRequirementMet=true`, `problems=[]`.

## 4. What the counters say about the round-58 delta

The two legs' `CELTIUM_VERIFY` lines, read against each other, say the current build does **less** work per frame than
round 58's, not more:

| counter | round 58 | round 63 |
|---|---|---|
| `immediateWindows` / frame | 23.5 | 21.4 |
| `actualFlushes` / frame | 22.5 | 20.4 |
| `immediateOffered` / frame | 167.6 | 163.5 |
| `windowRecordMillis` / window | 18.46 us | 20.94 us |

The per-window figure rose while the window count fell, and the two are the same measurement divided differently: a
window that carries more groups costs more. `windowRecords` fell 237481 -> 218069 while the group draws it carried did
not fall by the same share, so the per-window average is a denominator artefact, not a slowdown. That is an argument,
not a measurement - the control arm above is the measurement.
