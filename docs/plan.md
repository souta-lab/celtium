# Celtium 現状表（唯一の現在状態表）

2026-11-06 全面改訂。開発ベース **0.2.2**（[build定義](<../build-1122/build.gradle.kts>)）、最新リリース
[**0.2.5**](<releases/0.2.5.md>)（2026-11-20、影パスの地形描画・#423 のクラッシュ修正・swapchain 初期化の作り直しを含む正式版。前任の正式リリースは [v0.2.4](<releases/0.2.4.md>)、2026-11-17）。製品の出口は [goal.md](<goal.md>)。旧い表・日記・証拠は git 履歴にあり、現在の判定として読み戻さない。

## 現状（main `3090a65`・2026-11-19）

| 領域 | 状態 | 根拠 |
|---|---|---|
| **FPS（KPI: Supersymmetry 0.2.0.2・188 mods）** | **r14（`r14kpi-amd`/`r14kpi-intel`、renderer `1ae204dd`）: `goalCondition=NOT_MET`** — AMD p50 1.099・p95 1.056・p99 1.113、Intel p50 1.204・p95 1.084・p99 1.080。**これは正直な比較に直した結果**: KPI scene は GTCEu の bloom chain（15 full-screen pass/frame）を毎フレーム bind し、r13 までは既定の opt-in がその全 batch を拒否していた（`shaderProgramRefusals` 194,775/12,985 frame）。Celeritas は同じ chain を描くので、**r13 の 0.893/0.877/0.980 は「描画を削って得た FPS」**で達成に数えない。r14 で `modPrograms` を既定 on にし chain を描くと p50 2.694ms（対比 2.408）で 1.099（内訳は「次の作業」§1）。**その後の paired KPI は未実施**: 0.2.4 の +12.0/+12.3 %（PR #421）は celtium 自身の前後比較で Celeritas との比ではないので判定に使えず、0.2.5-rc1/rc2 も未再計測。 | [証拠](evidence/mod-program-default-2026-11-07.md)、[0.2.4](<releases/0.2.4.md>) |
| **E2E-E 1.87.3（≈430 mods）** | **AMD・Intel とも world・validation 0**（fx1）、Xaero raw-FBO identity でも 0（xr1）。0.7.14 用の pack 側置換3件。**verified ではなく partial**: ログ全文レビューと同 pose の Celeritas 画像比較が未了 | [証拠](evidence/e2e-cleanroom07-2026-10-26.md), [modpacks.md](<modpacks.md>) |
| **modpack 拡大** | **6 pack が3条件（validation 0・ログ全文レビュー・同pose Celeritas 画像、AMD/Intel）を満たす**。**ただし Nomifactory の画像は r126（bloom 既定前）で、r14 以降の jar での撮り直しが残る ＝ ビルド現行で3条件が揃うのは 5 pack**。Supersymmetry と E2E-E は **partial**（validation 0、ログ全文と同pose画像が未了。Supersymmetry の FPS 記述は r107 の bloom 拒否時の測定なので goal.md の規則では使えず、再計測か削除が要る）。内訳: SkyFactory 4 4.2.4（0.68 / 0.03 %・0.86 / 0.07 %）、All the Mods 3 6.1.1（7.39 / 6.61 %）、RLCraft 2.9.3（7.04 / 6.39 %・6.95 / 6.02 %）、SevTech: Ages 3.2.3（1.22 / 0.41 %・1.22 / 0.40 %）、Nomifactory CEu 1.7.7（3.28 / 0.06 %・3.30 / 0.07 %）、Project Ozone 3 3.4.11F（5.46 / 1.04 %・5.17 / 1.19 %）。pack 側の非互換は置換として記録。残る Celtium 側の欠陥は defect 10（ATM3 の上空）・5（menu 境界の破棄）・6（fixture の presented 計数。`559f979` で修正着地、fixture のみ・受入 leg 待ち） | [modpacks.md](<modpacks.md>), [証拠](evidence/modpack-reverify-2026-11-06.md) |
| **画質（4画面＋QMAGNET）** | settings 画素一致、world 16階調超 0.97/1.05 %（AMD/Intel）、menu・creative grid・tab icon 一致 | [証拠](evidence/image-quality-2026-10-28.md) |
| **画質（modpack の world）** | **葉の暗化を修正**（`r123`）: atlas の whole-image 更新が game 自身の mip を blit 鎖で上書きしていた。SF4 AMD の Celeritas 比較が **3.59 / 5.47 % → 0.67 / 0.02 %**。ATM3 の一人称手（`1565129a2`）と FBP 粒子（`f0efe0055`）は r122 の画像で確認。**display list の記録漏れを修正**（`r125`/`r126`）: `GlStateManager.glNewList` 経由の list が stub に記録されず replay が空だった（ATM3 の Astral Sorcery skybox、`unresolved=937 → 0`）。ATM3 AMD の Celeritas 比較は **12.82 / 9.9 % → 7.39 / 6.61 %**、Nomi は **5.25 / 4.0 % → 3.28 / 0.06 %**（AMD）・**3.30 / 0.07 %**（Intel）で上空の帯も解消。ATM3 の上空の帯（defect 10）は Astral Sorcery の skybox で残る | [証拠](evidence/modpack-reverify-2026-11-06.md) |
| **利用者報告** | パノラマ・エンドポータル・F2・ガラス・残像・退出後ボタン・Xaero（クラッシュ/黒/位置/透け）・ロード画面・F3+T・入室ラグ・移動時の欠け: 修正済み。**未決 2 件**: issue #422（RX 7900 XTX・ワールド参加時の紫ブロックは 0.2.5-rc1 の swapchain 初期化で修正、VSync 時のチラつきはローカル再現なしで報告者待ち）、issue #423（GTX 1650 Ti・新規ワールド作成時のネイティブクラッシュ。0.2.5-rc2 で immediate-flush 経路のフレームバッファ寿命を修正（`eac44bf`）、コード監査・単体テスト・CI で確認済み。**最終判定は報告者の実機確認待ち**） | 各証拠は git 履歴、[0.2.5-rc1](<releases/0.2.5-rc1.md>)、[0.2.5-rc2](<releases/0.2.5-rc2.md>) |
| **mod自前GLSL** | GTCEu bloom: **既定で native 実行**（`-Dceltium.vulkan.modPrograms=false` で拒否に戻せる）。chain の 16 pass が描画・validation 0・program 拒否 0、lamp row は 170.8→210.1（GL 参照 243.0）。**MRT は `gl_FragData[n]` の per-location fragment output 化で 8 出力まで**（`76631f3`..`2d59c3d`）、**GLSL 330 の in/out は明示 location で lower**（`5261fe7`、rc2 収載）。**残る壁**: uniform block がデバイスの push-constant 上限を超える FFP プログラムはサイズ別に拒否・計数（`ae28296` で上限を翻訳器へ）、format 2 の `a_Color` に source が無く NOT_DRAWN の program=60/768、`unexpected token` でコンパイルに落ちる program=177（切り分け中）。**halo は未達**: bloom layer の terrain geometry が通常 build されない（`CELTIUM_LAYER_GEOMETRY layer=Bloom ... passes=[...=false]`）。 | [証拠](evidence/mod-program-default-2026-11-07.md) |
| **コード整理** | `VulkanTerrainGpuBackend` 22,576 → 21,038（2026-11-06 の分割）→ **21,898 行（`dc4f7fc` 実測）**、`CeltiumGlStub` 11,649・`ValidationClient` 13,431。Phase 0 で `NativePassRegistry`・`PassExecutor`・名前つき target 台帳（`NamedRenderTargetAllocator`）を分離 | `wc -l` 実測、`8d24763`..`3dddf50` |
| **CI・マルチ OS** | **3 OS で同一スイートが green**: Ubuntu の従来 leg（`minecraft-1122`・`probe`）に加え、`vulkan-multi-os` マトリクスが Windows（`windows-latest`）・macOS（`macos-latest`）で `:celtium-vulkan:test`（94 suites / 462 tests）と OS 非依存検査を実行し全 leg 成功（run 43877915571）。前提として OS 依存監査で実欠陥 5 件を修正（LWJGL natives の Linux 固定、シェーダコンパイラの `.exe` 探索と `glslang` 改名追随、CI の Ubuntu 単独、macOS の読み取り範囲プロバイダ欠落）。Forge のパッケージング（`:forge122:packageJar`）は Ubuntu leg のまま。実機でのゲーム内描画確認は Windows・macOS とも未実施。スイートは **Demonica 互換サーフェス（出荷 jar のバイトコードから抽出した Tier A マニフェスト 125 クラス / 186 メソッド / 53 フィールド）の照合**を含み、`probe` ジョブはモジュール退役（`131a746`）後は純 Python の契約検査のみ | [workflow](<../.github/workflows/celtium.yaml>)、[run 43877915571](https://github.com/souta-lab/celtium/actions/runs/43877915571) |
| **シェーダーパック（Demonica）** | **本体 native 経路で地形まで描ける**（0.2.5-rc1、AMD RX 6400 の検証フィクスチャ、Demonica 0.9.1 + ComplementaryReimagined r5.9.3 のガード解除ローカルビルド、presented-image PASSED）: Phase 0 の native pass 基盤、image unit 32 と `GL_ARB_shader_image_load_store`、MRT 8、テクスチャ unit の是正（per-stage 32 / combined 64）、44 バイト拡張頂点、地形 3 パス（solid / cutout_mipped / translucent）。**影の読み込みはネイティブ経路に載った**（0.2.5-rc2、ネイティブ深度コピー提供 8 回以上・拒否 0）。地形 draw は影 target に着地し（419）、§3.4 出版も実装済み。ただし**暗転は未成立**。**製品版 Demonica jar は QuarantineGuard が Celeritas jar のピンを照合するため有効化しない** | [0.2.5-rc1](<releases/0.2.5-rc1.md>)、[0.2.5-rc2](<releases/0.2.5-rc2.md>)、`c9a1a45`..`3dddf50`（Phase 0）、`76631f3`..`2d59c3d`（MRT）、`36e878e`/`7705455`（image unit）、`65b2aad`（44 バイト頂点）、`33d0e07`..`689444f`（深度経路・影読み込み登録） |
| **粒子（カリング計器）** | **P-0 計器は着地**（`5954877`、項目339）: 粒子の到達可能性を数える計器群と不変確認 leg が通過。**提出スキップ（P-1 以降）は未実装** — 実 scene の率測定（P-1、probe-only）→ P-2 → P-3 が残る。スプライトアニメーションとの結合は切り分け済みで実害なし（項目503） | 項目339・503 の記録 |
| **吸収（Built into Celtium）** | 2026-11-17〜18 に VulkanMod-Next の D23 頂点パッキング（**24 バイト地形頂点を既定に**）・D25 draw-list retention・D13 動的ライト、Vulcanizator の生存層（stage kill switch・post chain quarantine・hardware profile gate 表・config 移行）・地形予算（**既定 4,000,000 頂点 / 16,384 draw**）・ハンドル世代、LoliASM/VanillaFix の可視スプライトのみアニメを本体実装で取り込んだ。既定が変わる機構は kill switch を持つ | [mod-compatibility.md](<mod-compatibility.md>) の Built into 表、`8aee01d`/`b37806c`/`b659f74`/`8328db7`/`e43a168`/`e8ed288`/`0e2e495` |
| **非互換リスト** | 起動時拒否は **8 mod**（Gnetum・Custom Loading Screen・SmoothFont・Nothirium・Naughthirium・OptiFine・Vintagium・Relictium）。加えて「同時に未検証」の表に Alfheim/Pulsar（光源データ側で描画経路ではない、2026-11-17 裁定）・Phosphor/Hesperus・FoamFix・VintageFix・TexFix・LoliASM/CensoredASM・Particle Culling・FPS Reducer・VanillaFix・BetterFps を理由つきで分離 | [mod-compatibility.md](<mod-compatibility.md>)、`423a74c`/`6dce97d`/`1bfdff1` |
| **リリース** | v0.2.3（11-16、マルチ OS 検証）→ v0.2.4（11-17、PR #421、Latest）→ v0.2.5-rc1（11-17、pre-release、Demonica 地形描画と #422 の present 修正）→ v0.2.5-rc2（11-18、pre-release、#423 のクラッシュ修正・影読み込みのネイティブ化・同梱 SPIR-V 再検証）。**main `3090a65` は rc2 から 53 commit 先行**（項目416 の device-lost 堅牢化を含む）。tag push で `packageJar` → jar/版検証 → Release 添付まで回す `celtium-release.yaml` が入り、死んでいた `gtnh.yaml`/`release.yaml` は削除済み。配布 jar の sha256 は rc1 `4e0175b2…`・0.2.4 `bd5c8fca…`（release asset の digest）、rc2 は未取得 | [workflow](<../.github/workflows/celtium-release.yaml>)、`51e17fd`、[0.2.4](<releases/0.2.4.md>)、[0.2.5-rc1](<releases/0.2.5-rc1.md>)、[0.2.5-rc2](<releases/0.2.5-rc2.md>) |

## 引き継ぎ（2026-11-07 時点のスナップショット。現在の判定は上の表）

modpack 再検証で見つかった問題の一覧と再現手順は [modpack-reverify-2026-11-06.md](evidence/modpack-reverify-2026-11-06.md)。
修正済み: 葉の暗化（atlas mip、`r123`）・FBP 粒子の頂点形式（`f0efe0055`）・ATM3 の一人称手（`1565129a2`）・
display list の記録漏れ（`r125`/`r126`）・**defect 3 の nested ordering flush（`r129`）**: 二重 window flush 失敗が command state を recording のまま残し、次の flush が全 batch を捨てていた（RLCraft で 2,050 batch）。同じ失敗を両armで再現した A/B で `orderingFlushFailures=0`・画像 7.04 / 6.39 % →
6.76 / 5.87 %。**6 pack すべて verified**（`w-sf4-*`・`w-rlcraft-*`・`w-sevtech-*`・`w-po3-*`、r126）。
未解決: ATM3 の上空の帯（defect 10、Astral Sorcery の skybox で dome の draw は発行されるが presented 画像に届かない）・
menu 境界の破棄（defect 5、43–1,314 batch/pack）・fixture の presented 計数
（defect 6、RLCraft の AMD leg が `FAILED_PRESENTED_IMAGE` になるが絵は正しい）。**defect 12（GTCEu bloom の program 拒否）は
r14 で既定 on にして解消**（拒否 0、chain が描く）が、halo は bloom layer の geometry 待ちで、**Nomi の画像は撮り直しが必要**。
再検証は `scripts/modpack-reverify.sh`。

**KPI leg はハーネスファイルの `guiScale=3` を共有設定で渡す**（2026-11-07 実測。渡さないと calibration key が合わず fixture が leg を拒否する）。

## 次の作業（優先順）

1. **FPS: まず現 main の paired KPI を取り直す**（最後の KPI は r14、2026-11-07）。r14 は bloom chain を描く正直な
   比較で `NOT_MET`（AMD 1.099/1.056/1.113、Intel 1.204/1.084/1.080）。0.2.4 の +12.0/+12.3 %（PR #421）は
   celtium 自身の前後比較で Celeritas との比ではないので、判定には使えない。`scripts/gv-kpi.sh` を両 GPU で
   `guiScale=3` を共有設定にして回し、`goalCondition` を更新する。r14 の内訳
   （window 5.0→19.6/frame、15 window × ~36us）は再計測で取り直す。
   tick frame 側の残り（`animBatchFlush` 66us・`animMirror` 30us・`dyn*` 28us）は第2群。
2. **bloom の halo**: bloom layer の terrain geometry が通常 build されない（`CELTIUM_LAYER_GEOMETRY layer=Bloom ...
   passes=[TerrainRenderPass[name=bloom]=false]`、同じ scene で `true` の回もある）。`terrainToBoundTarget` の routing は
   動く（`gt1-amd` で 4 frame 発火）ので、次は runtime 追加 layer の build の pass set を読む。
3. **残る Celtium 欠陥**: defect 10（ATM3 の上空）・defect 5（menu 境界の破棄）・defect 6。defect 6 は fixture 側の
   修正が着地し（`559f979`、window/skip 帰属）、**受入 leg（RLCraft AMD の再実行）が残る**。
   **Nomi の画像は r14 以降の jar で撮り直す**（21,885 拒否は 0 になり chain が描くので r126 の数値は変更前）。
4. **Xaero radar の entity icon**: 描かれるか未確認（entity の居る scene が必要、ハーネスファイルの `keepEntities=true`）。
5. **巨大クラス分割の継続**と効果の無い実験 switch の削除（挙動不変、CPU テストで確認）。
6. **影**: 影 target 着地（419）と §3.4 出版は実装済み。暗転は未成立。UBO 経路は実装済み（lowering 拒否は解消）。行使は deferred program の申し出が来る leg 待ち。（397 `SHADOW_VIEW` は未到達の仮配線、項目406）深度ストア・native depth copy・
   `glBlitFramebuffer` 深度経路と影パス読み込みの native 提供は着地済み（0.2.5-rc2、served ≥8・refused 0）。
   shaderpack 対応は本体 native 実装で進行中で、別 addon jar の
   成果物はまだ無い。製品版 Demonica jar の QuarantineGuard は利用者側制約で残る。

## 棄却済み（再入条件が無い限り再開しない）

- 窓の統合 sticky pass（p50 2.85→11.47 ms）・bind flush の延期（RADV SIGSEGV 2回）・font run の行列 bake（94 run 中 8）・
  format 統合（交替は texture も必ず違う）・A-B-A 合流（group の限界費用は測定以下）・indirect immediate の既定化
  （stride 整列で絵が壊れる）・JIT deopt 循環（renderer に deopt 無し）・value-keyed hit（効果なし、残置）。
  詳細は [罠](<verification-traps.md>) と各証拠。
- validation clean・`exitCode=0`・色数 gate は、描画の存在・正しい絵・画像一致をそれぞれ証明しない。

## 参照

[作業規約](<../AGENTS.md>) / [目標](<goal.md>) / [手順](<development.md>) / [計器](<instruments.md>) /
[罠](<verification-traps.md>) / [modpack 一覧](<modpacks.md>) / [README 規則](<readme-rules.md>)
