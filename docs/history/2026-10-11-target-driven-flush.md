# BH-14/15/16: the immediate window drains every pending target into its own pass

Owner: **fog-path**. Date: 2026-10-12, ~02:10 JST. GPU: AMD RX 6400 (RADV NAVI24).

## The change (`VulkanTerrainGpuBackend.flushImmediateWindow`)

`flushImmediateWindow()` now collects the distinct targets of the pending groups (the bound one first, then every
`group.target`), and for each one sets `currentRenderTarget`/`currentRenderTargetIsMain` to that target and calls
the per-target body, which is now `flushImmediateWindowForTarget()` (the previous method, unchanged). The fields
are restored in a `finally`, so the caller's view of the bound target is untouched.

- A target whose pass throws is counted (`unflushedTargetPassFailures`, `lastUnflushedTargetFailure`) and the
  drain continues with the next target, so one bad target cannot lose the others' content.
- Anything still queued after every target had its pass is **dropped and counted**
  (`unflushedTargetGroupsDropped`, with a bounded record line), never left queued forever and never silently
  re-routed into another target's pass (BH-15). All three counters reach the JSON report through
  `verificationSummary()`.

This is the drain only. **BH-14's per-identity owned target is not in this change**: the drain still routes
through the existing pair rule (`main -> frame image`, identity 0 -> default pair, every other non-main identity
-> chain pair), so two different non-main framebuffers still share the chain pair and its size. That is the next
unit, and the drain is what turns "never drawn" into "drawn, or dropped with a counted decision".

## Verification state

- `python3 scripts/cpu-contract/shader_push_constant_check.py` → `checked 33 shaders, 0 problem(s)`.
- The build did **not** complete in my session: `scripts/build.sh` fails in another worker's in-flight files
  (`VulkanFrameArena.java`, then `VulkanDynamicMesh.java` — the BH-28 ring work). No error was reported in my
  file. The menu leg that measures `offTargetPerTarget` before/after and
  `CELTIUM_TEX_COPY_SERVED … mean=` has therefore **not** been taken; the acceptance for this unit is open.
- Expected acceptance once a leg runs: the `offTargetPerTarget` row for the one-shot identity moves from
  `offered=677, atPass=32, drawnPasses=17` towards `offered ≈ drawn`, or the identity appears in
  `unflushedTargetGroupsDropped`; and the menu copy's mean moves off `255,255,255` if the panorama's pass now
  runs (or `unflushedTargetPassFailures` names why it does not).
