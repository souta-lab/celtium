# Shared matrix scratch: the partial-write leak and its siblings

Read-only audit for **task-72** (no Java edited, no leg run). Every claim carries `file:line` in this
tree. The rule being audited: *a GL projection or model-view builder that does not write all sixteen
elements inherits whatever the previous user of the scratch left*, and `[3] [7] [11] [12] [13] [15]`
are the elements with meaning in `w` and in depth — `[12]/[13]` are the x/y terms of `w` in a GL
projection, which is exactly what put the main-menu panorama outside the frustum.

**Verdict in one line: after the Lead's four `Arrays.fill` additions the tree has no remaining
partial-write site; the shape is nevertheless still *latent*, because the invariant lives in four
separate builders rather than in one place, and a fifth builder added to the same scratch would
reintroduce it verbatim.**

## 1. `VulkanTerrainGpuBackend.matrixScratch` (`VulkanTerrainGpuBackend.java:2470`)

Declared as one field, reused by exactly four builders (`grep matrixScratch` returns only these four
plus the declaration):

| builder | line | elements it writes itself | fill before writing | verdict |
|---|---|---|---|---|
| `glOrtho` | `:3750-3762` | `[0] [5] [10] [12] [13] [14] [15]` | `java.util.Arrays.fill(matrix, 0.0F)` at `:3753` | **safe** |
| `perspective` | `:3819-3830` | `[0] [5] [10] [11] [14]` | `Arrays.fill(frustum, 0.0F)` at `:3821` | **safe** |
| `frustum` (six-plane `glFrustum`) | `:3965-3975` | `[0] [5] [8] [9] [10] [11] [14]` | `Arrays.fill(matrix, 0.0F)` at `:3967` | **safe** |
| `applyDerivedProjection` (the `gluPerspective` path) | `:4753-4764` | `[0] [5] [10] [11] [14]` | `Arrays.fill(frustum, 0.0F)` at `:4756` | **safe (this is the fixed one)** |

What the leak was, named precisely: `glOrtho` (`:3750`) leaves `[12] [13] [15]` at
`0, 0, 1`… no — it *writes* them (`[12]=-(r+l)/(r-l)`, `[13]=-(t+b)/(t-b)`, `[14]=-2/(f-n)`,
`[15]=1`, `:3754-3762`). The GUI's ortho therefore leaves **non-zero `[12] [13]` and `[15] = 1`** in
the scratch, and `perspective`/`applyDerivedProjection` after it wrote only `[0] [5] [10] [11] [14]`
— so the projection became `w = m[12]·x + m[13]·y + m[3]·z + m[15]`, i.e. the GUI's ortho x/y terms
plus `1`, instead of `w = −z`. The fix (zeroing first) restores the intended `[3]=[7]=[12]=[13]=[15]=0`
for those builders, which is what a GL projection requires.

**Residual (latent, not present):** nothing in the field's contract forces the fill — the four
builders each remember it. A fifth builder, or a builder that gains a new early-return path *after*
the fill, has the identical shape available. Recommendation: one helper (e.g.
`private float[] zeroedMatrixScratch()` that fills and returns `matrixScratch`) so the invariant is
enforced by construction; a `-Dceltium.vulkan.matrixScratchAssert` mode could also check at
`loadMatrix` time that the builder marked its elements, but the helper is the cheaper half.

## 2. `VulkanGlState.matrixScratch` (`VulkanGlState.java:211`) and `multiplyResult` (`:212`)

Exactly two users of the state's scratch, and **both write all sixteen** before the array is read:

| path | line | write | verdict |
|---|---|---|---|
| `loadMatrixBuffer` (`glLoadMatrixf`) | `:219-226` | `for (i = 0; i < MATRIX_FLOATS; i++) matrix[i] = buffer.get(position + i);` guarded by `buffer.remaining() < MATRIX_FLOATS` at `:220-222` | **safe** |
| `multiplyMatrixBuffer` (`glMultMatrixf`) | `:232-239` | the same full loop, same guard at `:233-235` | **safe** |

`multiplyResult` is written **entirely** by `multiply(left, right, result)` (`:805-815`: the loop
assigns `result[column*4+row]` for every one of the sixteen) and copied straight out at `:254`. No
path reads it partially, and no other code holds a reference to it (`:207-210` documents the "no
caller may hold the reference" rule, and the two `arraycopy`/loop users obey it).

**The state class's other builders cannot leak either**, because they do not use a scratch at all:
`translate` (`:156-163`), `scale` (`:165-172`) and `rotate` (`:178-202`) each begin with
`float[] matrix = identity();`, and `identity()` (`:789-796`) and `copy()` (`:798-802`) both allocate
a **fresh** array. That is the second half of this family — a shared *scratch* is only dangerous when
the builder writes into it — and it is why the panorama's six `rotate` calls composed correctly
(task-71).

## 3. Every other sixteen-float holder in the package

| holder | declared | how it is written | verdict |
|---|---|---|---|
| `worldSnapshotProjection` / `worldSnapshotModelView` | `VulkanTerrainGpuBackend.java:1000-1001` | `System.arraycopy(..., 16)` when the snapshot is taken; read only under `worldSnapshotValid` | **safe** |
| `currentGroupProjection` / `currentGroupModelView` | `:1825-1826` | `System.arraycopy(this.glProjection/glModelView, 0, …, 0, 16)` at `:5539` / `:5545`; the OPT-18b identity branch does `Arrays.fill(this.currentGroupModelView, 0.0F)` at `:5556` before writing `[0] [5] [10] [15]` (`:5557-5560`) | **safe** |
| `glProjection` / `glModelView` | `:2471-2472` | 16-element `arraycopy` from the mirror at `:12678-12679` (world snapshot path) and `:12688` (the menu's path) | **safe** |
| `FontBatch.pendingProjection` / `pendingModelView` | `FontBatch.java:265-266` | `System.arraycopy(view.projection(), 0, …, 0, 16)` / same for the model-view at `:389-390` | **safe** |
| `p` / `m` locals in `captureGlMatrices` | `VulkanTerrainGpuBackend.java:4047-4048` | fresh arrays, filled by `FloatBuffer.get(float[])` (sixteen elements) | **safe** |
| `pushCombinedMatrixInto` push block | `:12791-12797` | the triple loop writes `push.putFloat(base + column*16 + row*4, sum)` for **every** row/column — all sixteen slots of the block are assigned | **safe** |
| `vulkanProjection` (`Matrix4f`) | `:265` | JOML `set(...)`/`mul(...)` write all elements; read via `get(...)` | **safe** |
| `new Matrix4f().set(modelViewNow)` / `compileView` / `relative` | `:10089-10091` | fresh instances, full `set`, `mul` into a fresh result | **safe** |
| `VulkanPostChain` | whole file | builds **member layouts** (offset/stride) for push/UBO, never a 16-float matrix itself: the matrix handling is `matrixStride`/`sizeOf` reflection at `:2094-2119`, and the pass matrices arrive from the collector as `float[]` | **safe (no scratch of this shape)** |
| `mirrorGluPerspective` (`:3859`) | — | resets the mirrored projection to identity (a full sixteen-element write via `loadIdentity`) and multiplies the frustum in through the state, so it does not touch the shared scratch; its early-return paths (`:3860-3866`) touch no GL state | **safe** |

## 4. Sites that are *still* unsafe, and what they could inherit

None in the current tree. The two residual risks are structural rather than present:

1. **`VulkanTerrainGpuBackend.matrixScratch` has no enforced contract.** Four builders each call
   `Arrays.fill` privately (`:3753`, `:3821`, `:3967`, `:4756`). The stale elements that can survive
   if a future builder omits it: `[12] [13] [15]` from `glOrtho`, and `[3] [7]` from anything that
   wrote a model-view-like matrix; for a projection those are the `w` terms, for a depth term `[11]`
   and `[14]` matter. Recommend the zeroing helper (§1).
2. **`VulkanGlState.matrixScratch` is filled per call, not per contract.** Both current users write
   all sixteen, but a third user added later (e.g. a `glLoadTransposeMatrixf`) would inherit the
   previous matrix's `[3] [7] [11] [12] [13] [15]` — the same six elements — unless it fills first.
   Same recommendation: one accessor that returns a zeroed scratch, or an assertion mode.

## 5. Why this audit says the fix is complete but the shape is not retired

The Lead's four fills close the *measured* defect (the panorama's projection at `:4753`, which is the
one the main menu reaches after the GUI's ortho) and the other three builders are now consistent with
it. What the audit did **not** find is a single remaining site that writes a shared scratch partially
and then reads it as a matrix — so the blast radius is empty today. What it did find is that the
protection is four copies of one line rather than one guarantee, which is exactly the condition that
produced the original defect and would produce it again. That is the finding to hand to whoever owns
`VulkanTerrainGpuBackend` next (blend-state currently holds it, so this is a note, not a patch).
