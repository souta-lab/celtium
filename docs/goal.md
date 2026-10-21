# Celtium 完成条件と目標（2026-10-21 改訂）

- Goal の状態・識別子は goal API が持つ（本書は製品仕様と完成条件）。
- 対象: Minecraft 1.12.2 / CleanroomMC 専用 native Vulkan レンダラ
- 基準環境: Cleanroom 0.6.13-alpha, LWJGL 3.4.1, Java 25, AMD RX 6400 / Intel UHD 630
- 比較基準: Celeritas 固定参照 `989ebf2e`
- 現在状態表: [plan.md](<plan.md>)（唯一）
- 方向性の根拠: [direction-2026-10-21.md](<direction-2026-10-21.md>)
- 作業規約: [../AGENTS.md](../AGENTS.md)

## 目的

Celtium を、Celeritas を明確に上回る実用レンダラとして完成させる。shaderless 描画の正しさ・FPS/frame time・主要 Mod 互換（E2E 1.93a、RLCraft 2.9.3）・配布品質を仕上げ、Native Shadow Map と Shaderpack を実画面と両 GPU で証明する。

## 現在地（実測済み・再測定しない前提）

| 項目 | 実測値 |
|---|---|
| KPI（検証税OFF, AMD, `scripts/gv-kpi.sh`） | Celtium p50 **3.20ms** / p95 7.95 vs Celeritas **1.40ms** / 1.96 ＝ **2.29x**（3ペア 2.26/2.30/2.29） |
| GPU（terrain 3 pass） | **1.4ms** ／ frame 8.5ms ⇒ GPU は遊んでいる |
| 負けの正体 | GL 呼び出し単位の変換 **3.5µs × 約290/frame ≒ 1ms**。hotspot 無し、prepared キャッシュ hit 7.7%、GC は Celeritas と同量 |
| 到着カウンタ（**違反ではない**・2026-10-21 訂正） | `glDrawArrays` **1024** 件のうち **1020 件が native 描画**・拒否 4。display list 再生は捕捉済みリストを引く（census 8/8 `captured=yes`）。**GL フォールバック描画の計器は存在しない**＝バニラは既に stub 非経由で native。残る問題は経路ではなく **per-call コスト**だけ |
| コード規模 | celtium-vulkan **71,270** 行、`VulkanTerrainGpuBackend` **18,209** 行 / 980 fields、計器 `CELTIUM_` **309** 種 / println 385 / スイッチ 93 |

## 破らない原則

1. **バニラ／Forge の描画 API は GL スタブを通さない。** Java API レベルで捕捉し native Vulkan へ渡す。
2. **GL スタブの担当は「Mod が直接呼ぶ GL」のみ。** HUD/GUI も例外にしない（未捕捉の残留は欠陥）。
3. **`VulkanTerrainGpuBackend` への新機能追加は禁止。** 抽出と削除のみ。
4. **計器を増やさない。** 受入に使うのは ①KPI ペア ②スタブ違反2計器 ③immediate batch 数 ④validation ⑤GPU pass 時間 の5つだけ。
5. **「呼び出しごとの変換」を速くする最適化は禁止**（余地なしと計測済み）。
6. **1フレームのパスは十数個以内**（公式 Vulkan の MC-311557「45+ パス」批判の轍を踏まない）。
7. 参考実装は**設計のみ**（VulkanMod / Sulfide / Rentities / Angelica / ImmediatelyFast）。コード流用をしない。

## フェーズと受入

### Phase A — 前提の確定（**完了 2026-10-21**）
- 到着カウンタ（`glDrawArrays` / `glCallList`）は native 処理の到着数であり GL フォールバックではない、と実測で確定
- `glDrawArrays` 1024 件中 1020 件が native 描画・拒否 4、display list 再生は捕捉済みリストを引く（8/8 `captured=yes`）
- ⇒ 「境界の違反」は存在せず、追加作業なし。以後の主作業は Phase B

### Phase B — 即時描画のモデル単位バッチ化
- `(format, texture, blend, matrix)` を粗いキーにパス内蓄積し run あたり 1 draw
- **受入**: 即時 batch 数 290 → 数十、KPI ペアで 2.29x から有意改善（±6% 超）

### Phase C — フレームを明示パスで取る
- sky / terrain / entities / block-entities / particles / GUI / post を明示パス化（十数パス以内）。余力で描画スレッド分離
- **受入**: KPI ペアで **ratio ≤ 1.0**、両 GPU

### Phase D — 互換と目視
- E2E 1.93a / RLCraft 2.9.3 代表シーン、qmagnets テストマップ
- 目視4画面（loading / home / game / settings）を画像で確認
- Entity Culling 1.12.2 など既存最適化 Mod との共存
- **受入**: 各シーンの画像 + validation clean

### Phase E — 影・Shaderpack・配布
- Native Shadow Map を実画面で証明（両 GPU）。影の別 mod 化は可
- Shaderpack（BSL）実機で読み込みと描画反映
- 配布品質（0.0.6-alpha 系）、既知問題、対応 Mod 一覧
- **受入**: 影 ON の画像 + 両 GPU、BSL の実機ログ、配布物と実装の一致

## 完成条件（すべて満たした時だけ Goal 完了）

1. KPI が Celeritas 以下（**warm window** の `frameIntervalWarmP50Ms` で ratio ≤ 1.0、AMD と Intel の両方）
   - 必須条件: `frameIntervalWarmSampleCount > 0`（cold window の `frameIntervalP50Ms` は判定に使わない）
   - 必須条件: 測定した JAR の `sha256` と `git.head`/`dirty` が、その leg のビルドと一致していること
2. バニラ描画の全経路（sky / terrain / entities / block-entities / particles / GUI / font / loading / settings）が**スタブ非経由**で native に描かれ、validation が clean
3. 目視4画面（loading / home / game / settings）を画像で確認
4. E2E 1.93a と RLCraft 2.9.3 の代表シーンが実機で正常
5. qmagnets テストマップで描画と性能を確認
6. Native Shadow Map が実画面に出る（両 GPU）
7. Shaderpack（BSL）が実機で読み込まれ描画に反映される
8. 配布物（jar・設定・ライセンス・既知問題）が実装と一致

## 検証の規約

- GPU の主張には commit・jar hash・GPU・scene・camera・flags・leg を紐づける
- **AMD 成功は Intel 成功ではない。** 主要受入は両 GPU
- KPI は `scripts/gv-kpi.sh` のペアで測る（可能なら REPS=3）。検証税は `-Dceltium.vulkan.presentedDumpEvery=0`
- **`CELTIUM_KPI_JARS` に、その HEAD から build した JAR を stage したディレクトリを明示する。** 既定の固定ディレクトリを使うと別ビルドを測る（2026-10-21 に4 leg が同一 sha を測定して判明）。leg の `-manifest.json` で `artifacts.renderer.sha256` と `git.head` を照合し、不一致なら測定を破棄する
- draw 数は**全発行点の合計**で読む（`VulkanDynamicMesh` の2点だけを数えた値を全体の結論にしない）
- 失敗・未較正・欠測を成功に数えない。無音の leg は重複 jar と計器の有効性を先に疑う
- 目視は画像を残す。数値だけで「見えている」と主張しない
