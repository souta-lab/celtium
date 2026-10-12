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
| 3 | stars | **V** (drawn at night) | self | `r11-night` (jar `7bb1746041575ef2`, fixture `d860e46679320d05`, GL-less, scene=terrain, pose `0.5/12/0.5 yaw 180 pitch 20`, `worldTime=18000`, equal build state with `r11-day`, `PASSED`): the night sky band holds point-like pixels at ~`(68,69,72)` against a background median of 18 (max 96 in rows 0-250), and `r11-day` at `worldTime=6000` has none. So the star path draws. **What is not established**: whether the star brightness is vanilla's - no Celeritas night reference exists, and the mirrored star group's alpha (`(0.046,0.046,0.046,0.046)`, refused by the capability gate in earlier GL-less runs) is the known suspect. | sky-draws (task-3) |
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
| 20 | video settings (the screen, its widgets and its own frame) | **W** on the stored floor, **V** on the re-derived one (task-47) | celeritas | The scene: `VIDEO_SETTINGS("videoSettings", true, true)`, `GuiVideoSettings` opened through the fixture's own click, knob `-Dceltium.validation.scene=videoSettings`. **The pair is now same-era and same-picture**: `vs47-cand` (GL-less, jar `f27f07f27253f79a`, fixture `396d40cd6043ded4`, tick 317) GUI frame = 145 distinct colours / 229 bands / nonBg 0.672704 / black 0.068322, panel 133 colours, changed 1.0, strong 0.998936, rowAmplitude 4.089, panel luma 57.783 against the world's 113.658 - **the same numbers to the digit as the upstream Celeritas reference** `vs-pic-ref` (145/229/0.672704/0.068322). It still failed, and the reason was the floors, not the picture: `verticalBands>=250` and `blackFraction<=0.05` were derived from the scene's **world** frame, and `panelDistinctColours>=4000` from a bridge GUI frame in which the screen's backgrounds did not draw. Re-derived from the reference's own GUI frame by the same margin rules (nonBg>=0.37, bands>=89, black<=0.118, panelChanged>=0.63, panelStrong>=0.545, panelDistinct>=33 presence guard, panelRowAmplitude>=0.25, **panelMeanLuma<=0.70x world**): **`vs47d-cand` PASSED** (GL-less, jar `5f94fc293154bd72`, fixture `01032550495d088c`, 0 assertion failures, `guiFrameGateFloors nonBackgroundFraction>=0.37 verticalBands>=89 blackFraction<=0.118`, `guiFrameGateSource` naming the upstream Celeritas GUI frame) and the bridge control **`vs47d-ref` FAILED on exactly one floor** - the luma ceiling (panel 87.496 above 0.7x the world's 109.524, ratio 0.799) - which is the backgrounds-missing frame being caught, not a correct picture rejected. Pictures preserved in `.celtium-work/acceptance/crops/vs47d/` (candidate GUI frame sha256 `bc5e8b060faf534e…`). §6d. | gui-stage (task-5), acceptance (task-47) |
| 21 | Intel UHD 630 leg (condition (d) on the GL-less path) | **U** | none | 153 reports in the workspace carry an Intel GL renderer (`Mesa Intel(R) UHD Graphics 630 (CFL GT2)`), and **0 of them set `-Dceltium.vulkan.glless=true`**: no Intel leg of the GL-less path has ever been taken. The newest Intel run is `intel-layout-glow` (09:01 today, `FAILED_PRESENTED_IMAGE`, pre-depth-fix, bridge leg), so nothing on the current tree says whether the Vulkan path works on Intel at all. Owner: task-11 (sky-draws). | sky-draws (task-11) |
| 22 | the user's own launch: menu and loading screen (task-22) | **V** (menu drawn and presented; the resize check in flight) | none - the user's configuration through the harness, with the Lead's capture as cross-check | Run `user-cfg-menu` (harness, no fixture, no validation client, explicit `--mod`, jar `8f3b34b68a1b2b80ad74`): 180 captures in `.celtium-work/acceptance/user-cfg-menu-caps/`, the game window mapped and photographed (`windows=[('Cleanroom', {x:0,y:0,width:1280,height:720})]`, `gamePid=2212088`). Best `compositor-0154.png`, sha256 `cfe88914b237a1dd…`, 46250 bytes, **921 distinct colours**, 720 distinct rows, mean (113.902,113.654,114.204); uniform black (1 colour) through capture 0040, then 731 at 0060 and 696-921 after. Log: `CELTIUM_WINDOW_RENDERER created=true attempt=1 ticks=0 reasons=[]`, `CELTIUM_PRESENT hookCalls=2 attempts=1 presented=1 failures=0 swapsSkipped=1`, `CELTIUM_SWAPPOINT calls=1..3`, all three glless lines, **VUID 0**. Independent cross-check: the Lead's `ww-menu` capture `compositor-0018.png` (sha256 `ade674e0be57ea63…`) at **889 distinct colours**, its predecessor uniform black. The Prism-launcher attempt is not the user's configuration (§6b). Shifted-menu fix (jar `8f3b34b68a1b2b80ad74`): check `user-real-real-resize2` in flight. | acceptance (task-22); menu fix = task-15; extent fix = Lead |
| 23 | mod cohort on the current jar (JEI; AppleSkin+MapWriter) | **V** (mods load and their UI draws) / verdict UNCALIBRATED | celeritas | **Reference taken**: `mod3b-jei-ref` (Celeritas `d12425eaaf184e79` + JEI `e592b0019aa0cd32`, fixture `f5b041ae841b2585`, same scene/pose, tick 201, `jeiScreenOpenedAtTick=36`, `UNCALIBRATED_PRESENTED_IMAGE` - correct, no calibrated jei+keep reference), `distinct=10751`, `nonBg=0.886132`. GL-less `mod3-jei` vs it: sky band 10189 px (6.6 %, mean 7.06), horizon 93408 (40.5 %), terrain centre 76341 (35.1 %), chat block 104347 (52.7 %), **hud strip 0 px**, **hotbar strip 0 px**, tooltip 240 px (3.75 %), whole frame 316029 (34.3 %, mean 19.19) - the same shape as the no-JEI inventory pair, so the residual is the GUI stage, not a JEI-specific defect; JEI's own grid, tooltip and recipe page draw correctly in the crops. GL-less, jar `609eb69a9064df05`, fixture `f5b041ae841b2585`, AMD RX 6400, scene=terrain, pose `0.5/12/0.5 yaw 180 pitch 20`. `mod3-jei` (JEI `e592b0019aa0cd32`, `jeiScreen`+`keepInventoryScreen`, capture tick 201): verdict `UNCALIBRATED_PRESENTED_IMAGE` (correct - the `screen=jei+keep` variant has no calibrated reference), `validationClean=true`, `validationMessageCount=0`, **VUID 0**, `distinct=5210`, `nonBg=0.92124`, `bg=0x8B8B8B`; per-format histogram `id1=564/564 id2=32393/32392 **id4=5916/5916** id5=55/55 id6=1579/1579 id7=463/463 id9=30/30` (format 4 = ITEM, i.e. the JEI grid). Crops `.celtium-work/acceptance/crops/mod3-jei/`: panel `(420,120,860,600)` 2670 distinct, mode `(139,139,139)` 16.5 %; tooltip `(560,280,880,400)` 654 distinct, white text 9.5 %; I looked at the panel crop - the JEI item grid, the "...ding Blocks" category label, the "**Gold Ore**" tooltip, the player slots and the hotbar are all drawn. `mod3-hud` (AppleSkin `4bbd37edecff0b42` + MapWriter `e9a0551013ab785e`, `keepGui=true`, tick 201): **`PASSED`**, `distinct=17080`, `nonBg=0.952757`, `bg=0xFFFFFF`, `validationClean=true`, `validationMessageCount=0`, **VUID 0**; histogram `id1=2/2 id2=23940/23939 id5=51/51 id6=528/528 id7=467/467 id9=12/12`. The game log lists `appleskin@1.0.14` and `mapwriter@2.8.10` and `Forge Mod Loader has successfully loaded 17 mods`; I looked at the presented frame - MapWriter's circular minimap with its coordinate readout and the vanilla toast are drawn, while **AppleSkin is loaded but its overlay is not visible in this frame** (no hunger bar is drawn in this scene), so AppleSkin's UI is unverified. Crops `.celtium-work/acceptance/crops/mod3-hud/`. Honest framing: the past cohort (`mod3-glless-jei` 01:10:56, `mod5-bridge-jei`, `mod6-bridge-hudmods`) is past evidence and **all three were FAILED_PRESENTED_IMAGE**; the current cohort's Celeritas reference with the same mods is queued (`mod3b-jei-ref`). `mod3b-jei-recipe` (same jar/fixture/JEI, `jeiRecipe=true`, capture tick 203): `jeiScreenOpenedAtTick=31`, **`jeiRecipeOpened=True`, `jeiRecipeOpenedAtTick=41`**, verdict `UNCALIBRATED_PRESENTED_IMAGE` (correct), `validationClean=true`, **VUID 0**, distinct=5017, nonBg=0.810433, bg=0xC6C6C6; histogram `id1=30/30 id2=11713/11712 **id4=1391/1391** id5=21/21 id6=623/623 id7=192/192 id9=30/30`. Crop `.celtium-work/acceptance/crops/mod3b-jei-recipe/recipe-panel.png`, which I looked at: the JEI **Crafting** recipe page with its `1/259` pagination, the crafting grid with items, the arrow, the output slot and the page buttons are all drawn. | gui-stage (task-5), pipelines (task-6), acceptance |
| 24 | normal play (the user's own route): stalls, time split, diagnostics | **V (playable; the lag is dominated by the validation instruments)** | celeritas (the renderer) + the fixture as driver | Route leg `play48` (jar `5f94fc293154bd72`, fixture `8a08daddea1955a1`, GL-less, AMD RX 6400, scene=videoSettings + worldSwitch): world, **quit to title at tick 100**, **second world at tick 100**, pause menu at 204, video settings at the capture, verdict **PASSED**, 0 failures; cold window (120-200) 10 samples P50 438.3 / P95 722.3 ms, warm window empty (the GUI stage stops the sampler before tick 400). Steady-state leg `r11-warm` (jar `7bb1746041575ef2`, fixture `d860e46679320d05`, scene=terrain, `captureCondition=sections` target 41 / min 295 frames, capture at tick 1073, **PASSED**): cold 10 samples P50 444.2 / P95 965.3 ms; **warm (400-600) 37 samples, P50 243.6 ms, P95 431.5 ms, P99 467.9 ms**. The same condition without the validation layer and without the presented-image readback (`lead-warm3`, jar not quoted by that leg): **warm 275 samples, P50 24.0 ms, P95 105.6 ms** - a 219 ms P50 gap that is instrument overhead, and the attribution leg (`r11-warm-noval`) is queued. Time split from `play48`'s own `celtium/vulkan-check`: `batchCallMillis=1705` (worst 301, CPU 494), `actualFlushMillis=294` (worst 77, CPU 81), `threadCpuTotalMillis=23566`, `passes=1129 skipped=0`, `immediateOffered=1879`, `refusedBeforeAppend=0`; `threadWaitedMillis=-1`/`threadBlockedMillis=-1` (**unmeasurable in this JVM**) and `glless target: no handoff` (no GL-to-Vulkan handoff on this path). The user's own shape (`plain48`, no fixture/validation/readback, 120 s): **1389 log lines, 916 CELTIUM lines** against >100,000 before the gate - but `CELTIUM_ARRIVALS` (every 500) and `CELTIUM_REFUSALS_BY_FORMAT` + `celtium offer decisions` (every 1000) are **still ungated** in `WorldVertexBufferUploaderMixin`, 467+233+233 lines in that session. **Not exercised**: window drag, minimise/restore, mouse look, the F3 overlay (nothing drives F3), the inventory screen. §6f. | acceptance (task-48); timing fix = frame-composite |
| 25 | the GUI-scale crash (round-11 user report) | **U** (not reproduced; the one crash report is a stale-jar artefact) | none | The user: changing the GUI scale crashes. The only crash report since the last round is `crash-2026-10-11_19.20.30-client.txt` (19:20, "Rendering entity in world"): `NoClassDefFoundError: org/soutalab/celtium/vulkan/gl/CeltiumGlStub$ReadableRange` from `drawArrays`, whose cause chain is `ClassNotFoundException` -> Forge `EventSubscriptionTransformer` -> `ArrayIndexOutOfBoundsException: Index 6 out of bounds for length 0` in `ClassReader.readShort` - i.e. **a zero-length class file**, which is what a jar read while it is being written gives. The installed jar (20:05, md5 `ff85a8f5857eff44`) contains that class at **574 bytes** and `CeltiumGlStub.class` at 87597, and the user's own 20:05-20:33 session (28 min, `CELTIUM_ARRIVALS total=244000`, `CELTIUM_FONT_GLYPH_SUMMARY windows=1012751`) has **no `NoClassDefFoundError`**. The one leg that exercised the change itself (`guiscale-leg`, jar `79c2d64552f0`, fixture `43e2d41be87e`) drove `GUI_SCALE` 'Auto' -> '1' through the screen's own button, saved and restored it, and finished `exitCode=0` with no crash report. **What this does not establish**: that the crash cannot recur - the zero-length read depends on a jar being read while written, and the reproduction is task-58 (sky-draws). "Not reproduced" is not "fixed". | task-58 (sky-draws); task-47/48 unaffected |
| 26 | F3 lag (round-11/12 user report) | **V** (attributed by a one-flag control) | self (one flag apart) | The user's own 20:05-20:33 session: `CELTIUM_FONT_GLYPH_SUMMARY windows=1012751 accepted=873822 refused=138929 submitMillis=47246 reasons={backend=138929}`. **The controlled pair now exists** (`-Dceltium.validation.showDebugInfo=true`, `showDebugInfoSource`="set through GameSettings.showDebugInfo (the F3 key cannot be synthesised honestly)"), jar `fe0a88fd32ba457c`, fixture `e3e5afc07d656684`, GL-less, scene=terrain, one flag apart: **F3 off** `showDebugInfoAtCapture=False`, 13 samples, P50 **287.1 ms**, P95 1550.3 ms, glyph counters `submitted=12000 accepted=0 refused=12000 windows=12000 submitMillis=419 meanSubmitMicros=34 reasons={backend}`; **F3 on** `showDebugInfoAtCapture=True`, 8 samples, P50 **769.7 ms**, P95 1171.6 ms, `submitted=128000 accepted=0 refused=128000 windows=128000 submitMillis=442 meanSubmitMicros=3 reasons={backend}`. So F3 on costs **2.7x the P50 frame interval and 10.7x the glyph windows** (128,000 against 12,000). **Re-taken on the batching-off jar `d805718167095c51` (fixture `68626ca7b426212f`), both legs at `captureStartTick=200`/`worldTicksAtEnd=241`: F3 off 97 samples, P50 **32.8 ms**, P95 129.6 ms, 211 world frames at capture, 72,000 glyph windows; F3 on 41 samples, P50 **127.4 ms**, P95 185.6 ms, **66 world frames at capture** (3.2x fewer), 128,000 glyph windows - so F3 on costs **3.9x the P50** with the same tick and scene, and the cost is no longer assumed. **Caveats, named**: both legs are `FAILED_PRESENTED_IMAGE` with "no picture was requested" (row 30's world-path state, not an F3 result), the samples are few (13/8), and **`accepted=0`/`refused=100 %` in both legs while the user's live session shows legible text** - the legible text does not come from an accepted glyph window in these counters, which is a contradiction to resolve, not to explain away. **Round 16 re-take** (jar `e5508fd5a626c009`, fixture `19ed2a8e1638ac9c`, both legs `PASSED`, `validationMessageCount=0`): F3 off 38 samples, P50 **83.2 ms**, P95 231.8 ms, 82 world frames at capture, **29,214** glyph windows, `submitMillis=400`; F3 on 13 samples, P50 **304.8 ms**, P95 409.1 ms, **30 frames**, **704,273** windows, `submitMillis=319` - **3.7x the P50**, 2.7x fewer frames, and **0.45 us per glyph** (319 ms / 704,273) against the Lead's 0.35 us (304/879,402) and 59 us with batching off. `refused=0` in both, so the earlier contradiction (legible text with every glyph refused) is gone. | task-59 (gui-stage), acceptance (this pair) |
| 27 | items (round-11 user report: fixed) | **V (play-verified) + measured** | celeritas | The user's play confirmation ("items are fixed") is play-verified evidence and is not re-derived here. Beside it, weather-water's pair (`ww27-cand`, jar `fa9487e0cd17ee7c`, against upstream `d12425eaaf184e79`, same fixture/scene/pose/tick) measures the per-slot icon fill at [0.736,0.722,0.722,0.746,0.724,0.682] against the reference's [0.692,0.722,0.722,0.746,0.724,0.696] - an isometric icon covers ~70 % of its slot, a flat texture ~100 %. I re-measured their crops myself: `item-grid` mean|d| **6.80** with **60.5 % byte-identical** (their 60.5 % matches exactly; their mean|d| 5.32 is a per-channel mean, mine a max-channel one), `hotbar` mean|d| 6.05 / 69.0 % identical, and my per-60x60-tile modal-fill proxy is 0.65-0.72 on the candidate grid. Their two caveats stand: the candidate carries the fixture's own debug text lines (an overlay difference), and the two legs sit at different build states, so the item crops are the valid comparison, not the world frame. | task-50 (weather-water) |
| 28 | night: sky, stars, terrain brightness (round-11 claim 3) | **V** (self-comparison at equal build state) | self (one flag apart) | `r11-night` (worldTime=18000) vs `r11-day` (worldTime=6000), jar `7bb1746041575ef2`, fixture `d860e46679320d05`, GL-less, AMD RX 6400, scene=terrain, pose `0.5/12/0.5 yaw 180 pitch 20`, **both captured by the fixture's own equal-build-state condition** (`captureCondition=sections`, target 41, `captureMinWorldFrames=295`; both reports show `captureCondition=sections-target`, `captureListedSections` 55/59, 295 world frames at capture), both `PASSED` with 0 assertion failures. Per-region means (night vs day): sky band `(6.2,7.0,12.2)` vs `(158.1,192.9,255.0)`; horizon `(41.5,25.7,27.7)` vs `(141.5,136.6,158.9)`; terrain centre `(75.8,43.5,38.9)` vs `(103.1,66.4,60.7)`; hud strip `(74.6,18.2,16.8)` vs `(101.6,27.8,26.2)`; whole frame `(55.3,30.4,29.5)` vs `(121.5,101.2,114.2)`; background colour `0x400F0E` vs `0x571716`; blackFraction 0.01135 vs 0.0. **Stars are drawn**: the night sky band holds point-like pixels at ~`(68,69,72)` against a background median of 18 (max 96 in rows 0-250), and the day frame has none - the first measurement of the star path (row 3). **Not covered here**: the user's entities/clouds/player brightness - this leg is the terrain scene with `entities=none` and no cloud pixel identified at this pose; those are task-35/55/7's legs. No Celeritas night reference exists, so the night *brightness* is measured but not compared with vanilla's. | acceptance (round-11); entity/cloud owners = task-35/55/7 |
| 29 | the loading screen (round-11 claim 4) | **M** (the frame during the load is black with the HUD) | none | `r11-loading` (jar `7bb1746041575ef2`, fixture `d860e46679320d05`, GL-less, scene=terrain, `dumpPresentedTick=1`): the earliest capturable presented frame - requested at tick 1 after 0 presented frames, world frame 1 - is **near-black with the HUD**: mean `(2.18,2.19,1.71)`, **123 distinct colours**, 720 distinct rows, not uniform, and the picture shows the black world, the crosshair, the hotbar and a small bar under the crosshair. The world frame at tick 202 is `(122.7,103.4,117.3)` with 2056 colours. So at the first world tick the loading screen's content is **not** in the presented frame; the user's "the loading screen is still not displayed" is corroborated for that stage. What this does not cover: the pre-world phase (the Forge splash and "Joining world") is not capturable with the fixture's current hooks - the splash is skipped by design under GL-less (`CELTIUM_GLLESS splash progress skipped (no shared GL context)`), and a `dumpPresentedMenuTick=5` dump produced no file because the fixture is already loading the world. The loading stage's own reference comparison is task-61 (texture-mirror). **On the post-fix jar `6aab53d4b0f8445c`** (`r11c-loading`, compositor capture from process start): 17 captures during the loading phase are **uniform white** (`(255,255,255)`, 1 distinct colour, window `Cleanroom` 1280x720 at 0,0, shutters 21:51:00-21:51:16) - a white field with no progress bar, no label, no dirt background - and the rest are black; `CELTIUM_REPLACE_DROP` is **absent** (0 lines, the (b) fix's half that needs no capture) and the run ends with the device-lost family (row 30). So the loading screen's *content* is still not presented: the window is a white field where the loading screen should be. **Round 16, after the published name is re-pointed at the pair's live image**: `r16-loading` (jar `e5508fd5a626c009`, fixture `19ed2a8e1638ac9c`, `PASSED`, `validationMessageCount=0`, 33 captures) - the uniform white field is **gone**. The phase now goes black (captures 0-19) -> 14 distinct colours (20) -> **4,411** (21) -> 8,757 (22) -> **15,000+** (24 onward, the built world). What capture 21 shows is the **world frame while the terrain is still unbuilt**: sky, clouds, crosshair and hotbar over a dark brown field - i.e. the window has content throughout the load, but the vanilla loading screen's *own* content (dirt background, "Loading terrain..." label, progress bar) is still not presented. Kept: `loading-first-content.png` (21, sha256 `a75066f5e6060afa…`) and `loading-content.png` (26, sha256 `a44dce7b4e40b6d8…`). | task-61 (texture-mirror) |
| 30 | **GL-less world path: crash, device-lost, then clear** (rounds 11-16) | **V** on the current jar (`e5508fd5a626c009`, fixture `19ed2a8e1638ac9c`), with the history kept | none | **Round 16, independently re-taken**: `r16-world` (layer on, GL-less, scene=terrain, `--validate`) is **`PASSED`** with **`validationMessageCount=0`**, `validationClean=true`, **0 assertion failures**, `worldTicksAtEnd=207`, `presentedDistinctColours=2058`, non-background fraction **0.759463**, **628 bands**, black 0.0 - and the **stale-group rebuild path is exercised**: `staleGroupDescriptorsRebuilt=17`, `staleGroupRefusals=0`, `publishedFrameTexturesStalePruned=18`, `Republished=0`, `passes=632`, `immediateOffered=1673`, `stalledTeardownSkips=0`, `idleWaits=0` (from the run's own `rendererDebug` summary). The earlier clear (`r12d-world`, jar `5235c6b00c05758c`: `PASSED`, 0 messages, 0 zero-view refusals, `presentedDistinctColours=2061`, nonBg 0.759463, 625 bands) stands beside it. The history below is kept because each step is what made it findable:  **Post-fix re-take, jar `6aab53d4b0f8445c` (fixture `8d933a02105ad539`, `publishedFrameTexturesRepublished` present), AMD RX 6400, GL-less, scene=terrain, layer on: no SIGSEGV in any leg, but `validationMessageCount=3` and `FAILED_PRESENTED_IMAGE` with "no picture was requested for scene=terrain stage=world"** - `r11c-terrain-pic` `exitCode=143` (hit the 150 s duration), `r11c-terrain` `exitCode=0` 91.5 s, `r11c-night` `exitCode=0` 111.2 s. The three messages are identical in all three: `VUID-vkCmdDrawIndexed-None-08114` (combined image sampler descriptor), `VUID-vkQueueSubmit-pSignalSemaphores-00067` (semaphore signalled while in use) and `WARNING ../src/amd/vulkan/radv_queue.c:1934 vkQueueSubmit() failed (VK_ERROR_DEVICE_LOST)`. So the crash class is fixed and the path is not healthy; the (a) acceptance (`validationMessageCount=0`) is **not** met on this jar either. **The zero-view guard did not fire on a layer-enabled world leg**: `r12b-world` (jar `d805718167095c51`, fixture `68626ca7b426212f`, layer on, world leg) gives `validationMessageCount=3` with the **same three messages** (`VUID-vkCmdDrawIndexed-None-08114`, `VUID-vkQueueSubmit-pSignalSemaphores-00067`, `radv_queue.c:1934 vkQueueSubmit() failed (VK_ERROR_DEVICE_LOST)`) and **0 `CELTIUM_ZERO_VIEW_REFUSAL` lines** - so the descriptor that the 08114 names is still reaching the draw through a path the `descriptorSetFor` guard does not cover, or its condition is not met; the chain is not gone. **The split is verified from my side**: `r11c-plain` (the user's shape on this jar - no fixture, no validation, no layer, 150 s) ran to the harness's duration with **0 `DEVICE_LOST` and 0 `Invalid VkImage` lines**, 103 non-black compositor captures, the last at **1255 distinct colours** (the menu renders); the world legs on the same jar show the device-lost family. So the menu path is healthy and the world/immediate path is not. **Earlier, on the pre-fix jar `7f650762946ef658`: 3 of 3 layer-enabled legs aborted** - Jar `7f650762946ef658` (HEAD `172f1c859` + the dirty files of 21:19), fixture `6f95950c237b6550`, AMD RX 6400, GL-less, scene=terrain: **3 of 3 legs aborted** - `r11b-terrain` (validation + presentReadback + dump) `exitCode=-6`, `MISSING_REPORT`, `validationMessageCount=1`, 39.9 s; `r11b-loading` (same + compositor capture) `-6`, `MISSING_REPORT`, 40.5 s; **`r11b-terrain2` (the plain shape: validation on, no readback, no dump) `-6`, `MISSING_REPORT`, `validationMessageCount=1`, `validationClean=false`, 33.0 s**. All three print `Celtium Vulkan validation: severity=ERROR id=VUID-VkImageMemoryBarrier-image-parameter vkCmdPipelineBarrier(): pImageMemoryBarriers[0].image Invalid VkImage Object 0x300000000030` and then die in native code: `hs_err_pid*.log` = `SIGSEGV (0xb) ... C [libvulkan_radeon.so+0x1262c1]`. The last renderer line is `CELTIUM_IMMEDIATE_ENQ frame=0 group=2 ... fmt=2 texture=6 batches=1 target=0 mirrored=true worldPass=false`. **This contradicts the round-11 acceptance as written** ("`validationMessageCount=0` on a plain terrain leg on the current jar"): it is 1 and the process dies. The invalid handle looks like a published frame texture, the family the round's (a) fix touches; `r11b-terrain3` (same leg without `vulkan.validation`) is running to say whether the invalid barrier is the renderer's or the layer's. **Every row measured on this jar is affected** - a leg that dies at 33-40 s cannot carry a verdict. **The layer is the trigger, measured**: `r11b-terrain3`, the same leg with `vulkan.validation` **off**, ran to completion (`exitCode=0`, 87.2 s, report written, **no "Invalid VkImage" line** because no layer was there to name it), with `validationClean=true` and `validationMessageCount=0` - which is vacuous, since without the layer no messages can exist; its verdict is `FAILED_PRESENTED_IMAGE` only because the plain shape requests no picture (under GL-less the game's own screenshot is black by construction), which is an instrument-shape artefact. So the invalid barrier is either created by the layer's presence or only fatal under it, and the acceptance shape that matters - a layer-enabled leg - is the one that dies. **It is a regression, and the old jar is the control**: `r11b-terrain4old`, the same plain leg with the layer on the older jar `c6e7a8058c3fd511` (19:5x), **ran to completion** (`exitCode=0`, 94.9 s) with **`validationMessageCount=20`, `validationClean=false`**, and its messages are exactly the two the round's (a) fix targets: `VUID-vkCmdBeginRenderPass-initialLayout-00900` x10 and `VUID-vkCmdDraw-None-09600` x10 - and **no invalid-image barrier**. So between `c6e7a805` and `7f650762` the layer-enabled terrain leg went from "twenty validation errors but alive" to "one invalid-image barrier and a SIGSEGV, 3/3". The regression window holds the (a) fix and several other commits, so attributing it needs a bisect - the owner's call, not mine. What is measured: the fix's stated acceptance (`validationMessageCount=0`) is **not** met on the current jar (it is 1 and the process dies), and the old jar's 00900/09600 messages are gone in the new one. **The window narrows to the fix itself**: the layer-enabled legs on jar `7bb1746041575ef2` (20:59, the round-11 day/night/loading/warm legs) all ran to completion, and a `strings` check of the three jars places `publishedFrameTexturesSkippedForAttach` **only in the jar that crashes** (`7f650762946ef658`: 2 strings; `7bb1746041575ef2`: 0; `c6e7a8058c3fd511`: 0). So the only jar whose published-name skip logic is new is the only one that barriers an invalid image; the two jars differ by other commits as well, but the fix changes exactly the barrier path the VUID names. | fix owner = the (a) VUID fix (Lead); acceptance = this row |
| 31 | **round-18 user report: "in the world the entities and the GUI are missing"** | **W** (entities and the first-person item are accepted and never drawn; the HUD is present) | celeritas vs upstream | Named answer, measured on jar `6a89906b2982bb30` (fixture `84189e52cb1cc2bb`, AMD RX 6400, scene=terrain, pose `0.5/12/0.5 yaw 180 pitch 20`, `--validate`) against upstream Celeritas `d12425eaaf184e79` at the same scene/pose (capture ticks 201/200). **The fixture hides the GUI unless `keepGui=true` and removes non-player entities at capture unless `captureEntities=true`** (fixture lines 4106 and 4287), so the leg the report asked for (`keepEntities` only) had **no HUD and no entities in either leg** - `nonPlayerEntitiesAtCapture=0`, and the HUD crop matched the reference at mean|d| 1.81 because both were terrain. With `keepGui=true` + `captureEntities=true` (`r18h-cand`/`r18h-ref`, `nonPlayerEntitiesAtCapture` 7/5): **the HUD is present and matches** - hotbar strip (367,654)-(913,720) mean|d| **1.81**, only 0.2 % of pixels differ; the toast and the chat block draw. **The entities are not drawn**: the mobs' model lists were captured (`ModelCreeper` x6, `ModelBiped`, `ModelSpider`, `RenderPlayer`), `modelMeshBuilt=38`, `modelMeshQuads=228`, `modelDraws=2469`, `modelDrawRefusals=0` - and the batch accounting shows the loss: `acceptedByFormat` has **id4 (item) = 46** and **id9 = 228** while `drawnByFormat` has **id4 = 0 and id9 = 0** (totals 3000 accepted against 2712 recorded/submitted/completed). `OLDMODEL_POSITION_TEX_NORMAL` - what `TexturedQuad` uses, i.e. every mob model - maps to **id 9**, not id8 (`VulkanImmediateFormats`), so id8 = 0 is not the entity path. The entity band (450,150)-(850,400) differs from the reference by 62 % of pixels (the reference holds the creeper/zombie/spider/enderman, the candidate terrain), and the first-person hand (960,500)-(1280,720) by 92 % (reference mean `(128.4,100.8,89.3)` - the skin-coloured arm - against the candidate's `(77.5,47.3,43.4)`). The HUD format id2 is drawn (1698 of 1702 accepted), which is why the hotbar survives. Crops: `.celtium-work/acceptance/r18-evidence/` (`cand-world.png` sha256 `4d4275760a92df41…`, `ref-world.png` `695311525e2a674f…`). | owner of the item/entity draw path; acceptance = this row |

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
- **A frozen fixture predating `f95c6ed08` silently ignores `-Dceltium.validation.worldTime`** (and `-Dceltium.validation.renderClouds`, which landed later, reported as `renderCloudsRequested`/`renderCloudsAtCapture`): a night leg on such a fixture reports `pinnedWorldTime=6000` and a noon picture while passing. **Check `pinnedWorldTime` in the report before reading any night row**; a night claim whose report says 6000 is a noon claim. My `r11-night` used fixture `d860e46679320d05` and its report says `pinnedWorldTime=18000` / `worldTimeAtCapture=18000`, so it is a genuine night leg - checked for exactly this reason.
- **`--validate` is required for the fixture to start a world.** A fixture leg run without it never leaves the menu: no world ticks, no capture, no report - and because the log looks healthy, the run can read as "the fixture did nothing" rather than "the leg was never asked to start a world". Every fixture leg in this table carries `--validate`; a fixture leg that does not is not a leg.
- **`keepGui=true` and `captureEntities=true` are required for a HUD/entity row to mean anything.** The fixture sets `mc.gameSettings.hideGUI = !keepGui` (ValidationClient line 4106) and calls `removeNonPlayerEntities(mc)` at capture unless `captureEntities` is set (line 4287). A terrain leg without them has **no HUD and no entities in either leg**, so a "the HUD matches the reference" reading on such a leg is terrain matching terrain - measured: the requested round-18 flag set gave `nonPlayerEntitiesAtCapture=0` and a HUD crop matching at mean|d| 1.81 because both frames were terrain.
- **The resize path (task-28) needs a re-check.** The flag the loading-screen fix gated (`celtium$noteBindSize(width, height, mainFramebuffer)`) is the one the mid-run resize fix introduced: a resize replaces the game framebuffer, and that is the case the flag must still fire for. Gating it on the main framebuffer keeps the resize case and drops the loading screen's 468x1018 bind; the resize leg's own acceptance (task-28) is therefore re-run rather than assumed.
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

### 6d. Floor provenance (task-47): every floor, the frame it came from, and the frame it judges

The video-settings gate judged its **GUI** frame with floors derived from the scene's **world** frame and from a
bridge GUI frame whose screen backgrounds did not draw, so the reference failed its own gate
(`presentedVerticalBands=229 < 250`, `presentedBlackFraction=0.068322 > 0.05`, `guiPanelDistinctColours=133 < 4000`
in `.celtium-work/runtime/vs-pic-ref` and `vs-pic-cand`). Only `VIDEO_SETTINGS` has `guiStage=true`, so this is the
only scene where a second stage exists to be mis-sourced; the audit below covers every floor the fixture enforces.

| floor | frame the old code derived it from | frame it judges | outcome |
|---|---|---|---|
| `distinctColours > 16` | smoke test, no reference | both stages | fine, and still reported as a smoke floor |
| `nonBackgroundFraction >= 0.40` | world frame (0.727735) | world **and** GUI | world fine; GUI wrong frame -> re-derived **0.37** from the GUI frame's 0.672704 |
| `verticalBands >= 250` | world frame (638) | world **and** GUI | world fine; GUI wrong frame -> re-derived **89** from the GUI frame's 229 |
| `blackFraction <= 0.05` | world frame (0.000000) | world **and** GUI | world fine; GUI wrong frame -> re-derived **0.118** from the GUI frame's 0.068322 + 0.05 |
| `panelChangedFraction >= 0.30` | bridge GUI frame (0.478) | GUI panel | right stage, wrong picture -> re-derived **0.63** from the reference's 0.999189 |
| `panelStrongFraction >= 0.25` | bridge GUI frame (0.456) | GUI panel | right stage, wrong picture -> re-derived **0.545** from 0.990540 |
| `panelDistinctColours >= 4000` | bridge GUI frame (15805) | GUI panel | **wrong picture**: 15805 came from a frame where the world showed through the panel; re-derived **33** = 25 % of the reference's 133, and it is now a presence guard only |
| `panelRowAmplitude >= 0.25` | bridge GUI frame (9.344) | GUI panel | flatness guard, value kept, source re-named |
| `panelMeanLuma <= 0.70x world` | **new**, from the reference | GUI panel | added because the colour floors cannot separate a drawn screen from one whose backgrounds are missing (see below) |

**The floors are derived from two same-era reference runs, and the candidate corroborates them.** The GUI-frame
numbers are identical in `vs-pic-ref` (upstream Celeritas, fixture `29f1a7d399017f66`, tick 235) and `vs47-cel`
(upstream Celeritas, fixture `396d40cd6043ded4`, tick 235): 145 distinct colours, 229 bands, nonBg 0.672704, black
0.068322, panel 133 colours, rowAmplitude 4.089, panel luma 57.783 against the world frame's 109.919/111.377. The
only spread is in the panel fractions: changed 0.999189/0.999617, strong 0.990540/0.995158. The GL-less candidate
`vs47-cand` measures the same frame to the digit (panel changed 1.0, strong 0.998936, luma 57.783 against 113.658) -
corroboration, not the source.

**One implementation trap, found by the first verification run.** The per-stage floors were first written into
`celtium$videoSettingsGate()`, but `celtium$resolveGate()` - the override path every run goes through - rebuilt the
gate with the 13-argument constructor, which drops `guiFloors`. The first verification run therefore still reported
`guiFrameGateFloors nonBackgroundFraction>=0.4 verticalBands>=250 blackFraction<=0.05` and failed the candidate. The
fix carries `measured.guiFloors` through; the run that showed it is `vs47b-cand` (fixture `5219e885d32c45f0`). A
floor set that is built correctly and then discarded by the next layer is the same class of defect as a floor taken
from the wrong frame, and it is why the verification leg is not optional.

**Why the panel-colours floor had to be re-derived, and what replaces its discriminating power.** The stored
reference runs' GUI frame is not the picture the completion condition is about. In the bridge leg `vs47-ref`
(jar `f27f07f27253f79a`, tick 248) the video-settings screen draws **no backgrounds at all**: the option labels and
the title are drawn over the live world, the world shows through the panel, and the panel measures **12213 distinct
colours against the world frame's 2025** at the same rectangle (scene-floors/video-a measured 15805 against 1509).
The upstream Celeritas reference and the GL-less candidate draw the screen properly - dirt background, grey buttons -
and their panel is **flatter** than the world there (133 colours against 2020-2480), with 99.9 % of its pixels
changed and its luma at 0.53x the world's. A floor that demands *more* colours than the world therefore demands the
defect. The floors that do separate the cases are `panelChangedFraction` and `panelStrongFraction` (0.999/0.991 drawn,
0.000 if the screen never drew) and the new luma ceiling: 0.526 on the reference, **0.774 on the backgrounds-missing
bridge frame**, 1.0 if the screen never drew.

**Not lowered to pass a leg.** Every value above is the same margin rule applied to the frame the stage actually
judges: 55 % of the measured non-background fraction, 39 % of the measured bands, measured + 0.05 for black, 63 % and
55 % for the panel fractions, 25 % for the panel colour count. The old numbers were not stricter - they described
other frames. **Verified by the pair `vs47d-cand` / `vs47d-ref`** (jar `5f94fc293154bd72`, fixture
`01032550495d088c`): the GL-less candidate **PASSED** with `guiFrameGateFloors nonBackgroundFraction>=0.37
verticalBands>=89 blackFraction<=0.118` and `guiFrameGateSource` naming the upstream Celeritas GUI frame; the
bridge control **FAILED on exactly one floor**, the new luma ceiling (panel 87.496 above 0.7x the world's 109.524,
ratio 0.799) - the backgrounds-missing frame caught rather than a correct picture rejected. Pictures:
`.celtium-work/acceptance/crops/vs47d/` (candidate GUI frame sha256 `bc5e8b060faf534e…`, bridge GUI frame
`ref-celtium-validation.png`).

## 6e. What the disk policy deleted, and which rows are now numbers-only

The workspace keeps **only the most recent completed run** (ledger
`.celtium-work/disk-cleanup-latest-only-20261011.json`, 8344 paths and 386 jar snapshots removed, ~4.45 GiB). Every
run directory's own `screenshots/` and `logs/` were deleted for every run except the newest (`runtime/vs47d-cand`) and
the protected set, so `rep.py` can no longer re-read the old reports and the run-level PNG paths quoted in earlier
revisions of this table are dead links.

What survives, and is what the rows rest on:

- **The crops and the frozen jars.** `.celtium-work/acceptance/crops/**` (584 PNGs, including `acc2/`, `acc3/`,
  `cand3-*/`, `mod3-*/`, `mod3b-jei-recipe/`, `vs47d/`, `terrain-recheck/`, and the stacked `hotbar-…`, `crosshair-…`,
  `chat-…` pairs), `.celtium-work/acceptance/user-cfg-menu-caps/` (180 captures) and
  `.celtium-work/acceptance/frozen/` (16 jars) are outside `runtime/` and were not touched.
- **The numbers and hashes written in this document.** Each row quotes its counters, verdict and jar/fixture hashes, so
  a deleted report does not make a row unverified - it makes it *numbers-only*: the measurement cannot be re-derived
  from the workspace without re-running it.
- **The newest run**, `vs47d-cand` (and `vs47d-ref`, which ran after the sweep), whose reports and screenshots are
  still in `runtime/`.

Rows whose evidence is now numbers-only (the crop survives, the run's own report and screenshot do not): 1, 2, 4, 5,
6, 7, 8, 9, 10, 11, 12, 13, 14, 17, 18, 19, 22, 23. Row 20's pair is the only one whose reports still exist. None of
them is to be read as newly verified: the status letters stand on the measurements recorded here, and re-taking any of
them needs a fresh run.

## 6f. The normal-play row: what was measured, and what the instruments cost

**The route, driven.** A human-free route needs a driver, so `play48` ran the user's renderer configuration (the
instance's mods, the current jar, `-Dceltium.vulkan.glless=true`) with the fixture as the driver and the timer. The
fixture adds no rendering path; it presses the keys and writes the report. What the run did, from its own report:
world load, `leftWorldAtTick=100` (the three calls "Save and Quit to Title" makes), `rejoinedAtTick=100` (a second
world), the pause menu opened at tick 204 (`GuiOptions`), the video settings screen at the capture
(`GuiVideoSettings`), `worldTicksAtEnd=296`, verdict `PASSED` with 0 assertion failures.

**The lag the user feels is dominated by the validation instruments, and that is measured.** Two legs ran the same
steady-state condition (`captureCondition=sections`, target 41, minimum 295 world frames, warm window 400-600, scene
=terrain, GL-less): `r11-warm` (jar `7bb1746041575ef2`, with the Khronos validation layer, `presentReadback` and
`dumpPresentedImage`) measured warm P50 **243.6 ms**, P95 431.5 ms, P99 467.9 ms over 37 samples; `lead-warm3` (same
condition, **without** the validation layer and **without** `presentReadback`) measured warm P50 **24.0 ms**, P95
105.6 ms over 275 samples. A 219 ms gap at P50 is a 10x frame time, and it is the instruments, not the renderer -
the user's own launch has none of them. Both differences (the layer and the readback) are present at once, so the
gap is the *sum*: `r11-warm-noval` is queued to attribute it with the layer off and the readback off. The route
leg's own numbers are in row 24.

**The interval numbers, and their window.** The fixture reports two windows and names them:
`frameIntervalWindows cold=worldTicks 120..200 (the chunk-build window; the unqualified frameIntervalP50Ms fields are
this one) warm=worldTicks 400..600 (after the world is built)`. `play48` reached tick 296, so its cold window holds
**10 samples: P50 438.3 ms, P95 722.3 ms, P99 722.3 ms**, and its warm window is **empty**. That is not a playability
number - it is the chunk-build window of a fixture that loads a world and switches worlds inside four minutes - and
the row says so rather than quoting it as "the frame rate". `play48b` repeats the route with the steady-state window
declared at the ticks the run actually reaches (`warmFromTick=250 warmToTick=420`) and with `handoffTimings` on; its
numbers go in the row when it lands.

**The time split, from the run's own counters.** `celtium/vulkan-check` at the end of `play48`:
`batchCallMillis=1705` (worst 301, CPU 494), `actualFlushMillis=294` (worst 77, CPU 81),
`threadCpuTotalMillis=23566`, `passes=1129 skipped=0`, `immediateState=18833 immediateOffered=1879`,
`refusedBeforeAppend=0`. Two of the counters the task names are **not measurable here**:
`threadWaitedMillis=-1` and `threadBlockedMillis=-1` (the JVM reports thread CPU unsupported), and the
`handoff` counter is absent because **GL-less has no GL-to-Vulkan handoff** - the run's own line says
`glless target: no handoff, abandoned=0`. Synchronous readbacks in this leg are the instruments, not the product:
`presentReadback` and `dumpPresentedImage` were on, and the compositor capture photographed the output throughout;
the user's shape (`plain48`) had none of them.

**The diagnostics, and what still repeats.** The gate (`VulkanDiagnostics.enabled()`, on for a validation run or
`-Dceltium.vulkan.diagnostics=true`) covers `CELTIUM_MODEL_MATRIX` (bounded), `CELTIUM_ACCEPTED` (every 500),
the arena census, the colour-batch census, the model build/off-target lines and the stub's texture-parameter
shapes. Measured in `plain48` (no fixture, no validation, 120 s): **1389 log lines, 916 of them CELTIUM lines** -
against the >100,000 lines the operator's session produced before the gate. Two repeating censuses are **still
ungated**, on the current jar too: the first measurement was `plain48` (jar `5f94fc293154bd72`) with
**1389 log lines / 916 CELTIUM lines** and `CELTIUM_ARRIVALS` x467 plus `CELTIUM_REFUSALS_BY_FORMAT` x233 with
`celtium offer decisions` x233; re-measured on the round-11 jar `7f650762946ef658` (`r11-plain`, no fixture, no
validation, no readback, 120 s): **886 log lines, 529 CELTIUM lines**, of which `CELTIUM_ARRIVALS` **234**,
`CELTIUM_REFUSALS_BY_FORMAT` **117**; and again on the post-fix jar `6aab53d4b0f8445c` (`r11c-plain`, same shape, 150 s): **700 lines, 381 CELTIUM lines**, `CELTIUM_ARRIVALS` **159**, `CELTIUM_REFUSALS_BY_FORMAT` **79**; and on the current jar `5235c6b00c05758c` (`r12d-menu`, same shape, 120 s): **2152 lines, 1479 CELTIUM lines**, `CELTIUM_ARRIVALS` **866**, `CELTIUM_REFUSALS_BY_FORMAT` **433**, `CELTIUM_FONT_GLYPH` 44, `CELTIUM_SHARED_LAYOUT` 23, `CELTIUM_MIRROR_SOURCE` 16,
`CELTIUM_INVALIDATE_ORIGIN` 12, `CELTIUM_GLESS_INVALIDATE` 9, `CELTIUM_PATCH_REJECTED` 8. **Round 16: both censuses are gated now** (`VulkanDiagnostics.enabled()`, commit `efd356d23`), and the measurement confirms it: `r16-menu` (user's shape, jar `e5508fd5a626c009`, 120 s) prints **0** `CELTIUM_ARRIVALS` / `CELTIUM_REFUSALS_BY_FORMAT` / `celtium offer decisions` lines and **425 log lines total, 185 CELTIUM** - down from 2152/1479 on the previous jar. A validation run still prints them (the `r16-world` leg has one `CELTIUM_ARRIVALS total=500 byFormat=[...]` line), which is the intended split.

**Display honesty.** `CELTIUM_NATIVE_DEVICE` names the real device in both legs, and the run's `glVendor=Celtium`
confirms the stub path. The **F3 overlay itself was not captured**: nothing in these legs presses F3, and
`CeleritasVintage.onF3Text` (line 55) returns immediately unless `gameSettings.showDebugInfo` is set. The F3 line's
source is `Celtium Renderer (VERSION)` plus `Terrain backend: ` + `TerrainBackendStatus.describe()`, which is the
live renderer's description or, before a world exists, `TerrainBackendSelection.pending(...)` - so a menu-time F3
would report the pending selection, not the live device. That is a claim about the code, not a measurement of the
overlay, and the row says so.

**What could not be exercised.** A literal window drag, minimise/restore, mouse look, the F3 overlay (no input in
these legs), and the inventory screen: the route used the pause menu to video settings, and the fixture's inventory
path needs its JEI flag. The loading screen is skipped under GL-less by design
(`CELTIUM_GLLESS splash progress skipped (no shared GL context)`).

## 6g. The font-batching caveat on every text row (round-11)

The user reports the menu text garbled, and the Lead measured it as a one-flag control:
`-Dceltium.font.batching=false` renders every label, the rotated splash text and the copyright line legibly, while
with batching on they render as blocks - the run's state witness was missing the matrix pair, so scaled and rotated
draws were merged into a run opened under a different transform. **The batching is off by default in the current tree** (the Lead's ruling after the one-flag control; `-Dceltium.font.batching=true` enables it), so the text rows are re-takeable on a current jar. **Our own capture on the jar that still had it on is the control for the user's report**: `r12-menu` (jar `fe0a88fd32ba457c`, the user's shape, compositor capture, 22:25, best frame 1448 distinct colours) shows the menu **with the button labels as white/grey blocks** - `Singleplayer`, `Multiplayer` and `Options` are unreadable, which is exactly the user's report, reproduced in our own capture rather than only in theirs. Crops: `.celtium-work/acceptance/r12-menu-evidence/menu-best.png` (sha256 `9348557ba03e1dfb…`), `menu-buttons.png`, `menu-title.png` (the logo and the splash text are drawn). `r12b-menu` on the batching-off jar is queued for the re-take. **The user's own live session is the strongest text evidence we have and it is theirs, not ours**: their desktop capture `~/Pictures/screenshot_20261011_215757.png` (21:57:57, 1913x869, **9701 distinct colours**, sha256 `3a30753d504c9455…`, preserved as `.celtium-work/acceptance/user-live/user-f3-live-215757.png`) shows a rendered world with the F3 overlay and **every line legible** - "Celtium Renderer (2.5.12-celtium.0.1.0-dev.1)", "Terrain backend: vulkan (Vulkan device matched the OpenGL device UUID and the interop contract was satisfied)", "Display: 1920x1080 (Celtium) apiversion=1.4.354 via Celtium Vulkan", "*AMD Radeon RX 6400 (RADV NAVI24)* uuid=… type=2", the chunk queues and the per-pass timings. Caveats: it is the **user's** screenshot of their own window (a desktop capture, not our compositor capture), the window is 1920x1080 while our legs are 1280x720, and the F3 frame-rate line in it reads **"0 fps"** - which is the user's own measurement of the F3 cost, not ours. In this table the text rows are:

- row 13 (chat) - "the text is drawn in both legs" was read from crops of a jar whose batching state is not recorded;
- row 19 (font / N03) - its `quads/submitted/accepted` counters are from `plan.md` §0.144, before batching;
- row 23 (the mod cohort) - the JEI panel crop I read showed "Gold Ore" and the category label legible on jar
  `609eb69a9064df05`, so *that* jar's text was not garbled; that is one observation, not a batching control.

**The re-take, and a correction to my own first reading.** My first "batching-off" leg was on a jar that still had
it **on**: `r12b-celtium.jar` (`d805718167095c51`, copied 22:30) compiles to
`System.getProperty("celtium.font.batching", "true")` - I checked the bytecode after seeing the garbling, and the
default flip (`45030e8f0`) landed in the jar built at 22:39. On that jar the menu's **button labels are blocks**
(footer and splash legible), and **the explicit control on the same jar is legible**: `r12c-menu-explicit`
(`-Dceltium.font.batching=false`, same jar, same leg shape, frames 95.6 % identical) shows `Singleplayer`
(yellow, hovered), `Multiplayer` and `Mods` cleanly. The user's own 21:25:28 capture shows the garbled state, and
their 21:57:57 capture (in-world, F3) shows legible text - both are their captures, preserved under
`.celtium-work/acceptance/user-live/`. So the batching flag **is** the mechanism for the garbling, the default
flip is what fixes it, and the text rows are re-takeable on a jar that carries it - which is the jar built at
22:39 (`r12d-celtium.jar`, `5235c6b00c05758c`, bytecode default `"false"`); `r12d-menu` is that re-take: on jar `5235c6b00c05758c` (fixture `05a274594e3363fe`, user's shape,
compositor capture 22:52, 108 non-black captures) the menu buttons read **`Singleplayer`** (yellow, hovered),
**`Multiplayer`** and **`Mods`** - the crop is **byte-identical to the explicit-flag control**
(`.celtium-work/acceptance/r12d-menu-evidence/menu-buttons.png`, sha256 `971999530a359e1f…`), so the default now
behaves as the flag. **The text rows are re-taken as legible on this jar.** **Round 16 re-took it with batching ON again** (the fix applies the run's recorded texture/alpha-test/matrices at the flush instead of the intervening draw's): jar `e5508fd5a626c009` (fixture `19ed2a8e1638ac9c`, bytecode default `"true"`), user's shape, compositor capture 23:08, 107 non-black captures - the buttons read **`Singleplayer`** (yellow, hovered), **`Multiplayer`**, **`Mods`** (`r16-menu-evidence/menu-buttons.png`, sha256 `ef0c53c5732d68e4…`). So the garbling was the submission-time capture, the fix holds with the optimisation on, and the text rows stand as legible on this jar. The font counters agree: `accepted=704,273 refused=0` with F3 on (round 16) against `accepted=0 refused=100 %` before the fix - the contradiction noted in row 26 is resolved. Crops of the control: `.celtium-work/acceptance/r12c-menu-evidence/menu-buttons.png`
(legible) against `.celtium-work/acceptance/r12b-menu-evidence/menu-buttons.png` (blocks).

## 6h. The user-facing state at the start of round 12 (recorded, not re-litigated)

- **"f3がラグい"** - the user's live report. Their own F3 screenshot (preserved at
  `.celtium-work/acceptance/user-live/user-f3-live-215757.png`, sha256 `3a30753d504c9455…`) shows the world rendered,
  the F3 text legible, and the frame-rate line reading **"0 fps"**. Row 26 carries it; the controlled pair is queued.
- **The world renders and the text is correct** in their session (same screenshot: grass, trees, buildings, and every
  debug label legible). That is the user's evidence, not our capture.
- **The loading screen is a uniform white field, not content**: 17 compositor captures at
  `(255,255,255)`/1 distinct colour during the loading phase on the post-fix jar (`r11c-loading`), the rest black, with
  `CELTIUM_REPLACE_DROP` absent. Row 29 carries it.

## 6i. Round-22 verification of the 37-finding hunt (verifier's ledger)

Rules applied to every row below: **a missing counter is not a missing draw**, and **a fix that only moves a number
without a picture is not accepted**. Each row names the BH id, the fix's file, the counter or crop before/after,
`validationMessageCount`, and whether a picture changed.

**The baseline is mine and it is pre-fix**: jar `3abc917b15e930cf`, fixture `5fc4975a301479fa`, HEAD `5e3b4a947`,
`keepGui=true`+`captureEntities=true`, scene=terrain, `--validate`, AMD RX 6400 (`r22b-cand` / `r22b-ref`, upstream
`d12425eaaf184e79`). Baseline numbers, to be compared against every later re-take: `validationMessageCount=0`,
`UNCALIBRATED_PRESENTED_IMAGE` (the entity variant is not a calibrated key), `presentedDistinctColours=16285`,
non-background fraction `0.952222`, `707` bands, `nonPlayerEntitiesAtCapture=8`, `worldFramesAtCapture=47`,
capture tick 200; **`acceptedByFormat` `[1:3 2:1444 4:24 5:46 6:306 7:190 8:0 9:228]` against `drawnByFormat`
`[1:3 2:1440 4:0 5:46 6:304 7:188 8:0 9:0]`** - the entity gap reproduces on this tree (id4 24 accepted / 0 drawn,
id9 228 accepted / 0 drawn); `modelMeshBuilt=38`, `modelMeshQuads=228`, `modelDraws=1217`,
`modelDrawRefusals=0`. Frames kept: `.celtium-work/acceptance/r22-baseline-cand.png` (sha256 `a16dcc4fa60d61fe…`),
`r22-baseline-ref.png` (`4418710250979ac5…`).

| BH | fix file | counter / crop before -> after | `validationMessageCount` | picture changed? |
|---|---|---|---|---|
| **entity gap** (id4/id9 accepted, never drawn) - **mechanism identified** | lit-fragment split (`immediate_tex_color_lit.frag`) + BH-02 in tree at `4955563e6`; **no commit yet closes the draw**; the drop is the window-flush failure clear (`VulkanTerrainGpuBackend:4744-4756`) | **`r23-cand` (jar `cbf8ad7a5c807c66`): `acceptedByFormat` `[1:2 2:1399 4:28 5:42 6:294 7:257 8:0 9:228]` against `drawnByFormat` `[1:2 2:1395 4:0 5:42 6:292 7:255 8:0 9:0]` - id4 28 accepted / 0 drawn, id9 228 accepted / 0 drawn, unchanged from the baseline** | **0, and 0 VUID lines** (the earlier `08114`/`00067` family is gone - the error was removed, the draws were not restored) | **no**: against upstream `d12425eaaf184e79` at the same scene/flags, entity band (450,150)-(850,400) mean|d| **47.06** / 61.5 % of pixels differ (the reference holds creeper/zombie/spider/enderman), first-person hand (960,500)-(1280,720) mean|d| **62.14** / 93.3 % (reference `(118.2,93.3,82.6)` skin arm against `(69.3,42.8,39.2)` terrain); `modelMeshBuilt=38`, `modelMeshQuads=228`, `modelDraws=1485`, `modelDrawRefusals=0`. **A fix that removed a validation error without changing the picture: not accepted.** **The mechanism, from the JSON report on two jars (both `keepEntities`+`captureEntities`, 9 entities at capture)**: `r24-cand` (jar `17f3d037c0fcff0e`, HEAD `a1ff07fcd`) - `windowFlushFailures=64`, **`windowFlushDroppedBatches=9680`**, `windowFlushFailureFrame=46`, **`lastWindowFlushFailure=IndexOutOfBoundsException: null`**, `immediateGroupsDiscarded=0`, `immediateGroupsDrawn=1408`, `immediateGroupsPending=4`, `immediateOnlyPasses=891`, `passes=853`, `immediateRecordedBatches=2437`; `r23-cand` (jar `cbf8ad7a5c807c66`) - 61 / 9168 / frame 43, same exception. So the groups are **cleared by the window-flush failure path**, not never-flushed (`immediateGroupsPending` stays at 4, not growing) and not teardown-discarded (`immediateGroupsDiscarded=0`); the flush is throwing an `IndexOutOfBoundsException`, and the clear drops the item/model batches that were pending at that moment. That is the bug to fix, and it is in the flush, not in the target stamping. **Root cause confirmed by the fix (`f2d5d3ff4`): the push block was 208 bytes and the light coordinate writes at offset 208** - a `ByteBuffer.put(index)` at the end of the buffer, which is why the exception had a null message. **Closed and verified on jar `65a3e7f4f27307cffa55299d` (`r25-cand`, fixture `468a4f54ab95d92f`, both entity flags, keepGui, presented dump): `windowFlushFailures=0`, `windowFlushDroppedBatches=0`, `drawnByFormat` id9 **228/228** and id4 non-zero, and the entities are visible in the presented frame** - creeper, zombie, spider, enderman, the stone item and the first-person hand are all in `r25-cand`'s frame, matching the reference's cast. `modelDraws=2186`, `immediateGroupsDrawn=3820`. Region means against the upstream reference at the same scene/pose/tick: **mob band, chat-free (450,140)-(850,330) mean|d| 19.57** (non-sky fraction 0.609 against 0.624 - the same amount of geometry stands there), **hand (960,500)-(1280,720) mean|d| 24.06** (down from 62.14 pre-fix), **HUD hotbar 2.13**. Two caveats: the candidate carries the fixture's boot-text chat overlay the reference does not, which is most of the residual in the full entity band (35.36 with the chat, 19.57 without), and the legs sit at different build states (60 against 155 world frames). **One counter anomaly to record rather than smooth**: `drawnByFormat[4]=13163` against `acceptedByFormat[4]=47` - the id4 drawn count cannot be read as batches (it is 280x the accepted count), so only the id9 pair is meaningful as accepted-vs-drawn on this jar. Two instrument gaps for the fix: `passSubmissions` is **not a report field** on either jar (the available pass counters are `passes` and `immediateOnlyPasses`), and the caught exception's **stack is not captured** anywhere - `record()` keeps only the message and the debug list is capped, so the throwing index has to come from a first-failure stack print (production code, the owner's call). |
| **HUD + text** (the user's other complaint) | n/a - verified as present | `r23-cand`: hotbar strip (367,654)-(913,720) mean|d| **1.86**, 0.2 % of pixels differ against the reference; **F3 overlay text legible** in our own capture (`r23-f3`, `showDebugInfoAtCapture=True`, crop `.celtium-work/acceptance/r23-evidence/f3-text-left.png`, sha256 `e0fd227cc6cc3496…`: "Minecraft 1.12.2 (1.12.2/fml,forge,cleanroom)", "2 fps (0 chunk updates)", "XYZ: 0.500 / 12.00000 / 0.500" all readable); the chat lines are legible in the world frame | 0 | HUD and text unchanged and correct - the text garbling is not present on this jar (the round-16 batching fix holds) |
| **gate verdict, entity variant** | fixture variant `keepEntities+captureEntities` | `r23-cand` `UNCALIBRATED_PRESENTED_IMAGE`, reason: "this scene has no measured reference, so no non-background, band, black or panel floor was applied; the smoke test, the picture identity, the frame binding and the camera pose held" | 0 | not a failure and not a pass: the variant has no calibrated key, so no floor was applied. The Lead's own leg on `d806e788` reported `FAILED_PRESENTED_IMAGE` where mine reports UNCALIBRATED - the two legs differ, and neither should be read as a pass |
| **BH-12** selection outline (`85e66f594` and the line-batch rule) | the line-batch accept path (`VulkanTerrainGpuBackend:4160-4178`), the fixture's `lookDown` + water-free stack, and the total added by `94f808dca` | **verified, counter (now with a total) and picture**: `r35-outline` (jar `0184a46539e69ebc`) reports **`lineBatchesNarrowed=215`** with 4 `CELTIUM_LINE_NARROWED requested=2.0 drawn=1.0 mode=3 format=1` event lines, and `r28-outline`'s crop shows the outline's edges in the presented frame (`r28-evidence/outline-tight.png`, sha256 `9d216017115cb7b7…`) | 0 | **yes** - drawn where it was previously refused; the total gap is closed |
| **BH-11** polygon offset / TESR z-fighting (`85e66f594`, mechanism fixed by `ae32bdbe7`) | the stub's `glPolygonOffset` record + `GL_POLYGON_OFFSET_FILL`; the three-way instrument `polygonOffsetCalls`/`polygonOffsetFillEnabled`/`polygonOffsetCalled` (`ef7577cd5`); **the fix captures the offset with the batch at group open** (`VulkanTerrainGpuBackend:7093-7095`) and the draw reads the group's copy (`:11885`) instead of the live flag | **verified end to end**: `r38-break` (jar `9186d89841f3c65e`, HEAD `ae32bdbe7`, `breakBlock=true`, `breakProgressCalls=49`, overlay drawn) gives **`immediateDepthBiasedGroups=48`** and `polygonOffsetCalls=96`; the one-flag control `r38-control` (`breakSubjectOnly=true`, `breakProgressCalls=0`) gives **`immediateDepthBiasedGroups=0`** and `polygonOffsetCalls=0`; `polygonOffsetFillEnabled=false` at report time in both, as expected (vanilla disables it in the same frame) | 0 both legs | **yes, and the picture is the evidence**: the same break leg on the pre-fix jar (`r36-break`, `immediateDepthBiasedGroups=0`) shows the crack as a **faint grey** pattern blended into the stone, while the fixed jar shows it **solid black with red stage-5 highlights standing in front of the face** - block region mean|d| **18.75**, 29.1 % of pixels differ >8 (crop `r38-offset-before-after.png`, sha256 `974ea9022f507d06…`). The earlier no-move readings (`r31` vs `r30`, mean|d| 0.70) were the *pre-fix* pair, where nothing was biased; the counter leg is what separated the two. The mechanism history is kept: the flag used to be read at draw time while vanilla's enable window closes in `postRenderDamagedBlocks` (`RenderGlobal.java:1914-1925`), so groups flushed after it got no bias - the accept-vs-flush class of BH-04/BH-05 |
| **BH-08** cull state per batch | the stub's cull capture, `cullEnabledGroups`/`cullDisabledGroups` at `VulkanTerrainGpuBackend:5365`/`:6820` | my legs: `r32-cand` `cullEnabledGroups=2137`, `cullDisabledGroups=3569`; `r35-break` 2007/1258, `r35-control` 2559/1331 - both columns populated on every leg (proj-channel's 2128/3205 is the same shape) | 0 | no picture claim: the state-mirror proof that the state is captured per batch rather than assumed |
| **BH-13** viewport restore | `groupViewportSets` | my leg: **`groupViewportSets=4240`** (their leg: 4072) | 0 | no picture claim; the counter shows the per-group viewport is set rather than left at the previous value |
| **BH-24** uniform-buffer growth | `VulkanUniformBuffer.growthFailures`/`growths`/`lastGrowthFailure` | my leg: `growths=0`, `growthFailures=0`, `lastGrowthFailure=none`, `uniformPreparationFailures=0`, `uniformgrowths=2` - the counters are observable but **my leg does not exercise a growth at all** | 0 | not independently verified: the injected-growth acceptance is fog-path's leg and I have not re-taken it, so it is recorded as reported, not as verified |
| **current jar crashes on a plain terrain leg** (`513421d0d361d0ad`, HEAD `715553f26`) | `settleGpu` at `VulkanTerrainGpuBackend:12069`, called from `updateRegions:7352` (region eviction) | **3 of 3 candidate legs died**: `r33-break`, `r33-control`, `r33-cand` all `exitCode=255`, `fixtureStatus=NO_CAPTURE`, at 22-26 s, with `java.lang.IllegalStateException: settleGpu(region eviction): vkDeviceWaitIdle returned -4` (`-4` = `VK_ERROR_DEVICE_LOST`) in `crash-reports/crash-2026-10-12_02.01.07-client.txt`, 30 `DEVICE_LOST` mentions in each console; the upstream reference leg on the same scene/flags ran to completion in the same window | 0 (the reports are 42-key `NO_CAPTURE` stubs, so no counters were written) | no picture at all - the run dies before the capture. **This blocks BH-11's counter leg and the canon rows**; the previous jar `9bc475cb5e1755e5` ran the same shape green at 01:50 (`r32-cand`), so the regression window is the commits between the two jars. **the control is in and it is decisive**: `r34-prev` (previous jar `9bc475cb5e1755e5`, same fixture `236f0c000f393666` and flags) came back **`exitCode=0`, `fixtureStatus=PASSED`, `validationMessageCount=0`, 29 s, no crash report** - so the regression is real and its window is the commits between `9bc475cb5e1755e5` and `513421d0d361d0ad`, not the fixture or the harness |
| **BH-27** whole-queue idle | `VulkanContext.beginCommands()/endCommands()` (`83aff9d70`; also `VulkanTerrainGpuBackend.verificationSummary`) | my legs, jar `3abc917b` -> `5f3d3bfb`: `bootstrapQueueWaitMillis` **316 -> 0**, worst **13 -> 0**; new `bootstrapSubmitWaits` **2738**, `...Millis` **234**, worst **6 ms**; `submitTimeouts` 0 -> 0, `gpuStalled` false -> false | 0 -> 0 | **no** at the gate level (`presentedNonBackgroundFraction` 0.952222 -> 0.95232, bands 707 -> 707); pixel-identity **not** established because the two legs sat at different build states (47 vs 88 world frames at capture), and the accepted histogram moved with the run length. Counter fix, not a picture fix |
| **BH-18** swapchain creation contract | `VulkanSwapchain` (`0e45ebaed`), `argumentSummary` | leg presents cleanly: `presentSucceeded=true`, no swapchain probe failure; **the chosen image count / usage / compositeAlpha / format are not printed on the success path** - `argumentSummary()` only reaches a log in the probe-failure message (`VulkanTerrainGpuBackend:8241`) | 0 | not measurable: the adversarial-capability acceptance (min=max=2, max=0, no OPAQUE, no TRANSFER_DST) is not exercisable on this surface, and the per-field values have no print site. **Not accepted as verified** |
| **BH-23** sampler one-shot guard | `confirmSamplerStateFromRecord` (`0e45ebaed`) | observable counters in my leg: `mirroredTextures=34`, `descriptorHits=3529`, `descriptorMisses=31`, `samplerReach=1`; the acceptance sequence (set parameters after upload, change after the first draw, re-draw the same announced image, lower the LOD) is not driven by any fixture hook | 0 | not measurable; the commit itself records the minification meaning as still NEAREST-vs-LINEAR with a fixed LINEAR mip, so the finding is **partially** addressed by its own statement |
| **BH-37** attach-side transition | covered by the in-tree rule (`publishedFrameTexturesSkippedForAttach`), per `0e45ebaed` | round-11 evidence already in row 30: `publishedFrameTexturesSkippedForAttach=18`, `stalePruned=18`, **0 invalid-image / 0 device-lost**, `validationMessageCount=0` | 0 | picture unchanged; the rule is what removed the invalid-image barrier family |
| **BH-09/10/11** scissor / colour mask / polygon offset | `3ab786172` is **analysis only** ("nothing fixed in the tree yet, and no leg taken") | none - no hook, no field, no write mask exists in the tree at that commit | - | nothing to verify yet; the commit's own conclusion is that all three states are **never mirrored** |

**Canon rows re-taken on the current jar** (`r32-cand`, jar `9bc475cb5e1755e5`, fixture `23e13d6b592e3c33`, both entity flags + keepGui, tick 200, 9 entities at capture, against upstream `d12425eaaf184e79` at the same scene/pose/tick): **HUD hotbar mean|d| 1.87** (0.2 % of pixels differ), **entity band (450,150)-(850,330) mean|d| 18.85** (37.8 % differ), **hand (960,500)-(1280,720) mean|d| 21.25** (95.5 % differ). The geometry matches - the same cast in the same places, `drawnByFormat` id9 **228/228** and id1 **47/47** - and the residual is a **shading** difference: the candidate is consistently darker than the reference in those regions (hand `(114.3,96.6,83.9)` against `(131.2,102.8,91.1)`, entity band `(115.8,110.1,118.9)` against `(120.0,121.3,128.4)`), which is the lighting territory of BH-05/BH-31 rather than a missing draw. `windowFlushFailures=0`. The font crop stays legible (row 26's F3 crop and this leg's chat text), and both harness traps stay in the table.

**Canon rows on the green jar** (`r35-cand`, jar `0184a46539e69ebc`, fixture `3275624fb753508b`, both entity flags + keepGui, tick 200, 7 entities at capture, against upstream `d12425eaaf184e79` at the same scene/pose/tick, 10 entities, 281 world frames against our 79): **HUD hotbar mean|d| 2.30** (0.2 % of pixels differ), **entity band (450,150)-(850,330) mean|d| 18.82** (41.2 %), **hand (960,500)-(1280,720) mean|d| 26.27** (98.8 %). **BH-29's half-texel light-map bias moved our hand slightly toward the reference - `(114.3,96.6,83.9)` on the previous jar against `(116.4,98.3,87.8)` here - but the pair cannot attribute the residual, because the reference leg's own hand mean moved the same way (`(131.2,102.8,91.1)` to `(138.4,108.2,95.8)`) and the two legs sit at different build states (79 against 281 world frames, 7 against 10 entities). A same-state pair (the fixture's `captureCondition=sections` with a minimum world-frame count, as the night/day row uses) is what would decide it; this tick-keyed pair does not.**

**BH-10's one-flag control, on a scene with a transparent layer** (the default stack carries `stained_glass` and `leaves`; the flag is the fixture's own `renderClouds`, so the cloud pass is the only difference): `r40-clouds-on` (`renderCloudsRequested=2`, `AtCapture=2`, `PASSED`, `validationMessageCount=0`) reports **`partialColorMaskGroups=506`** and **`colorMaskHistogram=[0x0:161 0x7:345 0xf:5483]`**; `r40-clouds-off` (`renderCloudsRequested=0`, `AtCapture=0`, `PASSED`) reports **`partialColorMaskGroups=368`** and **`colorMaskHistogram=[0x0:2 0x7:366 0xf:5558]`**. So **159 groups gain the zero write mask when Fancy clouds are on** - the depth-only cloud pass's mask reaches the group (and the pipeline) instead of being ignored, which is BH-10's mechanism, separated by one flag. Picture caveat: at this pose the cloud pixels are not in the scored band - whole-frame mean|d| **2.39** (1.1 % of pixels differ >8) and the sky band (0,0)-(1280,200) mean|d| **1.15** (0.0 % differ >8) - so the counter is the acceptance here and the crop is not; a pose that frames the cloud layer would be needed for a picture claim.

**The picture gate on a calibrated scene passes** (`r39-cal`, jar `4f56cad41500c595`, fixture `5839541aba6bf793`, plain terrain - no `keepGui`, no entity flags, i.e. the reference's own variant): **`PASSED`**, `presentedImageVerdict=PASSED`, **0 assertion failures**, `validationMessageCount=0`, `presentedDistinctColours=2142`, non-background fraction **0.759463**, **628 bands**, black 0.0, `sceneVariant="default flat stack, guiScale=3"`, `sceneGateSource` naming the gl-bridge `scene-ref-terrain` runs. So the gate is meaningful and green on the calibrated scene; the `FAILED_PRESENTED_IMAGE` readings the Lead saw come from the `keepGui+captureEntities` variant, whose key is **not** the calibrated one (my legs on that variant read `UNCALIBRATED_PRESENTED_IMAGE` - no floor applied - on three different jars). Both readings are kept: green where a floor exists, uncalibrated where none does.

**The canon rows, re-taken on the current tree** (`r39-cand`, jar `4f56cad41500c595`, tick 200, 8 entities, 75 world frames, against upstream `d12425eaaf184e79` at tick 201, 7 entities, 217 frames): **HUD hotbar mean|d| 1.93** (0.2 % of pixels differ), **entity band (450,150)-(850,330) mean|d| 18.81** (37.7 %), **hand (960,500)-(1280,720) mean|d| 21.82** (95.8 %). **The hand series across four jars** shows BH-29's direction but not its closure: candidate hand `(114.3,96.6,83.9)` on `9bc475cb` (before the half-texel fix), `(116.4,98.3,87.8)` on `0184a465`, `(116.5,98.3,87.9)` on `4f56cad4` - about **+2 red, +2 green, +4 blue**, i.e. slightly brighter and toward the reference - while the reference leg's own hand mean moved `(131.2,102.8,91.1)` -> `(138.4,108.2,95.8)` -> `(133.6,104.7,92.7)` across the same three legs. The gap is still ~17 points in red (116.5 against 133.6). **The pair cannot attribute the residual**: it is tick-keyed and the two legs sit at different build states (75 against 217 world frames, 8 against 7 entities). The row's next step is a same-state pair using the fixture's `captureCondition=sections` with a minimum world-frame count, as the night/day row does; until then the honest reading is "the candidate is consistently darker by ~10-15 % in the entity and hand regions, and BH-29 moved it slightly but did not close it".

**The glyph draw mode: the runs are triangle strips (mode 5), so the coalescing does join consecutive glyphs.** `r37-f3` (jar `70cc1c44e57e4f1b`, fixture `a8b08c74e370dd5b`, `showDebugInfo=true`, `fixtureStatus=PASSED`, `validationMessageCount=0`) prints at shutdown: `CELTIUM_FONT_GLYPH_SUMMARY windows=1119596 defaultWindows=1119596 unicodeWindows=0 bridgeWindows=0 quads=1119596 submitted=1119596 accepted=1119596 refused=0 submitMillis=391 reasons={} **modes={5=1119596}** fontRunsStarted=2433 fontQuadsAppended=1118942 flushedIntervening=2052 flushedBoundary=5 flushedStateChange=376`. **Every one of the 1,119,596 glyph windows is mode 5** - `GL_TRIANGLE_STRIP`, not mode 7 (`GL_QUADS`) - and 1,118,942 quads are appended into **2,433 runs**, so consecutive glyphs share a strip. On the Lead's own branch that is the mechanism branch: mode 5 or 6 means the coalesced runs *are* strips and the joining is what the user sees as bars between characters, not a quad-coalescing artefact. `refused=0` and `accepted=1119596`, so nothing is dropped; the mode is the finding. The menu leg (`r37-menu`, the user's shape) was terminated by the harness before its shutdown hook ran, so it has the progress lines but no summary - the fixture leg's graceful exit is what prints it.

**OPT-05's regression check, from the same legs**: `regionsOpened=1969`, `blockedOpenings=0`, `reclaimedSlots=79`, `hostBuffersCreated=6`, `frameArenaFallbackGroups=0` (`r35-break`); the green jar's normal legs show the same shape, which matches the acceptance the arena was landed with (8316 immediate groups, 6 host buffers, 0 blocked openings, 0 promotions).

**Where the fixers' claims cannot be verified with the current instruments**, and what would make them verifiable
(requests to route through the Lead, since I do not fix and the fixture is gui-stage's):
1. BH-18 needs the chosen swapchain arguments printed on the **success** path, or a leg on a surface with adversarial
   capabilities.
2. BH-23, BH-03, BH-04 and BH-05 need fixture hooks for their acceptance sequences: a texture-parameter change after
   the first draw; a two-quad same-texture colour/texture-matrix/fog change; a lit draw followed by a light change
   before the flush; and the alpha 0/0.05/0.1/0.5/1 x ref 0/0.1/0.5 x GREATER/GEQUAL/LESS/ALWAYS/NEVER table. Without
   them the only available evidence is the general frame against the reference, which cannot separate these states.
3. BH-27's second half ("submit/wait count does not grow linearly with payload count") needs a leg that varies the
   chunk upload volume; my two legs differ in run length, not in payload policy.

## 6j. The frame pass-split instrument (plan §0.227's missing instrument)

The lag-spike decode left `gameRenderer`'s self time mostly unattributed: in the worst 5 % of frames it is 318.6 ms
against 25.0 ms in median frames, the profiler's named children explain ~72 ms of it, and ~246 ms per spike frame
belongs to no section. `frameFenceWaitMillis` is 0.14 ms per wait, orders of magnitude too small to be the mechanism.

**What was landed.** `celtium-vulkan/.../FramePassTimings.java` - a per-frame wall-clock decomposition with its own
brackets, plus mixin brackets at the pass entry points:

- frame = `EntityRenderer.func_181560_a`; world = `func_78471_a`; hand = `func_78476_b`;
- clouds = `RenderGlobal.func_180447_b`; entities = `RenderGlobal.renderEntities` (measured *inside* world);
- particles = `ParticleManager.func_78874_a` (inside world); gui = **`GuiIngameForge.func_175180_a`**.

`residual = frame - (world + hand + clouds + weather + gui)` is computed **per frame** and then summarised; the inside
passes are reported but never subtracted, so nothing is double-counted. The summary reaches the report through the
`celtium/vulkan-check` line as `framePassSplit=[...]` and prints every 200 frames as `CELTIUM_FRAME_PASS_SPLIT`. Off
unless `VulkanDiagnostics.enabled()` or `-Dceltium.vulkan.passSplit=true`.

**Two limits, stated rather than implied.** (1) This is not the profiler's decomposition and must never be added to
it: the profiler's self-time sum is 4.01x the frame time because `frame`, `root` and `gameRenderer` each hold all of
it, while this is a single non-overlapping bracket of the frame. (2) The user's capture carries no jar hash, so it is
the user's build rather than a known one; it is used for ordering and ratios only, and acceptance is taken on our own
legs.

**First measurement** (`r42-split`, jar `1ad2ad6238a2a416`, fixture `d6aba83dfe8cf108`, plain terrain + keepGui,
`PASSED`, 107 frames):

| bracket | p50 | p95 | worst |
|---|---|---|---|
| frame | 31.9 | 83.7 | 465.2 |
| world | 21.9 | 57.7 | 364.6 |
| gui | 3.9 | 16.6 | 92.0 |
| clouds | 1.8 | 8.5 | 17.6 |
| hand | 0.5 | 2.4 | 41.0 |
| weather | 0.0 | 0.0 | 0.0 |
| **residual** | **0.0** | **2.1** | **34.3** |
| particles (inside world) | 2.6 | 11.4 | - |
| entities (inside world) | 0.0 | 0.0 | - |

So **on our leg the top-level passes account for the frame** - the residual is 0.0 ms at p50 and 2.1 ms at p95, where
the user's capture has ~246 ms of 318.6 ms `gameRenderer` unattributed. That is the instrument's first result and it is
a negative one for the "our passes hold the residual" hypothesis: whatever holds the user's 246 ms is not a top-level
pass of the kind bracketed here, and the user's build differs from ours. The `gui` reading also confirms the
instrument's shape: vanilla's `GuiIngame` hook never fired (Forge's `GuiIngameForge.func_175180_a` overrides it
without calling `super`), which is why the first leg read `gui=0.0` while the HUD was plainly in the frame; retargeting
it to the Forge class is what made `gui` appear. `entities=0.0` is consistent with this leg having no entity flags (the
scene has no mobs); an entity leg is what would confirm that bracket.

The user's capture's other numbers, recorded so they are not re-derived: `gui` is a constant ~13.7 ms per frame (about
half the median frame) and does not grow in spikes; `forgeHudText` totals 2.7 ms and `chat` 201.8 ms over the whole
20 s - so in that capture **the text path is a constant tax, not a spike source**, which is a different conclusion from
what the earlier screenshots suggested.

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
