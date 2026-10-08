*Celtium was a historical proposed name for element 72, now known as hafnium. [Name origin](https://en.wiktionary.org/wiki/celtium).*

# Celtium

A Celeritas fork developing native Vulkan terrain rendering for Minecraft 1.12.2 on CleanroomMC, with existing mod compatibility as a primary goal.

**Current stage: capability diagnostics. Terrain still renders through Celeritas' OpenGL backend.** Native Vulkan terrain rendering and shared-depth interoperability are not implemented yet.

## Approach

- Preserve Celeritas' meshing, compressed vertex formats, visibility work, translucency sorting and existing mod integrations.
- Add a terrain-specific Vulkan backend after proving OpenGL/Vulkan color, depth and synchronization interoperability.
- Keep GUI, entities, TESRs, particles and existing mod OpenGL calls on the current GL context.
- Retain the OpenGL terrain backend and the `celeritas` mod ID. Celtium replaces Celeritas; install one of them.
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

**Build and startup status:** Forge packaging and the 15 probe tests pass. The distributable has been checked for its mod ID, manifest, probe classes, licenses, Java 8 bytecode and absence of bundled LWJGL/native libraries. Stock Celeritas and Celtium both render a fixed flat world in Cleanroom 0.6.13-alpha on RX 6400. The commands below execute on the client thread; a missing Vulkan binding produces a diagnostic report while OpenGL stays active. See [validation](docs/validation-2026-10-08.md), [runtime reproduction](docs/runtime-validation.md) and [the Codex handoff](docs/HANDOFF.md).

- `/celtium` displays the current terrain-backend status.
- `/celtium probe` queries the existing GL context and writes `logs/celtium-capabilities.json` on the render thread.

Vulkan bindings must be available on the game's classpath and match that Cleanroom installation's LWJGL version for an in-game Vulkan query. Missing bindings or an unsupported context produce diagnostics; ordinary startup does not initialize Vulkan. The mod embeds Celtium's own probe classes, not another LWJGL runtime.

## Development

See [the design and effort estimate](docs/design.md) and [the implementation roadmap](docs/roadmap.md).

The next gate is an actual color/depth roundtrip using external memory and binary semaphores, including resource recreation and measured synchronization/copy cost. Larger terrain-backend refactoring follows that gate.

The observed Minecraft target is RGBA8 with a D24 depth renderbuffer, no stencil and one sample. RX 6400's D24 external-image query remains unsupported. A D32 sharing experiment must prove the conversion to and from the existing D24 target and preserve its object identities before it can be used by terrain rendering.

## License and attribution

Celtium retains Celeritas' LGPL-3.0 licensing and upstream history. See [COPYING](COPYING), [COPYING.LESSER](COPYING.LESSER), and [the preserved upstream README and credits](docs/upstream-README.md). Existing third-party notices remain in the source tree.
