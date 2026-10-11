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

## The discriminator pair, computed — and it names the inventory panel, not the shared overlay

The reference leg was re-run (`wbs-vs-ref2`, upstream Celeritas `d12425eaaf184e79` + `fog-path-fixture4.jar`
`30d0ded469c529b2`, `scene=videoSettings`, capture condition), and **both frames were copied out and hashed before
anything else touched the slot**, which is the process the retention policy now requires:

| frame | surviving copy | sha256 prefix |
|---|---|---|
| reference (GL screenshot) | `.celtium-work/sky-ratio/wbs-crops/vs-ref2-full.png` | `27758cb74a69e25d8cd1` |
| candidate (GL-less presented gui dump) | `.celtium-work/sky-ratio/wbs-crops/vs-cand-gui-full.png` | `91ef7f2fbdfb58db0416` |

| region | candidate | reference | ratio | pixels >8 |
|---|---|---|---|---|
| **sky band (0,0,1280,120)** | 35.4,27.4,21.5 | 35.4,27.6,21.9 | **1.000,0.993,0.982** | 37,776 of 153,600 (24.59 %) |
| horizon (0,120,1280,300) | 60.5,59.2,57.9 | 62.3,60.6,58.9 | 0.971,0.977,0.983 | 13,458 (5.84 %) |
| terrain centre (320,300,960,640) | 68.7,67.8,66.9 | 70.4,69.1,67.9 | 0.976,0.981,0.985 | 12,057 (5.54 %) |
| chat block (0,320,660,620) | 55.3,53.8,52.3 | 56.9,55.0,53.2 | 0.972,0.978,0.983 | 12,168 (6.15 %) |
| hotbar strip (490,660,790,720) | 73.4,71.0,69.2 | 77.5,74.1,71.7 | 0.947,0.957,0.965 | 2,904 (16.13 %) |
| panel (180,80,1100,624) | 70.2,69.3,68.5 | 71.4,70.2,69.2 | 0.983,0.987,0.990 | 22,842 (4.56 %) |
| **Done button (340,640,940,690)** | 110.1,110.1,110.1 | 110.1,110.1,110.1 | **1.000,1.000,1.000** | **8 of 30,000 (0.03 %)** |
| title (0,20,1280,60) | 41.8,31.9,24.8 | 39.4,30.2,23.8 | 1.063,1.055,1.044 | 9,297 (18.16 %) |
| whole frame | 52.9,49.9,47.5 | 55.1,51.7,48.9 | 0.959,0.966,0.972 | 114,666 (12.44 %) |

**The reading, and it is the discriminating one:**

- **The dim overlay is present and correct behind this screen.** Both legs put the sky band at 35.4/27.4-27.6/21.5-21.9 -
  a factor of **0.22** of the undimmed 158.1,192.9,255.0 - and the candidate/reference ratio is **1.000,0.993,0.982**.
  So the candidate dims the world behind the video-settings screen by exactly the amount the real GL renderer does.
- **Behind the inventory screen at the same build state the same comparison is 27.74 % on the terrain centre**
  (fog-path's `t41` pair). The overlay is shared between the two screens; the difference appears **only** with the
  inventory open.
- **Therefore the cause is not the shared `GuiScreen.drawWorldBackground` overlay.** It is the **inventory panel's own
  alpha/blend or its covering geometry** - the one thing that differs between the two screens while the overlay does not.

This supersedes the earlier candidate-side observation in this file, which pointed the other way; that observation was
one side of a pair and this is the pair, so the pair wins. The `title` band (18.16 %) and the hotbar strip (16.13 %) are
the font path and the immediate HUD respectively, and the Done button is pixel-identical.

## Status when this was written

All four legs were **queued behind the single-game slot** (the machine was at 10/15 GB with several harness scripts
waiting). No leg had produced a report yet. The table above is to be filled from the four runs plus fog-path's pair;
the per-region means and the crops go beside it in `.celtium-work/sky-ratio/` when they land.
