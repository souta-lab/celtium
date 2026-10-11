# Panorama re-take on the clip-fix jar: the clip is ruled out, and the faces' viewport is the next question

Owner: **fog-path**. Date: 2026-10-12, ~03:25 JST. GPU: AMD RX 6400 (RADV NAVI24).
Leg: `clip-menu` - fixture-free GL-less menu leg, jar `fog-path-clip.jar` md5
`c68ecefa127eacc9a07bad1509180f54` (the jar the Lead named: clip fix `9c7626783`, the clear classification and
the video-options fix), `enqAll`, `enqFormat=2`, `drawSiteLines=64`, `chainClearLog=12`.

## The copy is unchanged

```
CELTIUM_TEX_COPY_SERVED dest=20 … mean=255,255,255 opaque=65536/65536 served=1 frame=0
    readImage=0x7f8f14c35060
    lastDrawnImage=0x7f8f12f3bf70 lastDrawnFrame=0 drawnThisFrame=true
    lastDrawnEpoch=27 completedEpochNow=33 completionsSinceDraw=6
    passAttached=0x7f8f12f3bf70 passSubmissions=7 windowsThisFrame=7
```

and `CELTIUM_DRAW_SITE #1 … target=1938698341 … passImage=0x7f8f14c35060` - so **`readImage == passImage`**
still, six completions still elapse between the draw and the copy, and the read is still white. The clip fix
did not change this leg's copy.

## The clip is ruled out

**No `CELTIUM_GROUP_SCISSOR` line printed at all** in the leg (the line is bounded to four and prints on the
first scissored groups), so the faces' group carried no clip rectangle: `immediateScissoredGroups` is zero for
this run's groups and the scissor path never touched them.

## The viewport line, and what it says

Three `CELTIUM_GROUP_VIEWPORT` lines printed:

```
gl=0,0 1281x720 clipped=0,0 1280x720   target=1280x720
gl=0,0 1281x720 clipped=0,0 1280x720   target=1280x720
gl=0,0 256x256  clipped=0,464 256x256  target=1280x720
```

The third is a 256x256 viewport - the panorama's face size - converted to `0,464` in a 1280x720 target. That
conversion is the *correct* GL-bottom-left to Vulkan-top-left mapping for a viewport whose GL bottom edge is 0
(`720 - (0 + 256) = 464`), so the conversion itself is not obviously wrong. What it does mean is that the
faces' pass is drawn into a viewport in the **bottom band** of the target: if the geometry that pass submits is
expressed in the other convention (top-left NDC for a bottom-left viewport), the quad falls outside the
viewport rectangle and is clipped away by the viewport transform - which produces exactly the observed state:
the pass runs, the image keeps its clear, and the copy reads white.

## Next measurement (named)

The faces' group's **NDC bounds against that viewport rectangle**: `-Dceltium.vulkan.groupBounds=true
-Dceltium.vulkan.groupBoundsFormat=2` with the same leg, which prints the group's clip-space footprint so
"the quad lands inside `0,464..720`" and "the quad lands outside it" are distinguishable without another guess.
I attempted this leg once before (`r17b-bounds`) and it was killed in the harness slot wait, so it is still
owed; the viewport/NDC pair is now the only question left between the panorama and a fix.
