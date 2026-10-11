# BH-27: the whole-queue idle is gone from the upload and present paths

Owner: **fog-path** (round-22 package). Date: 2026-10-11, ~23:57 JST. GPU: AMD RX 6400 (RADV NAVI24).

## The change

`celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java`

- **`beginCommands()`** used to call `vkQueueWaitIdle` before allocating a command buffer, because a previous
  submission might still be reading one. That is not needed: every buffer on this path is freed only after its
  own submission's fence has signalled (`endCommands`), and the pool is never reset. The queue-wide wait is
  gone.
- **`endCommands()`** used to submit and then call `vkQueueWaitIdle` a second time. It now creates a fence,
  submits with it, and waits on **that fence** with the existing 5 s bound (`SUBMIT_TIMEOUT_NANOS`). A
  timeout counts (`submitTimeouts`), marks the context unusable and quarantines the command buffer instead of
  freeing it; `VK_ERROR_DEVICE_LOST` is treated as completion-equivalent, as before.
- Accounting is separated so a report says which happened: `bootstrapQueueWaits`/`...Millis`/`...WorstMillis`
  are now zero on the normal path, and the new `bootstrapSubmitWaits`/`...Millis`/`...WorstMillis` carry the
  per-submission fence waits. All six reach the JSON report through
  `VulkanTerrainGpuBackend.verificationSummary()` (the `celtium/vulkan-check` line).

## Before / after, same scene, pose and tick (GL-less, `--validate`, scene=terrain, AMD RX 6400)

| counter | before (`r11c-night-fogon`, jar mod9) | after (`bh27b-verify`, jar `fog-path-bh27b.jar`) |
|---|---|---|
| `bootstrapQueueWaits` | 390 | **0** |
| `bootstrapQueueWaitMillis` | 15 | **0** |
| `bootstrapQueueWaitWorstMillis` | 7 | **0** |
| `bootstrapSubmitWaits` | - (did not exist) | 2994 |
| `bootstrapSubmitWaitMillis` | - | 465 |
| `bootstrapSubmitWaitWorstMillis` | - | **21** |
| `submitTimeouts` | 0 | 0 |
| `gpuStalled` | true (that leg's device-lost) | **false** |
| `presentedImageVerdict` | FAILED (that leg) | **PASSED** |
| `presentedNonBackgroundFraction` | - | 0.759463 |

The after-leg's non-background fraction is identical to the pre-fix terrain legs (0.759463), so the picture is
unchanged; what changed is that 2994 submissions now wait on their own fences (21 ms worst) instead of the
renderer draining the queue 390 times.

## What is not claimed

The per-frame *frame* path (`submitWithSemaphores`) already waited on its own fence and is untouched. The
upload path still serialises *per copy* on its own fence; batching several copies into one submission and a
staging ring is the rest of BH-27's fix direction and is not done here. No FPS claim is made: the counter
change is the measurement.
