# Celtium — how to work in this repository

This file is for anyone (person or agent) about to change something here. It exists
because a specific, expensive mistake was made repeatedly, and a rule that lives only
in someone's memory is not a rule.

## Read the original before you decide anything

The build produces the **decompiled Minecraft and Forge sources**, and the SRG name
tables, as a normal part of building. They sat unread for weeks while signatures, draw
modes, vertex formats and call order were guessed at and argued about across several
rounds. Do not repeat that.

```sh
scripts/mc-sources.sh                     # paths to the sources and the name tables
scripts/mc-sources.sh --grep 'begin(7, DefaultVertexFormats'   # search them
scripts/mc-sources.sh --srg func_181679_a # deobf <-> SRG name, both directions
```

Before writing a mixin target, a format id, a draw-mode assumption, a shader input or
an ordering argument, look it up there. `javap` on a jar is a fallback for when the
sources do not cover a class, not the first move.

The same rule applies to this project's own instruments: if a counter, dump or report
already exists, read it instead of adding another one, and if you add one, name it in
the report you write.

## A failing gate is evidence

The picture gate reported `presentedImageVerdict=FAILED_PRESENTED_IMAGE` with
`sceneGateFloorsEnforced=true` and a matching calibration key for every GL-less run
after 09:33 on 2026-10-11. It was right: the terrain had stopped contributing pixels.
It stayed hidden for hours because the verdict was re-described as "the pinned scene's
normal state" and readers were told to look at the counters instead.

Do not do that. A failing gate is evidence until a measurement says it is wrong. If
you believe a gate is misfiring, take an experiment that separates the competing
explanations - the same fixture, the same camera and two jars, or the same jar and two
cameras - before you override it, and write down which explanation the experiment
ruled out.

## What "verified" means here

- A GPU run is evidence only with its jar hash, GPU, scene, camera and flags recorded.
- `validationClean=true` and a missing verdict are not success. A refusal is not a
  fallback: under the GL-less window a refused batch is drawn by nobody.
- A missing counter is not a missing draw, and a missing control is not a matching one.
  Both of those were learned by getting them wrong.
- Acceptance is taken on a real Cleanroom 0.6.13-alpha instance through
  `scripts/prism_validation.py`. `runClient` is a stock-Forge dev client: useful for
  exploration, never for acceptance.
- One game instance at a time:
  `for p in $(pgrep -x java); do tr '\0' '\n' < /proc/$p/cmdline | grep -q foundation.boot.Foundation && echo busy; done`

## Where things are

- `docs/audit-2026-10-12-maximum.md` — audit entry point; section 2a reconciles all legacy BH IDs with the new findings. Do not treat historical audits as independent pending queues.
- `docs/plan.md` — the single place the current state, priorities and acceptance live,
  including a header table that is kept current. Corrections are made in place.
- `docs/goal.md` — the objective, in sync with the goal the session runs under.
- `docs/history/` — past evidence and stage records. Not the current verdict.
- `docs/handoff-2026-10-10-next-model.md` — environment, run procedure, instruments,
  rejected hypotheses and the traps that have already been stepped in.
- `AGENTS.md` (this file) — the rules that are cheaper to read than to rediscover.

## An Intel leg is confirmed by the device line, not by the flag

A run given `--render-node /dev/dri/by-path/pci-0000:00:02.0-render --require-gpu Intel` is **not** an Intel run
until its log says so. Measured on 2026-10-12: two such legs reported
`CELTIUM_NATIVE_DEVICE name="AMD Radeon RX 6400 (RADV NAVI24)"`, and a claim of "accepted on both GPUs" was written
from them before anyone read that line. The harness does catch it (`gpuRequirementMet=false`, non-zero
`validationExitCode`) when its summary is inspected, but a leg run through a wrapper (`compositor_capture.py`) does
not surface that summary.

So: before calling anything Intel, read `CELTIUM_NATIVE_DEVICE name="…"` in the leg's log **and** the harness's
`gpuRequirementMet`. The requirement string must be the renderer the driver actually reports, e.g.
`--require-gpu 'UHD Graphics 630'`, not the vendor word. AMD success is never Intel success.

**And the render node alone does not select the device** - it selects the compositor's GPU. Measured on
2026-10-12: with the Intel render node and the right requirement string, the game still enumerated AMD
(`nativeDeviceName="AMD Radeon RX 6400 (RADV NAVI24)"`, `gpuRequirementMet=false`), while Sway itself was on
Intel. The working recipe is Mesa's device selection, which the harness passes through because the headless
environment is `os.environ.copy()`:

```sh
export DRI_PRIME=pci-0000_00_02_0!        # Intel UHD 630 (8086:3e92); the '!' exposes only that GPU
# or: export MESA_VK_DEVICE_SELECT=8086:3e92!
```

With that, the same leg reports `nativeDeviceName="Intel(R) UHD Graphics 630 (CFL GT2)"` and
`gpuRequirementMet=true`. `DRI_PRIME` also steers the compositor, which is why both ends then agree.

## A leg with no picture has not necessarily run your build

Measured on 2026-10-12 (round 212): the instance's `minecraft/mods/` held a second celtium jar,
`celtium-6794b0df2-pre-stub-changes.jar`, carrying the same modid (`celeritas`). The harness copies
the instance's mods into every run, so **every leg from that moment ran with two celtium jars**, and
the validation fixture went silent: `fixtureStatus=MISSING_REPORT`, no presented dump, and not one
`ValidationClient` line in the log - only the mod's own `CELTIUM_SPLASH_HOOK drawSplashScreen=true`.
The same leg with `--exclude-instance-mod 'celtium-*pre-stub-changes.jar'` wrote its dumps and
returned a report immediately.

So before reading a silent leg as a renderer finding, list `<run>/mods/` and count the celtium jars,
and pass `--exclude-instance-mod` for anything the run is not testing. A duplicate jar is a
*different artefact*, not a stale copy of the one you built: the results belong to nobody.
