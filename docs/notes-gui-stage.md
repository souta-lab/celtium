
## Round 236 — the two splash questions, from the sources (no leg run)

### Q1: does the menu's full-screen blurred panorama composite run on the loading screen? **NO.**

Every caller of the three steps, from the decompiled game sources:

| symbol | definition | callers |
|---|---|---|
| `renderSkybox` | `GuiMainMenu.java:513` | **only** `GuiMainMenu.java:549` (inside `GuiMainMenu.drawScreen`) |
| `drawPanorama` | `GuiMainMenu.java:388` | **only** `GuiMainMenu.java:517` (inside `renderSkybox`) |
| `rotateAndBlurSkybox` | `GuiMainMenu.java:478` | **only** `GuiMainMenu.java:518-524` — the seven calls, all inside `renderSkybox` |

`scripts/mc-sources.sh --grep 'renderSkybox'` returns exactly two lines (definition + the one call at `:549`);
`--grep 'drawPanorama|rotateAndBlurSkybox'` returns the definitions and their call sites, all inside `renderSkybox`.

**So the composite chain `glViewport(0,0,256,256)` -> `drawPanorama` -> 7x `rotateAndBlurSkybox` -> full-screen quad
has no loading-screen caller at all.** It is the main menu's, and only the main menu's. On the loading screen that
geometry cannot appear, so no group log can show it there - the question is settled by the call graph, and a leg would
only confirm the absence.

**What the bottom-left 256x256 box in `stage-splash-p1-picture-1920x1080.png` therefore is not:** it is not the
panorama composite. Something else is drawing into a 256x256 viewport on that screen, and the named candidates to check
are (a) the vanilla splash's own texture draw, (b) a `glViewport(0,0,256,256)` left set by an earlier draw whose own
geometry is unrelated. **I did not identify it** - see Q2, which is where the same evidence points.

### Q2: which texture does Cleanroom's splash logo come from? **Not identified this round.**

- `scripts/mc-sources.sh --cleanroom --grep 'Splash'` returns **no splash class**: that path lists the Cleanroom patch
  set and Kirino's runtime patch classes (`MinecraftCulling`, `MinecraftEntityRendering`, `MinecraftTESRRendering`), not
  a splash renderer. So the splash renderer is **not** in the patch tree this command exposes, and it is not in the
  decompiled vanilla tree either (vanilla's is `Minecraft.drawSplashScreen` -> `SplashProgress.drawVanillaScreen`).
- The fixture's hook confirms which path our legs take:
  `forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftMixin.java:102` injects at
  `Minecraft.drawSplashScreen` HEAD, and the file's own comment (`:68`, `:81`) names
  `Minecraft.java:517 -> SplashProgress.drawVanillaScreen -> drawSplashScreen`. **That is the vanilla path, and it is
  what our legs draw** - which is why the legs show the vanilla Mojang splash while the user's session runs Cleanroom's
  own.
- **The leg line that would prove the Cleanroom texture, and why I could not take it:** the named mirror dumps
  `mirror-gl<id>-<W>x<H>-l0-n*.png` would name the texture the logo is uploaded to, and
  `CELTIUM_UPLOAD_CALLER id=<n>` with `-Dceltium.vulkan.uploadCallerId=<n>` would name the caller. Neither was run:
  the GPU slot is the Lead's this round and my budget ran out.

### What is needed to close Q2
Find Cleanroom's splash renderer class first (it is in `cleanroom-0.6.13-alpha.jar`, not in the patch set this script
prints - a `javap`/`strings` pass over that jar for `Splash` is the cheapest next step), then one leg on the **user's**
path (Cleanroom's splash, i.e. Forge's splash **enabled**) with `-Dceltium.vulkan.mirrorDrawLog=60` and the named
mirror dumps on, and read which `gl<id>` the logo's 256x256 upload lands on.
