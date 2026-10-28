# Image quality: four screens and the QMAGNET world against Celeritas (2026-10-28)

Renderer `celtium-forge-mc1.12.2-0.1.0-alpha.jar` sha256 `0b01d5fb…527164` (source `5f75fc009` plus the
fixture's chat clear, same commit), fixture `4a10b4c1…`. Reference: `01-celeritas-forge-mc12.2-2.5.0-dev.jar` (GL),
same fixture, same instance (`world-0.7.11`, QMAGNET's Test Map), 1280×720, `fixedWorldTime=true worldTime=1500`,
menu held 80 ticks with the menu dump at tick 40, validation layer on for the native arm.
Legs: `iq-amd-cull`, `iq-intel-cull`, `iq-video-celtium` (all PASSED, validation clean, `gpuRequirementMet=true`);
references `iq-amd-celeritas`, `iq-intel-celeritas`, `iq-video-celeritas`.

Picture: [2026-10-28-four-screens.png](2026-10-28-four-screens.png) (half size; left Celtium, right Celeritas).
Metric: per-pixel absolute difference of the full-size captures, greyscale; "over 16" is the share of pixels more
than 16 levels apart.

| Screen | GPU | mean diff | over 16 | over 48 | What differs |
|---|---|---|---|---|---|
| loading | AMD | 0.74 | 0.45 % | 0.45 % | against vanilla's own layout (below); the differing pixels are 1-px glyph edges in the right half only |
| home (menu) | AMD | 7.77 | 15.6 % | 1.3 % | random splash text and the panorama's rotation phase only |
| home (menu) | Intel | 8.55 | 16.4 % | 1.8 % | same |
| settings (video) | AMD | 0.00 | 0.00 % | 0.00 % | pixel-identical |
| game (QMAGNET, tick 200) | AMD | 2.41 | 0.97 % | 0.08 % | see below (7.85 → 4.47 → 2.00 → 0.97 % over the three fixes) |
| game (QMAGNET, tick 200) | Intel | 2.39 | 1.05 % | 0.06 % | see below (4.49 → 2.05 → 1.05 %) |

The game screen's remaining differences, read from full-size crops:

- **Fixed here:** stained glass, ice and slime were drawn with their back faces (the translucent terrain pass had
  culling off), ~20–25 levels darker per channel and order-dependent ([before/after](2026-10-28-stained-glass-backfaces.png)).
  Over-16 share 7.85 % → 4.47 %.
- **Animated textures:** nether portal, water and fire differ by animation phase.
- **Fixed here:** the first-person arm was a uniform ×1.16 brighter. Against the skin texture (Alex, arm
  `(236,212,184)`) the reference is ×0.99 and Celtium was ×1.15: GL clamps the lit colour to [0,1] before the texture
  stages and the lit shaders did not, so a face both lights reach exceeded 1. All three lit shaders now clamp.
  Legs `iq-amd-clamp`, `iq-intel-clamp` (PASSED, validation clean).
- **Fixed here:** face shades (concrete side +11 %, quartz side +22 %, grass path −11 %) came from meshing with the
  Sodium-style `VintageBlockRenderer`, which upstream Celeritas only enables in development; the reference meshes
  through Forge's block-model pipeline. Celtium now does the same by default (legs `iq-amd-forgepipe`, `iq-intel-fp`,
  PASSED, validation clean; Intel paired KPI `kpi5-intel` control ratio 0.997, i.e. frame time unchanged).
- **Not a Celtium defect:** one large wall face is flat at 163–165 where the reference falls 157 → 145 left to right.
  `-Dceltium.validation.probePixel` (same answer in both arms) names it white concrete, south face, 21–24 blocks away,
  sky light 15 and block light 4 → 0 across the gradient. The game's own light map row for sky 15 (`lightmapSky15Row`)
  is flat (0xfafafa–0xfcfcfc for every block level), so vanilla's value is texture (207) × south shade 0.8 × 0.98 ≈ 162:
  Celtium's 164 is that value and the reference's gradient is the reference's own. Also ruled out on the way: fog,
  the entity light cache, the mesher, the atlas mip reach.

## Loading screen reference

The GL arm cannot dump the loading frame (the splash hook is Celtium's own mixin and the fixture is not loaded yet), so
the reference is vanilla's own procedure rebuilt from the source (`Minecraft.drawSplashScreen`): GUI scale 3 at
1280×720 (426×240 units), a full-screen quad textured with `mojang.png`'s white texel (0,0), then the 256×256 logo at
((426−256)/2, (240−256)/2) units = (255, −24) px, 768×768 px, nearest filtering. Against leg `load-amd`'s presented
loading frame: mean 0.74, 0.45 % of pixels over 16 levels, all of them 1-px glyph edges in the right half — vanilla
renders the 1278-px-wide framebuffer onto the 1280-px window, a stretch this reconstruction does not model.
