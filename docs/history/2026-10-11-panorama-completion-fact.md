# The copy's completion fact: it reads exactly the image the faces drew into, six submissions later

Owner: **fog-path**. Date: 2026-10-12, ~03:15 JST. GPU: AMD RX 6400 (RADV NAVI24).
Leg: `comp-menu` - fixture-free GL-less menu leg, `enqAll`, `enqFormat=2`, `drawSiteLines=64`,
`chainClearLog=12`, jar `fog-path-comp.jar`.

## The line

```
CELTIUM_TEX_COPY_SERVED dest=20 level=0 source=0,0 256x256 readFbo=0 mean=255,255,255 opaque=65536/65536
    served=1 frame=0 readImage=0x7f1730da4b20
    lastDrawnImage=0x7f16437a7bb0 lastDrawnFrame=0 drawnThisFrame=true
    lastDrawnEpoch=27 completedEpochNow=33 completionsSinceDraw=6
    passAttached=0x7f16437a7bb0 passSubmissions=7 windowsThisFrame=7
```

and the draw sites in the same frame:

| site | target | passImage |
|---|---|---|
| `DRAW_SITE #1` | `781913368` (the faces) | **`0x7f1730da4b20`** |
| `DRAW_SITE #2..#4` | `0` | `0x7f16437a7bb0` |

Resolving the handles: **`0x7f1730da4b20` is the chain pair** (and equals the copy's `readImage`), while
`0x7f16437a7bb0` is the default pair (the `target=0` groups' pass) and the frame image is a third handle,
`0x7f1731450ae0`.

## What that says

- **The copy reads exactly the image the faces drew into** (chain pair), not a mis-resolved one - the read
  resolution and the routing are both correct, which is the third independent confirmation of that.
- **Six submissions completed between the faces' draw and the copy** (`completionsSinceDraw=6`,
  `lastDrawnEpoch=27` → `completedEpochNow=33`), so the copy is not simply "early" in a naive sense.
- `lastDrawnImage`/`passAttached` at the copy is a *different* image only because later `target=0` groups drew
  into the default pair afterwards; that is a red herring for this question, and the faces' own pass image is
  the one that matters.

## What is left

The chain pair is written by the faces, read by the copy, six completions later, and is white. The remaining
candidates are all about the faces' own **submission**, not the image choice:

1. the window that recorded the faces' pass was **abandoned or reset** before submission (the flush retry path
   resets the command buffer and re-records; a group recorded into the discarded attempt would never execute);
2. the faces' pass was submitted into a command buffer whose submission **failed or was skipped**;
3. the pair was cleared again by a later point in the frame that the bounded `CELTIUM_CHAIN_CLEAR` line did not
   capture (the line is limited to 12, and `chainPairClearedFrame` guards one clear per frame - so this is the
   least likely of the three).

The measurement that separates them is a per-window record for the chain pair's pass: whether the window that
recorded it reached `submitCommands` or was abandoned, with the abandon reason. `windowFlushFailures`,
`unflushedTargetPassFailures` and `immediateGroupsDiscarded` all read zero in the legs so far, which is what
makes "the pass recorded and then discarded" the live hypothesis rather than an observed fact.
