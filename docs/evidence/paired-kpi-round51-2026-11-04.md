# paired KPI、round 51: **1.215**（2026-11-04）

条件 C の判定を現行 JAR で取り直した。round 47 の **1.261** から **1.215** へ下がったが、
`verdict=SLOWER`・`goalCondition=NOT_MET` のまま。**比の改善 3.6% のうち Celtium 自身は 1.4%**で、
残りは参照が遅くなった分である。

## 条件

| | |
|---|---|
| Renderer | `024f5979709e96432bb6ed8fb9b8a900b5b8c71d45b976e43085f45d3535682f` — commit `7b3df87ad` の tree が再現 |
| Fixture | `0e90c988c55f3313d65ccbc4b2cb8713f8f855caea2c803ed5a3902a6779bd65` |
| Reference | Celeritas `01-celeritas-forge-mc12.2-2.5.0-dev.jar`、sha256 `567dcf8a…` |
| GPU | AMD Radeon RX 6400（RADV NAVI24）。Celeritas arm は同じ GPU を radeonsi で |
| Scene | `world`、save `qmagnet`、1280×720、`guiScale=3`、fps 上限 260、vsync off — 6 leg すべて同一 `sceneCalibrationKey` |
| 窓 | `frameIntervalWarmP50Ms`、world ticks `[400,600)`、`worldTicksAtEnd=619`、samples 3,056–3,986 |
| Flags（共有） | `-Dceltium.vulkan.validation=false -Dceltium.vulkan.presentReadback=false -Dceltium.vulkan.presentedDumpEvery=0` |
| Run | `scripts/gv-kpi.sh r51kpi 240`、`CELTIUM_KPI_REPS=3`、`CELTIUM_LEG_SEED_OPTIONS=…/guiScale3.txt` |
| 環境 | `initialBusyCores=0.088`、`externalCpuCores=0.129`（上限 0.5）、`loadGuardBypassed=false` |

## 3 対

| rep | Celtium p50 / p95 / p99 (ms) | Celeritas p50 / p95 / p99 (ms) | p50 比 |
|---|---|---|---|
| 1 | 2.946 / 5.755 / 7.244 | 2.283 / 4.538 / 5.987 | 1.290 |
| 2 | 2.879 / 5.617 / 6.936 | 2.441 / 4.689 / 5.693 | 1.179 |
| 3 | 2.949 / 5.807 / 7.779 | 2.427 / 4.596 / 5.520 | 1.215 |
| **median** | **2.946** | **2.427** | **1.215** |

p95/p99 の median 比は 1.263/1.218。**差は 0.52 ms。**

## round 47 との差

| | round 47（JAR `0801cd8f`） | round 51（JAR `024f5979`） | 変化 |
|---|---|---|---|
| Celtium p50 median | 2.989 | **2.946** | **−1.4%** |
| Celeritas p50 median | 2.349 | **2.427** | **+3.3%** |
| 比 | 1.261 | **1.215** | −3.6% |

round 48–50 の修正（upload digest・pair clear・flush bound）は **1.4%** で、round 47 の見積り 0.14 ms（4.7%）には
届いていない。**「round 48–50 が 3.6% 速くした」とは言わない。**

## 同じ round の warm 窓内訳との突き合わせ

計器付き leg `r51a-warm`（同じ JAR・同じ窓）は `frameIntervalWarmP50Ms=2.852` で、本 KPI の 2.946 と
**同じ分布のばらつきの中**にある（計器税は warm p50 では検出できない）。その leg の内訳:

- `loop` 2.28 = render 1.56 + **fmlTick 0.52** + display 0.10 + tick 0.08（最終 120 フレーム）
- `passSplit` frame 1.4 = world 1.0 + gui 0.4 + hand 0.1、`flush` 0.4（`winRecord` 0.3 = `wrecLoop` 0.2 + `winHead` 0.1）
- `gui` 0.4 のうち Celtium は `guiBatch` 0.1 だけ

**Celtium 自身は ~0.7 ms**（record 0.5 + accept ~0.1 + terrain 0.1）。差 0.52 ms を埋めるには
**Celtium 自身を ~0.2 ms まで落とす**必要があり、`wrecLoop` 0.2 のうち `grpDraw` 0.1 は 113 draw の
`vkCmdDrawIndexed` そのもの、`winHead` 0.1 は pair clear（`pairClearInPass` で 0 にできる）、
残りは 23 render pass の begin/end と遷移である。**micro-opt では届かない**という round 46 の結論は変わらない。

## 主張しないこと

- 比の改善を round 48–50 の効果とは呼ばない（参照が 3.3% 遅くなっている）。
- 絶対 ms の機種間比較。`finalLoadAverage` は 4.15（ゲーム自身）。
- Intel arm（未実行）。
- 計器付き leg の 2.852 と本 KPI の 2.946 の差を計器税と呼ばない（同じ分布のばらつき）。
