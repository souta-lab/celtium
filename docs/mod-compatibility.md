# Mod compatibility scope

What Celtium does with a mod's drawing, which mods are covered, and which are not. It is filled in as mods are tested;
nothing here is a prediction, and nothing here is a claim that "all mods work".

> **Corrected 2026-10-11.** Three rows below described instruments and defects that have since been replaced; they are
> corrected in place and the correction is named, because a stale "not yet usable" reads exactly like a current one.
> The verified mod set is now stated explicitly in "What is actually verified today".

**Direction (2026-10-09):** Celtium is a Minecraft 1.12.2 / Cleanroom-specific native Vulkan renderer. The Forge and
Minecraft public APIs are kept as the **compatibility entry point**, not as an architecture to generalise: what sits behind
them is replaced by Vulkan rendering that understands what the call means in Minecraft. There is no plan to translate all of
GL so that all mods work, and no general GL-emulation layer is being built. See [the plan](plan.md), section 0.

## What is actually verified today

Measured on the runs cited in `docs/plan.md` and `docs/scope-and-limits.md`; the mod set is what the harness puts in the
run's own game directory, read from that directory and from the run log, not from the Prism instance:

| Component | Version | Role | Verified |
|---|---|---|---|
| Cleanroom | 0.6.13-alpha | the runtime | AMD RX 6400 and Intel UHD 630, JDK 25, LWJGL 3.4.1 |
| Fugue | 0.24.4 | Cleanroom library mod | loads and the game reaches the world in every cited run |
| Scalar (+ scala-library 3.9.0, scala-parser-combinators 2.4.0) | 3.9.0 | Cleanroom library mod | same |
| `celtium-validation-client` | 2.5.12-celtium.0.1.0-dev.1 | the fixture, **not shipped** | drives the scene, camera, screen and capture |
| Celtium | 2.5.12-celtium.0.1.0-dev.1 | the renderer under test | same |

**No third-party content mod is in that set.** Every "mod compatibility" statement in this file is therefore about the
*entry points* a mod would use, not about a mod that has been run. The one exception in older notes - "JEI" - is a
mismatch worth naming: the flag `-Dceltium.validation.jeiScreen=true` opens
`net.minecraft.client.gui.inventory.GuiInventory`, the **vanilla inventory**
(`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java:3032`), and **no JEI jar is loaded**
in those runs (measured: the run's `mods/` directory and its `latest.log` contain no JEI entry at all). Older notes and
run names that call that screen "JEI" are describing the vanilla inventory screen.

## Covered: Forge API mods

Mods that draw through Minecraft's own entry points - `Tessellator`, `BufferBuilder`, `WorldVertexBufferUploader`,
`GlStateManager`, `RenderItem`, `TESR`, the Forge render events - are the ones the swap can see. Capturing them is what
"no changes required from the mod" means, and it is a claim about the *entry point*, not about every call a mod can make.

| Mod / caller | What it does | Status | Evidence |
|---|---|---|---|
| Vanilla renderers (entities, particles, sky, GUI, text) | The same entry points | Captured where a pipeline exists; refused with a reason and drawn by GL where not | per-format histogram and `CELTIUM_GROUP_BOUNDS` in the 2026-10-11 GL-less runs; GUI/HUD regions compared against the stored GL reference (`docs/scope-and-limits.md` §1) |
| Validation fixture (`org.soutalab.celtium.validation`) | Forge render event drawing `POSITION_COLOR` quads with `Tessellator`/`BufferBuilder`/`GlStateManager` | **Corrected:** it is the primary instrument now, not "not yet usable". Its batches are accepted and counted per format, and its presented-frame dumps are the picture evidence for every GL-less acceptance | `docs/plan.md` §0.114-§0.147; `celtium offer decisions: offers=5000 accepted=4892 glBodyCompleted=154` in a 2026-10-11 GL-less run |
| ITEM-format callers (`RenderItem` path) | 28-byte `POSITION_TEX_COLOR` batches | **Corrected:** the layout is mapped to id 4 and accepted (`id4=3752/3752` in a 2026-10-11 GL-less run); a sprite-scale difference against the reference is open and is **not** the GUI model-view - all 1344 measured groups carry model-view scale `(16,-16,16)` | run `gs-item2`, jar `c42e3eebe6d0858f`; reported to the pipelines/texture owners |

## Investigated individually: direct GL, GLSL and native-GL mods

These call LWJGL themselves or ship shaders that they bind through GL. The Forge entry points never see those calls, so the
swap cannot capture them, and there is **no general translation layer** to fall back on. Each one is investigated where it
draws, and the outcome is recorded per mod rather than promised in advance.

| Pattern | Where it was found | What happens now |
|---|---|---|
| `org.lwjgl.util.glu.Project.gluPerspective` for the world projection | `EntityRenderer`, four call sites | **Corrected 2026-10-11:** a `GluProjectionMixin` exists and the projection channel is being reconciled (`docs/plan.md`, task-1); the old "compiles and applies but never fires (`matrixChannels=persp:0`)" note is superseded. The matrix mirror, not a GL read, is what the immediate path draws from (`VulkanTerrainGpuBackend` `MIRRORED_MATRICES`) |
| `GL11.glMatrixMode` / `glLoadIdentity` / `glFrustum` around the projection | `EntityRenderer` | Mixing into LWJGL's own `GL11` is ruled out by measurement: the client exits 255 about 5.7 s in, before the mod list finishes |
| Anything else that bypasses `GlStateManager` | — | Not enumerated. The state counters show what does come through, so gaps are visible rather than assumed. One gap is measured: the GL enable record behind `glIsEnabled` and the `GlStateManager` mirror can disagree, and `CELTIUM_GROUP_BOUNDS` now prints both (`state=` and `glBlend=`/`glDepthTest=`) so a mod that calls `glEnable`/`glDisable` directly is visible |

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
