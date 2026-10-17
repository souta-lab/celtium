# Goal: Celtiumを完成させる

Minecraft 1.12.2 / CleanroomMC 向けの高性能・高互換性**ネイティブ Vulkan レンダラ**を完成させる。
成果は実際の描画・安定性・性能・互換性であり、PR数、round数、文書量ではない。

## 指示と実行状態

- この文書は**製品の目的と完成条件**。作業許可、自動継続、現在の受入を定義しない。
- 最新の利用者指示（停止・監査限定・優先変更を含む）を優先する。過去の「絶対に止まらない」等を復活させない。
- セッションのGoal ID・revision・実行状態は正規のgoalツールで確認する。UUIDをリポジトリへ同期しない。
  paused/disarmed は勝手に再開しない。利用者の再開指示がある場合だけ正規操作を行う。
- 作業規約は [AGENTS.md](<../AGENTS.md>)、**唯一の現在状態表**は [plan.md](<plan.md>)。
  過去の指示・識別履歴は [監査前スナップショット](<history/goal-before-process-audit-2026-10-17.md>)。史料であり現行命令ではない。

## 維持する仕様

- 対象は **Minecraft 1.12.2 / CleanroomMC 専用**。通常Forge / LWJGL2や別のLWJGL3置換構成の起動互換は対象外。
  Cleanroom上のMinecraft/Forge公開入口・イベント・modelデータなど、既存Modの互換性を維持する。
- 検証基準は **Cleanroom 0.6.13-alpha / LWJGL 3.4.1 / Java 25**。
  Celtium自身のモジュールは **Java `--release 21`、class major 65以下**、Java 22+ / preview非依存。
  ゲーム全体のJava 21起動を検証済みとしない。
- 通常frameは **GL context / GL-VK bridge / GL fallbackを必要としない**native Vulkan。
  汎用GLエミュレータやZink組込みではなく、terrain、model/item、entity/TESR、particle、GUI、texture、FBO、postprocess、presentを意味のあるnative passとして実装する。
- allocator・buffer・descriptor・pipeline・resource registry、描画状態・所有権・寿命を一貫した契約で管理する。
  有効な変更を保持し、根因に対応したレビュー可能な差分にする。
- GPUの機能分岐は名前でなく **feature / format / surface能力**。mesh shaderを必須にしない。
  **AMD RX 6400 / Intel UHD 630**を独立に受け入れ、他GPU / OSは未検証を明示する。
- 比較基準は **Celeritas `989ebf2e`**。ライセンス・由来を保持する。
  制限付きの現行Sodiumソースをコピー・翻訳・改名・cherry-pickしない。

## 優先順位と対応範囲

1. **正しさ・安定性**: Vanilla主要描画、UI・loading・video settings、ShaderGroup・spectator・outline、最終合成・提示、reload・resize・復旧。
2. **FPS / frame time**: 正しさを維持し、同条件のCeleritas比較でp50/p95/p99、CPU/GPU、stall・allocation等を評価する。
   詳細設計は [性能計画](<performance_plan.md>)。過去の数字を現行性能に流用しない。
3. **主要Mod互換**: **E2E 1.93a / RLCraft 2.9.3**が最優先の完全Modpack目標。
   E2EではNothiriumを置換しNaughthiriumを除外する。他の内容Modを保持し、StellarCore等は機能単位で互換化する。
   entity・TESR・particle・foliage・weather・water・GUI/HUD、保存再読込・world-switch・性能を実worldで確認する。
   terrain画像ゲート1本の成功はpack全体の受入ではない。
4. **配布品質**: 設定、F3 device/driver、Java/GPU条件、ライセンス・credits、リブランド、既知問題、再現可能buildを整合させる。
5. **Native Shadow Map / Shaderpack**: [影計画](<shader-plan.md>)の既存資産を接続し、BSL等の実packで影・composition・性能を確認する。
   shaderless本体と主要Modの受入を製品化の前提とする。既定OFFの実験の並行可否は利用者の最新指示に従い、実験を完成扱いしない。

## 完成条件

- 上記主要描画・互換範囲が正しく動作し、native swapchainの完成frameが**実window/compositorへ提示**される証拠がある。
- 両GPUで主要scene・reload・world-switch・resize・失敗復旧が通り、GL参照との画像/状態比較がある。
- 主要ModとE2E/RLCraftの代表的な実プレイ・UI・保存再読込が成立する。
- 公平なCeleritas性能比較があり、重要な性能回帰を解消している。
- 実装・文書・配布物・ライセンス・設定が一致する。
- 影/Shaderpackは実native描画と両GPU受入で証明する。

局所CPUテスト、実装済み、AMD成功、Intel成功、両GPU成功、未較正、FAILED、未完を区別する。
個々の調査・PRの終了で製品Goalを完了しない。一方、**停止指示や権限境界を越えて継続しない**。
