# The panorama copy is served and reads a different image than the faces wrote

Date: 2026-10-11. Owner: gui-stage. Round 19.

## The measurement

```
CELTIUM_TEX_COPY_SERVED dest=20 level=0 destOffset=0,0 source=0,0 256x256 readFbo=0
  mean=255,255,255 opaque=65536/65536 served=1
```

The copy path works - it is served, the destination is named, the rectangle is right - and the **source rectangle is
uniform white**, so the cube faces are not in the image the copy read.

## The mechanism, from the source

`VulkanTerrainGpuBackend.celtium$serveTextureCopy(...)` resolves the read framebuffer through
`celtium$ownedReadImage(readFbo)` (`:9536`, defined `:9733`). With `readFbo == 0` that function returns:

```java
GllessFrameTarget glless = offTargetTarget();
if (readFbo == 0 && glless != null) {
    // Identity 0: the GL-less default framebuffer. Its draws are routed to the default pair, and the pair's
    // image is the one the game's own finished-frame composite reads.
    glless.ensureOffTarget(this.targetWidth, this.targetHeight);
    return glless.offTargetColorImage();
}
```

**`readFbo = 0` resolves to the off-target pair's colour image, not the frame image.** The frame image is returned only
for `readFbo != 0` equal to `mainTargetIdentity` - the game's own main framebuffer.

So the two halves are asking different questions of the same identity:

- the copy says "readFbo 0 means the default pair";
- the cube-face draws were measured with `target=1280x720` (`gl=0,0 256x256 clipped=0,464 256x256 target=1280x720`),
  i.e. they are routed to the **frame** image.

If those two are both true, the copy reads an image the faces never wrote, and uniform white is exactly what it would
find. **The copy is not broken; it is pointed at the wrong image for this draw.**

## The confirming measurement (one leg, no code change)

Print, in the same run, the two facts side by side:

1. `CELTIUM_IMMEDIATE_ENQ` (with `-Dceltium.vulkan.enqAll=true`) for the cube-face groups: their `target=` identity.
   If it is the **main** identity (not 0), the faces are in the frame image.
2. `CELTIUM_TEX_COPY_SERVED`'s own resolution: which image `celtium$ownedReadImage(0)` returned - the pair label from
   `GllessFrameTarget.pairLabelForIdentity(...)`, the way the framebuffer-bind census already names pairs.

If (1) says main and (2) says the default pair, the mismatch is proven and the fix is one of:

- resolve `readFbo = 0` to the **frame image** when the faces were drawn to the main target (the copy then reads what
  the faces wrote), or
- route the cube faces to the default pair, so that `readFbo = 0` is telling the truth about where they went.

Which of the two is correct depends on what vanilla does with the panorama: it draws the cube faces with the default
framebuffer bound and then copies out of it, so the *second* reading - the faces should be in the default pair - is the
one that matches the game's own model. **That is the fix to prefer**, and it is a routing question, not a copy
question.

## Why this is not the copy path

The refusal census is empty for this call and `served=1`; `mean=255,255,255 opaque=65536/65536` says the read
succeeded and returned real pixels - just not the ones wanted. A copy that reads the wrong image and a copy that reads
the right image before the submission look identical in the served counter, which is why the pairing above has to be
printed rather than inferred.
