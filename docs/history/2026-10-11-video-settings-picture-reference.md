# task-42 — the video-settings picture row: the reference is producible, and the floor is what fails

Date: 2026-10-11. Scene `videoSettings`, pose `x=0.5 y=12.0 z=0.5 yaw=180 pitch=20`, 1280x720.

## 1. The reference leg is producible, and it is the same variant

`vs-pic-ref`: **upstream Celeritas `989ebf2e` (`d12425eaaf184e79`), no celtium jar, no glless flag**, the same
fixture as the candidate (`29f1a7d399017f66`), `-Dceltium.validation.scene=videoSettings`, `screenshotAtTick=60`.

| field | value |
|---|---|
| `sceneCalibrationKey` | `scene=videoSettings pose=x=0.5000 y=12.0000 z=0.5000 yaw=180.000 pitch=20.000 stack=default blockSweep=0 allBlocks=false entities=none screen=none guiScale=3` |
| `sceneReferenceCalibrationKey` | identical to the above |
| `sceneCalibrationKeyMatches` | **True** |
| `guiScreenClassAtCapture` | `net.minecraft.client.gui.GuiVideoSettings` |
| `guiCaptureTick` | 235 |
| `guiScreenVerdict` | `GuiVideoSettings was open at the capture` |

The **same fixture runs the same chain in both legs**, so the operated screen, the option and the restored value are
identical: `videoSettingsChain` = `RENDER_CLOUDS 'Clouds: Fancy' -> 'Clouds: OFF', saved, restored`, and the restore
now cycles back properly (`pressed RENDER_CLOUDS 2 more time(s) and Done; value is now 'Clouds: Fancy' ... (restored)`).
Before that fix the candidate captured `Clouds: Fast` while the reference showed `Clouds: Fancy` — a three-state
option is not restored by one extra press, and the pair would have compared different variants.

So the answer to the task's item 4 is: **the reference can be produced**; the scene does not need a screen upstream
does not have. The screens match in content - title, all fourteen option rows, the scrollbar and the Done button are
present on both sides (`.celtium-work/sky-ratio/vs-apply4-gui-small.png` and `vs-ref-gui-small.png`).

## 2. What actually fails: the scene's floor was calibrated on a different picture

Both legs fail the same assertion, including the real GL reference:

```
FAILED: presentedVerticalBands=229 is below the scene floor of 250 for scene=videoSettings
        stage=gui file=screenshots/celtium-validation.png
presentedImageAssertionFailures 3
```

and the floor's own provenance, read from `sceneGateSource` in the same report, says where it came from:

```
gl-bridge scene-ref-video (scene=videoSettings, x=0.5 y=12.0 z=0.5 yaw=180 pitch=20,
  gui frame vs the same run's world frame): world frame distinctColours=1540
  nonBackgroundFraction=0.728299 verticalBands=638 blackFraction=0.000000
  -> floors distinctColours>16 nonBackgroundFraction>=0.40 verticalBands>=250 ...
```

**The `verticalBands>=250` floor was taken from the scene's WORLD frame (`verticalBands=638`), and is applied to the
scene's GUI frame.** The video-settings screen is a mostly flat panel of grey rows, so the frame that actually shows
it has 229 bands (reference) and 212 (an earlier candidate) - both below a floor measured on a picture of the
terrain. The reference therefore fails its own gate, which is the definition of a floor that does not describe the
picture it is applied to.

Per `AGENTS.md` this is not a reason to lower the floor. The fix is to calibrate the GUI stage's floor on the GUI
frame of the reference run, which exists (`scene-ref-video2` has both frames); that is the gate owner's call, and
this record is the evidence for it.

## 3. State of the pair

- Reference leg: **done** (`vs-pic-ref`, tick 235, key matches, chain ran and restored).
- Candidate leg: **queued** when this was written (`vs-pic-cand`, GL-less, jar `28dabb1dde958a7f`, same fixture and
  flags, waiting for the single-game slot). The per-region table and crops are added to this file when it lands; the
  screen content comparison above is from `vs-apply4` (same scene, jar `7972d151b86fb2b4`) against the reference, which
  is a content comparison at different ticks and is **not** offered as the acceptance pair.

## 4. Crops

`.celtium-work/sky-ratio/vs-ref-gui-small.png` (reference) and `vs-apply4-gui-small.png` (candidate), 50 % scale.
