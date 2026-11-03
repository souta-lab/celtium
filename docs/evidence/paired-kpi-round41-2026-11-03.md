# paired KPI、round 41: 1.677 → **1.386**、そして初めて `NO_SPEED_VERDICT` でない判定（2026-11-03）

条件 C（「同一 JAR・同一条件の paired 計測で Celeritas を上回る」）の唯一の判定を取った。round 35 の
1.677 から **1.386** へ下がり、しかも今回は**外部負荷が上限を下回ったので verdict が実際に出た**
（round 35 は `externalCpuCores=1.073` で `NO_SPEED_VERDICT`、比だけが測定だった）。

## 条件

| | |
|---|---|
| Renderer | `5e24f5742c2cdcffb22eccafbe7ac2efff35d4bd43ae44e8e0d6bd8105f9f733` — **committed tree（`79ddf8aa7`）が byte 一致で再現**（jar を消して `:forge122:packageJar` を再実行して確認） |
| Fixture | `0e90c988c55f3313d65ccbc4b2cb8713f8f855caea2c803ed5a3902a6779bd65` |
| Reference | Celeritas `01-celeritas-forge-mc12.2-2.5.0-dev.jar`、sha256 `567dcf8aadd6f4f4d5d594b02549c7997385b7dc8ade5e9722d1e9756056092c` |
| GPU | AMD Radeon RX 6400（RADV NAVI24）、全 leg で `gpuRequirementMet=true` |
| Scene | `scene=world`、save `qmagnet`、1280x720、`guiScale=3`、雲 0、world time 6000、fps 上限 260、vsync off — 6 leg すべて同一 `sceneCalibrationKey` |
| Window | `frameIntervalWarmP50Ms`、world ticks `[400,600)`、`worldTicksAtEnd=619`、samples 2,676–4,168 |
| Flags（共有） | `-Dceltium.vulkan.validation=false -Dceltium.vulkan.presentReadback=false -Dceltium.vulkan.presentedDumpEvery=0` |
| Run | `scripts/gv-kpi.sh r41kpi 240`、`CELTIUM_KPI_REPS=3`、`CELTIUM_LEG_SEED_OPTIONS=…/guiScale3.txt` |
| 環境 | `initialBusyCores=0.122`、`externalCpuCores=0.077`（上限 0.5）、`guardBypassed=false`、`disturbedRepetitions=[]` |

## 3 対

| rep | Celtium p50 / p95 / p99 (ms) | Celeritas p50 / p95 / p99 (ms) | p50 比 |
|---|---|---|---|
| 1 | 3.239 / 6.665 / 8.318 | 2.337 / 4.406 / 5.274 | 1.386 |
| 2 | 3.030 / 6.163 / 7.609 | 2.234 / 4.311 / 4.990 | 1.356 |
| 3 | 3.160 / 6.216 / 7.961 | 2.272 / 4.267 / 5.080 | 1.390 |
| **median** | | | **1.386** |

`verdict=SLOWER`、`goalCondition=NOT_MET`、p95/p99 の median 比は 1.457/1.567。**損失は裾ではなく
分布全体**（round 35 と同じ形）だが、**p50 の比は 1.677 → 1.386（相対 17% 改善）**。

## 何が下げたか（主張の範囲）

round 35 の測定は JAR `bc425402…`、今回が `5e24f574…`。その間の変更は round 36–41 の micro-opt で、
計器が名指しした順に: 受理経路の `supply` 判定を 1 パスに（`b8aa6988f`）、merge 述語の global epoch を
テクスチャ serial へ（`01c0317af`）、受理経路が同じ名前を registry に 3 回聞いていたのを 1 回に
（`4ff3b5ccc`）、窓 head の重複 lookup（`79ddf8aa7`）。**個々は 1% 前後で「測れない」と書いてきたが、
積み上げは 17% だった。** 内訳の帰属はこの 1 対では分けない（JAR が違い、round 35 の絶対 ms は
`externalCpuCores=1.073` の下で取られている）。

**この 1 つの比を「round 41 のコードの効果」とは呼ばない。** round 41 のコードは窓 head の 2 lookup と
計器 1 行で、13 窓/フレーム × 2 lookup は 1% に満たない。比の改善は round 36–41 の合計である。

## 残る差の形

round 17 の分解では Celtium の鎖は ~1.1–1.5 ms、ゲーム側が 1.6 ms、Celeritas の p50 が 1.79–2.34 ms。
今回 Celtium 3.03–3.24 ms に対し Celeritas 2.23–2.34 ms なので、**差は 0.8–0.9 ms**。round 41 の窓 census
（[証拠](window-sources-2026-11-03.md)）は窓 23.2/フレームのうち **13.0 が GregTech の post chain**
（`Shaders.renderFullImageInFBO`）だと名指しした。Celeritas arm では同じ 13 回が安い GL 呼び出しなので、
この 13 窓の固定費（~0.22 ms/フレーム）は Celtium 側だけの差である。

## 主張しないこと

- 絶対 ms の比較（マシンは静かだったが、`finalLoadAverage=3.82` は最後のゲーム自身）。
- 「あと 0.39 で条件 C」という線形外挿。p99 比は 1.567 で、裾は p50 より悪い。
- Intel arm（未実行）。
