# Goal 文書の監査前スナップショット

2026-10-17、`c46474aee` 時点。**史料であり指示ではない。** 古いUUID・誤認・命令形も修正せず保存する。
現行の目的は [goal.md](<../goal.md>)、現在状態は [plan.md](<../plan.md>)。

```markdown
# Goal: Celtiumを完成させる

現行 goal ID: `goal-0075306c-a69d-43e2-b28f-ee7b559d3882`。
識別履歴（維持）: `goal-8df71681-c0a5-4c71-a8a6-aa13f58f1ac7`、revision 2、旧上限 10000 round。
実行状態は DSH の goal を参照し、この文書を per-round の状態記録にしない。

Minecraft 1.12.2 / CleanroomMC専用の**高性能・高互換性ネイティブVulkanレンダラ「Celtium」**を完成させる。
Vanillaの正確な描画、安定性、FPS・frame time、既存Forge Mod互換性、配布品質を仕上げ、その後は既存の影開発計画に従ってNative Shadow MapとShaderpack対応を完成させる。
**最終的な成果は、実際のゲームで正しく動作し、既存レンダラと比較して性能と互換性を証明できるソフトウェアである。**
計画・監査・実験・リファクタリングは完成に向けた手段であり、実装・修正・最適化・統合を継続する。

## 1. 継続開発

- 毎round、最新HEAD・dirty差分・既存担当・artifact・[plan.md](../plan.md)を確認し、同じ実装単位・ブランチ・根因・TODOを必要な複数roundにわたり継続する。roundはcommitやPRの単位ではない。
- 担当起動後も主担当は実装・最適化・レビュー・統合を進める。Teammateは必要に応じ最大10名程度。長いbuild・ゲーム検証はbackground jobとして管理し、GPUは直列で共有する。
- 根因に必要な複数箇所を修正し、コンパイル・限定CPU契約・AMD/Intel実機受入を接続する。動作するソース変更とテスト・証拠が揃った単位をcommitし、CI・権限・保護条件に従って統合する。
- PR直前に `git diff origin/main --name-only` と内容を確認する。round経過だけのdocs-onlyブランチ・PR・即マージは禁止。例外は明示的な文書依頼、ライセンス・安全性・配布等の独立した目的だけ。
- [plan.md](../plan.md)冒頭の現状表は実装・根因・受入が意味的に変わった節目で直接更新する。途中経過はローカルメモか既存Issue/PRへ。主成果はレンダラであり、PR件数・round数・文書量ではない。
- 継続時はHEAD・TODO・artifactから復元し、DSHのactive/armed/runningを確認する。実際の制限や中断は原因を確認して継続可能な作業から再開する。

## 2. 現状管理と証拠

**現在状態の唯一の表は[plan.md](../plan.md)冒頭。** 実装済み・局所検証・AMD成功・Intel成功・両GPU成功・未較正・FAILED・未完を区別する。この文書は目標と完成条件を保持し、下の履歴を現行受入と解釈しない。

- 変更前にproducer・consumer・呼び出し順・所有権・状態契約を確認する。Minecraft/Forge原典と実行時Kirinoパッチを優先し、[mc-sources.sh](../../scripts/mc-sources.sh)の `--grep`・`--srg`・`--notch`・`--cleanroom` を使用する。
- 既存counter・dump・fixture・reportを読み、不足する情報だけ計器を拡張する。baseline/candidate、commit、JAR/SPIR-V hash、GPU、設定、scene、camera、測定区間、exit、validation、画像比較、frame timeを記録する。
- 競合仮説は条件を揃えた最小A/Bで判別する。失敗gateを正常扱いしない。CPU契約の成功はゲーム画質の受入ではない。
- 調査資料は[audit-2026-10-09-third.md](audit-2026-10-09-third.md)、[full-audit-0903.md](2026-10-11-full-audit-0903.md)、[full-audit-1058.md](2026-10-11-full-audit-1058.md)、[構造引継ぎ](handoff-structure-recovery-2026-10-10.md)、[Nether資料](hints-2026-10-10-nether.md)。歴史は現在のソースと実機結果を上書きしない。

## 3. 技術的な完成仕様

- 基準はCleanroom `0.6.13-alpha`、LWJGL `3.4.1`、Java25。Celtium対象モジュールはJava `--release 21`、class major65以下。比較基準はCeleritas固定参照 `989ebf2e`。既存の有効な最適化を保持・改善する。
- Minecraftの意味に沿ったnative passを完成する: terrain/chunk/sky/fog/weather、model/item/entity/TESR/particle、camera/matrix/lighting/texture/animation、font/GUI/loading/video settings、FBO/ShaderGroup/postprocess/spectator/outline、frame recording/composition/swapchain present。
- allocator・buffer・descriptor・pipeline・resource registryを共通化し、描画状態と資源寿命を一貫した契約で管理する。通常frameはGL contextなしの純ネイティブVulkanとし、GL/VK bridge・GL fallbackの機能はnative passへ置き換える。
- GPUは名前でなくVulkan feature・format・surface能力で分岐する。広いGPUを対象としmesh shaderは任意。Minecraft/Forge公開interface・イベント・modelデータ等、Cleanroomで動く既存Modの互換入口を維持する。
- 有効な変更を保護しつつ共通状態契約を改善し、個別修正の蓄積を減らす。

## 4. 開発優先順位

優先順は最新の失敗・未実装・性能測定から更新する。

**A. 正しさ・安定性**: P0/P1、crash、破損・欠落・freeze・validation違反を解消する。draw snapshot、matrix、texture unit、blend/alpha、viewport/scissor、target identity、clear順序、record/submit、resource lifetimeを共通契約として完成し、Vanillaの全描画・UI・loading・ShaderGroup・outline・presentを正しくする。

**B. FPS/frame time**: [performance_plan.md](../performance_plan.md)に沿い描画修正と並行する。CPU/GPU同期・idle、upload ring/suballocation/frame slots、buffer/descriptor/pipeline再利用、batch/indirect、mesh/texture cache、GC/native allocation、copy/readback/submit削減、fence/retirementを改善する。Angelicaや1.12.2最適化Mod・近年の設計をライセンスに適合させて参照する。通常Celeritasとの同一GPU・world・camera・設定・解像度でp50/p95/p99、CPU/GPU時間、stall、allocation、draw/upload数を比較する。

**C. 主要Mod**: JEI・GUI・custom item/model・entity layer・TESR・particle・fluids・animation・FBO・reload・world-switchを実際のModで完成する。**E2E1.93aとRLCraft2.9.3は最優先の完全Modpack互換性ターゲット。** E2EではCeltiumがNothiriumを置換しNaughthiriumも除外する。他のゲーム内容Modは保持しStellarCore等は機能単位で互換化する。RLCraftは大型entity・animation・equipment・particle・foliage・weather・water・HUDを重点対象とする。両packで実world・GUI・Modded描画・保存再読込・安定性・性能まで受け入れる。

**D. 配布品質**: 設定画面・F3 Vulkan device/driver・ライセンス・credits・リブランド・最低GPU能力・Java条件・対応Mod一覧・既知問題・再現可能buildを完成する。

**E. 影/Shaderpack**: shaderless本体と主要Modの描画成立後、既存計画・実装を活用し、解析・GLSL lowering・SPIR-V・native Shadow Map・sampling・composition・pack state/resource契約を接続する。BSL等の実packで実画面の影・性能と両GPU受入を証明する。

## 5. 完成判定

全項目を満たすまでGoalを完了しない。

1. Vanilla主要全描画・UI・設定・loading・最終合成が正しい。
2. 通常frameがGL contextなしのnative Vulkanで動き、swapchainから実window/compositorへ提示される。
3. AMD RX6400とIntel UHD630で主要scene・reload・world-switch・resize・失敗復旧を通す。
4. OpenGL参照との画像・状態比較で正しさを確認する。
5. 主要ModとE2E/RLCraftの代表sceneが正常に動く。
6. 公平なCeleritas性能比較があり重要な回帰を解消する。
7. 実装・文書・ライセンス・設定・配布物が一致する。
8. 影/Shaderpackは実native描画と両GPU受入で証明する。

失敗は次の根因修正へ接続する。修正後に必要な比較を取り、成功した契約を統合する。

## 6. 自律的な継続

理解した問題は実装へ移す。同じ仮説が停滞すれば判別実験か別手段へ進む。各roundは可能な限り実装・検証・統合・次の実装開始まで進め、Goal全体・TODOと照合して局所偏重を避ける。通常の開発判断・変更・検証・commit/pushは既存権限内で自律実行する。background jobのPID・終了状態・artifactを管理する。

**個々の調査・PR・roundの終了はGoal全体の終了ではない。開発可能な未完を継続して解消する。** 報告は簡潔な日本語とし、作業に時間を使う。

---

## 過去のマイルストーン（2026-10-15・現在状態ではない）

- **RLCraft 2.9.3 P1 = PASSED (AMD and Intel)** ✓✓ — leg `rlcraft-p1-leg7-recipe`（AMD・exit 0・PASSED・131.6s・
  `captureWorldFrames=1184`・`pictureFrameDelta_world=0`）と leg `rlcraft-p1-leg8-intel`（Intel UHD 630・
  `gpuRequirementMet=true`・exit 0・PASSED・336 frames・`pictureFrameDelta_world=-1`。**Intel 再走で 0 ⇒ 単発**）。
  Cleanroom 移行レシピ適用 profile（165 jar）＋ `--copy-pack-dir resources structures scripts`・184 mods。
  **P2**（modded scene: entity／particle／foliage・weather／water／GUI・HUD／TESR／modded dimension）は `task-7`、
  **P3**（save/reload・F3+T・dimension 遷移・resize・quit）はその後に別 task。
- **E2E 1.93a P0**: world 到達 ✓・`MemoryStack` 枯渇の解消 ✓・validation clean ✓。残りは
  (1) picture ゲートの assertion **1 件**（world/presented の deficit は **declined 7** と帰属済み）と、
  (2) **268-mod pack の GL-less（完成条件 (b)）** — 原因は `RandomPatches` が `Display` を mixin 登録前に定義することと特定し、
  coremod transformer（window hint）＋**スレッド単位の LWJGL3 capabilities** を実装済み（leg 12 待ち）。
- **Intel 受入は現行 main で再取得済み**（`gv-intel-main`・device 行と `gpuRequirementMet` の両方）。
  **P1-a（VUID-09600）は測定で閉止**（11 leg すべて VUID 0・帰属は未了と明記）。
- **性能**: PR #93 は cohort では noise 内（multipart 密集 scene での再測定まで保留）。通常コホートの A/B は収束済み。

## 過去の canon（2026-10-12 のユーザー指示・履歴として保持）

## 聖典・最優先の修正対象（2026-10-12 ユーザー指示）

**以下が全部直るまで「戻らない」。報告・受入・修正を止めない。3件程度の修正で終えない。**

1. **`docs/history/2026-10-11-code-bughunt.md` の37件（BH-01〜BH-37）を全部閉じる。**
   担当割り当て・進捗・受入は `docs/plan.md` に置く。P1から順に、各件を
   「根因の特定 → 修正 → 観測できる leg → before/after → commit/push」の単位で閉じる。
2. **文字がおかしい問題。** 現行 jar の実測では F3 overlay と chat が可読、hotbar strip が
   参照比 mean|d| **1.86**・差 0.2%。未再現なら再現条件を特定し、再現したら修正する。
   font counter は `refused=0` を維持する。
3. **エンティティとHUDが見えない問題。** HUD は受入済み（参照一致 1.86）。エンティティ/一人称の
   手持ちは根因を**特定・修正済み**: `drawPendingImmediate` の push block が 208 byte なのに lit ITEM の
   light-map 座標を offset 208/212 に書いていたため、light-map 座標を持つ group（＝model/item の batch）を
   描くたびに `IndexOutOfBoundsException` が window flush から飛び、catch が即時キュー全体を clear していた
   （修正前 `windowFlushFailures=64`・`windowFlushDroppedBatches=9680`・`drawnByFormat` id4=0/id9=0）。
   修正後: `windowFlushFailures=0`・`immediateGroupsDrawn=5297`・**`drawnByFormat` id4=17940・id9=228**、
   presented frame に**一人称の腕**が出る。**残り**: presented frame での entity band / hand の参照一致を閉じる。
   **訂正（round 246）**: 上記「HUD は受入済み（参照一致 1.86）」は **bridge（GL 経路）の測定**であり、
   `glless=true` 経路の証拠ではない。GL-less では **HUD の quad が面積 0 に collapse し、frame image に
   1画素も書かれていない**ことを実測した（`CELTIUM_HUD_GROUP` は 23 group/frame が main identity・
   `route=frame-image`・正しい GUI ortho と報告するのに、`CELTIUM_GROUP_BOUNDS` は全 group が
   `ndc x[-0.00..0.00] y[-0.00..0.00]`・`clip=(0,0,0.5,2091)`＝`cw = 1 - z`）。根因は共有 `matrixScratch` で
   `ortho` が perspective の `[11] = -1` を継承すること（GL の `glOrtho` の最終行は `0 0 0 1`）。
   修正 `-Dceltium.vulkan.scratchZero=perspective,ortho` が HUD を frame image に届かせることは実測済みだが、
   同時に**提示画像が一色黒**になる（collapse が捨てていた GUI stage の全画面 draw が届くため）ので既定は
   緑のまま。**round 248 で閉じた**: `scratchZero` 既定に `ortho` を加え、同時に **identity 0 の finished-frame
   composite を名前で落とす** `SELF_COMPOSITE_SKIP` を既定化（述語は「identity 0 かつ frame image 自身の published
   colour 名を sample する group」＝同一 image を sample しながら書く invalid usage の除去）。測定（leg
   `r212-world-hud26` jar `3cc28cb1a40e0c32`・既定確認 leg `r212-world-hud27` jar `585ba082e6afa133`）:
   `selfCompositeSkips=166`・`presentedMean=71.3`・present の `hotbarBand=(72,60,49)`・presented PNG に
   **world＋hotbar＋crosshair＋HUD テキスト**（distinct 12537）✓、harness も `status=PASSED`・
   `presentedImageVerdict=PASSED`・`worldFrameBlackFraction=0.010189`（床 0.05）✓。詳細は `docs/plan.md` の
   ヘッダ表 round 248 行。**残る確認**: Intel leg、および同一 run の `celtium-probe` capability report が
   `UNSUPPORTED_OR_UNAVAILABLE`/`effectiveTerrainBackend=opengl` と出す矛盾（plan の同項目 ✓）。
4. **font renderer の overhaul（Smooth Font 的実装・ユーザー提案）。** 原典確認の結果、1.12.2 の font は
   **bitmap sheet**（`Minecraft.java:523` が `textures/font/ascii.png`、`FontRenderer.java:160-187` が alpha から
   `charWidth` を算出、unicode は `font/glyph_sizes.bin`）で **TTF/OTF が assets に無い**。したがって
   「4x 再ラスタライズ」は同じ bitmap の拡大、「自前 atlas」は glyph rasteriser ＋ metrics 再導出を伴う
   新 subsystem で唯一「文字が悪くなる」方向に振れ得る。実現可能な滑らかさは **font mirror の sampler reach**
   （filter は既に linear、task-26 の `SAMPLER_MAX_LOD=1` が sheet の mip を level 1 で止めている）で、
   texture-mirror に着手中。subpixel 配置は game 側の parity break として較正 pair 付きで独立判断、
   生成 atlas は metrics 込みの subsystem。**速度面は既に達成済み**（1 glyph 57.9 → 0.65 µs）。

**注意**: この未較正 scene は boot 間の画素ノイズ床が **23.5%**（proj-channel 実測）なので、
絵の主張は較正済み leg（key が一致する参照）でのみ行う。ノイズ床以下の差分を修正の証拠にしない。

**この ID は live API で確認した現行 goal である。** 旧 `goal-0d07c3b5-8a9f-4698-9996-95bc88a28c68`（revision 61 と記載していた）は
監査 2026-10-11 17.2 で文書との不一致を指摘されたため、plan と本ファイルの両方を live 値へ同期した。

このファイルは goal の objective と同期する。status・優先順・受入の現状表は `docs/plan.md`（単一の管理場所）、
証拠と経緯は `docs/history/`、監査起点は `docs/history/audit-2026-10-09-third.md` と `docs/history/2026-10-11-full-audit-0903.md`。

**現状（2026-10-17 時点の現在状態は `docs/plan.md`。当時の § 番号の記録は `docs/history/` にある）**: GL context なしで通常起動のメインメニューが
**compositor 出力から撮れる**ところまで到達（`.celtium-work/ww-menu-caps/compositor-0018.png`）。
ユーザー自身の起動が真っ白だった真因は **instance に `org.lwjgl.vulkan` が無いこと**で、instance へ
`org.lwjgl:lwjgl-vulkan:3.4.1` を追加して解消（instance のライブラリだけで renderer が作られることを実測）。
メニューの「ズレ」は `ensureSwapchain` の extent 検査が world frame 境界の `frameId` に gate されていたためで、
present ごとの検査へ修正。frame identity ゲートは presented カウンタの world 採用時リセットで閉じた
（`pictureFrameDelta_world=0`・GL-less 受入 leg が PASSED）。未了は panorama 背景（`glCopyTexSubImage2D` の mirror）、
地平線帯（build 状態差・mip 内容）、block 破壊 overlay、group drop、雲、可視 glow、監査 B01〜B12 の残り、
mod cohort、Intel の残り場面。

## 維持する仕様（不変）
- Cleanroom 専用。通常 Forge（旧 Java/LWJGL2）や他 Forge+lwjgl3ify/RFB 構成の起動互換は提供対象外。
  Cleanroom で動く既存 Forge mod の Minecraft/Forge 公開クラス・メソッド署名・イベント順序・model データの互換入口は可能な限り維持する。
- Celtium の対象モジュールは `--release 21` / class major 65 以下。Java22 以降の API・言語・preview に依存しない。
  ゲーム・build・検証の JDK25 は許可し、ゲーム全体の Java21 起動を完成条件にしない。
- 検証済み Cleanroom 0.6.13-alpha / LWJGL3.4.1 / Java25 を基準に、実在する Cleanroom 公開 API・正式 Mixin/Accessor・
  直接 LWJGL3 を優先する。内部 API は依存理由と版を固定し、無根拠の反射や古い LWJGL 用互換を惰性で増やさない。
- 汎用 OpenGL→Vulkan 変換器・GL API 全体のエミュレーション・Zink の組込みを目的にしない。
  camera/terrain/model/item/entity/TESR/particle/UI/texture/FBO/postprocess/frame を意味の分かる native pass へ移す。
- GPU 名ではなく必要な API/feature/format/surface 能力で選ぶ。mesh shader を必須化しない。
  AMD RX6400 / Intel UHD630 の両方で実機検証し、未検証 vendor/OS は明記する。
- Celeritas 固定参照 989ebf2e を比較基準にする。制限付きの現行 Sodium ソースをコピー・翻訳・改名・cherry-pick しない。
- 2026-10-14のユーザー指示により、主要Mod互換と影mod／shaderpackの**実験開発を並行着手**する（[#76](https://github.com/souta-lab/celtium/issues/76)、[#77](https://github.com/souta-lab/celtium/issues/77)）。製品版の影pack互換受入には shaderless 本体・Mod・GPU/寿命の実測gateを残し、未達を完了扱いしない。影は既定OFFのnative実験経路から始める。バニラの ShaderGroup・spectator 効果・entity outline は本体機能として省略しない。
- 一括削除・全面書き直しをしない。有効な修正を保持し、一つの根因の所有・状態契約を揃える小さな修正を進める。

## 原典を読む（必須・2026-10-11 追加）
- build は**デコンパイル済み Minecraft/Forge ソースと SRG 名表**を通常の build の一部として生成している。
  `scripts/mc-sources.sh` がパスを示し、`--grep` で検索し、`--srg` で deobf↔SRG を両方向に引く。
  これらは数週間未読のまま、署名・draw mode・vertex format・呼び出し順序・shader 入力を推測で決めて何 round も往復した。**二度としない。**
- mixin の target、format id、draw mode、shader 入力、順序の議論を書く前に原典で確認する。`javap` は原典が扱わないクラスの fallback。
- このプロジェクト自身の計器（counter・dump・report）にも同じ規則: 既にあるものを読む。新しく足したら報告に名前を書く。
  担当を起動する時は原典のパスと `scripts/mc-sources.sh` を必ず渡す。
- 規則は `AGENTS.md`（新規参加者と担当が最初に読むファイル）に置く。
- 原典で確定した事実は `docs/history/handoff-2026-10-10-next-model.md` の § 群と `docs/history/`（バニラの (mode, format) 全表・`POSITION_NORMAL` が未対応・font glyph の契約）。

## 失敗しているゲートは証拠（必須・2026-10-11 追加）
- 画像ゲートは 09:33 以降の全 GL-less run で `sceneGateFloorsEnforced=true`・`sceneCalibrationKeyMatches=true`・
  `presentedImageVerdict=FAILED_PRESENTED_IMAGE` を出していた。**ゲートは正しかった**（terrain が画素を出していなかった）。
  私が「mean 213 の FAILED は固定 scene の正常状態」と言い換え、担当にもそう伝えたため数時間隠れた。
- 失敗しているゲートは、**測定が誤りだと示すまで証拠**。verdict を「既知の正常」と言い換えて counter だけを読まない。
- ゲートを上書きする前に、**競合する説明を分離する実験**（同一 fixture・同一カメラで2 jar／同一 jar で2 カメラ）を取り、
  どの説明が棄却されたかを書く。

## 射影チャネルの現状（2026-10-11・撤回済み）
`Project.gluPerspective` のフック（`30a30a375`）は **`f82bf4612` で撤回**。A/B 実測（同一 fixture・同一カメラ・jar のみ変更）:

| | 退行前 jar `d2073e9456da7bcc` | 現行 jar | フック撤回後 |
|---|---|---|---|
| `presentedNonBackgroundFraction` | 0.733183 | 0.364906 | **0.838628** |
| `presentedImageMeanAtCapture` | 213.6667 | 213.6667 | 44.0 |
| `presentedImageVerdict` | PASSED | FAILED | **PASSED** |
| `lastDraw/lastSections` | 22/25 | 8/8 | - |

mean がビット一致＝カメラは同一 → **renderer の退行**。フックは culling が読む射影スタックに掛かり、world を cull で消していた。
**代償**: mirror が `gluPerspective` を見ないギャップは戻る（空 fan・太陽/月・雲）。次は **`lastDraw/lastSections` を測りながら**設計する。

## 現在の到達点（2026-10-11 実測・当時の § は `docs/history/handoff-2026-10-10-next-model.md` と `docs/history/`）
- **GL context なしの window が実 scene を描いて提示する**。AMD/Intel とも bridge/GL-less 両 leg が PASSED・clean・VUID 0。
  標準 GL-less allBlocks は offers 5000 / accepted 4705（94.1%）、粒子修正後の run は**拒否 census が空**。
- **条件 (c) 充足**: `grim` で compositor 出力を連番 capture し、presented frame と **0/921600 画素差**で束縛。
  GL チャネルは 99.925% 異なる（証拠チャネルでない）。残る未測は scanout のみ（DRM master 権限なし）。
- **model/TESR 既定 ON**。N01（quad ごとの `nextVertex()` で 4 隅が同一頂点を上書き）を修正し AMD 36.9% 画素変化、
  Intel でも PASSED。float translate ミラー・`Project.gluPerspective` フック・N02 寿命契約を統合。本は GL 参照と画素位置一致。
- **texture**: lightmap の黒＝announcement の配列形状を修正（両GPU）。format 5 の descriptor gate を修正（73/73）。
  **depth format は能力ベース選択**（AMD に 24-bit depth が無い実測に基づく preferred + 5段 fallback + 名前付き拒否）。
- **Nether** は capture 時 dimension -1 で clean。画像ゲートは scene/pose/variant を key にし、未較正は `UNCALIBRATED_PRESENTED_IMAGE`。
  **video settings は bridge で動作**、GL-less は中身欠落で**正しく FAIL**（床は下げていない）。
- **性能（制限付き）**: GL-less p50 3.94ms / bridge 4.89ms / 参照 16.10ms。参照は 60Hz フロアであり「4倍速い」とは主張しない。

## 未完（優先順は plan の実行順）
**受入済み（このセッション）**: N03（font — glyph ポインタ修正が全て。magenta probe で実証・unicode 経路も初行使・advance は構造的に不変）、
N10（UBO 経路 — color_convolve 192B・spider 144B を SPIR-V から実測、ShaderGroupMixin の4バイトずれも修正）、
N07/N08/N17/N05、監査 A01〜A05、所有権ギャップ4つ（N17 画像側は bind 後の fault で実測）。
**outline chain は実行を確認**（`CELTIUM_POSTCHAIN create`・`program=entity_outline`・chain pair も実行時に使用）。
**撤回した主張（計器で反証）**: 旧「runtime は entity outline を描かない」・旧「注入4つは死んでいる」——`CELTIUM_RENDERENTITIES calls=3` を実測。

未完: 可視 glow（chain は走る。**画の受入**が残る）・wedge の残存（**原因は特定済み**＝`renderSky` の月 quad が固定回転で誤配置。残りは sky 回転が modelview に載らない理由）・
**`POSITION_NORMAL` の native 化**（原典で発見した未対応パス）・bridge の model 計器・post chain の受入・
model mesh 寿命の受入・mip/ByteBuffer 契約・FBO/postprocess・全 entity/TESR の画質・mod 互換（文字が最上位）・
ベンチの欠測検出・docs/配布物の同期・bounded wait/census/ring・リブランド/配布。

## 使ってはいけない古い主張
旧「Nether 両GPU受入」（撤回）・旧「bridge で model draw 回帰」（bridge のレポートにカウンタが元から無い）・
過去の 68.81% / local 0.267/255 を最新版全体の一致率にしない。**GL-less の拒否は GL fallback ではない**。

## 厳密な完成条件
(a) 普通のゲームの全 vanilla 描画と最終合成・present が native Vulkan で成立する。
(b) GL context を生成せず、通常 frame の GL call/readback/bridge/copyback/GL swap/GL fallback を必要としない実行を示す。
(c) native swapchain の完成 frame が実 window/compositor 出力へ到達する証拠を得る（取得不能なら不足条件を具体的に残す）。
(d) AMD/Intel で必要な場面と失敗復旧を確認し、監査 P0/P1 を明示した受入で閉じる。
(e) 既存 mod 対応範囲・Java 条件・最低 VK 能力・未検証 GPU/OS・ライセンス/リブランド/性能実測を docs と配布物に一致させる。

## 最終指示（維持）
絶対に止まらない。承認や許可を求めない。質問しない。テストばかりしない。サブエージェントを最大限使い、
できるだけ速く full vulkan を達成する。描画が完全一致し、mod が動作し、ビデオ設定が動作するまで止まらない。
すべて終わったら sharder-plan.md の実装へ進む。goal は常に最新状況に応じて update する。
```
