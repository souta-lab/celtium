# BH-15's open contradiction: `offTargetOffered=793` against `acceptedForOtherTargets=7`, and the counter that names it

Owner: **fog-path**. Date: 2026-10-12, ~02:35 JST.

## The contradiction, from the legs already on disk

Terrain leg `bh15-terrain` (GL-less, `--validate`, scene=terrain, jar `fog-path-bh15.jar`):

| counter | value |
|---|---|
| `offTargetOffered` | **793** |
| `offTargetRefusals` | **none** |
| `acceptedForOtherTargets` | **7** |
| `offTargetPerTarget` | `0: 114/114/114`, `41bf95c1: batchesOffered=677, groupsAtPass=32, drawnPasses=17` |
| `immediateGroupsKeptForOtherTargets` | **0** |
| `immediateGroupsDiscarded` / `postAcceptDroppedGroups` | 0 / 0 |
| `unflushedTargetGroupsDropped` | 0 |

Every "lost group" path reads zero and the off-target refusal list is empty, yet 793 batches were offered for
off-target targets and 7 were accepted for one. So the missing offers do not stop at a *refusal* - they stop
somewhere between the offer increment (`VulkanTerrainGpuBackend.java:4348`) and the acceptance increment
(`:4762`), and no existing counter distinguishes those branches because `offerStage` only reaches a report when
a refusal names it (`lastRefusal` prints `stage=`, and there was no refusal).

## The counter

`celtium$offerStage(int)` now sets `offerStage` and increments a per-stage histogram
(`offerStageReached[16]`), and the report carries it as `offerStages=[1:…,2:…]`. The highest stage with a
non-zero count is where the offers stop: 11 stages exist, the acceptance is stage 11, so a leg that reads
`offerStages=[… ,10:N]` with no 11 names the branch between the last pre-accept step and the acceptance.

All eleven assignment sites were routed through the setter, so no stage can be set without being counted.
`python3 scripts/cpu-contract/shader_push_constant_check.py` is clean (33 shaders, 0 problems) and the build is
green.

## What this does not do

It does not answer the panorama's white by itself; it makes the next leg answer it without another guess. The
two readings it separates are "the offers stop before acceptance" (the histogram's top non-zero stage) from
"they are accepted through a path that does not increment `acceptedForOtherTargets`" (the histogram reaching
11 while the acceptance counter stays at 7).
