# グループが開く理由の内訳: format が 48%、そして format は畳めない（2026-11-03、round 40、leg `r40a-census`）

round 39 は「offer の 55% が slow path」まで測って、次を `-Dceltium.vulkan.mergeCensus=true` で
拒否理由を読むことにした。本 round はそれを読んだ。結果は 2 つで、**2 つめが次の構造変更の選択肢を消す**。

## 1. 測定

| | |
|---|---|
| Leg | `r40a-census` — `world`（`celtium-world-accept`、188 mods、`qmagnet`）、AMD Radeon RX 6400（RADV NAVI24）、`gpuRequirementMet=true` |
| Renderer / fixture | `814da57a2429d6bb0efb1596cd096f3f8108221b208c16e01dec777d22ffea6e` / `0e90c988c55f3313d65ccbc4b2cb8713f8f855caea2c803ed5a3902a6779bd65`（round 39 の committed tree） |
| Scene | `sceneCalibrationKeyMatches=true`、1280×720、`guiScale3.txt`、240 s、warm 400–600、hold 620 |
| Flags | `-Dceltium.vulkan.validation=false -Dceltium.vulkan.presentReadback=false -Dceltium.vulkan.presentedDumpEvery=0 -Dceltium.vulkan.batchTimings=true -Dceltium.vulkan.frameStats=true -Dceltium.vulkan.frameBudget=true -Dceltium.vulkan.mergeCensus=true` |
| Verdict | `PASSED`、`problems=[]`、`fixtureStatus=PASSED`、`presentedImageVerdict=PASSED`、`presentedImageAssertionFailures=0` |

**この leg の絶対 ms は使わない。** census は拒否ごとに `TreeMap` merge を 1 回、affine と run-break の項では
文字列を 1 本作る（合計 ~1.04M 回）。使うのは**個数**と、同時に取った `offerStages` の**相対**配分。

## 2. グループは 931,838 回開き、48% は format のため

```
immediateOffered=1689249  fastMergedBatches=757411 (44.8%)  groupCreateCount=931838 (55.2%)
mergeRefusals={format=448268, no-mesh=190619, texture=167484, affine-f12=69264, matrix=21354,
               affine-f4=14236, state-key=13490, retained=7119, colour=4,
               run-break-f2-b1-FontRenderer=26600, run-break-f9-b1-TexturedQuad=29,
               run-break-f2-b0-TileEntityBeaconRenderer=5, run-break-f5-b1-ParticleManager=5,
               run-break-f9-b0-TexturedQuad=2, run-break-f6-b1-DrawUtil=1}
```

拒否の合計は **931,838** で `groupCreateCount` と**一致する**（census が決定点で数えていることの陽性対照）。
group open 側の独立した分類も同じ形を出す: `open-format=510082`（55%）、`open-texture=271820`、
`open-other=129328`、`open-state-key=20608`。

| 理由 | 回 | 1 フレーム | 何か |
|---|---|---|---|
| **format** | 448,268 | 63 | 直前のグループと vertex layout が違う |
| **no-mesh** | 190,619 | 27 | flush 後の最初の 1 個（窓 23.4/フレーム） |
| **texture** | 167,484 | 24 | バインドテクスチャが違う |
| affine-f12 / f4 | 83,500 | 12 | texgen（format 12）と ITEM の texture affine が動いた |
| matrix | 21,354 | 3 | modelview が動いた |
| state-key | 13,490 | 2 | blend/alpha 等が動いた |
| retained | 7,119 | 1 | retained geometry |

`frameId=7118`、`worldFrames=4481`、`windowRecords=166645`（**23.4 窓/フレーム**）、`actualFlushes=159454`。

## 3. format は畳めない（この round が消した選択肢）

format の 48% を消す最も直接的な方法は「vertex layout の違う 2 つを 1 本の pipeline に統合する」こと。
受理された layout は `acceptedByFormat=[1:1, 2:982773, 3:0, 4:114821, 5:2824, 6:386142, 7:122536]` なので、
本命は **2（POSITION_TEX_COLOR）と 6（POSITION_TEX）**。

**これは安全にできない。** `immediate_position_tex.vert`（format 6）は色を**push block から**取る
（`u_ColorR/G/B/A`、combine のときは白）。`immediate_tex_color.vert`（format 2）は**頂点ごと**の `a_Color` を取る。
6 を 2 に寄せるには頂点に色を焼くしかなく、push block の float を 8 bit に量子化することになる
（＝tint が変わる。画像 gate が落とすべき差）。逆向き（2→6）は頂点色を失うので論外。
**したがって format 分割は現行アーキテクチャでは構造的に残る。**

census は今後 `format-A>B` の形で**遷移**を記録する（`format=448268` ではなく `format-6>2=…`）。
どの遷移かが分かれば「その 2 つだけ」を対象にできるが、上の理由で頂点変換なしには成立しない。

### 遷移の内訳（leg `r40b-gate`、validation 有効なので個数は `r40a` と比較できない。比率だけ読む）

```
format-6>2=43904  format-2>6=41267  format-2>4=25488  format-4>2=23218  format-7>2=12077
format-2>7=10331  format-2>12=1168  format-12>5=1162  format-6>4=3028  format-4>6=2579
format-4>7=449  format-6>7=277  format-1>6=1
```

format 分割 164,941 のうち **2↔6 が 85,171（52%）**、2↔4 が 48,706（30%）、7↔2 が 22,408（14%）。
**52% が 1 組に集中している**が、その 1 組がまさに §3 で畳めないと分かった組である。
残る 2↔4（ITEM）と 7↔2 も layout が違う（4 は normal 付き、7 は texcoord 無し）ので同じ理由で畳めない。

## 4. 受理経路の内訳（`offerStages` の読み方に注意）

`offerStage(s)` は `marks[s-1]` に書き、集計は `deltas[stage] = marks[stage] - marks[stage-1]` なので、
**`sN` はマーク N からマーク N+1 の区間**（1 ずれる）。読み直すと:

```
offerStages[nanosPerReached s1=65 s2=21 s3=84 s4=66 s5=27 s6=22 s7=25 s8=861 s9=99 s10=1770 s11=1831 s12=55 s13=605]
```

| 表記 | 実際の区間 | ns | 母集団 |
|---|---|---|---|
| `s8` | マーク8→9: lightmap 検査・program query・state capture・state key・**registry lookup** | 861 | 全 offer |
| `s9` | マーク9→10: **merge 述語** | **99** | 全 offer |
| `s10` | マーク10→11: **supply + prepare** | 1770 | slow path |
| `s11` | マーク11→12: **group open** | 1831 | slow path |
| `s13` | マーク13→14: **頂点 append** | 605 | 受理 |

`s9=99` は独立した `accept[decideMs=37]` ÷ 392,455 = 94 ns と一致する。**merge 述語は 99 ns で、ホットスポットではない。**
コストは slow path の 2 区間（supply+prepare 1770、group open 1831）と、全 offer の `s8`（861）にある。

`s8` の中身は同時に取った parts が割っている: `parts[expand=50 matrix=62 extent=58 registry=654 supply=2314 prepare=879]`。
**registry lookup が 654 ns**で `s8` の 76%。`memo[hits=4240645 misses=290341 slots=256 drops=0 forgets=552]`
＝ミス率 6.4%、`frameId=7118` で **41 ミス/フレーム**。ミスは capture path 全体なので ~2 µs/ミス。
`prepareCache=917388/931899@1024`（98.4% hit）と `lazyMirror[supplies=0 rawWriteRefreshes=97 current=60859
empty=37497]` は既に床に近い。

## 5. 本 round のコード

受理経路は **1 offer で同じ名前を registry に 3 回**聞いていた（merge 述語の直前・`prepareImmediate` の中・
group open）。`celtium$supplyMirror` だけが wrapper を作り（`imageFor`）、`celtium$refreshRawWrites` は渡された
wrapper に upload する。つまり**mirror が完全だった batch**（＝描かれる側）では 2 回目・3 回目は同じ問いの
繰り返し。`celtium$supplyMayReplaceWrapper(boundTexture, mirrorComplete)` として規則を名前付き・static にし、
`SupplyWrapperReuseTest` が 4 ケースを固定。`prepareImmediate` に「呼び出し側が解決済みの wrapper」を取る
overload を足し、group open は supply か framebuffer publish が動きうるときだけ引き直す。

**速度は主張しない。** 期待値は ~1%（636 lookup/フレームのうち ~262 を消す。ヒットは ~100 ns）。

### 検証（leg `r40b-gate`、JAR `11537725…`、validation 有効）

`world`、AMD RX 6400、1280×720、`guiScale3`、240 s、`-Dceltium.vulkan.validation=true
-Dceltium.vulkan.presentReadback=true -Dceltium.vulkan.presentedDumpEvery=0 -Dceltium.vulkan.batchTimings=true
-Dceltium.vulkan.frameStats=true -Dceltium.vulkan.mergeCensus=true`、warm 400–600。

| | |
|---|---|
| verdict / fixture / problems | `PASSED` / `PASSED` / `[]` |
| `presentedImageVerdict` / failures | `PASSED` / 0 |
| distinct / nonBg / bands / black | 76,733 / 0.990181 / 720 / 0.007429 |
| `sceneCalibrationKeyMatches` | true |
| `VUID` 行（run dir 全体、gz 含む） | **0** |
| validation layer | `messenger=0x10000000001 layer=VK_LAYER_KHRONOS_validation` |

画像 gate は round 39 の 76,703 / 0.989693 と同じ範囲で、**変更は絵を動かしていない**。

## 6. 次の一手（この round が決めたこと）

- **format 分割は消せない**（§3）。group open を減らす残りの道は「offer を減らす」か「記録を client thread から出す」で、
  これは plan §12 の設計そのもの。受理経路の micro-opt は床（`s9=99 ns`、`prepareCache` 98.4%、`lazyMirror` は
  0 supply）。
- 窓は **23.4/フレーム**（`windowRecordPerFrameMillis=0.61`、`swapPointImageMillis=1363/7299=0.187 ms`）。
  `rawWriteGate=[skips=0 flushes=1]` なので round 39 の gate はこの scene では発火せず、窓の発生源は
  round 16 の census（post chain 13/フレーム）のまま。
- `CELTIUM_FRAME_BUDGET`（p50/p95/p99、ms）: `frame=3.1/4.6/5.3 record=0.5/0.7/0.7 batch=0.6/0.8/2.0
  present=0.2/0.4/0.6 gpu=0.1/0.1/0.2 other=1.7/3.0/3.9`、`gpuShare=0.02`。

## 主張しないこと

- 絶対 ms（census 税込み）。
- validation（この leg は `validation=false`）。画像 gate は PASSED だが画質の証拠にしない。
- 「format を畳めば 48% が消える」— 畳めない、が本 round の結論。
