# The device is lost by an unconsumed `waitSemaphore` signal: an abandoned glless frame signals twice

Owner: **fog-path** (acceptance blocker, round-12). Date: 2026-10-11, ~22:15 JST. GPU: AMD RX 6400 (RADV NAVI24).
Leg: `t61-layers-world` — the **layer-enabled** world leg: GL-less, `scene=terrain`, 1280x720, presented dump,
fixture `fog-path-fixture7.jar` sha256 `a7504c4589f2e41d87e8b0cdf2855cefe72eac11cb709f0ce1e2ef76c8974fd8`,
mod jar `fog-path-mod13.jar` sha256 `0b8bd943b2332d2cfc3763eeaa58bbc2203eb9d7079a88e98baf072d42b3f334`
(md5 `e5aa2de4a6d3f1646a3f1125e967286b`, the jar the Lead named).

## 1. Acceptance numbers

| field | value |
|---|---|
| `validationMessageCount` | **3** |
| `worldTicksAtEnd` | 210 |
| device survived? | **no** — `gpuStalled=true`, `frameId=30` at the end |
| `presentedImageVerdict` | `FAILED_PRESENTED_IMAGE` ("no picture was requested for stage=world") |
| `windowFlushFailures` | 1 at **frame 0**, `windowFlushDroppedBatches=1` |
| `lastWindowFlushFailure` | `IllegalStateException: vkQueueSubmit (glless beginFrame signal) failed with VkResult -4` |
| `stallOrigin` | `net.minecraft.client.Minecraft.handler$...$presentAtSwapPoint:3541 via settleGpu:10953` |

The three messages: `VUID-vkCmdDrawIndexed-None-08114` (twice), `VUID-vkQueueSubmit-pSignalSemaphores-00067`
(twice), and `vkQueueSubmit() failed (VK_ERROR_DEVICE_LOST)`.

## 2. Does the zero-view guard fire? **No.**

`CELTIUM_ZERO_VIEW_REFUSAL` appears **0 times** in the leg, while `VUID-vkCmdDrawIndexed-None-08114` fires
twice: `vkCmdDrawIndexed(): the combined image sampler descriptor [VkDescriptorSet 0x940000000094, Set 0,
Binding 0, Index 0, ...]` has a `VkImageView` of 0. So the descriptor with the null view is written by a path
the guard does not cover — the guard is on the descriptor-write helpers, and this set is not written through
them. **The guard is therefore unverified, and the zero view is not shown to be the trigger** (it may still be
a defect in its own right).

## 3. The trigger: an abandoned frame leaves `waitSemaphore` signalled

`VUID-vkQueueSubmit-pSignalSemaphores-00067` names it: `pSubmits[0].pSignalSemaphores[0] (VkSemaphore
0xb100000000b1) is being signaled by VkQueue 0x7f9a186b4570, but it was previously signaled by VkQueue
0x7f9a186b4570 and has not since been waited on` — **the same queue signals the same binary semaphore twice
with no wait between**.

The pairing in the glless target, read from the code:

- `GllessFrameTarget.beginFrame()` (`GllessFrameTarget.java:333`) calls `signalWait()` (`:302-311`), which is a
  **signal-only** submit: `vkQueueSubmit` with `pSignalSemaphores = [waitSemaphore]` and **no wait**;
- that signal is consumed by the frame's first real submission, which waits on `waitSemaphore`
  (`:324-325`, `waitSemaphoreCount`/`pWaitSemaphores`);
- `consumeDone()` (`:313-327`) consumes `doneSemaphore` only — nothing consumes `waitSemaphore` when a frame
  never reaches its first submission;
- `GllessFrameTarget.abandonFrame(String)` (`:365-371`) clears `frameOpen` and counts, and **does not consume
  the signal `beginFrame` already issued**;
- so the next `beginFrame()` calls `signalWait()` again → an already-signalled binary semaphore is signalled →
  `VUID-vkQueueSubmit-pSignalSemaphores-00067` → RADV: `vkQueueSubmit() failed (VK_ERROR_DEVICE_LOST)` → every
  later submit fails and `settleGpu` latches the renderer (`stallOrigin=... via settleGpu:10953`).

This leg abandoned a frame at **frame 0** (`windowFlushFailures=1`, `windowFlushFailureFrame=0`), which is the
abandoned-frame case the pairing does not handle. The first submit of frame 0 then already reports
`VkResult -4`, i.e. the device is gone by the time the frame's signal is attempted.

## 4. What the fix is

1. `abandonFrame` must leave the semaphore pairing in a legal state: if `waitSemaphore` was signalled by this
   frame and no submission has waited on it, consume it (a wait-only submit, as `consumeDone` does for
   `doneSemaphore`) or recreate the semaphore, and count which happened. Signalling it again next frame is
   what the spec forbids.
2. The zero-view guard needs to cover the descriptor-set path that actually writes the null view
   (`VkDescriptorSet 0x940000000094`, set 0, binding 0), or the `08114` message will keep appearing with the
   guard claiming to be in place.

## 5. Method notes

- My bounded window-flush catch and `stallOrigin` are what made this readable: the flush failure at frame 0
  carries the exact `VkResult -4` and the submit's name (`glless beginFrame signal`), which is what pointed at
  `signalWait()` rather than at the draw that reported the zero view.
- The leg had to be taken with layers on; every earlier leg was layer-free for frame budget, which is why the
  `00067` pairing fault had never been seen.
