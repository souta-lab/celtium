# パス内訳を µs で読む: 窓の固定費は 8 本に分散せず、`grpDraw` と `wrecLoop` が本体だった（2026-11-04、round 59）

round 58 の `CELTIUM_FRAME_PASS_SPLIT` は **0.1 ms 丸め**だったため、窓の固定費 8 本
（`winHead` / `winPublish` / `winBeginPass` / `winPassEnd` / `winSubmit` / `winBarrier` / `wfpBegin` / `wfpMiddle`）が
**全部 0.0** と表示され、どれが重いかを決められなかった。本 round は
**`-Dceltium.vulkan.passSplitMicros=true`** を足して同じ leg を µs で取り直し、
「窓の固定費 ~0.2 ms は 8 本に分散している」という round 58 の読みを**棄却**した。
あわせて受理経路の状態キーをキャッシュした（`stateKeyCache`）が、**その効果は測定限界以下**だった。

## 1. 測定条件

| | |
|---|---|
| leg | `r59a-us`（4 キー形）／`r59b-us`（3 キー形）。どちらも `world` stage、AMD RX 6400 (RADV NAVI24) |
| JAR | `c5e357d9…`（r59a）／`ce3446a6…`（r59b）。fixture は `0e90c988…` 不変 |
| flags | `-Dceltium.vulkan.validation=false -Dceltium.vulkan.presentReadback=false -Dceltium.vulkan.presentedDumpEvery=0` ＋ `passSplit=true passSplitMicros=true batchTimings=true` |
| warm 窓 | `warmFromTick=400 warmToTick=600 holdUntilTick=620` |
| 判定 | 両 leg `PASSED`、`problems=[]`、`fixtureStatus=PASSED`、`sceneCalibrationKeyMatches=true` |

**この ms/µs は計器税込み**（`passSplit` は bracket ごとに 2 回の時計読みと名前解決をする。
`batchTimings` は offer ごとに 13 段の `nanoTime` を打つ＝~520 ns/offer）。したがって下の数字は
**帰属**であって性能の主張ではない。

## 2. フレーム内訳（`r59a-us`、p50 µs/フレーム）

| bracket | µs | 備考 |
|---|---|---|
| **frame** | **2019.1** | |
| world / hand / gui | 1592.4 / 46.0 / 391.5 | residual 24.8 |
| **flush**（world 内） | **486.5** | |
| winRecord（flush 内） | 363.7 | |
| **wrecLoop**（winRecord 内） | **243.6** | |
| **grpDraw**（wrecLoop 内） | **127.6** | `vkCmdDrawIndexed` 本体 |
| rcPrepared / rcPush / rcViewport / grpPrep / rcResolve | 34.1 / 29.8 / 18.2 / 20.8 / 0.5 | |
| winSubmit / winBarrier / winBeginPass / winHead / winEnd | 42.9 / 30.9 / 27.7 / 20.0 / 11.6 | |
| wfpMiddle / winPrefix / winPassEnd / winPublish / winTransition / wfpBegin / flushCloseGroup | 13.2 / 9.2 / 7.6 / 6.7 / 3.6 / 0.4 / 6.8 | |
| closeGroup（flush 直下） | 24.2 | |
| terrain / terrainPreflush / terrainRecord | 135.1 / 50.1 / 63.1 | Celtium の terrain |
| offCapture / offPrepare / offMerge / offPreflight / offAppend | 17.8 / 27.9 / 12.0 / 38.0 / 33.1 | 受理経路 |
| guiBatch / guiFlush | 139.4 / 73.9 | HUD のうち Celtium 分 |

### 何が棄却されたか

- **「窓の固定費 ~0.2 ms は 8 本に分散している」→ 棄却。** 8 本の実測は
  `winHead` 20.0 ＋ `winPublish` 6.7 ＋ `winBeginPass` 27.7 ＋ `winPassEnd` 7.6 ＋ `winSubmit` 42.9 ＋
  `winBarrier` 30.9 ＋ `wfpBegin` 0.4 ＋ `wfpMiddle` 13.2 ＝ **150.4 µs** で、しかも
  **`winSubmit` 42.9 と `winBarrier` 30.9 と `winBeginPass` 27.7 の 3 本で 101.5 µs**（67%）を占める。
  round 58 が「各 0.0」と読んだのは丸めのせいで、実際には**3 本に集中**している。
- **`winSubmit` は `vkQueueSubmit` ではない。** 同じ leg の `queueSubmitCalls=20128`／`worldFrames=5872`＝
  **3.4 回/フレーム**なのに窓は 36.3 枚/フレーム。つまり 32.9 枚は batched 経路の早期 return で、
  42.9 µs の大半は **3.4 回の実 submit（~10 µs/回）** が占める。`winSubmit` を「窓の固定費」として削る対象に
  数えるのは誤りで、削るなら submit の回数か中身。
- **`grpDraw` 127.6 µs は床。** 209.5 group/フレーム × 0.61 µs。round 58 の `packetDrawMillis` は 0.68 µs/回で
  同じ水準。draw を減らすには group を減らすしかない。
- **`wrecLoop` 243.6 のうち 127.6 が `grpDraw`**、残り 116 µs が 5 本の prep（`rcPrepared` 34.1 ＋ `rcPush` 29.8 ＋
  `grpPrep` 20.8 ＋ `rcViewport` 18.2 ＋ `rcResolve` 0.5 ＝ 103.4）。**単一項目は 34 µs 以下**。
- **`winRecord` の未帰属は 52 µs**（363.7 − 311.7）。窓あたり 1.43 µs で、bracket の外
  （`setViewportAndScissor`・`commandState.beginPass`・`countGroupsForTarget`・`windowBeginInfo`）に散っている。
  次の一手の候補だが、フレームの 2.6% なので優先度は低い。

## 3. 受理経路の段（`offerStages[nanosPerReached]`、`r59a-us`）

`s1..s13 = 52 18 89 66 25 19 20 893 114 690 870 41 696`（合計 3593 ns/offer、`immediateSubmitAvgNs=3915`）。

| 段 | ns | 中身 |
|---|---|---|
| s8 | **893** | `expandIndices`・行列・extent・状態キー・program・registry |
| s10 | 690 | preflight（slow path のみ＝52%） |
| s11 | **870** | group open（slow path のみ） |
| s13 | 696 | append |
| s1–s7 | 289 | 形式・stride・lightmap の検査 |
| s9 | 114 | merge 述語 |

**名前の付いた bracket は 128.8 µs/フレーム（507 ns/offer）しかない**のに、段の合計は 3593 ns/offer。
差の ~3.1 µs/offer は**どの bracket にも入っていない本体**で、`batchTimings` の 13 段の時計（~520 ns/offer）を
引いても ~2.6 µs/offer が未帰属。**受理経路の最大の的はここ**で、`offCapture`/`offPrepare`/`offMerge`/
`offPreflight`/`offAppend` の 5 本を全部足しても受理経路の 1/6 にしかならない。

## 4. 状態キーのキャッシュ（`stateKeyCache`）— 効果は測定限界以下

`celtium$stateSignature`（round 58）は signature を 3 つの version でキャッシュする。本 round はその
**写像（`celtium$stateFromSignature`）と hash（`celtium$stateKey`）と census（`celtium$noteStateStatistics`）**も
同じ 3 version ＋ レイアウト id でキャッシュした。hit では scratch 配列を**古いまま**残し、値を読む slow path が
`celtium$materializeOfferState()` で埋め直す。census を offer 毎から group open へ移したので、
`cullEnabledGroups` などの `*Groups` という名前が**初めて実態と一致**する。

| leg | キー | `stateKeyCache` | `offCapture` µs/フレーム |
|---|---|---|---|
| `r59a-us` | 3 version ＋ レイアウト id | 327,154 / 1,164,197（**21.9%**） | **17.8** |
| `r59b-us` | 3 version のみ | 345,942 / 1,140,705（**30.3%**） | **18.4** |

**3 キー形のほうが hit 率は高いのに遅い。** 3 キー形は hash を毎 offer 払うので、
`0.303 × hash` が `0.084 × (写像＋census)` を上回る。実測から逆算すると **hash ≈ 31 ns**、
写像＋census ≈ 90 ns で、`0.277 × 90 = 25 ns < 31 ns` なので 4 キー形が勝つ。**4 キー形を採用**した。

**ただし差は 0.6 µs/フレーム＝フレームの 0.03%** で、2 つの leg は別セッション（world 内容が違う）なので
これは**制御された A/B ではない**。主張するのは「方向」だけで、効果量は主張しない。
受理経路の 5 本の bracket は 2 leg で有意に動いていない（`offPrepare` 27.9→27.3、`offMerge` 12.0→12.3、
`offPreflight` 38.0→37.6、`offAppend` 33.1→33.8）。

## 5. 主張しないこと

- この round は条件 C（Celeritas を上回る）を前進させていない。差 0.42 ms に対して 0.6 µs は無意味。
- §2 の µs は計器税込みの帰属で、性能値ではない。`r59a-us` の frame 2019 µs は `r58a-split` の 1.2 ms と
  比べられない（あちらは `packetTimings` 付き、こちらは `batchTimings` 付きで税が違う）。
- `stateKeyCache` の 21.9% / 30.3% は**別 run** の値で、同一条件の対照ではない。
- `winRecord` の未帰属 52 µs の**中身は特定していない**。候補を並べただけで、どれが持っているかは測っていない。
