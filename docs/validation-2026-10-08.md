# Initial validation — 2026-10-08

This checkpoint contains capability diagnostics and fork scaffolding. Terrain rendering remains OpenGL. The Minecraft mod has not successfully built or run yet.

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
