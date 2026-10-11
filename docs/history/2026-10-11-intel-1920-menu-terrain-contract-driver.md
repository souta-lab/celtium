# The current work on the Intel iGPU: 1920x1080 menu, terrain, contract, driver (task-40 / task-46) — 2026-10-11 18:5x

Condition (d) needs both vendors, and the newest work had only been exercised on AMD. This is the Intel pass:
the menu at 1920x1080 through the compositor-resize path, the terrain scene against the upstream reference,
the `CELTIUM_DEVICE_CONTRACT` line, and the real driver identity behind F3 (task-46).

Device pinning for every leg: `VK_ICD_FILENAMES=/usr/share/vulkan/icd.d/intel_icd.json`,
`--render-node /dev/dri/by-path/pci-0000:00:02.0-render` (renderD128), `--require-gpu Intel`, harness slot
arbitration. Script: `.celtium-work/runtime/intel-legs/intel-40.sh` (task-40) and
`.celtium-work/runtime/intel-legs/driver-legs.sh` (task-46).

## 1. The menu at 1920x1080 on Intel — it fills the window

`intel40-menu1920` (GL-less, fixture frozen at that run; `glVendor=Celtium`, `validationClean=true`,
`validationMessageCount=0`, verdict `UNCALIBRATED_PRESENTED_IMAGE` — the menu stage is documented as one the
verdict does not judge):

| counter | value |
|---|---|
| `resizeWindowFrom` → `resizeWindowTo` | `1280x720` → `1920x1080`, `windowSizeChanges=1` |
| `resizeCompositorResult` | `mode=1920x1080@60Hz exit=0 out=[ { "success": true } ]` |
| `resizeObservedFrom` → `resizeObservedTo` | `1280x720` → `1920x1080` |
| `glfwWindowSizeAtEnd` | `1920x1080` |
| `menuDumpWindowSize` / `menuDumpClientTick` | `1920x1080` / 30 |
| the kept dump | `screenshots/celtium-presented-menu-run-15d1b5dc5eabf-66d3c617-kept.png`, **1920x1080** |

Pixels of that dump, measured with `.celtium-work/runtime/intel-legs/menu-check.py`: black(max≤8) 9.59%,
449 distinct colours, non-background 0.9718; band means top (218.6,218.6,218.6) 0.00% black, left
(106.5) 4.63%, right (99.8) 5.41%, centre (99.1,100.0,103.9) 7.13%, bottom (47.9) 72.40% black. The preview
`.celtium-work/runtime/intel-legs/crops-menu-1920-preview.png` shows why the bottom band is dark and is not a
hole: it is the panorama's own dark lower third with the "Minecraft 1.12.2 / MCF 9.42 / Cleanroom
0.6.13-alpha / 15 mods loaded" line at the bottom-left edge and the copyright at the bottom-right edge,
both inside the 1920x1080 frame. The world dump from the same leg is 1920x1080 with **0.0000% black** and
every band populated, so the frame is not a 1280x720 image padded into a larger window.

## 2. The terrain scene on Intel against the upstream reference

`intel40-terrain` (GL-less, Intel, tick 206, pose `x=0.5 y=12.0 z=0.5 yaw=180 pitch=20`, 1280x720):
verdict **PASSED**, `sceneCalibrationKeyMatches=true`, `worldFrameNonBackgroundFraction=0.761032`,
`worldFrameDistinctColours=2324`, background `0x571716`.

Reference: the stored Intel leg `intel-ref` (upstream Celeritas `d12425eaaf184e79…`, real GL on the same
iGPU, same scene/pose/tick 200). A same-tree reference leg was queued twice and never got the slot before it
expired (`intel40-ref`, `--slot-wait 1200`), so this comparison uses the stored picture and says so; the
fixture builds differ between the two legs.

| rows | pixels >8 | mean abs diff | max |
|---|---|---|---|
| 0-120 (sky band) | **0** of 153,600 | **0.733** | 4 |
| 0-200 | **0** of 256,000 | **0.453** | 4 |
| 200-260 (horizon band) | 16,704 of 76,800 (21.75%) | 22.994 | 249 |
| 260-720 | 6 of 588,800 (0.00%) | 0.006 | 227 |

Row means agree to a tenth of a count: `(148.6,186.5,255.0)` at y0, `(155.8,191.4,255.0)` at y60,
`(175.7,204.9,255.0)` at y120, the fog colour `(179.0,207.0,255.0)` at y200. The sky band is the same
result AMD gives (0 pixels over 8, mean 0.453, max 4); the 200-260 band is the terrain silhouette that
differs on both vendors and belongs to the terrain stage.

## 3. `CELTIUM_DEVICE_CONTRACT` on Intel

```
CELTIUM_DEVICE_CONTRACT name="Intel(R) UHD Graphics 630 (CFL GT2)" uuid=8680923e000000000002000000000000
  deviceType=1 apiVersion=1.4 queueFamily=0
  required=[api>=1.2, queue=graphics+transfer, VK_KHR_swapchain, color=R8G8B8A8_UNORM
            depth=D32_SFLOAT(0x7e) samples=1]
  fdInterop=false enabledDeviceExtensions=[VK_KHR_swapchain]
  limits=[maxPushConstantsSize=256 immediatePushBytes=208 maxImageDimension2D=16384]
  advertised=[externalMemoryFd=true externalSemaphoreFd=true swapchain=true]
```

Intel is the second vendor of the B02 claim: the device advertises the fd extensions and the native path
neither requires nor enables them; `D32_SFLOAT` is candidate 1 of 6 here too; `maxPushConstantsSize=256`
covers the 208-byte immediate block. Identical shape to the AMD line except the name/uuid/type.

## 4. The real driver behind F3 (task-46)

New: `VulkanContext.reportDriverInfo` queries `VkPhysicalDeviceDriverProperties` (core 1.2) and
`VkPhysicalDeviceProperties.driverVersion` from the physical device the context actually created, decodes the
version per vendor, prints `CELTIUM_DRIVER_INFO`, and exposes it as `VulkanContext.driverSummary()` /
`driverName()` / `deviceSummary()`. `VulkanTerrainGpuBackend` carries it as `driverInfo=[…]` in the run's own
report. A value the driver does not fill in reads `unavailable`, never a stub string.

| vendor | measured line (both legs PASSED, `validationClean=true`, 0 VUID) |
|---|---|
| AMD | `driverName="radv" driverID=3(MESA_RADV) driverInfo="Mesa 26.2.4-1" driverVersion=109060100(26.2.4 (mesa vk-version layout)) device="AMD Radeon RX 6400 (RADV NAVI24)" uuid=00000000030000000000000000000000 type=2 apiVersion=1.4.354` |
| Intel | `driverName="Intel open-source Mesa driver" driverID=6(INTEL_OPEN_SOURCE_MESA) driverInfo="Mesa 26.2.4-1" driverVersion=109060100(26.2.4 (mesa vk-version layout)) device="Intel(R) UHD Graphics 630 (CFL GT2)" uuid=8680923e000000000002000000000000 type=1 apiVersion=1.4.354` |

Both are in `rendererDebug` as `driverInfo=[…]` in the same runs.

**Which line is authoritative for "what renders now": `CELTIUM_DRIVER_INFO`** (and its `driverInfo=[…]` copy in
the report), printed from the live `VulkanContext` after the device is chosen. The GL bootstrap probe's
`glRenderer`/`vulkanDevice` fields in `logs/celtium-vulkan-device.json` answer a different, earlier question -
which GL identity exists and what could be shared with it by fd - and the plan already records that a probe
result must not be read as the live renderer's state. `GlDeviceIdentity` (the probe) is untouched by this work.

**The stub string: landed.** `CeltiumGlStub.celtium$nativeRendererName()` returned only
`<device name> via Celtium Vulkan`; with the Lead's approval and weather-water's agreement (that file is
theirs and was hot with task-50) it now returns `VulkanContext.driverSummary() + " via Celtium Vulkan"` when a
Vulkan backend exists and the summary is not `"unavailable"`, and **`"unavailable"`** otherwise - no stub
string presented as a driver, which is the user's own constraint. `GL_VENDOR` is untouched ("Celtium").
Consequence for every later report: `glRendererPreflight` is now the longer driver summary rather than
`<device> via Celtium Vulkan`, from the same source (`GL11.glGetString(GL_RENDERER)` -> the stub);
`--require-gpu AMD` still matches because `gpuRequirementSource` is `nativeDeviceName` and the device name
appears inside the summary.

**The user's own session (item 3).** Their instance log (`…/cleanroom-0.6.13-alpha/minecraft/logs/latest.log`,
18:27) contains `CELTIUM_NATIVE_DEVICE name="AMD Radeon RX 6400 (RADV NAVI24)" … matchedOpenGl=false` and the
`CELTIUM_DEVICE_CONTRACT` line, and contains **zero** occurrences of `Celtium stub GL`. So their session was
**wired and the device was obtained** - not the fallback case. What it never had was the driver identity:
`CELTIUM_DRIVER_INFO` does not appear, because no code queried `VkPhysicalDeviceDriverProperties` before this
change. The instance's jar has since been replaced (10-11 18:29, sha256 `2a3c59fe21ae831b…`), i.e. after
HEAD `77456ccd4`, so the jar that produced that log is not the jar on disk now; the hash of the log's jar is
not recoverable from the log itself.

## What remains unverified on Intel

- **A same-tree upstream reference leg**: two attempts never got the game slot, so §2 compares against the
  stored Intel reference from an earlier fixture build (same scene/pose/tick, same upstream jar).
- **The transitions** (dimension change, world switch), **the panorama copy** (the item the 18:5x commit
  attributes the menu background to) and **the glow/entity-outline chain** on Intel.
- **B05's fog+lighting payload** on Intel (the audit item about the two not co-existing).
- **The wait-failure branches of B04** (no timeout/OOM/UNKNOWN/DEVICE_LOST injected) on either vendor.
- **F3's driver line end to end**: the values are queried, printed and reported, but the F3/`GL_RENDERER`
  string still shows the device name only until the stub's one-function change is landed with the Lead's
  agreement.
- **Resize at sizes other than 1920x1080**, resize during the world (only the menu path was taken), and the
  Intel leg of anything not listed above.

## Process note

The first `driver-legs.sh` run failed for a reason of mine: I omitted `VK_LAYER_PATH`, so the validation layer
the legs ask for was not found, device creation failed at window creation
(`CELTIUM_WINDOW_RENDERER created=false … the layer VK_LAYER_KHRONOS_validation is not installed`) and the
fixture then NPE'd on a null render-view entity (`World.getSkyColorBody(World.java:1608)` ←
`EntityRenderer.func_78466_h:1910`). Fixed and re-run: both legs then PASSED. The same crash shape appears in
other recent run dirs (`compcap-marker`, `p538-glless`, `ww15-gl-terrain`, `ww16-pre-window`), which may have
the same cause in their own scripts - worth checking before it is read as a tree regression.
