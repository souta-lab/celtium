# Acceptance notes

## Round 237 — HEI tooltip leg, suffixes `base` and `fixed`

Evidence read: `.celtium-work/hei-font-<suffix>.out` (harness summary), `.celtium-work/hei-font-<suffix>.json` (lock),
`.celtium-work/runtime/hei-font-<suffix>/` (report, dumps, log), script `.celtium-work/hei-fontcheck.sh`.

**Identity.** Hashes from the lock JSON (the `.out` does not carry them):
`base` celtium `28d454fd7d1ad2f2`, fixture `f19ce7941d2356f3`; `fixed` celtium `74a51308a91caa18`,
fixture `5a625e4199f95319`. Device, both legs: `CELTIUM_NATIVE_DEVICE name="AMD Radeon RX 6400 (RADV NAVI24)"`,
`gpuRequirementMet=true`. Flags, both legs: `glless=true`, `presentReadback=true`, `validation=true`,
`scene=terrain`, `keepGui=true`, `pinSections=64`, `minWorldFrames=5`, `jeiScreen=true`,
`keepInventoryScreen=true`, `jeiSearch=jeiFilterText=diamond`, `screenshotAtTick=200`,
`dumpPresentedImage=true`, `--require-gpu 'RX 6400'`, `--exclude-instance-mod 'celtium-*pre-stub-changes.jar'`.

### 1. Which gate the harness failed

Three different statements; only one of them is a failure.

- **Fixture did not pass** — `fixturePassed=false`, and the harness defines it as `fixtureStatus == "PASSED"`
  (`scripts/prism_validation.py:526`). This leg's `fixtureStatus` is **`UNCALIBRATED_PRESENTED_IMAGE`**, and the
  harness prints the reason itself: `note: the picture gate did not judge this scene (no reference at this
  pose/variant) - UNCALIBRATED_PRESENTED_IMAGE`.
- **Picture gate** — it did **not** judge: `presentedImageAssertionFailures=0`, `presentedImageAssertions` begins
  `UNCALIBRATED: this scene has no measured reference, so no non-background, band, black or panel floor…`,
  `floorsNotCalibrated` present, `sceneGuiStage=false`.
- **Validation messages present** — `validationMessageCount=2`, `validationClean=false`,
  `validationAllowedCount=0`. **This is what fails the leg**: `validationExitCode` is forced to 1 when messages exist
  (`prism_validation.py:528-529`), and the summary reads `validationExitCode=1` while `exitCode=0` (the game exited
  cleanly by itself).

So the leg failed on **validation messages**, not on the fixture and not on the picture.

### 2. The black presented frame

It is the **GL screenshot**, not the frame the gate scores — and the fixture says so in its own report:

- `pictureScreenshot_tick200=screenshots/celtium-at-tick-200.png`, `pictureScreenshotUsable_tick200=False`,
  `pictureScreenshotNote_tick200="black by construction under the GL-less stub: there is no GL default framebuffer to
  read…"`; measured: uniform black, 1 distinct colour. `celtium-validation.png` is the same shape
  (`pictureScreenshotUsable_world=False`).
- The **presented dump** the gate scores — `screenshots/celtium-presented-world-run-1cb9ad5a4e940-66d3c617-kept.png`,
  sha256 `db2cda728571b4d9…` — is not black: `presentedDistinctColours=2257`, `presentedBlackFraction=0.0`, and the
  image shows the world (terrain, sky, the player's arm).
- The present path ran: `CELTIUM_PRESENT hookCalls=3 attempts=2 presented=2 failures=0`; the group queue drained:
  `CELTIUM_GROUPS submitted=657 completed=656 discarded=0`.

**Verdict: instrument/scene state, not a renderer defect.** The black image is the GL-readback screenshot under
GL-less, which the fixture marks unusable; the separating measurement is the presented dump's own statistics
(`presentedDistinctColours`, `presentedBlackFraction`), not the screenshot.

**Second observation, recorded unresolved.** The presented world frame at `captureStartTick=217` shows **no HUD and no
JEI screen**, although `keepGui=true` and `keepInventoryScreen=true`, and the report says
`screenshotAtTickScreen=net.minecraft.client.gui.inventory.GuiContainerCreative` (the screen at tick 200),
`jeiScreenOpenedAtTick=34`, `jeiSearchStateAfter visible=13 filtered=13 filterText="diamond"`. The report carries no
screen-class-at-capture field for the world stage, so this leg cannot say whether the screen had been closed before
tick 217 or was open and not drawn. The single measurement that separates those two is a leg whose report names the
screen class at the **presented-dump** capture tick (the existing field names the screenshot tick). Until then it is
missing evidence, not a missing draw.

### 3. The two `VUID-vkCmdDraw-None-09600` messages vs the `VulkanPostChain` fix

- `base`: 2 messages. `fixed`: **2 messages — the count did not move** (same VUID, same image
  `0x370000000037`).
- The two messages are **opposite**, which is the detail worth keeping:
  1. expects `VK_IMAGE_LAYOUT_GENERAL`, current `VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL`;
  2. expects `VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL`, current `VK_IMAGE_LAYOUT_GENERAL`.
- So this leg's occurrence is **not covered by the post-chain fix as landed** (fixed jar `74a51308a91caa18`), and its
  shape differs from "one sampled-image descriptor declared GENERAL": two descriptors were written against the same
  image at different moments. It should be recorded as its own finding rather than folded into the post-chain one.
- Picture evidence is unchanged either way: `fixed` is also `UNCALIBRATED_PRESENTED_IMAGE` with 0 picture assertion
  failures.

Conventions honoured: leg suffix, jar/fixture hash, device line and flags accompany every claim; no past acceptance is
reused as a current fact; a missing counter (screen class at capture) is recorded as missing rather than read as a
missing draw.
