# RLCraft leg 5 is an invalid leg: three configuration errors, no product finding (2026-10-15)

`rlcraft-p1-leg5-rl145-no-ss` (RenderLib 1.4.5, SereneSeasons removed, the rest of the pack unchanged) was first
read as "the client starts, GL-less works, but rendering is throughput-bound at 2.3 s/frame and the world is never
reached". Reading the leg's own log to the end shows that reading is wrong. There are three separate leg-level
causes, and none of them is a renderer finding.

## 1. FML halted to an error screen - the world was never reachable

```
[01:23:16] [Cleanroom]: Loading plugin net.shadowfacts.forgelin.preloader.ForgelinPlugin for mod Forgelin-1.8.4.jar
            is in the forge_early.cfg blacklist and won't be loaded.
[01:23:29] [Client thread/FATAL] [FML]: net.minecraftforge.fml.common.MissingModsException: Mod bountiful (Bountiful) requires [forgelin@[1.8.0,1.9.0)]
[01:23:29] [Client thread/FATAL] [FML]: net.minecraftforge.fml.common.MissingModsException: Mod librarianlib (LibrarianLib) requires [forgelin@[1.8.0,)]
[01:23:29] [Client thread/FATAL] [FML]: An exception was thrown, the game will display an error screen and halt.
```

Cleanroom **blacklists Shadowfacts' Forgelin's loading plugin** (`forge_early.cfg`), so Forgelin never registers,
`bountiful` and `librarianlib` fail their dependency check, and FML halts. The staged `Forgelin-1.8.4.jar` is the
right version for the pack (`modid forgelin`, `version 1.8.4`) and still cannot work here. This is exactly the
migration recipe's "remove Shadowfacts' Forgelin, install Forgelin-Continuous" step, which means **that step is a
prerequisite for RLCraft booting at all**, not an optional optimisation. The 19 minutes that were read as a slow
loading screen were FML's error screen.

## 2. The window renderer was never created

```
CELTIUM_WINDOW_RENDERER created=false attempt=1..8 ticks=0 stage=createDisplay
  reasons=[Vulkan device creation failed at window creation: IllegalStateException:
           Validation was requested with -Dceltium.vulkan.validation=true,
           but the layer VK_LAYER_KHRONOS_validation is not installed.]
```

The leg requested validation without providing the layer path (`VK_LAYER_PATH` to the extracted
`explicit_layer.d`), so Vulkan device creation failed and the renderer was never created - eight attempts, all
refused. A leg in that state cannot say anything about rendering throughput.

## 3. The "2.3 s/frame" figure came from a counter that had stopped

`CELTIUM_FRAME_PASS_SPLIT frames=` reached **512 at 01:23:42** and stayed at 512 for the remaining 19.5 minutes
(all 353 report lines carry the same value), while `CELTIUM_SWAPPOINT calls=` kept rising (600 -> 36300). So the
frame counter was not measuring a slow frame rate; it had stopped advancing because the game was sitting on FML's
error screen. The 210,925,038 no-op calls and the `refused=524288` draw-array count are the cost of drawing that
screen, not evidence of a throughput limit in the GL-less path.

## What the leg does still show

- `CELTIUM_GLLESS stub GL provider installed`, `window hinted GLFW_NO_API`, `splash progress skipped (no shared GL
  context)`: the GL-less stub **was** installed in a 177-mod RLCraft pack, unlike the 268-mod E2E pack - the
  cross-check that supports the RandomPatches finding.
- `validationMessageCount=0`, `validationClean=true`, and **no crash report at all**: nothing crashed.
- `fixtureStatus=MISSING_REPORT`, `nativeDeviceName=null`, `gpuRequirementMet=false` - all consequences of never
  reaching a world, and none of them usable as a GPU acceptance.

## The next RLCraft leg has to be built differently

Apply the platform's recipe (remove 19, install 6 - **Forgelin-Continuous in particular**), keep RenderLib 1.4.5,
**put SereneSeasons back** (it is not on the platform's list, so removing it needs its own justification), and
**drop `-Dceltium.vulkan.validation=true` or pass the layer path**. The GL-less fix in PR #128 is not needed for
that leg: its failure causes are elsewhere, so the recipe should first be exercised on plain `main`.

A `ClassNotFoundException: org.soutalab.celtium.validation.ValidationClient` in the splash hook is also explained by
this: the run's `mods/celtium-validation-client-0.0.4-alpha.jar` **does** contain the class, so the fixture simply
never had a working classloader because FML had halted.
