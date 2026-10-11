# Celtium: what is Vulkan, what is not, and what is not supported

This file is the single place where the project states its boundary. Everything in it is either a measurement recorded in
`docs/history/2026-10-09-investigation-log.md` or a decision recorded in `docs/plan.md`; nothing here is a projection.
It is written because the objective requires the supported and unsupported scope to be explicit, and because the honest answer
to "is it Vulkan yet" is a list rather than a word.

> **Sections 0 and 8 are the current verdict (2026-10-11).** Section 0 is the requirement table completion condition (e)
> asks for: each requirement with the file and line that enforces it, each claim with the measurement behind it, and the
> named list of what is unverified. Sections 1-7 are the earlier English text and are corrected in place where a later
> measurement contradicts them; the Japanese addendum at the end is a **dated stage record (2026-10-09/10)** kept for
> provenance and is **not** the current state - several of its "未達成" rows were closed afterwards, and §0 says which.

## 0. What the code requires, where it is enforced, and what measured it

Every row names the file and line that enforces the requirement today. "Fail-closed" means the renderer refuses rather
than degrading silently. A row whose measurement column says "not measured" is exactly that - it is not a claim.

### 0.1 Runtime and device requirements

| Requirement | Enforced at | Behaviour when unmet | Measurement |
|---|---|---|---|
| Vulkan **1.2 or newer** loader | `celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java:86-87` (`REQUIRED_API_MAJOR`/`MINOR` = 1/2), `:154` (`isAvailable()` requires `VK.getInstanceVersionSupported() >= VK_API_VERSION_1_2`) | `isAvailable()` returns `false` and never throws; a missing binding class, a missing native loader and a too-old loader all report `false` | Vulkan loader 1.4.363, `vulkanDeviceCount 3`, in every run's `logs/celtium-capabilities.json`; device selected as AMD RX 6400 (`nativeDeviceName`, `nativeDeviceUuid 00000000030000000000000000000000`) |
| Physical device `apiVersion >= 1.2` | `VulkanContext.java:759-763` | the device is **skipped**; if no device is left, `IllegalStateException` at `:775-780` | both verified GPUs enumerate 1.4.x (AMD RADV NAVI24 1.4.354; Intel UHD 630 accepted in the same fixture) |
| The device is not a CPU device | `VulkanContext.java:756-758` (`VK_PHYSICAL_DEVICE_TYPE_CPU` skipped) | skipped, so llvmpipe cannot be selected | the capability census lists AMD (type 2), Intel UHD 630 (type 1) and llvmpipe (type 4); the llvmpipe entry is never chosen - `docs/performance-2026-10-11.md:229` |
| Same-GPU identity with the GL device (bridge path) | `VulkanContext.java:753-755` | the device is skipped and a **software Vulkan device is not used instead** (`:775-780`) | `CELTIUM_NATIVE_DEVICE ... matchedOpenGl=true` in bridge runs, `matchedOpenGl=false` under GL-less (no GL identity exists) - `docs/performance-2026-10-11.md:217-218` |
| `VK_KHR_external_memory_fd` **and** `VK_KHR_external_semaphore_fd` | required at `VulkanContext.java:795-803`, enabled at `:838-839` | `IllegalStateException` naming the missing extensions | the interop experiment verified FD export/import on both GPUs - `docs/interop-validation.md` |
| One queue family with **GRAPHICS and TRANSFER** | `VulkanContext.java:806-831` | `IllegalStateException` ("no queue family that supports both graphics and transfer") | every accepted run records `passes`/`skipped`; a device without such a family has never been available to test, so this row is enforced but **not measured against a failing device** |
| `VK_KHR_swapchain`, enabled only when advertised | `VulkanContext.java:845-856` | not enabled; the present path needs it and reports the refusal | present traces: `presentedFromVulkan=287` = `glSwapsSkipped=287` (`docs/plan.md` §0.114) |
| A depth format the device accepts, from a candidate list | `celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanDepthFormat.java:27-61` (`D32_SFLOAT` → `D24_UNORM_S8` → … → `D16_UNORM`), `:82` (`chosen()`) | per-candidate refusal with the reason; the terrain pipeline refuses rather than guessing | AMD has no sampled 24-bit depth, so the chooser picks from the list; the bridge regression of 09:47 was this contract having no caller (`docs/plan.md` N10, fixed at `0fddd6ccf`) |

### 0.2 The GL-less path

| Requirement | Enforced at | Behaviour when unmet | Measurement |
|---|---|---|---|
| The window is created with **no GL context** (`GLFW_CLIENT_API = GLFW_NO_API`) only when the flag is set | `forge122/src/main/java/org/taumc/celeritas/mixin/core/LwjglxDisplayMixin.java:31` (reads `celtium.vulkan.glless`), hint written at `:55-57` | with the flag absent the ordinary GL window is created and the GL bridge is used - the flag is **off by default** | `CELTIUM_GLLESS window hinted GLFW_NO_API`; `-Dceltium.vulkan.glless=true` is on every GL-less run cited in this file |
| The splash/progress path does not make a GL context current under the flag | `forge122/src/main/java/org/taumc/celeritas/mixin/core/SplashProgressMixin.java:19` | without the flag the ordinary path runs | same runs |
| The frame target is created by Vulkan, not imported from GL | `VulkanTerrainGpuBackend.java:8286` (`GllessFrameTarget.create`), `GllessFrameTarget.java:163-173` | the imported-GL-target path is used | colour+depth images created, depth format chosen from the device (`GllessFrameTarget.java:206-216`) |

### 0.3 Java

| Requirement | Enforced at | Behaviour when unmet | Measurement |
|---|---|---|---|
| Celtium's own modules compile at **`--release 21`** (class major 65) and use no Java 22+ API | `celtium-vulkan/build.gradle:13`, `celtium-interop/build.gradle:11` | the compile fails | measured on the shipped jar: **90 classes at major 65**, none later. (The earlier count of 49/86 in older docs was a different jar.) |
| Forge-facing code compiles at **`--release 8`** (class major 52) with a **JDK 21** compiler toolchain | `forge122/build.gradle.kts:138-139` (`sourceCompatibility = "21"`, `options.release = 8`), `:249-250` | the compile fails | measured on the shipped jar: **618 classes at major 52** |
| The jar contains no class from a release later than 21 | `scripts/verify_forge_jar.py:136-145` (`major > 65` is an error) | the jar is rejected by the verifier | measured class-major histogram of the shipped jar: `{46: 111, 51: 1, 52: 618, 65: 90}` - the 46/51 classes are the vendored upstream code, i.e. **older**, and 65 is the newest |
| Gradle itself runs on **JDK 25** | `README.md` "Minecraft 1.12.2 build"; `buildSrc/src/main/kotlin/celeritas.platform-conventions.gradle.kts:20-26` (toolchain 25 for 26.1+, 21 otherwise) | toolchain resolution fails | every run in `docs/performance-2026-10-11.md` records Java 25 (`§6.7`); the validated launch uses `/usr/lib/jvm/java-25-openjdk-amd64` |
| The **game** is verified on **Java 25**; it is **not** claimed that Cleanroom runs on Java 21 | this file §5 | - | Cleanroom's own launcher classes are major 69; only Celtium's classes target 21 |

### 0.4 Verified and unverified, named

| Axis | Verified | Unverified (explicitly) |
|---|---|---|
| GPU | AMD Radeon RX 6400 (RADV NAVI24, Mesa 26.2.4-1) and Intel UHD 630 | **NVIDIA, every other AMD/Intel part, every other driver, and llvmpipe** (skipped by type, never accepted) |
| OS | Linux, headless Sway/Xwayland compositor, 1280x720 | **Windows, macOS, and every other compositor or display server** |
| Runtime | Cleanroom 0.6.13-alpha, LWJGL 3.4.1, Java 25 | every other Cleanroom, LWJGL or Java version |
| Vulkan | loader 1.4.363, devices exposing 1.4.x, `VK_KHR_external_memory_fd`/`external_semaphore_fd`, a graphics+transfer queue family | a device that fails any row in §0.1 has **never been available to test**, so the refusal paths are enforced but unmeasured |
| Mods | see `docs/mod-compatibility.md` | all mods other than the ones named there |
| Performance | AMD RX 6400, one flat scene, render distance 12, 900 frames per configuration: GL-less 3.94 ms p50 / 253.8 fps, bridge 4.89 ms p50, reference 16.10 ms p50 (a 60 Hz floor, not a render cost); CPU per frame 3.33 / 3.94 / 3.09 ms (`docs/performance-2026-10-11.md` §4.1, §4.4) | **Intel GL-less performance, GPU time on either side, every other scene, and the validation fixture's own p50 numbers** (AMD 16.02 ms bridge vs reference 16.18 ms, Intel 28.54 ms bridge vs reference 16.17 ms) which come from a different harness and are **not merged** with the benchmark dataset |
| Scanout | the compositor output was captured and bound pixel-exactly to the presented frame (`docs/compositor-output-capture-2026-10-11.md`) | **scanout itself** (needs DRM master) |

## 1. What is drawn by native Vulkan, measured

Each entry below is a measured acceptance count from the per-format counters (`CELTIUM_ACCEPTED`, printed incrementally to
stdout) and the end-of-run report, on AMD RX 6400 and Intel UHD 630.

> **On the name "JEI" in the run names and older rows below (measured 2026-10-11):** the fixture flag
> `-Dceltium.validation.jeiScreen=true` opens `net.minecraft.client.gui.inventory.GuiInventory`, the **vanilla
> inventory** (`ValidationClient.java:3032`), and **no JEI jar is loaded** in those runs - the run's `mods/` directory and
> its `latest.log` contain no JEI entry. "JEI" here names the *screen variant*, not a mod that was tested.

| Content | Layout | Evidence |
|---|---|---|
| Terrain | the mod's own terrain pipelines | `vulkanTerrainPasses` 915-1653 (AMD), 330-834 (Intel); `glDrawsCancelled` matching one for one |
| GUI, font, HUD, hand | `POSITION_COLOR`, `POSITION_TEX_COLOR` | `outsideWorldPassAccepted` in the thousands per run, refusals 0 |
| Blocks | `BLOCK` | per-format acceptance, plus the S05/S06 audit fixtures |
| Items and entities | `ITEM` (position, texture, colour, normal) | `4:427` in the JEI recipe run - 431,617 arrivals of it in one partial run, all mapped |
| Particles | `PARTICLE_...` | per-format acceptance; the particle fixture exercises it |
| Sky, clouds, weather, water surface | colourless `POSITION_TEX` | `6:1353`-`6:2374` (AMD), `6:660`-`732` (Intel) |
| Position-only content | `POSITION` with the colour passed in the push block | `7:1008`-`1097` (AMD), `7:396` (Intel) |
| The composite's layout | `POSITION_TEX_NORMAL` | `9:12` on both GPUs |
| The game's other framebuffers | off-target render targets | measured by the command-buffer acceptance fixture: `offTargetAccepted/Recorded/Completed = 2/2/2`, red and green rectangles at the expected pixels in the target's own PNG, untouched pixels unchanged |
| Final composite and present | the Vulkan swapchain | present traces cycling the swapchain images, `presentFailures=0`, `outOfDate=0`. **The default-framebuffer composite is refused before acceptance** and stays GL's |

**Unmapped arrivals: zero**, verified on both GPUs and in a partial Intel run carrying **674,500** uploads.

Appearance: with these layouts left to OpenGL versus drawn by Vulkan, the frame differs by **0.267 per channel** against a
measured **scene noise floor of about 1.0** - equal to the GL path on a local comparison, which is not a claim of
pixel-identity.

### 1.1 The validation fixture's own numbers (a different harness - do not merge with §0.4's)

These are the numbers completion condition (e) has been quoted with. They come from the **validation fixture's** runs, not
from the benchmark harness in `docs/performance-2026-10-11.md`, and they are a **past stage record**
(`docs/history/plan-round-log-until-2026-10-10.md`), so they are restated with their provenance rather than merged into the
benchmark table:

| What | Measured | Source |
|---|---|---|
| Celtium bridge, AMD RX 6400, default configuration | p50 **16.02 ms**, `exit 0`, `PASSED` | `docs/history/plan-round-log-until-2026-10-10.md:1204` |
| The pinned Celeritas reference, same fixture | p50 **16.18 ms** (AMD) | same file, `:1213` |
| Celtium bridge, Intel UHD 630 | p50 **28.54 ms**, `PASSED` (from 42.66 ms earlier in the same session) | same file, `:1205`, `:1213` |
| The pinned Celeritas reference, **Intel** | p50 **16.17 ms** | same file, `:1031-1033` |
| **The reference is GPU-independent**: 16.18 ms on AMD against 16.17 ms on Intel | the reference's frame time is a wait, not a GPU cost | same file, `:1033` |
| Picture distance from the reference | **meanAbsDiff 7.78/255**, HF ratio 0.81 | same file, `:210`; quoted in `docs/plan.md:4256` |
| Controls for that distance | reference-vs-reference 0.22, candidate-vs-candidate 0.13 (`:210`); the plan row quotes 0.064 / HF 1.002 for its own pair | same |

**What these numbers are not:** they are not from the same harness as §0.4's benchmark dataset (different scene, fixture,
control set and measurement window), the two sets are **not merged**, and neither set measures GPU time. The benchmark
dataset is the one with the load gate, the 900-frame window and the paired controls.

## 2. What is still OpenGL, stated plainly

**Corrected 2026-10-11.** The three bullets below were written while the GL context was still required. They are kept
with the correction because "the GL context exists" and "the game can start without one" are both true of the current
build, of different configurations:

- **The GL context exists in the default (bridge) configuration**, and the GL/VK bridge is built on it (a shared image
  and semaphore pair). **Under `-Dceltium.vulkan.glless=true` there is no GL context at all**: the window is created
  with `GLFW_NO_API` (`LwjglxDisplayMixin.java:31`, `:55-57`), the game's own framebuffer is adopted into Vulkan-owned
  colour and depth images, and the finished frame is presented from the Vulkan swapchain with the GL swap skipped
  (`presentedFromVulkan=287` = `glSwapsSkipped=287`). Completion condition (3)/(b) is closed for the real scene, model/
  TESR and Nether, with the remaining items named in §0.4 and `docs/plan.md`.
- **The bridge's synchronisation and the window's buffer swap** remain GL's *in the bridge configuration*; under GL-less
  the swap is Vulkan's and the GL swap is skipped one-for-one.
- **Terrain mesh building and the mod's own chunk bookkeeping** are not drawing and were never in scope for replacement.
- **Nothing observed is left unmapped on the immediate path.** In the runs recorded here, not one upload was refused for being
  unrecognised; the legacy path is a fallback that currently has no traffic, and it is kept for the content this project has
  not seen. (One exception is named rather than hidden: `id0=57/0` in a 2026-10-11 GL-less run - 57 offers mapped to no id
  at all and refused. Not GUI content, and not yet attributed to a format.)

## 3. Environment limits on verification

**Corrected 2026-10-11.** The first bullet below was **retracted by measurement**, not softened:

- **The compositor output *was* observed.** `-Dceltium.vulkan.presentMarker=true` made the private headless wlroots/Sway
  output (`HEADLESS-1`, 1280x720) a uniform magenta for 14 captures, and compositor capture `0035` is **0 of 921600
  pixels different** from the renderer's own numbered presented dump `frame-20.png`; capture `0066` matches `frame-100.png`
  the same way. The earlier claim that "the X display reports a `0x0` screen and no capture tooling is installed" was
  wrong: sway 935 runs on `wayland-1` with a real `HDMI-A-4` output and Xwayland `:0`, and `grim` captures it.
  **What is still unmeasured is scanout itself** - `kmsgrab` needs DRM master and non-root cannot have it. Evidence:
  `docs/compositor-output-capture-2026-10-11.md`, `docs/plan.md` §0.123.
- **Intel plus JEI is borderline against the harness's duration cap.** The same jar completed at 720 seconds and timed out at
  720 seconds on consecutive attempts. Partial runs still yield evidence because the arrival and acceptance counters print
  incrementally.
- **The fixture's tick hooks advance one per game tick**, so on the slower GPU a fixed wall-clock budget reaches fewer of them;
  the hooks fire as early as the world is certainly up and the end-of-run tick count is reported.

## 4. Not supported, with reasons

- **Normal Forge (old Java, LWJGL 2) and other Forge + lwjgl3ify/RFB setups**: out of scope. Celtium is a Cleanroom renderer
  and uses Cleanroom's modern runtime. This is a decision, not a limitation discovered later.
- **Direct LWJGL, GLSL and native-GL mods**: not emulated. The project does not translate GL call by call and will not; such
  mods are investigated individually and their drawing sites either get a specific equivalent or are listed as unsupported.
- **Mesh shaders**: never required, by policy.
- **Zink**: not adopted. If ever considered, its conditions are checked and it is kept separate from the pure native path.
- **"Forge API swap makes every mod work"**: not claimed anywhere, and it will not be.

## 5. Java versions

- **Celtium compiles to `--release 21`** (class major 65) and its code uses no Java 22+ API, language feature or preview.
  Enforced at `celtium-vulkan/build.gradle:13` and `celtium-interop/build.gradle:11`.
- **The Forge-facing code compiles to `--release 8`** (class major 52) with a JDK 21 compiler toolchain
  (`forge122/build.gradle.kts:138-139`, `:249-250`).
- **Measured on the shipped jar** (2026-10-11): class-major histogram `{46: 111, 51: 1, 52: 618, 65: 90}` for the whole
  jar. The newest major is 65, i.e. **no class is newer than release 21**; the 46 and 51 entries are vendored upstream
  code and are *older*. The counts 49/86 that older notes quote are from a different jar and are superseded.
- **The game runs and is verified on Java 25.** The class files of Cleanroom's own launcher are major 69 and that is not a
  blocker.
- **It is not claimed that the whole game, Cleanroom included, runs on Java 21.** Only the Celtium classes are compiled for 21.
- **The Vulkan bindings are not bundled**: they must be on the game's classpath and match the validated Cleanroom
  installation's LWJGL version (3.4.1). `celtium-vulkan/build.gradle:18-19` documents this; the measured jar contains
  **no** `org/lwjgl` class.

## 6. Licensing and lineage

- The pinned upstream base is Celeritas at `989ebf2e`, whose lineage runs through Embeddium and Sodium 0.5.11 or earlier -
  FOSS code. Celtium is LGPL-3 (`COPYING`, `COPYING.LESSER` in the repository root).
- **No code from Sodium 0.6 or later (PolyForm Shield 1.0.0) has been copied, translated, renamed or cherry-picked.** Where an
  idea comes from published descriptions, its source is recorded in `docs/plan.md` rather than presented as original.
- Rewriting is not treated as a way to make a licensing question disappear.
- **What the shipped jar actually contains, measured 2026-10-11** from the built jar itself
  (`unzip -l celtium-forge-mc1.12.2-2.5.12-celtium.0.1.0-dev.1.jar`, and the census
  `scripts/verify_forge_jar.py` now prints): `org/embeddedt` 485 classes, `org/taumc` 207, `org/soutalab` 102,
  `grondag/bitraster` 13, `common/xyz/wagyourtail/jvmdg` 13, `org/embeddedt/embeddium/impl/shadow/joml` 111.
  No `org/lwjgl`, no unrelocated `org/joml`, no `net/minecraft`. Each bundled third-party component and the notice
  that covers it:
  - `grondag/bitraster` (13 classes) → **`licenses/bitraster-LICENSE`, Apache-2.0**.
  - `common/xyz/wagyourtail/jvmdg` (13 classes, the **JVM Downgrader** runtime stubs shaded in by
    `common/build.gradle`'s `shadeDowngradedApi`, embedded through `forge122/build.gradle.kts`) →
    **`licenses/jvmdowngrader-LICENSE.md` and `licenses/jvmdowngrader-LGPLv2.1.md`**. The licence was read from the
    dependency's own metadata, not from memory: `jvmdowngrader-2.0.1.pom` declares
    `<licenses><license><name>LGPL-2.1</name>`, and its `LICENSE.md` offers LGPL-2.1 **or** a commercial licence and
    states explicitly that shading its api classes into a Combined Work is permitted provided the unshaded jar or the
    source is available - which this repository is (LGPL-3.0, sources included). Both files are shipped byte-identical
    to the ones inside `jvmdowngrader-2.0.1.jar`, and the verifier compares them.
  - `org/embeddedt/embeddium/impl/shadow/joml` (111 classes) → **`META-INF/LICENSE`, the JOML MIT notice**.
    **Correction, 2026-10-11:** an earlier entry in this file called that notice a defect "because no JOML class is
    bundled". That was wrong and the instrument was at fault: JOML **is** bundled, relocated to
    `org/embeddedt/embeddium/impl/shadow/joml/` (version 1.10.5, from
    `META-INF/maven/org.joml/joml/pom.properties`), and a `grep -c 'org/joml'` on the jar returns 0 precisely because
    the package was relocated. The MIT notice is required by the licence of the code that is shipped, and the jar
    verifier had already been requiring that class and that notice together.
  - The project's own licence is in the jar at the root: **`COPYING` and `COPYING.LESSER` (LGPL-3.0)**, byte-identical
    to the repository's copies.
  - The manifest carries `Celtium-License: LGPL-3.0` and `Celtium-Vulkan-Minimum: 1.2`. The stale
    `Celtium-Stage: backend-boundary` (a 2026-10-09 label) is gone.
- **The decompiled Minecraft and Forge sources** that `scripts/mc-sources.sh` produces are reference material extracted
  on demand from the jar the build already uses. They are **not redistributed in the jar** (measured: no
  `net/minecraft` entry) and are Mojang/Forge-licensed material kept out of the distribution.
- **The verifier now checks the licence content, not just its presence** (`scripts/verify_forge_jar.py`, extended
  2026-10-11): it requires `COPYING`/`COPYING.LESSER`/`META-INF/LICENSE`/the two downgrader notices, reads
  `META-INF/LICENSE` and fails unless it is the JOML MIT notice *while* relocated JOML classes are bundled, prints a
  **licence census** of every bundled third-party package against the notice that covers it, compares the two
  downgrader files byte-for-byte with the dependency jar at the version pinned in `common/build.gradle`, and fails the
  jar if that pin and the resolved artifact disagree. Its scope line is now "packaging and licence content only".
  A negative control was run: a copy of the jar with those three notice entries removed reports
  `verified: false` with eight named errors, so the checks are live rather than vacuous.

## 7. How the claims above are measured

- **Arrival census**: what the frame offered, by mapped id and by element signature for anything unrecognised, printed every
  five hundred arrivals to stdout.
- **Acceptance counters**: what this renderer took, by id, printed every five hundred acceptances and in the end-of-run report.
- **Refusal reasons**: the backend's own text for a refused batch, reported once per format by the uploader hook. Every one of
  the eight guards and defects on these two paths was found this way rather than by reading the mapping.
- **Attribution switch**: the acceptance mask, which lets the same scene be rendered with a layout on Vulkan or left to
  OpenGL, for the frame comparison above.
- **Provenance**: each run's lock records the git head, subject, dirty state, effective JVM arguments, the fixture hash and, at
  the end, the driver.


---

# 第3回監査後の現状（実測に限定した再掲）

> **これは 2026-10-09/10 時点の段階記録であり、現在の判定ではない。** 冒頭の §0 と §0.4 が現在の受入状況である。
> 以下の表のうち「完了条件(b) GL contextなしの起動・描画: **未達成**」「完了条件(c): **未確認**」「FBOのVulkan完了: 未達」
> は**その後閉じた**（GL-less の起動・提示・実 scene・Nether 到達は `docs/plan.md` §0.114〜§0.127、
> compositor 出力の画素一致は `docs/compositor-output-capture-2026-10-11.md`）。この節は来歴として残し、削除しない。

本書の対応範囲は**ソースと実測**に限定する。数値は第3回監査（`docs/audit-2026-10-09-third.md`）と監査後の実測に対応する。
現在の完了判定は `docs/plan.md` の現状表を唯一の根拠とする。以下は**上書き**であり、古い記述より優先する。

| 項目 | 実測された現在 |
|---|---|
| 完了条件(a) 全vanilla描画＋合成・present | **未達成**。terrainと即時系のみnative。比較worldは固定flat |
| 完了条件(b) GL contextなしの起動・描画 | **未達成**。`withoutGlIdentity` は**identity照会省略の診断**であり、GL contextなし描画ではない |
| 完了条件(c) 実window出力への到達 | **未確認**。capture方法の調査を残す（x11grab一方式の失敗で断定しない） |
| FBOのVulkan完了 | **未達**。A32修正後は `offTargetPassesSkippedEmpty=2488` / `offTargetCopiedBack=0` で、**以前の1957は空targetのclear/copyだった**。off-targetの受理は0のままで、target別の順序・clear・depth・resize・sampleは未確認 |
| format認識 | format 2は認識されるが**既定maskではGLに残る**（`2:0`）。受理数は描画の証明ではない |
| texture供給 | 全面upload・patch経路とも実装。`imageFilledMirrors` は**適用回数**でresource数ではない |
| 画像一致 | 68.81%（旧baseline対地形）と 0.267/255・0.40%（同一Celtiumの切替）は**最新版全体の一致率ではない** |
| Java | jar内 `org/soutalab/celtium`: major 65が49、major 52が12。**Java21より新しいclassは無い**。ゲーム実行はJava25 |
| 監査P0 | A01（平行移動）・A02（平坦texel）・A03（patchの保存）は**修正済・受入済**（exit 0 / clean / message 0 / PASSED） |
| 保留 | JEI大規模budget反復は**延期**。影mod/shader packは**本体完成後**（vanillaのShaderGroup・spectator効果は本体機能） |
| 証拠保全 | `.celtium-work/audit-2026-10-09-third` は監査者のpinned再比較証拠であり**削除しない** |

## 追補（A32・2026-10-09）

- native passの**存在**と**coverageの検証**を分ける。nativeで描いているのは terrain、即時系family（block/item/particle/position/position_tex/tex_color/composite）、GUI/font/HUD・hand、particles、sky/cloud/water等のformat-6コンテンツ。**未検証**は全block/state・全entity/TESR・破壊/selection overlay・dimension・FBO/postprocess・spectator 3視点。**未実装**はvanilla ShaderGroupとentity outline。
- off-target passは**一致groupのみretire**し、他targetのgroupは保持する（A32）。空targetでは passを開始しない。
- A03は**通常fixture cleanのみでは閉じない**。2つの独立target/groupのCPU契約確認と色pattern受入が必要。

## 追補（コマンドバッファ所有権・2026-10-10）

- immediate系の記録は **backend専用の `VkCommandBuffer` 1本**で行い、その状態は `VulkanCommandBufferState`（handle別ライフサイクル）が持つ。
  `VulkanContext` の bootstrap 用 handle とは別物であり、context 全体の boolean で代用しない。
- 受理済みgroupは `pending → recorded（記録済み）→ retired（submit成功）→ fence確認後に解放` の順で所有権が移る。
  失敗時は queue へ戻し、**描かなかった geometry を捨てない**。
- 順序境界（Framebuffer の bind/unbind、clear、viewport、texture upload の告知）では、**GLが状態を変える前**に
  その時点の target の受理済みgroupを同期windowで描く。
- 旧 `VulkanOffscreenTarget` の scaffold（常に拒否し、空passをclear/copyしていた）は削除した。off-target の実描画は
  上記windowが担い、**copy-back回数を描画の証拠にしない**。
- 失敗時の扱い: submit前の失敗は再試行1回（資源は保持）、fence timeout/error は `gpuStalled` を立てて以降の記録・
  解放を行わない。**submit成功後失敗と device lost は分岐のみで実機注入は未実施**。
- 受入の実行: `bash scripts/run-command-buffer-targets.sh amd|intel <new-run-name> [failImmediateSubmits]`。
  非0終了は Python/fixture/verdict のいずれかの失敗を意味する。

## 追補（両GPU受入・2026-10-10）

command buffer の所有権修正と format 2（POSITION_TEX_COLOR）の既定受理について、**AMD RX 6400 と Intel UHD 630 の両方**で
次を受入済みとする。

| 検証 | AMD | Intel |
|---|---|---|
| target受入（main＋別FBOの実geometry・カウンタ4段一致・画像不一致0） | `sol-cb-windowcut-amd` / `sol-cb-final-amd` | `sol-cb-clean-intel` |
| submit前失敗の注入（記録=受理=submit=完了=4・`retries=1`・資源保持） | `sol-cb-final-retry-amd` | `sol-cb-clean-intel-retry` |
| 通常fixture回帰（format 2既定受理） | `p31c-windowcut-amd`（worldFrames 218、即時2829件一致） | `p31d-intel-clean`（worldFrames 154、即時2155件一致） |
| clear順序（描画→clear） | `sol-cb-clearorder-amd` | `sol-cb-clearorder-intel` |
| depth順序（近傍が遠方を隠す・D24往復） | `sol-cb-depth5-amd` | `sol-cb-depth-intel` |

証拠: `.celtium-work/sol-command-buffer/acceptance-summary.json`（各runのexit/clean/messages/fixture/passed/delta/worldFramesと
3枚のPNGハッシュ）。再現: `sol-cb-base-amd`（70メッセージ）。純ロジック: `VulkanCommandBufferStateTest` 4件。

**フレーム比較の決定性（2026-10-10 実測）**: 同一ビルド・同一flagの2 runでも**最大24.8%のピクセルが異なる**
（別の組では1.7%・1.4%）。したがって `compare_validation.py` のピクセル一致率だけで受入/不受入を決めない。
参照との差を主張するときは、**複数runで再現する領域統計**（例: 地形帯の平均色）を使い、
ピクセル単位の比較は決定性を別途示した場合に限る。

**shader実験の前提確認（2026-10-10）**: `compileTerrainShaders` は `src/main/shaders` を `@InputFiles` として追跡しており、
jar内の `terrain_solid.frag.spv` の SHA-256 は現在のソースを手動で `glslangValidator -V --target-env vulkan1.0` にかけた結果と
**バイト一致**する（`bc1e741db9983d32…`）。shaderを変更した実験は、この一致を確認してから結論に使うこと。

**測定手順の注意**: 2つのGPU runを同時に走らせると、headless sway と Minecraft 本体がCPUを奪い合い、
Intel側が480秒上限に達して `MISSING_REPORT` になる。これは環境の競合であって実装の退行ではない。
**単独で実行する**こと。
