# Celtium — 製品目標と完成条件

2026-10-21 再定義、2026-10-24 改訂（利用者指示: 新アーキテクチャへの全面移行と互換方針の変更）、
2026-10-25 改訂（利用者指示: 基盤固有の最適化の積極採用、描画modの最適化手法の一貫した吸収、巨大クラスの分割）。
以前の性能値・原因断定・完了宣言は前提にしない。
現在の実装と受入は [plan.md](<plan.md>)、目標設計は [new_architecture.md](<../new_architecture.md>)
（採否と順序の評価は plan.md）、旧設計と実測の記録は [architecture-2026-10-21.md](<architecture-2026-10-21.md>)、
実行手順は [development.md](<development.md>)。本書は作業許可やGoalの稼働状態を変更しない。

## 目的と唯一の描画制約

Minecraft 1.12.2 / CleanroomMC 向けに、**Celeritasを明確に上回る性能**と、**Forge標準描画経路の正しさ**を持つレンダラを作る。

- **native Vulkan-only。Minecraft/Forgeの描画・資源・状態・可視性・提出をnative rendererが所有する。**
  全描画物（地形・entity・TESR・item・particle・text・GUI・空・手・post）は1つのscene database・可視性・
  frame compilerに載る（[new_architecture.md](<../new_architecture.md>)）。バニラのGL呼出しをスタブへ転送して
  「native化」と呼ばない。HUD/GUI/loadingも例外ではない。
- **Forge API以外のraw GLはstub**（2026-10-24 利用者指示）。どのstub呼出しが実画面に必要だったかを診断で示し、
  stubが何も描かないことを互換と呼ばない。
- GLコンテキスト・GLによる描画fallbackに頼らない。拒否された描画は欠落であり、成功ではない。

### 基盤固有の最適化と描画modの吸収（2026-10-25 利用者指示）

- **SDL3 / Java 21 / LWJGL 3 / Cleanroom / Vulkan 1.2 でしかできない最適化を積極的に採る。**
  GL互換や旧環境のための遠回りは残さない。例: Vulkan 1.2 の descriptor indexing・draw indirect count・
  timeline semaphore・buffer device address、`VkPipelineCache` の永続化、LWJGL 3 の off-heap 構造体と `MemoryUtil`、
  Java 21 のrecord/sealed/パターンマッチと世代別ZGC（起動引数の推奨として）、SDL3 の高精度タイマ・Wayland直結。
  採否は実測（paired KPI・p95/p99・スパイク）で決め、効果の無いものは入れない。
- **描画系modはすべて競合として排除し、その最適化手法はすべて本体に取り込む。一貫して。**
  Celeritas/Sodium（地形）、EntityCulling（遮蔽によるentity/TESRの間引き）、ImmediatelyFast（GUI・文字・HUDのbatch）、
  Enhanced Block Entities（静的TESRのchunk mesh化）、memory系（FoamFix/VintageFix の資源・テクスチャ遅延）などの
  発想を、別modとの相互運用ではなく本体の一つのscene/可視性/提出経路として実装する。
- **巨大クラスを分割する。** `VulkanTerrainGpuBackend`（約2万行）を所有権ごとの部品（状態owner・受理・記録・提出・
  present・診断）へ分け、振る舞いを変えない段階的な抽出で進める。

それ以外の実装方式は固定しない。既存CPUエンジン、公開内部API、shader、資源配置、描画順の表現、
パッケージ構成、キャッシュ、スレッド分担、設定・互換方式は、性能と正しさに必要なら置き換える。
過去文書の「計器追加禁止」「per-call最適化禁止」「backend変更禁止」「S0以外禁止」「必ずmulti-draw」は撤回する。
ただしMinecraft/Forgeの観測可能な描画・イベント契約を壊す変更は、互換性向上と取り違えない。

## 完成条件（すべて必要）

1. **性能**：同一条件・hash固定のCeleritas対照に、AMD RX 6400とIntel UHD 630の双方で優位。
   完全なpost-startup区間のp50だけでなくp95/p99、起動・移動・チャンク更新、CPU/GPU時間、メモリと長時間安定性を確認する。
   最低限のKPIは3組以上のpaired ratio中央値 ≤ 1.0。明確な優位の宣言には再現性・分布・tailも必要。
   描画機能や距離を削って得た比率を同条件の勝利にしない。
2. **native境界**：sky / terrain / entities / block entities / particles / hand / GUI / font / loading / settingsを含む
   Minecraft/Forge経路が、GL互換層を逆呼出しせずnativeに描画される。状態query・texture・framebufferも含めて監査する。
3. **画質**：loading / home / game / settingsの4画面とQMAGNET's Test Mapを、原寸画像・参照・機能別の対照で確認する。
   粗い色数gateとvalidation cleanだけで画質一致とはしない。
4. **互換（2026-10-24 改訂・利用者指示）**：Forge APIの公開契約と、**Forge/vanillaの標準描画経路に載るコンテンツ**が
   欠落・ちらつき・順序・状態漏れなく描かれる。resize、resource reload、world切替、GUI/FBOの寿命も検証する。
   **描画に手を加えるMod（renderer置換・culling/batching/FPS系・独自shader・描画順/GUI描画の差替え等）は競合として非サポート**で、
   判明している競合はmod ID・transformer/mixin target・renderer置換の署名で起動時にfail-fastする（曖昧な推測で正常Modを排除しない）。
   E2E 1.93a / RLCraft 2.9.3 は「競合Modを除いた、標準描画経路のコンテンツ」で受入を再定義し、完全packの動作保証とは呼ばない。
   依存Modを削除した別packの起動を、完全packの受入にしない。
5. **影とshaderpack**：**Celtium本体ではなく、Demonica forkの別成果物（AGPL-3.0のaddon jar）**で実現する（2026-10-24 利用者指示）。
   本体の責務は、そのaddonが接続できる境界（terrain描画・pass・資源の受け渡し）を壊さないこと。受入はaddon側で、
   両GPUの実画面で影とBSLの描画反映・主要効果・破綻を確認する。本体の優先は①性能→④互換で、影はその後。
6. **配布**：packaged JARとテスト済み実装が一致し、対応環境・依存・ライセンス・設定・既知の非対応・導入手順が正確。
   初回導入と長時間プレイでdriver/device lost・資源リーク・受入外の無言の欠落がない。

## 証拠の最低条件

- full JAR SHA-256、fixture SHA-256、ビルド成功とsource revision/dirty差分、実GPU、scene、camera、flags、測定区間、legを紐づける。
  **実行時のgit HEADはJARの出自の証明ではない。** build provenanceとstageのbytesを照合する。
- 性能と画質/validationのrunを分ける。計測負荷を明記し、fixtureを含むframe intervalをrenderer単体時間と呼ばない。
- 既定のcold区間はworldTicks [120,200)、post-startup区間は [400,600)。後者は50以上のサンプルと区間完了が必要。
  fixtureは通常約200tickで終了するため、**起動時間の延長だけでなく `holdUntilTick` が必要**。
  tick番号だけではチャンク構築完了やsteady stateを証明しない。
- 部分計数から全draw数を断定しない。GPUの成功を別GPUへ、1sceneの成功を全Modへ拡張しない。
  失敗・未較正・欠測・hash不一致を成功から除外し、画像と診断の不一致を調べる。
- 過去の否定・肯定は再入条件を持つ仮説として扱い、対象artifactと有効な計器から再評価する。
