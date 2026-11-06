# Celtium 現状表（唯一の現在状態表）

2026-11-06 全面改訂。開発ベース **0.2.1**（[build定義](<../build-1122/build.gradle.kts>)）、最新リリース
[v0.2.1](<releases/0.2.1.md>)（2026-11-06）。製品の出口は [goal.md](<goal.md>)。旧い表・日記・証拠は
git 履歴にあり（2026-11-06 に docs から削除）、現在の判定として読み戻さない。

## 現状（main・2026-11-06）

| 領域 | 状態 | 根拠 |
|---|---|---|
| **FPS（KPI: Supersymmetry 0.2.0.2・188 mods）** | r130（`c219ed9a`）: **AMD p50 0.869・p95 0.881・p99 1.034、Intel p50 0.898・p95 0.983・p99 0.985**（対比中央値）。p50/p95 は両GPUで勝ち、p99 は AMD 1対・Intel 1対が >1 のため `goalCondition=NOT_MET`。**p99 は tick frame の上位中央**（全frameの99%点＝tick frameの79%点）で、Celtium の tick p50 は同等以上・tick p95 は AMD 1.1ms/Intel 1.6ms の広がり（Celeritas 1.0/1.0）。plain frame は 10–15% 速く、client tick は同一。**p99 frame の内訳（percentile計器、r6）: entities が超過分の54–56%**、terrain 15%・flush 10%・gui 7%。tick loop の超過は80%が client tick 本体（両arm同一）で、render は+229us のみ | [証拠](evidence/kpi-r130-2026-11-07.md), [p99内訳](evidence/kpi-p99-attribution-2026-11-07.md) |
| **E2E-E 1.87.3（≈430 mods）** | **AMD・Intel とも world・validation 0**（fx1）、Xaero raw-FBO identity でも 0（xr1）。0.7.14 用の pack 側置換3件 | [証拠](evidence/e2e-cleanroom07-2026-10-26.md) |
| **modpack 拡大** | **6 pack が verified**（validation 0・ログ全文レビュー・同pose Celeritas 画像の3条件を AMD/Intel で）: SkyFactory 4 4.2.4（0.68 / 0.03 %・0.86 / 0.07 %）、All the Mods 3 6.1.1（7.39 / 6.61 %）、RLCraft 2.9.3（7.04 / 6.39 %・6.95 / 6.02 %）、SevTech: Ages 3.2.3（1.22 / 0.41 %・1.22 / 0.40 %）、Nomifactory CEu 1.7.7（3.28 / 0.06 %・3.30 / 0.07 %）、Project Ozone 3 3.4.11F（5.46 / 1.04 %・5.17 / 1.19 %）。pack 側の非互換は置換として記録。残る Celtium 側の欠陥は defect 10（ATM3 の上空）・12（GTCEu bloom 拒否）・5（menu 境界の破棄）・6（fixture の presented 計数） | [modpacks.md](<modpacks.md>), [証拠](evidence/modpack-reverify-2026-11-06.md) |
| **画質（4画面＋QMAGNET）** | settings 画素一致、world 16階調超 0.97/1.05 %（AMD/Intel）、menu・creative grid・tab icon 一致 | [証拠](evidence/image-quality-2026-10-28.md) |
| **画質（modpack の world）** | **葉の暗化を修正**（`r123`）: atlas の whole-image 更新が game 自身の mip を blit 鎖で上書きしていた。SF4 AMD の Celeritas 比較が **3.59 / 5.47 % → 0.67 / 0.02 %**。ATM3 の一人称手（`1565129a2`）と FBP 粒子（`f0efe0055`）は r122 の画像で確認。**display list の記録漏れを修正**（`r125`/`r126`）: `GlStateManager.glNewList` 経由の list が stub に記録されず replay が空だった（ATM3 の Astral Sorcery skybox、`unresolved=937 → 0`）。ATM3 AMD の Celeritas 比較は **12.82 / 9.9 % → 7.39 / 6.61 %**、Nomi は **5.25 / 4.0 % → 3.28 / 0.06 %**（AMD）・**3.30 / 0.07 %**（Intel）で上空の帯も解消。ATM3 の上空の帯（defect 10）は Astral Sorcery の skybox で残る | [証拠](evidence/modpack-reverify-2026-11-06.md) |
| **利用者報告** | パノラマ・エンドポータル・F2・ガラス・残像・退出後ボタン・Xaero（クラッシュ/黒/位置/透け）・ロード画面・F3+T・入室ラグ・移動時の欠け: 修正済み | 各証拠は git 履歴 |
| **mod自前GLSL** | GTCEu bloom: opt-in（`-Dceltium.vulkan.modPrograms=true`）で native 実行・validation 0、既定は program ごとに拒否・計数。shaderc 同梱 | [証拠](evidence/gregtech-bloom-2026-11-04.md) |
| **コード整理** | `VulkanTerrainGpuBackend` 22,576 → 21,038 行（IndirectImmediateRuns・ModPrograms・FrameStatsReport・GroupLighting・GroupChange・PixelSignatures・HighlightProbe を分離）、孤立 javadoc 295 件整理 | git log 2026-11-06 |

## 引き継ぎ（2026-11-07）

modpack 再検証で見つかった問題の一覧と再現手順は [modpack-reverify-2026-11-06.md](evidence/modpack-reverify-2026-11-06.md)。
修正済み: 葉の暗化（atlas mip、`r123`）・FBP 粒子の頂点形式（`f0efe0055`）・ATM3 の一人称手（`1565129a2`）・
display list の記録漏れ（`r125`/`r126`）・**defect 3 の nested ordering flush（`r129`）**: 二重 window flush 失敗が
`frameBufferOpen` だけを落として command state を recording のまま残し、次の ordering flush が全 batch を捨てていた
（RLCraft で 2,050 batch）。同じ失敗を両armで再現した A/B で `orderingFlushFailures=0`・画像 7.04 / 6.39 % →
6.76 / 5.87 %。**6 pack すべて verified**（`w-sf4-*`・`w-rlcraft-*`・`w-sevtech-*`・`w-po3-*`、r126）。
未解決: ATM3 の上空の帯（defect 10、Astral Sorcery の skybox で dome の draw は発行されるが presented 画像に届かない）・
menu 境界の破棄（defect 5、43–1,314 batch/pack）・GTCEu bloom の program 拒否（defect 12）・fixture の presented 計数
（defect 6、RLCraft の AMD leg が `FAILED_PRESENTED_IMAGE` になるが絵は正しい）。再検証は `scripts/modpack-reverify.sh`。

**KPI leg は `-Dceltium.validation.guiScale=3` を共有flagで渡す**（2026-11-07 実測）: `celtium-world-accept` の
`options.txt` が `guiScale:2` になり、参照floorの calibration key（`guiScale=3`）と一致しなくなった。渡さないと
fixture が `UNCALIBRATED_PRESENTED_IMAGE` を返し KPI が leg を拒否する（`sceneCalibrationKeyMatches=false`）。

## 次の作業（優先順）

1. **FPS の全対条件**（最優先・未達）: p99 の対ごとの揺れの内側。r6 の percentile 計器
   （`-Dceltium.vulkan.tickPercentiles=true`）で p99 frame の内訳が付いた: **entities が超過分の54–56%**、terrain 15%・
   flush 10%・gui 7%。tick loop の超過は80%が client tick 本体（両arm同一）なので、render 側の差は小さい。
   次の判別は **両armの warm-window を tick frame に絞った profile**（2026-11-05 の profile は whole-window で
   mod HUD を指した）。**warm window の開始位置が regime を決める**（join 後20–30s は chunk-load wave の尾の内側）ので、
   再現性のためにはどちらの regime を測ったかを書く。`flush` の +208us は group 数×~1.1us で、indirect multi-draw が
   構造的な答えのまま。chunk-upload budget は KPI scene では発火しない（burst 32 未満、r6 実測）。
2. **残る Celtium 欠陥**: defect 10（ATM3 の上空、dome の draw は発行・presented に届かない）・defect 12（GTCEu bloom、
   Nomi の 21,885 拒否）・defect 5（menu 境界の破棄）・defect 6（fixture の presented 計数）。pack 側の非互換は置換として記録。
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
