# Correction: `offTargetPerTarget` compared batches with groups, and the mechanism built on it was wrong

Owner: **fog-path**. Date: 2026-10-12, ~02:20 JST.

## What was wrong

The row I (and the Lead) read as "677 offered, 32 at pass, 17 drawing passes" was read as *groups* throughout -
"645 offered groups for that target never drew at all", and on that reading I built the whole attribution of the
panorama and the loading screen's white window ("the groups are accepted for that target but their pass does not
draw them"), and then a drain that retargeted the recording to fix it.

The two columns are **different units**:

- `offTargetBatchesByTarget` is incremented **once per accepted batch**
  (`VulkanTerrainGpuBackend.java:4350`, `computeIfAbsent(...).incrementAndGet()` in the offer branch);
- `offTargetPassGroupsByTarget` merges `countGroupsForTarget(...)`, which counts **groups**
  (`:6716`), and `offTargetDrawnByTarget` counts drawing **passes**.

So `41bf95c1: offered=677, atPass=32, drawnPasses=17` says: 677 batches were accepted for that identity, they
were present as 32 groups at a pass, and 17 passes drew. It does **not** say any group was lost. The counter has
been renamed to say so (`batchesOffered=`, `groupsAtPass=`, `drawnPasses=`), with the reason in the code.

## What this invalidates

- The claim that a one-shot identity's groups never reach a pass. They do: the identity is the bound target 32
  times and 17 of those passes draw. The panorama's white is therefore **not** "the pass never runs", and the
  loading screen's white window needs the same re-reading.
- The drain (`e87aa0ec1`) was built on that claim; it was already reverted for the device-lost it caused
  (`e30a23cd6`), and this correction removes its motivation as well. Its acceptance leg had shown no movement,
  which is consistent: there was nothing to drain.

## What stands

- The binding-driven filter in `flushImmediateWindow` is real code (`countGroupsForTarget(currentRenderTarget)`
  gates the flush) and a group accepted for a target that is not bound again would wait; but **no leg has shown
  such a group existing**, and the counters now read honestly: `flushEntries=[0:entries=114,groupsAtEntry=114]`,
  `unflushedTargetGroupsDropped=0`, `unflushedTargetDropTargets=[none]` on a terrain leg.
- BH-15's frame-boundary drop is kept: a group that is still queued at a frame boundary is dropped and counted
  per identity (`unflushedTargetDroppedByTarget`), so a future leg can see it if it ever happens. It reads zero,
  which is the honest state of the evidence.

## The lesson, for the record

This is the same failure mode `AGENTS.md` warns about from the other side: a counter was read as if it measured
what the argument needed, without checking its unit or its increment site. The row is now self-describing, and
the panorama/loading-screen white has to be re-attributed from a measurement whose units are known.
