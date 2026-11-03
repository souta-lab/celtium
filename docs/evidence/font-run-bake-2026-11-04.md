# the font run's matrix is not what splits it: 8 of 94 runs coalesce, and the rest are ordering (2026-11-04)

The accept path is the renderer's largest single item (19.0% of the client thread, [profile](accept-profile-jfr-2026-11-03.md))
and the font path is the largest population inside it: on the shipped world profile **94 font runs a frame carrying
1.2 quads each** (`fontRunsStarted=221565` against `fontQuadsAppended=265707` over 2365 frames, leg r52f-sticky), and
every run is an offer through the accept path and a group of its own. The run key carries the modelView, and every
HUD string is drawn under its own `pushMatrix/translate/popMatrix`, so the hypothesis was that the per-string
translate is what splits them - and that baking the modelView into the glyph positions would coalesce them.

**It does not.** The bake works, is exact, and coalesces 8 of the 94 runs. The other 86 are flushed by an
*intervening submission* - the HUD interleaves text with other quads - which is an ordering rule the bake cannot
touch. The switch stays off by default and the mechanism is kept with the condition for re-opening it named.

## The change

`-Dceltium.vulkan.fontBake=true` (default **off**):

* `VulkanTerrainGpuBackend.celtium$fontAppend` copies the glyph segment into a scratch with the live modelView
  applied to its positions (`celtium$bakeFontSegment`), and records the identity as the run's modelView.
* `FontBatch.FontRunState.modelView()` answers the identity while the run is baked, so the per-string translate is
  not part of the run's identity.
* `celtium$fontMatrixBoundary` then drains the capture's buffer and does **not** submit the run
  (`FontBatch.celtium$fontMatrixBoundaryBaked`), because a modelView change cannot alter what the vertices encode.
* `celtium$submitFontRun` installs the run's own matrices and clears the mirror's "moved since the last capture"
  flag for the submission, restoring it afterwards. That is a correctness fix independent of the bake: a matrix
  operation between the append and the flush used to make `submitImmediateBatchBody` capture the live matrices over
  the run's, drawing the run under a transform it was never appended under.

The bake is legal exactly where `celtium$axisAlignedModelView` holds - the same condition
`VulkanDynamicMesh.copyVerticesTransformed` already bakes under - and fog is excluded because the shader computes it
from view-space distance. `immediate_tex_color.vert` ends with `gl_Position = u_ProjectionMatrix * vec4(a_Position,
1.0)`: the modelView reaches the GPU for this family only through the baked positions, which is why the bake is
exact rather than approximate. `FontBakeTest` pins the decision table (7 cases).

## The measurement

One paired set on AMD, same JAR, same scene, same flags, only the switch differing.

| | r53a-bakeoff | r53b-bakeon |
|---|---|---|
| `-Dceltium.vulkan.fontBake` | false | true |
| `frameIntervalWarmP50Ms` | **3.062** | **3.890** |
| `frameIntervalWarmP95Ms` | 6.046 | 7.589 |
| warm samples | 2931 | 2351 |
| `immediateOffered` a frame | 238.3 | **230.1** |
| `immediateGroupsDrawn` a frame | 136.5 | **133.5** |
| `immediateOnlyPasses` a frame | 23.4 | 23.4 |
| frame budget `frame` p50/p95/worst | 2.5 / 4.6 / 46.5 | 3.1 / 5.7 / 54.3 |
| frame budget `record` | 0.5 | 0.6 |
| frame budget `batch` | 0.4 | 0.4 |
| frame budget `other` (the game's own work) | 1.6 | 2.1 |

**The coalescing did not happen**: 8 offers and 3 groups a frame, against the 86 the hypothesis needed.

**The frame interval difference is not this change.** The whole of it is the frame budget's `other` bracket - the
game's own work, 1.6 -> 2.1 ms - while the renderer's own brackets are unchanged (`record` 0.5 -> 0.6, `batch` 0.4
-> 0.4, `present` 0.1 -> 0.1). The two legs ran sequentially rather than alternating, so drift is not separated from
the change by this pair; what the pair does establish is that the renderer's own brackets did not move, and that the
offer and group counts barely did.

Both legs: `verdict=PASSED`, `problems=[]`, `fixtureStatus=PASSED`, `presentedImageVerdict=PASSED`,
`sceneCalibrationKeyMatches=true`, `worldTicksAtEnd=619`. Renderer `10a357c3…`, fixture `0e90c988…` (unchanged).

## What this rules out, and what it names

* **Ruled out: the modelView is what splits the font run.** 86 of 94 runs are flushed by an intervening submission,
  not by a matrix boundary. Baking the transform removes the matrix term and buys 8 runs.
* **Named instead:** the font run's coalescing ceiling is the ordering rule in `submitGeometryBatch` - a held run
  goes out before any other batch, because a held quad must not land after content the game drew before it. Raising
  the ceiling needs a run that can be *reordered* past a draw it does not overlap (a scissor or target argument), not
  a cheaper key.
* **Not claimed:** no speed claim in either direction. The bake is off by default and the default path's only change
  is the `matricesDirty` restoration in `celtium$submitFontRun`, which is a no-op in practice (a run is only opened
  while the mirror is clean) and correct in the case it covers.
