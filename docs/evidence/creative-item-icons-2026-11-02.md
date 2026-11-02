# creative inventory: the missing item icons were a cached empty item plan (2026-11-02)

round 1 gave the creative tab a scene and a reference pair and measured "19 of 24 uncovered grid cells differ". This
round names the cause and fixes it: **Celtium's own item mesh cache stored a plan with zero segments for a model that
answered no quads once, and every stack sharing that model instance then drew nothing for the rest of the resource
epoch.** AMD Radeon RX 6400 (RADV NAVI24)・`plain`・Supersymmetry・1280x720・`guiScale3.txt`・validation 既定ON.

## The measurement that separated the two candidate causes

| leg | renderer | what it added |
|---|---|---|
| `r22a-itemstate` | `2a2d71a7…` (round-1 stage) | `-Dceltium.vulkan.groupBounds=true -Dceltium.vulkan.groupBoundsFormat=4` - one creative frame's format-4 groups with their NDC bounds |
| `r22b-emptyupload` | `c0ee04f2…` | `CELTIUM_EMPTY_UPLOAD` + the per-slot grid snapshot (`creativeGridAt<t>S*`) |
| `r22c-itemmeshfix` | `a3ddd5f5…` | the fix below |
| `r22d-nomesh` | `a3ddd5f5…` | same jar, `-Dceltium.itemMeshCache=false` (control) |

fixture `5503419d…` on the last three; `-Dceltium.validation.screen=creative -Dceltium.validation.creativeTab=12
-Dceltium.validation.screenshotAtTick=60` on all four.

1. **The container is not the problem.** `creativeGridAt180/240/300Filled = 46`, and the per-slot list is stable:
   `0..7` = the tools (`hammer recipeadvanced saw container wrench screwdriver chisel colortube`), `8..13` =
   `littletiles:multitiles`, `14` = `grabber`, `15..44` = `littletiles:premade`, `45` = `betterquesting:quest_book`.
2. **Only eleven item groups were ever submitted inside the panel.** The format-4 group census of one creative frame
   (`r22a`, 75 groups) holds the row-0 cells at x 377/428/480/537/588/646/697/755, the `r1c5` cell, the hotbar's slot
   45, the six tab icons and the JEI panel — and **nothing at all in rows 1-4**. The missing items are exactly the
   stacks whose item is one of the two `ICreativeRendered` classes (`multitiles`, `premade`).
3. **They are not refused, and not empty batches either.** `CELTIUM_REFUSALS_BY_FORMAT {}` at every print,
   `prepareDescriptorFailures=0`, `modelDrawRefusals=0`, `immediateRefusal=none`, and the new
   `CELTIUM_EMPTY_UPLOAD` census reports only `fmt=3` empties (1, 1, 10) — **no format-4 batch at all**, not even a
   zero-vertex one. The loss is upstream of every immediate batch.

## The cause

`forge122`'s `org.taumc.celeritas.mixin.core.RenderItemMixin` (Celtium's item mesh cache, `celtium.itemMeshCache`,
default on, ported from Angelica) replays an item's finished `begin/addVertexData/draw` sequence from a plan keyed on
**`(IBakedModel instance, colour)`**. Two facts made that key wrong for these items:

- `celtium$buildPlan` returned `new Plan(new Segment[0], …)` when the model answered no quads on any face, and the
  caller stored it. **One empty answer became "this model draws nothing" for every later draw of that model.**
- All 36 `multitiles`/`premade` stacks share one CreativeCore `CreativeBakedModel`, whose quads are a function of the
  stack it last saw (`CreativeBakedModel.getBlockQuads` reads the static `lastItemStack`; LittleTiles caches per
  `(stack, facing)` in `ItemModelCache.requestCache`). A stack with no preview answers nothing, and the tool stacks
  beside them use their own models — which is why exactly those 36 cells were empty and the other 11 were not.
- The hit path could not notice: its mutation probe re-read only the **null-face** quad list
  (`getQuads(null, null, 0)`), and `CreativeBakedModel.getBlockQuads` returns an empty list for every null-face query
  by construction, so the probe matched on every stack.

## The change

1. An empty answer is no longer cached: `celtium$buildPlan` reports it (`CELTIUM_ITEM_MESH_EMPTY`) and returns `null`,
   the caller leaves the draw to the original code, and the key is **not** demoted — the emptiness is a property of
   the stack being drawn, not of the model, so the next draw has to ask again. The fixed build names the model that
   answers empty:

   ```
   CELTIUM_ITEM_MESH_EMPTY colour=0xffffffff
     model=com.creativemd.creativecore.client.rendering.model.CreativeBakedModel@41798938
     (no quads on any face: not cached, the next draw asks again)
   ```

   which is the one instance all 36 `multitiles`/`premade` stacks share — the causal chain, measured rather than
   inferred.
2. The hit-path probe also re-reads the `UP` face's list (size + first quad). For an ordinary baked model that is the
   same list instance on every call, so nothing changes; for a stack-dependent model it is what makes the next stack
   rebuild instead of replay another stack's bytes.

## What the fix does to the picture (`r22c`, same scene and camera as the reference)

| region | round 1 | this round |
|---|---|---|
| creative panel (360,200)-(960,560), mean &#124;A−B&#124; vs Celeritas | 4.62 | **1.29** |
| the same region, pixels over 8/255 | 7.04 % | **2.86 %** |
| grid cells whose ink equals the reference's | 5 of 45 (rows 1-4 empty) | **44 of 45** |

The per-cell ink is now identical to the reference for every cell except `r2c4` (0.35 against 0.65) - and the
same-jar control has exactly the same one cell, so `r2c4` is a second, independent defect, not a residue of the cache. The tooltip's own
item icon, empty in round 1's picture, now carries the item's model. Images:
[fixed](creative-inventory-fixed-2026-11-02.png) against the round-1 [reference](creative-inventory-celeritas-2026-11-02.png).

Both arms remain `INCONCLUSIVE` / `UNCALIBRATED_PRESENTED_IMAGE` (exit 2): the named-screen variant has no calibrated
floor, which is the correct verdict and not a failure of this change.

## What this round does not claim

- **One cell still differs** (`r2c4`). Same class of defect or another one is not yet known.
- **The frame's whole-screen difference against the reference is larger than round 1's** (mean 2.61 → 7.61) although
  the panel's fell. The sky is 71.1 mean against the reference's 54.3 and the left-hand terrain 34.1 against 48.4,
  while the world content (`samples=5760 nonAir=1296`), the pinned world time (6000) and the camera pose are the same.
  **The same-jar control `r22d` (`-Dceltium.itemMeshCache=false`) shows the same thing** - sky 69.3, left terrain 33.6,
  whole frame 7.15 - so the difference is not this change and not the item cache: it is a property of this build's
  world rendering against the round-1 leg, and it is left **unattributed** here rather than called either a regression
  or a fix. The panel figure is the one this round moved, and the control reproduces it to 0.03 (1.32 against the
  fix's 1.29), which is what says the panel improvement is the item cache and nothing else.
- No FPS and no other scene's picture is claimed. The instrument index was regenerated for the two new counters.

## Instruments this round added

- `CELTIUM_EMPTY_UPLOAD fmt=<id> screen=<class> mode=<gl mode> count=<n>` — the finished immediate batches that carry
  no vertices and are therefore never offered, never refused and never counted anywhere else. Bounded to the first
  arrival and the powers of ten. This is the line that says "the draw never existed" instead of "the draw was lost".
- `creativeGridAt<tick>{Slots,Filled,Contents}` in the fixture — the grid walked again at ticks 180/240/300, because
  `creativeGridFilled` is the state of the tab switch and not of the frame.
- `CELTIUM_ITEM_MESH_EMPTY colour=<hex> model=<model>` — an item draw whose model answered no quads, which is now
  left uncached.
