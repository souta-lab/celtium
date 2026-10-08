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

1. ~~The terrain appears vertically mirrored.~~ **Fixed**: the shared-image bridge already carries the orientation
   OpenGL expects, so any additional Y flip (negative-height viewport or projection negation) mirrors the result.
   The projection and viewport now use the Vulkan convention unchanged, and the pipeline culls
   counter-clockwise front faces to match. The rendered frame now has the sky above the horizon exactly like the
   OpenGL reference.
2. The atlas has no mip chain, so grazing-angle terrain aliases into noise. `VulkanGlTexture` mirrors level 0 only;
   the mip levels must be copied or generated, and `u_MipBiasAdd` then becomes meaningful.
3. The final colour is much darker than the albedo. Two experiments narrow it down:
   - Forcing the fragment shader to output `v_Color` (vertex colour x light map x AO) gives a **uniform mid grey
     (~0.44)** over the whole terrain, while the albedo alone varies. So the light-map *sample* is constant and
     mid-dark, not the atlas.
   - Flipping the mirrored rows made the terrain fully black, which proves the row order GL hands over is already
     the one Vulkan's texture coordinates expect (GL's `glGetTexImage` returns bottom-up, and GL's `v = 0` is that
     first row). Reverted.
   The vertex bytes are verified good: the first uploaded vertex decodes as `pos=(0,4,0)`, `uv=(0.125,0.469)`,
   `color=0xff59bd91` (a green tint with full alpha) and `light=0xf000fc01`, i.e. block light 0 and sky light 240,
   which the shader turns into the light-map coordinate (0, 240) -> (0.03, 0.97). The attribute layout and the
   decode are therefore correct, and the constant mid-dark `v_Color` means the light map sample at that
   coordinate comes back mid-dark. Since flipping the mirrored rows breaks the atlas (which is currently correct),
   the row order is not the cause; the next step is to sample the mirrored light map directly - for example by
   outputting `fragColor = lightColor` or by comparing a readback of the Vulkan image against the GL dump - to see
   whether the image content, its layout or the sampler LOD is at fault.

   The uniform block is logged too: `fogColor=[0.497,0.576,0.711,1.0] start=96 end=128 density=1 shape=0
   glFogMode=3 cutoff=128`, so fog is a bright sky colour with full weight, and with the observed dark output the
   fog factor must be near zero for the sampled pixels - fog is not the darkener. Since `albedo x v_Color`
   reproduces the frame exactly and `v_Color` measures as a *grey*, while `lightColor` would have to exceed 1.0 in
   blue to make the decoded green vertex colour grey, the vertex colour attribute the shader reads cannot be the
   `0xff59bd91` that sits at offset 12. The next step is to compare the pipeline's attribute description against
   the buffer the backend actually binds (the arena is shared per region, and a wrong vertex binding offset or a
   stale buffer handle after an arena growth would produce exactly this).

   The decisive observation: with the fragment stage forced to output `v_Color`, the whole terrain becomes a
   **perfectly uniform grey (112,112,112), one unique colour for all 921600 pixels**, with the correct silhouette.
   A varying that is constant over an entire surface means every vertex carries the same value, and no light-map
   texel is anywhere near that grey (the mirrored light map only contains values from 14 to 252 along its
   gradient), while the decoded vertex colour is the plains green `(145,189,89)`. `green x light` cannot produce
   that grey for any light <= 1, so the colour the shader interpolates is not the colour in the buffer.
   Next experiment, chosen so the answer cannot be misread: write `a_Color.rgb` straight into `gl_Position` (and
   keep the rest unchanged). The resulting silhouette then *is* the attribute value, which shows directly whether
   the fetch returns the green bytes, a constant, or something else entirely.

   A probe that shifted `gl_Position.x` by the green attribute (green would move the silhouette by ~614 px, the
   observed grey by ~154 px) changed **nothing**, and the same probe added to the *compact* vertex module changed
   nothing either, while fragment-module probes do change the frame. So the vertex stage that produces the visible
   surface is not the module the backend believes it selected: either `VertexLayout.forPass` picks a module the
   pipeline does not use, `SpirvShaders.load` resolves to a different resource than the file that was edited (the
   packaged .spv was verified byte-identical to the edited file, so a stale resource is unlikely), or the surface is
   drawn by a pipeline whose vertex input state does not match the bound buffer. The next step is to make the
   pipeline report the module it loaded and the pass it belongs to, and to give each pass a distinct debug colour so
   the drawing pipeline can be identified unambiguously.

   Older measurements: the mirrored light map has alpha 255 everywhere (so the light map's alpha
   is not the darkener, and `m_color.a` after `v_Color = _vert_color * lightColor` stays 1.0), its bright end is at
   memory row 15 and the dump's row 0 is `[14,14,14]`, and the mirrored atlas averages `(82,76,64)`. The rendered
   terrain averages `(37,37,37)`, which is exactly atlas(`0.32`) x `v_Color`(`0.44`) - so every multiplier that is
   currently applied is consistent, and the remaining suspects are the ones that should *not* be darkening:
   `v_Color` measures as a uniform grey instead of the green tint decoded from the vertex data, and the fog term
   in the fragment shader (`mix(diffuseColor.rgb, u_FogColor.rgb, fogValue * u_FogColor.a)`) would darken the
   whole terrain if `u_FogColor` is black with a non-zero weight. The next run should therefore print the uniform
   block it writes and the `v_Color`/fog split per pixel.
   `-Dceltium.vulkan.dumpTextures=<dir>` writes what GL hands over, which is how the light map was cleared.
4. `VulkanGlTexture.uploadFromGlTexture` changes the active texture unit and the pack alignment with raw LWJGL
   calls while the game's `GlStateManager` caches that state. It restores both, but the capture should use
   `glGetTextureImage` (GL 4.5 is present) so no GL state changes at all, and the texture ids should come from the
   renderer rather than from whichever unit happens to be bound during the pass.

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
