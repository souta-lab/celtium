# The splash frame's ordering: can the composite read the Framebuffer before its draws are submitted?

task-76. Read-only audit; no Java edited, no leg run. file:line for every claim.

## 1. Which submission carries what, and the ordering primitive

- The splash's logo batches are offered with `target = 0x47869b33` (the FBO's identity, non-zero and non-main), so they
  belong to the **chain pair** (`GllessFrameTarget.pairLabelForIdentity`; the routing branch
  `chainIdentity = this.currentRenderTarget != 0L` in `recordImmediateOnlyPass`).
- The composite is offered with `target = 0` (the window), i.e. the **default pair**.
- The switch between them is a framebuffer bind, and `bindFramebuffer` flushes on a target change:
  `if (!this.immediateWindowBusy && targetIdentity != this.currentRenderTarget && !this.framebufferReplacing)
  flushPendingBeforeGlDraw();` (`VulkanTerrainGpuBackend.java:4546`).
- `flushPendingBeforeGlDraw` either draws into an already-open pass (`drawPendingImmediate`, same command buffer, so
  ordering is command order) or opens an immediate window, and that window **submits and waits**:
  `submitCommands(); if (!waitForIdle()) throw …; this.frameTarget.endFrame();` (`flushImmediateWindow`). The ordering
  primitive is therefore a **fence wait** (`waitForIdle` on the submission's fence), not submission order alone.
- Consequence: while the pair's window is submitted and waited for before the composite's window is recorded, the
  composite cannot read the pair before the pair's draws are complete. Ordering is correct in the code as it stands.

## 2. The published-image sampling: live image, and what a too-early read would return

- `PublishedFrameTextures` publishes the pair's image under the FBO's colour texture name, and the sampling batch
  resolves that name through the registry to the **pair's live image** (not a host mirror). So a read that happened
  before the pair's window was submitted would return the pair's *cleared* content - the clear the pair receives at its
  first use per frame (`clearChainPair` -> `Pair.clear`, `OFF_TARGET_CLEAR_RED/GREEN/BLUE/ALPHA`) - i.e. white, which
  is exactly the symptom.
- That is why the drop below matters: a dropped window leaves the pair cleared, and the composite then samples white.

## 3. The failure that was actually measured - a drop, not a missing barrier

- `flushPendingBeforeGlDraw` has a replacement branch that **drops** the pending groups instead of drawing them:
  `this.replaceBoundaryDrops++;` and the bounded line `CELTIUM_REPLACE_DROP batches=… target=…` (`:6802-6805`), taken
  when `framebufferReplacing` is set.
- The comment above `celtium$noteBindSize` records the measured instance verbatim: the hint used to fire for every
  framebuffer whose size differed, and "the next ordering flush **dropped the pending groups** instead of drawing them
  (`CELTIUM_REPLACE_DROP batches=1 target=854x480 frameId=0`, measured on the splash's own composite batch at
  startup). That drop is what kept the loading screen off the window." (`:4192-4198`).
- The fix is in the same place: `celtium$noteBindSize(width, height, mainFramebuffer)` now sets the hint **only for the
  main framebuffer** (`:4199-4204`), so the differently-sized splash FBO no longer announces a replacement and no
  longer causes the drop.
- So: ordering was never the missing piece; the pair's window was being *discarded* before it was submitted. With the
  gating in, `replaceBoundaryDrops` must be 0 in a splash leg, and if white persists the cause is downstream of this
  fork.

## 4. Does the same question apply to the menu, and why does the menu work?

- The menu's composite has the same shape: it samples a published name (the skybox texture) and its own draws land in
  identity 0. It works because nothing in the menu ever announces a replacement - there is no differently-sized
  framebuffer bind - so the ordering flush draws the panorama's window instead of dropping it, and the composite then
  samples a pair that holds the faces.
- The difference in the call sequence is therefore not the ordering primitive but the *presence of the replacement
  hint*: the splash binds a framebuffer of another size, the menu does not.

## Verdict

| point | verdict | deciding evidence |
|---|---|---|
| the composite can run before the pair's draws are submitted | **ruled out** | the target switch flushes (`:4546`) and the window path submits + waits on its fence (`flushImmediateWindow`: `submitCommands(); waitForIdle()`); same-command-buffer case is command order |
| the pair could be read while still cleared | **possible only if the window was dropped** | the drop branch `:6802-6805` plus the measured `CELTIUM_REPLACE_DROP … frameId=0` on the splash's composite batch (`:4192-4198`) |
| the drop still happens on the current tree | **ruled out by the gating** | `celtium$noteBindSize(w, h, mainFramebuffer)` fires only for the main framebuffer (`:4199-4204`) |
| ordering is the missing barrier/flush | **no** | no barrier or flush is missing; the missing piece was the *drop*, and it was removed by the gating |

**Counters that decide it without a new leg**: `replaceBoundaryDrops` and any `CELTIUM_REPLACE_DROP` line (must be 0
in a splash leg now), `otherTargetGroupsDiscarded` / `immediateGroupsKeptForOtherTargets` (a pair group that was kept
rather than drawn), and `offTargetPairClears=[default-pair=n, chain-pair=n]` (that the pair was actually cleared and
used). If all of those read as expected and the screen is still white, the remaining fork is the one my task-75 audit
named: the composite's own target is identity 0, i.e. the default pair - not the frame image the present blits.
