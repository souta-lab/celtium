# The "GPU stalled" latch is a false stall: it fires at frame 0 from the font path and never clears

Owner: **fog-path** (task-60, first measurement). Date: 2026-10-11, ~21:20 JST. GPU: AMD RX 6400 (RADV NAVI24).
Leg: `r11c-night-fogon` (GL-less, `fog-path-mod9.jar` sha256
`9ec9b477b605dc9a91718da4c9b03dbc1afc413e32391892623f2e79bfbfe8b9`, fixture `fog-path-fixture5.jar` sha256
`b5a7c9190f7c4947bf7ae925cf0ef5c0e5eb20c4d13e131b3e5a6405b368d872`, scene=terrain, 1280x720, no validation
layers). The leg was taken for the fog question; the stall fell out of it.

## 1. The latch fires, and the session draws nothing afterwards

`CELTIUM_DRAWARRAYS glDrawArraysCalls=723 drawn=0 vertices=0 empty=0 refused=723 ... refusedByReason=[
draw mode 6 is not handled (only 7 = GL_QUADS)=3;
stride 28 with client arrays [...] is not a layout this decode knows=36;
**the backend refused it: the GPU stalled; no further drawing until the renderer is rebuilt=678**;
the mirrored projection is the identity at draw time=6]`

and the composite is refused with the same reason (`CELTIUM_COMPOSITE_REFUSED reason=the GPU stalled ...`).
678 of 723 draws in the session are refused by the latch - the same shape as the user's session's ~50,200
refusals across formats 1/2/4/5/6/7.

## 2. The wait counters say no wait timed out

From the same leg's report (`rendererDebug`, `celtium/vulkan-check`, the fields added this round):

| counter | value |
|---|---|
| `bootstrapQueueWaits` | 390 |
| `bootstrapQueueWaitMillis` | 15 |
| `bootstrapQueueWaitWorstMillis` | **7** |
| `submitTimeouts` | **0** |
| `gpuStalled` | **true** |
| `deviceWaitToken` | 36 |
| `stalledGeometryDrops` | 1 |
| `stalledGeometryDroppedBatches` | 1 |

The unbounded bootstrap `vkQueueWaitIdle` path is therefore **not** the hazard it was suspected to be: 390
waits, 15 ms total, worst 7 ms. No submission fence timed out either. **The latch was set with no timeout at
all.**

## 3. What set it

`CELTIUM_STALL site=org.taumc.celeritas.impl.font.CeltiumFontGlyphCapture.submit:455 target=1280x720
frameId=0 frameTargetNull=false presentSkipReason=[presented]`

- frame **0**, during the loading screen, from the font glyph path;
- the only `noteStalled()` reachable from there is `VulkanTerrainGpuBackend.java:4564`: the
  `catch (RuntimeException | LinkageError)` around `flushImmediateWindow()` in the immediate-window append path
  (`VulkanTerrainGpuBackend.java:4560-4567`), which latches on an **exception** in the window flush and then
  abandons the frame;
- the latch is permanent: `submitImmediateBatch` refuses every later batch (`:3629`), `submitImmediateBatchTimed`
  refuses again (`:3665`), `flushImmediateWindow` drops accepted geometry (`:5084`), and nothing clears it except
  a renderer rebuild.

So the attribution is: **false stall, from an exception in the immediate-window flush at frame 0, latched
permanently.** The wait the task suspected (the per-upload `vkQueueWaitIdle`) is measured at a 7 ms worst case
in this leg and did not time out.

## 4. What the fix has to be

1. The exception that `flushImmediateWindow()` throws at frame 0 must be found and fixed (its text is recorded
   as `accepted geometry retained after window failure: <error>`; the diagnostics list did not reach the JSON
   report in this leg, which is the next instrument to fix).
2. The catch must not latch: a window-flush failure is bounded to the window/frame it belongs to - refuse the
   affected window's batches, count them, and let the next frame try again, rather than stopping the renderer
   for the session.
3. Every dropped batch must keep being counted (`stalledGeometryDrops`/`stalledGeometryDroppedBatches` already
   do; the refusals are already attributed by format).

## 5. Method notes

- The wait facts now reach the JSON report: `bootstrapQueueWaits`, `bootstrapQueueWaitMillis`,
  `bootstrapQueueWaitWorstMillis`, `submitTimeouts`, `gpuStalled`, `deviceWaitToken`,
  `stalledGeometryDrops`, `stalledGeometryDroppedBatches` in `VulkanTerrainGpuBackend.verificationSummary()`.
- The leg was taken at 1280x720 rather than the user's 854x480; the mechanism is resolution-independent, but
  the acceptance leg should use the user's configuration.
- The leg's own purpose (night fog) failed for an unrelated reason: the presented dump never completed
  (`presentedImageAssertions: no picture was requested for scene=terrain stage=world`), because the stall
  refused the frames the dump waits for. The stall and the missing picture are the same finding.
