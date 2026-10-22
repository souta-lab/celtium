# Celtium — native所有権への転換

2026-10-21、利用者の全面変更許可に基づく再設計。**唯一の描画制約はnative VK-only、GL変換はMod専用**。
これは設計であり、現在の達成度は [plan.md](<plan.md>)、製品の出口は [goal.md](<goal.md>)。
同日早朝のS0–S5固定計画、S0以外の実装禁止、SSBO/multi-drawの義務付けは本書で置き換える。

## 1. 捨てる前提

- 古いKPI、hotspot不在、per-call最適化の余地なし、GC/GPU/同期の根因断定を設計入力にしない。
  source・artifact・区間が一致した比較から再構築する。同じJARの再測定は変更のA/Bではない。
- 「nativeという名前の入口」「GL到着counterが少ない」「validation clean」は境界の証明ではない。
  旧 `CeltiumNativeDraw` はスタブを直接呼んでいた。GPU実行がVulkanでもfrontendの所有権はnativeになっていなかった。
- **論理draw数、Vulkan command数、render-pass数、submit数は別物**。どの数も単独の最適化目標にしない。
  2件なら必ずindirect、十数pass以内、CPU bake禁止、全計器完成まで実装停止、といった固定則を置かない。
- 既存の巨大backendを横に分割して相互に数百getterを生やすだけでは設計改善ではない。
  正しさを握るownerと契約を移す。移行接続のbackend変更は許すが、新しい資源所有者を中へ埋め込まない。

## 2. 設計軸：GL呼出しの変換ではなく、更新頻度と所有権

```text
Minecraft / Forge API                      Modのraw GL
  ├─ chunk CPU mesh / model / VBO              │
  ├─ Tessellator / GUI / glyph                 ▼
  ├─ texture / framebuffer / post          compatibility adapter
  └─ state / matrices / queries                │
               └──────────┬───────────────────┘
                          ▼
       typed native resources + immutable draw-state / target generations
                          ▼
       ordered frame records + resource dependencies + bounded transient arena
                          ▼
       native pass execution / pipeline & descriptor cache / submission scheduler
                          ▼
       Vulkan queue / swapchain / completion epochs → resource retirement
```

矢印は一方向。**native側がGL互換層へqueryして状態や資源を取り戻す構造を終わらせる。**
共通のnative state/resource ownerをfrontendとMod adapterが更新し、必要なGL queryだけadapterがそこから答える。
既存CPU chunk engineは使える部分を残すが、そのGL buffer/staging APIをnative転送の主入口にしない。
CPU engine自体の置換も、機能・仕事量・throughputの証拠で有利なら許す。

### 資源ごとの扱い

| domain | 所有と更新単位 | 実行時の契約 |
|---|---|---|
| 地形 | section/region mesh世代、dirty range、upload ticket | 変更分だけ転送。可視listとgeometry lifetimeを分ける。不要なcopy/waitをなくす |
| 保持型geometry | public VBO、モデル、固定GUI等の内容世代 | upload/rebuild時だけ頂点・indexを作る。各drawは既存meshとstateを参照 |
| transient geometry | glyph、動的GUI、particles等のframe-local領域 | 範囲と寿命を明示。毎drawのheap objectやbuffer確保を避ける |
| texture/material | image世代、sampler、descriptor世代 | bound/recordedなdescriptorを途中で書換えない。reload時は旧世代をretire |
| render target | color/depth attachment、clear/load、layout、サイズの世代 | 複数wrapperが同じimageを指すことと、別世代への差替えを区別 |
| frame/submission | recording、submission packet、fence/sequence | 記録受理とqueue送信とGPU完了を別状態にする |

資源IDはopaqueなnative identity。Modが数値handleを保存・復元しても同じ資源を指せるようadapterにviewを持たせるが、
native drawがGL名検索・client-array decode・GL storage mirrorを経由する必要はない。

### draw-stateと順序

- 描画を受理した時点のmatrix、texture世代、blend/depth/stencil/cull/color mask、fog/lighting、viewport/scissor、targetを保持する。
  後でglobal stateを読み直して別の状態で実行しない。target切替、clear、copy、readback、Mod callbackは必要な順序境界。
- UIとtranslucentの観測順を保持する。不透明物の並べ替えも、同じresource依存と描画結果を保証できる範囲だけ。
  粗い `(format, texture, blend, matrix)` キーだけではcolor mask・clip・destination等が落ちる。
- batchingはこの契約の上の実行選択。小さいtransient頂点のCPU連結、retained draw、instancing、indirect multi-drawのどれも候補。
  SSBOのmatrix配列はCPU/GPUの実費と対応featureを両GPUで比較して採否を決める。
  **描画が1件でも資源再利用で改善できるし、draw数が減っても総時間が悪化しうる。**

## 3. 最初の実装単位：VertexBufferのnative資源化

[VertexBufferMixin](<../forge122/src/main/java/org/taumc/celeritas/mixin/core/VertexBufferMixin.java>) が
Minecraftのpublic `VertexBuffer` の生成・bind・upload・draw・unbind・deleteを所有する。
Vanillaのsky/stars/horizonとForge cloudの保持型データを、GL buffer storeの再decodeから切り離す。

- [NativeGeometryBuffer](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/NativeGeometryBuffer.java>)：
  callerのremaining bytesをupload時にコピー。CPU内容とnative identityを所有し、device再生成に必要な内容を保持。
- [RetainedGeometryCache](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/RetainedGeometryCache.java>)：
  deviceごと・内容世代/primitive topologyごとのGPU mesh。毎frameの頂点copyをしない。
  現在のmesh memoryはmapped host-visibleであり、device-local配置へ移行済みという意味ではない。
- [LeasedResource](<../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/LeasedResource.java>)：
  cache ownerとは別に、受理したdrawがleaseを持つ。upload/deleteでownerを外しても、未記録・記録中・in-flightのdrawは生存。
  group破棄か対応sequenceの完了後にleaseを解放。**「2回waitしたから安全」とは判定しない。**
- retained drawは前後のtransient groupと混ぜずsealする。同じmeshをdepth-only/colorなど異なるstateで再利用し、頂点をappendしない。
- Modがpublic VBO APIとraw GLを混用する場合はadapterで同じidentity/内容を解決する。
  bind/query、data/subdata、transient map/unmap、deleteのCPU契約をテストする。persistent mappingはここでは広告しない。
- 形式はstrideだけで推測せず、element・offset・UV indexをupload時に解決。未知形式は無言で誤描画しない。

**限界**：この単位はnative frontend全体の完成ではない。既存state、texture、FBO、モデル入口には旧GL couplingが残る。
共通backendのstate snapshot/ordered queueを移行用に再利用しており、まだnative側からの互換state参照を全廃していない。
この残りを完了扱いせず次の移行境界にする。

## 4. 次に移すowner（固定フェーズでなく、依存関係）

| 未完領域 | 直近の判別・変更 | 出口 |
|---|---|---|
| native state | GlStateManagerのproducer/query、raw GL adapterのconsumerを同一native ownerへ | 通常vanilla/Forge stackにadapter逆呼出しなし。state push/pop・query・順序テスト |
| texture / target | TextureManager、TextureUtil、Framebufferのpublic lifecycleと世代をnative化 | reload/resize/Mod FBOの旧世代がqueued/recorded/in-flightでも安全。pixel対照 |
| モデル / glyph / GUI | モデル内容の保持、native material、transient arenaを共通寿命契約へ | display-list互換の再decodeや二重copyが主経路にない。4画面・entity/TESR対照 |
| frame execution | native resource依存からpass/barrierを編成。旧GL handoff分岐を主経路から除去 | 記録/送信/完了が一致。failure/retry/world切替/長時間で欠落・VUIDなし |
| terrain upload | dirty bytes・copy・submit・可視判定の実費をwarm/streamingで分離 | 機能同一でtransfer/CPU/tailを改善。worker→render ownerの引渡し明示 |
| grouping方式 | native資源/state境界が安全な範囲でmerge/instancing/MDIを比較 | 全仕事量と画像を揃えたpaired測定で利益。draw数だけを出口にしない |
| 互換・影・BSL・配布 | 完全packの代表場面、native shadows、shaderpack effect、配布導線 | [製品完成条件](<goal.md>)の実機証拠 |

計測修復は各変更と並行して行う。JARを固定できない・区間が空・画像が比較不能なら**性能の結論を出さない**のであって、
依存しない正しさの修正や資源ownerの移行を一律停止する理由にはしない。

## 5. 測定と撤回の運用

- hash固定のfresh stageとbuild provenanceを使用。warm区間を採るにはfixtureの早期finishを `holdUntilTick` で延ばす。
- 完全な区間・十分なsamples・両armの同じscene/camera/featuresを確認。最低3ペア、順序を交互にしてpairごとのratioを見る。
- correctness runはvalidation ON。performance runは計測負荷を明記し、CPU/GPUの仕事とframe pacingを混同しない。
- partial draw counterを全面の仕事量にしない。新しい計器も必要なら追加するが、所有範囲と陽性対照を持たせる。
- 既存実装・外部設計は比較材料。最適解を宣言して固定せず、製品の観測可能な契約と測定から選ぶ。

## 6. 受理経路の再設計：offerは範囲を記録し、group境界はflushで決める（2026-10-22 設計・未実装）

### なぜ
- フレームは **CPU律速**（640x360でも1.863ms、1280x720の1.85msとほぼ同じ／`CELTIUM_LEG_WINDOW_SIZE`）。
  したがってGPU側のフィルレートやシェーダを削っても AMD の差は動かない。
- 受理経路はフレームの **26%（0.48ms）**、**279 offer/frame** なので **1.7µs/offer**。Celeritas の1バッチは0.2-0.3µs。
- しかし**名前の付いた内訳を1つずつ消しても wall時間が動かない**: capture 79%減→0.7%、prepare 26%減→0.0%、
  行列比較 100%減→0.0%、インデックス再ベース全廃→上限0.5%。
  ⇒ コストは**group/mesh構造へのキャッシュ・メモリ局所性**に散っており、アトリビューションに名前で出ない。

### 現在の流れ（実コード順）
1. `WorldVertexBufferUploader` の mixin → `submitImmediateBatch` → `submitImmediateBatchBody`
2. `celtium$mergeRecordedDrawState()` → `celtium$captureState()` → `celtium$stateKey()`
3. merge判定（format/texture/stateKey/pass/scissor/pipeline/descriptor/matrix/colour/affine/fog/light）
4. 通れば **その場で** `VulkanDynamicMesh` へ追記: 頂点 `memCopy`、インデックスは `baseVertex` 再加算、カウンタ更新、`sealFrameSlice()`
5. 切れれば `ImmediateGroup` を作り直し、mesh を group に紐付け
6. flush で group を回して記録（`wrecLoop`）

### 変更案（および、そのうち実装できない部分）
- offer時は**(sourceAddress, stride, count, drawMode, formatId, textureId, stateKey, pass, scissor, matrixCopyId)** を
  追記専用の配列に書くだけ（数十ns）。group構造・mesh・descriptorには**触れない**。
- group境界判定は現行の述語を**そのままflushで評価**（受理時の値は配列に保存済みなので意味は変わらない）。
- **頂点の転写（`memCopy`/indices）は遅延できない**（2026-10-22 実装前提の確認で判明）。
  `WorldVertexBufferUploader`/`Tessellator` が渡す `vertexAddress` はゲームの `BufferBuilder` の内部バッファで、
  **次のdrawで再利用される**。受理時にコピーしないと中身が別のバッチで上書きされ、flush時には壊れている。
  ⇒ 転写は受理時に残す。**遅延できるのは境界判定とgroup生成だけ**。
- したがって §6 の見込み（5%）は、**境界判定の分だけでは届かない可能性が高い**。判定自体は既に安い（実測）。
  この設計は「per-offerでgroup構造に触らない」ことが主目的で、**転写はそのまま残る**。

### 保たねばならない不変条件（実コードで確認済み）
- `state[]`は受理時のsnapshot（groupが所有）。配列に `state` のコピーを持たせるか、`stateStamp`方式で参照を固定する。
- 行列は**受理時の値**（`glProjection`/`glModelView` のコピー）で判定する。後から書き換わるので参照を保持しない。
- `textureId` は受理時のbound name。flush時点のboundとは違い得る。
- scissor矩形・polygonOffset・fog・affine・colour・light は受理時の値（現行もgroupにcaptureしている）。
- 描画順は受理順を厳守（並べ替え禁止）。

### 測り方
- 判別条件: **flushの1パス化後、`warm p50` が 5%以上下がる**（1%は本機の分解能以下）。
- 制御arm付きKPI（同一jar・交互3ペア）で判定し、`scripts/gv-kpi.sh` の `medianControlRatio` を使う。
- 画質は通常のleg（validation ON・画像gate）で確認。

### 長い尾（2026-10-22 追加の実測）
- 受理経路の1.7µsは**数十個の小さな操作（各5-50ns）に散っている**。例: announce経路はoffer毎に
  `announcedImages.get(id)`（boxed HashMap）、pending時はさらに `announcedGeneration`/`consumedGeneration` の
  boxed get 2回と `Integer.equals`（`regAnnouncedSkips=2,043,897` ≒ 197/frame）。
- 本機の分解能は**1-2%（1 offerあたり5-10ns）**なので、**個別の5-30nsは測定できない**。
  ⇒ 次は「1つ消す」ではなく、**hot pathのboxed map・冗長チェックをまとめて掃除するsweep**として設計し、
  合計が5%を超える形で一度に測る。

### リスク
- `state`/matrixのsnapshotを配列側に持つため、**メモリ使用量が増える**（279 offer × 数百バイト/フレーム）。
- 転写をflushへ遅らせると、**arenaの寿命**がgroup閉じまで延びる。`LeasedResource` のlease契約に合わせる必要がある。
- 途中で例外が出た場合の部分適用（記録だけしてflushしていない状態）をどう巻き戻すかを決める必要がある。

## 7. 受理経路のコストは「長い尾」で、Java側の書き換えでは届かない（2026-10-22 実測の総括）

### 数字
- フレームは **CPU律速**（640x360でも 1.863ms、1280x720の 1.85ms とほぼ同じ）。
- **279 offer/frame、183 group/frame（1.5 offer/group）**、受理経路はフレームの **26%（0.48ms）**＝**1.7µs/offer**。
- Celeritas の同等処理（`glBufferData`＋`glDrawArrays`）は **0.2-0.3µs/offer**。
- 現行比: **AMD 1.408x / Intel 1.939x**（0.7.11-alpha + Java 27、jar `c322d70e…`、3ペア交互順）。

### 何を消しても動かなかったか（すべて実測・制御arm付き）
| 消したもの | 削減率 | wall時間 |
|---|---|---|
| state capture | 79% | 0.7%（誤差） |
| prepare のルックアップ | 26% | 0.0% |
| 行列比較（32 float×2） | 100% | 0.0% |
| インデックス再ベース | 100%相当 | 上限0.5% |
| 診断側の `synchronized`＋文字列 | 100% | 測定不能（<1%） |

⇒ **名前の付いたコストの合計は約0.7µs/offer で、残り約1µsは名前の無いもの**（オブジェクト生成・分岐・
キャッシュミス・呼び出し規約）。個々は5-50nsで、本機の分解能（1-2%＝5-10ns/offer）以下。

### 生き残っている方針は2つだけ
1. **hot pathをnativeへ移す**：accept経路の周辺（状態の取り出し・キー計算・述語・descriptor解決）を
   FFM/JNIの1回の呼び出しにまとめ、Java側は引数を並べるだけにする。現在の `CeltiumGlStub` が
   同じ手法でGL呼び出しを1回のtrampolineに畳んでいるので、前例はこのコードベース内にある。
   見込み: 1.7µs → 0.6-0.9µs（Java の per-offer 記帳が消える）。**ただし実装量が大きく、1ラウンドでは終わらない。**
2. **capture モデルを変える**：`WorldVertexBufferUploader` 単位ではなく、`BufferBuilder` が書く
   バッファそのものを所有してしまう（ゲームが我々のメモリに書く）。per-offer の転写が消え、
   状態は「描画時に1回」で済む。**ただしMinecraftの内部に踏み込むため互換リスクが高い。**

### やらないこと（実測で否定済み）
- group数を減らす最適化（コストはgroup数ではなく**offer数**に比例。1.5 offer/group）
- GPU側のフィルレート/シェーダ最適化（CPU律速なのでAMDには効かない）
- boxed `Map<Integer,X>` → fastutil（**2回とも実行時 `NoSuchMethodError`**。このコードベースでは不可）
- 1%未満の単品削減を「改善」として報告すること（分解能以下）

## 8. 影のキャスタ捕捉を地形パス内へ移す（2026-10-22 設計・未実装）

### なぜ
影の描画側は完成している（マップ生成 `size=2048`・shaderc不要の埋め込みSPIR-V・地形1バッチの描画提出・
レシーバの実マップバインド・シェーダは3x3 PCFで0.2倍まで暗くする）。しかし**絵の差は mean 0.17**で見えない。
原因は捕捉側: `captureShadowTuple` がフレーム境界で**1回**呼ばれ、`TerrainRenderer` も**最初の1バッチ**しか保持しない
（`if (this.shadowIndirectCount == 0)`、`TerrainRenderer:554`）。影マップの大半がクリア深度のままなので、
深度テストが「lit」を返し暗くならない。

### 素朴な修正が壊れる理由（実測済みの失敗）
「範囲のリストを貯めて全部描く」だけでは足りない。`TerrainRenderer` は indirect の範囲と頂点/インデックスバッファを
**同一瞬間に**取っている（`#397`）。範囲だけを貯めてバッファをbind地点で取ると、**リージョンAのindirectコマンドが
リージョンBの頂点を指す**。過去に counts を累積して offset を固定した版は `drawCount 3246 at offset 800` で
`VUID-vkCmdDrawIndexedIndirect-drawCount-00540` を出した（leg shadowTerrain5）。
さらに影の描画には **pipeline・descriptor set・dynamic offset・224バイトのpush block** も要り、
これらは**バッチ毎に違う**ので、境界時点の値を使うと同じ混在が再発する。

### 変更案
1. `TerrainRenderer` の indirect 記録ループ内で、バッチ毎に
   `(offset, count, vertexBuffer, indexBuffer, descriptorSet, dynamicOffset, pushBlock)` を**1つの不変レコード**として
   リング（上限64）に積む。押さえるのは記録のその瞬間。
2. backend は境界で**リングを丸ごと**消費し、`ShadowMapPass.Batch` をバッチ数だけ作る。
3. 影マップは1フレームに1回だけクリアする（バッチ毎にクリアしない）。

### 不変条件
- 1レコードの全フィールドは**同一記録時点**のもの（範囲とバッファと状態を混ぜない、#397）。
- リングはフレーム境界で必ず空にする（次フレームの記録に持ち越さない）。
- 上限超過は**落とした数を数える**（黙って捨てない）。
- 影の描画は既存の own-submission 経路（フレームのrender pass外）を維持する。

### 判別条件
- `CELTIUM_SHADOW_OWN submitted batches=N` の **N が terrain のバッチ数に近づく**こと。
- 影ON/OFFの presented 画像差が、ログ帯外で **mean 1.0 以上**（現在0.17）になること。
  0.2倍まで暗くするシェーダが入っているので、入力が揃えば桁で変わるはず。
- validation clean（`VUID-vkCmdDrawIndexedIndirect-drawCount-00540` を再発させない）。

### リスク
- バッチ毎の descriptor set を保持すると、セットが再利用/退役したときの寿命管理が要る（`#401` の退役規則に従う）。
- 64バッチ×push block 224B＝14KB/フレームのコピーが増える（CPU律速なので無視できない量。計測してから上限を決める）。

### 8.1 行列の当たりを取る（2026-10-22 時点の観測と次の一手）

キャスタ側は整っている: `recordTerrainBatches` は `lightSpaceBlock(batch.pushBlock)` を作り
（`ShadowMapPass:611`/`866`）、地形のパイプラインレイアウトでpushして `vkCmdDrawIndexedIndirect` する
（同:616-620）。つまり**マップには光源空間の深度が入る**。
レシーバ側は `eyeSpaceLight = lightVP * inverse(modelView)` を即時系へ、
`lightViewProjection`（world空間）を地形系から読む2系統がある（`VulkanTerrainGpuBackend:15063-15072`）。

**疑い**: 地形の頂点は**カメラ相対**（Celeritasの`push`ブロック）で、マップは**world空間の光源行列**。
地形シェーダがカメラ相対位置をworld行列で投影しているなら、ルックアップは定数オフセット（カメラ位置）だけ
ずれる。同じ理由で「94%の画素がマップ外」という過去の観測とも整合する。

**次の判別手段**（推測で行列をいじらない）:
1. 地形シェーダ（`terrain_solid.frag` の shadow 部）が、どの座標にどの行列を掛けているかを読み、
   **カメラ相対→world変換**（`+ cameraPosition`）が入っているかを確認する。
2. 入っていなければ、`lightVP` の前にカメラ平行移動を掛けた行列を**第3のブロック**として渡し、
   地形レシーバだけそれを読む。
3. 計器: マップ座標のin/out比をGPU側で数えるには、シェーダに
   `atomicAdd` するSSBO（in/outの2カウンタ）を1つ足すのが最小。CPUでは再現できない。

**判別条件**: in比が支配的（>80%）になり、かつ影ON/OFFの presented 差がログ帯外で mean 1.0 以上。

### 8.2 地形行列のカメラ補正は測って効かなかった（2026-10-22 試行→revert）

`terrain_solid.vert:229-237` を読むと、シェーダは `v_ShadowCoord = u_ShadowMatrix * vec4(worldPosition,1.0)` を計算し、
`worldPosition` はその後**カメラ相対の model view**で描画に使われる。そこで
`lightVP * T(centre)`（`centre`＝mirror modelviewの逆行列の平行移動）を渡す修正を実装した。

**結果**: 影ON/OFFの差はログ帯外で **mean 0.17 のまま**（変わらず）。leg `shadowcam`（jar `c02eb627…`）は PASSED・
validation clean。効果が無く、`worldPosition` が既にworld空間なら**二重変換のバグ**になるため**revert**（jarは`046b8bf2…`に復帰）。

**配線の確認（2026-10-22、消去法）**: 「行列が未バインド」という候補は**否定された**。
- `terrainPipeline.setShadowSources(view, sampler, matrixBuffer)` が呼ばれ（`VulkanTerrainGpuBackend:15090`）、
- `VulkanTerrainPipeline` は bind 時に **binding 3（マップ）と binding 4（行列）**を `shadowView`/`shadowUniform` から書く
  （`VulkanTerrainPipeline:699-729`、`SHADOW_MAP_BINDING=3`/`SHADOW_UNIFORM_BINDING=4`）、
- 行列は毎フレーム `matrixBuffer` のオフセット0に、eye空間版はオフセット64に書かれる（`ShadowMapPass:63-72`）＝シェーダの読み手と一致。
- 即時系も binding 2（行列）/3（マップ）で同じ規約。

**したがって残る未知は「マップの中身」か「座標のin/out比」**のどちらかで、これは画素に依存するため
**GPU側の2カウンタSSBO**（§8.1の3）でしか決まらない。CPU側の推論はここで尽きた。

**これで言えること**: 地形レシーバの行列は（少なくともこの変更の範囲では）律速ではない。
次の候補は (a) `worldPosition` が実際にworld空間かどうかを読み切る（`translation` の作られ方を追う）、
(b) 地形シェーダの `u_ShadowMatrix` が**書かれている場所**（binding 4 のUBO）が実際に更新されているかを確認、
(c) GPU側のin/outカウンタ（§8.1の3）。推測で行列をいじる段階は終わり、**観測を足す段階**に入っている。

## 9. shadercのはLWJGL版に固定される（2026-10-22 判明・同梱は単純ではない）

### 事実
- `celtium-vulkan/build.gradle` は `compileOnly 'org.lwjgl:lwjgl-shaderc'`、テストは3.4.3に揃えた（2026-10-22）。
- `forge122/build.gradle.kts` は **LWJGL 3.4.1** でコンパイルする（`compileOnly("org.lwjgl:lwjgl:3.4.1")`、validationも3.4.1）。
- 実行環境のLWJGLは**ローダ次第**で、0.7は 3.4.3、0.6は別版。
- 実測: 3.4.1のshaderc jarを3.4.3ランタイムに置くと
  `ClassNotFoundException: Found org.lwjgl.util.shaderc.Shaderc in invalid classes` で**ロードに失敗**する。
  3.4.3を置くと通る。

### だから何が言えるか
shadercのバインディングは**LWJGLの版に固定**されており、「modに同梱すれば解決」ではない。
同梱した版が実行時のLWJGLと食い違えば、**同梱しないより悪い**（無い場合の graceful degradation が、
「クラスはあるが壊れている」状態になる）。したがって選択肢は:

1. **パック利用時のみ、実行時LWJGLに合うshadercを要求する**（instance側の依存として文書化）。
   影は既に**埋め込みSPIR-V**でshaderc不要にしてあるので、影響はパックだけ。
2. **per-version の同梱**（3.4.1用・3.4.3用を両方入れ、実行時に選ぶ）。jarサイズと
   ネイティブのパス解決（`<os>/<arch>/...` が衝突）の問題があり、雑にやると壊れる。
3. **shadercを使わない**（glslangの外部プロセス、またはパックのSPIR-Vを事前生成）。配布の複雑さは増すが版依存は消える。

### 現状の判断
影は回避済み。パック（BSL）は②か③が必要で、①は利用者への依存追加になる。
**「shadercを同梱する」を単純な作業として計画に置かない**。

## 10. 影：キャスタとレシーバの射影が一致しない（2026-10-22 計器で確定）

### 確立した事実（すべて同一ビルド・実測）
1. **影マップの充填率は12.2%**（全体読み戻し: `mean=0.938 / belowOne=0.1219 / max=1.0`）。
2. **レシーバがキャスタのテクセルを踏むのは4画素**（in範囲48,319画素中の0.0%）。
   塗り分け計器: 緑=深度あり / 赤=クリア値 / 青=xy範囲外。
3. **ルックアップ自体は有効**: 地形画素451,692のうち**75.4%がin範囲**、`z` は常に `[0,1]`（`z<0` と `z>1` は0画素）。
4. **マップ深度は正しいスケール**: 中央ブロックで `0.500-0.514`（光源中心＝実カメラ (217.5,48,217.5)、`cameraDepth=0.5`）。
5. **キャスタのpush blockは正しい形**: `lightSpaceBlock` は第1スロットに光源行列、第2スロットに**identity**を書く（確認済み）。
6. それでも**判定は全域「lit」**（影と判定されたのは119画素）。

### したがって
両者は「同じ行列・同じpush block・同じindirect range」を使う設計なのに、**書いた場所と見に行く場所が一致しない**。
残る候補は「行列の外側」にある:

- **`_draw_id` の供給**: 地形シェーダは `_get_draw_translation(_draw_id)` でセクション座標を得る。
  間接パケットは `firstInstance = 0` を書いており（`TerrainRenderer:519` 付近）、`_draw_id` が
  `gl_InstanceIndex` 由来なら**全ドローが同じ翻訳**になる。メイン経路が正しく描けている以上、
  `_draw_id` は別の供給元（uniform配列）のはずだが、**影パスの描画でその供給が同じかは未確認**。
- **キャスタが実際に描いている Geometry**: 4バッチのみ・それぞれ1 regionの範囲。レシーバの75%がin範囲でも、
  キャスタが別セクションを描いていれば重ならない。

### 2026-10-22 に消去した候補（コードを読んで否定）
- **`_draw_id` の供給**: 地形シェーダの `_draw_id` は `packed_draw_params`（**per-vertex属性**）の
  `(packed >> 8) & 0xFF` で決まる（`terrain_solid.vert:223`）。uniformでもinstance indexでもないので、
  **キャスタとレシーバで同じ頂点なら同じ値**になる。間接パケットの `firstInstance=0` は無関係。
- **Y反転**: メインの地形パスは `height(targetHeight)` 正値（`VulkanTerrainGpuBackend.setViewportAndScissor`）、
  影パスも `height(SIZE)` 正値（`ShadowMapPass:625`）。**どちらも反転していない**ので上下ミラーではない。
- **region offset**: バッチとともに `block.clone()` で捕まえており、レシーバ側も同じ `u_RegionOffset` を使う。

### 次の判別手段（推測を避ける）
1. **キャスタ側のシェーダに同じ判定を出す**: 影マップへ描く際の `gl_Position` を、`-Dceltium.shadow.debug` のとき
   フレームバッファへも書く（または影マップの代わりに画面へ描く）と、キャスタの被覆が1枚で見える。
2. **`_draw_id` の供給を1行ログ**（メイン経路と影経路で同じ値か）。
3. 上記で一致が確認できたら、次に深度の比較（bias・近平面）へ進む。

### レシーバ側のバインドは毎回書かれている（2026-10-22 確認）
`VulkanTerrainPipeline.bindTextures(...)` は**呼ばれるたびに** binding 1-4 を書いており、
`shadowView`/`shadowSampler` が 0 でなければ影マップを、0 ならライトマップを差し込む。
`setShadowSources` は `publishShadowSources` から毎フレーム呼ばれる（`celtium.shadow.noSources` が無い限り）ので、
**「初回に作った set が古いまま」という候補も消えた**。

### さらに消去（2026-10-22）
- **頂点モジュール**: キャスタは `terrain.vertexModuleName()` のモジュールをそのまま使う（`ShadowMapPass:491`）＝
  レシーバと**同一のシェーダ**。vanilla変種ではない。
- **uniform（`u_RegionOffset`）**: キャスタは `batch.descriptorSet` を `batch.dynamicOffset` で bind する＝
  レシーバと同じ領域ブロックを読む。

### 行列の同一性を実測で確認（2026-10-22）
キャスタが実際にpushする行列（`lightSpaceBlock` が `matrixBuffer` から写す値）と、
レシーバが地形を描いた時点の `matrixBuffer` を**両方そのまま出力して比較**した:

```
CELTIUM_SHADOW_MATRICES same=true
  receiver=[0.011265784,-0.013470465,-0.0018682767,0.0 ... 2.3841858E-7,0.0,0.50000006,1.0]
  caster  =[0.011265784,-0.013470465,-0.0018682767,0.0 ... 2.3841858E-7,0.0,0.50000006,1.0]
```

**両者はビット一致**（`frameId>400` のフレームで確認）。`matrixBuffer` は地形パスの時点ではまだ前フレームの値
（公開はフレーム境界）だが、カメラが静的なら同じ値になる＝**1フレームのラグは無害**。

**計器の失敗2件も記録**: ①最初は「キャスタの生push block」と比べて `same=false` になったが、
キャスタの行列は**記録時に変換される**ので比較対象が誤り。②次に一発だけスナップショットしたら**フレーム0**
（行列がゼロ）を掴んだ。③`frameId>400` に限定して初めて意味のある比較になった。

### 残るデバッグ手段（この粒度の計器では追えない）
ここまでで消した候補: `_draw_id`、Y反転、region offset、深度remap、行列の合成、光源中心、
descriptor の更新漏れ、バッチ数、マップの充填とスケール。
**残っているのは「キャスタが実際にどこへ描いているか」だけ**で、これは数値の計器ではなく
**捕獲ツール（RenderDoc / gfxreconstruct）で影マップの描画を1フレーム捕まえる**のが最短。
無い場合は、キャスタの `gl_Position` を影マップではなく**フレーム画像へ**書く一時パスを作る
（影マップへの記録関数を流用し、attachment をフレーム画像に差し替える）。

### 影の不一致: シェーダ式とpush内容は同一（2026-10-22 追記）
コードを読んで確定したこと:
- レシーバ: `v_ShadowCoord = u_ShadowMatrix * vec4(worldPosition, 1.0)`（`terrain_solid.vert:236`）
- キャスタ: `gl_Position = pc.u_ProjectionMatrix * pc.u_ModelViewMatrix * vec4(worldPosition, 1.0)`（同:237）で、
  `lightSpaceBlock` は `[lightVP*T(centre), identity]` をpushする。**片方が単位行列なので積の順序に依存せず、両者は同じ式**。
- **`worldPosition = _vert_position + u_RegionOffset + _get_draw_translation(_draw_id)`** も共通。
⇒ 行列・式・頂点属性はすべて同一なのに、画像では**キャスタが x0.35-0.96 / y0.25-0.73 の塊**、**レシーバは左下(0.06,0.11)中心**。

**残る唯一の差は `u_RegionOffset` の供給**。キャスタは `batch.descriptorSet` + `batch.dynamicOffset` をbindするが、これは
**そのバッチを記録した時点の最後のリージョン**の値で、バッチが**複数リージョンを跨ぐ**なら大部分のコマンドでoffsetが食い違う。
⇒ **次の計器**: 1バッチが覆うリージョン数を数える（`regionsPerBatch`）と、キャスタの（descriptor/dynamicOffset）が
そのバッチの先頭リージョンと一致しているか**をログする。

### 2026-10-22 追記: descriptor/dynamicOffset も「記録時点のもの」と書かれている
`celtium$queueShadowBatches` の呼び出しは地形パス内（`11737`）で、コメントは
「the pipeline's set, dynamic offset and push block are the ones the batch was recorded with」と明言している。
`lastPushBlock` は**行列32個のみ**（`pushMatrices` が `2 * MATRIX_FLOATS` をコピー）で、`u_RegionOffset` は
descriptorのuniform buffer側にある。⇒ **入力はすべて「記録時のもの」で揃っている**はず。

**したがって残る可能性は間接コマンドの中身**: キャスタが描く範囲（`batch.indirectOffset`/`indirectCount`）が、
レシーバが描いた範囲と**ずれている**（別バッチ・別オフセット・別パス）。次の計器は
**両経路で `indirectOffset`・`indirectCount`・先頭コマンドの `indexCount`/`firstIndex`/`baseVertex` をログ**し、
同じ範囲を指しているかを確かめること。

### 触ってはいけないもの
`lightSpaceBlock` のidentity、`lightVP*T(centre)`、Vulkan深度remap（`cameraDepth=0.5` で検証済み）は
**いずれも実測で正しい**。ここを再度いじると、検証済みの土台を崩すだけになる。

## 11. 現状の総括（2026-10-22）— 次に何をするか

### 性能（同一jar・3ペア交互順・参照 `567dcf8a…`）
| jar | AMD RX 6400 | Intel UHD 630 |
|---|---|---|
| `e4622406…`（計器なし） | **1.373x** | **1.738x** |
| `c7dd20ad…`（計器入り＝HEAD） | **1.382x** | 未測定 |

### 差の所在（両GPUで別の原因、どちらも実測）
- **AMD**: 解像度非依存＝**CPU律速**。受理経路がフレームの26%（279 offer・1.7µs/offer）。
  個別の名前付きコストを消しても動かない（`capture` 79%減・`prepare` 26%減・行列比較100%減・`append`全部 いずれも0〜0.7%）。
- **Intel**: **ドライバ呼び出しの回数 × 単価**。回数はAMDと完全一致（window 5・groups 182・terrain submit 3 /frame）で、
  **ほぼ全CPUブラケットが一律 +0.1ms**（合計 +1.3ms）。Vulkan呼び出し自体は地形ブラケットの1割以下。

### 残るレバー（2026-10-22 算術で順序を訂正）
**まず「groupをまとめる」路線は棄却**: `grpDraw` は Intel 0.3ms / AMD 0.1ms、groups 182/frame ⇒ 1groupあたり
1.65µs / 0.55µs。**groupを半減しても Intel 4.8%・AMD 3.1%** にしかならない。受入に必要なのは
**AMD 29%（0.47ms）・Intel 42%（1.33ms）** なので、この路線では到達しない。
作成地点censusで原因も判明: group開始182/frame のうち **state-key差 49/frame（27%）**、texture 24/frame（13%）、
closes 55/frame。単項の診断フラグ（texture/format/state）は**同時に変わるため効果ゼロ**と実測済み。

1. **受理経路（AMDの26%＝0.42ms）の廃止またはGPU側移設** — AMDの差のほぼ全部がここ。1.7µs/offer × 278/frame。
   名前付きコストを8通り削っても0〜0.7%だったので、**呼び出し回数かスレッド配置を変える**しかない。
2. **フレーム内の pass/window/submit 数の構造的削減**（Intelの1.33ms）— 差は全ブラケットに一律+0.1msで分散し、
   回数はAMDと同一（windows 5・terrain submit 3・groups 182）。`batchFrameSubmits` は Intel で2.8倍遅い（実測）ので、
   submitではなく**pass/windowそのもの**を減らす。
3. **シェーダパックをワールド描画へ接続** — 現状 `celtium$probePackProgram`（解析→数プログラムのコンパイル→プローブ描画）まで。
   BSLは mean 0.31 で絵が変わらない。**shadercはLWJGL版に固定**（§9）なので、同梱では解決しない。

### 到達可能性の算術（2026-10-22・判断材料）
JFR定常（1,215サンプル）とpassSplitから、受理経路を**構造変更で遅延させた場合の上限**を計算した:
- 遅延可能（本体 5.4% ＋ close 0.7% ＋ stateKey 0.7%）＝ **スレッドの6.8%**、フレームの**6.1%＝0.10ms**
- 遅延不可（capture 1.0% ＋ 頂点コピー 1.7% ＋ 配列比較 0.7%）＝3.4%
- 構造変更後のAMD: **1.63 → 1.53ms ＝ 比 1.32x**（受入は ≤1.0）
- パリティ（1.16ms）に必要な削減: **0.47ms ＝ フレームの29% ＝ 自分側スレッド仕事の59%**

⇒ **受理経路の内側で完結する変更では受入に届かない**。パリティには**Celtium自身の描画スレッド仕事を約6割消す**必要があり、
それは最適化ではなく**per-draw のJava仕事をほぼ全廃する再設計**（描画をGPU側で駆動し、offer ごとの capture/コピー/判定を
持たない形）を意味する。1セッションの最適化で埋まる差ではない、というのが現時点の結論。

### 11.1 出荷構成のフレーム内訳（2026-10-22 実測・jar `a78b1f53…`・影OFF）

leg `perf-struct-1`（AMD RX 6400・world・固定カメラ・`-Dceltium.vulkan.passSplit=true -Dceltium.vulkan.passSplitCpu=true`、
`CELTIUM_FRAME_PASS_SPLIT clock=cpu frames=512`）のp50:

| 区間 | p50 (ms) | 備考 |
|---|---|---|
| frame | **2.5** | 参照実装は約1.2 |
| world | 2.4-2.5 | |
| └ flush | 0.7 | **内側の winRecord 0.6** |
| └ terrain | 0.8 | 内側 terrainRecord 0.2・terrainSubmit 0.0 |
| └ gui / clouds / hand / particles | 0.1 / 0.1 / 0.0 / 0.1 | |
| world の残差 | **約0.8** | bracket されていない世界パスのCPU仕事 |

**読み方**: 我々の記録仕事（winRecord＋terrainRecord＝0.8ms）と、未bracketの世界パス仕事（0.8ms）が二大項目。
受入（≤1.0）には frame 2.5 → 1.2 が必要で、**記録仕事の削減だけでは足りない**（§12 の結論と同じ）。

### 11.2 到達経路: GPU駆動の描画リング（§12 の「置き換え」の具体形）

§12 の結論（per-offer の Java 仕事をほぼ全廃）を、次の形で実装する。

1. **record リング**: host-visible・永続マップの固定長レコード配列（例 64B/record、8192 records/frame）。
   GLスタブは offer ごとに「状態index・descriptor index・頂点範囲・index範囲・push定数index」を**書き込んで index を1つ進める**だけ。
   Java のオブジェクト生成・比較・分岐は offer 経路から消える。
2. **状態表**: push定数・descriptor・pipeline を**フレーム先頭で一度だけ**配列に書き、record は index で参照する。
3. **実行**: 1パスにつき `vkCmdDrawIndexedIndirect` を**1本**発行（`VkDrawIndexedIndirectCommand` は record からGPU側で組むか、
   CPUで1回だけ詰める）。group 概念は「連続する同一状態の範囲」に退化し、bind は範囲先頭でのみ行う。
4. **計測**: `winRecord`/`terrainRecord`/残差を同じ bracket で測り、record 数（offer数）と bind 回数を併記する。

移行は**immediate（window）経路から**行う（最大の 0.6ms）。terrain は既に indirect なので、record 形式を共有したうえで後段で移す。

### 11.3 `winRecord`（flush 0.7ms の内側 0.6ms）の内訳（2026-10-22・JFR）

出荷構成のJFR（steady 1,020サンプル）で、`recordImmediateOnlyPass` を含むサンプルは **43（4.2%）**。
その先頭フレーム（＝実際に時間を使っている場所）は:

| 子フレーム | サンプル |
|---|---|
| **HashMap系（treeifiedバケット5＋`VulkanTextureRegistry.get` 3＋`LinkedHashMap.get` 1）** | **9（21%）** |
| `drawPendingImmediate` | 7 |
| `transitionPublishedFrameTextures` | 2 |
| JNI呼び出し（`callPV`/`callPPPV`） | 3 |
| `ThreadLocal`／`String.<init>`／`pairLayoutSummary` | 各1-2 |

⇒ **記録ループがgroupごとにテクスチャを再解決している**（`celtium$registry().get(group.texture)`）のが最大の内訳。
これは「準備後にラッパーが退役した場合」のための健全性チェックで、レジストリは記録中に変化しない。

**試作**: 記録ループ内で直前のテクスチャ解決をメモ化（同一idの連続groupで再利用）。
**結果は 0.994x（中立）**で撤回。JFRの**帰属**は残るが、**呼び出し回数を減らすだけではフレームは動かない**という
§11/§12の結論と一致する（時間は次段の構造＝描画実行の持ち方にある）。

### 11.4 inclusive ランキング（2026-10-22・JFR steady 777サンプル・出荷構成）

自前コードの**インクルーシブ**占有率（スタックに現れたサンプル数）:

| メソッド | % |
|---|---|
| `CeleritasWorldRenderer.renderBlockEntityList`（fork由来＝バニラのblock entity描画） | **8.4** |
| `celtium$renderPass`（地形パス全体） | 5.7 |
| `render`（地形パスの入口） | 4.9 |
| `celtium$drawModelList` | 4.8 |
| `submitGeometryBatch` | 3.9 |
| `recordRenderPass` | 3.7 |
| `submitImmediateBatchBody` / `submitImmediateBatchTimed` | 3.7 |
| `flushPendingBeforeGlDraw` / `flushImmediateWindow` | 3.1 |
| `recordImmediateOnlyPass` | 2.2 |
| `ModelGeometryCache.build` ＋ `celtium$buildModelMeshes` | **2.1** |

**読み方**: 最大項は**バニラのtile entity描画**（参照実装も同じ仕事をする＝共有コスト）。Celtium固有で目立つのは
`celtium$renderPass`配下と受理経路の各3-4%で、**単一の大きな塊は無い**。参照との差0.45msが
「受理経路＋地形記録＋即時ウィンドウ記録」に分散しているという§12の結論と一致する。

**次の構造候補**（この順）:
1. `ModelGeometryCache.build` は**毎フレーム全リスト走査**（世代一致なら再利用だがループは走る）。世代の変化が無いフレームを
   int比較1回で抜ける形にする（キャプチャ側に単調増加の世代番号を持たせる）。
2. 受理経路の**頂点変換をGPUへ**（§12の2）: `copyVerticesTransformed` のper-vertex変換を push constant の行列に移す。
3. flush/winRecord（0.6ms）の記録ループを**1本のindirect列**へ（§11.2）。

## 12. 受入に届かせるには何を変えるか（2026-10-22・実測に基づく設計メモ）

### 測って消えた道（再調査不要）
| 試したこと | 結果 |
|---|---|
| 受理経路の名前付きコスト削減（capture/prepare/append/matrix compare/index rebase/stateKey 等） | **9件すべて中立（0〜0.7%）** |
| per-offer 診断（batch signature）の除去 | **1.015x＝わずかに遅い**（JITレイアウト） |
| group数の削減（bindless を含む） | **上限5%**（`grpDraw` 0.3ms÷182）で不足 |
| 単項のマージ緩和（texture/format/state） | **効果0**（項が同時に変わる） |
| テクスチャ参照キャッシュ（直接マップ） | 0.993x（誤差）＋無効化漏れのリスク |
| ウィンドウ機構の無効化 | 1.637ms（既定同等） |
| `flushPendingBeforeGlDraw` の冗長性 | 20回/frame中5回だけ実作業＝冗長ではない |
| モデル経路 | 既に世代キャッシュ（新capture分のみ） |
| テクスチャ変換（AWT） | ワールド入場時のみ（定常外） |
| Intelのバッチ提出 | 2.8倍遅い（ポリシー通り無効が正しい） |

### なぜ「1行ずつ速く」が効かないか
受理本体の **インライン仕事がleaf最大（5.4%）**で、子メソッドに逃がせる塊が無い。さらに**15項の比較を1個のlong比較に置き換える**
実験（stateStamp）は **1.005x＝中立**だった。つまり**判断コストそのものが主因ではない**。
残るのは「offer 1件あたりに必ず起きる仕事の総量」で、内訳は
**guard/統計/フィールド書込み（5.4%）＋capture（1.0%）＋頂点コピー（1.7%）＋配列比較（0.7%）**。

### 届かせるために必要な変更（順序と規模）
1. **offerを減らす**: 描画要求はゲームが出す（278/frame）。減らすには**同一フレーム内で同一状態の呼び出しをゲーム側でまとめる**必要があり、
   互換レイヤの範囲では不可能。⇒ ここが上限を決めている。
2. **頂点コピーをGPU側へ**: `copyVerticesTransformed` はCPUでmodelviewを焼き込む。**per-group push constantの行列＋生コピー**に変えれば
   per-vertex の変換が消える（スレッド1.7%）。ただし順序・精度（大きな座標）の検証が要る。
3. **capture を差分化**: GLスタブが**状態変更時にダーティフラグ**を立て、offerは「変わっていなければ前回の配列を使う」。stateStampで
   部分的に検証済み（1.005x）だが、**capture全体**（15フィールドの読み出し）を飛ばす版は未実測。
4. 1〜3を足しても**フレームの5-8%**。**パリティ(29%)には届かない**。

### 結論
**この互換レイヤの構造（ゲームの描画要求ごとにJavaで受理する）を保つ限り、受入(≤1.0)には届かない。**
届かせるには「ゲームの描画要求をJavaで1件ずつ処理しない」形、すなわち
**GLスタブが要求をGPU側のリングに直接書き、描画は1本の間接コマンドで行う**設計への置き換えが必要で、
これは本ドキュメントの§6/§7を実装するのではなく**置き換える**規模の作業。

## 13. シェーダパックをワールド描画へ接続する手順（2026-10-22 実測に基づく）

### 実測で確定していること
- **S0**: BSL 10.1.8 → `files=292 programs=94 options=402 disabled=11 errors=0 loadMillis=515`（解析は0エラー）
- **S1**: `-Dceltium.shaderpack.compileProbe=true` で **6プログラム全て `result=OK`**
  （例 `gbuffers_basic.vsh` expanded=14,240 → lowered=14,248 → `spirvWords=1402`、module/descriptorLayout/pipelineLayout すべてok）
- **S2（パイプライン生成まで）**: `CELTIUM_PACK_PIPELINE program=gbuffers_basic pipeline=ok attributes=7 result=OK`
  ＝**実フレームのformat（R8G8B8A8 + D32）向けのグラフィックスパイプラインが作れる**
- 生成に使う部品は既に存在する: `ShaderPackPipelineFactory.createModule` / `createLayoutRebased(lowered, set=?)` /
  `ShaderPackDrawProbe.createBuiltin|createConstant`、`SpirvModule`、`ShadercGlslCompiler`

### 残り（S2の後半）: ワールドの地形をこのパイプラインで描く
1. **set 0 の実体を作る**: `createLayoutRebased` が返す `uniformBlockBinding`（実測 `uniformBlock=1`）に、パックが宣言する
   transform block（`gbuffers_*` は `ModelViewMat`/`ProjMat`/`ModelOffset` 等）を**レイアウト順に詰めて**書く。
   サイズとオフセットは lowered の interface から取る（推測しない）。
2. **sampler を供給**: `samplerBindings`（実測 `samplers=0` のステージと `1` のステージがある）に対して、
   `gtexture`→地形アトラス、`lightmap`→ライトマップ、`noisetex`→1x1白色、などの**フォールバック表**を用意する。
3. **地形の間接描画を流用**: `TerrainRenderer` の indirect 範囲（`shadowRecordedRanges()` と同じ組）を、
   **パック用のパイプライン＋ユニフォームで1本の `vkCmdDrawIndexedIndirect`** として記録する。
   vertex layout は現行地形（`vertexFormats/offsets/stride`）を使い、パック側の attribute 名・location は lowered の
   `attributes` マップ（実測 `{a_Colour=5, a_Normal=4, a_Position=6, a_TexCoord=...}`）で対応付ける。
4. **描画順**: パックの `gbuffers_terrain` は「地形をそのまま別 pipeline で描く」ので、既存の地形パスを置き換える形で入れる。
   壊れたら `-Dceltium.shaderpack.draw=false` で従来経路に戻せるようにする。
5. **受入**: ①パックON/OFFの presented 画像が**目視で異なる** ②validation 0件 ③`CELTIUM_PACK_DRAW` の記録が
   program名付きで出る ④両GPUで gate PASSED。

### 13.1 既存mod（Iris）との対応表 — 影を何に載せるか

リポジトリには Iris の shader frontend が同梱されている（`modern/src/main/shaders_java/net/irisshaders/iris/`、926ファイル）。
影の設計はそこから**要素単位で採り、実装は自前**にする（利用者指示②③）。実コードの該当箇所つきで現状を並べる。

| Iris の要素（実装位置） | Iris の内容 | Celtium の現状 | 次の一手 |
|---|---|---|---|
| 解像度 (`ShadowRenderer:120` `shadowDirectives.getResolution()`) | **パックが宣言**する shadow map 解像度 | **2048固定**（`celtium.shadow.mapSize`） | パックの directive を読んで既定にする |
| 影の距離 (`ShadowRenderer:321` `IrisVideoSettings.shadowDistance * 16`) | ブロック単位の距離（設定で上書き可） | **±48固定**（`celtium.shadow.radius`） | 同上。距離＝光源box半径に直結 |
| **distortion / half-plane** (`ShadowRenderer:132` "half plane = …") | カメラ近傍にテクセルを集中させる**非線形ワープ** | **無し**（等間隔ortho） | **最優先**: 同じ距離を少ない解像度でカバーできる。Irisの式を自前実装 |
| 投影の種類 (`ShadowRenderer:447-450`) | ortho と、legacy pack向け perspective の両対応 | ortho のみ | パックが perspective を要求したら追加 |
| 光源行列 (`shadows/ShadowMatrices.java`) | light view/proj の生成とbias | JOML `ortho` + **Vulkan深度remap**（対応済み） | 変更不要（実測で正しい） |
| 描画対象 (`ShadowRenderer.render` 周辺) | terrain・entity・block entity | **地形のみ** | entity casters（immediate経路）を足す |
| target 構成 (`shadows/ShadowRenderTargets.java`) | `shadowtex0/1`・depth・color を持つ | **depthのみ** | パックの `shadowtex` sampler を繋ぐ段で必要 |

**優先順**: ① distortion（コスト/品質の両方に効く）→ ② パック宣言の解像度・距離 → ③ entity casters → ④ `shadowtex` color target（パック接続時）。

### 13.2 BSL が実際に宣言している影設定（パックの zip から直接、2026-10-22）

`BSL_v10.1.8.zip` の `shaders/shaders.properties` に**プロファイル別の directive** がある:

| profile | shadowMapResolution | shadowDistance |
|---|---|---|
| MINIMUM | 512 | **128.0** |
| LOW | 1024 | **128.0** |
| MEDIUM | 1536 | **192.0** |
| HIGH | 2048 | **256.0** |
| ULTRA | 3072 | **512.0** |

（`final.glsl:37` に `const bool shadowHardwareFiltering = true;` もある）

**これは Celtium の現状と桁が違う**: 我々は **2048²・±48ブロック**。BSL の想定は **128–512ブロック**で、
ULTRA は 3072²。±48 の箱に 2048² を割ると 4.7cm/テクセルだが、BSL の設計は **distortion（half-plane）で近傍に集中**させて
広い距離を少ないテクセルで賄う前提なので、**距離を広げるだけでは精度が落ちる**（だから ①distortion が先）。

⇒ 次の実装単位は「**`shaders.properties` から `shadowMapResolution` と `shadowDistance` を読む**」
（frontend は既に properties を解析している: `PackProperties`）。読めたら native 影パスの解像度・箱半径をそれで初期化し、
distortion を入れてから距離を広げる順で検証する。

### 13.3 half-plane distortion の実装計画（2026-10-22 時点の調査結果）

**なぜ要るか**: BSL は 128–512ブロックの影距離を要求する（§13.2）。距離をそのまま広げると 2048² で 0.5m/テクセルになり、
近傍の影が潰れる。distortion は「近傍にテクセルを集中させ、遠方は粗く」して同じ解像度で広い距離を賄う（Iris の half-plane。

**式（自前実装）**: 正規化した光源空間座標 `u ∈ [-1,1]` に対して `warp(u) = sign(u) * |u|^p`（p<1）。
`du'/du = p*|u|^(p-1)` は中心で発散するので、近傍ほど1テクセルあたりの世界距離が小さくなる。
p=1.0 が恒等（現状）、0.5 前後が BSL の見た目に近い候補。

**両端で一致させる方法（この構成では無料）**: キャスタもレシーバも**同じ頂点シェーダ**（`terrain_solid.vert`）を通り、
`worldPosition` から `gl_Position`（=光源空間）と `v_ShadowCoord` の**両方**を作っている。したがって
- キャスタ: 頂点で `gl_Position.xy` を warp（w を保つ: `xy' = warp(xy/w) * w`）
- レシーバ: `v_ShadowCoord` を**フラグメント**で `uv' = warp(2*uv-1)*0.5+0.5` に変換（または頂点で `v_ShadowCoord` 自体を warp）
と、**同じ関数を同じ場所（頂点）で適用するのが最も安全**（z は不変、`dFdx/dFdy` も自然に追従）。

**ブロッカー（実装前に片付ける）**: 現在の terrain パイプラインは **fragment ステージにのみ** spec定数（id 0,1）を渡している
（`VulkanTerrainPipeline:1310` の `VkSpecializationMapEntry` が2個）。distortion は**頂点ステージ**の定数が要るので、
`VkPipelineShaderStageCreateInfo` の **vertex ステージ側に専用の specialization map** を足す必要がある（fragment とは別の map）。
それが入れば、シェーダ側は `layout(constant_id = 0) const float CELTIUM_SHADOW_WARP = 1.0;` を vert に足して
`-Dceltium.shadow.warp=0.5` で掃引できる。

**検証方法（測定が要らない）**: ①`celtium.shadow.radius` を 48→192 にして、**影ON/OFFの暗くなる画素の割合**が
増えるか（被覆）と、近傍のノイズ（アクネ）が増えていないかを presented 画像で見る。②validation 0件。③両GPU。
p の候補は 0.4/0.5/0.6 を掃引し、被覆とノイズのトレードで選ぶ。

### 注意（この期間に踏んだ罠）
- **probeは既定で呼ばれない**（`celtium.shaderpack.compileProbe=true` が必要）。フラグ無しの「無出力」を「失敗」と読まない。
- `CELTIUM_PACK_DRAW_SUBMIT` の8,215行は**ログ出力条件のバグ**だった（`shaderPackDraws < 3` がパック無しで常に真）。

### 計器の使い方（この期間に踏んだ罠）
- **legが落ちたら、まず制御ビルドで同じlegを回す**（環境要因か変更要因かを分ける）。実例: 2回失敗を変更のせいと即断しかけた。
- **フラグ名を確認する**: `-Dceltium.gpuPassTimings`（正） vs `-Dceltium.vulkan.gpuPassTimings`（無効・無出力に見える）。
- **古いjarを測らない**: 編集が当たっていないビルドを測って「in率6.6%」という誤値を出した。`git status` と jar ハッシュを毎回確認する。
- **単発フレームの画素比較は分散2倍**: 影の darkened は 1.70〜3.54% で振れる。比較は複数runか制御arm付きで。
- **IntelではGPUタイムスタンプが取れない**（`passTimings` が空）。CPUブラケット（`-Dceltium.vulkan.passSplit`）を使う。
