# Format 5 (the particle family): why the last 35 batches fell to GL

Date: 2026-10-11. Source: `celtium-vulkan/.../VulkanTerrainGpuBackend.java` at `529f270ca`, working tree
clean — the line numbers below are that revision's. Evidence: `.celtium-work/runtime/regr4-glless` (GL-less,
allBlocks, fixture PASSED, `validationClean=true`, `validationExitCode=0`), re-read with
`scripts/format5_particle_report.py`.

This is a diagnosis record. The patch below is **not applied**; it is written for the file's owner.

## What the run says

`celtium offer decisions: offers=5000 accepted=4705 glBodyCompleted=341`, arrivals
`[1:2254 2:634 5:126 6:946 7:860 9:180]`, backend `immediate formats offered/accepted: ... id5=126/91`,
final `acceptedByFormat=...,5:96,...` and `particleDescriptorRefusals=35`. So 35 of the 131 format-5
offers were refused, and the census names them all as the *same* refusal:

```
CELTIUM_REFUSALS_BY_FORMAT {5 unrecorded (format=5 stride=28 mode=7 count=3932) stage=10=1,
  5 unrecorded (... count=7716) stage=10=1, ... 5 unrecorded (... count=5296) stage=10=4}      (console:924)
```

`stage=10` is the `formatId == 5 && !canBuildDescriptor(boundTexture)` gate. The reason is the *entry
marker* `immediateLastRefusal` set at the top of `submitImmediateBatch` (`unrecorded (format=... stride=...
mode=... count=...)`), not a message from the gate: `canBuildDescriptor` sets a specific sentence for each
condition it names, and the gate only substitutes its own generic sentence when the field is null or empty,
so a named condition would have survived into the tally. The marker therefore proves the gate returned
false **without setting a reason**.

## Which of the three conditions it is: none of them

The three conditions the gate names are excluded by the run's own counters:

| condition | counter | reading |
|---|---|---|
| registry has no entry for `boundTexture` | `textureRefusalsBeforeAccept=0` | the stage-9 gate (`textureRegistry.get(boundTexture) == null`) passed, so the texture was mirrored |
| mirror `!hasFullContent()` | `partialMirrorRefusals=0`, `partialMirrorsCompleted=0` | a partial mirror would have incremented this and named the texture id; the only run that ever printed that sentence is `p348-a04`/`p347-adopt` (texture 29, older build) |
| `lightmapTexture == null` after `ensureLightmapMirror()` | the stage-7 gate `(formatId == 3 \|\| 5) && lightmapTexture == null` passed for these batches, and `lightmap identity: glId=15 ... registryFull=true same=true` all run | the light map was mirrored and whole before the particles arrived |

The condition that fires is the fourth, unnamed return in the same method (line 2407-2411):

```java
if (this.descriptorProbe == null) {
    VulkanTerrainPipeline ui = pipelineFor(this.lastUiPass);
    if (ui == null) {
        return false;                       // no reason recorded
    }
```

## What makes it true

`lastUiPass` is assigned only inside the terrain-pass entry (line 512), *after* that method has required
the atlas and the light map mirrors; until the first pass of the backend instance actually runs, it is
`null`, and `pipelineFor(null)` returns `null` (line 5632). The refused batches are exactly the format-5
offers that arrive before that first pass:

- the refused batches are all `ParticleManager.func_78874_a:371 <- EntityRenderer.func_175068_a:1488`
  (`CELTIUM_FMT5_SITE`, console:312), at `[08:53:44]`, and they bind the particle sheet `glTexture=122`
  (`CELTIUM_INVALIDATE_ORIGIN ... <- ParticleManager.func_78874_a:321`, console:308);
- the backend printed the pass state itself at the first flushed window, one frame before those offers:
  `CELTIUM_WINDOW_ORDER frame=1 lastTerrainPassFrame=-1 terrainRanFirst=false lastUiPass=<no render pass>
  groups=3` (console:274) — `lastUiPass` was null, and no terrain pass had run;
- the first terrain pass of the instance is a second later, `[08:53:45]`: `pipeline for solid created`,
  `block atlas: gl texture 12 512x512`, `CELTIUM_ATLAS_DESCRIPTOR phase=write pass=solid write#1 first`;
- the refusals are the *leading* run of format-5 offers: at `immediateTaken == 500` the backend prints
  `id5=47/12`, i.e. all 35 refusals lie inside the first 47 format-5 offers; the final totals
  (131 offered, 96 accepted, 35 refused) mean **zero refusals after that** — which is what the first pass
  running does: it gives `lastUiPass` a value, the probe pipeline is built, and every later format-5 batch
  is accepted;
- the light map is not the cause and neither is the texture: both gates above passed for these very batches.

The null is also provable from the code alone, without the timestamp: `pipelineFor` **creates** the terrain
pipeline on demand (line 5638-5649), so had `lastUiPass` held any pass at the moment of an offer, the gate
would have built that pipeline and returned non-null — the probe would have been created and the batch
accepted. A creation failure would have cached `null` under that pass and refused every later format-5
batch, which the 96 accepts contradict. A refusal therefore implies `lastUiPass == null`, and the refusals
stop exactly when the first pass runs.

The draw path never had this problem. `prepareImmediate` chooses the render pass the same way for every
format, with the standalone frame-compatible pass as the substitute (line 3883):

```java
long pass = terrain != null ? terrain.renderPass() : uiRenderPass();
```

and the flush path does the same (line 3645). The record chain of the run even opens with
`standalone UI render pass created for the no-terrain-pass frame`, and `immediateOnlyPasses=677` of 1007
passes are exactly that case. So the acceptance gate was refusing the batches the draw path already knew
how to draw — under the GL-less window the GL fallback draws nothing, which is the "does not draw
natively" symptom.

## Patch (for the file's owner)

`VulkanTerrainGpuBackend.canBuildDescriptor`, two hunks. The first makes the gate ask the descriptor
question with the render pass the batch will actually be recorded into; the bridge leg still refuses,
because `prepareImmediate` refuses there too — this is the draw path's own rule, not a relaxation.

```java
        try {
            if (this.descriptorProbe == null) {
                // The render pass has to be the one this batch will be recorded into, chosen exactly as
                // prepareImmediate chooses it: the last pass that ran when there is one, and the standalone
                // frame-compatible pass when there is not. lastUiPass is null until the first pass of the instance
                // runs and pipelineFor(null) returns null, so requiring a terrain pipeline here refused every
                // particle batch offered before that first pass - 35 of them in regr4-glless, all before the first
                // terrain pass, and all of them silent: this branch set no reason, so the tally read "unrecorded
                // (format=5 ...) stage=10". The bridge leg keeps refusing because prepareImmediate refuses there
                // too; this is the draw path's own rule, not a relaxation of the gate.
                VulkanTerrainPipeline ui = pipelineFor(this.lastUiPass);
                long renderPass;
                if (ui != null) {
                    renderPass = ui.renderPass();
                } else if (org.soutalab.celtium.vulkan.gl.CeltiumGlStub.isInstalled()) {
                    renderPass = uiRenderPass();
                } else {
                    this.immediateLastRefusal = "the particle descriptor probe needs a pass and none has run yet"
                            + " (lastUiPass=" + nameOf(this.lastUiPass) + ")";
                    return false;
                }
                this.descriptorProbe = VulkanImmediateParticlePipeline.create(this.context, renderPass,
                        false, 0, 0, false, false, false,
                        org.lwjgl.vulkan.VK10.VK_COMPARE_OP_LESS_OR_EQUAL);
            }
```

and the second removes the last silent refusal in the method (the catch that swallowed a probe that could
not be built at all):

```java
            this.particleDescriptorFailureReason = this.descriptorProbe.lastFailure();
            return ok;
        } catch (Throwable error) {
            // A refusal has to name itself: the gate below only fills in its generic sentence when nothing was
            // recorded, so a silent false here reads as "unrecorded (format=5 ...) stage=10" and cannot be told
            // from the conditions that do name themselves.
            this.immediateLastRefusal = "the particle descriptor probe could not be built: " + describe(error);
            return false;
        }
```

## Predicted verification

GL-less allBlocks, same run shape: format 5 accepted 131/131 (was 96/131), `particleDescriptorRefusals=0`
(was 35), no `stage=10` entries left in `CELTIUM_REFUSALS_BY_FORMAT`, offers/accepted up by 35, fixture
PASSED, `validationClean=true`, scene gate the **calibrated** one (`sceneGateCalibrated=true`, reference
`gl-bridge scene-ref-terrain`: distinctColours=1535, nonBackgroundFraction=0.732164, verticalBands=631),
`presentedImageVerdict=PASSED`.
