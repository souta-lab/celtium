# Full-modpack P0: E2E and RLCraft (2026-10-14)

**Status: official packs obtained and their CurseForge manifests fingerprinted; full client game instances NOT installed, and no complete-pack GL-less acceptance yet.** The earlier successful 7-mod cohort is *not* a full pack compatibility claim. Owner issues: [#82 E2E](https://github.com/souta-lab/celtium/issues/82), [#83 RLCraft](https://github.com/souta-lab/celtium/issues/83). Experimental native shadow/shaderpack features remain an independent track [#76](https://github.com/souta-lab/celtium/issues/76).

## Official reference archives (kept only in local fixture storage)

| Pack | Official CurseForge release | Archive SHA-256 | Required manifest `(projectID,fileID)` records | Forge loader | Config overrides | Embedded JARs outside manifest |
|---|---|---|---:|---|---:|---:|
| [E2E 1.93a](https://www.curseforge.com/minecraft/modpacks/enigmatica2expert/files/9088147) | Project `282744`, file `9088147`, 2026-10-07 | `feb355136a644191bcb2f9f68f2ac30987e1c5485290acc09f4b3bf2a08832ae` | **270** | `forge-14.23.5.2860` | 1,204 config files | 1 `overrides/mods/*.jar` |
| [RLCraft 2.9.3](https://www.curseforge.com/minecraft/modpacks/rlcraft/files/4612979) | Project `285109`, file `4612979`, 2023-06-27 | `5caa25d31f47f4ac69846e4faa741811baa9239804747769f6d54f7b1bbf1291` | **187** | `forge-14.23.5.2860` | 2,403 config files | 3 nested/loose `overrides/mods/*.jar` including repository libraries |

**Important distinctions:** The manifest entries are **file references**, not proof all those JARs have been downloaded/loaded. A bundled nested library under `overrides/mods/memory_repo/` is not necessarily an extra Forge Mod. Config paths such as `nothirium.cfg`, `vintagefix.properties`, `foamfix.cfg` and `phosphor.json` do **not** prove those exact Mods were enabled: installed JARs and client logs must decide. OptiFine does not appear in the RLCraft 2.9.3 bundled `modlist.html`, so never silently install it for compatibility testing. Keep a stable baseline from the exact original pack and explicitly document any necessary Cleanroom/renderer substitutions.

Official manifest comparison (from the two full distribution ZIPs): **32 common CurseForge project IDs**, of which **23** specify the same exact file ID and **9** specify different file IDs. Unique projects: **238 E2E** and **155 RLCraft**. The precise divergent IDs are reproducible with `scripts/modpack-compat/compare_locks.py`.

## Lockfiles and offline verification

The repository contains only original *numeric references* and cryptographic fingerprints, **not copyrighted Mod JARs, downloaded ZIPs, original config texts, or shader assets**:

- `docs/modpack-locks/e2e-1.93a.lock.json`
- `docs/modpack-locks/rlcraft-2.9.3.lock.json`

After obtaining the genuine original CurseForge ZIPs locally, re-run:

```sh
python3 scripts/modpack-compat/lock_archive.py /private/Enigmatica2Expert-1.93a.zip \
  --project-id 282744 --file-id 9088147 \
  --check docs/modpack-locks/e2e-1.93a.lock.json
python3 scripts/modpack-compat/lock_archive.py /private/RLCraft-2.9.3.zip \
  --project-id 285109 --file-id 4612979 \
  --check docs/modpack-locks/rlcraft-2.9.3.lock.json
python3 scripts/modpack-compat/compare_locks.py \
  docs/modpack-locks/e2e-1.93a.lock.json docs/modpack-locks/rlcraft-2.9.3.lock.json
python3 scripts/modpack-compat/test_lock_archive.py
```

The audit rejects malformed project IDs, invalid loader, unexpected Minecraft version, duplicate IDs, ZIP traversal/symlinks and oversized/decompression hazards, hashes the original download and every override file, and records embedded jar hashes. The CI check uses a **synthetic test ZIP only**; it does not fetch proprietary pack archives or assert client startup.

### Disk-safe Mod download and transformer inventory (PR #85)

After verifying the official ZIP's lock with `lock_archive.py`, use the **offline dry-run** first for
planning. The staging helper (`scripts/pack_stage.py`) may perform HTTPS HEAD requests to estimate
sizes but does **not** write on `--dry-run`. Runtime downloads accept only approved HTTPS
`*.forgecdn.net` URLs, reject archive-derived unsafe file paths and duplicate target names, and
apply a **hard streaming byte cap** even when Content-Length is missing or wrong. A partial file is
never renamed to a complete JAR, and downloads preserve at least 1 GiB disk free.

```sh
python3 scripts/pack_stage.py --pack /private/Enigmatica2Expert-1.93a.zip \
  --out /private/e2e-fixture --budget-gb 2 --dry-run
python3 scripts/pack_stage.py --pack /private/Enigmatica2Expert-1.93a.zip \
  --out /private/e2e-fixture --budget-gb 2
```

`--only` can narrow an investigative group before the full pack; such a subset is NOT an E2E/RLCraft
acceptance. The report `stage-report.json` is generated from the actual landed JARs, listing coremod,
access transformer, and Mixin config metadata. **RLCraft 2.9.3 manifest entries have no `downloadUrl`:**
this helper reports them as `no-url` rather than silently assigning random JARs. Its full 187-Mod
installation requires a verified CurseForge projectID/fileID download resolution step first.

## Compatibility release gates — never skip levels

| Gate | What must be demonstrated | Status 2026-10-14 |
|---|---|---|
| P0 exact fixture | Official ZIP SHA, exact files, config/override hashes, original pack/loader identity | **PASS metadata only** |
| P1 complete pack boot | All original content Mods or minimal approved renderer substitutions installed in an isolated profile; actual client starts, world enters, exits cleanly on AMD/Intel | **NOT TESTED** |
| P2 actual modded rendering | Native Vulkan presented screenshots of distinct Mod model, TESR, particles, liquids, translucent surfaces, entity animation, GUI, equipment and world-structure scenes; non-rendering dependencies untouched | **NOT TESTED** |
| P3 lifecycle | Save/reload, F3+T, dimension transitions, fullscreen/resize, game switch/quit with Vulkan VUID0 and no lingering GPU resource ownership | **NOT TESTED** |
| P4 parity | Correct **same-scene** non-Celtium OpenGL reference screenshots with matched world seed/camera/time/GUI state; enumerate and fix remaining missing pixels/geometry | **NOT TESTED** |
| P5 perf/reliability | Repeatable warm p50/p95/p99 + GPU/CPU measurements, baseline comparison and a non-regressing CI/GPU acceptance matrix | **NOT TESTED** |

### First failing render families to target

**E2E:** Industrial and magic machine TESRs, AE2 terminal and item screens, multiblocks, transparent fluid and conduit draw order, modded dimensions, animated particles and overlays. **RLCraft:** large custom animated creatures and rider/mount geometry, entity lighting, particle effects, modded foliage/weather, sky, translucent water and GUI/HUD overlays. These are test hypotheses, **not observed Celtium bugs**; test genuine installed components and do not infer the contents of a pack merely from its theme.

Full pack testing needs more storage than a manifest inspection. Remote machine at baseline had **~13 GiB free / 16 GiB physical RAM**; avoid duplicating the existing PrismLauncher Cleanroom instance and cache. Stage **one complete pack at a time**, reuse content-addressed downloaded jars, measure disk use and memory headroom, preserve user worlds/configs and do not suppress failing coremods just to record a pass. Never run both bulky clients concurrently.

### E2E renderer substitution (required for Celtium; user policy 2026-10-14)

**Do not install two terrain renderers together.** E2E 1.93a's downloaded upstream
archive pins 270 mods, but a Celtium runtime must exclude exactly the competing
`Nothirium-1.12.2-0.4.9-beta.jar` (CurseForge project `624960`, file `7441674`)
and its exclusively Nothirium-targeted helper `naughthirium-2.3.0.jar` (project
`1153019`, file `6473489`). Celtium **replaces** Nothirium, rather than trying
to run their mutually exclusive terrain renderers together. All **268 remaining
original JARs** must be present. Keep the official E2E ZIP/download cache intact
for provenance; excluding a JAR from the **game's runtime** is not deleting it
from the original archive. This replacement does not prove all pack features work.

`e2e_celtium_mods.py` checks the pinned upstream E2E 1.93a lock and downloader
report, verifies every downloaded file SHA-256, emits only the 268 eligible JARs,
and refuses a runtime that still contains either competing renderer or lacks any
required selected Mod. Example on locally obtained original archives/JARs:

```sh
python3 scripts/modpack-compat/e2e_celtium_mods.py \
  --lock docs/modpack-locks/e2e-1.93a.lock.json \
  --report /private/e2e-staged/stage-report.json \
  --staged-mods /private/e2e-staged/mods \
  --runtime-mods /private/e2e-runtime/mods
```

Test is synthetic and offline:
`python3 scripts/modpack-compat/test_e2e_celtium_mods.py`.

**First actual full-pack runtime evidence (2026-10-14):** the 270-mod + Celtium
attempt failed with Nothirium `MixinRenderGlobal` transformer conflict. The
Celtium-selected 268-mod + original Nutrition override + Celtium + validation
attempt loaded 306 Forge Mod IDs (`Forge Mod Loader has successfully loaded 306
mods`) and reached Celtium's swap-point loop; it did **not** reach a validated
world or record a GPU image. The run ended after 750.9 seconds (`MISSING_REPORT`,
exit 143/time-limit, no world frames). Treat this as a **startup/fixture blocker,
not a Modpack PASS**. Focus next on world/validation flow and actual content
rendering with Nothirium absent, not making two renderer cores coexist.

### E2E/Cleanroom: keep StellarCore, disable only incompatible parallel model loading

The full 268-original-Mod Celtium test on Cleanroom 0.6.13-alpha exposed
StellarCore 1.6.0 `mixins.stellar_core_forge_modelloader.json:MixinModelManager`
`InvalidInjectionException`: its `@At(INVOKE)` on `ModelManager.onResourceManagerReload`
did not match the Cleanroom-patched call site. **StellarCore is not a competing
terrain renderer and should be retained.** Its own configuration documents the
parallel model loader as experimental and incompatible with some model-loading
architectures; set **only** `B:ParallelModelLoader=false`, preserving its
other optimizations and gameplay. Read-only bytecode inspection of
`StellarCoreEarlyMixinLoader` confirms the mixin configuration is conditionally
registered according to this setting. The A/B run at 2026-10-14 22:42 used an
**isolated private profile** with this one config change and logged:

```
[StellarCore-MixinLoader] Mixin config mixins.stellar_core_forge_modelloader.json is disabled by config or mod is not loaded.
```

This proves the exact failing mixin was not registered. **It does not by itself
prove that E2E entered a world or produced correct Vulkan frames**: those need
independent game reports and presented images. For a reproducible fixture, never
edit the original installed config in place. Instead copy and transform a
verified original into a fresh destination profile:

```sh
python3 scripts/modpack-compat/e2e_stellar_parallel_config.py \
  --source /private/original/config/stellar_core.cfg \
  --target /private/new-test-profile/config/stellar_core.cfg
```

The helper refuses missing, ambiguous, oversized, symlinked or already-existing
targets. All other configuration bytes (including CRLF) are preserved. Its
synthetic, offline contract is
`python3 scripts/modpack-compat/test_e2e_stellar_parallel_config.py`.
