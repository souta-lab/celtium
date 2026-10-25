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

---

# task-70 — does the menu frame's depth clear reach the frame image?

Read-only audit; no Java edited, no leg run. Question: is the frame image's depth cleared once per game frame in the
menu, and which clear does it?

## (1) The clear at `EntityRenderer.java:1173` takes the **pair** route, not the frame latch

- Vanilla ends frame N with `framebuffer.unbindFramebuffer()` and `framebuffer.framebufferRender(...)`
  (`Minecraft.java:1234-1237`), which leaves the **window** bound - identity 0 in the renderer's own numbering.
- The next frame's `EntityRenderer.updateCameraAndRender` issues `GlStateManager.clear(256)` (`:1171-1173`) **before**
  `currentScreen.drawScreen`, i.e. while the window is still the bound framebuffer. Nothing re-binds the game's main
  framebuffer between the two, so the clear arrives with identity 0 bound and `currentIsMain()` **false**.
- `celtium$latchGameClearAtTargetChange` (`VulkanTerrainGpuBackend.java:9347-9377`) therefore takes the branch at
  `:9368-9372`: `if (!this.targetRegistry.currentIsMain()) { gameClearsRoutedToPairs++; reportGameClearLatch("pair",
  …); return; }`. The `noteGameClear(...)` / `noteGameClearsLatchedForFrame()` path at `:9373-9375` - the only one that
  latches a clear for the **frame image** - is **not** reached.
- This is consistent with the measured latch lines: all six bounded `CELTIUM_CLEAR_LATCH` entries are `route=pair`.
  `gameClearAttribution=[latchedForFrame=119, routedToPairs=125]` therefore cannot be explained by this clear.

## (2) What else clears the frame image's depth, and where the 119 latched clears come from

- The frame image's clears come only from `GllessFrameTarget.applyGameClears` (`:761-850`): either the **latched**
  clear (`gameClearLatched`, `:772-781`) or the first-use clear (`neverCleared`, `:762`). A clear is latched only when
  `currentIsMain()` was **true** at the bind change that ended it (`:9373`), i.e. a clear issued while the game's own
  main framebuffer was bound.
- The game issues exactly such clears itself: `RenderGlobal`'s `framebufferClear()` (the outline silhouette,
  `RenderGlobal.java:679`) and the loading-screen/main-framebuffer path. Those are the clears `latchedForFrame=119`
  counts; the window clear at `EntityRenderer.java:1173` is not one of them.
- A clear whose owner is a non-main framebuffer and which is not latched is **dropped** by
  `applyGameClears` (`:781-793`, `gameClearsDroppedForeignTarget++`) rather than applied, because every non-main
  identity shares the chain pair and applying it would wipe another identity's content. Measured
  `gameClearsDroppedForeignTarget=0`, so in that leg nothing was dropped - the window clear was counted as
  `routedToPairs` before this point.
- `offTargetPairClears=[default-pair=0, chain-pair=16]` is the confirming counter: the default pair - the image
  identity 0's draws go to - was **never cleared** in the menu phase, while the chain pair was. So the window's depth
  clear neither reached the frame image nor produced a recorded clear on the default pair.

## (3) Verdict

**The frame image's depth is not 1.0 at the start of `drawPanorama` each frame.** Nothing in the menu phase clears the
frame image's depth per frame: the only per-frame clear vanilla issues is the window clear at
`EntityRenderer.java:1173`, and that one takes the pair route (`:9368-9372`). The frame image therefore keeps the
previous frame's depth - the blur passes' NDC z=0.5 inside the 256x256 box (window depth ~0.75) - except in frames
where a *main*-bound clear was latched, which the menu does not produce.

- Code-side conclusion: the depth the composite quad tests against in the box is the **previous frame's blur depth**,
  not a fresh 1.0. That is the condition the Lead's `TextureCopyService.readDepthRectangle` +
  `CELTIUM_DEPTH_STAGE` leg is measuring; the two answers should agree, and if the leg reads 1.0 the code path above
  is wrong somewhere and the latch lines are where to look.
- Existing counters that separate the two explanations without a new leg: `gameClearAttribution` (`latchedForFrame` vs
  `routedToPairs`), `offTargetPairClears=[default-pair=n]`, `gameClearsDroppedForeignTarget`, and the bounded
  `CELTIUM_CLEAR_LATCH` routes. All four are already in the menu-phase report.
- The single new measurement that would settle it if the code were ambiguous is exactly the one the Lead added: a
  depth readback of the frame image's 256x256 box **before** `drawPanorama` (expect the previous frame's ~0.75) and
  after the clear (expect 1.0) - one value, two moments, no new instrument beyond `CELTIUM_DEPTH_STAGE`.
