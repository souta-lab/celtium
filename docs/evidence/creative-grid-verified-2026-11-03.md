# creative inventory: the grid draws every stack, and the snapshot that could not say so (2026-11-03)

rc.26 の4症状のうち「only some creative inventory tabs rendering wrong」は、2026-11-02 に scene と参照 pair が
作られ、**19 of 24 cells が Celeritas と mean|A-B| > 6** として測られた
（[creative-inventory-2026-11-02.md](creative-inventory-2026-11-02.md)）。同日 19:10 の `4c7b6b9a5`
（"the creative inventory's missing item icons were a cached empty item plan"）が根因を直している。
この round は**現行 JAR でその症状が再現しないこと**を、同じ scene・同じ flags・同じ参照画像で測った。

## 走らせた leg

`r42a-creative`（AMD Radeon RX 6400 / RADV NAVI24、mode `plain`、Supersymmetry instance、1280x720、
`guiScale3.txt`（guiScale=3, renderDistance=12）、validation ON、X11）。

* renderer `aaf8eecb57ca30fdd442ad84f2a326ef1eff4edabc1ab503cd49530cfe0e4de4`
* fixture `8162189d281c56d2586ec34e0c23a5907da8f065882dc9cad95c9a026298f6fa`
* `-Dceltium.validation.screen=creative -Dceltium.validation.creativeTab=12`
* `celtium/vulkan-check passes=1814 skipped=0 errors=0`、`VUID` 0行、`CELTIUM_REFUSALS_BY_FORMAT {}`
* report: `namedScreenOpened=net.minecraft.client.gui.inventory.GuiContainerCreative`（tick 31）、
  `creativeTabApplied=12`、`creativeTabLabel=littletiles`、`creativeTabPage=1`、`creativeTabCount=92`、
  `creativeGridSlots=54`、`creativeGridFilled=46`、`creativeGridFirstStack=Little Hammer x1`、
  `screenshotAtTickScreen=GuiContainerCreative`（tick 200）
* verdict は `UNCALIBRATED_PRESENTED_IMAGE`（exit 2）**by construction**: named screen は較正 key を動かすので
  terrain の floor はこの絵を記述しない。判定は下の測定であって verdict ではない。

## 測定1: 参照画像との一致

2026-11-02 の pair 画像（`creative-inventory-grid-pair-2026-11-02.png`、620x826、上=Celtium 旧・下=Celeritas）の
**下半分（Celeritas）**を、今回の presented dump（`celtium-presented-tick200-…-kept.png`）に
整数オフセットで位置合わせした（粗探索 4px 刻み → 1px 刻み）。最良は `dx=340, dy=147`、mean|A-B| = **1.10**。

| 指標 | 値 |
|---|---|
| 整列後の mean \|A-B\|（620x413 全面） | **1.17** |
| p99 / max | 23 / 255 |
| 差 > 6 の画素 | 1.39 %（うち rows 0-2 と 390-401 の crop 端が大半） |
| 差 > 24 の画素 | 0.99 % |

grid の 45 cell（5行×9列、`x0=375 y0=210 cell=48`）ごとの mean|A-B|:

| row | col0..col8 |
|---|---|
| 0 | 0.00 2.04 0.06 0.00 0.00 0.00 0.04 1.30 0.00 |
| 1 | 0.42 0.06 0.26 0.22 0.26 0.22 1.05 0.40 0.48 |
| 2 | 0.10 0.29 0.07 0.05 0.00 0.00 0.04 0.02 0.02 |
| 3 | 0.09 0.15 0.01 0.13 0.16 0.05 0.11 0.01 0.00 |
| 4 | 0.04 0.02 0.03 0.13 0.09 0.00 0.01 0.00 0.00 |

2026-11-02 は「tooltip に隠れていない 24 cell のうち 19 cell が mean|A-B| > 6」だった。現在の最大は **2.04**
（row0 col1）で、**全 cell が 2.1 未満**。差の残りは tooltip の文字（animated）と JEI panel の状態差で、
grid の item icon ではない。

## 測定2: 各 cell の ink（参照なしで言える形）

presented dump の grid 45 cell + 最下段で、slot 背景 `(139,139,139)` から 25 以上離れた画素を数えた:
**全 45 cell が 348–1600 画素**（tooltip に覆われた 9 cell は 1600 で飽和）、最下段 1257。空の cell は無い
（container は 54 slot 中 46 が stack 持ち、うち 45 が grid・1 が最下段）。

## 見つけた計器欠陥: 毎tickの grid snapshot は1行も書いていなかった

`celtium$reportCreativeGridSlots(tag)` は「container が空」と「renderer が描いていない」を分けるために
足された計器で、`worldTicks % 60 == 0` の tick に呼ばれる。この leg の report には
`creativeGridAt*` の key が**1つも無い**（`creativeGridSlots/Filled/FirstStack` は tab 切替時のものだけ）。

原因は2つで、どちらも「計器が黙って何も言わない」形:

1. メソッドは現在の screen が `GuiContainerCreative` でなければ**何も書かずに return** する。呼び出し側は
   その前に `celtium$creativeGridSnapshots++` しているので、**snapshot を取ったのに key が無い**状態が
   正常に見える。実際この leg では 4 回（tick 60/120/180/240）呼ばれて 4 回とも何も書いていない。
2. 幾何（`guiLeft/guiTop`・最初の slot の `xPos/yPos`・pitch・列数）が report に無く、**絵の cell を
   計算できない**。checker が panel の位置を定数で持つしかなく、それは自分の定数を測ることになる。

修正:

* snapshot は**最初に必ず** `creativeGrid<tag>Screen`（現在の screen の class 名、無ければ `none`）を書く。
  これで「snapshot が走ったか」「そのとき何が開いていたか」が report だけで読める。
* `creativeGridLayout`（`gui=WxH left=L top=T first=x,y pitch=P cols=C rows=R slots=S`、GUI 単位）を
  tab 切替時と各 snapshot の両方で書く。scale は書かない: `picture_width / guiWidth` で、checker が
  絵とこの行から読む（`tooltip-motion-check.py` と同じ規則）。
* `scripts/cpu-contract/creative-grid-check.py`（新規）: report の layout と presented dump から cell を作り、
  **container が stack を持つと報告した slot の cell が icon を持つこと**を要求する。cell の背景は
  grid 全体の最頻色（1 cell が全面 icon でも自分の最頻色にならない）。空 cell には最も濃い cell を
  貼って「occupied と読めるか」を要求する positive control 付き。
  `scripts/tests/test_creative_grid_check.py`（7 tests）が、icon の無い絵・1 cell だけ欠けた絵・
  layout の無い report を **FAIL** させ、揃った絵を PASS させることを固定する。

## 修正の実機確認（leg `r42b-creative`、新 fixture `23b92ae5…`）

同じ scene・同じ flags を、修正した fixture で1本走らせた（renderer は `aaf8eecb…` で不変）。
`VUID` 0行、`CELTIUM_REFUSALS_BY_FORMAT {}`、`celtium/vulkan-check passes=2769 errors=0`。

report に出たもの:

```
creativeGridLayout      = gui=427x240 left=116 top=52 first=9,18 pitch=18 cols=9 rows=6 slots=54
creativeGridAt240Screen = net.minecraft.client.gui.inventory.GuiContainerCreative
creativeGridAt240Layout = gui=427x240 left=116 top=52 first=9,18 pitch=18 cols=9 rows=6 slots=54
creativeGridAt240Filled = 46
creativeGridAt240Contents = 0=littletiles:hammerx1 … 45=betterquesting:quest_bookx1   (46 entries)
```

幾何は手で絵から導いた値（`left=116 top=52` は 427x240 の GUI に 195x136 の panel を中央寄せした値、
`first=9,18` は `ContainerCreative` の最初の slot）と一致した。tick 240 の snapshot が走り、
**そのとき開いていた screen を名指ししている**。

`creative-grid-check.py --run .gv-verify/run-r42b-creative`:

```
container: slots=54 filled=46
picture:   cells with an icon=54
per-cell ink: 882 1782 1062 1683 690 708 1029 1035 307 565 370 471 363 631 1208 836 1050 1051 839 692 517 627
              1630 1584 2304 2304 2304 459 721 327 1096 956 1724 2304 2304 2304 934 375 459 721 956 1584
              2304 2304 2304 1800 540 540 540 495 861 1062 1062 1062
control:   cells with an icon=54 of 54 (every empty cell painted from cell 24) -> ok
filled slots: creativeGridAt240Contents (0 … 45)
PASS: every one of the container's 46 stacks has an icon in its own cell
```

`2304` は tooltip に覆われた 9 cell（cell 全面が slot 背景と違う）。それ以外は 307–1800。

## この測定が言わないこと

* **どの修正が効いたか**は言わない。`4c7b6b9a5` が根因（空の item plan の cache）と名指ししているが、
  この round は現行 JAR の絵が参照と一致することだけを測った。
* **他の tab** は測っていない。`creativeTab=12`（littletiles、page 2）だけ。
* **Intel** は測っていない。
* presented dump と game 自身の GL screenshot の一致は、この leg では取っていない
  （`screenshotIsEvidence=false`）。2026-11-02 の evidence が同じ欠落を2枚で見ているので、
  dump 経路が item を落とす可能性は下がっているが、否定はしていない。
* FPS の主張はしない。validation は 0 行だが、これはこの scene の話。
