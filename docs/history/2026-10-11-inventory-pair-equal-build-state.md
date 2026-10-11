# The inventory scene's 48 %: it was the build state, and the B05 attribution does not survive

Owner: **fog-path** (task-41). Date: 2026-10-11, ~18:15 JST. GPU: AMD RX 6400 (RADV NAVI24).
Scene: `terrain` + `jeiScreen=true` + `keepInventoryScreen=true`, guiScale=3, 1280x720, candidate GL-less
(frozen jar `fog-path-mod8.jar` sha256 `609eb69a9064df057dda3c5bc9ea2146080bb843ed5d9cc0ec854544dd6bb1fd`)
vs upstream Celeritas `989ebf2e` (sha256 `d12425eaaf184e7925cd41d1deb4f9df69064115c8e78c8fcedfd6ecc63f93e7`),
same fixture `fog-path-fixture4.jar` sha256
`30d0ded469c529b282f65fe07ebff70dd4c47bad8dc3cb0334a0697a03e9d83e`.

## 1. Why the old pair was not a comparison

gui-stage's attribution used `cand3-inventory` vs `ref3-inventory`, both captured at tick 200. Their build
states, read from their own reports:

| leg | world frames | listed sections | geometry | rebuild queue |
|---|---|---|---|---|
| `cand3-inventory` | 54 | 12 | 8/14 MiB | P1=066 |
| `ref3-inventory` | 412 | 43 | 30/41 MiB | P1=130 |

A tick is not a build state (task-33). The re-take below uses
`captureCondition=sections captureSections=43 captureMinWorldFrames=412`; both legs landed at the target.

## 2. The pair at equal build state

| leg | condition | listed sections | world frames | rebuild queue | geometry |
|---|---|---|---|---|---|
| `t41-cand-inv` | `sections-target` | **43** | 521 | `P1=003 P2=009` | 16/18 MiB |
| `t41-ref-inv` | `sections-target` | **43** | 1384 | `P1=000 P2=002` | 17/38 MiB |

Region means, candidate/reference ratio and the `>8` fraction, beside the same numbers from the tick-200 pair
(gui-stage's table, `docs/history/2026-10-11-sky-ratio-three-scenes.md`):

| region | tick 200: ratio | tick 200: >8 | equal build: ratio | equal build: >8 |
|---|---|---|---|---|
| sky band (0,0,1280,120) | 0.857,0.842,0.825 | **83.4 %** | **0.992,0.991,0.991** | **1.28 %** |
| horizon (0,120,1280,300) | 0.991,0.968,0.961 | 53.0 % | 1.019,1.000,0.988 | 16.48 % |
| terrain centre (320,300,960,640) | 1.023,1.014,0.985 | 35.9 % | 1.029,1.016,0.987 | 27.74 % |
| chat block (0,320,660,620) | - | - | 1.039,1.029,1.007 | 17.30 % |
| hotbar strip (490,660,790,720) | 0.975,0.996,0.993 | 0.0 % | 1.000,1.000,1.000 | **0.00 %** |
| whole frame | 0.970,0.968,0.949 | **48.0 %** | 1.016,1.005,0.990 | **10.88 %** |

## 3. What survives

- **The sky's 17 % darkening does not.** It was 0.857/0.842/0.825 with 83.4 % of pixels over 8 at tick 200;
  at equal build state the same region is 0.992/0.991/0.991 with **1.28 %**. The correlation the B05
  attribution rested on - "2744 lit ITEM batches appear exactly when the sky goes dark" - was confounded by
  the build state, and the measurement that supported it does not survive the control.
- **The 48 % was mostly build state**: whole frame 48.0 % -> **10.88 %** over 8, with the hotbar strip at
  0.00 % and the sky at 1.28 %. What is left is the world behind the screen and the panel
  (terrain centre 27.7 %, chat block 17.3 %, horizon 16.5 %), which is gui-stage/texture-mirror territory,
  not the sky.
- **The lit items are still drawn.** The candidate's per-format histogram at equal build state is
  `id1=5680 id2=36055 id4=28927 id5=520 id6=10397 id7=763 id8=128 id9=30`; format 4 (ITEM) is
  **28,927 over 521 frames = 55.5 per frame**, against gui-stage's `cand3-inventory` **2,744 over 54 frames =
  50.8 per frame**. The screen still draws its lit item batches at the same rate, and the sky no longer
  darkens - so the batches are not the cause of the darkening.
- **B05's mechanism is still real in the code** - a group carrying the lit payload skips the fog block
  (`celtium$writeFogPush` is not called when `group.lightParams != null`, and `CELTIUM_FOG_CAPTURE` reports
  such groups with `lightParams=true`) - but it is no longer evidenced as the cause of this picture
  difference. The 0-vs-2744 control still shows that opening the screen adds lit ITEM batches; it no longer
  shows that they darken the sky.

**Method note.** `acceptedByFormat` and the per-format histogram are **cumulative over the run**, so two
legs with different frame counts cannot be compared by their totals: 2744 at 54 frames and 28,927 at 521
frames are the same rate. Compare per frame.

## 4. The entity pair

Not re-taken at the time of writing, and it needs one: `cand3-entity` had **8 listed sections after 30 world
frames** against `ref3-entity`'s **30 after 466** - the same mismatch as the inventory pair, so any region
table built from it is a build-state comparison. A re-take with
`captureCondition=sections captureSections=30 captureMinWorldFrames=466` is queued (`t41b-cand-entity`,
`t41b-ref-entity`); this record will be corrected with its numbers rather than left implying the pair was
comparable.
