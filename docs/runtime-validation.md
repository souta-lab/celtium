# Reproduce the Linux Cleanroom validation

Use the supplied, locally patched Prism profile with its existing library and asset caches. The validation launcher currently supports Linux x86_64. It reads that source instance, verifies cached library hashes and creates a fresh game directory for each experiment. Existing test directories are rejected. Authentication data and existing worlds are not copied.

Run Gradle on JDK 25 with JDK 21 and JDK 8 available as toolchains:

```sh
./gradlew -p celtium-probe test installDist --rerun-tasks --console=plain --no-daemon
./gradlew -p build-1122 :forge122:packageJar :forge122:validationRemapJar --console=plain --no-daemon
python3 scripts/verify_forge_jar.py build-1122/build/libs/*/*.jar
```

Supply `-Porg.gradle.java.installations.paths=<comma-separated JDK directories>` if local toolchain discovery needs assistance. GitHub Actions supplies the three JDKs through `JAVA_HOME_8_X64`, `JAVA_HOME_21_X64` and `JAVA_HOME_25_X64`. The probe's isolated build can run on JDK 21 directly.

Set the source instance directory to the parent of its `minecraft/` directory:

```sh
CELTIUM_INSTANCE=/path/to/PrismLauncher/instances/cleanroom-0.6.13-alpha
python3 scripts/prism_validation.py "$CELTIUM_INSTANCE" --output local/runtime-lock.json
```

The fixed fixture is a separate test mod. Install it only into the fresh test copy alongside the renderer under test:

```sh
python3 scripts/prism_validation.py "$CELTIUM_INSTANCE" \
  --output local/celtium-validation-lock.json \
  --game-dir local/celtium-validation-game \
  --mod build-1122/build/libs/2.5.12-celtium.0.1.0-dev.1/celtium-forge-mc1.12.2-2.5.12-celtium.0.1.0-dev.1.jar \
  --mod forge122/build/libs/celtium-validation-client-2.5.12-celtium.0.1.0-dev.1.jar \
  --run --validate --duration 90 --headless \
  --render-node /dev/dri/by-path/pci-0000:03:00.0-render --require-gpu 'RX 6400'
```

The agreed development preference is headless execution. `--headless` starts a private Sway/Xwayland compositor with a 1280 × 720 virtual output and an explicit GPU render node. It uses a separate temporary runtime directory, no desktop configuration or input backend, and null OpenAL output. It stops its own compositor after the client exits. Sway, swaymsg and Xwayland must be installed. Select the render node appropriate to the machine; the path above is this PC's RX 6400, not a portable device identifier. A startup failure does not fall back to the visible desktop. `--require-gpu` rejects a passing fixture on the wrong GPU. The client screenshot is saved directly from its FBO, including in headless mode.

The fixture is enabled by `--validate`, which supplies `-Dceltium.validation=true`. It creates a seed-locked flat world, positions the camera, renders 200 client ticks, observes the Minecraft FBO, invokes the available Celtium commands, captures the scene and requests normal shutdown. Its sources are in `validation/client/`; none are embedded in the renderer mod. The same fixture works with the pinned upstream Celeritas jar, where no Celtium command is expected.

The fixture calls `/celtium`, `/celtium probe` and `/celtium vulkan` when the command is registered. Add
`--library .celtium-work/runtime/celtium-headless-interop/test-libraries/lwjgl-vulkan-3.4.1.jar` and
`--jvm-arg=-Dceltium.validation.expectVulkan=true` to require a created Vulkan logical device; without them the
fixture records the missing-bindings diagnostic instead and still has to pass. See
[the P3 bootstrap record](p3-vulkan-device-bootstrap.md).

Inspect these outputs inside the test game directory:

- `logs/celtium-validation.json`: fixture status, actual GPU, framebuffer formats, camera and renderer information, startup frame intervals and command results.
- `logs/celtium-capabilities.json`: optional probe result when the Celtium command is available.
- `logs/celtium-vulkan-device.json`: optional Vulkan device-bootstrap result when the Celtium command is available.
- `screenshots/celtium-validation.png`: captured scene.
- `validation-console.log`: full process log.
- `validation-process.json`: exit status and whether the fixture passed or a time limit stopped the process.
- `validation-command.json`: reproducible offline Java command.
- `headless/session.json`, `headless/sway.log`: virtual display configuration and compositor log when headless execution is requested.

A time limit, missing fixture report, missing PNG, wrong required GPU or failed fixture is not a passing validation. Without headless mode the desktop may choose a different drawable size from the requested window; use the observed size. These startup frame intervals do not establish a performance improvement or broad mod compatibility. Headless tests still consume GPU/CPU resources, so they cannot establish desktop concurrency or presentation behavior. See the [validation record](validation-2026-10-08.md) for the tested scope and next interoperability gate.
