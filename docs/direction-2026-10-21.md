# 付録: Phase B の実装設計（2026-10-21・改訂: instancing ではなく既存 multi-draw の流用）

## 調査で判明した決定的な事実（設計を変えた）

1. **`immediate_tex_color_vk11_groupid.vert` が既に存在する**：push constant の `mat4 u_ModelView[]` を
   `u_ModelView[gl_DrawID]` で引く ＝ **draw ごとに行列を選ぶ multi-draw 用のシェーダ**。しかも **Java 側から参照されていない**
   （`grep groupid celtium-vulkan/src/main/java` が空）＝ 用意だけされていた。
2. **terrain は既に `vkCmdDrawIndexedIndirect` を使っている**（`TerrainRenderer` に3箇所）＝ multi-draw の実装は動いている。
3. **モデルの modelView は per-draw**（`celtium$drawModelList` が live matrix を clone して渡す。メッシュはモデル空間頂点）
   ＝ bake 済みではなく、draw ごとに行列を差し替える設計が成立する。

⇒ **instancing を新設する必要はない。** 同一 `(format/texture/blend)` の複数 draw を
**terrain と同じ indirect multi-draw にまとめ、行列を `u_ModelView[]` の配列で渡す**のが、既存資産を使う正しい形。
新しいパイプライン layout もインスタンスバッファも要らず、`groupid` シェーダがそのまま使える。

## 設計（改訂版）

1. **収集**：frame 内で `(listId, texture, modelView[16])` を蓄積（`VulkanTerrainGpuBackend` の外に collector を置く。
   原則3によりバックエンドへの新機能追加は不可なので、収集は新規クラス、呼び出しは `celtium$drawModelList` から1行）。
2. **確定**：既存 flush 点で `(format, texture, blend)` ごとにグループ化し、
   - 1件 → 現行の単一 draw（退行リスクゼロのフォールバック）
   - 2件以上 → **indirect multi-draw 1回**、`VkDrawIndexedIndirectCommand` 配列と `u_ModelView[]` を push constant に
3. **push constant の上限確認**：`u_ModelView[]` は可変長配列なので、device の `maxPushConstantsSize` から
   1グループに入る最大 draw 数を算出し、超える場合は複数グループに分割（値を実装時に測って定数化）。
4. **受入**：
   - 1frame のモデル draw 数が `(format, texture, blend)` の異なり数まで減少（計器②）
   - **KPI ペアで 2.30x から −6% 超**、両arm PASSED、画像ゲート維持

## 実装前に確認する残りの未知

1. `maxPushConstantsSize` と `VkDrawIndexedIndirectCommand` を `u_ModelView[]` と同居させる余裕（実装時に測る）
2. 半透明・`modelOffTargetCalls`（アウトライン用オフターゲット）を同じグループ化に載せてよいか
3. `groupid` シェーダの frag 側が ITEM レイアウトの colour/normal と一致するか（無ければ frag を1本追加）

pre-transform（行列キー分割の削減）は KPI 2ペアで 2.28x（基準 2.30x、ノイズ内）と測って棄却済み。
残るレバーは **同一 `(listId, texture)` を1 draw にまとめる instancing** だけなので、実装前に設計を固定する。

## 実測の前提（再測定不要）

| 事実 | 値 / 出典 |
|---|---|
| モデル再生 | `glCallList` 805〜823 回/leg、捕捉メッシュは `ModelGeometryCache` が list 単位で構築 |
| 統合済みの merges | **10.6/frame** |
| 行列キーによる分割 | **5.1/frame**（affine bake では動かず） |
| モデル頂点レイアウト | `VulkanDynamicMesh.Layout(28, 0, 16, 12, 24, -1)`（position, colour, tex, normal, pad） |
| モデル描画の入口 | `VulkanTerrainGpuBackend.celtium$drawModelList(listId, boundTextureId)` |
| 再生の呼び出し元 | `GlStateManagerMixin.celtium$countCallList`（MCP名 `callList`、GL本文は cancel） |

## 設計

1. **収集**：`CeltiumModelBatch`（新規, `forge122` ではなく `celtium-vulkan`）が frame 内で
   `(listId, texture, modelView[16])` を蓄積する。`drawModelList` は即時描画せずここへ積む。
2. **確定**：パス終端（既存の flush 点）で `(listId, texture)` ごとにグループ化。
   - 1件なら現行経路（従来の単一 draw）にそのまま流す ＝ 退行リスクゼロのフォールバック。
   - 2件以上なら instanced draw 1回。
3. **instance buffer**：`VulkanFrameArena` から frame 単位で確保（既存 arena を使い、新規アロケータを作らない）。
4. **パイプライン**：ITEM レイアウトに **instance 入力 binding を1本追加**した variant を
   `VulkanImmediateItemPipeline` に追加。pipeline cache のキーは既存 state key に「instanced」ビットを足すだけ。
5. **shader**：モデル用 vertex shader の variant を1本追加し、`gl_InstanceIndex` で mat4 を引き position に適用、
   normal は既存どおり（法線も回転が必要なら mat3 を instance に含める）。
6. **受入**：
   - 1frame のモデル draw 数が `(listId, texture)` の異なり数まで落ちる（計器②で観測）
   - **KPI ペアで 2.30x から −6% 超**の改善、両arm PASSED
   - 画像ゲート既存と一致（PASSED の維持）

## 実装前に確認する未知（ここを飛ばすと設計が崩れる）

1. **ITEM レイアウトを消費する vertex shader はどれか**（`celtium-vulkan/src/main/shaders/` に item 名のファイルが無いため、
   `VulkanImmediateItemPipeline` が読むシェーダ名を先に特定する）
2. **modelView が push constant か、既に頂点へ bake 済みか**（bake 済みなら instancing の代わりに「行列ごとに頂点を複製」になり無意味）
3. **normal を回転させる必要があるか**（ITEM レイアウトは normal を持つため、回転時に法線変換が要る）
4. 半透明・`RenderLivingBase` のアウトライン用オフターゲット再生（`modelOffTargetCalls`）が同じグループ化に載ってよいか
