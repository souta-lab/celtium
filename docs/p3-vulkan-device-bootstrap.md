# P3 step 1 — Vulkan device bootstrap

The first P3 increment creates a Vulkan logical device for exactly the GPU the game's OpenGL context runs on, on an
explicit command, and then destroys it again. **It is a bootstrap and a contract check, not a renderer**: no
terrain is uploaded, no draw is issued, and the terrain backend keeps using OpenGL.

## What exists

| Component | Responsibility |
| --- | --- |
| `celtium-vulkan` module | Java 8 classes compiled against LWJGL 3.4.1 (`compileOnly`), embedded in the mod jar like `celtium-probe`; LWJGL is never bundled |
| `GlDeviceIdentity` | Reads vendor/renderer/version, driver UUID and every device UUID from the current GL context (`GL_EXT_memory_object`); query only, no state change |
| `VulkanBootstrap` | Creates an instance, matches the physical device by device UUID, verifies the contract, creates one logical device with a graphics/transfer queue, destroys it, and reports JSON |
| `VulkanDeviceReport` | The report: GL identity, required contract, matched device, reasons, warnings |
| `/celtium vulkan` | Runs the bootstrap on the client thread and writes `logs/celtium-vulkan-device.json` |

The entry point is resolved reflectively and only after the explicit command, so an installation without Vulkan
bindings keeps starting and rendering in GL. The report always states `effectiveTerrainBackend: opengl` and
`nativeTerrainImplemented: false`.

Matching rules, all fail-closed:

- A Vulkan physical device is only considered when its `deviceUUID` equals one of the GL device UUIDs. A CPU
  device (`VK_PHYSICAL_DEVICE_TYPE_CPU`) and any device below Vulkan 1.2 are rejected before any contract work.
- No match means no Vulkan work at all: the report names the GL UUIDs it looked for. Nothing falls back to
  another GPU, and a software Vulkan device is never substituted.
- The contract is the one proven in P1b: `VK_KHR_external_memory_fd`, `VK_KHR_external_semaphore_fd`, binary
  semaphores importable *and* exportable through opaque FDs, `R8G8B8A8_UNORM` importable as a colour image,
  `D32_SFLOAT` importable as a depth image, single sample, and a queue family that is both graphics and transfer.
- Every reason is reported even when the device was created, and the logical device is destroyed in the same
  call: this stage leaves no Vulkan object behind.

## Result

Two runs per GPU, same jar (`celtium-forge-mc1.12.2-2.5.12-celtium.0.1.0-dev.1.jar`,
sha256 `ebec8dab2ccaa37a31143c6fd526a0eaff804b315717c3d2ec4ed1800e653071`), one without Vulkan bindings on the
classpath and one with LWJGL `lwjgl-vulkan-3.4.1.jar` added to the test classpath only.

| GPU | Bindings | Bootstrap status | Matched device | UUID match | Driver UUID match | Queue | Semaphores | Color/depth |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| RX 6400 | present | `DEVICE_READY` | `AMD Radeon RX 6400 (RADV NAVI24)`, discrete, Vulkan 1.4.354 | yes | yes | family 0, graphics + transfer | import + export | importable, dedicated allocation required |
| UHD 630 | present | `DEVICE_READY` | `Intel(R) UHD Graphics 630 (CFL GT2)`, integrated, Vulkan 1.4.354 | yes | yes | family 0, graphics + transfer | import + export | importable, no dedicated allocation |
| RX 6400 | absent | `UNSUPPORTED_OR_UNAVAILABLE` | none | – | – | – | – | – |
| UHD 630 | absent | `UNSUPPORTED_OR_UNAVAILABLE` | none | – | – | – | – | – |

Without bindings the recorded reason is
`Vulkan device creation unavailable: NoClassDefFoundError: org/lwjgl/vulkan/VK`, the fixture still reports
`PASSED`, and OpenGL keeps rendering. The loader in both runs is 1.4.363; the in-game LWJGL reports
`3.4.1-snapshot`.

The GL device UUIDs the bootstrap matched against were `00000000030000000000000000000000` (RX 6400, driver UUID
`414d442d4d4553412d44525600000000`) and `8680923e000000000002000000000000` (UHD 630, driver UUID
`bec8ea2bdaf79fc9c52dcd9bb1c7114d`) — the same identities the capability probe reports.

## The bootstrap does not disturb GL rendering

The device is created and destroyed on the client thread between world frames, so the P2 comparison was repeated
against the same upstream reference runs: both bindings runs produced pixel-identical captures
(0 differing pixels of 921600, max channel delta 0), the same `solid: 62` visible sections, the same framebuffer
contract, camera, used memory and buffer count.

- `.celtium-work/p3-bootstrap-parity-amd.json`, `.celtium-work/p3-bootstrap-parity-intel.json`
- `.celtium-work/runtime/p3c-celtium-{amd,intel}-bindings/logs/celtium-vulkan-device.json`
- `.celtium-work/runtime/p3c-celtium-{amd,intel}-nobindings/logs/celtium-vulkan-device.json`

## Reproduce

```sh
./gradlew -p build-1122 :forge122:packageJar :forge122:validationRemapJar --console=plain --no-daemon

# with Vulkan bindings available to the installation (test-only classpath here)
python3 scripts/prism_validation.py "$CELTIUM_INSTANCE" \
  --output .celtium-work/p3c-celtium-amd-bindings-lock.json \
  --game-dir .celtium-work/runtime/p3c-celtium-amd-bindings \
  --mod build-1122/build/libs/2.5.12-celtium.0.1.0-dev.1/celtium-forge-mc1.12.2-2.5.12-celtium.0.1.0-dev.1.jar \
  --mod forge122/build/libs/celtium-validation-client-2.5.12-celtium.0.1.0-dev.1.jar \
  --library .celtium-work/runtime/celtium-headless-interop/test-libraries/lwjgl-vulkan-3.4.1.jar \
  --jvm-arg=-Dceltium.validation.expectVulkan=true \
  --run --validate --duration 150 --headless \
  --render-node /dev/dri/by-path/pci-0000:03:00.0-render --require-gpu 'RX 6400'
```

Omitting `--library` and `--jvm-arg` exercises the missing-bindings path instead. The fixture calls
`/celtium vulkan` only when `-Dceltium.validation=true`, records `vulkanBootstrapStatus`, and requires
`DEVICE_READY` when `-Dceltium.validation.expectVulkan=true`.

## Not established

- No terrain mesh, arena, atlas/lightmap or draw exists on the Vulkan side; this stage creates and destroys a
  device only.
- No performance result of any kind.
- Nothing here proves that a Vulkan terrain draw can reproduce the OpenGL image; that is P3 steps 2–4.
- The bootstrap does not persist a device or queue, so nothing is yet shared between frames, and no image or
  semaphore from P1b is used in-game.
- Only RX 6400 (RADV) and UHD 630 (Intel Vulkan) with Mesa 26.2.4-1 were exercised. NVIDIA is unverified.
- The Vulkan bindings were supplied on the test classpath. A shipping Cleanroom installation must provide a
  matching LWJGL Vulkan module; without it the mod keeps rendering with OpenGL and says why.
