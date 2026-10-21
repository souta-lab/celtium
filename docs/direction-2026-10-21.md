# 方向性メモ（2026-10-21）— 転換計画に統合済み

> **この文書は履歴。** 現在の計画は [architecture-2026-10-21.md](<architecture-2026-10-21.md)、
> 現在の実装・受入は [plan.md](<plan.md>)。
> 以下は「当時こう判断し、どこが誤りだったか」の記録であり、実行指示ではない。

## 当時の判断と、その後の訂正

| 当時の記述 | 現在の判定 |
|---|---|
| multi-draw バッチングが本命。`immediate_tex_color_vk11_groupid.vert` と terrain の indirect を流用する | **実行方式としては妥当。ただし目的ではない。** 転換の本質は「Java frontend で意図を集め、順序保存でまとめ、native で実行」。run が1件なら通常 draw |
| モデルは全グループ単独（`multiDrawGroups=0`）なので multi-draw は無効 | **測定が同一 JAR・部分計数だったため撤回。** `DrawCounters` は `VulkanDynamicMesh` の2点のみ。地形の逐次 draw と terrain/shadow の indirect を数えていない |
| 1フレーム 6〜9 draw | **撤回**（上と同じ理由）。全面の draw 数は全発行点を数えてから語る |
| pre-transform の一般アフィン bake は KPI 2.28x で効果なし | **撤回。** その leg は `gv-kpi.sh` 既定の固定 JAR（`ca77e4a5…`, 2026-10-20 21:34）を測っており、変更後 JAR ではなかった |
| 3.20ms の内訳は world 4.8ms のうち即時 0.97ms | **cold window（worldTicks 120..200）の数字。** steady state は `frameIntervalWarmP50Ms` だが当時の leg では未取得（`warmSampleCount=0`） |

## 生きている知見（実装に使ってよい）

- `immediate_tex_color_vk11_groupid.vert` は `gl_DrawID` で SSBO（`set=0, binding=6`, `mat4 u_ModelView[]`）を引く。
  Vulkan 1.1 の `shaderDrawParameters` が必要で、`VulkanContext` は既に同 feature を照会・有効化している
  （`VulkanContext.java:2398-2427`）。Java 側からは未参照。
- `TerrainRenderer.tryIndirectBatch` は `vkCmdDrawIndexedIndirect` を実運用しており、
  `VkDrawIndexedIndirectCommand` を**マップ済み HOST_COHERENT メモリへ直接書く**（staging + `vkCmdCopyBuffer` を廃止した経緯がコメントに残る）。
- `VulkanFrameArena` は 2 slot・完了 epoch 判定で再利用する。新しいアロケータを足さずに intent 用の頂点を置ける。
- `WorldVertexBufferUploaderMixin` は `func_181679_a` の HEAD で捕捉し、受理時に GL 本体を cancel する
  （バニラ即時描画の主経路は既に Java API レベル）。
- `CeltiumNativeDraw.drawArrays` は**まだ `CeltiumGlStub.celtium$drawArrays` を直接呼ぶ**＝スタブ経由が残っている。
  S1 でここを収集器呼び出しへ置き換える。

## 参考実装（設計のみ・コード流用しない）

- VulkanMod（LGPL-3.0）: `GlStateManager` を `@Overwrite` で置換し、描画を自前 `Renderer` へ流す。
- ImmediatelyFast（LGPL-3.0）: 即時モードのバッチ統合と冗長な状態切替の排除。
- Sulfide（LGPL-3.0）: instanced entity rendering。
- 一次資料: [Vulkan 仕様 描画章](https://raw.githubusercontent.com/KhronosGroup/Vulkan-Docs/main/chapters/drawing.adoc)、
  [Khronos multi-draw indirect サンプル](https://github.com/KhronosGroup/Vulkan-Samples/tree/main/samples/performance/multi_draw_indirect)。
