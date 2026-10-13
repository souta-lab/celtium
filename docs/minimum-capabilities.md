# Celtium's minimum Vulkan capabilities

What a device must expose for the GL-less path to run, taken from the code that asks for it rather than from
what a typical GPU happens to support. Every line names the file that depends on it; a claim without a
consumer in the tree does not belong here.

## Extensions the context requires

| Extension | Why | Where |
|---|---|---|
| `VK_KHR_swapchain` | the finished frame is presented from the Vulkan swapchain | `VulkanContext` (`VK_KHR_SWAPCHAIN_EXTENSION_NAME`) |
| `VK_KHR_external_memory_fd` / `VK_KHR_external_semaphore_fd` | the adopted framebuffer and its semaphores cross the GL/VK boundary by file descriptor | `VulkanContext` (`VK_KHR_EXTERNAL_MEMORY_FD_EXTENSION_NAME`, `VK_KHR_EXTERNAL_SEMAPHORE_FD_EXTENSION_NAME`) |

`VK_EXT_debug_utils` is enabled when the validation layer is requested and is **not** required: it is
optional instrumentation, and the renderer runs without it.

## Device features

| Feature | Why | Verified by |
|---|---|---|
| `depthClamp` | the vanilla depth-clamp state (`GL_DEPTH_CLAMP`) has a direct Vulkan equivalent | one file names it |

This table is short on purpose. `samplerAnisotropy` and `independentBlend` were written here first and then
**removed after a grep found no consumer in the tree** - a capability list that names features the renderer
never asks for is the same unsupported claim as a performance number with no measurement behind it.

## Formats

| Format | Use |
|---|---|
| `VK_FORMAT_B8G8R8A8_UNORM` / `VK_FORMAT_R8G8B8A8_UNORM` | swapchain colour, framebuffer colour, texture uploads; both are queried and the first supported is chosen |
| `VK_FORMAT_D32_SFLOAT` (fallback `D24_UNORM_S8_UINT`) | depth attachment |
| `VK_FORMAT_R8G8B8A8_UNORM` | vanilla texture uploads |

## Limits the renderer relies on

These are the spec's own Vulkan 1.0 required minimums, so a conformant 1.0 device already meets them: this
table exists so a support request can be answered without reading the spec.

| Limit | Required minimum | Used for |
|---|---|---|
| `maxImageDimension2D` | 4096 | the framebuffer and the largest vanilla texture atlas |
| `maxColorAttachments` | 4 | the multi-attachment post-process targets |
| `maxUniformBufferRange` | 16384 | the per-frame uniform block |
| `maxPushConstantsSize` (queried as the push-constant limit) | 128 | the per-draw push constant block |
| `maxPerStageDescriptorSamplers` | 16 | texture units |
| `bufferImageGranularity` | at most 131072 | suballocation alignment for buffer/image aliasing |

## What is deliberately not required

- **No mesh shader.** The goal's own rule: mesh shaders must not be a requirement. Terrain, entities and the
  UI draw with the classic vertex/fragment pipeline.
- **No `VK_EXT_attachment_feedback_loop_layout`.** Where an image is sampled while attached, the renderer
  drops the self-composite draw rather than requiring the extension.
- **No vendor-specific extension and no GPU-name check.** Selection is by API version, features, formats and
  surface capabilities.

## Verified devices

| Device | Driver | Status |
|---|---|---|
| AMD Radeon RX 6400 (RADV NAVI24) | Mesa RADV | accepted; every measurement in `docs/plan.md` is from this device unless a leg says otherwise |
| Intel(R) UHD Graphics 630 (CFL GT2) | Mesa ANV | **accepted 2026-10-13 on the current main** (`intelfresh1`: `DRI_PRIME=pci-0000_00_02_0!`, `--require-gpu 'UHD Graphics 630'`, jar `b4bc6c3f6ea64f2fbd0e2653`, `status=PASSED`, `presentedImageVerdict=PASSED`, `exit=0`, VUID 0, `worldTicksAtEnd=900`, one celtium jar pair in `mods/`). Earlier on the same day: failure recovery (`intelfout2`), reload (`intelreload1`), resize (`intelresize1`) |

Every other vendor, driver and operating system is **unverified**. Nothing in this file claims otherwise.
