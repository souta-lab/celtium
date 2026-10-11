# Celtium goal — 2026-10-12 更新（live: `goal-780c63c7-acf3-4426-80dc-6c99d30d8627`, revision 27, phase active / activation armed / roundsStarted 103 / maxGoalRounds 1000, phase active / activation armed / roundsStarted 27 / maxGoalRounds 256）

goal ID: `goal-780c63c7-acf3-4426-80dc-6c99d30d8627`

**persisted goal は目標のみを保持する**（ユーザー指示 2026-10-12: goal は俺が喋らないと変えられないので、目標だけにしろ）。
goal revision 27 の objective は「完成目標 + 直近の必須修正3件 + 詳細はリポジトリの聖典に従う」の短い形で、
**詳細な仕様・優先順・受入・作業規律・証拠の扱いはこのファイル・`docs/plan.md`・`AGENTS.md`・
`docs/history/2026-10-11-code-bughunt.md` が聖典**である。以下はその詳細側（goal から移した canon）。

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
証拠と経緯は `docs/history/`、監査起点は `docs/audit-2026-10-09-third.md` と `docs/history/2026-10-11-full-audit-0903.md`。

**現状（plan.md §0.175〜§0.177 が最新）**: GL context なしで通常起動のメインメニューが
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
- 影 mod / shader pack 互換は本体の shaderless 完成後。バニラの ShaderGroup・spectator 効果・entity outline は本体機能として省略しない。
- 一括削除・全面書き直しをしない。有効な修正を保持し、一つの根因の所有・状態契約を揃える小さな修正を進める。

## 原典を読む（必須・2026-10-11 追加）
- build は**デコンパイル済み Minecraft/Forge ソースと SRG 名表**を通常の build の一部として生成している。
  `scripts/mc-sources.sh` がパスを示し、`--grep` で検索し、`--srg` で deobf↔SRG を両方向に引く。
  これらは数週間未読のまま、署名・draw mode・vertex format・呼び出し順序・shader 入力を推測で決めて何 round も往復した。**二度としない。**
- mixin の target、format id、draw mode、shader 入力、順序の議論を書く前に原典で確認する。`javap` は原典が扱わないクラスの fallback。
- このプロジェクト自身の計器（counter・dump・report）にも同じ規則: 既にあるものを読む。新しく足したら報告に名前を書く。
  担当を起動する時は原典のパスと `scripts/mc-sources.sh` を必ず渡す。
- 規則は `AGENTS.md`（新規参加者と担当が最初に読むファイル）に置く。
- 原典で確定した事実は plan §0.129（バニラの (mode, format) 全表・`POSITION_NORMAL` が未対応・font glyph の契約）。

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

## 現在の到達点（2026-10-11 実測・詳細は plan §0.100〜§0.126）
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
