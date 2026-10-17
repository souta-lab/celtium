# Celtium — how to work in this repository

このファイルは、ここを変更する人（人間・エージェント）向けの**規則集**。規則だけを置く。
長い実例・失敗談は `docs/verification-traps.md` にあり、各規則から参照する。

## まず読む順序（これで迷子を防ぐ）

| 目的 | 読む場所 |
|---|---|
| **現在の状態・優先順・受入**（唯一の状態表） | `docs/plan.md`（短い。ここが現在判定） |
| 計器（`-Dceltium.*`）を探す | `docs/instruments.md`（`grep <名前>` で実装位置へ） |
| 検証の罠・失敗例 | `docs/verification-traps.md` |
| 経過・棄却した仮説・途中の計測 | `docs/history/plan-diary.md`（追記専用） |
| 過去の監査・受入 | `docs/history/`, `docs/audit-*.md`（**当時の記録。現在判定ではない**） |
| 目的 | `docs/goal.md` ／ 配布・対応 | `README.md`, `docs/mod-compatibility.md` |

**`docs/plan.md` を太らせない。** round の経過は diary へ書く。plan.md は「実装・根因・受入の意味が
変わったとき」だけ更新する。2026-10-17 より前、plan.md は 886 KB の日記になり状態表として読めなくなっていた。

## 決める前に原典を読む

build は**デコンパイル済みの Minecraft/Forge ソース**と SRG 表を生成する。署名・描画モード・
vertex format・呼び出し順を推測で議論しない。

```sh
scripts/mc-sources.sh                       # ソースと名前表のパス
scripts/mc-sources.sh --grep 'begin(7, DefaultVertexFormats'   # 検索
scripts/mc-sources.sh --srg func_181679_a   # deobf <-> SRG の双方向
```

mixin のターゲット、format id、描画モード、shader 入力、順序の主張は**先にそこを見る**。
`javap` はソースで覆えないクラスの fallback。**同じ規則はこの repo 自身の計器にも適用**:
既存の counter / dump / report を先に読み（`docs/instruments.md`）、足したら索引を更新する。

## 失敗したゲートは証拠

較正済み scene の床を割った verdict は、測定が誤りだと言うまで**証拠**。説明を書き換えて隠さない。
ゲートが誤動作していると思うなら、**競合仮説を分離する実験**（同じ fixture と同じカメラで jar 2つ、
または同じ jar でカメラ 2つ）を取ってから上書きする。どの説明が棄却されたかを書く。

## 「検証済み」の定義

- GPU の主張には **jar hash・GPU・scene・camera・flags** を添える。
- `validationClean=true` は成功ではない。**verdict の欠落は成功ではない。**
- **拒否は fallback ではない**: GL-less では拒否された batch は誰も描かない。
- **counter が無いことは draw が無いことではない。対照が無いことは一致ではない。**
- 受入は実 Cleanroom 0.6.13-alpha instance を `scripts/prism_validation.py` で通す。
  `runClient` は stock-Forge の開発クライアントで、探索用。**受入には使わない。**
- ゲーム instance は同時に 1 つ:
  `for p in $(pgrep -x java); do tr '\0' '\n' < /proc/$p/cmdline | grep -q foundation.boot.Foundation && echo busy; done`

## 計器の読み方（この4つは毎回効く）

1. **0 行を結論にしない。** まず計器のゲート（switch・format・frame/tick 下限・行数上限）を疑う。
2. **A/B は同一 run 内に対照を置く。** 別 run の画像比較は、時間依存が無いと証明できたときだけ。
3. **poison の色は AARRGGBB。** 張ったら同じ leg の `mirrorDump` で適用を確認してから解釈する。
4. **性能は fixture 税を外してから**（`-Dceltium.validation.presentedDumpEvery=0`）。
   レンダーブラケットとその外側を分けて書く（`passes[...]`）。

## Intel の leg は device 行で確定する

`--require-gpu` を付けても Intel とは限らない。**`CELTIUM_NATIVE_DEVICE name="Intel(R) UHD Graphics 630 (CFL GT2)"`
と `gpuRequirementMet=true` の両方**を読む。render node は compositor の GPU を選ぶだけなので、
Mesa の device 選択を使う（`DRI_PRIME=pci-0000_00_02_0!`）。**AMD の成功は Intel の成功ではない。**

## 画像の無い leg は、あなたの build を走らせたとは限らない

instance の `minecraft/mods/` に別の celtium jar があると、**その leg は2つの celtium で走る**。
読む前に `<run>/mods/` の celtium jar を数え、テスト対象でないものは `--exclude-instance-mod` で外す。

## overloaded な mixin ターゲットは descriptor を書く

同名が2つあるメソッドに `@Inject(method = "updateRenderInfo")` を書くと `InvalidInjectionException` になり、
**ターゲットクラスの定義ごと落ちて** `NoClassDefFoundError` で死ぬ。`require = 0` も `remap = false` も効かない。
descriptor を綴る: `@Inject(method = "updateRenderInfo(Lnet/minecraft/entity/Entity;Z)V", ...)`。
runtime jar（`forge122/build/rfg/recompiled_minecraft-1.12.2.jar`）は **MCP 名**なので、
「runtime でこの名前か」はその jar への `javap` で答える。

## round は step であって PR ではない

- **1 round = 1 実装単位の1歩。** 同じ branch・同じ未完 TODO・同じ担当を必要なだけ持ち越す。round ごとに branch を切らない。
- **PR はレビュー可能な単位がまとまったとき**だけ開く。直前に `git diff origin/main --name-only` を見て、
  `docs/plan.md` しか無ければ単位はまだ無い。findings は diary か既存 PR/Issue に書く。
  例外は**利用者が明示的に依頼した文書**と、ライセンス・安全・配布のために独立して必要な変更。
- 成果は**レンダラ**であって、PR 数・round 数・docs の行数ではない。

## 環境の後始末

round の終わりに java プロセスと `.celtium-work/gpu.lock` を残さない。
leg の成果物は `.gv-verify/run-<leg>/`（`validation-process.json`・`validation-console.log`・`screenshots/`）。
leg の回し方は `docs/instruments.md` の末尾、または `scripts/gv-leg.sh --help` 相当のコメント。
