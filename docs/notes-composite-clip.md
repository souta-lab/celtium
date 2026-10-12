# What can clip, depth-reject or drop the menu's full-screen composite quad

Task task-69. Read-only analysis; no Java was edited and no game leg was run (the Lead holds the game slot).
Subject: group 388 (`GuiMainMenu.renderSkybox`'s final composite quad, `fmt=2`, `target=970565335`, full-screen NDC,
`proj=[m00=0.004 m11=-0.007]`, `state=[blend=1 src=6 dst=7 mask=0xf alphaFunc=516 alphaRef=0.1 depthTest=1
depthWrite=1 depthFunc=515 cull=1]`), against the measured symptom: the presented menu keeps the frame image's
cleared background except for a tilted blue parallelogram at the bottom-left.

All line numbers are `celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java` unless the
file is named.

## (a) Clip - **possible, and the prime candidate**

- The rectangle a group is drawn in is decided per group by `celtium$setGroupViewport` (`:12421`), from
  `group.viewportValues` - the viewport captured when the group was **opened**, not when it is drawn.
- That method intersects the group's rectangle with the target: `x0 = max(0, viewport[0])`, `yBottom0 = max(0,
  viewport[1])`, `x1 = min(targetWidth, viewport[0]+viewport[2])`, `yBottom1 = min(targetHeight, viewport[1]+
  viewport[3])` (`:12427-12431`). So a group whose captured rectangle is 256x256 is **clipped to 256x256** no matter
  how wide its NDC is - the NDC and the viewport are independent inputs.
- The panorama/blur passes run inside a 256x256 viewport (`CELTIUM_GROUP_VIEWPORT gl=0,0 256x256 clipped=0,464
  256x256 target=1280x720`), and `GuiMainMenu.renderSkybox` issues the composite immediately after those passes. A
  captured 256x256 rectangle on group 388 therefore clips the full-screen quad to the bottom-left 256x256 - which is
  the shape of the measured parallelogram.
- The stale-scissor *bug* proper is ruled out: the code sets viewport **and** scissor for every group and documents
  the earlier early-return (BH-13) that left a narrow rectangle in place for later groups (`:12433-12437`), and a
  narrowed group sets `groupViewportNarrowed` (`:12545`) so the pass's own rectangle is restored once per flush.
  What remains possible is the *correct* clipping of a group whose captured rectangle is the wrong one.
- **Separating instrument (exists, no new leg)**: `CELTIUM_GROUP_VIEWPORT` prints the group's rectangle with
  `clipped=` and `target=` for every narrowed group. If group 388 appears there with `clipped=0,<h> 256x256`, (a) is
  proven; if it never appears, its rectangle is the full target and (a) is ruled out for it.

## (b) Depth-reject - **possible, secondary**

- The chain-pair pass attaches the pair's depth image: `ensureChainPairFramebuffer` builds it from
  `glless.chainPairDepthView()` (`:9274`).
- The pair's depth is cleared by the renderer, not by the game: `clearChainPair` (`GllessFrameTarget.java:1026`) ->
  `Pair.clear` (`:1233`), which records `vkCmdClearDepthStencilImage` with `OFF_TARGET_CLEAR_DEPTH` (1.0). The
  game's own `glClear(GL_DEPTH_BUFFER_BIT)` is latched and applied to the **main** image (`applyGameClears`), so it
  does **not** reach the pair.
- With depth cleared to 1.0 and `depthFunc=515` (LEQUAL) a quad passes unless its depth is greater than the stored
  value, so the clear alone does not reject it. Two ways it can still be rejected: the pair was **not cleared this
  frame** (the call site guards on `chainPairClearedFrame != frameId`), or the same identity's earlier draws in the
  same frame left nearer depth in the pair (the outline silhouette path draws into it).
- **Separating instrument (exists)**: `offTargetPairClears=[chain-pair=n]` says whether the pair cleared once per
  frame, and `CELTIUM_GROUP_BOUNDS`/the depth state on the line name the test. A composite quad that is absent from
  the picture while `chain-pair` clears once per frame and no nearer geometry was drawn is not a depth rejection.

## (c) Drop at the per-target window boundary - **possible**

- `flushImmediateWindow` draws only the groups of the **current** target and returns when that target has none
  (`countGroupsForTarget` at `:7414`, used by the window's own guard), so a group owned by another identity is kept in
  the queue rather than drawn (`immediateGroupsKeptForOtherTargets`).
- A kept group is only drawn when a later flush happens while its identity is the current target; at the frame
  boundary whatever is still held for another target is discarded and counted as `otherTargetGroupsDiscarded`
  (`:2437`, incremented at `:8833`).
- Group 388's target is the non-zero off-target identity, so it depends on a flush for that identity *after* the
  composite was accepted and *before* the frame ends. A target switch to the main framebuffer (which the game does
  right after `renderSkybox`) is exactly the boundary where it can be kept and then dropped.
- **Separating instrument (exists)**: `otherTargetGroupsDiscarded` and `immediateGroupsKeptForOtherTargets` are in
  the report. A non-zero `otherTargetGroupsDiscarded` on the menu leg proves (c) for some group; the per-group
  identity is what the group-bounds dump gives.

## Verdict

| mechanism | verdict | why |
|---|---|---|
| (a) clip by a stale/captured rectangle | **possible - prime** | the rectangle comes from the group's captured `viewportValues` and is intersected with the target (`:12421-12431`); a 256x256 capture clips a full-screen NDC exactly as observed. The BH-13 stale-scissor *bug* is fixed (`:12433-12445`, `:12545`), so the surviving cause is the captured rectangle, not a leak between groups. |
| (b) depth-reject | **possible, secondary** | the pair's depth is cleared to 1.0 by the renderer (`GllessFrameTarget.java:1233-1275`) and the game's depth clear never reaches it; rejection needs a missed clear or nearer geometry in the same frame. |
| (c) drop at the window boundary | **possible** | a non-current target's groups are kept and discarded at the frame end (`:7414`, `:2437`, `:8833`). |

No leg was run; the three instruments above (`CELTIUM_GROUP_VIEWPORT` with `clipped=`, `offTargetPairClears`, and
`otherTargetGroupsDiscarded`/`immediateGroupsKeptForOtherTargets`) separate (a) from (b) from (c) on one menu leg,
without adding an instrument.
