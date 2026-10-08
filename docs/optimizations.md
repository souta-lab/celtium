# Celtium Vulkan backend: optimisations

Every change here keeps the rendered frame identical to the OpenGL reference, which the validation fixture checks
on both target GPUs (RX 6400 and UHD 630). Measurements come from the same fixture scene with the frame limiter
raised to 260, so the numbers are throughput, not the limiter.

## Implemented

### 1. One GL/Vulkan round trip per frame, not per pass

*Commit:* `375824512` - *Measured:* p99 21.24 ms -> 17.90 ms (OpenGL reference 17.00 ms), p50 unchanged.

The terrain is drawn by up to three passes in a row (solid, cutout_mipped, translucent). The backend used to hand
the shared colour and depth images back to OpenGL after every pass, which costs a full 1280x720 colour copy plus a
D24/D32 depth conversion each time. Now the first pass of a frame acquires the images, the last pass - the sorted
translucent one, which this renderer draws last - releases them, and the acquire/release barriers are recorded only
on those two passes. A pass that repeats closes the previous frame first, so a frame whose last pass has no
geometry can never leave the images on the Vulkan side.

### 2. Unchanged textures are not re-uploaded

*Commit:* `db11247cd` - *Measured:* p99 21.24 ms -> 18.33 ms (before optimisation 1).

`VulkanGlTexture` hashes the level-0 pixels it has just read from OpenGL and skips the copy, the command buffer
and the queue drain when the content is unchanged. The light map is re-read every frame but only differs when the
sun moves or a torch flickers, and every upload used to drain the queue.

### 3. Correct sampler filters (also a parity fix)

*Commit:* `5f3e35da3` - the block atlas uses `NEAREST` magnification with `NEAREST_MIPMAP_LINEAR` minification
(matching `setBlurMipmapDirect(false, mipmapLevels > 0)` in the render pass state) and the light map stays
`LINEAR`. Linear magnification blurred every magnified texel and made the frame differ from the reference.

### 4. Full mip chain on the mirrored textures

*Commit:* `a1b1c1a` (mip chain) - the atlas and light map are created with `mipLevels = log2(max(w,h)) + 1`, each
level is blitted from the one above and the sampler's `maxLod` follows it. Upload transitions cover the whole chain
and leave every level in `SHADER_READ_ONLY_OPTIMAL`.

### 5. F3 GPU timings (feature, not a speed-up)

*Commit:* `adf7a9d7d` - timestamp queries per pass, read once the frame fence has signalled, so the debug overlay
reports real terrain times under Vulkan instead of `?? ms`.

## Planned, with the reason each is not in yet

### Indirect multi-draw per region

A section is currently one `vkCmdDrawIndexed`; a region's sections could be one `vkCmdDrawIndexedIndirect` because
they share a vertex arena and a region offset, which is the GPU-agnostic half of what Nvidium does with mesh
shaders (indirect multi-draw is core Vulkan and available on both target GPUs; mesh shaders are NVIDIA-only and
must not be required). The first attempt was reverted because the indirect command buffer has to be host-visible
and mapped, and neither `VulkanArena` nor `VulkanBuffer` exposes a mapped address - the next step is a small
host-visible buffer helper (create, bind a host-visible memory type, map) and then one indirect call per run of
sections that share an index buffer.

### GPU-driven culling

The section visibility and sorting work is still done on the CPU. A compute pass writing the indirect command
buffer would move it to the GPU; it needs the indirect buffer above first.

### Region-merged geometry

Sections of one region could be merged into a single vertex range so that a region becomes one draw even without
indirect commands; that trades CPU work in the builder for fewer, larger draws and needs a rebuild policy.
