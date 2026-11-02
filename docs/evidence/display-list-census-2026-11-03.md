# the display-list census read `none` on a run that replays model lists every frame (2026-11-03)

Round 20 left the drawing surface measured: `CELTIUM_GL_SURFACE_DRAWING` says no drawing entry point reached the
zero-writer, and the no-op census cannot fire in the shipped path. What neither line covers is the **display list** -
the path the "some custom rendering is invisible" report is about, because geometry compiled into a list never passes
through the uploader the immediate path hooks.

The verification line already carried a display-list field, `celtium/lists[…]`. On the calibrated world leg
`r40b-gate` it read:

```
celtium/lists[none]
```

on all four of its prints. The same log holds thirty `CELTIUM_DISPLAY_LIST_ORIGIN` lines for the chest, sign, banner,
bed, skull and biped models (`list=110244 … 110338`). Both readings are true, and the pair is the defect: the census
was an `int[512]` / `long[512]` pair indexed by the list's GL name, and it recorded a replay only when the id was below
512. `glGenLists` hands out names from a process-wide counter, and vanilla's model renderers compile theirs after the
GUI and the Tessellator have taken thousands, so **the array could not record the lists the census exists for**. The
totals that would have said so (`displayListsCompiled/Replayed/Unresolved`) were printed only from a shutdown hook,
whose output round 20 measured as absent from both the log and the raw stdout capture.

## The change

`celtium/lists[…]` now reports the measurement instead of a token:

```
celtium/lists[compiled=2 replayed=0 unresolved=0 noList=1322 nestingOverflow=0 held=2 distinct=0]
```

* the per-list table is a sparse `Int2IntOpenHashMap` keyed by the real id, so there is no ceiling;
* `top=[id:xN/calls=M …]` ranks the ten busiest lists by replay count, with the recorded calls one replay executes -
  which is what separates a model replay (dozens of calls) from a sky quad (two);
* `unresolvedTop=[id:xN …]` names the ids that resolved to nothing, because "12 unresolved" says nothing about which
  lists a caller expected;
* the shutdown hook prints the same string, so the two cannot report two shapes of one measurement.

Two further defects fell out of making it readable, both measured rather than suspected:

| defect | measurement | fix |
|---|---|---|
| a non-positive name shared the unresolved counter | entity leg `r41d-entities`: `unresolved=1250`, **every one of them list `-1`** - what `ModelRenderer` passes for a part whose list was never compiled, which GL defines as a no-op | `noList=` is its own field; `unresolved=` now means a real name that found nothing, i.e. a lost draw |
| a replay refused for nesting past the 64-deep limit was counted nowhere | `displayListsReplayed` only counted the ones that ran | `nestingOverflow=` |

The three census maps live in a nested holder class. `CeltiumGlStub.<clinit>` is **7906 of HotSpot's 8000-byte
`HugeMethodLimit`**; the inline allocations took it to 7936, and a method over the limit is never JIT-compiled. Round 20
hit the same wall with the drawing-name set. Any new static field initializer in that class has to go into a holder.

## The legs (same JAR, AMD Radeon RX 6400 / RADV NAVI24)

Renderer `aaf8eecb57ca30fdd442ad84f2a326ef1eff4edabc1ab503cd49530cfe0e4de4` (the committed tree rebuilds it byte for
byte), fixture `8162189d281c56d2586ec34e0c23a5907da8f065882dc9cad95c9a026298f6fa`, 300 s, `guiScale3`, validation on.
The fixture JAR is content-identical across builds (all 33 entries byte-identical) but not byte-identical, because the
zip entry timestamps are the build time; the renderer JAR is byte-reproducible.

| leg | scene | verdict | validation | refusals | picture |
|---|---|---|---|---|---|
| `r41d-entities` | world + `keepEntities` + `captureEntities` + `displayListCensus` | `UNCALIBRATED_PRESENTED_IMAGE` (the entity variant moves the calibration key, by construction) | **0 lines** in both logs | `{}` | nonBg 0.989693, distinct 76924, black 0.007482 |
| `r41e-gate` | world, calibrated | **PASSED**, `accepted=true`, exit 0 | **0 lines** in both logs | `{}` | nonBg 0.989638, distinct 76342, black 0.006692, `sceneCalibrationKeyMatches=true` |

## What the readable line says, and what it does not

`replayed=0` on both legs is **correct and is not a defect**. The stub's replay path is not the model path: model lists
are drawn natively (`modelDraws=5545`, `modelDrawRefusals=0`, 69 meshes / 456 quads on the entity leg) and the GL body
is cancelled before the stub sees it, which is why the mixin's own census counts 6797 replays while the stub counts
none. The 1252-1322 `noList` calls are `glCallList(-1)`, which GL ignores.

The entity arm is measured here for the first time, and it is not invisible:

| leg | entities at capture | modelDraws | modelDrawRefusals | modelMeshLists | modelMeshQuads |
|---|---|---|---|---|---|
| `r41d-entities` | **335** | 5545 | **0** | 69 | 456 |
| `r41e-gate` | 0 | 3439 | 0 | 33 | 228 |

Same JAR, same scene, same pose, same pinned world time: the entity frame differs from the entity-free frame on
**2.09 % of its pixels** (threshold 24; 3.27 % at 8, 1.58 % at 64), and 10521 of those 19276 pixels are in the
horizon band (rows 240-360) where the cohort stands. That is a coverage measurement, not a calibrated gate: the entity
variant has no measured reference, so its verdict is `UNCALIBRATED_PRESENTED_IMAGE` and no floor was applied.

Not claimed: no Intel arm, no glow/outline arm (the entity leg ran without `glowEntities`), no tooltip-under-a-mod-FBO
arm, no picture change, no performance change. The census says which lists were compiled, replayed and lost; it does
not say the picture is right.
