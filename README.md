*Celtium was a historical proposed name for element 72, now known as hafnium. [Name origin](https://en.wiktionary.org/wiki/celtium).*

# Celtium

A Celeritas fork developing native Vulkan terrain rendering for Minecraft 1.12.2 on CleanroomMC, with existing mod compatibility as a primary goal.

**Current stage: the game runs and presents from Vulkan without a GL context.** With `-Dceltium.vulkan.glless=true` the window is created with no OpenGL context at all (`GLFW_NO_API`), the game's own framebuffer is adopted into Vulkan-owned colour and depth images, and the finished frame is presented from the Vulkan swapchain; the GL swap is skipped. Measured on both verified GPUs (RX 6400 and UHD 630): the validation fixture passes with zero validation messages, and terrain, entities, items, particles and the GUI draw through native pipelines. The presented frame was bound to the compositor's output pixel-exactly (0 of 921600 pixels differing) by capturing the private headless Wayland output with `grim` - scanout itself is not measured, because that needs DRM master. The GL bridge (`-Dceltium.vulkan.glless=false`) remains available and is what mods that issue their own GL calls still go through.

**What is not done, so it is not claimed:** text drawn by vanilla's font renderer does not appear under GL-less (its glyphs still go to the stubbed immediate-mode entry points - the glyph capture exists and is accepted, but the image is not verified); the entity-outline glow is incomplete under GL-less; the vanilla post-process chains run natively only where their real input image can be supplied, and are refused (falling back to GL) where it cannot; the bridge's own model draws are not separately instrumented; FBO/post-process ownership, all-block-state coverage, all entity/TESR quality and full existing-mod compatibility are open. See docs/plan.md for the current status table, priorities and acceptance, and docs/history/ for the stage records.

## Approach

- Preserve Celeritas' meshing, compressed vertex formats, visibility work, translucency sorting and existing mod integrations.
- Add a terrain-specific Vulkan backend after proving OpenGL/Vulkan color, depth and synchronization interoperability.
- Keep GUI, entities, TESRs, particles and existing mod OpenGL calls on the current GL context.
- Retain the OpenGL terrain backend and the `celeritas` mod ID. Celtium replaces Celeritas; install one of them.
- Rebrand everything a user sees to Celtium: mod display name, commands, logs, thread names, option text and documentation. The mod ID, mixin config and resource domain stay `celeritas` because existing mods, integrations and resource overrides reference them; `scripts/verify_forge_jar.py` rejects a jar whose user-visible names still say Celeritas.
- Select implementations by capabilities and the current GL GPU's UUID. Multiple GPU support is a core requirement; physical validation targets are RX 6400 and UHD 630. NVIDIA is unverified.
- Use the existing GPU driver. Zink is optional and is not bundled or required.

The current source base is the [GTNewHorizons Celeritas mirror](https://github.com/GTNewHorizons/Celeritas/tree/989ebf2e52ceed052ec112a569e0c1d172095756) at `989ebf2e52ceed052ec112a569e0c1d172095756`. Celeritas' upstream is [embeddedt/celeritas](https://git.taumc.org/embeddedt/celeritas). This reference is not a claim that a particular installed Cleanroom/modpack combination has been tested.

## Capability probe

The isolated Linux CLI queries:

- GL device/driver UUIDs and `GL_EXT_memory_object` / `GL_EXT_semaphore` FD support.
- Vulkan physical devices, same-GPU identity, external-memory and binary-semaphore capabilities.
- Export/import support for specific color/depth formats, image usages, optimal tiling and single-sample images.

It does not create a Vulkan logical device, share an image, replace a Minecraft FBO or enable Vulkan terrain. `CANDIDATE_FOR_INTEROP_TEST` means a small interoperability experiment can be attempted; it is not a compatibility or performance guarantee.

Build with JDK 21 or newer:

```sh
./gradlew -p celtium-probe test installDist
```

Run with the normal Mesa driver:

```sh
celtium-probe/build/install/celtium-probe/bin/celtium-probe --output local/capabilities.json
```

On a Linux hybrid-GPU system, `--egl-device 0` (or another enumerated index) selects an EGL device explicitly. An unavailable index fails instead of falling back to the default GPU. Always verify the reported renderer and matching UUID; indices can change and `DRI_PRIME` may be ignored by a surfaceless EGL implementation.

`--require-compatible` exits with code 2 if the query is unsupported or unavailable. Reports always distinguish unverified depth sharing and unimplemented native terrain.

The CLI has its own LWJGL distribution. `-Pceltium_lwjgl_version=<version>` changes that isolated distribution. Its libraries are not copied into the mod or the user's Minecraft installation.

## Minecraft 1.12.2 build

```sh
./gradlew -p build-1122 :forge122:packageJar
```

Use JDK 25 or newer to run Gradle (required by RetroFuturaGradle 2.0.4), with JDK 21 for mod compilation and JDK 8 for Minecraft preparation. This is a build requirement, not a request to change the game's Java version. The isolated 1.12.2 build reuses the upstream source directories and required build helpers without loading the modern-version Stonecutter plugin. The original multi-version build remains for upstream reference.

The resulting mod is under `build-1122/build/libs/<version>/`. In-game:

**Build and startup status:** Forge packaging and the probe tests pass. Celtium's own modules compile at `--release 21` (class major 65 or lower) and depend on no Java 22+ API; JDK 25 is used to build and to run the game, as noted above. The distributable is checked for its mod ID, manifest, licenses, absence of bundled LWJGL/native libraries and for every class staying at or below the release-21 target (class major 65) - measured on the current jar: 613 classes at major 52 for the Forge-facing code and 86 at major 65 for Celtium's own Vulkan modules, with none from a later release. Cleanroom 0.6.13-alpha with LWJGL 3.4.1 on JDK 25 is the verified combination on RX 6400 and UHD 630; **no other vendor, driver or operating system has been verified.** Vulkan bindings must be on the game's classpath and match that Cleanroom installation's LWJGL version. See [validation](docs/validation-2026-10-08.md), [runtime reproduction](docs/runtime-validation.md) and [the handoff](docs/HANDOFF.md).

- `/celtium` displays the active terrain backend and why it was selected.
- `/celtium probe` queries the existing GL context and writes `logs/celtium-capabilities.json` on the render thread.
- `/celtium vulkan` creates a Vulkan logical device for the same GPU as the GL context, verifies the interop contract and writes `logs/celtium-vulkan-device.json`; it does not start Vulkan terrain rendering.

Vulkan bindings must be available on the game's classpath and match that Cleanroom installation's LWJGL version for an in-game Vulkan query. Missing bindings or an unsupported context produce diagnostics; ordinary startup does not initialize Vulkan. The mod embeds Celtium's own probe and Vulkan bootstrap classes, not another LWJGL runtime.

## Development

See [the design and effort estimate](docs/design.md), [the implementation roadmap](docs/roadmap.md) and [the plan's status table](docs/plan.md), which is the single place the current state, priorities and acceptance are kept.

### Performance

Measured on RX 6400 in one flat scene with pinned controls (frame cap, vsync, render distance, camera pose and world seed read back inside the game at both window ends), 900 frames per configuration: GL-less 3.94 ms p50 (253.8 fps), GL bridge 4.89 ms p50, and the pinned Celeritas reference `989ebf2e` 16.10 ms p50. **That is not a claim that Celtium renders the scene four times faster.** The reference sat on the 60 Hz presentation interval - identical at 64 and 147 visible sections with its GPU 7.5% busy - so it never used its headroom, only Celtium publishes pass timings (there is no GPU-time comparison), and the measurement covers one scene. The numbers and the full list of what they do not show are in [docs/performance-2026-10-11.md](docs/performance-2026-10-11.md).

The separate [interop experiment](docs/interop-validation.md) verifies color, D24/D32 depth conversion, bidirectional occlusion, binary semaphores, recreation and retirement on AMD and Intel, including the existing Cleanroom FBO. It does not enable Vulkan terrain. [The P2 record](docs/p2-backend-boundary.md) documents the extracted terrain GPU boundary, the fail-closed backend selection and the pixel-identical comparison against stock Celeritas on both GPUs. [The P3 bootstrap record](docs/p3-vulkan-device-bootstrap.md) documents creating a Vulkan logical device for the GL GPU's UUID on both targets, with the interop contract verified in-game. Upload, arena and SOLID rendering are next.

The verified Minecraft target is RGBA8 with a D24 depth renderbuffer, no stencil and one sample. RX 6400's D24 external-image query remains unsupported; the tested route imports shared D32 and converts depth on the GPU while preserving the original target IDs. Other formats, stencil, MSAA and broader mod compatibility require separate validation. Interop costs are measured separately from game performance.

## License and attribution

Celtium retains Celeritas' LGPL-3.0 licensing and upstream history. See [COPYING](COPYING), [COPYING.LESSER](COPYING.LESSER), and [the preserved upstream README and credits](docs/upstream-README.md). Existing third-party notices remain in the source tree.
