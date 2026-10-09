# Mod compatibility scope

What Celtium does with a mod's drawing, which mods are covered, and which are not. It is filled in as mods are tested;
nothing here is a prediction, and nothing here is a claim that "all mods work".

**Direction (2026-10-09):** Celtium is a Minecraft 1.12.2 / Cleanroom-specific native Vulkan renderer. The Forge and
Minecraft public APIs are kept as the **compatibility entry point**, not as an architecture to generalise: what sits behind
them is replaced by Vulkan rendering that understands what the call means in Minecraft. There is no plan to translate all of
GL so that all mods work, and no general GL-emulation layer is being built. See [the plan](plan.md), section 0.

## Covered: Forge API mods

Mods that draw through Minecraft's own entry points - `Tessellator`, `BufferBuilder`, `WorldVertexBufferUploader`,
`GlStateManager`, `RenderItem`, `TESR`, the Forge render events - are the ones the swap can see. Capturing them is what
"no changes required from the mod" means, and it is a claim about the *entry point*, not about every call a mod can make.

| Mod / caller | What it does | Status | Evidence |
|---|---|---|---|
| Vanilla renderers (entities, particles, sky, GUI, text) | The same entry points | Captured where a pipeline exists; refused with a reason and drawn by GL where not | `immediateOffered` in the tens of thousands per run, `immediateTaken` in the hundreds to thousands, per-format refusal reasons in the debug line |
| Validation fixture (`org.soutalab.celtium.validation`) | Forge render event drawing `POSITION_COLOR` quads with `Tessellator`/`BufferBuilder`/`GlStateManager` | The batch is **not** shown to reach the frame yet | Its own batch produces zero pixels while `Gui.drawRect` in the same handler, pass and frame fills 55,080; the fixture is not yet a usable instrument. Recorded as open in [the second audit](audit-2026-10-09-second.md) |
| ITEM-format callers (`RenderItem` path) | 28-byte `POSITION_TEX_COLOR` batches | Layout corrected (audit S08) and verified in the shipped SPIR-V; the acceptance fixture is not built | Source and checked-in SPIR-V compared |

## Investigated individually: direct GL, GLSL and native-GL mods

These call LWJGL themselves or ship shaders that they bind through GL. The Forge entry points never see those calls, so the
swap cannot capture them, and there is **no general translation layer** to fall back on. Each one is investigated where it
draws, and the outcome is recorded per mod rather than promised in advance.

| Pattern | Where it was found | What happens now |
|---|---|---|
| `org.lwjgl.util.glu.Project.gluPerspective` for the world projection | `EntityRenderer`, four call sites | A Mixin targets the class by name; it compiles and applies but never fires (`matrixChannels=persp:0`), because the class is outside the transforming loader. The immediate path reads the live matrices instead - one of the GL-state reads the plan removes |
| `GL11.glMatrixMode` / `glLoadIdentity` / `glFrustum` around the projection | `EntityRenderer` | Mixing into LWJGL's own `GL11` is ruled out by measurement: the client exits 255 about 5.7 s in, before the mod list finishes |
| Anything else that bypasses `GlStateManager` | — | Not enumerated. The state counters show what does come through, so gaps are visible rather than assumed |

## Not supported, and why

| Item | Reason |
|---|---|
| Normal Forge (Java 8 / LWJGL 2) and Forge + lwjgl3ify / RFB configurations | Cleanroom is the only supported runtime; their startup paths are out of scope |
| Windows, macOS, NVIDIA, and any GPU not yet run here | Untested. The renderer is capability-checked rather than name-checked, but untested is untested |
| Framebuffers other than the verified RGBA8 + D24 contract (MSAA, other formats, custom blit chains) | The hybrid copies through a fixed contract; a mod using something else needs explicit support or a real fallback (audit S14) |
| Shader packs, OptiFine-style pipelines, and mods that bind their own GL programs | They replace the GL pipeline the swap sits in; no support is planned without a per-mod answer |
| Mods that call GL directly for drawing | Captured only if they happen to route through the entry points; otherwise a per-mod investigation, not a layer |

## What is measured, and how

Two separate things, never merged into one claim: **(a) binary/API compatibility** - the mod loads and its calls arrive,
observed through the offer/accept counters and the absence of Mixin transformation failures - and **(b) actual Vulkan
drawing** - images compared against GL with a per-configuration noise floor, with validation messages counted as failures.
A mod that loads and whose calls are accepted is not reported as a mod whose drawing is correct, and the current state of
both columns is in [the second audit](audit-2026-10-09-second.md).
