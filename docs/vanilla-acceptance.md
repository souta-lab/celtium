# Vanilla acceptance table — one row per vanilla stage

Owner: **acceptance** (task-10), the independent verifier of the vanilla-completion push. This file
fixes nothing; it says, per stage, what is implemented, locally verified, unverified or
unimplemented, and names the measurement behind each word. A status without a measurement is `U`,
not "working".

**Directive of 2026-10-11 13:5x: the bridge leg is no longer used. GL-less only.** The reference is
therefore one of two things, and every row says which:

- **`stored`** — a picture or a calibrated floor that already exists on disk, taken before today's
  defects (the bridge-era reference pictures, and the floors calibrated from them). It is evidence of
  what vanilla drew, and it is not re-run.
- **`self`** — a GL-less self-comparison: one jar, one flag apart (fog on/off, weather on/off,
  clouds on/off). The only comparison that can attribute a change to a flag.
- **`none`** — no reference exists, so the row is `U`.

A GL-less run compared against another GL-less run on a *different* jar is not a comparison and is
not used here. Every bridge-leg statement below is history or provenance of a stored picture.

Status vocabulary, used strictly: **V** verified locally (a named run's picture or counter decides
it, and I looked at the crop or counter); **W** wrong (implemented, measured, disagrees with the
reference); **M** measured missing (pixels a stage should produce are absent, with the crop or
counter that shows the absence); **U** unverified (no measurement decides it, with the reason and the
knob that would); **X** unimplemented.

Read `docs/handoff-vanilla-completion.md` first. Two of its premises were wrong when it was written;
§1 and §2 correct them.

## 0. This revision

Revision of 2026-10-11 ~14:05 JST. What changed since the first table (13:13):

1. **The bridge leg drew no terrain from 09:47 until `0fddd6ccf`, and the defect is fixed.** Its
   cause was a depth-format contract that only the GL-less configuration ever chose from. §1a. Every
   bridge "PASSED" in that window scored its floor on a chat block over a sky-only frame. §2.
2. **The oldest stored references are still valid**: the pre-defect pictures (`scene-floors/terrain-a`
   09:11, `glow-bridge5` 09:02, `scene-ref-terrain` 01:15) were taken while the bridge still drew the
   world, and they are what "vanilla drew" is measured against. §1b.
3. **A fresh stored reference and a matched GL-less leg were taken on one jar**
   (`acc2-ref-bridge` 13:44 + `acc2-glless` 13:47, both jar `b074fbb9b659729e`, fixture
   `0355ffcd40c22a24`). Result: **the entire frame difference is the sky.** Below row 300, four pixels
   of 460800 differ. §6, §3 rows 1/3/5/19.
4. **The harness now arbitrates the single game slot itself** (`67cf6f806`: lock file in
   `.celtium-work/game-slot` holding the owner's pid, `--slot-wait` default 900 s, stale reclaim,
   released on every exit path) after three concurrency incidents in ten minutes (13:39:20 two
   GL-less instances; 13:44 three). Runs taken under concurrency are not admissible. §4.
5. **A jar the Prism instance keeps in its own `mods/` is never copied into a test game directory**
   (`8ae2e64f8`, after `5c9bec7a1`), whatever a worker names its frozen copy. Verified copy-only
   without launching a game. §1b.

## 1. The terrain reference

### 1a. What was wrong, and why older bridge evidence is void

From 09:47 until `0fddd6ccf` (13:14) every bridge run carried
`passes=0 skipped=N skipReason=translucent:_no_pipeline` and one copy per attempt of

```
pipeline for solid|cutout_mipped|translucent failed:
  IllegalStateException: the depth format was asked for before anything chose one
```

`VulkanDepthFormat.chosen()` (no-arg) threw when nothing had chosen, and the only caller that
*chose* was `GllessFrameTarget` - GL-less only. So in the bridge leg every terrain pipeline creation
threw and every terrain pass was skipped, while Celeritas reported its sections. The separating
experiment (same fixture, scene, pose, flags, GPU, fresh directory; only the renderer jar differs)
is `.celtium-work/acceptance/bridge-ab.sh`:

| run | celtium jar | fixture | passes/skipped | nonBg | distinct | verdict |
|---|---|---|---|---|---|---|
| `a-acc-curjar-bridge` | `b874f92393f79b46` | `105598e0f89be16d` | 0 / 120 | 0.160747 | 104 | FAILED |
| `a-acc-oldjar-bridge` | `b63a8a85312027d5` | `105598e0f89be16d` | 242 / 0 | 0.735010 | 1534 | PASSED |

**Void as terrain evidence**: `n03-x-terrainbridge` (0/1068, nonBg 0.505074, *PASSED*),
`n03-ae-terrain-bridge` (0/936, 0.813402, *PASSED*), `skyfix-bridge1` (0/288, 0.149229),
`posefix-bridge` (0/177, 0.192509), `posefix2` (0/645, 0.522616), `jei-br` (0/930, 0.888320),
`van-bridge` (0/498, 0.166317), `van-bridge2` (0/1122, 0.153832), `m-entity-bridge` (0/636,
0.268383), `bridge-model` (0/447, 0.149201), `a-acc-curjar-bridge` (0/120, 0.160747). Any bridge run
whose `celtium/vulkan-check` line has `passes=0` is void for terrain whatever its verdict.

### 1b. The stored references, and the fresh one

| stored picture | time | jar | passes/skipped | distinct | nonBg | bands | bg | verdict |
|---|---|---|---|---|---|---|---|---|
| `scene-ref-terrain` | 01:15 | (not kept) | 661 / 0 | 1535 | 0.732164 | - | `0x571716` | FAILED (identity, since fixed) |
| `glow-bridge5` | 09:02 | `b63a8a85312027d5` | 909 / 0 | 1819 | 0.748878 | - | `0x571716` | PASSED |
| `scene-floors/terrain-a` | 09:11 | `478cf85555ec37a9` | 1566 / 0 | 1536 | 0.727735 | - | `0x571716` | PASSED |
| `a-acc-oldjar-bridge` | 13:13 | `b63a8a85312027d5` | 242 / 0 | 1534 | 0.735010 | - | `0x571716` | PASSED |
| `depthfix-bridge` | 13:16 | `0b8707888510d0c9` | 138 / 0 | 1534 | 0.744563 | 617 | `0x571716` | PASSED |
| **`acc2-ref-bridge`** | **13:44** | **`b074fbb9b659729e`** | **159 / 0** | **1534** | **0.744563** | **617** | **`0x571716`** | **PASSED** |

All AMD RX 6400 (RADV NAVI24), scene `terrain`, pose
`x=0.5000 y=12.0000 z=0.5000 yaw=180.000 pitch=20.000`, `captureStartTick=200`,
`sceneCalibrationKeyMatches=true`. `acc2-ref-bridge`'s screenshot is **byte-identical** to
`depthfix-bridge`'s, taken 28 minutes apart on different jar builds, one of them with two other game
instances running: the stored picture is deterministic and reproducible, so a GL-less leg can be
compared against it without re-running the bridge.

**Standing caveat.** The bridge leg's terrain is *also* Celtium's Vulkan terrain backend (every
report says `Terrain Backend: vulkan`, and these pictures have `passes` in the hundreds). A stored
bridge picture is therefore a regression reference for terrain, not an independent-OpenGL one. The
stages it *is* independent for are the ones real GL draws in the bridge: sky dome/stars/horizon,
HUD/GUI, chat, items and entity models.

**Frozen jars and hashes.** `.celtium-work/acceptance/frozen/` holds the pair used below:
celtium `b074fbb9b659729e` and fixture `0355ffcd40c22a24`. Two facts about those hashes, both
measured: the **celtium** jar is reproducible (a rebuild with unchanged sources gives the same
sha256; entries are timestamp-normalised to 1980-02-01), while the **fixture** jar is not (entries
carry real timestamps: two rebuilds with no source change gave `348b5747b02251a5` then
`0355ffcd40c22a24`). Quote the fixture hash as the bytes that ran, never as "the same fixture
build". A frozen copy can also be a commit behind: `ff68da097c102e47` was frozen by another worker as
"tree `95d74bac6`", but that commit is 13:31:48 and a build of it is `b074fbb9b659729e`; the format
table, `POSITION_NORMAL` (id 10), the sky `glDrawArrays` path and the projection mirror landed in it.

## 2. Why the gate did not catch the missing terrain

`worldFrameNonBackgroundFraction` counts pixels differing (tolerance 8) from the frame's **most
common colour**. A sky-only frame's mode is the sky, so the chat block, the toast, the tutorial box
and the arm all count as non-background.

- `n03-ae-terrain-bridge`: mode `(90,104,128)` at 10.1 % = the chat background; `distinct=732`;
  `nonBg=0.813402`; `PASSED`, with no terrain in its own picture.
- `van-bridge2`: mode `(179,207,255)` at 82.8 %; `distinct=96`; `nonBg=0.153832`; FAILED - same
  missing terrain, chat faded.
- The GL-less leg of the same minutes: `distinct=17400`, mode `(87,23,22)` = ground,
  `nonBg=0.841242`.

Hence "the bridge reference must have `nonBg >= 0.4`" (task-2/3/7/9 briefs) admits two void runs
(0.505, 0.813) and is not a validity test. A modal-colour test or `distinctColours > 1000` would be;
that is a fixture change (`ValidationClient`, N06) and is reported, not made, by me.

## 3. The table

| # | vanilla stage | status | reference | evidence | owner |
|---|---|---|---|---|---|
| 1 | terrain / chunk layers | **V** | stored | `acc2-glless` (13:47 GL-less, jar `b074fbb9b659729e`, fixture `0355ffcd40c22a24`, AMD RX 6400, scene=terrain, pose `0.5/12/0.5 yaw 180 pitch 20`, `captureStartTick=200`, `sceneCalibrationKeyMatches=true`, `PASSED`, `passes=1063 skipped=0 lastSections=28`) against the stored `acc2-ref-bridge` picture (same jar): terrain centre `(320,300,960,640)` **1 pixel of 204800** differs >8; bottom-left 1; bottom-right **0**; whole frame 28.4 % (261692 px) **all of it in the sky/horizon bands**. Means equal to 1 decimal in every region below row 300. Crops `.celtium-work/acceptance/crops/acc2/`. Caveat §1b: a regression check, not an independent-GL one. | terrain backend; depth-format defect = N10, fixed |
| 2 | sky dome | **W** | stored | Same pair. Sky band `(0,0,1280,120)`: **153600 of 153600 px (100 %)** differ >8, mean 38.1; GL-less is the **single colour** `(120,167,255)` where the stored picture has the dome's gradient `(158.1,192.9,255)` at the top. The path exists (`RenderGlobal.renderSky` → `VertexBuffer.drawArrays` → `glDrawArrays`, landed in `95d74bac6`, gate `celtium.glstub.skyFrameGate` default `capability`) and its census in this run is `glDrawArraysCalls=667 drawn=157 vertices=106132 empty=0 refused=510 pureBlueGroups=0`, refused by reason `no GL_ARRAY_BUFFER is bound (the pointer would be client memory, which the stub does not mirror)`. The star group is refused by the gate itself: `CELTIUM_SKY_REFUSAL buffer=17 count=3120 gate=capability fogEnabled=false clearColor=(0.425,0.492,0.607,0.0) colour=(0.046,0.046,0.046,0.046)`. The blue-wing artefact is gone (`pureBlueGroups=0`). | sky-draws (task-3), pipelines (task-6) |
| 3 | stars | **U** | none | `pinnedWorldTime=6000` in every report (noon, star brightness 0 by construction); no counter distinguishes a star draw. The one candidate is the refused group above (`colour alpha 0.046` = the mirrored star alpha, refused because the position shaders hardcode alpha 1.0). Needs a night scene flag, which does not exist. | sky-draws (task-3), pipelines (task-6) |
| 4 | horizon plane | **W/U** | stored | Same pair. Horizon band `(0,120,1280,300)`: **108089 of 230400 px (46.9 %)** differ >8, mean 55.1; GL-less `(129.9,135.0,170.3)` vs stored `(161.1,173.2,208.8)`. Whether that is the dome's lower gradient or the horizon plane is not separable from this pair: both live in the same band and the same gated path. | sky-draws (task-3) |
| 5 | clouds | **U** | none | A counter defect, measured: in `acc2-glless` and in `ww-gl-cloud` (13:38, jar `ff68da097c102e47`) format 8 reaches `128/128` in the **first** histogram line and never changes again, while every other id grows - i.e. the cloud batches are submitted in frame 1 and never again (`weather-water`'s measurement; I read the same sequence in both reports). No cloud pixel is identified in any GL-less picture. A self-comparison (`renderClouds` on/off, one jar) is the experiment that would decide it. | weather-water (task-9) |
| 6 | water surface | **U** | none | `ww-br-water`/`ww-gl-water` (13:37/13:38, jar `ff68da097c102e47`): both `UNCALIBRATED_PRESENTED_IMAGE` (new pose - correct, not a failure), and the bottom band `(600,720)` has the **same mean in both** `(82.1,107.1,50.4)` = grass. The GL-less picture shows flat sky over grass with no water-tinted pixel, so the run does not demonstrate water drawn or missing. Needs the camera in/over the water layer and a named crop. | weather-water (task-9) |
| 7 | weather (rain/snow) | **U** | self (not yet taken) | `ww-br-base` (13:36) vs `ww-gl-base` (13:37), same jar `ff68da097c102e47`, `weather=true particles=true`: both `PASSED`; my stage comparison gives sky 87.9 % >8 (mean 12.8), horizon 40.2 %, terrain centre 8.8 % (mean 3.6), whole frame 29.9 % (mean 7.4). Rain is stochastic between runs, so no attribution is possible without a one-flag control: weather on vs off, one jar, one tick. | weather-water (task-9) |
| 8 | particles | **U** | none | No format 5 in the GL-less runs that ask for particles: `ww-gl-base`'s histogram is `id0=99/0 id1=2/2 id2=33266/33266 id6=584/584 id7=537/528 id9=12/12` (no id5) and its `rendererDebug` has no particle line; `acc2-glless` likewise (`5:0` in `CELTIUM_ACCEPTED byFormat`). But `fogoff` (13:18, no particle flag) has `id5=48/48`, so "format 5 = particles" is not one-to-one as used, or that scene spawned none. Either way the pair does not support a particle claim. | weather-water (task-9), pipelines (task-6) |
| 9 | entities (models, renderers) | **U** | none | `m-entity-glless` (12:58, jar `ac347ff1f98e56ad`) `modelDraws=2477 modelMeshLists=38 modelQuads=228`, accepted format 9 = 228, but its only bridge twin is `UNCALIBRATED` **and** `passes=0` (void, §1a). The current attempt (`entity-runs/ent-glless` + `ent-bridge`, 13:39-13:44) ran **while two other game instances were up**, so per `AGENTS.md` its timings and pixels are inadmissible. No honest entity comparison exists on the current tree. | entity-models (task-7) |
| 10 | TESR / block entities | **U** | none | `-Dceltium.validation.enchantItem=true` was last set by `p519-lookdown` (10-10 19:34), jar not kept; no run today. `plan.md` §0.118's enchant-table match is on an older jar and does not reproduce here. | entity-models (task-7) |
| 11 | items (item models / GUI stacks) | **W/U** | stored | `jei-gl` (12:40 GL-less, jar `3a94b9dd3140…`, accepted format 4 = 4424) vs `jei-br` (12:40 bridge, same jar, `passes=0`): `compare_jei_pair.py` - inventory panel `(420,120,860,600)` **94.7 %** of pixels differ >8 (mean 89.6), whole frame 92.9 %. The *class* of difference (panel/slot backgrounds absent, item models present) is §0.157's finding; the numbers are from a pre-integration jar and the bridge leg is void for terrain, so this row needs a GL-less-only re-take. | gui-stage (task-5), pipelines (task-6) |
| 12 | GUI / HUD (hotbar, panels, slot backgrounds) | **M** | stored | Crop `(480,650)-(800,720)`, jar `216cd6284a3c22f7` both legs, tick 200: GL-less `n03-ac-terrain-deco` shows **terrain only** - no hotbar frame, no slots, no selection highlight; stored `n03-ae-terrain-bridge` shows the vanilla hotbar with 9 slots and the first highlighted. Stacked crop `.celtium-work/acceptance/crops/hotbar-glless-top-bridge-bottom.png`, which I looked at. The stored leg is void for terrain, so only the *presence* of the hotbar is decidable, not the mean colours under it. | gui-stage (task-5), texture-mirror (task-4) |
| 13 | chat | **W** | stored | Same pair: the **text** is drawn in both legs (same wrapped white text, same region - the N03 font path works); the **background quad is missing** under GL-less. Crop `.celtium-work/acceptance/crops/chat-glless-top-bridge-bottom.png`. Region means `(0,320,660,620)` `(89.2,65.6,85.5)` GL-less vs `(123.6,134.1,152.4)` stored - contaminated by the void stored terrain; read the crop. | gui-stage (task-5), texture-mirror (task-4) |
| 14 | crosshair | **M** | stored | Crop `(600,320)-(680,400)` of the same pair, `.celtium-work/acceptance/crops/crosshair-glless-top-bridge-bottom.png` (I looked at it): the stored leg shows the dark crosshair at frame centre; the GL-less leg shows terrain and text with **no crosshair**. | gui-stage (task-5) |
| 15 | selection outline (block selection box) | **U** | none | No run measures it: `allBlocks` was last set by `alive-again` (GL-less, no reference), no counter names the box, and the fixture has no knob that pins `drawSelectionBox`. | not attributed |
| 16 | block-breaking overlay | **U** | none | `-Dceltium.validation.breakBlock=true` exists in the fixture; the newest run that set it is `p471-all` (10-10 17:26), jar not kept; no run today, no counter. | not attributed |
| 17 | fog | **W** | self | Two runs one flag apart on one jar (`03c627cc52c8e566`, fixture `105598e0f89be16d`, scene=terrain): `fogoff` (13:18, `-Dceltium.vulkan.immediateFog=false`) `PASSED`, `distinct=1471 nonBg=0.667682`, `passes=459`; `fogon` (13:19, default, i.e. `immediateFog=true` at `VulkanTerrainGpuBackend.java:1603`) **`status=NO_CAPTURE`, no picture, no verdict**. So on that jar the shipped default captured nothing and only the off-switch passed. Separately, the terrain's distant fog matches a stored reference (0.2 % of the horizon band) while the sky's fog ramp is absent. | fog-path (task-2) |
| 18 | final composite / present | **V (composite) / W (content)** | stored | `acc2-glless`: `presentedImageVerdict=PASSED`, 0 assertion failures, `pictureEvidenceSource=presented-dump`, `presentedImageFrameCountAboveZero=true`. **The composite is faithful**: against the stored picture of the same jar the difference below row 300 is 4 pixels of 460800, and the whole-frame difference (261692 px, mean 20.1) is exactly the sky band (153600) plus the horizon band (108089). No darkening, no offset: the earlier "global darkening" reading was a GUI frame compared against a world frame, and this pair confirms it. Content is still incomplete because the sky is (row 2). `postChains=0 postPasses=0` in every run I read, so post-processing chains are **X** here. | frame-composite (task-8) |
| 19 | text / font (N03) | **V** | stored | `plan.md` §0.144 (`quads=52406 submitted=52406 accepted=52406 refused=0`, `unicodeWindows=40548`) plus the JEI pair's crops, where the text is the same size and position in both legs (I read those crops). Row 13's missing box is not the font. | font / N03 |

Cross-cutting counters from `acc2-glless`, which is the most recent GL-less run I read in full:

- `CELTIUM_ACCEPTED byFormat=[0:0 1:2 2:49052 … 4:3 5:0 6:888 7:792 8:128 9:12 10:0]` - id 10
  (`POSITION_NORMAL`, added in `95d74bac6`) accepted **0**; id 5 accepted **0**; id 8 frozen at 128.
- `CELTIUM_REFUSALS_BY_FORMAT {0 format 0 (unmapped) not in celtium.vulkan.immediateFormats=112}`
  and the histogram's `id0=122/0`: **112-122 batches per run are refused as an unmapped format**.
  Under GL-less a refused batch is drawn by nobody. A missing counter is not a missing draw, but
  here the counter is present and the acceptance is zero, so those batches are missing until the
  format table takes them.
- `CELTIUM_DRAWARRAYS glDrawArraysCalls=667 drawn=157 refused=510`.
- `CELTIUM_NOOP_CENSUS noopEntryPoints=46 noopCalls=582181 top=[glTexCoord2f=199184 glVertex3f=199184
  glEnd=49796 glBegin=49796 …]` - cumulative over the whole process, so it includes the menus and
  loading screens and must not be read as in-world text; named here so nobody re-derives it.

## 4. Claims contradicted by measurement

- **`plan.md` line 51 / N10**: the capability-based depth format was "no effect on AMD, measured" and
  "the GL-less target and the terrain pipeline share the same decision". The measurement behind it
  reported which format the chooser *would* pick and never ran a bridge leg, where nothing called the
  chooser. True about the format, false about the configuration. §1a.
- **"`n03-ae-terrain-bridge` passed"** as a usable reference: `PASSED` at `nonBg=0.813402` on a
  sky-only frame whose mode was the chat background. §2.
- **"a bridge run with `nonBg >= 0.4` is usable"**: two void runs scored 0.505 and 0.813. §2.
- **"the bridge leg draws with real OpenGL and is the reference"**: not for the world - every bridge
  report says `Terrain Backend: vulkan`. §1b.
- **"the GL-less frame is globally darker"** (task-8's brief): it was a GUI frame compared against a
  world frame. Against the stored picture of the same jar, the terrain centre differs by 1 px of
  204800, the bottom-right corner by 0, the HUD strip by 0, the hotbar strip by 0, the tooltip by 0,
  the chat block by 2, and every whole-frame difference is in the sky and horizon bands. No
  darkening, no offset. Independently reproduced by me on two stored references (`acc2-ref-bridge`
  and `scene-floors/terrain-a`, §6).
- **"rain (format 5) is absent from every histogram"**: it is absent from the runs that set
  `weather=true` (and from `acc2-glless`), but `fogoff`, which sets no particle flag, has
  `id5=48/48`. The claim holds for the weather scene only.
- **"clouds are accepted in frame 1 only"**: confirmed by me in two independent runs
  (`acc2-glless`, `ww-gl-cloud`): id 8 reaches 128 and never changes while every other id grows.
- **Jar hashes as content identity**: the celtium jar is reproducible, the fixture jar is not. §1b.
- **Concurrency**: 13:39:20 two GL-less instances (`entity-runs/ent-glless`, `runtime/p2-glu-on`);
  13:44 three (`fc8-br`, `tm-glless1`, `acc2-ref-bridge`). Those runs' timings and pixels are not
  admissible; the slot lock (`67cf6f806`) is the fix.
- **A transient broken harness**: at 13:45 my GL-less leg died with `SyntaxError: invalid syntax` at
  `prism_validation.py:297` while the file was mid-edit. Read that as "the instrument was being
  edited", not as a result; the leg was re-taken and is the one quoted above.

## 5. Claims I could not verify (no measurement exists)

- **stars** (row 3): no night scene; the fixture pins `worldTime=6000`.
- **clouds, water, weather, particles** (rows 5-8): the pairs that exist do not isolate their stage
  (identical 222-row profile in the cloud and water GL-less frames, grass in both water legs, no
  format 5 in the particle leg).
- **entities and TESR** (rows 9-10): the one current attempt ran concurrently; the older reference is
  uncalibrated and void for terrain.
- **selection outline, block-breaking overlay** (rows 15-16): no run today, no counter.
- **post-processing chains** (row 18): `postChains=0 postPasses=0` in every run I read.
- **the panel floor counters** (`panelChangedFraction`, `panelStrongFraction`,
  `panelDistinctColours`): they appear in `sceneGateFloors` but I found no run whose report carries
  their measured values.
- **anything not in a run's own report or in a crop I opened**: where a teammate's run is the source,
  §3 says so; I did not re-run their jar.

## 6. The matched pair, in full

Same jar both legs (`b074fbb9b659729e`), same fixture (`0355ffcd40c22a24`), same GPU, scene, pose and
tick; the only difference is `-Dceltium.vulkan.glless=true`, and the GL-less leg took the slot after
the harness lock existed (`67cf6f806`). GL-less: `worldFrameDistinctColours=1477`,
`nonBg=0.72661`, `verticalBands=509`, `blackFraction=0.0`, `bg=0x571716`, `passes=1063 skipped=0`,
`presentedImageVerdict=PASSED`. `scripts/cpu-contract/vanilla_stage_compare.py` between the GL-less
presented dump and the stored bridge screenshot:

| stage | mean | >8 | pixels>8 | GL-less mean rgb | stored mean rgb |
|---|---|---|---|---|---|
| sky band (top) | 38.108 | 100.0000 % | 153600 | (120.0,167.0,255.0) | (158.1,192.9,255.0) |
| horizon band | 55.067 | 46.9136 % | 108089 | (129.9,135.0,170.3) | (161.1,173.2,208.8) |
| terrain centre | 0.001 | 0.0005 % | **1** | (103.1,66.4,60.7) | (103.1,66.4,60.7) |
| bottom-left corner | 0.000 | 0.0020 % | 1 | (101.9,56.2,51.6) | (101.9,56.2,51.6) |
| bottom-right corner | 0.000 | 0.0000 % | **0** | (101.9,56.2,51.6) | (101.9,56.2,51.6) |
| hud strip (bottom) | 0.000 | 0.0000 % | **0** | (101.6,27.8,26.2) | (101.6,27.8,26.2) |
| chat block | 0.000 | 0.0010 % | 2 | (101.7,63.6,58.1) | (101.7,63.6,58.1) |
| hotbar strip | 0.000 | 0.0000 % | **0** | (103.6,23.2,22.2) | (103.6,23.2,22.2) |
| tooltip region | 0.000 | 0.0000 % | **0** | (103.6,64.9,59.6) | (103.6,64.9,59.6) |
| whole frame | 20.119 | 28.3954 % | 261692 | (112.3,96.5,117.1) | (126.4,110.3,126.7) |

Crops: `.celtium-work/acceptance/crops/acc2/`.

The same shape on the older stored reference (`van-glless` GL-less 12:56, jar `ac347ff1f98e56ad` vs
`scene-floors/terrain-a` 09:11, jar `478cf85555ec37a9`) - **a different jar on each side, so this is a
corroboration of the terrain result, not a valid comparison by the current rule**: terrain centre
1 px of 204800, bottom-left 1, bottom-right 0, hud strip 0, chat block 2, tooltip 0, horizon 460
(0.2 %), sky band 134870 (87.8 %), whole frame 135333 (14.68 %). The terrain and composite results
repeat across two jars and two months of tree state; the sky difference does not.

## 7. Instruments and artifacts

Reused, not added: `scripts/cpu-contract/vanilla_stage_compare.py` (per-stage diff, both sides' mean
colours, crops), `scripts/cpu-contract/compare_jei_pair.py`, the reports' `rendererDebug` lines, the
per-format histogram (`immediate formats offered/accepted: idN=x/y`), `celtium/vulkan-check`
(`passes skipped lastDraw lastSections skipReason`), `CELTIUM_ACCEPTED byFormat=`,
`CELTIUM_DRAWARRAYS`, `CELTIUM_REFUSALS_BY_FORMAT`, `CELTIUM_SKY_REFUSAL`, `CELTIUM_NOOP_CENSUS`.

Added by me, all read-only or under `.celtium-work/acceptance/`:

- `rep.py` - prints the fields this table quotes from a run's own `logs/celtium-validation.json` plus
  its lock file. Every number in §1, §3 and §6 comes from this reader, not from an ad-hoc grep.
- `acc2-pair.sh` - the matched pair, frozen jars in `frozen/`, one leg selectable
  (`pair|bridge|glless`).
- `acc2-compare.sh` - the stage comparison and counters for that pair.
- `bridge-ab.sh` - the A/B in §1a (inherited).
- Crops: `crops/acc2/` (the pair above), `crops/terrain-recheck/` (the older stored reference),
  `crops/hotbar-…png`, `crops/crosshair-…png`, `crops/chat-…png`, `crops/ww-base/`.

Instrument controls I ran, so the numbers can be trusted:

- same image twice → 0 px >8 in every region (zero-control);
- two stored bridge runs of the same scene, 28 min apart → **byte-identical**;
- two different poses → 100 % of pixels >8 (the comparator does not silently zero a real difference);
- my own recomputation of the fixture's `worldFrameNonBackgroundFraction` from the presented PNGs of
  `ww-gl-cloud`/`ww-gl-water` reproduces 0.308333 and 0.691667 exactly - the instrument reads what it
  claims.
