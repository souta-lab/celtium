# The accept path under async-profiler: no single leaf, the matrix bake is the largest (2026-11-04)

Leg `r11-prof` (renderer `047b51c1…` = census build with the census flag off, fixture `22fcf3e0…`, AMD RX 6400,
`celtium-world-accept`, validation/readback OFF, presented dump every 0). async-profiler 4.6 attached at 127 s
(warm window), `-e cpu -i 200us -t`, until the game exited (the leg's hold ended inside the 30 s window, so the
sample also contains the end of the warm window and some teardown). Client thread: 4589 samples.

JFR could not name what the accept path spends; async-profiler sees inlined frames and native code.

| client thread | share |
|---|---|
| `GuiIngame` (HUD, includes its accept work) | 14.5% |
| `submitImmediateBatchBody` (accept) | **13.7%** |
| `flushImmediateWindow` | 12.7% |
| `drawPendingImmediate` | 7.9% |

Inside the accept body (629 samples):

| callee | share of accept |
|---|---|
| `flushImmediateWindow` (a window closed by the offer) | 14.8% |
| `VulkanDynamicMesh.copyVerticesTransformed` (CPU bake of the model view into pre-transformed groups) | **14.6%** |
| self (the body's own code) | 10.7% |
| `closeCurrentImmediateGroup` | 6.0% |
| `refreshRawWrites` -> `applyRawWrites` -> `uploadFromGlTexture` / `hashUploadSlice` | 5.4% |
| `offerStateKey` | 3.5% |
| `VulkanTextureRegistry.get` | 3.3% |
| `prepareImmediate` | 3.2% |
| `CeltiumGlStub.textureLevelExtent` (a `Long2ObjectOpenHashMap` lookup per offer) | 2.4% |

Leaves: the body itself 10.7%, `libvulkan_radeon.so` 9.5% (recording inside the windows the offers close),
`copyVerticesTransformed` 7.2%, `Long2ObjectOpenHashMap.get` 6.0% (`textureLevelExtent`, `textureUploadSerial`,
`textureParameterValue`), array copies 6.4%.

## Reading

- The "unattributed 3135 ns/offer" of round 60 was the timing brackets' own split, not a hidden hot spot: no leaf in
  the accept path is above 15%.
- The two items that do work the frame did not ask for: a raw-write refresh that re-hashes an upload slice inside an
  offer, and per-offer hash lookups of texture facts (`textureLevelExtent`, `textureUploadSerial`) that change only
  on upload. Both are small (~8% of accept together, ~0.03 ms a frame).
- The largest single cost is the matrix bake (~0.05 ms a frame). It is what lets groups merge across model-view
  changes, so removing it trades accept CPU for more groups; it is not free to remove.

Nothing here closes the 0.52 ms gap on its own; the accept path is ~0.33 ms of the frame in total.
