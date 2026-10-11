# Mod compatibility, measured (2026-10-11)

What existing Forge mods do under Celtium, split into the two things the objective keeps apart:

- **load compatibility** - the mod is identified by FML, its core plugin/tweaker runs, its own screens open, and nothing
  named after it fails;
- **real native drawing** - how many batches the mod's own geometry contributed to the immediate path, how many of them
  the Vulkan backend accepted, and which refusal reason covers the rest.

Nothing here is a prediction. `fixtureStatus=PASSED` is not used as evidence of a picture, and no counter below is
restated from another document: each is read out of the named run's own logs.

## 1. The mods that exist in this environment

| Mod | Version | Source | Draws? |
|---|---|---|---|
| Fugue | 0.24.4 | Prism instance `minecraft/mods/+Fugue-0.24.4.jar`; Cleanroom coremod | no - patches other mods |
| Scalar | 3.9.0 | Prism instance `minecraft/mods/scalar-3.9.0.jar`; Scala language adapter | no |
| Scala runtime (Scalar's contained deps) | scala-library 3.9.0, scala-parser-combinators_3 2.4.0, scala3-tasty-inspector_3 3.9.0 | Prism instance `minecraft/mods/1.12.2/` | no |
| Just Enough Items (JEI) | 1.12.2-4.22.0.1035 | `.celtium-work/major-mods/jei_1.12.2-4.22.0.1035.jar` | yes - item list, overlay, recipe screen |
| AppleSkin | mc1.12-1.0.14 | fetched from `github.com/squeek502/AppleSkin` release `v1.0.14`, sha256 `4bbd37edecff0b42...` | HUD food/hunger overlay (needs a survival HUD) |
| MapWriter (Continued) | jar 2.8.15, mod reports `mapwriter@2.8.10` | fetched from Modrinth `mapwriter-continued` 2.8.15, sha256 `e9a0551013ab785e...` | yes - minimap HUD every frame |

Fugue and Scalar live in the source instance's `mods/` folder, so the harness copies them into every isolated game
directory. JEI, AppleSkin and MapWriter are passed with `--mod`; no jar was copied into the Prism instance. The isolated
game directory each run used is `.celtium-work/runtime/<run>/`, and its `mods/` folder is the record of what was loaded:
`mod5-hud-mods/mods/` and `mod6-bridge-hudmods/mods/` are the only two that contain AppleSkin or MapWriter.

These are all the 1.12.2 mod jars on this machine outside the libraries the launcher itself needs. AppleSkin and
MapWriter were fetched because a single content mod (JEI) is not a mod set; both are named with their download source and
hash so the same jars can be reproduced.

## 2. How the numbers are produced

| Counter | What it counts |
|---|---|
| `celtium offer decisions: offers=N accepted=M glBodyCompleted=K` | uploads that reached the `WorldVertexBufferUploader` hook with a live backend, how many the backend claimed (and whose GL body was therefore cancelled), and how often the vanilla GL body ran to completion |
| `celtium immediate formats offered/accepted: id1=... id7=...` | the backend's own arrivals and acceptances per layout, printed every 500 offers: 1 POSITION_COLOR, 2 POSITION_TEX_COLOR, 3 BLOCK, 4 ITEM, 5 PARTICLE, 6 POSITION_TEX, 7 POSITION, 8 POSITION_TEX_COLOR_NORMAL, 9 POSITION_TEX_NORMAL |
| `celtium uploads: offers=N glUploads=K` | the same two counts, printed from the vanilla body's `RETURN`; this line is where `K > N` is visible |
| `CELTIUM_ARRIVALS total=... byFormat=[...]` | hook arrivals per layout, counted before any decision and therefore also for 8 and 9 |
| `CELTIUM_ACCEPTED total=... byFormat=[...]` | backend acceptances per layout, printed every 500 acceptances; sized from `VulkanImmediateFormats.COUNT`, so it also carries 8 and 9 |
| `CELTIUM_REFUSALS_BY_FORMAT {...}` | cumulative per-reason refusal tally, each reason being the backend's `lastRefusal()` plus `stage=<n>`, the checkpoint the batch stopped at |
| `screenshots/celtium-presented-*.png` + `.identity.json` | the frame Vulkan presented, read back from the swapchain (`presentReadback=true`), with the run, scene and frame counters it belongs to |
| `screenshots/celtium-validation.png` | the game's own GL screenshot (usable in the bridge; black by construction under the GL-less stub) |

Three limits of the instrument matter for everything below and are defect D2, D3/D4 and D6 in section 8:

- the per-format offered table is an `int[8]` (`VulkanTerrainGpuBackend.java:1262`), while the accepted table is sized
  10; formats 8 and 9 are therefore accepted (and arrive, per `CELTIUM_ARRIVALS`) but can never appear in the
  `immediate formats offered/accepted` line;
- a refusal whose path does not set its own reason reports the entry marker at
  `VulkanTerrainGpuBackend.java:2508`, `unrecorded (format=<id> stride=<n> mode=<m> count=<v>)`, plus `stage=`;
- the hook only sees geometry that ends in `WorldVertexBufferUploader.func_181679_a`. Immediate-mode GL
  (`glBegin`/`glVertex3f`/`glEnd`) and direct `glDrawArrays`/`glDrawElements` never pass through it - and that is how
  vanilla draws every font glyph (section 8, D1).

The offer/format lines print every 1000 offers / 500 acceptances, so they are snapshots; every table below says whether
the value is the run's last print or a mid-run snapshot. Runs are identified by the pinned sha256 of the fixture and
engine jars, recorded per run in `.celtium-work/<run>.json` (`testMods[].sha256`); from `mod5-*` on, both jars are
pinned snapshots under `.celtium-work/mod-snapshots/` (`fixture.jar`, `celtium-engine.jar`).

## 3. Run ledger

The fixture jar is rebuilt continuously by another agent, so runs are grouped by the build they used.

| Run | Configuration | Mods in the run | fixture sha256 | engine sha256 |
|---|---|---|---|---|
| `modtest-base` | GL-less, readback, no screen | Fugue, Scalar, JEI | `800823d2247f` | `e4aa6bba7dae` |
| `modtest-jei` | GL-less, readback, JEI screens requested | Fugue, Scalar, JEI | `800823d2247f` | `e4aa6bba7dae` |
| `modtest-glbridge` | bridge, readback, JEI screens requested | Fugue, Scalar, JEI | `800823d2247f` | `e4aa6bba7dae` |
| `mod2-bridge-jei` | bridge, no readback, JEI screens requested | Fugue, Scalar, JEI | `800823d2247f` | `a8ccad5f2aae` |
| `mod2-glless-nojei` | GL-less, no readback, inventory screen | Fugue, Scalar | `800823d2247f` | `a8ccad5f2aae` |
| `mod3-glless-jei` | GL-less, no readback, inventory screen | Fugue, Scalar, JEI | `9ea72bc9df85` | `9b0913a314fe` |
| `mod2-glless-pic1` | GL-less, readback, inventory screen, presented dump | Fugue, Scalar, JEI | `800823d2247f` | `a8ccad5f2aae` |
| `mod2-glless-pic2` | same, `dumpPresentedTick=150` | Fugue, Scalar, JEI | `b03ceee35123` | `89afb0cd4bbb` |
| `mod4-jei-screen-a` | GL-less, no readback, inventory **and** recipe screen | Fugue, Scalar, JEI | `99cf2e432e59` | `6dcfba123f51` |
| `mod4-nojei-screen` | lost: fixture jar copied mid-write (22 bytes) | - | `8739c76e` (the 22-byte file) | `9b0913a314fe` |
| `mod5-nojei-screen` | GL-less, no readback, inventory screen, JEI absent | Fugue, Scalar | `99cf2e432e59` | `6dcfba123f51` |
| `mod5-hud-base` | GL-less, readback, `keepGui`+`keepEntities` | Fugue, Scalar, JEI | `99cf2e432e59` | `6dcfba123f51` |
| `mod5-hud-mods` | same, plus the two fetched mods | + AppleSkin, MapWriter | `99cf2e432e59` | `6dcfba123f51` |
| `mod5-bridge-jei` | bridge, no readback, JEI screens requested | Fugue, Scalar, JEI | `99cf2e432e59` | `6dcfba123f51` |
| `mod6-bridge-hudmods` | bridge, no readback, `keepGui`+`keepEntities` | + AppleSkin, MapWriter | `99cf2e432e59` | `6dcfba123f51` |

`mod4-jei-screen-a` and the five later `mod5-*`/`mod6-*` runs are the same engine (`6dcfba123f51`) and the same fixture
(`99cf2e432e59`), which is what makes the JEI/no-JEI pair in 5.3 like-for-like. The mod jars themselves are identical in
every run: Fugue `438248925eb3`, Scalar `9fa05d84b72f`, JEI `e592b0019aa0`, AppleSkin `4bbd37edecff`,
MapWriter `e9a0551013ab`.

## 4. Load compatibility

All runs report the same shape: `Forge Mod Loader has identified 16 mods to load` (18 with the two fetched mods; 15
without JEI; 14 in the lost run), then `Client attempting to join with <n> mods : ...`. The per-mod evidence:

| Mod | Loaded | Log line that says so | Anything named after it that failed |
|---|---|---|---|
| Fugue 0.24.4 | yes | `fugue@0.24.4` at the end of the FML join list; `[Foundation]: Loading tweak name com.cleanroommc.fugue.common.LateBootstrapTweaker`; `[CleanMix]: Loading late loader [com.cleanroommc.fugue.common.FugueLateMixinLoader] for its mixins.`; `[fugue] Found status: UP_TO_DATE Target: null` | none |
| Scalar 3.9.0 | yes | `scalar@3.9.0`; `[Foundation]: Calling tweak ScalarLoadingPlugin`; its contained deps are injected: `FML has found a non-mod file scala-library-3.9.0.jar ... injected into your classpath` (also `scala-parser-combinators_3-2.4.0.jar`) | none |
| JEI 4.22.0.1035 | yes | `jei@4.22.0.1035`; `[jei]: Starting JEI...`; `Registering mod plugins took 149.0 ms`; `Building recipe registry took 79.46 ms`; `Starting JEI took 615.6 ms` (all four `modtest-base`); `FMLFileResourcePack:Just Enough Items` in the resource reload | warnings only: 16x `Potentially Dangerous alternative prefix 'minecraft' for name 'jei.shield.decoration.*'` (JEI's own item registration, 16 in every JEI run) and, in the one run that opens the recipe screen through JEI's API, 2x `[jei] Received invalid GUI properties for screen: class mezz.jei.gui.recipes.RecipesGui` |
| AppleSkin 1.0.14 | yes | `appleskin@1.0.14` in the join list; `FMLFileResourcePack:AppleSkin` in the resource reload | none |
| MapWriter 2.8.15 | yes | `mapwriter@2.8.10` (its own version string); `[mapwriter] Mw.load: loading...`; `[mapwriter] Starting version check at .../ForgeMwVersion.json`; `created new MwTexture from GL texture id 12 (512x512) (262144 pixels)` (`mod5-hud-mods`; id 9 in the bridge run `mod6-bridge-hudmods`); `processed 579 block textures, 3162 skipped, 147 exceptions`; `config/mapwriter.cfg` written with `smallmap.enabled=true` and `smallmap.coordsMode=mw.config.map.coordsMode.small` | none |

`147 exceptions` in MapWriter's own block-colour generation is the mod reporting its own texture processing; it does not
name Celtium and the mod continues to its HUD. It is also the only line in any mod run that puts the word "exception" on
the same line as a mod name.

Not one run contains an exception, Mixin failure or crash naming Fugue, Scalar, JEI, AppleSkin or MapWriter. The three
non-mod messages that do appear (`Couldn't load Narrator library`, `Error loading class: org/lwjgl/opengl/Display`,
`Potentially Dangerous alternative prefix`) are identical with and without the mods and none of them stops loading.

The two JEI errors name JEI's own `mezz.jei.gui.overlay.GuiProperties`, whose `areValid` rejects left/top/xSize/ySize
outside +-1e9; they are JEI's overlay bookkeeping for a screen it cannot classify, not a load failure of JEI and not a
Celtium message. In the mod series `mod4-jei-screen-a` is the only run in which the fixture's tick-34 hook fired, and the
same run is the only one with `jeiRecipeOpened=true` (5.3).

## 5. Native drawing

### 5.1 Every run's own counters

Last print of each line; `-` means the line never printed in that run. Formats that the offered table cannot print
(8/9) are quoted from `CELTIUM_ACCEPTED` in the text below the tables.

GL-less runs:

| Run | Mods present | offers / accepted / glBodyCompleted | per-format offered/accepted (last print) | refusal tally (last print) |
|---|---|---|---|---|
| `modtest-base` | Fugue, Scalar, JEI | 3000 / 2574 / 480 | id1=153/153 id2=307/153 id5=143/141 id6=1129/1128 id7=1256/1256 | fmt2 142, fmt5 2 |
| `modtest-jei` | Fugue, Scalar, JEI | 1000 / 853 / 203 | id1=52/52 id2=105/52 id5=50/48 id6=382/382 id7=399/398 | fmt2 49, fmt5 2 |
| `mod2-glless-nojei` | Fugue, Scalar | 13000 / 12720 / 324 | id1=1483/1483 id2=261/261 id4=6901/6900 id5=114/111 id6=2663/2663 id7=920/920 | fmt5 3 |
| `mod3-glless-jei` | Fugue, Scalar, JEI | 7000 / 6902 / 156 | id1=454/454 id2=93/93 id4=4779/4778 id5=46/38 id6=1278/1278 id7=320/320 | fmt5 8 |
| `mod2-glless-pic1` | Fugue, Scalar, JEI | 13000 / 12832 / 228 | id1=954/954 id2=170/170 id4=9239/9239 id5=81/78 id6=2444/2443 id7=582/582 | fmt5 3 |
| `mod2-glless-pic2` | Fugue, Scalar, JEI | 6000 / 5918 / 132 | id1=387/387 id2=79/79 id4=4060/4060 id5=39/33 id6=1095/1094 id7=310/310 | fmt5 6 |
| `mod4-jei-screen-a` | Fugue, Scalar, JEI (+recipe screen) | 16000 / 15698 / 346 | id1=157/157 id2=270/270 id4=9483/9483 id5=125/110 id6=4191/4190 id7=1244/1244 | fmt5 15 (12 reasons) |
| `mod5-nojei-screen` | Fugue, Scalar | 14000 / 13701 / 343 | id1=1586/1586 id2=317/316 id4=8064/8064 id5=141/128 id6=3122/3122 id7=1112/1112 | fmt5 13 (11 reasons) |
| `mod5-hud-base` | Fugue, Scalar, JEI | 15000 / 14460 / 588 | id1=987/987 id2=179/179 id4=9700/9700 id5=89/80 id6=2555/2555 id7=634/633 | fmt5 9 |
| `mod5-hud-mods` | + AppleSkin, MapWriter | 2000 / 1647 / 403 | id1=37/37 id2=123/122 id4=35/35 id5=61/40 id6=598/598 id7=418/418 | fmt5 21 (15 reasons) |
| `mod4-nojei-screen` | lost to the rebuild race | - | - | - |

Bridge runs:

| Run | Mods present | offers / accepted / glBodyCompleted | per-format offered/accepted (last print) | refusal tally (last print) |
|---|---|---|---|---|
| `modtest-glbridge` | Fugue, Scalar, JEI | 2000 / 1748 / 297 | id1=89/86 id2=179/85 id5=82/79 id6=624/595 id7=514/514 | preflights: fmt1 3, fmt2 4, fmt5 3, fmt6 28, fmt9 12 |
| `mod2-bridge-jei` | Fugue, Scalar, JEI | 1000 / 855 / 204 | id1=57/55 id2=116/54 id5=56/54 id6=403/380 id7=356/356 | preflights: fmt1 2, fmt2 3, fmt5 2, fmt6 22, fmt9 12 |
| `mod5-bridge-jei` | Fugue, Scalar, JEI | 2000 / 1465 / 586 | id1=2/0 id2=207/67 id5=100/65 id6=702/482 id7=477/476 | preflights: fmt1 2, fmt2 36, fmt5 35, fmt6 220, fmt9 12 |
| `mod6-bridge-hudmods` | + AppleSkin, MapWriter | 2000 / 1188 / 871 | id1=29/27 id2=125/34 id4=27/27 id5=62/35 id6=596/341 id7=305/248 | preflights: fmt1 2, fmt2 28, fmt5 27, fmt6 255, fmt7 56, fmt8 128, fmt9 12 |

Formats 8 and 9, which the per-format line cannot print (D2): `CELTIUM_ACCEPTED` last print shows `9:30` in every
GL-less run without a screen and `9:228` in the ones with entities (`mod5-hud-base`, `mod5-hud-mods`), with `8:128` in
`mod2-glless-nojei`, `mod5-nojei-screen` and `mod5-hud-base`. `CELTIUM_ARRIVALS` agrees: `mod5-hud-base` last print is
`byFormat=[... 8:128 9:228]`. So layouts 8 and 9 are both arriving and accepted; only the histogram is blind to them.

### 5.2 The frame itself, with every mod loaded

`modtest-base` (GL-less, readback on, Fugue+Scalar+JEI, no screens): `fixtureStatus=PASSED`,
`presentedDistinctColours=15774`, `presentedNonBackgroundFraction=0.8216`, `presentedImageAssertionFailures=0`,
`presentedImagePixels=921600`. `mod5-hud-base` (same, `keepGui`+`keepEntities`): 17629 colours, 0.8263, 0 failures. The
dumped frame `mod2-glless-pic1/screenshots/celtium-presented.png` is a complete terrain scene with sky, horizon and the
fixture's block pattern, drawn with no GL context (0 pixels darker than 12 in its upper half).

Picture caveat, not attributable to any mod: in `modtest-base`, `modtest-jei`, `mod5-hud-base`, `mod5-hud-mods` and the
no-mod run `gui-native`, the presented frame contains the same 2652-pixel black triangle in the sky
(x 801-895, y 0-35; `max(r,g,b)<12`). It is absent from `mod2-glless-pic1` and from the bridge GL screenshots, it appears
in 49 of the 168 presented dumps on this machine with and without mods, and in `harness-ev` it is present in the early
dump and gone in the world dump. It is a transient unrendered region, so these frames are "the terrain scene" and not
pixel-complete. The cause of exactly this class of shard has since been found and committed by another agent:
`celtium$buildModelMeshes()`
(`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java`, the model-mesh builder) called
`mesh.nextVertex()` once per quad instead of once per vertex, so a quad wrote its four corners into one vertex while the
indices still asked for four. Commit `454fb3600` moved the call inside the vertex loop, added a vertex/index-count
contract (the check at `:5932`), and its message names the wedges as the corruption that shape produced. Every run cited
in this document predates that commit, which is why the shards are still in these frames.

The frames this document cites, copied byte-for-byte out of the run directories so the picture claims are checkable
without the gitignored work tree:

| File here | Source in the run | What it shows |
|---|---|---|
| `mod-compat-2026-10-11-glless-pic1-world.png` (sha256 `127b0581d491...`) | `mod2-glless-pic1/screenshots/celtium-presented.png` | GL-less terrain scene, 16 mods, no wedge: the "complete frame" claim of 5.2 |
| `mod-compat-2026-10-11-glless-pic2-jei-inventory.png` (sha256 `b6d7d6ed1846...`) | `mod2-glless-pic2/screenshots/celtium-presented-early-...png` | GL-less, inventory open, JEI's item list drawn, no text anywhere: 5.3 and D1 |
| `mod-compat-2026-10-11-glless-hudmods-world.png` (sha256 `581cb189204d...`) | `mod5-hud-mods/screenshots/celtium-presented-world-...png` | GL-less, 18 mods, `keepGui`: terrain only, no chat/toast/coordinate text, no hotbar |
| `mod-compat-2026-10-11-bridge-hudmods-gl.png` (sha256 `8b8f01d0804f...`) | `mod6-bridge-hudmods/screenshots/celtium-validation.png` | the same scene in the bridge, real GL: chat, tutorial toast and MapWriter's `0, 12, 0` are present, the hotbar is not |

### 5.3 JEI's own drawing

The fixture's own marker is not evidence of JEI. `jeiScreenOpenedAtTick=30` is written by the `worldTicks == 30` branch of
`ValidationClient` that calls `displayGuiScreen(new GuiInventory(mc.player))`
(`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java`, `:2751-2752` as this is
written - the fixture is rebuilt continuously, so its line numbers drift), whenever the flag
`celtium.validation.jeiScreen=true` is set, without
consulting the FML mod list: `mod2-glless-nojei` and `mod5-nojei-screen` carry `jeiScreenOpenedAtTick=30` with **15 mods
and no `jei@` in the join list at all**, while three runs that do set the flag (`modtest-jei`, `mod5-hud-mods`,
`mod5-bridge-jei`) do not carry the field in their report at all. What JEI itself produced is:

- `mod4-jei-screen-a`: `jeiRecipeOpened=true` - the fixture's tick-34 hook called JEI's own
  `IJeiRuntime.getRecipesGui().showCategories(uid)` (`CeltiumJeiPlugin.openFirstRecipeCategory`), which cannot answer
  true without JEI's runtime. JEI then logged its two `Received invalid GUI properties` errors for `RecipesGui`
  (section 4), so the screen's own pixels are not established by any picture: the run had the readback off.
- `mod2-glless-pic2`: the presented early dump at tick 153 with the inventory screen open
  (`screenshots/celtium-presented-early-run-125246168c568-66d3c617.png`, 21 presented frames by then) contains JEI's
  ingredient list - a separate right-hand grid of item stacks, the only UI mod in the run - drawn natively with no GL
  context. The same frame contains no text at all (no GUI title, no JEI search field) and large black regions, which is
  D1 plus the small frame count, not a JEI failure.

Like-for-like pair, same engine (`6dcfba123f51`) and fixture (`99cf2e432e59`), same GL-less configuration, same fixture
flags, the only differences being JEI's presence and its screens being open:

| Run | JEI | Frames | offers / accepted | ITEM (4) | POSITION_TEX (6) | POSITION_COLOR (1) |
|---|---|---|---|---|---|---|
| `mod5-nojei-screen` | absent | 184 | 14000 / 13701 | 8064 / 8064 | 3122 / 3122 | 1586 / 1586 |
| `mod4-jei-screen-a` | present + recipe screen | 165 | 16000 / 15698 | 9483 / 9483 | 4191 / 4190 | 157 / 157 |

Per presented frame the same pair reads: accepted 74.5 -> 95.1, ITEM 43.8 -> 57.5, POSITION_TEX 17.0 -> 25.4,
POSITION_COLOR 8.6 -> 1.0. The ITEM layout is the one item stacks are drawn with (`docs/scope-and-limits.md` section 1),
and in every GL-less JEI run its acceptance was essentially total: `9483/9483` (`mod4-jei-screen-a`), `9239/9239`
(`mod2-glless-pic1`), `4060/4060` (`mod2-glless-pic2`), `4779/4778` (`mod3-glless-jei`), and `8064/8064` in the no-JEI
half of the pair. No JEI run has a refusal whose reason names anything but the particle family (5.4).

### 5.4 What Celtium refused, with the renderer's own reason

Three families, all of them the game's own geometry rather than a mod's:

| Family | Reason string (verbatim, one example) | Where | Size |
|---|---|---|---|
| PARTICLE (format 5), single batches | `5 unrecorded (format=5 stride=28 mode=7 count=8924) stage=10` | every GL-less run, with and without JEI | 2-21 batches per run, one tally entry per distinct vertex count (`mod4-jei-screen-a`: 12 reasons, 15 batches; `mod5-hud-mods`: 15 reasons, 21 batches) |
| POSITION_TEX_COLOR (format 2) single quads | `2 unrecorded (format=2 stride=24 mode=7 count=4) stage=3` | the GL-less **readback** runs with no screen open | `modtest-base`: 142 of 307 format-2 arrivals; `modtest-jei`: 49 of 105 |
| bridge preflights | `preflight: format=6 texture=28 (no pass has run yet in this instance)` | bridge runs only, all run long | `mod5-bridge-jei`: format 6 x220 in five reasons (72/38/37/37/36), format 2 x36, format 5 x35, format 9 x12; `mod6-bridge-hudmods`: format 6 x255 (eight reasons), format 8 x128, format 7 x56, format 2 x28, format 5 x27, format 9 x12 |

What each family is, from the runs and the code:

- **Format 5** is `ParticleManager.renderParticles` (`WorldVertexBufferUploaderMixin` prints
  `CELTIUM_FMT5_SITE <- ... <- ParticleManager.func_78874_a:371 <- EntityRenderer.func_175068_a:1488`). The fixture's own
  FLAME particles were **not** spawned in these runs (`celtium.validation.particles` is not set in any mod run), so this
  is the game's ambient particle traffic. The refusing condition is the particle descriptor gate
  (`VulkanTerrainGpuBackend.java:3114`, `canBuildDescriptor` false), and the reason never reaches the tally (D3b). Its
  cause was found and fixed after these runs: commit `1e08cfd60` ("particles: the descriptor probe took a pass the draw
  path would never use") - the probe demanded `pipelineFor(lastUiPass)`, which is null until the first terrain pass,
  where the draw path substitutes the standalone frame-compatible pass; the commit reports the probe refusals gone
  (35 -> 0) and format 5 offered/accepted `73/73` in its own GL-less run. These mod runs predate it.
- **Format 2** with `count=4` is one quad - the game's own screen composite. Under the GL-less stub it is refused on
  purpose (`VulkanTerrainGpuBackend.java:2768-2773`: the quad samples the target's own colour image, and the swap point
  has already presented the frame), so the refusal is correct and only its *reason* is missing (D3a).
- **Bridge preflights** are `prepareImmediate` refusing because `lastUiPass == null`
  (`VulkanTerrainGpuBackend.java:3890`): the bridge renders terrain with OpenGL, so no Vulkan pass has run in the
  instance and every GUI/HUD batch is refused. They are not startup-only - the counts grow to the end of the run - and
  in the bridge GL draws them, so nothing is lost. They do mean a bridge run cannot measure native drawing of the GUI
  layouts at all.

Under the GL-less stub a refusal is not a fallback: `CeltiumGlStub` documents that "everything else records and no-ops -
drawing, blending and state setting" (`CeltiumGlStub.java:48`) and no-ops `glDrawArrays`/`glDrawElements` too
(`CeltiumGlStub.java:326-328`). `glBodyCompleted` counts the vanilla body running; under the stub that body draws
nothing, so the refused particle batches are lost in GL-less runs.

Two honest gaps in the instrument itself:

- both GL-less reason strings begin with `unrecorded`: the backend sets that placeholder on entry and only some refusal
  paths overwrite it, so these refusals are counted but not named by the code that made them (D3);
- the three numbers on the offer line do not close: `glBodyCompleted` is *larger* than `offers - accepted` in every run
  (`mod4-jei-screen-a`: 346 vs 302; `mod5-hud-base`: 588 vs 540; `mod6-bridge-hudmods`: 871 vs 812), and
  `celtium uploads: offers=500 glUploads=537` in `mod6-bridge-hudmods` shows more vanilla bodies completing than uploads
  offered at all. Uploads the hook sees while no backend is current are counted by neither `offers` nor the refusal
  tally, so "how much was replaced" cannot be read off one line (D6).

### 5.5 Mods that loaded but did not draw here

- **AppleSkin 1.0.14**: no AppleSkin geometry can be identified. The fixture hard-codes `GameType.CREATIVE` when it
  creates the world (the two `WorldSettings(seed, GameType.CREATIVE, false, false, WorldType.FLAT)` calls in
  `ValidationClient`, `:1987` and `:2243` as this is written); in creative `PlayerController.shouldDrawHUD()` is false, so
  the hunger bar is not drawn and the overlay AppleSkin attaches to it has nothing to attach to. This is the scene, not
  Celtium: the mod loaded, and no Celtium counter mentions it. No flag exists to make the fixture survival, so this is
  not re-runnable as it stands.
- **MapWriter 2.8.15**: its smallmap is enabled in its own config (`smallmap.enabled=true`,
  `smallmap.coordsMode=mw.config.map.coordsMode.small`), it created its 512x512 map texture, and its coordinate readout
  **is** on screen in the bridge GL screenshot `mod6-bridge-hudmods/screenshots/celtium-validation.png` (the white
  `0, 12, 0` at the top right). The circular minimap is absent from that GL screenshot as well as from the GL-less
  presented frame (`mod5-hud-mods/screenshots/celtium-presented-world-*.png`), so - if it is a failure - it is present
  with a real GL context too and this evidence does not attribute it to the native path. The readout is drawn with the
  vanilla font, which is immediate mode, so in GL-less it is lost with all other text (D1); the same bytes in the bridge
  prove the mod's HUD code runs.
- **The HUD itself is not in any frame.** `mod5-hud-base` (no HUD mods, `keepGui=true`), `mod5-hud-mods` (GL-less) and
  `mod6-bridge-hudmods` (the game's own GL screenshot, `keepGui=true`) all show terrain with **no hotbar and no
  crosshair**; the bottom-centre strip (x 400-880, y 620-720) is identical terrain in all three, and it contains no
  dark rectangles that a black-textured hotbar would leave. The bridge screenshot *does* show the chat lines and the
  tutorial toast, so `hideGUI` was false and `GuiIngame.renderGameOverlay` ran in that frame; the hotbar's absence is
  therefore not explained by the fixture hiding the HUD, and because it is identical with real GL it is **not** evidence
  about Celtium's native path. Its consequence is real either way: mod HUD overlays cannot be judged from these frames,
  which is why AppleSkin and MapWriter are reported as load-only (AppleSkin) or GL-only (MapWriter) findings above.

## 6. What could not be tested, and why

- **Any mod other than the five above.** There is no other 1.12.2 mod jar in use: the Prism instance ships Fugue and
  Scalar (+ the Scala runtime), `.celtium-work/major-mods/` holds only JEI, and a search of the home tree finds nothing
  else. AppleSkin and MapWriter were fetched to widen the set; a broader mod manifest (the plan's G4) is still missing.
  One fetched jar, `InvHUD_Configurable-1.0.1.jar`, sits in `.celtium-work/mod-snapshots/` and is in no run's lock - it
  was never loaded, so nothing here says anything about it.
- **Intel GPU.** Every run used `/dev/dri/by-path/pci-0000:03:00.0-render` (AMD RX 6400). No Intel run was made in this
  series.
- **The fixture's per-tick hooks vs the presented picture.** In the GL-less configuration the two cannot be had
  together: with `presentReadback=true` a frame costs ~100 ms, `mod5-hud-mods` draws 76 frames for 203 ticks, and the
  fixture's `frame()` hooks keyed on `worldTicks == 30/34` are stepped over (in the mod series the tick-34 recipe hook
  answered true only in `mod4-jei-screen-a`, and that run has no picture); with the readback off the hooks fire but the
  run has no presented image at all
  and ends `FAILED_PRESENTED_IMAGE`. Both configurations were run and each number above names which one it came from.
- **Per-batch attribution to a mod.** The immediate path has no per-caller tag, so "JEI drew N batches" is measured as
  the difference between two runs of the same scene with and without the mod, normalised per presented frame, plus the
  picture. That is why the JEI pair and not a single run is the evidence.
- **AppleSkin's and MapWriter's own scenes.** Both need content the fixture does not build: survival hunger for
  AppleSkin (creative is hard-coded), and a working smallmap for MapWriter (absent even in GL).
- **One run was lost to a rebuild race.** `mod4-nojei-screen` copied the fixture jar while another agent was writing it
  (22 bytes on disk), FML reported `FML has found a non-mod file celtium-validation-client-...jar`, the game stayed in
  the main menu, and the run ended `MISSING_REPORT`, `endedBy=time-limit`, `exitCode=143` after 120.3 s. Every later run
  used a pinned, `unzip -t`-verified snapshot.
- **Fixture verdicts.** The `FAILED_PRESENTED_IMAGE` verdicts in the mod runs are of three different kinds and none of
  them is evidence about a mod:
  - `mod3-glless-jei`, `mod4-jei-screen-a`, `mod5-nojei-screen` - GL-less with the readback off: the verdict says
    "no picture was requested for scene=terrain stage=world" and the report carries the same
    `pictureEvidenceProblem` ("the game's own screenshot is black by construction ... so this run has no picture of the
    window at all"). Their counters are unaffected and are used; their verdict is not.
  - `mod2-glless-nojei` - the same, plus `presentedImageFramesAtCapture=156 is not within one of worldFrames=158`, and
    no dumped image exists.
  - `mod5-bridge-jei` and `mod6-bridge-hudmods` - these two *do* have a usable picture
    (`pictureScreenshotUsable_world=true`): verdict is `FAILED: the picture ... is the frame the renderer presented at
    83, which is -37 away from the 120 world frames the capture recorded` (and 54 vs 83, -29, in `mod6`). That is the
    old +/-1 frame gate comparing the bridge's GL present counter with the world-render counter - two counters that
    measure different things in the bridge - and the picture itself is a complete terrain scene. A gate that counts
    frames is not a rendering failure and not evidence about a mod.
  - The mod5 runs' scene gate is the uncalibrated placeholder
    (`sceneGateFloors = distinctColours>16 nonBackgroundFraction>=0.0 verticalBands>=0 blackFraction<=1.0 ...`), so
    their `PASSED` says only "not a single colour, and the frame is one the renderer counted". Another run on this
    machine with the same camera (`glow`, GL-less, `keepEntities`) failed the same gate on a real floor:
    `presentedBlackFraction=0.335213 is above the scene ceiling of 0.05`. A `PASSED` in the mod runs is therefore weak
    evidence; the picture claims above rest on the dumped PNGs and their identity sidecars, not on the verdict.

## 7. Findings: per mod, load versus native drawing

The immediate path has no per-caller tag (section 6), so "native drawing" below is per run and per layout, with the
mod-attributable part named as such.

| Mod | Load | Its own screens | Native geometry attributable to it | Classification |
|---|---|---|---|---|
| Fugue 0.24.4 | yes, every run (`fugue@0.24.4`, LateBootstrapTweaker, FugueLateMixinLoader) | none of its own | none - it patches other mods at load time | nothing to measure; no failure named after it |
| Scalar 3.9.0 (+ deps) | yes, every run (`scalar@3.9.0`, `Calling tweak ScalarLoadingPlugin`, scala jars injected) | none of its own | none | nothing to measure; no failure named after it |
| JEI 4.22.0.1035 | yes, every run that includes it (`jei@4.22.0.1035`, `Starting JEI...` 615.6 ms in `modtest-base`) | recipe screen reached through JEI's API in `mod4-jei-screen-a` (`jeiRecipeOpened=true`); inventory screen is the fixture's, and its marker is not JEI evidence | ITEM (4) accepted in full in every run that has it: 9239/9239 (`mod2-glless-pic1`), 9483/9483 (`mod4-jei-screen-a`), 4060/4060 (`mod2-glless-pic2`), 4779/4778 (`mod3-glless-jei`); the no-JEI half of the pair is 8064/8064 | no refusal names JEI; its labels/search field are lost in GL-less (D1) |
| AppleSkin 1.0.14 | yes, in the two runs that include it (`appleskin@1.0.14`) | none of its own | none identifiable: creative fixture, no hunger HUD | scene, not a defect |
| MapWriter 2.8.15 | yes, in the two runs that include it (`mapwriter@2.8.10`, `Mw.load`, 512x512 MwTexture) | HUD only | coordinate readout present in the bridge GL screenshot; minimap absent in bridge **and** GL-less; no counter names MapWriter | minimap: mod/scene; readout: lost in GL-less (D1) |

Per mod, in the runs' own numbers:

- **Fugue.** Every run loads it (`mod5-hud-mods/logs/latest.log`: `Loading tweak name
  com.cleanroommc.fugue.common.LateBootstrapTweaker`, `Loading late loader
  [com.cleanroommc.fugue.common.FugueLateMixinLoader]`, `fugue@0.24.4` in the 18-mod join list). It has no renderer of
  its own, so no counter can attribute geometry to it; what it does is patch other mods, and the five mods here all load
  and behave with it present. No exception, Mixin failure or crash in any run names Fugue.
- **Scalar.** Same shape: `Calling tweak ScalarLoadingPlugin`, `scalar@3.9.0`, and its contained dependencies injected
  (`scala-library-3.9.0.jar`, `scala-parser-combinators_3-2.4.0.jar`). Nothing to measure, nothing failed.
- **JEI.** Load is not in question. Drawing: the ITEM layout is the item-stack layout, and its acceptance is total in
  every GL-less run that has JEI (`9483/9483` etc. above, and no format-4 refusal in any run's tally). The like-for-like
  pair (`mod4-jei-screen-a` 165 frames vs `mod5-nojei-screen` 184 frames) puts the difference at +20.6 accepted batches
  per frame, of which the item layout is +13.7 (43.8 -> 57.5) and POSITION_TEX +8.4 (17.0 -> 25.4) - the shape an
  item-heavy recipe screen would add. The picture that carries the claim is `mod2-glless-pic2`'s early dump (5.3).
  Caveat, and it is a JEI-side
  one: the fixture's `showCategories` call returned true, but the run that made it also logged JEI's two
  `GuiProperties` errors for `RecipesGui`, and no run has a picture of the recipe screen, so "JEI's recipe screen drew
  natively" is inferred from the item-layout counters and the inventory-screen picture, not photographed.
- **AppleSkin.** Loaded (`appleskin@1.0.14` in the 18-mod list of `mod5-hud-mods` and `mod6-bridge-hudmods`), no screen
  of its own, no geometry that can be identified. The like-for-like HUD pair `mod5-hud-mods` vs `mod5-hud-base` (same
  engine, fixture and flags; the only difference is the two fetched mods) differs in 2799 pixels >16 (2725 of them >64)
  out of 921600, all in one vertical band, and the two runs captured a different tick (203 vs 201), so the difference is
  not separable from scene animation. Root cause of "no AppleSkin geometry": the fixture cannot reach a survival HUD
  (`GameType.CREATIVE` hard-coded at `ValidationClient`'s two `WorldSettings` calls), not a Celtium refusal - no refusal in any
  run names AppleSkin or a food/HUD layout.
- **MapWriter.** Loaded (`mapwriter@2.8.10`, `Mw.load: loading...`, block-colour pass, 512x512 `MwTexture`,
  `smallmap.enabled=true`). Its coordinate readout is visible in the bridge GL screenshot and absent from the GL-less
  presented frame; its minimap is absent from both. No Celtium counter names it, and no refusal is attributable to it.
  The one thing this run does establish for MapWriter is the D1 case: its HUD text is drawn with the vanilla font, so
  under GL-less it cannot appear at all.

## 8. Defects the runs expose, ranked by what they block

Each defect names the run, the counter and the value that carries it, and the site. All of these are read from the runs
of 2026-10-11 01:10-01:24 (pinned fixture `99cf2e43` / engine `6dcfba12` for the `mod4`/`mod5`/`mod6` runs), and the tree
has moved since: `1e08cfd60` (particles, 09:08) fixes D4 and the specific silent refusal behind D3b, `454fb3600` fixes the
model-mesh corruption the frames' shards were made of (5.2), and the fixture's picture gate is being calibrated in the
working tree. D1, D2 and D3a are unchanged at HEAD. The numbers below are the runs' numbers and are not revised by those
commits; where a defect is fixed, it says so.

**D1 - Immediate-mode GL is a no-op under the GL-less stub, so every mod's text is lost, and the offer counters cannot
see it.** `CeltiumGlStub`
(`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java`) installs its no-op dispatch for
`glBegin`, `glEnd`, `glVertex3f`, `glTexCoord2f`, `glColor4f` and the direct draw calls (`:313`, `:326-328`; the class note
is at `:48`), while vanilla draws every glyph through exactly those
calls (`FontRenderer.renderDefaultChar`, `glBegin(5)`/`glVertex3f`/`glEnd`,
`forge122/build/rfg/minecraft-src/java/net/minecraft/client/gui/FontRenderer.java:238-247`; same for
`renderUnicodeChar` at `:292-301`). The offer hook only sees uploads that end in
`WorldVertexBufferUploader.func_181679_a`
(`forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java:120`,
`celtium$offerBatch`), so this geometry is not offered, not accepted, not refused and not counted
anywhere. Evidence: the bridge GL screenshot of `mod6-bridge-hudmods` contains the chat lines, the tutorial toast and
MapWriter's `0, 12, 0`; the GL-less presented frame of the same scene, same 18 mods, same fixture
(`mod5-hud-mods/screenshots/celtium-presented-world-run-125aef755ef07-66d3c617.png`) contains none of them, while its
own counters show the layouts that *do* go through the hook accepted and unrefused (`id2=123/122` and no format-2
refusal in that run - the font is not among them, because it never reaches the hook at all);
`mod2-glless-pic2`'s early dump draws JEI's item icons but no title, no search field. Blocks: judging any mod's
text/HUD output under GL-less (MapWriter's readout, JEI's labels, every mod that uses `FontRenderer`), and it makes the
counter set look complete when an entire class of geometry is missing from it. Not visible in any counter, so it cannot
be found by reading the tables - only by looking at a picture and reading the stub.

**D2 - The per-format offered table is truncated at 8, so layouts 8 and 9 can never appear in it.**
The field `immediateOfferedByFormat` is `new int[8]` (`VulkanTerrainGpuBackend.java:1262`) while
`immediateAcceptedByFormat` is `new int[VulkanImmediateFormats.COUNT]` (=10, `:1263`), and the histogram in
`submitImmediateBatchTimed` iterates the offered array (`:3175-3177` at this revision). Evidence: `CELTIUM_ACCEPTED` shows `9:30` in every
GL-less run and `8:128 9:228` in
`mod5-hud-base`/`mod5-hud-mods`, `CELTIUM_ARRIVALS` agrees (`mod5-hud-base` last print `8:128 9:228`), and no
`immediate formats offered/accepted` line anywhere on this machine prints `id8=` or `id9=`. The class comment in
`VulkanImmediateFormats` describes this exact mistake being fixed for two other counters, so the offered array is the
one that was missed. Blocks: reading the arrival/acceptance of the entity and composite layouts from the table this
document is built on; any per-format budget computed from that line silently omits two layouts.

**D3 - Two refusal paths do not name themselves, so the tally reads `unrecorded`.**
(a) The format-2 composite under GL-less: `submitImmediateBatchTimed` at `VulkanTerrainGpuBackend.java:2768-2773` refuses
(`else { return false; }`) without setting a reason, while the other branch clears the marker (`:2770`) for the case that
falls through to the
acceptance mask. The refusal itself is deliberate (the quad samples the current target's own colour image and the swap
point already presented the frame), so this is a
reasoning defect only. Evidence: `modtest-base` `2 unrecorded (format=2 stride=24 mode=7 count=4) stage=3`, 142 of 307
format-2 arrivals; `modtest-jei` 49 of 105.
(b) The particle descriptor: `submitImmediateBatchTimed` at `VulkanTerrainGpuBackend.java:3114-3128` refuses when
`canBuildDescriptor` is false, and its
own sentence (`:3122`) is unreachable because the guard at `:3121` tests `immediateLastRefusal == null ||
isEmpty()` while the entry marker set in `submitImmediateBatch` at `:2508` is never empty. `canBuildDescriptor`
(`:2380`) records its cause in
`particleDescriptorFailureReason` (`:2436`) but not in `immediateLastRefusal`. Evidence: every particle refusal in every
mod run reads `5 unrecorded (format=5 stride=28 mode=7 count=<n>) stage=10`, e.g. `mod4-jei-screen-a` 12 reasons/15
batches, `mod5-hud-base` 9/9, `mod2-glless-nojei` 3/3. Blocks: knowing why the particle layout is refused; the
`particleDescriptorFailureReason` the code already computes never reaches the run log. **The specific refusal these runs
recorded has since been fixed** by `1e08cfd60` (the probe took a pass the draw path would never use, 5.4): the probe
refusals are gone and the branch that could return silently now names itself. What remains at HEAD is the shape only -
the guard at `:3121` cannot fire while the entry marker is set, so a *different* silent return inside
`canBuildDescriptor` would still read `unrecorded`.

**D4 - Refused particle batches are lost, not drawn, under GL-less (fixed at HEAD after these runs).** The refusal at
D3b means the backend never takes
the batch, and its GL body is the stub, which draws nothing (`CeltiumGlStub.java:48`, `:326-328`). `glBodyCompleted`
counts the body running, which under GL-less is not a fallback. The layout is in the default acceptance mask
(`VulkanImmediateFormats.DEFAULT_ACCEPTED = "1,...,9"`), and accepted particle batches do exist (`id5` 33-141 accepted
per run), so this was a partial loss of one layout, not a whole-family gap. Evidence: per-run particle refusals above, with
`glBodyCompleted` on the same print (`mod4-jei-screen-a`: offers=16000 accepted=15698 glBodyCompleted=346). Smaller than
D1 in scope but the same shape: geometry the instrument counts as refused and the GL-less configuration cannot draw.
Commit `1e08cfd60` removes this family (its run: format 5 offered/accepted `73/73`, probe refusals 0), so a re-run with
the current tree should show no format-5 refusal at all.

**D5 - The bridge refuses every GUI/HUD layout for the whole run, so a bridge run measures no native GUI drawing.**
`prepareImmediate` (`VulkanTerrainGpuBackend.java:3866`) refuses while `lastUiPass == null` (reason built at `:3890`,
`preflight: format=<id> texture=<n> (no pass has run yet in this instance)`), and the bridge renders terrain with
OpenGL, so the condition never clears. Evidence: `mod6-bridge-hudmods` - format 6 x255, format 8 x128, format 7 x56,
format 2 x28, plus 5/9; `mod5-bridge-jei` - format 6 x220, format 2 x36. These are not startup-only: the tally grows
from the first print to the last. Blocks: using any bridge run as evidence that a mod's GUI/HUD geometry is drawn
natively (it is not even offered to a pass); in the bridge GL draws it, which is why the bridge picture still carries
the text evidence in D1.

**D6 - The offer arithmetic does not close, and the never-offered uploads are invisible.**
`glBodyCompleted > offers - accepted` in every run (`mod4-jei-screen-a` 346 vs 302; `mod5-hud-base` 588 vs 540;
`mod2-glless-nojei` 324 vs 280; `mod6-bridge-hudmods` 871 vs 812), and `celtium uploads: offers=500 glUploads=537` in
`mod6-bridge-hudmods` proves bodies completing that were never counted as offers. The code path is between the two
injectors: `celtium$offerBatch` returns before `celtium$offers++` when `TerrainGpuBackends.current()` is null or the
buffer is null (`WorldVertexBufferUploaderMixin.java:120-125`), while `celtium$countGlUpload` counts every completed
body (`:112-113`). Blocks: reading "how many batches were replaced" off one line, and separating "refused" from "never
offered" - the two have different fixes and the tally cannot tell them apart.

**D7 - The picture verdicts cannot be used as evidence, in three different ways.** (a) GL-less without readback: "no
picture was requested", reported as `FAILED_PRESENTED_IMAGE` (`mod3-glless-jei`, `mod4-jei-screen-a`,
`mod5-nojei-screen`, and `mod2-glless-nojei` with no dumped file), which is a configuration property, not a rendering
result. (b) Bridge: the verdict fails on a frame-counter comparison (`mod5-bridge-jei` "-37 away from the 120 world
frames", `mod6-bridge-hudmods` "-29") while `pictureScreenshotUsable_world=true` and the image is a complete scene - the
gate's counters measure different things in the bridge. (c) The scene gate in the mod5 runs is the uncalibrated
placeholder (`sceneGateFloors` all `>=0`/`<=1`), so `PASSED` there means only "not one colour and a counted frame";
elsewhere on this machine the same gate fails on a real floor (`glow`: `presentedBlackFraction=0.335213 is above the
scene ceiling of 0.05`), and the gate is being given real per-scene floors in the working tree (another agent's runs
report a calibrated `PASSED`). The mod pictures in this document are therefore cited by file and identity sidecar, and no
claim above rests on a verdict.

Not ranked with the renderer defects, because no mod is implicated: the 2652-pixel black sky triangle present in 49 of
168 presented dumps with and without mods (5.2) is a transient unrendered region and a caveat on using those frames as
"the complete scene". Its cause is the model-mesh vertex contract fixed by `454fb3600` (`celtium$buildModelMeshes`,
5.2); none of the runs here includes that fix, which is why the shards are still in the frames they contributed.
