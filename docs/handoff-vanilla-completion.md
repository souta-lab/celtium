# Vanilla completion — shared brief for every worker

This file is the common part of every task in the current push: **make vanilla Minecraft
render correctly under Celtium's GL-less Vulkan path.** JEI and other mods come after.

Read `AGENTS.md` first, then this. The rules in `AGENTS.md` are not optional and they were
each paid for.

## What "done" means for your task

A picture comparison at the **same scene, same pose, same tick**, plus the counters that explain
it. The reference is one of these three, in this order of preference:

1. **Upstream Celeritas** (pinned 989ebf2e) — the real GL renderer, and the reference the user
   chose: "glless vs celeritas のほうが楽だし、bridge の保守もいらなくなる". Run it as its own leg:
   same fixture, same flags, but `--mod .celtium-work/upstream-baseline/build-1122/build/libs/2.5.12-upstream.989ebf2e/celeritas-forge-mc12.2-2.5.12-upstream.989ebf2e.jar`
   and **no** celtium jar and **no** `-Dceltium.vulkan.glless=true`. It draws with OpenGL, which is
   what vanilla does.
2. **Stored evidence** — the reference pictures and calibrated floors taken earlier. They remain
   valid evidence of what vanilla drew, as long as you say which run and what its numbers were.
3. **A self-comparison on one jar, one flag apart** (a refusal control, a feature toggle). Say so
   explicitly; two GL-less legs on different jars are not a comparison.

**The bridge leg is retired.** The user decided the GL-interop mode is not needed for the
completion condition and does not want it maintained, so do not spend the slot on bridge legs and
do not treat one as a reference.

The GL-less leg (`-Dceltium.vulkan.glless=true`) is what you are fixing.
- Compare **crops**, name their coordinates, and say what changed. Numbers alone have been
  misread repeatedly; a crop plus a number is the standard here.
- `validationClean=true`, a missing verdict, an empty refusal census, or "the census no longer
  lists X" are **not** acceptance. A refusal is not a fallback: under the GL-less window a
  refused batch is drawn by nobody.

## Running the game

```sh
cd /home/s0u7a/projects/celtium
export JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64
./gradlew -p build-1122 :forge122:packageJar :forge122:validationRemapJar --console=plain --no-daemon \
  "-Porg.gradle.java.installations.paths=$JAVA_HOME,$PWD/.celtium-work/jdks/21/jdk-21.0.12.1+1,$PWD/.celtium-work/jdks/8/jdk8u504-b01"

export VK_LAYER_PATH="$PWD/.celtium-work/gpu-tools/extracted/usr/share/vulkan/explicit_layer.d"
export LD_LIBRARY_PATH="$PWD/.celtium-work/gpu-tools/extracted/usr/lib/x86_64-linux-gnu"

GD="$PWD/.celtium-work/runtime/<your-run-name>"
timeout 360 python3 scripts/prism_validation.py \
  /home/s0u7a/.local/share/PrismLauncher/instances/cleanroom-0.6.13-alpha \
  --output .celtium-work/<your-run-name>.json --game-dir "$GD" \
  --mod build-1122/build/libs/0.0.2-alpha-dev/celtium-forge-mc1.12.2-0.0.2-alpha-dev.jar \
  --mod forge122/build/libs/celtium-validation-client-0.0.2-alpha-dev.jar \
  --library .celtium-work/runtime/celtium-headless-interop/test-libraries/lwjgl-vulkan-3.4.1.jar \
  --jvm-arg=-Dceltium.vulkan.glless=true --jvm-arg=-Dceltium.vulkan.validation=true \
  --jvm-arg=-Dceltium.vulkan.presentReadback=true --jvm-arg=-Dceltium.validation.dumpPresentedImage=true \
  --jvm-arg=-Dceltium.validation.minWorldFrames=5 \
  --run --validate --duration 120 --headless --force-x11 \
  --render-node /dev/dri/by-path/pci-0000:03:00.0-render
```

- **One game instance at a time.** Check with:
  `for p in $(pgrep -x java); do tr '\0' '\n' < /proc/$p/cmdline | grep -q foundation.boot.Foundation && echo busy; done`
  Never use `pgrep -f`.
- **Build before you run, every time.** A stale jar looks exactly like a result, and a red
  tree stops every other worker's run. If you must leave the tree mid-edit, say so.
- Never copy a mod jar into the Prism instance's `minecraft/mods`.
- Use background jobs rather than sleeping.

## Instruments that already exist — read them before adding one

- `CELTIUM_GROUP_BOUNDS` (`-Dceltium.vulkan.groupBounds=true`, `groupBoundsFormat=6,7`,
  `groupBoundsCount=N`): per-group format, texture, NDC footprint, colour, mirrored matrices.
- `CELTIUM_IMMEDIATE_ENQ` (`-Dceltium.vulkan.enqAll=true`, `enqFormat=<id>`): every group with
  its format, texture, target and `worldPass`.
- The per-format histogram, printed as `celtium immediate formats offered/accepted: idN=x/y`.
- `CELTIUM_REFUSALS_BY_FORMAT`, `CELTIUM_DRAWARRAYS`, `CELTIUM_DRAWARRAY_LAYOUT`,
  `CELTIUM_NOOP_CENSUS`, `CELTIUM_INVALIDATE_ORIGIN`, `CELTIUM_MIRROR_PATCH`.
- The presented-frame dump (`-Dceltium.validation.dumpPresentedImage=true`,
  `dumpPresentedTick=<n>`) is the picture under GL-less; the game's own screenshot is black by
  construction there. In a bridge leg the screenshot **is** the picture.
- If you add an instrument, name it in your report.

## Traps that have already cost hours — do not re-step them

1. **The report's camera is not always the camera that was rendered.** The scene's camera
   overrides now run at the START of the render tick (`2849c1ec8`), so they are the pose that
   gets drawn; but any older note saying "pitch -14" may describe a frame drawn at +20.
2. **A measurement can be an artefact of the instrument.** A wedge was "localised" to the moon
   quad because the diagnostic read vertices at a 16-byte stride when the layout was 20; and a
   `pointerStride=1` was really `pointerStride=12` truncated by a `grep -oE` bound. Before
   acting on a number, check the instrument reads what it claims, and that what reached you is
   the whole value.
3. **The census line is a race.** `CELTIUM_NOOP_CENSUS` goes to the process's own stdout now;
   Forge routes `System.out` through Log4j, whose shutdown hook stops the appenders while hooks
   run concurrently. Its absence is not evidence.
4. **`worldTicks` can skip values.** Tick-scheduled steps are `>=` with once-flags now
   (`890817893`). Do not reintroduce `==`.
5. **Calibration keys describe a measurement.** They must not move with the candidate
   (`A03`, `2b81d7123`, `1c72132a6`). A run whose variant or pose differs from the reference is
   reported `UNCALIBRATED_PRESENTED_IMAGE`, which is correct, not a failure to fix.
6. **Do not override a failing gate.** It is evidence until a measurement says otherwise. Take
   the experiment that separates the competing explanations first.
7. **Read the original.** `scripts/mc-sources.sh`, `--grep`, `--srg`, `--notch` (for the
   obfuscated runtime-patch classes), `--cleanroom`. Do not guess a signature, a format id, a
   draw mode or an ordering again.
8. **Mixin rules**: a handler that cancels needs `cancellable = true`; one mixin must not call
   another mixin's method (methods merge into the target class); helpers do not live in the
   mixin package; return-typed handlers use `CallbackInfoReturnable<T>`.
9. **Never claim a completion percentage or "one more thing".** Say what is measured, what is
   not, and what you did not do.

## Known open defects in the vanilla path (as of this brief)

- **Sky dome, stars and horizon plane**: `RenderGlobal.renderSky` draws them through
  `VertexBuffer.drawArrays` → `GlStateManager.glDrawArrays` → the stub, where they were no-ops.
  A path exists but is **gated off** because it produced ~174,000 pure-blue pixels. The two
  named candidates are the projection channel and the absence of fog.
- **Projection channel**: Kirino mirrors `loadIdentity` then calls
  `org.lwjgl.util.glu.Project.gluPerspective`, which goes straight to `glMultMatrixf` and never
  reaches the mirror; `TerrainGpuBackend.perspective()`/`frustum()` exist and have **no caller**.
  Vanilla's own camera projection takes the same route (`EntityRenderer.java:729`), so any hook
  must fire for the sky's call and not for the camera's.
- **No fog at all in the immediate path** (`TerrainGpuBackend` has no `setFog*`), which is what
  makes the dome and the horizon plane read as flat polygons instead of sky.
- **Texture mirrors go stale and are refreshed by reading GL**, which cannot work under GL-less;
  GUI panel and slot backgrounds are submitted, accepted, and produce no pixels.
- **GUI/HUD solid quads** (hotbar frame, chat background, panel faces) and **item models** in
  GUI frames are reported missing in the picture.
- **`POSITION_NORMAL`** has no branch in the uploader's format mapping, so the white box that
  `RenderEntity.doRender` draws for a renderer-less entity is refused (id 0).
- **A black region** (~19% of the presented frame, two per-boot patterns) reported by the UBO
  worker, unowned.
- **Particles** (format 5) and **weather/clouds/water** (format 6) are unverified against the
  bridge.
