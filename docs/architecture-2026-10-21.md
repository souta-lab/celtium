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
