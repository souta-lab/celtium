# Verification: "the 2D HUD has never been present in a presented world frame"

Round 241. Read-only. My own script (`/tmp`-style inline python, not the Lead's), same file set the Lead described:
188 `runtime/*/screenshots/celtium-presented-world-run-*-kept.png` and 198 `celtium-presented-menu-*.png`.
Bands: **hotbar** = bottom 4.5% of height, middle 20% of width; **control** = rows 30-34%, middle 20% of width;
metric = distinct RGB triples in the band.

## 1. Does the distribution hold? - **No, it does not reproduce**

- World legs: **188**, of which **109 (58%) have a flat hotbar band** (<= 8 distinct colours). The Lead's method
  reported flat in every leg but one; my independent run finds **79 legs with a non-flat hotbar band**.
- The control band is flat (<= 8) in **85 of 188** legs, so "flat" is a common property of both bands, not a
  hotbar-specific signature. A flat reading therefore does not by itself separate "no HUD" from "a smooth region".
- Exceptions with numbers (leg, size, hotbar distinct, control distinct):
  `blit-world-native` 1920x1080 135 / 71; `font-f3` 1280x720 **204** / 10; `hei-cand` 1280x720 51 / **1086**;
  `hei-cand2` 52 / 1086; `hei-cur-merged` 47 / 1086; `p-bugrepro-bisect` 80 / 97; `r163-bh09-scissor` 640x480 100 / 5;
  `r164-bh09-noscissor` 92 / 3; `r164-bh09-scissor` 135 / 6; `hei-cand-cycle` 124 / 4;
  `cohort-healthcheck`, `cohort-tesr`, `framedump-white1`, `pairclear-splash` 11 / 1 (flat hotbar, flat control).
- So the claim as stated ("flat in every leg but one") is **not reproduced**: the measured distribution is roughly
  58% flat / 42% not flat on the same file set.

## 2. Is the hotbar band the right place? - **plausible, but not decisive as used**

- The hotbar sits at the bottom centre of the frame, so "bottom 4.5% of height, middle 20% of width" is the right
  *region* for this camera and GUI scale; at 1920x1080 that is 48 px tall, at 1280x720 32 px - both taller than the
  20 px hotbar, so the band should contain it.
- But the method as used cannot distinguish the three cases that matter: (i) no HUD drawn, (ii) HUD drawn but the band
  sits over a smooth part of the scene (sky, flat terrain) so the count stays low, (iii) the band is offset (the
  hotbar is centred, but a different GUI scale moves it). My control band being flat in 85/188 legs is the evidence
  that (ii) happens often: a flat band is not a HUD signal.
- A wrong band *can* produce the claimed answer by accident, and my own numbers show it does: any band over the sky
  or over flat terrain reads flat. The crosshair region (centre, rows ~45-55%) and the chat region (bottom-left,
  rows ~85-100%, left 30%) would each need their own control, and neither was used.

## 3. Positive control - **yes, one exists, and it refutes "no leg has ever captured a HUD"**

- **Menu legs are the positive control**: 198 menu dumps, only **14 flat** in the same band, and the non-flat ones hold
  **hundreds** of distinct colours in that band: `acc-intel-panorama-post-t20` 446 and 291; `acc-intel-panorama-pre-t20`
  442; `acc-intel-panorama-pre-t380` 422; `b08-menu` 305; `acc-intel-panorama-post-t380` 276 and 260. The menu picture
  plainly has widgets, so the band method **does** detect HUD-like content when it is present.
- World legs with HUD-like content in the same band also exist: `font-f3` 204 distinct (the F3 overlay leg),
  `blit-world-native` 135, `r163-bh09-scissor` 100, `r164-bh09-scissor` 135, `hei-cand` 51 with a 1086-colour control.
- Therefore the stronger statement "no leg here has ever captured a HUD" is **not supported**: menu legs and several
  world legs carry many distinct colours in exactly this band.

## 4. The same band on the menu legs

| leg | size | hotbar band distinct | control band distinct |
|---|---|---|---|
| `acc-intel-panorama-post-t20` | 1920x1080 | 446 | 2025 |
| `acc-intel-panorama-pre-t20` | 1920x1080 | 442 | 2656 |
| `acc-intel-panorama-pre-t380` | 1920x1080 | 422 | 2361 |
| `b08-menu` | 1920x1080 | 305 | 3797 |
| `acc-intel-panorama-post-t380` | 1920x1080 | 276 | 2013 |

14 of 198 menu legs are flat in that band; the rest are in the hundreds. So the band method is **not** wrong in
general - it reports content when content is there - and the claim cannot be withdrawn *because of the band*; it is
withdrawn because the world-leg distribution it rests on does not reproduce.

## Verdict

- **The claim "the hotbar band held 4 distinct colours in every leg but one" is refuted on the same file set**: 109 of
  188 world legs are flat (<= 8), i.e. 79 are not, with `font-f3` at 204, `blit-world-native` at 135 and
  `r163-bh09-scissor` at 100.
- **The claim "the 2D HUD has never been present in a presented world frame" is not established by this method.** The
  method cannot separate "no HUD" from "band over a smooth region" (85/188 controls are flat too), and positive
  controls exist (menu legs at 276-446 distinct in the same band; `font-f3` at 204).
- What the scan *does* support is a weaker statement: **in a majority of the recorded world dumps the bottom-centre
  band is flat**, which is consistent with the HUD being absent in those legs but is not proof, and the exceptions
  must be read individually before any claim about the HUD is made.

---

# Round 242 - code reading: which image the HUD's identity attaches, and which label is true

## 1. Routing table (`VulkanTerrainGpuBackend.java`)

The decision is three-way, not two-way (`:7788-7789`, `:7841`, `:7874`, `:7881`):

- `:7788` `boolean chainIdentity = this.targetRegistry.currentIdentity() != 0L;`
- `:7789` `GllessFrameTarget offTarget = (this.targetRegistry.currentIsMain() || windowIdentity) ? null : offTargetTarget();`

| identity at the bind | `offTarget` | pass attaches | framebuffer built |
|---|---|---|---|
| `== mainIdentity` (isMain true) | null | **`frameTarget.colorImage()`** | `ensureFramebuffer(renderPass)` (`:7881`) |
| `== 0` **and `windowIdentity` true** | null | **`frameTarget.colorImage()`** | `ensureFramebuffer(renderPass)` (`:7881`) |
| `== 0` and `windowIdentity` false | default pair | `offTarget.offTargetColorImage()` | `ensureOffTargetFramebuffer` (`:7874`) |
| any other non-zero non-main | chain pair | `offTarget.chainPairColorImage()` | `ensureChainPairFramebuffer` (`:7841`) |

So identity 0 is **not** unconditionally the default pair: the `windowIdentity` flag puts it on the frame image, which is
the path the window's own draws take (`CELTIUM_WINDOW_DRAW identity=0 image=frame`).

## 2. The two labels, and which one the attach follows

- `GllessFrameTarget.routeLabelForBinding(identity, isMain)` = `isMain ? "frame-image" : pairLabelForIdentity(identity)`.
- `GllessFrameTarget.pairLabelForIdentity(identity)` = `identity == 0 ? "default-pair" : "chain-pair"`.
- The **attach** follows the three-way rule in section 1: `isMain || windowIdentity` -> frame image; identity 0
  otherwise -> default pair; every other non-zero -> chain pair. So neither label alone is the truth: `pairLabelForIdentity`
  is the census's `pair=` column and misleads for identity 0 when the window flag is set, and `routeLabelForBinding`
  is right only because it already special-cases `isMain`. **The true rule is the three-way one at `:7789`.**

## 3. Which identity the HUD stage should carry, from the sources

- `EntityRenderer.java:1140` `this.mc.getFramebuffer().bindFramebuffer(true);` and `:1151`
  `this.mc.ingameGUI.renderGameOverlay(partialTicks);` - the vanilla order draws the HUD with the game's **main
  framebuffer** bound, i.e. identity == `mainIdentity`, which per section 1 attaches the **frame image** - the image
  `celtium$presentFrame` blits from (`:5088`). From the sources alone the HUD therefore *should* reach the presented
  image.
- The measurement (HUD family on a third identity) is not determined by this code: something between `:1140` and the
  HUD's draws rebinds a framebuffer (Forge's `renderGameOverlay` or the fixture), and the backend only records what it
  is told. **The code cannot answer which identity the HUD actually carries in that leg** - the bind census for that
  run is the answer, not this reading.

## 4. The one-line instrument

- `CELTIUM_DRAW_SITE` already prints, per drawn group, `target=`, `onMain=`, `passOpen=` at the moment of the draw
  (now bounded/filtered by `-Dceltium.vulkan.drawSiteLines` and `drawSiteMinFrame`). Running it with the frame filter
  set to the HUD's first frame answers "which identity, which pass" in one line per group, without a new instrument.
- `CELTIUM_WINDOW_DRAW identity=0 image=frame` names the identity-to-image mapping for the window bind, so the pair of
  lines (draw-site `target=` and window-draw `image=`) is what separates "the HUD's identity is not the frame image"
  from "it is the frame image and the draws are lost later".
