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

## Planned cohort (2026-10-12): the item/recipe mod is HEI, not JEI

Cleanroom's own modpack guide names **HadEnoughItems (HEI)** as the current choice for 1.12.2 and marks JEI
outdated for this loader ("JustEnoughItems (JEI) | Outdated | ✅ Use HadEnoughItems (HEI)"), and HEI is
Cleanroom's own fork with load-time, memory and render optimizations
([the guide](https://cleanroommc.com/wiki/end-user-guide/preparing-your-modpack),
[HEI](https://www.curseforge.com/minecraft/mc-mods/had-enough-items)). Its maven coordinate is
`mezz:jei:4.29.11` on `maven.cleanroommc.com`, so it is a drop-in for the JEI API that mods already target.

**What this changes for the plan**: the objective names JEI as the first representative mod, and on this
loader the honest target is HEI - the same API, the same GUI shape (item list, search bar, tooltips, recipe
pages, scroll, animation) and the same rendering paths, but the build Cleanroom itself recommends. A JEI jar
was fetched for a first look (`jei_1.12.2-4.16.5.1026.jar` from `maven.blamejared.com`), and JEI's own 1.12.2
issue tracker is dominated by OptiFine conflicts rather than by anything a renderer can fix.

**Status (2026-10-12)**: **HEI 4.31.2 is installed** in the Prism instance
(`HadEnoughItems_1.12.2-4.31.2.jar`, 890,976 bytes, `mcmod.info` modid `jei`, name "Had Enough Items", authors
mezz and Rongmario), fetched from `maven.cleanroommc.com/mezz/jei/4.31.2/jei-4.31.2.jar` — the coordinate the
project's own README gives for developers. The JEI jar fetched earlier was removed rather than kept beside it,
because two mods with modid `jei` in one instance is a conflict, not a comparison.

**The first leg did not run** (`r142-hei`): it reported `Waiting for the game slot (held by pid 2997923)`,
`endedBy=process-exit`, `elapsedSeconds=31.4` - a slot timeout, not a result, which is the same shape of
non-answer this project has already recorded once. What it does show is that the cohort was assembled with
HEI in it: the run's own debug log names `HadEnoughItems_1.12.2-4.31.2.jar` and the `jei` modid, so the
harness picked the jar up rather than ignoring it.

What the leg has to show before this row can be called verified is unchanged: the game reaches the world with
HEI loaded, the item list, search bar and tooltips draw natively, and the run is `validationMessageCount=0`
with the picture gate's own verdict quoted. A leg that merely starts is not acceptance - and neither is one
that never got the slot.

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

**No third-party content mod is in the *current* cohort's set** - the table above is the whole of what the current
Intel/AMD terrain runs load. That is a statement about this cohort, not a denial of the runs that came before it.

**Correction, 2026-10-11 (audit B12).** An earlier version of this file went further and said no JEI jar was ever loaded,
reading the run names as a misnomer. That was wrong, and it turned real past runs into a fabrication. Both things are
true and they are different claims:

- The *current* cohort (the runs cited in `docs/plan.md` and `docs/scope-and-limits.md`, and the `jeiScreen` flag) loads
  no JEI: that flag opens `net.minecraft.client.gui.inventory.GuiInventory`
  (`validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java:3032`), and the runs' `mods/`
  directories and logs contain no JEI entry. "JEI" in *those* run names is the screen variant, not a loaded mod.
- **Earlier runs on 2026-10-11 did load JEI and two other mods**, and their evidence is below. They are **past
  evidence**, not current acceptance: none of them passed the picture gate, and the current cohort has not re-run them.

## Past evidence: runs that did load content mods (2026-10-11, before this cohort)

Read from each run's own lock file and log in `.celtium-work/`, not from memory. Every hash is the one the run recorded
for the jar it loaded.

| Run | 2026-10-11 | Leg | Mods (jar sha256 prefix) | Verdict | What it is evidence of |
|---|---|---|---|---|---|
| `mod3-glless-jei` | 01:10:56 | GL-less | **JEI `jei_1.12.2-4.22.0.1035.jar` `e592b0019aa0cd32`**, celtium `9b0913a314fe7bd4`, fixture `9ea72bc9df85d94c`, Fugue `438248925eb38d16`, Scalar `9fa05d84b72f31df` | `FAILED_PRESENTED_IMAGE` | JEI loaded and initialised (`latest.log`: 43 JEI lines, JEI startup present), the fixture opened its inventory screen at tick 30, and the run reached tick 202 under GL-less |
| `mod5-bridge-jei` | 01:20 | GL bridge | the same JEI `e592b0019aa0cd32`, celtium `6dcfba123f51ee43`, fixture `99cf2e432e59774b` | `FAILED_PRESENTED_IMAGE`, `presentedNonBackgroundFraction=0.777045` | the same JEI jar under the bridge leg, with a picture |
| `mod6-bridge-hudmods` | 01:24 | GL bridge | the same JEI jar, **AppleSkin `AppleSkin-mc1.12-1.0.14.jar` `4bbd37edecff0b42`**, **MapWriter `mapwriter-2.8.15.jar` `e9a0551013ab785e`**, celtium `6dcfba123f51ee43`, fixture `99cf2e432e59774b` | `FAILED_PRESENTED_IMAGE`, `presentedNonBackgroundFraction=0.791715` | three content/HUD mods loaded together in one run; their HUD and map drawing were in the frame |

**What these runs do not show**, and must not be read as: a passing picture acceptance with JEI, AppleSkin or MapWriter
(each verdict is `FAILED_PRESENTED_IMAGE`), correctness of JEI's recipe rendering, or anything about the current jar. The
runs are from git heads `967ea5a8`, `ee397a6e` and `04a0ca8e` with dirty trees, hours before the current cohort, and
**the current cohort has not been re-run with any of these mods**. Re-running them is the way to turn this table into a
current claim; until then it stays a record of what was measured then.

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
