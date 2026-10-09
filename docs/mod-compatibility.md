# Mod compatibility record

This file records, per mod and per call pattern, what the Vulkan migration does with it: whether it is captured by the
Forge API swap, whether it needs the direct-GL compatibility layer, and what has actually been observed on hardware.
It is filled in as mods are tested; nothing here is a prediction.

The distinction that matters, and that this file keeps explicit:

- **Forge API mods** draw through Minecraft's own entry points (`Tessellator`, `BufferBuilder`,
  `WorldVertexBufferUploader`, `GlStateManager`, `RenderItem`, `TESR`, `RenderWorldLastEvent`). These are the ones the
  Mixin layer can capture, and capturing them is what "no changes required from the mod" means.
- **Direct GL mods** call LWJGL themselves (or ship GLSL and use it through OpenGL calls). The Forge entry points never
  see those calls, so they are a separate effort, listed here as encountered.
- Neither list is a claim that "all mods work". The list says what was measured.

## API mods

| Mod | What it does | Capture status | Evidence |
|---|---|---|---|
| Celtium validation fixture (`org.soutalab.celtium.validation`) | `RenderWorldLastEvent` handler drawing a `POSITION_COLOR` quad with `Tessellator`/`BufferBuilder`/`GlStateManager`, no Celtium-specific calls | Captured and drawn by Vulkan | Batch accepted with GL's own upload and draw cancelled; 25344 red pixels in the captured frame; `immediateOffered` rises by the mod's batch count (8365 to 9078 in the measured runs) |
| Vanilla renderers (entities, particles, sky, GUI, …) | The same entry points | Captured; the layouts that have no pipeline yet are refused with a reason and drawn by GL | `immediateState` (over 170k mirrored state calls per run), `immediateOffered`, and `immediateRefusal=no_pipeline_yet_for_a_24-byte_vertex` for the 24-byte formats |

## Call patterns that bypass the wrappers

| Pattern | Where it was found | What happens now |
|---|---|---|
| `org.lwjgl.util.glu.Project.gluPerspective` for the world projection | `EntityRenderer`, four call sites | A Mixin targets that class **by name** (`targets = "..."`, so no LWJGL 2 utility dependency is added). It compiles and applies, but the counter shows it never fires (`matrixChannels=persp:0`), because the class lives outside the transforming loader. The immediate path therefore reads the live matrices with `glGetFloatv` instead |
| `GL11.glMatrixMode` / `glLoadIdentity` / `glFrustum` around the projection | `EntityRenderer` | Mixing into LWJGL's own `org.lwjgl.opengl.GL11` is **ruled out by measurement**: the client exits with code 255 about 5.7 s in, while the mod list is still being searched. Capture has to happen at Minecraft's call sites or by reading GL state, not at the GL API class |
| Anything else that bypasses `GlStateManager` for matrices | — | Not yet enumerated. The state counters (`mode`, `identity`) show what does come through the wrapper, so gaps are visible rather than assumed |

## Direct GL usage seen so far

None from a content mod yet: the mods tested to this point are API mods. When a mod is found that calls LWJGL directly,
it is listed here with the specific call pattern, because the Forge API swap cannot capture it by construction.
