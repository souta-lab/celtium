# the creative inventory's hovered-slot highlight is accepted, drawn, and leaves no pixels (2026-11-02)

四条の未解決症状のうち「creative tab の一部破損」は round 2 で item icon の空 plan を直して閉じたが、
**1 cell だけ参照と違う**という残りがあった（`r22c`/`r22d`）。この round はその 1 cell を帰属させた:
**欠けているのは item ではなく、`GuiContainer` が hover 中の slot に描く `0x80FFFFFF` のハイライト**である。

## 観測 1: 差分は「白 50 % の overlay」そのもの（画素の等式で確定）

`docs/evidence/creative-inventory-fixed-2026-11-02.png`（Celtium）と
`docs/evidence/creative-inventory-celeritas-2026-11-02.png`（Celeritas 参照）の同じ矩形
`(594..642, 321..369)`（guiScale 3 の slot 1 個）で、**参照のすべての画素が Celtium の画素と白の中間値**になる:

```
celtium 139 139 139  ->  celeritas 197 197 197    ((139+255)/2 = 197)
celtium  21  21  21  ->  celeritas 138 138 138    ((21+255)/2  = 138)
cell 平均 127.01     ->  cell 平均 190.27
```

50 % 白の overlay 以外の説明がつかない（`0x80FFFFFF`、alpha 128）。item icon の差ではない。
`GuiContainer.drawScreen` は hover 中の slot に対して
`colorMask(true,true,true,false)` の下で `drawGradientRect(j1,k1,j1+16,k1+16,-2130706433,-2130706433)` を呼ぶ
（`GuiContainer.java:121-128`、`-2130706433 = 0x80FFFFFF`）。**この quad が絵に無い。**

## 観測 2: quad は受理され、正しい位置で、描かれている

leg `r23a-maskbounds`（stage `r22d` の packaged JAR、AMD RX 6400、creative scene、guiScale 3、
`-Dceltium.vulkan.groupBounds=true -Dceltium.vulkan.groupBoundsFormat=1 -Dceltium.vulkan.groupBoundsMinFrame=8`）:

```
CELTIUM_GROUP_BOUNDS frame=8 group=1417 fmt=1 worldPass=false target=1077150275 texture=390 batches=1
  vertices=4 indices=6 colour0=0x80ffffff vertexColour0=0x80ffffff
  state=[blend=1 src=6 dst=7 srcA=1 dstA=0 mask=0x7 eq=0/0 alphaFunc=0 depthTest=0 depthWrite=1 cull=1 ...]
  proj=[m00=0.005 m11=-0.008 m22=0.0 m32=-0.5]
  ndc x[-0.08..-0.00] y[-0.02..0.12] z[0.50..0.50]
```

* ndc → 画面 `(588..640, 317..367)` = **hover 中の slot そのもの**（投影・model-view・頂点とも正しい）。
* 同じ run の描画カウンタは `drawnByFormat=[1:1180, 2:2459, 4:1286576, ...]`。
  **format 1 の group は 1,180 個描かれている** — つまり「描かれなかった」のではない。
* 同じ window の隣の group（`0xc0101010` tooltip 背景、`0xb5000000`、`0x20ffffff`）は
  `mask=0xf depthTest=1` で、いずれも絵に出ている。

この group と、絵に出ている隣の group の差は **mask（0x7 vs 0xf）と depthTest（0 vs 1）だけ**。

## 棄却した仮説: 「alpha を書かないので coverage が無い」

`celtium$stateFromSignature` の frame-target 経路で、blend 有効かつ mask が RGB のみのとき alpha bit を
戻す変更（`-Dceltium.vulkan.blendWritesAlpha`、既定 ON）を実装し、同じ JAR で測った:

| leg | highlight 矩形の平均 | 判定 |
|---|---|---|
| `r23b-alpha`（alpha を書く） | 115.15 | **変わらず**（overlay 無しの値と同じ） |
| 変更前 `r23a`（mask 0x7 のまま） | 115.15 | — |

⇒ **alpha write は原因ではない**（この group は window target ではない: `target=1077150275` ≠ 0）。
変更は revert した。この 1 点で「coverage 説」は落ちる。

## 棄却した仮説 2: 「prepared pipeline が違う」

`CELTIUM_GROUP_BOUNDS` に `prepared=<pipeline class>` を足して測った leg `r23c-prepared`（同じ scene/flags、
新 JAR `d6e7bce663cf70043b638c97`）: その frame の POSITION_COLOR group 6 個は**すべて**
`prepared=VulkanImmediatePipeline`（highlight だけ別 class、ではない）。この仮説も落ちる。

## 残る候補（次の判別）

group は受理・close・draw されているので、残るのは **draw が画素を残さない形**だけ:

1. **group の scissor**。BH-09 の規則で clip 矩形は group ごとに capture され（`currentGroupScissor` /
   `currentGroupScissorEnabled`）、draw 時に `celtium$setGroupViewport` が適用する。highlight の group だけ
   scissor が有効/別矩形なら、GL の参照では出る絵がここでだけ消える。次の leg はこの 2 値を
   `CELTIUM_GROUP_BOUNDS` に足して、highlight と隣の可視 group を比べる（`glScissor` の呼び出し回数は
   `CELTIUM_GLSTUB` の scissor census で読める）。
2. viewport / descriptor がこの group だけ違う（同じ window の他 group は出ているので、group 単位の差）。
3. depth attachment の内容（`depthTest=0` なので test では落ちないが、pass 側の attachment が
   depth を持たない/別物という構成差は残る）。

`CELTIUM_GROUP_BOUNDS` の `state=` は capture 値、`gl*` は close 時の読みなので、
**draw 時に実際に bind された pipeline** を言う計器はこれまで無かった。`prepared=` がその 1 列目になる。
