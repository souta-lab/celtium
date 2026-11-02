# prepare キャッシュの負荷率が実欠陥だった（2026-11-03、round 18）

対象: AMD RX 6400 (RADV NAVI24)、`celtium-world-accept`（188 mods・`qmagnet`）、1280×720、
`CELTIUM_LEG_SEED_OPTIONS=guiScale3.txt`、240 s、`sceneCalibrationKeyMatches=true`、両 leg `fixtureStatus=PASSED`。

## 何を決めたか

`prepareImmediate` の直結キャッシュは **256 スロット**で、コメントは「1 フレームは約 194 種の
material を要求するので 1 フレーム分が入る」と書いていた。194/256 は**負荷率 76%**で、直結表では
2 つの material が同じスロットに落ちると**毎フレーム互いを追い出す**。census は
`prepareCache=405780/449954` = **ヒット 90.2% / ミス 9.8%**（44,174 ミス）で、生きている material が
約 194 種しかないのにこの数字だった。**仮説は「衝突による構造的なスラッシング」で、A/B で確定した。**

## A/B（同一 JAR・同一 scene・同一 flags、差は 1 プロパティだけ）

`-Dceltium.vulkan.prepareCacheBits=8`（256、旧）vs `=10`（1024、新）。JAR は両 arm とも
`ba4b68cdf2cfd857a417382fc94c5ea131bfc3f894d8c7438ddb0b49a85bdb5b`、fixture は
`9dde34c68f3e9060e8db71c5ab48d9626cb936177e0ce04db2860525f60977b7`。
flags: `-Dceltium.vulkan.validation=false -Dceltium.vulkan.presentReadback=false
-Dceltium.vulkan.presentedDumpEvery=0 -Dceltium.vulkan.frameBudget=true -Dceltium.vulkan.passSplit=true`。

| leg | 表 | prepareCache | ミス率 | frameInterval p50/p95/p99 (ms) | frameBudget frame p50 | record | batch |
|---|---|---|---|---|---|---|---|
| `r38a-cache8` | 256 | 405780/449954 | 9.81% | 2.740 / 3.808 / 4.558 | 3.1–3.5 | 0.6 | 0.8–0.9 |
| `r38b-cache10` | 1024 | 486810/495629 | 1.78% | **2.580 / 3.638** / 4.603 | **3.0–3.2** | **0.5–0.6** | 0.8 |

- **ミス 44,174 → 8,819（−80%）**。これが機構の証拠で、フレーム時間の差より先に確定する。
- 独立な 2 計器が同じ方向: fixture の `frameIntervalP50Ms` **−5.8%**、`p95` **−4.5%**、
  renderer の `frameBudget frame` p50 **3.3 → 3.1 ms**（3 窓の中央値）。
- 仕事量は同じ: groups/frame 116.2 vs 116.0、windows/frame 36.7 vs 37.8。
- p99 は 4.558 → 4.603 で**悪化側**（ノイズ幅）。p99 の改善は主張しない。

## 本 round のコード

- `PREPARE_CACHE_BITS`（既定 10 = 1024 スロット、`-Dceltium.vulkan.prepareCacheBits` で 6..12 に束縛）。
  スロットは `mix >>> (64 - BITS)` で、サイズとシフトが同じ定数から出る（256 のままシフトを 56 に
  固定すると 1024 表の上位 256 スロットしか使わない、という取り違えを型で防ぐ）。
- `prepareCacheSummary()` が表のサイズを併記（`hits/calls@size`）。サイズの違う 2 arm のヒット率を
  同じ数字として比べないため。
- **`frameBatchSignature` のハッシュを shipped path から外した**（`-Dceltium.vulkan.frameSignature`、
  既定 OFF）。offer ごとに `memGetInt` 2 回と乗算 6 回を払って、読むのは診断カウンタ 1 個だけだった。
  比較側も同じフラグで gate し、OFF のときレポートは `identicalFrameSignatures=off`（`0/0` は
  「同一フレームが無かった」と読めてしまう）。
- `expectedStride` の 12 項三項演算子を `celtium$expectedStride(int)` の switch へ。offer ごとに
  評価される定数表で、項の追加が位置ずれを起こす形をやめた。

## 主張しないこと

- **validation は両 arm とも OFF**。この A/B は時間の比較で、VUID 0 の証拠ではない（gate は別 leg）。
- 条件 C（Celeritas 超え）には届いていない。5.8% は 1.68× の差を埋める量ではない。
- ミス 1 回のコストは測っていない。ミス率が 80% 減って frame が 5.8% 縮んだ、までが観測。
- Intel arm は未実行。
