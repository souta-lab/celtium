# GLStub 戦略（2026-10-29 利用者方針）

**位置づけ**: vanilla の性能・不具合なしを達成した後、Celtium の成否を左右する中核技術は GLStub。
目的は「OpenGL を Vulkan でエミュレートする」ことではなく、**OpenGL の命令を解析し、最適化された Vulkan 描画へ
コンパイルする**こと。互換性を広げるほど共通の高速描画経路（batch/cache/送信最適化）を使い回せる設計を目指す。

この文書は**方針と受入**の正典。実装状況・優先順は [plan.md](<plan.md>)、手順は [development.md](<development.md>)。
GL-less は唯一のモードであり、切替プロパティ（旧 `celtium.vulkan.glless`）と実 OpenGL の bridge 経路は
2026-10-30 に削除した。以下の interception・GL 意味論の話はすべてこの単一モードの内側の話である。

## 現行コードの事実（2026-10-29 時点で確認）

| 項目 | 現状 |
|---|---|
| `glBegin` | `SEM_BEGIN` → `celtium$noteImmediateBegin(mode)`。**呼び出し元の記録のみで頂点は取らない** |
| `glEnd` | `SEM_END` → `BEGIN_END_CALLS` を加算するだけ |
| `glVertex3f` / `glTexCoord2f` / `glMultiTexCoord2f` | 汎用 no-op 表に登録。**頂点は捨てられる** |
| Tessellator / 即時描画（vanilla 経路） | `submitImmediateBatch(null, stride, formatId, address, count, mode)` で実描画。
  format 1 = POSITION_COLOR（stride 16）、format 2 = POSITION_TEX_COLOR（stride 24） |
| FBO / chain pair | `GllessFrameTarget` / `PostChainBridge` / `VulkanImmediateTexturePipeline` で既定描画は通る。
  多段 FBO（GregTech Bloom）・Raw FBO の整合は RC7/RC8 で前進、Windows 実測は未完 |
| Display List | `glNewList`/`glCallList` は描画内容の再実行/コンパイル経路が限定的 |

⇒ ユーザー指摘の「`SEM_BEGIN` は記録のみ、`glVertex3f` は no-op」は**現行コードで事実**（2026-10-29 確認）。

## 参考実装と採用順位

| 優先 | 実装 | 取り入れるもの | ライセンス |
|---|---|---|---|
| 1 | [gl4es](https://github.com/ptitSeb/gl4es) | Immediate Mode / Display List の頂点記録・状態スナップショット・プリミティブ変換・描画結合 | MIT |
| 2 | [ANGLE Vulkan backend](https://github.com/google/angle/tree/main/src/libANGLE/renderer/vulkan) | Render pass 状態機械、Image/Barrier 追跡、Descriptor 管理、FBO キャッシュ | BSD系（移植時は要確認） |
| 3 | [VulkanMod](https://github.com/xCollateral/VulkanMod) | Minecraft API 置換の軽量パターン（**GL 全状態の再現ではない**点に注意） | LGPL-3.0-only |
| 4 | [Mesa Zink](https://docs.mesa3d.org/drivers/zink.html) | Desktop GL の意味論・互換性テストの参照（丸ごと組込みはしない） | MIT |
| 5 | [GLOVE](https://github.com/Sagoia/GLOVE) | Context / State / Resources の責務分離 | LGPL-3.0 |

**採らない方針**: VulkanMod の GL 互換実装をそのまま輸入する（対象範囲が違う: 同実装は `glEnable` 空実装・
`glGetInteger` 0 など、Minecraft 本体が必要とする範囲に限定）。Zink/ANGLE を別 Vulkan コンテキストとして
丸ごと動かす（Celtium の Image/同期と共有できない）。

## 目標アーキテクチャ

```
Minecraft / Forge mods
  GL11 / GL15 / GL20 / GL30 / EXT / ARB
        │  (CeltiumGlStub + LWJGL transformer。既存の interception を維持)
        ▼
Celtium GL Frontend
  GL State Shadow / Legacy geometry (gl4es 由来) / Display List compiler
  Fixed-function shader / GLSL program state / FBO resource tracking
        │  (C 高速部は batch 境界のみ JNI。1 頂点 1 JNI は禁止)
        ▼
Celtium Render IR
  Draw / Clear / Copy / Barrier / Resource handle
        ▼
既存 VulkanTerrainGpuBackend（唯一の VkDevice・リソース所有）
  batching / pipeline cache / async submit / retire queue
```

**重要**: GLStub を別レンダラーにしない。GL の意味論を解釈し、描画・リソース寿命・GPU 同期は
native 経路と共有する（同一 batch・同一 cache・同一 submit 最適化）。

## 5つの開発原則

1. **GL 命令を即時実行しない**: 状態系（`glBindTexture`/`glEnable`/`glBlendFunc` 等）は Java 側の状態更新だけ。
   描画・Clear・Readback など観測可能な順序だけ維持する。
2. **Legacy GL をバッチ化する**: `glBegin/glEnd`・Display List・固定機能入力を構造化 Draw IR に変換し、
   同一状態をまとめ、繰り返し Display List は生成済み GPU リソースを再利用する。
3. **FBO を最重要の互換対象にする**: 多段 Bloom の実例どおり Render Target 切替が主戦場。GL の FBO 操作を
   Vulkan Image/Attachment/Layout/Barrier に対応させる（ANGLE の設計を参照）。
4. **CPU/GPU の暗黙同期を排除する**: GLStub が毎回 Readback/Fence Wait を挟んだら Vulkan 化の意味が消える。
   GPU で解ける依存は GPU に残し、CPU 読み戻しは要求時だけ。
5. **GLStub 経由コストを削る**: 頻出命令の専用高速経路、状態変更の重複排除、事前計算 state key、Pipeline Cache。

## 受入（測ってから最適化する）

| 検証 | 合格条件 |
|---|---|
| Cyberware Radial Menu | `GL_TRIANGLE_FAN` が正しく描画される |
| Forge Splash | `glBegin/glEnd` 描画が欠落しない（ロード画面） |
| GregTech Unreal Bloom | 多段 FBO の合成結果が正しい（画素比較） |
| Display List | 再実行時の再変換が削減される（カウンタで示す） |
| GLStub CPU 時間 | 旧実装比で短縮（`CELTIUM_FRAME_BUDGET` の record/batch と専用計器） |
| GPU リソース寿命 | Validation Error ゼロ |
| 大規模 Modpack | **SuperSymmetry / ATM3 で同一条件の Celeritas を FPS で上回る**（KPI） |

**数字を測らずに最適化しない**。「Linux では速いが Windows の Modpack で 10 FPS」を再発させないため、
フレーム時間は必ず分解して追う（`-Dceltium.vulkan.frameBudget=true` の record/batch/submit/fence/gpu/other）。

## 最初の PR 範囲: `feat/glstub-native-legacy-geometry`

対象を **legacy immediate geometry の捕捉と実描画** に限定する（FBO/GLSL は触らない）。

1. `glBegin(mode)` / `glVertex2f|3f|3d|4f` / `glColor3f|4f|4b` / `glTexCoord2f` / `glNormal3f` を
   **意味論ハンドラ**として登録し、頂点・色・UV・法線を記録する（no-op から外す）。
2. プリミティブ変換: `GL_QUADS`→2 三角形、`GL_TRIANGLES`、`GL_TRIANGLE_STRIP/FAN`、`GL_LINES`、`GL_LINE_STRIP`。
3. 頂点は現在の「POSITION_TEX_COLOR (stride 24)」または「POSITION_COLOR (stride 16)」に詰め、
   既存 `submitImmediateBatch` へ渡す（行列・fog・color/alpha の push は既存経路のまま）。
4. **JNI は batch 境界のみ**。gl4es の C 実装（頂点記録・状態スナップショット）は、まず Java で意味論を
   固定してから、測って遅い部分だけ C へ移す。C を入れる場合も VkDevice/VkImage の所有権は Java 側に残す。
5. 受入: Forge Splash（`glBegin(GL_QUADS)` 3 箇所）と Cyberware radial menu を実機で描画し、
   validation error 0・フレーム時間の悪化なしを確認してから次段（FBO/表示リスト）へ進む。

## 未決・リスク

- **頂点形式の互換**: 固定機能の頂点属性（法線・複数テクスチャ座標・color material）をどこまで format に載せるか。
  未対応属性は「描かない」ではなく、**理由を数える拒否**として記録する（無出力を正しさと誤認しない）。
- **Display List の意味論**: 記録時状態の再現・ネスト・再定義・`glCallList` 内の状態変更を OpenGL 仕様どおりに。
- **C 移植の採算**: JNI/コピーで遅くなるなら Java に残す。C 化自体を成果にしない。
- **ライセンス**: gl4es(MIT) の取り込みは著作権表示を保持。VulkanMod(LGPL-3.0-only)/GLOVE(LGPL-3.0) から
  コードを直接持ち込む場合は条件を確認し、設計のみの参照にとどめる判断もあり得る。
- **KPI との関係**: GLStub 改善は SuperSymmetry / ATM3 の FPS・エラー 0・完全動作の達成手段であり、
  単独の最適化を目的にしない。
