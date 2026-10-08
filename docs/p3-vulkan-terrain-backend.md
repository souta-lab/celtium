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

   Resolved by a module marker: forcing `_vert_color = vec4(0,1,0,1)` inside the stride-32 vertex module turns the
   whole terrain pure green (mean `(0,74.5,0)` over the terrain area, with the albedo and light map multiplying it),
   so that module **is** the vertex stage in use, the vertex-colour path works, and vertex-shader edits do reach the
   GPU. The earlier `gl_Position` probe must therefore have been inserted where it had no effect (an earlier
   assignment in the file), not because the module was unused.
   The remaining question is what `a_Color` actually contains at draw time: the buffer content that was logged is
   the plains green `(145,189,89)`, but the rendered colour behaves like a uniform white/AO-only value, which
   matches a drawn mesh whose colours differ from the logged one. The next step is to log the colour bytes of
   several meshes (not just the first upload) and of the mesh that belongs to a section that is actually visible in
   the captured frame.

   Split test: with `v_Color = _vert_color` (the light map removed from the equation) the terrain is still grey
   (mean `(41.9,42.7,42.5)`, 3106 distinct values from AO), not the green the buffer holds. So `a_Color` at draw time
   is *not* the green `(145,189,89)` that was read out of the upload buffer - the two are different data, which
   means the mesh that is drawn is not the mesh that was logged, or the colour bytes are rewritten between the
   upload and the draw. Next step: log the colour bytes of several meshes (and of a mesh in a section that is
   visible in the captured frame) instead of only the first upload, and compare that against what the shader sees.

   Arena growth is ruled out: starting each region's vertex arena at 8 MiB so it never grows leaves the frame
   identical (mean `(36.7,37.4,37.2)`), and eight logged meshes from different regions all carry the same green
   colour and UV, so the upload content is consistently correct while the shader still reads a grey. The next
   measurement has to look at the GPU side of that gap: read the arena's bytes back for the range the first draw
   uses and compare them with the bytes that were uploaded - that separates "the shader reads the wrong place" from
   "the buffer the draw binds is not the buffer that was written".

   The attribute path is now proven end to end: repainting every uploaded vertex colour to magenta in the upload
   path turns the whole terrain magenta (mean `(73.1,0.0,74.1)`), so the draw really reads the bytes the backend
   writes and the pipeline's attribute layout and the shader's usage of `a_Color` are correct. Combined with the
   logged green bytes, what is left is *which* data the visible meshes carry (the eight logged meshes are the first
   uploads of their batch, not necessarily the visible sections) and what the light map contributes: green x light
   map should tint the frame, yet the frame is grey, and no UNORM light-map value can turn the logged green into a
   grey. Next measurement: give each mesh a distinct colour (or log the colour of a mesh known to be in the visible
   render list) so the visible sections' own colour bytes can be read, and dump the light-map sample the shader
   computes for one of them.

   The drawn mesh itself is green: the backend now keeps the first vertex colour of every section as it uploads it
   and logs the colours of the sections in the first render list, which prints `#28=0xff59bd91` - the same plains
   green as the upload log. So for the visible geometry the shader receives green, the attribute path is proven by
   the magenta repaint, and yet both the split test (`v_Color = _vert_color`, which renders grey) and the `v_Color`
   diagnostic (uniform grey 112) say the interpolated colour is grey. The remaining explanations are outside the
   vertex data: the fragment stage's own arithmetic (`m_color`, the AO term, the RDH correction), the light-map
   sample, or the possibility that the visible surface is not the grass tops the colour was logged for. The next
   measurement should therefore print the fragment stage's intermediate values for one pixel - `m_color` before and
   after the RDH correction, `lightColor`, and the two multipliers - instead of the vertex inputs, which are now
   fully accounted for.

   The fragment intermediates are measured and the fragment stage is exonerated: with `m_color.r`, `m_color.a` and
   `v_Color.b` written into the three colour channels, `m_color.a` reads exactly 1.000 (so the AO multiplier does
   not darken anything) while `m_color.r` and `v_Color.b` are both exactly 0.439 - i.e. `v_Color` is the uniform
   grey `(0.439,0.439,0.439)`. A green `a_Color` `(0.569,0.741,0.349)` multiplied by any UNORM light-map value
   cannot produce that, so *something* in the chain replaces the colour, yet the magenta repaint proved the draw
   reads the bytes the backend writes. The two facts can only be reconciled by looking at what the GPU *holds*:
   the next measurement copies the arena's bytes back to host memory for the range the first draw uses and decodes
   them there.

   The GPU side is now measured directly: a debug readback copies the first 64 bytes of the range the first draw
   uses into host-visible memory, and it returns exactly the uploaded data
   (`[0] pos=(0.0,4.0,0.0) colour=0xff59bd91 uv=(0.125,0.469) light=0xf000fc01`). The buffer, the draw offsets and
   the fetch are therefore all correct, so the search moves to the light-map sample: solving
   `v_Color = a_Color x lightColor` for the measured uniform `(0.439,0.439,0.439)` gives `lightColor =
   (0.77,0.59,1.26)`, which a UNORM sample cannot produce ((0.569,0.741,0.349) x (0.77,0.59,1.26) matches all three
   channels exactly, so the algebra is not the problem - the plausibility of the sample is). The next measurement
   is therefore to output the light-map sample itself (`fragColor = lightColor`) so its true value is known, and to
   check the descriptor and the image view that binding 1 actually holds.

   The light-map sample on its own measures a uniform `(223,223,223)` = 0.8745 grey, not the 1.26-blue the algebra
   had suggested, and `0.439 / 0.8745 = 0.502`: in the diagnostic runs the interpolated vertex colour behaves like a
   uniform 0.502 grey while the GPU readback of the same range shows green `(0.569,0.741,0.349)`. The two
   measurements were taken in different runs with different shader builds, so they cannot be reconciled by
   reasoning alone; the next run must remove that variable by outputting `a_Color.r`, the light-map sample and their
   product **in the same frame**, one per colour channel, so all three numbers come from one build.

   The single-frame measurement is in, and it settles the earlier cross-run confusion: with `a_Color.r`, the
   light-map sample and their product written into one output, the shader reads `a_Color.r = 0.439` (112) while the
   GPU readback of the first draw's range holds `0x91` = 0.569 for the same channel. The light-map sample is
   0.769 (196) there. So the vertex colour the *visible* surface is shaded with is a uniform 0.439 grey - which is
   exactly the `0x70` byte value - while the range that was read back is green. The two are different draws: the
   readback inspects the first section of the first render list, and that is not necessarily the section that fills
   the screen. The next measurement therefore has to read back several draws (and ideally the one covering the
   centre of the screen) instead of only the first, so the colour of the visible mesh is known rather than assumed.

   Reading several draws back shows only one region has geometry in the first pass, and its colour is the green
   `0xff59bd91`. That makes the shader-side measurement (`a_Color.r = 0.439`) and the buffer-side measurement
   disagree about the *same* draw, which no data-flow explanation covers; the remaining suspects are the
   instrumentation itself (the screenshot region assumed to be terrain, the per-pixel averages of a varying that
   may not be uniform) rather than the pipeline. A fresh look should therefore start from a controlled scene - one
   section, one block, a fixed camera - instead of the fixture's full view, so every number can be traced to one
   vertex. Until that is done, the colour defect stays open and the OpenGL backend stays the default.

   The mirrored textures now carry a full mip chain (the image is created with `mipLevels = log2(max(w,h))+1`, the
   view covers every level, and each level is blitted from the one above), and the sampler's `maxLod` follows it -
   the OpenGL atlas is sampled with mips, so this is a real parity gap. It did **not** change the rendered frame at
   all (identical mean and identical 2795 distinct colours), which is itself informative: whatever the visible
   surface samples, flipping the atlas mip chain does not affect it. Together with the earlier vertex-module probes,
   the pattern is that fragment-module edits change the frame while texture and some vertex edits do not, so the
   next step is to log the `VkImageView` handles handed to `bindTextures` for binding 1 and 2 and compare them with
   the handles created for the atlas and the light map - a swap or a stale handle would explain the desaturated
   result and would also survive every edit made to the atlas itself.

   Build trust was checked after the mip chain appeared to do nothing: `recordMipChain` is present in the compiled
   class and the module recompiles on every source change, so the "no effect" result is real, not a stale build
   (one earlier edit - the binding log - had silently not applied, which is worth remembering when a diagnostic
   does not appear). Two calls are confirmed present: `bindTextures` runs once per pass before the render pass and
   `bindDescriptors` runs per draw inside it. The frame being pixel-identical with and without a mip chain, while
   fragment-module edits do change it, points at the sampled image having uniform content: a deliberate test
   pattern uploaded as the atlas (for example a checkerboard) would show immediately whether binding 2 really
   samples the mirrored atlas.

   The checkerboard settled the texture question and produced a sharper symptom. Painting the mirrored atlas as
   8x8 red/blue squares renders the terrain as alternating **blue `(0,0,128)` and black `(0,0,0)`** cells with a
   50/50 split, so binding 2 really does sample the mirrored atlas and the pattern survives to the screen - but only
   the blue channel does: the red squares, which are `(255,0,0)`, come out black. A channel-wise multiply cannot
   move colour between channels, so `m_color.r` and `m_color.g` must both be zero while `m_color.b` is about 0.5.
   That is a much narrower statement than "the colour is grey": the vertex colour the fragment stage applies is
   `(0,0,~0.5)`, i.e. blue-only, while the buffer holds green. The next probe should therefore print `m_color` and
   its two factors in the same frame *with the checkerboard in place*, so the zeroed channels can be attributed to
   either `a_Color` or the light-map sample.

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
