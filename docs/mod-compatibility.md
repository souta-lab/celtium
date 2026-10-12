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

**The first leg that ran** (`r148-hei`): **6 mods locked** (the cohort was 3 before HEI was added), and
`worldTicksAtEnd=241` - **the game reached the world with HEI loaded**, which is the first of the four
conditions and the only one this leg establishes. It reported `presentedImageVerdict=FAILED_PRESENTED_IMAGE`,
which is **not** read here as a HEI defect: that gate is calibrated per scene and this leg did not run the
calibrated scene, so the verdict is evidence that the leg was not a picture leg, not evidence about HEI. A
scene-calibrated leg is still needed for the picture, the item list, the search bar and the tooltips.

**The earlier attempt did not run** (`r142-hei`): it reported `Waiting for the game slot (held by pid 2997923)`,
`endedBy=process-exit`, `elapsedSeconds=31.4` - a slot timeout, not a result, which is the same shape of
non-answer this project has already recorded once. What it does show is that the cohort was assembled with
HEI in it: the run's own debug log names `HadEnoughItems_1.12.2-4.31.2.jar` and the `jei` modid, so the
harness picked the jar up rather than ignoring it.

**What HEI's item list actually exercises, read from its own rendering path** (so the leg is judged against a
named expectation rather than "it looked fine"): the ingredient list is a **batched renderer**
(`IngredientListBatchRenderer` feeding `ItemStackFastRenderer`), and its `render` splits the ingredients into
**3d items with `GlStateManager.enableLighting()` and 2d items with `disableLighting()`**, drawing the 3d group
first, then the 2d group, then the overlays (stack counts) after `disableAlpha`. That is **the same
light-state contract the hotbar-item darkening was fixed on**, so this leg is a direct test of it: a wrong
lighting state shows up as 3d blocks too dark or 2d items wrongly lit, and the user's separate report of GUI
text darkening is plausibly the same family. The stack-count overlay is a second contract - it is drawn with
the font path after `disableAlpha`, so it also exercises the font work in flight.

What the leg has to show before this row can be called verified is unchanged: the game reaches the world with
HEI loaded, the item list, search bar and tooltips draw natively, and the run is `validationMessageCount=0`
with the picture gate's own verdict quoted. A leg that merely starts is not acceptance - and neither is one
that never got the slot.

**Measured 2026-10-12 (reference half first, candidate half below; owner gui-text, record
`docs/history/2026-10-12-hei-acceptance.md`).**
The reference says what the mod's screens should look like; the candidate half (further down) says what Celtium
draws, and it is the candidate half that decides this row.
Four reference legs were taken with upstream Celeritas `d12425eaaf184e79` (jar sha256
`d12425ea…`), the same HEI 4.31.2 jar and fixture `c39840d62e3695e9`, scene `jeiScreen`+`keepInventoryScreen`
with `pinSections=64` at tick 200, all `validationClean=true` and `validationMessageCount=0`, all
`UNCALIBRATED_PRESENTED_IMAGE` (correct: the JEI/HEI screen variant has no calibration key - the fixture says
so with `fixtureUncalibrated`). What they establish on the reference side:

* **the item list draws with isometric icons**: the cell fill fraction (pixels differing from the icon
  square's modal colour) reads `min=0.234 median=0.781 max=0.848` over the detected 9x7 slot grid
  (`origin=(378,213) pitch=54`, i.e. 18 GUI px at scale 3) - the ~0.7-0.85 band an isometric icon occupies,
  against the ~1.0 a flat texture would give;
* **the search box filters and the picture shows it**: with `jeiFilterText=diamond` through the mod's public
  API `IIngredientFilter.setFilterText`, the mod reports `visible=13 filtered=13 filterText="diamond"`
  (from `visible=60 filtered=908 filterText=""`), the search field carries 801 pure-white text pixels against
  0 without the filter, and the item-list strip drops from 8988 to 1042 distinct colours;
* **the tooltip is drawn** (the grid's own "Gold Ore" at `(660,315)-(820,355)`, the region
  `compare_jei_pair.py` uses);
* **the screen transitions run**: `jeiScreenOpenedAtTick=31`, `jeiRecipeOpened=true` at 41 through the mod's
  own API (`IRecipesGui.showCategories`), `screenshotAtTickScreen=mezz.jei.gui.recipes.RecipesGui` on the
  recipe leg and `none` on the cycle leg, i.e. inventory -> HEI -> back.

Two corrections the measurements force on this file and on the fixture's own description: **`jeiScreen` opens
`net.minecraft.client.gui.inventory.GuiContainerCreative`, not `GuiInventory`** (the fixture's world is
CREATIVE), and **a keystroke cannot be delivered to HEI's search field from a fixture** - characters sent
through the screen's own `keyTyped` leave the mod's filter at `keyboardFocus=false filterText=""`, because the
mod reads the LWJGL keyboard event rather than the method's arguments. The filter is therefore driven through
the mod's API and the artifact names that path (`jeiFilterTextPath`) rather than presenting it as a keystroke.

**The candidate half is now taken** (`hei-cand`, `hei-cand2`, `hei-cand-cycle`; same fixture and flags as
`hei-ref-list`, celtium jars `543745fbe076f15d…` = the content of HEAD `cc103738f` and `7bd467145b0d28e4…` =
that plus perf-ring's F3-panel WIP, `glless`/`presentReadback`/`validation`, RX 6400, all
`validationClean=true`, `validationMessageCount=0`, `errors=0`, `UNCALIBRATED_PRESENTED_IMAGE`, counter read from
the last `CELTIUM_VERIFY` line, `via=fixture end`). Measured against the reference frame:

* the grid, the icons and the filter **match**: the same detected `origin=(378,213) pitch=54 cols=9 rows=7`,
  cell fill `median=0.781 mean=0.682` against the reference's `0.681`, the search box's 801 pure-white text
  pixels identical to the reference's 801, and the mod's own `60/908 "" -> 13/13 "diamond"` unchanged;
* the transitions **match**: `jeiScreenOpenedAtTick` 30/33 against 31, `jeiRecipeOpenedAtTick` 43 against 41,
  `screenshotAtTickScreen=none` on the cycle leg exactly as on the reference's, i.e. inventory -> HEI -> back;
* **the tooltip does not match**, and this is the finding of the leg: the vanilla item tooltip loses **most of
  its main text pass** under GL-less - 585 of 797 shadow-coloured pixels have no main-pass pixel (reference
  `0 / 693`), the white ink drops 828 px -> 238 px, and the candidate's white pixels are a strict subset of the
  reference's (same glyphs, same positions, cells simply not drawn). It is scoped to the **vanilla** GUI text of
  that screen: HEI's own search-box text is pixel-identical to the reference (0 / 675), and the item-count and
  hotbar regions match at `mean|d|=0.000`. Two candidate builds give the same numbers, so it is not a capture
  race. It is the user's dark-text complaint on a real mod screen, reported to the renderer owners rather than
  fixed here (`docs/history/2026-10-12-hei-acceptance.md`, instrument
  `scripts/cpu-contract/font-shadow-main-check.py`).

So the row is **"HEI loads; the reference is established; the candidate's item list, icons, filter and screen
transitions match it, and the tooltip text does not"** - the mod's own screens are accepted except for that
defect, and the defect is in the shared font path, not in HEI's rendering. It is **not** an unqualified
"HEI verified" and must not be read as one: the acceptance gate for this row is the tooltip crop, and the
tooltip crop is the thing that fails.

**Round 168 additions, kept separate from the measurement above:**
* **The user reports HEI works in their own session** ("HEI動作確認済み"). That is a user observation on a
  different build and a different scene from the legs below, so it is recorded as such: it does not close this
  row, and it does not contradict the tooltip measurement either - a tooltip that loses part of its main pass is
  still a working mod screen to a player who is not looking for it.
* **Re-measured on the merged build** (jar `7e7a16597d1d73a0`, which includes the font-batching PR #12, fixture
  `5eb174f084d525c7`): the tooltip reads **`shadow_px=797 shadow_without_main=585 (73.4%)`** - pixel-identical
  to the candidate measured before #12 - and the search box stays at `675 / 0`. So #12 neither fixed nor
  regressed this defect; the instrument needs the tooltip's own colours to reproduce the numbers
  (`--main 255,255,255 --shadow 63,63,63 --scale 3`), which are recorded here because the earlier entry did not
  name them and a reader cannot reproduce the row without them.

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

## Cohort attempt: Storage Drawers 1.12.2-5.5.2 (2026-10-12) — **does not load on this runtime**

The cohort's TESR / custom block-and-item-model coverage is still zero (HEI is the only mod with an acceptance), so
the next member was chosen to fill exactly that: **Storage Drawers**, whose drawers are drawn by a
`TileEntitySpecialRenderer` and whose items carry custom models, and whose in-world block can be exercised by an
existing instrument - the fixture's `-Dceltium.validation.blockSweep=<n>` walks `Block.REGISTRY` (modded blocks
included) and places a bounded slice of it in the flat world.

| | |
|---|---|
| Storage Drawers | `StorageDrawers-1.12.2-5.5.2.jar`, 621,835 bytes, sha1 `512979dd3ecfe70cffc4d1c59630855474837fd1` - **matches Modrinth's published hash**; `mcmod.info` modid `storagedrawers`, version 5.5.2, `requiredMods: [forge, chameleon]`; licence **MIT** (Modrinth project `guitPqEi`) |
| Chameleon (its required dependency) | `Chameleon-1.12.2-4.1.3.jar`, 94,972 bytes, sha1 `77c74d2311db380ba8251617b131f2f7fe5a7b44` - **matches Modrinth's published hash** |
| Source | both from Modrinth's CDN, project ids `guitPqEi` / `rZrAKGe1` |

**Result: the game crashes during startup, before any fixture report.** Two legs (one with the sweep, one without)
both ended `exitCode 255`, `fixtureStatus=MISSING_REPORT`, ~10 s in, with

```
[FML]: Exception caught during firing event net.minecraftforge.client.event.ModelRegistryEvent@…:
java.lang.NoSuchMethodError: 'net.minecraft.item.Item net.minecraft.item.Item.getItemFromBlock(net.minecraft.block.Block)'
```

**What that is and is not.** The decompiled, patched source this project builds against *has* that method
(`.celtium-work/mc-sources/net/minecraft/item/Item.java:125`, and vanilla uses it at `:1990`/`:1994`), so the mod's
call is legitimate against the source - the failure is between the mod's compiled references and Cleanroom
0.6.13-alpha's runtime.

Two checks narrowed it, and one hypothesis was **ruled out**: the jar is the **reobfuscated release**, not a
development build, so "Modrinth served a deobf jar that calls MCP names" is false - all 251 classes were scanned and
**zero** of them reference `getItemFromBlock` by its MCP name, while `func_150898_a` (its SRG name) appears in
`BlockController`, `BlockDrawers`, `BlockFramingTable` and others. So the mod asks for the right name and the runtime
does not resolve it; the MCP name in the crash text is Cleanroom's own deobfuscation of the trace, not the caller's
reference. The remaining candidates - Cleanroom's patched `Item` not carrying that static, or the call being
transformed by a coremod on the way in (Fugue is installed and exists to patch incompatible mods) - were **not**
separated, and this row does not claim one.

What it does establish is the practical fact for the cohort: **a third-party 1.12.2 mod cannot be assumed to load
here**, so every candidate needs a load check before it is used as an acceptance vehicle. Cleanroom's own
[incompatibility list](https://github.com/CleanroomMC/Cleanroom/issues/15) and the
[modpack preparation wiki](https://github.com/CleanroomMC/cleanroom-website/blob/main/docs/wiki/end-user-guide/preparing-your-modpack.md)
list the known cases (Scala/javax/ASM classes, and mods superseded by Fugue-patched forks); this one is not among
them, so it is either a Fugue gap or a genuinely incompatible mod.

**Action taken**: both jars were removed from the shared Prism instance immediately (a crashing mod breaks every
leg), and the instance was re-verified: 6 mods locked, `fixtureStatus=PASSED`, `presentedImageVerdict=PASSED`,
`worldFrames=153` (leg `cohort-healthcheck`, jar `239b59cf5071c371`). The jars are kept in `.celtium-work/cohort/`
with their hashes above so a future attempt does not have to re-download them.

**Next for this coverage**: either a mod published for Cleanroom (the instance's own set - HEI, Fugue, flare,
scalar, LagSpikeProfiler - is the known-good cohort), or an in-repo test mod built against the same Forge, which
would give TESR/custom-model/particle coverage under our control. The choice is recorded here before it is acted on.
