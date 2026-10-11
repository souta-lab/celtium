# Panorama: the draw reaches the image - the sampled texture is white

Owner: **fog-path**. Date: 2026-10-12, ~03:45 JST. GPU: AMD RX 6400 (RADV NAVI24).
Legs: `alpha-on` / `alpha-off` - fixture-free GL-less menu legs, jar `fog-path-alpha.jar`,
`enqAll`, `enqFormat=2`, `groupBounds=true`, `groupBoundsFormat=2`; the second with
`-Dceltium.vulkan.alphaTest=false`.

## The fragment-level A/B: the alpha test is not the cause

| leg | copy | group |
|---|---|---|
| `alpha-on` | `mean=255,255,255`, `readImage=0x7f4acc405700`, `completionsSinceDraw=6` | `fmt=2 texture=8 colour0=0x0 **vertexColour0=0xffffffff**` |
| `alpha-off` (`-Dceltium.vulkan.alphaTest=false`) | `mean=255,255,255`, `readImage=0x7fe410ccf8e0`, `completionsSinceDraw=6` | `fmt=2 texture=8 colour0=0x0 vertexColour0=0xffffffff` |

The mean is **identical** with the alpha test off, so no discard is eating the faces - which matches the shader
reading (`alphaFunc=0` is the shader's own "test disabled" marker and `alphaRef=0.0` does not take the combine
branch).

## `colour0=0x0` is a captured-state value, not the attribute

The new `vertexColour0=` field reads the first vertex's packed colour **out of the group's own vertex memory**
(stride from the mesh layout, colour as the last four bytes of the vertex): it reads **`0xffffffff`** - opaque
white. So the attribute is fine, `colour0=0x0` is what the state capture produced, and the fragment writes
`texture × v_Color` = `texture × white` = **the texture's own content**.

## The texture is white

`CELTIUM_MIRROR_SOURCE id=8 source=announced-int[] size=256x256 content=**opaque=65536/65536
firstOpaque=0xffffffff** announced=0 droppedAnnouncements=0 hasEntry=true stale=false full=true glLess=true`

Texture 8's mirror is a **uniformly opaque white 256x256 image**: every texel opaque, the first one white. The
faces sample it and write white - which is exactly the pair's clear colour, so the result is indistinguishable
from "nothing was drawn" until the attribute and the mirror are read together.

## What that means

- The draw **does** reach the image: the pass is attached, the vertex colour is opaque white, nothing discards,
  and the fragment output is the sampled texture.
- The panorama's white is therefore a **mirror-content** defect, not a routing, clip, viewport, clear or
  submission defect: texture 8's mirror holds white (`announced=0`, `hasEntry=true`, `full=true`), so whatever
  fills mirrors filled this one with white, or the announcement it came from carried white.
- Next measurement: the announcement that produced id 8's mirror (`source=announced-int[]`, `announced=0`) - its
  announced size/content against the mirror's - and whether the panorama's real source is a different GL name
  that never got mirrored. That is the texture-mirror/announcement path, not the immediate draw path.
