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
