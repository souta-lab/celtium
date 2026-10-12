# The vanilla loading screen: what the composite does, and what the loading screen really shows

Legs: `.celtium-work/r212-splash-splash13`, `splashpair2`, `splashmark2`, `splashpresent`, `splashfog`,
`splashrebuild` (harness `scripts/prism_validation.py`, AMD RX 6400 / RADV NAVI24, 1920x1080, jar hash in
each leg's `.out`, `-Dceltium.vulkan.glless=true`, one game at a time, `--exclude-instance-mod
'celtium-*pre-stub-changes.jar'`).

## The question

`docs/plan.md`'s header table carried "loading screen: white" as a lost draw: the frame image the present
serves was a single white value while the chain pair held the game's picture. The candidate was the
sampling side - a stale publication, a wrong view, a missing transition, a stale descriptor.

## What was ruled out, by measurement

| Candidate | Leg / flag | Result |
| --- | --- | --- |
| The composite draws nothing | `frameClearMark` (frame image cleared green at the head of the frame's pass) | the marker is gone after window 1 -> a draw writes, and it is white |
| The composite samples the wrong image | `pairProbeClear` (pair cleared magenta just before the composite) | the frame image is magenta at the next boundary -> the composite does sample the pair |
| The pair is blank when sampled | `CELTIUM_SPLASH_PAIRPOST` readback at the first boundary after the composite | pair centre (220,220,220) with darkPixels 84512 = the game's picture, not blank |
| A stale descriptor at the draw | `rebuildDescriptors` (8114 of 8614 groups rebuilt at the draw) | picture unchanged |
| The published wrapper is stale | `CELTIUM_SPLASH_HANDLES` | `publishedName6 == chainPair`, `publishedIsLivePair=true` |
| The layout is illegal | `CELTIUM_MIRROR_DRAW ... layout=1` | GENERAL, which sampling accepts |

## The answer: the first presented frame has no composite in it yet

The enqueue log carries the offer times, and `CELTIUM_PRESENT_PATCH` carries the presents:

```
03:36:07  CELTIUM_IMMEDIATE_ENQ frame=0 group=1 ... fmt=2 texture=8 target=1277161755
03:36:08  CELTIUM_PRESENT_PATCH present=1 frame=0 windows=1 windowsThisFrame=1
            frameCentre=(255,255,255) pairCentre=(220,220,220)
03:36:12  CELTIUM_IMMEDIATE_ENQ frame=0 group=2 ... fmt=2 texture=6 target=0
03:36:15  CELTIUM_WINDOW_DRAW identity=0 ... windowsThisFrame=1   <- the composite is drawn here
```

The game offers its framebuffer's draws, presents a frame (whose only content is the game's white clear),
and only then draws the framebuffer into the window. The presented white frame is that frame, not a lost
composite: the composite did not exist yet at the present. Window by window, from `CELTIUM_FRAME_AT_WINDOW`:

```
afterWindows=0  frameCentre=(0,0,0)          (fresh frame image)
afterWindows=1  frameCentre=(255,255,255)    (the present's frame: the game's own clear)
afterWindows=2  frameCentre=(219,219,219)    (the pair, copied in)
afterWindows=3  frameCentre=(219,219,219) corner=(73,95,36)   (the splash and its bar)
```

So the harness's loading capture is a first-present timing artifact of the game's own loop, and the
`UNCALIBRATED_PRESENTED_IMAGE` verdict on this scene is honest: no reference judges it.

## Correction (round 236): there were no two defects - the dump was upside down

The frame image, like every image this renderer owns, is in GL texture order: **row 0 is the picture's bottom**,
and the present flips once. The `stage-*.png` dumps write the rows in that order, so reading one directly shows
the picture upside down. Flipped into picture order (`VulkanGlTexture.dumpStageImageAsPicture`, which now also
writes `stage-splash-p<N>-picture-1920x1080.png`), the frame that carries the splash shows:

* the **Mojang logo, upright and correct** - the "horizontally mirrored Cleanroom logo" of round 235 was an
  upside-down logo, whose letters read like mirrored text;
* the panorama in the **bottom-left** 256x256 box - exactly where `glViewport(0,0,256,256)` puts it, not a
  "tile in the top-left corner".

The mirroring question is also closed by three independent measurements on the same leg
(`r212-splash-splashflip2`, `splashmir48`, AMD RX 6400, 1920x1080, `glless=true`):

* the captured vertices are not mirrored: the logo quad's texture coordinates are (0,1), (1,1), (1,0), (0,0) at
  x = 112, 368, 368, 112, which is `Minecraft.draw(...)`'s own order;
* the group's projection diagonal is `(+0.004, -0.007)` and the texture matrix is `none`, so neither the
  projection nor the tex matrix mirrors x;
* texture 8's mirror holds `mojang.png` the right way round (`mirror-gl8-256x256-l0-*.png`).

What remains for the loading screen is different from what round 235 said: (a) whether the full-screen blurred
panorama composite the menu draws also runs on the loading screen (the 256x256 box is only its first half), and
(b) which texture the Cleanroom splash's own logo comes from - our legs show the *vanilla* Mojang splash, so the
user's session runs Cleanroom's splash code, whose textures have to be identified by name.

## Instruments this unit added (all off by default, all bounded)

* `-Dceltium.vulkan.frameClearMark=true` - green clear at the head of the frame's pass, plus
  `CELTIUM_FRAME_AT_WINDOW` and `CELTIUM_SPLASH_FLIP` (frame and pair centre patches, printed on change).
* `-Dceltium.vulkan.pairProbeClear=true` - magenta pair clear before the frame's own pass.
* `-Dceltium.vulkan.presentPatch=true` - `CELTIUM_PRESENT_PATCH` at the first eight presents.
* `-Dceltium.vulkan.rebuildDescriptors=true` - rebuild every textured group's descriptor at the draw.
* `-Dceltium.vulkan.mirrorDrawLog=<n>` (existing) now carries the sampled view, the live pair's view and image,
  `standsForLivePair`, the recorded layout, the vertex positions/UVs/colours (twelve vertices, so a two-quad group
  shows its picture), the group's projection diagonal, its texture matrix and its fog.
* `VulkanGlTexture.dumpStageImageAsPicture` - the stage dumps in picture order; the splash probe writes
  `stage-splash-p<N>-picture-1920x1080.png` beside the image-order one. Unit: RGBA8, whole image, no cap beyond the
  dump gate. Its whole purpose is that an image-order dump read as a picture is a false defect.
* Announcement mirrors are dumped under the GL name they were announced with (`this.encodeLabel`), not as
  `mirror-gl-1-...`: the sixty-four 256x256 canvases of a splash leg were previously indistinguishable by name.

## Round 236 close-out: the loading screen has no defect

* **The panorama chain is menu-only.** `renderSkybox` (`GuiMainMenu.java:513`), `drawPanorama` (`:388`) and
  `rotateAndBlurSkybox` (`:478`) have exactly one caller each, and all of them are inside `GuiMainMenu`
  (`:549 -> :513 -> :517 -> :518-524`, the blur seven times). The loading screen therefore never draws the
  full-screen stretch quad; the 256x256 box is the chain's own viewport, seen at a present that happened
  before the chain finished. Leg `r212-splash-splashblur` (`-Dceltium.vulkan.mirrorDrawLog=400`) shows the
  chain running in the same frame: `groupTexture=21` eight times with twelve vertices each, i.e. three blur
  quads per copy, plus the faces at `groupTexture=46..51`.
* **The white first frame is the game's own loop**: it draws its framebuffer, swaps (the frame image holds
  the game's white clear), and only then draws the composite into the window. A GL client shows the same
  picture at that instant, so this is not a lost draw and not a deferral of ours.
* **The logo is Mojang's, not Cleanroom's** - the round-235 prose called it "Cleanroom" while its own
  transcription read `MOOЯNAƎJƆ`, i.e. an upside-down MOJANG. `mirror-gl8-256x256-l0-*.png` is mojang.png the
  right way round.
* The fixture runs the vanilla path (`MinecraftMixin` injects `Minecraft.drawSplashScreen`, whose caller is
  `SplashProgress.drawVanillaScreen`), and `config/splash.properties` has `enabled=true`, `background=0xFFFFFF`,
  `bar=0xCB3D35`. Nothing in that configuration explains a white screen beyond the timing above.
