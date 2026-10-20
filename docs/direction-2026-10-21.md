# 方向性の見直し（2026-10-21）

利用者指示「コードベースを見直し、これからの方向性を抜本的に見直せ」に対する分析。判断材料は**このセッションの実測**と**外部の先行実装**。

## 1. コードベースの実態（実測）

| モジュール | ファイル | 行数 |
|---|---|---|
| `celtium-vulkan` | 100 | **71,270** |
| `forge122`（mod/mixins） | 134 | 16,093 |
| `common`（Celeritas/Embeddium 由来） | 333 | 43,929 |

集中度が異常：

| ファイル | 行数 | 内容 |
|---|---|---|
| `VulkanTerrainGpuBackend.java` | **18,209**（fields 980, methods 600） | frame/pass/immediate/state/shadow/present が同居する god class |
| `gl/CeltiumGlStub.java` | 6,894 | GL エミュレータ |
| `VulkanGlTexture.java` | 3,917 | テクスチャ mirror |

計器・診断の量：

- **`CELTIUM_` 定数 309 種**
- `System.out.println` **385 箇所**
- `Boolean.getBoolean(...)` スイッチ **93 個**

## 2. 実測が示すボトルネック

| 項目 | 実測値 | 意味 |
|---|---|---|
| KPI（検証税OFF） | **3.20ms vs Celeritas 1.40ms ＝ 2.29x** | CPU 側で負けている |
| GPU（terrain 3 pass） | **1.4ms** / frame 8.5ms | **GPU は遊んでいる** |
| 即時描画の変換コスト | **3.5µs × 約290/frame ≒ 1ms** | hot spot 無しで分散 |
| GC | Celeritas と**同量** | 原因ではない |
| present 6.9ms | **readback+dump＝検証税** | 描画と無関係 |
| `prepareImmediate` キャッシュ | **ヒット率 7.7%** | 呼び出しごとに状態が違う |
| スタブ残存 | `glCallList` 610、`glDrawArrays` 1024 | バニラがまだ通っている |

**結論：GPU は余っており、負けはすべて「GL 呼び出しを1個ずつ変換している」ことに由来する。** マイクロ最適化は計測上もう余地が無い（hot spot 無し、キャッシュ不成立、GC 同一）。

## 3. 外部の先行実装（設計のみ参考、コードは流用しない）

| 実装 | 対象 | 学ぶ点 |
|---|---|---|
| **[VulkanMod](https://deepwiki.com/xCollateral/VulkanMod/5-shader-system)** | 現代 MC | GUI を自前 `GuiBatchRenderer` で描き、`submitBlockEntities` を cancel、`MainTarget` を自前パスへ。**API 抽象を置き換える**方式。1.12.2 に Blaze3D は無いので設計のみ |
| **[Sulfide](https://github.com/ohlunasa/Sulfide)（1.8.9, LGPLv3）** | **1.8.9** | **Instanced Entity Rendering — per-model batching for all entities**、高速 sky/cloud、GPU lightmap compute。**1.12.2 に最も近い先行例** |
| **[Rentities](https://modrinth.com/mod/rentities)（1.21.11）** | 現代 MC | 同種 entity を 1 draw にまとめ、位置・回転・アニメを SSBO、アニメを頂点シェーダで処理。**3000体で +155% FPS** |
| **[Angelica](https://github.com/GTNewHorizons/Angelica)（1.7.10）** | 1.7.10 | **GL3.3 core + Java による固定機能パイプラインのエミュレーション**、**Batched Font Renderer**、地形は Celeritas。Celtium の前例に最も近い |
| **[Mojira MC-311557](https://bugs.mojang.com/browse/MC-311557)** | 公式 Vulkan | 「1フレームに 45+ の dynamic render pass と blit」と批判されている。**パス分割しすぎるな**という警告 |
| [Tom's Hardware 報道](https://www.tomshardware.com/video-games/after-17-years-minecraft-java-edition-starts-replacing-opengl-with-a-vulkan-multi-threaded-renderer-update-will-prove-a-boon-for-performance-and-modding) | 公式 Vulkan | 描画を**別スレッド**で動かす。Cleanroom(LWJGL3/Java25) なら追従可能 |

## 4. 見直しの結論 — 3つの転換

### 転換1: エミュレータを「育てる」のをやめ、境界を法定化する
- `VulkanTerrainGpuBackend` は**新機能の追加を凍結**し、責務を抽出して契約で分ける
- 計器は診断ファサード1つに集約（309 → 受入に必要な十数個）。**計器の増殖が開発速度を食っている**
- スタブは「生GLを直接叩く Mod」専用。バニラ経路の残存（`glCallList` 610 / `glDrawArrays` 1024）は**欠陥**として0へ

### 転換2: 地形以外は「呼び出し単位」ではなく「モデル単位」でまとめる
- entity/TESR: モデル×テクスチャ単位で frame 内に蓄積し、可能なら**instanced**（Sulfide/Rentities の設計）
- フォント: Angelica の batched font と同じ発想（Celtium は既に fast glyph 経路あり）
- GUI: VulkanMod の `GuiBatchRenderer` 型（テクスチャごとに1 batch → 1 draw）
- ⇒ 290 batch → 数十 draw へ

### 転換3: 「呼び出しを取る」のをやめ「フレームを取る」
- sky / terrain / entities / block-entities / particles / GUI / post を明示パスにし、1つの render graph で編成
- ただし **MC-311557 の轍を踏まない**（パス分割しすぎない）。目標は**1フレーム十数パス以内**
- 余力で描画スレッド分離（公式 Vulkan と同じ方向、Cleanroom スタックの利点）

## 5. 直近3ラウンドの実装順

1. **計測**：`WorldVertexBufferUploader` 経由の offer を `(format, texture)` 別に集計し、**モデル単位まとめで何 draw まで落ちるか**を数値で確定（既存 `CELTIUM_ARRIVALS` を拡張）
2. **抽出**：immediate batch 経路を god class から `ImmediateBatcher` へ移し、`(format, texture)` アキュムレータで run あたり1 draw。**挙動不変**を leg で証明
3. **instanced 化**：entity/TESR をモデル単位 instancing へ（Sulfide/Rentities の設計を 1.12.2 の `ModelRenderer` 構造に合わせる）

## 6. やめること
- スタブ内部の修理（display list/hint）を主作業にすること
- 方向性が固まる前に計器を増やすこと
- 「呼び出しごとの変換」を速くする努力（計測で余地が無いと確定済み）

---

# 付録: Phase B「instancing」の実装設計（2026-10-21）

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
