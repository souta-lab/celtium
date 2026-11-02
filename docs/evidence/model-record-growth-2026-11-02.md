# model replay の per-frame segment は pack の需要で育つ（2026-11-02）

renderer `4130f4c8f6bfd637e80f8d5b4d51e645963a1f51ead885b3371846f26ac9479b`（rc.26、`main` dc3e3d8db + 本差分）、
fixture `1494d31751249f27e9dc301344de813ff86bcae69e8f161595122e879df2bddf`。両 leg とも AMD Radeon RX 6400
(RADV NAVI24)・mode `plain`・X11・validation 既定 ON・220 s・Supersymmetry instance・
`CELTIUM_LEG_COPY_PACK_DIRS=resources`・`menuHoldTicks=60`・同じ jar と同じ fixture。

## 観測: 固定 cap は「その leg の残り全部」を安くない経路へ送る

r15b（cap 32768）は frame 47..60 で 26,733 を書き切り、frame 109..120 で 37,380 を要求して 4,612 を断った
（`declinedFull=260903`、`framesOverCapacity=56`）。r15c（同一 jar、cap 4096）は 72.3 % を断った。
断られた replay は record 256 B + command 20 B の代わりに `ImmediateGroup` の確保・state コピー・bind・個別 draw を払う。
**cap が足りない限り、この fallback は leg の終わりまで毎 frame 続く**。cap は測定値（26,733 の plateau、37,380 の peak）で
決めたが、次の pack では同じ推測が外れる。r15b の末尾 12 frame がその実例。

## 変更

1. `ModelRecordCapacity.grownFor(current, demand)`: 前 frame が要求した replay 数（断った分を含む）が現在の
   segment を超えたら、`demand + 25 %` を 1024 の倍数に切り上げた値を返す（`MAX=262144` で clamp）。
   現在値で足りていれば 0。25 % は r15b 自身の plateau→peak の比（26,733→37,380）。
2. `VulkanModelInstances.beginFrame`: frame 境界でこの値に **segment を再確保**する。record/command buffer と
   descriptor pool/set を作り直し、旧 buffer・旧 set は frame in flight が読み終わるまで `retiredSegments` で保持して
   から破棄（mesh pool と同じ規則）。`segment * segmentRecords + used` の index は instance field になった。
   失敗時は現在の容量のまま fallback を続け、`CELTIUM_MODEL_RECORDS_GROW_FAILED` を 1 行残す。
3. `-Dceltium.vulkan.modelSegmentRecords=N` は **初期値**になった。`describe()` は `perFrameRecords=`（現在値）と
   `recordGrowths=` を出し、`-Dceltium.debug.modelRecords=N` は `CELTIUM_MODEL_RECORDS_GROW frame= from= to= demand=`
   を成長ごとに 1 行（最大 12 行）出す。

## 陽性対照 r16b: 4096 から開始して 5 回育つ

`-Dceltium.vulkan.modelSegmentRecords=4096 -Dceltium.debug.modelRecords=400`。成長と、その原因になった frame の要求:

| frame | attempts | declinedFull | usedAtEnd | 成長 |
|---|---|---|---|---|
| 30 | 7,168 | 3,072 | 4,096/4,096 | 4,096 → 9,216 |
| 45 | 10,752 | 1,536 | — | 9,216 → 14,336 |
| 46 | 16,128 | 1,792 | — | 14,336 → 20,480 |
| 53 | 25,088 | 4,608 | — | 20,480 → 31,744 |
| 55 | 32,109 | 365 | — | 31,744 → 40,960 |

最終 counters: `appended=2498379 runs=98 declinedFull=11373 declinedMissing=0 pipelines=1
perFrameRecords=40960 recordGrowths=5 worstFrameAttempts=39424 framesOverCapacity=5`。

**断りの会計が一致する**: 上の 5 frame の溢れ（3,072 + 1,536 + 1,792 + 4,608 + 365）は 11,373 で、
`declinedFull=11373` と 1 件の差もない。`framesOverCapacity=5` も同じ 5 frame。つまり断りは成長前の 5 frame に閉じ、
frame 56 以降（`worstFrameAttempts=39424` を含む）は 40,960 の segment に収まって 0 件。

同じ leg の replay 要求は 2,498,379 + 11,373 = 2,509,752 件・112 frame。**固定 4,096 のままなら**
`Σ max(0, attempts_i − 4096) ≥ 2,509,752 − 112×4096 = 2,051,000` 件（≥81.7 %）が fallback だった。
成長ありでは 11,373 件（0.45 %）。この比較は cross-leg の絵の差ではなく、同じ leg の要求数だけから出る。

## 既定 32768 の leg（r16a）

`appended=344064 declinedFull=0 recordGrowths=0 worstFrameAttempts=8960 frameId=68`。この leg の最重 frame は
8,960 で既定に届かず、成長は働いていない。**したがって「既定 32768 が steady state に足りる」とは言えない**
（r15b の 37,380 は依然として観測された最重で、それを扱うのがこの機構）。r16a は「既定が軽い scene で
何も壊さない」ことだけを言う。

## 受入（この leg で言えること）

- 両 leg: `accepted=true`、`verdict=PASSED`、`problems=[]`、`validationMessageCount=0`、
  `validationAllowedCount=0`、`validationClean=true`、`gpuRequirementMet=true`、`fixtureUncalibrated=false`。
- 絵: r16b world `mean=101.79 / 非黒 0.99935 / 黒 0.000646`、r16a `mean=102.34 / 0.99933 / 0.000663`、
  どちらも calibrated gate PASSED（`presentedImageVerdict=PASSED`）。40960 records の segment でも model は同じ絵で出る。
- 成長 5 回の descriptor pool/set 差し替えで validation メッセージ 0、device lost 0、`GROW_FAILED` 0。

## 言えないこと（次の判別）

- **FPS の勝敗ではない**。Celeritas arm の無い leg であり、r16a と r16b は同じ pose でも **frame あたりの可視 block
  entity 数が 4.4 倍違う**（r16a 8,960 / r16b 39,424）。chunk mesh の構築が frame 予算で進むため、leg の frame 数
  （68 vs 112）が内容を変える。cross-leg の `windowRecordMillis`・`actualFlushMillis` を改善量として使わない。
- r16a は既定 32768 を試していない（8,960 では届かない）。steady state の要求数は未測定のまま。
- Intel 未実行。menu/GUI stage はこの leg の対象外（`pictureStage=world`）。
- `retiredSegments` の解放は frame 境界の age 判定で、長時間成長が続く scene での lived 上限は測っていない。
