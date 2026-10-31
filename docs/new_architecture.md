# Celtium — Unified Native Rendering Architecture

**設計日:** 2026-10-24  
**調査対象:** `main` @ `33bd277c6223212d51d112a140d00779bd6a4395`  
**対象:** Minecraft 1.12.2 / CleanroomMC / Java 21 API（Java 27 runtime利用可能）/ LWJGL 3 / SDL3 / Vulkan 1.2+  
**目標:** 全描画領域の統合・Native Everything Culling・最小CPU overhead・低転送量・低レイテンシ・AMD RX 6400/Intel UHD 630両対応。

> **参考の設計案（2026-10-28 利用者指示: 絶対としない）。実測でより良い方法があればそちらを採る。** 本書の「描画に介入するModはすべて非サポート」という互換方針も、Xaero・JourneyMap 等への実際の対応方針（[plan.md](plan.md)）が優先する。
> **設計書であり実装・性能改善の完了宣言ではない。** 現状と実測の正典は [docs/plan.md](plan.md)、性能検証は [docs/development.md](development.md)。
> **最新の利用者指定:** Forge API以外のraw GLはstub。**描画実装・描画経路・描画処理に介入するModはすべて競合扱いで非サポート。** この互換ポリシーは [docs/goal.md](goal.md) の旧来の広いMod互換目標に優先する「新設計の指定」であり、実装移行時には製品契約も更新する。既存の停止状態・作業許可は変更しない。

## 0. Executive decision

**One scene database, one visibility system, one frame scheduler, multiple semantics-preserving passes.**

地形、Entity、TESR/Block Entity、モデル、アイテム、パーティクル、看板・フォント、GUI/HUD、空、雲、手、Framebuffer/Postを**同じnative resource systemとdraw/visibility recordに載せる**。カテゴリー別のCPU side drawループ、個別culling、GL state再捕捉、重複バッファ作成を通常経路から除去する。

「全てを統合」は「全てを1種類のpipeline・1 render passに描く」ことではない。順序・depth・blend・stencil・render target・shadow・2D clipが異なるものは別pass/ordered spanにする。一方、それらも**同じscene ID、同じ世代管理、同じ空間索引、同じ可視性結果、同じコマンドコンパイラ**を利用する。

**最速は常時GPU-drivenではない。** CPUのcheap reject、GPU compute culling、direct draw、instancing、MDIのどれが最小の総時間かをscene/GPU/入力規模で選択する。GPU待ち、余分なCompute、余分なbarrier、無駄なcache missを増やす「最適化」は却下する。

### Pipeline

~~~text
 Minecraft 1.12.2 / CleanroomMC / Forge
         │    public Forge API + vanilla renderer entrypoints
         ▼
 ┌──────────────────────────────────────────────────────────────┐
 │ Forge-native Producer Layer                                   │
 │ chunk / entity / TESR / item / particles / text / GUI / sky    │
 │ - registered contents and native draw metadata                │
 │ - compiled/baked meshes on dirty/reload, not every frame      │
 │ - direct transient arena writer for non-retained content      │
 │ - rendering overhaul Mods: startup conflict, NOT integrated  │
 │ - non-Forge raw GL: stub, not rendering compatibility         │
 └──────────────────────────┬───────────────────────────────────┘
                            ▼
 ┌──────────────────────────────────────────────────────────────┐
 │ Unified Scene Database (SoA + versioned handle registry)      │
 │ MeshPool | InstancePool | MaterialPool | StatePool | TargetPool│
 │ SpatialIndex | VisibilityFlags | FrameEventStream             │
 └──────────────────────────┬───────────────────────────────────┘
                            ▼
 ┌──────────────────────────────────────────────────────────────┐
 │ Native Everything Culling                                     │
 │ CPU cheap: region/section graph + frustum + per-object bounds │
 │ GPU optional: Hi-Z + temporal conservative cull + compaction │
 │ world 3D | light-view casters | UI 2D clip: shared framework   │
 └──────────────────────────┬───────────────────────────────────┘
                            ▼
 ┌──────────────────────────────────────────────────────────────┐
 │ Native Frame Compiler                                         │
 │ phase/target/ordering events -> render bins / visibility masks│
 │ direct draw | instancing | multi-draw indirect/count          │
 │ optimized barriers + resource/binding/pipeline reuse          │
 └──────────────────────────┬───────────────────────────────────┘
                            ▼
 ┌──────────────────────────────────────────────────────────────┐
 │ Vulkan Executor                                               │
 │ persistent arenas + upload ring + frame slots + retirement   │
 │ per-pass command buffers -> graphics/compute/transfer queue   │
 │ present -> SDL3 / Wayland                                      │
 └──────────────────────────────────────────────────────────────┘
~~~

## 1. Scope: Forge-only API, rendering Mod zero-compatibility

1. **保持するのはForge APIの公開インターフェース/観測可能な基本契約**（Mod登録、通常のアイテム・ブロック・Entityデータ、標準描画・モデル/テクスチャライフサイクルなど）。公開APIが全てGL互換であることは要求しない。必要なdraw語彙はnative bridgeから提供する。
2. **描画に手を加えるModはすべて非サポート**。coremod/Mixin/transformerによる`RenderGlobal`・`EntityRenderer`・`Tessellator`・`BufferBuilder`・texture/Framebuffer/GL state改変、独自シェーダ、culling・batching・FPS/modded renderer、描画順/GUI描画の差替え、描画パイプラインへの任意hookなど、**Forgeの標準描画を改変・上書きする実装**を含む。明示的なForge API経由であっても「rendererを書き換える」なら非サポート。
3. ゲームコンテンツ追加Modは、**既定のForge/vanilla描画経路に載る範囲のみ**扱う。独自TESR・Entity renderer、custom GL code、custom framebuffer等を要求するModには動作保証を与えない。Forge APIを提供することと、全Mod描画を保証することは別。
4. 非対応Modの検出は**判明している競合mod ID、transformer/mixin target、renderer replacement signature**でfail-fast。Mod IDだけの曖昧な推測で正常なModを排除しない。完全な静的検出は不可能なので、未知の変更はguard/警告/サポート対象外として扱う。
5. raw GL direct callは**stub**。どの呼び出しが実画面に必須だったか診断ログで分かるようにする。stubが何も描かないのを「完全互換」と主張しない。GLコンテキストやGL描画fallbackは禁止。
6. 競合排除の結果、Celtiumが**ワールドの描画資源・パス・visibility・sort・render order・提出**を完全所有できる。描画最適化Mod同士の相互作用を持ち込まない。

**重要なトレードオフ:** この方針は最高速化の自由度を大きく増やすが、独自描画を持つModpackの画質/動作保証は大きく狭くなる。RLCraft/E2E等の完全pack受入は、新互換境界に照らして対象を再定義する必要がある。「Forge API対応」だけで全packが動くと主張しない。

## 2. Every-renderable unified representation

### 2.1 Scene handles, lifetimes, dense pools

| Pool | 役割 | 更新頻度 |
| --- | --- | --- |
| `MeshPool` | section/region chunk、model、item、TESR、glyph、static GUIの共有GPU geometry | build/reload/dirty |
| `InstancePool` | camera-relative transform、pose/skin、light、tint、texture index、emitter param | motion/animationのみ |
| `MaterialPool` | pipeline family、texture/sampler index、blend/cull/depth/alpha class | material/reload |
| `ObjectBoundsPool` | AABB/sphere、section/region所属、occluder/occludee flag、view mask | movement/dirty |
| `StatePool` | immutable raster/transform/matrix/lighting snapshots | 実状態変更時 |
| `TargetPool` | framebuffer attachment/generation/layout/extent/aliasとpost依存 | resize/transition |
| `FrameArena` | CPU packet、upload staging、indirect commands、per-view GPU records | frame slot再利用時 |

- `handle = index + generation`。Javaオブジェクトポインタやraw GL nameをGPU資源IDにしない。drawにはaccept時のgenerationを保持。
- 連続SoA配列・packed bounds・primitive IDを使い、毎frameのHashMap/boxed object allocationを減らす。動的objectのみdirty queueで更新。
- immutable geometryはGPU側で再利用。再build・rename・delete時には旧generationをsubmitted frameが参照している間retireしない。
- mapped host-visible bufferはCPU更新用。静的meshはGPU転送効率を計測し、可能ならdevice-localメモリに配置。iGPUでは別戦略も計測。
- `FrameArena`は既存`VulkanFrameArena`、`FrameSubmissionScheduler`、`NativeGeometryBuffer`、`RetainedGeometryCache`を置き換え重複させず統合する。

### 2.2 Producers: one backend, specialized ingestion

| Producer | 運用方式 | 消すべき繰り返し |
| --- | --- | --- |
| Chunk | Celeritasのsection mesher + visible list → region mesh | per-section GL/VBO setup・per-frame rebuild |
| Entity/model | shared baked geometry + transform/pose instance | Entity毎の即時頂点生成・state capture |
| Item/held/dropped | item stack/variant/generation key mesh cache + instance | 毎frameの同一quadデコード |
| TESR/block entity | static submesh + dynamic part/transform record | ブロックごとの重複モデル構築 |
| Particle | emitter/typeごとにinstance/transient stream | particle毎のdraw/submit/culling |
| Font/sign/map | glyph atlas + cached run/layout + dirty rebuild | glyph単位のcallと文字列再整形 |
| GUI/HUD | retained geometry + clipped transient substream | 全GUI再生成・冗長なstate変更 |
| Sky/cloud/hand/post | 専用semantic pass、共有resource/visibility/scheduler | GL→Vulkan bridge・FBOコピー |

Minecraft 1.12.2の`BufferBuilder`は次のdrawで内部領域を再利用する。**pointerを後のflushまで保持してコピーを遅らせてはいけない**。自分が所有するForge producerにはarena direct writerを導入し、既存呼出しの互換経路ではaccept時に一度だけコピーする。CPUでretained geometryをdraw毎に「結合」するのは、過去の悪化測定から避ける。

### 2.3 API / GPU draw data

`DrawPacket` = compact POD: `objectId, meshId, materialId, instanceRange, passId, targetId, stateIndex, visibilityMask, sortClass, eventSequence`。packetに毎回行列・texture descriptor・pipeline handleをベタコピーしない。

`InstanceData` = transform/pose/light/tint、`MaterialData` = texture/sampler/pipeline、`MeshData` = vertex/index handles + ranges。固定の224B`ImmediateDrawRecords`は移行ABIとして利用可能だが、最終設計では**更新粒度別に分割**し、同じモデルを100体描く際に100回meshを転送しない。

- CPU表示側はJava 21 hot loopでprimitive/packed memoryへ書く（LWJGL `MemoryUtil`/`MemoryStack`/direct ByteBuffer）。JNI overheadを最小化するため1 packetごとにnative callを発行しない。
- Java 27 runtimeのJIT最適化はベンチする。**Java 22+のFFM最終APIは`--release 21`で直接使用不可**。FFM backendやVector APIはマルチリリース/別モジュールとして実測優位の場合だけ追加。Java 21のpreview APIを必須にしない。
- SIMD化候補: frustum plane vs SoA bounds、visibility bitset、頂点属性pack、instance transform。単純なSIMD導入よりもデータ構造・呼び出し数・帯域を先に最適化。

## 3. Native Everything Culling (NEC)

### 3.1 Philosophy: unified registry, differentiated semantics

**地形だけのcullingで終わらせない。** CPU/GPUの可視判定を1つのvisibility databaseで管理し、結果をchunk・entity・TESR・item・particle・text・GUIへ共通配布する。ただし3D遮蔽と2D clippingを混同しない。

~~~text
Scene spatial index
 ├─ region -> section -> static surface clusters/chunk meshes
 ├─ dynamic cell -> entity / TESR / item / projectile / emitters
 ├─ static world labels / world-space text / billboards
 ├─ shadow casters -> light-view spatial query
 └─ 2D screen-space rect / glyph runs -> viewport-scissor clipping

Visibility view (per camera / shadow cascade / post target / portal if any)
   CPU distance + section connectivity + frustum + 2D rect rejects
             ↓
   Candidate lists (conservative flags + generations)
             ↓
   Optional GPU Hi-Z tests + prefix sum/compaction by render bin
             ↓
   Visible-instance index + indirect commands (per pass/pipeline/material)
~~~

### 3.2 CPU phase — cheapest rejects first

1. **Hierarchical region/section graph.** 既存`SectionLattice`・`OcclusionCuller`・`RegionCullCache`の可視情報、unloaded-section fallback、frustumを活用。graphを全objectへ毎回再走査せず、**visible section bitset/epoch**を生成する。
2. **Dynamic spatial bins.** Entity/TESR/item/emitterをregion/sectionへ所属させ、移動/追加/除去の時だけindex変更。`EntityGatherer`と既存entity visibility経路は同じsnapshotを参照。section内のobjectでもboundsが視界内か判定。
3. **Bounds/frustum.** SoA AABB/sphere・距離・camera-relative floatを用いて安価にreject。boundsが不明または描画がAABB外に広がるコンテンツは**保守的拡張またはalways-visible**。不確かなobjectを誤って不可視にしない。
4. **Screen/UI.** UI/HUD/textはviewport + scissor + clip treeで判定。world-space labelsだけ3D判定へ載せる。sky/fullscreen overlay/handは個別可視性ポリシー。
5. **Temporal coherence.** 前フレーム可視とcamera/geometry世代を利用して同じ仕事を減らす。**camera teleport/急旋回、chunk・object変更、resize、depth不連続で履歴を無効化**する。
6. **Occluder selection.** 深度へ正しく書けるopaque/cutoutでalpha clipが成立するもののみを遮蔽に使用。透明、GUI、particle等を勝手にoccluderにしない。

### 3.3 GPU phase — conditional hierarchical Hi-Z/occlusion

GPUに十分な候補が残った時のみ実行。

- depth prepassまたは前段opaqueのdepthから**mip階層のHi-Z**を生成。reversed-Z/通常Z、projection、depth conventionに応じmin/max reductionと比較方向を切り替える。Vulkanのimage layout/access/barrierを正しく挿入。
- CPUが出した**region/section + dynamic object groupの候補**をComputeでvisibility判定し、typeやmaterialに関係なく共通のGPU candidate ABIを使う。screen boundsのprojection、near-plane crossing、depth uncertaintyは保守的にvisibleにする。
- ビジビリティ結果をtype/semantic pass/pipeline/material/buffer binでprefix-sum/compactionし、`VisibleInstanceIndices`と間接コマンドを生成。**meshをコピーして集約しない**。
- GPU結果を毎frame CPU readbackしない。draw indirect/indirect countが使える場合はGPU内でconsumerへつなぐ。count非対応ならCPU count固定+`instanceCount=0`等の合法なfallbackを測定。
- **前フレームHi-Zによる1-frame-late occlusionは安全ではない**。前frameの遮蔽をblind trustしない：新規・移動・screen edge・カメラ移動が大きい・過去にhidden・depth変化のあるobjectは可視扱いにする。必要なら同frameのdepth prepass→Hi-Z→late occludeesの2段を使う。
- コンピュート+barrier+追加depth passの固定費が削れたdraw/fragment費用を上回る軽いsceneでは**GPU cullingを完全skip**。RX 6400とUHD 630別々に閾値を計測する。
- 透明物は**occludeeにはできるが原則occluderにしない**。不可視でもsimulation更新や音/ゲーム状態を止めない。

### 3.4 View-specific correctness

| 種別 | CPU cull | GPU cull | 重要な例外 |
| --- | --- | --- | --- |
| Opaque terrain | section connectivity + frustum | Hi-Z / cluster | section graphがunloaded/不確実ならvisible |
| Cutout terrain | section + frustum | 有効なalpha-tested depthでのみ | alpha clipの深度一致 |
| Entity/model/item | spatial bins + bounds | AABB group Hi-Z | 名前plate/持ち物/アニメーションがbounds外なら拡大 |
| TESR | block-region + dynamic bounds | typeによって有効化 | 光・ビーム・任意拡張bounds |
| Particle | emitter/group bounds | grouping利益がある場合のみ | translucent sortingと移動体の遅延 |
| World text / billboard | world AABB + frustum | optional | UI textは別扱い |
| HUD/GUI/font | 2D clip & scissor | 原則不要 | UI重なり/ブレンド/表示順を保持 |
| Sky/hand/fullscreen | 特殊ルール | 通常のHi-Zに載せない | 描画セマンティクス優先 |
| Shadow casters | light frustum + receiver-driven search | cascadeごと optional | **カメラ外のcasterも必要**、main view可視だけで絞らない |
| Offscreen FBO/post | 別view/targetごと | 依存がある時のみ | clear、texture feedback、readback、alias |

### 3.5 Safety/measurement invariants

- **偽陰性（本来見えるものを消す）は許容しない**。不明→visible。オクルージョンは攻めても、可視性結果は保守的。
- per-frame visibility reuseはobject/camera/target/resource epochで失効させる。CPUとGPU両側のview epochを一致させる。
- GPU cullingの判定件数、forced-visible、false-negative検知用compare run、Compute時間、fragment削減、CPU準備時間、transition数を測る。
- 正解比較では`disableAllCulling`だけでなく、**CPU only / GPU only / combined / no-occlusion**を同じシーンで画像比較する。近距離、遮蔽物背後、移動、camera teleport、water/cutout、複数view、TESR、UIを含む。
- section graphの「見えた」という結果を描画結果の完成とは扱わない。GPU visible/indirect commands/present imageまで追跡。

## 4. Unified batching, binning & GPU command generation

### 4.1 Frame packets and ordering

`DrawPacket`は`object/material/mesh/instance/target/state`世代とsortability、ordered-sequenceを保持。別に`TargetEvent`、`ClearEvent`、`CopyEvent`、`ReadbackEvent`、`ForgeCallbackBoundary`をframe event streamへ入れる。

- opaqueでも**無条件sortは禁止**。stencil、depth mode、alpha-to-coverage、side effects、translucency、multiple targets、feedback、callbacksで境界を切る。
- 並べ替えが合法な連続した**opaque island**のみmaterial/pipeline/bufferでbin化。translucent/UIは原順を守る。隣接runのコマンド統合は順序を保てる範囲で利用。
- **グラフはpass/target/resource依存に限定**し、drawごとに汎用DAGオブジェクトを生成しない。CPU overheadが増える立派なframegraphより少数の明示passを優先。
- depth-only prepass、opaque、cutout、translucent、particles、hand、UI、post、presentを**実際に必要な時だけ**生成。空passをskipし、同じattachment上の余計な切替/clear/submitを消す。

### 4.2 Cost-based draw selection

1. **Instanced**: 同じmesh/material、異なるtransform/light/poseなど。per-instance recordをGPUが参照。
2. **`vkCmdDrawIndexedIndirect`**: 同じpipeline/descriptor/buffers上で異なるindex rangesを連続コマンドとしてGPU/CPUが生成。
3. **`vkCmdDrawIndexedIndirectCount`**: 対応featureが有効でGPU culling側が件数まで生成する場合。
4. **Direct `vkCmdDrawIndexed`**: 少数のGUI、状態境界が多いdraw、個別の特殊pass。間接準備コストが高い時は直drawが速い。

- vertex/indexをCPUでdrawごとに結合しない。間接コマンド`firstIndex`・`vertexOffset`を活用。
- `firstInstance`をdraw data indexに使う場合、deviceで`drawIndirectFirstInstance`を有効化していることを検査。
- `shaderDrawParameters`、`multiDrawIndirect`、descriptor array/nonuniform、`drawIndirectCount`などは**Vulkan 1.2で利用可能な概念でも個別feature enableが必要**。
- descriptor配列だけを追加して遅くなった過去の実験を尊重。**本当にbind/draw/state処理を減らせる時**だけ採用。
- drawごとのfull push blockはやめ、frame/material/instanceの別更新粒度へ分離。必要な小さなpushはrun単位に限定。
- GPU count/computeが高価な場合はCPU side visible packet compact/indirect emitで勝つ構成を選べる。

### 4.3 One interface, not one shader

`ShaderFamily`はterrain、skinned model、static model、item、particles、text、GUI、postを持ってよい。共通なのは**mesh/material/instanceの参照ABI、visible-indices、resource lifetime、submit scheduler**である。

「uber shaderですべて」を義務化しない。動的分岐とregister pressureによる逆効果を防ぎ、GPU向けのspecialized shadersを同じdraw compilerが選択する。Shaderpackの追加はpack用shader variant/別addonとし、coreの性能向上を依存させない。

## 5. CPU, upload, thread, and submission architecture

- **Java 21**: primitive packed arrays + generational handles + chunk update job graph + bounded worker pools。同期が少ない方針を優先。CPU重作業にvirtual threadsを盲目的に使わない。I/Oには有効な場合がある。
- **Producer parallelism**: chunk mesh build、model compile、texture decode/atlasは非同期化できるが、Forge callback/ゲーム世界のreadにthread safetyがなければimmutable snapshot/メインスレッドで収集した入力だけworkerへ渡す。描画要求順とworld epochを保持。
- **GPU mesh upload**: device-local region allocations + mapped staging ring + dirty ranges。既存`VulkanFrameArena`/deferred uploadを使い回し、短命バッファを大量生成しない。iGPUではhost-visibleの方が速いか測定。
- **Frame ownership**: 現行4 frame slotsを起点に完成epoch/`submitSequence`でretire。GPUが参照中のarena/mesh/texture/descriptorは再利用・破棄しない。
- **Queue work**: per-passで小さな`vkQueueSubmit`を乱発せず、必要な依存を保ちながらframe単位で併合。別のcompute/transfer queueはoverlapが本当に利益を出す時だけ（転送・barrier/owner transferの費用込み）。
- **Sync**: Vulkan 1.2 timeline semaphoreを利用可能ならcompletion epochで利用し、既存fence slotとの重複を避ける。swapchain acquire/presentのbinary semaphore要件と混同しない。`vkDeviceWaitIdle`/queue全量待ちは通常フレーム経路に置かない。
- **SDL3**: event/window/surface/fullscreen管理。GL-lessではSDL/EGL側の余分なswapを実行しない。swapchain resize/再生成はscene resource lifetimeと分離。
- **GC**: per-draw allocを無くす。JDK 27 runtimeでのGC/JITとJDK 21 build ABIは分けて測定。JDK 27という名前だけで速くなったとみなさない。
- **Failure modes**: world switch、reload、resize、device lost、upload ring pressure、full arena、Forge exceptionで未完のdraw/target stateを安全に破棄/復元。GPU stallで古いresourcesを解放しない。

## 6. 描画最適化Modの発想を汎化する表

| 既存分野の典型 | Celtiumに統合する共通原理 | 主な対象 |
| --- | --- | --- |
| Celeritas / Sodium系 | section visibility・mesh cache・region batches・indirect | terrainとworld spatial indexの基礎 |
| EntityCulling系 | 共有AABB/Hi-Z visibilityとtemporal result（誤消去防止） | entity/TESR/item/particle groups |
| ImmediatelyFast系 | バッチ化、state変更削減、glyph layout cache、GUI clipping | entities/UI/text/particles |
| Enhanced Block Entities系 | static部分のretained mesh、動的部分のinstance化 | TESR/chest/sign等 |
| Angelica / FalseTweaks系 | item/model geometry cache、display-list内容保持、render state batch | model/item/world labels |
| VulkanMod系 | arena、native buffer lifetime、submission batching、GPU-side command | all categories |
| Embeddium/Celeritas async系 | dirty tracking、bounded rebuild jobs、visibility cache | mesh/atlas/model |

**実装思想だけを使う。** それらのレンダラ/描画改変Modを一緒にロードする必要も互換を取る必要もない。LGPL等のコードを実際に取り込む場合は、該当ファイルのライセンス/由来・表示/ライブラリ改変・配布条件を別途確認。GPL/AGPL/PolyForm等と混同しない。

## 7. 現行Celtiumのどこを置き換えるか

| 現行の入口/クラス | 移行先 | 消えるべき仕事 |
| --- | --- | --- |
| `WorldVertexBufferUploaderMixin` | `ForgeNativeProducer` / `TransientWriter` | vanilla全描画の二次GL捕捉 |
| `VulkanTerrainGpuBackend.submitGeometryBatch` | `SceneWriter.publish(packet)` | 毎drawの`captureState`/matrix decode/prepare |
| `ImmediateGroup`・`VulkanDynamicMesh` | `DrawRecord` + `MeshPool` + `FrameArena` | draw毎group object/native allocation |
| `NativeRasterState`/`NativeTextureBindings` | `NativeStateOwners` | GL互換layerへの逆query |
| `RetainedGeometryCache`・`NativeGeometryBuffer` | `MeshPool`への既存機能移行 | 同じモデルの再decode・重複コピー |
| `SectionLattice` / `OcclusionCuller` | `NativeEverythingCuller`のCPU world backend | terrainだけの可視性結果に閉じる構造 |
| `EntityGatherer` / Forge entity hooks | `SpatialIndex` + `VisibilitySnapshot` | entity毎の冗長な可視性参照 |
| `VulkanTextureArray`/`ImmediateDrawRecords` | `MaterialPool`/`InstancePool`/`DrawBins` | 使わない大きな共有descriptorの固定費 |
| `TerrainRenderer` | `TerrainProducer` + generic frame compiler | terrainだけの提出管理 |
| `FrameCoordinator` / `FrameSubmissionScheduler` | `VulkanExecutor`の所有者 | 部分的なwait/submit/cleanup |
| 約20k行の`VulkanTerrainGpuBackend` | 移行facade → 段階的縮小 | state+resource+draw+present密結合 |

**一度に全部作り直さない。** 先に所有権/APIを分け、現在の描画品質を保ってproducer単位で同じcompilerへ載せる。既存のarena/indirect/lease/dirty trackingを新しい名前で重複実装しない。

## 8. Migration and gates (performance-first, falsifiable)

**Stage 0 — Baseline/provenance。** clean HEADでJAR hash固定。RTX等ではなく実対象RX 6400/UHD 630を利用。`worldTicks [400,600)`のwarm interval、load、frame p50/p95/p99、CPU vs GPU timings、texture readback、worker/tick、scene、features、draw/indirect/submit/transfer countsを記録。**2026-10-24の1.56msビルド再現失敗とp95大きなノイズを尊重し、静穏3+ paired測定が成立しなければ性能差を主張しない。**

**Stage 1 — Native scene object & state owners。** `SceneDatabase`、generational mesh/material/instance/target/state IDsを作り、2D/3D Forge producerが直接recordを生成。旧`submitGeometryBatch`は移行期間の互換gatewayに隔離。GPU実行前の同一画質/寿命を検証。

**Stage 2 — Unified Everything Culling CPU。** `SectionLattice`を共通visible-section snapshotへ昇格、entity/TESR/item/emitter/labelsを同じspatial indexに登録。既存カメラカリング、unloaded、camera teleport、off-box render、shadow caster、GUI scissorの偽陰性をfixtureで検査。CPU時間・可視/総object比を測る。

**Stage 3 — Retained/Instance conversion。** 共有GPU model/item/TESR、cached glyph/UI、particle emitter batchesを段階実装。動的内容はdirty updates。描画品質とCPU alloc・頂点転送bytes・frame CPUを同一JARフラグA/B比較。Celeritasとの対照も保持。

**Stage 4 — Frame compiler and bins。** shader family/opaque island/ordered spans、state/target events、scene-wide material bins、instanced/indirect/direct選択。`ImmediateDrawRecords`/`VulkanTextureArray`/既存indirectコードを再利用し、**実行中のfeature flagは現行ソースを確認**（HEADでは`indirectImmediate`/`sortOpaque`は`Boolean.getBoolean`なので無指定=false。過去の「既定ON」コメントを鵜呑みにしない）。命令数ではなく総frame時間で採用。

**Stage 5 — GPU Everything Culling optional。** Hi-Zを作り、静的chunk cluster+動的entity/TESR/item/particle candidatesを同一compute経路でcompaction。保守的temporal対策。GPU dispatch/barrierの固定費と削減量を比較し、小シーンはCPU-onlyが最速なら採用。GPU/HW別の閾値と画質対照が必須。

**Stage 6 — Submission/pacing and policy。** upload・barrier・frame slot/retirement最適化。render overhaul Mod conflict checkerで分かる競合をfail-fast。Java 21ベース/27 runtime両検証。全pack互換は目標から外し、Forge標準経路の正しさを製品条件にする。

### A/B matrix (minimum)

| 変えるもの | 対照 | 必ず見るもの |
| --- | --- | --- |
| Native producer | 従来Immediate frontend | frame p50/p95/p99、CPU main thread、copy bytes、同一画像 |
| Shared mesh+instance | 頂点再生成 | rebuild/memory/VRAM/CPU/animation correctness |
| CPU everything culling | 既存terrain/entity独立cull | CPU、visible count、false-negative、画質 |
| GPU Hi-Z | CPU-only | GPU compute/depth/barrier、draw減少、総frame、移動中の欠落 |
| Indirect+binning | direct | command/bind回数、CPU record、GPU、frame p50/p95 |
| Cache/descriptor変種 | 非共有 | per-frame更新・ロード・reload・descriptor変更税 |
| Java runtime/FFM/SIMD | Java 21互換base | JIT warmup、JFR、CPU時間、JVM flags、有意差 |
| Full optimization | fixed Celeritas reference | 両GPU、同じscene/features、paired ratio、tail、画像 |

### Hard correctness gates

- Forge標準描画: loading / menu / world / options / GUI / HUD / entity/TESR / particle / translucent / FBO / screenshot / world reload。
- GPU: AMD RX 6400およびIntel UHD 630、validation 0違反、画像差分（較正済み参照）、停止なし。
- 命令と資源: accepted / visible / emitted / submitted / presentedの各件数、retire/epoch/renameを分離。未知のraw GL stubでは非対応が可視化される。
- 性能: 3組以上のhash固定paired対照、CPU/GPU timestamp、p50/p95/p99、warm/cold/moving、upload、VRAM、GC、負荷・clockの確認。**速く見えたログ値ではなく有効な改善のみ採用**。
- Failure test: camera teleport、目の前の遮蔽物、FOV変更、透明水・ガラス、異常bounds、resize、resource reload、world切替、GPU stall、ring枯渇、shaderpack OFF/ON独立経路。

## 9. 性能に関する重要な反例と禁止事項

- **MDI化/descriptor配列化だけでは最速化を証明しない。** 現行の過去記録にはdescriptor配列単独の悪化、viewportをrun keyに追加した場合のrun分断、CPU頂点結合での悪化がある。
- **GPU cullingは安価ではない。** depth prepass + Hi-Z build + compute + compaction + barriersの全費用を見て、数十drawしかない軽いsceneでは省く。
- **フレーム間Hi-Zは本来可視の物体を消しうる。** 前frameのocclusionを無条件で信頼しない。camera motion/scene dirtyと保守的なscreen boundsを扱う。
- **透明/GUI/Forge eventを機械的に並べ替えない。** performanceのための違う画像は不合格。
- **Forge API互換と任意Modのrender compatibilityは別。** 競合Modを「ほぼ表示できるから」正式サポートしない。
- **CPU↔GPU同期やreadbackを勝手に削除しない。** framebufferコピーや画像取得は順序上の意味を持つ。GPU内コピーで可能な場合だけ置換。
- **描画のみをcullする。** tick、server simulation、physics、networkを勝手に停止することはこのrendererの仕事ではない。
- **FFM/Rust/Compute/SIMDは手段。** 既存のGPU/CPU時間を増やすなら採用しない。

## 10. 具体的な最初の実装単位（優先度順）

1. **`NativeSceneObject` / `SceneDatabase` / `VisibilityCandidate` / `FramePacket`のABI・世代・寿命の定義。** retained/transientどちらも共通化する、最小の独立した基盤。
2. **`SectionLattice`のvisibility snapshotをentity/TESR/item/particle側へ提供。** まずCPU-onlyのNative Everything Cullingを完成させ、すぐ計測できる実利益を出す。既存entityの可視性と二重判定しない。
3. **Entity/item/TESRをinstance化・mesh保持化**し、毎フレームのvertex生成/state capture/ImmediateGroupを削る。描画overhaul競合をサポートしないため、producerまで制御できる利点を最大限使う。
4. **GUI/text/particlesも同じframe arena/packetを使う**が、2D cullingとordered renderingの意味は保持。
5. **Native Frame Compiler**で完全なrender binsとper-material instancing/indirectを配線し、preflight/Java draw loopの固定費を段階的に除去。
6. **GPU Hi-Z Everything Cullingをoptional実装**し、CPU-onlyと比較してscene別/HW別に自動切替。shadow用visibilityは独立のlight-viewを用いる。
7. **静穏環境で実測が最速の構成をデフォルト化**。Vulkan 1.2の有効feature、JDK/runtime、GPUモデルごとに挙動を固定せず合理的に選択。

## 11. 一次資料・既存Celtiumの実装アンカー

- [現行backend: VulkanTerrainGpuBackend](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java) — `submitGeometryBatch` / `submitImmediateBatchBody` / `celtium$tryEnqueueIndirect` / `celtium$flushIndirectRun` / `recordImmediateOnlyPass`
- [現在のnative state owner](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/NativeRasterState.java)、[texture bindings](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/NativeTextureBindings.java)
- [ImmediateDrawRecords](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/ImmediateDrawRecords.java)、[VulkanFrameArena](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanFrameArena.java)、[FrameCoordinator](../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FrameCoordinator.java)
- [SectionLattice](../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/occlusion/SectionLattice.java)、[OcclusionCuller](../common/src/main/java/org/embeddedt/embeddium/impl/render/chunk/occlusion/OcclusionCuller.java)、[EntityGatherer](../forge122/src/main/java/org/taumc/celeritas/impl/render/entity/EntityGatherer.java)
- [開発・計測の記録](development.md)、[リリースv0.0.16](releases/0.0.16-alpha.md)
- [Khronos: GPU rendering and Multi-Draw Indirect](https://docs.vulkan.org/samples/latest/samples/performance/multi_draw_indirect/README.html)
- [Khronos: Vulkan 1.2 and feature breakdown](https://docs.vulkan.org/guide/latest/versions.html)
- [Khronos: timeline semaphores](https://docs.vulkan.org/tutorial/latest/Advanced_Vulkan_Compute/08_Asynchronous_Compute/03_timeline_semaphores.html)

**最終設計原則:** 個別の描画最適化Modの処理を寄せ集めない。**世界の全描画物を登録→変更時だけ構築→共有可視性で棄却→描画条件で分類→最小の転送/コマンドで提出**という一つのエンジンに、最適化の原理を吸収する。最速かどうかは、同一画像・同一環境のCPU/GPU両方のフレーム時間で決着をつける。



### 決定的測定（2026-10-31、Susy world、rc.14）

`-Dceltium.debug.drawKeyCensus=true` の実測: 1報告期間で **groups=13,440 / distinctKeys=54 / switches=12,839 / largestBucket=2,040**。
つまり**1groupあたりほぼ毎回keyが変わる**一方、マテリアルは54種しかない（112 groups/frame換算で約120フレーム分）。**opaque groupをmaterial keyで並べ替えるだけで平均run長 ≈249 group**になり、drawを~54回まで畳める計算。スキャフォールド（`sortOrder[segments=0 ordered=0]`）は未実装状態。

実装上の注意:
- opaque groupのみ並べ替え（blendedは到着順のまま）。深度比較LOWERで同一深度の重なりは順序依存なので、コピー面の重なりで描画差が出る可能性→正典ゲートで判定。
- 並べ替え後もgroup meshがarena sliceで同一バッファならrun keyが一致し、records indirectで1 draw/runに。array setは不要になる可能性（keyのalpha差が残る場合はarray不要でもrun数は54前後）。
- 計測再現: `-Dceltium.vulkan.frameStats=true -Dceltium.debug.drawKeyCensus=true`。

## array-records — bindless即時描画（設計確定 2026-10-31）

**目的**: per-groupのpush/drawを廃し、window内のtextured groupを1-3 drawに畳む（残差の主因である112 groups/frameのper-batchコスト）。

**判明済みの土台**（コード確認済み）:
- `VulkanTextureArray`: 4096枠のCOMBINED_IMAGE_SAMPLER bindless set（UPDATE_AFTER_BIND+PARTIALLY_BOUND）を**作成・充填済み**。描画未接続。`context.textureArraySupported()`でgate。
- records系は配線済み: `immediate_tex_color_records.vert`等がset1のSSBOからrecordを読み、`drawIndirectRange`がrun単位で1つの`vkCmdDrawIndexedIndirect`を発行。record stride=224B（litが208..215を使うため**216..223の8Bが空き**）。
- 現状runがsingletonなのはfragが`set0=sampler2D u_Texture`（per-texture set）だから。textureがkeyに入りrunが切れる。

**実装手順（フラグ`-Dceltium.vulkan.arrayRecords=true`、既定off）**:
1. `celtium$writeDrawRecord`でrecord offset 216にtexture array indexを書く。
2. 新シェーダ: records vertのコピー＋`layout(location=3) flat out int v_TextureIndex`（recordの216を読む）。fragは`layout(set=0,binding=0) uniform sampler2D u_Textures[];`で`texture(u_Textures[v_TextureIndex], uv)`。まず**unlit textured familyのみ**（lightmap不要、fogブロックは既存と同一）。
3. pipeline: records variantの新create（array set layout=TextureArray.layout、set1=records、pushなし）。
4. run keyからtexture setを外し、array setをrun開始時に1回bind。
5. 検証: 正典plainゲート（画像・床値）＋`CELTIUM_MERGE_CENSUS`/`drawn[perFrame]`でrun長を確認。device lost/VUIDゼロを必須条件。クラッシュ時は前回同様すぐrevert。

**注意**: bindless setはrecorded bufferが名前を残したまま書き換わるためUPDATE_AFTER_BIND前提。array indexの寿命はwindow内で固定（arrayはresetされない）。


### 主要描画は model multi-draw run（2026-10-31 判明）

`drawn[perFrame]=112` の正体を追跡: それは即時groupではなく **`ModelReplay.celtium$drawModelRun`（1回で1 run）**。各runは`modelInstances.draw(cmd, run, textureArray.set(), multiDrawIndirect, projection, fog)`を発行し、**texture array + multi-draw indirect** で既にまとめて描かれている。つまりbindlessはモデル経路で稼働済みで、即時group経路(1 group/frame)とは別物。

したがって残るper-batchコストの主因は **112 model run/frame × 1 multiDrawIndirect呼び出しのCPU**。削減は「(pipeline, projection, fog)が同じrunを1つのmultiDrawIndirectに合流させる」ことで、既存のrun buffer/indirect基盤をそのまま使える見込み。draw-key censusの54材料もこのrun集合の話として読み直す必要がある（即時pathの値ではない）。
