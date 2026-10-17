# Celtium 現状表（現在状態・優先順・受入）

**版**: 開発ベース **0.0.4-alpha**（`build-1122/build.gradle.kts` の既定値・`-Pceltium_release_version` で上書き）。
公開済み最新は **v0.0.5-alpha**（pre-release・2026-10-15）、非 pre-release では v0.0.3-alpha が最新。
**統合基点**: `origin/main = c6cc18222`（PR #313 まで統合済み）。

## このファイルの使い方（重要）

- **この表が現在状態の唯一の場所。** 実装・根因・受入の意味が変わったときだけ更新する。
- **round の経過・棄却した仮説・途中の計測はここに書かない。** `docs/history/plan-diary.md` へ追記する。
  （2026-10-17 より前は plan.md 自体が 886 KB の日記になり、この表が読めなくなっていた。旧内容は
  `docs/history/plan-state-snapshot-2026-10-17.md` と `docs/history/plan-diary.md` に保存済み。）
- 計器（`-Dceltium.*`）の一覧は `docs/instruments.md`、検証で踏んだ罠は `docs/verification-traps.md`。
- 過去の監査・受入は `docs/history/` と `docs/audit-*.md`。**それらは当時の記録であり現在判定ではない。**

## 現在状態（2026-10-17）

作業ブランチ: 単位ごとに切る（経過は `docs/history/plan-diary.md`）。worktree の未追跡は `.gv-verify/` のみ。

### 受入済み（両GPU実測・leg 名が根拠）

| 項目 | 状態 | 根拠（leg / PR） |
|---|---|---|
| GL-less 純ネイティブ Vulkan で提示（GL context なし・bridge/fallback なし） | **AMD ✓／Intel ✓** | `gv-cohort-*` 系・`CELTIUM_GLLESS` 行・`CELTIUM_NATIVE_DEVICE` |
| 主要 scene（calibrated terrain） | **両GPU PASSED ✓** | `gv-cohort-r30-box2` ほか（`validationClean=true`） |
| resize / reload / minimize | **両GPU PASSED ✓** | `gv-cohort-resize2/3`・`gv-cohort-reload2/3`・`gv-cohort-iconify1` |
| **E2E 1.93a（268 mod）world 画像ゲート** | **両GPU PASSED ✓✓** | `gv-e2e-268-r16-fbo`（AMD, `worldFrameBlackFraction` 0.0051）／`gv-e2e-268-r16-intel`（Intel, 0.0038） |
| **RLCraft 2.9.3（165 mod）terrain** | **両GPU PASSED ✓✓** | `gv-recipe-r14`（AMD p50 30.55 ms）／`gv-recipe-r14-intel` |
| display list 意味論（mob・player model が画像に出る） | **両GPU ✓** | `gv-cohort-*` の tick200 画像（PR #288 系） |
| Sodium 風メニューの半透明下敷き（利用者 #3） | **修正済み ✓✓** | PR #307・`docs/evidence/sodium-tooltip-backdrop-2026-10-16.png`・両GPU `validationClean` |
| 雲の描画（利用者 #4） | **欠陥なしと確定 ✓** | 下記「雲についての確定」 |

### 未解決（優先順）

| 優先 | 項目 | 状態 | 次の一手 |
|---|---|---|---|
| **P0** | ロード画面の進捗バー/テキストが出ない・リサイズに追従しない（利用者 #1/#5） | ✗ 未測定（fixture の `early`(worldTick 20) と `loading` は**どちらも world 生成後**に撮るため、world ロード画面を一度も撮れていない） | fixture に **client tick 基準の presented dump** を足し、`GuiDownloadTerrain`/`LoadingScreenRenderer` の frame を撮る |
| **P0** | p99 スパイク（利用者 #6） | ✗ 帰属済み: 遅い frame の時間は**レンダーブラケット外**（client tick ＋ fixture 税）。レンダラ定常は frame p50 **15.9 ms** | 下記「性能」の次の実装 |
| **P1** | 地形 pass の CPU record（`recordRenderPass` p50 6.5 ms・worst 88.3 ms） | ✗ 対象は一点に確定（submit 0.5 ms・GPU 2.6 ms ではない） | `recordRenderPass` の内側を同じ手法で分割（assembler 再構築 vs コマンド記録） |
| **P1** | `setupTerrain` 39.7 ms / `particles` 32.9 ms のスパイク | ✗ 計器で名前は判明 | 可視性トラバーサルと particle 経路の内訳 |
| **P1** | パノラマが重い・blur が強い（利用者 #2） | ✗ コピーは正しく走っている（6面＋5回/frame）が重い。**画像 A/B は `panoramaTimer` の位相で交絡** | パノラマ角度を固定する fixture ノブを入れてから A/B |
| **P2** | `texture=506` の `no mirror for this GL id` 拒否（44〜89/leg） | ✗ 生成経路（renderbuffer 添付・`glTexStorage2D`・非 vanilla FBO）が未特定 | 記録済み attachment と照合し producer を名指し |
| **P2** | world-switch（Nether）の残り | レンダラ層は GREEN ✓／✗ テスト機の keepalive 制約（地形生成が 30 秒に間に合わない） | より速いホスト、または render distance をさらに下げる |
| **P3** | Cleanroom 0.7.x（SDL3/Kirino） | 未検証の基準 ✗ | 0.6.13-alpha を基準に維持し、別 unit で |

### 利用者報告（2026-10-16・6項目）の現在判定

| # | 報告 | 判定 |
|---|---|---|
| 1 | ロード画面がロゴのみ・リサイズ追従なし | ✗ 未解決（上表 P0） |
| 2 | パノラマが重い・blur が強い | ✗ 未解決（A/B の交絡を先に外す） |
| 3 | ビデオ設定の背景が無い／文字が重なる | **✓ 解消**（PR #307。重なりは下敷き欠落の副作用だった） |
| 4 | 雲が無い空間がある | **✓ 欠陥なしと確定**（本人も「仕様の可能性」と注記） |
| 5 | 進捗がおかしい | ✗ #1 と同じ |
| 6 | FPS が下がっている | ✗ 帰属済み・最適化進行中（下記「性能」） |

## 性能（実測・出典つき）

- **fixture 税を外した frame interval（`presentedDumpEvery=0`）**: cohort warm p50 **34.2 ms**・p95 58.1・p99 428 ✗／videoSettings warm p50 **47.7 ms** ✗（leg `gv-notax-r33-*`）。
- **レンダーブラケット内訳（`gv-notax-r34-brack`・512 frames・validationClean）**:
  `frame[p50=15.9]` / `world[p50=13.9]` / **`terrain` p50 8.2**（worst 17.8）/ `flush` 3.2 / `gui` 1.6 / `particles` 1.1 / `setupTerrain` 1.4 / `sky` 1.0 / `clouds` 0.7 / `hand` 0.5。
- **地形 pass の内訳（`gv-notax-r36-phase`）**: `terrainRecord` p50 **6.5 ms**（p95 16.5・worst **88.3**）/ `terrainSubmit` 0.5 / `terrainHandoff` 0.0。
- **GPU は既存計器に出ている**（`rendererDebug` の F3 行）: 地形3パス合計 **≈2.6 ms** ⇒ **地形の律速は CPU 側**。
- **tick**: `gameTickMillisTotal` から約 13 ms/tick（cohort の 14 mod 由来。Celtium に per-tick フックは無い）。
- **棄却済み（再探索しない）**: multi-draw batch cache のスロット数 ✗（hit 25→27% で不変）、`VulkanMultiDrawBatch.delete()` の churn ✗（**delete は no-op**・GPU バッファを持たない）。
- 参考: タイトル画面の copy は host readback に落ちていた（23 ms/copy・計 7.4 s ✗）。`-Dceltium.vulkan.windowCopyBlit=true` で readback 0 になるが **p50 は動かない**（52.3 ↔ 56.1 ms）ため既定 off。絵の検証が済むまで既定にしない。

## 雲についての確定（再探索しない）

`renderCloudsFancy`（原典）は **8×8 セル**・UV は `(cell*8 + 0..8) * 0.00390625 + drift`（セル 0.03125・全体 0.25）・色は `color(f9,f10,f11, 0.8F)`。
我々の batch 実測は `count=5104`・`stride=24`（POSITION_TEX_COLOR）・`otherArrays=GL_COLOR_ARRAY`・**`col0..col3=[b2b2b2cc]`（alpha 0.8 ＝ 原典一致）**・UV スパン 0.172（batch の先頭/末尾は極値ではないので 0.25 と矛盾しない）・sampler `repeatU/V=true mag=NEAREST maxLod=0`・mirror は原画と画素一致。
⇒ **幾何・色・alpha・UV 進行・sampler・テクスチャすべて vanilla と同一構成**。利用者 #4 の「雲が無い空間」は **テクスチャ自身が 80%（52,180/65,536 texel）alpha=1 の隙間**であることと整合する（vanilla も同じ）。

## 優先順（方針）

**A. 正しさ・安定性** → **B. FPS/frame time** → **C. 主要Mod互換（E2E 1.93a と RLCraft 2.9.3 が最優先）** → **D. 配布品質** → **E. 影・Shaderpack**。
A と B は同時に進める（高速化で壊れたら直すループにしない。各変更で較正済み scene の回帰を取る）。

## 検証の約束（詳細は `docs/verification-traps.md`）

- GPU の主張には **jar hash・GPU・scene・camera・flags** を添える。`validationClean=true` は成功ではない。
- **Intel は `CELTIUM_NATIVE_DEVICE name="Intel(R) UHD Graphics 630 (CFL GT2)"` と `gpuRequirementMet=true` の両方**で確定。
- **沈黙を結論にしない**（0 行は計器のゲートを先に疑う）。**A/B は同一 run 内に対照を置く**。
- 較正済み scene の床を割ったらそれは証拠。上書きする前に仮説を分離する実験を取る。

## 参照

- 受入 leg ランナー（**正典**）: [`scripts/gv-leg.sh`](../scripts/gv-leg.sh) — `gv-leg.sh <leg名> <jar-dir> <mode> <gpu> [秒] [追加 -D…]`
- 検証ハーネス（**正典**）: [`scripts/prism_validation.py`](../scripts/prism_validation.py)
  ※ `.gv-verify/prism_validation.py` は**古い分岐**（631行・`--copy-pack-dir` 無し）。使わない。
- 計器一覧: [`docs/instruments.md`](instruments.md)（生成: `scripts/instrument-index.py`）
- 検証の罠: [`docs/verification-traps.md`](verification-traps.md)
- run 成果物の整理: `python3 scripts/gv-prune.py`（既定 dry-run。証拠は残し再生成可能な部分だけ落とす）
- round 日記（追記専用）: [`docs/history/plan-diary.md`](history/plan-diary.md)
- 性能計画: [`docs/performance_plan.md`](performance_plan.md)／Mod 互換: [`docs/mod-compatibility.md`](mod-compatibility.md)
- 目的: [`docs/goal.md`](goal.md)／作業規約: [`AGENTS.md`](../AGENTS.md)／配布: [`README.md`](../README.md)

### 旧 `§0.xxx` 番号について

この plan.md に `§0.100`〜`§0.177` の番号は**無い**（2026-10-17 の再構成以前に消えている）。
`§` 番号の定義側は [`docs/handoff-2026-10-10-next-model.md`](handoff-2026-10-10-next-model.md)（`§0.100`〜）と
[`docs/history/`](history/) の各記録。**「plan §…」と書いてある参照は古い**（多くは 2026-10-11 以前のもの）。
当時の plan の全文は [`docs/history/plan-state-snapshot-2026-10-17.md`](history/plan-state-snapshot-2026-10-17.md)
と [`docs/history/plan-diary.md`](history/plan-diary.md) に保存してある。
