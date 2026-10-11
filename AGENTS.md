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

- `docs/plan.md` — the single place the current state, priorities and acceptance live,
  including a header table that is kept current. Corrections are made in place.
- `docs/goal.md` — the objective, in sync with the goal the session runs under.
- `docs/history/` — past evidence and stage records. Not the current verdict.
- `docs/handoff-2026-10-10-next-model.md` — environment, run procedure, instruments,
  rejected hypotheses and the traps that have already been stepped in.
- `AGENTS.md` (this file) — the rules that are cheaper to read than to rediscover.
