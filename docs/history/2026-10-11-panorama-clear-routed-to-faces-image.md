# The panorama's mechanism: the faces draw, then a clear for another framebuffer lands on their image

Owner: **fog-path**. Date: 2026-10-12, ~02:45 JST. GPU: AMD RX 6400 (RADV NAVI24).
Leg: `ord-menu` - fixture-free GL-less menu leg, `enqAll=true`, `enqFormat=-1`, `drawSiteLines=64`,
`readResolveLog=8`, jar `fog-path-ord.jar`, 120 s.

## The three facts, in log order

| line | evidence |
|---|---|
| 238 | `CELTIUM_DRAW_SITE #1 frame=0 fmt=2 site=immediate-only-window target=1938698341 onMain=false passOpen=true vertices=8 indices=12 batches=2` - **the panorama's faces draw**, at frame 0, into the image their identity routes to |
| 798 | `CELTIUM_READ_RESOLVE readFbo=0 … frameImage=0x7f4ce0c4… defaultPair=0x7f4ce0c2… chainPair=0x7f4ce0c76290` |
| 801 | `CELTIUM_TEX_COPY_SERVED dest=20 … mean=255,255,255 opaque=65536/65536 served=1 frame=0 **readImage=0x7f4ce0c76290**` - the copy reads **the chain pair**, the image the faces were routed to |

So the draw and the read are in the same frame, on the same image, and the read is white.

## What happens between them: the clears

Between line 238 and line 801 the log carries **five `CELTIUM_CLEAR_LATCH` lines**, and their masks and colours
are the answer:

```
CELTIUM_CLEAR_LATCH route=pair boundIdentity=0x414f9922 boundIsMain=false mask=0x4000 colour=(1.0,1.0,1.0,0.0)
CELTIUM_CLEAR_LATCH route=pair boundIdentity=0x594b7289 boundIsMain=false mask=0x4100 colour=(1.0,1.0,1.0,0.0)
CELTIUM_CLEAR_LATCH route=pair boundIdentity=0x574c473e boundIsMain=false mask=0x4100 colour=(1.0,1.0,1.0,0.0)
CELTIUM_CLEAR_LATCH route=pair boundIdentity=0x594b7289 boundIsMain=false mask=0x4100 colour=(0.0,0.0,0.0,0.0)
CELTIUM_CLEAR_LATCH route=pair boundIdentity=0x574c473e boundIsMain=false mask=0x4100 colour=(0.0,0.0,0.0,0.0)
```

Every one of those identities is **non-main and non-zero**, so `routeLabelForBinding` routes the clear to the
**chain pair** - the same image the faces drew into - and the first one clears it to **white** (`mask=0x4000`,
`colour=(1.0,1.0,1.0,0.0)`). None of them is the faces' identity (`1938698341`).

## The mechanism

**The panorama's faces are drawn into the chain pair, and then the game's clear of a different framebuffer is
routed to that same pair and wipes it to white before the copy reads it.** This is BH-16 exactly: the clear's
target is not preserved, so a clear issued against framebuffer A lands on the image that framebuffer B's content
is in. The loading screen's white window is the same shape one frame earlier.

## What the fix has to be (BH-16)

The clear has to carry the target it was issued against, and be applied to that target's image only - or be
refused and counted when the target is not the one its routing would give. The `route=pair` line already
records the identity and the mask; what is missing is the check that the identity the clear names is the
identity whose pair is about to be cleared. The counter to add is "clears applied to a pair whose current
content belongs to another identity", so the next leg reads the loss instead of inferring it.

## Method notes

- The `readImage=` and `frame=` fields on `CELTIUM_TEX_COPY_SERVED` were added for this leg; without them the
  draw and the read could not be placed in one frame, and the ordering question stayed unanswerable.
- `immediateDrawSiteLogged` now reaches the report, so "zero lines" can no longer be confused with "the limit
  was reached" - the earlier zero-line claim in this investigation was a grep pattern that could not match
  `site=immediate-only-window` (hyphens), not a missing draw.
