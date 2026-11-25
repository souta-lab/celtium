# Synchronization rules

Which synchronization objects exist, who may signal and wait on them,
and the rules every pass must follow. The full audit behind this page
is item 427 (2026-11-19); the renderer closure report's D3 row is
closed on the basis of that audit. The inventory below was rewritten
for the new architecture's HAL (2026-11-25): completion tracking moved
to one timeline semaphore, and the per-submission fences and the frame
semaphore pair were deleted.

## Inventory (the only creation sites)

| System | Objects | Owner |
|---|---|---|
| Frame timeline | one timeline semaphore; values allocated monotonically, signalled by the frame submits (`submitWithSemaphores`, bootstrap) and by the batched deferred submit's last chunk | `VulkanContext` (`hal.FrameTimeline`) |
| Swapchain | acquire semaphore pool (one owner image tracked per semaphore), one `renderFinished` semaphore per image | `VulkanSwapchain` |
| Slots | one persistent fence per command slot (the deferred path substitutes the batch's timeline value for a slot whose submission went batched) | `VulkanTerrainGpuBackend` (policy: `FrameSubmissionScheduler`) |
| Frame target | none: the wait/done pair and its dummy submissions are deleted; `needsFrameSemaphores()` is false and both accessors return `VK_NULL_HANDLE` | `GllessFrameTarget` |

No other code creates semaphores or fences. `VulkanContext` creates no
fences at all: the synchronous submit paths wait on timeline values,
the deferred path completes on the batch's value, and the present is
synchronous (`hal.PresentEngine` submits and presents on the calling
thread and creates no synchronization objects of its own).

## Rules

- R1: Do not create synchronization objects outside the inventory
  above. Decide which system a new object belongs to first.
- R2: Signal a binary semaphore only when its previous signal is
  known to be consumed by a wait (double signalling is
  VUID-vkQueueSubmit-pSignalSemaphores-00067). The frame target
  carries no semaphores, so no frame-level drain of an outstanding
  signal exists or is needed.
- R3: Never reuse a consumed pair in a later pass. Cross-pass
  synchronization uses the object dedicated to that image or slot
  (`renderFinished`, slot fences, timeline values).
- R4: Pooled objects track their owner; an owned object is never
  reused, the pool grows instead. A replaced object is destroyed
  deferred, never immediately.
- R5: Submission completion is a timeline value, never a per-submission
  fence. Values are allocated in submission order (every synchronous
  submit flushes the pending batch before allocating its own), waits
  are always bounded by a timeout, and a value is never reset - the
  counter only moves forward. A submission whose wait does not
  confirm completion quarantines its command buffer; there is no
  fence to quarantine with it.
- R6: No thread submits asynchronously. The present runs on the render
  thread at the frame's end; its verdict is final when the call
  returns and is consumed on read, so a later frame can never
  re-apply an earlier present's result.
- R7: Whole-device synchronization (`vkDeviceWaitIdle`) belongs only
  at named boundaries (for example `settleGpu`), documented as
  unbounded. It is not a substitute for per-pass synchronization.
