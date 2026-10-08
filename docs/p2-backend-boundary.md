# P2 — terrain GPU boundary and GL parity

This stage extracts the narrow GPU boundary that a Vulkan terrain backend will implement, and proves that routing
the existing OpenGL backend through it does not change what is rendered. **Terrain still draws with OpenGL**; no
Vulkan device, allocation or draw exists in the mod.

## Boundary

| Type | Responsibility |
| --- | --- |
| `impl/render/chunk/backend/TerrainGpuBackend` | Upload, per-pass draw, region update, timing, memory statistics, transfer-queue diagnostics and destruction |
| `impl/render/chunk/backend/GlTerrainGpuBackend` | The retained OpenGL implementation; delegates to `ChunkRenderer` and `RenderRegionManager` unchanged |
| `impl/render/chunk/backend/TerrainBackendSelection` | Which backend was chosen for this device and why; fail-closed |
| `RenderSectionManager` | Meshing, graph updates, sorting, scheduling and section lifecycle; calls the backend for GPU work only |
| `SimpleWorldRenderer#getTerrainBackendDescription` | Reports the live selection to the client layer |
| `forge/TerrainBackendStatus` | Formats the selection for the startup log, F3 overlay and `/celtium` |

Meshing, compressed vertex formats, visibility, sorting, update scheduling and the existing mod hooks are
untouched. `RenderRegion`, its GL arenas and the draw-batch builder stay GL types behind the backend; moving those
ownership rules is P3 scope, together with backend-neutral upload/placement records. The GL `CommandList` is never
handed a Vulkan handle, and no GL state or allocation is added per draw.

`/celtium` now prints the backend that is actually active instead of a fixed string, and the F3 overlay and
startup log do the same. The selection stays `opengl` with the reason `native Vulkan terrain is not implemented in
this build`; a configuration that cannot be proven compatible must keep OpenGL and state why, never switch GPU
silently.

## Selection semantics

`TerrainBackendSelection.select()` is the single decision point. It is deliberately fail-closed:

- `opengl` — retained; the current result for every device.
- `vulkan` — reserved for the native terrain backend; not selectable in this build.
- The reason string is carried with the name so a user, log or test can see why the fallback happened.
- Capability checks (GL device/driver UUID identity, required formats, usages, sample counts, semaphore support)
  become its input when the Vulkan path lands. They are not inferred from a GPU name or from a successful query.

## Parity verification

The fixture in `validation/client` was extended so a stock-Celeritas run and a Celtium run can be compared
exactly, without changing what the renderer does:

1. The HUD is hidden three seconds before the capture, so chat lines, the hotbar and overlays cannot contribute.
2. Every non-player entity and particle is removed from 150 ticks onward, and the capture waits for a frame that
   was drawn with none. Mob spawning is not seed-deterministic; entities and TESRs are outside this comparison
   because they remain on the existing GL path.
3. Vanilla's random torch-flicker term is pinned to zero and the world time is pinned to noon. Vanilla regenerates
   the lightmap every frame with `Math.random()` and the flicker shifts terrain brightness by a few units per run;
   without pinning, two runs of the *same* jar differ as much as the two jars do. The fixture records
   `lightmapFlickerPinned` and refuses to be used as an exact comparison when pinning fails.
4. The report records the observed GL renderer, the framebuffer contract, the camera, the visible section count
   per pass, used geometry memory and the live terrain-backend description.

`scripts/compare_validation.py` compares two game directories. It requires identical status, GL renderer, seed,
render distance, world renderer class, framebuffer contract and camera; identical visible sections per pass;
identical used geometry/index memory and buffer count; zero non-player entities and a pinned flicker in both runs;
and a pixel-exact screenshot. The allocated arena capacity is reported but not compared: it depends on how many
chunks happened to be uploaded together.

### Result

| GPU | Reference | Candidate | Sections | Screenshot | Report |
| --- | --- | --- | --- | --- | --- |
| RX 6400 | `celeritas-forge-mc12.2-2.5.12-upstream.989ebf2e.jar` | Celtium P2 jar | `solid: 62` in both | identical, 0 differing pixels of 921600, max channel delta 0 | `.celtium-work/p2-parity-final-amd.json` |
| UHD 630 | same upstream jar | same Celtium jar | `solid: 62` in both | identical, 0 differing pixels of 921600, max channel delta 0 | `.celtium-work/p2-parity-final-intel.json` |

Both fixtures reported `PASSED`, `lightmapFlickerPinned=true`, `worldTimeAtCapture=6000`,
`nonPlayerEntitiesAtCapture=0`, and the same framebuffer contract (RGBA8 colour, D24 depth renderbuffer, no
stencil, one sample, 1280 × 720). The Celtium runs reported `terrainBackendDescription` =
`opengl (native Vulkan terrain is not implemented in this build)`; the upstream runs have no such accessor.

Artifacts, all JDK 21-compiled and remapped to Forge's SRG names:

| Artifact | SHA-256 |
| --- | --- |
| `celtium-forge-mc1.12.2-2.5.12-celtium.0.1.0-dev.1.jar` | `5251e3a8254052623b1686a35bd90cf0fc14625b3cc1054612864273199a1d6d` |
| `celtium-validation-client-2.5.12-celtium.0.1.0-dev.1.jar` | `f4c859a1d803aa1f61860e767647d15db2e5e62db85cad27c6352af61c61826c` |
| `celeritas-forge-mc12.2-2.5.12-upstream.989ebf2e.jar` | `d12425eaaf184e7925cd41d1deb4f9df69064115c8e78c8fcedfd6ecc63f93e7` |

The mod jar was checked with `scripts/verify_forge_jar.py` (`verified: true`, no errors) after the change from the
`capability-probe` to the `backend-boundary` manifest stage.

### Why the fixture pins the flicker

Before pinning, the same pairs were compared without it. On RX 6400:

| Pair | Differing pixels | Max channel delta |
| --- | --- | --- |
| upstream run A vs upstream run B (same jar) | 0.19 % | 1 |
| Celtium run B vs Celtium run C (same jar, identical render path) | 72.27 % | 11 |
| upstream run B vs Celtium run C | 72.29 % | 11 |

The same-jar pair differing as much as the cross-jar pair shows that the difference came from the run and not from
the renderer: vanilla's `EntityRenderer.updateTorchFlicker()` draws from `Math.random()` every frame and the value
shifts terrain brightness by a few units. Pinning it makes the captures byte-identical, as the result table shows.
Those earlier runs are kept in `.celtium-work/runtime/p2b-*`, `p2c-*` and `p2d-*`.

Run directories: `.celtium-work/runtime/p2f-{upstream,celtium}-{amd,intel}/`. Console logs:
`.celtium-work/p2f-*.log`. The captures are `.celtium-work/runtime/p2f-*/screenshots/celtium-validation.png`.

### Reproduce

```sh
CELTIUM_INSTANCE=/path/to/PrismLauncher/instances/cleanroom-0.6.13-alpha

./gradlew -p build-1122 :forge122:packageJar :forge122:validationRemapJar --console=plain --no-daemon

python3 scripts/prism_validation.py "$CELTIUM_INSTANCE" \
  --output .celtium-work/p2f-celtium-amd-lock.json \
  --game-dir .celtium-work/runtime/p2f-celtium-amd \
  --mod build-1122/build/libs/2.5.12-celtium.0.1.0-dev.1/celtium-forge-mc1.12.2-2.5.12-celtium.0.1.0-dev.1.jar \
  --mod forge122/build/libs/celtium-validation-client-2.5.12-celtium.0.1.0-dev.1.jar \
  --run --validate --duration 150 --headless \
  --render-node /dev/dri/by-path/pci-0000:03:00.0-render --require-gpu 'RX 6400'

python3 scripts/compare_validation.py \
  .celtium-work/runtime/p2f-upstream-amd .celtium-work/runtime/p2f-celtium-amd \
  --reference-label upstream-989ebf2e --candidate-label celtium-P2 \
  --output .celtium-work/p2-parity-final-amd.json
```

The corresponding upstream run uses the pinned jar
`celeritas-forge-mc12.2-2.5.12-upstream.989ebf2e.jar`. Game directories must not already exist.

## Not established by this stage

- No Vulkan terrain rendering, mesh upload, arena, atlas/lightmap handling or draw exists.
- No performance result. These are startup captures of one flat world, not a benchmark; no frame-time claim is
  made and the fixture's interval percentiles are explicitly scoped as non-benchmark data.
- Parity is shown for one flat world, one camera, one render distance, RGBA8/D24 with no stencil and one sample,
  and two GPUs (RX 6400, UHD 630) with one driver each. Other targets, stencil, MSAA, other depth formats and
  broad mod compatibility are not covered.
- Entity, TESR, particle, GUI and other GL rendering are outside the comparison because they stay on the existing
  GL path.
- The comparison proves that this refactor does not change the OpenGL result; it does not prove that a Vulkan
  backend can reproduce it.
