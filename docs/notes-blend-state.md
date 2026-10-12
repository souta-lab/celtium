# notes-blend-state — round 240: is the HUD drawn, and with what state?

Artifact: leg `r212-world-hud6` (`.celtium-work/runtime/r212-world-hud6/logs/latest.log`), jar
`42ce83e05d17e31c`, HEAD `6ae0a5c73` (the same jar the lead's `hud5` provenance carries), AMD RX 6400.
Flags: `glless=true presentReadback=true validation=true scene=terrain keepGui=true minWorldFrames=5
screenshotAtTick=200 dumpPresentedImage=true groupBounds=true groupBoundsFormat=1,2,6,7
groupBoundsMinFrame=40 groupBoundsCount=600` (read from `validation-command.json`).
600 `CELTIUM_GROUP_BOUNDS` lines, frames 40–83.

The earlier `hud5` leg was **not** usable for this question: its `groupBoundsFormat=2` filter is why its
only identity-0 groups were one quad per frame, so "the HUD is absent" must not be read from it.

## 1. Which identity-0 groups appear, with which texture and NDC

**Exactly one identity-0 group per frame, and it is the same tuple in all 43 of them:**

```
CELTIUM_GROUP_BOUNDS frame=40 group=602 fmt=2 worldPass=false target=0 texture=3 batches=1 vertices=4
  indices=6 colour0=0x0 vertexColour0=0xffffffff proj=[m00=0.002 m11=-0.003 m22=-0.501 m32=-0.5]
  state=[blend=0 src=0 dst=0 srcA=0 dstA=0 mask=0x7 eq=0/0 alphaFunc=0 depthTest=0 depthWrite=0
         cull=1 alphaRef=0.0 depthFunc=515 glBlend=off glDepthTest=off]
  texMat=none ndc x[-0.00..0.00] y[-0.00..0.00] z[0.50..0.50] behind=0
```

43 lines, one per frame 40–82, **one distinct `(fmt, tex, x, y, z)` tuple**: a 4-vertex textured quad,
`texture=3`, projection = the GUI ortho for 1280×720 (`2/1280 = 0.0016` and `-2/720 = -0.0028`, printed
rounded), and **all four vertices at NDC (0, 0)**. There is no hotbar-shaped group (no group anywhere in
the leg has `ndc y_min < -0.5`) and no crosshair-shaped one (a centre quad would be small but non-zero).

**Every other non-world group in the leg is collapsed the same way**, under the leg's other target:

| target | fmt | texture | worldPass | ndc x / y / z |
|---|---|---|---|---|
| 417745098 | 2 | 43 | false | `x[-0.00..0.00] y[-0.00..0.00] z[0.50..0.50]` |
| 417745098 | 6 | 118 | false | `x[-0.00..0.00] y[-0.00..0.00] z[0.50..0.50]` |
| 417745098 | 6 | 119 | false | `x[-0.00..0.00] y[-0.00..-0.00] z[0.50..0.50]` |
| 417745098 | 6 | 120 | false | `x[-0.00..0.00] y[-0.00..0.00] z[0.50..0.50]` |
| 417745098 | 6 | 125 | false | `x[0.00..0.00] y[0.00..0.00] z[0.50..0.50]` |
| 417745098 | 7 | 113 | false | `x[-0.00..0.00] y[-0.00..-0.00] z[0.50..0.50]` |

That uniformity is the important caveat: **a collapse of every non-world group, across formats 2, 6 and 7
and across seven textures, is more consistent with the bounds instrument not reading those groups' vertex
data than with the game drawing its whole GUI at the origin.** The world-phase groups do have real ranges
(e.g. `fmt=6 tex=115 world=true x[-0.89..0.89] y[-8.09..-2.08] z[1.00..1.00]`, and the far-out
`fmt=5 tex=123 world=true x[10.26..2495.07] y[-51.83..0.26]`), so the instrument is not uniformly broken -
it is the non-world (GUI-phase) groups whose bounds read as zero. A per-vertex read of one of those groups
(print the 4 raw vertex positions and the stride used) is what separates "the GUI geometry is degenerate"
from "the bounds reader uses the wrong stride/layout for the GUI phase".

## 2. Their state — none of the four mechanisms can be what hides the HUD

From the same line: `depthTest=0 depthWrite=0` (no depth test at all, so `depthFunc=515` LEQUAL and the
world's `z=0.50` are irrelevant for it), `mask=0x7` (RGB writes on), `blend=0` (off), `alphaFunc=0`
(alpha test disabled) with `alphaRef=0.0`, `cull=1`.

The other non-world groups carry the states one expects from the GUI phase
(`blend=1 src=6 dst=7 srcA=0 dstA=1 mask=0x7 depthTest=0`; `blend=1 src=0 dst=3 mask=0xf depthTest=0`;
`blend=1 src=6 dst=7 mask=0xf depthTest=1`; `blend=0 mask=0xf depthTest=1`), i.e. they are ordinary GUI
blend/mask combinations, not a state that would erase them.

So: **a depth rejection, a colour-write mask, a blend pair or an alpha test cannot be what makes the HUD
invisible** — the identity-0 quad has depth testing and alpha testing off and writes RGB, and the other
GUI groups have plausible GUI state. If the HUD is missing from the presented frame, it is not these four
mechanisms; the two remaining candidates are the collapsed bounds above (geometry or instrument) and the
destination (question 3).

## 3. Which target the HUD's draws are addressed to

The leg has exactly **two** targets: `0` (43 groups, all the single quad of question 1) and `417745098`
(557 groups: the world's own, plus every non-world GUI-phase group of the table above).

So the HUD families (the `fmt=6`/`fmt=7` groups with GUI textures 113/118/119/120/125 and the `fmt=2`
ones with 9/43) are **addressed to `417745098`, not to identity 0** — identity 0 carries only the one
4-vertex quad. That is the expected shape if `417745098` is the game's own framebuffer (the HUD is drawn
into `mc.framebuffer` after the world, exactly as vanilla does), and it means the finding to chase is
whether `417745098` is the identity whose draws reach the **frame image** the present blits.

This artifact cannot answer that: it has no `CELTIUM_READ_RESOLVE` and no `CELTIUM_DRAW_SITE` line, so the
frame image's identity is not printed in it. The instrument that closes it already exists -
`CELTIUM_DRAW_SITE` (target/passImage) and `CELTIUM_WINDOW_DRAW` (identity/frameImage/chainPair) - and the
one-line test is whether `417745098` appears as the pass image of the HUD's groups.

## Counters (question 3 of the brief)

From the same log: `prepareDescriptorFailures=0`, `postAcceptDroppedGroups=0`,
`immediateGroupsKeptForOtherTargets=0`, `staleGroupRefusals=0`, `immediateGroupsDiscarded=0`,
`groupsStaleTargetGeneration=0`. (`formatRefusals` has no occurrence in this log; the name may differ or
the counter may not be printed in this leg shape.) So **no refusal, discard or hold is recorded for these
groups** - they are accepted and drawn.

## What this does and does not establish

- Established: identity 0 carries one 4-vertex quad per world frame and nothing else; every non-world group
  (including all the GUI families) is addressed to `417745098`; no state among depth/mask/blend/alpha can
  hide them; no refusal counter moves.
- Not established here: whether the collapsed NDC bounds are the game's geometry or the bounds reader's
  layout assumption; and whether `417745098`'s draws reach the frame image. Both are one instrument away,
  and neither is claimed.
