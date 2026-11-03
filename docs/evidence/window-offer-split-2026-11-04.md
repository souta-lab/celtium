# 窓と受理経路の内訳（2026-11-04、round 49、leg `r49a-split`）

round 48 は受理経路の**名前の無い 0.5 ms** を残した。本 round は `passSplit`＋`windowSplit`＋
`batchTimings` を同時に当てて、**窓 1 枚の中**と**offer の stage ごと**の両方を 1 leg で取った。
結論は 2 つ: **窓 1 枚の最大項目は pair clear（19 µs）**で、それは GregTech bloom の mip pyramid が
1 フレームに 12 回クリアするため。**offer の最大項目は stage 8（state capture の区間、1660 ns）**で、
そこには名前の付いた部品が ~340 ns しか無い。

## 条件

| | |
|---|---|
| Leg | `r49a-split` — `world`（`celtium-world-accept`、188 mods、`qmagnet`）、AMD Radeon RX 6400（RADV NAVI24） |
| Renderer / fixture | `585e8a64347a6fd94d1054ee05b694433a7b0c20ce7d91a7695212e7b0a1be5d` / `0e90c988c55f3313d65ccbc4b2cb8713f8f855caea2c803ed5a3902a6779bd65` |
| Scene | 1280x720、`guiScale3.txt`、`sceneCalibrationKeyMatches=true` |
| Flags | `-Dceltium.vulkan.presentReadback=false -Dceltium.vulkan.presentedDumpEvery=0 -Dceltium.vulkan.frameBudget=true -Dceltium.vulkan.passSplit=true -Dceltium.vulkan.windowSplit=true -Dceltium.vulkan.batchTimings=true` |
| Verdict | `PASSED`、`problems=[]`、`fixtureStatus=PASSED`、`presentedImageVerdict=PASSED` |
| 規模 | `frameId=1968`、`immediateOffered=432674`（**219.9/フレーム**）、`immediateWindows=45506`（**23.1**） |

**この leg の絶対 ms は性能の証拠にしない。** 3 つの計器を同時に当てているので `frame` bracket は
p50 3.6 ms（KPI の frame interval は 2.87 ms）、`immediateSubmitAvgNs` は 8707（round 48 の
`batchTimings` だけの leg は 3494）。使うのは **(a) 窓 1 枚の中の相対配分、(b) offer の stage ごとの
相対配分、(c) 枚数と発生源**。

## 窓 1 枚の中（`CELTIUM_WINDOW_SPLIT`、45,530 窓の平均、µs/窓）

| 項目 | µs/窓 | 何を測っているか |
|---|---|---|
| **`offTarget`** | **22** | off-target routing 全体（下の 5 項目の合計） |
| └ **`clear`** | **19** | **pair clear**（`celtium$pairClearNeeded` ＋ クリア記録 ＋ `noteChainPairCleared`） |
| └ `pair` / `sync` / `trans` / `fb` | 0 | `ensureChainPair`・stale-name 走査・published-name 遷移・framebuffer/extent 解決 |
| `commands` | 12 | `beginCommands()`（開いた frame buffer を再利用する経路） |
| `acquire` | 10 | `recordAcquireBarriers` |
| `release` | 3 | `recordReleaseBarriers` |
| `route` / `pair` / `publish` / `begin` | 0 | — |

`syncScan=96/25651`（走査 96 回・memo ヒット 25,651）＝ stale-name 走査は既に memo で潰れている。

**窓 1 枚の最大項目は pair clear の 19 µs。** そして**そのクリアは 1 フレームに 12 回**走る:

`offTargetPairClears=[default-pair=0, chain-pair=23741]`（1968 フレームで **12.06/フレーム**）、
`offTargetPairs` は **16 個の chain pair** で、サイズは 1280x720 → 640x360 → 320x180 → 160x90 → 80x45 →
40x22 の **mip pyramid**（各段 2 枚＝ping-pong）＋ 1280x720 が 3 枚。発生源は round 41 の census が
名指しした `Shaders.renderFullImageInFBO`（GregTech の bloom post chain）で、段ごとに FBO を bind する
たびに 1 窓になる。

**12 × 19 µs ≈ 0.23 ms/フレーム**が Celtium だけの固定費。round 41 の見積り（13 × ~17 µs ≈ 0.22 ms）と
同じ桁で、**今回その中身が「pair clear」だと分かった**。

## offer の stage ごと（`offerStages[nanosPerReached ...]`、ns/到達 offer）

| stage 区間 | ns | 中身 |
|---|---|---|
| s1 | 81 | topology/line 判定・`expectedStride` |
| s2 | 22 | composite skip 判定 |
| s3 | 221 | present-from-composite 判定（`samplesBoundFramebufferColor` / `samplesCurrentTargetColor`） |
| s4 | 85 | `supportedImmediateTarget()` |
| s5 | 27 | `formatAccepted` |
| s6 | 19 | 範囲検査 |
| s7 | 29 | stride 検査・`ensureUsableLightmap` |
| **s8** | **1660** | **texture extent 参照 → `expandIndices` → 行列 capture → `captureState` → `stateKey` → program query → registry get** |
| s9 | 126 | merge 判定 |
| **s10** | **3181** | **slow path の preflight（mirror supply ＋ `prepareImmediate`）**（到達 51.9%） |
| **s11** | **1563** | **group open（samePass・matrix・scissor・light・descriptor）**（到達 51.9%） |
| s12 | 44 | texture mirror 検査 |
| **s13** | **1069** | **append ＋ 受理カウンタ** |

到達数: `offerStages=[1..10:432674 11:224711 12:224711 13:432674 14:432674]` ＝ **fast-merge 48.1%**
（`fastMergedBatches=207963`）。**拒否は 0**（`refusedBeforeAppend=0`、`shaderProgramRefusals=0`、
`immediateTaken=immediateOffered`）。

**s8 の 1660 ns のうち名前の付いた部品は ~340 ns**（`captureStateMillis=59`＋`stateKeyMillis=33`＋
`programQueryMillis=10`＋`matrixCopyMillis=8` を 432,674 offer で割ると 254 ns、`expandIndices` と
extent 参照を足して ~340）。**残り ~1300 ns は区間の中の名前の無い本体**で、`boundProgram != 0` の
分岐はこの leg では一度も入っていない（`shaderProgramRefusals=0`）。

## 本 round のコード

1. **pair clear の transfer barrier を 2 本から 1 本へ**（`GllessFrameTarget.Pair.toTransfer`）。
   色と深さの遷移は同じ `ALL_COMMANDS → TRANSFER` 依存を 1 つの pair の 2 画像に課すものなので、
   1 回の `vkCmdPipelineBarrier` にまとまる。判断は `GllessFrameTarget.celtium$transferImages`（static・
   package-private）に出し、`PairTransferBarrierTest` 4 ケースで表を固定した。
2. **`immediateFlushes` → `immediateGroupsFlushed`**。このキーは flush 数ではなく**描いた group 数**で、
   実測でも `immediateGroupsFlushed=225059` ＝ `immediateGroupsDrawn=225059`、本当の flush 数は
   `actualFlushes=43469`（22.1/フレーム）。CPU contract の baseline 2 本を同じ変更で更新した。
3. **`VulkanDynamicMesh` の slice alignment を stride ごとにキャッシュ**（`celtium$sliceAlignmentFor`）。
   `lcm(16, stride)` は append のたびに Euclid ループで計算されていたが、依存するのは stride だけ。
   `SliceAlignmentTest` 4 ケースで算術を固定した。

## 対照 leg（`r49b-merged`、同じ flags・同じ scene、JAR だけ違う）

`302cd2b61781aa3eb9b69407845d587669de186a97a2833a37f6a4d5aa79a6f0`（1 の barrier 統合と 2・3 を含む）。
`PASSED`、`problems=[]`、`fixtureStatus=PASSED`、`presentedImageVerdict=PASSED`、`sceneCalibrationKeyMatches=true`、
**validation メッセージ 0**、`refusedBeforeAppend=0`、`shaderProgramRefusals=0`、`pendingImmediateIndices=0`。
**barrier 統合が validation エラーを出さないことの証拠はこれ。**

| | r49a-split（統合前） | r49b-merged（統合後） |
|---|---|---|
| `frameId` | 1968 | 2167 |
| `immediateOffered`/フレーム | 219.9 | 221.7 |
| `immediateWindows`/フレーム | 23.12 | 23.19 |
| `offTargetPairClears`/フレーム | 12.06 | 12.06 |
| `CELTIUM_FRAME_BUDGET_SUMMARY` frame p50/p95/p99 | 5.2 / 8.3 / 220.0 | 5.2 / 7.3 / 264.1 |
| `record` p50 / `batch` p50 | 2.4 / 1.0 | 2.4 / 1.0 |

**速度は主張しない。** p50 は同一（5.2）、`record` と `batch` も同一。`frameId` の 1968→2167 は
**scene/load の差**で、frame budget の p50 が動いていない以上、この変更の効果ではない（p99 の
220→264 も world 読込の 1 フレーム）。12 クリア/フレーム × barrier 1 本は 5.2 ms の 1.3% で、
この leg の分解能以下。

## 主張しないこと

- **速度の改善は主張しない。** 1 は 12 クリア/フレーム × barrier 1 本、3 は 220 append/フレーム ×
  ~20 ns。どちらも 0.1% 台で、この leg の絶対 ms では分離できない。
- **s8 の ~1300 ns の正体は分かっていない。** 名前の付いた部品が 340 ns しかないことまでで、
  残りが cache miss なのか、`captureState` の中の見えていない仕事なのかは、この leg では決まらない。
- **pair clear を消せば 0.23 ms が戻る、とは言わない。** クリアはゲーム自身の `glClear` の再生で、
  絵の正しさが依存している。安くする方法（render pass の `loadOp=CLEAR`、`vkCmdClearAttachments`）は
  まだ試していない。
- この leg は 3 計器同時なので、round 48 の `r48a-split`（`batchTimings` のみ）と絶対値を比べない。
