# the accept path's mean is 1.6x its median, and 55% of offers take the slow path (2026-11-03)

Round 38 left two numbers to chase: `offerStages[s10]` (the merge decision) at 1,043 ns per reached offer, and
`parts[supply]` at 2,560 ns. Both are **means**, and the round-38 handoff itself warned that a mean over a short
interval is dominated by rare expensive events. This round added the distribution those means came from, and the
distribution says the means are usable - but it also says the population they are taken over is not the one the
handoff assumed.

## The instrument

`batchDist` is a log2 histogram of the accept path's per-offer duration, filled in `submitImmediateBatchTimed`'s
`finally` under `-Dceltium.vulkan.batchTimings=true` (one array increment per offer). Bucket `b` holds
`[2^(b-1), 2^b)` nanoseconds, so each percentile is an exact **upper bound** rather than an interpolation:
`p50<=2048` with `max=4096` says the microsecond is real work, `p50<=2048` with `max=173698761` says the mean is a
tail. `BatchDurationDistributionTest` pins the bucket mapping, the upper-bound rule, that a 2% tail does not move the
median, and that the 2^63 bucket saturates instead of going negative.

`acceptSpanSummary` also prints `partCalls[...]` now: the sample count each `parts[...]` mean was taken over. Without
it a part that runs on the slow path only and one that runs on every offer print the same shape, and a leg-to-leg
comparison of the two means reads as a regression when it is a different population - which is exactly how round 38
read `parts[supply]` 2,560 against r34c's 1,792.

## The legs

| | r39a-dist | r39b-merge |
|---|---|---|
| renderer | `726b15ac…` (histogram + `partCalls`) | `814da57a…` (+ the freshness gate) |
| fixture | `0e90c988…` | `0e90c988…` |
| flags | validation/readback off, `presentedDumpEvery=0`, `batchTimings`, `frameStats`, warm window 400-600 | same |
| verdict / problems | PASSED / `[]` | PASSED / `[]` |
| `sceneCalibrationKeyMatches` | true | true |
| `presentedImageVerdict` / failures | PASSED / 0 | PASSED / 0 |
| `validationMessageCount` | 0 | 0 |
| distinct / nonBg / bands / black | 76,878 / .990197 / 720 / .007374 | 76,703 / .989693 / 720 / .006560 |
| `frameIntervalWarmP50Ms` | 3.209 (2,801 samples) | 3.379 (2,644 samples) |
| `worldFrames` | 5,047 | 4,923 |

The two pictures agree to 0.2% on distinct colours and 0.05% on the non-background fraction, so the change moved no
pixel.

## What the distribution says

```
batchDist[p50<=2048 p95<=8192 p99<=16384 max=173698761 n=378563]   r39a
batchDist[p50<=2048 p95<=8192 p99<=16384 max=154823926 n=379841]   r39b
batchTimings[nanosPerOffer total=3235 …]                            r39a
batchTimings[nanosPerOffer total=3199 …]                            r39b
```

* The median offer is **at most 2 us** and the mean is **3.2 us**: the mean is 1.6x the median, not 15x. So the
  accept path's mean is a usable summary here and the round-38 worry - that these means are preemption artifacts -
  is **refuted for this scene**. (The stage-8 interval remains unexplained: its body is a stride compare, two format
  compares and a null check, and it reads 755 ns. That is a property of the *stage marks*, which are 17 ns clocks
  taken 14 times an offer, not of the accept path.)
* The tail is one event, not a population: `max` is 154-174 ms, which alone contributes ~450 ns to a mean over
  378,000 samples. `p99<=16384` bounds the rest.
* `n` is the count at the last `CELTIUM_FRAME_STATS` print, not the run total - the line prints a few times a run.

## What the population actually is

`partCalls` and the verification line's `fastMergedBatches` correct two assumptions the handoff carried:

| | value | what the handoff assumed |
|---|---|---|
| `fastMergedBatches` / `immediateOffered` | 894,800 / 2,005,142 = **44.6%** (r39b: 871,503 / 1,949,089 = 44.7%) | "most of them fast-merged" |
| `partCalls[supply]` / `partCalls[registry]` | 193,276 / 378,563 = **51%** | "the supply bracket runs ~27,000 times a run" |

So **55% of offers take the slow path** - the supply, the preflight and the group open - and the accept path's cost
is dominated by that half, not by the merge decision. The 27,000 figure was the *inner* `celtium$supplyMirror` call
count (empty 26,542 + upload 97 + deferred 2), not the number of offers that reach the supply region.

## The change, and the hypothesis it refutes

The fast merge's last term was `CeltiumGlStub.textureWriteEpoch() == slowPathWriteEpoch`, and that epoch is
**global**: every texture write anywhere moves it. The question the merge asks is whether *the group's own* texture
was written since its mirror was taken, so the term was replaced by `celtium$groupTextureUnchanged(boundTexture)` -
the global epoch as the cheap first gate, and the group's own `textureUploadSerial` when it moved. The group's serial
is captured where its mirror is resolved (`currentGroupTextureSerial`), and `GroupTextureFreshnessTest` pins the
five-case table.

**It did not raise the merge rate**: 44.63% -> 44.71%, i.e. unchanged. The gate is the last term of the `&&` chain,
so it only runs when every other term already passed - and for those offers the global epoch was already unchanged.
The over-conservatism is real in principle and the change is kept because it now asks the right question, but **no
speed is claimed and the merge rate is not the lever this round found**. The next round should read the refusal
census (`-Dceltium.vulkan.mergeCensus=true`, `mergeRefusalSummary`) to learn which term actually refuses 55% of
offers, rather than guessing at the gate.

## The environment fact this round established

`ps -eo pid,pcpu,etime,comm` shows **`ly` (PID 610, root) at 92.0% of a core, elapsed 1 day 13 hours**. That is the
"external core 0.9" every leg's `externalCpuCores` has reported and the reason the paired KPI keeps returning
`NO_SPEED_VERDICT` against its 0.5-1.0 limit. It is a root-owned system service and cannot be stopped from this
session, so every wall-clock bracket in this project is measured on a host with one core permanently busy. The paired
design cancels it between arms; a single arm's absolute milliseconds do not.

## What this round does not prove

- That any accept-path part is cheap or expensive in isolation. The distribution is of the whole offer, and the
  parts are still means.
- That the freshness gate is safe in a scene where a raw write to one texture is pending while another texture's
  batch merges. The argument is that the pending copy is flushed before the texture it belongs to is sampled, and
  that the merge does not sample it - but no leg exercised that ordering.
- Anything about the 55% slow path beyond its size. The refusal reasons were not measured.
