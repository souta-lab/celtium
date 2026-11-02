# the paired KPI on the real-world profile: 1.68x behind Celeritas, and the gap is record+batch (2026-11-03)

Completion condition C ("faster than Celeritas, proved by a paired measurement on the same JAR and the same
conditions") had no measurement on the current renderer. The AMD `NOT_SLOWER` verdict in
[plan.md](../plan.md) is from the `world-0.7.11` instance and an older JAR. This round took the paired set on the
profile the project calls its standard real-world one - `celtium-world-accept`, 188 mods, QMAGNET's `qmagnet` save -
and the answer is not the old one: **Celtium's warm p50 is 1.66-1.76x Celeritas's in all three pairs.**

| | |
|---|---|
| Renderer | `bc425402fb3be60abc28e059fa327e5b6aa96ee04747b396aff4d2bd3ed387c7` - **the committed tree rebuilds this byte for byte** (checked after the commit: the same `scripts/build.sh` invocation on the committed tree produced the same sha256, so the JAR is reproducible and the measurement is of the committed source, not of a nearby one) |
| Fixture | `6e53864ba7434e85f8b59fde7076439463defb33813212f26575b97cc78ef823` |
| Reference | Celeritas `01-celeritas-forge-mc12.2-2.5.0-dev.jar`, sha256 `567dcf8aadd6f4f4d5d594b02549c7997385b7dc8ade5e9722d1e9756056092c` |
| GPU | AMD Radeon RX 6400 (RADV NAVI24), `gpuRequirementMet=true` on every leg |
| Scene | `scene=world`, save `qmagnet`, render distance 18, camera `217.5/48.0/217.5 yaw 180 pitch 10`, 1280x720, `guiScale=3`, clouds 0, pinned world time 6000, framerate limit 260, vsync off - **identical `sceneCalibrationKey` on all six legs** |
| Window | `frameIntervalWarmP50Ms`, world ticks `[400,600)`, `worldTicksAtEnd=619` on every leg, samples 2145-3967 |
| Flags (shared) | `-Dceltium.vulkan.validation=false -Dceltium.vulkan.presentReadback=false -Dceltium.vulkan.presentedDumpEvery=0` |
| Run | `scripts/gv-kpi.sh r35kpi 240`, `CELTIUM_KPI_REPS=3`, `CELTIUM_LEG_SEED_OPTIONS=…/guiScale3.txt` |

## The pairs

| rep | Celtium p50 / p95 / p99 (ms) | Celeritas p50 / p95 / p99 (ms) | p50 ratio |
|---|---|---|---|
| 1 | 3.944 / 6.731 / 8.438 | 2.352 / 4.430 / 5.370 | 1.677 |
| 2 | 4.033 / 6.935 / 8.664 | 2.288 / 4.395 / 5.422 | 1.763 |
| 3 | 4.124 / 7.396 / 9.176 | 2.484 / 4.660 / 5.477 | 1.660 |
| **median** | | | **1.677** |

`goalCondition=NOT_MET`, and the p95/p99 medians are 1.578/1.598 - the loss is not a tail: it is the whole
distribution. The verdict field itself reads `NO_SPEED_VERDICT`, and that is **not** a disagreement between the arms:
all three pairs measured the same thing. It is the runner's external-load rule - 1.07 cores were busy outside the
legs during the run (this session) against the 1.0 limit recorded in the result (`environment.externalLimit=1.0`,
`externalCpuCores=1.073`). The paired, order-alternating design cancels that between arms, so the **ratio** is
reported here as the measurement and the absolute milliseconds are not. No repetition was dropped as starved
(`disturbedRepetitions=[]`).

## Why this does not contradict the `NOT_SLOWER` row, and what that row has to say now

The old result is a different instance: `world-0.7.11`, no Xaero, one pair per repetition at 0.63 vs 1.06 ms. This
one is the 188-mod profile the product is actually shipped against. Both can be true at once, and the plan's row was
written as if the first settled the condition. It did not; it settled it for one scene. `plan.md` now says so.

The difference between the two scenes is not attributed this round. Xaero's minimap alone does per-frame framebuffer
and tile work that the `world-0.7.11` scene does not have, so "the pack's GUI/HUD cost" and "a regression since the
old JAR" are both still open, and neither is claimed.

## Where the frame goes: `record` and `batch`, not the GPU

One Celtium arm of the same scene, same flags plus `-Dceltium.vulkan.frameBudget=true` (leg `r35a-budget`, PASSED,
exit 0, `problems=[]`, `gpuRequirementMet=true`, validation off by the flag above - so this leg is not a validation
arm and claims none). Steady-state line, 60-frame window:

```
CELTIUM_FRAME_BUDGET frames=960 window=60 ms[frame=3.0/4.4/5.0 record=0.5/0.6/0.6 batch=0.8/0.9/1.1
  submit=0.0/0.0/0.0 fence=0.0/0.0/0.0 slot=0.0/0.0/0.0 present=0.2/0.4/0.5 gpu=0.0/0.0/0.1 other=1.5/2.9/3.2]
  gpuShare=0.01
```

* `gpu=0.0` and `gpuShare=0.01`: the frame is **CPU-bound**, so this is not a submission/sync problem.
* The three Celtium-owned brackets sum to **1.5-1.9 ms** (`record` 0.6 + `batch` 0.9 + `present` 0.4). The gap to
  Celeritas' 2.35 ms p50 is ~1.6 ms. So the recorded immediate work is, on its own, the size of the whole gap.
* `record` is `recordImmediateOnlyPass` - the command recording of one immediate window (`addRecord` at
  `VulkanTerrainGpuBackend:10028`); `batch` is the end-to-end accept path of one offer (`addBatch` at `:7107`,
  summed by `submitImmediateBatch`'s own duration). Both are per-frame functions of the offer and window counts.
* `other` is everything the brackets do not cover (vanilla tick, world update, the game's own code). It is shared
  with the reference arm and is *not* the part to attack.

So the next performance unit is named by this split rather than guessed: fewer or cheaper **windows** (51 flush
entries per frame on this scene - `windowRecords=372199`, `actualFlushes=372120` over the leg) and a cheaper
**per-offer accept path** (`prepareImmediateCalls=970019`, 133 offers per frame). Both numbers are from the same
leg's verification line.

## The code change this round

`unflushedTargetGroupsDropped` had two writers with different units and only one recorded the target, so the
round-14 world leg could report `unflushedTargetGroupsDropped=98` beside `unflushedTargetDropTargets=[none]`: a
count with no image, in two units at once (groups at the menu boundary, batches at the world boundary). Both
boundaries now go through `celtium$dropUnflushedTargetGroups(site)`, which counts batches, records
`target:frames:batches` per identity, and writes a bounded record line naming the boundary. The verification line's
`unflushedTargetDropTargets` therefore now reads `hex:frames=N:batches=M` instead of `hex:frames=N`.

This does not draw the geometry that is dropped - the per-target drain was reverted for a device-lost regression and
still needs a pass of its own - but a counter that is quoted as evidence for "what was accepted was drawn" can no
longer be quoted without its image.

The new attribution immediately named something the old one could not: the three Celtium KPI legs report
`unflushedTargetGroupsDropped=59/68/73` with `unflushedTargetDropTargets=[0:frames=1:batches=59]` (and 68, 73) - one
frame per leg, and the target is **identity 0, the default framebuffer**, not a mod's off-target pass. So the loss is
main-target geometry accepted and never drawn, once per leg. That is a smaller and more specific statement than the
round-14 `98` with no target, and it is the one a later round can act on.

## What this round does not claim

* **No root cause** for the 1.68x. The split names the brackets, not the line inside them.
* **No Intel arm.** The Intel p99 pair in `plan.md` is untouched.
* **No visual claim** from the budget leg: validation was off for it, as the KPI flags require.
* **Condition C is not met** on this profile, and the plan says so in the row that previously carried `NOT_SLOWER`.
