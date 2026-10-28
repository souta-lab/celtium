# Mods that do not work with Celtium

Known failures and refusals, with the reason. Verified mods are in [verifiedmods.md](verifiedmods.md).

Last updated: 2026-10-28.

## Refused at startup (competing renderers)

Celtium replaces Minecraft's renderer. These mods replace the same parts, so the game stops at startup with a message
naming them. Remove them from the instance; Celtium provides the corresponding function natively.
(`-Dceltium.allowCompetitors=true` skips the check for diagnosis only.)

| Mod | What it replaces |
| --- | --- |
| Custom Loading Screen | The Forge loading screen |
| SmoothFont | The font renderer's glyph drawing (also a `VerifyError` against Celtium's text path) |
| Gnetum | The HUD's drawing (its framebuffer kept the HUD out of the frame) |
| Nothirium | The terrain renderer |
| Naughthirium | The terrain renderer |

## Unsupported

| Mod | Problem | Use instead |
| --- | --- | --- |
| Alfheim | Its client light pass re-marks the same chunk sections for rebuild every tick (~80,000 marks a second in E2E), so distant terrain never settles. Celtium logs a warning at startup. | [Pulsar Lighting Engine](https://modrinth.com/mod/pulsar-lighting-engine) |
| Celeritas | Celtium is a replacement for it. Install one, not both. | — |
| Shader packs / OptiFine shaders | Not part of Celtium. Shadows and shader packs are planned as a separate addon (a Demonica fork). | — |

## Known rendering gaps

| Mod | Gap | Status |
| --- | --- | --- |
| Enigmatica 2: Expert 1.93a (pack) | Beyond the render distance, below the horizon, the frame is black where Celeritas shows the fog colour (the superflat fixture world, player at y=12 under the horizon). 10 % of the capture is black, which fails the image gate (`FAILED_PRESENTED_IMAGE`, Vulkan validation clean, leg `e2e-amd-c4`). The terrain fog colour is right; the black is the region vanilla fills with the fogged lower-sky plane or the fog-coloured clear. | Open |
| Xaero's Minimap (in E2E-E) | Entity icons on the radar are missing. (The player arrow, which the depth mask used to reject, is fixed after 0.1.1-alpha.) | Open |

## Not working because of the platform (not Celtium)

These fail on Cleanroom 0.7.11 with or without Celtium (checked against the Celeritas reference). They are listed so a
failure is not blamed on the renderer.

| Mod / pack | Problem | Workaround |
| --- | --- | --- |
| RLCraft 2.9.3 | Does not start on Cleanroom (`VerifyError`) even without Celtium | — |
| ThaumcraftFix 1.1.11 | `IllegalAccessException` on its package-private `@Config` class | Make the class public (local patch); fixed in Fugue 0.25 |
| Trinity 1.5.h | `NoClassDefFoundError`: Cleanroom 0.7's `EventBus` resolves private ICBM lambdas | Strip the lambdas (local patch); upstream PR CleanroomMC/Cleanroom#720 |
| Botania Tweaks (via Fugue's reflection list) | `NoClassDefFoundError` for `com.cleanroommc.hackery.Reflection` (Cleanroom 0.6 only) | Empty Fugue's "sun.misc.Reflection Patching List"; upstream PR CleanroomMC/Fugue#136 |
| CodeChickenLib 3.3.8 | Calls `ReflectionHackery`, absent in Cleanroom 0.7 | Use 3.3.10 |
| ZenUtils 1.28.6 | Reflects on a Cleanroom 0.6-only Foundation field | Use 1.28.8 |
