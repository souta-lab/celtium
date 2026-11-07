# A mod's own shader program is drawn by default — and the KPI was measuring a renderer that skipped the bloom (2026-11-07)

Round 14 of phase 4. The change is one default: `ModPrograms.MOD_PROGRAMS` is now on unless
`-Dceltium.vulkan.modPrograms=false` says otherwise. The measurement that forced it is that the KPI's own scene binds
GTCEu's bloom programs every frame and the opt-in default refused every one of their batches, so the r13
`goalCondition=MET` compared a renderer that draws the chain against one that drops it — which goal.md excludes
("描画を削って得た FPS … は達成に数えない").

## The refusals were 15 full-screen passes a frame, in the KPI scene

`r13kpi-amd-celtium-r1` (renderer `25d73c78`, the r13 stage, world scene, validation off, warm ticks [400,600)):

```
CELTIUM_PROGRAM_BOUND program=110213 site=[... GL20.glUseProgram <- codechicken.lib.render.shader.ShaderProgram.useShader:122 <- gregtech.client.shader.Shaders.renderFullImageInFBO ...]
CELTIUM_PROGRAM_BOUND program=110242 ... (the same site)
CELTIUM_PROGRAM_BOUND program=110243 ... (the same site)
CELTIUM_PROGRAM_BOUND program=110244 ... (the same site)
CELTIUM_SHADER_PROGRAM_REFUSED program=110213 format=6 lowered=true uniformWrites=0 (first batch refused with this program bound)
... 8 programs in all, all bound from Shaders.renderFullImageInFBO
shaderProgramRefusals=194775   frameId=12985   -> 15.0 refused batches a frame
```

GTCEu's unreal bloom is 15 full-screen passes a frame (two `IMAGE_F` copies, five down-samples, five up-samples,
`composite`, `bloom_combine`, the final copy), and it runs unconditionally once `Shaders.allowedShader()` is true. The
Celeritas arm runs the same mod code through real GL, so it draws all 15. The r13 KPI therefore measured
`celtium (2.149 ms p50, chain dropped) / celeritas (2.450 ms p50, chain drawn) = 0.893`.

## The GTCEu scene: the chain draws, the emissive composite arrives, the halo does not

Both switches on (`-Dceltium.vulkan.modPrograms=true -Dceltium.vulkan.terrainToBoundTarget=true`), AMD RX 6400,
Supersymmetry, `scene=gregtech`:

| leg | guiScale | result | lamp box mean RGB | halo band mean RGB | `CELTIUM_TERRAIN_BOUND_TARGET` | `CELTIUM_MOD_PROGRAM_DRAWN` | refusals | `CELTIUM_LAYER_GEOMETRY layer=Bloom` |
|---|---|---|---|---|---|---|---|---|
| `gt1-amd` | 2 | `UNCALIBRATED_PRESENTED_IMAGE` (the reference is at guiScale 3) | 170.8 / 173.4 / 168.3 | 155.9 / 185.1 / 210.9 | 4 (frames 89-92) | 16 | 0 | `passes=[TerrainRenderPass[name=bloom]=true]` |
| `gt2-amd` | 3 | **PASSED**, validation 0 | **210.1 / 212.7 / 207.6** | 155.9 / 185.1 / 210.9 | 0 | 16 | 0 | `passes=[TerrainRenderPass[name=bloom]=false]` |
| reference (`r3-gtref2`, Celeritas 2.5.0-dev) | 3 | — | 243.0 / 245.5 / 240.6 | 209.4 / 236.3 / 233.2, near-white 0.1279 | — | — | — | — |

The lamp box is the rectangle the bloom evidence has used since 2026-11-04 (x 320..540, y 265..315 of the 1280x720
world frame); the halo band is x 300..560, y 230..265. Both legs are the same jar (`25d73c78`), the same fixture
(`f15ba461`), the same pose and the same stage.

Readings, kept apart:

- **The chain draws.** 16 `CELTIUM_MOD_PROGRAM_DRAWN` lines, `CELTIUM_SHADER_PROGRAM_REFUSED` 0, and the pass list is
  the whole chain: `bloomFBO(23884) -> main`, `main -> bloomFBO` with `glBlend=772,0` (DST_ALPHA), then
  `23896 -> 23898 -> ... -> 23906` for the down-samples.
- **The emissive composite arrives.** The lamp row goes from 170.8/173.4/168.3 (the r13 default, chain refused) to
  210.1/212.7/207.6 — the first `IMAGE_F` pass puts bloomFBO's content on the main image. The reference's 243.0 is
  that plus the halo.
- **The halo does not.** The halo band is unchanged to 0.1 in both legs, so the mask the blur chain reads is empty.
- **The routing is not the blocker; the geometry is.** `gt1-amd` built the bloom layer's terrain geometry
  (`passes=[...=true]`) and the terrain pass did attach the bound framebuffer's chain pair four frames running
  (`CELTIUM_TERRAIN_BOUND_TARGET pass=bloom identity=0x38b73849`). `gt2-amd` did not build it
  (`passes=[...=false]`), so the pass returned at `!renderLists.hasPass(pass)` before the target selection and the
  routing never ran. The same scene, jar and pose produced both, so the bloom layer's geometry build is the open
  defect, not `terrainToBoundTarget`.

## The KPI with the chain drawn: `NOT_MET`, and the cost is the windows

`r14kpi-amd` (AMD RX 6400, renderer `1ae204dd`, fixture `f15ba461`, reference Celeritas 2.5.0-dev `567dcf8a`, 3 reps,
240 s, guiScale 3, validation off, warm ticks [400,600), load guard passed and not bypassed):

| rep | celtium p50 / p95 / p99 (ms) | celeritas p50 / p95 / p99 (ms) |
|---|---|---|
| 1 | 2.694 / 4.787 / 6.076 | 2.408 / 4.532 / 5.345 |
| 2 | 2.673 / 4.850 / 6.040 | 2.432 / 4.568 / 5.502 |
| 3 | 2.621 / 4.797 / 6.337 | 2.480 / 4.692 / 5.694 |

`median paired ratio=1.099x verdict=SLOWER`, `p50/p95/p99 median ratios=1.099/1.056/1.113 goalCondition=NOT_MET`.

`r14kpi-intel` (Intel UHD Graphics 630 (CFL GT2), same stage and flags, load guard passed and not bypassed: initial busy
0.136, limit 2.0; all six runs `PASSED`, fixture `PASSED`):

| rep | celtium p50 / p95 / p99 (ms) | celeritas p50 / p95 / p99 (ms) |
|---|---|---|
| 1 | 4.243 / 6.853 / 7.761 | 3.551 / 6.321 / 7.223 |
| 2 | 4.512 / 7.251 / 8.756 | 3.747 / 6.780 / 8.111 |
| 3 | 4.745 / 7.643 / 8.659 | 3.740 / 6.421 / 7.477 |

`median paired ratio=1.204x verdict=SLOWER`, `p50/p95/p99 median ratios=1.204/1.084/1.080 goalCondition=NOT_MET`.
Celtium's three Intel reps drift upward (4.243 / 4.512 / 4.745) while Celeritas's stay flat (3.551 / 3.747 / 3.740), so
the Intel median is the pessimistic end of its own spread; the verdict does not depend on it (every pair is above 1.0
at every percentile on both GPUs).

Where the 545 us of p50 went, from the same runs' censuses:

| | `r13kpi-amd-celtium-r1` (chain refused) | `r14kpi-amd-celtium-r1` (chain drawn) |
|---|---|---|
| `frameId` | 12 985 | 10 250 |
| `windowRecords` / `immediateWindows` | 65 213 (5.0 a frame) | 201 106 (19.6 a frame) |
| `windowRecordMillis` | 2 798 (215 us a frame) | 4 313 (421 us a frame) |
| `shaderProgramRefusals` | 194 775 | **0** |
| warm p50 | 2.149 ms | 2.694 ms |

So the chain adds 14.6 windows a frame and 206 us of window recording; the other ~340 us is the flush each chain
window forces on the window before it, the barriers, and the submits. Celeritas draws the same 15 passes inside its
2.4 ms frame, so the per-window overhead — not the GPU work — is the gap.

## What changed

`ModPrograms.MOD_PROGRAMS` is `!"false".equalsIgnoreCase(System.getProperty("celtium.vulkan.modPrograms", "true"))`.
A program that cannot be lowered or compiled is still refused and counted per program
(`VulkanModProgramPipeline.unsupported`), so a machine without shaderc behaves exactly as before. The reason the
default was opt-in — the passes into the game's framebuffer blacking the world out — was the stale
`activeImmediateTarget` read fixed in round 9 of [gregtech-bloom-2026-11-04.md](gregtech-bloom-2026-11-04.md); with
the barrier keyed on `targetRegistry.currentIdentity()` the same passes draw with validation clean (`r42-gt`).

## What is open

1. **The halo**: the bloom layer's terrain geometry is usually not built
   (`CELTIUM_LAYER_GEOMETRY layer=Bloom ... passes=[TerrainRenderPass[name=bloom]=false]`), so the mask stays empty.
   The next discriminator is the build: which pass set a section's build result carries for a layer added at runtime
   by `EnumHelper.addEnum`, and why the same scene produced `true` in `gt1-amd` and `false` in `gt2-amd`.
2. **The chain's cost**: 15 windows a frame at ~36 us each (14 us of recording plus the flush, barriers and submit).
   The target is the per-window overhead, not the passes' pixels.
3. **Nomi's picture** must be re-shot with the new default: its 21,885 format-6 refusals are gone, and the chain now
   draws, so the r126 picture numbers in [modpacks.md](../modpacks.md) are pre-change.
