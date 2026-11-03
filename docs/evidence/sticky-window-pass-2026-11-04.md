# The sticky window pass: the merge fires, and the frame is 4x slower

2026-11-04, round 52. Renderer `969e4c54` (switch on) against `024f5979` (round 51's baseline).
AMD RX 6400 (RADV NAVI24), world scene `qmagnet`, 240 s, `guiScale3`, warm window ticks 400–600,
`holdUntilTick=620`, `-Dceltium.vulkan.stickyWindowPass=true`, `passSplit` + `frameBudget` + `frameStats` on.
Legs `r52a-sticky` … `r52f-sticky`; only `r52f-sticky` is kept.

## The hypothesis

A window is the renderer's most expensive unit: 23.5 a frame, ~19 µs of recording each, and the whole flush is
0.4 ms of a 1.4 ms frame. Most of that is fixed cost per window — the begin/end render pass pair, the frame image's
acquire/release round trip, the target routing and the submission bookkeeping — while the average window carries six
groups. The pass is over the same framebuffer with the same attachment layout every time, so the next window's groups
could be recorded into the pass that is already open. That is exactly what the in-pass ordering flush already does
inside one window.

## What was built

`-Dceltium.vulkan.stickyWindowPass=true` (default **off**):

- `recordImmediateOnlyPass` leaves the pass open when the window draws into the frame image, the unified frame buffer
  is open and the device is not stalled.
- `flushImmediateWindow` merges a window into that pass when the decision table
  `celtium$stickyMergeAllowed` says so (same target, same render pass, GL-less target, no acquire needed, no
  framebuffer replacement). `StickyWindowPassTest` pins the table (9 cases).
- `celtium$closeStickyPass()` ends the pass and records the release barrier the merged windows skipped. It is called
  from `beginCommands`, `celtium$applyPendingRawWrites`, `celtium$finishFrameBuffer`, the frame boundary, the
  non-merge window path, and — through `VulkanContext.celtium$beforeBarrier` — from every barrier site, because a
  barrier cannot be recorded inside a render pass.

## What the legs measured

| leg | what changed | `stickyWindowsMerged` | warm p50 |
|---|---|---|---|
| r52a | first cut | — | validation **dirty**: `VUID-vkCmdPipelineBarrier-None-07889` |
| r52b | barrier hook added | 0 | PASSED, no merge |
| r52c | `currentIsMain()` instead of `identity == 0` | 0 | 5.7 ms frame, `stickyBarrierCloses=23852` |
| r52d | window's own release skipped when the pass stays open | 0 | **38.7 ms** frame, 2 fps |
| r52e | close when another target is queued | 0 | 5.3 ms frame |
| r52f | in-pass flush branch bypassed while the pass is open | **6260** (2.6/frame) | **11.47 ms** |

Baseline (round 51, same window, same flags): `frameIntervalWarmP50Ms` **2.85**, `frame` p50 1.4,
`flush` 0.4, `winRecord` 0.3, `wrecLoop` 0.2, `grpDraw` 0.1.

r52f, the only leg where the merge actually fired:

```
frameIntervalWarmP50Ms = 11.47   (baseline 2.85)
frame[p50=6.3] world[p50=5.1] gui[p50=1.1]
flush[p50=3.1] winRecord[p50=2.6] wrecLoop[p50=1.8] grpDraw[p50=1.3] winHead[p50=0.5]
immediateWindows=49211 (20.8/frame, baseline 23.5)  immediateGroupsDrawn=320532 (135/frame, baseline 142)
```

## What it means

The merge works and removes 2.6 windows a frame, and the frame is **4x slower**. The windows are not the cost the
merge removes: `grpDraw` goes from 0.9 µs a group to **9.6 µs a group** while the group count is unchanged (135 a
frame). The per-draw driver call is what got expensive, not the per-window fixed cost.

That is not explained by anything measured here. The candidates, none of them tested:

- one render pass over the whole frame changes what the driver can do per draw (RADV's per-pass state setup);
- the pass's depth attachment is loaded once instead of per window, so the depth test's work per draw changes;
- the frame's command buffer is much larger, and `vkCmdDrawIndexed` into it costs more.

Until one of those is separated, the approach is **rejected**: the switch stays default off, and re-opening it needs a
measurement that names why a draw costs 10x inside a long pass. The infrastructure it added is kept because it is
correct on its own: the barrier hook (`VulkanContext.celtium$beforeBarrier`) is what makes any future in-pass
recording legal, and the close points are the complete list of places a pass may not outlive.

## What is not claimed

Nothing here is a picture comparison: every leg passed the fixture and the presented-image verdict, but the switch is
off by default, so no shipped picture changed. `r52d`'s 2 fps and `r52f`'s 11.47 ms are measurements of the switch,
not of the renderer.
