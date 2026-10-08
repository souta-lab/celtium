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

### 6. The GL/Vulkan handoff is measured, and the shared depth format is settled by evidence

*Commit:* this one - the debug line now carries both numbers every second.

The handoff is the one cost that is specific to this design, so it is measured instead of estimated: the backend
times every `beginFrame()` and `endFrame()` call and counts opens, closes and pass calls, reporting
`handoff: avg 168 us over 1096 calls (548 opens, 548 closes, 1644 pass calls), worst 811 us` on the RX 6400.

The counters answer the question the timing alone could not: opens equal closes and there are three pass calls per
open, so the images already change hands exactly **once per frame** - there is no redundant pair to remove, and the
earlier estimate of "4.4 calls per frame" was an arithmetic error from dividing by a different frame count. With
three terrain passes per frame, one pair per frame is the minimum this design can do. The cost that remains is
per-handoff, about 0.34 ms per frame on average with occasional 0.8-1.5 ms outliers, and the depth conversion inside
it cannot be removed on this driver (see below).

An attempt to reduce the pair count by teaching the backend the renderer's frame counter was made, measured and
reverted: it removed the close-and-reopen for a pass object rendered twice in one frame, but the counters showed the
same 1 pair per frame before and after, so the change bought nothing measurable and added a parameter to the backend
interface. The frame-counter plumbing was therefore dropped rather than kept on faith.

The obvious way to remove that cost is to stop converting depth on every handoff, which only exists because the
shared depth image is `VK_FORMAT_D32_SFLOAT` while the game's depth attachment is a D24 renderbuffer. A probe now
asks the device directly whether a shared `D24_UNORM_S8_UINT` depth image is possible:
`shared depth formats: D24S8=query failed (-11) D32F=shareable`. `VK_ERROR_FORMAT_NOT_SUPPORTED` is the answer on the
RX 6400, so the conversion cannot be replaced by a same-format blit on this driver and the two full-screen
conversion passes per handoff stay.

Closing the frame from the renderer's once-per-frame hook (`updateTimings`, called from `runAsyncTasks`) was also
tried and reverted: it submitted a command buffer while the previous submission was still pending, and validation
reported `vkResetCommandBuffer-00045`, `vkBeginCommandBuffer-00049`, `vkQueueSubmit-00071` and
`vkResetFences-01123` before the game crashed. Any future change in this area has to close the frame on the render
thread at a point where the renderer knows the terrain passes are finished.

### 6b. Buffer growth drains the queue before destroying the old buffer

*Commit:* this one - *Measured:* validation went from two
`VUID-vkDestroyBuffer-buffer-00922` messages per run to zero, frame unchanged.

`VulkanBuffer.ensureCapacity` creates a larger buffer, copies the contents across and destroys the old one. Commands
submitted in an earlier frame can still refer to that buffer, so the queue is now drained before the destroy.
Validation reported the alternative as a buffer destroyed while still in use, which is a use-after-free hazard even
when it happens to be harmless on a given driver.

## Planned, with the reason each is not in yet

### Indirect multi-draw per region - investigated, not enabled

Four measured configurations, all on the RX 6400 against the same OpenGL reference (a correct frame is a 6.86 mean
absolute difference):

| configuration | result |
| --- | --- |
| multi-draw with one range shared by all passes | 11.68 mean, 57.1% of pixels above 8 |
| multi-draw with one range per pass | 11.42 mean, 57.1% |
| multi-draw with a per-pass cursor so no region reuses a slot | 11.56 mean, 57.0% |
| one indirect command per call (`drawCount = 1`) | 12.40 mean, 56.9% |
| the same commands read back and issued as `vkCmdDrawIndexed` | **6.86 mean, 43.7% - correct** |

The last row is the important one: the command *content*, the slot arithmetic and the run splitting are right,
because replaying the identical values through direct draws reproduces the reference frame exactly. What differs is
`vkCmdDrawIndexedIndirect` itself. Along the way the investigation also found and fixed a real defect: the
device-limit check rejected pipeline creation ("The device allows only -1 bytes per uniform range") on a device that
handles the 256-byte block, which left the frame with no terrain at all; the check now logs the values and creates
the pipeline on trust.

`VulkanHostBuffer` (host-visible, persistently mapped) was added as part of this work and is kept as the
groundwork. The next step is to isolate the indirect call on its own - a minimal scene with one region and one
command - rather than in the full fixture.

### Indirect multi-draw per region (original plan)

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
