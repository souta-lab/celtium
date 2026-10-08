# P3 — native Vulkan terrain backend

This stage gives terrain a real Vulkan path: the same build output, the same per-section metadata and the same
culling/sorting results are uploaded into Vulkan arenas and drawn with `vkCmdDrawIndexed` into the game's own
framebuffer, which OpenGL and Vulkan share through opaque-FD external memory. **The OpenGL backend is still the
default**; the Vulkan path is opt-in until every pass has been validated on both GPUs.

## Components

| Component | Responsibility |
| --- | --- |
| `celtium-vulkan` module | Java 8 classes compiled against LWJGL 3.4.1 (`compileOnly`), embedded in the mod jar like `celtium-probe`; LWJGL and joml are never bundled |
| `VulkanContext` | Persistent instance, GL-UUID-matched physical device, logical device, queue, command pool, staging upload helper |
| `VulkanBuffer` / `VulkanArena` | Device-local buffer with a segment allocator that hands out `MeshBufferSegment`s, so the existing per-section metadata and batch caches work unchanged |
| `VulkanGlTexture` | Mirrors a GL texture (block atlas, lightmap) into a Vulkan sampled image; GL textures cannot be exported to Vulkan, so this is a copy, refreshed on a cadence |
| `VulkanTerrainPipeline` | Vertex input for the COMPACT 24-byte vertex, push constants (projection + model view), descriptor set (uniforms, atlas, lightmap), depth test, optional blending |
| `VulkanMultiDrawBatch` | Records the draw commands `BatchAssembler` produces in Vulkan form; installed through the existing `MultiDrawBatch` extension point |
| `VulkanFrameTarget` | GL⇄Vulkan frame bridge: colour and depth images shared through opaque FDs, GL copies in and out, D24↔D32 depth conversion, binary semaphores |
| `VulkanTerrainGpuBackend` | Implements `TerrainGpuBackend`: upload, per-pass draw, region lifetime, memory statistics, teardown |
| `VulkanTerrainFactory` | Reflective selection entry point used by the mod core |

Shaders live in `celtium-vulkan/src/main/shaders` and are compiled to SPIR-V with the pinned `glslangValidator`;
the modules are checked into `resources/.../spirv` so a build needs no shader compiler. One vertex/fragment pair
covers every pass variant: fog mode, fog shape, fragment discard, chunk fade and the material bits are runtime
uniforms, and the material mip bias stays per-vertex exactly as the OpenGL shader computes it.

### What is reused rather than reimplemented

- CPU meshing, compressed vertex formats, the section graph, visibility and translucency sorting.
- `SectionRenderDataStorage` and `SectionRenderDataUnsafe`: the same per-section vertex/index offsets and per-face
  ranges. Only the segment type is now an interface, so a GL arena and a Vulkan arena can both back it.
- `BatchAssembler`: the same culling, run merging and camera-validity caching. The Vulkan backend installs its own
  `MultiDrawBatch` implementation through `BatchAssembler.setBatchFactory` while it is active.
- `RenderRegionManager`: region creation, empty-region reclamation and the per-section storages stay in one place.
- The frame is rendered by one command buffer per terrain pass; the OpenGL context keeps drawing GUI, entities,
  TESRs, particles and everything else.

### First on-GPU results (RX 6400, in-game fixture)

The forced Vulkan path now runs end to end: the device matches the GL GPU, meshes upload, the draw list is built,
the pipeline is created and 62 sections are drawn into the shared image, which is handed back to OpenGL. Three
defects remain and are the next work items:

1. The terrain appears vertically mirrored. The Y flip was moved from a negative-height viewport to the projection
   matrix and the output did not change, so the flip is being applied twice somewhere in the chain
   (`beginFrame`'s blit, the projection, or `endFrame`'s blit back) and must be traced with a fixed test pattern.
2. The terrain is nearly black: the lightmap never uploads. `boundTextureId(1)` returns 0 during the pass, so the
   lightmap is not bound on unit 1 at that moment. The texture must be resolved from the renderer or the texture
   manager instead of from the current GL binding.
3. `VulkanGlTexture.uploadFromGlTexture` and the texture capture change the active texture unit with raw LWJGL
   calls while the game's `GlStateManager` caches that state, which can make later GL binds land on the wrong unit.
   The capture and upload paths must not touch GL state outside the state manager.

`maxUniformBufferRange` also reads back as -1 from `VkPhysicalDeviceProperties` in this runtime while its
neighbours are correct, so the pipeline logs `device limits were not reported` and skips that one check instead of
refusing to draw.

### Known gaps (why OpenGL is still the default)

- Chunk fade-in and per-region age uniforms are not ported. The shader has the switch and it is off, which is the
  OpenGL default look for a build without `CHUNK_FADE_IN_DURATION_MS`.
- The atlas copy has a single mip level, so distant terrain does not have the OpenGL mip chain yet.
- No GPU timestamp queries: the debug overlay shows `?? ms` for terrain passes while Vulkan is active.
- `USE_FOG_POSTMODERN`, `CELERITAS_NO_LIGHTMAP` and `USE_VANILLA_COLOR_FORMAT` variants are not ported. The
  VanillaLike vertex format and shader packs therefore keep OpenGL. The selection refuses those configurations
  instead of rendering them wrong.

## Selection

`VulkanTerrainFactory.implementationComplete()` is the gate. While it returns false, `-Dceltium.vulkanTerrain=auto`
(the default) keeps OpenGL and `-Dceltium.vulkanTerrain=true` forces the Vulkan path for development and
validation. Selection stays fail-closed: a missing module, missing bindings, a device that does not match the GL
UUID, a missing contract feature or a pipeline failure all keep OpenGL and record a reason that `/celtium`, the F3
overlay and the client log show.
