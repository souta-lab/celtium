# Synchronization rules

Which synchronization objects exist, who may signal and wait on them,
and the rules every pass must follow. The full audit behind this page
is item 427 (2026-11-19); the renderer closure report's D3 row is
closed on the basis of that audit.

## Inventory (the only creation sites)

Semaphores are binary only; timeline semaphores are not used anywhere.

| System | Objects | Owner |
|---|---|---|
| Frame pair | `waitSemaphore` / `doneSemaphore` per frame target, plus one `presentDone` semaphore per swapchain image | `GllessFrameTarget` |
| Swapchain | acquire semaphore pool (one owner image tracked per semaphore), one `renderFinished` semaphore per image | `VulkanSwapchain` |
| Submission | one fence created per submission (`submitWithSemaphores`, bootstrap, deferred) | `VulkanContext` |
| Slots | one persistent fence per command slot | `VulkanTerrainGpuBackend` (policy: `FrameSubmissionScheduler`) |

No other code creates semaphores or fences. The async presenter
(`VulkanContext.celtium$presentAsync`) creates none of its own; it
consumes the swapchain's acquire/renderFinished pair it is handed.

## Rules

- R1: Do not create synchronization objects outside the inventory
  above. Decide which system a new object belongs to first.
- R2: Signal a binary semaphore only when its previous signal is
  known to be consumed by a wait. A frame that was abandoned drains
  its outstanding signal with a wait-only submission before the next
  `beginFrame` (double signalling is VUID-vkQueueSubmit-pSignalSemaphores-00067).
- R3: Never reuse a consumed pair in a later pass. Cross-pass
  synchronization uses the object dedicated to that image or slot
  (`presentDone`, `renderFinished`, slot fences).
- R4: Pooled objects track their owner; an owned object is never
  reused, the pool grows instead. A replaced object is destroyed
  deferred, never immediately.
- R5: Submission fences are created per submission and never reused.
  Waits are always bounded by a timeout, and a fence whose wait
  reported `VK_ERROR_DEVICE_LOST` is never reset.
- R6: A thread that submits asynchronously (the presenter) does not
  own the objects it submits. Its result is read only after joining
  it, and nothing touches its in-flight batch before the join.
- R7: Whole-device synchronization (`vkDeviceWaitIdle`) belongs only
  at named boundaries (for example `settleGpu`), documented as
  unbounded. It is not a substitute for per-pass synchronization.
