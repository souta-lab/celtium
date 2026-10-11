# task-38 — why the sky is 17 % dark with a screen open and pixel-identical without one

Date: 2026-10-11. Scene: `terrain`, pose `x=0.5 y=12.0 z=0.5 yaw=180 pitch=20`, 1280x720, tick 200.
Pairs already in the workspace (no new run was needed for the result): candidate = the GL-less presented world dump,
reference = the upstream Celeritas `989ebf2e` GL screenshot, **same fixture** (`ref-fixture-acfb62b1.jar`) for both legs
of each pair, same scene flags, same capture tick.

| leg | run | jar / fixture |
|---|---|---|
| A candidate, no screen | `cand3-terrain` | GL-less, presented world dump |
| A reference, no screen | `ref3-terrain` | upstream Celeritas GL, `celtium-validation.png` |
| B candidate, inventory open | `cand3-inventory` | GL-less, presented world dump |
| B reference, inventory open | `ref3-inventory` | upstream Celeritas GL, `celtium-validation.png` |

## 1. The table

Mean colour per region and the candidate/reference ratio; `>8` is the fraction of pixels differing by more than 8.

| region | A no screen: cand | A ref | A ratio | A >8 | B inventory: cand | B ref | B ratio | B >8 |
|---|---|---|---|---|---|---|---|---|
| sky band (0,0,1280,120) | 158.1,192.9,255.0 | 158.1,192.9,255.0 | **1.000,1.000,1.000** | **0.0 %** | 53.7,58.4,67.7 | 62.6,69.4,82.1 | **0.857,0.842,0.825** | 83.4 % |
| horizon (0,120,1280,300) | 150.1,152.8,181.0 | 148.9,156.0,178.7 | 1.008,0.979,1.012 | 34.9 % | 87.7,84.7,85.2 | 88.5,87.5,88.7 | 0.991,0.968,0.961 | 53.0 % |
| terrain centre (320,300,960,640) | 103.1,66.4,60.7 | 103.8,66.8,61.2 | 0.994,0.993,0.993 | 2.7 % | 123.7,118.5,111.9 | 120.9,116.9,113.5 | 1.023,1.014,0.985 | 35.9 % |
| hotbar strip (490,660,790,720) | 103.6,23.2,22.2 | 103.6,23.2,22.2 | 1.000,1.000,1.000 | 0.0 % | 24.9,22.7,20.4 | 25.6,22.8,20.6 | 0.975,0.996,0.993 | 0.0 % |
| whole frame | 123.7,105.2,119.7 | 123.8,106.3,119.5 | 0.999,0.990,1.002 | 10.4 % | 70.2,67.4,67.2 | 72.4,69.6,70.8 | 0.970,0.968,0.949 | 48.0 % |

The sky band with **no screen is pixel-identical** (0 of 153,600 pixels differ by more than 8; the means agree to
0.1/255), and with the inventory open it is 0.83-0.86 with 83.4 % of pixels differing. Nothing about the sky's own
geometry, projection or colour changes between the two legs - only whether a screen is open.

## 2. The batch counts that go with it

Per-format accepted batches, from each run's own report (`acceptedByFormat`, and the per-format histogram line):

| leg | format 4 (ITEM - lit item batches) | format 2 | format 6 | format 7 | format 1 |
|---|---|---|---|---|---|
| A no screen | **0** | 14048 | 308 | 225 | 2 |
| B inventory open | **2744** | 30530 | 1064 | 427 | 542 |

The ITEM family goes from **0 to 2744** exactly when the sky goes from pixel-identical to 17 % dark.

## 3. What this rules out, and what it names

- **Not the projection or the ortho switch.** The sky band is at the right place with the right extent in B; only its
  brightness differs, and A proves the same sky at the same pose and tick is pixel-exact. A projection error moves or
  clips geometry; this does not move anything.
- **Not the screen's dim overlay.** `GuiScreen.drawWorldBackground` dims everything the world shows through, so an
  overlay whose alpha/blend was wrong would scale *every* world pixel under it by the same factor. Measured in the same
  B frame, under the same overlay, the **chat block is 1.026/1.053/1.037 (slightly brighter)** while the **sky band is
  0.857/0.842/0.825**. One overlay cannot darken one region by 15 % and leave another 3 % brighter.
- **Not a pure alpha scale on the sky either**: the three channel ratios are 0.857/0.842/0.825, i.e. the blue is
  darkened most, which a multiply would not do.
- **Named: task-29's B05** - a lit group's payload overwriting the fog bytes for later groups in the same flush. The
  sky is drawn by the world pass's immediate family (formats 6 and 7), and in scene B it is flushed in a frame that also
  carries 2744 lit ITEM batches, which is exactly the ordering B05 describes; in scene A there are **zero** lit ITEM
  batches and the sky is exact. The correlation is perfect and the two alternatives are measured against.

## 4. The third scene the task asked for: the fixture cannot separate it

Scene 3 was to be "JEI screen" against scene 2's "vanilla inventory". **In this cohort those are the same scene.**
`-Dceltium.validation.jeiScreen=true` opens `net.minecraft.client.gui.inventory.GuiInventory`
(`ValidationClient.java:3032`), and no JEI jar is loaded: the run's `mods/` directory and its `latest.log` contain no
JEI entry. So the fixture **cannot** separate "JEI specifically" from "a screen is open" here, and the honest reading of
scene B is "the vanilla inventory screen", not "JEI".

The runs that did load JEI (`mod3-glless-jei`, `mod5-bridge-jei`, `mod6-bridge-hudmods`, 2026-10-11 01:10-01:24, JEI
`e592b0019aa0cd32…`) cannot supply the ratio either: they are a different fixture and jar era, they have no upstream
Celeritas reference leg, and all three verdicts are `FAILED_PRESENTED_IMAGE`. What would separate it: a JEI-loaded
candidate leg **and** a JEI-loaded upstream reference leg at the same fixture, jar and tick - the recipe is in
`.celtium-work/acceptance/mod3-legs.sh`.

## 5. Crops

`.celtium-work/sky-ratio/crops/`, candidate on top and reference below in each file:
`A-no-screen-{sky-band,horizon,terrain-centre,hotbar-strip}.png`, `B-inventory-{...}.png`, and
`{A-no-screen,B-inventory}-whole.png` (whole frame, 50 % scale, stacked).
