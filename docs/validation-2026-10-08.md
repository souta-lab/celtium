# Initial validation — 2026-10-08

The initial checkpoint below contains capability diagnostics and fork scaffolding. The later handoff follow-up records the successful mod build and runtime checks. Terrain rendering remains OpenGL throughout.

## Isolated probe

Command: `./gradlew -p celtium-probe test installDist --console=plain --no-daemon`.

Result: **BUILD SUCCESSFUL** with Temurin JDK 21.0.12.1. The eligibility suite has **15 tests, zero failures/errors/skips**. The main and test classes compile with Java 8 release compatibility. This is not a Minecraft integration test.

Environment: Debian sid, Linux 7.2.9-x64v3-xanmod1, Mesa 26.2.4-1, LWJGL 3.4.3+4, Vulkan loader 1.4.363. The isolated CLI creates its own headless EGL context; it does not modify a Minecraft installation.

| CLI case | Actual GL/Vulkan selection | Result with `--require-compatible` |
| --- | --- | --- |
| `--egl-device 0` | RX 6400: radeonsi / RADV NAVI24; device and driver UUIDs match | Exit 2: required `D24_UNORM_S8_UINT` external image format unsupported |
| `--egl-device 1` | Intel UHD 630: Mesa GL / Vulkan; device and driver UUIDs match | Exit 0: candidate for a future interoperability experiment |
| `--egl-device 2` | llvmpipe software GL/Vulkan | Exit 2: software target and incomplete FD/semaphore capabilities |
| `--egl-device 99` | Unavailable index | Exit 2; does not fall back to a default GPU |
| Missing Vulkan loader library | Real GL context; deliberately unavailable Vulkan library | Exit 2; reason preserved in JSON |
| `LIBGL_ALWAYS_SOFTWARE=1` | llvmpipe | Exit 2 |

EGL device indices are observations on this machine, not stable GPU identifiers.

On RX 6400, the query for optimal 2D, opaque-FD, single-sample images with attachment and transfer-source/destination usages reports:

- `R8G8B8A8_UNORM` and `R8G8B8A8_SRGB`: import/export supported, dedicated allocation required.
- `X8_D24_UNORM_PACK32` and `D24_UNORM_S8_UINT`: `VK_ERROR_FORMAT_NOT_SUPPORTED` (-11).
- `D32_SFLOAT` and `D32_SFLOAT_S8_UINT`: import/export supported, dedicated allocation required.

These results do not establish the format of the actual Minecraft FBO. D32 availability does not establish a valid D24-to-D32 depth blit or safe FBO replacement. No Vulkan logical device, external image import, GL/Vulkan semaphore exchange, depth roundtrip, terrain draw or performance benchmark was performed. All JSON reports retain `nativeTerrainImplemented=false` and `depthRoundtripVerified=false`.

## Minecraft build blockers

1. The unmodified upstream build failed to resolve Stonecutter `0.10-alpha.9`; `maven.kikugie.dev` could not be reached from either execution environment.
2. `build-1122/` was introduced to load only `common`, `forge122` and `celtium-probe`, directly compiling the two required upstream build helpers. Those helpers compiled successfully.
3. The isolated mod build stopped during project configuration: the included `celeritas-mdg-plugin` requires `net.fabricmc:tiny-remapper:0.11.0`, but the consuming plugin classpath searched only Gradle Plugin Portal and Maven Central. The next investigation is the plugin-management repository configuration for Fabric and other included-plugin dependencies.

No successful Forge jar or runtime result exists at this checkpoint. The client command's compilation, packaging, lazy binding failure behavior and in-game execution still need verification.

## Local evidence on the development PC

All paths below are relative to `/home/s0u7a/projects/celtium/` and excluded from git:

- `.celtium-work/baseline-build.log`: original upstream build failure.
- `.celtium-work/probe-build.log`: successful probe build.
- `.celtium-work/celtium-build.log`: isolated Minecraft build failure and stacktrace.
- `.celtium-work/egl-device-{0,1,2,99}.json`: explicit-device capability reports.
- `.celtium-work/missing-loader.json`, `.celtium-work/software-gl.json`: failure-path reports.
- `.celtium-work/hardware-test-summary.json`: six hardware/CLI cases.

An earlier file called `rx6400-capabilities.json` actually selected Intel via `DRI_PRIME=1`; use the explicitly selected device reports above for attribution.

## Handoff follow-up: Forge build and jar verification

The consuming plugin classpath now includes Fabric Maven and NeoForged Maven. The remapper dependency resolves. Further failures exposed by that fix were addressed:

- RFG 2.0.4's plugin class uses class version 69. Gradle must run on JDK 25; JDK 21 remains the mod compiler and JDK 8 prepares Minecraft. CI now supplies all three. The settings script reports the JDK requirement before loading RFG. The upstream [RFG 2.0.0 release](https://github.com/GTNewHorizons/RetroFuturaGradle/releases/tag/2.0.0) also documents this requirement.
- A dedicated consumable probe-jar configuration removes ambiguous classes/resources variants and excludes the CLI's runtime dependencies from the mod.
- Embedded jars use a dedicated configuration so Shadow does not add nonexistent external jar names to `Class-Path`. Java module descriptors are excluded from the Java 8 distributable.
- The command uses the MCP 39 field `Minecraft.gameDir`.
- Both binding adapters' compile classpaths are available to the common bytecode downgrader's hierarchy analysis. Its earlier missing-LWJGL-type errors disappear; neither binding runtime is embedded.
- The jar preserves both LGPL files, the project README, upstream credits and the bitraster and JOML license texts.

Command (JDK paths supplied through `org.gradle.java.installations.paths` on this PC):

```sh
./gradlew -p build-1122 :forge122:packageJar --console=plain --stacktrace --no-daemon
python3 scripts/verify_forge_jar.py build-1122/build/libs/*/*.jar
```

Result: **BUILD SUCCESSFUL**, remapping and packaging complete. The verifier passes: mod ID `celeritas`, expected core-plugin manifest, all own probe classes, matching notices, no LWJGL/Gson/native/nested-jar payload, no validation-fixture payload, no duplicate entries, and **699 classes with maximum class version 52**. The published output directory is `build-1122/build/libs/2.5.12-celtium.0.1.0-dev.1/`. Checksums are stored in `.celtium-work/forge-jar-verification.json`; edits to the embedded README change the jar's checksum.

The remapper warns about the upstream mixin's already-SRG-named `RenderItem.func_175032_a` alias. The same warning occurs when building the fixed upstream revision. Both resulting jars start and render the validation world.

Probe command repeated with `--rerun-tasks`: **15 tests, zero failures/errors/skips**. A Java 8 process with the packaged mod, Gson and no LWJGL classes also successfully invokes the optional entry point and receives `UNSUPPORTED_OR_UNAVAILABLE` with both missing-binding reasons and the OpenGL backend flags.

## Handoff follow-up: supplied Cleanroom runtime

The user supplied a fresh Prism instance during this session. Its source instance is read only; all test worlds, logs and mod replacements are in `.celtium-work/runtime/`. `scripts/prism_validation.py` checks the cached library SHA-1 values against the profile and records SHA-256 values for **100 library jars** and the installed mod jars. It uses an offline test identity and does not read authentication data.

| Component | Fixed version |
| --- | --- |
| Minecraft | 1.12.2 |
| Cleanroom | 0.6.13-alpha |
| Java | Debian OpenJDK 25.0.5-ea+7-1-Debian |
| LWJGL core | 3.4.1-unsafe (reports 3.4.1-snapshot) |
| LWJGL modules | 3.4.1 |
| Fugue | 0.24.4 |
| Scalar | 3.9.0 |
| Celeritas baseline source | 989ebf2e52ceed052ec112a569e0c1d172095756 |
| GL device / driver | RX 6400 / radeonsi, Mesa 26.2.4-1 |

The fixed upstream is built in a detached worktree using the isolated 1.12.2 build wrapper. No upstream Java renderer source is changed. A separate, explicitly enabled validation mod creates a disposable flat world with seed `12345678`, render distance 8, captures a screenshot and observes the existing FBO using GL 4.5 named queries. This test mod is excluded from the distributable.

Both upstream and Celtium runs pass and exit normally. The initial runs each render over 550 world frames and reach 200 client ticks. Celtium's `/celtium` and `/celtium probe` both return 1 through the real client command handler. The latter writes a report on the client thread and retains `effectiveTerrainBackend=opengl`, `nativeTerrainImplemented=false`, `depthRoundtripVerified=false`.

The `upstream-world-final` and `celtium-world-final` reruns additionally require an active `CeleritasWorldRenderer` and record its debug strings and the camera. Both pass with camera `(0.5, 7.809486741892088, 0.5)`, yaw 180°, pitch 20°. Drawable width varies under desktop tiling; these runs are not used for image equality or performance comparisons.

The supplied profile has **no Vulkan binding jar**. The in-game report preserves `NoClassDefFoundError: org/lwjgl/vulkan/VK` and returns `UNSUPPORTED_OR_UNAVAILABLE`. World rendering continues and the test exits normally. This checks the missing-binding path; it does not constitute an in-game Vulkan query.

Observed during the world render event in both runs:

| Property | Observation |
| --- | --- |
| Active Minecraft draw/read FBO | 1 / 1 |
| Actual drawable size | 944 × 1018 (desktop tiling overrides requested window size) |
| Color | GL_RGBA8 (`0x8058`), 8 bits per channel |
| Depth | GL_DEPTH_COMPONENT24 (`0x81a6`), renderbuffer, 24 bits |
| Stencil | 0 bits |
| Samples | 1 |

Screenshots show the flat terrain, sky, hand and GUI. The Celtium capture additionally contains the diagnostic chat messages. This is a startup/scene check, not a pixel-identical regression suite. Frame intervals are recorded with a configured 120 FPS limit and desktop presentation constraints; they are not evidence of a performance improvement. Both runs have a nonfatal missing narrator (`fliteWrapper`) warning.

Local evidence:

- `.celtium-work/forge-build-resolve.log`, `forge-build-jdk25.log`, `forge-build-embed.log`: successive dependency, toolchain and command-compile failures.
- `.celtium-work/forge-build-verified.log`, `forge-jar-verification.json`: successful build and distributable inspection.
- `.celtium-work/probe-build-handoff.log`, `optional-binding-smoke.log`: repeated eligibility tests and Java 8 optional-entry smoke check.
- `.celtium-work/upstream-baseline-build.log`, `upstream-baseline/`: fixed upstream build and isolated worktree.
- `.celtium-work/runtime-lock.json`, `upstream-world-lock.json`, `celtium-world-lock.json`: source/runtime artifact hashes.
- `.celtium-work/runtime/{upstream-world,celtium-world}/logs/celtium-validation.json`: structured fixture results.
- `.celtium-work/runtime/celtium-world/logs/celtium-capabilities.json`: actual-context missing-binding result.
- `.celtium-work/runtime/{upstream-world,celtium-world}/screenshots/celtium-validation.png`: scene captures.

Reproduction: [runtime validation guide](runtime-validation.md). The next gate is external color/D32 sharing, conversion to/from the observed D24 renderbuffer, bidirectional occlusion, binary-semaphore ownership/layout transitions and measured recreation/copy cost on RX 6400.

## Headless development execution

At the user's request, validation now uses an independent Sway/Xwayland headless session. It is explicitly pinned to the RX 6400 render node. The Celtium run in `.celtium-work/runtime/celtium-headless-2/` passes and exits 0 after 200 world ticks and 582 world frames. The actual GL renderer is RX 6400, the active renderer is `CeleritasWorldRenderer`, both commands succeed, and a visually inspected 1280 × 720 PNG contains terrain, sky, hand, GUI and the command messages. The observed attachments remain RGBA8 color, D24 depth renderbuffer, zero stencil bits and one sample. No desktop window or sound output is needed. The owned Sway/Xwayland processes exit after the run.

The first headless attempt failed before launching Java because the compositor's Unix socket path exceeded its length limit. The launcher now owns a short, private temporary runtime directory and does not fall back to the desktop. Headless execution preserves this test's hardware-rendering and screenshot coverage; these startup intervals remain insufficient for a performance claim.

The pinned upstream jar also passes the same headless fixture (`.celtium-work/runtime/upstream-headless/`), on RX 6400 with a saved and visually inspected 1280 × 720 PNG. Both runs use the recorded camera, normal process exit and the same observed attachment formats. Their lock JSON, virtual display/session logs and client outputs provide reproduction evidence.

## Actual interoperability: RX 6400 and UHD 630

The user made capability-based, multiple GPU support a core requirement and selected the available RX 6400 and UHD 630 as physical validation targets. NVIDIA hardware is unavailable and remains unverified. The new separate `celtium-interop/` experiment selects the current GL GPU by valid device/driver UUIDs and exact external-image/semaphore properties, without a model-name whitelist. Terrain remains OpenGL.

On **both GPUs**, actual dedicated Vulkan RGBA8/D32 memory exports and GL imports succeed. The exact query includes mutable image flags and all supported ordinary usages (color 159, depth 167), rather than reusing the earlier narrower capability query. D24 depth is copied to an ordinary D24 texture, converted by a GL shader into the shared D32 target and converted back into the original D24 renderbuffer. Color copies use GPU blits. GL/Vulkan binary semaphores explicitly cover both textures and GENERAL layouts; Vulkan releases/acquires external queue-family ownership. No per-pass device idle or GL finish is used.

The functional oracle verifies GL foreground occludes Vulkan, returned Vulkan depth occludes farther GL, nearer GL remains visible and 15 depth values from zero through one return within two D24 steps. Original contents are deliberately cleared before return copies to reject no-op paths. CPU readback is limited to verification and screenshots.

| Physical target | Standalone stress | Existing Cleanroom FBO | Validation errors / warnings | FD count after each cycle |
| --- | --- | --- | --- | --- |
| RX 6400, radeonsi / RADV | 12 cycles across 64², 720p, 1080p; 8 functional frames and 4 handoffs/frame | Pass, original IDs preserved | 0 / 0 | 26 throughout |
| UHD 630, Mesa GL / Intel Vulkan | Same | Pass, original IDs preserved | 0 / 0 | 34 throughout |

After the verification readbacks, each cycle submits eight additional handoffs and queries a GL completion fence without waiting. It is initially unsignaled in all 24 cycles. Vulkan slots are also observed pending in some cycles. Both APIs complete before GL objects, Vk images/memory and semaphores are deleted. This verifies retirement with outstanding work and stable FD counts; it does not prove the absence of all GPU-memory leaks.

Earlier sequential samples with 4 handoffs/frame and 120 measured frames after 32 warmup frames show substantial overhead: RX 6400 spans approximately 1.2–1.5 ms at 720p and 2.5 ms at 1080p, versus roughly 8.8–9.6 ms and 20.7–21 ms for UHD 630. These GL timestamp spans include the test's copies, triangle, semaphores, error queries and command-slot waits. They are not raw transfer times or Minecraft FPS, and are not evidence of a performance improvement. Later parallel stress runs establish functional stability, not uncontended performance. Intel cost requires attention during later batching/copy optimization.

The separate in-game fixture uses the existing **Minecraft FBO**, with RGBA8 color and D24 depth renderbuffer, zero stencil bits and one sample. It adds only Vulkan bindings 3.4.1 to the disposable test classpath. Its own proof classes/shaders are embedded in the validation mod so they share Cleanroom's initialized GL/mod classloader. A first attempt placed the proof jar on the application classpath and aborted on an uninitialized GL native dispatch. Corrected runs pass on both GPUs; loader identity and Java GL capabilities are now checked before native calls. Source/core/OpenGL/native jars remain unchanged.

The stronger fixture also resumes normal world rendering for 40 ticks after the proof, checks the original target IDs and GL error state, then captures the resumed terrain/hand/GUI. RX 6400 passes with 108 subsequent world frames; UHD 630 passes with 105. Both recovery PNGs were visually inspected, and both clients exit normally with no validation errors/warnings. Unsupported source formats, stencil attachments, MSAA, shader packs, actual terrain draws and broad mod compatibility are outside this proof.

Local evidence:

- `.celtium-work/interop-image-import.json`, `interop-roundtrip-720p.json`: initial actual imports and occlusion.
- `.celtium-work/interop-stress.json`, `interop-stress-intel.json`: earlier sequential six-cycle functional/cost runs.
- `.celtium-work/interop-stress-{amd,intel}-final.json`: twelve-cycle resize/precision/retirement tests per GPU.
- `.celtium-work/runtime/celtium-headless-interop-2/` and `celtium-headless-interop-intel/`: existing-FBO success with command query and synthetic proof.
- `.celtium-work/runtime/celtium-interop-{amd,intel}-post/`: stronger fixture, interop JSON/PNG, normal-world recovery PNG, process outcome and locks.
- `.celtium-work/interop-unavailable-device.json`: invalid EGL index fails without a default GPU fallback.

Reproduce with [the interop guide](interop-validation.md). `celtium-probe` reports remain capability-only, and the shipping Forge jar does not contain the proof or enable native terrain.

## P2 follow-up: terrain GPU boundary and GL parity

The terrain draw/upload/lifetime path now goes through `impl/render/chunk/backend/TerrainGpuBackend`, implemented
by the retained `GlTerrainGpuBackend`. Meshing, graph updates, visibility, sorting, scheduling and the existing mod
hooks are unchanged. `/celtium`, the F3 overlay and the startup log report the backend that is actually active and
the reason for the selection; the selection is fail-closed and currently always returns `opengl`.

The validation fixture now hides the HUD before the capture, removes non-player entities and particles, pins
vanilla's random lightmap flicker and pins the world time, and records the live backend description. With those
controls, a stock Celeritas run and a Celtium run of the same flat world produced **byte-identical** 1280 × 720
captures (0 differing pixels) on RX 6400 and on UHD 630, with identical framebuffer contract, camera, `solid: 62`
visible sections, used geometry memory and buffer count. Without the flicker pin, two runs of the *same* jar
differed as much as the two jars did (72 % of pixels, max channel delta 11 on RX 6400), which is why the pin
exists.

Full method, artifacts and limitations: [the P2 record](p2-backend-boundary.md). Evidence:
`.celtium-work/p2-parity-final-{amd,intel}.json`, `.celtium-work/runtime/p2f-*/`, `.celtium-work/p2f-*.log`,
`.celtium-work/p2-forge-jar-verification.json`. The probe suite still passes 15/15 tests with
`--rerun-tasks`. This stage adds no Vulkan device, allocation, draw or performance result.

## P3 step 1 follow-up: Vulkan device bootstrap

`/celtium vulkan` now creates a Vulkan logical device for the GPU the GL context runs on, verifies the P1b interop
contract against it, reports JSON and destroys the device again. With LWJGL `lwjgl-vulkan-3.4.1.jar` on the test
classpath both targets report `DEVICE_READY`: RX 6400 matches `RADV NAVI24` and UHD 630 matches `Intel(R) UHD
Graphics 630 (CFL GT2)`, both with matching device *and* driver UUIDs, a graphics+transfer queue, opaque-FD
semaphore import and export, and importable `R8G8B8A8_UNORM` / `D32_SFLOAT` images. Without bindings the same
command reports `NoClassDefFoundError: org/lwjgl/vulkan/VK` and OpenGL keeps rendering.

The device is created and destroyed on the client thread between world frames, and the P2 comparison was repeated
afterwards: the captures are still pixel-identical to the upstream reference on both GPUs (0 differing pixels of
921600), with the same visible sections, framebuffer contract, camera, used memory and buffer count. No Vulkan
terrain draw exists and no performance claim is made. Method, artifacts and limitations:
[the P3 bootstrap record](p3-vulkan-device-bootstrap.md). Evidence:
`.celtium-work/runtime/p3c-celtium-{amd,intel}-bindings/`,
`.celtium-work/runtime/p3c-celtium-{amd,intel}-nobindings/`,
`.celtium-work/p3-bootstrap-parity-{amd,intel}.json`.


## GitHub Actions status

Push `0e6fcc69b` triggered [run 37780359487](https://github.com/souta-lab/celtium/actions/runs/37780359487). GitHub rejected both jobs before any step because of the account's recent payment/spending-limit state. No CI build log exists. Local builds, packaging verification and hardware tests above are the evidence; CI is not reported as passing.
