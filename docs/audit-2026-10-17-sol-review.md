# 開発プロセス再監査（2026-10-17・SOL）

## 範囲と結論

利用者の依頼は**レンダラ開発ではなく、開発を円滑にする仕組みの監査・修正**。
前回の[監査記録](<audit-2026-10-17-process.md>)も監査対象とした。
ソース/文書/既存runの証拠を確認し、通常の独立レビュー2件を利用した。レンダラ・shader・実instanceは変更せず、GPU/Gradleは起動していない。
Goalは正規ツールで短縮し、その時点ではpaused/disarmedを維持した。途中にactive/armedへの変化を観測したため利用者へ確認し、停止選択後の`get_goal` revision 12で**paused / disarmed**を再確認した。変化の原因は未特定で、DSH内部を直接改変していない。停止を製品完成とはしていない。

内部の「thinking」を診断したという主張ではない。以下は観測可能な誤判定・反復・指示矛盾への対策である。

## 重要な発見と修正

| 発見（監査前） | 確認した事実 | 修正 |
|---|---|---|
| 古い命令がGoalを上書き | 旧goal文書に「聖典」「絶対に止まらない」「最終指示（維持）」と、実Goalとは異なるlive UUID | [旧文書](<history/goal-before-process-audit-2026-10-17.md>)を全文保全。現行[goal](<goal.md>)は仕様/完成条件だけ。指示優先・停止尊重を[AGENTS](<../AGENTS.md>)へ |
| roundごとの仕事の強制 | Goalが実装/検証/統合/次実装、最大10担当、状態復旧を繰返し要求 | 永続Objectiveを短縮。round/commit/PR/担当起動をノルマにせず、必要な範囲だけ読む |
| **性能制御flagが無効** | 手順と旧`gv-notax-*`のeffective argsはvalidation名前空間、実装のread APIはvulkan名前空間 | 正しい`celtium.vulkan.presentedDumpEvery`へ訂正。残る別dump/要求capture/readback/validation負荷も明示し「全税除去」を撤回 |
| **失敗が両GPU成功に昇格** | Intel resize3はfixture PASSEDでもVUID-00892が2件、validationExitCode=1。iconify1はAMDのみ | [唯一の現在表](<plan.md>)を訂正。READMEの重複受入/性能表を除き現在表へ誘導 |
| 部分観測が因果の確定に昇格 | UV先頭/末尾・一部colour・mirror一致は全雲画像一致でない。全run tick合計はstartup worstを含む。CeltiumにもrunTick hookあり | 「雲欠陥なし確定」「14 mods由来」「唯一の律速」を撤回。観測/仮説/不足条件を分離。元の数値そのものを捏造とは判断しない |
| runnerが証拠を削除 | 同名legの`rm -rf`、未検証LEGパス、失敗したmkdir待ち後に他者lockを所有扱い | [runner](<../scripts/gv_leg.py>)をfresh-only・入力事前検査・正典共有slotへ。hash/count不一致をfatal、JSONとPNGとnative GPUを合わせて判定 |
| 未較正がexit 0 | [ハーネス](<../scripts/prism_validation.py>)はUNCALIBRATEDを正常終了に含めていた | **未較正exit 2**。本当のvalidation/GPU失敗を優先し、raw JVM exitと判定exitを分離 |
| プルーンが証拠/外部pathを削除し得る | run/logs symlink追従、未知logsも削除、失敗/未完の除外なし、削除エラー隠蔽 | [pruner](<../scripts/gv-prune.py>)は既定dry-run。完了・較正・成功・provenance・別原本のchecksumが揃った依存JARだけ。logs/crash/world/config/全画像を保全 |
| 索引に偽のswitchと欠落 | 翻訳キーを`-D`として列挙し、prefix付きNATIVE_DEVICE/DRAW_SITE等を落としていた。鮮度checkは通っていた | [生成器](<../scripts/instrument-index.py>)は実reader APIを抽出、単純定数を解決。偽の既定値を推測せず、対応範囲の限界と出力位置を示す |
| skillが機械外に出ない | 以前のskillはホーム内だけで、実flagも誤っていた | [正典skill](<../.agents/skills/celtium-verify/SKILL.md>)をrepoへ。ホーム側は正典への入口だけ。手順の複製をしない |
| 検査が遅い/意味を保証しない | Pythonだけの索引checkがForge build後。既存lock競合テストはCI対象外 | [CI](<../.github/workflows/celtium.yaml>)で軽量process検査を先行。誤flag/リンク/入口肥大/索引鮮度を[文書契約](<../scripts/check_agent_docs.py>)で検出 |

## 証拠と保全

- [監査証拠JSON](<evidence/process-audit-2026-10-17.json>)は既存15 legsの抜粋。元ファイルのfull SHA256、artifact、flags、camera、verdictを残す。
  旧resize/reload/iconifyの一部はstdout summaryしか残っていない。その不足を明示し、元JSON/画像を復元したとは主張しない。
- E2E/RLCraftの当該terrain gateの両GPU成功は既存process/fixtureから確認できたため維持した。
  完全Modpack互換、全scene、最新HEADの再受入に拡張していない。
- 前回監査のplan分割は、`origin/main`旧本文の**非空行がすべて2つのarchiveに残る**ことを機械確認した。
  旧planは885,066 bytes / 1,053 lines。「202本=34%」は前回記録内の算術矛盾で、202/1055は約19%。
  旧監査の「すべて実測」「1画面」「337個すべてswitch」は保証として採用しない。
- 前回pruneで既に消えた資料は本監査では復元できていない。今回、実runに対する削除を行っていない。

## 検査

**最終ローカル結果: 新規51件 + 既存lock/isolation 13件 = 計64件、すべて成功（exit 0）。** 文書契約、索引鮮度、shell構文、diff whitespace、既存artifactのversion契約も成功。初回の索引テストは期待行番号の1行ずれで失敗し、API呼出し行を期待するよう訂正して全再実行した。
テストは模擬harness・一時ディレクトリを使用し、Minecraft/実GPUを起動していない。E2Eのsource-instance側のrenderer除外と、runnerのprovenanceをprunerが実際に消費する結合テストも含む。

繰り返し読み込む文書の実サイズは [AGENTS.md](<../AGENTS.md>) **5,833 bytes**、[goal.md](<goal.md>) **4,881 bytes**（旧27,016）、[plan.md](<plan.md>) **7,066 bytes**（旧885,066）。索引は網羅範囲を修正したため大きくなったが、必要なキーだけ検索する資料であり毎turnの必読にはしない。

```sh
python3 -m unittest discover -s scripts/tests -p 'test_*.py'
python3 scripts/cpu-contract/shared_game_slot_check.py
python3 scripts/cpu-contract/isolated_game_directory_check.py
python3 scripts/check_agent_docs.py
python3 scripts/instrument-index.py --check
bash -n scripts/gv-leg.sh
git diff --check
```

## 制限・残課題

- 本体のバグを今回修正したとは主張しない。Intel resize、loading、性能、雲等の次の実装は利用者の再開指示後。
- 索引はlexicalな探索補助。計算キー・未知API・runtime-only文字列の完全網羅や意味の保証はしない。文書checkerも歴史の全断定を検証するものではない。
- process treeをSIGKILLされた場合やOS/native crash後の全後始末は保証できない。自分のjobだけを確認する。
- 他の大きな設計書は今回機械的に分割しない。正典入口から必要な節だけ読み、別の現在状態表を増やさない。
- 利用者のgroupchat希望に対し共有ルーム作成を試みたが、DSHのrecord key `session-…|lead` がpath-safe検査で拒否された。
  参加者0の空roomを閉じた。groupchat skillはcatalogになく、共有会話が成立したふりはしていない。DSH本体/DBの改変・再起動はしていない。
- PR #314は既存の変更用。新しいdocs-only PRや自動マージは行わない。統合前に全差分と軽量検査をレビューする。
