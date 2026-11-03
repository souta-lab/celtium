# pair clear を既定で pass の中へ（2026-11-04、round 54）

round 53 は「font run を割るのは行列ではない」を棄却で終え、次にやることを 5 つ名指しした。その 1 つが
**`pairClearInPass` の既定を決める**ことだった。実装は round 31 から入っていて既定 OFF、AMD の paired 計測で
`winHead` 0.1 → 0.0 ms が出ていたが、Intel の `COLOR_CLEAR_TRANSFER`（CCS fast clear）を失わないことを
確かめる leg が無いままだった。本 round は既定を ON にし、両 GPU の受入 leg で validation 0・絵の不変を確認した。
あわせて 2 つの小さな修正（clear rect の clamp、受理経路の flush 二重計上の分離）を入れた。

## 1. 何を変えたか

| 変更 | 内容 |
|---|---|
| **既定の反転** | `GllessFrameTarget.Pair.PAIR_CLEAR_IN_PASS` を **既定 ON**。`-Dceltium.vulkan.pairClearInPass=false` が旧経路（`vkCmdClear*Image` 2 回 + barrier 2 本）で、A/B はこの 1 フラグで取れる |
| **rect の clamp** | `recordDeferredClearAttachments` は pass の render area をそのまま rect にしていた。`celtium$deferredClearExtent(passExtent, pairExtent)` で pair の実 extent に clamp する（`VUID-vkCmdClearAttachments-pRects-00016` の予防。通常は両者が一致するので絵は変わらない） |
| **受理経路の flush 分離** | `flushImmediateWindow()` は `submitImmediateBatchTimed` の中、つまり `immediateSubmitNanos` の内側で走る。flush は `flush`/`winRecord` として別に測られているので、受理の平均に二重計上されていた。`accept[flushMs]` を追加し、受理自身のコストと、受理が起こした窓のコストを分けられるようにした |

`PairClearInPassTest` に 2 ケース追加（既定の表・clamp の表）。celtium-vulkan は **388 → 390 tests / 0 failures**。

## 2. なぜ既定を動かしてよいと判断したか

round 53 の paired 計測（同一 JAR・同一 scene・同一 flags、スイッチだけが違う 2 本）が根拠:

| | スイッチ OFF | スイッチ ON |
|---|---|---|
| `winHead` | 0.1 ms | **0.0 ms** |
| warm p50 | 3.062 ms | 3.890 ms（差は frame budget の `other`＝ゲーム自身。renderer の bracket は不変） |

`winHead` は「`recordImmediateOnlyPass` が `winPrefix` より前にやること（target routing・pair clear・begin-info）」で、
その中身はほぼ pair clear だけだった。world scene は 12 pair/フレームを clear する（GregTech の bloom chain が
mip pyramid の framebuffer を bind する）。外側の clear は image を transfer layout へ往復させ、transfer write を
attachment read に見せる barrier を要求する。pass の中では attachment がすでに pass の宣言した layout にあり、
clear は pass 自身が順序づけるので、その 2 本の barrier と 2 回の `vkCmdClear*Image` が 1 回の
`vkCmdClearAttachments` になる。

**色を `TRANSFER_DST_OPTIMAL` ではなく attachment layout で clear する**のが Intel の懸念点だった
（`COLOR_CLEAR_TRANSFER` はその layout のためにある）。`vkCmdClearAttachments` は Intel でも fast clear の
正規経路なので、失う理由は無いと読んだが、**読んだだけでは受入にしない**。両 GPU で leg を取った（§3）。

## 3. 受入 leg

同一 stage（renderer `1997081a…`、fixture `0e90c988…` 不変）、`world` scene、`guiScale3` seed、
validation ON、既定 flags（＝pair clear は ON）。

| leg | GPU | verdict | VUID | `CELTIUM_REFUSALS_BY_FORMAT` | vulkan-check | `refusedBeforeAppend` | presentedImage | calibration |
|---|---|---|---|---|---|---|---|---|
| `r54a-gate` | AMD RX 6400 (RADV NAVI24) | **PASSED** | **0** | `{}` | errors=0 | 0 | **PASSED** | true |
| `r54b-intel` | Intel UHD Graphics 630 (CFL GT2) | **PASSED** | **0** | `{}` | errors=0 | 0 | **PASSED** | true |

`problems=[]`、`fixtureStatus=PASSED`。AMD は `worldTicksAtEnd=345`・`worldFrames=1465`、Intel は 346・1063
（fixture が capture 後に抜ける）。`CELTIUM_CHAIN_PAIR_CLEAR` は frame 0 から出ており、deferred 経路が実際に
走ったことはログで確認できる。

## 4. スイッチの効果（同一 JAR・同一 flags・同一 scene の 2 本）

`passSplit=true batchTimings=true windowSplit=true`、`warmFromTick=400 warmToTick=600 holdUntilTick=620`、
`presentedDumpEvery=0`。**違うのは `-Dceltium.vulkan.pairClearInPass` だけ**。

| | ON（`r54c-split`） | OFF（`r54d-off`） |
|---|---|---|
| `winHead`（plain フレーム平均） | **43 µs** | **141 µs** |
| `winRecord`（plain） | 430 µs | 463 µs |
| `wrecLoop`（plain） | 266 µs | 263 µs |
| `flush`（plain） | 583 µs | 606 µs |
| `world`（plain） | 2171 µs | 2041 µs |
| warm p50 | 3.6176 ms | 3.4972 ms |
| frames / ticks | 4888 / 619 | 4961 / 619 |

**`winHead` は 141 → 43 µs、98 µs の削減。** これは変更が触る bracket そのもので、round 53 の
「0.1 → 0.0 ms」と一致する。`winRecord` も 33 µs 下がっている（barrier 2 本と `vkCmdClear*Image` 2 回が
pass 内の `vkCmdClearAttachments` 1 回になるので、clear の仕事は winHead から winRecord へ移るだけでなく
総量も減る）。

**フレーム全体では差を主張しない。** warm p50 は ON が 3.4% 悪いが、`world` の非 window 部分
（terrain 等、この変更が触らない所）が ON で 153 µs 高く、そちらが window の差を上回っている。
逐次 2 本で scene 状態もわずかに違い（4888 vs 4961 フレーム）、交互実行ではないので分離できない。

### この 2 本の片方が受入に落ちた理由（計器の罠）

ON の `r54c-split` は `presentedImageVerdict=FAILED_PRESENTED_IMAGE` で落ちた。中身は
`presentedRestartAtWorldFrame=4889`（capture は 4888）＝**presented カウンタが capture の 1 フレーム後に
再スタートした**ため、`worldFrames - worldFramesAtWorldStart = -1` になり、絵のフレーム番号と合わせられなく
なった、というもの。`ValidationClient` は presented カウンタの**減少**を「新しい world が window 所有の
インスタンスを adopt した」と読む（`celtium$adoptTerrainState`）ので、run の最後に adopt が来ると
この assertion は絵が正しくても落ちる。同じ JAR・同じ ON 構成の `r54a-gate` と `r54b-intel` は
`presentedImageVerdict=PASSED`・`presentedCounterRestarts` 無しで通っている。
**再スタート自体の原因は特定していない**（pair clear との因果は示せていない）。

## 5. 主張しないこと

- **98 µs は `winHead` の bracket の値**で、フレーム時間の改善ではない。§4 のとおり、この 2 本の
  warm p50 は ON が 3.4% 悪く、その差は変更が触らない `world` の非 window 部分にある。フレームでの前進を
  主張するには、交互実行の paired set が要る。未取得。
- Intel で CCS fast clear が生きていることを**直接**は見ていない（見たのは validation 0 と絵の一致）。
  Intel の性能が悪化していないことの証明には、Intel 側の paired A/B が要る。未取得。
- `accept[flushMs]` は `-Dceltium.vulkan.batchTimings=true` **と** `-Dceltium.vulkan.frameStats=true` の
  両方が要る（`acceptSpanSummary` は `CELTIUM_FRAME_STATS` の行にしか出ない）。本 round の測定 leg は
  `frameStats` を渡していないので、**この計器の値はまだ読んでいない**。配線はコードで確認しただけ。
- 条件 C（Celeritas を上回る）は前進していない。差 0.52 ms に対して 98 µs の見込みで、しかも AMD の
  bracket 値。`winHead` は 43 µs 残っており、pair clear だけでは 0 にならない。
