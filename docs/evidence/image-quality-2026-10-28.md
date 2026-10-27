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
| loading | AMD | — | — | — | Forge/Mojang splash drawn; the GL arm has no loading dump, so no reference |
| home (menu) | AMD | 7.77 | 15.6 % | 1.3 % | random splash text and the panorama's rotation phase only |
| home (menu) | Intel | 8.55 | 16.4 % | 1.8 % | same |
| settings (video) | AMD | 0.00 | 0.00 % | 0.00 % | pixel-identical |
| game (QMAGNET, tick 200) | AMD | 3.88 | 4.47 % | 0.22 % | see below |
| game (QMAGNET, tick 200) | Intel | 3.85 | 4.49 % | 0.23 % | see below |

The game screen's remaining differences, read from full-size crops:

- **Fixed here:** stained glass, ice and slime were drawn with their back faces (the translucent terrain pass had
  culling off), ~20–25 levels darker per channel and order-dependent ([before/after](2026-10-28-stained-glass-backfaces.png)).
  Over-16 share 7.85 % → 4.47 %.
- **Animated textures:** nether portal, water and fire differ by animation phase.
- **First-person arm:** a uniform ×1.16 brighter on every channel. The instance's light terms are the vanilla ones
  (ambient 0.4, two diffuse 0.6, both lights enabled; `-Dceltium.debug.modelLightFrame`), and other lit models in the
  scene (armour stand, biped, minecart) match the reference, so the cause is specific to the arm draw. Open.
- **Smooth lighting on some faces:** white concrete side +11 %, quartz pillar side +22 %, grass path top −11 %. The
  reference meshes through Forge's block-model pipeline (JFR: `ForgeBlockModelRenderer`), Celtium through its own
  `SmoothLightPipeline`; an algorithm difference, not yet judged against vanilla.
