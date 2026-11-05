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
