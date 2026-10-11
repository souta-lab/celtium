# Vanilla acceptance table — one row per vanilla stage

Owner of this file: **worker A, the independent verifier** of the vanilla-completion push. The rule
this file follows is the one in `AGENTS.md`: every status carries the measurement that decides it,
and a status without a measurement is written as `U` (unverified), not as "working".

Read `docs/handoff-vanilla-completion.md` first. Two things in this file are corrections to that
brief's premises, and both were measured, not argued:

1. **The bridge leg currently renders no world at all.** It has drawn no terrain since 09:47
   today. The cause is in this tree, not in the fixture and not in world generation. §1.
2. **The bridge runs that "passed" passed on chat and toast pixels.** `nonBackgroundFraction`
   is measured against the frame's most common colour, so a sky-only frame with a chat block over
   it scores 0.81 against a floor of 0.40. §2.

Because of (1) and (2), the only honest bridge terrain reference in this workspace is the pair of
01:15/09:11 runs, and even those are not an independent OpenGL reference for terrain: their terrain
was drawn by Celtium's own Vulkan terrain backend (`passes=661`/`1566`, `skipped=0`), the same code
the GL-less leg runs. The bridge leg differs from the GL-less leg in its frame target and present
path, not in who draws the terrain.

## 1. The bridge leg draws no terrain — confirmed, with the experiment that separates tree from fixture and world

**What is measured.** Every bridge-leg run (`glVendor=AMD` in its report; the GL-less leg reports
`glVendor=Celtium`) carries a Vulkan terrain pass counter. Its value over today:

| bridge run | mtime | jar | nonBg | `passes` | `skipped` | bg | verdict |
|---|---|---|---|---|---|---|---|
| `regr2-bridge` | 01:12 | (not kept) | 0.730838 | 719 | 0 | 0x571716 | FAILED (identity, since fixed) |
| `scene-ref-terrain` | 01:15 | (not kept) | 0.732164 | 661 | 0 | 0x571716 | FAILED (identity) |
| `glow-bridge5` | 09:02 | `b63a8a85312027d5` | 0.748878 | 909 | 0 | 0x571716 | PASSED |
| `bridge-model` | 09:47 | `6bb26fee7ce1b65e` | 0.149201 | 0 | 447 | 0xB3CFFF | FAILED |
| `n03-x-terrainbridge` | 11:56 | `0c90f819f6456c4f` | 0.505074 | 0 | 1068 | 0xB2CEFE | PASSED |
| `n03-ae-terrain-bridge` | 12:23 | `216cd6284a3c22f7` | 0.813402 | 0 | 936 | 0x5A6880 | PASSED |
| `van-bridge2` | 12:59 | `ac347ff1f98e56ad` | 0.153832 | 0 | 1122 | 0xB3CFFF | FAILED |

`skipReason=translucent:_no_pipeline` in all of them, and the same report contains one copy per
attempt of

```
pipeline for solid|cutout_mipped|translucent failed:
  IllegalStateException: the depth format was asked for before anything chose one
```

**The mechanism.** `VulkanDepthFormat.chosen()` (no-arg) throws when nothing has chosen yet
(`VulkanDepthFormat.java:87`). The only caller that *chooses* is `GllessFrameTarget.java:187`
(`chosen(physicalDevice)`) — GL-less only. `VulkanTerrainPipeline.java:901` uses the no-arg form.
So in the bridge leg no one ever chooses, every terrain pipeline creation throws, and every terrain
pass is skipped: Celeritas reports `solid - 46 sections` in the same frame whose Vulkan pass count
is 0. The throw and the choosing were both introduced by `9655ff681` "depth: choose the format from
the device instead of fixing it" (09:30). The first bridge run after it, `bridge-model` (09:47), is
the first sky-only bridge frame. `plan.md` N10 owns this; its row calls the change "AMD では無影響と
実測", which measured *which format the chooser picks* (`scripts/vulkan_depth_format_probe.py`) — it
never ran a bridge leg, so it could not observe that nothing calls the chooser there. `plan.md:51`'s
claim that "GL-less target と terrain pipeline が同じ決定を共有" holds only under GL-less.

**The experiment that separates tree from fixture from world generation.** Same fixture, same
scene, same pose, same flags, same GPU, fresh game directory per run, one minute apart; only the
renderer jar differs. Script: `.celtium-work/acceptance/bridge-ab.sh`.

| run | celtium jar | fixture | `passes` | depth-format throws | nonBg | distinct | bg | verdict |
|---|---|---|---|---|---|---|---|---|
| `a-acc-curjar-bridge` | `b874f92393f79b46` (current tree) | `105598e0f89be16d` | 0 / skipped 120 | 120 | 0.160747 | 104 | 0xB3CFFF | FAILED |
| `a-acc-oldjar-bridge` | `b63a8a85312027d5` (09:02 era) | `105598e0f89be16d` (same) | 242 / skipped 0 | 0 | 0.735010 | 1534 | 0x571716 | PASSED |

Both runs: AMD RX 6400 (RADV NAVI24), bridge leg, scene=terrain, `x=0.5 y=12.0 z=0.5 yaw=180
pitch=20`, tick 200, 1280x720. Pictures: the current jar's is 28346 bytes, 82.10% of its pixels
exactly `(179,207,255)`, mode the sky colour; the old jar's is 110741 bytes, 24.52% `(87,23,22)`
(ground), 16.73% `(179,207,255)` (sky), 1534 distinct colours.

- **Tree, not fixture**: the fixture is byte-identical between the two runs, and the old jar passes
  the current fixture's floors (`PASSED`, `nonBg=0.735010`). A fixture defect cannot make the
  terrain pipelines throw in one leg and not the other; the leg is selected by a JVM property the
  tree reads.
- **Tree, not world generation**: a fresh game directory is generated for each run, and the old jar
  renders the world in its own fresh directory. The world is there.
- Corroborating, not required: the GL-less leg of the same minutes (`van-glless` 12:56,
  `n03-ag-jei-glless` 12:25) runs the same fixture and draws the same flat world with
  `passes=904/931`, `skipped=0`.

**State of the tree at the time of writing.** The defect is still in the working tree: the only
caller of `VulkanDepthFormat.chosen(device)` is still `GllessFrameTarget`
(`GllessFrameTarget.java:212`), and `VulkanTerrainPipeline.java:901` still uses the throwing no-arg
form. Commit `0fddd6ccf` records this finding; it is a record, not a fix. The tree's built jar
changed repeatedly while I measured it (`765ff5296a992952`, then `b874f92393f79b46` for the A/B's
current-jar run at 13:13, then `03c627cc52c8e566`), so every run's jar hash above is the hash of the
jar that run actually loaded, and a re-run must quote its own.

**Consequence for every other worker.** Any comparison whose bridge side is a run after 09:47 is a
comparison against sky plus whatever HUD the frame happens to contain. Do not use `n03-x`,
`n03-ae`, `n03-af`, `n03-ah`, `n03-ai`, `n03-aj`, `skyfix-bridge1`, `posefix-bridge`, `posefix2`,
`jei-br`, `van-bridge`, `m-entity-bridge`, `van-bridge2` as a terrain reference.

## 2. Why the gate did not catch it: the floor is satisfied by a chat block

`nonBackgroundFraction` counts the pixels that differ (tolerance 8) from the frame's **most common
colour**. A sky-only frame's most common colour is the sky, so *every* non-sky pixel counts as
non-background — including the chat block, the toast and the hand.

- `n03-ae-terrain-bridge` (12:23): mode `(90,104,128)` at 10.1% = the chat background;
  `worldFrameDistinctColours=732`; `nonBg=0.813402`; verdict PASSED. Its own picture shows a sky,
  a chat block, the tutorial toast, the hotbar and the player's arm — **no terrain**.
- `van-bridge2` (12:59): mode `(179,207,255)` at 82.8%; `distinct=96`; `nonBg=0.153832`; FAILED.
  Same absence of terrain; the difference is that this fixture run's chat had faded.
- The GL-less leg in the same minutes: `distinct=17400`, mode `(87,23,22)` = ground,
  `nonBg=0.841242`.

So `presentedImageVerdict=PASSED` on a bridge run today means "the frame is not one flat colour and
contains a chat block", not "the world is drawn". `distinctColours` and the modal colour separate
the two cases; the fraction alone does not. A useful extra floor is "the modal colour must not be
the sky colour" or "distinctColours > 1000", but that is a fixture change and not mine to make; it
is reported to the owner (N06, `ValidationClient`).

## 3. The table

Columns: **status** / **evidence** / **owner**. Status vocabulary, used strictly:

- `V` verified locally: a measurement at a named scene/pose/tick against a bridge picture, and I
  looked at the crop or the counter that decides it.
- `W` wrong: implemented, measured, and the measurement disagrees with the reference.
- `M` measured missing: the pixels a stage should produce are absent in a frame that should have
  them, with the counter or crop that shows the absence.
- `U` unverified: no measurement exists that decides it, with the reason.
- `X` unimplemented: no code path / deliberately gated off.

| # | vanilla stage | status | evidence (run, jar, GPU, scene, pose, tick, verdict, counter or crop) | owner |
|---|---|---|---|---|
| 1 | terrain / chunk layers (GL-less) | **V** | `van-glless` GL-less, jar `ac347ff1f98e56ad`, AMD RX 6400, scene=terrain, `x=0.5 y=12.0 z=0.5 yaw=180 pitch=20`, tick 205, `PASSED`, `passes=904 skipped=0` vs bridge `scene-floors/terrain-a` (jar `478cf85555ec37a9`, same pose, tick 200, `nonBg=0.727735`): `vanilla_stage_compare.py` — terrain centre `(320,300,960,640)` 1 pixel of 204800 differs by >8; bottom-left 1 px; bottom-right 0 px; mean RGB equal to 1 decimal in every region below the sky. Crops `.celtium-work/acceptance/crops/terrain/`. **Caveat: the bridge's terrain is also Vulkan (`passes=1566`), so this is a regression check, not an independent-GL check.** | terrain backend; depth-format defect = N10 |
| 2 | terrain / chunk layers (bridge) | **W** | `a-acc-curjar-bridge` jar `b874f92393f79b46`, fixture `105598e0f89be16d`, AMD, bridge, tick 200: `passes=0 skipped=120 skipReason=translucent:_no_pipeline`, 120× depth-format exception, `nonBg=0.160747`, `distinct=104`, verdict FAILED. §1. | N10 (`VulkanDepthFormat`, `GllessFrameTarget`, `VulkanTerrainPipeline`) |
| 3 | sky dome | **W/X** | Row means every 20 px, 1280×720: GL-less `van-glless` rows 0–210 are the **single colour** `(179,207,255)` — a clear colour, no dome gradient; bridge `scene-floors/terrain-a` rows 0–140 are a **gradient** `(149,186,255)→(179,207,255)` then flat; GL-less `n03-ac-terrain-deco` (jar `216cd6284a3c22f7`, tick 200) rows 0–200 are a dark, strongly
varying sky — `(78,88,169)` at y=0, darkest `(42,47,130)` at y=60, `(140,159,215)` at y=160–200 —
**plus large blue wedges across it**. Three different skies in three runs, so nothing here matches. The path that draws it is the `glDrawArrays` one the brief says is gated off because it produced ~174,000 pure-blue pixels; `n03-ac`'s wedges are that path switched on. `sky band (top)` region differ: 87.8% gl-less vs bridge-then. | sky worker (`RenderGlobal.renderSky` via `VertexBuffer.drawArrays`); plan §0.133–0.135 |
| 4 | stars | **U** | No run can decide it: the fixture pins `worldTime=6000` (noon, `pinnedWorldTime=6000` in every report), where vanilla's star brightness is 0, and no counter distinguishes a star draw. The `starVBO` path is part of the gated-off `glDrawArrays` group (row 3). | sky worker |
| 5 | horizon plane | **U/X** | Same gated path as the sky dome. In `van-glless` there is no dark horizon band (rows 140–210 are exactly the sky colour); in `scene-floors/terrain-a` the dome gradient reaches that same colour at the horizon, which is also what a missing horizon plane looks like. Not separable from row 3 with the runs that exist. | sky worker |
| 6 | clouds | **U** | No measured frame contains a cloud pixel in either leg (`scene-floors/terrain-a`, `van-glless`, `a-acc-oldjar-bridge` all show a clean sky), and no run on disk sets `renderClouds`; the run directories' `options.txt` is empty. A run with clouds explicitly on, compared against the bridge-then reference, is the experiment that would decide it. | not attributed in the sources I read |
| 7 | water surface | **U** | The fixture's flat-stack scene contains no water block (`sceneVariant="default flat stack"`, `worldLayers` default bedrock/dirt/grass), and no run in `.celtium-work/` names water. `VulkanImmediateTexturePipeline`'s own comment says format 6 carries "the water surface" among others, so the layout has a branch; nothing measures it. | format-6 owner (`VulkanImmediateTexturePipeline`) |
| 8 | weather (rain/snow) | **U** | The knob exists (`-Dceltium.validation.weather=true`) and 172 runs set it, but the newest is `p490-kitchen` (10-10 18:12) and its jar was not kept; **no run today sets it**. Nothing on the current tree measures it. | format-6 owner |
| 9 | particles | **U (counter yes, picture no)** | Format 5 is implemented and accepted under GL-less on the current tree: `particles` 09:08 `id5=76/76`, `n03-ac-terrain-deco` 12:21 `id5=108/108`, `van-glless` 12:56 `id5=115/115`, `particleDescriptorRefusals=0` in all. There is no bridge picture comparison with particles on for any run whose jar was kept; `docs/history/2026-10-11-format5-particle-probe.md` is the owner's instrument. | format-5 / particle owner |
| 10 | entities (models, renderers) | **U (counter yes, picture no)** | `m-entity-glless` 12:58 GL-less jar `ac347ff1f98e56ad` tick 200: `modelDraws=2477 modelMeshLists=38 modelQuads=228`, accepted format 9 = 228. Its bridge twin `m-entity-bridge` (12:59) is `UNCALIBRATED_PRESENTED_IMAGE` (variant differs) **and** has no terrain, so it cannot serve as the comparison. `plan.md` §0.104/§0.118 record matches on older jars. | N01/N02 (`VulkanDynamicMesh`, `VulkanModelQuadCapture`) |
| 11 | TESR / block entities | **U** | `-Dceltium.validation.enchantItem=true` was last set by `p519-lookdown` (10-10 19:34), jar not kept; no run today. The enchant-table claim in `plan.md` §0.118 is on an older jar and I could not reproduce it here. | N02 owner |
| 12 | items (item models / GUI item stacks) | **W** | `jei-gl` 12:40 GL-less accepted format 4 (ITEM) 4424 batches; `jei-br` 12:41 bridge. `scripts/cpu-contract/compare_jei_pair.py`: inventory panel `(420,120,860,600)` **94.7% of pixels differ by >8** (mean 89.6), JEI item count 100.0%, whole frame 92.9%. The two legs' screens do not agree. | item-8/GUI owner; format 4 pipeline |
| 13 | GUI / HUD (hotbar, panels, slot backgrounds) | **M** | Crop `(480,650)-(800,720)`, same jar `216cd6284a3c22f7`, same calibration key (both `sceneCalibrationKeyMatches=true`), tick 200: GL-less `n03-ac-terrain-deco` shows **terrain only** — no hotbar frame, no slots, no selection highlight; bridge `n03-ae-terrain-bridge` shows the vanilla hotbar with 9 slots and the first slot highlighted. Region means: GL-less `(101.8,22.7,22.0)` (ground) vs bridge `(78.8,82.9,78.1)` (slot frame). Crop: `.celtium-work/acceptance/crops/hotbar-glless-top-bridge-bottom.png`. This is the texture-mirror staleness the brief names. | GUI/texture owner (`VulkanGlTexture` mirror refresh) |
| 14 | chat | **W** | Same pair and crops. The **text** is drawn in both legs (identical wrapped white text over the same region); the **background quad is missing** under GL-less — `n03-ac` shows text over terrain, `n03-ae` shows the vanilla dark box. Region `chat block (0,320,660,620)` mean `(89.2,65.6,85.5)` GL-less vs `(123.6,134.1,152.4)` bridge. The font path (N03) is the part that works. | N03 (font) for text; GUI/quads owner for the box |
| 15 | crosshair | **M** | Crop `(600,320)-(680,400)` of the same pair: bridge `n03-ae` shows the crosshair (dark plus at frame centre) over the chat; GL-less `n03-ac` shows terrain and text, **no crosshair**. Crop: `.celtium-work/acceptance/crops/crosshair-glless-top-bridge-bottom.png`. | GUI/HUD quads owner |
| 16 | selection outline (block selection box) | **U** | No run measures it: `allBlocks` was last set by `alive-again` (10-15, GL-less, no bridge twin); the box needs `drawSelectionBox` and neither leg's report counts it. `plan.md` §0.137 lists `drawSelectionBox(...)` among the injection sweep and does not close it. | not attributed |
| 17 | block-breaking overlay | **U** | `-Dceltium.validation.breakBlock=true` was set by two runs, newest `p471-all` (10-10 17:26), jar not kept; no run today, no counter. | not attributed |
| 18 | fog | **W (partial)** | `fogModeSource=mirror` in every report (both legs), so the *parameter* is mirrored; the distant-terrain region matches the bridge-then (`horizon band (0,120,1280,300)`: 460 of 230400 px differ = 0.2%, mean RGB equal to 1 decimal). But the sky's fog ramp is absent: GL-less rows 0–210 are flat `(179,207,255)` where the bridge-then ramps `(149,186,255)→(179,207,255)`. The brief's "no fog at all in the immediate path (`TerrainGpuBackend` has no `setFog*`)" is consistent with the sky measurement and not contradicted by the terrain one (the terrain's fog is the terrain pipeline's, not the immediate path's). | fog/immediate owner |
| 19 | final composite / present | **V (presence) / W (content)** | GL-less `van-glless`: `presentSucceeded=true presentSubmitDone=true compositePresentRefusals=0 presentedImageFrameCountAboveZero=true`, and the presented dump is the picture that contains the world (row 1). Content is incomplete by rows 3–15: the composite carries terrain + chat text and not the HUD, crosshair, sky dome or GUI. `postChains=0 postPasses=0` in every run I read, so post-processing chains are **X** (unimplemented) — `plan.md` §0.143–0.145 shows an `entity_outline` chain executing in a `glow` scene, which is not a post chain in these runs. | present/compositor owner; N04/N05 for post chains |

Cross-cutting counter, all runs above: `refusedBeforeAppend` is 0 in the GL-less runs and 6249 in
`van-bridge2`; `acceptedByFormat` is `1:2,2:37704,3:0,4:3,5:115,6:777,7:512,8:0,9:12` in
`van-glless` and all-zero in `van-bridge2` — i.e. under the bridge leg Celtium's *immediate* path
accepts nothing, because that leg's immediate calls are answered by real GL. This is why the bridge
leg is a usable reference for the immediate-mode stages (GUI, items, sky as GL draws it) and not for
terrain, which both legs route to the same Vulkan backend.

## 4. Claims I could not verify, and claims the evidence contradicts

**Contradicted by measurement.**

- `plan.md` line 51 and N10's row: the depth-format contract is "no impact on AMD, measured" and
  "the GL-less target and the terrain pipeline share the same decision". On AMD's bridge leg the
  decision is never made, all terrain pipelines throw, and no terrain is drawn. §1.
- "`n03-ae-terrain-bridge` passed" as evidence of a good reference (`PASSED` at 12:23). It passed on
  a chat block over sky with `passes=0`; its picture contains no terrain. §2.
- `plan.md` §0.126's "the bridge model-draw regression was not established" is right that a missing
  counter is not a missing draw. The bridge's *terrain* regression is established by a counter that
  does exist (`passes=0 skipped=N` plus the exception text, plus the A/B with a jar that draws it).
- The brief's "the GL bridge leg draws with real OpenGL and is the reference" is not true for the
  world: both legs' terrain goes through Celtium's Vulkan terrain backend
  (`Terrain Backend: vulkan` in the debug lines of every run I read, bridge included), and the
  bridge reference's own terrain run has `passes=1566`.

**Could not verify (no measurement exists).**

- stars, horizon, clouds, water, weather (rows 4,5,6,7,8): no run on the current tree exercises them
  or contains them; §3 names the knob and the last run that set it, where one exists.
- entities and TESR against the bridge on the current tree (rows 10,11): counters exist and are
  non-zero under GL-less, but the bridge twin that would be the comparison is either uncalibrated or
  missing terrain.
- selection outline and block-breaking overlay (rows 16,17): no run today, no counter.
- The `surface=` / panel floor counters (`panelChangedFraction`, `panelStrongFraction`,
  `panelDistinctColours`) appear in `sceneGateFloors` but I found no run whose report carries their
  measured values, so `plan.md`'s panel-floor claims are `U` from where I sit.

**Not mine to fix, reported instead.** The depth-format call ordering (row 2) is a renderer change;
I did not touch renderer sources. The gate's floor being satisfiable by a chat block (§2) is a
fixture change (`ValidationClient`).

## 5. Instruments and artifacts

- `.celtium-work/acceptance/bridge-ab.sh` — the A/B in §1. Reproduce:
  `bash .celtium-work/acceptance/bridge-ab.sh` (it takes the single game slot for ~8 minutes).
- `.celtium-work/acceptance/crops/terrain/` — `vanilla_stage_compare.py` crops for
  `van-glless` vs `scene-floors/terrain-a`.
- `.celtium-work/acceptance/crops/n03ac-vs-n03ae/`, and the stacked pairs
  `hotbar-…png`, `crosshair-…png`, `chat-…png` (GL-less on top, bridge below).
- Reused, not added: `scripts/cpu-contract/vanilla_stage_compare.py`,
  `scripts/cpu-contract/compare_jei_pair.py`, the reports' `rendererDebug` lines, and the
  `passes/skipped/skipReason` counter in `celtium/vulkan-check`.
