# celtium-shaderpack

Pure-CPU shaderpack frontend (S1 scope of issue #76): loads an OptiFine/Iris-style
shaderpack from a zip archive or an extracted directory and produces a
`PackManifest` - the immutable plan a future native backend would render from.

**No GL objects, no GPU, no SPIR-V, no Minecraft.** Java 21, zero runtime
dependencies, so the module is embeddable in the mod without dragging in Guava,
LWJGL or transformer libraries.

```
Path input = ...;                      // pack.zip or pack directory
PackManifest m = ShaderPackLoader.load(input, LoadOptions.defaults());
System.out.print(m.toReport());        // deterministic census text
m.dimensions();                        // base + world-1/world0/world1 plans
m.disabledPrograms();                  // enabled=false under the default env
m.diagnostics();                       // ERROR / WARNING / UNSUPPORTED, sorted
m.hasErrors();
```

`PackInspect` (`cli` package) does the same from a shell for local inspection.

## What it does

- **Container safety.** Zip and directory inputs are snapshotted eagerly into an
  immutable in-memory map before any parsing. Traversal entries (`..` past the
  root, drive-letter names, NULs), duplicate canonical names, encrypted entries,
  file-count/size/total-size bounds and a compression-ratio check are enforced
  at open time; directory walks never follow symlinks and reject fifos/devices.
  `shaders/` nested one level under a single top directory (`MyPack/shaders/`)
  is tolerated with a warning. Everything outside `shaders/` is counted and
  ignored, never read.
- **Include expansion.** `#include` resolves like OptiFine: `</lib/x.glsl>` and
  `"/x.glsl"` are rooted at `shaders/`, `"x.glsl"` is relative to the including
  file with a `shaders/`-root fallback. Cycles, missing files, targets escaping
  `shaders/`, non-text includes, depth > 64 and expansion > 8192 are errors,
  never hangs.
- **Preprocessor.** `#define`/`#undef`, `#if`/`#ifdef`/`#ifndef`/`#elif`/`#else`
  /`#endif` with a real expression evaluator (`defined` both forms, `|| && == !
  < > <= >= + - * / % ~`, ints/floats/hex, recursive macro resolution). Text in
  `/* */` block comments is not a directive (packs document example `#define`s
  there). `#version`/`#extension`/`#line`/`#pragma` pass through. `#error` is a
  diagnostic.
- **Properties.** `shaders.properties`, `dimension.properties`, `block.properties`
  etc. are preprocessed with the same machinery (`#if MC_VERSION` picks the right
  branch), then parsed with real properties rules - continuations, escapes,
  `=`/`:`/whitespace separators. Profiles (`profile.X`, `!OPT`, `OPT=v`,
  `profile.Y` references), screen/sliders tokens, `program.*.enabled`,
  `dimension.worldN`, `alphaTest.*`, `blend.*`, `texture.*`, `uniform.*` /
  `variable.*` custom expressions, and `iris.features.*` are all classified.
- **Program census.** `world-1`/`world0`/`world1` directories plus root-level
  `shaders/` (base) programs are enumerated; a `worldN` stage overrides the base
  stage, a `worldN` program with no base counterpart is dimension-local, and a
  `worldN` absent entirely inherits the base plan. Stages `.vsh .fsh .gsh .tcs
  .tes .csh` are recognized; geometry/tessellation/compute are flagged
  `UNSUPPORTED` (kept in the census, excluded from nothing). Per-program
  `/*DRAWBUFFERS:...*/`, `/*RENDERTARGETS:...*/`, `const colortexNFormat` and
  similar comment directives are collected.
- **Determinism.** File order, diagnostics, options, profiles, program order and
  the `toReport()` text are fully sorted/stable - the same pack bytes always
  produce the same manifest, and the content hash is container-agnostic (a zip
  and its extraction agree).

## What it deliberately does not do (yet)

- No rendering, pipelines, samplers, GL state or GLSL compilation - that is S2+.
- `uniform.*`/`variable.*` custom expressions are recorded, not evaluated.
- `image.*` (CUSTOM_IMAGES) and other Iris-only declarations are surfaced as
  feature flags + `UNSUPPORTED`/`WARNING` diagnostics.
- Compute (`*.csh`), geometry (`*.gsh`) and tessellation (`*.tcs`/`*.tes`)
  stages are censused but flagged out of scope.

## Test / validate

```sh
./gradlew -p celtium-shaderpack test    # 68 fixture tests, synthetic packs only

# inspect a real pack locally (never committed):
java -cp celtium-shaderpack/build/classes/java/main \
     org.soutalab.celtium.shaderpack.cli.PackInspect BSL_v10.1.8.zip
```

The JUnit suite covers traversal/bomb/symlink hardening, include cycles and
bounds, conditional evaluation, properties parsing, per-dimension overrides,
profile/override flips, and zip-vs-directory determinism. When a real BSL zip is
reachable (`-Dceltium.bslZip=...` or `CELTIUM_BSL_ZIP`), one extra test loads it
and asserts the census is error-free with the expected dimension map; CI skips
it cleanly.
