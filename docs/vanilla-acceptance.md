# Vanilla acceptance table — one row per vanilla stage

Owner: **acceptance** (task-10), the independent verifier of the vanilla-completion push. This file
fixes nothing; it says, per stage, what is implemented, locally verified, unverified or
unimplemented, and names the measurement behind each word. A status without a measurement is `U`,
not "working".

**Reference rule (directive of 2026-10-11 14:00, supersedes the bridge).** The reference is
**upstream Celeritas, pinned `989ebf2e`** - a reference leg is the candidate's harness command with
the celtium jar replaced by the pinned Celeritas jar and no `-Dceltium.vulkan.glless=true`. The
bridge leg is no longer used: it is our own interop path, so a bridge comparison compared our code
with our code. Every row's reference column is one of:

- **`celeritas`** — a reference leg run (named, with its jar and numbers).
- **`stored`** — a picture or calibrated floor that already exists on disk, taken before today's
  defects. Valid only where the run's own `worldFrameNonBackgroundFraction` and pixel content show it
  drew the world; the bridge runs in §1a did not.
- **`self`** — a GL-less self-comparison, one jar, one flag apart (fog on/off, weather on/off).
- **`none`** — no reference exists, so the row is `U`.

A GL-less run compared against another GL-less run on a *different* jar is not a comparison and is
not used here.

Status vocabulary, used strictly: **V** verified locally (a named run's picture or counter decides
it, and I looked at the crop or the counter that decides it); **W** wrong (implemented, measured,
disagrees with the reference); **M** measured missing (pixels a stage should produce are absent, with
the crop or counter that shows the absence); **U** unverified (no measurement decides it, with the
reason and the knob that would); **X** unimplemented.

## 0. This revision

Revision of 2026-10-11 ~14:35 JST.

1. **The bridge leg drew no terrain from 09:47 until `0fddd6ccf`** (a depth-format contract only the
   GL-less configuration ever chose from), and several of those runs reported `PASSED` on a chat
   block over a sky-only frame. §1a, §2. This is why the bridge is no longer the reference.
2. **The reference is now upstream Celeritas `989ebf2e`.** The recipe, the crash the first attempt
   hit, and the two reference legs that exist (`ref-celeritas2`, `sky-draws-ref`, both
   `nonBg=0.761173` on two different fixture builds) are in §1c; the comparison is in §6.
3. **A matched GL-less leg on the current tree exists** (`acc2-glless`, 13:47, jar
   `b074fbb9b659729e`), taken with the harness's own slot lock. Against Celeritas: the terrain centre
   differs by 5832 px of 204800 (mean 0.885), the hotbar strip by **0**; against the stored picture of
   the same jar the difference below row 300 is four pixels of 460800. **The whole-frame difference is
   the sky and the horizon.** §6.
4. **The harness arbitrates the single game slot itself** (`67cf6f806`), after three concurrency
   incidents in ten minutes (13:39:20 two GL-less instances; 13:44 three). Runs taken under
   concurrency are not admissible evidence. §4.
5. **A jar the Prism instance keeps in its own `mods/` is never copied into a test game directory**
   (`8ae2e64f8`), whatever a worker names its frozen copies; verified copy-only, without launching.
6. **The fixture must not name Celtium classes** for a Celeritas leg to boot - measured, §1c.
7. **Two rows are superseded by newer jars and both readings are kept.** Row 2 (sky dome): on jar
   `a106e84ac2f26a84` rows 0-199 are **0 of 256000 px** different from the Celeritas reference, with
   63 distinct colours on both sides; the format-7-refused control is the flat one. Row 8 (particles):
   `id5=142/142` and no `id0` on jar `f21bc38693c8dadb`. Every row now names its jar, and §3a is the
   provenance index.
8. **The `nonBg >= 0.4` validity rule is itself a defect**, with the counters that would replace it
   (`passes`, per-layer section counts). §2, §4.
9. **Two rows added that nobody had taken**: video settings (the user names it; the scene exists in
   the fixture and 35 runs use it, none current - all `FAILED`, newest 12:31) and the Intel UHD 630
   leg (153 Intel reports, **0** with `glless=true`; task-11). §3 rows 20-21.
10. **The leftover-game-directory blocker is fixed** (`b0b1877a8`): a directory with no report and no
    screenshots is reclaimed, one holding evidence is still refused. §4.
11. **Fresh Celeritas reference legs for the three scenes task-22 needs** - terrain `nonBg=0.761173`,
    entity `0.774399`, inventory `0.888320`, all tick 200, all with their `effectiveConfig` JVM
    arguments quoted so "no `glless` in the reference" is verified per leg rather than assumed. §1d.
12. **The user's own launch is now an instrument, not an assumption**: Prism 11.1 `--launch` on a
    private headless output, photographed with `grim` and measured by distinct-colour count, with the
    instance's own log lines as the GL-less evidence. §6b. The verdict leg waits for task-15's jar.

## 1. References

### 1a. What was wrong with the bridge, and why older bridge evidence is void

From 09:47 until `0fddd6ccf` (13:14) every bridge run carried
`passes=0 skipped=N skipReason=translucent:_no_pipeline` and one copy per attempt of

```
pipeline for solid|cutout_mipped|translucent failed:
  IllegalStateException: the depth format was asked for before anything chose one
```

`VulkanDepthFormat.chosen()` (no-arg) threw when nothing had chosen, and the only caller that
*chose* was `GllessFrameTarget` - GL-less only. The separating experiment (same fixture, scene, pose,
flags, GPU, fresh directory, one minute apart; only the renderer jar differs) is
`.celtium-work/acceptance/bridge-ab.sh`:

| run | celtium jar | fixture | passes/skipped | nonBg | distinct | verdict |
|---|---|---|---|---|---|---|
| `a-acc-curjar-bridge` | `b874f92393f79b46` | `105598e0f89be16d` | 0 / 120 | 0.160747 | 104 | FAILED |
| `a-acc-oldjar-bridge` | `b63a8a85312027d5` | `105598e0f89be16d` | 242 / 0 | 0.735010 | 1534 | PASSED |

Void bridge runs, each with its own `celtium/vulkan-check` line (`passes=0`), its modal-colour
statistics and verdict:

| run | time | passes/skipped | distinct | nonBg | verdict |
|---|---|---|---|---|---|
| `n03-x-terrainbridge` | 11:56 | 0 / 1068 | 541 | 0.505074 | **PASSED** |
| `n03-ae-terrain-bridge` | 12:23 | 0 / 936 | 732 | 0.813402 | **PASSED** |
| `n03-af-terrain-jabridge` | - | 0 / 1068 | 640 | 0.591122 | UNCALIBRATED |
| `n03-ah-jei-bridge` | - | 0 / 945 | 691 | 0.809351 | **PASSED** |
| `n03-ai-jei-bridge2` | - | 0 / 978 | 764 | 0.820577 | **PASSED** |
| `n03-aj-jei-bridge35` | - | 0 / 1029 | 575 | 0.810275 | **PASSED** |
| `n03-ak-jei-vs35` | - | 0 / 378 | - | - | FAILED |
| `skyfix-bridge1` | 12:32 | 0 / 288 | 682 | 0.149229 | FAILED |
| `posefix-bridge` | 12:30 | 0 / 177 | 783 | 0.192509 | FAILED |
| `posefix2` | 12:32 | 0 / 645 | 687 | 0.522616 | FAILED |
| `jei-br` | 12:40 | 0 / 930 | 7594 | 0.888320 | UNCALIBRATED |
| `van-bridge` | 12:56 | 0 / 498 | 108 | 0.166317 | FAILED |
| `van-bridge2` | 12:59 | 0 / 1122 | 96 | 0.153832 | FAILED |
| `m-entity-bridge` | 12:58 | 0 / 636 | 961 | 0.268383 | UNCALIBRATED |
| `bridge-model` | 09:47 | 0 / 447 | 687 | 0.149201 | FAILED |
| `a-acc-curjar-bridge` | 13:13 | 0 / 120 | 104 | 0.160747 | FAILED |

**Seven of these reported PASSED or UNCALIBRATED with no world in the picture.** Any bridge run with
`passes=0` is void for terrain whatever its verdict.

### 1b. The stored pictures that are still valid

| stored picture | time | jar | passes/skipped | distinct | nonBg | bands | bg | verdict |
|---|---|---|---|---|---|---|---|---|
| `scene-ref-terrain` | 01:15 | (not kept) | 661 / 0 | 1535 | 0.732164 | - | `0x571716` | FAILED (identity, since fixed) |
| `glow-bridge5` | 09:02 | `b63a8a85312027d5` | 909 / 0 | 1819 | 0.748878 | - | `0x571716` | PASSED |
| `scene-floors/terrain-a` | 09:11 | `478cf85555ec37a9` | 1566 / 0 | 1536 | 0.727735 | - | `0x571716` | PASSED |
| `a-acc-oldjar-bridge` | 13:13 | `b63a8a85312027d5` | 242 / 0 | 1534 | 0.735010 | - | `0x571716` | PASSED |
| `depthfix-bridge` | 13:16 | `0b8707888510d0c9` | 138 / 0 | 1534 | 0.744563 | 617 | `0x571716` | PASSED |
| `acc2-ref-bridge` | 13:44 | `b074fbb9b659729e` | 159 / 0 | 1534 | 0.744563 | 617 | `0x571716` | PASSED |

All AMD RX 6400 (RADV NAVI24), scene `terrain`, pose
`x=0.5000 y=12.0000 z=0.5000 yaw=180.000 pitch=20.000`, `captureStartTick=200`,
`sceneCalibrationKeyMatches=true`. `acc2-ref-bridge`'s screenshot is **byte-identical** to
`depthfix-bridge`'s, 28 minutes and a different jar build apart, one of them with two other game
instances running - so the stored picture is deterministic and reproducible. Caveat: the bridge
leg's terrain is also Celtium's Vulkan backend (`Terrain Backend: vulkan` in every report), so a
stored bridge picture is a regression reference for terrain, which is exactly why the reference is
moving to Celeritas.

### 1c. The Celeritas reference leg

Recipe, as directed: the candidate's harness command with the renderer jar replaced by the pinned
upstream Celeritas jar (`.celtium-work/upstream-baseline/build-1122/build/libs/2.5.12-upstream.989ebf2e/celeritas-forge-mc12.2-2.5.12-upstream.989ebf2e.jar`,
sha256 `d12425eaaf184e7925cd41d1deb4f9df69064115c8e78c8fcedfd6ecc63f93e7`) and no celtium jar.

**Take 1 failed, and the failure is a property of the fixture, not of the leg.** With the fixture
frozen at 13:52 (`0355ffcd40c22a24`) the game crashed 9.3 s in, `exitCode 255`,
`fixtureStatus=MISSING_REPORT`, no screenshot:

```
crash-reports/crash-2026-10-11_14.00.30-client.txt
Description: Initializing game
java.lang.NoClassDefFoundError: org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend
  at net.minecraftforge.fml.common.FMLModContainer.gatherAnnotations(FMLModContainer.java:425)
```

Measured cause: `strings` over the fixture jar finds **3** references to
`org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend` in `0355ffcd40c22a24` and **0** in
`1ac3021812f75aba` (the current build, 14:01). A fixture whose mod class names Celtium types in its
signatures cannot boot without Celtium, whatever flags the leg passes.

**Take 2** (`acc3-celeritas-ref`, fixture `1ac3021812f75aba` frozen as
`.celtium-work/acceptance/frozen/celtium-validation-client-celtiumfree.jar`, celeritas
`d12425ea…`, `-Dceltium.validation.minWorldFrames=5` only, capture at the fixture default tick 200 to
match the GL-less candidate) was taken but **never got the slot**: it waited 900 s behind other
workers' legs and exited 2 (`--slot-wait` expired). The script is
`.celtium-work/acceptance/acc3-celeritas-ref.sh`. The flags differ from the candidate's in that the
three Celtium-only ones (`-Dceltium.vulkan.validation`, `-Dceltium.vulkan.presentReadback`,
`-Dceltium.validation.dumpPresentedImage`) are dropped: they are Celtium mechanisms and this leg has
no Celtium, so passing them would describe a dump that cannot exist.

**The reference legs that do exist** (taken by other workers, verified by me from their own reports
and pictures):

| reference leg | celeritas jar | fixture | scene / pose / tick | distinct | nonBg | bg | verdict |
|---|---|---|---|---|---|---|---|
| `ref-celeritas2` | `d12425eaaf184e79` | `1ac3021812f75aba` | terrain, `0.5/12/0.5 yaw 180 pitch 20`, 200 | 2476 | **0.761173** | `0x571716` | PASSED |
| `sky-draws-ref` | `d12425eaaf184e79` | `f03a129610323819…` | terrain, same pose, 200 | 2569 | **0.761173** | `0x571716` | PASSED |

Two independent legs on two different fixture builds agree on `worldFrameNonBackgroundFraction` to
six decimals, and both report `sceneCalibrationKeyMatches=true`. Both exited 0, `PASSED`, three
minutes apart (14:02 and 14:05), so neither overlapped another instance. Their GPU provenance is the
GL string `AMD Radeon RX 6400 (radeonsi, navi24, ACO, DRM 3.64, 7.2.9-x64v3-xanmod1)` in each
report: without Celtium there is no `CELTIUM_NATIVE_DEVICE` line, so `--require-gpu` cannot name a
Vulkan device on a reference leg. `ref-celeritas2` uses the same
fixture build that I froze, so it pairs with `acc2-glless` exactly; §6 is that comparison. The
stored bridge picture of the GL-less candidate's own jar gives `nonBg=0.744563` for the same scene,
which is why the two reference kinds are reported separately.

### 1d. Reference legs for the three scenes the completion condition needs (taken 15:2x)

| leg | scene variant | pose | tick | distinct | nonBg | bg | black | verdict | JVM args that reached the JVM |
|---|---|---|---|---|---|---|---|---|---|
| `ref3-terrain` | default flat stack | `0.5/12/0.5 yaw 180 pitch 20` | 200 | 2510 | **0.761173** | `0x571716` | 0.0 | PASSED | profile `['-Dfile.encoding=UTF-8']`, runner `[minWorldFrames=5]` |
| `ref3-entity` | keepEntities + captureEntities | same | 200 | 3189 | **0.774399** | `0x571716` | 0.001109 | UNCALIBRATED | runner `[minWorldFrames=5, keepEntities, captureEntities]` |
| `ref3-inventory` | jeiScreen + keepInventoryScreen | same | 200 | 8210 | **0.888320** | `0x8B8B8B` | 0.014728 | UNCALIBRATED | runner `[minWorldFrames=5, jeiScreen, keepInventoryScreen, screenshotAtTick=60]` |

All three: upstream Celeritas `d12425eaaf184e79`, frozen fixture `acfb62b16ed70ac3` (the current
Celtium-free build), no celtium jar, AMD Radeon RX 6400, `--validate`, capture tick 200. The two
`UNCALIBRATED` verdicts are correct, not failures: the entity and inventory variants are not the
calibrated plain-terrain variant, so the key cannot match by construction. All three are usable
references by the §2 test (`nonBg >= 0.4`). The terrain leg's `nonBg=0.761173` is the **third**
independent Celeritas terrain leg to produce that exact number (`ref-celeritas2` and `sky-draws-ref`
in §1c), which is what makes it usable as a reference at all.

**Provenance, verified per leg rather than assumed.** `prism_validation.py` builds its command from
the Prism *patches* (`patches/*.json` `+jvmArgs`, lines 77 and 258-260) plus `--jvm-arg`; the
instance's `instance.cfg` `JvmArgs` reaches the JVM only in a Prism launch. Every leg's
`effectiveConfig` is therefore quoted literally:

| leg | `effectiveConfig.profileJvmArgs` | `effectiveConfig.runnerJvmArgs` | `glless` in the union |
|---|---|---|---|
| `ref3-terrain` | `["-Dfile.encoding=UTF-8"]` | `["-Dceltium.validation.minWorldFrames=5"]` | **no** |
| `ref3-entity` | `["-Dfile.encoding=UTF-8"]` | `["-Dceltium.validation.minWorldFrames=5", "-Dceltium.validation.keepEntities=true", "-Dceltium.validation.captureEntities=true"]` | **no** |
| `ref3-inventory` | `["-Dfile.encoding=UTF-8"]` | `["-Dceltium.validation.minWorldFrames=5", "-Dceltium.validation.jeiScreen=true", "-Dceltium.validation.keepInventoryScreen=true", "-Dceltium.validation.screenshotAtTick=60"]` | **no** |
| `acc2-glless` (candidate) | `["-Dfile.encoding=UTF-8"]` | `[…, "-Dceltium.vulkan.glless=true"]` | yes |
| `ww6-gl-base` (candidate) | `["-Dfile.encoding=UTF-8"]` | `[…, "-Dceltium.vulkan.glless=true", …]` | yes |
| `sky-draws-final` (candidate) | `["-Dfile.encoding=UTF-8"]` | `["-Dceltium.vulkan.glless=true", …]` | yes |

The reference legs were never GL-less; that is measured per leg, not inferred from the absence of a
flag, and the same reading is what a reader must do for any future leg.

**Tick pairing, because it breaks comparisons silently.** `-Dceltium.validation.screenshotAtTick=60`
writes an *extra* `celtium-at-tick-60.png`; the main `celtium-validation.png` is still taken at tick
200 (`requestedAtTick=200` in its identity file). With `keepInventoryScreen=true` the screen is still
open at 200, so a candidate whose world presented dump is taken at tick 200 pairs with it directly,
and the tick-60 file is a second, earlier observation rather than the reference.

## 2. Why the gate did not catch the missing terrain

`worldFrameNonBackgroundFraction` counts pixels differing (tolerance 8) from the frame's **most
common colour**. A sky-only frame's mode is the sky, so the chat block, the toast, the tutorial box
and the arm all count as non-background:

- `n03-ae-terrain-bridge`: mode `(90,104,128)` at 10.1 % = the chat background; `distinct=732`;
  `nonBg=0.813402`; `PASSED`, no terrain in its own picture.
- `n03-ah`/`n03-ai`/`n03-aj`: `nonBg` 0.809351 / 0.820577 / 0.810275, all `PASSED`, all `passes=0` -
  three more PASSED over a sky-only frame.
- `van-bridge2`: mode `(179,207,255)` at 82.8 %; `distinct=96`; `nonBg=0.153832`; FAILED - same
  missing terrain, chat faded.
- The GL-less leg of the same minutes: `n03-ag-jei-glless` `distinct=17400`, mode `(87,23,22)` =
  ground, `nonBg=0.841242`.

Hence "the reference must have `nonBg >= 0.4`" (task-2/3/7/9 briefs) admits seven void runs and is
not a validity test. **This is a defect of the rule, not of a run**: a fraction of pixels that differ
from the modal colour cannot distinguish "the world is drawn" from "a HUD element is drawn over a
flat sky", because the modal colour of a sky-only frame is the sky and every HUD pixel counts as
non-background. What a terrain-valid reference needs is a counter that HUD pixels cannot satisfy.
Two candidates exist and both are already reported by the runs:

- **`passes` / `lastSections` from `celtium/vulkan-check`** - every void run has `passes=0` while
  every run that drew the world has `passes` in the hundreds (`scene-floors/terrain-a` 1566,
  `acc2-ref-bridge` 159, `acc2-glless` 1063, `ww6-gl-base` 649/996 class). It is the counter that
  named the defect in §1a in the first place. Caveat: it is Celtium-specific, so a Celeritas
  reference leg does not carry it.
- **per-layer section counts** (the `solid - N sections` lines), which are renderer-independent and
  are present in both legs' logs; a floor of "at least one solid layer with N > 0" cannot be met by a
  chat block.

A modal-colour test, or `distinctColours > 1000`, would also separate the cases. All three are
fixture changes (`ValidationClient`, N06), reported by me and not made.

## 3. The table

| # | vanilla stage | status | reference | evidence | owner |
|---|---|---|---|---|---|
| 1 | terrain / chunk layers | **V** | celeritas | **Current: `cand3-terrain`** (GL-less, jar `7ac31c7601be73c7c26a`, fixture `acfb62b16ed70ac3`, AMD RX 6400, scene=terrain, pose `0.5/12/0.5 yaw 180 pitch 20`, tick 207, `PASSED`, `pictureFrameDelta_world=0`, `passes=969 skipped=0`) against `ref3-terrain` (Celeritas `d12425ea…`, tick 200): terrain centre `(320,300,960,640)` 5770 px of 204800 (2.65 %, mean 0.868), bottom corners 4 px each, hud strip 6 px, hotbar strip **0**, sky band **0 px**. Earlier, same shape: `acc2-glless` (jar `b074fbb9b659729e`) against `ref-celeritas2` 5832 px / mean 0.885; against the stored same-jar picture 1 px. §6c, §6a. | terrain backend; depth-format defect = N10, fixed |
| 2 | sky dome | **V** on `7ac31c7601be73c7c26a` and `a106e84ac2f26a84`, **W** on `b074fbb9b659729e` | celeritas | **Current: the dome matches the Celeritas reference on the counter-reset jar.** `cand3-terrain` and `cand3-entity` (jar `7ac31c7601be73c7c26a`) vs `ref3-terrain`/`ref3-entity`: sky band `(0,0,1280,120)` **0 of 153600 px differ >8** (mean 1.365, max 4) in both scenes; rows 0-199 = 0 of 256000 on the sky-draws pair of jar `a106e84ac2f26a84`. The control with format 7 refused is the flat one (1 distinct colour). The older jar `b074fbb9b659729e` read 100 % of the band different with the flat `(120,167,255)`; that reading is kept as the record of what was measured when. §6c. | sky-draws (task-3), pipelines (task-6) |
| 3 | stars | **U** | none | `pinnedWorldTime=6000` in every report (noon; star brightness 0 by construction) and no counter distinguishes a star draw. The candidate is the refused group above (`colour alpha 0.046` = the mirrored star alpha, refused because the position shaders hardcode alpha 1.0). Needs a night scene flag, which does not exist. | sky-draws (task-3) |
| 4 | horizon plane | **V** (closed at equal build state) | celeritas | fog-path's equal-build-state pair, condition quoted: `captureCondition=sections captureSections=41 captureMinWorldFrames=295` - both legs reached the same listed-section target (ours 41 after 819 world frames, the upstream reference 43 after 405). At that state rows 200-320 differ by **mean 0.403, 744 of 153600 px over 8 (0.484 %)**; sky rows 0-199 **0 of 254720**; terrain rows 320-720 0.012 %; whole frame 0.087 %. The large numbers I measured earlier (34.9 % / 33.0 % on jar `7ac31c7601be73c7c26a`, 40.8 % on `a106e84ac2f26a84`, 66.7 % on `b074fbb9b659729e`) were taken at a **tick-keyed** capture, which is a *different build state*: at the fixture's tick 200 our leg had only 106-162 world frames, so the listed-section counts (22 vs 32, 34 vs 41) were build throughput, not culling - and the band looked like a rendering difference when it was a comparison across states. Kept as the record of what was measured when. | sky-draws (task-3), fog-path (task-2) |
| 5 | clouds | **U** | celeritas (reference only) | Counter defect, measured in two independent runs on older jars: in `acc2-glless` (jar `b074fbb9b659729e`) and `ww-gl-cloud` (13:38, jar `ff68da097c102e47`) format 8 (`POSITION_TEX_COLOR_NORMAL`, 28 B / 5 elements - the fancy-cloud layout) reaches `128/128` in the **first** histogram line and never changes again while every other id grows: the cloud batches are submitted in frame 1 and never again. Newest state: the Celeritas reference for the look-up pose exists - `ww7-ref-cloud` (14:2x, celeritas `d12425ea…`, fixture `1ac3021812f75aba`, pitch -20, `distinct=16561`, `nonBg=0.776253`, `UNCALIBRATED` because the pose is new - correct, not a failure) - but the matching GL-less leg `ww6-gl-cloud` produced **no report and no screenshots at all**, so there is no pair. No cloud pixel is identified in any GL-less picture. | weather-water (task-9) |
| 6 | water surface | **U** | none | `ww-br-water`/`ww-gl-water` (13:37/13:38, jar `ff68da097c102e47`): both `UNCALIBRATED_PRESENTED_IMAGE` (new pose - correct, not a failure), and the bottom band `(600,720)` has the **same mean in both** `(82.1,107.1,50.4)` = grass. The GL-less picture is flat sky over grass with no water-tinted pixel, so the run demonstrates neither water drawn nor water missing. Needs the camera in/over the water layer and a named crop. | weather-water (task-9) |
| 7 | weather (rain/snow) | **U** | celeritas | Newest pair, both on the same scene/pose/tick 200: `ww6-gl-base` (GL-less, jar `f21bc38693c8dadb`, fixture `f03a129610323819`, `weather=true particles=true`, `distinct=5096`, `nonBg=0.747739`, `bg=0x571716`, `PASSED`) vs `ww7-ref-base` (Celeritas `d12425ea…`, fixture `1ac3021812f75aba`, `distinct=19106`, `nonBg=0.780331`, `bg=0x571715`, `PASSED`). My stage comparison: sky band 20.7 % >8 (mean 6.1), horizon 54.0 % (mean 30.8), terrain centre 16.1 % (mean 6.7), whole frame 26.5 % (mean 12.7); every region's mean agrees to about 1 count. Both legs draw the same content; rain is stochastic between runs, so the residual cannot be attributed without a one-flag control (weather on/off, one jar). Older pair (`ff68da097c102e47`, bridge leg): sky 87.9 %, horizon 40.2 %, terrain 8.8 %. | weather-water (task-9) |
| 8 | particles | **U** (counter now yes, picture no) | celeritas | **Superseded**: format 5 (`PARTICLE`, 28 B / 4 elements) is offered and fully accepted on the newest jars - `ww6-gl-base` (14:15, jar `f21bc38693c8dadb`, fixture `f03a129610323819`, `weather=true particles=true`) ends at `id5=142/142` with **no `id0` line at all**, and `sky-draws-final` (jar `a106e84ac2f26a84`) at `id5=113/113`; `ww6-gl-water` at `id5=154/154`. The old "no id5 anywhere" reading was true of the jars measured then (`ww-gl-base`, `acc2-glless`: `5:0`), and the `id0` refusals it carried are gone after the two wrong element strings were fixed. What is still missing is the picture: no crop names a particle pixel in a GL-less frame, and the pair that would decide it is `ww6-gl-base` vs `ww7-ref-base` (row 7 numbers). | weather-water (task-9), pipelines (task-6) |
| 9 | entities (models, renderers) | **U** | none | `m-entity-glless` (12:58, jar `ac347ff1f98e56ad`) `modelDraws=2477 modelMeshLists=38 modelQuads=228`, accepted format 9 = 228, but its only bridge twin is `UNCALIBRATED` **and** `passes=0` (void, §1a). The current attempt (`entity-runs/ent-glless` + `ent-bridge`, 13:39-13:44) ran while two other game instances were up, so per `AGENTS.md` its timings and pixels are inadmissible. No honest entity comparison exists on the current tree. | entity-models (task-7) |
| 10 | TESR / block entities | **U** | none | `-Dceltium.validation.enchantItem=true` was last set by `p519-lookdown` (10-10 19:34), jar not kept; no run today. `plan.md` §0.118's enchant-table match is on an older jar and does not reproduce here. | entity-models (task-7) |
| 11 | items (item models / GUI stacks) | **W/U** | stored (void for terrain) | `jei-gl` (12:40 GL-less, jar `3a94b9dd3140…`, accepted format 4 = 4424) vs `jei-br` (12:40 bridge, same jar, `passes=0`): `compare_jei_pair.py` - inventory panel `(420,120,860,600)` **94.7 %** of pixels differ >8 (mean 89.6), whole frame 92.9 %. The class of difference (panel/slot backgrounds absent, item models present) is §0.157's finding; the numbers are a pre-integration jar and the stored leg is void for terrain. A Celeritas leg with the inventory open is the re-take. | gui-stage (task-5), pipelines (task-6) |
| 12 | GUI / HUD (hotbar, panels, slot backgrounds) | **M** | stored (void for terrain) | Crop `(480,650)-(800,720)`, jar `216cd6284a3c22f7` both legs, tick 200: GL-less `n03-ac-terrain-deco` shows **terrain only** - no hotbar frame, no slots, no selection highlight; stored `n03-ae-terrain-bridge` shows the vanilla hotbar with 9 slots and the first highlighted. Stacked crop `.celtium-work/acceptance/crops/hotbar-glless-top-bridge-bottom.png`, which I looked at. The stored leg drew no world (`passes=0`), so only the *presence* of the HUD elements is decidable, not the colours under them. | gui-stage (task-5), texture-mirror (task-4) |
| 13 | chat | **W** | stored (void for terrain) | Same pair: the **text** is drawn in both legs (same wrapped white text, same region - the N03 font path works); the **background quad is missing** under GL-less. Crop `.celtium-work/acceptance/crops/chat-glless-top-bridge-bottom.png`. Region means `(0,320,660,620)` `(89.2,65.6,85.5)` GL-less vs `(123.6,134.1,152.4)` stored - contaminated by the void stored terrain; read the crop. | gui-stage (task-5), texture-mirror (task-4) |
| 14 | crosshair | **M** | stored (void for terrain) | Crop `(600,320)-(680,400)` of the same pair, `.celtium-work/acceptance/crops/crosshair-glless-top-bridge-bottom.png` (I looked at it): the stored leg shows the dark crosshair at frame centre; the GL-less leg shows terrain and text with **no crosshair**. | gui-stage (task-5) |
| 15 | selection outline (block selection box) | **U** | none | No run measures it: `allBlocks` was last set by `alive-again` (GL-less, no reference), no counter names the box, and the fixture has no knob that pins `drawSelectionBox`. | not attributed |
| 16 | block-breaking overlay | **U** | none | `-Dceltium.validation.breakBlock=true` exists in the fixture; the newest run that set it is `p471-all` (10-10 17:26), jar not kept; no run today, no counter. | not attributed |
| 17 | fog | **W** | self | Two runs one flag apart on one jar (`03c627cc52c8e566`, fixture `105598e0f89be16d`, scene=terrain): `fogoff` (13:18, `-Dceltium.vulkan.immediateFog=false`) `PASSED`, `distinct=1471 nonBg=0.667682`, `passes=459`; `fogon` (13:19, default, i.e. `immediateFog=true` at `VulkanTerrainGpuBackend.java:1603`) **`status=NO_CAPTURE`, no picture, no verdict**. On that jar the shipped default captured nothing and only the off-switch passed. Separately, the terrain's distant fog matches the stored reference (0.2 % of the horizon band) while the sky's fog ramp is absent. | fog-path (task-2) |
| 18 | final composite / present | **V (composite) / W (content)** | celeritas + stored | `acc2-glless`: `presentedImageVerdict=PASSED`, 0 assertion failures, `pictureEvidenceSource=presented-dump`, `presentedImageFrameCountAboveZero=true`. **The composite is faithful**: against Celeritas the hotbar strip differs by **0** px, the HUD strip by 6, the bottom corners by 4 each, and against the stored picture of the same jar the difference below row 300 is 4 px of 460800 - while the whole-frame difference is dominated by the sky (153600 px) and horizon (108089/153573 px) bands. No darkening and no offset; the earlier "global darkening" reading was a GUI frame compared against a world frame. Content is incomplete because the sky is (row 2). `postChains=0 postPasses=0` in every run I read, so post-processing chains are **X** here. | frame-composite (task-8) |
| 19 | text / font (N03) | **V** | stored | `plan.md` §0.144 (`quads=52406 submitted=52406 accepted=52406 refused=0`, `unicodeWindows=40548`) plus the JEI pair's crops, where the text is the same size and position in both legs (I read them). Row 13's missing box is not the font. | font / N03 |
| 20 | video settings (the screen, its widgets and its own frame) | **U** | stored (void for terrain) | The scene exists in the fixture: `VIDEO_SETTINGS("videoSettings", true, true)` - `GuiVideoSettings` opened through `displayGuiScreen` and clicked, with a GUI stage and its own gate; knob `-Dceltium.validation.scene=videoSettings`. Stored references exist (`scene-floors/video-a` 09:11, `video-b` 09:12, `scene-ref-video2` 01:18). **35 runs in the workspace use the scene, and none of them is current**: the newest are `n03-u-clean`, `n03-v-cleanbridge`, `n03-z-final`, `n03-aa-accept`, `n03-ab-accept-ja` (11:49-12:15) and `n03-ak-jei-vs35` (12:31), every one `FAILED_PRESENTED_IMAGE`, none after the depth fix, no Celeritas leg. So the user-named stage is unmeasured on the current tree; the experiment is one GL-less leg and one Celeritas leg of the same scene, and the fixture already knows how to drive it. | gui-stage (task-5), fixture |
| 21 | Intel UHD 630 leg (condition (d) on the GL-less path) | **U** | none | 153 reports in the workspace carry an Intel GL renderer (`Mesa Intel(R) UHD Graphics 630 (CFL GT2)`), and **0 of them set `-Dceltium.vulkan.glless=true`**: no Intel leg of the GL-less path has ever been taken. The newest Intel run is `intel-layout-glow` (09:01 today, `FAILED_PRESENTED_IMAGE`, pre-depth-fix, bridge leg), so nothing on the current tree says whether the Vulkan path works on Intel at all. Owner: task-11 (sky-draws). | sky-draws (task-11) |
| 22 | the user's own launch: menu and loading screen (task-22) | **V** (menu drawn and presented; the resize check in flight) | none - the user's configuration through the harness, with the Lead's capture as cross-check | Run `user-cfg-menu` (harness, no fixture, no validation client, explicit `--mod`, jar `8f3b34b68a1b2b80ad74`): 180 captures in `.celtium-work/acceptance/user-cfg-menu-caps/`, the game window mapped and photographed (`windows=[('Cleanroom', {x:0,y:0,width:1280,height:720})]`, `gamePid=2212088`). Best `compositor-0154.png`, sha256 `cfe88914b237a1dd…`, 46250 bytes, **921 distinct colours**, 720 distinct rows, mean (113.902,113.654,114.204); uniform black (1 colour) through capture 0040, then 731 at 0060 and 696-921 after. Log: `CELTIUM_WINDOW_RENDERER created=true attempt=1 ticks=0 reasons=[]`, `CELTIUM_PRESENT hookCalls=2 attempts=1 presented=1 failures=0 swapsSkipped=1`, `CELTIUM_SWAPPOINT calls=1..3`, all three glless lines, **VUID 0**. Independent cross-check: the Lead's `ww-menu` capture `compositor-0018.png` (sha256 `ade674e0be57ea63…`) at **889 distinct colours**, its predecessor uniform black. The Prism-launcher attempt is not the user's configuration (§6b). Shifted-menu fix (jar `8f3b34b68a1b2b80ad74`): check `user-real-real-resize2` in flight. | acceptance (task-22); menu fix = task-15; extent fix = Lead |
| 23 | mod cohort on the current jar (JEI; AppleSkin+MapWriter) | **V** (mods load and their UI draws) / verdict UNCALIBRATED | celeritas | **Reference taken**: `mod3b-jei-ref` (Celeritas `d12425eaaf184e79` + JEI `e592b0019aa0cd32`, fixture `f5b041ae841b2585`, same scene/pose, tick 201, `jeiScreenOpenedAtTick=36`, `UNCALIBRATED_PRESENTED_IMAGE` - correct, no calibrated jei+keep reference), `distinct=10751`, `nonBg=0.886132`. GL-less `mod3-jei` vs it: sky band 10189 px (6.6 %, mean 7.06), horizon 93408 (40.5 %), terrain centre 76341 (35.1 %), chat block 104347 (52.7 %), **hud strip 0 px**, **hotbar strip 0 px**, tooltip 240 px (3.75 %), whole frame 316029 (34.3 %, mean 19.19) - the same shape as the no-JEI inventory pair, so the residual is the GUI stage, not a JEI-specific defect; JEI's own grid, tooltip and recipe page draw correctly in the crops. GL-less, jar `609eb69a9064df05`, fixture `f5b041ae841b2585`, AMD RX 6400, scene=terrain, pose `0.5/12/0.5 yaw 180 pitch 20`. `mod3-jei` (JEI `e592b0019aa0cd32`, `jeiScreen`+`keepInventoryScreen`, capture tick 201): verdict `UNCALIBRATED_PRESENTED_IMAGE` (correct - the `screen=jei+keep` variant has no calibrated reference), `validationClean=true`, `validationMessageCount=0`, **VUID 0**, `distinct=5210`, `nonBg=0.92124`, `bg=0x8B8B8B`; per-format histogram `id1=564/564 id2=32393/32392 **id4=5916/5916** id5=55/55 id6=1579/1579 id7=463/463 id9=30/30` (format 4 = ITEM, i.e. the JEI grid). Crops `.celtium-work/acceptance/crops/mod3-jei/`: panel `(420,120,860,600)` 2670 distinct, mode `(139,139,139)` 16.5 %; tooltip `(560,280,880,400)` 654 distinct, white text 9.5 %; I looked at the panel crop - the JEI item grid, the "...ding Blocks" category label, the "**Gold Ore**" tooltip, the player slots and the hotbar are all drawn. `mod3-hud` (AppleSkin `4bbd37edecff0b42` + MapWriter `e9a0551013ab785e`, `keepGui=true`, tick 201): **`PASSED`**, `distinct=17080`, `nonBg=0.952757`, `bg=0xFFFFFF`, `validationClean=true`, `validationMessageCount=0`, **VUID 0**; histogram `id1=2/2 id2=23940/23939 id5=51/51 id6=528/528 id7=467/467 id9=12/12`. The game log lists `appleskin@1.0.14` and `mapwriter@2.8.10` and `Forge Mod Loader has successfully loaded 17 mods`; I looked at the presented frame - MapWriter's circular minimap with its coordinate readout and the vanilla toast are drawn, while **AppleSkin is loaded but its overlay is not visible in this frame** (no hunger bar is drawn in this scene), so AppleSkin's UI is unverified. Crops `.celtium-work/acceptance/crops/mod3-hud/`. Honest framing: the past cohort (`mod3-glless-jei` 01:10:56, `mod5-bridge-jei`, `mod6-bridge-hudmods`) is past evidence and **all three were FAILED_PRESENTED_IMAGE**; the current cohort's Celeritas reference with the same mods is queued (`mod3b-jei-ref`). `mod3b-jei-recipe` (same jar/fixture/JEI, `jeiRecipe=true`, capture tick 203): `jeiScreenOpenedAtTick=31`, **`jeiRecipeOpened=True`, `jeiRecipeOpenedAtTick=41`**, verdict `UNCALIBRATED_PRESENTED_IMAGE` (correct), `validationClean=true`, **VUID 0**, distinct=5017, nonBg=0.810433, bg=0xC6C6C6; histogram `id1=30/30 id2=11713/11712 **id4=1391/1391** id5=21/21 id6=623/623 id7=192/192 id9=30/30`. Crop `.celtium-work/acceptance/crops/mod3b-jei-recipe/recipe-panel.png`, which I looked at: the JEI **Crafting** recipe page with its `1/259` pagination, the crafting grid with items, the arrow, the output slot and the page buttons are all drawn. | gui-stage (task-5), pipelines (task-6), acceptance |

### 3a. Jar provenance per row

Every row above is marked by the jar it came from; where a row was superseded, the old numbers are
kept and this table says which jar produced them.

| row | candidate jar (bytes the run loaded) | reference jar | note |
|---|---|---|---|
| 1, 18 | **current** celtium `7ac31c7601be73c7c26a` (`cand3-terrain`, fixture `acfb62b16ed70ac3`); earlier `b074fbb9b659729e`, fixture `0355ffcd40c22a24` | celeritas `d12425eaaf184e79` (`ref3-terrain`, fixture `acfb62b16ed70ac3`; earlier `ref-celeritas2`) | the §6c pair, PASSED with `pictureFrameDelta_world=0` |
| 2 | celtium `7ac31c7601be73c7c26a` (current, 0 px) and `a106e84ac2f26a84` (0 px) → `b074fbb9b659729e` (100 %, superseded) | celeritas `d12425eaaf184e79` (`ref3-terrain`/`ref3-entity`, and `sky-draws-ref`) | dome fixed on both newer jars |
| 4 | celtium `7ac31c7601be73c7c26a` (34.9 % / 33.0 %); `a106e84ac2f26a84` (40.8 %); `b074fbb9b659729e` (66.7 %) | celeritas `d12425eaaf184e79` | rows 200-320 are the remaining difference |
| 5 | celtium `b074fbb9b659729e` / `ff68da097c102e47` (counter) | celeritas `d12425eaaf184e79` (`ww7-ref-cloud`, fixture `1ac3021812f75aba`) | no GL-less picture to pair with |
| 6 | celtium `f21bc38693c8dadb` (`ww6-gl-water`) | celeritas `d12425eaaf184e79` (`ww7-ref-water`: `NO_CAPTURE`) | no usable pair |
| 7, 8 | celtium `f21bc38693c8dadb`, fixture `f03a129610323819` | celeritas `d12425eaaf184e79` (`ww7-ref-base`, fixture `1ac3021812f75aba`) | `id5=142/142`, no `id0` |
| 9, 10 | celtium `ac347ff1f98e56ad` (counters only) | none | - |
| 11-14 | celtium `3a94b9dd3140…` (items) and `216cd6284a3c22f7` (HUD/chat/crosshair), fixture of that hour | stored bridge picture of the same run | stored leg is void for terrain |
| 17 | celtium `03c627cc52c8e566`, fixture `105598e0f89be16d` | self-comparison | one flag apart |
| 19 | (plan §0.144 jar, not kept here) | stored | - |
| 20, 21 | none current | stored / none | see the rows |
| 22 | celtium `8f3b34b68a1b2b80ad74` (`user-cfg-menu`, harness, no fixture) and `609eb69a9064df05` (`resize-menu`) | none - the user's configuration; cross-check `ww-menu` harness jar `eed0f86f03402db5` | menu drawn: 921 distinct colours; resize verified at 1920x1080 |
| 23 | celtium `609eb69a9064df05`, fixture `f5b041ae841b2585`, JEI `e592b0019aa0cd32` | Celeritas + JEI leg in flight | mods load and their UI draws; verdict UNCALIBRATED (no calibrated jei+keep reference) |


Cross-cutting counters from `acc2-glless`, the most recent GL-less run I read in full:

- `CELTIUM_ACCEPTED byFormat=[0:0 1:2 2:49052 … 4:3 5:0 6:888 7:792 8:128 9:12 10:0]` - id 10
  (`POSITION_NORMAL`, added in `95d74bac6`) accepted **0**; id 5 accepted **0**; id 8 frozen at 128.
- `CELTIUM_REFUSALS_BY_FORMAT {0 format 0 (unmapped) not in celtium.vulkan.immediateFormats=112}`
  and the histogram's `id0=122/0`: **112-122 batches per run are refused as an unmapped format**.
  Under GL-less a refused batch is drawn by nobody. A missing counter is not a missing draw, but here
  the counter is present and the acceptance is zero, so those batches are missing until the format
  table takes them.
- `CELTIUM_DRAWARRAYS glDrawArraysCalls=667 drawn=157 refused=510`.
- `CELTIUM_NOOP_CENSUS noopEntryPoints=46 noopCalls=582181 top=[glTexCoord2f=199184 glVertex3f=199184
  glEnd=49796 glBegin=49796 …]` - cumulative over the process, so it includes menus and loading
  screens and must not be read as in-world text; named here so nobody re-derives it.

## 4. Claims contradicted by measurement

- **`plan.md` line 51 / N10**: the capability-based depth format was "no effect on AMD, measured" and
  "the GL-less target and the terrain pipeline share the same decision". The measurement behind it
  reported which format the chooser *would* pick and never ran a bridge leg, where nothing called the
  chooser. True about the format, false about the configuration. §1a.
- **"`n03-ae-terrain-bridge` passed"** as a usable reference: `PASSED` at `nonBg=0.813402` on a
  sky-only frame whose mode was the chat background - as did `n03-ah` (0.809351), `n03-ai`
  (0.820577), `n03-aj` (0.810275) and `n03-x` (0.505074). §2.
- **"a reference with `nonBg >= 0.4` is usable"**: seven void runs satisfy it, three of them reporting
  PASSED at 0.81. The rule cannot separate a drawn world from a HUD element over a flat sky; `passes`
  or per-layer section counts can. §2.
- **"the sky dome is 100 % wrong"** and **"format 5 is offered nowhere"** were true of the jars
  measured at 13:47 (`b074fbb9b659729e`) and are false on `a106e84ac2f26a84` (dome: 0 of 256000 px
  differ in rows 0-199) and on `f21bc38693c8dadb` (`id5=142/142`, no `id0`). Rows 2 and 8 carry both
  readings, with the jar each came from. A verdict in this project is a verdict about bytes.
- **"the bridge leg draws with real OpenGL and is the reference"**: not for the world - every bridge
  report says `Terrain Backend: vulkan`; and as our own interop path it compared our code with our
  code. §1b.
- **"the GL-less frame is globally darker"** (task-8's brief): it was a GUI frame compared against a
  world frame. Against the stored picture of the same jar the terrain centre differs by 1 px of
  204800, the bottom-right corner by 0, the HUD strip by 0, the hotbar strip by 0, the tooltip by 0,
  the chat block by 2, and every whole-frame difference is in the sky and horizon bands. No
  darkening, no offset. The same shape repeats against the older stored reference
  (`scene-floors/terrain-a`, §6), so it is not an artefact of one pair.
- **"the Celeritas reference leg works with the current fixture"**: take 1 died with
  `NoClassDefFoundError: org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend` at
  `FMLModContainer.gatherAnnotations` because the fixture jar `0355ffcd40c22a24` still named Celtium
  classes (3 references; 0 in `1ac3021812f75aba`). §1c.
- **"clouds are accepted in frame 1 only"**: confirmed by me in two independent runs
  (`acc2-glless`, `ww-gl-cloud`): id 8 reaches 128 and never changes while every other id grows.
- **"rain (format 5) is absent from every histogram"**: absent from the runs that set `weather=true`
  and from `acc2-glless`, but `fogoff` (no particle flag) has `id5=48/48`. The claim holds for the
  weather scene only.
- **Jar hashes as content identity**: the celtium jar is reproducible (unchanged sources → same
  sha256; entries timestamp-normalised to 1980-02-01), the **fixture jar is not** (entries carry real
  timestamps: two rebuilds with no source change gave `348b5747b02251a5` then `0355ffcd40c22a24`,
  and the Celtium-free build differs again at `1ac3021812f75aba`). Quote the fixture hash as the
  bytes that ran, never as "the same fixture build". A frozen jar can also be a commit behind:
  `ff68da097c102e47` was frozen by another worker as "tree `95d74bac6`", but that commit is 13:31:48
  and a build of it is `b074fbb9b659729e`.
- **Concurrency**: 13:39:20 two GL-less instances (`entity-runs/ent-glless`, `runtime/p2-glu-on`);
  13:44 three (`fc8-br`, `tm-glless1`, `acc2-ref-bridge`). Those runs' timings and pixels are not
  admissible; the slot lock (`67cf6f806`) is the fix.
- **An instrument fix, not a renderer finding**: my first real-session leg found no game window
  because the Xwayland window has **no class and is titled "Cleanroom"** (read from the compositor
  capture record: `windows=[('Cleanroom', {x:0,y:0,width:1280,height:720})]`), while the matcher
  looked for class `Cleanroom` or "Minecraft" in the title. The honest instrument accepts **any window
  that was not there before the launch**. The real-session path was then dropped in favour of the
  headless compositor-resize path (`resizeDuringMenu` + `resizeViaCompositor`), because a window
  appearing on the operator's desktop is the thing the user complained about.
- **An orphaned game instance ran for ~15 minutes**: pid 2204233, a leftover of a harness whose
  launcher died at 16:49, overlapped other workers' legs until the Lead killed it. Any leg from that
  window (roughly 16:49-17:05) is suspect and is not used as a reference here.
- **An empty fixture jar is a silent no-run**: a fixture jar copied while a build was still writing it
  is a **22-byte empty zip** (`PK\x05\x06`); FML then logs `FML has found a non-mod file
  celtium-validation-client-…jar` while Cleanroom's discovery lists only `scalar, celeritas, fugue`,
  and the run comes back `MISSING_REPORT` with an otherwise healthy log. Before concluding anything
  about a leg that "did not run", check `stat -c %s` of the jar in the run's `mods/` directory; copy
  fixtures only after the build has finished, or use a frozen one. Every frozen jar I used was
  checked this way and none is empty (sizes 102761-2488001 bytes).
- **A counter that stops is not a stopped picture** (the mirror of "a missing counter is not a missing draw"): in `user-cfg-menu` (jar `8f3b34b68a1b2b80ad74`) the log holds only `CELTIUM_SWAPPOINT calls=1..3` and `CELTIUM_PRESENT hookCalls=3 attempts=2 presented=2 failures=0` over a 90 s menu session, yet consecutive compositor captures differ by ~5000 of 921600 px (0.55 %) with a maximum channel difference of 254, localised to `(690,113)-(1122,286)` - the logo/splash region, which only changes if new frames reach the screen. So the window-time present path is not covered by the counter the acceptance text names ("CELTIUM_SWAPPOINT rising"); the presentation evidence for row 22 is the pixels, and the counter's coverage is a question for the owner (task-15 / frame-composite), not a rendering failure.
- **A tick-keyed capture is a different build state, not a different renderer**: at the fixture's tick
  200 our leg had only 106-162 world frames against the reference's more, so listed-section counts of
  22 vs 32 and 34 vs 41 were build throughput, not culling. The horizon band's 34.9 %/40.8 %/66.7 %
  readings were that artefact; at equal build state the band differs by 0.484 % (row 4).
- **Two harness wrinkles, both measured and one now fixed**: at 13:45 the GL-less leg died with
  `SyntaxError: invalid syntax` at `prism_validation.py:297` while the file was mid-edit - read that
  as "the instrument was being edited", not as a result; and a run killed while *waiting* for the
  slot left its game directory already created (the copy happens before the slot wait), which blocked
  the retry with `File exists`. `b0b1877a8` fixes the second: `prism_validation.py:202-209` now
  reclaims a directory that holds **no** `logs/celtium-validation.json` and **no** `screenshots/`,
  and still refuses one that holds evidence. I read that code path; I did not re-take a game slot to
  exercise it, so it is the Lead's measurement, not mine.

## 5. Claims I could not verify (no measurement exists)

- **stars** (row 3): no night scene; the fixture pins `worldTime=6000`.
- **clouds, water, weather, particles** (rows 5-8): the pairs that exist do not isolate their stage
  (same 222-row non-background profile in the cloud and water GL-less frames, grass in both water
  legs, no format 5 in the particle leg).
- **entities and TESR** (rows 9-10): the one current attempt ran concurrently; the older reference is
  uncalibrated and void for terrain.
- **selection outline, block-breaking overlay** (rows 15-16): no run today, no counter.
- **post-processing chains** (row 18): `postChains=0 postPasses=0` in every run I read.
- **the panel floor counters** (`panelChangedFraction`, `panelStrongFraction`,
  `panelDistinctColours`): they appear in `sceneGateFloors` but I found no run whose report carries
  their measured values.
- **video settings** (row 20): the scene and its stored references exist, but no run on the current
  tree and no Celeritas leg; the newest attempt is 12:31 and `FAILED`.
- **the Intel UHD 630 leg** (row 21): 153 Intel reports, none GL-less, newest 09:01 and pre-fix. The
  completion condition's Intel requirement is entirely unmeasured on the Vulkan path.
- **my own Celeritas leg**: it is written and pinned (`.celtium-work/acceptance/acc3-celeritas-ref.sh`)
  but never got the slot - 900 s `--slot-wait` expired behind other workers' legs. The rows that
  need it rest on `ref-celeritas2`/`sky-draws-ref`, two legs taken by other workers that I verified
  from their own reports and pictures; their agreement with each other (nonBg 0.761173 on two fixture
  builds) is what makes them usable without my own run.
- **anything not in a run's own report or in a crop I opened**: where a teammate's run is the source,
  §3 says so; I did not re-run their jar.

## 6. The matched GL-less leg, and the comparisons that exist

Same jar both legs (`b074fbb9b659729e`), same fixture (`0355ffcd40c22a24`), same GPU, scene, pose and
tick; the only difference is `-Dceltium.vulkan.glless=true`, and the GL-less leg took the slot after
the harness lock existed (`67cf6f806`). GL-less: `worldFrameDistinctColours=1477`, `nonBg=0.72661`,
`verticalBands=509`, `blackFraction=0.0`, `bg=0x571716`, `passes=1063 skipped=0`,
`presentedImageVerdict=PASSED`, `pictureEvidenceSource=presented-dump`.
`scripts/cpu-contract/vanilla_stage_compare.py` between the GL-less presented dump and the stored
bridge screenshot of the same jar:

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

**The Celeritas comparison** — `acc2-glless` GL-less presented dump (jar `b074fbb9b659729e`) against
`ref-celeritas2` (upstream Celeritas `d12425ea…`, same fixture build `1ac3021812f75aba`, same scene,
pose and tick 200), crops `.celtium-work/acceptance/crops/acc3/`:

| stage | mean | >8 | pixels>8 | GL-less mean rgb | Celeritas mean rgb |
|---|---|---|---|---|---|
| sky band (top) | 38.108 | 100.0000 % | 153600 | (120.0,167.0,255.0) | (158.1,192.9,255.0) |
| horizon band | 43.569 | 66.6549 % | 153573 | (129.9,135.0,170.3) | (147.8,154.3,176.0) |
| terrain centre | 0.885 | 2.6801 % | 5832 | (103.1,66.4,60.7) | (103.8,66.8,61.2) |
| bottom-left corner | 0.007 | 0.0078 % | 4 | (101.9,56.2,51.6) | (101.9,56.2,51.6) |
| bottom-right corner | 0.003 | 0.0078 % | 4 | (101.9,56.2,51.6) | (101.9,56.2,51.6) |
| hud strip (bottom) | 0.002 | 0.0059 % | 6 | (101.6,27.8,26.2) | (101.6,27.8,26.2) |
| chat block | 0.742 | 2.3919 % | 4736 | (101.7,63.6,58.1) | (102.3,63.9,58.5) |
| hotbar strip | 0.000 | 0.0000 % | **0** | (103.6,23.2,22.2) | (103.6,23.2,22.2) |
| tooltip region | 2.752 | 9.2656 % | 593 | (103.6,64.9,59.6) | (105.6,66.4,61.0) |
| whole frame | 17.820 | 35.1962 % | 324368 | (112.3,96.5,117.1) | (123.5,105.9,118.8) |

Reading: the **sky band is 100 % different and the two references agree on it** (both stored bridge
and Celeritas give `(158.1,192.9,255.0)` at the top where GL-less is flat `(120,167,255)`), so the
sky defect is not an artefact of the bridge. The **horizon band** differs more against Celeritas
(66.7 %) than against the same-jar stored picture (46.9 %): the Celeritas leg's dome reaches further
down. Below the horizon the two legs are close but not identical - terrain centre 5832 px of 204800
at mean 0.885, bottom corners 4 px each, HUD strip 6 px, hotbar 0 - which is the size of the
difference between upstream Celeritas' GL terrain path and Celtium's Vulkan terrain path, and it is
the first independent measurement of that difference on this tree. Row 1 therefore rests on
Celeritas now, with the same-jar stored picture as the regression check.

**The same shape on the older stored reference** (`van-glless` GL-less 12:56, jar `ac347ff1f98e56ad` vs
`scene-floors/terrain-a` 09:11, jar `478cf85555ec37a9`) - **different jars on the two sides, so this
corroborates and does not decide**: terrain centre 1 px of 204800, bottom-left 1, bottom-right 0, hud
strip 0, chat block 2, tooltip 0, horizon 460 (0.2 %), sky band 134870 (87.8 %), whole frame 135333
(14.68 %). Terrain and composite repeat across two jars and two tree states; the sky difference does
not.

### 6a. The superseding comparisons on the newer jars

**Sky, jar `a106e84ac2f26a84` (fixture `77453688bac95a4b`) vs `sky-draws-ref`** (Celeritas
`d12425ea…`, fixture `f03a129610323819`), same scene/pose/tick 200, computed by me from the two
pictures:

| region | pixels | >8 | fraction | mean | max |
|---|---|---|---|---|---|
| rows 0-199 (sky + upper dome) | 256000 | **0** | 0.0000 % | 0.844 | 4 |
| sky band (0,0,1280,120) | 153600 | **0** | 0.0000 % | 1.365 | 4 |
| horizon band (0,120,1280,300) | 230400 | 93992 | 40.7951 % | 30.212 | 252 |
| terrain centre (320,300,960,640) | 217600 | 5832 | 2.6801 % | 0.884 | 227 |
| whole frame | 921600 | 111187 | 12.0646 % | 8.356 | 252 |

Row-band profile of the difference (candidate / reference means): rows 120-199 identical; `200-219`
candidate `(176.7,202.9,249.3)` vs reference `(161.6,181.7,214.1)`; `220-239` `(120.3,109.4,115.4)`
vs `(113.8,112.2,102.0)`; `240-259` `(101.8,68.0,62.2)` vs `(112.7,108.2,97.5)`; `260-299` and
`300-319` decaying. The candidate is brighter and less hazy in exactly the band where Celeritas fogs
distant terrain. The **control** on the same jar with format 7 refused
(`sky-draws-final-control`) has **1 distinct colour** in rows 0-199 and differs over 52.9 % of that
band, which is what makes the 0-pixel result a measurement of the dome and not of a missing
screenshot. The terrain-centre number is **5832 px of 217600, mean 0.88** - the same figure the older
pair gives against Celeritas, so the Celtium-vs-Celeritas terrain difference is stable across two
jars, while the sky difference is not.

**Weather + particles, jar `f21bc38693c8dadb` (fixture `f03a129610323819`) vs `ww7-ref-base`**
(Celeritas, fixture `1ac3021812f75aba`), same scene/pose/tick 200: sky band 20.7 % >8 (mean 6.1),
horizon 54.0 % (mean 30.8), terrain centre 16.1 % (mean 6.7), whole frame 26.5 % (mean 12.7), every
region mean within about one count. `ww6-gl-base` also carries `id5=142/142` with no `id0` line, so
the particle batches are accepted and the difference that remains is content (rain is stochastic
between runs), not a missing format. The matching cloud and water legs have no usable pair
(`ww6-gl-cloud` produced no report; `ww7-ref-water` is `NO_CAPTURE`).

### 6b. The user's own launch (task-22), and the instrument that photographs it

**What the user does.** Prism Launcher launches the real instance `cleanroom-0.6.13-alpha`; that
instance's `instance.cfg` carries `JvmArgs=-Dceltium.vulkan.glless=true` with
`OverrideJavaArgs=true`, so the GL-less property is part of the user's launch and of nothing else.
It was cleared once during this session (the file read `JvmArgs=` at 15:16, which would have made a
Prism launch a *bridge* launch and the whole task a measurement of the wrong path); the Lead restored
it and it is now the configuration under test - nobody edits it again.

**Why the harness cannot stand in.** `prism_validation.py` never reads `instance.cfg`'s `JvmArgs`
(§1d) and always drives a fixture with `--mod` files. The user's launch has no fixture, no validation
client, and its JVM arguments come from the instance. A harness leg with `--jvm-arg=…glless=true` is a
useful candidate, but it is not this configuration, so the verdict leg is taken through Prism itself.

**The instrument** is `.celtium-work/acceptance/user-prism.py`:

- launches `~/Applications/PrismLauncher-11.1.0.AppImage --launch cleanroom-0.6.13-alpha --offline
  CeltiumUser` on a private GPU-rendered Sway output (1280x720) owned by
  `headless_wayland.HeadlessWayland`, so nothing appears on the user's desktop and the window can be
  photographed;
- removes `DBUS_SESSION_BUS_ADDRESS`/`DBUS_SESSION_BUS_PID` from the launch environment, because the
  user's own Prism (9.4) is running on their session bus and a second launcher that tries to register
  the same app id with the portal exits during startup;
- photographs the output with `grim` every second and measures each PNG with
  `compositor_capture_report.measure`, which reports **`distinctColours`** and `distinctRows` by
  `numpy.unique` over the pixels - a count, not a "non-black" test: a flat clear colour scores 1, a
  menu scores in the hundreds or thousands, and a uniform capture reports its single RGB;
- records the compositor's window tree at each shutter, fullscreens the Cleanroom window when it
  appears, and records the game's pids;
- ends only processes whose command line carries this instance's game directory, so it can never kill
  a teammate's harness run, and it takes the same `.celtium-work/game-slot` lock as the harness;
- afterwards reads the instance's own `minecraft/logs/latest.log` and reports the lines that decide
  the GL-less questions: `CELTIUM_GLLESS stub GL provider installed`,
  `CELTIUM_GLLESS window hinted GLFW_NO_API`, `CELTIUM_GLLESS splash progress skipped (no shared GL
  context)`, `CELTIUM_SWAPPOINT calls=N`, and the `CELTIUM_ACCEPTED`/`CELTIUM_DRAWARRAYS` censuses if
  they appear.

**Instrument checks already taken, so the verdict leg is not also an instrument debug:** Prism 11.1
starts on the private display and stays alive (25 s, `live.check` written), its window is
`app_id=org.prismlauncher.PrismLauncher` titled "Prism Launcher 11.1.0", and `grim` captures it
(67347 bytes) - `.celtium-work/runtime/prism-smoke/`.

**First `--launch` attempt (`user-prism-launch-check`, 16:24-16:27): the instrument worked, the jar
was stale, and the leg is not the user's configuration.** 48 captures in 115 s, every one with
pixels, the largest **820 distinct colours** (`capture-0002.png`, sha256 `8b5621ed9a2bb4c0…`, 16768
bytes) - but the compositor's window tree at every shutter holds **only Prism's own dialog**,
"Account refresh failed — Prism Launcher 11.1.0" (504x104 floating), and `gamePids=0`. The
instance's own log for that window is unambiguous:
`CELTIUM_GLLESS stub GL provider installed`, `CELTIUM_GLLESS window hinted GLFW_NO_API`,
`CELTIUM_GLLESS splash progress skipped (no shared GL context)`, `CELTIUM_SWAPPOINT calls=1 … 2700`,
and **`CELTIUM_WINDOW_RENDERER failed: attempt=1..8 error=java.lang.reflect.InvocationTargetException`**.
The instance's mod jar at that moment was `bf5a4e144965d83b` (15:16) - a build from before task-15.

**Correction, and it is mine to record.** A GUI launcher cannot be driven headlessly: the captures
show the launcher's modal, not the game, and no game window was ever mapped on that output. So that
leg is **not** the user's configuration and its PNGs are **not evidence about the renderer** - only
about the instrument (Prism starts, grim captures, the distinct-colour counter reads a real window).
The user-configuration leg is taken instead through the harness with the user's instance and an
explicit `--mod`, no fixture and no validation client, with `--jvm-arg=-Dceltium.vulkan.glless=true`,
and each run's captures in **its own directory** (the shared one collides across runs).

**Cross-check of the reported menu capture, by my own measurement.** The Lead's
`.celtium-work/ww-menu-caps/compositor-0018.png` is 1280x720, sha256 `ade674e0be57ea63…`, 44841 bytes,
**distinctColours=889**, `distinctRows=720`, meanRGB (113.894, 113.646, 114.231), not uniform - and the
same directory's `compositor-0000.png` is **uniform black (1 distinct colour)**, with
`compositor-0010.png` at 831. That is a quantified before/after of the user's complaint ("a window
with nothing in it") in one capture directory; it is a *harness* leg on jar `eed0f86f03402db5`
(`ww-menu`), not a Prism launch, so it supports the menu row without being it.

**The swapchain/surface-extent fix, verified on the headless compositor (jar `609eb69a9064df05`).**
The user reported the menu drawn but **shifted**, right side and logo cut off, unchanged by
fullscreen. Cause, diagnosed by the Lead: `ensureSwapchain`'s surface-extent test was gated on
`frameId`, a *world-frame* counter, so a window resized while the menu was up kept the swapchain from
its first present and the bottom-anchored unscaled blit showed only the frame's lower left. The
independent check is `resize-menu` (`.celtium-work/acceptance/resize-headless.sh`): the fixture drives
the transition headlessly with `-Dceltium.validation.resizeDuringMenu=true` and
`resizeViaCompositor=true`, which runs `swaymsg output HEADLESS-1 mode WxH@60Hz`, and
`compositor_capture.py` photographs the output throughout. Measured:

| evidence | value |
|---|---|
| `resizeCompositorResult` | `mode=1920x1080@60Hz exit=0 out=[ { "success": true } ]` |
| `resizeReason` / `resizeRequestedAtTick` / `resizeMenuHeldTicks` | `menu` / 0 / 40 |
| `resizeWindowFrom` → `resizeWindowTo` | `1280x720` → `1920x1080` |
| `CELTIUM_RESIZE_OBSERVED` | `tick=0 from=1280x720 to=1920x1080` |
| `CELTIUM_PRESENT` | `blitted 1280x720 onto image 0`, then **`blitted 1920x1080 onto image 1`** |
| final report `swapchain` | `platform=x11 format=44 … size=1920x1080` |
| capture coverage at 1920x1080 (`compositor-0040/0042`) | black fraction **0.0**, top edge `(150.3,187.6,255.0)` sky, bottom `(103.5,23.0,22.1)` ground, left/right content |
| capture coverage at 1280x720 (`user-cfg-menu` `compositor-0154`) | black fraction 0.0222, top `(247.9,247.9,247.9)`, bottom `(45.7,…)`, left/right content |

So the presented frame follows the window size and fills it at both sizes. One honest gap: in
`resize-menu` the mode change fires at tick 0, before the menu has content, so the 1280x720 *content*
evidence comes from the separate `user-cfg-menu` leg rather than from the same run; a leg with
`resizeAtTick` in the world would give both sizes in one series, and that is not taken. The
real-session path (`user-real.py`) was dropped in favour of this one - a window on the operator's
desktop is the thing the user complained about - and its first attempt failed on an instrument bug
(the Xwayland window has no class and is titled "Cleanroom"), which is recorded in §4 as an
instrument fix.

**The Prism-launcher path was dropped, not re-run.** A second `user-prism-verdict` leg was queued and
then stopped, because the correction above makes the approach invalid rather than merely unlucky: a
GUI launcher cannot be driven headlessly, so its captures can never be the game's window. The
user-configuration row is taken through the harness with the user's instance, an explicit `--mod`, no
fixture and no validation client, in its own capture directory.

### 6c. The task-22 harness legs: GL-less against the ref3 Celeritas references

Candidate jar `7ac31c7601be73c7c26a` (weather-water's presented-frame counter reset; frozen copy
`.celtium-work/acceptance/frozen/cand3-celtium-7ac31c76.jar`), fixture `acfb62b16ed70ac3` (the same
fixture as the references), AMD Radeon RX 6400, scene/pose/ticks as §1d. Scripts `cand3-legs.sh`,
`cand3-compare.sh`.

| leg | verdict | distinct | nonBg | bg | `pictureFrameDelta_world` | `passes`/`skipped` |
|---|---|---|---|---|---|---|
| `cand3-terrain` | **PASSED** | 2054 | 0.759463 | `0x571716` | **0** | 969 / 0 |
| `cand3-entity` | UNCALIBRATED (variant differs - correct) | 2927 | 0.773387 | `0x571716` | **0** | 1027 / 0 |
| `cand3-inventory` | UNCALIBRATED (variant differs - correct) | 4892 | 0.923916 | `0x8B8B8B` | **0** | - |

**A GL-less acceptance run reaches PASSED** (`cand3-terrain`, jar `7ac31c76`), with
`pictureFrameDelta_world=0`, so the identity gate is green on this jar and the picture comparison is
accepted, not merely measured. The two `UNCALIBRATED` verdicts are the correct outcome for variants
that are not the calibrated plain-terrain one, exactly as the reference legs are.

Stage comparisons, GL-less presented dump against the Celeritas picture of the same fixture/scene
(ticks: terrain 207 vs 200, entity 202 vs 200, inventory 200 vs 200 - the terrain and entity captures
landed a few ticks after the reference's, which the pinned scene makes immaterial; the sky band is
pixel-identical at 0 px, which is what shows the tick did not matter):

| stage | terrain: >8 (mean) | entity: >8 (mean) | inventory: >8 (mean) |
|---|---|---|---|
| sky band (top) | **0 px** (1.365) | **0 px** (1.365) | 128052 px 83.4 % (14.849) |
| horizon band | 80380 px 34.9 % (21.818) | 75959 px 33.0 % (36.668) | 122130 px 53.0 % (15.411) |
| terrain centre | 5770 px 2.65 % (0.868) | 6663 px 3.06 % (1.833) | 78079 px 35.9 % (18.890) |
| bottom-left / bottom-right | 4 / 4 px | 4 / 4 px | 19959 / 10356 px |
| hud strip | 6 px | 6 px | 7389 px |
| chat block | 4714 px 2.38 % | 4338 px 2.19 % | 128201 px 64.7 % |
| hotbar strip | **0 px** | **0 px** | **0 px** |
| tooltip region | 593 px 9.27 % | 347 px 5.42 % | 240 px 3.75 % |
| whole frame | 95460 px 10.36 % (6.228) | 91932 px 9.98 % (10.167) | 442175 px 47.98 % (14.054) |

Reading, with the jar named: the **sky dome now matches the Celeritas reference exactly** in the
terrain and entity scenes (0 of 153600 px, mean 1.365, max 4 - the same sub-tolerance residual the
sky-draws pair shows), so rows 2 and 4 of §3 are superseded again, this time by a *Celeritas*
comparison on the counter-reset jar. The **horizon band remains the largest sky-side difference**
(34.9 % / 33.0 %, the GL-less leg brighter and less hazy than the reference), and the terrain
centre's ~2.7-3.1 % at mean <2 counts is the stable Celtium-vs-Celeritas terrain difference first
measured in §6a. The inventory scene is the one that does not match: 48 % of the frame differs, and
the sky band behind the GUI is 83.4 % different, while the hotbar strip is 0 px in all three scenes.

**The inventory difference is same-variant, checked before it is called a defect.** The two reports'
`sceneCalibrationKey` are identical - `… entities=none screen=jei+keep guiScale=3` - on the same
fixture `acfb62b16ed70ac3`, the same pose and both `captureStartTick=200`, and both are
`UNCALIBRATED_PRESENTED_IMAGE` because the stored reference key is `screen=none` (so neither can
match by construction). The flags differ only in the candidate's Celtium-only properties
(`vulkan.glless`, `vulkan.validation`, `vulkan.presentReadback`, `validation.dumpPresentedImage`),
inert in a Celeritas leg, and in the reference's `validation.screenshotAtTick=60`, which writes an
extra `celtium-at-tick-60.png` and does not change the tick-200 picture. So the 48 % is a GUI-stage
difference, not a variant artefact, and it is routed to gui-stage with those numbers.

## 7. Instruments and artifacts

Reused, not added: `scripts/cpu-contract/vanilla_stage_compare.py`, `compare_jei_pair.py`, the
reports' `rendererDebug` lines, the per-format histogram
(`immediate formats offered/accepted: idN=x/y`), `celtium/vulkan-check`
(`passes skipped lastDraw lastSections skipReason`), `CELTIUM_ACCEPTED byFormat=`,
`CELTIUM_DRAWARRAYS`, `CELTIUM_REFUSALS_BY_FORMAT`, `CELTIUM_SKY_REFUSAL`, `CELTIUM_NOOP_CENSUS`.
Format ids used above are from `VulkanImmediateFormats`: 1 POSITION_COLOR, 2 POSITION_TEX_COLOR,
3 BLOCK, 4 ITEM, 5 PARTICLE, 6 POSITION_TEX, 7 POSITION, 8 POSITION_TEX_COLOR_NORMAL,
9 POSITION_TEX_NORMAL, 10 POSITION_NORMAL.

Added by me, all read-only or under `.celtium-work/acceptance/`:

- `rep.py` - prints the fields this table quotes from a run's own `logs/celtium-validation.json` plus
  its lock file. Every number in §1-§3 and §6 comes from this reader.
- `acc2-pair.sh` / `acc2-compare.sh` - the matched GL-less/bridge-era pair and its stage comparison;
  frozen jars in `frozen/`.
- `acc3-celeritas-ref.sh` / `acc3-compare.sh` - the Celeritas reference leg and its comparison.
- `ref3-legs.sh` - the three fresh reference legs of §1d, frozen fixture `ref-fixture-acfb62b1.jar`.
- `user-prism.py` - the user's own Prism launch on a private headless output, photographed with grim
  and measured by distinct-colour count (§6b); `user-leg.py` is the same idea built on the harness's
  own launcher, kept for instrument comparison only.
- `bridge-ab.sh` - the A/B in §1a (inherited).
- Crops: `crops/acc2/`, `crops/terrain-recheck/`, `crops/hotbar-…png`, `crops/crosshair-…png`,
  `crops/chat-…png`, `crops/ww-base/`.

Instrument controls I ran, so the numbers can be trusted: same image twice → 0 px >8 in every region;
two stored bridge runs of the same scene 28 minutes apart → **byte-identical**; two different poses →
100 % of pixels >8; my own recomputation of `worldFrameNonBackgroundFraction` from the presented PNGs
of `ww-gl-cloud`/`ww-gl-water` reproduces 0.308333 and 0.691667 exactly.
