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

## The control has landed, and it validates the method

`wbs-noscreen-ref` / `wbs-noscreen-cand`, equal build state (44 and 45 listed sections, fog-path's jars and fixture,
capture condition rather than a tick):

| region | candidate mean | reference mean | ratio | pixels >8 |
|---|---|---|---|---|
| sky band (0,0,1280,120) | 158.1,192.9,255.0 | 158.1,192.9,255.0 | **1.000,1.000,1.000** | **0.00 %** |
| horizon (0,120,1280,300) | 147.2,153.4,174.6 | 147.3,153.6,174.6 | 0.999,0.999,1.000 | 0.72 % |
| terrain centre (320,300,960,640) | 103.8,66.8,61.2 | 103.8,66.8,60.8 | 1.000,1.000,1.007 | **0.01 %** |
| chat block (0,320,660,620) | 102.3,63.9,58.5 | 102.3,63.9,57.8 | 1.000,1.000,1.013 | **0.01 %** |
| hotbar strip (490,660,790,720) | 103.6,23.2,22.2 | 103.6,23.2,22.2 | 1.000,1.000,1.002 | **0.00 %** |
| whole frame | 123.4,105.7,118.4 | 123.4,105.7,118.1 | 1.000,0.999,1.002 | **0.19 %** |

**This is the control that makes the inventory column readable.** With no screen open and at equal build state, the two
renderers agree to 0.19 % of pixels whole-frame and to zero in the sky band, so the 27.74 % terrain-centre difference
that remains with the inventory open is **caused by the screen being open** and is not a build-state or renderer-level
artefact. The earlier "48 %" is fully explained: it was a build-state difference (54 vs 412 world frames, 12 vs 43
listed sections), and the re-take at equal build state leaves a real, screen-caused 10.88 % whole-frame difference.

The video-settings discriminator leg (`wbs-vs-cand`) was still running when this was written; `wbs-vs-ref` is done
(tick 886, 44 sections). The reading in the section above is unchanged and its inputs are: this control, fog-path's
inventory column, and the discriminator leg when it lands.

## The discriminator leg landed — and its reference had already been deleted

`wbs-vs-cand` completed: `GuiVideoSettings` open at the capture (`guiCaptureTick=1782`, `captureListedSections=46`),
chain ran and restored, picture `screenshots/celtium-presented-gui-run-16141606e16d3-66d3c617-kept.png`.

**The pair cannot be computed**: `wbs-vs-ref`'s screenshot and logs were deleted by the retention policy
(`.celtium-work/disk-cleanup-latest-only-20261011.json`) while the candidate leg was still queued. Only its
`validation-command.json` and `validation-process.json` survive, and neither carries pixel statistics. So the
three-scene comparison stops one frame short, and that is a fact about the retention policy rather than about the
renderer.

**What the candidate frame alone shows** (self-comparison on one jar, one scene flag apart - the candidate's own
video-settings frame against the candidate's own no-screen frame, whose recorded means are in the control table above):

| region of the candidate's video-settings frame | mean | candidate no-screen mean | factor |
|---|---|---|---|
| sky band (0,0,1280,120) | 35.4,27.4,21.5 | 158.1,192.9,255.0 | 0.22,0.14,0.08 |
| left margin (0,80,170,640) | 15.2,11.3,7.5 | - | - |
| right margin (1110,80,1280,640) | 23.8,20.8,18.0 | - | - |
| bottom band (0,690,1280,720) | 27.8,21.6,17.2 | - | - |
| whole frame | 52.9,49.9,47.5 | 123.4,105.7,118.4 | 0.43,0.47,0.40 |

**The candidate's video-settings screen does dim the world behind it, heavily** (sky band to 0.22 of the undimmed
value). So the shared dim overlay is present behind this screen too, which points away from "the inventory panel is
uniquely at fault" and towards the shared `GuiScreen.drawWorldBackground` - **but this is a candidate-side observation,
not a pair**, and the decisive half is the reference's own video-settings frame. Recorded as such rather than promoted.

**What recovers it:** re-run one leg, `wbs-vs-ref` (`nohup ./.celtium-work/gui-stage/run-wbs.sh` re-runs the set; the
reference alone is enough), and compare immediately - before the next run completes and the retention policy removes
the frame. The candidate's frame is preserved as a 50 % copy at
`.celtium-work/sky-ratio/wbs-crops/wbs-vs-cand-gui-small.png`.

## Status when this was written

All four legs were **queued behind the single-game slot** (the machine was at 10/15 GB with several harness scripts
waiting). No leg had produced a report yet. The table above is to be filled from the four runs plus fog-path's pair;
the per-region means and the crops go beside it in `.celtium-work/sky-ratio/` when they land.
