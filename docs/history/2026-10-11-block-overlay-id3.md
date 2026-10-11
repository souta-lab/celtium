# The block-breaking overlay and the BLOCK layout (id 3)

Owner: **texture-mirror** (task-17). Runs quoted by jar hash; pictures under
`evidence/texture-mirror-20261011/`.

## What the fixture's flag actually did

`-Dceltium.validation.breakBlock=true` called
`mc.renderGlobal.sendBlockBreakProgress(1, mc.objectMouseOver.getBlockPos(), 5)` every frame from tick 150 to 200,
**but only when `mc.objectMouseOver.typeOfHit == BLOCK`**. Instrumented (new report properties `breakSubject`,
`breakSubjectSource`, `breakSubjectDrawable`, `breakProgressCalls`, added to `ValidationClient.frame`):

- `breakTarget = BlockPos{x=0, y=11, z=-5}` in every run — and `typeOfHit = MISS`. `RayTraceResult.getBlockPos()`
  returns the ray's **end** position for a MISS, so the old property read like a block position while the guard had
  never passed: `breakProgressCalls = 0`, `sendBlockBreakProgress` was never called, and `damagedBlocks` stayed empty.
  That is why no histogram line in any run had ever shown id3: the overlay was never registered, so
  `RenderGlobal.drawBlockDamageTexture` never began its `BLOCK` batch.
- The scene cannot be fixed by aiming differently at this pose: the crosshair's ray ends 5.5 blocks away in mid-air,
  past the player's 5-block reach, so no block is under it.

**Fixture fix (landed, `validation/client/**`):** the subject is chosen by the fixture and registered through the
game's own method — no progress value is faked in the renderer:

- the mouse-over block when the block path would draw it (`renderBlockDamage` only emits for a `MODEL` block with a
  non-air material, and `drawBlockDamageTexture` skips a chest, an ender chest, a sign, a skull and any tile entity
  whose `canRenderBreaking()` answers yes);
- otherwise a stone block is placed once at the crosshair's ray end (`World.setBlockState`, the same call the
  fixture's block sweep uses), which is in the centre of the view and inside the 32-block range the draw enforces;
- `RenderGlobal.sendBlockBreakProgress(1, target, 5)` then registers progress 5 every frame, the same call
  `NetHandlerPlayClient.handleBlockBreakAnimation` makes;
- `-Dceltium.validation.breakSubjectOnly=true` places the subject and registers nothing — the control.

Measured after the fix: `breakSubject = BlockPos{x=0, y=11, z=-5} minecraft:stone[variant=stone]`,
`breakSubjectSource = mouse-over`, `breakProgressCalls = 24` (GL-less) / `155` (upstream Celeritas).

## The measurement

Same fixture, scene, pose and tick (terrain, x0.5 y12.0 z0.5 yaw180 pitch20, tick 200), GL-less jar
`f677efba5b3e341b` vs upstream Celeritas `989ebf2e` (`d12425eaaf184e79`):

| | GL-less (`t17c-break-on`) | upstream GL (`t17c-break-ref`) |
|---|---|---|
| histogram | `id3=16/16` — **first time the BLOCK layout is offered and accepted** | no celtium histogram |
| picture, crop `(560,300)-(720,450)` | plain stone cube | stone cube with the `destroy_stage_5` crack overlay |
| fixture verdict | FAILED (watchdog: no world frame after the capture; the picture itself is the presented dump) | PASSED |

Crop, reference left and GL-less right, 3x:
`evidence/texture-mirror-20261011/block-overlay-glref-left-glless-right.png`.

So the layout is now exercised and **drawn wrongly**: the batch is accepted and contributes no pixels.

## What the batch looks like, and what was ruled out

`CELTIUM_GROUP_BOUNDS` for the overlay, one line per frame (46..51 in `t17c-break-on`):

```
fmt=3 worldPass=true target=… texture=12 batches=1 vertices=24 indices=36 colour0=0xffffffff
proj=[m00=0.707 m11=1.257 m22=-1.0 m32=-0.05]
state=[blend=1 src=4 dst=2 depthTest=1 depthWrite=1 cull=0 alphaRef=0.1 depthFunc=515]
ndc x[-0.07..0.07] y[-0.23..0.08] z[0.99..0.99] behind=0
```

- `texture=12` is the block atlas mirror: `CELTIUM_MIRROR_SOURCE id=12 source=capture size=512x512
  content=opaque=139685/262144 full=true`, and its dump is a fully populated atlas
  (`evidence/texture-mirror-20261011/atlas-mirror-512x512-gl-less.png`). The terrain samples the same mirror and is
  correct, so the mirror is not the defect.
- 24 vertices = the cube's six faces; `colour0=0xffffffff` is the white the damage model's quads carry.
- The blend is the mirrored vanilla pair `DST_COLOR, SRC_COLOR` (Vulkan 4/2), the alpha reference is 0.1 and the
  depth function is `LEQUAL` — the state vanilla's `preRenderDamagedBlocks` sets.
- The NDC footprint is on the block (`x[-0.07..0.07] y[-0.23..0.08]`, the block's screen position), so the geometry
  is placed correctly with the combined projection×model-view the shader uses.
- The pipeline's vertex layout was checked against the format: stride 28, attributes at 0 (pos3f), 12 (col4ub),
  16 (uv2f), 24 (lmap2u) — the same offsets `DefaultVertexFormats.BLOCK` has, and the same the shader declares.

**Control that separates discard from rasterisation.** With the atlas mirror painted as a coordinate ramp
(`-Dceltium.vulkan.mirrorEncode=12`, every texel alpha=255) and otherwise identical flags, the overlay run differs
from its `breakSubjectOnly` control at the block: block region mean `(159.9, 118.0, 163.5)` against
`(127.5, 80.8, 118.5)`, mean absolute difference 38.2 over the block. So the batch **does** rasterise at the block
and the geometry/depth/blend are fine; with the real atlas it contributes nothing, which leaves the alpha test as the
mechanism: the sampled alpha is below 0.1 across the whole quad, so every fragment is discarded.

**And the sample is not the bound atlas.** Under the doubled multiply the blend implements
(`result = 2*src*dst`), the fragment colour the encoded pair implies is `src = result*255/(2*dst)` ≈
`(155..197, 251, 251)`: the blue channel alone rules the encoded atlas out — the ramp's blue is 128, so a fragment
sampling it cannot exceed 128 before the lightmap, and the lightmap can only scale it down. The fragment therefore
reads a bright image with the lightmap's shape (its red varies with the lightmap gradient while green and blue sit at
the lightmap's bright end), not the atlas the descriptor binds.

## State of the finding

Proven: the fixture never registered the overlay; the fixture now does; id3 arrives and is accepted; the batch is
placed, textured, blended and depth-configured as vanilla's; it rasterises (encode control); with the real atlas it is
discarded by the alpha test; and the colour it would contribute is not the bound atlas's content.

Not proven: *why* the texture sample is not the atlas. The two candidates are the overlay's texture coordinates (a
non-identity texture matrix applied by the shader's `u_TexB.z > 0.5` branch, or garbage UV bytes in the mesh) and the
descriptor the draw binds (the block pipeline writes binding 0 = texture, binding 1 = lightmap, and its shader reads
them in that order — both were read, and both look right). The next experiment that would name it, in the order I
would run them:

1. print `texMat` on the `fmt=3` group-bounds line (the group already carries `ImmediateGroup.texMatrix`, so it is one
   field) — a non-null affine on the overlay's group is the answer;
2. `-Dceltium.vulkan.mirrorPoisonFlat=12=FFFF00FF` — if the overlay's contribution turns magenta, it samples the
   atlas and the UVs are the problem; if it does not, the draw is reading another image.

## What remains unverified

The overlay at other damage stages (only progress 5 is exercised); the tile-entity damage path (chest, sign, skull,
banner) which `drawBlockDamageTexture` deliberately skips — the fixture's own subject selection avoids it; the
overlay in the entity/TESR scenes; and the `POSITION_NORMAL` white box the format sweep added (a different task).
