# Shaderpack frontend S0/S1 — findings

Issue: https://github.com/souta-lab/celtium/issues/76
Scope: pure-CPU load of OptiFine/Iris-style packs → immutable `PackManifest`.
Delivered by `celtium-shaderpack/` (standalone module, Java 21, zero deps).

## Reuse audit (decided: narrow fresh implementation)

Iris's pack-loading classes (`modern/src/main/shaders_java/...`) were examined
as a reuse candidate and rejected for this module:

- they are filesystem-only (no zip container, no path hardening);
- they couple to ~25 non-JDK types (GL constants, Minecraft registries, Guava,
  fastutil, taumc) — inlining them would drag the mod's internals into a module
  whose reason to exist is being isolated and CPU-only;
- several choices Iris makes (lazy reads, per-version classes) are exactly what
  the S1 contract forbids (eager snapshot, immutable manifest).

They remain the semantics reference for option/profile/dimension behavior; no
Iris code was copied. Everything in `celtium-shaderpack/` is new, licensed with
the module like the rest of the repo.

## BSL v10.1.8 census (validation run, zip not committed)

`BSL_v10.1.8.zip` (sha256 `36b0a50ff7918bf10e9422c401d93779f9d0088a26acea975b435243f96930b5`,
309 archive entries) loads with **zero ERROR diagnostics** at `MC_VERSION=11202`:

- **292 files** under `shaders/`; three dimensions `world-1`/`world0`/`world1`
  (no root-level/base programs). Nether has 30 programs; overworld and end have
  32 each — the nether diff is exactly `gbuffers_skybasic`/`gbuffers_skytextured`.
- **94 stage files**: 91 `.vsh`+`.fsh` pairs and 3 `shadowcomp.csh` compute
  shaders (one per dimension — flagged `UNSUPPORTED_STAGE_COMPUTE`, out of S1
  scope but fully censused).
- **dimension.properties** binds the standard ids (`minecraft:overworld *`,
  `the_nether`, `the_end`).
- **140 live properties** in `shaders.properties`: 5 profiles (MINIMUM→ULTRA),
  ~70 `screen.*`/`sliders` UI entries, per-dimension `program.*.enabled`
  expressions, `alphaTest.*`/`blend.*`/texture overrides, `iris.features.optional
  = CUSTOM_IMAGES FADE_VARIABLE` (recorded as `IRIS_FEATURE_FLAGS`; the image
  keys themselves sit behind `#ifdef MULTICOLORED_BLOCKLIGHT` → the feature is
  *conditional*, not active).
- **402 option declarations** found lexically (365 user-facing: named by
  screens/sliders/profiles/enabled-conditions), environment of 335 effective
  values including `const` tunables (`shadowMapResolution`, `sunPathRotation`,
  `waterAlpha`…).
- **`program.*.enabled` gating is per-dimension and evaluated**: overworld
  `shadow` is `SHADOW` (on), nether `shadow` is `SHADOW && MULTICOLORED_BLOCKLIGHT`
  (off — MCBL is disabled by default), `shadowcomp` is `MULTICOLORED_BLOCKLIGHT`
  (off everywhere by default), composites gate on `AO`/`MOTION_BLUR`/`DOF`/
  `FXAA && !RETRO_FILTER`/`TAA && !RETRO_FILTER`/`LIGHT_SHAFT`/`SHADOW`.
- **`block.properties`/`entity.properties`/`item.properties` are version-branched.**
  At `MC_VERSION=11202` the `>= 10800` branch is live (23 `block.NNNNN` entries
  with namespaced ids); `entity.*`/`item.*` are entirely inside
  `#if MC_VERSION >= 11300` → **zero live entries on 1.12.2** — BSL does not map
  entity/item ids for this target.
- **30 diagnostics total**: the 3 compute UNSUPPORTED markers plus 27
  `CUSTOM_UNIFORM_EXPRESSION` warnings (`uniform.float.*`/`variable.float.*`
  expressions recorded, not evaluated — S2+).

### Sticking points found and fixed while loading it

- `defined X || defined Y` needed non-short-circuit boolean parsing (a lazy
  `||` skips parsing the right operand and corrupts the cursor).
- `#define` values must have `//[...]` allowed-value comments and block comments
  stripped before they reach the expression evaluator.
- `/* */` doc blocks in settings.glsl contain literal `#define` examples —
  comment-aware directive detection is mandatory or fake options appear.
- `MC_VERSION >= 11300` gates in .properties files require the same
  preprocessor as shader sources (they are not plain key=value files).
- Include resolution: quoted includes fall back to `shaders/` root; absolute
  `"/lib/..."` and angled `<lib/...>` are rooted at `shaders/`.

## Semantics decisions (documented in code)

- Condition seeds = `MC_VERSION` (default 11202) + `extraDefines` + effective
  option env (lexical defaults → selected `profile.X` → explicit overrides).
- `program.enabled` is evaluated against the effective option env; programs
  carry `enabledCondition` + `enabledByDefault` so the backend can re-evaluate
  per live options later.
- A `worldN` program with no files inherits all base stages; a `worldN` with
  partial stages overlays them on the base program.
- Options keep declaration-site info (file:line, conditional flag, allowed
  values, enabled-by-default); `userFacing` vs. internal defines is reported
  so the reviewer sees which of the ~400 `#define`s are actually UI-visible.
- Diagnostics are `ERROR` (pack is broken), `WARNING` (suspicious but
  accepted), `UNSUPPORTED` (documented capability gap — never silently dropped).

## What's left for S2+

- Evaluate `uniform.*`/`variable.*` expressions (need the runtime uniform set).
- Decide how `custom_images`, Iris `iris.features.*`, DH (`dh_terrain`/`dh_water`)
  and Voxy programs map onto the native pipeline.
- `noiseTextureResolution`/`colortexNFormat`/`const` target metadata is already
  collected per program — the SPIR-V backend consumes it directly.
