# Panorama re-attribution: the faces and the copy agree on the chain pair, and it is empty

Owner: **fog-path**. Date: 2026-10-12, ~02:25 JST. GPU: AMD RX 6400 (RADV NAVI24).
Leg: `pan-attrib` - fixture-free GL-less menu leg, `presentReadback=true`, `enqAll=true`, `enqFormat=2`,
`drawSiteLines=24`, `readResolveLog=8`, jar `fog-path-bh15.jar`, 120 s.

## The four instruments, in one frame

| fact | value |
|---|---|
| the faces' enqueue | `group=1 fmt=2 texture=8 batches=2 target=1941007851` |
| the composite's enqueue | `group=2 fmt=2 texture=6 batches=1 target=0` |
| the published name | `published glTexture=6 image=0x7f9a7cc7a4a0 view=0x7f9a7cc7ab40 size=1280x720` |
| the read's candidates | `frameImage=0x7f9a7cc1d5a0`, `defaultPair=0x7f9a72ddb0c0`, **`chainPair=0x7f9a7cc7a4a0`** |
| the copy | `CELTIUM_TEX_COPY_SERVED dest=20 readFbo=0 mean=255,255,255 opaque=65536/65536 served=1` |

**The published name is the chain pair image, not the frame image**: `chainPair == published image`
(`0x7f9a7cc7a4a0`), while `frameImage` is a different image (`0x7f9a7cc1d5a0`). So the composite (which samples
texture 6) and the faces (which are routed by `routeLabelForBinding(1941007851, false)` to the chain pair) agree
on the **same** image - and the copy, which now resolves `readFbo=0` to the chain pair, reads it too.

That removes the routing mismatch as the cause: all three agree. The image is simply **empty** - it holds the
clear, which is white.

## The instrument that says why produced nothing

`CELTIUM_DRAW_SITE` printed **zero lines** in this leg (`enqAll=true`, `enqFormat=2`, `drawSiteLines=24`). That
print sits in the draw loop (`VulkanTerrainGpuBackend.java:11724`) and is the companion of the enqueue, so its
silence is evidence in itself: with the enqueue switch on and fmt=2 selected, **no fmt=2 group reached that
draw loop in the whole run** - including the panorama's faces. The faces are offered for the chain pair and are
never drawn through the loop that would put them there, so the chain pair stays at its clear colour and every
consumer of it (the composite, the copy) reads uniform white.

## What is established, and what is next

- Established: the routing agrees (faces, published name and copy all on the chain pair); the white is the
  chain pair's **cleared content**, not a mis-resolved read and not a lost group (the units correction already
  removed that reading).
- Next measurement: why the offered groups do not reach the draw loop. The candidates are the draw loop's entry
  condition and the group's stamp (`groupFrame`/`groupGeneration`, which `CELTIUM_DRAW_SITE` would have printed
  had it fired). A bounded print at the offer->draw transition for one identity (`-Dceltium.vulkan.drawSiteLines`
  already gates it) with `enqFormat=-1` would say whether *any* group draws through that loop in a menu leg -
  which separates "the loop is never entered" from "these groups are filtered out of it".
