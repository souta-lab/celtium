# The panorama cube's six faces: do the `k` rotations reach the model-view?

Read-only audit for **task-71** (no leg run, no Java edited). Every claim carries `file:line` in this
tree (HEAD `d19105200`). The expectation is the decompiled vanilla
`.celtium-work/mc-sources/net/minecraft/client/gui/GuiMainMenu.java`, `drawPanorama` lines 393-473.

## Verdict first

**The six faces are DISTINCT. The `k` rotations do reach the model-view.** The mirror's `rotate` is
GL-correct (degrees, normalised axis, post-multiply), the stacks are per matrix mode, and the one
code path that could have swallowed the rotation — the OPT-18b pre-transform, which bakes the
matrix into the vertices and records **identity** for the group — explicitly refuses a rotated
matrix. So the "one quadrant, one straight missing edge" shape is **not** a lost rotation: it is
something common to all six faces, and the candidate sites are listed in §5 for the Lead's dump to
discriminate.

## 1. Where the mirror applies `glRotatef`, and how it multiplies

| what | where |
|---|---|
| hook (`GlStateManager.rotate` → mirror) | `forge122/.../mixin/core/GlStateManagerMixin.java:351-352` — `celtium$mirror(backend -> backend.rotate(angle, x, y, z))` |
| backend entry | `celtium-vulkan/.../VulkanTerrainGpuBackend.java:3769-3772` — `rotate(...)` mirrors into the state and sets `matricesDirty = true` (`:3771`) |
| the maths | `celtium-vulkan/.../VulkanGlState.java:178-202` — `rotate(angleDegrees, x, y, z)` |
| the multiply | `VulkanGlState.java:805-815` — `multiply(left, right, result)` = `result = left · right`, called as `multiply(top(matrixMode), matrix, …)` at `VulkanGlState.java:246-253` |

Reading `rotate` line by line:

- **degrees**: `double radians = Math.toRadians(angleDegrees);` (`VulkanGlState.java:179`) — GL's
  `glRotatef` takes degrees, so this is the right unit and the right direction.
- **axis normalisation**: `length = sqrt(x²+y²+z²)`, `x/length`… (`:180-186`), with a zero-length
  axis returning without touching the matrix (`:181-183`) — GL leaves the matrix unchanged for a
  zero-length axis, so the early return matches.
- **matrix shape**: the column-major Rodrigues form `M[0]=nₓ²t+c`, `M[1]=n_ynₓt+n_zs`,
  `M[2]=n_znₓt−n_ys`, … (`:188-201`) — exactly what `glRotatef` builds.
- **pre/post**: `multiplyMatrix` does `multiply(top(matrixMode), matrix, …)` (`:253`), and
  `multiply` computes `result[col*4+row] = Σ_k left[k*4+row] * right[col*4+k]` (`:805-815`), i.e.
  **`M ← M · R`** — post-multiplication, which is GL's convention (the new transform is applied in
  the *local* frame). Pre-multiplying would have been the classic mistake here; it is not made.
- **matrix mode**: the transform lands on `top(this.matrixMode)` (`:253`), and `matrixMode(int)`
  simply stores the mode (`:298-300`) — so it goes to whichever stack vanilla selected. Vanilla
  switches to `GL_MODELVIEW` before the face loop (`GuiMainMenu` 393-473), so it lands on the
  **model-view** stack, which is the one the faces need.

## 2. `pushMatrix` / `popMatrix`, and whether a rotation can leak into the next face

Four stacks, one per mode, are fields: `modelViewStack` (`VulkanGlState.java:23`), `projectionStack`
(`:24`), `textureStack0`/`textureStack1` (`:30-31`), selected by `matrixMode` (`:33`, `:776-781`).

- `pushMatrix()` — `stack(this.matrixMode).push(copy(top(this.matrixMode)))` (`:141-143`): a **copy**
  of the current top, so the pushed copy cannot be mutated through the old reference.
- `popMatrix()` — pops, guarded by `stack.size() > 1` (`:145-150`), so an unbalanced pop at the base
  is ignored instead of emptying the stack.
- `loadIdentity()` writes the identity into `top(matrixMode)` in place (`:153-155`).

Consequence for the panorama: vanilla does `pushMatrix` → per-face `rotate` → `draw` → `popMatrix`
for each `k` (and an outer `pushMatrix`/`popMatrix` per jitter `j`). Each face's rotation is applied
to the copy made by *its own* `pushMatrix`, and the `popMatrix` discards it, so **a rotation cannot
leak into the next face** — and the six calls therefore produce six different model-views. The only
way they could coincide is if the mirror wrote to a different stack than the one the pop restores,
and it does not: push, rotate and pop all resolve through the same `stack(this.matrixMode)`.

## 3. Is the rotation applied to the matrix the group captures?

Yes — **the same array, on the same stack**, with one branch that deliberately opts out and is
inapplicable here.

- The capture copies the mirror's model-view into `glModelView`:
  `VulkanTerrainGpuBackend.java:12676-12679` (world snapshot path) and `:12686-12688` (the
  non-world path that the menu takes — both are `immediateState.matrix(MATRIX_MODE_MODELVIEW)` into
  `glModelView`). The capture runs when `matricesDirty` is set, at `:5234-5238`.
- The group's own copy: `System.arraycopy(this.glModelView, 0, this.currentGroupModelView, 0, 16)`
  at `:5545`, and the group key compares it (`!Arrays.equals(this.currentGroupModelView, this.glModelView)`
  → new group), which is what makes **one group per face orientation**.
- The opt-out: `OPT-18b` pre-transformation. `celtium$preTransformable(formatId, state)` (`:6126-6144`)
  accepts the GUI layouts `1, 2, 5, 6, 7`, rejects lit and fogged batches, and then requires
  `celtium$axisAlignedModelView()` (`:6109-6115`). That test demands **every off-diagonal term of
  `glModelView` to be exactly zero** (`m[1] m[2] m[3] m[4] m[6] m[7] m[8] m[9] m[11]` and
  `m[15] == 1`), i.e. a pure translate/scale/flip. The panorama's matrices are rotations — the
  outer `rotate(180,1,0,0)`/`rotate(90,0,0,1)` and each face's `rotate(±90, 0,1,0)` /
  `rotate(±90, 1,0,0)` all put non-zero terms there — so the test is **false**, and when it is false
  the group records the live matrix (`:5545`) instead of the identity (`:5538-5544`).

That last point is the answer to the question this audit was asked: the path that could have
discarded a rotation (identity in the group, transform baked into vertices) is gated on exactly the
axis-aligned case, and the panorama is not it.

## 4. What the measurement is consistent with

The measured bounds differ per group — `(-0.63..-0.08, 0.25..1.72)`, `(-1.7..-0.08, 0.25..0.41)`,
`(-4.29..-1.7, 0.28..3.13)`, `(-4.29..-0.31, 0.85..3.13)`, `(-0.31..-0.08, 0.41..1.72)`,
`(-4.29..-0.43, 0.25..3.13)` — which is six *different* panels, i.e. the distinctness this audit
predicts. What is common to them (all left of centre, all above it, one straight edge missing) is
therefore a transform **shared by all six**, not a rotation that failed to arrive.

## 5. Candidate sites for the shared offset (for the Lead's dump to discriminate)

Listed in the order I would check them, each with the code path it would break:

1. **The projection captured for the panel.** The menu sets `matrixMode(GL_PROJECTION)`,
   `loadIdentity`, `Project.gluPerspective(120, 1, 0.05, 10)` and never restores a window mapping;
   the mirror's projection on the menu comes from `immediateState.matrix(MATRIX_MODE_PROJECTION)`
   (`VulkanTerrainGpuBackend.java:12687`), and `gluPerspective` reaches the mirror through the
   projection channel (`GluProjectionMixin` at `org.lwjgl.util.glu.Project.gluPerspective`). A wrong
   `ProjMat` for the panel shifts/clips *all six* identically.
2. **The outer model-view**, i.e. the two rotations before the `j` loop — a sign or order error
   there rotates the whole cube, again identically for all six faces.
3. **The batch viewport** for the 256x256 box: a group drawn with the window's viewport instead of
   the box's would land all six panels in one corner of the frame; that is
   `celtium$setGroupViewport` (`:10950+`) and the `BH-13` fix, which now issues the viewport for
   every group — worth confirming the *box* rectangle is the one captured, not the frame's.
4. **The texture-46..51 binding**: if a face's texture resolved to a neighbour's image the *shape*
   would be right but the pattern wrong; that is a different symptom from the one measured, so it is
   last.

## 6. Summary table (task-71 deliverables)

| question | answer | evidence |
|---|---|---|
| (1) where `glRotatef` is mirrored, how it multiplies | `GlStateManagerMixin:351` → `VulkanTerrainGpuBackend:3769` → `VulkanGlState.rotate:178-202`; degrees (`:179`), normalised axis (`:180-186`), post-multiply `M←M·R` (`:253`, `:805-815`), active mode stack (`:298`, `:776-781`) | cited lines |
| (1) push/pop fields | `modelViewStack:23`, `projectionStack:24`, `textureStack0/1:30-31`; `pushMatrix:141-143` copies the top, `popMatrix:145-150` pops with a size guard | cited lines |
| (2) can a rotate leak into the next face | **No** — push/rotate/pop all resolve through `stack(this.matrixMode)`, and the push stores a copy | `:141-150`, `:253` |
| (2) do the six calls give six matrices | **Yes** — six distinct model-views, hence six groups; the key that splits them is `!Arrays.equals(currentGroupModelView, glModelView)` | `:5545`, group-key comparison |
| (3) same matrix as the group captures | **Yes** — `glModelView` from the MODELVIEW stack (`:12676-12688`), copied into the group at `:5545`; the OPT-18b identity branch is gated off by `celtium$axisAlignedModelView` (`:6109-6115`) because rotations are not axis-aligned | cited lines |
| (4) verdict | **Distinct.** No call site drops the `k` rotations; the shared one-quadrant placement must come from the projection, the outer model-view, or the group viewport (§5) | this document |
