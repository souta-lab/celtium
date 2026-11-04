# Celtium — 製品目標と完成条件

2026-10-21 再定義。利用者指示による改訂: 10-24（新アーキテクチャ・互換方針）、10-25（基盤固有最適化・描画modの吸収・
巨大クラス分割・KPI p50/p95/p99・GL stubの範囲）、10-27（巨大packの置換・旧コード削除量）、
11-04（Mod自前のshader program・条件ごとの閉じる証拠）。
以前の性能値・原因断定・完了宣言は前提にしない。**本書も絶対ではない**: 実測がより良い方針を示せば直す。
状態は [plan.md](<plan.md>)（唯一の現在表）、設計案（非拘束）は [new_architecture.md](<new_architecture.md>)、
手順は [development.md](<development.md>)。本書は作業許可やGoalの稼働状態を変更しない。

## 目的と唯一の描画制約

Minecraft 1.12.2 / CleanroomMC 向けに、**Celeritasを明確に上回る性能**と、**Forge標準描画経路の正しさ**を持つレンダラを作る。

- **native Vulkan-only。Minecraft/Forgeの描画・資源・状態・可視性・提出をnative rendererが所有する。**
  全描画物（地形・entity・TESR・item・particle・text・GUI・空・手・post）は1つのscene database・可視性・
  frame compilerに載る（形は実測で決める）。バニラのGL呼出しをスタブへ転送して「native化」と呼ばない。
- **GL stubは「GLを直接呼ぶmod」のためだけ**（10-25）。vanilla/Forgeはmixinで意味を受け取りnativeに描く。
  mod の直接GL（例: Xaero の PBO・raw FBO・raw matrix）はstubが受け、結果をnativeの target・texture・matrix へ届ける。
  stubが何も描かないことを互換と呼ばない。GLコンテキスト・GL fallbackに頼らず、拒否された描画は欠落と数える。
- **Mod自前のshader program（GLSL）もstubの責務**（11-04）。例: GTCEu の bloom 合成。固定集合ではないので
  実行時に GLSL→SPIR-V へ変換し native pass で実行する。それまでは program bind 中の batch を拒否して
  program ごとに数えるのが正しい状態で、世界投影で別の絵を描くことを互換と呼ばない。

### 基盤固有の最適化と描画modの吸収（10-25 利用者指示）

- **SDL3 / Java 21 / LWJGL 3 / Cleanroom / Vulkan 1.2 でしかできない最適化を積極的に採る**（descriptor indexing・
  draw indirect count・timeline semaphore・`VkPipelineCache` 永続化・世代別ZGC・SDL3 Wayland直結など）。採否は実測で決める。
- **描画系modは競合として排除し、その手法を本体の一つのscene/可視性/提出経路に取り込む**: Celeritas/Sodium（地形）、
  EntityCulling、ImmediatelyFast（GUI・文字・HUDのbatch）、Enhanced Block Entities、FoamFix/VintageFix（資源遅延）。
- **巨大クラスを分割する**: `VulkanTerrainGpuBackend` を所有権ごと（状態owner・受理・記録・提出・present・診断）に段階抽出。
- **旧コードを削除する**: 置き換わったクラス・旧経路・使われない計器/実験switchの**削除行数を成果に数える**。
  効果の無い実験は消し、CPUテストとlegで無変化を確かめる。

それ以外の実装方式は固定しない。ただしMinecraft/Forgeの観測可能な描画・イベント契約を壊す変更を互換向上と取り違えない。

## 完成条件（すべて必要）

各条件の「閉じる証拠」以外で達成を宣言しない。

1. **性能**：hash固定のCeleritas対照に、**AMD RX 6400とIntel UHD 630の双方で p50・p95・p99 すべてが明確に下回る**（10-25）。
   3組以上のpaired runで各ratio中央値が1.0未満・全対で同じ向き・負荷ゲート通過。起動・移動・チャンク更新・スパイク
   （16.7/33/50ms超の枚数と最大値）・CPU/GPU時間・メモリ・長時間安定性も確認。描画を削って得た比率は勝利にしない。
   *閉じる証拠*: `scripts/gv-kpi.sh` の形式verdict（KPI profile `celtium-world-accept`・validation/readback OFF・
   warm tick [400,600)）。validation ON の内訳・単発leg・計器込みの ms・旧JARの比では閉じない。
2. **native境界**：sky / terrain / entities / block entities / particles / hand / GUI / font / loading / settings が
   GL互換層を逆呼出しせずnativeに描かれる（状態query・texture・framebufferを含む）。
   *閉じる証拠*: 出荷経路の census（`CELTIUM_GL_SURFACE*`・未route呼出し・program bind/拒否）が Mod 由来だけを示す。
3. **画質**：loading / home / game / settings の4画面とQMAGNET's Test Mapを原寸で確認。
   *閉じる証拠*: 同fixture・同poseの GL 参照（Celeritas）との比較と機能別の陽性対照。色数gate・validation clean単独では閉じない。
4. **互換**（10-24）：Forge API の公開契約と**Forge/vanillaの標準描画経路に載るコンテンツ**が欠落・ちらつき・順序・
   状態漏れなく描かれる（resize・resource reload・world切替・GUI/FBO寿命を含む）。**描画に手を加えるMod**（renderer置換・
   culling/batching/FPS系・独自shaderpack・描画順の差替え）は非サポートで、判明した競合は mod ID・transformer/mixin
   target・署名で起動時にfail-fastする（推測で正常Modを排除しない）。**E2E 1.93a と E2E-E が完全に動作すること**
   （RLCraft 2.9.3 はCeltium無しでも起動しないため除外）。
   *閉じる証拠*: 両packの実instance leg（両GPU）で world を開いた提示画像に異常が無いこと。依存Modを抜いたpack・
   `runClient`・回帰検出用scene（GTCEu/Supersymmetry・SF4）は完全packの受入を代用しない。
5. **影とshaderpack**：**Demonica forkの別成果物（AGPL-3.0のaddon jar）**で実現（10-24）。本体の責務は接続境界
   （terrain描画・pass・資源の受け渡し）を壊さないこと。受入はaddon側で両GPUの実画面。本体の優先は①性能→④互換。
6. **配布**：packaged JARとテスト済み実装が一致し、対応環境・依存・ライセンス・設定・既知の非対応・導入手順が正確。
   初回導入と長時間プレイでdevice lost・資源リーク・無言の欠落がない。*閉じる証拠*: 配布jarと検証jarの SHA-256 一致。

## 証拠の最低条件

- full JAR／fixture の SHA-256、build provenance と dirty差分、実GPU、scene、camera、flags、測定区間、legを紐づける。
  **実行時のgit HEADはJARの出自の証明ではない。**
- 性能と画質/validationのrunを分け、計測負荷を明記する。cold区間は worldTicks [120,200)、post-startup は [400,600)
  （50サンプル以上・区間完了・`holdUntilTick` が必要）。tick番号はsteady stateを証明しない。
- 部分計数から全数を、1 GPU・1 sceneの成功を他へ拡張しない。失敗・未較正・欠測・hash不一致は成功から除く。
- 過去の否定・肯定は再入条件を持つ仮説として扱い、対象artifactと有効な計器から再評価する。
