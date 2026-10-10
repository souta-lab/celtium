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
directory. JEI, AppleSkin and MapWriter are passed with `--mod`; no jar was copied into the Prism instance.

These are all the 1.12.2 mod jars on this machine outside the libraries the launcher itself needs. AppleSkin and
MapWriter were fetched because a single content mod (JEI) is not a mod set; both are named with their download source and
hash so the same jars can be reproduced.

## 2. How the numbers are produced

| Counter | What it counts |
|---|---|
| `celtium offer decisions: offers=N accepted=M glBodyCompleted=K` | uploads the `WorldVertexBufferUploader` hook saw, how many the Vulkan backend claimed, how often the vanilla GL body still ran |
| `celtium immediate formats offered/accepted: id4=6901/6900 ...` | arrivals and native acceptances per layout: 1 POSITION_COLOR, 2 POSITION_TEX_COLOR, 3 BLOCK, 4 ITEM, 5 PARTICLE, 6 POSITION_TEX, 7 POSITION, 8 POSITION_TEX_COLOR_NORMAL, 9 POSITION_TEX_NORMAL |
| `CELTIUM_REFUSALS_BY_FORMAT {...}` | each backend refusal with the renderer's own reason string plus `stage=` |
| `CELTIUM_ACCEPTED total=... byFormat=[...]` | accepted batches per layout, printed every 500 |
| `screenshots/celtium-presented-world-*.png` | the frame Vulkan presented, read back from the swapchain (`presentReadback=true`) |
| `screenshots/celtium-validation.png` | the game's own GL screenshot (usable only in the bridge) |

The offer/format lines print every 1000 offers / 500 acceptances, so they are snapshots; every table below says whether
the value is the run's last print or a mid-run snapshot. Runs are additionally identified by the pinned sha256 of the
engine and fixture jars in each `--output` lock JSON.

## 3. Run ledger

The fixture jar is rebuilt continuously by another agent, so runs are grouped by the build they used. From
`mod5-*` on, both jars are pinned snapshots under `.celtium-work/mod-snapshots/`.

| Run | Configuration | Mods in the run | Fixture sha256 |
|---|---|---|---|
| `modtest-base` | GL-less, readback, no screen | Fugue, Scalar, JEI | `800823d2...` |
| `modtest-jei` | GL-less, readback, JEI screens requested (hooks missed) | Fugue, Scalar, JEI | `800823d2...` |
| `modtest-glbridge` | bridge, readback, JEI screens requested (hooks missed) | Fugue, Scalar, JEI | `800823d2...` |
| `mod2-glless-nojei` | GL-less, no readback, inventory screen | Fugue, Scalar | `800823d2...` |
| `mod3-glless-jei` | GL-less, no readback, inventory screen | Fugue, Scalar, JEI | `9ea72bc9...` |
| `mod2-glless-pic1`, `pic2` | GL-less, readback, inventory screen, presented dump | Fugue, Scalar, JEI | `800823d2...`, `b03ceee3...` |
| `mod4-jei-screen-a` | GL-less, no readback, inventory **and** recipe screen | Fugue, Scalar, JEI | `99cf2e43...` |
| `mod5-nojei-screen` | GL-less, no readback, inventory screen, JEI absent | Fugue, Scalar | `99cf2e43...` |
| `mod5-hud-base` | GL-less, readback, `keepGui`+`keepEntities` | Fugue, Scalar, JEI | `99cf2e43...` |
| `mod5-hud-mods` | same, plus the two fetched mods | + AppleSkin, MapWriter | `99cf2e43...` |
| `mod5-bridge-jei` | bridge, no readback, JEI screens requested | Fugue, Scalar, JEI | `99cf2e43...` |
| `mod6-bridge-hudmods` | bridge, no readback, `keepGui`+`keepEntities` | + AppleSkin, MapWriter | `99cf2e43...` |

## 4. Load compatibility

All runs report the same shape: `Forge Mod Loader has identified 16/18 mods`, then
`Client attempting to join with <n> mods : ...`. The per-mod evidence:

| Mod | Loaded | Log line that says so | Anything named after it that failed |
|---|---|---|---|
| Fugue 0.24.4 | yes | `fugue@0.24.4` in the FML mod list; `[Foundation]: Calling tweak Fugue`; `Loading tweak name com.cleanroommc.fugue.common.LateBootstrapTweaker`; `Calling tweak com.cleanroommc.fugue.common.LateBootstrapTweaker@5db34d24`; `[CleanMix]: Loading late loader [com.cleanroommc.fugue.common.FugueLateMixinLoader] for its mixins.`; `FMLFileResourcePack:Fugue`; `[fugue] Found status: UP_TO_DATE` | none |
| Scalar 3.9.0 | yes | `scalar@3.9.0`; `[Foundation]: Calling tweak ScalarLoadingPlugin`; its contained deps are injected: `FML has found a non-mod file scala-library-3.9.0.jar ... injected into your classpath` (also `scala-parser-combinators_3-2.4.0.jar`) | none |
| JEI 4.22.0.1035 | yes | `jei@4.22.0.1035`; `[jei]: Starting JEI...`; `Registering mod plugins took 263.3 ms`; `Building recipe registry took 54.72 ms`; `Starting JEI took 976.5 ms`; `FMLFileResourcePack:Just Enough Items` | warnings only: 17x `Potentially Dangerous alternative prefix 'minecraft' for name 'jei.shield.decoration.*'` (JEI's own item registration) and, when the fixture opens the recipe screen through JEI's API, 2x `[jei] Received invalid GUI properties for screen: class mezz.jei.gui.recipes.RecipesGui` |
| AppleSkin 1.0.14 | yes | `appleskin@1.0.14`; `FMLFileResourcePack:AppleSkin` | none |
| MapWriter 2.8.15 | yes | `mapwriter@2.8.10` (its own version string) in the mod list; mod log `Mw.load: loading...`; `[mapwriter] Starting version check ...`; `created new MwTexture from GL texture id 9 (512x512) (262144 pixels)`; `processed 579 block textures, 3162 skipped, 147 exceptions`; `config/mapwriter.cfg` written with `smallmap.enabled=true` | none |

`147 exceptions` in MapWriter's own block-colour generation is the mod reporting its own texture processing; it does not
name Celtium and the mod continues to its HUD.

Not one run contains an exception, Mixin failure or crash naming Fugue, Scalar, JEI, AppleSkin or MapWriter. The three
non-mod messages that do appear (`Couldn't load Narrator library`, `Error loading class: org/lwjgl/opengl/Display`,
`Potentially Dangerous alternative prefix`) are identical with and without the mods and none of them stops loading.

## 5. Native drawing

### 5.1 The frame itself, with every mod loaded

`modtest-base` (GL-less, readback on, Fugue+Scalar+JEI, no screens): `fixtureStatus=PASSED`,
`presentedDistinctColours=15774`, `presentedNonBackgroundFraction=0.8216`, `presentedImageAssertionFailures=0`,
`presentedImagePixels=921600`. `mod5-hud-base` (same, `keepGui`+`keepEntities`): `PASSED`, 17629 colours, 0.8263, 0
failures. The dumped frame `mod2-glless-pic1/screenshots/celtium-presented.png` is a complete terrain scene with sky,
horizon and the fixture's block pattern, drawn with no GL context.

### 5.2 JEI's own drawing

JEI's screens really were reached, so this is a claim about the mod's rendering and not only about it loading:

- `mod4-jei-screen-a`: `jeiScreenOpenedAtTick=30`, `jeiRecipeOpened=true` (the fixture opens `RecipesGui` through JEI's
  own `IJeiRuntime`), 202 ticks, 165 presented frames.
- `mod3-glless-jei` and `mod2-glless-pic1/2`: `jeiScreenOpenedAtTick=30`.
- The 34-tick recipe hook only fires when the frame rate is high enough, see section 6.

Like-for-like pair, same engine (`6dcfba12...`) and fixture (`99cf2e43...`), same GL-less configuration, same fixture
flags, the only differences being JEI's presence and its screens being open:

| Run | JEI | Frames | offers / accepted | ITEM (4) | POSITION_TEX (6) | POSITION_COLOR (1) |
|---|---|---|---|---|---|---|
| `mod5-nojei-screen` | absent | 184 | 14000 / 13701 | 8064 / 8064 | 3122 / 3122 | 1586 / 1586 |
| `mod4-jei-screen-a` | present + recipe screen | 165 | 16000 / 15698 | 9483 / 9483 | 4191 / 4190 | 157 / 157 |

Per presented frame the same pair reads: accepted 74.5 → 95.1, ITEM 43.8 → 57.5, POSITION_TEX 17.0 → 25.4,
POSITION_COLOR 8.6 → 1.0. The ITEM layout is the one item stacks are drawn with (docs/scope-and-limits.md section 1), and
in every GL-less JEI run its acceptance was essentially total: `9483/9483`, `9239/9239`, `4060/4060`, `4779/4778`,
`8064/8064`. No JEI run has a refusal whose reason names anything but the vanilla particle family (5.3).

Picture evidence, `mod2-glless-pic2`, the run's own early presented dump at tick 153 with the inventory screen open
(`screenshots/celtium-presented-early-run-125246168c568-66d3c617.png`): JEI's item grid is in the frame - dozens of item
stacks laid out as the mod's list, drawn natively with no GL context. In the same frame the world behind the screen is
black because only 22 frames had been drawn by then in that configuration; the run's capture frame (screen closed) is the
complete terrain picture.

### 5.3 What Celtium refused, with the renderer's own reason

Every backend refusal carries a reason string in `CELTIUM_REFUSALS_BY_FORMAT`. In the mod runs there are two families in
GL-less and one in the bridge:

| Family | Reason string (verbatim) | Where | Size |
|---|---|---|---|
| PARTICLE (format 5), single batches | `unrecorded (format=5 stride=28 mode=7 count=<n>) stage=10` | every GL-less run, with and without JEI | 8-15 per run (`mod4-jei-screen-a`: 12 reasons, values 1-3, 15 batches; `mod5-hud-base`: 9 reasons, 9 batches) |
| POSITION_TEX_COLOR (format 2) single quads | `unrecorded (format=2 stride=24 mode=7 count=4) stage=3` | the GL-less **readback** runs with no screen open | `modtest-base`: 142 of 307 format-2 arrivals; `modtest-jei`: 49 of 105 |
| bridge startup preflights | `preflight: format=<id> texture=<n> (no pass has run yet in this instance)` | bridge runs only | `mod5-bridge-jei`: 72x format 6, 38x format 6, 37x format 6/2, 36x format 6/5 ... |

The particle family is the fixture's own `FLAME` particles: it appears with and without JEI, at the same rate, and it is
the only family left in the fast-frame GL-less runs. The format-2 family is one quad per frame - the game's
default-framebuffer composite - and it is the only content in those runs whose acceptance is not ~100 % (the other half
of format 2, the font and GUI, was accepted in full). Neither family is mod geometry, and neither is named after a mod.

Two honest gaps in the instrument itself:

- both GL-less reason strings begin with `unrecorded`: the backend sets that placeholder on entry and only some refusal
  paths overwrite it, so these refusals are counted but not named by the code that made them;
- `offers - accepted` is larger than the refusal tally (`mod4-jei-screen-a`: 302 vs 15). The tally counts only batches the
  backend was asked about; the rest are offers the hook returns from before the decision (no vertex data or no address),
  which also run the vanilla GL body (`glBodyCompleted=346`). The instrument does not separate those two cases.

### 5.4 Mods that loaded but did not draw here

- **AppleSkin 1.0.14**: no AppleSkin geometry can be identified. The fixture's world is creative with a sword in hand, so
  there is no hunger/saturation HUD for the mod to overlay and no food in the inventory screen's hover slot. This is the
  scene, not Celtium: the mod loaded and no Celtium counter mentions it.
- **MapWriter 2.8.15**: its smallmap is enabled in its own config (`smallmap.enabled=true`, 30 % of the screen, top-right)
  and its coordinate readout *is* on screen in the bridge screenshot `mod6-bridge-hudmods/screenshots/celtium-validation.png`,
  but the circular minimap is absent from both that GL screenshot and the GL-less presented frame
  (`mod5-hud-mods/screenshots/celtium-presented-world-*.png`). The failure - if it is one - is therefore present with a
  real GL context as well, so this evidence does not attribute it to the native path; there is no stock-Cleanroom
  reference run in this environment to separate the two.
- **No hotbar or crosshair is on screen in any of the HUD runs, in either configuration.** `mod5-hud-base` (no HUD mods
  at all, `keepGui=true`), `mod5-hud-mods` (GL-less presented frame) and `mod6-bridge-hudmods` (the game's own GL
  screenshot, `keepGui=true`) all show terrain with chat and tutorial text but no hotbar and no crosshair. The
  keepGui toggle exists in the build that ran (`celtium.validation.keepGui` in the pinned fixture, and its
  `hideGUI = !keepGui` path), so the evidence does not separate "the fixture hid the HUD at the capture" from "the HUD
  did not reach the frame" - and because the bridge case is GL's own output and looks the same, the observation is **not**
  evidence about Celtium's native path. Its consequence is real either way: mod HUD overlays cannot be judged from these
  frames, which is why AppleSkin and MapWriter are reported as load-only findings above. The counters do say the HUD
  layouts were accepted natively in the runs without a screen (`modtest-base`: format 7 `1256/1256`, format 1 `153/153`,
  format 2 `307` offered with 153 accepted - see 5.3 for the refused half).

## 6. What could not be tested, and why

- **Any mod other than the five above.** There is no other 1.12.2 mod jar on the machine: the Prism instance ships
  Fugue and Scalar (+ the Scala runtime), `.celtium-work/major-mods/` holds only JEI, and a search of the home tree finds
  nothing else. AppleSkin and MapWriter were fetched to widen the set; a broader mod manifest (the plan's G4) is still
  missing.
- **Intel GPU.** Every run used `/dev/dri/by-path/pci-0000:03:00.0-render` (AMD RX 6400). No Intel run was made.
- **The fixture's per-tick hooks vs the presented picture.** In the GL-less configuration the two cannot be had
  together: with `presentReadback=true` a frame costs ~100 ms, the run draws 39-107 frames for 200+ ticks, and the
  fixture's `frame()` hooks keyed on `worldTicks == 30/34` are stepped over (6 of 8 JEI runs never opened a JEI screen);
  with the readback off the hooks fire but the run has no picture and ends `FAILED_PRESENTED_IMAGE`. Both configurations
  were run and each number above names which one it came from.
- **Per-batch attribution to a mod.** The immediate path has no per-caller tag, so "JEI drew N batches" is measured as
  the difference between two runs of the same scene with and without the mod, normalised per presented frame, plus the
  picture. That is why the JEI pair and not a single run is the evidence.
- **AppleSkin's and MapWriter's own scenes.** Both need content the fixture does not build: survival hunger for
  AppleSkin, and a working smallmap for MapWriter (section 5.4).
- **One run was lost to a rebuild race.** `mod4-nojei-screen` copied the fixture jar while another agent was writing it
  (22 bytes), FML reported `FML has found a non-mod file celtium-validation-client-...jar`, the game stayed in the main
  menu and the run ended `MISSING_REPORT` / exit 124. Every later run used a pinned, `unzip -t`-verified snapshot.
- **Fixture verdicts in the hook runs.** `mod3/mod4/mod5-nojei/bridge` runs end `FAILED_PRESENTED_IMAGE` because the
  readback was deliberately off to keep the frame rate up; their counters are unaffected and are used, their verdict is
  not.
