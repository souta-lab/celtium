# the sky plane's draw is issued every frame and its pixels are missing in most of them (2026-11-03)

The still-scene flicker (round 29: two consecutive frames of a still camera differ over 14.7% of the frame, all of it
the sky band) had one open question: is the flat frame a plane that is not drawn, or a plane drawn fully fogged? The
clear colour is the fog colour, so the picture cannot tell them apart. This round separates them, and the answer is
neither of the two the previous round assumed.

## What the offer census got wrong

Round 30's `CELTIUM_SKY_FRAME` printed the model view it read at the offer and reported `mvT=(2,226,-1779)` for every
world frame, which was read as "the plane is offered under a stale loading-screen matrix". It is not: the census reads
`glModelView` **before** the offer path refreshes it from the mirror, so the value it printed was the previous batch's
matrix (a GUI one). The group's own matrices are the camera's.

The new `CELTIUM_SKY_GROUP` census prints the group that holds the 676-vertex `POSITION` batch as it is drawn, on a
change of that state, and it is constant for the whole world:

```
CELTIUM_SKY_GROUP frame=6 target=true batches=1 proj=0.707,1.258,-1.0,-0.05 mvT=(0.0,-1.522,-0.504)
  fog=3/0.0/128.0 colour=0.471,0.655,1.0,1.0 state=blend0 depthTest1 depthWrite0 cull1 alphaRef0.5 depthFunc515
  mask15 viewport=0,0,1280,720 pipeline=780aa2c3 drawn=1
```

So the plane is offered, closed and **drawn** every frame with the camera model view, the world's fog and the sky
colour. The frame-to-frame difference is downstream of all of that.

## The two switches that separate the remaining shapes

| switch | what it changes | what it answers |
|---|---|---|
| `-Dceltium.vulkan.skyPoison=true` | the plane's own colour becomes magenta | did this draw's pixels reach the image at all |
| `-Dceltium.vulkan.skyNoFog=true` | the plane's fog selector becomes 0 | is a flat frame the plane drawn fully fogged |
| `-Dceltium.vulkan.skyNoDepth=true` | the plane's group is drawn with the depth test off | is the depth attachment what removes it |

The poison alone cannot separate "missing" from "fully fogged" (a fully fogged plane is the fog colour whatever its
colour is), which is why the no-fog switch exists: an unfogged plane is the sky colour.

## The measurement

Leg `r31c-skyposion` (plain/terrain, Supersymmetry, AMD RX 6400 RADV NAVI24, 1280x720, guiScale3, validation ON,
`worldLayers=3;bedrock,2*dirt,grass;1;`, `flickerArmTick=198`, `skyPoison=true`), the pair is frames 649/650:

| frame | y=0 | y=50 | y=100 | y=150 |
|---|---|---|---|---|
| prev (649) | (226,79,255) | (220,96,255) | (204,136,255) | (179,207,255) |
| cur (650) | (179,207,255) | (179,207,255) | (179,207,255) | (179,207,255) |

The poisoned plane is visible in one frame and absent in the next. Leg `r31d-skyfog` (`skyNoFog=true`) says the same
from the other side: the prev frame's sky is the sky colour (120,167,255) from y=0 to y=150, the cur frame's is the
fog colour. So the flat frame is **not** a fully fogged plane - the plane's pixels are not in the image.

Leg `r31e-skydepth` (`skyNoDepth=true`) reproduces the baseline exactly (`changed=135325/921600`, same gradient/flat),
so the depth attachment is not what removes the draw.

## The fix this round did land

`VulkanPushCache` is keyed by the command buffer's handle, and the handle survives `vkResetCommandBuffer` +
`vkBeginCommandBuffer` - but the push constants do not. The first draw of a re-recorded buffer could therefore elide
its push and be drawn with whatever the previous recording left. The cache is now invalidated at every reset/begin
(the frame's unified buffer and both recovery paths). It did not change this picture (`r31f-pushfix` is the baseline
again), so it is not the sky's cause, but it is a real correctness hole on the same "issued, counted, no pixels"
shape.

## Scope

One pack (Supersymmetry), AMD RX 6400, GUI scale 3, one pose, one pair per leg, a scene uncalibrated by construction
(`worldLayers` is part of the calibration key), so every leg's verdict is `UNCALIBRATED_PRESENTED_IMAGE` (exit 2).
Validation was clean on every leg (`vulkan-check passes=239 errors=0`, zero `VUID` lines). No performance claim.

## What is left

The draw command is issued, the depth test is not the cause, the push block is correct and the bind cache is reset per
flush. The remaining candidates are the window/pass the group is recorded into and the frame image it lands in; the
next instrument is the group's window index, its pass-attached colour image and the submission sequence at the draw.
