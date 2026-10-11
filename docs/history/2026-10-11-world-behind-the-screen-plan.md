# The world behind the screen, at equal build state — plan and status

Date: 2026-10-11. Owner: gui-stage. Approved by the Lead. Follows the withdrawal of the B05 attribution
(`2026-10-11-sky-ratio-three-scenes.md` §3) and fog-path's re-take (task-41).

## Why a re-take is needed

The pair that produced "the inventory scene is 48 % different" compared **different build states**: at the same tick,
`cand3-inventory` had 54 world frames / 12 listed sections / 8 MiB where `ref3-inventory` had 412 / 43 / 30 MiB. fog-path
re-took it with a capture condition instead of a wall-clock tick and the whole-frame difference fell from 48.0 % to
10.88 %, with the sky band from 83.4 % to 1.28 %. What remains at equal build state is **the world behind the screen**:

| region | inventory open, equal build state | same scene, no screen |
|---|---|---|
| terrain centre (320,300,960,640) | 27.74 % over 8 | 2.65 % |
| chat block (0,320,660,620) | 17.30 % | - |
| horizon (0,120,1280,300) | 16.48 % | - |
| whole frame | 10.88 % | - |

The three candidates are (a) the shared dim overlay `GuiScreen.drawWorldBackground`, (b) the panel's own alpha/blend,
(c) the panel simply covering the world. The discriminating fact is whether a *different* screen with **no item
batches** and a **different panel** dims the world behind it by the same amount.

## The legs

All four reuse **fog-path's frozen jars and fixture**, so every leg shares one build state, and the same capture
condition (`captureCondition=sections`, `captureSections=43`, `captureMinWorldFrames=412`, `captureDeadlineTick=6000`):

| role | jar | sha256 prefix |
|---|---|---|
| reference | `celeritas-forge-mc12.2-2.5.12-upstream.989ebf2e.jar` | `d12425eaaf184e79` |
| candidate | `fog-path-mod8.jar` | `609eb69a9064df05` |
| fixture | `fog-path-fixture4.jar` | `30d0ded469c529b2` |

| leg | scene | purpose |
|---|---|---|
| `wbs-noscreen-ref` | terrain, no screen | the control reference |
| `wbs-noscreen-cand` | terrain, no screen, GL-less | the control candidate |
| `wbs-vs-ref` | `scene=videoSettings` | the discriminator reference |
| `wbs-vs-cand` | `scene=videoSettings`, GL-less | the discriminator candidate |

The inventory scene at this build state is fog-path's `t41-ref-inv` / `t41-cand-inv` pair and is **reused, not
re-taken**. Script: `.celtium-work/gui-stage/run-wbs.sh` (legs run sequentially, so at most one is ever queued).

## The reading, once the legs land

- **Same ~27 % behind the video-settings screen as behind the inventory** -> the cause is the shared dim overlay
  (`GuiScreen.drawWorldBackground`), because the two screens share it and nothing else.
- **Much less behind the video-settings screen** -> the cause is the inventory panel's own alpha/blend (or its
  covering geometry), because the overlay is the same and the panel is not.
- **No difference behind either screen** -> the difference is the panel covering the world, not a shading effect, and
  the region to compare is the world *outside* the panel.

## Status when this was written

All four legs were **queued behind the single-game slot** (the machine was at 10/15 GB with several harness scripts
waiting). No leg had produced a report yet. The table above is to be filled from the four runs plus fog-path's pair;
the per-region means and the crops go beside it in `.celtium-work/sky-ratio/` when they land.
