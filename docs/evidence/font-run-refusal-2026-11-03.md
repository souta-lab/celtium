# a refused glyph run was dropped, and the census that would have said so had no reader (2026-11-03)

The font run's retention queue was written on 2026-11-01 to keep a refused glyph run instead of losing it, and its own
class doc says it is "implemented and unexercised until a leg actually refuses a run". This round finds that it was not
unexercised but **unreachable**: the backend's sink answered every submission with `true`, so the queue could only ever
be entered by a *thrown* submission, and the batch path does not throw on a refusal - it returns `false`. Every refused
glyph run was lost in silence, and the counters that would have shown it had no caller in any leg.

AMD Radeon RX 6400 (RADV NAVI24)・`celtium-world-accept`・`qmagnet`・1280x720・`guiScale3.txt`・validation 既定ON.

## The contract that was broken

`FontBatch.RunSink` states it in one sentence:

> Returning `false` means the run was refused; throwing means it could not be submitted. Either way the batch retains
> the run and offers it again at the next flush, because a refused run that is dropped is text drawn by nobody.

`VulkanTerrainGpuBackend.celtium$submitFontRun` called `submitImmediateBatch(...)` and then `return true;` - the result
was discarded. `submitImmediateBatch` returns `false` for every refusal it has (a custom shader program bound, a target
that is not ready, a viewport with no area, a framebuffer violation, a reentrant upload, a preflight failure) and only
returns `true` after the batch has been appended, so the discarded value was exactly the fact the queue needed.

The same wiring is correct everywhere else it exists: the legacy `glBegin` capture's sink returns
`submitImmediateBatch`'s own answer (`CeltiumGlStub:3037`), and the display-list compile sink returns whether the run
was filed. The font sink was the only one that answered `true` unconditionally.

## The census that could not be read

`FontBatch.celtium$fontRetentionSummary()` and `celtium$fontStateSummary()` existed with no production caller - only
`scripts/cpu-contract/FontBatchContractCheck.java` read them. So "the retention never had to fire" and "the retention
cannot fire" were the same absence in every leg record, which is why the first survived four rounds.

They are now printed at world teardown as `CELTIUM_FONT_RUN_CENSUS`, next to the per-frame split's summary. The glyph
capture's shutdown summary carries them too, but **a shutdown hook's stdout is not in the log**: measured on leg
`r39a-gate`, `CELTIUM_FONT_GLYPH_SUMMARY` appears in no file of the run at all. World teardown is the last point whose
output the log actually holds (the same reason `FrameBudgetTimings.printSummary()` is called there).

## The change

1. `celtium$submitFontRun` returns `submitImmediateBatch`'s answer. A refused run now goes into the retention queue, is
   offered again at the next flush, and is dropped (counted) at the frame boundary if its target never returns - the
   rule `84d325fbf` wrote.
2. The refusal is named once (`CELTIUM_FONT_RUN_REFUSED`, bounded to four lines) and counted (`fontRunRefusals`).
3. `CELTIUM_FONT_RUN_CENSUS` prints the retention and draw-state summaries once per run, at world teardown.
4. `FontRunRetentionTest` (new, 4 tests) pins the batch half of the contract in `:celtium-vulkan:test`: a refusing sink
   leaves the run queued with its bytes intact, the retry hands the sink the same bytes, an accepted run leaves nothing
   queued, a throwing sink retains, and the frame boundary drops what is left and counts it.

## The measurement

Leg `r39b-gate` (renderer `19111c41…`, fixture `5a3e432f…`, same scene and flags as the round-18 gate):

```
CELTIUM_FONT_RUN_CENSUS retainedRuns=0 retainedQuads=0 retainedPendingRuns=0 retainedPendingBytes=0
  retainedDroppedRuns=0 retainedDroppedQuads=0 retainedControlDrops=0 fontRunRefusals=0
  fontDrawStateFlushes=0 depthTest=true depthWrite=true blend=false cull=off(0) colorMask=f scissor=0
```

* `verdict=PASSED`, `fixtureStatus=PASSED`, `exitCode=0`, `sceneCalibrationKeyMatches=true`,
  `sceneGateFloorsEnforced=true`, `presentedImageVerdict=PASSED` (`nonBackgroundFraction=0.990164`,
  `distinctColours=76801`).
* **0** `VUID`/`Validation Error`/`Validation Warning` lines; `CELTIUM_REFUSALS_BY_FORMAT {}`; no Celtium exception.
* `fontRunRefusals=0` is the honest reading of this scene: the calibrated world scene refuses **no** immediate batch at
  all (`refusedBeforeAppend=0`), so the retention has nothing to keep there. The fix is therefore **not exercised by
  this leg** - it is exercised by the four unit tests, and the leg's job is to show it changes nothing where nothing is
  refused.
* `fontDrawStateFlushes=0` is the same kind of statement: the world scene's text is drawn under one draw state, so the
  witness that closed the tooltip's shadow/main defect has nothing to close here.

A second scene was run to look for a refusal, because a fix that never fires is not evidence: leg `r39c-creative`
(same JAR and fixture, `plain`, `-Dceltium.validation.screen=creative -Dceltium.validation.creativeTab=12
-Dceltium.validation.screenshotAtTick=60`, `GuiContainerCreative` open at the capture, tab `littletiles`):

```
CELTIUM_FONT_RUN_CENSUS retainedRuns=0 ... fontRunRefusals=0 fontDrawStateFlushes=0
  depthTest=false depthWrite=false blend=false cull=off(0) colorMask=f scissor=0
```

`fontRunRefusals=0` again, with `CELTIUM_REFUSALS_BY_FORMAT {}` and zero validation lines. The verdict is
`UNCALIBRATED_PRESENTED_IMAGE` (exit 2) by construction - the creative scene's calibration key carries
`screen=named:creative` while the terrain reference carries `screen=none` - so the verdict is not the measurement here;
the census is. **Neither measured scene refuses a font run**, which is the round's other finding: the refusal path needs
a scene with a custom program bound or a target that is not ready, and neither the calibrated world scene nor the
creative inventory is one.

## What this does not claim

* No picture changed in either measured scene, and none is claimed to.
* The fix is **not exercised by a leg**: both scenes refuse no immediate batch at all (`CELTIUM_REFUSALS_BY_FORMAT {}`,
  `refusedBeforeAppend=0`), so the retention has nothing to keep. The evidence that the queue works is the four unit
  tests; the legs' job is to show the change is inert where nothing is refused.
* The scenes where a run *can* be refused (a custom program bound, a target that is not ready) were not run this round;
  the claim there is the contract and the unit tests, not a picture.
* No performance claim. The retention adds a copy only when a run is refused, which is zero in both scenes.
* Intel is not run this round.
