# Paired KPI round 67: p50 1.062x SLOWER (2026-11-05)

Leg set `r67kpi`: renderer `178eec65…` (HEAD `1610773d9`, shaderc natives + noMerge diagnostic fix; shipped
behaviour unchanged since round 64's code apart from those), fixture `22fcf3e0…`, reference Celeritas
`567dcf8a…` (`01-celeritas-forge-mc12.2-2.5.0-dev.jar`). AMD RX 6400 (RADV NAVI24), `celtium-world-accept`
(193 mods, `qmagnet`), Cleanroom 0.7.14-alpha via relauncher, guiScale 3 seeded (`CELTIUM_LEG_SEED_OPTIONS`),
validation/readback off, presented dump every 0, warm ticks 400..600, 3 pairs, every leg PASSED.

| rep | Celtium p50 | Celeritas p50 |
|---|---|---|
| 1 | 2.627 (p99 23.3, 2601 samples: a disturbed leg) | 2.474 |
| 2 | 2.532 | 2.364 |
| 3 | 2.517 | 2.561 |

**median paired ratio 1.062x, verdict SLOWER, p50/p95/p99 1.062/1.218/1.220, goalCondition NOT_MET.**
Round 64 was 1.189x; the p50 gap is now ~0.15 ms.

## The reference arm draws the same scene

Celeritas's own capture fires early (`captureStartTick` 327-337 with `listedSections=3`, against Celtium's 17 at
341-383), and its tick-200 and capture pictures show almost no terrain, which looked like a lighter workload in the
warm window. Rejected: leg `r67ref450` (same inputs, Celeritas, `screenshotAtTick=450`, PASSED) shows the full scene
at tick 450 - terrain, banners, bed, skulls, spawner, shulker box, minimap. "listed N stable for M frames" at the end
is not time-since-arrival: the fixture flies and sweeps the camera from tick 400, and Celtium's own count also
restarts (rep 2: stable for 396 frames at the end).

Not established: Celeritas's GL screenshots lack the info-HUD text that Celtium's presented frames carry; that can be
the capture point inside the frame rather than a workload difference.

## CPU profile of one pair - withdrawn: it covered world loading

`r67prof-{celtium,celeritas}` attached async-profiler 110 s after the game JVM started. A JIT recording taken the
same way (`r68jit`, `jfr-jit.jfc`) shows where that lands: windows 11:10:26-51 (Celtium joined the world 11:10:44)
and 11:13:30-55 (Celeritas joined 11:13:55) - the integrated server start and world join, not warm ticks 400..600.
So the profile's differences (TESRs, `drawChunkLayer`, `setupTerrain`, client tick, and the JIT's 7357 vs 3534
compilations with similar deopts 667 vs 564 and no recompile loop) describe loading, and are not leads for the
measured frames. Re-profile aligned to the join (warm ticks are ~20-30 s after it).
