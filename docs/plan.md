# Celtium 現状表（唯一の現在状態表）

2026-11-06 全面改訂。開発ベース **0.2.1**（[build定義](<../build-1122/build.gradle.kts>)）、最新リリース
[v0.2.1](<releases/0.2.1.md>)（2026-11-06）。製品の出口は [goal.md](<goal.md>)。旧い表・日記・証拠は
git 履歴にあり（2026-11-06 に docs から削除）、現在の判定として読み戻さない。

## 現状（main・2026-11-06）

| 領域 | 状態 | 根拠 |
|---|---|---|
| **FPS（KPI: Supersymmetry 0.2.0.2・188 mods）** | r107: **AMD p50 427 vs 407 FPS・p95 +7%・p99 +0.7%、Intel p50 296 vs 273 FPS・p95 +3%・p99 +0.7%**（対比中央値 AMD 0.976/0.935/0.993、Intel 0.940/0.970/0.993）。各GPU 1対が p99 で >1 のため `goalCondition=NOT_MET` | [証拠](evidence/animated-sprite-uploads-2026-11-05.md) |
| **E2E-E 1.87.3（≈430 mods）** | **AMD・Intel とも world・validation 0**（fx1）、Xaero raw-FBO identity でも 0（xr1）。0.7.14 用の pack 側置換3件 | [証拠](evidence/e2e-cleanroom07-2026-10-26.md) |
| **modpack 拡大** | SkyFactory 4 4.2.4: **AMD・Intel world・validation 0**（sf6）。toast 表示中に空が平坦な青になる不具合（非ITEM群が照明payloadで fog を失う）を `76ff182ee` で修正。All the Mods 3 6.1.1・RLCraft 2.9.3・SevTech: Ages 3.2.3・Nomifactory CEu 1.7.7: **AMD・Intel world・validation 0**（atm1・rl2/rl3・st3・nm1）。RLCraft で frame dump 有効時だけ fixture の presented 計数が止まり dump が黒い（present 自体は全成功）＝診断側の未解決。動いた pack は [modpacks.md](<modpacks.md>) へ | — |
| **画質（4画面＋QMAGNET）** | settings 画素一致、world 16階調超 0.97/1.05 %（AMD/Intel）、menu・creative grid・tab icon 一致 | [証拠](evidence/image-quality-2026-10-28.md) |
| **画質（modpack の world）** | **葉の暗化を修正**（`r123`）: atlas の whole-image 更新が game 自身の mip を blit 鎖で上書きしていた。SF4 AMD の Celeritas 比較が **3.59 / 5.47 % → 0.67 / 0.02 %**。ATM3 の一人称手（`1565129a2`）と FBP 粒子（`f0efe0055`）は r122 の画像で確認。**display list の記録漏れを修正**（`r125`/`r126`）: `GlStateManager.glNewList` 経由の list が stub に記録されず replay が空だった（ATM3 の Astral Sorcery skybox、`unresolved=937 → 0`）。ATM3 AMD の Celeritas 比較は **12.82 / 9.9 % → 7.39 / 6.61 %**、Nomi は **5.25 / 4.0 % → 3.28 / 0.06 %**（AMD）・**3.30 / 0.07 %**（Intel）で上空の帯も解消。ATM3 の上空の帯（defect 10）は Astral Sorcery の skybox で残る | [証拠](evidence/modpack-reverify-2026-11-06.md) |
| **利用者報告** | パノラマ・エンドポータル・F2・ガラス・残像・退出後ボタン・Xaero（クラッシュ/黒/位置/透け）・ロード画面・F3+T・入室ラグ・移動時の欠け: 修正済み | 各証拠は git 履歴 |
| **mod自前GLSL** | GTCEu bloom: opt-in（`-Dceltium.vulkan.modPrograms=true`）で native 実行・validation 0、既定は program ごとに拒否・計数。shaderc 同梱 | [証拠](evidence/gregtech-bloom-2026-11-04.md) |
| **コード整理** | `VulkanTerrainGpuBackend` 22,576 → 21,038 行（IndirectImmediateRuns・ModPrograms・FrameStatsReport・GroupLighting・GroupChange・PixelSignatures・HighlightProbe を分離）、孤立 javadoc 295 件整理 | git log 2026-11-06 |

## 引き継ぎ（2026-11-06）

modpack 再検証で見つかった問題の一覧と再現手順は [modpack-reverify-2026-11-06.md](evidence/modpack-reverify-2026-11-06.md)。
修正済み: 葉の暗化（atlas mip、`r123`）・FBP 粒子の頂点形式（`f0efe0055`）・ATM3 の一人称手（`1565129a2`）・
display list の記録漏れ（`r125`/`r126`、`GlStateManager.glNewList` 経由の list と compile 中の Tessellator draw）。
**Nomi の上空の帯も display list 修正で解消**（`w-nomi-amd`/`w-nomi-intel` 3.28 / 0.06 %・3.30 / 0.07 %、verified）。
未解決: ATM3 の上空の帯（defect 10、Astral Sorcery の skybox で dome の draw は発行されるが presented 画像に届かない）・
RLCraft の ordering flush / destroyed mesh / menu 境界の破棄・GTCEu bloom の program 拒否（defect 12）・fixture の
presented 計数。SevTech は専用 pose（`scripts/modpack-reverify.sh`）で地形が見えるようになった。再検証は
`scripts/modpack-reverify.sh`。**defect 1 の修正で全 pack の絵が変わるので、画像比較は撮り直しが要る**（Intel は
ATM3・Nomi 以外まだ未実施）。

## 次の作業（優先順）

1. **有名modpackを verified にする**（`docs/modpacks.md` の3条件: validation 0・ログ全文レビュー・同pose Celeritas 画像）。
   残り 4 pack（SF4・RLCraft・SevTech・PO3）が pending。`scripts/modpack-reverify.sh` で AMD/Intel/参照を撮り直し、
   `pack_verify.py logs` で Celtium 由来の行を分類する。残る Celtium 側の欠陥は defect 10（ATM3 のみ）と defect 3/4
   （RLCraft の ordering flush・destroyed mesh）、defect 12（GTCEu bloom）。pack 側の非互換は置換として記録する。
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
