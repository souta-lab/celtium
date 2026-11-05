# Hidden-section rebuilds: off by default (2026-11-05)

## Observation: a join-aligned wall profile

`r69prof-{celtium,celeritas}`: renderer `178eec65…` / Celeritas `567dcf8a…`, fixture `22fcf3e0…`, AMD RX 6400,
`celtium-world-accept`, guiScale 3 seeded, validation/readback off. async-profiler 4.6 `-e wall -i 1ms -t -d 9`
attached 21 s after the "joined the game" line (warm ticks), both legs PASSED. Client thread, samples in 9 s:

| subtree | Celtium | Celeritas |
|---|---|---|
| total | 7552 | 8933 |
| GL driver (`libgallium`) | - | 3164 |
| block entities (`renderBlockEntityList`) | 1024 | 2648 |
| `drawChunkLayer` (terrain record) | 608 | 96 |
| `setupTerrain` | 383 | 48 |
| of which `RenderSectionManager.updateChunks` | 208 | 30 |
| of which `submitRebuildTasks` (`WorldSlice.prepare` 79, `scheduleTask` 33) | 112 | **0** |
| `LockSupport.unpark` -> `pthread_cond_signal` (waking build workers) | 154 | - |
| client `runTick` | 671 | 389 |

Celeritas submits no rebuild in the warm ticks; Celtium keeps submitting. The cause is in this fork:
`RenderSectionManager.celtium$queueMeshUpdates` queues **every** section with a pending mesh update (up to ~19,600 at
render distance 18), while the visibility search (`VisibleChunkCollector.visit`, upstream) already queues the visible
ones, nearest first. The extra producer was added when a leg (`gv-cohort-rl2`) read P0=P1=P2=0 with real chunk data.

## A/B on one jar

`r70hidden`: renderer `d9d3844c…` (switch added, default on), `-Dceltium.terrain.queueHiddenPending=false` in the
candidate arm only, 3 reps, validation off, warm ticks 400..600, all six legs PASSED with 17 listed sections.

| rep | off | on |
|---|---|---|
| 1 | 2.483 (p95 5.001) | 2.857 (p95 5.726) |
| 2 | 2.413 (p95 4.556) | 2.637 (p95 5.254) |
| 3 | 2.468 (p95 4.909) | 2.483 (p95 4.878) |

**Median control ratio 0.915x.** Warm tick-frame p50 5.29/5.33/5.70 off vs 5.92/5.78/5.52 on.

Default now off (`7f2b1c19b`); `-Dceltium.terrain.queueHiddenPending=true` restores the producer.

Acceptance of the new default (renderer `ae276acd…`, validation on): `r71-world-intel` (Intel UHD 630,
world-accept) PASSED, gpuRequirementMet=true, validation 0, 17 listed sections; `r71c-light-amd` (private
`world-0.7.11`, AMD) PASSED, validation 0. (The `cleanroom-0.7.11-alpha` instance cannot run unattended: its
`qmagnet` save was written with Xaero's minimap and stops on Forge's missing-registry prompt.)

Not established yet: E2E (heavy pack, night) where the producer was last needed for the far floor; pop-in when
turning onto hidden sections (they now build when the search reaches them, as in Celeritas).

## KPI against Celeritas on the new default: disturbed, not a verdict

`r72kpi` (renderer `ae276acd…`, reference `567dcf8a…`, same profile as round 67): median paired ratio 1.166x, but rep 1's
Celtium leg (3.284 ms, p99 16.7, 2414 samples) ran while this session built the next jar with Gradle, and the reps
spread from 1.42 to 0.94 (rep 3: Celtium 2.208 vs Celeritas 2.359). Not used as the current judgment; to be re-run
with the machine idle.

The join-aligned profile (r69prof) also caught the fixture's own PNG writes and reads on Celtium's client thread
(presented-image dump, picture hashing): its 9 s window ran past world tick 600 into the end-of-run picture work, so
wall-time-since-join does not map exactly onto ticks. In the KPI legs both arms capture before tick 400 (Celtium 288,
Celeritas 310/342), so the measured ticks 400..600 do not contain it.

## Clean KPI on the new default: p50 NOT_SLOWER

`r74kpi` (renderer `ae276acd…`, same code as HEAD after the animation-batch revert; reference `567dcf8a…`; AMD RX 6400,
world-accept, guiScale 3, validation off, warm ticks 400..600, 3 pairs, all PASSED, machine idle):

| rep | Celtium p50 | Celeritas p50 | p50 | p95 | p99 |
|---|---|---|---|---|---|
| 1 | 2.429 | 2.361 | 1.029 | 1.085 | 1.060 |
| 2 | 2.142 | 2.352 | 0.911 | 0.958 | 1.106 |
| 3 | 2.167 | 2.370 | 0.914 | 0.978 | 1.059 |

**Median paired p50 ratio 0.914x, verdict NOT_SLOWER**; medians p95 0.978, p99 1.060; goalCondition NOT_MET (it needs
every percentile below 1 in every pair). The remaining gap is the tail: p99 is 6-11% slower in every pair.

## Evening KPI on the current renderer (2026-11-05, renderer `3d4fd4fe…`, reference `567dcf8a…`, world-accept, guiScale 3)

- AMD `n3kpi-amd` (3 pairs, PASSED): p50 **0.981** NOT_SLOWER, p95 1.028, p99 1.033, NOT_MET. Pair 1 went the other way
  (2.601 vs 2.486 ms); pairs 2-3 0.970/0.981. The 0.914 of round 74 did not reproduce; the machine was not quiet
  (load average 3-5 through the evening), so neither value is preferred over the other yet.
- Intel `n5kpi-intel` (3 pairs, PASSED): p50 **0.919** NOT_SLOWER, p95 0.984, p99 1.048, NOT_MET — consistent with r81
  (0.901/1.015/1.054). A first attempt (`n3kpi-intel`) died on a full disk after two pairs and is not used.
