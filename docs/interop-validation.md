# Reproduce the GL/Vulkan roundtrip experiment

> **Stage record, superseded.** This document records what was done during a stage and is kept for evidence.
> The single current plan is [`docs/plan.md`](../plan.md); where this file contradicts it, the plan wins.

`celtium-interop/` is a separate Linux experiment, not the terrain renderer and not part of the shipped Forge jar. It uses the current GL GPU's valid device/driver UUIDs, hardware device type, graphics queue, FD extensions and binary-semaphore properties. It queries each external image using its exact flags and usages; it does not select by a GPU model name.

The verified contract is single-sample RGBA8 color and an existing D24 depth renderbuffer with no stencil. The experiment allocates dedicated, mutable, optimal Vulkan RGBA8 and D32 images with all supported ordinary usages. GL imports their opaque FDs. Color uses GPU blits. Depth uses a same-format D24 blit to an ordinary depth texture, a shader conversion to shared D32 and a shader conversion back to the original D24 renderbuffer. The original FBO and attachment IDs remain intact. This does not assume a cross-format depth blit is legal. Other source formats, stencil and MSAA are not verified by this path and must not be enabled by the terrain backend on this evidence.

Two exported binary semaphores coordinate the GL and Vulkan streams. Vulkan explicitly transfers image ownership to/from `VK_QUEUE_FAMILY_EXTERNAL`, in GENERAL layout; GL signal/wait calls list both textures and their layouts. A GL flush precedes the Vulkan wait. Resource retirement requires both the Vulkan command-slot fences and a GL completion fence. `vkDeviceWaitIdle` is confined to device shutdown; there is no per-pass `glFinish` or device idle.

## Build and isolated execution

Use JDK 21 or newer, a build-time glslangValidator and the Vulkan loader. The default isolated LWJGL version is 3.4.1, matching the supplied Cleanroom modules. Its runtime libraries are confined to the CLI distribution.

```sh
./gradlew -p celtium-interop installDist \
  -Pglslang_validator=/path/to/glslangValidator --console=plain --no-daemon
JAVA_OPTS='--enable-native-access=ALL-UNNAMED -Djava.awt.headless=true' \
  celtium-interop/build/install/celtium-interop/bin/celtium-interop \
  --egl-device 0 --validation --sizes 64x64,1280x720,1920x1080 \
  --cycles 12 --frames 8 --handoffs 4 --measure-frames 120 \
  --screenshots local/interop-screenshots --output local/interop.json
```

`--validation` requires VK_LAYER_KHRONOS_validation and enables synchronization validation; its absence is an error. Set `VK_LAYER_PATH` and `LD_LIBRARY_PATH` if using locally extracted layers. On this development PC those are `.celtium-work/gpu-tools/extracted/usr/share/vulkan/explicit_layer.d` and `.celtium-work/gpu-tools/extracted/usr/lib/x86_64-linux-gnu`. Device indices are enumerations, not persistent IDs. This PC observed RX 6400 at 0 and UHD 630 at 1. Run each and verify the reported renderer/UUIDs. Do not run simultaneous GPU experiments when collecting performance evidence.

The functional oracle verifies GL geometry in front of Vulkan, Vulkan geometry in front of subsequent GL, visible near GL geometry and 15 depth values from zero through one. It deliberately clears the original target before return copies so a missing copy cannot pass. CPU readback is confined to the oracle and PNG capture. Cost samples disable that extra clear and readback; GL timestamps span the copy, Vulkan draw and semaphore sequence, including this harness's error checks and command-slot waits. They are not Minecraft frame-rate measurements. The resource test submits more work after its readbacks, observes pending completion and waits before deletion. FD counts after each cycle help detect FD growth; they are not a general GPU-memory leak proof.

## Existing Minecraft FBO

Build the interop jar first, then include its own classes/shaders in the separate validation mod:

```sh
./gradlew -p build-1122 :forge122:validationRemapJar \
  -Pceltium_validation_interop_jar="$PWD/celtium-interop/build/libs/celtium-interop-0.1.0-dev.1.jar"
python3 scripts/prism_validation.py "$CELTIUM_INSTANCE" \
  --output local/interop-runtime-lock.json --game-dir local/interop-game \
  --mod build-1122/build/libs/2.5.12-celtium.0.1.0-dev.1/celtium-forge-mc1.12.2-2.5.12-celtium.0.1.0-dev.1.jar \
  --mod forge122/build/libs/celtium-validation-client-2.5.12-celtium.0.1.0-dev.1.jar \
  --library celtium-interop/build/install/celtium-interop/lib/lwjgl-vulkan-3.4.1.jar \
  --jvm-arg=-Dceltium.validation.interop=true \
  --jvm-arg=-Dceltium.validation.vulkanLayer=true \
  --jvm-arg=-Djava.awt.headless=true \
  --run --validate --duration 90 --headless \
  --render-node /dev/dri/by-path/pci-0000:03:00.0-render --require-gpu 'RX 6400'
```

Run Gradle on JDK 25 with the JDK 21/8 toolchains, as in [runtime validation](runtime-validation.md). Do not run the two Gradle builds concurrently: they share probe outputs. For UHD 630 on this PC use `/dev/dri/by-path/pci-0000:00:02.0-render` and `--require-gpu 'UHD Graphics 630'`. Keep source profile/core/OpenGL/native jars unchanged. Only the version-matching Vulkan binding is added to the copied test game's classpath, with a recorded hash.

Do not place the interop jar on the application's classpath. Its classes must run in Cleanroom's initialized mod/GL classloader. An earlier misplaced jar resolved an uninitialized native GL dispatch path and aborted the disposable client. Embedding it in the validation mod corrected the loader identity; the entry point also checks loader identity and Java GL capabilities before native calls.

The fixture captures the normal world at tick 200, runs synthetic occlusion/precision tests on the real Minecraft FBO, restores touched GL state, renders another 40 ticks and captures the resumed normal world. Inspect `logs/celtium-interop.json`, `logs/celtium-interop.png`, `logs/celtium-validation.json` and `screenshots/celtium-validation-post-interop.png`. The source instance stays read only. This establishes the tested FBO bridge, not Vulkan terrain, shader-pack compatibility or a performance improvement.
