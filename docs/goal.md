# Celtium goal — 2026-10-11 更新（revision 65, phase active / activation armed）

goal ID: `goal-0d07c3b5-8a9f-4698-9996-95bc88a28c68`

このファイルは goal の objective と同期する。status・優先順・受入の現状表は `docs/plan.md`（単一の管理場所）、
証拠と経緯は `docs/history/`、監査起点は `docs/audit-2026-10-09-third.md` と `docs/history/2026-10-11-full-audit-0903.md`。

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
文字（N03 実画像受入）・可視 glow（chain 専用 image へ設計変更中）・wedge の残存（原典が示す3候補から識別）・
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
