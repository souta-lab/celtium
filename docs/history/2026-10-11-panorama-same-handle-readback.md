# The panorama's last comparison: same handle, and the readback is reading a genuinely white image

Owner: **fog-path**. Date: 2026-10-12, ~07:15 JST. GPU: AMD RX 6400 (RADV NAVI24).
Leg: `hold-menu` - fixture-free GL-less menu leg, `menuHoldTicks=60`, `enqAll`, `enqFormat=2`,
`drawSiteLines=8`, jar `fog-path-hold.jar`.

## The comparison the line narrowed to

```
CELTIUM_DRAW_SITE #1 frame=0 fmt=2 site=immediate-only-window target=510345185 passImage=0x7f6b74829930
CELTIUM_TEX_COPY_SERVED dest=20 … mean=255,255,255 … frame=0 readImage=0x7f6b74829930
```

**The handles are the same** (`0x7f6b74829930`), in the same frame - the third leg in a row that reads this
way (`comp-menu`, `clip-menu`, `alpha-on` all agree). By the decision tree that makes the readback the owner.

## But the readback is not misreading

The write itself is white, and two independent readings say so:

- `CELTIUM_GROUP_BOUNDS … fmt=2 texture=8 colour0=0x0 **vertexColour0=0xffffffff**` - the vertex's packed
  colour, read out of the group's own vertex memory, is **opaque white**;
- `CELTIUM_MIRROR_SOURCE id=8 … size=256x256 content=**opaque=65536/65536 firstOpaque=0xffffffff**
  announced=0 hasEntry=true full=true` - the sampled texture's mirror is a **uniformly opaque white** image;
- the fragment writes `texture(u_Texture, v_TexCoord) * v_Color` with no discard (`alphaFunc=0`, `alphaRef=0.0`,
  and the A/B with `-Dceltium.vulkan.alphaTest=false` leaves the copy's mean unchanged).

So the faces draw white into the image the copy reads, and `nonWhite=0` over the 65536 readback pixels is the
**correct** read of an image that was written white. The readback's layout, format and extent are not what makes
it white; the texture it samples is.

## The owner, named

**Texture id 8's mirror content.** Everything upstream of the sample is measured correct: the routing
(`routeLabelForBinding`), the read resolution (same handle), the pass attachment, the clip (no scissor on the
group), the viewport (full frame in this leg), the clears (the pair's clear precedes the draw, and the foreign
clears are no longer dropped wrongly), the submission (six completions between draw and copy), the vertex
attribute (opaque white), and the fragment (no discard). What is left is that the mirror holds white: whatever
fills it filled it with white, or the announcement behind it (`announced=0`, `hasEntry=true`, `full=true`)
carried white.

Next measurement, on the mirror/announcement path rather than the draw path: the announcement record behind id 8
against the mirror's content (size, source, first texel), and whether the panorama's real source is a different
GL name that never got mirrored at all.
