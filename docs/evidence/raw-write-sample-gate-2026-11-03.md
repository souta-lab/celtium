# the ordering flush for a pending copy is asked of the texture, not of the offer (2026-11-03)

The window census ([window-census-2026-11-03.md](window-census-2026-11-03.md)) named the frame's window sources and
put **9.2 of the 35.8 windows a frame** at one site: `WorldVertexBufferUploader…offerBatch`, i.e. an immediate batch
offered while a coalesced raw write was pending. The accept path flushed and applied on *every* offer while anything
was pending, which is one window per offer in a mod's copy-then-draw burst. This round asks the question the ordering
rule actually needs - does the batch about to be accepted sample a texture whose copy is still pending - and measures
the window count with the gate on and off in one paired set.

## The change

`VulkanTerrainGpuBackend.submitImmediateBatchBody`, the pending-write branch:

* before: `if (COALESCE_RAW_WRITES && !pendingRawWrites.isEmpty() && !immediateWindowBusy) { flush; apply; }`
* after: the same branch, but the flush happens only when `celtium$pendingRawWriteSamples(formatId)` says the batch
  samples one of the pending copies' textures - the bound texture, and for the two lightmapped layouts (`BLOCK`,
  `PARTICLE`) the lightmap. Otherwise the offer is counted (`rawWriteGateSkips`) and accepted without a window.

`-Dceltium.vulkan.rawWriteSampleGate=false` restores the unconditional flush; that is the control arm below. The
counters are printed in the end-of-run summary as `rawWriteGate=[skips=N flushes=M]`.

Why the deferred copies are not lost or misordered:

* A copy must be recorded **after** the groups queued before it and **before** any draw that samples its texture.
  The first half is unchanged: the copies are recorded at the next flush, after those groups.
* The second half is what the gate enforces: a batch that samples the copy's texture still flushes and applies first.
  A batch that samples a different texture cannot see the copy.
* Every frame ends with `celtium$flushPendingAtFrameBoundary()` → `celtium$applyPendingRawWrites()`, so a copy that
  no draw sampled this frame is applied at the frame boundary rather than carried indefinitely.

## The paired set

| | |
|---|---|
| Legs | `r46b-gateoff` (`-Dceltium.vulkan.rawWriteSampleGate=false`) and `r46c-gateon` (default) |
| Renderer / fixture | `e34480edeebc3351ce789fa50d188aec545d469315a49f83165ff7657714cdb2` / `79899e494374f89f5c813029d937985d5231265bf91abf027b1ed683860303ca` (same JAR in both arms) |
| Profile | `world` (`celtium-world-accept`, 188 mods, `qmagnet`), AMD Radeon RX 6400 (RADV NAVI24), `gpuRequirementMet=true` |
| Scene | `sceneCalibrationKeyMatches=true` in both, 1280x720, `guiScale3.txt`, render distance 18, pinned time 6000, clouds 0 |
| Flags | the leg's own: `-Dceltium.vulkan.validation=true -Dceltium.validation=true -Dceltium.validation.scene=world …` (validation **on** in both arms) |
| Duration | 240 s each (the world reaches tick 338 / 351) |
| Verdict | both `PASSED`, `problems=[]`, `fixtureStatus=PASSED` |

## The window count

| | gate off | gate on | |
|---|---|---|---|
| `immediateWindows` / frame | **36.02** (42830 / 1189) | **27.59** (35893 / 1301) | **-8.43 windows a frame, -23.4%** |
| this site's flushes | 17497 (14.7/frame) | 6884 (5.3/frame) | -61% |
| this site's skips | 0 | 147797 (113.6/frame) | |
| `immediateFlushes` / frame | 117.21 | 115.21 | |
| offers / frame | 221.55 | 221.78 | unchanged (the gate changes no acceptance) |
| `flushBeforeGlDrawWithGroups` | 42751 | 35754 | -16% |

The skip count is larger than the control's flush count because the control empties the pending map on every offer:
with the gate, the map stays non-empty across a burst and every offer in it is asked. The two numbers that compare
are the flushes (17497 → 6884) and the windows (36.02 → 27.59 a frame).

## Fidelity and validity

| | gate off | gate on |
|---|---|---|
| validation messages / VUID lines | 0 / 0 | 0 / 0 |
| `validationClean` / `validationExitCode` | true / 0 | true / 0 |
| `CELTIUM_REFUSALS_BY_FORMAT` | `{}` | `{}` |
| `vulkan-check` | passes=42958 skipped=0 errors=0 | passes=36032 skipped=0 errors=0 |
| presented image gate | PASSED: distinct=77136, nonBackground=0.990168, black=0.007345, bands=720 | PASSED: distinct=76451, nonBackground=0.989606, black=0.006719, bands=720 |
| `sceneCalibrationKeyMatches` | true | true |

The two presented frames are the same scene from the same pose, and their difference is the animated content, not
structure: mean|A-B| 5.35/255, 6.2% of pixels above 8, and the differing pixels are the end portal's swirl (the
purple/black frame in the middle), the animated block textures and the HUD's own text (the FPS line reads 10 in one
and 19 in the other). The block layout, the camera, the held item and the HUD frame are identical. A pixel-equality
claim is not available on this scene because those textures animate; the gate floors and the structural comparison
are what this leg supports.

## What this does not claim

* **No speed claim.** `frameIntervalP50Ms` is 7.997 (gate off) against 7.637 (gate on), -4.5%, which is inside the
  +-7% run-to-run noise this project measures; the run also completed 1301 frames against 1189 in the same wall time
  (+9.4%), which is consistent with the window reduction but is not a paired KPI.
* **No warm-window KPI.** Neither arm reached the warm window (ticks 400-600): the world ends at tick 338 / 351, so
  `frameIntervalWarmP50Ms` is -1 in both. The 240 s duration is the minimum that lets this profile finish its scene;
  150 s times the harness out (`harnessExitCode=124`, leg `r46a-gateoff`).
* **No Celeritas comparison and no Intel arm.** This is a window-count measurement on one GPU.
* **Not a claim that the remaining 27.6 windows are all necessary.** The census's other sources (the pack's post
  chain at 13.0, `glClear` at 3.2, the font flush at 2.0) are untouched here.
