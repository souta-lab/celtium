# The video-settings screen: the Celeritas screen is the one captured, measured against itself

Owner: **glyph-fix**. Leg `vs-celeritas` (jar `a1a3aa967f5ec1e0a4449e0e9ca52e1c`, fixture
`celtium-validation-client` at the same tree, AMD RX 6400, GL-less, presented dump), scene `videoSettings`,
`-Dceltium.validation.videoSettingsOption=RENDER_CLOUDS`, `-Dceltium.validation.pinSections=64`.

## What the leg establishes

* **The Celeritas screen is the one the picture is of**: `guiScreenClassAtCapture =
  org.taumc.celeritas.impl.gui.CeleritasVideoOptionsScreen`. `guiScreenOpenedVia` still carries the *entry* the fixture
  used (`Minecraft.displayGuiScreen(new GuiOptions(null, gameSettings))`), so the class at capture is the field to read.
* **The panel is drawn, not a flat fill**: crop `(180,80,1100,624)` has **587 distinct colours** (mean `(27.6,26.5,27.6)`);
  `rows-left (180,90,620,620)` 506; `Done (340,640,940,690)` 145; the world strip below it `(360,690,920,720)` 103.
  Frame sha256 `7ab2ffa523b417ff…`, crop `celeritas-screen-panel-1x.png` sha256 `a8253fb50caca65a…`.
* **The capture is pinned**: `captureListedSections=59`, `pinSections=64`, `worldTicksAtEnd=237`,
  `frameIntervalSampleCount=76`, `frameIntervalP50Ms=41.6`.
* Reproduce with `docs/history/evidence/video-settings-20261012/video-settings-crop-check.py <frame>`.

## What it does **not** establish, and why the acceptance is a self-consistency claim

There is **no Celeritas-screen reference and one cannot be taken with the upstream jar**: the diverter that opens
`CeleritasVideoOptionsScreen` lives in our tree (`forge122/.../mixin/features/options/MixinGuiOptions.java:20`), so an
upstream leg opens vanilla's `GuiVideoSettings` - which is why `vs47d-ref` and `wbs-vs-ref2` both record the fallback
text, and why the 139-colour panel figure belongs to vanilla's screen and must not be applied to this one. The
`vs-pic-ref` leg §0.2322's table cites has also had its screenshots cleaned, so that frame no longer exists.

The leg's own early dump is the **world** (7,247 distinct colours in the crop, centre pixel terrain), not the same
screen, so a ratio against it would compare a screen with terrain and is not reported. The honest claim is therefore:
the screen opens, its class is recorded, the panel/rows/Done are drawn with the distinct-colour counts above, and the
same-screen upstream comparison is a build decision (an upstream build carrying that one mixin), not a leg.

## One thing this leg does not clear: ten validation messages, with the fix in the jar

`validationMessageCount=10`, `validationClean=false`, all ten the same:
`vkQueueSubmit(): pSubmits[0].pSignalSemaphores[0] (VkSemaphore 0x1e900000001e9) is being signaled ... but it was
previously signaled` (`VUID-vkQueueSubmit-pSignalSemaphores-00067`). The jar **contains** the fix for exactly this
class (`7e3afed8d`, "the bridge done semaphore is signalled only when its previous signal has been waited on" -
`doneSemaphoreSignalOutstanding` is present in `FrameImageTarget`, `GllessFrameTarget` and `VulkanTerrainGpuBackend`
in the built jar), so the guard does not cover the path this leg takes. Reported rather than smoothed: the
`validationMessageCount=0` half of the acceptance **fails**, and that is a finding for the owner of that fix.
