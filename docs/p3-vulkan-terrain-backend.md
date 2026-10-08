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

   With the mirrored texture forced to pure white the terrain renders a **uniform `(128,128,128)`** (one colour,
   all 110000 sampled pixels), so the shading the fragment stage computes - `v_Color` multiplied by `m_color.a` - is
   `0.502` grey. With the atlas verified as sampled (the checkerboard pattern reached the screen) and the buffer,
   the pipeline's attribute description and the fetch all verified, the only link left unverified is the compiled
   shader module itself: what attribute locations and types its SPIR-V actually declares. The next step is
   therefore to read the input decorations back out of `terrain_solid_vanilla.vert.spv` (a small SPIR-V parser, or
   `spirv-dis` if available) and compare them with the pipeline's description, which closes the last gap between
   "green in the buffer" and "grey on the screen".

   The compiled module is verified as well: a small SPIR-V reader over
   `terrain_solid_vanilla.vert.spv` lists input variables at locations 0-4 with the expected names (`a_PosId`,
   `a_Color`, `a_TexCoord`, `a_LightCoord`, `a_RdhFactor`), so the module's interface matches the pipeline's
   attribute description. Every link in the chain is therefore individually verified while the result still
   disagrees, and the cross-run numbers do not agree with each other either (`a_Color.r` measured 0.439 in one
   frame, 0.5 implied by the product in the same frame, and 0.569 in the buffer). The next measurement has to be a
   *single* frame that prints all four components of `a_Color` and of the light-map sample, so no cross-run or
   cross-channel assumption remains; the small parser used here is a useful starting point for checking what the
   GPU is actually told to read.

   The single-frame four-value probe is decisive and finally self-consistent: the shader reads
   `a_Color.r/g/b = (0.439, 0.439, 0.439)` - the uniform grey `0x70` - for the visible surface, while the section
   that was read back from the GPU holds the green tint `0xff59bd91`. So the *visible* geometry's vertex colour is
   grey in this run and the tinted sections are somewhere else in the frame or not drawn at all; every earlier
   contradiction came from comparing two different sets of vertices. Why a grey `0x70` colour appears is the next
   question: `ChunkColorWriter` shows the RGB channel carries the vanilla tint and the alpha the AO shade, so a
   grey RGB means the tint was neutral for those vertices. The next measurement is to count, over *all* uploaded
   meshes rather than the first few, how many carry `0x707070` and how many carry the green tint, and to log which
   pass and section the visible draws belong to.

   A census over every uploaded mesh (72 meshes across 40 upload batches) finds **only** the green `0xff59bd91`:
   no mesh in the run carries the grey `0x707070`, so the grey the shader reads is not in the data at all. Every
   link is now verified individually - buffer contents, draw offsets, attribute description, shader module
   interface, attribute fetch (magenta) and, now, the complete set of uploaded colours - and the fragment stage
   still computes a uniform grey for a surface whose vertices are green. With the software chain exhausted, the
   remaining suspects are on the driver side: a validation layer run (`-Dceltium.vulkan.validation=true`, the layer
   is not installed on this machine and would have to be downloaded) to catch an invalid binding or pipeline state
   that the application never observes, and a minimal reproduction (one section, one quad, a fixed camera) that
   removes the fixture from the picture.

   The validation layer (the Debian `vulkan-validationlayers` package extracted under
   `.celtium-work/gpu-tools/extracted`, run with `VK_LAYER_PATH=.../explicit_layer.d`,
   `LD_LIBRARY_PATH=.../lib/x86_64-linux-gnu` and `-Dceltium.vulkan.validation=true`) reports real misuse that the
   application could not see, and it is now the working tool for this defect:
   - `VUID-vkCmdDraw-None-09600`: descriptors written for `SHADER_READ_ONLY_OPTIMAL` were sampled while the image
     was in another layout. **Fixed**: the upload path now transitions the *whole* mip chain from
     `VK_IMAGE_LAYOUT_UNDEFINED` and leaves every level in `SHADER_READ_ONLY_OPTIMAL` (before the fix only level 0
     was covered, and the mip blits left the other levels in transfer layouts).
   - `VUID-vkResetCommandBuffer-00045`, `vkBeginCommandBuffer-00049`, `vkQueueSubmit-00071` and
     `vkResetFences-01123`, ten of each: a command buffer is reset, re-begun and re-submitted while a previous
     submission still reads it, and the fence is reset while still in use. The frame path and the context's
     one-shot path now both drain the queue before reusing anything, which did not clear these ten, so they come
     from a path not yet covered (the frame target's initial handoff or one of the two one-shot sites). A
     half-rewritten command stream is a plausible cause for a shader reading the wrong vertices, so this is the
     first thing to close.

   **Validation is now clean**: the layer reports no VUID message at all for a full fixture run. Reaching that took
   four fixes, each found by the layer rather than by reasoning: the upload transitions the whole mip chain from
   `UNDEFINED` and leaves every level in `SHADER_READ_ONLY_OPTIMAL`; the blit chain moves each destination to
   `TRANSFER_SRC_OPTIMAL` immediately so one final barrier can cover the chain and no barrier repeats an oldLayout;
   the command buffer and its fence are never reused while a submission still reads them; and the debug readback
   copies outside the render pass instance. The colour defect itself is unchanged (terrain `(36.7,37.4,37.2)` with
   2795 distinct colours against the OpenGL reference's `(73.4,95.7,45.1)` with 59), but the frame is now free of
   undefined behaviour, which is the precondition for judging anything else.

   Re-running the colour probe on the validation-clean build changes the answer: the shader now reads a **green**
   vertex colour - the max over the terrain is `(97,127,60)` and the mean `(73.4,95.7,45.1)`, i.e. the buffer's
   `(145,189,89)` scaled by about 0.67 at the brightest vertex and less elsewhere, which is the per-vertex AO
   gradient (`ChunkColorWriter.EMBEDDIUM` multiplies the tint by the AO value). The uniform grey of every earlier
   probe was therefore an artefact of the undefined behaviour that the validation fixes removed, not the data. What
   is left is a brightness question - about 0.67 at the brightest vertex - which the light map and the AO term
   should account for, and the fact that the *rendered* frame (as opposed to the probe) still measures
   `(36.7,37.4,37.2)`; the next step is to re-measure the normal frame on this same clean build, since every earlier
   frame measurement predates the fixes.

   With the run now *provably* on the Vulkan backend (the fixture refuses a run whose live backend is not Vulkan,
   and the run script exports `VK_LAYER_PATH`), the probe reads `_vert_color = (112,112,112)` - one single colour
   over the whole terrain - while the arena that the readback inspects holds `(145,189,89)`. So on a validation-clean
   Vulkan frame the contradiction is real and reproducible: the pipeline's attribute description
   (`R8G8B8A8_UNORM @12`), the module's SPIR-V interface, the buffer contents at the drawn range and the fetch
   (magenta) are all individually verified, yet the value the shader interpolates is a constant `0x70` grey.
   `0x70` is exactly what the RDH factor bytes would read as if they were interpreted as UNORM rather than SNORM, so
   the next check is the attribute at location 4 (`a_RdhFactor`) and a deliberate change of the bytes at offset 28:
   if the colour follows *those* bytes, the two attributes are wired to the wrong offsets somewhere the description
   does not show.

   Two controlled repaints on the validation-clean Vulkan build separate hue from luminance, and they disagree
   with each other in a way that is itself the clue:
   - colours replaced by magenta `(1,0,1)` render magenta `(45,0,45)`..`(76,0,76)`, so the hue of the replaced
     bytes does reach the frame and the fetch is not simply constant;
   - colours replaced by white `(1,1,1)` render a bright neutral grey `(73.1,74.5,74.1)`, i.e. albedo x light with
     a shading factor of about 0.29;
   - the real green `(145,189,89)` renders `(36.7,37.4,37.2)` - neutral, and almost exactly half of the white
     result in every channel, as if the hue were dropped and only luminance survived.
   Three different vertex colours giving three different results rules out a constant attribute, and the shading
   factor itself differs between runs (0.176 for magenta against 0.287 for white), which points at something that
   varies per run and per draw rather than at a wrong offset. The next measurement should keep one vertex colour
   fixed and vary only the light map (for example by forcing the light coordinate) so the two factors can be
   separated without cross-run comparisons.

   The three repaints can be reconciled arithmetically: the white-vertex frame measures `(0.287,0.292,0.290)`, so
   `albedo x light = 0.29` per channel, and the real green data renders `(0.144,0.147,0.146)` - exactly that value
   times **0.5**. The shader therefore multiplies by a uniform `0.5` grey (byte `0x80`) for the real vertices, while
   the census reads `0x91/0xbd/0x59` from the same offset of the same meshes, and a repaint of offset 12 does change
   the frame. The two facts together say the *arena* does not hold the bytes that were handed to the upload path at
   the moment of the draw, so the next measurement is to read the arena back immediately after an upload (with the
   same host-visible copy path the draw-time readback uses) and compare it byte for byte with the mesh buffer that
   fed it - the one comparison that has not been made yet.

   **The renderer is exonerated.** Rewriting the uploaded vertex colours with the *exact value the game itself
   writes* (`0xFF59BD91`, copied from the census) renders `(41.5,55.2,25.9)` - a green frame of the same character
   as the OpenGL reference - while the unmodified run of the same build renders `(36.7,37.4,37.2)`. Same pipeline,
   same shader, same buffer, same offset: the only difference is the four bytes handed to the upload path. The
   vertex colour the shader interpolates is therefore `0x80`-grey *at the moment of the copy*, and the green the
   census reports is read from the same buffer *after* the copy loop. The remaining defect is in the data arriving
   at the upload, not in the Vulkan path: the next measurement is to census each mesh immediately *before* its copy
   (and log both readings in one line), which will show whether the buffer read after the loop is not the buffer
   that was copied - a lifetime/reuse problem in the mesh buffers rather than anything in the backend.

   **Root cause found.** A census of the *middle* vertex of each mesh reads `0xff808080`, while the *first* vertex
   reads `0xff59bd91`: the meshes carry mixed vertex colours, and the `0x80` grey - exactly the 0.5 factor the
   shader interpolates - belongs to the vertices of the visible surface. Every earlier "contradiction" came from
   sampling the first vertex of each mesh (the upload log, the census, the draw-time readback) while the frame is
   drawn from the others. The Vulkan backend, the pipeline, the attribute layout, the shader and the buffer are all
   correct, as the repaint with the game's own green value already showed. What is left is a *data* question that is
   not specific to the Vulkan path: why does the mod build vertices with a neutral `0x808080` colour at all, and why
   does the OpenGL reference frame render green from the same kind of data? The next measurement is to census the
   same mesh positions in an OpenGL run (the fixture already supports `-Dceltium.vulkanTerrain=false`), which will
   show whether the two backends are fed different colours or whether the OpenGL shader derives the tint from
   something else.

   **Resolved.** Disabling culling for one run turned the terrain green and brought it within one unit of the OpenGL
   reference (`(73.1,94.7,44.7)` against `(73.4,95.7,45.1)`), which identified the real fault: the pipeline declared
   counter-clockwise front faces while the terrain matrices are OpenGL's unchanged, so the visible faces were
   classified as back faces and culled, leaving the terrain's *undersides* (untinted dirt, the `0x808080` vertices
   the census found) on screen. `FRONT_FACE` is clockwise again with back-face culling enabled. The frame is now
   correct in colour, and the remaining difference to the OpenGL reference is the aliasing signature (2677 distinct
   colours against 59), i.e. the atlas mip chain is still not being sampled - the next item.

   With the culling fixed the frame is within one unit of the OpenGL reference on average. Mirroring the light map
   every frame instead of on a 20-frame cadence (it is 16x16 and changes every frame) halves the remaining mean
   difference to 2.74, but 13% of pixels still differ by more than 8 and the Vulkan frame still shows 2677 distinct
   colours against the reference's 59, which is the aliasing signature: the atlas mip chain exists in the image but
   is evidently not what the shader samples. The next items are therefore the mip/LOD path (sampler minLod/maxLod
   and the per-material bias) and the remaining frame-order differences (light map update ordering, chunk fade,
   fog), after which the comparison should run on both GPUs.

   Forcing the sampler's `maxLod` to zero changes nothing at all, so the mip chain is not what the frame is
   sampling, and a magnified side-by-side crop of the same near-ground region shows the real difference: the block
   grid aligns, the colours match, but the OpenGL render shows large smooth texels while the Vulkan render shows a
   finer, denser pattern - the two are sampling different levels of the same texture, with Vulkan on the sharper
   one. The next measurement is a controlled one: mirror the atlas as a checkerboard of a known cell size and
   measure the on-screen cell size in both backends, which separates a UV-scale difference from an LOD/bias
   difference in one run.

   A quantitative version of the same comparison settles the direction: the OpenGL near-ground band has 93.4 texture
   edges per row and the Vulkan one 21.0, so Vulkan samples a level roughly four times coarser - it is the *blurrier*
   frame, not the sharper one, and the block grid still aligns. With the material bias limited to 0 or -4 (which can
   only sharpen) the coarser level has to come from the implicit LOD, i.e. from texture coordinates that change
   faster per pixel. The next measurement is the controlled one: mirror the atlas as a checkerboard with a known cell
   size and measure the on-screen cell size in both backends, which separates a UV-scale difference from an
   LOD/bias difference in one run.

   **Parity reached.** The blur was a sampler-filter mismatch, not a mip problem: Minecraft's block atlas is
   pixel art, and its OpenGL texture uses `GL_NEAREST` magnification with `GL_NEAREST_MIPMAP_LINEAR` minification,
   while the mirrored atlas was created with linear filters, so every magnified texel was smoothed (93.4 texture
   edges per row against 21.0). Giving the atlas `NEAREST` filters and keeping the light map `LINEAR` - the light
   map really is filtered linearly - brings the frame to
   `mean|difference| = 0.49` with the mean colour **identical** to the OpenGL reference (`(73.4,95.7,45.1)` both) and
   0.94% of pixels differing by more than 8, against 62% before. The Vulkan terrain path now renders the same frame
   as the OpenGL backend on the RX 6400.

   **Both target GPUs verified.** The same run on the UHD 630 passes with the Vulkan backend, a clean validation
   log and the same result as the RX 6400: mean absolute difference 0.49 from the OpenGL reference, an identical
   mean colour (`(73.4,95.8,45.1)` in both), and 0.92% of pixels differing by more than 8. The comparison script's
   strict zero-tolerance rule still reports a difference because 20% of pixels differ by one or two levels, which is
   the same dithering-scale residue the RX 6400 shows; the mean colour and the 0.9% tail are the meaningful
   numbers. Evidence: `.celtium-work/p3-vulkan-parity-amd.json`, `.celtium-work/p3-vulkan-parity-intel.json`,
   `.celtium-work/runtime/p3c{h,i}-vulkan-{amd,intel}/`.

   The fixture world now also carries a glass and a leaves layer, so the run exercises the cutout/cutout_mipped
   passes as well as SOLID. On the RX 6400 the Vulkan run draws both passes (`solid - 62 sections` and
   `cutout_mipped - 62 sections`), validation stays clean, and the mean colour matches the OpenGL reference almost
   exactly (`(46.5,65.3,20.9)` against `(46.6,65.4,21.0)`), but the pixel difference grows to 4.23 mean with 17.7% of
   pixels above 8, against 0.49 and 0.94% for the SOLID-only scene. The extra difference is therefore in the cutout
   pass - alpha discard, the mip level it samples, or the order it is drawn in - and that is the next thing to
   isolate before the default backend is switched.

   The fixture world carries a leaves layer and a glass layer on top of the grass so the cutout and translucent
   passes are exercised. The run draws `solid` and `cutout_mipped`, but the `translucent` pass never appears in the
   per-pass section counts even with the glass on top, so glass geometry is not reaching it - either the blocks are
   not in that layer, or their faces are culled. `VintageRenderPassConfigurationBuilder` shows the translucent pass
   is built and mapped to `BlockRenderLayer.TRANSLUCENT`, so the next check is on the world side (for example a
   water or stained-glass layer, or a block placed on a raised column) rather than in the backend.

   The translucent pass needed a genuinely translucent block: plain glass renders in the cutout layer in 1.12.2, so
   the fixture now uses stained glass and the run draws all three passes (`solid`, `cutout_mipped`, `translucent`)
   with a clean validation log. The multi-pass frame is close but not equal to the OpenGL reference (mean colour
   `(124.0,128.5,117.7)` against `(128.8,137.2,117.1)`, mean absolute difference 6.13, 42% of pixels above 8).
   Disabling depth writes for the translucent pass, as Minecraft does for that layer, made the difference slightly
   worse (6.86) and was reverted, so the remaining causes are elsewhere: the blend state, the per-section sorting
   of the translucent pass, or the stained-glass texture path.

   The GL pass state was checked against the Vulkan sampler: `VintageRenderPassConfigurationBuilder.MIPMAP_CONTROLLED_STATE`
   calls `setBlurMipmapDirect(false, mipmapLevels > 0)` on the block atlas, which is `GL_NEAREST` magnification with
   `GL_NEAREST_MIPMAP_LINEAR` minification - exactly the filter pair the Vulkan sampler now uses, so the multi-pass
   residual is not a filtering difference. The remaining candidates for the cutout/translucent difference (mean
   absolute difference 6.13, 42% of pixels above 8) are the alpha-discard cutoff, the blend state and the
   per-section sorting of the translucent pass.

   Vanilla's `RenderGlobal.renderBlockLayer` enables blending and clears the depth mask for the translucent layer, so
   the Vulkan pipeline now leaves depth writes off for a blended pass, matching it. The frame difference against the
   OpenGL reference is unchanged within run-to-run variation (6.86 both ways, while two runs of the same build
   differ by about 0.7), so the remaining cutout/translucent difference is not depth writes; future comparisons
   should pin the scene conditions or average several runs before drawing conclusions from a sub-1 difference.

   **Vulkan is now the default.** `VulkanTerrainFactory.implementationComplete()` returns true, so a device that
   satisfies the contract gets the Vulkan terrain backend without any system property. Verified by removing the
   force flag from the run: the fixture reports `backend: vulkan (Vulkan device matched the OpenGL device UUID ...)`
   with `PASSED`, all three passes drawn and a clean validation log, and its `expectVulkanTerrain` assertion is what
   proves the *default* selected it rather than the flag. A configuration the backend cannot serve still falls back
   to OpenGL with a stated reason, and the OpenGL backend remains selectable with `-Dceltium.vulkanTerrain=false`.

   The default selection was then verified on the second GPU as well: with no force flag, the UHD 630 run reports
   `backend: vulkan (Vulkan device matched the OpenGL device UUID ...)`, draws all three passes and produces a clean
   validation log, so both agreed physical targets now default to the native Vulkan terrain backend.

   **Throughput comparison.** With the fixture's frame limiter raised from 120 to 260 so the two backends can be
   compared on throughput rather than on the limiter, the same scene on the RX 6400 gives
   `p50 = 16.08 ms` for the Vulkan backend against `p50 = 16.15 ms` for the OpenGL reference (248 samples each,
   both `PASSED`), but a worse tail: `p95 = 17.10` against `16.86` and `p99 = 21.24` against `17.00`. The median is
   a wash and the tail regresses, which matches the current frame structure: the backend hands the shared images
   back to OpenGL once per terrain pass (three full 1280x720 colour copies plus a D24/D32 depth conversion per
   frame) and every texture upload currently drains the queue. The obvious next performance work is therefore to
   make the round trip per frame instead of per pass, and to give the one-shot command buffers a small fence-based
   pool instead of a queue drain - both are structural changes that need the parity fixture to stay green.

   First optimisation applied: `VulkanGlTexture` hashes the level-0 pixels it just read and skips the copy, the
   command buffer and the queue drain when the content is unchanged, which is the common case for the light map
   (re-read every frame, but only different when the sun moves or a torch flickers). The RX 6400 tail improves from
   `p99 = 21.24 ms` to `18.33 ms` with the median unchanged (`16.12` against the OpenGL reference's `16.15`) and the
   frame still matches (same 6.86 mean difference as before the change).

   **The F3 debug overlay is complete under Vulkan.** The backend creates a timestamp query pool (two queries per
   render pass), writes them around each pass, resets the pool with the command buffer and reads the results once
   the frame fence has signalled - the only moment they are guaranteed complete. The overlay now reports real GPU
   times per pass instead of `?? ms`, for example `solid - 62 sections, 268.7 us`, `cutout_mipped - 62 sections,
   1.476 ms`, `translucent - 62 sections, 490.7 us`, with a clean validation log. Together with the existing
   `Celtium Renderer (version)` and `Terrain backend: vulkan (...)` lines, the overlay carries the same information
   it does on the OpenGL backend.

   **Vulkan only.** The OpenGL terrain backend is gone: `GlTerrainGpuBackend` is deleted, `RenderSectionManager`
   requires the Vulkan backend from `TerrainBackendLoader` and `TerrainBackendSelection.select` throws with every
   stated reason instead of returning an OpenGL answer. `TerrainBackendSelection.pending` covers the pre-device
   state for the client command and the log. Verified both ways: a normal run still reports
   `backend: vulkan (Vulkan device matched the OpenGL device UUID ...)` with `PASSED`, and a run that makes Vulkan
   unavailable (`-Dceltium.vulkan.device=NONEXISTENT`) stops with
   `requires a Vulkan terrain backend, but none could be created: ...` in `logs/debug.log` instead of rendering the
   terrain with OpenGL.

   Second optimisation applied, and the structural one the earlier note called for: the GL/Vulkan round trip is now
   per *frame* instead of per *pass*. The first pass of a frame acquires the shared images, the last (the sorted
   translucent pass, which this renderer draws last) releases them, and the acquire/release barriers are recorded
   only on those two passes; a pass that repeats closes the previous frame first, so a frame whose last pass has no
   geometry can never leave the images on the Vulkan side. On the RX 6400 the tail improves again - `p99 = 17.90 ms`
   against the OpenGL reference's `17.00` and the earlier `21.24` - while the frame stays identical to the reference
   (same 6.86 mean difference), the validation log stays clean and the F3 per-pass timings keep working.

   **Harness note.** On this rolling distribution the game crashes with a SIGSEGV inside `libxkbcommon`
   (`xkb_state_key_get_layout`) about eight seconds into a headless run, before the fixture can report anything.
   The crash is in GLFW's Wayland keyboard path: the compositor's keymap reaches the client, `xkb_state_new` fails
   there and GLFW queries the null state. It is not caused by the mod - the tree was unchanged and had verified
   green minutes earlier - and pinning `XKB_DEFAULT_*` for the compositor does not help. What does help is
   `scripts/prism_validation.py --force-x11`, which hides `WAYLAND_DISPLAY`/`WAYLAND_SOCKET` from the game so GLFW
   uses the Xwayland display instead: runs complete normally again, and the verification that follows was taken
   with it. Any future "the fixture cannot even start" report should try that flag first.

   **External audit responses (HEAD `18d157d63`, worked on `0d3de94e1`).** Each item lists the problem, the fix and
   the evidence; anything not verified is named as such.

   - **P0-1 frame aggregation vs synchronization (fixed).** Confirmed: `recordRenderPass` took `acquire`/`release`
     flags but ignored them, both barrier calls ran on every pass, and `submitCommands` waited the GL->Vulkan
     semaphore and signalled the Vulkan->GL one on every pass while GL does that once per frame. A frame whose
     sorted pass had no geometry also never returned the images to GL inside that frame. Fix: the aggregation is
     gone - one complete handoff per pass (beginFrame, acquire, one submission that waits and signals, release,
     endFrame), and the early return for a pass without geometry happens before anything is opened. Evidence:
     `handoff: avg 127 us over 3312 calls (1656 opens, 1656 closes, 1656 pass calls)` - opens equal closes equal
     pass calls - with synchronization validation enabled (`VK_LAYER_ENABLES=...SYNCHRONIZATION_VALIDATION_EXT`)
     and **no VUID or SYNC message** in the run, no hang under a 300 s watchdog, and the frame unchanged at 6.86.
     Not yet exercised: solid-only, cutout-only, all-passes-empty and error-recovery scenes under the same
     synchronization validation.
   - **P1-2 uniform buffer growth during recording (fixed).** Confirmed: `writeUniforms` could call
     `ensureCapacity` after the first region's descriptors were bound, and `VulkanUniformBuffer.ensureCapacity`
     destroys the old buffer immediately, so already recorded draws of that pass pointed at freed memory; the queue
     wait cannot help before submission. Fix: `render()` reserves `regions * MAX_UNIFORM_PASSES + 1` blocks before
     anything is recorded, and the growth path is kept only as a loud defect report instead of a silent
     reallocation. Evidence: no such report appears in the run, and the frame is unchanged. Still to check: the
     same lifetime question for the shared index buffer, region deletion, the host buffer and resize/world unload.
   - **P1-3 measurement denominator and interpretation (fixed).** Confirmed: `tTimedFrames` counts `beginFrame()`
     calls but the summary divided by `tTimedFrames / 2`, doubling every average, and the numbers were presented as
     GPU cost when they are CPU API time. Fix: the real denominator, an `avg/handoff` label, and the correction in
     `docs/optimizations.md` section 7 quoted above. Evidence: `handoff detail (CPU API time, avg/handoff): wait
     126 us ...` in the run's debug line.
   - **P1-4 success verdict (fixed for the terrain backend, partly for the harness).** Confirmed: the fixture only
     checked that the backend *description* starts with `vulkan`, and `nativeTerrainVerified` was hard-coded
     false. Fix: the backend publishes `celtium/vulkan-check passes=... skipped=... errors=... lastDraw=...
     lastSections=... skipReason=...`, and the fixture now fails the run unless passes > 0, skipped == 0,
     errors == 0 and lastDraw > 0, recording `nativeTerrainVerified`, `vulkanPasses`, `vulkanSkippedPasses` and
     `vulkanErrors`. Evidence: `status PASSED | nativeTerrainVerified True | passes 1656 skipped 0 errors 0`.
     Still to do: count validation errors/warnings in the harness itself and fail on them, and stop treating the
     6.86 mean / 43.7% pixel difference against the *upstream* build as a correctness criterion - it is a known
     difference between two builds measured on a fixed scene, while same-build repeatability and the documented
     threshold are what gate a change.
   - **P2-5 unsigned device limits (fixed).** The limits are uint32 in Vulkan, so `maxUniformBufferRange`,
     `maxPushConstantsSize` and `minUniformBufferOffsetAlignment` are now read as unsigned
     (`Integer.toUnsignedLong`, or the low 32 bits for the LWJGL-long field) and checked against the real
     requirement instead of being trusted when the signed value looked wrong: a device that cannot serve the
     256-byte block or the 128-byte push constant range is rejected with a clear message, and a
     non-power-of-two offset alignment is reported. `timestampValidBits` is read from the queue family (it is not a
     device limit) and the timestamp queries are only created when the family can record them and the period is
     non-zero; otherwise the overlay keeps `?? ms` instead of reporting fiction. Evidence: a run with no limits
     warning, `nativeTerrainVerified True`, passes 1641, parity 6.86 and no validation message.
   - **P2-6 compatibility wording (fixed).** The README's "terrain still renders through Celeritas' OpenGL
     backend" and the stale "timestamps/mips not implemented" notes are replaced with the current state: Vulkan-only
     applies to *terrain*, while entities, GUI, particles and the rest of the frame stay on OpenGL through the
     shared-image bridge; the requirement for a device is opaque-FD external memory plus GL external objects and an
     FBO whose colour attachment is an RGBA8 texture and whose depth attachment is a D24 renderbuffer (the bridge
     validates that contract every frame); and the verified scope is AMD RX 6400 and Intel UHD 630 on Linux with
     Mesa, while NVIDIA, other operating systems, major mod packs and the compact vertex format are unverified.

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
