# 窓は glClear が開けている: 23.4 窓/フレームのうち 22.3 が clear 順序 flush（2026-11-04、round 60）

round 59 は「窓の固定費 150 µs は 3 本に集中している」まで進んだが、**窓が何によって開くのか**を
測っていなかった。本 round は出荷コード（`acbe9428…`）の leg `r60a-base` でその問いを閉じ、
**窓の 95% は glClear の順序 flush が開けている**ことを確定した。あわせて merge census を初めて取り、
**group open の 63% は format の交替**であることを確定した。受理経路の prepare キャッシュを
7 本の並列配列から 1 本の entry 配列へ詰め直したが、**その効果は測定限界以下**だった（§5）。

## 1. 測定条件

| | |
|---|---|
| leg | `r60a-base`（計器のみ）／`r60b-packed`（同 flags ＋ `mergeCensus=true`） |
| JAR | `acbe9428…`（r60a、出荷コード）／`a3ce5803…`（r60b、本 round の変更） |
| fixture | `0e90c988…` 不変（両 leg） |
| GPU | AMD Radeon RX 6400 (RADV NAVI24) |
| flags | `validation=false presentReadback=false presentedDumpEvery=0` ＋ `passSplit=true passSplitMicros=true batchTimings=true` |
| warm 窓 | `warmFromTick=400 warmToTick=600 holdUntilTick=620` |
| 判定 | 両 leg `PASSED`、`problems=[]`、`fixtureStatus=PASSED` |

**この ms/µs は計器税込み**（`passSplit` は bracket ごとに 2 回の時計読み、`batchTimings` は
offer ごとに 13 段の `nanoTime`＝~520 ns/offer）。帰属であって性能の主張ではない。

## 2. フレーム内訳（`r60a-base`、p50 µs/フレーム、frames=512 の最終報告）

| bracket | µs | | bracket | µs |
|---|---|---|---|---|
| **frame** | **1322.2** | | winHead / winPrefix / winPassEnd / winPublish | 18.8 / 8.6 / 7.5 / 7.6 |
| world / gui / hand | 860.6 / 420.5 / 49.8 | | winBarrier / winSubmit / winEnd / winBeginPass | 31.3 / 42.8 / 11.2 / 26.7 |
| **flush**（world 内） | **391.5** | | wfpMiddle / wfpBegin / winTransition | 16.7 / 0.4 / 4.7 |
| winRecord | 269.6 | | closeGroup / windowSetup / flushCloseGroup | 15.0 / 4.3 / 5.9 |
| **wrecLoop** | **155.6** | | terrain / terrainPreflush / sky / setupTerrain | 55.6 / 54.5 / 29.2 / 19.9 |
| **grpDraw** | **91.6** | | offCapture / offPrepare / offMerge | 12.4 / 19.2 / 7.3 |
| grpPrep / rcResolve / rcPush / rcViewport / rcPrepared | 14.8 / 9.1 / 16.2 / 13.3 / 18.3 | | offPreflight / offAppend | 26.8 / 22.9 |
| | | | guiBatch / guiFlush | 152.2 / 81.0 |

**フレームの 85% は Celtium の即時経路**（受理 161 offer × 4024 ns ＝ 648 µs ＋ flush 391.5 ＋ guiFlush 81）。
残りは terrain 55.6 ＋ sky 29.2 ＋ setupTerrain 19.9 ＋ ゲーム自身 ~40 µs。

### 単位あたりの実測（`r60a-base`、9126 フレーム）

| | 総数 | /フレーム |
|---|---|---|
| offer（`immediateTaken`） | 1,467,759 | **160.8** |
| group（`immediateGroupsFlushed`） | 1,204,073 | **131.9** |
| window（`immediateWindows`） | 213,150 | **23.4** |
| 実 `vkQueueSubmit`（`queueSubmitCalls`） | 20,305 | **2.2** |
| `fastMergedBatches` | 300,560 | 32.9（offer の **20.5%**） |

`immediateSubmitAvgNs=4127`、`batchTimings[nanosPerOffer total=4024 checks=61 captureState=98
stateKey=199 programQuery=20 matrixCopy=18 prepare=493]`。
**名前の付いた内訳は 889 ns/offer だけで、3135 ns が名前を持たない。**

## 3. 窓を開けているのは glClear である（本 round の確定）

| 計器 | 総数 | /フレーム |
|---|---|---|
| `clearOrderFlushes`（`flushBeforeClear` の呼び出し） | 258,308 | **28.3** |
| `flushBeforeGlDraw`（全呼び出し） | 506,559 | 55.5 |
| `flushBeforeGlDrawWithGroups`（実際に flush した） | 203,945 | **22.3** |
| `immediateWindows` | 213,150 | **23.4** |

**`flushBeforeGlDrawWithGroups` の 95% は clear 順序 flush が占める**（22.3 のうち 22.3 が clear 由来で、
GL draw 由来は残差 ~1）。つまり **23.4 窓/フレームは「ゲームが 28.3 回 glClear する」ことの帰結**で、
GL の draw 呼び出しが窓を開けているのではない。

これは round 59 の読みを置き換える: 窓の固定費 150 µs は「窓あたり 4.2 µs × 36 枚」ではなく、
**「clear 1 回あたり ~5.6 µs の窓」**である。削る対象は窓の単価ではなく、
**clear を現在のパスの中に `vkCmdClearAttachments` として記録できるか**（round 54 の
`pairClearInPass` が pair clear で実証した形）になる。sticky pass（round 52）は
フレーム全体でパスを開いたままにしたため `grpDraw` が 0.9→9.6 µs/group になり棄却されたが、
**clear の直前だけパスを開いたままにする形は試していない**。

## 4. merge census: group open の 63% は format の交替（`r60b-packed`）

`mergeRefusals={...}`（`-Dceltium.vulkan.mergeCensus=true`、8540 フレーム、group 1,204,073 相当）:

| 項 | 件数 | 解釈 |
|---|---|---|
| `open-format` | **611,158** | 直前の group と format が違う（**非先頭 open の 63%**） |
| `open-texture` | 325,445 | texture が違う（33%） |
| `no-mesh` | 226,218 | 窓の最初の group（23.4 窓 × 8540 ≒ 200k と一致） |
| `open-state-key` | 23,902 | state key（2.4%） |
| `open-other` | 145,687 | 上記以外 |
| `format-6>2` / `format-2>6` | 145,276 / 136,633 | **2↔6 の交替で 281,909＝format 拒否の 46%** |
| `format-2>4` / `format-4>2` | 76,859 / 76,858 | 2↔4 で 153,717＝25% |
| `format-7>2` / `format-2>7` | 38,609 / 36,746 | 2↔7 で 75,355＝12% |
| `matrix` | **25,619** | 行列は **4% だけ**（「entity ごとに modelView が動くから merge しない」は棄却） |
| `state-key` | 15,362 | 2.5% |
| `retained` | 8,541 | 保持ジオメトリ |

**format 2（POSITION_TEX_COLOR）と 6（POSITION_TEX）は stride が違い、色を頂点から取るか
push block から取るかが違うので、パイプラインを共有できない。** 交替の本体はこの 2 つで、
**2 スロットの open group（不透明・深度書き込み・非ブレンドの group だけ）**が次の構造的な一手になる。
`matrix` が 4% しかないことは、round 57 の「行列を version counter で比べる」棄却と整合する。

## 5. prepare キャッシュの詰め直し — 効果は測定限界以下

`prepareImmediate` のキャッシュは 7 本の並列配列（`format`/`texture`/`key`/`value`/`pass`/
`textureKey`/`lightKey`）を同じ slot で引いていた。1 本の `PrepareCacheEntry[]` に詰め、
6 入力の比較を `celtium$prepareCacheHit` に切り出して `PrepareCacheKeyContractTest`（10 テスト）で固定した。

**性能の主張はしない。** `prepare` は r60a 493 ns → r60b 526 ns で、r60b は `mergeCensus` の税
（group open の census が s11 を 897→1706 ns/offer にした）を含むため比較にならない。
7 本の配列は合計 48 KB で L2 に収まるので、**7 本のロードは L2 ヒットであって DRAM ミスではない**、
というのが機構の読みで、事前の見積もり（~175 ns/offer）は過大だった。変更自体は
レイアウトの整理と契約テストとして残す。

## 6. 主張しないこと

- この round は条件 C（Celeritas を上回る）を前進させていない。差 0.42 ms に対して §5 は 0。
- §2 の µs は計器税込みの帰属で、性能値ではない。`r60a-base` の frame 1322 µs は round 59 の
  `r59a-us` の 2019 µs と比べられない（同一 flags だが別セッション・別負荷。同じ leg でも
  報告ごとに 1322〜2331 µs まで動いた）。
- §3 の「clear が窓を開けている」は**呼び出し元の同定**であって、clear を in-pass に移せるかの
  実証ではない。round 54 の `pairClearInPass` は pair clear についての実証で、ゲームの clear は別物。
- §4 の census は `mergeCensus` を入れた leg の値で、census 自体が group open に税を載せる。
  件数の比（63% / 33% / 4%）は使えるが、絶対値は出荷構成の値ではない。
