# P0: the target-driven drain is reverted - it caused the device-lost

Owner: **fog-path**. Date: 2026-10-12, ~02:10 JST. GPU: AMD RX 6400 (RADV NAVI24).

## The reproduction

The configuration the Lead reported failing **3 of 3** legs on HEAD: terrain + `keepGui` + `captureEntities`,
GL-less, ~26 s legs, `vkDeviceWaitIdle returned -4` / `vkQueueSubmit returned VkResult -4`, exit 255, crash
reports present.

| jar | legs | `gpuStalled` | `stallOrigin` | `submitTimeouts` | device-lost strings | crash reports |
|---|---|---|---|---|---|---|
| HEAD with the drain (`e87aa0ec1`) | 3 of 3 failed (Lead) | true | `settleGpu:10953` / `endCommands` | - | `-4` present | present |
| **reverted** (`fog-path-revert.jar`) | **2 of 2 passed** | **false** | **none** | **0** | **0** | **0** |

Legs: `rev-terrain` (`presentedNonBackgroundFraction=0.952229`, `worldTicksAtEnd=210`,
`flushEntries=[2d041d34:entries=989,groupsAtEntry=3722]`), `rev-terrain2` (`0.951916`, 206,
`entries=1020,groupsAtEntry=3723`). Both `UNCALIBRATED_PRESENTED_IMAGE`, which is the scene key changing with
`keepGui`/`captureEntities` rather than a picture failure.

## What the drain did wrong (the mechanism, for the next attempt)

The drain set `currentRenderTarget`/`currentRenderTargetIsMain` to each pending target and ran the per-target
body, restoring both in a `finally`. The per-target body records acquire/release barriers and the copy-back for
**the identity it believes is bound**, and opens its pass against that identity's framebuffer - so running it
for a target that is not the one the command buffer was opened for records barriers and a pass for an identity
the frame's other state does not match. The driver answers that with `VK_ERROR_DEVICE_LOST` on the next wait or
submit, which is exactly the `-4` both reports carry. Restoring the fields afterwards does not undo what was
already recorded.

A correct version cannot just retarget the same body: it has to **open a pass per target** (bind that target's
framebuffer, re-issue the state it needs, record its own acquire/release pair) rather than borrow the current
command buffer's pass. OPT-10's constraint says the same thing from the other side: the state cache is
invalidated at target/pass boundaries, so a per-target pass must re-issue its state.

## What is kept

- The counters and instruments from the drain's unit: `unflushedTargetGroupsDropped`,
  `unflushedTargetPassFailures`, `lastUnflushedTargetFailure`, `drainedOtherTargetPasses` (zero while the drain
  is reverted) and `flushEntries=[identity:entries=…,groupsAtEntry=…]`, which now records the bound target's
  entry counts in the restored single-target flush. They read
  `2d041d34: entries=989/1020, groupsAtEntry=3722/3723` in the two legs.
- The attribution record: the flush filters by the bound target
  (`docs/history/2026-10-11-offtarget-flush-is-binding-driven.md`), which is still the mechanism the panorama
  and the loading screen's white window need fixed.

## Honest note on the drain's own acceptance

Its acceptance leg never showed a benefit: `offTargetPerTarget` stayed at `37a8b711: offered=622, atPass=30,
drawnPasses=16` against 677/32/17 before, with zero drops - the one-shot target's groups are not in
`immediateGroups` when any flush runs, so the drain had nothing to drain. The P0 and that null result together
are why the revert costs nothing measured.
