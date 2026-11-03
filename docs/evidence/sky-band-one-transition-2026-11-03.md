# the sky band is one transition, not a flicker, and the frame image holds it (2026-11-03)

Rounds 29-31 measured a still scene whose two consecutive frames differ over 14.7% of the frame, all of it the sky
band, and left one question: the plane's draw is issued and counted every frame, so where do its pixels go? This round
answers the shape of the difference first, and that answer changes the question.

## The difference is one transition, not an alternation

`-Dceltium.debug.flickerPairs=8` samples eight consecutive pairs instead of one. Leg `r32a-skyprobe` (plain/terrain,
Supersymmetry, AMD RX 6400 RADV NAVI24, 1280x720, guiScale3, validation ON,
`worldLayers=3;minecraft:bedrock,2*minecraft:dirt,minecraft:grass;1;`, `flickerArmTick=198`):

```
CELTIUM_FLICKER present=0 frame=587 changed=135325/921600 fraction=0.14684 maxDelta=37
CELTIUM_FLICKER present=0 frame=589 changed=0/921600 fraction=0.00000 maxDelta=0
CELTIUM_FLICKER present=0 frame=591 changed=0/921600 fraction=0.00000 maxDelta=0
... six more pairs, all changed=0 ...
```

One pair differs and the next seven are pixel-identical. So the sky does not alternate between two states: it changes
**once**, at the fixture's arm tick, and stays changed. Every earlier round read the single pair as a flicker because
one pair cannot tell a transition from an alternation.

## The frame image and the presented image agree

`-Dceltium.vulkan.skyFrameProbe=<n>` reads the frame image's sky band at the frame boundary (the image the present
blits from), and the flicker line now prints the same rows of both presented frames. Leg `r32b-skyimage`:

| frame | frame image `top` | frame image `quarter` | presented `top` |
|---|---|---|---|
| 575-579 | (148,186,254) | (146,171,196) | — |
| 580 | (148,186,255) | (179,207,255) | (148,186,255) (prev of the pair) |
| 581-584 | (179,207,255) | (179,207,255) | (179,207,255) (cur of the pair) |

The presented image lags the frame image by one frame and otherwise carries exactly what the frame image holds. So the
loss is **in the recording**, not in the blit or the present: the frame image itself stops holding the plane's pixels
at the transition. That rules out the present path, which was the last candidate the previous round's instrument could
not separate.

## The plane is drawn in the frame's first window, after the frame's colour clear

`-Dceltium.vulkan.skyDrawProbe` prints, at the draw itself, the frame target's clear counters and the window index.
Leg `r32d-skyclear`, every frame 570-584:

```
CELTIUM_SKY_DRAW frame=570 clears=4157 colourClears=572 windowsThisFrame=0 inPass=true target=true batches=1 vertices=676
CELTIUM_SKY_FRAME_PROBE frame=570 skyDraws=1 windows=22 colourClears=572 skyBand[top=(148,186,254) quarter=(145,170,196) ...]
```

`windowsThisFrame=0` means the plane is drawn in the frame's first window, `inPass=true` that it is inside a render
pass, and the colour-clear count is the same at the draw and at the frame boundary - so the frame's one colour clear
happened **before** the plane was drawn, not after it. The frame image receives seven clears a frame, exactly one of
them a colour clear; the other six are depth-only and cannot erase the sky.

## What the transition is attached to

The transition lands on the fixture's arm, which hides the GUI, clears the particles and pins the world time. The
window count drops from 22 to 18 in the same frame (`windows=22` before, `windows=18` after in `r32b`), so the arm's
own `hideGUI` is the only one of the three that changes the frame's structure. The transition frame is not fixed
(580 in `r32b`, 586 in `r32d`), which is what a per-run timing difference in the arm looks like and not what a
deterministic state change would.

## The code

- `VulkanTerrainGpuBackend`: `-Dceltium.vulkan.skyFrameProbe` / `skyFrameProbeLimit` (frame image's sky band at the
  frame boundary, plus a picture dump of the first probed frame), `-Dceltium.vulkan.skyPresentProbe` (the same band
  immediately before the present blit), `CELTIUM_SKY_DRAW` (clear counters, window index and pass state at the draw),
  `celtium$skyBandSignature` / `celtium$rowMean`, and the sky-group predicate is no longer gated on `skyCensus` - the
  poison and no-fog switches are diagnostics of that group, and gating them on a second property meant a leg that set
  only the switch changed nothing.
- `VulkanSwapchain`: the flicker line carries both frames' sky rows (`prevSky`/`curSky`/`prevSkyMid`/`curSkyMid`).
- `GllessFrameTarget`: `colourClearsApplied()`, the count of clears that named the colour buffer.

## Scope

One pack (Supersymmetry), AMD RX 6400, GUI scale 3, one pose, a scene uncalibrated by construction (`worldLayers` is
part of the calibration key), so every leg's verdict is `UNCALIBRATED_PRESENTED_IMAGE` (exit 2). Validation was clean on
every leg (`vulkan-check passes=239 errors=0`, zero `VUID` lines). No performance claim.

## What is left

The plane's pixels stop reaching the frame image at the arm, in the frame's first window, after the frame's colour
clear. The remaining candidates are the window the plane is recorded into and the pass that window opens: the next
instrument is the window's own index and the pass handle at the draw, compared against the window that carries the
frame's colour clear, plus the same probe on a leg whose arm does **not** hide the GUI - which separates `hideGUI` from
the world-time pin in one leg.
