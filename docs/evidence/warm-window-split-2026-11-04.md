# warm 窓（KPI と同じ worldTicks 400–600）の内訳（2026-11-04、round 51）

KPI が採点する窓を初めて計器付きで測った。round 50 までの内訳は tick 357 で終わる leg のもので、
**KPI の 2.989 ms はその窓ではなかった**。本 round は `holdUntilTick=620` を付けた 1 leg で、
KPI と同じ `frameIntervalWarmP50Ms` と、その内訳を同じ run から取った。

## 条件

| | |
|---|---|
| Leg | `r51a-warm`（frameStats + passSplit + frameBudget） |
| Renderer / fixture | `024f5979709e96432bb6ed8fb9b8a900b5b8c71d45b976e43085f45d3535682f` / `0e90c988c55f3313d65ccbc4b2cb8713f8f855caea2c803ed5a3902a6779bd65` |
| GPU | AMD Radeon RX 6400（RADV NAVI24）、`gpuRequirementMet=true` |
| Scene | `world`（`celtium-world-accept`、188 mods、`qmagnet`）、1280×720、`guiScale3.txt`、240 s、`sceneCalibrationKeyMatches=true` |
| 窓 | `warmFromTick=400 warmToTick=600 holdUntilTick=620`、`worldTicksAtEnd=619`、samples **3195** |
| Flags | `-Dceltium.vulkan.validation=false -Dceltium.vulkan.presentReadback=false -Dceltium.vulkan.presentedDumpEvery=0` |
| Verdict | `PASSED`、`problems=[]`、`fixtureStatus=PASSED`、`presentedImageVerdict=PASSED`、validation 0 |

## 1. KPI と同じ数字: warm p50 **2.852 ms**

`frameIntervalWarmP50Ms=2.8519`、p95 5.648、p99 7.024。round 47 の KPI（JAR `0801cd8f`、計器なし）は
**2.989**、Celeritas は **2.349**。**現行 JAR は round 47 より 4.6% 速い**が、比はまだ 1.21。

窓の中は 2 種類のフレームに分かれる:

| | フレーム数 | p50 ms |
|---|---|---|
| tick を含むフレーム | 200 | **5.939** |
| tick を含まないフレーム | 2995 | （p95 3.699 / p99 5.190） |
| tick 本体（`warmTickP50Ms`） | 200 | **1.799** |

p50 は tick を含まないフレーム。**tick フレームは 6.3% しかないが 5.9 ms** で、tick 1.8 のほかに
~1.6 ms を同じフレームが払っている（chunk upload / その後の frame）。

## 2. ループは µs 分解できるようになった: `fmlTick` が 0.52 ms

`summaryDelta` は ms 整数で割っていたので `tickMean=1` が 1.0 とも 1.9 とも読めなかった。ns で差分を取るよう
直した後の最終窓（120 フレーム、tick ~615–619）:

```
loopMean=2.28 tickMean=1.15 displayMean=0.10 renderMean=1.56 fmlMean=0.52 schedMean=0.00 syncMean=0.00
```

`tickMean` は tick あたり（1 tick ≈ 0.07 loop）なので loop への寄与は ~0.08。**loop 2.28 = render 1.56 +
fmlTick 0.52 + display 0.10 + tick 0.08 + 端数**。`fmlTick` は Forge の render tick フック（fixture と
Journeymap を含む）で、**Celeritas も同じだけ払う**。`display` は本 leg では frameBudget の timestamp readback
を含む（計器税）。

## 3. passSplit（512 フレーム窓、p50 ms）

```
frame 1.4 = world 1.0 + gui 0.4 + hand 0.1
world 1.0 = flush 0.4 + terrain 0.1 + （ゲームの renderWorldPass 仕事 ~0.5）
flush 0.4 = winRecord 0.3 + winHead 0.1 + winBarrier 0.0
winRecord 0.3 = wrecLoop 0.2 + winHead 0.1
```

## 4. `gui` 0.4 のうち Celtium は 0.1 だけ（round 50 の前提を覆す）

新設の `guiBatch`（受理経路）と `guiFlush`（窓の記録）を `gui` の内側で測った:

| bracket | p50 ms | 誰の仕事か |
|---|---|---|
| `gui` | 0.4 | HUD 全体 |
| `guiBatch` | **0.1** | Celtium の受理経路（`guiFlush` を含む） |
| `guiFlush` | **0.1** | Celtium の窓の記録 |

**Celtium の HUD 分は 0.1 ms で、残り ~0.3 ms はゲーム自身の HUD 描画。** round 50 の handoff は
「gui 0.4 が Celtium 固有の最大単一項目」としたが、それは誤り。**HUD を最適化しても 0.1 ms しか取れない。**

## 5. 計器の修正 2 件

- **`CELTIUM_FRAME_BUDGET_SUMMARY` が teardown で `frames=0` を出す件**: 原因は「world teardown が
  計測 world の frame を 1 枚も描く前に 2 回走る」こと。fixture の `printCounterSummaryNow`（`fixture end`）からも
  `printSummary()` を呼ぶようにした。本 leg の最後の行は **`frames=8877 window=512 frame=2.5 record=0.5
  batch=0.4 present=0.0 other=1.5 gpuShare=0.03`**。
- **`CELTIUM_PRESENT_INTERVAL_SUMMARY` の `intervalMs[0.0/0.0/0.0]`**: present ring のカーソルは
  `presentIntervals` なのに、`window()`/`share()` が frame ring の `written` で読んでいた。カーソルを引数に
  した（`FrameBudgetTimingsTest.aRingIsReadThroughItsOwnCursor`）。

## 6. 主張しないこと

- **KPI の比ではない**: 本 leg は計器 ON（`display` に readback 税）、round 47 の KPI は計器 OFF。同じ JAR の
  計器なしの値は次の KPI run で取る。
- **Celeritas の内訳は無い**: 参照 arm に我々の計器は無い。`fmlTick` が同じ 0.52 であることは仮定であって測定ではない。
- Intel arm、`pairClearInPass` の既定、warm 窓の JIT 収束（loop は 5.47 → 2.28 と最後まで下がり続けた）。
