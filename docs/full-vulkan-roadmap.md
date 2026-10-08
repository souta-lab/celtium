# Celtium: full-screen native Vulkan roadmap

The goal is no longer "terrain on Vulkan". The completion condition is that Minecraft's own rendering, the final
composition and the present are native Vulkan, with no GL context and no GL/Vulkan shared-image bridge left in the
startup path.

**Current status is hybrid and is labelled as such everywhere:** terrain geometry is meshed by Celeritas and drawn by
Celtium's Vulkan pipelines into shared images; every other part of the frame is still drawn by Minecraft's OpenGL
renderer and composited through the bridge. Nothing in this document claims otherwise, and "terrain backend: vulkan"
in the debug overlay is not evidence of full Vulkan.

## Inventory of render entry points and their GL dependencies

| Area | 1.12.2 entry point | GL surface used today | Port route |
| --- | --- | --- | --- |
| Terrain | `RenderGlobal.renderBlockLayer` -> Celeritas terrain | none (already Vulkan) | done (S0) |
| Entities | `RenderGlobal.renderEntities` -> `Render<T>` -> `ModelBase.render` | `BufferBuilder`/`Tessellator`, `GlStateManager` matrices and state, `RenderHelper` lighting | dynamic Vulkan vertex stream + MC-format pipelines (S4) |
| Tile entities | `TileEntityRendererDispatcher.render` -> TESRs | Tessellator, `GlStateManager`, occasional FBO/DisplayList, font renderer (signs) | S4 + S6 (font) |
| Item / hand | `ItemRenderer.renderItemInFirstPerson`, `RenderItem` | `GlStateManager` matrix stack, `RenderHelper`, `TextureManager`, TESR for some items | S4 |
| GUI / font / HUD | `GuiIngame`, `GuiScreen.drawTexturedModalRect`, `FontRenderer` | ortho matrices, `GlStateManager`, glyph atlas textures, Tessellator | ortho Vulkan pipeline + font atlas upload (S6) |
| Particles | `ParticleManager.renderParticles` -> `Particle.renderParticle` | Tessellator, blend/depth-mask toggles | S4 |
| Sky / clouds / weather | `RenderGlobal.renderSky`, `renderClouds`, `renderRainSnow` | Tessellator, `GL_TEXTURE` binds, **`ShaderGroup` (GLSL)** for some sky/outline paths | S4 + shader translation (S5) |
| Water / translucency | terrain translucent pass + `BlockFluidRenderer` | none (terrain) | done with terrain (S0) |
| FBO / postprocess | `EntityRenderer.renderWorldPass`, main framebuffer, entity-outline framebuffer, `ShaderGroup` | `Framebuffer` (GL FBO), post shader passes | Vulkan render targets + fullscreen pipelines (S3/S5) |
| Texture animation / resource reload | `TextureMap`, `TextureAtlasSprite`, `TextureManager`, `SimpleReloadableResourceManager` | GL texture upload and mipmap generation | Vulkan texture manager for every texture, not just the terrain atlas (S5) |
| Resize / world switch | `Minecraft.resize`, `EntityRenderer.updateFramebufferSize`, `RenderGlobal.loadRenderers` | FBO/texture recreation | swapchain + render-target recreation (S2/S3) |
| Present | GLFW/GL swap buffers on MC's window | GL context and default framebuffer | `VkSurfaceKHR` (Wayland/X11) + swapchain present (S2) |

Direct GL surfaces that mods reach for, and how each is to be handled:

| Mod-facing API | Examples in the wild | Handling |
| --- | --- | --- |
| `GlStateManager` state and matrices | most content mods | implement the used subset on the Vulkan side (state tracker + matrix stack) |
| `Tessellator` / `BufferBuilder` / `WorldVertexBufferUploader` | most content mods | route into the dynamic Vulkan vertex stream (S4); vertex formats are declared per `VertexFormat` |
| `RenderHelper`, `OpenGlHelper`, `GlStateManager` texture binds | most content mods | map to Vulkan state (lighting becomes a uniform, binds become descriptor sets) |
| `GL11`/`GL12`/`GL20`/`GL30` direct calls | a minority, often for state or raw draws | compatibility layer for the supported subset; anything outside it is reported per mod rather than emulated silently |
| `DisplayList` (`glNewList`/`glCallList`) | vanilla sky and some mods | record into Vulkan command buffers or re-record geometry; measured per user |
| `Framebuffer` (GL FBO) | shader mods, minimaps, portals | map onto Vulkan render targets (S3); formats and attachments validated per use |
| `ShaderGroup` (GLSL) and raw GLSL programs | shader packs, entity outline | translate to SPIR-V for the supported subset; unsupported stages reported |
| raw LWJGL / `GLContext` / `GL.createCapabilities` | low-level mods | only meaningful once GL is gone; these become unsupported and are listed as such |

Status per mod is tracked in `docs/mod-compatibility.md` (created as mods are tested); "it loaded" is not a result -
the entry point it uses and whether its output was verified is.

## Stages

- **S0 - terrain on Vulkan (done).** Meshing stays in Celeritas; arenas, pipelines, per-pass handoff and the
  shared-image bridge are implemented and verified on AMD RX 6400 and Intel UHD 630.
- **S1 - common GPU abstraction.** Introduce a device-agnostic interface for buffers, textures, samplers, pipelines,
  dynamic state and render targets in `common/`, with the Vulkan implementation behind it. Terrain moves onto it
  first so the abstraction is proven against working code before it carries anything else.
- **S2 - Vulkan presentation.** `VkSurfaceKHR` for the Wayland and X11 (Xwayland) cases, swapchain creation and
  recreation, acquire/present with proper semaphores, and an image-layout contract for the frame. Capability-based:
  a surface extension that is not advertised means the platform is unsupported, not that another GPU name is tried.
- **S3 - Vulkan render targets replace Minecraft's framebuffers.** The main framebuffer, the entity-outline
  framebuffer and any postprocess targets become Vulkan images; the bridge and its colour/depth copies disappear
  from the frame path.
- **S4 - dynamic geometry for everything that is not terrain.** A vertex stream for `Tessellator`/`BufferBuilder`
  style geometry with MC's vertex formats (`POSITION_COLOR`, `POSITION_TEX`, `POSITION_TEX_COLOR`,
  `POSITION_NORMAL_TEX_LIGHT` and the entity/item variants), plus the pipelines for the state combinations those
  formats use (blend modes, depth mask, cull, alpha test).
- **S5 - textures and shaders.** Every texture goes through a Vulkan texture manager (upload, mipmaps, animation,
  reload); `ShaderGroup` and the vanilla shader paths are translated to SPIR-V for the supported subset.
- **S6 - GUI, font, HUD, sky, clouds, weather, particles.** Ortho pipeline and font atlas, then the world-space
  extras. These are the last things that still need the GL path today.
- **S7 - remove GL.** Startup without a GL context, a frame rendered and presented entirely by Vulkan, and the
  interop bridge deleted. Until this stage is verified on both GPUs, the project is hybrid and says so.

Each stage lands with the validation harness extended to cover it, and the documents state implemented /
not implemented / verified on hardware separately.

### Foundation code landed so far (S4/S5, not yet wired into the renderer)

- `celtium-vulkan/.../VulkanDynamicMesh.java` - host-visible vertex and index streams for the geometry Minecraft
  builds per frame, with layouts that mirror `DefaultVertexFormats` (`POSITION_COLOR` + light,
  `POSITION_TEX_LIGHTMAP_COLOR`, `POSITION_TEX_COLOR`, `POSITION_NORMAL_TEX_LIGHTMAP_COLOR`). This is what
  `Tessellator`/`BufferBuilder` output has to become in S4.
- `celtium-vulkan/.../VulkanTexture2D.java` - a sampled image with its own sampler, a staging upload path, an
  optional mip chain and the NEAREST/LINEAR choice the atlas and the GUI need. This is the S5 foundation for every
  texture that is not the terrain atlas.

Both are now wired: with `-Dceltium.vulkan.uiTest=true` the backend builds a textured quad through
`VulkanDynamicMesh` (the `POSITION_TEX_LIGHTMAP_COLOR` layout), draws it with `VulkanUiPipeline`
(`celtium-vulkan/.../VulkanUiPipeline.java`, SPIR-V from `ui_quad.vert`/`ui_quad.frag`) into the terrain colour image
inside the same render pass, and samples the block atlas through a second descriptor set. That exercises, on real
hardware, the dynamic host-visible vertex stream, a second graphics pipeline, a second texture binding, straight
alpha blending and the push-constant convention - the pieces every later stage needs.

Evidence (RX 6400, `p5h-uitest-amd` against the same build without the flag): exactly 36864 pixels change, in a
192x192 region at x 32..287, y 18..161 - the quad's declared NDC extent - with 13 distinct colours from the atlas
(mean `(209.5, 99.1, 24.8)`), `status PASSED`, `nativeTerrainVerified True`, 1635 passes, 0 skipped, 0 errors and no
validation message. This is a *test quad*, not the GUI port: no Minecraft GUI code path calls Vulkan yet, and the
flag is off by default. What it establishes is that the abstraction works against real drawing; the next step is
routing an actual `Tessellator`/`BufferBuilder` consumer (GUI quads, then particles) through it.

## Mod compatibility policy

Minecraft's own abstraction layer is the preferred route: a mod that draws through `Tessellator`, `BufferBuilder`,
`RenderHelper` or the `GlStateManager` subset is carried by implementing those entry points on Vulkan (S4/S6).
Direct GL calls are handled by a compatibility layer for the subset that can be mapped safely (state toggles,
matrix stack, texture bind, simple draws); every API that cannot be mapped is reported explicitly with the mod that
used it, and per-mod results are recorded rather than assumed. Interop - keeping GL alive for mods while Vulkan
draws the rest - is an intermediate stage of the migration and not the completion condition.

## Zink

Zink (GL on Vulkan) is a different proposition and is treated separately from the native path: it still requires a
GL-dependent stack and a Mesa build shipped to users, so it would have to be justified by the concrete conditions
of OS, driver, native distribution, context creation, present path and licensing. It is not adopted here, and no
stage above depends on it.

## Feature selection

Every capability decision is made from `VkPhysicalDeviceFeatures`, `VkPhysicalDeviceVulkan12Features` (or the
extension's own feature struct), the enabled extensions and the queried limits - never from a GPU name or vendor.
Optional accelerations such as mesh shaders are used only when advertised and never required, because the verified
targets are an AMD RX 6400 and an Intel UHD 630 and the code must run on anything that satisfies the contract.
