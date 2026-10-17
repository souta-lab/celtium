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

**規約**: 0行なら有効条件（switch・format・frame/tick下限・行数上限・出力先）を読み、計器が有効な陽性対照を確認する。上限を変えても0だった、だけで呼出し不存在を証明したことにはならない。不足が残れば「未観測」と書く。

---

## 2. A/Bの固定条件と交絡を明示する

**症状**: 2 つの leg を比べて「A が原因」と結論する。

**実例**
- 雲: `mirrorPoisonFlat=35` の腕と素の腕を**別 run**で比較 → 雲は時間とともに動くので差の帰属不能。
- パノラマ: `GuiMainMenu` の回転は `panoramaTimer += partialTicks` で進むため、leg ごとに角度が違う。画素比較は**同一角度でなければ無意味**。
- tooltip: 「説明文が出た leg」と「出ていない leg」を比べて「修正で文字が出るようになった」と誤って結論（R27）。実際は**tooltip が出るかどうかの差**だった（R29 で訂正）。

**規約**: 同一run内対照が可能なら優先する。別JARを比べる場合は別runでよいが、scene・camera・時間/天候・panorama位相・GUI/tooltip・解像度等を揃え、残る交絡とノイズを明示する。「必ずfixtureノブを増設」ではなく、既存の固定条件・反復・記録で判別可能かを先に調べる。

---

## 3. poison の色は AARRGGBB（色指定を読み違えると無意味な測定になる）

**症状**: 「poison しても magenta にならない ⇒ その texture をサンプルしていない」と結論。

**実例**: `mirrorPoisonFlat=35=FF00FFFF` は **A=FF, R=00, G=FF, B=FF ＝ cyan**。magenta は `FFFF00FF`（R21 の実績値）。R38 の雲の結論はこの取り違えで無効になった。

**規約**: poison を張ったら**同じ leg で `mirrorDump` を取り、ダンプの平均色で適用を確認**してから解釈する（R39 で確立）。

---

## 4. 検出器を厳しくしすぎない（混色を見逃す）

**症状**: 「magenta 画素 0」で「サンプルしていない」と結論。

**過去記録**: 雲は色・alphaで混色され、`R>150 & G<110 & B>150`では拾えなかった。R39にはnear-white率1.064%→0.334%という記録がある。ただし領域統計の変化だけでは、samplingや最終画像一致を単独で証明しない。

**規約**: 単色判定に加え、適用dump・対応draw・固定した領域の差・対照を確認する。変化があることと、その原因の特定を区別する。

---

## 5. fixture 税を外してから性能を語る

**症状**: frame interval が 500〜1000 ms あるので「レンダラが遅い」と結論。

**訂正**: 旧手順の`celtium.validation.presentedDumpEvery=0`は存在しないキーだった。旧`gv-notax-*`のeffective argsにも誤指定があり、p50 34.2msを「dump税除去後」とする説明は撤回する。生の観測値まで偽だったという意味ではない。

**規約**: 周期dump停止は **`-Dceltium.vulkan.presentedDumpEvery=0`**、frameDumpDir経路は別の **`-Dceltium.vulkan.frameDumpEvery=0`**。要求capture/readback/validation等はなお残る。全frame intervalとrender bracketの測定区間を揃え、外側を自動的に「mods/fixture由来」と帰属させない。手順は [development.md](<development.md>)。

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
| buildが落ちる | remapで`ClosedFileSystemException`の旧記録 | 同じ一過性例外を確認した場合だけ1回再試行。反復/別例外ならログを保全し調べる |
| 編集が反映されない | python で一括編集 → `assert` 失敗時は**最後の write に到達せず**ファイル未変更 | 編集後は必ず `grep` で確認してから build |
| 自分のshellが死ぬ | `pgrep`/`pkill`のパターンが自分にも一致した旧記録 | 管理job/PIDと所有process groupを使う。スクリプト経由でも無差別killは安全にならない |
| 古いjarで検証 | build失敗後に旧jarが残る | build成功・今回の出力・full SHA256を紐づける。hashだけでもHEADからのbuildは証明しない |
| leg が沈黙 | 二重の celtium jar（`celtium-*pre-stub-changes.jar`）が instance mods に残っていた | leg 前に `<run>/mods/` の celtium jar を数える |
| mixin が効かない | overloaded target は descriptor 必須／plugin の `shouldApplyMixin` は常に false（走査で登録） | 原典で descriptor を確認。config 経由の登録は当てにしない |
| `git checkout main` が失敗 | worktree が main を使用中 | `git diff origin/main --name-only` / detached で比較 |
| 環境の取り残し | java/lockを所有者不明のまま削除 | 自分のjobを回収・停止し、その終了を確認する。他人のjavaやgame-slot所有者を消さない |

---

## 8. 主張の書き方（証拠の形）

- GPU の主張には **jar hash・GPU 名・scene・camera・flags・leg 名**を添える。
- **Intel** は `CELTIUM_NATIVE_DEVICE name="Intel(R) UHD Graphics 630 (CFL GT2)"` と
  `gpuRequirementMet=true` の**両方**で確定（render node だけでは選ばれない。`DRI_PRIME=pci-0000_00_02_0!`）。
- `validationClean=true` は成功ではない。**verdict の欠落は成功ではない。**
- 較正済み scene の床を割ったら**それは証拠**。上書きする前に仮説を分離する実験を取る。
- 棄却仮説には対象artifact・scene・前提と、再調査が必要になる条件を残す。過去の局所否定を永久の探索禁止にしない。
- 指示・観測・推論を混ぜない。歴史資料にある「最終指示」「現在」「確定」を現行の命令/受入へ昇格させない。
- 同じ失敗の再試行が続いたら、成功条件・不足証拠・次の判別条件を短く残す。印や自己評価の反復ではなく、検証可能な次の操作を選ぶ。
