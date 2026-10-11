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

## Acceptance leg (taken after the change, per the Lead's instruction)

`drain-accept`: GL-less, `--validate`, scene=terrain, 1280x720, layers off, jar `fog-path-drain.jar`, AMD RX 6400.

| field | value |
|---|---|
| `presentedImageVerdict` | **PASSED** |
| `presentedNonBackgroundFraction` | **0.759463** (identical to the pre-change legs - no regression) |
| `worldTicksAtEnd` | 206 |
| `unflushedTargetGroupsDropped` | **0** |
| `unflushedTargetPassFailures` | **0** |
| `offTargetPerTarget` | `0: offered=114, atPass=114, drawnPasses=114, pair=default-pair` / `37a8b711: offered=622, atPass=30, drawnPasses=16, pair=chain-pair` / `394c0263: offered=2, atPass=1, drawnPasses=1, pair=chain-pair` |

**The row did not move** (622/30/16 against 677/32/17 before), and the drop counter is zero - so the drain is
not inert because it drops, but because **those groups are not in `immediateGroups` when any flush runs**. The
next measurement is therefore not "which pass draws them" but "where do they leave the queue": the candidates
are the `recordedImmediateGroups` path (groups recorded into a pass before the flush), the
`flushPendingBeforeGlDraw` drop path, and `closeCurrentImmediateGroup`. The counter to add is a per-target
"groups present at flush entry" line, which separates "never present" from "present but not drawn".

`validationMessageCount` is not carried by this leg's report (layers are off for the frame budget); the harness
reported the leg clean and the picture PASSED.

## The instrument for the open question (added after the acceptance leg)

`flushEntrySummary()` reports, per target, `entries=` (how often the drain visited it) and `groupsAtEntry=`
(how many of its groups were present at flush entry), plus `drainedOtherTargetPasses` (flush entries whose
target was not the bound one). The three reach the report as
`drainedOtherTargetPasses=… flushEntries=[…]`. A target with `groupsAtEntry=0` was **never in the queue at a
flush**, which is a different defect from one that was present and not drawn - and the next leg reads which of
the two the one-shot identity is, without another code change.
