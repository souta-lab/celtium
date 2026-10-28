# Verified mods

Mods and modpacks that have been run with Celtium on the test machines (AMD Radeon RX 6400 and Intel UHD Graphics 630,
Linux/Mesa, Cleanroom 0.7.11-alpha) and checked on the presented Vulkan image. Each entry says **what** was checked;
anything not listed here is untested, not "probably fine". Mods that fail or are refused are in
[notworkingmods.md](notworkingmods.md).

Last updated: 2026-10-28 (0.1.1-alpha).

## Checked individually

The mod's own drawing was compared against Celeritas 2.5.0-dev (OpenGL) on the same instance, scene and camera.

| Mod | Version | What was checked | Evidence |
| --- | --- | --- | --- |
| Xaero's Minimap | 25.3.13 | Minimap frame, map, compass and coordinates on the QMAGNET world; AMD and Intel PASSED, Vulkan validation clean | [plan.md](docs/plan.md) (Xaero row) |
| Xaero's World Map (+ xaerolib) | 1.40.16 (lib 1.1.0) | Map tiles in the minimap (four-texture-unit combine, `e57b51421`); matches Celeritas on AMD and Intel. The player arrow is drawn after 0.1.1-alpha (it was lost to a depth-rounding mismatch with the minimap's mask) | [image](docs/evidence/2026-10-28-xaero-worldmap-minimap.png) |
| JourneyMap | 5.7.1p3 | Minimap tiles, grid, compass and labels on the qmagnet world (`54b42982d`: tiles are written through raw `glTexSubImage2D`); AMD PASSED. Tile content matches JourneyMap's own saved region image | world-jm leg |
| Had Enough Items (HEI) | 4.31.2 | Item list, search field, creative inventory and tooltip on both GPUs | [mod-compatibility.md](docs/mod-compatibility.md) |
| GVC (GVCLib / GVCR2) | as in the user's instance | Mobs and vehicles drawn through client-array display lists; A4 matches the Celeritas reference (`eeb6b1d0e`). The A4 server crash is GVCR2's own bug, patched by `scripts/fix_gvcr2_a4.py` | [plan.md](docs/plan.md) |
| Fugue | 0.24.4 | Loads; required on Cleanroom 0.7 by several packs | world legs |
| Scalar | 3.10.0 | Loads (needed by the Celeritas reference); world legs PASSED | world legs |
| Pulsar Lighting Engine | 0.5.2 | Used in E2E-E in place of Alfheim; world PASSED | E2E-E legs |

## Checked as a whole pack

The pack starts, enters the fixture's world, and the presented image passes the image gate with Vulkan validation
clean. This is **not** a check of every mod's every feature: a pack-level pass says the pack loads and its world and HUD
draw, nothing more specific.

| Pack | Mods | Result | Notes |
| --- | --- | --- | --- |
| Enigmatica 2: Expert - Extended (E2E-E) | 1.87.3, 421 jars | AMD: fixture PASSED, validation 0 (leg `e2ee-amd-c4`, renderer `e5cef155…`); presented image within 0.25 % of Celeritas over 16 levels | Needs the Cleanroom 0.7 substitutions in `.celtium-work/modpack-e2ee/SUBSTITUTIONS.md` (ThaumcraftFix, Trinity, Fugue lists, ZenUtils, CodeChickenLib) — those are Cleanroom 0.7 issues, present without Celtium too. Custom Loading Screen, SmoothFont, Gnetum and Nothirium are removed (see [notworkingmods.md](notworkingmods.md)) |
| Enigmatica 2: Expert (E2E) | 1.93a, 270 jars | AMD: fixture PASSED (leg `e2e-lod3`, `af1e97fe0`); presented black fraction 0.004, floor drawn to the horizon as in Celeritas. The earlier black band came from the terrain sampling atlas mip levels the game never wrote (the pack runs with mipmaps off) | Nothirium removed; Pulsar replaces Alfheim |

Notable mods inside the E2E-E pass (loaded and present in the world, not individually compared): Thaumcraft 6 and its
addons, Applied Energistics 2 (UEL), Mekanism CE, Thermal series, Immersive Engineering, Tinkers' Construct (Antique),
Botania, Blood Magic, Astral Sorcery, OpenComputers, EnderIO CE, FTB Quests, The One Probe CE, Chisel/CTM,
StellarCore, Xaero's Minimap and World Map.
