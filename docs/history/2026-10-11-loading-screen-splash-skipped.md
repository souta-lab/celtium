# The white loading screen: the frame-texture fix works (the menu renders), and the splash itself is skipped by design

Owner: **fog-path** (round-16, loading-phase capture). Date: 2026-10-11, ~23:10 JST. GPU: AMD RX 6400
(RADV NAVI24). Leg: `r16-loading` — **fixture-free** GL-less startup leg, compositor capture
(`scripts/compositor_capture.py --interval 1.0 --startup-timeout 1500`), 151 captures, jar
`fog-path-mod14.jar` sha256 `0b1b9ea50550ae5926599c791974431427209bb404306cf8e75d7e38a57316bf`.

## 1. What the compositor shows

| captures | content | distinct colours |
|---|---|---|
| 0000-0010 | black | 1 (window not mapped) |
| **0011-0013** | **uniform white** | **1** |
| 0014-0150 | the main menu: logo, "JAVA EDITION", splash text, Singleplayer/Multiplayer/Mods/Options/Quit, the mod list and the copyright line | 359-828 |

So the uniform white field is **down from the whole capture to three frames**: the window's first mapped
seconds, between `map` and the first presented frame. From capture 14 on, the picture is real content
(`compositor-0020.png` is the menu, whole and legible).

## 2. Where the loading screen itself went: it is cancelled by design

```
[Celtium] CELTIUM_GLLESS splash progress skipped (no shared GL context)
```

`forge122/src/main/java/org/taumc/celeritas/mixin/core/SplashProgressMixin.java:18`
(`celtium$skipSplashUnderGlless`) cancels Forge's `SplashProgress` whenever
`-Dceltium.vulkan.glless=true` is set. There is therefore **no loading screen being drawn at all** in this
configuration: the white frames are not a composite of an empty mirror, they are a window that has nothing
to show until the first frame lands. The user's "the loading screen is still not displayed" is correct, and
the cause is this cancellation rather than the publish/drop lifecycle the Lead's fix addressed.

## 3. The publish/drop sequence and the present counters (same leg)

```
23:08:19 CELTIUM_EXTERNAL_IMAGE published glTexture=6 image=0x7f8e60cf0680 view=0x7f8e60cf0d20
         size=1281x720 layout=GENERAL owner=renderer
23:08:19 CELTIUM_PRESENT hookCalls=1 attempts=0 presented=0 ...
23:08:23 CELTIUM_EXTERNAL_IMAGE dropped: GL texture 6 was unpublished (the image is the owner's; only this
         wrapper's sampler is released)
23:08:23 CELTIUM_EXTERNAL_IMAGE published glTexture=6 ...
23:08:23 CELTIUM_PRESENT hookCalls=2 presented=1
23:08:23 CELTIUM_PRESENT hookCalls=3 presented=2
```

The publish/drop/republish cycle still happens, and the first successful present follows the republish - which
is exactly the window the three white captures fall in.

## 4. Acceptance

- **No loading-screen content capture exists**, and the named mechanism is the splash cancellation above
  (`SplashProgressMixin.java:18`), with the white window bounded to captures 11-13.
- `validationMessageCount` is not applicable: the leg is fixture-free (`--validate` is what starts the
  fixture), so the harness reports no message count; `endedBy=time-limit` at `--duration 150`.
- The frame-texture fix is **verified for what it can reach**: the menu, which was the uniform white field
  before, now composites with its own content (359-828 distinct colours, 137 consecutive captures).

## 5. What would put a loading screen on the screen

1. Stop cancelling `SplashProgress` under GL-less, or replace it with a GL-less-safe splash that draws
   through the immediate path (the same path the menu already uses, which this capture shows working).
2. Or accept the gap and draw something during it: the white window is the ~3 s between the window being
   mapped and the first presented frame, and any first-frame content would cover it.
