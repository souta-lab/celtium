# Celtium 現状表（唯一の現在状態表）

2026-11-06 全面改訂。開発ベース **0.2.0**（[build定義](<../build-1122/build.gradle.kts>)）、最新リリース
[v0.2.0](<releases/0.2.0.md>)（2026-11-04）。製品の出口は [goal.md](<goal.md>)。旧い表・日記・証拠は
git 履歴にあり（2026-11-06 に docs から削除）、現在の判定として読み戻さない。

## 現状（main・2026-11-06）

| 領域 | 状態 | 根拠 |
|---|---|---|
| **FPS（KPI: Supersymmetry 0.2.0.2・188 mods）** | r107: **AMD p50 427 vs 407 FPS・p95 +7%・p99 +0.7%、Intel p50 296 vs 273 FPS・p95 +3%・p99 +0.7%**（対比中央値 AMD 0.976/0.935/0.993、Intel 0.940/0.970/0.993）。各GPU 1対が p99 で >1 のため `goalCondition=NOT_MET` | [証拠](evidence/animated-sprite-uploads-2026-11-05.md) |
| **E2E-E 1.87.3（≈430 mods）** | **AMD・Intel とも world・validation 0**（fx1）、Xaero raw-FBO identity でも 0（xr1）。0.7.14 用の pack 側置換3件 | [証拠](evidence/e2e-cleanroom07-2026-10-26.md) |
| **modpack 拡大** | SkyFactory 4 4.2.4: **AMD・Intel world・validation 0**（sf6）。toast 表示中に空が平坦な青になる不具合（非ITEM群が照明payloadで fog を失う）を `76ff182ee` で修正。All the Mods 3 6.1.1・RLCraft 2.9.3・SevTech: Ages 3.2.3・Nomifactory CEu 1.7.7: **AMD・Intel world・validation 0**（atm1・rl2/rl3・st3・nm1）。RLCraft で frame dump 有効時だけ fixture の presented 計数が止まり dump が黒い（present 自体は全成功）＝診断側の未解決。動いた pack は [modpacks.md](<modpacks.md>) へ | — |
| **画質（4画面＋QMAGNET）** | settings 画素一致、world 16階調超 0.97/1.05 %（AMD/Intel）、menu・creative grid・tab icon 一致 | [証拠](evidence/image-quality-2026-10-28.md) |
| **利用者報告** | パノラマ・エンドポータル・F2・ガラス・残像・退出後ボタン・Xaero（クラッシュ/黒/位置/透け）・ロード画面・F3+T・入室ラグ・移動時の欠け: 修正済み | 各証拠は git 履歴 |
| **mod自前GLSL** | GTCEu bloom: opt-in（`-Dceltium.vulkan.modPrograms=true`）で native 実行・validation 0、既定は program ごとに拒否・計数。shaderc 同梱 | [証拠](evidence/gregtech-bloom-2026-11-04.md) |
| **コード整理** | `VulkanTerrainGpuBackend` 22,576 → 21,038 行（IndirectImmediateRuns・ModPrograms・FrameStatsReport・GroupLighting・GroupChange・PixelSignatures・HighlightProbe を分離）、孤立 javadoc 295 件整理 | git log 2026-11-06 |

## 次の作業（優先順）

1. **有名modpackを順に動かす**（利用者指示 2026-11-06: 自分で探して動かし、古い pack は消し、動いた pack を
   一覧へ。目標は error 0 と FPS 上回り）。順: ~~SkyFactory 4~~ → ~~ATM3~~ → ~~RLCraft~~ → ~~SevTech: Ages~~ → Project Ozone 3 →
   MC Eternal → ~~Nomifactory CEu~~（Rongmario の gist の移行手順に従う）。各 pack は AMD/Intel の e2e leg で validation を
   数え、Celtium の不具合は修正、pack 側の非互換は置換として記録する。
2. **FPS の全対条件**: p99 の対ごとの揺れの内側。残る CPU 差は mod HUD の GUI 描画（InGameInfo・CreativeCore の
   文字 run が窓 flush を起こす）と GTCEu bloom の terrain pass 前 flush。`flushUploads` の空 poll 削除（`r118-poll`）は
   同一jar A/B `p2poll` 4対で旧挙動 1.012×（対 0.83–1.12、雑音内）＝効果は測定以下、無害なので残置。
3. **Xaero radar の entity icon**: 描かれるか未確認（entity の居る scene が必要、`-Dceltium.validation.keepEntities=true`）。
4. **巨大クラス分割の継続**と効果の無い実験 switch の削除（挙動不変、CPU テストで確認）。
5. **影・shaderpack**: Demonica fork の別 jar（AGPL-3.0）。本体の 1・2 の後。

## 棄却済み（再入条件が無い限り再開しない）

- 窓の統合 sticky pass（p50 2.85→11.47 ms）・bind flush の延期（RADV SIGSEGV 2回）・font run の行列 bake（94 run 中 8）・
  format 統合（交替は texture も必ず違う）・A-B-A 合流（group の限界費用は測定以下）・indirect immediate の既定化
  （stride 整列で絵が壊れる）・JIT deopt 循環（renderer に deopt 無し）・value-keyed hit（効果なし、残置）。
  詳細は [罠](<verification-traps.md>) と各証拠。
- validation clean・`exitCode=0`・色数 gate は、描画の存在・正しい絵・画像一致をそれぞれ証明しない。

## 参照

[作業規約](<../AGENTS.md>) / [目標](<goal.md>) / [手順](<development.md>) / [計器](<instruments.md>) /
[罠](<verification-traps.md>) / [modpack 一覧](<modpacks.md>) / [README 規則](<readme-rules.md>)
