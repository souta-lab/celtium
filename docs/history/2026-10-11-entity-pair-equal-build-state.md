# The entity pair at equal build state: 0.68 % of the frame, and one real item-entity difference

Owner: **fog-path** (task-49). Date: 2026-10-11, ~18:50 JST. GPU: AMD RX 6400 (RADV NAVI24).
Scene: `terrain` + `keepEntities=true` + `captureEntities=true`, 1280x720. Candidate GL-less (frozen jar
`fog-path-mod8.jar` sha256 `609eb69a9064df057dda3c5bc9ea2146080bb843ed5d9cc0ec854544dd6bb1fd`) vs upstream
Celeritas `989ebf2e` (sha256 `d12425eaaf184e7925cd41d1deb4f9df69064115c8e78c8fcedfd6ecc63f93e7`), same
fixture `fog-path-fixture4.jar` sha256
`30d0ded469c529b282f65fe07ebff70dd4c47bad8dc3cb0334a0697a03e9d83e`.

## 1. The two conditions, read before any region table

`captureCondition=sections captureSections=64 captureMinWorldFrames=466` for both legs (the reference first).

| leg | condition fired | listed sections | geometry | world frames | rebuild queue | capture tick |
|---|---|---|---|---|---|---|
| `t49-ref-entity` (reference) | `sections-target` | **64** | 24/34 MiB | 1788 | `P1=013 P2=000` | 1407 |
| `t41b-cand-entity` (candidate) | `sections-target` | **64** | 24/34 MiB | 466 | `P1=063 P2=000` | 2175 |

The listed-section count and the geometry are **equal** (64 sections, 24/34 MiB) - the quantity the condition
controls. The world-frame counts and the queues are **not**: the candidate reached the same listed geometry in
466 frames and was still carrying 63 queued rebuilds, the reference took 1788 frames and was nearly drained
(13). So this pair is matched on listed geometry and not on queue state, and the capture ticks differ (2175 vs
1407), which is the price of the build-state condition: animated content cannot be compared across it.

## 2. Regions (candidate/reference ratio, `>8` fraction)

| region | ratio | >8 |
|---|---|---|
| sky band (0,0,1280,120) | 1.000,1.000,1.000 | **0.00 %** |
| horizon (0,120,1280,300) | 1.001,1.001,1.004 | 1.24 % |
| terrain centre (320,300,960,640) | 0.998,0.993,1.014 | 1.52 % |
| chat block (0,320,660,620) | 1.000,1.001,1.019 | 0.01 % |
| hotbar strip (490,660,790,720) | 1.000,1.000,1.032 | 0.00 % |
| entity cone (300,380,1000,680) | 0.997,0.991,1.010 | 1.38 % |
| whole frame | 1.000,0.999,1.006 | **0.68 %** |

## 3. Per-family crops (the earlier entity work's boxes, `entity-m/crop.py`)

| family | candidate mean | reference mean | ratio | >8 |
|---|---|---|---|---|
| mobs (430,110,830,320) | 132.0,139.9,156.1 | 131.6,139.3,154.9 | 1.003,1.004,1.007 | 3.85 % |
| creeper (470,130,600,320) | 120.9,128.4,133.3 | 120.6,127.7,132.2 | 1.002,1.005,1.008 | 4.66 % |
| zombie (595,150,700,300) | 122.1,133.4,146.8 | 121.6,132.1,144.7 | 1.004,1.010,1.014 | 5.50 % |
| spider (700,200,830,310) | 105.0,91.4,88.9 | 103.7,90.8,87.7 | 1.013,1.006,1.014 | 6.78 % |
| blockentities (520,420,780,600) | 101.9,64.1,60.1 | 101.9,64.0,58.7 | 1.000,1.001,1.025 | **0.00 %** |
| chest (525,470,640,600) | 102.2,56.3,51.8 | 102.2,56.3,50.7 | 1.000,1.000,1.020 | **0.00 %** |
| sign (655,455,760,520) | 103.0,63.8,59.4 | 103.0,63.8,58.1 | 1.000,1.000,1.022 | **0.00 %** |
| stone (540,270,730,490) | 102.7,91.2,90.0 | 102.6,90.1,86.0 | 1.001,1.011,1.046 | 3.07 % |
| **item (820,380,880,440)** | **88.2,28.8,29.4** | **101.8,58.4,52.6** | **0.867,0.493,0.559** | **71.31 %** |

## 4. The item region is a real difference, not this pair's tick mismatch

The dropped-item entity is drawn as a large dark sprite by the candidate where the reference draws a slender
item at the same position (crop `.celtium-work/fogpath-jar/crops/t49-item-region.png`; the same shape is
already visible in the earlier `.celtium-work/entity-m/pair-item.png`). It is **not** an artefact of the
build-state pair's different capture ticks: on the old tick-matched pair (`cand3-entity` tick 202 vs
`ref3-entity` tick 200) the same region is 0.816,0.581,0.617 with **58.42 %** of its pixels over 8. So it
reproduces at matched ticks and at matched build state, and it belongs to the entity-models owner
(task-7/N02): the item entity's model, transform or texture. Everything else in the scene matches to
0.0-6.8 % per family.

## 5. Per-frame format rates

`acceptedByFormat` and the per-format histogram are cumulative over a run, so they are quoted per frame. The
candidate (`466` world frames):

| format | accepted over the run | per frame |
|---|---|---|
| 1 POSITION_COLOR | 2 (of 21 offered) | 0.004 |
| 2 POSITION_TEX_COLOR | 12209 | 26.20 |
| 4 ITEM | 428 | 0.92 |
| 5 particle | 431 | 0.92 |
| 6 POSITION_TEX | 1004 | 2.15 |
| 7 POSITION | 550 (of 551) | 1.18 |
| 8 POSITION_TEX_COLOR_NORMAL | 128 | 0.27 |
| 9 POSITION_TEX_NORMAL | 228 | 0.49 |

The upstream reference reports no per-format histogram at all - it has no celtium backend to count with - so
the format rows exist for the candidate only; that is a limit of the reference, not a missing measurement.

## 6. Verdict

The pair is **comparable on the build state** (64 listed sections and 24/34 MiB in both legs) and the picture
matches to **0.68 % of the frame**, with the sky band at 0.00 %. The one entity-specific difference is the
dropped item, which reproduces independently of this pair's tick and build-state choices. A pair matched on
*queue* state as well (both `P1`/`P2` zero for N frames) would remove the last caveat, and would need a
condition that drains rather than targets; the entity scene's queue did drain to `P1=013` on the reference
within 1788 frames, so it is reachable.
