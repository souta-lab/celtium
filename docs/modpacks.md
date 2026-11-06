# Modpacks run with Celtium

Every pack here was started with Celtium on the verification harness (`scripts/gv-leg.sh`, mode `e2e` or `world`)
and reached the world. **Goal for every pack: zero Vulkan validation errors and more FPS than Celeritas 2.5.0-dev.**
A row names the pack version, the mod count, the Celtium build, the GPUs and the runs; pack-side substitutions needed
for Cleanroom 0.7 are listed per pack and are never Celtium changes. FPS is only given where a paired KPI was run.

| Modpack | Version | Mods | Cleanroom | Result | Celtium build | Runs |
| --- | --- | --- | --- | --- | --- | --- |
| Supersymmetry | 0.2.0.2 | 188 | 0.7.14 (relauncher) | world, validation 0; **FPS above Celeritas at p50/p95 on AMD and Intel**, p99 at parity (KPI pack) | r107 `5ff004c26` | `l27kpi-amd`, `l27kpi-intel` |
| Enigmatica 2: Expert - Extended | 1.87.3 | ≈430 | 0.7.14 (relauncher) | world, **validation 0 on AMD and Intel**, also with Xaero raw-FBO identity | r116 `d8cb18595`, r117 `0403f9f0b` | `fx1-e2ee-amd`, `fx1-e2ee-intel`, `xr1-e2ee-xaero-amd` |
| SkyFactory 4 | 4.2.4 | 198 | 0.7.14 (relauncher) | world, **validation 0 on AMD and Intel**, sky correct on every dumped frame (a lit-payload bug that turned the sky into a flat band while a mod toast was up was fixed in `76ff182ee`) | r119 `76ff182ee` | `sf6-amd`, `sf6-intel` |
| All the Mods 3 | 6.1.1 | 250 | 0.7.14 (relauncher) | world, **validation 0 on AMD and Intel**, no anomaly in any dumped frame | r119 `76ff182ee` | `atm1-amd`, `atm1-intel` |
| Enigmatica 2: Expert | 1.93a | 270 | 0.7.11 | world on both GPUs (2026-11-05; superseded by E2E-E, instance removed 2026-11-06) | 2026-11-05 build | see evidence |

## Substitutions per pack

- **Enigmatica 2: Expert - Extended 1.87.3**: Fugue 0.24.4 -> 0.25.5, CodeChickenLib 3.3.8 -> 3.3.10, Trinity without
  its `ICBMEvents` hook (no ICBM in the pack); excluded at leg time: SmoothFont, gnetum, CustomLoadingScreen
  (competing renderers), NothiriumLittleTilesPatch, the pack's older relauncher and FermiumBooter (bundled by
  Cleanroom). Detail: [e2e-cleanroom07-2026-10-26.md](<evidence/e2e-cleanroom07-2026-10-26.md>).

- **SkyFactory 4 4.2.4**: Rongmario's gist - removed FastFurnace, FastWorkbench, FoamFix, JEI, Mercurius,
  Shadowfacts' Forgelin, Surge, Toast Control; Mekanism/Generators -> Mekanism CE 9.12.15; Flux Networks 4.1.1.34;
  CraftTweaker 4.1.20.715; added Fugue 0.25.5, Scalar Legacy, Had Enough Items, Forgelin-Continuous; SmoothFont removed
  (competing font renderer). Legs use `-Dceltium.validation.worldType=default -Dceltium.validation.cameraY=100`: the
  fixture's superflat options crash Topography (it parses generator options as JSON), and the default pose is
  underground in a generated world.

- **All the Mods 3 6.1.1**: Rongmario's gist - removed AI Improvements, Bed Patch, BetterFPS, Diet Hopper, FastWorkbench,
  Fence Jumper, FoamFix, HelpFixer, JEI, LibrarianLib, NotEnoughIDs, Quick Leaf Decay, RandomPatches, Shadowfacts'
  Forgelin, Spark, Toast Control, Unloader; added Fugue 0.25.5, Scalar Legacy, Had Enough Items, Forgelin-Continuous,
  Roughly Enough IDs, LibrarianLib-Continuous 4.22-2.0-3. The pinned MineColonies file is gone from CurseForge, so
  1.12.2-0.11.841 comes from the LDT Team maven. Same leg flags as SkyFactory 4.

## Adding a pack

1. Stage the pack's mods with `scripts/pack_stage.py` (pins resolved with `scripts/modpack-compat/cf_file_index.py`),
   apply the Cleanroom migration notes from Rongmario's gist, and record the substitutions under
   `.celtium-work/modpack-<pack>/SUBSTITUTIONS.md`.
2. Add an `E2E_PACKS` preset in `scripts/gv_leg.py` and run `CELTIUM_E2E_PACK=<pack> scripts/gv-leg.sh <leg> <stage> e2e
   <gpu> 2400` on AMD and Intel.
3. A pack goes in the table once it reaches the world; the result column states the validation count honestly.
4. Older copies of a pack are deleted when a newer one replaces them (only the newest set is kept on disk).
