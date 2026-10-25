# Why the Mojang logo's DynamicTexture upload never reaches its mirror

Task-74. Read-only audit. No Java edited, no leg run. Every claim carries file:line.

## 1. The original path

- `Minecraft.drawSplashScreen` (`.celtium-work/mc-sources/net/minecraft/client/Minecraft.java:959-1011`) creates the
  logo texture from `ImageIO.read(LOCATION_MOJANG_PNG)` (256x256) and draws a full-screen quad (all four vertices at
  tex (0,0)) and a centred 256x256 quad into its own offscreen `Framebuffer`, then `framebufferRender`s it.
- `DynamicTexture` (`.../texture/DynamicTexture.java`): `:18` `DynamicTexture(BufferedImage)`, `:25`
  `DynamicTexture(int textureWidth, int textureHeight)`, `:37` `updateDynamicTexture()`.
- The BufferedImage constructor delegates to the size constructor first and then copies the image's pixels into
  `this.imageData` before calling `updateDynamicTexture()` - so the array handed to `TextureUtil` is the logo's, whose
  (0,0) texel is **transparent**, not white.
- `updateDynamicTexture()` calls `TextureUtil.func_110988_a` (`uploadTexture(int, int[], int, int)`) - the **int[]**
  entry point (measured in this session: `CELTIUM_UPLOAD_CALLER id=8 256x256 first=0xffffffff callers=[
  TextureUtil.func_110988_a <- DynamicTexture.func_110564_a:35 <- DynamicTexture.<init>:18 <- Minecraft.func_180510_a:889
  <- SplashProgress.drawVanillaScreen:1051]`).

## 2. Our hooks

| hook | covers | evidence |
|---|---|---|
| `TextureUtilMixin` injection on `func_110988_a` (int[]) | the DynamicTexture path | the census line above shows the hook firing for it, with `first=0xffffffff` |
| `TextureUtilMixin` injection on `func_110989_a` (`uploadTextureImageAllocate`, BufferedImage) | SimpleTexture / PNG loads | the panorama's `CELTIUM_INVALIDATE_ORIGIN glTexture=46..51 <- TextureUtil.handler$…invalidateUploadedTexture <- SimpleTexture.func_110551_a` |
| `func_110988_a` also carries the early-announcement replay (`TerrainGpuBackends` pending list) | the pre-backend window | the panorama's PNGs were recovered by that replay |

So the entry point the DynamicTexture constructor uses **is hooked** - this is not "no hook".

## 3. Where the white fill comes from

- The mirror census reports the logo's mirror as `content=opaque=65536/65536 firstOpaque=0xffffffff` - entirely white,
  and the logo's own (0,0) texel is transparent, so the mirror does not hold the logo.
- The same census prints `wrapDefaults` inside the `glLess[...]` block. That counter is incremented in
  `VulkanTextureRegistry.wrapRepeats` when the stub has no recorded `glTexParameteri` for the pname
  (`VulkanTextureRegistry.java`, the `wrapDefaultsUsed++` site), i.e. it counts wrap answers that fell back to the GL
  default. It is **not** a statement that the mirror was filled with a default: a mirror's pixels come from the
  announcement/capture path, not from the wrap answer. Reading `wrapDefaults` as "filled with a default" is a
  misattribution that this audit records explicitly.
- The white pixels in the mirror therefore came from an actual captured upload whose `int[]` was white - the measured
  `first=0xffffffff` on the `func_110988_a` hook for that name. Which upload is white depends on which constructor
  path was taken: the **size-only** constructor (`DynamicTexture(int,int)`) allocates an array and calls
  `updateDynamicTexture()` before any image is copied in, so its upload is a white/zero array. `SplashProgress`'s own
  texture (the caller chain measured above ends in `SplashProgress.drawVanillaScreen:1051`) is created that way.

## 4. Verdict

**(b), in its ordering form: the captured upload is the constructor's pre-image (white) array, and the upload that
carries the logo's own pixels is not the one the mirror ends up holding** - either because it never happens on this
path (the size-only constructor has no image to copy) or because it is a *second* upload for the same name whose
announcement the mirror did not keep. The entry point is hooked (so not (a)), and a dropped announcement is possible
but not what the census shows: the census shows a *white content*, which is the pre-image array, not a missing fill
(so (c) is not the primary shape).

**The one measurement that separates the two sub-cases**: an unbounded `CELTIUM_UPLOAD_CALLER` line **for that single
GL name** (the `mirrorDumpIds` filter pattern already exists), printing `first=` for every upload of that name.

- If exactly one upload arrives with `first=0xffffffff` (white), the logo's pixels never reach `TextureUtil` on this
  path - the defect is in the constructor path / the game's own data, and the fix is not ours.
- If two arrive, the second with `first=0x00000000` (the logo's transparent (0,0) texel), then the capture works and
  the announcement for the second upload is being dropped or overwritten in the registry - and the same counter plus
  `CELTIUM_MIRROR_SOURCE`'s `announced=` field says which.

## Not established

- Whether the logo's texture is the same GL name as `SplashProgress`'s own (the measured caller chain names
  `SplashProgress.drawVanillaScreen`, not `Minecraft.drawSplashScreen`), so the white mirror measured may belong to the
  splash-progress texture rather than to the Mojang logo. The per-name upload census above settles it.
- Whether `wrapDefaults` appearing in the same line is causally related to the white content (this audit says it is
  not, and cites the counter's increment site).
