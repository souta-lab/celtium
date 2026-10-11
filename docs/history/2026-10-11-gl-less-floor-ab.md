# A/B: the GL-less allBlocks floor loss is a renderer regression, not the camera

One fixture, one command, two renderer jars. Both legs: AMD RX 6400 (RADV NAVI24), GL-less
(`-Dceltium.vulkan.glless=true`), validation on, `presentReadback` + `dumpPresentedImage`, allBlocks,
`minWorldFrames=5`, duration 110, render node `pci-0000:03:00.0-render`, game dir under
`.celtium-work/runtime/ab-*`, 2026-10-11 11:1x.

| | A: pinned pre-regression | B: current build |
|---|---|---|
| renderer jar sha256 | `d2073e9456da7bcc…` (built 09:11, last good run 09:16) | `cc1ca7ee8e256c13…` |
| fixture jar | 09:11 pinned, same in both legs | — |
| `presentedNonBackgroundFraction` | **0.733183** | **0.364906** |
| `presentedImageMeanAtCapture` | 213.6666717529297 | 213.6666717529297 |
| `presentedBackgroundColour` | **0x571716** (the scene's own dark background) | **0xB3CFFF** (the clear colour) |
| `presentedImageVerdict` | **PASSED** | FAILED_PRESENTED_IMAGE |
| `celtium/vulkan-check` | passes=778 skipped=0 errors=0 **lastDraw=22 lastSections=25** | passes=433 skipped=0 errors=0 **lastDraw=8 lastSections=8** |
| world dump sha256 | `fc549f5da7f08a88…` (presented-world), `4030a1e7477f83d3…` (frames/frame-40.png) | `25238b5edf64b19a…` (all three dumps identical) |

## The experiment separates the two explanations

The competing explanations were "the renderer regressed" and "the camera moved and the sky view is correct".
They predict different things about `presentedImageMeanAtCapture`: a moved camera changes it, a lost floor
does not have to. The two legs report **the same mean to the last bit** (213.6666717529297), so the camera is
identical by construction and explanation two is ruled out. What changed is which pixels those same camera
rays land on.

Pixel analysis of the two world frames (`.celtium-work/ab-evidence/`):

| | A | B |
|---|---|---|
| most common colour | (87,23,22) = 0x571716, 15.2% of pixels | (179,207,255) = 0xB3CFFF, **63.5%** |
| bottom-right quadrant, exactly the background colour | 30.3% | **100.0%** |
| top-right quadrant | 8.4% | 98.2% |
| quadrant means | TL 72/69/122, TR 131/131/148, BL 83/44/58, BR 96/55/52 | TL 100/111/172, TR 176/204/251, BL 143/158/205, BR **179/207/255** |

`ab-side-by-side.png` shows it directly: A is a lit, tiled floor with the allBlocks wall and a real sky; B is
the clear colour with a few translucent shards. The terrain contributes no opaque pixels in B, and
`lastDraw`/`lastSections` fall from 22/25 to 8/8 in the same run - the terrain path is still submitting, but
it is drawing almost nothing.

## What this fixes in the bisect

The regression is in the renderer between jar `d2073e9456da7bcc` (good) and `cc1ca7ee8e256c13` (bad), which is
the 09:17-09:30 window: `b7b634179` (09:17 unbound-framebuffer composite), `30a30a375` (09:27 the
Project.gluPerspective mirror), `9655ff681` (09:30 depth format). The depth format is separately ruled out on
AMD by `scripts/vulkan_depth_format_probe.py` (AMD exposes D32_SFLOAT, which is both the chooser's first
preference and the constant it replaced), which leaves the projection hook as the suspect - and it is in both
legs of the renderer (the GL bridge lost its floor too), which is consistent with a matrix change rather than a
GL-less-only path.

## Gate defect found while measuring

The B leg reports `sceneGateFloorsEnforced=true`, `sceneGateCalibrated=true` and still passes a
`nonBackgroundFraction>=0.4` floor at 0.364906, because the calibration key differs between the legs: A's
`sceneVariant` is `default flat stack, allBlocks wall` and B's is the same string **plus `, guiScale=3 (the
scale the panel floors were measured at)`**. The variant string is produced by the renderer jar itself, so the
key changed under the gate when the renderer changed, and the enforced floor was skipped for exactly the runs
that needed it. A gate that reports "enforced" while not enforcing is worse than no gate; the key mismatch
should surface as uncalibrated, not as a pass.

## Instruments

`-Dceltium.vulkan.presentReadback=true -Dceltium.validation.dumpPresentedImage=true` are both required for the
picture counters to exist at all: without them `presentedNonBackgroundFraction` is absent and
`presentedImageMeanAtCapture` is -1.0 with `pictureEvidenceProblem` naming the missing flags. Runs measured
without them cannot say anything about the frame.
