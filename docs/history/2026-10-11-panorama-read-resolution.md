# The panorama copy's read: resolved from the routing, and what it still finds

Owner: **fog-path**. Date: 2026-10-12, ~01:25 JST. GPU: AMD RX 6400 (RADV NAVI24).

## The measurement

Fixture-free menu leg `pan-menu3` (jar `fog-path-pan4.jar` sha256
`90f498c78f0b095c63839f73640b0f8c9a33db94844a1290aec7ac381dd2f81e`, `presentReadback=true`,
`-Dceltium.vulkan.readResolveLog=6`):

```
CELTIUM_READ_RESOLVE readFbo=0 mainIdentity=560902357 mainColorName=3 currentTarget=0 currentIsMain=false
    frameImage=140326271745696 defaultPair=140322201491328 chainPair=…
CELTIUM_TEX_COPY_SERVED dest=20 level=0 destOffset=0,0 source=0,0 256x256 readFbo=0 mean=255,255,255
    opaque=65536/65536 served=1
CELTIUM_IMMEDIATE_ENQ frame=0 group=1 fmt=2 texture=8 batches=2 target=572879803 mirrored=true worldPass=false
CELTIUM_IMMEDIATE_ENQ frame=0 group=2 fmt=2 texture=6 batches=1 target=0          mirrored=true worldPass=false
CELTIUM_EXTERNAL_IMAGE published glTexture=6 image=0x7efc5440b790 size=1280x720 owner=renderer
```

`0x7efc5440b790` is exactly the `frameImage` the resolve line names.

## The routing rule, read from the code

`GllessFrameTarget.routeLabelForBinding(identity, isMain)` returns `"frame-image"` when `isMain`, else
`pairLabelForIdentity(identity)` - `"default-pair"` for identity 0 and `"chain-pair"` for every other non-main
identity. So the panorama's faces (identity `572879803`) are routed to the **chain pair**, while the copy
(`readFbo=0`, `currentTarget=0`, `currentIsMain=false`) resolved to the **default pair** - an image the faces
never wrote, which is uniform white.

## The change (`VulkanTerrainGpuBackend.celtium$ownedReadImage`)

1. For `readFbo == 0` with a target bound that is not main, the read resolves through
   `routeLabelForBinding(currentRenderTarget, currentRenderTargetIsMain)` first, so it follows the same rule the
   draws did.
2. If that lands on the default pair while this frame's non-main draws went to a non-zero identity
   (`lastDrawnOffTargetIdentity`, recorded where batches are accepted), the read resolves through **that**
   identity's pair instead. Counted as `readResolvedFromLastDrawnTarget` and reported in the
   `celtium/vulkan-check` line.
3. `CELTIUM_READ_RESOLVE` (`-Dceltium.vulkan.readResolveLog`, default 6) prints the inputs and all three
   candidate images, so the branch that fired is in the log rather than inferred.

## What it still finds, and what that means

The copy still reads `mean=255,255,255` with both resolutions in place, so the image it now reads - the chain
pair - is **empty**: the panorama's groups are accepted for that target but their pass does not draw them.
That is the same shape as the loading screen's counter (`offTargetPerTarget`: **677 offered, 32 atPass, 17
drawnPasses**), and it is the subject of BH-14/15/16 (per-identity off-target targets, a failed creation that
is not silently re-routed to the main frame, and a clear that keeps its target, mask, colour and order), which
is the next unit.
