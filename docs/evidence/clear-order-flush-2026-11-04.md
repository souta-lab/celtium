# The clear-order flush was not the window source (round 61, 2026-11-04)

Round 60 concluded that the frame's 23.4 windows are opened by `glClear`: `clearOrderFlushes` 28.3 a frame →
`flushBeforeGlDrawWithGroups` 22.3 → `immediateWindows` 23.4, and "95% of the flushes are clear-order flushes".
This round tested that by removing the clear-order flush where it provably cannot be needed, and the window count
did not move. The inference was wrong, and the reason is in the counters: `clearOrderFlushes` counts **calls**, and
most of those calls returned before opening anything.

## What was tried first, and why it failed

The planned change was to let the clear ride the draw queue: `celtium$gameClear` already carries a non-main clear as
a `postClear` marker on the last queued group of the clear's own target, and the group's pass replays it as
`vkCmdClearAttachments` between the draws before and after. The flush ran first and emptied the queue, so that arm
was never reached for a clear issued through `GlStateManager.clear`.

Leg `r61a-defer` (renderer `44a0934d…`, AMD RX 6400, 1680 world frames) measured the marker route:

```
clearOrderFlushes=72874  clearOrderDeferred=0
celtium/clears[main=38690 foreign=8058]
```

**It never fired once.** The reason is in the clear log: every foreign clear logged `queued=1 lastQueuedTarget=0x0`
and `route=latch` — the queue held a group for the *window* identity while the clear named a mod's framebuffer, and
a mod clears its framebuffer right after binding it, before it has queued a draw. There is no group of its own
target to carry the marker. The marker route is dead for this scene.

## What the clears actually are

`-Dceltium.vulkan.clearCallers=true` names the caller of each main-framebuffer clear (bounded to 16 lines). Two
families, and the second is the majority:

| caller | mask | colour |
|---|---|---|
| `net.minecraft.client.renderer.GlStateManager#func_179086_m` | 0x4100 / 0x0100 | fog colour / — |
| `com.cleanroommc.modularui.drawable.Stencil#reset` | **0x400** | (1,1,1,0) |

`0x400` is `GL_STENCIL_BUFFER_BIT`. **This renderer does not implement stencil clears at all** — `celtium$gameClear`
and `celtium$recordQueuedClearsInPass` handle colour (0x4000) and depth (0x0100) only. So the dominant clear in the
measured scene erases nothing the renderer owns, and it was still submitting the GUI batch it cannot touch: one
window per ModularUI widget.

## The change

`flushBeforeClear` now skips the ordering flush for two reasons that are provable from the clear itself:

1. **The mask names no attachment this renderer owns** (`(mask & 0x4100) == 0`). The rule was written for `mask == 0`
   alone; it is the same rule for stencil. Pinned by `celtium$clearNamesOwnedAttachment`.
2. **The clear's own image has no pending draws** (`celtium$clearNeedsOrderingFlush`). The ordering rule is about one
   image, but the flush drew every target in the queue. Two identities share an image and are treated as one: the
   main identity and identity 0 both draw into the frame image (`routeLabelForBinding`); every other non-main
   identity owns its own chain pair. Pinned by `celtium$clearNeedsOrderingFlushDecision`.

Both are behind `-Dceltium.vulkan.clearSkipFlush` (default on). The marker route was removed rather than kept: it
never fires, and a switch that cannot fire is a claim without a measurement.

Also fixed: a `postClear` attached to a **model-run** group was silently dropped, because the draw loop's model-run
arm `continue`d past the replay. Invisible while the flush emptied the queue before every clear; a lost clear the
moment a clear rides the queue.

## What it bought, and what it did not

| leg | renderer | world frames | `clearOrderFlushes` | skipped | no-target-mask | `flushBeforeGlDraw` | with groups | windows |
|---|---|---|---|---|---|---|---|---|
| `r61a-defer` | `44a0934d…` | 1680 | 72,874 | 0 | 0 | 143,135 | 57,313 | 59,948 |
| `r61b-skip` | `64e0f1d2…` | 1728 | 74,752 | 48,129 | 0 | 98,915 | 59,084 | 61,800 |
| `r61c-mask` | `e4395400…` | 1538 | 74,428 | 37,350 | 15,846 | 93,090 | 53,621 | 56,318 |

**71% of the clear-order flush calls are gone** (53,196 of 74,428) and `flushBeforeGlDraw` fell 35%. **The window
count per frame did not fall**: 35.7 → 35.8 → 36.6. The skipped calls were calls that would have returned at the
"no groups" check anyway, or that drew groups the next flush would have drawn a moment later.

So the round-60 chain `clearOrderFlushes → flushBeforeGlDrawWithGroups → immediateWindows` was a coincidence of
magnitudes, not a causal chain. In `r61c-mask`, clear-order flushes that reach `flushPendingBeforeGlDraw` are
21,232 (13.8 a frame) against 53,621 flushes with groups (34.9 a frame): **at most 40% of the flushes with groups
can be clear-order flushes, and the other ~21 a frame come from the other call sites.**

## The picture

All three legs PASSED with `problems=[]`, `fixtureStatus=PASSED`, `sceneCalibrationKeyMatches=true`,
`sceneGateCalibrated=true`, `validationMessageCount=0`:

| leg | distinct colours | black fraction | non-background |
|---|---|---|---|
| `r61a-defer` | 76,633 | 0.006975 | 0.989756 |
| `r61b-skip` | 76,472 | 0.007216 | 0.989756 |
| `r61c-mask` | 76,873 | 0.007423 | 0.990197 |

The world picture of `r61c-mask` was inspected: terrain, coloured blocks, entities, HUD text, minimap, hotbar icon,
crosshair and the held item all render correctly.

## Not a performance claim

The three legs ran under a load average of 1.3–3.0 with zram swap in use, and their warm p50 is 8.9–10.1 ms against
`r60a-base`'s 3.49 ms on the same scene. **The legs are not comparable to each other or to round 60 in absolute
terms**, and no frame-time claim is made. What is claimed is the counter arithmetic above, which is per-frame and
independent of the frame rate.

## The window source, named

`-Dceltium.vulkan.flushReasonCensus=true` stack-walks each flush and names the first frame outside the renderer. It
had never been read in a leg because it prints to stdout and the log limiter keeps only the first sixteen lines of a
tag - all of them frame 0, the loading screen. The census now also rides the one-line diagnostics
(`flushReasons=[...]`), and leg `r61e-sites` (renderer `41524dd2…`, AMD RX 6400) read it:

```
flushReasons=[Framebuffer.handler$…$noteRenderTarget=33260
              GlStateManager.handler$…$clear=9959
              Framebuffer.handler$…$beforeUnbind=2656
              RenderSectionManager.renderLayer=2490
              CeltiumGllessHint.reportRawFramebuffer=2490
              WorldVertexBufferUploader.handler$…$offerBatch=1
              TextureCopyService.celtium$readPixels=1
              flushSiteCounts=raw-write=1 bind-framebuffer=53146]
```

**65% of the flushes with groups (33,260 of 50,857) come from a framebuffer bind**, not from a clear. The mixin
frame is `FramebufferMixin.celtium$noteRenderTarget`, which calls `backend.bindFramebuffer(identity, isMain)`; the
backend's target-change flush is the one that opens the window. `flushSiteCounts=bind-framebuffer=53146` says the
same thing from the other side. Clears are 9,959 - **20%**, not 95%.

So the round-60 conclusion was wrong twice over: the windows are opened by **binds**, and the clear-order flush was
never the mechanism. The next question is the one the bind flush has to answer: the queue is per-target and a window
draws only the current target's groups, so a bind that changes target could leave the old target's groups queued for
a later window of their own instead of drawing them now. That is the hypothesis round 62 should test.
