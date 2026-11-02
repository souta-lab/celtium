# 窓 1 枚の中身を割った（2026-11-03、round 17）

対象: AMD RX 6400 (RADV NAVI24)、`celtium-world-accept`（188 mods・`qmagnet`）、1280×720、
`CELTIUM_LEG_SEED_OPTIONS=guiScale3.txt`、240 s、`fixtureStatus=PASSED`。

## 何を決めたか

round 16 は「窓 1 枚 ≈ 17 µs、うち ~10 µs が無名」で終わった。本 round はその無名部分を
`winHead`（`recordImmediateOnlyPass` の先頭から `winPrefix` まで）と `winPublish`
（sampling 側の published-texture 遷移ループ）で割り、**仮説が外れたことを確定した**。

**棄却: 「窓の残りは published-texture 遷移ループ（38 名 × 35.8 窓）が支配している」。**
`winPublish` は validation 有効・無効の両 leg で **p50 0.0 ms/フレーム**。ループは 1 窓あたり
< 1.4 µs で、窓の固定費ではない。この仮説のために書いた skip キャッシュ
（`-Dceltium.vulkan.publishedTransitionCache`）は**計測後に revert した**（効果 0 に対して
layout の staleness リスクだけを足すため）。

## 実測（validation off、leg `r37b-split`、JAR `50eafd8e…`）

`CELTIUM_FRAME_BUDGET`（p50/p95/p99、ms）:

| frame | record | batch | present | other | gpuShare |
|---|---|---|---|---|---|
| 3.2 / 4.4 / 4.6 | 0.5 / 0.6 / 0.6 | 0.8 / 0.9 / 0.9 | 0.2 / 0.3 / 0.4 | 1.6 / 2.8 / 3.0 | 0.01 |

`CELTIUM_FRAME_PASS_SPLIT`（p50、ms/フレーム）:

| frame | world | gui | flush | winRecord | winHead | winPrefix | winPublish | winBeginPass | wrecLoop | winPassEnd | winBarrier | winSubmit |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 1.7 | 1.0 | 0.6 | 0.6 | 0.4 | 0.1 | 0.0 | 0.0 | 0.0 | 0.2 | 0.0 | 0.0 | 0.1 |

窓 1 枚（35.8/フレーム）に割ると: `winHead` ≈ 2.8 µs、`wrecLoop` ≈ 5.6 µs、`winPrefix` +
`winBeginPass` + `winPassEnd` ≈ 0 µs。**窓の固定費は合計 ~0.15 ms/フレームで、1.68× の主因ではない。**

validation 有効の 2 leg（`r37a-nocache` JAR `8f6d5c68…`、`r37c-gate` JAR `cf5544d7…`）でも同じ形:
`winRecord` 1.9–2.0、`winHead` 0.4、`winBeginPass` 0.2、`wrecLoop` 1.2、`winPassEnd` 0.1、
`winPublish` 0.0。validation は vkCmd を含む bracket（`wrecLoop` 6×、`winBeginPass`）を膨らませ、
純 Java の `winPublish` は膨らませない — 0.0 は「測れていない」ではなく「小さい」の意味だと確認できる。

## 残る差の形

p50 の frame 3.2 ms は `record` 0.5 + `batch` 0.8 + `present` 0.2 + `other` 1.6。
`record` は flush 窓では `batch` の内側なので、Celtium 自身の鎖は **~1.1–1.5 ms**、残り 1.6 ms は
ゲーム側の仕事。Celeritas の p50 は 1.79 ms（`docs/evidence/paired-kpi-world-accept-2026-11-03.md`）。

したがって条件 C を満たすには**鎖を ~0.2 ms まで落とす**必要があり、窓の固定費（0.15 ms）を
削っても届かない。次の単位は「記録そのものを client thread から出す」か「1 batch あたり
3.6 µs を ~1 µs に落とす」のどちらかで、前者は plan.md §12 が paired KPI で採否を決めると
していた案そのもの。

## 本 round のコード（計測で残った分だけ）

- `winHead` / `winPublish` bracket を追加（既定 OFF、`-Dceltium.vulkan.passSplit`）。
- `celtium$rebuildPublishedCache` が pair identity も一緒に解く（遷移ループの per-name
  `chainPairIdentityForImage` を 35.8 回/フレームから 1 回/キャッシュ再構築へ）。`offTargetTarget()`
  もループの外へ。
- `celtium$syncPublishedChainOutput` の stale-name 走査（全 published 名 × 2 の boxed lookup、
  off-target 窓ごと = 13/フレーム）に (identity, pair image, publication version) のキーを付け、
  変わっていなければ走査しない。`winHead` 0.1 ms/フレームの一部と、名前ごとの `Integer` 確保。

## 主張しないこと

- 速度の改善は主張しない。`r37c-gate` の定常状態（frame p50 6.6–7.7、record 2.8、batch 2.6、
  validation 有効）は `r37a-nocache`（6.4 / 2.8 / 2.5）と同じで、差は run 間のばらつきの範囲。
  `r37c-gate` の最終 60 フレーム窓（p95 157 ms）は teardown の stall で、定常状態ではない。
- `other` 1.6 ms の中身は依然として無名（tick・chunk build・entity render・GUI 本体）。
- Intel arm は未実行。
