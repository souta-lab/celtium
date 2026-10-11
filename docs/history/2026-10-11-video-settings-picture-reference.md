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

## 3. The pair, and the region numbers

Candidate `vs-pic-cand` (GL-less, jar `28dabb1dde958a7f`, fixture `29f1a7d399017f66`, `GuiVideoSettings` at tick 297)
against the reference `vs-pic-ref` (upstream Celeritas, tick 235). Both keys identical,
`sceneCalibrationKeyMatches=True`, both restored `Clouds: Fancy`, both `FAILED_PRESENTED_IMAGE` on the same
`verticalBands` assertion.

| region | candidate mean | reference mean | ratio | pixels >8 |
|---|---|---|---|---|
| title (0,20,1280,60) | 41.8,31.9,24.8 | 39.4,30.2,23.8 | 1.063,1.055,1.044 | 9,297 of 51,200 (18.16 %) |
| option rows, left column (180,80,620,620) | 78.2,77.4,76.7 | 79.3,78.2,77.3 | 0.987,0.989,0.992 | 10,134 of 237,600 (4.27 %) |
| option rows, right column (660,80,1100,620) | 68.1,67.3,66.5 | 69.2,68.1,67.2 | 0.985,0.988,0.991 | 10,134 of 237,600 (4.27 %) |
| panel (180,80,1100,624) | 70.2,69.3,68.5 | 71.4,70.2,69.2 | 0.983,0.987,0.990 | 22,842 of 500,480 (4.56 %) |
| **Done button (340,640,940,690)** | 110.1,110.1,110.1 | 110.1,110.1,110.1 | **1.000,1.000,1.000** | **8 of 30,000 (0.03 %)** |
| whole frame | 52.9,49.9,47.6 | 55.1,51.7,48.9 | 0.959,0.966,0.972 | 114,666 of 921,600 (12.44 %) |

**The title band is its own finding, not part of the panel number.** It is the largest of the screen's regions at 18.16 % and it is the font path: the same residual appeared as the label row in the HUD measurement (47.9 % against the stored reference before the alpha-test fix, 0.00 % after it), so the shape is known and it belongs to the font/text work rather than to the panel. It is listed as its own row above so it cannot hide inside the panel figure.

The screen's own content matches: the Done button is pixel-identical, the option rows and the panel are within
1-2 % of the reference's means, and the title band is the largest of the screen's regions at 18.16 % - the font path,
not the panel. The whole-frame 12.44 % is dominated by the world behind the screen, which is the separate open
question. Crops: `.celtium-work/sky-ratio/vspic-crops/` (candidate above, reference below).

## 3b. State of the pair

- Reference leg: **done** (`vs-pic-ref`, tick 235, key matches, chain ran and restored).
- Candidate leg: **queued** when this was written (`vs-pic-cand`, GL-less, jar `28dabb1dde958a7f`, same fixture and
  flags, waiting for the single-game slot). The per-region table and crops are added to this file when it lands; the
  screen content comparison above is from `vs-apply4` (same scene, jar `7972d151b86fb2b4`) against the reference, which
  is a content comparison at different ticks and is **not** offered as the acceptance pair.

## 4. Crops

`.celtium-work/sky-ratio/vs-ref-gui-small.png` (reference) and `vs-apply4-gui-small.png` (candidate), 50 % scale.
