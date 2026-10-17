# 検証で踏んだ罠（症状 → 実例 → 規約）

`AGENTS.md` の短い規則集を補う詳細版。**同じ穴に落ちた実例つき**で書く。
新しい罠を踏んだら、症状・実例・規約の3点でここへ追記する（round 日記ではなくここ）。

---

## 1. 沈黙を結論にしない（0 行はまず計器のゲートを疑う）

**症状**: 計器が 0 行 ⇒ 「描かれていない」「呼ばれていない」と結論してしまう。

**実例（すべてこのセッションで実際に踏んだ）**
- `CELTIUM_GROUP_BOUNDS` が 0 行 → 原因は計器側。`groupBoundsGuiOnly` は **HUD セクション限定**で、画面の quad は対象外だった。
- `CELTIUM_DRAW_SITE` が 0 行 → `enqFormat=7` が **enqueue 行まで絞る**ため、draw-site のゲートも閉じていた。専用 switch（`drawSite` / `drawSiteFormat`）を足して解決。
- さらに 0 行 → `drawSiteMinFrame=800` に対し、その leg の frame 番号は最大 **774** だった（tick と frame は別物）。
- `celtium.probe.texture=37` の 8 行上限が **main menu 中に消費**され、目当ての videoSettings 段が出なかった（caller で dedup して解決）。

**規約**: 0 行を見たら、①その計器の有効条件（switch・format・frame/tick の下限・行数上限）をソースで読み、②上限を上げるか別 switch で回し、③それでも 0 なら「来ていない」と結論する。**「0 行＝無い」と書かない。**

---

## 2. A/B は同一 run 内に対照を置く（別 run 比較は交絡する）

**症状**: 2 つの leg を比べて「A が原因」と結論する。

**実例**
- 雲: `mirrorPoisonFlat=35` の腕と素の腕を**別 run**で比較 → 雲は時間とともに動くので差の帰属不能。
- パノラマ: `GuiMainMenu` の回転は `panoramaTimer += partialTicks` で進むため、leg ごとに角度が違う。画素比較は**同一角度でなければ無意味**。
- tooltip: 「説明文が出た leg」と「出ていない leg」を比べて「修正で文字が出るようになった」と誤って結論（R27）。実際は**tooltip が出るかどうかの差**だった（R29 で訂正）。

**規約**: 対照は**同じ leg の中**に置く（switch 1つ違い・同じ jar・同じ tick）。それが作れないなら、**位相を固定する fixture ノブを先に作る**。別 run の画像比較は、時間依存が無いと**証明できた**ときだけ使う。

---

## 3. poison の色は AARRGGBB（色指定を読み違えると無意味な測定になる）

**症状**: 「poison しても magenta にならない ⇒ その texture をサンプルしていない」と結論。

**実例**: `mirrorPoisonFlat=35=FF00FFFF` は **A=FF, R=00, G=FF, B=FF ＝ cyan**。magenta は `FFFF00FF`（R21 の実績値）。R38 の雲の結論はこの取り違えで無効になった。

**規約**: poison を張ったら**同じ leg で `mirrorDump` を取り、ダンプの平均色で適用を確認**してから解釈する（R39 で確立）。

---

## 4. 検出器を厳しくしすぎない（混色を見逃す）

**症状**: 「magenta 画素 0」で「サンプルしていない」と結論。

**実例**: 雲は `getCloudColour` と alpha 0.8 で**混色**されるため、`R>150 & G<110 & B>150` の判定では 1 画素も拾えない。R39 では **near-white 率の変化（1.064% → 0.334%）** でサンプルを証明できた。

**規約**: 単色判定ではなく、**同じ領域の統計（平均・特定クラスの画素率）の変化**で判定する。

---

## 5. fixture 税を外してから性能を語る

**症状**: frame interval が 500〜1000 ms あるので「レンダラが遅い」と結論。

**実例**: 952 ms の frame interval でも **render ブラケットは 18.5 ms** だった（残りは client tick と **fixture 自身のダンプ/キャプチャ**）。`-Dceltium.validation.presentedDumpEvery=0` で外すと p50 34.2 ms（cohort）。

**規約**: 性能の主張は `presentedDumpEvery=0` の leg で取り、**`passes[...]`（`CELTIUM_DRAW_SITE` / slow-frame 記録）でレンダーブラケットと外側を分けて**書く。

---

## 6. 既存の計器を先に読む（同じ情報を二度作らない）

**実例**
- GPU timestamp は**既に実装済み**で、消費先（F3 debug 行）も既存 → fixture の `rendererDebug` にそのまま出ていた（地形 GPU ≈2.6 ms）。新しい計器は不要だった。
- 逆に、multi-draw batch cache の hits/misses は**既存に無かった**ので追加した（R37）。

**規約**: 計器を足す前に `docs/instruments.md` を grep する（`grep <用途のキーワード> docs/instruments.md`）。足したら索引を再生成して同じ commit に含める。

---

## 7. 道具・手順まわりの罠（時間を溶かす順に）

| 症状 | 実例 | 規約 |
|---|---|---|
| build が落ちる | `ClosedFileSystemException` が散発（remap タスク） | **同じコマンドを再実行**。1回の失敗で設計を疑わない |
| 編集が反映されない | python で一括編集 → `assert` 失敗時は**最後の write に到達せず**ファイル未変更 | 編集後は必ず `grep` で確認してから build |
| 自分の shell が死ぬ | `pgrep`/`pkill` のパターンが**自分のコマンドラインに一致**（2回踏んだ） | kill は**スクリプトファイル経由**（`/tmp/killgame.py` 等）。パターン文字列をコマンドラインに置かない |
| 古い jar で leg を回す | build 失敗の直後に leg を起動 → 前の jar を検証 | leg 前に **sha256 を記録**し、jar 名だけで判断しない |
| leg が沈黙 | 二重の celtium jar（`celtium-*pre-stub-changes.jar`）が instance mods に残っていた | leg 前に `<run>/mods/` の celtium jar を数える |
| mixin が効かない | overloaded target は descriptor 必須／plugin の `shouldApplyMixin` は常に false（走査で登録） | 原典で descriptor を確認。config 経由の登録は当てにしない |
| `git checkout main` が失敗 | worktree が main を使用中 | `git diff origin/main --name-only` / detached で比較 |
| 環境の取り残し | java プロセス・`.celtium-work/gpu.lock` | round 終了時に確認して残さない |

---

## 8. 主張の書き方（証拠の形）

- GPU の主張には **jar hash・GPU 名・scene・camera・flags・leg 名**を添える。
- **Intel** は `CELTIUM_NATIVE_DEVICE name="Intel(R) UHD Graphics 630 (CFL GT2)"` と
  `gpuRequirementMet=true` の**両方**で確定（render node だけでは選ばれない。`DRI_PRIME=pci-0000_00_02_0!`）。
- `validationClean=true` は成功ではない。**verdict の欠落は成功ではない。**
- 較正済み scene の床を割ったら**それは証拠**。上書きする前に仮説を分離する実験を取る。
- 棄却した仮説は「棄却」と書いて残す（再探索の防止）。**再探索しない一覧は `docs/plan.md` にある。**
