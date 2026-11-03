# 窓の固定費を窓あたり µs で割った: 3 µs で、残りは group loop と prefix（2026-11-04、leg `r44a-split`）

`window-census-2026-11-03.md` が「窓 1 枚の固定費 ~10 µs の**中身**は未帰属」と書いて残した問いに答えた。
答えは「固定費は 3 µs しかない」で、窓の 0.4 ms の大半は group loop と、prefix/publish/pass-end の 4 つである。
同時に、受理経路の遅い側（supply + `prepareImmediate`）が**費用ではない**ことも確定した。

## 条件

| | |
|---|---|
| Leg | `r44a-split`（`world`、`celtium-world-accept` 188 mods・`qmagnet`） |
| Renderer / fixture | `7033d622e8e6925b47b922884e8b87ee92c3c95ae9848a74e3cd179793125255` / `0e90c988c55f3313d65ccbc4b2cb8713f8f855caea2c803ed5a3902a6779bd65` |
| GPU | AMD Radeon RX 6400（RADV NAVI24）、`gpuRequirementMet=true` |
| Scene | `sceneCalibrationKeyMatches=true`、`presentedDistinctColours=76892`、1280x720、`guiScale3.txt` |
| Flags | `-Dceltium.vulkan.validation=false -Dceltium.vulkan.presentReadback=false -Dceltium.vulkan.presentedDumpEvery=0 -Dceltium.vulkan.passSplit=true -Dceltium.vulkan.windowSplit=true -Dceltium.vulkan.frameBudget=true` |
| Verdict | `PASSED`、`problems=[]`、`fixtureStatus=PASSED`、`exitCode=0` |
| 窓 | world 5,452 フレーム・125,793 窓 = **23.1/フレーム** |

**絶対 ms は KPI の値ではない。** 理由は 2 つで、計器税だけではない。(a) `passSplit` と `windowSplit` の
bracket 税（23 窓 × 17 対 ≈ 780 `nanoTime` = ~20 µs/フレーム）。(b) **KPI の warm/hold flag を渡していない**ので
world が tick し続け、`other` が tick を含む（KPI は `warmFromTick=400 warmToTick=600 holdUntilTick=620` で
tick を止めた区間を測る）。`frameIntervalWarmP50Ms=-1.0`（未測定）。この leg の `CELTIUM_FRAME_BUDGET` は
`frame=6.7/9.6/10.0`・`other=4.3/6.6/7.0` で、KPI leg の 3.03–3.24 ms とは比較できない。使うのは
**同じ leg の中の相対配分**だけ。

## 窓の固定費（`CELTIUM_WINDOW_SPLIT`、µs/窓）

| 区間 | world（124,880 窓） | menu（362 窓） |
|---|---|---|
| `route`（identity・target 解決） | 0 | 4 |
| `pair`（chain pair の ensure・hand-back・clear・framebuffer） | 0 | 6 |
| うち `publish`（色テクスチャ公開） | 0 | 22 |
| `begin`（begin-info） | 0 | 17 |
| `commands`（command buffer begin + stack push） | 1 | 126 |
| `acquire`（acquire barrier 記録） | 2 | 154 |
| `release`（release barrier 記録） | 0 | 3 |

world の合計は **~3 µs/窓**。23.1 窓/フレームで **~0.07 ms/フレーム**。つまり窓の固定費は主因ではない
（plan の「窓の固定費は主因ではない」はこれで裏づけられた）。

**menu の行を world の行として読まないこと。** menu の 362 窓はパイプラインと framebuffer の初回生成を
その 362 で割った値で、定常費用ではない（`commands=126 µs` はほぼ初回の `vkBeginCommandBuffer` 群）。

## 窓の 0.4 ms はどこか（`CELTIUM_FRAME_PASS_SPLIT`、p50 ms/フレーム）

| frame | world | gui | hand | flush | terrain | winRecord | wrecLoop | grpDraw | winHead | winBarrier | winPrefix | winPublish | winPassEnd | winBeginPass |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 1.4 | 1.0 | 0.4 | 0.1 | 0.5 | 0.1 | 0.4 | 0.2 | 0.1 | 0.1 | 0.0 | 0.0 | 0.0 | 0.0 | 0.0 |

`winRecord` 0.4 = `wrecLoop` 0.2（116 group × 1.7 µs、うち `grpDraw` 0.9 µs）+ 固定費 0.07 + `winHead` 0.1 +
`winPrefix`/`winPublish`/`winPassEnd`/`winBeginPass` の 4 つで ~0.13。**窓の残りは group loop と、この 4 つの
1.4 µs/窓ずつ**であって、target 解決でも pair でも公開でもない。

## 受理経路の遅い側は費用ではない（`offPreflight`）

新しい bracket `offPreflight`（mirror supply + `prepareImmediate`、`offMerge` と `offAppend` の間）は
**p50 0.0 / p95 0.1 / worst 93.2 ms**。定常状態では 0.05 ms/フレーム未満で、`batch` 0.6 ms の内訳ではない。
JFR の `submitGeometryBatch` 19.0% の「窓 flush でない半分」はここではなく、capture・key・merge・append と
**offer が引き起こす flush**（`flush` 0.5 ms は `batch` の中）にある。

## 主張しないこと

- 絶対 ms（計器税・warm window 無し）。
- validation（この leg は `validation=false`。絵の gate は PASSED だが画質の証拠にしない）。
- Intel（AMD のみ）。
- 「窓を減らせば勝てる」— 窓の固定費は 0.07 ms/フレームで、23 窓を 13 にしても 0.03 ms である。
