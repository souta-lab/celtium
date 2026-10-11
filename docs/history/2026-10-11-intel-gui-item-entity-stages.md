# Intel UHD 630: the GUI, item and entity stages (task-23) — 2026-10-11 16:44

task-11 took the Intel terrain leg and left a list of what was unverified there: the GUI, item and entity
stages had never run on the Intel driver, so completion condition (d) had one vendor for terrain only. This
is those three scenes on Intel, each as a GL-less candidate leg against an upstream-Celeritas reference leg
at the same flags, pose and tick.

**Headline: all three stages draw on Intel.** The mobs, the chest and the dropped item are in the frame;
the inventory/JEI panel, its item grid and its tooltip are in the frame; the video settings screen is in the
frame and matches the reference to a mean of 0.978 of a count. No VUID in any leg. What fails is the
fixture's own verdict, and in two of the three scenes it fails *for the reference leg too* or for a reason
that has already fired on AMD — the details are below, because "FAILED" here is a finding about the gate and
the dump handshake, not about the Intel driver.

## What was run

`VK_ICD_FILENAMES=/usr/share/vulkan/icd.d/intel_icd.json`, `--render-node /dev/dri/by-path/pci-0000:00:02.0-render`
(renderD128, the iGPU), `--require-gpu Intel`, `--run --validate --duration 240 --headless --force-x11`,
harness slot arbitration (`--slot-wait 1200`). Script: `.celtium-work/runtime/intel-legs/intel-scenes.sh`;
per-scene numbers and crops: `.celtium-work/runtime/intel-legs/scene-report.py`.

| scene | flags | legs | fixture verdict |
|---|---|---|---|
| entity | `keepEntities=true captureEntities=true` | `intel-entity-ref`, `intel-entity-glless` | ref UNCALIBRATED, GL-less **FAILED** (dump identity, delta +22) |
| inventory | `jeiScreen=true keepInventoryScreen=true` | `intel-inv-ref`, `intel-inv-glless` | ref UNCALIBRATED, GL-less **FAILED** (dump identity, delta +20) |
| videoSettings | `scene=videoSettings` | `intel-vid-ref`, `intel-vid-glless` | **both FAILED** — the reference fails three calibrated floors, the GL-less leg fails those three plus the dump identity |

Celtium jar `652be4675787b727…` (frozen at `.celtium-work/runtime/intel-legs/jars/celtium-frozen.jar`,
built from tree `c53011d98` with three renderer files dirty), fixture `b0999ac19e237c0b…`, reference jar
`d12425eaaf184e79…`. Every leg: `validationClean=true`, `validationMessageCount=0`, 0 `VUID` occurrences,
`depthFormat=D32_SFLOAT` (candidate 1 of 6 on Intel), `nativeDeviceName="Intel(R) UHD Graphics 630 (CFL GT2)"`
and `gpuRequirementMet=true` in the GL-less legs.

## Entity scene — the models are there; the difference is the distant terrain

Both legs spawn the same mobs (creeper, zombie, spider), the chest, the standing sign and the dropped apple
at the same coordinates, and both frames contain all of them — crop
`.celtium-work/runtime/intel-legs/crops/intel-entity-band-150-420.png` (rows 150-420, full width): creeper,
zombie, spider and the chest are drawn in both, in the same poses.

| region | GL-less | reference | >8 | mean |
|---|---|---|---|---|
| sky (0,0,1280,120) | (158.2,193.0,255.0) | (158.2,193.0,255.0) | 0.00% | 0.733 |
| entity cone (300,380,1000,680) | (102.3,65.6,60.9) | (102.6,65.9,61.0) | 0.75% | 0.402 |
| hotbar strip (490,660,790,720) | (103.7,23.2,22.4) | (103.7,23.2,22.4) | 0.00% | 0.000 |
| whole frame | (122.4,105.4,119.6) | (123.4,107.7,121.3) | 8.99% | 2.988 |

The whole-frame difference is not the entities: it is rows 200-360, where the reference shows the distant
terrain plateau and the GL-less leg shows much less of it (y240-279: GL-less (98.3,63.6,58.0) against
reference (107.1,94.6,85.2), 87.6% of pixels over 8). That is the same shape as the terrain scene's horizon
difference, which is another task's stage and is present on both vendors; below row 480 the two frames are
pixel-identical (0.00% over 8).

Counters: `modelDraws=1944 modelMeshLists=38 modelMeshBuilt=38 modelQuads=228 modelQuadVertices=912
modelDrawRefusals=0`, `glDrawArraysCalls=248 drawn=191 vertices=691932 refused=57 pureBlueGroups=0`;
histogram `id1=36/3 id2=17835/17796 id4=33/33 id5=60/60 id6=400/400 id7=280/280 id8=128/128 id9=228/228`.
The two refusal reasons visible in the census are general, not Intel-only:
`target: the viewport is 1281x720, the target is 1280x720` (19) appears in 17 run logs in this workspace
including AMD ones (`cand3-terrain`, `t16-atlas1/4`, `t17b/t17c-break-on`, `t18b-glless-glow`), and
`line width 2.0 > the 1.0 this renderer draws` (15) appears on AMD too (`now-gl`). The 33 offered/3 accepted
format-1 batches are those two reasons.

## Inventory scene — panel, grid and tooltip all present; the difference is brightness

Crop `.celtium-work/runtime/intel-legs/crops/intel-inv-panel-420-120-860-600.png` (GL-less above, reference
below): the JEI "Building Blocks" panel, its 9x5 slot grid, the item icons and the "Gold Ore" tooltip are
drawn in both legs.

| region | GL-less | reference | >8 | mean |
|---|---|---|---|---|
| panel (420,120,860,600) | (136.4,128.9,120.3) | (131.9,127.5,123.1) | 40.78% | 17.889 |
| tooltip (660,315,820,355) | (58.6,46.1,60.5) | (59.4,47.1,61.6) | 3.75% | 2.872 |
| hotbar strip (490,660,790,720) | (24.8,22.7,20.3) | (25.5,22.8,20.5) | 0.00% | 0.331 |
| chat block (0,320,660,620) | (86.4,81.9,77.6) | (84.2,77.8,74.8) | 65.00% | 15.647 |
| whole frame | (69.8,67.2,67.0) | (72.2,69.4,70.6) | 49.91% | 10.874 |

Panel distinct colours: reference 6,550, GL-less 2,672 with the same two modal greys — the brightness and
colour-count shape task-8 owns, and the same shape the AMD pairs showed. The hotbar strip is identical.
Counters: `modelDraws=140 modelMeshLists=5 modelQuads=30`, `drawn=119 vertices=430716 refused=24`;
histogram `id1=377/377 id2=24563/24528 id4=1904/1904 id5=38/38 id6=754/754 id7=334/334 id9=30/30` — the
1,904 item-model (format 4) batches are all accepted.

## videoSettings scene — both legs fail the calibrated floors, the reference first

The GL-less leg and the upstream-Celeritas reference agree on the screen (crop
`.celtium-work/runtime/intel-legs/crops/intel-vid-gui-centre-320-100-960-620.png`, rows 100-620, columns
320-960: "Graphics: Fancy", "Lighting: Maximum", "GUI Scale: Auto", "Render Distance", "Mipmap Levels" and
the rest of the list drawn in both; 3.78% of pixels over 8, mean 0.978). The reference leg is still
`FAILED`:

| assertion | reference (Intel, upstream GL) | GL-less |
|---|---|---|
| `presentedVerticalBands >= 250` | **229** | **212** |
| `presentedBlackFraction <= 0.05` | **0.068322** | **0.073333** |
| `guiPanelDistinctColours >= 4000` | **125** | **127** |
| dump identity (world, gui) | — (screenshot written synchronously) | **+20 frames** both stages |

So the videoSettings floors are not vendor-portable as calibrated: the *reference renderer* on Intel misses
all three, which means the floor describes the AMD GL-bridge runs' screen, not this screen. The GL-less leg
misses them by the same amounts, plus the dump-identity lag. Nobody in this pair failed on anything the
candidate does and the reference does not, and the screen itself matches to a mean of 0.978.
Counters: `id2=23548/23511 id6=549/549 id7=327/327 id9=12/12`, `drawn=183 vertices=662400 refused=25`.

## The dump-identity failure is a general GL-less handshake condition, not Intel

Both FAILED candidate verdicts carry the same single extra failure: the kept presented dump is the frame the
renderer presented N frames after the capture recorded its world frames — +22 (entity), +20 (inventory), and
+20 on both stages (videoSettings) — against a tolerance of ±1. The same assertion has already failed on AMD
GL-less runs of this workspace, at the same magnitudes:

| run | vendor | `pictureFrameDelta_world` | verdict |
|---|---|---|---|
| `t17d-encode` | Celtium | +20 | FAILED |
| `lc-wswitch` | Celtium | −27 | FAILED |
| `ww11-gl-terrain` | Celtium | −37 | FAILED |
| `t19-bounds7` | Celtium | +22 | FAILED |
| `t17c-break-on` | Celtium | +18 | FAILED |
| `intel-entity-glless` | Celtium (Intel) | +22 | FAILED |
| `intel-inv-glless`, `intel-vid-glless` | Celtium (Intel) | +20 | FAILED |

The scene is static after the spawn (pinned camera, pinned world time, no motion), so the picture content is
still comparable and was compared above; but the verdict cannot pass while the dump lands tens of frames
after the capture, and that is a defect in the GL-less dump/present handshake with an owner outside this
task. It is recorded here with the deltas rather than worked around.

## What remains unverified on Intel

- **A passing verdict for any of the three scenes.** The entity and inventory scenes are UNCALIBRATED for the
  reference (no stored reference at those variants) and FAILED for the candidate (dump identity); the
  videoSettings scene fails on floors the reference itself fails. Nothing here is a picture match *accepted
  by the gate* on Intel — it is a picture comparison taken and read by hand.
- **The dump-identity handshake on Intel at ±1**: not achieved in any of the three scenes.
- **The videoSettings floors on Intel**: whether they should be recalibrated per vendor or the reference
  picture is wrong is not decided here; the measurement is that upstream Celeritas on Intel misses all three.
- **The distant-terrain plateau** (entity scene rows 200-360): measured as a difference, not attributed; it
  is the terrain stage, present on AMD too.
- **Particles, weather, clouds, water, entities other than the four mobs, TESR glint, resize, device loss**
  on Intel, and any vendor or OS other than AMD and Intel on Linux.
