# model replay は自分を繰り返す — list別のtupleで再解決を止めた（2026-11-02）

測定した renderer `3963fb4fd4997b1a10f409376ef86f2875f12af4f24fcee91a80704a25c57720`、fixture
`c5f146c3148d6c7b76f9b3382afead776917f8c3a358db3bc7eed3287547b1dc`（rc.26、`main` d8aa659ff + 本差分）。
commit する jar は `e405a89285118f9ba5d7f3388e731de29249aa892bd1b51908a01b0110fdb24e` / fixture
`4e6382a793561721791ebc39e0659a90eecc414eb4c3cc6133871213534168bc` で、**触った 4 class
（`ModelReplay`・`VulkanTerrainGpuBackend`・`ModelReplayFastPath`・`VulkanModelInstances`）は `javap -p -c` の
code が測定 jar と同一**（差は comment と line table のみ。fixture も `ValidationClient` の code が同一）。
AMD Radeon RX 6400（RADV NAVI24）・
mode `plain`・X11・validation 既定 ON・200 s・Supersymmetry instance・`CELTIUM_LEG_SEED_OPTIONS=guiScale3`・
`-Dceltium.vulkan.batchTimings=true`。

## 観測: 2.1M replay のうち 98.9 % は「同じ list の同じ入力」

`modelInstances` は 2,149,974 replay を **92 run** で描いた（run は stateKey/target/projection/fog/viewport/pass が
一致する限り伸びる）。つまり frame あたり約 1 run であり、run の中では state が変わっていないことが既に証明されている。
それでも replay ごとに `celtium$captureState`（16 int の組み立て + 15 個の per-batch 計数）・`stateKey`・
texture registry 2 回・array index 2 回・texture affine・light map 座標・lit payload を毎回作り直していた。

## 変更

1. `celtium$captureState` を **signature（19 個の生入力）→ 16 int の写像 → 計数** に分解した。写像は
   `celtium$stateFromSignature` という純関数ひとつに置き、capture も fast path も同じ関数を通る。
   したがって「signature が同じなら state も同じ」は定義から成り立つ（`ModelStateSignatureTest`）。
2. `ModelReplayFastPath`: replay の解決済み tuple（state/key、texture/lightmap の array index、colour、combine、
   affine、lit payload、light map 座標、pool slice、quads、対象と frame）を **list ごとの 64 slot** に保持する。
   guard は identity（frame/list/texture/target/pass/meshTable/openGroup/instances/registry/textureArray/lightmap/combine）→
   値（signature/projection/colour/affine/lightCoord/light/combine）→ run の条件（`extend` と同じ判定）の順で、
   1つでも違えば miss として既存経路へ落ちる。hit は record の matrix だけ書き直す。
3. `-Dceltium.vulkan.modelReplayFastPath=false` が同一 JAR の対照（`enabled=false` で全 miss）。
   計器は `modelFastPath[hit= miss=(shape= inputs= run=) armed= lastShape= enabled=]`。

**最初の実装は全 miss だった**（leg `r18e-shape`: 1,828,826 replay がすべて shape miss、`lastShape=list`）。
原因は単一 slot の「直前 replay」キャッシュで、pack は list A→B→A と交互に replay するため一致し得ない。
list を key にしたことで hits が出るようになった。この判別は計器（miss 理由の名前）が無ければできなかった。

## 陽性: list 別キャッシュ（leg `r18g-on`）

`modelFastPath[hit=2127290 miss=22682(shape=607 inputs=21089 run=986)]` = **98.9 % hit**。
miss の内訳は意味がある: `shape=607` は list ごと frame ごとの初回（8 list × 116 frame 程度）、
`inputs=21089` は rotation/light/affine/colour/lightmap のどれかが動いた replay（entity の向き・アニメ）、
`run=986` は run 自体が切り替わった回。つまり「同じ list だが入力が動いた」は正しく miss になっている。

同じ leg の `modelEnqueueMillis=865 / modelEnqueueCalls=2149974` = **402 ns/replay**。
単一 slot 版で全 miss だった leg `r18e-shape` は `1144 ms / 1828828` = **626 ns/replay** で、
replay 数（1.8M vs 2.1M）も run 数（89 vs 92）も同程度である。差 224 ns/replay を hit の節約と読む。
**ただし別 build・別 run の比較であり、frame 時間の主張ではない**（下記）。

## 受入（この round の leg で言えること）

| leg | jar | 結果 |
|---|---|---|
| `r18a-fastpath` | `dfcc3b6a…` | `accepted=true`・`verdict=PASSED`・`fixtureStatus=PASSED`・`presentedImageVerdict=PASSED`（black 0.00377, distinct 13293）・`validationMessageCount=0`・`validationAllowedCount=0`・`gpuRequirementMet=true`。replay 2188957 |
| `r18e-shape` | `c225f206…` | 同上 PASSED（black 0.003776, distinct 11321）。fast path 全 miss の診断 leg |
| `r18g-on` | `3963fb4f…` | 同上 PASSED（black 0.00377, distinct 13587）。hit 98.9 % |
| `r18h-off` | `3963fb4f…` | 同上 PASSED（black 0.00377, distinct 11905）。`enabled=false` の対照 |
| `r18b-entities-on` | `dfcc3b6a…` | entity variant は `UNCALIBRATED_PRESENTED_IMAGE`（scene variant の floor が無い）。**validation 0・例外 0 で描画は継続**するが画像判定は無い |

全 leg で `modelDrawRefusals=0`、`at org.soutalab.celtium` の stack trace 0、VUID 0。

## 言えないこと

- **FPS の勝敗ではない**。`plain` scene の frame p50 は leg ごとに 87.7 / 304.5 / 1657.9 ms と大きく動く
  （chunk 構築が支配的）。同一 JAR の対照 `r18h-off` は replay 195,329（on 側 2,149,974 の 9 %）しか描いておらず、
  同じ scene の対照になっていない。よって on/off の frame 差は測れていない。
- `402 ns/replay` と `626 ns/replay` の差は **別 build・別 run**であり、計測区間に `batchTimings` の nanoTime 2 回を含む。
  同じ JAR・同じ scene での on/off は次 round の leg（同一 seed で replay 数が揃うまで再試行）に持ち越す。
- Intel 未実行。menu/GUI stage は対象外（`pictureStage=world`）。
- entity variant（`keepEntities`）は floor が無いので、entity の絵の受入には使えない。

## 次の判別

1. on/off を同じ JAR で比べるには、**replay 数が揃った 2 leg** が要る。`plain` の chunk 構築差が原因なので、
   同じ実時間で同じ world 状態に到達させる（`pinSections`/`minWorldFrames`/`screenshotAtTick` を固定し、
   最初の 60 frame ではなく「world 定常」の区間で `modelEnqueueMillis` を比べる）か、
   in-leg で hit/miss それぞれの ns を出す計器（`noteEnqueue` を 2 本に分ける）を足す。
2. hit 率は scene 依存である: `inputs` miss が 21k あったのは entity の向きが動くため。静的な TESR だけの scene では
   さらに上がるはずで、`lastShape`/miss 内訳がその scene の性質を言う。
3. この経路は **record の matrix しか書かない**ので、次に削れるのは record 書き込みそのもの（256 B/instance、
   `VulkanModelInstances.writeInstance`）か、`modelStats.noteReplay` の 2 本の 16-float コピーである。
