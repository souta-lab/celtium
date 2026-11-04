# 受理経路の state-key キャッシュを layout id ごとのスロットに分ける（2026-11-04、round 63）

round 62 は「残る 0.52 ms は 1 round の最適化では動かない」を確定させ、次にやることを 2 つに絞った
（immediate の bindless texture 化・不透明 group の 2 スロット化）。どちらも複数 round の工事で、
残り 2 round では入らない。本 round はその 2 つを**始めず**、受理経路に残っていた
**単一エントリのキャッシュが、census が測っている交替パターンそのものに当たって必ず外れる**欠陥を直した。

## 1. 変更: offer state-key キャッシュを layout id ごとの 16 スロットへ

`celtium$offerStateKey(int formatId)` は 4 つのキー（`stateVersion`・`environmentVersion`・
`windowTarget`・`formatId`）で 1 エントリを引いていた。**layout id がキーに入っている**（round 59 の
`r59a-us`/`r59b-us` で「id を落とすと hash の方が高くつく」と測って入れた）ので、
**format が変わるたびに必ず miss する**: 答えるはずのエントリは、直前の offer が上書きしたものだから。

merge census は group open の **63% が format 交替**（うち 2↔6 が 52%）と言っている。これは offer 列に
ついて同じことを言っている — 連続する offer が違う id を持つことが多い — ので、単一エントリは
**census が測っているパターンの上で thrash していた**。

```java
int slot = celtium$offerKeyCacheSlot(formatId);   // formatId & 15
if (celtium$stateKeyCacheHit(stateVersion, environmentVersion, windowTarget, formatId,
        this.celtium$keyCacheStates[slot], this.celtium$keyCacheEnvironments[slot],
        this.celtium$keyCacheWindowTargets[slot], this.celtium$keyCacheFormats[slot],
        this.celtium$keyCacheValids[slot])) { ... return this.celtium$keyCacheKeys[slot]; }
```

* **id は identity のまま**。スロットは hint で、id はスロットに保存されて毎回比較される。
  `VulkanImmediateFormats.COUNT = 14` なので 0..13 は衝突しない。表の外の id はスロットを共有しうるが、
  **hit が減るだけで、別 layout の key を返すことはない**。
* 4 つのキーの predicate は変えていない。`celtium$stateKeyCacheHit` はそのまま。
* `celtium$stateScratch` の契約も変えていない。hit は scratch を stale のまま残し、slow path が
  `celtium$materializeOfferState()` で埋める（`celtium$offerStateFresh` は hit/miss 両方で false にする）。

## 2. 計器: 収集していたのに一度も出していなかった hit/miss を verify 行に載せた

`celtium$stateKeyCacheSummary()` は**定義だけで呼び手が無かった**（`grep` で 1 件）。
つまり「key はキャッシュされている」と「毎 offer miss している」をログから区別できず、
上の欠陥は数字で反証できない状態だった。`CELTIUM_VERIFY` 行に
`stateKeyCache=hit<hits>/<misses>` を足した（`textureIndex=hit…/miss…` の隣）。

## 3. テスト

`StateSignatureVersionContractTest#theKeyCacheGivesEachLayoutItsOwnSlot`:

* `slot(2) != slot(6)` — census が名指しする主要交替が同じスロットに落ちたら、この変更は
  「単一エントリの改名」であって修正ではない。
* `0..VulkanImmediateFormats.COUNT-1` の全 id が相異なるスロットを持つ。
* `slot(2) == slot(18)` — スロットは hint であって identity ではないこと。

celtium-vulkan **421 tests / 0 failures / 0 errors**（420 → 421）。

## 4. これは (C) を閉じない

見積りは **~85 ns/offer × 161 offer = ~14 µs/frame**（frame 1322 µs の約 1%）。
round 58 の paired KPI は 1.175 で、必要なのは ~12%。**この変更単独で (C) に届く主張はしない。**
round 63 の (C) への寄与は「受理経路のキャッシュが交替パターンで外れていた」という
**機構の修正**と、その hit 率を次 leg で読めるようにしたことである。

## 5. 棄却・未着手（再試行しないために）

* **2 スロット open group**（format 63%）: `currentImmediateMesh` ほか ~25 フィールドが
  「開いている 1 group」を前提にしており、2 スロット化は shipping path 全体の refactor。
  残り 2 round では入れない（round 62 の判断を維持）。
* **immediate の bindless texture 化**（texture 24%）: pipeline layout・push block・shader 同時変更。
  同上。
* **`prepareImmediate` のキャッシュ詰め直し**: round 62 で「測定限界以下」。
* **`SORT_OPAQUE`（既定 OFF）を既定 ON に**: round 58 の `pipeline bind 4,117/727,499` が示すとおり
  bind の dedupe は既に効いており、並べ替えの余地が無い。
* **`INDIRECT_IMMEDIATE`（既定 OFF）**: `celtium$tryEnqueueIndirect` は既定で即 return する。

## 6. paired KPI（round 63、AMD RX 6400、3対、`disturbedRepetitions=[]`）

`CELTIUM_KPI_JARS=.gv-verify/jars/r63a-stage`（renderer `0d04469b…`、fixture `0e90c988…` 不変）/
`CELTIUM_KPI_REFERENCE=.gv-verify/run-r58kpi-celeritas-r1/mods/01-celeritas-forge-mc12.2-2.5.0-dev.jar`
（`567dcf8a…`）/ `CELTIUM_KPI_REPS=3` / `CELTIUM_LEG_SEED_OPTIONS=.celtium-work/options/guiScale3.txt`。
sceneCalibrationKey は round 58 と**完全一致**、jvmArgs も `frameDumpDir` 以外一致。

| rep | Celtium p50 | Celeritas p50 | 比 |
|---|---|---|---|
| 1 | 2.988 ms | 2.335 ms | 1.279 |
| 2 | 3.488 ms | 2.373 ms | 1.470 |
| 3 | 2.947 ms | 2.340 ms | 1.259 |
| **median** | **2.988** | **2.340** | **1.279** |

`verdict=SLOWER`、`goalCondition=NOT_MET`、`disturbedRepetitions=[]`、`externalCpuCores=0.083`（上限 0.5）。

### これは round 58 からの**悪化**である（本 round で判明した最重要事項）

round 58 の同じ KPI は **1.175**（Celtium 2.642 / Celeritas 2.325）。**Celeritas は 2.325→2.335 ms で不変**、
**Celtium だけ 2.642→2.988 ms（+13.1%）**。scene・窓・flags・fixture・GPU は同一なので、
**round 58 と round 63 の間の Celtium の変更が frame を 13% 遅くした**。

同じ 2 つの leg の `CELTIUM_VERIFY` を突き合わせると、遅くなったのは**窓の記録**である:

| 計器 | round 58 | round 63 | 変化 |
|---|---|---|---|
| `windowRecordMillis` / `windowRecords` | 4385/237481 = **18.46 µs** | 4568/218069 = **20.94 µs** | **+13.4%** |
| `actualFlushes` / frame | 22.5 | 20.4 | −9% |
| `immediateWindows` / frame | 23.5 | 21.4 | −9% |
| `immediateOffered` / frame | 167.6 | 163.5 | −2% |
| `terrainBatchMillis` | 81 | 94 | +16% |
| `terrainBindMillis` | 25 | 39 | +56% |

**窓の数は減っているのに 1 窓が 13.4% 遅い。** 窓の記録は frame の 16.4% なので、これ単独では 13% を説明しない
（2.2% ぶん）。**frame 全体が一様に ~13% 遅い**形であり、原因は未確定。round 64 の主題にする。

候補（未検証、優先順）:
1. **ホットメソッドの bytecode 肥大で inlining が落ちた**。`HugeMethodLimitTest` は 8000 バイト超を禁止しており
   通っているが、325–8000 バイト帯の**inlining 閾値**は見ていない。round 59–63 は
   `submitImmediateBatchBody` / `drawPendingImmediate` / `recordImmediateOnlyPass` の周辺に
   計器と分岐を足している。
2. `regAnnouncedSkips` が **4558 → 0**。registry の announcement skip が一度も効かなくなった。
3. `itemLitGroups` が **1499 → 3**（format 4 の lit 判定）。頻度は 0.15/frame なので frame への寄与は小さいが、
   state mirror の `state[8]` が変わった可能性を示す。
4. GC（未計測）。

**round 64 の判別手段**: `CELTIUM_KPI_CONTROL_JARS` に round 58 の JAR を入れて同一 repetition で
candidate→celeritas→control を回す（`medianControlRatio` が machine drift を相殺する）。
または `65946f88a` の JAR を stage して 1 leg ずつ `windowRecordMillis/windowRecords` を比べる。
