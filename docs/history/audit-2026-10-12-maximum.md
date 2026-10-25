# Celtium maximum audit / bug hunt / performance review — 2026-10-12

作成: 2026-10-12。監査開始 18:43 JST。初期固定 18:44:12 JST。19:45までの main / dirty と、監査中に統合された PR #20 / #21 も追跡した。

## 結論

**native Vulkan の実描画・present は成立している。Celeritas の地形 CPU エンジンも大きく保持できている。しかし「残るのはロード画面だけ」「最適化の正しさも含めて完了」とするには、現コードに具体的な穴がある。**

特に急ぐべきなのは、**PR #21 の item cache の使用範囲と状態復元**、**非同期 submit に対する uniform / descriptor / buffer の寿命**、**copy cache が必要な描画更新を省略すること**である。速い GPU や診断 readback の待ちが問題を隠せるため、正常なプレイ実績を否定せず、条件付きの欠陥を分けて閉じる。

本監査では **7つの CPU witness** を実ソース・原典のメソッドから構成して確認した。依存クラスは stub を使っている。**Minecraft / GPU / 画像受入の再現とは区別する。** GPU の新規起動はしていない。main / SWE2 のゲーム検証に割り込まず、既存 run と JFR を検査した。

renderer の実装変更、他担当の dirty の取り込み、goal ID の変更は行っていない。監査の成果物だけを独立した commit にする。

## 1. 固定した対象と証拠の強さ

| 対象 | 固定・確認 |
| --- | --- |
| 初期 HEAD | 8fa273d289b024b9dfd7eb426685722428c8a6a5 |
| 初期 dirty | VulkanTerrainGpuBackend.java。patch と各ファイルの hash を保存 |
| 初期 snapshot | .celtium-work/audit-20261012-184412/source/、689ファイル |
| manifest SHA-256 | e065b6600272f0ef029020ef884f00fc6765269d79cc87a21dfb6edb91b7d608 |
| 中間再確認 HEAD | 601ea924a367ce26c8a574d506065811fd2a7b3f |
| 追加確認 | PR #20 merge 1b1484054、PR #21 merge 070790f1d、36530f542 の window/copy route 修正 |
| 固定 Celeritas | 989ebf2e52ceed052ec112a569e0c1d172095756 |
| 最新 Celeritas | 別 clone、7818b33172086d0576456a3acf04e1548ddbed74 |
| Minecraft 原典 | build が生成した .celtium-work/mc-sources/net/minecraft/ |
| Forge 原典 | Gradle cache の forge/1.12.2-14.23.5.2847/unpacked/src/main/java/ |
| SRG | scripts/mc-sources.sh --srg renderModel で PR #21 の3引数 targetも確認 |
| witness 結果 SHA-256 | 9fc4eaeeab3dc7d3b3dddec862fa5bf31a712a93bf5df1748d0b2be30c7fb790 |

snapshot 全ファイルを固定したことは、689ファイル全部を同じ深さで行ごとに読んだという意味ではない。主な renderer producer / consumer / resource owner を追い、common と forge122 の Java を両参照に対して比較した。外部 mod 全部、全 OS、全 GPU、全依存ライブラリの内部まで監査したとは主張しない。

中間確認では、TerrainPipeline、UniformBuffer、FrameArena、HostBuffer、DynamicMesh、PostChain、ImmediateTexturePipeline、EntityGatherer、FontGlyphCapture、FramePassTimings、Swapchain、GlTexture の関連ファイルは初期 snapshot と同一だった。backend の beginCommands / writeUniforms / ensureUniformCapacity / tryIndirectBatch / uploadRegion / onSubmissionConfirmed / ensureFrameTarget / destroyFramebuffer / updateTimingsBody / postChainRun も同一。後続の loading / window route 修正はこれらの欠陥の修正ではない。

証拠を以下で区別する。

- **CPU再現**: 当該ソースのメソッド、または原典のメソッドを限定 harness で実行した。dependency stub の制約あり。
- **コード確定**: producer / consumer と必要な契約の欠落を確認。条件が成立する実 GPU run は未実施。
- **既知・継続**: 過去の指摘の残り。新規発見に数え直さない。
- **候補**: 追加の帰属実験が必要。完成判定を取り消す根拠に単独では使わない。
- **修正中 / 受入未確認**: 他担当の変更は保持するが、コード追加だけで受入とはしない。

P1 は通常描画、状態、資源寿命、長時間の継続動作に関わる。既定無効・未行使の経路は明記する。P2 は条件付き互換、計器、失敗復旧、低頻度の問題を含む。

## 2. 優先表

**既に追跡中のP0: main menu panoramaが灰色になる回帰。19:45時点のmainは未閉鎖としている。** B08/B09の以前からあるcopy省略欠陥と、最新のdraw/read routing回帰を同じ原因と決めつけない。2 jar / 同じcameraの対照で分離する。

| ID | 優先 | 状態 | 要点 |
| --- | --- | --- | --- |
| B01 | P1 | CPU再現・新規差分 | item cache が使用頂点でなく scratch buffer の容量を保存 |
| B02 | P1 | CPU再現・新規差分 | item cache hit で Forge の lighting cleanup が消える |
| B03 | P1 | コード確定 | terrain UBO の offset 0 を pending submit がある間に再利用 |
| B04 | P1 | コード確定 | terrain の単一 descriptor set を pending 使用中にも更新可能 |
| B05 | P1 | コード確定 | FrameArena の同一 frame 中の growth が旧 host buffer を即破棄 |
| B06 | P1 | コード確定 | terrain segment の即時再利用と transfer の prior-read 同期不足 |
| B07 | P1 | コード確定・既定無効 | indirect packets が region ごとに同じ offset を上書き |
| B08 | P1 | コード確定・原典照合 | copy cache がバニラの7段階 panorama blur を同一コピー扱い |
| B09 | P1 | CPU再現 | copy key が違う矩形でも衝突。source identity / content も含まない |
| B10 | P1 | コード確定 | post chain が resize 後も旧 FrameImageTarget を保持 |
| B11 | P1 | コード確定・条件付き | post の32-slot descriptor / UBO ring に completion ownership がない |
| B12 | P1 | コード確定・条件付き | acquire semaphore を cursor だけで再利用 |
| B13 | P1/P2 | 既知の残りを確認 | descriptor cache の FIFO producer と継続回収が欠ける |
| B14 | P2 | コード確定・新規差分 | item cache の negative / tint side cache は無制限で reload hook なし |
| B15 | P2 | CPU再現 | arena promotion の sealed region が abandon では解放されない |
| B16 | P2 | CPU再現 | font fallback の一部拒否を全 glyph accepted と数える |
| B17 | P2 | CPU再現・原典照合 | pass timing が world とその内側を二重に差し引く |
| B18 | P2 | コード確定 | query が未完了でも slot / pass attribution を捨てる |
| B19 | P2 | CPU再現・互換差 | entity tick cache は同一 tick の集合 / render-pass 変更を見ない |
| B20 | P2 | 既知の残りを確認 | PostChain / Target の段階的 create に rollback 不足 |
| B21 | P2 | コード確定 | viewport / scissor query がミラーした実状態を返さない |
| B22 | P2 | コード確定 | compositor wrapper が launcher failure を終了コードに返さない |
| B23 | P2 | 原典・上流照合 | fast block dispatch は upstream の mod hook と同等ではない |
| B24 | P2 | ドキュメント | goal / plan / perf 表に撤回済み原因と古い状態が残る |

これは24個の独立した GPU 不具合を実機再現した表ではない。B03〜B07 / B11〜B13 には非同期 ownership の共通根因があり、まとめて契約を修復できる。以前の24項目 ledger とこの表の ID は別物である。

## 2a. 既存 BH-01〜37 の引継ぎと作業入口（2026-10-12 20:06 JST）

**監査を読む入口は本書。現在の担当・優先順・受入の更新先は docs/plan.md。**
古い監査を別々の未処理キューとして実行しない。本書の B01〜B24 と下表で重複をまとめ、同じ根因・所有契約を一つの作業単位として扱う。
docs/history/2026-10-11-code-bughunt.md は起票時の根拠。冒頭の件数も現在の判定ではない。
本表は修正履歴を照合した引継ぎであり、37件全部を最新HEADで再受入したとの主張ではない。以後の修正はcommit・受入証拠とともにplanへ反映し、本書の固定時点と区別する。

| 既存ID | 引継ぎ判定 | 今回の監査との対応・残る作業 |
| --- | --- | --- |
| BH-01, 02, 03, 04, 05, 06, 07, 08, 10, 11, 12, 13, 15, 16, 19, 23, 25, 27, 28, 29, 30, 32, 34, 36, 37 | 修正・受入の履歴あり（各件の受入範囲は異なる） | 旧指摘をそのまま再実装しない。BH-27の待ち削減はB03〜B07等のpending ownershipを免除しない。BH-28のarena導入後の穴はB05/B15。BH-36のindirect実装後の穴はB07（既定off）。BH-15の安全な失敗処理を全FBO対応完了としない。 |
| BH-09 | clip経路の受入あり | 5cc4002f0 / 5586370ff。強制viewportでscissorCapturedGroups=63。旧「受入未取得」は失効。ただしB21のquery/save-restore不一致は別契約。 |
| BH-18 | create引数の修正・CPU契約受入あり | 0a8b8d253。surface条件の合成入力を検査。未行使のrecreate条件やB12のacquire semaphore寿命まで受入済みとしない。 |
| BH-35 | 広告境界の修正・受入記録あり | 4fb0b581b。広告をGL3.0 / GLSL1.20等の列挙した下限へ変更。全GL機能や全mod互換の実装証明ではない。B21のquery値の意味は別。 |
| BH-14 | 未完の引継ぎ | 非main FBOごとのidentity / generation / size契約。B10のpost resizeも合わせ、main / owned / adopted targetの所有境界を整合する。B10だけ直してBH-14全部を閉じない。 |
| BH-17 | 未完の引継ぎ | 128B push-constant baseline。通常immediateが208B以上を要求する状態と、postのUBO化を区別。本書の能力・配布条件も参照。 |
| BH-20 | 部分修正・残りあり | acc99fe3cのpipeline rollbackはfault実測済み。未解決と一括表示しない。PostChain / Targetの残りはB20へ統合。 |
| BH-21 | 受入完了を確認できていない | world destroyのCPU/off-heap/staging所有とworld-switch/reloadのcreated/released census。今ある資源解放を保持し、資源分類と不足分を確認する。 |
| BH-22 | 部分修正・残りあり | 退役とcensusは追加済み。継続再利用・FIFO producer・generation churnの枯渇対策はB13へ統合。 |
| BH-24 | 未完の引継ぎ | UBO交換のtransactionと旧bufferのconsumer完了までの保持。B03/B04と一緒に修復するが、毎recordingのrange再利用とgrow失敗rollbackは別の受入条件。 |
| BH-26 | 未完の引継ぎ | 未submit / discard時のlayout・first clearのpending/confirmed契約。新Bの番号だけではこの要件を代替しない。 |
| BH-31 | 判定欠落・未解決扱いで保持 | 非一様scale / shear時のnormal lighting。旧解決一覧・残件一覧の両方から抜けていた。既存修正と原典のnormal matrixを照合し、数値対照・画像受入が得られるまで閉じない。 |
| BH-33 | 設定側のみ対処・描画機能未完 | fade未対応を有効に見せない対応は保持。fadeそのものの移植・設定連動・画像受入は別に必要。 |

**作業順**: 既知P0のpanorama回帰を分離して修復 → B01/B02/B14のitem cache契約 → B03〜B07/B11〜B13/B15とBH-24/26の非同期所有 → B08/B09/B10/B20とBH-14/20/21のtarget・copy・寿命 → BH-17/31/33および互換・計器の残り → 正しい画像を維持した性能改善・主要content mod受入。
同じ巨大backendの並行編集を増やさず、担当ファイルとinterfaceを決めて進める。未受入のdirty修正を完了へ繰り上げない。

## 3. 最優先: PR #21 の item cache

### B01 — 使用範囲を確定しないまま容量全体を保存

対象: forge122/.../mixin/core/RenderItemMixin.java の buildPlan / emitSegment / bytes、原典 BufferBuilder / Tessellator / WorldVertexBufferUploader。

scratch は 1 << 17 byte の BufferBuilder を begin し、renderQuads の後で WorldVertexBufferUploader.draw(scratch) を直接呼ぶ。その後 bytes() は getByteBuffer().asIntBuffer() の remaining 全体を保存する。

原典では **Tessellator.draw が finishDrawing を先に呼ぶ**。finishDrawing が isDrawing を閉じ、position=0、limit=使用サイズにする。WorldVertexBufferUploader.draw は finishDrawing を呼ばず、最後に reset する。reset は vertexCount を0にするだけで容量の limit を縮めない。Celtium の native uploader capture も finishDrawing の代わりにはならない。

**CPU witness**: 24頂点、168 int の cube を作り、draw の reset 後に実 bytes メソッドを呼ぶと **32768 int が保存され、replay は4681頂点になる**。期待は24頂点。使用データは672Bなのに保存量は128KiBになる。

これは first miss の正常表示と両立する。hit が余分な頂点を再提出したとき、degenerate geometry になるか、末尾頂点が拒否されるか、どの画像差になるかは GPU で未確認。少なくとも「同じ仕事を省略しただけ」とは言えない。

**修復**: finishDrawing を含む producer 契約を揃え、使用範囲を draw/reset 前に保持する。ByteBuffer の position / limit と int count / stride / primitive count を検証する。capacity を頂点数にしない。

**受入**: 同じ model の miss と hit で頂点数・index数・画像が一致。小さい item、複数 ITEM quad、JEI 全ページで不要 upload bytes が増えない。runtime の CELTIUM_ITEM_MESH は p-im3 で発火していたため、未登録・死んだ注入という説明にはしない。

### B02 — hit と miss で outgoing lighting が違う

同 class の buildPlan は hasLighting が立つと最後に lightmap coordinates と enableLighting を復元する。replay は各 segment で disableLighting / enableLighting を適用し、lastBrightness の Java field を戻すが、**ループ後の Forge cleanup を持たない**。

原典 ForgeHooksClient.renderLitItem は、最後に hasLighting の条件で lightmap と lighting を復元する。cache hit の最後が diffuse=false の segment なら、その先の renderer に lighting=false が漏れる。

**CPU witness**: lighting=true で入り、shade=false / updateShading=true の実 replay メソッドを実行すると、戻り時は lighting=false。第一回と第二回で外部に見える状態契約が変わる。

**修復**: plan に cleanup 契約を持たせ、miss / hit / exception の outgoing state を同じにする。Java field の復元と native state の復元を区別する。

**受入**: mixed diffuse item → 普通の item / 手持ち / entity / GUI を同 frame に続け、hit/missで明暗・状態が一致する。

### B14 — 256-entry の外側に無制限 cache がある

positive の itemMeshes は256 entryに制限される。しかし uncacheableMeshes は HashSet、tintedModels は IdentityHashMap で、class 内に削除や reload invalidation がない。tinted / negative model だけでなく、tint 判定した通常 model も後者に保持される。

モデル同一性だけを永久的な geometry の不変条件にもしている。IBakedModel.getQuads が同じ object のまま変化する mod では、cache hit が再評価を飛ばす。未特定 mod の故障を実測したとはしないが、公開 interface に「object identity なら quads は永久不変」という保証はない。

**修復**: positive / negative / tint 判定を同じ resource epoch と bounded lifecycle に置く。static geometry の根拠がない model は cache 対象にしないか、変化を検出する。reload の convergence と cache miss/hit の model hook の動作を確認する。

## 4. 非同期化後の資源所有

### B03 — terrain UBO の範囲再利用

VulkanTerrainGpuBackend.beginCommands は4個の command slotを回し、**選んだ slot の fenceだけ**を待つ。最後に uniformWriteCursor=0 を設定する。writeUniforms は単一 VulkanUniformBuffer の mapped memoryに、その cursorから直接書く。

opaque / cutout / translucent や次の frame が異なる pending command slotを使っても、同じ offset 0 の region/fog/camera blockを上書きできる。HOST_COHERENT は cache visibility の条件で、GPU completion の証明ではない。

growth も ensureCapacity が旧 buffer/memory を直ちに destroy/freeする。atomic replacement の allocation failure 対応はあるが、pending reader の lifetime 対応にはなっていない。

**修復**: submit slotごとの UBO range / allocationを割り当て、最後の consumer completionまで再利用しない。growthで旧 generationをretireする。全 frame待ちを常時復活させるだけの対応は最適化の後退になる。

**受入**: GPUが遅い場合にも複数submitをpendingにし、camera/region/fogが異なるpassの実使用blockを確認。capacity growthも行使する。

### B04 — terrain descriptor の更新中使用

TerrainPipeline は maxSets=1、単一 descriptorSet。bindTextures は値が同一かを確認せず、UBO / atlas / lightmap の3 bindingに vkUpdateDescriptorSets を毎pass行う。set layoutに UPDATE_AFTER_BIND / UPDATE_UNUSED_WHILE_PENDING の契約はない。

直前の同じ terrain pipeline を使った command が pending の場合、この更新は Vulkan の descriptor usage 条件に反する。diagnostic readback や速い GPUで fenceが既にsignalしている runでは露出しない。

**修復**: immutable descriptor generationをbuffer/view/samplerの完全なidentityでcacheし、pending中のsetは書き換えない。変更がないwriteの省略と、変更時のgeneration分離をセットで実装する。

**受入**: 同一passを複数pending、texture / UBO generation変更、reloadを含むvalidation run。画像とdescriptorに残るresourceの寿命を両方確認する。

### B05 — FrameArena の growth が既存consumerを破壊

FrameArenaは同一 frameのslotへsliceを追加できる。slot.awaitingCompletion / pendingGroups があっても ensureVertices / ensureIndices は HostBuffer.ensureCapacity を呼べる。

HostBuffer.ensureCapacity はreplacementへmemCopy後に旧bufferをdeleteし、即時unmap/destroy/freeする。同じframeの先のwindowが旧handleで提出済みでも保持しない。FrameArenaのframe間reclaim guardは、このframe内growthを保護しない。

**修復**: backing generationを固定したchunk/sliceに分けるか、旧allocationをlast-consumer ticketまでretireする。容量を大きくしただけでは契約を修復したことにならない。

**受入**: 1MiB vertex / 256KiB indexの初期容量を越える前後にsubmitを入れ、旧handleがcompletion前に解放されないことを検証。

### B06 — terrain allocation の再利用と転送前同期

uploadRegion は old section mesh/indexをremoveした後に、新しいsegmentをallocateする。Segment.delete → Arena.free は即座にfree/coalesceするため、前のdrawが使うrangeに新しいuploadを置ける。

Context.flushUploads のbarrierは全copyの**後**の TRANSFER_WRITE → vertex/index/shader 等の読みを保護する。prior graphics read → 新しいtransfer write の順序や、copy同士の同一rangeのWAWは別契約で、同じqueueというだけではmemory/execution dependencyにならない。

**修復**: freed rangeをlast draw completionまで再利用しないか、正しいprior-read → transferのdependencyを明示する。特にcamera movementによるsort index交換とsection rebuildを含める。

**受入**: pending terrain drawと同一section rebuild / sortを重ねる。最終bufferの内容確認だけで、以前のdrawが読むデータの正しさを受入にしない。

### B07 — indirect packets の上書き（既定無効）

tryIndirectBatch はregionごとにfirst=0とし、terrainIndirectBufferの同じoffsetへpacketsを書き、そこをvkCmdDrawIndexedIndirectへ渡す。buffer writeはpendingUploadsへ入り、graphics submitの前にまとめてflushされる。

複数regionのdrawを記録した時点で、先のregionも後のregionのpacket内容を読み得る。direct/indirect checksumは生成した入力同士を比較しており、GPUが読む最終rangeを検証していない。

**既定は off**。通常プレイでこの経路が壊れているとは言わない。OPT-09を有効化する前の修正条件である。

**修復**: recordingごとにappendするpacket allocatorとregion offset、pending中のgeneration保護。allocation growth中に既に記録したhandleも破棄しない。

**受入**: 異なる2region、opaqueとsorted、draw-count clampで、実GPUの各rangeと画像がdirectと一致する。

### B11 — post descriptor / UBO ring は回数でしか守っていない

PostChainは32set、32 UBO slotのringを持つ。setsThisRecordingは一回のrecordingの32pass超を拒否するが、setCursorのwrapを以前のsubmit completionと結びつけない。commitPassesはtarget initializationのsubmit確定であり、ring slotのGPU完了通知ではない。

4 command slotがあり、1 recordingのpass数は最大32。たとえば複数pending recordingが合計32setを越えれば、単一recordingのbudget内でもpending descriptor / uniformを再利用できる。

**修復**: ring allocationをsubmit ticketで所有し、completed prefixまでreclaimする。VUID 0の短いoutline chainだけで全programのring寿命を受入にしない。

### B12 — acquire semaphore のrotationにcompletion条件がない

Swapchain.acquireSemaphore はimage count相当のpoolをcursorで回す。使用したacquire semaphoreを待つsubmitのcompletionとpool slotを結びつけない。Contextのdeferred submissionは非同期である。

複数imageを先にacquireしてGPUが遅れたとき、rotationが既存のpending waitを持つsemaphoreへ戻れる。image-indexで管理したrender-finished semaphoreは改善されているが、acquire側は別の寿命である。

**修復**: acquire slotをそのwaitを消費したsubmit completionと結びつける。poolを増やすだけで安全性を仮定しない。

**受入**: GPUが遅いGUI-only frame、FIFO / MAILBOX、swapchain recreation、acquire failureでVUIDと資源が安定する。timeoutのLong.MAX_VALUEは実質非常に長いが、UINT64_MAXとは異なるため、そのVUID違反とは断定しない。

### B13 — descriptor cache の回収は完成していない

ImmediateTexturePipelineのFIFO evictionは insertionOrder が非emptyであることを要求する。しかし全referenceを追っても、同listへのaddがない。cache limit=64はこのevictionを実行できない。

setはpoolごとに64、poolは最大64。累積generationが増えれば、4096set程度で新規allocationが拒否される。retiredSetsもcompletion後にfree/reuseするconsumerがなく、他のimmediate familyにも累積setの寿命が残る。

**boundedに確保を止めること**と、**長時間のgeneration churnを継続できること**は別。BH-22を単に全て再オープンするのではなく、この残りのproducer/consumerを閉じる。

**修復**: immutable setのcache eviction、completion後のpool/set recycleを揃える。古いdescriptorをpending中に書き換えない。generation keyの変更ごとに恒久allocateしない。

## 5. texture / FBO / postprocess

### B08 / B09 — copy の省略条件がcontentを表していない

TextureCopyService.serveTextureCopyはdest/level/offset/rectangleをXORで混ぜたlongを、present serial単位のsetで重複判定する。source image / readFbo / content generationはkeyにない。判定はpending geometryをflushする**前**に行われる。

原典GuiMainMenu.renderSkyboxはrotateAndBlurSkyboxを7回呼ぶ。それぞれ同じ256×256のglCopyTexSubImage2Dを行うが、間にdrawがありsource contentsは更新される。現cacheは同一present中の2回目以降をservedとして戻せる。

さらにkeyはinjectiveではない。CPU witnessの同destについて、(x,y,width,height)=(0,0,64,64)、(1,16,64,64)、(0,0,65,68)が **全て30064771392** になる。異なる正当なrequestが省略される。

**修復**: exact tupleでkeyを構成し、source content / image generationを条件に含める。contentが更新される操作は省略しない。panoramaをMinecraftのsemantic passとして扱う改善は可能だが、7段の結果を保持する。

**受入**: panoramaの各iterationをGL参照と比較。異なるsource FBO、同frameのdraw→copy→draw→copy、同dest異なるrect、dest再定義を行使する。copy count減少だけを成功にしない。

関連する残り:

- readback fallbackの診断はpixels[0..3]を無条件に読む。1〜3 pixelの正当なcopyで診断自身が例外を起こせる。
- native blitのdest transitionはTOP_OF_PIPE → TRANSFERで、既存のsampling readerのexecutionをscopeに含めない。src framebufferのcolor-write barrierとは別に、destのprior-useを確認する。
- blitRectangleFromはpixel contentを変えるが、lastPixelHash / lastUploadSerial等のCPU由来skip cacheを更新しない。後続CPU uploadの省略条件も合わせて無効化する必要がある。
- 読めるFBOの解決は専用main/default/chain pairに限定される。任意のForge/Mod FBOが提供されるわけではない。未所有のreadを拒否すること自体は正しい。

### B10 — post executor の frame target がresizeに追従しない

PostChainはcreate時のFrameImageTargetをfinal fieldで保持する。backend.ensureFrameTarget(width,height)はdeviceをsettleして旧targetをdeleteし、新targetを作るが、postChainをrebind/recreateしない。destroyFramebufferもpostChainを処理しない。postChainがnullになるのは別の全体delete側だけ。

その後のinIsMain / outIsMain / auxIsMainは旧targetのimage/viewを参照する。GPU lifetimeがsettle済みでも、**新しいtargetへ移っていないというidentityの欠陥**は残る。

**修復**: frame-target generation変更をexecutorに明示し、owned/adopted target・main framebuffer・descriptorも整合して交換する。

**受入**: spectator effectをONにしてresize/fullscreen切替、effect切替、通常描画へ復帰。outlineを含む全vanilla postのbefore/after画像をAMD/Intelで確認。

### B20 — 段階的 create のrollbackの残り

ImmediateTexture/Item等のfactoryとsemaphore poolにはrollbackが追加されている。古いBH-20の全範囲が今も未修正とはしない。

一方、PostChain.createにはrenderPass、setLayout、descriptorPool、uniform buffer、sampler、32setの段階的確保があり、全体のfailure rollbackが揃っていない。Target.createもimage/memory/view確保後のbind/view/framebuffer failureで局所handleが戻らない。

**受入**: 各段階のfaultでlive handleがbaselineへ戻り、次の正常createが成功。失敗途中で一括deleteできるownerをpublish前に持つ。

## 6. CPU ownership / 互換 / 計器

### B15 — arena promotion がsealed sliceを置き去りにする

DynamicMesh.promoteFromArenaは旧Region.abandonを呼んで参照をnullにする。Region.abandonはsealedならreturnする。通常append後はsealFrameSliceでsealedになり、pendingGroupsが加算される。

そのsliceがcapacity超過でpromoteされると、旧pendingGroupsは減らず、completion後もarena slotのreset条件を満たさない。

**CPU witness**: 2slotそれぞれでseal→abandon→submit→completeを行うと、3frame目のopenMeshがnull、blockedOpenings=1。GPUを遅らせなくてもCPU ownershipだけで再現する。

**修復**: promotionで旧sliceのownershipを正しくretire/releaseする。unsealedな書込み中止と、sealedなconsumer付きsliceの解放を混同しない。

### B16 — fontのacceptedに部分失敗が隠れる

FontGlyphCapture.flushBufferedQuadsはfast appendがfalseならglyphごとにfallbackする。takenをORし、一件でもtrueならaccepted += bufferedQuadCountとする。全glyph拒否でもrefuseを必ず記録する構造ではない。

**CPU witness**: 2 glyph中1件だけacceptedでもcounterは2になる。glyph accepted=... / refused=0だけで欠落なしとは言えない。

**修復**: fallbackの各結果を集計し、全受理 / 部分受理 / 拒否を区別。accepted / recorded / submitted / completedの段階を保つ。

### B17 — frame pass split のoverlap

FramePassTimings.TOP_LEVELはworld、hand、clouds、weather、gui。residualはframeから全TOP_LEVELを引き、負なら0にする。

原典renderWorld / renderWorldPassの中でhand、clouds、weatherを実行する。これらをworldの内側として測った場合は二重控除になる。現runでもworld/hand/cloudsの非zero計測を確認したが、Cleanroomの変換後の厳密なnestingはtraceで確定してからinstrumentを修正する。

**CPU witness**: 実計器にworldの内側でcloudsを与えると、true outside world=9605nsなのにresidual=0。値をclampしているためoverlapが見えなくなる。

**修復**: inclusive / exclusiveの定義を分け、frameとの比較は互いに重ならない区間だけで行う。frameごとにresidualを計算し、カテゴリのp50同士を足し引きしない。

### B18 — timestamp のpending結果を失う

backend.updateTimingsBodyはWITH_AVAILABILITYで未availableのqueryをskipしても、最後にtimestampPassesをclear、timestampSlots=0にする。beginCommandsは単一pool全体をresetする。

未完了分の帰属を保留せず、後のrecordingのqueryに同じslotを再利用する。GPU性能の判断に使う前に、submit slot / query range / collection completionを一つのownerで管理する。

### B19 — entity tick cache の互換差

EntityGathererはworld objectとtotalWorldTimeだけでcacheを返す。same tick中のloadedEntityList変更やshouldRenderInPassの結果変更を再評価しない。

**CPU witness**: entity追加後も同tickでは1件、次tickで2件。render-pass eligibilityを変えても同tickでは2件、次tickで1件。

バニラの通常tick運用で目立たなくても、render eventなどから集合/eligibilityを変えるcontent modへ同等性は保証しない。特定modの不具合を再現したとはしない。

**修復**: entity membershipのinvalidationとpass eligibilityの再評価を分ける。性能改善を維持しつつ、世界tickだけを不変条件にしない。

### B21 — queryの値がsave/restoreに使えない

stubはscissorBoxを記録するが、getInts(GL_SCISSOR_BOX)は(0,0,viewportW,viewportH)を返す。GL_VIEWPORTもx/yを0として返す。depth func / fog mode等も固定値のqueryが残る。

PR #20はscalar queryのFFIを省略したが、**元のqueryの意味を正した変更ではない**。save/restoreするmodがmirrorへ誤った状態を戻し得る。

**修復**: 対応しているstateの照会は同じownerから返す。非対応値はnativeの対応範囲に応じて明示する。OpenGL全APIの一般エミュレーションへ拡大しない。

### B22 — wrapperの終了コード

scripts/compositor_capture.pyはlauncher.waitのcodeをsummaryへ保存するが、normal pathでreturn 0する。launcherのGPU mismatch / validation failureがwrapperのexitで見えない。

**修復**: captureの成否とゲーム受入の成否を別fieldで持ち、外側のexitへ反映する。画像が撮れたことだけでGPU要件を満たしたとしない。

### B23 — upstreamのmod入口が同等という主張の範囲

最新upstreamのBlockRendererDispatcherMixinはrenderBlock内部のmodel callだけをwrapする。コメントも、modがrenderBlockをoverride/injectする機会を維持することを明記している。

CeltiumはMODELをmeshing taskから直接VintageBlockRendererへdispatchする。通常のBakedModelを処理する範囲が同じでも、dispatcher / getModelForStateのmod介入点は同じではない。原典のgetActualState例外を捕捉して継続する処理も直接経路と異なる。

他のrendering overhaulとの併用は対象外でよい。しかし普通のcontent modの描画入口まで一律に対象外にしない。最適化のcoverageとAPI/hook compatibilityを別項目にする。

## 7. native化・汎用変換器・scope

- 地形はnative buffer / pipeline / draw。CPUのgraph / meshing / sorting / cached batchが接続されている。
- immediate / model / font / particle / GUIはMinecraftの既知formatとgroup/stateを受けるnative経路。既定受付はformat 1〜10。POSITION_NORMALを未対応とする古い記述は現コードと不一致。
- 実window/compositorへ届く証拠は既にある。scanoutそのものは未測定として区別する。
- stubの存在はGL driverで描画していることの証拠ではない。native側の結果をGL stubのbootstrap probeがeffectiveTerrainBackend=opengl / nativeTerrainImplemented=falseと報告するため、reportのscope=bootstrap-onlyを必ず読む。
- 未対応のGL symbolをno-op trampolineで答える入口、固定query、fake compile/statusが残る。未知GL shader・任意client array・特殊FBOを全て動かせるという保証はない。
- 現構造を「GL API全体を実装した汎用変換器」とは判定しない。ただしbackendにstate capture、shader profile、target routing、resource lifetime、diagnosticsが集中する。新規modはsemantic adapterと明示した対応表で追加し、無制限なGL機能増設へ進まない。
- direct matrix/GL操作はmirror miss/no-op censusと実画で確認する。メソッド名やdecompileだけでフックの不在を断定しない。

未検証GPU/OSと最低featureを明記する。ContextはVulkan 1.2と**maxPushConstantsSize>=208B**を要求する。postにUBO routeがあってもimmediate family全体が128B機器で動くわけではない。baseline拡張として128Bのrouteを設計する余地は残る。mesh shaderは必要ない。

## 8. Celeritas差分の独立確認

最新upstreamとのJavaの比較で、**same 321 / changed 36 / new 42** を確認した。これはsnapshot側のcommon・forge122に存在するファイルの集計。upstream-onlyファイルの数まで含めた対称diffではない。

| 領域 | 独立確認と留保 |
| --- | --- |
| section graph / occlusion traversal | SectionGraphの差はthread名。RenderSectionManagerのlist生成とCeleritasの可視判定を保持 |
| meshing scheduler | 固定参照のChunkBuilder、ChunkBuildOutput、RasterBudgetをbyte単位で比較し同一 |
| translucency / sorting | 上流のCPU sorting体系を維持。index rangeのGPU再利用はB06として別に監査 |
| cached multidraw batch | backendがgetCachedMultiDrawBatch / isValidFor(camera int-pos)を実際に呼ぶ |
| GL device layer | native VKへの置換であり、GL classの有無だけで最適化喪失とはしない |
| native draw aggregation | 既定は各packetごとのvkCmdDrawIndexed。cached CPU batchの存在をGPUのmulti-draw成立と同一視しない |
| fast block renderer | 機能のCPU経路は保持。B23のmod介入点差がある |
| SectionLattice | 固定参照と同一。最新7818b33の高さwindow変更は取り込まれていない |
| raster occlusion | useRasterOcclusionCulling=falseは両者共通。現在使っている最適化を失ったという差ではない |
| EntityGatherer | tick cacheはCeltium追加。B19の同等性条件を付ける |
| newer restricted Sodium | 本監査でcopy/translation/cherry-pickを確認したという主張はしない。一般アルゴリズムの独立実装とlicense根拠を維持 |

**「CPU最適化の主要な仕組みを保持」は支持できる。そこから「上流差分を取り込む余地は全てゼロ」「GPU draw効率も同等」「mod入口も同等」までは導けない。**

## 9. フル最適化余地 — 実装済みをやり直さない

| 優先 | 仕事 | 現状・根拠 | 採用条件 / 弱いGPUへの配慮 |
| --- | --- | --- | --- |
| 0 | B01/B02のitem cache修復 | 必要データに限定してから速度を測る | miss/hitのgeometry/state同一。cache無効との比較 |
| 0 | submit resource ownership | B03〜B07/B11/B12/B13 | 常時全GPU待ちを戻さず、range/generationをcompletionで管理 |
| 1 | window size / surface照会の削減 | ensureFrameTarget・ensureSwapchainが繰返しnative照会 | GLFW size/DPI callbackとframe単位のsnapshot。全GPUに効くCPU削減 |
| 1 | semantic boundary内のsubmit集約 | window-splashで2684 submit / 202 frame。cross-phase計数なのでFPS倍率にはしない | target/read/write/upload順序を保持。単なるwindow budget拡大で隠さない |
| 1 | 安全なterrain indirect packets | 存在するが既定off、B07 | multiDrawIndirectをquery+enable。非対応GPUはdirect baseline |
| 1 | immutable descriptor世代と回収 | terrainのwrite毎回、immediateは累積allocate | complete後recycle、sampler/view/UBOの正しいidentity |
| 2 | unicode fast glyph全page | fast routeはch<256。日本語等は旧glyph captureも行使する | 原典のwidth/italic/bold/shadow/format順序を保持。texture array必須にしない |
| 2 | model compile inverse / scratch | model drawがmatrix clone / inversion / remapを毎callする箇所あり | compile generationごとに不変部分をcache。まずJFR帰属 |
| 2 | allocator / arenaの改善 | shared frame arenaは既存。growthとpromotion契約は未完 | pool/chunk分割、実peakに応じたbounded容量。巨大一括確保をbaselineにしない |
| 2 | driver VkPipelineCache | 多くのcreateGraphicsPipelinesでcache=VK_NULL_HANDLE | device/driver/shader UUIDで無効化。cold hitch改善をsteady FPSと混同しない |
| 2 | texture uploadの範囲と変換 | write serial skipは既存。部分更新/再変換/ByteBuffer配分は要帰属 | dirtyと実pixel更新を区別。staging bytesと二重copyを測る |
| 2 | native GL query roundtrip削減 | PR #20で主要scalar queryの直接取得が入った | 完了済みを再実装しない。query意味の修復はB21 |
| 3 | Game-side tick / loading帰属 | renderer bracket外の大きな時間がある | 実world・tick/logicの正しさを保持。fixtureが作る負荷を先に分離 |
| 3 | optional GPU culling / sort | 追加余地だが今の計測だけでROIは不明 | graph/culling baselineを保持し、readback待ちや見落としを導入しない |
| 3 | final frame copy / bandwidth | presentのflip blitはnative。直接swapchain描画は可能性 | post/FBO/copyの意味を保持。単にblitを削るとorientationとconsumerが壊れる |
| 3 | capability baseline拡張 | 208B push requirement、Linux surfaceのみ実装/検証 | 128B+UBOなど。mesh/bindless/新しいGPUを必須にしない |

既存の改善: CPU-splitは既定off、ThreadMXBean lookupはcache済み。write serial、frame arena、state command skip、font batching、frame semaphore省略、16MiB staging ring、dispatch/matrix scratchは存在する。「これらを今から導入すれば速い」という重複案にはしない。

**回収対象の待ちが特定runで0になったこと**と、**renderer全体の最適化余地が尽きたこと**は別。required backpressure / teardown / readback待ちは理由ごとに保持する。

## 10. 実測を読んだ結果と、性能判定の限界

### 既存runの読み直し

| run | cold p50 / p95 / p99 ms | warm samples | image verdict |
| --- | --- | --- | --- |
| r166-gc-pre16 | 41.06 / 130.33 / 208.40 | 0 | FAILED_PRESENTED_IMAGE |
| r166-gc-post16 | 41.30 / 61.65 / 89.80 | 0 | FAILED_PRESENTED_IMAGE |
| r166-gc-post16b | 34.34 / 59.22 / 70.41 | 0 | FAILED_PRESENTED_IMAGE |
| goalG4-amd | 37.49 / 78.75 / 98.73 | 0 | FAILED_PRESENTED_IMAGE |
| goalG4-intel | 38.97 / 54.50 / 70.39 | 0 | FAILED_PRESENTED_IMAGE |
| window-splash | 32.08 / 62.65 / 390.23 | 0 | PASSED |

r166等のFAILEDはreportが**requested picture did not arrive**と記録している。黒い画像が実際に保存された証拠とは違うが、**画像同等の性能受入が成立した証拠でもない**。そのverdictを「既知の正常」に言い換えない。

cold windowはworldTicks 120〜200、warmは400〜600。上のrunはwarm未到達である。holdUntilTickなどwarm到達の手段は既に別branchに実装済みなので、fixtureに永久にwarmを測れないという主張もしない。

window-splashのVERIFYではuniform growth=2、submitSequence=2684 / completed=2682、deferred outstanding=1が報告される。これだけで危険な重なりが発生したとは言えないが、全submitが常にCPUから同期完了しているという説明は使えない。

### JFRの再検査

SWE2のp-rw2.jfrを既存計器として解析した。SHA-256: 2300ebf784e3a07fbaaa6710d0cd60193d911ff7aeb3a61cd2e18e4ced8ad4cf。sidecar HEAD 368e03b56aadad5369104094214a0a4c47f76540。recordingは18:38:52〜18:39:20 JST、約28秒、起動を含む。

rendererを含むstackからthread identityを確認すると **javaName=main / javaThreadId=3**。ログ表示名Client threadでfilterするとsampleを全て落としてしまう。初回filterの空結果を描画の不在とは読まず、identityを訂正して解析した。

そのthreadでJava 411 / native 515 sample。first renderer frameとしてwindowFramebufferSize 108、ensureSwapchain 64、matchesSurfaceExtent 30が出る。**これは28秒の起動混在recordingのsample数**であり、steady FPSの割合やGPU待ち時間ではない。

Inflater / ASM / file canonicalize / Zip等も多い。これをrenderer本体の定常コストに割り振らない。allocation sampleのweightは推定量で、class別sample数やexact allocation bytesとも別。

このrunの報告warm samplesは0、image verdictはFAILED。5.15msのcold p50だけで「完成版がこの速度」とはしない。

### 次の性能受入

- 同一scene/camera/animation/capture tick、同じjar/fixture/driver/settings、同じ描画のpixel結果。
- cold、到達したwarm、通常プレイ、world-switch/reload、JEI scrollを別区間で保存。
- frameごとのrenderer / game tick / present / upload / GPU timingを同じ区間で対応させる。
- p50同士やrun全体averageとwindow p50を足し引きして残差を作らない。
- p99は十分な測定時間とframe数を取り、startup / PNG / diagnostic readback / GCとの時刻対応を確認。
- 60Hzの参照present間隔をrenderer CPU/GPU時間として扱わない。
- AMDとIntelのnative device line、gpuRequirementMet、validationExitCodeを読む。compositorのGPUだけで分類しない。

## 11. 監査中に進んだ修正の扱い

- loadingの初期白区間は241c13473でframe-reached-the-queue ownerを修正。修正前の「ロード画面だけ残る」を現在のstatusへ固定しない。
- Intel名目runがAMDだった誤りは39da33e30 / 7ed349144で撤回。721e454d9でDRI_PRIMEによるVulkan選択とnative device行を確認した再受入が記録された。正しい要件文字列だけではGPU選択にならない。
- loading修正後のpanorama regressionは601ea924aでP0として記録された。36530f542でidentity 0のdraw/read routingを揃えたが、**89d331328でcandidateも灰色menuのままと記録され、P0はopen**。a87d1fe22ではcopyはframe imageを読む一方window passのrecord先を追っている。本監査はその修正作業に割り込まず、この現在の失敗を保持する。
- PR #20のscalar query fast routeは本監査中に入り、重複最適化案から外した。
- PR #21のitem cacheを新規deltaとして読み、B01/B02/B14を追加。snapshot固定後の変更を無視して古いバグ一覧を返していない。
- これらの修正でB03〜B13等のownership問題が消えたとは扱わない。関連メソッドの再確認結果を保存した。

## 12. release / modern API / license

最新の生成mod jarもclass headerを検査した。celtium-forge-mc1.12.2-0.0.2-alpha-dev.jar、SHA-256 68fc425ed08e6e2f251ab576f47ef368df198955ccd8b78f6c99c7b631a4acdc。major 46/51/52/65、65超なし。net/minecraft / net/minecraftforge classを同梱していない。COPYING / COPYING.LESSERと第三者noticeがある。

これはそのjarの検査であり、後からbuildされたjarや未公開release 0.0.3の受入証拠へ転用しない。ゲーム/buildのJDK25と、配布classのrelease21は区別する。

Cleanroom専用化は維持する。windowを探すClass.forName(Display)+reflectionがhot pathに残るため、正式なwindow ownership/APIへ寄せる余地がある。旧package名だけでLWJGL2依存と判断せず、実際のCleanroom/LWJGLx提供物を確認してtyped boundaryを作る。

rootのCOPYING / COPYING.LESSERとupstream attributionは残っている。本監査は全ソースのlicense chainについて法的結論を出すものではない。PR #21が参照するAngelica PRのsource/version/license/provenanceも配布説明と合わせて記録する。

## 13. B24 — canonical docsの修復

docs/goal.mdには古いwedge=月quad原因、POSITION_NORMAL未対応、文字が最優先等が残る。これらは現コードと撤回済み主張に矛盾する。headerのphase/roundも重複している。

docs/plan.mdは最新行を追加しているが、前方に古いfont未確認・TESR等の記述が残る。perf-statusにもPR open / mechanism未証明 / branch-onlyの状態が混在する。

**修復**:

1. plan冒頭を最新の実装・局所確認・GPU受入・未受入の一表にする。
2. 旧roundはhistoryへ移し、末尾の訂正だけで整合したことにしない。
3. goalは完成objectiveと残課題の優先を同期し、goal IDと最後の自律実行方針を保持。
4. 新しい発見はこの監査IDとclosure evidenceを結びつける。docs/historyを現在の完了判定にしない。

本監査では実行中のgoalを上書きしていない。active / armed / runningは別のlive状態であり、goal.mdの古いheaderから推定しない。

## 14. 最短の修復単位と分担案

| 単位 | 内容 | 主なowner | 必要な受入 |
| --- | --- | --- | --- |
| U1 | item cache range、hit/miss cleanup、bounded epoch | RenderItemMixin | cube/tinted/mixed-shade/dynamic model、miss/hit、reload、JEI |
| U2 | terrain UBO/descriptor/indirect/segment lifetime | terrain + scheduler + allocator | pending overlap、growth、sort/rebuild、両GPUvalidation/image |
| U3 | frame arena backing generation / promotion | FrameArena + HostBuffer + DynamicMesh | same-frame growth、promotion、completion後convergence |
| U4 | content-aware copy + image read/write dependencies | TextureCopyService + texture/target owner | 7段panorama、same-framecopy、collision矩形、copy failure |
| U5 | post target generation + descriptor/query slot ownership | PostChain + frame coordinator | spectator/outline、resize/fullscreen、multi-pass pending |
| U6 | WSI semaphore ticket + wrapper outcome | Swapchain + Context + harness | slow GUI、acquire/out-of-date/failure、GPU requirement |
| U7 | precise counters + canonical docs | report/harness/docs | partial拒否・inclusive timing・query未完了・source hash |
| U8 | work量削減とcontent mod cohort | semantic renderer owners | 同じ画像、到達warm、実world/JEI、性能と互換の同時確認 |

U1とU2は担当ファイルが分かれる。U2/U3/U4/U5を同じ巨大backendへ同時編集させず、resource ticket / generation / interfaceを先に固定する。

安いcompile/CPU契約確認でproducerとconsumerを揃えてから、まとまったGPU受入を行う。全待ちを復活させる、buffer/pool budgetを増やす、失敗verdictを言い換える、描画を落として速くする対応はclosureにしない。

既存の有効な変更を保持し、受入を伴う小さい構造整理を進める。performance_plan / refactoring-planを再利用する。本体と主要content modのshaderless成立後にshader planへ進む。

## 15. witnessと参照

[CPU witness results](../audits/2026-10-12/cpu-witnesses.json) / [reproduction script](../audits/2026-10-12/source-witnesses.py)。

scriptは固定snapshotとbuildが生成した原典を使う。生成したdependency stubはMinecraft全体を代用するものではない。バグが修正された後は「witness changed」で失敗し得るので、恒久CIの合格試験にはしていない。GPU run、validation-clean、画像一致をこの7probeから主張しない。

主要な仕様参照（primary）:

- [Vulkan uniform buffers / frames in flight](https://docs.vulkan.org/tutorial/latest/05_Uniform_buffers/00_Descriptor_set_layout_and_buffer.html): pending readerと次のhost writeの分離。
- [vkUpdateDescriptorSets](https://docs.vulkan.org/refpages/latest/refpages/source/vkUpdateDescriptorSets.html): pending descriptor更新条件、VUID-vkUpdateDescriptorSets-None-03047。
- [Synchronization specification](https://docs.vulkan.org/spec/latest/chapters/synchronization.html): submission order自体はexecution/memory dependencyではない。
- [Memory specification](https://docs.vulkan.org/spec/latest/chapters/memory.html): HOST_COHERENTとhost cache管理の範囲。
- [vkAcquireNextImageKHR](https://docs.vulkan.org/refpages/latest/refpages/source/vkAcquireNextImageKHR.html): unsignaledとpending signal/waitの条件、VUID-01779。
- [Vulkan limits](https://docs.vulkan.org/spec/latest/chapters/limits.html): baseline push constant能力と実際のrenderer必要量を区別。

本監査が閉じたのは**調査と根因の分類**である。列挙した実装欠陥の修正・両GPU・画像・mod受入が完了したという報告ではない。
