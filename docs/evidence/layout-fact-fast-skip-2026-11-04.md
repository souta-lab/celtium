# 共有イメージの layout 事実は台帳を読まずに skip を答えられる（2026-11-04、round 57）

`transitionForeignLayout` は「このイメージは既に目的の layout か」を台帳（`RECORDINGS` の 2 回の
map 参照＋クラスモニタ）に聞いていた。台帳の答えは、**そのイメージに未確定の遷移が無いなら共有事実
そのもの**である。`ForeignLayoutState.openPending` がその未確定数を数え、`celtium$layoutSettled` が
2 つのフィールド読みで答える。**近似ではなく厳密**で、未確定が 1 つでもあれば従来どおり台帳を読む。

## 1. ルール

- `openPending` は `noteForeignTransition`（新しい pending を作った時だけ +1）、
  `commitLayoutRecording`（適用した各 entry で −1）、`abandonLayoutRecording`（捨てた各 entry で −1）、
  そして `beginLayoutRecording` の**暗黙 abandon**（同じ handle で未確定のまま次の recording が開いた時）で維持する。
- `declareForeignImage` が新しい state を作る時は、開いている recording を走査して同じ handle の pending を数える。
  Vulkan の handle は破棄後に再利用され得るので、これをしないと「新しい state は openPending=0」なのに
  古い pending が残っている、という取り違えが起きる。初回宣言だけの費用。
- 高速路は `openPending == 0 && confirmed == wanted`。pending があれば「別の layout を要求した command buffer が
  あり、その submit が通ればそちらが勝つ」ので、台帳を読まないと正しくない。
- 計器は `foreignLayoutFastSkips` を分けて数える（`skips` は台帳が答えた skip、`fastSkips` は台帳を読まなかった skip）。

## 2. 効果は小さい（実測）

leg `r56a-merge`（既定 flags、1,456 world frames）の `foreignLayoutSummary`:

```
transitions=0 skips=37949 commits=1713 abandons=0 dropped=0 open=0 unbound=0 declaredMismatches=0
```

**37,949 / 1,456 = 26 skip/フレーム**。1 回あたり 2 map 参照＋2 モニタ（~60 ns）なので
**約 1.6 µs/フレーム**。**フレームは動かない。** この変更は「厳密で試験できる」から残すのであって、
性能の主張ではない。

`transitions=0` も記録しておく: この scene では published-name の layout handshake は**一度も barrier を
記録していない**（全部 skip）。walk の費用は 1 フレーム約 10 回 × 38 名の述語呼び出しであって、
台帳参照ではない。

## 3. 同じ round の paired KPI は **NO_SPEED_VERDICT**（外部負荷）

`scripts/gv-kpi.sh r57kpi 240`、`CELTIUM_KPI_REPS=3`、stage `r57a-stage`
（renderer `051a03dd…`、fixture `0e90c988…` 不変）、参照 Celeritas `567dcf8a…`。

| rep | Celtium p50 (ms) | Celeritas p50 (ms) | 比 |
|---|---|---|---|
| 1 | 2.946 | 2.709 | 1.088 |
| 2 | 2.817 | 2.416 | 1.166 |
| 3 | **3.910** | 2.345 | 1.667 |
| median | — | — | **1.166** |

`verdict=NO_SPEED_VERDICT`、`goalCondition=NOT_MET`。理由は
`externalCpuCores=0.649`（上限 0.5）と `initialBusyCores=1.358`:
**ビルド直後に KPI を開始したため Gradle/Kotlin デーモンの負荷が残っていた**（開始時 load average 3.73）。
rep 3 の Celtium は p99 22.9 ms で、明らかに外乱。**この比は判定に使わない。**

### それでも読めること: **Celtium の warm p50 は round 51 から動いていない**

round 51 の rep 1 は **2.946**、本 round の rep 1 も **2.946**。round 52–56 の変更
（pair clear の pass 内化・font colour witness の絞り込み＝受理 CPU −0.28 ms/フレーム）を挟んで
**同じ値**である。round 56 の handoff が「end-to-end は未証明」と書いたのは正しく、
**受理経路の CPU 削減はフレーム間隔に現れていない**。

## 4. フレームの内訳（`r56b-combine` の `CELTIUM_FRAME_STATS`、warm 窓）

```
ms[p50=2.60 p95=4.49] loop[frames=120 loopMean=2.82 tickMean=1.35 displayMean=0.25
  renderMean=1.85 syncMean=0.00 fmlMean=0.53]
```

`tickMean` は **tick が走ったフレーム数**（`tickFrames`）で割るので、120 フレーム中の寄与は
1.35 × 6 / 120 ≈ 0.07。したがって

**frame 2.60–2.82 = render 1.85 + fmlTick 0.53 + display 0.25 + tick 0.07**（残り ~0.1 が未帰属）。

**render がフレームの 71%**で、Celeritas との差はここにある。Celeritas の frame が 2.35–2.71 なので、
同じ fmlTick/display を引くと Celeritas の render は ~1.5 ms、**差は ~0.35 ms**。
Celtium 自身の render 内訳は受理 ~0.5（うち flush が大半）＋ window ~0.4 ＋ present ~0.2。

## 5. JFR は「leg 全体」では答えにならない（今 round の失敗）

`-XX:StartFlightRecording=…,settings=…/jfr-wait-1ms.jfc,duration=200s` を leg `r57b-jfr` に渡した。
記録は取れたが **`jdk.ExecutionSample` が 165 s で 437 件**（間隔 7–17 ms＝1 ms 設定が効いていない）で、
しかもサンプルは**ロード局面**に集中していた。理由は単純で、**leg 170 s のうち world が描いているのは
warm 窓の ~9 s だけ**（`worldFrames` 1,456 × 2.9 ms ≈ 4.2 s）だから、leg 全体を録ると
99% が待ち・ロードになる。**次に JFR を使うなら warm 窓だけを録る**（`delay` を実測して合わせるか、
`jdk.ExecutionSample` を stack で絞る）。同じ jfc は素の JVM では 1 ms で動くことは確認済み。

## 6. 主張しないこと

- 速度の改善は主張しない（§2）。
- KPI の比 1.166 は判定に使わない（§3、外部負荷）。
- 「受理経路を削ってもフレームが動かない」の**理由**は未確定。render 1.85 の内訳が
  Celtium 自身の仕事で埋まっているのか、待ちで埋まっているのかは、warm 窓を狙った計測でしか決まらない。
