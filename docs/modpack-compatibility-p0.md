# Full-modpack P0: E2E and RLCraft (2026-10-14)

**Status: official packs obtained and their CurseForge manifests fingerprinted; full client game instances NOT installed, and no complete-pack GL-less acceptance yet.** The earlier successful 7-mod cohort is *not* a full pack compatibility claim. Owner issues: [#82 E2E](https://github.com/souta-lab/celtium/issues/82), [#83 RLCraft](https://github.com/souta-lab/celtium/issues/83). Experimental native shadow/shaderpack features remain an independent track [#76](https://github.com/souta-lab/celtium/issues/76).

## Official reference archives (kept only in local fixture storage)

| Pack | Official CurseForge release | Archive SHA-256 | Required manifest `(projectID,fileID)` records | Forge loader | Config overrides | Embedded JARs outside manifest |
|---|---|---|---:|---|---:|---:|
| [E2E 1.93a](https://www.curseforge.com/minecraft/modpacks/enigmatica2expert/files/9088147) | Project `282744`, file `9088147`, 2026-10-07 | `feb355136a644191bcb2f9f68f2ac30987e1c5485290acc09f4b3bf2a08832ae` | **270** | `forge-14.23.5.2860` | 1,095 config files (1,204 entries) | 1 `overrides/mods/*.jar` |
| [RLCraft 2.9.3](https://www.curseforge.com/minecraft/modpacks/rlcraft/files/4612979) | Project `285109`, file `4612979`, 2023-06-27 | `5caa25d31f47f4ac69846e4faa741811baa9239804747769f6d54f7b1bbf1291` | **187** | `forge-14.23.5.2860` | 2,205 config files (2,403 entries) | 3 nested/loose `overrides/mods/*.jar` including repository libraries |

**Important distinctions:** The manifest entries are **file references**, not proof all those JARs have been downloaded/loaded. A bundled nested library under `overrides/mods/memory_repo/` is not necessarily an extra Forge Mod. Config paths such as `nothirium.cfg`, `vintagefix.properties`, `foamfix.cfg` and `phosphor.json` do **not** prove those exact Mods were enabled: installed JARs and client logs must decide. Keep a stable baseline from the exact original pack and explicitly document any necessary Cleanroom/renderer substitutions.

**OptiFine is an official manual addition to RLCraft, not an absent Mod (verified externally 2026-10-15).** OptiFine does not appear in the bundled `modlist.html` — but the pack's own
[CurseForge install page for 2.9.3](https://www.curseforge.com/minecraft/modpacks/rlcraft/install/4612979) tells players RLCraft v2.9 "needs the latest Optifine or G5" and links `OptiFine_1.12.2_HD_U_G5.jar`, and the
[RLCraft wiki](https://rlcraft.wiki.gg/) describes the pack as "approximately 185 (186 with Optifine) mods". So absence from the manifest is **not** evidence that the pack is OptiFine-free: OptiFine is a documented, player-installed requirement of the pack. For a Celtium runtime it is a **competing shader/GL pipeline** and is therefore excluded deliberately, and that exclusion is a **substitution this project makes and must state** (P1's "minimal approved renderer substitutions") — not a claim about the pack. Never install it silently, and never report an OptiFine-free run as the pack's own configuration without saying so.

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
this helper reports them as `no-url` rather than silently assigning random JARs. The verified
projectID/fileID resolution step that the earlier revision of this paragraph said was still missing
now exists and is measured in
[RLCraft 2.9.3: key-free pin resolution and disk-safe staging](#rlcraft-293-key-free-pin-resolution-and-disk-safe-staging-2026-10-15)
below; with it the same helper stages all 187 pins. Note also that **10 of those 187 pins are
resource packs, not mods**, so "187-Mod installation" is not the right description of this pack.

## Compatibility release gates — never skip levels

| Gate | What must be demonstrated | Status 2026-10-14 |
|---|---|---|
| P0 exact fixture | Official ZIP SHA, exact files, config/override hashes, original pack/loader identity | **PASS metadata only** |
| P1 complete pack boot | All original content Mods or minimal approved renderer substitutions installed in an isolated profile; actual client starts, world enters, exits cleanly on AMD/Intel | **PASSED (AMD)** - `rlcraft-p1-leg7-recipe`, exit 0, 131.6 s, `fixtureStatus PASSED`, 1184 world frames, 4692 colours, `pictureFrameDelta_world 0`. **Intel NOT TESTED**; E2E 1.93a still reaches a world without a passing picture gate |
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

### E2E 268-mod first Vulkan world: immediate-group MemoryStack exhaustion (2026-10-14)

The full E2E1.93a/Celtium fixture (268 original JARs, Nothirium/Naughthirium
replaced, original Nutrition override, Celtium and validation) **did launch a
Minecraft world** after PR #91 repaired the `CustomMainMenu` fixture trap.
Cleanroom 0.6.13-alpha/Java25 loaded **306 FML Mod IDs**, the integrated server
created the validation world and its modded dimensions, and RADV chose
`AMD Radeon RX 6400` as the native Vulkan device. The first presented world
frame was #9; the fixture recorded **40 world frames and 196 world ticks**.

**That run failed**, exit 255, `NO_CAPTURE`, and had no calibrated world image:
Minecraft reported `java.lang.OutOfMemoryError: Out of stack space` at
`MemoryStack.nmalloc` -> `VulkanDynamicMesh.bindAndDraw` in the item-immediate
pipeline. Zero Vulkan VUIDs occurred **before** the crash; this does not pass the
full pack. The renderer's `drawPendingImmediate` shared one outer LWJGL
`MemoryStack` frame across all queued groups; every group then allocated native
viewport, descriptor-set and vertex-binding arrays through the same stack, but
none of those allocations were released until the entire flush completed. E2E
recorded enough item/model groups to exhaust that stack while drawing.

The targeted fix makes a **nested `MemoryStack.stackPush()` frame for each
individual drawn group**, spanning the viewport and all five immediate pipeline
dispatches, and closes it before per-group accounting. The unchanged outer stack
retains only the reusable 216-byte push-constant block. Vulkan's synchronous
`vkCmd*` record functions consume the temporary native pointer arrays while the
nested frame is alive. `scripts/cpu-contract/immediate_group_stack_scope_check.py`
ensures the scope still encloses viewport and all pipeline families. Verify on
real AMD and Intel devices, with world frames and actual presented screenshots,
**before** claiming the original crash is resolved or E2E compatible.

### RLCraft 2.9.3: key-free pin resolution and disk-safe staging (2026-10-15)

Everything in this section is **measured** on the genuine archive
`RLCraft-2.9.3.zip` (SHA-256 `5caa25d31f47f4ac69846e4faa741811baa9239804747769f6d54f7b1bbf1291`,
re-checked by the tools below and recorded in the resolve index). No API key, account or token is
involved anywhere. The archive was only ever read.

#### The problem, restated from the bytes

The manifest's 187 required entries are bare `{projectID, fileID, required}`: **0 of 187 carry a
`downloadUrl`**. The bundled `modlist.html` lists **188 project links - 178 under `mc-mods` and 10
under `texture-packs` - and contains no file ID at all**, so it cannot name a pinned file, and it is
not even a 1:1 map of the 187 pins.

#### What each candidate route actually answers

`scripts/modpack-compat/probe_cf_resolution.py` re-runs every row below live; it issues read-only
requests only. Last run: **13/13 rows matched**, and the raw observations were written to a JSON
alongside the fixture.

| Route | Observed | Verdict |
|---|---|---|
| `api.curseforge.com/v1/mods/{p}/files/{f}/download-url`, no key | **403** | rejected |
| same URL with a deliberately wrong `x-api-key` | **403** | key-gated, not header-gated; a real key needs user approval |
| `www.curseforge.com/.../files/{f}`, ordinary user agent | **403** (0/10 repeats succeeded) | Cloudflare interstitial |
| same URL **plus** `Sec-Fetch-Dest: document` and `Sec-Fetch-Mode: navigate` | **200** (6/6), body contains the exact file name | first-party, but needs a project *slug* |
| same, wrong slug or wrong file id | **404** | the slug is load-bearing, so the pin alone is not enough |
| `edge.forgecdn.net/files/{f//1000}/{f%1000}/{exact name}` | **200** | the artifact route |
| same path, any other name | **403** (`AccessDenied` from S3/CloudFront) | the numeric path alone is not enough |
| `mediafilez.forgecdn.net/?list-type=2&prefix=files/` | **403** | no bucket listing to enumerate names from |
| `addons-ecs.forgesvc.net/api/v2/addon/{p}/file/{f}` | no answer (URLError) | retired |
| `api.cfwidget.com/{projectID}` | **200** | key-free metadata, adopted |

#### Adopted route

`scripts/modpack-compat/cf_file_index.py` reads each pin's exact file name from the public
`api.cfwidget.com` index - one GET per distinct project, 1 s apart, retried once on 429/5xx - and
builds the **official CurseForge CDN** URL from the pin and that name. Result on the real archive:

```
pins      : 187 required entries, 187 distinct projects
resolved  : 187 of 187 pins (286.8 MiB reported by upstream)
by state  : {'ok': 187}
by kind   : {'jar': 177, 'other': 10}
```

**Where the trust ends.** The *artifact* always comes from the official CDN, and the CDN is itself
the verifier of the name: the path encodes the file ID, so a wrong or stale name cannot answer 200.
Every staged byte is additionally size-checked against the resolved size, and a disagreement keeps
nothing. Independently, `probe_cf_resolution.py --index` re-fetched 12 sampled file pages from the
**first-party** site and **12/12 contained the resolved name verbatim**. The third party supplies
metadata only; it cannot substitute a different artifact without a 403.

#### Ten of the 187 pins are not mods

They resolve to resource packs - `Wilhelm.zip`, `Emis_Rlcraft.zip`, `LycanitesMobs32X.zip`,
`Leviosa.zip`, `RLTooltips.zip` and five more - and they are exactly the ten `texture-packs` links in
`modlist.html`. Two earlier readings are corrected here: the pack's 187 manifest entries are
**177 mods + 10 resource packs**, and a `.zip` pin is not an "unsafe" name. `pack_stage.py` now
classifies pins by `kind` and **never writes a resource pack into `mods/`**; with
`--resourcepacks-out` it fetches them into that directory under the same budget and atomicity rules,
and without it, it reports them as not staged and exits **5** rather than 0.

#### Staged on the real archive

`--budget-gb 2`, the 1 GiB free-space floor never approached, every download streamed under a hard
byte cap and renamed only when complete:

| | artifacts | bytes |
|---|---:|---:|
| mod jars to `mods/` | 177 | 287,034,329 |
| resource packs to `resourcepacks/` | 10 | 13,730,766 |
| **total** | **187** | **300,765,095** (286.8 MiB) |
| unresolved / failed / not staged | 0 / 0 / 0 | - |

The staged total equals the upstream-reported total to the byte, which is only possible because all
187 individually size-matched. A second, verification pass over the landed tree reported
`187 already present, 0 not fetched`, re-derived all 187 SHA-256 values, and left free space at
26 GiB (the staging itself costs 287 MiB; the larger change during the window came from other agents
freeing disk, not from this run). All 187 landed files are readable ZIP archives, no basename
collides, and 162 distinct `modid`s were read out of their `mcmod.info` files. The transformer
surface read from the landed jars: **41 coremods, 41 with access transformers, 1 with a mixin
config**.

#### The bundled `overrides/mods/` jars are not manifest pins

The archive ships exactly three jars under `overrides/mods/`: `antiquecities-1.2.1.jar` (a real mod
jar, `mcmod.info` modid `antiquecities`) and two Maven-layout libraries under `memory_repo/`
(`com.github.mcjty:mcjtytools:0.0.21` and `net.ilexiconn:llibrary-core:1.0.11-1.12.2`). Each
`memory_repo` jar has a sibling `.jar.meta` whose `Maven-Artifact:` header names that coordinate,
and the `llibrary-core` one also carries `FMLCorePlugin:
net.ilexiconn.llibrary.server.core.plugin.LLibraryPlugin` plus per-class `SHA-256-Digest` entries.
**None of them is one of the 187 pins**, no file inside the pack references `memory_repo`, and they
belong to the `overrides/` tree rather than to the pin set - so a staging run must not count them as
downloaded mods, and it must not "fix" their absence from the manifest by inventing pins for them.
`overrides/` carries 3,381 files across 3,721 ZIP entries. Counts in this document are **files**;
an earlier revision printed ZIP *entry* counts and called them files, which inflated every number:

| tree | entries (incl. directories) | files |
|---|---:|---:|
| `config` | 2,403 | 2,205 |
| `resources` | 939 | 816 |
| `structures` | 343 | 338 |
| `mods` | 16 | 5 (3 jars + 2 `.jar.meta`) |
| `scripts` | 10 | 9 |
| `resourcepacks` | 2 | 1 (`RLHats.zip`) |
| root files | 8 | 7 |

The authoritative numbers are the lock files themselves: `lock_archive.py` counts files, so
`docs/modpack-locks/e2e-1.93a.lock.json` records `configFileCount: 1095` and
`docs/modpack-locks/rlcraft-2.9.3.lock.json` records `configFileCount: 2205`. Any prose that
disagrees with those is wrong.

Extracting that tree is a separate step that `pack_stage.py` does not perform.

#### Reproduce

```sh
# What does each route answer today?
python3 scripts/modpack-compat/probe_cf_resolution.py \
  --index docs/modpack-locks/rlcraft-2.9.3.resolve-index.json --sample 12

# Resolve all pins (network); --cache-dir makes later runs offline, --offline uses that cache
python3 scripts/modpack-compat/cf_file_index.py --pack /private/RLCraft-2.9.3.zip \
  --out docs/modpack-locks/rlcraft-2.9.3.resolve-index.json --cache-dir /private/cf-cache

# Size first, then stage; the archive is read-only throughout
python3 scripts/pack_stage.py --pack /private/RLCraft-2.9.3.zip --out /private/rlcraft-stage \
  --budget-gb 2 --resolve-index docs/modpack-locks/rlcraft-2.9.3.resolve-index.json --dry-run
python3 scripts/pack_stage.py --pack /private/RLCraft-2.9.3.zip --out /private/rlcraft-stage \
  --budget-gb 2 --resolve-index docs/modpack-locks/rlcraft-2.9.3.resolve-index.json \
  --resourcepacks-out /private/rlcraft-stage/resourcepacks \
  --fingerprint-report docs/modpack-locks/rlcraft-2.9.3.stage-fingerprints.json

python3 scripts/modpack-compat/test_cf_file_index.py
python3 scripts/modpack-compat/test_pack_stage_resolve.py
python3 scripts/cpu-contract/pack_stage_check.py
```

The two committed artifacts hold numeric pins, file names, sizes, CDN URLs and SHA-256 digests
only - no JAR bytes, no config text, no local paths:

- `docs/modpack-locks/rlcraft-2.9.3.resolve-index.json` - the resolution, anchored to the archive
  SHA-256.
- `docs/modpack-locks/rlcraft-2.9.3.stage-fingerprints.json` - per-artifact `kind`, bytes and
  SHA-256, plus the classification of anything that did not land.

**This is still not a P1 pass.** Resolving and staging every pinned artifact says the fixture can be
assembled; it says nothing about whether RLCraft boots or renders under Cleanroom/Celtium, which
remains `NOT TESTED` in the gate table above.

### RLCraft 2.9.3 P1: the client boots GL-less and does not reach a world (2026-10-15)

Five legs, one variable at a time. Every number below is read from a leg's own harness report, game
log or run directory; none is inferred. The control is the same harness and the same generation of
Celtium booting the 268-mod E2E cohort to a world in 194.8 s (`gv-e2e-268-step4`, exit 0,
`nativeDeviceName = AMD Radeon RX 6400`, `gpuRequirementMet = true`).

| leg | configuration | result | what it ruled out |
|---|---|---|---|
| `rlcraft-p1-leg1` | all 178 pack jars | **exit 255, 15.2 s**, `MISSING_REPORT`, `NoClassDefFoundError: RenderGlobal` | - |
| `rlcraft-p1-leg2-bisect` | − EntityCulling, particleculling, BlockOverlayFix | **exit 255, 15.9 s**, same failure | those three are not the cause |
| `rlcraft-p1-leg3-renderlib145` | RenderLib **1.2.8 → 1.4.5** | **exit 255, 21.7 s**, but the `RenderGlobal` failure is **gone**; new `VerifyError` in `EntityRenderer.func_78484_h` (`addRainParticles`) | RenderLib 1.2.8 is the first blocker |
| `rlcraft-p1-leg4-no-renderlib` | − RenderLib, − EntityCulling | **exit 255, 16.3 s** | **invalid leg**: EntityCulling references `meldexun/renderlib` in 16 classes, so removing RenderLib breaks its dependants instead of testing anything |
| `rlcraft-p1-leg5-rl145-no-ss` | RenderLib 1.4.5, − SereneSeasons | **exit 143, 1200.8 s**, `MISSING_REPORT` | **invalid as a P1 measurement** - FML halted on an error screen (Forgelin blacklisted) and no window renderer was created; see the retraction below |
| `rlcraft-p1-leg6-recipe` | Cleanroom recipe applied | **exit 1, 0 s** | **invalid leg** - the profile was not in the `instances/<name>/` layout the harness expects, so it never launched |
| `rlcraft-p1-leg7-recipe` | recipe + RenderLib 1.4.5 + Recurrent Complex 1.4.8.7, SereneSeasons **restored** | **exit 0, 131.6 s, `fixtureStatus PASSED`** | - |

#### Blocker 1 - RenderLib 1.2.8's coremod transformer removes our injection point

`RenderLibClassTransformer` is an `IClassTransformer`, so it runs **before** Mixin, and it names
`RenderGlobal#renderEntities(Entity, ICamera, float)` directly. RLCraft 2.9.3 pins RenderLib
**1.2.8**; the E2E cohort carries **1.4.5**, and only 1.4.5's transformer also carries the SRG name
`func_180446_a`. With 1.2.8 the class Mixin receives no longer matches, and Celtium's
`@Inject(method = "renderEntities", at = @At(INVOKE, RenderHelper.enableStandardItemLighting,
ordinal = 1))` reports:

```
Critical injection failure: Callback method sodium$renderTileEntities(...) in
mixins.celeritas.json:core.terrain.RenderGlobalMixin from mod celeritas failed injection check,
(0/1) succeeded. Scanned 0 target(s). No refMap loaded.
```

Vanilla `RenderGlobal.renderEntities` (`mc-sources.sh` -> `RenderGlobal.java:574`) has that call
twice, at lines 696 and 713, so the injection is correct against the original; something rewrote the
method before Mixin saw it. Substituting 1.4.5 removes exactly this failure.

#### Blocker 2 - SereneSeasons' ASM patch produces a VerifyError under Cleanroom

With 1.4.5 in place the run gets 6.5 s further and dies differently:

```
java.lang.VerifyError: Bad type on operand stack
  Location: net/minecraft/client/renderer/EntityRenderer.func_78484_h()V @289: invokestatic
  Reason: Type 'WorldClient' (stack[1]) is not assignable to 'Biome'
```

`func_78484_h` is `EntityRenderer.addRainParticles`. This is **not a Celtium bug**: CleanroomMC's
own tracker carries the same VerifyError in the same method with Serene Seasons 1.2.18 on
Cleanroom 0.3.35-alpha ([Fugue #118](https://github.com/CleanroomMC/Fugue/issues/118)), the thread
concludes the ASM patch should be disabled, and Serene Seasons later **removed** that transformer
upstream. SereneSeasons 1.12.2 exposes **no config toggle** for it - `config/sereneseasons/` holds
only `seasons.cfg`, `cropfertility.cfg` and `biome_info.json`, all gameplay settings.

#### Leg 5 is retracted as a P1 measurement: it was an FML error screen

A first reading of leg 5 concluded "the client boots GL-less but is limited to ~2.3 s/frame by the
stub's no-op path". **That reading is withdrawn.** Reading the log to the end shows three setup
errors, and the leg measures the harness, not the pack:

1. **FML halted before any world.** Cleanroom blacklists Shadowfacts' Forgelin's loading plugin:

   ```
   [Cleanroom]: Loading plugin net.shadowfacts.forgelin.preloader.ForgelinPlugin for mod
                Forgelin-1.8.4.jar is in the forge_early.cfg blacklist and won't be loaded.
   [FML]: MissingModsException: Mod bountiful (Bountiful) requires [forgelin@[1.8.0,1.9.0)]
   [FML]: MissingModsException: Mod librarianlib (LibrarianLib) requires [forgelin@[1.8.0,)]
   [FML]: An exception was thrown, the game will display an error screen and halt.
   ```

   The 19 minutes were that error screen. `Forgelin-1.8.4.jar` is the right version for the pack but
   **does not run under Cleanroom**, which is precisely why the migration recipe removes it and adds
   Forgelin-Continuous. The recipe is therefore not optional for this pack.

2. **The window renderer was never created.**
   `CELTIUM_WINDOW_RENDERER created=false attempt=1..8`, reason
   `Vulkan device creation failed at window creation: IllegalStateException: Validation was
   requested with -Dceltium.vulkan.validation=true, but the layer VK_LAYER_KHRONOS_validation is not
   installed.` The leg passed the validation flag without `VK_LAYER_PATH`, so no renderer existed.

3. **The frame counter was not advancing.** `CELTIUM_FRAME_PASS_SPLIT frames=512` appears **351
   times** - it reached 512 at 01:23:42 and never moved again - while `CELTIUM_SWAPPOINT calls` grew
   from 1 to 70,500. So the run was repainting the error screen, not drawing a loading screen slowly.
   `CELTIUM_DRAWARRAYS ... refused=524288` and `CELTIUM_NOOP_CENSUS noopCalls=210925038` describe
   that error screen and are **not** evidence of a throughput limit.

The harness still reports `exitCode 143`, `endedBy time-limit`, `elapsedSeconds 1200.8`,
`fixtureStatus MISSING_REPORT`, `screenshotSaved false`, `nativeDeviceName null`,
`gpuRequirementMet false`, `validationMessageCount 0` - and those numbers are what a leg with a
halted FML and no renderer looks like. `CELTIUM_SPLASH_HOOK fixture call failed:
ClassNotFoundException: ValidationClient` has the same cause: the class *is* in the run's
`celtium-validation-client-0.0.4-alpha.jar` (16 entries match), but FML halted before the mod class
loader was assembled.

#### P1 verdict: PASSED on AMD

Leg 7 applied the Cleanroom recipe, RenderLib 1.4.5 and Recurrent Complex 1.4.8.7, **restored
SereneSeasons** (it is not in the recipe), dropped the validation flag, and carried
`resources`/`structures`/`scripts` in with `--copy-pack-dir`. Harness report
(`rlcraft-p1-leg7-recipe`):

| | |
|---|---|
| exitCode / endedBy | **0 / process-exit** (not a duration timeout) |
| elapsedSeconds | **131.6** |
| **fixtureStatus** | **PASSED** |
| nativeDeviceName | **AMD Radeon RX 6400 (RADV NAVI24)** |
| gpuRequirementMet | **True** |
| validationMessageCount / validationClean | 0 / True |
| run directory | 167 top-level jars, 181 files hashed (`rlcraft-p1-leg7-recipe-mods.sha256`) |

Fixture report (`logs/celtium-validation.json`): `status PASSED`; FML
`successfully loaded 184 mods`; `CELTIUM_NATIVE_DEVICE name="AMD Radeon RX 6400 (RADV NAVI24)"`;
`captureWorldFrames 1184`; `firstPresentedWorldFrame 2`; `captureDimension 0` at 1280x720;
`presentedDistinctColours 4692`; `presentedBlackFraction 0.008033`;
`presentedBackgroundColour 0x571716`; `pictureEvidenceSource presented-dump`;
**`pictureFrameDelta_world 0`** - the presented counter tracks world frames exactly, so the
world/presented deficit seen in the E2E cohort does not appear here; four presented images kept
(`loading`, `early`, `tick200`, `world`, each `-kept.png` with its identity JSON);
`capabilitiesBackendAgreesWithLive True` with `capabilitiesBackendSource live renderer: vulkan`;
`cameraHeld True`; `frameIntervalP50Ms 39.5`. `pictureScreenshotUsable_world False` is correct
behaviour, not a failure: under the GL-less stub there is no GL default framebuffer to read, so the
presented dump is this configuration's picture.

| P1 requirement | Measured |
|---|---|
| actual client starts | **YES** - FML loads 184 mods, window renderer created on attempt 1 |
| world enters | **YES** - 1184 world frames, presented images written |
| exits cleanly | **YES** - `exitCode 0`, `process-exit` |

**AMD only.** No Intel leg has been run for RLCraft, so the gate table records
`PASSED (AMD) / Intel NOT TESTED`.

`nativeDeviceName = null` and `gpuRequirementMet = false` in **leg 5's** report are **not** evidence
about the GPU: the capability report is written once a world is entered, and leg 5 never entered one.
That leg was launched with `--require-gpu 'RX 6400'`, so its requirement check is vacuous. Nor does
its game log carry a `CELTIUM_NATIVE_DEVICE` line - grepped, **0 occurrences** - so leg 5 holds
**no device evidence at all**, and none should be read out of it. A leg without a world report
cannot judge GPU requirement in either direction.

#### Substitutions this pack needs, with reasons

Recorded the way the E2E Nothirium exclusion is recorded - a substitution, never a silent edit:

1. **RenderLib 1.2.8 -> 1.4.5** (`eeca6dc4ef5acf3069e4…`; the pack pins `4c27f9e965f4613ec384…`).
   Reason: 1.2.8's `IClassTransformer` rewrites `RenderGlobal.renderEntities` before Mixin and
   destroys the injection point Celtium needs. 1.4.5 is the version the E2E cohort already runs
   against the same injection.
2. **SereneSeasons: NOT excluded in the end.** Leg 3's VerifyError came from its `addRainParticles`
   ASM patch, but leg 7 restored it under the recipe and **the VerifyError did not recur** - so the
   clash was with RenderLib 1.2.8, not with SereneSeasons on its own, and no gameplay is lost. The
   upstream report (CleanroomMC/Fugue#118) still stands as the reason that patch is fragile.
3. **Recurrent Complex 1.4.8.4 -> 1.4.8.7** (`f0019fd07cfea901c2e52d…`; the pack pins
   `81457c01f6113b347dbd…`). Reason: the pack's 1.4.8.4 aborts mod loading with
   `ClassFormatError: Class name is empty or contains illegal character in descriptor in class file
   ivorius/reccomplex/RecurrentComplex`. This is the migration recipe's "Recurrent Complex: delete
   and re-fetch" item.
4. **Shadowfacts' Forgelin 1.8.4 -> Forgelin-Continuous 2.4.20.0.** Reason: Cleanroom blacklists
   `net.shadowfacts.forgelin.preloader.ForgelinPlugin` in `forge_early.cfg`, so `forgelin` never
   registers and `bountiful` and `librarianlib` both abort the load with `MissingModsException`.
   **Required, not optional**: without it the client never gets past the FML error screen.

The Cleanroom pack-migration notes' RLCraft section lists the wider substitution set (remove
AttributeFix, Born in a Barn, BreakBedBegone, Block Overlay Fix, Entity Culling, FoamFix, Frame Void
Patch, HelpFixer, JEI, LibrarianLib, MixinBootstrap, MixinCompatibility, Quark, Phosphor,
PortalDupeBegone, Forgelin, Spark, Surge, Toast Control; add Fugue, Scalar Legacy, Had Enough Items,
LibrarianLib-Continuous, Forgelin-Continuous, Quark: RotN Edition; update RenderLib, Recurrent
Complex, RLTweaker). It is recorded in
`docs/modpack-locks/rlcraft-2.9.3.cleanroom-recipe.json` and **was measured in leg 7**, which is what produced the P1 pass; the two
mods missing locally were resolved through the same key-free cfwidget -> official-CDN route as the
staging work and size-verified exactly. Two of its items are already confirmed by measurement here -
EntityCulling (whose removal breaks its RenderLib dependants) and RenderLib (1.2.8 -> 1.4.5) - and
a third, Forgelin, is the reason leg 5 halted.

