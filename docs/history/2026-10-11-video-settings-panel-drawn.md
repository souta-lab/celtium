# The video-settings panel is drawn — and its floor was calibrated on another scene

Date: 2026-10-11. Owner: gui-stage. Follows `docs/plan.md` §0.189/§0.203, which names "the GUI frame's panel crop
plus the background texture's mirror dump" as the next measurement. The pair already exists, so this is that
measurement for the panel half.

## The claim under test

§0.189: "the panel is still flat: `guiPanelDistinctColours=133` against the reference's **15,805**, `changedFraction
0.999996` — not drawn from the background texture."

## The measurement

Frames: `wbs-vs-ref2` (upstream Celeritas GL, `scene=videoSettings`, capture condition) against `wbs-vs-cand`
(GL-less), copied and hashed before the retention policy could take them —
`.celtium-work/sky-ratio/wbs-crops/vs-ref2-full.png` (`27758cb74a69e25d8cd1…`) and
`vs-cand-gui-full.png` (`91ef7f2fbdfb58db0416…`).

**Distinct colours inside the panel region `(180,80,1100,624)`, on the frames themselves:**

| leg | panel distinct colours | rows-left `(180,90,620,620)` |
|---|---|---|
| **reference (real GL, upstream Celeritas)** | **139** | 131 |
| candidate (GL-less) | 104 | 95 |

**The reference's own panel has 139 distinct colours, not 15,805.** The floor is comparing the video-settings screen
against a number that does not describe it: 15,805 is an **inventory-scene** figure, where the panel contains item
sprites and their atlas art. The vanilla video-settings panel is a tiled grey options list, so it has ~140 colours in
either renderer. **This is the same defect as the band floor routed to task-47** — a floor measured on one picture and
applied to a different one — and it is the second instance of it in the same gate.

And the region comparison says the panel matches:

| region | candidate/reference ratio | pixels >8 |
|---|---|---|
| **panel (180,80,1100,624)** | **0.983,0.987,0.990** | **4.56 %** |
| rows-left (180,90,620,620) | 0.985,0.988,0.991 | 3.93 % |
| **Done button (340,640,940,690)** | **1.000,1.000,1.000** | **0.03 %** |
| title (0,20,1280,60) | 1.063,1.055,1.044 | 18.16 % |
| whole frame | 0.959,0.966,0.972 | 12.44 % |

## What is true, and what is left

- **The panel, the rows and the buttons are drawn.** The Done button is pixel-identical, the rows are 3.93 % and the
  panel 4.56 % of pixels over 8 against the real GL renderer, with mean ratios within 1-2 %. `changedFraction
  0.999996` cannot be reproduced from this pair; whatever run produced it is not this variant.
- **The residual is real but small and it is texture detail, not absence**: the candidate has **104** distinct colours
  in the panel against the reference's **139**. That is the direction the plan's "background texture mirror dump"
  would settle — the tiled options background sampling slightly differently — and it is a texture question, not a
  geometry one.
- **The `guiPanelDistinctColours>=15,805` floor cannot be satisfied by any video-settings leg, including the real GL
  reference**, exactly as the `verticalBands>=250` floor cannot. It needs recalibrating on this scene's own GUI frame;
  that is the gate owner's call, and task-47 already owns the provenance fix and the audit of the other floors — this
  is the second instance to hand it.

## Caveat, stated rather than hidden

Both legs here are the **task-42 pair** (tick 235 and 297, `scene=videoSettings`, the fixture's chain run on the
captured screen and the value restored). The `guiPanelDistinctColours=133` reading came from a different leg whose
provenance I have not checked; what this record establishes is that the number cannot be read as "the panel is
missing", because the reference scores 139 on the same region of the same scene. A leg that reports 133 for the
candidate against a 15,805 floor is a leg to re-measure with the floor's provenance fixed, not a rendering defect to
chase.

## The 104-versus-139 gap, characterised (round 16)

The panel region's colour sets, compared directly:

| | count |
|---|---|
| candidate distinct colours | 104 |
| reference distinct colours | 139 |
| **shared** | **104** |
| **candidate-only** | **0** |
| reference-only | 35 |

**Every colour the candidate draws in the panel, the reference also draws.** The candidate is missing 35
*intermediate shades*, and it draws nothing the reference does not. That is not missing geometry, not a wrong
texture, and not a missing quad: it is the signature of **texture filtering** - the reference blends neighbouring
texels into in-between values and the candidate samples more coarsely.

The difference is only mildly edge-concentrated: 4.56 % of panel pixels differ by more than 8, and 10.3 % of those
sit on a reference colour edge, against 6.4 % of panel pixels being on an edge at all.

**So the next measurement is the sampler's filter state, not the mirror's content.** The wrap census already showed
the game's texture *parameters* are recorded and consumed (`CELTIUM_TEX_PARAM_CENSUS recorded=194 textures=29
wrapRepeat=38 wrapClamp=0`); the filter pair (`GL_TEXTURE_MIN_FILTER` / `GL_TEXTURE_MAG_FILTER`, and the mipmap
level) belongs in the same census, and the question is whether the GUI atlas mirror is sampled with the filter the
game set or with the renderer's own default. That is a texture-mirror question with a named measurement, and it is
the whole of what remains of this row.

**Also unexercised, and named as such:** the chat background bars. The fixture sends no chat line, so `Gui.drawRect`'s
chat bars have no geometry in any scene of this fixture. A chat scene needs a hook that prints chat lines - the
fixture owner has it this round - and until then the chat half of the HUD complaint is unexercised rather than
passing or failing. The hotbar frame, by contrast, is measured: 0.00 % of pixels over 8, pixel-identical.
