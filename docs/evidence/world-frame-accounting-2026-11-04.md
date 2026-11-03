# 出荷構成のフレーム会計: 219 offer/フレーム・113.6 group・22.1 flush・23.1 window（2026-11-04、round 48）

条件 C（同一 JAR・同一条件の paired 計測で Celeritas を上回る）は round 47 の KPI で **1.261（SLOWER）**。
round 42–47 の内訳はすべて `-Dceltium.vulkan.validation=true` の leg で取られていたため、round 47 で
「検証レイヤを測っていた」と判明した。本 round は **出荷構成（validation off）で世界 scene の内訳を初めて
1 leg で取り**、その数字から次の一手を決められる形にした。結論は「Celtium 自身の 1.36 ms は 219 offer +
23 window + present に薄く広がっていて、単一項目は 0.53 ms を超えない」＝**micro-opt では 0.6 ms の差は
詰まらない**、である。あわせて計器の欠陥を 2 つ見つけて 1 つ直した。

## 1. 測定条件

| | |
|---|---|
| leg | `r48a-split` — `world`（`celtium-world-accept`、188 mods、`qmagnet`）、AMD RX 6400 (RADV NAVI24) |
| JAR / fixture | `0801cd8f…` / `0e90c988…`（round 47 の committed pair） |
| flags | `-Dceltium.vulkan.validation=false -Dceltium.vulkan.presentReadback=false -Dceltium.vulkan.presentedDumpEvery=0` ＋ `passSplit=true batchTimings=true windowSplit=true packetTimings=true` |
| 判定 | `PASSED`、`problems=[]`、`fixtureStatus=PASSED`、`sceneCalibrationKeyMatches=true`、`worldTicksAtEnd=619` |

**この leg の ms は計器税込み**（`passSplit` は bracket ごとに `indexOf` の線形走査をする）。したがって
下の「/フレーム」は**帰属**の数字であって性能の主張ではない。税抜きの性能は §4 の A/B で取る。

## 2. 世界 scene のカウンタ（teardown 行、`worldFrames=5650`）

| 量 | 実測 | /フレーム |
|---|---|---|
| `immediateTaken`（受理 batch） | 1,238,431 | **219.2** |
| `immediateGroupsDrawn`（描いた group） | 642,082 | **113.6** |
| `actualFlushes`（実際の flush） | 124,665 | **22.1** |
| `windowRecords` / `immediateWindows` | 130,389 | **23.1** |
| `fastMergedBatches` | 596,713 | 105.6（48.2%） |
| `prepareImmediateCalls`（slow path） | 641,718 | 113.6 |
| `fontRunsStarted` | 526,926 | **93.3** |
| `flushBeforeGlDraw` / `...WithGroups` | 315,549 / 124,665 | 55.9 / 22.1 |
| `queueSubmitCalls` | 13,326 | 2.36（batched submit が効いている） |
| `swapPointCalls` / `swapPointMillis` | 5,968 / 1,115 | 1.06 / **0.197 ms** |
| `immediateSubmitAvgNs` | **3,494 ns/offer** | **0.766 ms** |

**受理経路は 0.766 ms/フレーム**（219 × 3.49 µs）。内訳の named 部分は
`append` 0.116 ＋ `prepare` 0.090 ＋ `captureState` 0.020 ＋ `stateKey` 0.015 ＋ `programQuery` 0.004 ＋
`matrixCopy` 0.004 ＋ `groupCreate` 0.010 ＝ **0.259 ms**。**残り 0.5 ms は名前の無い本体**で、1 offer あたり
2.3 µs。`groupDrawMillis=1,327`（0.235 ms、113.6 group × 2.07 µs）は flush の中、`present` 0.197 ms。

合計 **Celtium 自身 ≈ 1.36 ms/フレーム**（受理 0.77 ＋ group draw 0.24 ＋ window 0.30 ＋ present 0.20 − 重複）。
round 47 のレンダー bracket 1.4 ms と一致する。

### 何が「大きい」のか

| 項目 | ms/フレーム | 単独で消せる上限 |
|---|---|---|
| 受理経路（219 offer） | 0.77 | 0.77 |
| window（23.1 枚） | 0.30 | 0.30 |
| group draw（113.6 group） | 0.24 | 0.24 |
| present | 0.20 | 0.20 |

**どれか 1 つを消しても 0.6 ms には届かない。** round 47 が「ドロー 0.11 と窓の固定費 0.14 を全部消しても
届かない」と書いたのと同じ形で、今 round は「受理経路 0.77 を全部消しても届かない」を足した。
`submitBuckets=[<5us:1186623 5-50:45394 50-200:5851 >200us:563 tailMillis=1142]`＝**95.8% の offer は 5 µs 未満**で、
平均 3.49 µs は 4.2% の尾（world 読込の stall）が押し上げている。定常状態の 1 offer は ~2.5 µs。

## 3. 見つけた計器の欠陥 2 つ（1 つは修正）

### (a) `pendingImmediateIndices` は model run で負になっていた → **修正**

受理経路は `pendingImmediateIndices` を受理 batch の index 数で加算し、`IMMEDIATE_FLUSH_INDICES`（8192）と
比べて window が抱える geometry を bound する。描画側は**描いた全 group** の `mesh.indexCount()` を引いていたが、
model-run group の index は受理時に一度も加算されていない。結果、カウンタは負になり、**負の値は閾値に
永久に届かない＝bound が「厳しい」のではなく「死んでいる」**。

証拠は CPU contract の baseline 2 本で、どちらも**モデル 1 draw あたりちょうど 36 index**（6 quad × 6 index）:

| baseline | `pendingImmediateIndices` | `modelDraws` | 比 |
|---|---|---|---|
| `verify_line_baseline.txt` | **-3672** | 102 | 36.0 |
| `verify_line_baseline_postmerge.txt` | **-3168** | 88 | 36.0 |

修正は `celtium$pendingIndicesAfterDraw(pending, modelRun, indexCount)`（static・package-private、テスト可能）で、
model run は引かない。flush 末尾の再計算（`stillQueued`）は既に model run を除外していたので、これはその規則の
残り半分。`PendingIndicesAccountingTest` が 4 ケースを固定する。

### (b) `immediateFlushes` は flush ではなく **描いた group 数**（未修正・名前の欠陥）

`noteImmediateFlushes()` は `drawPendingImmediate` の group ごとに呼ばれる。実測でも
`immediateFlushes=642082` ＝ `immediateGroupsDrawn=642082`（世界）、`19859`＝`19859`（baseline）。
本当の flush 数は `actualFlushes`（22.1/フレーム）。**この 2 つを並べた行を読む限り取り違えない**が、
名前は嘘である。`verify_line_baseline*.txt` を比較する CPU contract があるため、キー名の変更は
別の変更として扱う（本 round では触らない）。

### (c) `celtium$pairClearNeeded` は off-target window ごとに String を組んでいた → **修正**

`"chain-pair:" + Long.toHexString(identity)` を呼び出し側で作り、第 4 引数で渡していたが、その文字列は
**拒否時のみ**読まれる。off-target window は 13/フレームなので、ほぼ出ない行のために毎 window
concat ＋ hex 変換を払っていた。ラベルを拒否分岐の中で組むようにした（`slot` と `pairIdentity` から）。

## 4. A/B（同一 scene・同一 flags、JAR だけが違う）

| | r48b-old（`0801cd8f`） | r48a-new（`06a3d6c6`） | 比 |
|---|---|---|---|
| `frameIntervalWarmP50Ms` | 2.880620 | **2.873357** | 0.9975 |
| `frameIntervalWarmP95Ms` | 5.517281 | 5.447658 | 0.987 |
| `frameIntervalWarmP99Ms` | 7.258676 | 6.790015 | 0.936 |
| `sceneCalibrationKeyMatches` | true | true | — |
| `presentedImageVerdict` | PASSED | PASSED | — |
| `worldTicksAtEnd` | 619 | 619 | — |

**p50 は 0.25% 差＝ノイズ。回帰は無い。** p99 の 6.4% は 1 サンプルの尾なので主張しない。
この round の変更は性能ではなく**正しさ**（bound を生かす）と**割り当ての削除**である。

## 5. 主張しないこと

- この round は条件 C を前進させていない。p50 0.25% は測定誤差の内側。
- §2 の ms は計器税込みの帰属で、性能値ではない。
- `pendingImmediateIndices` の修正が**フレーム時間を変える**とは主張しない。世界 scene には model run が
  無い（`modelDraws=0`）ので、この leg では差が出ない。証拠は baseline の算術（36 index/draw × draw 数）で、
  実機の再現ではない。
- `immediateFlushes` の名前は直していない。
