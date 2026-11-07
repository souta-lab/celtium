# Celtium 現状表（唯一の現在状態表）

2026-11-06 全面改訂。開発ベース **0.2.2**（[build定義](<../build-1122/build.gradle.kts>)）、最新リリース
[v0.2.2](<releases/0.2.2.md>)（2026-11-07）。製品の出口は [goal.md](<goal.md>)。旧い表・日記・証拠は
git 履歴にあり（2026-11-06 に docs から削除）、現在の判定として読み戻さない。

## 現状（main・2026-11-06）

| 領域 | 状態 | 根拠 |
|---|---|---|
| **FPS（KPI: Supersymmetry 0.2.0.2・188 mods）** | **r14（`r14kpi-amd`/`r14kpi-intel`、renderer `1ae204dd`）: `goalCondition=NOT_MET`** — AMD p50 1.099・p95 1.056・p99 1.113。**これは正直な比較に直した結果**: KPI scene は GTCEu の bloom chain（15 full-screen pass/frame）を毎フレーム bind し、r13 までは既定の opt-in がその全 batch を拒否していた（`shaderProgramRefusals=194,775`/12,985 frame = 15/frame、`CELTIUM_PROGRAM_BOUND ... Shaders.renderFullImageInFBO`）。Celeritas は同じ chain を描くので、**r13 の 0.893/0.877/0.980 は「描画を削って得た FPS」**で goal.md は達成に数えない。r14 で `modPrograms` を既定 on にし chain を描くと p50 2.694ms（対比 2.408）で 1.099。**コストの内訳**: window 5.0→19.6/frame、`windowRecordMillis` 215→421us/frame、p50 +545us。**次の標的は per-window overhead**（15 window × ~36us）で、GPU の画素ではない。 | [証拠](evidence/mod-program-default-2026-11-07.md) |
| **E2E-E 1.87.3（≈430 mods）** | **AMD・Intel とも world・validation 0**（fx1）、Xaero raw-FBO identity でも 0（xr1）。0.7.14 用の pack 側置換3件 | [証拠](evidence/e2e-cleanroom07-2026-10-26.md) |
| **modpack 拡大** | **6 pack が verified**（validation 0・ログ全文レビュー・同pose Celeritas 画像の3条件を AMD/Intel で）: SkyFactory 4 4.2.4（0.68 / 0.03 %・0.86 / 0.07 %）、All the Mods 3 6.1.1（7.39 / 6.61 %）、RLCraft 2.9.3（7.04 / 6.39 %・6.95 / 6.02 %）、SevTech: Ages 3.2.3（1.22 / 0.41 %・1.22 / 0.40 %）、Nomifactory CEu 1.7.7（3.28 / 0.06 %・3.30 / 0.07 %）、Project Ozone 3 3.4.11F（5.46 / 1.04 %・5.17 / 1.19 %）。pack 側の非互換は置換として記録。残る Celtium 側の欠陥は defect 10（ATM3 の上空）・12（GTCEu bloom 拒否）・5（menu 境界の破棄）・6（fixture の presented 計数） | [modpacks.md](<modpacks.md>), [証拠](evidence/modpack-reverify-2026-11-06.md) |
| **画質（4画面＋QMAGNET）** | settings 画素一致、world 16階調超 0.97/1.05 %（AMD/Intel）、menu・creative grid・tab icon 一致 | [証拠](evidence/image-quality-2026-10-28.md) |
| **画質（modpack の world）** | **葉の暗化を修正**（`r123`）: atlas の whole-image 更新が game 自身の mip を blit 鎖で上書きしていた。SF4 AMD の Celeritas 比較が **3.59 / 5.47 % → 0.67 / 0.02 %**。ATM3 の一人称手（`1565129a2`）と FBP 粒子（`f0efe0055`）は r122 の画像で確認。**display list の記録漏れを修正**（`r125`/`r126`）: `GlStateManager.glNewList` 経由の list が stub に記録されず replay が空だった（ATM3 の Astral Sorcery skybox、`unresolved=937 → 0`）。ATM3 AMD の Celeritas 比較は **12.82 / 9.9 % → 7.39 / 6.61 %**、Nomi は **5.25 / 4.0 % → 3.28 / 0.06 %**（AMD）・**3.30 / 0.07 %**（Intel）で上空の帯も解消。ATM3 の上空の帯（defect 10）は Astral Sorcery の skybox で残る | [証拠](evidence/modpack-reverify-2026-11-06.md) |
| **利用者報告** | パノラマ・エンドポータル・F2・ガラス・残像・退出後ボタン・Xaero（クラッシュ/黒/位置/透け）・ロード画面・F3+T・入室ラグ・移動時の欠け: 修正済み | 各証拠は git 履歴 |
| **mod自前GLSL** | GTCEu bloom: **既定で native 実行**（`-Dceltium.vulkan.modPrograms=false` で拒否に戻せる）。chain の 16 pass が描画・validation 0・program 拒否 0、lamp row は 170.8→210.1（GL 参照 243.0）。**halo は未達**: bloom layer の terrain geometry が通常 build されない（`CELTIUM_LAYER_GEOMETRY layer=Bloom ... passes=[...=false]`）。 | [証拠](evidence/mod-program-default-2026-11-07.md) |
| **コード整理** | `VulkanTerrainGpuBackend` 22,576 → 21,038 行（IndirectImmediateRuns・ModPrograms・FrameStatsReport・GroupLighting・GroupChange・PixelSignatures・HighlightProbe を分離）、孤立 javadoc 295 件整理 | git log 2026-11-06 |

## 引き継ぎ（2026-11-07）

modpack 再検証で見つかった問題の一覧と再現手順は [modpack-reverify-2026-11-06.md](evidence/modpack-reverify-2026-11-06.md)。
修正済み: 葉の暗化（atlas mip、`r123`）・FBP 粒子の頂点形式（`f0efe0055`）・ATM3 の一人称手（`1565129a2`）・
display list の記録漏れ（`r125`/`r126`）・**defect 3 の nested ordering flush（`r129`）**: 二重 window flush 失敗が
`frameBufferOpen` だけを落として command state を recording のまま残し、次の ordering flush が全 batch を捨てていた
（RLCraft で 2,050 batch）。同じ失敗を両armで再現した A/B で `orderingFlushFailures=0`・画像 7.04 / 6.39 % →
6.76 / 5.87 %。**6 pack すべて verified**（`w-sf4-*`・`w-rlcraft-*`・`w-sevtech-*`・`w-po3-*`、r126）。
未解決: ATM3 の上空の帯（defect 10、Astral Sorcery の skybox で dome の draw は発行されるが presented 画像に届かない）・
menu 境界の破棄（defect 5、43–1,314 batch/pack）・fixture の presented 計数
（defect 6、RLCraft の AMD leg が `FAILED_PRESENTED_IMAGE` になるが絵は正しい）。**defect 12（GTCEu bloom の program 拒否）は
r14 で既定 on にして解消**（拒否 0、chain が描く）が、halo は bloom layer の geometry 待ちで、**Nomi の画像は撮り直しが必要**。
再検証は `scripts/modpack-reverify.sh`。

**KPI leg は `-Dceltium.validation.guiScale=3` を共有flagで渡す**（2026-11-07 実測）: `celtium-world-accept` の
`options.txt` が `guiScale:2` になり、参照floorの calibration key（`guiScale=3`）と一致しなくなった。渡さないと
fixture が `UNCALIBRATED_PRESENTED_IMAGE` を返し KPI が leg を拒否する（`sceneCalibrationKeyMatches=false`）。

## 次の作業（優先順）

1. **FPS: 正直な比較では未達（r14 で `NOT_MET`、AMD 1.099/1.056/1.113）**。原因は r13 まで既定が拒否していた
   GTCEu bloom chain の 15 full-screen pass/frame で、r14 に描くようにしたら p50 が 2.149→2.694ms（対比 2.408）に
   なった。**標的は per-window overhead**: chain が window を 5.0→19.6/frame に増やし、`windowRecordMillis` が
   215→421us/frame、残り ~340us は各 chain window が直前の window を flush させる分・barrier・submit。
   **15 window × ~36us を削る**のが本筋（GPU の画素ではない）。r13 の 0.893/0.877/0.980 は chain を落とした計測なので
   現在の判定に使わない。tick frame 側の残り（`animBatchFlush` 66us・`animMirror` 30us・`dyn*` 28us）は第2群。
2. **bloom の halo**: bloom layer の terrain geometry が通常 build されない（`CELTIUM_LAYER_GEOMETRY layer=Bloom ...
   passes=[TerrainRenderPass[name=bloom]=false]`、同じ scene で `true` の回もある）。`terrainToBoundTarget` の routing は
   動く（`gt1-amd` で 4 frame 発火）ので、次は runtime 追加 layer の build の pass set を読む。
3. **残る Celtium 欠陥**: defect 10（ATM3 の上空）・defect 5（menu 境界の破棄）・defect 6（fixture の presented 計数）。
   **Nomi の画像は r14 の既定で撮り直す**（21,885 拒否は 0 になり chain が描くので r126 の数値は変更前）。
4. **Xaero radar の entity icon**: 描かれるか未確認（entity の居る scene が必要、`-Dceltium.validation.keepEntities=true`）。
5. **巨大クラス分割の継続**と効果の無い実験 switch の削除（挙動不変、CPU テストで確認）。
6. **影・shaderpack**: Demonica fork の別 jar（AGPL-3.0）。本体の 1・2 の後。

## 棄却済み（再入条件が無い限り再開しない）

- 窓の統合 sticky pass（p50 2.85→11.47 ms）・bind flush の延期（RADV SIGSEGV 2回）・font run の行列 bake（94 run 中 8）・
  format 統合（交替は texture も必ず違う）・A-B-A 合流（group の限界費用は測定以下）・indirect immediate の既定化
  （stride 整列で絵が壊れる）・JIT deopt 循環（renderer に deopt 無し）・value-keyed hit（効果なし、残置）。
  詳細は [罠](<verification-traps.md>) と各証拠。
- validation clean・`exitCode=0`・色数 gate は、描画の存在・正しい絵・画像一致をそれぞれ証明しない。

## 参照

[作業規約](<../AGENTS.md>) / [目標](<goal.md>) / [手順](<development.md>) / [計器](<instruments.md>) /
[罠](<verification-traps.md>) / [modpack 一覧](<modpacks.md>) / [README 規則](<readme-rules.md>)
