# 開発プロセス監査（2026-10-17）

**目的**: レンダラの実装ではなく、**開発が円滑に回る状態**にする。指示系統・docs・計器・skill・
冗長・時間/トークンの浪費・ハルシネーション・thinking の崩壊を、**測ってから**直す。
（利用者指示: 「開発を円滑にすることだけをやってください」）

## 所見（すべて実測）

| # | 症状 | 実測 | 影響 |
|---|---|---|---|
| 1 | **「唯一の現在状態表」が読めない** | `docs/plan.md` が 1055 行 / **886 KB**。1000 バイト超の行が **202 本（34%）**、行長の中央値 399 バイト。内容の大半は round 日記 | 指示「冒頭表を唯一の現在状態表とする」が**実行不能**。エージェントは毎回 grep で断片を拾い、状態を取り違える |
| 2 | 毎ターン自動ロードされる規約が長い | `AGENTS.md` 10 KB（167 行） | 全ターンの固定費。規則と失敗談が混在 |
| 3 | **計器が多すぎて索引が無い** | `-Dceltium.*` / `-Dceleritas.*` スイッチ **337 個**、`CELTIUM_*` マーカー 34 個。一覧は存在しない | 「どの計器がどこにあり何を出すか」を毎回ソース全体の grep で探索 |
| 4 | **受入ハーネスが git 管理外** | leg ランナーは `.gv-verify/gv-leg.sh`（未追跡・絶対パス固定）。さらに `scripts/prism_validation.py`（追跡・682 行）と `.gv-verify/prism_validation.py`（未追跡・**631 行**）が**別物**で、leg は**古い方**を走らせていた | worktree を失えば受入手順ごと消える。受入が「どのハーネスで取ったか」を再現できない |
| 5 | **run 成果物が 13 GB・未追跡** | `.gv-verify/` 13 GB（run 90 本）。1 本の大半は `mods/`（411 MB のパックコピー）・`saves/`・`config/` 等で**証拠ではない** | `git status` のノイズ、ディスク枯渇（実際に 100% で leg が落ちた） |
| 6 | 壊れた参照 | 9 つの docs が「plan §0.xxx」を参照。`§` の**定義側**は `docs/handoff-2026-10-10-next-model.md` と `docs/history/` にあり、**plan.md には無い**（今回の変更以前から） | 参照を辿ったエージェントが空振りし、推測で埋める |
| 7 | 検証の罠が散在 | 0 行を結論にする・別 run 比較・poison の色指定・混色を見逃す検出器・fixture 税 などが、AGENTS.md と各 round の記述に分散 | **同じ穴に繰り返し落ちる**（今回のセッションで実際に 5 回以上） |

## 実施した修正

| 症状 | 修正 | 効果（実測） |
|---|---|---|
| #1 | `docs/plan.md` を**現在状態だけ**に書き直し、旧内容を無損失で退避: `docs/history/plan-state-snapshot-2026-10-17.md`（旧 現在状態節）と `docs/history/plan-diary.md`（旧 diary・**今後の追記先**） | **886 KB → 10.1 KB（1/88）**。ヘッダ表・受入・未解決・性能数値・利用者報告6項目の判定が 1 画面で読める |
| #2 | `AGENTS.md` を**規則集**に圧縮し、長い失敗談は `docs/verification-traps.md` へ。読む順序の表と「計器の読み方 4 か条」を追加 | **10 KB → 6.1 KB**（毎ターンの固定費を削減） |
| #3 | `scripts/instrument-index.py` を追加し、`docs/instruments.md` を生成（337 スイッチ＋34 マーカーを名前空間ごとに、`file:line` つき） | `grep <名前> docs/instruments.md` で実装位置へ直行。`--check` で CI にも使える |
| #4 | `scripts/gv-leg.sh` として**可搬化して追跡**（`ROOT` は script 位置から導出・instance は環境変数で差し替え可・**正典ハーネス `scripts/prism_validation.py` を使用**）。古い `.gv-verify/prism_validation.py` を使わない旨を plan.md とスクリプトに明記 | 受入手順が repo に入り、どのハーネスで走ったかを sha で記録できる |
| #5 | `.gitignore` に `.gv-verify/`。`scripts/gv-prune.py` を追加（既定 dry-run、`--apply` で実行） | **13 GB → 1.6 GB（11.1 GB 解放）**。証拠（`validation-process.json`・`logs/celtium-validation.json`・`celtium-vulkan-device.json`・`celtium-capabilities.json`・`cleanmix.log`・`screenshots/`）は保持 |
| #6 | `docs/goal.md` の壊れた「plan §」参照を修正し、`docs/plan.md` に**旧 § 番号の所在**を明記 | 参照が解決する。今後「plan §…」を書かない |
| #7 | `docs/verification-traps.md`（症状 → 実例 → 規約の 8 節）を新設。`AGENTS.md` に「計器の読み方 4 か条」を明文化 | 同じ穴に落ちる確率を下げる（0 行＝ゲートを疑う／対照は同一 run／poison は AARRGGBB＋ダンプ確認／性能は fixture 税を外す） |
| — | skill `celtium-verify` を `~/.agents/skills/` に追加（leg 手順・証拠 4 点セット・画像 A/B の約束・後始末） | 検証の手順と罠を、必要なときに 1 枚で読める |

## この監査自体の検証

- `docs/plan.md` を読む CI 契約があるため実行: `python3 scripts/verify_version_consistency.py` →
  `the plan's header names the artifact's release … ['0.0.4-alpha']`・**`verified: True`（rc=0）**。
- prune は **dry-run で「証拠が削除予定に出ない」ことを確認**してから `--apply`（`celtium-validation.json` /
  `celtium-vulkan-device.json` / `celtium-capabilities.json` / `cleanmix.log` の削除予定は 0 件）。
- `scripts/gv-leg.sh` は `bash -n` で構文確認。
- すべての変更は `git diff origin/main --name-only` で追える。

## 残る推奨（今回は未実施・理由つき）

1. **他の巨大 docs の分割**: `docs/diagnosis-r213-user-visual-breakage.md` 198 KB、
   `docs/vanilla-acceptance.md` 143 KB、`docs/performance_plan.md` 109 KB、`docs/history/plan-diary.md` 749 KB。
   今回は「毎回読まれる」plan.md を優先した。performance_plan / vanilla-acceptance も
   「現状＋履歴」に分ける価値がある（内容の意味を変えない分割なので、次回まとめて）。
2. **`docs/goal.md`（26 KB）**: 歴史節が大半。objective と完成条件を前半に固定し、履歴を分離する候補。
3. **計器 337 個の棚卸し**: 実際に leg で使うのは数十。使われていない計器を落とすか、
   「受入で使う計器」を `docs/instruments.md` の先頭に列挙する。
4. **PR の粒度**: このセッションは 13 PR を出した。`AGENTS.md` の規約（unit がまとまってから）に対し、
   実態は round 寄りだった。規約は維持しつつ、計器のみの PR は「その計器で何が分かったか」を
   本文に必須にする（既に概ねそうなっている）。
5. **古いハーネスの削除**: `.gv-verify/prism_validation.py`（631 行）は誤用の元。
   削除するか、冒頭に「古い。正典は scripts/ 側」と明記する。

## 教訓（次に同じことをしないために）

- **「唯一の◯◯」と書くなら、それは人間が 1 画面で読める大きさでなければならない。**
  886 KB の「唯一の状態表」は存在しないのと同じ。
- **規約は自動ロードされるファイルに、詳細は必要なときだけ読むファイルに。** 混ぜると両方損する。
- **計器は名前と位置の索引と一緒に増やす。** 索引の無い 337 個は、無いより悪い（探索で時間を溶かす）。
- **受入の手順とハーネスは repo に入れる。** 未追跡の手順は再現できない。
- **証拠とゴミを分けてから消す。** `logs/` の中にこそ一次証拠（device 行・capabilities・mixin 記録）があった。
