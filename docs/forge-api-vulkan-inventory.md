# Forge/Minecraft drawing APIs and their Vulkan translation

> **Stage record, superseded.** This document records what was done during a stage and is kept for evidence.
> The single current plan is [`docs/plan.md`](../plan.md); where this file contradicts it, the plan wins.

Policy: existing mods should not have to be changed. Where a mod draws through Minecraft's or Forge's own API, the
implementation behind that API is replaced with a Vulkan one (Mixin first, fork only with a recorded reason).
This document is grounded in the real 1.12.2 sources extracted from
`forge122/build/rfg/srg_patched_minecraft-sources.jar`, so the entry points below are the methods mods actually call.

## 1. Entry points that are draw/state/upload commands

| Class | Method (SRG name, what mods call) | Role |
| --- | --- | --- |
| `net.minecraft.client.renderer.WorldVertexBufferUploader` | `func_181679_a(BufferBuilder)` | **The single immediate-mode draw entry.** `Tessellator.func_78381_a()` ends in this, and so does every `BufferBuilder`-based renderer (entities, TESRs, items, particles, GUI, sky, clouds, weather). Intercepting it covers the whole immediate path. |
| `net.minecraft.client.renderer.Tessellator` | `func_78381_a()` | Flushes the shared buffer into the uploader above; covered by the same interception. |
| `net.minecraft.client.renderer.BufferBuilder` | `func_181668_a(int, VertexFormat)` begin, `func_187315_a` pos, `func_187314_a` tex, `func_181666_a`/`func_181669_b` colour, `func_178978_a` normal, `func_178972_a`/`func_178962_a` lightmap, `func_78914_f` end vertex, `func_181674_a` sort, `func_178965_a` reset, `func_178981_a(int[])` put-bulk-data, `func_181672_a`/`func_178993_a` state save/restore | Vertex *production*. Translating these means replacing the buffer itself; intercepting at the uploader is enough for drawing and keeps `State` semantics intact for mods that cache vertices. |
| `net.minecraft.client.renderer.GlStateManager` | ~200 `public static` methods (`func_179094_E`/`func_179121_F` matrix stack, `func_179147_l` blend, `func_179098_w` texture 2D, `func_179068_m` blend func, `func_187428_a` blend func separate, `func_179097_i` depth mask, `func_179132_a` depth test, `func_179131_c` colour, `func_179069_o` shade model, ...) | **State commands.** These must not reach GL; they become a tracked renderer state that selects a Vulkan pipeline lazily when a draw arrives. |
| `net.minecraft.client.renderer.texture.TextureManager` / `GlStateManager` texture binds | `func_110577_a(ResourceLocation)`, `func_187421_b`, `func_179144_i(int)` | Binds become descriptor-set selection by texture identity, not GL calls. |
| `net.minecraft.client.renderer.OpenGlHelper` | `func_77475_a`/`func_77473_a` (lightmap texture unit), `func_188785_f` (shader) | Maps to light-map binding and the SPIR-V program selection. |
| `net.minecraft.client.shader.Framebuffer` | `func_147610_a`, `func_147615_c`, `func_147605_b/e` | Render targets (roadmap S3); GL FBO objects become Vulkan render targets. |

## 2. Forge paths that are *not* draw entries

| Path | Why it is not an entry | What to do instead |
| --- | --- | --- |
| `net.minecraftforge.client.model.pipeline.IVertexConsumer` / `VertexBufferConsumer` / `LightMap` / `QuadGatheringTransformer` | They are vertex *transformers*; `VertexBufferConsumer` writes into a `BufferBuilder`, so their output is already covered by the uploader interception. | Leave the classes and their signatures alone. |
| `BakedQuad`, `UnpackedBakedQuad`, `BakedQuadRetextured`, `IModel`, `IModelState`, `TRSRTransformation` | Pure model data and transforms; no drawing command. | Do not touch; vanilla `RenderItem`/`BlockModelRenderer` paths that consume them end in `BufferBuilder` and are covered. |
| `net.minecraftforge.client.event.RenderWorldLastEvent`, `RenderGameOverlayEvent`, `DrawBlockHighlightEvent`, ... | Event *dispatch*: the mod then draws through `Tessellator`/`GlStateManager` (covered) or through raw GL (not covered, see section 4). | Preserve event order and signatures exactly; do not reroute dispatch. |
| `TileEntitySpecialRenderer`, `RenderItem`, `IItemRenderer` | Higher-level renderers that call the immediate path internally. | Covered through the uploader; no signature changes. |

## 3. Translation design

1. **Draw entry.** A Mixin on `WorldVertexBufferUploader.func_181679_a(BufferBuilder)` first offers the batch to
   Celtium: the buffer's native bytes, vertex count and `VertexFormat` are handed to the Vulkan side, which writes
   them into `VulkanDynamicMesh` (formats already supported: the `POSITION_*` family used by the immediate path) and
   issues one indexed draw with the pipeline selected from the tracked state. If Celtium declines (format it does not
   know yet, or no Vulkan frame is open), the original body runs unchanged so nothing regresses to "no output".
2. **State.** A `GlStateManager` Mixin records the state that affects pipeline selection (blend enable and factors,
   depth test/mask, cull, alpha test, shade model, texture bind, colour, matrix mode and stack) into a
   `VulkanGlState` object instead of issuing GL calls. The draw entry turns that object plus the vertex format into a
   pipeline key, which is cached. Matrices feed the push-constant block.
3. **Deferred pipeline selection.** State changes never create pipelines; they only mutate the state object. The
   pipeline is chosen when a draw actually arrives, so a mod that sets state it never uses costs nothing.
4. **Binary compatibility.** No public class, method or field signature of Minecraft or Forge is changed. Mixins
   inject at method heads and use `@Accessor` for private fields; the public surface mods link against stays
   untouched, and the compatibility layer only adds classes under Celtium's own package.

## 4. What Forge entry points cannot capture

Mods that call LWJGL directly (`GL11`, `GL20`, `GLContext`, `GL.createCapabilities`), compile their own GLSL
(`ShaderGroup`, raw `glCreateShader`), or use GL-only features (FBOs via raw GL, `glReadPixels`, display lists
through `GL11.glNewList`) bypass the classes above. For those, the compatibility layer has to intercept the GL entry
points themselves (the `GlStateManager` subset is not enough) or the mod has to be ported; per-mod results are
tracked in `docs/mod-compatibility.md` as they are measured. "Forge API swap makes every mod work" is not claimed.

## 5. Implementation landed so far

- `celtium-vulkan/.../VulkanImmediateDrawer.java` - the topology half of the draw entry: Minecraft's `GL_QUADS`
  become two triangles each, triangle/fan/strip pass through with consistent winding, and modes with no Vulkan
  equivalent (points, lines) are refused with a reason instead of drawing something wrong. Verified by six unit
  tests (`VulkanImmediateDrawerTest`), which run without a GPU because the conversion is pure arithmetic.
- `celtium-vulkan/.../VulkanGlState.java` - the state half: blend factors, depth test/mask, cull, alpha test,
  lighting, shade model, colour, texture binding and the three matrix stacks (`float[16]`, column-major, no matrix
  library dependency). `pipelineKey(vertexFormatHash)` changes exactly when a different pipeline is required and
  deliberately ignores colour, matrices and texture bindings, which are push constants and descriptors. Verified by
  five unit tests (`VulkanGlStateTest`) covering push/pop, stack independence, column-major multiplication order and
  key stability.
- Both are foundations: no Mixin calls them yet, no pipeline is created from the key yet, and nothing about them is
  GPU-verified. The next step is the `WorldVertexBufferUploader.func_181679_a` Mixin plus the deferred pipeline cache
  that consumes these two classes.

## 6. Verification plan

1. A small test mod that draws in a Forge event (`RenderWorldLastEvent`) through `Tessellator` and
   `GlStateManager` - the smallest thing that exercises the real entry points, with a pixel check that its output
   appears.
2. The same mod compiled against the unmodified Forge/Minecraft API (binary compatibility: no recompilation against
   Celtium).
3. Real major mods afterwards, one at a time, recording which entry point each one uses and whether its output was
   verified on both GPUs.

## 7. Fork policy

Mixin-first. A Cleanroom/Forge fork is only proposed where a Mixin cannot reach the behaviour (for example a change
required inside a final method or a lambda the injector cannot target), and each such case is recorded here with the
reason, the distribution impact and the maintenance cost before anything is forked.
