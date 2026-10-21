# Celtium アーキテクチャ転換計画（2026-10-21）

> この文書は**計画**であり、実装済みの主張ではない。現在の実装・受入は [plan.md](<plan.md>) が唯一。
> 製品の完成条件は [goal.md](<goal.md>)。判断の根拠は本文中に実測・コード位置として示す。

## 0. なぜ転換するのか（この1週間で否定された前提）

| 前提 | 実際 | 根拠 |
|---|---|---|
| 「per-call 変換が 3.5µs × 290/frame ≒ 1ms の主因」 | 即時パスの内訳は prepare 0.42 + branch 0.38 + append 0.17 ≒ **0.97ms/frame**。主因ではあるが、`flush` 区間全体でも 1.1ms | `physplit1` 系 leg の `CELTIUM_*Millis` |
| 「draw は 6〜9/frame なので 1:1 変換は無い」 | **部分計数**。`DrawCounters` は `VulkanDynamicMesh` の2点のみで、地形の逐次 draw・terrain/shadow の indirect を数えていない | `VulkanDynamicMesh.java:288,526`、`TerrainRenderer.java:549`、`ShadowMapPass.java:599,798` |
| 「KPI 2.29x は変更後の比較」 | `gv-kpi.sh` の既定 JAR は **2026-10-20 21:34** の固定物。4 leg がすべて同一 sha `ca77e4a5…` を測定 | 各 leg の `-manifest.json` |
| 「2.29x が FPS の判定」 | `frameIntervalP50Ms` は **cold window（worldTicks 120..200＝チャンク構築中）**。steady state は `frameIntervalWarmP50Ms` だが `warmSampleCount=0` | `ValidationClient.java:5245`、`physplit1` の report |

⇒ **この間の「効果なし/退行なし」の結論は測定として無効**。まず測定を直さない限り、どの最適化も判定できない。

## 1. 転換の骨格

現行: **GL 呼び出し → スタブ/ヒント → Java で1件ずつ Vulkan draw へ変換**（`CeltiumNativeDraw.drawArrays` は今も
`CeltiumGlStub.celtium$drawArrays` を直接呼ぶ＝スタブ経由が残っている）。

転換後: **Java の描画意図をデータとして集め、順序を保ったまま、少数の native 実行単位にする。**

```
Minecraft / Forge の描画API（Tessellator, ModelRenderer, FontRenderer, GlStateManager, RenderItem）
        │  ← Java API レベルで捕捉（GL スタブを通さない）
        ▼
  DrawIntent 収集        1件 = 1 draw ではない。{頂点範囲, 状態, 行き先, 順序番号} の記録
        │
        ▼
  順序保存バッチ化       「隣接し、状態が同一」な intent だけを run にまとめる（順序を跨がない）
        │
        ▼
  native 実行            run ごとに 1 つの draw / indirect multi-draw。状態は SSBO・push constant で供給
```

**multi-draw はこの構造の実行方式**であって目的ではない。run が1件なら通常の `vkCmdDraw*`、2件以上なら indirect。
どちらも「intent を貯めてから出す」という同じ構造の上に乗る。

### 1.1 なぜ GL スタブを主経路から外すのか
GL スタブは「GL の意味論を Java で再現する層」であり、`glDrawArrays` 1回ごとに状態の読み直し・descriptor 解決・
グループ管理を払う。バニラの意図は Java API の時点で分かっているので、GL の意味論を経由する必要がない。
**スタブは「Mod が直接 GL11 を叩く」場合の互換層に限定する**（[refactoring-plan §3-7](<refactoring-plan.md>)）。

## 2. 実装段階（順序固定）

各段階は「受入が測れる」ことを出口にする。**測れない段階は開始しない。**

### S0 — 測定の修復（最優先・コードは計器と検証のみ）
1. **JAR 固定**: KPI は `CELTIUM_KPI_JARS` に、その HEAD から build した JAR を明示 stage したディレクトリを渡す。
   `-manifest.json` の `artifacts.renderer.sha256` と `git.head`/`git.dirty` が**そのビルドと一致**することを確認する。
   一致しなければ測定を破棄する（今回の罠）。
2. **warm window で判定**: `frameIntervalWarmSampleCount > 0` を必須にし、`frameIntervalWarmP50Ms` を KPI にする。
   `warmToTick`（既定 600）に届く leg 長を確保する。
3. **draw 計数を全発行点へ**: `VulkanDynamicMesh`（2点）に加え、`TerrainRenderer.celtium$drawIndexedRun`、
   `tryIndirectBatch`、`ShadowMapPass` の indirect を数える。合計を `CELTIUM_DRAWS` に出す。
4. **陽性対照**: 各計器は「必ず出る条件」を1つ持つ（今回 `CELTIUM_MODEL_GROUPS` が出ない原因究明に時間を溶かした）。

**受入**: 同じ JAR を2回測って warm p50 の比が ±3% 以内。draw 数が全経路の合計として出る。

### S1 — Java frontend を主経路にする（バニラ即時描画）
1. `Tessellator`/`BufferBuilder`/`GlStateManager`/フォント/モデルを **Java API で捕捉**（既存 mixin を拡張）。
2. 捕捉結果を `DrawIntent`（頂点範囲・状態スナップショット・行き先・順序）として記録し、
   `VulkanTerrainGpuBackend.submitImmediateBatch` を**直接呼ばない**。収集器に渡す。
3. スタブ経由の到着数を計器で 0 に近づける（`CeltiumNativeDraw` がスタブを呼ぶ実装を、収集器呼び出しへ置換）。

**受入**: バニラ描画の到着が `CeltiumGlStub` を経由しない（スタック計器で確認）。画像ゲート PASSED。

### S2 — 順序保存バッチ実行
1. intent を「隣接し、状態が同一」な run にまとめる。**順序を跨ぐ並べ替えはしない**（blend・depth の前後関係を壊さない）。
2. run が2件以上なら indirect multi-draw。行列・状態は SSBO（`set=0, binding=6` の `u_ModelView[]`、
   `immediate_tex_color_vk11_groupid.vert` が既にこの形を宣言）と push constant で供給する。
3. 1件の run は通常 draw（退行リスクを負わない）。

**受入**: draw 数（全経路合計）が S1 比で有意に減る。warm p50 が Celeritas 比で改善。画像ゲート PASSED。

### S3 — フレーム予算とパス編成
1. warm window の frame を「ゲームロジック／チャンク構築／アップロード／即時／記録＋submit／present」に分解する
   （既存 `FramePassTimings` の区間を warm window で出す）。
2. 最大区間だけを攻める。パス数は十数個以内を維持。

**受入**: warm p50 が **ratio ≤ 1.0**（AMD・Intel）。

### S4 — 互換と目視
E2E 1.93a、RLCraft 2.9.3、qmagnets、目視4画面（loading / home / game / settings）、Entity Culling 共存。

### S5 — 影・Shaderpack・配布
Native Shadow Map の両 GPU 実画面、BSL 実機、配布物と実装の一致。

## 3. やらないこと（今回の失敗から）

- **測定を直さずに最適化を実装しない。** 同一 JAR を測っていた間の結論は無効。
- **部分計数を全体の結論に使わない。** 「draw が 6〜9」は2点だけの計数だった。
- **cold window の数字で FPS を語らない。** 120..200 tick はチャンク構築中。
- **スタブに新しい入口を足さない。** スタブは Mod 互換専用。
- **`VulkanTerrainGpuBackend` に機能を足さない**（抽出と削除のみ）。18,209 行の god class をこれ以上育てない。
- 参考実装は設計のみ（VulkanMod = LGPL-3.0、ImmediatelyFast = LGPL-3.0、Sulfide = LGPL-3.0）。**コード流用はしない。**
  一次資料: [Vulkan 仕様の描画章](https://raw.githubusercontent.com/KhronosGroup/Vulkan-Docs/main/chapters/drawing.adoc)、
  [Khronos multi-draw indirect サンプル](https://github.com/KhronosGroup/Vulkan-Samples/tree/main/samples/performance/multi_draw_indirect)
  （`VkDrawIndexedIndirectCommand` は firstIndex/vertexOffset を持つため、**1頂点バッファ＋1 index バッファに詰めて
  バインドを1回にする**のが本質。draw 数の削減自体が目的ではない）。

## 4. 直近の1手

**S0 のみ**を実施する（コードは計器と検証スクリプトだけ、描画経路は触らない）。
これが通るまで S1 以降に進まない。理由: 現状の KPI は測定として成立していないため、
S1 以降の効果を判定する手段が存在しない。
