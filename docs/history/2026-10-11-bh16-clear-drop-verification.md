# BH-16's clear-target fix: it fires (428 drops), and the panorama's copy is still white

Owner: **fog-path**. Date: 2026-10-12, ~02:55 JST. GPU: AMD RX 6400 (RADV NAVI24).

## The fix fires

Fixture leg `bh16-terrain` (GL-less, `--validate`, scene=terrain, `keepGui`, jar `fog-path-bh16.jar` sha256
`9c08d704d869adb9161e96732b7bb1cdc7ab71b0c9077d5c6d6a35259824eab8`, the jar carrying the Lead's
`pendingClearDrawFbo` + `gameClearsDroppedForeignTarget` change):

| counter | value |
|---|---|
| `gameClearsDroppedForeignTarget` | **428** |
| `lastDroppedClearOwner` | **1** |
| `gpuStalled` | false |
| `unflushedTargetGroupsDropped` | 0 |
| `presentedImageVerdict` | `FAILED_PRESENTED_IMAGE` (was PASSED in this configuration before the fix - see below) |

So a clear whose owner is a non-zero framebuffer is now dropped rather than applied to the shared pair, exactly
as the mechanism required, and the count is large.

## The copy is still white

Menu leg `bh16-menu` on the **same jar** (fixture-free, `enqAll`, `enqFormat=2`, `drawSiteLines=64`):

```
CELTIUM_DRAW_SITE #1 frame=0 fmt=2 site=immediate-only-window target=1938698341 onMain=false passOpen=true
    vertices=8 indices=12 batches=2 ... passImage=0x7effd8c71390
CELTIUM_TEX_COPY_SERVED dest=20 ... readFbo=0 mean=255,255,255 opaque=65536/65536 served=1 frame=0
    readImage=0x7effd8c71390
```

**`passImage == readImage` in the same frame, and the read is still white.** The five
`CELTIUM_CLEAR_LATCH route=pair … boundIdentity=0x62b7b4d6/0x6d2dacee/0x3e3e1276` entries still appear between
the draw and the copy in this leg (the latch line records the request; the drop happens later, in
`applyGameClears`), and the copy's content is unchanged.

## What that leaves

With the clear no longer wiping the pair and the two handles matching, the remaining candidate is **ordering /
submission**: `CELTIUM_DRAW_SITE` prints at **record** time (line 239 of the log) and the copy reads at line 801,
but recording order is not execution order - if the faces' pass is recorded into a command buffer that is
submitted after the copy's read, the copy sees the image as it was before that pass, which is its clear. The
next measurement is the submit boundary: the frame's submission sequence against the copy's position, or a
readback that says whether the pass had executed (a fence/timestamp on the pair at the copy).

## Flag: the picture gate

`bh16-terrain` reads `FAILED_PRESENTED_IMAGE` in a configuration that read `PASSED` on the pre-fix jar
(`stage-leg`, same flags, `nonBg=0.952229`). Per `AGENTS.md` a failing gate is evidence: dropping 428 clears may
be dropping clears the frame needed (the game's own background clear of a non-zero framebuffer that *is* the
delivered image in this configuration). Worth checking before the drop rule is kept as-is: the owner rule
(`owner != 0` drops) and the identity rule (the pair's content belongs to another identity) are not the same
test, and this leg cannot tell them apart yet.
