# paired KPI、round 47: 1.386 → **1.261**（2026-11-04）

条件 C（同一 JAR・同一条件の paired 計測で Celeritas を上回る）の判定を取り直した。round 41 の **1.386** から
**1.261** へ下がったが、`verdict=SLOWER`・`goalCondition=NOT_MET` のまま。**比の改善のうち Celtium 自身は
5.4% で、残りは参照が遅くなった分**である。

## 条件

| | |
|---|---|
| Renderer | `0801cd8f928fa3600b9542af08b17ebb36454df6c721b3f4d494663896f87428` — committed tree（`4aab55595`）が byte 一致で再現 |
| Fixture | `0e90c988c55f3313d65ccbc4b2cb8713f8f855caea2c803ed5a3902a6779bd65` |
| Reference | Celeritas `01-celeritas-forge-mc12.2-2.5.0-dev.jar`、sha256 `567dcf8aadd6f4f4d5d594b02549c7997385b7dc8ade5e9722d1e9756056092c` |
| GPU | AMD Radeon RX 6400（RADV NAVI24）。Celeritas arm は同じ GPU を radeonsi で（`radeonsi, navi24, ACO, DRM 3.64`） |
| Scene | `scene=world`、save `qmagnet`、1280x720、`guiScale=3`、雲 0、world time 6000、fps 上限 260、vsync off — 6 leg すべて同一 `sceneCalibrationKey` |
| Window | `frameIntervalWarmP50Ms`、world ticks `[400,600)`、`worldTicksAtEnd=619`、samples 2,967–3,964 |
| Flags（共有） | `-Dceltium.vulkan.validation=false -Dceltium.vulkan.presentReadback=false -Dceltium.vulkan.presentedDumpEvery=0` |
| Run | `scripts/gv-kpi.sh r47kpi 240`、`CELTIUM_KPI_REPS=3`、`CELTIUM_LEG_SEED_OPTIONS=…/guiScale3.txt` |
| 環境 | `initialBusyCores=0.108`、`initialLoadAverage=0.606`（上限 2.0）、`loadGuardBypassed=false` |

## 3 対

| rep | Celtium p50 / p95 / p99 (ms) | Celeritas p50 / p95 / p99 (ms) | p50 比 |
|---|---|---|---|
| 1 | 3.030 / 5.932 / 7.674 | 2.403 / 4.526 / 5.664 | 1.261 |
| 2 | 2.989 / 5.954 / 8.044 | 2.349 / 4.406 / 5.209 | 1.272 |
| 3 | 2.739 / 5.248 / 7.001 | 2.303 / 4.483 / 6.088 | 1.189 |
| **median** | **2.989** | **2.349** | **1.261** |

`verdict=SLOWER`、`goalCondition=NOT_MET`、p95/p99 の median 比は 1.310/1.355。

## round 41 との差を分解する

| | round 41（JAR `5e24f574`） | round 47（JAR `0801cd8f`） | 変化 |
|---|---|---|---|
| Celtium p50 median | 3.160 | **2.989** | **−5.4%** |
| Celeritas p50 median | 2.272 | **2.349** | **+3.4%** |
| 比 | 1.386 | **1.261** | −9.0% |

**比の 9% のうち Celtium 自身は 5.4% で、3.4% は参照が遅くなった分**（同じ参照 JAR・同じ scene・同じ flags
なので、マシンか Mesa の状態が違う）。**「round 42–47 が 9% 速くした」とは言わない。**

Celtium の 5.4% を round 42–47 のどれに帰属するかは、この 1 対では分けない（JAR が 1 つしか違わない A/B が
無い）。本 round の upload 修正の見積りは 0.14 ms/フレーム＝2.989 の 4.7% で、**5.4% の大半を説明しうる**が、
それは見積りであって測定ではない。

## この比が意味すること（round 47 の内訳との突き合わせ）

同じ JAR の leg `r47a-shipping`（検証 OFF・readback ON）の `CELTIUM_FRAME_PASS_SPLIT` は
**`frame` p50 = 1.4 ms**（`world` 1.0 + `gui` 0.4 + `hand` 0.1）。KPI のフレーム間隔は 2.989 ms。
**差の ~1.6 ms はレンダーの bracket の外**（tick・chunk・display update・limiter）で、Celeritas も同じゲーム仕事を
払う。したがって **0.64 ms の差（2.989 − 2.349）はほぼ全部がレンダーの bracket の中**にある。

bracket の中で Celtium 固有なのは:

| 項目 | p50 ms | 誰の仕事か |
|---|---|---|
| `flush` | 0.4 | Celtium（窓 23.2/フレームの記録・barrier・提出） |
| 受理経路 | ~0.36（round 46、検証ON の leg） | Celtium（GL→Vulkan 変換） |
| `terrain` | 0.1 | Celtium |
| `gui` | 0.4 | ほぼゲーム（HUD） |
| `hand` | 0.1 | ゲーム |
| `world` の bracket 外 | ~0.5 | ゲームの `renderWorldPass` ＋ 受理経路 |

**Celtium 固有は ~0.86 ms、差は 0.64 ms。** つまり条件 C は「Celtium 固有の 0.86 ms を Celeritas の
~0.2 ms 以下まで落とす」ことでしか満たせない。ドロー（0.11 ms）と窓の固定費（0.14 ms）を全部消しても届かない。

## 主張しないこと

- 比の改善を round 42–47 の効果とは呼ばない（参照が 3.4% 遅くなっている）。
- 絶対 ms の機種間比較。`finalLoadAverage` は 2.08（ゲーム自身）。
- Intel arm（未実行）。
- 受理経路 0.36 ms は round 46 の**検証ON** の leg の値で、本 round の upload 修正後の値ではない。
